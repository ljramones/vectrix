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
package org.dynamisengine.vectrix.geometry;

import org.dynamisengine.vectrix.core.Vector4dc;
import org.dynamisengine.vectrix.core.Matrix4x3dc;
import org.dynamisengine.vectrix.core.Matrix3dc;
import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.linalg.Eigen3d;

/**
 * Bounding-volume construction for point sets (double precision): Welzl's minimal enclosing sphere, PCA-fitted oriented
 * boxes, the minimal-area enclosing rectangle in 2D (searched over the convex hull's edge directions), an approximate
 * minimal-volume box in 3D, and k-DOPs. Points are {@code x, y, z} (or {@code x, y}) tuples in a flat array, starting
 * at index 0. These are fitting routines: they allocate scratch proportional to the input. The bounds of single shapes
 * ({@code aabOf...} and {@code transformSphere}) do not allocate.
 *
 * @since 2.2.0
 */
public final class BoundingVolumesd {
    private BoundingVolumesd() {
    }

    /**
     * Computes the smallest sphere that contains the first {@code count} points (Welzl 1991, iterative with a fixed
     * pseudo-random order, so the result is deterministic). The work is done in double. A point counts as inside when
     * its squared distance from the center exceeds the squared radius by at most a relative {@code 1e-12}, so points
     * may lie outside the result by about that much.
     *
     * @param xyz the points as packed {@code x, y, z} triples starting at index 0
     * @param count the number of points, at least 1
     * @param dest receives {@code (cx, cy, cz, radius)}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code count <= 0}
     */
    public static Vector4d minimalSphere(double[] xyz, int count, Vector4d dest) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        double[] p = new double[3 * count];
        int[] order = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        long seed = 0x9E3779B97F4A7C15L;
        for (int i = count - 1; i > 0; i--) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int j = (int) ((seed >>> 33) % (i + 1));
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        for (int i = 0; i < count; i++) {
            p[3 * i] = xyz[3 * order[i]];
            p[3 * i + 1] = xyz[3 * order[i] + 1];
            p[3 * i + 2] = xyz[3 * order[i] + 2];
        }
        double[] s = {p[0], p[1], p[2], 0.0};
        for (int i = 1; i < count; i++) {
            if (outside(s, p, i)) {
                s = sphere1(p, i);
                for (int j = 0; j < i; j++) {
                    if (outside(s, p, j)) {
                        s = sphere2(p, i, j);
                        for (int k = 0; k < j; k++) {
                            if (outside(s, p, k)) {
                                s = sphere3(p, i, j, k);
                                for (int l = 0; l < k; l++) {
                                    if (outside(s, p, l)) {
                                        s = sphere4(p, i, j, k, l);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return dest.set(s[0], s[1], s[2], java.lang.Math.sqrt(s[3]));
    }

    /** {@code s = (cx, cy, cz, r²)}. */
    private static boolean outside(double[] s, double[] p, int i) {
        double dx = p[3 * i] - s[0], dy = p[3 * i + 1] - s[1], dz = p[3 * i + 2] - s[2];
        return dx * dx + dy * dy + dz * dz > s[3] * (1.0 + 1e-12) + 1e-24;
    }

    private static double[] sphere1(double[] p, int i) {
        return new double[] {p[3 * i], p[3 * i + 1], p[3 * i + 2], 0.0};
    }

    private static double[] sphere2(double[] p, int i, int j) {
        double cx = 0.5 * (p[3 * i] + p[3 * j]), cy = 0.5 * (p[3 * i + 1] + p[3 * j + 1]);
        double cz = 0.5 * (p[3 * i + 2] + p[3 * j + 2]);
        double dx = p[3 * i] - cx, dy = p[3 * i + 1] - cy, dz = p[3 * i + 2] - cz;
        return new double[] {cx, cy, cz, dx * dx + dy * dy + dz * dz};
    }

    /** Circumscribed sphere of three points (centre in their plane); falls back to the widest pair if collinear. */
    private static double[] sphere3(double[] p, int i, int j, int k) {
        double ax = p[3 * i], ay = p[3 * i + 1], az = p[3 * i + 2];
        double bx = p[3 * j] - ax, by = p[3 * j + 1] - ay, bz = p[3 * j + 2] - az;
        double cx = p[3 * k] - ax, cy = p[3 * k + 1] - ay, cz = p[3 * k + 2] - az;
        double nx = by * cz - bz * cy, ny = bz * cx - bx * cz, nz = bx * cy - by * cx;
        double n2 = nx * nx + ny * ny + nz * nz;
        double b2 = bx * bx + by * by + bz * bz, c2 = cx * cx + cy * cy + cz * cz;
        if (n2 <= 1e-30 * b2 * c2) {
            double[] s1 = sphere2(p, i, j), s2 = sphere2(p, i, k), s3 = sphere2(p, j, k);
            double[] best = s1[3] >= s2[3] ? s1 : s2;
            return best[3] >= s3[3] ? best : s3;
        }
        // centre - a = (b2 (c × n) + c2 (n × b)) / (2 |n|²)
        double ux = b2 * (cy * nz - cz * ny) + c2 * (ny * bz - nz * by);
        double uy = b2 * (cz * nx - cx * nz) + c2 * (nz * bx - nx * bz);
        double uz = b2 * (cx * ny - cy * nx) + c2 * (nx * by - ny * bx);
        double f = 0.5 / n2;
        ux *= f;
        uy *= f;
        uz *= f;
        return new double[] {ax + ux, ay + uy, az + uz, ux * ux + uy * uy + uz * uz};
    }

    /** Circumscribed sphere of four points; falls back to the best three-point sphere if they are coplanar. */
    private static double[] sphere4(double[] p, int i, int j, int k, int l) {
        double ax = p[3 * i], ay = p[3 * i + 1], az = p[3 * i + 2];
        double[] r = new double[9];
        double[] rhs = new double[3];
        int[] idx = {j, k, l};
        for (int m = 0; m < 3; m++) {
            double dx = p[3 * idx[m]] - ax, dy = p[3 * idx[m] + 1] - ay, dz = p[3 * idx[m] + 2] - az;
            r[3 * m] = dx;
            r[3 * m + 1] = dy;
            r[3 * m + 2] = dz;
            rhs[m] = 0.5 * (dx * dx + dy * dy + dz * dz);
        }
        double det = r[0] * (r[4] * r[8] - r[5] * r[7]) - r[1] * (r[3] * r[8] - r[5] * r[6])
                + r[2] * (r[3] * r[7] - r[4] * r[6]);
        double scale = java.lang.Math.abs(rhs[0]) + java.lang.Math.abs(rhs[1]) + java.lang.Math.abs(rhs[2]);
        if (java.lang.Math.abs(det) <= 1e-14 * scale * java.lang.Math.sqrt(scale) || scale == 0.0) {
            double[] best = sphere3(p, i, j, k);
            for (double[] c : new double[][] {sphere3(p, i, j, l), sphere3(p, i, k, l), sphere3(p, j, k, l)}) {
                if (c[3] > best[3]) {
                    best = c;
                }
            }
            return best;
        }
        // Cramer's rule for r u = rhs
        double ux = (rhs[0] * (r[4] * r[8] - r[5] * r[7]) - r[1] * (rhs[1] * r[8] - r[5] * rhs[2])
                + r[2] * (rhs[1] * r[7] - r[4] * rhs[2])) / det;
        double uy = (r[0] * (rhs[1] * r[8] - r[5] * rhs[2]) - rhs[0] * (r[3] * r[8] - r[5] * r[6])
                + r[2] * (r[3] * rhs[2] - rhs[1] * r[6])) / det;
        double uz = (r[0] * (r[4] * rhs[2] - rhs[1] * r[7]) - r[1] * (r[3] * rhs[2] - rhs[1] * r[6])
                + rhs[0] * (r[3] * r[7] - r[4] * r[6])) / det;
        return new double[] {ax + ux, ay + uy, az + uz, ux * ux + uy * uy + uz * uz};
    }

    /**
     * Fits an oriented box to the points by principal component analysis. The axes are the eigenvectors of the point
     * covariance (accumulated in double), ordered by descending variance; they form a proper rotation (det +1). The box
     * is the tight extent of the points along them. When the covariance has repeated eigenvalues (fewer than three
     * points, or symmetric point sets) the axes within that eigenspace are not unique; any choice gives a valid
     * bounding box.
     *
     * @param xyz the points as packed {@code x, y, z} triples starting at index 0
     * @param count the number of points, at least 1
     * @param center receives the box center
     * @param axes receives the box axes as its columns
     * @param halfExtents receives the half extents along the three columns of {@code axes}
     * @throws IllegalArgumentException if {@code count <= 0}
     */
    public static void pcaObb(double[] xyz, int count, Vector3d center, Matrix3d axes, Vector3d halfExtents) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        double mx = 0, my = 0, mz = 0;
        for (int i = 0; i < count; i++) {
            mx += xyz[3 * i];
            my += xyz[3 * i + 1];
            mz += xyz[3 * i + 2];
        }
        mx /= count;
        my /= count;
        mz /= count;
        double cxx = 0, cxy = 0, cxz = 0, cyy = 0, cyz = 0, czz = 0;
        for (int i = 0; i < count; i++) {
            double dx = xyz[3 * i] - mx, dy = xyz[3 * i + 1] - my, dz = xyz[3 * i + 2] - mz;
            cxx += dx * dx;
            cxy += dx * dy;
            cxz += dx * dz;
            cyy += dy * dy;
            cyz += dy * dz;
            czz += dz * dz;
        }
        Matrix3d cov = new Matrix3d(cxx, cxy, cxz, cxy, cyy, cyz,
                cxz, cyz, czz);
        Eigen3d.symmetric(cov, new Vector3d(), axes);
        boxAlongAxes(xyz, count, axes, center, halfExtents);
    }

    /** Tight box of the points along the orthonormal columns of {@code axes}. */
    private static void boxAlongAxes(double[] xyz, int count, Matrix3d axes, Vector3d center, Vector3d halfExtents) {
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        Vector3d a = new Vector3d();
        for (int k = 0; k < 3; k++) {
            axes.getColumn(k, a);
            for (int i = 0; i < count; i++) {
                double d = a.x * xyz[3 * i] + a.y * xyz[3 * i + 1] + a.z * xyz[3 * i + 2];
                lo[k] = java.lang.Math.min(lo[k], d);
                hi[k] = java.lang.Math.max(hi[k], d);
            }
        }
        double cx = 0, cy = 0, cz = 0;
        for (int k = 0; k < 3; k++) {
            axes.getColumn(k, a);
            double m = 0.5 * (lo[k] + hi[k]);
            cx += m * a.x;
            cy += m * a.y;
            cz += m * a.z;
        }
        center.set(cx, cy, cz);
        halfExtents.set((0.5 * (hi[0] - lo[0])), (0.5 * (hi[1] - lo[1])), (0.5 * (hi[2] - lo[2])));
    }

    /**
     * Finds the minimal-area rectangle that encloses {@code count} 2D points and returns its area. It builds the convex
     * hull (monotone chain, exact orientation tests) and tries the direction of every hull edge, since an optimal
     * rectangle has a side on a hull edge. The work is done in double. A single point gives a zero-size rectangle with
     * {@code axisU = (1, 0)}; collinear points give a zero-width one.
     *
     * @param xy the points as packed {@code x, y} pairs starting at index 0
     * @param count the number of points, at least 1 (not checked)
     * @param center receives the rectangle center
     * @param axisU receives the unit direction of the rectangle's first side
     * @param halfExtents receives the half extents along {@code axisU} and along {@code axisU} rotated 90 degrees
     *        counter-clockwise
     * @return the area of the rectangle
     */
    public static double minimalAreaRectangle(double[] xy, int count, Vector2d center, Vector2d axisU,
            Vector2d halfExtents) {
        int[] hull = convexHull(xy, count);
        int h = hull.length;
        double bestArea = Double.MAX_VALUE;
        double bux = 1, buy = 0, bcu = 0, bcv = 0, bhu = 0, bhv = 0;
        for (int e = 0; e < java.lang.Math.max(1, h); e++) {
            double ux;
            double uy;
            if (h >= 2) {
                int i = hull[e], j = hull[(e + 1) % h];
                ux = xy[2 * j] - xy[2 * i];
                uy = xy[2 * j + 1] - xy[2 * i + 1];
                double len = java.lang.Math.sqrt(ux * ux + uy * uy);
                if (len == 0.0) {
                    continue;
                }
                ux /= len;
                uy /= len;
            } else {
                ux = 1;
                uy = 0;
            }
            double minU = Double.MAX_VALUE, maxU = -Double.MAX_VALUE, minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
            for (int k = 0; k < java.lang.Math.max(1, h); k++) {
                int idx = h >= 1 ? hull[k] : 0;
                double x = xy[2 * idx], y = xy[2 * idx + 1];
                double u = x * ux + y * uy, v = -x * uy + y * ux;
                minU = java.lang.Math.min(minU, u);
                maxU = java.lang.Math.max(maxU, u);
                minV = java.lang.Math.min(minV, v);
                maxV = java.lang.Math.max(maxV, v);
            }
            double area = (maxU - minU) * (maxV - minV);
            if (area < bestArea) {
                bestArea = area;
                bux = ux;
                buy = uy;
                bcu = 0.5 * (minU + maxU);
                bcv = 0.5 * (minV + maxV);
                bhu = 0.5 * (maxU - minU);
                bhv = 0.5 * (maxV - minV);
            }
        }
        axisU.set(bux, buy);
        center.set((bcu * bux - bcv * buy), (bcu * buy + bcv * bux));
        halfExtents.set(bhu, bhv);
        return bestArea;
    }

    /** Convex hull indices in counter-clockwise order (Andrew's monotone chain with exact orientation tests). */
    static int[] convexHull(double[] xy, int count) {
        Integer[] idx = new Integer[count];
        for (int i = 0; i < count; i++) {
            idx[i] = i;
        }
        java.util.Arrays.sort(idx, (a, b) -> xy[2 * a] != xy[2 * b] ? Double.compare(xy[2 * a], xy[2 * b])
                : Double.compare(xy[2 * a + 1], xy[2 * b + 1]));
        int[] h = new int[2 * count + 1];
        int k = 0;
        for (int pass = 0; pass < 2; pass++) {
            int start = k;
            for (int n = 0; n < count; n++) {
                int i = pass == 0 ? idx[n] : idx[count - 1 - n];
                while (k >= start + 2 && RobustPredicates.orient2d(xy[2 * h[k - 2]], xy[2 * h[k - 2] + 1],
                        xy[2 * h[k - 1]], xy[2 * h[k - 1] + 1], xy[2 * i], xy[2 * i + 1]) <= 0.0) {
                    k--;
                }
                h[k++] = i;
            }
            k--;
        }
        return java.util.Arrays.copyOf(h, java.lang.Math.max(1, k));
    }

    /**
     * Fits an oriented box of small volume to the points, an approximation of the minimal-volume box. It starts from
     * the {@link #pcaObb} box. Then, for each PCA axis, it takes the minimal-area rectangle of the points projected
     * onto the plane perpendicular to that axis and extrudes it along the axis. The smallest of these four boxes is
     * written; its axes form a proper rotation.
     *
     * @param xyz the points as packed {@code x, y, z} triples starting at index 0
     * @param count the number of points, at least 1
     * @param center receives the box center
     * @param axes receives the box axes as its columns
     * @param halfExtents receives the half extents along the three columns of {@code axes}
     * @return the volume of the box written
     * @throws IllegalArgumentException if {@code count <= 0}
     */
    public static double approximateMinimalObb(double[] xyz, int count, Vector3d center, Matrix3d axes,
            Vector3d halfExtents) {
        pcaObb(xyz, count, center, axes, halfExtents);
        double best = 8.0 * halfExtents.x * halfExtents.y * halfExtents.z;
        Matrix3d pca = new Matrix3d(axes);
        Vector3d[] col = {pca.getColumn(0, new Vector3d()), pca.getColumn(1, new Vector3d()), pca.getColumn(2, new Vector3d())};
        double[] xy = new double[2 * count];
        Vector2d c2 = new Vector2d(), u2 = new Vector2d(), h2 = new Vector2d();
        Vector3d c = new Vector3d(), he = new Vector3d();
        Matrix3d cand = new Matrix3d();
        for (int k = 0; k < 3; k++) {
            Vector3d n = col[k], e1 = col[(k + 1) % 3], e2 = col[(k + 2) % 3];
            for (int i = 0; i < count; i++) {
                double x = xyz[3 * i], y = xyz[3 * i + 1], z = xyz[3 * i + 2];
                xy[2 * i] = x * e1.x + y * e1.y + z * e1.z;
                xy[2 * i + 1] = x * e2.x + y * e2.y + z * e2.z;
            }
            minimalAreaRectangle(xy, count, c2, u2, h2);
            Vector3d a = new Vector3d(e1).mul(u2.x).fma(u2.y, e2).normalize();
            Vector3d b = new Vector3d(n).cross(a).normalize();
            cand.setColumn(0, a).setColumn(1, b).setColumn(2, n);
            if (cand.determinant() < 0) {
                cand.setColumn(1, b.negate());
            }
            boxAlongAxes(xyz, count, cand, c, he);
            double vol = 8.0 * he.x * he.y * he.z;
            if (vol < best) {
                best = vol;
                axes.set(cand);
                center.set(c);
                halfExtents.set(he);
            }
        }
        return best;
    }

    /**
     * Writes the {@code k / 2} directions of a k-DOP and returns how many it wrote. The directions are not normalized:
     * the three coordinate axes first, then for {@code k = 14} the four corner diagonals {@code (1, ±1, ±1)}, for
     * {@code k = 18} the six edge diagonals {@code (1, ±1, 0), (1, 0, ±1), (0, 1, ±1)}, and for {@code k = 26} the
     * corner diagonals followed by the edge diagonals. Allocates its small direction tables on each call.
     *
     * @param k the number of k-DOP planes: 6, 14, 18 or 26
     * @param out receives the directions as packed {@code x, y, z} triples from index 0; at least {@code 3 * k / 2}
     *        elements
     * @return the number of directions written, {@code k / 2}
     * @throws IllegalArgumentException if {@code k} is not 6, 14, 18 or 26
     */
    public static int kDopDirections(int k, double[] out) {
        double[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        double[][] corners = {{1, 1, 1}, {1, 1, -1}, {1, -1, 1}, {1, -1, -1}};
        double[][] edges = {{1, 1, 0}, {1, 0, 1}, {0, 1, 1}, {1, -1, 0}, {1, 0, -1}, {0, 1, -1}};
        double[][][] sets;
        switch (k) {
            case 6:
                sets = new double[][][] {axes};
                break;
            case 14:
                sets = new double[][][] {axes, corners};
                break;
            case 18:
                sets = new double[][][] {axes, edges};
                break;
            case 26:
                sets = new double[][][] {axes, corners, edges};
                break;
            default:
                throw new IllegalArgumentException("k must be 6, 14, 18 or 26");
        }
        int n = 0;
        for (double[][] set : sets) {
            for (double[] d : set) {
                out[3 * n] = d[0];
                out[3 * n + 1] = d[1];
                out[3 * n + 2] = d[2];
                n++;
            }
        }
        return n;
    }

    /**
     * Computes the k-DOP of the points. For each direction {@code i} of {@link #kDopDirections}, the minimum and
     * maximum of {@code p · direction} go to {@code minMax[2i]} and {@code minMax[2i + 1]}. The directions are not
     * normalized, so the diagonal values are not distances. The 6-DOP is the AABB, as {@code minX, maxX, minY, maxY,
     * minZ, maxZ}. With {@code count == 0} every minimum is {@code +∞} and every maximum {@code −∞}. Allocates a small
     * table.
     *
     * @param xyz the points as packed {@code x, y, z} triples starting at index 0
     * @param count the number of points, 0 or more
     * @param k the number of k-DOP planes: 6, 14, 18 or 26
     * @param minMax receives the {@code k} bounds; at least {@code k} elements
     * @throws IllegalArgumentException if {@code k} is not 6, 14, 18 or 26
     */
    public static void kDop(double[] xyz, int count, int k, double[] minMax) {
        double[] dirs = new double[39];
        int n = kDopDirections(k, dirs);
        for (int d = 0; d < n; d++) {
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < count; i++) {
                double v = xyz[3 * i] * dirs[3 * d] + xyz[3 * i + 1] * dirs[3 * d + 1] + xyz[3 * i + 2] * dirs[3 * d + 2];
                lo = java.lang.Math.min(lo, v);
                hi = java.lang.Math.max(hi, v);
            }
            minMax[2 * d] = lo;
            minMax[2 * d + 1] = hi;
        }
    }

    /**
     * Computes the axis-aligned bounds of an oriented box. Each world half extent is {@code Σ_j |axes_ij| h_j}, which
     * is exact (tight).
     *
     * @param center the box center
     * @param axes the box axes as columns, unit length
     * @param halfExtents the half extents along the columns of {@code axes}
     * @param min receives the minimum corner; may alias an input
     * @param max receives the maximum corner; may alias an input
     * @since 2.4.0
     */
    public static void aabOfObb(Vector3dc center, Matrix3dc axes, Vector3dc halfExtents, Vector3d min, Vector3d max) {
        double hx = halfExtents.x(), hy = halfExtents.y(), hz = halfExtents.z();
        double cx = center.x(), cy = center.y(), cz = center.z();
        double ex = Math.abs(axes.m00()) * hx + Math.abs(axes.m10()) * hy + Math.abs(axes.m20()) * hz;
        double ey = Math.abs(axes.m01()) * hx + Math.abs(axes.m11()) * hy + Math.abs(axes.m21()) * hz;
        double ez = Math.abs(axes.m02()) * hx + Math.abs(axes.m12()) * hy + Math.abs(axes.m22()) * hz;
        min.set(cx - ex, cy - ey, cz - ez);
        max.set(cx + ex, cy + ey, cz + ez);
    }

    /**
     * Computes the axis-aligned bounds of a capsule. Exact.
     *
     * @param a one end of the capsule's segment
     * @param b the other end of the capsule's segment
     * @param r the capsule radius, non-negative
     * @param min receives the minimum corner; may alias an input
     * @param max receives the maximum corner; may alias an input
     * @since 2.4.0
     */
    public static void aabOfCapsule(Vector3dc a, Vector3dc b, double r, Vector3d min, Vector3d max) {
        double ax = a.x(), ay = a.y(), az = a.z(), bx = b.x(), by = b.y(), bz = b.z();
        min.set(Math.min(ax, bx) - r, Math.min(ay, by) - r, Math.min(az, bz) - r);
        max.set(Math.max(ax, bx) + r, Math.max(ay, by) + r, Math.max(az, bz) + r);
    }

    /**
     * Computes the axis-aligned bounds of a solid cylinder.
     * <p>
     * The bounds are exact up to rounding: along world axis {@code i}, each cap disc extends
     * {@code r · sqrt(u_j² + u_k²) / |u|}, where {@code u = b − a}. That form does not cancel. A zero-length cylinder
     * has no defined disc orientation, so it is bounded as a sphere of radius {@code r}.
     *
     * @param a the center of one cap
     * @param b the center of the other cap
     * @param r the cylinder radius, non-negative
     * @param min receives the minimum corner; may alias an input
     * @param max receives the maximum corner; may alias an input
     * @since 2.4.0
     */
    public static void aabOfCylinder(Vector3dc a, Vector3dc b, double r, Vector3d min, Vector3d max) {
        double ax = a.x(), ay = a.y(), az = a.z(), bx = b.x(), by = b.y(), bz = b.z();
        double ux = (double) bx - ax, uy = (double) by - ay, uz = (double) bz - az;
        double len = Math.sqrt(ux * ux + uy * uy + uz * uz);
        double ex = r, ey = r, ez = r;
        if (len > 0) {
            ex = (r * Math.sqrt(uy * uy + uz * uz) / len);
            ey = (r * Math.sqrt(ux * ux + uz * uz) / len);
            ez = (r * Math.sqrt(ux * ux + uy * uy) / len);
        }
        min.set(Math.min(ax, bx) - ex, Math.min(ay, by) - ey, Math.min(az, bz) - ez);
        max.set(Math.max(ax, bx) + ex, Math.max(ay, by) + ey, Math.max(az, bz) + ez);
    }

    /**
     * Computes the axis-aligned bounds of {@code count} points transformed by the affine {@code m}, with no allocation.
     * With {@code count == 0}, {@code min} is {@code +∞} and {@code max} is {@code −∞}.
     *
     * @param xyz the points as packed {@code x, y, z} triples starting at index 0
     * @param count the number of points, 0 or more
     * @param m the affine transform applied to each point
     * @param min receives the minimum corner
     * @param max receives the maximum corner
     * @since 2.4.0
     */
    public static void aabOfTransformedPoints(double[] xyz, int count, Matrix4x3dc m, Vector3d min, Vector3d max) {
        double m00 = m.m00(), m01 = m.m01(), m02 = m.m02(), m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        double m20 = m.m20(), m21 = m.m21(), m22 = m.m22(), m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            double x = xyz[3 * i], y = xyz[3 * i + 1], z = xyz[3 * i + 2];
            double tx = Math.fma(m00, x, Math.fma(m10, y, Math.fma(m20, z, m30)));
            double ty = Math.fma(m01, x, Math.fma(m11, y, Math.fma(m21, z, m31)));
            double tz = Math.fma(m02, x, Math.fma(m12, y, Math.fma(m22, z, m32)));
            minX = Math.min(minX, tx);
            minY = Math.min(minY, ty);
            minZ = Math.min(minZ, tz);
            maxX = Math.max(maxX, tx);
            maxY = Math.max(maxY, ty);
            maxZ = Math.max(maxZ, tz);
        }
        min.set(minX, minY, minZ);
        max.set(maxX, maxY, maxZ);
    }


    /**
     * The largest singular value of the 3×3 matrix with columns {@code (m00, m01, m02)}, {@code (m10, m11, m12)} and
     * {@code (m20, m21, m22)}: the most it stretches any vector.
     * <p>
     * It is computed as the square root of the largest eigenvalue of {@code LᵀL}, by cyclic Jacobi rotations,
     * which are accurate to rounding even when eigenvalues coincide. It is then rounded up by a few ulps, so a
     * radius scaled by it never under-covers. The largest column length, a common shortcut, equals this value only
     * for orthogonal columns; under shear it is smaller, and the bound is then too small.
     */
    static double maxStretch(double m00, double m01, double m02, double m10, double m11, double m12, double m20,
            double m21, double m22) {
        // A = L^T L, symmetric: a_ij = column_i . column_j
        double a00 = m00 * m00 + m01 * m01 + m02 * m02, a11 = m10 * m10 + m11 * m11 + m12 * m12;
        double a22 = m20 * m20 + m21 * m21 + m22 * m22;
        double a01 = m00 * m10 + m01 * m11 + m02 * m12, a02 = m00 * m20 + m01 * m21 + m02 * m22;
        double a12 = m10 * m20 + m11 * m21 + m12 * m22;
        for (int sweep = 0; sweep < 12; sweep++) {
            double off = a01 * a01 + a02 * a02 + a12 * a12;
            if (off <= 1e-36 * (a00 * a00 + a11 * a11 + a22 * a22)) {
                break;
            }
            // rotate (0,1)
            if (a01 != 0) {
                double th = (a11 - a00) / (2 * a01);
                double t = Math.copySign(1.0, th) / (Math.abs(th) + Math.sqrt(th * th + 1));
                double c = 1 / Math.sqrt(t * t + 1), sn = t * c;
                a00 -= t * a01;
                a11 += t * a01;
                double r0 = a02, r1 = a12;
                a02 = c * r0 - sn * r1;
                a12 = sn * r0 + c * r1;
                a01 = 0;
            }
            // rotate (0,2)
            if (a02 != 0) {
                double th = (a22 - a00) / (2 * a02);
                double t = Math.copySign(1.0, th) / (Math.abs(th) + Math.sqrt(th * th + 1));
                double c = 1 / Math.sqrt(t * t + 1), sn = t * c;
                a00 -= t * a02;
                a22 += t * a02;
                double r0 = a01, r1 = a12;
                a01 = c * r0 - sn * r1;
                a12 = sn * r0 + c * r1;
                a02 = 0;
            }
            // rotate (1,2)
            if (a12 != 0) {
                double th = (a22 - a11) / (2 * a12);
                double t = Math.copySign(1.0, th) / (Math.abs(th) + Math.sqrt(th * th + 1));
                double c = 1 / Math.sqrt(t * t + 1), sn = t * c;
                a11 -= t * a12;
                a22 += t * a12;
                double r0 = a01, r1 = a02;
                a01 = c * r0 - sn * r1;
                a02 = sn * r0 + c * r1;
                a12 = 0;
            }
        }
        double lambda = Math.max(a00, Math.max(a11, a22));
        double sigma = Math.sqrt(Math.max(lambda, 0));
        return sigma + 8 * Math.ulp(sigma);
    }

    /**
     * Computes a bounding sphere of a sphere transformed by the affine {@code m}: the transformed center, and the
     * radius scaled by the largest singular value of {@code m}'s linear part. That singular value comes from a Jacobi
     * eigenvalue solve in double and is rounded up by a few ulps, and the scaled radius is rounded up by two ulps, so
     * the result contains the transformed sphere. That holds for any linear part, including shear and non-uniform
     * scale, where the largest-column-length shortcut is too small. Does not allocate.
     *
     * @param sphere the sphere: center in {@code x, y, z}, radius in {@code w}
     * @param m the affine transform
     * @param dest receives the transformed sphere in the same layout; may alias {@code sphere}
     * @return {@code dest}
     * @since 2.4.0
     */
    public static Vector4d transformSphere(Vector4dc sphere, Matrix4x3dc m, Vector4d dest) {
        double x = sphere.x(), y = sphere.y(), z = sphere.z(), r = sphere.w();
        double s = maxStretch(m.m00(), m.m01(), m.m02(), m.m10(), m.m11(), m.m12(), m.m20(), m.m21(), m.m22());
        double rr = r * s;
        return dest.set(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(), m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                m.m02() * x + m.m12() * y + m.m22() * z + m.m32(), rr + 2 * Math.ulp(rr));
    }

}
