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
 * A plan for the modified discrete cosine transform of {@code 2n} samples into {@code n} coefficients
 * ({@code n} even), {@code X[k] = Σ x[j]·cos(π/n·(j + 1/2 + n/2)(k + 1/2))}, and its inverse.
 * <p>
 * {@link #inverse} includes the factor {@code 2/n}, so the usual time-domain aliasing cancellation holds exactly:
 * frames of {@code 2n} samples advancing by {@code n}, each multiplied by a Princen–Bradley window (for example
 * {@link Windows#sine}) before {@link #forward} and again after {@link #inverse}, overlap-add back to the signal.
 * Both directions fold or unfold around one {@link Dctd} DCT-IV. Plans are immutable and shareable; calls take
 * caller scratch and do not allocate.
 *
 * @since 2.5.0
 */
public final class Mdctd {

    private final int n;
    private final Dctd dct;
    private final int scratchSize;

    private Mdctd(int n) {
        this.n = n;
        dct = Dctd.plan(n);
        scratchSize = n + dct.scratchSize();
    }

    /**
     * Creates a plan.
     *
     * @param n the number of coefficients, even and at least 2
     * @return the plan
     */
    public static Mdctd plan(int n) {
        if (n < 2 || (n & 1) != 0) {
            throw new IllegalArgumentException("the MDCT needs an even number of coefficients: " + n);
        }
        return new Mdctd(n);
    }

    /** @return the number of coefficients; frames have {@code 2n} samples */
    public int size() {
        return n;
    }

    /** @return the doubles of scratch a call needs */
    public int scratchSize() {
        return scratchSize;
    }

    /**
     * Transforms {@code 2n} samples into {@code n} coefficients.
     *
     * @param out receives {@code n} coefficients; must not overlap {@code in}
     */
    public void forward(double[] in, int inOff, double[] out, int outOff, double[] scratch, int scratchOff) {
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
            out[outOff + k] *= 0.5; // the DCT-IV's factor 2
        }
    }

    /**
     * Inverse-transforms {@code n} coefficients into {@code 2n} samples, scaled by {@code 2/n} for exact
     * overlap-add reconstruction.
     *
     * @param out receives {@code 2n} samples; must not overlap {@code in}
     */
    public void inverse(double[] in, int inOff, double[] out, int outOff, double[] scratch, int scratchOff) {
        range(in, inOff, n, "in");
        range(out, outOff, 2 * n, "out");
        range(scratch, scratchOff, scratchSize, "scratch");
        int h = n / 2;
        int u = scratchOff;
        dct.forwardIV(in, inOff, scratch, u, scratch, u + n);
        // y = (u2, -u2_reversed, -u1_reversed, -u1)/n for halves u1 = u[0, h), u2 = u[h, n)
        double d = n;
        for (int i = 0; i < h; i++) {
            double u1 = scratch[u + i];
            double u2 = scratch[u + h + i];
            out[outOff + i] = u2 / d;
            out[outOff + n - 1 - i] = -u2 / d;
            out[outOff + n + h + i] = -u1 / d;
            out[outOff + n + i] = -scratch[u + h - 1 - i] / d;
        }
    }

    private static void range(double[] a, int off, int count, String name) {
        if (a == null || off < 0 || off > a.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " doubles from offset " + off);
        }
    }
}
