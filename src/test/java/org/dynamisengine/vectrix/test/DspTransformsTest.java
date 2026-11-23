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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.dsp.Dctd;
import org.dynamisengine.vectrix.dsp.Dctf;
import org.dynamisengine.vectrix.dsp.Mdctd;
import org.dynamisengine.vectrix.dsp.Mdctf;
import org.dynamisengine.vectrix.dsp.Resampler;
import org.dynamisengine.vectrix.dsp.Windows;
import org.junit.jupiter.api.Test;

/** DCT-II/III/IV and MDCT (2.5.0) against compensated direct sums with exactly reduced cosines. */
class DspTransformsTest {

    private static final int[] SIZES = {1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 15, 16, 30, 97, 100, 128, 1000, 1024};

    /** Σ a[j]·cosTurns(num(j), den) in double-double. */
    interface Num {
        long at(int j);
    }

    private static double sum(double[] a, int n, Num num, long den) {
        double hi = 0;
        double lo = 0;
        for (int j = 0; j < n; j++) {
            double c = AccurateMath.cosTurns(num.at(j), den);
            double p = a[j] * c;
            double pe = java.lang.Math.fma(a[j], c, -p);
            double s = hi + p;
            double bb = s - hi;
            lo += (hi - (s - bb)) + (p - bb) + pe;
            hi = s;
        }
        return hi + lo;
    }

    private static double relErr(double[] got, double[] want) {
        double num = 0;
        double den = 0;
        for (int i = 0; i < want.length; i++) {
            num += (got[i] - want[i]) * (got[i] - want[i]);
            den += want[i] * want[i];
        }
        return java.lang.Math.sqrt(num / java.lang.Math.max(den, 1e-300));
    }

    private static double[] noise(SplittableRandom r, int n) {
        double[] a = new double[n];
        for (int i = 0; i < n; i++) {
            a[i] = r.nextDouble(-1, 1);
        }
        return a;
    }

    @Test
    void dctsMatchTheirDefinitions() {
        SplittableRandom r = new SplittableRandom(1);
        for (int n : SIZES) {
            double[] x = noise(r, n);
            Dctd p = Dctd.plan(n);
            double[] s = new double[p.scratchSize()];
            double[] want2 = new double[n];
            double[] want3 = new double[n];
            for (int k = 0; k < n; k++) {
                final int kk = k;
                want2[k] = 2 * sum(x, n, j -> (2L * j + 1) * kk, 4L * n);
                double[] tail = x.clone();
                tail[0] = 0;
                want3[k] = (x[0] + 2 * sum(tail, n, j -> (long) j * (2 * kk + 1), 4L * n)) / (2.0 * n);
            }
            double[] y = new double[n];
            p.forward(x, 0, y, 0, s, 0);
            assertTrue(relErr(y, want2) < 6e-16, "DCT-II n " + n + ": " + relErr(y, want2));
            p.inverse(x, 0, y, 0, s, 0);
            assertTrue(relErr(y, want3) < 6e-16, "DCT-III n " + n + ": " + relErr(y, want3));
            // round trip, in place
            double[] z = x.clone();
            p.forward(z, 0, z, 0, s, 0);
            p.inverse(z, 0, z, 0, s, 0);
            assertTrue(relErr(z, x) < 6e-16, "round trip n " + n);
            Dctf pf = Dctf.plan(n);
            float[] xf = new float[n];
            double[] xd = new double[n];
            for (int i = 0; i < n; i++) {
                xf[i] = (float) x[i];
                xd[i] = xf[i];
            }
            float[] yf = new float[n];
            pf.forward(xf, 0, yf, 0, new float[pf.scratchSize()], 0);
            double[] yd = new double[n];
            p.forward(xd, 0, yd, 0, s, 0);
            double[] yfd = new double[n];
            for (int i = 0; i < n; i++) {
                yfd[i] = yf[i];
            }
            assertTrue(relErr(yfd, yd) < 3e-7, "float DCT-II n " + n);
            if ((n & 1) == 0) {
                double[] want4 = new double[n];
                for (int k = 0; k < n; k++) {
                    final int kk = k;
                    want4[k] = 2 * sum(x, n, j -> (2L * j + 1) * (2L * kk + 1), 8L * n);
                }
                p.forwardIV(x, 0, y, 0, s, 0);
                assertTrue(relErr(y, want4) < 6e-16, "DCT-IV n " + n + ": " + relErr(y, want4));
                p.inverseIV(y, 0, y, 0, s, 0);
                assertTrue(relErr(y, x) < 6e-16, "DCT-IV round trip n " + n);
            } else {
                assertThrows(IllegalStateException.class, () -> p.forwardIV(x, 0, new double[n], 0, s, 0));
            }
        }
    }

