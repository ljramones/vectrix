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

    /**
     * Maps a point of the unit square to a uniformly distributed point on the unit disk, using Shirley and Chiu's
     * concentric mapping (low distortion). The density is {@code 1/π} per unit area.
     *
     * @param u1 the first uniform variate, in {@code [0, 1)}
     * @param u2 the second uniform variate, in {@code [0, 1)}
     * @param dest receives the point, with {@code x² + y² <= 1}
     * @return {@code dest}
     */
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

    /**
     * Maps a point of the unit square to a cosine-weighted direction on the {@code +z} hemisphere, by lifting the
     * concentric disk point to the hemisphere (Malley's method). The density is {@code cosθ / π} per unit solid
     * angle; see {@link #pdfCosineHemisphere}.
     *
     * @param u1 the first uniform variate, in {@code [0, 1)}
     * @param u2 the second uniform variate, in {@code [0, 1)}
     * @param dest receives the unit direction, with {@code z >= 0}
     * @return {@code dest}
     */
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

    /**
     * Returns the density of {@link #squareToCosineHemisphere} per unit solid angle.
     *
     * @param cosTheta the cosine of the angle between the direction and {@code +z}
     * @return {@code cosθ / π} for {@code cosTheta > 0}, else 0
     */
    public static float pdfCosineHemisphere(float cosTheta) {
        return cosTheta > 0.0f ? cosTheta * INV_PI : 0.0f;
    }

    /**
     * Maps a point of the unit square to a uniformly distributed direction on the {@code +z} hemisphere. The density
     * is {@code 1 / (2π)} per unit solid angle.
     *
     * @param u1 the first uniform variate, in {@code [0, 1)}; it becomes {@code z}
     * @param u2 the second uniform variate, in {@code [0, 1)}; it sets the azimuth {@code 2π u2}
     * @param dest receives the unit direction
     * @return {@code dest}
     */
    public static Vector3f squareToUniformHemisphere(float u1, float u2, Vector3f dest) {
        float z = u1;
        float r = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0f, 1.0f - z * z));
        float phi = 2.0f * PI * u2;
        return dest.set(r * (float) AccurateMath.cos(phi), r * (float) AccurateMath.sin(phi), z);
    }

    /**
     * Returns the density of {@link #squareToUniformHemisphere} per unit solid angle.
     *
     * @return {@code 1 / (2π)}
     */
    public static float pdfUniformHemisphere() {
        return 0.5f * INV_PI;
    }

    /**
     * Maps a point of the unit square to a uniformly distributed direction on the unit sphere. The density is
     * {@code 1 / (4π)} per unit solid angle.
     *
     * @param u1 the first uniform variate, in {@code [0, 1)}; it sets {@code z = 1 - 2 u1}
     * @param u2 the second uniform variate, in {@code [0, 1)}; it sets the azimuth {@code 2π u2}
     * @param dest receives the unit direction
     * @return {@code dest}
     */
    public static Vector3f squareToUniformSphere(float u1, float u2, Vector3f dest) {
        float z = 1.0f - 2.0f * u1;
        float r = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0f, 1.0f - z * z));
        float phi = 2.0f * PI * u2;
        return dest.set(r * (float) AccurateMath.cos(phi), r * (float) AccurateMath.sin(phi), z);
    }

    /**
     * Returns the density of {@link #squareToUniformSphere} per unit solid angle.
     *
     * @return {@code 1 / (4π)}
     */
    public static float pdfUniformSphere() {
        return 0.25f * INV_PI;
    }

    /**
     * Maps a point of the unit square to a uniformly distributed direction in the cone around {@code +z} with
     * half-angle {@code acos(cosThetaMax)}, as when sampling a spherical light seen from outside. The density is
     * {@code 1 / (2π (1 - cosThetaMax))} per unit solid angle; see {@link #pdfUniformCone}.
     *
     * @param u1 the first uniform variate, in {@code [0, 1)}; it sets {@code z = 1 - u1 (1 - cosThetaMax)}
     * @param u2 the second uniform variate, in {@code [0, 1)}; it sets the azimuth {@code 2π u2}
     * @param cosThetaMax the cosine of the cone's half-angle, in {@code [-1, 1]}
     * @param dest receives the unit direction
     * @return {@code dest}
     */
    public static Vector3f squareToUniformCone(float u1, float u2, float cosThetaMax, Vector3f dest) {
        float z = 1.0f - u1 * (1.0f - cosThetaMax);
        float r = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0f, 1.0f - z * z));
        float phi = 2.0f * PI * u2;
        return dest.set(r * (float) AccurateMath.cos(phi), r * (float) AccurateMath.sin(phi), z);
    }

    /**
     * Returns the density of {@link #squareToUniformCone} per unit solid angle for a direction with the given
     * {@code cosθ}. For {@code cosThetaMax = 1} (a zero-width cone) the result is infinite inside the cone.
     *
     * @param cosTheta the cosine of the angle between the direction and {@code +z}
     * @param cosThetaMax the cosine of the cone's half-angle
     * @return {@code 1 / (2π (1 - cosThetaMax))} if {@code cosTheta >= cosThetaMax}, else 0
     */
    public static float pdfUniformCone(float cosTheta, float cosThetaMax) {
        return cosTheta >= cosThetaMax ? 1.0f / (2.0f * PI * (1.0f - cosThetaMax)) : 0.0f;
    }

    /**
     * Returns the balance heuristic weight for a sample drawn from strategy f when strategies f and g are combined
     * (Veach 1997): {@code nf pf / (nf pf + ng pg)}.
     *
     * @param nf the number of samples taken from strategy f
     * @param pdfF the density of strategy f at the sample
     * @param ng the number of samples taken from strategy g
     * @param pdfG the density of strategy g at the sample
     * @return the weight in {@code [0, 1]}, or 0 when both weighted densities are 0
     */
    public static float balanceHeuristic(int nf, float pdfF, int ng, float pdfG) {
        float f = nf * pdfF;
        float g = ng * pdfG;
        return f + g > 0.0f ? f / (f + g) : 0.0f;
    }

    /**
     * Returns the power heuristic weight with exponent 2 (Veach's recommendation) for a sample drawn from strategy f:
     * {@code (nf pf)² / ((nf pf)² + (ng pg)²)}.
     *
     * @param nf the number of samples taken from strategy f
     * @param pdfF the density of strategy f at the sample
     * @param ng the number of samples taken from strategy g
     * @param pdfG the density of strategy g at the sample
     * @return the weight in {@code [0, 1]}, or 0 when both weighted densities are 0
     */
    public static float powerHeuristic(int nf, float pdfF, int ng, float pdfG) {
        float f = nf * pdfF;
        float g = ng * pdfG;
        float f2 = f * f;
        float s = f2 + g * g;
        return s > 0.0f ? f2 / s : 0.0f;
    }
}
