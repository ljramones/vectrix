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
package org.vectrix.ltc;

import org.vectrix.core.Vector3d;
import org.vectrix.core.Vector3dc;

/**
 * LTC evaluation helpers for area-light form factors (double-precision).
 */
public final class LtcEvald {
    private static final double INV_TWO_PI = 1.0 / (2.0 * java.lang.Math.PI);

    private LtcEvald() {
    }

    public static Vector3d transformDirection(double[] m3, Vector3dc dir, Vector3d dest) {
        if (m3 == null || m3.length < 9) {
            throw new IllegalArgumentException("m3");
        }
        double x = dir.x();
        double y = dir.y();
        double z = dir.z();
        return dest.set(
            m3[0] * x + m3[1] * y + m3[2] * z,
            m3[3] * x + m3[4] * y + m3[5] * z,
            m3[6] * x + m3[7] * y + m3[8] * z);
    }

    public static double horizonClip(double z) {
        return java.lang.Math.max(0.0, z);
    }

    public static double formFactorRect(Vector3dc v0, Vector3dc v1, Vector3dc v2, Vector3dc v3) {
        Vector3d sum = new Vector3d();
        integrateEdge(v0, v1, sum);
        integrateEdge(v1, v2, sum);
        integrateEdge(v2, v3, sum);
        integrateEdge(v3, v0, sum);
        return clamp01(sum.z * INV_TWO_PI);
    }

    public static double formFactorRectClipped(Vector3dc v0, Vector3dc v1, Vector3dc v2, Vector3dc v3) {
        return formFactorPolygonClipped(new Vector3dc[] {v0, v1, v2, v3}, 4);
    }

    public static double formFactorPolygonClipped(Vector3dc[] vertices, int count) {
        if (vertices == null || count < 3 || count > vertices.length) {
            throw new IllegalArgumentException("vertices/count");
        }
        Vector3d[] clipped = new Vector3d[count];
        for (int i = 0; i < count; i++) {
            Vector3dc v = vertices[i];
            clipped[i] = new Vector3d(v.x(), v.y(), horizonClip(v.z()));
            if (clipped[i].lengthSquared() > 1E-12) {
                clipped[i].normalize();
            }
        }
        Vector3d sum = new Vector3d();
        for (int i = 0; i < count; i++) {
            integrateEdge(clipped[i], clipped[(i + 1) % count], sum);
        }
        return clamp01(sum.z * INV_TWO_PI);
    }

    public static double formFactorDisc(double cosThetaCenter, double discAngularRadius) {
        double omega = 2.0 * java.lang.Math.PI * (1.0 - java.lang.Math.cos(java.lang.Math.max(0.0, discAngularRadius)));
        return clamp01(horizonClip(cosThetaCenter) * omega * INV_TWO_PI);
    }

    public static double formFactorTube(double cosThetaCenter, double angularRadius, double angularHalfLength) {
        double width = 2.0 * java.lang.Math.max(0.0, angularRadius);
        double length = 2.0 * java.lang.Math.max(0.0, angularHalfLength);
        double omegaApprox = width * length;
        return clamp01(horizonClip(cosThetaCenter) * omegaApprox * INV_TWO_PI);
    }

    private static void integrateEdge(Vector3dc a, Vector3dc b, Vector3d accum) {
        double cx = a.y() * b.z() - a.z() * b.y();
        double cy = a.z() * b.x() - a.x() * b.z();
        double cz = a.x() * b.y() - a.y() * b.x();
        double len = java.lang.Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1E-12) {
            return;
        }
        double d = clamp(a.x() * b.x() + a.y() * b.y() + a.z() * b.z(), -1.0, 1.0);
        double angle = java.lang.Math.acos(d);
        double s = angle / len;
        accum.x += cx * s;
        accum.y += cy * s;
        accum.z += cz * s;
    }

    private static double clamp01(double v) {
        return java.lang.Math.max(0.0, java.lang.Math.min(1.0, v));
    }

    private static double clamp(double v, double lo, double hi) {
        return java.lang.Math.max(lo, java.lang.Math.min(hi, v));
    }
}
