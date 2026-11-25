# Benchmarks

This document defines the benchmark methodology and records current baseline results for Vectrix.

## Tooling
- Harness: JMH
- Java: `--add-modules jdk.incubator.vector`
- Entry: `./scripts/bench-run.sh`
- Bench artifact: `target/benchmarks.jar`
- Normalization: `./scripts/bench-normalize.py`
- Regression compare: `tools/bench-compare` (JSON) or `./scripts/bench-compare.sh`

## Methodology
- Build benchmark jar:
  - `mvn -q clean package -Pbench -DskipTests`
- Run benchmark suite using profile wrappers:
  - `./scripts/bench-quick.sh`
  - `./scripts/bench-full.sh`
  - `./scripts/bench-prof.sh`
  - `./scripts/bench-regression.sh`
  - `./scripts/bench-regression-phaseb.sh`
  - `./scripts/bench-regression-skinning.sh`
  - `./scripts/bench-regression-integration.sh`
  - `./scripts/bench-regression-passh.sh`
- Standard profile defaults:
  - `quick`: `f=1 wi=3 i=5`
  - `full`: `f=3 wi=6 i=10`
  - `prof`: `f=3 wi=6 i=10 -prof gc -prof stack`
  - `regression`: `f=2 wi=4 i=6`
- Output artifacts:
  - `benchmarks/results/YYYY-MM-DD/{quick,full,profiled,regression}.json`
  - `benchmarks/results/YYYY-MM-DD/{quick,full,profiled,regression}.txt`
  - `target/benchmarks/latest.txt`
  - `target/benchmarks/latest.json`
- Normalize per-item metrics:
  - `./scripts/bench-normalize.py benchmarks/results/YYYY-MM-DD/full.json`

## Current Published Snapshot (2026-11-21)
Measured after the code-review fixes (see `CHANGELOG.md`). It replaces the earlier snapshots below, which predate
three changes that invalidate their numbers:

1. **SIMD never ran.** `SimdSupport` failed to detect the Vector API and the benchmark forks did not add
   `--add-modules jdk.incubator.vector`, so every earlier "SIMD" or "Vector API" result measured scalar code.
2. **Packed affine layout.** `PackedAffineArray` changed from basis columns to the row-major
   `AFFINE_3X4_ROW_MAJOR` layout (review M-1); earlier packed-affine numbers used the old layout.
3. **Memory backend.** The default is now the bounds-checked FFM backend, not Unsafe.

Environment: Apple M4 Max, macOS 27.0.1 (Darwin 27.0.0 arm64), Temurin OpenJDK 25.0.4 LTS.
Profile: `quick` (`-f 1 -wi 3 -w 1s -i 5 -r 1s`, AverageTime), one fork per configuration, so treat differences
under about 5 % as noise. Raw results: `benchmarks/results/2026-11-21/*.{csv,txt}`.

All values are ns per call; "per item" divides by the batch size.

**Memory backends** (`MemoryBackendBenchmark`, one call per matrix, `Matrix4f` into a direct buffer):

| Matrices per loop | FFM, ByteBuffer | FFM, FloatBuffer | Unsafe, ByteBuffer | Unsafe, FloatBuffer |
|---|---:|---:|---:|---:|
| 1 | 3.33 | 2.79 | 2.15 | 2.05 |
| 16 | 29.6 | 29.5 | 32.3 | 28.3 |
| 256 | 457 | 473 | 511 | 449 |

On this arm64 machine FFM (the default) matches or beats Unsafe for loops of transfers; an isolated transfer costs about 1 ns more. On x86_64 FFM is slower in loops too (see the x86_64 snapshot).

