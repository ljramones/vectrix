# Getting data to the GPU

This guide covers the CPU side of a GPU upload: writing vectors and matrices into NIO buffers and off-heap memory,
laying out uniform and storage blocks, compressing vertex attributes, and packing per-instance transforms. Vectrix
never calls a graphics API. It produces the bytes, and you hand them to OpenGL, Vulkan or your engine's GPU layer.

The classes are in `gpu` (layouts, codecs, transform writers, meshlet cones), `affine` (the packed 3x4 affine
transform arrays), `world` (camera-relative transforms for large worlds) and the buffer methods of the `core`
types. The packed 3x4 layout, matrix storage order and other shared rules are defined once in
[conventions](../conventions.md).

## Which class for which job

| Task | Class | Notes |
|---|---|---|
| Write a vector or matrix into a `ByteBuffer` or `FloatBuffer` | `Matrix4f.get`, `Vector3f.get`, and the `get` methods of the other `core` types | Column-major, like GLSL. Never moves the buffer position. |
| Write a matrix as a 12-float row-major affine | `Matrix4f.get4x3Transposed`, `Matrix4x3f.getTransposed` | The packed 3x4 layout from [conventions](../conventions.md). |
| Write to off-heap memory by address | `Matrix4f.getToAddress`, `Vector4f.getToAddress` | No bounds checks. Needs `--enable-native-access`. |
| Lay out a uniform or storage block | `ShaderLayout` | std140, std430 and scalar rules; arrays, structs, matrices; emits GLSL. |
| Quick offsets for flat blocks of float, vec2-4 and mat4 | `StdLayout` | No arrays or structs. |
| Describe and validate an interleaved vertex format | `VertexLayout`, `VertexAttribute`, `VertexAttributeFormat` | Checks alignment, overlap and stride. |
| Half floats | `Half` | IEEE binary16, round to nearest even. |
| unorm/snorm 8 and 16 bit | `PackedNorm` | Vulkan's normalized-integer rules. |
| 10_10_10_2, RGB9E5, R11G11B10F, sRGB8, QTangent | `PackedFormats` | Bit-exact to the Vulkan formats. |
| Unit normals in 32 bits | `OctaNormal` | Octahedral mapping, two snorm16. |
| Rotations in 64 bits | `QuatCompression` | Smallest-three, 16 bits per component. |
| Instance transforms as packed 3x4 affine | `PackedAffineArray`, `PackedAffineKernels`, `GpuTransformWriteKernels` | The default instancing format. |
| Instance transforms as TRS structs | `GpuTransformLayout` | 40-byte float or 28-byte compact form, always little-endian. |
| Model matrices far from the origin | `CameraRelative` | Subtracts the camera before rounding to float. |
| Meshlet back-face cone culling | `MeshletCones` | Build, test and quantize normal cones. |

## Byte order

GPUs read little-endian data, and x86_64 and arm64 CPUs are little-endian too. A `ByteBuffer`
from `ByteBuffer.allocateDirect`, or from `MemorySegment.asByteBuffer`, starts in big-endian order, so set
`order(ByteOrder.nativeOrder())` on it before you write GPU data.

How each writer treats the order:

- The `get` methods of the `core` types write in the buffer's own order. A `FloatBuffer` made with
  `asFloatBuffer()` inherits the order of its `ByteBuffer`. Direct buffers in native order take the fastest path.
- `GpuTransformLayout` always writes little-endian bytes, whatever the buffer's order.
- The codecs (`Half`, `PackedNorm`, `PackedFormats`, `OctaNormal`, `QuatCompression`) return a `short`, `int` or
  `long`. Their bit layouts below describe that value. You store it with `ByteBuffer.putInt` and friends, which
  use the buffer's order, so a little-endian buffer gives the byte layout the GPU format expects.

## Writing vectors and matrices into buffers

Every `core` value type can write itself into an NIO buffer and read itself back:

