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

public final class UniformBSpline1d {
    private final double[] controlPoints;

    public UniformBSpline1d(double[] controlPoints) {
        if (controlPoints.length < 4) {
            throw new IllegalArgumentException("controlPoints");
        }
        this.controlPoints = controlPoints;
    }

    public int segmentCount() {
        return controlPoints.length - 3;
    }

    public double evaluate(int segmentIndex, double t) {
        int i = checkedSegment(segmentIndex);
        return CubicCurveMath.bspline(controlPoints[i], controlPoints[i + 1], controlPoints[i + 2], controlPoints[i + 3], t);
    }

    public double derivative(int segmentIndex, double t) {
        int i = checkedSegment(segmentIndex);
        return CubicCurveMath.bsplineDerivative(controlPoints[i], controlPoints[i + 1], controlPoints[i + 2], controlPoints[i + 3], t);
    }

    public double secondDerivative(int segmentIndex, double t) {
        int i = checkedSegment(segmentIndex);
        return CubicCurveMath.bsplineSecondDerivative(controlPoints[i], controlPoints[i + 1], controlPoints[i + 2], controlPoints[i + 3], t);
    }

    public void evaluateBatch(int segmentIndex, double[] tValues, int tOffset, int length, double[] outValues, int outOffset) {
        for (int i = 0; i < length; i++) {
            outValues[outOffset + i] = evaluate(segmentIndex, tValues[tOffset + i]);
        }
    }

    private int checkedSegment(int segmentIndex) {
        if (segmentIndex < 0 || segmentIndex >= segmentCount()) {
            throw new IllegalArgumentException("segmentIndex");
        }
        return segmentIndex;
    }
}
