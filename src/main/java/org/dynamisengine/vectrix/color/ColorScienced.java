/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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
package org.dynamisengine.vectrix.color;

import org.dynamisengine.vectrix.core.Vector3d;

/**
 * Minimal color science helpers including CIE 1931 approximations.
  * @since 1.0.0
  */
public final class ColorScienced {
    public static final double VISIBLE_MIN_NM = 380.0;
    public static final double VISIBLE_MAX_NM = 780.0;

    private ColorScienced() {
    }

    /**
     * CIE 1931 2-degree colour matching functions (xBar, yBar, zBar), using the multi-lobe piecewise-Gaussian
     * fit of Wyman, Sloan and Shirley (JCGT 2013). See {@link ColorSciencef#cie1931XyzBar}.
     */
    public static Vector3d cie1931XyzBar(double wavelengthNm, Vector3d dest) {
        double wl = clamp(wavelengthNm, VISIBLE_MIN_NM, VISIBLE_MAX_NM);
        double x = 1.056 * g(wl, 599.8, 0.0264, 0.0323) + 0.362 * g(wl, 442.0, 0.0624, 0.0374)
                - 0.065 * g(wl, 501.1, 0.0490, 0.0382);
        double y = 0.821 * g(wl, 568.8, 0.0213, 0.0247) + 0.286 * g(wl, 530.9, 0.0613, 0.0322);
        double z = 1.217 * g(wl, 437.0, 0.0845, 0.0278) + 0.681 * g(wl, 459.0, 0.0385, 0.0725);
        return dest.set(x, y, z);
    }

    public static Vector3d xyzToLinearSrgb(double x, double y, double z, Vector3d dest) {
        // Single source with ColorMath (7-digit IEC 61966-2-1 matrix); this used to keep a 4-digit copy.
        return ColorMathd.xyzToLinearSrgb(x, y, z, dest);
    }

    public static Vector3d linearSrgbToXyz(double r, double g, double b, Vector3d dest) {
        return ColorMathd.linearSrgbToXyz(r, g, b, dest);
    }

    private static double g(double wl, double mu, double sigmaLo, double sigmaHi) {
        double d = (wl - mu) * (wl < mu ? sigmaLo : sigmaHi);
        return org.dynamisengine.vectrix.core.AccurateMath.exp(-0.5 * d * d);
    }

    /**
     * Color of a blackbody (Planckian radiator) at the given temperature, in linear sRGB, normalised so the largest
     * channel is 1.
     * <p>
     * The chromaticity comes from the Planckian locus in CIE 1931 xy (cubic-spline fit by Kim et al., 2002, valid
     * from 1667 K to 25000 K, within about 1e-4 of the exact locus) and is converted through the same XYZ to
     * linear-sRGB matrix as {@code ColorMath*.xyzToLinearSrgb}. Below about 1900 K the color lies outside the sRGB
     * gamut and its negative blue component is clipped to 0. The result is smooth in temperature: earlier versions
     * used the Tanner Helland curve fit, which jumped at 6600 K. Note that 6500 K is close to, but not the same as,
     * D65 (white): the Planckian locus passes slightly below D65 in y, so it is a little magenta.
     *
     * @param kelvin color temperature in Kelvin; clamped to [1667, 25000]
     * @param dest   destination vector to store the linear RGB result
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code dest} is null, or kelvin is non-finite or &lt;= 0
     */
    public static Vector3d kelvinToLinearRgb(double kelvin, Vector3d dest) {
        if (dest == null) {
            throw new IllegalArgumentException("dest is required");
        }
        if (!Double.isFinite(kelvin) || kelvin <= 0.0) {
            throw new IllegalArgumentException("kelvin must be finite and > 0");
        }
        double t = clampPlanckianKelvin(kelvin);
        double x = planckianX(t);
        double y = planckianY(x, t);
        ColorMathd.xyzToLinearSrgb(x / y, 1.0, (1.0 - x - y) / y, dest);
        double r = java.lang.Math.max(dest.x, 0.0), g = java.lang.Math.max(dest.y, 0.0), bl = java.lang.Math.max(dest.z, 0.0);
        double max = java.lang.Math.max(r, java.lang.Math.max(g, bl));
        return dest.set(r / max, g / max, bl / max);
    }

    static double clampPlanckianKelvin(double kelvin) {
        return java.lang.Math.max(1667.0, java.lang.Math.min(25000.0, kelvin));
    }

    /** CIE 1931 x of the Planckian locus at {@code t} Kelvin (Kim et al. 2002), {@code t} in [1667, 25000]. */
    static double planckianX(double t) {
        double u = 1e3 / t;
        if (t <= 4000.0) {
            return ((-0.2661239 * u - 0.2343589) * u + 0.8776956) * u + 0.179910;
        }
        return ((-3.0258469 * u + 2.1070379) * u + 0.2226347) * u + 0.240390;
    }

    /** CIE 1931 y of the Planckian locus at {@code t} Kelvin, given its x (Kim et al. 2002). */
    static double planckianY(double x, double t) {
        if (t <= 2222.0) {
            return ((-1.1063814 * x - 1.34811020) * x + 2.18555832) * x - 0.20219683;
        }
        if (t <= 4000.0) {
            return ((-0.9549476 * x - 1.37418593) * x + 2.09137015) * x - 0.16748867;
        }
        return ((3.0817580 * x - 5.87338670) * x + 3.75112997) * x - 0.37001483;
    }

    private static double clamp(double v, double lo, double hi) {
        return java.lang.Math.max(lo, java.lang.Math.min(hi, v));
    }
}
