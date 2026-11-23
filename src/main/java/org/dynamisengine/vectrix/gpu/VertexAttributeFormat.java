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
 * The format of one component of a vertex attribute: its size and whether the shader reads it as a normalized
 * float.
 */
public enum VertexAttributeFormat {
    /** 32-bit IEEE float. */
    FLOAT32(4, false),
    /** 16-bit IEEE half float (see {@link Half}). */
    FLOAT16(2, false),
    /** 8-bit unsigned integer. */
    UINT8(1, false),
    /** 8-bit signed integer. */
    SINT8(1, false),
    /** 8-bit unsigned normalized: {@code [0, 255]} reads as {@code [0, 1]} (see {@link PackedNorm}). */
    UNORM8(1, true),
    /** 8-bit signed normalized: {@code [-127, 127]} reads as {@code [-1, 1]} (see {@link PackedNorm}). */
    SNORM8(1, true),
    /** 16-bit unsigned integer. */
    UINT16(2, false),
    /** 16-bit signed integer. */
    SINT16(2, false),
    /** 16-bit unsigned normalized: {@code [0, 65535]} reads as {@code [0, 1]} (see {@link PackedNorm}). */
    UNORM16(2, true),
    /** 16-bit signed normalized: {@code [-32767, 32767]} reads as {@code [-1, 1]} (see {@link PackedNorm}). */
    SNORM16(2, true),
    /** 32-bit unsigned integer. */
    UINT32(4, false),
    /** 32-bit signed integer. */
    SINT32(4, false);

    private final int byteSize;
    private final boolean normalized;

    VertexAttributeFormat(int byteSize, boolean normalized) {
        this.byteSize = byteSize;
        this.normalized = normalized;
    }

    /**
     * The size of one component in bytes; also the alignment {@link VertexLayout} requires for the offset.
     *
     * @return 1, 2 or 4
     */
    public int byteSize() {
        return byteSize;
    }

    /**
     * Whether the shader reads this format as a normalized float (the unorm and snorm formats).
     *
     * @return {@code true} for the unorm and snorm formats
     */
    public boolean normalized() {
        return normalized;
    }
}
