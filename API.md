# Vectrix API Guide

This document is a practical API reference for Vectrix with usage examples by module.

## Concepts and Definitions

### Quaternion

A quaternion is a compact way to represent 3D rotation (four values: `x, y, z, w`).  
Why use it: avoids gimbal lock and is stable for interpolation compared to Euler angles.  
In practice: use `Quaternionf` for object orientation, camera orientation, and animation blending.

### Dual Quaternion

A dual quaternion represents a rigid transform (rotation + translation) in one structure.  
Why use it: blending rigid transforms (for skinning) produces fewer artifacts than blending matrices directly.  
In practice: use `DualQuatTransformf` and `DualQuatSoA` for dual-quaternion skinning paths.

### Frustum

A frustum is the camera’s visible volume (a truncated pyramid in perspective projection).  
Why use it: fast visibility tests let you skip drawing objects the camera cannot see.  
In practice: `FrustumPlanes` + `CullingKernels` classify bounds as `OUTSIDE`, `INTERSECT`, or `INSIDE`.

### TRS (Translation, Rotation, Scale)

TRS is a transform decomposition into position, orientation, and size components.  
Why use it: intuitive editing and stable composition in scene graphs.  
In practice: `Transformf` is a first-class TRS type, with conversion to `Affine4f`/`Matrix4x3f`.

### Rigid Transform and Rigid Inverse

A rigid transform has rotation + translation only (no scaling/skew).  
Rigid inverse uses specialized math to invert it faster than a full general matrix inverse.  
Why use it: camera/view transforms and many skeletal/joint operations are rigid.

### SoA (Structure of Arrays)

SoA stores each component in its own array (`tx[]`, `ty[]`, `tz[]`, ...), instead of an array of objects.  
Why use it: better memory locality, easier vectorization, and better throughput for batch kernels/GPU upload.  
In practice: `TransformSoA`, `DualQuatSoA`, and `AABBSoA`.

### DualQuatSoA

SoA container for dual quaternions (`real` + `dual` components in separate arrays).  
Why use it: batch dual-quaternion skinning and blending with cache-friendly layout.

### AABB and AABBSoA

AABB means Axis-Aligned Bounding Box (min/max bounds aligned to world axes).  
Why use it: cheap intersection/culling primitive for broad-phase visibility/collision.  
`AABBSoA` stores many AABBs in SoA format for batched culling.

### Skinning Kernels

Skinning kernels deform mesh vertices using bone/joint transforms and per-vertex weights.  
Why use it: this is a hot path in animated rendering.  
In practice: `SkinningKernels` supports batched LBS and dual-quaternion style workflows.

### Batch Transform Compose

Batch compose computes many `world = parent * local` transforms in one call.  
Why use it: scene graphs and skeletons can contain thousands of nodes every frame.  
In practice: `TransformKernels.composeBatch(...)`.

### Ray / AABB Intersection

Tests whether a ray intersects a bounding box.  
Why use it: picking, editor selection, visibility probes, and broad collision checks.  
In practice: configure `RayAabIntersection` once, test many boxes.

### Octahedral Normals

Octahedral encoding packs a 3D unit normal into two components.  
Why use it: major bandwidth/storage reduction for normals with good reconstruction quality.  
In practice: `OctaNormal.encodeSnorm16(...)` / `decodeSnorm16(...)`.

### Quaternion Compression (Smallest-3)

Stores only 3 quaternion components plus metadata; reconstructs the omitted one at decode.  
Why use it: compact animation/instance/network transform data.  
In practice: `QuatCompression.packSmallest3(...)` / `unpackSmallest3(...)`.

### Packed Normalized Formats (SNORM/UNORM)

SNORM/UNORM map floats in fixed ranges to integers (for example 8-bit or 16-bit channels).  
Why use it: compact vertex/material data with predictable decode behavior.  
In practice: `PackedNorm` helpers and `VertexAttributeFormat` choices.

### Affine Matrix (3x4)

Affine matrices represent linear transform + translation without full projective terms.  
Why use it: smaller and often faster than 4x4 for most object/world transforms.  
In practice: `Affine4f` and `Matrix4x3f`.

## Quick Start

Purpose: show Vectrix’s allocation-aware API style.  
When to use: any per-frame or per-vertex loop where GC pressure matters.  
Result: operations write into caller-provided destinations instead of allocating temporary objects.

