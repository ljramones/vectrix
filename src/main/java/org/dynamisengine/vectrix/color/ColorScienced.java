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
     * Double-precision {@link ColorSciencef#kelvinToLinearRgb(float, org.dynamisengine.vectrix.core.Vector3f)}:
     * approximate blackbody color (Tanner Helland fit) in linear sRGB.
     * <p>
     * The Helland fit is piecewise at 6600 K, so the result has a visible step there: in linear RGB, green drops from
     * 1.000 to 0.970 and blue rises from 0.978 to 1.000. Time-of-day sweeps that cross 6600 K show it. The fit is kept as
     * is because it matches the widely used reference implementation; blend across the step if it matters.
     *
     * @param kelvin color temperature in Kelvin; clamped to [1000, 40000]
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
        double temperature = clamp(kelvin, 1000.0, 40000.0) / 100.0;
        double red;
        double green;
        double blue;
        if (temperature <= 66.0) {
            red = 255.0;
            green = 99.4708025861 * org.dynamisengine.vectrix.core.AccurateMath.log(temperature) - 161.1195681661;
            blue = temperature <= 19.0
                    ? 0.0
                    : 138.5177312231 * org.dynamisengine.vectrix.core.AccurateMath.log(temperature - 10.0) - 305.0447927307;
        } else {
            red = 329.698727446 * org.dynamisengine.vectrix.core.AccurateMath.pow(temperature - 60.0, -0.1332047592);
            green = 288.1221695283 * org.dynamisengine.vectrix.core.AccurateMath.pow(temperature - 60.0, -0.0755148492);
            blue = 255.0;
        }
        dest.set(clamp(red / 255.0, 0.0, 1.0), clamp(green / 255.0, 0.0, 1.0), clamp(blue / 255.0, 0.0, 1.0));
        return ColorMathd.srgbToLinear(dest, dest);
    }

    private static double clamp(double v, double lo, double hi) {
        return java.lang.Math.max(lo, java.lang.Math.min(hi, v));
    }
}
