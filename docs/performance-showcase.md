# Vectrix Performance Showcase

Date: 2026-03-06

## Executive Summary
Vectrix is now running with benchmark-driven runtime defaults, not ad-hoc hot-path choices.

Current defaults:
1. Packed-affine (`3x4`) for bulk transform/update/upload work.
2. Matrix-palette tight LBS (`skinLbs4MatrixPalette`) for default CPU skinning.
3. `Matrix4f` as fallback/interoperability format, not default bulk runtime form.
4. Locality-first traversal as a first-order rule for large batches.

## Environment
Published showcase results were captured on:
1. Apple M4 Max
2. macOS 26.3 (Darwin 25.3.0 arm64)
3. Temurin OpenJDK 25.0.1+8 LTS

Benchmark methodology and environment controls:
1. `BENCHMARKS.md`
2. `docs/benchmarking-guide.md`
3. `docs/benchmark-environment.md`

## How The Data Was Obtained
Standard path:

```bash
mvn -q clean package -Pbench -DskipTests
./scripts/bench-full.sh
./scripts/bench-prof.sh
./scripts/bench-normalize.py benchmarks/results/2026-03-06/<run>.json
```

Primary result artifacts:
1. `benchmarks/results/2026-03-06/passE-gpu-layout.{json,txt,normalized.csv}`
2. `benchmarks/results/2026-03-06/passE-skinning-equivalence.{json,txt,normalized.csv}`
3. `benchmarks/results/2026-03-06/integration-slice.{json,txt,normalized.csv}`
4. `benchmarks/results/2026-03-06/subsystem-integration.{json,txt,normalized.csv}`
5. `benchmarks/results/2026-03-06/passH-geometry-aabb.{json,txt,normalized.csv}`
6. `benchmarks/results/2026-03-06/passH-rendering-lut.{json,txt,normalized.csv}`

## Why These Results Are Achievable On The JVM
Vectrix relies on modern HotSpot behavior plus data-oriented kernel design:
1. JIT specialization from runtime profiles.
2. Escape analysis and allocation elimination in hot loops.
3. Aggressive inlining/loop optimization for tight kernels.
4. Vector API paths (`jdk.incubator.vector`) where SIMD shape is favorable.

## Workload Shapes Tested
Representative sizes map to common engine workload bands:

| Shape | Example Use |
|---|---|
| `64` | small actor groups / animation jobs |
| `1024` | scene node / instance batches |
| `16384` | large instance, culling, or submission batches |

## Headline Kernel Results
All values below are normalized `ns/item`.

### GPU Transform Layout Write Path
Shape: `count=16384`, `accessPattern=RANDOM`, `sourceRep=packedAffine`

1. `packedAffine`: `3.192`
2. `matrix4f`: `3.461`
3. `instanceCompact`: `17.568`

Takeaway:
1. packed-affine is the fastest practical default write path.
2. matrix fallback is close enough for compatibility paths.
3. compact path is a niche path, not a default throughput path.

### Skinning Equivalence Resolution
Shape: `vertices=16384`, `paletteSize=512`, `writeMode=fullWrite`

1. `legacyLbs`: `6.652`
2. `kernelLbs`: `9.817`
3. `kernelMatrixTight`: `6.082`

Takeaway:
1. `kernelMatrixTight` is the default hot-path CPU skinning baseline.
2. generic kernel shape is retained but not preferred in this workload.

### Geometry Miss-Path Tightening
Shape: `rayAabBatch`, `count=16384`, `precision=float`, `distribution=missHeavy`, `accessPattern=random`

1. Pass G baseline: `7.164`
2. Pass H optimized: `1.863`
3. Improvement: ~`3.85x`

Takeaway:
1. miss-heavy/scattered ray-AABB path now has a materially cheaper default hot-kernel shape.
2. this directly targets high-volume query-style workloads (picking, broad-phase helpers, editor ray tests).

### SSS LUT Builder Tightening
Shape: `buildSssLut`, `resolution=64`, `quality=medium`

