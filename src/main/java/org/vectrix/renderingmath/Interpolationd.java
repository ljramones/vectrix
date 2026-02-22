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
package org.vectrix.renderingmath;

/**
 * Bilinear and bicubic interpolation helpers (double-precision).
 */
public final class Interpolationd {
    private Interpolationd() {
    }

    public static double bilinear(double q00, double q10, double q01, double q11, double tx, double ty) {
        double x0 = lerp(q00, q10, tx);
        double x1 = lerp(q01, q11, tx);
        return lerp(x0, x1, ty);
    }

    public static double cubicHermite(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2.0 * p1) + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
            + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    public static double cubicBSpline(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return ((-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
            + (3.0 * p0 - 6.0 * p1 + 3.0 * p2) * t2
            + (-3.0 * p0 + 3.0 * p2) * t
            + (p0 + 4.0 * p1 + p2)) / 6.0;
    }

    public static double cubicMitchellNetravali(double p0, double p1, double p2, double p3, double t, double b, double c) {
        double w0 = mnWeight(1.0 + t, b, c);
        double w1 = mnWeight(t, b, c);
        double w2 = mnWeight(1.0 - t, b, c);
        double w3 = mnWeight(2.0 - t, b, c);
        return p0 * w0 + p1 * w1 + p2 * w2 + p3 * w3;
    }

    public static double bicubicHermite(double[] samples4x4, double tx, double ty) {
        check4x4(samples4x4);
        double r0 = cubicHermite(samples4x4[0], samples4x4[1], samples4x4[2], samples4x4[3], tx);
        double r1 = cubicHermite(samples4x4[4], samples4x4[5], samples4x4[6], samples4x4[7], tx);
        double r2 = cubicHermite(samples4x4[8], samples4x4[9], samples4x4[10], samples4x4[11], tx);
        double r3 = cubicHermite(samples4x4[12], samples4x4[13], samples4x4[14], samples4x4[15], tx);
        return cubicHermite(r0, r1, r2, r3, ty);
    }

    public static double bicubicBSpline(double[] samples4x4, double tx, double ty) {
        check4x4(samples4x4);
        double r0 = cubicBSpline(samples4x4[0], samples4x4[1], samples4x4[2], samples4x4[3], tx);
        double r1 = cubicBSpline(samples4x4[4], samples4x4[5], samples4x4[6], samples4x4[7], tx);
        double r2 = cubicBSpline(samples4x4[8], samples4x4[9], samples4x4[10], samples4x4[11], tx);
        double r3 = cubicBSpline(samples4x4[12], samples4x4[13], samples4x4[14], samples4x4[15], tx);
        return cubicBSpline(r0, r1, r2, r3, ty);
    }

    public static double bicubicMitchellNetravali(double[] samples4x4, double tx, double ty, double b, double c) {
        check4x4(samples4x4);
        double r0 = cubicMitchellNetravali(samples4x4[0], samples4x4[1], samples4x4[2], samples4x4[3], tx, b, c);
        double r1 = cubicMitchellNetravali(samples4x4[4], samples4x4[5], samples4x4[6], samples4x4[7], tx, b, c);
        double r2 = cubicMitchellNetravali(samples4x4[8], samples4x4[9], samples4x4[10], samples4x4[11], tx, b, c);
        double r3 = cubicMitchellNetravali(samples4x4[12], samples4x4[13], samples4x4[14], samples4x4[15], tx, b, c);
        return cubicMitchellNetravali(r0, r1, r2, r3, ty, b, c);
    }

    private static double mnWeight(double x, double b, double c) {
        double ax = java.lang.Math.abs(x);
        if (ax < 1.0) {
            return ((12.0 - 9.0 * b - 6.0 * c) * ax * ax * ax
                + (-18.0 + 12.0 * b + 6.0 * c) * ax * ax
                + (6.0 - 2.0 * b)) / 6.0;
        }
        if (ax < 2.0) {
            return ((-b - 6.0 * c) * ax * ax * ax
                + (6.0 * b + 30.0 * c) * ax * ax
                + (-12.0 * b - 48.0 * c) * ax
                + (8.0 * b + 24.0 * c)) / 6.0;
        }
        return 0.0;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static void check4x4(double[] v) {
        if (v == null || v.length < 16) {
            throw new IllegalArgumentException("samples4x4");
        }
    }
}
