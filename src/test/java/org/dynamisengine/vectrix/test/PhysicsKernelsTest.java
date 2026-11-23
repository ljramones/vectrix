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

import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Matrix4x3d;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.geometry.BoundingVolumesd;
import org.dynamisengine.vectrix.geometry.ClosestPointsd;
import org.dynamisengine.vectrix.geometry.Contactsd;
import org.dynamisengine.vectrix.geometry.GeometryUtils;
import org.dynamisengine.vectrix.geometry.Intersectiond;
import org.dynamisengine.vectrix.physics.InertiaTensord;
import org.dynamisengine.vectrix.physics.MassPropertiesd;
import org.dynamisengine.vectrix.physics.Pacejkad;
import org.dynamisengine.vectrix.physics.SpringDamperd;
import org.dynamisengine.vectrix.physics.SpringDamperf;
import org.junit.jupiter.api.Test;

/** Physics phase-1 kernels (2.4.0) against independent references. */
class PhysicsKernelsTest {

    /** x'' = -k x - c v by RK4 with many substeps. */
    private static double[] rk4(double x, double v, double k, double c, double t, int n) {
        double h = t / n;
        for (int i = 0; i < n; i++) {
            double k1x = v, k1v = -k * x - c * v;
            double k2x = v + 0.5 * h * k1v, k2v = -k * (x + 0.5 * h * k1x) - c * (v + 0.5 * h * k1v);
            double k3x = v + 0.5 * h * k2v, k3v = -k * (x + 0.5 * h * k2x) - c * (v + 0.5 * h * k2v);
            double k4x = v + h * k3v, k4v = -k * (x + h * k3x) - c * (v + h * k3v);
            x += h / 6 * (k1x + 2 * k2x + 2 * k3x + k4x);
            v += h / 6 * (k1v + 2 * k2v + 2 * k3v + k4v);
        }
        return new double[] {x, v};
    }

    @Test
    void dampedSpringStepIsExactInEveryRegime() {
        double[] vel = new double[1];
        // underdamped, near-critical on both sides of the series switch, critical, overdamped, strongly overdamped
        double[][] cases = {{100, 2}, {100, 19.9}, {100, 20}, {100, 20.1}, {100, 60}, {100, 1e4}, {1, 0}, {0, 3}};
        for (double[] kc : cases) {
            for (double dt : new double[] {0.001, 0.016, 0.1, 0.7}) {
                double k = kc[0], c = kc[1], x0 = 0.8, v0 = -1.7;
                double x = SpringDamperd.dampedStep(x0 + 3, 3, v0, k, c, dt, vel) - 3;
                double v = vel[0];
                double[] ref = rk4(x0, v0, k, c, dt, c > 1000 ? 2_000_000 : 100_000);
                double scale = 1 + Math.abs(x0) + Math.abs(v0);
                String ctx = "k " + k + " c " + c + " dt " + dt;
                assertEquals(ref[0], x, 1e-11 * scale, "x " + ctx);
                assertEquals(ref[1], v, 1e-9 * scale * (1 + c), "v " + ctx);
                // composition: one step equals two half steps
                double xh = SpringDamperd.dampedStep(x0, 0, v0, k, c, dt / 2, vel);
                double x2 = SpringDamperd.dampedStep(xh, 0, vel[0], k, c, dt / 2, vel);
                assertEquals(x, x2, 1e-14 * scale, "composition " + ctx);
            }
        }
        // the series/closed-form boundary is continuous: u t^2 = +-0.25 approached from both sides
        for (double sign : new double[] {1, -1}) {
            double k = 100, t = 0.05, g = Math.sqrt(k + sign * 0.25 / (t * t));
            double below = SpringDamperd.dampedStep(1, 0, 0, k, 2 * g * (1 - 1e-12), t, vel);
            double above = SpringDamperd.dampedStep(1, 0, 0, k, 2 * g * (1 + 1e-12), t, vel);
            assertEquals(below, above, 1e-13, "branch switch " + sign);
        }
        // a very stiff spring with a large step stays bounded (the Euler step explodes)
        double xs = SpringDamperd.dampedStep(1, 0, 0, 1e8, 10, 0.1, vel);
        assertTrue(Math.abs(xs) <= 1 && Math.abs(vel[0]) <= 1e4, "stiff " + xs + " " + vel[0]);
        // critically damped equals the general step with c = 2 w, and is exact
        float[] vf = new float[1];
        float cd = SpringDamperf.criticallyDamped(1, 0, 0, 10, 0.1f, vf);
        assertEquals((1 + 10 * 0.1) * Math.exp(-1), cd, 1e-7);
        assertEquals(SpringDamperd.dampedStep(1, 0, 0, 100, 20, 0.1, vel), cd, 1e-7);
        assertEquals(2 * Math.sqrt(50 * 2), SpringDamperd.criticalDamping(50, 2), 1e-12);
        assertEquals(2 * Math.PI * 2 * 2 * Math.PI * 2 * 3, SpringDamperd.stiffnessForFrequency(2, 3), 1e-9);
        assertEquals(20 * 0.5 + 3 * 2, SpringDamperd.suspensionForceFromCompression(0.5, 2, 20, 3), 1e-12);
        assertEquals(20 * (1 - 0.5) - 3 * 2, SpringDamperd.suspensionForceFromLength(0.5, 1, 2, 20, 3), 1e-12);
    }

