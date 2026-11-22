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
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector3d;

/**
 * Warps from the unit square to directions and points (double precision), each with its probability density, for
 * Monte-Carlo integration and importance sampling. Directions are in a local frame with the surface normal on
 * {@code +z}; densities are per unit solid angle (or per unit area for the disk). {@code u1, u2} are in {@code [0, 1)}.
 * Also the multiple-importance-sampling weights of Veach (1997).
 *
 * @since 2.1.0
 */
public final class Warpd {
    private static final double PI = java.lang.Math.PI;
    private static final double INV_PI = 1.0 / java.lang.Math.PI;

    private Warpd() {
    }

    /** Uniform point on the unit disk, Shirley and Chiu's concentric mapping (low distortion). Density {@code 1/π}. */
    public static Vector2d squareToUniformDiskConcentric(double u1, double u2, Vector2d dest) {
        double a = 2.0 * u1 - 1.0;
        double b = 2.0 * u2 - 1.0;
        if (a == 0.0 && b == 0.0) {
            return dest.set(0.0, 0.0);
        }
        double r;
        double phi;
        if (a * a > b * b) {
            r = a;
            phi = (PI / 4.0) * (b / a);
        } else {
            r = b;
            phi = (PI / 2.0) - (PI / 4.0) * (a / b);
        }
        return dest.set(r * AccurateMath.cos(phi), r * AccurateMath.sin(phi));
    }

    /** Cosine-weighted hemisphere direction (Malley's method on the concentric disk). Density {@code cosθ / π}. */
    public static Vector3d squareToCosineHemisphere(double u1, double u2, Vector3d dest) {
        double a = 2.0 * u1 - 1.0;
        double b = 2.0 * u2 - 1.0;
        double x;
        double y;
        if (a == 0.0 && b == 0.0) {
            x = 0.0;
            y = 0.0;
        } else {
            double r;
            double phi;
            if (a * a > b * b) {
                r = a;
                phi = (PI / 4.0) * (b / a);
            } else {
                r = b;
                phi = (PI / 2.0) - (PI / 4.0) * (a / b);
            }
            x = r * AccurateMath.cos(phi);
            y = r * AccurateMath.sin(phi);
        }
        double z = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - x * x - y * y));
        return dest.set(x, y, z);
    }

    /** Density of {@link #squareToCosineHemisphere}: {@code cosθ / π} for {@code cosθ > 0}, else 0. */
    public static double pdfCosineHemisphere(double cosTheta) {
        return cosTheta > 0.0 ? cosTheta * INV_PI : 0.0;
    }

    /** Uniform hemisphere direction. Density {@code 1 / (2π)}. */
    public static Vector3d squareToUniformHemisphere(double u1, double u2, Vector3d dest) {
        double z = u1;
        double r = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - z * z));
        double phi = 2.0 * PI * u2;
        return dest.set(r * AccurateMath.cos(phi), r * AccurateMath.sin(phi), z);
    }

    /** Density of {@link #squareToUniformHemisphere}: {@code 1 / (2π)}. */
    public static double pdfUniformHemisphere() {
        return 0.5 * INV_PI;
    }

    /** Uniform sphere direction. Density {@code 1 / (4π)}. */
    public static Vector3d squareToUniformSphere(double u1, double u2, Vector3d dest) {
        double z = 1.0 - 2.0 * u1;
        double r = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - z * z));
        double phi = 2.0 * PI * u2;
        return dest.set(r * AccurateMath.cos(phi), r * AccurateMath.sin(phi), z);
    }

    /** Density of {@link #squareToUniformSphere}: {@code 1 / (4π)}. */
    public static double pdfUniformSphere() {
        return 0.25 * INV_PI;
    }

    /**
     * Uniform direction in the cone around {@code +z} with half-angle {@code acos(cosThetaMax)}, as for sampling a
     * spherical light seen from outside. Density {@code 1 / (2π (1 - cosThetaMax))}.
     */
    public static Vector3d squareToUniformCone(double u1, double u2, double cosThetaMax, Vector3d dest) {
        double z = 1.0 - u1 * (1.0 - cosThetaMax);
        double r = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - z * z));
        double phi = 2.0 * PI * u2;
        return dest.set(r * AccurateMath.cos(phi), r * AccurateMath.sin(phi), z);
    }

    /** Density of {@link #squareToUniformCone} for a direction with {@code cosθ}; 0 outside the cone. */
    public static double pdfUniformCone(double cosTheta, double cosThetaMax) {
        return cosTheta >= cosThetaMax ? 1.0 / (2.0 * PI * (1.0 - cosThetaMax)) : 0.0;
    }

    /**
     * Balance heuristic weight for a sample drawn from strategy f: {@code nf pf / (nf pf + ng pg)}, with
     * {@code nf}, {@code ng} the sample counts and {@code pf}, {@code pg} the two densities at the sample.
     */
    public static double balanceHeuristic(int nf, double pdfF, int ng, double pdfG) {
        double f = nf * pdfF;
        double g = ng * pdfG;
        return f + g > 0.0 ? f / (f + g) : 0.0;
    }

    /** Power heuristic weight with exponent 2 (Veach's recommendation): {@code (nf pf)² / ((nf pf)² + (ng pg)²)}. */
    public static double powerHeuristic(int nf, double pdfF, int ng, double pdfG) {
        double f = nf * pdfF;
        double g = ng * pdfG;
        double f2 = f * f;
        double s = f2 + g * g;
        return s > 0.0 ? f2 / s : 0.0;
    }
}
