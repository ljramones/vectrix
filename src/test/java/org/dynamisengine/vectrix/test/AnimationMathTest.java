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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.affine.DualQuatTransformd;
import org.dynamisengine.vectrix.affine.DualQuatTransformf;
import org.dynamisengine.vectrix.affine.RigidTransformf;
import org.dynamisengine.vectrix.animation.QuaternionCurvesd;
import org.dynamisengine.vectrix.animation.QuaternionSpringd;
import org.dynamisengine.vectrix.core.Matrix4x3d;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.linalg.Lie3d;
import org.junit.jupiter.api.Test;

/** Quaternion curves, the rotation spring and dual quaternion operations against exact identities. */
class AnimationMathTest {

    private static Quaterniond randomRotation(SplittableRandom rnd) {
        return new Quaterniond(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian())
                .normalize();
    }

    /** Rotation angle between a and b, accurate for small angles (acos of the dot product is not). */
    private static double angleBetween(Quaterniond a, Quaterniond b) {
        Quaterniond d = new Quaterniond(a).conjugate().mul(b);
        double v = Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z);
        return 2 * Math.atan2(v, Math.abs(d.w));
    }

    private static double[] keys(Quaterniond... qs) {
        double[] k = new double[4 * qs.length];
        for (int i = 0; i < qs.length; i++) {
            k[4 * i] = qs[i].x;
            k[4 * i + 1] = qs[i].y;
            k[4 * i + 2] = qs[i].z;
            k[4 * i + 3] = qs[i].w;
        }
        return k;
    }

    private static double sameRotation(Quaterniond a, Quaterniond b) {
        double d = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        double s = d < 0 ? -1 : 1;
        return Math.max(Math.max(Math.abs(a.x - s * b.x), Math.abs(a.y - s * b.y)),
                Math.max(Math.abs(a.z - s * b.z), Math.abs(a.w - s * b.w)));
    }

    @Test
    void unrollAndInterpolatingCurves() {
        SplittableRandom rnd = new SplittableRandom(1);
        int n = 8;
        Quaterniond[] q = new Quaterniond[n];
        for (int i = 0; i < n; i++) {
            q[i] = randomRotation(rnd);
            if (rnd.nextBoolean()) {
                q[i].set(-q[i].x, -q[i].y, -q[i].z, -q[i].w);
            }
        }
        double[] k = keys(q);
        QuaternionCurvesd.unroll(k, n);
        for (int i = 1; i < n; i++) {
            double d = k[4 * i] * k[4 * i - 4] + k[4 * i + 1] * k[4 * i - 3] + k[4 * i + 2] * k[4 * i - 2]
                    + k[4 * i + 3] * k[4 * i - 1];
            assertTrue(d >= 0);
        }
        double[] ctrl = QuaternionCurvesd.squadControlPoints(k, n, new double[4 * n]);
        Quaterniond a = new Quaterniond(), b = new Quaterniond();
        for (int i = 0; i + 1 < n; i++) {
            Quaterniond ki = new Quaterniond(k[4 * i], k[4 * i + 1], k[4 * i + 2], k[4 * i + 3]);
            assertTrue(sameRotation(ki, QuaternionCurvesd.squad(k, ctrl, i, 0, a)) < 1e-15, "SQUAD hits key " + i);
            if (i > 0) {
                // C1: angular velocity matches across the key
                double h = 1e-5;
                QuaternionCurvesd.squad(k, ctrl, i - 1, 1 - h, a);
                QuaternionCurvesd.squad(k, ctrl, i, h, b);
                double left = angleBetween(a, ki) / h, right = angleBetween(b, ki) / h;
                assertEquals(left, right, 1e-4 * Math.max(1, left), "SQUAD speed continuity at key " + i);
            }
        }
        // Bezier: endpoints are the first and last control rotations
        assertTrue(sameRotation(q[0], QuaternionCurvesd.bezier(keys(q[0], q[1], q[2], q[3]), 0, 0, a)) < 1e-15);
        double[] k4 = keys(q[0], q[1], q[2], q[3]);
        QuaternionCurvesd.unroll(k4, 4);
        assertTrue(sameRotation(q[3], QuaternionCurvesd.bezier(k4, 0, 1, a)) < 1e-15);
    }

    @Test
    void cumulativeBSplineReproducesUniformRotationAndIsC2() {
        Vector3d axis = new Vector3d(0.3, -0.5, 0.8).normalize();
        double step = 0.4;
        int n = 7;
        Quaterniond base = new Quaterniond().rotateXYZ(0.2, 0.5, -0.1);
        Quaterniond[] q = new Quaterniond[n];
        for (int i = 0; i < n; i++) {
            q[i] = new Quaterniond(base).mul(new Quaterniond().rotateAxis(i * step, axis.x, axis.y, axis.z));
        }
        double[] k = keys(q);
        Quaterniond out = new Quaterniond();
        for (int i = 1; i < n - 2; i++) {
            for (double t = 0; t <= 1; t += 0.125) {
                QuaternionCurvesd.cumulativeBSpline(k, n, i, t, out);
                Quaterniond ref = new Quaterniond(base).mul(new Quaterniond().rotateAxis((i + t) * step, axis.x, axis.y,
                        axis.z));
                assertTrue(sameRotation(ref, out) < 1e-14, "uniform rotation at " + i + "+" + t);
            }
        }
        // generic keys: continuous value, speed and acceleration across segments
        SplittableRandom rnd = new SplittableRandom(2);
        for (int i = 0; i < n; i++) {
            q[i] = new Quaterniond(q[Math.max(i - 1, 0)]).mul(new Quaterniond().rotateXYZ(rnd.nextDouble(-0.6, 0.6),
                    rnd.nextDouble(-0.6, 0.6), rnd.nextDouble(-0.6, 0.6)));
        }
        k = keys(q);
        QuaternionCurvesd.unroll(k, n);
        Quaterniond l0 = new Quaterniond(), l1 = new Quaterniond(), r0 = new Quaterniond(), r1 = new Quaterniond();
        double h = 1e-4;
        for (int i = 1; i < n - 3; i++) {
            QuaternionCurvesd.cumulativeBSpline(k, n, i, 1, l0);
            QuaternionCurvesd.cumulativeBSpline(k, n, i + 1, 0, r0);
            assertTrue(sameRotation(l0, r0) < 1e-14, "C0 at " + i);
            QuaternionCurvesd.cumulativeBSpline(k, n, i, 1 - h, l1);
            QuaternionCurvesd.cumulativeBSpline(k, n, i + 1, h, r1);
            Quaterniond lm = new Quaterniond(), rm = new Quaterniond();
            QuaternionCurvesd.cumulativeBSpline(k, n, i, 1 - 2 * h, lm);
            QuaternionCurvesd.cumulativeBSpline(k, n, i + 1, 2 * h, rm);
            // second differences on each side: equal for C2
            double accLeft = (angleBetween(l0, l1) - angleBetween(l1, lm)) / (h * h);
            double accRight = (angleBetween(r1, rm) - angleBetween(r0, r1)) / (h * h);
            assertEquals(accLeft, accRight, 2e-2 * Math.max(1, Math.abs(accLeft)), "C2 at " + i);
        }
    }

    @Test
    void weightedMeanIsTheKarcherMean() {
        Quaterniond a = new Quaterniond().rotateY(0.2), b = new Quaterniond().rotateY(1.4);
        Quaterniond m = QuaternionCurvesd.weightedMean(keys(a, b), new double[] {1, 1}, 2, new Quaterniond());
        assertTrue(sameRotation(new Quaterniond().rotateY(0.8), m) < 1e-15);
        SplittableRandom rnd = new SplittableRandom(3);
        for (int t = 0; t < 50; t++) {
            int n = 2 + rnd.nextInt(6);
            Quaterniond center = randomRotation(rnd);
            Quaterniond[] q = new Quaterniond[n];
            double[] w = new double[n];
            for (int i = 0; i < n; i++) {
                q[i] = new Quaterniond(center).mul(new Quaterniond().rotateXYZ(rnd.nextDouble(-0.8, 0.8),
                        rnd.nextDouble(-0.8, 0.8), rnd.nextDouble(-0.8, 0.8)));
                if (rnd.nextBoolean()) {
                    q[i].set(-q[i].x, -q[i].y, -q[i].z, -q[i].w); // hemisphere must not matter
                }
                w[i] = rnd.nextDouble(0.1, 2);
            }
            QuaternionCurvesd.weightedMean(keys(q), w, n, m);
            // first-order optimality: the weighted tangent vectors sum to zero
            Vector3d sum = new Vector3d();
            for (int i = 0; i < n; i++) {
                Quaterniond d = new Quaterniond(m).conjugate().mul(q[i]);
                if (d.w < 0) {
                    d.set(-d.x, -d.y, -d.z, -d.w);
                }
                double v = Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z), th = Math.atan2(v, d.w);
                if (v > 0) {
                    sum.add(new Vector3d(d.x, d.y, d.z).mul(w[i] * th / v));
                }
            }
            assertTrue(sum.length() < 1e-13, "Karcher residual " + sum.length());
        }
    }

    @Test
    void criticallyDampedSpringIsExact() {
        Quaterniond goal = new Quaterniond().rotateXYZ(0.3, -0.2, 0.9);
        SplittableRandom rnd = new SplittableRandom(4);
        for (int t = 0; t < 20; t++) {
            Quaterniond q0 = new Quaterniond(goal).premul(new Quaterniond().rotateXYZ(rnd.nextDouble(-1, 1),
                    rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1)));
            Vector3d v0 = new Vector3d(rnd.nextDouble(-2, 2), rnd.nextDouble(-2, 2), rnd.nextDouble(-2, 2));
            double halflife = rnd.nextDouble(0.05, 0.5), dt = rnd.nextDouble(0.001, 0.05);
            Quaterniond one = new Quaterniond(), two = new Quaterniond(q0);
            Vector3d v1 = new Vector3d(), v2 = new Vector3d(v0);
            QuaternionSpringd.step(q0, v0, goal, halflife, dt, one, v1);
            QuaternionSpringd.step(two, v2, goal, halflife, dt / 2, two, v2);
            QuaternionSpringd.step(two, v2, goal, halflife, dt / 2, two, v2);
            assertTrue(sameRotation(one, two) < 1e-13, "frame-rate independence " + sameRotation(one, two));
            assertTrue(v1.distance(v2) < 1e-11 * Math.max(1, v1.length()), "velocity " + v1.distance(v2));
        }
        // from rest about one axis: theta(t) = theta0 e^{-y t} (1 + y t), monotone, converging
        double theta0 = 1.2, halflife = 0.2, y = 2 * Math.log(2) / halflife;
        Quaterniond q = new Quaterniond(goal).premul(new Quaterniond().rotateZ(theta0));
        Vector3d v = new Vector3d();
        double prev = theta0;
        for (int i = 1; i <= 200; i++) {
            QuaternionSpringd.step(q, v, goal, halflife, 0.01, q, v);
            double tt = i * 0.01;
            double expected = theta0 * Math.exp(-y * tt) * (1 + y * tt);
            double actual = angleBetween(q, goal);
            assertEquals(expected, actual, 1e-12, "analytic decay at " + tt);
            assertTrue(actual <= prev);
            prev = actual;
        }
    }

    private static Matrix4x3d toMatrix(DualQuatTransformd dq) {
        Vector3d t = dq.getTranslation(new Vector3d());
        return new Matrix4x3d().translation(t).rotate(dq.real);
    }

    @Test
    void dualQuaternions() {
        SplittableRandom rnd = new SplittableRandom(5);
        Vector3d p = new Vector3d(0.3, -1.2, 2.5), out = new Vector3d();
        for (int trial = 0; trial < 200; trial++) {
            DualQuatTransformd a = new DualQuatTransformd().set(randomRotation(rnd),
                    new Vector3d(rnd.nextDouble(-5, 5), rnd.nextDouble(-5, 5), rnd.nextDouble(-5, 5)));
            Quaterniond rb = trial % 4 == 0 ? new Quaterniond(a.real)
                    : trial % 4 == 1 ? new Quaterniond(a.real).mul(new Quaterniond().rotateX(1e-7))
                    : randomRotation(rnd);
            DualQuatTransformd b = new DualQuatTransformd().set(rb,
                    new Vector3d(rnd.nextDouble(-5, 5), rnd.nextDouble(-5, 5), rnd.nextDouble(-5, 5)));
            // composition and inverse
            DualQuatTransformd ab = a.mul(b, new DualQuatTransformd());
            Vector3d viaB = b.transformPosition(p.x, p.y, p.z, new Vector3d());
            assertTrue(ab.transformPosition(p.x, p.y, p.z, out).distance(a.transformPosition(viaB.x, viaB.y, viaB.z,
                    new Vector3d())) < 1e-12);
            DualQuatTransformd id = a.mul(a.invert(new DualQuatTransformd()), new DualQuatTransformd());
            assertTrue(id.transformPosition(p.x, p.y, p.z, out).distance(p) < 1e-12);
            // ScLERP against SE(3) exp/log: A exp(t log(A^-1 B))
            double t = rnd.nextDouble(-0.5, 1.5);
            DualQuatTransformd s = a.sclerp(b, t, new DualQuatTransformd());
            DualQuatTransformd bAligned = new DualQuatTransformd().set(b);
            if (a.real.dot(b.real) < 0) {
                bAligned.real.mul(-1);
                bAligned.dual.mul(-1);
            }
            Matrix4x3d rel = toMatrix(a.invert(new DualQuatTransformd()).mul(bAligned, new DualQuatTransformd()));
            Vector3d w = new Vector3d(), v = new Vector3d();
            Lie3d.logSe3(rel, w, v);
            Matrix4x3d ref = toMatrix(a).mul(Lie3d.expSe3(w.mul(t), v.mul(t), new Matrix4x3d()));
            Vector3d refP = ref.transformPosition(new Vector3d(p));
            assertTrue(s.transformPosition(p.x, p.y, p.z, out).distance(refP) < 1e-10,
                    "ScLERP vs SE(3) at trial " + trial + ": " + out.distance(refP));
            // the screw is a one-parameter subgroup: sclerp(A, B, s u) = sclerp(A, sclerp(A, B, s), u)
            double su = rnd.nextDouble(0.1, 0.9), u = rnd.nextDouble(-1, 2);
            DualQuatTransformd c = a.sclerp(b, su, new DualQuatTransformd());
            DualQuatTransformd lhs = a.sclerp(b, su * u, new DualQuatTransformd());
            DualQuatTransformd rhs = a.sclerp(c, u, new DualQuatTransformd());
            assertTrue(lhs.transformPosition(p.x, p.y, p.z, out).distance(rhs.transformPosition(p.x, p.y, p.z,
                    new Vector3d())) < 1e-12, "screw semigroup");
            // exact unit results
            assertEquals(1.0, s.real.lengthSquared(), 1e-15);
            assertEquals(0.0, s.real.dot(s.dual), 1e-15);
        }
        // blending: sign invariance, pure translations average exactly, unit result
        DualQuatTransformd t1 = new DualQuatTransformd().set(new Quaterniond(), new Vector3d(1, 0, 0));
        DualQuatTransformd t2 = new DualQuatTransformd().set(new Quaterniond(), new Vector3d(0, 4, 0));
        DualQuatTransformd t2neg = new DualQuatTransformd().set(t2);
        t2neg.real.mul(-1);
        t2neg.dual.mul(-1);
        DualQuatTransformd bl = DualQuatTransformd.blend(new DualQuatTransformd[] {t1, t2neg}, new double[] {3, 1}, 2,
                new DualQuatTransformd());
        assertTrue(bl.getTranslation(new Vector3d()).distance(0.75, 1.0, 0) < 1e-15);
        Vector3d scale = new Vector3d();
        DualQuatTransformd.blendScaled(new DualQuatTransformd[] {t1, t2}, new double[] {1, 1, 1, 3, 3, 3},
                new double[] {1, 1}, 2, bl, scale);
        assertTrue(scale.distance(2, 2, 2) < 1e-15);
        // float: setFromRigid now normalizes before forming the dual part
        RigidTransformf rigid = new RigidTransformf();
        rigid.rotation.set(0, 0, 2, 0); // a non-unit 180-degree rotation about z
        rigid.translation.set(1, 2, 3);
        DualQuatTransformf f = new DualQuatTransformf().setFromRigid(rigid);
        assertTrue(f.getTranslation(new Vector3f()).distance(1, 2, 3) < 1e-6f);
        assertTrue(f.transformPosition(1, 0, 0, new Vector3f()).distance(0, 2, 3) < 1e-6f);
        Quaternionf unused = f.getRotation(new Quaternionf());
        assertEquals(1f, unused.lengthSquared(), 1e-6f);
    }
}
