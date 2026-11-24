# vectrix-gpu

GPU twins of Vectrix kernels. Each one runs the Vectrix algorithm on the GPU through
[DynamisGPU](https://github.com/ljramones/DynamicGPU)'s compute API (Metal or Vulkan). On the devices tested so
far, the results are the same bits as the CPU method.

The Vectrix jar never touches the GPU and doesn't depend on this project. vectrix-gpu depends on Vectrix only in
its tests, which use Vectrix as the reference, and on `dynamis-gpu-api`. The application chooses the backend.

```
vectrix (CPU reference)        dynamis-gpu-api
          \                      /           \
           vectrix-gpu                        dynamis-gpu-metal / dynamis-gpu-vulkan
                |
   NeuralNetworks, LightEngine, ...
```

"IEEE devices" in the last column means GPUs that round single-precision add, multiply, divide and `sqrt` correctly
and fuse `fma`; see [Where the GPU can differ](#where-the-gpu-can-differ-from-the-cpu).

**Linear algebra**

| Class | Twin of | Kernel | Matches the CPU |
|---|---|---|---|
| `GpuBlasf` | `blas.Blasf` `dot`, `sum`, `asum`, `nrm2`, `iamax`, `axpy`, `scal` | reductions over whole vectors in Blasf's pairwise order, with the result left on the GPU; element-wise `axpy` (in place or not) and `scal` | bit for bit on IEEE devices |
| `GpuGemmf` | `blas.Gemmf.gemm`, `Gemmf.gemv` | `C = α·op(A)·op(B) + β·C` and `y = α·op(A)·x + β·y`, row-major, any transposes, offsets and strides | bit for bit on IEEE devices |

**Neural networks**

| Class | Twin of | Kernel | Matches the CPU |
|---|---|---|---|
| `GpuActivations` | `nn.Activations`, all but `gelu` and `geluBackward` (they compute in double) | activations forward and backward, in place or not; inverted dropout with the Philox mask | bit for bit on IEEE devices; the dropout mask on every device |
| `GpuSoftmax` | `nn.Softmax` `softmax`, `logSoftmax`, `logSumExp`, `softmaxBackward`, `logSoftmaxBackward` | over the rows of a matrix, in place or not | bit for bit on IEEE devices |
| `GpuLoss` | `nn.Loss` `crossEntropy`, `crossEntropyBackward` | over rows, one int target per row | bit for bit on IEEE devices |
| `GpuNorms` | `nn.Norms` `layerNorm`, `rmsNorm` and their backward passes | over rows; parameter gradients summed in row order, as on the CPU | bit for bit on IEEE devices |
| `GpuAttention` | `nn.Attention` `forward`, `backward`, `groupedQuery`, `groupedQueryBackward` | scaled dot-product attention batched over queries and heads, with an optional causal mode; gradients accumulated in the CPU's order | bit for bit on IEEE devices |
| `GpuOptim` | `nn.Optim` `adamW`, `sgd` | one optimizer step per element, with AdamW's bias corrections computed on the host as Vectrix does | bit for bit on IEEE devices |
| `GpuFormats` | `nn.Formats` | bf16, E4M3 and E5M2 conversions over buffers; int8 quantize, dequantize, dot and gemv (blocks of 32) | conversions, quantize and dequantize: bit for bit on every device, subnormals included. Dot and gemv: bit for bit on IEEE devices |
| `GpuSampler` | `nn.Sampler.argmax` | the argmax of each row (the first NaN wins; ties go to the lowest index) | bit for bit on every device |

**Random numbers**

| Class | Twin of | Kernel | Matches the CPU |
|---|---|---|---|
| `GpuPhilox` | `hash.Philox.word`, `Philox.uniform` | fills a buffer with a counter-based random stream | bit for bit on every device (integer only) |

**Scenes and animation**

| Class | Twin of | Kernel | Matches the CPU |
|---|---|---|---|
| `GpuCullingKernels` | `geometry.CullingKernels.frustumCullAabbBatch` | INSIDE, INTERSECT or OUTSIDE per box against six planes | bit for bit on IEEE devices |
| `GpuTransformKernels` | `soa.TransformKernels.composeBatch` | parent·local TRS composition (the FAST mode) | bit for bit on IEEE devices |
| `GpuSkinningKernels` | `soa.SkinningKernels`: linear-blend, dual-quaternion and matrix-palette skinning, and building palettes | four-influence skinning, interleaved or per-slot | bit for bit on IEEE devices |
| `GpuPackedAffineKernels` | `affine.PackedAffineKernels`: quaternion plus translation, AABB transform, upload | batches of 3×4 affine transforms in `AFFINE_3X4_ROW_MAJOR` | bit for bit on IEEE devices |

**Not ported:**
- `Activations.gelu` and `geluBackward`, `Rope`, and `Optim.clipFactor`: they compute in double, and Metal has no
  FP64.
- `Sampler.sample`: sequential host-side work.
- Methods that take arrays of objects, such as `PackedAffineKernels.trsToPackedAffineBatch`.

## Using it

Coordinates: `org.dynamisengine:vectrix-gpu:3.1.0` (module `org.dynamisengine.vectrix.compute`), with
`dynamis-gpu-metal` or `dynamis-gpu-vulkan` at runtime. Neither is on Maven Central: install vectrix-gpu from this
repository (`mvn -f vectrix-gpu/pom.xml install`) and DynamisGPU from its GitHub Release (see
[Building](#building)).

<!-- snippet: UsageExampleTest#usage -->
```java
var usage = EnumSet.of(BufferUsage.STORAGE, BufferUsage.TRANSFER_SOURCE, BufferUsage.TRANSFER_DESTINATION);
try (GpuGemmf gemm = new GpuGemmf(device); // compiles the kernel: create once per device and reuse
        GpuBuffer ga = device.allocate(new BufferDescriptor(4L * m * k, usage, Placement.PRIVATE));
        GpuBuffer gb = device.allocate(new BufferDescriptor(4L * k * n, usage, Placement.PRIVATE));
        GpuBuffer gc = device.allocate(new BufferDescriptor(4L * m * n, usage, Placement.PRIVATE));
        GpuCommandEncoder encoder = device.encoder()) {
    encoder.upload(ga, littleEndian(a)).upload(gb, littleEndian(b));
    gemm.gemm(encoder, false, false, m, n, k, 1f, ga, 0, k, gb, 0, n, 0f, gc, 0, n); // C = A·B
    GpuReadback c = encoder.download(gc);
    try (GpuSubmission done = device.queue().submit(encoder)) {
        done.await(Duration.ofSeconds(10));
    }
    return floats(c.bytes()); // the bits Gemmf.gemm computes on the CPU
}
```

The full program is in [`UsageExampleTest`](src/test/java/org/dynamisengine/vectrix/compute/UsageExampleTest.java).
Open the device with `new MetalProvider().adapters().get(0).open()`, or the same with `VulkanProvider`.

The methods follow the Vectrix signatures, with a `GpuBuffer` and an element offset in place of an array and its
offset:
- **Validation:** arguments are checked before anything is recorded, and bad ones throw
  `IllegalArgumentException`, as on the CPU.
- **Recording:** dispatches only record into your encoder, so you can batch several and wait once.
- **Data:** buffers hold little-endian 32-bit values. 8- and 16-bit data is packed with element 0 in the low bits:
  two bf16 or four 8-bit codes per word. That's the byte layout of a Java `short[]` or `byte[]`, so arrays upload as
  they are. Offsets and counts need not be word-aligned.
- **Layouts:** scene types such as `TransformSoA` and `AABBSoA` become one buffer per type, each field a plane of
  `count` values in the Java field order. Packed transforms keep the `AFFINE_3X4_ROW_MAJOR` bytes of
  `PackedAffineArray`.

### Where the GPU can differ from the CPU

- **Subnormals:** GPUs that flush subnormal floats (magnitude below 2⁻¹²⁶) to zero, including every Apple GPU, give
  0 where Vectrix gives a subnormal. Values computed from it follow from the zero. For example, a softmax
  probability below 2⁻¹²⁶ comes out as 0.
- **DynamisGPU before 1.0.3:** on MoltenVK, 1.0.2 and earlier leave Metal fast math on, which reassociates
  arithmetic. Use 1.0.3 or later, or set `MVK_CONFIG_FAST_MATH_ENABLED=0` in the environment.
- **Other devices:** anything that doesn't round single-precision add, multiply and divide correctly, or doesn't fuse
  `fma`, will differ in the last bits. The tests show which.
- **Values only the kernel reads:** some arguments live in buffers, so they can't be checked before the dispatch.
  Where the CPU method throws on a bad value, the GPU twin writes a defined result instead:
  - an out-of-range cross-entropy target gives a NaN loss or gradient row;
  - out-of-range joint indices or transform `order` entries leave that element unchanged;
  - `GpuFormats.quantizeInt8` gives a block holding a NaN or an infinity the scale NaN and zero codes.

### Limits

- **Indices:** element indices must stay below 2³².
- **Row length:** at most `GpuSoftmax.MAX_ROW` (262,144) elements for every row kernel (softmax, loss, norms,
  argmax). The same limit applies to attention keys per query (`GpuAttention.MAX_KEYS`).
- **Philox fill length:** at most `GpuPhilox.MAX_FILL` values.
- **Scratch:** BLAS reductions take caller scratch of `GpuBlasf.scratchSize(n)` elements (at most 278,530), and the
  attention backward passes take `GpuAttention.backwardScratchSize(...)`. Scratch must be a separate buffer from the
  inputs and outputs.
- **Buffers:** a destination must be a different buffer from the inputs. The exceptions are the in-place forms the
  CPU allows, where the output is an input at the same offset and stride. These include activations, dropout, softmax
  and its relatives, norms and `axpy`. Optimizer buffers must all be distinct.

## Building

Install the library first. vectrix-gpu always builds against the library version in the same commit.

```bash
mvn install -DskipTests                       # in the repository root
mvn -f vectrix-gpu/pom.xml verify
```

DynamisGPU comes from its GitHub Release when it isn't already in your local repository:

```bash
curl -fsSLO https://github.com/ljramones/DynamicGPU/releases/download/v1.0.3/dynamisgpu-1.0.3-maven.zip
unzip -q dynamisgpu-1.0.3-maven.zip && cp -R dynamisgpu-1.0.3/repository/. ~/.m2/repository/
```

## Testing

Every kernel test runs on each enabled backend and compares every output value with the Vectrix method, bit for
bit:

| Backend | Enabled by | What it shows |
|---|---|---|
| reference | always (CI) | A Java model of the GPU that runs the Vectrix method. It decodes parameters at the offsets the compiled kernel uses, and checks that the dispatch's groups cover the work. It tests the Java side of the ABI and the validation, not the shaders |
| Metal | `-Pmetal` (macOS) | the shaders on the native Metal backend |
| Vulkan | `-Pvulkan` | the shaders on Vulkan |

```bash
mvn -f vectrix-gpu/pom.xml verify -Pmetal
VK_DRIVER_FILES=$HOME/VulkanSDK/1.4.341.0/macOS/share/vulkan/icd.d/MoltenVK_icd.json \
  mvn -f vectrix-gpu/pom.xml verify -Pvulkan \
  -Ddynamisgpu.vk.loader=$HOME/VulkanSDK/1.4.341.0/macOS/lib/libvulkan.1.4.341.dylib
```

A requested backend without a device fails the run instead of skipping it.

The CI runners have no GPU, so run both hardware profiles before a release. On an Apple M4 Max with DynamisGPU
1.0.3, every test passes on both native Metal and MoltenVK, with no environment settings.

Two sweeps cover every 32-bit float input but are opt-in, because they take minutes:
`-Dvectrix.gpu.exhaustive=true` checks all 2³² inputs to the math functions (`ReproducibleMathGpuTest`) and to the
format encoders (`GpuFormatsTest`). Normal runs check about a million inputs spread over the whole range.

## Adding or changing a kernel

Kernels are written in [Slang](https://shader-slang.org), in `src/main/slang`. `scripts/build-kernels.py` compiles
them to SPIR-V and Metal source in `src/main/resources`, and the build uses those committed files, so neither the
build nor CI needs `slangc`. After editing a `.slang` file:

```bash
python3 vectrix-gpu/scripts/build-kernels.py   # finds slangc via $SLANGC, $VULKAN_SDK or ~/VulkanSDK
```

`KernelManifestTest` fails while a source differs from what was compiled. It also checks that the parameter blocks
the Java side writes match the fields and offsets Slang compiled.

**Rules for kernel sources:**
- **Port the Vectrix method exactly:** the same operation order, explicit `fma` where the Java uses `Math.fma`, and
  the helpers in `common.slang`:
  - `jmax`, `jmin`, `jabs` and `jcopySign` for `Math.max`, `min`, `abs` and `copySign`;
  - `reproducibleExp`, `Expm1`, `Tanh`, `Sigmoid`, `Log` and `Log1p` for `ReproducibleMath`, each verified on all
    2³² inputs;
  - `pairwiseBlocks` and `combineLanes` for the `Blasf` reduction order.

  The GPU's own `sqrt` is correctly rounded, also verified on all inputs. `Math.invsqrt` computes in double; its
  port is `vectrixInvsqrt` in `engine.slang`.
- **Declare bindings in order:** buffers in slot order, then the `[[vk::push_constant]]` parameter block, all fields
  32-bit. Metal then numbers buffers by slot and puts the parameters at the next index, as DynamisGPU expects; the
  script checks this.
- **Don't reread group memory you've copied to a local:** if other threads may write that group variable later, keep
  the value in its own group variable that nothing writes again for the row (see `softmax_row.slang`).
- **Keep barriers in uniform control flow:** every thread of a group must reach the same barriers. Loop bounds may
  depend only on parameters and the group index.
- **Don't start a sum from a constant `0.0f`:** Slang folds `0.0f + v` to `v` even at `-O0`, which keeps a `-0`
  that Java turns into `+0`. Route the first term through `plusZero()` (`engine.slang`), or start the accumulator in
  a loop variable.
- **Import what you use:** Slang imports aren't transitive, so a kernel that imports a module built on `common`
  must also `import common;` itself.

**What the script works around:**
- **Slang's optimizer:** it folds `(x + c) - c` to `x`, which IEEE arithmetic forbids, so kernels compile at
  `-O0`.
- **Fusing in SPIR-V:** every float add and subtract is marked `NoContraction`, so no driver fuses a product into
  it. Multiplies are left unmarked, because SPIRV-Cross turns a marked multiply into `fma(a, b, 0)`, which loses
  `-0`.
- **Fusing in Metal:** the Metal source starts with `#pragma METAL fp math_mode(safe)` and
  `#pragma METAL fp contract(off)`. Without them, Metal fuses multiply-adds even with fast math disabled.

**Then wire it up:**
1. Add a Java class beside `GpuGemmf`, and add the kernel to `KERNELS` in `scripts/build-kernels.py`.
2. Add a CPU model of the kernel in a class of its own, registered with one line in `ReferenceModels`.
3. Add a parity test that runs on `Devices.names()`, and assert its `PARAMS` in `KernelManifestTest`.
