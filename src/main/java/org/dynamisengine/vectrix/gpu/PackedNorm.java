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

/**
 * Normalized integer codecs (unorm/snorm 8 and 16 bits), as Vulkan defines them:
 * <ul>
 * <li><b>Decoding</b> is the exact quotient {@code c / (2^b − 1)}, clamped to {@code −1} for snorm. Before 2.4.0
 * it multiplied by a rounded reciprocal, which was one ulp off for some codes.</li>
 * <li><b>Encoding</b> rounds {@code clamp(v) × (2^b − 1)} to nearest, ties toward +∞ ({@code Math.round}). The
 * product is formed exactly, in double. NaN encodes as 0.</li>
 * </ul>
 */
public final class PackedNorm {
    private PackedNorm() {
    }

    /**
     * Encodes {@code v} as unorm8: {@code round(clamp(v, 0, 1) * 255)}.
     *
     * @param v the value; clamped to {@code [0, 1]}, NaN gives 0
     * @return the code in the low 8 bits
     */
    public static int packUnorm8(float v) {
        int q = (int) java.lang.Math.round(clamp(v, 0.0f, 1.0f) * 255.0);
        return q & 0xFF;
    }

    /**
     * Decodes unorm8: {@code c / 255}.
     *
     * @param packed the code; only the low 8 bits are read
     * @return the value in {@code [0, 1]}
     */
    public static float unpackUnorm8(int packed) {
        return (packed & 0xFF) / 255.0f;
    }

    /**
     * Encodes {@code v} as unorm16: {@code round(clamp(v, 0, 1) * 65535)}.
     *
     * @param v the value; clamped to {@code [0, 1]}, NaN gives 0
     * @return the code in the low 16 bits
     */
    public static int packUnorm16(float v) {
        int q = (int) java.lang.Math.round(clamp(v, 0.0f, 1.0f) * 65535.0);
        return q & 0xFFFF;
    }

    /**
     * Decodes unorm16: {@code c / 65535}.
     *
     * @param packed the code; only the low 16 bits are read
     * @return the value in {@code [0, 1]}
     */
    public static float unpackUnorm16(int packed) {
        return (packed & 0xFFFF) / 65535.0f;
    }

    /**
     * Encodes {@code v} as snorm8: {@code round(clamp(v, -1, 1) * 127)}, two's complement.
     *
     * @param v the value; clamped to {@code [-1, 1]}, NaN gives 0
     * @return the code in the low 8 bits (upper bits zero)
     */
    public static int packSnorm8(float v) {
        float clamped = clamp(v, -1.0f, 1.0f);
        int q = (int) java.lang.Math.round(clamped * 127.0);
        return q & 0xFF;
    }

    /**
     * Decodes snorm8: {@code max(c / 127, -1)}, so both -128 and -127 decode to -1.
     *
     * @param packed the code; only the low 8 bits are read, as a signed byte
     * @return the value in {@code [-1, 1]}
     */
    public static float unpackSnorm8(int packed) {
        int s = (byte) (packed & 0xFF);
        return java.lang.Math.max(s / 127.0f, -1.0f);
    }

    /**
     * Encodes {@code v} as snorm16: {@code round(clamp(v, -1, 1) * 32767)}, two's complement.
     *
     * @param v the value; clamped to {@code [-1, 1]}, NaN gives 0
     * @return the code in the low 16 bits (upper bits zero)
     */
    public static int packSnorm16(float v) {
        float clamped = clamp(v, -1.0f, 1.0f);
        int q = (int) java.lang.Math.round(clamped * 32767.0);
        return q & 0xFFFF;
    }

    /**
     * Decodes snorm16: {@code max(c / 32767, -1)}, so both -32768 and -32767 decode to -1.
     *
     * @param packed the code; only the low 16 bits are read, as a signed short
     * @return the value in {@code [-1, 1]}
     */
    public static float unpackSnorm16(int packed) {
        int s = (short) (packed & 0xFFFF);
        return java.lang.Math.max(s / 32767.0f, -1.0f);
    }

    /**
     * Packs four unorm8 values ({@link #packUnorm8}) into one word, {@code x} in the low byte: the
     * {@code R8G8B8A8_UNORM} layout read as a little-endian word.
     *
     * @param x the first component, bits 0-7
     * @param y the second component, bits 8-15
     * @param z the third component, bits 16-23
     * @param w the fourth component, bits 24-31
     * @return the packed word
     */
    public static int packUnorm8x4(float x, float y, float z, float w) {
        return packUnorm8(x) | (packUnorm8(y) << 8) | (packUnorm8(z) << 16) | (packUnorm8(w) << 24);
    }

    /**
     * Packs four snorm8 values ({@link #packSnorm8}) into one word, {@code x} in the low byte: the
     * {@code R8G8B8A8_SNORM} layout read as a little-endian word.
     *
     * @param x the first component, bits 0-7
     * @param y the second component, bits 8-15
     * @param z the third component, bits 16-23
     * @param w the fourth component, bits 24-31
     * @return the packed word
     */
    public static int packSnorm8x4(float x, float y, float z, float w) {
        return packSnorm8(x) | (packSnorm8(y) << 8) | (packSnorm8(z) << 16) | (packSnorm8(w) << 24);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
