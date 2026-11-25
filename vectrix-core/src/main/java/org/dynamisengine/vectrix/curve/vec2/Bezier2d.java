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

package org.dynamisengine.vectrix.curve.vec2;

import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector2dc;
import org.dynamisengine.vectrix.curve.CubicCurveMath;

/**
 * A cubic Bézier segment over 2D points: {@code B(t) = (1-t)³·p0 + 3(1-t)²t·p1 + 3(1-t)t²·p2 + t³·p3}. The curve starts
 * at {@code p0}, ends at {@code p3} and is pulled toward {@code p1} and {@code p2}.
 * <p>
 * The parameter {@code t} runs over {@code [0, 1]} on the segment and is not clamped: values outside extrapolate the
 * cubic. The methods are static and stateless. Every {@code dest} may be one of the input points. Nothing allocates.
 * {@link Bezier2f} is the float twin.
 *
 * @since 1.0.0
 */
public final class Bezier2d {
    private Bezier2d() {}

    /**
     * Evaluates the segment at {@code t}.
     *
     * @param p0 the start point
     * @param p1 the first control point
     * @param p2 the second control point
     * @param p3 the end point
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param dest receives the result
     * @return {@code dest}
     */
    public static Vector2d evaluate(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3, double t, Vector2d dest) {
        return dest.set(CubicCurveMath.bezier(p0.x(), p1.x(), p2.x(), p3.x(), t), CubicCurveMath.bezier(p0.y(), p1.y(), p2.y(), p3.y(), t));
    }

    /**
     * Computes the first derivative with respect to {@code t} (the tangent, not normalized).
     *
     * @param p0 the start point
     * @param p1 the first control point
     * @param p2 the second control point
     * @param p3 the end point
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param dest receives the result
     * @return {@code dest}
     */
    public static Vector2d derivative(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3, double t, Vector2d dest) {
        return dest.set(CubicCurveMath.bezierDerivative(p0.x(), p1.x(), p2.x(), p3.x(), t), CubicCurveMath.bezierDerivative(p0.y(), p1.y(), p2.y(), p3.y(), t));
    }

    /**
     * Computes the second derivative with respect to {@code t}.
     *
     * @param p0 the start point
     * @param p1 the first control point
     * @param p2 the second control point
     * @param p3 the end point
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param dest receives the result
     * @return {@code dest}
     */
    public static Vector2d secondDerivative(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3, double t, Vector2d dest) {
        return dest.set(CubicCurveMath.bezierSecondDerivative(p0.x(), p1.x(), p2.x(), p3.x(), t), CubicCurveMath.bezierSecondDerivative(p0.y(), p1.y(), p2.y(), p3.y(), t));
    }

    /**
     * Evaluates the segment at {@code length} parameters, writing one point, one array per coordinate. The arrays and offsets are checked before anything is written; an output array may be {@code tValues} at the same offset.
     *
     * @param p0 the start point
     * @param p1 the first control point
     * @param p2 the second control point
     * @param p3 the end point
     * @param tValues the curve parameters
     * @param tOffset the offset of the first parameter in {@code tValues}
     * @param length the number of parameters to evaluate, at least 0
     * @param outX receives the x coordinates
     * @param outXOffset the offset of the first coordinate in {@code outX}
     * @param outY receives the y coordinates
     * @param outYOffset the offset of the first coordinate in {@code outY}
     * @throws IllegalArgumentException if {@code length} is negative or an array is null or too short for its
     *         offset; nothing is written then
     */
    public static void evaluateBatch(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3, double[] tValues, int tOffset, int length, double[] outX, int outXOffset, double[] outY, int outYOffset) {
        checkBatch(tValues, tOffset, length, outX, outXOffset, outY, outYOffset);
        for (int i = 0; i < length; i++) {
            double t = tValues[tOffset + i];
            outX[outXOffset + i] = CubicCurveMath.bezier(p0.x(), p1.x(), p2.x(), p3.x(), t);
            outY[outYOffset + i] = CubicCurveMath.bezier(p0.y(), p1.y(), p2.y(), p3.y(), t);
        }
    }

    private static void checkBatch(double[] tValues, int tOffset, int length, double[] outX, int outXOffset, double[] outY, int outYOffset) {
        if (length < 0 || !fits(tValues, tOffset, length) || !fits(outX, outXOffset, length) || !fits(outY, outYOffset, length)) {
            throw new IllegalArgumentException("tValues, outX and outY must hold " + length + " values from their offsets");
        }
    }

    private static boolean fits(double[] array, int offset, int length) {
        return array != null && offset >= 0 && offset <= array.length - length;
    }
}
