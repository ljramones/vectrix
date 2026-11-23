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
package org.dynamisengine.vectrix.soa;

import org.dynamisengine.vectrix.affine.DualQuatTransformf;

/**
 * Structure-of-arrays storage for dual quaternions, the joint input of
 * {@link SkinningKernels#skinDualQuat4} and {@link SkinningKernels#skinDualQuat4SoA}.
 * <p>
 * Element {@code i} is the dual quaternion with real (rotation) part {@code (rx[i], ry[i], rz[i], rw[i])} and dual
 * part {@code (dx[i], dy[i], dz[i], dw[i])}, as in {@link DualQuatTransformf}. The arrays are public and have a
 * fixed length. Not thread-safe: synchronize writers yourself.
 */
public class DualQuatSoA {
    /** The real (rotation) part of each dual quaternion: x, y, z and w components. */
    public final float[] rx, ry, rz, rw;
    /** The dual (translation) part of each dual quaternion: x, y, z and w components. */
    public final float[] dx, dy, dz, dw;

    /**
     * Creates storage for {@code size} dual quaternions, each the identity (real part {@code (0, 0, 0, 1)}, dual part
     * zero).
     *
     * @param size the number of dual quaternions
     */
    public DualQuatSoA(int size) {
        rx = new float[size];
        ry = new float[size];
        rz = new float[size];
        rw = new float[size];
        dx = new float[size];
        dy = new float[size];
        dz = new float[size];
        dw = new float[size];
        for (int i = 0; i < size; i++) {
            rw[i] = 1.0f;
        }
    }

    /**
     * Copies {@code dq} into element {@code i}.
     *
     * @param i the element index
     * @param dq the dual quaternion to store
     */
    public void set(int i, DualQuatTransformf dq) {
        rx[i] = dq.real.x;
        ry[i] = dq.real.y;
        rz[i] = dq.real.z;
        rw[i] = dq.real.w;
        dx[i] = dq.dual.x;
        dy[i] = dq.dual.y;
        dz[i] = dq.dual.z;
        dw[i] = dq.dual.w;
    }
}
