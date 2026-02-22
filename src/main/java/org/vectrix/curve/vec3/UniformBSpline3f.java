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

package org.vectrix.curve.vec3;

import org.vectrix.core.Vector3f;
import org.vectrix.core.Vector3fc;
import org.vectrix.curve.CubicCurveMath;

/**
 * @since 1.0.0
 */

public final class UniformBSpline3f {
    private final Vector3fc[] controlPoints;

    public UniformBSpline3f(Vector3fc[] controlPoints) {
        if (controlPoints.length < 4) throw new IllegalArgumentException("controlPoints");
        this.controlPoints = controlPoints;
    }

    public int segmentCount() { return controlPoints.length - 3; }

    public Vector3f evaluate(int segmentIndex, float t, Vector3f dest) {
        int i = checkedSegment(segmentIndex);
        return dest.set(CubicCurveMath.bspline(controlPoints[i].x(), controlPoints[i+1].x(), controlPoints[i+2].x(), controlPoints[i+3].x(), t),
                CubicCurveMath.bspline(controlPoints[i].y(), controlPoints[i+1].y(), controlPoints[i+2].y(), controlPoints[i+3].y(), t),
                CubicCurveMath.bspline(controlPoints[i].z(), controlPoints[i+1].z(), controlPoints[i+2].z(), controlPoints[i+3].z(), t));
    }

    public Vector3f derivative(int segmentIndex, float t, Vector3f dest) {
        int i = checkedSegment(segmentIndex);
        return dest.set(CubicCurveMath.bsplineDerivative(controlPoints[i].x(), controlPoints[i+1].x(), controlPoints[i+2].x(), controlPoints[i+3].x(), t),
                CubicCurveMath.bsplineDerivative(controlPoints[i].y(), controlPoints[i+1].y(), controlPoints[i+2].y(), controlPoints[i+3].y(), t),
                CubicCurveMath.bsplineDerivative(controlPoints[i].z(), controlPoints[i+1].z(), controlPoints[i+2].z(), controlPoints[i+3].z(), t));
    }

    public Vector3f secondDerivative(int segmentIndex, float t, Vector3f dest) {
        int i = checkedSegment(segmentIndex);
        return dest.set(CubicCurveMath.bsplineSecondDerivative(controlPoints[i].x(), controlPoints[i+1].x(), controlPoints[i+2].x(), controlPoints[i+3].x(), t),
                CubicCurveMath.bsplineSecondDerivative(controlPoints[i].y(), controlPoints[i+1].y(), controlPoints[i+2].y(), controlPoints[i+3].y(), t),
                CubicCurveMath.bsplineSecondDerivative(controlPoints[i].z(), controlPoints[i+1].z(), controlPoints[i+2].z(), controlPoints[i+3].z(), t));
    }

    public void evaluateBatch(int segmentIndex, float[] tValues, int tOffset, int length, float[] outX, int outXOffset, float[] outY, int outYOffset, float[] outZ, int outZOffset) {
        for (int i = 0; i < length; i++) {
            Vector3f v = evaluate(segmentIndex, tValues[tOffset + i], new Vector3f());
            outX[outXOffset + i] = v.x;
            outY[outYOffset + i] = v.y;
            outZ[outZOffset + i] = v.z;
        }
    }

    private int checkedSegment(int segmentIndex) {
        if (segmentIndex < 0 || segmentIndex >= segmentCount()) throw new IllegalArgumentException("segmentIndex");
        return segmentIndex;
    }
}
