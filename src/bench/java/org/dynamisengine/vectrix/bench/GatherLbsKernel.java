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
package org.dynamisengine.vectrix.bench;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import org.dynamisengine.vectrix.soa.SkinningKernels;
import org.dynamisengine.vectrix.soa.TransformSoA;

/**
 * Benchmark-only Vector API version of {@code SkinningKernels.skinLbs4SoAScalar}: rigid 4-joint LBS with the joint
 * quaternion and translation gathered per lane ({@code FloatVector.fromArray} with an index map). It performs the
 * scalar kernel's operations in the same order, so the results are bitwise identical. It exists to measure whether
 * hardware gathers (AVX-512) make SIMD skinning worthwhile; on arm64 (NEON has no gather) an equivalent kernel measured
 * 6.8&times; slower than scalar.
 */
final class GatherLbsKernel {
    private static final VectorSpecies<Float> S = FloatVector.SPECIES_PREFERRED;

    private GatherLbsKernel() {
    }

    static void skin(TransformSoA joints, int[] j0, int[] j1, int[] j2, int[] j3, float[] w0, float[] w1, float[] w2,
            float[] w3, float[] inX, float[] inY, float[] inZ, float[] outX, float[] outY, float[] outZ, int count) {
        int lanes = S.length();
        int limit = count - count % lanes;
        int[][] js = {j0, j1, j2, j3};
        float[][] ws = {w0, w1, w2, w3};
        int i = 0;
        for (; i < limit; i += lanes) {
            FloatVector x = FloatVector.fromArray(S, inX, i);
            FloatVector y = FloatVector.fromArray(S, inY, i);
            FloatVector z = FloatVector.fromArray(S, inZ, i);
            FloatVector ox = FloatVector.zero(S);
            FloatVector oy = FloatVector.zero(S);
            FloatVector oz = FloatVector.zero(S);
            for (int k = 0; k < 4; k++) {
                int[] j = js[k];
                FloatVector rx = FloatVector.fromArray(S, joints.qx, 0, j, i);
                FloatVector ry = FloatVector.fromArray(S, joints.qy, 0, j, i);
                FloatVector rz = FloatVector.fromArray(S, joints.qz, 0, j, i);
                FloatVector rw = FloatVector.fromArray(S, joints.qw, 0, j, i);
                FloatVector tx = FloatVector.fromArray(S, joints.tx, 0, j, i);
                FloatVector ty = FloatVector.fromArray(S, joints.ty, 0, j, i);
                FloatVector tz = FloatVector.fromArray(S, joints.tz, 0, j, i);
                FloatVector w = FloatVector.fromArray(S, ws[k], i);
                FloatVector tx2 = ry.mul(z).sub(rz.mul(y)).mul(2.0f);
                FloatVector ty2 = rz.mul(x).sub(rx.mul(z)).mul(2.0f);
                FloatVector tz2 = rx.mul(y).sub(ry.mul(x)).mul(2.0f);
                // scalar: (x + rw * tx2 + (ry * tz2 - rz * ty2) + t) * w, evaluated left to right
                ox = ox.add(x.add(rw.mul(tx2)).add(ry.mul(tz2).sub(rz.mul(ty2))).add(tx).mul(w));
                oy = oy.add(y.add(rw.mul(ty2)).add(rz.mul(tx2).sub(rx.mul(tz2))).add(ty).mul(w));
                oz = oz.add(z.add(rw.mul(tz2)).add(rx.mul(ty2).sub(ry.mul(tx2))).add(tz).mul(w));
            }
            ox.intoArray(outX, i);
            oy.intoArray(outY, i);
            oz.intoArray(outZ, i);
        }
        if (i < count) {
            int n = count - i;
            float[][] tail = new float[14][];
            SkinningKernels.skinLbs4SoA(joints, slice(j0, i, n), slice(j1, i, n), slice(j2, i, n), slice(j3, i, n),
                    slice(w0, i, n), slice(w1, i, n), slice(w2, i, n), slice(w3, i, n), slice(inX, i, n), slice(inY, i, n),
                    slice(inZ, i, n), tail[0] = new float[n], tail[1] = new float[n], tail[2] = new float[n], n);
            System.arraycopy(tail[0], 0, outX, i, n);
            System.arraycopy(tail[1], 0, outY, i, n);
            System.arraycopy(tail[2], 0, outZ, i, n);
        }
    }

    private static int[] slice(int[] a, int from, int n) {
        int[] r = new int[n];
        System.arraycopy(a, from, r, 0, n);
        return r;
    }

    private static float[] slice(float[] a, int from, int n) {
        float[] r = new float[n];
        System.arraycopy(a, from, r, 0, n);
        return r;
    }
}
