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
package org.dynamisengine.vectrix.linalg;

import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector4d;

/**
 * Small least-squares problems (double precision): overdetermined linear systems by Householder QR (more stable than
 * the normal equations), and plane and line fits to point sets by principal component analysis.
 *
 * @since 2.2.0
 */
public final class LeastSquaresd {
    private LeastSquaresd() {
    }

    /**
     * Minimises {@code ||A x - b||₂} for the {@code rows × cols} matrix {@code a} (row-major, {@code rows >= cols})
     * by Householder QR, computed in double. A diagonal element of {@code R} at most {@code 1e-13} times the largest
     * counts as rank deficient. Arrays start at index 0. Allocates {@code O(rows²)} scratch per call: meant for
     * fitting, not per-frame loops.
     *
     * @param a the matrix, row-major, {@code rows * cols} elements
     * @param b the right-hand side, {@code rows} elements
     * @param rows the number of equations
     * @param cols the number of unknowns, at least 1 and at most {@code rows}
     * @param x receives the solution, {@code cols} elements
     * @return false if {@code A} is rank deficient (to working precision); {@code x} is then unspecified
     * @throws IllegalArgumentException if {@code rows < cols} or {@code cols <= 0}
     */
    public static boolean solve(double[] a, double[] b, int rows, int cols, double[] x) {
        if (rows < cols || cols <= 0) {
            throw new IllegalArgumentException("need rows >= cols > 0");
        }
        return LinalgKernels.leastSquares(a, b, rows, cols, x);
    }

    /**
     * Best-fit plane {@code n·p + d = 0} (unit {@code n}) through {@code count} points stored as {@code x, y, z}
     * triples: the normal is the covariance eigenvector with the smallest eigenvalue. Writes {@code (n, d)} to {@code
     * dest}. The sign of the normal is arbitrary. With fewer than three points, or collinear points, the plane is one
     * of many that fit. Uses a per-thread workspace; does not allocate after the thread's first call.
     *
     * @param xyz the points, {@code x, y, z} per point, from index 0
     * @param count the number of points, at least 1
     * @param dest receives the unit normal in x, y, z and the offset {@code d} in w
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code count <= 0} or {@code xyz} holds fewer than {@code count} points
     */
    public static Vector4d fitPlane(double[] xyz, int count, Vector4d dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] c = ws.centroid3;
        double[] cov = covariance(xyz, count, c, ws.cov9);
        double[] w = ws.out3a, v = ws.out9a;
        LinalgKernels.eigenSym3(cov, w, v);
        double nx = v[2], ny = v[5], nz = v[8];
        return dest.set(nx, ny, nz, -(nx * c[0] + ny * c[1] + nz * c[2]));
    }

    /**
     * Best-fit line through {@code count} points: a point on it (the centroid) and its unit direction (the covariance
     * eigenvector with the largest eigenvalue). The sign of the direction is arbitrary. Uses a per-thread workspace;
     * does not allocate after the thread's first call.
     *
     * @param xyz the points, {@code x, y, z} per point, from index 0
     * @param count the number of points, at least 1
     * @param point receives the centroid of the points
     * @param direction receives the unit direction of the line
     * @throws IllegalArgumentException if {@code count <= 0} or {@code xyz} holds fewer than {@code count} points
     */
    public static void fitLine(double[] xyz, int count, Vector3d point, Vector3d direction) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] c = ws.centroid3;
        double[] cov = covariance(xyz, count, c, ws.cov9);
        double[] w = ws.out3a, v = ws.out9a;
        LinalgKernels.eigenSym3(cov, w, v);
        point.set(c[0], c[1], c[2]);
        direction.set(v[0], v[3], v[6]);
    }

    private static double[] covariance(double[] xyz, int count, double[] centroid, double[] m) {
        if (count <= 0 || xyz.length < 3 * count) {
            throw new IllegalArgumentException("need count > 0 points in xyz");
        }
        centroid[0] = 0.0;
        centroid[1] = 0.0;
        centroid[2] = 0.0;
        for (int i = 0; i < 9; i++) {
            m[i] = 0.0;
        }
        for (int i = 0; i < count; i++) {
            centroid[0] += xyz[3 * i];
            centroid[1] += xyz[3 * i + 1];
            centroid[2] += xyz[3 * i + 2];
        }
        centroid[0] /= count;
        centroid[1] /= count;
        centroid[2] /= count;
        for (int i = 0; i < count; i++) {
            double dx = xyz[3 * i] - centroid[0], dy = xyz[3 * i + 1] - centroid[1], dz = xyz[3 * i + 2] - centroid[2];
            m[0] += dx * dx;
            m[1] += dx * dy;
            m[2] += dx * dz;
            m[4] += dy * dy;
            m[5] += dy * dz;
            m[8] += dz * dz;
        }
        m[3] = m[1];
        m[6] = m[2];
        m[7] = m[5];
        return m;
    }
}
