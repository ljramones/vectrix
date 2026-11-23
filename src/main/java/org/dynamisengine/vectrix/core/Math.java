/*
 * The MIT License
 *
 * Copyright (c) 2015-2024 JOML
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
package org.dynamisengine.vectrix.core;

/**
 * Scalar math functions used throughout the library: wrappers over {@link AccurateMath} and {@link java.lang.Math},
 * interpolation, rounding, and optional fast approximations of sine, cosine and arc tangent.
 * <p>
 * By default, the transcendental functions go through {@link AccurateMath}. To use the approximations in this class
 * for {@code sin}, {@code cos} and {@code atan2}, start the JVM with {@code -Dvectrix.fastmath=true} (or the older
 * {@code -Djoml.fastmath}). {@code -Dvectrix.strictMath=true} turns the approximations off again.
 * <p>
 * There are two algorithms for approximating sin/cos:
 * <ol>
 * <li>arithmetic <a href="http://www.java-gaming.org/topics/joml-1-8-0-release/37491/msg/361815/view.html#msg361815">polynomial approximation</a> contributed by roquendm
 * <li>theagentd's <a href="http://www.java-gaming.org/topics/extremely-fast-sine-cosine/36469/msg/346213/view.html#msg346213">linear interpolation</a> variant of Riven's algorithm from
 * <a href="http://www.java-gaming.org/topics/extremely-fast-sine-cosine/36469/view.html">http://www.java-gaming.org/</a>
 * </ol>
 * By default, the first algorithm is used. To use the second one, start the JVM with
 * {@code -Dvectrix.sinLookup=true}. The lookup table size can be set with {@code -Dvectrix.sinLookup.bits=<n>},
 * where {@code n} is the number of bits of the table index. Both approximations fall back to the accurate path for
 * large arguments, NaN and infinities.
 * <p>
 * All methods are static and thread-safe.
 *
 * @author Kai Burjack
 */
public final class Math {

    /** Not instantiable: every member is static. */
    private Math() {
    }

    /*
     * The following implementation of an approximation of sine and cosine was
     * thankfully donated by Riven from http://java-gaming.org/.
     * 
     * The code for linear interpolation was gratefully donated by theagentd
     * from the same site.
     */
    /** The double closest to pi. */
    public static final double PI = java.lang.Math.PI;
    /** The double closest to 2 pi. */
    public static final double PI_TIMES_2 = PI * 2.0;
    /** The float closest to pi. */
    public static final float PI_f = (float) java.lang.Math.PI;
    /** Two times {@link #PI_f}, the float closest to 2 pi. */
    public static final float PI_TIMES_2_f = PI_f * 2.0f;
    /** The double closest to pi / 2. */
    public static final double PI_OVER_2 = PI * 0.5;
    /** The float closest to pi / 2. */
    public static final float PI_OVER_2_f = (float) (PI * 0.5);
    /** The double closest to pi / 4. */
    public static final double PI_OVER_4 = PI * 0.25;
    /** The float closest to pi / 4. */
    public static final float PI_OVER_4_f = (float) (PI * 0.25);
    /** {@code 1 / PI} in double. */
    public static final double ONE_OVER_PI = 1.0 / PI;
    /** {@code 1 / PI} computed in double and rounded to float. */
    public static final float ONE_OVER_PI_f = (float) (1.0 / PI);
    /** {@code -Djoml.fastmath}, unless {@code -Dvectrix.strictMath} asks for reproducible transcendentals. */
    private static final boolean FAST_MATH = Options.FASTMATH && !Options.STRICT_MATH;
    private static final int lookupBits = Options.SIN_LOOKUP_BITS;
    private static final int lookupTableSize = 1 << lookupBits;
    private static final int lookupTableSizeMinus1 = lookupTableSize - 1;
    private static final int lookupTableSizeWithMargin = lookupTableSize + 1;
    private static final float pi2OverLookupSize = PI_TIMES_2_f / lookupTableSize;
    private static final float lookupSizeOverPi2 = lookupTableSize / PI_TIMES_2_f;
    private static final float sinTable[];
    static {
        if (FAST_MATH && Options.SIN_LOOKUP) {
            sinTable = new float[lookupTableSizeWithMargin];
            for (int i = 0; i < lookupTableSizeWithMargin; i++) {
                double d = i * pi2OverLookupSize;
                sinTable[i] = (float) AccurateMath.sin(d);
            }
        } else {
            sinTable = null;
        }
    }

    private static final double c1 = Double.longBitsToDouble(-4628199217061079772L);
    private static final double c2 = Double.longBitsToDouble(4575957461383582011L);
    private static final double c3 = Double.longBitsToDouble(-4671919876300759001L);
    private static final double c4 = Double.longBitsToDouble(4523617214285661942L);
    private static final double c5 = Double.longBitsToDouble(-4730215272828025532L);
    private static final double c6 = Double.longBitsToDouble(4460272573143870633L);
    private static final double c7 = Double.longBitsToDouble(-4797767418267846529L);

    /**
     * @author theagentd
     */
    static double sin_theagentd_arith(double x){
        double xi = floor((x + PI_OVER_4) * ONE_OVER_PI);
        double x_ = x - xi * PI;
        double sign = ((int)xi & 1) * -2 + 1;
        double x2 = x_ * x_;
        double sin = x_;
        double tx = x_ * x2;
        sin += tx * c1; tx *= x2;
        sin += tx * c2; tx *= x2;
        sin += tx * c3; tx *= x2;
        sin += tx * c4; tx *= x2;
        sin += tx * c5; tx *= x2;
        sin += tx * c6; tx *= x2;
        sin += tx * c7;
        return sign * sin;
    }

    /**
     * Reference: <a href="http://www.java-gaming.org/topics/joml-1-8-0-release/37491/msg/361718/view.html#msg361718">http://www.java-gaming.org/</a>
     */
    static double sin_roquen_arith(double x) {
        double xi = Math.floor((x + PI_OVER_4) * ONE_OVER_PI);
        double x_ = x - xi * PI;
        double sign = ((int)xi & 1) * -2 + 1;
        double x2 = x_ * x_;

        // code from sin_theagentd_arith:
        // double sin = x_;
        // double tx = x_ * x2;
        // sin += tx * c1; tx *= x2;
        // sin += tx * c2; tx *= x2;
        // sin += tx * c3; tx *= x2;
        // sin += tx * c4; tx *= x2;
        // sin += tx * c5; tx *= x2;
        // sin += tx * c6; tx *= x2;
        // sin += tx * c7;
        // return sign * sin;

        double sin;
        x_  = sign*x_;
        sin =          c7;
        sin = sin*x2 + c6;
        sin = sin*x2 + c5;
        sin = sin*x2 + c4;
        sin = sin*x2 + c3;
        sin = sin*x2 + c2;
        sin = sin*x2 + c1;
        return x_ + x_*x2*sin;
    }

