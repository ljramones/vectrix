/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
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
package org.dynamisengine.vectrix.sampling;

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Solid-angle sampling of area lights (single precision): directions uniformly distributed over the spherical
 * triangle (Arvo 1995) or spherical rectangle (Ureña, Fajardo and King 2013) that a planar light subtends from a
 * shading point. The density of a direction inside the region is {@code 1 / solidAngle}.
 *
 * @since 2.1.0
 */
public final class SphericalSamplingf {
    private SphericalSamplingf() {
    }

    /**
     * Returns the solid angle of the spherical triangle with unit-vector vertices {@code a, b, c} (Van Oosterom and
     * Strackee 1983): {@code 2 atan2(|a·(b×c)|, 1 + a·b + b·c + c·a)}.
     *
     * @param a the first vertex, a unit vector
     * @param b the second vertex, a unit vector
     * @param c the third vertex, a unit vector
     * @return the solid angle in steradians, in {@code [0, 2π]}
     */
    public static float solidAngleTriangle(Vector3fc a, Vector3fc b, Vector3fc c) {
        float tx = b.y() * c.z() - b.z() * c.y();
        float ty = b.z() * c.x() - b.x() * c.z();
        float tz = b.x() * c.y() - b.y() * c.x();
        float triple = java.lang.Math.abs(a.x() * tx + a.y() * ty + a.z() * tz);
        float denom = 1.0f + dot(a, b) + dot(b, c) + dot(c, a);
        return 2.0f * (float) AccurateMath.atan2(triple, denom);
    }

