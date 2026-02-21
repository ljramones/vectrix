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
            return sumKahan(values);
        }
        return sumFast(values);
    }

    public static float dot(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Mismatched vector lengths");
        }
        if (KernelConfig.mathMode() == MathMode.STRICT) {
            float sum = 0.0f;
            float compensation = 0.0f;
            for (int i = 0; i < a.length; i++) {
                float y = a[i] * b[i] - compensation;
                float t = sum + y;
                compensation = (t - sum) - y;
                sum = t;
            }
            return sum;
        }
        float sum = 0.0f;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    static float sumFast(float[] values) {
        float sum = 0.0f;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
        }
        return sum;
    }

    static float sumKahan(float[] values) {
        float sum = 0.0f;
        float compensation = 0.0f;
        for (int i = 0; i < values.length; i++) {
            float y = values[i] - compensation;
            float t = sum + y;
            compensation = (t - sum) - y;
            sum = t;
        }
        return sum;
    }
}
