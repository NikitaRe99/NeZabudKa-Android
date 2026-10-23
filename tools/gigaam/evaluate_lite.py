"""Compare raw CTC results on development/held-out FLEURS and frozen controls.

No fitting/calibration happens here. Keep the second half sealed until choosing
the candidate. Acceptance: no extra errors on primary controls, empty silence,
and no aggregate WER regression on either FLEURS subset.
"""
import argparse
import hashlib
import json
from pathlib import Path
import runpy
import statistics
import time

import numpy as np
import onnxruntime as ort
import psutil
import soundfile as sf

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".gigaam-cache/lite"
SCORING = runpy.run_path(str(ROOT / "tools/whisper/score-audit.py"))


def decode(labels, length, vocabulary):
    previous = -1
    text = []
    for token in labels[0, :int(length[0])]:
        token = int(token)
        if token != len(vocabulary) and token != previous:
            text.append(vocabulary[token])
        previous = token
    return "".join(text)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--phase", choices=["development", "heldout"], default="development")
    parser.add_argument("--threads", type=int, default=4)
    args = parser.parse_args()
    manifest = json.loads((OUT / "fleurs/test/manifest.json").read_text(encoding="utf-8"))
    assert len(manifest) == 96
    entries = manifest[:48] if args.phase == "development" else manifest[48:]
    if args.phase == "development":
        controls = json.loads((ROOT / ".gigaam-cache/corpus.json").read_text(encoding="utf-8"))
        entries = [{**e, "group": "fleurs-development"} for e in entries] + controls
    else:
        entries = [{**e, "group": "fleurs-heldout"} for e in entries]
    options = ort.SessionOptions()
    options.intra_op_num_threads = args.threads
    options.inter_op_num_threads = 1
    options.add_session_config_entry("session.intra_op.allow_spinning", "0")
    options.add_session_config_entry("session.inter_op.allow_spinning", "0")
    started = time.perf_counter()
    session = ort.InferenceSession(str(args.model), options, providers=["CPUExecutionProvider"])
    loaded_ms = (time.perf_counter() - started) * 1000
    vocabulary = json.loads(session.get_modelmeta().custom_metadata_map["nezabudka.vocabulary"])
    process = psutil.Process()
    rows = []
    rss_after_load = process.memory_info().rss
    for index, entry in enumerate(entries):
        path = ROOT / entry["path"]
        assert hashlib.sha256(path.read_bytes()).hexdigest() == entry["sha256"]
        audio, rate = sf.read(path, dtype="float32")
        assert rate == 16_000 and audio.ndim == 1
        started = time.perf_counter()
        labels, length = session.run(None, {"audio": audio[None, :], "length": np.array([len(audio)], dtype=np.int64)})
        elapsed = (time.perf_counter() - started) * 1000
        text = decode(labels, length, vocabulary)
        expected = SCORING["words"](entry["expected"])
        rows.append({"id": entry["id"], "group": entry["group"], "expected": entry["expected"], "text": text,
            "words": len(expected), "errors": SCORING["distance"](expected, SCORING["words"](text)),
            "ms": elapsed, "audioSeconds": len(audio) / rate})
        if index % 12 == 0:
            print(f"{args.model.name}: {index + 1}/{len(entries)}", flush=True)
    # Synthetic controls never count as extra independent speech recordings.
    if args.phase == "development":
        stress = [("silence-5s", np.zeros(80_000, dtype=np.float32), "")]
        for entry in entries:
            if entry["group"] != "primary":
                continue
            audio, _ = sf.read(ROOT / entry["path"], dtype="float32")
            stress.append((entry["id"] + "-quiet18dB", audio * np.float32(10 ** (-18 / 20)), entry["expected"]))
            stress.append((entry["id"] + "-padding", np.pad(audio, (16_000, 16_000)), entry["expected"]))
        for name, audio, expected_text in stress:
            labels, length = session.run(None, {"audio": audio[None, :], "length": np.array([len(audio)], dtype=np.int64)})
            text = decode(labels, length, vocabulary)
            expected = SCORING["words"](expected_text)
            rows.append({"id": name, "group": "synthetic", "expected": expected_text, "text": text,
                "words": len(expected), "errors": SCORING["distance"](expected, SCORING["words"](text))})
    groups = {}
    for group in sorted({r["group"] for r in rows}):
        subset = [r for r in rows if r["group"] == group]
        word_count = sum(r["words"] for r in subset)
        errors = sum(r["errors"] for r in subset)
        timings = [r["ms"] for r in subset if "ms" in r]
        groups[group] = {"recordings": len(subset), "words": word_count, "errors": errors,
            "werPercent": 100 * errors / word_count if word_count else None,
            "medianMs": statistics.median(timings) if timings else None}
    report = {"model": args.model.name, "sha256": hashlib.sha256(args.model.read_bytes()).hexdigest(),
        "bytes": args.model.stat().st_size, "phase": args.phase, "threads": args.threads,
        "loadMs": loaded_ms, "rssAfterLoadBytes": rss_after_load, "rssAfterInferenceBytes": process.memory_info().rss,
        "groups": groups, "cases": rows}
    destination = OUT / f"{args.model.stem}.{args.phase}.json"
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"model": args.model.name, "groups": groups}), flush=True)


if __name__ == "__main__":
    main()