    private static final double s5 = Double.longBitsToDouble(4523227044276562163L);
    private static final double s4 = Double.longBitsToDouble(-4671934770969572232L);
    private static final double s3 = Double.longBitsToDouble(4575957211482072852L);
    private static final double s2 = Double.longBitsToDouble(-4628199223918090387L);
    private static final double s1 = Double.longBitsToDouble(4607182418589157889L);

    /**
     * Reference: <a href="http://www.java-gaming.org/topics/joml-1-8-0-release/37491/msg/361815/view.html#msg361815">http://www.java-gaming.org/</a>
     */
    static double sin_roquen_9(double v) {
      double i  = java.lang.Math.rint(v* ONE_OVER_PI);
      double x  = v - i * Math.PI;
      double qs = 1-2*((int)i & 1);
      double x2 = x*x;
      double r;
      x = qs*x;
      r =        s5;
      r = r*x2 + s4;
      r = r*x2 + s3;
      r = r*x2 + s2;
      r = r*x2 + s1;
      return x*r;
    }

    private static final double k1 = Double.longBitsToDouble(-4628199217061079959L);
    private static final double k2 = Double.longBitsToDouble(4575957461383549981L);
    private static final double k3 = Double.longBitsToDouble(-4671919876307284301L);
    private static final double k4 = Double.longBitsToDouble(4523617213632129738L);
    private static final double k5 = Double.longBitsToDouble(-4730215344060517252L);
    private static final double k6 = Double.longBitsToDouble(4460268259291226124L);
    private static final double k7 = Double.longBitsToDouble(-4798040743777455072L);

    /**
     * Reference: <a href="http://www.java-gaming.org/topics/joml-1-8-0-release/37491/msg/361815/view.html#msg361815">http://www.java-gaming.org/</a>
     */
    static double sin_roquen_newk(double v) {
      double i  = java.lang.Math.rint(v* ONE_OVER_PI);
      double x  = v - i * Math.PI;
      double qs = 1-2*((int)i & 1);
      double x2 = x*x;
      double r;
      x = qs*x;
      r =        k7;
      r = r*x2 + k6;
      r = r*x2 + k5;
      r = r*x2 + k4;
      r = r*x2 + k3;
      r = r*x2 + k2;
      r = r*x2 + k1;
      return x + x*x2*r;
    }

    /**
     * Reference: <a href="http://www.java-gaming.org/topics/extremely-fast-sine-cosine/36469/msg/349515/view.html#msg349515">http://www.java-gaming.org/</a>
     */
    static float sin_theagentd_lookup(float rad) {
        float index = rad * lookupSizeOverPi2;
        int ii = (int)java.lang.Math.floor(index);
        float alpha = index - ii;
        int i = ii & lookupTableSizeMinus1;
        float sin1 = sinTable[i];
        float sin2 = sinTable[i + 1];
        return sin1 + (sin2 - sin1) * alpha;
    }

    /**
     * Returns the sine of an angle. Without {@code -Dvectrix.fastmath} this is {@link AccurateMath#sin(double)},
     * computed in double and rounded to float; with it, the polynomial or lookup-table approximation described in the
     * class comment.
     *
     * @param rad the angle in radians
     * @return the sine of {@code rad}
     */
    public static float sin(float rad) {
        if (FAST_MATH) {
            if (Options.SIN_LOOKUP)
                return lookupSin(rad);
            return (float) fastSin(rad);
        }
        return (float) AccurateMath.sin(rad);
    }
    /**
     * Returns the sine of an angle. Without {@code -Dvectrix.fastmath} this is {@link AccurateMath#sin(double)};
     * with it, the polynomial approximation, or the lookup table, which reads the angle as a float.
     *
     * @param rad the angle in radians
     * @return the sine of {@code rad}
     */
    public static double sin(double rad) {
        if (FAST_MATH) {
            if (Options.SIN_LOOKUP)
                return lookupSin((float) rad);
            return fastSin(rad);
        }
        return AccurateMath.sin(rad);
    }

    /**
     * Returns the cosine of an angle. Without {@code -Dvectrix.fastmath} this is {@link AccurateMath#cos(double)},
     * computed in double and rounded to float; with it, the approximation described in the class comment.
     *
     * @param rad the angle in radians
     * @return the cosine of {@code rad}
     */
    public static float cos(float rad) {
        if (FAST_MATH)
            return Options.SIN_LOOKUP ? lookupCos(rad) : (float) fastCos(rad);
        return (float) AccurateMath.cos(rad);
    }
    /**
     * Returns the cosine of an angle. Without {@code -Dvectrix.fastmath} this is {@link AccurateMath#cos(double)}; with
     * it, the polynomial approximation, or the lookup table, which reads the angle as a float.
     *
     * @param rad the angle in radians
     * @return the cosine of {@code rad}
     */
    public static double cos(double rad) {
        if (FAST_MATH)
            return Options.SIN_LOOKUP ? lookupCos((float) rad) : fastCos(rad);
        return AccurateMath.cos(rad);
    }

    /**
     * The fast-math sine: a minimax polynomial after reduction by pi, within 1e-10 of the exact value for
     * {@code |v| < FAST_SIN_LIMIT} (1e6). Beyond that, and for NaN and infinities, it uses the accurate path. Before
     * 2.5.0 the reduction's int conversion saturated beyond about 6.7e9 rad, which flipped the sign, and
     * {@code sin(1e19)} returned 0.
     */
    static double fastSin(double v) {
        if (!(java.lang.Math.abs(v) < FAST_SIN_LIMIT))
            return AccurateMath.sin(v);
        if (v == 0)
            return v; // keeps -0
        return sin_roquen_newk(v);
    }

    /**
     * The fast-math cosine: {@code sin(v + pi/2)}, with the shift taken in double. It is within 2e-10 of the exact value
     * for {@code |v| < 1e6} (the shift rounds to the argument's ulp), and uses the accurate path beyond.
     */
    static double fastCos(double v) {
        if (!(java.lang.Math.abs(v) < FAST_SIN_LIMIT))
            return AccurateMath.cos(v);
        return sin_roquen_newk(v + PI_OVER_2);
    }

