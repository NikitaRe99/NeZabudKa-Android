"""Rewrite eligible 1x1 convolutions as equivalent MatMul and dynamically quantize.

Only group=1, stride=1, unpadded 1D pointwise convolutions are eligible.
Other convolutions and the waveform frontend retain FP32. No calibration data.
"""
import hashlib
import json
from pathlib import Path
import tempfile

import numpy as np
import onnx
from onnx import helper, numpy_helper
from onnxruntime.quantization import quantize_dynamic, QuantType

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".gigaam-cache/lite"


def main():
    source = ROOT / ".gigaam-cache/publish/gigaam-v3-ctc-int8.onnx"
    floating = OUT / "gigaam-v3-pointwise-fp32-check.onnx"
    target = OUT / "gigaam-v3-ctc-lite-pointwise-int8.onnx"
    if target.exists():
        raise SystemExit("Refusing to overwrite an existing experiment")
    temporary = OUT / "tmp"
    temporary.mkdir(parents=True, exist_ok=True)
    tempfile.tempdir = str(temporary)
    model = onnx.load(source)
    tensors = {t.name: t for t in model.graph.initializer}
    rewritten = []
    nodes = []
    weights = []
    for node in model.graph.node:
        if node.op_type != "Conv" or "/frontend/" in node.name or node.input[1] not in tensors:
            nodes.append(node)
            continue
        weight = numpy_helper.to_array(tensors[node.input[1]])
        attributes = {a.name: helper.get_attribute_value(a) for a in node.attribute}
        eligible = (weight.ndim == 3 and weight.shape[-1] == 1 and attributes.get("group", 1) == 1
            and attributes.get("strides", [1]) == [1] and attributes.get("dilations", [1]) == [1]
            and not any(attributes.get("pads", [0, 0])) and attributes.get("auto_pad", b"NOTSET") in (b"NOTSET", b"VALID"))
        if not eligible:
            nodes.append(node)
            continue
        prefix = node.name + "/lite"
        matrix = prefix + "/weight"
        weights.append(numpy_helper.from_array(np.ascontiguousarray(weight[:, :, 0].T), matrix))
        nodes.append(helper.make_node("Transpose", [node.input[0]], [prefix + "/nlc"], name=prefix + "/to_nlc", perm=[0, 2, 1]))
        matmul = prefix + "/MatMul"
        nodes.append(helper.make_node("MatMul", [prefix + "/nlc", matrix], [prefix + "/product"], name=matmul))
        output = prefix + "/product"
        if len(node.input) > 2 and node.input[2]:
            nodes.append(helper.make_node("Add", [output, node.input[2]], [prefix + "/biased"], name=prefix + "/bias"))
            output = prefix + "/biased"
        nodes.append(helper.make_node("Transpose", [output], list(node.output), name=prefix + "/to_ncl", perm=[0, 2, 1]))
        rewritten.append(matmul)
    assert len(rewritten) == 33, f"Unexpected source architecture: {len(rewritten)} pointwise convolutions"
    del model.graph.node[:]
    model.graph.node.extend(nodes)
    model.graph.initializer.extend(weights)
    used = {value for node in model.graph.node for value in node.input}
    kept = [tensor for tensor in model.graph.initializer if tensor.name in used]
    del model.graph.initializer[:]
    model.graph.initializer.extend(kept)
    onnx.checker.check_model(model)
    onnx.save(model, floating)
    del model, tensors, kept, weights, nodes
    quantize_dynamic(floating, target, nodes_to_quantize=rewritten, op_types_to_quantize=["MatMul"],
        per_channel=True, weight_type=QuantType.QInt8, extra_options={"MatMulConstBOnly": True})
    model = onnx.load(target)
    properties = {entry.key: entry.value for entry in model.metadata_props}
    properties.update({"nezabudka.model": "v3_ctc_lite_pointwise_int8_experimental",
        "nezabudka.quantization": "dynamic per-channel MatMul INT8 including equivalent 1x1 Conv; frontend and other Conv FP32"})
    onnx.helper.set_model_props(model, properties)
    onnx.checker.check_model(model)
    onnx.save(model, target)
    report = {"file": target.name, "bytes": target.stat().st_size,
        "sha256": hashlib.sha256(target.read_bytes()).hexdigest(), "pointwiseConvolutions": len(rewritten),
        "sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest()}
    target.with_suffix(".build.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report), flush=True)


if __name__ == "__main__":
    main()
