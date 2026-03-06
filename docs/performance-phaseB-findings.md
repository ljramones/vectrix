# Phase B Findings

Date: 2026-03-06  
Scope: Pass B locality + packed-affine implementation and constrained `bench-full` validation.

## Data Artifacts
- Raw JSON:
  - `benchmarks/results/2026-03-05/phaseB-batch.json`
  - `benchmarks/results/2026-03-05/phaseB-packed-conversion.json`
  - `benchmarks/results/2026-03-05/phaseB-aabb.json`
  - `benchmarks/results/2026-03-06/phaseB-instance-upload.json`
- Normalized CSV:
  - `benchmarks/results/2026-03-05/phaseB-batch.normalized.csv`
  - `benchmarks/results/2026-03-05/phaseB-packed-conversion.normalized.csv`
  - `benchmarks/results/2026-03-05/phaseB-aabb.normalized.csv`
  - `benchmarks/results/2026-03-06/phaseB-instance-upload.normalized.csv`
- Consolidated summary:
  - `benchmarks/results/2026-03-06/phaseB-summary.csv`

## Constrained Full Run Parameters
- `TransformAabbBenchmark`
  - `count=1024,16384`
  - `representation=matrix4f,affine4x3,packedAffine`
  - `dataDistribution=clustered`
  - `accessPattern=hot`
  - `traversalMode=SEQUENTIAL,RANDOM,CHUNKED`
  - `chunkSize=128`
- `InstanceUploadBenchmark`
  - `instances=1024,16384`
  - `dataDistribution=clustered`
  - `accessPattern=hot`
  - `traversalMode=SEQUENTIAL,RANDOM,CHUNKED`
  - `chunkSize=128`
- `BatchMatrixBenchmark`
  - `size=64,1024,16384`
  - `chunkSize=128`
- `PackedAffineConversionBenchmark`
  - `size=64,1024,16384`

## Key Observations
1. Packed affine is now the clear AABB transform winner.
- `transformAabbBatch` at `16384`:
  - `matrix4f`: `~8.50-13.53 ns/item` (sequential/chunked vs random)
  - `affine4x3`: `~8.39-13.42 ns/item`
  - `packedAffine`: `~3.90-8.45 ns/item`
- `transformAabbBatchChunked` shows the same pattern:
  - `packedAffine`: `~3.97 ns/item` (sequential/chunked), `~8.37 ns/item` (random)
  - matrix/affine remain `~8.4+` (sequential/chunked) and `~13.2+` (random).

2. Locality is strongly visible at large sizes.
- AABB (`16384`, matrix4f):
  - `SEQUENTIAL`: `~8.50 ns/item`
  - `RANDOM`: `~13.53 ns/item`
- Upload (`16384`, matrix4f):
  - `SEQUENTIAL`: `~4.95 ns/item`
  - `RANDOM`: `~9.05 ns/item`
- Packed affine upload also degrades on random, but less in absolute terms (`~2.03 -> ~2.94 ns/item`).

3. Packed affine is the fastest upload-prep format.
- `instanceUploadPackedAffine` vs `instanceUploadMatrix4f`:
  - `1024`: `~1.72-1.74` vs `~5.38-5.86 ns/item`
  - `16384`: `~2.00-2.94` vs `~4.95-9.05 ns/item`
- In this pass, packed affine upload is about `2x-3x` better depending on size/access pattern.

4. Generic chunked method variants are not automatically faster.
- `instanceUploadPackedAffineChunked` is slower than non-chunked packed upload in this configuration.
- `transformBatchChunked` and `transformBatchSoAScalarChunked` are near parity with non-chunked baselines, sometimes slightly slower.
- The practical win comes from traversal/order locality and packed layout, not from wrapper chunk loops alone.

5. Packed-affine conversion cost is acceptable for late materialization.
- `matrix4f -> packedAffine`: `~1.71 -> 2.66 -> 2.75 ns/item` (`64 -> 1024 -> 16384`)
- `quat+translation -> packedAffine`: `~1.86 -> 2.51 -> 2.74 ns/item`
- `TRS -> packedAffine`: `~2.49 -> 3.02 -> 2.91 ns/item`
- This keeps conversion overhead in a range that is substantially lower than matrix-path AABB/upload kernels.

## Pass B Recommendations
1. Keep packed affine as the default bulk representation for AABB and instance-upload kernels.
2. Keep non-chunked packed-affine upload as the current fast path; treat chunked upload as optional/situational.
3. Keep traversal-aware scheduling work active for large random workloads (AABB + upload), since locality deltas remain large.
4. Keep SoA scalar transform path as the stable high-throughput baseline (`~1.73-1.75 ns/item`).
5. Move Pass C focus to skinning kernels and palette locality, with packed affine retained as the transform/materialization boundary.

## Follow-Up Questions
1. Do we want adaptive runtime policy to switch scheduling for random traversal workloads at large counts?
2. Should upload chunking stay exposed publicly, or remain internal until it shows clear wins for specific hardware patterns?
3. Is a dedicated packed-affine + vector AABB kernel the next highest-value transform optimization after Pass C skinning work?
