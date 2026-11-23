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
 * Symmetric Toeplitz systems in {@code O(n²)}: Levinson's solver for any right-hand side, and the Levinson–Durbin
 * recursion for the Yule–Walker equations of linear prediction.
 * <p>
 * The matrix is given by its first row {@code r[0..n)} ({@code T[i][j] = r[|i - j|]}), typically an autocorrelation.
 * Both algorithms require the leading submatrices to be nonsingular (true for any positive-definite autocorrelation)
 * and report failure otherwise. Both work in the caller's scratch and write their outputs only on success: a bad
 * argument or a failure throws {@link IllegalArgumentException} with the outputs unchanged. Sums run in a fixed
 * order; nothing allocates. Arrays are used from index 0.
 *
 * @since 2.5.0
 */
public final class Toeplitz {

    private Toeplitz() {
    }

    /**
     * Returns the scratch size of {@link #levinsonDurbin} for prediction order {@code p}: {@code 2p + 1}.
     *
     * @param p the prediction order, {@code >= 0}
     * @return the number of scratch doubles
     * @throws IllegalArgumentException if {@code p} is negative or too large for an array
     * @since 3.0.0
     */
    public static int levinsonDurbinScratchSize(int p) {
        if (p < 0 || p > (Integer.MAX_VALUE - 9) / 2) {
            throw new IllegalArgumentException("prediction order out of range: " + p);
        }
        return 2 * p + 1;
    }

    /**
     * Returns the scratch size of {@link #solve} for a system of order {@code n}: {@code 2n}.
     *
     * @param n the order of the system, at least 1
     * @return the number of scratch doubles
     * @throws IllegalArgumentException if {@code n < 1} or {@code n} is too large for an array
     * @since 3.0.0
     */
    public static int solveScratchSize(int n) {
        if (n < 1 || n > (Integer.MAX_VALUE - 8) / 2) {
            throw new IllegalArgumentException("order out of range: " + n);
        }
        return 2 * n;
    }

    /**
     * Linear prediction by Levinson–Durbin: the coefficients {@code a[1..p]} minimizing the error of
     * {@code x[t] ≈ -Σ a[k]·x[t - k]}, given the autocorrelation {@code r[0..p]}.
     *
     * @param r the autocorrelation at lags {@code 0..p}
     * @param p the prediction order, {@code >= 0}
     * @param a receives {@code p + 1} coefficients with {@code a[0] = 1}; may be {@code r}
     * @param reflection receives the {@code p} reflection (PARCOR) coefficients, or {@code null}
     * @param scratch at least {@link #levinsonDurbinScratchSize(int) levinsonDurbinScratchSize(p)} doubles of working
     *        memory, {@code 2p + 1}
     * @return the final prediction error power
     * @throws IllegalArgumentException if {@code p < 0}; {@code r}, {@code a} or {@code scratch} is null or too
     *         short; {@code reflection} is shorter than {@code p}; {@code r[0] <= 0}; or the recursion becomes
     *         unstable ({@code |k| >= 1}, the autocorrelation is not positive definite). {@code a} and
     *         {@code reflection} are then unchanged
     */
    public static double levinsonDurbin(double[] r, int p, double[] a, double[] reflection, double[] scratch) {
        int need = levinsonDurbinScratchSize(p);
        if (r == null || a == null || scratch == null || r.length < p + 1 || a.length < p + 1
                || scratch.length < need || reflection != null && reflection.length < p) {
            throw new IllegalArgumentException("need r and a of p + 1 values, reflection of p values (or null) and "
                    + "2p + 1 doubles of scratch: p = " + p);
        }
        if (!(r[0] > 0)) {
            throw new IllegalArgumentException("r[0] must be positive");
        }
        // coefficients in scratch[0 .. p], reflection coefficients in scratch[p + 1 .. 2p]
        int ks = p + 1;
        java.util.Arrays.fill(scratch, 0, p + 1, 0.0);
        scratch[0] = 1;
        double err = r[0];
        for (int i = 1; i <= p; i++) {
            double acc = r[i];
            for (int j = 1; j < i; j++) {
                acc = Math.fma(scratch[j], r[i - j], acc);
            }
            double k = -acc / err;
            if (!(Math.abs(k) < 1)) {
                throw new IllegalArgumentException("the autocorrelation is not positive definite at order " + i);
            }
            scratch[ks + i - 1] = k;
            // a_j += k·a_(i-j), in place by symmetric pairs
            for (int j = 1, l = i - 1; j <= l; j++, l--) {
                double aj = scratch[j];
                double al = scratch[l];
                scratch[j] = Math.fma(k, al, aj);
                if (l != j) {
                    scratch[l] = Math.fma(k, aj, al);
                }
            }
            scratch[i] = k;
            err *= (1 - k) * (1 + k);
        }
        System.arraycopy(scratch, 0, a, 0, p + 1);
        if (reflection != null) {
            System.arraycopy(scratch, ks, reflection, 0, p);
        }
        return err;
    }

