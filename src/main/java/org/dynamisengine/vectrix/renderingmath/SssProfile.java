/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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
package org.dynamisengine.vectrix.renderingmath;

/**
 * Sum-of-Gaussians diffusion profile for preintegrated SSS.
 * Arrays are flattened RGB per lobe: {@code [l0.r,l0.g,l0.b,l1.r,...]}. Instances are immutable and safe to share
 * between threads.
  * @since 1.0.0
  */
public final class SssProfile {
    private final int lobes;
    private final float[] weightsRgb;
    private final float[] variancesRgb;

    /**
     * Creates a profile from per-lobe RGB weights and variances. The arrays are copied. Lobe {@code l} is
     * {@code w · exp(−r² / (2 v)) / (2π v)} per channel, a normalized 2D Gaussian scaled by its weight.
     *
     * @param weightsRgb the weights, three per lobe ({@code [l0.r, l0.g, l0.b, l1.r, ...]})
     * @param variancesRgb the variances, laid out like {@code weightsRgb}, each greater than 0, in the square of
     *                     the radius unit (mm² for radii in millimeters)
     * @throws IllegalArgumentException if an array is null or empty, the lengths differ or are not a multiple of
     *         3, or a variance is not greater than 0
     */
    public SssProfile(float[] weightsRgb, float[] variancesRgb) {
        if (weightsRgb == null || variancesRgb == null || weightsRgb.length == 0 || weightsRgb.length != variancesRgb.length || (weightsRgb.length % 3) != 0) {
            throw new IllegalArgumentException("profile arrays");
        }
        this.lobes = weightsRgb.length / 3;
        this.weightsRgb = weightsRgb.clone();
        this.variancesRgb = variancesRgb.clone();
        for (int i = 0; i < this.variancesRgb.length; i++) {
            if (this.variancesRgb[i] <= 0.0f) {
                throw new IllegalArgumentException("variances must be positive");
            }
        }
    }

    /**
     * Creates a profile of a single Gaussian lobe with per-channel weights and variances.
     *
     * @param wr the red weight
     * @param wg the green weight
     * @param wb the blue weight
     * @param vr the red variance, greater than 0
     * @param vg the green variance, greater than 0
     * @param vb the blue variance, greater than 0
     * @return the new profile
     * @throws IllegalArgumentException if a variance is not greater than 0
     */
    public static SssProfile singleGaussian(float wr, float wg, float wb, float vr, float vg, float vb) {
        return new SssProfile(new float[] {wr, wg, wb}, new float[] {vr, vg, vb});
    }

    /**
     * Returns the number of Gaussian lobes.
     *
     * @return the lobe count, at least 1
     */
    public int lobes() {
        return lobes;
    }

    /**
     * Evaluates the diffusion profile, the sum of all lobes, at a radial distance. Does not allocate.
     *
     * @param radius the radial distance (millimeters by convention, matching the variances); negative values
     *               count as 0
     * @param destRgb receives the red, green and blue values at indices 0..2
     * @throws IllegalArgumentException if {@code destRgb} is null or shorter than 3
     */
    public void evaluate(float radius, float[] destRgb) {
        if (destRgb == null || destRgb.length < 3) {
            throw new IllegalArgumentException("destRgb");
        }
        float r = java.lang.Math.max(0.0f, radius);
        float r2 = r * r;
        float outR = 0.0f;
        float outG = 0.0f;
        float outB = 0.0f;
        for (int l = 0; l < lobes; l++) {
            int o = l * 3;
            float vr = variancesRgb[o];
            float vg = variancesRgb[o + 1];
            float vb = variancesRgb[o + 2];
            outR += gaussian(r2, weightsRgb[o], vr);
            outG += gaussian(r2, weightsRgb[o + 1], vg);
            outB += gaussian(r2, weightsRgb[o + 2], vb);
        }
        destRgb[0] = outR;
        destRgb[1] = outG;
        destRgb[2] = outB;
    }

    private static float gaussian(float r2, float weight, float variance) {
        return (float) (weight * org.dynamisengine.vectrix.core.AccurateMath.exp(-r2 / (2.0f * variance)) / (2.0f * java.lang.Math.PI * variance));
    }
}
