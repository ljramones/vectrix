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
 * Float transcendental functions for neural-network kernels, reproducible bit for bit everywhere.
 * <p>
 * {@code java.lang.Math.exp} and friends may differ in the last bit between CPUs and JIT tiers. These are built from
 * series with exact rational coefficients (Taylor and atanh series whose truncation error is below 6e-9 relative,
 * a tenth of a float ulp), argument reductions that are exact, and fused multiply-adds; the Vector API versions in
 * the kernels perform the same operations in the same order. Measured errors against the exact functions: see each
 * method.
 *
 * @since 2.5.0
 */
public final class NnMath {

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
    static final float SQRT2 = 1.4142135f;
    static final float THIRD = 0.33333334f;        // 1/(2j + 1)
    static final float FIFTH = 0.2f;
    static final float SEVENTH = 0.14285715f;
    static final float NINTH = 0.11111111f;
    static final float TWO_OVER_SQRT_PI = 1.1283792f;
    /** {@code (2/√π)·(-1)^n / (n!·(2n + 1))}, n = 0..12, rounded to float. */
    static final float[] ERF_C = {1.1283792f, -0.37612638f, 0.11283792f, -0.026866172f, 0.0052239778f,
        -8.548327e-4f, 1.2055333e-4f, -1.4925650e-5f, 1.6462114e-6f, -1.6365844e-7f, 1.4807193e-8f, -1.2290555e-9f,
        9.422759e-11f};
    static final int ERFC_TERMS = 64;
    static final double INV_SQRT2_D = 0.7071067811865476;
    /** {@code (2/√π)·(-1)^n / (n!·(2n + 1))}, n = 0..21, in double (truncation 2e-23 below 1). */
    static final double[] ERF_D = {
        1.1283791670955126, -0.37612638903183754, 0.11283791670955126,
        -0.026866170645131252, 0.005223977625442188, -0.0008548327023450853,
        0.00012055332981789664, -1.492565035840625e-05, 1.6462114365889248e-06,
        -1.6365844691234924e-07, 1.4807192815879218e-08, -1.2290555301717928e-09,
        9.422759064650411e-11, -6.7113668551641105e-12, 4.4632242632864775e-13,
        -2.7835162072109215e-14, 1.6342614095367152e-15, -9.063970842808673e-17,
        4.763348040515068e-18, -2.3784598852774293e-19, 1.131218725924631e-20,
        -5.136209054585811e-22};
    static final float SQRT_PI = 1.7724539f;

    private NnMath() {
    }

    /**
     * {@code e^x}, within 1.1 ulp (measured on [-87.3, 88.7]); overflows to +∞ above 88.72, underflows
     * gradually to 0 below -87.3.
     */
    public static float exp(float x) {
        float c = Math.min(Math.max(x, -104f), 89f);
        float kf = (c * LOG2E + ROUND) - ROUND;
        float r = Math.fma(kf, -LN2_HI, c);
        r = Math.fma(kf, -LN2_LO, r);
        float p = Math.fma(Math.fma(Math.fma(Math.fma(Math.fma(E7, r, E6), r, E5), r, E4), r, E3), r, E2);
        float t = Math.fma(p, r * r, r) + 1f;
        int k = (int) kf;
        int k1 = k >> 1;
        return t * Float.intBitsToFloat((k1 + 127) << 23) * Float.intBitsToFloat((k - k1 + 127) << 23);
    }

    /** {@code e^x - 1} without cancellation near 0 (degree-10 Taylor below 0.5 in magnitude), within 2 ulp. */
    public static float expm1(float x) {
        if (Math.abs(x) < 0.5f) {
            float p = Math.fma(Math.fma(Math.fma(Math.fma(Math.fma(Math.fma(Math.fma(Math.fma(E10, x, E9), x, E8), x,
                    E7), x, E6), x, E5), x, E4), x, E3), x, E2);
            return Math.fma(p, x * x, x);
        }
        return exp(x) - 1f;
    }

    /** {@code tanh x = expm1(2|x|) / (expm1(2|x|) + 2)}, signed; within 2.5 ulp; exactly ±1 beyond 9.1. */
    public static float tanh(float x) {
        float a = Math.min(Math.abs(x), 9.1f);
        float e = expm1(2f * a);
        return Math.copySign(e / (e + 2f), x);
    }

    /** The logistic function {@code 1 / (1 + e^-x)}, within 2.5 ulp. */
    public static float sigmoid(float x) {
        return 1f / (1f + exp(-x));
    }

    /**
     * {@code log x}, within 2 ulp: exact exponent extraction, then {@code 2·atanh(s)} with {@code s = f/(2+f)} as an
     * odd series. {@code log 0 = -∞}, negative arguments give NaN.
     */
    public static float log(float x) {
        if (!(x > 0f) || x == Float.POSITIVE_INFINITY) {
            return x == 0f ? Float.NEGATIVE_INFINITY : x == Float.POSITIVE_INFINITY ? x : Float.NaN;
        }
        int sub = 0;
        if (x < Float.MIN_NORMAL) {
            x *= 8388608f; // 2^23
            sub = 23;
        }
        int bits = Float.floatToRawIntBits(x);
        int e = (bits >> 23) - 127 - sub;
        float m = Float.intBitsToFloat((bits & 0x007fffff) | 0x3f800000);
        if (m > SQRT2) {
            m *= 0.5f;
            e += 1;
        }
        return logReduced(m - 1f, e);
    }

