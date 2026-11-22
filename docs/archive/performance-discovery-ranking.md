# Performance Discovery Ranking

Date: 2026-03-06

## Scope
Constrained discovery sweep executed for high-priority suites:
- `GpuTransformLayoutBenchmark`
- `SkinningEquivalenceBenchmark`
- `PhysicsMathBenchmark`
- `IntegrationPipelineBenchmark` (cache-stress variants)
- `SubsystemIntegrationBenchmark` (cache-stress variants)

Run profile used:
- `BENCH_PROFILE=full`, constrained params, `FORKS=1`, `WARMUP_ITERS=2`, `MEASURE_ITERS=4`
- normalization via `scripts/bench-normalize.py`
- short allocation pass via `BENCH_PROFILE=prof`, `FORKS=1`, `WARMUP_ITERS=1`, `MEASURE_ITERS=2`

Artifacts:
- `benchmarks/results/2026-03-06/discovery-gpu-transform-layout.{json,txt,normalized.csv}`
- `benchmarks/results/2026-03-06/discovery-skinning-equivalence.{json,txt,normalized.csv}`
- `benchmarks/results/2026-03-06/discovery-physics.{json,txt,normalized.csv}`
- `benchmarks/results/2026-03-06/discovery-integration-cache-stress.{json,txt,normalized.csv}`
- `benchmarks/results/2026-03-06/discovery-prof-*.{json,txt}`

## Key observations
1. `GpuTransformLayoutBenchmark`:
- At `count=16384`, `accessPattern=RANDOM`, `sourceRep=packedAffine`:
  - `layout=packedAffine`: `4.018 ns/item`
  - `layout=matrix4f`: `4.749 ns/item`
  - `layout=instanceCompact`: `27.495 ns/item`
- Packed-affine layout write is ~15% faster than matrix4f write and ~6.8x faster than compact path in this shape.

2. `SkinningEquivalenceBenchmark`:
- At `vertices=16384`, `paletteSize=512`, `writeMode=fullWrite`:
  - `legacyLbs`: `6.584 ns/item`
  - `kernelLbs`: `9.717 ns/item`
- Legacy loop remains ~47.6% faster in this apples-to-apples benchmark shape.

3. `PhysicsMathBenchmark` (`count=16384`):
- `springDamperBatch`: `0.726 ns/item`
- `pdControllerBatch`: `1.887 ns/item`
- `pbdDistanceConstraintBatch`: `1.811 ns/item`
- `inertiaTensorBatch`: `2.373 ns/item`
- Physics helpers are fast and scale cleanly; no immediate hotspot signal.

4. Cache-stress integrated paths (`count=16384`, `vertices=4096`, `traversalMode=RANDOM`, `workingSet=cold`):
- Integration benchmark:
  - matrix path: `33.717 ns/item`
  - packed path: `16.658 ns/item` (~2.02x faster)
- Subsystem benchmark:
  - matrix fallback: `32.985 ns/item`
  - packed default: `17.555 ns/item` (~1.88x faster)
- Packed path advantage persists under cold/random access.

## Allocation signal (profiled)
Representative `gc.alloc.rate.norm`:
- GPU layout (`count=16384`, random, packed source):
  - matrix4f: `41.549 B/op`
  - packedAffine: `34.856 B/op`
  - instanceCompact: `201.796 B/op`
- Skinning equivalence (`vertices=16384`, palette=512, fullWrite):
  - legacyLbs: `59.242 B/op`
  - kernelLbs: `84.902 B/op`
- Integration/subsystem (`count=16384`, random+cold):
  - integration matrix: `295.340 B/op`
  - integration packed: `149.569 B/op`
  - subsystem matrix fallback: `289.645 B/op`
  - subsystem packed default: `156.515 B/op`
- Physics (`count=16384`): `6.278-20.344 B/op` across tested methods.

## Ranking
Promote:
- `GpuTransformLayoutBenchmark`: clear write-layout winner (`packedAffine`) and a high-cost outlier (`instanceCompact`) worth targeted optimization.
- `SkinningEquivalenceBenchmark`: unresolved but now explicit large gap; highest leverage for near-term skinning work.
- Integration/subsystem cache-stress paths: packed default remains strongly superior in both speed and allocation.

Good enough:
- `PhysicsMathBenchmark`: throughput is strong, scaling is stable, allocation is low; keep as regression coverage.

Parked (not in this pass; run next wave before ranking):
- `HashBenchmark`
- `SdfBenchmark`
- `SamplingBenchmark`
- `ColorBenchmark`
- `EasingBenchmark`
- `ParallelTransformBenchmark`

## Recommended next two optimization battlegrounds
1. Skinning kernel follow-up:
- use `SkinningEquivalenceBenchmark` to isolate why kernel path trails legacy path.
- target both throughput and allocation reduction in kernel path.

2. GPU transform layout write path:
- harden packed-affine write as default runtime-prep path.
- treat compact layout path as explicitly non-default until write-cost/alloc profile improves.
