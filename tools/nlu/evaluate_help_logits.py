"""Read-only frozen help evaluation. Uses token IDs from the production Kotlin tokenizer.
Does not train, rewrite models, or access phone data.
"""
import hashlib
import json
from pathlib import Path
import numpy as np
import onnxruntime as ort

root = Path(__file__).resolve().parents[2]
source = root / "android/app/build/tmp/nlu-help-tokens.json"
models = {
    "v7": root / "tools/nlu/output/recovery-v7/classic_nlu_int8.onnx",
    "v8": root / "tools/nlu/output/accuracy-v8-retained/classic_nlu_v8_int8.onnx",
}
rows = json.loads(source.read_text(encoding="utf-8"))
result = []
for version, path in models.items():
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    options = ort.SessionOptions()
    options.intra_op_num_threads = 4
    options.inter_op_num_threads = 1
    session = ort.InferenceSession(str(path), options, providers=["CPUExecutionProvider"])
    for start in range(0, len(rows), 4):
        batch = rows[start:start+4]
        output = session.run(["intent_logits", "slot_logits"], {
            name: np.asarray([row[name] for row in batch], dtype=np.int64)
            for name in ["input_ids", "attention_mask", "token_type_ids"]
        })
        for index, row in enumerate(batch):
            result.append(dict(row, model=version, model_sha256=digest,
                intent_logits=output[0][index].tolist(), slot_logits=output[1][index].tolist()))
    print(version, digest, "evaluated", len(rows), "phrases")
(root / "android/app/build/tmp/nlu-help-logits.json").write_text(
    json.dumps(result, ensure_ascii=False), encoding="utf-8")
