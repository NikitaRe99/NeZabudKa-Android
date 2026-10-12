"""Export parity and final corpus audit; never used to choose a checkpoint."""
import hashlib
import argparse
import json
from pathlib import Path
import sys
import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer
from train_nlu import BASE_MODEL, MAX_LENGTH, template_group
from dataset import Example

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("--output", type=Path, default=root / "tools/nlu/output/recovery-v6")
args = parser.parse_args()
output = args.output
tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, local_files_only=True)
resources = root / "android/app/src/test/resources/llm"
cases = json.loads((resources / "nlu_frozen_v6.json").read_text(encoding="utf-8"))
train = json.loads((output / "training_manifest.json").read_text(encoding="utf-8"))
validation = json.loads((output / "validation_manifest.json").read_text(encoding="utf-8"))
options = ort.SessionOptions()
options.intra_op_num_threads = 4
fp32 = ort.InferenceSession(str(output / "classic_nlu.onnx"), options, providers=["CPUExecutionProvider"])
int8 = ort.InferenceSession(str(output / "classic_nlu_int8.onnx"), options, providers=["CPUExecutionProvider"])
# Quantization checks use validation only, leaving frozen final cases for Android evaluation.
texts = [e["text"] for e in validation]
intent_equal = slot_equal = tokens = 0
max_error = 0.0
for begin in range(0, len(texts), 32):
    batch = tokenizer(texts[begin:begin + 32], max_length=MAX_LENGTH, padding="max_length", truncation=True, return_tensors="np")
    inputs = {name: batch[name].astype(np.int64) for name in ("input_ids", "attention_mask", "token_type_ids")}
    a = fp32.run(None, inputs)
    b = int8.run(None, inputs)
    intent_equal += int((a[0].argmax(-1) == b[0].argmax(-1)).sum())
    mask = inputs["attention_mask"].astype(bool)
    slot_equal += int(((a[1].argmax(-1) == b[1].argmax(-1)) & mask).sum())
    tokens += int(mask.sum())
    max_error = max(max_error, float(np.max(np.abs(a[0] - b[0]))))
report = {"validation_count": len(texts), "quantized_intent_agreement": intent_equal / len(texts), "quantized_slot_token_agreement": slot_equal / tokens, "intent_logit_max_abs_error": max_error}
known_phrases = {" ".join(e["text"].lower().split()).strip(" .?!") for e in train + validation}
report["frozen_cases"] = len(cases)
report["frozen_exact_phrase_overlaps"] = [c["utterance"] for c in cases if " ".join(c["utterance"].lower().split()).strip(" .?!") in known_phrases]
known_payloads = {e["text"][a:b].strip().casefold() for e in train + validation for a, b, slot in e["spans"] if slot in ("TEXT", "FOLDER", "TARGET")}
report["frozen_payload_overlaps"] = sorted({c[k].casefold() for c in cases for k in ("text", "folder", "target") if k in c and c[k].casefold() in known_payloads})
for name in ("classic_nlu_int8.onnx", "classic_nlu.onnx", "vocab.txt", "metadata.json"):
    report[name] = {"sha256": hashlib.sha256((output / name).read_bytes()).hexdigest(), "bytes": (output / name).stat().st_size}
report["frozen_sha256"] = hashlib.sha256((resources / "nlu_frozen_v6.json").read_bytes()).hexdigest()
(output / "export_verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(report, ensure_ascii=False, indent=2), flush=True)
assert not report["frozen_exact_phrase_overlaps"]
assert not report["frozen_payload_overlaps"]
assert report["quantized_intent_agreement"] >= .99
