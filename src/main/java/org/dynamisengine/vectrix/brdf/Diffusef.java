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
package org.dynamisengine.vectrix.brdf;

/**
 * Diffuse BRDF lobes (single precision): Lambert, Burley (Disney) and Oren-Nayar. Each returns the BRDF value to
 * multiply by the albedo; cosines are dot products of unit vectors with the surface normal ({@code nDotV},
 * {@code nDotL}) or between the light and half vectors ({@code lDotH}).
 *
 * @since 2.1.0
 */
public final class Diffusef {
    private static final float INV_PI = (float) (1.0 / java.lang.Math.PI);

    private Diffusef() {
    }

    /** Lambert: {@code 1/π}. */
    public static float lambert() {
        return INV_PI;
    }

    /**
     * Burley's diffuse (Disney 2012): {@code (1/π) (1 + (f90 - 1)(1 - nDotL)⁵)(1 + (f90 - 1)(1 - nDotV)⁵)} with
     * {@code f90 = 0.5 + 2 roughness lDotH²}. Not energy conserving; it adds retro-reflection at grazing angles.
     */
    public static float burley(float nDotV, float nDotL, float lDotH, float perceptualRoughness) {
        float f90 = 0.5f + 2.0f * perceptualRoughness * lDotH * lDotH;
        float l = 1.0f + (f90 - 1.0f) * pow5(1.0f - nDotL);
        float v = 1.0f + (f90 - 1.0f) * pow5(1.0f - nDotV);
        return l * v * INV_PI;
    }

    /**
     * Qualitative Oren-Nayar (1994) with roughness {@code sigma} (radians, the standard deviation of facet slopes):
     * {@code (1/π)(A + B max(0, s) / max(nDotL, nDotV))} with {@code s = lDotV - nDotL nDotV},
     * {@code A = 1 - 0.5σ²/(σ² + 0.33)} and {@code B = 0.45σ²/(σ² + 0.09)}. This is the paper's
     * {@code cos(φ) sinα tanβ} term without angles; {@code sigma = 0} gives Lambert.
     */
    public static float orenNayar(float nDotV, float nDotL, float lDotV, float sigma) {
        float s2 = sigma * sigma;
        float a = 1.0f - 0.5f * s2 / (s2 + 0.33f);
        float b = 0.45f * s2 / (s2 + 0.09f);
        float s = lDotV - nDotL * nDotV;
        float t = java.lang.Math.max(nDotL, nDotV);
        float term = s > 0.0f && t > 0.0f ? s / t : 0.0f;
        return (a + b * term) * INV_PI;
    }

    private static float pow5(float x) {
        float x2 = x * x;
        return x2 * x2 * x;
    }
}
