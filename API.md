# Vectrix API Guide

Last updated: 2026-02-22

This guide is the practical API reference for Vectrix. It is example-first and intended for users integrating Vectrix into rendering, animation, simulation, and tooling code.

For capability inventory by consumer domain, see `docs/vectrix-capabilities.md`.

## 1) Core Math (`org.vectrix.core`)

### Vectors and matrices

```java
import org.vectrix.core.*;

Vector3f a = new Vector3f(1, 2, 3);
Vector3f b = new Vector3f(4, 5, 6);
Vector3f out = new Vector3f();

a.add(b, out);                // destination-overload style
float d = a.dot(b);

Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(60.0), 16f / 9f, 0.1f, 1000f);
Matrix4f view = new Matrix4f().lookAt(0, 2, 8, 0, 0, 0, 0, 1, 0);
Matrix4f viewProj = proj.mul(view, new Matrix4f());
```

### Quaternion interpolation and curves

```java
Quaternionf q0 = new Quaternionf().rotateY(0.2f);
Quaternionf q1 = new Quaternionf().rotateY(1.0f);
Quaternionf s0 = q0.squadControlPoint(q0, q1, new Quaternionf());
Quaternionf s1 = q1.squadControlPoint(q0, q1, new Quaternionf());

Quaternionf slerpMid = q0.slerp(q1, 0.5f, new Quaternionf());
Quaternionf squadMid = q0.squad(q1, s0, s1, 0.5f, new Quaternionf());
```

### Swing-twist and angular velocity bridge

```java
Vector3f axisY = new Vector3f(0, 1, 0);
Quaternionf swing = new Quaternionf();
Quaternionf twist = new Quaternionf();
q0.swingTwist(axisY, swing, twist);

Vector3f omega = q0.angularVelocity(q1, 1.0f / 60.0f, new Vector3f());
Quaternionf dq = Quaternionf.integrateAngularVelocity(omega, 1.0f / 60.0f, new Quaternionf());
```

### Numeric utilities

```java
float x = Rangef.remapClamped(0.75f, 0f, 1f, -1f, 1f);

float[] state = new float[] {1f, 0f};
float[] scratch = new float[state.length * 5];
IntegratorRK4f.step(new OdeDerivativef() {
    @Override
    public void compute(float t, float[] s, float[] dsdt) {
        dsdt[0] = s[1];
        dsdt[1] = -s[0];
    }
}, 0f, 0.016f, state, 0, state.length, scratch, 0, state, 0);
```

## 2) Affine and Transforms (`org.vectrix.affine`)

```java
import org.vectrix.affine.*;
import org.vectrix.core.*;

Transformf t = new Transformf();
t.translation.set(1, 2, 3);
t.rotation.rotateXYZ(0.1f, 0.2f, 0.3f);
t.scale.set(2, 2, 2);

Affine4f a = t.toAffine4fFast(new Affine4f());
Matrix4x3f m = t.toAffineMat4Fast(new Matrix4x3f());

Transformf world = Transformf.compose(parent, local, new Transformf());
```

Dual-quaternion transform for rigid blend paths:

```java
DualQuatTransformf dq = new DualQuatTransformf().setFromRigid(new RigidTransformf().identity()).normalize();
Vector3f p = dq.transformPosition(1, 0, 0, new Vector3f());
```

## 3) Curves (`org.vectrix.curve`)

Families available (float and double):
- Bezier
- Hermite
- Catmull-Rom
- Uniform B-spline

Domains available:
- scalar (`curve.scalar`)
- vec2/vec3/vec4 (`curve.vec2`, `curve.vec3`, `curve.vec4`)

```java
import org.vectrix.curve.vec3.Bezier3f;
import org.vectrix.core.Vector3f;

Vector3f p = Bezier3f.evaluate(p0, p1, p2, p3, 0.35f, new Vector3f());
Vector3f v = Bezier3f.derivative(p0, p1, p2, p3, 0.35f, new Vector3f());

float[] arcTable = CurveReparameterizer3f.buildArcLengthTableForBezier(p0, p1, p2, p3, 128, new float[128]);
float t = CurveReparameterizer3f.mapArcLengthToT(0.5f, arcTable);
```

## 4) SoA and Skinning (`org.vectrix.soa`)

```java
TransformSoA transforms = new TransformSoA(1024);
AABBSoA bounds = new AABBSoA(1024);
DualQuatSoA dq = new DualQuatSoA(1024);

TransformKernels.composeBatch(parents, locals, world, 1024);
SkinningKernels.skinLbs4SoA(soa, j0, j1, j2, j3, w0, w1, w2, w3, inX, inY, inZ, outX, outY, outZ, count);
SkinningKernels.skinDualQuat4SoA(dq, j0, j1, j2, j3, w0, w1, w2, w3, inX, inY, inZ, outX, outY, outZ, count);
```

## 5) Geometry (`org.vectrix.geometry`)

```java
FrustumPlanes planes = new FrustumPlanes().set(viewProj, true);
int[] cls = new int[bounds.length()];
CullingKernels.frustumCullAabbBatch(planes, bounds, cls, bounds.length());

RayAabIntersection hit = new RayAabIntersection().set(ox, oy, oz, dx, dy, dz);
boolean intersects = hit.test(minX, minY, minZ, maxX, maxY, maxZ);
```