| Method | Writes |
|---|---|
| `Vector3f.get(FloatBuffer)`, `Vector3f.get(int, FloatBuffer)` | x, y, z (12 bytes). The same pattern exists for every vector type. |
| `Matrix4f.get(FloatBuffer)` | 16 floats, column-major: GLSL `mat4` |
| `Matrix4f.getTransposed(FloatBuffer)` | 16 floats, row-major |
| `Matrix4f.get4x3Transposed(FloatBuffer)` | 12 floats, the packed 3x4 row-major affine |
| `Matrix4x3f.get(FloatBuffer)` | 12 floats, four columns of three |
| `Matrix4x3f.getTransposed(FloatBuffer)` | 12 floats, the packed 3x4 row-major affine |
| `Matrix4x3f.get4x4(FloatBuffer)` | 16 floats, with the implied last row (0, 0, 0, 1) |
| `Matrix3f.get3x4(FloatBuffer)` | 12 floats: three columns padded to four, which is a `mat3` under std140 and std430 |
| `Matrix4d.get(FloatBuffer)`, `Matrix4d.getFloats(ByteBuffer)`, `Vector3d.getf(ByteBuffer)` | double values rounded to float |
| `Matrix4f.set(FloatBuffer)`, `Vector3f.set(int, ByteBuffer)` | the reverse: read a value back |

Every method has a `ByteBuffer` overload and an overload with a leading `int index`. Watch for three things:

- **The buffer position never moves.** The plain overload writes at the current position and leaves it there.
  Advance it yourself, or use the `index` overloads and compute each offset.
- **The index unit depends on the buffer type.** For a `FloatBuffer` (or `DoubleBuffer`) `index` counts elements;
  for a `ByteBuffer` it counts bytes. `m.get(64, byteBuffer)` writes the second matrix of an array, and
  `m.get(16, floatBuffer)` does the same.
- **Quaternions have no buffer writer.** `Quaternionf` only writes itself as a matrix (`Quaternionf.getAsMatrix4f`).
  Store x, y, z, w with `putFloat`, or compress the rotation with `QuatCompression`.

All writes are bounds-checked: writing past the buffer's limit throws `IndexOutOfBoundsException`, and a read-only
buffer throws `ReadOnlyBufferException`, as plain NIO does. Heap buffers and slices work too.

### Memory backends

The buffer methods run on one of three backends, chosen once when the library loads:

| Backend | When | Notes |
|---|---|---|
| Foreign Function & Memory (FFM) | Default | Bounds-checked memory segments over direct, native-order buffers. Never touches `sun.misc.Unsafe`. |
| `sun.misc.Unsafe` | `-Dvectrix.forceUnsafe=true`, or `-Dvectrix.noffm=true` | The JDK prints its deprecation warning for `Unsafe` memory access. |
| Plain NIO | `-Dvectrix.noffm=true -Dvectrix.nounsafe=true` | `put`/`get` calls on the buffer. |

Heap buffers and buffers that are not in native order use plain NIO calls on every backend. Each property can also
be given with the `joml.` prefix (`-Djoml.forceUnsafe=true`); the `vectrix.` name wins when both are set.
`-Dvectrix.forceFfm=true` makes startup fail if FFM is unavailable, and contradictory settings such as
`forceUnsafe` together with `forceFfm` throw a `ConfigurationException` when the library initializes.

Every backend writes the same bytes, so the choice changes speed only, never results. FFM is the default because it
is bounds-checked and uses no deprecated API. On arm64 it matches `Unsafe` in loops of transfers; on x86_64 it is
slower, and `-Dvectrix.forceUnsafe=true` recovers the difference where it matters. The measurements are in
[benchmarks](../benchmarks.md) and the trade-off in [performance](../performance.md).

### MemorySegments and raw addresses

There are no `MemorySegment` overloads. You have two ways to write into a segment:

1. **As a buffer (bounds-checked).** Take `segment.asByteBuffer()`, set native order, and use the buffer methods
   above. `MemorySegment.asByteBuffer` only works for segments under 2 GiB; take an `asSlice` of a larger one
   first.
2. **By address (unchecked).** Pass `segment.address() + offset` to `Matrix4f.getToAddress` or
   `Vector3f.getToAddress`, and read back with `Matrix4f.setFromAddress`. Matrices also have
   `Matrix4f.getTransposedToAddress` and `Matrix4f.setTransposedFromAddress`. The value is written in native byte
   order with the same element order as the buffer methods. Nothing checks the address: a wrong address, or a
   segment whose arena has been closed, can crash the JVM.

The raw-address methods use FFM by default, which needs native access. Start the JVM with
`--enable-native-access=org.dynamisengine.vectrix` when Vectrix is on the module path, or
`--enable-native-access=ALL-UNNAMED` on the class path. Without the flag the JDK prints a one-time warning about a
restricted method. If native access is denied (`--illegal-native-access=deny`) the methods fall back to
`sun.misc.Unsafe`, and when that is disabled too they throw `UnsupportedOperationException`. A program that never
calls them never requests native access.

