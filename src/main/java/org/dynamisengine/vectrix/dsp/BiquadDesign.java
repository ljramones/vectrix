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
 * Second-order IIR section designs: the RBJ "Audio EQ Cookbook" filters and Butterworth cascades.
 * <p>
 * Each section is five doubles {@code b0, b1, b2, a1, a2} (normalized so {@code a0 = 1}), for
 * {@code H(z) = (b0 + b1 z⁻¹ + b2 z⁻²) / (1 + a1 z⁻¹ + a2 z⁻²)}; cascades are consecutive sections, processed by
 * {@link Biquad}. Frequencies are in cycles per sample, in (0, 0.5); designs use the bilinear transform with
 * pre-warping, so the stated frequency is exact. Terms of the form {@code 1 ± cos w} are computed as
 * {@code 2·sin²(w/2)} and {@code 2·cos²(w/2)}, which keeps low-frequency designs accurate where the cookbook
 * formulas cancel.
 *
 * @since 2.5.0
 */
public final class BiquadDesign {

    /** The number of coefficients per section. */
    public static final int STRIDE = 5;

    private BiquadDesign() {
    }

    /** Low-pass; {@code |H(f0)| = q}, so {@code q = 1/√2} is Butterworth. */
    public static void lowpass(double[] dest, int off, double f0, double q) {
        check(dest, off, 1, f0, q);
        double h = halfAngleSin2(f0); // (1 - cos w)/2
        double c = cosW(f0);
        double alpha = sinW(f0) / (2 * q);
        set(dest, off, h, 2 * h, h, 1 + alpha, -2 * c, 1 - alpha);
    }

    /** High-pass; {@code |H(f0)| = q}. */
    public static void highpass(double[] dest, int off, double f0, double q) {
        check(dest, off, 1, f0, q);
        double h = halfAngleCos2(f0); // (1 + cos w)/2
        double c = cosW(f0);
        double alpha = sinW(f0) / (2 * q);
        set(dest, off, h, -2 * h, h, 1 + alpha, -2 * c, 1 - alpha);
    }

    /** Band-pass with unit gain at {@code f0}; {@code q} sets the bandwidth ({@code f0 / q}). */
    public static void bandpass(double[] dest, int off, double f0, double q) {
        check(dest, off, 1, f0, q);
        double c = cosW(f0);
        double alpha = sinW(f0) / (2 * q);
        set(dest, off, alpha, 0, -alpha, 1 + alpha, -2 * c, 1 - alpha);
    }

    /** Notch: zero gain at {@code f0}, unit gain at DC and Nyquist. */
    public static void notch(double[] dest, int off, double f0, double q) {
        check(dest, off, 1, f0, q);
        double c = cosW(f0);
        double alpha = sinW(f0) / (2 * q);
        set(dest, off, 1, -2 * c, 1, 1 + alpha, -2 * c, 1 - alpha);
    }

    /** All-pass: unit gain everywhere, phase −π at {@code f0}. */
    public static void allpass(double[] dest, int off, double f0, double q) {
        check(dest, off, 1, f0, q);
        double c = cosW(f0);
        double alpha = sinW(f0) / (2 * q);
        set(dest, off, 1 - alpha, -2 * c, 1 + alpha, 1 + alpha, -2 * c, 1 - alpha);
    }

    /** Peaking EQ: gain {@code gainDb} at {@code f0}, unit gain far from it. */
    public static void peaking(double[] dest, int off, double f0, double q, double gainDb) {
        check(dest, off, 1, f0, q);
        double a = AccurateMath.pow(10.0, gainDb / 40.0);
        double c = cosW(f0);
        double alpha = sinW(f0) / (2 * q);
        set(dest, off, 1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a);
    }

    /** Low shelf: gain {@code gainDb} at DC, unit gain at Nyquist, half the gain (in dB) at {@code f0}. */
    public static void lowShelf(double[] dest, int off, double f0, double q, double gainDb) {
        check(dest, off, 1, f0, q);
        double a = AccurateMath.pow(10.0, gainDb / 40.0);
        double c = cosW(f0);
        double s = 2 * java.lang.Math.sqrt(a) * sinW(f0) / (2 * q);
        double ap = a + 1;
        double am = a - 1;
        set(dest, off, a * (ap - am * c + s), 2 * a * (am - ap * c), a * (ap - am * c - s),
                ap + am * c + s, -2 * (am + ap * c), ap + am * c - s);
    }