Where that 1 ns goes (`FfmAccessBenchmark`, 2026-11-22, three forks): per matrix inside a loop of 16, FFM costs 1.91 ns
and Unsafe 2.06 ns, so the stores themselves are not slower. The extra cost of a single call (3.36 vs 2.21 ns) is a
fixed ~1.2 ns per call, consistent with `MemorySegment.ofBuffer`, which the JIT hoists out of loops but must pay once
per call. Measured in straight-line code with no loop, `Matrix4f.get(ByteBuffer)` costs the same on both backends (2.62
vs 2.67 ns). Removing it would need a per-buffer segment cache (which keeps buffers alive and is not thread-safe) or
JDK internals. Two micro-optimisations, flattening the pair-packing helpers and storing pairs through a fixed
little-endian layout, removed the inlining failures reported by `-XX:+PrintInlining` but did not change the timings,
so neither was kept. The raw-address API shows a similar fixed cost (9.6 vs 5.9 ns for one `Matrix4f` write plus read,
5.9 vs 11.1 ns per matrix in a loop of 16).

**Transform compose** (`TransformComposeBenchmark`, per batch):

| Batch | SoA, Vector API | SoA, scalar | Affine4f | Transformf objects | Matrix4f |
|---|---:|---:|---:|---:|---:|
| 64 | 77.9 | 279 | 316 | 339 | 388 |
| 1024 | 1 232 | 4 450 | 7 312 | 7 427 | 9 278 |
| 16384 | 71 097 | 80 000 – 396 000 (bimodal across forks) | 120 860 | 136 242 | 165 719 |

The SoA Vector API kernel is 3.6× faster than scalar up to 1024 transforms and bitwise identical to it.

**Culling, skinning, bounds and upload:**

| Benchmark | Shape | ns per batch | ns per item |
|---|---|---:|---:|
| `FrustumCullingBenchmark.batchKernelSimdForced` | 16384 AABBs | 47 679 | 2.91 |
| `FrustumCullingBenchmark.batchKernelScalarForced` | 16384 AABBs | 54 962 | 3.35 |
| `FrustumCullingBenchmark.frustumIntersectionLoop` | 16384 AABBs | 85 411 | 5.21 |
| `SkinningKernelBenchmark.skinLbs4MatrixPaletteTight` | 4096 vertices, 64 joints | 25 335 | 6.19 |
| `SkinningKernelBenchmark.skinLbs4` (quaternion LBS) | 4096 vertices, 64 joints | 41 504 | 10.13 |
| `SkinningKernelBenchmark.skinDualQuat4` | 4096 vertices, 64 joints | 46 306 | 11.31 |
| `TransformAabbBenchmark.transformAabbBatch` packedAffine | 16384 boxes | 65 477 | 4.00 |
| `TransformAabbBenchmark.transformAabbBatch` matrix4f | 16384 boxes | 139 974 | 8.54 |
| `InstanceUploadBenchmark.instanceUploadPackedAffine` | 16384 instances | 35 881 | 2.19 |
| `InstanceUploadBenchmark.instanceUploadMatrix4f` | 16384 instances | 89 568 | 5.47 |
| `PackedAffineConversionBenchmark.trsToPackedAffineBatch` | 4096 | 13 318 | 3.25 |

`skinLbs4SoASimdForced` and `skinLbs4Vector` now run the scalar kernel (41.8 and 41.4 µs, the same as scalar): a
gather-based Vector API kernel measured 6.8× slower on arm64, where NEON has no gather instruction.

**Rendering math** (count 4096; FFT complex count 1024):

| Benchmark | ns per batch | ns per item |
|---|---:|---:|
| `CurveBenchmark.bezierEvaluateLoop` | 8 078 | 1.97 |
| `CurveBenchmark.mapArcLengthLoop` | 17 872 | 4.36 |
| `OpticsBenchmark.fresnelDielectricLoop` | 8 727 | 2.13 |
| `OpticsBenchmark.thinFilmRgbLoop` | 108 233 | 26.4 |
| `ShBenchmark.evaluateL3` | 37 681 | 9.20 |
| `ShBenchmark.projectL3` | 54 952 | 13.4 |
| `ShHotPathBenchmark.evaluateIrradianceZeroAllocLoop` | 19 398 | 4.74 |
| `ShHotPathBenchmark.projectSampleZeroAllocLoop` | 32 994 | 8.06 |
| `LtcBenchmark.ltcTableSampleLoop` | 41 436 | 10.1 |
| `LtcBenchmark.ltcFormFactorRectClippedLoop` | 137 550 | 33.6 |
| `QuaternionRotationBenchmark.swingTwistLoop` | 28 784 | 7.03 |
| `QuaternionRotationBenchmark.angularVelocityLoop` | 49 960 | 12.2 |
| `LowDiscrepancyBenchmark.sobolScrambledLoop` | 67 233 | 16.4 |
| `FftBenchmark.forwardInverse` | 14 044 | 13.7 |
| `FftBenchmark.circularConvolution` | 21 546 | 21.0 |