## Uniform and storage block layouts

A shader reads a uniform or storage block at byte offsets fixed by its layout rules, and the CPU writer must use
the same offsets. The rules differ mainly in padding:

- **std140** (uniform blocks): arrays and structs are aligned to 16 bytes, so a `float[4]` takes 64 bytes.
- **std430** (storage blocks): no rounding to 16 bytes, so a `float[4]` takes 16 bytes.
- **scalar** (`VK_EXT_scalar_block_layout`): every member is aligned to its component size, with no padding.

Under std140 and std430 a `vec3` is aligned to 16 bytes but is only 12 bytes long, so a following `float` fills its
fourth slot.

Use `ShaderLayout` for real blocks. You describe the block with `ShaderLayout.struct`, `ShaderLayout.member` and the
type helpers (`ShaderLayout.vec`, `ShaderLayout.mat`, `ShaderLayout.array`, `ShaderLayout.rowMajor`,
`ShaderLayout.FLOAT`, `ShaderLayout.INT`, ...), then call `ShaderLayout.layout` with one of the
`ShaderLayout.Rules`. The returned `ShaderLayout.Block` gives:

- `offset("lights[3].color")`: the byte offset of any member path, including array elements and nested structs;
- `arrayStride(path)` and `matrixStride(path)`;
- `size()` (as SPIR-V reflection reports it), `paddedSize()` (the stride of an array of these blocks) and
  `alignment()`;
- `entries()`: every member with its offset, size and strides;
- `toGlsl(storage, blockName, instanceName, binding)`: the GLSL declaration, so the shader and the Java writer
  share one definition. It adds `#extension GL_EXT_scalar_block_layout : require` where the rules need it.

The test suite checks `ShaderLayout` against glslang's own layout on randomly generated blocks. It allocates, so
build layouts at startup and keep the offsets.

`StdLayout` is a small helper for flat blocks of `StdLayout.Type` members (`FLOAT`, `VEC2`, `VEC3`, `VEC4`,
`MAT4`): `StdLayout.offsetsStd140` and `StdLayout.offsetsStd430` return the member offsets, and
`StdLayout.structSizeStd140` and `StdLayout.structSizeStd430` the struct size. It has no arrays or nested structs;
use `ShaderLayout` for those.

## Vertex layouts

`VertexLayout` describes one interleaved vertex stream: a stride in bytes and a list of `VertexAttribute`s. Each
attribute has a name, a component count (1 to 4), a `VertexAttributeFormat` (`FLOAT32`, `FLOAT16`, `UNORM8`,
`SNORM16`, `UINT32`, ...) and a byte offset. Build one with `VertexLayout.ofInterleaved` or the constructor. It
throws `IllegalArgumentException` when an attribute is not aligned to its component size, runs past the stride,
overlaps another attribute, or repeats a name.

The layout is a descriptor: it validates offsets and gives you `VertexLayout.attribute` (by name, or `null`),
`VertexAttribute.byteSize` and `VertexAttributeFormat.normalized`, which your GPU layer maps to the API's vertex
input description. You write the vertex bytes yourself, using the codecs below for compressed attributes.

## Compact encodings

Smaller vertex and instance data means less memory traffic. Each codec below has a matching GPU-side decoder,
usually a built-in format or GLSL function.