    @Test
    void mdctMatchesItsDefinitionAndReconstructs() {
        SplittableRandom r = new SplittableRandom(2);
        for (int n : new int[] {2, 4, 6, 8, 64, 100, 480, 1024}) {
            double[] x = noise(r, 2 * n);
            Mdctd p = Mdctd.plan(n);
            double[] s = new double[p.scratchSize()];
            double[] want = new double[n];
            for (int k = 0; k < n; k++) {
                final int kk = k;
                want[k] = sum(x, 2 * n, j -> (2L * j + 1 + n) * (2L * kk + 1), 8L * n);
            }
            double[] c = new double[n];
            p.forward(x, 0, c, 0, s, 0);
            assertTrue(relErr(c, want) < 6e-16, "MDCT n " + n + ": " + relErr(c, want));
            // time-domain aliasing cancellation with the sine window
            int frames = 12;
            double[] sig = noise(r, (frames + 1) * n);
            double[] w = new double[2 * n];
            Windows.sine(w, 0, 2 * n);
            double[] outSig = new double[sig.length];
            double[] frame = new double[2 * n];
            double[] back = new double[2 * n];
            for (int t = 0; t < frames; t++) {
                for (int i = 0; i < 2 * n; i++) {
                    frame[i] = sig[t * n + i] * w[i];
                }
                p.forward(frame, 0, c, 0, s, 0);
                p.inverse(c, 0, back, 0, s, 0);
                for (int i = 0; i < 2 * n; i++) {
                    outSig[t * n + i] += back[i] * w[i];
                }
            }
            double[] inner = Arrays.copyOfRange(sig, n, frames * n);
            double[] got = Arrays.copyOfRange(outSig, n, frames * n);
            assertTrue(relErr(got, inner) < 1e-15, "TDAC n " + n + ": " + relErr(got, inner));
            // float twin
            Mdctf pf = Mdctf.plan(n);
            float[] xf = new float[2 * n];
            for (int i = 0; i < 2 * n; i++) {
                xf[i] = (float) x[i];
            }
            float[] cf = new float[n];
            pf.forward(xf, 0, cf, 0, new float[pf.scratchSize()], 0);
            double[] cfd = new double[n];
            for (int i = 0; i < n; i++) {
                cfd[i] = cf[i];
            }
            assertTrue(relErr(cfd, want) < 5e-7, "float MDCT n " + n + ": " + relErr(cfd, want));
        }
        assertThrows(IllegalArgumentException.class, () -> Mdctd.plan(7));
    }

    @Test
    void resamplerConvertsTonesAndStreamsExactly() {
        int[][] ratios = {{160, 147}, {2, 1}, {1, 3}, {3, 2}};
        for (int[] q : ratios) {
            Resampler rs = Resampler.plan(q[0], q[1]);
            double fin = 0.05 / java.lang.Math.max(1.0, (double) q[1] / q[0]); // well inside both pass bands
            int len = 20000;
            double[] x = new double[len];
            for (int i = 0; i < len; i++) {
                x[i] = java.lang.Math.sin(2 * java.lang.Math.PI * fin * i);
            }
            double[] y = new double[rs.maxOutput(len)];
            double[] st = new double[rs.stateSize()];
            int m = rs.process(x, 0, len, y, 0, st, 0);
            double fout = fin * rs.down() / rs.up();
            double err = 0;
            for (int k = m / 4; k < m - m / 4; k++) {
                double want = java.lang.Math.sin(2 * java.lang.Math.PI * fout * (k - rs.delay()));
                err = java.lang.Math.max(err, java.lang.Math.abs(y[k] - want));
            }
            assertTrue(err < 2e-5, q[0] + "/" + q[1] + ": " + err);
            // random chunks give the same bits; the float path is the double path rounded
            double[] st2 = new double[rs.stateSize()];
            double[] y2 = new double[y.length + 8];
            SplittableRandom r = new SplittableRandom(q[0]);
            int got = 0;
            for (int p = 0; p < len;) {
                int c = java.lang.Math.min(len - p, r.nextInt(0, 700));
                double[] tmp = new double[rs.maxOutput(c)];
                int k = rs.process(x, p, c, tmp, 0, st2, 0);
                System.arraycopy(tmp, 0, y2, got, k);
                got += k;
                p += c;
            }
            assertTrue(got == m, "output count " + got + " vs " + m);
            assertTrue(Arrays.equals(Arrays.copyOf(y, m), Arrays.copyOf(y2, m)), "chunking");
            float[] xf = new float[len];
            double[] xd = new double[len];
            for (int i = 0; i < len; i++) {
                xf[i] = (float) x[i];
                xd[i] = xf[i];
            }
            float[] yf = new float[rs.maxOutput(len)];
            int mf = rs.process(xf, 0, len, yf, 0, new double[rs.stateSize()], 0);
            double[] yd = new double[rs.maxOutput(len)];
            rs.process(xd, 0, len, yd, 0, new double[rs.stateSize()], 0);
            for (int k = 0; k < mf; k++) {
                assertTrue((float) yd[k] == yf[k], "float path at " + k);
            }
        }
        // a constant stays constant; up == down is an exact delay
        Resampler rs = Resampler.plan(5, 4);
        double[] ones = new double[4000];
        Arrays.fill(ones, 1.0);
        double[] y = new double[rs.maxOutput(4000)];
        int m = rs.process(ones, 0, 4000, y, 0, new double[rs.stateSize()], 0);
        for (int k = m / 2; k < m; k++) {
            assertTrue(java.lang.Math.abs(y[k] - 1) < 2e-5, "DC gain at " + k + ": " + y[k]);
        }
        Resampler id = Resampler.plan(3, 3, 4, 80);
        double[] x = noise(new SplittableRandom(9), 100);
        double[] yi = new double[id.maxOutput(100)];
        int mi = id.process(x, 0, 100, yi, 0, new double[id.stateSize()], 0);
        assertTrue(mi == 100);
        for (int k = 4; k < 100; k++) {
            assertTrue(yi[k] == x[k - 4], "identity delay at " + k);
        }
    }
}
