# Geometry and physics math

This guide covers the geometric queries and the physics and linear-algebra kernels: ray and primitive
intersection tests, closest points, contact depth and normal, exact orientation predicates, bounding-volume
fitting, space-filling-curve codes, mesh helpers, signed distance functions, mass properties, springs, position-based
constraints, ODE integration, and small dense linear algebra. The packages are `geometry`, `sdf`, `physics` and
`linalg`, plus the RK4 integrator in `core`.

Vectrix gives you the math, not a collision or physics system. There are no shape types, broad phase, contact
manifolds, solvers or rigid bodies here: you pass shapes as plain parameters (centers, radii, corners, axes), and the
functions return numbers. A collision library (in the Dynamis engine, DynamisCollision) owns shapes and pipelines and
calls these kernels. Frustum culling lives in the same `geometry` package but has its own guide:
[scene-and-culling.md](scene-and-culling.md).

## Which class for which job

| Task | Class | Notes |
|---|---|---|
| Ray, segment, line against sphere, box, triangle, plane, circle, rectangle | `Intersectionf`, `Intersectiond` | Static. The most complete set of 2D and 3D tests |
| One ray against many boxes | `RayAabIntersection` | Precomputes ray slopes once, then `RayAabIntersection.test` per box |
| Ray against capsule | `ClosestPointsf.intersectRayCapsule` | Returns `t`, or -1 on a miss |
| Closest point on a triangle, segment, plane, rectangle | `Intersectionf.findClosestPointOnTriangle` and the other `findClosestPoint*` methods | The triangle version also reports vertex, edge or face |
| Closest points: point–OBB, triangle–triangle, segment–triangle, segment–box | `ClosestPointsf`, `ClosestPointsd` | Squared distance plus both closest points |
| Penetration depth, normal and contact point | `Contactsf`, `Contactsd` | Sphere, box (AABB) and capsule pairs |
| Exact orientation and in-circle tests | `RobustPredicates` | The sign is always correct |
| Fit a bounding sphere, OBB, rectangle or k-DOP to points | `BoundingVolumesf`, `BoundingVolumesd` | Fitting routines; they allocate scratch |
| AABB of an OBB, capsule, cylinder or transformed points | `BoundingVolumesf.aabOfObb` and the other `aabOf*` methods | No allocation |
| Morton and Hilbert codes for sorting and linear BVHs | `SpatialCodes` | Integer grid coordinates in, codes out |
| Barycentric coordinates, triangle area, winding, quality | `MeshMath` | Single precision |
| Vertex normals and tangents of an indexed mesh | `MeshMath.vertexNormals`, `MeshMath.vertexTangents` | Flat arrays |
| Triangle normal, tangent frame, perpendicular vectors | `GeometryUtils` | Float and double overloads |
| Point in many concave polygons (with holes) | `PolygonsIntersection` | Build once, test many points |
| Signed distance to a sphere, box, capsule, cylinder, torus | `Sdf3f`, `Sdf3d` | Negative inside |
| Inertia of primitives, volumes, parallel axis, combining bodies | `InertiaTensorf`, `MassPropertiesf` (and `d` twins) | `MassPropertiesf.polyhedron` for closed meshes |
| Damped spring step, damping and stiffness helpers, suspension | `SpringDamperf`, `SpringDamperd` | Exact steps, any `dt` |
| PD torque controller | `PdControllersf`, `PdControllersd` | |
| Tyre forces (Pacejka magic formula) and slip | `Pacejkaf`, `Pacejkad` | |
| Position-based constraint corrections | `PbdConstraintsf`, `PbdConstraintsd` | Distance, bending, volume, shape matching |
| Integrate an ODE one step | `IntegratorRK4f`, `IntegratorRK4d` | Caller-owned scratch, in place allowed |
| Symmetric eigen, SVD, polar, QR | `Eigen3f`, `Svd3f`, `Polar3f`, `Qrf` (and `d` twins) | 3×3 (QR also 4×4) |
| Solve small linear systems, condition numbers | `Solversf`, `Solversd` | Cholesky, LDLᵀ, LU |
| Least squares, plane and line fits | `LeastSquaresf`, `LeastSquaresd` | QR-based |
| Rotation vectors and twists (SO(3), SE(3) exp and log) | `Lie3f`, `Lie3d` | |

