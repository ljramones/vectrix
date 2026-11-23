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
package org.dynamisengine.vectrix.color;

/**
 * RGB color spaces with their primaries and white point, for {@link ColorSpacesf} and {@link ColorSpacesd}. All are
 * linear (scene- or display-referred values without a transfer function).
 *
 * @since 2.1.0
 */
public enum ColorGamut {
    /** sRGB / Rec.709 primaries, D65. Its XYZ matrix is {@link ColorMathd#linearSrgbToXyz}'s. */
    SRGB(0.64, 0.33, 0.30, 0.60, 0.15, 0.06, false),
    /** Display P3 (DCI-P3 primaries with D65). */
    DISPLAY_P3(0.680, 0.320, 0.265, 0.690, 0.150, 0.060, false),
    /** Rec.2020 / Rec.2100 primaries, D65. */
    REC2020(0.708, 0.292, 0.170, 0.797, 0.131, 0.046, false),
    /** ACEScg: ACES AP1 primaries with the ACES white point (≈ D60). */
    ACES_CG(0.713, 0.293, 0.165, 0.830, 0.128, 0.044, true),
    /** ACES2065-1: ACES AP0 primaries with the ACES white point. */
    ACES_2065_1(0.7347, 0.2653, 0.0, 1.0, 0.0001, -0.0770, true);

    final double rx;
    final double ry;
    final double gx;
    final double gy;
    final double bx;
    final double by;
    /** Whether the white point is the ACES one (x 0.32168, y 0.33767) rather than D65. */
    final boolean acesWhite;

    ColorGamut(double rx, double ry, double gx, double gy, double bx, double by, boolean acesWhite) {
        this.rx = rx;
        this.ry = ry;
        this.gx = gx;
        this.gy = gy;
        this.bx = bx;
        this.by = by;
        this.acesWhite = acesWhite;
    }
}
