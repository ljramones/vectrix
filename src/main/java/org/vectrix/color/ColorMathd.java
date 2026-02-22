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
package org.vectrix.color;

import org.vectrix.core.Vector3d;
import org.vectrix.core.Vector3dc;

public final class ColorMathd {
    private ColorMathd() {
    }

    public static double srgbToLinear(double c) {
        if (c <= 0.04045) {
            return c / 12.92;
        }
        return java.lang.Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static double linearToSrgb(double c) {
        if (c <= 0.0031308) {
            return c * 12.92;
        }
        return 1.055 * java.lang.Math.pow(c, 1.0 / 2.4) - 0.055;
    }

    public static Vector3d srgbToLinear(Vector3dc srgb, Vector3d dest) {
        return dest.set(srgbToLinear(srgb.x()), srgbToLinear(srgb.y()), srgbToLinear(srgb.z()));
    }

    public static Vector3d linearToSrgb(Vector3dc linear, Vector3d dest) {
        return dest.set(linearToSrgb(linear.x()), linearToSrgb(linear.y()), linearToSrgb(linear.z()));
    }

    public static double luminanceLinear(double r, double g, double b) {
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    public static double luminanceLinear(Vector3dc linearRgb) {
        return luminanceLinear(linearRgb.x(), linearRgb.y(), linearRgb.z());
    }

    public static Vector3d linearSrgbToXyz(Vector3dc linearRgb, Vector3d dest) {
        double r = linearRgb.x(), g = linearRgb.y(), b = linearRgb.z();
        return dest.set(
                0.4124564 * r + 0.3575761 * g + 0.1804375 * b,
                0.2126729 * r + 0.7151522 * g + 0.0721750 * b,
                0.0193339 * r + 0.1191920 * g + 0.9503041 * b);
    }

    public static Vector3d xyzToLinearSrgb(Vector3dc xyz, Vector3d dest) {
        double x = xyz.x(), y = xyz.y(), z = xyz.z();
        return dest.set(
                3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
                -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
                0.0556434 * x - 0.2040259 * y + 1.0572252 * z);
    }

    public static double reinhardToneMap(double hdr) {
        return hdr / (1.0 + hdr);
    }

    public static Vector3d reinhardToneMap(Vector3dc hdr, Vector3d dest) {
        return dest.set(reinhardToneMap(hdr.x()), reinhardToneMap(hdr.y()), reinhardToneMap(hdr.z()));
    }
}