    /**
     * The lookup-table sine ({@code -Djoml.sinLookup}). The table index is a float, so its angle error grows with
     * {@code |rad|}. Within {@code LOOKUP_LIMIT} (256 rad) the sine and cosine are within 4e-5 of the exact values; beyond that, and for NaN and
     * infinities, it uses the accurate path. Before 2.5.0 large arguments gave values like {@code sin(1e6) = 176395.95}.
     */
    static float lookupSin(float rad) {
        if (!(java.lang.Math.abs(rad) < LOOKUP_LIMIT))
            return (float) AccurateMath.sin(rad);
        if (rad == 0)
            return rad;
        return sin_theagentd_lookup(rad);
    }

    /** The lookup-table cosine: the shift by pi/2 is taken in double before the table lookup. */
    static float lookupCos(float rad) {
        if (!(java.lang.Math.abs(rad) < LOOKUP_LIMIT))
            return (float) AccurateMath.cos(rad);
        return sin_theagentd_lookup((float) (rad + PI_OVER_2));
    }

    /** |argument| below which the fast-math polynomial sine is used. */
    static final double FAST_SIN_LIMIT = 1e6;
    /** |argument| below which the lookup-table sine is used. */
    static final float LOOKUP_LIMIT = 256f;

    /**
     * {@code cos(angle)} given {@code sin = sin(angle)}. Where {@code |cos| ≥ |sin|} the identity
     * {@code ±sqrt(1 − sin²)} is exact to rounding and cheaper than a cosine; elsewhere (near ±90 degrees, where that
     * identity cancels and loses up to all digits) and for huge angles the cosine is evaluated directly. (Before 2.4.0
     * the identity was used everywhere, losing up to 1e-8 in double near ±90 degrees, and the sign was wrong beyond
     * about 1.35e10 rad.)
     *
     * @param sin the sine of {@code angle}
     * @param angle the angle in radians
     * @return the cosine of {@code angle}
     */
    public static float cosFromSin(float sin, float angle) {
        if (FAST_MATH)
            return cos(angle);
        if (java.lang.Math.abs(sin) > 0.70710677f || !(java.lang.Math.abs(angle) < 1e9f))
            return (float) AccurateMath.cos(angle);
        float cos = (float) java.lang.Math.sqrt(1.0 - (double) sin * sin);
        return cosIsNegative(angle) ? -cos : cos;
    }
    /**
     * Returns {@code cos(angle)} given {@code sin = sin(angle)}; see {@link #cosFromSin(float, float)}.
     *
     * @param sin the sine of {@code angle}
     * @param angle the angle in radians
     * @return the cosine of {@code angle}
     */
    public static double cosFromSin(double sin, double angle) {
        if (FAST_MATH)
            return cos(angle);
        if (java.lang.Math.abs(sin) > 0.7071067811865476 || !(java.lang.Math.abs(angle) < 1e9))
            return AccurateMath.cos(angle);
        double cos = sqrt(1.0 - sin * sin);
        return cosIsNegative(angle) ? -cos : cos;
    }
    /** Whether cos(angle) < 0, for |angle| < 1e9 and |cos| ≥ 0.707 (far from the sign changes, so reduction is safe). */
    private static boolean cosIsNegative(double angle) {
        double a = angle + PI_OVER_2;
        double b = a - java.lang.Math.floor(a / PI_TIMES_2) * PI_TIMES_2;
        return b >= PI;
    }

    /* Other math functions not yet approximated */

    /**
     * Returns the square root, computed in double and rounded to float (correctly rounded).
     *
     * @param r the value
     * @return the square root of {@code r}; NaN if {@code r} is negative or NaN
     */
    public static float sqrt(float r) {
        return (float) java.lang.Math.sqrt(r);
    }
    /**
     * Returns the correctly rounded square root ({@link java.lang.Math#sqrt(double)}).
     *
     * @param r the value
     * @return the square root of {@code r}; NaN if {@code r} is negative or NaN
     */
    public static double sqrt(double r) {
        return java.lang.Math.sqrt(r);
    }

    /**
     * {@code 1 / sqrt(r)}, computed in double and rounded once (before 2.5.0 two float roundings, up to 1.47 ulp).
     *
     * @param r the value
     * @return the reciprocal square root of {@code r}; infinity for 0, NaN for negative values
     */
    public static float invsqrt(float r) {
        return (float) (1.0 / java.lang.Math.sqrt(r));
    }
    /**
     * {@code 1 / sqrt(r)}, refined by one Newton step on the exactly evaluated residual {@code 1 - r y²}, which makes
     * it correctly rounded in nearly all cases. The plain quotient rounds twice and errs by up to 1.45 ulp. Outside
     * {@code [2^-1000, 2^1000]}, and for 0, infinities and NaN, the plain quotient is returned.
     *
     * @param r the value
     * @return the reciprocal square root of {@code r}; infinity for 0, NaN for negative values
     */
    public static double invsqrt(double r) {
        double y = 1.0 / java.lang.Math.sqrt(r);
        if (!(r > 0x1p-1000 && r < 0x1p1000)) {
            return y;
        }
        double hi = y * y, lo = java.lang.Math.fma(y, y, -hi);
        double e = java.lang.Math.fma(-r, hi, 1.0) - r * lo;
        return java.lang.Math.fma(0.5 * y, e, y);
    }

    /**
     * Returns the tangent, computed in double by {@link AccurateMath#tan(double)} and rounded to float.
     *
     * @param r the angle in radians
     * @return the tangent of {@code r}
     */
    public static float tan(float r) {
        return (float) AccurateMath.tan(r);
    }
    /**
     * Returns the tangent ({@link AccurateMath#tan(double)}).
     *
     * @param r the angle in radians
     * @return the tangent of {@code r}
     */
    public static double tan(double r) {
        return AccurateMath.tan(r);
    }

