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
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.animation.PoseKernelsd;
import org.dynamisengine.vectrix.animation.PoseKernelsf;
import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Vector3d;
import org.junit.jupiter.api.Test;

/** Pose kernels: forward kinematics against matrix composition, exact round trips, blends and additive layers. */
class PoseKernelsTest {

    private static double[] randomRotations(SplittableRandom r, int n) {
        double[] q = new double[4 * n];
        for (int i = 0; i < n; i++) {
            Quaterniond x = new Quaterniond(r.nextGaussian(), r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            q[4 * i] = x.x;
            q[4 * i + 1] = x.y;
            q[4 * i + 2] = x.z;
            q[4 * i + 3] = x.w;
        }
        return q;
    }

    private static double[] randomVectors(SplittableRandom r, int n, double lo, double hi) {
        double[] v = new double[3 * n];
        for (int i = 0; i < 3 * n; i++) {
            v[i] = r.nextDouble(lo, hi);
        }
        return v;
    }

    private static Quaterniond q(double[] a, int i) {
        return new Quaterniond(a[4 * i], a[4 * i + 1], a[4 * i + 2], a[4 * i + 3]);
    }

    private static double angle(Quaterniond a, Quaterniond b) {
        Quaterniond d = new Quaterniond(a).conjugate().mul(b);
        return 2 * Math.atan2(Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z), Math.abs(d.w));
    }

    @Test
    void forwardKinematicsMatchesMatrixCompositionAndRoundTrips() {
        SplittableRandom r = new SplittableRandom(1);
        int n = 40;
        int[] parents = new int[n];
        parents[0] = -1;
        for (int i = 1; i < n; i++) {
            parents[i] = r.nextInt(i);
        }
        double[] lt = randomVectors(r, n, -2, 2), lr = randomRotations(r, n), ls = new double[3 * n];
        for (int i = 0; i < n; i++) {
            double s = r.nextDouble(0.5, 2); // uniform scale per joint: the TRS hierarchy is then exact
            ls[3 * i] = s;
            ls[3 * i + 1] = s;
            ls[3 * i + 2] = s;
        }
        double[] mt = new double[3 * n], mr = new double[4 * n], ms = new double[3 * n];
        PoseKernelsd.localToModel(parents, n, lt, lr, ls, mt, mr, ms);
        Matrix4d[] ref = new Matrix4d[n];
        for (int i = 0; i < n; i++) {
            Matrix4d local = new Matrix4d().translationRotateScale(lt[3 * i], lt[3 * i + 1], lt[3 * i + 2], lr[4 * i],
                    lr[4 * i + 1], lr[4 * i + 2], lr[4 * i + 3], ls[3 * i], ls[3 * i + 1], ls[3 * i + 2]);
            ref[i] = parents[i] < 0 ? local : new Matrix4d(ref[parents[i]]).mul(local);
            Vector3d p = ref[i].getTranslation(new Vector3d());
            assertTrue(p.distance(mt[3 * i], mt[3 * i + 1], mt[3 * i + 2]) < 1e-11 * (1 + p.length()), "translation " + i);
            Vector3d probe = new Vector3d(0.3, -0.7, 1.1);
            Vector3d viaMatrix = ref[i].transformDirection(new Vector3d(probe));
            Vector3d viaPose = q(mr, i).transform(new Vector3d(probe)).mul(ms[3 * i]);
            assertTrue(viaMatrix.distance(viaPose) < 1e-11 * (1 + viaMatrix.length()), "rotation and scale " + i);
        }
        // exact inverse, also with non-uniform scale; and in place
        double[] nls = randomVectors(r, n, 0.5, 2);
        PoseKernelsd.localToModel(parents, n, lt, lr, nls, mt, mr, ms);
        double[] bt = new double[3 * n], br = new double[4 * n], bs = new double[3 * n];
        PoseKernelsd.modelToLocal(parents, n, mt, mr, ms, bt, br, bs);
        for (int i = 0; i < 3 * n; i++) {
            assertEquals(lt[i], bt[i], 1e-12 * (1 + Math.abs(lt[i])) * 8, "local translation");
            assertEquals(nls[i], bs[i], 1e-14 * 8, "local scale");
        }
        for (int i = 0; i < n; i++) {
            assertTrue(angle(q(lr, i), q(br, i)) < 1e-12, "local rotation " + i);
        }
        double[] it = lt.clone(), ir = lr.clone(), is = nls.clone();
        PoseKernelsd.localToModel(parents, n, it, ir, is, it, ir, is);
        for (int i = 0; i < 3 * n; i++) {
            assertEquals(mt[i], it[i], 0.0, "in place matches");
        }
        PoseKernelsd.modelToLocal(parents, n, it, ir, is, it, ir, is);
        for (int i = 0; i < 3 * n; i++) {
            assertEquals(bt[i], it[i], 0.0, "in place inverse matches");
        }
        int[] bad = {-1, 2, 0};
        assertThrows(IllegalArgumentException.class, () -> PoseKernelsd.localToModel(bad, 3, new double[9], new double[12],
                null, new double[9], new double[12], null));
    }

