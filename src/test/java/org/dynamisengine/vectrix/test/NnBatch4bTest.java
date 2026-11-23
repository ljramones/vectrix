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

import org.dynamisengine.vectrix.nn.Attention;
import org.dynamisengine.vectrix.nn.Formats;
import org.dynamisengine.vectrix.nn.Optim;
import org.junit.jupiter.api.Test;

/** Number formats, attention and optimizers (2.5.0). */
class NnBatch4bTest {

    @Test
    void bf16RoundsToNearestEven() {
        for (int h = 0; h < 65536; h++) {
            float f = Formats.fromBf16((short) h);
            if (f == f) {
                assertEquals(h, Formats.toBf16(f) & 0xffff, "round trip " + Integer.toHexString(h));
            }
        }
        assertEquals(1f, Formats.fromBf16(Formats.toBf16(1f + 0x1p-8f)), 0f, "tie to even (down)");
        assertEquals(1f + 0x1p-6f, Formats.fromBf16(Formats.toBf16(1f + 0x1.8p-7f)), 0f, "tie to even (up)");
        assertEquals(1f + 0x1p-7f, Formats.fromBf16(Formats.toBf16(1f + 0x1p-8f + 0x1p-20f)), 0f);
        assertTrue(Float.isNaN(Formats.fromBf16(Formats.toBf16(Float.intBitsToFloat(0x7f800001)))), "NaN stays NaN");
        assertEquals(Float.POSITIVE_INFINITY, Formats.fromBf16(Formats.toBf16(Float.MAX_VALUE)), 0f);
    }

    /** Brute force: the nearest finite code, ties to the even code; NaN for none. */
    private static int nearest(float x, boolean e4m3) {
        int best = -1;
        double bd = Double.POSITIVE_INFINITY;
        for (int c = 0; c < 256; c++) {
            float v = e4m3 ? Formats.fromE4M3((byte) c) : Formats.fromE5M2((byte) c);
            if (v != v || Float.isInfinite(v) || (Float.floatToRawIntBits(v) < 0) != (Float.floatToRawIntBits(x) < 0)) {
                continue;
            }
            double dist = java.lang.Math.abs((double) x - v);
            if (dist < bd || dist == bd && (c & 1) == 0) {
                bd = dist;
                best = c;
            }
        }
        return best;
    }

    @Test
    void fp8EncodersPickTheNearestValue() {
        for (int c = 0; c < 256; c++) {
            for (boolean e4 : new boolean[] {true, false}) {
                float v = e4 ? Formats.fromE4M3((byte) c) : Formats.fromE5M2((byte) c);
                if (v == v) {
                    int back = (e4 ? Formats.toE4M3(v, false) : Formats.toE5M2(v, false)) & 0xff;
                    assertEquals(c, back, (e4 ? "E4M3 " : "E5M2 ") + Integer.toHexString(c));
                }
            }
        }
        assertEquals(448f, Formats.fromE4M3(Formats.toE4M3(464f, false)), 0f, "tie past max goes to even 448");
        assertTrue(Float.isNaN(Formats.fromE4M3(Formats.toE4M3(465f, false))), "E4M3 overflow is NaN");
        assertEquals(448f, Formats.fromE4M3(Formats.toE4M3(1e9f, true)), 0f, "saturating");
        assertEquals(Float.POSITIVE_INFINITY, Formats.fromE5M2(Formats.toE5M2(61440f, false)), 0f, "IEEE tie to inf");
        assertEquals(57344f, Formats.fromE5M2(Formats.toE5M2(61439f, false)), 0f);
        assertEquals(-57344f, Formats.fromE5M2(Formats.toE5M2(Float.NEGATIVE_INFINITY, true)), 0f);
        SplittableRandom r = new SplittableRandom(1);
        for (int i = 0; i < 200_000; i++) {
            float x = (float) (java.lang.Math.scalb(r.nextDouble(-1, 1), r.nextInt(-20, 10)));
            for (boolean e4 : new boolean[] {true, false}) {
                int want = nearest(x, e4);
                float max = e4 ? 448f : 57344f;
                if (java.lang.Math.abs(x) > max) {
                    continue; // overflow cases are checked above
                }
                int got = (e4 ? Formats.toE4M3(x, false) : Formats.toE5M2(x, false)) & 0xff;
                assertEquals(want, got, (e4 ? "E4M3 " : "E5M2 ") + x);
            }
        }
    }

