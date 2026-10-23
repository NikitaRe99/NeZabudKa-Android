"""Combine immutable model hashes, independent accuracy checks and ABBA device runs."""
import json
from pathlib import Path
import runpy
import statistics

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".gigaam-cache/lite"
SCORING = runpy.run_path(str(ROOT / "tools/whisper/score-audit.py"))


def read(name):
    return json.loads((OUT / name).read_text(encoding="utf-8-sig"))


def main():
    report = {"accuracy": {}, "device": {}, "protocol": "Samsung S9 Android 10; CPU EP, 4 threads, spin=0; fresh processes in baseline/lite/lite/baseline order; 19 recordings, 2 runs each"}
    corpus = {case["id"]: case for case in read("device/corpus.json")}
    for variant, stem in [("baseline", "gigaam-v3-ctc-int8"), ("lite", "gigaam-v3-ctc-lite-pointwise-int8")]:
        development = read(stem + ".development.json")
        heldout = read(stem + ".heldout.json")
        assert development["sha256"] == heldout["sha256"]
        groups = {**development["groups"], **heldout["groups"]}
        words = groups["fleurs-development"]["words"] + groups["fleurs-heldout"]["words"]
        errors = groups["fleurs-development"]["errors"] + groups["fleurs-heldout"]["errors"]
        report["accuracy"][variant] = {"sha256": development["sha256"], "bytes": development["bytes"],
            "groups": groups, "fleursTotalWords": words, "fleursTotalErrors": errors, "fleursWerPercent": 100 * errors / words}
        rounds = [read(f"device-{variant}-round{number}.json") for number in (1, 2)]
        assert all(r["status"] == "complete" and len(r["cases"]) == 19 for r in rounds)
        assert all(r["sha256"] == development["sha256"] for r in rounds)
        rows = [run for r in rounds for case in r["cases"] for run in case["runs"]]
        reference_texts = {case["id"]: case["runs"][0]["text"] for case in rounds[0]["cases"]}
        assert all(run["text"] == reference_texts[case["id"]] for r in rounds for case in r["cases"] for run in case["runs"])
        device_accuracy = {}
        for case in rounds[0]["cases"]:
            entry = corpus[case["id"]]
            group = device_accuracy.setdefault(entry["group"], {"words": 0, "errors": 0})
            words = SCORING["words"](entry["expected"])
            group["words"] += len(words)
            group["errors"] += SCORING["distance"](words, SCORING["words"](case["runs"][0]["text"]))
        report["device"][variant] = {"device": rounds[0]["device"], "hardware": rounds[0]["hardware"],
            "inferences": len(rows), "medianMs": statistics.median(row["ms"] for row in rows),
            "sumMs": sum(row["ms"] for row in rows), "sumCpuMs": sum(row["cpuMs"] for row in rows),
            "meanLoadMs": statistics.mean(r["loadMs"] for r in rounds),
            "meanPssAfterInferenceMiB": statistics.mean(r["pssAfterInferenceKiB"] / 1024 for r in rounds),
            "maxPeakRssMiB": max(r["peakRssKiB"] / 1024 for r in rounds), "accuracy": device_accuracy}
    for key in ("bytes",):
        report["sizeReductionPercent"] = 100 * (1 - report["accuracy"]["lite"][key] / report["accuracy"]["baseline"][key])
    base = report["device"]["baseline"]
    lite = report["device"]["lite"]
    report["deviceChangesPercent"] = {"totalInferenceTimeReduction": 100 * (1 - lite["sumMs"] / base["sumMs"]),
        "cpuTimeReduction": 100 * (1 - lite["sumCpuMs"] / base["sumCpuMs"]),
        "steadyPssReduction": 100 * (1 - lite["meanPssAfterInferenceMiB"] / base["meanPssAfterInferenceMiB"])}
    report["decision"] = "experimental opt-in catalog release authorized; held-out aggregate WER non-regression gate failed by one word; do not replace recommended default"
    target = ROOT / "docs/gigaam/lite-results-2026-10-23.json"
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
