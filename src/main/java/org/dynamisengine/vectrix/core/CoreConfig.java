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
 * Global runtime configuration for math behavior used in core primitives.
 */
public final class CoreConfig {
    /** System property selecting the initial math mode ({@code FAST} or {@code STRICT}). */
    public static final String PROP_MATH_MODE = "vectrix.math.mode";

    /**
     * {@link MathMode#STRICT} tolerance for float data: a quaternion or axis is unit when
     * {@code |lengthSquared − 1| <= 1e-5}, and a quaternion is pure when {@code |w| <= 1e-5}. This is about 80 ulps
     * of 1.0, which leaves room for the rounding drift of hundreds of chained float quaternion products (about
     * 1.8e-6 after 32) while still rejecting unnormalised input.
     */
    public static final float STRICT_UNIT_TOLERANCE_F = 1e-5f;

    /** {@link MathMode#STRICT} tolerance for double data, with the same meaning as {@link #STRICT_UNIT_TOLERANCE_F}. */
    public static final double STRICT_UNIT_TOLERANCE_D = 1e-6;

    private static volatile MathMode mathMode = parseMathMode(System.getProperty(PROP_MATH_MODE));

    private CoreConfig() {
    }

    public static MathMode mathMode() {
        return mathMode;
    }

    public static void setMathMode(MathMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode");
        }
        mathMode = mode;
    }

    /**
     * Parse a math-mode property value. {@code null}, empty or unrecognised values mean {@link MathMode#FAST}.
     */
    public static MathMode parseMathMode(String value) {
        if (value == null || value.isEmpty()) {
            return MathMode.FAST;
        }
        return "STRICT".equalsIgnoreCase(value) ? MathMode.STRICT : MathMode.FAST;
    }

    /**
     * Whether a float squared length counts as unit under {@link MathMode#STRICT}. NaN is never unit.
     */
    public static boolean withinStrictUnitTolerance(float lengthSquared) {
        return java.lang.Math.abs(lengthSquared - 1.0f) <= STRICT_UNIT_TOLERANCE_F;
    }

    /**
     * Whether a double squared length counts as unit under {@link MathMode#STRICT}. NaN is never unit.
     */
    public static boolean withinStrictUnitTolerance(double lengthSquared) {
        return java.lang.Math.abs(lengthSquared - 1.0) <= STRICT_UNIT_TOLERANCE_D;
    }
}
