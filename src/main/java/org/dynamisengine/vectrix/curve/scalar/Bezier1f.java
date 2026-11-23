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
 * A cubic Bézier segment over scalar values: {@code B(t) = (1-t)³·p0 + 3(1-t)²t·p1 + 3(1-t)t²·p2 + t³·p3}. The curve
 * starts at {@code p0}, ends at {@code p3} and is pulled toward {@code p1} and {@code p2}.
 * <p>
 * The parameter {@code t} runs over {@code [0, 1]} on the segment and is not clamped: values outside extrapolate the
 * cubic. The methods are static and stateless. Nothing allocates. {@link Bezier1d} is the double twin.
 *
 * @since 1.0.0
 */
public final class Bezier1f {
    private Bezier1f() {}

    /**
     * Evaluates the segment at {@code t}.
     *
     * @param p0 the start value
     * @param p1 the first control value
     * @param p2 the second control value
     * @param p3 the end value
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @return the value at {@code t}
     */
    public static float evaluate(float p0, float p1, float p2, float p3, float t) { return CubicCurveMath.bezier(p0, p1, p2, p3, t); }

    /**
     * Computes the first derivative with respect to {@code t}.
     *
     * @param p0 the start value
     * @param p1 the first control value
     * @param p2 the second control value
     * @param p3 the end value
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @return the derivative at {@code t}
     */
    public static float derivative(float p0, float p1, float p2, float p3, float t) { return CubicCurveMath.bezierDerivative(p0, p1, p2, p3, t); }

    /**
     * Computes the second derivative with respect to {@code t}.
     *
     * @param p0 the start value
     * @param p1 the first control value
     * @param p2 the second control value
     * @param p3 the end value
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @return the second derivative at {@code t}
     */
    public static float secondDerivative(float p0, float p1, float p2, float p3, float t) { return CubicCurveMath.bezierSecondDerivative(p0, p1, p2, p3, t); }

    /**
     * Evaluates the segment at {@code length} parameters, writing one value. The arrays are not checked up front; an
     * output array may be {@code tValues} at the same offset.
     *
     * @param p0 the start value
     * @param p1 the first control value
     * @param p2 the second control value
     * @param p3 the end value
     * @param tValues the curve parameters
     * @param tOffset the offset of the first parameter in {@code tValues}
     * @param length the number of parameters to evaluate
     * @param outValues receives the {@code length} values
     * @param outOffset the offset of the first value in {@code outValues}
     */
    public static void evaluateBatch(float p0, float p1, float p2, float p3, float[] tValues, int tOffset, int length, float[] outValues, int outOffset) {
        for (int i = 0; i < length; i++) {
            outValues[outOffset + i] = evaluate(p0, p1, p2, p3, tValues[tOffset + i]);
        }
    }
}
