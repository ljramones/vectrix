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

## API Intention
- Fast/default API usage should select matrix LBS unless caller explicitly requests a quality mode.
- Quality mode API usage should be explicit at call-site and in configuration.

## Current Evidence
From Phase 2 constrained full run (`2026-03-05`):
- `lbsLikeMatrixSkinning` outperformed quaternion blend in benchmark family.
- `skinLbs4` outperformed `skinDualQuat4` in kernel family.
- `skinLbs4SoAAuto` was close to `skinLbs4` and needs further SIMD/layout investigation before broad policy changes.

## Next Required Measurements
1. Palette access pattern variants (contiguous/clustered/random).
2. Layout variants for indices/weights.
3. Work-equivalence analysis between legacy and new kernel benchmarks.
