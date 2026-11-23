# Known issues

Open defects and limitations. Fixed items move to the CHANGELOG and are deleted here. The 2.6.0 documentation pass
read every public method; 3.0.0 fixed what it found, except the items below.

Status: **open**, a fix not yet started. **By design**, deliberate behavior with the reason given, documented in the
Javadoc.

| Where | Issue | Status |
|---|---|---|
| `geometry/BoundingVolumesf.aabOfCapsule`, `aabOfObb`, `aabOfTransformedPoints` (and d) | Round to nearest, not outward, so a bound can be smaller than the shape by up to half an ulp. `transformSphere` and `aabOfCylinder` are already conservative | Open |
| `curve/vec2`, `vec3`, `vec4` batch evaluators (about 30 `evaluateBatch` methods) | No up-front array checks: a short array throws `ArrayIndexOutOfBoundsException` partway. `UniformBSpline1f`/`1d` check | Open |
| `nn/Formats.quantizeInt8` | A block whose largest magnitude is below about 127·2⁻¹⁴⁹ gets a scale that underflows to 0 and all-zero codes | Open |
| `dsp/Spectra`, `dsp/Wiener` | No float overloads. Keeping them bit-identical to the double path needs float-specific scratch sizes | Open |
| `dsp/ParticleFilter` | Could draw its random numbers with `Philox.uniformDouble` instead of the caller's uniforms | Open |
| `atmosphere/Atmosphere.multipleScatteringLut` | Recomputes its 256 × 128 sun-transmittance table on every call (in caller scratch, no allocation). A cache would be shared state | By design |
| `atmosphere/Atmosphere.transmittanceLut`, `curve/CurveReparameterizer3f` table builders (and d) | Allocate a few small temporaries per call; both are setup-time builders | By design |
| `affine/Transformf.compose` (and d) | A TRS cannot represent the shear that a non-uniform parent scale under rotation produces; the per-axis product is an approximation | By design |
| `color/ColorSpacesd.Adaptation` | The adaptation enum lives in the double class and both twins share it | By design |
| `dsp/AdaptiveFilter` | No float overloads: sample arguments are scalars (floats widen), and the state stays double like `Biquad` state | By design |
