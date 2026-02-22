# V1 Readiness Checklist (Final)

Status: Complete (2026-02-22)

This checklist is retained as a release record for V1 readiness.

## API and Semantics
- [x] Stable domain packages documented
- [x] Explicit conventions doc (`docs/conventions.md`)
- [x] Internal marker guidance and public compatibility policy documented

## Numeric and Determinism
- [x] Global epsilon policy (`Epsilonf`, `Epsilond`)
- [x] FAST/STRICT runtime mode (`MathMode` + shared config surfaces)
- [x] Deterministic-aware reduction utility (`Reduction`)

## Geometry, Curves, and Rotation
- [x] Rotation toolkit (SLERP/NLERP, log/exp, SQUAD, swing-twist, angular velocity)
- [x] Curve framework (Bezier/Hermite/Catmull-Rom/B-spline, scalar+vec2/3/4, derivatives, batch)
- [x] Arc-length reparameterization utilities
- [x] Geometry helpers and spatial query coverage

## Rendering and Optics Math
- [x] SH basis/projection/convolution (L2 and L3)
- [x] FFT and convolution support
- [x] Optics package (IOR/Fresnel/thin-film/spectral helpers)
- [x] SSS and atmospheric LUT builders
- [x] LTC evaluation helpers
- [x] Bent-normal cone utilities

## GPU and Sampling
- [x] Packed GPU format utilities and layout helpers
- [x] SoA skinning kernels (including dual-quaternion paths)
- [x] Low-discrepancy sequences (Halton/Sobol/scrambled Sobol)

## Testing and Benchmarking
- [x] Unit tests for all added math domains
- [x] JMH benchmarks for curves/SH/FFT/optics and added hot paths
- [x] Benchmark publication doc with normalized per-op results (`BENCHMARKS.md`)

## Operational
- [x] `LICENSE` and `NOTICE` present with attribution
- [x] Consumer-facing `README.md` and capability documentation
- [x] Release process documented (`docs/release-cut-checklist.md`)