## 6) Rendering Math (`org.vectrix.renderingmath`)

```java
SssProfile profile = SssProfile.singleGaussian(1f, 0.8f, 0.6f, 0.2f, 0.3f, 0.4f);
float[] sssLut = new float[128 * 128 * 3];
SssLutBuilder.build(128, 128, profile, 2.0f, 6.0f, 64, sssLut);

AtmosphereParams atm = new AtmosphereParams(
    6360000f, 6460000f,
    8000f, 1200f,
    new Vector3f(5.8e-6f, 13.5e-6f, 33.1e-6f),
    new Vector3f(2.0e-5f, 2.0e-5f, 2.0e-5f),
    0.8f);
float[] transLut = new float[256 * 64 * 3];
TransmittanceLutBuilder.build(256, 64, atm, 64, transLut);
```

Interpolation and cone helpers:

```java
float x = Interpolationf.bilinear(q00, q10, q01, q11, tx, ty);
float y = Interpolationf.bicubicHermite(samples4x4, tx, ty);
float coneAngle = BentNormalConef.coneAngleFromAo(ao);
```

## 7) SH (`org.vectrix.sh`)

```java
ShCoeffs9f coeffs = new ShCoeffs9f().zero();
float[] b9 = new float[9];
ShProjection.projectSample(nx, ny, nz, r, g, bl, solidAngle, b9, coeffs);

ShCoeffs9f irradianceCoeffs = ShConvolution.convolveLambertL2(coeffs, new ShCoeffs9f());
float[] rgb = new float[3];
ShConvolution.evaluateIrradiance(irradianceCoeffs, nx, ny, nz, b9, rgb);
```

L3 support is available via `ShBasis`, `ShProjection`, `ShConvolution`, and `ShCoeffs16f/d`.

## 8) FFT and Convolution (`org.vectrix.fft`)

Data is interleaved complex: `data[2*i] = real`, `data[2*i+1] = imag`.

```java
float[] data = ...; // length = 2 * N, N power-of-two
FFT1f.forward(data);
FFT1f.inverse(data); // inverse scales by 1/N

float[] a = ...;
float[] b = ...;
float[] out = new float[a.length];
Convolutionf.circular(a, b, out);
```

## 9) Optics and Color

### Optics (`org.vectrix.optics`)

```java
float fDielectric = Fresnelf.dielectric(cosTheta, 1.0f, 1.5f);
float fConductor = Fresnelf.conductor(cosTheta, n, k);

Vector3f thin = ThinFilmf.reflectanceRgb(1.0f, 1.38f, 1.5f, 320.0f, cosTheta, new Vector3f());
float f0 = Iorf.schlickF0(1.0f, 1.5f);
```

### Color (`org.vectrix.color`)

```java
float linear = ColorMathf.srgbToLinear(0.5f);
float srgb = ColorMathf.linearToSrgb(linear);
float Y = ColorMathf.luminanceLinear(r, g, b);
Vector3f xyz = ColorMathf.linearSrgbToXyz(new Vector3f(r, g, b), new Vector3f());
```

## 10) LTC (`org.vectrix.ltc`)

```java
float[] m3 = new float[9];
LtcTablef.sampleMat3(table, width, height, roughness, ndotv, m3);

float ffRect = LtcEvalf.formFactorRectClipped(v0, v1, v2, v3);
```

## 11) Sampling (`org.vectrix.sampling`)

```java
Vector2f h = HaltonSequence.halton2D(index, new Vector2f());
Vector2f s = SobolSequence.sobol2D(index, new Vector2f());
Vector2f ss = SobolSequence.sobolScrambled2D(index, seed, new Vector2f());

SobolSequence.sobolScrambledBatch2D(start, count, seed, outX, 0, outY, 0);
```

Legacy samplers are also available (`PoissonSampling`, `StratifiedSampling`, `UniformSampling`, `BestCandidateSampling`, `SpiralSampling`).

## 12) GPU Utilities (`org.vectrix.gpu`)

```java
short h = Half.pack(0.75f);
float f = Half.unpack(h);

int packedN = PackedNorm.packSnorm8x4(nx, ny, nz, nw);
int oct = OctaNormal.encodeSnorm16(nx, ny, nz);
Vector3f dec = OctaNormal.decodeSnorm16(oct, new Vector3f());

long qPacked = QuatCompression.packSmallest3(q);
Quaternionf q2 = QuatCompression.unpackSmallest3(qPacked, new Quaternionf());
```

## 13) Runtime Mode and SIMD

```java
KernelConfig.setMathMode(MathMode.FAST);
float sumFast = Reduction.sumFast(values);

KernelConfig.setMathMode(MathMode.STRICT);
float sumStrict = Reduction.sumStrict(values);

boolean simd = SimdSupport.isVectorApiAvailable();
```

## Build, Test, Benchmark

```bash
mvn -q test
mvn -q verify
mvn -q clean package -Pbench -DskipTests
BENCH_REGEX='org.vectrix.bench.*' ./scripts/bench-run.sh
```

## Usage Notes
- Prefer destination-parameter overloads in hot loops.
- Prefer SoA/batch kernels for large workloads.
- Use STRICT mode for validation/repro, FAST mode for throughput.
- For release metrics and normalized per-op results, use `BENCHMARKS.md`.
