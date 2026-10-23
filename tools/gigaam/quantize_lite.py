"""Experimental extra INT8 convolution quantization; keep the audio frontend FP32.

Calibration uses only FLEURS train. Evaluation data must never enter this script.
The published mixed INT8 model is the source; existing linear quantization stays.
"""
import argparse
import hashlib
import json
from pathlib import Path
import time
import tempfile

import numpy as np
import onnx
from onnxruntime.quantization import CalibrationDataReader, CalibrationMethod, QuantFormat, QuantType, quantize_static
import soundfile as sf

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".gigaam-cache/lite"


class SpeechCalibration(CalibrationDataReader):
    def __init__(self, entries):
        self.entries = entries
        self.index = 0

    def get_next(self):
        if self.index >= len(self.entries):
            return None
        entry = self.entries[self.index]
        path = ROOT / entry["path"]
        assert hashlib.sha256(path.read_bytes()).hexdigest() == entry["sha256"]
        audio, rate = sf.read(path, dtype="float32")
        assert rate == 16_000 and audio.ndim == 1
        self.index += 1
        if self.index == 1 or self.index % 8 == 0:
            print(f"Calibration {self.index}/{len(self.entries)}", flush=True)
        return {"audio": audio[None, :], "length": np.array([len(audio)], dtype=np.int64)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--method", choices=["minmax", "percentile"], default="minmax")
    args = parser.parse_args()
    source = ROOT / ".gigaam-cache/publish/gigaam-v3-ctc-int8.onnx"
    target = OUT / f"gigaam-v3-ctc-lite-{args.method}-int8.onnx"
    if target.exists():
        raise SystemExit(f"Refusing to overwrite existing experiment: {target}")
    OUT.mkdir(parents=True, exist_ok=True)
    temporary = OUT / "tmp"
    temporary.mkdir(exist_ok=True)
    tempfile.tempdir = str(temporary)
    graph = onnx.load(source)
    nodes = [node.name for node in graph.graph.node if node.op_type == "Conv" and "/frontend/" not in node.name]
    assert nodes
    del graph
    entries = json.loads((OUT / "fleurs/train/manifest.json").read_text(encoding="utf-8"))
    started = time.perf_counter()
    quantize_static(source, target, SpeechCalibration(entries), quant_format=QuantFormat.QDQ,
        op_types_to_quantize=["Conv"], nodes_to_quantize=nodes, per_channel=True,
        activation_type=QuantType.QInt8, weight_type=QuantType.QInt8,
        calibrate_method=CalibrationMethod.MinMax if args.method == "minmax" else CalibrationMethod.Percentile,
        calibration_cache_path=OUT / f"calibration-{args.method}.json",
        extra_options={"CalibPercentile": 99.99,
                       "ActivationSymmetric": False, "WeightSymmetric": True})
    graph = onnx.load(target)
    props = {value.key: value.value for value in graph.metadata_props}
    props.update({"nezabudka.model": "v3_ctc_lite_int8_experimental",
                  "nezabudka.quantization": f"dynamic per-channel MatMul INT8 + static per-channel Conv INT8 ({args.method}); frontend FP32",
                  "nezabudka.calibration": f"google/fleurs ru_ru train, {len(entries)} recordings, CC BY 4.0"})
    onnx.helper.set_model_props(graph, props)
    onnx.checker.check_model(graph)
    onnx.save(graph, target)
    del graph
    result = {"file": target.name, "bytes": target.stat().st_size,
        "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "convolutions": len(nodes), "calibrationRecordings": len(entries), "method": args.method,
        "buildSeconds": time.perf_counter() - started}
    target.with_suffix(".build.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result), flush=True)


if __name__ == "__main__":
    main()