    /**
     * Returns the arc cosine, computed in double by {@link AccurateMath#acos(double)} and rounded to float.
     *
     * @param r the cosine, in {@code [-1, 1]}
     * @return the angle in radians, in {@code [0, pi]}; NaN if {@code |r| > 1} or {@code r} is NaN
     */
    public static float acos(float r) {
        return (float) AccurateMath.acos(r);
    }
    /**
     * Returns the arc cosine ({@link AccurateMath#acos(double)}).
     *
     * @param r the cosine, in {@code [-1, 1]}
     * @return the angle in radians, in {@code [0, pi]}; NaN if {@code |r| > 1} or {@code r} is NaN
     */
    public static double acos(double r) {
        return AccurateMath.acos(r);
    }

    /**
     * Returns the arc cosine, with the argument clamped to {@code [-1, 1]} first, so a dot product that rounding pushed
     * slightly outside the range still gives an angle. NaN stays NaN.
     *
     * @param v the cosine
     * @return the angle in radians, in {@code [0, pi]}
     */
    public static float safeAcos(float v) {
        if (v < -1.0f)
            return Math.PI_f;
        else if (v > +1.0f)
            return 0.0f;
        else
            return acos(v);
    }
    /**
     * Returns the arc cosine, with the argument clamped to {@code [-1, 1]} first, so a dot product that rounding pushed
     * slightly outside the range still gives an angle. NaN stays NaN.
     *
     * @param v the cosine
     * @return the angle in radians, in {@code [0, pi]}
     */
    public static double safeAcos(double v) {
        if (v < -1.0)
            return Math.PI;
        else if (v > +1.0)
            return 0.0;
        else
            return acos(v);
    }

    /*
     * https://math.stackexchange.com/questions/1098487/atan2-faster-approximation/1105038#answer-1105038
     */
    /**
     * The fast-math atan2: a polynomial within 2.1e-4 rad of the exact value. Zeros (with their signs), infinities
     * and NaN go to the accurate path. Before 2.5.0 they gave NaN (for example {@code atan2(0, 0)}) or a lost sign
     * ({@code atan2(-0, -1)} gave +pi).
     */
    static double fastAtan2Guarded(double y, double x) {
        if (y == 0 || x == 0 || !(java.lang.Math.abs(x) < Double.POSITIVE_INFINITY) || !(java.lang.Math.abs(y) < Double.POSITIVE_INFINITY))
            return AccurateMath.atan2(y, x);
        return fastAtan2(y, x);
    }

    private static double fastAtan2(double y, double x) {
        double ax = x >= 0.0 ? x : -x, ay = y >= 0.0 ? y : -y;
        double a = ay > ax ? ax / ay : ay / ax;
        double s = a * a;
        double r = fma(fma(fma(-0.0464964749, s, 0.15931422), s, -0.327622764) * s, a, a);
        if (ay > ax)
            r = PI_OVER_2 - r;
        if (x < 0.0)
            r = PI - r;
        return y >= 0 ? r : -r;
    }
    private static float fastAtan2(float y, float x) {
        float ax = x >= 0.0f ? x : -x, ay = y >= 0.0f ? y : -y;
        float a = ay > ax ? ax / ay : ay / ax;
        float s = a * a;
        float r = fma(fma(fma(-0.0464964749f, s, 0.15931422f), s, -0.327622764f) * s, a, a);
        if (ay > ax)
            r = PI_OVER_2_f - r;
        if (x < 0.0f)
            r = PI_f - r;
        return y >= 0f ? r : -r;
    }

    /**
     * Returns the angle of the point {@code (x, y)}, like {@link java.lang.Math#atan2(double, double)}. Without
     * {@code -Dvectrix.fastmath} it is {@link AccurateMath#atan2(double, double)} rounded to float; with it, a
     * polynomial within 2.1e-4 rad of the exact value (zeros, infinities and NaN still take the accurate path).
     *
     * @param y the y coordinate
     * @param x the x coordinate
     * @return the angle in radians, in {@code [-pi, pi]}
     */
    public static float atan2(float y, float x) {
        if (FAST_MATH)
            return (float) fastAtan2Guarded(y, x);
        return (float) AccurateMath.atan2(y, x);
    }
    /**
     * Returns the angle of the point {@code (x, y)}, like {@link java.lang.Math#atan2(double, double)}. Without
     * {@code -Dvectrix.fastmath} it is {@link AccurateMath#atan2(double, double)}; with it, a polynomial within
     * 2.1e-4 rad of the exact value (zeros, infinities and NaN still take the accurate path).
     *
     * @param y the y coordinate
     * @param x the x coordinate
     * @return the angle in radians, in {@code [-pi, pi]}
     */
    public static double atan2(double y, double x) {
        if (FAST_MATH)
            return fastAtan2Guarded(y, x);
        return AccurateMath.atan2(y, x);
    }
    /**
     * Returns the arc tangent, computed in double by {@link AccurateMath#atan(double)} and rounded to float.
     *
     * @param r the tangent
     * @return the angle in radians, in {@code [-pi/2, pi/2]}
     */
    public static float atan(float r) {
        return (float) AccurateMath.atan(r);
    }
    /**
     * Returns the arc tangent ({@link AccurateMath#atan(double)}).
     *
     * @param r the tangent
     * @return the angle in radians, in {@code [-pi/2, pi/2]}
     */
    public static double atan(double r) {
        return AccurateMath.atan(r);
    }

    /**
     * Returns the arc sine, computed in double by {@link AccurateMath#asin(double)} and rounded to float.
     *
     * @param r the sine, in {@code [-1, 1]}
     * @return the angle in radians, in {@code [-pi/2, pi/2]}; NaN if {@code |r| > 1} or {@code r} is NaN
     */
    public static float asin(float r) {
        return (float) AccurateMath.asin(r);
    }
    /**
     * Returns the arc sine ({@link AccurateMath#asin(double)}).
     *
     * @param r the sine, in {@code [-1, 1]}
     * @return the angle in radians, in {@code [-pi/2, pi/2]}; NaN if {@code |r| > 1} or {@code r} is NaN
     */
    public static double asin(double r) {
        return AccurateMath.asin(r);
    }
    /**
     * Returns the arc sine, with the argument clamped to {@code [-1, 1]} first. NaN stays NaN.
     *
     * @param r the sine
     * @return the angle in radians, in {@code [-pi/2, pi/2]}
     */
    public static float safeAsin(float r) {
        return r <= -1.0f ? -PI_OVER_2_f : r >= 1.0f ? PI_OVER_2_f : asin(r);
    }
    /**
     * Returns the arc sine, with the argument clamped to {@code [-1, 1]} first. NaN stays NaN.
     *
     * @param r the sine
     * @return the angle in radians, in {@code [-pi/2, pi/2]}
     */
    public static double safeAsin(double r) {
        return r <= -1.0 ? -PI_OVER_2 : r >= 1.0 ? PI_OVER_2 : asin(r);
    }

