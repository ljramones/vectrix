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
package org.vectrix.gpu;

import org.vectrix.core.Quaternionf;
import org.vectrix.core.Quaternionfc;

/**
 * Quaternion compression helpers.
 */
public final class QuatCompression {
    private static final float INV_SQRT2 = 0.7071067811865475f;
    private static final float SQRT2 = 1.4142135623730951f;

    private QuatCompression() {
    }

    /**
     * Smallest-3 quaternion compression with 16 bits per stored component.
     * Layout: [2 bits index][16 bits c0][16 bits c1][16 bits c2]
     */
    public static long packSmallest3(Quaternionfc q) {
        float x = q.x(), y = q.y(), z = q.z(), w = q.w();
        float invLen = org.vectrix.core.Math.invsqrt(x * x + y * y + z * z + w * w);
        x *= invLen;
        y *= invLen;
        z *= invLen;
        w *= invLen;

        int idx = 0;
        float ax = java.lang.Math.abs(x), ay = java.lang.Math.abs(y), az = java.lang.Math.abs(z), aw = java.lang.Math.abs(w);
        float maxAbs = ax;
        if (ay > maxAbs) {
            idx = 1;
            maxAbs = ay;
        }
        if (az > maxAbs) {
            idx = 2;
            maxAbs = az;
        }
        if (aw > maxAbs) {
            idx = 3;
        }
        float largest = idx == 0 ? x : idx == 1 ? y : idx == 2 ? z : w;
        if (largest < 0.0f) {
            x = -x;
            y = -y;
            z = -z;
            w = -w;
        }

        float c0, c1, c2;
        if (idx == 0) {
            c0 = y;
            c1 = z;
            c2 = w;
        } else if (idx == 1) {
            c0 = x;
            c1 = z;
            c2 = w;
        } else if (idx == 2) {
            c0 = x;
            c1 = y;
            c2 = w;
        } else {
            c0 = x;
            c1 = y;
            c2 = z;
        }
        int p0 = PackedNorm.packSnorm16(c0 * SQRT2);
        int p1 = PackedNorm.packSnorm16(c1 * SQRT2);
        int p2 = PackedNorm.packSnorm16(c2 * SQRT2);
        return ((long) idx << 48) | ((long) p0 << 32) | ((long) p1 << 16) | (long) p2;
    }

    public static Quaternionf unpackSmallest3(long packed, Quaternionf dest) {
        int idx = (int) ((packed >>> 48) & 0x3L);
        float c0 = PackedNorm.unpackSnorm16((int) (packed >>> 32)) * INV_SQRT2;
        float c1 = PackedNorm.unpackSnorm16((int) (packed >>> 16)) * INV_SQRT2;
        float c2 = PackedNorm.unpackSnorm16((int) packed) * INV_SQRT2;

        float x, y, z, w;
        float omitted = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0f, 1.0f - c0 * c0 - c1 * c1 - c2 * c2));
        if (idx == 0) {
            x = omitted;
            y = c0;
            z = c1;
            w = c2;
        } else if (idx == 1) {
            x = c0;
            y = omitted;
            z = c1;
            w = c2;
        } else if (idx == 2) {
            x = c0;
            y = c1;
            z = omitted;
            w = c2;
        } else {
            x = c0;
            y = c1;
            z = c2;
            w = omitted;
        }
        return dest.set(x, y, z, w);
    }

    public static float angularErrorDegrees(Quaternionfc a, Quaternionfc b) {
        float dot = java.lang.Math.abs(a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w());
        dot = dot > 1.0f ? 1.0f : dot;
        return (float) java.lang.Math.toDegrees(2.0 * java.lang.Math.acos(dot));
    }
}
