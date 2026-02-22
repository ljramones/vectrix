# Vectrix Technology Explainer

This guide is for developers who need to use Vectrix effectively — whether you are a graphics specialist looking for the precise API boundaries, or an engine integrator who needs to understand the design rationale before wiring Vectrix into a larger system.

---

## What Vectrix Is

Vectrix is a rendering-complete Java math kernel for real-time graphics, simulation, and engine workloads. It began as a fork of JOML and has grown into something substantially broader: a full-stack math foundation that covers everything from basic linear algebra through spectral optics, atmospheric LUT generation, and L3 spherical harmonics.

The design philosophy is consistent throughout: **allocation-free hot paths, explicit data layout control, and SIMD-aware execution with scalar fallbacks**. Every API that is expected to appear in a render loop has a dest-overload form that writes into caller-provided storage. Every batch API uses SoA (Structure of Arrays) layout for cache efficiency. Nothing in the hot path creates garbage.

Vectrix is the foundation layer that animation libraries, physics engines, mesh processing tools, and renderers build on. It has no engine dependencies — only the JDK. Every library above it can trust that the math is correct, fast, and consistent.

---

## Core Design Decisions

Understanding these four decisions will make the entire API surface feel logical rather than arbitrary.

### 1. Allocation-Free by Default

In Java, object allocation is not free. Even with a modern GC, allocating a `new Vector3f()` inside a loop that runs 50,000 times per frame creates measurable GC pressure. Vectrix avoids this through **dest-overload APIs**:

```java
// In-place form — mutates 'a', no allocation
a.add(b);

// Dest form — writes into caller-provided storage, zero allocation
Vector3f result = new Vector3f(); // allocate once, outside the loop
a.add(b, result);                 // reuse every frame
```

The pattern appears consistently across vectors, matrices, quaternions, curves, and interpolators. If you see a method that returns a new object, there is almost always a dest-overload form alongside it. Prefer the dest form in any path that executes more than a few times per frame.

### 2. SoA Layout for Batch Paths

For operations on large collections — skinning a thousand bones, evaluating a curve at ten thousand time samples, projecting environment samples onto SH coefficients — Vectrix provides **Structure of Arrays** (SoA) containers and batch kernels.

The difference matters because of how CPU caches work:

```
// AoS (Array of Structs) — common, but cache-unfriendly for component-wise ops
Transform[] transforms = new Transform[1000];
// Accessing transforms[i].x means jumping through 1000 full Transform objects

// SoA (Structure of Arrays) — how Vectrix batch paths work
float[] posX = new float[1000];
float[] posY = new float[1000];
float[] posZ = new float[1000];
// Accessing posX[0..999] is a single sequential memory region — cache friendly
```

When you are processing thousands of items per frame, SoA is typically 2–6× faster than equivalent AoS code because the CPU prefetcher can stream data efficiently. The `org.vectrix.soa` package provides ready-made SoA containers for transforms and dual quaternions, and the batch APIs in the curve, SH, and sampling packages all follow the same layout convention.

### 3. FAST vs STRICT Mode

Vectrix has a global execution mode controlled by `CoreConfig.mathMode()`:

```java
// Check current mode
MathMode mode = CoreConfig.mathMode();

// Switch modes (global — affects all subsequent calls on all threads)
CoreConfig.setMathMode(MathMode.STRICT);
// ... run tests ...
CoreConfig.setMathMode(MathMode.FAST);
```

**STRICT mode** enables precondition checks throughout the library. Methods that require unit quaternions will throw `IllegalArgumentException` if you pass a non-unit input. Methods that require pure quaternions (w=0) will throw if w is non-zero. STRICT mode is designed for CI and test environments where catching contract violations early is worth the runtime cost.

**FAST mode** skips those checks entirely and trusts the caller to supply valid inputs. FAST mode is designed for production hot paths where the inputs have already been validated upstream.

The recommended workflow: run your full test suite with STRICT mode active. Ship production builds in FAST mode. Use the save/restore pattern when switching in tests:

```java
MathMode prev = KernelConfig.mathMode();
try {
    KernelConfig.setMathMode(MathMode.STRICT);
    // test code that expects precondition enforcement
} finally {
    KernelConfig.setMathMode(prev);
}
```

One important caveat: because mode is global state, STRICT/FAST-sensitive tests should not run in parallel with other mode-sensitive tests without isolation.

