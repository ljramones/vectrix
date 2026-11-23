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
 * Adaptive FIR filters, one sample at a time over caller-held state: LMS, normalized LMS and RLS.
 * <p>
 * The filter output is {@code y = Σ w[k]·u[k]} over the {@code n} most recent inputs {@code u} (newest first); after
 * each sample the weights move to reduce the error {@code e = d - y} against the desired sample {@code d}. Each step
 * shifts the history, filters, adapts and returns {@code e}. LMS converges slowly and cheaply ({@code O(n)} per
 * sample); NLMS normalizes the step by the input power; RLS converges fast at {@code O(n²)} per sample. All
 * arithmetic runs in a fixed order; nothing allocates.
 *
 * @since 2.5.0
 */
public final class AdaptiveFilter {

    private AdaptiveFilter() {
    }

    /**
     * One LMS step: {@code w += μ·e·u}.
     *
     * @param w the {@code n} weights, updated
     * @param u the {@code n} newest inputs (newest first), updated with {@code x}
     * @param x the new input sample
     * @param d the desired output
     * @param mu the step size
     * @return the a-priori error {@code d - y}
     */
    public static double lms(int n, double[] w, double[] u, double x, double d, double mu) {
        push(n, u, x);
        double e = d - dot(n, w, u);
        double s = mu * e;
        for (int k = 0; k < n; k++) {
            w[k] = Math.fma(s, u[k], w[k]);
        }
        return e;
    }

    /**
     * One normalized-LMS step: {@code w += μ·e·u / (eps + |u|²)}, which is stable for {@code 0 < μ < 2} whatever
     * the input level.
     */
    public static double nlms(int n, double[] w, double[] u, double x, double d, double mu, double eps) {
        push(n, u, x);
        double e = d - dot(n, w, u);
        double s = mu * e / (eps + dot(n, u, u));
        for (int k = 0; k < n; k++) {
            w[k] = Math.fma(s, u[k], w[k]);
        }
        return e;
    }

    /**
     * One recursive-least-squares step with forgetting factor {@code λ} (exponentially weighted).
     * Initialize {@code p} to {@code δ⁻¹·I} with a small {@code δ}.
     *
     * @param p the {@code n×n} inverse correlation matrix (row-major), updated
     * @param lambda the forgetting factor, in (0, 1]
     * @param scratch {@code 2n} doubles
     */
    public static double rls(int n, double[] w, double[] u, double[] p, double x, double d, double lambda,
            double[] scratch) {
        push(n, u, x);
        double e = d - dot(n, w, u);
        int pu = 0;
        int kv = n;
        for (int i = 0; i < n; i++) { // P·u
            double s = 0;
            for (int j = 0; j < n; j++) {
                s = Math.fma(p[i * n + j], u[j], s);
            }
            scratch[pu + i] = s;
        }
        double den = lambda + dot(n, u, scratch, pu);
        for (int i = 0; i < n; i++) { // gain k = P·u / (λ + uᵀ·P·u)
            scratch[kv + i] = scratch[pu + i] / den;
        }
        for (int i = 0; i < n; i++) {
            w[i] = Math.fma(scratch[kv + i], e, w[i]);
        }
        // P = (P - k·(P·u)ᵀ)/λ, symmetrized
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                p[i * n + j] = Math.fma(-scratch[kv + i], scratch[pu + j], p[i * n + j]) / lambda;
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double v = 0.5 * (p[i * n + j] + p[j * n + i]);
                p[i * n + j] = v;
                p[j * n + i] = v;
            }
        }
        return e;
    }

    private static void push(int n, double[] u, double x) {
        System.arraycopy(u, 0, u, 1, n - 1);
        u[0] = x;
    }

    private static double dot(int n, double[] a, double[] b) {
        return dot(n, a, b, 0);
    }

    private static double dot(int n, double[] a, double[] b, int bOff) {
        double s = 0;
        for (int k = 0; k < n; k++) {
            s = Math.fma(a[k], b[bOff + k], s);
        }
        return s;
    }
}
