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

import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Microfacet specular terms (single precision): the GGX / Trowbridge-Reitz distribution, Smith masking-shadowing,
 * Schlick Fresnel, and the sheen and clearcoat lobes, as in Filament and pbrt.
 * <p>
 * Conventions: {@code alpha} is the GGX width, usually {@code perceptualRoughness²} ({@link #alphaFromRoughness});
 * cosines are dot products of unit vectors with the surface normal ({@code nDotH}, {@code nDotV}, {@code nDotL})
 * or between half and view vectors ({@code vDotH}), and back-facing inputs are the caller's to exclude. A specular
 * microfacet BRDF is {@code D * V * F}, with the visibility {@code V = G / (4 nDotV nDotL)} already including the
 * denominator. LightEngine's current shader formulas, which differ, are in {@link LightEngineCompatf}.
 *
 * @since 2.1.0
 */
public final class Microfacetf {
    private static final float INV_PI = (float) (1.0 / java.lang.Math.PI);

    private Microfacetf() {
    }

    /**
     * Returns the GGX width for a perceptual roughness: {@code roughness²} (Burley 2012, Filament).
     *
     * @param perceptualRoughness the artist-facing roughness, usually in [0, 1]
     * @return {@code perceptualRoughness²}
     */
    public static float alphaFromRoughness(float perceptualRoughness) {
        return perceptualRoughness * perceptualRoughness;
    }

    /**
     * Isotropic GGX normal distribution {@code D(h) = α² / (π ((n·h)² (α² - 1) + 1)²)}, normalised so that
     * {@code ∫ D(h) (n·h) dω = 1}. Returns 0 for {@code nDotH <= 0}.
     *
     * @param nDotH the cosine between the normal and the half vector
     * @param alpha the GGX width; 0 gives a delta peak (0 everywhere except NaN at {@code nDotH = 1})
     * @return the distribution value, per steradian
     */
    public static float dGgx(float nDotH, float alpha) {
        if (nDotH <= 0.0f) {
            return 0.0f;
        }
        float a2 = alpha * alpha;
        float d = nDotH * nDotH * (a2 - 1.0f) + 1.0f;
        return a2 * INV_PI / (d * d);
    }

    /**
     * Anisotropic GGX distribution (Burley 2012) with widths {@code alphaT} along the tangent and {@code alphaB} along
     * the bitangent; {@code tDotH} and {@code bDotH} are the half vector's tangent-frame components. Returns 0 for
     * {@code nDotH <= 0}.
     *
     * @param nDotH the half vector's normal component
     * @param tDotH the half vector's tangent component
     * @param bDotH the half vector's bitangent component
     * @param alphaT the GGX width along the tangent, positive
     * @param alphaB the GGX width along the bitangent, positive
     * @return the distribution value, per steradian
     */
    public static float dGgxAnisotropic(float nDotH, float tDotH, float bDotH, float alphaT, float alphaB) {
        if (nDotH <= 0.0f) {
            return 0.0f;
        }
        float x = tDotH / alphaT;
        float y = bDotH / alphaB;
        float d = x * x + y * y + nDotH * nDotH;
        return INV_PI / (alphaT * alphaB * d * d);
    }

    /**
     * Returns Smith's Λ for GGX: {@code (√(1 + α² tan²θ) - 1) / 2} for the direction with {@code cosθ = nDotX}.
     * Only {@code nDotX²} is used, so the sign is ignored; {@code nDotX = 0} gives positive infinity.
     *
     * @param nDotX the cosine between the normal and the view or light direction, nonzero
     * @param alpha the GGX width
     * @return Λ, at least 0
     */
    public static float lambdaGgx(float nDotX, float alpha) {
        float c2 = nDotX * nDotX;
        float tan2 = (1.0f - c2) / c2;
        return 0.5f * ((float) java.lang.Math.sqrt(1.0f + alpha * alpha * tan2) - 1.0f);
    }

    /**
     * Returns the Smith masking term {@code G1 = 1 / (1 + Λ)} for GGX. Returns 0 for {@code nDotX <= 0}.
     *
     * @param nDotX the cosine between the normal and the view or light direction
     * @param alpha the GGX width
     * @return {@code G1}, in [0, 1]
     */
    public static float g1Smith(float nDotX, float alpha) {
        if (nDotX <= 0.0f) {
            return 0.0f;
        }
        return 1.0f / (1.0f + lambdaGgx(nDotX, alpha));
    }

    /**
     * Returns the height-correlated Smith masking-shadowing term {@code G2 = 1 / (1 + Λ(v) + Λ(l))} (Heitz 2014) for
     * GGX. Returns 0 when {@code nDotV <= 0} or {@code nDotL <= 0}.
     *
     * @param nDotV the cosine between the normal and the view direction
     * @param nDotL the cosine between the normal and the light direction
     * @param alpha the GGX width
     * @return {@code G2}, in [0, 1]
     */
    public static float g2SmithHeightCorrelated(float nDotV, float nDotL, float alpha) {
        if (nDotV <= 0.0f || nDotL <= 0.0f) {
            return 0.0f;
        }
        return 1.0f / (1.0f + lambdaGgx(nDotV, alpha) + lambdaGgx(nDotL, alpha));
    }

    /**
     * Height-correlated Smith visibility {@code V = G2 / (4 nDotV nDotL)}, in Filament's form
     * {@code 0.5 / (nDotL √(nDotV² (1 - α²) + α²) + nDotV √(nDotL² (1 - α²) + α²))}, which avoids the tangents.
     * Returns 0 when {@code nDotV <= 0} or {@code nDotL <= 0}.
     *
     * @param nDotV the cosine between the normal and the view direction
     * @param nDotL the cosine between the normal and the light direction
     * @param alpha the GGX width
     * @return the visibility term {@code V}
     */
    public static float vSmithGgxCorrelated(float nDotV, float nDotL, float alpha) {
        if (nDotV <= 0.0f || nDotL <= 0.0f) {
            return 0.0f;
        }
        float a2 = alpha * alpha;
        float gv = nDotL * (float) java.lang.Math.sqrt(nDotV * nDotV * (1.0f - a2) + a2);
        float gl = nDotV * (float) java.lang.Math.sqrt(nDotL * nDotL * (1.0f - a2) + a2);
        return 0.5f / (gv + gl);
    }

    /**
     * Anisotropic height-correlated Smith visibility (Heitz 2014, Filament); {@code tDotV}, {@code bDotV} and
     * {@code tDotL}, {@code bDotL} are the view and light vectors' tangent-frame components. Returns 0 when
     * {@code nDotV <= 0} or {@code nDotL <= 0}.
     *
     * @param nDotV the view vector's normal component
     * @param tDotV the view vector's tangent component
     * @param bDotV the view vector's bitangent component
     * @param nDotL the light vector's normal component
     * @param tDotL the light vector's tangent component
     * @param bDotL the light vector's bitangent component
     * @param alphaT the GGX width along the tangent
     * @param alphaB the GGX width along the bitangent
     * @return the visibility term {@code V = G2 / (4 nDotV nDotL)}
     */
    public static float vSmithGgxCorrelatedAnisotropic(float nDotV, float tDotV, float bDotV, float nDotL, float tDotL,
            float bDotL, float alphaT, float alphaB) {
        if (nDotV <= 0.0f || nDotL <= 0.0f) {
            return 0.0f;
        }
        float lambdaV = nDotL * length(alphaT * tDotV, alphaB * bDotV, nDotV);
        float lambdaL = nDotV * length(alphaT * tDotL, alphaB * bDotL, nDotL);
        return 0.5f / (lambdaV + lambdaL);
    }

    /**
     * Separable Schlick-GGX masking with Karis's image-based-lighting remap {@code k = α / 2} (UE4, Karis 2013): the
     * term split-sum BRDF lookup tables are traditionally built with ({@link DfgLutBuilder.Model#SCHLICK_GGX_KARIS}).
     * Use {@link #g2SmithHeightCorrelated} for new work. Returns {@code nDotX / (nDotX (1 - k) + k)} without clamping;
     * {@code nDotX = alpha = 0} gives NaN.
     *
     * @param nDotX the cosine between the normal and the view or light direction, in [0, 1]
     * @param alpha the GGX width (not the perceptual roughness)
     * @return the masking term for one direction, in [0, 1] for inputs in range
     */
    public static float gSchlickGgxKaris(float nDotX, float alpha) {
        float k = alpha * 0.5f;
        return nDotX / (nDotX * (1.0f - k) + k);
    }

    private static float length(float x, float y, float z) {
        return (float) java.lang.Math.sqrt(x * x + y * y + z * z);
    }

    private static float pow5(float x) {
        float x2 = x * x;
        return x2 * x2 * x;
    }

    /**
     * Returns Schlick's Fresnel approximation {@code f0 + (f90 - f0) (1 - vDotH)⁵}, usually with {@code f90 = 1}.
     *
     * @param f0 the reflectance at normal incidence
     * @param f90 the reflectance at grazing incidence
     * @param vDotH the cosine between the view and half vectors, in [0, 1]
     * @return the reflectance
     */
    public static float fSchlick(float f0, float f90, float vDotH) {
        return f0 + (f90 - f0) * pow5(1.0f - vDotH);
    }

    /**
     * Computes Schlick's Fresnel approximation per RGB channel with a common {@code f90}; {@code dest} may alias
     * {@code f0}.
     *
     * @param f0 the RGB reflectance at normal incidence
     * @param f90 the reflectance at grazing incidence
     * @param vDotH the cosine between the view and half vectors, in [0, 1]
     * @param dest receives the RGB reflectance
     * @return {@code dest}
     */
    public static Vector3f fSchlick(Vector3fc f0, float f90, float vDotH, Vector3f dest) {
        float w = pow5(1.0f - vDotH);
        float r = f0.x(), g = f0.y(), b = f0.z();
        return dest.set(r + (f90 - r) * w, g + (f90 - g) * w, b + (f90 - b) * w);
    }

    /**
     * Roughness-aware Schlick Fresnel for image-based lighting (Lagarde): {@code f90} becomes
     * {@code max(1 - roughness, f0)} per channel. {@code dest} may alias {@code f0}.
     *
     * @param f0 the RGB reflectance at normal incidence
     * @param perceptualRoughness the perceptual roughness, in [0, 1]
     * @param nDotV the cosine between the normal and the view direction, in [0, 1]
     * @param dest receives the RGB reflectance
     * @return {@code dest}
     */
    public static Vector3f fSchlickRoughness(Vector3fc f0, float perceptualRoughness, float nDotV, Vector3f dest) {
        float w = pow5(1.0f - nDotV);
        float s = 1.0f - perceptualRoughness;
        float r = f0.x(), g = f0.y(), b = f0.z();
        return dest.set(r + (java.lang.Math.max(s, r) - r) * w, g + (java.lang.Math.max(s, g) - g) * w,
                b + (java.lang.Math.max(s, b) - b) * w);
    }

    /**
     * "Charlie" sheen distribution (Estevez and Kulla 2017): {@code (2 + 1/α) sin^(1/α)θh / (2π)}, normalised like
     * {@link #dGgx}. {@code alpha} must be positive. Returns 0 for {@code nDotH <= 0}.
     *
     * @param nDotH the cosine between the normal and the half vector
     * @param alpha the sheen width, positive
     * @return the distribution value, per steradian
     */
    public static float dCharlie(float nDotH, float alpha) {
        if (nDotH <= 0.0f) {
            return 0.0f;
        }
        float invAlpha = 1.0f / alpha;
        float sin2h = java.lang.Math.max(1.0f - nDotH * nDotH, 0.0f);
        float sinPow = (float) org.dynamisengine.vectrix.core.AccurateMath.pow(sin2h, invAlpha * 0.5f);
        return (2.0f + invAlpha) * sinPow * (0.5f * INV_PI);
    }

    /**
     * Returns the sheen visibility term of Neubelt and Pettineo 2013: {@code 1 / (4 (nDotL + nDotV - nDotL nDotV))}.
     * Not clamped: {@code nDotV = nDotL = 0} gives positive infinity.
     *
     * @param nDotV the cosine between the normal and the view direction, in [0, 1]
     * @param nDotL the cosine between the normal and the light direction, in [0, 1]
     * @return the visibility term
     */
    public static float vNeubelt(float nDotV, float nDotL) {
        return 1.0f / (4.0f * (nDotL + nDotV - nDotL * nDotV));
    }

    /**
     * Returns the clearcoat visibility term of Kelemen 2001: {@code 1 / (4 lDotH²)}. Not clamped: {@code lDotH = 0}
     * gives positive infinity.
     *
     * @param lDotH the cosine between the light and half vectors
     * @return the visibility term
     */
    public static float vKelemen(float lDotH) {
        return 0.25f / (lDotH * lDotH);
    }

    /**
     * Kulla-Conty multiple-scattering energy compensation for a split-sum DFG term {@code (a, b)} (Karis layout,
     * {@code specular = f0 * a + b}): the factor {@code 1 + f0 (1 / (a + b) - 1)} that the single-scattering
     * specular lobe is multiplied by, as in Filament. {@code a + b} is the lobe's directional albedo with F = 1.
     *
     * @param f0 the reflectance at normal incidence
     * @param a the DFG scale term
     * @param b the DFG bias term; {@code a + b} must be positive
     * @return the multiplier, at least 1 for {@code a + b <= 1} and nonnegative {@code f0}
     */
    public static float energyCompensation(float f0, float a, float b) {
        return 1.0f + f0 * (1.0f / (a + b) - 1.0f);
    }
}
