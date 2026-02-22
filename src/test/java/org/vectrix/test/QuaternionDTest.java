/*
 * The MIT License
 *
 * Copyright (c) 2015-2024 JOML.
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
package org.vectrix.test;

import org.vectrix.core.*;
import org.vectrix.geometry.*;
import org.vectrix.core.Math;
import org.vectrix.experimental.KernelConfig;
import org.junit.jupiter.api.Test;

import static org.vectrix.test.TestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link Quaterniond}.
 * @author Sebastian Fellner
 */
class QuaternionDTest {
    private static Quaterniond unitQuat(double x, double y, double z, double w) {
        return new Quaterniond(x, y, z, w).normalize();
    }

    private static boolean approxEqualOrNegated(Quaterniond a, Quaterniond b, double eps) {
        boolean direct = Math.abs(a.x - b.x) < eps
                && Math.abs(a.y - b.y) < eps
                && Math.abs(a.z - b.z) < eps
                && Math.abs(a.w - b.w) < eps;
        boolean negated = Math.abs(a.x + b.x) < eps
                && Math.abs(a.y + b.y) < eps
                && Math.abs(a.z + b.z) < eps
                && Math.abs(a.w + b.w) < eps;
        return direct || negated;
    }

    @Test
    void testMulQuaternionDQuaternionDQuaternionD() {
        // Multiplication with the identity quaternion should change nothing
        Quaterniond testQuat = new Quaterniond(1, 23.3, -7.57, 2.1);
        Quaterniond identityQuat = new Quaterniond().identity();
        Quaterniond resultQuat = new Quaterniond();
        
        testQuat.mul(identityQuat, resultQuat);
        assertTrue(quatEqual(testQuat, resultQuat, STANDARD_AROUND_ZERO_PRECISION_DOUBLE));
        
        identityQuat.mul(testQuat, resultQuat);
        assertTrue(quatEqual(testQuat, resultQuat, STANDARD_AROUND_ZERO_PRECISION_DOUBLE));
        
        // Multiplication with conjugate should give (0, 0, 0, dot(this, this))
        Quaterniond conjugate = new Quaterniond();
        testQuat.conjugate(conjugate);
        testQuat.mul(conjugate, resultQuat);
        
        Quaterniond wantedResultQuat = new Quaterniond(0, 0, 0, testQuat.dot(testQuat));
        assertTrue(quatEqual(resultQuat, wantedResultQuat, MANY_OPS_AROUND_ZERO_PRECISION_DOUBLE));
    }

    @Test
    void testRotationXYZ() {
        Quaterniond v = new Quaterniond().rotationXYZ(0.12f, 0.521f, 0.951f);
        Matrix4f m = new Matrix4f().rotateXYZ(0.12f, 0.521f, 0.951f);
        Matrix4f n = new Matrix4f().set(v);
        assertMatrix4fEquals(m, n, 1E-6f);
    }

    @Test
    void testRotationZYX() {
        Quaterniond v = new Quaterniond().rotationZYX(0.12f, 0.521f, 0.951f);
        Matrix4f m = new Matrix4f().rotateZYX(0.12f, 0.521f, 0.951f);
        Matrix4f n = new Matrix4f().set(v);
        assertMatrix4fEquals(m, n, 1E-6f);
    }

    @Test
    void testRotationYXZ() {
        Quaterniond v = new Quaterniond().rotationYXZ(0.12f, 0.521f, 0.951f);
        Matrix4f m = new Matrix4f().rotationYXZ(0.12f, 0.521f, 0.951f);
        Matrix4f n = new Matrix4f().set(v);
        assertMatrix4fEquals(m, n, 1E-6f);
    }

    @Test
    void testRotateXYZ() {
        Quaterniond v = new Quaterniond().rotateXYZ(0.12f, 0.521f, 0.951f);
        Matrix4f m = new Matrix4f().rotateXYZ(0.12f, 0.521f, 0.951f);
        Matrix4f n = new Matrix4f().set(v);
        assertMatrix4fEquals(m, n, 1E-6f);
    }

