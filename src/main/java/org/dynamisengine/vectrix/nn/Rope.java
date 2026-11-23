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
 * Rotary position embeddings (RoPE): each pair of features of a head vector is rotated by the angle
 * {@code position·θ_i}, {@code θ_i = base^(-2i/dim)}.
 * <p>
 * Pairs are {@code (x[2i], x[2i + 1])} in the {@link Layout#INTERLEAVED} layout (the original paper, GPT-J) and
 * {@code (x[i], x[i + dim/2])} in the {@link Layout#HALF} layout (GPT-NeoX, LLaMA in Hugging Face form). Angles grow to
 * millions of radians at long contexts, where float sine and cosine are useless; here the angle is formed in double and
 * its sine and cosine come from {@link NnMath}'s reproducible double routine (exact argument reduction), and the
 * rotation is computed in double and rounded once. Results are the same bits everywhere. A plan is immutable and
 * shareable; nothing allocates.
 *
 * @since 2.5.0
 */
public final class Rope {

    /** Which features form a rotated pair. */
    public enum Layout {
        /** {@code (x[2i], x[2i + 1])}. */
        INTERLEAVED,
        /** {@code (x[i], x[i + dim/2])}. */
        HALF
    }

    private final int dim;
    private final double[] invFreq;
    private final Layout layout;

    private Rope(int dim, double[] invFreq, Layout layout) {
        this.dim = dim;
        this.invFreq = invFreq;
        this.layout = layout;
    }

    /**
     * Creates a plan with the standard frequencies {@code θ_i = base^(-2i/dim)}.
     *
     * @param dim the head dimension, even and positive
     * @param base the frequency base (10000 in most models), {@code > 1}
     * @return the plan
     */
    public static Rope plan(int dim, double base, Layout layout) {
        if (dim < 2 || (dim & 1) != 0 || !(base > 1) || layout == null) {
            throw new IllegalArgumentException("need an even dim >= 2, base > 1 and a layout");
        }
        double[] f = new double[dim / 2];
        for (int i = 0; i < f.length; i++) {
            f[i] = NnMath.powD(base, -2.0 * i / dim);
        }
        return new Rope(dim, f, layout);
    }

    /**
     * Creates a plan with caller-chosen frequencies (for scaled variants such as NTK-aware or YaRN), copied.
     *
     * @param invFreq the {@code dim/2} angular frequencies in radians per position
     */
    public static Rope plan(double[] invFreq, Layout layout) {
        if (invFreq == null || invFreq.length == 0 || layout == null) {
            throw new IllegalArgumentException("need frequencies and a layout");
        }
        return new Rope(2 * invFreq.length, invFreq.clone(), layout);
    }

    /** @return the head dimension */
    public int dim() {
        return dim;
    }

    /** Rotates the head vector {@code x[off .. off + dim)} in place for {@code position}. */
    public void apply(float[] x, int off, long position) {
        rotate(x, off, position, 1.0);
    }

    /** The backward pass (and the inverse rotation): rotates {@code dy[off .. off + dim)} in place by the opposite angle. */
    public void applyBackward(float[] dy, int off, long position) {
        rotate(dy, off, position, -1.0);
    }

    private void rotate(float[] x, int off, long position, double sign) {
        if (x == null || off < 0 || off > x.length - dim) {
            throw new IllegalArgumentException("x must hold " + dim + " values from offset " + off);
        }
        int half = dim / 2;
        double pos = position;
        for (int i = 0; i < half; i++) {
            double a = pos * invFreq[i];
            // NnMath.sinD/cosD expanded inline, sharing one argument reduction (RopeTest checks they agree bitwise)
            double s;
            double c;
            {
                double kf = (a * NnMath.TWO_OVER_PI_D + NnMath.ROUND_D) - NnMath.ROUND_D;
                double r = Math.fma(kf, -NnMath.PIO2_1, a);
                r = Math.fma(kf, -NnMath.PIO2_2, r);
                r = Math.fma(kf, -NnMath.PIO2_3, r);
                r = Math.fma(kf, -NnMath.PIO2_3T, r);
                double z = r * r;
                double ps = NnMath.SIN_D[NnMath.SIN_D.length - 1];
                for (int n = NnMath.SIN_D.length - 2; n >= 0; n--) {
                    ps = Math.fma(ps, z, NnMath.SIN_D[n]);
                }
                double pc = NnMath.COS_D[NnMath.COS_D.length - 1];
                for (int n = NnMath.COS_D.length - 2; n >= 0; n--) {
                    pc = Math.fma(pc, z, NnMath.COS_D[n]);
                }
                double sn = Math.fma(r * z, ps, r);
                double cs = Math.fma(z, pc, 1.0);
                int q = (int) ((long) kf & 3);
                s = q == 0 ? sn : q == 1 ? cs : q == 2 ? -sn : -cs;
                c = q == 0 ? cs : q == 1 ? -sn : q == 2 ? -cs : sn;
            }
            s *= sign;
            int i0 = layout == Layout.INTERLEAVED ? off + 2 * i : off + i;
            int i1 = layout == Layout.INTERLEAVED ? i0 + 1 : i0 + half;
            double x0 = x[i0];
            double x1 = x[i1];
            x[i0] = (float) (x0 * c - x1 * s);
            x[i1] = (float) (x0 * s + x1 * c);
        }
    }
}
