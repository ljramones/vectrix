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
 * <p>
 * {@code periodogram}, {@code welch}, {@code crossWelch} and {@code analytic} each have a float overload that
 * computes in double exactly as the double method does on the widened inputs and rounds only its outputs, so its result is the double method's rounded once to float. The float overloads keep
 * their intermediate results in double scratch, so they take more of it: {@link #floatScratchSize(RealFftd)},
 * {@link #floatCrossScratchSize(RealFftd)} and {@link #floatScratchSize(ComplexFftd)} doubles.
 *
 * @since 2.5.0
 */
public final class Spectra {

    private Spectra() {
    }

    /**
     * Returns the scratch size of {@link #periodogram} and {@link #welch} for this plan.
     *
     * @param plan the real FFT plan of the segment length
     * @return the number of scratch doubles
     */
    public static int scratchSize(RealFftd plan) {
        return plan.spectrumLength() + plan.scratchSize();
    }

    /**
     * Returns the scratch size of {@link #analytic} for this plan.
     *
     * @param plan the complex FFT plan of the signal length
     * @return the number of scratch doubles
     */
    public static int scratchSize(ComplexFftd plan) {
        return plan.scratchSize();
    }

    /**
     * Returns the scratch size of the float overloads of {@link #periodogram} and {@link #welch} for this plan:
     * {@link #scratchSize(RealFftd)} plus {@code n/2 + 1} doubles that accumulate the densities.
     *
     * @param plan the real FFT plan of the segment length
     * @return the number of scratch doubles
     * @since 3.0.0
     */
    public static int floatScratchSize(RealFftd plan) {
        return scratchSize(plan) + plan.size() / 2 + 1;
    }

    /**
     * Returns the scratch size of the float overload of {@link #analytic} for this plan:
     * {@link #scratchSize(ComplexFftd)} plus {@code 2n} doubles that hold the complex signal.
     *
     * @param plan the complex FFT plan of the signal length
     * @return the number of scratch doubles
     * @since 3.0.0
     */
    public static int floatScratchSize(ComplexFftd plan) {
        return plan.scratchSize() + 2 * plan.size();
    }

    /**
     * The periodogram of one windowed segment of {@code n = plan.size()} samples.
     *
     * @param plan the real FFT plan; its size is the segment length {@code n}
     * @param window {@code n} window values from {@code windowOff}, or {@code null} for rectangular
     * @param windowOff the offset of the window values
     * @param x the samples
     * @param xOff the offset of the first sample
     * @param psd receives {@code n/2 + 1} densities, from DC to Nyquist; must not overlap {@code scratch}
     * @param psdOff the offset of the first density
     * @param scratch working memory of {@link #scratchSize(RealFftd)} doubles
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if an array does not hold its values from its offset (checked before
     *         anything is written)
     */
    public static void periodogram(RealFftd plan, double[] window, int windowOff, double[] x, int xOff,
            double[] psd, int psdOff, double[] scratch, int scratchOff) {
        int n = plan.size();
        check(x, xOff, n, "x");
        checkCommon(plan, window, windowOff, psd, psdOff, scratch, scratchOff);
        java.util.Arrays.fill(psd, psdOff, psdOff + n / 2 + 1, 0.0);
        load(window, windowOff, x, xOff, n, scratch, scratchOff);
        accumulate(plan, psd, psdOff, scratch, scratchOff);
        scale(n, energy(window, windowOff, n), psd, psdOff, 1);
    }

    /**
     * The periodogram of float samples, computing in double exactly as
     * {@link #periodogram(RealFftd, double[], int, double[], int, double[], int, double[], int)} does on the widened
     * values; only the densities are rounded to float.
     *
     * @param plan the real FFT plan; its size is the segment length {@code n}
     * @param window {@code n} window values from {@code windowOff}, or {@code null} for rectangular
     * @param windowOff the offset of the window values
     * @param x the samples
     * @param xOff the offset of the first sample
     * @param psd receives {@code n/2 + 1} densities, from DC to Nyquist
     * @param psdOff the offset of the first density
     * @param scratch working memory of {@link #floatScratchSize(RealFftd)} doubles
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if an array does not hold its values from its offset (checked before
     *         anything is written)
     * @since 3.0.0
     */
    public static void periodogram(RealFftd plan, float[] window, int windowOff, float[] x, int xOff,
            float[] psd, int psdOff, double[] scratch, int scratchOff) {
        int n = plan.size();
        int bins = n / 2 + 1;
        check(x, xOff, n, "x");
        checkCommon(plan, window, windowOff, psd, psdOff, scratch, scratchOff);
        int work = scratchOff + bins;
        java.util.Arrays.fill(scratch, scratchOff, work, 0.0);
        load(window, windowOff, x, xOff, n, scratch, work);
        accumulate(plan, scratch, scratchOff, scratch, work);
        scale(n, energy(window, windowOff, n), scratch, scratchOff, 1);
        round(scratch, scratchOff, psd, psdOff, bins);
    }

    /**
     * Welch's estimate: the average of the periodograms of segments of {@code plan.size()} samples, advancing by
     * {@code hop}; the samples after the last whole segment are ignored.
     *
     * @param plan the real FFT plan; its size is the segment length {@code n}
     * @param window {@code n} window values from {@code windowOff}, or {@code null} for rectangular
     * @param windowOff the offset of the window values
     * @param hop the distance between segment starts in samples, at least 1
     * @param x the samples
     * @param xOff the offset of the first sample
     * @param length the number of samples, at least {@code plan.size()}
     * @param psd receives {@code n/2 + 1} densities, from DC to Nyquist; must not overlap {@code scratch}
     * @param psdOff the offset of the first density
     * @param scratch working memory of {@link #scratchSize(RealFftd)} doubles
     * @param scratchOff the offset into {@code scratch}
     * @return the number of segments averaged
     * @throws IllegalArgumentException if {@code hop < 1}, {@code length < plan.size()}, or an array does not hold
     *         its values from its offset (checked before anything is written)
     */
    public static int welch(RealFftd plan, double[] window, int windowOff, int hop, double[] x, int xOff,
            int length, double[] psd, int psdOff, double[] scratch, int scratchOff) {
        int n = plan.size();
        checkSegments(n, hop, length);
        check(x, xOff, length, "x");
        checkCommon(plan, window, windowOff, psd, psdOff, scratch, scratchOff);
        java.util.Arrays.fill(psd, psdOff, psdOff + n / 2 + 1, 0.0);
        int segments = 0;
        for (int start = 0; start + n <= length; start += hop) {
            load(window, windowOff, x, xOff + start, n, scratch, scratchOff);
            accumulate(plan, psd, psdOff, scratch, scratchOff);
            segments++;
        }
        scale(n, energy(window, windowOff, n), psd, psdOff, segments);
        return segments;
    }

    /**
     * Welch's estimate of float samples, computing in double exactly as
     * {@link #welch(RealFftd, double[], int, int, double[], int, int, double[], int, double[], int)} does on the
     * widened values; only the densities are rounded to float.
     *
     * @param plan the real FFT plan; its size is the segment length {@code n}
     * @param window {@code n} window values from {@code windowOff}, or {@code null} for rectangular
     * @param windowOff the offset of the window values
     * @param hop the distance between segment starts in samples, at least 1
     * @param x the samples
     * @param xOff the offset of the first sample
     * @param length the number of samples, at least {@code plan.size()}
     * @param psd receives {@code n/2 + 1} densities, from DC to Nyquist
     * @param psdOff the offset of the first density
     * @param scratch working memory of {@link #floatScratchSize(RealFftd)} doubles
     * @param scratchOff the offset into {@code scratch}
     * @return the number of segments averaged
     * @throws IllegalArgumentException if {@code hop < 1}, {@code length < plan.size()}, or an array does not hold
     *         its values from its offset (checked before anything is written)
     * @since 3.0.0
     */
    public static int welch(RealFftd plan, float[] window, int windowOff, int hop, float[] x, int xOff,
            int length, float[] psd, int psdOff, double[] scratch, int scratchOff) {
        int n = plan.size();
        int bins = n / 2 + 1;
        checkSegments(n, hop, length);
        check(x, xOff, length, "x");
        checkCommon(plan, window, windowOff, psd, psdOff, scratch, scratchOff);
        int work = scratchOff + bins;
        java.util.Arrays.fill(scratch, scratchOff, work, 0.0);
        int segments = 0;
        for (int start = 0; start + n <= length; start += hop) {
            load(window, windowOff, x, xOff + start, n, scratch, work);
            accumulate(plan, scratch, scratchOff, scratch, work);
            segments++;
        }
        scale(n, energy(window, windowOff, n), scratch, scratchOff, segments);
        round(scratch, scratchOff, psd, psdOff, bins);
        return segments;
    }

    /**
     * Returns the scratch size of {@link #crossWelch} for this plan.
     *
     * @param plan the real FFT plan of the segment length
     * @return the number of scratch doubles
     */
    public static int crossScratchSize(RealFftd plan) {
        return 2 * plan.spectrumLength() + plan.scratchSize();
    }

    /**
     * Returns the scratch size of the float overload of {@link #crossWelch} for this plan:
     * {@link #crossScratchSize(RealFftd)} plus {@code 2·(n/2 + 1)} doubles that accumulate the cross spectrum.
     *
     * @param plan the real FFT plan of the segment length
     * @return the number of scratch doubles
     * @since 3.0.0
     */
    public static int floatCrossScratchSize(RealFftd plan) {
        return crossScratchSize(plan) + 2 * (plan.size() / 2 + 1);
    }

    /**
     * Welch's estimate of the one-sided cross power spectral density {@code P_xy[k] = c·conj(X[k])·Y[k] / Σw²},
     * averaged over segments, scaled like {@link #welch} (so {@code P_xx} is the PSD of {@code x}).
     *
     * @param plan the real FFT plan; its size is the segment length {@code n}
     * @param window {@code n} window values from {@code windowOff}, or {@code null} for rectangular
     * @param windowOff the offset of the window values
     * @param hop the distance between segment starts in samples, at least 1
     * @param x the first signal
     * @param xOff the offset of the first sample of {@code x}
     * @param y the second signal
     * @param yOff the offset of the first sample of {@code y}
     * @param length the number of samples of each signal, at least {@code plan.size()}
     * @param out receives {@code n/2 + 1} complex values (real, imaginary) interleaved, from DC to Nyquist; must
     *        not overlap {@code scratch}
     * @param outOff the offset of the first value
     * @param scratch working memory of {@link #crossScratchSize(RealFftd)} doubles
     * @param scratchOff the offset into {@code scratch}
     * @return the number of segments averaged
     * @throws IllegalArgumentException if {@code hop < 1}, {@code length < plan.size()}, or an array does not hold
     *         its values from its offset (checked before anything is written)
     */
    public static int crossWelch(RealFftd plan, double[] window, int windowOff, int hop, double[] x, int xOff,
            double[] y, int yOff, int length, double[] out, int outOff, double[] scratch, int scratchOff) {
        int n = plan.size();
        checkSegments(n, hop, length);
        check(x, xOff, length, "x");
        check(y, yOff, length, "y");
        check(out, outOff, 2 * (n / 2 + 1), "out");
        check(scratch, scratchOff, crossScratchSize(plan), "scratch");
        if (window != null) {
            check(window, windowOff, n, "window");
        }
        int sp = plan.spectrumLength();
        java.util.Arrays.fill(out, outOff, outOff + 2 * (n / 2 + 1), 0.0);
        int segments = 0;
        for (int start = 0; start + n <= length; start += hop) {
            for (int k = 0; k < n; k++) {
                double w = window == null ? 1.0 : window[windowOff + k];
                scratch[scratchOff + k] = x[xOff + start + k] * w;
                scratch[scratchOff + sp + k] = y[yOff + start + k] * w;
            }
            crossAccumulate(plan, out, outOff, scratch, scratchOff);
            segments++;
        }
        crossScale(n, energy(window, windowOff, n), out, outOff, segments);
        return segments;
    }

    /**
     * Welch's cross spectrum of float signals, computing in double exactly as
     * {@link #crossWelch(RealFftd, double[], int, int, double[], int, double[], int, int, double[], int, double[], int)}
     * does on the widened values; only the cross spectrum is rounded to float.
     *
     * @param plan the real FFT plan; its size is the segment length {@code n}
     * @param window {@code n} window values from {@code windowOff}, or {@code null} for rectangular
     * @param windowOff the offset of the window values
     * @param hop the distance between segment starts in samples, at least 1
     * @param x the first signal
     * @param xOff the offset of the first sample of {@code x}
     * @param y the second signal
     * @param yOff the offset of the first sample of {@code y}
     * @param length the number of samples of each signal, at least {@code plan.size()}
     * @param out receives {@code n/2 + 1} complex values (real, imaginary) interleaved, from DC to Nyquist
     * @param outOff the offset of the first value
     * @param scratch working memory of {@link #floatCrossScratchSize(RealFftd)} doubles
     * @param scratchOff the offset into {@code scratch}
     * @return the number of segments averaged
     * @throws IllegalArgumentException if {@code hop < 1}, {@code length < plan.size()}, or an array does not hold
     *         its values from its offset (checked before anything is written)
     * @since 3.0.0
     */
    public static int crossWelch(RealFftd plan, float[] window, int windowOff, int hop, float[] x, int xOff,
            float[] y, int yOff, int length, float[] out, int outOff, double[] scratch, int scratchOff) {
        int n = plan.size();
        int values = 2 * (n / 2 + 1);
        checkSegments(n, hop, length);
        check(x, xOff, length, "x");
        check(y, yOff, length, "y");
        check(out, outOff, values, "out");
        check(scratch, scratchOff, floatCrossScratchSize(plan), "scratch");
        if (window != null) {
            check(window, windowOff, n, "window");
        }
        int sp = plan.spectrumLength();
        int work = scratchOff + values;
        java.util.Arrays.fill(scratch, scratchOff, work, 0.0);
        int segments = 0;
        for (int start = 0; start + n <= length; start += hop) {
            for (int k = 0; k < n; k++) {
                double w = window == null ? 1.0 : window[windowOff + k];
                scratch[work + k] = x[xOff + start + k] * w;
                scratch[work + sp + k] = y[yOff + start + k] * w;
            }
            crossAccumulate(plan, scratch, scratchOff, scratch, work);
            segments++;
        }
        crossScale(n, energy(window, windowOff, n), scratch, scratchOff, segments);
        round(scratch, scratchOff, out, outOff, values);
        return segments;
    }

    /** Transforms the two windowed segments at {@code buf} and {@code buf + sp} and adds {@code conj(X)·Y}. */
    private static void crossAccumulate(RealFftd plan, double[] out, int outOff, double[] scratch, int buf) {
        int n = plan.size();
        int sp = plan.spectrumLength();
        int bx = buf;
        int by = buf + sp;
        int work = buf + 2 * sp;
        plan.forward(scratch, bx, scratch, bx, scratch, work);
        plan.forward(scratch, by, scratch, by, scratch, work);
        for (int k = 0; k <= n / 2; k++) {
            double xr = scratch[bx + 2 * k];
            double xi = scratch[bx + 2 * k + 1];
            double yr = scratch[by + 2 * k];
            double yi = scratch[by + 2 * k + 1];
            out[outOff + 2 * k] += xr * yr + xi * yi;     // conj(X)·Y
            out[outOff + 2 * k + 1] += xr * yi - xi * yr;
        }
    }

    private static void crossScale(int n, double s2, double[] out, int outOff, int segments) {
        double base = 1.0 / (s2 * segments);
        for (int k = 0; k <= n / 2; k++) {
            double c = k == 0 || 2 * k == n ? base : 2 * base;
            out[outOff + 2 * k] *= c;
            out[outOff + 2 * k + 1] *= c;
        }
    }

    /** Writes the windowed segment into {@code scratch[buf .. buf + n)}. */
    private static void load(double[] window, int windowOff, double[] x, int xOff, int n, double[] scratch, int buf) {
        for (int k = 0; k < n; k++) {
            scratch[buf + k] = window == null ? x[xOff + k] : x[xOff + k] * window[windowOff + k];
        }
    }

    /** Writes the widened windowed segment into {@code scratch[buf .. buf + n)}. */
    private static void load(float[] window, int windowOff, float[] x, int xOff, int n, double[] scratch, int buf) {
        for (int k = 0; k < n; k++) {
            scratch[buf + k] = window == null ? (double) x[xOff + k] : (double) x[xOff + k] * window[windowOff + k];
        }
    }

    /** Transforms the windowed segment at {@code buf} and adds its power to {@code psd}. */
    private static void accumulate(RealFftd plan, double[] psd, int psdOff, double[] scratch, int buf) {
        int n = plan.size();
        plan.forward(scratch, buf, scratch, buf, scratch, buf + plan.spectrumLength());
        for (int k = 0; k <= n / 2; k++) {
            double re = scratch[buf + 2 * k];
            double im = scratch[buf + 2 * k + 1];
            psd[psdOff + k] += re * re + im * im;
        }
    }

    /** {@code Σ w²} over the window, {@code n} for a rectangular one. */
    private static double energy(double[] window, int windowOff, int n) {
        double s2 = 0.0;
        for (int k = 0; k < n; k++) {
            double w = window == null ? 1.0 : window[windowOff + k];
            s2 += w * w;
        }
        return s2;
    }

    /** {@code Σ w²} over the widened window, {@code n} for a rectangular one. */
    private static double energy(float[] window, int windowOff, int n) {
        double s2 = 0.0;
        for (int k = 0; k < n; k++) {
            double w = window == null ? 1.0 : window[windowOff + k];
            s2 += w * w;
        }
        return s2;
    }

    private static void scale(int n, double s2, double[] psd, int psdOff, int segments) {
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
     * @param plan the complex FFT plan; its size is the signal length {@code n}
     * @param x the real samples
     * @param xOff the offset of the first sample
     * @param out receives {@code n} complex values, interleaved (real, imaginary); must not overlap {@code x}
     * @param outOff the offset of the first value
     * @param scratch working memory of {@link #scratchSize(ComplexFftd)} doubles
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if an array does not hold its values from its offset (checked before
     *         anything is written)
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
        hilbert(plan, out, outOff, scratch, scratchOff);
    }

    /**
     * The analytic signal of float samples, computing in double exactly as
     * {@link #analytic(ComplexFftd, double[], int, double[], int, double[], int)} does on the widened values; only the
     * result is rounded to float.
     *
     * @param plan the complex FFT plan; its size is the signal length {@code n}
     * @param x the real samples
     * @param xOff the offset of the first sample
     * @param out receives {@code n} complex values, interleaved (real, imaginary); may overlap {@code x} (it is
     *        written last)
     * @param outOff the offset of the first value
     * @param scratch working memory of {@link #floatScratchSize(ComplexFftd)} doubles
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if an array does not hold its values from its offset (checked before
     *         anything is written)
     * @since 3.0.0
     */
    public static void analytic(ComplexFftd plan, float[] x, int xOff, float[] out, int outOff, double[] scratch,
            int scratchOff) {
        int n = plan.size();
        check(x, xOff, n, "x");
        check(out, outOff, 2 * n, "out");
        check(scratch, scratchOff, floatScratchSize(plan), "scratch");
        for (int k = 0; k < n; k++) {
            scratch[scratchOff + 2 * k] = x[xOff + k];
            scratch[scratchOff + 2 * k + 1] = 0.0;
        }
        hilbert(plan, scratch, scratchOff, scratch, scratchOff + 2 * n);
        round(scratch, scratchOff, out, outOff, 2 * n);
    }

    /** Turns the complex signal {@code data[off .. off + 2n)} (imaginary parts 0) into its analytic signal. */
    private static void hilbert(ComplexFftd plan, double[] data, int off, double[] scratch, int scratchOff) {
        int n = plan.size();
        plan.forward(data, off, scratch, scratchOff);
        // keep DC (and Nyquist), double the positive frequencies, clear the negative ones
        for (int k = 1; k < n; k++) {
            int o = off + 2 * k;
            if (2 * k < n) {
                data[o] *= 2;
                data[o + 1] *= 2;
            } else if (2 * k > n) {
                data[o] = 0.0;
                data[o + 1] = 0.0;
            }
        }
        plan.inverse(data, off, scratch, scratchOff);
    }

    private static void round(double[] src, int srcOff, float[] dst, int dstOff, int count) {
        for (int i = 0; i < count; i++) {
            dst[dstOff + i] = (float) src[srcOff + i];
        }
    }

    private static void checkSegments(int n, int hop, int length) {
        if (hop < 1 || length < n) {
            throw new IllegalArgumentException("need hop >= 1 and length >= n: hop " + hop + ", length " + length);
        }
    }

    private static void checkCommon(RealFftd plan, double[] window, int windowOff, double[] psd, int psdOff,
            double[] scratch, int scratchOff) {
        int n = plan.size();
        check(psd, psdOff, n / 2 + 1, "psd");
        check(scratch, scratchOff, scratchSize(plan), "scratch");
        if (window != null) {
            check(window, windowOff, n, "window");
        }
    }

    private static void checkCommon(RealFftd plan, float[] window, int windowOff, float[] psd, int psdOff,
            double[] scratch, int scratchOff) {
        int n = plan.size();
        check(psd, psdOff, n / 2 + 1, "psd");
        check(scratch, scratchOff, floatScratchSize(plan), "scratch");
        if (window != null) {
            check(window, windowOff, n, "window");
        }
    }

    private static void check(double[] a, int off, int count, String name) {
        if (a == null || off < 0 || off > a.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " doubles from offset " + off);
        }
    }

    private static void check(float[] a, int off, int count, String name) {
        if (a == null || off < 0 || off > a.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " floats from offset " + off);
        }
    }
}