    @Test
    void pacejka() {
        double b = 10, c = 1.9, d = 1, e = 0.97;
        for (double s = -1; s <= 1; s += 0.01) {
            double bx = b * s;
            assertEquals(d * Math.sin(c * Math.atan(bx - e * (bx - Math.atan(bx)))), Pacejkad.magicFormula(s, b, c, d, e), 1e-15);
        }
        assertEquals(Pacejkad.magicFormula(0.1, b, c, d, e) + 0.2, Pacejkad.magicFormula(0.05, b, c, d, e, 0.05, 0.2), 1e-15);
        assertEquals(0.1, Pacejkad.slipRatio(11, 1, 10, 0.5), 1e-15, "driving forward");
        assertEquals(-0.1, Pacejkad.slipRatio(9, 1, 10, 0.5), 1e-15, "braking forward");
        // the sign is that of the force along the forward axis: driving in reverse pushes backward
        assertEquals(-0.1, Pacejkad.slipRatio(-11, 1, -10, 0.5), 1e-15, "driving in reverse");
        assertEquals(0.1, Pacejkad.slipRatio(-9, 1, -10, 0.5), 1e-15, "braking in reverse");
        assertEquals(Math.PI / 2, Pacejkad.slipAngle(3, 0), 0.0, "sliding sideways");
        assertEquals(Math.atan(0.5), Pacejkad.slipAngle(1, -2), 1e-16, "reversing");
        assertEquals(0.0, Pacejkad.slipAngle(0, 0), 0.0);
    }

    /** A box mesh (12 outward triangles). */
    private static double[] boxMesh(double hx, double hy, double hz, Matrix4x3d m, int[] indicesOut) {
        double[] v = new double[24];
        for (int i = 0; i < 8; i++) {
            Vector3d p = m.transformPosition(new Vector3d((i & 1) == 0 ? -hx : hx, (i & 2) == 0 ? -hy : hy, (i & 4) == 0 ? -hz : hz));
            v[3 * i] = p.x;
            v[3 * i + 1] = p.y;
            v[3 * i + 2] = p.z;
        }
        int[] idx = {0, 2, 3, 0, 3, 1, 4, 5, 7, 4, 7, 6, 0, 1, 5, 0, 5, 4, 2, 6, 7, 2, 7, 3, 0, 4, 6, 0, 6, 2, 1, 3, 7, 1, 7, 5};
        System.arraycopy(idx, 0, indicesOut, 0, idx.length);
        return v;
    }

