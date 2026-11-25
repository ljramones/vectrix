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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.dsp.FirDesign;
import org.dynamisengine.vectrix.dsp.Kalman;
import org.dynamisengine.vectrix.dsp.Wavelet;
import org.dynamisengine.vectrix.dsp.Windows;
import org.junit.jupiter.api.Test;

/** Parks–McClellan, wavelets and the Kalman filter (2.5.0). */
class DspExtrasTest {

    private static double maxError(double[] h, double lo, double hi, double want) {
        double m = 0;
        for (int i = 0; i <= 4000; i++) {
            double f = lo + (hi - lo) * i / 4000;
            m = java.lang.Math.max(m, java.lang.Math.abs(FirDesign.magnitude(h, 0, h.length, f) - want));
        }
        return m;
    }

    @Test
    void remezIsEquirippleAndBeatsWindows() {
        int n = 61;
        double[] h = new double[n];
        double d = FirDesign.remez(h, 0, n, new double[] {0, 0.2, 0.25, 0.5}, new double[] {1, 0}, new double[] {1, 1});
        for (int k = 0; k < n; k++) {
            assertEquals(h[k], h[n - 1 - k], 1e-15, "linear phase");
        }
        assertEquals(d, maxError(h, 0, 0.2, 1), 0.01 * d, "pass band ripple is delta");
        assertEquals(d, maxError(h, 0.25, 0.5, 0), 0.01 * d, "stop band ripple is delta");
        // a Kaiser window design of the same length and cutoff has a larger worst error over the same bands
        double[] k = new double[n];
        double[] w = new double[n];
        Windows.kaiser(w, 0, n, FirDesign.kaiserBeta(-20 * java.lang.Math.log10(d)), false);
        FirDesign.lowpass(k, 0, n, 0.225, w, 0);
        assertTrue(java.lang.Math.max(maxError(k, 0, 0.2, 1), maxError(k, 0.25, 0.5, 0)) > 1.3 * d);
        // weights: the stop band weighted 10 has a tenth of the pass band's ripple
        double dw = FirDesign.remez(h, 0, n, new double[] {0, 0.2, 0.25, 0.5}, new double[] {1, 0},
            new double[] {1, 10});
        assertEquals(dw, maxError(h, 0, 0.2, 1), 0.01 * dw);
        assertEquals(dw / 10, maxError(h, 0.25, 0.5, 0), 0.01 * dw / 10);
        // a band-pass with three bands
        double[] b = new double[81];
        double db = FirDesign.remez(b, 0, 81, new double[] {0, 0.1, 0.15, 0.3, 0.35, 0.5}, new double[] {0, 1, 0},
            new double[] {1, 1, 1});
        assertEquals(db, maxError(b, 0.15, 0.3, 1), 0.01 * db);
        assertEquals(db, maxError(b, 0, 0.1, 0), 0.01 * db);
        assertEquals(db, maxError(b, 0.35, 0.5, 0), 0.01 * db);
    }

    @Test
    void daubechiesFiltersAreOrthonormalAndReconstructExactly() {
        assertEquals((1 + java.lang.Math.sqrt(3)) / (4 * java.lang.Math.sqrt(2)), Wavelet.daubechies(2).lowpass()[0], 1e-15);
        SplittableRandom r = new SplittableRandom(1);
        for (int p = 1; p <= 10; p++) {
            Wavelet wv = Wavelet.daubechies(p);
            double[] h = wv.lowpass();
            double[] g = wv.highpass();
            assertEquals(2 * p, h.length);
            double s = 0;
            for (double v : h) {
                s += v;
            }
            assertEquals(java.lang.Math.sqrt(2), s, 1e-14, "db" + p + " sum");
            for (int m = 0; 2 * m < h.length; m++) {
                double o = 0;
                for (int k = 0; k + 2 * m < h.length; k++) {
                    o += h[k] * h[k + 2 * m];
                }
                assertEquals(m == 0 ? 1 : 0, o, 2e-15, "db" + p + " shift " + m);
            }
            for (int q = 0; q < p; q++) {
                double mm = 0;
                for (int k = 0; k < g.length; k++) {
                    mm += java.lang.Math.pow(k, q) * g[k];
                }
                assertEquals(0, mm / java.lang.Math.pow(g.length, q), 1e-13, "db" + p + " moment " + q);
            }
            // single and multilevel round trips, and energy conservation
            int n = 256;
            double[] x = new double[n];
            double e = 0;
            for (int i = 0; i < n; i++) {
                x[i] = r.nextDouble(-1, 1);
                e += x[i] * x[i];
            }
            double[] c = new double[n];
            double[] back = new double[n];
            wv.forward(x, 0, n, c, 0);
            wv.inverse(c, 0, n, back, 0);
            double ec = 0;
            for (int i = 0; i < n; i++) {
                assertEquals(x[i], back[i], 1e-14);
                ec += c[i] * c[i];
            }
            assertEquals(e, ec, 1e-12 * e, "Parseval");
            double[] scratch = new double[n];
            wv.forward(x, 0, n, 4, c, 0, scratch, 0);
            wv.inverse(c, 0, n, 4, back, 0, scratch, 0);
            for (int i = 0; i < n; i++) {
                assertEquals(x[i], back[i], 1e-13, "multilevel db" + p);
            }
        }
    }

