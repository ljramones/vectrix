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

import org.dynamisengine.vectrix.core.Vector3f;

/**
 * Minimal color science helpers including CIE 1931 approximations.
  * @since 1.0.0
  */
public final class ColorSciencef {
    public static final float VISIBLE_MIN_NM = 380.0f;
    public static final float VISIBLE_MAX_NM = 780.0f;

    private ColorSciencef() {
    }

    /**
     * CIE 1931 2-degree colour matching functions (xBar, yBar, zBar), using the multi-lobe piecewise-Gaussian
     * fit of Wyman, Sloan and Shirley, "Simple Analytic Approximations to the CIE XYZ Color Matching
     * Functions" (JCGT 2013). Its maximum absolute error against the official CIE 1 nm tables is 0.014 (x̄), 0.008
     * (ȳ) and 0.022 (z̄); {@link CieObserver} has the tabulated values.
     * The wavelength is clamped to [380, 780] nm.
     */
    public static Vector3f cie1931XyzBar(float wavelengthNm, Vector3f dest) {
        float wl = clamp(wavelengthNm, VISIBLE_MIN_NM, VISIBLE_MAX_NM);
        float x = 1.056f * g(wl, 599.8f, 0.0264f, 0.0323f) + 0.362f * g(wl, 442.0f, 0.0624f, 0.0374f)
                - 0.065f * g(wl, 501.1f, 0.0490f, 0.0382f);
        float y = 0.821f * g(wl, 568.8f, 0.0213f, 0.0247f) + 0.286f * g(wl, 530.9f, 0.0613f, 0.0322f);
        float z = 1.217f * g(wl, 437.0f, 0.0845f, 0.0278f) + 0.681f * g(wl, 459.0f, 0.0385f, 0.0725f);
        return dest.set(x, y, z);
    }

    /**
     * Convert XYZ to linear sRGB.
     */
    public static Vector3f xyzToLinearSrgb(float x, float y, float z, Vector3f dest) {
        // Single source with ColorMath (7-digit IEC 61966-2-1 matrix); this used to keep a 4-digit copy.
        return ColorMathf.xyzToLinearSrgb(x, y, z, dest);
    }

    /**
     * Convert linear sRGB to XYZ.
     */
    public static Vector3f linearSrgbToXyz(float r, float g, float b, Vector3f dest) {
        return ColorMathf.linearSrgbToXyz(r, g, b, dest);
    }

    private static float g(float wl, float mu, float sigmaLo, float sigmaHi) {
        float d = (wl - mu) * (wl < mu ? sigmaLo : sigmaHi);
        return (float) org.dynamisengine.vectrix.core.AccurateMath.exp(-0.5f * d * d);
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
    public static Vector3f kelvinToLinearRgb(float kelvin, Vector3f dest) {
        if (dest == null) {
            throw new IllegalArgumentException("dest is required");
        }
        if (!Float.isFinite(kelvin) || kelvin <= 0f) {
            throw new IllegalArgumentException("kelvin must be finite and > 0");
        }
        double t = ColorScienced.clampPlanckianKelvin(kelvin);
        double x = ColorScienced.planckianX(t);
        double y = ColorScienced.planckianY(x, t);
        ColorMathf.xyzToLinearSrgb((float) (x / y), 1.0f, (float) ((1.0 - x - y) / y), dest);
        float r = java.lang.Math.max(dest.x, 0f), g = java.lang.Math.max(dest.y, 0f), bl = java.lang.Math.max(dest.z, 0f);
        float max = java.lang.Math.max(r, java.lang.Math.max(g, bl));
        return dest.set(r / max, g / max, bl / max);
    }

    private static float clamp(float v, float lo, float hi) {
        return java.lang.Math.max(lo, java.lang.Math.min(hi, v));
    }

    /**
     * Planck's law: spectral radiance of a blackbody at {@code kelvin}, in W·sr⁻¹·m⁻²·nm⁻¹, for a wavelength in nm.
     * Uses the exact SI constants h, c and k (2019 redefinition).
     */
    public static float planckRadiance(float wavelengthNm, float kelvin) {
        double h = 6.62607015e-34;
        double c = 299792458.0;
        double k = 1.380649e-23;
        double l = wavelengthNm * 1e-9;
        double l5 = l * l * l * l * l;
        double b = 2.0 * h * c * c / (l5 * (org.dynamisengine.vectrix.core.AccurateMath.exp(h * c / (l * k * kelvin)) - 1.0));
        return (float) (b * 1e-9);
    }
}
