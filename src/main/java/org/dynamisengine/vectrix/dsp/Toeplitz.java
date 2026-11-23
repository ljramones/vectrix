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
 * and report failure otherwise. Sums run in a fixed order; nothing allocates.
 *
 * @since 2.5.0
 */
public final class Toeplitz {

    private Toeplitz() {
    }

    /**
     * Linear prediction by Levinson–Durbin: the coefficients {@code a[1..p]} minimizing the error of
     * {@code x[t] ≈ -Σ a[k]·x[t - k]}, given the autocorrelation {@code r[0..p]}.
     *
     * @param a receives {@code p + 1} coefficients with {@code a[0] = 1}
     * @param reflection receives the {@code p} reflection (PARCOR) coefficients, or {@code null}
     * @return the final prediction error power
     * @throws IllegalArgumentException if {@code r[0] <= 0} or the recursion becomes unstable ({@code |k| >= 1})
     */
    public static double levinsonDurbin(double[] r, int p, double[] a, double[] reflection, double[] scratch) {
        if (p < 0 || r.length < p + 1 || a.length < p + 1 || scratch.length < p + 1) {
            throw new IllegalArgumentException("need r and a of p + 1 values and p + 1 doubles of scratch");
        }
        if (!(r[0] > 0)) {
            throw new IllegalArgumentException("r[0] must be positive");
        }
        java.util.Arrays.fill(a, 0, p + 1, 0.0);
        a[0] = 1;
        double err = r[0];
        for (int i = 1; i <= p; i++) {
            double acc = r[i];
            for (int j = 1; j < i; j++) {
                acc = Math.fma(a[j], r[i - j], acc);
            }
            double k = -acc / err;
            if (!(Math.abs(k) < 1)) {
                throw new IllegalArgumentException("the autocorrelation is not positive definite at order " + i);
            }
            if (reflection != null) {
                reflection[i - 1] = k;
            }
            System.arraycopy(a, 0, scratch, 0, i);
            for (int j = 1; j < i; j++) {
                a[j] = Math.fma(k, scratch[i - j], scratch[j]);
            }
            a[i] = k;
            err *= (1 - k) * (1 + k);
        }
        return err;
    }

    /**
     * Solves {@code T·x = b} for the symmetric Toeplitz matrix with first row {@code r[0..n)} (Levinson's algorithm).
     *
     * @param x receives the {@code n} unknowns
     * @param scratch {@code 2n} doubles
     * @throws IllegalArgumentException if a leading submatrix is singular
     */
    public static void solve(double[] r, double[] b, int n, double[] x, double[] scratch) {
        if (n < 1 || r.length < n || b.length < n || x.length < n || scratch.length < 2 * n) {
            throw new IllegalArgumentException("need n values in r, b and x, and 2n doubles of scratch");
        }
        int f = 0;      // forward vector f_i: T_i·f_i = e_1
        int t = n;      // temporary
        if (r[0] == 0) {
            throw new IllegalArgumentException("singular leading submatrix");
        }
        scratch[f] = 1 / r[0];
        x[0] = b[0] / r[0];
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
            // the backward vector of a symmetric Toeplitz matrix is the reversed forward vector
            for (int j = 0; j <= i; j++) {
                double fj = j < i ? scratch[f + j] : 0;
                double bj = j > 0 ? scratch[f + i - j] : 0;
                scratch[t + j] = (fj - eps * bj) / den;
            }
            System.arraycopy(scratch, t, scratch, f, i + 1);
            double ex = 0;
            for (int j = 0; j < i; j++) {
                ex = Math.fma(r[i - j], x[j], ex);
            }
            double c = b[i] - ex;
            x[i] = 0;
            for (int j = 0; j <= i; j++) {
                x[j] = Math.fma(c, scratch[f + i - j], x[j]); // add c times the backward vector
            }
        }
    }
}