    @Test
    void kalmanFilterReachesTheRiccatiSteadyStateAndMatchesTheTextbook() {
        // random walk: posterior variance converges to (-q + sqrt(q² + 4qr)) / 2
        double q = 0.3;
        double rr = 2.0;
        double[] x = {0};
        double[] p = {10};
        double[] scratch = new double[Kalman.scratchSize(1, 1)];
        for (int k = 0; k < 200; k++) {
            Kalman.predict(1, x, p, new double[] {1}, new double[] {q}, scratch);
            Kalman.update(1, 1, x, p, new double[] {1}, new double[] {rr}, new double[] {k % 3}, scratch);
        }
        assertEquals((-q + java.lang.Math.sqrt(q * q + 4 * q * rr)) / 2, p[0], 1e-12);
        // constant velocity, position measured: compare with the textbook form using an explicit inverse
        double dt = 0.1;
        double[] f = {1, dt, 0, 1};
        double[] qq = {1e-3, 2e-3, 2e-3, 4e-2};
        double[] h = {1, 0};
        double[] rm = {0.25};
        double[] xs = {0, 1};
        double[] ps = {1, 0, 0, 1};
        double[] xt = xs.clone();
        double[] pt = ps.clone();
        double[] sc = new double[Kalman.scratchSize(2, 1)];
        SplittableRandom rnd = new SplittableRandom(2);
        for (int k = 0; k < 50; k++) {
            double z = 0.1 * k + rnd.nextGaussian() * 0.5;
            Kalman.predict(2, xs, ps, f, qq, sc);
            double maha = Kalman.update(2, 1, xs, ps, h, rm, new double[] {z}, sc);
            // textbook: x = Fx, P = FPFᵀ + Q, S = HPHᵀ + R, K = PHᵀ/S, x += K(z - Hx), P = (I - KH)P
            double x0 = xt[0] + dt * xt[1];
            double x1 = xt[1];
            double p00 = pt[0] + dt * (pt[2] + pt[1]) + dt * dt * pt[3] + qq[0];
            double p01 = pt[1] + dt * pt[3] + qq[1];
            double p10 = pt[2] + dt * pt[3] + qq[2];
            double p11 = pt[3] + qq[3];
            double s = p00 + rm[0];
            double k0 = p00 / s;
            double k1 = p10 / s;
            double y = z - x0;
            assertEquals(y * y / s, maha, 1e-9 * (1 + maha), "Mahalanobis");
            xt = new double[] {x0 + k0 * y, x1 + k1 * y};
            pt = new double[] {(1 - k0) * p00, (1 - k0) * p01, p10 - k1 * p00, p11 - k1 * p01};
            for (int i = 0; i < 2; i++) {
                assertEquals(xt[i], xs[i], 1e-10, "state " + i + " step " + k);
            }
            for (int i = 0; i < 4; i++) {
                assertEquals(pt[i], ps[i], 1e-10, "covariance " + i + " step " + k);
            }
            assertEquals(ps[1], ps[2], 0.0, "symmetric");
        }
    }
}
