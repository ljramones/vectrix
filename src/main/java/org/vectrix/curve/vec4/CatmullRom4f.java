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

/**
 * @since 1.0.0
 */

public final class CatmullRom4f {
    private CatmullRom4f() {}
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
    public static Vector4f derivative(Vector4fc p0, Vector4fc p1, Vector4fc p2, Vector4fc p3, float t, float tension, Vector4f dest) { return evaluate(p0,p1,p2,p3,t,tension,dest).set(CubicCurveMath.hermiteDerivative(p1.x(), CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), p2.x(), CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension), t), CubicCurveMath.hermiteDerivative(p1.y(), CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), p2.y(), CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension), t), CubicCurveMath.hermiteDerivative(p1.z(), CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension), p2.z(), CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension), t), CubicCurveMath.hermiteDerivative(p1.w(), CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension), p2.w(), CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension), t)); }
    public static Vector4f secondDerivative(Vector4fc p0, Vector4fc p1, Vector4fc p2, Vector4fc p3, float t, float tension, Vector4f dest) { return dest.set(CubicCurveMath.hermiteSecondDerivative(p1.x(), CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), p2.x(), CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension), t), CubicCurveMath.hermiteSecondDerivative(p1.y(), CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), p2.y(), CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension), t), CubicCurveMath.hermiteSecondDerivative(p1.z(), CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension), p2.z(), CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension), t), CubicCurveMath.hermiteSecondDerivative(p1.w(), CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension), p2.w(), CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension), t)); }
    public static void evaluateBatch(Vector4fc p0, Vector4fc p1, Vector4fc p2, Vector4fc p3, float tension, float[] tValues, int tOffset, int length, float[] outX, int outXOffset, float[] outY, int outYOffset, float[] outZ, int outZOffset, float[] outW, int outWOffset) {
        for (int i = 0; i < length; i++) {
            Vector4f v = evaluate(p0, p1, p2, p3, tValues[tOffset + i], tension, new Vector4f());
            outX[outXOffset+i] = v.x; outY[outYOffset+i] = v.y; outZ[outZOffset+i] = v.z; outW[outWOffset+i] = v.w;
        }
    }
}