Every class comes as a float and a double twin with the same methods (`RayAabIntersection`, `MeshMath`,
`SpatialCodes`, `RobustPredicates`, `PolygonsIntersection` and `GeometryUtils` are single classes). The examples below name the
float class. Type suffixes, `dest` parameters, aliasing and the read-only `...c` interfaces are described once in
[conventions.md](../conventions.md).

## Intersection tests

`Intersectionf` and `Intersectiond` hold static tests between pairs of primitives. The method name says what it
does and which shapes it takes:

| Prefix | Returns |
|---|---|
| `test...` | `true` or `false` only. Cheapest |
| `intersect...` | Also the hit: a ray parameter `t`, an intersection point, or a classification code |
| `findClosestPoint...` | The closest point, written to a result vector |
| `distance...` | A distance |

| Name part | Shape |
|---|---|
| `Aab` | Axis-aligned box, given as `min` and `max` corners |
| `Aar` | Axis-aligned rectangle (2D), given as `min` and `max` |
| `Ob` | Oriented box: center, three unit axes, half-sizes |
| `Sphere`, `Circle` | Center and radius (see the radius note below) |
| `Plane` | Either a point and a normal, or coefficients `a, b, c, d` with `a·x + b·y + c·z + d = 0` |
| `Ray` | Origin and direction: points `origin + t·dir` with `t >= 0` |
| `LineSegment` | Two end points |
| `Line` | An infinite line (2D: `a·x + b·y + c = 0`) |
| `SweptSphere`, `MovingCircle` | A sphere or circle moving along a velocity vector |

Every test exists in two forms: one taking vector objects (`Vector3fc`, `Vector2fc`) and one taking every coordinate
as a separate `float`. The scalar form avoids reading through an interface and is useful when your data is in arrays.

Things to watch:
- **Squared or plain radius.** Most sphere and circle tests take the squared radius: `Intersectionf.testRaySphere`,
  `Intersectionf.intersectRaySphere`, `Intersectionf.testSphereSphere`, `Intersectionf.testAabSphere`,
  `Intersectionf.testLineSegmentSphere`, `Intersectionf.testCircleCircle`, `Intersectionf.testPointCircle` and
  `Intersectionf.intersectRayCircle`. The plane–sphere, line–circle, sphere–triangle, swept-sphere and moving-circle
  tests take the plain radius. Check the parameter name (`radiusSquared` or `radius`) before you call.
- **Ray direction.** `Intersectionf.intersectRaySphere` expects a unit direction. The box and triangle tests do not.
- **Ray results.** `Intersectionf.intersectRayAab` returns a boolean and writes the near and far `t` into a
  `Vector2f`; it returns `true` when the origin is inside the box. `Intersectionf.intersectRayTriangle` and
  `Intersectionf.intersectRayPlane` return `t`, or `-1` on a miss. `Intersectionf.intersectRayPlane` also returns
  `-1` when the origin is on the negative side of the plane (the normal points away from the origin).
- **Triangle winding.** `Intersectionf.intersectRayTriangle` hits both faces. `Intersectionf.intersectRayTriangleFront`
  and `Intersectionf.testRayTriangleFront` skip back faces (counter-clockwise is front). The `epsilon` argument of the
  ray–triangle tests is the minimum distance along the ray for a hit, not a determinant tolerance; the test is
  watertight and handles parallel rays.
- **Classification codes.** `Intersectionf.intersectLineSegmentAab` returns `Intersectionf.OUTSIDE`,
  `Intersectionf.INSIDE`, `Intersectionf.ONE_INTERSECTION` or `Intersectionf.TWO_INTERSECTION`.
  `Intersectionf.intersectRayAar` returns the side that was hit (`Intersectionf.AAR_SIDE_MINX` and so on) or -1.
- **Swept sphere.** `Intersectionf.intersectSweptSphereTriangle` finds the first contact time in `[0, maxT)` from
  either side. A sphere that already touches the triangle at `t = 0` reports time 0. Contact is inclusive.
- **Points in triangles and polygons.** `Intersectionf.testPointTriangle` tests a 2D point against a 2D triangle with
  exact orientation tests; the triangle is closed and either winding works. `Intersectionf.testPointInTriangle`
  tests whether a 3D point, projected onto the triangle's plane, falls inside it. `Intersectionf.testPolygonPolygon`
  tests two convex 2D polygons, and `Intersectionf.intersectPolygonRay` returns the index of the edge a ray hits
  (or -1).

