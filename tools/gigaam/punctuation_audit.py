"""Run the fixed punctuation corpus with token IDs produced by the app tokenizer."""
import hashlib
import json
import numpy as np
import onnxruntime as ort
from benchmark import ROOT

path = ROOT / "android/app/build/punctuation-research/rupunct_small_int8.onnx"
assert hashlib.file_digest(path.open("rb"), "sha256").hexdigest() == "b105da023474d98aa13ba18953ae67b04b17bd0595034bc06030c17536893933"
options = ort.SessionOptions()
options.intra_op_num_threads = 4
session = ort.InferenceSession(str(path), options, providers=["CPUExecutionProvider"])
rows = json.loads((ROOT / ".gigaam-cache/punct-tokens.json").read_text(encoding="utf-8"))
for row in rows:
    row["logits"] = session.run(None, {name: np.asarray([row[name]], dtype=np.int64)
        for name in ("input_ids", "attention_mask", "token_type_ids")})[0][0].tolist()
(ROOT / ".gigaam-cache/punct-logits.json").write_text(json.dumps(rows, ensure_ascii=False), encoding="utf-8")
print(f"Evaluated {len(rows)} fixed texts using unchanged RUPunct INT8")
