# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

## [Unreleased]
### Added
- Test fixtures (`ValueFixtures`) fill and compare primitive-array state, so array-backed types (`ShCoeffs*`,
  `FrustumPlanes`) are now driven by `TwinParityTest`, `AliasingTest` and the determinism golden. The golden gains 32
  entries, among them every `ShProjection` and `ShConvolution` method; nothing already in it changed.

## [2.0.0] - Unreleased
Major version: several changes alter results or reject inputs that 1.10.13 accepted. See the **Breaking** entries
below and `docs/conventions.md` before upgrading: the packed-affine layout is row-major, STRICT mode validates
inputs, integer vector distances throw on overflow, the SH basis follows the standard ordering, and colour
temperature follows the Planckian locus. Requires JDK 25 and `dynamis-parent` 1.0.1.

### Added
- Per-task math mode: `CoreConfig.runWithMathMode(mode, task)` / `callWithMathMode(mode, task)` override the
  process-wide mode for one task on the calling thread (and its `StructuredTaskScope` subtasks) through a
  `ScopedValue`, so a STRICT simulation step can run next to FAST rendering threads. `defaultMathMode()` reports
  the process-wide value; `mathMode()` and `determinismFingerprint()` report the one in effect.
- `CrossPlatformDeterminismTest`: hashes the results of every public method (about 8 200) and of the batch kernels
  under `-Dvectrix.strictMath=true` and compares them with a recorded golden. CI now runs `mvn verify` on x86_64
  Linux, arm64 Linux and arm64 macOS, in both the default and experimental profiles (it ran `mvn test` on x86_64
  Linux only).
- `AddressApiTest`: the raw-address API writes the same bytes as the `ByteBuffer` API and reads back what it
  wrote, for every type, on the FFM path, the Unsafe path and with each of them denied.
- JaCoCo coverage report and a ratcheting gate (now line ≥ 78 %, branch ≥ 53 %; review M-17). Coverage rose from
  18.4 % / 17.5 % at the review to 78.4 % / 53.4 % (66.5 % / 42.4 % after Batch 5), mainly through
  `TwinParityTest` (originally `FloatDoubleParityTest`), which drives about 8 400 float/double method pairs with identical random inputs, plus
  behavioural tests for the matrix stacks, `SimplexNoise`, `FrustumRayBuilder`, `InstanceSubmissionPipeline`,
  `QuaterniondInterpolator`, the sampling helpers and every B-spline variant.
- `ColorScienced.kelvinToLinearRgb` (double parity), `Quaterniond.angularError`, and
  `FrustumPlanes.set(m, normalize, zZeroToOne)` for [0, 1] clip depth (Vulkan / Direct3D).
- `ColorMathf/d.linearSrgbToXyz` and `xyzToLinearSrgb` scalar overloads, the single source of the sRGB/XYZ
  matrices.
- `AccurateMath`: the single place where transcendental functions choose between `java.lang.Math` and
  `StrictMath`. `-Dvectrix.strictMath=true` makes every transcendental in the library bitwise reproducible across
  platforms, overriding `joml.fastmath` (review M-12). All 72 direct `java.lang.Math` transcendental calls now go
  through it, and a test rejects new ones.
- `CoreConfig.determinismFingerprint()` names every setting that changes output bits, for replay files.
  `docs/determinism.md` documents them.
- `-Dvectrix.test.jvmArgs` passes extra JVM arguments to the experimental profile's tests, for example
  `-Djoml.forceFfm=true` to run the suite on the FFM backend.
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
- **Breaking (stored SH data):** `ShBasis` now uses the standard real SH basis (Ramamoorthi and Hanrahan; no
  Condon–Shortley phase): `Y(l, m)` at index `l(l+1)+m`, evaluated on the direction as passed. It mixed
  conventions before (band 1 Z-up; bands 2 and 3 Y-up with xy/xz swapped; a negative sign on four band-3
  functions only), so shaders and standard SH rotation could not consume the coefficients. Indices 0–5 and 7 are
  unchanged; 6 is now `3z² − 1` (was `3y² − 1`), 8 is now `x² − y²` (was `x² − z²`), and all of band 3 (9–15)
  changed. Re-project stored coefficients. `ShConventionTest` pins the basis against associated Legendre
  polynomials.
