#!/usr/bin/env python3
"""Compile the vectrix-gpu Slang kernels to SPIR-V (Vulkan) and Metal source, and record their ABI.

Run from anywhere after editing src/main/slang; commit the regenerated resources:

    python3 vectrix-gpu/scripts/build-kernels.py

The build itself never needs slangc: it uses the committed outputs, and KernelManifestTest fails when a .slang file
no longer matches the hash recorded here. Finds slangc through $SLANGC, then $VULKAN_SDK/bin, then the newest
~/VulkanSDK/*/macOS/bin, then PATH.

Both targets are compiled so that every float operation rounds as the vectrix Java code does:
- slangc runs at -O0, because its optimizer folds (x + c) - c to x in SPIR-V, which IEEE arithmetic forbids.
- SPIR-V: every OpFAdd and OpFSub is decorated NoContraction, so no driver fuses a product into it. Products are
  left alone: SPIRV-Cross (MoltenVK) turns a NoContraction multiply into fma(a, b, 0), which loses the sign of -0.
- Metal: the source starts with `#pragma METAL fp math_mode(safe)` and `#pragma METAL fp contract(off)`; without
  them the Metal compiler fuses multiply-adds even with fast math disabled.
Buffers are declared in slot order with the parameter block last, so Metal buffer indices equal the slots and the
parameters take the next index (DynamisGPU's BindingLayout.parameterSlot()). The script checks that for each kernel.
"""
import glob
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SLANG = os.path.join(ROOT, "src", "main", "slang")
OUT = os.path.join(ROOT, "src", "main", "resources", "org", "dynamisengine", "vectrix", "compute", "kernels")
# Test-only kernels (probes of the shared functions); they may import the main modules.
TEST_SLANG = os.path.join(ROOT, "src", "test", "slang")
TEST_OUT = os.path.join(ROOT, "src", "test", "resources", "org", "dynamisengine", "vectrix", "compute", "testkernels")

# (resource name, source file, entry point)
KERNELS = [
    ("gemm", "gemm.slang", "gemm"),
    ("philox", "philox.slang", "philox"),
    ("softmax", "softmax.slang", "softmax"),
    ("softmax_inplace", "softmax_inplace.slang", "softmaxInPlace"),
    ("blas_partial", "blas_partial.slang", "blasPartial"),
    ("blas_combine", "blas_combine.slang", "blasCombine"),
    ("blas_amax", "blas_amax.slang", "blasAmax"),
    ("blas_axpy", "blas_axpy.slang", "axpy"),
    ("blas_axpy_inplace", "blas_axpy_inplace.slang", "axpyInPlace"),
    ("blas_scal", "blas_scal.slang", "scal"),
    ("gemv", "gemv.slang", "gemv"),
]

TEST_KERNELS = [
    ("math_probe", "math_probe.slang", "probe"),
]

METAL_PRELUDE = "#pragma METAL fp math_mode(safe)\n#pragma METAL fp contract(off)\n"

OP_DECORATE = 71
NO_CONTRACTION = 42
FADD_FSUB = {129, 131}
# capabilities, extensions, imports, memory model, entry points, execution modes, debug, annotations
LEADING = {17, 10, 11, 14, 15, 16, 330, 331, 2, 3, 4, 5, 6, 7, 8, 12, 71, 72, 73, 74, 75, 332, 5632, 5633}


def find_slangc():
    candidates = [os.environ.get("SLANGC")]
    if os.environ.get("VULKAN_SDK"):
        candidates.append(os.path.join(os.environ["VULKAN_SDK"], "bin", "slangc"))
    candidates += sorted(glob.glob(os.path.expanduser("~/VulkanSDK/*/macOS/bin/slangc")), reverse=True)
    candidates.append(shutil.which("slangc"))
    for c in candidates:
        if c and os.path.isfile(c):
            probe = subprocess.run([c, "-v"], capture_output=True, text=True)
            if probe.returncode == 0:
                return c, (probe.stdout + probe.stderr).strip().splitlines()[0]
    sys.exit("slangc not found (set SLANGC or VULKAN_SDK)")


def source_hash(*dirs):
    h = hashlib.sha256()
    for path in sorted(p for d in dirs for p in glob.glob(os.path.join(d, "*.slang"))):
        h.update(os.path.basename(path).encode())
        h.update(b"\0")
        h.update(open(path, "rb").read())
        h.update(b"\0")
    return h.hexdigest()


