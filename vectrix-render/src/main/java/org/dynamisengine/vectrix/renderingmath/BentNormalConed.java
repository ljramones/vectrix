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
package org.dynamisengine.vectrix.renderingmath;

import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Bent-normal visibility cone utilities (double-precision).
  * @since 1.0.0
  */
public final class BentNormalConed {
    private BentNormalConed() {
    }

    /**
     * Converts ambient accessibility (unoccluded fraction of the hemisphere) to the half-angle of a cone with the
     * same share of the sphere: a cone of solid angle {@code 2π · ao}, so {@code acos(1 − ao)}.
     *
     * @param ao the ambient accessibility, clamped to {@code [0, 1]}
     * @return the cone half-angle in radians, in {@code [0, π/2]}
     */
    public static double coneAngleFromAo(double ao) {
        double a = clamp01(ao);
        return org.dynamisengine.vectrix.core.AccurateMath.acos(1.0 - a);
    }

    /**
     * Returns the solid angle of a cone, {@code 2π (1 − cos halfAngle)}.
     *
     * @param halfAngle the half-angle in radians; negative values count as 0
     * @return the solid angle in steradians
     */
    public static double coneSolidAngle(double halfAngle) {
        double a = java.lang.Math.max(0.0, halfAngle);
        return 2.0 * java.lang.Math.PI * (1.0 - org.dynamisengine.vectrix.core.AccurateMath.cos(a));
    }

    /**
     * Estimates the solid angle of the intersection of two cones with a shared apex. Returns 0 for disjoint cones
     * and the smaller cone's solid angle when one contains the other; in between, it ramps linearly with the
     * angular overlap {@code (a + b − d) / (2 min(a, b))}, where {@code d} is the angle between the axes.
     *
     * @param halfAngleA the half-angle of the first cone in radians; negative values count as 0
     * @param axisA the unit axis of the first cone
     * @param halfAngleB the half-angle of the second cone in radians; negative values count as 0
     * @param axisB the unit axis of the second cone
     * @return the estimated solid angle in steradians
     */
    public static double estimateIntersectionSolidAngle(double halfAngleA, Vector3dc axisA, double halfAngleB, Vector3dc axisB) {
        double a = java.lang.Math.max(0.0, halfAngleA);
        double b = java.lang.Math.max(0.0, halfAngleB);
        double dot = clamp(axisA.x() * axisB.x() + axisA.y() * axisB.y() + axisA.z() * axisB.z(), -1.0, 1.0);
        double d = org.dynamisengine.vectrix.core.AccurateMath.acos(dot);
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
