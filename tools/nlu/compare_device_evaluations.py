"""Compare identical read-only Android evaluation reports including extracted fields."""
import argparse
import json
import re
from pathlib import Path
from collections import Counter

def normalized(value):
    return re.sub(r"\s+", " ", str(value).casefold().strip(' «»".,:;!?—–-')) if value is not None else None

def differences(row):
    case = row["case"]
    errors = []
    if row["actualIntent"] != case["intent"]:
        errors.append("intent")
    for key, output in (("text", "payload"), ("target", "payload"), ("folder", "folder"), ("remind_at", "remindAt"), ("period_start", "periodStart"), ("period_end", "periodEnd"), ("recurrence", "recurrence")):
        if key in case and normalized(case[key]) != normalized(row.get(output)):
            errors.append(key)
    return errors

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("old", type=Path)
    parser.add_argument("new", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    old = json.loads(args.old.read_text(encoding="utf-8"))
    new = json.loads(args.new.read_text(encoding="utf-8"))
    old_by_text = {(r["corpus"], r["case"]["utterance"]): r for r in old}
    report = {"total": len(new), "old_correct": sum(not differences(r) for r in old), "new_correct": sum(not differences(r) for r in new), "fixed": [], "regressions": [], "remaining": []}
    for row in new:
        prior = old_by_text.get((row["corpus"], row["case"]["utterance"]))
        errors = differences(row)
        if errors: report["remaining"].append({**row, "differences": errors})
        if prior is not None:
            if differences(prior) and not errors: report["fixed"].append(row)
            if not differences(prior) and errors: report["regressions"].append(row)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print({k: len(v) if isinstance(v, list) else v for k, v in report.items()}, flush=True)
    print(Counter((row["corpus"], not differences(row)) for row in new), flush=True)
