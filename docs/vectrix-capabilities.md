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
- Complex value types (`float`/`double`).
  Location: `org.dynamisengine.vectrix.fft.Complexf`, `org.dynamisengine.vectrix.fft.Complexd`.
- In-place radix-2 FFT (forward unscaled, inverse scaled by `1/N`).
  Location: `org.dynamisengine.vectrix.fft.FFT1f`, `org.dynamisengine.vectrix.fft.FFT1d`.
- FFT-based convolution:
  - circular
  - linear (internal zero-padding)
  Location: `org.dynamisengine.vectrix.fft.Convolutionf`, `org.dynamisengine.vectrix.fft.Convolutiond`.

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