    /**
     * Returns the absolute value ({@link java.lang.Math#abs(float)}).
     *
     * @param r the value
     * @return {@code |r|}; {@code +0} for {@code -0}
     */
    public static float abs(float r) {
        return java.lang.Math.abs(r);
    }
    /**
     * Returns the absolute value ({@link java.lang.Math#abs(double)}).
     *
     * @param r the value
     * @return {@code |r|}; {@code +0} for {@code -0}
     */
    public static double abs(double r) {
        return java.lang.Math.abs(r);
    }

    static boolean absEqualsOne(float r) {
        return (Float.floatToRawIntBits(r) & 0x7FFFFFFF) == 0x3F800000;
    }
    static boolean absEqualsOne(double r) {
        return (Double.doubleToRawLongBits(r) & 0x7FFFFFFFFFFFFFFFL) == 0x3FF0000000000000L;
    }

    /**
     * Returns the absolute value ({@link java.lang.Math#abs(int)}).
     *
     * @param r the value
     * @return {@code |r|}; {@link Integer#MIN_VALUE} for {@link Integer#MIN_VALUE}, which has no positive counterpart
     */
    public static int abs(int r) {
        return java.lang.Math.abs(r);
    }
    /**
     * Returns the absolute value ({@link java.lang.Math#abs(long)}).
     *
     * @param r the value
     * @return {@code |r|}; {@link Long#MIN_VALUE} for {@link Long#MIN_VALUE}, which has no positive counterpart
     */
    public static long abs(long r) {
        return java.lang.Math.abs(r);
    }

    /**
     * Returns the greater of two values.
     *
     * @param x the first value
     * @param y the second value
     * @return the greater of {@code x} and {@code y}
     */
    public static int max(int x, int y) {
        return java.lang.Math.max(x, y);
    }
    /**
     * Returns the smaller of two values.
     *
     * @param x the first value
     * @param y the second value
     * @return the smaller of {@code x} and {@code y}
     */
    public static int min(int x, int y) {
        return java.lang.Math.min(x, y);
    }

    /**
     * Returns the greater of two values.
     *
     * @param x the first value
     * @param y the second value
     * @return the greater of {@code x} and {@code y}
     */
    public static long max(long x, long y) {
        return java.lang.Math.max(x, y);
    }
    /**
     * Returns the smaller of two values.
     *
     * @param x the first value
     * @param y the second value
     * @return the smaller of {@code x} and {@code y}
     */
    public static long min(long x, long y) {
        return java.lang.Math.min(x, y);
    }

    /**
     * Returns the smaller of two values, with {@link java.lang.Math} semantics: NaN if either is NaN, and
     * {@code -0 < +0}, so the result does not depend on the argument order.
     *
     * @param a the first value
     * @param b the second value
     * @return the smaller of {@code a} and {@code b}
     */
    public static double min(double a, double b) {
        return java.lang.Math.min(a, b);
    }
    /**
     * Returns the smaller of two values, with {@link java.lang.Math} semantics: NaN if either is NaN, and
     * {@code -0 < +0}, so the result does not depend on the argument order.
     *
     * @param a the first value
     * @param b the second value
     * @return the smaller of {@code a} and {@code b}
     */
    public static float min(float a, float b) {
        return java.lang.Math.min(a, b);
    }

    /**
     * Returns the greater of two values, with {@link java.lang.Math} semantics: NaN if either is NaN, and
     * {@code -0 > +0}, so the result does not depend on the argument order.
     *
     * @param a the first value
     * @param b the second value
     * @return the greater of {@code a} and {@code b}
     */
    public static float max(float a, float b) {
        return java.lang.Math.max(a, b);
    }
    /**
     * Returns the greater of two values, with {@link java.lang.Math} semantics: NaN if either is NaN, and
     * {@code -0 > +0}, so the result does not depend on the argument order.
     *
     * @param a the first value
     * @param b the second value
     * @return the greater of {@code a} and {@code b}
     */
    public static double max(double a, double b) {
        return java.lang.Math.max(a, b);
    }

    /**
     * Clamps {@code val} to {@code [a, b]}: returns {@code max(a, min(b, val))}.
     * <p>
     * <b>Argument order: minimum, maximum, value.</b> This is JOML's order. It is the <em>opposite</em> of the JDK's
     * {@code java.lang.Math.clamp(value, min, max)} (Java 21+). Calling this method in the JDK order compiles
     * without warning but silently drops the upper bound. For example, {@code clamp(5, 0, 1)} returns 5, not 1,
     * because it clamps 1 to {@code [5, 0]}.
     *
     * @param a the lower bound
     * @param b the upper bound, at least {@code a}
     * @param val the value to clamp
     * @return {@code val} clamped to {@code [a, b]}
     */
    public static float clamp(float a, float b, float val){
        return max(a,min(b,val));
    }
    /**
     * Clamps {@code val} to {@code [a, b]}: returns {@code max(a, min(b, val))}.
     * <p>
     * <b>Argument order: minimum, maximum, value.</b> This is JOML's order. It is the <em>opposite</em> of the JDK's
     * {@code java.lang.Math.clamp(value, min, max)} (Java 21+). Calling this method in the JDK order compiles
     * without warning but silently drops the upper bound. For example, {@code clamp(5, 0, 1)} returns 5, not 1,
     * because it clamps 1 to {@code [5, 0]}.
     *
     * @param a the lower bound
     * @param b the upper bound, at least {@code a}
     * @param val the value to clamp
     * @return {@code val} clamped to {@code [a, b]}
     */
    public static double clamp(double a, double b, double val) {
        return max(a,min(b,val));
    }
    /**
     * Clamps {@code val} to {@code [a, b]}: returns {@code max(a, min(b, val))}.
     * <p>
     * <b>Argument order: minimum, maximum, value.</b> This is JOML's order. It is the <em>opposite</em> of the JDK's
     * {@code java.lang.Math.clamp(value, min, max)} (Java 21+). Calling this method in the JDK order compiles
     * without warning but silently drops the upper bound. For example, {@code clamp(5, 0, 1)} returns 5, not 1,
     * because it clamps 1 to {@code [5, 0]}.
     *
     * @param a the lower bound
     * @param b the upper bound, at least {@code a}
     * @param val the value to clamp
     * @return {@code val} clamped to {@code [a, b]}
     */
    public static int clamp(int a, int b, int val) {
        return max(a, min(b, val));
    }
    /**
     * Clamps {@code val} to {@code [a, b]}: returns {@code max(a, min(b, val))}.
     * <p>
     * <b>Argument order: minimum, maximum, value.</b> This is JOML's order. It is the <em>opposite</em> of the JDK's
     * {@code java.lang.Math.clamp(value, min, max)} (Java 21+). Calling this method in the JDK order compiles
     * without warning but silently drops the upper bound. For example, {@code clamp(5, 0, 1)} returns 5, not 1,
     * because it clamps 1 to {@code [5, 0]}.
     *
     * @param a the lower bound
     * @param b the upper bound, at least {@code a}
     * @param val the value to clamp
     * @return {@code val} clamped to {@code [a, b]}
     */
    public static long clamp(long a, long b, long val) {
        return max(a, min(b, val));
    }

