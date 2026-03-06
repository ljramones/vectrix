# Phase B Decision Memo

Date: 2026-03-06  
Scope: Pass B locality + packed-affine implementation decisions.

## Decision Summary
1. Packed affine is now the default bulk transform kernel format.
2. Packed affine is now the default instance upload-prep format.
3. `Matrix4f` remains boundary/interoperability format, not hot-path bulk format.
4. Locality (traversal order and cache behavior) is first-order for large batches.
5. Generic chunk wrappers are orchestration helpers, not automatic optimizations.

## Decision Table

| Area | Decision |
| --- | --- |
| Packed-affine bulk transforms | Keep / default |
| Packed-affine upload staging | Keep / default |
| Generic chunk wrappers | Keep for structure/scheduling, not as assumed perf win |
| Matrix4f hot-path bulk use | Discourage |
| SoA transform kernels | Keep |
| SoA skinning | Experimental |
| Dual quaternion skinning | Quality mode only |

## Measured Basis (Normalized)
- AABB transform (`count=16384`, constrained full pass):
  - matrix4f: `~8.50 ns/item` (sequential), `~13.53 ns/item` (random)
  - packed affine: `~3.90 ns/item` (sequential), `~8.45 ns/item` (random)
- Instance upload (`instances=16384`, constrained full pass):
  - matrix4f: `~4.95 ns/item` (sequential), `~9.05 ns/item` (random)
  - packed affine: `~2.03 ns/item` (sequential), `~2.94 ns/item` (random)

These results were reproduced in constrained profiled runs (`gc` + `stack`) with the same size/locality slices.

## Pass C Targeting
1. `skinLbs4Vector` as the first Pass C kernel target.
2. Palette locality matrix: contiguous/clustered/random + small/medium/large palette.
3. Work-equivalence audit between `lbsLikeMatrixSkinning` and kernelized `skinLbs4`.
4. SoA skinning investment only when vectorized variants show clear wins.
