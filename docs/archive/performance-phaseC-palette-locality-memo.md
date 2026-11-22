# Phase C Palette Locality Memo

Date: 2026-03-06  
Scope: constrained quick run for skinning kernels with palette access variants.

## Run Scope
- Benchmark: `SkinningKernelBenchmark`
- Methods:
  - `skinLbs4`
  - `skinLbs4Vector`
  - `skinLbs4SoAScalarForced`
  - `skinLbs4SoASimdForced`
- Params:
  - `vertices=1024,16384`
  - `paletteAccess=contiguous,clustered,random`
  - `paletteSize=64,256`
- Artifacts:
  - `benchmarks/results/2026-03-06/phaseC-skinning-kernel.json`
  - `benchmarks/results/2026-03-06/phaseC-skinning-kernel.normalized.csv`

## Key Results (ns/item)
### `skinLbs4` (current winner)
- 1024 vertices: `~9.70` (best contiguous/64) to `~9.90`
- 16384 vertices: `~9.73` (best contiguous/64) to `~9.93`

### `skinLbs4Vector` (new vectorized packed-path kernel)
- 1024 vertices: `~10.00-10.09`
- 16384 vertices: `~9.97-10.13`

### `skinLbs4SoAScalarForced`
- 1024 vertices: `~9.85-9.96`
- 16384 vertices: `~9.86-10.07`

### `skinLbs4SoASimdForced`
- 1024 vertices: `~10.01-10.06`
- 16384 vertices: `~9.96-10.11`

## Interpretation
1. Current packed scalar LBS (`skinLbs4`) remains the fastest path in this pass.
2. `skinLbs4Vector` is functional and parity-safe, but not yet a throughput win.
3. Palette locality effects exist, but are smaller than transform/upload locality effects seen in Pass B:
   - contiguous tends to be slightly better than random,
   - delta is usually in the low single-digit percentage range for this benchmark shape.
4. Non-vectorized and current vectorized SoA variants do not beat packed scalar baseline here.

## Decisions From This Pass
1. Keep matrix LBS (`skinLbs4`) as default fast path.
2. Keep `skinLbs4Vector` as experimental and optimization target, not default.
3. Continue Pass C with deeper palette-access stress and work-equivalence audit before broad SoA/vector policy changes.
