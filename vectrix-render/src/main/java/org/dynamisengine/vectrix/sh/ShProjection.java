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
package org.dynamisengine.vectrix.sh;

import org.dynamisengine.vectrix.core.Vector3dc;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Projection of directional RGB radiance samples into real SH coefficients.
 * <p>
 * Uses the standard real basis ordering {@code l * (l + 1) + m} defined by {@link ShBasis}. Callers should provide
 * unit directions and solid-angle weights (for example from environment sampling).
 * <p>
 * Every method adds one sample's contribution {@code L(dir) · Y_i(dir) · weight} to {@code dest}; it does not clear
 * {@code dest} first, so call {@code zero()} before the first sample. With weights that sum to {@code 4π} over the
 * sphere (or a Monte Carlo weight of {@code 1 / (pdf · N)}), the sums are the radiance coefficients. Methods are
 * stateless, safe to call from any thread on separate {@code dest} objects, and do not allocate.
 */
public final class ShProjection {
    private ShProjection() {
    }

    /**
     * Adds one RGB radiance sample to L2 coefficients (9 per channel).
     *
     * @param dir the sample direction, unit length; it is not normalized
     * @param rgb the radiance of the sample, per channel
     * @param solidAngleWeight the solid angle the sample stands for, in steradians
     * @param dest the coefficients the sample is added to
     */
    public static void accumulateSampleL2(Vector3fc dir, Vector3fc rgb, float solidAngleWeight, ShCoeffs9f dest) {
        accumulate9(dir.x(), dir.y(), dir.z(), rgb.x(), rgb.y(), rgb.z(), solidAngleWeight, dest.c);
    }

    /**
     * Adds one RGB radiance sample to L2 coefficients (9 per channel), using caller scratch for the basis values.
     * Gives the same result as {@code accumulateSampleL2}.
     *
     * @param dirX the x component of the unit sample direction; it is not normalized
     * @param dirY the y component of the direction
     * @param dirZ the z component of the direction
     * @param r the red radiance
     * @param g the green radiance
     * @param b the blue radiance
     * @param solidAngle the solid angle the sample stands for, in steradians
     * @param scratchBasis caller scratch of length at least 9; receives the basis values
     * @param dest the coefficients the sample is added to
     * @throws IllegalArgumentException if {@code scratchBasis.length < 9}
     */
    public static void projectSample(float dirX, float dirY, float dirZ, float r, float g, float b, float solidAngle,
        float[] scratchBasis, ShCoeffs9f dest) {
        if (scratchBasis.length < 9) {
            throw new IllegalArgumentException("scratchBasis");
        }
        ShBasis.evaluateL2(dirX, dirY, dirZ, scratchBasis);
        float rw = r * solidAngle;
        float gw = g * solidAngle;
        float bw = b * solidAngle;
        for (int i = 0; i < 9; i++) {
            float bi = scratchBasis[i];
            int o = i * 3;
            dest.c[o] += rw * bi;
            dest.c[o + 1] += gw * bi;
            dest.c[o + 2] += bw * bi;
        }
    }

    /**
     * Adds one RGB radiance sample to L2 coefficients (9 per channel).
     *
     * @param dir the sample direction, unit length; it is not normalized
     * @param rgb the radiance of the sample, per channel
     * @param solidAngleWeight the solid angle the sample stands for, in steradians
     * @param dest the coefficients the sample is added to
     */
    public static void accumulateSampleL2(Vector3dc dir, Vector3dc rgb, double solidAngleWeight, ShCoeffs9d dest) {
        accumulate9(dir.x(), dir.y(), dir.z(), rgb.x(), rgb.y(), rgb.z(), solidAngleWeight, dest.c);
    }

    /**
     * Adds one RGB radiance sample to L2 coefficients (9 per channel), using caller scratch for the basis values.
     * Gives the same result as {@code accumulateSampleL2}.
     *
     * @param dirX the x component of the unit sample direction; it is not normalized
     * @param dirY the y component of the direction
     * @param dirZ the z component of the direction
     * @param r the red radiance
     * @param g the green radiance
     * @param b the blue radiance
     * @param solidAngle the solid angle the sample stands for, in steradians
     * @param scratchBasis caller scratch of length at least 9; receives the basis values
     * @param dest the coefficients the sample is added to
     * @throws IllegalArgumentException if {@code scratchBasis.length < 9}
     */
    public static void projectSample(double dirX, double dirY, double dirZ, double r, double g, double b, double solidAngle,
        double[] scratchBasis, ShCoeffs9d dest) {
        if (scratchBasis.length < 9) {
            throw new IllegalArgumentException("scratchBasis");
        }
        ShBasis.evaluateL2(dirX, dirY, dirZ, scratchBasis);
        double rw = r * solidAngle;
        double gw = g * solidAngle;
        double bw = b * solidAngle;
        for (int i = 0; i < 9; i++) {
            double bi = scratchBasis[i];
            int o = i * 3;
            dest.c[o] += rw * bi;
            dest.c[o + 1] += gw * bi;
            dest.c[o + 2] += bw * bi;
        }
    }