### Many boxes, one ray

When you test one ray against many boxes (a BVH traversal, picking), use `RayAabIntersection`. Call
`RayAabIntersection.set` with the ray once; it precomputes the ray's slopes. Then call `RayAabIntersection.test` with
each box's min and max corners. It answers yes or no only; use `Intersectionf.intersectRayAab` when you need `t`.
After `set`, any number of threads can call `test` on the same instance.

`Intersectionf.testRayAabInvDir` is the alternative when you already store the reciprocal direction.

## Closest points and distances

| Query | Method | Output |
|---|---|---|
| Point to triangle | `Intersectionf.findClosestPointOnTriangle` | The point, and a code: `Intersectionf.POINT_ON_TRIANGLE_VERTEX_0` to `_2`, `Intersectionf.POINT_ON_TRIANGLE_EDGE_01`, `_12`, `_20`, or `Intersectionf.POINT_ON_TRIANGLE_FACE` |
| Point to segment, plane, rectangle | `Intersectionf.findClosestPointOnLineSegment`, `Intersectionf.findClosestPointOnPlane`, `Intersectionf.findClosestPointOnRectangle` | The point |
| Segment to segment | `Intersectionf.findClosestPointsLineSegments` | Both points; returns the squared distance |
| Segment to triangle | `Intersectionf.findClosestPointsLineSegmentTriangle`, `ClosestPointsf.distanceSquaredSegmentTriangle` | Both points; returns the squared distance (0 if the segment pierces the triangle) |
| Triangle to triangle | `ClosestPointsf.distanceSquaredTriangleTriangle` | Both points; returns the squared distance (0 if they intersect) |
| Point to oriented box | `ClosestPointsf.closestPointObb` | The point; `p` itself when inside |
| Point to AABB | `ClosestPointsf.closestPointAab`, `ClosestPointsf.distanceSquaredPointAab` | The clamped point; squared distance, 0 inside |
| Segment to AABB | `ClosestPointsf.segmentAab` | Both points; returns the squared distance. Exact, no iteration |
| Point to plane or line | `Intersectionf.distancePointPlane`, `Intersectionf.distancePointLine` | Signed distance to a plane given as `a, b, c, d` or by three points; distance to a 2D line (`a, b, c` or two points) or a 3D line through two points |

`MeshMath.closestPointOnTriangle` is a simpler point-to-triangle query without the region code.

## Contact depth, normal and point

`Contactsf` and `Contactsd` compute what a collision response needs for one pair of primitives:
`Contactsf.sphereSphere`, `Contactsf.aabbAabb`, `Contactsf.sphereAabb` and `Contactsf.capsuleAabb`. All of them
follow the same rules:
- **Return value:** the signed depth. Positive when the volumes overlap: the distance to move the second volume along
  `normal` to separate them. Zero when they touch. Minus the separation distance when they are apart.
- **`normal`:** unit length, pointing from the first volume to the second.
- **`point`:** the midpoint between the two volumes' deepest points along the normal (their closest points when
  apart), unless a method documents otherwise. For two overlapping boxes it is the center of the overlap region.

Radii here are plain radii, not squared. Two spheres with the same center get the normal `+X`. For an oriented box,
transform the other volume into the box's frame, call the AABB version, and rotate the normal and point back.
`Contactsf.testSphereSphere` is the boolean-only check, and `Contactsf.nearestFace` gives the face of a box nearest to
a point (positive distance inside).

## Exact predicates

Floating-point orientation tests can return the wrong sign for nearly collinear or nearly coplanar points. Code such
as Delaunay triangulation, convex hulls and mesh booleans then makes inconsistent decisions and fails.
`RobustPredicates` always returns the correct sign:

| Method | Positive when |
|---|---|
| `RobustPredicates.orient2d` | `a, b, c` are counter-clockwise |
| `RobustPredicates.orient3d` | `d` lies below the plane through `a, b, c` (counter-clockwise seen from above) |
| `RobustPredicates.incircle` | `d` lies inside the circle through counter-clockwise `a, b, c` |
| `RobustPredicates.insphere` | `e` lies inside the sphere through `a, b, c, d` (with `orient3d(a, b, c, d) > 0`) |

