# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

## [Unreleased]

## [3.0.0] - 2026-11-23
New in this release:
- **Every catalogued issue fixed.** The 2.6.0 documentation pass read every public method. 3.0 fixes everything it
  found, each fix with a test, and `docs/maintainers/known-issues.md` now lists only deliberate limitations. The
  most consequential fixes:
  - spherical-Gaussian irradiance was wrong below sharpness 1;
  - a zero quaternion compressed to a half turn;
  - several bounding volumes could cut into their shapes;
  - robust predicates threw on NaN;
  - the unscented Kalman filter could lose positive definiteness;
  - methods across `dsp`, `nn`, `soa`, `gpu`, `sh` and `curve` failed partway on bad arguments.
- **Consistent API.**
  - Sphere and circle methods take the plain radius.
  - Clamp arguments are ordered like `core.Math.clamp`.
  - Read-only parameters use the `...c` interfaces.
  - Every array API checks its arguments up front.
  - Skinning applies joint scale.
  - Kalman filters can gate a measurement without updating.
- **The deprecations of 2.6.0 are removed**, along with `StdLayout` and other duplicates.

Upgrading from 2.x: read [`docs/migrating-to-3.0.md`](docs/migrating-to-3.0.md). Some changes alter results without
changing signatures.

Major release: the deprecations of 2.6.0 are removed, and the issues catalogued in 2.6.0 are fixed, including those
that needed an API change. Upgrading: read [`docs/migrating-to-3.0.md`](docs/migrating-to-3.0.md) first. It lists
the changes that keep their signatures but change their results (the sphere and circle radius convention,
`Rangef.clamp` order, skinning with joint scale), which the compiler cannot catch.

### Removed
- Deprecated in 2.6.0:
  - `simd.Vector4fa`;
  - `nn.NnMath` (use `core.ReproducibleMath`) and `nn.Philox` (use `hash.Philox`, and `nn.Activations.dropout`);
  - `fft.FFT1f`/`FFT1d` and `fft.Convolutionf`/`Convolutiond` (use `ComplexFft*`, `RealFft*` and
    `BlockConvolver*`);
  - `fft.Complexf`/`Complexd` (use `core.Complexf`/`Complexd`);
  - `core.QuaternionfInterpolator`/`QuaterniondInterpolator` (use `QuaternionCurves*.weightedMean`);
  - `camera.DepthRange` and every overload taking it (use `core.DepthRange`);
  - `affine.TransformKernels` (use `soa.TransformKernels`);
  - `renderingmath.AtmosphereParams` and `TransmittanceLutBuilder` (use the `atmosphere` package);
  - the backend-specific `CullingKernels` and `SkinningKernels` entry points, and
    `CullingKernels.frustumCullAabbBatch(Matrix4fc, ...)` (use `FrustumPlanes`);
  - `ShCoeffs*.BANDS` (use `COEFFS`);
  - `SpringDamper*.overdamped`, `SpringDamper*.suspensionForce`, and the six-argument
    `Matrix3x2*.scaleAroundLocal`.
- The `vectrix.skinning.simd` property (there is no SIMD skinning kernel).
- The public constructors of the static utility classes (`core.Math`, `Interpolationf/d`, `SimplexNoise`,
  `Intersectionf/d`, `GeometryUtils`, `sampling.Convolution` and the sampling holders), which are now `final`.

### Changed (breaking)
The migration guide covers each of these.
- **Results change, signatures do not:**
  - `Intersectionf`/`Intersectiond`: every sphere and circle method takes the plain radius. Thirteen methods used to
    take the squared radius.
  - `testSphereSphere` counts containment as intersecting.
  - `Rangef`/`Ranged.clamp` take `(min, max, value)`.
  - `SkinningKernels.skinLbs4` and `skinLbs4SoA` apply joint scale.
  - `new PackedAffineArray(n)` starts as identity transforms.
  - `Contacts*.nearestFace` returns the distance to the box for outside points.
  - `InstanceSubmissionPipeline` writes bounds per slot.
  - `Easing*.spring` ends exactly at 1, and `Sampler.argmax` treats NaN like `Pool2d.max`.
- **Removed:**
  - `gpu.StdLayout` (use `ShaderLayout`);
  - `GpuTransformWriteKernels.writePackedAffine` (use `PackedAffineKernels.uploadPackedAffine`);
  - `GpuTransformLayout.writeFloatTrs`/`writeCompactTrs` and the `GpuTransformWriteKernels` equivalents (the
    layout decides the encoding).
- **Renamed or re-signed:**
  - `Affine4d.toMatrix4f(Matrix4d)` is now `toMatrix4d`, and `ShConvolution.evaluateIrradiance` is now
    `evaluateL2`.
  - `SphericalGaussians*.fit` takes offsets.
  - `Atmosphere.multipleScatteringLut` and `MeshMath.vertexTangents` take caller scratch.
  - `CurveReparameterizer3*` table builders return the segment length.
  - `Toeplitz.levinsonDurbin` needs `levinsonDurbinScratchSize(p)`.
  - `GpuTransformWriteKernels.writeMatrix4fFromPackedAffine` takes an output offset.
  - `RealFft2*.inverse` preserves its input (size scratch with `scratchSize()`).
- **Read-only parameters:** `testObOb`, `testMovingCircleCircle`, `PbdConstraints*` and
  `Interpolationf.bilinearVec3` take `...c` interfaces. This is source-compatible; recompile.
- **New exceptions for invalid input:**
  - `Transform*.invertRigidFast` (non-unit scale);
  - `DualQuatTransform*.blend` (no transforms or all-zero weights);
  - `PoseKernels*` (only one scale array);
  - `PdControllers*.clampTorque` (negative limit);
  - `UnscentedKalman` (`n + kappa <= 0`);
  - `Wavelet.fromLowpass` (taps that are not orthonormal);
  - `FirDesign.kaiserBeta`/`kaiserTaps`;
  - `Kalman.scratchSize`;
  - `Formats.quantizeInt8` (NaN or infinite input);
  - `Attention` (row strides);
  - `UniformBSpline1*` (copies its control points);
  - `GpuTransformLayout` (invalid layouts);
  - `VertexLayout` (misaligned stride);
  - `ShaderLayout` (two structs with the same name);
  - `AtmosphereModel` (non-finite fields).
- **Last-bit differences:** `ComplexFft2*.inverse` now rounds once, and `UnscentedKalman.update` uses the Joseph
  form.
- `CoreConfig.determinismFingerprint` names its entries `vectrix.*` and leaves out the SIMD settings, which never
  change result bits.

### Added
- `Kalman`, `ExtendedKalman` and `UnscentedKalman` gain `innovationDistance`, the gating distance without
  updating, and `dsp.InnovationFunction` lets the extended filter wrap angle innovations.
- `hash.Philox` gains `uniformDouble`, and `fillUniform`/`fillNormal` for doubles (53-bit).
- Float overloads of `Correlation.correlate`, `Toeplitz.solve`/`levinsonDurbin` and `Wavelet.forward`/`inverse`.
- `RobustPredicates.incircle` and `insphere` for floats.
- `LeastSquares*.solve` with caller scratch.
- `Projections*.ndcDepth(proj, x, y, z)`, exact for oblique projections.
- `PackedAffineKernels.uploadPackedAffine` with an output offset.
- `SkinningKernels.buildMatrixPalette12`, with scale.
- `ShCoeffs16*.get`/`set`, and `ShConvolution.evaluateL2` on arrays.
- `ColorSpacesf.toXyz`/`fromXyz` and the matrix getters.
- `Matrix4dc.frustumAabb`.
- `Interpolationd.bilinearVec3`.
- `QuatCompression.IDENTITY` and `OctaNormal.UP`.
- Float overloads for `Spectra` (`periodogram`, `welch`, `crossWelch`, `analytic`) and for every `Wiener` method,
  with their float scratch sizes. Like the other float overloads, they compute in double and round once.
- `ParticleFilter.systematicResample(w, count, seed, index, indices)` draws its uniform from `Philox.uniformDouble`.

### Fixed
- `SphericalGaussians*.irradianceFitted` was wrong below sharpness 1: NaN at 0, about −3e15 at 1e-6, still negative
  at 0.5. It now computes the exact value there.
- `QuatCompression` encoded a zero or NaN quaternion as a half turn. It is now the identity, and `OctaNormal`
  defines zero and NaN input too.
- `RobustPredicates` threw `NumberFormatException` for NaN or infinite input. It now returns NaN.
- The `BoundingVolumes*` bounds could cut into their shape by up to half an ulp. `transformSphere`,
  `aabOfCylinder`, `aabOfCapsule`, `aabOfObb` and `aabOfTransformedPoints` now round outward.
- `Formats.quantizeInt8` underflowed to a zero scale for blocks of tiny values. Their scale now rounds up, so the
  codes stay meaningful.
