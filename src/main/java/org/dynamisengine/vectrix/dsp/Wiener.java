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
 * </ul>
 * Results are deterministic; the processing methods take caller scratch and do not allocate.
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
     * @param w receives {@code n} taps
     * @param scratch {@code 2n} doubles
     */
    public static void fir(double[] rxx, double[] rdx, int n, double[] w, double[] scratch) {
        Toeplitz.solve(rxx, rdx, n, w, scratch);
    }

    /**
     * The Wiener gain {@code S/(S + N)} per bin, clamped to {@code [floor, 1]} (a floor above 0 limits the
     * "musical noise" of spectral subtraction); bins with {@code S + N = 0} get the floor.
     *
     * @param signalPower {@code n} signal power estimates
     * @param noisePower {@code n} noise power estimates
     * @param gain receives {@code n} gains
     */
    public static void gain(double[] signalPower, double[] noisePower, int n, double floor, double[] gain) {
        for (int i = 0; i < n; i++) {
            double s = Math.max(signalPower[i], 0);
            double t = s + Math.max(noisePower[i], 0);
            double g = t > 0 ? s / t : 0;
            gain[i] = Math.min(1, Math.max(floor, g));
        }
    }

    /**
     * The non-causal Wiener filter {@code H[k] = P_xy[k] / P_xx[k]} per bin, from the PSD of the observation and its
     * cross spectrum with the desired signal (both from {@link Spectra}); 0 where {@code P_xx} is not positive.
     *
     * @param pxx {@code bins} auto-spectrum values
     * @param pxy {@code bins} complex cross-spectrum values, interleaved
     * @param h receives {@code bins} complex responses, interleaved
     */
    public static void noncausal(double[] pxx, double[] pxy, int bins, double[] h) {
        for (int k = 0; k < bins; k++) {
            double d = pxx[k];
            h[2 * k] = d > 0 ? pxy[2 * k] / d : 0.0;
            h[2 * k + 1] = d > 0 ? pxy[2 * k + 1] / d : 0.0;
        }
    }

    /**
     * The FIR taps of a frequency response on {@code plan}'s bins: its inverse transform, rotated by {@code n/2} so the
     * non-causal part comes first (a delay of {@code n/2} samples). Window the taps to shorten them.
     *
     * @param h the {@code n/2 + 1} complex responses, interleaved
     * @param taps receives {@code n} taps
     * @param scratch {@code plan.spectrumLength() + plan.scratchSize()} doubles
     */
    public static void impulseResponse(RealFftd plan, double[] h, double[] taps, double[] scratch) {
        int n = plan.size();
        int sp = plan.spectrumLength();
        System.arraycopy(h, 0, scratch, 0, sp);
        plan.inverse(scratch, 0, scratch, 0, scratch, sp);
        for (int i = 0; i < n; i++) {
            taps[i] = scratch[(i + n - n / 2) % n];
        }
    }

    /** @return the scratch doubles {@link #deconvolve} needs with this plan */
    public static int deconvolveScratchSize(RealFftd plan) {
        return 2 * plan.spectrumLength() + plan.scratchSize();
    }

    /**
     * Wiener deconvolution of a signal blurred by circular convolution with a known kernel:
     * {@code X = conj(H)·Y / (|H|² + nsr)}, where {@code nsr} is the noise-to-signal power ratio (0 is plain inverse
     * filtering, unstable where {@code H} is small; larger values trade sharpness for noise).
     *
     * @param y the {@code n} observed samples
     * @param kernel the blur, {@code kLen <= n} taps (tap 0 is the unshifted one)
     * @param nsr the noise-to-signal ratio, {@code >= 0}, the same for every bin; see the overload for one per bin
     * @param out receives the {@code n} restored samples
     */
    public static void deconvolve(RealFftd plan, double[] y, int yOff, double[] kernel, int kOff, int kLen, double nsr,
            double[] out, int outOff, double[] scratch, int scratchOff) {
        deconvolve(plan, y, yOff, kernel, kOff, kLen, null, nsr, out, outOff, scratch, scratchOff);
    }

    /** Wiener deconvolution with a noise-to-signal ratio per bin ({@code n/2 + 1} values). */
    public static void deconvolve(RealFftd plan, double[] y, int yOff, double[] kernel, int kOff, int kLen,
            double[] nsrPerBin, double[] out, int outOff, double[] scratch, int scratchOff) {
        deconvolve(plan, y, yOff, kernel, kOff, kLen, nsrPerBin, 0.0, out, outOff, scratch, scratchOff);
    }

    private static void deconvolve(RealFftd plan, double[] y, int yOff, double[] kernel, int kOff, int kLen,
            double[] nsrBins, double nsr, double[] out, int outOff, double[] scratch, int scratchOff) {
        int n = plan.size();
        int sp = plan.spectrumLength();
        if (kLen < 1 || kLen > n || scratch.length - scratchOff < deconvolveScratchSize(plan)) {
            throw new IllegalArgumentException("need 1 <= kLen <= n and deconvolveScratchSize doubles of scratch");
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

    /** @return the scratch doubles {@link #deconvolve2d} needs with this plan */
    public static int deconvolve2dScratchSize(RealFft2d plan) {
        return 2 * plan.spectrumLength() + plan.scratchSize();
    }

    /**
     * Wiener deconvolution of an image ({@code rows × cols}, row-major) blurred by circular 2D convolution with a known
     * kernel ({@code kRows × kCols}, its tap {@code (0, 0)} unshifted): {@code X = conj(H)·Y / (|H|² + nsr)}.
     */
    public static void deconvolve2d(RealFft2d plan, double[] y, int yOff, double[] kernel, int kOff, int kRows,
            int kCols, double nsr, double[] out, int outOff, double[] scratch, int scratchOff) {
        int rows = plan.rows();
        int cols = plan.cols();
        int sp = plan.spectrumLength();
        if (kRows < 1 || kCols < 1 || kRows > rows || kCols > cols
                || scratch.length - scratchOff < deconvolve2dScratchSize(plan)) {
            throw new IllegalArgumentException("the kernel must fit the image; scratch deconvolve2dScratchSize");
        }
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
     * The locally adaptive Wiener filter for images: with the mean {@code μ} and variance {@code σ²} of the
     * {@code (2·half + 1)²} window around each pixel (cut at the borders),
     * {@code out = μ + max(σ² - ν², 0) / max(σ², ν²)·(x - μ)}, smoothing flat regions and keeping edges.
     *
     * @param noiseVariance {@code ν²}; 0 or less estimates it as the mean of the local variances
     * @param scratch {@code 2·(rows + 1)·(cols + 1)} doubles (two summed-area tables)
     * @return the noise variance used
     */
    public static double adaptive2d(double[] img, int rows, int cols, int half, double noiseVariance, double[] out,
            double[] scratch) {
        if (rows < 1 || cols < 1 || half < 0 || img.length < rows * cols || out.length < rows * cols
                || scratch.length < 2 * (rows + 1) * (cols + 1)) {
            throw new IllegalArgumentException("need rows·cols pixels and 2·(rows+1)·(cols+1) doubles of scratch");
        }
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
        double nu = noiseVariance;
        if (!(nu > 0)) {
            double sum = 0;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    sum += localVariance(scratch, s1, s2, w, rows, cols, r, c, half);
                }
            }
            nu = sum / ((double) rows * cols);
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int r0 = Math.max(0, r - half);
                int r1 = Math.min(rows, r + half + 1);
                int c0 = Math.max(0, c - half);
                int c1 = Math.min(cols, c + half + 1);
                double cnt = (double) (r1 - r0) * (c1 - c0);
                double mean = box(scratch, s1, w, r0, r1, c0, c1) / cnt;
                double var = Math.max(0, box(scratch, s2, w, r0, r1, c0, c1) / cnt - mean * mean);
                double g = Math.max(var - nu, 0) / Math.max(var, nu);
                out[r * cols + c] = Math.fma(g, img[r * cols + c] - mean, mean);
            }
        }
        return nu;
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
     * Multiplies a half spectrum (interleaved complex, as {@link RealFftd} produces) by real per-bin gains.
     *
     * @param bins the number of complex bins
     */
    public static void apply(double[] spectrum, int off, double[] gain, int bins) {
        for (int k = 0; k < bins; k++) {
            spectrum[off + 2 * k] *= gain[k];
            spectrum[off + 2 * k + 1] *= gain[k];
        }
    }
}
