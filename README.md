# Vectrix

Vectrix is a pure Java math kernel for real-time graphics, simulation, and engine workloads. It covers the full mathematical surface needed to build a production rendering engine, from core linear algebra through spherical harmonics, FFT-based convolution, physically based optics, and precomputed LUT generation.

**JDK 25 is the target platform.** Vectrix is tuned for modern JIT behavior and Vector API-enabled execution paths, with scalar fallbacks for portability and correctness.

MIT licensed. JOML lineage attribution is documented in `NOTICE`.

---

## Design Goals

**Zero allocation in hot paths.** Hot-path APIs provide destination overloads that write into caller-provided storage. Reuse dest objects/scratch arrays in frame loops.

**Maximum throughput on modern hardware.** Batch kernels use SoA (Structure of Arrays) layout for cache efficiency. SIMD paths use the JDK Vector API when enabled and supported.

**Explicit contracts, enforced when you want them.**
- `FAST`: throughput-first, trust valid inputs.
- `STRICT`: runtime precondition enforcement (for example unit-quaternion checks).

**Rendering-complete.** Vectrix includes renderer-specific math domains (SH, LTC, FFT/convolution, thin-film/Fresnel optics, LUT builders, low-discrepancy sampling), not just generic linear algebra.

---

## What You Can Build With It

**Animation systems**
- Quaternion SLERP/NLERP, weighted average, `log`/`exp`, SQUAD, swing-twist decomposition.
- Angular velocity derive/integrate utilities.
- Curve families (Bezier/Hermite/Catmull-Rom/B-spline) across scalar/vec2/vec3/vec4 with derivatives and arc-length mapping.

**Skinning pipelines**
- LBS and dual-quaternion skinning (with antipodality handling).
- SoA containers and batch kernels (`TransformSoA`, `DualQuatSoA`, `SkinningKernels`).

**Lighting and GI**
- SH L2/L3 projection/convolution/evaluation.
- LTC table sampling and area-light form-factor helpers.

**Physically based materials**
- IOR utilities, dielectric/conductor Fresnel (complex IOR), thin-film interference.
- Preintegrated SSS LUT generation and atmospheric transmittance LUT generation.

**Signal processing**
- Radix-2 FFT (float/double), circular/linear convolution.

**GPU data pipelines**
- Vertex layout descriptors, half-float conversion, packed normalized formats, octahedral normal encoding, quaternion compression.

**Sampling and hashes**
- Poisson/stratified/uniform/spiral/best-candidate samplers.
- Halton, Sobol, scrambled Sobol.
- PCG hash and spatial hash.

Full inventory: [`docs/vectrix-capabilities.md`](docs/vectrix-capabilities.md).

---

## Performance Snapshot

All numbers are JMH AverageTime, normalized to `ns/call`. Published baseline hardware is Apple M4 Max on JDK 25.

| Operation | ns/call |
|---|---:|
| Bezier evaluate (vec3) | ~1.84 |
| Arc-length map | ~4.39 |
| Fresnel dielectric | ~1.77 |
| Thin-film RGB | ~24.57 (16384 batch) |
| SH L3 evaluate | ~9.38 |
| SH L3 project | ~11.72 |
| FFT forward+inverse (256 complex) | ~13.16 per sample |

Full methodology/results: [`BENCHMARKS.md`](BENCHMARKS.md).

---

## Quick Start

### FAST / STRICT mode

```java
import org.vectrix.core.MathMode;
import org.vectrix.experimental.KernelConfig;

MathMode prev = KernelConfig.mathMode();
try {
    KernelConfig.setMathMode(MathMode.STRICT);
    // ... test paths with contract enforcement ...
} finally {
    KernelConfig.setMathMode(prev);
}
```

### Smooth keyframe rotation (SQUAD)

```java
Quaternionf s0 = q0.squadControlPoint(prev, q1, new Quaternionf());
Quaternionf s1 = q1.squadControlPoint(q0, next, new Quaternionf());

Quaternionf out = new Quaternionf();
q0.squad(q1, s0, s1, t, out);
```

### SH probe baking (zero-allocation hot path)

```java
ShCoeffs9f coeffs = new ShCoeffs9f();
float[] scratch = new float[9];

for (int i = 0; i < sampleCount; i++) {
    ShProjection.projectSample(
        dir[i].x, dir[i].y, dir[i].z,
        r[i], g[i], b[i], solidAngle[i],
        scratch, coeffs);
}
ShConvolution.convolveLambertL2(coeffs, coeffs);
```

### Curve arc-length mapping

```java
float[] table = new float[129];
CurveReparameterizer3f.buildArcLengthTableForBezier(p0, p1, p2, p3, 128, table);

float t = CurveReparameterizer3f.mapArcLengthToT(s, table);
Bezier3f.evaluate(p0, p1, p2, p3, t, outPos);
```

### FFT and convolution

```java
// Interleaved complex: [re0, im0, re1, im1, ...]
FFT1f.forward(signal);
FFT1f.inverse(signal);

float[] out = new float[(na + nb - 1) * 2];
Convolutionf.linear(a, b, out);
```

### Dual quaternion skinning (SoA)

```java
SkinningKernels.skinDualQuat4SoA(
    pose,
    j0, j1, j2, j3,
    w0, w1, w2, w3,
    inX, inY, inZ,
    outX, outY, outZ,
    count);
```

---

## Build and Test

```bash
mvn -q test
mvn -q verify
mvn -q clean package -Pbench -DskipTests
```

Run benchmarks:

```bash
BENCH_REGEX='org.vectrix.bench.*' ./scripts/bench-run.sh
```

Compare benchmark outputs:

```bash
./scripts/bench-compare.sh <baseline.csv> <candidate.csv> 12 20 20
```

---

## Project Layout

```text
src/main/java/org/vectrix/
  core/           vectors, matrices, quaternions, numeric utilities
  affine/         TRS/rigid/dual-quat transforms
  soa/            SoA containers and skinning kernels
  simd/           Vector API helpers
  curve/          curves and arc-length reparameterization
  geometry/       culling/intersection/mesh helpers
  gpu/            packing/layout/compression
  sampling/       stochastic and low-discrepancy sampling
  sh/             spherical harmonics
  fft/            FFT and convolution
  optics/         IOR/Fresnel/thin-film/spectral helpers
  ltc/            LTC table sampling and form factors
  renderingmath/  LUT builders, interpolation, bent-normal cones
  color/          color transforms and science utilities
  easing/         easing functions
  sdf/            signed distance functions
  hash/           hash utilities
  experimental/   opt-in experimental controls and kernels
```

---

## Documentation

| Document | Purpose |
|---|---|
| [`API.md`](API.md) | Practical API reference by module |
| [`docs/vectrix-capabilities.md`](docs/vectrix-capabilities.md) | Complete capability inventory by consumer domain |
| [`docs/technology-explainer.md`](docs/technology-explainer.md) | Educational deep dive for non-expert users |
| [`BENCHMARKS.md`](BENCHMARKS.md) | Benchmark methodology and normalized results |
| [`docs/conventions.md`](docs/conventions.md) | API and math conventions |
| [`docs/api-policy.md`](docs/api-policy.md) | Stability and compatibility policy |
| [`docs/release-cut-checklist.md`](docs/release-cut-checklist.md) | Release process |

---

## License

MIT — see [`LICENSE`](LICENSE).  
JOML lineage attribution — see [`NOTICE`](NOTICE).
