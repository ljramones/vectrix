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
package org.dynamisengine.vectrix.nn;

import org.dynamisengine.vectrix.blas.Blasf;

/**
 * Layer normalization and RMS normalization over one row of floats, forward and backward.
 * <p>
 * Means and variances use {@link Blasf}'s documented summation order, and the variance is computed from centred
 * values (two passes), so it is never negative and does not cancel. The forward kernels return the statistics the
 * backward kernels need ({@code mean} and {@code 1/σ}, or {@code 1/rms}), so a training step stores two floats per
 * row. Parameter gradients are <i>accumulated</i> ({@code +=}) so rows can be processed in any batch; for
 * reproducible parameter gradients across a batch, accumulate rows in a fixed order. Nothing allocates.
 *
 * @since 2.5.0
 */
public final class Norms {

    private Norms() {
    }

    /**
     * {@code y = (x - mean)/σ·γ + β}, {@code σ = sqrt(var + eps)}.
     *
     * @param gamma the scale ({@code n} values), or {@code null} for 1
     * @param beta the shift ({@code n} values), or {@code null} for 0
     * @param stats receives {@code mean} at {@code stats[statsOff]} and {@code 1/σ} at {@code stats[statsOff + 1]},
     *        or {@code null}
     */
    public static void layerNorm(float[] x, int xOff, float[] gamma, int gOff, float[] beta, int bOff, float eps,
            float[] y, int yOff, int n, float[] stats, int statsOff) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        checkOpt(gamma, gOff, n, "gamma");
        checkOpt(beta, bOff, n, "beta");
        if (n == 0) {
            return;
        }
        float mean = Blasf.sum(x, xOff, n) / n;
        for (int i = 0; i < n; i++) {
            y[yOff + i] = x[xOff + i] - mean;
        }
        float var = Blasf.dot(y, yOff, y, yOff, n) / n;
        float rstd = 1f / (float) Math.sqrt(var + eps);
        for (int i = 0; i < n; i++) {
            float v = y[yOff + i] * rstd;
            float g = gamma == null ? 1f : gamma[gOff + i];
            float b = beta == null ? 0f : beta[bOff + i];
            y[yOff + i] = Math.fma(v, g, b);
        }
        if (stats != null) {
            stats[statsOff] = mean;
            stats[statsOff + 1] = rstd;
        }
    }

    /**
     * Layer-norm backward:
     * {@code dx = (1/σ)·(dx̂ - mean(dx̂) - x̂·mean(dx̂·x̂))}, {@code dx̂ = dy·γ}, {@code x̂ = (x - mean)/σ};
     * {@code dγ += dy·x̂}, {@code dβ += dy}.
     *
     * @param stats the {@code mean, 1/σ} the forward kernel returned
     * @param dGamma accumulates the scale gradient, or {@code null}
     * @param dBeta accumulates the shift gradient, or {@code null}
     * @param scratch {@code 2n} floats of working memory
     */
    public static void layerNormBackward(float[] x, int xOff, float[] gamma, int gOff, float[] stats, int statsOff,
            float[] dy, int dyOff, float[] dx, int dxOff, float[] dGamma, int dgOff, float[] dBeta, int dbOff, int n,
            float[] scratch, int scratchOff) {
        check(x, xOff, n, "x");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        checkOpt(gamma, gOff, n, "gamma");
        checkOpt(dGamma, dgOff, n, "dGamma");
        checkOpt(dBeta, dbOff, n, "dBeta");
        check(scratch, scratchOff, 2 * n, "scratch");
        if (n == 0) {
            return;
        }
        float mean = stats[statsOff];
        float rstd = stats[statsOff + 1];
        int xh = scratchOff;
        int dxh = scratchOff + n;
        for (int i = 0; i < n; i++) {
            float h = (x[xOff + i] - mean) * rstd;
            float g = gamma == null ? 1f : gamma[gOff + i];
            float d = dy[dyOff + i];
            scratch[xh + i] = h;
            scratch[dxh + i] = d * g;
            if (dGamma != null) {
                dGamma[dgOff + i] = Math.fma(d, h, dGamma[dgOff + i]);
            }
            if (dBeta != null) {
                dBeta[dbOff + i] += d;
            }
        }
        float m1 = Blasf.sum(scratch, dxh, n) / n;
        float m2 = Blasf.dot(scratch, dxh, scratch, xh, n) / n;
        for (int i = 0; i < n; i++) {
            dx[dxOff + i] = rstd * Math.fma(-scratch[xh + i], m2, scratch[dxh + i] - m1);
        }
    }

    /**
     * {@code y = x / rms·γ}, {@code rms = sqrt(mean(x²) + eps)}.
     *
     * @param gamma the scale, or {@code null} for 1
     * @param stats receives {@code 1/rms} at {@code stats[statsOff]}, or {@code null}
     */
    public static void rmsNorm(float[] x, int xOff, float[] gamma, int gOff, float eps, float[] y, int yOff, int n,
            float[] stats, int statsOff) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        checkOpt(gamma, gOff, n, "gamma");
        if (n == 0) {
            return;
        }
        float ms = Blasf.dot(x, xOff, x, xOff, n) / n;
        float rinv = 1f / (float) Math.sqrt(ms + eps);
        for (int i = 0; i < n; i++) {
            float g = gamma == null ? 1f : gamma[gOff + i];
            y[yOff + i] = x[xOff + i] * rinv * g;
        }
        if (stats != null) {
            stats[statsOff] = rinv;
        }
    }

    /**
     * RMS-norm backward: {@code dx = (1/rms)·(dx̂ - x̂·mean(dx̂·x̂))}, {@code dx̂ = dy·γ}, {@code x̂ = x/rms};
     * {@code dγ += dy·x̂}.
     *
     * @param stats the {@code 1/rms} the forward kernel returned
     * @param scratch {@code 2n} floats of working memory
     */
    public static void rmsNormBackward(float[] x, int xOff, float[] gamma, int gOff, float[] stats, int statsOff,
            float[] dy, int dyOff, float[] dx, int dxOff, float[] dGamma, int dgOff, int n, float[] scratch,
            int scratchOff) {
        check(x, xOff, n, "x");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        checkOpt(gamma, gOff, n, "gamma");
        checkOpt(dGamma, dgOff, n, "dGamma");
        check(scratch, scratchOff, 2 * n, "scratch");
        if (n == 0) {
            return;
        }
        float rinv = stats[statsOff];
        int xh = scratchOff;
        int dxh = scratchOff + n;
        for (int i = 0; i < n; i++) {
            float h = x[xOff + i] * rinv;
            float g = gamma == null ? 1f : gamma[gOff + i];
            float d = dy[dyOff + i];
            scratch[xh + i] = h;
            scratch[dxh + i] = d * g;
            if (dGamma != null) {
                dGamma[dgOff + i] = Math.fma(d, h, dGamma[dgOff + i]);
            }
        }
        float m2 = Blasf.dot(scratch, dxh, scratch, xh, n) / n;
        for (int i = 0; i < n; i++) {
            dx[dxOff + i] = rinv * Math.fma(-scratch[xh + i], m2, scratch[dxh + i]);
        }
    }

    private static void check(float[] a, int off, int n, String name) {
        if (a == null || n < 0 || off < 0 || off > a.length - n) {
            throw new IllegalArgumentException(name + " must hold " + n + " values from offset " + off);
        }
    }

    private static void checkOpt(float[] a, int off, int n, String name) {
        if (a != null) {
            check(a, off, n, name);
        }
    }
}
