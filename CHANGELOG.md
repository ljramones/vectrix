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
- `Vector3L` constructors take `long` (`(long)`, `(long, long, long)`, new `(long[])`). `setComponent` takes a
  `long`, and the `(Vector2dc, z, mode)` constructor takes a `double` z. `Vector4L` gains a `(long[])`
  constructor. `Vector2L`/`Vector2Lc.minComponent`/`maxComponent` return `int` (a component index), like every
  other vector type. All of these are source compatible.
- **Packed affine layout (review M-1).** `PackedAffineArray` now stores rows (`[r00 r01 r02 tx, r10 r11 r12 ty,
  r20 r21 r22 tz]`), the layout already used by the skinning palette and Vulkan's `VkTransformMatrixKHR`. It
  previously stored basis columns under the same documentation, so a packed array fed to `skinLbs4MatrixPalette`
  or uploaded to a row-major consumer applied the inverse rotation. Code that reads `raw()` by index must swap
  elements 1/4, 2/8 and 6/9. The JOML mapping (`rRC = m.mCR()`) is in `docs/conventions.md`.
- **One STRICT contract (review M-11).** `MathMode.STRICT` now means: validate inputs and throw, never repair,
  results bitwise equal to the FAST scalar path for valid inputs, fixed scalar dispatch, and accurate reductions
  (documented on `MathMode` and in `docs/conventions.md`). As a result:
  - STRICT `TransformKernels.composeBatch` throws for non-unit, zero or NaN rotations, instead of renormalising
    the output and replacing a zero product with identity.
  - The float unit tolerance is now `1e-5` on `|q|^2 - 1` (`CoreConfig.STRICT_UNIT_TOLERANCE_F`). The previous
    `1e-6` rejected ordinary output of about eight chained float products. Double stays at `1e-6`.
  - NaN now fails every STRICT unit check; previously `|NaN - 1| > tol` was false, so NaN passed.
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
- Physics PBD helpers (review C-3, C-4, H-3):
  - `PbdConstraintsf.bendConstraint` pushed away from the rest angle, with an unnormalised step of up to
    ~57 units for centimetre edges. It is now the standard PBD projection
    `d_i = -k C w_i grad_i / sum w_j |grad_j|^2`, with `atan2` for the angle.
  - `PbdConstraintsf.volumeConstraint` had sign-flipped gradients for p0 and p2, so it drove the volume the
    wrong way and did not conserve momentum.
  - Both constraints gain inverse-mass overloads; the existing signatures are the equal-mass case.
  - `PbdConstraintsf.shapeMatchingGoal` applied the inverse rotation. Its Gram-Schmidt step was also wrong
    for any rest shape with a non-diagonal covariance, even under purely rigid motion. It now uses the
    polar rotation (Mueller et al. 2016 iteration, warm-started by Gram-Schmidt) and no longer allocates.
- `StdLayout`: a `vec3` has size 12 (alignment 16), so a following scalar packs into its fourth component.
  `structSizeStd430` rounds up to the largest member alignment (review H-1). `VertexLayoutTest` had pinned
  the wrong values.
- `GpuTransformWriteKernels.writeMatrix4fFromPackedAffine` produced an invalid mat4: the translation sat in
  the bottom row. It now writes a column-major matrix identical to JOML's (review H-2).
- `ColorSciencef/d.cie1931XyzBar`: added the missing lobe weights of the Wyman–Sloan–Shirley fit. yBar(555 nm)
  was 1.70 instead of 1.00, and xBar(500 nm) was 1.13 instead of 0.005. The fit is now within 0.015 of the
  CIE 1931 tables (review H-4).
- LTC (review H-5, M-5):
  - `formFactorDisc` was half the correct value. It now uses Snyder's closed form for a spherical cap, which
    is exact including caps that cross the horizon.
  - `formFactorTube` was also half the correct value (`Ω/π`, not `Ω/2π`).
  - `formFactorPolygonClipped`/`formFactorRectClipped` projected vertices instead of clipping edges. Errors
    were −26 % to +41 %, and a polygon entirely below the horizon gave 0.5. They now use exact streaming
    Sutherland–Hodgman clipping.
  - Lights are documented as one-sided: front faces wind counter-clockwise.
- `SssLutBuilder` now normalises by the profile integral (Penner 2011). Previously the LUT darkened as the
  profile widened (0.39 and 0.19 instead of 0.97 on a flat surface); a flat surface now reproduces N·L
  (review M-4).
- Long vectors `Vector2L`/`Vector3L`/`Vector4L` (review H-6) now keep 64-bit semantics:
  - `Math.roundLongUsing` HALF_* modes clamped to int, so every rounding constructor and setter did.
  - `Vector3L/4L.set(Vector*dc)` truncated through `(int)`, and `div(float)` used a float quotient with an int cast.
  - `length()`/`distance()` squared in `long`, overflowing to NaN above about 3.04e9. They also resolved to
    the float `sqrt` overload, giving ~7 significant digits. They now compute in double from exact `long`
    differences.
  - `Vector3L` min/maxComponent compared through float, and `toString` printed 4-digit scientific notation;
    it now prints exact integers.
- `Math.roundHalfUp/HalfDown` (int and long) decide ties on the exact fractional part. Before,
  `roundHalfUp(0.49999999999999994)` was 1 and `roundLongHalfUp(2^52+1)` returned 2^52+2.
- Quaternions (review H-7, M-3):
  - Float `log` and `angularVelocity` used `acos(w)`. That collapsed for rotations below ~5e-4 rad: `log`
    returned 0, and angular velocity was 0 for 0.02 rad/s at 60 Hz. Both now use `atan2` (f and d).
  - `exp` keeps the vector part for tiny arguments, so `exp(log(q))` round-trips.
  - `swingTwist` is safe when `swing` or `twist` is `this`, and rejects `swing == twist`.
- `Transformf.compose`/`RigidTransformf.compose` produced the wrong translation when `dest == parent`
  (review M-2).
- `CurveReparameterizer3f/3d.mapArcLengthToT` took the sample count from `table.length`, so oversized or
  reused tables gave a wrong t (0.984 instead of 0.526). It now has a `samples` overload, and NaN propagates
  instead of returning 0 (review M-6).
- `Reduction.sumStrict`/`dotStrict` (STRICT `sum`/`dot`) dropped the per-lane Kahan compensations when it
  combined lanes, so `{1e8, 3, 3, 3, 3, 0, 0, 0}` gave the FAST answer 100000008 instead of 100000016. Both are now
  sequential double-double, within one ulp of the exact result (review M-10).
- `FFT1f` ran its twiddle recurrence in float, losing about 3 digits at large sizes (relative error 2.2e-4 at
  N = 65536, 2.2e-6 at N = 1024). The recurrence now runs in double; the error is 1.2e-7 to 1.6e-7 (review M-7).
- `PoissonSampling.Disk` searched only the neighbouring ±1 grid cells, but ±2 are needed at cell size
  minDist/√2, so samples came as close as about 0.7 × minDist. It also drew the first sample in the unit disk,
  which threw `ArrayIndexOutOfBoundsException` for radii below 1 (review M-9, upstream JOML).
- Several benchmarks filled packed-affine and palette arrays by hand in inconsistent orders, some using the
  always-zero `m03`/`m13`/`m23`. They now use the canonical row-major layout.
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
