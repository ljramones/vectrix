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

/**
 * A precomputed plan for the 2D discrete Fourier transform of a {@code rows × cols} grid of real floats, any sizes;
 * the single-precision twin of {@link RealFft2d}. The input is row-major: element {@code (r, c)} at {@code off +
 * r·cols + c}. The spectrum keeps the non-redundant half of each row: {@code rows × (cols/2 + 1)} complex values,
 * row-major and interleaved, so row {@code r} starts at {@code off + r·}{@link #spectrumRowLength()}.
 * <p>
 * {@link #forward} is unscaled; {@link #inverse} divides by {@code rows·cols} once, after both passes. Neither
 * modifies its input, like the 1D transforms. Rows use {@link RealFftf} and columns {@link ComplexFftf}, processed
 * 16 at a time. Accuracy, thread safety and determinism are those of {@link ComplexFftf}.
 *
 * @since 2.5.0
 */
public final class RealFft2f {

    private static final int TILE = 16;

    private final int rows;
    private final int cols;
    private final int half;
    private final RealFftf rowPlan;
    private final ComplexFftf colPlan;
    private final int tile;
    private final int buf;
    private final int scratchSize;

    private RealFft2f(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        half = cols / 2 + 1;
        rowPlan = RealFftf.plan(cols);
        colPlan = ComplexFftf.plan(rows);
        tile = java.lang.Math.min(TILE, half);
        long b = java.lang.Math.max(2L * rows * tile, 2L * half); // column tiles, or one unpacked spectrum row
        long need = b + java.lang.Math.max(rowPlan.scratchSize(), colPlan.scratchSize());
        if (need > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("grid too large: " + rows + " x " + cols);
        }
        buf = (int) b;
        scratchSize = (int) need;
    }

    /**
     * Creates a plan, with row and column transform plans for these sizes. Allocates the plans' tables.
     *
     * @param rows the number of rows, at least 1 and at most {@link ComplexFftf#MAX_SIZE}
     * @param cols the number of columns, at least 1 and at most {@link ComplexFftf#MAX_SIZE}
     * @return the plan
     * @throws IllegalArgumentException if a size is out of range, the spectrum or the scratch exceeds an array, or
     *         a size cannot be planned by {@link ComplexFftf#plan}
     */
    public static RealFft2f plan(int rows, int cols) {
        if (rows < 1 || cols < 1 || (long) rows * 2 * (cols / 2 + 1) > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("sizes out of range: " + rows + " x " + cols);
        }
        return new RealFft2f(rows, cols);
    }

    /**
     * Returns the number of rows.
     *
     * @return the number of rows
     */
    public int rows() {
        return rows;
    }

    /**
     * Returns the number of columns.
     *
     * @return the number of columns
     */
    public int cols() {
        return cols;
    }

    /**
     * Returns the number of floats per spectrum row: {@code 2·(cols/2 + 1)}.
     *
     * @return the number of floats per spectrum row: {@code 2·(cols/2 + 1)}
     */
    public int spectrumRowLength() {
        return 2 * half;
    }

    /**
     * Returns the number of floats in the spectrum: {@code rows·spectrumRowLength()}.
     *
     * @return the number of floats in the spectrum: {@code rows·spectrumRowLength()}
     */
    public int spectrumLength() {
        return rows * 2 * half;
    }

    /**
     * Returns the number of floats the scratch array needs.
     *
     * @return the number of floats the scratch array needs
     */
    public int scratchSize() {
        return scratchSize;
    }

    /**
     * Transforms the real grid into its half spectrum. {@code in} is not modified.
     *
     * @param in the grid, {@code rows·cols} floats from {@code inOff}
     * @param inOff the offset of element {@code (0, 0)}
     * @param out receives the {@link #spectrumLength()} floats of the spectrum; must not overlap {@code in}
     * @param outOff the offset of the spectrum
     * @param scratch caller-owned working memory of at least {@link #scratchSize()} floats
     * @param scratchOff the offset into {@code scratch}; the scratch must not overlap {@code in} or {@code out}
     * @throws IllegalArgumentException if an array is null or too short from its offset; nothing is written then
     */
    public void forward(float[] in, int inOff, float[] out, int outOff, float[] scratch, int scratchOff) {
        check(in, inOff, (long) rows * cols, "in");
        check(out, outOff, spectrumLength(), "out");
        check(scratch, scratchOff, scratchSize, "scratch");
        int work = scratchOff + buf;
        int rl = 2 * half;
        for (int r = 0; r < rows; r++) {
            rowPlan.forward(in, inOff + r * cols, out, outOff + r * rl, scratch, work);
        }
        columns(out, outOff, scratch, scratchOff, work);
    }

