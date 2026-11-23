# Performance

This page explains where Vectrix's speed comes from, the rules that keep your own hot loops fast, and the defaults
an engine should pick: which transform representation, which skinning kernel, which jar, math mode and memory
backend. Measured numbers are in [benchmarks](benchmarks.md); this page quotes only a few headline ratios.

## Why Vectrix is fast

- **No allocation in hot paths.** Methods write into a `dest` object or a caller-supplied array instead of returning
  new objects (see [conventions](conventions.md)). A test measures bytes allocated per call for the hot-path APIs.
- **Batch kernels over flat arrays.** `soa.TransformKernels`, `soa.SkinningKernels`, `geometry.CullingKernels` and
  `affine.PackedAffineKernels` loop over structure-of-arrays or packed `float[]` data. Sequential array access is
  what caches, prefetchers and the JIT's loop optimizations handle best.
- **Fewer bytes per transform.** Bulk transform work uses the packed 3x4 affine (12 floats) instead of `Matrix4f`
  (16 floats plus an object header and a reference per instance).
- **Tight, specialized loops.** A kernel is kept generic only if specializing it does not pay. For example, CPU
  skinning defaults to a flat matrix-palette loop because it measured faster than the quaternion form.
- **Vector API paths.** The `simd` jar adds `jdk.incubator.vector` kernels to the batch operations where SIMD pays
  off. Every SIMD path gives the same bits as its scalar path.
- **JIT-friendly code.** Final classes, static kernels, primitive arrays and no boxing let HotSpot inline and
  optimize the hot loops.

## Hot-path rules

Follow these in code that runs every frame:

1. **Do not allocate.** Create vectors, matrices and scratch arrays once and reuse them. Use the overloads with a
   `dest` parameter.
2. **Call batch kernels, not per-object methods in a loop.** One `soa.TransformKernels.composeBatch` call over an
   array beats a loop of `Transformf.compose` calls.
3. **Keep data contiguous and walk it in order.** Structure-of-arrays (`TransformSoA`, `AABBSoA`) and packed arrays
   (`PackedAffineArray`) keep each field in one array. Scattered access (a shuffled `order` array, pointer-chasing
   through objects) is slower for every representation; sort work by memory order where you can.
