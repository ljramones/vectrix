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
package org.dynamisengine.vectrix.animation;

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * Double-precision quaternion kernels on a scratch array ({@code x, y, z, w} at an offset), shared by the float and
 * double animation classes so both compute identically. Formulas avoid cancellation: the angle between unit
 * quaternions is {@code 2 atan2(|a − b|, |a + b|)}, and {@code sin(kθ) / sin θ} is evaluated directly (exact for
 * small θ) with its limit {@code k} at θ = 0.
 */
final class QuatKernels {
    static final ThreadLocal<double[]> SCRATCH = ThreadLocal.withInitial(() -> new double[64]);

    private QuatKernels() {
    }

    static void mul(double[] s, int a, int b, int o) {
        double ax = s[a], ay = s[a + 1], az = s[a + 2], aw = s[a + 3];
        double bx = s[b], by = s[b + 1], bz = s[b + 2], bw = s[b + 3];
        s[o] = aw * bx + ax * bw + ay * bz - az * by;
        s[o + 1] = aw * by - ax * bz + ay * bw + az * bx;
        s[o + 2] = aw * bz + ax * by - ay * bx + az * bw;
        s[o + 3] = aw * bw - ax * bx - ay * by - az * bz;
    }

    static void conj(double[] s, int a, int o) {
        s[o] = -s[a];
        s[o + 1] = -s[a + 1];
        s[o + 2] = -s[a + 2];
        s[o + 3] = s[a + 3];
    }

    static void normalize(double[] s, int a) {
        double inv = 1.0 / java.lang.Math.sqrt(s[a] * s[a] + s[a + 1] * s[a + 1] + s[a + 2] * s[a + 2]
                + s[a + 3] * s[a + 3]);
        s[a] *= inv;
        s[a + 1] *= inv;
        s[a + 2] *= inv;
        s[a + 3] *= inv;
    }

    static double dot(double[] s, int a, int b) {
        return s[a] * s[b] + s[a + 1] * s[b + 1] + s[a + 2] * s[b + 2] + s[a + 3] * s[b + 3];
    }

    /** log of a unit quaternion into a 3-vector at {@code o}: {@code (θ/2) axis}, θ/2 = atan2(|v|, w). */
    static void log(double[] s, int a, int o) {
        double x = s[a], y = s[a + 1], z = s[a + 2], w = s[a + 3];
        double v = java.lang.Math.sqrt(x * x + y * y + z * z);
        double k = v == 0.0 ? 0.0 : AccurateMath.atan2(v, w) / v;
        s[o] = x * k;
        s[o + 1] = y * k;
        s[o + 2] = z * k;
    }

    /** exp of a 3-vector at {@code a} into a unit quaternion at {@code o}. */
    static void exp(double[] s, int a, int o) {
        double x = s[a], y = s[a + 1], z = s[a + 2];
        double th = java.lang.Math.sqrt(x * x + y * y + z * z);
        double k = th == 0.0 ? 1.0 : AccurateMath.sin(th) / th;
        s[o] = x * k;
        s[o + 1] = y * k;
        s[o + 2] = z * k;
        s[o + 3] = AccurateMath.cos(th);
    }

    /** Angle between unit 4-vectors, accurate for nearly equal and nearly opposite inputs. */
    static double angle(double[] s, int a, int b) {
        double d2 = 0.0, s2 = 0.0;
        for (int i = 0; i < 4; i++) {
            double d = s[a + i] - s[b + i], p = s[a + i] + s[b + i];
            d2 += d * d;
            s2 += p * p;
        }
        return 2.0 * AccurateMath.atan2(java.lang.Math.sqrt(d2), java.lang.Math.sqrt(s2));
    }

    /** {@code sin(kΩ) / sin Ω}, with its limit {@code k} at Ω = 0. */
    static double ratio(double k, double omega) {
        return omega == 0.0 ? k : AccurateMath.sin(k * omega) / AccurateMath.sin(omega);
    }

    /** Slerp along the great arc from a to b as given (no hemisphere flip), into o. */
    static void slerp(double[] s, int a, int b, double t, int o) {
        double om = angle(s, a, b);
        double wa = ratio(1.0 - t, om), wb = ratio(t, om);
        for (int i = 0; i < 4; i++) {
            s[o + i] = wa * s[a + i] + wb * s[b + i];
        }
    }
}
