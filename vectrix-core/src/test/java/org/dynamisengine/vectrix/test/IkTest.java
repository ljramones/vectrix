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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.ik.Aimd;
import org.dynamisengine.vectrix.ik.Ccdd;
import org.dynamisengine.vectrix.ik.DampedLeastSquaresd;
import org.dynamisengine.vectrix.ik.Fabrikf;
import org.dynamisengine.vectrix.ik.JointLimitsd;
import org.dynamisengine.vectrix.ik.TwoBoneIkd;
import org.dynamisengine.vectrix.ik.TwoBoneIkf;
import org.junit.jupiter.api.Test;

/** IK kernels against exact geometric properties. */
class IkTest {

    private static Vector3d rnd3(SplittableRandom r, double s) {
        return new Vector3d(r.nextDouble(-s, s), r.nextDouble(-s, s), r.nextDouble(-s, s));
    }

    @Test
    void twoBoneReachesExactlyAndBendsTowardThePole() {
        SplittableRandom r = new SplittableRandom(1);
        Vector3d nm = new Vector3d(), ne = new Vector3d(), tmp = new Vector3d();
        Quaterniond q1 = new Quaterniond(), q2 = new Quaterniond();
        for (int t = 0; t < 2000; t++) {
            Vector3d root = rnd3(r, 5), mid = new Vector3d(root).add(rnd3(r, 1));
            Vector3d end = new Vector3d(mid).add(rnd3(r, 1));
            double a = root.distance(mid), b = mid.distance(end);
            double reachLo = Math.abs(a - b), reachHi = a + b;
            double d = reachLo + (reachHi - reachLo) * r.nextDouble(0.02, 0.98);
            Vector3d dir = rnd3(r, 1).normalize();
            Vector3d target = new Vector3d(dir).mul(d).add(root);
            Vector3d pole = new Vector3d(root).add(rnd3(r, 3));
            double err = TwoBoneIkd.solve(root, mid, end, target, pole, 1.0, nm, ne, q1, q2);
            assertEquals(0.0, err, 1e-12 * (1 + d), "reaches the target");
            assertEquals(a, root.distance(nm), 1e-12 * (1 + a), "first bone length");
            assertEquals(b, nm.distance(ne), 1e-12 * (1 + b), "second bone length");
            // in the plane of root, target and pole, on the pole's side
            Vector3d u = new Vector3d(target).sub(root).normalize();
            Vector3d h = new Vector3d(pole).sub(root);
            h.sub(new Vector3d(u).mul(h.dot(u)));
            Vector3d m = new Vector3d(nm).sub(root);
            Vector3d normal = new Vector3d(u).cross(h).normalize();
            assertEquals(0.0, m.dot(normal), 1e-10, "mid in the pole plane");
            assertTrue(m.dot(h) >= -1e-12, "mid on the pole side");
            // the deltas reproduce the positions
            q1.transform(new Vector3d(mid).sub(root), tmp);
            assertTrue(tmp.distance(new Vector3d(nm).sub(root)) < 1e-12 * (1 + a));
            q2.transform(q1.transform(new Vector3d(end).sub(mid), new Vector3d()), tmp);
            assertTrue(tmp.distance(new Vector3d(ne).sub(nm)) < 1e-12 * (1 + b));
        }
        // out of reach: straight toward the target; stretch up to the limit
        Vector3d root = new Vector3d(), mid = new Vector3d(1, 0, 0), end = new Vector3d(1, 1, 0);
        Vector3d far = new Vector3d(0, 0, 5), pole = new Vector3d(1, 0, 0);
        double err = TwoBoneIkd.solve(root, mid, end, far, pole, 1.0, nm, ne, q1, q2);
        assertEquals(3.0, err, 1e-15);
        assertTrue(ne.distance(0, 0, 2) < 1e-15);
        assertEquals(0.0, TwoBoneIkd.solve(root, mid, end, new Vector3d(0, 0, 2.2), pole, 1.2, nm, ne, q1, q2), 1e-15);
        assertEquals(0.6, TwoBoneIkd.solve(root, mid, end, new Vector3d(0, 0, 3), pole, 1.2, nm, ne, q1, q2), 1e-15);
        // too close: fully folded; straight chain with the pole in line: no NaN
        Vector3d endUnequal = new Vector3d(1, 0.4, 0);
        TwoBoneIkd.solve(root, mid, endUnequal, new Vector3d(0, 0, 0.1), pole, 1.0, nm, ne, q1, q2);
        assertEquals(0.6, ne.length(), 1e-15);
        Vector3d straightEnd = new Vector3d(2, 0, 0);
        TwoBoneIkd.solve(root, mid, straightEnd, new Vector3d(1.5, 0, 0), new Vector3d(3, 0, 0), 1.0, nm, ne, q1, q2);
        assertTrue(Double.isFinite(nm.x + nm.y + nm.z) && ne.distance(1.5, 0, 0) < 1e-15);
        // float interface
        Vector3f fm = new Vector3f(), fe = new Vector3f();
        float ferr = TwoBoneIkf.solve(new Vector3f(), new Vector3f(1, 0, 0), new Vector3f(1, 1, 0), new Vector3f(0.5f, 1.2f, 0.3f),
                new Vector3f(1, 0, 1), 1f, fm, fe, new org.dynamisengine.vectrix.core.Quaternionf(),
                new org.dynamisengine.vectrix.core.Quaternionf());
        assertEquals(0f, ferr, 1e-6f);
    }