| Data | Encode | Decode | Size | GPU side |
|---|---|---|---|---|
| float to half | `Half.pack` | `Half.unpack` | 16 bits | `VK_FORMAT_R16_SFLOAT`, `unpackHalf2x16` |
| [0, 1] to unorm | `PackedNorm.packUnorm8`, `PackedNorm.packUnorm16`, `PackedNorm.packUnorm8x4` | `PackedNorm.unpackUnorm8`, `PackedNorm.unpackUnorm16` | 8 or 16 bits | `_UNORM` formats, `unpackUnorm4x8` |
| [-1, 1] to snorm | `PackedNorm.packSnorm8`, `PackedNorm.packSnorm16`, `PackedNorm.packSnorm8x4` | `PackedNorm.unpackSnorm8`, `PackedNorm.unpackSnorm16` | 8 or 16 bits | `_SNORM` formats, `unpackSnorm4x8` |
| RGBA, 10 bits per color | `PackedFormats.packUnorm1010102`, `PackedFormats.packSnorm1010102` | `PackedFormats.unpackUnorm1010102`, `PackedFormats.unpackSnorm1010102` | 32 bits | `A2B10G10R10_UNORM_PACK32` / `_SNORM_PACK32` |
| HDR color, shared exponent | `PackedFormats.packRgb9e5` | `PackedFormats.unpackRgb9e5` | 32 bits | `E5B9G9R9_UFLOAT_PACK32` |
| HDR color, small floats | `PackedFormats.packR11G11B10F` | `PackedFormats.unpackR11G11B10F` | 32 bits | `B10G11R11_UFLOAT_PACK32` |
| linear to sRGB 8-bit | `PackedFormats.encodeSrgb8` | `PackedFormats.decodeSrgb8` | 8 bits | `_SRGB` formats |
| unit normal | `OctaNormal.encodeSnorm16` | `OctaNormal.decodeSnorm16` | 32 bits | `unpackSnorm2x16` plus the octahedral unfold |
| tangent frame | `PackedFormats.packQTangent` | `PackedFormats.unpackQTangent` | 64 bits | `R16G16B16A16_SNORM` plus a quaternion rotate |
| rotation | `QuatCompression.packSmallest3` | `QuatCompression.unpackSmallest3` | 64 bits (50 used) | custom decode |

Rounding and special values:

- `Half.pack` rounds to nearest, ties to even, the same as `Float.floatToFloat16` and GPU conversion. Values too
  large become infinity; NaN stays NaN.
- `PackedNorm` encodes `round(clamp(v) * (2^b - 1))` for unorm and `round(clamp(v) * (2^(b-1) - 1))` for snorm,
  with ties toward positive infinity, computed exactly in double. NaN encodes as 0. Decoding is the exact quotient
  (`c / 255` for unorm8, `c / 127` for snorm8), clamped to -1 for snorm, so the most negative code also decodes
  to -1.
- `PackedFormats.packRgb9e5` follows the Vulkan specification's shared-exponent algorithm. It clamps components to
  [0, 65408], NaN to 0.
- `PackedFormats.packR11G11B10F` rounds to nearest even, maps negative values to 0, keeps NaN, and rounds values
  beyond the largest finite value (65024 for 11 bits, 64512 for 10 bits) to infinity.
- `PackedFormats.encodeSrgb8` uses the exact piecewise sRGB curve, so it round-trips through an `_SRGB` image; a
  `pow(x, 1/2.2)` approximation does not.

### Bit layouts

Codes are stored with the first component in the low bits, which matches Vulkan's packed formats and GLSL's
`unpack*` functions:

```text
packUnorm8x4 / packSnorm8x4    x: bits 0-7    y: 8-15    z: 16-23    w: 24-31
packUnorm1010102               r: bits 0-9    g: 10-19   b: 20-29    a: 30-31
packRgb9e5                     r: bits 0-8    g: 9-17    b: 18-26    shared exponent: 27-31
packR11G11B10F                 r: bits 0-10   g: 11-21   b: 22-31
OctaNormal.encodeSnorm16       u: bits 0-15   v: 16-31                   (snorm16 each)
packQTangent                   x: bits 0-15   y: 16-31   z: 32-47    w: 48-63   (snorm16 each)
packSmallest3                  c2: bits 0-15  c1: 16-31  c0: 32-47   index of the dropped component: 48-49
```

### Normals and tangent frames

`OctaNormal.encodeSnorm16` maps a unit normal onto an octahedron and unfolds it into a square, giving two snorm16
values. The error is spread evenly over the sphere, unlike spherical angles, which waste precision at the poles.
`OctaNormal.decodeSnorm16` inverts the mapping and renormalizes.

`PackedFormats.qTangent` encodes a whole tangent frame (normal, tangent and bitangent sign) as one unit quaternion.
The bitangent sign is the glTF `tangent.w`, stored in the sign of the quaternion's w. The tangent is first made
orthogonal to the normal. `PackedFormats.packQTangent` stores the quaternion as four snorm16 values, and
`PackedFormats.unpackQTangent` returns the unit normal and tangent and the bitangent sign. The encoder allocates and
is meant for asset processing; decoding allocates nothing.

### Quaternions

