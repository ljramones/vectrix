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
package org.dynamisengine.vectrix.dsp;

/**
 * The linear Kalman filter's predict and update steps over caller-held state ({@code n} states, {@code m}
 * measurements), in double, with row-major matrices.
 * <p>
 * The update computes the gain with a Cholesky solve of the innovation covariance {@code S = HPHᵀ + R} (no matrix
 * inverse) and the covariance in Joseph form {@code P = (I - KH)P(I - KH)ᵀ + KRKᵀ}, which stays symmetric and
 * positive semi-definite despite rounding; both steps also symmetrize {@code P} exactly. Every sum runs in a fixed
 * order, so results are reproducible. Nothing allocates; calls take {@link #scratchSize(int, int)} doubles of
 * scratch.
 * <p>
 * All arrays are used from index 0. Their sizes are checked up front: an array that is null or too short throws
 * {@link IllegalArgumentException} before the state changes.
 *
 * @since 2.5.0
 */
public final class Kalman {

    private Kalman() {
    }

    /**
     * Returns the scratch size of {@link #predict}, {@link #update} and {@link #innovationDistance}.
     *
     * @param n the number of states, at least 1
     * @param m the number of measurements, at least 0 (0 for {@link #predict} alone)
     * @return the number of scratch doubles
     * @throws IllegalArgumentException if {@code n < 1}, {@code m < 0}, or the size exceeds an array
     */
    public static int scratchSize(int n, int m) {
        if (n < 1 || m < 0) {
            throw new IllegalArgumentException("need n >= 1 and m >= 0: " + n + ", " + m);
        }
        long nn = n;
        long size = 3 * nn * nn + 2 * nn * m + (long) m * m + 2L * m + nn;
        if (size > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("dimensions too large: " + n + ", " + m);
        }
        return (int) size;
    }

