/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
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
package org.dynamisengine.vectrix.camera;

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Clustered / froxel lighting grids (double precision): a {@code tilesX × tilesY} screen grid times {@code slices}
 * exponential depth slices between {@code near} and {@code far} (Olsson et al. 2012; the Doom 2016 slicing),
 * cluster bounds in view space, and conservative light-vs-cluster tests.
 * <p>
 * Slice {@code k} spans view distances {@code [sliceDepth(k), sliceDepth(k + 1))} with
 * {@code sliceDepth(k) = near · (far / near)^(k / slices)}. {@link #sliceIndex} is corrected against
 * {@link #sliceDepth}, so a depth is always assigned to the slice whose bounds contain it, even at the boundaries
 * where the logarithm rounds. View space is right-handed looking down {@code −z}; screen tiles are numbered from
 * NDC {@code (−1, −1)}.
 *
 * @since 2.3.0
 */
public final class ClusterGridd {
    private ClusterGridd() {
    }

    /**
     * Returns the view distance where slice {@code k} begins: {@code near · (far / near)^(k / slices)}. Indices at or
     * below 0 give {@code near} and indices at or above {@code slices} give {@code far}, both exactly.
     *
     * @param k the slice index; {@code slices} gives the far end of the last slice
     * @param slices the number of depth slices, at least 1
     * @param near the view distance where the grid begins, greater than 0
     * @param far the view distance where the grid ends, greater than {@code near}
     * @return the view distance of the slice's near boundary
     */
    public static double sliceDepth(int k, int slices, double near, double far) {
        if (k <= 0) {
            return near;
        }
        if (k >= slices) {
            return far;
        }
        return (near * AccurateMath.pow((double) far / near, (double) k / slices));
    }

    /**
     * Returns the slice that contains view distance {@code d}, consistent with {@link #sliceDepth}. Distances at or
     * below {@code near}, and NaN, give 0; distances at or beyond {@code far} give {@code slices − 1}.
     *
     * @param d the view distance, along {@code −z}
     * @param slices the number of depth slices, at least 1
     * @param near the view distance where the grid begins, greater than 0
     * @param far the view distance where the grid ends, greater than {@code near}
     * @return the slice index, in {@code [0, slices − 1]}
     */
    public static int sliceIndex(double d, int slices, double near, double far) {
        if (!(d > near)) {
            return 0;
        }
        int k = (int) java.lang.Math.floor(AccurateMath.log((double) d / near) / AccurateMath.log((double) far / near)
                * slices);
        k = java.lang.Math.max(0, java.lang.Math.min(k, slices - 1));
        // the logarithm can round across a boundary: settle against the exact bounds
        while (k > 0 && d < sliceDepth(k, slices, near, far)) {
            k--;
        }
        while (k < slices - 1 && d >= sliceDepth(k + 1, slices, near, far)) {
            k++;
        }
        return k;
    }

    /**
     * Returns the linear cluster index of tile {@code (x, y)} in slice {@code k}:
     * {@code (k · tilesY + y) · tilesX + x}. The arguments are not range-checked.
     *
     * @param x the tile column, in {@code [0, tilesX)}
     * @param y the tile row, in {@code [0, tilesY)}, counted from NDC {@code y = −1}
     * @param k the depth slice
     * @param tilesX the number of tile columns
     * @param tilesY the number of tile rows
     * @return the linear cluster index
     */
    public static int clusterIndex(int x, int y, int k, int tilesX, int tilesY) {
        return (k * tilesY + y) * tilesX + x;
    }

    /**
     * Cluster index of a view-space point, or −1 outside the grid's depth range or screen, for a symmetric
     * perspective with half-angle tangents {@code tanHalfX}, {@code tanHalfY}. The grid covers view distances
     * {@code [near, far)} and NDC {@code [−1, 1)} on both axes.
     *
     * @param viewPos the point in view space
     * @param tanHalfX the tangent of half the horizontal field of view
     * @param tanHalfY the tangent of half the vertical field of view
     * @param tilesX the number of tile columns
     * @param tilesY the number of tile rows
     * @param slices the number of depth slices
     * @param near the view distance where the grid begins, greater than 0
     * @param far the view distance where the grid ends, greater than {@code near}
     * @return the linear cluster index (see {@link #clusterIndex}), or −1 if the point is outside the grid
     */
    public static int clusterOf(Vector3dc viewPos, double tanHalfX, double tanHalfY, int tilesX, int tilesY,
            int slices, double near, double far) {
        double d = -viewPos.z();
        if (!(d >= near) || !(d < far)) {
            return -1;
        }
        double ndcX = viewPos.x() / (d * (double) tanHalfX), ndcY = viewPos.y() / (d * (double) tanHalfY);
        if (!(ndcX >= -1.0) || !(ndcX < 1.0) || !(ndcY >= -1.0) || !(ndcY < 1.0)) {
            return -1;
        }
        int x = java.lang.Math.min((int) ((ndcX + 1.0) * 0.5 * tilesX), tilesX - 1);
        int y = java.lang.Math.min((int) ((ndcY + 1.0) * 0.5 * tilesY), tilesY - 1);
        return clusterIndex(x, y, sliceIndex(d, slices, near, far), tilesX, tilesY);
    }

    /**
     * View-space AABB of cluster {@code (x, y, k)} (the AABB of the froxel's eight corners) for a symmetric
     * perspective with half-angle tangents {@code tanHalfX}, {@code tanHalfY}.
     *
     * @param x the tile column, in {@code [0, tilesX)}
     * @param y the tile row, in {@code [0, tilesY)}, counted from NDC {@code y = −1}
     * @param k the depth slice, in {@code [0, slices)}
     * @param tilesX the number of tile columns
     * @param tilesY the number of tile rows
     * @param slices the number of depth slices
     * @param near the view distance where the grid begins, greater than 0
     * @param far the view distance where the grid ends, greater than {@code near}
     * @param tanHalfX the tangent of half the horizontal field of view
     * @param tanHalfY the tangent of half the vertical field of view
     * @param min receives the minimum corner of the box
     * @param max receives the maximum corner of the box
     */
    public static void clusterAabb(int x, int y, int k, int tilesX, int tilesY, int slices, double near, double far,
            double tanHalfX, double tanHalfY, Vector3d min, Vector3d max) {
        double d0 = sliceDepth(k, slices, near, far), d1 = sliceDepth(k + 1, slices, near, far);
        double x0 = (2.0 * x / tilesX - 1.0) * tanHalfX, x1 = (2.0 * (x + 1) / tilesX - 1.0) * tanHalfX;
        double y0 = (2.0 * y / tilesY - 1.0) * tanHalfY, y1 = (2.0 * (y + 1) / tilesY - 1.0) * tanHalfY;
        // x_view = ndc-slope · d: the extremes are at d0 or d1 depending on the slope's sign
        min.set(java.lang.Math.min(x0 * d0, x0 * d1), java.lang.Math.min(y0 * d0, y0 * d1),
                -d1);
        max.set(java.lang.Math.max(x1 * d0, x1 * d1), java.lang.Math.max(y1 * d0, y1 * d1),
                -d0);
    }

    /**
     * Tests whether a sphere touches an axis-aligned box, using the exact closest-point distance. Touching counts as
     * intersecting.
     *
     * @param center the sphere center (in the box's space, normally view space)
     * @param radius the sphere radius, at least 0
     * @param min the minimum corner of the box
     * @param max the maximum corner of the box
     * @return {@code true} if the sphere and the box intersect
     */
    public static boolean sphereIntersectsAabb(Vector3dc center, double radius, Vector3dc min, Vector3dc max) {
        double dx = java.lang.Math.max(java.lang.Math.max(min.x() - center.x(), center.x() - max.x()), 0.0);
        double dy = java.lang.Math.max(java.lang.Math.max(min.y() - center.y(), center.y() - max.y()), 0.0);
        double dz = java.lang.Math.max(java.lang.Math.max(min.z() - center.z(), center.z() - max.z()), 0.0);
        return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }

    /**
     * Conservative spot-light test against a sphere bounding a cluster (Wronski's cone-sphere test): {@code false}
     * only if the sphere lies entirely outside the cone of unit axis {@code dir}, half-angle with cosine and sine
     * {@code cosHalfAngle}, {@code sinHalfAngle}, apex {@code apex} and range {@code range}. The angular test is
     * exact; the range test uses the plane at {@code range}, so it can report spheres just beyond the spherical cap.
     *
     * @param apex the cone apex (the light position)
     * @param dir the cone axis, unit length
     * @param cosHalfAngle the cosine of the cone's half-angle
     * @param sinHalfAngle the sine of the cone's half-angle
     * @param range the light range along the axis
     * @param center the center of the bounding sphere
     * @param radius the radius of the bounding sphere
     * @return {@code false} if the sphere is certainly outside the cone, {@code true} otherwise
     */
    public static boolean coneIntersectsSphere(Vector3dc apex, Vector3dc dir, double cosHalfAngle, double sinHalfAngle,
            double range, Vector3dc center, double radius) {
        double vx = center.x() - apex.x(), vy = center.y() - apex.y(), vz = center.z() - apex.z();
        double vLenSq = vx * vx + vy * vy + vz * vz;
        double v1 = vx * dir.x() + vy * dir.y() + vz * dir.z();
        double distanceToCone = cosHalfAngle * java.lang.Math.sqrt(java.lang.Math.max(vLenSq - v1 * v1, 0.0))
                - v1 * sinHalfAngle;
        boolean angleCull = distanceToCone > radius;
        boolean frontCull = v1 > radius + range;
        boolean backCull = v1 < -radius;
        return !(angleCull || frontCull || backCull);
    }
}
