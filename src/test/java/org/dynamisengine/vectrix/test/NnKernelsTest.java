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
import java.util.function.DoubleUnaryOperator;

import org.dynamisengine.vectrix.experimental.KernelConfig;
import org.dynamisengine.vectrix.nn.Activations;
import org.dynamisengine.vectrix.nn.NnMath;
import org.dynamisengine.vectrix.nn.Norms;
import org.dynamisengine.vectrix.nn.Softmax;
import org.junit.jupiter.api.Test;

/** Neural-network math and kernels (2.5.0). */
class NnKernelsTest {

    interface FloatFn {
        float f(float x);
    }

    static double erf(double a) {
        double s = java.lang.Math.abs(a);
        double t = s;
        double sum = s;
        for (int n = 1; n < 400; n++) {
            t *= 2 * s * s / (2 * n + 1);
            sum += t;
            if (t < 1e-20 * sum) {
                break;
            }
        }
        return java.lang.Math.copySign(2 / java.lang.Math.sqrt(java.lang.Math.PI) * StrictMath.exp(-s * s) * sum, a);
    }

    /** erfc by its continued fraction in double, evaluated deep enough for every argument used. */
    static double erfcTail(double a) {
        double t = a;
        for (int n = 400; n >= 1; n--) {
            t = a + (n / 2.0) / t;
        }
        return StrictMath.exp(-a * a) / (java.lang.Math.sqrt(java.lang.Math.PI) * t);
    }

    private static void ulps(String name, FloatFn f, DoubleUnaryOperator ref, double lo, double hi, double bound) {
        SplittableRandom r = new SplittableRandom(name.hashCode());
        double worst = 0;
        for (int i = 0; i < 1_000_000; i++) {
            float x = (float) (lo + (hi - lo) * r.nextDouble());
            double want = ref.applyAsDouble(x);
            if (java.lang.Math.abs(want) < Float.MIN_NORMAL || java.lang.Math.abs(want) > Float.MAX_VALUE) {
                continue;
            }
            worst = java.lang.Math.max(worst, java.lang.Math.abs(f.f(x) - want) / java.lang.Math.ulp((float) want));
        }
        assertTrue(worst <= bound, name + " " + worst + " ulp > " + bound);
    }

    @Test
    void mathIsWithinItsDocumentedBounds() {
        ulps("exp", NnMath::exp, StrictMath::exp, -87.3, 88.7, 1.1);
        ulps("expm1", NnMath::expm1, StrictMath::expm1, -20, 20, 2);
        ulps("expm1 small", NnMath::expm1, StrictMath::expm1, -1e-3, 1e-3, 2);
        ulps("tanh", NnMath::tanh, StrictMath::tanh, -10, 10, 2.5);
        ulps("tanh small", NnMath::tanh, StrictMath::tanh, -1e-3, 1e-3, 2.5);
        ulps("sigmoid", NnMath::sigmoid, x -> 1 / (1 + StrictMath.exp(-x)), -80, 30, 2.5);
        ulps("log", NnMath::log, StrictMath::log, 1e-30, 1e30, 2);
        ulps("log near 1", NnMath::log, StrictMath::log, 0.5, 2, 2);
        ulps("log1p", NnMath::log1p, StrictMath::log1p, -0.999, 1e6, 3);
        ulps("erf", NnMath::erf, NnKernelsTest::erf, -4, 4, 2.5);
        ulps("erfc", NnMath::erfc, v -> 1 - erf(v), -4, 1, 2.5);
        ulps("erfc tail", NnMath::erfc, NnKernelsTest::erfcTail, 1, 9.5, 2.5);
        ulps("normalCdf lower", NnMath::normalCdf, v -> 0.5 * erfcTail(-v / java.lang.Math.sqrt(2)), -13, -1.42, 2.5);
        ulps("normalPdf", NnMath::normalPdf, v -> StrictMath.exp(-v * v / 2) / java.lang.Math.sqrt(2 * java.lang.Math.PI), -13,
            13, 1);
        ulps("normalCdf", NnMath::normalCdf, v -> 0.5 * (1 + erf(v / java.lang.Math.sqrt(2))), -1.41, 6, 2.5);
        assertEquals(Float.POSITIVE_INFINITY, NnMath.exp(89f), 0f);
        assertEquals(0f, NnMath.exp(-104f), 0f);
        assertTrue(Float.isNaN(NnMath.exp(Float.NaN)) && Float.isNaN(NnMath.log(-1f)) && Float.isNaN(NnMath.erf(Float.NaN)));
        assertEquals(Float.NEGATIVE_INFINITY, NnMath.log(0f), 0f);
        assertEquals(1f, NnMath.tanh(50f), 0f);
        assertEquals(-1f, NnMath.erf(-5f), 0f);
        assertEquals(Float.floatToRawIntBits(-0f), Float.floatToRawIntBits(NnMath.tanh(-0f)));
        assertEquals(NnMath.log(Float.MIN_VALUE), (float) StrictMath.log(Float.MIN_VALUE), 2e-5f);
    }

