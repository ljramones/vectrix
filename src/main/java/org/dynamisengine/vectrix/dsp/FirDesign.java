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
 * <p>
 * The window designs and the responses do not allocate; {@link #remez} allocates its frequency grid. Argument errors,
 * including a design with no gain at its reference frequency, throw {@link IllegalArgumentException} before any tap
 * is written.
 *
 * @since 2.5.0
 */
public final class FirDesign {

    private FirDesign() {
    }

    /**
     * Kaiser's window shape for a stop-band attenuation.
     *
     * @param attenuationDb the attenuation in dB, positive and finite; below 21 dB the result is 0 (rectangular)
     * @return β for {@link Windows#kaiser}
     * @throws IllegalArgumentException if {@code attenuationDb} is not positive and finite
     */
    public static double kaiserBeta(double attenuationDb) {
        checkAttenuation(attenuationDb);
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
     * @param attenuationDb the attenuation in dB, positive and finite
     * @param transitionWidth the transition band in cycles per sample, in (0, 0.5)
     * @return the number of taps, at least 1
     * @throws IllegalArgumentException if {@code attenuationDb} is not positive and finite, {@code transitionWidth}
     *         is outside (0, 0.5), or the estimate exceeds {@link Integer#MAX_VALUE}
     */
    public static int kaiserTaps(double attenuationDb, double transitionWidth) {
        checkAttenuation(attenuationDb);
        if (!(transitionWidth > 0 && transitionWidth < 0.5)) {
            throw new IllegalArgumentException("transition width must be in (0, 0.5): " + transitionWidth);
        }
        double taps = java.lang.Math.ceil((attenuationDb - 7.95) / (14.357 * transitionWidth) + 1);
        if (!(taps <= Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("the filter would need more than 2^31 - 1 taps: " + taps);
        }
        return (int) java.lang.Math.max(1, taps);
    }

    private static void checkAttenuation(double attenuationDb) {
        if (!(attenuationDb > 0 && attenuationDb < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("attenuation must be positive and finite: " + attenuationDb);
        }
    }

    /**
     * A low-pass filter.
     *
     * @param h receives the taps
     * @param off the offset of the first tap
     * @param n the number of taps, at least 1
     * @param cutoff the cutoff in cycles per sample, in (0, 0.5)
     * @param window {@code n} window values from {@code windowOff}, or {@code null} for rectangular
     * @param windowOff the offset of the window
     * @throws IllegalArgumentException if {@code cutoff} is outside (0, 0.5), an array does not hold {@code n}
     *         values from its offset, or the design has no gain at DC
     */
    public static void lowpass(double[] h, int off, int n, double cutoff, double[] window, int windowOff) {
        checkBand(cutoff);
        check(h, off, n, window, windowOff);
        design(LOWPASS, cutoff, 0, h, off, n, window, windowOff, 0.0);
    }

    /**
     * A high-pass filter: an odd number of taps is required (an even-length symmetric filter has a zero at
     * Nyquist).
     *
     * @param h receives the taps
     * @param off the offset of the first tap
     * @param n the number of taps, odd
     * @param cutoff the cutoff in cycles per sample, in (0, 0.5)
     * @param window {@code n} window values from {@code windowOff}, or {@code null} for rectangular
     * @param windowOff the offset of the window
     * @throws IllegalArgumentException if {@code cutoff} is outside (0, 0.5), {@code n} is even, an array does not
     *         hold {@code n} values from its offset, or the design has no gain at Nyquist
     */
    public static void highpass(double[] h, int off, int n, double cutoff, double[] window, int windowOff) {
        checkBand(cutoff);
        check(h, off, n, window, windowOff);
        requireOdd(n);
        design(HIGHPASS, cutoff, 0, h, off, n, window, windowOff, 0.5);
    }

    /**
     * A band-pass filter, unit gain at the band centre.
     *
     * @param h receives the taps
     * @param off the offset of the first tap
     * @param n the number of taps, at least 1
     * @param low the lower edge in cycles per sample, {@code > 0}
     * @param high the upper edge, {@code low < high < 0.5}
     * @param window {@code n} window values from {@code windowOff}, or {@code null} for rectangular
     * @param windowOff the offset of the window
     * @throws IllegalArgumentException if an edge is outside (0, 0.5), {@code low >= high}, an array does not hold
     *         {@code n} values from its offset, or the design has no gain at the band centre
     */
    public static void bandpass(double[] h, int off, int n, double low, double high, double[] window, int windowOff) {
        checkBand(low);
        checkBand(high);
        if (!(low < high)) {
            throw new IllegalArgumentException("band edges must increase: " + low + ", " + high);
        }
        check(h, off, n, window, windowOff);
        design(BANDPASS, low, high, h, off, n, window, windowOff, 0.5 * (low + high));
    }

    /**
     * A band-stop (notch) filter, unit gain at DC; an odd number of taps is required.
     *
     * @param h receives the taps
     * @param off the offset of the first tap
     * @param n the number of taps, odd
     * @param low the lower edge in cycles per sample, {@code > 0}
     * @param high the upper edge, {@code low < high < 0.5}
     * @param window {@code n} window values from {@code windowOff}, or {@code null} for rectangular
     * @param windowOff the offset of the window
     * @throws IllegalArgumentException if an edge is outside (0, 0.5), {@code low >= high}, {@code n} is even, an
     *         array does not hold {@code n} values from its offset, or the design has no gain at DC
     */
    public static void bandstop(double[] h, int off, int n, double low, double high, double[] window, int windowOff) {
        checkBand(low);
        checkBand(high);
        if (!(low < high)) {
            throw new IllegalArgumentException("band edges must increase: " + low + ", " + high);
        }
        check(h, off, n, window, windowOff);
        requireOdd(n);
        design(BANDSTOP, low, high, h, off, n, window, windowOff, 0.0);
    }

    /**
     * An optimal equiripple linear-phase filter (Parks–McClellan, by the Remez exchange): the odd-length symmetric
     * filter that minimizes the largest weighted error {@code W(f)·|D(f) - A(f)|} over the given bands, where
     * {@code A} is the filter's real amplitude. The bands are pairs of edges in cycles per sample; the gaps between
     * them are transition bands, left free. Up to the grid resolution the result has the equal-ripple alternation
     * that characterizes the optimum, so for a given length it beats any window design. The design allocates its
     * dense frequency grid and working arrays, of a size proportional to {@code n}.
     *
     * @param h receives the taps
     * @param off the offset of the first tap
     * @param n the number of taps, odd and at least 3
     * @param bands {@code 2·k} ascending edges in [0, 0.5]: band {@code j} is {@code [bands[2j], bands[2j + 1]]}
     * @param desired the {@code k} band gains
     * @param weights the {@code k} positive band weights (a band weighted 10 has a tenth of the ripple)
     * @return the achieved weighted ripple {@code δ} (after at most 200 exchanges; an infeasible specification
     *         returns its best attempt, recognizable by a large ripple)
     * @throws IllegalArgumentException if {@code n} is even or less than 3, {@code h} does not hold {@code n} taps
     *         from {@code off}, the array lengths do not match, an edge is outside [0, 0.5] or out of order, a
     *         weight is not positive, or the bands are too narrow for {@code n} taps (checked before any tap is
     *         written)
     */
    public static double remez(double[] h, int off, int n, double[] bands, double[] desired, double[] weights) {
        if (n < 3 || (n & 1) == 0) {
            throw new IllegalArgumentException("the Remez design needs an odd number of taps >= 3: " + n);
        }
        check(h, off, n, null, 0);
        int nb = desired.length;
        if (bands.length != 2 * nb || weights.length != nb || nb < 1) {
            throw new IllegalArgumentException("need 2k band edges, k gains and k weights");
        }
        for (int j = 0; j < 2 * nb; j++) {
            if (!(bands[j] >= 0 && bands[j] <= 0.5) || j > 0 && bands[j] < bands[j - 1]) {
                throw new IllegalArgumentException("band edges must ascend within [0, 0.5]");
            }
        }
        for (double wt : weights) {
            if (!(wt > 0)) {
                throw new IllegalArgumentException("weights must be positive");
            }
        }
        int l = (n - 1) / 2;
        int r = l + 2;
        // dense grid over the bands, edges included
        double total = 0;
        for (int j = 0; j < nb; j++) {
            total += bands[2 * j + 1] - bands[2 * j];
        }
        int target = 32 * (l + 1);
        int count = 0;
        int[] per = new int[nb];
        for (int j = 0; j < nb; j++) {
            per[j] = Math.max(2, (int) Math.ceil(target * (bands[2 * j + 1] - bands[2 * j]) / Math.max(total, 1e-12)) + 1);
            count += per[j];
        }
        double[] gf = new double[count];
        double[] gd = new double[count];
        double[] gw = new double[count];
        int[] gb = new int[count];
        int g = 0;
        for (int j = 0; j < nb; j++) {
            for (int i = 0; i < per[j]; i++) {
                gf[g] = bands[2 * j] + (bands[2 * j + 1] - bands[2 * j]) * i / (per[j] - 1);
                gd[g] = desired[j];
                gw[g] = weights[j];
                gb[g] = j;
                g++;
            }
        }
        if (count < r) {
            throw new IllegalArgumentException("the bands are too narrow for " + n + " taps");
        }
        int[] ext = new int[r];
        for (int i = 0; i < r; i++) {
            ext[i] = (int) ((long) i * (count - 1) / (r - 1));
        }
        double[] x = new double[r];
        double[] cval = new double[r];
        double[] bw = new double[r];
        double[] err = new double[count];
        int[] cand = new int[count];
        double delta = 0;
        boolean converged = false;
        for (int iter = 0; iter < 200 && !converged; iter++) {
            for (int i = 0; i < r; i++) {
                x[i] = cos2pi(gf[ext[i]]);
            }
            baryWeights(x, r, bw);
            double num = 0;
            double den = 0;
            for (int i = 0; i < r; i++) {
                num += bw[i] * gd[ext[i]];
                den += ((i & 1) == 0 ? 1 : -1) * bw[i] / gw[ext[i]];
            }
            delta = num / den;
            for (int i = 0; i < r - 1; i++) {
                cval[i] = gd[ext[i]] - ((i & 1) == 0 ? 1 : -1) * delta / gw[ext[i]];
            }
            baryWeights(x, r - 1, bw);
            for (int k = 0; k < count; k++) {
                err[k] = gw[k] * (gd[k] - interp(x, bw, cval, r - 1, cos2pi(gf[k])));
            }
            // candidate extrema: local extrema of E within each band (band ends included), merged to alternate
            int nc = 0;
            for (int k = 0; k < count; k++) {
                double e = err[k];
                if (e == 0) {
                    continue;
                }
                boolean left = k == 0 || gb[k - 1] != gb[k] || (e > 0 ? e >= err[k - 1] : e <= err[k - 1]);
                boolean right = k == count - 1 || gb[k + 1] != gb[k] || (e > 0 ? e >= err[k + 1] : e <= err[k + 1]);
                if (left && right) {
                    cand[nc++] = k;
                }
            }
            nc = alternate(cand, nc, err);
            // too many: drop from whichever end has the smaller error (alternation is preserved)
            int first = 0;
            while (nc - first > r) {
                if (Math.abs(err[cand[first]]) < Math.abs(err[cand[nc - 1]])) {
                    first++;
                } else {
                    nc--;
                }
            }
            if (first > 0) {
                System.arraycopy(cand, first, cand, 0, nc - first);
                nc -= first;
            }
            if (nc < r) {
                break; // the current set already alternates as well as the grid allows
            }
            converged = true;
            for (int i = 0; i < r; i++) {
                converged &= cand[i] == ext[i];
                ext[i] = cand[i];
            }
        }
        // amplitude at f = k/n, k = 0..l, then the inverse cosine transform to taps (exactly reduced cosines)
        for (int i = 0; i < r; i++) {
            x[i] = cos2pi(gf[ext[i]]);
        }
        baryWeights(x, r, bw);
        double num = 0;
        double den = 0;
        for (int i = 0; i < r; i++) {
            num += bw[i] * gd[ext[i]];
            den += ((i & 1) == 0 ? 1 : -1) * bw[i] / gw[ext[i]];
        }
        delta = num / den;
        for (int i = 0; i < r - 1; i++) {
            cval[i] = gd[ext[i]] - ((i & 1) == 0 ? 1 : -1) * delta / gw[ext[i]];
        }
        baryWeights(x, r - 1, bw);
        double[] amp = new double[l + 1];
        for (int k = 0; k <= l; k++) {
            amp[k] = interp(x, bw, cval, r - 1, AccurateMath.cosTurns(k, n));
        }
        for (int m = 0; m < n; m++) {
            double v = amp[0];
            for (int k = 1; k <= l; k++) {
                v += 2 * amp[k] * AccurateMath.cosTurns((long) k * (m - l), n);
            }
            h[off + m] = v / n;
        }
        return Math.abs(delta);
    }

    private static double cos2pi(double f) {
        return AccurateMath.cos(2 * Math.PI * f);
    }

    /** Keeps one candidate per run of equal error signs (the largest), in place; returns the new count. */
    private static int alternate(int[] cand, int nc, double[] err) {
        int k = 0;
        for (int i = 0; i < nc; i++) {
            int c = cand[i];
            if (k > 0 && Math.signum(err[cand[k - 1]]) == Math.signum(err[c])) {
                if (Math.abs(err[c]) > Math.abs(err[cand[k - 1]])) {
                    cand[k - 1] = c;
                }
            } else {
                cand[k++] = c;
            }
        }
        return k;
    }

    /** Barycentric weights {@code 1/Π(2(x_i - x_j))} (the factor 2 keeps the products in range). */
    private static void baryWeights(double[] x, int m, double[] w) {
        for (int i = 0; i < m; i++) {
            double p = 1;
            for (int j = 0; j < m; j++) {
                if (j != i) {
                    p *= 2 * (x[i] - x[j]);
                }
            }
            w[i] = 1 / p;
        }
    }

    /** The barycentric Lagrange interpolant through {@code (x_i, c_i)}, {@code i < m}. */
    private static double interp(double[] x, double[] w, double[] c, int m, double at) {
        double num = 0;
        double den = 0;
        for (int i = 0; i < m; i++) {
            double d = at - x[i];
            if (d == 0) {
                return c[i];
            }
            double t = w[i] / d;
            num += t * c[i];
            den += t;
        }
        return num / den;
    }

    /**
     * The frequency response {@code H(f) = Σ h[k]·exp(-2πi f k)}.
     *
     * @param h the taps
     * @param off the offset of the first tap
     * @param n the number of taps, at least 1
     * @param f the frequency in cycles per sample
     * @param dest receives the real part at {@code dest[0]} and the imaginary part at {@code dest[1]}
     * @throws IllegalArgumentException if {@code n < 1}, {@code h} does not hold {@code n} taps from {@code off},
     *         or {@code dest} holds fewer than 2 values (checked before anything is written)
     */
    public static void response(double[] h, int off, int n, double f, double[] dest) {
        check(h, off, n, null, 0);
        if (dest == null || dest.length < 2) {
            throw new IllegalArgumentException("dest must hold 2 values");
        }
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

    /**
     * The magnitude response {@code |H(f)|} of the taps.
     *
     * @param h the taps
     * @param off the offset of the first tap
     * @param n the number of taps, at least 1
     * @param f the frequency in cycles per sample
     * @return the linear gain at {@code f}
     * @throws IllegalArgumentException if {@code n < 1} or {@code h} does not hold {@code n} taps from {@code off}
     */
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

    private static final int LOWPASS = 0;
    private static final int HIGHPASS = 1;
    private static final int BANDPASS = 2;
    private static final int BANDSTOP = 3;

    /** Tap {@code k} of a window design, before normalization. */
    private static double tap(int kind, double a, double b, int k, int n, double[] window, int windowOff) {
        double v;
        switch (kind) {
            case LOWPASS: v = sinc(a, k, n); break;
            case HIGHPASS: v = delta(k, n) - sinc(a, k, n); break;
            case BANDPASS: v = sinc(b, k, n) - sinc(a, k, n); break;
            default: v = delta(k, n) - sinc(b, k, n) + sinc(a, k, n); break;
        }
        return v * w(window, windowOff, k);
    }

    /**
     * Writes the taps scaled to unit gain at {@code f}. The gain is computed from the unscaled taps before any is
     * written, so a design without gain throws with {@code h} untouched; the window may be {@code h} itself.
     */
    private static void design(int kind, double a, double b, double[] h, int off, int n, double[] window,
            int windowOff, double f) {
        double re = 0.0;
        double im = 0.0;
        for (int k = 0; k < n; k++) {
            double t = tap(kind, a, b, k, n, window, windowOff);
            double ang = 2 * java.lang.Math.PI * reduce(f * k);
            re += t * AccurateMath.cos(ang);
            im -= t * AccurateMath.sin(ang);
        }
        double g = java.lang.Math.sqrt(re * re + im * im);
        if (!(g > 0 && g < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("the design has no finite gain at its reference frequency " + f);
        }
        if (window == h && windowOff < off) {
            // the window lies in h before the taps: write backwards, so no window value is overwritten unread
            for (int k = n - 1; k >= 0; k--) {
                h[off + k] = tap(kind, a, b, k, n, window, windowOff) / g;
            }
        } else {
            for (int k = 0; k < n; k++) {
                h[off + k] = tap(kind, a, b, k, n, window, windowOff) / g;
            }
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