    @Test
    void testRotateZYX() {
        Quaterniond v = new Quaterniond().rotateZYX(0.12f, 0.521f, 0.951f);
        Matrix4f m = new Matrix4f().rotateZYX(0.12f, 0.521f, 0.951f);
        Matrix4f n = new Matrix4f().set(v);
        assertMatrix4fEquals(m, n, 1E-6f);
    }

    @Test
    void testRotateYXZ() {
        Quaterniond v = new Quaterniond().rotateYXZ(0.12f, 0.521f, 0.951f);
        Matrix4f m = new Matrix4f().rotateYXZ(0.12f, 0.521f, 0.951f);
        Matrix4f n = new Matrix4f().set(v);
        assertMatrix4fEquals(m, n, 1E-6f);
    }

    @Test
    void testRotateToReturnsDestination() {
        Quaterniondc rotation = new Quaterniond();
        Quaterniond destination = new Quaterniond();
        Quaterniondc result = rotation.rotateTo(0, 1, 0, 0, 1, 0, destination);
        assertSame(destination, result);
    }

    @Test
    void testFromAxisAngle() {
        Vector3d axis = new Vector3d(1.0, 0.0, 0.0);
        double angleDeg = 45.0;
        double angleRad = java.lang.Math.toRadians(angleDeg);
        Quaterniondc fromRad1 = new Quaterniond().fromAxisAngleRad(axis, angleRad);
        Quaterniondc fromRad2 = new Quaterniond().fromAxisAngleRad(axis.x(), axis.y(), axis.z(), angleRad);
        Quaterniondc fromDeg = new Quaterniond().fromAxisAngleDeg(axis, angleDeg);
        assertEquals(fromRad1, fromRad2);
        assertEquals(fromRad2, fromDeg);
    }

    @Test
    void logIdentityQuaternionReturnsZeroTangent() {
        Quaterniond result = new Quaterniond().log(new Quaterniond());
        assertEquals(0.0, result.x, 1E-12);
        assertEquals(0.0, result.y, 1E-12);
        assertEquals(0.0, result.z, 1E-12);
        assertEquals(0.0, result.w, 1E-12);
    }

    @Test
    void expZeroTangentReturnsIdentity() {
        Quaterniond zero = new Quaterniond(0.0, 0.0, 0.0, 0.0);
        Quaterniond result = zero.exp(new Quaterniond());
        assertEquals(0.0, result.x, 1E-12);
        assertEquals(0.0, result.y, 1E-12);
        assertEquals(0.0, result.z, 1E-12);
        assertEquals(1.0, result.w, 1E-12);
    }

    @Test
    void expLogRoundtripArbitraryUnitQuaternions() {
        Quaterniond[] quats = {
                unitQuat(1.0, 0.0, 0.0, 1.0),
                unitQuat(0.0, 1.0, 0.0, 1.0),
                unitQuat(1.0, 1.0, 0.0, 1.0),
                unitQuat(1.0, 1.0, 1.0, 1.0),
                unitQuat(0.1, 0.2, 0.3, 0.9),
                unitQuat(-0.5, 0.5, -0.5, 0.5)
        };
        for (Quaterniond q : quats) {
            Quaterniond roundTrip = q.log(new Quaterniond()).exp(new Quaterniond());
            assertTrue(approxEqualOrNegated(q, roundTrip, 1E-12));
        }
    }

    @Test
    void logAxisAngleTangentMagnitudeEqualsHalfAngle() {
        double angle = java.lang.Math.PI * 0.5;
        Quaterniond q = new Quaterniond().rotationY(angle);
        Quaterniond logQ = q.log(new Quaterniond());
        double tangentMag = java.lang.Math.sqrt(Math.fma(logQ.x, logQ.x, Math.fma(logQ.y, logQ.y, logQ.z * logQ.z)));
        assertEquals(angle * 0.5, tangentMag, 1E-12);
        assertEquals(0.0, logQ.w, 1E-12);
    }

