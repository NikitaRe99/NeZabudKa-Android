"""Build self-describing release assets, verify decode after adding metadata."""
import hashlib
import json
import shutil
import onnx
from benchmark import ROOT
from export_android import validate

cache = ROOT / ".gigaam-cache"
out = cache / "publish"
out.mkdir(exist_ok=True)
corpus = json.loads((cache / "corpus.json").read_text(encoding="utf-8"))
manifest = []
for version in (1, 2, 3):
    name = f"v{version}_ctc"
    source = cache if version == 3 else cache / name
    validation = json.loads((source / "onnx-validation.json").read_text(encoding="utf-8"))
    official = json.loads((cache / f"{name}-desktop.json").read_text(encoding="utf-8"))
    references = {c["id"]: c["text"] for c in official["cases"]}
    assert all(c["sameAsOfficial"] for v in validation["variants"] for c in v["cases"])
    metadata = json.loads((source / "metadata.json").read_text(encoding="utf-8"))
    path = out / f"gigaam-v{version}-ctc-int8.onnx"
    model = onnx.load(str(source / f"{name}-waveform-int8.onnx"))
    onnx.helper.set_model_props(model, {
        "nezabudka.format": "gigaam-waveform-ctc-1",
        "nezabudka.model": name,
        "nezabudka.vocabulary": json.dumps(metadata["vocabulary"], ensure_ascii=False),
        "nezabudka.sample_rate": "16000",
        "nezabudka.license": "MIT; salute-developers/GigaAM",
        "nezabudka.quantization": metadata["quantization"],
        "nezabudka.upstream_commit": "7447938d791c4f3e643386ee22c33777004293a5",
    })
    onnx.save(model, str(path))
    del model
    result = validate(path, corpus, references, metadata["vocabulary"])
    assert all(c["sameAsOfficial"] for c in result["cases"])
    manifest.append({"id": f"gigaam-v{version}", "version": version, **result,
        "checkpointSha256": official["checkpointSha256"], "primaryWordErrors": official["summary"]["wordErrors"]})
nlu = out / "classic_nlu_v8_int8.onnx"
shutil.copyfile(ROOT / "tools/nlu/output/accuracy-v8-retained/classic_nlu_v8_int8.onnx", nlu)
assert hashlib.file_digest(nlu.open("rb"), "sha256").hexdigest() == "5de0ba03db948763d5567a278e9cf0843e68c5181160a4cbe35f69926ec8e35f"
(out / "manifest.json").write_text(json.dumps({"gigaam": manifest, "nlu": {"file": nlu.name,
    "bytes": nlu.stat().st_size, "sha256": hashlib.file_digest(nlu.open("rb"), "sha256").hexdigest()}}, ensure_ascii=False, indent=2), encoding="utf-8")
license_text = (cache / "GigaAM/LICENSE").read_text(encoding="utf-8")
(out / "MODEL_LICENSES.md").write_text("# GigaAM v1/v2/v3 CTC\n\nИсточник: https://github.com/salute-developers/GigaAM\n\nПроизводные ONNX INT8 для НеЗабудьКа. Встроена обработка WAV mono 16 kHz.\nДинамически квантованы только линейные MatMul; frontend и свёртки FP32.\n\n" + license_text + "\n\n# NLU v8\n\nДообученная модель команд НеЗабудьКа на базе cointegrated/rubert-tiny2.\nИсходная модель: https://huggingface.co/cointegrated/rubert-tiny2 (MIT).\nМетки и токенизатор совместимы с приложением; сама модель не распознаёт звук.\n", encoding="utf-8")
print("Release assets ready", flush=True)
