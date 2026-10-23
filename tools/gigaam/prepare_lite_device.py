"""Stage flat, hashed fixtures for the isolated Android benchmark (no app data)."""
import hashlib
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / ".gigaam-cache"
OUT = CACHE / "lite/device"
OUT.mkdir(parents=True, exist_ok=True)
corpus = json.loads((CACHE / "corpus.json").read_text(encoding="utf-8"))
fleurs = json.loads((CACHE / "lite/fleurs/test/manifest.json").read_text(encoding="utf-8"))
corpus += [{**entry, "group": "fleurs-heldout"} for entry in fleurs[48:56]]
for entry in corpus:
    entry["file"] = entry["id"] + ".wav"
    shutil.copyfile(ROOT / entry["path"], OUT / entry["file"])
models = [CACHE / "publish/gigaam-v3-ctc-int8.onnx", CACHE / "lite/gigaam-v3-ctc-lite-pointwise-int8.onnx"]
metadata = json.loads((CACHE / "metadata.json").read_text(encoding="utf-8"))
metadata["hashes"] = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in models}
(OUT / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
(OUT / "corpus.json").write_text(json.dumps(corpus, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"Prepared {len(corpus)} recordings and two model hashes")