    @Test
    void int8BlocksQuantizeAndMultiply() {
        SplittableRandom r = new SplittableRandom(2);
        int m = 5;
        int n = 256;
        float[] w = new float[m * n];
        for (int i = 0; i < w.length; i++) {
            w[i] = (float) (r.nextGaussian() * (i % 64 == 0 ? 10 : 1));
        }
        byte[] q = new byte[m * n];
        float[] s = new float[m * n / 32];
        Formats.quantizeInt8(w, 0, m * n, q, 0, s, 0);
        float[] back = new float[m * n];
        Formats.dequantizeInt8(q, 0, s, 0, back, 0, m * n);
        for (int i = 0; i < w.length; i++) {
            assertTrue(java.lang.Math.abs(back[i] - w[i]) <= 0.5f * s[i / 32] * 1.0001f, "within half a step at " + i);
        }
        float[] x = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = (float) r.nextGaussian();
        }
        float[] y = new float[m];
        Formats.gemvInt8(m, n, q, 0, s, 0, x, 0, y, 0);
        for (int i = 0; i < m; i++) {
            double exact = 0;
            double mag = 0;
            for (int j = 0; j < n; j++) {
                exact += (double) q[i * n + j] * s[(i * n + j) / 32] * x[j];
                mag += java.lang.Math.abs((double) q[i * n + j] * s[(i * n + j) / 32] * x[j]);
            }
            assertEquals(exact, y[i], (7 + n / 32) * 0x1p-24 * mag, "row " + i);
        }
    }

    /** The documented int8 order, written plainly. */
    static float specDotInt8(byte[] q, float[] s, float[] x, int n) {
        float[] t = new float[8];
        for (int b = 0; b < n / 32; b++) {
            float[] a = new float[8];
            for (int j = 0; j < 32; j++) {
                a[j & 7] = java.lang.Math.fma(q[b * 32 + j], x[b * 32 + j], a[j & 7]);
            }
            for (int l = 0; l < 8; l++) {
                t[l] = java.lang.Math.fma(s[b], a[l], t[l]);
            }
        }
        return ((t[0] + t[4]) + (t[2] + t[6])) + ((t[1] + t[5]) + (t[3] + t[7]));
    }

    @Test
    void int8DotFollowsTheDocumentedOrderOnEveryPath() {
        SplittableRandom r = new SplittableRandom(9);
        boolean was = org.dynamisengine.vectrix.experimental.KernelConfig.simdEnabled();
        try {
            for (int n : new int[] {32, 64, 1024, 4096}) {
                float[] w = new float[n];
                float[] x = new float[n];
                for (int i = 0; i < n; i++) {
                    w[i] = (float) r.nextGaussian();
                    x[i] = (float) r.nextGaussian();
                }
                byte[] q = new byte[n];
                float[] s = new float[n / 32];
                Formats.quantizeInt8(w, 0, n, q, 0, s, 0);
                float want = specDotInt8(q, s, x, n);
                for (int mode = 0; mode < 2; mode++) {
                    org.dynamisengine.vectrix.experimental.KernelConfig.setSimdEnabled(mode == 1);
                    assertEquals(Float.floatToRawIntBits(want), Float.floatToRawIntBits(Formats.dotInt8(q, 0, s, 0, x, 0,
                        n)), "n " + n + " simd " + mode);
                }
            }
        } finally {
            org.dynamisengine.vectrix.experimental.KernelConfig.setSimdEnabled(was);
        }
    }

    /** Double attention for gradient checks: Σ dOut·out. */
    private static double attentionLoss(double[] q, double[] k, double[] v, double[] dOut, int t, int d, int dv,
            double scale) {
        double[] s = new double[t];
        double m = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < t; j++) {
            for (int c = 0; c < d; c++) {
                s[j] += q[c] * k[j * d + c];
            }
            s[j] *= scale;
            m = java.lang.Math.max(m, s[j]);
        }
        double z = 0;
        for (int j = 0; j < t; j++) {
            s[j] = StrictMath.exp(s[j] - m);
            z += s[j];
        }
        double loss = 0;
        for (int c = 0; c < dv; c++) {
            double o = 0;
            for (int j = 0; j < t; j++) {
                o += s[j] / z * v[j * dv + c];
            }
            loss += dOut[c] * o;
        }
        return loss;
    }

    @Test
    void attentionMatchesADoubleModelAndItsGradients() {
        SplittableRandom r = new SplittableRandom(3);
        int t = 9;
        int d = 8;
        int dv = 6;
        float scale = (float) (1 / java.lang.Math.sqrt(d));
        float[] q = new float[d];
        float[] k = new float[t * d];
        float[] v = new float[t * dv];
        float[] dOut = new float[dv];
        for (float[] a : new float[][] {q, k, v, dOut}) {
            for (int i = 0; i < a.length; i++) {
                a[i] = (float) r.nextDouble(-1, 1);
            }
        }
        float[] p = new float[t];
        float[] out = new float[dv];
        Attention.forward(q, 0, d, k, 0, d, v, 0, dv, dv, t, scale, null, 0, p, 0, out, 0, new float[t], 0);
        double[] qd = toD(q);
        double[] kd = toD(k);
        double[] vd = toD(v);
        for (int c = 0; c < dv; c++) {
            double[] unit = new double[dv];
            unit[c] = 1;
            assertEquals(attentionLoss(qd, kd, vd, unit, t, d, dv, scale), out[c], 1e-6);
        }
        float[] dq = new float[d];
        float[] dk = new float[t * d];
        float[] dV = new float[t * dv];
        Attention.backward(q, 0, d, k, 0, d, v, 0, dv, dv, t, scale, p, 0, dOut, 0, dq, 0, dk, 0, dV, 0,
            new float[2 * t], 0);
        double[] dOutD = toD(dOut);
        double h = 1e-6;
        checkGrad(qd, dq, x -> attentionLoss(x, kd, vd, dOutD, t, d, dv, scale), h, "dq");
        checkGrad(kd, dk, x -> attentionLoss(qd, x, vd, dOutD, t, d, dv, scale), h, "dk");
        checkGrad(vd, dV, x -> attentionLoss(qd, kd, x, dOutD, t, d, dv, scale), h, "dv");
        // a mask of -inf removes keys exactly
        float[] mask = new float[t];
        java.util.Arrays.fill(mask, 4, t, Float.NEGATIVE_INFINITY);
        Attention.forward(q, 0, d, k, 0, d, v, 0, dv, dv, t, scale, mask, 0, p, 0, out, 0, new float[t], 0);
        float[] out4 = new float[dv];
        Attention.forward(q, 0, d, k, 0, d, v, 0, dv, dv, 4, scale, null, 0, new float[4], 0, out4, 0, new float[4],
            0);
        for (int c = 0; c < dv; c++) {
            assertEquals(out4[c], out[c], 0f, "masked = shorter");
        }
    }

    private static double[] toD(float[] a) {
        double[] d = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            d[i] = a[i];
        }
        return d;
    }

    private static void checkGrad(double[] x, float[] g, java.util.function.ToDoubleFunction<double[]> f, double h,
            String name) {
        for (int i = 0; i < x.length; i++) {
            double[] xp = x.clone();
            double[] xm = x.clone();
            xp[i] += h;
            xm[i] -= h;
            double want = (f.applyAsDouble(xp) - f.applyAsDouble(xm)) / (2 * h);
            assertEquals(want, g[i], 2e-5 * (1 + java.lang.Math.abs(want)), name + " " + i);
        }
    }

    @Test
    void optimizersFollowTheirDefinitions() {
        float[] p = {1f, -2f, 0.5f};
        float[] g = {0.1f, -0.3f, 2f};
        float[] m = {0.01f, 0f, -0.2f};
        float[] v = {0.001f, 0.01f, 0.5f};
        float[] p0 = p.clone();
        float[] m0 = m.clone();
        float[] v0 = v.clone();
        Optim.adamW(p, 0, g, 0, m, 0, v, 0, 3, 1e-3f, 0.9f, 0.999f, 1e-8f, 0.01f, 7);
        for (int i = 0; i < 3; i++) {
            double mi = 0.9 * m0[i] + 0.1 * g[i];
            double vi = 0.999 * v0[i] + 0.001 * g[i] * g[i];
            double mh = mi / (1 - java.lang.Math.pow(0.9, 7));
            double vh = vi / (1 - java.lang.Math.pow(0.999, 7));
            double want = p0[i] * (1 - 1e-3 * 0.01) - 1e-3 * mh / (java.lang.Math.sqrt(vh) + 1e-8);
            assertEquals(mi, m[i], 1e-7);
            assertEquals(vi, v[i], 1e-7);
            assertEquals(want, p[i], 1e-6);
        }
        float[] q = {1f, -2f};
        float[] b = {0.5f, 0f};
        Optim.sgd(q, 0, new float[] {0.2f, 0.4f}, 0, b, 0, 2, 0.1f, 0.9f, 0f, false);
        assertEquals(1f - 0.1f * (0.9f * 0.5f + 0.2f), q[0], 1e-7f);
        assertEquals(-2f - 0.1f * 0.4f, q[1], 1e-7f);
        assertEquals(1f, Optim.clipFactor(0.5, 1.0), 0f);
        assertEquals(0.25f, Optim.clipFactor(4.0, 1.0), 0f);
    }
}
