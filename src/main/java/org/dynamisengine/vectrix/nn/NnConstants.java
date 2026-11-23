/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
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
package org.dynamisengine.vectrix.nn;

/**
 * The constants of {@link org.dynamisengine.vectrix.core.ReproducibleMath} that the Vector API kernels of this
 * package expand inline. They must equal that class's; the kernel tests compare every SIMD path with the scalar
 * functions bit for bit.
 */
final class NnConstants {

    static final float LOG2E = 1.442695040888963f;
    static final float LN2_HI = 0.693359375f;      // 9 bits: k·LN2_HI is exact
    static final float LN2_LO = -2.12194440e-4f;
    static final float ROUND = 12582912f;          // 1.5·2^23: (t + ROUND) - ROUND rounds t to an integer
    static final float E2 = 0.5f;                  // 1/n!, rounded to float
    static final float E3 = 0.16666667f;
    static final float E4 = 0.041666668f;
    static final float E5 = 0.008333334f;
    static final float E6 = 0.0013888889f;
    static final float E7 = 1.984127e-4f;
    static final float E8 = 2.4801588e-5f;
    static final float E9 = 2.7557319e-6f;
    static final float E10 = 2.7557319e-7f;
    static final double ROUND_D = 6755399441055744.0;       // 1.5·2^52
    static final double PIO2_1 = 1.57079632673412561417e+00;   // fdlibm's split of π/2: 33 + 33 + 33 bits
    static final double PIO2_2 = 6.07710050630396597660e-11;
    static final double PIO2_3 = 2.02226624871116645580e-21;
    static final double PIO2_3T = 8.47842766036889956997e-32;
    static final double TWO_OVER_PI_D = 0.6366197723675814;
    /** (-1)^n/(2n+1)!, n = 1..8, and (-1)^n/(2n)!, n = 1..9 (truncation below 1e-19 on |r| <= π/4). */
    static final double[] SIN_D = {-0.16666666666666666, 0.008333333333333333, -0.0001984126984126984,
        2.7557319223985893e-06, -2.505210838544172e-08, 1.6059043836821613e-10, -7.647163731819816e-13,
        2.8114572543455206e-15};
    static final double[] COS_D = {-0.5, 0.041666666666666664, -0.001388888888888889, 2.48015873015873e-05,
        -2.755731922398589e-07, 2.08767569878681e-09, -1.1470745597729725e-11, 4.779477332387385e-14,
        -1.5619206968586225e-16};

    private NnConstants() {
    }
}