`QuatCompression.packSmallest3` normalizes the quaternion, drops its largest-magnitude component (flipping the sign
of the whole quaternion so that component is positive), and stores the other three, in x, y, z, w order, as snorm16
of `c * sqrt(2)`. The dropped component is rebuilt from the unit-length constraint. Stored as two 32-bit words, low
word first (a GLSL `uvec2`), word 0 holds `c1 << 16 | c2` and word 1 holds `index << 16 | c0`, with index 0 to 3
meaning x, y, z, w. `QuatCompression.angularErrorDegrees` measures the rotation error between an original and a
decoded quaternion, so you can check the precision on your own data.

## Instance transforms

### Packed 3x4 affine (the default)

An instanced draw needs one model transform per instance. The default format is the packed 3x4 affine: 12 floats
per instance, the three rows of the upper 3x4 of the model matrix. It is 25 % smaller than a `mat4`, it is the
order of Vulkan's `VkTransformMatrixKHR`, and a shader applies it as three dot products with `vec4(p, 1)` on a
`vec4 rows[3]`. The element order and the mapping to `Matrix4f` accessors are in the
[packed 3x4 affine section of conventions](../conventions.md); use that table whenever you move data between the
two forms.

| Class or method | Does |
|---|---|
| `PackedAffineArray` | A `float[]` of 12-float transforms. `PackedAffineArray.set` sets one from its rows; `PackedAffineArray.raw()` exposes the array; `PackedAffineArray.offsetOf` gives the float offset of an index. It can also wrap an existing array. |
| `PackedAffineKernels.trsToPackedAffineBatch` | `Transformf[]` (translation, rotation, scale) to packed affines, as `T * R * S`, the same matrix as `Matrix4f.translationRotateScale`. |
| `PackedAffineKernels.quatTranslationToPackedAffineBatch` | Rotation and translation arrays, unit scale. |
| `PackedAffineKernels.matrix4fToPackedAffineBatch` | `Matrix4f[]` to packed affines. |
| `PackedAffineKernels.transformAabbPackedAffineBatch` | Transforms local AABBs (SoA min/max arrays) to world AABBs. |
| `PackedAffineKernels.uploadPackedAffine`, `GpuTransformWriteKernels.writePackedAffine` | Copy the transforms, in a draw order, into a contiguous upload array. |
| `GpuTransformWriteKernels.writeMatrix4fFromPackedAffine` | Expand to 16-float column-major `mat4` for shaders that need full matrices. |
| `Matrix4f.get4x3Transposed`, `Matrix4x3f.getTransposed` | Write a single matrix in the packed layout straight into a buffer. |

The batch kernels take an `order` array of instance indices. The upload writers gather: output slot `i` receives
transform `order[i]`, so the upload array is in draw order. `PackedAffineKernels.transformAabbPackedAffineBatch`
only uses `order` for its traversal; it writes each result back at the instance's own index. Pass `0, 1, 2, ...`
when you have no draw order. The upload kernels fill a `float[]`, from index 0 or from an output offset; copy it
into your mapped buffer with `FloatBuffer.put(float[], int, int)`. Every `PackedAffineKernels` method checks its
counts, array sizes, `order` indices and offsets before writing, and throws `IllegalArgumentException` on a bad one.

The `...Chunked` variants run the same kernel over consecutive sub-ranges, as `BatchChunks` splits them. They help
you schedule work; they are not faster on their own.

`InstanceSubmissionPipeline` chains the whole per-frame path in one call: skinning, TRS to packed affine, bounds
and upload. Its `InstanceSubmissionPipeline.Path` selects the packed-affine path or a `Matrix4f` fallback (also
forced by the `vectrix.runtime.instanceSubmission.forceMatrix` system property) for comparing the two. It reuses
internal scratch arrays, growing them when a frame has more instances than before, so use one instance per thread.

### TRS structs

`GpuTransformLayout` writes a transform as separate translation, rotation and scale instead of a matrix. Two layouts
are predefined:

| Layout | Stride | GLSL (`layout(scalar)`) |
|---|---|---|
| `GpuTransformLayout.floatTRS` | 40 bytes | `struct { vec3 t; vec4 q; vec3 s; }` |
| `GpuTransformLayout.compactTRS` | 28 bytes | `struct { vec3 t; uvec2 q; uint16_t s[3]; }`: smallest-three rotation, half-float scale |