    /**
     * Predict: {@code x = F·x}, {@code P = F·P·Fᵀ + Q}.
     *
     * @param n the number of states, at least 1
     * @param x the {@code n} state values, updated
     * @param p the {@code n×n} covariance, updated
     * @param f the {@code n×n} transition matrix
     * @param q the {@code n×n} process noise covariance
     * @param scratch at least {@code scratchSize(n, 0)} doubles of working memory
     * @throws IllegalArgumentException if {@code n < 1} or an array is null or too short (checked before anything
     *         is written)
     */
    public static void predict(int n, double[] x, double[] p, double[] f, double[] q, double[] scratch) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be at least 1: " + n);
        }
        need(scratch, scratchSize(n, 0));
        need(x, n, "x");
        need(p, n * n, "p");
        need(f, n * n, "f");
        need(q, n * n, "q");
        int xs = 0;
        int fp = n;
        for (int i = 0; i < n; i++) {
            double s = 0;
            for (int k = 0; k < n; k++) {
                s = Math.fma(f[i * n + k], x[k], s);
            }
            scratch[xs + i] = s;
        }
        System.arraycopy(scratch, xs, x, 0, n);
        matmul(n, n, n, f, 0, false, p, 0, false, scratch, fp);      // F·P
        matmul(n, n, n, scratch, fp, false, f, 0, true, p, 0);       // (F·P)·Fᵀ
        for (int i = 0; i < n * n; i++) {
            p[i] += q[i];
        }
        symmetrize(n, p);
    }

    /**
     * Update with a measurement {@code z}: innovation {@code y = z - H·x}, gain {@code K = P·Hᵀ·S⁻¹},
     * {@code x += K·y}, and the Joseph-form covariance.
     *
     * @param n the number of states, at least 1
     * @param m the number of measurements, at least 1
     * @param x the {@code n} state values, updated
     * @param p the {@code n×n} covariance, updated
     * @param h the {@code m×n} measurement matrix
     * @param r the {@code m×m} measurement noise covariance
     * @param z the {@code m} measurements
     * @param scratch at least {@code scratchSize(n, m)} doubles of working memory
     * @return the squared Mahalanobis distance {@code yᵀ·S⁻¹·y} of the innovation, as
     *         {@link #innovationDistance} returns it
     * @throws IllegalArgumentException if {@code n} or {@code m} is less than 1, or an array is null or too short
     *         (checked before anything is written)
     * @throws IllegalStateException if {@code S} is not positive definite; {@code x} and {@code p} are then unchanged
     */
    public static double update(int n, int m, double[] x, double[] p, double[] h, double[] r, double[] z,
            double[] scratch) {
        innovation(n, m, x, p, h, r, z, scratch);
        return correct(n, m, x, p, h, r, scratch);
    }

    /**
     * The squared Mahalanobis distance {@code yᵀ·S⁻¹·y} of the innovation {@code y = z - H·x}, with
     * {@code S = H·P·Hᵀ + R}, without updating the estimate: gate a measurement with it, then call {@link #update}
     * only for the measurements you accept. For a correct measurement it follows a chi-square distribution with
     * {@code m} degrees of freedom. The value is the one {@link #update} returns for the same arguments, bit for bit.
     *
     * @param n the number of states, at least 1
     * @param m the number of measurements, at least 1
     * @param x the {@code n} state values, not modified
     * @param p the {@code n×n} covariance, not modified
     * @param h the {@code m×n} measurement matrix
     * @param r the {@code m×m} measurement noise covariance
     * @param z the {@code m} measurements
     * @param scratch at least {@code scratchSize(n, m)} doubles of working memory
     * @return the squared Mahalanobis distance of the innovation
     * @throws IllegalArgumentException if {@code n} or {@code m} is less than 1, or an array is null or too short
     * @throws IllegalStateException if {@code S} is not positive definite
     * @since 3.0.0
     */
    public static double innovationDistance(int n, int m, double[] x, double[] p, double[] h, double[] r,
            double[] z, double[] scratch) {
        innovation(n, m, x, p, h, r, z, scratch);
        return distance(n, m, p, h, r, scratch);
    }

    /** Checks the arguments of an update and writes the innovation {@code y = z - H·x} to {@code scratch[0 .. m)}. */
    private static void innovation(int n, int m, double[] x, double[] p, double[] h, double[] r, double[] z,
            double[] scratch) {
        if (n < 1 || m < 1) {
            throw new IllegalArgumentException("n and m must be at least 1: " + n + ", " + m);
        }
        need(scratch, scratchSize(n, m));
        need(x, n, "x");
        need(p, n * n, "p");
        need(h, m * n, "h");
        need(r, m * m, "r");
        need(z, m, "z");
        for (int i = 0; i < m; i++) {
            double v = z[i];
            for (int j = 0; j < n; j++) {
                v = Math.fma(-h[i * n + j], x[j], v);
            }
            scratch[i] = v;
        }
    }

    /**
     * The squared Mahalanobis distance of the innovation {@code y = scratch[0 .. m)}, given the measurement
     * Jacobian {@code h}, computed as {@link #correct} computes it.
     */
    static double distance(int n, int m, double[] p, double[] h, double[] r, double[] scratch) {
        int y = 0;
        int s = y + m;
        int ph = s + m * m;      // P·Hᵀ, n×m
        int w = ph + 2 * n * m + 3 * n * n;
        factorS(n, m, p, h, r, scratch, s, ph);
        System.arraycopy(scratch, y, scratch, w, m);
        solve(m, scratch, s, scratch, w);
        double maha = 0;
        for (int i = 0; i < m; i++) {
            maha = Math.fma(scratch[y + i], scratch[w + i], maha);
        }
        return maha;
    }

    /** {@code P·Hᵀ} at {@code ph}, and the Cholesky factor of {@code S = H·P·Hᵀ + R} at {@code s}. */
    private static void factorS(int n, int m, double[] p, double[] h, double[] r, double[] scratch, int s, int ph) {
        matmul(n, n, m, p, 0, false, h, 0, true, scratch, ph);      // P·Hᵀ
        for (int i = 0; i < m; i++) {                                // S = H·(P·Hᵀ) + R
            for (int j = 0; j < m; j++) {
                double v = r[i * m + j];
                for (int l = 0; l < n; l++) {
                    v = Math.fma(h[i * n + l], scratch[ph + l * m + j], v);
                }
                scratch[s + i * m + j] = v;
            }
        }
        symmetrize(m, scratch, s);
        if (!cholesky(m, scratch, s)) {
            throw new IllegalStateException("the innovation covariance is not positive definite");
        }
    }

    /**
     * The update given the innovation {@code y = scratch[0 .. m)} and the measurement Jacobian {@code h}: shared by
     * the linear and the extended filter.
     */
    static double correct(int n, int m, double[] x, double[] p, double[] h, double[] r, double[] scratch) {
        int y = 0;
        int s = y + m;
        int ph = s + m * m;      // P·Hᵀ, n×m
        int k = ph + n * m;      // K, n×m
        int ikh = k + n * m;     // I - K·H, n×n
        int t1 = ikh + n * n;    // n×n
        int t2 = t1 + n * n;     // n×n
        int w = t2 + n * n;      // m + n
        factorS(n, m, p, h, r, scratch, s, ph);
        // K = P·Hᵀ·S⁻¹: each row of K solves S·kᵀ = (row of P·Hᵀ)ᵀ
        for (int i = 0; i < n; i++) {
            System.arraycopy(scratch, ph + i * m, scratch, w, m);
            solve(m, scratch, s, scratch, w);
            System.arraycopy(scratch, w, scratch, k + i * m, m);
        }
        // Mahalanobis distance, then x += K·y
        System.arraycopy(scratch, y, scratch, w, m);
        solve(m, scratch, s, scratch, w);
        double maha = 0;
        for (int i = 0; i < m; i++) {
            maha = Math.fma(scratch[y + i], scratch[w + i], maha);
        }
        for (int i = 0; i < n; i++) {
            double v = x[i];
            for (int j = 0; j < m; j++) {
                v = Math.fma(scratch[k + i * m + j], scratch[y + j], v);
            }
            x[i] = v;
        }
        // Joseph form: P = (I - KH)·P·(I - KH)ᵀ + K·R·Kᵀ
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double v = i == j ? 1 : 0;
                for (int l = 0; l < m; l++) {
                    v = Math.fma(-scratch[k + i * m + l], h[l * n + j], v);
                }
                scratch[ikh + i * n + j] = v;
            }
        }
        matmul(n, n, n, scratch, ikh, false, p, 0, false, scratch, t1);      // (I-KH)·P
        matmul(n, n, n, scratch, t1, false, scratch, ikh, true, scratch, t2); // ·(I-KH)ᵀ
        for (int i = 0; i < n; i++) {                                // + K·R·Kᵀ
            for (int j = 0; j < n; j++) {
                double v = scratch[t2 + i * n + j];
                for (int a = 0; a < m; a++) {
                    double kr = 0;
                    for (int b = 0; b < m; b++) {
                        kr = Math.fma(scratch[k + i * m + b], r[b * m + a], kr);
                    }
                    v = Math.fma(kr, scratch[k + j * m + a], v);
                }
                p[i * n + j] = v;
            }
        }
        symmetrize(n, p);
        return maha;
    }

    // ---- small dense helpers (fixed summation order) ----

    static void matmul(int rows, int inner, int cols, double[] a, int aOff, boolean ta, double[] b, int bOff,
            boolean tb, double[] out, int oOff) {
        // out never aliases an input: callers pass distinct regions
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double v = 0;
                for (int l = 0; l < inner; l++) {
                    double av = ta ? a[aOff + l * rows + i] : a[aOff + i * inner + l];
                    double bv = tb ? b[bOff + j * inner + l] : b[bOff + l * cols + j];
                    v = Math.fma(av, bv, v);
                }
                out[oOff + i * cols + j] = v;
            }
        }
    }

    static void symmetrize(int n, double[] a) {
        symmetrize(n, a, 0);
    }

    static void symmetrize(int n, double[] a, int off) {
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double v = 0.5 * (a[off + i * n + j] + a[off + j * n + i]);
                a[off + i * n + j] = v;
                a[off + j * n + i] = v;
            }
        }
    }

    /**
     * In-place Cholesky factor {@code L} (lower triangle) of a symmetric positive-definite matrix; returns false,
     * leaving the matrix partly factored, when it is not positive definite.
     */
    static boolean cholesky(int m, double[] a, int off) {
        for (int j = 0; j < m; j++) {
            double d = a[off + j * m + j];
            for (int l = 0; l < j; l++) {
                d = Math.fma(-a[off + j * m + l], a[off + j * m + l], d);
            }
            if (!(d > 0)) {
                return false;
            }
            double ljj = Math.sqrt(d);
            a[off + j * m + j] = ljj;
            for (int i = j + 1; i < m; i++) {
                double v = a[off + i * m + j];
                for (int l = 0; l < j; l++) {
                    v = Math.fma(-a[off + i * m + l], a[off + j * m + l], v);
                }
                a[off + i * m + j] = v / ljj;
            }
        }
        return true;
    }

    /** Solves {@code L·Lᵀ·v = b} in place ({@code b} at {@code vOff}). */
    static void solve(int m, double[] l, int lOff, double[] v, int vOff) {
        for (int i = 0; i < m; i++) {
            double s = v[vOff + i];
            for (int k = 0; k < i; k++) {
                s = Math.fma(-l[lOff + i * m + k], v[vOff + k], s);
            }
            v[vOff + i] = s / l[lOff + i * m + i];
        }
        for (int i = m - 1; i >= 0; i--) {
            double s = v[vOff + i];
            for (int k = i + 1; k < m; k++) {
                s = Math.fma(-l[lOff + k * m + i], v[vOff + k], s);
            }
            v[vOff + i] = s / l[lOff + i * m + i];
        }
    }

    private static void need(double[] scratch, int size) {
        need(scratch, size, "scratch");
    }

    static void need(double[] a, int size, String name) {
        if (a == null || a.length < size) {
            throw new IllegalArgumentException(name + " must hold " + size + " doubles");
        }
    }
}
