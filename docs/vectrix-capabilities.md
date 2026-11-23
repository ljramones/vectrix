# Vectrix Capabilities (Consumer-Oriented)

This document lists what downstream libraries can rely on today, grouped by use-case rather than package layout.

## Rotation & Orientation
- Quaternion interpolation: `slerp`, `nlerp`, weighted variants, weighted average.
  Location: `org.dynamisengine.vectrix.core.Quaternionf`, `org.dynamisengine.vectrix.core.Quaterniond`, interpolators.
- Quaternion curve primitives: `log`, `exp`, `squadControlPoint`, `squad`.
  Location: `org.dynamisengine.vectrix.core.Quaternionf`, `org.dynamisengine.vectrix.core.Quaterniond`.
- Swing-twist decomposition.
  Location: `org.dynamisengine.vectrix.core.Quaternionf`, `org.dynamisengine.vectrix.core.Quaterniond`.
- Angular velocity bridge utilities (derive/integrate).
  Location: `org.dynamisengine.vectrix.core.Quaternionf`, `org.dynamisengine.vectrix.core.Quaterniond`.
- Batch hemisphere consistency for keyframe preprocessing.
  Location: `org.dynamisengine.vectrix.core.Quaternionf`, `org.dynamisengine.vectrix.core.Quaterniond`.
- Transform interpolation (TRS-level blend).
  Location: `org.dynamisengine.vectrix.affine.Transformf`.

## Curves & Interpolation
- Curve families for scalar/vec2/vec3/vec4 in float/double:
  - Bezier
  - Hermite
  - Catmull-Rom
  - Uniform B-spline
  Location: `org.dynamisengine.vectrix.curve.scalar`, `org.dynamisengine.vectrix.curve.vec2`, `org.dynamisengine.vectrix.curve.vec3`, `org.dynamisengine.vectrix.curve.vec4`.
- First and second derivatives for curve types.
  Location: same curve packages above.
- SoA batch evaluation (offset/length variants).
  Location: curve package classes.
- Arc-length reparameterization for Bezier and Uniform B-spline.
  Location: `org.dynamisengine.vectrix.curve.CurveReparameterizer3f`, `org.dynamisengine.vectrix.curve.CurveReparameterizer3d`.
- Arc-length reparameterization for Hermite and Catmull-Rom.
  Location: `org.dynamisengine.vectrix.curve.CurveReparameterizer3f`, `org.dynamisengine.vectrix.curve.CurveReparameterizer3d`.
- Bilinear and bicubic interpolation utilities:
  - Catmull-Rom/Hermite cubic
  - B-spline cubic
  - Mitchell-Netravali cubic
  Location: `org.dynamisengine.vectrix.renderingmath.Interpolationf`, `org.dynamisengine.vectrix.renderingmath.Interpolationd`.

## Geometry & Spatial Math
- Core vector/matrix/quaternion primitives and transforms.
  Location: `org.dynamisengine.vectrix.core`, `org.dynamisengine.vectrix.affine`.
- Intersection/collision/frustum queries.
  Location: `org.dynamisengine.vectrix.geometry` (`Intersectionf/d`, `FrustumIntersection`, `RayAabIntersection`, etc.).
- Geometry kernels and mesh math helpers.
  Location: `org.dynamisengine.vectrix.geometry`.
- SoA containers for transforms/AABB and skinning-oriented data layouts.
  Location: `org.dynamisengine.vectrix.soa`.

## Rendering Math
- Spherical harmonics L2 RGB:
  - basis evaluation
  - sample projection
  - Lambert convolution
  - irradiance evaluation
  Location: `org.dynamisengine.vectrix.sh`.
- Spherical harmonics L3 RGB:
  - basis evaluation
  - sample projection
  - radiance evaluation
  - Lambert convolution helpers
  Location: `org.dynamisengine.vectrix.sh`.
- Preintegrated SSS:
  - sum-of-Gaussians profile definition
  - LUT builder (curvature × `NdotL`)
  Location: `org.dynamisengine.vectrix.renderingmath.SssProfile`, `org.dynamisengine.vectrix.renderingmath.SssLutBuilder`.
