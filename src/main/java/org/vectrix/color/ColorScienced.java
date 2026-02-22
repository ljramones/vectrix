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

/**
 * Minimal color science helpers including CIE 1931 approximations.
  * @since 1.0.0
  */
public final class ColorScienced {
    public static final double VISIBLE_MIN_NM = 380.0;
    public static final double VISIBLE_MAX_NM = 780.0;

    private ColorScienced() {
    }

    public static Vector3d cie1931XyzBar(double wavelengthNm, Vector3d dest) {
        double wl = clamp(wavelengthNm, VISIBLE_MIN_NM, VISIBLE_MAX_NM);
        double x = g(wl, 442.0, 0.0624, 0.0374) + g(wl, 599.8, 0.0264, 0.0323) + g(wl, 501.1, 0.0490, 0.0382);
        double y = g(wl, 568.8, 0.0213, 0.0247) + g(wl, 530.9, 0.0613, 0.0322);
        double z = g(wl, 437.0, 0.0845, 0.0278) + g(wl, 459.0, 0.0385, 0.0725);
        return dest.set(x, y, z);
    }

    public static Vector3d xyzToLinearSrgb(double x, double y, double z, Vector3d dest) {
        double r = 3.2406 * x - 1.5372 * y - 0.4986 * z;
        double g = -0.9689 * x + 1.8758 * y + 0.0415 * z;
        double b = 0.0557 * x - 0.2040 * y + 1.0570 * z;
        return dest.set(r, g, b);
    }

    public static Vector3d linearSrgbToXyz(double r, double g, double b, Vector3d dest) {
        double x = 0.4124 * r + 0.3576 * g + 0.1805 * b;
        double y = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double z = 0.0193 * r + 0.1192 * g + 0.9505 * b;
        return dest.set(x, y, z);
    }

    private static double g(double wl, double mu, double sigmaLo, double sigmaHi) {
        double d = (wl - mu) * (wl < mu ? sigmaLo : sigmaHi);
        return java.lang.Math.exp(-0.5 * d * d);
    }

    private static double clamp(double v, double lo, double hi) {
        return java.lang.Math.max(lo, java.lang.Math.min(hi, v));
    }
}
