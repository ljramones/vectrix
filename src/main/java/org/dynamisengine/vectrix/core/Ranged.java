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
 * Scalar range utilities for double-precision values. All methods are static and thread-safe.
 */
public final class Ranged {
    private Ranged() {
    }

    /**
     * Maps {@code value} linearly from {@code [inMin, inMax]} to {@code [outMin, outMax]}. Values outside the input
     * range extrapolate.
     *
     * @param value the value to map
     * @param inMin the input value that maps to {@code outMin}
     * @param inMax the input value that maps to {@code outMax}, different from {@code inMin}
     * @param outMin the output at {@code inMin}
     * @param outMax the output at {@code inMax}
     * @return the mapped value
     * @throws IllegalArgumentException if {@code inMin == inMax}
     */
    public static double remap(double value, double inMin, double inMax, double outMin, double outMax) {
        return outMin + inverseLerp(inMin, inMax, value) * (outMax - outMin);
    }

    /**
     * Maps {@code value} linearly from {@code [inMin, inMax]} to {@code [outMin, outMax]}, clamping to the output
     * range: values beyond either input end give that end's output.
     *
     * @param value the value to map
     * @param inMin the input value that maps to {@code outMin}
     * @param inMax the input value that maps to {@code outMax}, different from {@code inMin}
     * @param outMin the output at {@code inMin}
     * @param outMax the output at {@code inMax}
     * @return the mapped value, between {@code outMin} and {@code outMax}
     * @throws IllegalArgumentException if {@code inMin == inMax}
     */
    public static double remapClamped(double value, double inMin, double inMax, double outMin, double outMax) {
        return outMin + saturate(inverseLerp(inMin, inMax, value)) * (outMax - outMin);
    }

    /**
     * Returns the interpolation factor {@code t} for which {@code lerp(a, b, t) == value}:
     * {@code (value - a) / (b - a)}. The result is not clamped.
     *
     * @param a the value at {@code t = 0}
     * @param b the value at {@code t = 1}, different from {@code a}
     * @param value the value to locate
     * @return the factor; 0 at {@code a}, 1 at {@code b}
     * @throws IllegalArgumentException if {@code a == b}
     */
    public static double inverseLerp(double a, double b, double value) {
        if (a == b) {
            throw new IllegalArgumentException("a == b");
        }
        return (value - a) / (b - a);
    }

    /**
     * Clamps a value to {@code [0, 1]}.
     *
     * @param value the value
     * @return {@code value} clamped to {@code [0, 1]}
     */
    public static double saturate(double value) {
        return clamp(value, 0.0, 1.0);
    }

    /**
     * Clamps a value to {@code [min, max]}. Note the argument order: value first, like
     * {@code java.lang.Math.clamp} and unlike {@link Math#clamp(double, double, double)}.
     *
     * @param value the value to clamp
     * @param min the lower bound
     * @param max the upper bound, at least {@code min}
     * @return {@code value} clamped to {@code [min, max]}
     * @throws IllegalArgumentException if {@code min > max}
     */
    public static double clamp(double value, double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        return java.lang.Math.max(min, java.lang.Math.min(max, value));
    }
}
