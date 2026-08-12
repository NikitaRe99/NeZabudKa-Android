from __future__ import annotations

import argparse
import copy
import json
from pathlib import Path
import random
import shutil
from collections import Counter

import numpy as np
import torch
from torch import nn
from torch.utils.data import DataLoader, Dataset
from transformers import AutoModel, AutoTokenizer

from dataset import Example, INTENTS, SLOT_LABELS, build_training_examples


BASE_MODEL = "cointegrated/rubert-tiny2"
MAX_LENGTH = 64
SEED = 20260808


class JointNluModel(nn.Module):
    def __init__(self, base_model: str, intent_count: int, slot_count: int):
        super().__init__()
        self.encoder = AutoModel.from_pretrained(base_model)
        hidden = self.encoder.config.hidden_size
        self.dropout = nn.Dropout(0.1)
        self.intent_classifier = nn.Linear(hidden, intent_count)
        self.slot_classifier = nn.Linear(hidden, slot_count)

    def forward(self, input_ids, attention_mask, token_type_ids):
        encoded = self.encoder(
            input_ids=input_ids,
            attention_mask=attention_mask,
            token_type_ids=token_type_ids,
        ).last_hidden_state
        encoded = self.dropout(encoded)
        return self.intent_classifier(encoded[:, 0]), self.slot_classifier(encoded)


class NluDataset(Dataset):
    def __init__(self, examples: list[Example], tokenizer):
        self.examples = examples
        self.tokenizer = tokenizer

    def __len__(self):
        return len(self.examples)

    def __getitem__(self, index):
        example = self.examples[index]
        encoded = self.tokenizer(
            example.text,
            max_length=MAX_LENGTH,
            padding="max_length",
            truncation=True,
            return_offsets_mapping=True,
        )
        labels = []
        begun: set[tuple[int, int, str]] = set()
        for token_index, (start, end) in enumerate(encoded.pop("offset_mapping")):
            if start == end or encoded["attention_mask"][token_index] == 0:
                labels.append(-100)
                continue
            matched = next((span for span in example.spans if start < span[1] and end > span[0]), None)
            if matched is None:
                labels.append(SLOT_LABELS.index("O"))
            else:
                prefix = "I" if matched in begun else "B"
                begun.add(matched)
                labels.append(SLOT_LABELS.index(f"{prefix}-{matched[2]}"))
        token_type_ids = encoded.get("token_type_ids", [0] * MAX_LENGTH)
        return {
            "input_ids": torch.tensor(encoded["input_ids"], dtype=torch.long),
            "attention_mask": torch.tensor(encoded["attention_mask"], dtype=torch.long),
            "token_type_ids": torch.tensor(token_type_ids, dtype=torch.long),
            "intent_labels": torch.tensor(INTENTS.index(example.intent), dtype=torch.long),
            "slot_labels": torch.tensor(labels, dtype=torch.long),
        }


def _macro_f1(expected: list[int], actual: list[int], labels: list[int]) -> float:
    scores = []
    for label in labels:
        true_positive = sum(left == label and right == label for left, right in zip(expected, actual))
        false_positive = sum(left != label and right == label for left, right in zip(expected, actual))
        false_negative = sum(left == label and right != label for left, right in zip(expected, actual))
        denominator = 2 * true_positive + false_positive + false_negative
        if denominator:
            scores.append(2 * true_positive / denominator)
    return sum(scores) / len(scores) if scores else 0.0