- `SpatialCodes.morton2dX`/`Y` mis-decoded codes with the top bit set.
- `GpuTransformLayout.write` silently used the compact encoding for mixed layouts.
- `WorldPos.normalize`, `add` and `set` are atomic.
- `Affine4*.getTransform` returns a unit rotation for reflections and shear.
- `JointLimits*` with a zero limit gave NaN. `DampedLeastSquares*` with `lambda = 0` on a straight chain gave NaN.
- `PbdConstraints*` degeneracy thresholds are relative to the configuration's size.
- `Atmosphere.densityIntegrals` integrated backwards for a negative length.
- `DfgLutBuilder` accepted invalid input. Roughness 0 gives the mirror limit.
- NaN indices of refraction passed the optics checks.
- `UnscentedKalman.update` could lose positive semi-definiteness. It now uses the Joseph form.
- `ComplexFft2*.inverse` and `RealFft2*.inverse` scale once.
- Up-front validation, with no partial writes:
  - `Spectra`, `FirDesign`, `Biquad.response`, `Toeplitz`, `Wavelet`, `Smoothers.median`, `Wiener`, `Gemm*`;
  - `Kalman`, `ExtendedKalman` and `UnscentedKalman`;
  - `ShProduct`, `ShWindowing`, `AliasTable`, `CieObserver`;
  - `PackedAffineKernels`, `PoseKernels*`, `IntegratorRK4*`, `Projections*.haltonJitter`;
  - the `soa` kernels;
  - every `nn` kernel;
  - `InstanceSubmissionPipeline`, `PackedFormats.qTangent`, `Conv2d.of`/`Pool2d.of`;
  - `ParticleFilter`;
  - the curve batch evaluators in `curve.scalar`, `vec2`, `vec3` and `vec4`. A negative count used to do nothing,
    and now throws.
- Allocation removed from:
  - `ColorSpacesd.adaptationMatrix`, the LTC clipped form factors and the atmosphere quadrature;
  - `SpatialCodes.hilbert3d`, `MeshMath`, `BoundingVolumes*.kDop` and least squares;
  - `PackedFormats.qTangent` and the chunked packed-affine kernels.

## [2.6.0] - 2026-11-23
New in this release:
- **Documentation:**
  - Every public type and member is documented, and the build enforces it (doclint, warnings fail).
  - Every package states its threading, allocation, determinism and error contract.
  - The docs were rewritten: a short README, getting started, conventions, a package map and eleven task guides.
    All their Java code is quoted from tested examples.
- **Examples:** `examples/`, nine runnable programs (culling, cameras, large worlds, animation, SH lighting,
  signal processing, Kalman tracking, a small network). Each is tested and run in CI.
- **Structure:**
  - The packages form a dependency DAG, enforced by a test.
  - Shared types moved down: `core.DepthRange`, `core.ReproducibleMath`, `core.Complexd`/`Complexf`,
    `hash.Philox` and `soa.TransformKernels`.
  - The SIMD switch (`SimdSupport`) is public API.
  - The OSGi bundle exports every package.
- **Fixes:** `ReproducibleMath.exp(double)` over its whole range, several output-aliasing and edge-case bugs, and
  up-front validation in `Kalman`, `ShRotation` and the Sobol/Halton batches.

Nothing is removed. Every moved or replaced API keeps a deprecated (`forRemoval`) forwarder until 3.0. The
remaining issues found by reading every method are listed in `docs/maintainers/known-issues.md`.

### Added
- `simd.SimdSupport` is supported API. `isEnabled()`/`setEnabled(boolean)` switch the SIMD paths at run time
  (initially from `vectrix.simd.enabled`), and `backend()` reports the path in use.
- `core.ReproducibleMath` (formerly `nn.NnMath`): float and double transcendentals that give the same bits
  everywhere, now usable by the whole library. Its double functions are overloads (`exp(double)`, not `expD`).
- `core.DepthRange`, `core.Complexd`/`Complexf`, `hash.Philox`, `soa.TransformKernels` and `nn.Activations.dropout`
  are the new homes of types and methods that sat in the wrong package. Every method taking the old types has an
  overload taking the new ones.
- `ShCoeffs*.COEFFS`, the number of coefficients per channel.
- Every inherited `joml.*` system property can also be given as `vectrix.*`. The `vectrix.` name wins when both are
  set.
- Explicit, documented constructors on `FrustumPlanes` and the `ShCoeffs*` classes.
- `examples/`: nine runnable example programs, in a separate project that is not published. Each is tested
  against an independent computation, and CI builds and runs them against every commit.
- New documentation:
  - a short README, `docs/getting-started.md` and an expanded `docs/conventions.md`;
  - `docs/package-map.md`;
  - eleven task guides in `docs/guides/`;
  - `docs/performance.md`, which merges six earlier pages.
- Every Java block in the docs is quoted from a tested example (`DocSnippetsTest`). Every API the docs name must
  exist and not be deprecated, and every relative link must resolve (`DocReferencesTest`).
- `SimdSupport.isSimdBuild()` reports whether the running jar is the `-simd` classifier.
- Tests that keep the structure from regressing:
  - `PackageDependencyTest`: the top-level packages form a DAG, with `core` and `hash` at the bottom.
  - `PackageExportsTest`: module-info, the OSGi `Export-Package` and the source packages agree.

### Changed
- Every public and protected type, member, parameter and return value is now documented. About 6,300 gaps were
  filled. The build enforces doclint (`all`, warnings fail), so a gap can no longer be merged.
- Every package description ends with a contract paragraph covering threading, allocation, determinism, errors,
  and precision and naming.
- Documentation that contradicted the code is corrected, including:
  - `ShRotation` and several IK solvers claimed not to allocate, but they allocate per-thread scratch on the
    first call;
  - `DfgLutBuilder` claimed identical tables on every platform, true only under `vectrix.strictMath`;
  - `Intersectionf.testCircleCircle` called its squared-radius arguments radii;
  - `WorldPos` misquoted its resolution;
  - `CameraRelative.modelMatrix` described its order of rotation and scale backwards;
  - `AtmosphereModel.earth()` gave a Mie extinction that did not match the code.
- The BLAS and GEMM generators keep the hand-written Javadoc of the files they regenerate
  (`scripts/javadoc_overlay.py`).

### Fixed
- `ReproducibleMath.exp(double)` returned +∞ for very negative arguments (such as `-1e300`) and NaN for ±∞, so
  `pow(0, y)` was NaN. Results just below `Double.MAX_VALUE` overflowed. It is now within 1 ulp of `StrictMath`
  over the whole range, including subnormal results.
- Outputs aliasing inputs now work in:
  - `ClosestPointsf/d.distanceSquaredTriangleTriangle`, which also no longer allocates;
  - `TwoBoneIkf/d.solve`, when an output is the target;
  - `QuaternionCurvesf/d.squadControlPoints`, in place.
- `Intersectionf/d.testLineSegmentSphere` treats a zero-length segment as a point. Before, it always returned
  false.
- `PerceptualColorf/d`: HSV and HSL input hues wrap for negative values, and output hues are always in [0, 360),
  never 360 or −0.
- `SobolSequence` and `HaltonSequence` batch methods rejected neither an index range past `Integer.MAX_VALUE` nor
  overflowing offsets. Both now throw `IllegalArgumentException` before writing.
- Up-front validation that throws `IllegalArgumentException`:
  - `Kalman.predict`/`update` check every array and require `n, m >= 1`; before, a short array failed partway
    through;
  - `ShRotation` checks matrix and output lengths.
- `Biquad.process(double[] ...)` with zero sections copies the input to the output, as the float overload does.
- `AtmosphereModel.equals` agrees with `hashCode` for −0.0 and NaN fields.
- `ColorSpacesd.convert` and `conversionMatrix` no longer allocate: every gamut matrix is computed once.
- `SimdSupport.backend()` reported `VECTOR_API` for the plain jar when `jdk.incubator.vector` was present, though
  that jar has no Vector API code.
- The `MathMode` Javadoc promised accurate double-double reductions in STRICT mode. No code implements them since
  the internal `Reduction` class was removed, so the promise is gone too.
- The library's one console message (a JVMCI diagnostic in the experimental build) now goes through
  `System.Logger`. `NoConsoleOutputTest` keeps console output out of the library.
- The OSGi `Export-Package` header omitted eight packages: `camera`, `brdf`, `spherical`, `atmosphere`,
  `animation`, `ik`, `linalg` and `world`. It is now generated from module-info.
- `core` no longer depends on `camera`, and `affine` and `soa` no longer depend on each other, except through the
  deprecated forwarders.

### Deprecated (for removal in 3.0)
- Moved types:
  - `camera.DepthRange` (use `core.DepthRange`);
  - `nn.NnMath` (use `core.ReproducibleMath`);
  - `nn.Philox` (use `hash.Philox` and `Activations.dropout`);
  - `fft.Complexd`/`Complexf` (use `core.Complexd`/`Complexf`);
  - `affine.TransformKernels` (use `soa.TransformKernels`).
- Superseded APIs:
  - `fft.FFT1f`/`FFT1d` and `fft.Convolutionf`/`Convolutiond` (use the planned transforms and `BlockConvolver*`);
  - `QuaternionfInterpolator`/`QuaterniondInterpolator` (use `QuaternionCurves*.weightedMean`);
  - `simd.Vector4fa` (it exists only in the `-simd` jar).
- The backend-specific entry points `CullingKernels.frustumCullAabbBatchScalar`/`Simd` and
  `SkinningKernels.skinLbs4Vector`/`Scalar`/`SoAScalar`/`SoASimd`. The plain entry points pick the backend.
- `ShCoeffs*.BANDS`, which counts coefficients, not bands. Use `COEFFS`.
- `CullingKernels.frustumCullAabbBatch(Matrix4fc, ...)`. It allocates a `FrustumPlanes` per call and assumes
  OpenGL depth, so for [0, 1] and reverse-Z projections it culls nothing near the camera. Extract the planes once
  with `FrustumPlanes.set(m, normalize, depthRange)`.
