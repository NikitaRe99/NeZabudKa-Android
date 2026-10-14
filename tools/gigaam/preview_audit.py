"""Replay increasing real-audio prefixes; verify final inference after cancellation."""
import json
import threading
import time
import numpy as np
import onnxruntime as ort
from benchmark import ROOT, read_audio
from export_android import decode

cache = ROOT / ".gigaam-cache"
options = ort.SessionOptions()
options.intra_op_num_threads = 4
session = ort.InferenceSession(str(cache / "publish/gigaam-v3-ctc-int8.onnx"), options, providers=["CPUExecutionProvider"])
vocab = json.loads(session.get_modelmeta().custom_metadata_map["nezabudka.vocabulary"])
corpus = json.loads((cache / "corpus.json").read_text(encoding="utf-8"))
reference = {r["id"]: r["text"] for r in json.loads((cache / "v3_ctc-desktop.json").read_text(encoding="utf-8"))["cases"]}
report = []
for case in corpus:
    if case["group"] != "primary":
        continue
    audio = read_audio(ROOT / case["path"]).numpy()
    previews = []
    for end in range(12800, audio.shape[1], 16000):
        started = time.perf_counter()
        labels, length = session.run(None, {"audio": audio[:, :end].copy(), "length": np.array([end], dtype=np.int64)})
        previews.append({"seconds": end / 16000, "text": decode(labels, length, vocab), "ms": (time.perf_counter() - started) * 1000})
    # Abort a real native run, then prove a fresh RunOptions still gives the full text.
    run = ort.RunOptions()
    timer = threading.Timer(0.001, lambda: setattr(run, "terminate", True))
    timer.start()
    aborted = False
    try:
        session.run(None, {"audio": audio, "length": np.array([audio.shape[1]], dtype=np.int64)}, run)
    except Exception as error:
        if "terminate" not in str(error).lower():
            raise
        aborted = True
    finally:
        timer.cancel()
        timer.join()
    labels, length = session.run(None, {"audio": audio, "length": np.array([audio.shape[1]], dtype=np.int64)})
    final = decode(labels, length, vocab)
    assert final == reference[case["id"]], (case["id"], final)
    report.append({"id": case["id"], "previews": previews, "aborted": aborted, "final": final, "unchangedFinal": True})
destination = ROOT / "docs/gigaam/preview-audit-0.2.82.json"
destination.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"{len(report)} recordings: final text unchanged after prefix inference and native cancellation")
