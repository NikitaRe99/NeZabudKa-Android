"""Summarize decoder-only audit results; a per-case regression rejects a candidate."""
import argparse
import json
import re
import statistics
from pathlib import Path

def words(text):
    tokens = re.findall(r"[^\W_]+", text.lower().replace("ё", "е"))
    units = "ноль один два три четыре пять шесть семь восемь девять десять одиннадцать двенадцать тринадцать четырнадцать пятнадцать шестнадцать семнадцать восемнадцать девятнадцать".split()
    tens = {20: "двадцать", 30: "тридцать", 40: "сорок", 50: "пятьдесят"}
    result = []
    for token in tokens:
        # Equivalent clock notations (10:25 / десять двадцать пять) should not
        # count as different words. No phonetic or grammatical substitutions.
        if token.isdigit() and 0 <= int(token) < 60:
            n = int(token)
            result.extend([units[n]] if n < 20 else [tens[n // 10 * 10]] + ([units[n % 10]] if n % 10 else []))
        else:
            result.append(token)
    return result

def distance(reference, actual):
    previous = list(range(len(actual) + 1))
    for i, a in enumerate(reference, 1):
        current = [i]
        for j, b in enumerate(actual, 1):
            current.append(min(current[-1] + 1, previous[j] + 1, previous[j - 1] + (a != b)))
        previous = current
    return previous[-1]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    data = json.loads(args.input.read_text(encoding="utf-8-sig"))
    baseline = {r["id"]: r for r in data if r["variant"] == 0}
    groups = {}
    for row in data:
        row["wordErrors"] = distance(words(row["expected"]), words(row["text"]))
        row["referenceWords"] = len(words(row["expected"]))
        row["commas"] = row["text"].count(",")
        groups.setdefault(row["variant"], []).append(row)
    summary = []
    for variant, rows in groups.items():
        regressions = [r["id"] for r in rows if r["wordErrors"] > baseline[r["id"]]["wordErrors"]]
        summary.append({"variant": variant, "cases": len(rows),
            "wordErrors": sum(r["wordErrors"] for r in rows),
            "referenceWords": sum(r["referenceWords"] for r in rows),
            "medianDecodeMs": statistics.median(r["ms"] for r in rows),
            "regressions": regressions,
            "timeoutsOrErrors": sum(r["status"] != 0 for r in rows)})
    report = {"scope": "Raw first decode; excludes VAD, Kotlin retries, transcript normalization and punctuation model",
        "limitations": "Small known regression set, not a blind accuracy estimate; timings vary with device load/temperature",
        "summary": summary, "cases": data}
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()
