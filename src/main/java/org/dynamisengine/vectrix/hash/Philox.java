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
package org.dynamisengine.vectrix.hash;

import org.dynamisengine.vectrix.core.ReproducibleMath;

/**
 * Philox4x32-10, the counter-based random number generator of Salmon et al. (Random123): the {@code n}-th random
 * number of a stream is a pure function of {@code (seed, n)}.
 * <p>
 * There is no generator state, so any element can be drawn in any order by any thread with the same result: a
 * Monte Carlo sample, a dropout mask or a weight initialization is reproducible however the work is split, and a backward pass can
 * regenerate its forward mask instead of storing it. Index {@code n} uses word {@code n mod 4} of the block for
 * counter {@code n / 4}; the 64-bit seed is the key. Outputs match the Random123 known-answer vectors. Nothing
 * allocates.
 *
 * @since 2.6.0 (2.5.0 as {@code nn.Philox})
 */
public final class Philox {

    private static final int M0 = 0xD2511F53;
    private static final int M1 = 0xCD9E8D57;
    private static final int W0 = 0x9E3779B9;
    private static final int W1 = 0xBB67AE85;

    private Philox() {
    }

    /**
     * The block for a 128-bit counter {@code (c0, c1, c2, c3)} and key {@code (k0, k1)}, as four 32-bit words.
     *
     * @param out receives four words at {@code out[off .. off + 4)}
     */
    public static void block(int c0, int c1, int c2, int c3, int k0, int k1, int[] out, int off) {
        for (int r = 0; r < 10; r++) {
            long p0 = (M0 & 0xffffffffL) * (c0 & 0xffffffffL);
            long p1 = (M1 & 0xffffffffL) * (c2 & 0xffffffffL);
            int n0 = (int) (p1 >>> 32) ^ c1 ^ k0;
            int n2 = (int) (p0 >>> 32) ^ c3 ^ k1;
            c0 = n0;
            c1 = (int) p1;
            c2 = n2;
            c3 = (int) p0;
            k0 += W0;
            k1 += W1;
        }
        out[off] = c0;
        out[off + 1] = c1;
        out[off + 2] = c2;
        out[off + 3] = c3;
    }

    /** The 32-bit word number {@code index} of the stream {@code seed}. */
    public static int word(long seed, long index) {
        long counter = index >>> 2;
        int c0 = (int) counter;
        int c1 = (int) (counter >>> 32);
        int k0 = (int) seed;
        int k1 = (int) (seed >>> 32);
        int c2 = 0;
        int c3 = 0;
        int w = (int) (index & 3);
        int r0 = c0;
        int r1 = c1;
        int r2 = c2;
        int r3 = c3;
        for (int r = 0; r < 10; r++) {
            long p0 = (M0 & 0xffffffffL) * (r0 & 0xffffffffL);
            long p1 = (M1 & 0xffffffffL) * (r2 & 0xffffffffL);
            int n0 = (int) (p1 >>> 32) ^ r1 ^ k0;
            int n2 = (int) (p0 >>> 32) ^ r3 ^ k1;
            r0 = n0;
            r1 = (int) p1;
            r2 = n2;
            r3 = (int) p0;
            k0 += W0;
            k1 += W1;
        }
        return w == 0 ? r0 : w == 1 ? r1 : w == 2 ? r2 : r3;
    }

    /** A uniform float in [0, 1) with 24 random bits: number {@code index} of stream {@code seed}. */
    public static float uniform(long seed, long index) {
        return (word(seed, index) >>> 8) * 0x1p-24f;
    }

    /** {@code dest[off + i] = uniform(seed, start + i)}. */
    public static void fillUniform(long seed, long start, float[] dest, int off, int n) {
        check(dest, off, n);
        for (int i = 0; i < n; i++) {
            dest[off + i] = uniform(seed, start + i);
        }
    }

    /**
     * Normally distributed floats by the Box–Muller transform: element {@code i} uses the uniforms
     * {@code 2·⌊(start + i)/2⌋} and the next one, taking the cosine for even and the sine for odd stream positions,
     * all computed in double with {@link ReproducibleMath} and rounded once.
     */
    public static void fillNormal(long seed, long start, float mean, float std, float[] dest, int off, int n) {
        check(dest, off, n);
        for (int i = 0; i < n; i++) {
            long j = start + i;
            long pair = j & ~1L;
            double u1 = ((word(seed, pair) >>> 8) + 1) * 0x1p-24; // (0, 1]: log is finite
            double u2 = (word(seed, pair + 1) >>> 8) * 0x1p-24;
            double rad = Math.sqrt(-2.0 * ReproducibleMath.log(u1));
            double a = 2 * Math.PI * u2;
            double z = (j & 1) == 0 ? rad * ReproducibleMath.cos(a) : rad * ReproducibleMath.sin(a);
            dest[off + i] = (float) Math.fma(z, std, mean);
        }
    }

    private static void check(float[] a, int off, int n) {
        if (a == null || n < 0 || off < 0 || off > a.length - n) {
            throw new IllegalArgumentException("array must hold " + n + " values from offset " + off);
        }
    }
}
