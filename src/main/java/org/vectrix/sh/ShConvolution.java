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

import org.vectrix.core.Vector3d;
import org.vectrix.core.Vector3dc;
import org.vectrix.core.Vector3f;
import org.vectrix.core.Vector3fc;

public final class ShConvolution {
    private static final float A0 = (float) java.lang.Math.PI;
    private static final float A1 = (float) (2.0 * java.lang.Math.PI / 3.0);
    private static final float A2 = (float) (java.lang.Math.PI / 4.0);
    private static final float A3 = 0.0f;

    private static final double D0 = java.lang.Math.PI;
    private static final double D1 = 2.0 * java.lang.Math.PI / 3.0;
    private static final double D2 = java.lang.Math.PI / 4.0;
    private static final double D3 = 0.0;

    private ShConvolution() {
    }

    public static ShCoeffs9f convolveLambertL2(ShCoeffs9f in, ShCoeffs9f dest) {
        for (int i = 0; i < 9; i++) {
            float k = i == 0 ? A0 : (i <= 3 ? A1 : A2);
            int o = i * 3;
            dest.c[o] = in.c[o] * k;
            dest.c[o + 1] = in.c[o + 1] * k;
            dest.c[o + 2] = in.c[o + 2] * k;
        }
        return dest;
    }

    public static ShCoeffs9d convolveLambertL2(ShCoeffs9d in, ShCoeffs9d dest) {
        for (int i = 0; i < 9; i++) {
            double k = i == 0 ? D0 : (i <= 3 ? D1 : D2);
            int o = i * 3;
            dest.c[o] = in.c[o] * k;
            dest.c[o + 1] = in.c[o + 1] * k;
            dest.c[o + 2] = in.c[o + 2] * k;
        }
        return dest;
    }

    public static ShCoeffs16f convolveLambertL3(ShCoeffs16f in, ShCoeffs16f dest) {
        for (int i = 0; i < 16; i++) {
            float k = i == 0 ? A0 : (i <= 3 ? A1 : (i <= 8 ? A2 : A3));
            int o = i * 3;
            dest.c[o] = in.c[o] * k;
            dest.c[o + 1] = in.c[o + 1] * k;
            dest.c[o + 2] = in.c[o + 2] * k;
        }
        return dest;
    }

    public static ShCoeffs16d convolveLambertL3(ShCoeffs16d in, ShCoeffs16d dest) {
        for (int i = 0; i < 16; i++) {
            double k = i == 0 ? D0 : (i <= 3 ? D1 : (i <= 8 ? D2 : D3));
            int o = i * 3;
            dest.c[o] = in.c[o] * k;
            dest.c[o + 1] = in.c[o + 1] * k;
            dest.c[o + 2] = in.c[o + 2] * k;
        }
        return dest;
    }

    public static Vector3f evaluateL2(ShCoeffs9f coeffs, Vector3fc dir, Vector3f dest) {
        float[] b = ShBasis.evaluateL2(dir, new float[9]);
        return evaluateIrradiance(coeffs, b, dest);
    }

    /**
     * Zero-allocation hot path for evaluating irradiance from L2 SH coefficients.
     * The result is written to destRgb as [r,g,b].
     */
    public static void evaluateIrradiance(ShCoeffs9f coeffs, float nx, float ny, float nz, float[] scratchBasis, float[] destRgb) {
        if (scratchBasis.length < 9) {
            throw new IllegalArgumentException("scratchBasis");
        }
        if (destRgb.length < 3) {
            throw new IllegalArgumentException("destRgb");
        }
        ShBasis.evaluateL2(nx, ny, nz, scratchBasis);
        float r = 0.0f;
        float g = 0.0f;
        float bl = 0.0f;
        for (int i = 0; i < 9; i++) {
            int o = i * 3;
            float bi = scratchBasis[i];
            r += coeffs.c[o] * bi;
            g += coeffs.c[o + 1] * bi;
            bl += coeffs.c[o + 2] * bi;
        }
        destRgb[0] = r;
        destRgb[1] = g;
        destRgb[2] = bl;
    }

    public static Vector3d evaluateL2(ShCoeffs9d coeffs, Vector3dc dir, Vector3d dest) {
        double[] b = ShBasis.evaluateL2(dir, new double[9]);
        return evaluateIrradiance(coeffs, b, dest);
    }

    public static Vector3f evaluateL3(ShCoeffs16f coeffs, Vector3fc dir, Vector3f dest) {
        float[] b = ShBasis.evaluateL3(dir, new float[16]);
        float r = 0.0f;
        float g = 0.0f;
        float bl = 0.0f;
        for (int i = 0; i < 16; i++) {
            int o = i * 3;
            float bi = b[i];
            r += coeffs.c[o] * bi;
            g += coeffs.c[o + 1] * bi;
            bl += coeffs.c[o + 2] * bi;
        }
        return dest.set(r, g, bl);
    }

    public static Vector3d evaluateL3(ShCoeffs16d coeffs, Vector3dc dir, Vector3d dest) {
        double[] b = ShBasis.evaluateL3(dir, new double[16]);
        double r = 0.0;
        double g = 0.0;
        double bl = 0.0;
        for (int i = 0; i < 16; i++) {
            int o = i * 3;
            double bi = b[i];
            r += coeffs.c[o] * bi;
            g += coeffs.c[o + 1] * bi;
            bl += coeffs.c[o + 2] * bi;
        }
        return dest.set(r, g, bl);
    }

    /**
     * Zero-allocation hot path for evaluating irradiance from L2 SH coefficients.
     * The result is written to destRgb as [r,g,b].
     */
    public static void evaluateIrradiance(ShCoeffs9d coeffs, double nx, double ny, double nz, double[] scratchBasis, double[] destRgb) {
        if (scratchBasis.length < 9) {
            throw new IllegalArgumentException("scratchBasis");
        }
        if (destRgb.length < 3) {
            throw new IllegalArgumentException("destRgb");
        }
        ShBasis.evaluateL2(nx, ny, nz, scratchBasis);
        double r = 0.0;
        double g = 0.0;
        double bl = 0.0;
        for (int i = 0; i < 9; i++) {
            int o = i * 3;
            double bi = scratchBasis[i];
            r += coeffs.c[o] * bi;
            g += coeffs.c[o + 1] * bi;
            bl += coeffs.c[o + 2] * bi;
        }
        destRgb[0] = r;
        destRgb[1] = g;
        destRgb[2] = bl;
    }

    private static Vector3f evaluateIrradiance(ShCoeffs9f coeffs, float[] basis, Vector3f dest) {
        float r = 0.0f;
        float g = 0.0f;
        float bl = 0.0f;
        for (int i = 0; i < 9; i++) {
            int o = i * 3;
            float bi = basis[i];
            r += coeffs.c[o] * bi;
            g += coeffs.c[o + 1] * bi;
            bl += coeffs.c[o + 2] * bi;
        }
        return dest.set(r, g, bl);
    }

    private static Vector3d evaluateIrradiance(ShCoeffs9d coeffs, double[] basis, Vector3d dest) {
        double r = 0.0;
        double g = 0.0;
        double bl = 0.0;
        for (int i = 0; i < 9; i++) {
            int o = i * 3;
            double bi = basis[i];
            r += coeffs.c[o] * bi;
            g += coeffs.c[o + 1] * bi;
            bl += coeffs.c[o + 2] * bi;
        }
        return dest.set(r, g, bl);
    }
}
