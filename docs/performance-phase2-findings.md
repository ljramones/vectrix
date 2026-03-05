# Phase 2 Findings (Initial)

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
- Full benchmark execution remains blocked by the tracked named-module JMH packaging issue (see `benchmarks/PERF_PLAN.md`).

## Next Measurement Pass (after blocker fix)
- Run `quick`, `full`, and `prof` profiles for all Phase 2 kernels.
- Normalize outputs with `scripts/bench-normalize.py`.
- Produce comparative per-item scaling tables for:
  - matrix vs affine AABB transforms
  - matrix vs affine vs TRS composition paths
  - full-matrix upload vs packed-affine upload