- **Breaking (colors):** `ColorSciencef/d.kelvinToLinearRgb` compute the blackbody color from the Planckian locus (Kim et al. 2002 fit in
  CIE xy, through the shared XYZ/sRGB matrix) instead of the Tanner Helland curve fit. The visible jump at 6600 K
  is gone (the largest 1 K change is now 6e-4, at a spline join), and the chromaticity matches Planck's law
  integrated over the CIE 1931 functions to 1e-3 from 2700 K to 25000 K. Colors shift: 6500 K is now
  (1, 0.944, 0.993) in linear sRGB, slightly magenta like the true locus, and the input is clamped to [1667, 25000] K
  (was [1000, 40000]). DynamisSky's sun and ambient colors use this.
- The raw-address API (`getToAddress`, `setFromAddress` and their transposed forms) runs on FFM by default, through
  one segment spanning the address space, so it keeps working once `sun.misc.Unsafe` memory access is denied.
  Without `--enable-native-access=org.dynamisengine.vectrix` (or `ALL-UNNAMED`) the JDK prints a one-time
  "restricted method" warning instead of the Unsafe deprecation warning. `-Djoml.forceUnsafe`, `-Djoml.noffm` or
  denied native access select the Unsafe path. Loops of transfers are faster than with Unsafe (93 vs 175 ns for 16
  `Matrix4f` writes and reads, 1.2 vs 3.2 µs for 256); a single transfer costs about 4 ns more.
- Benchmarks re-measured after the review fixes (`BENCHMARKS.md`, "Current Published Snapshot (2026-11-21)"); the
  earlier snapshots are kept as history, since their SIMD figures measured scalar code. The showcase and
  state-of-the-union docs carry dated corrections.
- `FFT1f/d` compute each twiddle once per stage instead of once per block (bit-identical results): `forwardInverse`
  at 1024 points is 13.7 ns per element, faster than both the float-recurrence original (15.8) and the
  double-recurrence fix (20.1).
- The SH basis is evaluated in straight-line code again. The allocation-free refactor computed it through a per-index
  switch that the JIT did not fold, doubling the cost of projection and evaluation; `ShConsistencyTest` now keeps
  every copy of the formulas bit-identical.
- **FFM is the default memory backend.** Buffer transfers use bounds-checked `MemorySegment` views and never touch
  `sun.misc.Unsafe`, so consumers no longer get the JDK's "terminally deprecated method in sun.misc.Unsafe" warning
  at startup. Measured on arm64: loops of 16–256 `Matrix4f` transfers equal or beat Unsafe; a single isolated
  transfer costs about 1 ns more (2.8–3.3 ns vs 2.1 ns). `-Djoml.noffm=true` or `-Djoml.forceUnsafe=true` selects
  Unsafe. The raw-address API (`getToAddress`/`setFromAddress`) still needs Unsafe and initialises it only when
  first used.
- Namespace and coordinates: packages moved from `org.vectrix` to `org.dynamisengine.vectrix`, and the build
  adopted `dynamis-parent` (2026-03-06, previously unrecorded). `ColorSciencef.kelvinToLinearRgb` was added
  upstream from DynamisSky.
- `ColorSciencef/d` sRGB/XYZ conversions delegate to `ColorMath`'s 7-digit matrices instead of a 4-digit copy;
  results change by about 1e-4 and the two directions are now accurate inverses.
- `Fresnelf/d.conductor` is the exact conductor Fresnel equation (pbrt-v3). It used the pbrt-v2 approximation,
  off by up to 0.099 (n = 1.5, k = 0, 79°), without saying so. `dielectric` with a negative cosine now swaps the
  indices (a ray leaving the medium) instead of returning about 1.