    /**
     * Inverse-transforms a half spectrum into the real grid, dividing by {@code rows·cols} once. {@code in} is not
     * modified. The columns are transformed first and kept in {@code out}, packed (each row's half spectrum has
     * real values at {@code 0} and, for even {@code cols}, at {@code cols/2}, so it fits in {@code cols}
     * values); then each row is unpacked into the scratch and transformed.
     *
     * @param in the {@link #spectrumLength()} floats of the spectrum, not modified
     * @param inOff the offset of the spectrum
     * @param out receives the grid, {@code rows·cols} floats; must not overlap {@code in}
     * @param outOff the offset of element {@code (0, 0)}
     * @param scratch caller-owned working memory of at least {@link #scratchSize()} floats
     * @param scratchOff the offset into {@code scratch}; the scratch must not overlap {@code in} or {@code out}
     * @throws IllegalArgumentException if an array is null or too short from its offset; nothing is written then
     */
    public void inverse(float[] in, int inOff, float[] out, int outOff, float[] scratch, int scratchOff) {
        check(in, inOff, spectrumLength(), "in");
        check(out, outOff, (long) rows * cols, "out");
        check(scratch, scratchOff, scratchSize, "scratch");
        int work = scratchOff + buf;
        int rl = 2 * half;
        boolean even = (cols & 1) == 0;
        // columns: unscaled inverse transforms of the half spectrum's columns, packed into the rows of out
        for (int c0 = 0; c0 < half; c0 += tile) {
            int w = java.lang.Math.min(tile, half - c0);
            for (int r = 0; r < rows; r++) {
                int src = inOff + r * rl + 2 * c0;
                for (int c = 0; c < w; c++) {
                    int dst = scratchOff + 2 * (c * rows + r);
                    scratch[dst] = in[src + 2 * c];
                    scratch[dst + 1] = in[src + 2 * c + 1];
                }
            }
            for (int c = 0; c < w; c++) {
                colPlan.run(scratch, scratchOff + 2 * c * rows, scratch, work, true);
            }
            for (int r = 0; r < rows; r++) {
                int row = outOff + r * cols;
                for (int c = 0; c < w; c++) {
                    int l = c0 + c;
                    int src = scratchOff + 2 * (c * rows + r);
                    if (l == 0) {
                        out[row] = scratch[src];
                    } else if (even && 2 * l == cols) {
                        out[row + 1] = scratch[src];
                    } else {
                        int dst = row + (even ? 2 * l : 2 * l - 1);
                        out[dst] = scratch[src];
                        out[dst + 1] = scratch[src + 1];
                    }
                }
            }
        }
        // rows: unpack each row's half spectrum into the scratch, then its unscaled real inverse into out
        for (int r = 0; r < rows; r++) {
            int row = outOff + r * cols;
            int b = scratchOff;
            scratch[b] = out[row];
            scratch[b + 1] = 0;
            for (int l = 1; 2 * l < cols; l++) {
                int src = row + (even ? 2 * l : 2 * l - 1);
                scratch[b + 2 * l] = out[src];
                scratch[b + 2 * l + 1] = out[src + 1];
            }
            if (even) {
                scratch[b + cols] = out[row + 1];
                scratch[b + cols + 1] = 0;
            }
            rowPlan.inverse(scratch, b, out, row, scratch, work, false);
        }
        // one division by rows·cols for both passes (the even row transform has already halved)
        int count = rows * rowPlan.unscaledFactor();
        int end = outOff + rows * cols;
        if (FftMath.isPowerOfTwo(count)) {
            float s = 1.0f / count; // exact
            for (int i = outOff; i < end; i++) {
                out[i] *= s;
            }
        } else {
            double s = count;
            for (int i = outOff; i < end; i++) {
                out[i] = (float) (out[i] / s);
            }
        }
    }

    private void columns(float[] d, int off, float[] scratch, int base, int work) {
        int rl = 2 * half;
        for (int c0 = 0; c0 < half; c0 += tile) {
            int w = java.lang.Math.min(tile, half - c0);
            for (int r = 0; r < rows; r++) {
                int src = off + r * rl + 2 * c0;
                for (int c = 0; c < w; c++) {
                    int dst = base + 2 * (c * rows + r);
                    scratch[dst] = d[src + 2 * c];
                    scratch[dst + 1] = d[src + 2 * c + 1];
                }
            }
            for (int c = 0; c < w; c++) {
                colPlan.forward(scratch, base + 2 * c * rows, scratch, work);
            }
            for (int r = 0; r < rows; r++) {
                int dst = off + r * rl + 2 * c0;
                for (int c = 0; c < w; c++) {
                    int src = base + 2 * (c * rows + r);
                    d[dst + 2 * c] = scratch[src];
                    d[dst + 2 * c + 1] = scratch[src + 1];
                }
            }
        }
    }

    private static void check(float[] a, int off, long count, String name) {
        if (a == null || off < 0 || off > a.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " floats from offset " + off);
        }
    }
}
