# Conventions

These rules hold across the whole library unless a class's Javadoc says otherwise. The guides assume them.

## Types and names

**Precision suffix.** The last letter of a type names its element type:

| Suffix | Element | Examples |
|---|---|---|
| `f` | `float` | `Vector3f`, `Matrix4f`, `Quaternionf`, `Projectionsf`, `Gemmf` |
| `d` | `double` | `Vector3d`, `Matrix4d`, `Projectionsd`, `Gemmd` |
| `i` | `int` | `Vector3i` |
| `L` | `long` | `Vector3L` |

A few packages work in one precision and leave the suffix off: classes in `dsp` are double (with float overloads
where the Javadoc says so), and the structure-of-arrays containers in `soa` are float unless they end in `d`.
In `Conv2d`, `Pool2d`, `ComplexFft2f` and `RealFft2d`, "2" means two-dimensional; the precision is still the last
letter (`ComplexFft2d` is the double 2D transform, while `Conv2d` and `Pool2d` are float).

**Three forms of each math type.**
- The mutable class (`Matrix4f`) has fluent methods that change `this` and return it.
- The read-only interface, named with a `c` suffix (`Matrix4fc`), has the same queries and no setters. Accept it
  as a parameter when your code must not change the caller's object. It is a view, not a copy.
- The stack class (`Matrix4fStack`) adds `pushMatrix`/`popMatrix` for nested transforms.

Static utility classes hold functions on plain arrays or on several types: `Projectionsf`, `Intersectionf`,
`ShProjection`, `Gemmf`.

## Results go into a destination you pass

Methods that compute a new value take a `dest` parameter, write the result there and return it. They leave
`this` and the other arguments unchanged. The same method without `dest` changes `this` instead.

<!-- snippet: FirstSteps#fluent -->
```java
// Mutable types have fluent methods that change `this` and return it.
Matrix4f model = new Matrix4f()
    .translate(10, 0, 0)
    .rotateY((float) Math.toRadians(90))
    .scale(2);

// Methods with a `dest` parameter write the result there and leave the inputs alone, so a frame loop can
// reuse its objects instead of allocating.
Vector3f moved = model.transformPosition(new Vector3f(1, 0, 0), new Vector3f());
```

- **Aliasing is allowed.** `dest` may be `this` or any argument: `a.mul(b, a)` is correct. A test runs every
  destination method both ways and requires identical bits.
- **No hidden allocation.** Hot-path methods do not allocate. Reuse your `dest` objects and scratch arrays across
  frames. A test measures bytes allocated per call for the hot-path APIs.

## Arrays, offsets, scratch and plans

The batch and signal APIs (`soa`, `fft`, `dsp`, `blas`, `nn`, `world`, `gpu`) work on caller-owned arrays:

- Each array argument comes with an offset, and the call takes an element count. Matrices in these packages are
  row-major with an explicit row stride (`lda`, `ldb`, ...).
- **Scratch.** Calls that need working memory take a scratch array plus offset. Its size comes from a
  `scratchSize(...)` method (`RealFftd.scratchSize`, `Kalman.scratchSize`, `Gemmf.scratchSize`). Allocate it once
  and reuse it.
- **Plans.** Transforms and convolvers are created once for a size (`RealFftd.plan`, `BlockConvolverd.plan`).
  A plan is immutable: share it across threads, giving each thread its own scratch and state.
- **Up-front checks.** Methods that take offsets check them, and the array sizes, before touching any element. A
  bad argument throws `IllegalArgumentException` and leaves your arrays unchanged.
- **In-place.** Whether an output array may be an input array is stated per method, for example "`in` may be
  `out`".

Some older batch APIs (in `animation`, `ik` and `soa`) take a count but no offsets and start at index 0. The `soa`
kernels still check sizes and indices up front; the `animation` and `ik` ones rely on Java's array bounds checks, so
an error appears there as `ArrayIndexOutOfBoundsException`.

## Coordinates, angles and matrices

- **Right-handed coordinates.** Cameras look along −Z with +Y up (`Matrix4f.lookAt`, `Projectionsf`).
- **Angles are in radians** everywhere, including fields of view.
- **Quaternions** are `(x, y, z, w)`, with `w` the scalar part. Rotation methods expect unit quaternions.
- **`Matrix4f` and the other square matrices are column-major**, like GLSL `mat4`. Accessors name the column
  first: `m.m30()` is column 3, row 0, the x translation.
