# Skinning Runtime Policy

Date: 2026-03-05

## Purpose
Define default and optional CPU skinning paths so performance and quality tradeoffs are explicit.

## Default Path
- Default runtime CPU skinning: matrix LBS (`SkinningKernels.skinLbs4` family).
- Default data layout target: contiguous SoA-compatible arrays, with 4-influence kernels as primary optimized form.

## Optional Quality Path
- Dual quaternion skinning (`skinDualQuat4*`) is opt-in for quality-sensitive cases.
- Dual quaternion path is not the default performance baseline.

## SoA and SIMD Status
- SoA skinning path is supported.
- SIMD-enabled SoA path remains experimental and must justify retention with benchmark wins over scalar baseline at production sizes.
- `skinLbs4Vector` (packed-input vectorized kernel) is now available as an experimental optimization target, but it is not default until it beats `skinLbs4` in constrained full runs.

## API Intention
- Fast/default API usage should select matrix LBS unless caller explicitly requests a quality mode.
- Quality mode API usage should be explicit at call-site and in configuration.

## Current Evidence
From Phase 2 constrained full run (`2026-03-05`):
- `lbsLikeMatrixSkinning` outperformed quaternion blend in benchmark family.
- `skinLbs4` outperformed `skinDualQuat4` in kernel family.
- `skinLbs4SoAAuto` was close to `skinLbs4` and needs further SIMD/layout investigation before broad policy changes.

From Phase C constrained quick locality pass (`2026-03-06`):
- `skinLbs4` remained the fastest measured LBS variant (`~9.7-9.9 ns/item` across tested palette modes/sizes).
- `skinLbs4Vector` and forced SIMD SoA variants were generally slower (`~10.0 ns/item` range).
- Palette locality effects were present but modest in this benchmark shape.

From integration-slice pass (`2026-03-06`):
- `skinLbs4` remained stable as the composed-path baseline while packed-affine transform/upload stages preserved their wins.
- No evidence from this slice to replace `skinLbs4` as default CPU path.

## Next Required Measurements
1. Constrained/full rerun of `skinLbs4Vector` after loop-body/vector strategy optimization.
2. Expanded palette locality stress (small/medium/large palette with cache-cold variants).
3. Layout variants for indices/weights.
4. Work-equivalence analysis between legacy and new kernel benchmarks (tracked in `docs/skinning-work-equivalence-audit.md`).