    /**
     * Converts an angle from degrees to radians, computed in double and rounded to float.
     *
     * @param angles the angle in degrees
     * @return the angle in radians
     */
    public static float toRadians(float angles) {
        return (float) java.lang.Math.toRadians(angles);
    }
    /**
     * Converts an angle from degrees to radians ({@link java.lang.Math#toRadians(double)}).
     *
     * @param angles the angle in degrees
     * @return the angle in radians
     */
    public static double toRadians(double angles) {
        return java.lang.Math.toRadians(angles);
    }

    /**
     * Converts an angle from radians to degrees, computed in double and rounded to float.
     *
     * @param angles the angle in radians
     * @return the angle in degrees
     */
    public static float toDegrees(float angles) {
        return (float) java.lang.Math.toDegrees(angles);
    }
    /**
     * Converts an angle from radians to degrees ({@link java.lang.Math#toDegrees(double)}).
     *
     * @param angles the angle in radians
     * @return the angle in degrees
     */
    public static double toDegrees(double angles) {
        return java.lang.Math.toDegrees(angles);
    }

    /**
     * Returns the largest integer value not greater than {@code v} ({@link java.lang.Math#floor(double)}).
     *
     * @param v the value
     * @return {@code v} rounded toward negative infinity
     */
    public static double floor(double v) {
        return java.lang.Math.floor(v);
    }

    /**
     * Returns the largest integer value not greater than {@code v}.
     *
     * @param v the value
     * @return {@code v} rounded toward negative infinity
     */
    public static float floor(float v) {
        return (float) java.lang.Math.floor(v);
    }

    /**
     * Returns the smallest integer value not less than {@code v} ({@link java.lang.Math#ceil(double)}).
     *
     * @param v the value
     * @return {@code v} rounded toward positive infinity
     */
    public static double ceil(double v) {
        return java.lang.Math.ceil(v);
    }

    /**
     * Returns the smallest integer value not less than {@code v}.
     *
     * @param v the value
     * @return {@code v} rounded toward positive infinity
     */
    public static float ceil(float v) {
        return (float) java.lang.Math.ceil(v);
    }

    /**
     * Rounds to the nearest long, ties toward positive infinity ({@link java.lang.Math#round(double)}).
     *
     * @param v the value
     * @return the rounded value; 0 for NaN, saturated at the long range
     */
    public static long round(double v) {
        return java.lang.Math.round(v);
    }

    /**
     * Rounds to the nearest int, ties toward positive infinity ({@link java.lang.Math#round(float)}).
     *
     * @param v the value
     * @return the rounded value; 0 for NaN, saturated at the int range
     */
    public static int round(float v) {
        return java.lang.Math.round(v);
    }

    /**
     * Returns {@code e} raised to the power {@code a} ({@link AccurateMath#exp(double)}).
     *
     * @param a the exponent
     * @return {@code e^a}
     */
    public static double exp(double a) {
        return AccurateMath.exp(a);
    }

    /**
     * Returns whether a value is neither infinite nor NaN.
     *
     * @param d the value
     * @return {@code true} if {@code d} is finite
     */
    public static boolean isFinite(double d) {
        return abs(d) <= Double.MAX_VALUE;
    }

    /**
     * Returns whether a value is neither infinite nor NaN.
     *
     * @param f the value
     * @return {@code true} if {@code f} is finite
     */
    public static boolean isFinite(float f) {
        return abs(f) <= Float.MAX_VALUE;
    }

    /**
     * Returns {@code a * b + c}: rounded once ({@link java.lang.Math#fma(float, float, float)}), or, with
     * {@code -Dvectrix.useMathFma=false}, as a separate multiply and add.
     *
     * @param a the first factor
     * @param b the second factor
     * @param c the addend
     * @return {@code a * b + c}
     */
    public static float fma(float a, float b, float c) {
        if (Runtime.HAS_Math_fma)
            return java.lang.Math.fma(a, b, c);
        return a * b + c;
    }

    /**
     * Returns {@code a * b + c}: rounded once ({@link java.lang.Math#fma(double, double, double)}), or, with
     * {@code -Dvectrix.useMathFma=false}, as a separate multiply and add.
     *
     * @param a the first factor
     * @param b the second factor
     * @param c the addend
     * @return {@code a * b + c}
     */
    public static double fma(double a, double b, double c) {
        if (Runtime.HAS_Math_fma)
            return java.lang.Math.fma(a, b, c);
        return a * b + c;
    }

