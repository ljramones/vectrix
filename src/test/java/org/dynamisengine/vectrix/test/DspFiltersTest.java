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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.dsp.Biquad;
import org.dynamisengine.vectrix.dsp.BiquadDesign;
import org.dynamisengine.vectrix.dsp.FirDesign;
import org.dynamisengine.vectrix.dsp.Windows;
import org.junit.jupiter.api.Test;

/** Windows, FIR design and biquads (2.5.0), each checked against a property derived independently. */
class DspFiltersTest {

    @Test
    void turnsAreExactlyReduced() {
        for (long d : new long[] {1, 2, 3, 4, 7, 8, 12, 1000, 1 << 20, 1_000_003}) {
            for (long e = -2 * d; e <= 2 * d; e += java.lang.Math.max(1, d / 37)) {
                assertEquals(AccurateMath.cosTurns(e, d), AccurateMath.cosTurns(d - e, d), 0.0);
                assertEquals(AccurateMath.sinTurns(e, d), -AccurateMath.sinTurns(d - e, d) + 0.0, 0.0);
                double a = 2 * java.lang.Math.PI * java.lang.Math.floorMod(e, d) / d;
                assertEquals(StrictMath.cos(a), AccurateMath.cosTurns(e, d), 1e-15);
            }
            if (d % 4 == 0) {
                assertEquals(0.0, AccurateMath.cosTurns(d / 4, d), 0.0);
                assertEquals(-1.0, AccurateMath.cosTurns(d / 2, d), 0.0);
                assertEquals(-1.0, AccurateMath.sinTurns(3 * d / 4, d), 0.0);
            }
        }
    }

    @Test
    void windowsHaveTheirDefiningProperties() {
        double[] w = new double[5];
        Windows.hann(w, 0, 5, false);
        assertArrayEquals(new double[] {0, 0.5, 1, 0.5, 0}, w, 1e-16);
        for (int n : new int[] {1, 2, 7, 64, 1001}) {
            double[] sym = new double[n];
            double[] per = new double[n];
            double[] longer = new double[n + 1];
            for (int kind = 0; kind < 9; kind++) {
                fill(kind, sym, false);
                fill(kind, per, true);
                double[] l = new double[n + 1];
                fill(kind, l, false);
                System.arraycopy(l, 0, longer, 0, n + 1);
                for (int k = 0; k < n; k++) {
                    assertEquals(sym[k], sym[n - 1 - k], 0.0, "symmetry, kind " + kind + " n " + n);
                    assertEquals(longer[k], per[k], 0.0, "periodic = symmetric(n + 1), kind " + kind);
                }
            }
        }
        // known gains: Hann (periodic) coherent gain 0.5 and noise bandwidth 1.5 bins
        double[] h = new double[1024];
        Windows.hann(h, 0, 1024, true);
        assertEquals(0.5, Windows.coherentGain(h, 0, 1024), 1e-15);
        assertEquals(1.5, Windows.equivalentNoiseBandwidth(h, 0, 1024), 1e-12);
        double[] bh = new double[1024];
        Windows.blackmanHarris(bh, 0, 1024, true);
        assertEquals(2.0044, Windows.equivalentNoiseBandwidth(bh, 0, 1024), 1e-4);
        // Kaiser with beta 0 is rectangular; Tukey 0 is rectangular and Tukey 1 is Hann
        double[] k = new double[33];
        Windows.kaiser(k, 0, 33, 0.0, false);
        double[] t = new double[33];
        Windows.tukey(t, 0, 33, 1.0, false);
        double[] hn = new double[33];
        Windows.hann(hn, 0, 33, false);
        for (int i = 0; i < 33; i++) {
            assertEquals(1.0, k[i], 0.0);
            assertEquals(hn[i], t[i], 1e-15);
        }
        Windows.tukey(t, 0, 33, 0.0, false);
        for (double v : t) {
            assertEquals(1.0, v, 0.0);
        }
        // the sine window is power complementary over 50 % overlap (Princen–Bradley)
        double[] s = new double[64];
        Windows.sine(s, 0, 64);
        for (int i = 0; i < 32; i++) {
            assertEquals(1.0, s[i] * s[i] + s[i + 32] * s[i + 32], 1e-15);
        }
        // float overloads round the double values once
        float[] f = new float[64];
        Windows.blackman(f, 0, 64, true);
        double[] d = new double[64];
        Windows.blackman(d, 0, 64, true);
        for (int i = 0; i < 64; i++) {
            assertEquals((float) d[i], f[i], 0f);
        }
    }

