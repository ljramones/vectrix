# Known issues

Open defects and limitations. Fixed items move to the CHANGELOG and are deleted here. The 2.6.0 documentation pass
read every public method, and 3.0.0 fixed everything it found. What remains are deliberate limitations.

Status: **open**, a fix not yet started (none at the moment). **By design**, deliberate behavior with the reason
given, documented in the Javadoc.

| Where | Issue | Status |
|---|---|---|
| `atmosphere/Atmosphere.multipleScatteringLut` | Recomputes its 256 × 128 sun-transmittance table on every call (in caller scratch, no allocation). A cache would be shared state | By design |
| `atmosphere/Atmosphere.transmittanceLut`, `curve/CurveReparameterizer3f` table builders (and d) | Allocate a few small temporaries per call; both are setup-time builders | By design |
| `affine/Transformf.compose` (and d) | A TRS cannot represent the shear that a non-uniform parent scale under rotation produces; the per-axis product is an approximation | By design |
| `color/ColorSpacesd.Adaptation` | The adaptation enum lives in the double class and both twins share it | By design |
| `dsp/AdaptiveFilter` | No float overloads: sample arguments are scalars (floats widen), and the state stays double like `Biquad` state | By design |
