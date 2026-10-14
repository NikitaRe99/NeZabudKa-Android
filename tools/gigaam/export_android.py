"""Export waveform-to-CTC ONNX and dynamic INT8; verify against frozen corpus.

The DFT convolution implements the official Hann STFT without complex ONNX
operators, including reflection padding for v1/v2. Window/mel weights are copied
from the official frontend.
Only constant-weight MatMul layers are quantized; audio frontend remains FP32.
"""
import argparse
import gc
import hashlib
import json
import time

import gigaam
import numpy as np
import onnx
import onnxruntime as ort
import torch
from onnxruntime.quantization import QuantType, quantize_dynamic
from torch import nn
from torch.nn import functional as F

from benchmark import ROOT, SCORING, read_audio

CACHE = ROOT / ".gigaam-cache"


class Frontend(nn.Module):
    def __init__(self, original):
        super().__init__()
        assert original.n_fft == original.win_length
        mel = original.featurizer[0]
        n = original.n_fft
        # Compute the fixed basis in double precision, then use FP32 inference.
        phase = 2 * torch.pi * torch.arange(n // 2 + 1, dtype=torch.float64)[:, None] * torch.arange(n, dtype=torch.float64)[None, :] / n
        basis = torch.cat([phase.cos(), -phase.sin()]) * mel.spectrogram.window.double()
        self.register_buffer("basis", basis.float().unsqueeze(1))
        self.register_buffer("mel", mel.mel_scale.fb.clone())
        self.hop = original.hop_length
        self.n = n
        self.center = original.center

    def forward(self, audio, length):
        signal = audio.unsqueeze(1)
        if self.center:
            signal = F.pad(signal, (self.n // 2, self.n // 2), mode="reflect")
        spectrum = F.conv1d(signal, self.basis, stride=self.hop)
        real, imaginary = spectrum[:, :self.n // 2 + 1], spectrum[:, self.n // 2 + 1:]
        power = real.square() + imaginary.square()
        features = torch.matmul(power.transpose(1, 2), self.mel).transpose(1, 2)
        frames = length.div(self.hop, rounding_mode="floor") + 1 if self.center else (length - self.n).div(self.hop, rounding_mode="floor") + 1
        return features.clamp(1e-9, 1e9).log(), frames.long()


class WaveformCTC(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.frontend = Frontend(model.preprocessor)
        self.encoder = model.encoder
        self.head = model.head

    def forward(self, audio, length):
        features, frames = self.frontend(audio, length)
        encoded, encoded_length = self.encoder(features, frames)
        return self.head(encoded).argmax(-1), encoded_length


def decode(labels, length, vocab):
    previous = -1
    tokens = []
    for token in labels[0, :int(length[0])]:
        token = int(token)
        if token != len(vocab) and token != previous:
            tokens.append(vocab[token])
        previous = token
    return "".join(tokens)


def validate(path, corpus, references, vocab):
    options = ort.SessionOptions()
    options.intra_op_num_threads = 6
    options.inter_op_num_threads = 1
    options.log_severity_level = 3
    started = time.perf_counter()
    session = ort.InferenceSession(str(path), options, providers=["CPUExecutionProvider"])
    load_ms = (time.perf_counter() - started) * 1000
    results = []
    for case in corpus:
        audio = read_audio(ROOT / case["path"]).numpy()
        inputs = {"audio": audio, "length": np.array([audio.shape[1]], dtype=np.int64)}
        outputs, durations = [], []
        for _ in range(3):
            started = time.perf_counter()
            labels, length = session.run(None, inputs)
            durations.append((time.perf_counter() - started) * 1000)
            outputs.append(decode(labels, length, vocab))
        assert len(set(outputs)) == 1
        text = outputs[0]
        errors = SCORING["distance"](SCORING["words"](case["expected"]), SCORING["words"](text))
        results.append({"id": case["id"], "text": text, "wordErrors": errors,
            "sameAsOfficial": text == references[case["id"]], "desktopMs": durations})
        print(path.name, case["id"], errors, text, flush=True)
    del session
    gc.collect()
    return {"file": path.name, "bytes": path.stat().st_size,
        "sha256": hashlib.file_digest(path.open("rb"), "sha256").hexdigest(),
        "loadMs": load_ms, "cases": results}


@torch.inference_mode()
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", choices=["v1_ctc", "v2_ctc", "v3_ctc"], default="v3_ctc")
    args = parser.parse_args()
    output = CACHE if args.model == "v3_ctc" else CACHE / args.model
    output.mkdir(exist_ok=True)
    torch.set_num_threads(6)
    torch.set_num_interop_threads(1)
    corpus = json.loads((CACHE / "corpus.json").read_text(encoding="utf-8"))
    baseline = json.loads((CACHE / f"{args.model}-desktop.json").read_text(encoding="utf-8"))
    references = {case["id"]: case["text"] for case in baseline["cases"]}
    fp32 = output / f"{args.model}-waveform-fp32.onnx"
    int8 = output / f"{args.model}-waveform-int8.onnx"
    model = gigaam.load_model(args.model, device="cpu", fp16_encoder=False, use_flash=False,
        download_root=str(CACHE / "models"))
    wrapper = WaveformCTC(model).eval()
    vocab = list(model.decoding.tokenizer.vocab)
    frontend_checks = []
    for case in corpus:
        audio = read_audio(ROOT / case["path"])
        length = torch.tensor([audio.shape[1]], dtype=torch.long)
        official, official_len = model.preprocessor(audio, length)
        converted, converted_len = wrapper.frontend(audio, length)
        difference = (official - converted).abs()
        assert torch.equal(official_len, converted_len)
        assert difference.max().item() < 0.005, difference.max().item()
        labels, out_len = wrapper(audio, length)
        text = decode(labels.numpy(), out_len.numpy(), vocab)
        assert text == references[case["id"]], (case["id"], text)
        frontend_checks.append({"id": case["id"], "maxLogMelDifference": difference.max().item()})
    print("Frontend verified on all recordings", flush=True)
    (output / "metadata.json").write_text(json.dumps({"model": args.model, "vocabulary": vocab,
        "blankId": len(vocab), "sampleRate": 16000, "decoder": "greedy CTC",
        "quantization": "dynamic per-channel INT8 constant MatMul; frontend FP32"}, ensure_ascii=False, indent=2), encoding="utf-8")
    if not fp32.exists():
        audio = read_audio(ROOT / corpus[1]["path"])
        length = torch.tensor([audio.shape[1]], dtype=torch.long)
        with model.encoder.onnx_export_mode():
            torch.onnx.export(wrapper, (audio, length), str(fp32),
                input_names=["audio", "length"], output_names=["labels", "encoded_length"],
                dynamic_axes={"audio": {1: "samples"}, "labels": {1: "frames"}},
                opset_version=17, dynamo=False)
        print("FP32 export complete", flush=True)
    del wrapper, model
    gc.collect()
    if not int8.exists():
        graph = onnx.load(str(fp32))
        exclude = [node.name for node in graph.graph.node if "/frontend/" in node.name]
        del graph
        gc.collect()
        quantize_dynamic(str(fp32), str(int8), per_channel=True, weight_type=QuantType.QInt8,
            op_types_to_quantize=["MatMul"], nodes_to_exclude=exclude,
            extra_options={"MatMulConstBOnly": True})
        print("INT8 export complete", flush=True)
    report = {"frontendChecks": frontend_checks, "variants": [
        validate(fp32, corpus, references, vocab), validate(int8, corpus, references, vocab)]}
    (output / "onnx-validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
