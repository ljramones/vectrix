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

/**
 * Per-component vertex attribute element formats.
 */
public enum VertexAttributeFormat {
    FLOAT32(4, false),
    FLOAT16(2, false),
    UINT8(1, false),
    SINT8(1, false),
    UNORM8(1, true),
    SNORM8(1, true),
    UINT16(2, false),
    SINT16(2, false),
    UNORM16(2, true),
    SNORM16(2, true),
    UINT32(4, false),
    SINT32(4, false);

    private final int byteSize;
    private final boolean normalized;

    VertexAttributeFormat(int byteSize, boolean normalized) {
        this.byteSize = byteSize;
        this.normalized = normalized;
    }

    public int byteSize() {
        return byteSize;
    }

    public boolean normalized() {
        return normalized;
    }
}