def no_contraction(spv):
    words = list(struct.unpack("<%dI" % (len(spv) // 4), spv))
    if words[0] != 0x07230203:
        sys.exit("unexpected SPIR-V byte order")
    insts, i = [], 5
    while i < len(words):
        count = words[i] >> 16
        insts.append(words[i:i + count])
        i += count
    marked = {w[1] for w in insts if w[0] & 0xFFFF == OP_DECORATE and w[2] == NO_CONTRACTION}
    targets = [w[2] for w in insts if w[0] & 0xFFFF in FADD_FSUB and w[2] not in marked]
    at = next((k for k, w in enumerate(insts) if w[0] & 0xFFFF not in LEADING), len(insts))
    decorations = [[(3 << 16) | OP_DECORATE, t, NO_CONTRACTION] for t in targets]
    out = words[:5] + [w for inst in insts[:at] + decorations + insts[at:] for w in inst]
    return struct.pack("<%dI" % len(out), *out)


def reflect(path, entry):
    r = json.load(open(path))
    buffers, params = [], []
    for prm in r["parameters"]:
        kind = prm["binding"]["kind"]
        if kind == "descriptorTableSlot":
            buffers.append((prm["name"], prm["binding"]["index"]))
        elif kind == "pushConstantBuffer":
            for f in prm["type"]["elementType"]["fields"]:
                params.append((f["name"], f["binding"]["offset"], f["type"]["scalarType"]))
    ep = next(e for e in r["entryPoints"] if e["name"] == entry)
    return sorted(buffers, key=lambda b: b[1]), params, ep["threadGroupSize"]


def check_metal(text, entry, buffers):
    sig = re.search(r"\[\[kernel\]\]\s+void\s+" + re.escape(entry) + r"\((.*?)\)\s*\{", text, re.S)
    if not sig:
        sys.exit(f"{entry}: kernel signature not found in the Metal output")
    found = dict((name, int(idx)) for name, idx in re.findall(r"\*\s*(\w+?)_\d+\s*\[\[buffer\((\d+)\)\]\]", sig.group(1)))
    expect = {name: slot for name, slot in buffers}
    expect["p"] = max(expect.values()) + 1
    if found != expect:
        sys.exit(f"{entry}: Metal buffer indices {found} differ from slots {expect}")


def run(cmd):
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        sys.exit("command failed: " + " ".join(cmd) + "\n" + res.stdout + res.stderr)


def build(slangc, version, spirv_val, kernels, src_dir, out_dir, manifest_name, hash_dirs):
    os.makedirs(out_dir, exist_ok=True)
    manifest = {"slangc.version": version, "source.sha256": source_hash(*hash_dirs)}
    with tempfile.TemporaryDirectory() as tmp:
        for name, src, entry in kernels:
            common = [slangc, os.path.join(src_dir, src), "-I", SLANG, "-I", src_dir, "-entry", entry, "-stage",
                      "compute", "-O0", "-line-directive-mode", "none"]
            spv_tmp, refl = os.path.join(tmp, name + ".spv"), os.path.join(tmp, name + ".json")
            run(common + ["-target", "spirv", "-fvk-use-entrypoint-name", "-reflection-json", refl, "-o", spv_tmp])
            spv = no_contraction(open(spv_tmp, "rb").read())
            spv_out = os.path.join(out_dir, name + ".spv")
            open(spv_out, "wb").write(spv)
            if os.path.isfile(spirv_val):
                run([spirv_val, spv_out])
            else:
                print("warning: spirv-val not found; SPIR-V not validated")
            metal_tmp = os.path.join(tmp, name + ".metal")
            run(common + ["-target", "metal", "-o", metal_tmp])
            metal = METAL_PRELUDE + open(metal_tmp).read()
            buffers, params, local = reflect(refl, entry)
            check_metal(metal, entry, buffers)
            open(os.path.join(out_dir, name + ".metal"), "w").write(metal)
            manifest[name + ".entry"] = entry
            manifest[name + ".local"] = ",".join(str(v) for v in local)
            manifest[name + ".buffers"] = ",".join(f"{n}:{s}" for n, s in buffers)
            manifest[name + ".params"] = ",".join(f"{n}:{o}:{t}" for n, o, t in params)
            manifest[name + ".spirv.sha256"] = hashlib.sha256(spv).hexdigest()
            manifest[name + ".metal.sha256"] = hashlib.sha256(metal.encode()).hexdigest()
            print(f"{name}: {len(spv)} bytes SPIR-V, {len(metal)} chars Metal, local {local}, buffers {buffers}")
    lines = ["# Generated by scripts/build-kernels.py; do not edit."]
    lines += [f"{k}={v}" for k, v in sorted(manifest.items())]
    open(os.path.join(out_dir, manifest_name), "w").write("\n".join(lines) + "\n")


def main():
    slangc, version = find_slangc()
    spirv_val = shutil.which("spirv-val") or os.path.join(os.path.dirname(slangc), "spirv-val")
    build(slangc, version, spirv_val, KERNELS, SLANG, OUT, "kernels.properties", [SLANG])
    build(slangc, version, spirv_val, TEST_KERNELS, TEST_SLANG, TEST_OUT, "testkernels.properties",
          [SLANG, TEST_SLANG])


if __name__ == "__main__":
    main()
