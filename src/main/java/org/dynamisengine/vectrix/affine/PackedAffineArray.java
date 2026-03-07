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
 * Contiguous packed 3x4 affine transform storage.
 *
 * Layout per transform (12 floats):
 * [m00 m01 m02 tx  m10 m11 m12 ty  m20 m21 m22 tz]
 */
public final class PackedAffineArray {
    public static final int STRIDE_FLOATS = 12;

    private final float[] data;

    public PackedAffineArray(int count) {
        this.data = new float[count * STRIDE_FLOATS];
    }

    public PackedAffineArray(float[] data) {
        if (data.length % STRIDE_FLOATS != 0) {
            throw new IllegalArgumentException("Packed affine array length must be a multiple of 12");
        }
        this.data = data;
    }

    public int size() {
        return data.length / STRIDE_FLOATS;
    }

    public float[] raw() {
        return data;
    }

    public int offsetOf(int index) {
        return index * STRIDE_FLOATS;
    }

    public void set(int index,
                    float m00, float m01, float m02, float tx,
                    float m10, float m11, float m12, float ty,
                    float m20, float m21, float m22, float tz) {
        int o = offsetOf(index);
        data[o] = m00;
        data[o + 1] = m01;
        data[o + 2] = m02;
        data[o + 3] = tx;
        data[o + 4] = m10;
        data[o + 5] = m11;
        data[o + 6] = m12;
        data[o + 7] = ty;
        data[o + 8] = m20;
        data[o + 9] = m21;
        data[o + 10] = m22;
        data[o + 11] = tz;
    }
}
