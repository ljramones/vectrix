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

package org.vectrix.curve.scalar;

import org.vectrix.curve.CubicCurveMath;

public final class CatmullRom1d {
    private CatmullRom1d() {}

    public static double evaluate(double p0, double p1, double p2, double p3, double t, double tension) {
        double m1 = CubicCurveMath.cardinalTangent(p0, p2, tension);
        double m2 = CubicCurveMath.cardinalTangent(p1, p3, tension);
        return CubicCurveMath.hermite(p1, m1, p2, m2, t);
    }

    public static double derivative(double p0, double p1, double p2, double p3, double t, double tension) {
        double m1 = CubicCurveMath.cardinalTangent(p0, p2, tension);
        double m2 = CubicCurveMath.cardinalTangent(p1, p3, tension);
        return CubicCurveMath.hermiteDerivative(p1, m1, p2, m2, t);
    }

    public static double secondDerivative(double p0, double p1, double p2, double p3, double t, double tension) {
        double m1 = CubicCurveMath.cardinalTangent(p0, p2, tension);
        double m2 = CubicCurveMath.cardinalTangent(p1, p3, tension);
        return CubicCurveMath.hermiteSecondDerivative(p1, m1, p2, m2, t);
    }

    public static void evaluateBatch(double p0, double p1, double p2, double p3, double tension, double[] tValues, int tOffset, int length, double[] outValues, int outOffset) {
        for (int i = 0; i < length; i++) {
            outValues[outOffset + i] = evaluate(p0, p1, p2, p3, tValues[tOffset + i], tension);
        }
    }
}