    @Test
    void squadDegeneratesToSlerpWhenControlsAreEndpoints() {
        Quaterniond q0 = unitQuat(1.0, 0.0, 0.0, 1.0);
        Quaterniond q1 = unitQuat(0.0, 1.0, 0.0, 1.0);
        double[] ts = {0.0, 0.25, 0.5, 0.75, 1.0};
        for (double t : ts) {
            Quaterniond slerp = q0.slerp(q1, t, new Quaterniond());
            Quaterniond squad = q0.squad(q1, q0, q1, t, new Quaterniond());
            assertTrue(approxEqualOrNegated(slerp, squad, 1E-12));
        }
    }

    @Test
    void squadAtEndpointsReturnsStartAndEnd() {
        Quaterniond q0 = unitQuat(1.0, 0.0, 0.0, 1.0);
        Quaterniond q1 = unitQuat(0.0, 1.0, 0.0, 1.0);
        Quaterniond s0 = q0.squadControlPoint(q0, q1, new Quaterniond());
        Quaterniond s1 = q1.squadControlPoint(q0, q1, new Quaterniond());
        Quaterniond atZero = q0.squad(q1, s0, s1, 0.0, new Quaterniond());
        Quaterniond atOne = q0.squad(q1, s0, s1, 1.0, new Quaterniond());
        assertTrue(approxEqualOrNegated(q0, atZero, 1E-12));
        assertTrue(approxEqualOrNegated(q1, atOne, 1E-12));
    }

    @Test
    void squadControlPointContinuityAtSegmentJoin() {
        Quaterniond q0 = unitQuat(1.0, 0.0, 0.0, 1.0);
        Quaterniond q1 = unitQuat(0.0, 1.0, 0.0, 1.0);
        Quaterniond q2 = unitQuat(0.0, 0.0, 1.0, 1.0);
        Quaterniond q3 = unitQuat(1.0, 1.0, 0.0, 1.0);

        Quaterniond s0 = q0.squadControlPoint(q0, q1, new Quaterniond());
        Quaterniond s1 = q1.squadControlPoint(q0, q2, new Quaterniond());
        Quaterniond s1b = q1.squadControlPoint(q0, q2, new Quaterniond());
        Quaterniond s2 = q2.squadControlPoint(q1, q3, new Quaterniond());

        double h = 1E-3;
        Quaterniond fwdA = q0.squad(q1, s0, s1, 1.0 - h, new Quaterniond());
        Quaterniond fwdB = q0.squad(q1, s0, s1, 1.0, new Quaterniond());
        Quaterniond bwdA = q1.squad(q2, s1b, s2, 0.0, new Quaterniond());
        Quaterniond bwdB = q1.squad(q2, s1b, s2, h, new Quaterniond());

        double dxRight = (fwdB.x - fwdA.x) / h;
        double dyRight = (fwdB.y - fwdA.y) / h;
        double dzRight = (fwdB.z - fwdA.z) / h;
        double dwRight = (fwdB.w - fwdA.w) / h;
        double dxLeft = (bwdB.x - bwdA.x) / h;
        double dyLeft = (bwdB.y - bwdA.y) / h;
        double dzLeft = (bwdB.z - bwdA.z) / h;
        double dwLeft = (bwdB.w - bwdA.w) / h;

        assertEquals(dxRight, dxLeft, 1E-2);
        assertEquals(dyRight, dyLeft, 1E-2);
        assertEquals(dzRight, dzLeft, 1E-2);
        assertEquals(dwRight, dwLeft, 1E-2);
    }

    @Test
    void logNearlyIdentityQuaternionNoNanOrInf() {
        Quaterniond nearIdentity = new Quaterniond(1E-12, 0.0, 0.0, 1.0).normalize();
        Quaterniond result = nearIdentity.log(new Quaterniond());
        assertFalse(Double.isNaN(result.x) || Double.isNaN(result.y) || Double.isNaN(result.z) || Double.isNaN(result.w));
        assertFalse(Double.isInfinite(result.x) || Double.isInfinite(result.y) || Double.isInfinite(result.z) || Double.isInfinite(result.w));
    }