- `Quaternionf/d.integrateAngularVelocity` keeps small rates: it returned the identity below 1e-4 rad/s (float) or
  1e-6 rad/s (double), less than Earth's rotation. `angularError` likewise no longer zeroes errors below 2e-6 rad.
- `UniformBSpline*` copies its control points, so later changes to the caller's array or vectors do not move the
  curve.
- `ShBasis` documents its 16-term ordering index by index; band 1 is Z-up ordered while the zonal terms of bands
  2–3 are Y-up, so external SH data must follow the table.
- Working memos (performance passes, phases, namespace migration) moved to `docs/archive/`, with a note that their
  SIMD numbers measured scalar code.
- JVMCI code blobs (experimental profile) are disabled on Windows pending verification: they appear to use stack
  below RSP, and Win64 has no red zone. `-Dvectrix.jvmci.allowWindows=true` re-enables them.
- **Memory backends (review H-9).** The FFM backend now accesses buffers through bounds-checked
  `MemorySegment.ofBuffer` views. It no longer uses Unsafe, raw addresses or restricted methods, and checks
  limits, slices and read-only buffers like NIO. It is compiled into the default jar and is the fallback when
  Unsafe is disabled (`-Djoml.nounsafe`) or denied by the JVM (`--sun-misc-unsafe-memory-access=deny`), instead of
  plain NIO. Measured per `Matrix4f` transfer on arm64: Unsafe 2.0–2.1 ns, FFM 2.6–3.4 ns, NIO about 4.1 ns.
- The Unsafe backend probes one real memory access during initialisation. Under
  `--sun-misc-unsafe-memory-access=deny` the lookups succeed but every access throws, so it used to be selected
  and then fail on every transfer.
- `getToAddress`/`setFromAddress` throw `UnsupportedOperationException` when Unsafe is disabled or denied, as
  documented, instead of `NoClassDefFoundError`.
- `skinLbs4Vector` (AoS) and `skinLbs4SoASimd` run the scalar kernel. Their "SIMD" bodies did scalar work per lane
  plus `withLane`. A real gather kernel measured 6.8× slower than scalar on arm64, where NEON has no gather.
- The `experimental` profile builds again on JDK 25, and its tests now include the previously uncompiled
  `experimental/**` tests (review M-16). It compiles without `--release`, which hides `jdk.internal.vm.ci`.
- Benchmarks fork with `--add-modules=jdk.incubator.vector`. Earlier "SIMD" benchmark results measured the scalar
  code; see the `SimdSupport` fix below.
- `Vector3L` constructors take `long` (`(long)`, `(long, long, long)`, new `(long[])`). `setComponent` takes a
  `long`, and the `(Vector2dc, z, mode)` constructor takes a `double` z. `Vector4L` gains a `(long[])`
  constructor. `Vector2L`/`Vector2Lc.minComponent`/`maxComponent` return `int` (a component index), like every
  other vector type. All of these are source compatible.
- **Breaking: packed affine layout (review M-1).** `PackedAffineArray` now stores rows (`[r00 r01 r02 tx, r10 r11 r12 ty,
  r20 r21 r22 tz]`), the layout already used by the skinning palette and Vulkan's `VkTransformMatrixKHR`. It
  previously stored basis columns under the same documentation, so a packed array fed to `skinLbs4MatrixPalette`
  or uploaded to a row-major consumer applied the inverse rotation. Code that reads `raw()` by index must swap
  elements 1/4, 2/8 and 6/9. The JOML mapping (`rRC = m.mCR()`) is in `docs/conventions.md`.
