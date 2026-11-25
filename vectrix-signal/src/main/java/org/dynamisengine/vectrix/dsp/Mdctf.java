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
package org.dynamisengine.vectrix.dsp;

/**
 * A plan for the modified discrete cosine transform of {@code 2n} float samples into {@code n} coefficients
 * ({@code n} even), {@code X[k] = Σ x[j]·cos(π/n·(j + 1/2 + n/2)(k + 1/2))}, and its inverse; the single-precision
 * twin of {@link Mdctd}.
 * <p>
 * {@link #inverse} includes the factor {@code 2/n}, so the usual time-domain aliasing cancellation holds exactly:
 * frames of {@code 2n} samples advancing by {@code n}, each multiplied by a Princen–Bradley window (for example
 * {@link Windows#sine}) before {@link #forward} and again after {@link #inverse}, overlap-add back to the signal.
 * Both directions fold or unfold around one {@link Dctf} DCT-IV. Plans are immutable and shareable; calls take
 * caller scratch and do not allocate.
 *
 * @since 2.5.0
 */
public final class Mdctf {

    private final int n;
    private final Dctf dct;
    private final int scratchSize;

    private Mdctf(int n) {
        this.n = n;
        dct = Dctf.plan(n);
        scratchSize = n + dct.scratchSize();
    }

    /**
     * Creates a plan.
     *
     * @param n the number of coefficients, even and at least 2
     * @return the plan
     * @throws IllegalArgumentException if {@code n} is odd or less than 2
     */
    public static Mdctf plan(int n) {
        if (n < 2 || (n & 1) != 0) {
            throw new IllegalArgumentException("the MDCT needs an even number of coefficients: " + n);
        }
        return new Mdctf(n);
    }

    /**
     * Returns the number of coefficients {@code n}; frames have {@code 2n} samples.
     *
     * @return the number of coefficients
     */
    public int size() {
        return n;
    }

    /**
     * Returns the scratch size of {@link #forward} and {@link #inverse}.
     *
     * @return the number of floats
     */
    public int scratchSize() {
        return scratchSize;
    }

    /**
     * Transforms {@code 2n} samples into {@code n} coefficients.
     *
     * @param in the {@code 2n} samples, already windowed
     * @param inOff the offset of the first sample
     * @param out receives {@code n} coefficients; must not overlap {@code in} or {@code scratch}
     * @param outOff the offset of the first coefficient
     * @param scratch working memory of {@link #scratchSize()} floats
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if an array does not hold its values from its offset (checked before
     *         anything is written)
     */
    public void forward(float[] in, int inOff, float[] out, int outOff, float[] scratch, int scratchOff) {
        range(in, inOff, 2 * n, "in");
        range(out, outOff, n, "out");
        range(scratch, scratchOff, scratchSize, "scratch");
        int h = n / 2;
        int u = scratchOff;
        // the input as quarters (a, b, c, d): u = (-c_reversed - d, a - b_reversed)
        for (int i = 0; i < h; i++) {
            scratch[u + i] = -in[inOff + n + h - 1 - i] - in[inOff + n + h + i];
            scratch[u + h + i] = in[inOff + i] - in[inOff + n - 1 - i];
        }
        dct.forwardIV(scratch, u, out, outOff, scratch, u + n);
        for (int k = 0; k < n; k++) {
            out[outOff + k] *= 0.5f; // the DCT-IV's factor 2
        }
    }

    /**
     * Inverse-transforms {@code n} coefficients into {@code 2n} samples, scaled by {@code 2/n} for exact
     * overlap-add reconstruction.
     *
     * @param in the {@code n} coefficients
     * @param inOff the offset of the first coefficient
     * @param out receives {@code 2n} samples, to be windowed and overlap-added; must not overlap {@code in} or
     *        {@code scratch}
     * @param outOff the offset of the first sample
     * @param scratch working memory of {@link #scratchSize()} floats
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if an array does not hold its values from its offset (checked before
     *         anything is written)
     */
    public void inverse(float[] in, int inOff, float[] out, int outOff, float[] scratch, int scratchOff) {
        range(in, inOff, n, "in");
        range(out, outOff, 2 * n, "out");
        range(scratch, scratchOff, scratchSize, "scratch");
        int h = n / 2;
        int u = scratchOff;
        dct.forwardIV(in, inOff, scratch, u, scratch, u + n);
        // y = (u2, -u2_reversed, -u1_reversed, -u1)/n for halves u1 = u[0, h), u2 = u[h, n)
        double d = n;
        for (int i = 0; i < h; i++) {
            float u1 = scratch[u + i];
            float u2 = scratch[u + h + i];
            out[outOff + i] = (float) (u2 / d);
            out[outOff + n - 1 - i] = (float) (-u2 / d);
            out[outOff + n + h + i] = (float) (-u1 / d);
            out[outOff + n + i] = (float) (-scratch[u + h - 1 - i] / d);
        }
    }

    private static void range(float[] a, int off, int count, String name) {
        if (a == null || off < 0 || off > a.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " floats from offset " + off);
        }
    }
}