    /**
     * Rounds to an int with the given {@link RoundingMode}. Values outside the int range saturate and NaN gives 0.
     *
     * @param v the value
     * @param mode one of the {@link RoundingMode} constants
     * @return the rounded value
     * @throws UnsupportedOperationException if {@code mode} is not a {@link RoundingMode} constant
     */
    public static int roundUsing(float v, int mode) {
        switch (mode) {
        case RoundingMode.TRUNCATE:
            return (int) v;
        case RoundingMode.CEILING:
            return (int) java.lang.Math.ceil(v);
        case RoundingMode.FLOOR:
            return (int) java.lang.Math.floor(v);
        case RoundingMode.HALF_DOWN:
            return roundHalfDown(v);
        case RoundingMode.HALF_UP:
            return roundHalfUp(v);
        case RoundingMode.HALF_EVEN:
            return roundHalfEven(v);
        default:
            throw new UnsupportedOperationException();
        }
    }
    /**
     * Rounds to an int with the given {@link RoundingMode}. Values outside the int range saturate and NaN gives 0.
     *
     * @param v the value
     * @param mode one of the {@link RoundingMode} constants
     * @return the rounded value
     * @throws UnsupportedOperationException if {@code mode} is not a {@link RoundingMode} constant
     */
    public static int roundUsing(double v, int mode) {
        switch (mode) {
        case RoundingMode.TRUNCATE:
            return (int) v;
        case RoundingMode.CEILING:
            return (int) java.lang.Math.ceil(v);
        case RoundingMode.FLOOR:
            return (int) java.lang.Math.floor(v);
        case RoundingMode.HALF_DOWN:
            return roundHalfDown(v);
        case RoundingMode.HALF_UP:
            return roundHalfUp(v);
        case RoundingMode.HALF_EVEN:
            return roundHalfEven(v);
        default:
            throw new UnsupportedOperationException();
        }
    }
    /**
     * Rounds to a long with the given {@link RoundingMode}. Values outside the long range saturate and NaN gives 0.
     *
     * @param v the value
     * @param mode one of the {@link RoundingMode} constants
     * @return the rounded value
     * @throws UnsupportedOperationException if {@code mode} is not a {@link RoundingMode} constant
     */
    public static long roundLongUsing(double v, int mode) {
        switch (mode) {
            case RoundingMode.TRUNCATE:
                return (long) v;
            case RoundingMode.CEILING:
                return (long) java.lang.Math.ceil(v);
            case RoundingMode.FLOOR:
                return (long) java.lang.Math.floor(v);
            case RoundingMode.HALF_DOWN:
                return roundLongHalfDown(v);
            case RoundingMode.HALF_UP:
                return roundLongHalfUp(v);
            case RoundingMode.HALF_EVEN:
                return roundLongHalfEven(v);
            default:
                throw new UnsupportedOperationException();
        }
    }
    
    /**
     * {@code a + t (b − a)}, exact at both ends: {@code t = 0} gives {@code a} and {@code t = 1} gives {@code b} (the
     * single-fma form {@code fma(b − a, t, a)} used before 2.4.0 did not, e.g. {@code lerp(1e20, 1, 1)} was 0).
     *
     * @param a the value at {@code t = 0}
     * @param b the value at {@code t = 1}
     * @param t the interpolation factor, usually in {@code [0, 1]}; other values extrapolate
     * @return the interpolated value
     */
    public static float lerp(float a, float b, float t){
        return Math.fma(t, b, Math.fma(-t, a, a));
    }
    /**
     * Linear interpolation {@code a + t (b - a)}, exact at both ends; see {@link #lerp(float, float, float)}.
     *
     * @param a the value at {@code t = 0}
     * @param b the value at {@code t = 1}
     * @param t the interpolation factor, usually in {@code [0, 1]}; other values extrapolate
     * @return the interpolated value
     */
    public static double lerp(double a, double b, double t) {
        return Math.fma(t, b, Math.fma(-t, a, a));
    }

    /**
     * Bilinear interpolation of four corner values: lerps along x on both rows, then along y.
     *
     * @param q00 the value at {@code (0, 0)}
     * @param q10 the value at {@code (1, 0)}
     * @param q01 the value at {@code (0, 1)}
     * @param q11 the value at {@code (1, 1)}
     * @param tx the x interpolation factor, usually in {@code [0, 1]}
     * @param ty the y interpolation factor, usually in {@code [0, 1]}
     * @return the interpolated value
     */
    public static float biLerp(float q00, float q10, float q01, float q11, float tx, float ty) {
        float lerpX1 = lerp(q00, q10, tx);
        float lerpX2 = lerp(q01, q11, tx);
        return lerp(lerpX1, lerpX2, ty);
    }

    /**
     * Bilinear interpolation of four corner values: lerps along x on both rows, then along y.
     *
     * @param q00 the value at {@code (0, 0)}
     * @param q10 the value at {@code (1, 0)}
     * @param q01 the value at {@code (0, 1)}
     * @param q11 the value at {@code (1, 1)}
     * @param tx the x interpolation factor, usually in {@code [0, 1]}
     * @param ty the y interpolation factor, usually in {@code [0, 1]}
     * @return the interpolated value
     */
    public static double biLerp(double q00, double q10, double q01, double q11, double tx, double ty) {
        double lerpX1 = lerp(q00, q10, tx);
        double lerpX2 = lerp(q01, q11, tx);
        return lerp(lerpX1, lerpX2, ty);
    }

    /**
     * Trilinear interpolation of eight corner values: lerps along x, then y, then z. {@code qXYZ} is the value at
     * corner {@code (X, Y, Z)}.
     *
     * @param q000 the value at {@code (0, 0, 0)}
     * @param q100 the value at {@code (1, 0, 0)}
     * @param q010 the value at {@code (0, 1, 0)}
     * @param q110 the value at {@code (1, 1, 0)}
     * @param q001 the value at {@code (0, 0, 1)}
     * @param q101 the value at {@code (1, 0, 1)}
     * @param q011 the value at {@code (0, 1, 1)}
     * @param q111 the value at {@code (1, 1, 1)}
     * @param tx the x interpolation factor, usually in {@code [0, 1]}
     * @param ty the y interpolation factor, usually in {@code [0, 1]}
     * @param tz the z interpolation factor, usually in {@code [0, 1]}
     * @return the interpolated value
     */
    public static float triLerp(float q000, float q100, float q010, float q110, float q001, float q101, float q011, float q111, float tx, float ty, float tz) {
        float x00 = lerp(q000, q100, tx);
        float x10 = lerp(q010, q110, tx);
        float x01 = lerp(q001, q101, tx);
        float x11 = lerp(q011, q111, tx);
        float y0 = lerp(x00, x10, ty);
        float y1 = lerp(x01, x11, ty);
        return lerp(y0, y1, tz);
    }