- **Breaking: one STRICT contract (review M-11).** `MathMode.STRICT` now means: validate inputs and throw, never repair,
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
- **Breaking:** legacy pre-JDK-25 compatibility layer. JDK 25 is now the minimum and is enforced by `maven-enforcer-plugin`:
  - `buildhelper/Java6to2` bytecode downgrader and the `maven.compiler.source/target 1.8` properties.
  - `__HAS_NIO__` and `__HAS_INTEGER_SIGNUM__` preprocessor guards (always true on JDK 25+).
  - `Runtime.HAS_floatToRawIntBits`, `Runtime.HAS_doubleToRawLongBits`, `Runtime.HAS_Long_rotateLeft`, and the
    reflection probes behind them and `Runtime.HAS_Math_fma`. `HAS_Math_fma` now reflects only `-Djoml.useMathFma`.
  - Java 1.4 `DecimalFormat` workaround and ten duplicated copies of the matrix `toString` exponent formatter
    (all now call `Runtime.formatNumbers`). Output is unchanged.

### Fixed
- `Matrix2f.getTransposed(FloatBuffer)` and `getTransposed(ByteBuffer)` wrote the matrix untransposed: they
  delegated to `get` instead of `getTransposed` (upstream JOML). Found by `AddressApiTest`.
- Byte order and backend consistency (new `ByteOrderTest`, which checks 179 buffer writers in big-endian,
  little-endian and native direct buffers on every backend):
  - `Quaternionf.getAsMatrix4f(ByteBuffer)` put the 1.0 in m32 instead of m33 for big-endian buffers, the default
    for `ByteBuffer.allocate`: the NIO path wrote two floats as one `putLong`, which follows the buffer's order.
  - The FFM and Unsafe backends packed float and int pairs into 64-bit writes assuming a little-endian CPU. The
    packing now follows the native byte order (`MemUtil.pairInts`), so big-endian JVMs get the same results.
  - Unsafe `Matrix2d.getTransposed` wrote `m00, m10, m10, m10` and `getTransposedFloats` wrote `m00` four times
    (upstream JOML); only `-Djoml.forceUnsafe` used them after FFM became the default.
  - `Quaternionf.getAsMatrix3f/4f/4x3f` produced slightly different values (one ulp) on the FFM/Unsafe paths than on
    the NIO path, which used a different but equivalent formula. All backends now evaluate the same expressions.
- `RayAabIntersection.test` missed hits for rays with a zero x component and positive y and z (`OPP`), and for zero
  y with positive x and z (`POP`): their slope inequalities were reversed (upstream JOML). Found by comparing with
  `Intersectionf.testRayAab` (new `GeometryEquivalenceTest`, which also checks `FrustumIntersection`, `Affine4f`,
  `GeometryUtils` and axis-angle conversions against independent references).
- **Breaking:** `lengthSquared`, `distanceSquared` and (long vectors) `gridDistance` of the int and long vectors compute exactly
  and throw `ArithmeticException` when the result does not fit in a long, instead of wrapping to a negative value.
  `Vector2i/3i/4i.gridDistance` summed int differences and overflowed; it now uses long arithmetic.
- `ColorMathf/d.luminanceLinear` uses the Y row of the sRGB/XYZ matrix (7 digits) instead of the 4-digit Rec. 709
  weights, so luminance and XYZ agree.
- Found by widening the parity test to int/long vector twins, same-class float/double overloads, and `int` and array
  parameters (about 9 900 calls):
  - `Vector2i/3i/4i.length()` and `distance()` returned doubles computed in float precision (`Math.sqrt(long)`
    resolved to the float overload), and their differences overflowed `int` for far-apart points, the same defects
    fixed in the long vectors (review H-6). A `Vector4i` at `Integer.MIN_VALUE` had length NaN.
  - `Matrix3x2f/d.set(Matrix2fc/dc)` zeroed the translation although documented to "not change the other elements"
    (`Matrix3x2d.set(Matrix2fc)` already kept it).