    /** {@code log(1 + y)}, accurate near 0, within 3 ulp. */
    public static float log1p(float y) {
        if (y > -0.29289323f && y < 0.41421357f) {
            return logReduced(y, 0);
        }
        return log(1f + y);
    }

    /** {@code e·ln 2 + log(1 + f)} for {@code 1 + f} in [√½, √2]. */
    static float logReduced(float f, int e) {
        float s = f / (2f + f);
        float z = s * s;
        float r = z * Math.fma(Math.fma(Math.fma(NINTH, z, SEVENTH), z, FIFTH), z, THIRD);
        float t = Math.fma(s, r, s);
        float ef = e;
        return Math.fma(ef, LN2_HI, Math.fma(ef, LN2_LO, 2f * t));
    }

    /**
     * The error function; exactly ±1 beyond 3.92. Below 1 it is the Taylor series (13 terms, truncation 7e-12); from
     * 1 it returns {@code 1 - erfc(a)}, with {@code erfc} from a 64-term continued fraction whose relative error
     * vanishes in the subtraction, and {@code a²} carried exactly into the exponential. Within 2.5 ulp (measured on
     * [0, 4]).
     */
    public static float erf(float x) {
        float a = Math.min(Math.abs(x), 3.92f);
        float a2 = a * a;
        float a2lo = Math.fma(a, a, -a2);
        float v;
        if (a < 1f) {
            // Taylor: (2/√π)·Σ (-1)^n a^(2n+1) / (n!·(2n + 1)), coefficients folded with 2/√π
            float p = ERF_C[ERF_C.length - 1];
            for (int n = ERF_C.length - 2; n >= 0; n--) {
                p = Math.fma(p, a2, ERF_C[n]);
            }
            v = a * p;
        } else {
            float g = exp(-a2) * (1f - a2lo); // e^-(a² exactly), a2lo being below half an ulp of a2
            // erfc(a) = e^-a² / (√π·(a + (1/2)/(a + 1/(a + (3/2)/(a + ...))))), evaluated from the bottom
            float t = a;
            for (int n = ERFC_TERMS; n >= 1; n--) {
                t = a + (0.5f * n) / t;
            }
            v = 1f - g / (SQRT_PI * t);
        }
        return Math.copySign(Math.min(v, 1f), x);
    }

    /**
     * The complementary error function {@code 1 - erf x}, accurate relative to its own (small) value for large
     * arguments: between -1 and 1 the Taylor series, and from 1 the continued fraction, both in double and rounded
     * once; below -1 {@code 1 - erf x} (no cancellation). Within 2.5 ulp (measured).
     */
    public static float erfc(float x) {
        if (!(x >= 1f)) {
            if (x > -1f) { // 1 - erf cancels for positive x: the series in double, rounded once
                double a = x;
                double a2 = a * a;
                double p = ERF_D[ERF_D.length - 1];
                for (int n = ERF_D.length - 2; n >= 0; n--) {
                    p = Math.fma(p, a2, ERF_D[n]);
                }
                return (float) (1.0 - a * p);
            }
            return 1f - erf(x); // NaN stays NaN; below -1 there is no cancellation
        }
        return (float) erfcTailD(Math.min(x, 10.1f)); // erfc underflows to 0 beyond about 10.05
    }

    /**
     * The standard normal distribution function {@code Φ(x) = erfc(-x/√2)/2}, accurate relative to its value in the
     * lower tail: below √2 it is evaluated in double (the Taylor series, or in the tail the continued fraction) and
     * rounded once, so neither the cancellation of {@code (1 + erf)/2} nor the rounding of {@code x/√2} shows.
     * Within 2.5 ulp (measured).
     */
    public static float normalCdf(float x) {
        if (!(x <= -1.4142135f)) {
            if (x >= 1.4142135f || x != x) {
                return 0.5f * (1f + erf(x * 0.70710677f)); // no cancellation; NaN stays NaN
            }
            // |x/√2| < 1: (1 + erf)/2 cancels for negative x, so evaluate the Taylor series in double and round
            // once (double +, × and fma are reproducible everywhere)
            double a = x * INV_SQRT2_D;
            double a2 = a * a;
            double p = ERF_D[ERF_D.length - 1];
            for (int n = ERF_D.length - 2; n >= 0; n--) {
                p = Math.fma(p, a2, ERF_D[n]);
            }
            return (float) (0.5 * (1.0 + a * p));
        }
        return (float) (0.5 * erfcTailD(Math.min(-x * INV_SQRT2_D, 10.1)));
    }

