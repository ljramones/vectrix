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
import org.dynamisengine.vectrix.fft.ComplexFftd;

/**
 * Window functions for spectral analysis and FIR design.
 * <p>
 * Each window writes {@code n} values to {@code dest[off .. off + n)}. A <i>symmetric</i> window
 * ({@code periodic = false}) has {@code w[k] == w[n-1-k]} exactly and suits filter design; a <i>periodic</i> one is
 * the first {@code n} values of the symmetric window of length {@code n + 1}, which tiles without overlap error and
 * suits spectral analysis (STFT, Welch). The cosine terms use exact argument reduction
 * ({@link AccurateMath#cosTurns}), so symmetric windows are bit-exactly symmetric and the values are within a few ulps.
 * The float overloads compute in double and round once.
 *
 * @since 2.5.0
 */
public final class Windows {

    private static final double[] HANN = {0.5, 0.5};
    private static final double[] HAMMING = {0.54, 0.46};
    private static final double[] BLACKMAN = {0.42, 0.5, 0.08};
    private static final double[] BLACKMAN_HARRIS = {0.35875, 0.48829, 0.14128, 0.01168};
    private static final double[] NUTTALL = {0.3635819, 0.4891775, 0.1365995, 0.0106411};
    private static final double[] FLAT_TOP = {0.21557895, 0.41663158, 0.277263158, 0.083578947, 0.006947368};

    private Windows() {
    }

    /** Hann: {@code 0.5 - 0.5·cos(2πk/D)}; 31.5 dB side lobes, 1.5 bins noise bandwidth. */
    public static void hann(double[] dest, int off, int n, boolean periodic) {
        cosineSum(HANN, dest, null, off, n, periodic);
    }

    /** Hann, float. */
    public static void hann(float[] dest, int off, int n, boolean periodic) {
        cosineSum(HANN, null, dest, off, n, periodic);
    }

    /** Hamming: {@code 0.54 - 0.46·cos(2πk/D)}; 42.7 dB side lobes. */
    public static void hamming(double[] dest, int off, int n, boolean periodic) {
        cosineSum(HAMMING, dest, null, off, n, periodic);
    }

    /** Hamming, float. */
    public static void hamming(float[] dest, int off, int n, boolean periodic) {
        cosineSum(HAMMING, null, dest, off, n, periodic);
    }

    /** Blackman (0.42, 0.5, 0.08); 58 dB side lobes. */
    public static void blackman(double[] dest, int off, int n, boolean periodic) {
        cosineSum(BLACKMAN, dest, null, off, n, periodic);
    }

    /** Blackman, float. */
    public static void blackman(float[] dest, int off, int n, boolean periodic) {
        cosineSum(BLACKMAN, null, dest, off, n, periodic);
    }

    /** 4-term Blackman–Harris; 92 dB side lobes. */
    public static void blackmanHarris(double[] dest, int off, int n, boolean periodic) {
        cosineSum(BLACKMAN_HARRIS, dest, null, off, n, periodic);
    }

    /** 4-term Blackman–Harris, float. */
    public static void blackmanHarris(float[] dest, int off, int n, boolean periodic) {
        cosineSum(BLACKMAN_HARRIS, null, dest, off, n, periodic);
    }

    /** Blackman–Nuttall; 98 dB side lobes. */
    public static void nuttall(double[] dest, int off, int n, boolean periodic) {
        cosineSum(NUTTALL, dest, null, off, n, periodic);
    }

    /** Blackman–Nuttall, float. */
    public static void nuttall(float[] dest, int off, int n, boolean periodic) {
        cosineSum(NUTTALL, null, dest, off, n, periodic);
    }

    /** Flat top (the SRS coefficients): amplitude error below 0.01 dB for tones between bins. */
    public static void flatTop(double[] dest, int off, int n, boolean periodic) {
        cosineSum(FLAT_TOP, dest, null, off, n, periodic);
    }

    /** Flat top, float. */
    public static void flatTop(float[] dest, int off, int n, boolean periodic) {
        cosineSum(FLAT_TOP, null, dest, off, n, periodic);
    }

    /**
     * Sine window {@code sin(π(k + 0.5)/n)}: the MDCT analysis/synthesis window, power complementary over 50 %
     * overlap. It is always periodic in this sense.
     */
    public static void sine(double[] dest, int off, int n) {
        check(dest == null ? -1 : dest.length, off, n);
        for (int k = 0; k < n; k++) {
            dest[off + k] = AccurateMath.sinTurns(2L * k + 1, 4L * n);
        }
    }

    /** Sine window, float. */
    public static void sine(float[] dest, int off, int n) {
        check(dest == null ? -1 : dest.length, off, n);
        for (int k = 0; k < n; k++) {
            dest[off + k] = (float) AccurateMath.sinTurns(2L * k + 1, 4L * n);
        }
    }