### 4. Float and Double Parity

Every capability in Vectrix exists in both `float` and `double` precision. The float family uses `f` suffixes (`Quaternionf`, `Bezier3f`, `ShCoeffs9f`). The double family uses `d` suffixes (`Quaterniond`, `Bezier3d`, `ShCoeffs9d`).

The appropriate choice depends on your use case. Float is the right choice for GPU-facing data, real-time rendering math, and anything that will be uploaded to a buffer. Double is appropriate for offline computation, high-precision LUT generation, astronomical positioning, and numerical integration where accumulated error matters.

---

## Package Map

| Package | Purpose |
|---|---|
| `org.vectrix.core` | Vectors, matrices, quaternions, dual quaternions, ranges, RK4 |
| `org.vectrix.affine` | TRS/affine transforms, rigid transforms, dual-quat transforms |
| `org.vectrix.soa` | SoA containers: TransformSoA, DualQuatSoA, SkinningKernels |
| `org.vectrix.simd` | Vector API helpers and SIMD utility types |
| `org.vectrix.curve` | Arc-length reparameterization, curve constants |
| `org.vectrix.curve.scalar` | Bezier/Hermite/CatmullRom/BSpline for scalar float/double channels |
| `org.vectrix.curve.vec2` | Same curve families for 2D vector channels |
| `org.vectrix.curve.vec3` | Same curve families for 3D vector channels |
| `org.vectrix.curve.vec4` | Same curve families for 4D vector channels |
| `org.vectrix.geometry` | Frustum, culling, intersection, mesh math |
| `org.vectrix.gpu` | Vertex layout, half-float, packed normals, quaternion compression |
| `org.vectrix.sampling` | Best-candidate, Poisson, stratified, Halton, Sobol, scrambled Sobol |
| `org.vectrix.easing` | Linear, smoothstep, quad/cubic, bounce, spring easing |
| `org.vectrix.color` | sRGB/linear, XYZ, luminance, tone mapping, color science |
| `org.vectrix.sh` | Spherical harmonics L0–L3: basis, projection, convolution |
| `org.vectrix.fft` | Complex types, radix-2 FFT, circular/linear convolution |
| `org.vectrix.optics` | IOR, Fresnel, thin-film interference, spectral sampling |
| `org.vectrix.sdf` | Signed distance functions: sphere, box, capsule, cylinder, torus |
| `org.vectrix.hash` | PCG hash, spatial hash |
| `org.vectrix.ltc` | LTC area-light table sampling and form-factor evaluation |
| `org.vectrix.renderingmath` | SSS LUT, atmosphere transmittance LUT, interpolation, bent normals |
| `org.vectrix.experimental` | KernelConfig, experimental SIMD and FFM paths |

---

## Domain Guide

### Rotation and Orientation

Rotations in Vectrix use quaternions — always. Euler angles are not represented because they are ambiguous, order-dependent, and produce poor interpolation results. If you are coming from a system that uses Euler angles, convert them to quaternions at the boundary and work exclusively with quaternions inside.

**The interpolation hierarchy**, ordered by quality and cost:

`nlerp` is the cheapest rotation blend — normalized linear interpolation. Fast but produces slightly non-uniform angular speed. Use for blending many weights simultaneously or where speed matters more than precision.

`slerp` is the standard rotation blend — spherical linear interpolation. Uniform angular speed, well-understood behavior, the right default for two-quaternion blending.

`squad` is the correct choice for smooth animation across multiple keyframes. Two raw slerp calls between keyframes will produce a visible velocity discontinuity at each keyframe. SQUAD eliminates that discontinuity by using computed control points to produce C1-continuous curves. Always use SQUAD for multi-keyframe rotation animation:

```java
// Compute control points once (when keyframes are loaded)
Quaternionf s1 = q1.squadControlPoint(q0, q2, new Quaternionf());
Quaternionf s2 = q2.squadControlPoint(q1, q3, new Quaternionf());

// Evaluate at any t in [0,1] between q1 and q2
Quaternionf result = q1.squad(q2, s1, s2, t, new Quaternionf());
```