    /**
     * Adds one RGB radiance sample to L3 coefficients (16 per channel).
     *
     * @param dir the sample direction, unit length; it is not normalized
     * @param rgb the radiance of the sample, per channel
     * @param solidAngleWeight the solid angle the sample stands for, in steradians
     * @param dest the coefficients the sample is added to
     */
    public static void accumulateSampleL3(Vector3fc dir, Vector3fc rgb, float solidAngleWeight, ShCoeffs16f dest) {
        accumulate16(dir.x(), dir.y(), dir.z(), rgb.x(), rgb.y(), rgb.z(), solidAngleWeight, dest.c);
    }

    /**
     * Adds one RGB radiance sample to L3 coefficients (16 per channel), using caller scratch for the basis values.
     * Gives the same result as {@code accumulateSampleL3}.
     *
     * @param dirX the x component of the unit sample direction; it is not normalized
     * @param dirY the y component of the direction
     * @param dirZ the z component of the direction
     * @param r the red radiance
     * @param g the green radiance
     * @param b the blue radiance
     * @param solidAngle the solid angle the sample stands for, in steradians
     * @param scratchBasis caller scratch of length at least 16; receives the basis values
     * @param dest the coefficients the sample is added to
     * @throws IllegalArgumentException if {@code scratchBasis.length < 16}
     * @since 1.0.0
     */
    public static void projectSampleL3(float dirX, float dirY, float dirZ, float r, float g, float b, float solidAngle,
        float[] scratchBasis, ShCoeffs16f dest) {
        if (scratchBasis.length < 16) {
            throw new IllegalArgumentException("scratchBasis");
        }
        ShBasis.evaluateL3(dirX, dirY, dirZ, scratchBasis);
        float rw = r * solidAngle;
        float gw = g * solidAngle;
        float bw = b * solidAngle;
        for (int i = 0; i < 16; i++) {
            float bi = scratchBasis[i];
            int o = i * 3;
            dest.c[o] += rw * bi;
            dest.c[o + 1] += gw * bi;
            dest.c[o + 2] += bw * bi;
        }
    }

    /**
     * Adds one RGB radiance sample to L3 coefficients (16 per channel).
     *
     * @param dir the sample direction, unit length; it is not normalized
     * @param rgb the radiance of the sample, per channel
     * @param solidAngleWeight the solid angle the sample stands for, in steradians
     * @param dest the coefficients the sample is added to
     */
    public static void accumulateSampleL3(Vector3dc dir, Vector3dc rgb, double solidAngleWeight, ShCoeffs16d dest) {
        accumulate16(dir.x(), dir.y(), dir.z(), rgb.x(), rgb.y(), rgb.z(), solidAngleWeight, dest.c);
    }

    /**
     * Adds one RGB radiance sample to L3 coefficients (16 per channel), using caller scratch for the basis values.
     * Gives the same result as {@code accumulateSampleL3}.
     *
     * @param dirX the x component of the unit sample direction; it is not normalized
     * @param dirY the y component of the direction
     * @param dirZ the z component of the direction
     * @param r the red radiance
     * @param g the green radiance
     * @param b the blue radiance
     * @param solidAngle the solid angle the sample stands for, in steradians
     * @param scratchBasis caller scratch of length at least 16; receives the basis values
     * @param dest the coefficients the sample is added to
     * @throws IllegalArgumentException if {@code scratchBasis.length < 16}
     * @since 1.0.0
     */
    public static void projectSampleL3(double dirX, double dirY, double dirZ, double r, double g, double b, double solidAngle,
        double[] scratchBasis, ShCoeffs16d dest) {
        if (scratchBasis.length < 16) {
            throw new IllegalArgumentException("scratchBasis");
        }
        ShBasis.evaluateL3(dirX, dirY, dirZ, scratchBasis);
        double rw = r * solidAngle;
        double gw = g * solidAngle;
        double bw = b * solidAngle;
        for (int i = 0; i < 16; i++) {
            double bi = scratchBasis[i];
            int o = i * 3;
            dest.c[o] += rw * bi;
            dest.c[o + 1] += gw * bi;
            dest.c[o + 2] += bw * bi;
        }
    }

    private static void accumulate9(float x, float y, float z, float r, float g, float b, float solidAngle, float[] c) {
        ShBasis.accumulate9(x, y, z, r * solidAngle, g * solidAngle, b * solidAngle, c);
    }

    private static void accumulate16(float x, float y, float z, float r, float g, float b, float solidAngle, float[] c) {
        ShBasis.accumulate16(x, y, z, r * solidAngle, g * solidAngle, b * solidAngle, c);
    }

    private static void accumulate9(double x, double y, double z, double r, double g, double b, double solidAngle, double[] c) {
        ShBasis.accumulate9(x, y, z, r * solidAngle, g * solidAngle, b * solidAngle, c);
    }

    private static void accumulate16(double x, double y, double z, double r, double g, double b, double solidAngle, double[] c) {
        ShBasis.accumulate16(x, y, z, r * solidAngle, g * solidAngle, b * solidAngle, c);
    }
}
