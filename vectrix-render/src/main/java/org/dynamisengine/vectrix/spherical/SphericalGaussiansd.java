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
import org.dynamisengine.vectrix.core.Vector3dc;
import org.dynamisengine.vectrix.core.Vector4d;

/**
 * Spherical Gaussians (double precision). An SG is {@code G(v) = a · exp(λ (μ · v − 1))} with unit axis {@code μ},
 * sharpness {@code λ ≥ 0} and amplitude {@code a}; everything is linear in {@code a}, so RGB lobes use one call per
 * channel or scale the result. Lobes are written as {@code (axis.xyz, sharpness)} into a {@link Vector4d}.
 * <p>
 * Formulas follow Wang et al. 2009 and the Neubelt-Pettineo / Hill irradiance fit (as in "SG Series", 2016);
 * anisotropic SGs follow Xu et al. 2013. Sharpness fitting uses the Banerjee et al. 2005 estimate for the von
 * Mises-Fisher distribution, which has the same shape.
 *
 * @since 2.3.0
 */
public final class SphericalGaussiansd {
    /** Sharpness of the SG approximating a clamped cosine lobe (Wang et al. 2009). */
    public static final double COSINE_LOBE_SHARPNESS = 2.133;
    /** Amplitude of the SG approximating a clamped cosine lobe. */
    public static final double COSINE_LOBE_AMPLITUDE = 1.170;

    private SphericalGaussiansd() {
    }

    /**
     * Returns the value of the SG at a direction.
     *
     * @param axis the unit lobe axis
     * @param sharpness the sharpness {@code λ}, at least 0
     * @param amplitude the amplitude {@code a}
     * @param dir the unit direction to evaluate at
     * @return {@code a · exp(λ (axis · dir − 1))}
     */
    public static double evaluate(Vector3dc axis, double sharpness, double amplitude, Vector3dc dir) {
        return amplitude * AccurateMath.exp(sharpness * (axis.dot(dir) - 1.0));
    }

    /** {@code ∫ exp(λ (μ · v − 1)) dv = 2π (1 − e^(−2λ)) / λ}, stable for small {@code λ}. */
    private static double unitIntegral(double sharpness) {
        if (sharpness < 1e-3) {
            return (2.0 * java.lang.Math.PI) * (2.0 - 2.0 * sharpness + 4.0 / 3.0 * sharpness * sharpness);
        }
        return (2.0 * java.lang.Math.PI) * (1.0 - AccurateMath.exp(-2.0 * sharpness)) / sharpness;
    }

    /**
     * Returns the integral of the SG over the sphere, {@code 2π a (1 − e^(−2λ)) / λ}. A series is used for
     * {@code λ < 1e-3}, so {@code λ = 0} gives {@code 4π a}.
     *
     * @param sharpness the sharpness {@code λ}, at least 0
     * @param amplitude the amplitude {@code a}
     * @return the integral
     */
    public static double integral(double sharpness, double amplitude) {
        return amplitude * unitIntegral(sharpness);
    }

    /**
     * Product of two SGs, which is again an SG: writes its {@code (axis, sharpness)} to {@code dest} and returns its
     * amplitude. When the lobes cancel exactly (opposite axes, equal sharpness) the axis is {@code axis1}.
     *
     * @param axis1 the unit axis of the first lobe
     * @param sharpness1 the sharpness of the first lobe, at least 0
     * @param amplitude1 the amplitude of the first lobe
     * @param axis2 the unit axis of the second lobe
     * @param sharpness2 the sharpness of the second lobe, at least 0
     * @param amplitude2 the amplitude of the second lobe
     * @param dest receives the product's unit axis in xyz and its sharpness in w
     * @return the product's amplitude
     */
    public static double product(Vector3dc axis1, double sharpness1, double amplitude1, Vector3dc axis2, double sharpness2,
            double amplitude2, Vector4d dest) {
        double x = sharpness1 * axis1.x() + sharpness2 * axis2.x();
        double y = sharpness1 * axis1.y() + sharpness2 * axis2.y();
        double z = sharpness1 * axis1.z() + sharpness2 * axis2.z();
        double lm = java.lang.Math.sqrt(x * x + y * y + z * z);
        double amplitude = amplitude1 * amplitude2 * AccurateMath.exp(lm - sharpness1 - sharpness2);
        if (lm > 0.0) {
            dest.set(x / lm, y / lm, z / lm, lm);
        } else {
            dest.set(axis1.x(), axis1.y(), axis1.z(), 0.0);
        }
        return amplitude;
    }

