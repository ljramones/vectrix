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
package org.dynamisengine.vectrix.compute;

import java.nio.ByteOrder;
import java.util.Map;
import org.dynamisengine.vectrix.nn.Embedding;
import org.dynamisengine.vectrix.nn.Pointwise;
import org.dynamisengine.vectrix.nn.Rope;

/** Reference models of the nn kernels added in 4.1: Rope, Pointwise, Embedding. */
final class NnModels {

    private NnModels() {
    }

    static void register(Map<String, ReferenceModels.ReferenceModel> m) {
        m.put("rope", NnModels::rope);
        m.put("pointwise", NnModels::pointwise);
        m.put("pointwise_inplace", NnModels::pointwiseInPlace);
        m.put("pointwise_self", NnModels::pointwiseSelf);
        m.put("embedding_gather", NnModels::gather);
        m.put("embedding_scatter_add", NnModels::scatterAdd);
    }

    private static void rope(ReferenceModels.Call c) {
        int half = c.i("half");
        int count = c.i("count");
        int group = c.i("group");
        c.requireLinearGrid(((long) count * half + 63) / 64);
        double[] f = new double[half];
        c.slots().get(1).duplicate().order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().get(f);
        Rope rope = Rope.plan(f, c.u("interleaved") != 0 ? Rope.Layout.INTERLEAVED : Rope.Layout.HALF);
        float[] x = c.floats(0);
        var pos = c.slots().get(2).duplicate().order(ByteOrder.LITTLE_ENDIAN);
        for (int v = 0; v < count; v++) {
            long position = pos.getLong((c.i("posOff") + v / group) * 8);
            int off = c.i("xOff") + v * c.i("ldx");
            if (c.u("backward") != 0) {
                rope.applyBackward(x, off, position);
            } else {
                rope.apply(x, off, position);
            }
        }
        c.put(0, x);
    }

    /** Applies Pointwise op {@code op} (GpuPointwise.Op order) to {@code n} elements. */
    static void apply(int op, float[] a, int aOff, float[] b, int bOff, float[] y, int yOff, int n) {
        switch (GpuPointwise.Op.values()[op]) {
            case EXP -> Pointwise.exp(a, aOff, y, yOff, n);
            case LOG -> Pointwise.log(a, aOff, y, yOff, n);
            case SQRT -> Pointwise.sqrt(a, aOff, y, yOff, n);
            case NEG -> Pointwise.neg(a, aOff, y, yOff, n);
            case SQUARE -> Pointwise.square(a, aOff, y, yOff, n);
            case ADD -> Pointwise.add(a, aOff, b, bOff, y, yOff, n);
            case SUB -> Pointwise.sub(a, aOff, b, bOff, y, yOff, n);
            case MUL -> Pointwise.mul(a, aOff, b, bOff, y, yOff, n);
            case DIV -> Pointwise.div(a, aOff, b, bOff, y, yOff, n);
            case MAX -> Pointwise.max(a, aOff, b, bOff, y, yOff, n);
            case MIN -> Pointwise.min(a, aOff, b, bOff, y, yOff, n);
        }
    }

    private static void pointwise(ReferenceModels.Call c) {
        int n = c.i("n");
        c.requireLinearGrid((n + 255L) / 256);
        float[] a = c.floats(0);
        float[] b = c.floats(1);
        float[] y = c.floats(2);
        apply(c.i("op"), a, c.i("aOff"), b, c.i("bOff"), y, c.i("destOff"), n);
        c.put(2, y);
    }

    private static void pointwiseInPlace(ReferenceModels.Call c) {
        int n = c.i("n");
        c.requireLinearGrid((n + 255L) / 256);
        c.require(GpuPointwise.Op.values()[c.i("op")].binary(), "the in-place kernel runs binary ops only");
        float[] dest = c.floats(0);
        float[] other = c.floats(1);
        int destOff = c.i("destOff");
        int otherOff = c.i("otherOff");
        if (c.u("destIsDy") != 0) {
            apply(c.i("op"), other, otherOff, dest, destOff, dest, destOff, n);
        } else {
            apply(c.i("op"), dest, destOff, other, otherOff, dest, destOff, n);
        }
        c.put(0, dest);
    }

    private static void pointwiseSelf(ReferenceModels.Call c) {
        int n = c.i("n");
        c.requireLinearGrid((n + 255L) / 256);
        float[] x = c.floats(0);
        int off = c.i("off");
        apply(c.i("op"), x, off, x, off, x, off, n);
        c.put(0, x);
    }

    private static void gather(ReferenceModels.Call c) {
        int rows = c.i("rows");
        int dim = c.i("dim");
        int count = c.i("count");
        c.requireLinearGrid(((long) count * dim + 255) / 256);
        float[] table = c.floats(0);
        int[] idx = c.ints(1);
        float[] out = c.floats(2);
        for (int t = 0; t < count; t++) {
            int r = idx[c.i("idxOff") + t];
            for (int d = 0; d < dim; d++) {
                out[c.i("outOff") + t * dim + d] = r >= 0 && r < rows ? table[c.i("tableOff") + r * dim + d]
                        : Float.NaN;
            }
        }
        c.put(2, out);
    }

    private static void scatterAdd(ReferenceModels.Call c) {
        int dim = c.i("dim");
        int unique = c.i("unique");
        c.requireLinearGrid(((long) unique * dim + 255) / 256);
        int[] plan = c.ints(1);
        int planOff = c.i("planOff");
        int count = plan[planOff + 2 * unique];
        int[] idx = new int[count];
        int rows = 0;
        for (int k = 0; k < unique; k++) {
            int row = plan[planOff + k];
            rows = Math.max(rows, row + 1);
            for (int j = plan[planOff + unique + k]; j < plan[planOff + unique + k + 1]; j++) {
                idx[plan[planOff + 2 * unique + 1 + j]] = row;
            }
        }
        float[] dOut = c.floats(0);
        float[] dTable = c.floats(2);
        Embedding.scatterAdd(dOut, c.i("dOutOff"), idx, 0, count, dim, dTable, c.i("tableOff"), rows);
        c.put(2, dTable);
    }
}
