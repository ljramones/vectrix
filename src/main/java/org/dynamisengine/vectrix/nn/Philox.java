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
package org.dynamisengine.vectrix.nn;

/**
 * Philox4x32-10 counter-based random numbers.
 *
 * @since 2.5.0
 * @deprecated moved to {@link org.dynamisengine.vectrix.hash.Philox}; {@code dropout} is now
 *             {@link Activations#dropout}
 */
@Deprecated(since = "2.6.0", forRemoval = true)
public final class Philox {

    private Philox() {
    }

    /** Forwards to {@link org.dynamisengine.vectrix.hash.Philox#block}. */
    public static void block(int c0, int c1, int c2, int c3, int k0, int k1, int[] out, int off) {
        org.dynamisengine.vectrix.hash.Philox.block(c0, c1, c2, c3, k0, k1, out, off);
    }

    /** Forwards to {@link org.dynamisengine.vectrix.hash.Philox#word}. */
    public static int word(long seed, long index) {
        return org.dynamisengine.vectrix.hash.Philox.word(seed, index);
    }

    /** Forwards to {@link org.dynamisengine.vectrix.hash.Philox#uniform}. */
    public static float uniform(long seed, long index) {
        return org.dynamisengine.vectrix.hash.Philox.uniform(seed, index);
    }

    /** Forwards to {@link org.dynamisengine.vectrix.hash.Philox#fillUniform}. */
    public static void fillUniform(long seed, long start, float[] dest, int off, int n) {
        org.dynamisengine.vectrix.hash.Philox.fillUniform(seed, start, dest, off, n);
    }

    /** Forwards to {@link org.dynamisengine.vectrix.hash.Philox#fillNormal}. */
    public static void fillNormal(long seed, long start, float mean, float std, float[] dest, int off, int n) {
        org.dynamisengine.vectrix.hash.Philox.fillNormal(seed, start, mean, std, dest, off, n);
    }

    /** Forwards to {@link Activations#dropout}. */
    public static void dropout(long seed, long start, float p, float[] x, int xOff, float[] y, int yOff, int n) {
        Activations.dropout(seed, start, p, x, xOff, y, yOff, n);
    }
}
