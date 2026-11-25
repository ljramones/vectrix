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
package org.dynamisengine.vectrix.optics;

/**
 * Spectral sampling utilities (single-precision).
  * @since 1.0.0
  */
public final class SpectralSamplingf {
    /** Lower end of the sampled visible range, in nanometers. */
    public static final float MIN_NM = 380.0f;
    /** Upper end of the sampled visible range, in nanometers. */
    public static final float MAX_NM = 780.0f;
    /** Width of the sampled range, {@code MAX_NM − MIN_NM}, in nanometers. */
    public static final float RANGE_NM = MAX_NM - MIN_NM;

    private SpectralSamplingf() {
    }

    /**
     * Maps a uniform random number to a wavelength uniformly distributed over {@code [MIN_NM, MAX_NM]}.
     *
     * @param u01 the random number, clamped to {@code [0, 1]}
     * @return the wavelength in nanometers
     */
    public static float sampleUniform(float u01) {
        float u = clamp01(u01);
        return MIN_NM + u * RANGE_NM;
    }

    /**
     * Returns a wavelength in stratum {@code stratum} of {@code strataCount} equal-width strata of
     * {@code [MIN_NM, MAX_NM]}, placed at offset {@code jitter01} within the stratum. Over all strata the samples
     * have the uniform density {@link #pdfUniform()}.
     *
     * @param stratum the stratum index, in {@code [0, strataCount)}
     * @param strataCount the number of strata, at least 1
     * @param jitter01 the position within the stratum, clamped to {@code [0, 1]}
     * @return the wavelength in nanometers
     * @throws IllegalArgumentException if {@code strataCount} is less than 1 or {@code stratum} is out of range
     */
    public static float sampleStratified(int stratum, int strataCount, float jitter01) {
        if (strataCount <= 0 || stratum < 0 || stratum >= strataCount) {
            throw new IllegalArgumentException("strata");
        }
        float u = (stratum + clamp01(jitter01)) / (float) strataCount;
        return sampleUniform(u);
    }

    /**
     * Returns the probability density of {@link #sampleUniform}, {@code 1 / RANGE_NM}.
     *
     * @return the density per nanometer
     */
    public static float pdfUniform() {
        return 1.0f / RANGE_NM;
    }

    private static float clamp01(float v) {
        return java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, v));
    }
}