- The public constructors of static utility classes, such as `Intersectionf`, `GeometryUtils` and `core.Math`.
  They become private in 3.0.

### Removed
- The unexported `experimental` package (`KernelConfig`, and the unused `Reduction`). `KernelConfig`'s SIMD switch
  is now `SimdSupport`, and its math mode is `CoreConfig`.
- Historical process notes, the `docs/archive` folder and superseded plans.
- `API.md`, `docs/vectrix-capabilities.md`, `docs/technology-explainer.md` and `docs/signal-and-ml-roadmap.md`. The
  guides replace them, and their examples, some of which no longer compiled, are now tested. `BENCHMARKS.md` moved
  to `docs/benchmarks.md`, and the maintainer pages moved to `docs/maintainers/`.
- `core.Options` and `core.Runtime` are now marked `@Internal`. Configuration is by system property.

## [2.5.0] - 2026-11-23
New in this release:
- **Signal processing:**
  - The FFT was rebuilt (`fft`): planned transforms of any size, real and 2D, and streaming FIR convolution.
    - Its twiddles are exact: error below 6e-16 at every size, against 7.8e-12 before at 2^20.
    - Its Vector API kernels give the same bits as the scalar ones. At 2^20 it is 14 times faster than `FFT1f`
      (26 times with the SIMD jar).
  - New package `dsp`:
    - windows;
    - FIR design (window method and Parks–McClellan) and biquad filters;
    - STFT, power spectra, correlation and Goertzel;
    - DCT and MDCT, and resampling;
    - wavelets;
    - Wiener filtering: FIR, non-causal, deconvolution of signals and images, adaptive image filtering;
    - state estimation: Kalman, extended and unscented Kalman, and particle filters;
    - adaptive filters (LMS, NLMS, RLS) and smoothers.
- **Machine-learning kernels:** new packages `blas` and `nn`, reproducible bit for bit across platforms, jars and
  batch sizes.
  - BLAS-1, and GEMM/GEMV in which every element is a dot product in a documented order (batch-invariant).
  - Reproducible float math: exp, log, tanh, erf, normal CDF.
  - Activations, softmax, norms, attention (multi-head, grouped and multi-query), rotary embeddings, and
    convolution and pooling, each with a backward pass.
  - Cross-entropy, sampling, Philox, AdamW/SGD, and bf16/FP8/int8 formats.
- **A second audit of the code inherited from JOML:** 49 fixes. They cover matrix property flags, projection
  builders, closest points, frustum and ray tests, extreme scales and fast-math bounds.
- **Projection queries for every depth convention:** NDC [-1, 1], [0, 1] and reverse-Z.

No public API was removed. Results change where the audit fixed them. `FFT1d`/`FFT1f` change in the last bits from
their exact twiddles. `Matrix3x2f/d.scaleAroundLocal`'s 3D overload is deprecated.

### Added
- **FFT rebuild** (`fft`), the first phase of the signal-processing roadmap (`docs/signal-and-ml-roadmap.md`).
  Every class has a float twin (`...f`):
  - `ComplexFftd`: planned transforms of any size.
    - Sizes with prime factors up to 61 use a self-sorting mixed-radix algorithm (radix 4, 2, 3, 5 and direct
      prime butterflies); other sizes use Bluestein's algorithm.
    - `fastSize(min)` gives the nearest size with prime factors 2, 3 and 5 only.
    - The measured relative RMS error is below 6e-16 (float: 2.5e-7) at every size tested, up to 2^22.
    - The kernels are generated from one butterfly description (`scripts/gen-fft-kernels.py`). The `-simd` jar
      adds Vector API kernels that give exactly the same bits as the scalar ones; `FftPlanTest` checks this.
    - Compared with `FFT1f`, a float transform is 1.9 times faster (3.2 with the `-simd` jar) from 256 to 4,096
      points, and 14 times faster (26) at 2^20, measured on an Apple M4 Max.
  - `RealFftd`: real-input transforms with a half spectrum, about twice as fast as complex.
  - `ComplexFft2d` and `RealFft2d`: 2D transforms.
  - `BlockConvolverd`: streaming FIR filtering with caller-held state.
    - It picks direct convolution or FFT overlap-add from a measured cost model.
    - It accepts chunks of any size.
  - Plans are immutable and can be shared between threads. Calls take caller-owned scratch, do not allocate,
    and are deterministic. The 1D plans also accept `MemorySegment` data.

- **Signal processing** (`dsp`, phase 2 of the roadmap):
  - `Windows`:
    - Hann, Hamming, Blackman, Blackman–Harris, Nuttall, flat top, Kaiser, Tukey, Gaussian, Dolph–Chebyshev and
      sine windows;
    - each symmetric or periodic, and bit-exactly symmetric;
    - coherent gain and equivalent noise bandwidth.
  - `FirDesign`:
    - window-method low-pass, high-pass, band-pass and band-stop, scaled to exactly unit gain;
    - Kaiser's β and length formulas;
    - FIR frequency response.
  - `BiquadDesign`:
    - the RBJ cookbook sections (low/high/band-pass, notch, all-pass, peaking, shelves);
    - Butterworth cascades of any order, matching the analytic response to 1e-12;
    - `1 ± cos w` computed as `2sin²(w/2)`/`2cos²(w/2)`, so low-frequency designs don't cancel.
  - `Biquad`:
    - cascade processing in transposed direct form II, in double, with caller-held state;
    - a float-sample overload;
    - frequency response.
  - `Stftd`/`Stftf`: STFT analysis and weighted overlap-add synthesis, with caller-held state. Synthesis
    divides by the windows' actual overlap sum, so reconstruction is exact (error below 1e-14) for any windows and
    hop, not only COLA-compliant ones.
  - `Spectra`: periodogram and Welch power spectral density, density-scaled and checked against Parseval's
    identity; the analytic signal (Hilbert transform).
  - `Correlation`: full cross-correlation plans. They sum directly or use FFTs, whichever is cheaper.
  - `Goertzel`: one DFT value at any frequency. Near DC and Nyquist it uses Reinsch's stabilized recurrences,
    about 10^5 times more accurate there than the classic form.
  - `Dctd`/`Dctf`: DCT-II of any size and its exact inverse, and DCT-IV, in FFTW's conventions, via one FFT.
  - `Mdctd`/`Mdctf`: MDCT and inverse. The inverse is scaled so that windowed overlap-add reconstructs exactly.
  - `Resampler`: streaming rational sample-rate conversion (any up/down) through a polyphase Kaiser filter,
    computed in double for double and float samples. Any chunking gives identical output.
- **Machine-learning kernels** (`blas`, phase 3 of the roadmap):
  - `Blasf`/`Blasd`: BLAS level 1 (dot, sum, asum, nrm2, axpy, scal, iamax) over arrays and `MemorySegment`s.
    - Every reduction has one documented order: 256-element blocks, 8 interleaved fused-multiply-add lanes
      combined in a fixed tree, and pairwise across blocks.
    - The scalar and Vector API paths implement that order exactly, so results are the same bits on every
      platform, vector width and jar. The error is at most about (32 + log2(n/256))·ε·Σ|terms|.
    - `nrm2` doesn't overflow or underflow.
    - Speed on an Apple M4 Max: a float dot product is 2.2 times faster than a plain loop in scalar code, and 6–9
      times faster with the `-simd` jar (0.06 ns per element).
    - The kernels are generated by `scripts/gen-blas-kernels.py`.
  - `Gemmf`/`Gemmd`: `gemm` (C = α·op(A)·op(B) + β·C) and `gemv`, row-major with leading dimensions and
    transposes.
    - Every element is exactly the `dot` of its row and column, in the documented order. Results are therefore
      batch-invariant: a batch of one gives the same bits as a batch of many, for any tiling or thread split.
    - The Vector API path computes 4×2 tiles (2×2 in double) from operands packed into caller scratch.
    - Speed on one Apple M4 Max core: float 51–61 GFLOP/s and double 25–27 GFLOP/s with the `-simd` jar, against
      about 10 GFLOP/s for a plain loop.
    - Generated by `scripts/gen-gemm-kernels.py`.
- **Neural-network kernels** (`nn`, phase 3 of the roadmap, batch 3):
  - `NnMath`: float exp, expm1, log, log1p, tanh, sigmoid, erf, erfc, and the normal CDF and density.
    - They are built from series with exact rational coefficients, exact argument reductions and fused
      multiply-adds, so they are reproducible bit for bit everywhere, unlike `java.lang.Math`. The normal CDF and
      erfc go through a reproducible double exponential where needed.
    - Measured errors: exp 1.1 ulp; erf, erfc and the normal CDF 2.5 ulp (relative, including the far tails); log
      2 ulp; tanh and sigmoid 2.5 ulp.
  - `Activations`: ReLU, sigmoid, SiLU, tanh, GELU (exact, via the normal CDF, so it doesn't cancel for negative
    inputs) and its tanh approximation, softplus; backward passes for ReLU, SiLU and GELU. The exp-based kernels
    have bit-identical Vector API paths.
  - `Softmax`: softmax, log-softmax, log-sum-exp and their backward passes.
    - Stable for any range: ±3e38 works.
    - The shift by the maximum keeps its exact rounding error (TwoSum), so tiny probabilities keep their relative
      accuracy.
  - `Norms`: layer norm and RMS norm, forward and backward, with two-pass variance, saved statistics and
    accumulated γ/β gradients. They are checked against finite differences of a double model.
