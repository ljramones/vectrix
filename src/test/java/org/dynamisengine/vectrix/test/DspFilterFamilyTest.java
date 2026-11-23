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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.dsp.AdaptiveFilter;
import org.dynamisengine.vectrix.dsp.Smoothers;
import org.dynamisengine.vectrix.dsp.Toeplitz;
import org.dynamisengine.vectrix.dsp.Wiener;
import org.junit.jupiter.api.Test;

/** Toeplitz/Levinson, Wiener, adaptive filters and smoothers (2.5.0). */
class DspFilterFamilyTest {

    private static double[] autocorr(double[] x, int lags) {
        double[] r = new double[lags];
        for (int k = 0; k < lags; k++) {
            for (int t = k; t < x.length; t++) {
                r[k] += x[t] * x[t - k];
            }
            r[k] /= x.length;
        }
        return r;
    }

    @Test
    void toeplitzSolversMatchDirectElimination() {
        SplittableRandom rnd = new SplittableRandom(1);
        double[] s = new double[5000];
        for (int i = 0; i < s.length; i++) {
            s[i] = rnd.nextGaussian() + (i > 0 ? 0.6 * s[i - 1] : 0);
        }
        int n = 20;
        double[] r = autocorr(s, n);
        double[] b = new double[n];
        for (int i = 0; i < n; i++) {
            b[i] = rnd.nextDouble(-1, 1);
        }
        double[] x = new double[n];
        Toeplitz.solve(r, b, n, x, new double[2 * n]);
        for (int i = 0; i < n; i++) {
            double v = 0;
            for (int j = 0; j < n; j++) {
                v += r[java.lang.Math.abs(i - j)] * x[j];
            }
            assertEquals(b[i], v, 1e-12, "residual row " + i);
        }
        // Levinson–Durbin satisfies the Yule–Walker equations and finds an AR(2) process
        double[] ar = new double[200_000];
        for (int t = 2; t < ar.length; t++) {
            ar[t] = 1.2 * ar[t - 1] - 0.5 * ar[t - 2] + rnd.nextGaussian();
        }
        int p = 4;
        double[] ra = autocorr(ar, p + 1);
        double[] a = new double[p + 1];
        double[] k = new double[p];
        double err = Toeplitz.levinsonDurbin(ra, p, a, k, new double[p + 1]);
        for (int i = 1; i <= p; i++) {
            double v = 0;
            for (int j = 0; j <= p; j++) {
                v += a[j] * ra[java.lang.Math.abs(i - j)];
            }
            assertEquals(0, v, 1e-12 * ra[0], "Yule-Walker row " + i);
        }
        assertEquals(-1.2, a[1], 0.02);
        assertEquals(0.5, a[2], 0.02);
        assertEquals(0, a[3], 0.02);
        assertEquals(1.0, err, 0.02, "innovation variance");
    }

    @Test
    void wienerFilterReducesTheError() {
        SplittableRandom rnd = new SplittableRandom(2);
        int len = 50_000;
        double[] d = new double[len];
        double[] x = new double[len];
        for (int t = 0; t < len; t++) {
            d[t] = java.lang.Math.sin(0.05 * t) + (t > 0 ? 0 : 0);
            x[t] = d[t] + 0.7 * rnd.nextGaussian();
        }
        int n = 32;
        double[] rxx = autocorr(x, n);
        double[] rdx = new double[n];
        for (int k = 0; k < n; k++) {
            for (int t = k; t < len; t++) {
                rdx[k] += d[t] * x[t - k];
            }
            rdx[k] /= len;
        }
        double[] w = new double[n];
        Wiener.fir(rxx, rdx, n, w, new double[2 * n]);
        double raw = 0;
        double filtered = 0;
        for (int t = n; t < len; t++) {
            double y = 0;
            for (int k = 0; k < n; k++) {
                y += w[k] * x[t - k];
            }
            raw += (x[t] - d[t]) * (x[t] - d[t]);
            filtered += (y - d[t]) * (y - d[t]);
        }
        assertTrue(filtered < 0.15 * raw, "filtered " + filtered + " raw " + raw);
        double[] g = new double[3];
        Wiener.gain(new double[] {3, 0, 1}, new double[] {1, 1, 0}, 3, 0.1, g);
        assertArrayEquals(new double[] {0.75, 0.1, 1}, g, 0.0);
    }

