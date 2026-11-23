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
package org.dynamisengine.vectrix.renderingmath;

import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Bilinear and bicubic interpolation helpers (double-precision).
  * @since 1.0.0
  */
public final class Interpolationd {
    private Interpolationd() {
    }

    /**
     * Interpolates bilinearly between four corner values: first along x, then along y. {@code tx} and {@code ty}
     * are not clamped, so values outside {@code [0, 1]} extrapolate.
     *
     * @param q00 the value at {@code (0, 0)}
     * @param q10 the value at {@code (1, 0)}
     * @param q01 the value at {@code (0, 1)}
     * @param q11 the value at {@code (1, 1)}
     * @param tx the position along x
     * @param ty the position along y
     * @return the interpolated value
     */
    public static double bilinear(double q00, double q10, double q01, double q11, double tx, double ty) {
        double x0 = lerp(q00, q10, tx);
        double x1 = lerp(q01, q11, tx);
        return lerp(x0, x1, ty);
    }

    /**
     * Interpolates four corner vectors bilinearly, component by component, as {@link #bilinear}. {@code dest} may
     * be any of the inputs.
     *
     * @param q00 the vector at {@code (0, 0)}
     * @param q10 the vector at {@code (1, 0)}
     * @param q01 the vector at {@code (0, 1)}
     * @param q11 the vector at {@code (1, 1)}
     * @param tx the position along x
     * @param ty the position along y
     * @param dest receives the result
     * @return {@code dest}
     * @since 3.0.0
     */
    public static Vector3d bilinearVec3(Vector3dc q00, Vector3dc q10, Vector3dc q01, Vector3dc q11, double tx, double ty,
            Vector3d dest) {
        double x = bilinear(q00.x(), q10.x(), q01.x(), q11.x(), tx, ty);
        double y = bilinear(q00.y(), q10.y(), q01.y(), q11.y(), tx, ty);
        double z = bilinear(q00.z(), q10.z(), q01.z(), q11.z(), tx, ty);
        return dest.set(x, y, z);
    }

