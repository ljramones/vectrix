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
 * A cardinal (Catmull–Rom) spline segment over 4D vectors: the Hermite segment from {@code p1} to {@code p2} with
 * tangents {@code (1 - tension)·(p2 - p0)/2} at {@code p1} and {@code (1 - tension)·(p3 - p1)/2} at {@code p2}. Tension
 * 0 gives the uniform Catmull–Rom spline, which passes through every point; tension 1 gives zero tangents.
 * <p>
 * The parameter {@code t} runs over {@code [0, 1]} on the segment and is not clamped: values outside extrapolate the
 * cubic. The methods are static and stateless. Every {@code dest} may be one of the input points. Nothing allocates.
 * {@link CatmullRom4d} is the double twin.
 *
 * @since 1.0.0
 */
public final class CatmullRom4f {
    private CatmullRom4f() {}

    /**
     * Evaluates the segment at {@code t}.
     *
     * @param p0 the point before the segment
     * @param p1 the start point of the segment
     * @param p2 the end point of the segment
     * @param p3 the point after the segment
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param tension the cardinal tension: 0 for Catmull–Rom, 1 for zero tangents
     * @param dest receives the result
     * @return {@code dest}
     */
    public static Vector4f evaluate(Vector4fc p0, Vector4fc p1, Vector4fc p2, Vector4fc p3, float t, float tension, Vector4f dest) {
        float m1x = CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension);
        float m1y = CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension);
        float m1z = CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension);
        float m1w = CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension);
        float m2x = CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension);
        float m2y = CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension);
        float m2z = CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension);
        float m2w = CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension);
        return dest.set(CubicCurveMath.hermite(p1.x(), m1x, p2.x(), m2x, t), CubicCurveMath.hermite(p1.y(), m1y, p2.y(), m2y, t), CubicCurveMath.hermite(p1.z(), m1z, p2.z(), m2z, t), CubicCurveMath.hermite(p1.w(), m1w, p2.w(), m2w, t));
    }

    /**
     * Computes the first derivative with respect to {@code t} (the tangent, not normalized).
     *
     * @param p0 the point before the segment
     * @param p1 the start point of the segment
     * @param p2 the end point of the segment
     * @param p3 the point after the segment
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param tension the cardinal tension: 0 for Catmull–Rom, 1 for zero tangents
     * @param dest receives the result
     * @return {@code dest}
     */
    public static Vector4f derivative(Vector4fc p0, Vector4fc p1, Vector4fc p2, Vector4fc p3, float t, float tension, Vector4f dest) { return dest.set(CubicCurveMath.hermiteDerivative(p1.x(), CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), p2.x(), CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension), t), CubicCurveMath.hermiteDerivative(p1.y(), CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), p2.y(), CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension), t), CubicCurveMath.hermiteDerivative(p1.z(), CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension), p2.z(), CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension), t), CubicCurveMath.hermiteDerivative(p1.w(), CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension), p2.w(), CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension), t)); }

    /**
     * Computes the second derivative with respect to {@code t}.
     *
     * @param p0 the point before the segment
     * @param p1 the start point of the segment
     * @param p2 the end point of the segment
     * @param p3 the point after the segment
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param tension the cardinal tension: 0 for Catmull–Rom, 1 for zero tangents
     * @param dest receives the result
     * @return {@code dest}
     */
    public static Vector4f secondDerivative(Vector4fc p0, Vector4fc p1, Vector4fc p2, Vector4fc p3, float t, float tension, Vector4f dest) { return dest.set(CubicCurveMath.hermiteSecondDerivative(p1.x(), CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), p2.x(), CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension), t), CubicCurveMath.hermiteSecondDerivative(p1.y(), CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), p2.y(), CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension), t), CubicCurveMath.hermiteSecondDerivative(p1.z(), CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension), p2.z(), CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension), t), CubicCurveMath.hermiteSecondDerivative(p1.w(), CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension), p2.w(), CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension), t)); }

    /**
     * Evaluates the segment at {@code length} parameters, writing one point, one array per coordinate. The arrays are
     * not checked up front; an output array may be {@code tValues} at the same offset.
     *
     * @param p0 the point before the segment
     * @param p1 the start point of the segment
     * @param p2 the end point of the segment
     * @param p3 the point after the segment
     * @param tension the cardinal tension: 0 for Catmull–Rom, 1 for zero tangents
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
     */
    public static void evaluateBatch(Vector4fc p0, Vector4fc p1, Vector4fc p2, Vector4fc p3, float tension, float[] tValues, int tOffset, int length, float[] outX, int outXOffset, float[] outY, int outYOffset, float[] outZ, int outZOffset, float[] outW, int outWOffset) {
        float m1x = CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), m2x = CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension);
        float ax = p1.x(), bx = p2.x();
        float m1y = CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), m2y = CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension);
        float ay = p1.y(), by = p2.y();
        float m1z = CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension), m2z = CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension);
        float az = p1.z(), bz = p2.z();
        float m1w = CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension), m2w = CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension);
        float aw = p1.w(), bw = p2.w();
        for (int i = 0; i < length; i++) {
            float t = tValues[tOffset + i];
            outX[outXOffset + i] = CubicCurveMath.hermite(ax, m1x, bx, m2x, t);
            outY[outYOffset + i] = CubicCurveMath.hermite(ay, m1y, by, m2y, t);
            outZ[outZOffset + i] = CubicCurveMath.hermite(az, m1z, bz, m2z, t);
            outW[outWOffset + i] = CubicCurveMath.hermite(aw, m1w, bw, m2w, t);
        }
    }
}
