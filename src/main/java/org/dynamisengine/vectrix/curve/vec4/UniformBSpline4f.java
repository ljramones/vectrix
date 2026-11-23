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

package org.dynamisengine.vectrix.curve.vec4;

import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.core.Vector4fc;
import org.dynamisengine.vectrix.curve.CubicCurveMath;

/**
 * A uniform cubic B-spline over 4D vectors: segment {@code i} is shaped by control points {@code i .. i + 3},
 * so {@code n} points give {@code n - 3} segments. The curve is C² continuous and does not, in general, pass through
 * the control points.
 * <p>
 * The parameter {@code t} runs over {@code [0, 1]} on the segment and is not clamped: values outside extrapolate the
 * cubic. The constructor copies the control points, so an instance is immutable and safe to share between threads.
 * Every {@code dest} may be one of the input points. Nothing allocates after construction. {@link UniformBSpline4d} is
 * the double twin.
 *
 * @since 1.0.0
 */
public final class UniformBSpline4f {
    private final Vector4fc[] controlPoints;

    /**
     * Creates a spline from {@code controlPoints}, copying them.
     *
     * @param controlPoints the control points, at least 4
     * @throws IllegalArgumentException if there are fewer than 4 control points
     */
    public UniformBSpline4f(Vector4fc[] controlPoints) {
        if (controlPoints.length < 4) throw new IllegalArgumentException("controlPoints");
        // Defensive copy: later changes to the caller's array or vectors do not alter the curve.
        Vector4fc[] copy = new Vector4fc[controlPoints.length];
        for (int k = 0; k < copy.length; k++) {
            copy[k] = new Vector4f(controlPoints[k]);
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
    public Vector4f evaluate(int segmentIndex, float t, Vector4f dest) {
        int i = checkedSegment(segmentIndex);
        return dest.set(CubicCurveMath.bspline(controlPoints[i].x(), controlPoints[i+1].x(), controlPoints[i+2].x(), controlPoints[i+3].x(), t), CubicCurveMath.bspline(controlPoints[i].y(), controlPoints[i+1].y(), controlPoints[i+2].y(), controlPoints[i+3].y(), t), CubicCurveMath.bspline(controlPoints[i].z(), controlPoints[i+1].z(), controlPoints[i+2].z(), controlPoints[i+3].z(), t), CubicCurveMath.bspline(controlPoints[i].w(), controlPoints[i+1].w(), controlPoints[i+2].w(), controlPoints[i+3].w(), t));
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
    public Vector4f derivative(int segmentIndex, float t, Vector4f dest) {
        int i = checkedSegment(segmentIndex);
        return dest.set(CubicCurveMath.bsplineDerivative(controlPoints[i].x(), controlPoints[i+1].x(), controlPoints[i+2].x(), controlPoints[i+3].x(), t), CubicCurveMath.bsplineDerivative(controlPoints[i].y(), controlPoints[i+1].y(), controlPoints[i+2].y(), controlPoints[i+3].y(), t), CubicCurveMath.bsplineDerivative(controlPoints[i].z(), controlPoints[i+1].z(), controlPoints[i+2].z(), controlPoints[i+3].z(), t), CubicCurveMath.bsplineDerivative(controlPoints[i].w(), controlPoints[i+1].w(), controlPoints[i+2].w(), controlPoints[i+3].w(), t));
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
    public Vector4f secondDerivative(int segmentIndex, float t, Vector4f dest) {
        int i = checkedSegment(segmentIndex);
        return dest.set(CubicCurveMath.bsplineSecondDerivative(controlPoints[i].x(), controlPoints[i+1].x(), controlPoints[i+2].x(), controlPoints[i+3].x(), t), CubicCurveMath.bsplineSecondDerivative(controlPoints[i].y(), controlPoints[i+1].y(), controlPoints[i+2].y(), controlPoints[i+3].y(), t), CubicCurveMath.bsplineSecondDerivative(controlPoints[i].z(), controlPoints[i+1].z(), controlPoints[i+2].z(), controlPoints[i+3].z(), t), CubicCurveMath.bsplineSecondDerivative(controlPoints[i].w(), controlPoints[i+1].w(), controlPoints[i+2].w(), controlPoints[i+3].w(), t));
    }

    /**
     * Evaluates the segment {@code segmentIndex} at {@code length} parameters, writing one point, one array per
     * coordinate. The arrays are not checked up front; an output array may be {@code tValues} at the same offset.
     *
     * @param segmentIndex the segment, in {@code [0, segmentCount())}
     * @param tValues the curve parameters
     * @param tOffset the offset of the first parameter in {@code tValues}
     * @param length the number of parameters to evaluate
     * @param outX receives the x coordinates
     * @param outXOffset the offset of the first coordinate in {@code outX}
     * @param outY receives the y coordinates
     * @param outYOffset the offset of the first coordinate in {@code outY}
     * @param outZ receives the z coordinates
     * @param outZOffset the offset of the first coordinate in {@code outZ}
     * @param outW receives the w coordinates
     * @param outWOffset the offset of the first coordinate in {@code outW}
     * @throws IllegalArgumentException if {@code segmentIndex} is out of range
     */
    public void evaluateBatch(int segmentIndex, float[] tValues, int tOffset, int length, float[] outX, int outXOffset, float[] outY, int outYOffset, float[] outZ, int outZOffset, float[] outW, int outWOffset) {
        int s = checkedSegment(segmentIndex);
        float c0x = controlPoints[s].x(), c1x = controlPoints[s + 1].x(), c2x = controlPoints[s + 2].x(), c3x = controlPoints[s + 3].x();
        float c0y = controlPoints[s].y(), c1y = controlPoints[s + 1].y(), c2y = controlPoints[s + 2].y(), c3y = controlPoints[s + 3].y();
        float c0z = controlPoints[s].z(), c1z = controlPoints[s + 1].z(), c2z = controlPoints[s + 2].z(), c3z = controlPoints[s + 3].z();
        float c0w = controlPoints[s].w(), c1w = controlPoints[s + 1].w(), c2w = controlPoints[s + 2].w(), c3w = controlPoints[s + 3].w();
        for (int i = 0; i < length; i++) {
            float t = tValues[tOffset + i];
            outX[outXOffset + i] = CubicCurveMath.bspline(c0x, c1x, c2x, c3x, t);
            outY[outYOffset + i] = CubicCurveMath.bspline(c0y, c1y, c2y, c3y, t);
            outZ[outZOffset + i] = CubicCurveMath.bspline(c0z, c1z, c2z, c3z, t);
            outW[outWOffset + i] = CubicCurveMath.bspline(c0w, c1w, c2w, c3w, t);
        }
    }
    private int checkedSegment(int segmentIndex) { if (segmentIndex < 0 || segmentIndex >= segmentCount()) throw new IllegalArgumentException("segmentIndex"); return segmentIndex; }
}