    /**
     * Trilinear interpolation of eight corner values: lerps along x, then y, then z. {@code qXYZ} is the value at
     * corner {@code (X, Y, Z)}.
     *
     * @param q000 the value at {@code (0, 0, 0)}
     * @param q100 the value at {@code (1, 0, 0)}
     * @param q010 the value at {@code (0, 1, 0)}
     * @param q110 the value at {@code (1, 1, 0)}
     * @param q001 the value at {@code (0, 0, 1)}
     * @param q101 the value at {@code (1, 0, 1)}
     * @param q011 the value at {@code (0, 1, 1)}
     * @param q111 the value at {@code (1, 1, 1)}
     * @param tx the x interpolation factor, usually in {@code [0, 1]}
     * @param ty the y interpolation factor, usually in {@code [0, 1]}
     * @param tz the z interpolation factor, usually in {@code [0, 1]}
     * @return the interpolated value
     */
    public static double triLerp(double q000, double q100, double q010, double q110, double q001, double q101, double q011, double q111, double tx, double ty, double tz) {
        double x00 = lerp(q000, q100, tx);
        double x10 = lerp(q010, q110, tx);
        double x01 = lerp(q001, q101, tx);
        double x11 = lerp(q011, q111, tx);
        double y0 = lerp(x00, x10, ty);
        double y1 = lerp(x01, x11, ty);
        return lerp(y0, y1, tz);
    }

    /**
     * Rounds to the nearest int, ties to even. Values outside the int range saturate and NaN gives 0.
     *
     * @param v the value
     * @return the rounded value
     */
    public static int roundHalfEven(float v) {
        return (int) java.lang.Math.rint(v);
    }
    /**
     * Rounds to the nearest int, ties toward zero. Values outside the int range saturate and NaN gives 0.
     *
     * @param v the value
     * @return the rounded value
     */
    public static int roundHalfDown(float v) {
        return (int) halfDown(v);
    }
    /**
     * Rounds to the nearest int, ties away from zero. Values outside the int range saturate and NaN gives 0.
     *
     * @param v the value
     * @return the rounded value
     */
    public static int roundHalfUp(float v) {
        return (int) halfUp(v);
    }

    /**
     * Rounds to the nearest int, ties to even. Values outside the int range saturate and NaN gives 0.
     *
     * @param v the value
     * @return the rounded value
     */
    public static int roundHalfEven(double v) {
        return (int) java.lang.Math.rint(v);
    }
    /**
     * Rounds to the nearest int, ties toward zero. Values outside the int range saturate and NaN gives 0.
     *
     * @param v the value
     * @return the rounded value
     */
    public static int roundHalfDown(double v) {
        return (int) halfDown(v);
    }
    /**
     * Rounds to the nearest int, ties away from zero. Values outside the int range saturate and NaN gives 0.
     *
     * @param v the value
     * @return the rounded value
     */
    public static int roundHalfUp(double v) {
        return (int) halfUp(v);
    }

    /**
     * Rounds to the nearest long, ties to even. Values outside the long range saturate and NaN gives 0.
     *
     * @param v the value
     * @return the rounded value
     */
    public static long roundLongHalfEven(double v) {
        return (long) java.lang.Math.rint(v);
    }
    /**
     * Rounds to the nearest long, ties toward zero. Values outside the long range saturate and NaN gives 0.
     *
     * @param v the value
     * @return the rounded value
     */
    public static long roundLongHalfDown(double v) {
        return (long) halfDown(v);
    }
    /**
     * Rounds to the nearest long, ties away from zero. Values outside the long range saturate and NaN gives 0.
     *
     * @param v the value
     * @return the rounded value
     */
    public static long roundLongHalfUp(double v) {
        return (long) halfUp(v);
    }

    /*
     * Ties away from zero / toward zero, decided on the exact fractional part. The old v +/- 0.5 forms
     * rounded the addition itself: 0.49999999999999994 + 0.5 == 1.0, and 2^52 + 1 + 0.5 rounded up
     * to 2^52 + 2. a - floor(a) is exact for every double.
     */
    private static double halfUp(double v) {
        double a = java.lang.Math.abs(v);
        double f = java.lang.Math.floor(a);
        return java.lang.Math.copySign(a - f >= 0.5 ? f + 1.0 : f, v);
    }
    private static double halfDown(double v) {
        double a = java.lang.Math.abs(v);
        double f = java.lang.Math.floor(a);
        return java.lang.Math.copySign(a - f > 0.5 ? f + 1.0 : f, v);
    }

    /**
     * Returns a pseudo-random value in {@code [0, 1)} from {@link java.lang.Math#random()}. It is not seeded and not
     * reproducible; use {@link Random} for deterministic sequences.
     *
     * @return a pseudo-random value, at least 0 and less than 1
     */
    public static double random() {
        return java.lang.Math.random();
    }

    /**
     * Returns the sign of a value ({@link java.lang.Math#signum(double)}).
     *
     * @param v the value
     * @return -1 or 1 as {@code v} is negative or positive; {@code v} itself for zeros and NaN
     */
    public static double signum(double v) {
        return java.lang.Math.signum(v);
    }
    /**
     * Returns the sign of a value ({@link java.lang.Math#signum(float)}).
     *
     * @param v the value
     * @return -1 or 1 as {@code v} is negative or positive; {@code v} itself for zeros and NaN
     */
    public static float signum(float v) {
        return java.lang.Math.signum(v);
    }
    /**
     * Returns the sign of a value.
     *
     * @param v the value
     * @return -1, 0 or 1 as {@code v} is negative, zero or positive
     */
    public static int signum(int v) {
        return Integer.signum(v);
    }
    /**
     * Returns the sign of a value.
     *
     * @param v the value
     * @return -1, 0 or 1 as {@code v} is negative, zero or positive
     */
    public static int signum(long v) {
        return Long.signum(v);
    }

    /**
     * {@code a*a + b*b} in exact long arithmetic, for the squared lengths and distances of the int and long vectors.
     *
     * @throws ArithmeticException if the result does not fit in a long
     */
    static long sumOfSquaresExact(long a, long b) {
        return java.lang.Math.addExact(java.lang.Math.multiplyExact(a, a), java.lang.Math.multiplyExact(b, b));
    }

    /** {@code a*a + b*b + c*c}, exact; see {@link #sumOfSquaresExact(long, long)}. */
    static long sumOfSquaresExact(long a, long b, long c) {
        return java.lang.Math.addExact(sumOfSquaresExact(a, b), java.lang.Math.multiplyExact(c, c));
    }

    /** {@code a*a + b*b + c*c + d*d}, exact; see {@link #sumOfSquaresExact(long, long)}. */
    static long sumOfSquaresExact(long a, long b, long c, long d) {
        return java.lang.Math.addExact(sumOfSquaresExact(a, b, c), java.lang.Math.multiplyExact(d, d));
    }
}
