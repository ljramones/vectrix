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
package org.dynamisengine.vectrix.atmosphere;

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * Phase functions for participating media (single precision results; sampling is computed in double precision),
 * each normalized to 1 over the sphere and written in terms of {@code u = cos θ}, the cosine between the incident
 * propagation direction and the scattered direction ({@code u = 1}: forward). Samplers map a uniform random number
 * {@code xi} in [0, 1) to {@code u} by exact inversion of the cumulative distribution, so they are also usable for
 * stratified and low-discrepancy sampling; the azimuth is uniform and left to the caller.
 * <p>
 * References: Henyey and Greenstein 1941; Cornette and Shanks 1992; Draine 2003; Jendersie and d'Eon, "An
 * Approximate Mie Scattering Function for Fog and Cloud Rendering" (SIGGRAPH 2023 talk and supplemental), whose
 * fitted parameters are approximations of averaged Mie scattering by design.
 *
 * @since 2.3.0
 */
public final class PhaseFunctionsf {
    private static final double INV_4PI = 1.0 / (4.0 * java.lang.Math.PI);

    private PhaseFunctionsf() {
    }

    /**
     * Returns the Rayleigh phase function {@code 3 (1 + u²) / (16π)}.
     *
     * @param u the cosine of the scattering angle, in [-1, 1]
     * @return the phase function value, per steradian
     */
    public static float rayleigh(float u) {
        return (float) (3.0 / (16.0 * java.lang.Math.PI) * (1.0 + (double) u * u));
    }

    /**
     * Samples the Rayleigh phase function by exact inversion of its CDF: the real root of
     * {@code u³ + 3u = 4 (2ξ − 1)}.
     *
     * @param xi a uniform number in [0, 1]
     * @return the cosine of the scattering angle, in [-1, 1]
     */
    public static float sampleRayleigh(float xi) {
        double q = 4.0 * (2.0 * xi - 1.0);
        double a = AccurateMath.cbrt(0.5 * java.lang.Math.abs(q) + java.lang.Math.sqrt(0.25 * q * q + 1.0));
        double u = a - 1.0 / a;
        return (float) (q < 0.0 ? -u : u);
    }

    /**
     * Returns the Henyey-Greenstein phase function {@code (1 − g²) / (4π (1 + g² − 2gu)^(3/2))}.
     *
     * @param u the cosine of the scattering angle, in [-1, 1]
     * @param g the mean cosine, in (−1, 1); positive is forward scattering
     * @return the phase function value, per steradian
     */
    public static float henyeyGreenstein(float u, float g) {
        double gg = g;
        double den = 1.0 + gg * gg - 2.0 * gg * u;
        return (float) (INV_4PI * (1.0 - gg * gg) / (den * java.lang.Math.sqrt(den)));
    }

    /**
     * Exact inverse-CDF sample of Henyey-Greenstein. The usual {@code (1 + g² − t²) / (2g)} form loses all precision
     * as {@code g → 0}; this is the same expression expanded in {@code s = 2ξ − 1}, exact for every {@code g},
     * including 0: {@code u = (s + g (3 + s²)/2 + g² s + g³ (s² − 1)/2) / (1 + g s)²}. The result is clamped to
     * [-1, 1].
     *
     * @param xi a uniform number in [0, 1]
     * @param g the mean cosine, in (−1, 1)
     * @return the cosine of the scattering angle, in [-1, 1]
     */
    public static float sampleHenyeyGreenstein(float xi, float g) {
        return (float) sampleHg(xi, g);
    }

    static double sampleHg(double xi, double g) {
        double s = 2.0 * xi - 1.0;
        double d = 1.0 + g * s;
        double u = (s + g * (0.5 * (3.0 + s * s) + g * (s + 0.5 * g * (s * s - 1.0)))) / (d * d);
        return java.lang.Math.max(-1.0, java.lang.Math.min(1.0, u));
    }

    /**
     * Returns the Cornette-Shanks phase function {@code 3 (1 − g²)(1 + u²) / (8π (2 + g²)(1 + g² − 2gu)^(3/2))}
     * (Draine with α = 1).
     *
     * @param u the cosine of the scattering angle, in [-1, 1]
     * @param g the asymmetry parameter, in (−1, 1); it is not the mean cosine (see {@link #draineMeanCosine})
     * @return the phase function value, per steradian
     */
    public static float cornetteShanks(float u, float g) {
        return draine(u, g, 1.0f);
    }

    /**
     * Samples the Cornette-Shanks phase function by inverting its CDF (see {@link #sampleDraine}).
     *
     * @param xi a uniform number in [0, 1]
     * @param g the asymmetry parameter, in (−1, 1)
     * @return the cosine of the scattering angle, in [-1, 1]
     */
    public static float sampleCornetteShanks(float xi, float g) {
        return sampleDraine(xi, g, 1.0f);
    }

    /**
     * Draine's phase function {@code (1/4π) (1 − g²) / (1 + g² − 2gu)^(3/2) · (1 + α u²) / (1 + α (1 + 2g²)/3)}: HG
     * for {@code α = 0}, Cornette-Shanks for {@code α = 1}, Rayleigh for {@code g = 0, α = 1}.
     *
     * @param u the cosine of the scattering angle, in [-1, 1]
     * @param g the asymmetry parameter, in (−1, 1)
     * @param alpha the shape parameter α, at least 0
     * @return the phase function value, per steradian
     */
    public static float draine(float u, float g, float alpha) {
        double gg = g, uu = u, a = alpha;
        double den = 1.0 + gg * gg - 2.0 * gg * uu;
        return (float) (INV_4PI * (1.0 - gg * gg) / (den * java.lang.Math.sqrt(den)) * (1.0 + a * uu * uu)
                / (1.0 + a * (1.0 + 2.0 * gg * gg) / 3.0));
    }

