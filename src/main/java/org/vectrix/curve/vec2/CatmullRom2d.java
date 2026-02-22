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

package org.vectrix.curve.vec2;

import org.vectrix.core.Vector2d;
import org.vectrix.core.Vector2dc;
import org.vectrix.curve.CubicCurveMath;

public final class CatmullRom2d {
    private CatmullRom2d() {}

    public static Vector2d evaluate(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3, double t, double tension, Vector2d dest) {
        double m1x = CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension);
        double m1y = CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension);
        double m2x = CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension);
        double m2y = CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension);
        return dest.set(CubicCurveMath.hermite(p1.x(), m1x, p2.x(), m2x, t), CubicCurveMath.hermite(p1.y(), m1y, p2.y(), m2y, t));
    }
    public static Vector2d derivative(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3, double t, double tension, Vector2d dest) {
        double m1x = CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension);
        double m1y = CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension);
        double m2x = CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension);
        double m2y = CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension);
        return dest.set(CubicCurveMath.hermiteDerivative(p1.x(), m1x, p2.x(), m2x, t), CubicCurveMath.hermiteDerivative(p1.y(), m1y, p2.y(), m2y, t));
    }
    public static Vector2d secondDerivative(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3, double t, double tension, Vector2d dest) {
        double m1x = CubicCurveMath.cardinalTangent(p0.x(), p2.x(), tension);
        double m1y = CubicCurveMath.cardinalTangent(p0.y(), p2.y(), tension);
        double m2x = CubicCurveMath.cardinalTangent(p1.x(), p3.x(), tension);
        double m2y = CubicCurveMath.cardinalTangent(p1.y(), p3.y(), tension);
        return dest.set(CubicCurveMath.hermiteSecondDerivative(p1.x(), m1x, p2.x(), m2x, t), CubicCurveMath.hermiteSecondDerivative(p1.y(), m1y, p2.y(), m2y, t));
    }
    public static void evaluateBatch(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3, double tension, double[] tValues, int tOffset, int length, double[] outX, int outXOffset, double[] outY, int outYOffset) {
        for (int i = 0; i < length; i++) {
            Vector2d v = evaluate(p0, p1, p2, p3, tValues[tOffset + i], tension, new Vector2d());
            outX[outXOffset + i] = v.x;
            outY[outYOffset + i] = v.y;
        }
    }
}