    @Test
    void squadControlPointNearlyOppositeNeighborsNoNanOrInf() {
        Quaterniond curr = unitQuat(0.0, 0.0, 0.0, 1.0);
        Quaterniond prev = unitQuat(0.0, 1E-9, 0.0, -1.0);
        Quaterniond next = unitQuat(1E-9, 0.0, 0.0, -1.0);
        Quaterniond result = curr.squadControlPoint(prev, next, new Quaterniond());
        assertFalse(Double.isNaN(result.x) || Double.isNaN(result.y) || Double.isNaN(result.z) || Double.isNaN(result.w));
        assertFalse(Double.isInfinite(result.x) || Double.isInfinite(result.y) || Double.isInfinite(result.z) || Double.isInfinite(result.w));
    }

    @Test
    void logStrictModeThrowsOnNonUnitInput() {
        MathMode prev = KernelConfig.mathMode();
        try {
            KernelConfig.setMathMode(MathMode.STRICT);
            Quaterniond nonUnit = new Quaterniond(1.0, 1.0, 1.0, 1.0);
            assertThrows(IllegalArgumentException.class, () -> nonUnit.log(new Quaterniond()));
        } finally {
            KernelConfig.setMathMode(prev);
        }
    }

    @Test
    void expStrictModeThrowsOnNonPureInput() {
        MathMode prev = KernelConfig.mathMode();
        try {
            KernelConfig.setMathMode(MathMode.STRICT);
            Quaterniond nonPure = new Quaterniond(1.0, 0.0, 0.0, 1.0);
            assertThrows(IllegalArgumentException.class, () -> nonPure.exp(new Quaterniond()));
        } finally {
            KernelConfig.setMathMode(prev);
        }
    }

    @Test
    void testGetEulerAnglesXYZ() {
        Random rnd = new Random(1L);
        int failure = 0;
        int N = 30000;
        for (int i = 0; i < N; i++) {
            double x = (rnd.nextFloat() * 2.0 - 1.0) * Math.PI;
            double y = (rnd.nextFloat() * 2.0 - 1.0) * Math.PI;
            double z = (rnd.nextFloat() * 2.0 - 1.0) * Math.PI;
            Quaterniond p = new Quaterniond().rotateXYZ(x, y, z);
            Vector3d a = p.getEulerAnglesXYZ(new Vector3d());
            Quaterniond q = new Quaterniond().rotateX(a.x).rotateY(a.y).rotateZ(a.z);
            Vector3d v = new Vector3d(rnd.nextFloat()*2-1, rnd.nextFloat()*2-1, rnd.nextFloat()*2-1);
            Vector3d t1 = p.transform(v, new Vector3d());
            Vector3d t2 = q.transform(v, new Vector3d());
            if (!t1.equals(t2, 1E-10f))
                failure++;
        }
        if ((float)failure / N > 0.0001f) // <- allow for a failure rate of 0.01%
            throw new AssertionError();
    }

    @Test
    void testGetEulerAnglesZYX() {
        Random rnd = new Random(1L);
        int failure = 0;
        int N = 30000;
        for (int i = 0; i < N; i++) {
            double x = (rnd.nextFloat() * 2.0 - 1.0) * Math.PI;
            double y = (rnd.nextFloat() * 2.0 - 1.0) * Math.PI;
            double z = (rnd.nextFloat() * 2.0 - 1.0) * Math.PI;
            Quaterniond p = new Quaterniond().rotateZ(z).rotateY(y).rotateX(x);
            Vector3d a = p.getEulerAnglesZYX(new Vector3d());
            Quaterniond q = new Quaterniond().rotateZ(a.z).rotateY(a.y).rotateX(a.x);
            Vector3d v = new Vector3d(rnd.nextFloat()*2-1, rnd.nextFloat()*2-1, rnd.nextFloat()*2-1);
            Vector3d t1 = p.transform(v, new Vector3d());
            Vector3d t2 = q.transform(v, new Vector3d());
            if (!t1.equals(t2, 1E-10f))
                failure++;
        }
        if ((float)failure / N > 0.0001f) // <- allow for a failure rate of 0.01%
            throw new AssertionError();
    }

