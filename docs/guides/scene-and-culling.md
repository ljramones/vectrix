# Scenes: transforms, bounds and culling

Each frame a renderer computes every object's world transform from its parents, bounds the object in world space,
and keeps only what the camera can see. This guide covers the classes for each step, one object at a time and
in batches. The packages are `affine`, `soa`, `core` and `geometry`.

The complete, tested program is [`SceneCulling`](../../examples/src/main/java/org/dynamisengine/vectrix/examples/SceneCulling.java).

## Which class for which job

| Task | Class | Notes |
|---|---|---|
| A transform as translation, rotation and scale | `Transformf` | Public fields `translation`, `rotation`, `scale`; easy to animate and interpolate |
| A general affine transform | `Matrix4x3f`, `Affine4f` | 12 floats; handle shear and non-uniform scale under rotation |
| A projection or any 4×4 matrix | `Matrix4f` | Column-major |
| Many transforms at once | `soa.TransformSoA`, `soa.TransformKernels` | Structure of arrays: one array per component |
| World bounds of a box | `Matrix4x3f.transformAab` | Exact box of the transformed corners |
| Bounds from points | `BoundingVolumesf` | Minimal spheres, PCA and minimal oriented boxes, k-DOPs |
| Test one object against the view | `FrustumIntersection` | Points, spheres, boxes; plane masks for hierarchies |
| Test many boxes against the view | `CullingKernels`, `FrustumPlanes`, `soa.AABBSoA` | Batch classification; SIMD in the `simd` jar |

## Transforms and hierarchies

A `Transformf` holds a translation, a rotation quaternion and a per-axis scale.
- `Transformf.compose(parent, local, dest)` gives the world transform of a child.
- `Transformf.interpolate` blends two transforms.
- `Transformf.invertRigidFast` inverts a transform that has no scale.
- `Transformf.toAffineMat4Fast` converts one to a `Matrix4x3f`, for bounds and for upload.

For many objects, keep the transforms in a `TransformSoA`, with separate arrays for each component.
`TransformKernels.composeBatch` composes `parents[i] · locals[i]` for a whole array in one call. The `simd` jar
runs it with the Vector API. Compose a hierarchy one level at a time: gather each level's parents from the
world transforms of the level above, then compose the level.

<!-- snippet: SceneCulling#hierarchy -->
```java
// Local transforms of the children, relative to the root: a grid centered on the root.
TransformSoA locals = new TransformSoA(n);
Transformf t = new Transformf();
for (int i = 0; i < n; i++) {
    t.identity();
    t.translation.set((i % SIDE - SIDE / 2) * SPACING, 0, (i / SIDE - SIDE / 2) * SPACING);
    locals.set(i, t);
}

// The root's world transform, repeated as the parent of every child. In a deeper hierarchy you compose one
// level at a time, gathering each level's parents from the level above.
Transformf root = new Transformf();
root.translation.set(0, 0, -70);
root.rotation.rotateY((float) Math.toRadians(20));
TransformSoA parents = new TransformSoA(n);
for (int i = 0; i < n; i++) {
    parents.set(i, root);
}

// world[i] = parents[i] * locals[i], in one batch call.
TransformSoA world = new TransformSoA(n);
TransformKernels.composeBatch(parents, locals, world, n);
```

`TransformSoA.set` and `TransformSoA.get` copy one element in or out. Batch kernels read and write the arrays
directly, so a frame loop does not allocate.

## World-space bounds

Transforming a box's eight corners and taking their minimum and maximum gives the tightest axis-aligned box. That
is `Matrix4x3f.transformAab`, which does it without forming the corners. For other shapes, `BoundingVolumesf` gives:
- `BoundingVolumesf.aabOfObb`, `BoundingVolumesf.aabOfCapsule` and `BoundingVolumesf.aabOfCylinder` for boxes of
  other shapes;
- `BoundingVolumesf.transformSphere` for a bounding sphere under a transform;
- `BoundingVolumesf.minimalSphere` and `BoundingVolumesf.pcaObb` to build bounds from vertex positions.

