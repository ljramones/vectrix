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
 * Diffuse BRDF lobes (double precision): Lambert, Burley (Disney) and Oren-Nayar. Each returns the BRDF value to
 * multiply by the albedo; cosines are dot products of unit vectors with the surface normal ({@code nDotV},
 * {@code nDotL}) or between the light and half vectors ({@code lDotH}).
 *
 * @since 2.1.0
 */
public final class Diffused {
    private static final double INV_PI = 1.0 / java.lang.Math.PI;

    private Diffused() {
    }

    /**
     * Returns the Lambert BRDF, the constant {@code 1/π}.
     *
     * @return {@code 1/π}
     */
    public static double lambert() {
        return INV_PI;
    }

    /**
     * Burley's diffuse (Disney 2012): {@code (1/π) (1 + (f90 - 1)(1 - nDotL)⁵)(1 + (f90 - 1)(1 - nDotV)⁵)} with
     * {@code f90 = 0.5 + 2 roughness lDotH²}. Not energy conserving; it adds retro-reflection at grazing angles.
     * The cosines are not clamped; pass values in [0, 1].
     *
     * @param nDotV the cosine between the normal and the view direction
     * @param nDotL the cosine between the normal and the light direction
     * @param lDotH the cosine between the light and half vectors
     * @param perceptualRoughness the perceptual roughness, in [0, 1]
     * @return the BRDF value, per steradian
     */
    public static double burley(double nDotV, double nDotL, double lDotH, double perceptualRoughness) {
        double f90 = 0.5 + 2.0 * perceptualRoughness * lDotH * lDotH;
        double l = 1.0 + (f90 - 1.0) * pow5(1.0 - nDotL);
        double v = 1.0 + (f90 - 1.0) * pow5(1.0 - nDotV);
        return l * v * INV_PI;
    }

    /**
     * Qualitative Oren-Nayar (1994) with roughness {@code sigma} (radians, the standard deviation of facet slopes):
     * {@code (1/π)(A + B max(0, s) / max(nDotL, nDotV))} with {@code s = lDotV - nDotL nDotV},
     * {@code A = 1 - 0.5σ²/(σ² + 0.33)} and {@code B = 0.45σ²/(σ² + 0.09)}. This is the paper's
     * {@code cos(φ) sinα tanβ} term without angles; {@code sigma = 0} gives Lambert.
     *
     * @param nDotV the cosine between the normal and the view direction
     * @param nDotL the cosine between the normal and the light direction
     * @param lDotV the cosine between the light and view directions
     * @param sigma the roughness, in radians
     * @return the BRDF value, per steradian
     */
    public static double orenNayar(double nDotV, double nDotL, double lDotV, double sigma) {
        double s2 = sigma * sigma;
        double a = 1.0 - 0.5 * s2 / (s2 + 0.33);
        double b = 0.45 * s2 / (s2 + 0.09);
        double s = lDotV - nDotL * nDotV;
        double t = java.lang.Math.max(nDotL, nDotV);
        double term = s > 0.0 && t > 0.0 ? s / t : 0.0;
        return (a + b * term) * INV_PI;
    }

    private static double pow5(double x) {
        double x2 = x * x;
        return x2 * x2 * x;
    }
}
