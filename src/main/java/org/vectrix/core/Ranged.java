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
 * Scalar range utilities for double-precision values.
 */
public final class Ranged {
    private Ranged() {
    }

    public static double remap(double value, double inMin, double inMax, double outMin, double outMax) {
        return outMin + inverseLerp(inMin, inMax, value) * (outMax - outMin);
    }

    public static double remapClamped(double value, double inMin, double inMax, double outMin, double outMax) {
        return outMin + saturate(inverseLerp(inMin, inMax, value)) * (outMax - outMin);
    }

    public static double inverseLerp(double a, double b, double value) {
        if (a == b) {
            throw new IllegalArgumentException("a == b");
        }
        return (value - a) / (b - a);
    }

    public static double saturate(double value) {
        return clamp(value, 0.0, 1.0);
    }

    public static double clamp(double value, double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        return java.lang.Math.max(min, java.lang.Math.min(max, value));
    }
}
