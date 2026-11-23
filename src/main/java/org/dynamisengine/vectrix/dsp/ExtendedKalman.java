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
 * filter itself allocates nothing per step. Angle state components can be wrapped in {@code f}; angle measurements
 * need an {@link InnovationFunction} that wraps the innovation {@code z - h(x)}, given to the constructor (without
 * one, the innovation is the plain difference). {@link #innovationDistance} gives the gating distance of a
 * measurement without updating. The vectors and matrices are used from index 0; their lengths are checked before
 * the model functions run.
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
    private final InnovationFunction innovation;
    private final double[] fx;
    private final double[] jf;
    private final double[] hx;
    private final double[] jh;
    private final double[] y;
    private final double[] scratch;

    /**
     * Creates a filter whose innovation is the plain difference {@code z - h(x)}.
     *
     * The constructor allocates the work arrays; the steps do not allocate.
     *
     * @param n the state dimension, at least 1
     * @param m the measurement dimension, at least 1
     * @param f the transition {@code x → f(x)} ({@code n} values)
     * @param fJacobian {@code x → ∂f/∂x} ({@code n×n}, row-major)
     * @param h the measurement {@code x → h(x)} ({@code m} values)
     * @param hJacobian {@code x → ∂h/∂x} ({@code m×n}, row-major)
     * @throws IllegalArgumentException if {@code n} or {@code m} is less than 1, a function is {@code null}, or the
     *         work arrays would exceed an array
     */
    public ExtendedKalman(int n, int m, VectorFunction f, VectorFunction fJacobian, VectorFunction h,
            VectorFunction hJacobian) {
        this(n, m, f, fJacobian, h, hJacobian, null);
    }

    /**
     * Creates a filter with a custom innovation, for measurements that need wrapping (angles) or another
     * difference than {@code z - h(x)}.
     *
     * The constructor allocates the work arrays; the steps do not allocate.
     *
     * @param n the state dimension, at least 1
     * @param m the measurement dimension, at least 1
     * @param f the transition {@code x → f(x)} ({@code n} values)
     * @param fJacobian {@code x → ∂f/∂x} ({@code n×n}, row-major)
     * @param h the measurement {@code x → h(x)} ({@code m} values)
     * @param hJacobian {@code x → ∂h/∂x} ({@code m×n}, row-major)
     * @param innovation forms the innovation from the measurement and {@code h(x)}, or {@code null} for
     *        {@code z - h(x)}
     * @throws IllegalArgumentException if {@code n} or {@code m} is less than 1, a model function is {@code null},
     *         or the work arrays would exceed an array
     * @since 3.0.0
     */
    public ExtendedKalman(int n, int m, VectorFunction f, VectorFunction fJacobian, VectorFunction h,
            VectorFunction hJacobian, InnovationFunction innovation) {
        if (n < 1 || m < 1) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        if (f == null || fJacobian == null || h == null || hJacobian == null) {
            throw new IllegalArgumentException("the model functions must not be null");
        }
        this.n = n;
        this.m = m;
        this.f = f;
        this.fJacobian = fJacobian;
        this.h = h;
        this.hJacobian = hJacobian;
        this.innovation = innovation;
        scratch = new double[Kalman.scratchSize(n, m)];
        fx = new double[n];
        jf = new double[n * n];
        hx = new double[m];
        jh = new double[m * n];
        y = new double[m];
    }

    /**
     * Predict: {@code F = ∂f/∂x} at the current estimate, {@code x = f(x)}, {@code P = F·P·Fᵀ + Q}.
     *
     * @param x the {@code n} state values, updated
     * @param p the {@code n×n} covariance (row-major), updated
     * @param q the {@code n×n} process noise covariance (row-major)
     * @throws IllegalArgumentException if an array is null or too short (checked before anything is written)
     */
    public void predict(double[] x, double[] p, double[] q) {
        Kalman.need(x, n, "x");
        Kalman.need(p, n * n, "p");
        Kalman.need(q, n * n, "q");
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
     * Update with measurement {@code z}: innovation {@code y = z - h(x)} (or the {@link InnovationFunction}'s),
     * {@code H = ∂h/∂x} at the current estimate.
     *
     * @param x the {@code n} state values, updated
     * @param p the {@code n×n} covariance (row-major), updated
     * @param r the {@code m×m} measurement noise covariance (row-major)
     * @param z the {@code m} measurements
     * @return the squared Mahalanobis distance of the innovation, as {@link #innovationDistance} returns it
     * @throws IllegalArgumentException if an array is null or too short (checked before anything is written)
     * @throws IllegalStateException if the innovation covariance is not positive definite; {@code x} and {@code p}
     *         are then unchanged
     */
    public double update(double[] x, double[] p, double[] r, double[] z) {
        linearize(x, p, r, z);
        return Kalman.correct(n, m, x, p, jh, r, scratch);
    }

    /**
     * The squared Mahalanobis distance {@code yᵀ·S⁻¹·y} of the innovation, with {@code S = H·P·Hᵀ + R}, without
     * updating the estimate: gate a measurement with it, then call {@link #update} only for the measurements you
     * accept. The value is the one {@link #update} returns for the same arguments, bit for bit.
     *
     * @param x the {@code n} state values, not modified
     * @param p the {@code n×n} covariance (row-major), not modified
     * @param r the {@code m×m} measurement noise covariance (row-major)
     * @param z the {@code m} measurements
     * @return the squared Mahalanobis distance of the innovation
     * @throws IllegalArgumentException if an array is null or too short
     * @throws IllegalStateException if the innovation covariance is not positive definite
     * @since 3.0.0
     */
    public double innovationDistance(double[] x, double[] p, double[] r, double[] z) {
        linearize(x, p, r, z);
        return Kalman.distance(n, m, p, jh, r, scratch);
    }

    /** Checks the arguments, evaluates {@code h} and its Jacobian, and writes the innovation to the scratch. */
    private void linearize(double[] x, double[] p, double[] r, double[] z) {
        Kalman.need(x, n, "x");
        Kalman.need(p, n * n, "p");
        Kalman.need(r, m * m, "r");
        Kalman.need(z, m, "z");
        h.apply(x, hx);
        hJacobian.apply(x, jh);
        if (innovation == null) {
            for (int i = 0; i < m; i++) {
                scratch[i] = z[i] - hx[i];
            }
        } else {
            innovation.apply(z, hx, y);
            System.arraycopy(y, 0, scratch, 0, m);
        }
    }
}
