# Integration Slice Findings

Date: 2026-03-06  
Scope: end-to-end composed engine-style path using runtime transforms, skinning, bounds update, and upload prep.

## Integration Benchmark
- Benchmark class:
  - `org.vectrix.bench.IntegrationPipelineBenchmark`
- Methods:
  - `integrationPackedPipeline`
  - `integrationMatrixPipeline`
- Pipeline stages:
  1. runtime transform input (`Transformf[]`)
  2. materialization (`packed affine` or `Matrix4f`)
  3. skinning (`SkinningKernels.skinLbs4`)
  4. bounds update (`transformAabb...`)
  5. upload prep (`packed` or `matrix`)

## Artifacts
- Raw:
  - `benchmarks/results/2026-03-06/integration-slice.json`
  - `benchmarks/results/2026-03-06/integration-slice-prof.json`
- Normalized:
  - `benchmarks/results/2026-03-06/integration-slice.normalized.csv`
  - `benchmarks/results/2026-03-06/integration-slice-prof.normalized.csv`

## Main Results (Constrained Full)
Parameters:
- `count=1024,16384`
- `vertices=4096`
- `traversalMode=SEQUENTIAL,RANDOM`

Normalized `ns/item`:
- `count=1024`
  - matrix: `53.370` (seq), `55.186` (random)
  - packed: `48.152` (seq), `48.604` (random)
- `count=16384`
  - matrix: `25.563` (seq), `33.600` (random)
  - packed: `11.337` (seq), `16.668` (random)

Interpretation:
- Packed pipeline remains ahead in composed flow.
- Advantage is modest at small count and large at high count.
- Locality still matters in both paths; random traversal regresses both, but packed remains faster.

## Profiled Allocation/Stack Check (Constrained Prof)
Parameters:
- `count=16384`, `vertices=4096`, `traversalMode=SEQUENTIAL,RANDOM`

Normalized `ns/item` + `gc.alloc.rate.norm`:
- matrix:
  - seq: `22.616 ns/item`, `196.079 B/op`
  - random: `28.414 ns/item`, `247.567 B/op`
- packed:
  - seq: `11.244 ns/item`, `99.008 B/op`
  - random: `16.465 ns/item`, `146.057 B/op`

Notes:
- `gc.count ~= 0` for all profiled cases.
- Packed path keeps lower allocation-normalized footprint and better throughput under composition.

## Keep / Change List
### Keep
1. Packed-affine materialization as default bulk composed-path format.
2. Packed-affine upload fast path as default.
3. `skinLbs4` as CPU skinning baseline in composed workloads.
4. Locality-aware traversal/scheduling as a first-order concern.

### Change
1. Do not promote matrix bulk path beyond boundary/interoperability usage.
2. Keep `skinLbs4Vector` experimental until repeated full runs beat `skinLbs4`.
3. Add an integration-slice regression gate in next pass to protect composed-path behavior.

## Verdict
The isolated winners survived integration:
- packed-affine still wins in end-to-end composed flow,
- `skinLbs4` remains the correct default baseline,
- no hidden conversion cost erased the microbenchmark gains in this slice.
