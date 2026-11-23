# Cameras and projections

This guide covers projection matrices, depth conventions and depth precision. It also covers the camera
calculations a renderer needs around them: TAA jitter, oblique clipping, shadow cascades, clustered lighting
grids and physical lens parameters. The package is `camera`, with `Matrix4f` from `core`.

The tested program is [`CameraDepth`](../../examples/src/main/java/org/dynamisengine/vectrix/examples/CameraDepth.java).

## Which class for which job

| Task | Class | Notes |
|---|---|---|
| Perspective projection for any depth convention | `Projectionsf.perspective` | Finite or infinite far plane |
| Reverse-Z with an infinite far plane | `Projectionsf.reverseZInfinite` | The best depth precision |
| View matrix | `Matrix4f.lookAt`, `Matrix4f.lookAlong` | Right-handed, camera looks along −Z |
| Depth buffer value ↔ view distance | `Projectionsf.ndcDepth`, `Projectionsf.linearDepth` | Exact inverses; for an oblique projection pass `ndcDepth` the view-space point |
| Read the planes back from a matrix | `Projectionsf.nearPlane`, `Projectionsf.farPlane` | |
| OpenGL-style matrix on Vulkan | `Projectionsf.vulkanClipCorrection`, `Projectionsf.flipY` | |
| TAA jitter | `Projectionsf.haltonJitter`, `Projectionsf.applyJitter` | Works for any projection |
| Mirrors and portals | `Projectionsf.obliqueNearPlane` | Replaces the near plane |
| Cascaded shadow maps | `ShadowCascadesf` | Split distances, bounding spheres, texel-stable light matrices |
| Clustered (froxel) lighting | `ClusterGridf` | Exponential slices, cluster bounds, light tests |
| Lens parameters | `PhysicalCameraf` | Focal length, circle of confusion, depth of field |

Every class has a double twin (`Projectionsd`, `ShadowCascadesd`, ...).

## Depth conventions

A projection maps the near and far planes to two depth values, and graphics APIs disagree on which values. Pass
`core.DepthRange` to every call that builds a projection, reads its planes or extracts a frustum:

| `DepthRange` | Near → far | Used by |
|---|---|---|
| `NEGATIVE_ONE_TO_ONE` | −1 → 1 | OpenGL by default |
| `ZERO_TO_ONE` | 0 → 1 | Vulkan, Direct3D, Metal, OpenGL with `glClipControl` |
| `REVERSED_ZERO_TO_ONE` | 1 → 0 | Reverse-Z on any [0, 1] API |

Reverse-Z puts distant depths near 0, where floating-point numbers are densest, which cancels most of the
perspective loss of precision. With a float depth buffer it resolves geometry far better than the other two. It
also allows an infinite far plane at no cost. Use it unless something else forces a choice.

<!-- snippet: CameraDepth#projection -->
```java
// The same camera in each convention. Reverse-Z also allows an infinite far plane
// (Projectionsf.reverseZInfinite); a finite one keeps the comparison fair here.
Matrix4f proj = Projectionsf.perspective((float) Math.toRadians(60), 16f / 9f, NEAR, FAR, range,
    new Matrix4f());

// Depth written to the depth buffer for a point DISTANCE metres in front of the camera, and back.
float depth = Projectionsf.ndcDepth(proj, DISTANCE);
float back = Projectionsf.linearDepth(depth, NEAR, FAR, range);

// Floats are dense near 0 and sparse near 1. Reverse-Z puts distant depths near 0, where a float can
// still tell 5000.00 m from 5000.01 m; the other conventions put them near 1, where it cannot.
boolean resolves = Projectionsf.ndcDepth(proj, DISTANCE + 0.01f) != depth;
```

At 5 km from a camera with a 0.1 m near plane, only reverse-Z can tell two surfaces 1 cm apart. The other
conventions also lose several metres when the depth is converted back to a distance. Run the example to see the
numbers.

The matrix methods `Matrix4f.perspective` and `Matrix4f.setPerspective` are the JOML originals. They take a
`zZeroToOne` flag instead of a range and cannot express reverse-Z, so prefer `Projectionsf.perspective` in new code.

## Jitter, clip corrections and oblique clipping

- **TAA jitter.** `Projectionsf.haltonJitter(frame, sampleCount, width, height, dest)` gives a sub-pixel offset
  in NDC units. `Projectionsf.applyJitter` shifts any projection by it: perspective, orthographic, off-center or
  oblique.
- **Vulkan clip correction.** `Projectionsf.vulkanClipCorrection` converts an OpenGL-convention matrix to Vulkan's
  clip space. `Projectionsf.flipY` negates clip-space y, which stays correct for jittered and oblique
  projections.
- **Oblique clipping.** `Projectionsf.obliqueNearPlane` replaces the near plane with a view-space plane, for
  mirror and portal rendering. The far plane tilts to match.

## Shadow cascades

- **Splits.** `ShadowCascadesf.practicalSplits` divides a view range into cascades, blending logarithmic and
  uniform splits with `lambda`.
- **Bounds.** `ShadowCascadesf.sliceSphere` bounds each slice with a sphere.
- **Light matrices.** `ShadowCascadesf.stableLightMatrices` builds the light's view and orthographic projection
  for a sphere. It snaps them to whole shadow-map texels, so shadows do not shimmer as the camera moves.

<!-- snippet: CameraDepth#cascades -->
```java
// Four shadow cascades over the first 200 m, half logarithmic and half uniform (lambda = 0.5).
float[] splits = ShadowCascadesf.practicalSplits(NEAR, 200f, 4, 0.5f, new float[5]);
```

## Clustered lighting

`ClusterGridf` divides the view into a screen grid times exponential depth slices:
- `ClusterGridf.sliceIndex` gives a depth's slice, and `ClusterGridf.sliceDepth` a slice's near distance.
- `ClusterGridf.clusterOf` finds the cluster of a view-space position.
- `ClusterGridf.clusterAabb` gives a cluster's view-space box.
- `ClusterGridf.sphereIntersectsAabb` and `ClusterGridf.coneIntersectsSphere` are conservative light tests for
  building per-cluster light lists.

## Physical camera

`PhysicalCameraf` converts between field of view and focal length for a sensor size. It also computes the circle
of confusion, the hyperfocal distance and the near and far limits of the depth of field, for depth-of-field
effects. Use one unit throughout; in metres, a 50 mm lens is `0.05`. Exposure from aperture, shutter speed and
ISO is in `color.Exposuref`.

## Accuracy, allocation and threading

- The projection builders evaluate in double and round once, so the matrices and their plane distances are
  accurate to within float rounding, including for infinite far planes.
- `ndcDepth` and `linearDepth` are exact inverses up to that rounding.
- No call allocates. The classes are static and stateless.

## See also

- [Scenes: transforms, bounds and culling](scene-and-culling.md): frustum extraction with the same `DepthRange`.
- [Large worlds](large-worlds.md): camera-relative rendering far from the origin.
- [Conventions](../conventions.md): right-handed coordinates, radians, column-major matrices.
- Sources: [`camera`](../../src/main/java/org/dynamisengine/vectrix/camera/).
