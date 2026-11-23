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
 * <p>
 * Threading: {@link #build} changes the table; once built, {@link #sample} and {@link #pdf} only read it and may be
 * called from any number of threads.
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

    /**
     * Creates a table for {@code size} outcomes and allocates its arrays. Call {@link #build} before sampling; until
     * then every outcome has probability 0 and {@link #sample} returns 0.
     *
     * @param size the number of outcomes, positive
     * @throws IllegalArgumentException if {@code size} is not positive
     */
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

    /**
     * Returns the number of outcomes.
     *
     * @return the number of outcomes given to the constructor
     */
    public int size() {
        return size;
    }

    /**
     * Builds the table from the first {@link #size()} entries of {@code weights}, replacing any earlier build. The
     * weights need not be normalized. Does not allocate.
     *
     * @param weights the non-negative, finite weights, not all zero; length at least {@link #size()}
     * @return {@code this}
     * @throws IllegalArgumentException if {@code weights} is null or shorter than {@link #size()}, a weight is negative
     *         or not finite, or all weights are zero
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

    /**
     * Draws an index from one uniform number {@code u}: {@code u * size} picks the column and its fraction is the coin
     * flip between the column and its alias. {@code u = 1} is accepted (a float rounded up from just below 1) and
     * draws from the last column.
     *
     * @param u the uniform variate, in {@code [0, 1]}
     * @return the drawn index, in {@code [0, size)}
     * @throws IllegalArgumentException if {@code u} is outside {@code [0, 1]} or NaN
     */
    public int sample(float u) {
        if (!(u >= 0.0f && u <= 1.0f)) {
            throw new IllegalArgumentException("u must be in [0, 1]: " + u);
        }
        float x = u * size;
        int i = java.lang.Math.min((int) x, size - 1);
        float coin = x - i;
        return coin < probability[i] ? i : alias[i];
    }

    /**
     * Returns the probability of outcome {@code i}, {@code weights[i] / sum(weights)}, from the last build.
     *
     * @param i the outcome, in {@code [0, size)}
     * @return the probability, in {@code [0, 1]}
     */
    public float pdf(int i) {
        return pdf[i];
    }
}
