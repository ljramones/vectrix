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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.dynamisengine.vectrix.affine.Transformf;

/**
 * Layout and writer for per-instance translation, rotation and scale (TRS) records in a GPU buffer.
 * <p>
 * A layout is a record stride and the byte offsets of the three fields inside a record. Instance {@code i} starts
 * at byte {@code i * strideBytes} of the buffer (absolute indexing: the buffer position is neither used nor moved).
 * The writers always produce little-endian bytes. Instances are immutable; the writers do not allocate.
 */
public final class GpuTransformLayout {
    /** How the rotation quaternion is stored. */
    public enum RotationEncoding {
        /** Four 32-bit floats, {@code x, y, z, w} (16 bytes). */
        FLOAT4,
        /** The 64-bit smallest-three code of {@link QuatCompression#packSmallest3} (8 bytes). */
        QUAT_SMALLEST3_16
    }

    /** How the scale is stored. */
    public enum ScaleEncoding {
        /** Three 32-bit floats (12 bytes). */
        FLOAT3,
        /** Three IEEE half floats from {@link Half#pack} (6 bytes). */
        HALF3
    }

    /** The size of one record in bytes: the distance between consecutive instances. */
    public final int strideBytes;
    /** The byte offset of the translation (three floats) inside a record. */
    public final int translationOffset;
    /** The byte offset of the rotation inside a record. */
    public final int rotationOffset;
    /** The byte offset of the scale inside a record. */
    public final int scaleOffset;
    /** The rotation encoding; {@link #write} uses it together with {@link #scaleEncoding} to pick a writer. */
    public final RotationEncoding rotationEncoding;
    /** The scale encoding; {@link #write} uses it together with {@link #rotationEncoding} to pick a writer. */
    public final ScaleEncoding scaleEncoding;

    /**
     * Creates a layout. Nothing is validated: the caller must make the fields fit in the stride without
     * overlapping.
     *
     * @param strideBytes the record size in bytes
     * @param translationOffset the byte offset of the translation in a record
     * @param rotationOffset the byte offset of the rotation in a record
     * @param scaleOffset the byte offset of the scale in a record
     * @param rotationEncoding the rotation encoding
     * @param scaleEncoding the scale encoding
     */
    public GpuTransformLayout(int strideBytes, int translationOffset, int rotationOffset, int scaleOffset,
            RotationEncoding rotationEncoding, ScaleEncoding scaleEncoding) {
        this.strideBytes = strideBytes;
        this.translationOffset = translationOffset;
        this.rotationOffset = rotationOffset;
        this.scaleOffset = scaleOffset;
        this.rotationEncoding = rotationEncoding;
        this.scaleEncoding = scaleEncoding;
    }

    /**
     * The 40-byte float layout: translation at 0, rotation ({@code FLOAT4}) at 12, scale ({@code FLOAT3}) at 28.
     *
     * @return a new layout
     */
    public static GpuTransformLayout floatTRS() {
        return new GpuTransformLayout(40, 0, 12, 28, RotationEncoding.FLOAT4, ScaleEncoding.FLOAT3);
    }

    /**
     * The 28-byte compact layout: float translation at 0, smallest-three rotation at 12, half-float scale at 20,
     * and two bytes of padding at the end.
     *
     * @return a new layout
     */
    public static GpuTransformLayout compactTRS() {
        return new GpuTransformLayout(28, 0, 12, 20, RotationEncoding.QUAT_SMALLEST3_16, ScaleEncoding.HALF3);
    }

    /**
     * Bytes needed for {@code count} transforms: {@code count * strideBytes}.
     *
     * @param count the number of transforms
     * @return the buffer size in bytes
     * @throws ArithmeticException if the size does not fit in an {@code int} (a {@link ByteBuffer} limit); this
     *         used to overflow silently, e.g. {@code requiredBytes(100_000_000)} returned -1494967296
     */
    public int requiredBytes(int count) {
        return java.lang.Math.multiplyExact(count, strideBytes);
    }