```java
import org.vectrix.core.*;

Vector3f a = new Vector3f(1.0f, 2.0f, 3.0f);
Vector3f b = new Vector3f(0.5f, 1.0f, -2.0f);
Vector3f out = new Vector3f();

a.add(b, out);               // allocation-free destination style
float d = a.dot(b);          // scalar result
```

## 1) Core Math (`org.vectrix.core`)

### Vectors

Purpose: demonstrate core vector operations used in camera, lighting, tangent-space, and physics math.  
When to use: whenever you need direction, orientation, or magnitude calculations.  
Result: `cross` gives perpendicular vector, `normalize` gives unit-length direction, `length` gives magnitude.

```java
Vector3f v = new Vector3f(1, 2, 3);
Vector3f w = new Vector3f(4, 5, 6);
Vector3f out = new Vector3f();

v.cross(w, out);             // out = v x w
v.normalize(out);            // out = normalized v
float len = v.length();
```

### Matrices

Purpose: build render-camera matrices and combine them for one-step world-to-clip transforms.  
When to use: render setup, frustum extraction, GPU uniform updates.  
Result: `viewProj` can be reused for culling, projection, and shader constants.

```java
Matrix4f proj = new Matrix4f().perspective((float) java.lang.Math.toRadians(60.0), 16f / 9f, 0.1f, 1000f);
Matrix4f view = new Matrix4f().lookAt(0, 2, 8, 0, 0, 0, 0, 1, 0);
Matrix4f viewProj = proj.mul(view, new Matrix4f());
```

### Quaternions

Purpose: rotate vectors directly via quaternion math.  
When to use: orientation updates, camera forward/right/up vectors, animation rotations.  
Result: transformed vector with fewer intermediate objects and no matrix construction.

```java
Quaternionf q = new Quaternionf().rotateY((float) java.lang.Math.toRadians(45));
Vector3f dir = q.transform(new Vector3f(0, 0, -1));
```

## 2) Affine and Transforms (`org.vectrix.affine`)

### Transformf (TRS)

Purpose: keep transform state in TRS form while still emitting matrix data for downstream systems.  
When to use: scene graph updates, instance transform upload, skinning precompute.  
Result: `Affine4f`/`Matrix4x3f` ready for culling, CPU transforms, or GPU packing.

```java
Transformf t = new Transformf();
t.translation.set(1, 2, 3);
t.rotation.rotateXYZ(0.1f, 0.2f, 0.3f);
t.scale.set(2, 2, 2);

Affine4f a = t.toAffine4fFast(new Affine4f());
Matrix4x3f m = t.toAffineMat4Fast(new Matrix4x3f());
```

### Compose parent/child transforms

Purpose: compose hierarchical transforms without converting to matrices first.  
When to use: parent-child scene graphs or skeleton joint propagation.  
Result: world-space TRS transform in `dest`.

```java
Transformf world = Transformf.compose(parent, local, new Transformf());
```

### Rigid inverse

Purpose: invert rigid transforms using specialized math (cheaper than full affine inverse).  
When to use: camera view matrix generation and rigid object inverse transforms.  
Result: inverse rigid transform with minimal overhead.

```java
RigidTransformf rigid = new RigidTransformf();
rigid.translation.set(0, 1, 0);
rigid.rotation.rotateY(1.2f);
Matrix4x3f inv = rigid.invertRigidFast(new RigidTransformf())
    .toAffineMat4Fast(new Matrix4x3f());
```

### Dual quaternion transform

Purpose: represent rigid motion as a dual quaternion and apply it to positions.  
When to use: dual-quaternion skinning or transform blending where matrix interpolation artifacts are undesirable.  
Result: transformed point using normalized dual-quaternion motion.

```java
DualQuatTransformf dq = new DualQuatTransformf();
RigidTransformf joint = new RigidTransformf().identity();
dq.setFromRigid(joint).normalize();
Vector3f skinned = dq.transformPosition(1, 0, 0, new Vector3f());
```

## 3) SoA Batch Containers (`org.vectrix.soa`)

### TransformSoA

Purpose: lay out transform components in SoA form for vectorized/batched processing.  
When to use: large instance counts, skinning inputs, broad transform kernels.  
Result: contiguous component arrays (`tx[]`, `qy[]`, etc.) with better cache behavior than AoS objects.

