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
package org.vectrix.optics;

/**
 * Spectral sampling utilities (single-precision).
 */
public final class SpectralSamplingf {
    public static final float MIN_NM = 380.0f;
    public static final float MAX_NM = 780.0f;
    public static final float RANGE_NM = MAX_NM - MIN_NM;

    private SpectralSamplingf() {
    }

    /**
     * Uniform wavelength sample in the visible range.
     */
    public static float sampleUniform(float u01) {
        float u = clamp01(u01);
        return MIN_NM + u * RANGE_NM;
    }

    /**
     * Stratified sample in [stratum, stratum+1) out of {@code strataCount}.
     */
    public static float sampleStratified(int stratum, int strataCount, float jitter01) {
        if (strataCount <= 0 || stratum < 0 || stratum >= strataCount) {
            throw new IllegalArgumentException("strata");
        }
        float u = (stratum + clamp01(jitter01)) / (float) strataCount;
        return sampleUniform(u);
    }

    public static float pdfUniform() {
        return 1.0f / RANGE_NM;
    }

    private static float clamp01(float v) {
        return java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, v));
    }
}
