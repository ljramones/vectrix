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

import org.vectrix.core.Vector3f;
import org.vectrix.core.Vector3fc;

/**
 * LTC evaluation helpers for area-light form factors.
 * <p>
 * Inputs are expected in the local LTC shading frame (typically normal-aligned space).
 * Polygon and rectangle form-factor routines expect unit direction vectors from shading
 * point to light-shape vertices.
 */
public final class LtcEvalf {
    private static final float INV_TWO_PI = (float) (1.0 / (2.0 * java.lang.Math.PI));

    private LtcEvalf() {
    }

    /**
     * Multiply direction by 3x3 matrix (row-major).
     */
    public static Vector3f transformDirection(float[] m3, Vector3fc dir, Vector3f dest) {
        if (m3 == null || m3.length < 9) {
            throw new IllegalArgumentException("m3");
        }
        float x = dir.x();
        float y = dir.y();
        float z = dir.z();
        return dest.set(
            m3[0] * x + m3[1] * y + m3[2] * z,
            m3[3] * x + m3[4] * y + m3[5] * z,
            m3[6] * x + m3[7] * y + m3[8] * z);
    }

    /**
     * Horizon clipping helper for local-space z.
     */
    public static float horizonClip(float z) {
        return java.lang.Math.max(0.0f, z);
    }

    /**
     * Polygon (rectangle) form factor using edge integral on unit direction vectors.
     */
    public static float formFactorRect(Vector3fc v0, Vector3fc v1, Vector3fc v2, Vector3fc v3) {
        Vector3f sum = new Vector3f();
        integrateEdge(v0, v1, sum);
        integrateEdge(v1, v2, sum);
        integrateEdge(v2, v3, sum);
        integrateEdge(v3, v0, sum);
        return clamp01(sum.z * INV_TWO_PI);
    }

    /**
     * Rectangle form factor with horizon clipping applied to vertex directions before integration.
     *
     * @since 1.0.0
     */
    public static float formFactorRectClipped(Vector3fc v0, Vector3fc v1, Vector3fc v2, Vector3fc v3) {
        return formFactorPolygonClipped(new Vector3fc[] {v0, v1, v2, v3}, 4);
    }

    /**
     * Polygon form factor with simple horizon clipping in local space.
     *
     * @param vertices unit or near-unit direction vectors to polygon vertices
     * @param count number of vertices to integrate (>=3)
     * @return clipped form factor in [0,1]
     * @since 1.0.0
     */
    public static float formFactorPolygonClipped(Vector3fc[] vertices, int count) {
        if (vertices == null || count < 3 || count > vertices.length) {
            throw new IllegalArgumentException("vertices/count");
        }
        Vector3f[] clipped = new Vector3f[count];
        for (int i = 0; i < count; i++) {
            Vector3fc v = vertices[i];
            clipped[i] = new Vector3f(v.x(), v.y(), horizonClip(v.z()));
            if (clipped[i].lengthSquared() > 1E-8f) {
                clipped[i].normalize();
            }
        }
        Vector3f sum = new Vector3f();
        for (int i = 0; i < count; i++) {
            integrateEdge(clipped[i], clipped[(i + 1) % count], sum);
        }
        return clamp01(sum.z * INV_TWO_PI);
    }

    /**
     * Disc form factor approximation from center direction and disc angular radius.
     */
    public static float formFactorDisc(float cosThetaCenter, float discAngularRadius) {
        float omega = (float) (2.0 * java.lang.Math.PI * (1.0 - java.lang.Math.cos(java.lang.Math.max(0.0f, discAngularRadius))));
        return clamp01(horizonClip(cosThetaCenter) * omega * INV_TWO_PI);
    }

    /**
     * Tube/capsule form factor approximation from projected angular width and half-length.
     */
    public static float formFactorTube(float cosThetaCenter, float angularRadius, float angularHalfLength) {
        float width = 2.0f * java.lang.Math.max(0.0f, angularRadius);
        float length = 2.0f * java.lang.Math.max(0.0f, angularHalfLength);
        float omegaApprox = width * length;
        return clamp01(horizonClip(cosThetaCenter) * omegaApprox * INV_TWO_PI);
    }

    private static void integrateEdge(Vector3fc a, Vector3fc b, Vector3f accum) {
        float cx = a.y() * b.z() - a.z() * b.y();
        float cy = a.z() * b.x() - a.x() * b.z();
        float cz = a.x() * b.y() - a.y() * b.x();
        float len = (float) java.lang.Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1E-7f) {
            return;
        }
        float d = clamp(a.x() * b.x() + a.y() * b.y() + a.z() * b.z(), -1.0f, 1.0f);
        float angle = (float) java.lang.Math.acos(d);
        float s = angle / len;
        accum.x += cx * s;
        accum.y += cy * s;
        accum.z += cz * s;
    }

    private static float clamp01(float v) {
        return java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, v));
    }

    private static float clamp(float v, float lo, float hi) {
        return java.lang.Math.max(lo, java.lang.Math.min(hi, v));
    }
}
