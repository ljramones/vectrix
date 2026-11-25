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

import org.dynamisengine.vectrix.core.Vector3f;

/**
 * Octahedral normal encoding: a direction mapped onto the octahedron, unfolded into a square and stored as two
 * snorm16 values in 32 bits. Stateless and allocation-free.
 */
public final class OctaNormal {
    /**
     * The code of {@code +Z = (0, 0, 1)}, the centre of the octahedral square. It is also what
     * {@link #encodeSnorm16} returns for a zero, NaN or infinite vector.
     *
     * @since 3.0.0
     */
    public static final int UP = 0;

    private OctaNormal() {
    }

    /**
     * Encodes a direction as two snorm16 octahedral coordinates. The input is divided by its L1 norm, so it need not
     * be unit length. Very long or very short (subnormal) vectors are rescaled first, so they encode like their unit
     * form.
     * <p>
     * A vector without a direction, the zero vector or one with a NaN or infinite component, encodes as
     * {@link #UP}, the code of {@code +Z = (0, 0, 1)}: a defined, valid normal rather than NaN coordinates.
     *
     * @param nx the direction's x
     * @param ny the direction's y
     * @param nz the direction's z
     * @return packed x in the low 16 bits, y in the high 16 bits
     */
    public static int encodeSnorm16(float nx, float ny, float nz) {
        float l1 = java.lang.Math.abs(nx) + java.lang.Math.abs(ny) + java.lang.Math.abs(nz);
        if (!(l1 >= 0x1p-100f && l1 <= 0x1p100f)) {
            // zero, NaN, infinite, or an L1 norm whose reciprocal would over- or underflow
            float m = java.lang.Math.max(java.lang.Math.abs(nx), java.lang.Math.max(java.lang.Math.abs(ny),
                    java.lang.Math.abs(nz)));
            if (!(m > 0.0f && m < Float.POSITIVE_INFINITY)) {
                return UP; // Math.max propagates NaN, so this also catches a NaN component
            }
            nx /= m;
            ny /= m;
            nz /= m;
            l1 = java.lang.Math.abs(nx) + java.lang.Math.abs(ny) + java.lang.Math.abs(nz);
        }
        float invL1 = 1.0f / l1;
        float x = nx * invL1;
        float y = ny * invL1;
        float z = nz * invL1;

        if (z < 0.0f) {
            float ox = x;
            x = (1.0f - java.lang.Math.abs(y)) * signNotZero(ox);
            y = (1.0f - java.lang.Math.abs(ox)) * signNotZero(y);
        }
        int px = PackedNorm.packSnorm16(x);
        int py = PackedNorm.packSnorm16(y);
        return px | (py << 16);
    }

    /**
     * Decodes {@link #encodeSnorm16} into a unit vector.
     *
     * @param packed the code: x in the low 16 bits, y in the high 16 bits
     * @param dest receives the normalized direction
     * @return {@code dest}
     */
    public static Vector3f decodeSnorm16(int packed, Vector3f dest) {
        float x = PackedNorm.unpackSnorm16(packed);
        float y = PackedNorm.unpackSnorm16(packed >>> 16);
        float z = 1.0f - java.lang.Math.abs(x) - java.lang.Math.abs(y);
        if (z < 0.0f) {
            float ox = x;
            x = (1.0f - java.lang.Math.abs(y)) * signNotZero(ox);
            y = (1.0f - java.lang.Math.abs(ox)) * signNotZero(y);
        }
        float invLen = org.dynamisengine.vectrix.core.Math.invsqrt(x * x + y * y + z * z);
        dest.set(x * invLen, y * invLen, z * invLen);
        return dest;
    }

    private static float signNotZero(float v) {
        return v >= 0.0f ? 1.0f : -1.0f;
    }
}
