# Vectrix Capabilities (Consumer-Oriented)

This document lists what downstream libraries can rely on today, grouped by use-case rather than package layout.

## Rotation & Orientation
- Quaternion interpolation: `slerp`, `nlerp`, weighted variants, weighted average.
  Location: `org.vectrix.core.Quaternionf`, `org.vectrix.core.Quaterniond`, interpolators.
- Quaternion curve primitives: `log`, `exp`, `squadControlPoint`, `squad`.
  Location: `org.vectrix.core.Quaternionf`, `org.vectrix.core.Quaterniond`.
- Swing-twist decomposition.
  Location: `org.vectrix.core.Quaternionf`, `org.vectrix.core.Quaterniond`.
- Angular velocity bridge utilities (derive/integrate).
  Location: `org.vectrix.core.Quaternionf`, `org.vectrix.core.Quaterniond`.
- Batch hemisphere consistency for keyframe preprocessing.
  Location: `org.vectrix.core.Quaternionf`, `org.vectrix.core.Quaterniond`.
- Transform interpolation (TRS-level blend).
  Location: `org.vectrix.affine.Transformf`.

## Curves & Interpolation
- Curve families for scalar/vec2/vec3/vec4 in float/double:
  - Bezier
  - Hermite
  - Catmull-Rom
  - Uniform B-spline
  Location: `org.vectrix.curve.scalar`, `org.vectrix.curve.vec2`, `org.vectrix.curve.vec3`, `org.vectrix.curve.vec4`.
- First and second derivatives for curve types.
  Location: same curve packages above.
- SoA batch evaluation (offset/length variants).
  Location: curve package classes.
- Arc-length reparameterization for Bezier and Uniform B-spline.
  Location: `org.vectrix.curve.CurveReparameterizer3f`, `org.vectrix.curve.CurveReparameterizer3d`.
- Arc-length reparameterization for Hermite and Catmull-Rom.
  Location: `org.vectrix.curve.CurveReparameterizer3f`, `org.vectrix.curve.CurveReparameterizer3d`.
- Bilinear and bicubic interpolation utilities:
  - Catmull-Rom/Hermite cubic
  - B-spline cubic
  - Mitchell-Netravali cubic
  Location: `org.vectrix.renderingmath.Interpolationf`, `org.vectrix.renderingmath.Interpolationd`.

## Geometry & Spatial Math
- Core vector/matrix/quaternion primitives and transforms.
  Location: `org.vectrix.core`, `org.vectrix.affine`.
- Intersection/collision/frustum queries.
  Location: `org.vectrix.geometry` (`Intersectionf/d`, `FrustumIntersection`, `RayAabIntersection`, etc.).
- Geometry kernels and mesh math helpers.
  Location: `org.vectrix.geometry`.
- SoA containers for transforms/AABB and skinning-oriented data layouts.
  Location: `org.vectrix.soa`.

## Rendering Math
- Spherical harmonics L2 RGB:
  - basis evaluation
  - sample projection
  - Lambert convolution
  - irradiance evaluation
  Location: `org.vectrix.sh`.
- Spherical harmonics L3 RGB:
  - basis evaluation
  - sample projection
  - radiance evaluation
  - Lambert convolution helpers
  Location: `org.vectrix.sh`.
- Preintegrated SSS:
  - sum-of-Gaussians profile definition
  - LUT builder (curvature × `NdotL`)
  Location: `org.vectrix.renderingmath.SssProfile`, `org.vectrix.renderingmath.SssLutBuilder`.
- Atmospheric transmittance:
  - atmosphere parameterization
  - LUT builder (height × zenith cosine)
  Location: `org.vectrix.renderingmath.AtmosphereParams`, `org.vectrix.renderingmath.TransmittanceLutBuilder`.
- Bent normal visibility cone math:
  - AO→cone angle
  - cone solid angle
  - cone intersection estimate
  - float + double parity
  Location: `org.vectrix.renderingmath.BentNormalConef`, `org.vectrix.renderingmath.BentNormalConed`.
- LTC evaluation math:
  - table bilinear sampling
  - direction transform
  - horizon clipping
  - rect/disc/tube form-factor helpers
  - clipping variants (polygon/rect)
  - float + double parity
  Location: `org.vectrix.ltc`.

## GPU Utilities
- Quaternion compression and octahedral direction encoding.
  Location: `org.vectrix.gpu.QuatCompression`, `org.vectrix.gpu.OctaNormal`.
