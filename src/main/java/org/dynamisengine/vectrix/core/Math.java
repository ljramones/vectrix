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
 * Contains fast approximations of some {@link java.lang.Math} operations.
 * <p>
 * By default, {@link java.lang.Math} methods will be used by all other JOML classes. In order to use the approximations in this class, start the JVM with the parameter <code>-Djoml.fastmath</code>.
 * <p>
 * There are two algorithms for approximating sin/cos:
 * <ol>
 * <li>arithmetic <a href="http://www.java-gaming.org/topics/joml-1-8-0-release/37491/msg/361815/view.html#msg361815">polynomial approximation</a> contributed by roquendm 
 * <li>theagentd's <a href="http://www.java-gaming.org/topics/extremely-fast-sine-cosine/36469/msg/346213/view.html#msg346213">linear interpolation</a> variant of Riven's algorithm from
 * <a href="http://www.java-gaming.org/topics/extremely-fast-sine-cosine/36469/view.html">http://www.java-gaming.org/</a>
 * </ol>
 * By default, the first algorithm is being used. In order to use the second one, start the JVM with <code>-Djoml.sinLookup</code>. The lookup table bit length of the second algorithm can also be adjusted
 * for improved accuracy via <code>-Djoml.sinLookup.bits=&lt;n&gt;</code>, where &lt;n&gt; is the number of bits of the lookup table.
 * 
 * @author Kai Burjack
 */
public class Math {

    /*
     * The following implementation of an approximation of sine and cosine was
     * thankfully donated by Riven from http://java-gaming.org/.
     * 
     * The code for linear interpolation was gratefully donated by theagentd
     * from the same site.
     */
    public static final double PI = java.lang.Math.PI;
    public static final double PI_TIMES_2 = PI * 2.0;
    public static final float PI_f = (float) java.lang.Math.PI;
    public static final float PI_TIMES_2_f = PI_f * 2.0f;
    public static final double PI_OVER_2 = PI * 0.5;
    public static final float PI_OVER_2_f = (float) (PI * 0.5);
    public static final double PI_OVER_4 = PI * 0.25;
    public static final float PI_OVER_4_f = (float) (PI * 0.25);
    public static final double ONE_OVER_PI = 1.0 / PI;
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

    public static float sin(float rad) {
        if (FAST_MATH) {
            if (Options.SIN_LOOKUP)
                return lookupSin(rad);
            return (float) fastSin(rad);
        }
        return (float) AccurateMath.sin(rad);
    }
    public static double sin(double rad) {
        if (FAST_MATH) {
            if (Options.SIN_LOOKUP)
                return lookupSin((float) rad);
            return fastSin(rad);
        }
        return AccurateMath.sin(rad);
    }

    public static float cos(float rad) {
        if (FAST_MATH)
            return Options.SIN_LOOKUP ? lookupCos(rad) : (float) fastCos(rad);
        return (float) AccurateMath.cos(rad);
    }
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
     */
    public static float cosFromSin(float sin, float angle) {
        if (FAST_MATH)
            return cos(angle);
        if (java.lang.Math.abs(sin) > 0.70710677f || !(java.lang.Math.abs(angle) < 1e9f))
            return (float) AccurateMath.cos(angle);
        float cos = (float) java.lang.Math.sqrt(1.0 - (double) sin * sin);
        return cosIsNegative(angle) ? -cos : cos;
    }
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

    public static float sqrt(float r) {
        return (float) java.lang.Math.sqrt(r);
    }
    public static double sqrt(double r) {
        return java.lang.Math.sqrt(r);
    }

