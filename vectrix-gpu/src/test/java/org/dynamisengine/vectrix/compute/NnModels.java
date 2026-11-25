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
import org.dynamisengine.vectrix.nn.Rope;

/** Reference models of the nn kernels added in 4.1: Rope. */
final class NnModels {

    private NnModels() {
    }

    static void register(Map<String, ReferenceModels.ReferenceModel> m) {
        m.put("rope", NnModels::rope);
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
}
