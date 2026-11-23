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
package org.dynamisengine.vectrix.sampling;

import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;

/**
 * Halton low-discrepancy sequence: coordinate {@code k} of point {@code index} is the radical inverse of
 * {@code index} in base {@code b_k}, with bases 2, 3 and 5 for the 2D and 3D points. All functions are pure and
 * stateless, and no method allocates. Values are in {@code [0, 1)}; the float variants are clamped to the largest
 * float below 1. Point 0 is the origin.
 *
 * @since 1.0.0
 */
public final class HaltonSequence {
    private HaltonSequence() {
    }

    /**
     * Returns the radical inverse of {@code index} in {@code base}, in single precision.
     *
     * @param index the sample index, non-negative
     * @param base the base, at least 2; use distinct primes for different dimensions
     * @return the value, in {@code [0, 1)}
     * @throws IllegalArgumentException if {@code index} is negative or {@code base} is less than 2
     */
    public static float halton(int index, int base) {
        return toUnitFloat(haltonDouble(index, base));
    }

    /**
     * Returns the radical inverse of {@code index} in {@code base}: the base-{@code base} digits of {@code index}
     * mirrored about the radix point. Computed in double precision.
     *
     * @param index the sample index, non-negative
     * @param base the base, at least 2; use distinct primes for different dimensions
     * @return the value, in {@code [0, 1)}
     * @throws IllegalArgumentException if {@code index} is negative or {@code base} is less than 2
     */
    public static double haltonDouble(int index, int base) {
        if (index < 0) {
            throw new IllegalArgumentException("index");
        }
        if (base < 2) {
            throw new IllegalArgumentException("base");
        }
        double f = 1.0;
        double r = 0.0;
        int i = index;
        while (i > 0) {
            f /= base;
            r += f * (i % base);
            i /= base;
        }
        return r;
    }

    /**
     * Writes the 2D Halton point at {@code index} (bases 2 and 3) into {@code dest}, in single precision.
     *
     * @param index the sample index, non-negative
     * @param dest receives the point, in {@code [0, 1)²}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector2f halton2D(int index, Vector2f dest) {
        return dest.set(halton(index, 2), halton(index, 3));
    }

    /**
     * Writes the 2D Halton point at {@code index} (bases 2 and 3) into {@code dest}, in double precision.
     *
     * @param index the sample index, non-negative
     * @param dest receives the point, in {@code [0, 1)²}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector2d halton2D(int index, Vector2d dest) {
        return dest.set(haltonDouble(index, 2), haltonDouble(index, 3));
    }

    /**
     * Writes the 3D Halton point at {@code index} (bases 2, 3 and 5) into {@code dest}, in single precision.
     *
     * @param index the sample index, non-negative
     * @param dest receives the point, in {@code [0, 1)³}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector3f halton3D(int index, Vector3f dest) {
        return dest.set(halton(index, 2), halton(index, 3), halton(index, 5));
    }

    /**
     * Writes the 3D Halton point at {@code index} (bases 2, 3 and 5) into {@code dest}, in double precision.
     *
     * @param index the sample index, non-negative
     * @param dest receives the point, in {@code [0, 1)³}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector3d halton3D(int index, Vector3d dest) {
        return dest.set(haltonDouble(index, 2), haltonDouble(index, 3), haltonDouble(index, 5));
    }

    /**
     * Writes the 2D Halton points (bases 2 and 3) {@code startIndex} to {@code startIndex + count - 1} into two float
     * arrays: point {@code i} goes to {@code outX[outXOffset + i]} and {@code outY[outYOffset + i]}. The arguments are
     * checked before any element is written.
     *
     * @param startIndex the index of the first point, non-negative
     * @param count the number of points, non-negative
     * @param outX receives the x coordinates
     * @param outXOffset the first index written in {@code outX}
     * @param outY receives the y coordinates
     * @param outYOffset the first index written in {@code outY}
     * @throws IllegalArgumentException if {@code startIndex} or {@code count} is negative, the last index
     *         {@code startIndex + count - 1} exceeds {@code Integer.MAX_VALUE}, or an output range is outside its array
     */
    public static void haltonBatch2D(int startIndex, int count, float[] outX, int outXOffset, float[] outY, int outYOffset) {
        checkBatch(startIndex, count, outX.length, outXOffset, outY.length, outYOffset);
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            outX[outXOffset + i] = halton(idx, 2);
            outY[outYOffset + i] = halton(idx, 3);
        }
    }

    /**
     * Writes the 2D Halton points (bases 2 and 3) {@code startIndex} to {@code startIndex + count - 1} into two double
     * arrays: point {@code i} goes to {@code outX[outXOffset + i]} and {@code outY[outYOffset + i]}. The arguments are
     * checked before any element is written.
     *
     * @param startIndex the index of the first point, non-negative
     * @param count the number of points, non-negative
     * @param outX receives the x coordinates
     * @param outXOffset the first index written in {@code outX}
     * @param outY receives the y coordinates
     * @param outYOffset the first index written in {@code outY}
     * @throws IllegalArgumentException if {@code startIndex} or {@code count} is negative, the last index
     *         {@code startIndex + count - 1} exceeds {@code Integer.MAX_VALUE}, or an output range is outside its array
     */
    public static void haltonBatch2D(int startIndex, int count, double[] outX, int outXOffset, double[] outY, int outYOffset) {
        checkBatch(startIndex, count, outX.length, outXOffset, outY.length, outYOffset);
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            outX[outXOffset + i] = haltonDouble(idx, 2);
            outY[outYOffset + i] = haltonDouble(idx, 3);
        }
    }

    private static void checkBatch(int startIndex, int count, int xLen, int outXOffset, int yLen, int outYOffset) {
        if (startIndex < 0) {
            throw new IllegalArgumentException("startIndex");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count");
        }
        // long arithmetic: int sums of large values would wrap and pass the checks
        if ((long) startIndex + count - 1 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("startIndex + count exceeds the int index range");
        }
        if (outXOffset < 0 || outYOffset < 0 || (long) outXOffset + count > xLen || (long) outYOffset + count > yLen) {
            throw new IllegalArgumentException("output bounds");
        }
    }

    /**
     * Narrow a [0, 1) double to float without leaving [0, 1): rounding to float sends values within half an ulp
     * of 1 up to exactly 1.0f.
     */
    private static float toUnitFloat(double v) {
        return java.lang.Math.min((float) v, ONE_BELOW_F);
    }

    private static final float ONE_BELOW_F = java.lang.Math.nextDown(1.0f);
}
