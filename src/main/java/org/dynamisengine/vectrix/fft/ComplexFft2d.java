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
 * A precomputed plan for the 2D discrete Fourier transform of a {@code rows × cols} grid of complex values, any
 * sizes. Data is row-major and interleaved: element {@code (r, c)} is at {@code off + 2·(r·cols + c)}.
 * <p>
 * {@link #forward} is unscaled; {@link #inverse} divides by {@code rows·cols}. The transform is separable: every
 * row, then every column, with {@link ComplexFftd} plans; columns are processed 16 at a time, gathered into
 * scratch. Accuracy, thread safety and determinism are those of {@link ComplexFftd}.
 *
 * @since 2.5.0
 */
public final class ComplexFft2d {

    private static final int TILE = 16;

    private final int rows;
    private final int cols;
    private final ComplexFftd rowPlan;
    private final ComplexFftd colPlan;
    private final int tile;
    private final int scratchSize;

    private ComplexFft2d(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        rowPlan = ComplexFftd.plan(cols);
        colPlan = ComplexFftd.plan(rows);
        tile = java.lang.Math.min(TILE, cols);
        long need = 2L * rows * tile + java.lang.Math.max(rowPlan.scratchSize(), colPlan.scratchSize());
        if (need > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("grid too large: " + rows + " x " + cols);
        }
        scratchSize = (int) need;
    }

    /**
     * Creates a plan.
     *
     * @param rows the number of rows, at least 1
     * @param cols the number of columns, at least 1
     * @return the plan
     * @throws IllegalArgumentException if a size is out of range or the grid exceeds an array
     */
    public static ComplexFft2d plan(int rows, int cols) {
        if (rows < 1 || cols < 1 || (long) rows * cols > Integer.MAX_VALUE / 2) {
            throw new IllegalArgumentException("sizes out of range: " + rows + " x " + cols);
        }
        return new ComplexFft2d(rows, cols);
    }

    /** @return the number of rows */
    public int rows() {
        return rows;
    }

    /** @return the number of columns */
    public int cols() {
        return cols;
    }

    /** @return the number of doubles the scratch array needs */
    public int scratchSize() {
        return scratchSize;
    }

    /**
     * Transforms the grid in place.
     *
     * @param data the grid, {@code 2·rows·cols} doubles from {@code off}
     * @param off the offset of element {@code (0, 0)}
     * @param scratch caller-owned working memory of at least {@link #scratchSize()} doubles
     * @param scratchOff the offset into {@code scratch}
     */
    public void forward(double[] data, int off, double[] scratch, int scratchOff) {
        transform(data, off, scratch, scratchOff, false);
    }

    /**
     * Inverse-transforms the grid in place, dividing by {@code rows·cols}.
     *
     * @param data the grid, {@code 2·rows·cols} doubles from {@code off}
     * @param off the offset of element {@code (0, 0)}
     * @param scratch caller-owned working memory of at least {@link #scratchSize()} doubles
     * @param scratchOff the offset into {@code scratch}
     */
    public void inverse(double[] data, int off, double[] scratch, int scratchOff) {
        transform(data, off, scratch, scratchOff, true);
    }

    private void transform(double[] data, int off, double[] scratch, int scratchOff, boolean inv) {
        if (data == null || off < 0 || off > data.length - 2L * rows * cols) {
            throw new IllegalArgumentException("data must hold " + 2L * rows * cols + " doubles from offset " + off);
        }
        if (scratch == null || scratchOff < 0 || scratchOff > scratch.length - scratchSize) {
            throw new IllegalArgumentException("scratch must hold " + scratchSize + " doubles from offset "
                    + scratchOff);
        }
        int work = scratchOff + 2 * rows * tile;
        for (int r = 0; r < rows; r++) {
            if (inv) {
                rowPlan.inverse(data, off + 2 * r * cols, scratch, work);
            } else {
                rowPlan.forward(data, off + 2 * r * cols, scratch, work);
            }
        }
        for (int c0 = 0; c0 < cols; c0 += tile) {
            int w = java.lang.Math.min(tile, cols - c0);
            // gather columns c0 .. c0+w into w contiguous sequences
            for (int r = 0; r < rows; r++) {
                int src = off + 2 * (r * cols + c0);
                for (int c = 0; c < w; c++) {
                    int dst = scratchOff + 2 * (c * rows + r);
                    scratch[dst] = data[src + 2 * c];
                    scratch[dst + 1] = data[src + 2 * c + 1];
                }
            }
            for (int c = 0; c < w; c++) {
                if (inv) {
                    colPlan.inverse(scratch, scratchOff + 2 * c * rows, scratch, work);
                } else {
                    colPlan.forward(scratch, scratchOff + 2 * c * rows, scratch, work);
                }
            }
            for (int r = 0; r < rows; r++) {
                int dst = off + 2 * (r * cols + c0);
                for (int c = 0; c < w; c++) {
                    int src = scratchOff + 2 * (c * rows + r);
                    data[dst + 2 * c] = scratch[src];
                    data[dst + 2 * c + 1] = scratch[src + 1];
                }
            }
        }
    }
}
