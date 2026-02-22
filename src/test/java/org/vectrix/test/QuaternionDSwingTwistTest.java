/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
 */
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.vectrix.core.MathMode;
import org.vectrix.core.Quaterniond;
import org.vectrix.core.Vector3d;
import org.vectrix.experimental.KernelConfig;

class QuaternionDSwingTwistTest {

    private static final double EPSILON_D = 1E-12;

    private static Quaterniond unitQuat(double x, double y, double z, double w) {
        return new Quaterniond(x, y, z, w).normalize();
    }

    private static boolean approxEqualOrNegated(Quaterniond a, Quaterniond b, double eps) {
        boolean direct = java.lang.Math.abs(a.x - b.x) < eps && java.lang.Math.abs(a.y - b.y) < eps
                && java.lang.Math.abs(a.z - b.z) < eps && java.lang.Math.abs(a.w - b.w) < eps;
        boolean negated = java.lang.Math.abs(a.x + b.x) < eps && java.lang.Math.abs(a.y + b.y) < eps
                && java.lang.Math.abs(a.z + b.z) < eps && java.lang.Math.abs(a.w + b.w) < eps;
        return direct || negated;
    }

    @Test
    void swingTwistReconstructionArbitraryQuaternions() {
        Vector3d yAxis = new Vector3d(0, 1, 0);
        Quaterniond[] quats = {
                unitQuat(1, 0, 0, 1),
                unitQuat(0, 1, 0, 1),
                unitQuat(1, 1, 0, 1),
                unitQuat(0.1, 0.5, 0.3, 0.8),
                unitQuat(-0.5, 0.5, -0.5, 0.5)
        };
        for (Quaterniond q : quats) {
            Quaterniond swing = new Quaterniond();
            Quaterniond twist = new Quaterniond();
            q.swingTwist(yAxis, swing, twist);
            Quaterniond reconstructed = swing.mul(twist, new Quaterniond());
            assertTrue(approxEqualOrNegated(q, reconstructed, EPSILON_D));
        }
    }

    @Test
    void swingTwistTwistAxisParallelToInputAxis() {
        Vector3d twistAxis = new Vector3d(0, 1, 0);
        Quaterniond q = unitQuat(0.2, 0.8, 0.1, 0.5);
        Quaterniond swing = new Quaterniond();
        Quaterniond twist = new Quaterniond();
        q.swingTwist(twistAxis, swing, twist);

        double cx = twist.y * twistAxis.z - twist.z * twistAxis.y;
        double cy = twist.z * twistAxis.x - twist.x * twistAxis.z;
        double cz = twist.x * twistAxis.y - twist.y * twistAxis.x;
        assertEquals(0.0, cx, EPSILON_D);
        assertEquals(0.0, cy, EPSILON_D);
        assertEquals(0.0, cz, EPSILON_D);
    }

    @Test
    void swingTwistSwingAxisPerpendicularToTwistAxis() {
        Vector3d twistAxis = new Vector3d(0, 1, 0);
        Quaterniond q = unitQuat(0.3, 0.6, 0.2, 0.7);

        Quaterniond swing = new Quaterniond();
        Quaterniond twist = new Quaterniond();
        q.swingTwist(twistAxis, swing, twist);

        double dot = swing.x * twistAxis.x + swing.y * twistAxis.y + swing.z * twistAxis.z;
        assertEquals(0.0, dot, EPSILON_D);
    }

    @Test
    void swingTwistIdentityInputYieldsIdentityOutputs() {
        Quaterniond identity = new Quaterniond(0, 0, 0, 1);
        Quaterniond swing = new Quaterniond();
        Quaterniond twist = new Quaterniond();
        identity.swingTwist(new Vector3d(0, 1, 0), swing, twist);

        Quaterniond id = new Quaterniond(0, 0, 0, 1);
        assertTrue(approxEqualOrNegated(id, swing, EPSILON_D));
        assertTrue(approxEqualOrNegated(id, twist, EPSILON_D));
    }

