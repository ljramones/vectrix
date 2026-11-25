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
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.ReproducibleMath;
import org.dynamisengine.vectrix.nn.Attention;
import org.dynamisengine.vectrix.nn.Embedding;
import org.dynamisengine.vectrix.nn.Pointwise;
import org.dynamisengine.vectrix.nn.Softmax;
import org.junit.jupiter.api.Test;

/** Pointwise, Embedding and the fully masked row rule of Softmax (4.1.0). */
class PointwiseEmbeddingTest {

    private static final float[] SPECIAL = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -0f, 1f,
        -1f, Float.MIN_VALUE, Float.MAX_VALUE, 0.5f, 3f};

    private static float[] values(SplittableRandom r, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = r.nextInt(8) == 0 ? SPECIAL[r.nextInt(SPECIAL.length)] : (float) (r.nextDouble() * 20 - 10);
        }
        return a;
    }

    private static void sameBits(float[] expected, float[] actual) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(Float.floatToIntBits(expected[i]), Float.floatToIntBits(actual[i]), "at " + i);
        }
    }

    @Test
    void pointwiseIsOneJavaOperationPerElement() {
        SplittableRandom r = new SplittableRandom(41);
        int n = 333;
        float[] a = values(r, n + 2);
        float[] b = values(r, n + 3);
        float[] y = new float[n + 1];
        float[] e = new float[n + 1];
        Pointwise.add(a, 2, b, 3, y, 1, n);
        for (int i = 0; i < n; i++) {
            e[1 + i] = a[2 + i] + b[3 + i];
        }
        sameBits(e, y);
        Pointwise.sub(a, 2, b, 3, y, 1, n);
        for (int i = 0; i < n; i++) {
            e[1 + i] = a[2 + i] - b[3 + i];
        }
        sameBits(e, y);
        Pointwise.mul(a, 2, b, 3, y, 1, n);
        for (int i = 0; i < n; i++) {
            e[1 + i] = a[2 + i] * b[3 + i];
        }
        sameBits(e, y);
        Pointwise.div(a, 2, b, 3, y, 1, n);
        for (int i = 0; i < n; i++) {
            e[1 + i] = a[2 + i] / b[3 + i];
        }
        sameBits(e, y);
        Pointwise.max(a, 2, b, 3, y, 1, n);
        for (int i = 0; i < n; i++) {
            e[1 + i] = Math.max(a[2 + i], b[3 + i]);
        }
        sameBits(e, y);
        Pointwise.min(a, 2, b, 3, y, 1, n);
        for (int i = 0; i < n; i++) {
            e[1 + i] = Math.min(a[2 + i], b[3 + i]);
        }
        sameBits(e, y);
        Pointwise.exp(a, 2, y, 1, n);
        for (int i = 0; i < n; i++) {
            e[1 + i] = ReproducibleMath.exp(a[2 + i]);
        }
        sameBits(e, y);
        Pointwise.log(a, 2, y, 1, n);
        for (int i = 0; i < n; i++) {
            e[1 + i] = ReproducibleMath.log(a[2 + i]);
        }
        sameBits(e, y);
        Pointwise.sqrt(a, 2, y, 1, n);
        for (int i = 0; i < n; i++) {
            e[1 + i] = (float) Math.sqrt(a[2 + i]);
        }
        sameBits(e, y);
        Pointwise.neg(a, 2, y, 1, n);
        for (int i = 0; i < n; i++) {
            e[1 + i] = -a[2 + i];
        }
        sameBits(e, y);
        Pointwise.square(a, 2, y, 1, n);
        for (int i = 0; i < n; i++) {
            e[1 + i] = a[2 + i] * a[2 + i];
        }
        sameBits(e, y);
    }

    @Test
    void pointwiseRunsInPlace() {
        float[] a = {1f, 2f, 3f};
        float[] b = {4f, 5f, 6f};
        Pointwise.add(a, 0, b, 0, a, 0, 3);
        assertArrayEquals(new float[] {5f, 7f, 9f}, a);
        Pointwise.mul(a, 0, b, 0, b, 0, 3);
        assertArrayEquals(new float[] {20f, 35f, 54f}, b);
        Pointwise.neg(b, 0, b, 0, 3);
        assertArrayEquals(new float[] {-20f, -35f, -54f}, b);
        assertThrows(IllegalArgumentException.class, () -> Pointwise.add(a, 1, b, 0, a, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> Pointwise.exp(a, 0, b, 0, -1));
    }

    @Test
    void gatherCopiesRows() {
        float[] table = new float[2 + 5 * 3];
        for (int i = 0; i < table.length; i++) {
            table[i] = i;
        }
        int[] idx = {9, 4, 0, 4, 2};
        float[] out = new float[1 + 4 * 3];
        Embedding.gather(table, 2, 5, 3, idx, 1, 4, out, 1);
        assertArrayEquals(new float[] {0, 14, 15, 16, 2, 3, 4, 14, 15, 16, 8, 9, 10}, out);
        assertThrows(IllegalArgumentException.class, () -> Embedding.gather(table, 2, 5, 3, idx, 0, 4, out, 1));
        assertThrows(IllegalArgumentException.class, () -> Embedding.gather(table, 2, 5, 3, idx, 1, 5, out, 1));
    }

    @Test
    void scatterAddSumsRepeatedRowsInTokenOrder() {
        SplittableRandom r = new SplittableRandom(7);
        int rows = 6;
        int dim = 5;
        int count = 40;
        int[] idx = new int[count];
        for (int t = 0; t < count; t++) {
            idx[t] = r.nextInt(3) == 0 ? 2 : r.nextInt(rows);
        }
        float[] dOut = values(r, count * dim);
        float[] dTable = values(r, rows * dim);
        float[] expected = dTable.clone();
        for (int row = 0; row < rows; row++) {
            for (int d = 0; d < dim; d++) {
                float acc = expected[row * dim + d];
                for (int t = 0; t < count; t++) {
                    if (idx[t] == row) {
                        acc = acc + dOut[t * dim + d];
                    }
                }
                expected[row * dim + d] = acc;
            }
        }
        Embedding.scatterAdd(dOut, 0, idx, 0, count, dim, dTable, 0, rows);
        sameBits(expected, dTable);
        float[] before = dTable.clone();
        idx[count - 1] = rows;
        assertThrows(IllegalArgumentException.class, () -> Embedding.scatterAdd(dOut, 0, idx, 0, count, dim, dTable,
                0, rows));
        assertArrayEquals(before, dTable, "an invalid index writes nothing");
    }

    @Test
    void fullyMaskedRowsGiveZeroProbabilities() {
        float inf = Float.NEGATIVE_INFINITY;
        float[] x = {inf, inf, inf};
        float[] y = new float[3];
        Softmax.softmax(x, 0, y, 0, 3);
        assertArrayEquals(new float[] {0f, 0f, 0f}, y);
        Softmax.logSoftmax(x, 0, y, 0, 3, new float[3], 0);
        assertArrayEquals(new float[] {inf, inf, inf}, y);
        Softmax.softmax(new float[] {inf, 0f, inf}, 0, y, 0, 3);
        assertArrayEquals(new float[] {0f, 1f, 0f}, y);
        // attention with every key masked: zero probabilities and zero output
        int t = 4;
        int d = 3;
        float[] q = {1f, 2f, 3f};
        float[] k = new float[t * d];
        float[] v = new float[t * d];
        Arrays.fill(k, 0.5f);
        Arrays.fill(v, 2f);
        float[] mask = new float[t];
        Arrays.fill(mask, inf);
        float[] p = new float[t];
        float[] out = new float[d];
        Arrays.fill(out, 9f);
        Attention.forward(q, 0, d, k, 0, d, v, 0, d, d, t, 1f, mask, 0, p, 0, out, 0, new float[64], 0);
        assertArrayEquals(new float[t], p);
        assertArrayEquals(new float[d], out);
    }
}
