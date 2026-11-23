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

import org.dynamisengine.vectrix.blas.Blasf;
import org.dynamisengine.vectrix.blas.Gemmf;

/**
 * Scaled dot-product attention of one query against {@code t} cached keys and values, forward and backward.
 * <p>
 * {@code out = Σ_j p_j·V_j} with {@code p = softmax(scale·q·K_jᵀ + mask_j)}. Scores are {@link Blasf#dot}s, the
 * softmax is {@link Softmax}'s, and the weighted sum is a transposed {@link Gemmf#gemv}, so every number is computed
 * in a documented order and the result does not depend on how queries, heads or positions are batched or split
 * across threads. Causal attention passes the keys up to the query's position. Keys and values are row-major
 * {@code t×d} and {@code t×dv} with row strides; the probabilities are left in {@code p} for the backward pass.
 * Nothing allocates.
 *
 * @since 2.5.0
 */
public final class Attention {

    private Attention() {
    }

    /**
     * Forward pass.
     *
     * @param q the query, {@code d} floats
     * @param k the keys, {@code t} rows of {@code d} floats with stride {@code ldk}
     * @param v the values, {@code t} rows of {@code dv} floats with stride {@code ldv}
     * @param t the number of keys, at least 1
     * @param scale usually {@code 1/√d}
     * @param mask {@code t} additive biases (for example {@code -∞} for excluded keys), or {@code null}
     * @param p receives the {@code t} attention probabilities
     * @param out receives {@code dv} floats
     * @param scratch {@code t} floats of working memory
     */
    public static void forward(float[] q, int qOff, int d, float[] k, int kOff, int ldk, float[] v, int vOff, int ldv,
            int dv, int t, float scale, float[] mask, int maskOff, float[] p, int pOff, float[] out, int outOff,
            float[] scratch, int scratchOff) {
        if (t < 1 || d < 1 || dv < 1) {
            throw new IllegalArgumentException("need t, d, dv >= 1");
        }
        range(p, pOff, t, "p");
        range(out, outOff, dv, "out");
        range(scratch, scratchOff, t, "scratch");
        if (mask != null) {
            range(mask, maskOff, t, "mask");
        }
        for (int j = 0; j < t; j++) {
            float s = scale * Blasf.dot(q, qOff, k, kOff + j * ldk, d);
            p[pOff + j] = mask == null ? s : s + mask[maskOff + j];
        }
        Softmax.softmax(p, pOff, p, pOff, t);
        // out = Vᵀ·p: element c is the dot of column c of V with p
        Gemmf.gemv(true, dv, t, 1f, v, vOff, ldv, p, pOff, 0f, out, outOff, scratch, scratchOff);
    }

    /**
     * Backward pass, from the forward probabilities {@code p}: writes {@code dq} and <i>accumulates</i> {@code dk}
     * and {@code dv} (keys and values are shared by all queries):
     * {@code dV_j += p_j·dOut}, {@code dp_j = dOut·V_j}, {@code ds = softmax'(p)·dp},
     * {@code dq = scale·Σ ds_j·K_j}, {@code dK_j += scale·ds_j·q}.
     *
     * @param scratch {@code 2t} floats of working memory
     */
    public static void backward(float[] q, int qOff, int d, float[] k, int kOff, int ldk, float[] v, int vOff, int ldv,
            int dv, int t, float scale, float[] p, int pOff, float[] dOut, int dOutOff, float[] dq, int dqOff,
            float[] dk, int dkOff, float[] dV, int dvOff, float[] scratch, int scratchOff) {
        if (t < 1 || d < 1 || dv < 1) {
            throw new IllegalArgumentException("need t, d, dv >= 1");
        }
        range(scratch, scratchOff, 2 * t, "scratch");
        range(dq, dqOff, d, "dq");
        int ds = scratchOff;
        for (int j = 0; j < t; j++) {
            float pj = p[pOff + j];
            Blasf.axpy(pj, dOut, dOutOff, dV, dvOff + j * ldv, dv);
            scratch[ds + j] = Blasf.dot(dOut, dOutOff, v, vOff + j * ldv, dv);
        }
        Softmax.softmaxBackward(p, pOff, scratch, ds, scratch, ds, t);
        // dq = scale·Kᵀ·ds, a transposed gemv
        Gemmf.gemv(true, d, t, scale, k, kOff, ldk, scratch, ds, 0f, dq, dqOff, scratch, scratchOff + t);
        for (int j = 0; j < t; j++) {
            Blasf.axpy(scale * scratch[ds + j], q, qOff, dk, dkOff + j * ldk, d);
        }
    }

