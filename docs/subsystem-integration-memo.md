# Subsystem Integration Memo

Date: 2026-03-06  
Scope: transform update + instance submission subsystem path with packed-affine default and matrix fallback.

## Implemented Subsystem Path
- Runtime class:
  - `org.vectrix.gpu.InstanceSubmissionPipeline`
- Default path:
  - packed-affine materialization
  - skinning via matrix-palette tight LBS (`skinLbs4MatrixPalette`)
  - bounds update + packed upload prep
- Fallback path:
  - matrix-centric branch (`Path.MATRIX_FALLBACK`)
- Fallback controls:
  - explicit `Path` argument
  - runtime property `vectrix.runtime.instanceSubmission.forceMatrix`

## Subsystem Benchmark
- Benchmark class:
  - `org.vectrix.bench.SubsystemIntegrationBenchmark`
- Methods:
  - `subsystemPackedDefaultPath`
  - `subsystemMatrixFallbackPath`
- Params:
  - `count=1024,16384`
  - `vertices=4096`
  - `traversalMode=SEQUENTIAL,RANDOM`

Artifacts:
- `benchmarks/results/2026-03-06/subsystem-integration.json`
- `benchmarks/results/2026-03-06/subsystem-integration.normalized.csv`
- `benchmarks/results/2026-03-06/subsystem-integration-prof.json`
- `benchmarks/results/2026-03-06/subsystem-integration-prof.normalized.csv`

## Before/After (Normalized)
`ns/item` from constrained full run:
- `count=1024`
  - matrix fallback: `53.065` (seq), `54.974` (random)
  - packed default: `48.079` (seq), `48.357` (random)
- `count=16384`
  - matrix fallback: `25.171` (seq), `32.415` (random)
  - packed default: `11.175` (seq), `16.925` (random)

## Profiled Allocation Signal
Constrained prof run (`count=16384`):
- matrix fallback:
  - seq: `24.911 ns/item`, `220.893 B/op`
  - random: `28.165 ns/item`, `248.223 B/op`
- packed default:
  - seq: `11.022 ns/item`, `98.367 B/op`
  - random: `16.483 ns/item`, `147.073 B/op`

`gc.count ~= 0` in all profiled slices.

## Decision
The subsystem integration confirms doctrine:
1. Keep packed-affine as default composed runtime branch.
2. Keep matrix branch as explicit fallback/interoperability path only.
3. Keep matrix-palette tight LBS (`skinLbs4MatrixPalette`) as subsystem baseline.
4. Keep locality-sensitive scheduling as first-order concern.
