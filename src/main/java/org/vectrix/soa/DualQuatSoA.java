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
package org.vectrix.soa;

import org.vectrix.affine.DualQuatTransformf;

/**
 * Structure-of-arrays storage for dual quaternions.
 */
public class DualQuatSoA {
    public final float[] rx, ry, rz, rw;
    public final float[] dx, dy, dz, dw;

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
