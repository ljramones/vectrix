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
package org.dynamisengine.vectrix.fft;

/**
 * In-place radix-2 1D FFT over interleaved complex arrays:
 * {@code data[2*i]} = real, {@code data[2*i+1]} = imaginary.
  * @since 1.0.0
  */
public final class FFT1f {
    private FFT1f() {
    }

    public static void forward(float[] data) {
        transform(data, false);
    }

    public static void inverse(float[] data) {
        transform(data, true);
    }

    public static boolean isPowerOfTwoSize(int complexCount) {
        return complexCount > 0 && (complexCount & (complexCount - 1)) == 0;
    }

    static void transform(float[] data, boolean inverse) {
        if (data == null || (data.length & 1) != 0) {
            throw new IllegalArgumentException("data");
        }
        int n = data.length >>> 1;
        if (!isPowerOfTwoSize(n)) {
            throw new IllegalArgumentException("complex length must be power-of-two");
        }

        int j = 0;
        for (int i = 0; i < n; i++) {
            if (i < j) {
                swapComplex(data, i, j);
            }
            int bit = n >>> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>>= 1;
            }
            j ^= bit;
        }

        for (int len = 2; len <= n; len <<= 1) {
            // The twiddle recurrence runs in double: in float it loses about 3 digits over N/2 steps (relative
            // error 2.2e-4 at N = 65536), in double its drift stays below 1e-12 (6.8e-13 at N = 65536), far under float rounding.
            double angle = 2.0 * java.lang.Math.PI / len;
            if (!inverse) {
                angle = -angle;
            }
            double wLenR = org.dynamisengine.vectrix.core.AccurateMath.cos(angle);
            double wLenI = org.dynamisengine.vectrix.core.AccurateMath.sin(angle);
            int half = len >>> 1;
            // k outer: each twiddle is computed once per stage instead of once per block. The twiddle sequence is
            // the same and butterflies within a stage touch disjoint elements, so results are bit-identical.
            double w64R = 1.0;
            double w64I = 0.0;
            for (int k = 0; k < half; k++) {
                float wR = (float) w64R;
                float wI = (float) w64I;
                for (int i = k; i < n; i += len) {
                    int uo = i << 1;
                    int vo = (i + half) << 1;

                    float vr = data[vo];
                    float vi = data[vo + 1];
                    float tr = wR * vr - wI * vi;
                    float ti = wR * vi + wI * vr;

                    float ur = data[uo];
                    float ui = data[uo + 1];
                    data[uo] = ur + tr;
                    data[uo + 1] = ui + ti;
                    data[vo] = ur - tr;
                    data[vo + 1] = ui - ti;
                }
                double nextWR = w64R * wLenR - w64I * wLenI;
                double nextWI = w64R * wLenI + w64I * wLenR;
                w64R = nextWR;
                w64I = nextWI;
            }
        }

        if (inverse) {
            float invN = 1.0f / n;
            for (int i = 0; i < data.length; i++) {
                data[i] *= invN;
            }
        }
    }

    private static void swapComplex(float[] data, int a, int b) {
        int ao = a << 1;
        int bo = b << 1;
        float tr = data[ao];
        float ti = data[ao + 1];
        data[ao] = data[bo];
        data[ao + 1] = data[bo + 1];
        data[bo] = tr;
        data[bo + 1] = ti;
    }
}
