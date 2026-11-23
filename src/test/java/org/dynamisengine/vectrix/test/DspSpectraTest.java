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
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.dsp.Correlation;
import org.dynamisengine.vectrix.dsp.Goertzel;
import org.dynamisengine.vectrix.dsp.Spectra;
import org.dynamisengine.vectrix.dsp.Stftd;
import org.dynamisengine.vectrix.dsp.Stftf;
import org.dynamisengine.vectrix.dsp.Windows;
import org.dynamisengine.vectrix.fft.ComplexFftd;
import org.dynamisengine.vectrix.fft.RealFftd;
import org.junit.jupiter.api.Test;

/** STFT, spectral estimates, correlation and Goertzel (2.5.0). */
class DspSpectraTest {

    private static double[] noise(SplittableRandom r, int n) {
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = r.nextDouble(-1, 1) + r.nextDouble(-1, 1) + r.nextDouble(-1, 1);
        }
        return a;
    }

    /** Σ a·b in double-double. */
    private static double exactDot(double[] a, int ao, double[] b, int bo, int n, int bStep) {
        double hi = 0;
        double lo = 0;
        for (int i = 0; i < n; i++) {
            double p = a[ao + i] * b[bo + bStep * i];
            double pe = java.lang.Math.fma(a[ao + i], b[bo + bStep * i], -p);
            double s = hi + p;
            double bb = s - hi;
            lo += (hi - (s - bb)) + (p - bb) + pe;
            hi = s;
        }
        return hi + lo;
    }

    @Test
    void stftReconstructsExactlyForAnyWindows() {
        SplittableRandom r = new SplittableRandom(1);
        int[][] configs = {{256, 64, 0}, {256, 128, 1}, {256, 100, 2}, {255, 85, 3}, {64, 64, 0}};
        for (int[] c : configs) {
            int n = c[0];
            int hop = c[1];
            double[] wa = new double[n];
            double[] ws = null;
            switch (c[2]) {
                case 0: Windows.hann(wa, 0, n, true); break;
                case 1: Windows.sine(wa, 0, n); ws = wa.clone(); break;
                case 2: Windows.blackman(wa, 0, n, true); ws = new double[n]; Windows.hann(ws, 0, n, true); break;
                default: Windows.kaiser(wa, 0, n, 6, false); break;
            }
            if (c[0] == 64) {
                Windows.tukey(wa, 0, n, 0.5, false);
                java.util.Arrays.fill(wa, 1.0); // rectangular, no overlap
            }
            Stftd p = Stftd.plan(n, hop, wa, 0, ws, 0);
            int len = 40 * hop;
            double[] x = noise(r, len);
            double[] padded = new double[len + 2 * n];
            System.arraycopy(x, 0, padded, n - hop, len); // n - hop leading zeros
            double[] spec = new double[p.spectrumLength()];
            double[] state = new double[p.stateSize()];
            double[] s = new double[p.scratchSize()];
            double[] y = new double[padded.length];
            int frames = (padded.length - n) / hop + 1;
            for (int t = 0; t < frames; t++) {
                p.analyze(padded, t * hop, spec, 0, s, 0);
                p.synthesize(spec, 0, y, t * hop, state, 0, s, 0);
            }
            double err = 0;
            for (int i = 0; i < len; i++) {
                err = java.lang.Math.max(err, java.lang.Math.abs(y[n - hop + i] - x[i]));
            }
            assertTrue(err < 1e-14, "n " + n + " hop " + hop + " window " + c[2] + ": " + err);
            // float twin
            float[] waf = new float[n];
            for (int i = 0; i < n; i++) {
                waf[i] = (float) wa[i];
            }
            float[] wsf = null;
            if (ws != null) {
                wsf = new float[n];
                for (int i = 0; i < n; i++) {
                    wsf[i] = (float) ws[i];
                }
            }
            Stftf pf = Stftf.plan(n, hop, waf, 0, wsf, 0);
            float[] pad = new float[padded.length];
            for (int i = 0; i < pad.length; i++) {
                pad[i] = (float) padded[i];
            }
            float[] specf = new float[pf.spectrumLength()];
            float[] stf = new float[pf.stateSize()];
            float[] sf = new float[pf.scratchSize()];
            float[] yf = new float[pad.length];
            for (int t = 0; t < frames; t++) {
                pf.analyze(pad, t * hop, specf, 0, sf, 0);
                pf.synthesize(specf, 0, yf, t * hop, stf, 0, sf, 0);
            }
            double errF = 0;
            for (int i = 0; i < len; i++) {
                errF = java.lang.Math.max(errF, java.lang.Math.abs(yf[n - hop + i] - pad[n - hop + i]));
            }
            assertTrue(errF < 5e-6, "float: " + errF);
        }
        double[] zero = new double[16];
        zero[0] = 1; // the overlap sum is zero away from phase 0
        assertThrows(IllegalArgumentException.class, () -> Stftd.plan(16, 8, zero, 0, null, 0));
    }

    @Test
    void periodogramSatisfiesParsevalAndWelchFindsTheNoiseDensity() {
        SplittableRandom r = new SplittableRandom(2);
        for (int n : new int[] {64, 63, 1000}) {
            RealFftd plan = RealFftd.plan(n);
            double[] w = new double[n];
            Windows.hann(w, 0, n, true);
            double[] x = noise(r, n);
            double[] psd = new double[n / 2 + 1];
            Spectra.periodogram(plan, w, 0, x, 0, psd, 0, new double[Spectra.scratchSize(plan)], 0);
            double total = 0;
            for (double v : psd) {
                total += v;
            }
            double xw2 = 0;
            double w2 = 0;
            for (int k = 0; k < n; k++) {
                xw2 += x[k] * w[k] * x[k] * w[k];
                w2 += w[k] * w[k];
            }
            assertEquals(xw2 / w2, total / n, 1e-13 * xw2 / w2, "Parseval, n " + n);
        }
        // white noise of variance 1 (uniform on [-√3, √3]) has one-sided density 2 in cycles per sample
        int n = 256;
        RealFftd plan = RealFftd.plan(n);
        double[] w = new double[n];
        Windows.hann(w, 0, n, true);
        int len = 400_000;
        double[] x = new double[len];
        for (int i = 0; i < len; i++) {
            x[i] = r.nextDouble(-java.lang.Math.sqrt(3), java.lang.Math.sqrt(3));
        }
        double[] psd = new double[n / 2 + 1];
        int segs = Spectra.welch(plan, w, 0, n / 2, x, 0, len, psd, 0, new double[Spectra.scratchSize(plan)], 0);
        assertEquals((len - n) / (n / 2) + 1, segs);
        double mean = 0;
        for (int k = 1; k < n / 2; k++) {
            mean += psd[k];
        }
        mean /= n / 2 - 1;
        assertEquals(2.0, mean, 0.02);
    }

    @Test
    void analyticSignalOfACosineIsTheComplexExponential() {
        int n = 1000;
        ComplexFftd plan = ComplexFftd.plan(n);
        double[] x = new double[n];
        for (int k = 0; k < n; k++) {
            x[k] = 0.7 * AccurateMath.cosTurns(37L * k, n) + 0.2 * AccurateMath.cosTurns(201L * k + 50, n);
        }
        double[] z = new double[2 * n];
        Spectra.analytic(plan, x, 0, z, 0, new double[Spectra.scratchSize(plan)], 0);
        for (int k = 0; k < n; k++) {
            double er = 0.7 * AccurateMath.cosTurns(37L * k, n) + 0.2 * AccurateMath.cosTurns(201L * k + 50, n);
            double ei = 0.7 * AccurateMath.sinTurns(37L * k, n) + 0.2 * AccurateMath.sinTurns(201L * k + 50, n);
            assertEquals(er, z[2 * k], 1e-14);
            assertEquals(ei, z[2 * k + 1], 1e-14);
        }
    }

    @Test
    void correlationMatchesTheExactSums() {
        SplittableRandom r = new SplittableRandom(3);
        for (int[] c : new int[][] {{1, 1}, {1, 50}, {7, 3}, {40, 60}, {300, 1000}, {1000, 1000}}) {
            int nx = c[0];
            int ny = c[1];
            double[] x = noise(r, nx);
            double[] y = noise(r, ny);
            Correlation p = Correlation.plan(nx, ny);
            double[] out = new double[p.outputLength()];
            p.correlate(x, 0, y, 0, out, 0, new double[p.scratchSize()], 0);
            double scale = 0;
            for (int k = 0; k < out.length; k++) {
                int lag = k - (nx - 1);
                int i0 = java.lang.Math.max(0, -lag);
                int i1 = java.lang.Math.min(nx - 1, ny - 1 - lag);
                double want = i1 < i0 ? 0 : exactDot(x, i0, y, i0 + lag, i1 - i0 + 1, 1);
                scale = java.lang.Math.max(scale, java.lang.Math.abs(want));
                assertEquals(want, out[k], 1e-13 * java.lang.Math.sqrt(nx * (double) ny) + 1e-15,
                    nx + "x" + ny + " lag " + lag + " fft=" + p.usesFft());
            }
        }
        assertFalse(Correlation.plan(8, 8).usesFft());
        assertTrue(Correlation.plan(1000, 1000).usesFft());
    }

    @Test
    void goertzelMatchesTheExactDftEverywhere() {
        SplittableRandom r = new SplittableRandom(4);
        int n = 20000;
        double[] x = noise(r, n);
        float[] xf = new float[n];
        double[] xd = new double[n];
        for (int i = 0; i < n; i++) {
            xf[i] = (float) x[i];
            xd[i] = xf[i];
        }
        double[] cs = new double[n];
        double[] sn = new double[n];
        for (double f : new double[] {0.0, 1e-6, 3.3e-4, 0.1234567, 0.25, 0.3, 0.4999, 0.5, -0.2, 1.75}) {
            for (int k = 0; k < n; k++) {
                double ph = f * k;
                double a = 2 * java.lang.Math.PI * (ph - java.lang.Math.rint(ph));
                cs[k] = StrictMath.cos(a);
                sn[k] = -StrictMath.sin(a);
            }
            double re = exactDot(x, 0, cs, 0, n, 1);
            double im = exactDot(x, 0, sn, 0, n, 1);
            double[] g = new double[2];
            Goertzel.dft(x, 0, n, f, g);
            // the rounding of f·k itself limits any method to about n·|f|·eps of phase per sample
            double tol = 2e-11 * java.lang.Math.sqrt(n);
            assertEquals(re, g[0], tol, "re at " + f);
            assertEquals(im, g[1], tol, "im at " + f);
            Goertzel.dft(xf, 0, n, f, g);
            assertEquals(exactDot(xd, 0, cs, 0, n, 1), g[0], tol, "float re at " + f);
        }
    }
}