    @Test
    void testGetEulerAnglesZXY() {
        Random rnd = new Random(1L);
        int failure = 0;
        int N = 30000;
        for (int i = 0; i < N; i++) {
            double x = (rnd.nextFloat() * 2.0 - 1.0) * Math.PI;
            double y = (rnd.nextFloat() * 2.0 - 1.0) * Math.PI;
            double z = (rnd.nextFloat() * 2.0 - 1.0) * Math.PI;
            Quaterniond p = new Quaterniond().rotateZ(z).rotateX(x).rotateY(y);
            Vector3d a = p.getEulerAnglesZXY(new Vector3d());
            Quaterniond q = new Quaterniond().rotateZ(a.z).rotateX(a.x).rotateY(a.y);
            Vector3d v = new Vector3d(rnd.nextFloat()*2-1, rnd.nextFloat()*2-1, rnd.nextFloat()*2-1);
            Vector3d t1 = p.transform(v, new Vector3d());
            Vector3d t2 = q.transform(v, new Vector3d());
            if (!t1.equals(t2, 1E-10f))
                failure++;
        }
        if ((float)failure / N > 0.0001f) // <- allow for a failure rate of 0.01%
            throw new AssertionError();
    }

    @Test
    void testGetEulerAnglesYXZ() {
        Random rnd = new Random(1L);
        int failure = 0;
        int N = 30000;
        for (int i = 0; i < N; i++) {
            double x = (rnd.nextFloat() * 2.0 - 1.0) * Math.PI;
            double y = (rnd.nextFloat() * 2.0 - 1.0) * Math.PI;
            double z = (rnd.nextFloat() * 2.0 - 1.0) * Math.PI;
            Quaterniond p = new Quaterniond().rotateY(y).rotateX(x).rotateZ(z);
            Vector3d a = p.getEulerAnglesYXZ(new Vector3d());
            Quaterniond q = new Quaterniond().rotateY(a.y).rotateX(a.x).rotateZ(a.z);
            Vector3d v = new Vector3d(rnd.nextFloat()*2-1, rnd.nextFloat()*2-1, rnd.nextFloat()*2-1);
            Vector3d t1 = p.transform(v, new Vector3d());
            Vector3d t2 = q.transform(v, new Vector3d());
            if (!t1.equals(t2, 1E-10f))
                failure++;
        }
        if ((float)failure / N > 0.0001f) // <- allow for a failure rate of 0.01%
            throw new AssertionError();
    }

    @Test
    void testLookAlong() {
        assertVector3dEquals(new Vector3d(0, 0, 1), new Vector3d(-1, 1, 1).normalize().rotate(new Quaterniond().lookAlong(new Vector3d(-1, 1, 1), new Vector3d(0, 1, 0))), 1E-6);
        assertVector3dEquals(new Vector3d(0, 0, 1), new Vector3d(1, 1, 1).normalize().rotate(new Quaterniond().lookAlong(new Vector3d(1, 1, 1), new Vector3d(0, 1, 0))), 1E-6);
        assertVector3dEquals(new Vector3d(0, 0, 1), new Vector3d(1, -1, 1).normalize().rotate(new Quaterniond().lookAlong(new Vector3d(1, -1, 1), new Vector3d(0, 1, 0))), 1E-6);
        assertVector3dEquals(new Vector3d(0, 0, 1), new Vector3d(1, 1, 1).normalize().rotate(new Quaterniond().lookAlong(new Vector3d(1, 1, 1), new Vector3d(0, 1, 0))), 1E-6);
        assertVector3dEquals(new Vector3d(0, 0, 1), new Vector3d(1, 1, -1).normalize().rotate(new Quaterniond().lookAlong(new Vector3d(1, 1, -1), new Vector3d(0, 1, 0))), 1E-6);
        assertVector3dEquals(new Vector3d(0, 0, 1), new Vector3d(1, 1, 1).normalize().rotate(new Quaterniond().lookAlong(new Vector3d(1, 1, 1), new Vector3d(0, 1, 0))), 1E-6);
    }
}
