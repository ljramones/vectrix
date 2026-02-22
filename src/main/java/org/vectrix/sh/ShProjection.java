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
package org.vectrix.sh;

import org.vectrix.core.Vector3dc;
import org.vectrix.core.Vector3fc;

public final class ShProjection {
    private ShProjection() {
    }

    public static void accumulateSampleL2(Vector3fc dir, Vector3fc rgb, float solidAngleWeight, ShCoeffs9f dest) {
        projectSample(dir.x(), dir.y(), dir.z(), rgb.x(), rgb.y(), rgb.z(), solidAngleWeight, new float[9], dest);
    }

    /**
     * Zero-allocation hot path for projecting one RGB sample into L2 SH coefficients.
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

    public static void accumulateSampleL2(Vector3dc dir, Vector3dc rgb, double solidAngleWeight, ShCoeffs9d dest) {
        projectSample(dir.x(), dir.y(), dir.z(), rgb.x(), rgb.y(), rgb.z(), solidAngleWeight, new double[9], dest);
    }

    /**
     * Zero-allocation hot path for projecting one RGB sample into L2 SH coefficients.
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

    public static void accumulateSampleL3(Vector3fc dir, Vector3fc rgb, float solidAngleWeight, ShCoeffs16f dest) {
        projectSampleL3(dir.x(), dir.y(), dir.z(), rgb.x(), rgb.y(), rgb.z(), solidAngleWeight, new float[16], dest);
    }

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

    public static void accumulateSampleL3(Vector3dc dir, Vector3dc rgb, double solidAngleWeight, ShCoeffs16d dest) {
        projectSampleL3(dir.x(), dir.y(), dir.z(), rgb.x(), rgb.y(), rgb.z(), solidAngleWeight, new double[16], dest);
    }

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
}
