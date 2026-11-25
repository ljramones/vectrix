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

import org.dynamisengine.vectrix.fft.RealFft2d;
import org.dynamisengine.vectrix.fft.RealFftd;

/**
 * Wiener filtering: the minimum-mean-square-error linear estimate of a desired signal from an observed one.
 * <ul>
 * <li>{@link #fir}: the optimal causal FIR filter from the Wiener–Hopf equations (a symmetric Toeplitz system,
 * solved by {@link Toeplitz#solve}).</li>
 * <li>{@link #noncausal}: the optimal non-causal (two-sided, infinite) filter {@code H = P_xy/P_xx} from cross and
 * auto spectra ({@link Spectra#crossWelch}); {@link #impulseResponse} turns it into a centred FIR.</li>
 * <li>{@link #gain}: the noise-reduction gain {@code S/(S + N)} per bin, for STFT frames.</li>
 * <li>{@link #deconvolve} and {@link #deconvolve2d}: Wiener deconvolution, {@code X = conj(H)·Y/(|H|² + NSR)},
 * undoing a known blur in signals and images.</li>
 * <li>{@link #adaptive2d}: the locally adaptive image filter (as MATLAB's {@code wiener2}).</li>
 * <li>{@link #apply}: multiplies a half spectrum by per-bin gains, such as those of {@link #gain}.</li>
 * </ul>
 * Results are deterministic; the processing methods take caller scratch and do not allocate. Arrays without an
 * offset parameter are used from index 0.
 * <p>
 * Every processing method has a float overload that computes in double exactly as the double method does on the
 * widened inputs and rounds only its outputs, so its result is the double method's rounded once to float. Their
 * scratch is double; the float {@code deconvolve2d} needs more of it, {@link #deconvolve2dFloatScratchSize(RealFft2d)}
 * doubles.
 *
 * @since 2.5.0
 */
public final class Wiener {

    private Wiener() {
    }

    /**
     * The optimal {@code n}-tap FIR filter {@code w} minimizing {@code E[(d[t] - Σ w[k]·x[t - k])²]}: the solution of
     * {@code Σ_k w[k]·rxx[|i - k|] = rdx[i]}.
     *
     * @param rxx the observation's autocorrelation at lags {@code 0..n-1}
     * @param rdx the cross-correlation {@code E[d[t]·x[t - i]]} at lags {@code 0..n-1}
     * @param n the number of taps, at least 1
     * @param w receives {@code n} taps; may be {@code rdx}
     * @param scratch {@code 2n} doubles
     * @throws IllegalArgumentException if {@code n < 1}, an array is too short, or a leading submatrix of the
     *         autocorrelation matrix is singular (see {@link Toeplitz#solve})
     */
    public static void fir(double[] rxx, double[] rdx, int n, double[] w, double[] scratch) {
        Toeplitz.solve(rxx, rdx, n, w, scratch);
    }

    /**
     * The optimal FIR filter of float correlations, computing in double exactly as
     * {@link #fir(double[], double[], int, double[], double[])} does on the widened values (with
     * {@link Toeplitz#solve(float[], float[], int, float[], double[])}); only the taps are rounded to float.
     *
     * @param rxx the observation's autocorrelation at lags {@code 0..n-1}
     * @param rdx the cross-correlation {@code E[d[t]·x[t - i]]} at lags {@code 0..n-1}
     * @param n the number of taps, at least 1
     * @param w receives {@code n} taps; may be {@code rdx}
     * @param scratch {@code 2n} doubles
     * @throws IllegalArgumentException if {@code n < 1}, an array is too short, or a leading submatrix of the
     *         autocorrelation matrix is singular (see {@link Toeplitz#solve})
     * @since 3.0.0
     */
    public static void fir(float[] rxx, float[] rdx, int n, float[] w, double[] scratch) {
        Toeplitz.solve(rxx, rdx, n, w, scratch);
    }

    /**
     * The Wiener gain {@code S/(S + N)} per bin, clamped to {@code [floor, 1]} (a floor above 0 limits the
     * "musical noise" of spectral subtraction); bins with {@code S + N = 0} get the floor.
     *
     * Negative power estimates count as 0.
     *
     * @param signalPower {@code n} signal power estimates
     * @param noisePower {@code n} noise power estimates
     * @param n the number of bins
     * @param floor the smallest gain, normally in [0, 1)
     * @param gain receives {@code n} gains; may be either input
     * @throws IllegalArgumentException if {@code n} is negative or an array holds fewer than {@code n} values
     *         (checked before anything is written)
     */
    public static void gain(double[] signalPower, double[] noisePower, int n, double floor, double[] gain) {
        count(n);
        check(signalPower, 0, n, "signalPower");
        check(noisePower, 0, n, "noisePower");
        check(gain, 0, n, "gain");
        for (int i = 0; i < n; i++) {
            double s = Math.max(signalPower[i], 0);
            double t = s + Math.max(noisePower[i], 0);
            double g = t > 0 ? s / t : 0;
            gain[i] = Math.min(1, Math.max(floor, g));
        }
    }