1. Pass G baseline: `2515.40`
2. Pass H optimized: `549.42`
3. Improvement: ~`4.58x`

Takeaway:
1. invariant-hoisted kernel precomputation produced a large real gain for SSS precompute work.
2. transmittance LUT path remained near-flat on comparable shape (~`1.02x`), so further work there is deferred.

## Composed Pipeline Results
Integration slice (`IntegrationPipelineBenchmark`) and subsystem path (`SubsystemIntegrationBenchmark`) both preserve kernel winners under composition.

### Integration Slice
`count=16384`:
1. matrix pipeline: `25.563` (seq), `33.600` (random)
2. packed pipeline: `11.337` (seq), `16.668` (random)

### Subsystem Integration
`count=16384`:
1. matrix fallback: `25.171` (seq), `32.415` (random)
2. packed default: `11.175` (seq), `16.925` (random)

Comparison interpretation (`count=16384`, subsystem path):
1. Sequential: packed is ~`2.25x` faster (`25.171 / 11.175`).
2. Random: packed is ~`1.92x` faster (`32.415 / 16.925`).

Takeaway:
1. packed-affine wins are not just microbench artifacts; they survive end-to-end composition.
2. random/scattered access hurts all paths, but packed defaults remain materially faster.

## Allocation Signal (Profiled)
Representative profiled slices (`gc.alloc.rate.norm`):
1. GPU layout:
   - packed-affine: `27.998 B/op`
   - matrix4f: `29.827 B/op`
   - compact: `140.656 B/op`
2. Skinning equivalence:
   - legacy: `57.672 B/op`
   - kernelLbs: `85.169 B/op`
   - kernelMatrixTight: `53.970 B/op`
3. Subsystem integration (`count=16384`):
   - matrix fallback: `220.893 B/op` (seq), `248.223 B/op` (random)
   - packed default: `98.367 B/op` (seq), `147.073 B/op` (random)

Takeaway:
1. current defaults are faster and generally lighter on allocation.

## Runtime Doctrine (Current)
See:
1. `docs/engine-runtime-defaults.md`
2. `docs/performance-state-of-the-union.md`
3. `docs/skinning-runtime-policy.md`

Current doctrine:
1. Packed-affine default for bulk runtime transform/update/upload.
2. Matrix-palette tight LBS default for CPU skinning.
3. Miss-fast `Intersection*.testRayAab` default for miss-heavy query paths.
4. Invariant-hoisted `SssLutBuilder` default for SSS precompute.
5. Matrix fallback retained explicitly for boundaries/interoperability.
6. Vector skinning remains experimental pending repeated wins.

## Implications For Engine Architecture
1. Use packed-affine transforms for bulk runtime processing.
2. Reserve `Matrix4f` for boundary/interoperability code.
3. Prefer tight specialized kernels over overly generic hot-loop abstractions.
4. Preserve locality in large traversal/update workloads.

## Regression Discipline
Active benchmark protection:
1. `scripts/bench-regression-phaseb.sh`
2. `scripts/bench-regression-skinning.sh`
3. `scripts/bench-regression-integration.sh`
4. `scripts/bench-regression-passh.sh`

Purpose:
1. protect packed-affine AABB/upload winners,
2. protect skinning baseline (`kernelMatrixTight` equivalence gate),
3. protect composed integration behavior,
4. protect Pass H geometry miss-path and SSS LUT winners.

## Limits And Scope
These results are strong for current hardware/JDK and tested workload shapes.
Do not generalize across machines without reruns under the same methodology.

## Supporting Decision Memos
1. `docs/performance-passE-gpu-layout-decision.md`
2. `docs/performance-passE-skinning-resolution.md`
3. `docs/performance-integration-slice-findings.md`
4. `docs/subsystem-integration-memo.md`
5. `docs/performance-passE-summary.md`
6. `docs/performance-passH-geometry-findings.md`
7. `docs/performance-passH-lut-findings.md`
8. `docs/performance-passH-summary.md`
