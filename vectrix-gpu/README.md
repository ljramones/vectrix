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

| Class | Twin of | Kernel | Matches the CPU |
|---|---|---|---|
| `GpuGemmf` | `blas.Gemmf.gemm` | `C = α·op(A)·op(B) + β·C`, row-major, any transposes, offsets and strides | bit for bit on IEEE devices |
| `GpuPhilox` | `hash.Philox.word`, `Philox.uniform` | fills a buffer with a counter-based random stream | bit for bit on every device (integer only) |
| `GpuSoftmax` | `nn.Softmax.softmax` | softmax over the rows of a matrix, in place or not | bit for bit on IEEE devices |

## Using it

Coordinates: `org.dynamisengine:vectrix-gpu:3.0.0` (module `org.dynamisengine.vectrix.compute`), with
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
- **Data:** buffers hold little-endian 32-bit values.

### Where the GPU can differ from the CPU

- **Subnormals:** GPUs that flush subnormal floats (magnitude below 2⁻¹²⁶) to zero, including every Apple GPU, give
  0 where Vectrix gives a subnormal. Values computed from it follow from the zero. For example, a softmax
  probability below 2⁻¹²⁶ comes out as 0.
- **MoltenVK fast math:** on Vulkan through MoltenVK, fast math must be off (`MVK_CONFIG_FAST_MATH_ENABLED=0` in the
  environment). With it on, the Metal compiler reassociates arithmetic and the results differ. Native Metal needs
  nothing: the kernels switch off fusing themselves.
- **Other devices:** anything that doesn't round single-precision add, multiply and divide correctly, or doesn't fuse
  `fma`, will differ in the last bits. The tests show which.

### Limits

- **Indices:** element indices must stay below 2³².
- **Softmax row length:** at most `GpuSoftmax.MAX_ROW` (262,144) elements.
- **Philox fill length:** at most `GpuPhilox.MAX_FILL` values.
- **Buffers:** a destination must be a different buffer from the inputs. The one exception is in-place softmax,
  where `y` is `x` at the same offset and stride.

## Building

Install the library first. vectrix-gpu always builds against the library version in the same commit.

```bash
mvn install -DskipTests                       # in the repository root
mvn -f vectrix-gpu/pom.xml verify
```

DynamisGPU comes from its GitHub Release when it isn't already in your local repository:

```bash
curl -fsSLO https://github.com/ljramones/DynamicGPU/releases/download/v1.0.2/dynamisgpu-1.0.2-maven.zip
unzip -q dynamisgpu-1.0.2-maven.zip && cp -R dynamisgpu-1.0.2/repository/. ~/.m2/repository/
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
1.0.2, every test passes on both native Metal and MoltenVK.

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
  the helpers in `common.slang` (`jmax`/`jmin` for `Math.max`/`min`, `reproducibleExp`, `pairwiseBlocks` for the
  `Blasf` reduction order).
- **Declare bindings in order:** buffers in slot order, then the `[[vk::push_constant]]` parameter block, all fields
  32-bit. Metal then numbers buffers by slot and puts the parameters at the next index, as DynamisGPU expects; the
  script checks this.
- **Don't reread group memory you've copied to a local:** if other threads may write that group variable later, keep
  the value in its own group variable that nothing writes again for the row (see `softmax_row.slang`).
- **Keep barriers in uniform control flow:** every thread of a group must reach the same barriers. Loop bounds may
  depend only on parameters and the group index.

**What the script works around:**
- **Slang's optimizer:** it folds `(x + c) - c` to `x`, which IEEE arithmetic forbids, so kernels compile at
  `-O0`.
- **Fusing in SPIR-V:** every float add and subtract is marked `NoContraction`, so no driver fuses a product into
  it. Multiplies are left unmarked, because SPIRV-Cross turns a marked multiply into `fma(a, b, 0)`, which loses
  `-0`.
- **Fusing in Metal:** the Metal source starts with `#pragma METAL fp math_mode(safe)` and
  `#pragma METAL fp contract(off)`. Without them, Metal fuses multiply-adds even with fast math disabled.

**Then wire it up:**
1. Add a Java class beside `GpuGemmf`.
2. Add a model of the kernel to `ReferenceProvider`.
3. Add a parity test that runs on `Devices.names()`.