    /**
     * The Wiener gain of float power estimates, computing in double exactly as
     * {@link #gain(double[], double[], int, double, double[])} does on the widened values; only the gains are rounded
     * to float.
     *
     * @param signalPower {@code n} signal power estimates
     * @param noisePower {@code n} noise power estimates
     * @param n the number of bins
     * @param floor the smallest gain, normally in [0, 1)
     * @param gain receives {@code n} gains; may be either input
     * @throws IllegalArgumentException if {@code n} is negative or an array holds fewer than {@code n} values
     *         (checked before anything is written)
     * @since 3.0.0
     */
    public static void gain(float[] signalPower, float[] noisePower, int n, float floor, float[] gain) {
        count(n);
        check(signalPower, 0, n, "signalPower");
        check(noisePower, 0, n, "noisePower");
        check(gain, 0, n, "gain");
        for (int i = 0; i < n; i++) {
            double s = Math.max(signalPower[i], 0.0);
            double t = s + Math.max(noisePower[i], 0.0);
            double g = t > 0 ? s / t : 0;
            gain[i] = (float) Math.min(1, Math.max(floor, g));
        }
    }

    /**
     * The non-causal Wiener filter {@code H[k] = P_xy[k] / P_xx[k]} per bin, from the PSD of the observation and its
     * cross spectrum with the desired signal (both from {@link Spectra}); 0 where {@code P_xx} is not positive.
     *
     * @param pxx {@code bins} auto-spectrum values
     * @param pxy {@code bins} complex cross-spectrum values, interleaved
     * @param bins the number of frequency bins
     * @param h receives {@code bins} complex responses, interleaved; may be {@code pxy}
     * @throws IllegalArgumentException if {@code bins} is negative or an array is too short (checked before
     *         anything is written)
     */
    public static void noncausal(double[] pxx, double[] pxy, int bins, double[] h) {
        count(bins);
        check(pxx, 0, bins, "pxx");
        check(pxy, 0, 2 * bins, "pxy");
        check(h, 0, 2 * bins, "h");
        for (int k = 0; k < bins; k++) {
            double d = pxx[k];
            h[2 * k] = d > 0 ? pxy[2 * k] / d : 0.0;
            h[2 * k + 1] = d > 0 ? pxy[2 * k + 1] / d : 0.0;
        }
    }

    /**
     * The non-causal Wiener filter of float spectra, computing in double exactly as
     * {@link #noncausal(double[], double[], int, double[])} does on the widened values; only the responses are rounded
     * to float.
     *
     * @param pxx {@code bins} auto-spectrum values
     * @param pxy {@code bins} complex cross-spectrum values, interleaved
     * @param bins the number of frequency bins
     * @param h receives {@code bins} complex responses, interleaved; may be {@code pxy}
     * @throws IllegalArgumentException if {@code bins} is negative or an array is too short (checked before
     *         anything is written)
     * @since 3.0.0
     */
    public static void noncausal(float[] pxx, float[] pxy, int bins, float[] h) {
        count(bins);
        check(pxx, 0, bins, "pxx");
        check(pxy, 0, 2 * bins, "pxy");
        check(h, 0, 2 * bins, "h");
        for (int k = 0; k < bins; k++) {
            double d = pxx[k];
            h[2 * k] = d > 0 ? (float) (pxy[2 * k] / d) : 0f;
            h[2 * k + 1] = d > 0 ? (float) (pxy[2 * k + 1] / d) : 0f;
        }
    }

    /**
     * The FIR taps of a frequency response on {@code plan}'s bins: its inverse transform, rotated by {@code n/2} so the
     * non-causal part comes first (a delay of {@code n/2} samples). Window the taps to shorten them.
     *
     * @param plan the real FFT plan; its size is the number of taps {@code n}
     * @param h the {@code n/2 + 1} complex responses, interleaved
     * @param taps receives {@code n} taps; must not be {@code scratch}
     * @param scratch {@code plan.spectrumLength() + plan.scratchSize()} doubles
     * @throws IllegalArgumentException if an array is too short (checked before anything is written)
     */
    public static void impulseResponse(RealFftd plan, double[] h, double[] taps, double[] scratch) {
        int n = plan.size();
        int sp = plan.spectrumLength();
        check(h, 0, sp, "h");
        check(taps, 0, n, "taps");
        check(scratch, 0, sp + plan.scratchSize(), "scratch");
        System.arraycopy(h, 0, scratch, 0, sp);
        plan.inverse(scratch, 0, scratch, 0, scratch, sp);
        for (int i = 0; i < n; i++) {
            taps[i] = scratch[(i + n - n / 2) % n];
        }
    }

