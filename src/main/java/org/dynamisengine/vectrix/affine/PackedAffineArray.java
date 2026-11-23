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
package org.dynamisengine.vectrix.affine;

/**
 * Contiguous packed 3x4 affine transform storage, in the engine's canonical {@code AFFINE_3X4_ROW_MAJOR} layout.
 * <p>
 * Each transform is 12 floats holding the three rows of the upper 3x4 of the matrix:
 * <pre>
 * [r00 r01 r02 tx,  r10 r11 r12 ty,  r20 r21 r22 tz]
 * </pre>
 * {@code rRC} is the element in row R, column C (mathematical indices), so a point maps as
 * {@code x' = r00*x + r01*y + r02*z + tx}, and so on. This is the order of Vulkan's
 * {@code VkTransformMatrixKHR}, of GLSL {@code vec4 rows[3]} with {@code p' = vec3(dot(rows[0], vec4(p, 1)), ...)},
 * and of {@link org.dynamisengine.vectrix.soa.SkinningKernels#buildRigidMatrixPalette12 the skinning palette}.
 * <p>
 * JOML-style accessors name elements by column first: {@code Matrix4f.mCR()} is column C, row R. The mapping is
 * therefore {@code rRC = m.mCR()}, for example {@code r01 = m.m10()} and {@code tx = m.m30()}.
 * <p>
 * Transform {@code i} occupies {@code raw()[12 * i]} to {@code raw()[12 * i + 11]}. {@link #set} throws
 * {@link IndexOutOfBoundsException} for an index outside {@code [0, size())}; {@link #offsetOf} does not check.
 * Threads may write disjoint transforms concurrently.
 */
public final class PackedAffineArray {
    /** The number of floats per transform: 12. */
    public static final int STRIDE_FLOATS = 12;

    private final float[] data;

    /**
     * Creates storage for {@code count} transforms, each the identity.
     *
     * @param count the number of transforms, at least 0
     * @throws IllegalArgumentException if {@code count} is negative or {@code 12 * count} does not fit in an
     *         {@code int}
     */
    public PackedAffineArray(int count) {
        if (count < 0 || count > Integer.MAX_VALUE / STRIDE_FLOATS) {
            throw new IllegalArgumentException("count: " + count);
        }
        this.data = new float[count * STRIDE_FLOATS];
        for (int o = 0; o < data.length; o += STRIDE_FLOATS) {
            data[o] = 1.0f;
            data[o + 5] = 1.0f;
            data[o + 10] = 1.0f;
        }
    }

    /**
     * Wraps {@code data} without copying it: writes through this object change {@code data}, and the other way round.
     *
     * @param data the packed transforms; its length must be a multiple of 12
     * @throws IllegalArgumentException if {@code data.length} is not a multiple of 12
     */
    public PackedAffineArray(float[] data) {
        if (data.length % STRIDE_FLOATS != 0) {
            throw new IllegalArgumentException("Packed affine array length must be a multiple of 12");
        }
        this.data = data;
    }

    /**
     * Returns the number of transforms.
     *
     * @return {@code raw().length / 12}
     */
    public int size() {
        return data.length / STRIDE_FLOATS;
    }

    /**
     * Returns the backing array itself, not a copy.
     *
     * @return the backing array
     */
    public float[] raw() {
        return data;
    }

    /**
     * Returns the array index of the first float of transform {@code index}. Does not check {@code index}.
     *
     * @param index the transform index
     * @return {@code 12 * index}
     */
    public int offsetOf(int index) {
        return index * STRIDE_FLOATS;
    }

    /**
     * Sets transform {@code index} from its rows; see the class documentation for the element meaning.
     *
     * @param index the transform index, in {@code [0, size())}
     * @param r00 row 0, column 0
     * @param r01 row 0, column 1
     * @param r02 row 0, column 2
     * @param tx the translation x (row 0, column 3)
     * @param r10 row 1, column 0
     * @param r11 row 1, column 1
     * @param r12 row 1, column 2
     * @param ty the translation y (row 1, column 3)
     * @param r20 row 2, column 0
     * @param r21 row 2, column 1
     * @param r22 row 2, column 2
     * @param tz the translation z (row 2, column 3)
     * @throws IndexOutOfBoundsException if {@code index} is outside {@code [0, size())}; nothing is written then
     */
    public void set(int index,
                    float r00, float r01, float r02, float tx,
                    float r10, float r11, float r12, float ty,
                    float r20, float r21, float r22, float tz) {
        int o = java.util.Objects.checkIndex(index, size()) * STRIDE_FLOATS;
        data[o] = r00;
        data[o + 1] = r01;
        data[o + 2] = r02;
        data[o + 3] = tx;
        data[o + 4] = r10;
        data[o + 5] = r11;
        data[o + 6] = r12;
        data[o + 7] = ty;
        data[o + 8] = r20;
        data[o + 9] = r21;
        data[o + 10] = r22;
        data[o + 11] = tz;
    }
}
