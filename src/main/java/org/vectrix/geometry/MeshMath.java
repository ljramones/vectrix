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
package org.vectrix.geometry;

import org.vectrix.core.Epsilonf;
import org.vectrix.core.Vector2fc;
import org.vectrix.core.Vector3f;
import org.vectrix.core.Vector3fc;

/**
 * Mesh-oriented geometric helpers used by render and mesh pipelines.
 */
public final class MeshMath {
    private MeshMath() {
    }

    /**
     * Compute barycentric coordinates for point {@code p} in triangle {@code abc}.
     *
     * @return {@code dest}
     */
    public static Vector3f barycentric(Vector3fc p, Vector3fc a, Vector3fc b, Vector3fc c, Vector3f dest) {
        float v0x = b.x() - a.x();
        float v0y = b.y() - a.y();
        float v0z = b.z() - a.z();
        float v1x = c.x() - a.x();
        float v1y = c.y() - a.y();
        float v1z = c.z() - a.z();
        float v2x = p.x() - a.x();
        float v2y = p.y() - a.y();
        float v2z = p.z() - a.z();

        float d00 = dot(v0x, v0y, v0z, v0x, v0y, v0z);
        float d01 = dot(v0x, v0y, v0z, v1x, v1y, v1z);
        float d11 = dot(v1x, v1y, v1z, v1x, v1y, v1z);
        float d20 = dot(v2x, v2y, v2z, v0x, v0y, v0z);
        float d21 = dot(v2x, v2y, v2z, v1x, v1y, v1z);

        float denom = d00 * d11 - d01 * d01;
        if (Epsilonf.isZero(denom)) {
            return dest.set(Float.NaN, Float.NaN, Float.NaN);
        }

        float invDenom = 1.0f / denom;
        float v = (d11 * d20 - d01 * d21) * invDenom;
        float w = (d00 * d21 - d01 * d20) * invDenom;
        float u = 1.0f - v - w;
        return dest.set(u, v, w);
    }

    /**
     * Return whether barycentric coordinate triplet is inside or on the triangle.
     */
    public static boolean isInsideBarycentric(Vector3fc barycentric) {
        float e = Epsilonf.ABSOLUTE;
        return barycentric.x() >= -e && barycentric.y() >= -e && barycentric.z() >= -e;
    }

    /**
     * Compute closest point on triangle {@code abc} to point {@code p}.
     *
     * @return {@code dest}
     */
    public static Vector3f closestPointOnTriangle(Vector3fc p, Vector3fc a, Vector3fc b, Vector3fc c, Vector3f dest) {
        float abx = b.x() - a.x();
        float aby = b.y() - a.y();
        float abz = b.z() - a.z();
        float acx = c.x() - a.x();
        float acy = c.y() - a.y();
        float acz = c.z() - a.z();
        float apx = p.x() - a.x();
        float apy = p.y() - a.y();
        float apz = p.z() - a.z();

        float d1 = dot(abx, aby, abz, apx, apy, apz);
        float d2 = dot(acx, acy, acz, apx, apy, apz);
        if (d1 <= 0.0f && d2 <= 0.0f) {
            return dest.set(a);
        }

        float bpx = p.x() - b.x();
        float bpy = p.y() - b.y();
        float bpz = p.z() - b.z();
        float d3 = dot(abx, aby, abz, bpx, bpy, bpz);
        float d4 = dot(acx, acy, acz, bpx, bpy, bpz);
        if (d3 >= 0.0f && d4 <= d3) {
            return dest.set(b);
        }

        float vc = d1 * d4 - d3 * d2;
        if (vc <= 0.0f && d1 >= 0.0f && d3 <= 0.0f) {
            float v = d1 / (d1 - d3);
            return dest.set(a.x() + abx * v, a.y() + aby * v, a.z() + abz * v);
        }

        float cpx = p.x() - c.x();
        float cpy = p.y() - c.y();
        float cpz = p.z() - c.z();
        float d5 = dot(abx, aby, abz, cpx, cpy, cpz);
        float d6 = dot(acx, acy, acz, cpx, cpy, cpz);
        if (d6 >= 0.0f && d5 <= d6) {
            return dest.set(c);
        }

        float vb = d5 * d2 - d1 * d6;
        if (vb <= 0.0f && d2 >= 0.0f && d6 <= 0.0f) {
            float w = d2 / (d2 - d6);
            return dest.set(a.x() + acx * w, a.y() + acy * w, a.z() + acz * w);
        }

        float va = d3 * d6 - d5 * d4;
        if (va <= 0.0f && (d4 - d3) >= 0.0f && (d5 - d6) >= 0.0f) {
            float w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
            return dest.set(b.x() + (c.x() - b.x()) * w, b.y() + (c.y() - b.y()) * w, b.z() + (c.z() - b.z()) * w);
        }

        float denom = 1.0f / (va + vb + vc);
        float v = vb * denom;
        float w = vc * denom;
        return dest.set(a.x() + abx * v + acx * w, a.y() + aby * v + acy * w, a.z() + abz * v + acz * w);
    }

    /**
     * Twice the signed area of a 2D triangle.
     */
    public static float signedArea2(Vector2fc a, Vector2fc b, Vector2fc c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    /**
     * Triangle winding in 2D.
     *
     * @return {@code 1} for CCW, {@code -1} for CW, {@code 0} for degenerate
     */
    public static int winding2D(Vector2fc a, Vector2fc b, Vector2fc c) {
        float s = signedArea2(a, b, c);
        if (s > Epsilonf.ABSOLUTE) {
            return 1;
        }
        if (s < -Epsilonf.ABSOLUTE) {
            return -1;
        }
        return 0;
    }

    /**
     * Triangle area in 3D.
     */
    public static float triangleArea(Vector3fc a, Vector3fc b, Vector3fc c) {
        float abx = b.x() - a.x();
        float aby = b.y() - a.y();
        float abz = b.z() - a.z();
        float acx = c.x() - a.x();
        float acy = c.y() - a.y();
        float acz = c.z() - a.z();
        float cx = aby * acz - abz * acy;
        float cy = abz * acx - abx * acz;
        float cz = abx * acy - aby * acx;
        return 0.5f * (float) java.lang.Math.sqrt(dot(cx, cy, cz, cx, cy, cz));
    }

    private static float dot(float ax, float ay, float az, float bx, float by, float bz) {
        return ax * bx + ay * by + az * bz;
    }
}
