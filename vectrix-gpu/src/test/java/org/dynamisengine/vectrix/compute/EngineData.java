/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
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
package org.dynamisengine.vectrix.compute;

import java.util.Arrays;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.soa.DualQuatSoA;
import org.dynamisengine.vectrix.soa.TransformSoA;

/** Test data for the engine kernels: SoA buffers with offsets and strides, transforms of every kind. */
final class EngineData {

    /** Counts from 1 to large: partial groups, exactly one group, and grids taller than one row of 7 groups. */
    static final int[] COUNTS = {1, 2, 63, 64, 65, 447, 448, 449, 1000, 4099, 70_001};

    /** The scale kinds of the transform tests. */
    enum Scale { IDENTITY, UNIFORM, NON_UNIFORM }

    private EngineData() {
    }

    static float uniform(SplittableRandom r, float lo, float hi) {
        return (float) (lo + (hi - lo) * r.nextDouble());
    }

    /** A float array of {@code n} values filled with {@code v}. */
    static float[] filled(int n, float v) {
        float[] a = new float[n];
        Arrays.fill(a, v);
        return a;
    }

    /** {@code planes} packed {@code stride} apart from {@code off}, in a buffer with {@code slack} extra values. */
    static float[] pack(float[][] planes, int n, int off, int stride, int slack, float fill) {
        float[] a = filled(off + (planes.length - 1) * stride + n + slack, fill);
        EngineModels.pack(planes, n, a, off, stride);
        return a;
    }

    /** The {@code fields × n} planes at {@code off}, {@code stride} apart, of {@code a}. */
    static float[][] unpack(float[] a, int off, int stride, int fields, int n) {
        float[][] p = new float[fields][n];
        EngineModels.unpack(a, off, stride, p, n);
        return p;
    }

    /** A unit quaternion as a float array {@code x, y, z, w}, normalized in float. */
    static float[] unitQuaternion(SplittableRandom r) {
        float x = uniform(r, -1, 1);
        float y = uniform(r, -1, 1);
        float z = uniform(r, -1, 1);
        float w = uniform(r, -1, 1);
        float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        return new float[] {x / len, y / len, z / len, w / len};
    }

    /** Random transforms with unit rotations, translations in [-50, 50] and the given scale kind. */
    static TransformSoA transforms(SplittableRandom r, int n, Scale scale) {
        TransformSoA t = new TransformSoA(n);
        for (int i = 0; i < n; i++) {
            float[] q = unitQuaternion(r);
            t.tx[i] = uniform(r, -50, 50);
            t.ty[i] = uniform(r, -50, 50);
            t.tz[i] = uniform(r, -50, 50);
            t.qx[i] = q[0];
            t.qy[i] = q[1];
            t.qz[i] = q[2];
            t.qw[i] = q[3];
            switch (scale) {
                case IDENTITY -> {
                    t.sx[i] = 1f;
                    t.sy[i] = 1f;
                    t.sz[i] = 1f;
                }
                case UNIFORM -> {
                    float s = uniform(r, 0.1f, 4f);
                    t.sx[i] = s;
                    t.sy[i] = s;
                    t.sz[i] = s;
                }
                case NON_UNIFORM -> {
                    t.sx[i] = uniform(r, 0.1f, 4f);
                    t.sy[i] = uniform(r, -3f, 3f);
                    t.sz[i] = uniform(r, 0.1f, 4f);
                }
                default -> throw new AssertionError(scale);
            }
        }
        return t;
    }

    /** Random unit dual quaternions: a unit rotation r and dual part 0.5·(t, 0)·r. */
    static DualQuatSoA dualQuats(SplittableRandom r, int n) {
        DualQuatSoA d = new DualQuatSoA(n);
        for (int i = 0; i < n; i++) {
            float[] q = unitQuaternion(r);
            float tx = uniform(r, -20, 20);
            float ty = uniform(r, -20, 20);
            float tz = uniform(r, -20, 20);
            d.rx[i] = q[0];
            d.ry[i] = q[1];
            d.rz[i] = q[2];
            d.rw[i] = q[3];
            // (t, 0) · (x, y, z, w) / 2
            d.dx[i] = 0.5f * (tx * q[3] + ty * q[2] - tz * q[1]);
            d.dy[i] = 0.5f * (-tx * q[2] + ty * q[3] + tz * q[0]);
            d.dz[i] = 0.5f * (tx * q[1] - ty * q[0] + tz * q[3]);
            d.dw[i] = 0.5f * (-tx * q[0] - ty * q[1] - tz * q[2]);
        }
        return d;
    }
}
