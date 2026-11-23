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
package org.dynamisengine.vectrix.fft;

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * Exactly reduced roots of unity shared by the FFT plans.
 * <p>
 * {@code exp(-2πi e/d)} is reduced to an angle of at most π/4 with integer arithmetic before the sine and cosine are
 * taken, so every root is within about one ulp and the symmetries are exact: {@code W^(d/4) = -i},
 * {@code W^(d/2) = -1}, {@code W^(d-e) = conj(W^e)}.
 */
final class FftMath {

    private FftMath() {
    }

    /** Writes {@code exp(-2πi e/d)} to {@code out[o]} (real) and {@code out[o + 1]} (imaginary). */
    static void root(long e, long d, double[] out, int o) {
        out[o] = AccurateMath.cosTurns(e, d);
        out[o + 1] = -AccurateMath.sinTurns(e, d) + 0.0; // "+ 0.0" turns a negative zero positive
    }

    /** The interleaved roots {@code exp(-2πi k/d)}, {@code k < count}. */
    static double[] roots(int count, long d) {
        double[] t = new double[2 * count];
        for (int k = 0; k < count; k++) {
            root(k, d, t, 2 * k);
        }
        return t;
    }

    static float[] toFloat(double[] a) {
        float[] f = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            f[i] = (float) a[i];
        }
        return f;
    }

    static boolean isPowerOfTwo(long n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    /** The largest prime factor handled by a direct butterfly; larger ones use Bluestein's algorithm. */
    static final int MAX_DIRECT_PRIME = 61;

    /**
     * The stage radices of {@code n}: fours, at most one two, threes, fives, then other primes ascending. When
     * the last exceeds {@link #MAX_DIRECT_PRIME} the size needs Bluestein's algorithm.
     */
    static int[] factor(int n) {
        int[] tmp = new int[32];
        int k = 0;
        int rest = n;
        while (rest % 4 == 0) {
            tmp[k++] = 4;
            rest /= 4;
        }
        if (rest % 2 == 0) {
            tmp[k++] = 2;
            rest /= 2;
        }
        for (int p = 3; rest > 1; p += 2) {
            if ((long) p * p > rest) {
                p = rest;
            }
            while (rest % p == 0) {
                tmp[k++] = p;
                rest /= p;
            }
        }
        return java.util.Arrays.copyOf(tmp, k);
    }
}
