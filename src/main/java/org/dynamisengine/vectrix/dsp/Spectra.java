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

import org.dynamisengine.vectrix.fft.ComplexFftd;
import org.dynamisengine.vectrix.fft.RealFftd;

/**
 * Spectral estimates and the analytic signal.
 * <p>
 * Power spectral densities are one-sided and density-scaled for frequencies in cycles per sample:
 * {@code P[k] = c·|X[k]|² / Σw²}, with {@code c = 2} except at DC and (for even {@code n}) Nyquist, where
 * {@code c = 1}. The density integrates to the signal's mean power: {@code Σ P[k] / n} is the mean square of
 * the windowed samples divided by the window's mean square (Parseval). Multiply by {@code 1/sampleRate} for a
 * density per hertz.
 * <p>
 * The methods take a caller-made FFT plan of the segment length and caller scratch of
 * {@link #scratchSize(RealFftd)} (or {@link #scratchSize(ComplexFftd)}) doubles; they do not allocate.
 *
 * @since 2.5.0
 */
public final class Spectra {

    private Spectra() {
    }

    /** @return the scratch doubles {@link #periodogram} and {@link #welch} need with this plan */
    public static int scratchSize(RealFftd plan) {
        return plan.spectrumLength() + plan.scratchSize();
    }

    /** @return the scratch doubles {@link #analytic} needs with this plan */
    public static int scratchSize(ComplexFftd plan) {
        return plan.scratchSize();
    }

    /**
     * The periodogram of one windowed segment of {@code plan.size()} samples.
     *
     * @param window {@code n} window values from {@code windowOff}, or {@code null} for rectangular
     * @param psd receives {@code n/2 + 1} densities
     */
    public static void periodogram(RealFftd plan, double[] window, int windowOff, double[] x, int xOff,
            double[] psd, int psdOff, double[] scratch, int scratchOff) {
        int n = plan.size();
        check(x, xOff, n, "x");
        check(psd, psdOff, n / 2 + 1, "psd");
        java.util.Arrays.fill(psd, psdOff, psdOff + n / 2 + 1, 0.0);
        accumulate(plan, window, windowOff, x, xOff, psd, psdOff, scratch, scratchOff);
        scale(plan, window, windowOff, psd, psdOff, 1);
    }

    /**
     * Welch's estimate: the average of the periodograms of segments of {@code plan.size()} samples, advancing by
     * {@code hop}; the samples after the last whole segment are ignored.
     *
     * @param length the number of samples, at least {@code plan.size()}
     * @param psd receives {@code n/2 + 1} densities
     * @return the number of segments averaged
     */
    public static int welch(RealFftd plan, double[] window, int windowOff, int hop, double[] x, int xOff,
            int length, double[] psd, int psdOff, double[] scratch, int scratchOff) {
        int n = plan.size();
        if (hop < 1 || length < n) {
            throw new IllegalArgumentException("need hop >= 1 and length >= n: hop " + hop + ", length " + length);
        }
        check(x, xOff, length, "x");
        check(psd, psdOff, n / 2 + 1, "psd");
        java.util.Arrays.fill(psd, psdOff, psdOff + n / 2 + 1, 0.0);
        int segments = 0;
        for (int start = 0; start + n <= length; start += hop) {
            accumulate(plan, window, windowOff, x, xOff + start, psd, psdOff, scratch, scratchOff);
            segments++;
        }
        scale(plan, window, windowOff, psd, psdOff, segments);
        return segments;
    }

    private static void accumulate(RealFftd plan, double[] window, int windowOff, double[] x, int xOff,
            double[] psd, int psdOff, double[] scratch, int scratchOff) {
        int n = plan.size();
        if (window != null) {
            check(window, windowOff, n, "window");
        }
        check(scratch, scratchOff, scratchSize(plan), "scratch");
        int buf = scratchOff;
        for (int k = 0; k < n; k++) {
            scratch[buf + k] = window == null ? x[xOff + k] : x[xOff + k] * window[windowOff + k];
        }
        plan.forward(scratch, buf, scratch, buf, scratch, buf + plan.spectrumLength());
        for (int k = 0; k <= n / 2; k++) {
            double re = scratch[buf + 2 * k];
            double im = scratch[buf + 2 * k + 1];
            psd[psdOff + k] += re * re + im * im;
        }
    }

    private static void scale(RealFftd plan, double[] window, int windowOff, double[] psd, int psdOff, int segments) {
        int n = plan.size();
        double s2 = 0.0;
        for (int k = 0; k < n; k++) {
            double w = window == null ? 1.0 : window[windowOff + k];
            s2 += w * w;
        }
        double base = 1.0 / (s2 * segments);
        for (int k = 0; k <= n / 2; k++) {
            boolean edge = k == 0 || 2 * k == n;
            psd[psdOff + k] *= edge ? base : 2 * base;
        }
    }

    /**
     * The analytic signal {@code x + i·H{x}} of {@code plan.size()} real samples, where {@code H} is the Hilbert
     * transform (computed with the FFT, so the signal is treated as periodic). Its magnitude is the envelope and
     * its argument the instantaneous phase.
     *
     * @param out receives {@code 2n} interleaved values; must not overlap {@code x}
     */
    public static void analytic(ComplexFftd plan, double[] x, int xOff, double[] out, int outOff, double[] scratch,
            int scratchOff) {
        int n = plan.size();
        check(x, xOff, n, "x");
        check(out, outOff, 2 * n, "out");
        check(scratch, scratchOff, plan.scratchSize(), "scratch");
        for (int k = 0; k < n; k++) {
            out[outOff + 2 * k] = x[xOff + k];
            out[outOff + 2 * k + 1] = 0.0;
        }
        plan.forward(out, outOff, scratch, scratchOff);
        // keep DC (and Nyquist), double the positive frequencies, clear the negative ones
        for (int k = 1; k < n; k++) {
            int o = outOff + 2 * k;
            if (2 * k < n) {
                out[o] *= 2;
                out[o + 1] *= 2;
            } else if (2 * k > n) {
                out[o] = 0.0;
                out[o + 1] = 0.0;
            }
        }
        plan.inverse(out, outOff, scratch, scratchOff);
    }

    private static void check(double[] a, int off, int count, String name) {
        if (a == null || off < 0 || off > a.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " doubles from offset " + off);
        }
    }
}