- Bugs inherited from JOML, found by the new aliasing harness (`AliasingTest`), which runs every
  destination-style method with `dest` aliased to `this` and to each argument, and checks that results do not
  depend on what `dest` held before:
  - `rotateAround`/`rotateAroundAffine` (`Matrix4f/4d/4x3f/4x3d`) with a separate `dest` computed the translation
    from the old third column: only the in-place call was correct.
  - `Quaternionf/d.transform*(Vector4fc/dc, dest)`, `AxisAngle4f/d.transform(Vector4, dest)` and
    `Vector4f/d.rotate`/`rotateAxis` left `dest.w` at its previous value instead of the input's `w`.
  - `Vector3d.half(other, dest)` overwrote `other` before reading it when `dest == other`.
  - `normalize3x3(dest)` left part of `dest` stale: `m03/m13/m23` in `Matrix4f/4d`, the translation in
    `Matrix4x3f/4x3d`.
  - `Quaternionf/d.div(scalar, dest)` returned `this` instead of `dest`.
  - `Matrix4f/4d.translationRotateScaleMulAffine` ignored the scale (found while triaging the harness output).
- Bugs inherited from JOML, found by the new float/double parity test (`TwinParityTest`, review M-17):
  - `Vector2f.mulPosition(Matrix3x2fc)` and `mulDirection(Matrix3x2fc)` computed y from the already-updated x, so
    every in-place call was wrong.
  - `Quaterniond.rotationX` built `(sin, 0, cos, 0)`, which is not a rotation about x.
  - `Matrix4f.set(Quaterniondc)` left `m23` unchanged, and `Matrix4x3f.set(Quaterniondc)` left the translation.
  - `Matrix4d.rotateAround(q, ox, oy, oz, dest)` wrote into `this` and ignored `dest`; the identity shortcut did
    the same in `Matrix4f/4d/4x3f/4x3d`. `Matrix3x2f/d.scaleAround(factor, ox, oy, dest)` and
    `Matrix4f/4d/4x3f.withLookAtUp(up, dest)` also ignored `dest`.
  - `Quaternionf/d.nlerpIterative` looped forever for a threshold of 1 or more (|dot| never exceeds 1); it now stops
    after 32 halvings, below float/double resolution.
  - `Quaternionf/d.angle()` and `get(AxisAngle4f/4d)` used `2 acos(w)`, which is 0 in float below about 5e-4 rad;
    the axis was also returned unnormalised below `sqrt(1 - w^2) = 0.001`. Both now use `atan2` and a unit axis.
- `Epsilonf/d.equals` treated an infinity as equal to any other value (the tolerance became infinite too).
- Hot-path allocation (review M-14): `squad`/`squadControlPoint` (eight quaternions per call), the SH projection and
  evaluation convenience overloads, and every curve `evaluateBatch` (one vector per sample) no longer allocate;
  arc-length table builders no longer allocate per sample. Results are bitwise unchanged, and `squad` and
  `squadControlPoint` are now alias-safe for every argument.
- The FFM backend checks a transfer's whole range once with `asSlice` instead of on every store. Measured A/B on
  arm64 (3 forks, `MemoryBackendBenchmark`): loops of 16 and 256 `Matrix4f` transfers are 11–27 % faster and now
  equal or beat Unsafe (256 into a ByteBuffer: 461 ns vs 523 ns); a single isolated transfer is about 0.2 ns slower
  than before (2.8–3.3 ns, Unsafe 2.1 ns). A public `get(MemorySegment, long)` and a batched transfer API were
  prototyped and dropped: neither was faster (the batch loop was 10–30 % slower).
- `CatmullRom4f.derivative` evaluated the curve into `dest` first, corrupting the result when `dest` aliased a
  control point.
- `MeshMath.barycentric` and `winding2D` tested an area that scales with length⁴ (length²) against an absolute
  epsilon, so triangles below about 3 cm were degenerate (a 1 mm triangle gave NaN). The tests are now relative.
- `Intersectionf.intersectRayAar` used `<=` where every other ray test and `Intersectiond` use `<` (upstream).
- SIMD frustum culling classified NaN bounds as inside, unlike the scalar path.
- `GpuTransformLayout.requiredBytes` and the write offsets overflowed `int` silently
  (`requiredBytes(100_000_000)` was −1494967296); they now throw `ArithmeticException`.