    /**
     * Returns the inner product of two SGs, {@code ∫ G1(v) G2(v) dv} over the sphere.
     *
     * @param axis1 the unit axis of the first lobe
     * @param sharpness1 the sharpness of the first lobe, at least 0
     * @param amplitude1 the amplitude of the first lobe
     * @param axis2 the unit axis of the second lobe
     * @param sharpness2 the sharpness of the second lobe, at least 0
     * @param amplitude2 the amplitude of the second lobe
     * @return the integral of the product
     */
    public static double innerProduct(Vector3dc axis1, double sharpness1, double amplitude1, Vector3dc axis2,
            double sharpness2, double amplitude2) {
        double x = sharpness1 * axis1.x() + sharpness2 * axis2.x();
        double y = sharpness1 * axis1.y() + sharpness2 * axis2.y();
        double z = sharpness1 * axis1.z() + sharpness2 * axis2.z();
        double lm = java.lang.Math.sqrt(x * x + y * y + z * z);
        return amplitude1 * amplitude2 * AccurateMath.exp(lm - sharpness1 - sharpness2) * unitIntegral(lm);
    }

    /**
     * Irradiance at unit normal {@code n} from SG radiance, {@code ∫ G(v) max(n · v, 0) dv}, by Hill's fitted
     * approximation (Neubelt and Pettineo): within 2 % of the lobe's integral for {@code λ ≥ 1}. The fit diverges
     * for wide lobes (it tends to −∞ as {@code λ → 0}), so for {@code λ < 1} the exact irradiance is computed
     * instead, from the Legendre series of the lobe against the clamped cosine (Funk-Hecke); {@code λ = 0} gives
     * {@code π a}, the irradiance of a constant environment.
     *
     * @param axis the unit lobe axis
     * @param sharpness the sharpness {@code λ}, at least 0
     * @param amplitude the amplitude {@code a}
     * @param n the unit surface normal
     * @return the irradiance
     */
    public static double irradianceFitted(Vector3dc axis, double sharpness, double amplitude, Vector3dc n) {
        double muDotN = axis.dot(n);
        if (sharpness < 1.0) {
            return exactIrradiance(sharpness, muDotN) * amplitude;
        }
        double c0 = 0.36;
        double c1 = 1.0 / (4.0 * c0);
        double eml = AccurateMath.exp(-sharpness);
        double em2l = eml * eml;
        double rl = 1.0 / sharpness;
        double scale = 1.0 + 2.0 * em2l - rl;
        double bias = (eml - em2l) * rl - em2l;
        double x = java.lang.Math.sqrt(1.0 - scale);
        double x0 = c0 * muDotN;
        double x1 = c1 * x;
        double nn = x0 + x1;
        double y = java.lang.Math.max(0.0, java.lang.Math.min(1.0, muDotN));
        if (java.lang.Math.abs(x0) <= x1) {
            y = nn * nn / x;
        }
        double result = scale * y + bias;
        return result * amplitude * (2.0 * java.lang.Math.PI) / sharpness;
    }

    /** {@code Â_l / π} of the clamped cosine, {@code 2 ∫₀¹ P_l(t) t dt}, for l = 0..14. */
    private static final double[] CLAMPED_COSINE = {1.0, 2.0 / 3.0, 1.0 / 4.0, 0.0, -1.0 / 24.0, 0.0, 1.0 / 64.0,
        0.0, -1.0 / 128.0, 0.0, 7.0 / 1536.0, 0.0, -3.0 / 1024.0, 0.0, 33.0 / 16384.0};

