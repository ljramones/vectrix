# Vectrix API Guide

This document is a practical API reference for Vectrix with usage examples by module.

## Quick Start

What this does: shows Vectrix’s allocation-aware style. Most hot-path methods have destination overloads so you can avoid temporary allocations.

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

What this does: demonstrates basic vector ops used constantly in rendering loops (cross, normalize, length).

```java
Vector3f v = new Vector3f(1, 2, 3);
Vector3f w = new Vector3f(4, 5, 6);
Vector3f out = new Vector3f();

v.cross(w, out);             // out = v x w
v.normalize(out);            // out = normalized v
float len = v.length();
```

### Matrices

What this does: builds a projection and view matrix, then combines them into a single view-projection matrix for transforms/culling.

```java
Matrix4f proj = new Matrix4f().perspective((float) java.lang.Math.toRadians(60.0), 16f / 9f, 0.1f, 1000f);
Matrix4f view = new Matrix4f().lookAt(0, 2, 8, 0, 0, 0, 0, 1, 0);
Matrix4f viewProj = proj.mul(view, new Matrix4f());
```

### Quaternions

What this does: rotates a direction vector without constructing an intermediate matrix.

```java
Quaternionf q = new Quaternionf().rotateY((float) java.lang.Math.toRadians(45));
Vector3f dir = q.transform(new Vector3f(0, 0, -1));
```

## 2) Affine and Transforms (`org.vectrix.affine`)

### Transformf (TRS)

What this does: uses first-class TRS state (translation/rotation/scale) and converts it to affine matrix forms used by render paths.

```java
Transformf t = new Transformf();
t.translation.set(1, 2, 3);
t.rotation.rotateXYZ(0.1f, 0.2f, 0.3f);
t.scale.set(2, 2, 2);

Affine4f a = t.toAffine4fFast(new Affine4f());
Matrix4x3f m = t.toAffineMat4Fast(new Matrix4x3f());
```

### Compose parent/child transforms

What this does: computes world transform from parent/local in TRS space.

```java
Transformf world = Transformf.compose(parent, local, new Transformf());
```

### Rigid inverse

What this does: inverts a rigid transform (rotation + translation) using the fast path; ideal for camera/view transforms.

```java
RigidTransformf rigid = new RigidTransformf();
rigid.translation.set(0, 1, 0);
rigid.rotation.rotateY(1.2f);
Matrix4x3f inv = rigid.invertRigidFast(new RigidTransformf())
    .toAffineMat4Fast(new Matrix4x3f());
```

### Dual quaternion transform

What this does: builds a dual-quaternion transform and applies it to a position. This is useful for rigid skinning and blend-friendly transforms.

```java
DualQuatTransformf dq = new DualQuatTransformf();
RigidTransformf joint = new RigidTransformf().identity();
dq.setFromRigid(joint).normalize();
Vector3f skinned = dq.transformPosition(1, 0, 0, new Vector3f());
```

## 3) SoA Batch Containers (`org.vectrix.soa`)

### TransformSoA

What this does: stores transforms in structure-of-arrays format for cache-friendly batch processing and GPU upload preparation.

```java
int n = 1024;
TransformSoA soa = new TransformSoA(n);

soa.tx[0] = 1f; soa.ty[0] = 2f; soa.tz[0] = 3f;
soa.qw[0] = 1f; // identity rotation
soa.sx[0] = soa.sy[0] = soa.sz[0] = 1f;
```

### DualQuatSoA and AABBSoA

What this does: sets up SoA storage for dual quaternions and bounds used by skinning/culling kernels.

```java
DualQuatSoA dqSoA = new DualQuatSoA(n);
AABBSoA bounds = new AABBSoA(n);
bounds.minX[0] = -1f; bounds.maxX[0] = 1f;
```

### Skinning kernels

What this does: runs linear blend skinning in batch form over SoA inputs and writes skinned positions into output arrays.

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

What this does: composes many parent/local transforms in one call; this is the typical world-transform update kernel.

```java
TransformSoA parents = new TransformSoA(1024);
TransformSoA locals = new TransformSoA(1024);
TransformSoA world = new TransformSoA(1024);

TransformKernels.composeBatch(parents, locals, world, 1024);
```

