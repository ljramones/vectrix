# Conventions

This project is rendering-first and defaults to these conventions unless an API explicitly states otherwise:

- Coordinate system: right-handed.
- Matrix storage: column-major (`Matrix4f` and friends, GLSL `mat4`). The one exception is the packed 3x4
  affine below, which is row-major.
- Transform composition: post-multiplication style for chained transforms.
- Quaternion convention: `(x, y, z, w)` with `w` as scalar term.
- Clip-space: API-dependent; keep projection building explicit at call sites.

## Packed 3x4 Affine (`AFFINE_3X4_ROW_MAJOR`)

Every 12-float affine in Vectrix uses one layout: `PackedAffineArray`, the `PackedAffineKernels` outputs,
`GpuTransformWriteKernels.writePackedAffine`, and the skinning palette (`SkinningKernels.buildRigidMatrixPalette12`,
`skinLbs4MatrixPalette`). Each transform is three rows:

```
[r00 r01 r02 tx,  r10 r11 r12 ty,  r20 r21 r22 tz]      x' = r00*x + r01*y + r02*z + tx, ...
```

`rRC` is row R, column C. This is the order of Vulkan's `VkTransformMatrixKHR` and of GLSL `vec4 rows[3]` read as
`p' = vec3(dot(rows[0], vec4(p, 1)), dot(rows[1], vec4(p, 1)), dot(rows[2], vec4(p, 1)))`.

JOML-style accessors name elements column first (`m.mCR()` is column C, row R), so **`rRC = m.mCR()`**:

| Packed index | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Element | r00 | r01 | r02 | tx | r10 | r11 | r12 | ty | r20 | r21 | r22 | tz |
| `Matrix4f` accessor | `m00()` | `m10()` | `m20()` | `m30()` | `m01()` | `m11()` | `m21()` | `m31()` | `m02()` | `m12()` | `m22()` | `m32()` |

In 1.10.13 and earlier `PackedAffineArray` stored basis columns instead (review finding M-1), which applied the inverse
rotation wherever it met a row-major consumer.

## Numeric Policy

- Use `MathMode.FAST` for throughput-sensitive rendering paths.
- Use `MathMode.STRICT` for validation and reproducibility-sensitive workflows (capture/replay/tests). STRICT is
  one contract, documented on `MathMode`:
  1. **Validation.** Operations with a documented input domain (unit quaternion, unit axis, pure quaternion)
     throw `IllegalArgumentException` outside it, NaN included. Unit means `|x·x − 1| <= 1e-5` for float data and
     `1e-6` for double (`CoreConfig.STRICT_UNIT_TOLERANCE_F/_D`, `CoreConfig.withinStrictUnitTolerance`).
  2. **No repair.** STRICT never renormalises, clamps or substitutes; for valid inputs, element-wise results are
     bitwise identical to the FAST scalar path.
  3. **Fixed dispatch.** Batch kernels always take their scalar path, independent of SIMD support.
  4. **Accurate reductions.** `Reduction` sums and dot products are sequential double-double, so the result is
     the exact value rounded to float (within one ulp) on every JVM.
- STRICT does not yet make `java.lang.Math` transcendentals bit-reproducible across CPU architectures, nor does it
  override `-Djoml.fastmath` or `-Djoml.useMathFma`. That is the determinism contract (review finding M-12).
- Use `Epsilonf`/`Epsilond` for tolerant comparisons instead of ad-hoc literals.

## Threading Policy

- Mutable vectors/matrices/quaternions/transforms are not thread-safe.
- Read-only interfaces (`*c`) are safe for concurrent reads.
- Batch kernels are pure with respect to provided inputs/outputs; call-site synchronization remains the caller responsibility.

## GPU Layout Policy

- Validate vertex stream layouts via `VertexLayout`.
- Use `StdLayout` for std140/std430 offset and size calculations.
- Keep packed formats explicit (`Half`, `PackedNorm`, `OctaNormal`, `QuatCompression`) and benchmark all conversion hot paths.
