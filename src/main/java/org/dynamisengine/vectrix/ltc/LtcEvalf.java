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
package org.dynamisengine.vectrix.ltc;

import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Form factors of area lights for linearly transformed cosines (LTC, Heitz et al. 2016), single precision.
 * <p>
 * Directions are in the local shading frame, with the surface normal along {@code +z}: typically the vectors from
 * the shading point to the light's vertices, transformed by the matrix read from an {@link LtcTablef} table with
 * {@link #transformDirection}. The form-factor routines normalize each vertex direction themselves, so the
 * transformed vectors need not be unit length. Results are the cosine-weighted form factor, in {@code [0, 1]}.
 */
public final class LtcEvalf {
    private static final float INV_TWO_PI = (float) (1.0 / (2.0 * java.lang.Math.PI));
    private static final float INV_PI = (float) (1.0 / java.lang.Math.PI);

    private LtcEvalf() {
    }

    /**
     * Multiplies a direction by a row-major 3×3 matrix: {@code dest.x = m3[0] x + m3[1] y + m3[2] z}, and so on.
     * The result is not normalized. {@code dest} may be {@code dir}.
     *
     * @param m3 the matrix, row-major, at least 9 elements, starting at index 0
     * @param dir the direction to transform
     * @param dest receives the transformed direction
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code m3} is null or shorter than 9
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
     * Clamps a local-frame {@code z} (a cosine to the normal) to the upper hemisphere: {@code max(0, z)}.
     *
     * @param z the value to clamp
     * @return {@code z}, or 0 if it is negative
     */
    public static float horizonClip(float z) {
        return java.lang.Math.max(0.0f, z);
    }

    /**
     * Cosine-weighted form factor of a spherical polygon (Lambert's edge integral, as in Heitz et al. 2016).
     * Vertices are unit directions from the shading point. The light is one-sided: its front face has
     * counter-clockwise winding about +z, meaning {@code (v_i x v_(i+1)).z > 0} for a small light at the zenith.
     * A back-facing polygon yields 0. No horizon clipping is applied; use {@link #formFactorRectClipped} for
     * lights that may cross the horizon. Does not allocate.
     *
     * @param v0 the direction to the first vertex
     * @param v1 the direction to the second vertex
     * @param v2 the direction to the third vertex
     * @param v3 the direction to the fourth vertex
     * @return the form factor, clamped to {@code [0, 1]}
     */
    public static float formFactorRect(Vector3fc v0, Vector3fc v1, Vector3fc v2, Vector3fc v3) {
        float z = edgeZ(v0.x(), v0.y(), v0.z(), v1.x(), v1.y(), v1.z())
                + edgeZ(v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z())
                + edgeZ(v2.x(), v2.y(), v2.z(), v3.x(), v3.y(), v3.z())
                + edgeZ(v3.x(), v3.y(), v3.z(), v0.x(), v0.y(), v0.z());
        return clamp01(z * INV_TWO_PI);
    }

    /**
     * Returns the form factor of a quadrilateral light clipped to the upper hemisphere ({@code z >= 0}), as
     * {@link #formFactorPolygonClipped} with four vertices. Allocates a small clipping object per call.
     *
     * @param v0 the direction to the first vertex
     * @param v1 the direction to the second vertex
     * @param v2 the direction to the third vertex
     * @param v3 the direction to the fourth vertex
     * @return the clipped form factor, in {@code [0, 1]}; 0 if the light is entirely below the horizon
     * @since 1.0.0
     */
    public static float formFactorRectClipped(Vector3fc v0, Vector3fc v1, Vector3fc v2, Vector3fc v3) {
        Clip c = new Clip();
        c.add(v0); c.add(v1); c.add(v2); c.add(v3);
        return c.finish();
    }

    /**
     * Polygon form factor, clipped to the upper hemisphere ({@code z >= 0}) before integration. Each edge
     * crossing the horizon is cut where its chord meets the plane {@code z = 0}. That point lies on the edge's
     * great circle, so the clipped spherical polygon is exact. Winding and one-sidedness are as in
     * {@link #formFactorRect}. Allocates a small clipping object per call.
     *
     * @param vertices direction vectors to the polygon vertices (need not be unit length), in order
     * @param count number of vertices to integrate, starting at index 0, at least 3
     * @return clipped form factor in [0,1]; 0 if the light is entirely below the horizon
     * @throws IllegalArgumentException if {@code vertices} is null, {@code count} is less than 3 or greater than
     *         {@code vertices.length}
     * @since 1.0.0
     */
    public static float formFactorPolygonClipped(Vector3fc[] vertices, int count) {
        if (vertices == null || count < 3 || count > vertices.length) {
            throw new IllegalArgumentException("vertices/count");
        }
        Clip c = new Clip();
        for (int i = 0; i < count; i++) {
            c.add(vertices[i]);
        }
        return c.finish();
    }

    /**
     * Form factor of a uniformly emitting sphere or disc that subtends a cap of angular radius
     * {@code discAngularRadius}, centred {@code acos(cosThetaCenter)} from the normal. This is exact for a
     * spherical cap, including caps that cross the horizon (Snyder 1996; Lagarde and de Rousiers 2014). Above
     * the horizon it reduces to {@code cosTheta * sin^2(alpha)}.
     *
     * @param cosThetaCenter cosine of the angle between the normal and the cap's centre, clamped to {@code [−1, 1]}
     * @param discAngularRadius the cap's angular radius in radians, clamped to {@code [0, π/2]}
     * @return the form factor, in {@code [0, 1]}; 0 for a zero radius or a cap entirely below the horizon
     */
    public static float formFactorDisc(float cosThetaCenter, float discAngularRadius) {
        double alpha = java.lang.Math.min(java.lang.Math.max(0.0, discAngularRadius), java.lang.Math.PI / 2);
        double sinSigma = org.dynamisengine.vectrix.core.AccurateMath.sin(alpha);
        double sinSigmaSqr = sinSigma * sinSigma;
        if (sinSigmaSqr <= 0.0) {
            return 0.0f;
        }
        double cosTheta = java.lang.Math.max(-1.0, java.lang.Math.min(1.0, cosThetaCenter));
        double sinTheta = java.lang.Math.sqrt(1.0 - cosTheta * cosTheta);
        double e;
        if (cosTheta * cosTheta >= sinSigmaSqr || sinTheta < 1e-9) {
            // cap entirely above (or below) the horizon, or centred on the normal
            e = sinSigmaSqr * java.lang.Math.max(0.0, cosTheta);
        } else {
            double x = java.lang.Math.sqrt(1.0 / sinSigmaSqr - 1.0);
            double y = -x * cosTheta / sinTheta;
            double sinThetaSqrtY = sinTheta * java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - y * y));
            e = ((cosTheta * org.dynamisengine.vectrix.core.AccurateMath.acos(y) - x * sinThetaSqrtY) * sinSigmaSqr
                    + org.dynamisengine.vectrix.core.AccurateMath.atan2(sinThetaSqrtY, x)) / java.lang.Math.PI;
        }
        return clamp01((float) e);
    }

    /**
     * Tube/capsule form factor, approximated as {@code cosTheta * omega / pi} with the small-angle solid angle
     * {@code omega ~ (2 * angularRadius) * (2 * angularHalfLength)}. It is accurate only for small tubes that do
     * not cross the horizon.
     *
     * @param cosThetaCenter cosine of the angle between the normal and the tube's centre; negative values give 0
     * @param angularRadius the tube's angular radius in radians; negative values count as 0
     * @param angularHalfLength half the tube's angular length in radians; negative values count as 0
     * @return the approximate form factor, clamped to {@code [0, 1]}
     */
    public static float formFactorTube(float cosThetaCenter, float angularRadius, float angularHalfLength) {
        float width = 2.0f * java.lang.Math.max(0.0f, angularRadius);
        float length = 2.0f * java.lang.Math.max(0.0f, angularHalfLength);
        float omegaApprox = width * length;
        return clamp01(horizonClip(cosThetaCenter) * omegaApprox * INV_PI);
    }

    /** z-component of Lambert's edge integral for the arc a->b (inputs need not be normalised). */
    private static float edgeZ(float ax, float ay, float az, float bx, float by, float bz) {
        float la = (float) java.lang.Math.sqrt(ax * ax + ay * ay + az * az);
        float lb = (float) java.lang.Math.sqrt(bx * bx + by * by + bz * bz);
        if (la < 1E-7f || lb < 1E-7f) {
            return 0.0f;
        }
        ax /= la; ay /= la; az /= la;
        bx /= lb; by /= lb; bz /= lb;
        float cx = ay * bz - az * by;
        float cy = az * bx - ax * bz;
        float cz = ax * by - ay * bx;
        float len = (float) java.lang.Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1E-7f) {
            return 0.0f;
        }
        float angle = (float) org.dynamisengine.vectrix.core.AccurateMath.atan2(len, ax * bx + ay * by + az * bz);
        return cz * (angle / len);
    }

    /** Streaming Sutherland-Hodgman clip against z >= 0 that accumulates the edge integral of the output. */
    private static final class Clip {
        private float firstX, firstY, firstZ, prevInX, prevInY, prevInZ, outX, outY, outZ, outFirstX, outFirstY, outFirstZ;
        private boolean hasIn, hasOut;
        private int emitted;
        private float sum;

        void add(Vector3fc v) {
            float x = v.x(), y = v.y(), z = v.z();
            if (!hasIn) {
                firstX = x; firstY = y; firstZ = z;
                hasIn = true;
            } else {
                edge(prevInX, prevInY, prevInZ, x, y, z);
            }
            prevInX = x; prevInY = y; prevInZ = z;
        }

        float finish() {
            if (hasIn) {
                edge(prevInX, prevInY, prevInZ, firstX, firstY, firstZ);
            }
            if (emitted >= 3) {
                sum += edgeZ(outX, outY, outZ, outFirstX, outFirstY, outFirstZ);
            }
            return emitted >= 3 ? clamp01(sum * INV_TWO_PI) : 0.0f;
        }

        private void edge(float ax, float ay, float az, float bx, float by, float bz) {
            boolean aIn = az >= 0.0f, bIn = bz >= 0.0f;
            if (aIn != bIn) {
                float t = az / (az - bz);
                emit(ax + t * (bx - ax), ay + t * (by - ay), 0.0f);
            }
            if (bIn) {
                emit(bx, by, bz);
            }
        }

        private void emit(float x, float y, float z) {
            if (hasOut) {
                sum += edgeZ(outX, outY, outZ, x, y, z);
            } else {
                outFirstX = x; outFirstY = y; outFirstZ = z;
                hasOut = true;
            }
            outX = x; outY = y; outZ = z;
            emitted++;
        }
    }

    private static float clamp01(float v) {
        return java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, v));
    }

    private static float clamp(float v, float lo, float hi) {
        return java.lang.Math.max(lo, java.lang.Math.min(hi, v));
    }
}