Compared with the February snapshot, per item:
- `angularVelocityLoop` is about 25 % slower (12.2 vs 9.7 ns): `atan2` replaced `acos(w)`, which returned 0 for slow
  rotations in float (review H-7). This is the price of the accuracy fix.
- The FFT is faster (13.7 vs 15.8 ns for `forwardInverse`, 21.0 vs 24.4 for `circularConvolution`): the twiddle is
  now computed once per stage, which more than pays for running the recurrence in double (review M-7).
- `ltcFormFactorRectClippedLoop` is about 27 % faster (33.6 vs 45.7 ns): exact streaming clipping replaced vertex
  projection (review M-5).
- SH evaluation and projection are back at their February cost. The first run of this snapshot found them 2× slower
  after a refactor that computed the basis through a per-index switch; the straight-line code was restored, with a
  test (`ShConsistencyTest`) that keeps every copy of the formulas bit-identical. SH and FFT rows above are the
  re-measured values (`sh-after-fix`, `fft-after-fix`).
- Other differences are within about 15 % and mostly within run-to-run noise for a single fork.

## x86_64 Snapshot (2026-11-22)
Same groups and profile as the 2026-11-21 snapshot (`scripts/bench-snapshot.sh`), run by the `Benchmark Snapshot`
workflow on a GitHub-hosted runner: Intel Xeon Platinum 8573C (AVX-512, 4 vCPUs, shared VM), Ubuntu, Temurin
25.0.5. Raw results and `environment.txt`: `benchmarks/results/2026-11-22-x86/`. A shared VM is slower and noisier
than the M4 workstation (most errors stay under 5 %), so compare ratios within this run, not absolute times across
machines.

**SIMD vs scalar** (same machine, ns per batch):

| Kernel | Shape | Vector API | Scalar | Speed-up (x86) | Speed-up (M4) |
|---|---|---:|---:|---:|---:|
| `TransformKernels.composeBatch` (SoA) | 64 | 108 | 636 | 5.9× | 3.6× |
| `TransformKernels.composeBatch` (SoA) | 1024 | 3 268 | 10 032 | 3.1× | 3.6× |
| `TransformKernels.composeBatch` (SoA) | 16384 | 63 160 | 213 577 | 3.4× | bimodal |
| `CullingKernels.frustumCullAabbBatch` | 16384 | 69 097 (`SimdForced`) | 187 977 | 2.7× | 1.15× |

AVX-512 gives the SIMD kernels much more headroom than NEON. In this run culling's default dispatch
(`batchKernelAuto`, 189 199) still took the scalar path, because SIMD culling was opt-in. It has since become the
default: its only documented difference from scalar (NaN bounds) had already been fixed, and a special-values parity
test (`SimdPathTest`) confirms identical classification. On the M4 the default is now 13–21 % faster than scalar at
64 to 16384 boxes (190 vs 241 ns at 64, 48.3 vs 55.2 µs at 16384).

**Memory backends** (ns per call, one `Matrix4f` per transfer):

| Matrices per loop | FFM, ByteBuffer | Unsafe, ByteBuffer | FFM raw address (write + read) | Unsafe raw address |
|---|---:|---:|---:|---:|
| 1 | 6.0 | 3.3 | 16.7 | 9.3 |
| 16 | 57.0 | 48.1 | 183 | 113 |
| 256 | 863 | 783 | 2 285 | 1 829 |

