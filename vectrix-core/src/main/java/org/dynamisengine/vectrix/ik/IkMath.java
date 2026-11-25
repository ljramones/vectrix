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
package org.dynamisengine.vectrix.ik;

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * Double-precision helpers shared by the float and double IK kernels. Quaternions are {@code (x, y, z, w)} in
 * {@code double[4]} (or at an offset), vectors {@code double[3]}.
 */
final class IkMath {
    /** Per-thread scratch: [0] 2-vector, [1..3] quaternions, [4..6] 3-vectors. */
    static final ThreadLocal<double[][]> SCRATCH = ThreadLocal.withInitial(() -> new double[][] {new double[2],
        new double[4], new double[4], new double[4], new double[3], new double[3], new double[3]});

    private IkMath() {
    }

    /** Shortest-arc rotation taking direction {@code u} to direction {@code v} (need not be unit) into {@code q}. */
    static void rotationTo(double ux, double uy, double uz, double vx, double vy, double vz, double[] q) {
        double lu = java.lang.Math.sqrt(ux * ux + uy * uy + uz * uz), lv = java.lang.Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (lu == 0.0 || lv == 0.0) {
            q[0] = 0.0;
            q[1] = 0.0;
            q[2] = 0.0;
            q[3] = 1.0;
            return;
        }
        ux /= lu;
        uy /= lu;
        uz /= lu;
        vx /= lv;
        vy /= lv;
        vz /= lv;
        double w = 1.0 + (ux * vx + uy * vy + uz * vz);
        double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        if (w < 1e-12) { // opposite: 180 degrees about any axis perpendicular to u
            double ax = java.lang.Math.abs(ux) < 0.9 ? 1.0 : 0.0, ay = ax == 1.0 ? 0.0 : 1.0;
            cx = uy * 0.0 - uz * ay;
            cy = uz * ax - ux * 0.0;
            cz = ux * ay - uy * ax;
            w = 0.0;
        }
        double n = java.lang.Math.sqrt(cx * cx + cy * cy + cz * cz + w * w);
        q[0] = cx / n;
        q[1] = cy / n;
        q[2] = cz / n;
        q[3] = w / n;
    }

    /** Rotates {@code (x, y, z)} by unit quaternion {@code q} into {@code out[o..o+2]}. */
    static void rotate(double[] q, double x, double y, double z, double[] out, int o) {
        double qx = q[0], qy = q[1], qz = q[2], qw = q[3];
        double tx = 2.0 * (qy * z - qz * y), ty = 2.0 * (qz * x - qx * z), tz = 2.0 * (qx * y - qy * x);
        out[o] = x + qw * tx + (qy * tz - qz * ty);
        out[o + 1] = y + qw * ty + (qz * tx - qx * tz);
        out[o + 2] = z + qw * tz + (qx * ty - qy * tx);
    }

    /** {@code a · b} into {@code out} (may alias). */
    static void mul(double[] a, double[] b, double[] out) {
        double ax = a[0], ay = a[1], az = a[2], aw = a[3], bx = b[0], by = b[1], bz = b[2], bw = b[3];
        out[0] = aw * bx + ax * bw + ay * bz - az * by;
        out[1] = aw * by - ax * bz + ay * bw + az * bx;
        out[2] = aw * bz + ax * by - ay * bx + az * bw;
        out[3] = aw * bw - ax * bx - ay * by - az * bz;
    }

    /** Unit quaternion of rotation vector {@code (x, y, z)} (angle = length). */
    static void fromRotationVector(double x, double y, double z, double[] q) {
        double th = java.lang.Math.sqrt(x * x + y * y + z * z);
        double k = th == 0.0 ? 0.5 : AccurateMath.sin(0.5 * th) / th;
        q[0] = x * k;
        q[1] = y * k;
        q[2] = z * k;
        q[3] = AccurateMath.cos(0.5 * th);
    }

    /** Rotation vector (axis · angle, angle in [0, π]) of unit quaternion {@code q} into {@code v}. */
    static void toRotationVector(double[] q, double[] v) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        if (w < 0.0) {
            x = -x;
            y = -y;
            z = -z;
            w = -w;
        }
        double s = java.lang.Math.sqrt(x * x + y * y + z * z);
        double k = s == 0.0 ? 2.0 : 2.0 * AccurateMath.atan2(s, w) / s;
        v[0] = x * k;
        v[1] = y * k;
        v[2] = z * k;
    }

    /** Area-stable law of cosines: the angle opposite side {@code c} in a triangle with sides a, b, c, as (cos, sin). */
    static void angleOpposite(double a, double b, double c, double[] cs) {
        // Kahan's stable triangle area with sides sorted p >= q >= r
        double p = java.lang.Math.max(a, java.lang.Math.max(b, c));
        double r = java.lang.Math.min(a, java.lang.Math.min(b, c));
        double q = a + b + c - p - r;
        double prod = (p + (q + r)) * (r - (p - q)) * (r + (p - q)) * (p + (q - r));
        double area4 = java.lang.Math.sqrt(java.lang.Math.max(prod, 0.0)); // 4 · area
        double cos = a * a + b * b - c * c; // times 2ab
        double sin = area4; // 2 · area · 2 = 4 area = 2ab sin
        double n = java.lang.Math.sqrt(cos * cos + sin * sin);
        if (n == 0.0) {
            cs[0] = 1.0;
            cs[1] = 0.0;
            return;
        }
        cs[0] = cos / n;
        cs[1] = sin / n;
    }
}
