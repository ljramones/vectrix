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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.nn.Conv2d;
import org.dynamisengine.vectrix.nn.Pool2d;
import org.junit.jupiter.api.Test;

/** Convolution and pooling (2.5.0) against direct double-precision definitions and finite differences. */
class ConvPoolTest {

    // cin, cout, h, w, kh, kw, stride, pad, dilation, groups
    private static final int[][] SHAPES = {
        {1, 1, 5, 5, 3, 3, 1, 0, 1, 1}, {3, 4, 7, 6, 3, 3, 1, 1, 1, 1}, {4, 6, 9, 8, 3, 2, 2, 1, 1, 2},
        {3, 3, 8, 8, 3, 3, 1, 1, 1, 3}, {2, 5, 10, 9, 3, 3, 1, 2, 2, 1}, {6, 4, 1, 20, 1, 5, 2, 2, 1, 1},
        {2, 3, 6, 6, 1, 1, 1, 0, 1, 1}};

    private static float[] rnd(SplittableRandom r, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = (float) r.nextDouble(-1, 1);
        }
        return a;
    }

    /** The direct definition, in double: Σ over the group's channels and the kernel. */
    private static double[] reference(int[] s, double[] in, double[] wt, double[] bias, int hout, int wout) {
        int cin = s[0];
        int cout = s[1];
        int h = s[2];
        int w = s[3];
        int kh = s[4];
        int kw = s[5];
        int st = s[6];
        int pad = s[7];
        int dil = s[8];
        int g = s[9];
        int cpg = cin / g;
        int opg = cout / g;
        double[] out = new double[cout * hout * wout];
        for (int co = 0; co < cout; co++) {
            int grp = co / opg;
            for (int oy = 0; oy < hout; oy++) {
                for (int ox = 0; ox < wout; ox++) {
                    double acc = bias == null ? 0 : bias[co];
                    for (int c = 0; c < cpg; c++) {
                        for (int ky = 0; ky < kh; ky++) {
                            for (int kx = 0; kx < kw; kx++) {
                                int iy = oy * st - pad + ky * dil;
                                int ix = ox * st - pad + kx * dil;
                                if (iy >= 0 && iy < h && ix >= 0 && ix < w) {
                                    acc += wt[((co * cpg + c) * kh + ky) * kw + kx] * in[((grp * cpg + c) * h + iy) * w + ix];
                                }
                            }
                        }
                    }
                    out[(co * hout + oy) * wout + ox] = acc;
                }
            }
        }
        return out;
    }

    private static double[] d(float[] a) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            r[i] = a[i];
        }
        return r;
    }

    @Test
    void convolutionMatchesTheDefinitionAndItsGradients() {
        SplittableRandom r = new SplittableRandom(1);
        for (int[] s : SHAPES) {
            Conv2d conv = Conv2d.of(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], s[8], s[9]);
            int ho = conv.outHeight();
            int wo = conv.outWidth();
            float[] in = rnd(r, s[0] * s[2] * s[3]);
            float[] wt = rnd(r, conv.weightCount());
            float[] bias = rnd(r, s[1]);
            float[] out = new float[s[1] * ho * wo];
            float[] scratch = new float[conv.scratchSize()];
            conv.forward(in, 0, wt, 0, bias, 0, out, 0, scratch, 0);
            double[] want = reference(s, d(in), d(wt), d(bias), ho, wo);
            for (int i = 0; i < out.length; i++) {
                assertEquals(want[i], out[i], 1e-5, java.util.Arrays.toString(s) + " at " + i);
            }
            // gradients of L = Σ dOut·out
            float[] dOut = rnd(r, out.length);
            float[] dIn = new float[in.length];
            float[] dW = new float[wt.length];
            float[] dB = new float[s[1]];
            conv.backward(in, 0, wt, 0, dOut, 0, dIn, 0, dW, 0, dB, 0, scratch, 0);
            double[] inD = d(in);
            double[] wD = d(wt);
            double[] dOutD = d(dOut);
            double h = 1e-4;
            for (int i = 0; i < in.length; i++) {
                double[] p = inD.clone();
                double[] m = inD.clone();
                p[i] += h;
                m[i] -= h;
                double g = (loss(s, p, wD, dOutD, ho, wo) - loss(s, m, wD, dOutD, ho, wo)) / (2 * h);
                assertEquals(g, dIn[i], 1e-4 * (1 + java.lang.Math.abs(g)), "dIn " + i);
            }
            for (int i = 0; i < wt.length; i++) {
                double[] p = wD.clone();
                double[] m = wD.clone();
                p[i] += h;
                m[i] -= h;
                double g = (loss(s, inD, p, dOutD, ho, wo) - loss(s, inD, m, dOutD, ho, wo)) / (2 * h);
                assertEquals(g, dW[i], 1e-4 * (1 + java.lang.Math.abs(g)), "dW " + i);
            }
            for (int c = 0; c < s[1]; c++) {
                double g = 0;
                for (int i = 0; i < ho * wo; i++) {
                    g += dOut[c * ho * wo + i];
                }
                assertEquals(g, dB[c], 1e-5);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Conv2d.of(3, 4, 5, 5, 3, 3, 1, 0, 1, 2));
    }

    private static double loss(int[] s, double[] in, double[] wt, double[] dOut, int ho, int wo) {
        double[] out = reference(s, in, wt, null, ho, wo);
        double l = 0;
        for (int i = 0; i < out.length; i++) {
            l += out[i] * dOut[i];
        }
        return l;
    }

    @Test
    void poolingMatchesTheDefinitionAndItsGradients() {
        SplittableRandom r = new SplittableRandom(2);
        int c = 3;
        int h = 7;
        int w = 8;
        for (int[] cfg : new int[][] {{2, 2, 0}, {3, 2, 1}, {3, 1, 1}}) {
            Pool2d pool = Pool2d.of(c, h, w, cfg[0], cfg[1], cfg[2]);
            int ho = pool.outHeight();
            int wo = pool.outWidth();
            float[] in = rnd(r, c * h * w);
            float[] mx = new float[c * ho * wo];
            float[] av = new float[c * ho * wo];
            int[] arg = new int[mx.length];
            pool.max(in, 0, mx, 0, arg, 0);
            pool.average(in, 0, av, 0);
            for (int ch = 0; ch < c; ch++) {
                for (int oy = 0; oy < ho; oy++) {
                    for (int ox = 0; ox < wo; ox++) {
                        double m = Double.NEGATIVE_INFINITY;
                        double sum = 0;
                        int cnt = 0;
                        for (int ky = 0; ky < cfg[0]; ky++) {
                            for (int kx = 0; kx < cfg[0]; kx++) {
                                int iy = oy * cfg[1] - cfg[2] + ky;
                                int ix = ox * cfg[1] - cfg[2] + kx;
                                if (iy >= 0 && iy < h && ix >= 0 && ix < w) {
                                    double v = in[(ch * h + iy) * w + ix];
                                    m = java.lang.Math.max(m, v);
                                    sum += v;
                                    cnt++;
                                }
                            }
                        }
                        int o = (ch * ho + oy) * wo + ox;
                        assertEquals(m, mx[o], 0.0);
                        assertEquals(m, in[arg[o]], 0.0);
                        assertEquals(sum / cnt, av[o], 1e-6);
                    }
                }
            }
            float[] dOut = rnd(r, mx.length);
            float[] dIn = new float[in.length];
            pool.maxBackward(arg, 0, dOut, 0, dIn, 0);
            double total = 0;
            for (float v : dIn) {
                total += v;
            }
            double totalOut = 0;
            for (float v : dOut) {
                totalOut += v;
            }
            assertEquals(totalOut, total, 1e-5, "max routes every gradient once");
            pool.averageBackward(dOut, 0, dIn, 0);
            // averaging is linear: Σ dIn·x = Σ dOut·avg(x)
            double lhs = 0;
            double rhs = 0;
            for (int i = 0; i < in.length; i++) {
                lhs += (double) dIn[i] * in[i];
            }
            for (int i = 0; i < av.length; i++) {
                rhs += (double) dOut[i] * av[i];
            }
            assertEquals(rhs, lhs, 1e-5, "adjoint of the average");
        }
    }
}