Unlike on arm64, FFM is slower than Unsafe on x86 even in loops: about 10 % for buffer transfers and 25 % for the
raw-address API at 256 matrices, and about 2× for a single transfer. FFM remains the default for safety (bounds
checks, no deprecated API); `-Djoml.forceUnsafe=true` recovers the difference where it matters.

**Defaults re-checked on x86:** packed affine stays the fastest bulk form (AABB transform 166 µs vs 513 µs for
`Matrix4f` at 16384; instance upload 55 µs vs 182 µs), and matrix-palette tight LBS stays the fastest skinning path
(72 µs vs 98 µs for quaternion LBS at 4096 vertices). `skinLbs4SoASimdForced` equals scalar, as on arm64: there is no
SIMD skinning kernel.

**Gather-based SIMD skinning** (`GatherLbsKernel`, benchmark-only; bitwise equal to scalar LBS, checked in the
benchmark setup). Measured in a separate run, which landed on an AMD EPYC 7763 (Zen 3, AVX2, no AVX-512): GitHub
assigns different CPUs to different runs, so compare within the run only. Results in
`benchmarks/results/2026-11-22-x86-skinning/`.

| 4096 vertices, 64 joints | x86 (EPYC 7763) | arm64 (M4, 1024 vertices ×4) |
|---|---:|---:|
| `skinLbs4MatrixPaletteTight` (default path) | 48.3 µs | 25.8 µs |
| `skinLbs4SoAGather` (Vector API gather) | 65.9 µs | 288.5 µs |
| `skinLbs4SoAScalarForced` (quaternion LBS) | 104.1 µs | 42.5 µs |

On x86 hardware gathers make the SIMD kernel 1.6× faster than scalar quaternion LBS (on NEON it is 6.8× slower), but
it is still 1.36× slower than the matrix-palette path that skinning already uses, so it is not promoted.

**Gather-based matrix-palette skinning** (`GatherPaletteKernel`, benchmark-only, two palette layouts, both bitwise
equal to `skinLbs4MatrixPalette`, checked in setup). Measured on an AMD EPYC 9V74 (Zen 4, AVX-512), with the
quaternion kernels re-run alongside. Results in `benchmarks/results/2026-11-22-x86-gather-palette/`.

| 4096 vertices, 64 joints | x86 (EPYC 9V74, AVX-512) | arm64 (M4) |
|---|---:|---:|
| `skinLbs4MatrixPaletteTight` (default path) | 50.3 µs | 25.5 µs |
| `skinLbs4MatrixPaletteGatherAos` (12 gathers per joint from the AoS palette) | 139.0 µs | 520 µs |
| `skinLbs4MatrixPaletteGatherSoa` (SoA palette, transpose included) | 141.0 µs | 504 µs |
| `skinLbs4SoAGather` (quaternion gather) | 47.4 µs | 284 µs |
| `skinLbs4SoAScalarForced` (quaternion scalar) | 88.3 µs | 42.4 µs |

Contiguous and random palette access measure the same, within 1 %. Conclusions:
- **Matrix-palette gathers are 2.8× slower than the tight kernel even with AVX-512.** They need 48 gathers per
  lane group, and that costs more than the arithmetic they save.
- **The quaternion gather is 6 % faster than the default on Zen 4 alone.** It is 1.36× slower on Zen 3 and 11× slower
  on arm64. That is not worth CPU-specific dispatch.

The tight matrix-palette kernel stays the default, and the gather experiment is closed. The benchmark kernels remain,
so future CPUs can be re-measured.

## Historical Snapshot (2026-03-06)
Superseded by the 2026-11-21 snapshot above; kept for the decision history. Its SIMD figures measured scalar code
and its packed-affine figures used the old column layout.

- Environment:
  - Apple M4 Max, macOS 26.3 (Darwin 25.3.0 arm64)
  - Temurin OpenJDK 25.0.1+8 LTS
