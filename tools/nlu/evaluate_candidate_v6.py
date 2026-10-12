"""Actual ONNX predictions for the separately frozen command checks."""
import json
import argparse
from pathlib import Path
import onnxruntime as ort
from transformers import AutoTokenizer
from evaluate_nlu import predict_batch
from train_nlu import BASE_MODEL

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("--output", type=Path, default=root / "tools/nlu/output/recovery-v6")
parser.add_argument("--include-regression", action="store_true")
args = parser.parse_args()
output = args.output
tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, local_files_only=True)
metadata = json.loads((output / "metadata.json").read_text(encoding="utf-8"))
options = ort.SessionOptions()
options.intra_op_num_threads = 4
session = ort.InferenceSession(str(output / "classic_nlu_int8.onnx"), options, providers=["CPUExecutionProvider"])
rows = []
corpora = ["nlu_user_trial_v6.json", "nlu_frozen_v6.json", "nlu_final_unseen_v6.json"]
if args.include_regression:
    corpora += ["command_corpus_ru.json", "command_corpus_ru_extended.json", "nlu_heldout_evaluation.json", "nlu_new_commands.json"]
for corpus in corpora:
    cases = json.loads((root / "android/app/src/test/resources/llm" / corpus).read_text(encoding="utf-8"))
    for case in cases:
        case.setdefault("scope", "assistant")
    results = []
    for start in range(0, len(cases), 32):
        results.extend(predict_batch(session, tokenizer, metadata, cases[start:start + 32]))
    for case, (intent, confidence, slots, raw) in zip(cases, results):
        rows.append({"corpus": corpus, "case": case, "intent": intent, "confidence": confidence, "slots": slots, "raw_intent": raw})
    print(corpus, "raw intents", sum(c["intent"] == r[3] for c, r in zip(cases, results)), "/", len(cases), flush=True)
(output / "offline_commands.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