    private static void fill(int kind, double[] w, boolean periodic) {
        int n = w.length;
        switch (kind) {
            case 0: Windows.hann(w, 0, n, periodic); break;
            case 1: Windows.hamming(w, 0, n, periodic); break;
            case 2: Windows.blackman(w, 0, n, periodic); break;
            case 3: Windows.blackmanHarris(w, 0, n, periodic); break;
            case 4: Windows.nuttall(w, 0, n, periodic); break;
            case 5: Windows.flatTop(w, 0, n, periodic); break;
            case 6: Windows.kaiser(w, 0, n, 8.6, periodic); break;
            case 7: Windows.tukey(w, 0, n, 0.4, periodic); break;
            default: Windows.gaussian(w, 0, n, 7.5, periodic); break;
        }
    }

    /** The largest side lobe in dB below the peak, from a dense DTFT beyond the first null. */
    private static double sideLobeDb(double[] w) {
        int n = w.length;
        int grid = 64 * n;
        double[] mag = new double[grid / 2];
        for (int i = 0; i < grid / 2; i++) {
            mag[i] = FirDesign.magnitude(w, 0, n, (double) i / grid);
        }
        int first = 1;
        while (first < mag.length - 1 && mag[first + 1] < mag[first]) {
            first++;
        }
        double side = 0;
        for (int i = first; i < mag.length; i++) {
            side = java.lang.Math.max(side, mag[i]);
        }
        return 20 * java.lang.Math.log10(mag[0] / side);
    }

    @Test
    void windowSideLobesMatchTheirSpecifications() {
        double[] w = new double[63];
        Windows.hann(w, 0, 63, false);
        assertEquals(31.5, sideLobeDb(w), 0.5);
        Windows.blackmanHarris(w, 0, 63, false);
        assertTrue(sideLobeDb(w) > 91.5, "Blackman-Harris " + sideLobeDb(w));
        for (double at : new double[] {40, 80, 120}) {
            for (int n : new int[] {31, 32, 63}) {
                double[] c = new double[n];
                Windows.chebyshev(c, 0, n, at, false);
                assertEquals(at, sideLobeDb(c), 0.05, "Chebyshev " + at + " dB, n " + n);
                assertEquals(1.0, Arrays.stream(c).max().getAsDouble(), 0.0);
            }
        }
    }