    /**
     * Writes {@code transform} at {@code index} in the float TRS layout. The bytes are always little-endian, the
     * order GPUs read, whatever {@code dst.order()} is. Before 2.4.0 the buffer's order was used, so a default
     * (big-endian) {@code ByteBuffer} received byte-swapped values.
     * <p>
     * The default {@link #floatTRS()} layout is the GLSL struct
     * {@code layout(scalar) struct { vec3 t; vec4 q; vec3 s; }}, or ten floats. It is not expressible under std430,
     * where the {@code vec4} would need 16-byte alignment.
     * <p>
     * This method ignores {@link #rotationEncoding} and {@link #scaleEncoding}: it always writes four float
     * rotation components and three float scale components at the layout's offsets.
     *
     * @param dst the buffer; written with absolute puts, its position and order are left unchanged
     * @param index the instance index; the record starts at byte {@code index * strideBytes}
     * @param transform the transform to write
     * @throws ArithmeticException if {@code index * strideBytes} overflows an {@code int}
     * @throws IndexOutOfBoundsException if a field falls outside the buffer's limit (earlier fields may already
     *         have been written)
     */
    public void writeFloatTrs(ByteBuffer dst, int index, Transformf transform) {
        int base = java.lang.Math.multiplyExact(index, strideBytes); // a wrapped offset could be a valid one
        boolean swap = dst.order() != ByteOrder.LITTLE_ENDIAN;
        putFloat(dst, base + translationOffset, transform.translation.x, swap);
        putFloat(dst, base + translationOffset + 4, transform.translation.y, swap);
        putFloat(dst, base + translationOffset + 8, transform.translation.z, swap);
        putFloat(dst, base + rotationOffset, transform.rotation.x, swap);
        putFloat(dst, base + rotationOffset + 4, transform.rotation.y, swap);
        putFloat(dst, base + rotationOffset + 8, transform.rotation.z, swap);
        putFloat(dst, base + rotationOffset + 12, transform.rotation.w, swap);
        putFloat(dst, base + scaleOffset, transform.scale.x, swap);
        putFloat(dst, base + scaleOffset + 4, transform.scale.y, swap);
        putFloat(dst, base + scaleOffset + 8, transform.scale.z, swap);
    }

    /**
     * Writes {@code transform} at {@code index} in the compact TRS layout, in little-endian bytes whatever
     * {@code dst.order()} is (see {@link #writeFloatTrs}). The rotation is the 64-bit smallest-three code, low word
     * first; the scale is three half floats. The default {@link #compactTRS()} layout is the GLSL struct
     * {@code layout(scalar) struct { vec3 t; uvec2 q; uint16_t s[3]; }} (16-bit storage), or seven 32-bit words
     * with the halves unpacked by {@code unpackHalf2x16}.
     * <p>
     * This method ignores {@link #rotationEncoding} and {@link #scaleEncoding}: it always writes the 8-byte
     * smallest-three rotation and the 6-byte half-float scale at the layout's offsets.
     *
     * @param dst the buffer; written with absolute puts, its position and order are left unchanged
     * @param index the instance index; the record starts at byte {@code index * strideBytes}
     * @param transform the transform to write; its rotation is normalized before compression
     * @throws ArithmeticException if {@code index * strideBytes} overflows an {@code int}
     * @throws IndexOutOfBoundsException if a field falls outside the buffer's limit (earlier fields may already
     *         have been written)
     */
    public void writeCompactTrs(ByteBuffer dst, int index, Transformf transform) {
        int base = java.lang.Math.multiplyExact(index, strideBytes); // a wrapped offset could be a valid one
        boolean swap = dst.order() != ByteOrder.LITTLE_ENDIAN;
        putFloat(dst, base + translationOffset, transform.translation.x, swap);
        putFloat(dst, base + translationOffset + 4, transform.translation.y, swap);
        putFloat(dst, base + translationOffset + 8, transform.translation.z, swap);
        long q = QuatCompression.packSmallest3(transform.rotation);
        dst.putLong(base + rotationOffset, swap ? Long.reverseBytes(q) : q);
        putShort(dst, base + scaleOffset, Half.pack(transform.scale.x), swap);
        putShort(dst, base + scaleOffset + 2, Half.pack(transform.scale.y), swap);
        putShort(dst, base + scaleOffset + 4, Half.pack(transform.scale.z), swap);
    }

    private static void putFloat(ByteBuffer dst, int at, float v, boolean swap) {
        int bits = Float.floatToRawIntBits(v);
        dst.putInt(at, swap ? Integer.reverseBytes(bits) : bits);
    }

    private static void putShort(ByteBuffer dst, int at, short v, boolean swap) {
        dst.putShort(at, swap ? Short.reverseBytes(v) : v);
    }

    /**
     * Writes {@code transform} at {@code index} with the writer chosen by the encodings: {@link #writeFloatTrs}
     * when the rotation is {@code FLOAT4} and the scale is {@code FLOAT3}, and {@link #writeCompactTrs} for every
     * other combination. A mixed layout such as {@code FLOAT4} with {@code HALF3} therefore gets the compact
     * encoding of both fields (smallest-three rotation and half-float scale), not the encoding its fields name.
     *
     * @param dst the buffer; written with absolute puts, its position and order are left unchanged
     * @param index the instance index; the record starts at byte {@code index * strideBytes}
     * @param transform the transform to write
     * @throws ArithmeticException if {@code index * strideBytes} overflows an {@code int}
     * @throws IndexOutOfBoundsException if a field falls outside the buffer's limit
     */
    public void write(ByteBuffer dst, int index, Transformf transform) {
        if (rotationEncoding == RotationEncoding.FLOAT4 && scaleEncoding == ScaleEncoding.FLOAT3) {
            writeFloatTrs(dst, index, transform);
        } else {
            writeCompactTrs(dst, index, transform);
        }
    }
}
