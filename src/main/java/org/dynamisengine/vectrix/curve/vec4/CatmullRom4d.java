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

import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.core.Vector4dc;
import org.dynamisengine.vectrix.curve.CubicCurveMath;

/**
 * A cardinal (Catmull–Rom) spline segment over 4D vectors: the Hermite segment from {@code p1} to {@code p2} with
 * tangents {@code (1 - tension)·(p2 - p0)/2} at {@code p1} and {@code (1 - tension)·(p3 - p1)/2} at {@code p2}. Tension
 * 0 gives the uniform Catmull–Rom spline, which passes through every point; tension 1 gives zero tangents.
 * <p>
 * The parameter {@code t} runs over {@code [0, 1]} on the segment and is not clamped: values outside extrapolate the
 * cubic. The methods are static and stateless. Every {@code dest} may be one of the input points. Nothing allocates.
 * {@link CatmullRom4f} is the float twin.
 *
 * @since 1.0.0
 */
public final class CatmullRom4d {
    private CatmullRom4d() {}

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
    public static Vector4d evaluate(Vector4dc p0, Vector4dc p1, Vector4dc p2, Vector4dc p3, double t, double tension, Vector4d dest) {
        return dest.set(CubicCurveMath.hermite(p1.x(), CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), p2.x(), CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension), t),
                CubicCurveMath.hermite(p1.y(), CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), p2.y(), CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension), t),
                CubicCurveMath.hermite(p1.z(), CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension), p2.z(), CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension), t),
                CubicCurveMath.hermite(p1.w(), CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension), p2.w(), CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension), t));
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
    public static Vector4d derivative(Vector4dc p0, Vector4dc p1, Vector4dc p2, Vector4dc p3, double t, double tension, Vector4d dest) {
        return dest.set(CubicCurveMath.hermiteDerivative(p1.x(), CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), p2.x(), CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension), t),
                CubicCurveMath.hermiteDerivative(p1.y(), CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), p2.y(), CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension), t),
                CubicCurveMath.hermiteDerivative(p1.z(), CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension), p2.z(), CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension), t),
                CubicCurveMath.hermiteDerivative(p1.w(), CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension), p2.w(), CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension), t));
    }

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
    public static Vector4d secondDerivative(Vector4dc p0, Vector4dc p1, Vector4dc p2, Vector4dc p3, double t, double tension, Vector4d dest) {
        return dest.set(CubicCurveMath.hermiteSecondDerivative(p1.x(), CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), p2.x(), CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension), t),
                CubicCurveMath.hermiteSecondDerivative(p1.y(), CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), p2.y(), CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension), t),
                CubicCurveMath.hermiteSecondDerivative(p1.z(), CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension), p2.z(), CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension), t),
                CubicCurveMath.hermiteSecondDerivative(p1.w(), CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension), p2.w(), CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension), t));
    }

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
    public static void evaluateBatch(Vector4dc p0, Vector4dc p1, Vector4dc p2, Vector4dc p3, double tension, double[] tValues, int tOffset, int length, double[] outX, int outXOffset, double[] outY, int outYOffset, double[] outZ, int outZOffset, double[] outW, int outWOffset) {
        double m1x = CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), m2x = CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension);
        double ax = p1.x(), bx = p2.x();
        double m1y = CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), m2y = CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension);
        double ay = p1.y(), by = p2.y();
        double m1z = CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension), m2z = CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension);
        double az = p1.z(), bz = p2.z();
        double m1w = CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension), m2w = CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension);
        double aw = p1.w(), bw = p2.w();
        for (int i = 0; i < length; i++) {
            double t = tValues[tOffset + i];
            outX[outXOffset + i] = CubicCurveMath.hermite(ax, m1x, bx, m2x, t);
            outY[outYOffset + i] = CubicCurveMath.hermite(ay, m1y, by, m2y, t);
            outZ[outZOffset + i] = CubicCurveMath.hermite(az, m1z, bz, m2z, t);
            outW[outWOffset + i] = CubicCurveMath.hermite(aw, m1w, bw, m2w, t);
        }
    }
}
