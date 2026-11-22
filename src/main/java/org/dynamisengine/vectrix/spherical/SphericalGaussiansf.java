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
package org.dynamisengine.vectrix.spherical;

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.core.Vector3fc;
import org.dynamisengine.vectrix.core.Vector4f;

/**
 * Spherical Gaussians (single precision). An SG is {@code G(v) = a · exp(λ (μ · v − 1))} with unit axis {@code μ},
 * sharpness {@code λ ≥ 0} and amplitude {@code a}; everything is linear in {@code a}, so RGB lobes use one call per
 * channel or scale the result. Lobes are written as {@code (axis.xyz, sharpness)} into a {@link Vector4f}.
 * <p>
 * Formulas follow Wang et al. 2009 and the Neubelt-Pettineo / Hill irradiance fit (as in "SG Series", 2016);
 * anisotropic SGs follow Xu et al. 2013. Sharpness fitting uses the Banerjee et al. 2005 estimate for the von
 * Mises-Fisher distribution, which has the same shape.
 *
 * @since 2.3.0
 */
public final class SphericalGaussiansf {
    /** Sharpness of the SG approximating a clamped cosine lobe (Wang et al. 2009). */
    public static final float COSINE_LOBE_SHARPNESS = 2.133f;
    /** Amplitude of the SG approximating a clamped cosine lobe. */
    public static final float COSINE_LOBE_AMPLITUDE = 1.170f;

    private SphericalGaussiansf() {
    }

    /** Value of the SG at unit direction {@code dir}. */
    public static float evaluate(Vector3fc axis, float sharpness, float amplitude, Vector3fc dir) {
        return amplitude * (float) AccurateMath.exp(sharpness * (axis.dot(dir) - 1.0f));
    }

    /** {@code ∫ exp(λ (μ · v − 1)) dv = 2π (1 − e^(−2λ)) / λ}, stable for small {@code λ}. */
    private static float unitIntegral(float sharpness) {
        if (sharpness < 1e-3f) {
            return (float) (2.0 * java.lang.Math.PI) * (2.0f - 2.0f * sharpness + 4.0f / 3.0f * sharpness * sharpness);
        }
        return (float) (2.0 * java.lang.Math.PI) * (1.0f - (float) AccurateMath.exp(-2.0f * sharpness)) / sharpness;
    }

    /** Integral of the SG over the sphere. */
    public static float integral(float sharpness, float amplitude) {
        return amplitude * unitIntegral(sharpness);
    }

    /**
     * Product of two SGs, which is again an SG: writes its {@code (axis, sharpness)} to {@code dest} and returns its
     * amplitude. When the lobes cancel exactly (opposite axes, equal sharpness) the axis is {@code axis1}.
     */
    public static float product(Vector3fc axis1, float sharpness1, float amplitude1, Vector3fc axis2, float sharpness2,
            float amplitude2, Vector4f dest) {
        float x = sharpness1 * axis1.x() + sharpness2 * axis2.x();
        float y = sharpness1 * axis1.y() + sharpness2 * axis2.y();
        float z = sharpness1 * axis1.z() + sharpness2 * axis2.z();
        float lm = (float) java.lang.Math.sqrt(x * x + y * y + z * z);
        float amplitude = amplitude1 * amplitude2 * (float) AccurateMath.exp(lm - sharpness1 - sharpness2);
        if (lm > 0.0f) {
            dest.set(x / lm, y / lm, z / lm, lm);
        } else {
            dest.set(axis1.x(), axis1.y(), axis1.z(), 0.0f);
        }
        return amplitude;
    }

    /** {@code ∫ G1 G2} over the sphere. */
    public static float innerProduct(Vector3fc axis1, float sharpness1, float amplitude1, Vector3fc axis2,
            float sharpness2, float amplitude2) {
        float x = sharpness1 * axis1.x() + sharpness2 * axis2.x();
        float y = sharpness1 * axis1.y() + sharpness2 * axis2.y();
        float z = sharpness1 * axis1.z() + sharpness2 * axis2.z();
        float lm = (float) java.lang.Math.sqrt(x * x + y * y + z * z);
        return amplitude1 * amplitude2 * (float) AccurateMath.exp(lm - sharpness1 - sharpness2) * unitIntegral(lm);
    }

