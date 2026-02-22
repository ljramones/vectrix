/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.vectrix.fft.Convolutiond;
import org.vectrix.fft.Convolutionf;
import org.vectrix.fft.FFT1d;
import org.vectrix.fft.FFT1f;

class FFTTest {
    @Test
    void forwardMatchesKnownDftVector() {
        // [1,2,3,4] real-valued, imag=0
        float[] x = new float[] {1f, 0f, 2f, 0f, 3f, 0f, 4f, 0f};
        FFT1f.forward(x);
        assertComplex(x, 0, 10f, 0f, 1E-5f);
        assertComplex(x, 1, -2f, 2f, 1E-5f);
        assertComplex(x, 2, -2f, 0f, 1E-5f);
        assertComplex(x, 3, -2f, -2f, 1E-5f);
    }

    @Test
    void inverseRoundTrip() {
        float[] x = new float[] {
            1.0f, -0.5f, 0.25f, 0.75f, -1.25f, 0.33f, 2.0f, -1.5f,
            0.125f, -0.25f, -0.7f, 0.2f, 0.3f, -0.9f, 1.8f, 0.6f
        };
        float[] orig = x.clone();
        FFT1f.forward(x);
        FFT1f.inverse(x);
        for (int i = 0; i < x.length; i++) {
            assertEquals(orig[i], x[i], 2E-5f);
        }
    }

    @Test
    void nonPowerOfTwoThrows() {
        assertThrows(IllegalArgumentException.class, () -> FFT1f.forward(new float[] {1, 0, 2, 0, 3, 0}));
        assertThrows(IllegalArgumentException.class, () -> FFT1d.forward(new double[] {1, 0, 2, 0, 3, 0}));
    }

    @Test
    void circularConvolutionMatchesNaive() {
        int n = 8;
        float[] a = new float[n * 2];
        float[] b = new float[n * 2];
        for (int i = 0; i < n; i++) {
            a[2 * i] = i + 1.0f;
            a[2 * i + 1] = 0.1f * i;
            b[2 * i] = (i % 3) - 1.0f;
            b[2 * i + 1] = -0.05f * i;
        }
        float[] expected = naiveCircular(a, b);
        float[] actual = new float[n * 2];
        Convolutionf.circular(a, b, actual);
        assertArrayApprox(expected, actual, 2E-4f);
    }

    @Test
    void linearConvolutionMatchesNaive() {
        float[] a = new float[] {1f, 0f, 2f, 0f, 3f, 0f, 4f, 0f, 5f, 0f};
        float[] b = new float[] {2f, 0f, -1f, 0f, 0.5f, 0f};
        float[] expected = naiveLinear(a, b);
        float[] actual = new float[expected.length];
        Convolutionf.linear(a, b, actual);
        assertArrayApprox(expected, actual, 2E-4f);
    }

    @Test
    void floatDoubleParity() {
        float[] xf = new float[] {1f, 0f, 0.5f, 0.25f, -1f, 0.75f, 2f, -1f};
        double[] xd = toDouble(xf);
        FFT1f.forward(xf);
        FFT1d.forward(xd);
        for (int i = 0; i < xf.length; i++) {
            assertEquals((double) xf[i], xd[i], 2E-6);
        }

        float[] af = new float[] {1f, 0f, 2f, 0f, 0f, 0f, 0f, 0f};
        float[] bf = new float[] {0.5f, 0f, -1f, 0f, 3f, 0f, 0f, 0f};
        double[] ad = toDouble(af);
        double[] bd = toDouble(bf);
        float[] cf = new float[af.length];
        double[] cd = new double[ad.length];
        Convolutionf.circular(af, bf, cf);
        Convolutiond.circular(ad, bd, cd);
        for (int i = 0; i < cf.length; i++) {
            assertEquals((double) cf[i], cd[i], 2E-5);
        }
    }

    private static void assertComplex(float[] data, int index, float er, float ei, float eps) {
        int o = index << 1;
        assertEquals(er, data[o], eps);
        assertEquals(ei, data[o + 1], eps);
    }

    private static float[] naiveCircular(float[] a, float[] b) {
        int n = a.length >>> 1;
        float[] out = new float[a.length];
        for (int k = 0; k < n; k++) {
            float sr = 0.0f;
            float si = 0.0f;
            for (int i = 0; i < n; i++) {
                int j = (k - i) & (n - 1);
                int ao = i << 1;
                int bo = j << 1;
                float ar = a[ao], ai = a[ao + 1];
                float br = b[bo], bi = b[bo + 1];
                sr += ar * br - ai * bi;
                si += ar * bi + ai * br;
            }
            int o = k << 1;
            out[o] = sr;
            out[o + 1] = si;
        }
        return out;
    }

    private static float[] naiveLinear(float[] a, float[] b) {
        int na = a.length >>> 1;
        int nb = b.length >>> 1;
        int n = na + nb - 1;
        float[] out = new float[n << 1];
        for (int k = 0; k < n; k++) {
            float sr = 0.0f;
            float si = 0.0f;
            int i0 = java.lang.Math.max(0, k - (nb - 1));
            int i1 = java.lang.Math.min(na - 1, k);
            for (int i = i0; i <= i1; i++) {
                int j = k - i;
                int ao = i << 1;
                int bo = j << 1;
                float ar = a[ao], ai = a[ao + 1];
                float br = b[bo], bi = b[bo + 1];
                sr += ar * br - ai * bi;
                si += ar * bi + ai * br;
            }
            int o = k << 1;
            out[o] = sr;
            out[o + 1] = si;
        }
        return out;
    }

    private static void assertArrayApprox(float[] a, float[] b, float eps) {
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], eps, "idx " + i);
        }
    }

    private static double[] toDouble(float[] v) {
        double[] d = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            d[i] = v[i];
        }
        return d;
    }
}
