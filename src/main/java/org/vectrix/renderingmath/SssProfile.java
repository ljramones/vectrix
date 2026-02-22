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
package org.vectrix.renderingmath;

/**
 * Sum-of-Gaussians diffusion profile for preintegrated SSS.
 * Arrays are flattened RGB per lobe: {@code [l0.r,l0.g,l0.b,l1.r,...]}.
 */
public final class SssProfile {
    private final int lobes;
    private final float[] weightsRgb;
    private final float[] variancesRgb;

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
     * Convenience helper for a single Gaussian with per-channel weights and variances.
     */
    public static SssProfile singleGaussian(float wr, float wg, float wb, float vr, float vg, float vb) {
        return new SssProfile(new float[] {wr, wg, wb}, new float[] {vr, vg, vb});
    }

    public int lobes() {
        return lobes;
    }

    /**
     * Evaluate diffusion profile at radial distance (millimeters).
     * Result is written to {@code destRgb[0..2]}.
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
        return (float) (weight * java.lang.Math.exp(-r2 / (2.0f * variance)) / (2.0f * java.lang.Math.PI * variance));
    }
}
