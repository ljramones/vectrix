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
package org.dynamisengine.vectrix.experimental;

import org.dynamisengine.vectrix.core.Internal;
import org.dynamisengine.vectrix.core.MathMode;

/**
 * Reduction helpers used by batch kernels. {@link #sum} and {@link #dot} dispatch on
 * {@link KernelConfig#mathMode()}: FAST uses four independent partial sums, STRICT uses the accurate fixed-order
 * forms {@link #sumStrict} and {@link #dotStrict}.
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

    /**
     * {@link MathMode#STRICT} sum: sequential, fixed-order accumulation in double-double precision (Knuth
     * TwoSum), so the result is the exact sum rounded to float (within one ulp, through double rounding) and does
     * not depend on lane count, SIMD support or the JVM.
     */
    public static float sumStrict(float[] values) {
        double s = 0.0;
        double c = 0.0;
        for (float v : values) {
            double t = s + v;
            double bp = t - s;
            c += (s - (t - bp)) + (v - bp);
            s = t;
        }
        return (float) (s + c);
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

    /**
     * {@link MathMode#STRICT} dot product. Each float product is exact in double, and the products are summed
     * as in {@link #sumStrict(float[])}.
     */
    public static float dotStrict(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Mismatched vector lengths");
        }
        double s = 0.0;
        double c = 0.0;
        for (int i = 0; i < a.length; i++) {
            double p = (double) a[i] * b[i];
            double t = s + p;
            double bp = t - s;
            c += (s - (t - bp)) + (p - bp);
            s = t;
        }
        return (float) (s + c);
    }
}