- How obtained:
  1. `mvn -q clean package -Pbench -DskipTests`
  2. Constrained full/prof runs:
     - `./scripts/bench-full.sh` (targeted regex sets)
     - `./scripts/bench-prof.sh` (targeted regex sets with `-prof gc -prof stack`)
  3. Normalization:
     - `./scripts/bench-normalize.py benchmarks/results/2026-03-06/<run>.json`
- Primary artifacts:
  - `benchmarks/results/2026-03-06/passE-gpu-layout.{json,txt,normalized.csv}`
  - `benchmarks/results/2026-03-06/passE-skinning-equivalence.{json,txt,normalized.csv}`
  - `benchmarks/results/2026-03-06/integration-slice.{json,txt,normalized.csv}`
  - `benchmarks/results/2026-03-06/subsystem-integration.{json,txt,normalized.csv}`
  - `benchmarks/results/2026-03-06/passG-*.{json,txt,normalized.csv}`
  - `benchmarks/results/2026-03-06/passG-prof-*.{json,txt,normalized.csv}`
  - `benchmarks/results/2026-03-06/passH-geometry-aabb.{json,txt,normalized.csv}`
  - `benchmarks/results/2026-03-06/passH-prof-geometry-aabb.{json,txt,normalized.csv}`
  - `benchmarks/results/2026-03-06/passH-rendering-lut.{json,txt,normalized.csv}`
  - `benchmarks/results/2026-03-06/passH-prof-rendering-lut.{json,txt,normalized.csv}`

## Coverage
Current benchmark suites (2026-03-06):
- Core latency/throughput:
  - `JomlBenchmark`, `JomlFmaBenchmark`, `Affine4fBenchmark`, `BatchMatrixBenchmark`, `ReductionBenchmark`
- Transform/runtime prep:
  - `TransformComposeBenchmark`, `TransformAabbBenchmark`, `PackedAffineConversionBenchmark`, `InstanceUploadBenchmark`, `QuatMatrixConversionBenchmark`
- Skinning:
  - `SkinningBenchmark`, `SkinningKernelBenchmark`, `SkinningEquivalenceBenchmark`
- Engine-composed flows:
  - `IntegrationPipelineBenchmark`
- Geometry/mesh/culling:
  - `FrustumCullingBenchmark`, `MeshMathBenchmark`
- GPU/interoperability/memory:
  - `GpuPackingBenchmark`, `InteropBenchmark`, `MemoryBackendBenchmark`, `QuatCompressionBenchmark`
- Rendering math domains:
  - `CurveBenchmark`, `ShBenchmark`, `ShHotPathBenchmark`, `FftBenchmark`, `LtcBenchmark`, `OpticsBenchmark`, `LowDiscrepancyBenchmark`, `QuaternionRotationBenchmark`
- New expansion suites:
  - `PhysicsMathBenchmark`, `HashBenchmark`, `SdfBenchmark`, `SamplingBenchmark`, `ColorBenchmark`, `EasingBenchmark`, `ParallelTransformBenchmark`
  - `RenderingMathBenchmark`, `GeometryIntersectionBenchmark`, `SimdSupportBenchmark`, `FrustumRayBuilderBenchmark`, `CoreBatchUtilityBenchmark`

## Benchmark Coverage Gaps (Current)
### Covered Well
- transforms / affine / packed-affine runtime paths
- GPU upload/layout staging
- skinning kernel families and equivalence checks
- composed integration and subsystem paths
- renderingmath coverage (`Interpolation*`, `BentNormalCone*`, LUT builders)
- geometry intersection helpers (`Intersection*`, `RayAabIntersection`, `PolygonsIntersection`, `FrustumRayBuilder`)
- SIMD backend detection (`SimdSupport`)

### Partially Covered
- core (curated batch utility coverage exists; deeper integrator/controller families still selective)
- physics/hash/SDF/sampling/color/easing are benchmarked but not deeply profiled across all workload shapes

### Remaining Notable Gaps
1. wider renderingmath kernel shapes beyond current representative methods
2. broader core batch coverage for upcoming runtime integrator/controller usage
3. deeper parallel/scaling sweeps for newly added suites

