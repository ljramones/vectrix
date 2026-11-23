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
 * Linear-phase FIR filter design by the window method, and FIR frequency responses.
 * <p>
 * The designs write {@code n} taps {@code h[0..n)}: the ideal (sinc) response centred at {@code (n-1)/2}, times a
 * window (any {@link Windows} output of length {@code n}, or {@code null} for rectangular), then scaled to exactly
 * unit gain at the reference frequency: DC for low-pass and band-stop, Nyquist for high-pass, the band centre for
 * band-pass. The taps are symmetric, so the phase is linear with a delay of {@code (n-1)/2} samples. Use the taps
 * with {@link org.dynamisengine.vectrix.fft.BlockConvolverd}.
 * <p>
 * For a Kaiser design, {@link #kaiserBeta} and {@link #kaiserTaps} give the window shape and length for a
 * stop-band attenuation and transition width (Kaiser's formulas).
 *
 * @since 2.5.0
 */
public final class FirDesign {

    private FirDesign() {
    }

    /**
     * Kaiser's window shape for a stop-band attenuation.
     *
     * @param attenuationDb the attenuation in dB, {@code > 0}
     * @return β for {@link Windows#kaiser}
     */
    public static double kaiserBeta(double attenuationDb) {
        double a = attenuationDb;
        if (a > 50) {
            return 0.1102 * (a - 8.7);
        }
        if (a >= 21) {
            return 0.5842 * AccurateMath.pow(a - 21, 0.4) + 0.07886 * (a - 21);
        }
        return 0.0;
    }

    /**
     * Kaiser's estimate of the taps needed for a stop-band attenuation and transition width.
     *
     * @param attenuationDb the attenuation in dB, {@code > 0}
     * @param transitionWidth the transition band in cycles per sample, in (0, 0.5)
     * @return the number of taps, at least 1
     */
    public static int kaiserTaps(double attenuationDb, double transitionWidth) {
        if (!(transitionWidth > 0 && transitionWidth < 0.5)) {
            throw new IllegalArgumentException("transition width must be in (0, 0.5): " + transitionWidth);
        }
        double taps = (attenuationDb - 7.95) / (14.357 * transitionWidth) + 1;
        return (int) java.lang.Math.max(1, java.lang.Math.ceil(taps));
    }

    /**
     * A low-pass filter.
     *
     * @param h receives the taps
     * @param off the offset of the first tap
     * @param n the number of taps
     * @param cutoff the cutoff in cycles per sample, in (0, 0.5)
     * @param window {@code n} window values from {@code windowOff}, or {@code null} for rectangular
     * @param windowOff the offset of the window
     */
    public static void lowpass(double[] h, int off, int n, double cutoff, double[] window, int windowOff) {
        checkBand(cutoff);
        check(h, off, n, window, windowOff);
        for (int k = 0; k < n; k++) {
            h[off + k] = sinc(cutoff, k, n) * w(window, windowOff, k);
        }
        normalize(h, off, n, 0.0);
    }

    /**
     * A high-pass filter: an odd number of taps is required (an even-length symmetric filter has a zero at
     * Nyquist).
     *
     * @param cutoff the cutoff in cycles per sample, in (0, 0.5)
     */
    public static void highpass(double[] h, int off, int n, double cutoff, double[] window, int windowOff) {
        checkBand(cutoff);
        check(h, off, n, window, windowOff);
        requireOdd(n);
        for (int k = 0; k < n; k++) {
            h[off + k] = (delta(k, n) - sinc(cutoff, k, n)) * w(window, windowOff, k);
        }
        normalize(h, off, n, 0.5);
    }

    /**
     * A band-pass filter, unit gain at the band centre.
     *
     * @param low the lower edge in cycles per sample
     * @param high the upper edge, {@code low < high < 0.5}
     */
    public static void bandpass(double[] h, int off, int n, double low, double high, double[] window, int windowOff) {
        checkBand(low);
        checkBand(high);
        if (!(low < high)) {
            throw new IllegalArgumentException("band edges must increase: " + low + ", " + high);
        }
        check(h, off, n, window, windowOff);
        for (int k = 0; k < n; k++) {
            h[off + k] = (sinc(high, k, n) - sinc(low, k, n)) * w(window, windowOff, k);
        }
        normalize(h, off, n, 0.5 * (low + high));
    }

    /**
     * A band-stop (notch) filter, unit gain at DC; an odd number of taps is required.
     *
     * @param low the lower edge in cycles per sample
     * @param high the upper edge, {@code low < high < 0.5}
     */
    public static void bandstop(double[] h, int off, int n, double low, double high, double[] window, int windowOff) {
        checkBand(low);
        checkBand(high);
        if (!(low < high)) {
            throw new IllegalArgumentException("band edges must increase: " + low + ", " + high);
        }
        check(h, off, n, window, windowOff);
        requireOdd(n);
        for (int k = 0; k < n; k++) {
            h[off + k] = (delta(k, n) - sinc(high, k, n) + sinc(low, k, n)) * w(window, windowOff, k);
        }
        normalize(h, off, n, 0.0);
    }

    /**
     * The frequency response {@code H(f) = Σ h[k]·exp(-2πi f k)}.
     *
     * @param f the frequency in cycles per sample
     * @param dest receives the real part at {@code dest[0]} and the imaginary part at {@code dest[1]}
     */
    public static void response(double[] h, int off, int n, double f, double[] dest) {
        check(h, off, n, null, 0);
        double re = 0.0;
        double im = 0.0;
        for (int k = 0; k < n; k++) {
            double a = 2 * java.lang.Math.PI * reduce(f * k);
            re += h[off + k] * AccurateMath.cos(a);
            im -= h[off + k] * AccurateMath.sin(a);
        }
        dest[0] = re;
        dest[1] = im;
    }

    /** {@code |H(f)|}. */
    public static double magnitude(double[] h, int off, int n, double f) {
        check(h, off, n, null, 0);
        double re = 0.0;
        double im = 0.0;
        for (int k = 0; k < n; k++) {
            double a = 2 * java.lang.Math.PI * reduce(f * k);
            re += h[off + k] * AccurateMath.cos(a);
            im -= h[off + k] * AccurateMath.sin(a);
        }
        return java.lang.Math.sqrt(re * re + im * im);
    }

    /** The fractional part, in [-0.5, 0.5]: the phase in turns, so large {@code f·k} keep their precision. */
    private static double reduce(double turns) {
        return turns - java.lang.Math.rint(turns);
    }

    /** The ideal low-pass impulse response {@code 2fc·sinc(2fc·t)}, {@code t = k - (n-1)/2}. */
    private static double sinc(double fc, int k, int n) {
        double t = k - 0.5 * (n - 1); // exact: an integer or a half-integer
        if (t == 0) {
            return 2 * fc;
        }
        return AccurateMath.sin(2 * java.lang.Math.PI * reduce(fc * t)) / (java.lang.Math.PI * t);
    }

    private static double delta(int k, int n) {
        return 2 * k == n - 1 ? 1.0 : 0.0;
    }

    private static double w(double[] window, int off, int k) {
        return window == null ? 1.0 : window[off + k];
    }

    /** Scales the taps to unit gain at {@code f}. */
    private static void normalize(double[] h, int off, int n, double f) {
        double g = magnitude(h, off, n, f);
        if (!(g > 0)) {
            throw new IllegalArgumentException("the design has no gain at its reference frequency " + f);
        }
        for (int k = 0; k < n; k++) {
            h[off + k] /= g;
        }
    }

    private static void checkBand(double f) {
        if (!(f > 0 && f < 0.5)) {
            throw new IllegalArgumentException("frequency must be in (0, 0.5) cycles per sample: " + f);
        }
    }

    private static void requireOdd(int n) {
        if ((n & 1) == 0) {
            throw new IllegalArgumentException("this design needs an odd number of taps: " + n);
        }
    }

    private static void check(double[] h, int off, int n, double[] window, int windowOff) {
        if (h == null || n < 1 || off < 0 || off > h.length - n) {
            throw new IllegalArgumentException("h must hold " + n + " taps from offset " + off);
        }
        if (window != null && (windowOff < 0 || windowOff > window.length - n)) {
            throw new IllegalArgumentException("window must hold " + n + " values from offset " + windowOff);
        }
    }
}
