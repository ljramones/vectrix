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

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * One DFT value at any frequency, {@code X(f) = Σ x[k]·exp(-2πi f k)}, in O(n) time with one real multiply per
 * sample: Goertzel's algorithm, for tone detection and for a few bins of a long signal.
 * <p>
 * The classic recurrence {@code s = x + 2cos(w)·s1 - s2} loses accuracy near DC and Nyquist, where
 * {@code 2cos w} approaches ±2 (the error grows like {@code n²ε} at low frequencies). This uses Reinsch's forms
 * instead near the ends: the recurrence on {@code s} and its difference (below 1/8 of the sample rate) or its sum
 * (above 3/8), whose coefficients {@code -4sin²(w/2)} and {@code 4cos²(w/2)} carry no cancellation, and the classic
 * recurrence in between, where it is the most accurate. Near DC and Nyquist this is about 10⁵ times more accurate
 * than the classic form alone (20,000 samples). The float overload computes in double.
 *
 * @since 2.5.0
 */
public final class Goertzel {

    private Goertzel() {
    }

    /**
     * Writes {@code X(f)} to {@code dest[0]} (real) and {@code dest[1]} (imaginary).
     *
     * @param x the samples
     * @param off the offset of the first sample
     * @param n the number of samples, at least 1
     * @param f the frequency in cycles per sample (any value; only its fractional part matters)
     * @param dest receives the real and imaginary parts
     * @throws IllegalArgumentException if {@code n < 1} or {@code x} does not hold {@code n} samples from {@code off}
     */
    public static void dft(double[] x, int off, int n, double f, double[] dest) {
        check(x == null ? -1 : x.length, off, n);
        run(x, null, off, n, f, dest);
    }

    /**
     * Writes {@code X(f)} of float samples to {@code dest[0]} (real) and {@code dest[1]} (imaginary), computed in
     * double.
     *
     * @param x the samples
     * @param off the offset of the first sample
     * @param n the number of samples, at least 1
     * @param f the frequency in cycles per sample (any value; only its fractional part matters)
     * @param dest receives the real and imaginary parts
     * @throws IllegalArgumentException if {@code n < 1} or {@code x} does not hold {@code n} samples from {@code off}
     */
    public static void dft(float[] x, int off, int n, double f, double[] dest) {
        check(x == null ? -1 : x.length, off, n);
        run(null, x, off, n, f, dest);
    }

    /**
     * Returns the power {@code |X(f)|²} of one DFT value.
     *
     * @param x the samples
     * @param off the offset of the first sample
     * @param n the number of samples, at least 1
     * @param f the frequency in cycles per sample (any value; only its fractional part matters)
     * @return {@code |X(f)|²}
     * @throws IllegalArgumentException if {@code n < 1} or {@code x} does not hold {@code n} samples from {@code off}
     */
    public static double power(double[] x, int off, int n, double f) {
        check(x == null ? -1 : x.length, off, n);
        return run(x, null, off, n, f, null);
    }

    /**
     * Returns the power {@code |X(f)|²} of one DFT value of float samples, computed in double.
     *
     * @param x the samples
     * @param off the offset of the first sample
     * @param n the number of samples, at least 1
     * @param f the frequency in cycles per sample (any value; only its fractional part matters)
     * @return {@code |X(f)|²}
     * @throws IllegalArgumentException if {@code n < 1} or {@code x} does not hold {@code n} samples from {@code off}
     */
    public static double power(float[] x, int off, int n, double f) {
        check(x == null ? -1 : x.length, off, n);
        return run(null, x, off, n, f, null);
    }

    /** Writes {@code X(f)} to {@code dest} unless it is null; returns {@code |X(f)|²}. */
    private static double run(double[] xd, float[] xf, int off, int n, double f, double[] dest) {
        double t = f - java.lang.Math.rint(f); // turns, in [-0.5, 0.5]
        double w = 2 * java.lang.Math.PI * t;
        double sh = AccurateMath.sin(0.5 * w);
        double ch = AccurateMath.cos(0.5 * w);
        double sw = AccurateMath.sin(w);
        double s = 0.0;
        double v = 0.0;
        double at = java.lang.Math.abs(t);
        double yr;
        double yi;
        if (at <= 0.125) {
            // d_k = d_(k-1) + λ·s_(k-1) + x_k, s_k = s_(k-1) + d_k, λ = 2cos w - 2 = -4sin²(w/2)
            double lambda = -4 * sh * sh;
            for (int k = 0; k < n; k++) {
                double xk = xd != null ? xd[off + k] : xf[off + k];
                v = v + lambda * s + xk;
                s = s + v;
            }
            // y = s - exp(-iw)·(s - d) = s·(2sin²(w/2) + i·sin w) + exp(-iw)·d
            yr = s * 2 * sh * sh + AccurateMath.cos(w) * v;
            yi = s * sw - sw * v;
        } else if (at >= 0.375) {
            // e_k = -e_(k-1) + μ·s_(k-1) + x_k, s_k = e_k - s_(k-1), μ = 2cos w + 2 = 4cos²(w/2)
            double mu = 4 * ch * ch;
            for (int k = 0; k < n; k++) {
                double xk = xd != null ? xd[off + k] : xf[off + k];
                v = -v + mu * s + xk;
                s = v - s;
            }
            // y = s - exp(-iw)·(e - s) = s·(2cos²(w/2) - i·sin w) - exp(-iw)·e
            yr = s * 2 * ch * ch - AccurateMath.cos(w) * v;
            yi = -s * sw + sw * v;
        } else {
            // mid band: the classic recurrence, whose coefficient 2cos w is small here
            double c2 = 2 * AccurateMath.cos(w);
            for (int k = 0; k < n; k++) {
                double xk = xd != null ? xd[off + k] : xf[off + k];
                double s0 = xk + c2 * s - v;
                v = s;
                s = s0;
            }
            // y = s_(n-1) - exp(-iw)·s_(n-2)
            yr = s - AccurateMath.cos(w) * v;
            yi = sw * v;
        }
        if (dest == null) {
            return yr * yr + yi * yi; // the phase factor below has unit magnitude
        }
        // X = exp(-iw(n-1))·y
        double ph = (n - 1) * t;
        double a = 2 * java.lang.Math.PI * (ph - java.lang.Math.rint(ph));
        double c = AccurateMath.cos(a);
        double sn = -AccurateMath.sin(a);
        dest[0] = yr * c - yi * sn;
        dest[1] = yr * sn + yi * c;
        return yr * yr + yi * yi;
    }

    private static void check(int length, int off, int n) {
        if (length < 0 || n < 1 || off < 0 || off > length - n) {
            throw new IllegalArgumentException("x must hold " + n + " samples from offset " + off);
        }
    }
}
