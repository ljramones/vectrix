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
package org.dynamisengine.vectrix.core;

/**
 * Accurate transcendental functions: never the {@code -Dvectrix.fastmath} approximations, and the single place
 * where Vectrix chooses between {@link java.lang.Math} and {@link StrictMath}.
 * <p>
 * By default these delegate to {@code java.lang.Math}, whose results may differ in the last bit between CPU
 * architectures and JIT tiers (HotSpot intrinsics; about 6.9 % of double {@code sin}/{@code cos} inputs differ from
 * {@code StrictMath} on arm64). With {@code -Dvectrix.strictMath=true} they delegate to {@code StrictMath}
 * (fdlibm), which is bitwise reproducible on every platform; {@link Math}'s transcendental functions then use them
 * too, ignoring {@code -Dvectrix.fastmath}. See {@code docs/determinism.md}.
 * <p>
 * Every call to a platform transcendental ({@code java.lang.Math} or {@code StrictMath}) in the library goes through
 * this class. The only other transcendental code is the fast-math approximations in {@link Math} and the series of
 * {@link ReproducibleMath}, which are reproducible in any configuration.
 * <p>
 * All methods are static, allocation-free and thread-safe.
 */
public final class AccurateMath {

    /** Whether {@code -Dvectrix.strictMath=true} selected {@link StrictMath}. */
    public static final boolean STRICT = Options.STRICT_MATH;

    private AccurateMath() {
    }

    /**
     * Returns the sine of an angle.
     *
     * @param a the angle in radians
     * @return the sine of {@code a}; NaN for NaN and infinities
     */
    public static double sin(double a) {
        return STRICT ? StrictMath.sin(a) : java.lang.Math.sin(a);
    }

    /**
     * Returns the cosine of an angle.
     *
     * @param a the angle in radians
     * @return the cosine of {@code a}; NaN for NaN and infinities
     */
    public static double cos(double a) {
        return STRICT ? StrictMath.cos(a) : java.lang.Math.cos(a);
    }

    /**
     * Returns the tangent of an angle.
     *
     * @param a the angle in radians
     * @return the tangent of {@code a}; NaN for NaN and infinities
     */
    public static double tan(double a) {
        return STRICT ? StrictMath.tan(a) : java.lang.Math.tan(a);
    }

    /**
     * Returns the arc sine.
     *
     * @param a the sine, in {@code [-1, 1]}
     * @return the angle in radians, in {@code [-pi/2, pi/2]}; NaN if {@code |a| > 1} or {@code a} is NaN
     */
    public static double asin(double a) {
        return STRICT ? StrictMath.asin(a) : java.lang.Math.asin(a);
    }

    /**
     * Returns the arc cosine.
     *
     * @param a the cosine, in {@code [-1, 1]}
     * @return the angle in radians, in {@code [0, pi]}; NaN if {@code |a| > 1} or {@code a} is NaN
     */
    public static double acos(double a) {
        return STRICT ? StrictMath.acos(a) : java.lang.Math.acos(a);
    }

    /**
     * Returns the arc tangent.
     *
     * @param a the tangent
     * @return the angle in radians, in {@code [-pi/2, pi/2]}
     */
    public static double atan(double a) {
        return STRICT ? StrictMath.atan(a) : java.lang.Math.atan(a);
    }

    /**
     * Returns the angle of the point {@code (x, y)} from the positive x axis, with the semantics of
     * {@link java.lang.Math#atan2(double, double)}.
     *
     * @param y the y coordinate
     * @param x the x coordinate
     * @return the angle in radians, in {@code [-pi, pi]}
     */
    public static double atan2(double y, double x) {
        return STRICT ? StrictMath.atan2(y, x) : java.lang.Math.atan2(y, x);
    }

    /**
     * Returns {@code e} raised to a power.
     *
     * @param a the exponent
     * @return {@code e^a}
     */
    public static double exp(double a) {
        return STRICT ? StrictMath.exp(a) : java.lang.Math.exp(a);
    }

    /**
     * Returns the natural logarithm.
     *
     * @param a the argument
     * @return the natural logarithm of {@code a}; -infinity for 0, NaN for negative values and NaN
     */
    public static double log(double a) {
        return STRICT ? StrictMath.log(a) : java.lang.Math.log(a);
    }

    /**
     * Returns {@code a} raised to the power {@code b}, with the semantics of
     * {@link java.lang.Math#pow(double, double)}.
     *
     * @param a the base
     * @param b the exponent
     * @return {@code a^b}
     */
    public static double pow(double a, double b) {
        return STRICT ? StrictMath.pow(a, b) : java.lang.Math.pow(a, b);
    }

    /**
     * {@code cos(2π·e/d)}: the cosine of {@code e/d} turns, with the angle reduced exactly in integer arithmetic to at
     * most π/4 before the cosine is taken. The result is within about one ulp for every {@code e}, and the
     * symmetries are exact: {@code cosTurns(d - e, d) == cosTurns(e, d)}, quarter and half turns give exactly 0 and
     * ±1.
     *
     * @param e the numerator, any value
     * @param d the denominator, {@code 1 <= d <= 2^60}
     * @return the cosine
     * @throws IllegalArgumentException if {@code d} is outside {@code [1, 2^60]}
     * @since 2.5.0
     */
    public static double cosTurns(long e, long d) {
        return turns(e, d, true);
    }

    /**
     * {@code sin(2π·e/d)}, reduced exactly as for {@link #cosTurns(long, long)}:
     * {@code sinTurns(d - e, d) == -sinTurns(e, d)}.
     *
     * @param e the numerator, any value
     * @param d the denominator, {@code 1 <= d <= 2^60}
     * @return the sine
     * @throws IllegalArgumentException if {@code d} is outside {@code [1, 2^60]}
     * @since 2.5.0
     */
    public static double sinTurns(long e, long d) {
        return turns(e, d, false);
    }

    private static double turns(long e, long d, boolean cosine) {
        if (d < 1 || d > (1L << 60)) {
            throw new IllegalArgumentException("denominator must be in [1, 2^60]: " + d);
        }
        long r = java.lang.Math.floorMod(e, d);
        long q = (4 * r) / d;            // quadrant of the angle 2π r/d
        long t = 4 * r - q * d;          // the angle within the quadrant is (π/2)·t/d
        double c;
        double s;
        if (2 * t == d) { // exactly an odd multiple of π/4: both are √½, correctly rounded
            c = java.lang.Math.sqrt(0.5);
            s = c;
        } else if (2 * t < d) {
            double a = 0.5 * java.lang.Math.PI * ((double) t / d);
            c = cos(a);
            s = sin(a);
        } else {
            double a = 0.5 * java.lang.Math.PI * ((double) (d - t) / d);
            c = sin(a);
            s = cos(a);
        }
        double v;
        switch ((int) q) {
            case 0: v = cosine ? c : s; break;
            case 1: v = cosine ? -s : c; break;
            case 2: v = cosine ? -c : -s; break;
            default: v = cosine ? s : -c; break;
        }
        return v + 0.0; // no negative zero
    }

    /**
     * Cube root, sign-preserving ({@code cbrt(-8) = -2}).
     *
     * @param a the argument
     * @return the cube root of {@code a}
     */
    public static double cbrt(double a) {
        return STRICT ? StrictMath.cbrt(a) : java.lang.Math.cbrt(a);
    }
}
