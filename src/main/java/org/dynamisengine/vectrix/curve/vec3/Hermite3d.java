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
 * A cubic Hermite segment over 3D points: it runs from {@code p0} with tangent {@code m0} to {@code p1} with tangent
 * {@code m1}. Tangents are derivatives with respect to {@code t}, so they scale with the segment's parameter range.
 * <p>
 * The parameter {@code t} runs over {@code [0, 1]} on the segment and is not clamped: values outside extrapolate the
 * cubic. The methods are static and stateless. Every {@code dest} may be one of the input points. Nothing allocates.
 * {@link Hermite3f} is the float twin.
 *
 * @since 1.0.0
 */
public final class Hermite3d {
    private Hermite3d() {}

    /**
     * Evaluates the segment at {@code t}.
     *
     * @param p0 the start point
     * @param m0 the tangent at the start
     * @param p1 the end point
     * @param m1 the tangent at the end
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param dest receives the result
     * @return {@code dest}
     */
    public static Vector3d evaluate(Vector3dc p0, Vector3dc m0, Vector3dc p1, Vector3dc m1, double t, Vector3d dest) {
        return dest.set(CubicCurveMath.hermite(p0.x(), m0.x(), p1.x(), m1.x(), t), CubicCurveMath.hermite(p0.y(), m0.y(), p1.y(), m1.y(), t), CubicCurveMath.hermite(p0.z(), m0.z(), p1.z(), m1.z(), t));
    }

    /**
     * Computes the first derivative with respect to {@code t} (the tangent, not normalized).
     *
     * @param p0 the start point
     * @param m0 the tangent at the start
     * @param p1 the end point
     * @param m1 the tangent at the end
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param dest receives the result
     * @return {@code dest}
     */
    public static Vector3d derivative(Vector3dc p0, Vector3dc m0, Vector3dc p1, Vector3dc m1, double t, Vector3d dest) {
        return dest.set(CubicCurveMath.hermiteDerivative(p0.x(), m0.x(), p1.x(), m1.x(), t), CubicCurveMath.hermiteDerivative(p0.y(), m0.y(), p1.y(), m1.y(), t), CubicCurveMath.hermiteDerivative(p0.z(), m0.z(), p1.z(), m1.z(), t));
    }

    /**
     * Computes the second derivative with respect to {@code t}.
     *
     * @param p0 the start point
     * @param m0 the tangent at the start
     * @param p1 the end point
     * @param m1 the tangent at the end
     * @param t the curve parameter, usually in {@code [0, 1]}; not clamped
     * @param dest receives the result
     * @return {@code dest}
     */
    public static Vector3d secondDerivative(Vector3dc p0, Vector3dc m0, Vector3dc p1, Vector3dc m1, double t, Vector3d dest) {
        return dest.set(CubicCurveMath.hermiteSecondDerivative(p0.x(), m0.x(), p1.x(), m1.x(), t), CubicCurveMath.hermiteSecondDerivative(p0.y(), m0.y(), p1.y(), m1.y(), t), CubicCurveMath.hermiteSecondDerivative(p0.z(), m0.z(), p1.z(), m1.z(), t));
    }

    /**
     * Evaluates the segment at {@code length} parameters, writing one point, one array per coordinate. The arrays are
     * not checked up front; an output array may be {@code tValues} at the same offset.
     *
     * @param p0 the start point
     * @param m0 the tangent at the start
     * @param p1 the end point
     * @param m1 the tangent at the end
     * @param tValues the curve parameters
     * @param tOffset the offset of the first parameter in {@code tValues}
     * @param length the number of parameters to evaluate
     * @param outX receives the x coordinates
     * @param outXOffset the offset of the first coordinate in {@code outX}
     * @param outY receives the y coordinates
     * @param outYOffset the offset of the first coordinate in {@code outY}
     * @param outZ receives the z coordinates
     * @param outZOffset the offset of the first coordinate in {@code outZ}
     */
    public static void evaluateBatch(Vector3dc p0, Vector3dc m0, Vector3dc p1, Vector3dc m1, double[] tValues, int tOffset, int length, double[] outX, int outXOffset, double[] outY, int outYOffset, double[] outZ, int outZOffset) {
        for (int i = 0; i < length; i++) {
            double t = tValues[tOffset + i];
            outX[outXOffset + i] = CubicCurveMath.hermite(p0.x(), m0.x(), p1.x(), m1.x(), t);
            outY[outYOffset + i] = CubicCurveMath.hermite(p0.y(), m0.y(), p1.y(), m1.y(), t);
            outZ[outZOffset + i] = CubicCurveMath.hermite(p0.z(), m0.z(), p1.z(), m1.z(), t);
        }
    }
}