    /**
     * Returns a uniformly distributed direction inside the spherical triangle with unit-vector vertices
     * {@code a, b, c} (Arvo 1995). The density of the direction is {@code 1 / solidAngleTriangle(a, b, c)}. A
     * degenerate triangle (zero area) gives NaN components.
     *
     * @param a the first vertex, a unit vector
     * @param b the second vertex, a unit vector
     * @param c the third vertex, a unit vector
     * @param u1 the first uniform variate, in {@code [0, 1)}; it selects the sub-triangle area
     * @param u2 the second uniform variate, in {@code [0, 1)}; it selects the point along the arc from {@code b}
     * @param dest receives the unit direction; may alias an input
     * @return {@code dest}
     */
    public static Vector3f sampleTriangle(Vector3fc a, Vector3fc b, Vector3fc c, float u1, float u2, Vector3f dest) {
        float ax = a.x(), ay = a.y(), az = a.z();
        float bx = b.x(), by = b.y(), bz = b.z();
        float cx = c.x(), cy = c.y(), cz = c.z();
        // internal angles at a, b, c and side length c (between a and b)
        float alpha = angleAt(ax, ay, az, bx, by, bz, cx, cy, cz);
        float beta = angleAt(bx, by, bz, cx, cy, cz, ax, ay, az);
        float gamma = angleAt(cx, cy, cz, ax, ay, az, bx, by, bz);
        float area = alpha + beta + gamma - (float) java.lang.Math.PI;
        float areaHat = u1 * area;
        float s = (float) AccurateMath.sin(areaHat - alpha);
        float t = (float) AccurateMath.cos(areaHat - alpha);
        float cosAlpha = (float) AccurateMath.cos(alpha);
        float sinAlpha = (float) AccurateMath.sin(alpha);
        float cosC = ax * bx + ay * by + az * bz;
        float uu = t - cosAlpha;
        float vv = s + sinAlpha * cosC;
        float q = ((vv * t - uu * s) * cosAlpha - vv) / ((vv * s + uu * t) * sinAlpha);
        q = java.lang.Math.max(-1.0f, java.lang.Math.min(1.0f, q));
        // c' = q a + sqrt(1 - q²) normalize(c - (c·a) a)
        float ca = cx * ax + cy * ay + cz * az;
        float ox = cx - ca * ax, oy = cy - ca * ay, oz = cz - ca * az;
        float on = invLength(ox, oy, oz);
        float sq = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0f, 1.0f - q * q));
        float chx = q * ax + sq * ox * on, chy = q * ay + sq * oy * on, chz = q * az + sq * oz * on;
        // point on the arc from b to c'
        float cb = chx * bx + chy * by + chz * bz;
        float z = 1.0f - u2 * (1.0f - cb);
        float px = chx - cb * bx, py = chy - cb * by, pz = chz - cb * bz;
        float pn = invLength(px, py, pz);
        float sz = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0f, 1.0f - z * z));
        return dest.set(z * bx + sz * px * pn, z * by + sz * py * pn, z * bz + sz * pz * pn);
    }

    /** Internal angle at vertex p of the spherical triangle (p, q, r). */
    private static float angleAt(float px, float py, float pz, float qx, float qy, float qz, float rx, float ry,
            float rz) {
        // tangent directions at p towards q and r: (q - (q·p) p) and (r - (r·p) p)
        float qp = qx * px + qy * py + qz * pz;
        float rp = rx * px + ry * py + rz * pz;
        float tqx = qx - qp * px, tqy = qy - qp * py, tqz = qz - qp * pz;
        float trx = rx - rp * px, try_ = ry - rp * py, trz = rz - rp * pz;
        float cos = (tqx * trx + tqy * try_ + tqz * trz) * invLength(tqx, tqy, tqz) * invLength(trx, try_, trz);
        return (float) AccurateMath.acos(java.lang.Math.max(-1.0f, java.lang.Math.min(1.0f, cos)));
    }

    /**
     * Returns the solid angle subtended from the origin by the rectangle with corner {@code s} and orthogonal edges
     * {@code ex}, {@code ey}, as in Ureña, Fajardo and King (2013). Place the shading point at the origin by passing
     * {@code s} relative to it. The side of the rectangle that faces the origin does not matter.
     *
     * @param s the rectangle's corner, relative to the shading point
     * @param ex the first edge vector, orthogonal to {@code ey}
     * @param ey the second edge vector, orthogonal to {@code ex}
     * @return the solid angle in steradians
     */
    public static float solidAngleRectangle(Vector3fc s, Vector3fc ex, Vector3fc ey) {
        float exl = length(ex.x(), ex.y(), ex.z());
        float eyl = length(ey.x(), ey.y(), ey.z());
        float x0 = (s.x() * ex.x() + s.y() * ex.y() + s.z() * ex.z()) / exl;
        float y0 = (s.x() * ey.x() + s.y() * ey.y() + s.z() * ey.z()) / eyl;
        float zx = ex.y() * ey.z() - ex.z() * ey.y();
        float zy = ex.z() * ey.x() - ex.x() * ey.z();
        float zz = ex.x() * ey.y() - ex.y() * ey.x();
        float zl = length(zx, zy, zz);
        float z0 = (s.x() * zx + s.y() * zy + s.z() * zz) / zl;
        if (z0 > 0.0f) {
            z0 = -z0;
        }
        float x1 = x0 + exl;
        float y1 = y0 + eyl;
        return rectangleSolidAngle(x0, y0, x1, y1, z0);
    }

    private static float rectangleSolidAngle(float x0, float y0, float x1, float y1, float z0) {
        // normals of the four planes through the origin and each edge, and the angles between them
        float n0x = 0, n0y = z0, n0z = -y0;
        float n1x = -z0, n1y = 0, n1z = x1;
        float n2x = 0, n2y = -z0, n2z = y1;
        float n3x = z0, n3y = 0, n3z = -x0;
        float i0 = invLength(n0x, n0y, n0z), i1 = invLength(n1x, n1y, n1z);
        float i2 = invLength(n2x, n2y, n2z), i3 = invLength(n3x, n3y, n3z);
        float g0 = acos(-(n0x * n1x + n0y * n1y + n0z * n1z) * i0 * i1);
        float g1 = acos(-(n1x * n2x + n1y * n2y + n1z * n2z) * i1 * i2);
        float g2 = acos(-(n2x * n3x + n2y * n3y + n2z * n3z) * i2 * i3);
        float g3 = acos(-(n3x * n0x + n3y * n0y + n3z * n0z) * i3 * i0);
        return g0 + g1 + g2 + g3 - 2.0f * (float) java.lang.Math.PI;
    }

    /**
     * Returns a uniformly distributed direction towards the rectangle with corner {@code s} and orthogonal edges
     * {@code ex}, {@code ey}, by the method of Ureña, Fajardo and King (2013). The density of the direction is
     * {@code 1 / solidAngleRectangle(s, ex, ey)}.
     *
     * @param s the rectangle's corner, relative to the shading point
     * @param ex the first edge vector, orthogonal to {@code ey}
     * @param ey the second edge vector, orthogonal to {@code ex}
     * @param u1 the first uniform variate, in {@code [0, 1)}; it selects the coordinate along {@code ex}
     * @param u2 the second uniform variate, in {@code [0, 1)}; it selects the coordinate along {@code ey}
     * @param dest receives the unit direction from the shading point; may alias an input
     * @return {@code dest}
     */
    public static Vector3f sampleRectangle(Vector3fc s, Vector3fc ex, Vector3fc ey, float u1, float u2, Vector3f dest) {
        float exl = length(ex.x(), ex.y(), ex.z());
        float eyl = length(ey.x(), ey.y(), ey.z());
        // local frame: x along ex, y along ey, z along their normal facing away from the origin
        float xx = ex.x() / exl, xy = ex.y() / exl, xz = ex.z() / exl;
        float yx = ey.x() / eyl, yy = ey.y() / eyl, yz = ey.z() / eyl;
        float zx = xy * yz - xz * yy, zy = xz * yx - xx * yz, zz = xx * yy - xy * yx;
        float sx = s.x(), sy = s.y(), sz = s.z();
        float z0 = sx * zx + sy * zy + sz * zz;
        if (z0 > 0.0f) {
            zx = -zx;
            zy = -zy;
            zz = -zz;
            z0 = -z0;
        }
        float x0 = sx * xx + sy * xy + sz * xz;
        float y0 = sx * yx + sy * yy + sz * yz;
        float x1 = x0 + exl;
        float y1 = y0 + eyl;
        float n0x = 0, n0y = z0, n0z = -y0;
        float n1x = -z0, n1y = 0, n1z = x1;
        float n2x = 0, n2y = -z0, n2z = y1;
        float n3x = z0, n3y = 0, n3z = -x0;
        float i0 = invLength(n0x, n0y, n0z), i1 = invLength(n1x, n1y, n1z);
        float i2 = invLength(n2x, n2y, n2z), i3 = invLength(n3x, n3y, n3z);
        n0y *= i0;
        n0z *= i0;
        n2y *= i2;
        n2z *= i2;
        float g0 = acos(-(n0x * n1x + n0y * n1y + n0z * n1z) * i1);
        float g1 = acos(-(n1x * n2x + n1y * n2y + n1z * n2z) * i1);
        float g2 = acos(-(n2x * n3x + n2y * n3y + n2z * n3z) * i3);
        float g3 = acos(-(n3x * n0x + n3y * n0y + n3z * n0z) * i3);
        float b0 = n0z;
        float b1 = n2z;
        float k = 2.0f * (float) java.lang.Math.PI - g2 - g3;
        float area = g0 + g1 - k;
        // sample the x coordinate
        float au = u1 * area + k;
        float fu = ((float) AccurateMath.cos(au) * b0 - b1) / (float) AccurateMath.sin(au);
        float cu = java.lang.Math.copySign(1.0f, fu) / (float) java.lang.Math.sqrt(fu * fu + b0 * b0);
        cu = java.lang.Math.max(-1.0f, java.lang.Math.min(1.0f, cu));
        float xu = -(cu * z0) / (float) java.lang.Math.sqrt(java.lang.Math.max(1e-30f, 1.0f - cu * cu));
        xu = java.lang.Math.max(x0, java.lang.Math.min(x1, xu));
        // sample the y coordinate
        float d = (float) java.lang.Math.sqrt(xu * xu + z0 * z0);
        float h0 = y0 / (float) java.lang.Math.sqrt(d * d + y0 * y0);
        float h1 = y1 / (float) java.lang.Math.sqrt(d * d + y1 * y1);
        float hv = h0 + u2 * (h1 - h0);
        float hv2 = hv * hv;
        float yv = hv2 < 1.0f - 1e-6f ? (hv * d) / (float) java.lang.Math.sqrt(1.0f - hv2) : y1;
        float px = xu * xx + yv * yx + z0 * zx;
        float py = xu * xy + yv * yy + z0 * zy;
        float pz = xu * xz + yv * yz + z0 * zz;
        float ip = invLength(px, py, pz);
        return dest.set(px * ip, py * ip, pz * ip);
    }

    private static float acos(float x) {
        return (float) AccurateMath.acos(java.lang.Math.max(-1.0f, java.lang.Math.min(1.0f, x)));
    }

    private static float dot(Vector3fc a, Vector3fc b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }

    private static float length(float x, float y, float z) {
        return (float) java.lang.Math.sqrt(x * x + y * y + z * z);
    }

    private static float invLength(float x, float y, float z) {
        return 1.0f / length(x, y, z);
    }
}
