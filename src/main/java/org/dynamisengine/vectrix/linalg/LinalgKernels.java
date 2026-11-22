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

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * Double-precision kernels behind the {@code linalg} classes, on row-major arrays ({@code a[r * n + c]}). The float
 * and double public classes convert to and from the matrix types and call these, so both precisions share one
 * implementation computed in double.
 */
final class LinalgKernels {
    /**
     * Per-thread scratch storage, so the fixed-size kernels do not allocate. Each kernel uses its own fields, so nested
     * calls (svd3 calls eigenSym3 and qr) never share storage.
     */
    static final class Workspace {
        final double[] in9 = new double[16];
        final double[] out9a = new double[16];
        final double[] out9b = new double[16];
        final double[] out3a = new double[4];
        final double[] out3b = new double[4];
        final double[] in3 = new double[4];
        final double[] eigM = new double[9];
        final double[] eigQ = new double[9];
        final double[] eigD = new double[3];
        final int[] eigIdx = new int[3];
        final double[] qrV = new double[4];
        final double[] svdAta = new double[9];
        final double[] svdW = new double[3];
        final double[] svdB = new double[9];
        final double[] svdR = new double[9];
        final double[] polU = new double[9];
        final double[] polS = new double[3];
        final double[] polV = new double[9];
        final double[] mulTmp = new double[9];
        final double[] solveY = new double[4];
        final double[] lu16 = new double[16];
        final int[] perm4 = new int[4];
        final double[] condLu = new double[16];
        final int[] condPerm = new int[4];
        final double[] condE = new double[4];
        final double[] condX = new double[4];
        final double[] lieAxis = new double[3];
        final double[] lieC1 = new double[3];
        final double[] lieC2 = new double[3];
        final double[] cov9 = new double[9];
        final double[] centroid3 = new double[3];
    }

