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

import org.vectrix.affine.Transformf;

/**
 * Structure-of-arrays storage for transform batches.
 */
public class TransformSoA {
    public final float[] tx;
    public final float[] ty;
    public final float[] tz;
    public final float[] qx;
    public final float[] qy;
    public final float[] qz;
    public final float[] qw;
    public final float[] sx;
    public final float[] sy;
    public final float[] sz;

    public TransformSoA(int size) {
        tx = new float[size];
        ty = new float[size];
        tz = new float[size];
        qx = new float[size];
        qy = new float[size];
        qz = new float[size];
        qw = new float[size];
        sx = new float[size];
        sy = new float[size];
        sz = new float[size];
        for (int i = 0; i < size; i++) {
            qw[i] = 1.0f;
            sx[i] = 1.0f;
            sy[i] = 1.0f;
            sz[i] = 1.0f;
        }
    }

    public int size() {
        return tx.length;
    }

    public void set(int i, Transformf t) {
        tx[i] = t.translation.x;
        ty[i] = t.translation.y;
        tz[i] = t.translation.z;
        qx[i] = t.rotation.x;
        qy[i] = t.rotation.y;
        qz[i] = t.rotation.z;
        qw[i] = t.rotation.w;
        sx[i] = t.scale.x;
        sy[i] = t.scale.y;
        sz[i] = t.scale.z;
    }

    public Transformf get(int i, Transformf dest) {
        dest.translation.set(tx[i], ty[i], tz[i]);
        dest.rotation.set(qx[i], qy[i], qz[i], qw[i]);
        dest.scale.set(sx[i], sy[i], sz[i]);
        return dest;
    }
}
