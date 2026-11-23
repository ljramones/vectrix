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
 * Picks a token from a row of logits: greedy, or random with temperature, top-k and top-p (nucleus) filtering.
 * <p>
 * The randomness is the caller's uniform number {@code u} in [0, 1) (for example from
 * {@link org.dynamisengine.vectrix.hash.Philox}), so sampling is reproducible. Candidates are ranked by probability
 * with ties broken by the lower index, top-k keeps the first {@code k}, top-p the shortest prefix whose probability
 * reaches {@code p}, and {@code u} selects within the kept mass by cumulative probability in rank order. Nothing
 * allocates: the caller passes {@code n} floats and {@code n} ints of scratch.
 *
 * @since 2.5.0
 */
public final class Sampler {

    private Sampler() {
    }

    /**
     * Returns the index of the largest logit, the first of equal ones. NaN logits after the first element are
     * skipped. The offsets are not checked up front: a short array throws {@code ArrayIndexOutOfBoundsException}.
     *
     * @param logits the row of logits
     * @param off the offset of the row in {@code logits}
     * @param n the number of logits
     * @return the index relative to {@code off}, or -1 if {@code n <= 0}
     */
    public static int argmax(float[] logits, int off, int n) {
        int best = -1;
        float m = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            float v = logits[off + i];
            if (best < 0 || v > m) {
                best = i;
                m = v;
            }
        }
        return best;
    }

    /**
     * Samples a token. The probabilities are {@code softmax(logits / temperature)}; {@code topK} filtering is
     * applied first, then {@code topP} within the kept tokens.
     *
     * @param logits the row of logits
     * @param off the offset of the row in {@code logits}
     * @param n the number of logits, at least 1
     * @param temperature {@code <= 0} for greedy decoding; otherwise logits are divided by it
     * @param topK keep the {@code topK} most probable tokens ({@code <= 0} for all)
     * @param topP keep the smallest set whose probability reaches {@code topP} ({@code >= 1} for all)
     * @param u a uniform random number in [0, 1)
     * @param probs {@code n} floats of scratch
     * @param probsOff the offset in {@code probs}
     * @param order {@code n} ints of scratch
     * @param orderOff the offset in {@code order}
     * @return the chosen index, relative to {@code off}
     * @throws IllegalArgumentException if {@code n < 1} or an array is too short for its offset
     */
    public static int sample(float[] logits, int off, int n, float temperature, int topK, float topP, float u,
            float[] probs, int probsOff, int[] order, int orderOff) {
        if (n < 1 || logits == null || off < 0 || off > logits.length - n) {
            throw new IllegalArgumentException("logits must hold " + n + " values from offset " + off);
        }
        if (probs == null || probsOff < 0 || probsOff > probs.length - n || order == null || orderOff < 0
                || orderOff > order.length - n) {
            throw new IllegalArgumentException("scratch must hold " + n + " values");
        }
        if (!(temperature > 0f)) {
            return argmax(logits, off, n);
        }
        for (int i = 0; i < n; i++) {
            probs[probsOff + i] = logits[off + i] / temperature;
        }
        Softmax.softmax(probs, probsOff, probs, probsOff, n);
        int k = topK > 0 ? Math.min(topK, n) : n;
        selectTop(probs, probsOff, n, order, orderOff, k);
        // top-p: the shortest ranked prefix (within top-k) whose mass reaches topP
        int m = k;
        if (topP < 1f) {
            float cum = 0f;
            for (int j = 0; j < k; j++) {
                cum += probs[probsOff + order[orderOff + j]];
                if (cum >= topP) {
                    m = j + 1;
                    break;
                }
            }
        }
        float total = 0f;
        for (int j = 0; j < m; j++) {
            total += probs[probsOff + order[orderOff + j]];
        }
        float target = u * total;
        float cum = 0f;
        for (int j = 0; j < m; j++) {
            cum += probs[probsOff + order[orderOff + j]];
            if (target < cum) {
                return order[orderOff + j];
            }
        }
        return order[orderOff + m - 1];
    }

    /** {@code a} ranks before {@code b}: higher probability, then lower index. */
    private static boolean before(float[] p, int po, int a, int b) {
        float pa = p[po + a];
        float pb = p[po + b];
        return pa > pb || pa == pb && a < b;
    }

    /**
     * Puts the {@code k} best indices, in rank order, at {@code order[o .. o + k)}: a heap of the {@code k} best seen
     * so far (worst at the root), then heap sort of those.
     */
    static void selectTop(float[] p, int po, int n, int[] order, int o, int k) {
        for (int i = 0; i < k; i++) {
            order[o + i] = i;
        }
        for (int i = k / 2 - 1; i >= 0; i--) {
            siftDown(p, po, order, o, i, k);
        }
        for (int i = k; i < n; i++) {
            if (before(p, po, i, order[o])) {
                order[o] = i;
                siftDown(p, po, order, o, 0, k);
            }
        }
        // repeatedly move the worst of the heap to the end: ranks fill from the back
        for (int end = k - 1; end > 0; end--) {
            int t = order[o];
            order[o] = order[o + end];
            order[o + end] = t;
            siftDown(p, po, order, o, 0, end);
        }
    }

    /** Restores the heap property (the worst element at the root) below {@code i} in {@code order[o .. o + size)}. */
    private static void siftDown(float[] p, int po, int[] order, int o, int i, int size) {
        while (true) {
            int l = 2 * i + 1;
            if (l >= size) {
                return;
            }
            int w = l;
            int r = l + 1;
            if (r < size && before(p, po, order[o + l], order[o + r])) {
                w = r; // the worse child
            }
            if (before(p, po, order[o + w], order[o + i])) {
                return;
            }
            int t = order[o + i];
            order[o + i] = order[o + w];
            order[o + w] = t;
            i = w;
        }
    }
}
