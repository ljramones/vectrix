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

import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Microfacet specular terms (double precision): the GGX / Trowbridge-Reitz distribution, Smith masking-shadowing,
 * Schlick Fresnel, and the sheen and clearcoat lobes, as in Filament and pbrt.
 * <p>
 * Conventions: {@code alpha} is the GGX width, usually {@code perceptualRoughness²} ({@link #alphaFromRoughness});
 * cosines are dot products of unit vectors with the surface normal ({@code nDotH}, {@code nDotV}, {@code nDotL})
 * or between half and view vectors ({@code vDotH}), and back-facing inputs are the caller's to exclude. A specular
 * microfacet BRDF is {@code D * V * F}, with the visibility {@code V = G / (4 nDotV nDotL)} already including the
 * denominator. LightEngine's current shader formulas, which differ, are in {@link LightEngineCompatd}.
 *
 * @since 2.1.0
 */
public final class Microfacetd {
    private static final double INV_PI = 1.0 / java.lang.Math.PI;

    private Microfacetd() {
    }

    /** GGX width from perceptual roughness: {@code roughness²} (Burley 2012, Filament). */
    public static double alphaFromRoughness(double perceptualRoughness) {
        return perceptualRoughness * perceptualRoughness;
    }

    /**
     * Isotropic GGX normal distribution {@code D(h) = α² / (π ((n·h)² (α² - 1) + 1)²)}, normalised so that
     * {@code ∫ D(h) (n·h) dω = 1}. Returns 0 for {@code nDotH <= 0}.
     */
    public static double dGgx(double nDotH, double alpha) {
        if (nDotH <= 0.0) {
            return 0.0;
        }
        double a2 = alpha * alpha;
        double d = nDotH * nDotH * (a2 - 1.0) + 1.0;
        return a2 * INV_PI / (d * d);
    }

    /**
     * Anisotropic GGX distribution (Burley 2012) with widths {@code alphaT} along the tangent and {@code alphaB} along
     * the bitangent; {@code tDotH} and {@code bDotH} are the half vector's tangent-frame components.
     */
    public static double dGgxAnisotropic(double nDotH, double tDotH, double bDotH, double alphaT, double alphaB) {
        if (nDotH <= 0.0) {
            return 0.0;
        }
        double x = tDotH / alphaT;
        double y = bDotH / alphaB;
        double d = x * x + y * y + nDotH * nDotH;
        return INV_PI / (alphaT * alphaB * d * d);
    }

    /** Smith Λ for GGX: {@code (√(1 + α² tan²θ) - 1) / 2} for the direction with {@code cosθ = nDotX}. */
    public static double lambdaGgx(double nDotX, double alpha) {
        double c2 = nDotX * nDotX;
        double tan2 = (1.0 - c2) / c2;
        return 0.5 * (java.lang.Math.sqrt(1.0 + alpha * alpha * tan2) - 1.0);
    }

    /** Smith masking {@code G1 = 1 / (1 + Λ)}. */
    public static double g1Smith(double nDotX, double alpha) {
        if (nDotX <= 0.0) {
            return 0.0;
        }
        return 1.0 / (1.0 + lambdaGgx(nDotX, alpha));
    }

    /** Height-correlated Smith masking-shadowing {@code G2 = 1 / (1 + Λ(v) + Λ(l))} (Heitz 2014). */
    public static double g2SmithHeightCorrelated(double nDotV, double nDotL, double alpha) {
        if (nDotV <= 0.0 || nDotL <= 0.0) {
            return 0.0;
        }
        return 1.0 / (1.0 + lambdaGgx(nDotV, alpha) + lambdaGgx(nDotL, alpha));
    }

    /**
     * Height-correlated Smith visibility {@code V = G2 / (4 nDotV nDotL)}, in Filament's form
     * {@code 0.5 / (nDotL √(nDotV² (1 - α²) + α²) + nDotV √(nDotL² (1 - α²) + α²))}, which avoids the tangents.
     */
    public static double vSmithGgxCorrelated(double nDotV, double nDotL, double alpha) {
        if (nDotV <= 0.0 || nDotL <= 0.0) {
            return 0.0;
        }
        double a2 = alpha * alpha;
        double gv = nDotL * java.lang.Math.sqrt(nDotV * nDotV * (1.0 - a2) + a2);
        double gl = nDotV * java.lang.Math.sqrt(nDotL * nDotL * (1.0 - a2) + a2);
        return 0.5 / (gv + gl);
    }

    /**
     * Anisotropic height-correlated Smith visibility (Heitz 2014, Filament); {@code tDotV}, {@code bDotV} and
     * {@code tDotL}, {@code bDotL} are the view and light vectors' tangent-frame components.
     */
    public static double vSmithGgxCorrelatedAnisotropic(double nDotV, double tDotV, double bDotV, double nDotL, double tDotL,
            double bDotL, double alphaT, double alphaB) {
        if (nDotV <= 0.0 || nDotL <= 0.0) {
            return 0.0;
        }
        double lambdaV = nDotL * length(alphaT * tDotV, alphaB * bDotV, nDotV);
        double lambdaL = nDotV * length(alphaT * tDotL, alphaB * bDotL, nDotL);
        return 0.5 / (lambdaV + lambdaL);
    }

    /**
     * Separable Schlick-GGX masking with Karis's image-based-lighting remap {@code k = α / 2} (UE4, Karis 2013): the
     * term split-sum BRDF lookup tables are traditionally built with ({@link DfgLutBuilder.Model#SCHLICK_GGX_KARIS}).
     * Use {@link #g2SmithHeightCorrelated} for new work.
     */
    public static double gSchlickGgxKaris(double nDotX, double alpha) {
        double k = alpha * 0.5;
        return nDotX / (nDotX * (1.0 - k) + k);
    }

    private static double length(double x, double y, double z) {
        return java.lang.Math.sqrt(x * x + y * y + z * z);
    }

    private static double pow5(double x) {
        double x2 = x * x;
        return x2 * x2 * x;
    }

    /** Schlick Fresnel {@code f0 + (f90 - f0) (1 - vDotH)⁵}, usually with {@code f90 = 1}. */
    public static double fSchlick(double f0, double f90, double vDotH) {
        return f0 + (f90 - f0) * pow5(1.0 - vDotH);
    }

    /** Schlick Fresnel per RGB channel with a common {@code f90}; {@code dest} may alias {@code f0}. */
    public static Vector3d fSchlick(Vector3dc f0, double f90, double vDotH, Vector3d dest) {
        double w = pow5(1.0 - vDotH);
        double r = f0.x(), g = f0.y(), b = f0.z();
        return dest.set(r + (f90 - r) * w, g + (f90 - g) * w, b + (f90 - b) * w);
    }

    /**
     * Roughness-aware Schlick Fresnel for image-based lighting (Lagarde): {@code f90} becomes
     * {@code max(1 - roughness, f0)} per channel. {@code dest} may alias {@code f0}.
     */
    public static Vector3d fSchlickRoughness(Vector3dc f0, double perceptualRoughness, double nDotV, Vector3d dest) {
        double w = pow5(1.0 - nDotV);
        double s = 1.0 - perceptualRoughness;
        double r = f0.x(), g = f0.y(), b = f0.z();
        return dest.set(r + (java.lang.Math.max(s, r) - r) * w, g + (java.lang.Math.max(s, g) - g) * w,
                b + (java.lang.Math.max(s, b) - b) * w);
    }

    /**
     * "Charlie" sheen distribution (Estevez and Kulla 2017): {@code (2 + 1/α) sin^(1/α)θh / (2π)}, normalised like
     * {@link #dGgx}. {@code alpha} must be positive.
     */
    public static double dCharlie(double nDotH, double alpha) {
        if (nDotH <= 0.0) {
            return 0.0;
        }
        double invAlpha = 1.0 / alpha;
        double sin2h = java.lang.Math.max(1.0 - nDotH * nDotH, 0.0);
        double sinPow = org.dynamisengine.vectrix.core.AccurateMath.pow(sin2h, invAlpha * 0.5);
        return (2.0 + invAlpha) * sinPow * (0.5 * INV_PI);
    }

    /** Sheen visibility of Neubelt and Pettineo 2013: {@code 1 / (4 (nDotL + nDotV - nDotL nDotV))}. */
    public static double vNeubelt(double nDotV, double nDotL) {
        return 1.0 / (4.0 * (nDotL + nDotV - nDotL * nDotV));
    }

    /** Clearcoat visibility of Kelemen 2001: {@code 1 / (4 lDotH²)}. */
    public static double vKelemen(double lDotH) {
        return 0.25 / (lDotH * lDotH);
    }

    /**
     * Kulla-Conty multiple-scattering energy compensation for a split-sum DFG term {@code (a, b)} (Karis layout,
     * {@code specular = f0 * a + b}): the factor {@code 1 + f0 (1 / (a + b) - 1)} that the single-scattering
     * specular lobe is multiplied by, as in Filament. {@code a + b} is the lobe's directional albedo with F = 1.
     */
    public static double energyCompensation(double f0, double a, double b) {
        return 1.0 + f0 * (1.0 / (a + b) - 1.0);
    }
}
