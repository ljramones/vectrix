# Vectrix

Vectrix is a rendering-focused Java math library for real-time engines.  
It includes core linear algebra, transforms, interpolation, sampling, signal processing, and renderer-oriented math kernels.

MIT licensed. JOML lineage attribution is documented in `NOTICE`.

## What You Can Build With It
- Animation systems: quaternion interpolation (`slerp`, `squad`, swing-twist), curve tracks, arc-length mapping.
- Rendering systems: SH probes, FFT workloads, LTC math, optics/thin-film, SSS/transmittance LUT generation.
- GPU pipelines: layout/packing/compression (`Half`, `PackedNorm`, `OctaNormal`, `QuatCompression`).
- Simulation/physics glue: RK4 integration, range/remap helpers, low-discrepancy sampling.

Full consumer-oriented inventory: `docs/vectrix-capabilities.md`.

## Quick Start

### 1) Quaternion SQUAD
```java
Quaternionf q0 = ...;
Quaternionf q1 = ...;
Quaternionf prev = ...;
Quaternionf next = ...;

Quaternionf s0 = q0.squadControlPoint(prev, q1, new Quaternionf());
Quaternionf s1 = q1.squadControlPoint(q0, next, new Quaternionf());
Quaternionf out = q0.squad(q1, s0, s1, 0.35f, new Quaternionf());
```

### 2) Curve Arc-Length Mapping
```java
float[] table = CurveReparameterizer3f.buildArcLengthTableForBezier(p0, p1, p2, p3, 128, new float[129]);
float t = CurveReparameterizer3f.mapArcLengthToT(0.5f, table);
Vector3f p = Bezier3f.evaluate(p0, p1, p2, p3, t, new Vector3f());
```

### 3) FFT + Convolution
```java
float[] signal = ...; // interleaved complex [re0, im0, re1, im1, ...]
FFT1f.forward(signal);
FFT1f.inverse(signal); // inverse includes 1/N normalization

float[] out = new float[(na + nb - 1) * 2];
Convolutionf.linear(a, b, out);
```

### 4) SH Projection (L2/L3)
```java
ShCoeffs16f sh = new ShCoeffs16f().zero();
float[] scratch = new float[16];
ShProjection.projectSampleL3(dirX, dirY, dirZ, r, g, b, solidAngle, scratch, sh);
```

## Build & Test
```bash
mvn -q test
mvn -q verify
```

Bench build:
```bash
mvn -q clean package -Pbench -DskipTests
```

## Benchmarks
- Methodology and baseline publication format: `BENCHMARKS.md`
- Runner script: `./scripts/bench-run.sh`
- Latest artifacts: `target/benchmarks/latest.txt`, `target/benchmarks/latest.csv`

## Project Layout
- `src/main/java/org/vectrix/core` - vectors/matrices/quaternions and numeric utilities
- `src/main/java/org/vectrix/affine`, `soa`, `simd` - transforms, batch kernels, SIMD support
- `src/main/java/org/vectrix/curve` - cubic curves, derivatives, arc-length reparameterizers
- `src/main/java/org/vectrix/renderingmath`, `sh`, `ltc`, `optics` - rendering-domain math
- `src/main/java/org/vectrix/gpu` - packing/layout/compression
- `src/main/java/org/vectrix/sampling`, `fft`, `hash`, `sdf`, `color`, `easing` - supporting domains
- `src/test/java` - unit tests
- `src/bench/java`, `src/jmh/java` - JMH benchmarks

## Release Docs
- Capabilities: `docs/vectrix-capabilities.md`
- Conventions: `docs/conventions.md`
- API policy: `docs/api-policy.md`

## License
- MIT: `LICENSE`
- Attribution notice: `NOTICE`