    @Test
    void polyhedralMassPropertiesMatchTheBoxFarFromTheOrigin() {
        int[] idx = new int[36];
        Quaterniond q = new Quaterniond().rotateXYZ(0.4, -1.1, 2.3);
        Vector3d at = new Vector3d(1e5, -2e4, 3e5);
        Matrix4x3d m = new Matrix4x3d().translationRotate(at.x, at.y, at.z, q);
        double hx = 1.5, hy = 0.25, hz = 0.8, density = 7.8;
        double[] v = boxMesh(hx, hy, hz, m, idx);
        Vector3d com = new Vector3d();
        Matrix3d inertia = new Matrix3d();
        double mass = MassPropertiesd.polyhedron(v, 8, idx, 12, density, com, inertia);
        double vol = MassPropertiesd.boxVolume(hx, hy, hz);
        // the vertices themselves round at |at| * 1.1e-16 = 3.5e-11, about 1e-10 of the box size
        assertEquals(density * vol, mass, 1e-9 * mass, "mass");
        assertTrue(com.distance(at) < 1e-9, "center of mass " + com);
        Vector3d moments = InertiaTensord.box(mass, hx, hy, hz, new Vector3d());
        Matrix3d local = new Matrix3d().scaling(moments.x, moments.y, moments.z);
        Matrix3d expect = MassPropertiesd.rotate(local, new Matrix3d().set(q), new Matrix3d());
        assertTrue(maxDiff(expect, inertia) < 1e-9 * moments.x, "inertia\n" + expect + "\n" + inertia);
        // combining two half boxes gives the whole box; the parallel-axis shift matches
        Matrix3d half = new Matrix3d().scaling(InertiaTensord.box(mass / 2, hx / 2, hy, hz, new Vector3d()));
        Vector3d cOut = new Vector3d();
        Matrix3d iOut = new Matrix3d();
        double mm = MassPropertiesd.combine(mass / 2, new Vector3d(-hx / 2, 0, 0), half, mass / 2, new Vector3d(hx / 2, 0, 0), half,
                cOut, iOut);
        assertEquals(mass, mm, 1e-12);
        assertTrue(cOut.length() < 1e-15 && maxDiff(local, iOut) < 1e-12 * moments.x, "combine\n" + iOut);
        Matrix3d shifted = MassPropertiesd.parallelAxis(half, mass / 2, new Vector3d(hx / 2, 0, 0), new Matrix3d());
        assertTrue(maxDiff(local, shifted.add(shifted)) < 1e-12 * moments.x, "parallel axis");
        assertEquals(Math.PI * 4 * (3 + 4.0 / 3 * 2), MassPropertiesd.capsuleVolume(2, 3), 1e-12);
    }