    @Test
    void firDesignsMeetTheirSpecifications() {
        // 80 dB stop band, transition 0.02 cycles/sample, cutoff 0.2: passband ripple below Kaiser's bound
        double atten = 80;
        int n = FirDesign.kaiserTaps(atten, 0.02);
        n |= 1;
        double[] win = new double[n];
        Windows.kaiser(win, 0, n, FirDesign.kaiserBeta(atten), false);
        double[] h = new double[n];
        FirDesign.lowpass(h, 0, n, 0.2, win, 0);
        assertEquals(1.0, Arrays.stream(h).sum(), 1e-15, "unit DC gain");
        for (int k = 0; k < n; k++) {
            assertEquals(h[k], h[n - 1 - k], 0.0, "linear phase");
        }
        double ripple = java.lang.Math.pow(10, -atten / 20);
        for (double f = 0; f <= 0.19; f += 0.001) {
            assertEquals(1.0, FirDesign.magnitude(h, 0, n, f), 1.5 * ripple, "pass band at " + f);
        }
        for (double f = 0.21; f <= 0.5; f += 0.001) {
            assertTrue(FirDesign.magnitude(h, 0, n, f) < 1.5 * ripple, "stop band at " + f);
        }
        double[] hp = new double[n];
        FirDesign.highpass(hp, 0, n, 0.2, win, 0);
        assertEquals(1.0, FirDesign.magnitude(hp, 0, n, 0.5), 1e-14);
        assertTrue(FirDesign.magnitude(hp, 0, n, 0.1) < 1.5 * ripple);
        double[] bp = new double[n];
        FirDesign.bandpass(bp, 0, n, 0.1, 0.3, win, 0);
        assertEquals(1.0, FirDesign.magnitude(bp, 0, n, 0.2), 1e-14);
        assertTrue(FirDesign.magnitude(bp, 0, n, 0.02) < 1.5 * ripple);
        assertTrue(FirDesign.magnitude(bp, 0, n, 0.4) < 1.5 * ripple);
        double[] bs = new double[n];
        FirDesign.bandstop(bs, 0, n, 0.1, 0.3, win, 0);
        assertEquals(1.0, FirDesign.magnitude(bs, 0, n, 0.0), 1e-14);
        assertTrue(FirDesign.magnitude(bs, 0, n, 0.2) < 1.5 * ripple);
        assertThrows(IllegalArgumentException.class, () -> FirDesign.highpass(new double[8], 0, 8, 0.2, null, 0));
        assertEquals(0.1102 * (80 - 8.7), FirDesign.kaiserBeta(80), 0.0);
        assertEquals(0.0, FirDesign.kaiserBeta(20), 0.0);
    }

    @Test
    void biquadDesignsHaveTheirStatedGains() {
        double[] c = new double[BiquadDesign.STRIDE];
        double q = 0.9;
        for (double f0 : new double[] {1e-4, 0.01, 0.1, 0.3, 0.45}) {
            // direct-form coefficients lose about eps/(2π f0)² near DC: that, not the design, bounds the error
            double tol = 1e-13 + 1e-14 / java.lang.Math.pow(2 * java.lang.Math.PI * f0, 2);
            BiquadDesign.lowpass(c, 0, f0, q);
            assertEquals(1.0, Biquad.magnitude(c, 0, 1, 0.0), tol, "LP DC " + f0);
            assertEquals(q, Biquad.magnitude(c, 0, 1, f0), tol, "LP |H(f0)| = Q at " + f0);
            BiquadDesign.highpass(c, 0, f0, q);
            assertEquals(1.0, Biquad.magnitude(c, 0, 1, 0.5), tol, "HP Nyquist " + f0);
            assertEquals(q, Biquad.magnitude(c, 0, 1, f0), tol);
            BiquadDesign.bandpass(c, 0, f0, q);
            assertEquals(1.0, Biquad.magnitude(c, 0, 1, f0), tol);
            BiquadDesign.notch(c, 0, f0, q);
            assertEquals(0.0, Biquad.magnitude(c, 0, 1, f0), tol);
            BiquadDesign.allpass(c, 0, f0, q);
            for (double f = 0; f <= 0.5; f += 0.05) {
                assertEquals(1.0, Biquad.magnitude(c, 0, 1, f), tol, "all-pass");
            }
            BiquadDesign.peaking(c, 0, f0, q, 6.0);
            assertEquals(java.lang.Math.pow(10, 6.0 / 20), Biquad.magnitude(c, 0, 1, f0), tol);
            BiquadDesign.lowShelf(c, 0, f0, q, -9.0);
            assertEquals(java.lang.Math.pow(10, -9.0 / 20), Biquad.magnitude(c, 0, 1, 0.0), tol);
            assertEquals(1.0, Biquad.magnitude(c, 0, 1, 0.5), tol);
            BiquadDesign.highShelf(c, 0, f0, q, 4.0);
            assertEquals(java.lang.Math.pow(10, 4.0 / 20), Biquad.magnitude(c, 0, 1, 0.5), tol);
            assertEquals(1.0, Biquad.magnitude(c, 0, 1, 0.0), tol);
        }
        // Butterworth: |H|² = 1 / (1 + (tan(πf)/tan(πf0))^(2N)) exactly, for every order
        for (int order = 1; order <= 9; order++) {
            double f0 = 0.07;
            double[] bw = new double[BiquadDesign.STRIDE * BiquadDesign.butterworthSections(order)];
            int sections = BiquadDesign.butterworthLowpass(bw, 0, order, f0);
            double[] hw = new double[bw.length];
            BiquadDesign.butterworthHighpass(hw, 0, order, f0);
            for (double f = 0.005; f < 0.5; f += 0.01) {
                double r = java.lang.Math.tan(java.lang.Math.PI * f) / java.lang.Math.tan(java.lang.Math.PI * f0);
                double lp = 1 / java.lang.Math.sqrt(1 + java.lang.Math.pow(r, 2 * order));
                double hpw = 1 / java.lang.Math.sqrt(1 + java.lang.Math.pow(1 / r, 2 * order));
                assertEquals(lp, Biquad.magnitude(bw, 0, sections, f), 1e-12, "order " + order + " at " + f);
                assertEquals(hpw, Biquad.magnitude(hw, 0, sections, f), 1e-12, "HP order " + order + " at " + f);
            }
        }
    }

