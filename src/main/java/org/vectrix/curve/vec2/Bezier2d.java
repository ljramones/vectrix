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

public final class Bezier2d {
    private Bezier2d() {}

    public static Vector2d evaluate(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3, double t, Vector2d dest) {
        return dest.set(CubicCurveMath.bezier(p0.x(), p1.x(), p2.x(), p3.x(), t), CubicCurveMath.bezier(p0.y(), p1.y(), p2.y(), p3.y(), t));
    }
    public static Vector2d derivative(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3, double t, Vector2d dest) {
        return dest.set(CubicCurveMath.bezierDerivative(p0.x(), p1.x(), p2.x(), p3.x(), t), CubicCurveMath.bezierDerivative(p0.y(), p1.y(), p2.y(), p3.y(), t));
    }
    public static Vector2d secondDerivative(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3, double t, Vector2d dest) {
        return dest.set(CubicCurveMath.bezierSecondDerivative(p0.x(), p1.x(), p2.x(), p3.x(), t), CubicCurveMath.bezierSecondDerivative(p0.y(), p1.y(), p2.y(), p3.y(), t));
    }
    public static void evaluateBatch(Vector2dc p0, Vector2dc p1, Vector2dc p2, Vector2dc p3, double[] tValues, int tOffset, int length, double[] outX, int outXOffset, double[] outY, int outYOffset) {
        for (int i = 0; i < length; i++) {
            double t = tValues[tOffset + i];
            outX[outXOffset + i] = CubicCurveMath.bezier(p0.x(), p1.x(), p2.x(), p3.x(), t);
            outY[outYOffset + i] = CubicCurveMath.bezier(p0.y(), p1.y(), p2.y(), p3.y(), t);
        }
    }
}