    /**
     * Returns the mean cosine of Draine's phase function: {@code g (1 + α (3 + 2g²)/5) / (1 + α (1 + 2g²)/3)} (from
     * the HG moments {@code ⟨u³⟩ = (2g³ + 3g)/5}).
     *
     * @param g the asymmetry parameter, in (−1, 1)
     * @param alpha the shape parameter α, at least 0
     * @return the mean cosine {@code ⟨u⟩}
     */
    public static float draineMeanCosine(float g, float alpha) {
        double gg = g, k = 1.0 + 2.0 * gg * gg;
        return (float) (gg * (1.0 + alpha * (3.0 + 2.0 * gg * gg) / 5.0) / (1.0 + alpha * k / 3.0));
    }

    /**
     * Exact inverse-CDF sample of Draine's phase function: the closed-form quartic solution of Jendersie and d'Eon
     * (supplemental eq. 6) as the starting point where it is well conditioned, refined by safeguarded Newton steps on
     * the exact CDF (analytic, or by quadrature for small {@code |g|}, where the analytic form cancels).
     * {@code alpha = 0} uses {@link #sampleHenyeyGreenstein}'s formula.
     *
     * @param xi a uniform number in [0, 1]
     * @param g the asymmetry parameter, in (−1, 1)
     * @param alpha the shape parameter α, at least 0
     * @return the cosine of the scattering angle, in [-1, 1]
     */
    public static float sampleDraine(float xi, float g, float alpha) {
        return (float) DraineSampling.sample(xi, g, alpha);
    }

    /**
     * The fog/cloud model {@code (1 − wD) · HG(gHG) + wD · Draine(gD, α)} for parameters
     * {@code (gHG, gD, α, wD)} in {@code params} (e.g. from {@link #fogParameters}).
     *
     * @param u the cosine of the scattering angle, in [-1, 1]
     * @param params {@code (gHG, gD, α, wD)} at indices 0 to 3
     * @return the phase function value, per steradian
     */
    public static float fog(float u, float[] params) {
        float w = params[3];
        return (1.0f - w) * henyeyGreenstein(u, params[0]) + w * draine(u, params[1], params[2]);
    }

    /**
     * Samples the fog model: {@code xiLobe} picks the lobe (Draine when below {@code wD}), {@code xi} samples it.
     *
     * @param xiLobe a uniform number in [0, 1) that selects the lobe
     * @param xi a uniform number in [0, 1] that samples the selected lobe
     * @param params {@code (gHG, gD, α, wD)} at indices 0 to 3
     * @return the cosine of the scattering angle, in [-1, 1]
     */
    public static float sampleFog(float xiLobe, float xi, float[] params) {
        return xiLobe < params[3] ? sampleDraine(xi, params[1], params[2]) : sampleHenyeyGreenstein(xi, params[0]);
    }

    /**
     * Fog/cloud model parameters {@code (gHG, gD, α, wD)} for water droplets of mean diameter {@code d} micrometres
     * ({@code 0 < d ≤ 50}), from the published fits: {@code d ≤ 0.1}, {@code 0.1 < d < 1.5}, {@code 1.5 ≤ d < 5},
     * {@code 5 ≤ d ≤ 50} (supplemental eqs. 7-22; the fits are not continuous across the range boundaries).
     * {@code d} is not validated: values above 50 extrapolate the last fit, and {@code d <= 0} gives NaN or
     * meaningless parameters.
     *
     * @param d the mean droplet diameter, in micrometres, in (0, 50]
     * @param out receives {@code (gHG, gD, α, wD)} at indices 0 to 3; length at least 4
     * @return {@code out}
     */
    public static float[] fogParameters(float d, float[] out) {
        double x = d, gHg, gD, alpha, wD;
        if (x <= 0.1) {
            gHg = 13.8 * x * x;
            gD = 1.1456 * x * AccurateMath.sin(9.29044 * x);
            alpha = 250.0;
            wD = 0.252977 - 312.983 * AccurateMath.pow(x, 4.3);
        } else if (x < 1.5) {
            double l = AccurateMath.log(x);
            gHg = 0.862 - 0.143 * l * l;
            gD = 0.379685 * AccurateMath.cos(1.19692 * AccurateMath.cos((l - 0.238604) * (l + 1.00667)
                    / (0.507522 - 0.15677 * l)) + 1.37932 * l + 0.0625835) + 0.344213;
            alpha = 250.0;
            wD = 0.146209 * AccurateMath.cos(3.38707 * l + 2.11193) + 0.316072 + 0.0778917 * l;
        } else if (x < 5.0) {
            double l = AccurateMath.log(x), ll = AccurateMath.log(l);
            gHg = 0.0604931 * ll + 0.940256;
            gD = 0.500411 - 0.081287 / (-2.0 * l + AccurateMath.tan(l) + 1.27551);
            alpha = 7.30354 * l + 6.31675;
            wD = 0.026914 * (l - AccurateMath.cos(5.68947 * (ll - 0.0292149))) + 0.376475;
        } else {
            gHg = AccurateMath.exp(-0.0990567 / (x - 1.67154));
            gD = AccurateMath.exp(-2.20679 / (x + 3.91029) - 0.428934);
            alpha = AccurateMath.exp(3.62489 - 8.29288 / (x + 5.52825));
            wD = AccurateMath.exp(-0.599085 / (x - 0.641583) - 0.665888);
        }
        out[0] = (float) gHg;
        out[1] = (float) gD;
        out[2] = (float) alpha;
        out[3] = (float) wD;
        return out;
    }
}
