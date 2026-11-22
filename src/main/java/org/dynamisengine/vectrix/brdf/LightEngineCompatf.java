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
 * DynamisLightEngine's current shader BRDF, reproduced exactly (single precision), so CPU-side tools and tests can
 * match what the renderer draws today and measure what changes when it moves to {@link Microfacetf}.
 * <p>
 * Source: {@code distributionGGX}, {@code geometrySchlickGGX}, {@code geometrySmith} and {@code fresnelSchlick} in
 * {@code engine-impl-vulkan VulkanMainFragmentShaderPart0} and {@code engine-impl-opengl GlShaderSources}. They follow
 * the UE4 / LearnOpenGL formulation: GGX with {@code α = roughness²} but π written as 3.14159 and the denominator
 * clamped to 1e-4, and a separable Schlick-GGX masking term with Karis's direct-lighting remap
 * {@code k = (roughness + 1)² / 8} instead of height-correlated Smith.
 *
 * @since 2.1.0
 */
public final class LightEngineCompatf {
    private LightEngineCompatf() {
    }

    /** {@code distributionGGX(ndh, roughness)}: {@code a² / max(3.14159 d², 0.0001)}, {@code a = roughness²}. */
    public static float distributionGgx(float nDotH, float roughness) {
        float a = roughness * roughness;
        float a2 = a * a;
        float d = (nDotH * nDotH) * (a2 - 1.0f) + 1.0f;
        return a2 / java.lang.Math.max(3.14159f * d * d, 0.0001f);
    }

    /** {@code geometrySchlickGGX(ndv, roughness)} with {@code k = (roughness + 1)² / 8}. */
    public static float geometrySchlickGgx(float nDotX, float roughness) {
        float r = roughness + 1.0f;
        float k = (r * r) / 8.0f;
        return nDotX / java.lang.Math.max(nDotX * (1.0f - k) + k, 0.0001f);
    }

    /** {@code geometrySmith(ndv, ndl, roughness)}: the product of the two separable terms. */
    public static float geometrySmith(float nDotV, float nDotL, float roughness) {
        return geometrySchlickGgx(nDotV, roughness) * geometrySchlickGgx(nDotL, roughness);
    }

    /** {@code fresnelSchlick(cosTheta, f0)} for one channel: {@code f0 + (1 - f0) pow(1 - cosTheta, 5)}. */
    public static float fresnelSchlick(float cosTheta, float f0) {
        return f0 + (1.0f - f0) * (float) org.dynamisengine.vectrix.core.AccurateMath.pow(1.0f - cosTheta, 5.0f);
    }
}