<!-- snippet: SceneCulling#bounds -->
```java
// World-space bounds of each unit cube: transform its local box by the world matrix.
AABBSoA bounds = new AABBSoA(n);
float[] wMin = new float[3 * n];
float[] wMax = new float[3 * n];
Matrix4x3f m = new Matrix4x3f();
Vector3f min = new Vector3f();
Vector3f max = new Vector3f();
for (int i = 0; i < n; i++) {
    world.get(i, t).toAffineMat4Fast(m);
    m.transformAab(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, min, max);
    bounds.set(i, min.x, min.y, min.z, max.x, max.y, max.z);
    wMin[3 * i] = min.x;
    wMin[3 * i + 1] = min.y;
    wMin[3 * i + 2] = min.z;
    wMax[3 * i] = max.x;
    wMax[3 * i + 1] = max.y;
    wMax[3 * i + 2] = max.z;
}
```

## Culling

A view frustum is six planes taken from the view-projection matrix. The near plane depends on the depth
convention the projection was built for, so always pass that `DepthRange`. Extracting with the wrong range puts
the near plane behind the camera for [0, 1] and reverse-Z projections. That is conservative, but it culls nothing
close to the camera.

- **Many boxes.** Store them in an `AABBSoA`. Extract the planes once per view with
  `FrustumPlanes.set(viewProj, normalize, depthRange)`. Then `CullingKernels.frustumCullAabbBatch` writes one
  classification per box: `FrustumIntersection.INSIDE`, `FrustumIntersection.INTERSECT` or
  `FrustumIntersection.OUTSIDE`. Scalar and SIMD paths classify every box identically, NaN and infinite bounds
  included.
- **One object at a time.**
  - `FrustumIntersection.set(viewProj, allowTestSpheres, depthRange)` prepares the view.
  - `FrustumIntersection.testAab`, `FrustumIntersection.testSphere` and `FrustumIntersection.testPoint` answer
    visible or not.
  - `FrustumIntersection.intersectAab` also says which plane rejected the box. With its `mask` argument, a
    bounding-volume hierarchy skips the planes a parent was already inside.

<!-- snippet: SceneCulling#cull -->
```java
// A reverse-Z camera with an infinite far plane: depth 1 at the near plane, 0 at infinity.
Matrix4f proj = Projectionsf.reverseZInfinite((float) Math.toRadians(60), 16f / 9f, 0.1f, new Matrix4f());
Matrix4f view = new Matrix4f().lookAt(0, 2, 0, 0, 2, -1, 0, 1, 0);
Matrix4f viewProj = proj.mul(view, new Matrix4f());

// Extract the planes for the depth range the projection was built with.
FrustumPlanes frustum = new FrustumPlanes().set(viewProj, false, DepthRange.REVERSED_ZERO_TO_ONE);
int[] classification = new int[n];
CullingKernels.frustumCullAabbBatch(frustum, bounds, classification, n);

int visible = 0;
for (int c : classification) {
    if (c != FrustumIntersection.OUTSIDE) {
        visible++;
    }
}
```

Both culling paths are conservative. A box reported outside is entirely outside, but a box near a frustum corner
may be reported as intersecting when it is not.

## Accuracy, allocation and threading

- `composeBatch`, `transformAab`, `FrustumPlanes.set` and `frustumCullAabbBatch` do not allocate.
- The batch kernels give the same bits on the scalar and SIMD paths, in FAST and in STRICT mode. STRICT also checks
  that every rotation is a unit quaternion.
- `FrustumPlanes` and `FrustumIntersection` are mutable. Prepare one per view, then share it read-only between
  culling threads, each writing its own result array.

## See also

- [Cameras and projections](camera-and-projection.md): building the projection and choosing a depth range.
- [Large worlds](large-worlds.md): camera-relative transforms when the scene is far from the origin.
- [Geometry and physics](geometry-and-physics.md): ray, box and triangle intersections, closest points.
- [Conventions](../conventions.md): column-major matrices and multiplication order.
- Sources: [`soa`](../../src/main/java/org/dynamisengine/vectrix/soa/),
  [`geometry`](../../src/main/java/org/dynamisengine/vectrix/geometry/),
  [`affine`](../../src/main/java/org/dynamisengine/vectrix/affine/).
