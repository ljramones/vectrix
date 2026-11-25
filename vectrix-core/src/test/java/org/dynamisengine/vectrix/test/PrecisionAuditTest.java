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

import org.dynamisengine.vectrix.core.AxisAngle4d;
import org.dynamisengine.vectrix.core.AxisAngle4f;
import org.dynamisengine.vectrix.core.Math;
import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Matrix4x3d;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4d;
import org.junit.jupiter.api.Test;

/**
 * Precision audit of the JOML-inherited core against independent references (direct evaluation, exact constructions,
 * exact identities). Twin parity cannot see these: the float and double twins shared every one of them.
 */
class PrecisionAuditTest {

    /** Angle between unit quaternions as rotations, accurate at every size. */
    private static double angle(Quaterniond a, Quaterniond b) {
        Quaterniond d = new Quaterniond(a).conjugate().mul(b);
        return 2 * java.lang.Math.atan2(java.lang.Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z), java.lang.Math.abs(d.w));
    }

    @Test
    void cosFromSinIsAccurateEverywhere() {
        SplittableRandom r = new SplittableRandom(1);
        double worstD = 0, worstF = 0;
        double[] specials = {java.lang.Math.PI / 2 - 1e-8, java.lang.Math.PI / 2 + 1e-8, -java.lang.Math.PI / 2 + 1e-9,
            java.lang.Math.PI / 2 - 1e-6, 3 * java.lang.Math.PI / 2 - 1e-7, 2e10, -3e10, 1e15};
        for (int i = 0; i < 20000 + specials.length; i++) {
            double a = i < specials.length ? specials[i] : r.nextDouble(-50, 50);
            double ref = java.lang.Math.cos(a);
            worstD = java.lang.Math.max(worstD, java.lang.Math.abs(Math.cosFromSin(java.lang.Math.sin(a), a) - ref));
            float af = (float) a;
            float fref = (float) java.lang.Math.cos(af);
            worstF = java.lang.Math.max(worstF, java.lang.Math.abs(Math.cosFromSin((float) java.lang.Math.sin(af), af) - fref));
        }
        assertTrue(worstD <= 2.3e-16, "double cosFromSin worst absolute error " + worstD);
        assertTrue(worstF <= 1.2e-7, "float cosFromSin worst absolute error " + worstF);
        // a rotation near 180 degrees keeps its angle
        assertEquals(java.lang.Math.PI - 2e-4, new Quaternionf().rotationZ((float) (java.lang.Math.PI - 2e-4)).angle(), 3e-7);
        assertEquals(java.lang.Math.PI - 1e-9, new Quaterniond().rotationZ(java.lang.Math.PI - 1e-9).angle(), 1e-15);
    }

    @Test
    void axisAngleFromMatrixAndQuaternion() {
        Vector3d axis = new Vector3d(0.3, -0.5, 0.8).normalize();
        for (double th : new double[] {1e-12, 1e-8, 1e-6, 4e-5, 1e-4, 0.3, 2, java.lang.Math.PI - 1e-3,
            java.lang.Math.PI - 4e-5, java.lang.Math.PI - 1e-7}) {
            Matrix3d m = new Matrix3d().rotation(th, axis);
            AxisAngle4d aa = new AxisAngle4d().set(m);
            assertEquals(th, aa.angle, 4e-15 + 1e-15 * th, "angle from matrix " + th);
            assertTrue(new Vector3d(aa.x, aa.y, aa.z).distance(axis) < 1e-9, "axis from matrix " + th);
            Quaterniond q = new Quaterniond().fromAxisAngleRad(axis, th);
            AxisAngle4d qa = new AxisAngle4d().set(q);
            assertEquals(th, qa.angle, 1e-15 * (1 + th), "angle from quaternion " + th);
            assertEquals(1.0, new Vector3d(qa.x, qa.y, qa.z).length(), 1e-15, "unit axis " + th);
        }
        AxisAngle4f f = new AxisAngle4f().set(new Quaternionf().rotationAxis(1e-4f, 0, 0, 1));
        assertEquals(1e-4f, f.angle, 1e-10f);
    }

    @Test
    void rotationToNearAntiparallel() {
        Vector3d from = new Vector3d(1, 1, 1).normalize();
        Vector3d perp = new Vector3d(1, -1, 0).normalize();
        for (double eps : new double[] {0, 1e-9, 1e-6, 1e-3}) {
            Vector3d to = new Quaterniond().rotationAxis(java.lang.Math.PI - eps, perp.x, perp.y, perp.z).transform(new Vector3d(from));
            Quaterniond q = new Quaterniond().rotationTo(from, to);
            assertEquals(1.0, java.lang.Math.sqrt(q.lengthSquared()), 1e-15, "unit at " + eps);
            assertTrue(q.transform(new Vector3d(from)).distance(to) < 1e-14, "maps from onto to at " + eps);
        }
    }

    @Test
    void vectorAngles() {
        for (double th : new double[] {1e-12, 1e-8, 1e-6, 0.5, java.lang.Math.PI - 1e-8, java.lang.Math.PI - 1e-12}) {
            Vector3d a = new Vector3d(1, 0, 0), b = new Vector3d(java.lang.Math.cos(th), java.lang.Math.sin(th), 0);
            assertEquals(th, a.angle(b), 1e-15 * (1 + th) + 1e-20, "3D angle " + th);
            Vector4d a4 = new Vector4d(1, 0, 0, 0), b4 = new Vector4d(java.lang.Math.cos(th), 0, 0, java.lang.Math.sin(th));
            assertEquals(th, a4.angle(b4), 1e-15 * (1 + th) + 1e-20, "4D angle " + th);
        }
        assertEquals(1e-3f, new Vector3f(1, 0, 0).angle(new Vector3f((float) java.lang.Math.cos(1e-3), (float) java.lang.Math.sin(1e-3),
                0)), 1e-9f);
    }

    @Test
    void slerpAndNlerpIterative() {
        Quaterniond a = new Quaterniond().rotateXYZ(0.3, -0.2, 0.5);
        for (double om : new double[] {1e-9, 1e-6, 1.4e-3, 0.5, 3}) {
            Quaterniond b = new Quaterniond(a).rotateAxis(om, 0.2, 0.6, -0.7);
            for (double t : new double[] {0, 0.3, 0.75, 1}) {
                Quaterniond s = new Quaterniond(a).slerp(b, t);
                assertEquals(1.0, java.lang.Math.sqrt(s.lengthSquared()), 1e-15, "slerp unit, om " + om);
                assertEquals(t * om, angle(a, s), 1e-15 + 1e-13 * om, "slerp angle, om " + om + " t " + t);
                Quaterniond n = new Quaterniond(a).nlerpIterative(b, t, 1 - 1e-12);
                assertTrue(angle(s, n) < 1e-11, "nlerpIterative vs slerp, om " + om + " t " + t + ": " + angle(s, n));
            }
        }
    }

    @Test
    void eulerAnglesAtGimbalLock() {
        for (double pitch : new double[] {java.lang.Math.PI / 2 - 1e-9, java.lang.Math.PI / 2 - 1e-6, -java.lang.Math.PI / 2 + 1e-7, 0.4}) {
            Quaterniond q = new Quaterniond().rotationXYZ(0.3, pitch, -0.2);
            Vector3d e = q.getEulerAnglesXYZ(new Vector3d());
            assertEquals(pitch, e.y, 1e-14, "XYZ pitch " + pitch);
            // the recovered angles rebuild the same rotation
            assertTrue(angle(q, new Quaterniond().rotationXYZ(e.x, e.y, e.z)) < 1e-14, "XYZ round trip " + pitch);
            Quaterniond z = new Quaterniond().rotationZYX(0.3, pitch, -0.2);
            Vector3d ez = z.getEulerAnglesZYX(new Vector3d());
            assertEquals(pitch, ez.y, 1e-14, "ZYX pitch " + pitch);
            assertTrue(angle(z, new Quaterniond().rotationZYX(ez.z, ez.y, ez.x)) < 1e-14, "ZYX round trip " + pitch);
            Quaterniond yx = new Quaterniond().rotationYXZ(0.3, pitch, -0.2);
            Vector3d ey = yx.getEulerAnglesYXZ(new Vector3d());
            assertEquals(pitch, ey.x, 1e-14, "YXZ pitch " + pitch);
            assertTrue(angle(yx, new Quaterniond().rotationYXZ(ey.y, ey.x, ey.z)) < 1e-14, "YXZ round trip " + pitch);
            Quaterniond zx = new Quaterniond().rotateZ(0.3).rotateX(pitch).rotateY(-0.2);
            Vector3d ezx = zx.getEulerAnglesZXY(new Vector3d());
            assertEquals(pitch, ezx.x, 1e-14, "ZXY pitch " + pitch);
            assertTrue(angle(zx, new Quaterniond().rotateZ(ezx.z).rotateX(ezx.x).rotateY(ezx.y)) < 1e-14, "ZXY round trip " + pitch);
        }
        // exactly at gimbal lock the split is arbitrary but the rotation is rebuilt exactly
        Quaterniond lock = new Quaterniond().rotationXYZ(0.3, java.lang.Math.PI / 2, 0.2);
        Vector3d el = lock.getEulerAnglesXYZ(new Vector3d());
        assertTrue(angle(lock, new Quaterniond().rotationXYZ(el.x, el.y, el.z)) < 1e-14, "exact gimbal lock");
    }

    @Test
    void lengthsAndNormalizationNeverOverflow() {
        Vector3d big = new Vector3d(1e200, 1e200, 0);
        assertEquals(java.lang.Math.sqrt(2) * 1e200, big.length(), 1e185);
        assertTrue(new Vector3d(big).normalize().distance(1 / java.lang.Math.sqrt(2), 1 / java.lang.Math.sqrt(2), 0) < 1e-15);
        assertTrue(new Vector3d(1e-170, 0, 0).normalize().distance(1, 0, 0) < 1e-15);
        assertEquals(java.lang.Math.PI / 4, big.angle(new Vector3d(1e200, 0, 0)), 1e-15);
        assertEquals(1e-21f, new Vector3f(1e-21f, 0, 0).length(), 1e-28f);
    }

    @Test
    void lerpHitsItsEndpoints() {
        assertEquals(1.0, Math.lerp(1e20, 1.0, 1.0), 0.0);
        assertEquals(3.0, Math.lerp(-1e16, 3.0, 1.0), 0.0);
        assertEquals(1e20, Math.lerp(1e20, 1.0, 0.0), 0.0);
        assertEquals(1f, Math.lerp(1e8f, 1f, 1f), 0f);
    }

    private static double orthonormalityError(Matrix4d m) {
        Matrix3d r = new Matrix3d();
        m.get3x3(r);
        Matrix3d rtr = new Matrix3d(r).transpose().mul(r);
        double e = 0;
        for (int c = 0; c < 3; c++) {
            for (int rr = 0; rr < 3; rr++) {
                e = java.lang.Math.max(e, java.lang.Math.abs(rtr.get(c, rr) - (c == rr ? 1 : 0)));
            }
        }
        return e;
    }

    @Test
    void matrixFixes() {
        // affineSpan: the inverse applied to the clip cube (an operator-precedence bug before 2.4.0)
        Matrix4d m = new Matrix4d().translation(1, 2, 3).rotateXYZ(0.3, -0.2, 0.5).scale(2, 3, 4);
        Vector3d corner = new Vector3d(), xd = new Vector3d(), yd = new Vector3d(), zd = new Vector3d();
        m.affineSpan(corner, xd, yd, zd);
        Matrix4d inv = new Matrix4d(m).invert();
        assertTrue(corner.distance(inv.transformPosition(new Vector3d(-1, -1, -1))) < 1e-14, "affineSpan corner " + corner);
        assertTrue(xd.distance(inv.transformDirection(new Vector3d(2, 0, 0))) < 1e-14, "affineSpan x direction");
        // Euler angles from matrices, next to and at gimbal lock, and with scale
        for (double pitch : new double[] {java.lang.Math.PI / 2 - 1e-9, java.lang.Math.PI / 2, -java.lang.Math.PI / 2 + 1e-7, 0.4}) {
            Matrix4d r = new Matrix4d().rotationXYZ(0.3, pitch, -0.2).scale(2, 0.5, 3);
            Vector3d e = r.getEulerAnglesXYZ(new Vector3d());
            assertTrue(Double.isFinite(e.x + e.y + e.z), "finite at " + pitch);
            Matrix4d back = new Matrix4d().rotationXYZ(e.x, e.y, e.z).scale(2, 0.5, 3);
            assertTrue(back.equals(r, 1e-14), "matrix XYZ round trip at " + pitch);
            Matrix4d rz = new Matrix4d().rotationZYX(0.3, pitch, -0.2);
            Vector3d ez = rz.getEulerAnglesZYX(new Vector3d());
            assertTrue(new Matrix4d().rotationZYX(ez.z, ez.y, ez.x).equals(rz, 1e-14), "matrix ZYX round trip at " + pitch);
            Matrix4d ry = new Matrix4d().rotationYXZ(0.3, pitch, -0.2);
            Vector3d ey = ry.getEulerAnglesYXZ(new Vector3d());
            assertTrue(new Matrix4d().rotationYXZ(ey.y, ey.x, ey.z).equals(ry, 1e-14), "matrix YXZ round trip at " + pitch);
        }
        // normal(): the rotation part only, also on the orthonormal fast path
        Matrix4d n = new Matrix4d().translation(10, 20, 30).rotateY(0.5).normal();
        assertEquals(0.0, n.m30() + n.m31() + n.m32(), 0.0);
        Matrix4x3d n3 = new Matrix4x3d().translation(10, 20, 30).rotateY(0.5).normal();
        assertEquals(0.0, n3.m30() + n3.m31() + n3.m32(), 0.0);
        // billboardSpherical: +Z maps exactly onto the target direction, also nearly and exactly behind
        for (double off : new double[] {0.5, 1e-7, 1e-9, 1e-12, 0}) {
            Vector3d target = new Vector3d(java.lang.Math.sin(off), 0, -java.lang.Math.cos(off));
            Matrix4d b = new Matrix4d().billboardSpherical(new Vector3d(), target);
            Vector3d z = b.transformDirection(new Vector3d(0, 0, 1));
            assertTrue(z.distance(target) < 1e-15, "billboard +Z at " + off + ": " + z);
            // a few ulps, as for any rotation built from a unit quaternion (8e-11 at 1e-7 rad before 2.4.0)
            assertTrue(orthonormalityError(b) < 2e-15, "billboard orthonormal at " + off);
        }
        // lookAt: orthonormal as up approaches the view direction
        Vector3d dir = new Vector3d(0.3, -0.5, 0.8).normalize();
        Vector3d perp = new Vector3d(1, 0, 0).sub(new Vector3d(dir).mul(dir.x)).normalize();
        for (double a : new double[] {1e-2, 1e-6, 1e-8}) {
            Vector3d up = new Vector3d(dir).mul(java.lang.Math.cos(a)).add(new Vector3d(perp).mul(java.lang.Math.sin(a)));
            Matrix4d l = new Matrix4d().setLookAt(0, 0, 0, dir.x, dir.y, dir.z, up.x, up.y, up.z);
            assertTrue(orthonormalityError(l) < 1e-15, "lookAt orthonormal at " + a + ": " + orthonormalityError(l));
        }
        // perspectiveFov recovers narrow fields of view; invertPerspective uses exact reciprocals
        for (double fov : new double[] {1e-6, 1e-4, 0.8, 2.5}) {
            Matrix4d pr = new Matrix4d().perspective(fov, 1.5, 0.1, 100);
            assertEquals(fov, pr.perspectiveFov(), 1e-12 * fov, "fov " + fov);
            Matrix4d ip = pr.invertPerspective(new Matrix4d());
            assertEquals(1.0 / pr.m00(), ip.m00(), 0.0);
            assertEquals(1.0 / pr.m11(), ip.m11(), 0.0);
        }
        assertEquals(1e-6 - 1, new Matrix4d().perspective(1, 1, 0.1, Double.POSITIVE_INFINITY).m22(), 0.0, "epsilon 1e-6, not 1e-6f");
    }
}