def validation_metrics(model, loader, device):
    model.eval()
    expected_intents: list[int] = []
    actual_intents: list[int] = []
    expected_slots: list[int] = []
    actual_slots: list[int] = []
    entity_exact = entity_total = 0
    with torch.no_grad():
        for batch in loader:
            intent_logits, slot_logits = model(
                batch["input_ids"].to(device),
                batch["attention_mask"].to(device),
                batch["token_type_ids"].to(device),
            )
            intent_predictions = intent_logits.argmax(-1).cpu()
            expected_intents.extend(batch["intent_labels"].tolist())
            actual_intents.extend(intent_predictions.tolist())
            slot_predictions = slot_logits.argmax(-1).cpu()
            for expected, actual in zip(batch["slot_labels"], slot_predictions):
                mask = expected != -100
                expected_sequence = expected[mask].tolist()
                actual_sequence = actual[mask].tolist()
                expected_slots.extend(expected_sequence)
                actual_slots.extend(actual_sequence)
                entity_exact += int(expected_sequence == actual_sequence)
                entity_total += 1
    intent_accuracy = sum(left == right for left, right in zip(expected_intents, actual_intents)) / len(expected_intents)
    slot_accuracy = sum(left == right for left, right in zip(expected_slots, actual_slots)) / len(expected_slots)
    confusion = [[0 for _ in INTENTS] for _ in INTENTS]
    for expected, actual in zip(expected_intents, actual_intents):
        confusion[expected][actual] += 1
    return {
        "intent_accuracy": intent_accuracy,
        "intent_macro_f1": _macro_f1(expected_intents, actual_intents, list(range(len(INTENTS)))),
        "slot_token_accuracy": slot_accuracy,
        "slot_macro_f1": _macro_f1(expected_slots, actual_slots, list(range(1, len(SLOT_LABELS)))),
        "entity_exact_match": entity_exact / entity_total,
        "confusion_matrix": confusion,
    }


def stratified_split(examples: list[Example], validation_fraction: float = 0.14) -> tuple[list[Example], list[Example]]:
    train: list[Example] = []
    validation: list[Example] = []
    for intent in INTENTS:
        group = [example for example in examples if example.intent == intent]
        validation_size = max(1, round(len(group) * validation_fraction))
        validation.extend(group[:validation_size])
        train.extend(group[validation_size:])
    random.Random(SEED + 1).shuffle(train)
    random.Random(SEED + 2).shuffle(validation)
    return train, validation


def resolve_device(requested: str) -> torch.device:
    if requested == "cuda":
        if not torch.cuda.is_available():
            raise SystemExit("CUDA requested but unavailable; refusing silent CPU training")
        return torch.device("cuda")
    if requested == "cpu":
        return torch.device("cpu")
    return torch.device("cuda" if torch.cuda.is_available() else "cpu")