    private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);

    static Workspace ws() {
        return WORKSPACE.get();
    }

    private LinalgKernels() {
    }

    static double det3(double[] a) {
        return a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6])
                + a[2] * (a[3] * a[7] - a[4] * a[6]);
    }

    static void mul3(double[] a, double[] b, double[] out) {
        double[] r = ws().mulTmp;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[3 * i + j] = a[3 * i] * b[j] + a[3 * i + 1] * b[3 + j] + a[3 * i + 2] * b[6 + j];
            }
        }
        System.arraycopy(r, 0, out, 0, 9);
    }

    /**
     * Symmetric 3×3 eigen-decomposition by cyclic Jacobi rotations: {@code a = v diag(w) vᵀ}, with {@code w} sorted in
     * descending order and the eigenvectors in the columns of {@code v}, which is a proper rotation (det +1). Only
     * the symmetric part of {@code a} is used.
     */
    static void eigenSym3(double[] a, double[] w, double[] v) {
        Workspace ws = ws();
        double[] m = ws.eigM;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                m[3 * i + j] = 0.5 * (a[3 * i + j] + a[3 * j + i]);
            }
        }
        double[] q = ws.eigQ;
        for (int i = 0; i < 9; i++) {
            q[i] = i % 4 == 0 ? 1.0 : 0.0;
        }
        double scale = 0.0;
        for (double x : m) {
            scale = java.lang.Math.max(scale, java.lang.Math.abs(x));
        }
        for (int sweep = 0; sweep < 64 && scale > 0.0; sweep++) {
            double off = m[1] * m[1] + m[2] * m[2] + m[5] * m[5];
            if (off <= 1e-36 * scale * scale) {
                break;
            }
            for (int p = 0; p < 2; p++) {
                for (int r = p + 1; r < 3; r++) {
                    double apr = m[3 * p + r];
                    if (java.lang.Math.abs(apr) <= 1e-300) {
                        continue;
                    }
                    double app = m[3 * p + p], arr = m[3 * r + r];
                    double theta = (arr - app) / (2.0 * apr);
                    double t = java.lang.Math.signum(theta) / (java.lang.Math.abs(theta)
                            + java.lang.Math.sqrt(theta * theta + 1.0));
                    if (theta == 0.0) {
                        t = 1.0;
                    }
                    double c = 1.0 / java.lang.Math.sqrt(t * t + 1.0), s = t * c;
                    // m = Jᵀ m J with the rotation J in the (p, r) plane
                    for (int k = 0; k < 3; k++) {
                        double mkp = m[3 * k + p], mkr = m[3 * k + r];
                        m[3 * k + p] = c * mkp - s * mkr;
                        m[3 * k + r] = s * mkp + c * mkr;
                    }
                    for (int k = 0; k < 3; k++) {
                        double mpk = m[3 * p + k], mrk = m[3 * r + k];
                        m[3 * p + k] = c * mpk - s * mrk;
                        m[3 * r + k] = s * mpk + c * mrk;
                    }
                    for (int k = 0; k < 3; k++) {
                        double qkp = q[3 * k + p], qkr = q[3 * k + r];
                        q[3 * k + p] = c * qkp - s * qkr;
                        q[3 * k + r] = s * qkp + c * qkr;
                    }
                }
            }
        }
        // sort descending
        int[] idx = ws.eigIdx;
        idx[0] = 0;
        idx[1] = 1;
        idx[2] = 2;
        double[] d = ws.eigD;
        d[0] = m[0];
        d[1] = m[4];
        d[2] = m[8];
        for (int i = 0; i < 2; i++) {
            for (int j = i + 1; j < 3; j++) {
                if (d[idx[j]] > d[idx[i]]) {
                    int t = idx[i];
                    idx[i] = idx[j];
                    idx[j] = t;
                }
            }
        }
        for (int c = 0; c < 3; c++) {
            w[c] = d[idx[c]];
            for (int r = 0; r < 3; r++) {
                v[3 * r + c] = q[3 * r + idx[c]];
            }
        }
        if (det3(v) < 0.0) {
            for (int r = 0; r < 3; r++) {
                v[3 * r + 2] = -v[3 * r + 2];
            }
        }
    }

    /**
     * Householder QR of the {@code m × n} matrix {@code a} ({@code m >= n}): {@code a = q r} with {@code q}
     * {@code m × m} orthogonal and {@code r} {@code m × n} upper triangular with a non-negative diagonal.
     */
    static void qr(double[] a, int m, int n, double[] q, double[] r) {
        System.arraycopy(a, 0, r, 0, m * n);
        for (int i = 0; i < m * m; i++) {
            q[i] = 0.0;
        }
        for (int i = 0; i < m; i++) {
            q[i * m + i] = 1.0;
        }
        double[] v = m <= 4 ? ws().qrV : new double[m];
        for (int k = 0; k < java.lang.Math.min(n, m - 1); k++) {
            double norm = 0.0;
            for (int i = k; i < m; i++) {
                norm += r[i * n + k] * r[i * n + k];
            }
            norm = java.lang.Math.sqrt(norm);
            if (norm == 0.0) {
                continue;
            }
            double alpha = r[k * n + k] > 0.0 ? -norm : norm;
            double vv = 0.0;
            for (int i = k; i < m; i++) {
                v[i] = r[i * n + k] - (i == k ? alpha : 0.0);
                vv += v[i] * v[i];
            }
            if (vv == 0.0) {
                continue;
            }
            // r = (I - 2 v vᵀ / vᵀv) r, q = q (I - 2 v vᵀ / vᵀv)
            for (int j = 0; j < n; j++) {
                double dot = 0.0;
                for (int i = k; i < m; i++) {
                    dot += v[i] * r[i * n + j];
                }
                double f = 2.0 * dot / vv;
                for (int i = k; i < m; i++) {
                    r[i * n + j] -= f * v[i];
                }
            }
            for (int i = 0; i < m; i++) {
                double dot = 0.0;
                for (int j = k; j < m; j++) {
                    dot += q[i * m + j] * v[j];
                }
                double f = 2.0 * dot / vv;
                for (int j = k; j < m; j++) {
                    q[i * m + j] -= f * v[j];
                }
            }
        }
        // make the diagonal of r non-negative
        for (int k = 0; k < n; k++) {
            if (r[k * n + k] < 0.0) {
                for (int j = 0; j < n; j++) {
                    r[k * n + j] = -r[k * n + j];
                }
                for (int i = 0; i < m; i++) {
                    q[i * m + k] = -q[i * m + k];
                }
            }
        }
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < java.lang.Math.min(i, n); j++) {
                r[i * n + j] = 0.0;
            }
        }
    }

    /**
     * Signed 3×3 SVD (McAdams et al. 2011): {@code a = u diag(s) vᵀ} with {@code u} and {@code v} proper rotations and
     * {@code s[0] >= s[1] >= |s[2]|}; {@code s[2]} is negative exactly when {@code det(a) < 0}. {@code v} comes from
     * the eigenvectors of {@code aᵀa}, and {@code u} from a QR decomposition of {@code a v}, which keeps the accuracy of
     * the small singular values.
     */
    static void svd3(double[] a, double[] u, double[] s, double[] v) {
        Workspace ws = ws();
        double[] ata = ws.svdAta;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                ata[3 * i + j] = a[i] * a[j] + a[3 + i] * a[3 + j] + a[6 + i] * a[6 + j];
            }
        }
        double[] w = ws.svdW;
        eigenSym3(ata, w, v);
        double[] b = ws.svdB;
        mul3(a, v, b);
        double[] r = ws.svdR;
        qr(b, 3, 3, u, r);
        s[0] = r[0];
        s[1] = r[4];
        s[2] = r[8];
        if (det3(u) < 0.0) {
            for (int i = 0; i < 3; i++) {
                u[3 * i + 2] = -u[3 * i + 2];
            }
            s[2] = -s[2];
        }
    }

    /** Polar decomposition {@code a = rot · stretch} with {@code rot} a proper rotation and {@code stretch} symmetric. */
    static void polar3(double[] a, double[] rot, double[] stretch) {
        Workspace ws = ws();
        double[] u = ws.polU, s = ws.polS, v = ws.polV;
        svd3(a, u, s, v);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                rot[3 * i + j] = u[3 * i] * v[3 * j] + u[3 * i + 1] * v[3 * j + 1] + u[3 * i + 2] * v[3 * j + 2];
                stretch[3 * i + j] = v[3 * i] * s[0] * v[3 * j] + v[3 * i + 1] * s[1] * v[3 * j + 1]
                        + v[3 * i + 2] * s[2] * v[3 * j + 2];
            }
        }
    }

    /** Cholesky {@code a = l lᵀ} of a symmetric positive-definite {@code n × n} matrix; false if it is not. */
    static boolean cholesky(double[] a, int n, double[] l) {
        for (int i = 0; i < n * n; i++) {
            l[i] = 0.0;
        }
        for (int j = 0; j < n; j++) {
            double d = a[j * n + j];
            for (int k = 0; k < j; k++) {
                d -= l[j * n + k] * l[j * n + k];
            }
            if (!(d > 0.0)) {
                return false;
            }
            double ljj = java.lang.Math.sqrt(d);
            l[j * n + j] = ljj;
            for (int i = j + 1; i < n; i++) {
                double x = a[i * n + j];
                for (int k = 0; k < j; k++) {
                    x -= l[i * n + k] * l[j * n + k];
                }
                l[i * n + j] = x / ljj;
            }
        }
        return true;
    }

    /** LDLᵀ {@code a = l diag(d) lᵀ} (unit lower {@code l}) of a symmetric matrix; false on a zero pivot. */
    static boolean ldlt(double[] a, int n, double[] l, double[] d) {
        for (int i = 0; i < n * n; i++) {
            l[i] = 0.0;
        }
        for (int j = 0; j < n; j++) {
            double dj = a[j * n + j];
            for (int k = 0; k < j; k++) {
                dj -= l[j * n + k] * l[j * n + k] * d[k];
            }
            if (dj == 0.0 || !Double.isFinite(dj)) {
                return false;
            }
            d[j] = dj;
            l[j * n + j] = 1.0;
            for (int i = j + 1; i < n; i++) {
                double x = a[i * n + j];
                for (int k = 0; k < j; k++) {
                    x -= l[i * n + k] * l[j * n + k] * d[k];
                }
                l[i * n + j] = x / dj;
            }
        }
        return true;
    }

    /**
     * LU decomposition with partial pivoting, in place: {@code lu} holds L (unit, below the diagonal) and U, and row
     * {@code i} of {@code P a} is row {@code perm[i]} of {@code a}. False if a pivot is exactly zero.
     */
    static boolean lu(double[] a, int n, double[] lu, int[] perm) {
        System.arraycopy(a, 0, lu, 0, n * n);
        for (int i = 0; i < n; i++) {
            perm[i] = i;
        }
        for (int k = 0; k < n; k++) {
            int p = k;
            double best = java.lang.Math.abs(lu[k * n + k]);
            for (int i = k + 1; i < n; i++) {
                double v = java.lang.Math.abs(lu[i * n + k]);
                if (v > best) {
                    best = v;
                    p = i;
                }
            }
            if (best == 0.0) {
                return false;
            }
            if (p != k) {
                for (int j = 0; j < n; j++) {
                    double t = lu[k * n + j];
                    lu[k * n + j] = lu[p * n + j];
                    lu[p * n + j] = t;
                }
                int t = perm[k];
                perm[k] = perm[p];
                perm[p] = t;
            }
            for (int i = k + 1; i < n; i++) {
                double f = lu[i * n + k] / lu[k * n + k];
                lu[i * n + k] = f;
                for (int j = k + 1; j < n; j++) {
                    lu[i * n + j] -= f * lu[k * n + j];
                }
            }
        }
        return true;
    }

    /** Solves {@code a x = b} from {@link #lu}'s output. */
    static void luSolve(double[] lu, int n, int[] perm, double[] b, double[] x) {
        double[] y = ws().solveY;
        for (int i = 0; i < n; i++) {
            double s = b[perm[i]];
            for (int k = 0; k < i; k++) {
                s -= lu[i * n + k] * y[k];
            }
            y[i] = s;
        }
        for (int i = n - 1; i >= 0; i--) {
            double s = y[i];
            for (int k = i + 1; k < n; k++) {
                s -= lu[i * n + k] * x[k];
            }
            x[i] = s / lu[i * n + i];
        }
    }

    /** Solves {@code l lᵀ x = b} from a Cholesky factor. */
    static void choleskySolve(double[] l, int n, double[] b, double[] x) {
        double[] y = ws().solveY;
        for (int i = 0; i < n; i++) {
            double s = b[i];
            for (int k = 0; k < i; k++) {
                s -= l[i * n + k] * y[k];
            }
            y[i] = s / l[i * n + i];
        }
        for (int i = n - 1; i >= 0; i--) {
            double s = y[i];
            for (int k = i + 1; k < n; k++) {
                s -= l[k * n + i] * x[k];
            }
            x[i] = s / l[i * n + i];
        }
    }

    /** Solves {@code l diag(d) lᵀ x = b} from {@link #ldlt}'s output. */
    static void ldltSolve(double[] l, double[] d, int n, double[] b, double[] x) {
        double[] y = ws().solveY;
        for (int i = 0; i < n; i++) {
            double s = b[i];
            for (int k = 0; k < i; k++) {
                s -= l[i * n + k] * y[k];
            }
            y[i] = s;
        }
        for (int i = 0; i < n; i++) {
            y[i] /= d[i];
        }
        for (int i = n - 1; i >= 0; i--) {
            double s = y[i];
            for (int k = i + 1; k < n; k++) {
                s -= l[k * n + i] * x[k];
            }
            x[i] = s;
        }
    }

    /** 1-norm condition number {@code ||a||₁ ||a⁻¹||₁}; infinite if {@code a} is singular. */
    static double condition1(double[] a, int n) {
        Workspace ws = ws();
        double[] lu = ws.condLu;
        int[] perm = ws.condPerm;
        if (!lu(a, n, lu, perm)) {
            return Double.POSITIVE_INFINITY;
        }
        double normA = 0.0, normInv = 0.0;
        double[] e = ws.condE, x = ws.condX;
        for (int j = 0; j < n; j++) {
            double col = 0.0;
            for (int i = 0; i < n; i++) {
                col += java.lang.Math.abs(a[i * n + j]);
                e[i] = i == j ? 1.0 : 0.0;
            }
            normA = java.lang.Math.max(normA, col);
            luSolve(lu, n, perm, e, x);
            double inv = 0.0;
            for (int i = 0; i < n; i++) {
                inv += java.lang.Math.abs(x[i]);
            }
            normInv = java.lang.Math.max(normInv, inv);
        }
        return normA * normInv;
    }

    /**
     * Least squares {@code min ||a x - b||} for an {@code m × n} matrix ({@code m >= n}) by Householder QR. Allocates
     * {@code O(m²)} scratch: it is meant for fitting, not per-frame loops.
     */
    static boolean leastSquares(double[] a, double[] b, int m, int n, double[] x) {
        double[] q = new double[m * m], r = new double[m * n];
        qr(a, m, n, q, r);
        double[] qtb = new double[n];
        for (int i = 0; i < n; i++) {
            double s = 0.0;
            for (int k = 0; k < m; k++) {
                s += q[k * m + i] * b[k];
            }
            qtb[i] = s;
        }
        double tiny = 0.0;
        for (int i = 0; i < n; i++) {
            tiny = java.lang.Math.max(tiny, java.lang.Math.abs(r[i * n + i]));
        }
        tiny *= 1e-13;
        for (int i = n - 1; i >= 0; i--) {
            double rii = r[i * n + i];
            if (java.lang.Math.abs(rii) <= tiny) {
                return false;
            }
            double s = qtb[i];
            for (int k = i + 1; k < n; k++) {
                s -= r[i * n + k] * x[k];
            }
            x[i] = s / rii;
        }
        return true;
    }

    // ---- SO(3) and SE(3) ----

    /** Rotation matrix {@code exp([w]×)} (Rodrigues), row-major. */
    static void expSo3(double wx, double wy, double wz, double[] r) {
        double t2 = wx * wx + wy * wy + wz * wz;
        double t = java.lang.Math.sqrt(t2);
        double a;
        double b;
        if (t < 1e-4) {
            a = 1.0 - t2 / 6.0 + t2 * t2 / 120.0;
            b = 0.5 - t2 / 24.0 + t2 * t2 / 720.0;
        } else {
            a = AccurateMath.sin(t) / t;
            b = (1.0 - AccurateMath.cos(t)) / t2;
        }
        // R = I + a W + b W², W = [w]×
        r[0] = 1.0 - b * (wy * wy + wz * wz);
        r[1] = -a * wz + b * wx * wy;
        r[2] = a * wy + b * wx * wz;
        r[3] = a * wz + b * wx * wy;
        r[4] = 1.0 - b * (wx * wx + wz * wz);
        r[5] = -a * wx + b * wy * wz;
        r[6] = -a * wy + b * wx * wz;
        r[7] = a * wx + b * wy * wz;
        r[8] = 1.0 - b * (wx * wx + wy * wy);
    }

    /** Rotation vector {@code w} with {@code exp([w]×) = r}, {@code |w| <= π}. */
    static void logSo3(double[] r, double[] w) {
        double c = java.lang.Math.max(-1.0, java.lang.Math.min(1.0, 0.5 * (r[0] + r[4] + r[8] - 1.0)));
        double vx = r[7] - r[5], vy = r[2] - r[6], vz = r[3] - r[1];
        double sin2 = java.lang.Math.sqrt(vx * vx + vy * vy + vz * vz); // = 2 sin θ
        double theta = AccurateMath.atan2(0.5 * sin2, c);
        if (theta < 1e-4) {
            double f = 0.5 * (1.0 + theta * theta / 6.0);
            w[0] = f * vx;
            w[1] = f * vy;
            w[2] = f * vz;
        } else if (java.lang.Math.PI - theta < 1e-3) {
            // near π the skew part vanishes: take the axis from the symmetric part (R + I) / 2 = a aᵀ
            // (R + Rᵀ)/2 = cosθ I + (1 - cosθ) a aᵀ, exact for every θ: a_k² = (R_kk - c)/(1 - c) and
            // a_i a_k = (R_ik + R_ki) / (2 (1 - c))
            int k = r[0] >= r[4] && r[0] >= r[8] ? 0 : (r[4] >= r[8] ? 1 : 2);
            double[] axis = ws().lieAxis;
            double oneMinusC = 1.0 - c;
            double dk = java.lang.Math.sqrt(java.lang.Math.max(0.0, (r[4 * k] - c) / oneMinusC));
            for (int i = 0; i < 3; i++) {
                axis[i] = i == k ? dk : (r[3 * i + k] + r[3 * k + i]) / (2.0 * oneMinusC * dk);
            }
            double n = java.lang.Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
            // orient the axis with the remaining skew part, so that slightly-less-than-π angles keep their sign
            double sign = axis[0] * vx + axis[1] * vy + axis[2] * vz < 0.0 ? -1.0 : 1.0;
            for (int i = 0; i < 3; i++) {
                w[i] = sign * theta * axis[i] / n;
            }
        } else {
            double f = theta / sin2;
            w[0] = f * vx;
            w[1] = f * vy;
            w[2] = f * vz;
        }
    }

    /** SE(3) exponential: rotation {@code r} (row-major) and translation {@code t} from the twist {@code (w, v)}. */
    static void expSe3(double[] w, double[] v, double[] r, double[] t) {
        expSo3(w[0], w[1], w[2], r);
        double t2 = w[0] * w[0] + w[1] * w[1] + w[2] * w[2];
        double th = java.lang.Math.sqrt(t2);
        double b;
        double c;
        if (th < 1e-4) {
            b = 0.5 - t2 / 24.0;
            c = 1.0 / 6.0 - t2 / 120.0;
        } else {
            b = (1.0 - AccurateMath.cos(th)) / t2;
            c = (th - AccurateMath.sin(th)) / (t2 * th);
        }
        // t = V v, V = I + b W + c W²
        Workspace ws = ws();
        double[] wv = cross(w, v, ws.lieC1);
        double[] wwv = cross(w, wv, ws.lieC2);
        for (int i = 0; i < 3; i++) {
            t[i] = v[i] + b * wv[i] + c * wwv[i];
        }
    }

    /** SE(3) logarithm: the twist {@code (w, v)} of rotation {@code r} and translation {@code t}. */
    static void logSe3(double[] r, double[] t, double[] w, double[] v) {
        logSo3(r, w);
        double t2 = w[0] * w[0] + w[1] * w[1] + w[2] * w[2];
        double th = java.lang.Math.sqrt(t2);
        double e;
        if (th < 1e-4) {
            e = 1.0 / 12.0 + t2 / 720.0;
        } else {
            e = (1.0 - th * AccurateMath.sin(th) / (2.0 * (1.0 - AccurateMath.cos(th)))) / t2;
        }
        // v = V⁻¹ t, V⁻¹ = I - W / 2 + e W²
        Workspace ws = ws();
        double[] wt = cross(w, t, ws.lieC1);
        double[] wwt = cross(w, wt, ws.lieC2);
        for (int i = 0; i < 3; i++) {
            v[i] = t[i] - 0.5 * wt[i] + e * wwt[i];
        }
    }

    private static double[] cross(double[] a, double[] b, double[] out) {
        double x = a[1] * b[2] - a[2] * b[1], y = a[2] * b[0] - a[0] * b[2], z = a[0] * b[1] - a[1] * b[0];
        out[0] = x;
        out[1] = y;
        out[2] = z;
        return out;
    }
}
