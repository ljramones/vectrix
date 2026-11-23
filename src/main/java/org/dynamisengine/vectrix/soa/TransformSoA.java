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

import org.dynamisengine.vectrix.affine.Transformf;

/**
 * Structure-of-arrays storage for transform batches: element {@code i} is the translation
 * {@code (tx[i], ty[i], tz[i])}, the rotation quaternion {@code (qx[i], qy[i], qz[i], qw[i])} and the scale
 * {@code (sx[i], sy[i], sz[i])} of a {@link Transformf}.
 * <p>
 * The arrays are public and have a fixed length. Not thread-safe: synchronize writers yourself.
 */
public class TransformSoA {
    /**
     * The x translation of each transform.
     */
    public final float[] tx;
    /**
     * The y translation of each transform.
     */
    public final float[] ty;
    /**
     * The z translation of each transform.
     */
    public final float[] tz;
    /**
     * The x component of each rotation quaternion.
     */
    public final float[] qx;
    /**
     * The y component of each rotation quaternion.
     */
    public final float[] qy;
    /**
     * The z component of each rotation quaternion.
     */
    public final float[] qz;
    /**
     * The w (scalar) component of each rotation quaternion.
     */
    public final float[] qw;
    /**
     * The x scale of each transform.
     */
    public final float[] sx;
    /**
     * The y scale of each transform.
     */
    public final float[] sy;
    /**
     * The z scale of each transform.
     */
    public final float[] sz;

    /**
     * Creates storage for {@code size} transforms, each the identity: zero translation, rotation {@code (0, 0, 0, 1)}
     * and scale 1.
     *
     * @param size the number of transforms
     */
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

    /**
     * Returns the number of transforms.
     *
     * @return the length of the arrays
     */
    public int size() {
        return tx.length;
    }

    /**
     * Copies {@code t} into element {@code i}.
     *
     * @param i the element index
     * @param t the transform to store
     */
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

    /**
     * Copies element {@code i} into {@code dest}.
     *
     * @param i the element index
     * @param dest receives the transform
     * @return {@code dest}
     */
    public Transformf get(int i, Transformf dest) {
        dest.translation.set(tx[i], ty[i], tz[i]);
        dest.rotation.set(qx[i], qy[i], qz[i], qw[i]);
        dest.scale.set(sx[i], sy[i], sz[i]);
        return dest;
    }
}