- Atmospheric transmittance:
  - atmosphere parameterization
  - LUT builder (height × zenith cosine)
  Location: `org.dynamisengine.vectrix.renderingmath.AtmosphereParams`, `org.dynamisengine.vectrix.renderingmath.TransmittanceLutBuilder`.
- Bent normal visibility cone math:
  - AO→cone angle
  - cone solid angle
  - cone intersection estimate
  - float + double parity
  Location: `org.dynamisengine.vectrix.renderingmath.BentNormalConef`, `org.dynamisengine.vectrix.renderingmath.BentNormalConed`.
- LTC evaluation math:
  - table bilinear sampling
  - direction transform
  - horizon clipping
  - rect/disc/tube form-factor helpers
  - clipping variants (polygon/rect)
  - float + double parity
  Location: `org.dynamisengine.vectrix.ltc`.

## GPU Utilities
- Quaternion compression and octahedral direction encoding.
  Location: `org.dynamisengine.vectrix.gpu.QuatCompression`, `org.dynamisengine.vectrix.gpu.OctaNormal`.
- Packed normalized formats and half-float helpers.
  Location: `org.dynamisengine.vectrix.gpu.PackedNorm`, `org.dynamisengine.vectrix.gpu.Half`.
- Vertex/transform layout and packing utilities.
  Location: `org.dynamisengine.vectrix.gpu`.

## Numerical Utilities
- RK4 integrators (allocation-free, caller-owned scratch, in-place safe).
  Location: `org.dynamisengine.vectrix.core.IntegratorRK4f`, `org.dynamisengine.vectrix.core.IntegratorRK4d`.
- ODE derivative callback interfaces.
  Location: `org.dynamisengine.vectrix.core.OdeDerivativef`, `org.dynamisengine.vectrix.core.OdeDerivatived`.
- Range/remap helpers:
  - `remap`, `remapClamped`, `inverseLerp`, `saturate`, `clamp`
  Location: `org.dynamisengine.vectrix.core.Rangef`, `org.dynamisengine.vectrix.core.Ranged`.
- Easing function utilities.
  Location: `org.dynamisengine.vectrix.easing.Easingf`, `org.dynamisengine.vectrix.easing.Easingd`.
- Hash/spatial hash utilities.
  Location: `org.dynamisengine.vectrix.hash`.

## Sampling
- Uniform, stratified, Poisson, best-candidate, spiral sampling.
  Location: `org.dynamisengine.vectrix.sampling`.
- Low-discrepancy sequences:
  - Halton (indexed, batch SoA)
  - Sobol (indexed, batch SoA)
  - Scrambled Sobol (seeded, per-dimension scramble, batch SoA)
  Location: `org.dynamisengine.vectrix.sampling.HaltonSequence`, `org.dynamisengine.vectrix.sampling.SobolSequence`.

## Signal Processing (FFT)
- Planned complex and real-input FFTs of any size (mixed radix 2/3/4/5, direct prime butterflies, Bluestein), 1D
  and 2D, double and float. They take caller scratch, don't allocate, and give the same bits from the scalar and
  Vector API paths. The error grows only with `log n`.
  Location: `fft.ComplexFftd/f`, `fft.RealFftd/f`, `fft.ComplexFft2d/f`, `fft.RealFft2d/f`.
- Streaming FIR filtering by direct or FFT overlap-add convolution.
  Location: `fft.BlockConvolverd/f`.
- The original in-place radix-2 API, now on exact twiddle tables.
  Location: `fft.FFT1f/d`, `fft.Convolutionf/d`, `fft.Complexf/d`.

## Signal Processing (DSP)
- Windows (Hann, Hamming, Blackman family, Kaiser, Tukey, Gaussian, Dolph–Chebyshev, sine), window gains.
  Location: `dsp.Windows`.
- FIR design by the window method; RBJ biquads and Butterworth cascades, with caller-held state.
  Location: `dsp.FirDesign`, `dsp.BiquadDesign`, `dsp.Biquad`.
