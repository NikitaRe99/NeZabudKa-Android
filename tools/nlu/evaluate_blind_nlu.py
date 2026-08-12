from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re

import onnxruntime as ort
from transformers import BertTokenizerFast

from blind_corpus_v2 import build_blind_corpus_v2
from blind_corpus_v3 import build_blind_corpus_v3
from blind_corpus_v4 import build_blind_corpus_v4
from blind_corpus_v5 import build_blind_corpus_v5
from dataset import build_training_examples
from evaluate_nlu import (
    CORPUS,
    EXTENDED_CORPUS,
    METADATA,
    MODEL,
    allowed,
    mapped_payload,
    normalized,
    predict_batch,
)
from heldout_corpus import build_heldout_corpus


ROOT = Path(__file__).resolve().parents[2]


def normalized_phrase(value: str) -> str:
    return re.sub(r"[^0-9a-zа-яё]+", " ", value.casefold()).strip()


def expected_payload(case: dict[str, object]) -> str | None:
    value = case.get("text") or case.get("target") or case.get("folder")
    return value if isinstance(value, str) else None


def case_skeleton(case: dict[str, object]) -> str:
    utterance = str(case["utterance"])
    payload = expected_payload(case)
    if payload:
        utterance = re.sub(re.escape(payload), " {payload} ", utterance, count=1, flags=re.I)
    return normalized_phrase(utterance)


