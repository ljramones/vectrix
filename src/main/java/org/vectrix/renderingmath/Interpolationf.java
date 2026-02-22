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

import org.vectrix.core.Vector3f;

/**
 * Bilinear and bicubic interpolation helpers (single-precision).
  * @since 1.0.0
  */
public final class Interpolationf {
    private Interpolationf() {
    }

    public static float bilinear(float q00, float q10, float q01, float q11, float tx, float ty) {
        float x0 = lerp(q00, q10, tx);
        float x1 = lerp(q01, q11, tx);
        return lerp(x0, x1, ty);
    }

    public static Vector3f bilinearVec3(Vector3f q00, Vector3f q10, Vector3f q01, Vector3f q11, float tx, float ty, Vector3f dest) {
        float x = bilinear(q00.x, q10.x, q01.x, q11.x, tx, ty);
        float y = bilinear(q00.y, q10.y, q01.y, q11.y, tx, ty);
        float z = bilinear(q00.z, q10.z, q01.z, q11.z, tx, ty);
        return dest.set(x, y, z);
    }

    /**
     * Catmull-Rom cubic Hermite interpolation of four samples.
     */
    public static float cubicHermite(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * ((2.0f * p1) + (-p0 + p2) * t + (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t2
            + (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t3);
    }

    public static float cubicBSpline(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return ((-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t3
            + (3.0f * p0 - 6.0f * p1 + 3.0f * p2) * t2
            + (-3.0f * p0 + 3.0f * p2) * t
            + (p0 + 4.0f * p1 + p2)) / 6.0f;
    }

    public static float cubicMitchellNetravali(float p0, float p1, float p2, float p3, float t, float b, float c) {
        float w0 = mnWeight(1.0f + t, b, c);
        float w1 = mnWeight(t, b, c);
        float w2 = mnWeight(1.0f - t, b, c);
        float w3 = mnWeight(2.0f - t, b, c);
        return p0 * w0 + p1 * w1 + p2 * w2 + p3 * w3;
    }

    public static float bicubicHermite(float[] samples4x4, float tx, float ty) {
        check4x4(samples4x4);
        float r0 = cubicHermite(samples4x4[0], samples4x4[1], samples4x4[2], samples4x4[3], tx);
        float r1 = cubicHermite(samples4x4[4], samples4x4[5], samples4x4[6], samples4x4[7], tx);
        float r2 = cubicHermite(samples4x4[8], samples4x4[9], samples4x4[10], samples4x4[11], tx);
        float r3 = cubicHermite(samples4x4[12], samples4x4[13], samples4x4[14], samples4x4[15], tx);
        return cubicHermite(r0, r1, r2, r3, ty);
    }

    public static float bicubicBSpline(float[] samples4x4, float tx, float ty) {
        check4x4(samples4x4);
        float r0 = cubicBSpline(samples4x4[0], samples4x4[1], samples4x4[2], samples4x4[3], tx);
        float r1 = cubicBSpline(samples4x4[4], samples4x4[5], samples4x4[6], samples4x4[7], tx);
        float r2 = cubicBSpline(samples4x4[8], samples4x4[9], samples4x4[10], samples4x4[11], tx);
        float r3 = cubicBSpline(samples4x4[12], samples4x4[13], samples4x4[14], samples4x4[15], tx);
        return cubicBSpline(r0, r1, r2, r3, ty);
    }

    public static float bicubicMitchellNetravali(float[] samples4x4, float tx, float ty, float b, float c) {
        check4x4(samples4x4);
        float r0 = cubicMitchellNetravali(samples4x4[0], samples4x4[1], samples4x4[2], samples4x4[3], tx, b, c);
        float r1 = cubicMitchellNetravali(samples4x4[4], samples4x4[5], samples4x4[6], samples4x4[7], tx, b, c);
        float r2 = cubicMitchellNetravali(samples4x4[8], samples4x4[9], samples4x4[10], samples4x4[11], tx, b, c);
        float r3 = cubicMitchellNetravali(samples4x4[12], samples4x4[13], samples4x4[14], samples4x4[15], tx, b, c);
        return cubicMitchellNetravali(r0, r1, r2, r3, ty, b, c);
    }

    private static float mnWeight(float x, float b, float c) {
        float ax = java.lang.Math.abs(x);
        if (ax < 1.0f) {
            return ((12.0f - 9.0f * b - 6.0f * c) * ax * ax * ax
                + (-18.0f + 12.0f * b + 6.0f * c) * ax * ax
                + (6.0f - 2.0f * b)) / 6.0f;
        }
        if (ax < 2.0f) {
            return ((-b - 6.0f * c) * ax * ax * ax
                + (6.0f * b + 30.0f * c) * ax * ax
                + (-12.0f * b - 48.0f * c) * ax
                + (8.0f * b + 24.0f * c)) / 6.0f;
        }
        return 0.0f;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static void check4x4(float[] v) {
        if (v == null || v.length < 16) {
            throw new IllegalArgumentException("samples4x4");
        }
    }
}
