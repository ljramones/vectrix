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
  - `org.dynamisengine.vectrix.gpu.InstanceSubmissionPipeline`
- Specialized GPU write kernels:
  - `org.dynamisengine.vectrix.gpu.GpuTransformWriteKernels`
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

### Removed
- Legacy pre-JDK-25 compatibility layer. JDK 25 is now the minimum and is enforced by `maven-enforcer-plugin`:
  - `buildhelper/Java6to2` bytecode downgrader and the `maven.compiler.source/target 1.8` properties.
  - `__HAS_NIO__` and `__HAS_INTEGER_SIGNUM__` preprocessor guards (always true on JDK 25+).
  - `Runtime.HAS_floatToRawIntBits`, `Runtime.HAS_doubleToRawLongBits`, `Runtime.HAS_Long_rotateLeft`, and the
    reflection probes behind them and `Runtime.HAS_Math_fma`. `HAS_Math_fma` now reflects only `-Djoml.useMathFma`.
  - Java 1.4 `DecimalFormat` workaround and ten duplicated copies of the matrix `toString` exponent formatter
    (all now call `Runtime.formatNumbers`). Output is unchanged.

### Fixed
- Memory backend safety (review C-1, C-2):
  - The Unsafe backend crashed the JVM (SIGSEGV) when given heap NIO buffers, and wrote outside direct buffers
    for out-of-range offsets. Heap and read-only buffers now take the NIO path, and the raw-address path is
    always bounds-checked against `limit()`, throwing `IndexOutOfBoundsException`. Previously this check ran only
    under `-Djoml.debug`. Cost: about 0.06–0.28 ns per buffer transfer (`MemoryBackendBenchmark`).
  - The FFM backend wrote to the wrong native address: it added the buffer position twice, and wrote outside
    slices. It also sent heap buffers down the raw-address path. Both are fixed. Because FFM still lacks bounds
    checks, it is now opt-in via `-Djoml.forceFfm=true`; Unsafe is the default in every profile.
- `InertiaTensorf.capsule` overestimated I_xx/I_zz (by +37.6 % for r=0.5, h=2). The hemisphere parallel-axis
  term is now `h²/4` (review M-8). This affects the DynamisPhysics ODE4J backend.
- `Half.pack` now rounds ties to nearest-even, matching `Float.floatToFloat16` and GPU conversion. Exact ties
  previously rounded away from zero, for example 2049 → 2050. This affects MeshForge half-float vertex packing.
- The math mode set via `CoreConfig.setMathMode` was silently reset to FAST the first time `KernelConfig` loaded,
  and `-Dvectrix.math.mode` was ignored until then. `CoreConfig` now reads the property itself (review H-8).
- Packaging (review H-11 to H-13):
  - `module-info` no longer exports `experimental` to module names that don't exist. The package is
    `@Internal`; use `CoreConfig` for the math mode.
  - The manifest says Vectrix rather than JOML, and `Export-Package` lists the real packages.
  - The POM `url`/`scm` point to `ljramones/vectrix`; developers are inherited from `dynamis-parent`.
  - The local `sign` profile is removed. It silently disabled the default profile; the parent `release` profile
    handles signing.
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
- `FrustumCullingBenchmark` updated to import `MathMode` from `org.dynamisengine.vectrix.core`.
- Javadoc improvements on release-critical APIs (RK4 usage, quaternion interpolation contracts, SH projection and LTC contracts).

### Fixed
- Dual-quaternion blending antipodality regression coverage was added in tests.
- Various edge-case protections and STRICT-mode validation tests for quaternion and interpolation additions.

## [1.10.8] - Previous
### Note
- See git history before `1.10.9` for prior baseline work.