### Triage Rule For New Suites
Every new suite should end with one classification:
1. `Promote` (optimize now)
2. `Good enough` (track, no immediate optimization)
3. `Parked` (defer)

Acquisition path for the current suite index above:
- Build benchmark artifact:
  - `mvn -q clean package -Pbench -DskipTests`
- Run profiles:
  - `./scripts/bench-quick.sh`
  - `./scripts/bench-full.sh`
  - `./scripts/bench-prof.sh`
- Normalize and compare:
  - `./scripts/bench-normalize.py benchmarks/results/YYYY-MM-DD/full.json`
  - `./scripts/bench-compare.sh <baseline.json> <candidate.json> <thresholds...>`
- Preserve artifacts:
  - `benchmarks/results/YYYY-MM-DD/*.json`
  - `benchmarks/results/YYYY-MM-DD/*.normalized.csv`

## Historical Baseline (2026-02-22)
Superseded; see the 2026-11-21 snapshot. Same caveats as the 2026-03-06 snapshot.

Baseline capture profile used:
- `BENCH_REGEX='org.dynamisengine.vectrix.bench.(CurveBenchmark|ShBenchmark|FftBenchmark|OpticsBenchmark).*'`
- `FORKS=1 WARMUP_ITERS=1 MEASURE_ITERS=1 THREADS=1 TIME_UNIT=ns`

Hardware/software:
- CPU model: Apple M4 Max
- OS: macOS 26.3 (Darwin 25.3.0 arm64)
- JVM: Temurin OpenJDK 25.0.1+8 LTS
- Date: 2026-02-22 15:14:08 EST

Result artifacts:
- `target/benchmarks/jmh-20260222-150521.csv`
- `target/benchmarks/jmh-20260222-150521.txt`

Representative results (AverageTime, ns/op):
- CurveBenchmark:
  - `bezierEvaluateLoop(count=256)` = 463.155
  - `bezierEvaluateLoop(count=4096)` = 7455.766
  - `bezierEvaluateLoop(count=16384)` = 30153.349
  - `mapArcLengthLoop(count=256)` = 1143.668
  - `mapArcLengthLoop(count=4096)` = 16802.590
  - `mapArcLengthLoop(count=16384)` = 61175.956
- FftBenchmark:
  - `circularConvolution(complexCount=256)` = 5168.600
  - `circularConvolution(complexCount=1024)` = 25021.071
  - `circularConvolution(complexCount=4096)` = 119555.242
  - `forwardInverse(complexCount=256)` = 3352.452
  - `forwardInverse(complexCount=1024)` = 16172.115
  - `forwardInverse(complexCount=4096)` = 77706.213
- OpticsBenchmark:
  - `fresnelDielectricLoop(count=256)` = 451.077
  - `fresnelDielectricLoop(count=4096)` = 7211.630
  - `fresnelDielectricLoop(count=16384)` = 29080.754
  - `thinFilmRgbLoop(count=256)` = 4524.113
  - `thinFilmRgbLoop(count=4096)` = 95849.576
  - `thinFilmRgbLoop(count=16384)` = 433526.007
- ShBenchmark:
  - `evaluateL3(count=256)` = 2380.876
  - `evaluateL3(count=4096)` = 38450.799
  - `evaluateL3(count=16384)` = 154013.449
  - `projectL3(count=256)` = 3439.568
  - `projectL3(count=4096)` = 53115.972
  - `projectL3(count=16384)` = 193231.278

Normalized per-call costs (`ns/op-per-call = ns/op ÷ count`):

| Benchmark | 256 | 4096 | 16384 |
|---|---:|---:|---:|
| `bezierEvaluateLoop` | 1.809 | 1.820 | 1.840 |
| `mapArcLengthLoop` | 4.468 | 4.102 | 3.734 |
| `circularConvolution` | 20.190 | 24.435 | 29.188 |
| `forwardInverse` | 13.096 | 15.793 | 18.971 |
| `fresnelDielectricLoop` | 1.762 | 1.761 | 1.776 |
| `thinFilmRgbLoop` | 17.672 | 23.401 | 26.461 |
| `evaluateL3` | 9.300 | 9.387 | 9.401 |
| `projectL3` | 13.436 | 12.968 | 11.794 |

