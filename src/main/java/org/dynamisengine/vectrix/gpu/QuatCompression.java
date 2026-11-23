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
package org.dynamisengine.vectrix.gpu;

import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Quaternionfc;

/**
 * Smallest-three quaternion compression: a rotation in 64 bits, 16 bits per stored component. Stateless and
 * allocation-free.
 */
public final class QuatCompression {
    private static final float INV_SQRT2 = 0.7071067811865475f;
    private static final float SQRT2 = 1.4142135623730951f;

    private QuatCompression() {
    }

    /**
     * Compresses a rotation with the smallest-three scheme. The quaternion is first normalized, then negated if
     * needed so that its largest-magnitude component is positive (the same rotation). That component is dropped;
     * the other three, in {@code x, y, z, w} order without it, lie in {@code [-1/√2, 1/√2]} and are stored scaled
     * by {@code √2} as snorm16 ({@link PackedNorm#packSnorm16}).
     * <p>
     * Bit layout of the result: bits 48-49 hold the index of the dropped component (0 = x, 1 = y, 2 = z, 3 = w),
     * bits 32-47 the first stored component, bits 16-31 the second and bits 0-15 the third; bits 50-63 are zero.
     * On ties the lowest index is dropped. A zero or NaN quaternion encodes as 0, which decodes to
     * {@code (1, 0, 0, 0)}.
     *
     * @param q the rotation; need not be normalized
     * @return the 64-bit code
     */
    public static long packSmallest3(Quaternionfc q) {
        float x = q.x(), y = q.y(), z = q.z(), w = q.w();
        float invLen = org.dynamisengine.vectrix.core.Math.invsqrt(x * x + y * y + z * z + w * w);
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

    /**
     * Decodes {@link #packSmallest3}. The dropped component is rebuilt as {@code sqrt(max(0, 1 - c0² - c1² - c2²))},
     * so it is never negative, and the result is not renormalized: its length is 1 up to quantization error.
     *
     * @param packed the 64-bit code; bits 50-63 are ignored
     * @param dest receives the quaternion
     * @return {@code dest}
     */
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

    /**
     * The angle between the rotations of {@code a} and {@code b}, in degrees: {@code 2 acos(|a · b|)}. A
     * quaternion and its negation count as the same rotation. Both inputs should be unit quaternions; they are not
     * normalized, and a dot product above 1 is clamped.
     *
     * @param a the first rotation
     * @param b the second rotation
     * @return the angle in degrees, in {@code [0, 180]}
     */
    public static float angularErrorDegrees(Quaternionfc a, Quaternionfc b) {
        float dot = java.lang.Math.abs(a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w());
        dot = dot > 1.0f ? 1.0f : dot;
        return (float) java.lang.Math.toDegrees(2.0 * org.dynamisengine.vectrix.core.AccurateMath.acos(dot));
    }
}
