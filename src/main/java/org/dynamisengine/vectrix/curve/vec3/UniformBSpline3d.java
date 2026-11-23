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

package org.dynamisengine.vectrix.curve.vec3;

import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;
import org.dynamisengine.vectrix.curve.CubicCurveMath;

/**
 * A uniform cubic B-spline over 3D points: segment {@code i} is shaped by control points {@code i .. i + 3},
 * so {@code n} points give {@code n - 3} segments. The curve is C² continuous and does not, in general, pass through
 * the control points.
 * <p>
 * The parameter {@code t} runs over {@code [0, 1]} on the segment and is not clamped: values outside extrapolate the
 * cubic. The constructor copies the control points, so an instance is immutable and safe to share between threads.
 * Every {@code dest} may be one of the input points. Nothing allocates after construction. {@link UniformBSpline3f} is
 * the float twin.
 *
 * @since 1.0.0
 */
public final class UniformBSpline3d {
    private final Vector3dc[] controlPoints;

    /**
     * Creates a spline from {@code controlPoints}, copying them.
     *
     * @param controlPoints the control points, at least 4
     * @throws IllegalArgumentException if there are fewer than 4 control points
     */
    public UniformBSpline3d(Vector3dc[] controlPoints) {
        if (controlPoints.length < 4) throw new IllegalArgumentException("controlPoints");
        // Defensive copy: later changes to the caller's array or vectors do not alter the curve.
        Vector3dc[] copy = new Vector3dc[controlPoints.length];
        for (int k = 0; k < copy.length; k++) {
            copy[k] = new Vector3d(controlPoints[k]);
        }
        this.controlPoints = copy;
    }

    /**
     * Returns the number of segments.
     *
     * @return the number of control points minus 3
     */
    public int segmentCount() { return controlPoints.length - 3; }

    /**
     * Evaluates the segment {@code segmentIndex} at {@code t}.
     *
     * @param segmentIndex the segment, in {@code [0, segmentCount())}
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param dest receives the result
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code segmentIndex} is out of range
     */
    public Vector3d evaluate(int segmentIndex, double t, Vector3d dest) {
        int i = checkedSegment(segmentIndex);
        return dest.set(CubicCurveMath.bspline(controlPoints[i].x(), controlPoints[i+1].x(), controlPoints[i+2].x(), controlPoints[i+3].x(), t),
                CubicCurveMath.bspline(controlPoints[i].y(), controlPoints[i+1].y(), controlPoints[i+2].y(), controlPoints[i+3].y(), t),
                CubicCurveMath.bspline(controlPoints[i].z(), controlPoints[i+1].z(), controlPoints[i+2].z(), controlPoints[i+3].z(), t));
    }

    /**
     * Computes the first derivative with respect to {@code t} (the tangent, not normalized).
     *
     * @param segmentIndex the segment, in {@code [0, segmentCount())}
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param dest receives the result
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code segmentIndex} is out of range
     */
    public Vector3d derivative(int segmentIndex, double t, Vector3d dest) {
        int i = checkedSegment(segmentIndex);
        return dest.set(CubicCurveMath.bsplineDerivative(controlPoints[i].x(), controlPoints[i+1].x(), controlPoints[i+2].x(), controlPoints[i+3].x(), t),
                CubicCurveMath.bsplineDerivative(controlPoints[i].y(), controlPoints[i+1].y(), controlPoints[i+2].y(), controlPoints[i+3].y(), t),
                CubicCurveMath.bsplineDerivative(controlPoints[i].z(), controlPoints[i+1].z(), controlPoints[i+2].z(), controlPoints[i+3].z(), t));
    }

    /**
     * Computes the second derivative with respect to {@code t}.
     *
     * @param segmentIndex the segment, in {@code [0, segmentCount())}
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param dest receives the result
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code segmentIndex} is out of range
     */
    public Vector3d secondDerivative(int segmentIndex, double t, Vector3d dest) {
        int i = checkedSegment(segmentIndex);
        return dest.set(CubicCurveMath.bsplineSecondDerivative(controlPoints[i].x(), controlPoints[i+1].x(), controlPoints[i+2].x(), controlPoints[i+3].x(), t),
                CubicCurveMath.bsplineSecondDerivative(controlPoints[i].y(), controlPoints[i+1].y(), controlPoints[i+2].y(), controlPoints[i+3].y(), t),
                CubicCurveMath.bsplineSecondDerivative(controlPoints[i].z(), controlPoints[i+1].z(), controlPoints[i+2].z(), controlPoints[i+3].z(), t));
    }

    /**
     * Evaluates the segment {@code segmentIndex} at {@code length} parameters, writing one point, one array per
     * coordinate. The arrays and offsets are checked before anything is written; an output array may be {@code tValues} at the same offset.
     *
     * @param segmentIndex the segment, in {@code [0, segmentCount())}
     * @param tValues the curve parameters
     * @param tOffset the offset of the first parameter in {@code tValues}
     * @param length the number of parameters to evaluate, at least 0
     * @param outX receives the x coordinates
     * @param outXOffset the offset of the first coordinate in {@code outX}
     * @param outY receives the y coordinates
     * @param outYOffset the offset of the first coordinate in {@code outY}
     * @param outZ receives the z coordinates
     * @param outZOffset the offset of the first coordinate in {@code outZ}
     * @throws IllegalArgumentException if {@code segmentIndex} is out of range, {@code length} is negative, or an
     *         array is null or too short for its offset; nothing is written then
     */
    public void evaluateBatch(int segmentIndex, double[] tValues, int tOffset, int length, double[] outX, int outXOffset, double[] outY, int outYOffset, double[] outZ, int outZOffset) {
        int s = checkedSegment(segmentIndex);
        checkBatch(tValues, tOffset, length, outX, outXOffset, outY, outYOffset, outZ, outZOffset);
        double c0x = controlPoints[s].x(), c1x = controlPoints[s + 1].x(), c2x = controlPoints[s + 2].x(), c3x = controlPoints[s + 3].x();
        double c0y = controlPoints[s].y(), c1y = controlPoints[s + 1].y(), c2y = controlPoints[s + 2].y(), c3y = controlPoints[s + 3].y();
        double c0z = controlPoints[s].z(), c1z = controlPoints[s + 1].z(), c2z = controlPoints[s + 2].z(), c3z = controlPoints[s + 3].z();
        for (int i = 0; i < length; i++) {
            double t = tValues[tOffset + i];
            outX[outXOffset + i] = CubicCurveMath.bspline(c0x, c1x, c2x, c3x, t);
            outY[outYOffset + i] = CubicCurveMath.bspline(c0y, c1y, c2y, c3y, t);
            outZ[outZOffset + i] = CubicCurveMath.bspline(c0z, c1z, c2z, c3z, t);
        }
    }

    private int checkedSegment(int segmentIndex) {
        if (segmentIndex < 0 || segmentIndex >= segmentCount()) throw new IllegalArgumentException("segmentIndex");
        return segmentIndex;
    }

    private static void checkBatch(double[] tValues, int tOffset, int length, double[] outX, int outXOffset, double[] outY, int outYOffset, double[] outZ, int outZOffset) {
        if (length < 0 || !fits(tValues, tOffset, length) || !fits(outX, outXOffset, length) || !fits(outY, outYOffset, length) || !fits(outZ, outZOffset, length)) {
            throw new IllegalArgumentException("tValues, outX, outY and outZ must hold " + length + " values from their offsets");
        }
    }

    private static boolean fits(double[] array, int offset, int length) {
        return array != null && offset >= 0 && offset <= array.length - length;
    }
}
