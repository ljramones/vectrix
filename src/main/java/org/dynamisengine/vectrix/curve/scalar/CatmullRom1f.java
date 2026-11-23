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

package org.dynamisengine.vectrix.curve.scalar;

import org.dynamisengine.vectrix.curve.CubicCurveMath;

/**
 * A cardinal (Catmull–Rom) spline segment over scalar values: the Hermite segment from {@code p1} to {@code p2} with
 * tangents {@code (1 - tension)·(p2 - p0)/2} at {@code p1} and {@code (1 - tension)·(p3 - p1)/2} at {@code p2}. Tension
 * 0 gives the uniform Catmull–Rom spline, which passes through every point; tension 1 gives zero tangents.
 * <p>
 * The parameter {@code t} runs over {@code [0, 1]} on the segment and is not clamped: values outside extrapolate the
 * cubic. The methods are static and stateless. Nothing allocates. {@link CatmullRom1d} is the double twin.
 *
 * @since 1.0.0
 */
public final class CatmullRom1f {
    private CatmullRom1f() {}

    /**
     * Evaluates the segment at {@code t}.
     *
     * @param p0 the value before the segment
     * @param p1 the start value of the segment
     * @param p2 the end value of the segment
     * @param p3 the value after the segment
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param tension the cardinal tension: 0 for Catmull–Rom, 1 for zero tangents
     * @return the value at {@code t}
     */
    public static float evaluate(float p0, float p1, float p2, float p3, float t, float tension) {
        float m1 = CubicCurveMath.cardinalTangent(p0, p2, tension);
        float m2 = CubicCurveMath.cardinalTangent(p1, p3, tension);
        return CubicCurveMath.hermite(p1, m1, p2, m2, t);
    }

    /**
     * Computes the first derivative with respect to {@code t}.
     *
     * @param p0 the value before the segment
     * @param p1 the start value of the segment
     * @param p2 the end value of the segment
     * @param p3 the value after the segment
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param tension the cardinal tension: 0 for Catmull–Rom, 1 for zero tangents
     * @return the derivative at {@code t}
     */
    public static float derivative(float p0, float p1, float p2, float p3, float t, float tension) {
        float m1 = CubicCurveMath.cardinalTangent(p0, p2, tension);
        float m2 = CubicCurveMath.cardinalTangent(p1, p3, tension);
        return CubicCurveMath.hermiteDerivative(p1, m1, p2, m2, t);
    }

    /**
     * Computes the second derivative with respect to {@code t}.
     *
     * @param p0 the value before the segment
     * @param p1 the start value of the segment
     * @param p2 the end value of the segment
     * @param p3 the value after the segment
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param tension the cardinal tension: 0 for Catmull–Rom, 1 for zero tangents
     * @return the second derivative at {@code t}
     */
    public static float secondDerivative(float p0, float p1, float p2, float p3, float t, float tension) {
        float m1 = CubicCurveMath.cardinalTangent(p0, p2, tension);
        float m2 = CubicCurveMath.cardinalTangent(p1, p3, tension);
        return CubicCurveMath.hermiteSecondDerivative(p1, m1, p2, m2, t);
    }

    /**
     * Evaluates the segment at {@code length} parameters, writing one value. The arrays and offsets are checked before anything is written; an
     * output array may be {@code tValues} at the same offset.
     *
     * @param p0 the value before the segment
     * @param p1 the start value of the segment
     * @param p2 the end value of the segment
     * @param p3 the value after the segment
     * @param tension the cardinal tension: 0 for Catmull–Rom, 1 for zero tangents
     * @param tValues the curve parameters
     * @param tOffset the offset of the first parameter in {@code tValues}
     * @param length the number of parameters to evaluate, at least 0
     * @param outValues receives the {@code length} values
     * @param outOffset the offset of the first value in {@code outValues}
     * @throws IllegalArgumentException if {@code length} is negative or an array is null or too short for its
     *         offset; nothing is written then
     */
    public static void evaluateBatch(float p0, float p1, float p2, float p3, float tension, float[] tValues, int tOffset, int length, float[] outValues, int outOffset) {
        checkBatch(tValues, tOffset, length, outValues, outOffset);
        for (int i = 0; i < length; i++) {
            outValues[outOffset + i] = evaluate(p0, p1, p2, p3, tValues[tOffset + i], tension);
        }
    }

    private static void checkBatch(float[] tValues, int tOffset, int length, float[] outValues, int outOffset) {
        if (length < 0 || !fits(tValues, tOffset, length) || !fits(outValues, outOffset, length)) {
            throw new IllegalArgumentException("tValues and outValues must hold " + length + " values from their offsets");
        }
    }

    private static boolean fits(float[] array, int offset, int length) {
        return array != null && offset >= 0 && offset <= array.length - length;
    }
}