Zero means exactly collinear, coplanar, cocircular or cospherical. Each predicate first evaluates in double and
returns that value when it is above Shewchuk's error bound. Otherwise it recomputes exactly with `BigDecimal`, which
allocates; this happens only for near-degenerate input. Only the sign is guaranteed; the magnitude is the
determinant when the fast path is taken. `orient2d` and `orient3d` have float overloads, which convert to double
exactly.

## Bounding volumes

`BoundingVolumesf` and `BoundingVolumesd` fit volumes to a point set. Points are packed `x, y, z` triples (or `x, y`
pairs) in a flat array, with a `count`.

| Method | Result |
|---|---|
| `BoundingVolumesf.minimalSphere` | The smallest enclosing sphere (Welzl), as `(cx, cy, cz, radius)` in a `Vector4f`. Deterministic |
| `BoundingVolumesf.pcaObb` | Oriented box from principal component analysis: center, axes as the columns of a `Matrix3f` (a rotation), half extents |
| `BoundingVolumesf.approximateMinimalObb` | A smaller oriented box: the best of the PCA box and the minimal rectangles across each PCA axis. Returns the volume |
| `BoundingVolumesf.minimalAreaRectangle` | The minimal-area 2D rectangle (rotating calipers): center, unit direction of the first side, half extents. Returns the area |
| `BoundingVolumesf.kDop` | A k-DOP for k = 6, 14, 18 or 26: min and max of `p · direction` for each of the k/2 directions of `BoundingVolumesf.kDopDirections` (not normalized) |

The fitting methods above allocate scratch proportional to the input, so call them at load or build time. The
bounds helpers allocate nothing and suit per-frame use: `BoundingVolumesf.aabOfObb`, `BoundingVolumesf.aabOfCapsule`,
`BoundingVolumesf.aabOfCylinder`, `BoundingVolumesf.aabOfTransformedPoints` (points under a `Matrix4x3f`), and
`BoundingVolumesf.transformSphere` (a bounding sphere under an affine transform).

## Spatial codes

A space-filling curve maps grid cells to a single integer so that cells close in space tend to get close codes.
Sorting objects by code groups neighbors together, which is how linear BVHs and spatially coherent buffers are
built. `SpatialCodes` takes non-negative integer grid coordinates; quantize floats first, for example
`(int) ((x - min) / extent * 1024)`.

| Method | Input range | Output |
|---|---|---|
| `SpatialCodes.morton2d` | `x, y` in `[0, 65535]` | 32-bit Morton (Z-order) code |
| `SpatialCodes.morton2d64` | `x, y` as unsigned 32-bit | 64-bit code |
| `SpatialCodes.morton3d` | `x, y, z` in `[0, 1023]` | 30-bit code |
| `SpatialCodes.morton3d64` | `x, y, z` in `[0, 2²¹ − 1]` | 63-bit code |
| `SpatialCodes.hilbert2d`, `SpatialCodes.hilbert3d` | A `2^bits` grid (`bits <= 31` in 2D, `<= 21` in 3D) | Hilbert index; consecutive indices are adjacent cells |

`SpatialCodes.morton2dX`, `SpatialCodes.morton3dX` (and the `Y`, `Z` versions), `SpatialCodes.hilbert2dToXy` and
`SpatialCodes.hilbert3dToXyz` decode. To hash a grid cell into a hash-table bucket instead, use
`SpatialHash.hashCell3i` (see [curves-sampling-noise.md](curves-sampling-noise.md)).

## Triangles and meshes

`MeshMath` works on single triangles and on indexed meshes:
- `MeshMath.barycentric` writes the barycentric coordinates `(u, v, w)` of a point with respect to triangle `abc`.
  The degeneracy test is relative to the triangle's size; a degenerate triangle gives NaN.
  `MeshMath.isInsideBarycentric` checks the result with a small tolerance.
- `MeshMath.triangleArea`, `MeshMath.signedArea2` (twice the signed 2D area) and `MeshMath.winding2D` (1 for
  counter-clockwise, -1 for clockwise, 0 for degenerate, using a scale-independent tolerance).
- Quality measures for mesh processing: `MeshMath.triangleAspectRatio` (1 for equilateral, growing as the triangle
  degenerates), `MeshMath.triangleRadiusRatio` (1 for equilateral, 0 for degenerate) and `MeshMath.triangleMinAngle`
  (radians).
- `MeshMath.vertexNormals` takes packed positions and three indices per triangle and writes one normal per vertex
  (3 floats). With `angleWeighted` false, face normals are weighted by area; with true, by the corner angle, which
  does not depend on how faces are triangulated. Vertices with no usable face get the zero vector.
