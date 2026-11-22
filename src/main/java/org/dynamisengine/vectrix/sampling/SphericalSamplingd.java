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
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Solid-angle sampling of area lights (double precision): directions uniformly distributed over the spherical
 * triangle (Arvo 1995) or spherical rectangle (Ureña, Fajardo and King 2013) that a planar light subtends from a
 * shading point. The density of a direction inside the region is {@code 1 / solidAngle}.
 *
 * @since 2.1.0
 */
public final class SphericalSamplingd {
    private SphericalSamplingd() {
    }

    /**
     * Solid angle of the spherical triangle with unit-vector vertices {@code a, b, c} (Van Oosterom and Strackee
     * 1983): {@code 2 atan2(|a·(b×c)|, 1 + a·b + b·c + c·a)}.
     */
    public static double solidAngleTriangle(Vector3dc a, Vector3dc b, Vector3dc c) {
        double tx = b.y() * c.z() - b.z() * c.y();
        double ty = b.z() * c.x() - b.x() * c.z();
        double tz = b.x() * c.y() - b.y() * c.x();
        double triple = java.lang.Math.abs(a.x() * tx + a.y() * ty + a.z() * tz);
        double denom = 1.0 + dot(a, b) + dot(b, c) + dot(c, a);
        return 2.0 * AccurateMath.atan2(triple, denom);
    }

