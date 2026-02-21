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
package org.vectrix.experimental;

import org.vectrix.core.Internal;

/**
 * Deterministic-aware reduction helpers used by batch kernels.
 */
@Internal("Internal reduction utilities for kernel backends.")
public final class Reduction {
    private Reduction() {
    }

    public static float sum(float[] values) {
        if (KernelConfig.mathMode() == MathMode.STRICT) {
            return sumStrict(values);
        }
        return sumFast(values);
    }

    public static float dot(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Mismatched vector lengths");
        }
        if (KernelConfig.mathMode() == MathMode.STRICT) {
            return dotStrict(a, b);
        }
        return dotFast(a, b);
    }

    public static float sumFast(float[] values) {
        float s0 = 0.0f, s1 = 0.0f, s2 = 0.0f, s3 = 0.0f;
        int i = 0;
        int upper = values.length & ~3;
        for (; i < upper; i += 4) {
            s0 += values[i];
            s1 += values[i + 1];
            s2 += values[i + 2];
            s3 += values[i + 3];
        }
        float sum = (s0 + s1) + (s2 + s3);
        for (; i < values.length; i++) {
            sum += values[i];
        }
        return sum;
    }

    public static float sumStrict(float[] values) {
        return sumKahan(values);
    }

    public static float dotFast(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Mismatched vector lengths");
        }
        float s0 = 0.0f, s1 = 0.0f, s2 = 0.0f, s3 = 0.0f;
        int i = 0;
        int upper = a.length & ~3;
        for (; i < upper; i += 4) {
            s0 += a[i] * b[i];
            s1 += a[i + 1] * b[i + 1];
            s2 += a[i + 2] * b[i + 2];
            s3 += a[i + 3] * b[i + 3];
        }
        float sum = (s0 + s1) + (s2 + s3);
        for (; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    public static float dotStrict(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Mismatched vector lengths");
        }
        float s0 = 0.0f, s1 = 0.0f, s2 = 0.0f, s3 = 0.0f;
        float c0 = 0.0f, c1 = 0.0f, c2 = 0.0f, c3 = 0.0f;
        int i = 0;
        int upper = a.length & ~3;
        for (; i < upper; i += 4) {
            float y0 = a[i] * b[i] - c0;
            float t0 = s0 + y0;
            c0 = (t0 - s0) - y0;
            s0 = t0;

            float y1 = a[i + 1] * b[i + 1] - c1;
            float t1 = s1 + y1;
            c1 = (t1 - s1) - y1;
            s1 = t1;

            float y2 = a[i + 2] * b[i + 2] - c2;
            float t2 = s2 + y2;
            c2 = (t2 - s2) - y2;
            s2 = t2;

            float y3 = a[i + 3] * b[i + 3] - c3;
            float t3 = s3 + y3;
            c3 = (t3 - s3) - y3;
            s3 = t3;
        }
        float sum = 0.0f;
        float compensation = 0.0f;
        float y = s0 - compensation;
        float t = sum + y;
        compensation = (t - sum) - y;
        sum = t;
        y = s1 - compensation;
        t = sum + y;
        compensation = (t - sum) - y;
        sum = t;
        y = s2 - compensation;
        t = sum + y;
        compensation = (t - sum) - y;
        sum = t;
        y = s3 - compensation;
        t = sum + y;
        compensation = (t - sum) - y;
        sum = t;
        for (; i < a.length; i++) {
            y = a[i] * b[i] - compensation;
            t = sum + y;
            compensation = (t - sum) - y;
            sum = t;
        }
        return sum;
    }

    static float sumKahan(float[] values) {
        float s0 = 0.0f, s1 = 0.0f, s2 = 0.0f, s3 = 0.0f;
        float c0 = 0.0f, c1 = 0.0f, c2 = 0.0f, c3 = 0.0f;
        int i = 0;
        int upper = values.length & ~3;
        for (; i < upper; i += 4) {
            float y0 = values[i] - c0;
            float t0 = s0 + y0;
            c0 = (t0 - s0) - y0;
            s0 = t0;

            float y1 = values[i + 1] - c1;
            float t1 = s1 + y1;
            c1 = (t1 - s1) - y1;
            s1 = t1;

            float y2 = values[i + 2] - c2;
            float t2 = s2 + y2;
            c2 = (t2 - s2) - y2;
            s2 = t2;

            float y3 = values[i + 3] - c3;
            float t3 = s3 + y3;
            c3 = (t3 - s3) - y3;
            s3 = t3;
        }
        float sum = 0.0f;
        float compensation = 0.0f;
        float y = s0 - compensation;
        float t = sum + y;
        compensation = (t - sum) - y;
        sum = t;
        y = s1 - compensation;
        t = sum + y;
        compensation = (t - sum) - y;
        sum = t;
        y = s2 - compensation;
        t = sum + y;
        compensation = (t - sum) - y;
        sum = t;
        y = s3 - compensation;
        t = sum + y;
        compensation = (t - sum) - y;
        sum = t;
        for (; i < values.length; i++) {
            y = values[i] - compensation;
            t = sum + y;
            compensation = (t - sum) - y;
            sum = t;
        }
        return sum;
    }
}
