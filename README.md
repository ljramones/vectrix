# Vectrix

Vectrix is a pure Java math kernel for real-time graphics, simulation, and engine workloads. It covers the full mathematical surface needed to build a production rendering engine, from core linear algebra through spherical harmonics, FFT-based convolution, physically based optics, and precomputed LUT generation.

**JDK 25 is the target platform.** Vectrix is tuned for modern JIT behavior and Vector API-enabled execution paths, with scalar fallbacks for portability and correctness.

MIT licensed. JOML lineage attribution is documented in `NOTICE`; why Vectrix forked JOML, and how the inherited
code has been corrected since, is in [`docs/joml-divergence.md`](docs/joml-divergence.md).

---
## Current Benchmarks (Prominent)

Current benchmark inventory and methodology are tracked in [`BENCHMARKS.md`](BENCHMARKS.md), with runnable details in [`docs/benchmarking-guide.md`](docs/benchmarking-guide.md) and environment capture in [`docs/benchmark-environment.md`](docs/benchmark-environment.md).

How results are obtained (current standard path):

```bash
mvn -q clean package -Pbench -DskipTests
./scripts/bench-quick.sh
./scripts/bench-full.sh
./scripts/bench-prof.sh
./scripts/bench-normalize.py benchmarks/results/YYYY-MM-DD/full.json
```

Current benchmark suites include:
- Core latency/throughput: `JomlBenchmark`, `JomlFmaBenchmark`, `Affine4fBenchmark`, `BatchMatrixBenchmark`, `ReductionBenchmark`
- Transform/runtime prep: `TransformComposeBenchmark`, `TransformAabbBenchmark`, `PackedAffineConversionBenchmark`, `InstanceUploadBenchmark`, `GpuTransformLayoutBenchmark`
- Skinning: `SkinningBenchmark`, `SkinningKernelBenchmark`, `SkinningEquivalenceBenchmark`
- Integration: `IntegrationPipelineBenchmark`, `SubsystemIntegrationBenchmark`
- Geometry/mesh/culling: `FrustumCullingBenchmark`, `MeshMathBenchmark`
- GPU/interoperability/memory: `GpuPackingBenchmark`, `GpuLayoutBenchmark`, `StdLayoutBenchmark`, `InteropBenchmark`, `MemoryBackendBenchmark`, `QuatCompressionBenchmark`
- Rendering math domains: `CurveBenchmark`, `ShBenchmark`, `ShHotPathBenchmark`, `FftBenchmark`, `LtcBenchmark`, `OpticsBenchmark`, `LowDiscrepancyBenchmark`
- Newly added coverage: `PhysicsMathBenchmark`, `HashBenchmark`, `SdfBenchmark`, `SamplingBenchmark`, `ColorBenchmark`, `EasingBenchmark`, `ParallelTransformBenchmark`

---

## Design Goals

**Zero allocation in hot paths.** Hot-path APIs provide destination overloads that write into caller-provided storage. Reuse dest objects/scratch arrays in frame loops.

**Maximum throughput on modern hardware.** Batch kernels use SoA (Structure of Arrays) layout for cache efficiency. SIMD paths use the JDK Vector API when enabled and supported (`simd` classifier, see Quick Start).

**Explicit contracts, enforced when you want them.**
- `FAST`: throughput-first, trust valid inputs.
- `STRICT`: validates preconditions (for example unit quaternions) and throws instead of repairing, keeps FAST
  scalar results bit for bit, and uses accurate fixed-order reductions. See `docs/conventions.md`.

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

### Dependency

```xml
<dependency>
    <groupId>org.dynamisengine</groupId>
    <artifactId>vectrix</artifactId>
    <version>2.5.0</version>
</dependency>
```

**SIMD build.** The `simd` classifier is the same library with the Vector API batch kernels compiled
in (transform compose, frustum culling, reductions). Use it instead of the plain jar, and run with
`--add-modules jdk.incubator.vector` to enable them:

```xml
<dependency>
    <groupId>org.dynamisengine</groupId>
    <artifactId>vectrix</artifactId>
    <version>2.5.0</version>
    <classifier>simd</classifier>
</dependency>
```

Without that flag the `simd` jar runs the scalar kernels, so it is safe to ship either way.
`SimdSupport.backend()` reports which one is active, and `-Dvectrix.simd.enabled=false` forces scalar. SIMD and scalar
results are bitwise identical (see `docs/determinism.md`). The Vector API is an incubator module, so the JDK prints a
one-line warning when it is enabled.


### FAST / STRICT mode

```java
import org.dynamisengine.vectrix.core.MathMode;
import org.dynamisengine.vectrix.core.CoreConfig;

MathMode prev = CoreConfig.mathMode();
try {
    CoreConfig.setMathMode(MathMode.STRICT);
    // ... test paths with contract enforcement ...
} finally {
    CoreConfig.setMathMode(prev);
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
BENCH_REGEX='org.dynamisengine.vectrix.bench.*' ./scripts/bench-run.sh
```

Compare benchmark outputs:

```bash
./scripts/bench-compare.sh <baseline.csv> <candidate.csv> 12 20 20
```

---

## Project Layout

```text
src/main/java/org/dynamisengine/vectrix/
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
  physics/        inertia tensors and PBD constraint helpers
  experimental/   opt-in experimental controls and kernels (@Internal, not exported)
```

---

## Documentation

| Document | Purpose |
|---|---|
| [`API.md`](API.md) | Practical API reference by module |
| [`docs/vectrix-capabilities.md`](docs/vectrix-capabilities.md) | Complete capability inventory by consumer domain |
| [`docs/technology-explainer.md`](docs/technology-explainer.md) | Educational deep dive for non-expert users |
| [`docs/performance-showcase.md`](docs/performance-showcase.md) | Concise performance evidence summary and runtime doctrine |
| [`docs/why-vectrix-is-fast.md`](docs/why-vectrix-is-fast.md) | Short technical explainer of why the current kernels are fast |
| [`BENCHMARKS.md`](BENCHMARKS.md) | Benchmark methodology and normalized results |
| [`docs/conventions.md`](docs/conventions.md) | API and math conventions |
| [`docs/api-policy.md`](docs/api-policy.md) | Stability and compatibility policy |
| [`docs/release-cut-checklist.md`](docs/release-cut-checklist.md) | Release process |

---

## Build and Release

Vectrix follows the same release flow as `org.dynamisengine:dynamis-parent`.

- Local build: `./build.sh`
- Pre-deploy gate: `mvn -Prelease clean verify`
- Deploy: `./deploy.sh`

Note: legacy OSSRH/Nexus staging configuration was intentionally removed in favor of the parent-aligned Central publishing flow.

---

## License

MIT — see [`LICENSE`](LICENSE).  
JOML lineage attribution — see [`NOTICE`](NOTICE).  
The official CIE colorimetric tables in `color.CieData` (CIE 1931 / 1964 observers, illuminant D65; © CIE) are
CC BY-SA 4.0, attributed in [`NOTICE`](NOTICE); that file alone is under that license.