## 4) SIMD (`org.vectrix.simd`)

What this does: checks whether Vector API is available and what lane width the runtime selected.

```java
boolean simdAvailable = SimdSupport.isVectorApiAvailable();
int laneWidth = SimdSupport.preferredFloatLanes();
SimdSupport.Backend backend = SimdSupport.backend();
```

Use SIMD-aware kernels where provided; keep scalar fallbacks for portability.

## 5) Geometry and Culling (`org.vectrix.geometry`)

### Frustum planes and culling

What this does: extracts frustum planes from view-projection and classifies an AABB as outside/intersect/inside.

```java
FrustumPlanes planes = new FrustumPlanes().set(viewProj, true);

AABBSoA bounds = new AABBSoA(1);
bounds.set(0, minX, minY, minZ, maxX, maxY, maxZ);
int[] out = new int[1];
CullingKernels.frustumCullAabbBatch(planes, bounds, out, 1);
int cls = out[0]; // FrustumIntersection.OUTSIDE / INTERSECT / INSIDE
```

### Ray/AABB intersection

What this does: configures a ray once, then tests whether an axis-aligned bounding box is hit.

```java
RayAabIntersection hit = new RayAabIntersection();
hit.set(rayOriginX, rayOriginY, rayOriginZ, rayDirX, rayDirY, rayDirZ);
boolean intersects = hit.test(
    minX, minY, minZ,
    maxX, maxY, maxZ
);
```

### Mesh helpers

What this does: `MeshMath` and `GeometryUtils` provide common mesh-level utilities such as bounds and geometric helper operations.

```java
// See MeshMath / GeometryUtils for bounds, normal/tangent helpers, and geometry utilities.
```

## 6) GPU Utilities (`org.vectrix.gpu`)

### Half (float16)

What this does: converts between 32-bit float and 16-bit half representation for compact GPU buffers.

```java
short h = Half.pack(0.75f);
float f = Half.unpack(h);
```

### Packed normalized formats

What this does: packs four normalized scalar values into one 32-bit integer (common for normals/tangents/colors).

```java
int packed = PackedNorm.packSnorm8x4(nx, ny, nz, nw);
```

### Octahedral normals

What this does: encodes a unit normal into compact 2-component octahedral form and decodes it back.

```java
int oct = OctaNormal.encodeSnorm16(nx, ny, nz);
Vector3f decoded = OctaNormal.decodeSnorm16(oct, new Vector3f());
```

### Quaternion compression

What this does: compresses a unit quaternion using smallest-3 encoding and restores it for runtime use.

```java
Quaternionf src = new Quaternionf().rotateXYZ(0.1f, 0.2f, 0.3f);
long packed = QuatCompression.packSmallest3(src);
Quaternionf q = QuatCompression.unpackSmallest3(packed, new Quaternionf());
```

### Vertex layout

What this does: defines a validated interleaved vertex stream schema (attribute names, formats, offsets, stride).

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

What this does: writes TRS transform data into a GPU-ready binary layout (float or compact encodings).

```java
GpuTransformLayout layout = GpuTransformLayout.compactTRS();
ByteBuffer buffer = ByteBuffer.allocateDirect(layout.requiredBytes(1));
layout.write(buffer, 0, new Transformf().identity());
```

## 7) Experimental Modes (`org.vectrix.experimental`)

What this does: switches between throughput-oriented (`FAST`) and deterministic (`STRICT`) math behavior at runtime.

```java
KernelConfig.setMathMode(MathMode.FAST);
float dotFast = Reduction.dot(aArray, bArray);

KernelConfig.setMathMode(MathMode.STRICT);
float dotStrict = Reduction.dot(aArray, bArray);
```

Direct mode-specific entry points:

What this does: bypasses global mode dispatch when you explicitly want a fixed behavior.

```java
float s0 = Reduction.sumFast(values);
float s1 = Reduction.sumStrict(values);
```

## 8) Sampling (`org.vectrix.sampling`)

What this does: generates stochastic sample sets for rendering tasks such as dithering, importance patterns, and procedural placement.

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