    private static double maxDiff(Matrix3d a, Matrix3d b) {
        double m = 0;
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                m = Math.max(m, Math.abs(a.get(c, r) - b.get(c, r)));
            }
        }
        return m;
    }

    private static double segBox(Vector3d a, Vector3d b, Vector3d min, Vector3d max, double s) {
        Vector3d p = new Vector3d(a).lerp(b, s);
        return ClosestPointsd.distanceSquaredPointAab(p, min, max);
    }

    @Test
    void segmentAabIsExact() {
        SplittableRandom r = new SplittableRandom(6);
        Vector3d on1 = new Vector3d(), on2 = new Vector3d();
        for (int i = 0; i < 20000; i++) {
            Vector3d min = new Vector3d(r.nextDouble(-2, 0), r.nextDouble(-2, 0), r.nextDouble(-2, 0));
            Vector3d max = new Vector3d(min).add(r.nextDouble(0, 3), r.nextDouble(0, 3), r.nextDouble(0, 3));
            Vector3d a = new Vector3d(r.nextDouble(-5, 5), r.nextDouble(-5, 5), r.nextDouble(-5, 5));
            Vector3d b = i % 10 == 0 ? new Vector3d(a) : new Vector3d(r.nextDouble(-5, 5), r.nextDouble(-5, 5), r.nextDouble(-5, 5));
            double lo = 0, hi = 1;
            for (int k = 0; k < 300; k++) { // the squared distance is convex along the segment
                double m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
                if (segBox(a, b, min, max, m1) <= segBox(a, b, min, max, m2)) {
                    hi = m2;
                } else {
                    lo = m1;
                }
            }
            double ref = segBox(a, b, min, max, (lo + hi) / 2);
            double got = ClosestPointsd.segmentAab(a, b, min, max, on1, on2);
            assertEquals(ref, got, 1e-12 * (1 + ref), "case " + i);
            assertEquals(got, on1.distanceSquared(on2), 1e-12 * (1 + got), "points " + i);
            assertEquals(0.0, ClosestPointsd.distanceSquaredPointAab(on2, min, max), 0.0, "onBox lies in the box");
        }
        // zero-length segments no longer give NaN in the plain closest-point routine
        Vector3d res = Intersectiond.findClosestPointOnLineSegment(1, 2, 3, 1, 2, 3, 9, 9, 9, new Vector3d());
        assertEquals(new Vector3d(1, 2, 3), res);
    }

    @Test
    void contactsAreMinimalTranslations() {
        Vector3d n = new Vector3d(), p = new Vector3d();
        // spheres
        assertEquals(1.0, Contactsd.sphereSphere(new Vector3d(0, 0, 0), 2, new Vector3d(3, 0, 0), 2, n, p), 1e-15);
        assertEquals(new Vector3d(1, 0, 0), n);
        assertEquals(new Vector3d(1.5, 0, 0), p);
        assertEquals(-1.0, Contactsd.sphereSphere(new Vector3d(0, 0, 0), 1, new Vector3d(0, 3, 0), 1, n, p), 1e-15);
        assertTrue(Contactsd.testSphereSphere(new Vector3d(), 5, new Vector3d(1, 0, 0), 1), "contained sphere overlaps");
        // boxes: B inside A along x needs the smaller of the two pushes, not the overlap width
        double dep = Contactsd.aabbAabb(new Vector3d(0, 0, 0), new Vector3d(10, 10, 10), new Vector3d(4, 1, 1), new Vector3d(5, 9.5, 9),
                n, p);
        assertEquals(5.0, dep, 1e-15, "the overlap width along x is 1, but separating along x needs 5");
        assertEquals(new Vector3d(-1, 0, 0), n);
        assertEquals(-Math.sqrt(2), Contactsd.aabbAabb(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1), new Vector3d(2, 2, 0),
                new Vector3d(3, 3, 1), n, p), 1e-15);
        // sphere center inside a box: pushed out through the nearest face
        assertEquals(1.5, Contactsd.sphereAabb(new Vector3d(0.5, 0, 0), 1, new Vector3d(-1, -1, -1), new Vector3d(1, 1, 1), n, p), 1e-15);
        assertEquals(new Vector3d(-1, 0, 0), n);
        // capsules: after moving the box by depth along the normal they just touch, and no direction does better
        SplittableRandom r = new SplittableRandom(7);
        Vector3d s1 = new Vector3d(), s2 = new Vector3d();
        for (int i = 0; i < 3000; i++) {
            Vector3d min = new Vector3d(-1, -0.5, -0.8), max = new Vector3d(1, 0.5, 0.8);
            Vector3d a = new Vector3d(r.nextDouble(-1.5, 1.5), r.nextDouble(-1, 1), r.nextDouble(-1, 1));
            Vector3d b = new Vector3d(r.nextDouble(-1.5, 1.5), r.nextDouble(-1, 1), r.nextDouble(-1, 1));
            double rad = r.nextDouble(0.05, 0.4);
            double depth = Contactsd.capsuleAabb(a, b, rad, min, max, n, p);
            Vector3d moved = new Vector3d(n).mul(depth);
            double after = Math.sqrt(ClosestPointsd.segmentAab(a, b, new Vector3d(min).add(moved), new Vector3d(max).add(moved), s1, s2));
            assertEquals(rad, after, 1e-9, "touching after the push, case " + i);
            if (depth > rad) { // deep: compare with sampled directions
                for (int k = 0; k < 400; k++) {
                    Vector3d dir = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                    double sMax = Math.max(a.dot(dir), b.dot(dir));
                    double boxMin = -(1 * Math.abs(dir.x) + 0.5 * Math.abs(dir.y) + 0.8 * Math.abs(dir.z));
                    assertTrue(sMax - boxMin + rad >= depth - 1e-12, "a smaller push exists, case " + i);
                }
            }
        }
    }

    @Test
    void boundsAreTight() {
        SplittableRandom r = new SplittableRandom(8);
        Vector3d min = new Vector3d(), max = new Vector3d();
        for (int i = 0; i < 500; i++) {
            Quaterniond q = new Quaterniond().rotateXYZ(r.nextDouble(-3, 3), r.nextDouble(-3, 3), r.nextDouble(-3, 3));
            Matrix3d axes = new Matrix3d().set(q);
            Vector3d c = new Vector3d(r.nextDouble(-5, 5), r.nextDouble(-5, 5), r.nextDouble(-5, 5));
            Vector3d h = new Vector3d(r.nextDouble(0.1, 2), r.nextDouble(0.1, 2), r.nextDouble(0.1, 2));
            BoundingVolumesd.aabOfObb(c, axes, h, min, max);
            Vector3d lo = new Vector3d(Double.MAX_VALUE), hi = new Vector3d(-Double.MAX_VALUE);
            for (int k = 0; k < 8; k++) {
                Vector3d corner = axes.transform(new Vector3d((k & 1) == 0 ? -h.x : h.x, (k & 2) == 0 ? -h.y : h.y, (k & 4) == 0 ? -h.z : h.z)).add(c);
                lo.min(corner);
                hi.max(corner);
            }
            assertTrue(lo.distance(min) < 1e-12 && hi.distance(max) < 1e-12, "obb " + i);
            // cylinder: the exact extent bounds a dense rim sampling and is reached by it
            Vector3d a = new Vector3d(c), b = new Vector3d(c).add(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
            double rad = r.nextDouble(0.1, 1);
            BoundingVolumesd.aabOfCylinder(a, b, rad, min, max);
            Vector3d u = new Vector3d(b).sub(a).normalize(), e1 = new Vector3d(), e2 = new Vector3d();
            GeometryUtils.perpendicular(u, e1, e2);
            assertEquals(0.0, e1.dot(u) + e2.dot(u) + e1.dot(e2), 1e-15);
            lo.set(Double.MAX_VALUE);
            hi.set(-Double.MAX_VALUE);
            for (int k = 0; k < 4096; k++) {
                double t = 2 * Math.PI * k / 4096;
                Vector3d off = new Vector3d(e1).mul(rad * Math.cos(t)).add(new Vector3d(e2).mul(rad * Math.sin(t)));
                lo.min(new Vector3d(a).add(off)).min(new Vector3d(b).add(off));
                hi.max(new Vector3d(a).add(off)).max(new Vector3d(b).add(off));
            }
            assertTrue(min.x <= lo.x + 1e-12 && min.y <= lo.y + 1e-12 && min.z <= lo.z + 1e-12, "cylinder contains");
            assertTrue(lo.distance(min) < 1e-6 && hi.distance(max) < 1e-6, "cylinder tight " + lo + " " + min);
        }
        double[] pts = {1, 2, 3, -4, 5, 6, 7, -8, 9};
        Matrix4x3d m = new Matrix4x3d().translation(10, 0, 0).rotateZ(Math.PI / 2);
        BoundingVolumesd.aabOfTransformedPoints(pts, 3, m, min, max);
        assertTrue(min.distance(10 - 5, -4, 3) < 1e-12 && max.distance(10 + 8, 7, 9) < 1e-12, min + " " + max);
    }
}