    /** {@code 1 / sqrt(r)}, computed in double and rounded once (before 2.5.0 two float roundings, up to 1.47 ulp). */
    public static float invsqrt(float r) {
        return (float) (1.0 / java.lang.Math.sqrt(r));
    }
    /**
     * {@code 1 / sqrt(r)}, refined by one Newton step on the exactly evaluated residual {@code 1 - r y²}, which makes
     * it correctly rounded in nearly all cases. The plain quotient rounds twice and errs by up to 1.45 ulp. Outside
     * {@code [2^-1000, 2^1000]}, and for 0, infinities and NaN, the plain quotient is returned.
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

    public static float tan(float r) {
        return (float) AccurateMath.tan(r);
    }
    public static double tan(double r) {
        return AccurateMath.tan(r);
    }

    public static float acos(float r) {
        return (float) AccurateMath.acos(r);
    }
    public static double acos(double r) {
        return AccurateMath.acos(r);
    }

    public static float safeAcos(float v) {
        if (v < -1.0f)
            return Math.PI_f;
        else if (v > +1.0f)
            return 0.0f;
        else
            return acos(v);
    }
    public static double safeAcos(double v) {
        if (v < -1.0)
            return Math.PI;
        else if (v > +1.0)
            return 0.0;
        else
            return acos(v);
    }

    /**
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

    public static float atan2(float y, float x) {
        if (FAST_MATH)
            return (float) fastAtan2Guarded(y, x);
        return (float) AccurateMath.atan2(y, x);
    }
    public static double atan2(double y, double x) {
        if (FAST_MATH)
            return fastAtan2Guarded(y, x);
        return AccurateMath.atan2(y, x);
    }
    public static float atan(float r) {
        return (float) AccurateMath.atan(r);
    }
    public static double atan(double r) {
        return AccurateMath.atan(r);
    }

    public static float asin(float r) {
        return (float) AccurateMath.asin(r);
    }
    public static double asin(double r) {
        return AccurateMath.asin(r);
    }
    public static float safeAsin(float r) {
        return r <= -1.0f ? -PI_OVER_2_f : r >= 1.0f ? PI_OVER_2_f : asin(r);
    }
    public static double safeAsin(double r) {
        return r <= -1.0 ? -PI_OVER_2 : r >= 1.0 ? PI_OVER_2 : asin(r);
    }

    public static float abs(float r) {
        return java.lang.Math.abs(r);
    }
    public static double abs(double r) {
        return java.lang.Math.abs(r);
    }

    static boolean absEqualsOne(float r) {
        return (Float.floatToRawIntBits(r) & 0x7FFFFFFF) == 0x3F800000;
    }
    static boolean absEqualsOne(double r) {
        return (Double.doubleToRawLongBits(r) & 0x7FFFFFFFFFFFFFFFL) == 0x3FF0000000000000L;
    }

    public static int abs(int r) {
        return java.lang.Math.abs(r);
    }
    public static long abs(long r) {
        return java.lang.Math.abs(r);
    }

    public static int max(int x, int y) {
        return java.lang.Math.max(x, y);
    }
    public static int min(int x, int y) {
        return java.lang.Math.min(x, y);
    }

    public static long max(long x, long y) {
        return java.lang.Math.max(x, y);
    }
    public static long min(long x, long y) {
        return java.lang.Math.min(x, y);
    }

    public static double min(double a, double b) {
        return a < b ? a : b;
    }
    public static float min(float a, float b) {
        return a < b ? a : b;
    }

    public static float max(float a, float b) {
        return a > b ? a : b;
    }
    public static double max(double a, double b) {
        return a > b ? a : b;
    }

    /**
     * Clamps {@code val} to {@code [a, b]}: returns {@code max(a, min(b, val))}.
     * <p>
     * <b>Argument order: minimum, maximum, value.</b> This is JOML's order. It is the <em>opposite</em> of the JDK's
     * {@code java.lang.Math.clamp(value, min, max)} (Java 21+). Calling this method in the JDK order compiles
     * without warning but silently drops the upper bound. For example, {@code clamp(5, 0, 1)} returns 5, not 1,
     * because it clamps 1 to {@code [5, 0]}. DynamisPhysics shipped exactly this mistake at 16 call sites.
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
     * because it clamps 1 to {@code [5, 0]}. DynamisPhysics shipped exactly this mistake at 16 call sites.
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
     * because it clamps 1 to {@code [5, 0]}. DynamisPhysics shipped exactly this mistake at 16 call sites.
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
     * because it clamps 1 to {@code [5, 0]}. DynamisPhysics shipped exactly this mistake at 16 call sites.
     *
     * @param a the lower bound
     * @param b the upper bound, at least {@code a}
     * @param val the value to clamp
     * @return {@code val} clamped to {@code [a, b]}
     */
    public static long clamp(long a, long b, long val) {
        return max(a, min(b, val));
    }

