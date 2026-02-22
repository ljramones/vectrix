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

import org.vectrix.core.Vector3fc;

/**
 * Bent-normal visibility cone utilities (single-precision).
 */
public final class BentNormalConef {
    private BentNormalConef() {
    }

    /**
     * Convert ambient accessibility AO in [0,1] to cone half-angle.
     * Uses spherical-cap relation: solidAngle = 2*pi*AO.
     */
    public static float coneAngleFromAo(float ao) {
        float a = clamp01(ao);
        return (float) java.lang.Math.acos(1.0f - a);
    }

    public static float coneSolidAngle(float halfAngle) {
        float a = java.lang.Math.max(0.0f, halfAngle);
        return (float) (2.0 * java.lang.Math.PI * (1.0 - java.lang.Math.cos(a)));
    }

    /**
     * Approximate intersection solid angle of two cones sharing the same apex.
     */
    public static float estimateIntersectionSolidAngle(float halfAngleA, Vector3fc axisA, float halfAngleB, Vector3fc axisB) {
        float a = java.lang.Math.max(0.0f, halfAngleA);
        float b = java.lang.Math.max(0.0f, halfAngleB);
        float dot = clamp(axisA.x() * axisB.x() + axisA.y() * axisB.y() + axisA.z() * axisB.z(), -1.0f, 1.0f);
        float d = (float) java.lang.Math.acos(dot);
        float omegaA = coneSolidAngle(a);
        float omegaB = coneSolidAngle(b);
        float omegaMin = java.lang.Math.min(omegaA, omegaB);

        if (d >= a + b) {
            return 0.0f;
        }
        if (d <= java.lang.Math.abs(a - b)) {
            return omegaMin;
        }
        float t = (a + b - d) / (2.0f * java.lang.Math.min(a, b));
        t = clamp01(t);
        return omegaMin * t;
    }

    private static float clamp01(float v) {
        return java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, v));
    }

    private static float clamp(float v, float lo, float hi) {
        return java.lang.Math.max(lo, java.lang.Math.min(hi, v));
    }
}