    /**
     * The FIR taps of a float frequency response, computing in double exactly as
     * {@link #impulseResponse(RealFftd, double[], double[], double[])} does on the widened values; only the taps are
     * rounded to float.
     *
     * @param plan the real FFT plan; its size is the number of taps {@code n}
     * @param h the {@code n/2 + 1} complex responses, interleaved
     * @param taps receives {@code n} taps
     * @param scratch {@code plan.spectrumLength() + plan.scratchSize()} doubles
     * @throws IllegalArgumentException if an array is too short (checked before anything is written)
     * @since 3.0.0
     */
    public static void impulseResponse(RealFftd plan, float[] h, float[] taps, double[] scratch) {
        int n = plan.size();
        int sp = plan.spectrumLength();
        check(h, 0, sp, "h");
        check(taps, 0, n, "taps");
        check(scratch, 0, sp + plan.scratchSize(), "scratch");
        for (int i = 0; i < sp; i++) {
            scratch[i] = h[i];
        }
        plan.inverse(scratch, 0, scratch, 0, scratch, sp);
        for (int i = 0; i < n; i++) {
            taps[i] = (float) scratch[(i + n - n / 2) % n];
        }
    }

    /**
     * Returns the scratch size of {@link #deconvolve} for this plan.
     *
     * @param plan the real FFT plan of the signal length
     * @return the number of scratch doubles
     */
    public static int deconvolveScratchSize(RealFftd plan) {
        return 2 * plan.spectrumLength() + plan.scratchSize();
    }

    /**
     * Wiener deconvolution of a signal blurred by circular convolution with a known kernel:
     * {@code X = conj(H)·Y / (|H|² + nsr)}, where {@code nsr} is the noise-to-signal power ratio (0 is plain inverse
     * filtering, unstable where {@code H} is small; larger values trade sharpness for noise).
     *
     * @param plan the real FFT plan; its size is the signal length {@code n}
     * @param y the {@code n} observed samples
     * @param yOff the offset of the first observed sample
     * @param kernel the blur, {@code kLen <= n} taps (tap 0 is the unshifted one)
     * @param kOff the offset of the first tap
     * @param kLen the number of taps, in {@code [1, n]}
     * @param nsr the noise-to-signal ratio, {@code >= 0}, the same for every bin; see the overload for one per bin
     * @param out receives the {@code n} restored samples; may be {@code y}
     * @param outOff the offset of the first restored sample
     * @param scratch {@link #deconvolveScratchSize(RealFftd)} doubles of working memory; must not overlap the other
     *        arrays
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if {@code kLen} is outside {@code [1, n]}, {@code nsr} is negative or NaN, or
     *         an array does not hold its values from its offset (checked before anything is written)
     */
    public static void deconvolve(RealFftd plan, double[] y, int yOff, double[] kernel, int kOff, int kLen, double nsr,
            double[] out, int outOff, double[] scratch, int scratchOff) {
        deconvolve(plan, y, yOff, kernel, kOff, kLen, null, nsr, out, outOff, scratch, scratchOff);
    }

    /**
     * Wiener deconvolution with a noise-to-signal ratio per bin; otherwise as
     * {@link #deconvolve(RealFftd, double[], int, double[], int, int, double, double[], int, double[], int)}.
     *
     * @param plan the real FFT plan; its size is the signal length {@code n}
     * @param y the {@code n} observed samples
     * @param yOff the offset of the first observed sample
     * @param kernel the blur, {@code kLen <= n} taps (tap 0 is the unshifted one)
     * @param kOff the offset of the first tap
     * @param kLen the number of taps, in {@code [1, n]}
     * @param nsrPerBin {@code n/2 + 1} noise-to-signal ratios, {@code >= 0}, from DC to Nyquist, from index 0
     * @param out receives the {@code n} restored samples; may be {@code y}
     * @param outOff the offset of the first restored sample
     * @param scratch {@link #deconvolveScratchSize(RealFftd)} doubles of working memory; must not overlap the other
     *        arrays
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if {@code kLen} is outside {@code [1, n]}, {@code nsrPerBin} is null or
     *         shorter than {@code n/2 + 1} or holds a negative or NaN ratio, or an array does not hold its values
     *         from its offset (checked before anything is written)
     */
    public static void deconvolve(RealFftd plan, double[] y, int yOff, double[] kernel, int kOff, int kLen,
            double[] nsrPerBin, double[] out, int outOff, double[] scratch, int scratchOff) {
        if (nsrPerBin == null) {
            throw new IllegalArgumentException("nsrPerBin must not be null");
        }
        deconvolve(plan, y, yOff, kernel, kOff, kLen, nsrPerBin, 0.0, out, outOff, scratch, scratchOff);
    }

