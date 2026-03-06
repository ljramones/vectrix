# Benchmark Dashboard Summary

Date: 2026-03-06

This page is the compact scorecard for current architecture-defining kernels.

## Runtime Defaults Scorecard

| Area | Current Default | Status |
|---|---|---|
| Bulk transforms | Packed-affine (`3x4`) | Settled |
| Upload prep | Packed-affine write path | Settled |
| CPU skinning | `skinLbs4MatrixPalette` | Settled |
| Ray/AABB queries | Miss-fast `Intersection*.testRayAab` | Settled |
| SSS LUT build | Invariant-hoisted `SssLutBuilder` | Settled |
| Transmittance LUT | Current scalar path | Good enough |
| Vector skinning | `skinLbs4Vector` | Experimental |

## Headline Deltas

| Kernel | Before | After | Delta |
|---|---:|---:|---:|
| Ray/AABB miss-heavy random (`count=16384`, float) | `7.164 ns/item` | `1.863 ns/item` | `~3.85x` faster |
| SSS LUT (`resolution=64`, medium) | `2515.40 ns/item` | `549.42 ns/item` | `~4.58x` faster |
| Transmittance LUT (`resolution=64`, medium) | `2706.63 ns/item` | `2641.26 ns/item` | `~1.02x` (flat) |

## Protection Gates
1. `scripts/bench-regression-phaseb.sh`
2. `scripts/bench-regression-skinning.sh`
3. `scripts/bench-regression-integration.sh`
4. `scripts/bench-regression-passh.sh`

## Canonical Evidence Docs
1. [performance-showcase.md](/Users/larrymitchell/Dynamis/vectrix/docs/performance-showcase.md)
2. [performance-passH-summary.md](/Users/larrymitchell/Dynamis/vectrix/docs/performance-passH-summary.md)
3. [engine-runtime-defaults.md](/Users/larrymitchell/Dynamis/vectrix/docs/engine-runtime-defaults.md)
4. [performance-state-of-the-union.md](/Users/larrymitchell/Dynamis/vectrix/docs/performance-state-of-the-union.md)
