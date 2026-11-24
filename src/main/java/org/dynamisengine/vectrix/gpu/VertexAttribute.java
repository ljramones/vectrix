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
 * One vertex input attribute inside an interleaved stream: a name, a component count, a per-component format and
 * a byte offset within the vertex. Immutable.
 *
 * @deprecated Moved to DynamisGPU: {@code org.dynamisengine.gpu.api.layout.VertexAttribute} in {@code dynamis-gpu-api}
 *         1.2.0. Vectrix removes this class in 4.0.
 */
@Deprecated(since = "3.3.0", forRemoval = true)
public final class VertexAttribute {
    /** The attribute name, unique within a {@link VertexLayout}. */
    public final String name;
    /** The number of components, 1 to 4. */
    public final int components;
    /** The format of each component. */
    public final VertexAttributeFormat format;
    /** The byte offset of the attribute from the start of the vertex. */
    public final int offsetBytes;

    /**
     * Creates an attribute.
     *
     * @param name the attribute name, not empty
     * @param components the number of components, 1 to 4
     * @param format the format of each component
     * @param offsetBytes the byte offset within the vertex, at least 0
     * @throws IllegalArgumentException if {@code name} is null or empty, {@code components} is outside
     *         {@code [1, 4]}, {@code format} is null or {@code offsetBytes} is negative
     */
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

    /**
     * The attribute size in bytes: {@code components * format.byteSize()}, with no padding.
     *
     * @return the size in bytes
     */
    public int byteSize() {
        return components * format.byteSize();
    }
}
