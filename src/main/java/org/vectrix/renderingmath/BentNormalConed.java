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
package org.vectrix.renderingmath;

import org.vectrix.core.Vector3dc;

/**
 * Bent-normal visibility cone utilities (double-precision).
 */
public final class BentNormalConed {
    private BentNormalConed() {
    }

    public static double coneAngleFromAo(double ao) {
        double a = clamp01(ao);
        return java.lang.Math.acos(1.0 - a);
    }

    public static double coneSolidAngle(double halfAngle) {
        double a = java.lang.Math.max(0.0, halfAngle);
        return 2.0 * java.lang.Math.PI * (1.0 - java.lang.Math.cos(a));
    }

    public static double estimateIntersectionSolidAngle(double halfAngleA, Vector3dc axisA, double halfAngleB, Vector3dc axisB) {
        double a = java.lang.Math.max(0.0, halfAngleA);
        double b = java.lang.Math.max(0.0, halfAngleB);
        double dot = clamp(axisA.x() * axisB.x() + axisA.y() * axisB.y() + axisA.z() * axisB.z(), -1.0, 1.0);
        double d = java.lang.Math.acos(dot);
        double omegaA = coneSolidAngle(a);
        double omegaB = coneSolidAngle(b);
        double omegaMin = java.lang.Math.min(omegaA, omegaB);
        if (d >= a + b) {
            return 0.0;
        }
        if (d <= java.lang.Math.abs(a - b)) {
            return omegaMin;
        }
        double t = (a + b - d) / (2.0 * java.lang.Math.min(a, b));
        t = clamp01(t);
        return omegaMin * t;
    }

    private static double clamp01(double v) {
        return java.lang.Math.max(0.0, java.lang.Math.min(1.0, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return java.lang.Math.max(lo, java.lang.Math.min(hi, v));
    }
}
