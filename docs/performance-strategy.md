# Vectrix Performance Strategy

## Purpose
Vectrix performance work is driven by engine workloads, not isolated micro-ops. This document defines how benchmark data is gathered, interpreted, and used for implementation decisions.

## Performance Charter
- Prioritize end-to-end kernel throughput (transforms, culling prep, skinning, staging/upload) over micro-op prestige wins.
- Treat primitive latency benchmarks as regression guards once they are in a stable "fast enough" range.
- Require benchmark-backed decisions for representation and memory-backend policies.
- Prefer zero-allocation behavior in hot paths and track allocation explicitly in benchmark runs.
- Compare throughput by normalized metrics (`ns/item`, `items/sec`), not only total `ns/op`.
- Separate strict/scalar correctness behavior from fast/SIMD behavior and verify parity in tests.

## Benchmark Tier Taxonomy
- Tier A (`primitive`): single-operation latency baselines.
- Tier B (`batch`): throughput and scaling for `N`-item kernels.
- Tier C (`layout`): memory layout, packing, copy, and interop staging costs.
- Tier D (`kernel`): engine-relevant end-to-end kernels.

Tier goals:
- Tier A: regression protection.
- Tier B: core optimization focus.
- Tier C: data-layout and backend policy focus.
- Tier D: architecture and engine integration decisions.

## Naming Convention
Use stable canonical IDs in reports and regression tooling:
- `vectrix.<category>.<kernel>.<variant>`

Examples:
- `vectrix.math.vector.dot`
- `vectrix.batch.transform.matrix`
- `vectrix.skinning.matrix.lbs`
- `vectrix.interop.upload.soa`

Implementation notes:
- JMH benchmark names remain JVM class/method identifiers.
- Reporting scripts normalize JMH names into canonical IDs.
- Canonical IDs are enforced by `scripts/bench-normalize.py`.

Method naming rules:
- Use verb + subject + scope, for example `transformVectors`, `composeAffine`, `stagePackedAffine`.
- Include parameterized scale factors via `@Param` fields, not in method names.
- Use consistent parameter names:
  - `count` for generic item count
  - `vertices` for skinning/mesh vertex counts
  - `instances` for instance-upload/update kernels
  - `bytes` for copy/transfer size

## Decision Process
1. Use Tier A to guard correctness-preserving low-level performance.
2. Use Tier B and Tier C to identify scaling and memory-layout bottlenecks.
3. Confirm architecture choices with Tier D kernel benchmarks.
4. Record selected policies and thresholds in dedicated docs:
   - `docs/transform-representation-policy.md`
   - `docs/transform-materialization-audit.md`
   - `docs/memory-backend-policy.md`
   - `docs/skinning-runtime-policy.md`
   - `docs/hot-path-policy.md`

## Golden Kernels
These kernels are currently treated as architecture-defining hot paths and should remain explicitly protected by regression gates:
- packed-affine AABB transform (`TransformAabbBenchmark` packed-affine slices),
- packed-affine instance upload staging (`InstanceUploadBenchmark.instanceUploadPackedAffine`),
- SoA/vector transform batch kernels (`BatchMatrixBenchmark` SoA slices),
- skinning default baseline (`SkinningKernelBenchmark.skinLbs4`) and experimental vector tracking (`skinLbs4Vector`).

## Phase Commit Rule
Each performance phase ends with a local commit on `main` and no push until explicitly requested.