    /** High shelf: gain {@code gainDb} at Nyquist, unit gain at DC, half the gain (in dB) at {@code f0}. */
    public static void highShelf(double[] dest, int off, double f0, double q, double gainDb) {
        check(dest, off, 1, f0, q);
        double a = AccurateMath.pow(10.0, gainDb / 40.0);
        double c = cosW(f0);
        double s = 2 * java.lang.Math.sqrt(a) * sinW(f0) / (2 * q);
        double ap = a + 1;
        double am = a - 1;
        set(dest, off, a * (ap + am * c + s), -2 * a * (am + ap * c), a * (ap + am * c - s),
                ap - am * c + s, 2 * (am - ap * c), ap - am * c - s);
    }

    /**
     * A Butterworth low-pass of any order as a cascade: {@code order / 2} second-order sections, plus a
     * first-order section (with {@code b2 = a2 = 0}) when the order is odd. {@code |H(f0)|² = 1/2}.
     *
     * @param dest receives {@link #butterworthSections(int)} sections
     * @param order the order, at least 1
     * @return the number of sections written
     */
    public static int butterworthLowpass(double[] dest, int off, int order, double f0) {
        return butterworth(dest, off, order, f0, false);
    }

    /** A Butterworth high-pass of any order; see {@link #butterworthLowpass}. */
    public static int butterworthHighpass(double[] dest, int off, int order, double f0) {
        return butterworth(dest, off, order, f0, true);
    }

    /** @return the number of sections of a Butterworth filter of this order: {@code ceil(order / 2)} */
    public static int butterworthSections(int order) {
        return (order + 1) / 2;
    }

    private static int butterworth(double[] dest, int off, int order, double f0, boolean high) {
        if (order < 1) {
            throw new IllegalArgumentException("order must be at least 1: " + order);
        }
        int sections = butterworthSections(order);
        check(dest, off, sections, f0, 1.0);
        int o = off;
        for (int k = 1; k <= order / 2; k++) {
            // pole pair k of the analog prototype: Q = 1 / (2·sin((2k - 1)π / (2·order)))
            double q = 1.0 / (2 * AccurateMath.sinTurns(2L * k - 1, 4L * order));
            if (high) {
                highpass(dest, o, f0, q);
            } else {
                lowpass(dest, o, f0, q);
            }
            o += STRIDE;
        }
        if ((order & 1) == 1) {
            // first order: H(s) = 1/(s + 1) (or s/(s + 1)), bilinear with K = tan(π f0)
            double kk = sinHalf(f0) / cosHalf(f0);
            double a1 = (kk - 1) / (kk + 1);
            double b0 = high ? 1 / (kk + 1) : kk / (kk + 1);
            dest[o] = b0;
            dest[o + 1] = high ? -b0 : b0;
            dest[o + 2] = 0;
            dest[o + 3] = a1;
            dest[o + 4] = 0;
        }
        return sections;
    }

    /** {@code cos(2π f0)} with exact reduction of the turn fraction. */
    private static double cosW(double f0) {
        return AccurateMath.cos(2 * java.lang.Math.PI * f0);
    }

    private static double sinW(double f0) {
        return AccurateMath.sin(2 * java.lang.Math.PI * f0);
    }

    private static double sinHalf(double f0) {
        return AccurateMath.sin(java.lang.Math.PI * f0);
    }

    private static double cosHalf(double f0) {
        return AccurateMath.cos(java.lang.Math.PI * f0);
    }

    /** {@code (1 - cos w)/2 = sin²(w/2)}, without cancellation. */
    private static double halfAngleSin2(double f0) {
        double s = sinHalf(f0);
        return s * s;
    }

    /** {@code (1 + cos w)/2 = cos²(w/2)}. */
    private static double halfAngleCos2(double f0) {
        double c = cosHalf(f0);
        return c * c;
    }

    private static void set(double[] d, int o, double b0, double b1, double b2, double a0, double a1, double a2) {
        d[o] = b0 / a0;
        d[o + 1] = b1 / a0;
        d[o + 2] = b2 / a0;
        d[o + 3] = a1 / a0;
        d[o + 4] = a2 / a0;
    }

    private static void check(double[] dest, int off, int sections, double f0, double q) {
        if (dest == null || off < 0 || off > dest.length - STRIDE * sections) {
            throw new IllegalArgumentException("dest must hold " + STRIDE * sections + " coefficients from " + off);
        }
        if (!(f0 > 0 && f0 < 0.5)) {
            throw new IllegalArgumentException("frequency must be in (0, 0.5) cycles per sample: " + f0);
        }
        if (!(q > 0)) {
            throw new IllegalArgumentException("q must be > 0: " + q);
        }
    }
}
