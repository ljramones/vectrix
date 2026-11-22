# Pass G Discovery Ranking (2026-03-06)

## Scope
Pass G added and ran:
- `RenderingMathBenchmark`
- `GeometryIntersectionBenchmark`
- `SimdSupportBenchmark`
- `FrustumRayBuilderBenchmark`
- `CoreBatchUtilityBenchmark`

Run settings used for triage:
- Constrained full: `FORKS=1`, `WARMUP_ITERS=2`, `MEASURE_ITERS=4`
- Profiled short sweep: `BENCH_PROFILE=prof`, `FORKS=1`, `WARMUP_ITERS=1`, `MEASURE_ITERS=2`, `-prof gc -prof stack`
- Artifacts: `benchmarks/results/2026-03-06/passG-*.{json,txt,normalized.csv}` and `passG-prof-*`

## Ranking

### Promote
1. `GeometryIntersectionBenchmark`
- Signal: branchy helpers are in the runtime-relevant range and sensitive to access pattern/miss mix.
- Representative full result:
  - `rayAabBatch` (`count=16384`, `random`, `mixed`, `float`) ~= `6.370 ns/item`
  - `polygonPointBatch` (`count=16384`, `random`, `mixed`, `float`) ~= `5.574 ns/item`
- Profiled allocation signal:
  - `polygonPointBatch` ~= `3.006 B/op`
  - `rayAabBatch` ~= `1.427 B/op`
- Why Promote: high engine relevance + branch-heavy code path where small improvements compound.

2. `RenderingMathBenchmark` (targeted Promote)
- Signal: interpolation is already efficient; LUT builders are clearly heavier and allocate materially more.
- Representative full result:
  - `interpolationBatch` (`count=16384`, `arrayBatch`, `float`) ~= `3.558 ns/item`
  - `buildSssLut` (`count=64`, `arrayBatch`, `float`) ~= `40257.082 ns/item`
  - `buildTransmittanceLut` (`count=64`, `arrayBatch`, `float`) ~= `42917.769 ns/item`
- Profiled allocation signal:
  - `buildTransmittanceLut` ~= `1387.409 B/op`
  - `buildSssLut` ~= `1296.565 B/op`
- Why Promote: builder paths dominate this suite’s cost and allocation footprint.

### Good Enough
1. `FrustumRayBuilderBenchmark`
- Representative full result:
  - `singleRayGenerationBatch` (`count=16384`, `randomSamples`) ~= `2.314 ns/item`
  - `gridRayGenerationBatch` (`count=16384`, `randomSamples`) ~= `0.013 ns/item`
- Profiled allocation signal:
  - `singleRayGenerationBatch` ~= `1.187 B/op`
  - `gridRayGenerationBatch` ~= `0.106 B/op`
- Verdict: strong throughput for current workload shape; keep as regression-tracked utility benchmark.

2. `CoreBatchUtilityBenchmark`
- Representative full result:
  - `quaternionNormalizeBatch` (`count=16384`, `identityHeavy`, `float`) ~= `1.815 ns/item`
  - `rk4Batch` (`count=16384`, `identityHeavy`, `float`) ~= `9.884 ns/item`
- Profiled allocation signal:
  - `rk4Batch` ~= `6.108 B/op` (highest in this suite)
- Verdict: useful baseline coverage; no immediate hotspot requiring broad optimization pass.

### Parked
1. `SimdSupportBenchmark` (as a direct optimization target)
- Representative full result:
  - `vector4faAddBatch` (`count=16384`, `random`, `offset`) ~= `5.960 ns/item`
  - `scalarAddBatch` (`count=16384`, `random`, `offset`) ~= `7.069 ns/item`
- Profiled allocation signal:
  - `vector4faAddBatch` ~= `2.050 B/op`
  - `scalarAddBatch` ~= `1.099 B/op`
- Verdict: retain for visibility and regressions, but do not prioritize standalone SIMD-wrapper tuning now.

## Immediate Follow-Up
1. Start a focused optimization pass on `GeometryIntersectionBenchmark` hot methods (`rayAab*`, polygon tests).
2. Run a targeted renderingmath pass for LUT builder write shape and allocation reduction.
3. Keep Frustum/Core/SIMD suites as tracked baselines; revisit only if integration-level evidence elevates priority.