- Packed normalized formats and half-float helpers.
  Location: `org.vectrix.gpu.PackedNorm`, `org.vectrix.gpu.Half`.
- Vertex/transform layout and packing utilities.
  Location: `org.vectrix.gpu`.

## Numerical Utilities
- RK4 integrators (allocation-free, caller-owned scratch, in-place safe).
  Location: `org.vectrix.core.IntegratorRK4f`, `org.vectrix.core.IntegratorRK4d`.
- ODE derivative callback interfaces.
  Location: `org.vectrix.core.OdeDerivativef`, `org.vectrix.core.OdeDerivatived`.
- Range/remap helpers:
  - `remap`, `remapClamped`, `inverseLerp`, `saturate`, `clamp`
  Location: `org.vectrix.core.Rangef`, `org.vectrix.core.Ranged`.
- Easing function utilities.
  Location: `org.vectrix.easing.Easingf`, `org.vectrix.easing.Easingd`.
- Hash/spatial hash utilities.
  Location: `org.vectrix.hash`.

## Sampling
- Uniform, stratified, Poisson, best-candidate, spiral sampling.
  Location: `org.vectrix.sampling`.
- Low-discrepancy sequences:
  - Halton (indexed, batch SoA)
  - Sobol (indexed, batch SoA)
  - Scrambled Sobol (seeded, per-dimension scramble, batch SoA)
  Location: `org.vectrix.sampling.HaltonSequence`, `org.vectrix.sampling.SobolSequence`.

## Signal Processing (FFT)
- Complex value types (`float`/`double`).
  Location: `org.vectrix.fft.Complexf`, `org.vectrix.fft.Complexd`.
- In-place radix-2 FFT (forward unscaled, inverse scaled by `1/N`).
  Location: `org.vectrix.fft.FFT1f`, `org.vectrix.fft.FFT1d`.
- FFT-based convolution:
  - circular
  - linear (internal zero-padding)
  Location: `org.vectrix.fft.Convolutionf`, `org.vectrix.fft.Convolutiond`.

## Optics & Material Math
- IOR utilities and Schlick `F0` helpers.
  Location: `org.vectrix.optics.Iorf`, `org.vectrix.optics.Iord`.
- Fresnel equations:
  - dielectric
  - conductor (complex IOR)
  Location: `org.vectrix.optics.Fresnelf`, `org.vectrix.optics.Fresneld`.
- Thin-film interference:
  - single-wavelength reflectance
  - tri-band RGB reflectance
  Location: `org.vectrix.optics.ThinFilmf`, `org.vectrix.optics.ThinFilmd`.
- Minimal spectral RGB wavelength constants.
  Location: `org.vectrix.optics.SpectralRGBf`, `org.vectrix.optics.SpectralRGBd`.
- Spectral sampling utilities (uniform/stratified wavelength sampling and PDF).
  Location: `org.vectrix.optics.SpectralSamplingf`, `org.vectrix.optics.SpectralSamplingd`.

## Color Science
- CIE 1931 XYZ observer-function approximations.
- XYZ ↔ linear sRGB conversion helpers.
  Location: `org.vectrix.color.ColorSciencef`, `org.vectrix.color.ColorScienced`.

## Infrastructure (FAST/STRICT, SIMD, Benchmarks)
- Math mode controls (`FAST`/`STRICT`) with shared config.
  Location: `org.vectrix.core.MathMode`, `org.vectrix.core.CoreConfig`, `org.vectrix.experimental.KernelConfig`.
- SIMD support and scalar fallbacks.
  Location: `org.vectrix.simd`, SIMD-aware kernels in `org.vectrix.affine`, `org.vectrix.geometry`, `org.vectrix.soa`.
- SoA and batch kernels for hot paths (skinning, culling, transforms).
  Location: `org.vectrix.soa`, `org.vectrix.affine`, `org.vectrix.geometry`.
- JMH benchmark harness and benchmark modules.
  Location: `src/bench/java`, `src/jmh/java`, `scripts/bench-run.sh`.

## Known Gaps / v2 Scope
- No known math-domain gaps against the current engine wish list.
- Future additions are expected to be quality/performance expansions (for example: specialized SIMD kernels, richer spectral workflows, and more advanced fitting/tooling), not missing foundational primitives.