- **Products follow the order you read them.** `a.mul(b)` is `a · b`, applied to a vector as `a · (b · v)`.
  Fluent calls post-multiply, so `new Matrix4f().translate(t).rotateY(r)` rotates a vector first, then
  translates it.
- **Row-major exceptions:**
  - the packed 3×4 affine below;
  - the matrices of `blas`, `nn` and `dsp` (`Gemmf`, `Kalman`);
  - the 3×4 transforms of `world.CameraRelative`.
- **Depth conventions are explicit.** Every API that builds a projection, reads its planes or extracts a frustum
  takes a `core.DepthRange`: `NEGATIVE_ONE_TO_ONE` (OpenGL), `ZERO_TO_ONE` (Vulkan, Direct3D) or
  `REVERSED_ZERO_TO_ONE` (reverse-Z). See [the camera guide](guides/camera-and-projection.md).

### Packed 3×4 affine

Every 12-float affine transform in Vectrix uses one layout:
- `PackedAffineArray`;
- the outputs of `PackedAffineKernels`;
- the skinning palette (`SkinningKernels.buildMatrixPalette12`, `SkinningKernels.buildRigidMatrixPalette12`,
  `SkinningKernels.skinLbs4MatrixPalette`).

Each transform is three rows:

```text
[r00 r01 r02 tx,  r10 r11 r12 ty,  r20 r21 r22 tz]      x' = r00*x + r01*y + r02*z + tx, ...
```

`rRC` is row R, column C. This matches Vulkan's `VkTransformMatrixKHR` and a GLSL `vec4 rows[3]` read as
`p' = vec3(dot(rows[0], vec4(p, 1)), dot(rows[1], vec4(p, 1)), dot(rows[2], vec4(p, 1)))`. Since matrix accessors
name the column first, `rRC = m.mCR()`:

| Packed index | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Element | r00 | r01 | r02 | tx | r10 | r11 | r12 | ty | r20 | r21 | r22 | tz |
| `Matrix4f` accessor | `m00()` | `m10()` | `m20()` | `m30()` | `m01()` | `m11()` | `m21()` | `m31()` | `m02()` | `m12()` | `m22()` | `m32()` |

## FAST and STRICT

`MathMode.FAST`, the default, trusts its inputs. `MathMode.STRICT` is for tests, tools and validation runs:

1. **Validation.** Operations with a documented input domain (unit quaternion, unit axis, pure quaternion) throw
   `IllegalArgumentException` outside it, NaN included. "Unit" means `|x·x − 1| <= 1e-5` for float data and
   `1e-6` for double (`CoreConfig.STRICT_UNIT_TOLERANCE_F`, `CoreConfig.STRICT_UNIT_TOLERANCE_D`).
2. **No repair.** STRICT never renormalizes, clamps or substitutes values. For valid inputs, element-wise results
   are bitwise identical to FAST.
3. **Fixed dispatch.** Batch kernels always take their scalar path.

Set the mode for the process with `CoreConfig.setMathMode` or `-Dvectrix.math.mode=STRICT`. To change it for one
task on the current thread only, use `CoreConfig.runWithMathMode` or `CoreConfig.callWithMathMode`:

<!-- snippet: FirstSteps#strict -->
```java
// STRICT mode checks preconditions instead of trusting them; FAST (the default) skips the checks.
// The mode applies to the current task only, so other threads keep theirs.
boolean rejected;
try {
    CoreConfig.runWithMathMode(MathMode.STRICT, () -> {
        Quaternionf notUnit = new Quaternionf(0, 0, 0, 2);
        notUnit.log(new Quaternionf()); // throws: the logarithm needs a unit quaternion
    });
    rejected = false;
} catch (IllegalArgumentException e) {
    rejected = true;
}
```

The `blas`, `nn`, `fft` and `dsp` kernels do not consult the mode. They sum in a fixed order in both modes.

## Determinism

The same inputs give the same bits on every platform when you:
- use `-Dvectrix.strictMath=true`, which makes the transcendental functions `StrictMath`;
- keep the settings listed in [determinism.md](determinism.md) equal between runs.

The `blas` and `nn` kernels and `ReproducibleMath` go further: they give the same bits everywhere in any
configuration. The `fft` and `dsp` kernels give the same bits with or without SIMD, and the stream filters
(`Biquad`, `Resampler`) give the same bits however a stream is chunked. `BlockConvolverd` adds the carried tail
separately, so its last bits depend on where blocks start. Their twiddle factors, windows and filter designs use the platform's `sin` and `cos`, so they match
across platforms under `vectrix.strictMath`.