`swingTwist` decomposes a rotation into two independent components: the twist (rotation around a specified axis, typically a bone's primary axis) and the swing (rotation perpendicular to that axis). This is essential for joint constraints — you cannot implement elbow limits or shoulder cones without separating these components:

```java
Quaternionf swing = new Quaternionf();
Quaternionf twist = new Quaternionf();
boneRotation.swingTwist(new Vector3f(0, 1, 0), swing, twist);
// twist.angle() now gives you the isolated rotation around Y
// swing represents the remaining perpendicular rotation
```

**Hemisphere consistency** is a subtle but critical concern for sequences of animation keyframes. Quaternions `q` and `-q` represent the same rotation, but if a sequence alternates signs, SLERP and SQUAD will take the long path around the sphere instead of the short path. Always pre-process a keyframe sequence before use:

```java
Quaternionf.ensureConsistentHemisphere(keyframes); // in-place, zero allocation
```

**Angular velocity** bridges the rotation and physics domains. Convert between quaternion derivatives and angular velocity vectors for physics-driven animation:

```java
// Derive angular velocity from two poses and elapsed time
Vector3f omega = q0.angularVelocity(q1, dt, new Vector3f());

// Integrate angular velocity back to a rotation delta
Quaternionf delta = Quaternionf.integrateAngularVelocity(omega, dt, new Quaternionf());
```

---

### Curves and Interpolation

The `org.vectrix.curve` package provides four curve families across five channel dimensions (scalar, vec2, vec3, vec4) in both float and double precision. All stateless families use purely static methods — no construction cost, no state.

**Bezier** (cubic) — defined by four control points. The curve passes through the first and last points; the middle two are tangent handles that pull the curve without being on it. Best for authored paths where an artist needs direct handle control.

**Hermite** (cubic) — defined by two endpoints and two tangents at those endpoints. More intuitive for animation curves because the tangents directly represent arrival and departure velocities. Most animation curve editors produce Hermite data.

**Catmull-Rom** — defined by four points where the curve passes through the middle two. The outer two points influence the tangents. The tension parameter controls tightness (0.5 is standard Catmull-Rom). Useful for automatically generating smooth curves through a sequence of control points without specifying tangents.

**UniformBSpline** — defined by an arbitrary-length control point array, evaluated one cubic segment at a time. The curve does not pass through the control points, which makes it smoother and more predictable for camera paths and procedural motion. Requires construction-time storage:

```java
UniformBSpline3f path = new UniformBSpline3f(controlPoints); // stores reference, caller owns
int segments = path.segmentCount();                           // controlPoints.length - 3
Vector3f pos = path.evaluate(segmentIndex, t, new Vector3f());
Vector3f vel = path.derivative(segmentIndex, t, new Vector3f());
```

**Arc-length reparameterization** solves the non-uniform speed problem. Evaluating a Bezier curve at uniformly spaced `t` values does not produce uniformly spaced positions — the curve moves faster in some regions than others. Arc-length mapping converts a normalized arc-length parameter `s ∈ [0,1]` to the `t` value that produces that position:

```java
// Build the table once (expensive — do offline or on load)
float[] table = new float[CurveConstants.DEFAULT_ARCLEN_SAMPLES];
CurveReparameterizer3f.buildArcLengthTableForBezier(p0, p1, p2, p3, 64, table);

// Evaluate at uniform speed (cheap — binary search on the table)
Vector3f pos = CurveReparameterizer3f.evaluateByArcLengthBezier(
    p0, p1, p2, p3, s, table, new Vector3f());
```

**Batch evaluation** is available for all curve families. Use it whenever you need to evaluate a curve at many parameter values — for example, pre-baking a path into a position buffer or sampling a camera spline for motion blur:

```java
// Evaluate 4096 points on a Bezier curve — SoA output
float[] outX = new float[4096];
float[] outY = new float[4096];
float[] outZ = new float[4096];
Bezier3f.evaluateBatch(p0, p1, p2, p3,
    tValues, 0, 4096,
    outX, 0, outY, 0, outZ, 0);
```

---

### Skinning and Skeletal Transforms

For skinned mesh animation, Vectrix provides two skinning paths in `SkinningKernels`: Linear Blend Skinning (LBS) and Dual Quaternion Blending (DQB).

LBS is the traditional approach — blend bone matrices linearly, weighted by the per-vertex bone weights. It is fast and simple but produces the well-known "candy-wrapper" artifact when joints twist significantly.

DQB eliminates the candy-wrapper artifact by blending dual quaternions rather than matrices. The result is geometrically correct under twist but requires slightly more computation. Vectrix's DQB implementation includes antipodality correction — it detects and fixes sign conflicts between dual quaternions that would otherwise cause the blend to take the long path around the rotation sphere.

Both paths have SoA-optimized batch variants (`skinDualQuat4SoA`, `skinLbs4SoA`) that process multiple vertices simultaneously and are structured for SIMD exploitation.

The `TransformSoA` and `DualQuatSoA` containers provide the flat array layout these kernels expect. If you are integrating with an animation system that produces bone transforms per frame, populate a `DualQuatSoA` with the current pose and pass it directly to the skinning kernels.

---

### Spherical Harmonics

Spherical harmonics (SH) are the standard representation for low-frequency lighting in real-time engines — environment light probes, irradiance volumes, and indirect diffuse GI all use SH internally. Vectrix provides L0–L3 SH support (9 and 16 coefficients respectively) with an RGB coefficient storage type designed for probe workflows.

**The three operations you need:**

*Projection* — given a set of environment samples (direction + RGB radiance), project them onto SH coefficients. This is the "capture" operation that runs when a probe is baked:

```java
ShCoeffs9f coeffs = new ShCoeffs9f();
float[] scratch = new float[9]; // reuse across samples — zero allocation hot path

for (int i = 0; i < samples; i++) {
    ShProjection.projectSample(
        dir[i].x, dir[i].y, dir[i].z,
        r[i], g[i], b[i],
        solidAngle[i],
        scratch, coeffs); // zero-allocation overload
}
```

*Convolution* — convolve the SH coefficients with the Lambert diffuse kernel to produce irradiance SH. This converts a radiance probe into an irradiance probe:

```java
ShConvolution.convolveLambertL2(coeffs, coeffs); // in-place
```

*Evaluation* — given a surface normal, evaluate the irradiance SH at that direction to get the incident indirect diffuse light:

```java
float[] rgb = new float[3];
float[] scratch = new float[9];
ShConvolution.evaluateIrradiance(coeffs, nx, ny, nz, scratch, rgb);
// rgb[0], rgb[1], rgb[2] = incident R, G, B irradiance
```

The coordinate convention is Y-up right-handed throughout. All probe data captured and evaluated using Vectrix SH assumes this convention.

---

### FFT and Convolution

The `org.vectrix.fft` package provides a radix-2 Cooley-Tukey FFT with the following conventions locked:

- **Input size must be a power of two.** Non-power-of-two sizes throw `IllegalArgumentException`. Use the next power of two and zero-pad if needed.
- **Interleaved complex layout:** `data[2i]` is the real component, `data[2i+1]` is the imaginary component.
- **Forward FFT is unscaled.** Inverse FFT is scaled by `1/N`. This is the standard engineering convention.

The `Convolutionf` class provides two convolution modes:

*Circular convolution* — both inputs must be the same power-of-two complex count. The output wraps around. Use this when you want frequency-domain multiplication without edge effects, for example in bloom kernel application.

*Linear convolution* — inputs can have different lengths. Zero-padding to `nextPowerOfTwo(na + nb - 1)` is handled internally. The output length is `na + nb - 1`. Use this for general signal convolution, for example in ocean simulation where you are convolving a wave spectrum with a displacement kernel.

---

### Optics and Material Math

The `org.vectrix.optics` package covers the math underlying physically-based material models.

**IOR utilities** (`Iorf`) handle the conversion math that appears throughout PBR shading — converting between IOR values and Fresnel F0 reflectance, computing Schlick approximations, and handling the eta ratio between media:

```java
float F0 = Iorf.schlickF0(1.0f, 1.5f); // air to glass
```

**Fresnel equations** (`Fresnelf`) compute exact reflectance for both dielectric and conductor materials. Dielectric Fresnel takes real IOR values. Conductor Fresnel takes complex IOR values using `Complexf`:

```java
// Dielectric (glass, water, plastic)
float reflectance = Fresnelf.dielectric(cosTheta, etaI, etaT);

// Conductor (metals — requires complex IOR n+ik)
Complexf ior = new Complexf(2.93f, 3.0f); // gold approximation at 550nm
float reflectance = Fresnelf.conductor(cosTheta, ior);
```

**Thin-film interference** (`ThinFilmf`) computes the wavelength-dependent reflectance of a thin coating. This produces iridescent color shifts as a function of film thickness and viewing angle — soap bubbles, beetle shells, oil slicks:

```java
Vector3f rgb = new Vector3f();
ThinFilmf.reflectanceRgb(1.0f, filmIor, substrateIor, filmThickness, cosTheta, rgb);
// rgb contains the tri-band (R/G/B wavelength) reflectance
```

---

### Rendering Math (LUT Generation)

The `org.vectrix.renderingmath` package contains the math for generating precomputed lookup tables. These are renderer-agnostic — the outputs are flat float arrays that your renderer uploads as textures.

**Preintegrated SSS LUT** — builds the 2D lookup table for preintegrated subsurface scattering (Penner/d'Eon method). The LUT axes are curvature and NdotL. The output is RGB reflectance:

```java
SssProfile skin = SssProfile.singleGaussian(
    0.6f, 0.4f, 0.2f,  // weights (r,g,b)
    0.8f, 0.5f, 0.3f); // variances (r,g,b)

float[] lut = new float[64 * 64 * 3];
SssLutBuilder.build(64, 64, skin, 2.0f, 6.0f, 64, lut);
```

**Atmospheric transmittance LUT** — builds the 2D transmittance table for Bruneton-style atmosphere rendering. The LUT axes are height above ground and cosine of the zenith angle:

```java
AtmosphereParams earth = new AtmosphereParams(
    6360000f, 6460000f, // ground/atmosphere radii
    8000f, 1200f,       // Rayleigh/Mie scale heights
    5.8e-6f, 13.5e-6f, 33.1e-6f, // betaRayleigh RGB
    2.0e-5f, 2.0e-5f, 2.0e-5f    // betaMie RGB
);
float[] lut = new float[256 * 64 * 3];
TransmittanceLutBuilder.build(256, 64, earth, 64, lut);
```

**LTC area-light evaluation** — the `org.vectrix.ltc` package provides the runtime evaluation math for Linearly Transformed Cosines area lighting. The LTC matrix table (a 64×64 float array) lives in your renderer as a texture; Vectrix provides the sampling and form-factor evaluation:

```java
// Sample the LTC matrix at (NdotV, roughness)
float[] m3 = new float[9];
LtcTablef.sampleMat3(ltcTableData, 64, 64, roughness, NdotV, m3);

// Evaluate rectangle area light form factor
float formFactor = LtcEvalf.formFactorRectClipped(v0, v1, v2, v3);
```

---

### Sampling

The `org.vectrix.sampling` package covers stochastic sampling for rendering algorithms.

**Poisson and stratified sampling** are the general-purpose options for offline or pre-baked sample sets. Use stratified for any application where regular grid sampling would alias.

**Halton sequences** generate a deterministic low-discrepancy sequence indexed by integer position. Base 2/3 for 2D, base 2/3/5 for 3D. The key property is that any prefix of a Halton sequence is well-distributed — you can add more samples without discarding earlier ones:

```java
Vector2f sample = new Vector2f();
HaltonSequence.halton2D(frameIndex, sample); // TAA jitter
```

**Sobol sequences** provide better distribution than Halton for higher dimensions. Vectrix includes hardcoded direction numbers for dimensions 1–4. The scrambled variant (Owen scrambling) improves stratification further and is recommended for production TAA and path tracing:

```java
Vector2f sample = new Vector2f();
SobolSequence.sobolScrambled2D(sampleIndex, frameIndex, sample); // seed by frame for temporal variation
```

---

### GPU Packing and Data Transfer

The `org.vectrix.gpu` package handles the conversion from full-precision math types to compact GPU formats. Packing happens once on upload; the GPU works with the compact form.

**Half-float** (`Half`) converts `float` to 16-bit float (IEEE 754 half-precision). Reduces position/normal buffer sizes by 50% at the cost of reduced precision — usually acceptable for normals and UVs.

**Packed normalized formats** (`PackedNorm`) pack normalized float values into 8, 10, or 16-bit integer formats. Used for vertex colors and blend weights.

**Octahedral normal packing** (`OctaNormal`) encodes a unit normal vector into two floats or integers using the octahedral projection. Produces more uniformly distributed error than spherical coordinates and is the recommended format for normal buffers.

**Quaternion compression** (`QuatCompression`) compresses a unit quaternion to 32 or 48 bits using the "smallest three" method — stores the three smallest components and reconstructs the largest from the unit quaternion constraint.

---

## Performance Rules

**1. Allocate once, reuse always.** Create dest objects and scratch arrays once outside your loop and pass them in on every iteration. The JVM's escape analysis may optimize away short-lived allocations but don't rely on it in a hot path.

**2. Use batch APIs for anything over a hundred items.** Single-item APIs have function call overhead and branch prediction cost per call. Batch APIs amortize that cost and give the JIT more context for optimization.

**3. SoA over AoS at scale.** If you are processing more than a few hundred items per frame, restructure your data into SoA layout. The cache benefit is real and measurable — typically 2–4× improvement on large batches.

**4. STRICT in tests, FAST in production.** The precondition checks in STRICT mode are not free. They add branch overhead and length computation to every guarded call. Run your test suite with STRICT to catch contract violations; ship with FAST.

**5. Measure with JMH before and after changes.** The benchmark suite in `src/bench/java` covers all major subsystems. Always run a before/after comparison with a fixed seed and iteration count before claiming a performance improvement. The `bench-compare.sh` script handles normalization.

**6. Match precision to purpose.** Float for GPU-facing and real-time paths. Double for offline LUT generation, numerical integration, and anything where accumulated error matters over many iterations.

---

## Common Integration Patterns

### Animation System Integration

```java
// 1. Load keyframes (once, on asset load)
Quaternionf[] rotations = loadKeyframeRotations();
Quaternionf.ensureConsistentHemisphere(rotations); // fix sign flips

// 2. Compute SQUAD control points (once, on asset load)
Quaternionf[] controlPoints = buildSquadControlPoints(rotations);

// 3. Each frame: find segment and evaluate
int seg = findSegment(currentTime, keyframeTimes);
float t = localT(currentTime, keyframeTimes[seg], keyframeTimes[seg + 1]);
Quaternionf boneRot = rotations[seg].squad(
    rotations[seg + 1],
    controlPoints[seg],
    controlPoints[seg + 1],
    t, scratchQuat);
```

### Probe Baking Integration

```java
// 1. Sample the environment (many directions, typically 1024–4096)
ShCoeffs9f coeffs = new ShCoeffs9f();
float[] scratch = new float[9]; // reuse across samples

for (int i = 0; i < sampleCount; i++) {
    // sample environment at direction[i] -> r, g, b
    ShProjection.projectSample(
        dir[i].x, dir[i].y, dir[i].z,
        r, g, b, solidAngle[i], scratch, coeffs);
}

// 2. Convolve for irradiance
ShConvolution.convolveLambertL2(coeffs, coeffs);

// 3. Upload coeffs to GPU (27 floats per probe)
uploadProbeData(coeffs.c);
```

### Camera Path Integration

```java
// 1. Build spline from authored control points (once)
UniformBSpline3f cameraPath = new UniformBSpline3f(waypoints);

// 2. Build arc-length table for each segment (once)
float[][] tables = buildArcLengthTables(cameraPath);

// 3. Each frame: evaluate at uniform speed
float s = currentTime / totalPathDuration; // normalized arc-length
int seg = (int)(s * cameraPath.segmentCount());
float localS = (s * cameraPath.segmentCount()) - seg;
Vector3f camPos = CurveReparameterizer3f.evaluateByArcLength(
    cameraPath, seg, localS, tables[seg], new Vector3f());
```

---

## Benchmark Methodology

All benchmarks use JMH (Java Microbenchmark Harness). The exact run profile and current published parameters are documented in `BENCHMARKS.md`.

When reading benchmark numbers, always use **normalized per-call cost** rather than raw `ns/op`. Raw `ns/op` varies with batch size and is not comparable across methods. Normalized cost is `ns/op ÷ batch_size`.

Hardware dependency: current published baselines were captured on Apple M4 Max. M-series chips have excellent cache hierarchy and out-of-order execution that is particularly favorable for sequential math code. Results on x86 server hardware will differ — not necessarily worse, but characteristically different. Always rerun benchmarks on your target hardware before making optimization decisions.

---

## Where to Go Next

- **API capability inventory:** `docs/vectrix-capabilities.md` — complete feature list organized by consumer domain
- **Benchmark results:** `BENCHMARKS.md` — normalized per-call performance tables for all subsystems
- **Build and test:** `mvn clean verify` for full build, `mvn -Pbench package` for benchmark jar
- **Experimental paths:** `mvn -Pexperimental test` for Vector API and FFM-oriented paths
- **Release process:** `docs/release-cut-checklist.md`