- `MeshMath.vertexTangents` writes 4 floats per vertex in the style of MikkTSpace: a unit tangent and a handedness
  `w = ±1`, with the bitangent `w · (n × t)`. It does not split vertices at mirrored UV seams; split the mesh first.

`GeometryUtils` has the per-triangle helpers: `GeometryUtils.normal` (unit normal of three points),
`GeometryUtils.tangent`, `GeometryUtils.bitangent` and `GeometryUtils.tangentBitangent` from positions and UVs, and
`GeometryUtils.perpendicular`, which completes a unit vector to an orthonormal basis.

For many points against fixed 2D polygons, build a `PolygonsIntersection` once from packed `x, y` vertices and the
start index of each polygon, then call `PolygonsIntersection.testPoint`. It uses the even-odd rule over all polygons
together, so a polygon inside another is a hole. The vertex array is not copied, so do not change it while the object
is in use. `testPoint` is safe to call from many threads.

## Signed distance functions

A signed distance function returns the distance from a point to a surface: positive outside, negative inside, zero on
the surface. Its uses include ray marching, soft shadows, particle collision and procedural modelling.
`Sdf3f` and `Sdf3d` return exact Euclidean distances for:

| Method | Shape |
|---|---|
| `Sdf3f.sphere` | Sphere of a radius, centered at the origin |
| `Sdf3f.box` | Box with half extents, centered at the origin |
| `Sdf3f.capsule` | Capsule around segment `a`–`b` with a radius (a point segment is a sphere) |
| `Sdf3f.cylinderY` | Solid cylinder along Y: radius and half height |
| `Sdf3f.torusY` | Torus around the Y axis: major and minor radius |

The shapes other than the capsule sit at the origin. To place one elsewhere, transform the query point into the
shape's local frame first (for example with the inverse of its world transform); rotations and translations keep
distances exact, while non-uniform scale does not. Union, intersection and subtraction are `min`, `max` and
`max(a, -b)` of the distances; Vectrix does not provide them as methods.

## Mass properties

`InertiaTensorf` returns the diagonal inertia (principal moments `Ixx, Iyy, Izz` in a `Vector3f`) of solid primitives
about their center of mass, given the mass:

| Method | Shape and parameters |
|---|---|
| `InertiaTensorf.sphere` | Radius |
| `InertiaTensorf.box` | Half extents `hx, hy, hz` |
| `InertiaTensorf.capsule` | Along Y; `height` is the cylindrical section only, without the caps |
| `InertiaTensorf.cylinder` | Along Y; full height |
| `InertiaTensorf.cone` | Along Y, apex at +Y; about its center of mass, `height/4` above the base |

`InertiaTensorf.principalAxes` diagonalizes a full tensor: moments in descending order and the axes as the columns of a
rotation matrix.

`MassPropertiesf` handles full tensors (`Matrix3f`) and compound bodies:
- Volumes: `MassPropertiesf.sphereVolume`, `MassPropertiesf.boxVolume`, `MassPropertiesf.capsuleVolume`,
  `MassPropertiesf.cylinderVolume`, `MassPropertiesf.coneVolume`. Multiply by density for the mass.
- `MassPropertiesf.parallelAxis` moves a tensor from the center of mass to a point at an offset.
- `MassPropertiesf.rotate` computes `R I Rᵀ` for a rotated body.
- `MassPropertiesf.combine` merges two bodies given in a common frame and returns the total mass, the combined center
  of mass and the inertia about it. Call it repeatedly for a compound.
- `MassPropertiesf.polyhedron` gives the exact mass, center of mass and inertia of a closed triangle mesh of uniform
  density. Triangles must be wound counter-clockwise seen from outside; an inside-out mesh gives a negative mass. The
  integrals are taken relative to the mean vertex in double, so a mesh far from the origin keeps its precision.

## Springs, controllers and tyres

`SpringDamperf.dampedStep` advances a damped spring toward a target by one time step, for unit mass:
`x'' = -k·x - c·x'`. It is exact in every regime (under-, critically and overdamped), so it is stable for any `dt` and
gives the same motion at any frame rate. It returns the new position and writes the new velocity to `velOut[0]`.
`SpringDamperf.criticallyDamped` is the same for a critically damped spring parameterized by angular frequency.