Neither fits std430, where the `vec4` would need 16-byte alignment, so the shader needs the scalar block layout.
`GpuTransformLayout.write` writes instance `index` at byte offset `index * strideBytes`. It uses the float form
only when the layout has both `FLOAT4` rotation and `FLOAT3` scale; any other combination is written in the compact
form (smallest-three rotation and half-float scale). `GpuTransformLayout.writeFloatTrs` and
`GpuTransformLayout.writeCompactTrs` force one form. The bytes are
always little-endian. `GpuTransformLayout.requiredBytes` gives the buffer size and throws `ArithmeticException` if
it overflows an `int`. `GpuTransformWriteKernels.writeFloatTrs` and `GpuTransformWriteKernels.writeCompactTrs`
write a whole array in draw order.

The shader has to rebuild a matrix from TRS, and the compact form also has to decode the rotation, so prefer the
packed affine unless memory size is the constraint.

### Large worlds

Far from the origin a `float` translation loses precision: at 10⁹ m its steps are 64 m. `CameraRelative`
subtracts the camera position before rounding to float, so nearby objects keep sub-millimetre placement wherever
the camera is. `CameraRelative.toCameraRelative` converts arrays of double-precision packed affines, or world-cell
positions, to camera-relative packed affines; `CameraRelative.modelMatrix` builds one model matrix:

<!-- snippet: LargeWorld#model -->
```java
// The model matrix the renderer uploads: rotation and scale at the object's position, relative to the camera.
Matrix4x3f model = CameraRelative.modelMatrix(object, new Quaternionf().rotateY(0.5f), new Vector3f(1, 1, 1),
    camera, new Matrix4x3f());
```

Upload it with `Matrix4x3f.getTransposed` for the packed layout. The view matrix then contains only the camera's
rotation, not its position.

## Meshlet cone culling

A meshlet (cluster) whose triangles all face away from the camera can be skipped before rasterization. The test
uses a normal cone: an axis and the cosine of a half angle such that every triangle normal of the cluster lies
inside the cone.

1. At build time, `MeshletCones.normalCone` computes the tightest cone of a range of triangles from a position array
   and an index array. It writes the axis to `xyz` and the cosine to `w` of a `Vector4f`. A cluster whose normals do
   not fit in an open hemisphere gets a cosine of -1, which is never culled. This method allocates.
2. `MeshletCones.quantize` packs the cone into 32 bits (snorm8 axis in bytes 0 to 2, snorm8 cosine in byte 3),
   widening it so the packed cone still contains every normal. `MeshletCones.dequantize` unpacks it.
3. `MeshletCones.isBackfacing` tests a cluster's bounding sphere and cone against a camera position. The test is
   exact: it never culls a cluster with a visible triangle. Port it to your task or mesh shader, or use it for
   CPU-side culling.

## Accuracy, allocation and threading

- `PackedFormats` and `PackedNorm` are bit-exact to their Vulkan definitions, and `Half.pack` matches
  `Float.floatToFloat16`. `ShaderLayout` is checked against glslang. Normal cones, quantized or not, are
  conservative.
- The codecs allocate nothing, except the QTangent encoders (`PackedFormats.qTangent`,
  `PackedFormats.packQTangent`). `MeshletCones.normalCone`, `ShaderLayout` and the `StdLayout` offset arrays also
  allocate. All of these are meant for setup and asset processing, not per-frame work.
- The batch kernels write only into the arrays and buffers you pass. They are static and keep no state, so threads
  can run them on disjoint output ranges. NIO buffers themselves are not thread-safe: give each thread its own
  buffer or slice. `InstanceSubmissionPipeline` keeps scratch state; use one per thread.
- `GpuTransformLayout`, `ShaderLayout.Block` and `VertexLayout` are immutable once built.

## See also

- [Conventions](../conventions.md): the packed 3x4 affine layout, column-major matrices, `dest` parameters.
- [Performance](../performance.md): why packed affine is the default bulk format, and the memory-backend trade-off.
- [Benchmarks](../benchmarks.md): measured upload, bounds and memory-backend costs.
- [Determinism](../determinism.md): the memory backend never changes output bits.
- Examples: [`LargeWorld`](../../examples/src/main/java/org/dynamisengine/vectrix/examples/LargeWorld.java) and the
  [examples README](../../examples/README.md).
- Source: [`gpu`](../../src/main/java/org/dynamisengine/vectrix/gpu/),
  [`affine`](../../src/main/java/org/dynamisengine/vectrix/affine/),
  [`world`](../../src/main/java/org/dynamisengine/vectrix/world/).
