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
package org.vectrix.core;

/**
 * Float epsilon policy helpers for geometric and rendering computations.
 */
public final class Epsilonf {
    public static final String PROP_ABSOLUTE = "vectrix.epsilon.float.abs";
    public static final String PROP_RELATIVE = "vectrix.epsilon.float.rel";

    public static final float ABSOLUTE = parse(System.getProperty(PROP_ABSOLUTE), 1E-6f);
    public static final float RELATIVE = parse(System.getProperty(PROP_RELATIVE), 1E-5f);

    private Epsilonf() {
    }

    public static boolean isZero(float value) {
        return java.lang.Math.abs(value) <= ABSOLUTE;
    }

    public static boolean equals(float a, float b) {
        float diff = java.lang.Math.abs(a - b);
        float scale = java.lang.Math.max(java.lang.Math.max(java.lang.Math.abs(a), java.lang.Math.abs(b)), 1.0f);
        return diff <= ABSOLUTE + RELATIVE * scale;
    }

    public static boolean lessOrEqual(float a, float b) {
        return a < b || equals(a, b);
    }

    public static boolean greaterOrEqual(float a, float b) {
        return a > b || equals(a, b);
    }

    private static float parse(String value, float defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