    /**
     * Uniform direction inside the spherical triangle with unit-vector vertices {@code a, b, c} (Arvo 1995).
     * Returns a unit vector; its density is {@code 1 / solidAngleTriangle(a, b, c)}. {@code dest} may alias an input.
     */
    public static Vector3d sampleTriangle(Vector3dc a, Vector3dc b, Vector3dc c, double u1, double u2, Vector3d dest) {
        double ax = a.x(), ay = a.y(), az = a.z();
        double bx = b.x(), by = b.y(), bz = b.z();
        double cx = c.x(), cy = c.y(), cz = c.z();
        // internal angles at a, b, c and side length c (between a and b)
        double alpha = angleAt(ax, ay, az, bx, by, bz, cx, cy, cz);
        double beta = angleAt(bx, by, bz, cx, cy, cz, ax, ay, az);
        double gamma = angleAt(cx, cy, cz, ax, ay, az, bx, by, bz);
        double area = alpha + beta + gamma - java.lang.Math.PI;
        double areaHat = u1 * area;
        double s = AccurateMath.sin(areaHat - alpha);
        double t = AccurateMath.cos(areaHat - alpha);
        double cosAlpha = AccurateMath.cos(alpha);
        double sinAlpha = AccurateMath.sin(alpha);
        double cosC = ax * bx + ay * by + az * bz;
        double uu = t - cosAlpha;
        double vv = s + sinAlpha * cosC;
        double q = ((vv * t - uu * s) * cosAlpha - vv) / ((vv * s + uu * t) * sinAlpha);
        q = java.lang.Math.max(-1.0, java.lang.Math.min(1.0, q));
        // c' = q a + sqrt(1 - q²) normalize(c - (c·a) a)
        double ca = cx * ax + cy * ay + cz * az;
        double ox = cx - ca * ax, oy = cy - ca * ay, oz = cz - ca * az;
        double on = invLength(ox, oy, oz);
        double sq = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - q * q));
        double chx = q * ax + sq * ox * on, chy = q * ay + sq * oy * on, chz = q * az + sq * oz * on;
        // point on the arc from b to c'
        double cb = chx * bx + chy * by + chz * bz;
        double z = 1.0 - u2 * (1.0 - cb);
        double px = chx - cb * bx, py = chy - cb * by, pz = chz - cb * bz;
        double pn = invLength(px, py, pz);
        double sz = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - z * z));
        return dest.set(z * bx + sz * px * pn, z * by + sz * py * pn, z * bz + sz * pz * pn);
    }

    /** Internal angle at vertex p of the spherical triangle (p, q, r). */
    private static double angleAt(double px, double py, double pz, double qx, double qy, double qz, double rx, double ry,
            double rz) {
        // tangent directions at p towards q and r: (q - (q·p) p) and (r - (r·p) p)
        double qp = qx * px + qy * py + qz * pz;
        double rp = rx * px + ry * py + rz * pz;
        double tqx = qx - qp * px, tqy = qy - qp * py, tqz = qz - qp * pz;
        double trx = rx - rp * px, try_ = ry - rp * py, trz = rz - rp * pz;
        double cos = (tqx * trx + tqy * try_ + tqz * trz) * invLength(tqx, tqy, tqz) * invLength(trx, try_, trz);
        return AccurateMath.acos(java.lang.Math.max(-1.0, java.lang.Math.min(1.0, cos)));
    }

    /**
     * Solid angle subtended from the origin by the rectangle with corner {@code s} and edges {@code ex}, {@code ey}
     * (orthogonal), as in Ureña et al.
     */
    public static double solidAngleRectangle(Vector3dc s, Vector3dc ex, Vector3dc ey) {
        double exl = length(ex.x(), ex.y(), ex.z());
        double eyl = length(ey.x(), ey.y(), ey.z());
        double x0 = (s.x() * ex.x() + s.y() * ex.y() + s.z() * ex.z()) / exl;
        double y0 = (s.x() * ey.x() + s.y() * ey.y() + s.z() * ey.z()) / eyl;
        double zx = ex.y() * ey.z() - ex.z() * ey.y();
        double zy = ex.z() * ey.x() - ex.x() * ey.z();
        double zz = ex.x() * ey.y() - ex.y() * ey.x();
        double zl = length(zx, zy, zz);
        double z0 = (s.x() * zx + s.y() * zy + s.z() * zz) / zl;
        if (z0 > 0.0) {
            z0 = -z0;
        }
        double x1 = x0 + exl;
        double y1 = y0 + eyl;
        return rectangleSolidAngle(x0, y0, x1, y1, z0);
    }

    private static double rectangleSolidAngle(double x0, double y0, double x1, double y1, double z0) {
        // normals of the four planes through the origin and each edge, and the angles between them
        double n0x = 0, n0y = z0, n0z = -y0;
        double n1x = -z0, n1y = 0, n1z = x1;
        double n2x = 0, n2y = -z0, n2z = y1;
        double n3x = z0, n3y = 0, n3z = -x0;
        double i0 = invLength(n0x, n0y, n0z), i1 = invLength(n1x, n1y, n1z);
        double i2 = invLength(n2x, n2y, n2z), i3 = invLength(n3x, n3y, n3z);
        double g0 = acos(-(n0x * n1x + n0y * n1y + n0z * n1z) * i0 * i1);
        double g1 = acos(-(n1x * n2x + n1y * n2y + n1z * n2z) * i1 * i2);
        double g2 = acos(-(n2x * n3x + n2y * n3y + n2z * n3z) * i2 * i3);
        double g3 = acos(-(n3x * n0x + n3y * n0y + n3z * n0z) * i3 * i0);
        return g0 + g1 + g2 + g3 - 2.0 * java.lang.Math.PI;
    }

    /**
     * Uniform direction towards the rectangle with corner {@code s} and orthogonal edges {@code ex}, {@code ey}
     * (relative to the shading point) by the method of Ureña, Fajardo and King (2013). Returns a unit vector; its
     * density is {@code 1 / solidAngleRectangle(s, ex, ey)}.
     */
    public static Vector3d sampleRectangle(Vector3dc s, Vector3dc ex, Vector3dc ey, double u1, double u2, Vector3d dest) {
        double exl = length(ex.x(), ex.y(), ex.z());
        double eyl = length(ey.x(), ey.y(), ey.z());
        // local frame: x along ex, y along ey, z along their normal facing away from the origin
        double xx = ex.x() / exl, xy = ex.y() / exl, xz = ex.z() / exl;
        double yx = ey.x() / eyl, yy = ey.y() / eyl, yz = ey.z() / eyl;
        double zx = xy * yz - xz * yy, zy = xz * yx - xx * yz, zz = xx * yy - xy * yx;
        double sx = s.x(), sy = s.y(), sz = s.z();
        double z0 = sx * zx + sy * zy + sz * zz;
        if (z0 > 0.0) {
            zx = -zx;
            zy = -zy;
            zz = -zz;
            z0 = -z0;
        }
        double x0 = sx * xx + sy * xy + sz * xz;
        double y0 = sx * yx + sy * yy + sz * yz;
        double x1 = x0 + exl;
        double y1 = y0 + eyl;
        double n0x = 0, n0y = z0, n0z = -y0;
        double n1x = -z0, n1y = 0, n1z = x1;
        double n2x = 0, n2y = -z0, n2z = y1;
        double n3x = z0, n3y = 0, n3z = -x0;
        double i0 = invLength(n0x, n0y, n0z), i1 = invLength(n1x, n1y, n1z);
        double i2 = invLength(n2x, n2y, n2z), i3 = invLength(n3x, n3y, n3z);
        n0y *= i0;
        n0z *= i0;
        n2y *= i2;
        n2z *= i2;
        double g0 = acos(-(n0x * n1x + n0y * n1y + n0z * n1z) * i1);
        double g1 = acos(-(n1x * n2x + n1y * n2y + n1z * n2z) * i1);
        double g2 = acos(-(n2x * n3x + n2y * n3y + n2z * n3z) * i3);
        double g3 = acos(-(n3x * n0x + n3y * n0y + n3z * n0z) * i3);
        double b0 = n0z;
        double b1 = n2z;
        double k = 2.0 * java.lang.Math.PI - g2 - g3;
        double area = g0 + g1 - k;
        // sample the x coordinate
        double au = u1 * area + k;
        double fu = (AccurateMath.cos(au) * b0 - b1) / AccurateMath.sin(au);
        double cu = java.lang.Math.copySign(1.0, fu) / java.lang.Math.sqrt(fu * fu + b0 * b0);
        cu = java.lang.Math.max(-1.0, java.lang.Math.min(1.0, cu));
        double xu = -(cu * z0) / java.lang.Math.sqrt(java.lang.Math.max(1e-30, 1.0 - cu * cu));
        xu = java.lang.Math.max(x0, java.lang.Math.min(x1, xu));
        // sample the y coordinate
        double d = java.lang.Math.sqrt(xu * xu + z0 * z0);
        double h0 = y0 / java.lang.Math.sqrt(d * d + y0 * y0);
        double h1 = y1 / java.lang.Math.sqrt(d * d + y1 * y1);
        double hv = h0 + u2 * (h1 - h0);
        double hv2 = hv * hv;
        double yv = hv2 < 1.0 - 1e-6 ? (hv * d) / java.lang.Math.sqrt(1.0 - hv2) : y1;
        double px = xu * xx + yv * yx + z0 * zx;
        double py = xu * xy + yv * yy + z0 * zy;
        double pz = xu * xz + yv * yz + z0 * zz;
        double ip = invLength(px, py, pz);
        return dest.set(px * ip, py * ip, pz * ip);
    }

    private static double acos(double x) {
        return AccurateMath.acos(java.lang.Math.max(-1.0, java.lang.Math.min(1.0, x)));
    }

    private static double dot(Vector3dc a, Vector3dc b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }

    private static double length(double x, double y, double z) {
        return java.lang.Math.sqrt(x * x + y * y + z * z);
    }

    private static double invLength(double x, double y, double z) {
        return 1.0 / length(x, y, z);
    }
}
