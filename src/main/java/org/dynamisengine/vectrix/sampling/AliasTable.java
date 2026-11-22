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
package org.dynamisengine.vectrix.sampling;

/**
 * Walker-Vose alias table: draws an index {@code i} with probability {@code weights[i] / sum(weights)} in constant
 * time, for importance sampling discrete distributions such as environment-map pixels (weighted by luminance and
 * solid angle). Build is {@code O(n)} and reuses the table's arrays; sampling is allocation-free.
 *
 * @since 2.1.0
 */
public final class AliasTable {
    private final float[] probability;
    private final int[] alias;
    private final float[] pdf;
    private final int[] small;
    private final int[] large;
    private final double[] scaled;
    private final int size;

    /** A table for {@code size} outcomes; call {@link #build} before sampling. */
    public AliasTable(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        this.size = size;
        probability = new float[size];
        alias = new int[size];
        pdf = new float[size];
        small = new int[size];
        large = new int[size];
        scaled = new double[size];
    }

    /** Number of outcomes. */
    public int size() {
        return size;
    }

    /**
     * Builds the table from {@code weights} (length at least {@link #size()}, non-negative, finite, not all zero).
     *
     * @return {@code this}
     * @throws IllegalArgumentException if a weight is negative or not finite, or all weights are zero
     */
    public AliasTable build(float[] weights) {
        if (weights == null || weights.length < size) {
            throw new IllegalArgumentException("weights must hold size values");
        }
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            float w = weights[i];
            if (!(w >= 0.0f) || Float.isInfinite(w)) {
                throw new IllegalArgumentException("weight " + i + " is negative or not finite: " + w);
            }
            sum += w;
        }
        if (sum <= 0.0) {
            throw new IllegalArgumentException("all weights are zero");
        }
        int ns = 0;
        int nl = 0;
        for (int i = 0; i < size; i++) {
            pdf[i] = (float) (weights[i] / sum);
            scaled[i] = weights[i] * size / sum;
            if (scaled[i] < 1.0) {
                small[ns++] = i;
            } else {
                large[nl++] = i;
            }
        }
        while (ns > 0 && nl > 0) {
            int s = small[--ns];
            int l = large[--nl];
            probability[s] = (float) scaled[s];
            alias[s] = l;
            scaled[l] = (scaled[l] + scaled[s]) - 1.0;
            if (scaled[l] < 1.0) {
                small[ns++] = l;
            } else {
                large[nl++] = l;
            }
        }
        // rounding leaves the rest at (almost) exactly 1
        while (nl > 0) {
            int l = large[--nl];
            probability[l] = 1.0f;
            alias[l] = l;
        }
        while (ns > 0) {
            int s = small[--ns];
            probability[s] = 1.0f;
            alias[s] = s;
        }
        return this;
    }

    /** Draws an index from one uniform number {@code u} in {@code [0, 1)} (it picks the column and the coin flip). */
    public int sample(float u) {
        float x = u * size;
        int i = java.lang.Math.min((int) x, size - 1);
        float coin = x - i;
        return coin < probability[i] ? i : alias[i];
    }

    /** Probability of outcome {@code i}: {@code weights[i] / sum(weights)}. */
    public float pdf(int i) {
        return pdf[i];
    }
}
