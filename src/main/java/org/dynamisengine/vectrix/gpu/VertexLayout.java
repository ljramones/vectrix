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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An interleaved vertex layout: a vertex stride and its attributes, validated on construction. Immutable.
 * <p>
 * Validation requires every attribute offset to be a multiple of its component size, every attribute to end within
 * the stride, no two attributes to overlap and no two to share a name. The stride must be a multiple of the largest
 * component size, so that the attributes of every vertex, not only the first, are aligned.
 */
public final class VertexLayout {
    /** The size of one vertex in bytes. */
    public final int strideBytes;
    /** The attributes, in the order given; unmodifiable. */
    public final List<VertexAttribute> attributes;

    /**
     * Creates and validates a layout. The list is copied.
     *
     * @param strideBytes the size of one vertex in bytes, at least 1 and a multiple of every attribute's component
     *        size
     * @param attributes the attributes, at least one, none null
     * @throws IllegalArgumentException if {@code strideBytes} is not positive or not a multiple of an attribute's
     *         component size, the list is null or empty or holds a null attribute, or the attributes are misaligned,
     *         exceed the stride, overlap or share a name
     */
    public VertexLayout(int strideBytes, List<VertexAttribute> attributes) {
        if (strideBytes <= 0) {
            throw new IllegalArgumentException("strideBytes must be positive: " + strideBytes);
        }
        if (attributes == null || attributes.isEmpty()) {
            throw new IllegalArgumentException("attributes must not be null or empty");
        }
        for (VertexAttribute a : attributes) {
            if (a == null) {
                throw new IllegalArgumentException("attributes must not hold null");
            }
        }
        this.strideBytes = strideBytes;
        this.attributes = Collections.unmodifiableList(new ArrayList<VertexAttribute>(attributes));
        validate();
    }

    /**
     * Creates and validates a layout from an attribute array; see the constructor.
     *
     * @param strideBytes the size of one vertex in bytes, at least 1 and a multiple of every attribute's component
     *        size
     * @param attributes the attributes, at least one, none null
     * @return the layout
     * @throws IllegalArgumentException if the layout is invalid (see the constructor)
     */
    public static VertexLayout ofInterleaved(int strideBytes, VertexAttribute... attributes) {
        if (attributes == null) {
            throw new IllegalArgumentException("attributes must not be null");
        }
        ArrayList<VertexAttribute> list = new ArrayList<VertexAttribute>(attributes.length);
        Collections.addAll(list, attributes);
        return new VertexLayout(strideBytes, list);
    }

    /**
     * Finds an attribute by name.
     *
     * @param name the attribute name
     * @return the attribute, or {@code null} if there is none with that name
     */
    public VertexAttribute attribute(String name) {
        for (int i = 0; i < attributes.size(); i++) {
            VertexAttribute a = attributes.get(i);
            if (a.name.equals(name)) {
                return a;
            }
        }
        return null;
    }

    private void validate() {
        for (int i = 0; i < attributes.size(); i++) {
            VertexAttribute a = attributes.get(i);
            int align = a.format.byteSize();
            if ((a.offsetBytes % align) != 0) {
                throw new IllegalArgumentException("Attribute offset is not aligned: " + a.name);
            }
            if (strideBytes % align != 0) {
                throw new IllegalArgumentException("strideBytes " + strideBytes + " is not a multiple of the "
                        + align + "-byte components of " + a.name);
            }
            long end = (long) a.offsetBytes + a.byteSize();
            if (end > strideBytes) {
                throw new IllegalArgumentException("Attribute exceeds stride: " + a.name);
            }
            for (int j = i + 1; j < attributes.size(); j++) {
                VertexAttribute b = attributes.get(j);
                if (rangesOverlap(a.offsetBytes, end, b.offsetBytes, (long) b.offsetBytes + b.byteSize())) {
                    throw new IllegalArgumentException("Attributes overlap: " + a.name + " and " + b.name);
                }
                if (a.name.equals(b.name)) {
                    throw new IllegalArgumentException("Duplicate attribute name: " + a.name);
                }
            }
        }
    }

    private static boolean rangesOverlap(long a0, long a1, long b0, long b1) {
        return a0 < b1 && b0 < a1;
    }
}
