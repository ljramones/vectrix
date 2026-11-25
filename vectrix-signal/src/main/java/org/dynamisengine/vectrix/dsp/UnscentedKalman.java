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
 * The unscented Kalman filter (Julier and Uhlmann; the scaled form of Wan and van der Merwe) for nonlinear models
 * {@code x' = f(x) + w}, {@code z = h(x) + v}, without Jacobians: {@code 2n + 1} sigma points carry the mean and
 * covariance through the model functions exactly to second order.
 * <p>
 * Sigma points are {@code x ± columns of chol((n + λ)·P)}, {@code λ = α²(n + κ) - n}; typical parameters are
 * {@code α = 1e-3}, {@code β = 2} (optimal for Gaussians), {@code κ = 0}. For a linear model the filter equals the
 * Kalman filter. An instance owns its work arrays (one per stream and thread) and allocates nothing per step.
 * Vectors and row-major matrices are used from index 0; their lengths are checked before the model functions run.
 * Wrap angles in {@code f}; the innovation {@code z - z̄} is not wrapped.
 * <p>
 * The covariance update is the unscented {@code P - K·S·Kᵀ}, computed in Joseph form
 * {@code (I - K·H)·P·(I - K·H)ᵀ + K·(R + D)·Kᵀ} with the statistically linearized measurement matrix
 * {@code H = Pxzᵀ·P⁻¹} and {@code D = S - R - H·P·Hᵀ}, the covariance of the sigma points' linearization residuals.
 * The two are equal in exact arithmetic; the Joseph form keeps {@code P} symmetric and positive semi-definite despite
 * rounding whenever {@code D} is, which holds for every {@code β >= α²} (so for the usual {@code β = 2}).
 *
 * @since 2.5.0
 */
public final class UnscentedKalman {

    private final int n;
    private final int m;
    private final VectorFunction f;
    private final VectorFunction h;
    private final double lambda;
    private final double wm0;
    private final double wc0;
    private final double wi;
    private final double[] sig;   // (2n + 1)·n sigma points
    private final double[] prop;  // (2n + 1)·max(n, m) propagated points
    private final double[] chol;  // n×n
    private final double[] in;
    private final double[] out;
    private final double[] s;     // m×m
    private final double[] pxz;   // n×m
    private final double[] k;     // n×m
    private final double[] zbar;
    private final double[] col;
    private final double[] hm;    // m×n, the statistically linearized H
    private final double[] reff;  // m×m, R + D
    private final double[] ikh;   // n×n
    private final double[] t1;    // n×n
    private final double[] t2;    // n×n

    /**
     * Creates a filter. The constructor allocates the work arrays; the steps do not allocate.
     *
     * @param n the state dimension, at least 1
     * @param m the measurement dimension, at least 1
     * @param f the transition ({@code n} values)
     * @param h the measurement function ({@code m} values)
     * @param alpha the spread of the sigma points around the mean, {@code > 0}
     * @param beta the prior-distribution parameter (2 for Gaussians)
     * @param kappa the secondary scaling parameter, usually 0; {@code n + kappa} must be positive
     * @throws IllegalArgumentException if {@code n} or {@code m} is less than 1, {@code alpha} is not positive and
     *         finite, {@code n + kappa} is not positive, {@code beta} is not finite, a function is {@code null}, or
     *         the work arrays would exceed an array
     */
    public UnscentedKalman(int n, int m, VectorFunction f, VectorFunction h, double alpha, double beta,
            double kappa) {
        if (n < 1 || m < 1 || !(alpha > 0) || alpha == Double.POSITIVE_INFINITY) {
            throw new IllegalArgumentException("need positive dimensions and a positive, finite alpha");
        }
        if (!(n + kappa > 0) || kappa == Double.POSITIVE_INFINITY || !Double.isFinite(beta)) {
            throw new IllegalArgumentException("need n + kappa > 0 and finite kappa and beta: " + kappa + ", " + beta);
        }
        if (f == null || h == null) {
            throw new IllegalArgumentException("the model functions must not be null");
        }
        long pts = 2L * n + 1;
        long big = Math.max(n, m);
        if (pts * big > Integer.MAX_VALUE - 8 || (long) n * n > Integer.MAX_VALUE - 8
                || (long) n * m > Integer.MAX_VALUE - 8 || (long) m * m > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("dimensions too large: " + n + ", " + m);
        }
        this.n = n;
        this.m = m;
        this.f = f;
        this.h = h;
        lambda = alpha * alpha * (n + kappa) - n;
        wm0 = lambda / (n + lambda);
        wc0 = wm0 + (1 - alpha * alpha + beta);
        wi = 1 / (2 * (n + lambda));
        sig = new double[(int) pts * n];
        prop = new double[(int) (pts * big)];
        chol = new double[n * n];
        in = new double[n];
        out = new double[(int) big];
        s = new double[m * m];
        pxz = new double[n * m];
        k = new double[n * m];
        zbar = new double[m];
        col = new double[(int) big];
        hm = new double[m * n];
        reff = new double[m * m];
        ikh = new double[n * n];
        t1 = new double[n * n];
        t2 = new double[n * n];
    }

    private void sigmaPoints(double[] x, double[] p) {
        for (int i = 0; i < n * n; i++) {
            chol[i] = (n + lambda) * p[i];
        }
        if (!Kalman.cholesky(n, chol, 0)) {
            throw new IllegalStateException("the state covariance P is not positive definite");
        }
        System.arraycopy(x, 0, sig, 0, n);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                double c = i >= j ? chol[i * n + j] : 0.0; // column j of the lower factor
                sig[(1 + j) * n + i] = x[i] + c;
                sig[(1 + n + j) * n + i] = x[i] - c;
            }
        }
    }

    private double wm(int i) {
        return i == 0 ? wm0 : wi;
    }

    private double wc(int i) {
        return i == 0 ? wc0 : wi;
    }

    /**
     * Predict: propagate the sigma points through {@code f}, then their weighted mean and covariance plus {@code Q}.
     *
     * @param x the {@code n} state values, updated
     * @param p the {@code n×n} covariance (row-major), updated
     * @param q the {@code n×n} process noise covariance (row-major)
     * @throws IllegalArgumentException if an array is null or too short (checked before anything is written)
     * @throws IllegalStateException if {@code P} is not positive definite; {@code x} and {@code p} are then
     *         unchanged
     */
    public void predict(double[] x, double[] p, double[] q) {
        Kalman.need(x, n, "x");
        Kalman.need(p, n * n, "p");
        Kalman.need(q, n * n, "q");
        sigmaPoints(x, p);
        int pts = 2 * n + 1;
        for (int i = 0; i < pts; i++) {
            System.arraycopy(sig, i * n, in, 0, n);
            f.apply(in, out);
            System.arraycopy(out, 0, prop, i * n, n);
        }
        for (int a = 0; a < n; a++) {
            double v = 0;
            for (int i = 0; i < pts; i++) {
                v = Math.fma(wm(i), prop[i * n + a], v);
            }
            x[a] = v;
        }
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                double v = q[a * n + b];
                for (int i = 0; i < pts; i++) {
                    v = Math.fma(wc(i) * (prop[i * n + a] - x[a]), prop[i * n + b] - x[b], v);
                }
                p[a * n + b] = v;
            }
        }
        Kalman.symmetrize(n, p);
    }

    /**
     * Update with measurement {@code z}: sigma points of the current estimate through {@code h}, then the
     * cross-covariance gain {@code K = Pxz·S⁻¹}, {@code x += K·(z - z̄)}, and the covariance {@code P - K·S·Kᵀ} in
     * the Joseph form described in the class documentation.
     *
     * @param x the {@code n} state values, updated
     * @param p the {@code n×n} covariance (row-major), updated
     * @param r the {@code m×m} measurement noise covariance (row-major)
     * @param z the {@code m} measurements
     * @return the squared Mahalanobis distance of the innovation, as {@link #innovationDistance} returns it
     * @throws IllegalArgumentException if an array is null or too short (checked before anything is written)
     * @throws IllegalStateException if {@code P} or the innovation covariance {@code S} is not positive definite (the
     *         message names which); {@code x} and {@code p} are then unchanged
     */
    public double update(double[] x, double[] p, double[] r, double[] z) {
        double maha = innovate(x, p, r, z);
        for (int a = 0; a < n; a++) { // row a of K solves S·kᵀ = (row a of Pxz)ᵀ
            System.arraycopy(pxz, a * m, col, 0, m);
            Kalman.solve(m, s, 0, col, 0);
            System.arraycopy(col, 0, k, a * m, m);
        }
        // H = Pxzᵀ·P⁻¹: row b of H solves ((n + λ)·P)·v = (column b of Pxz), times n + λ
        double scale = n + lambda;
        for (int b = 0; b < m; b++) {
            for (int i = 0; i < n; i++) {
                col[i] = pxz[i * m + b];
            }
            Kalman.solve(n, chol, 0, col, 0);
            for (int i = 0; i < n; i++) {
                hm[b * n + i] = scale * col[i];
            }
        }
        // R + D, D = Σ wc·e·eᵀ over the linearization residuals e of the sigma points: e = Z0 - z̄ at the centre and
        // (Z+ + Z-)/2 - z̄ for both points of each pair
        for (int a = 0; a < m; a++) {
            for (int b = 0; b < m; b++) {
                double v = Math.fma(wc0 * (prop[a] - zbar[a]), prop[b] - zbar[b], r[a * m + b]);
                for (int j = 0; j < n; j++) {
                    int plus = (1 + j) * m;
                    int minus = (1 + n + j) * m;
                    double ea = 0.5 * (prop[plus + a] + prop[minus + a]) - zbar[a];
                    double eb = 0.5 * (prop[plus + b] + prop[minus + b]) - zbar[b];
                    v = Math.fma(2 * wi * ea, eb, v);
                }
                reff[a * m + b] = v;
            }
        }
        Kalman.symmetrize(m, reff, 0);
        for (int a = 0; a < m; a++) {
            col[a] = z[a] - zbar[a];
        }
        for (int a = 0; a < n; a++) {
            double v = x[a];
            for (int b = 0; b < m; b++) {
                v = Math.fma(k[a * m + b], col[b], v);
            }
            x[a] = v;
        }
        // Joseph form: P = (I - KH)·P·(I - KH)ᵀ + K·(R + D)·Kᵀ
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double v = i == j ? 1 : 0;
                for (int l = 0; l < m; l++) {
                    v = Math.fma(-k[i * m + l], hm[l * n + j], v);
                }
                ikh[i * n + j] = v;
            }
        }
        Kalman.matmul(n, n, n, ikh, 0, false, p, 0, false, t1, 0);
        Kalman.matmul(n, n, n, t1, 0, false, ikh, 0, true, t2, 0);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double v = t2[i * n + j];
                for (int a = 0; a < m; a++) {
                    double kr = 0;
                    for (int b = 0; b < m; b++) {
                        kr = Math.fma(k[i * m + b], reff[b * m + a], kr);
                    }
                    v = Math.fma(kr, k[j * m + a], v);
                }
                p[i * n + j] = v;
            }
        }
        Kalman.symmetrize(n, p);
        return maha;
    }

    /**
     * The squared Mahalanobis distance {@code (z - z̄)ᵀ·S⁻¹·(z - z̄)} of the innovation, without updating the
     * estimate: gate a measurement with it, then call {@link #update} only for the measurements you accept. The
     * value is the one {@link #update} returns for the same arguments, bit for bit.
     *
     * @param x the {@code n} state values, not modified
     * @param p the {@code n×n} covariance (row-major), not modified
     * @param r the {@code m×m} measurement noise covariance (row-major)
     * @param z the {@code m} measurements
     * @return the squared Mahalanobis distance of the innovation
     * @throws IllegalArgumentException if an array is null or too short
     * @throws IllegalStateException if {@code P} or the innovation covariance {@code S} is not positive definite
     * @since 3.0.0
     */
    public double innovationDistance(double[] x, double[] p, double[] r, double[] z) {
        return innovate(x, p, r, z);
    }

    /**
     * Checks the arguments, propagates the sigma points through {@code h}, and computes {@code z̄}, {@code Pxz}, the
     * Cholesky factor of {@code S} (in {@code s}) and the returned Mahalanobis distance; writes no argument.
     */
    private double innovate(double[] x, double[] p, double[] r, double[] z) {
        Kalman.need(x, n, "x");
        Kalman.need(p, n * n, "p");
        Kalman.need(r, m * m, "r");
        Kalman.need(z, m, "z");
        sigmaPoints(x, p);
        int pts = 2 * n + 1;
        for (int i = 0; i < pts; i++) {
            System.arraycopy(sig, i * n, in, 0, n);
            h.apply(in, out);
            System.arraycopy(out, 0, prop, i * m, m);
        }
        for (int a = 0; a < m; a++) {
            double v = 0;
            for (int i = 0; i < pts; i++) {
                v = Math.fma(wm(i), prop[i * m + a], v);
            }
            zbar[a] = v;
        }
        for (int a = 0; a < m; a++) {
            for (int b = 0; b < m; b++) {
                double v = r[a * m + b];
                for (int i = 0; i < pts; i++) {
                    v = Math.fma(wc(i) * (prop[i * m + a] - zbar[a]), prop[i * m + b] - zbar[b], v);
                }
                s[a * m + b] = v;
            }
        }
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < m; b++) {
                double v = 0;
                for (int i = 0; i < pts; i++) {
                    v = Math.fma(wc(i) * (sig[i * n + a] - x[a]), prop[i * m + b] - zbar[b], v);
                }
                pxz[a * m + b] = v;
            }
        }
        Kalman.symmetrize(m, s, 0);
        if (!Kalman.cholesky(m, s, 0)) {
            throw new IllegalStateException("the innovation covariance S is not positive definite");
        }
        for (int a = 0; a < m; a++) {
            col[a] = z[a] - zbar[a];
        }
        Kalman.solve(m, s, 0, col, 0);
        double maha = 0;
        for (int a = 0; a < m; a++) {
            maha = Math.fma(z[a] - zbar[a], col[a], maha);
        }
        return maha;
    }
}
