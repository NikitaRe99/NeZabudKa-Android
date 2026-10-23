"""Small reproducible, disjoint FLEURS calibration/evaluation subsets (CC BY 4.0).

Stream only the beginning of each archive; never extract archive paths to disk.
Audio/transcripts are local ignored artifacts, not application assets.
"""
import argparse
import csv
import hashlib
import io
import json
from pathlib import Path
import tarfile
import urllib.request

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".gigaam-cache/lite"
BASE = "https://huggingface.co/datasets/google/fleurs/resolve/main/data/ru_ru"


def prepare(split, count, forbidden_ids):
    destination = OUT / "fleurs" / split
    destination.mkdir(parents=True, exist_ok=True)
    manifest = destination / "manifest.json"
    if manifest.exists():
        entries = json.loads(manifest.read_text(encoding="utf-8"))
        assert len(entries) == count
        assert not ({e["promptId"] for e in entries} & forbidden_ids)
        for entry in entries:
            assert hashlib.sha256((ROOT / entry["path"]).read_bytes()).hexdigest() == entry["sha256"]
        return entries
    with urllib.request.urlopen(BASE + f"/{split}.tsv", timeout=60) as response:
        raw = response.read()
    (destination / "source.tsv").write_bytes(raw)
    rows = {row[1]: row for row in csv.reader(io.StringIO(raw.decode("utf-8")), delimiter="\t")}
    entries = []
    # Use different text IDs in calibration/test as well as the official split.
    seen = set(forbidden_ids)
    with urllib.request.urlopen(BASE + f"/audio/{split}.tar.gz?download=true", timeout=60) as response:
        with tarfile.open(fileobj=response, mode="r|gz") as archive:
            for member in archive:
                name = Path(member.name).name
                if not member.isfile() or name not in rows:
                    continue
                row = rows[name]
                prompt = int(row[0])
                if prompt in seen:
                    continue
                # Calibration covers varying lengths; cap only selection, never truncate speech.
                seconds = int(row[5]) / 16_000
                if not 2 <= seconds <= 18:
                    continue
                with archive.extractfile(member) as stream:
                    audio, rate = sf.read(io.BytesIO(stream.read()), dtype="float32")
                assert rate == 16_000 and audio.ndim == 1
                assert np.isfinite(audio).all()
                target = destination / name
                sf.write(target, audio, rate, subtype="PCM_16")
                entries.append({"id": f"fleurs-{split}-{prompt}", "promptId": prompt,
                    "path": target.relative_to(ROOT).as_posix(), "expected": row[3],
                    "gender": row[6], "seconds": len(audio) / rate,
                    "sha256": hashlib.sha256(target.read_bytes()).hexdigest()})
                seen.add(prompt)
                if len(entries) == count:
                    break
    assert len(entries) == count
    manifest.write_text(json.dumps(entries, ensure_ascii=False, indent=2), encoding="utf-8")
    return entries


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--calibration", type=int, default=64)
    parser.add_argument("--evaluation", type=int, default=96)
    args = parser.parse_args()
    train = prepare("train", args.calibration, set())
    print(f"Calibration: {len(train)} recordings, {sum(e['seconds'] for e in train):.1f}s", flush=True)
    test = prepare("test", args.evaluation, {e["promptId"] for e in train})
    print(f"Evaluation: {len(test)} recordings, {sum(e['seconds'] for e in test):.1f}s", flush=True)
    (OUT / "DATA_SOURCE.md").write_text(
        "# FLEURS Russian\n\nSource: https://huggingface.co/datasets/google/fleurs\n"
        "License: CC BY 4.0. Google FLEURS, Conneau et al., 2022.\n"
        "https://arxiv.org/abs/2205.12446\n\n"
        "Separate train/test splits, disjoint prompt IDs. WAV converted to mono PCM16 16kHz.\n"
        "Selection follows archive order, durations 2–18 seconds; no truncation.\n", encoding="utf-8")


if __name__ == "__main__":
    main()
