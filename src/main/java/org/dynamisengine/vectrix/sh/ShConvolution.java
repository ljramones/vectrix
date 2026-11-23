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

import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Convolution of SH radiance with the clamped-cosine (Lambert) kernel, and evaluation of SH coefficient sets at a
 * direction.
 * <p>
 * {@code convolveLambertL2}/{@code L3} turn radiance coefficients into irradiance coefficients by scaling band
 * {@code l} by {@code Â_l}: {@code π} for band 0, {@code 2π/3} for band 1, {@code π/4} for band 2 and 0 for band 3
 * (Ramamoorthi and Hanrahan 2001). The {@code evaluate...} methods then reconstruct
 * {@code f(n) = Σ c_i Y_i(n)} per channel; they do not convolve, so pass irradiance coefficients to get irradiance.
 * Coefficients follow the {@link ShBasis} index order. Methods are stateless, thread-safe and do not allocate.
 *
 * @since 1.0.0
 */

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

    /**
     * Convolves radiance coefficients (bands 0..2) with the clamped-cosine kernel, giving irradiance coefficients:
     * each coefficient of band {@code l} is multiplied by {@code Â_l} (see the class comment).
     *
     * @param in the radiance coefficients
     * @param dest receives the irradiance coefficients; may be {@code in}
     * @return {@code dest}
     */
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

    /**
     * Convolves radiance coefficients (bands 0..2) with the clamped-cosine kernel, giving irradiance coefficients:
     * each coefficient of band {@code l} is multiplied by {@code Â_l} (see the class comment).
     *
     * @param in the radiance coefficients
     * @param dest receives the irradiance coefficients; may be {@code in}
     * @return {@code dest}
     */
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

    /**
     * Convolves radiance coefficients (bands 0..3) with the clamped-cosine kernel, giving irradiance coefficients:
     * each coefficient of band {@code l} is multiplied by {@code Â_l} (see the class comment).
     *
     * @param in the radiance coefficients
     * @param dest receives the irradiance coefficients; may be {@code in}
     * @return {@code dest}
     */
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

    /**
     * Convolves radiance coefficients (bands 0..3) with the clamped-cosine kernel, giving irradiance coefficients:
     * each coefficient of band {@code l} is multiplied by {@code Â_l} (see the class comment).
     *
     * @param in the radiance coefficients
     * @param dest receives the irradiance coefficients; may be {@code in}
     * @return {@code dest}
     */
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

    /**
     * Evaluates the 9-coefficient SH function {@code Σ c_i Y_i(dir)} per RGB channel. Pass irradiance coefficients
     * (from {@code convolveLambertL2}) to get irradiance.
     *
     * @param coeffs the coefficients
     * @param dir the direction, unit length; it is not normalized
     * @param dest receives {@code (r, g, b)}; may be {@code dir}
     * @return {@code dest}
     */
    public static Vector3f evaluateL2(ShCoeffs9f coeffs, Vector3fc dir, Vector3f dest) {
        return ShBasis.dot9(coeffs.c, dir.x(), dir.y(), dir.z(), dest);
    }

    /**
     * Evaluates the 9-coefficient SH function {@code Σ c_i Y_i(n)} per RGB channel into an array, without
     * allocating. Like the other {@code evaluate...} methods it does not convolve: pass irradiance coefficients
     * (from {@code convolveLambertL2}) to get irradiance. Renamed from {@code evaluateIrradiance} in 3.0.0.
     *
     * @param coeffs the coefficients
     * @param nx the x component of the unit normal; it is not normalized
     * @param ny the y component of the unit normal
     * @param nz the z component of the unit normal
     * @param scratchBasis caller scratch of length at least 9; receives the basis values
     * @param destRgb receives {@code r, g, b} in elements 0..2
     * @throws IllegalArgumentException if {@code scratchBasis.length < 9} or {@code destRgb.length < 3}
     * @since 3.0.0
     */
    public static void evaluateL2(ShCoeffs9f coeffs, float nx, float ny, float nz, float[] scratchBasis, float[] destRgb) {
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

    /**
     * Evaluates the 9-coefficient SH function {@code Σ c_i Y_i(dir)} per RGB channel. Pass irradiance coefficients
     * (from {@code convolveLambertL2}) to get irradiance.
     *
     * @param coeffs the coefficients
     * @param dir the direction, unit length; it is not normalized
     * @param dest receives {@code (r, g, b)}; may be {@code dir}
     * @return {@code dest}
     */
    public static Vector3d evaluateL2(ShCoeffs9d coeffs, Vector3dc dir, Vector3d dest) {
        return ShBasis.dot9(coeffs.c, dir.x(), dir.y(), dir.z(), dest);
    }

    /**
     * Evaluates the 16-coefficient SH function {@code Σ c_i Y_i(dir)} per RGB channel. Pass irradiance coefficients
     * (from {@code convolveLambertL3}) to get irradiance.
     *
     * @param coeffs the coefficients
     * @param dir the direction, unit length; it is not normalized
     * @param dest receives {@code (r, g, b)}; may be {@code dir}
     * @return {@code dest}
     */
    public static Vector3f evaluateL3(ShCoeffs16f coeffs, Vector3fc dir, Vector3f dest) {
        return ShBasis.dot16(coeffs.c, dir.x(), dir.y(), dir.z(), dest);
    }

    /**
     * Evaluates the 16-coefficient SH function {@code Σ c_i Y_i(dir)} per RGB channel. Pass irradiance coefficients
     * (from {@code convolveLambertL3}) to get irradiance.
     *
     * @param coeffs the coefficients
     * @param dir the direction, unit length; it is not normalized
     * @param dest receives {@code (r, g, b)}; may be {@code dir}
     * @return {@code dest}
     */
    public static Vector3d evaluateL3(ShCoeffs16d coeffs, Vector3dc dir, Vector3d dest) {
        return ShBasis.dot16(coeffs.c, dir.x(), dir.y(), dir.z(), dest);
    }

    /**
     * Evaluates the 9-coefficient SH function {@code Σ c_i Y_i(n)} per RGB channel into an array, without
     * allocating. Like the other {@code evaluate...} methods it does not convolve: pass irradiance coefficients
     * (from {@code convolveLambertL2}) to get irradiance. Renamed from {@code evaluateIrradiance} in 3.0.0.
     *
     * @param coeffs the coefficients
     * @param nx the x component of the unit normal; it is not normalized
     * @param ny the y component of the unit normal
     * @param nz the z component of the unit normal
     * @param scratchBasis caller scratch of length at least 9; receives the basis values
     * @param destRgb receives {@code r, g, b} in elements 0..2
     * @throws IllegalArgumentException if {@code scratchBasis.length < 9} or {@code destRgb.length < 3}
     * @since 3.0.0
     */
    public static void evaluateL2(ShCoeffs9d coeffs, double nx, double ny, double nz, double[] scratchBasis, double[] destRgb) {
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


}
