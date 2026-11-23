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
 * A uniform cubic B-spline over scalar values: segment {@code i} is shaped by control points {@code i .. i + 3}, so
 * {@code n} points give {@code n - 3} segments. The curve is C² continuous and does not, in general, pass through the
 * control points.
 * <p>
 * The parameter {@code t} runs over {@code [0, 1]} on the segment and is not clamped: values outside extrapolate the
 * cubic. The constructor copies the control points, so an instance is immutable and safe to share between threads,
 * like the 2D to 4D splines. Nothing allocates after construction.
 * {@link UniformBSpline1d} is the double twin.
 *
 * @since 1.0.0
 */
public final class UniformBSpline1f {
    private final float[] controlPoints;

    /**
     * Creates a spline from {@code controlPoints}, copying them: later changes to the caller's array do not alter the
     * curve.
     *
     * @param controlPoints the control points, at least 4
     * @throws IllegalArgumentException if {@code controlPoints} is null or holds fewer than 4 points
     */
    public UniformBSpline1f(float[] controlPoints) {
        if (controlPoints == null || controlPoints.length < 4) {
            throw new IllegalArgumentException("controlPoints");
        }
        this.controlPoints = controlPoints.clone();
    }

    /**
     * Returns the number of segments.
     *
     * @return the number of control points minus 3
     */
    public int segmentCount() {
        return controlPoints.length - 3;
    }

    /**
     * Evaluates the segment {@code segmentIndex} at {@code t}.
     *
     * @param segmentIndex the segment, in {@code [0, segmentCount())}
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @return the value at {@code t}
     * @throws IllegalArgumentException if {@code segmentIndex} is out of range
     */
    public float evaluate(int segmentIndex, float t) {
        int i = checkedSegment(segmentIndex);
        return CubicCurveMath.bspline(controlPoints[i], controlPoints[i + 1], controlPoints[i + 2], controlPoints[i + 3], t);
    }

    /**
     * Computes the first derivative with respect to {@code t}.
     *
     * @param segmentIndex the segment, in {@code [0, segmentCount())}
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @return the derivative at {@code t}
     * @throws IllegalArgumentException if {@code segmentIndex} is out of range
     */
    public float derivative(int segmentIndex, float t) {
        int i = checkedSegment(segmentIndex);
        return CubicCurveMath.bsplineDerivative(controlPoints[i], controlPoints[i + 1], controlPoints[i + 2], controlPoints[i + 3], t);
    }

    /**
     * Computes the second derivative with respect to {@code t}.
     *
     * @param segmentIndex the segment, in {@code [0, segmentCount())}
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @return the second derivative at {@code t}
     * @throws IllegalArgumentException if {@code segmentIndex} is out of range
     */
    public float secondDerivative(int segmentIndex, float t) {
        int i = checkedSegment(segmentIndex);
        return CubicCurveMath.bsplineSecondDerivative(controlPoints[i], controlPoints[i + 1], controlPoints[i + 2], controlPoints[i + 3], t);
    }

    /**
     * Evaluates the segment {@code segmentIndex} at {@code length} parameters, writing one value each. The arguments
     * are checked before anything is written; the output array may be {@code tValues} at the same offset.
     *
     * @param segmentIndex the segment, in {@code [0, segmentCount())}
     * @param tValues the curve parameters
     * @param tOffset the offset of the first parameter in {@code tValues}
     * @param length the number of parameters to evaluate, at least 0
     * @param outValues receives the {@code length} values
     * @param outOffset the offset of the first value in {@code outValues}
     * @throws IllegalArgumentException if {@code segmentIndex} is out of range, {@code length} is negative, or an
     *         array is null or too short for its offset; nothing is written then
     */
    public void evaluateBatch(int segmentIndex, float[] tValues, int tOffset, int length, float[] outValues, int outOffset) {
        checkedSegment(segmentIndex);
        if (length < 0 || tValues == null || outValues == null || tOffset < 0 || tOffset > tValues.length - length
                || outOffset < 0 || outOffset > outValues.length - length) {
            throw new IllegalArgumentException("tValues and outValues must hold " + length + " values from their offsets");
        }
        for (int i = 0; i < length; i++) {
            outValues[outOffset + i] = evaluate(segmentIndex, tValues[tOffset + i]);
        }
    }

    private int checkedSegment(int segmentIndex) {
        if (segmentIndex < 0 || segmentIndex >= segmentCount()) {
            throw new IllegalArgumentException("segmentIndex");
        }
        return segmentIndex;
    }
}
