"""Frozen, read-only evaluation. Never imports these cases into training."""
import argparse
import hashlib
import json
from pathlib import Path

import onnxruntime as ort
from transformers import AutoTokenizer
from evaluate_nlu import predict_batch
from train_nlu import BASE_MODEL

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("--cases", type=Path, default=root / "docs/nlu/command-set-2026-10-12.json")
parser.add_argument("--model-dir", type=Path, default=root / "tools/nlu/output/recovery-v7")
parser.add_argument("--now", default="2026-10-12T12:00")
args = parser.parse_args()
cases = json.loads(args.cases.read_text(encoding="utf-8"))
for case in cases:
    case.setdefault("scope", "assistant")
model = args.model_dir / "classic_nlu_int8.onnx"
metadata = json.loads((args.model_dir / "metadata.json").read_text(encoding="utf-8"))
tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, local_files_only=True)
session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])
results = predict_batch(session, tokenizer, metadata, cases)
rows = [{"corpus": args.cases.name, "case": case, "now": args.now,
         "intent": intent, "confidence": confidence, "slots": slots, "raw_intent": raw}
        for case, (intent, confidence, slots, raw) in zip(cases, results)]
(args.model_dir / "fresh_commands.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
print("Model SHA-256:", hashlib.sha256(model.read_bytes()).hexdigest())
print("Raw intent correct:", sum(c["intent"] == r[3] for c, r in zip(cases, results)), "/", len(cases))
print("Run FreshOfflineCommandSetTest with NLU_NEW_SET_EVALUATION pointing to fresh_commands.json")