    /**
     * Kaiser: {@code I0(β·sqrt(1 - (2k/D - 1)²)) / I0(β)}. {@code β} trades main-lobe width for side-lobe level
     * (β = 0 is rectangular; see {@link FirDesign#kaiserBeta}).
     *
     * @param beta the shape, {@code >= 0}
     */
    public static void kaiser(double[] dest, int off, int n, double beta, boolean periodic) {
        fill(KAISER, beta, dest, null, off, n, periodic);
    }

    /** Kaiser, float. */
    public static void kaiser(float[] dest, int off, int n, double beta, boolean periodic) {
        fill(KAISER, beta, null, dest, off, n, periodic);
    }

    /**
     * Tukey (tapered cosine): flat in the middle, with Hann tapers over a fraction {@code alpha} of the window
     * ({@code alpha = 0} is rectangular, {@code 1} is Hann).
     *
     * @param alpha the tapered fraction, in [0, 1]
     */
    public static void tukey(double[] dest, int off, int n, double alpha, boolean periodic) {
        fill(TUKEY, alpha, dest, null, off, n, periodic);
    }

    /** Tukey, float. */
    public static void tukey(float[] dest, int off, int n, double alpha, boolean periodic) {
        fill(TUKEY, alpha, null, dest, off, n, periodic);
    }

    /**
     * Gaussian: {@code exp(-((k - D/2)/sigma)²/2)}.
     *
     * @param sigma the standard deviation in samples, {@code > 0}
     */
    public static void gaussian(double[] dest, int off, int n, double sigma, boolean periodic) {
        fill(GAUSSIAN, sigma, dest, null, off, n, periodic);
    }

    /** Gaussian, float. */
    public static void gaussian(float[] dest, int off, int n, double sigma, boolean periodic) {
        fill(GAUSSIAN, sigma, null, dest, off, n, periodic);
    }

    /**
     * Dolph–Chebyshev: the narrowest main lobe for equiripple side lobes {@code attenuationDb} below the peak. It
     * is computed through a DFT of the Chebyshev polynomial (the usual method), which allocates; the side lobes
     * are within a small fraction of a dB of the target up to about 200 dB.
     *
     * @param attenuationDb the side-lobe attenuation in dB, {@code > 0}
     */
    public static void chebyshev(double[] dest, int off, int n, double attenuationDb, boolean periodic) {
        check(dest == null ? -1 : dest.length, off, n);
        double[] w = chebyshev(periodic ? n + 1 : n, attenuationDb);
        System.arraycopy(w, 0, dest, off, n);
    }

    /** Dolph–Chebyshev, float. */
    public static void chebyshev(float[] dest, int off, int n, double attenuationDb, boolean periodic) {
        check(dest == null ? -1 : dest.length, off, n);
        double[] w = chebyshev(periodic ? n + 1 : n, attenuationDb);
        for (int k = 0; k < n; k++) {
            dest[off + k] = (float) w[k];
        }
    }

    /**
     * The coherent gain {@code Σw / n}: a windowed tone's amplitude is scaled by it.
     *
     * @return the coherent gain
     */
    public static double coherentGain(double[] w, int off, int n) {
        check(w == null ? -1 : w.length, off, n);
        double s = 0.0;
        for (int k = 0; k < n; k++) {
            s += w[off + k];
        }
        return s / n;
    }

    /**
     * The equivalent noise bandwidth in bins, {@code n·Σw² / (Σw)²}: the window's noise power relative to a
     * rectangular window (1.5 for Hann).
     *
     * @return the noise bandwidth in bins
     */
    public static double equivalentNoiseBandwidth(double[] w, int off, int n) {
        check(w == null ? -1 : w.length, off, n);
        double s = 0.0;
        double s2 = 0.0;
        for (int k = 0; k < n; k++) {
            double v = w[off + k];
            s += v;
            s2 += v * v;
        }
        return n * s2 / (s * s);
    }

    private static final int KAISER = 0;
    private static final int TUKEY = 1;
    private static final int GAUSSIAN = 2;

    private static void check(int length, int off, int n) {
        if (length < 0 || n < 1 || off < 0 || off > length - n) {
            throw new IllegalArgumentException("dest must hold " + n + " values from offset " + off);
        }
    }

    private static void cosineSum(double[] a, double[] dest, float[] destF, int off, int n, boolean periodic) {
        check(dest != null ? dest.length : destF == null ? -1 : destF.length, off, n);
        long d = periodic ? n : n - 1;
        for (int k = 0; k < n; k++) {
            double v;
            if (d == 0) {
                v = 1.0;
            } else {
                v = a[0];
                for (int j = 1; j < a.length; j++) {
                    double c = a[j] * AccurateMath.cosTurns((long) j * k, d);
                    v = (j & 1) == 1 ? v - c : v + c;
                }
            }
            put(dest, destF, off + k, v);
        }
    }

