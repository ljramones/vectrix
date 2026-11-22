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

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3f;

/**
 * Warps from the unit square to directions and points (single precision), each with its probability density, for
 * Monte-Carlo integration and importance sampling. Directions are in a local frame with the surface normal on
 * {@code +z}; densities are per unit solid angle (or per unit area for the disk). {@code u1, u2} are in {@code [0, 1)}.
 * Also the multiple-importance-sampling weights of Veach (1997).
 *
 * @since 2.1.0
 */
public final class Warpf {
    private static final float PI = (float) java.lang.Math.PI;
    private static final float INV_PI = (float) (1.0 / java.lang.Math.PI);

    private Warpf() {
    }

    /** Uniform point on the unit disk, Shirley and Chiu's concentric mapping (low distortion). Density {@code 1/π}. */
    public static Vector2f squareToUniformDiskConcentric(float u1, float u2, Vector2f dest) {
        float a = 2.0f * u1 - 1.0f;
        float b = 2.0f * u2 - 1.0f;
        if (a == 0.0f && b == 0.0f) {
            return dest.set(0.0f, 0.0f);
        }
        float r;
        float phi;
        if (a * a > b * b) {
            r = a;
            phi = (PI / 4.0f) * (b / a);
        } else {
            r = b;
            phi = (PI / 2.0f) - (PI / 4.0f) * (a / b);
        }
        return dest.set(r * (float) AccurateMath.cos(phi), r * (float) AccurateMath.sin(phi));
    }

    /** Cosine-weighted hemisphere direction (Malley's method on the concentric disk). Density {@code cosθ / π}. */
    public static Vector3f squareToCosineHemisphere(float u1, float u2, Vector3f dest) {
        float a = 2.0f * u1 - 1.0f;
        float b = 2.0f * u2 - 1.0f;
        float x;
        float y;
        if (a == 0.0f && b == 0.0f) {
            x = 0.0f;
            y = 0.0f;
        } else {
            float r;
            float phi;
            if (a * a > b * b) {
                r = a;
                phi = (PI / 4.0f) * (b / a);
            } else {
                r = b;
                phi = (PI / 2.0f) - (PI / 4.0f) * (a / b);
            }
            x = r * (float) AccurateMath.cos(phi);
            y = r * (float) AccurateMath.sin(phi);
        }
        float z = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0f, 1.0f - x * x - y * y));
        return dest.set(x, y, z);
    }

    /** Density of {@link #squareToCosineHemisphere}: {@code cosθ / π} for {@code cosθ > 0}, else 0. */
    public static float pdfCosineHemisphere(float cosTheta) {
        return cosTheta > 0.0f ? cosTheta * INV_PI : 0.0f;
    }

    /** Uniform hemisphere direction. Density {@code 1 / (2π)}. */
    public static Vector3f squareToUniformHemisphere(float u1, float u2, Vector3f dest) {
        float z = u1;
        float r = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0f, 1.0f - z * z));
        float phi = 2.0f * PI * u2;
        return dest.set(r * (float) AccurateMath.cos(phi), r * (float) AccurateMath.sin(phi), z);
    }

    /** Density of {@link #squareToUniformHemisphere}: {@code 1 / (2π)}. */
    public static float pdfUniformHemisphere() {
        return 0.5f * INV_PI;
    }

    /** Uniform sphere direction. Density {@code 1 / (4π)}. */
    public static Vector3f squareToUniformSphere(float u1, float u2, Vector3f dest) {
        float z = 1.0f - 2.0f * u1;
        float r = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0f, 1.0f - z * z));
        float phi = 2.0f * PI * u2;
        return dest.set(r * (float) AccurateMath.cos(phi), r * (float) AccurateMath.sin(phi), z);
    }

    /** Density of {@link #squareToUniformSphere}: {@code 1 / (4π)}. */
    public static float pdfUniformSphere() {
        return 0.25f * INV_PI;
    }

    /**
     * Uniform direction in the cone around {@code +z} with half-angle {@code acos(cosThetaMax)}, as for sampling a
     * spherical light seen from outside. Density {@code 1 / (2π (1 - cosThetaMax))}.
     */
    public static Vector3f squareToUniformCone(float u1, float u2, float cosThetaMax, Vector3f dest) {
        float z = 1.0f - u1 * (1.0f - cosThetaMax);
        float r = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0f, 1.0f - z * z));
        float phi = 2.0f * PI * u2;
        return dest.set(r * (float) AccurateMath.cos(phi), r * (float) AccurateMath.sin(phi), z);
    }

    /** Density of {@link #squareToUniformCone} for a direction with {@code cosθ}; 0 outside the cone. */
    public static float pdfUniformCone(float cosTheta, float cosThetaMax) {
        return cosTheta >= cosThetaMax ? 1.0f / (2.0f * PI * (1.0f - cosThetaMax)) : 0.0f;
    }

    /**
     * Balance heuristic weight for a sample drawn from strategy f: {@code nf pf / (nf pf + ng pg)}, with
     * {@code nf}, {@code ng} the sample counts and {@code pf}, {@code pg} the two densities at the sample.
     */
    public static float balanceHeuristic(int nf, float pdfF, int ng, float pdfG) {
        float f = nf * pdfF;
        float g = ng * pdfG;
        return f + g > 0.0f ? f / (f + g) : 0.0f;
    }

    /** Power heuristic weight with exponent 2 (Veach's recommendation): {@code (nf pf)² / ((nf pf)² + (ng pg)²)}. */
    public static float powerHeuristic(int nf, float pdfF, int ng, float pdfG) {
        float f = nf * pdfF;
        float g = ng * pdfG;
        float f2 = f * f;
        float s = f2 + g * g;
        return s > 0.0f ? f2 / s : 0.0f;
    }
}
