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
 * FFT-based convolution for interleaved complex sequences ({@code data[2*i]} real, {@code data[2*i+1]}
 * imaginary). Both methods allocate their FFT buffers on every call.
 *
 * @since 1.0.0
 * @deprecated use a {@link ComplexFftd} plan (forward, multiply, inverse), {@link BlockConvolverd} for streaming
 *             real filters, or {@code dsp.Correlation}
 */
@Deprecated(since = "2.6.0", forRemoval = true)
public final class Convolutiond {
    private Convolutiond() {
    }

    /**
     * Circular convolution {@code dest[k] = Σ a[j]·b[(k - j) mod n]}. All inputs must have equal interleaved length
     * and power-of-two complex count. {@code dest} may be {@code a} or {@code b}.
     *
     * @param a the first sequence, interleaved
     * @param b the second sequence, interleaved
     * @param dest receives the result, interleaved
     * @throws IllegalArgumentException if an array is null or of odd length, the lengths differ, or the complex
     *         count is not a power of two
     */
    public static void circular(double[] a, double[] b, double[] dest) {
        checkInterleaved(a, "a");
        checkInterleaved(b, "b");
        checkInterleaved(dest, "dest");
        if (a.length != b.length || a.length != dest.length) {
            throw new IllegalArgumentException("length mismatch");
        }
        int n = a.length >>> 1;
        if (!FFT1d.isPowerOfTwoSize(n)) {
            throw new IllegalArgumentException("complex length must be power-of-two");
        }
        double[] fa = a.clone();
        double[] fb = b.clone();
        FFT1d.forward(fa);
        FFT1d.forward(fb);
        multiplyPointwise(fa, fb);
        FFT1d.inverse(fa);
        System.arraycopy(fa, 0, dest, 0, dest.length);
    }

    /**
     * Linear convolution of {@code na} and {@code nb} complex values into {@code na + nb - 1} complex values.
     * Inputs may have any nonzero complex length; zero-padding is internal. {@code dest} must hold at least
     * {@code 2*(na+nb-1)} values; only that many are written. {@code dest} may be {@code a} or {@code b} if it is
     * long enough.
     *
     * @param a the first sequence, interleaved
     * @param b the second sequence, interleaved
     * @param dest receives the result, interleaved
     * @throws IllegalArgumentException if an array is null or of odd length, an input is empty, or {@code dest} is
     *         too short
     */
    public static void linear(double[] a, double[] b, double[] dest) {
        checkInterleaved(a, "a");
        checkInterleaved(b, "b");
        checkInterleaved(dest, "dest");
        int na = a.length >>> 1;
        int nb = b.length >>> 1;
        if (na == 0 || nb == 0) {
            // used to fail with ArrayIndexOutOfBoundsException from arraycopy
            throw new IllegalArgumentException("linear convolution needs non-empty inputs");
        }
        int out = na + nb - 1;
        if ((dest.length >>> 1) < out) {
            throw new IllegalArgumentException("dest");
        }
        int fftN = nextPowerOfTwo(out);
        double[] fa = new double[fftN << 1];
        double[] fb = new double[fftN << 1];
        System.arraycopy(a, 0, fa, 0, a.length);
        System.arraycopy(b, 0, fb, 0, b.length);
        FFT1d.forward(fa);
        FFT1d.forward(fb);
        multiplyPointwise(fa, fb);
        FFT1d.inverse(fa);
        System.arraycopy(fa, 0, dest, 0, out << 1);
    }

    private static void multiplyPointwise(double[] a, double[] b) {
        int n = a.length >>> 1;
        for (int i = 0; i < n; i++) {
            int o = i << 1;
            double ar = a[o];
            double ai = a[o + 1];
            double br = b[o];
            double bi = b[o + 1];
            a[o] = ar * br - ai * bi;
            a[o + 1] = ar * bi + ai * br;
        }
    }

    private static void checkInterleaved(double[] data, String name) {
        if (data == null || (data.length & 1) != 0) {
            throw new IllegalArgumentException(name);
        }
    }

    private static int nextPowerOfTwo(int n) {
        int v = 1;
        while (v < n) {
            v <<= 1;
        }
        return v;
    }
}