4. **Compose in TRS, convert late, convert once.** Avoid chains such as TRS to `Matrix4f` to affine to upload in one
   frame phase. See [transform representations](#transform-representations).
5. **Use `Matrix4f` at boundaries**: projection, view, interop with other libraries and shaders that need a full
   `mat4`. Do not make it the bulk per-instance format.
6. **Run setup-time APIs at setup.** Layout calculators (`ShaderLayout`), mesh builders (`MeshletCones.normalCone`)
   and asset encoders (`PackedFormats.packQTangent`) allocate.
7. **Chunking is for scheduling, not speed.** The `...Chunked` kernels and `BatchChunks` split a batch into ranges
   so you can hand them to workers. They do not make a single thread faster.
8. **Measure.** Benchmark hot-path changes with JMH, report time per item, and check `-prof gc` shows
   0 bytes per operation. See the [benchmarking guide](maintainers/benchmarking-guide.md).

## Transform representations

| Representation | Classes | Use for |
|---|---|---|
| TRS objects | `Transformf`, `Transformd` | Authoring, editing and single objects: translation, rotation quaternion, scale. |
| TRS structure-of-arrays | `TransformSoA` with `soa.TransformKernels.composeBatch` | Per-frame hierarchy updates: local-to-world composition in batches. Has a Vector API path. |
| Packed 3x4 affine | `PackedAffineArray` with `PackedAffineKernels` | The bulk runtime form: world bounds, instance upload, skinning palettes. |
| Affine matrix objects | `Matrix4x3f`, `Affine4f` | Affine math on single transforms when you need a matrix (inverse, shear, non-uniform scale under rotation). |
| Full 4x4 matrix | `Matrix4f` | Projections, view matrices, API boundaries. |
| Dual quaternion | `DualQuatTransformf`, `DualQuatSoA` | Quality skinning; see [skinning](#skinning). |

The default per-frame pipeline:

```text
TransformSoA (local)  --composeBatch-->  TransformSoA (world)
Transformf[] (world)  --trsToPackedAffineBatch-->  PackedAffineArray
PackedAffineArray     --transformAabbPackedAffineBatch-->  world AABBs  -->  culling
PackedAffineArray     --uploadPackedAffine-->  float[] upload, 12 floats per instance, in draw order
```

`PackedAffineKernels.trsToPackedAffineBatch` takes a `Transformf[]`; there is no kernel from `TransformSoA` to
packed affine yet. `InstanceSubmissionPipeline` chains skinning, conversion, bounds and upload in one call and is a
working reference for this pipeline. [Getting data to the GPU](guides/gpu-data.md) describes the packed layout, the
kernels and their `order` arrays.

## Skinning

`soa.SkinningKernels` blends up to four joints per vertex. Positions are SoA (`inX`, `inY`, `inZ`), and joint
indices and weights are interleaved, four per vertex, except in the `...SoA` variants, which take one array per
influence. The kernels do not normalize the weights.

| Kernel | Joint data | When |
|---|---|---|
| `SkinningKernels.skinLbs4MatrixPalette` | 12-float matrix palette | **Default.** Fastest linear blend skinning. |
| `SkinningKernels.skinLbs4` | `TransformSoA` (translation, rotation, scale) | Linear blend skinning without building a palette. Slower per vertex. |
| `SkinningKernels.skinLbs4SoA` | `TransformSoA`, per-influence arrays | When your influences are already stored one array per slot. Same speed class as `SkinningKernels.skinLbs4`. |
| `SkinningKernels.skinDualQuat4`, `SkinningKernels.skinDualQuat4SoA` | `DualQuatSoA` | Quality mode: dual quaternions keep volume at twisting joints (no "candy-wrapper" collapse). Slower. |

For the default path, build the palette once per frame with `SkinningKernels.buildMatrixPalette12`, then call
`SkinningKernels.skinLbs4MatrixPalette` for each mesh. `SkinningKernels.buildMatrixPalette12`,
`SkinningKernels.skinLbs4` and `SkinningKernels.skinLbs4SoA` apply each joint's full translation, rotation and
scale; with unit scale they give the same bits as rigid skinning. `SkinningKernels.buildRigidMatrixPalette12` ignores
the scale. The dual quaternion kernels are rigid, since a unit dual quaternion holds no scale. A `PackedAffineArray`
from `PackedAffineKernels.trsToPackedAffineBatch` also works as a palette through `PackedAffineArray.raw()`.

Choose dual quaternion skinning explicitly, per asset or in configuration, where artifacts at elbows and wrists
matter more than speed.

Skinning has no SIMD kernel, in either jar. Linear blend skinning reads joint data through per-vertex indices, which
needs a gather. Gather-based Vector API kernels were measured on arm64 and x86_64 and were slower than the scalar
matrix-palette loop on every CPU but one, so the scalar loop is used everywhere.

## Runtime defaults for an engine

| Setting | Default | Recommendation |
|---|---|---|
| Jar | plain (scalar) | Use the `simd` classifier jar and start the JVM with `--add-modules jdk.incubator.vector`. |
| SIMD switch | on (`vectrix.simd.enabled`) | Leave on. `SimdSupport.setEnabled` toggles it at run time for A/B measurement or to rule out a JVM problem. |
| Math mode | `FAST` (`vectrix.math.mode`) | `FAST` for frame work. `STRICT` for tests and validation runs. |
| Memory backend | FFM | Keep it. On x86_64, consider `-Dvectrix.forceUnsafe=true` if buffer writes dominate a profile. |
| Culling dispatch | SIMD when available (`vectrix.culling.simd`) | Leave on. |

### SIMD

The Vector API is an incubator module, so it ships in a separate jar: depend on Vectrix with
`<classifier>simd</classifier>` and add `--add-modules jdk.incubator.vector` (the JDK then prints a one-line
warning that an incubator module is in use). Without the flag, or with the plain jar, every kernel runs its scalar
path. `SimdSupport.backend` reports which path is in use, `SimdSupport.isVectorApiAvailable` whether the module is
present, and `SimdSupport.preferredFloatLanes` the vector width.

Kernels with Vector API paths:

- `soa.TransformKernels.composeBatch`, from 32 transforms;
- `CullingKernels.frustumCullAabbBatch`, from 64 to 32768 boxes;
- the FFT (`fft.ComplexFftf`, `fft.ComplexFftd`), BLAS (`blas.Blasf`, `blas.Blasd`, `blas.Gemmf`, `blas.Gemmd`)
  and neural-network kernels (`nn.Activations`, `nn.Norms`, `nn.Softmax`, `nn.Formats`).

Skinning has none (see [skinning](#skinning)). Every SIMD path gives the same bits as its scalar path, so the jar
and the switch change speed only. The gain depends on vector width: it is much larger on AVX-512 x86_64 than on
arm64 NEON (see [headline numbers](#headline-numbers)).

### Math mode

`MathMode.FAST`, the default, trusts documented preconditions and lets batch kernels use SIMD. `MathMode.STRICT`
validates inputs (for example, unit quaternions), throws `IllegalArgumentException` on bad ones, and forces every
batch kernel onto its scalar path. That costs time, so use `STRICT` in tests, asset validation and replay checks.
`CoreConfig.setMathMode` sets the process-wide mode; `CoreConfig.runWithMathMode` and
`CoreConfig.callWithMathMode` override it for one task on one thread. For bit-reproducible results across machines
see [determinism](determinism.md).

### Memory backend

Vector and matrix writes into NIO buffers go through FFM by default: bounds-checked and free of deprecated APIs.
Every backend writes the same bytes. On arm64 FFM is as fast as `sun.misc.Unsafe` for loops of transfers and about
1 ns slower for an isolated one. On x86_64 it is about 10 % slower in loops and about 2 times slower for a single
transfer. `-Dvectrix.forceUnsafe=true` switches to `Unsafe`, and the JDK then prints its deprecation warning. The
properties and the raw-address API are described in [getting data to the GPU](guides/gpu-data.md).

## Headline numbers

Ratios from the published snapshots in [benchmarks](benchmarks.md). Apple M4 Max, macOS, Temurin 25.0.4,
measured 2026-11-21, unless marked x86_64: Intel Xeon Platinum 8573C (AVX-512, shared 4-vCPU runner), Temurin
25.0.5, measured 2026-11-22.

| Path | Result |
|---|---|
| Instance upload, 16384 instances | packed affine 2.5 times faster than `Matrix4f` (2.19 vs 5.47 ns per instance) |
| World AABB transform, 16384 boxes | packed affine 2.1 times faster than `Matrix4f` (4.00 vs 8.54 ns per box) |
| Skinning, 4096 vertices, 64 joints | matrix palette 6.19 ns per vertex; quaternion LBS 1.6 times and dual quaternion 1.8 times slower |
| SoA transform compose | Vector API 3.6 times faster than scalar up to 1024 transforms; x86_64: 3.1 to 5.9 times |
| Frustum culling, 16384 boxes | SIMD kernel 1.15 times faster than scalar and 1.8 times faster than a `FrustumIntersection` loop; x86_64: 2.7 times faster than scalar |

These hold for the shapes measured. Re-run the benchmarks on your own hardware before relying on a ratio; the
[benchmarking guide](maintainers/benchmarking-guide.md) explains how.

## See also

- [Getting data to the GPU](guides/gpu-data.md): packed affine kernels, upload writers, memory backends.
- [Conventions](conventions.md): `dest` parameters, array APIs, FAST and STRICT, the packed 3x4 layout.
- [Determinism](determinism.md): which settings change output bits.
- [Benchmarks](benchmarks.md) and the [benchmarking guide](maintainers/benchmarking-guide.md).
- Examples: [`SceneCulling`](../examples/src/main/java/org/dynamisengine/vectrix/examples/SceneCulling.java)
  (batch compose and culling) and [`Animation`](../examples/src/main/java/org/dynamisengine/vectrix/examples/Animation.java)
  (skinning).
- Source: [`soa`](../src/main/java/org/dynamisengine/vectrix/soa/),
  [`affine`](../src/main/java/org/dynamisengine/vectrix/affine/),
  [`simd`](../src/main/java/org/dynamisengine/vectrix/simd/).
