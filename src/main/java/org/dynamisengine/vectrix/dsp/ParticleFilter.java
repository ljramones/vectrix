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
package org.dynamisengine.vectrix.dsp;

import org.dynamisengine.vectrix.core.ReproducibleMath;
import org.dynamisengine.vectrix.hash.Philox;

/**
 * Building blocks of a particle filter (sequential Monte Carlo): weight normalization in log space, the effective
 * sample size, systematic resampling, and weighted estimates.
 * <p>
 * A bootstrap filter step is: move every particle through the transition with process noise (draw it from
 * {@link org.dynamisengine.vectrix.hash.Philox} with an index derived from the step and particle, so the filter is
 * reproducible and parallel-safe); add each particle's measurement log-likelihood to its log-weight;
 * {@link #normalizeLogWeights}; and when {@link #effectiveSampleSize} falls below about half the particles,
 * {@link #systematicResample} with one uniform number (yours, or one it draws from {@code Philox} by seed and index)
 * and {@link #gather}. Exponentials and logarithms use
 * {@link org.dynamisengine.vectrix.core.ReproducibleMath}'s reproducible functions; nothing allocates.
 * <p>
 * All arrays are used from index 0, and particles are stored contiguously, {@code dim} doubles each. Only
 * {@link #normalizeLogWeights} validates its arguments; the other methods rely on Java's array bounds checks.
 *
 * @since 2.5.0
 */
public final class ParticleFilter {

    private ParticleFilter() {
    }

    /**
     * Normalizes log-weights into weights that sum to 1, without underflow (the maximum is subtracted first).
     *
     * Log-weights more than 700 below the maximum get the weight of {@code exp(-700)} relative to it.
     *
     * @param logW {@code count} log-weights
     * @param count the number of particles, at least 1
     * @param w receives {@code count} weights (may be {@code logW})
     * @return {@code log(Σ e^logW) - log(count)}: the step's log-evidence increment (log mean weight), useful for
     *         model comparison
     * @throws IllegalArgumentException if {@code count < 1} or every log-weight is negative infinity
     */
    public static double normalizeLogWeights(double[] logW, int count, double[] w) {
        if (count < 1) {
            throw new IllegalArgumentException("need at least one particle");
        }
        double mx = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            mx = Math.max(mx, logW[i]);
        }
        if (mx == Double.NEGATIVE_INFINITY) {
            throw new IllegalArgumentException("every particle has zero weight");
        }
        double sum = 0;
        for (int i = 0; i < count; i++) {
            double e = ReproducibleMath.exp(Math.max(logW[i] - mx, -700));
            w[i] = e;
            sum += e;
        }
        for (int i = 0; i < count; i++) {
            w[i] /= sum;
        }
        return mx + ReproducibleMath.log(sum) - ReproducibleMath.log((double) count);
    }

    /**
     * The effective sample size {@code 1 / Σ w²} of normalized weights, between 1 and {@code count}.
     *
     * @param w {@code count} normalized weights
     * @param count the number of particles
     * @return the effective number of particles
     */
    public static double effectiveSampleSize(double[] w, int count) {
        double s = 0;
        for (int i = 0; i < count; i++) {
            s = Math.fma(w[i], w[i], s);
        }
        return 1 / s;
    }

    /**
     * Systematic resampling: {@code count} indices drawn at the points {@code (j + u)/count} of the cumulative
     * weights. Each particle is chosen {@code ⌊count·w⌋} or {@code ⌈count·w⌉} times, with the lowest variance of the
     * standard schemes.
     *
     * @param w {@code count} normalized weights
     * @param count the number of particles, at least 1
     * @param u a uniform number in [0, 1)
     * @param indices receives {@code count} particle indices, ascending
     */
    public static void systematicResample(double[] w, int count, double u, int[] indices) {
        double cum = w[0];
        int i = 0;
        for (int j = 0; j < count; j++) {
            double point = (j + u) / count;
            while (point > cum && i < count - 1) {
                i++;
                cum += w[i];
            }
            indices[j] = i;
        }
    }

    /**
     * Systematic resampling with its uniform number drawn from {@link Philox}: the same as
     * {@link #systematicResample(double[], int, double, int[])} with {@code u = Philox.uniformDouble(seed, index)}.
     * Use a different {@code index} for each resampling, for example the step number, so the filter is reproducible
     * without a random generator to carry along.
     *
     * @param w {@code count} normalized weights
     * @param count the number of particles, at least 1
     * @param seed the seed of the {@link Philox} double stream
     * @param index the position in that stream
     * @param indices receives {@code count} particle indices, ascending
     * @since 3.0.0
     */
    public static void systematicResample(double[] w, int count, long seed, long index, int[] indices) {
        systematicResample(w, count, Philox.uniformDouble(seed, index), indices);
    }

    /**
     * Copies the chosen particles: {@code out[j] = particles[indices[j]]}, each particle {@code dim} doubles.
     * {@code out} must not overlap {@code particles}.
     *
     * @param particles the particles, {@code dim} doubles each
     * @param dim the number of doubles per particle
     * @param count the number of particles to copy
     * @param indices {@code count} indices into {@code particles}, as {@link #systematicResample} writes them
     * @param out receives {@code count·dim} doubles
     */
    public static void gather(double[] particles, int dim, int count, int[] indices, double[] out) {
        for (int j = 0; j < count; j++) {
            System.arraycopy(particles, indices[j] * dim, out, j * dim, dim);
        }
    }

    /**
     * The weighted mean of the particles ({@code dim} doubles each) into {@code mean}.
     *
     * @param particles the particles, {@code dim} doubles each
     * @param dim the number of doubles per particle
     * @param count the number of particles
     * @param w {@code count} weights, normally normalized
     * @param mean receives {@code dim} doubles; must not overlap {@code particles} or {@code w}
     */
    public static void mean(double[] particles, int dim, int count, double[] w, double[] mean) {
        for (int a = 0; a < dim; a++) {
            double v = 0;
            for (int i = 0; i < count; i++) {
                v = Math.fma(w[i], particles[i * dim + a], v);
            }
            mean[a] = v;
        }
    }
}
