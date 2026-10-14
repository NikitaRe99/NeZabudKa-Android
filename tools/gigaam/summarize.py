"""Score saved reports without altering recognition text or expected references."""
import json
import statistics
from benchmark import ROOT, SCORING

cache = ROOT / ".gigaam-cache"
corpus = json.loads((cache / "corpus.json").read_text(encoding="utf-8"))
reference = {case["id"]: case for case in corpus}
desktop = json.loads((cache / "v3_ctc-desktop.json").read_text(encoding="utf-8"))
official = {case["id"]: case["text"] for case in desktop["cases"]}
reports = []
for file in sorted(cache.glob("v3_ctc-waveform-*.onnx.threads*.json")):
    report = json.loads(file.read_text(encoding="utf-8"))
    if report.get("status") != "complete":
        print("Excluded incomplete:", file.name)
        continue
    assert len(report["cases"]) == len(corpus)
    errors, times, cases = 0, [], []
    for case in report["cases"]:
        texts = {run["text"] for run in case["runs"]}
        assert len(texts) == 1, case["id"]
        text = texts.pop()
        item = reference[case["id"]]
        error = SCORING["distance"](SCORING["words"](item["expected"]), SCORING["words"](text))
        durations = [run["ms"] for run in case["runs"]]
        cases.append({"id": case["id"], "expected": item["expected"], "text": text,
            "wordErrors": error, "sameAsOfficial": text == official[case["id"]], "ms": durations})
        if item["group"] == "primary":
            errors += error
            times.extend(durations)
    reports.append({"source": file.name, "device": report["device"], "sha256": report["sha256"],
        "modelBytes": report["modelBytes"], "threads": report["threads"], "loadMs": report["loadMs"],
        "pssAfterLoadKiB": report["pssAfterLoadKiB"], "pssAfterInferenceKiB": report["pssAfterInferenceKiB"],
        "summary": {"primaryCases": 9, "referenceWords": 57, "wordErrors": errors,
            "minMs": min(times), "maxMs": max(times), "medianMs": statistics.median(times)}, "cases": cases})
destination = ROOT / "docs/gigaam"
destination.mkdir(exist_ok=True)
(destination / "android-results-2026-10-14.json").write_text(json.dumps(reports, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps([{**r["summary"], "source": r["source"], "loadMs": r["loadMs"],
    "pssMiB": r["pssAfterInferenceKiB"] / 1024} for r in reports], indent=2))