    /**
     * Interpolates four evenly spaced samples with the Catmull-Rom spline, which passes through {@code p1} at
     * {@code t = 0} and {@code p2} at {@code t = 1}.
     *
     * @param p0 the sample before {@code p1}
     * @param p1 the sample at {@code t = 0}
     * @param p2 the sample at {@code t = 1}
     * @param p3 the sample after {@code p2}
     * @param t the position between {@code p1} and {@code p2}, normally in {@code [0, 1]} (not clamped)
     * @return the interpolated value
     */
    public static double cubicHermite(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2.0 * p1) + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
            + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    /**
     * Evaluates the uniform cubic B-spline of four evenly spaced samples. It is smoother than
     * {@link #cubicHermite} but approximates: at {@code t = 0} it gives {@code (p0 + 4 p1 + p2) / 6}, not
     * {@code p1}.
     *
     * @param p0 the sample before {@code p1}
     * @param p1 the sample at {@code t = 0}
     * @param p2 the sample at {@code t = 1}
     * @param p3 the sample after {@code p2}
     * @param t the position between {@code p1} and {@code p2}, normally in {@code [0, 1]} (not clamped)
     * @return the spline value
     */
    public static double cubicBSpline(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return ((-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3
            + (3.0 * p0 - 6.0 * p1 + 3.0 * p2) * t2
            + (-3.0 * p0 + 3.0 * p2) * t
            + (p0 + 4.0 * p1 + p2)) / 6.0;
    }

    /**
     * Filters four evenly spaced samples with the Mitchell-Netravali cubic of parameters {@code (b, c)}. Common
     * choices are {@code b = c = 1/3} (Mitchell-Netravali's recommendation), {@code (0, 0.5)} (Catmull-Rom, same
     * as {@link #cubicHermite}) and {@code (1, 0)} (B-spline, same as {@link #cubicBSpline}).
     *
     * @param p0 the sample before {@code p1}
     * @param p1 the sample at {@code t = 0}
     * @param p2 the sample at {@code t = 1}
     * @param p3 the sample after {@code p2}
     * @param t the position between {@code p1} and {@code p2}, normally in {@code [0, 1]} (not clamped)
     * @param b the B parameter
     * @param c the C parameter
     * @return the filtered value
     */
    public static double cubicMitchellNetravali(double p0, double p1, double p2, double p3, double t, double b, double c) {
        double w0 = mnWeight(1.0 + t, b, c);
        double w1 = mnWeight(t, b, c);
        double w2 = mnWeight(1.0 - t, b, c);
        double w3 = mnWeight(2.0 - t, b, c);
        return p0 * w0 + p1 * w1 + p2 * w2 + p3 * w3;
    }

    /**
     * Interpolates a 4×4 neighbourhood with {@link #cubicHermite}: each row along x, then the four row results
     * along y.
     *
     * @param samples4x4 the 4×4 neighbourhood, row-major: sample {@code (column i, row j)} at index {@code 4 j + i},
     *                   rows along y; at least 16 elements, starting at index 0
     * @param tx the position along x between columns 1 and 2, normally in {@code [0, 1]} (not clamped)
     * @param ty the position along y between rows 1 and 2, normally in {@code [0, 1]} (not clamped)
     * @return the interpolated value
     * @throws IllegalArgumentException if {@code samples4x4} is null or shorter than 16
     */
    public static double bicubicHermite(double[] samples4x4, double tx, double ty) {
        check4x4(samples4x4);
        double r0 = cubicHermite(samples4x4[0], samples4x4[1], samples4x4[2], samples4x4[3], tx);
        double r1 = cubicHermite(samples4x4[4], samples4x4[5], samples4x4[6], samples4x4[7], tx);
        double r2 = cubicHermite(samples4x4[8], samples4x4[9], samples4x4[10], samples4x4[11], tx);
        double r3 = cubicHermite(samples4x4[12], samples4x4[13], samples4x4[14], samples4x4[15], tx);
        return cubicHermite(r0, r1, r2, r3, ty);
    }

    /**
     * Evaluates the bicubic B-spline of a 4×4 neighbourhood with {@link #cubicBSpline}: each row along x, then the
     * four row results along y.
     *
     * @param samples4x4 the 4×4 neighbourhood, row-major: sample {@code (column i, row j)} at index {@code 4 j + i},
     *                   rows along y; at least 16 elements, starting at index 0
     * @param tx the position along x between columns 1 and 2, normally in {@code [0, 1]} (not clamped)
     * @param ty the position along y between rows 1 and 2, normally in {@code [0, 1]} (not clamped)
     * @return the spline value
     * @throws IllegalArgumentException if {@code samples4x4} is null or shorter than 16
     */
    public static double bicubicBSpline(double[] samples4x4, double tx, double ty) {
        check4x4(samples4x4);
        double r0 = cubicBSpline(samples4x4[0], samples4x4[1], samples4x4[2], samples4x4[3], tx);
        double r1 = cubicBSpline(samples4x4[4], samples4x4[5], samples4x4[6], samples4x4[7], tx);
        double r2 = cubicBSpline(samples4x4[8], samples4x4[9], samples4x4[10], samples4x4[11], tx);
        double r3 = cubicBSpline(samples4x4[12], samples4x4[13], samples4x4[14], samples4x4[15], tx);
        return cubicBSpline(r0, r1, r2, r3, ty);
    }

    /**
     * Filters a 4×4 neighbourhood with {@link #cubicMitchellNetravali}: each row along x, then the four row results
     * along y.
     *
     * @param samples4x4 the 4×4 neighbourhood, row-major: sample {@code (column i, row j)} at index {@code 4 j + i},
     *                   rows along y; at least 16 elements, starting at index 0
     * @param tx the position along x between columns 1 and 2, normally in {@code [0, 1]} (not clamped)
     * @param ty the position along y between rows 1 and 2, normally in {@code [0, 1]} (not clamped)
     * @param b the B parameter
     * @param c the C parameter
     * @return the filtered value
     * @throws IllegalArgumentException if {@code samples4x4} is null or shorter than 16
     */
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
