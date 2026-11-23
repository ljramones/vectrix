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

import org.dynamisengine.vectrix.core.Matrix3f;
import org.dynamisengine.vectrix.core.Matrix3fc;
import org.dynamisengine.vectrix.core.Matrix4x3fc;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.core.Vector4fc;
import org.dynamisengine.vectrix.linalg.Eigen3f;

/**
 * Bounding-volume construction for point sets (single precision; computed in double where it matters): Welzl's
 * minimal enclosing sphere, PCA-fitted oriented boxes, the minimal-area enclosing rectangle in 2D (rotating
 * calipers), an approximate minimal-volume box in 3D, and k-DOPs. Points are {@code x, y, z} (or {@code x, y})
 * tuples in a flat array. These are fitting routines: they allocate scratch proportional to the input.
 *
 * @since 2.2.0
 */
public final class BoundingVolumesf {
    private BoundingVolumesf() {
    }

    /**
     * The smallest sphere containing all {@code count} points (Welzl 1991, iterative with a fixed pseudo-random
     * order, so the result is deterministic). Writes {@code (cx, cy, cz, radius)} to {@code dest}.
     */
    public static Vector4f minimalSphere(float[] xyz, int count, Vector4f dest) {
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
        return dest.set((float) s[0], (float) s[1], (float) s[2], (float) java.lang.Math.sqrt(s[3]));
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
     * Oriented box fitted by principal component analysis: the axes are the eigenvectors of the point covariance
     * (columns of {@code axes}, a rotation), and the box is the tight extent along them. When the covariance has
     * repeated eigenvalues (fewer than three points, or symmetric point sets) the axes within that eigenspace are not
     * unique; any choice gives a valid bounding box.
     */
    public static void pcaObb(float[] xyz, int count, Vector3f center, Matrix3f axes, Vector3f halfExtents) {
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
        Matrix3f cov = new Matrix3f((float) cxx, (float) cxy, (float) cxz, (float) cxy, (float) cyy, (float) cyz,
                (float) cxz, (float) cyz, (float) czz);
        Eigen3f.symmetric(cov, new Vector3f(), axes);
        boxAlongAxes(xyz, count, axes, center, halfExtents);
    }

    /** Tight box of the points along the orthonormal columns of {@code axes}. */
    private static void boxAlongAxes(float[] xyz, int count, Matrix3f axes, Vector3f center, Vector3f halfExtents) {
        double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        Vector3f a = new Vector3f();
        for (int k = 0; k < 3; k++) {
            axes.getColumn(k, a);
            for (int i = 0; i < count; i++) {
                double d = (double) a.x * xyz[3 * i] + (double) a.y * xyz[3 * i + 1] + (double) a.z * xyz[3 * i + 2];
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
        center.set((float) cx, (float) cy, (float) cz);
        halfExtents.set((float) (0.5 * (hi[0] - lo[0])), (float) (0.5 * (hi[1] - lo[1])), (float) (0.5 * (hi[2] - lo[2])));
    }

    /**
     * The minimal-area rectangle enclosing {@code count} 2D points: convex hull (monotone chain, exact orientation
     * tests) and the hull-edge directions (an optimal rectangle has a side on a hull edge). Writes the centre, the
     * unit direction of its first side, and its half extents along that side and the perpendicular one; returns the
     * area.
     */
    public static float minimalAreaRectangle(float[] xy, int count, Vector2f center, Vector2f axisU,
            Vector2f halfExtents) {
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
        axisU.set((float) bux, (float) buy);
        center.set((float) (bcu * bux - bcv * buy), (float) (bcu * buy + bcv * bux));
        halfExtents.set((float) bhu, (float) bhv);
        return (float) bestArea;
    }

    /** Convex hull indices in counter-clockwise order (Andrew's monotone chain with exact orientation tests). */
    static int[] convexHull(float[] xy, int count) {
        Integer[] idx = new Integer[count];
        for (int i = 0; i < count; i++) {
            idx[i] = i;
        }
        java.util.Arrays.sort(idx, (a, b) -> xy[2 * a] != xy[2 * b] ? Float.compare(xy[2 * a], xy[2 * b])
                : Float.compare(xy[2 * a + 1], xy[2 * b + 1]));
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
     * An approximation of the minimal-volume oriented box: for each PCA axis, the minimal-area rectangle of the points
     * projected onto the plane perpendicular to it, extruded along it; the smallest of these and the PCA box. Writes
     * the box as {@link #pcaObb} does and returns its volume.
     */
    public static float approximateMinimalObb(float[] xyz, int count, Vector3f center, Matrix3f axes,
            Vector3f halfExtents) {
        pcaObb(xyz, count, center, axes, halfExtents);
        float best = 8.0f * halfExtents.x * halfExtents.y * halfExtents.z;
        Matrix3f pca = new Matrix3f(axes);
        Vector3f[] col = {pca.getColumn(0, new Vector3f()), pca.getColumn(1, new Vector3f()), pca.getColumn(2, new Vector3f())};
        float[] xy = new float[2 * count];
        Vector2f c2 = new Vector2f(), u2 = new Vector2f(), h2 = new Vector2f();
        Vector3f c = new Vector3f(), he = new Vector3f();
        Matrix3f cand = new Matrix3f();
        for (int k = 0; k < 3; k++) {
            Vector3f n = col[k], e1 = col[(k + 1) % 3], e2 = col[(k + 2) % 3];
            for (int i = 0; i < count; i++) {
                float x = xyz[3 * i], y = xyz[3 * i + 1], z = xyz[3 * i + 2];
                xy[2 * i] = x * e1.x + y * e1.y + z * e1.z;
                xy[2 * i + 1] = x * e2.x + y * e2.y + z * e2.z;
            }
            minimalAreaRectangle(xy, count, c2, u2, h2);
            Vector3f a = new Vector3f(e1).mul(u2.x).fma(u2.y, e2).normalize();
            Vector3f b = new Vector3f(n).cross(a).normalize();
            cand.setColumn(0, a).setColumn(1, b).setColumn(2, n);
            if (cand.determinant() < 0) {
                cand.setColumn(1, b.negate());
            }
            boxAlongAxes(xyz, count, cand, c, he);
            float vol = 8.0f * he.x * he.y * he.z;
            if (vol < best) {
                best = vol;
                axes.set(cand);
                center.set(c);
                halfExtents.set(he);
            }
        }
        return best;
    }

    /** Number of directions of a k-DOP: {@code k / 2}. */
    public static int kDopDirections(int k, float[] out) {
        float[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        float[][] corners = {{1, 1, 1}, {1, 1, -1}, {1, -1, 1}, {1, -1, -1}};
        float[][] edges = {{1, 1, 0}, {1, 0, 1}, {0, 1, 1}, {1, -1, 0}, {1, 0, -1}, {0, 1, -1}};
        float[][][] sets;
        switch (k) {
            case 6:
                sets = new float[][][] {axes};
                break;
            case 14:
                sets = new float[][][] {axes, corners};
                break;
            case 18:
                sets = new float[][][] {axes, edges};
                break;
            case 26:
                sets = new float[][][] {axes, corners, edges};
                break;
            default:
                throw new IllegalArgumentException("k must be 6, 14, 18 or 26");
        }
        int n = 0;
        for (float[][] set : sets) {
            for (float[] d : set) {
                out[3 * n] = d[0];
                out[3 * n + 1] = d[1];
                out[3 * n + 2] = d[2];
                n++;
            }
        }
        return n;
    }

    /**
     * k-DOP ({@code k} = 6, 14, 18 or 26) of the points: for each of the {@code k / 2} directions of
     * {@link #kDopDirections} (not normalised), the minimum and maximum of {@code p · direction} into
     * {@code minMax[2i]} and {@code minMax[2i + 1]}. The 6-DOP is the AABB.
     */
    public static void kDop(float[] xyz, int count, int k, float[] minMax) {
        float[] dirs = new float[39];
        int n = kDopDirections(k, dirs);
        for (int d = 0; d < n; d++) {
            float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < count; i++) {
                float v = xyz[3 * i] * dirs[3 * d] + xyz[3 * i + 1] * dirs[3 * d + 1] + xyz[3 * i + 2] * dirs[3 * d + 2];
                lo = java.lang.Math.min(lo, v);
                hi = java.lang.Math.max(hi, v);
            }
            minMax[2 * d] = lo;
            minMax[2 * d + 1] = hi;
        }
    }

    /**
     * Axis-aligned bounds of an oriented box: center {@code center}, unit axes in the columns of {@code axes}, half
     * extents {@code halfExtents}. Each world half extent is {@code Σ_j |axes_ij| h_j}, which is exact (tight).
     *
     * @since 2.4.0
     */
    public static void aabOfObb(Vector3fc center, Matrix3fc axes, Vector3fc halfExtents, Vector3f min, Vector3f max) {
        float hx = halfExtents.x(), hy = halfExtents.y(), hz = halfExtents.z();
        float cx = center.x(), cy = center.y(), cz = center.z();
        float ex = Math.abs(axes.m00()) * hx + Math.abs(axes.m10()) * hy + Math.abs(axes.m20()) * hz;
        float ey = Math.abs(axes.m01()) * hx + Math.abs(axes.m11()) * hy + Math.abs(axes.m21()) * hz;
        float ez = Math.abs(axes.m02()) * hx + Math.abs(axes.m12()) * hy + Math.abs(axes.m22()) * hz;
        min.set(cx - ex, cy - ey, cz - ez);
        max.set(cx + ex, cy + ey, cz + ez);
    }

    /**
     * Axis-aligned bounds of a capsule (segment {@code a}–{@code b}, radius {@code r}). Exact.
     *
     * @since 2.4.0
     */
    public static void aabOfCapsule(Vector3fc a, Vector3fc b, float r, Vector3f min, Vector3f max) {
        float ax = a.x(), ay = a.y(), az = a.z(), bx = b.x(), by = b.y(), bz = b.z();
        min.set(Math.min(ax, bx) - r, Math.min(ay, by) - r, Math.min(az, bz) - r);
        max.set(Math.max(ax, bx) + r, Math.max(ay, by) + r, Math.max(az, bz) + r);
    }

    /**
     * Axis-aligned bounds of a solid cylinder with cap centers {@code a} and {@code b} and radius {@code r}.
     * <p>
     * The bounds are exact: along world axis {@code i}, each cap disc extends {@code r · sqrt(u_j² + u_k²) / |u|},
     * where {@code u = b − a}. That form does not cancel. A zero-length cylinder has no defined disc orientation, so
     * it is bounded as a sphere of radius {@code r}.
     *
     * @since 2.4.0
     */
    public static void aabOfCylinder(Vector3fc a, Vector3fc b, float r, Vector3f min, Vector3f max) {
        float ax = a.x(), ay = a.y(), az = a.z(), bx = b.x(), by = b.y(), bz = b.z();
        double ux = (double) bx - ax, uy = (double) by - ay, uz = (double) bz - az;
        double len = Math.sqrt(ux * ux + uy * uy + uz * uz);
        float ex = r, ey = r, ez = r;
        if (len > 0) {
            ex = (float) (r * Math.sqrt(uy * uy + uz * uz) / len);
            ey = (float) (r * Math.sqrt(ux * ux + uz * uz) / len);
            ez = (float) (r * Math.sqrt(ux * ux + uy * uy) / len);
        }
        min.set(Math.min(ax, bx) - ex, Math.min(ay, by) - ey, Math.min(az, bz) - ez);
        max.set(Math.max(ax, bx) + ex, Math.max(ay, by) + ey, Math.max(az, bz) + ez);
    }

    /**
     * Axis-aligned bounds of {@code count} points ({@code xyz}, packed) transformed by the affine {@code m}, with no
     * allocation. With {@code count == 0}, {@code min} is {@code +∞} and {@code max} is {@code −∞}.
     *
     * @since 2.4.0
     */
    public static void aabOfTransformedPoints(float[] xyz, int count, Matrix4x3fc m, Vector3f min, Vector3f max) {
        float m00 = m.m00(), m01 = m.m01(), m02 = m.m02(), m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        float m20 = m.m20(), m21 = m.m21(), m22 = m.m22(), m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            float x = xyz[3 * i], y = xyz[3 * i + 1], z = xyz[3 * i + 2];
            float tx = Math.fma(m00, x, Math.fma(m10, y, Math.fma(m20, z, m30)));
            float ty = Math.fma(m01, x, Math.fma(m11, y, Math.fma(m21, z, m31)));
            float tz = Math.fma(m02, x, Math.fma(m12, y, Math.fma(m22, z, m32)));
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
     * The bounding sphere of a sphere ({@code sphere.xyz} center, {@code sphere.w} radius) under the affine
     * {@code m}. The radius is scaled by the exact largest singular value of the linear part, computed in double,
     * then rounded up, so the result always contains the transformed sphere. That stays true under shear, where the
     * largest-column-length shortcut is too small. See {@link BoundingVolumesd#transformSphere}.
     *
     * @since 2.4.0
     */
    public static Vector4f transformSphere(Vector4fc sphere, Matrix4x3fc m, Vector4f dest) {
        double x = sphere.x(), y = sphere.y(), z = sphere.z(), r = sphere.w();
        double s = BoundingVolumesd.maxStretch(m.m00(), m.m01(), m.m02(), m.m10(), m.m11(), m.m12(), m.m20(), m.m21(),
                m.m22());
        double rd = r * s;
        float rf = (float) rd;
        if (rf < rd) {
            rf = Math.nextUp(rf);
        }
        return dest.set((float) (m.m00() * x + m.m10() * y + m.m20() * z + m.m30()),
                (float) (m.m01() * x + m.m11() * y + m.m21() * z + m.m31()),
                (float) (m.m02() * x + m.m12() * y + m.m22() * z + m.m32()), rf);
    }

}