def export_model(model, tokenizer, output: Path):
    output.mkdir(parents=True, exist_ok=True)
    model.eval().cpu()
    dummy = tokenizer(
        "напомни завтра позвонить врачу",
        max_length=MAX_LENGTH,
        padding="max_length",
        truncation=True,
        return_tensors="pt",
    )
    token_type_ids = dummy.get("token_type_ids", torch.zeros_like(dummy["input_ids"]))
    fp32_path = output / "classic_nlu.onnx"
    torch.onnx.export(
        model,
        (dummy["input_ids"], dummy["attention_mask"], token_type_ids),
        fp32_path,
        input_names=["input_ids", "attention_mask", "token_type_ids"],
        output_names=["intent_logits", "slot_logits"],
        dynamic_axes={
            "input_ids": {0: "batch"}, "attention_mask": {0: "batch"}, "token_type_ids": {0: "batch"},
            "intent_logits": {0: "batch"}, "slot_logits": {0: "batch"},
        },
        opset_version=17,
        dynamo=False,
    )
    from onnxruntime.quantization import QuantType, quantize_dynamic
    int8_path = output / "classic_nlu_int8.onnx"
    quantize_dynamic(fp32_path, int8_path, weight_type=QuantType.QInt8)

    vocab = tokenizer.get_vocab()
    ordered_vocab = [token for token, _ in sorted(vocab.items(), key=lambda pair: pair[1])]
    (output / "vocab.txt").write_text("\n".join(ordered_vocab), encoding="utf-8")
    metadata = {
        "base_model": BASE_MODEL,
        "max_length": MAX_LENGTH,
        "do_lower_case": bool(getattr(tokenizer, "do_lower_case", False)),
        "intent_labels": INTENTS,
        "slot_labels": SLOT_LABELS,
        "intent_threshold": 0.72,
        "intent_thresholds": {"create_reminder": 0.65},
        "explicit_destructive_intent_threshold": 0.50,
        "destructive_intent_threshold": 0.90,
    }
    (output / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    return fp32_path, int8_path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("tools/nlu/output"))
    parser.add_argument("--assets", type=Path, default=Path("android/app/src/main/assets/nlu"))
    parser.add_argument("--epochs", type=int, default=6)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--per-intent", type=int, default=800)
    parser.add_argument("--unknown-count", type=int, default=850)
    parser.add_argument("--device", choices=("cuda", "cpu", "auto"), default="cuda")
    args = parser.parse_args()

    random.seed(SEED)
    np.random.seed(SEED)
    torch.manual_seed(SEED)
    torch.cuda.manual_seed_all(SEED)
    examples = build_training_examples(per_intent=args.per_intent, unknown_count=args.unknown_count)
    train_examples, validation_examples = stratified_split(examples)
    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, use_fast=True)
    train_loader = DataLoader(NluDataset(train_examples, tokenizer), batch_size=args.batch_size, shuffle=True)
    validation_loader = DataLoader(NluDataset(validation_examples, tokenizer), batch_size=args.batch_size)

    device = resolve_device(args.device)
    model = JointNluModel(BASE_MODEL, len(INTENTS), len(SLOT_LABELS)).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=3e-5, weight_decay=0.01)
    counts = Counter(example.intent for example in train_examples)
    largest_class = max(counts.values())
    class_weights = torch.tensor(
        [(largest_class / counts[intent]) ** 0.5 for intent in INTENTS],
        dtype=torch.float32,
        device=device,
    )
    intent_loss = nn.CrossEntropyLoss(weight=class_weights)
    slot_loss = nn.CrossEntropyLoss(ignore_index=-100)
    device_name = torch.cuda.get_device_name(device) if device.type == "cuda" else "CPU"
    print(
        f"examples={len(examples)} train={len(train_examples)} validation={len(validation_examples)} "
        f"device={device} device_name={device_name} classes={dict(sorted(counts.items()))}"
    )
    best_score = -1.0
    best_state = None
    best_metrics = None
    for epoch in range(args.epochs):
        model.train()
        total_loss = 0.0
        for batch in train_loader:
            optimizer.zero_grad(set_to_none=True)
            intent_logits, slot_logits = model(
                batch["input_ids"].to(device),
                batch["attention_mask"].to(device),
                batch["token_type_ids"].to(device),
            )
            loss = intent_loss(intent_logits, batch["intent_labels"].to(device)) + slot_loss(
                slot_logits.reshape(-1, len(SLOT_LABELS)), batch["slot_labels"].to(device).reshape(-1)
            )
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            optimizer.step()
            total_loss += loss.item()
        metrics = validation_metrics(model, validation_loader, device)
        score = metrics["intent_macro_f1"] + metrics["entity_exact_match"]
        if score > best_score:
            best_score = score
            best_state = copy.deepcopy(model.state_dict())
            best_metrics = metrics
        print(
            f"epoch={epoch + 1} loss={total_loss / len(train_loader):.4f} "
            f"intent_acc={metrics['intent_accuracy']:.4f} intent_macro_f1={metrics['intent_macro_f1']:.4f} "
            f"slot_macro_f1={metrics['slot_macro_f1']:.4f} entity_exact={metrics['entity_exact_match']:.4f}"
        )

    if best_state is None or best_metrics is None:
        raise SystemExit("Training produced no checkpoint")
    model.load_state_dict(best_state)

    args.output.mkdir(parents=True, exist_ok=True)
    torch.save(model.state_dict(), args.output / "classic_nlu.pt")
    training_report = {
        "device": str(device),
        "device_name": device_name,
        "examples": len(examples),
        "train_examples": len(train_examples),
        "validation_examples": len(validation_examples),
        "class_counts": dict(sorted(Counter(example.intent for example in examples).items())),
        "best_validation": best_metrics,
    }
    (args.output / "training_report.json").write_text(
        json.dumps(training_report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    fp32_path, int8_path = export_model(model, tokenizer, args.output)
    args.assets.mkdir(parents=True, exist_ok=True)
    for name in ("classic_nlu_int8.onnx", "vocab.txt", "metadata.json"):
        shutil.copy2(args.output / name, args.assets / name)
    print(f"exported_fp32={fp32_path} bytes={fp32_path.stat().st_size}")
    print(f"exported_int8={int8_path} bytes={int8_path.stat().st_size}")


if __name__ == "__main__":
    main()