    /**
     * Exact {@code ∫ exp(λ (μ · v − 1)) max(n · v, 0) dv} for {@code 0 <= λ < 1}, {@code c = μ · n}: the lobe's Legendre
     * coefficients {@code (2l + 1) e^(−λ) i_l(λ)} (modified spherical Bessel functions, by their power series) times
     * the clamped cosine's {@code Â_l P_l(c)}. Terms beyond l = 14 are below double rounding for {@code λ < 1}.
     */
    private static double exactIrradiance(double lambda, double c) {
        double x = 0.5 * lambda * lambda;
        double e = 0.0;
        double pPrev = 0.0, pl = 1.0; // P_(l-1)(c), P_l(c)
        double lambdaPow = 1.0, doubleFactorial = 1.0; // λ^l, (2l + 1)!!
        for (int l = 0; l < CLAMPED_COSINE.length; l++) {
            if (l > 0) {
                double next = ((2 * l - 1) * c * pl - (l - 1) * pPrev) / l;
                pPrev = pl;
                pl = next;
                lambdaPow *= lambda;
                doubleFactorial *= 2 * l + 1;
            }
            if (CLAMPED_COSINE[l] != 0.0) {
                double term = 1.0, series = 1.0;
                for (int k = 1; k <= 8; k++) {
                    term *= x / (k * (2.0 * l + 2.0 * k + 1.0));
                    series += term;
                }
                e += (2 * l + 1) * lambdaPow / doubleFactorial * series * CLAMPED_COSINE[l] * pl;
            }
        }
        return e * java.lang.Math.PI * AccurateMath.exp(-lambda);
    }

    /**
     * Irradiance at unit normal {@code n} by the inner product with an SG fit of the clamped cosine lobe
     * ({@link #COSINE_LOBE_SHARPNESS}, {@link #COSINE_LOBE_AMPLITUDE}): cheaper but coarse, with errors up to about
     * 15 % of the lobe's integral (against about 2 % for {@link #irradianceFitted}), largest for wide lobes.
     *
     * @param axis the unit lobe axis
     * @param sharpness the sharpness {@code λ}, at least 0
     * @param amplitude the amplitude {@code a}
     * @param n the unit surface normal
     * @return the irradiance
     */
    public static double irradianceInnerProduct(Vector3dc axis, double sharpness, double amplitude, Vector3dc n) {
        return innerProduct(axis, sharpness, amplitude, n, COSINE_LOBE_SHARPNESS, COSINE_LOBE_AMPLITUDE);
    }

    /**
     * Sharpness whose lobe has mean resultant length {@code r} (the length of the average of its unit directions,
     * in [0, 1)): {@code λ ≈ r (3 − r²) / (1 − r²)} (Banerjee et al. 2005). {@code r} is clamped to
     * {@code [0, 0.9999]}, so the result is finite.
     *
     * @param meanResultantLength the mean resultant length {@code r}
     * @return the sharpness, at least 0
     */
    public static double fitSharpness(double meanResultantLength) {
        double r = java.lang.Math.max(0.0, java.lang.Math.min(meanResultantLength, 0.9999));
        return r * (3.0 - r * r) / (1.0 - r * r);
    }

