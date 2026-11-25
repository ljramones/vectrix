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
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * sRGB transfer curve, relative luminance, the linear sRGB/XYZ matrices and the simple Reinhard tonemap, in float
 * precision.
 * <p>
 * The sRGB curve is the IEC 61966-2-1 piecewise function, applied per channel without clamping: inputs below 0 or
 * above 1 are extended along the same formula. All matrices use the D65 white point. Every {@code dest} may alias
 * the input vector. The methods are stateless, allocate nothing and are safe to call from any thread.
 *
 * @since 1.0.0
 */
public final class ColorMathf {
    private ColorMathf() {
    }

    /**
     * Decodes one sRGB-encoded channel to linear light (the IEC 61966-2-1 curve: {@code c / 12.92} up to 0.04045,
     * then {@code ((c + 0.055) / 1.055)^2.4}).
     *
     * @param c the encoded channel value, usually in [0, 1]
     * @return the linear value
     */
    public static float srgbToLinear(float c) {
        if (c <= 0.04045f) {
            return c / 12.92f;
        }
        return (float) org.dynamisengine.vectrix.core.AccurateMath.pow((c + 0.055f) / 1.055f, 2.4);
    }

    /**
     * Encodes one linear channel with the sRGB curve (IEC 61966-2-1: {@code 12.92 c} up to 0.0031308, then
     * {@code 1.055 c^(1/2.4) - 0.055}). The inverse of {@link #srgbToLinear(float)}.
     *
     * @param c the linear channel value, usually in [0, 1]
     * @return the encoded value
     */
    public static float linearToSrgb(float c) {
        if (c <= 0.0031308f) {
            return c * 12.92f;
        }
        return 1.055f * (float) org.dynamisengine.vectrix.core.AccurateMath.pow(c, 1.0 / 2.4) - 0.055f;
    }

    /**
     * Decodes each channel of an sRGB-encoded color to linear light with {@link #srgbToLinear(float)}.
     *
     * @param srgb the encoded color
     * @param dest receives the linear color; may be {@code srgb}
     * @return {@code dest}
     */
    public static Vector3f srgbToLinear(Vector3fc srgb, Vector3f dest) {
        return dest.set(srgbToLinear(srgb.x()), srgbToLinear(srgb.y()), srgbToLinear(srgb.z()));
    }

    /**
     * Encodes each channel of a linear color with the sRGB curve, {@link #linearToSrgb(float)}.
     *
     * @param linear the linear color
     * @param dest receives the encoded color; may be {@code linear}
     * @return {@code dest}
     */
    public static Vector3f linearToSrgb(Vector3fc linear, Vector3f dest) {
        return dest.set(linearToSrgb(linear.x()), linearToSrgb(linear.y()), linearToSrgb(linear.z()));
    }

    /**
     * Returns the relative luminance Y of a linear sRGB color. The weights are the Y row of
     * {@link #linearSrgbToXyz(float, float, float, Vector3f)}, so the result equals the Y of that conversion.
     *
     * @param r the linear red channel
     * @param g the linear green channel
     * @param b the linear blue channel
     * @return the relative luminance, 1 for linear white (1, 1, 1)
     */
    public static float luminanceLinear(float r, float g, float b) {
        // the Y row of linearSrgbToXyz, so luminance and XYZ agree (it used the 4-digit Rec. 709 weights)
        return 0.2126729f * r + 0.7151522f * g + 0.0721750f * b;
    }

    /**
     * Returns the relative luminance Y of a linear sRGB color; see {@link #luminanceLinear(float, float, float)}.
     *
     * @param linearRgb the linear sRGB color
     * @return the relative luminance
     */
    public static float luminanceLinear(Vector3fc linearRgb) {
        return luminanceLinear(linearRgb.x(), linearRgb.y(), linearRgb.z());
    }

    /**
     * Converts linear sRGB to CIE XYZ (D65); see {@link #linearSrgbToXyz(float, float, float, Vector3f)}.
     *
     * @param linearRgb the linear sRGB color
     * @param dest receives XYZ; may be {@code linearRgb}
     * @return {@code dest}
     */
    public static Vector3f linearSrgbToXyz(Vector3fc linearRgb, Vector3f dest) {
        return linearSrgbToXyz(linearRgb.x(), linearRgb.y(), linearRgb.z(), dest);
    }

    /**
     * Converts linear sRGB (D65) to CIE XYZ with the 7-digit IEC 61966-2-1 matrix. This method and
     * {@link #xyzToLinearSrgb(float, float, float, Vector3f)} are the library's single source for the sRGB/XYZ
     * matrices; {@code ColorSciencef} delegates here. Linear white (1, 1, 1) maps to Y = 1.
     *
     * @param r the linear red channel
     * @param g the linear green channel
     * @param b the linear blue channel
     * @param dest receives X, Y and Z
     * @return {@code dest}
     */
    public static Vector3f linearSrgbToXyz(float r, float g, float b, Vector3f dest) {
        return dest.set(
                0.4124564f * r + 0.3575761f * g + 0.1804375f * b,
                0.2126729f * r + 0.7151522f * g + 0.0721750f * b,
                0.0193339f * r + 0.1191920f * g + 0.9503041f * b);
    }

    /**
     * Converts CIE XYZ to linear sRGB (D65); see {@link #xyzToLinearSrgb(float, float, float, Vector3f)}.
     *
     * @param xyz the XYZ color
     * @param dest receives the linear sRGB color; may be {@code xyz}
     * @return {@code dest}
     */
    public static Vector3f xyzToLinearSrgb(Vector3fc xyz, Vector3f dest) {
        return xyzToLinearSrgb(xyz.x(), xyz.y(), xyz.z(), dest);
    }

    /**
     * Converts CIE XYZ to linear sRGB (D65); the inverse of {@link #linearSrgbToXyz(float, float, float, Vector3f)}.
     * Colors outside the sRGB gamut give channels below 0 or above 1; nothing is clamped.
     *
     * @param x the X tristimulus value
     * @param y the Y tristimulus value
     * @param z the Z tristimulus value
     * @param dest receives the linear sRGB color
     * @return {@code dest}
     */
    public static Vector3f xyzToLinearSrgb(float x, float y, float z, Vector3f dest) {
        return dest.set(
                3.2404542f * x - 1.5371385f * y - 0.4985314f * z,
                -0.9692660f * x + 1.8760108f * y + 0.0415560f * z,
                0.0556434f * x - 0.2040259f * y + 1.0572252f * z);
    }

    /**
     * Applies the simple Reinhard tonemap {@code hdr / (1 + hdr)} to one channel. It maps [0, infinity) to [0, 1);
     * see {@code Tonemapf.reinhardExtended} for the form with a white point.
     *
     * @param hdr the linear scene value, at least 0
     * @return the tonemapped value
     */
    public static float reinhardToneMap(float hdr) {
        return hdr / (1.0f + hdr);
    }

    /**
     * Applies the simple Reinhard tonemap {@link #reinhardToneMap(float)} to each channel.
     *
     * @param hdr the linear scene color
     * @param dest receives the tonemapped color; may be {@code hdr}
     * @return {@code dest}
     */
    public static Vector3f reinhardToneMap(Vector3fc hdr, Vector3f dest) {
        return dest.set(reinhardToneMap(hdr.x()), reinhardToneMap(hdr.y()), reinhardToneMap(hdr.z()));
    }
}