To choose coefficients:
- `SpringDamperf.stiffnessForFrequency` gives the stiffness `m (2π f)²` for an oscillation frequency in hertz.
- `SpringDamperf.criticalDamping` gives `2 sqrt(k m)`.
- `SpringDamperf.dampingForRatio` gives `2 ζ sqrt(k m)` for a damping ratio `ζ` (below 1 bounces, 1 is critical,
  above 1 is sluggish).

`SpringDamperf.force` is the plain Hooke-plus-damping force. For vehicle suspension, use
`SpringDamperf.suspensionForceFromLength` or `SpringDamperf.suspensionForceFromCompression`; both return the force
along the strut, positive when it pushes the wheel away from the body.

`PdControllersf.torque` computes `kp · angularError - kd · angularVelocity`, and `PdControllersf.clampTorque` limits
its magnitude.

`Pacejkaf.magicFormula` evaluates the Pacejka tyre curve `D sin(C atan(B x − E (B x − atan(B x))))`, with an
overload that adds horizontal and vertical shifts. Feed it `Pacejkaf.slipRatio` (longitudinal,
`(ω r − v) / max(|v|, minSpeed)`) or `Pacejkaf.slipAngle` (lateral, radians in `[−π/2, π/2]`).

## Position-based constraints

Position-based dynamics (PBD) moves particles directly to satisfy constraints, instead of applying forces.
`PbdConstraintsf` computes the position corrections for one constraint; your solver loop applies them and iterates.
Inverse mass 0 pins a particle. `stiffness` is in `[0, 1]`, where 1 solves the linearized constraint in one step.

| Method | Constraint | Output |
|---|---|---|
| `PbdConstraintsf.distanceConstraint` | Distance between A and B equals `restLength` | The correction for A in `dest`. Call again with A and B swapped for B's correction |
| `PbdConstraintsf.bendConstraint` | The angle at `p1` between edges to `p0` and `p2` equals `restAngle` (radians) | Corrections `d0, d1, d2`. An overload without inverse masses treats all as 1 |
| `PbdConstraintsf.volumeConstraint` | Signed tetrahedron volume equals `restVolume` | Corrections `d0` to `d3`; returns the current volume. Mass-weighted, so linear momentum is conserved |
| `PbdConstraintsf.shapeMatchingGoal` | Shape matching (Müller et al. 2005) | The best-fit rotation and a goal position per particle |

The bend and volume constraints read all inputs before writing any output, so outputs may alias inputs. Degenerate
configurations give zero corrections: zero-length or parallel edges for bending, and for volume a tetrahedron whose
gradients all vanish (collinear points, or every particle pinned). A flat tetrahedron is pushed back toward its rest
volume. The degeneracy tests are relative to the size of the configuration, so they behave the same at any scale.
`PbdConstraintsf.shapeMatchingGoal` does not allocate; it extracts the rotation iteratively, starting from a
Gram-Schmidt estimate so that large rotations converge too.

## Integrating ODEs

`IntegratorRK4f.step` and `IntegratorRK4d.step` advance a state array by one fourth-order Runge-Kutta step. You
supply the derivative as an `OdeDerivativef` (or `OdeDerivatived`): `OdeDerivativef.compute` receives the time and
the state and writes the derivative. Arguments:

| Argument | Meaning |
|---|---|
| `t`, `dt` | Current time and step size |
| `state`, `stateOffset`, `stateSize` | The state values to advance |
| `scratch`, `scratchOffset` | At least `5 · stateSize` values you own: k1 to k4 and a temporary state |
| `dest`, `destOffset` | Where the new state goes. May be the same array and offset as `state` |

The step does not allocate. For a single spring, `SpringDamperf.dampedStep` is exact and cheaper than RK4.

## Small linear algebra

The `linalg` package solves fixed-size problems that come up in geometry and physics: principal axes, closest
rotations, small linear systems and fits. All kernels compute in double precision; the float classes convert at the
boundary, so `Svd3f` is as accurate as `Svd3d` up to the final rounding. Inputs and outputs are the core matrix
types, which are column-major.

