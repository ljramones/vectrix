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

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * Builds the split-sum DFG lookup table for image-based lighting (Karis 2013): for each {@code (nDotV, roughness)}
 * the scale {@code a} and bias {@code b} such that the GGX specular lobe integrated against a white environment is
 * {@code f0 * a + b}. {@code a + b} is the lobe's directional albedo, which also drives
 * {@link Microfacetf#energyCompensation} (Kulla-Conty).
 * <p>
 * Layout: {@code size × size} texels, two floats each, row-major; texel {@code (x, y)} is at index
 * {@code 2 (y size + x)} and covers {@code nDotV = (x + 0.5) / size} and perceptual roughness
 * {@code (y + 0.5) / size} ({@code α = roughness²}). That is the {@code vec2(nDotV, roughness)} lookup used by
 * DynamisLightEngine. The integral is a deterministic Hammersley estimate with GGX importance sampling, accumulated in
 * double precision. Its only transcendental is {@link AccurateMath#cos}, so under {@code -Dvectrix.strictMath=true}
 * the same inputs give the same table on every platform.
 *
 * @since 2.1.0
 */
public final class DfgLutBuilder {

    /** The masking-shadowing term the table is integrated with. */
    public enum Model {
        /** Height-correlated Smith ({@link Microfacetf#vSmithGgxCorrelated}); the reference model, as in Filament. */
        HEIGHT_CORRELATED_SMITH,
        /** Separable Schlick-GGX with {@code k = α / 2} ({@link Microfacetf#gSchlickGgxKaris}), as in UE4. */
        SCHLICK_GGX_KARIS
    }

    private DfgLutBuilder() {
    }

    /**
     * Fills {@code outAb} (length at least {@code 2 * size * size}) with the table, in the layout described on the
     * class. Allocates one two-element scratch array per call.
     *
     * @param size the table width and height in texels, positive
     * @param sampleCount Hammersley samples per texel; 512 to 1024 is typical for 8-bit to 16-bit precision
     * @param model the masking-shadowing term to integrate with
     * @param outAb receives the interleaved {@code (a, b)} pairs, starting at index 0
     * @throws IllegalArgumentException if {@code size} or {@code sampleCount} is not positive, {@code model} is null or
     *                                  {@code outAb} is too small
     */
    public static void build(int size, int sampleCount, Model model, float[] outAb) {
        if (size <= 0 || sampleCount <= 0) {
            throw new IllegalArgumentException("size and sampleCount must be positive");
        }
        if (model == null) {
            throw new IllegalArgumentException("model");
        }
        if (outAb == null || outAb.length < 2L * size * size) {
            throw new IllegalArgumentException("outAb must hold 2 * size * size floats");
        }
        double[] ab = new double[2];
        for (int y = 0; y < size; y++) {
            double roughness = (y + 0.5) / size;
            for (int x = 0; x < size; x++) {
                integrate((x + 0.5) / size, roughness, sampleCount, model, ab);
                int o = 2 * (y * size + x);
                outAb[o] = (float) ab[0];
                outAb[o + 1] = (float) ab[1];
            }
        }
    }

    /**
     * Computes the DFG term for one {@code (nDotV, perceptualRoughness)}: writes {@code a} to {@code outAb[0]} and
     * {@code b} to {@code outAb[1]}. Roughness 0 is allowed: it gives the mirror limit
     * {@code a = 1 - F_c(nDotV)}, {@code b = F_c(nDotV)} with {@code F_c = (1 - nDotV)⁵}.
     *
     * @param nDotV the cosine between the normal and the view direction, in (0, 1]
     * @param perceptualRoughness the perceptual roughness, in [0, 1]; {@code α = perceptualRoughness²}
     * @param sampleCount the number of Hammersley samples, positive
     * @param model the masking-shadowing term to integrate with, not {@code null}
     * @param outAb receives {@code a} at index 0 and {@code b} at index 1; length at least 2
     * @throws IllegalArgumentException if {@code nDotV} is outside (0, 1], {@code perceptualRoughness} is outside
     *         [0, 1], {@code sampleCount} is not positive, {@code model} is {@code null} or {@code outAb} is
     *         shorter than 2 (NaN arguments included; checked before anything is written)
     */
    public static void integrate(double nDotV, double perceptualRoughness, int sampleCount, Model model, double[] outAb) {
        checkCommon(nDotV, sampleCount);
        if (!(perceptualRoughness >= 0.0 && perceptualRoughness <= 1.0)) {
            throw new IllegalArgumentException("perceptualRoughness must be in [0, 1]: " + perceptualRoughness);
        }
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        if (outAb.length < 2) {
            throw new IllegalArgumentException("outAb must hold 2 doubles: " + outAb.length);
        }
        double alpha = perceptualRoughness * perceptualRoughness;
        double a2 = alpha * alpha;
        double vx = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - nDotV * nDotV));
        double vz = nDotV;
        double sumA = 0.0;
        double sumB = 0.0;
        for (int i = 0; i < sampleCount; i++) {
            double u1 = (i + 0.5) / sampleCount;
            double u2 = radicalInverse(i);
            // GGX importance sampling of the half vector (pdf D(h) n.h)
            double phi = 2.0 * java.lang.Math.PI * u1;
            double cosTheta = java.lang.Math.sqrt((1.0 - u2) / (1.0 + (a2 - 1.0) * u2));
            double sinTheta = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - cosTheta * cosTheta));
            double hx = sinTheta * AccurateMath.cos(phi);
            double hz = cosTheta;
            double vDotH = vx * hx + vz * hz;
            double lz = 2.0 * vDotH * hz - vz;
            if (lz <= 0.0 || vDotH <= 0.0) {
                continue;
            }
            double nDotL = lz;
            double nDotH = hz;
            // G * vDotH / (nDotH nDotV): the estimator weight of the importance-sampled lobe
            double gVis;
            if (model == Model.HEIGHT_CORRELATED_SMITH) {
                double vis = Microfacetd.vSmithGgxCorrelated(nDotV, nDotL, alpha);
                gVis = 4.0 * vis * nDotL * vDotH / nDotH;
            } else {
                double g = Microfacetd.gSchlickGgxKaris(nDotV, alpha) * Microfacetd.gSchlickGgxKaris(nDotL, alpha);
                gVis = g * vDotH / (nDotH * nDotV);
            }
            double fc = 1.0 - vDotH;
            double fc2 = fc * fc;
            fc = fc2 * fc2 * fc;
            sumA += (1.0 - fc) * gVis;
            sumB += fc * gVis;
        }
        outAb[0] = sumA / sampleCount;
        outAb[1] = sumB / sampleCount;
    }

    /**
     * The cloth (sheen) DFG term of Filament: {@code ∫ D_Charlie(α) V_Neubelt nDotL dl} over the hemisphere, the
     * directional albedo of the Charlie sheen lobe, for {@code α = perceptualRoughness²}. Integrated by stratified
     * uniform sampling of the half vector ({@code pdf_l = 1 / (2π · 4 vDotH)}). Store it as the third DFG channel
     * and scale sheen energy with it, as Filament does. Roughness 0 is rejected: the Charlie distribution has no
     * finite limit there.
     *
     * @param nDotV the cosine between the normal and the view direction, in (0, 1]
     * @param perceptualRoughness the perceptual roughness, in (0, 1]
     * @param sampleCount the number of samples, positive
     * @return the directional albedo of the sheen lobe
     * @throws IllegalArgumentException if {@code nDotV} or {@code perceptualRoughness} is outside (0, 1] or
     *         {@code sampleCount} is not positive (NaN arguments included)
     * @since 2.3.0
     */
    public static double integrateCloth(double nDotV, double perceptualRoughness, int sampleCount) {
        checkCommon(nDotV, sampleCount);
        if (!(perceptualRoughness > 0.0 && perceptualRoughness <= 1.0)) {
            throw new IllegalArgumentException("perceptualRoughness must be in (0, 1]: " + perceptualRoughness);
        }
        double alpha = perceptualRoughness * perceptualRoughness;
        double vx = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - nDotV * nDotV));
        double vz = nDotV;
        double sum = 0.0;
        for (int i = 0; i < sampleCount; i++) {
            double u1 = (i + 0.5) / sampleCount;
            double u2 = radicalInverse(i);
            // uniform hemisphere direction for the half vector
            double phi = 2.0 * java.lang.Math.PI * u1;
            double cosTheta = 1.0 - u2;
            double sinTheta = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - cosTheta * cosTheta));
            double hx = sinTheta * AccurateMath.cos(phi);
            double hz = cosTheta;
            double vDotH = vx * hx + vz * hz;
            double lz = 2.0 * vDotH * hz - vz;
            if (lz <= 0.0 || vDotH <= 0.0) {
                continue;
            }
            double d = Microfacetd.dCharlie(hz, alpha);
            double v = Microfacetd.vNeubelt(nDotV, lz);
            sum += d * v * lz * vDotH;
        }
        return sum * (4.0 * 2.0 * java.lang.Math.PI / sampleCount);
    }

    /**
     * Builds the cloth DFG channel ({@code size × size}, u = nDotV, v = perceptual roughness, texel centers, the same
     * layout as {@link #build} but one float per texel: texel {@code (x, y)} is at index {@code y size + x}).
     *
     * @param size the table width and height in texels, positive
     * @param sampleCount samples per texel, positive
     * @param out receives the table, starting at index 0; length at least {@code size * size}
     * @throws IllegalArgumentException if {@code size} or {@code sampleCount} is not positive or {@code out} is too
     *         small (checked before anything is written)
     * @since 2.3.0
     */
    public static void buildCloth(int size, int sampleCount, float[] out) {
        if (size <= 0 || sampleCount <= 0) {
            throw new IllegalArgumentException("size and sampleCount must be positive");
        }
        if (out.length < (long) size * size) {
            throw new IllegalArgumentException("out must hold size * size floats");
        }
        for (int y = 0; y < size; y++) {
            double roughness = (y + 0.5) / size;
            for (int x = 0; x < size; x++) {
                out[y * size + x] = (float) integrateCloth((x + 0.5) / size, roughness, sampleCount);
            }
        }
    }

    private static void checkCommon(double nDotV, int sampleCount) {
        if (!(nDotV > 0.0 && nDotV <= 1.0)) {
            throw new IllegalArgumentException("nDotV must be in (0, 1]: " + nDotV);
        }
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("sampleCount must be positive: " + sampleCount);
        }
    }

    /** Van der Corput radical inverse in base 2. */
    private static double radicalInverse(int i) {
        return (Integer.reverse(i) & 0xFFFFFFFFL) * 0x1p-32;
    }
}
