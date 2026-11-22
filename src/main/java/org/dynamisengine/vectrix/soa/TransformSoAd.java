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

import org.dynamisengine.vectrix.affine.Transformd;

/**
 * Structure-of-arrays storage for transform batches.
 * <p>
 * Double-precision twin of {@link TransformSoA}, for world-scale transforms (see {@code world.WorldPos}).
 *
 * @since 2.3.0
 */
public class TransformSoAd {
    public final double[] tx;
    public final double[] ty;
    public final double[] tz;
    public final double[] qx;
    public final double[] qy;
    public final double[] qz;
    public final double[] qw;
    public final double[] sx;
    public final double[] sy;
    public final double[] sz;

    public TransformSoAd(int size) {
        tx = new double[size];
        ty = new double[size];
        tz = new double[size];
        qx = new double[size];
        qy = new double[size];
        qz = new double[size];
        qw = new double[size];
        sx = new double[size];
        sy = new double[size];
        sz = new double[size];
        for (int i = 0; i < size; i++) {
            qw[i] = 1.0;
            sx[i] = 1.0;
            sy[i] = 1.0;
            sz[i] = 1.0;
        }
    }

    public int size() {
        return tx.length;
    }

    public void set(int i, Transformd t) {
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

    public Transformd get(int i, Transformd dest) {
        dest.translation.set(tx[i], ty[i], tz[i]);
        dest.rotation.set(qx[i], qy[i], qz[i], qw[i]);
        dest.scale.set(sx[i], sy[i], sz[i]);
        return dest;
    }
}
