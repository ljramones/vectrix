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

import org.dynamisengine.vectrix.core.ReproducibleMath;

/**
 * Reproducible float and double transcendentals.
 *
 * @since 2.5.0
 * @deprecated moved to {@link ReproducibleMath}, where the whole library can use them; the double functions are
 *             overloads there ({@code expD} is {@code exp(double)} and so on)
 */
@Deprecated(since = "2.6.0", forRemoval = true)
public final class NnMath {

    private NnMath() {
    }

    /**
     * Forwards to {@link ReproducibleMath#exp(float)}.
     *
     * @param x the argument
     * @return {@code e^x}
     */
    public static float exp(float x) {
        return ReproducibleMath.exp(x);
    }

    /**
     * Forwards to {@link ReproducibleMath#expm1(float)}.
     *
     * @param x the argument
     * @return {@code e^x - 1}
     */
    public static float expm1(float x) {
        return ReproducibleMath.expm1(x);
    }

    /**
     * Forwards to {@link ReproducibleMath#tanh(float)}.
     *
     * @param x the argument
     * @return the hyperbolic tangent of {@code x}
     */
    public static float tanh(float x) {
        return ReproducibleMath.tanh(x);
    }

    /**
     * Forwards to {@link ReproducibleMath#sigmoid(float)}.
     *
     * @param x the argument
     * @return {@code 1 / (1 + e^-x)}
     */
    public static float sigmoid(float x) {
        return ReproducibleMath.sigmoid(x);
    }

    /**
     * Forwards to {@link ReproducibleMath#log(float)}.
     *
     * @param x the argument
     * @return the natural logarithm of {@code x}
     */
    public static float log(float x) {
        return ReproducibleMath.log(x);
    }

    /**
     * Forwards to {@link ReproducibleMath#log1p(float)}.
     *
     * @param y the argument
     * @return {@code ln(1 + y)}
     */
    public static float log1p(float y) {
        return ReproducibleMath.log1p(y);
    }

    /**
     * Forwards to {@link ReproducibleMath#erf(float)}.
     *
     * @param x the argument
     * @return the error function of {@code x}
     */
    public static float erf(float x) {
        return ReproducibleMath.erf(x);
    }

    /**
     * Forwards to {@link ReproducibleMath#erfc(float)}.
     *
     * @param x the argument
     * @return {@code 1 - erf(x)}
     */
    public static float erfc(float x) {
        return ReproducibleMath.erfc(x);
    }

    /**
     * Forwards to {@link ReproducibleMath#normalCdf(float)}.
     *
     * @param x the argument
     * @return the standard normal cumulative distribution at {@code x}
     */
    public static float normalCdf(float x) {
        return ReproducibleMath.normalCdf(x);
    }

    /**
     * Forwards to {@link ReproducibleMath#normalPdf(float)}.
     *
     * @param x the argument
     * @return the standard normal density at {@code x}
     */
    public static float normalPdf(float x) {
        return ReproducibleMath.normalPdf(x);
    }

    /**
     * Forwards to {@link ReproducibleMath#sin(double)}.
     *
     * @param a the angle in radians
     * @return the sine of {@code a}
     */
    public static double sinD(double a) {
        return ReproducibleMath.sin(a);
    }

    /**
     * Forwards to {@link ReproducibleMath#cos(double)}.
     *
     * @param a the angle in radians
     * @return the cosine of {@code a}
     */
    public static double cosD(double a) {
        return ReproducibleMath.cos(a);
    }

    /**
     * Forwards to {@link ReproducibleMath#log(double)}.
     *
     * @param x the argument
     * @return the natural logarithm of {@code x}
     */
    public static double logD(double x) {
        return ReproducibleMath.log(x);
    }

    /**
     * Forwards to {@link ReproducibleMath#pow(double, double)}.
     *
     * @param b the base
     * @param y the exponent
     * @return {@code b^y}
     */
    public static double powD(double b, double y) {
        return ReproducibleMath.pow(b, y);
    }

    /**
     * Forwards to {@link ReproducibleMath#exp(double)}.
     *
     * @param x the argument
     * @return {@code e^x}
     */
    public static double expD(double x) {
        return ReproducibleMath.exp(x);
    }
}
