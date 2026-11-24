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
package org.dynamisengine.vectrix.compute;

/**
 * Host-side copies of the double-precision Vectrix {@code core.ReproducibleMath} functions the wrappers need, so that
 * vectrix-gpu has no runtime dependency on Vectrix: {@code pow}, and the {@code log} and {@code exp} it is built
 * from. Each is the Vectrix method's code with the same constants; {@code HostMathTest} checks they return the same
 * bits. Change both together.
 */
final class HostMath {

    private static final double LOG2E_D = 1.4426950408889634;
    private static final double LN2_HI_D = 0.6931471806019545;
    private static final double LN2_LO_D = -4.2009150726810846e-11;
    private static final double ROUND_D = 6755399441055744.0;
    private static final double[] EXP_D = {0.5, 0.16666666666666666, 0.041666666666666664, 0.008333333333333333,
        0.001388888888888889, 0.0001984126984126984, 2.48015873015873e-05, 2.7557319223985893e-06,
        2.755731922398589e-07, 2.505210838544172e-08, 2.08767569878681e-09, 1.6059043836821613e-10};
    private static final double[] LOG_D = {0.3333333333333333, 0.2, 0.14285714285714285, 0.1111111111111111,
        0.09090909090909091, 0.07692307692307693, 0.06666666666666667, 0.058823529411764705, 0.05263157894736842,
        0.047619047619047616};

    private HostMath() {
    }

    /** {@code ReproducibleMath.log(double)}. */
    static double log(double x) {
        if (!(x > 0) || x == Double.POSITIVE_INFINITY) {
            return x == 0 ? Double.NEGATIVE_INFINITY : x == Double.POSITIVE_INFINITY ? x : Double.NaN;
        }
        int sub = 0;
        if (x < Double.MIN_NORMAL) {
            x *= 0x1p54;
            sub = 54;
        }
        int e = Math.getExponent(x);
        double m = Math.scalb(x, -e);
        if (m > 1.4142135623730951) {
            m *= 0.5;
            e += 1;
        }
        e -= sub;
        double f = m - 1.0;
        double sv = f / (2.0 + f);
        double z = sv * sv;
        double p = LOG_D[LOG_D.length - 1];
        for (int n = LOG_D.length - 2; n >= 0; n--) {
            p = Math.fma(p, z, LOG_D[n]);
        }
        double t = Math.fma(sv * z, p, sv);
        return Math.fma(e, LN2_HI_D, Math.fma(e, LN2_LO_D, 2.0 * t));
    }

    /** {@code ReproducibleMath.pow(double, double)}: {@code e^(y·log b)}. */
    static double pow(double b, double y) {
        return exp(y * log(b));
    }

    /** {@code ReproducibleMath.exp(double)}. */
    static double exp(double x) {
        if (!(x > -746.0)) {
            return x != x ? x : 0.0;
        }
        if (x > 710.0) {
            return Double.POSITIVE_INFINITY;
        }
        double kf = (x * LOG2E_D + ROUND_D) - ROUND_D;
        double r = Math.fma(kf, -LN2_HI_D, x);
        r = Math.fma(kf, -LN2_LO_D, r);
        double p = EXP_D[EXP_D.length - 1];
        for (int n = EXP_D.length - 2; n >= 0; n--) {
            p = Math.fma(p, r, EXP_D[n]);
        }
        int k = (int) kf;
        int k1 = k >> 1;
        return Math.scalb(Math.scalb(Math.fma(p, r * r, r) + 1.0, k1), k - k1);
    }
}
