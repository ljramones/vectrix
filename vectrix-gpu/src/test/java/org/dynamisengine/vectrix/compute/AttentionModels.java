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

import java.util.Map;

import org.dynamisengine.vectrix.blas.Blasf;
import org.dynamisengine.vectrix.blas.Gemmf;
import org.dynamisengine.vectrix.nn.Softmax;

/**
 * Reference models of the {@link GpuAttention} kernels: each runs the Vectrix pieces of {@code nn.Attention} the
 * kernel ports ({@code Blasf.dot}, {@code Softmax.softmax}, {@code Softmax.softmaxBackward}, the transposed
 * {@code Gemmf.gemv}, {@code Blasf.axpy}) over the attention rows the parameters describe, and checks that the
 * groups cover the work.
 */
final class AttentionModels {

    private static final int CHUNK = 64;

    private AttentionModels() {
    }

    static void register(Map<String, ReferenceModels.ReferenceModel> m) {
        m.put("attention_scores", AttentionModels::scores);
        m.put("attention_softmax", AttentionModels::softmax);
        m.put("attention_softmax_backward", AttentionModels::softmaxBackward);
        m.put("attention_mix", AttentionModels::mix);
        m.put("attention_accumulate", AttentionModels::accumulate);
    }

    /** The keys query {@code r} attends to (attention_common.slang's keysFor). */
    private static int keys(ReferenceModels.Call c, int r) {
        return c.u("causal") != 0 ? c.i("t") - c.i("queries") + 1 + r : c.i("t");
    }

    private static void requireChunks(ReferenceModels.Call c, int n) {
        c.require((long) c.i("chunks") * CHUNK >= n, "chunks " + c.i("chunks") + " do not cover " + n);
    }

    private static void scores(ReferenceModels.Call c) {
        int rows = c.i("rows");
        int heads = c.i("heads");
        int width = c.i("width");
        requireChunks(c, c.i("t"));
        c.requireLinearGrid((long) rows * c.i("chunks"));
        float[] x = c.floats(0);
        float[] m = c.floats(1);
        float[] mask = c.floats(2);
        float[] out = c.floats(3);
        for (int row = 0; row < rows; row++) {
            int r = row / heads;
            int g = row % heads / c.i("group");
            for (int j = 0; j < keys(c, r); j++) {
                float d = Blasf.dot(x, c.i("xOff") + row * c.i("ldx"), m, c.i("mOff") + j * c.i("ldm") + g * width,
                        width);
                float s = c.u("scaled") != 0 ? c.f("scale") * d : d;
                out[c.i("outOff") + row * c.i("ldOut") + j] = c.u("masked") != 0
                        ? s + mask[c.i("maskOff") + r * c.i("ldMask") + j] : s;
            }
        }
        c.put(3, out);
    }

    private static void softmax(ReferenceModels.Call c) {
        int rows = c.i("rows");
        c.requireLinearGrid(rows);
        float[] p = c.floats(0);
        for (int row = 0; row < rows; row++) {
            int base = c.i("pOff") + row * c.i("ldp");
            Softmax.softmax(p, base, p, base, keys(c, row / c.i("heads")));
        }
        c.put(0, p);
    }

    private static void softmaxBackward(ReferenceModels.Call c) {
        int rows = c.i("rows");
        c.requireLinearGrid(rows);
        float[] p = c.floats(0);
        float[] ds = c.floats(1);
        for (int row = 0; row < rows; row++) {
            int ds0 = c.i("dsOff") + row * c.i("ldds");
            Softmax.softmaxBackward(p, c.i("pOff") + row * c.i("ldp"), ds, ds0, ds, ds0, keys(c, row / c.i("heads")));
        }
        c.put(1, ds);
    }

    private static void mix(ReferenceModels.Call c) {
        int rows = c.i("rows");
        int heads = c.i("heads");
        int width = c.i("width");
        requireChunks(c, width);
        c.requireLinearGrid((long) rows * c.i("chunks"));
        float[] m = c.floats(0);
        float[] w = c.floats(1);
        float[] out = c.floats(2);
        float[] scratch = new float[c.i("t")];
        for (int row = 0; row < rows; row++) {
            int g = row % heads / c.i("group");
            Gemmf.gemv(true, width, keys(c, row / heads), c.f("alpha"), m, c.i("mOff") + g * width, c.i("ldm"), w,
                    c.i("wOff") + row * c.i("ldw"), 0f, out, c.i("outOff") + row * c.i("ldOut"), scratch, 0);
        }
        c.put(2, out);
    }

    private static void accumulate(ReferenceModels.Call c) {
        int heads = c.i("heads");
        int width = c.i("width");
        requireChunks(c, width);
        c.requireLinearGrid((long) c.i("t") * c.i("kvHeads") * c.i("chunks"));
        float[] w = c.floats(0);
        float[] x = c.floats(1);
        float[] dest = c.floats(2);
        for (int r = 0; r < c.i("queries"); r++) {
            for (int h = 0; h < heads; h++) {
                int row = r * heads + h;
                int g = h / c.i("group");
                for (int j = 0; j < keys(c, r); j++) {
                    float wv = w[c.i("wOff") + row * c.i("ldw") + j];
                    float a = c.u("scaled") != 0 ? c.f("scale") * wv : wv;
                    Blasf.axpy(a, x, c.i("xOff") + row * c.i("ldx"), dest,
                            c.i("destOff") + j * c.i("ldDest") + g * width, width);
                }
            }
        }
        c.put(2, dest);
    }
}