    private static float[] chain(SplittableRandom r, int n) {
        float[] p = new float[3 * n];
        for (int i = 1; i < n; i++) {
            Vector3d d = rnd3(r, 1).normalize().mul(r.nextDouble(0.5, 1.5));
            p[3 * i] = (float) (p[3 * i - 3] + d.x);
            p[3 * i + 1] = (float) (p[3 * i - 2] + d.y);
            p[3 * i + 2] = (float) (p[3 * i - 1] + d.z);
        }
        return p;
    }

    private static double[] lengths(float[] p, int n) {
        double[] l = new double[n - 1];
        for (int i = 0; i < n - 1; i++) { // subtract in double: float subtraction rounds at ~3e-8
            double dx = (double) p[3 * i + 3] - p[3 * i], dy = (double) p[3 * i + 4] - p[3 * i + 1];
            double dz = (double) p[3 * i + 5] - p[3 * i + 2];
            l[i] = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return l;
    }

    @Test
    void fabrikConvergesKeepsLengthsAndBendLimits() {
        SplittableRandom r = new SplittableRandom(2);
        for (int t = 0; t < 300; t++) {
            int n = 3 + r.nextInt(6);
            float[] p = chain(r, n);
            double[] l0 = lengths(p, n);
            double total = 0;
            for (double v : l0) {
                total += v;
            }
            double inner = Math.max(0, 2 * java.util.Arrays.stream(l0).max().getAsDouble() - total); // unreachable core
            Vector3d dir = rnd3(r, 1).normalize();
            double reach = inner + (total - inner) * r.nextDouble(0.1, 0.9);
            float err = Fabrikf.solve(p, n, null, (float) (dir.x * reach), (float) (dir.y * reach), (float) (dir.z * reach),
                    1e-4f, 200, null);
            assertTrue(err <= 1e-4f, "converged: " + err);
            assertEquals(0f, p[0], 0f);
            double[] l1 = lengths(p, n);
            for (int i = 0; i < n - 1; i++) {
                assertEquals(l0[i], l1[i], 1e-5 * (1 + l0[i]), "bone " + i);
            }
        }
        // unreachable: a straight line toward the target
        float[] p = chain(r, 5);
        double total = 0;
        for (double v : lengths(p, 5)) {
            total += v;
        }
        float err = Fabrikf.solve(p, 5, null, 0, 0, (float) (2 * total), 1e-4f, 50, null);
        assertEquals(total, err, 1e-4 * total);
        for (int i = 0; i < 5; i++) {
            assertEquals(0f, p[3 * i], 1e-5f);
            assertEquals(0f, p[3 * i + 1], 1e-5f);
        }
        // bend limits: every bend between 10 and 30 degrees
        float cos30 = (float) Math.cos(Math.toRadians(30)), cos10 = (float) Math.cos(Math.toRadians(10));
        float lo = (float) Math.toRadians(10), hi = (float) Math.toRadians(30);
        float[] limits = {0, 0, lo, hi, lo, hi, lo, hi, lo, hi, lo, hi};
        for (int t = 0; t < 100; t++) {
            float[] q = chain(r, 6);
            Fabrikf.solve(q, 6, null, (float) r.nextDouble(-3, 3), (float) r.nextDouble(-3, 3), (float) r.nextDouble(-3, 3),
                    1e-4f, 50, limits);
            for (int i = 1; i < 5; i++) {
                Vector3d b0 = new Vector3d(q[3 * i] - q[3 * i - 3], q[3 * i + 1] - q[3 * i - 2], q[3 * i + 2] - q[3 * i - 1]);
                Vector3d b1 = new Vector3d(q[3 * i + 3] - q[3 * i], q[3 * i + 4] - q[3 * i + 1], q[3 * i + 5] - q[3 * i + 2]);
                double cb = b0.dot(b1) / (b0.length() * b1.length());
                assertTrue(cb >= cos30 - 1e-4 && cb <= cos10 + 1e-4, "bend limits at joint " + i + ": " + Math.toDegrees(Math.acos(Math.min(1, cb))));
            }
        }
    }

    @Test
    void ccdAndDampedLeastSquares() {
        SplittableRandom r = new SplittableRandom(3);
        for (int t = 0; t < 200; t++) {
            int n = 3 + r.nextInt(5);
            float[] p0 = chain(r, n);
            double[] l0 = lengths(p0, n);
            double total = 0;
            for (double v : l0) {
                total += v;
            }
            double inner = Math.max(0, 2 * java.util.Arrays.stream(l0).max().getAsDouble() - total);
            Vector3d dir = rnd3(r, 1).normalize();
            double reach = inner + (total - inner) * r.nextDouble(0.15, 0.85);
            double tx = dir.x * reach, ty = dir.y * reach, tz = dir.z * reach;
            // CCD in double, with per-bone deltas
            double[] pd = new double[3 * n];
            for (int i = 0; i < 3 * n; i++) {
                pd[i] = p0[i];
            }
            double[] deltas = new double[4 * (n - 1)];
            for (int i = 0; i < n - 1; i++) {
                deltas[4 * i + 3] = 1;
            }
            double err = Ccdd.solve(pd, n, tx, ty, tz, 1e-9, 500, 0, deltas);
            assertTrue(err <= 1e-6, "CCD converged: " + err);
            for (int i = 0; i < n - 1; i++) {
                Vector3d orig = new Vector3d((double) p0[3 * i + 3] - p0[3 * i], (double) p0[3 * i + 4] - p0[3 * i + 1],
                        (double) p0[3 * i + 5] - p0[3 * i + 2]);
                Vector3d fin = new Vector3d(pd[3 * i + 3] - pd[3 * i], pd[3 * i + 4] - pd[3 * i + 1], pd[3 * i + 5] - pd[3 * i + 2]);
                Quaterniond dq = new Quaterniond(deltas[4 * i], deltas[4 * i + 1], deltas[4 * i + 2], deltas[4 * i + 3]);
                assertEquals(orig.length(), fin.length(), 1e-12 * (1 + orig.length()), "CCD bone length");
                assertTrue(dq.transform(orig, new Vector3d()).distance(fin) < 1e-10, "CCD delta reproduces bone " + i);
            }
            // DLS in double
            double[] pl = new double[3 * n];
            for (int i = 0; i < 3 * n; i++) {
                pl[i] = p0[i];
            }
            double derr = DampedLeastSquaresd.solve(pl, n, tx, ty, tz, 0.05, 0.5, 1e-9, 2000, new double[3 * (n - 1)]);
            assertTrue(derr <= 1e-6, "DLS converged: " + derr);
            for (int i = 0; i < n - 1; i++) {
                double dx = pl[3 * i + 3] - pl[3 * i], dy = pl[3 * i + 4] - pl[3 * i + 1], dz = pl[3 * i + 5] - pl[3 * i + 2];
                assertEquals(l0[i], Math.sqrt(dx * dx + dy * dy + dz * dz), 1e-6 * (1 + l0[i]), "DLS bone length");
            }
        }
        // singular: a straight chain asked to reach beyond its end along its own axis stays finite
        double[] straight = {0, 0, 0, 1, 0, 0, 2, 0, 0};
        double e = DampedLeastSquaresd.solve(straight, 3, 5, 0, 0, 0.05, 0.5, 1e-9, 100, new double[6]);
        assertEquals(3.0, e, 1e-9);
        assertTrue(Double.isFinite(straight[6]));
    }

    @Test
    void jointLimits() {
        SplittableRandom r = new SplittableRandom(4);
        Vector3d axis = new Vector3d(0, 1, 0), sa = new Vector3d(1, 0, 0);
        double limA = 0.6, limB = 0.3, minT = -0.4, maxT = 0.9;
        Quaterniond out = new Quaterniond(), again = new Quaterniond();
        for (int t = 0; t < 5000; t++) {
            Quaterniond q = new Quaterniond(r.nextGaussian(), r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            boolean clamped = JointLimitsd.clampSwingTwist(q, axis, sa, limA, limB, minT, maxT, out);
            double tw = JointLimitsd.twistAngle(out, axis);
            assertTrue(tw >= minT - 1e-12 && tw <= maxT + 1e-12, "twist within range");
            // swing of the result inside the ellipse
            Quaterniond twist = new Quaterniond().fromAxisAngleRad(axis.x, axis.y, axis.z, tw);
            Quaterniond swing = new Quaterniond(out).mul(new Quaterniond(twist).conjugate());
            if (swing.w < 0) {
                swing.set(-swing.x, -swing.y, -swing.z, -swing.w);
            }
            double sl = Math.sqrt(swing.x * swing.x + swing.y * swing.y + swing.z * swing.z);
            double ang = 2 * Math.atan2(sl, swing.w);
            double va = sl == 0 ? 0 : swing.x / sl * ang, vb = sl == 0 ? 0 : -swing.z / sl * ang;
            assertTrue((va / limA) * (va / limA) + (vb / limB) * (vb / limB) <= 1 + 1e-9, "swing within the cone");
            // idempotent; unclamped inputs unchanged
            assertFalse(JointLimitsd.clampSwingTwist(out, axis, sa, limA, limB, minT, maxT, again) && clamped
                    && Math.abs(Math.abs(again.dot(out)) - 1) > 1e-12, "projection is idempotent");
            if (!clamped) {
                assertEquals(1.0, Math.abs(out.dot(q)), 1e-15);
            }
        }
        // hinge: a pure rotation about the axis, angle clamped
        Quaterniond q = new Quaterniond().rotateY(1.2).rotateX(0.3);
        JointLimitsd.clampHinge(q, axis, -0.5, 0.8, out);
        assertEquals(0.0, out.x, 1e-15);
        assertEquals(0.0, out.z, 1e-15);
        assertEquals(0.8, JointLimitsd.twistAngle(out, axis), 1e-12);
    }

    @Test
    void aimPointsExactlyWithControlledRoll() {
        SplittableRandom r = new SplittableRandom(5);
        Quaterniond q = new Quaterniond();
        Vector3d worldUp = new Vector3d(0, 1, 0);
        for (int t = 0; t < 2000; t++) {
            Vector3d aim = rnd3(r, 1).normalize(), up = rnd3(r, 1);
            Vector3d to = rnd3(r, 1).normalize();
            Aimd.aim(aim, up, to, worldUp, q);
            Vector3d na = q.transform(new Vector3d(aim), new Vector3d());
            assertTrue(na.distance(to) < 1e-12, "aims exactly");
            Vector3d upPerp = new Vector3d(up).sub(new Vector3d(aim).mul(up.dot(aim))).normalize();
            Vector3d nu = q.transform(upPerp, new Vector3d());
            Vector3d plane = new Vector3d(to).cross(worldUp).normalize();
            assertEquals(0.0, nu.dot(plane), 1e-12, "up stays in the target/world-up plane");
            assertTrue(nu.dot(worldUp) > 0, "up on world-up's side");
        }
        // target along world up: still aims exactly
        Aimd.aim(new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 1, 0), worldUp, q);
        assertTrue(q.transform(new Vector3d(1, 0, 0), new Vector3d()).distance(0, 1, 0) < 1e-15);
    }
}
