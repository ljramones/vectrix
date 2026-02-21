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
 * Descriptor for one vertex input attribute inside an interleaved stream.
 */
public final class VertexAttribute {
    public final String name;
    public final int components;
    public final VertexAttributeFormat format;
    public final int offsetBytes;

    public VertexAttribute(String name, int components, VertexAttributeFormat format, int offsetBytes) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name");
        }
        if (components < 1 || components > 4) {
            throw new IllegalArgumentException("components must be in [1, 4]");
        }
        if (format == null) {
            throw new IllegalArgumentException("format");
        }
        if (offsetBytes < 0) {
            throw new IllegalArgumentException("offsetBytes");
        }
        this.name = name;
        this.components = components;
        this.format = format;
        this.offsetBytes = offsetBytes;
    }

    public int byteSize() {
        return components * format.byteSize();
    }
}