Interpretation notes:
- `thinFilmRgbLoop` shows a size-dependent rise in per-call cost; this was optimized by sharing interface/Fresnel terms across RGB channels.
- FFT paths (`forwardInverse`, `circularConvolution`) also rise with problem size as expected for increasing memory/compute pressure.
- `mapArcLengthLoop` and `projectL3` do not regress with larger counts; both trend slightly cheaper per call at scale.

## Historical Coverage Pass (2026-02-22)
Superseded; see the 2026-11-21 snapshot. The `skinLbs4SoASimdForced` row measured scalar code.

Latest end-to-end capture profile:
- `BENCH_REGEX='org.dynamisengine.vectrix.bench.(CurveBenchmark|FftBenchmark|OpticsBenchmark|ShBenchmark|QuaternionRotationBenchmark|ShHotPathBenchmark|LtcBenchmark|LowDiscrepancyBenchmark|SkinningKernelBenchmark).*'`
- `FORKS=1 WARMUP_ITERS=1 MEASURE_ITERS=1 THREADS=1 TIME_UNIT=ns`

Result artifacts:
- `target/benchmarks/jmh-20260222-153429.csv`
- `target/benchmarks/jmh-20260222-153429.txt`

Thin-film scaling after optimization (`thinFilmRgbLoop`):
- `count=256`: `4287.360 ns/op` (`16.748 ns/call`)
- `count=4096`: `98247.842 ns/op` (`23.986 ns/call`)
- `count=16384`: `402556.168 ns/op` (`24.570 ns/call`)

Representative new benchmark families (AverageTime, ns/op):
- `QuaternionRotationBenchmark.swingTwistLoop(count=4096)` = `26891.263`
- `QuaternionRotationBenchmark.angularVelocityLoop(count=4096)` = `39889.576`
- `QuaternionRotationBenchmark.integrateAngularVelocityLoop(count=4096)` = `19030.103`
- `ShHotPathBenchmark.projectSampleZeroAllocLoop(count=4096)` = `31328.699`
- `ShHotPathBenchmark.evaluateIrradianceZeroAllocLoop(count=4096)` = `18695.931`
- `LtcBenchmark.ltcTableSampleLoop(count=4096)` = `39771.203`
- `LtcBenchmark.ltcFormFactorRectClippedLoop(count=4096)` = `187210.109`
- `LowDiscrepancyBenchmark.sobolScrambledLoop(count=4096)` = `58707.729`
- `LowDiscrepancyBenchmark.sobolScrambledBatch2DLoop(count=4096)` = `67525.768`
- `SkinningKernelBenchmark.skinDualQuat4(vertices=4096)` = `45042.917`

Normalized per-op costs from `target/benchmarks/jmh-20260222-153429.csv`
(`ns/call = ns/op ÷ N`):

| Benchmark | N=256 | N=4096 | N=16384 |
|---|---:|---:|---:|
| `bezierEvaluateLoop` | 1.837 | 1.835 | 1.841 |
| `mapArcLengthLoop` | 4.392 | 4.054 | 3.735 |
| `fresnelDielectricLoop` | 1.791 | 1.767 | 1.767 |
| `thinFilmRgbLoop` | 16.747 | 23.986 | 24.570 |
| `evaluateL3` | 9.299 | 9.448 | 9.376 |
| `projectL3` | 13.431 | 12.959 | 11.724 |
| `swingTwistLoop` | 6.584 | 6.565 | 6.692 |
| `angularVelocityLoop` | 9.529 | 9.739 | 9.618 |
| `integrateAngularVelocityLoop` | 4.892 | 4.646 | 4.349 |
| `projectSampleZeroAllocLoop` | 7.666 | 7.649 | 7.780 |
| `evaluateIrradianceZeroAllocLoop` | 4.575 | 4.564 | 4.569 |
| `ltcTableSampleLoop` | 8.597 | 9.710 | 9.786 |
| `ltcFormFactorRectClippedLoop` | 46.199 | 45.706 | 46.174 |
| `sobolScrambledLoop` | 6.095 | 14.333 | 35.635 |
| `sobolScrambledBatch2DLoop` | 6.666 | 16.486 | 35.610 |
| `skinLbs4` | 9.803 | 9.798 | 9.773 |
| `skinDualQuat4` | 10.986 | 10.997 | 10.968 |
| `skinLbs4SoAAuto` | 9.925 | 9.803 | 9.822 |
| `skinLbs4SoAScalarForced` | 9.819 | 9.818 | 9.844 |
| `skinLbs4SoASimdForced` | 10.008 | 10.024 | 10.012 |

