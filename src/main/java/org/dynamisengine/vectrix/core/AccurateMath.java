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
 * Accurate transcendental functions: never the {@code -Djoml.fastmath} approximations, and the single place
 * where Vectrix chooses between {@link java.lang.Math} and {@link StrictMath}.
 * <p>
 * By default these delegate to {@code java.lang.Math}, whose results may differ in the last bit between CPU
 * architectures and JIT tiers (HotSpot intrinsics; about 6.9 % of double {@code sin}/{@code cos} inputs differ from
 * {@code StrictMath} on arm64). With {@code -Dvectrix.strictMath=true} they delegate to {@code StrictMath}
 * (fdlibm), which is bitwise reproducible on every platform; {@link Math}'s transcendental functions then use them
 * too, ignoring {@code -Djoml.fastmath}. See {@code docs/determinism.md}.
 * <p>
 * Every transcendental call in the library goes through this class or through {@link Math}.
 */
public final class AccurateMath {

    /** Whether {@code -Dvectrix.strictMath=true} selected {@link StrictMath}. */
    public static final boolean STRICT = Options.STRICT_MATH;

    private AccurateMath() {
    }

    public static double sin(double a) {
        return STRICT ? StrictMath.sin(a) : java.lang.Math.sin(a);
    }

    public static double cos(double a) {
        return STRICT ? StrictMath.cos(a) : java.lang.Math.cos(a);
    }

    public static double tan(double a) {
        return STRICT ? StrictMath.tan(a) : java.lang.Math.tan(a);
    }

    public static double asin(double a) {
        return STRICT ? StrictMath.asin(a) : java.lang.Math.asin(a);
    }

    public static double acos(double a) {
        return STRICT ? StrictMath.acos(a) : java.lang.Math.acos(a);
    }

    public static double atan(double a) {
        return STRICT ? StrictMath.atan(a) : java.lang.Math.atan(a);
    }

    public static double atan2(double y, double x) {
        return STRICT ? StrictMath.atan2(y, x) : java.lang.Math.atan2(y, x);
    }

    public static double exp(double a) {
        return STRICT ? StrictMath.exp(a) : java.lang.Math.exp(a);
    }

    public static double log(double a) {
        return STRICT ? StrictMath.log(a) : java.lang.Math.log(a);
    }

    public static double pow(double a, double b) {
        return STRICT ? StrictMath.pow(a, b) : java.lang.Math.pow(a, b);
    }

    /** Cube root, sign-preserving ({@code cbrt(-8) = -2}). */
    public static double cbrt(double a) {
        return STRICT ? StrictMath.cbrt(a) : java.lang.Math.cbrt(a);
    }
}