    private static float[] floats(SplittableRandom r, int n, double scale) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = (float) (scale * r.nextDouble(-1, 1));
        }
        return a;
    }

    @Test
    void simdAndScalarKernelsGiveTheSameBits() {
        SplittableRandom r = new SplittableRandom(1);
        boolean was = KernelConfig.simdEnabled();
        try {
            for (int n : new int[] {1, 7, 64, 1001}) {
                float[] x = floats(r, n, 60);
                x[0] = Float.NaN;
                float[][][] out = new float[2][4][];
                for (int mode = 0; mode < 2; mode++) {
                    KernelConfig.setSimdEnabled(mode == 1);
                    float[] y = new float[n];
                    Activations.sigmoid(x, 0, y, 0, n);
                    out[mode][0] = y.clone();
                    Activations.silu(x, 0, y, 0, n);
                    out[mode][1] = y.clone();
                    Activations.relu(x, 0, y, 0, n);
                    out[mode][2] = y.clone();
                    float[] z = floats(new SplittableRandom(n), n, 30);
                    Softmax.softmax(z, 0, y, 0, n);
                    out[mode][3] = y.clone();
                }
                for (int k = 0; k < 4; k++) {
                    assertArrayEquals(out[0][k], out[1][k], 0f, "kernel " + k + " n " + n);
                }
                for (int i = 1; i < n; i++) {
                    assertEquals(NnMath.sigmoid(x[i]), out[0][0][i], 0f);
                }
            }
        } finally {
            KernelConfig.setSimdEnabled(was);
        }
    }

    @Test
    void activationsMatchDoubleReferences() {
        SplittableRandom r = new SplittableRandom(2);
        int n = 5000;
        float[] x = floats(r, n, 8);
        float[] y = new float[n];
        Activations.gelu(x, 0, y, 0, n);
        for (int i = 0; i < n; i++) {
            double v = x[i];
            double z = -v / java.lang.Math.sqrt(2);
            double want = 0.5 * v * (z >= 1 ? erfcTail(z) : 1 - erf(z));
            assertEquals(want, y[i], 6e-7 * java.lang.Math.abs(want) + 1e-37, "gelu " + v);
        }
        Activations.softplus(x, 0, y, 0, n);
        for (int i = 0; i < n; i++) {
            double want = StrictMath.log1p(StrictMath.exp(x[i]));
            assertEquals(want, y[i], 4e-7 * want, "softplus " + x[i]);
        }
        // backward kernels against central differences of the double functions
        float[] dy = floats(r, n, 1);
        float[] dx = new float[n];
        DoubleUnaryOperator gelu = v -> 0.5 * v * (-v / java.lang.Math.sqrt(2) >= 1 ? erfcTail(-v / java.lang.Math.sqrt(2))
            : 1 - erf(-v / java.lang.Math.sqrt(2)));
        DoubleUnaryOperator silu = v -> v / (1 + StrictMath.exp(-v));
        Activations.geluBackward(x, 0, dy, 0, dx, 0, n);
        checkGrad(x, dy, dx, gelu, "gelu");
        Activations.siluBackward(x, 0, dy, 0, dx, 0, n);
        checkGrad(x, dy, dx, silu, "silu");
        DoubleUnaryOperator geluTanh = v -> 0.5 * v * (1 + StrictMath.tanh(java.lang.Math.sqrt(2 / java.lang.Math.PI)
            * (v + 0.044715 * v * v * v)));
        Activations.geluTanhBackward(x, 0, dy, 0, dx, 0, n);
        checkGrad(x, dy, dx, geluTanh, "geluTanh");
        Activations.softplusBackward(x, 0, dy, 0, dx, 0, n);
        checkGrad(x, dy, dx, v -> StrictMath.log1p(StrictMath.exp(v)), "softplus");
        float[] fy = new float[n];
        Activations.sigmoid(x, 0, fy, 0, n);
        Activations.sigmoidBackward(fy, 0, dy, 0, dx, 0, n);
        checkGrad(x, dy, dx, v -> 1 / (1 + StrictMath.exp(-v)), "sigmoid");
        Activations.tanh(x, 0, fy, 0, n);
        Activations.tanhBackward(fy, 0, dy, 0, dx, 0, n);
        checkGrad(x, dy, dx, StrictMath::tanh, "tanh");
        Activations.reluBackward(x, 0, dy, 0, dx, 0, n);
        for (int i = 0; i < n; i++) {
            assertEquals(x[i] > 0 ? dy[i] : 0f, dx[i], 0f);
        }
    }

    private static void checkGrad(float[] x, float[] dy, float[] dx, DoubleUnaryOperator f, String name) {
        for (int i = 0; i < x.length; i++) {
            double h = 1e-6 * java.lang.Math.max(1, java.lang.Math.abs(x[i]));
            double d = (f.applyAsDouble(x[i] + h) - f.applyAsDouble(x[i] - h)) / (2 * h);
            assertEquals(dy[i] * d, dx[i], 2e-6 * (java.lang.Math.abs(dy[i] * d) + java.lang.Math.abs(dy[i])), name + " at "
                + x[i]);
        }
    }

    @Test
    void softmaxIsStableAndConsistent() {
        SplittableRandom r = new SplittableRandom(3);
        int n = 777;
        float[] x = floats(r, n, 40);
        float[] y = new float[n];
        Softmax.softmax(x, 0, y, 0, n);
        double m = Double.NEGATIVE_INFINITY;
        for (float v : x) {
            m = java.lang.Math.max(m, v);
        }
        double s = 0;
        for (float v : x) {
            s += StrictMath.exp(v - m);
        }
        for (int i = 0; i < n; i++) {
            double want = StrictMath.exp(x[i] - m) / s;
            assertEquals(want, y[i], 4e-7 * want + 1e-44);
        }
        float[] scratch = new float[n];
        float[] ly = new float[n];
        Softmax.logSoftmax(x, 0, ly, 0, n, scratch, 0);
        double lse = m + StrictMath.log(s);
        assertEquals(lse, Softmax.logSumExp(x, 0, n, scratch, 0), 1e-6 * java.lang.Math.abs(lse));
        for (int i = 0; i < n; i++) {
            assertEquals(x[i] - lse, ly[i], 2e-5);
        }
        // huge inputs do not overflow
        float[] big = {3e38f, 3e38f, -3e38f};
        assertEquals(3e38f + (float) StrictMath.log(2), Softmax.logSumExp(big, 0, 3, scratch, 0), 1e32f);
        Softmax.softmax(big, 0, y, 0, 3);
        assertEquals(0.5f, y[0], 0f);
        // backward: dx = J·dy with J = diag(y) - y·yᵀ
        float[] dy = floats(r, n, 1);
        float[] dx = new float[n];
        Softmax.softmax(x, 0, y, 0, n);
        Softmax.softmaxBackward(y, 0, dy, 0, dx, 0, n);
        double dot = 0;
        for (int i = 0; i < n; i++) {
            dot += (double) dy[i] * y[i];
        }
        for (int i = 0; i < n; i++) {
            assertEquals(y[i] * (dy[i] - dot), dx[i], 1e-6 * (java.lang.Math.abs(y[i]) + 1e-30));
        }
    }

    /** Double-precision layer norm with γ, β; returns Σ dy·y for gradient checks. */
    private static double layerNormLoss(double[] x, double[] g, double[] b, double[] dy, double eps, boolean rms) {
        int n = x.length;
        double mean = 0;
        for (double v : x) {
            mean += v;
        }
        mean = rms ? 0 : mean / n;
        double var = 0;
        for (double v : x) {
            var += (v - mean) * (v - mean);
        }
        double r = 1 / java.lang.Math.sqrt(var / n + eps);
        double loss = 0;
        for (int i = 0; i < n; i++) {
            loss += dy[i] * ((x[i] - mean) * r * g[i] + (rms ? 0 : b[i]));
        }
        return loss;
    }

    @Test
    void normsMatchDoubleReferencesAndGradients() {
        SplittableRandom r = new SplittableRandom(4);
        int n = 96;
        float eps = 1e-5f;
        for (int pass = 0; pass < 2; pass++) {
            boolean rms = pass == 1;
            float[] x = floats(r, n, 3);
            for (int i = 0; i < n; i++) {
                x[i] += 2.5f; // an offset: the variance must not cancel
            }
            float[] g = floats(r, n, 2);
            float[] b = floats(r, n, 1);
            float[] dy = floats(r, n, 1);
            float[] y = new float[n];
            float[] stats = new float[2];
            if (rms) {
                Norms.rmsNorm(x, 0, g, 0, eps, y, 0, n, stats, 0);
            } else {
                Norms.layerNorm(x, 0, g, 0, b, 0, eps, y, 0, n, stats, 0);
            }
            double[] xd = new double[n];
            double[] gd = new double[n];
            double[] bd = new double[n];
            double[] dyd = new double[n];
            for (int i = 0; i < n; i++) {
                xd[i] = x[i];
                gd[i] = g[i];
                bd[i] = b[i];
                dyd[i] = dy[i];
            }
            float[] dx = new float[n];
            float[] dg = new float[n];
            float[] db = new float[n];
            float[] scratch = new float[2 * n];
            if (rms) {
                Norms.rmsNormBackward(x, 0, g, 0, stats, 0, dy, 0, dx, 0, dg, 0, n, scratch, 0);
            } else {
                Norms.layerNormBackward(x, 0, g, 0, stats, 0, dy, 0, dx, 0, dg, 0, db, 0, n, scratch, 0);
            }
            for (int i = 0; i < n; i++) {
                double h = 1e-5;
                double[] xp = xd.clone();
                double[] xm = xd.clone();
                xp[i] += h;
                xm[i] -= h;
                double want = (layerNormLoss(xp, gd, bd, dyd, eps, rms) - layerNormLoss(xm, gd, bd, dyd, eps, rms)) / (2 * h);
                assertEquals(want, dx[i], 2e-5 * (1 + java.lang.Math.abs(want)), (rms ? "rms" : "layer") + " dx " + i);
                double[] gp = gd.clone();
                double[] gm = gd.clone();
                gp[i] += h;
                gm[i] -= h;
                double wantG = (layerNormLoss(xd, gp, bd, dyd, eps, rms) - layerNormLoss(xd, gm, bd, dyd, eps, rms)) / (2 * h);
                assertEquals(wantG, dg[i], 2e-5 * (1 + java.lang.Math.abs(wantG)), "dgamma " + i);
                if (!rms) {
                    assertEquals(dy[i], db[i], 0f);
                }
            }
        }
    }
}