    private static void deconvolve(RealFftd plan, double[] y, int yOff, double[] kernel, int kOff, int kLen,
            double[] nsrBins, double nsr, double[] out, int outOff, double[] scratch, int scratchOff) {
        int n = plan.size();
        int sp = plan.spectrumLength();
        if (kLen < 1 || kLen > n) {
            throw new IllegalArgumentException("need 1 <= kLen <= n: " + kLen);
        }
        check(y, yOff, n, "y");
        check(kernel, kOff, kLen, "kernel");
        check(out, outOff, n, "out");
        check(scratch, scratchOff, deconvolveScratchSize(plan), "scratch");
        if (nsrBins == null) {
            checkNsr(nsr);
        } else {
            check(nsrBins, 0, n / 2 + 1, "nsrPerBin");
            for (int k = 0; k <= n / 2; k++) {
                checkNsr(nsrBins[k]);
            }
        }
        int by = scratchOff;
        int bh = scratchOff + sp;
        int work = scratchOff + 2 * sp;
        System.arraycopy(y, yOff, scratch, by, n);
        java.util.Arrays.fill(scratch, bh, bh + n, 0.0);
        System.arraycopy(kernel, kOff, scratch, bh, kLen);
        plan.forward(scratch, by, scratch, by, scratch, work);
        plan.forward(scratch, bh, scratch, bh, scratch, work);
        for (int k = 0; k < sp / 2; k++) {
            divide(scratch, by + 2 * k, bh + 2 * k, nsrBins == null ? nsr : nsrBins[k]);
        }
        plan.inverse(scratch, by, out, outOff, scratch, work);
    }

