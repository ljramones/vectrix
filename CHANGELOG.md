# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

## [Unreleased]
### Added
- Engine-style composed performance coverage:
  - `IntegrationPipelineBenchmark`
  - `SubsystemIntegrationBenchmark`
- Expanded benchmark suites for runtime and domain coverage:
  - `GpuTransformLayoutBenchmark`, `SkinningEquivalenceBenchmark`
  - `PhysicsMathBenchmark`, `HashBenchmark`, `SdfBenchmark`
  - `SamplingBenchmark`, `ColorBenchmark`, `EasingBenchmark`, `ParallelTransformBenchmark`
- Runtime subsystem path implementation with packed-affine default and matrix fallback:
  - `org.vectrix.gpu.InstanceSubmissionPipeline`
- Specialized GPU write kernels:
  - `org.vectrix.gpu.GpuTransformWriteKernels`
- Tight matrix-palette skinning support:
  - `SkinningKernels.skinLbs4MatrixPalette(...)`
  - `SkinningKernels.buildRigidMatrixPalette12(...)`
- Additional regression gating scripts and benchmark guard coverage:
  - `scripts/bench-regression-phaseb.sh`
  - `scripts/bench-regression-skinning.sh`
  - `scripts/bench-regression-integration.sh`
- New performance documentation artifacts:
  - `docs/performance-showcase.md`
  - `docs/performance-state-of-the-union.md`
  - `docs/engine-runtime-defaults.md`
  - Pass B/C/E decision/findings memos and subsystem integration memo

### Changed
- Runtime/default doctrine is now explicitly benchmark-driven:
  - packed-affine default for bulk transform/update/upload
  - matrix paths retained for fallback/interoperability
  - matrix-palette tight LBS promoted as default CPU skinning baseline
- `BENCHMARKS.md` now includes a prominent current benchmark snapshot and acquisition path.
- `README.md` and docs index were updated to surface benchmark methodology and the performance showcase.

### Fixed
- Resolved benchmark execution reliability under named-module `-Pbench` workflow and documented the approach.
- Closed skinning equivalence ambiguity by introducing and validating the `kernelMatrixTight` path as the fastest measured baseline shape.

## [1.10.13] - 2026-02-25
### Fixed
- Fixes invalid consumer POM metadata (`systemPath` profile removed).

## [1.10.9] - 2026-02-22
### Added
- Quaternion rotation toolkit additions:
  - logarithm/exponential maps (`log`, `exp`)
  - SQUAD support (`squadControlPoint`, `squad`)
  - swing/twist decomposition
  - angular-velocity derive/integrate utilities
  - batch hemisphere normalization helpers
- Core math mode layering cleanup with `CoreConfig` and core-owned `MathMode` usage.
- Curve framework:
  - cubic Bezier/Hermite/Catmull-Rom/Uniform B-spline
  - scalar, vec2, vec3, vec4; float/double parity
  - first/second derivatives and SoA batch evaluation
  - arc-length table builders and evaluation mapping utilities
- Rendering/math subsystems:
  - color math and color-science utilities
  - easing functions
  - SDF primitive utilities
  - SH basis/projection/convolution (L2 + L3) with zero-allocation hot-path overloads
  - low-discrepancy sampling (Halton, Sobol, scrambled Sobol)
  - FFT + convolution support with complex types
  - optics package (`Ior*`, `Fresnel*`, thin-film, spectral helpers)
  - LUT builders for preintegrated SSS and atmospheric transmittance
  - interpolation utilities (bilinear + bicubic variants)
  - LTC helpers and bent-normal cone math
  - RK4 integration and range/remap helpers
- Package-level documentation (`package-info.java`) for new capability packages.
- New benchmark coverage for curves, SH, FFT, and optics.
- `BENCHMARKS.md` with reproducible methodology and baseline metrics.
- `NOTICE` file and license attribution updates for JOML-derived lineage.
- Capability reference document: `docs/vectrix-capabilities.md`.

### Changed
- `README.md` rewritten as a consumer-oriented guide with quick-start usage.
- `FrustumCullingBenchmark` updated to import `MathMode` from `org.vectrix.core`.
- Javadoc improvements on release-critical APIs (RK4 usage, quaternion interpolation contracts, SH projection and LTC contracts).

### Fixed
- Dual-quaternion blending antipodality regression coverage was added in tests.
- Various edge-case protections and STRICT-mode validation tests for quaternion and interpolation additions.

## [1.10.8] - Previous
### Note
- See git history before `1.10.9` for prior baseline work.