    private static void fill(int kind, double p, double[] dest, float[] destF, int off, int n, boolean periodic) {
        check(dest != null ? dest.length : destF == null ? -1 : destF.length, off, n);
        long d = periodic ? n : n - 1;
        double i0b = kind == KAISER ? besselI0(p) : 0.0;
        if (kind == KAISER && !(p >= 0)) {
            throw new IllegalArgumentException("beta must be >= 0: " + p);
        }
        if (kind == TUKEY && !(p >= 0 && p <= 1)) {
            throw new IllegalArgumentException("alpha must be in [0, 1]: " + p);
        }
        if (kind == GAUSSIAN && !(p > 0)) {
            throw new IllegalArgumentException("sigma must be > 0: " + p);
        }
        for (int k = 0; k < n; k++) {
            double v;
            if (d == 0) {
                v = 1.0;
            } else if (kind == KAISER) {
                // 1 - (2k/D - 1)² = 4k(D - k)/D², symmetric in k and D - k
                double r = java.lang.Math.sqrt(4.0 * k * (d - k)) / d;
                v = besselI0(p * r) / i0b;
            } else if (kind == TUKEY) {
                long x = java.lang.Math.min(k, d - k); // distance to the nearer end
                double edge = 0.5 * p * d;
                v = x >= edge ? 1.0 : 0.5 - 0.5 * AccurateMath.cos(java.lang.Math.PI * x / edge);
            } else {
                double t = (2.0 * k - d) / (2.0 * p); // (k - D/2)/sigma, symmetric up to sign
                v = AccurateMath.exp(-0.5 * t * t);
            }
            put(dest, destF, off + k, v);
        }
    }

    private static void put(double[] dest, float[] destF, int i, double v) {
        if (dest != null) {
            dest[i] = v;
        } else {
            destF[i] = (float) v;
        }
    }

    /** The modified Bessel function {@code I0(x)} by its power series (all terms positive: no cancellation). */
    static double besselI0(double x) {
        double q = 0.25 * x * x;
        double term = 1.0;
        double sum = 1.0;
        for (int m = 1; m < 1000; m++) {
            term *= q / ((double) m * m);
            sum += term;
            if (term < 1e-17 * sum) {
                break;
            }
        }
        return sum;
    }

    /** The symmetric Dolph–Chebyshev window of length {@code m}, peak 1. */
    private static double[] chebyshev(int m, double attenuationDb) {
        if (!(attenuationDb > 0)) {
            throw new IllegalArgumentException("attenuation must be > 0 dB: " + attenuationDb);
        }
        double[] w = new double[m];
        if (m == 1) {
            w[0] = 1.0;
            return w;
        }
        double order = m - 1.0;
        double r = AccurateMath.pow(10.0, attenuationDb / 20.0);
        double beta = cosh(acosh(r) / order);
        double[] p = new double[2 * m];
        for (int k = 0; k < m; k++) {
            double x = beta * AccurateMath.cosTurns(k, 2L * m); // beta·cos(πk/m)
            double v;
            if (x > 1) {
                v = cosh(order * acosh(x));
            } else if (x < -1) {
                v = ((m & 1) == 1 ? 1 : -1) * cosh(order * acosh(-x));
            } else {
                v = AccurateMath.cos(order * AccurateMath.acos(x));
            }
            if ((m & 1) == 1) {
                p[2 * k] = v;
            } else { // times exp(iπk/m)
                p[2 * k] = v * AccurateMath.cosTurns(k, 2L * m);
                p[2 * k + 1] = v * AccurateMath.sinTurns(k, 2L * m);
            }
        }
        ComplexFftd plan = ComplexFftd.plan(m);
        plan.forward(p, 0, new double[plan.scratchSize()], 0);
        if ((m & 1) == 1) {
            int h = (m + 1) / 2;
            for (int i = 0; i < h; i++) {
                w[h - 1 + i] = p[2 * i];
                w[h - 1 - i] = p[2 * i];
            }
        } else {
            int h = m / 2 + 1;
            for (int i = 1; i < h; i++) {
                w[h - 2 + i] = p[2 * i];
                w[h - 1 - i] = p[2 * i];
            }
        }
        double max = 0.0;
        for (double v : w) {
            max = java.lang.Math.max(max, v);
        }
        for (int i = 0; i < m; i++) {
            w[i] /= max;
        }
        return w;
    }

    private static double cosh(double x) {
        double e = AccurateMath.exp(x);
        return 0.5 * (e + 1.0 / e);
    }

    private static double acosh(double x) {
        return AccurateMath.log(x + java.lang.Math.sqrt((x - 1.0) * (x + 1.0)));
    }
}
