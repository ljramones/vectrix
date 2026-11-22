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

package org.dynamisengine.vectrix.curve.vec2;

import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector2fc;
import org.dynamisengine.vectrix.curve.CubicCurveMath;

/**
 * @since 1.0.0
 */

public final class CatmullRom2f {
    private CatmullRom2f() {}

    public static Vector2f evaluate(Vector2fc p0, Vector2fc p1, Vector2fc p2, Vector2fc p3, float t, float tension, Vector2f dest) {
        float m1x = CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension);
        float m1y = CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension);
        float m2x = CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension);
        float m2y = CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension);
        return dest.set(CubicCurveMath.hermite(p1.x(), m1x, p2.x(), m2x, t), CubicCurveMath.hermite(p1.y(), m1y, p2.y(), m2y, t));
    }
    public static Vector2f derivative(Vector2fc p0, Vector2fc p1, Vector2fc p2, Vector2fc p3, float t, float tension, Vector2f dest) {
        float m1x = CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension);
        float m1y = CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension);
        float m2x = CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension);
        float m2y = CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension);
        return dest.set(CubicCurveMath.hermiteDerivative(p1.x(), m1x, p2.x(), m2x, t), CubicCurveMath.hermiteDerivative(p1.y(), m1y, p2.y(), m2y, t));
    }
    public static Vector2f secondDerivative(Vector2fc p0, Vector2fc p1, Vector2fc p2, Vector2fc p3, float t, float tension, Vector2f dest) {
        float m1x = CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension);
        float m1y = CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension);
        float m2x = CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension);
        float m2y = CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension);
        return dest.set(CubicCurveMath.hermiteSecondDerivative(p1.x(), m1x, p2.x(), m2x, t), CubicCurveMath.hermiteSecondDerivative(p1.y(), m1y, p2.y(), m2y, t));
    }
    public static void evaluateBatch(Vector2fc p0, Vector2fc p1, Vector2fc p2, Vector2fc p3, float tension, float[] tValues, int tOffset, int length, float[] outX, int outXOffset, float[] outY, int outYOffset) {
        float m1x = CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension), m2x = CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension);
        float ax = p1.x(), bx = p2.x();
        float m1y = CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension), m2y = CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension);
        float ay = p1.y(), by = p2.y();
        for (int i = 0; i < length; i++) {
            float t = tValues[tOffset + i];
            outX[outXOffset + i] = CubicCurveMath.hermite(ax, m1x, bx, m2x, t);
            outY[outYOffset + i] = CubicCurveMath.hermite(ay, m1y, by, m2y, t);
        }
    }
}
