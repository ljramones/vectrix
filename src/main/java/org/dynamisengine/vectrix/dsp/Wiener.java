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
 * Wiener filtering: the minimum-mean-square-error linear estimate of a desired signal from an observed one.
 * <p>
 * {@link #fir} finds the optimal FIR filter from the observation's autocorrelation and its cross-correlation with
 * the desired signal (the Wiener–Hopf equations, a symmetric Toeplitz system solved by {@link Toeplitz#solve}).
 * {@link #gain} is the frequency-domain form for noise reduction, {@code H = S/(S + N)} per bin from signal and
 * noise power estimates; apply it to STFT frames ({@link Stftd}) and resynthesize. Nothing allocates.
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
