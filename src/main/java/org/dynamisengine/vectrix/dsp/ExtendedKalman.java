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
 * The extended Kalman filter for nonlinear models {@code x' = f(x) + w}, {@code z = h(x) + v}: the {@link Kalman}
 * steps with the models linearized by their Jacobians at the current estimate.
 * <p>
 * The update shares {@link Kalman}'s numerics (Cholesky-solved gain, Joseph-form covariance, exact symmetrization).
 * An instance owns the work arrays the model functions write into, so use one per estimated stream and thread. The
 * filter itself allocates nothing per step. Angles in the state or measurement need wrapping in the model functions.
 *
 * @since 2.5.0
 */
public final class ExtendedKalman {

    private final int n;
    private final int m;
    private final VectorFunction f;
    private final VectorFunction fJacobian;
    private final VectorFunction h;
    private final VectorFunction hJacobian;
    private final double[] fx;
    private final double[] jf;
    private final double[] hx;
    private final double[] jh;
    private final double[] scratch;

    /**
     * Creates a filter.
     *
     * @param n the state dimension
     * @param m the measurement dimension
     * @param f the transition {@code x → f(x)} ({@code n} values)
     * @param fJacobian {@code x → ∂f/∂x} ({@code n×n}, row-major)
     * @param h the measurement {@code x → h(x)} ({@code m} values)
     * @param hJacobian {@code x → ∂h/∂x} ({@code m×n}, row-major)
     */
    public ExtendedKalman(int n, int m, VectorFunction f, VectorFunction fJacobian, VectorFunction h,
            VectorFunction hJacobian) {
        if (n < 1 || m < 1) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        this.n = n;
        this.m = m;
        this.f = f;
        this.fJacobian = fJacobian;
        this.h = h;
        this.hJacobian = hJacobian;
        fx = new double[n];
        jf = new double[n * n];
        hx = new double[m];
        jh = new double[m * n];
        scratch = new double[Kalman.scratchSize(n, m)];
    }

    /** Predict: {@code F = ∂f/∂x} at the current estimate, {@code x = f(x)}, {@code P = F·P·Fᵀ + Q}. */
    public void predict(double[] x, double[] p, double[] q) {
        fJacobian.apply(x, jf);
        f.apply(x, fx);
        System.arraycopy(fx, 0, x, 0, n);
        Kalman.matmul(n, n, n, jf, 0, false, p, 0, false, scratch, 0);
        Kalman.matmul(n, n, n, scratch, 0, false, jf, 0, true, p, 0);
        for (int i = 0; i < n * n; i++) {
            p[i] += q[i];
        }
        Kalman.symmetrize(n, p);
    }

    /**
     * Update with measurement {@code z}: innovation {@code y = z - h(x)}, {@code H = ∂h/∂x} at the current estimate.
     *
     * @return the squared Mahalanobis distance of the innovation
     */
    public double update(double[] x, double[] p, double[] r, double[] z) {
        h.apply(x, hx);
        hJacobian.apply(x, jh);
        for (int i = 0; i < m; i++) {
            scratch[i] = z[i] - hx[i];
        }
        return Kalman.correct(n, m, x, p, jh, r, scratch);
    }
}
