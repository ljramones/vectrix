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
 * std140/std430 packing helpers for common scalar/vector/matrix uniform types.
 */
public final class StdLayout {
    public enum Type {
        FLOAT,
        VEC2,
        VEC3,
        VEC4,
        MAT4
    }

    private StdLayout() {
    }

    public static int[] offsetsStd140(Type... types) {
        return offsets(true, types);
    }

    public static int[] offsetsStd430(Type... types) {
        return offsets(false, types);
    }

    public static int sizeStd140(Type type) {
        switch (type) {
            case FLOAT:
                return 4;
            case VEC2:
                return 8;
            case VEC3:
            case VEC4:
                return 16;
            case MAT4:
                return 64;
            default:
                throw new IllegalArgumentException("type");
        }
    }

    public static int sizeStd430(Type type) {
        switch (type) {
            case FLOAT:
                return 4;
            case VEC2:
                return 8;
            case VEC3:
            case VEC4:
                return 16;
            case MAT4:
                return 64;
            default:
                throw new IllegalArgumentException("type");
        }
    }

    public static int alignStd140(Type type) {
        switch (type) {
            case FLOAT:
                return 4;
            case VEC2:
                return 8;
            case VEC3:
            case VEC4:
            case MAT4:
                return 16;
            default:
                throw new IllegalArgumentException("type");
        }
    }

    public static int alignStd430(Type type) {
        switch (type) {
            case FLOAT:
                return 4;
            case VEC2:
                return 8;
            case VEC3:
            case VEC4:
            case MAT4:
                return 16;
            default:
                throw new IllegalArgumentException("type");
        }
    }

    public static int structSizeStd140(Type... types) {
        int[] offsets = offsetsStd140(types);
        if (types.length == 0) {
            return 0;
        }
        int tail = offsets[types.length - 1] + sizeStd140(types[types.length - 1]);
        return roundUp(tail, 16);
    }

    public static int structSizeStd430(Type... types) {
        int[] offsets = offsetsStd430(types);
        if (types.length == 0) {
            return 0;
        }
        int tail = offsets[types.length - 1] + sizeStd430(types[types.length - 1]);
        return tail;
    }

    static int[] offsets(boolean std140, Type... types) {
        int[] out = new int[types.length];
        int cursor = 0;
        for (int i = 0; i < types.length; i++) {
            Type t = types[i];
            int align = std140 ? alignStd140(t) : alignStd430(t);
            cursor = roundUp(cursor, align);
            out[i] = cursor;
            cursor += std140 ? sizeStd140(t) : sizeStd430(t);
        }
        return out;
    }

    static int roundUp(int value, int align) {
        return ((value + align - 1) / align) * align;
    }
}