- STFT with exact reconstruction for any windows; periodogram and Welch PSD; analytic signal; cross-correlation;
  Goertzel.
  Location: `dsp.Stftd/f`, `dsp.Spectra`, `dsp.Correlation`, `dsp.Goertzel`.
- DCT-II/III/IV, MDCT, and rational polyphase resampling.
  Location: `dsp.Dctd/f`, `dsp.Mdctd/f`, `dsp.Resampler`.

## Machine Learning Kernels
- BLAS level 1 (dot, sum, nrm2, axpy, ...) with one documented reduction order. Arrays and `MemorySegment`s.
  Location: `blas.Blasf/d`.
- GEMM and GEMV in which every element is exactly a dot product, so results are batch-invariant.
  Location: `blas.Gemmf/d`.
- Reproducible float transcendentals (exp, log, tanh, sigmoid, erf, erfc, the normal CDF) and double helpers
  (sin, cos, exp, log, pow).
  Location: `nn.NnMath`.
- Activations, softmax, layer and RMS norm, cross-entropy, attention, rotary embeddings, convolution and pooling,
  each with a backward pass.
  Location: `nn.Activations`, `nn.Softmax`, `nn.Norms`, `nn.Loss`, `nn.Attention`, `nn.Rope`, `nn.Conv2d`,
  `nn.Pool2d`.
- Philox random numbers and dropout; token sampling; AdamW and SGD; bf16, FP8 and int8 block formats.
  Location: `nn.Philox`, `nn.Sampler`, `nn.Optim`, `nn.Formats`.

## Optics & Material Math
- IOR utilities and Schlick `F0` helpers.
  Location: `org.dynamisengine.vectrix.optics.Iorf`, `org.dynamisengine.vectrix.optics.Iord`.
- Fresnel equations:
  - dielectric
  - conductor (complex IOR)
  Location: `org.dynamisengine.vectrix.optics.Fresnelf`, `org.dynamisengine.vectrix.optics.Fresneld`.
- Thin-film interference:
  - single-wavelength reflectance
  - tri-band RGB reflectance
  Location: `org.dynamisengine.vectrix.optics.ThinFilmf`, `org.dynamisengine.vectrix.optics.ThinFilmd`.
- Minimal spectral RGB wavelength constants.
  Location: `org.dynamisengine.vectrix.optics.SpectralRGBf`, `org.dynamisengine.vectrix.optics.SpectralRGBd`.
- Spectral sampling utilities (uniform/stratified wavelength sampling and PDF).
  Location: `org.dynamisengine.vectrix.optics.SpectralSamplingf`, `org.dynamisengine.vectrix.optics.SpectralSamplingd`.

## Color Science
- CIE 1931 XYZ observer-function approximations.
- XYZ ↔ linear sRGB conversion helpers.
  Location: `org.dynamisengine.vectrix.color.ColorSciencef`, `org.dynamisengine.vectrix.color.ColorScienced`.

## Infrastructure (FAST/STRICT, SIMD, Benchmarks)
- Math mode controls (`FAST`/`STRICT`) with shared config.
  Location: `org.dynamisengine.vectrix.core.MathMode`, `org.dynamisengine.vectrix.core.CoreConfig`, `org.dynamisengine.vectrix.experimental.KernelConfig`.
- SIMD support and scalar fallbacks.
  Location: `org.dynamisengine.vectrix.simd`, SIMD-aware kernels in `org.dynamisengine.vectrix.affine`, `org.dynamisengine.vectrix.geometry`, `org.dynamisengine.vectrix.soa`.
- SoA and batch kernels for hot paths (skinning, culling, transforms).
  Location: `org.dynamisengine.vectrix.soa`, `org.dynamisengine.vectrix.affine`, `org.dynamisengine.vectrix.geometry`.
- JMH benchmark harness and benchmark modules.
  Location: `src/bench/java`, `src/jmh/java`, `scripts/bench-run.sh`.

## Known Gaps / v2 Scope
- No known math-domain gaps against the current engine wish list.
- Future additions are expected to be quality/performance expansions (for example: specialized SIMD kernels, richer spectral workflows, and more advanced fitting/tooling), not missing foundational primitives.