    private static void check(double[] a, int off, int count, String name) {
        if (a == null || off < 0 || off > a.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " doubles from offset " + off);
        }
    }

    /**
     * Wiener deconvolution of a float signal, computing in double exactly as
     * {@link #deconvolve(RealFftd, double[], int, double[], int, int, double, double[], int, double[], int)} does on
     * the widened values; only the restored samples are rounded to float.
     *
     * @param plan the real FFT plan; its size is the signal length {@code n}
     * @param y the {@code n} observed samples
     * @param yOff the offset of the first observed sample
     * @param kernel the blur, {@code kLen <= n} taps (tap 0 is the unshifted one)
     * @param kOff the offset of the first tap
     * @param kLen the number of taps, in {@code [1, n]}
     * @param nsr the noise-to-signal ratio, {@code >= 0}, the same for every bin
     * @param out receives the {@code n} restored samples; may be {@code y}
     * @param outOff the offset of the first restored sample
     * @param scratch {@link #deconvolveScratchSize(RealFftd)} doubles of working memory
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if {@code kLen} is outside {@code [1, n]}, {@code nsr} is negative or NaN, or
     *         an array does not hold its values from its offset (checked before anything is written)
     * @since 3.0.0
     */
    public static void deconvolve(RealFftd plan, float[] y, int yOff, float[] kernel, int kOff, int kLen, double nsr,
            float[] out, int outOff, double[] scratch, int scratchOff) {
        deconvolve(plan, y, yOff, kernel, kOff, kLen, null, nsr, out, outOff, scratch, scratchOff);
    }

    /**
     * Wiener deconvolution of a float signal with a noise-to-signal ratio per bin, computing in double exactly as
     * {@link #deconvolve(RealFftd, double[], int, double[], int, int, double[], double[], int, double[], int)} does on
     * the widened values; only the restored samples are rounded to float.
     *
     * @param plan the real FFT plan; its size is the signal length {@code n}
     * @param y the {@code n} observed samples
     * @param yOff the offset of the first observed sample
     * @param kernel the blur, {@code kLen <= n} taps (tap 0 is the unshifted one)
     * @param kOff the offset of the first tap
     * @param kLen the number of taps, in {@code [1, n]}
     * @param nsrPerBin {@code n/2 + 1} noise-to-signal ratios, {@code >= 0}, from DC to Nyquist, from index 0
     * @param out receives the {@code n} restored samples; may be {@code y}
     * @param outOff the offset of the first restored sample
     * @param scratch {@link #deconvolveScratchSize(RealFftd)} doubles of working memory
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if {@code kLen} is outside {@code [1, n]}, {@code nsrPerBin} is null or
     *         shorter than {@code n/2 + 1} or holds a negative or NaN ratio, or an array does not hold its values
     *         from its offset (checked before anything is written)
     * @since 3.0.0
     */
    public static void deconvolve(RealFftd plan, float[] y, int yOff, float[] kernel, int kOff, int kLen,
            float[] nsrPerBin, float[] out, int outOff, double[] scratch, int scratchOff) {
        if (nsrPerBin == null) {
            throw new IllegalArgumentException("nsrPerBin must not be null");
        }
        deconvolve(plan, y, yOff, kernel, kOff, kLen, nsrPerBin, 0.0, out, outOff, scratch, scratchOff);
    }

    private static void deconvolve(RealFftd plan, float[] y, int yOff, float[] kernel, int kOff, int kLen,
            float[] nsrBins, double nsr, float[] out, int outOff, double[] scratch, int scratchOff) {
        int n = plan.size();
        int sp = plan.spectrumLength();
        if (kLen < 1 || kLen > n) {
            throw new IllegalArgumentException("need 1 <= kLen <= n: " + kLen);
        }
        check(y, yOff, n, "y");
        check(kernel, kOff, kLen, "kernel");
        check(out, outOff, n, "out");
        check(scratch, scratchOff, deconvolveScratchSize(plan), "scratch");
        if (nsrBins == null) {
            checkNsr(nsr);
        } else {
            check(nsrBins, 0, n / 2 + 1, "nsrPerBin");
            for (int k = 0; k <= n / 2; k++) {
                checkNsr(nsrBins[k]);
            }
        }
        int by = scratchOff;
        int bh = scratchOff + sp;
        int work = scratchOff + 2 * sp;
        for (int i = 0; i < n; i++) {
            scratch[by + i] = y[yOff + i];
        }
        java.util.Arrays.fill(scratch, bh, bh + n, 0.0);
        for (int i = 0; i < kLen; i++) {
            scratch[bh + i] = kernel[kOff + i];
        }
        plan.forward(scratch, by, scratch, by, scratch, work);
        plan.forward(scratch, bh, scratch, bh, scratch, work);
        for (int k = 0; k < sp / 2; k++) {
            divide(scratch, by + 2 * k, bh + 2 * k, nsrBins == null ? nsr : nsrBins[k]);
        }
        plan.inverse(scratch, by, scratch, by, scratch, work);
        for (int i = 0; i < n; i++) {
            out[outOff + i] = (float) scratch[by + i];
        }
    }

    private static void check(float[] a, int off, int count, String name) {
        if (a == null || off < 0 || off > a.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " floats from offset " + off);
        }
    }

    private static void count(int n) {
        if (n < 0 || n > Integer.MAX_VALUE / 2) {
            throw new IllegalArgumentException("count out of range: " + n);
        }
    }

    private static void checkNsr(double nsr) {
        if (!(nsr >= 0)) {
            throw new IllegalArgumentException("noise-to-signal ratios must be >= 0: " + nsr);
        }
    }

    /** {@code Y ← conj(H)·Y / (|H|² + nsr)} for one bin. */
    private static void divide(double[] s, int yi, int hi, double nsr) {
        double hr = s[hi];
        double him = s[hi + 1];
        double den = hr * hr + him * him + nsr;
        double yr = s[yi];
        double yim = s[yi + 1];
        if (den > 0) {
            s[yi] = (hr * yr + him * yim) / den;
            s[yi + 1] = (hr * yim - him * yr) / den;
        } else {
            s[yi] = 0.0;
            s[yi + 1] = 0.0;
        }
    }

    /**
     * Returns the scratch size of {@link #deconvolve2d} for this plan.
     *
     * @param plan the 2D real FFT plan of the image size
     * @return the number of scratch doubles
     */
    public static int deconvolve2dScratchSize(RealFft2d plan) {
        return 2 * plan.spectrumLength() + plan.scratchSize();
    }

    /**
     * Returns the scratch size of the float overload of {@link #deconvolve2d} for this plan:
     * {@link #deconvolve2dScratchSize(RealFft2d)} plus {@code rows·cols} doubles that hold the widened image (the 2D
     * transforms cannot work in place).
     *
     * @param plan the 2D real FFT plan of the image size
     * @return the number of scratch doubles
     * @since 3.0.0
     */
    public static int deconvolve2dFloatScratchSize(RealFft2d plan) {
        return deconvolve2dScratchSize(plan) + plan.rows() * plan.cols();
    }

    /**
     * Wiener deconvolution of an image ({@code rows × cols}, row-major) blurred by circular 2D convolution with a known
     * kernel ({@code kRows × kCols}, its tap {@code (0, 0)} unshifted): {@code X = conj(H)·Y / (|H|² + nsr)}.
     *
     * @param plan the 2D real FFT plan; its size is the image size {@code rows × cols}
     * @param y the {@code rows·cols} observed pixels, row-major without padding
     * @param yOff the offset of the first pixel
     * @param kernel the blur, {@code kRows·kCols} taps, row-major without padding
     * @param kOff the offset of the first tap
     * @param kRows the kernel height, in {@code [1, rows]}
     * @param kCols the kernel width, in {@code [1, cols]}
     * @param nsr the noise-to-signal ratio, {@code >= 0}, the same for every bin
     * @param out receives the {@code rows·cols} restored pixels, row-major; may be {@code y}
     * @param outOff the offset of the first restored pixel
     * @param scratch {@link #deconvolve2dScratchSize(RealFft2d)} doubles of working memory; must not overlap the
     *        other arrays
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if the kernel is empty or larger than the image, {@code nsr} is negative or
     *         NaN, or an array does not hold its values from its offset (checked before anything is written)
     */
    public static void deconvolve2d(RealFft2d plan, double[] y, int yOff, double[] kernel, int kOff, int kRows,
            int kCols, double nsr, double[] out, int outOff, double[] scratch, int scratchOff) {
        int rows = plan.rows();
        int cols = plan.cols();
        int sp = plan.spectrumLength();
        if (kRows < 1 || kCols < 1 || kRows > rows || kCols > cols) {
            throw new IllegalArgumentException("the kernel must fit the image: " + kRows + " x " + kCols);
        }
        check(y, yOff, rows * cols, "y");
        check(kernel, kOff, kRows * kCols, "kernel");
        check(out, outOff, rows * cols, "out");
        check(scratch, scratchOff, deconvolve2dScratchSize(plan), "scratch");
        checkNsr(nsr);
        int by = scratchOff;
        int bh = scratchOff + sp;
        int work = scratchOff + 2 * sp;
        // the kernel padded to the image size, laid out row-major in the second buffer's first rows·cols values
        java.util.Arrays.fill(scratch, bh, bh + rows * cols, 0.0);
        for (int r = 0; r < kRows; r++) {
            System.arraycopy(kernel, kOff + r * kCols, scratch, bh + r * cols, kCols);
        }
        // transform the padded kernel (copied to the first buffer, since input and output must not overlap)
        System.arraycopy(scratch, bh, scratch, by, rows * cols);
        plan.forward(scratch, by, scratch, bh, scratch, work);
        plan.forward(y, yOff, scratch, by, scratch, work);
        for (int k = 0; k < sp / 2; k++) {
            divide(scratch, by + 2 * k, bh + 2 * k, nsr);
        }
        plan.inverse(scratch, by, out, outOff, scratch, work);
    }

    /**
     * Wiener deconvolution of a float image, computing in double exactly as
     * {@link #deconvolve2d(RealFft2d, double[], int, double[], int, int, int, double, double[], int, double[], int)}
     * does on the widened values; only the restored pixels are rounded to float.
     *
     * @param plan the 2D real FFT plan; its size is the image size {@code rows × cols}
     * @param y the {@code rows·cols} observed pixels, row-major without padding
     * @param yOff the offset of the first pixel
     * @param kernel the blur, {@code kRows·kCols} taps, row-major without padding
     * @param kOff the offset of the first tap
     * @param kRows the kernel height, in {@code [1, rows]}
     * @param kCols the kernel width, in {@code [1, cols]}
     * @param nsr the noise-to-signal ratio, {@code >= 0}, the same for every bin
     * @param out receives the {@code rows·cols} restored pixels, row-major; may be {@code y}
     * @param outOff the offset of the first restored pixel
     * @param scratch {@link #deconvolve2dFloatScratchSize(RealFft2d)} doubles of working memory
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if the kernel is empty or larger than the image, {@code nsr} is negative or
     *         NaN, or an array does not hold its values from its offset (checked before anything is written)
     * @since 3.0.0
     */
    public static void deconvolve2d(RealFft2d plan, float[] y, int yOff, float[] kernel, int kOff, int kRows,
            int kCols, double nsr, float[] out, int outOff, double[] scratch, int scratchOff) {
        int rows = plan.rows();
        int cols = plan.cols();
        int sp = plan.spectrumLength();
        if (kRows < 1 || kCols < 1 || kRows > rows || kCols > cols) {
            throw new IllegalArgumentException("the kernel must fit the image: " + kRows + " x " + kCols);
        }
        check(y, yOff, rows * cols, "y");
        check(kernel, kOff, kRows * kCols, "kernel");
        check(out, outOff, rows * cols, "out");
        check(scratch, scratchOff, deconvolve2dFloatScratchSize(plan), "scratch");
        checkNsr(nsr);
        int by = scratchOff;
        int bh = scratchOff + sp;
        int img = scratchOff + 2 * sp;
        int work = img + rows * cols;
        java.util.Arrays.fill(scratch, bh, bh + rows * cols, 0.0);
        for (int r = 0; r < kRows; r++) {
            for (int c = 0; c < kCols; c++) {
                scratch[bh + r * cols + c] = kernel[kOff + r * kCols + c];
            }
        }
        System.arraycopy(scratch, bh, scratch, by, rows * cols);
        plan.forward(scratch, by, scratch, bh, scratch, work);
        for (int i = 0; i < rows * cols; i++) {
            scratch[img + i] = y[yOff + i];
        }
        plan.forward(scratch, img, scratch, by, scratch, work);
        for (int k = 0; k < sp / 2; k++) {
            divide(scratch, by + 2 * k, bh + 2 * k, nsr);
        }
        plan.inverse(scratch, by, scratch, img, scratch, work);
        for (int i = 0; i < rows * cols; i++) {
            out[outOff + i] = (float) scratch[img + i];
        }
    }

    /**
     * The locally adaptive Wiener filter for images: with the mean {@code μ} and variance {@code σ²} of the
     * {@code (2·half + 1)²} window around each pixel (cut at the borders),
     * {@code out = μ + max(σ² - ν², 0) / max(σ², ν²)·(x - μ)}, smoothing flat regions and keeping edges.
     *
     * @param img the {@code rows·cols} pixels, row-major without padding, from index 0
     * @param rows the image height, at least 1
     * @param cols the image width, at least 1
     * @param half the window radius in pixels, {@code >= 0}
     * @param noiseVariance {@code ν²}; 0 or less estimates it as the mean of the local variances
     * @param out receives the {@code rows·cols} filtered pixels; may be {@code img}
     * @param scratch {@code 2·(rows + 1)·(cols + 1)} doubles (two summed-area tables)
     * @return the noise variance used
     * @throws IllegalArgumentException if a size is out of range or an array is too short (checked before anything
     *         is written)
     */
    public static double adaptive2d(double[] img, int rows, int cols, int half, double noiseVariance, double[] out,
            double[] scratch) {
        checkAdaptive(rows, cols, half, img == null ? -1 : img.length, out == null ? -1 : out.length, scratch);
        int w = cols + 1;
        int s1 = 0;
        int s2 = (rows + 1) * w;
        java.util.Arrays.fill(scratch, 0, 2 * (rows + 1) * w, 0.0);
        for (int r = 0; r < rows; r++) {
            double row1 = 0;
            double row2 = 0;
            for (int c = 0; c < cols; c++) {
                double v = img[r * cols + c];
                row1 += v;
                row2 = Math.fma(v, v, row2);
                scratch[s1 + (r + 1) * w + c + 1] = scratch[s1 + r * w + c + 1] + row1;
                scratch[s2 + (r + 1) * w + c + 1] = scratch[s2 + r * w + c + 1] + row2;
            }
        }
        double nu = noiseVariance(scratch, rows, cols, half, noiseVariance);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                out[r * cols + c] = filtered(scratch, rows, cols, half, nu, r, c, img[r * cols + c]);
            }
        }
        return nu;
    }

    /**
     * The locally adaptive Wiener filter for float images, computing in double exactly as
     * {@link #adaptive2d(double[], int, int, int, double, double[], double[])} does on the widened values; only the
     * filtered pixels are rounded to float.
     *
     * @param img the {@code rows·cols} pixels, row-major without padding, from index 0
     * @param rows the image height, at least 1
     * @param cols the image width, at least 1
     * @param half the window radius in pixels, {@code >= 0}
     * @param noiseVariance {@code ν²}; 0 or less estimates it as the mean of the local variances
     * @param out receives the {@code rows·cols} filtered pixels; may be {@code img}
     * @param scratch {@code 2·(rows + 1)·(cols + 1)} doubles (two summed-area tables)
     * @return the noise variance used
     * @throws IllegalArgumentException if a size is out of range or an array is too short (checked before anything
     *         is written)
     * @since 3.0.0
     */
    public static double adaptive2d(float[] img, int rows, int cols, int half, double noiseVariance, float[] out,
            double[] scratch) {
        checkAdaptive(rows, cols, half, img == null ? -1 : img.length, out == null ? -1 : out.length, scratch);
        int w = cols + 1;
        int s1 = 0;
        int s2 = (rows + 1) * w;
        java.util.Arrays.fill(scratch, 0, 2 * (rows + 1) * w, 0.0);
        for (int r = 0; r < rows; r++) {
            double row1 = 0;
            double row2 = 0;
            for (int c = 0; c < cols; c++) {
                double v = img[r * cols + c];
                row1 += v;
                row2 = Math.fma(v, v, row2);
                scratch[s1 + (r + 1) * w + c + 1] = scratch[s1 + r * w + c + 1] + row1;
                scratch[s2 + (r + 1) * w + c + 1] = scratch[s2 + r * w + c + 1] + row2;
            }
        }
        double nu = noiseVariance(scratch, rows, cols, half, noiseVariance);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                out[r * cols + c] = (float) filtered(scratch, rows, cols, half, nu, r, c, img[r * cols + c]);
            }
        }
        return nu;
    }

    private static void checkAdaptive(int rows, int cols, int half, int imgLength, int outLength, double[] scratch) {
        if (rows < 1 || cols < 1 || half < 0 || imgLength < 0 || outLength < 0 || scratch == null
                || 2L * (rows + 1L) * (cols + 1L) > Integer.MAX_VALUE - 8 || imgLength < (long) rows * cols
                || outLength < (long) rows * cols || scratch.length < 2L * (rows + 1L) * (cols + 1L)) {
            throw new IllegalArgumentException("need rows·cols pixels and 2·(rows+1)·(cols+1) doubles of scratch");
        }
    }

    /** {@code noiseVariance} if positive, else the mean of the local variances from the summed-area tables. */
    private static double noiseVariance(double[] scratch, int rows, int cols, int half, double noiseVariance) {
        double nu = noiseVariance;
        if (!(nu > 0)) {
            int w = cols + 1;
            double sum = 0;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    sum += localVariance(scratch, 0, (rows + 1) * w, w, rows, cols, r, c, half);
                }
            }
            nu = sum / ((double) rows * cols);
        }
        return nu;
    }

    /** The filtered value of pixel {@code (r, c)}, whose value is {@code x}. */
    private static double filtered(double[] scratch, int rows, int cols, int half, double nu, int r, int c,
            double x) {
        int w = cols + 1;
        int s1 = 0;
        int s2 = (rows + 1) * w;
        int r0 = Math.max(0, r - half);
        int r1 = Math.min(rows, r + half + 1);
        int c0 = Math.max(0, c - half);
        int c1 = Math.min(cols, c + half + 1);
        double cnt = (double) (r1 - r0) * (c1 - c0);
        double mean = box(scratch, s1, w, r0, r1, c0, c1) / cnt;
        double var = Math.max(0, box(scratch, s2, w, r0, r1, c0, c1) / cnt - mean * mean);
        double g = Math.max(var - nu, 0) / Math.max(var, nu);
        return Math.fma(g, x - mean, mean);
    }

    private static double localVariance(double[] s, int s1, int s2, int w, int rows, int cols, int r, int c,
            int half) {
        int r0 = Math.max(0, r - half);
        int r1 = Math.min(rows, r + half + 1);
        int c0 = Math.max(0, c - half);
        int c1 = Math.min(cols, c + half + 1);
        double cnt = (double) (r1 - r0) * (c1 - c0);
        double mean = box(s, s1, w, r0, r1, c0, c1) / cnt;
        return Math.max(0, box(s, s2, w, r0, r1, c0, c1) / cnt - mean * mean);
    }

    /** The sum over rows {@code [r0, r1)} and columns {@code [c0, c1)} from a summed-area table. */
    private static double box(double[] s, int off, int w, int r0, int r1, int c0, int c1) {
        return (s[off + r1 * w + c1] - s[off + r0 * w + c1]) - (s[off + r1 * w + c0] - s[off + r0 * w + c0]);
    }

    /**
     * Multiplies a half spectrum (interleaved complex, as {@link RealFftd} produces) by real per-bin gains, in place.
     *
     * @param spectrum the interleaved complex bins, modified in place
     * @param off the offset of the first bin
     * @param gain {@code bins} gains, from index 0
     * @param bins the number of complex bins
     * @throws IllegalArgumentException if {@code bins} is negative or an array is too short from its offset
     *         (checked before anything is written)
     */
    public static void apply(double[] spectrum, int off, double[] gain, int bins) {
        count(bins);
        check(spectrum, off, 2 * bins, "spectrum");
        check(gain, 0, bins, "gain");
        for (int k = 0; k < bins; k++) {
            spectrum[off + 2 * k] *= gain[k];
            spectrum[off + 2 * k + 1] *= gain[k];
        }
    }

    /**
     * Multiplies a float half spectrum by float per-bin gains, in place. Each product is computed in double, where it
     * is exact, and rounded once, exactly as {@link #apply(double[], int, double[], int)} on the widened values.
     *
     * @param spectrum the interleaved complex bins, modified in place
     * @param off the offset of the first bin
     * @param gain {@code bins} gains, from index 0
     * @param bins the number of complex bins
     * @throws IllegalArgumentException if {@code bins} is negative or an array is too short from its offset
     *         (checked before anything is written)
     * @since 3.0.0
     */
    public static void apply(float[] spectrum, int off, float[] gain, int bins) {
        count(bins);
        check(spectrum, off, 2 * bins, "spectrum");
        check(gain, 0, bins, "gain");
        for (int k = 0; k < bins; k++) {
            double g = gain[k];
            spectrum[off + 2 * k] = (float) (spectrum[off + 2 * k] * g);
            spectrum[off + 2 * k + 1] = (float) (spectrum[off + 2 * k + 1] * g);
        }
    }
}
