# Phase 2 Decision Memo

Date: 2026-03-05  
Scope: constrained `bench-full` run for Phase 2 kernels with normalized outputs

## Data Artifacts
- Raw JSON: `benchmarks/results/2026-03-05/phase2-*.json`
- Normalized CSV: `benchmarks/results/2026-03-05/phase2-*.normalized.csv`
- Cross-kernel summary: `benchmarks/results/2026-03-05/phase2-summary.csv`

## Key Observations
1. Batch transform scaling diverges by layout path.
- `transformBatch`: `1.711 -> 2.862 -> 3.060 ns/item` (`64 -> 1024 -> 16384`)
- `transformBatchSoAScalar`: flat near `1.72 ns/item`
- `transformBatchSoAVector`: fastest at smaller sizes (`~0.26-0.29 ns/item`), with large-size degradation (`0.449 ns/item`).

2. Matrix multiply batch scaling remains stable.
- `mulBatch`: `5.895 -> 6.933 -> 6.710 ns/item`, good near-linear throughput behavior.

3. Compose kernels clearly favor SoA/affine over matrix.
- `composeSoABatch`: `4.192 -> 4.193 -> 5.009 ns/item`
- `composeAffineBatch`: `4.642 -> 6.702 -> 7.118 ns/item`
- `composeMatrixBatch`: `5.881 -> 9.228 -> 9.501 ns/item`

4. Quat conversion favors affine output format.
- `quatToAffineMatrixBatch`: `2.969 -> 3.261 -> 3.713 ns/item`
- `quatToMatrixBatch`: `3.608 -> 4.231 -> 4.558 ns/item`

5. Upload preparation favors packed affine, especially in hot paths.
- `instanceUploadPackedAffine` (hot): `4.529 -> 4.691 -> 3.939 ns/item`
- `instanceUploadMatrix4f` (hot): `6.025 -> 5.599 -> 5.112 ns/item`
- Packed affine remains better in cold access too (`~5.812 vs 6.948 ns/item` at 1024; `~9.159 vs 10.054 ns/item` at 16384).

6. AABB transform is largely representation-neutral; access locality dominates.
- Hot access at 16384: `~8.51-8.53 ns/item`
- Cold access at 16384: `~13.49-13.61 ns/item`
- Matrix4f vs Affine4x3 differences are small compared to hot/cold separation.

7. Skinning path: matrix LBS remains baseline winner in this pass.
- `lbsLikeMatrixSkinning`: `2.663 -> 2.605 -> 3.218 ns/item`
- `quaternionTransformBlend`: `3.576 -> 3.472 -> 4.013 ns/item`
- Kernel variants: `skinLbs4 ~9.82-9.91 ns/item` vs `skinDualQuat4 ~11.01-11.07 ns/item`.

## Recommendations
1. Runtime compose representation should default to SoA/TRS-oriented paths, with affine favored over full matrix when materialization is required.
2. GPU instance upload should default to packed affine staging; keep full matrix as compatibility fallback.
3. AABB transform optimization should prioritize locality/cache strategy before representation specialization.
4. Keep matrix LBS as default skinning baseline; treat quaternion/dual-quat paths as secondary until additional palette-access experiments are run.
5. For large transform batches, prioritize SoA scalar/vector kernels over AoS transform paths.

## Follow-Up Questions
1. Why does `transformBatchSoAVector` degrade at 16384 while still outperforming scalar paths overall?
2. Should we split upload policy by access pattern (`hot` vs `cold`) in addition to representation?
3. Do the cold-path deltas suggest a required cache-aware scheduling pass for AABB and upload staging kernels?

## Next Work Trigger
- Move to Phase 3A (representation/layout policy) with a smaller targeted pass on:
  - SoA vector large-size stability
  - cache-cold scheduling for AABB/upload
  - skinning palette-access pattern variants