def training_skeleton(example) -> str:
    value = example.text
    for start, end, slot in sorted(example.spans, reverse=True):
        value = value[:start] + f" {{{slot.casefold()}}} " + value[end:]
    return normalized_phrase(value)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", choices=("blind2", "blind3", "blind4", "blind5"), default="blind2")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()

    builders = {
        "blind2": build_blind_corpus_v2,
        "blind3": build_blind_corpus_v3,
        "blind4": build_blind_corpus_v4,
        "blind5": build_blind_corpus_v5,
    }
    blind = builders[args.corpus]()
    training = build_training_examples()
    previous = (
        json.loads(CORPUS.read_text(encoding="utf-8")) +
        json.loads(EXTENDED_CORPUS.read_text(encoding="utf-8")) +
        build_heldout_corpus()
    )
    if args.corpus == "blind3":
        previous += build_blind_corpus_v2()
    elif args.corpus == "blind4":
        previous += build_blind_corpus_v2() + build_blind_corpus_v3()
    elif args.corpus == "blind5":
        previous += build_blind_corpus_v2() + build_blind_corpus_v3() + build_blind_corpus_v4()

    blind_phrases = [normalized_phrase(str(case["utterance"])) for case in blind]
    known_phrases = {normalized_phrase(example.text) for example in training}
    known_phrases.update(normalized_phrase(str(case["utterance"])) for case in previous)
    exact_overlaps = sorted(set(blind_phrases) & known_phrases)
    duplicate_blind = [phrase for phrase, count in Counter(blind_phrases).items() if count > 1]

    known_payloads = {
        normalized_phrase(example.text[start:end])
        for example in training
        for start, end, slot in example.spans
        if slot in {"TEXT", "TARGET", "FOLDER"}
    }
    known_payloads.update(
        normalized_phrase(payload)
        for case in previous
        if (payload := expected_payload(case))
    )
    blind_payloads = {
        normalized_phrase(payload)
        for case in blind
        if (payload := expected_payload(case))
    }
    payload_overlaps = sorted(blind_payloads & known_payloads)

    known_skeletons = {training_skeleton(example) for example in training}
    known_skeletons.update(case_skeleton(case) for case in previous)
    blind_skeletons = {case_skeleton(case) for case in blind}
    skeleton_overlaps = sorted(blind_skeletons & known_skeletons)

    overlap_report = {
        "blind_total": len(blind),
        "blind_unique": len(set(blind_phrases)),
        "training_total": len(training),
        "previous_test_total": len(previous),
        "exact_phrase_overlaps": len(exact_overlaps),
        "payload_overlaps": len(payload_overlaps),
        "template_skeleton_overlaps": len(skeleton_overlaps),
    }
    if exact_overlaps or duplicate_blind or payload_overlaps or skeleton_overlaps:
        raise SystemExit(json.dumps({
            **overlap_report,
            "duplicate_blind": duplicate_blind,
            "exact_overlap_examples": exact_overlaps[:10],
            "payload_overlap_examples": payload_overlaps[:10],
            "skeleton_overlap_examples": skeleton_overlaps[:10],
        }, ensure_ascii=False, indent=2))
    print(json.dumps(overlap_report, ensure_ascii=False, indent=2))
    if args.verify_only:
        return

    metadata = json.loads(METADATA.read_text(encoding="utf-8"))
    tokenizer = BertTokenizerFast(
        vocab=str(MODEL.parent / "vocab.txt"),
        do_lower_case=bool(metadata.get("do_lower_case", False)),
    )
    session = ort.InferenceSession(MODEL, providers=["CPUExecutionProvider"])
    predictions = [
        prediction
        for start in range(0, len(blind), 8)
        for prediction in predict_batch(session, tokenizer, metadata, blind[start:start + 8])
    ]

    raw_intent_correct = final_intent_correct = final_payload_correct = 0
    raw_false_destructive = final_false_destructive = 0
    by_intent = Counter()
    correct_by_intent = Counter()
    raw_correct_by_intent = Counter()
    payload_correct_by_intent = Counter()
    raw_confusion: dict[str, Counter[str]] = {}
    final_confusion: dict[str, Counter[str]] = {}
    raw_false_destructive_examples = []
    failures = []
    for case, (intent, confidence, slots, raw_intent) in zip(blind, predictions):
        expected_intent = str(case["intent"])
        by_intent[expected_intent] += 1
        raw_intent_correct += int(raw_intent == expected_intent)
        raw_correct_by_intent[expected_intent] += int(raw_intent == expected_intent)
        final_intent_correct += int(intent == expected_intent)
        correct_by_intent[expected_intent] += int(intent == expected_intent)
        raw_confusion.setdefault(expected_intent, Counter())[raw_intent] += 1
        final_confusion.setdefault(expected_intent, Counter())[intent] += 1
        if (
            raw_intent in {"delete_reminder", "delete_folder"} and
            raw_intent != expected_intent and
            allowed(raw_intent, str(case["scope"]))
        ):
            raw_false_destructive += 1
            raw_false_destructive_examples.append({
                "id": case["id"], "utterance": case["utterance"],
                "expected": expected_intent, "raw_intent": raw_intent,
            })
        if intent in {"delete_reminder", "delete_folder"} and intent != expected_intent:
            final_false_destructive += 1

        expected = expected_payload(case)
        actual = mapped_payload(intent, str(case["utterance"]), slots)
        payload_ok = expected is None or normalized(expected) == normalized(actual)
        final_payload_correct += int(intent == expected_intent and payload_ok)
        payload_correct_by_intent[expected_intent] += int(intent == expected_intent and payload_ok)
        if intent != expected_intent or not payload_ok:
            failures.append({
                "id": case["id"],
                "scope": case["scope"],
                "utterance": case["utterance"],
                "expected": expected_intent,
                "raw_intent": raw_intent,
                "actual": intent,
                "confidence": round(confidence, 4),
                "expected_payload": expected,
                "actual_payload": actual,
                "slots": slots,
            })

    report = {
        **overlap_report,
        "raw_intent_accuracy": raw_intent_correct / len(blind),
        "final_intent_accuracy": final_intent_correct / len(blind),
        "final_payload_accuracy": final_payload_correct / len(blind),
        "raw_false_destructive": raw_false_destructive,
        "final_false_destructive": final_false_destructive,
        "by_intent": {
            intent: f"{correct_by_intent[intent]}/{total}"
            for intent, total in sorted(by_intent.items())
        },
        "raw_by_intent": {
            intent: f"{raw_correct_by_intent[intent]}/{total}"
            for intent, total in sorted(by_intent.items())
        },
        "payload_by_intent": {
            intent: f"{payload_correct_by_intent[intent]}/{total}"
            for intent, total in sorted(by_intent.items())
        },
        "raw_confusion": {expected: dict(actual) for expected, actual in sorted(raw_confusion.items())},
        "final_confusion": {expected: dict(actual) for expected, actual in sorted(final_confusion.items())},
        "raw_false_destructive_examples": raw_false_destructive_examples,
        "failure_count": len(failures),
        "failures": failures,
    }
    output = ROOT / f"tools/nlu/output/{args.corpus}_report.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "failures"}, ensure_ascii=False, indent=2))
    print(f"report={output}")


if __name__ == "__main__":
    main()
