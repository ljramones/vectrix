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

import org.dynamisengine.vectrix.fft.ComplexFftd;
import org.dynamisengine.vectrix.fft.RealFftd;

/**
 * A plan for the full cross-correlation of real sequences of fixed lengths:
 * {@code c[ℓ] = Σ x[i]·y[i + ℓ]} for every lag {@code ℓ} from {@code -(nx - 1)} to {@code ny - 1}, written to
 * {@code out[ℓ + nx - 1]} ({@code nx + ny - 1} values). Correlating a sequence with itself gives its
 * autocorrelation.
 * <p>
 * Like {@link org.dynamisengine.vectrix.fft.BlockConvolverd}, the plan sums directly (with fused multiply-adds) when
 * that is cheaper and otherwise uses real FFTs of a fast size. Plans are immutable and shareable; calls take
 * caller scratch, do not allocate, and are deterministic.
 *
 * @since 2.5.0
 */
public final class Correlation {

    private final int nx;
    private final int ny;
    private final RealFftd fft;
    private final int scratchSize;

    private Correlation(int nx, int ny) {
        this.nx = nx;
        this.ny = ny;
        int len = nx + ny - 1;
        int m = 2 * ComplexFftd.fastSize((len + 1) / 2);
        long cost = (long) m * (64 - Long.numberOfLeadingZeros(m));
        if ((long) nx * ny <= 2 * cost) {
            fft = null;
            scratchSize = 0;
        } else {
            fft = RealFftd.plan(m);
            scratchSize = 2 * fft.spectrumLength() + fft.scratchSize();
        }
    }

    /**
     * Creates a plan.
     *
     * @param nx the length of {@code x}, at least 1
     * @param ny the length of {@code y}, at least 1
     * @return the plan
     * @throws IllegalArgumentException if a length is less than 1 or {@code nx + ny} exceeds
     *         {@link ComplexFftd#MAX_SIZE}
     */
    public static Correlation plan(int nx, int ny) {
        if (nx < 1 || ny < 1 || (long) nx + ny > ComplexFftd.MAX_SIZE) {
            throw new IllegalArgumentException("lengths out of range: " + nx + ", " + ny);
        }
        return new Correlation(nx, ny);
    }

    /**
     * Returns the number of outputs, {@code nx + ny - 1}: one per lag.
     *
     * @return the output length
     */
    public int outputLength() {
        return nx + ny - 1;
    }

    /**
     * Returns the scratch size of {@link #correlate}: 0 on the direct path.
     *
     * @return the number of scratch doubles
     */
    public int scratchSize() {
        return scratchSize;
    }

    /**
     * Returns whether this plan uses FFTs rather than direct summation.
     *
     * @return {@code true} on the FFT path
     */
    public boolean usesFft() {
        return fft != null;
    }

    /**
     * Correlates {@code x} with {@code y}.
     *
     * @param x the {@code nx} values of the first sequence
     * @param xOff the offset of the first value of {@code x}
     * @param y the {@code ny} values of the second sequence
     * @param yOff the offset of the first value of {@code y}
     * @param out receives {@link #outputLength()} values, lag {@code -(nx - 1)} first; must not overlap the inputs
     * @param outOff the offset of the first output
     * @param scratch working memory of {@link #scratchSize()} doubles; may be {@code null} when that is 0
     * @param scratchOff the offset into {@code scratch}
     * @throws IllegalArgumentException if an array does not hold its values from its offset (checked before
     *         anything is written)
     */
    public void correlate(double[] x, int xOff, double[] y, int yOff, double[] out, int outOff, double[] scratch,
            int scratchOff) {
        check(x, xOff, nx, "x");
        check(y, yOff, ny, "y");
        check(out, outOff, nx + ny - 1, "out");
        if (fft == null) {
            for (int k = 0; k < nx + ny - 1; k++) {
                int lag = k - (nx - 1);
                int i0 = java.lang.Math.max(0, -lag);
                int i1 = java.lang.Math.min(nx - 1, ny - 1 - lag);
                double s = 0.0;
                for (int i = i0; i <= i1; i++) {
                    s = java.lang.Math.fma(x[xOff + i], y[yOff + i + lag], s);
                }
                out[outOff + k] = s;
            }
            return;
        }
        check(scratch, scratchOff, scratchSize, "scratch");
        int m = fft.size();
        int sp = fft.spectrumLength();
        int a = scratchOff;
        int b = scratchOff + sp;
        int work = scratchOff + 2 * sp;
        // a = x reversed, b = y, both zero-padded to m: their convolution is the correlation
        for (int i = 0; i < nx; i++) {
            scratch[a + i] = x[xOff + nx - 1 - i];
        }
        java.util.Arrays.fill(scratch, a + nx, a + m, 0.0);
        System.arraycopy(y, yOff, scratch, b, ny);
        java.util.Arrays.fill(scratch, b + ny, b + m, 0.0);
        fft.forward(scratch, a, scratch, a, scratch, work);
        fft.forward(scratch, b, scratch, b, scratch, work);
        for (int k = 0; k < sp; k += 2) {
            double ar = scratch[a + k];
            double ai = scratch[a + k + 1];
            double br = scratch[b + k];
            double bi = scratch[b + k + 1];
            scratch[a + k] = ar * br - ai * bi;
            scratch[a + k + 1] = ar * bi + ai * br;
        }
        fft.inverse(scratch, a, scratch, a, scratch, work);
        System.arraycopy(scratch, a, out, outOff, nx + ny - 1);
    }

    private static void check(double[] v, int off, int count, String name) {
        if (v == null || off < 0 || off > v.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " doubles from offset " + off);
        }
    }
}
