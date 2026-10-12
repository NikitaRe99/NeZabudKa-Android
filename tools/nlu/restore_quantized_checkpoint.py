"""Recover trainable weights from our own deployed INT8 checkpoint, preserving learned heads.

The source hash is pinned. Linear ONNX MatMul weights are transposed relative to PyTorch.
Only unused encoder.pooler parameters may remain from the original base model.
"""
import hashlib
from pathlib import Path
import onnx
from onnx import numpy_helper
import torch
from train_nlu import JointNluModel, BASE_MODEL, INTENTS, SLOT_LABELS

source = Path("tools/nlu/output/baseline/classic_nlu_int8.onnx")
assert hashlib.sha256(source.read_bytes()).hexdigest() == "76bb09f071e3d9659d01d6ef63240b236a69e00e687fe0a11fdd5fb70304166f"
graph = onnx.load(source)
arrays = {v.name: numpy_helper.to_array(v).copy() for v in graph.graph.initializer}
model = JointNluModel(BASE_MODEL, len(INTENTS), len(SLOT_LABELS))
state = model.state_dict()
restored = set()
def assign(name, array):
    assert name in state, name
    assert tuple(array.shape) == tuple(state[name].shape), (name, array.shape, state[name].shape)
    state[name] = torch.tensor(array, dtype=state[name].dtype)
    restored.add(name)

def dequantize(name):
    base = name.removesuffix("_quantized")
    return (arrays[name].astype("float32") - arrays[base + "_zero_point"].astype("float32")) * arrays[base + "_scale"].astype("float32")

for name in state:
    if name in arrays:
        assign(name, arrays[name])
    elif name + "_quantized" in arrays and ".embeddings." in name:
        assign(name, dequantize(name + "_quantized"))
for node in graph.graph.node:
    if node.op_type != "MatMulInteger" or node.input[1] not in arrays:
        continue
    module = node.name.split("/MatMul")[0].split("/Gemm")[0].strip("/").replace("/", ".")
    assign(module + ".weight", dequantize(node.input[1]).T)
missing = sorted(set(state) - restored)
assert all(name.startswith("encoder.pooler.") for name in missing), missing
torch.save(state, source.parent / "restored_classic_nlu.pt")
print("restored", len(restored), "unused", missing, flush=True)
