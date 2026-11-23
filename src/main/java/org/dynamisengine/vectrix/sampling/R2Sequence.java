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
package org.dynamisengine.vectrix.sampling;

import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector2f;

/**
 * The R2 low-discrepancy sequence of Roberts (2018): point {@code n} is {@code frac(0.5 + n (1/g, 1/g²))} with
 * {@code g} the plastic number, the real root of {@code x³ = x + 1}. It is open-ended (any prefix is well
 * distributed), needs no tables, and is a good 2D blue-noise-like pattern for dithering and sample offsets.
 *
 * @since 2.1.0
 */
public final class R2Sequence {
    private static final double G = 1.32471795724474602596;
    private static final double A1 = 1.0 / G;
    private static final double A2 = 1.0 / (G * G);

    private R2Sequence() {
    }

    /**
     * Writes point {@code index} of the sequence into {@code dest}, computed in double precision. The fraction keeps
     * about {@code 53 - log2(index)} bits, so resolution drops slowly for very large indices.
     *
     * @param index the point index; any value, normally non-negative
     * @param dest receives the point, in {@code [0, 1)²}
     * @return {@code dest}
     */
    public static Vector2d r2(long index, Vector2d dest) {
        return dest.set(frac(0.5 + index * A1), frac(0.5 + index * A2));
    }

    /**
     * Writes point {@code index} of the sequence into {@code dest} in single precision. The point is computed in double
     * and rounded to float; a value that would round to 1 is clamped to the largest float below 1.
     *
     * @param index the point index; any value, normally non-negative
     * @param dest receives the point, in {@code [0, 1)²}
     * @return {@code dest}
     */
    public static Vector2f r2(long index, Vector2f dest) {
        return dest.set(toUnitFloat(frac(0.5 + index * A1)), toUnitFloat(frac(0.5 + index * A2)));
    }

    /**
     * Returns one coordinate of point {@code index}, computed in double precision.
     *
     * @param index the point index; any value, normally non-negative
     * @param dimension 0 for x or 1 for y
     * @return the coordinate, in {@code [0, 1)}
     * @throws IllegalArgumentException if {@code dimension} is not 0 or 1
     */
    public static double r2(long index, int dimension) {
        if (dimension != 0 && dimension != 1) {
            throw new IllegalArgumentException("dimension must be 0 or 1");
        }
        return frac(0.5 + index * (dimension == 0 ? A1 : A2));
    }

    private static double frac(double x) {
        return x - java.lang.Math.floor(x);
    }

    private static float toUnitFloat(double x) {
        float f = (float) x;
        return f < 1.0f ? f : java.lang.Math.nextDown(1.0f);
    }
}
