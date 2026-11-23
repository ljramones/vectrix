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
 * Double epsilon policy helpers for geometric and rendering computations.
 */
public final class Epsilond {
    /** The system property that sets {@link #ABSOLUTE}. */
    public static final String PROP_ABSOLUTE = "vectrix.epsilon.double.abs";
    /** The system property that sets {@link #RELATIVE}. */
    public static final String PROP_RELATIVE = "vectrix.epsilon.double.rel";

    /**
     * The absolute tolerance: {@code 1e-12} unless {@value #PROP_ABSOLUTE} sets another value when the class loads.
     */
    public static final double ABSOLUTE = parse(System.getProperty(PROP_ABSOLUTE), 1E-12);
    /**
     * The relative tolerance: {@code 1e-10} unless {@value #PROP_RELATIVE} sets another value when the class loads.
     */
    public static final double RELATIVE = parse(System.getProperty(PROP_RELATIVE), 1E-10);

    private Epsilond() {
    }

    /**
     * Returns whether a value is within {@link #ABSOLUTE} of zero.
     *
     * @param value the value
     * @return {@code true} if {@code |value| <= ABSOLUTE}; {@code false} for NaN
     */
    public static boolean isZero(double value) {
        return java.lang.Math.abs(value) <= ABSOLUTE;
    }

    /**
     * Returns whether two values are equal within the tolerance
     * {@code |a - b| <= ABSOLUTE + RELATIVE * max(|a|, |b|, 1)}. Infinities are equal only to themselves, and NaN
     * is equal to nothing.
     *
     * @param a the first value
     * @param b the second value
     * @return {@code true} if {@code a} and {@code b} are equal within the tolerance
     */
    public static boolean equals(double a, double b) {
        if (a == b) {
            return true; // exact equality, including equal infinities (Inf - Inf is NaN)
        }
        if (Double.isInfinite(a) || Double.isInfinite(b)) {
            return false; // the tolerance below is itself infinite when either value is, which made Inf equal 5
        }
        double diff = java.lang.Math.abs(a - b);
        double scale = java.lang.Math.max(java.lang.Math.max(java.lang.Math.abs(a), java.lang.Math.abs(b)), 1.0);
        return diff <= ABSOLUTE + RELATIVE * scale;
    }

    /**
     * Returns whether {@code a < b} or the two are {@linkplain #equals(double, double) equal within the tolerance}.
     *
     * @param a the first value
     * @param b the second value
     * @return {@code true} if {@code a} is less than or about equal to {@code b}
     */
    public static boolean lessOrEqual(double a, double b) {
        return a < b || equals(a, b);
    }

    /**
     * Returns whether {@code a > b} or the two are {@linkplain #equals(double, double) equal within the tolerance}.
     *
     * @param a the first value
     * @param b the second value
     * @return {@code true} if {@code a} is greater than or about equal to {@code b}
     */
    public static boolean greaterOrEqual(double a, double b) {
        return a > b || equals(a, b);
    }

    private static double parse(String value, double defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