- **Neural-network kernels, batch 4a** (`nn`):
  - `Rope`: rotary position embeddings, interleaved and half-split layouts, with backward. The angles, up to
    millions of radians at long contexts, are formed in double. Their sine and cosine use a reproducible double
    routine with fdlibm's three-part π/2 reduction.
  - `Philox`: the Philox4x32-10 counter-based generator. It matches the Random123 known-answer vectors.
    - Uniform and normal (Box–Muller) fills, and inverted dropout.
    - Any element can be drawn in any order or thread with the same result, and backward passes regenerate their
      masks.
  - `Loss`: cross-entropy and its gradient, through log-sum-exp.
  - `Sampler`: greedy, temperature, top-k and top-p sampling.
    - The uniform number comes from the caller, and ties go to the lower index.
    - Top-k uses heap selection, and nothing allocates.
  - `NnMath`: the reproducible double functions `expD`, `logD`, `powD`, `sinD` and `cosD` are public.
  - Backward passes for sigmoid, tanh, the tanh-form GELU and softplus.
- **Neural-network kernels, batch 4b** (`nn`), completing phase 3:
  - `Formats`:
    - bfloat16, and both OCP FP8 formats (E4M3 finite-only and E5M2), round to nearest even, with or without
      saturation. The FP8 encoders search the exact value set, and are checked against brute force.
    - int8 block quantization with a scale per 32 values (the largest element maps to ±127 exactly).
    - A quantized dot product and GEMV with a defined lane order (8 lanes per block, with per-lane totals across
      blocks). It has a Vector API path with the same bits. It saves memory, not time: on an M4 Max a 4096×4096
      int8 GEMV takes 1.8 ms against 1.05 ms for float.
  - `Attention`: scaled dot-product attention of one query over a key/value cache, with an optional additive mask
    and a backward pass. It is built from the deterministic dot, softmax and transposed GEMV, so results are
    batch-invariant.
  - `Optim`: AdamW (decoupled weight decay), SGD with momentum and Nesterov, and the global-norm clip factor.
- **Convolution and pooling** (`nn`):
  - `Conv2d`: 2D convolution (1D as a special case) with stride, padding, dilation and groups (including depthwise),
    forward and backward. It is im2col followed by the deterministic GEMM, so every output is one documented-order
    dot product and results are batch-invariant.
  - `Pool2d`: max pooling (with argmax for the backward pass) and average pooling over the in-bounds cells, forward
    and backward.