FFT benchmarks use `complexCount` (1024 mid-point):

| Benchmark | N=256 | N=1024 | N=4096 |
|---|---:|---:|---:|
| `circularConvolution` | 20.171 | 24.369 | 29.212 |
| `forwardInverse` | 13.160 | 15.917 | 19.238 |

## Notes
- Baselines should be compared only across matching hardware/JVM settings.
- Use longer runs and isolated machine conditions for release-grade publication.

## Phase B Regression Gates
Focused regression gate suite to protect Pass B gains:
- `regression-phaseb-aabb`:
  - `TransformAabbBenchmark.transformAabbBatch`
  - params: `count=16384`, `representation=matrix4f,packedAffine`, `traversalMode=SEQUENTIAL,RANDOM`, `accessPattern=hot`
- `regression-phaseb-upload`:
  - `InstanceUploadBenchmark.instanceUploadMatrix4f`
  - `InstanceUploadBenchmark.instanceUploadPackedAffine`
  - params: `instances=16384`, `traversalMode=SEQUENTIAL,RANDOM`, `accessPattern=hot`

Intent:
- guard packed-affine AABB and upload winners,
- retain one matrix baseline for relative comparison,
- retain one locality-sensitive random traversal case.

## Skinning Regression Gates
Focused gate suite for current skinning policy:
- `regression-skinning-lbs4`:
  - `SkinningKernelBenchmark.skinLbs4`
  - params: `vertices=16384`, `paletteAccess=contiguous,random`, `paletteSize=64`
- `regression-skinning-lbs4vector`:
  - `SkinningKernelBenchmark.skinLbs4Vector`
  - same params, tracked as experimental target.
- `regression-skinning-equivalence`:
  - `SkinningEquivalenceBenchmark.skinningEquivalent`
  - params: `vertices=16384`, `paletteSize=512`, `writeMode=fullWrite`, `path=legacyLbs,kernelMatrixTight,kernelLbs`
  - intent: protect stripped matrix-palette tight baseline and monitor `kernelLbs` as non-default comparator.

## Integration Regression Gate
Composed-path gate to protect runtime doctrine under end-to-end flow:
- `regression-integration-slice`:
  - `IntegrationPipelineBenchmark.integrationPackedPipeline`
  - `IntegrationPipelineBenchmark.integrationMatrixPipeline`
  - params: `count=16384`, `vertices=4096`, `traversalMode=SEQUENTIAL,RANDOM`

## Pass H Regression Gates
Focused gate suite for Pass H winners:
- `regression-passh-geometry-rayaab`:
  - `GeometryIntersectionBenchmark.rayAabBatch`
  - params: `count=16384`, `precision=float`, `distribution=missHeavy`, `accessPattern=random`, `verts=16`
  - intent: protect miss-heavy/scattered ray-AABB win.
- `regression-passh-sss-lut`:
  - `RenderingMathBenchmark.buildSssLut`
  - params: `count=1024`, `precision=float`, `mode=arrayBatch`, `distribution=uniform`, `accessPattern=sequential`, `resolution=64`, `quality=medium`
  - intent: protect SSS LUT invariant-hoisting win on comparable shape.