| Method | Computes |
|---|---|
| `Eigen3f.symmetric` | `a = V diag(λ) Vᵀ` for symmetric 3×3 `a` (Jacobi). Eigenvalues descending; eigenvectors in the columns of a proper rotation. Only the symmetric part of `a` is read |
| `Svd3f.decompose` | `a = U diag(σ) Vᵀ` with `U`, `V` proper rotations and `σ0 >= σ1 >= abs(σ2)`. `σ2` is negative exactly when `det(a) < 0` |
| `Svd3f.condition` | 2-norm condition number `σ0 / abs(σ2)`; infinite when singular |
| `Polar3f.decompose`, `Polar3f.rotation` | `a = R S`: the rotation closest to `a` and a symmetric stretch |
| `Qrf.decompose` | Householder `a = Q R` for 3×3 and 4×4, `R` with a non-negative diagonal |
| `Solversf.cholesky`, `Solversf.solveCholesky` | Factor and solve symmetric positive-definite systems (3×3 and 4×4). `cholesky` returns false if the matrix is not positive definite |
| `Solversf.ldlt`, `Solversf.solveLdlt` | Symmetric, possibly indefinite 3×3 systems |
| `Solversf.solve` | General 3×3 or 4×4 system by LU with partial pivoting; false (dest unchanged) when singular |
| `Solversf.condition1` | 1-norm condition number |
| `LeastSquaresf.solve` | Minimizes `‖A x − b‖₂` for a row-major `rows × cols` array, `rows >= cols`, by QR. False when `A` is rank deficient |
| `LeastSquaresf.fitPlane` | Best-fit plane `n·p + d = 0` through packed points, as `(n, d)` |
| `LeastSquaresf.fitLine` | Best-fit line: the centroid and a unit direction |
| `Lie3f.expSo3`, `Lie3f.logSo3` | Rotation vector (axis times angle, radians) to rotation matrix and back; the log is exact up to π |
| `Lie3f.expSe3`, `Lie3f.logSe3` | Twist `(w, v)` to rigid motion (`Matrix4x3f`) and back |

Use the polar rotation for shape matching and for extracting the rotation from a deformation gradient. Use the SVD
when you also need the singular values (for example, to detect inverted elements by the sign of `σ2`). Use
`Lie3f` to integrate angular velocity (`exp` of `ω·dt`) and to interpolate rigid motions in the tangent space.

## Accuracy, allocation and threading

- All classes in these packages are static functions except `RayAabIntersection`, `PolygonsIntersection` and the
  integrator callbacks. Static functions keep no shared state, so any number of threads may call them. See
  [conventions.md](../conventions.md) for the general threading rules.
- No allocation: the `Intersectionf` tests, the contact kernels, the `Sdf3f` functions, the inertia and spring
  kernels, `PbdConstraintsf`, `IntegratorRK4f.step`, the `aabOf*` bounds helpers and the fixed-size `linalg` kernels.
  The fixed-size `linalg` kernels use per-thread scratch arrays created on a thread's first call.
- Allocation proportional to the input: the `BoundingVolumesf` fitting methods, `MeshMath.vertexTangents` and
  `LeastSquaresf.solve` (which builds a full `rows × rows` Q, so keep `rows` small).
- `RobustPredicates` allocates only when it falls back to exact arithmetic for near-degenerate input.
- `ClosestPointsf.segmentAab` is exact (every candidate evaluated in double). `Contactsf.aabbAabb` gives the exact
  minimum translation. `Contactsf.capsuleAabb` is exact for the segment against the box.
- `SpringDamperf.dampedStep` and `SpringDamperf.criticallyDamped` are exact for any `dt` and compute in double.
- `MassPropertiesf.combine`, `MassPropertiesf.polyhedron` and the `linalg` kernels compute in double even in the
  float classes.

## See also

- [conventions.md](../conventions.md): type suffixes, `dest` parameters and aliasing, column-major matrices,
  radians, threading, errors.
- [scene-and-culling.md](scene-and-culling.md): frustum tests and batch culling from the same `geometry` package.
- [curves-sampling-noise.md](curves-sampling-noise.md): spatial hashing, random numbers and sampling patterns.
- [determinism.md](../determinism.md): reproducible results across machines.
- Source: [geometry](../../src/main/java/org/dynamisengine/vectrix/geometry/),
  [sdf](../../src/main/java/org/dynamisengine/vectrix/sdf/),
  [physics](../../src/main/java/org/dynamisengine/vectrix/physics/),
  [linalg](../../src/main/java/org/dynamisengine/vectrix/linalg/),
  [core](../../src/main/java/org/dynamisengine/vectrix/core/) (`IntegratorRK4f`).