    @Test
    void quaternionArrayOperationsAndBlends() {
        SplittableRandom r = new SplittableRandom(2);
        int n = 200;
        double[] a = randomRotations(r, n), b = randomRotations(r, n), out = new double[4 * n], out2 = new double[4 * n];
        double[] negB = b.clone();
        for (int i = 0; i < negB.length; i++) {
            negB[i] = -negB[i];
        }
        double t = 0.3;
        PoseKernelsd.slerp(a, b, t, out, n);
        PoseKernelsd.slerp(a, negB, t, out2, n);
        for (int i = 0; i < n; i++) {
            double full = angle(q(a, i), q(b, i));
            assertEquals(t * full, angle(q(a, i), q(out, i)), 1e-12, "slerp covers t of the angle");
            assertEquals((1 - t) * full, angle(q(out, i), q(b, i)), 1e-12, "and is on the arc");
            assertTrue(angle(q(out, i), q(out2, i)) < 1e-14, "hemisphere does not matter");
        }
        PoseKernelsd.slerp(a, b, 0, out, n);
        PoseKernelsd.slerp(a, b, 1, out2, n);
        for (int i = 0; i < n; i++) {
            assertTrue(angle(q(a, i), q(out, i)) < 1e-15 && angle(q(b, i), q(out2, i)) < 1e-14, "slerp endpoints");
        }
        PoseKernelsd.mul(a, b, out, n);
        for (int i = 0; i < n; i++) {
            assertTrue(angle(new Quaterniond(q(a, i)).mul(q(b, i)), q(out, i)) < 1e-15, "mul");
        }
        // two-way weighted blend is nlerp; weights need not be normalized; masked blends select
        PoseKernelsd.nlerp(a, b, t, out, n);
        PoseKernelsd.blendRotations(new double[][] {a, negB}, new double[] {(1 - t) * 3, t * 3}, 2, out2, n);
        for (int i = 0; i < n; i++) {
            assertTrue(angle(q(out, i), q(out2, i)) < 1e-14, "weighted blend equals nlerp");
            assertEquals(1.0, q(out, i).lengthSquared(), 1e-15);
        }
        double[] mask = new double[n];
        for (int i = 0; i < n; i++) {
            mask[i] = i % 2;
        }
        PoseKernelsd.slerpMasked(a, b, mask, out, n);
        for (int i = 0; i < n; i++) {
            assertTrue(angle(i % 2 == 0 ? q(a, i) : q(b, i), q(out, i)) < 1e-14, "mask selects");
        }
        double[] va = randomVectors(r, n, -5, 5), vb = randomVectors(r, n, -5, 5), vo = new double[3 * n];
        PoseKernelsd.blendVectors(new double[][] {va, vb}, new double[] {1, 3}, 2, vo, n);
        for (int i = 0; i < 3 * n; i++) {
            assertEquals(0.25 * va[i] + 0.75 * vb[i], vo[i], 1e-14);
        }
    }

    @Test
    void additiveLayersRestoreThePose() {
        SplittableRandom r = new SplittableRandom(3);
        int n = 100;
        double[] pose = randomRotations(r, n), ref = randomRotations(r, n), add = new double[4 * n], out = new double[4 * n];
        PoseKernelsd.makeAdditiveRotations(pose, ref, add, n);
        PoseKernelsd.applyAdditiveRotations(ref, add, 1, out, n);
        for (int i = 0; i < n; i++) {
            assertTrue(angle(q(pose, i), q(out, i)) < 1e-14, "weight 1 restores the pose");
        }
        PoseKernelsd.applyAdditiveRotations(ref, add, 0.5, out, n);
        for (int i = 0; i < n; i++) {
            double full = angle(q(ref, i), q(pose, i));
            assertEquals(0.5 * full, angle(q(ref, i), q(out, i)), 1e-12, "half weight, half the rotation");
        }
        PoseKernelsd.applyAdditiveRotations(ref, add, 0, out, n);
        for (int i = 0; i < n; i++) {
            assertTrue(angle(q(ref, i), q(out, i)) < 1e-15, "weight 0 is the base");
        }
        double[] tp = randomVectors(r, n, -3, 3), tr = randomVectors(r, n, -3, 3), d = new double[3 * n], o = new double[3 * n];
        PoseKernelsd.makeAdditiveVectors(tp, tr, d, n);
        PoseKernelsd.applyAdditiveVectors(tr, d, 1, o, n);
        for (int i = 0; i < 3 * n; i++) {
            assertEquals(tp[i], o[i], 1e-14);
        }
        // float interface agrees
        float[] fa = new float[4 * n], fb = new float[4 * n], fo = new float[4 * n];
        for (int i = 0; i < 4 * n; i++) {
            fa[i] = (float) pose[i];
            fb[i] = (float) ref[i];
        }
        PoseKernelsf.slerp(fa, fb, 0.4f, fo, n);
        double[] dd = new double[4 * n];
        PoseKernelsd.slerp(pose, ref, 0.4, dd, n);
        for (int i = 0; i < 4 * n; i++) {
            assertEquals(dd[i], fo[i], 2e-6);
        }
    }
}
