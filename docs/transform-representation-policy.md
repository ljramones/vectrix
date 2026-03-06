# Transform Representation Policy

Date: 2026-03-05

## Purpose
Define the default transform representations for runtime kernels, staging paths, and API boundaries so performance work stays consistent across features.

## Default
- Runtime local/world transform state: `Transformf`/`TransformSoA` (TRS-oriented representation).
- Bulk transform materialization target: packed affine (canonical 3x4/12-float form).
- GPU instance staging default: packed affine payloads (12-float equivalent) unless a boundary contract explicitly requires full matrices.

## Allowed
- `Matrix4x3f` for interoperability with existing affine-capable APIs and JOML-style operations.
- `Matrix4f` at external boundaries (interop, third-party API expectations, explicit homogeneous-matrix contracts).
- Dual quaternion representation for quality-oriented skinning paths.

## Discouraged (Hot Path)
- Eager full `Matrix4f` materialization in high-frequency loops.
- Object-by-object conversion patterns when equivalent batch/SoA kernels exist.
- Repeated conversion chains in one frame phase (`TRS -> Matrix4f -> Affine -> upload`).

## Selection Rules
1. Keep authoring/runtime composition in TRS/SoA for cache-friendly compose/update kernels.
2. Convert late to packed affine for bounds/update/upload stages.
3. Materialize `Matrix4f` only when required by API boundary or semantic need.
4. Prefer contiguous-array kernels over per-object calls.
5. Treat generic chunk wrappers as orchestration tools; only keep chunking on the hot path when kernel-native chunked loops show measured wins.

## Evidence Basis
- Phase 2 constrained full run (`2026-03-05`) showed:
  - Compose kernels: `composeSoABatch` and `composeAffineBatch` outperform `composeMatrixBatch`.
  - Quat conversion: `quatToAffineMatrixBatch` outperforms `quatToMatrixBatch`.
  - Instance upload: packed affine staging outperforms full-matrix staging.
- Pass B constrained full + profiled runs (`2026-03-05` to `2026-03-06`) strengthened this:
  - AABB transform (`count=16384`): packed affine is significantly faster than matrix/affine baselines.
  - Instance upload (`instances=16384`): packed affine staging is roughly 2x-3x faster than matrix staging.
  - Locality remains first-order (random traversal penalties are large across representations).

See:
- `docs/performance-phase2-decision-memo.md`
- `docs/performance-phaseB-decision-memo.md`
- `docs/performance-phaseB-packed-affine-postmortem.md`
- `benchmarks/results/2026-03-05/phase2-summary.csv`
- `benchmarks/results/2026-03-06/phaseB-summary.csv`