```java
int n = 1024;
TransformSoA soa = new TransformSoA(n);

soa.tx[0] = 1f; soa.ty[0] = 2f; soa.tz[0] = 3f;
soa.qw[0] = 1f; // identity rotation
soa.sx[0] = soa.sy[0] = soa.sz[0] = 1f;
```

### DualQuatSoA and AABBSoA

Purpose: provide batch-friendly containers for skinning and culling inputs.  
When to use: CPU skinning batches and AABB broad-phase/frustum tests.  
Result: normalized array storage consumable by kernel APIs.

```java
DualQuatSoA dqSoA = new DualQuatSoA(n);
AABBSoA bounds = new AABBSoA(n);
bounds.minX[0] = -1f; bounds.maxX[0] = 1f;
```

### Skinning kernels

Purpose: run LBS skinning over many vertices in one call.  
When to use: CPU fallback skinning, offline bake, or validation vs GPU paths.  
Result: skinned output positions in `outX/outY/outZ`.

```java
int count = 256;
float[] inX = new float[count], inY = new float[count], inZ = new float[count];
float[] outX = new float[count], outY = new float[count], outZ = new float[count];
int[] j0 = new int[count], j1 = new int[count], j2 = new int[count], j3 = new int[count];
float[] w0 = new float[count], w1 = new float[count], w2 = new float[count], w3 = new float[count];

SkinningKernels.skinLbs4SoA(
    soa, j0, j1, j2, j3, w0, w1, w2, w3,
    inX, inY, inZ,
    outX, outY, outZ,
    count
);
```

### Batch transform compose

Purpose: perform scene-wide transform composition in a single kernel call.  
When to use: per-frame world-transform updates for many entities/joints.  
Result: output SoA containing world transforms.

```java
TransformSoA parents = new TransformSoA(1024);
TransformSoA locals = new TransformSoA(1024);
TransformSoA world = new TransformSoA(1024);

TransformKernels.composeBatch(parents, locals, world, 1024);
```

## 4) SIMD (`org.vectrix.simd`)

Purpose: query runtime SIMD capability before selecting optimized paths.  
When to use: startup dispatch, diagnostics, or adaptive kernel routing.  
Result: backend selection info (`SCALAR` vs `VECTOR_API`) and preferred lane count.

```java
boolean simdAvailable = SimdSupport.isVectorApiAvailable();
int laneWidth = SimdSupport.preferredFloatLanes();
SimdSupport.Backend backend = SimdSupport.backend();
```

Use SIMD-aware kernels where provided; keep scalar fallbacks for portability.

## 5) Geometry and Culling (`org.vectrix.geometry`)

### Frustum planes and culling

Purpose: compute frustum planes and classify bounds against them.  
When to use: visibility culling before draw submission.  
Result: one of `OUTSIDE`, `INTERSECT`, or `INSIDE`.

```java
FrustumPlanes planes = new FrustumPlanes().set(viewProj, true);

AABBSoA bounds = new AABBSoA(1);
bounds.set(0, minX, minY, minZ, maxX, maxY, maxZ);
int[] out = new int[1];
CullingKernels.frustumCullAabbBatch(planes, bounds, out, 1);
int cls = out[0]; // FrustumIntersection.OUTSIDE / INTERSECT / INSIDE
```

### Ray/AABB intersection

Purpose: test ray overlap against many AABBs efficiently.  
When to use: picking, editor selection, or CPU-side collision pretests.  
Result: boolean hit/miss for each tested box.

```java
RayAabIntersection hit = new RayAabIntersection();
hit.set(rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
boolean intersects = hit.test(
    minX, minY, minZ,
    maxX, maxY, maxZ
);
```

### Mesh helpers

Purpose: centralize reusable geometry/mash helper routines.  
When to use: mesh import, preprocessing, procedural generation, validation.  
Result: reduced duplicate math code across higher-level systems.

```java
// See MeshMath / GeometryUtils for bounds, normal/tangent helpers, and geometry utilities.
```

## 6) GPU Utilities (`org.vectrix.gpu`)

### Half (float16)

Purpose: convert between float32 and binary16 storage formats.  
When to use: compact vertex/instance buffers or GPU interop paths requiring half precision.  
Result: bit-packed half value and recovered float.