    public static float toRadians(float angles) {
        return (float) java.lang.Math.toRadians(angles);
    }
    public static double toRadians(double angles) {
        return java.lang.Math.toRadians(angles);
    }

    public static float toDegrees(float angles) {
        return (float) java.lang.Math.toDegrees(angles);
    }
    public static double toDegrees(double angles) {
        return java.lang.Math.toDegrees(angles);
    }

    public static double floor(double v) {
        return java.lang.Math.floor(v);
    }

    public static float floor(float v) {
        return (float) java.lang.Math.floor(v);
    }

    public static double ceil(double v) {
        return java.lang.Math.ceil(v);
    }

    public static float ceil(float v) {
        return (float) java.lang.Math.ceil(v);
    }

    public static long round(double v) {
        return java.lang.Math.round(v);
    }

    public static int round(float v) {
        return java.lang.Math.round(v);
    }

    public static double exp(double a) {
        return AccurateMath.exp(a);
    }

    public static boolean isFinite(double d) {
        return abs(d) <= Double.MAX_VALUE;
    }

    public static boolean isFinite(float f) {
        return abs(f) <= Float.MAX_VALUE;
    }

    public static float fma(float a, float b, float c) {
        if (Runtime.HAS_Math_fma)
            return java.lang.Math.fma(a, b, c);
        return a * b + c;
    }

    public static double fma(double a, double b, double c) {
        if (Runtime.HAS_Math_fma)
            return java.lang.Math.fma(a, b, c);
        return a * b + c;
    }

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
     */
    public static float lerp(float a, float b, float t){
        return Math.fma(t, b, Math.fma(-t, a, a));
    }
    public static double lerp(double a, double b, double t) {
        return Math.fma(t, b, Math.fma(-t, a, a));
    }

    public static float biLerp(float q00, float q10, float q01, float q11, float tx, float ty) {
        float lerpX1 = lerp(q00, q10, tx);
        float lerpX2 = lerp(q01, q11, tx);
        return lerp(lerpX1, lerpX2, ty);
    }

    public static double biLerp(double q00, double q10, double q01, double q11, double tx, double ty) {
        double lerpX1 = lerp(q00, q10, tx);
        double lerpX2 = lerp(q01, q11, tx);
        return lerp(lerpX1, lerpX2, ty);
    }

    public static float triLerp(float q000, float q100, float q010, float q110, float q001, float q101, float q011, float q111, float tx, float ty, float tz) {
        float x00 = lerp(q000, q100, tx);
        float x10 = lerp(q010, q110, tx);
        float x01 = lerp(q001, q101, tx);
        float x11 = lerp(q011, q111, tx);
        float y0 = lerp(x00, x10, ty);
        float y1 = lerp(x01, x11, ty);
        return lerp(y0, y1, tz);
    }

    public static double triLerp(double q000, double q100, double q010, double q110, double q001, double q101, double q011, double q111, double tx, double ty, double tz) {
        double x00 = lerp(q000, q100, tx);
        double x10 = lerp(q010, q110, tx);
        double x01 = lerp(q001, q101, tx);
        double x11 = lerp(q011, q111, tx);
        double y0 = lerp(x00, x10, ty);
        double y1 = lerp(x01, x11, ty);
        return lerp(y0, y1, tz);
    }

    public static int roundHalfEven(float v) {
        return (int) java.lang.Math.rint(v);
    }
    public static int roundHalfDown(float v) {
        return (int) halfDown(v);
    }
    public static int roundHalfUp(float v) {
        return (int) halfUp(v);
    }

    public static int roundHalfEven(double v) {
        return (int) java.lang.Math.rint(v);
    }
    public static int roundHalfDown(double v) {
        return (int) halfDown(v);
    }
    public static int roundHalfUp(double v) {
        return (int) halfUp(v);
    }

    public static long roundLongHalfEven(double v) {
        return (long) java.lang.Math.rint(v);
    }
    public static long roundLongHalfDown(double v) {
        return (long) halfDown(v);
    }
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

    public static double random() {
        return java.lang.Math.random();
    }

    public static double signum(double v) {
        return java.lang.Math.signum(v);
    }
    public static float signum(float v) {
        return java.lang.Math.signum(v);
    }
    public static int signum(int v) {
        return Integer.signum(v);
    }
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