    /**
     * Grouped-query attention for one position: {@code heads} query heads share {@code kvHeads} key/value heads,
     * query head {@code h} using key/value head {@code h / (heads / kvHeads)}. {@code kvHeads = 1} is multi-query
     * attention, {@code kvHeads = heads} ordinary multi-head attention. Each head is exactly {@link #forward}.
     * <p>
     * Layouts: {@code q} is {@code heads × d}; each cache row (one position) holds every key/value head,
     * {@code kvHeads × d} keys and {@code kvHeads × dv} values; {@code out} is {@code heads × dv}; {@code p} receives
     * {@code heads × t} probabilities for the backward pass. For causal self-attention over a sequence, call this
     * for each position with {@code t = position + 1}.
     *
     * @param scratch {@code t} floats of working memory
     */
    public static void groupedQuery(float[] q, int qOff, int heads, int d, float[] kCache, int kOff, float[] vCache,
            int vOff, int kvHeads, int dv, int t, float scale, float[] mask, int maskOff, float[] p, int pOff, float[] out,
            int outOff, float[] scratch, int scratchOff) {
        int group = groupSize(heads, kvHeads);
        for (int h = 0; h < heads; h++) {
            int g = h / group;
            forward(q, qOff + h * d, d, kCache, kOff + g * d, kvHeads * d, vCache, vOff + g * dv, kvHeads * dv, dv, t,
                    scale, mask, maskOff, p, pOff + h * t, out, outOff + h * dv, scratch, scratchOff);
        }
    }

    /**
     * Backward pass of {@link #groupedQuery}: writes {@code dq} ({@code heads × d}) and accumulates the key and value
     * gradients into {@code dk} and {@code dV}, laid out like the caches; query heads that share a key/value head add
     * into it in head order.
     *
     * @param scratch {@code 2t} floats of working memory
     */
    public static void groupedQueryBackward(float[] q, int qOff, int heads, int d, float[] kCache, int kOff,
            float[] vCache, int vOff, int kvHeads, int dv, int t, float scale, float[] p, int pOff, float[] dOut,
            int dOutOff, float[] dq, int dqOff, float[] dk, int dkOff, float[] dV, int dvOff, float[] scratch,
            int scratchOff) {
        int group = groupSize(heads, kvHeads);
        for (int h = 0; h < heads; h++) {
            int g = h / group;
            backward(q, qOff + h * d, d, kCache, kOff + g * d, kvHeads * d, vCache, vOff + g * dv, kvHeads * dv, dv, t,
                    scale, p, pOff + h * t, dOut, dOutOff + h * dv, dq, dqOff + h * d, dk, dkOff + g * d, dV,
                    dvOff + g * dv, scratch, scratchOff);
        }
    }

    private static int groupSize(int heads, int kvHeads) {
        if (heads < 1 || kvHeads < 1 || heads % kvHeads != 0) {
            throw new IllegalArgumentException("heads must be a positive multiple of kvHeads: " + heads + ", "
                    + kvHeads);
        }
        return heads / kvHeads;
    }

    private static void range(float[] a, int off, int n, String name) {
        if (a == null || off < 0 || off > a.length - n) {
            throw new IllegalArgumentException(name + " must hold " + n + " values from offset " + off);
        }
    }
}
