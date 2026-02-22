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

import org.vectrix.core.Vector3f;
import org.vectrix.core.Vector3fc;

public final class ColorMathf {
    private ColorMathf() {
    }

    public static float srgbToLinear(float c) {
        if (c <= 0.04045f) {
            return c / 12.92f;
        }
        return (float) java.lang.Math.pow((c + 0.055f) / 1.055f, 2.4);
    }

    public static float linearToSrgb(float c) {
        if (c <= 0.0031308f) {
            return c * 12.92f;
        }
        return 1.055f * (float) java.lang.Math.pow(c, 1.0 / 2.4) - 0.055f;
    }

    public static Vector3f srgbToLinear(Vector3fc srgb, Vector3f dest) {
        return dest.set(srgbToLinear(srgb.x()), srgbToLinear(srgb.y()), srgbToLinear(srgb.z()));
    }

    public static Vector3f linearToSrgb(Vector3fc linear, Vector3f dest) {
        return dest.set(linearToSrgb(linear.x()), linearToSrgb(linear.y()), linearToSrgb(linear.z()));
    }

    public static float luminanceLinear(float r, float g, float b) {
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    public static float luminanceLinear(Vector3fc linearRgb) {
        return luminanceLinear(linearRgb.x(), linearRgb.y(), linearRgb.z());
    }

    public static Vector3f linearSrgbToXyz(Vector3fc linearRgb, Vector3f dest) {
        float r = linearRgb.x(), g = linearRgb.y(), b = linearRgb.z();
        return dest.set(
                0.4124564f * r + 0.3575761f * g + 0.1804375f * b,
                0.2126729f * r + 0.7151522f * g + 0.0721750f * b,
                0.0193339f * r + 0.1191920f * g + 0.9503041f * b);
    }

    public static Vector3f xyzToLinearSrgb(Vector3fc xyz, Vector3f dest) {
        float x = xyz.x(), y = xyz.y(), z = xyz.z();
        return dest.set(
                3.2404542f * x - 1.5371385f * y - 0.4985314f * z,
                -0.9692660f * x + 1.8760108f * y + 0.0415560f * z,
                0.0556434f * x - 0.2040259f * y + 1.0572252f * z);
    }

    public static float reinhardToneMap(float hdr) {
        return hdr / (1.0f + hdr);
    }

    public static Vector3f reinhardToneMap(Vector3fc hdr, Vector3f dest) {
        return dest.set(reinhardToneMap(hdr.x()), reinhardToneMap(hdr.y()), reinhardToneMap(hdr.z()));
    }
}