```java
short h = Half.pack(0.75f);
float f = Half.unpack(h);
```

### Packed normalized formats

Purpose: compress normalized values into compact integer channels.  
When to use: vertex formats for normals/tangents/colors or packed material parameters.  
Result: one 32-bit packed integer suitable for transfer/storage.

```java
int packed = PackedNorm.packSnorm8x4(nx, ny, nz, nw);
```

### Octahedral normals

Purpose: encode/decode unit normals with octahedral mapping.  
When to use: normal storage in compact G-buffer/vertex formats.  
Result: smaller normal representation with recoverable direction.

```java
int oct = OctaNormal.encodeSnorm16(nx, ny, nz);
Vector3f decoded = OctaNormal.decodeSnorm16(oct, new Vector3f());
```

### Quaternion compression

Purpose: compress quaternions for storage/transmission and restore at runtime.  
When to use: animation tracks, instance data, networked transform replication.  
Result: packed 64-bit value and reconstructed quaternion.

```java
Quaternionf src = new Quaternionf().rotateXYZ(0.1f, 0.2f, 0.3f);
long packed = QuatCompression.packSmallest3(src);
Quaternionf q = QuatCompression.unpackSmallest3(packed, new Quaternionf());
```

### Vertex layout

Purpose: declare a strict vertex memory layout with overlap/alignment checks.  
When to use: renderer input setup and mesh format contracts.  
Result: validated layout descriptor with stride and named attributes.

```java
VertexLayout layout = VertexLayout.ofInterleaved(
    24,
    new VertexAttribute("position", 3, VertexAttributeFormat.FLOAT32, 0),
    new VertexAttribute("normal", 4, VertexAttributeFormat.SNORM16, 12),
    new VertexAttribute("uv", 2, VertexAttributeFormat.FLOAT16, 20)
);

int stride = layout.strideBytes;
```

### GPU transform packing layout

Purpose: serialize transforms into GPU-ready buffer memory with configurable encoding.  
When to use: instance upload pipelines and compact transform streams.  
Result: binary-packed transform records in a `ByteBuffer`.

```java
GpuTransformLayout layout = GpuTransformLayout.compactTRS();
ByteBuffer buffer = ByteBuffer.allocateDirect(layout.requiredBytes(1));
layout.write(buffer, 0, new Transformf().identity());
```

## 7) Experimental Modes (`org.vectrix.experimental`)

Purpose: control math behavior globally between throughput and reproducibility.  
When to use: `FAST` for production performance, `STRICT` for tests/replay determinism.  
Result: runtime dispatch picks corresponding reduction/kernel behavior.

```java
KernelConfig.setMathMode(MathMode.FAST);
float dotFast = Reduction.dot(aArray, bArray);

KernelConfig.setMathMode(MathMode.STRICT);
float dotStrict = Reduction.dot(aArray, bArray);
```

Direct mode-specific entry points:

Purpose: call mode-specific reductions directly without global state checks.  
When to use: tight loops where branchless explicit mode selection is preferred.  
Result: deterministic (`Strict`) or speed-focused (`Fast`) reduction result directly.

```java
float s0 = Reduction.sumFast(values);
float s1 = Reduction.sumStrict(values);
```

## 8) Sampling (`org.vectrix.sampling`)

Purpose: generate procedural sample distributions used in rendering and simulation.  
When to use: Poisson/blue-noise style placement, random sphere directions, sampling experiments.  
Result: callback receives generated samples for immediate consumption or storage.

```java
new PoissonSampling.Disk(1234L, 1.0f, 0.05f, 30, (x, y) -> {
    // consume sample
});

new UniformSampling.Sphere(1234L, 128, (x, y, z) -> {
    // consume sample
});
```

## Build and Bench Commands

```bash
mvn clean verify
mvn -Pexperimental test
mvn -Pbench -DskipTests package
java --add-modules=jdk.incubator.vector -jar target/benchmarks.jar
```

## Notes

- API is allocation-aware: prefer destination-parameter overloads in hot paths.
- Use `Transformf`/`Affine4f` for render transforms; convert to `Matrix4f` only when required.
- For large batches, prefer SoA containers and kernel APIs.
- For deterministic test/replay behavior, prefer `MathMode.STRICT`.