    @Test
    void processingMatchesTheResponseAndIsChunkingInvariant() {
        double[] c = new double[BiquadDesign.STRIDE * 3];
        int sections = BiquadDesign.butterworthLowpass(c, 0, 5, 0.05);
        // a steady sinusoid comes out scaled by |H| and shifted by arg H
        double f = 0.03;
        int len = 20000;
        double[] x = new double[len];
        for (int i = 0; i < len; i++) {
            x[i] = AccurateMath.sinTurns((long) (f * 1e6) * i, 1_000_000L);
        }
        double[] y = new double[len];
        double[] st = new double[Biquad.STATE_STRIDE * sections];
        Biquad.process(c, 0, sections, st, 0, x, 0, y, 0, len);
        double[] r = new double[2];
        Biquad.response(c, 0, sections, f, r);
        double mag = java.lang.Math.hypot(r[0], r[1]);
        double ph = java.lang.Math.atan2(r[1], r[0]);
        for (int i = len - 200; i < len; i++) {
            assertEquals(mag * java.lang.Math.sin(2 * java.lang.Math.PI * f * i + ph), y[i], 1e-9);
        }
        // random chunks, in place, give the same bits
        double[] z = x.clone();
        double[] st2 = new double[st.length];
        SplittableRandom rnd = new SplittableRandom(3);
        for (int p = 0; p < len;) {
            int cnt = java.lang.Math.min(len - p, rnd.nextInt(1, 300));
            Biquad.process(c, 0, sections, st2, 0, z, p, z, p, cnt);
            p += cnt;
        }
        assertArrayEquals(y, z, 0.0);
        // the float path computes in double: it equals the double path rounded
        float[] xf = new float[len];
        for (int i = 0; i < len; i++) {
            xf[i] = (float) x[i];
        }
        double[] xd = new double[len];
        for (int i = 0; i < len; i++) {
            xd[i] = xf[i];
        }
        double[] yd = new double[len];
        Biquad.process(c, 0, sections, new double[st.length], 0, xd, 0, yd, 0, len);
        float[] yf = new float[len];
        Biquad.process(c, 0, sections, new double[st.length], 0, xf, 0, yf, 0, len);
        for (int i = 0; i < len; i++) {
            assertEquals((float) yd[i], yf[i], 0f);
        }
    }
}