    /**
     * Solves {@code T·x = b} for the symmetric Toeplitz matrix with first row {@code r[0..n)} (Levinson's algorithm).
     *
     * @param r the first row of the matrix, {@code n} values
     * @param b the right-hand side, {@code n} values
     * @param n the order of the system, at least 1
     * @param x receives the {@code n} unknowns; may be {@code b} or {@code r}
     * @param scratch at least {@link #solveScratchSize(int) solveScratchSize(n)} doubles of working memory,
     *        {@code 2n}
     * @throws IllegalArgumentException if {@code n < 1}, an array is null or too short, or a leading submatrix is
     *         singular; {@code x} is then unchanged
     */
    public static void solve(double[] r, double[] b, int n, double[] x, double[] scratch) {
        int need = solveScratchSize(n);
        if (r == null || b == null || x == null || scratch == null || r.length < n || b.length < n || x.length < n
                || scratch.length < need) {
            throw new IllegalArgumentException("need n values in r, b and x, and 2n doubles of scratch: n = " + n);
        }
        if (r[0] == 0) {
            throw new IllegalArgumentException("singular leading submatrix");
        }
        int f = 0;      // forward vector f_i: T_i·f_i = e_1
        int xs = n;     // the solution, written to x at the end
        scratch[f] = 1 / r[0];
        scratch[xs] = b[0] / r[0];
        for (int i = 1; i < n; i++) {
            // error of extending f with a zero: eps = Σ r[i - j]·f[j]
            double eps = 0;
            for (int j = 0; j < i; j++) {
                eps = Math.fma(r[i - j], scratch[f + j], eps);
            }
            double den = 1 - eps * eps;
            if (den == 0) {
                throw new IllegalArgumentException("singular leading submatrix of order " + (i + 1));
            }
            // f_(i+1) = (f_i - eps·reversed f_i) / den, with f_i extended by a zero; in place by symmetric pairs
            // (the backward vector of a symmetric Toeplitz matrix is the reversed forward vector)
            scratch[f + i] = 0;
            for (int j = 0, l = i; j <= l; j++, l--) {
                double fj = scratch[f + j];
                double fl = scratch[f + l];
                scratch[f + j] = (fj - eps * fl) / den;
                if (l != j) {
                    scratch[f + l] = (fl - eps * fj) / den;
                }
            }
            double ex = 0;
            for (int j = 0; j < i; j++) {
                ex = Math.fma(r[i - j], scratch[xs + j], ex);
            }
            double c = b[i] - ex;
            scratch[xs + i] = 0;
            for (int j = 0; j <= i; j++) {
                scratch[xs + j] = Math.fma(c, scratch[f + i - j], scratch[xs + j]); // add c times the backward vector
            }
        }
        System.arraycopy(scratch, xs, x, 0, n);
    }
}