    @Test
    void swingTwistPureTwistYieldsIdentitySwing() {
        Quaterniond q = new Quaterniond().rotationY(java.lang.Math.PI / 2.0);
        Quaterniond swing = new Quaterniond();
        Quaterniond twist = new Quaterniond();
        q.swingTwist(new Vector3d(0, 1, 0), swing, twist);

        Quaterniond id = new Quaterniond(0, 0, 0, 1);
        assertTrue(approxEqualOrNegated(id, swing, EPSILON_D));
    }

    @Test
    void swingTwistPureSwingYieldsIdentityTwist() {
        Quaterniond q = new Quaterniond().rotationX(java.lang.Math.PI / 2.0);
        Quaterniond swing = new Quaterniond();
        Quaterniond twist = new Quaterniond();
        q.swingTwist(new Vector3d(0, 1, 0), swing, twist);

        Quaterniond id = new Quaterniond(0, 0, 0, 1);
        assertTrue(approxEqualOrNegated(id, twist, EPSILON_D));
    }

    @Test
    void swingTwistStrictThrowsOnNonUnitQuaternion() {
        MathMode prev = KernelConfig.mathMode();
        try {
            KernelConfig.setMathMode(MathMode.STRICT);
            Quaterniond nonUnit = new Quaterniond(1, 1, 1, 1);
            assertThrows(IllegalArgumentException.class,
                    () -> nonUnit.swingTwist(new Vector3d(0, 1, 0), new Quaterniond(), new Quaterniond()));
        } finally {
            KernelConfig.setMathMode(prev);
        }
    }

    @Test
    void swingTwistStrictThrowsOnNonUnitAxis() {
        MathMode prev = KernelConfig.mathMode();
        try {
            KernelConfig.setMathMode(MathMode.STRICT);
            Quaterniond q = new Quaterniond(0, 0, 0, 1);
            Vector3d nonUnitAxis = new Vector3d(0, 2, 0);
            assertThrows(IllegalArgumentException.class,
                    () -> q.swingTwist(nonUnitAxis, new Quaterniond(), new Quaterniond()));
        } finally {
            KernelConfig.setMathMode(prev);
        }
    }

    @Test
    void swingTwistNearZeroVectorPartNoNanOrInf() {
        Quaterniond nearIdentity = new Quaterniond(1E-12, 1E-12, 1E-12, 1).normalize();
        Quaterniond swing = new Quaterniond();
        Quaterniond twist = new Quaterniond();
        nearIdentity.swingTwist(new Vector3d(0, 1, 0), swing, twist);

        assertNoNanOrInf(swing, "swing");
        assertNoNanOrInf(twist, "twist");
    }

    @Test
    void swingTwistAxisAlignedWithRotationAxisNoNanOrInf() {
        Quaterniond q = new Quaterniond().rotationY(java.lang.Math.PI / 3.0);
        Quaterniond swing = new Quaterniond();
        Quaterniond twist = new Quaterniond();
        q.swingTwist(new Vector3d(0, 1, 0), swing, twist);

        assertNoNanOrInf(swing, "swing");
        assertNoNanOrInf(twist, "twist");
    }

    @Test
    void swingTwistNearlyOppositeQuaternionNoNanOrInf() {
        Quaterniond nearOpposite = new Quaterniond(1E-9, 0, 0, -1).normalize();
        Quaterniond swing = new Quaterniond();
        Quaterniond twist = new Quaterniond();
        nearOpposite.swingTwist(new Vector3d(0, 1, 0), swing, twist);

        assertNoNanOrInf(swing, "swing");
        assertNoNanOrInf(twist, "twist");
    }

    private static void assertNoNanOrInf(Quaterniond q, String label) {
        assertFalse(Double.isNaN(q.x) || Double.isNaN(q.y) || Double.isNaN(q.z) || Double.isNaN(q.w), "NaN in " + label);
        assertFalse(Double.isInfinite(q.x) || Double.isInfinite(q.y) || Double.isInfinite(q.z) || Double.isInfinite(q.w), "Inf in " + label);
    }
}
