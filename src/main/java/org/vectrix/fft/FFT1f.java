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
package org.vectrix.fft;

/**
 * In-place radix-2 1D FFT over interleaved complex arrays:
 * {@code data[2*i]} = real, {@code data[2*i+1]} = imaginary.
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
            float angle = (float) (2.0 * java.lang.Math.PI / len);
            if (!inverse) {
                angle = -angle;
            }
            float wLenR = (float) java.lang.Math.cos(angle);
            float wLenI = (float) java.lang.Math.sin(angle);
            int half = len >>> 1;
            for (int i = 0; i < n; i += len) {
                float wR = 1.0f;
                float wI = 0.0f;
                for (int k = 0; k < half; k++) {
                    int u = i + k;
                    int v = u + half;
                    int uo = u << 1;
                    int vo = v << 1;

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

                    float nextWR = wR * wLenR - wI * wLenI;
                    float nextWI = wR * wLenI + wI * wLenR;
                    wR = nextWR;
                    wI = nextWI;
                }
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
