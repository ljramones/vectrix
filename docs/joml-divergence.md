# Why Vectrix Forked JOML, and How It Has Diverged

Vectrix began as a fork of [JOML](https://github.com/JOML-CI/JOML), the Java OpenGL Math Library. JOML is mature,
fast and widely used, and Vectrix keeps its API shape: mutable types, read-only `…c` interfaces, `dest` parameters
and float/double twins. Attribution is in `NOTICE`.

This page records why Vectrix is a separate library, and what has changed in the code inherited from JOML. Anyone
comparing the two, or porting code from one to the other, will find the differences here. Statements about JOML refer
to the code as Vectrix inherited it; upstream JOML may have changed since.

## 1. Why a fork

Vectrix is the math foundation of the Dynamis engine. Its requirements go beyond what a general-purpose math library
promises.

- **Precision as a contract.**
  - Every function is either exact to rounding or documented with its measured error.
  - The audit tests compare it against independent references: BigDecimal, exact identities, brute force and
    quadrature.
  - JOML optimizes for speed, and accepts approximations that are fine for most rendering but not for simulation,
    large worlds or offline baking.
- **Bit-for-bit determinism across platforms.**
  - All transcendentals go through `AccurateMath`, and a strict mode uses `StrictMath`.
  - A golden file of about 8,800 results is compared on x86_64 and arm64 in CI (`docs/determinism.md`).
- **Zero allocation on hot paths**, enforced by `HotPathAllocationTest`, and destinations that may alias their
  inputs, enforced by `AliasingTest` for every destination-style method.
- **A modern JDK.**
  - JDK 25, JPMS and the Foreign Function & Memory API replace `sun.misc.Unsafe`.
  - An optional `simd` artifact uses the Vector API.
- **Engine conventions and scope.** These include a canonical packed 3×4 affine layout, exact reverse-Z projections,
  large-world positions (`world.WorldPos`), and rendering, animation, IK, atmosphere, spectral and geometry kernels
  that a game engine needs.
- **Float/double twins held to agreement.** `TwinParityTest` drives every public method of a type next to its twin.

## 2. Defects found in the inherited code

The first full review of Vectrix (2026-11) spot-checked the JOML-derived core on typical inputs and found it "mature
and largely correct"; most defects were in code Vectrix had added. A later targeted audit (Vectrix 2.4.0) instead
pushed the inherited code to its edges:
- angles near 0, 90 and 180 degrees;
- nearly parallel vectors;
- distant geometry;
- degenerate shapes;
- the less-used geometry functions.

It found about 36 defects. Almost all were shared by the float and double twins, which is why the existing twin and
determinism tests could not see them. The table lists the significant ones, with the error measured before the fix.
The full list is in `CHANGELOG.md` (2.4.0, "Fixed"), and every fix has a regression test in `PrecisionAuditTest` or
`GeometryAuditTest`.

| Area | Inherited behaviour | Measured effect | Vectrix |
|---|---|---|---|
| `Math.cosFromSin`, used by every rotation builder | `cos = ±sqrt(1 − sin²)` everywhere | All digits lost near ±90° (1e-8 absolute in double, 2.4e-4 in float); wrong sign beyond ~1.35e10 rad | `cos` evaluated directly where the identity cancels |
| `AxisAngle.set(Quaternion/Matrix)` | `acos` plus epsilon snapping | Rotations below ~1e-4 rad reported as none; rotations near 180° reported as exactly 180° | `atan2` forms; near 180° the axis comes from the symmetric part |
| `Quaternion.rotationTo` | Fallback when nearly antiparallel | Non-unit quaternion that ignored the target | Axis-angle form, exact to 1e-16 |
| `Quaternion.slerp`, `nlerpIterative` | Lerp fallback; early exit | Results off unit length by up to 2.5e-7; `alpha` ignored for small arcs | Cancellation-free angle |
| Euler angles (quaternions and matrices) | `asin`; `sqrt(1 − s²)` | Inexact near gimbal lock; NaN from matrices when rounding pushed \|s\| above 1 | `atan2` middle angle; outer angle from the residual rotation, so the rotation is rebuilt exactly |
| `Vector*.angle` | `acos` of the normalized dot product | Angles below ~1e-8 rad returned as 0 | `atan2(\|a × b\|, a · b)` |
| `Vector*.length` / `normalize` | Plain sum of squares | `(1e200, 1e200, 0)` had length ∞; `(1e-170, 0, 0)` normalized to NaN | Power-of-two scaling outside the safe range |
| `lookAt` / `lookAlong` family | Left = normalize(up × dir) | Up to 6.6e-9 non-orthogonality as `up` approaches the view direction, in a result flagged orthonormal | One Gram-Schmidt step |
| `Matrix.affineSpan` | Operator-precedence error | Wrong for any scaled matrix | Fixed |
| `Matrix.normal()` | Orthonormal fast path | Kept the translation, against its contract | Rotation part only |
| `Matrix.perspectiveFov` | `acos` | 3.7e-4 relative error at a 1e-6 rad field of view | `atan2` |
| `Intersection*.testObOb` | Rotation matrix built transposed | About 3 % wrong answers on random rotated box pairs | `R[i][j] = Aᵢ · Bⱼ`; matches a separating-axis reference |
| Ray–sphere, ray–circle | `d² = \|L\|² − tca²` | Thousands of wrong hits or misses per 10k rays at distant spheres; tangent rays missed | Perpendicular distance computed directly, stable roots |
| Ray–triangle | Möller–Trumbore with an absolute epsilon | About 20 % of rays aimed at an edge shared by two triangles missed both; small triangles missed head-on | Woop et al.'s watertight algorithm |
| Plane and circle helpers | Sign and factor errors | Wrong circle centre (plane–sphere, line–circle); wrong closest point on a plane; swept spheres missing planes; circle–circle intersections reported absent | Fixed against their documented contracts |
| `intersectSweptSphereTriangle` | Front-face-only sweep with an epsilon for parallel motion | Face hits at negative times when moving away; the exit time from behind; no hit while sliding parallel into an edge; exit time for a sphere starting on a vertex | Two-sided first contact; contact at t = 0 reports the closest point; stable quadratics, matching a bisection reference to 1e-9 |
| Point-in-triangle tests (2D and 3D) | Sign tests that are inconsistent on the boundary | Two edges of three counted as inside in 3D; edges inside for one winding only in 2D | Closed triangles; exact orientation predicates in 2D |
| `PolygonsIntersection` | Bounds skipped each polygon's first vertex | Points inside the polygon rejected, depending on vertex order | Bounds over all vertices |

Earlier releases fixed further inherited defects. For example, `findClosestPointsLineSegmentTriangle` missed
segments that pierce the triangle; that was fixed in 2.2.0.

The four issues the audit left open (swept sphere against triangle, the two point-in-triangle tests, and moving circles
that already overlap) were fixed in the same release.

## 3. Behaviour differences when porting from JOML

Most code ports unchanged. Differences in results:

- **Last-bit differences** in rotation builders (because of `cosFromSin`) and in float vector lengths and
  normalization, which are now accumulated in double and correctly rounded.
- **Tiny rotations are kept.** `AxisAngle` of a 1e-9 rad quaternion is that rotation, not the identity.
- **Ray–triangle `epsilon`** is the minimum hit distance along the ray. In JOML it was a tolerance on the determinant.
  Parallel rays are handled exactly.
- **Squared radii.** `testCircleCircle(x, y, rA, x, y, rB)` takes squared radii, as JOML documented, though JOML's
  implementation used plain radii.
- **Matrix Euler angles** remove scale before extracting the rotation.
- **`intersectSweptSphereTriangle`** works from both sides, ignores `epsilon`, and reports time 0 for a sphere that
  starts in contact.
- **Triangle boundaries are inside** for `testPointInTriangle` and `testPointTriangle`, for either winding.
- **`testSphereSphere` asks whether the surfaces meet.** That is the existing behaviour, now documented: a sphere
  strictly inside another reports false.
- **Default memory backend:** FFM, not `sun.misc.Unsafe`. Options select the old backends (`docs/determinism.md`,
  `CLAUDE.md`).

## 4. Keeping it this way

The audit's tests run in CI on x86_64 and arm64, in the default and experimental profiles, next to the parity,
determinism, aliasing and allocation suites.

New code follows `docs/determinism.md` and the rules in `CLAUDE.md`:
- transcendentals only through `AccurateMath`;
- no allocation on hot paths;
- every float method has a double twin;
- results are tested against exact references, not only checked to be finite.