- `SobolSequence`/`HaltonSequence` float outputs could round up to 1.0f, outside the documented [0, 1).
- `Sdf3f/d.capsule` with `a == b` returned NaN; it is now a sphere.
- `Convolutionf/d.linear` with an empty input threw `ArrayIndexOutOfBoundsException`; it now throws
  `IllegalArgumentException`.
- `Epsilonf/d.equals(+Inf, +Inf)` returned false.
- `LtcTablef/d.sample*` rejects NaN roughness or `ndotv` instead of returning NaN.
- JVMCI fast paths (experimental) crashed the JVM on a null `dest`; they now throw `NullPointerException` first.
- `Quaternionfc`/`Quaterniondc` had two stacked Javadoc blocks on `log`, `exp`, `squad` and `squadControlPoint`;
  each now has one, including aliasing rules, STRICT behaviour and the `log(-1)` convention.
- Build: removed the invalid `includeTestSourceRoots` Checkstyle parameter (a warning on every build). Stale
  `org/joml` and `org/vectrix` paths fixed in `CLAUDE.md`, `AGENTS.md` and the README layout.
- `SimdSupport` never detected the Vector API: it called `length()` through the non-public species class, got
  `IllegalAccessException`, and reported `SCALAR` even with `--add-modules jdk.incubator.vector`. No SIMD path had
  ever run. It now uses the public `VectorSpecies` interface (review M-15).
- Vector API builds threw `NoClassDefFoundError` from the *scalar* skinning and culling paths on a JVM without
  `--add-modules jdk.incubator.vector`: a static species field, and Vector API method bodies that HotSpot verifies
  when the class links. SIMD code now lives in nested `Simd` classes that only the SIMD path loads (review M-15).
- `TransformKernels.composeBatch`'s Vector API path was a scalar stub. It is now a real kernel, bitwise identical
  to the scalar path and 3.6–3.7× faster for 64 to 1024 transforms on arm64 (memory-bound at 16384).
- `Affine4f` called `java.lang.Math.fma` directly, ignoring `-Djoml.useMathFma` (review M-13).
- `CoreConfigModeTest`'s "isolated" class loader was not isolated under Surefire's module path: the platform
  loader delegated to the already-loaded classes. Tests that observe static initialisation now use
  `IsolatedClasses` (bootstrap parent, with a self-check). Re-verified: the pre-fix `CoreConfig`/`KernelConfig`
  fail both H-8 scenarios.
- Memory backend safety (review C-1, C-2):
  - The Unsafe backend crashed the JVM (SIGSEGV) when given heap NIO buffers, and wrote outside direct buffers
    for out-of-range offsets. Heap and read-only buffers now take the NIO path, and the raw-address path is
    always bounds-checked against `limit()`, throwing `IndexOutOfBoundsException`. Previously this check ran only
    under `-Djoml.debug`. Cost: about 0.06–0.28 ns per buffer transfer (`MemoryBackendBenchmark`).
  - The FFM backend wrote to the wrong native address: it added the buffer position twice, and wrote outside
    slices. It also sent heap buffers down the raw-address path. Both are fixed. Because FFM still lacks bounds
    checks, it was made opt-in via `-Djoml.forceFfm=true`. (Later superseded: the backend was rewritten with bounds checks and is now the default; see Changed.)
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

## [1.10.12] - 2026-02-24
Reconstructed from git history; this release shipped without a changelog entry.
### Added
- `org.vectrix.physics` package: `InertiaTensorf`, `PbdConstraintsf`, `PdControllersf`, `SpringDamperf`
  (now `org.dynamisengine.vectrix.physics`).
- `Quaternionf.angularError(from, to, dest)` and `Math.atan(float/double)`.

## [1.10.11]
Not released (the version was skipped).

## [1.10.10] - 2026-02-24
Reconstructed from git history.
### Changed
- The JPMS module exports the `color` package.

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
