# Phase 2 Findings (Updated)

Date: 2026-03-05

## Scope Added
- Batch sweep expansion:
  - `BatchMatrixBenchmark`
  - `SkinningBenchmark`
  - `SkinningKernelBenchmark`
  - `TransformComposeBenchmark`
- New kernels:
  - `TransformAabbBenchmark`
  - `QuatMatrixConversionBenchmark`
  - `InstanceUploadBenchmark`

## Initial Representation Signals
- Affine (`3x4`) should be favored for AABB transform and instance-upload staging where full homogeneous row data is unnecessary.
- TRS (`quat + translation + scale`) remains a strong internal compose representation and conversion source format.
- Full `Matrix4f` remains useful as a compatibility baseline, especially for broad API interop and correctness parity checks.

## Status
- Harness and benchmark classes are in place for Phase 2 measurements.
- Named-module JMH packaging blocker is resolved (see `docs/benchmark-modulepath-resolution.md`).
- Constrained `bench-full` run completed for:
  - `BatchMatrixBenchmark`
  - `TransformComposeBenchmark`
  - `QuatMatrixConversionBenchmark`
  - `TransformAabbBenchmark`
  - `InstanceUploadBenchmark`
  - `SkinningBenchmark`
  - `SkinningKernelBenchmark`
- Decision-quality memo and normalized summary produced:
  - `docs/performance-phase2-decision-memo.md`
  - `benchmarks/results/2026-03-05/phase2-summary.csv`

## Next Measurement Pass
- Expand focused profiling (`bench-prof`) for:
  - large-size SoA vector transform behavior
  - cache-cold AABB and upload kernels
  - skinning palette-access variants
