"""Create flat, hashed inputs for the isolated Android audit package."""
import json
import shutil
from benchmark import ROOT

cache = ROOT / ".gigaam-cache"
destination = cache / "device"
destination.mkdir(exist_ok=True)
corpus = json.loads((cache / "corpus.json").read_text(encoding="utf-8"))
for case in corpus:
    case["file"] = case["id"] + ".wav"
    shutil.copyfile(ROOT / case["path"], destination / case["file"])
(destination / "corpus.json").write_text(json.dumps(corpus, ensure_ascii=False, indent=2), encoding="utf-8")
metadata = json.loads((cache / "metadata.json").read_text(encoding="utf-8"))
validation = json.loads((cache / "onnx-validation.json").read_text(encoding="utf-8"))
metadata["hashes"] = {item["file"]: item["sha256"] for item in validation["variants"]}
(destination / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"Prepared {len(corpus)} recordings; no main app data access needed")
