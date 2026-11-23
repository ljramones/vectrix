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
 * Vectors and row-major matrices are used from index 0, with no length checks beyond Java's bounds checks. Wrap
 * angles in {@code f}; the innovation {@code z - z̄} is not wrapped.
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
     * @throws IllegalArgumentException if {@code n} or {@code m} is less than 1 or {@code alpha} is not positive
     */
    public UnscentedKalman(int n, int m, VectorFunction f, VectorFunction h, double alpha, double beta,
            double kappa) {
        if (n < 1 || m < 1 || !(alpha > 0)) {
            throw new IllegalArgumentException("need positive dimensions and alpha");
        }
        this.n = n;
        this.m = m;
        this.f = f;
        this.h = h;
        lambda = alpha * alpha * (n + kappa) - n;
        wm0 = lambda / (n + lambda);
        wc0 = wm0 + (1 - alpha * alpha + beta);
        wi = 1 / (2 * (n + lambda));
        int pts = 2 * n + 1;
        sig = new double[pts * n];
        prop = new double[pts * Math.max(n, m)];
        chol = new double[n * n];
        in = new double[n];
        out = new double[Math.max(n, m)];
        s = new double[m * m];
        pxz = new double[n * m];
        k = new double[n * m];
        zbar = new double[m];
        col = new double[Math.max(n, m)];
    }

    private void sigmaPoints(double[] x, double[] p) {
        for (int i = 0; i < n * n; i++) {
            chol[i] = (n + lambda) * p[i];
        }
        Kalman.cholesky(n, chol, 0);
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
     * @throws IllegalStateException if {@code P} is not positive definite (the message names the innovation
     *         covariance); {@code x} and {@code p} are then unchanged
     */
    public void predict(double[] x, double[] p, double[] q) {
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
     * cross-covariance gain {@code K = Pxz·S⁻¹}, {@code x += K·(z - z̄)}, {@code P -= K·S·Kᵀ}.
     *
     * @param x the {@code n} state values, updated
     * @param p the {@code n×n} covariance (row-major), updated
     * @param r the {@code m×m} measurement noise covariance (row-major)
     * @param z the {@code m} measurements
     * @return the squared Mahalanobis distance of the innovation
     * @throws IllegalStateException if {@code P} or the innovation covariance {@code S} is not positive definite;
     *         {@code x} and {@code p} are then unchanged
     */
    public double update(double[] x, double[] p, double[] r, double[] z) {
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
        Kalman.cholesky(m, s, 0); // in place; the covariance update uses K·Pxzᵀ, which equals K·S·Kᵀ

        for (int a = 0; a < n; a++) { // row a of K solves S·kᵀ = (row a of Pxz)ᵀ
            System.arraycopy(pxz, a * m, col, 0, m);
            Kalman.solve(m, s, 0, col, 0);
            System.arraycopy(col, 0, k, a * m, m);
        }
        double maha = 0;
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
        Kalman.solve(m, s, 0, col, 0);
        for (int a = 0; a < m; a++) {
            maha = Math.fma(z[a] - zbar[a], col[a], maha);
        }
        for (int a = 0; a < n; a++) { // P -= K·S·Kᵀ = K·Pxzᵀ
            for (int b = 0; b < n; b++) {
                double v = p[a * n + b];
                for (int c = 0; c < m; c++) {
                    v = Math.fma(-k[a * m + c], pxz[b * m + c], v);
                }
                p[a * n + b] = v;
            }
        }
        Kalman.symmetrize(n, p);
        return maha;
    }
}
