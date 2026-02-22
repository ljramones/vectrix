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

package org.vectrix.curve.vec4;

import org.vectrix.core.Vector4d;
import org.vectrix.core.Vector4dc;
import org.vectrix.curve.CubicCurveMath;

public final class Bezier4d {
    private Bezier4d() {}
    public static Vector4d evaluate(Vector4dc p0, Vector4dc p1, Vector4dc p2, Vector4dc p3, double t, Vector4d dest) {
        return dest.set(CubicCurveMath.bezier(p0.x(), p1.x(), p2.x(), p3.x(), t), CubicCurveMath.bezier(p0.y(), p1.y(), p2.y(), p3.y(), t), CubicCurveMath.bezier(p0.z(), p1.z(), p2.z(), p3.z(), t), CubicCurveMath.bezier(p0.w(), p1.w(), p2.w(), p3.w(), t));
    }
    public static Vector4d derivative(Vector4dc p0, Vector4dc p1, Vector4dc p2, Vector4dc p3, double t, Vector4d dest) {
        return dest.set(CubicCurveMath.bezierDerivative(p0.x(), p1.x(), p2.x(), p3.x(), t), CubicCurveMath.bezierDerivative(p0.y(), p1.y(), p2.y(), p3.y(), t), CubicCurveMath.bezierDerivative(p0.z(), p1.z(), p2.z(), p3.z(), t), CubicCurveMath.bezierDerivative(p0.w(), p1.w(), p2.w(), p3.w(), t));
    }
    public static Vector4d secondDerivative(Vector4dc p0, Vector4dc p1, Vector4dc p2, Vector4dc p3, double t, Vector4d dest) {
        return dest.set(CubicCurveMath.bezierSecondDerivative(p0.x(), p1.x(), p2.x(), p3.x(), t), CubicCurveMath.bezierSecondDerivative(p0.y(), p1.y(), p2.y(), p3.y(), t), CubicCurveMath.bezierSecondDerivative(p0.z(), p1.z(), p2.z(), p3.z(), t), CubicCurveMath.bezierSecondDerivative(p0.w(), p1.w(), p2.w(), p3.w(), t));
    }
    public static void evaluateBatch(Vector4dc p0, Vector4dc p1, Vector4dc p2, Vector4dc p3, double[] tValues, int tOffset, int length, double[] outX, int outXOffset, double[] outY, int outYOffset, double[] outZ, int outZOffset, double[] outW, int outWOffset) {
        for (int i = 0; i < length; i++) {
            double t = tValues[tOffset + i];
            outX[outXOffset + i] = CubicCurveMath.bezier(p0.x(), p1.x(), p2.x(), p3.x(), t);
            outY[outYOffset + i] = CubicCurveMath.bezier(p0.y(), p1.y(), p2.y(), p3.y(), t);
            outZ[outZOffset + i] = CubicCurveMath.bezier(p0.z(), p1.z(), p2.z(), p3.z(), t);
            outW[outWOffset + i] = CubicCurveMath.bezier(p0.w(), p1.w(), p2.w(), p3.w(), t);
        }
    }
}
