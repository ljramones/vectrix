# Packed-Affine Win Postmortem (Pass B)

Date: 2026-03-06  
Scope: explain why packed-affine AABB and upload kernels outperformed matrix baselines.

## Root Cause Summary
The packed-affine wins are primarily explained by:
1. less memory traffic per transform payload (`12` floats vs `16` for matrix upload paths),
2. simpler contiguous read/write shape in kernel bodies,
3. reduced hot-loop work (no full-matrix reconstruction/materialization in packed upload path),
4. locality benefits that compound at large sizes, especially when traversal is not random.

## Evidence From Profiled Pass
Profile command family: constrained `BENCH_PROFILE=prof` (`gc` + `stack`), `FORKS=1`, `wi=3`, `i=5`.

### AABB Transform (`count=16384`)
- `matrix4f`:
  - sequential: `~7.567 ns/item`, `~65.91 B/op`
  - random: `~12.125 ns/item`, `~104.48 B/op`
- `packedAffine`:
  - sequential: `~3.879 ns/item`, `~34.34 B/op`
  - random: `~8.648 ns/item`, `~76.22 B/op`

Interpretation:
- Packed affine reduces per-item memory footprint and keeps transform data in a tight contiguous 3x4 layout.
- Random traversal remains expensive for both paths, confirming locality is first-order.

### Instance Upload (`instances=16384`)
- `instanceUploadMatrix4f`:
  - sequential: `~4.956 ns/item`, `~43.43 B/op`
  - random: `~8.860 ns/item`, `~76.79 B/op`
- `instanceUploadPackedAffine`:
  - sequential: `~1.995 ns/item`, `~17.69 B/op`
  - random: `~2.911 ns/item`, `~25.55 B/op`

Interpretation:
- Upload is a direct copy-like kernel where payload width and contiguous write shape dominate.
- Packed-affine path avoids full-matrix expansion work and moves fewer bytes, which aligns with the observed 2x-3x win.

## Stack Profile Notes
- Matrix upload path hotspots were centered in `InstanceUploadBenchmark.instanceUploadMatrix4f`.
- Packed upload path hotspots were centered in `PackedAffineKernels.uploadPackedAffineRange`.
- Both paths showed `gc.count ~= 0`; differences are throughput and per-op allocation-normalization metrics, not frequent GC events.

## Practical Guardrail
Do not treat this as a universal claim that chunking is always faster.
- Generic wrapper chunking was neutral/slightly negative in multiple runs.
- The durable gain came from representation (`packedAffine`) and locality-aware kernel shape.
