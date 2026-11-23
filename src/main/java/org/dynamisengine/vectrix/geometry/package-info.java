/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
/**
 * Geometric queries on points, rays, segments, triangles, boxes, spheres and polygons: intersection and overlap
 * tests ({@link org.dynamisengine.vectrix.geometry.Intersectionf}), closest points and contacts, bounding volume
 * construction, frustum culling of single objects ({@link org.dynamisengine.vectrix.geometry.FrustumIntersection})
 * and of structure-of-arrays batches ({@link org.dynamisengine.vectrix.geometry.CullingKernels}), exact orientation
 * predicates, mesh normals and tangents, and Morton and Hilbert codes. The package provides functions on plain
 * coordinates and vectors; it defines no shape types.
 * <p>
 * Sphere and circle methods are not uniform about radii: some take the radius and others its square, as each
 * method's parameter documentation states.
 * <p>
 * <b>Contract.</b> Threading: the static classes keep no state and are safe to call from any thread;
 * {@code FrustumIntersection}, {@code FrustumPlanes}, {@code FrustumRayBuilder}, {@code RayAabIntersection} and
 * {@code PolygonsIntersection} are mutable objects, safe for concurrent queries only while nobody updates them.
 * Allocation: the per-object queries and the culling batch do not allocate; construction-time methods do
 * ({@code BoundingVolumes*} hulls, spheres and k-DOPs, the {@code PolygonsIntersection} constructor, the
 * {@code MeshMath} per-vertex methods), as do {@code ClosestPoints*.distanceSquaredTriangleTriangle},
 * {@code SpatialCodes.hilbert3d}/{@code hilbert3dToXyz}, {@code MeshMath.triangleMinAngle} and the exact path of
 * {@code RobustPredicates}. Determinism: results follow the library's determinism settings (see
 * {@code docs/determinism.md}); the culling batch gives the same results on its SIMD and scalar paths, and
 * {@code RobustPredicates} gives the exact sign on every platform. Errors: most methods do not validate their
 * arguments, so NaN inputs give NaN or {@code false} results and degenerate input gives the result each method
 * documents; methods that take counts or option codes ({@code BoundingVolumes*}, {@code PolygonsIntersection})
 * throw {@link java.lang.IllegalArgumentException}, and the older array methods ({@code MeshMath},
 * {@code CullingKernels}) rely on array bounds checks. Precision and naming: classes ending in {@code f} work in
 * float and their {@code d} twins in double; the unsuffixed classes work in float, except {@code RobustPredicates}
 * (double, with float overloads) and {@code GeometryUtils} (float and double overloads).
 */
package org.dynamisengine.vectrix.geometry;
