"""Official GigaAM forward + greedy CTC, unchanged WAV and no prompt/NLU/postprocessing."""
import argparse
import hashlib
import json
import runpy
import statistics
import time
import wave
from pathlib import Path

import gigaam
import numpy as np
import psutil
import torch
from omegaconf import OmegaConf

ROOT = Path(__file__).resolve().parents[2]
SCORING = runpy.run_path(str(ROOT / "tools/whisper/score-audit.py"))

def read_audio(path):
    with wave.open(str(path), "rb") as audio:
        assert (audio.getnchannels(), audio.getsampwidth(), audio.getframerate()) == (1, 2, 16000)
        samples = np.frombuffer(audio.readframes(audio.getnframes()), dtype="<i2").copy()
    return torch.from_numpy(samples).float().div_(32768).unsqueeze(0)

@torch.inference_mode()
def transcribe(model, waveform):
    length = torch.tensor([waveform.shape[-1]], dtype=torch.long)
    encoded, encoded_length = model(waveform, length)
    return model.decoding.decode(model.head, encoded, encoded_length)[0][0]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="v3_ctc")
    parser.add_argument("--threads", type=int, default=6)
    args = parser.parse_args()
    corpus = json.loads((ROOT / ".gigaam-cache/corpus.json").read_text(encoding="utf-8"))
    torch.set_num_threads(args.threads)
    torch.set_num_interop_threads(1)
    started = time.perf_counter()
    model = gigaam.load_model(args.model, device="cpu", fp16_encoder=False, use_flash=False,
        download_root=str(ROOT / ".gigaam-cache/models"))
    load_ms = (time.perf_counter() - started) * 1000
    OmegaConf.save(model.cfg, str(ROOT / f".gigaam-cache/{args.model}.yaml"))
    transcribe(model, read_audio(ROOT / corpus[0]["path"]))
    results = []
    for case in corpus:
        path = ROOT / case["path"]
        assert hashlib.sha256(path.read_bytes()).hexdigest() == case["sha256"]
        waveform = read_audio(path)
        durations, outputs = [], []
        for _ in range(3):
            started = time.perf_counter()
            outputs.append(transcribe(model, waveform))
            durations.append((time.perf_counter() - started) * 1000)
        assert len(set(outputs)) == 1, f"Unstable decode: {case['id']}"
        text = outputs[0]
        errors = SCORING["distance"](SCORING["words"](case["expected"]), SCORING["words"](text))
        results.append({**case, "text": text, "wordErrors": errors,
            "referenceWords": len(SCORING["words"](case["expected"])),
            "desktopMs": durations, "medianDesktopMs": statistics.median(durations)})
        print(case["id"], round(statistics.median(durations)), "ms", errors, "errors:", text, flush=True)
    primary = [r for r in results if r["group"] == "primary"]
    checkpoint = ROOT / f".gigaam-cache/models/{args.model}.ckpt"
    report = {"model": args.model, "runtime": "Official PyTorch CPU, greedy CTC, no prompts",
        "torch": torch.__version__, "threads": args.threads, "loadMs": load_ms,
        "checkpointBytes": checkpoint.stat().st_size,
        "checkpointSha256": hashlib.sha256(checkpoint.read_bytes()).hexdigest(),
        "parameters": sum(p.numel() for p in model.parameters()),
        "desktopRssAfterInference": psutil.Process().memory_info().rss,
        "summary": {"cases": len(primary), "wordErrors": sum(r["wordErrors"] for r in primary),
            "referenceWords": sum(r["referenceWords"] for r in primary)}, "cases": results,
        "limitations": "Known small regression corpus, not a blind test. Desktop timing/memory is not Android performance."}
    destination = ROOT / f".gigaam-cache/{args.model}-desktop.json"
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report["summary"]), flush=True)

if __name__ == "__main__":
    main()
