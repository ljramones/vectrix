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

import org.vectrix.core.Vector4f;
import org.vectrix.core.Vector4fc;
import org.vectrix.curve.CubicCurveMath;

public final class Bezier4f {
    private Bezier4f() {}
    public static Vector4f evaluate(Vector4fc p0, Vector4fc p1, Vector4fc p2, Vector4fc p3, float t, Vector4f dest) {
        return dest.set(CubicCurveMath.bezier(p0.x(), p1.x(), p2.x(), p3.x(), t), CubicCurveMath.bezier(p0.y(), p1.y(), p2.y(), p3.y(), t), CubicCurveMath.bezier(p0.z(), p1.z(), p2.z(), p3.z(), t), CubicCurveMath.bezier(p0.w(), p1.w(), p2.w(), p3.w(), t));
    }
    public static Vector4f derivative(Vector4fc p0, Vector4fc p1, Vector4fc p2, Vector4fc p3, float t, Vector4f dest) {
        return dest.set(CubicCurveMath.bezierDerivative(p0.x(), p1.x(), p2.x(), p3.x(), t), CubicCurveMath.bezierDerivative(p0.y(), p1.y(), p2.y(), p3.y(), t), CubicCurveMath.bezierDerivative(p0.z(), p1.z(), p2.z(), p3.z(), t), CubicCurveMath.bezierDerivative(p0.w(), p1.w(), p2.w(), p3.w(), t));
    }
    public static Vector4f secondDerivative(Vector4fc p0, Vector4fc p1, Vector4fc p2, Vector4fc p3, float t, Vector4f dest) {
        return dest.set(CubicCurveMath.bezierSecondDerivative(p0.x(), p1.x(), p2.x(), p3.x(), t), CubicCurveMath.bezierSecondDerivative(p0.y(), p1.y(), p2.y(), p3.y(), t), CubicCurveMath.bezierSecondDerivative(p0.z(), p1.z(), p2.z(), p3.z(), t), CubicCurveMath.bezierSecondDerivative(p0.w(), p1.w(), p2.w(), p3.w(), t));
    }
    public static void evaluateBatch(Vector4fc p0, Vector4fc p1, Vector4fc p2, Vector4fc p3, float[] tValues, int tOffset, int length, float[] outX, int outXOffset, float[] outY, int outYOffset, float[] outZ, int outZOffset, float[] outW, int outWOffset) {
        for (int i = 0; i < length; i++) {
            float t = tValues[tOffset + i];
            outX[outXOffset + i] = CubicCurveMath.bezier(p0.x(), p1.x(), p2.x(), p3.x(), t);
            outY[outYOffset + i] = CubicCurveMath.bezier(p0.y(), p1.y(), p2.y(), p3.y(), t);
            outZ[outZOffset + i] = CubicCurveMath.bezier(p0.z(), p1.z(), p2.z(), p3.z(), t);
            outW[outWOffset + i] = CubicCurveMath.bezier(p0.w(), p1.w(), p2.w(), p3.w(), t);
        }
    }
}
