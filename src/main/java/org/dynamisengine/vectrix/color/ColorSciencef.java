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
     * Approximate CIE 1931 2-deg color matching functions (xBar, yBar, zBar).
     */
    public static Vector3f cie1931XyzBar(float wavelengthNm, Vector3f dest) {
        float wl = clamp(wavelengthNm, VISIBLE_MIN_NM, VISIBLE_MAX_NM);
        float x = g(wl, 442.0f, 0.0624f, 0.0374f) + g(wl, 599.8f, 0.0264f, 0.0323f) + g(wl, 501.1f, 0.0490f, 0.0382f);
        float y = g(wl, 568.8f, 0.0213f, 0.0247f) + g(wl, 530.9f, 0.0613f, 0.0322f);
        float z = g(wl, 437.0f, 0.0845f, 0.0278f) + g(wl, 459.0f, 0.0385f, 0.0725f);
        return dest.set(x, y, z);
    }

    /**
     * Convert XYZ to linear sRGB.
     */
    public static Vector3f xyzToLinearSrgb(float x, float y, float z, Vector3f dest) {
        float r = 3.2406f * x - 1.5372f * y - 0.4986f * z;
        float g = -0.9689f * x + 1.8758f * y + 0.0415f * z;
        float b = 0.0557f * x - 0.2040f * y + 1.0570f * z;
        return dest.set(r, g, b);
    }

    /**
     * Convert linear sRGB to XYZ.
     */
    public static Vector3f linearSrgbToXyz(float r, float g, float b, Vector3f dest) {
        float x = 0.4124f * r + 0.3576f * g + 0.1805f * b;
        float y = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        float z = 0.0193f * r + 0.1192f * g + 0.9505f * b;
        return dest.set(x, y, z);
    }

    private static float g(float wl, float mu, float sigmaLo, float sigmaHi) {
        float d = (wl - mu) * (wl < mu ? sigmaLo : sigmaHi);
        return (float) java.lang.Math.exp(-0.5f * d * d);
    }

    /**
     * Approximate blackbody (Planckian locus) color for a given color temperature
     * using the Tanner Helland algorithm, returned in linear sRGB.
     *
     * @param kelvin color temperature in Kelvin; clamped to [1000, 40000]
     * @param dest   destination vector to store the linear RGB result
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code dest} is null, or kelvin is
     *                                  non-finite or &lt;= 0
     */
    public static Vector3f kelvinToLinearRgb(float kelvin, Vector3f dest) {
        if (dest == null) {
            throw new IllegalArgumentException("dest is required");
        }
        if (!Float.isFinite(kelvin) || kelvin <= 0f) {
            throw new IllegalArgumentException("kelvin must be finite and > 0");
        }

        float temperature = clamp(kelvin, 1000f, 40000f) / 100f;

        float red;
        float green;
        float blue;

        if (temperature <= 66f) {
            red = 255f;
            green = 99.4708f * (float) java.lang.Math.log(temperature) - 161.11957f;
            blue = temperature <= 19f
                    ? 0f
                    : 138.51773f * (float) java.lang.Math.log(temperature - 10f) - 305.0448f;
        } else {
            red = 329.69873f * (float) java.lang.Math.pow(temperature - 60f, -0.13320476f);
            green = 288.12216f * (float) java.lang.Math.pow(temperature - 60f, -0.075514846f);
            blue = 255f;
        }

        float sr = clamp(red / 255f, 0f, 1f);
        float sg = clamp(green / 255f, 0f, 1f);
        float sb = clamp(blue / 255f, 0f, 1f);

        dest.set(sr, sg, sb);
        return ColorMathf.srgbToLinear(dest, dest);
    }

    private static float clamp(float v, float lo, float hi) {
        return java.lang.Math.max(lo, java.lang.Math.min(hi, v));
    }
}
