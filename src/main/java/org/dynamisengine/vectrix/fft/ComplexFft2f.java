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
 * A precomputed plan for the 2D discrete Fourier transform of a {@code rows × cols} grid of complex floats, any
 * sizes; the single-precision twin of {@link ComplexFft2d}. Data is row-major and interleaved: element
 * {@code (r, c)} is at {@code off + 2·(r·cols + c)}.
 * <p>
 * {@link #forward} is unscaled; {@link #inverse} divides by {@code rows·cols}. The transform is separable: every
 * row, then every column, with {@link ComplexFftf} plans; columns are processed 16 at a time, gathered into
 * scratch. Accuracy, thread safety and determinism are those of {@link ComplexFftf}.
 *
 * @since 2.5.0
 */
public final class ComplexFft2f {

    private static final int TILE = 16;

    private final int rows;
    private final int cols;
    private final ComplexFftf rowPlan;
    private final ComplexFftf colPlan;
    private final int tile;
    private final int scratchSize;

    private ComplexFft2f(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        rowPlan = ComplexFftf.plan(cols);
        colPlan = ComplexFftf.plan(rows);
        tile = java.lang.Math.min(TILE, cols);
        long need = 2L * rows * tile + java.lang.Math.max(rowPlan.scratchSize(), colPlan.scratchSize());
        if (need > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("grid too large: " + rows + " x " + cols);
        }
        scratchSize = (int) need;
    }

    /**
     * Creates a plan, with row and column transform plans for these sizes. Allocates the plans' tables.
     *
     * @param rows the number of rows, at least 1 and at most {@link ComplexFftf#MAX_SIZE}
     * @param cols the number of columns, at least 1 and at most {@link ComplexFftf#MAX_SIZE}
     * @return the plan
     * @throws IllegalArgumentException if a size is out of range, the grid or the scratch exceeds an array, or a
     *         size cannot be planned by {@link ComplexFftf#plan}
     */
    public static ComplexFft2f plan(int rows, int cols) {
        if (rows < 1 || cols < 1 || (long) rows * cols > Integer.MAX_VALUE / 2) {
            throw new IllegalArgumentException("sizes out of range: " + rows + " x " + cols);
        }
        return new ComplexFft2f(rows, cols);
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
     * Returns the number of floats the scratch array needs.
     *
     * @return the number of floats the scratch array needs
     */
    public int scratchSize() {
        return scratchSize;
    }

    /**
     * Transforms the grid in place.
     *
     * @param data the grid, {@code 2·rows·cols} floats from {@code off}
     * @param off the offset of element {@code (0, 0)}
     * @param scratch caller-owned working memory of at least {@link #scratchSize()} floats
     * @param scratchOff the offset into {@code scratch}; the scratch must not overlap {@code data}
     * @throws IllegalArgumentException if {@code data} or {@code scratch} is null or too short from its offset;
     *         nothing is written then
     */
    public void forward(float[] data, int off, float[] scratch, int scratchOff) {
        transform(data, off, scratch, scratchOff, false);
    }

    /**
     * Inverse-transforms the grid in place, dividing by {@code rows·cols}.
     *
     * @param data the grid, {@code 2·rows·cols} floats from {@code off}
     * @param off the offset of element {@code (0, 0)}
     * @param scratch caller-owned working memory of at least {@link #scratchSize()} floats
     * @param scratchOff the offset into {@code scratch}; the scratch must not overlap {@code data}
     * @throws IllegalArgumentException if {@code data} or {@code scratch} is null or too short from its offset;
     *         nothing is written then
     */
    public void inverse(float[] data, int off, float[] scratch, int scratchOff) {
        transform(data, off, scratch, scratchOff, true);
    }

    private void transform(float[] data, int off, float[] scratch, int scratchOff, boolean inv) {
        if (data == null || off < 0 || off > data.length - 2L * rows * cols) {
            throw new IllegalArgumentException("data must hold " + 2L * rows * cols + " floats from offset " + off);
        }
        if (scratch == null || scratchOff < 0 || scratchOff > scratch.length - scratchSize) {
            throw new IllegalArgumentException("scratch must hold " + scratchSize + " floats from offset "
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
