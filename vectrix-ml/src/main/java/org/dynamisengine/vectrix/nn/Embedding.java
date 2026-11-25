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
 * Embedding lookup and its gradient: rows of a table selected by integer indices, and the gradient summed back into
 * the table rows in a fixed order.
 * <p>
 * The table is row-major, {@code rows × dim}. {@link #gather} copies row {@code idx[t]} to output row {@code t};
 * {@link #scatterAdd} adds output-gradient row {@code t} into table-gradient row {@code idx[t]} for {@code t} in
 * increasing order, so a row selected several times sums its gradients in token order: the same bits every run, on
 * every platform, and on the GPU twin in vectrix-gpu ({@code GpuEmbedding}). Indices are validated before anything is
 * written. Nothing allocates.
 *
 * @since 4.1.0
 */
public final class Embedding {

    private Embedding() {
    }

    /**
     * Copies table row {@code idx[idxOff + t]} to output row {@code t}, for {@code t} in {@code [0, count)}.
     *
     * @param table the table, {@code rows × dim}, row-major
     * @param tableOff the offset of the table in {@code table}
     * @param rows the number of table rows
     * @param dim the row length
     * @param idx the row indices
     * @param idxOff the offset in {@code idx}
     * @param count the number of indices
     * @param out receives {@code count × dim} values; must not overlap the table
     * @param outOff the offset in {@code out}
     * @throws IllegalArgumentException if a size is negative, an array is too short for its offset, or an index is
     *         outside {@code [0, rows)}
     */
    public static void gather(float[] table, int tableOff, int rows, int dim, int[] idx, int idxOff, int count,
            float[] out, int outOff) {
        check(table, tableOff, rows, dim, idx, idxOff, count, out, outOff, "out");
        for (int t = 0; t < count; t++) {
            System.arraycopy(table, tableOff + idx[idxOff + t] * dim, out, outOff + t * dim, dim);
        }
    }

    /**
     * Adds gradient row {@code t} into table-gradient row {@code idx[idxOff + t]}, for {@code t} in increasing order:
     * {@code dTable[idx[t]][d] += dOut[t][d]}. Repeated indices sum in token order.
     *
     * @param dOut the output gradient, {@code count × dim}
     * @param dOutOff the offset in {@code dOut}
     * @param idx the row indices of the forward gather
     * @param idxOff the offset in {@code idx}
     * @param count the number of indices
     * @param dim the row length
     * @param dTable the table gradient, {@code rows × dim}, accumulated into; must not overlap {@code dOut}
     * @param tableOff the offset of the table gradient in {@code dTable}
     * @param rows the number of table rows
     * @throws IllegalArgumentException if a size is negative, an array is too short for its offset, or an index is
     *         outside {@code [0, rows)} (then nothing is written)
     */
    public static void scatterAdd(float[] dOut, int dOutOff, int[] idx, int idxOff, int count, int dim,
            float[] dTable, int tableOff, int rows) {
        check(dTable, tableOff, rows, dim, idx, idxOff, count, dOut, dOutOff, "dOut");
        for (int t = 0; t < count; t++) {
            int row = tableOff + idx[idxOff + t] * dim;
            int src = dOutOff + t * dim;
            for (int d = 0; d < dim; d++) {
                dTable[row + d] += dOut[src + d];
            }
        }
    }

    private static void check(float[] table, int tableOff, int rows, int dim, int[] idx, int idxOff, int count,
            float[] rowsArray, int rowsOff, String rowsName) {
        if (rows < 0 || dim < 0 || count < 0) {
            throw new IllegalArgumentException("rows, dim and count must be >= 0");
        }
        range(table == null ? -1 : table.length, tableOff, (long) rows * dim, "table");
        range(idx == null ? -1 : idx.length, idxOff, count, "idx");
        range(rowsArray == null ? -1 : rowsArray.length, rowsOff, (long) count * dim, rowsName);
        for (int t = 0; t < count; t++) {
            int r = idx[idxOff + t];
            if (r < 0 || r >= rows) {
                throw new IllegalArgumentException("index " + r + " at " + t + " is outside [0, " + rows + ")");
            }
        }
    }

    private static void range(int length, int off, long n, String name) {
        if (length < 0 || off < 0 || off > length - n) {
            throw new IllegalArgumentException(name + " must hold " + n + " values from offset " + off);
        }
    }
}
