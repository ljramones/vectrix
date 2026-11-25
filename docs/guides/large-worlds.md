# Large worlds

A float has 24 bits of precision. Near the origin that is sub-millimetre. At 10 km it is a millimetre, and at
10⁹ m, a planet-scale or space game, it is 64 m: objects jitter and vertices snap. A double helps but runs out
too, at about 0.1 µm at 10⁹ m, and GPUs want floats. The package `world` solves both problems: positions that are
exact at any distance, and conversion to small camera-relative floats for rendering.

The tested program is [`LargeWorld`](../../examples/src/main/java/org/dynamisengine/vectrix/examples/LargeWorld.java).

## Which class for which job

| Task | Class | Notes |
|---|---|---|
| A position anywhere | `WorldPos` | Integer cell plus a double offset within it |
| The difference of two positions | `WorldPos.sub` | Correctly rounded, at any distance |
| An object's model matrix relative to the camera | `CameraRelative.modelMatrix` | Rotation, scale and exact translation |
| Many transforms relative to the camera | `CameraRelative.toCameraRelative` | Batch, 3×4 row-major output |
| Integer coordinates beyond `int` | `Vector3L` | Exact long arithmetic |

## Positions

A `WorldPos` stores an integer cell (`cell`, a `Vector3L`) and a double offset from the cell's center (`offset`).
Cells are `2^cellBits` metres across. With 1 km cells the offset's resolution is about 1e-13 m, the same at
10⁹ m as at the origin.
- `WorldPos.set(x, y, z)` splits a double position into cell and offset exactly.
- `WorldPos.add` moves the position, and `WorldPos.normalize` keeps the offset within its cell.
- Integer overflow of the cell index throws instead of wrapping.

<!-- snippet: LargeWorld#worldpos -->
```java
// 1024 m cells (2^10). The offset within a cell is a double, so resolution is about 1e-13 m everywhere.
WorldPos camera = new WorldPos(10).set(FAR_AWAY, 2.0, FAR_AWAY);
WorldPos object = new WorldPos(10).set(FAR_AWAY, 2.0, FAR_AWAY).add(0.123456789, 1.5, -3.25);

// The camera-relative position: exact difference, rounded once to float for the GPU.
Vector3f exact = object.sub(camera, new Vector3f());
```

`WorldPos.sub(other, Vector3f)` gives the difference as a float vector. This is the camera-relative position the
GPU needs, and it is within one float ulp of the exact difference. `WorldPos.sub(other, Vector3d)` gives it in
double. `WorldPos.toDouble` converts to one double per axis, which loses precision far from the origin.

## Rendering relative to the camera

Render with the camera at the origin. Every object's translation becomes its position minus the camera's,
computed exactly and then rounded to float. The values sent to the GPU stay small, and precision is highest near
the camera, where it is visible.

<!-- snippet: LargeWorld#model -->
```java
// The model matrix the renderer uploads: rotation and scale at the object's position, relative to the camera.
Matrix4x3f model = CameraRelative.modelMatrix(object, new Quaternionf().rotateY(0.5f), new Vector3f(1, 1, 1),
    camera, new Matrix4x3f());
```

For many objects, `CameraRelative.toCameraRelative` converts whole arrays of transforms. It accepts either
double-precision 3×4 matrices, or cells, offsets and float linear parts. It writes float 3×4 row-major matrices,
the layout of `PackedAffineArray`.

## Accuracy, allocation and threading

- `WorldPos.set` and `WorldPos.normalize` are exact, and `WorldPos.sub` rounds once. Relative positions are
  therefore correct to the last bit of the result type, whatever the distance from the origin.
- The conversion functions do not allocate.
- A `WorldPos` is a mutable object, like the vector types.

## See also

- [Cameras and projections](camera-and-projection.md): reverse-Z, the other half of precision at distance.
- [GPU data](gpu-data.md): the packed 3×4 layout and uploading transforms.
- Sources: [`world`](../../vectrix-core/src/main/java/org/dynamisengine/vectrix/world/).
