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

import org.vectrix.core.Vector3f;

/**
 * Octahedral unit normal encoding/decoding.
 */
public final class OctaNormal {
    private OctaNormal() {
    }

    /**
     * Encode a unit normal to signed-normalized 16-bit x/y.
     *
     * @return packed x in low 16 bits, y in high 16 bits
     */
    public static int encodeSnorm16(float nx, float ny, float nz) {
        float invL1 = 1.0f / (java.lang.Math.abs(nx) + java.lang.Math.abs(ny) + java.lang.Math.abs(nz));
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
     * Decode signed-normalized 16-bit octahedral normal.
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
        float invLen = org.vectrix.core.Math.invsqrt(x * x + y * y + z * z);
        dest.set(x * invLen, y * invLen, z * invLen);
        return dest;
    }

    private static float signNotZero(float v) {
        return v >= 0.0f ? 1.0f : -1.0f;
    }
}
