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
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * sRGB transfer curve, relative luminance, the linear sRGB/XYZ matrices and the simple Reinhard tonemap, in double
 * precision.
 * <p>
 * The sRGB curve is the IEC 61966-2-1 piecewise function, applied per channel without clamping: inputs below 0 or
 * above 1 are extended along the same formula. All matrices use the D65 white point. Every {@code dest} may alias
 * the input vector. The methods are stateless, allocate nothing and are safe to call from any thread.
 *
 * @since 1.0.0
 */
public final class ColorMathd {
    private ColorMathd() {
    }

    /**
     * Decodes one sRGB-encoded channel to linear light (the IEC 61966-2-1 curve: {@code c / 12.92} up to 0.04045,
     * then {@code ((c + 0.055) / 1.055)^2.4}).
     *
     * @param c the encoded channel value, usually in [0, 1]
     * @return the linear value
     */
    public static double srgbToLinear(double c) {
        if (c <= 0.04045) {
            return c / 12.92;
        }
        return org.dynamisengine.vectrix.core.AccurateMath.pow((c + 0.055) / 1.055, 2.4);
    }

    /**
     * Encodes one linear channel with the sRGB curve (IEC 61966-2-1: {@code 12.92 c} up to 0.0031308, then
     * {@code 1.055 c^(1/2.4) - 0.055}). The inverse of {@link #srgbToLinear(double)}.
     *
     * @param c the linear channel value, usually in [0, 1]
     * @return the encoded value
     */
    public static double linearToSrgb(double c) {
        if (c <= 0.0031308) {
            return c * 12.92;
        }
        return 1.055 * org.dynamisengine.vectrix.core.AccurateMath.pow(c, 1.0 / 2.4) - 0.055;
    }

    /**
     * Decodes each channel of an sRGB-encoded color to linear light with {@link #srgbToLinear(double)}.
     *
     * @param srgb the encoded color
     * @param dest receives the linear color; may be {@code srgb}
     * @return {@code dest}
     */
    public static Vector3d srgbToLinear(Vector3dc srgb, Vector3d dest) {
        return dest.set(srgbToLinear(srgb.x()), srgbToLinear(srgb.y()), srgbToLinear(srgb.z()));
    }

    /**
     * Encodes each channel of a linear color with the sRGB curve, {@link #linearToSrgb(double)}.
     *
     * @param linear the linear color
     * @param dest receives the encoded color; may be {@code linear}
     * @return {@code dest}
     */
    public static Vector3d linearToSrgb(Vector3dc linear, Vector3d dest) {
        return dest.set(linearToSrgb(linear.x()), linearToSrgb(linear.y()), linearToSrgb(linear.z()));
    }

    /**
     * Returns the relative luminance Y of a linear sRGB color. The weights are the Y row of
     * {@link #linearSrgbToXyz(double, double, double, Vector3d)}, so the result equals the Y of that conversion.
     *
     * @param r the linear red channel
     * @param g the linear green channel
     * @param b the linear blue channel
     * @return the relative luminance, 1 for linear white (1, 1, 1)
     */
    public static double luminanceLinear(double r, double g, double b) {
        // the Y row of linearSrgbToXyz, so luminance and XYZ agree (it used the 4-digit Rec. 709 weights)
        return 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
    }

    /**
     * Returns the relative luminance Y of a linear sRGB color; see {@link #luminanceLinear(double, double, double)}.
     *
     * @param linearRgb the linear sRGB color
     * @return the relative luminance
     */
    public static double luminanceLinear(Vector3dc linearRgb) {
        return luminanceLinear(linearRgb.x(), linearRgb.y(), linearRgb.z());
    }

    /**
     * Converts linear sRGB to CIE XYZ (D65); see {@link #linearSrgbToXyz(double, double, double, Vector3d)}.
     *
     * @param linearRgb the linear sRGB color
     * @param dest receives XYZ; may be {@code linearRgb}
     * @return {@code dest}
     */
    public static Vector3d linearSrgbToXyz(Vector3dc linearRgb, Vector3d dest) {
        return linearSrgbToXyz(linearRgb.x(), linearRgb.y(), linearRgb.z(), dest);
    }

    /**
     * Converts linear sRGB (D65) to CIE XYZ with the 7-digit IEC 61966-2-1 matrix. This method and
     * {@link #xyzToLinearSrgb(double, double, double, Vector3d)} are the library's single source for the sRGB/XYZ
     * matrices; {@code ColorScienced} delegates here. Linear white (1, 1, 1) maps to Y = 1.
     *
     * @param r the linear red channel
     * @param g the linear green channel
     * @param b the linear blue channel
     * @param dest receives X, Y and Z
     * @return {@code dest}
     */
    public static Vector3d linearSrgbToXyz(double r, double g, double b, Vector3d dest) {
        return dest.set(
                0.4124564 * r + 0.3575761 * g + 0.1804375 * b,
                0.2126729 * r + 0.7151522 * g + 0.0721750 * b,
                0.0193339 * r + 0.1191920 * g + 0.9503041 * b);
    }

    /**
     * Converts CIE XYZ to linear sRGB (D65); see {@link #xyzToLinearSrgb(double, double, double, Vector3d)}.
     *
     * @param xyz the XYZ color
     * @param dest receives the linear sRGB color; may be {@code xyz}
     * @return {@code dest}
     */
    public static Vector3d xyzToLinearSrgb(Vector3dc xyz, Vector3d dest) {
        return xyzToLinearSrgb(xyz.x(), xyz.y(), xyz.z(), dest);
    }

    /**
     * Converts CIE XYZ to linear sRGB (D65); the inverse of {@link #linearSrgbToXyz(double, double, double, Vector3d)}.
     * Colors outside the sRGB gamut give channels below 0 or above 1; nothing is clamped.
     *
     * @param x the X tristimulus value
     * @param y the Y tristimulus value
     * @param z the Z tristimulus value
     * @param dest receives the linear sRGB color
     * @return {@code dest}
     */
    public static Vector3d xyzToLinearSrgb(double x, double y, double z, Vector3d dest) {
        return dest.set(
                3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
                -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
                0.0556434 * x - 0.2040259 * y + 1.0572252 * z);
    }

    /**
     * Applies the simple Reinhard tonemap {@code hdr / (1 + hdr)} to one channel. It maps [0, infinity) to [0, 1);
     * see {@code Tonemapd.reinhardExtended} for the form with a white point.
     *
     * @param hdr the linear scene value, at least 0
     * @return the tonemapped value
     */
    public static double reinhardToneMap(double hdr) {
        return hdr / (1.0 + hdr);
    }

    /**
     * Applies the simple Reinhard tonemap {@link #reinhardToneMap(double)} to each channel.
     *
     * @param hdr the linear scene color
     * @param dest receives the tonemapped color; may be {@code hdr}
     * @return {@code dest}
     */
    public static Vector3d reinhardToneMap(Vector3dc hdr, Vector3d dest) {
        return dest.set(reinhardToneMap(hdr.x()), reinhardToneMap(hdr.y()), reinhardToneMap(hdr.z()));
    }
}
