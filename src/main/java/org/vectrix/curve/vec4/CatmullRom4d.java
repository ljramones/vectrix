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

/**
 * @since 1.0.0
 */

public final class CatmullRom4d {
    private CatmullRom4d() {}
    public static Vector4d evaluate(Vector4dc p0, Vector4dc p1, Vector4dc p2, Vector4dc p3, double t, double tension, Vector4d dest) {
        return dest.set(CubicCurveMath.hermite(p1.x(), CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), p2.x(), CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension), t),
                CubicCurveMath.hermite(p1.y(), CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), p2.y(), CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension), t),
                CubicCurveMath.hermite(p1.z(), CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension), p2.z(), CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension), t),
                CubicCurveMath.hermite(p1.w(), CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension), p2.w(), CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension), t));
    }
    public static Vector4d derivative(Vector4dc p0, Vector4dc p1, Vector4dc p2, Vector4dc p3, double t, double tension, Vector4d dest) {
        return dest.set(CubicCurveMath.hermiteDerivative(p1.x(), CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), p2.x(), CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension), t),
                CubicCurveMath.hermiteDerivative(p1.y(), CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), p2.y(), CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension), t),
                CubicCurveMath.hermiteDerivative(p1.z(), CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension), p2.z(), CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension), t),
                CubicCurveMath.hermiteDerivative(p1.w(), CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension), p2.w(), CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension), t));
    }
    public static Vector4d secondDerivative(Vector4dc p0, Vector4dc p1, Vector4dc p2, Vector4dc p3, double t, double tension, Vector4d dest) {
        return dest.set(CubicCurveMath.hermiteSecondDerivative(p1.x(), CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), p2.x(), CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension), t),
                CubicCurveMath.hermiteSecondDerivative(p1.y(), CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), p2.y(), CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension), t),
                CubicCurveMath.hermiteSecondDerivative(p1.z(), CubicCurveMath.cardinalTangent(p0.z(), p2.z(), tension), p2.z(), CubicCurveMath.cardinalTangent(p1.z(), p3.z(), tension), t),
                CubicCurveMath.hermiteSecondDerivative(p1.w(), CubicCurveMath.cardinalTangent(p0.w(), p2.w(), tension), p2.w(), CubicCurveMath.cardinalTangent(p1.w(), p3.w(), tension), t));
    }
    public static void evaluateBatch(Vector4dc p0, Vector4dc p1, Vector4dc p2, Vector4dc p3, double tension, double[] tValues, int tOffset, int length, double[] outX, int outXOffset, double[] outY, int outYOffset, double[] outZ, int outZOffset, double[] outW, int outWOffset) {
        for (int i = 0; i < length; i++) {
            Vector4d v = evaluate(p0, p1, p2, p3, tValues[tOffset + i], tension, new Vector4d());
            outX[outXOffset+i] = v.x; outY[outYOffset+i] = v.y; outZ[outZOffset+i] = v.z; outW[outWOffset+i] = v.w;
        }
    }
}