`CoreConfig.determinismFingerprint()` records those settings. SIMD and scalar paths give identical bits. For
reproducible transcendentals without the cost of `StrictMath`, use `ReproducibleMath`. Java widens an `int`
argument to the `float` overload, so write `ReproducibleMath.log((double) n)` when you want the double result.

## Threading

- Mutable math objects are not thread-safe: give each thread its own, or synchronize.
- Concurrent reads of an object nobody is writing are safe.
- Static functions and batch kernels keep no shared state. They are safe to call from any number of threads, on
  separate output arrays.
- Kernels are single-threaded. To use several cores, split the output into disjoint ranges or tiles and run one
  call per range. Scheduling is yours. The `blas` and `nn` results do not depend on the split, because every
  element is computed in a fixed order.
- Plans are immutable and shareable. Scratch and filter state belong to one thread at a time.
- The math mode set with `runWithMathMode` applies to the calling task only. `setMathMode` sets the default for
  all threads.

## SIMD

The library ships as two jars with the same API:
- **The plain jar** is all scalar.
- **The `simd` classifier jar** adds Vector API paths to the batch kernels: FFT, `blas`, `nn`, culling and
  transform composition.

Run the `simd` jar with `--add-modules jdk.incubator.vector`. Without that flag it runs the scalar paths.
`simd.SimdSupport.backend()` reports the active path. `SimdSupport.setEnabled(false)`, or
`-Dvectrix.simd.enabled=false`, forces scalar. Results are the same bits either way; only the speed changes.

## Errors

| Exception | When |
|---|---|
| `IllegalArgumentException` | A bad argument in the array APIs (sizes, offsets, ranges); an input outside the domain in STRICT mode |
| `IllegalStateException` | A numerical precondition failed during the call, for example a Kalman innovation covariance that is not positive definite |
| `ArithmeticException` | Exact integer arithmetic overflowed (`WorldPos` cells) |
| `IndexOutOfBoundsException` | Row and column indices of the matrix types (`Matrix4f.getRow`), and the older array APIs (as `ArrayIndexOutOfBoundsException`) |

Component indices of the vector types (`Vector3f.get(int)`, `Vector3f.setComponent`) throw `IllegalArgumentException`.

In FAST mode, the JOML-derived types do not validate values. A NaN input gives a NaN output.

## Tolerant comparisons

Use `Epsilonf` and `Epsilond` rather than literal epsilons. Their absolute and relative defaults can be set
with `-Dvectrix.epsilon.float.abs`, `-Dvectrix.epsilon.float.rel`, `-Dvectrix.epsilon.double.abs` and
`-Dvectrix.epsilon.double.rel`.

## Configuration properties

All properties are read once, when the library first uses them. Each JOML-era `joml.*` property can also be
written `vectrix.*`, and the `vectrix.` name wins when both are set.

| Property | Default | Effect |
|---|---|---|
| `vectrix.math.mode` | `FAST` | The default math mode |
| `vectrix.strictMath` | `false` | Transcendentals use `StrictMath`: the same bits on every platform |
| `vectrix.simd.enabled` | `true` | Allows Vector API paths (the `simd` jar only) |
| `vectrix.culling.simd` | `true` | Allows the Vector API culling kernel |
| `vectrix.fastmath` | `false` | Polynomial or table `sin`/`cos`/`atan2` in `core.Math` (see [determinism.md](determinism.md) for the error bounds) |
| `vectrix.sinLookup`, `vectrix.sinLookup.bits` | `false`, `14` | Table instead of polynomial for `fastmath`, and the table size |
| `vectrix.useMathFma` | `true` | Fused multiply-add in `Math.fma` |
| `vectrix.noffm`, `vectrix.forceFfm`, `vectrix.nounsafe`, `vectrix.forceUnsafe` | | The memory backend for NIO buffers and raw addresses; see [the GPU data guide](guides/gpu-data.md) |
| `vectrix.format`, `vectrix.format.decimals` | `true`, `3` | Number formatting in `toString` |
| `vectrix.debug` | `false` | Extra runtime checks, such as rejecting heap buffers on the `Unsafe` backend |
| `vectrix.epsilon.*` | | The `Epsilonf`/`Epsilond` defaults |
| `vectrix.runtime.instanceSubmission.forceMatrix` | `false` | `InstanceSubmissionPipeline` uploads `Matrix4f` instead of packed 3×4 transforms |