- **More signal processing** (`dsp`):
  - `FirDesign.remez`: Parks–McClellan optimal equiripple FIR design (any bands, gains and weights).
  - `Wavelet`: periodized orthogonal DWT, single and multilevel.
    - Daubechies filters of 1–10 vanishing moments are computed reproducibly by spectral factorization.
    - The filters are orthonormal to 1e-15.
  - `Kalman`: linear predict/update with a Cholesky gain and Joseph-form covariance. It returns the innovation's
    Mahalanobis distance.
  - `Toeplitz`: Levinson's solver and the Levinson–Durbin recursion (linear prediction, reflection coefficients).
  - `Wiener`: the optimal FIR Wiener filter, and the frequency-domain Wiener gain for STFT denoising.
  - `AdaptiveFilter`: LMS, normalized LMS and RLS.
  - `Smoothers`: Savitzky–Golay coefficients (smoothing and derivatives), a running median, the alpha-beta
    tracker, and the One Euro filter for jittery input.
  - Wiener deconvolution of signals and images (`Wiener.deconvolve`, `deconvolve2d`), with a constant or per-bin
    noise-to-signal ratio.
  - The adaptive image Wiener filter `Wiener.adaptive2d` (MATLAB's `wiener2`).
  - The non-causal Wiener filter `H = P_xy/P_xx` (`Wiener.noncausal`, `impulseResponse`), with Welch cross
    spectra (`Spectra.crossWelch`).
  - `ExtendedKalman` and `UnscentedKalman` for nonlinear models, sharing `Kalman`'s numerics. On linear models
    both equal the Kalman filter.
  - `ParticleFilter`: log-space weight normalization with the evidence increment, effective sample size,
    systematic resampling, gather and weighted mean. It is reproducible with `nn.Philox` noise.
- **More neural-network kernels** (`nn`):
  - `Attention.groupedQuery` (and its backward): grouped-query and multi-query attention over shared caches.
  - Vector API paths for tanh, tanh-GELU and the layer/RMS norm loops (2–3.4 times faster). They give the same
    bits as the scalar paths.
- `AccurateMath.cosTurns/sinTurns(e, d)`: cos and sin of `2π·e/d`, with the angle reduced exactly in integers. The
  symmetries are exact (odd multiples of π/4 give the correctly rounded √½ for both). The FFT twiddles now use
  them.

### Added (projection queries)
- `DepthRange` forms of the projection queries inherited from JOML, which assumed OpenGL's [-1, 1] depth:
  - `Matrix4f/d`:
    - `perspectiveNear`/`perspectiveFar`;
    - `frustumPlane`, `frustumCorner`, `frustumAabb`;
    - `orthoCrop`, `projectedGridRange`, `perspectiveFrustumSlice`;
    - the scalar `project`, `unproject` and `unprojectInv`;
    - `unprojectRay` and `unprojectInvRay`. Their origin lies on the near plane, and for an infinite reverse-Z
      projection their direction points forward.
  - `FrustumIntersection.set` and `FrustumPlanes.set`, which now also express reverse-Z.
- `Matrix3x2f/d.scaleAroundLocal(sx, sy, ox, oy)`, an in-place 2D form.

  For a `zZeroToOne` projection the OpenGL forms are wrong:
  - `perspectiveNear` returns about half the near distance.
  - Near corners are misplaced.
  - Culling keeps points down to half the near distance.
  - `unproject` of a Vulkan depth value of 0 misses the near plane.

  The OpenGL forms are unchanged and documented as such. Each new form gives the same physical frustum for
  [-1, 1], [0, 1] and reverse-Z.

### Fixed (second audit of the JOML-inherited code)
Each fix has a regression test in `MatrixAudit2Test`, `VectorQuatAudit2Test` or `GeometryAudit2Test`.

- **Matrix property flags.** Wrong flags sent later `invert()`, `mul()` or `transformProject()` calls down fast paths
  that gave wrong results, off by up to 19 in the tests:
  - `translationRotate` never wrote the 4th row, so a matrix that had held a projection kept `m23 = -1` while it
    was flagged affine.
  - `Matrix4d.m13(double)` tested `m03`, so it kept the affine and translation flags.
  - `shadow` kept `PROPERTY_AFFINE`; a shadow projection is projective.
  - `mul` with 16 scalars flagged the result affine whatever the right matrix was.
  - `lerp` kept `PROPERTY_ORTHONORMAL`; element-wise interpolation of rotations is not a rotation. This affected
    `Matrix4f/d` and `Matrix4x3f/d`.
  - `transpose3x3(dest)` gave `dest` the flags of `this`, although `dest` keeps its own 4th row and column.
  - `perspective`, `perspectiveLH`, `perspectiveRect` and `perspectiveOffCenter` applied to a perspective matrix
    kept `PROPERTY_PERSPECTIVE`.
  - Matrices built from, or rotated by, a **non-unit quaternion** were flagged orthonormal, although they hold
    `|q|² R`, so `invert()` transposed them. The flag is now set only for unit quaternions, to within the precision
    of their components.
- **Projection builders:**
  - `setFromIntrinsic` had the signs of `m20`, `m21`, `m22` and `m32` reversed. The optical axis landed on pixel
    `(W - u0, H - v0)`, and depth ran backwards. It now follows the cited construction, `glOrtho(0, W, 0, H, n, f) · K`,
    and is flagged perspective only without skew or principal-point offset.
  - `frustumLH`, `setFrustumLH` and `perspectiveOffCenterFovLH` used the right-handed signs for off-centre frustums.
    A left-handed frustum is now exactly the right-handed one with z flipped.
  - Left-handed projections with `zNear = +∞` copied the right-handed depth limit, which clipped almost everything
    and reversed depth.
  - `rotationTowardsXY`/`rotateTowardsXY` sent +X to `(dirY, dirX)`, contrary to their documentation. `rotateZ`,
    which is built on them, is unchanged.
- **Vectors:**
  - `smoothStep` (Vector3/4) added a spurious `x·t` term. It now returns `x + (v − x)(3t² − 2t³)`, exactly at both
    ends.
  - `hermite` (Vector3/4) used the point where the tangent belongs. At t = 1 it returned (3, −3, 0) for the
    endpoint (2, 0, 0).
  - `orthogonalize`/`orthogonalizeUnit` (Vector3) ignored `this` and returned an arbitrary perpendicular of `v`.
    They now perform the documented Gram–Schmidt step.
  - Vector `lerp` (all six classes) missed `other` at t = 1 for large magnitudes. `Math.lerp` was fixed in 2.4.0,
    and the vectors now use the same exact form.
  - `round` turned NaN into 0 and saturated beyond the int/long range. It now returns NaN, infinities and
    already-integral values unchanged.
  - `rotateAxis` assumed a unit axis without saying so; a non-unit axis is now normalized.
  - `angleSigned` overflowed or underflowed at extreme magnitudes, and a non-unit normal distorted the angle.
  - `distance` overflowed and underflowed. Only `length` had been fixed in 2.4.0.
- **Quaternions:**
  - `positiveX/Y/Z` and `transformPositiveX/Y/Z` were wrong for non-unit quaternions.
  - `normalize`, `fromAxisAngleRad` and `rotationAxis` gave NaN, ∞ or 0 at extreme magnitudes.
  - `lookAlong` lost orthogonality as `up` approached the direction, the defect 2.4.0 fixed in the matrices.
  - `Quaterniond.integrate` truncated its small-angle series at 1e-8; it now truncates at 1e-18.
- **`Interpolation*.dFdxLinear`/`dFdyLinear`:** these added and then subtracted `f2`, which cancelled. They now use
  the direct form, exact to rounding.
- **`Math.invsqrt`:** it rounded twice, with errors up to 1.47 ulp in float and 1.45 ulp in double. Float now
  rounds once from double; double adds one Newton step on the exactly evaluated residual, which makes it correctly
  rounded in practice. This changes the last bits of the many results that normalize through it.
- **Geometry:**
  - `findClosestPointOnTriangle` (2D and 3D, both twins) rebuilt the point from dot-product determinants that
    cancel on slivers. A point inside a sliver triangle came back 1.2 units away in float, so `intersectSphereTriangle`
    missed. Now the face region is decided by an exact inside test, and the point is `p` (2D) or its projection
    (3D); otherwise it is the nearest clamped edge projection.
  - `FrustumIntersection`, `FrustumPlanes` and `frustumPlane(…, DepthRange)` normalized the far plane of an
    infinite reverse-Z projection, which is the plane at infinity with a zero normal. The result was NaN, and every
    test culled. That plane is now stored as `(0, 0, 0, ±1)`.
  - `RayAabIntersection`:
    - Its sign test classified `±Float.MIN_VALUE` backwards.
    - Its slopes overflowed for tiny direction components, and a zero direction never hit.
    - Rays whose slopes are not finite now use the slab test.
  - `FrustumIntersection` built with `allowTestSpheres = false` gave wrong sphere and distance answers, because its
    planes are unnormalized. Those queries now scale by the plane lengths, computed once.
  - `testRayAab`, `intersectRayAab`, `testRayAar` and `intersectRayAar` reported NaN rays as hits.
  - `GeometryUtils`:
    - `tangentBitangent` corrupted the bitangent when `destTangent` aliased an input.
    - `normal`, `tangent` and `bitangent` lost up to 0.086 rad on thin triangles in float. They now compute in
      double, and `normal` has double overloads.
  - `PolygonsIntersection` accepted invalid polygon starts: `0` merged everything, and a start at the last vertex
    threw. Starts are now validated, and `testPoint` documents its even-odd rule over all polygons, which is how
    holes work.
  - Float `intersectRayLineSegment`, `intersectPolygonRay` and `intersectLineLine` compute in double. Errors were up
    to 200 ulp.
  - The masked `intersectAab` forms are documented: `INSIDE` always refers to all six planes. Masked planes are
    skipped only for culling; this is conservative and unchanged.
- **Matrix precision at extreme scales:**
  - `getScale` and `normalize3x3` (`Matrix3/4/4x3`, both twins) used plain square roots. A column of length 2e19
    gave ∞ in float, and 1e-162 gave 0 in double. They now use the overflow-safe norm.
  - Float `Matrix4f.invert`/`invertAffine`, `Matrix3f.invert` and `Matrix4x3f.invert` gave ∞ or NaN for
    well-conditioned matrices of extreme scale, because the float determinant underflowed below about 1e-15 per
    axis. They now compute in double and round each element once, which is also more precise.
- **Fast-math mode** (`-Djoml.fastmath`, opt-in):
  - The polynomial `sin`/`cos` flipped sign beyond about 6.7e9 rad, and `sin(1e19)` was 0. The lookup table returned
    values like `sin(1e6) = 176395.95`.
  - `cos(float)` shifted by π/2 in float, so for \|x\| ≥ 2²⁵ it returned the sine.
  - `atan2` gave NaN for zeros and infinities, and lost the sign of zero.
  - The approximations are now used only within their accurate range, and everything else takes the accurate path.
    The error bounds are documented in `docs/determinism.md`: polynomial sin 1e-10, cos 2e-10; lookup 4e-5 below
    256 rad; atan2 2.1e-4 rad.
- **Edge cases:**
  - `Math.min`/`max` and vector `min`/`max` gave results that depended on the argument order for NaN and −0. They now
    follow `java.lang.Math`: NaN propagates, and −0 < +0.
  - `SpiralSampling` with a single sample returned NaN; the sample now sits at the centre.
  - `Convolution.gaussianKernel` with σ = 0 returned NaN everywhere; it now gives the unit impulse.
  - `BestCandidateSampling` with zero candidates recursed until `StackOverflowError` (Disk) or produced NaN samples
    (Sphere). It now rejects counts below 1.
  - `BestCandidateSampling.Disk/Quad.generate(FloatBuffer)` wrote 2D samples at stride 3. That overran correctly
    sized buffers and left gaps.
- **Documentation:**
  - `obliqueZ` documented `y' = y + a·z`; the code correctly uses `b`.
  - Rotation extraction from matrices (`getUnnormalizedRotation`, `getNormalizedRotation`, quaternion
    `setFromUnnormalized`/`setFromNormalized`, `AxisAngle.set(Matrix…)`) is documented as requiring a rotation. A
    mirror or a shear gives a meaningless non-unit result; `linalg.Polar3d` splits a general matrix into rotation and
    stretch.
  - `getScale` is documented as returning unsigned column lengths.
  - The triangle interpolators are documented as returning NaN for degenerate triangles.

### Fixed
- `FFT1d`/`FFT1f` (and `Convolutiond/f`, which use them) generated twiddle factors by recurrence. The error grew
  with the size: relative leakage of 1.1e-13 at 2^14 and 7.8e-12 at 2^20. They now read an exactly reduced table,
  shared and immutable: at most 1.2e-16 at every size measured. The API, in-place operation and no-allocation
  behaviour are unchanged.
- `Matrix4f/d.perspectiveFrustumSlice` scaled `m00` and `m11` by `near / oldNear`, which changed the field of view:
  slicing a 1.1 rad projection to [1, 10] made it about ten times narrower. A slice now keeps the field of view,
  aspect and off-centre terms, and changes only the depth terms. This is an inherited JOML defect.

### Deprecated
- `Matrix3x2f/d.scaleAroundLocal(sx, sy, sz, ox, oy, oz)`, a 3D leftover that ignores `sz` and `oz`.

### Tests
- The twin-parity, determinism and aliasing harnesses now drive methods with enum parameters, which they used to
  skip.

## [2.4.0] - 2026-11-22
New in this release:
- **Inverse kinematics and pose kernels:** new package `ik`, and `animation.PoseKernels`.
- **A precision and correctness audit of the code inherited from JOML:** about 40 fixes. The fork rationale is in
  `docs/joml-divergence.md`.
- **Physics phase-1 kernels** for DynamisCollision and DynamisPhysics:
  - `geometry.Contacts`;
  - exact segment–box closest points;
  - tight bounds;
  - `physics.MassProperties`, `physics.Pacejka` and exact spring steps;
  - double twins for the whole `physics` package.
- **GPU data contracts:** `gpu.ShaderLayout` (checked against glslang), `gpu.PackedFormats`, `gpu.MeshletCones` and
  exact sphere bounds.
- **First benchmarks** of the 2.3.0 and 2.4.0 kernels.

No public API was removed or changed. Results change where the audit fixed them; see "Fixed" and "Changed", and
"Deprecated" for the misnamed spring helpers.

### Added
- IK kernels, new package `ik`, float and double, allocation-free. Animis keeps rig and solve policy; see
  `docs/architecture/math-gpu-ownership.md`.
  - `TwoBoneIkf/d`:
    - Analytic solve with a pole vector and a stretch limit, using a cancellation-free law of cosines.
    - Returns the new positions and the two world-space delta rotations.
    - Reachable targets are hit to 1e-12, and bone lengths are kept exactly.
  - `Fabrikf/d`: FABRIK over flat position chains, with optional per-joint `[min, max]` bend limits (Animis's
    representation).
  - `Ccdf/d`: CCD with a per-step angle cap. Each bone's accumulated world rotation reproduces its final vector, twist
    included.
  - `DampedLeastSquaresf/d`: the J Jᵀ of a ball-joint chain is 3×3, so each step is one Cholesky solve. The step is
    capped uniformly, preserving its direction; capping joints one by one cycled near singular poses.
  - `JointLimitsf/d`: swing-twist elliptical cone with a twist range, a circular cone, and a hinge.
  - `Aimf/d`: aim and look-at with roll controlled by world up, via a Shepperd matrix-to-quaternion conversion.
- Pose kernels: `animation.PoseKernelsf/d`, allocation-free, over flat per-joint arrays in Animis's `PoseBuffer` layout.
  They replace the four forward-kinematics copies and the slerp/quatMul/normalize copies listed in
  `Animis/docs/vectrix-migration.md`.
  - Forward kinematics and its exact inverse over parent indices. Both can run in place, and the result matches matrix
    composition to 1e-11.
  - Quaternion array operations: multiply, normalize, and shortest-arc slerp/nlerp.
  - Masked and N-way weighted blends.
  - Additive layers. Applying a layer at weight 1 restores the pose it was made from.

- Physics phase-1 kernels, for DynamisCollision and DynamisPhysics to adopt in place of their own copies. All are
  allocation-free, in float and double. The whole `physics` package now has double twins (`*d`), since Collision
  computes in double.
  - **`geometry.Contactsf/d`**: contact kernels returning signed depth, a unit normal from the first volume to the
    second, and a contact point.
    - Sphere–sphere, with a solid overlap test that includes containment.
    - AABB–AABB, using the exact minimum translation, not the axis of least overlap.
    - Sphere–AABB, through the nearest face when the center is inside.
    - Capsule–AABB. When the segment is inside the box, its depth comes from an exact separating-axis test.
    - The nearest box face to a point.
  - **`ClosestPointsf/d`**:
    - `closestPointAab` and `distanceSquaredPointAab`.
    - `segmentAab`, the exact closest points between a segment and a box: piecewise-quadratic minimization with no
      iteration. It replaces Collision's 48-step ternary search.
  - **`BoundingVolumesf/d`**: tight bounds of an oriented box, a capsule and a cylinder (cancellation-free disc
    extents), and an allocation-free bound of a transformed point array.
  - **`GeometryUtils.perpendicular`**: double overloads.
  - **`physics.MassPropertiesf/d`**:
    - primitive volumes;
    - parallel-axis shift;
    - `R I Rᵀ`;
    - combining two bodies;
    - exact polyhedral mass properties (Eberly), integrated about the mean vertex so distant meshes keep their
      precision.
  - **`physics.Pacejkaf/d`**: the magic formula, with and without shifts; a signed slip ratio, correct in reverse;
    an `atan2` slip angle.
  - **`SpringDamperf/d`**:
    - `dampedStep`, exact for underdamped, critical and overdamped springs, stable for stiff springs at any step;
    - `criticalDamping(k, m) = 2√(km)` (the shortcut `2√k` is critical only for unit mass);
    - `dampingForRatio`, `stiffnessForFrequency`;
    - `suspensionForceFromLength` / `suspensionForceFromCompression`.

- GPU data contracts (X1, X5), checked against the compiler and the Vulkan definitions:
  - **`gpu.ShaderLayout`**: an interface-block layout calculator for std140, std430 and scalar layouts, covering
    scalars, vectors, matrices (column- or row-major), arrays (multidimensional too) and nested structs.
    - It gives member offsets, array and matrix strides, and the block size. Paths such as `lights[3].color` work.
    - It emits the block's GLSL declaration, so writers and shaders share one definition.
    - It matches glslang on 450 random blocks, every member checked. The glslang data is recorded by
      `scripts/gen-shader-layout-golden.py`, so CI needs no tools.
  - **`gpu.PackedFormats`**, bit-exact to Vulkan:
    - `A2B10G10R10` unorm/snorm.
    - RGB9E5 shared exponent: the specification's algorithm, bit-equal to a direct transcription of it.
    - R11G11B10F unsigned floats, round to nearest-even, with NaN, Inf and negative inputs handled.
    - sRGB8 from the exact transfer function, with a decode table.
    - QTangent frames whose bitangent sign survives quantization.
  - **`gpu.MeshletCones`**:
    - the tightest normal cone of a cluster (from the minimal enclosing ball of its normals);
    - an exact back-face test for a cluster's bounding sphere, `(d·a) cos α − |d × a| sin α > r`, which never culls
      a visible triangle;
    - conservative 32-bit quantization.
  - **`BoundingVolumesf/d.transformSphere`**: sphere bounds under an affine transform, scaled by the exact largest
    singular value (Jacobi, rounded up). The largest-column-length shortcut is too small under shear.
  - **`FrustumPlanes.getVec4Array`**: the `vec4 planes[6]` layout shaders use.

### Fixed (precision and correctness audit of the JOML-inherited core)
The inherited `core` and `geometry` code was checked against independent references: BigDecimal, direct evaluation,
exact identities, brute force and separating-axis references. Almost every finding was shared by the float and double
twins, so twin parity could not see it. `PrecisionAuditTest` and `GeometryAuditTest` cover each fix.

- Scalar math:
  - `Math.cosFromSin`: it computed `sqrt(1 − sin²)` everywhere. Near ±90° that lost up to 1.5e-8 in double (all
    digits at 1e-8 from π/2) and 2.4e-4 in float, and the sign was wrong beyond about 1.35e10 rad. Every rotation
    builder uses it. It now evaluates `cos` where `|sin| > 0.707`, and the identity elsewhere, where it is exact.
  - `Math.lerp` and `biLerp`/`triLerp` did not return `b` at `t = 1`; for example `lerp(1e20, 1, 1)` gave 0.
- Axis-angle: `AxisAngle4d/4f.set(Quaternion*)`, `set(Matrix*)` and the constructors snapped angles below about 1e-4
  to 0 and angles near π to exactly π, and lost precision through `acos`. They now use `atan2` forms, taking the axis
  near 180° from the symmetric part. Tiny rotations are no longer reported as "no rotation".
- Quaternions:
  - `rotationTo`/`rotateTo` near antiparallel returned a non-unit quaternion that ignored the target. Near
    antiparallel they now use an axis-angle form with the axis re-orthogonalized, exact to 1e-16.
  - `slerp`'s near-identity fallback left results off unit length by up to 2.5e-7; it now uses a
    cancellation-free angle.
  - `nlerpIterative` ignored `alpha` for small arcs, and the double version normalized in float.
  - `getEulerAngles*` used `asin`, which lost precision near gimbal lock. The outer angle now comes from the
    residual rotation, so the angles rebuild the rotation exactly, also at gimbal lock.
- Vectors:
  - `angle` used `acos`, losing everything below about 1e-8 rad; it now uses `atan2(|a × b|, a · b)`.
  - `length`, `normalize` and `angleCos` overflowed or underflowed, e.g. `(1e200, 1e200, 0)` had length ∞ and
    `(1e-170, 0, 0)` normalized to NaN. Double vectors now scale by a power of two outside the safe range.
  - Float vectors accumulate in double, so they are correctly rounded and their last bits change.
- Matrices:
  - `affineSpan` had an operator-precedence error: it was wrong for any scaled matrix.
  - `getEulerAngles*` could return NaN (rounding pushed `|m20|` above 1), lost precision near gimbal lock, and
    ignored scale. They now go through the quaternion route.
  - `normal()` on the orthonormal path kept the translation.
  - `billboardSpherical` lost precision as the target approached directly behind, and was NaN exactly behind.
  - The `lookAt`/`lookAlong` family lost orthogonality as `up` approached the view direction (6.6e-9 at 1e-8 rad)
    while flagging the result orthonormal. It now applies one Gram-Schmidt step: 4.4e-16 at every angle.
  - `perspectiveFov` used `acos`, losing 3.7e-4 relative at a 1e-6 rad field of view.
  - `invertPerspective`/`invertPerspectiveView` rounded twice.
  - The infinite-projection epsilon in `Matrix4d` was the float literal `1E-6f`.
- Geometry, in `Intersectiond/f` unless noted:
  - `testObOb` built its rotation matrix transposed: about 3 % wrong answers on random rotated boxes.
  - `PolygonsIntersection` left each polygon's first vertex out of its bounds.
  - `intersectPlaneSphere` and `intersectLineCircle` moved the circle center the wrong way.
  - `findClosestPointOnPlane` and the swept-sphere-versus-plane tests had the plane constant's sign reversed.
  - `intersectCircleCircle` was missing a factor of 2.
  - `testCircleCircle` treated its documented squared radii as radii.
  - `testSphereSphere` returned NaN-false for concentric spheres. Its surface-intersection meaning is now
    documented.
  - `findClosestPointsLineSegments` returned a dot product, not the squared distance, when both segments were
    points. It also treated short segments as points through an absolute threshold, now relative.
  - Ray-sphere and ray-circle tests cancelled catastrophically for distant spheres: thousands of wrong answers per
    10k rays. The perpendicular distance and both roots are now computed stably, and tangent rays hit.
  - The ray-box slab tests missed flat boxes.
  - `intersectRayPlane` returned Infinity for parallel rays.
  - `intersectRayTriangle(Front)` returned negative distances for triangles behind the ray.
  - The ray-triangle tests now use Woop et al.'s watertight algorithm. Möller-Trumbore let about 20 % of rays aimed
    at a shared edge miss both triangles.
  - `intersectSweptSphereTriangle` is rewritten as a first-contact computation, two-sided:
    - It used to report face hits at negative times for spheres moving away.
    - It used the exit time and the wrong side for spheres approaching from behind.
    - It reported no hit for spheres moving parallel to the plane, even when they slid into an edge.
    - The edge 2–0 test compared against the wrong time.
    - It returned the exit time for spheres that started overlapping a vertex.
    - It divided by zero when moving along an edge.

    A sphere already touching at t = 0 now reports time 0 and the triangle's closest point. Vertex and edge times
    come from product-form quadratics that do not cancel, and they match a bisection reference to 1e-9.
  - `testPointInTriangle` and the 2D `testPointTriangle` are closed triangles. The 3D test used to include two edges
    but not the third. The 2D test included edges for one winding only; it now uses exact orientation predicates.
  - `testMovingCircleCircle` returned false for circles that already overlapped while moving apart.

### Changed
- `GpuTransformLayout` writers always write little-endian bytes, the order GPUs read. They used the buffer's order,
  so a default (big-endian) `ByteBuffer` received byte-swapped values. The float TRS layout is documented as a
  scalar-layout struct; it is not std430.
- `PackedNorm` decodes exactly (`c / (2^b − 1)`); it multiplied by a rounded reciprocal, one ulp off for some codes.
  Encoding forms the product in double.
- `SpringDamperf.criticallyDamped` now evaluates `e^(−ω dt)` exactly. It used a cubic approximation, 0.1 % off at
  `ω dt = 1`.
- `Intersection*.findClosestPointOnLineSegment` returns the point `a` for a zero-length segment. It used to return NaN.

- `core.Math.clamp` documents its argument order, `(min, max, value)` (JOML's), which is the opposite of the JDK's
  `java.lang.Math.clamp(value, min, max)`. Called in the JDK order it compiles and silently drops the upper bound.

### Deprecated
- `SpringDamperf.overdamped`: it is one semi-implicit Euler step, not an overdamped solution. Use `dampedStep`.
- `SpringDamperf.suspensionForce`: its parameters were misnamed. It computes `k (rest − length) − c · lengthRate`, so
  passing compression gave inverted forces. Use `suspensionForceFromLength` or `suspensionForceFromCompression`.

- `intersectSweptSphereTriangle` ignores its `epsilon` (parallel motion is exact) and works from either side; the
  winding no longer matters.
- The `epsilon` of the ray-triangle tests is now the minimum hit distance along the ray. It used to be a tolerance
  on the determinant; parallel rays are now handled exactly.
- Results change in their last bits for rotation builders (through `cosFromSin`) and for float vector lengths and
  normalization. About 1000 determinism-golden entries were re-recorded; every changed method traces to a fix above.

## [2.3.0] - 2026-11-22
New in this release: rendering math II (spherical functions, camera, atmosphere: review Part II §15.3, §15.5,
§15.6), animation math (§18, without IK), large-world precision (§12), and spectral colour from the official CIE
tables with Jakob-Hanika upsampling. New packages: `spherical`, `camera`, `atmosphere`, `animation`, `world`.
Fixes the double-precision SH constants and dual quaternion normalization, and deprecates the single-precision
`renderingmath` atmosphere builder. No breaking changes from 2.2.0. Licensing: `color.CieData` carries the CIE's
1 nm tables under CC BY-SA 4.0 (see `NOTICE`); everything else remains MIT.

### Added
- `sh.ShRotation` (review Part II §15.3): rotation of L2 and L3 RGB coefficient sets, float and double, by
  projection onto fixed sample directions (exact for the `ShBasis` conventions, no Wigner recurrences to get wrong).
  `rotationMatrixL2/L3` build the per-band matrices once for many probes, `applyL2/L3` and `rotateL2/L3` allocate
  nothing. `ShRotationTest` checks g(R u) = f(u), band orthogonality, composition and the band-1 block.
- More spherical functions (§15.3): `ShWindowing` (Hann and Lanczos deringing windows, Sloan 2017), `ShProduct`
  (products of L2/L3 functions, RGB with RGB or with a scalar function such as visibility, through Gaunt
  coefficients computed exactly at class load), `ShL1` (linear and Hazel non-linear L1 irradiance). New package
  `spherical`: `SphericalGaussiansf/d` (evaluation, integral, product, inner product, Hill's fitted irradiance and
  the cosine-lobe inner product, vMF-based fitting, anisotropic SGs) and `DirectionMappingsf/d` (octahedral and
  hemi-octahedral mappings, cubemap face selection in the OpenGL/Vulkan convention, exact texel solid angles).
  `ShExtrasTest` and `SphericalFunctionsTest` check them against exact quadrature.
- Camera math (review Part II §15.5), new package `camera`, float and double:
  - `DepthRange`: OpenGL, zero-to-one and reverse-Z depth conventions, taken by every function that produces or
    reads depth.
  - `Projectionsf/d`: perspectives exact in every range, including infinite far planes with no epsilon; near and far
    extraction; depth to view distance; the Vulkan clip correction; a full-row Y flip; exact NDC jitter for any
    projection; Halton TAA jitter; Lengyel oblique near-plane clipping.
  - `ShadowCascadesf/d`: practical splits, the closed-form minimal sphere of a frustum slice (rotation-invariant),
    and texel-snapped light matrices that always contain the sphere.
  - `ClusterGridf/d`: exponential slices whose index and boundaries agree exactly, cluster AABBs, a point's cluster,
    and sphere and spot-light tests with no false negatives.
  - `PhysicalCameraf/d`: field of view and focal length, thin-lens circle of confusion, hyperfocal distance and
    depth-of-field limits.

  `CameraMathTest` checks them against exact identities. For example, a float reverse-Z infinite depth keeps all
  18 000 half-metre steps between 1 and 10 km distinct, where standard [0, 1] depth keeps 1 511.

- Atmosphere math (review Part II §15.6), new package `atmosphere`; DynamisSky owns the rendering policy:
  - `PhaseFunctionsf/d`: Rayleigh, Henyey-Greenstein, Cornette-Shanks, Draine, and the Jendersie-d'Eon fog/cloud
    Mie fit for droplet diameters up to 50 µm (all four published ranges). Every phase function has an exact
    inverse-CDF sampler: the CDF of each sample returns ξ to 1e-13. HG uses an expanded form with no 1/g
    cancellation; Draine's closed form is polished by Newton steps on the analytic CDF, or on quadrature for
    |g| < 0.15, the crossover where the measured errors meet.
  - `AtmosphereModel`: an immutable record with Hillaire's Earth defaults, including ozone and Mie absorption.
  - `Atmosphere`, in double precision:
    - Ray-sphere distances and altitudes without cancellation, checked against BigDecimal at 1 mm above the ground.
    - Optical depth by adaptive Gauss-Kronrod, split at the ozone kinks. It matches the analytic vertical integrals
      to 1e-13, and its tolerance never asks for more than the integrand's own evaluation precision.
    - Bruneton-mapped transmittance LUTs.
    - Hillaire's multiple-scattering LUT.

- Animation math (review Part II §18, without IK, which stays with Animis):
  - New package `animation`, float and double:
    - `QuaternionCurvesf/d`: key unrolling, SQUAD over key arrays, spherical Bezier, the Kim-Kim-Shin C² cumulative
      B-spline (reproduces uniform rotation exactly), and the weighted Karcher (geodesic) mean of rotations.
    - `QuaternionSpringf/d`: Holden's exact critically damped rotation spring. One step of dt equals two steps of
      dt/2 to 1e-13, so it is frame-rate independent.
  - `DualQuatTransformf` gains:
    - Composition, inversion, translation and rotation extraction, and direction transforms.
    - Exact ScLERP: a closed-form screw power with a series where it cancels. It matches SE(3) exp/log to 1e-10 and
      the screw-subgroup identity to 1e-12.
    - Sign-aligned dual quaternion linear blending, and a scale-aware (two-phase) blend.
  - New `DualQuatTransformd` twin.
- Large-world precision (review Part II §12):
  - New package `world`:
    - `WorldPos`: a long cell of `2^k` metres plus a centred double offset. Splitting any double into cell and
      offset, and rebasing, is exact. Relative positions are rounded once (TwoSum), so they are correctly rounded
      at any distance. At 10⁹ m they are within 0.5 float ulp; naive float is 64 m off and naive double 1.2e-7 m.
    - `CameraRelative`: batch conversion of double, or cell-and-offset, transforms to camera-relative floats in the
      `AFFINE_3X4_ROW_MAJOR` upload layout, and a single-object `modelMatrix`.
  - Long vectors get a documented overflow policy: ordinary arithmetic wraps, and the new `addExact`, `subExact`
    and `mulExact` throw. They also get `floorDiv` and `floorMod` for cell arithmetic.
  - Double-precision twins: `Transformd`, `RigidTransformd`, `Affine4d`, `soa.TransformSoAd` and
    `soa.DualQuatSoAd`, plus `DualQuatTransformd.setFromRigid`.
- Spectral colour and cloth (the X3 leftovers):
  - Official CIE data:
    - `CieObserver`: the CIE 1931 2° and 1964 10° observers from the CIE's 1 nm open-access tables, MD5-verified
      and stored verbatim. The 1964 z̄ is undefined from 560 nm and stored as 0, as in CIE 15.
    - `Spectra`: D65 at 1 nm, plus CIE summation for emission and reflectance. The computed D65 white,
      (0.950471, 1, 1.088829), matches the standard value in every published digit.
  - `SpectralUpsampling`: Jakob-Hanika RGB-to-spectrum fitting by exact Newton solve instead of precomputed
    tables, about 50 µs per colour. It fits every sRGB and Display P3 colour exactly; for Rec. 2020 colours that no
    reflectance can produce, it reports failure and returns the closest spectrum.
  - `DfgLutBuilder.integrateCloth` / `buildCloth`: Filament's Charlie-sheen DFG channel. It agrees with an
    independent light-space quadrature to 0.2 %.
  - Licensing: the CIE tables are CC BY-SA 4.0. They live only in `color.CieData`, which carries that licence and
    is attributed in `NOTICE`. The rest of Vectrix stays MIT. Both jars now ship `META-INF/LICENSE` and
    `META-INF/NOTICE`.
### Deprecated
- `renderingmath.TransmittanceLutBuilder` and `AtmosphereParams`. They work in single precision, their ground
  intersection cancels near the horizon, and they have no ozone or Mie absorption. Use `atmosphere.Atmosphere`.

### Fixed
- `DualQuatTransformf.setFromRigid` built the dual part from the unnormalized rotation, which gave a wrong
  translation for non-unit input. `normalize` now makes the dual quaternion exactly unit, projecting out the dual
  part's component along the real part; before, it only divided by an approximate norm.
- The double-precision `ShBasis` constants had only 10 significant digits (float-grade accuracy, about 1e-10
  relative); they now carry full double precision. Double SH projection and evaluation results change in the last
  digits; the float path is unchanged.

## [2.2.0] - 2026-11-22
New in this release: the `linalg` package (review Part II §16) and the geometry functions of §17 (exact predicates,
spatial codes, bounding volumes, closest points, mesh normals and tangents), plus a fix to
`Intersection*.findClosestPointsLineSegmentTriangle` for segments that pierce the triangle. No breaking changes
from 2.1.0.

### Added
- Geometry functions (review Part II §17, as functions: vectrix adds no shape types, which DynamisCollision owns):
  `RobustPredicates` (exact `orient2d`, `orient3d`, `incircle`, `insphere`: double fast path within Shewchuk's error
  bounds, exact fallback), `SpatialCodes` (2D/3D Morton codes, 32/64-bit; 2D and 3D Hilbert curves),
  `BoundingVolumesf/d` (Welzl minimal sphere, PCA OBB, minimal-area rectangle by convex hull and calipers,
  approximate minimal OBB, 6/14/18/26-DOPs), `ClosestPointsf/d` (point to OBB, triangle to triangle, segment to
  triangle, ray against capsule), and `MeshMath` vertex normals (area- or angle-weighted), MikkTSpace-style vertex
  tangents and triangle quality metrics. `GeometryFunctionsTest` checks the predicates against exact BigDecimal
  determinants (including a grid where plain doubles get over 1000 signs wrong), Hilbert continuity, bounding volumes
  against brute force and the Badoiu-Clarkson approximation, distances against sampling, and ray-capsule against
  sphere tracing.

### Fixed
- `Intersectionf/d.findClosestPointsLineSegmentTriangle` reported a positive distance for a segment that pierces
  the triangle (it compared edges and end points only; inherited from JOML). It now returns 0 and the crossing point.
- `linalg` package (review Part II §16): symmetric 3×3 eigen-decomposition (cyclic Jacobi, sorted, right-handed
  eigenvectors), signed 3×3 SVD (McAdams et al.: rotations U and V, reflection carried by σ₂), polar decomposition,
  Householder QR (3×3, 4×4), Cholesky, LDLᵀ and pivoted LU solvers (3×3, 4×4), 2-norm and 1-norm condition numbers,
  SO(3)/SE(3) exponential and logarithm maps (exact up to π), and least squares by QR with plane and line fits.
  Float and double APIs share double-precision kernels with per-thread scratch, so the fixed-size operations do not
  allocate. `InertiaTensorf.principalAxes` uses the eigen-decomposition. `LinalgTest` checks reconstruction and
  structure on random, repeated-eigenvalue, rank-deficient and reflecting matrices, the polar rotation against
  Higham's iteration, solver residuals and failure detection, and the Lie maps against axis-angle rotations.

## [2.1.0] - 2026-11-22
New in this release: the `simd` classifier jar with the Vector API kernels, SIMD frustum culling by default, and
the rendering-math packages of the review's Part II X3: `brdf` (§15.1), Monte-Carlo sampling (§15.2) and the colour
pipeline (§15.4). No breaking changes from 2.0.0.

### Changed
- `CullingKernels.frustumCullAabbBatch` uses the Vector API kernel by default (with the `simd` artifact and
  `--add-modules jdk.incubator.vector`, FAST mode, 64 to 32768 boxes): 2.7× faster than scalar on AVX-512 x86 and
  13–21 % faster on arm64. It classifies every box exactly like scalar; the NaN-bounds difference that kept it
  opt-in was fixed in 2.0.0, and `SimdPathTest` now checks NaN, infinite, signed-zero and inverted bounds and
  corrupted planes. `-Dvectrix.culling.simd=false` keeps the scalar kernel.

### Added
- Colour pipeline (review Part II §15.4): `ColorGamut` and `ColorSpacesf/d` (sRGB, Display P3, Rec.2020, ACEScg,
  ACES2065-1, matrices derived from the primaries, Bradford or CAT16 adaptation), `TransferFunctionsf/d` (PQ, HLG,
  Rec.709, gamma 2.2), `PerceptualColorf/d` (OKLab/OKLCH, CIELAB/LCh, HSV/HSL, CIEDE2000, ΔE-OK), `Tonemapf/d` (ACES
  Hill and Narkowicz fits, AgX, Khronos PBR Neutral, Uchimura, Lottes, extended Reinhard, and DynamisLightEngine's
  exponential curve), `Exposuref/d` (EV100, saturation-based exposure, photometric units) and
  `ColorSciencef/d.planckRadiance`. `AccurateMath.cbrt`. `ColorTest` checks the published ACES matrices, Ottosson's
  OKLab table, Sharma et al.'s CIEDE2000 pairs, PQ/HLG reference points, the tonemappers' fixed points, bounds and
  monotonicity, and Wien's and Stefan-Boltzmann's laws. Not yet: tabulated 1 nm CIE data and Jakob-Hanika spectral
  upsampling, which need large coefficient tables.
- Sampling for Monte-Carlo rendering (review Part II §15.2): `Warpf/d` (concentric disk, cosine and uniform
  hemisphere, sphere and cone, each with its density; balance and power MIS heuristics), `GgxSamplingf/d` (GGX
  visible-normal sampling by Dupuy and Benyoub's spherical caps, isotropic and anisotropic, with half-vector and
  reflected-direction densities), `SphericalSamplingf/d` (uniform solid-angle sampling of spherical triangles, Arvo
  1995, and rectangles, Ureña et al. 2013, with their solid angles), `AliasTable` (Walker-Vose, O(1) sampling),
  `R2Sequence`, and Owen-scrambled Sobol (`SobolSequence.sobolOwen*`, Burley 2020), which keeps the (0, m, 2)-net
  structure. `SamplingTest` checks that every density integrates to 1, chi-square goodness of fit for every warp,
  uniformity of the area-light samples over their solid angle, the alias-table frequencies, and the sequences'
  stratification.
- `brdf` package (review Part II §15.1): GGX distribution (isotropic and anisotropic), Smith Λ/G1 and
  height-correlated G2 and visibility, Schlick Fresnel (F0/F90, RGB, roughness-aware), Charlie sheen with Neubelt
  visibility, Kelemen clearcoat visibility, Kulla-Conty energy compensation, Lambert/Burley/Oren-Nayar diffuse, and
  `DfgLutBuilder` for split-sum IBL tables (height-correlated Smith or the UE4 Schlick-GGX model; deterministic).
  `LightEngineCompatf/d` reproduce DynamisLightEngine's current shader BRDF exactly. `BrdfTest` checks normalisation,
  the weak white furnace, energy bounds and Kulla-Conty, reciprocity, the DFG estimator against quadrature, and the
  compatibility variants against the shader formulas.
- `simd` classifier artifact (review §14): the same library with the Vector API batch kernels compiled in, built and
  published with every release. It is not a preview build and needs no JVMCI flags. With
  `--add-modules jdk.incubator.vector` it runs the SIMD kernels; without it, the scalar ones (classpath or module path,
  checked). `mvn verify` runs the whole test suite a second time against it with the Vector API enabled, including the
  determinism golden, which it matches bit for bit.
- `scripts/bench-snapshot.sh`, the benchmark groups behind the `BENCHMARKS.md` snapshot, and a manually triggered
  `Benchmark Snapshot` workflow that runs it on a GitHub-hosted x86_64 or arm64 runner.
- `BENCHMARKS.md` x86_64 snapshot (Xeon Platinum 8573C, AVX-512): SIMD transform compose 3.1–5.9× faster than
  scalar and SIMD culling 2.7× faster, which default dispatch leaves unused because SIMD culling is opt-in. FFM is
  10–25 % slower than Unsafe in loops on x86, unlike on arm64.
- Benchmark-only gather-based SIMD LBS kernel (`GatherLbsKernel`): 1.6× faster than scalar LBS on x86 (AVX2) and
  6.8× slower on arm64, but slower than the default matrix-palette path on both, so not added to the library.
- Test fixtures (`ValueFixtures`) fill and compare primitive-array state, so array-backed types (`ShCoeffs*`,
  `FrustumPlanes`) are now driven by `TwinParityTest`, `AliasingTest` and the determinism golden. The golden gains 32
  entries, among them every `ShProjection` and `ShConvolution` method; nothing already in it changed.
- Coverage gate raised to line ≥ 79 %, branch ≥ 54 % (measured 79.4 % / 54.3 %).
- `FfmAccessBenchmark`: isolates the layers of an FFM transfer. It shows FFM's extra ~1 ns on a single transfer is
  a fixed per-call cost (segment creation from the buffer), not slower stores; see `BENCHMARKS.md`.

## [2.0.0] - 2026-11-22
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
