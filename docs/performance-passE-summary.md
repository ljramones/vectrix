# Performance Pass E Summary

Date: 2026-03-06

## Scope
1. Resolve GPU transform layout write-path defaults.
2. Resolve legacy vs kernel skinning equivalence gap.
3. Promote winners into runtime doctrine and regression protection.

## Key Results
1. GPU layout (`count=16384`, random, packed source):
   - `packedAffine`: `3.192 ns/item`
   - `matrix4f`: `3.461 ns/item`
   - `instanceCompact`: `17.568 ns/item`
2. Skinning equivalence (`vertices=16384`, `palette=512`, `fullWrite`):
   - `legacyLbs`: `6.652 ns/item`
   - `kernelLbs`: `9.817 ns/item`
   - `kernelMatrixTight`: `6.082 ns/item`

## Decisions
1. Packed-affine remains the default GPU write-path layout.
2. Matrix remains valid fallback/interoperability layout.
3. Compact layout remains niche and not default for hot-path writes.
4. Matrix-palette tight LBS (`skinLbs4MatrixPalette`) is the default CPU skinning baseline.
5. `skinLbs4Vector` remains experimental.

## Regression Protection
1. Keep `scripts/bench-regression-skinning.sh` equivalence gate on:
   - `path=legacyLbs,kernelMatrixTight,kernelLbs`
2. Keep GPU layout suite in regression checks with packed-affine and matrix fallback baselines.

## References
1. `docs/performance-passE-gpu-layout-decision.md`
2. `docs/performance-passE-skinning-resolution.md`
3. `docs/engine-runtime-defaults.md`
4. `docs/performance-state-of-the-union.md`