    /**
     * Irradiance at unit normal {@code n} from SG radiance, {@code ∫ G(v) max(n · v, 0) dv}, by Hill's fitted
     * approximation (Neubelt and Pettineo): within 2 % of the lobe's integral for {@code λ ≥ 1}.
     */
    public static float irradianceFitted(Vector3fc axis, float sharpness, float amplitude, Vector3fc n) {
        float muDotN = axis.dot(n);
        float c0 = 0.36f;
        float c1 = 1.0f / (4.0f * c0);
        float eml = (float) AccurateMath.exp(-sharpness);
        float em2l = eml * eml;
        float rl = 1.0f / sharpness;
        float scale = 1.0f + 2.0f * em2l - rl;
        float bias = (eml - em2l) * rl - em2l;
        float x = (float) java.lang.Math.sqrt(1.0f - scale);
        float x0 = c0 * muDotN;
        float x1 = c1 * x;
        float nn = x0 + x1;
        float y = java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, muDotN));
        if (java.lang.Math.abs(x0) <= x1) {
            y = nn * nn / x;
        }
        float result = scale * y + bias;
        return result * amplitude * (float) (2.0 * java.lang.Math.PI) / sharpness;
    }

    /**
     * Irradiance at unit normal {@code n} by the inner product with an SG fit of the clamped cosine lobe
     * ({@link #COSINE_LOBE_SHARPNESS}, {@link #COSINE_LOBE_AMPLITUDE}): cheaper but coarse, with errors up to about
     * 15 % of the lobe's integral (against about 2 % for {@link #irradianceFitted}), largest for wide lobes.
     */
    public static float irradianceInnerProduct(Vector3fc axis, float sharpness, float amplitude, Vector3fc n) {
        return innerProduct(axis, sharpness, amplitude, n, COSINE_LOBE_SHARPNESS, COSINE_LOBE_AMPLITUDE);
    }

    /**
     * Sharpness whose lobe has mean resultant length {@code r} (the length of the average of its unit directions,
     * in [0, 1)): {@code λ ≈ r (3 − r²) / (1 − r²)} (Banerjee et al. 2005). {@code r} is clamped below 1.
     */
    public static float fitSharpness(float meanResultantLength) {
        float r = java.lang.Math.max(0.0f, java.lang.Math.min(meanResultantLength, 0.9999f));
        return r * (3.0f - r * r) / (1.0f - r * r);
    }

    /**
     * Fits an SG to weighted directions ({@code dirs} xyz, unit; {@code weights} are sample values times their
     * solid-angle weights, as in a Monte Carlo estimate): the axis is the weighted mean direction, the sharpness
     * comes from its length ({@link #fitSharpness}), and the amplitude makes the SG's integral equal the total
     * weight. Writes {@code (axis, sharpness)} to {@code dest} and returns the amplitude (0 if the total weight is
     * not positive).
     */
    public static float fit(float[] dirs, float[] weights, int count, Vector4f dest) {
        double sx = 0.0, sy = 0.0, sz = 0.0, sw = 0.0; // double sums: count may be large
        for (int i = 0; i < count; i++) {
            double w = weights[i];
            sx += w * dirs[3 * i];
            sy += w * dirs[3 * i + 1];
            sz += w * dirs[3 * i + 2];
            sw += w;
        }
        if (!(sw > 0.0f)) {
            dest.set(0.0f, 0.0f, 1.0f, 0.0f);
            return 0.0f;
        }
        double len = java.lang.Math.sqrt(sx * sx + sy * sy + sz * sz);
        float sharpness = fitSharpness((float) (len / sw));
        if (len > 0.0) {
            dest.set((float) (sx / len), (float) (sy / len), (float) (sz / len), sharpness);
        } else {
            dest.set(0.0f, 0.0f, 1.0f, sharpness);
        }
        return (float) (sw / unitIntegral(sharpness));
    }

    /**
     * Anisotropic SG (Xu et al. 2013): {@code a · max(v · z, 0) · exp(−λ (v · x)² − μ (v · y)²)} for an orthonormal
     * frame {@code (x, y, z)} with lobe axis {@code z} and bandwidths {@code λ} (along {@code x}) and {@code μ}.
     */
    public static float evaluateAnisotropic(Vector3fc x, Vector3fc y, Vector3fc z, float lambda, float mu,
            float amplitude, Vector3fc dir) {
        float s = java.lang.Math.max(z.dot(dir), 0.0f);
        float dx = x.dot(dir), dy = y.dot(dir);
        return amplitude * s * (float) AccurateMath.exp(-lambda * dx * dx - mu * dy * dy);
    }

    /** Approximate integral of an anisotropic SG, {@code π a / √(λ μ)}: accurate for large bandwidths. */
    public static float integralAnisotropic(float lambda, float mu, float amplitude) {
        return (float) java.lang.Math.PI * amplitude / (float) java.lang.Math.sqrt(lambda * mu);
    }
}
