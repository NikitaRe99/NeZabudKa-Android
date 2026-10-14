"""Fixed synthetic controls, not new independent speech recordings or training."""
import json
import numpy as np
import onnxruntime as ort
from benchmark import ROOT, SCORING, read_audio
from export_android import decode

cache = ROOT / ".gigaam-cache"
corpus = json.loads((cache / "corpus.json").read_text(encoding="utf-8"))
vocab = json.loads((cache / "metadata.json").read_text(encoding="utf-8"))["vocabulary"]
options = ort.SessionOptions()
options.intra_op_num_threads = 6
options.inter_op_num_threads = 1
options.log_severity_level = 3
all_results = []
for variant in ["fp32", "int8"]:
    session = ort.InferenceSession(str(cache / f"v3_ctc-waveform-{variant}.onnx"), options, providers=["CPUExecutionProvider"])
    controls = [("silence-5s", np.zeros((1, 80000), dtype=np.float32), "")]
    for case in corpus:
        if case["group"] != "primary":
            continue
        audio = read_audio(ROOT / case["path"]).numpy()
        controls.append((case["id"] + "-quiet18dB", audio * np.float32(10 ** (-18 / 20)), case["expected"]))
        padded = np.pad(audio, ((0, 0), (16000, 16000)))
        controls.append((case["id"] + "-silence-padding", padded, case["expected"]))
    for name, audio, expected in controls:
        labels, length = session.run(None, {"audio": audio, "length": np.array([audio.shape[1]], dtype=np.int64)})
        text = decode(labels, length, vocab)
        errors = SCORING["distance"](SCORING["words"](expected), SCORING["words"](text))
        all_results.append({"variant": variant, "id": name, "expected": expected, "text": text, "errors": errors})
        print(variant, name, errors, text, flush=True)
    del session
(cache / "stress-validation.json").write_text(json.dumps(all_results, ensure_ascii=False, indent=2), encoding="utf-8")
