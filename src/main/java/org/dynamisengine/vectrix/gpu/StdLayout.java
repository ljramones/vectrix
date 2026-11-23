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
 * Offsets and sizes for flat blocks of {@code float}, {@code vec2}, {@code vec3}, {@code vec4} and {@code mat4}
 * members under std140 and std430.
 * <p>
 * For these five types the two rule sets agree: the size and alignment functions return the same values for both,
 * and so do {@link #offsetsStd140} and {@link #offsetsStd430}. They differ only in the struct size, which std140
 * rounds up to 16 bytes. Arrays and nested structs, where std140 and std430 really diverge, are not supported; use
 * {@link ShaderLayout} for those and for any other type.
 */
public final class StdLayout {
    /** The member types this helper supports. */
    public enum Type {
        /** {@code float}: size 4, alignment 4. */
        FLOAT,
        /** {@code vec2}: size 8, alignment 8. */
        VEC2,
        /** {@code vec3}: size 12, alignment 16; a following scalar fills its fourth slot. */
        VEC3,
        /** {@code vec4}: size 16, alignment 16. */
        VEC4,
        /** Column-major {@code mat4}: size 64, alignment 16. */
        MAT4
    }

    private StdLayout() {
    }

    /**
     * The std140 byte offset of each member, in declaration order. Allocates the result.
     *
     * @param types the member types
     * @return a new array with one offset per member
     */
    public static int[] offsetsStd140(Type... types) {
        return offsets(true, types);
    }

    /**
     * The std430 byte offset of each member, in declaration order; identical to {@link #offsetsStd140} for these
     * types. Allocates the result.
     *
     * @param types the member types
     * @return a new array with one offset per member
     */
    public static int[] offsetsStd430(Type... types) {
        return offsets(false, types);
    }

    /**
     * The std140 size of {@code type} in bytes.
     *
     * @param type the member type
     * @return the size in bytes
     */
    public static int sizeStd140(Type type) {
        switch (type) {
            case FLOAT:
                return 4;
            case VEC2:
                return 8;
            case VEC3:
                return 12; // base alignment is 16, but the size is 12: a following scalar packs into the 4th slot
            case VEC4:
                return 16;
            case MAT4:
                return 64;
            default:
                throw new IllegalArgumentException("type");
        }
    }

    /**
     * The std430 size of {@code type} in bytes (the same as std140 for these types).
     *
     * @param type the member type
     * @return the size in bytes
     */
    public static int sizeStd430(Type type) {
        switch (type) {
            case FLOAT:
                return 4;
            case VEC2:
                return 8;
            case VEC3:
                return 12; // base alignment is 16, but the size is 12: a following scalar packs into the 4th slot
            case VEC4:
                return 16;
            case MAT4:
                return 64;
            default:
                throw new IllegalArgumentException("type");
        }
    }

    /**
     * The std140 base alignment of {@code type} in bytes.
     *
     * @param type the member type
     * @return the alignment in bytes
     */
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

    /**
     * The std430 base alignment of {@code type} in bytes (the same as std140 for these types).
     *
     * @param type the member type
     * @return the alignment in bytes
     */
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

    /**
     * Size of a std140 struct with these members: the end of the last member rounded up to 16 bytes (std140
     * rounds struct alignment up to that of a vec4). Allocates a temporary offsets array.
     *
     * @param types the member types, in declaration order
     * @return the struct size in bytes, or 0 for no members
     */
    public static int structSizeStd140(Type... types) {
        int[] offsets = offsetsStd140(types);
        if (types.length == 0) {
            return 0;
        }
        int tail = offsets[types.length - 1] + sizeStd140(types[types.length - 1]);
        return roundUp(tail, 16);
    }

    /**
     * Size of a std430 struct with these members, as used for array strides and nested members: the end of
     * the last member rounded up to the struct's base alignment (the largest member alignment). Allocates a
     * temporary offsets array.
     *
     * @param types the member types, in declaration order
     * @return the struct size in bytes, or 0 for no members
     */
    public static int structSizeStd430(Type... types) {
        int[] offsets = offsetsStd430(types);
        if (types.length == 0) {
            return 0;
        }
        int tail = offsets[types.length - 1] + sizeStd430(types[types.length - 1]);
        int align = 1;
        for (Type t : types) {
            align = java.lang.Math.max(align, alignStd430(t));
        }
        return roundUp(tail, align);
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