    /**
     * Fits an SG to weighted directions ({@code dirs} xyz, unit; {@code weights} are sample values times their
     * solid-angle weights, as in a Monte Carlo estimate): the axis is the weighted mean direction, the sharpness
     * comes from its length ({@link #fitSharpness}), and the amplitude makes the SG's integral equal the total
     * weight. Writes {@code (axis, sharpness)} to {@code dest} and returns the amplitude. If the total weight is
     * not positive (or NaN), {@code dest} is set to {@code (0, 0, 1, 0)} and 0 is returned; if the weighted mean
     * direction is zero, the axis is {@code (0, 0, 1)}. Sums are accumulated in double. Does not allocate.
     *
     * @param dirs the unit directions, packed xyz: direction {@code i} at {@code dirs[dirsOffset + 3 i ..]}
     * @param dirsOffset the index of the first direction's x
     * @param weights the weights: weight {@code i} at {@code weights[weightsOffset + i]}
     * @param weightsOffset the index of the first weight
     * @param count the number of directions, at least 0
     * @param dest receives the unit axis in xyz and the sharpness in w
     * @return the amplitude
     * @throws IllegalArgumentException if {@code count} or an offset is negative, or an array is too small for
     *         {@code count} entries from its offset (checked before anything is written)
     * @since 3.0.0
     */
    public static double fit(double[] dirs, int dirsOffset, double[] weights, int weightsOffset, int count, Vector4d dest) {
        if (count < 0 || dirsOffset < 0 || weightsOffset < 0) {
            throw new IllegalArgumentException("count and offsets must be at least 0");
        }
        if (dirsOffset + 3L * count > dirs.length) {
            throw new IllegalArgumentException("dirs too small for " + count + " directions from " + dirsOffset);
        }
        if ((long) weightsOffset + count > weights.length) {
            throw new IllegalArgumentException("weights too small for " + count + " weights from " + weightsOffset);
        }
        double sx = 0.0, sy = 0.0, sz = 0.0, sw = 0.0; // double sums: count may be large
        for (int i = 0; i < count; i++) {
            double w = weights[weightsOffset + i];
            int o = dirsOffset + 3 * i;
            sx += w * dirs[o];
            sy += w * dirs[o + 1];
            sz += w * dirs[o + 2];
            sw += w;
        }
        if (!(sw > 0.0)) {
            dest.set(0.0, 0.0, 1.0, 0.0);
            return 0.0;
        }
        double len = java.lang.Math.sqrt(sx * sx + sy * sy + sz * sz);
        double sharpness = fitSharpness((len / sw));
        if (len > 0.0) {
            dest.set((sx / len), (sy / len), (sz / len), sharpness);
        } else {
            dest.set(0.0, 0.0, 1.0, sharpness);
        }
        return (sw / unitIntegral(sharpness));
    }

    /**
     * Anisotropic SG (Xu et al. 2013): {@code a · max(v · z, 0) · exp(−λ (v · x)² − μ (v · y)²)} for an orthonormal
     * frame {@code (x, y, z)} with lobe axis {@code z} and bandwidths {@code λ} (along {@code x}) and {@code μ}
     * (along {@code y}).
     *
     * @param x the unit tangent along which {@code lambda} applies
     * @param y the unit bitangent along which {@code mu} applies
     * @param z the unit lobe axis
     * @param lambda the bandwidth along {@code x}, at least 0
     * @param mu the bandwidth along {@code y}, at least 0
     * @param amplitude the amplitude {@code a}
     * @param dir the unit direction to evaluate at
     * @return the lobe value, 0 in the hemisphere opposite {@code z}
     */
    public static double evaluateAnisotropic(Vector3dc x, Vector3dc y, Vector3dc z, double lambda, double mu,
            double amplitude, Vector3dc dir) {
        double s = java.lang.Math.max(z.dot(dir), 0.0);
        double dx = x.dot(dir), dy = y.dot(dir);
        return amplitude * s * AccurateMath.exp(-lambda * dx * dx - mu * dy * dy);
    }

    /**
     * Returns the approximate integral of an anisotropic SG, {@code π a / √(λ μ)}: accurate for large bandwidths,
     * infinite when either bandwidth is 0.
     *
     * @param lambda the bandwidth along {@code x}, greater than 0
     * @param mu the bandwidth along {@code y}, greater than 0
     * @param amplitude the amplitude {@code a}
     * @return the approximate integral
     */
    public static double integralAnisotropic(double lambda, double mu, double amplitude) {
        return java.lang.Math.PI * amplitude / java.lang.Math.sqrt(lambda * mu);
    }
}