    @Test
    void adaptiveFiltersIdentifyAnUnknownSystem() {
        SplittableRandom rnd = new SplittableRandom(3);
        double[] h = {0.5, -0.3, 0.2, 0.1, -0.05, 0.02, 0.01, -0.01};
        int n = h.length;
        for (int kind = 0; kind < 3; kind++) {
            double[] w = new double[n];
            double[] u = new double[n];
            double[] hist = new double[n];
            double[] p = new double[n * n];
            for (int i = 0; i < n; i++) {
                p[i * n + i] = 1e6; // a weak prior: the regularization bias is about 1/(P0·t)
            }
            double[] scratch = new double[2 * n];
            int steps = kind == 2 ? 500 : 30_000;
            for (int t = 0; t < steps; t++) {
                double x = rnd.nextGaussian();
                System.arraycopy(hist, 0, hist, 1, n - 1);
                hist[0] = x;
                double d = 0;
                for (int k = 0; k < n; k++) {
                    d += h[k] * hist[k];
                }
                if (kind == 0) {
                    AdaptiveFilter.lms(n, w, u, x, d, 0.01);
                } else if (kind == 1) {
                    AdaptiveFilter.nlms(n, w, u, x, d, 0.5, 1e-9);
                } else {
                    AdaptiveFilter.rls(n, w, u, p, x, d, 1.0, scratch);
                }
            }
            for (int k = 0; k < n; k++) {
                assertEquals(h[k], w[k], kind == 2 ? 1e-6 : 1e-4, "kind " + kind + " tap " + k);
            }
        }
    }

    @Test
    void smoothersHaveTheirClassicProperties() {
        double[] c = new double[7];
        Smoothers.savitzkyGolay(3, 2, 0, c);
        double[] want = {-2, 3, 6, 7, 6, 3, -2};
        for (int i = 0; i < 7; i++) {
            assertEquals(want[i] / 21, c[i], 1e-14, "SG(7, 2) tap " + i);
        }
        double[] d1 = new double[5];
        Smoothers.savitzkyGolay(2, 2, 1, d1);
        double[] wd = {-2, -1, 0, 1, 2};
        for (int i = 0; i < 5; i++) {
            assertEquals(wd[i] / 10, d1[i], 1e-14, "SG first derivative tap " + i);
        }
        // a cubic is reproduced exactly by a cubic fit, and its derivative too
        double[] c3 = new double[9];
        double[] d3 = new double[9];
        Smoothers.savitzkyGolay(4, 3, 0, c3);
        Smoothers.savitzkyGolay(4, 3, 1, d3);
        double s = 0;
        double ds = 0;
        for (int t = -4; t <= 4; t++) {
            double v = 2 + 0.5 * t - 0.25 * t * t + 0.1 * t * t * t;
            s += c3[t + 4] * v;
            ds += d3[t + 4] * v;
        }
        assertEquals(2, s, 1e-12);
        assertEquals(0.5, ds, 1e-12);
        // the median removes a spike and keeps a step
        float[] x = {1, 1, 1, 9, 1, 1, 5, 5, 5, 5};
        float[] y = new float[x.length];
        Smoothers.median(x, 0, x.length, 1, y, 0, new float[3]);
        assertArrayEquals(new float[] {1, 1, 1, 1, 1, 1, 5, 5, 5, 5}, y, 0f);
        // alpha-beta locks onto constant velocity
        double[] st = {0, 0};
        double pos = 0;
        for (int t = 0; t < 200; t++) {
            pos += 3 * 0.1;
            Smoothers.alphaBeta(st, pos, 0.1, 0.5, 0.2);
        }
        assertEquals(pos, st[0], 1e-9);
        assertEquals(3, st[1], 1e-9);
        // One Euro: a constant stays constant; a step is followed; fast motion lags less with a larger beta
        double[] e = new double[3];
        for (int t = 0; t < 10; t++) {
            assertEquals(2.5, Smoothers.oneEuro(e, 2.5, 0.01, 1, 0.007, 1), 0.0);
        }
        double[] slow = new double[3];
        double[] fast = new double[3];
        double lagSlow = 0;
        double lagFast = 0;
        for (int t = 0; t < 100; t++) {
            double v = 5.0 * t * 0.01;
            lagSlow = v - Smoothers.oneEuro(slow, v, 0.01, 1, 0.0, 1);
            lagFast = v - Smoothers.oneEuro(fast, v, 0.01, 1, 1.0, 1);
        }
        assertTrue(lagFast < 0.5 * lagSlow && lagFast > 0, "lag " + lagFast + " vs " + lagSlow);
    }
}