    /** The standard normal density {@code e^(-x²/2)/√(2π)}, computed in double and rounded once. */
    public static float normalPdf(float x) {
        double v = x;
        return (float) (0.3989422804014327 * expD(-0.5 * v * v));
    }

    static final double LOG2E_D = 1.4426950408889634;
    static final double LN2_HI_D = 0.6931471806019545;      // 31 bits: k·LN2_HI_D is exact for |k| < 2^20
    static final double LN2_LO_D = -4.2009150726810846e-11;
    static final double ROUND_D = 6755399441055744.0;       // 1.5·2^52
    /** 1/n!, n = 2..13 (truncation 4e-18 on |r| <= ln2/2). */
    static final double[] EXP_D = {0.5, 0.16666666666666666, 0.041666666666666664, 0.008333333333333333,
        0.001388888888888889, 0.0001984126984126984, 2.48015873015873e-05, 2.7557319223985893e-06,
        2.755731922398589e-07, 2.505210838544172e-08, 2.08767569878681e-09, 1.6059043836821613e-10};
    static final int ERFC_TERMS_D = 120;
    static final double SQRT_PI_D = 1.7724538509055159;

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
    /** 1/(2j+1), j = 1..10. */
    static final double[] LOG_D = {0.3333333333333333, 0.2, 0.14285714285714285, 0.1111111111111111,
        0.09090909090909091, 0.07692307692307693, 0.06666666666666667, 0.058823529411764705, 0.05263157894736842,
        0.047619047619047616};

    /**
     * {@code sin a} in double, reproducibly: the argument is reduced by fdlibm's three-part π/2 (exact products for
     * {@code |a|} up to about 1.6e6, so the reduction loses nothing there), then Taylor series on [-π/4, π/4].
     * Within 1 ulp of 1 absolutely (measured); NaN and infinities give NaN.
     */
    public static double sinD(double a) {
        return sinCosD(a, true);
    }

    /** {@code cos a} in double, reproducibly; see {@link #sinD}. */
    public static double cosD(double a) {
        return sinCosD(a, false);
    }

    private static double sinCosD(double a, boolean sine) {
        double kf = (a * TWO_OVER_PI_D + ROUND_D) - ROUND_D;
        if (kf != kf || Math.abs(kf) > 9.0e15) {
            return Double.NaN;
        }
        double r = Math.fma(kf, -PIO2_1, a);
        r = Math.fma(kf, -PIO2_2, r);
        r = Math.fma(kf, -PIO2_3, r);
        r = Math.fma(kf, -PIO2_3T, r);
        double z = r * r;
        double ps = SIN_D[SIN_D.length - 1];
        for (int n = SIN_D.length - 2; n >= 0; n--) {
            ps = Math.fma(ps, z, SIN_D[n]);
        }
        double pc = COS_D[COS_D.length - 1];
        for (int n = COS_D.length - 2; n >= 0; n--) {
            pc = Math.fma(pc, z, COS_D[n]);
        }
        double sn = Math.fma(r * z, ps, r);
        double cs = Math.fma(z, pc, 1.0);
        int q = (int) ((long) kf & 3) + (sine ? 0 : 1); // cos a = sin(a + π/2)
        q &= 3;
        return q == 0 ? sn : q == 1 ? cs : q == 2 ? -sn : -cs;
    }

    /**
     * {@code log x} in double, reproducibly ({@code 2·atanh} series after exact exponent extraction); within 2 ulp
     * (measured). {@code log 0 = -∞}; negative arguments give NaN.
     */
    public static double logD(double x) {
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

    /**
     * {@code b^y} in double, reproducibly, as {@code e^(y·log b)}, for {@code b > 0} and moderate results; the rounding
     * of {@code y·log b} costs about {@code |y·log b|} ulps (2e-15 relative for {@code 10000^-2}).
     */
    public static double powD(double b, double y) {
        return expD(y * logD(b));
    }

    /**
     * {@code e^x} in double, reproducibly (Cody–Waite reduction, degree-13 Taylor), for {@code |x| <= 700}; within
     * 1 ulp (measured).
     */
    public static double expD(double x) {
        double kf = (x * LOG2E_D + ROUND_D) - ROUND_D;
        double r = Math.fma(kf, -LN2_HI_D, x);
        r = Math.fma(kf, -LN2_LO_D, r);
        double p = EXP_D[EXP_D.length - 1];
        for (int n = EXP_D.length - 2; n >= 0; n--) {
            p = Math.fma(p, r, EXP_D[n]);
        }
        return Math.scalb(Math.fma(p, r * r, r) + 1.0, (int) kf);
    }

    /** {@code erfc(a)} for {@code a >= 1}, in double: the continued fraction times {@code e^-a²}. */
    static double erfcTailD(double a) {
        double t = a;
        for (int n = ERFC_TERMS_D; n >= 1; n--) {
            t = a + (0.5 * n) / t;
        }
        return expD(-a * a) / (SQRT_PI_D * t);
    }

}
