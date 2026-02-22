/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
 */
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.vectrix.core.MathMode;
import org.vectrix.core.Quaternionf;
import org.vectrix.core.Vector3f;
import org.vectrix.experimental.KernelConfig;

class QuaternionfSwingTwistTest {

    private static final float EPSILON_F = 1E-6f;

    private static Quaternionf unitQuat(float x, float y, float z, float w) {
        return new Quaternionf(x, y, z, w).normalize();
    }

    private static boolean approxEqualOrNegated(Quaternionf a, Quaternionf b, float eps) {
        boolean direct = java.lang.Math.abs(a.x - b.x) < eps && java.lang.Math.abs(a.y - b.y) < eps
                && java.lang.Math.abs(a.z - b.z) < eps && java.lang.Math.abs(a.w - b.w) < eps;
        boolean negated = java.lang.Math.abs(a.x + b.x) < eps && java.lang.Math.abs(a.y + b.y) < eps
                && java.lang.Math.abs(a.z + b.z) < eps && java.lang.Math.abs(a.w + b.w) < eps;
        return direct || negated;
    }

    @Test
    void swingTwistReconstructionArbitraryQuaternions() {
        Vector3f yAxis = new Vector3f(0, 1, 0);
        Quaternionf[] quats = {
                unitQuat(1, 0, 0, 1),
                unitQuat(0, 1, 0, 1),
                unitQuat(1, 1, 0, 1),
                unitQuat(0.1f, 0.5f, 0.3f, 0.8f),
                unitQuat(-0.5f, 0.5f, -0.5f, 0.5f)
        };
        for (Quaternionf q : quats) {
            Quaternionf swing = new Quaternionf();
            Quaternionf twist = new Quaternionf();
            q.swingTwist(yAxis, swing, twist);
            Quaternionf reconstructed = swing.mul(twist, new Quaternionf());
            assertTrue(approxEqualOrNegated(q, reconstructed, EPSILON_F));
        }
    }

    @Test
    void swingTwistTwistAxisParallelToInputAxis() {
        Vector3f twistAxis = new Vector3f(0, 1, 0);
        Quaternionf q = unitQuat(0.2f, 0.8f, 0.1f, 0.5f);
        Quaternionf swing = new Quaternionf();
        Quaternionf twist = new Quaternionf();
        q.swingTwist(twistAxis, swing, twist);

        float cx = twist.y * twistAxis.z - twist.z * twistAxis.y;
        float cy = twist.z * twistAxis.x - twist.x * twistAxis.z;
        float cz = twist.x * twistAxis.y - twist.y * twistAxis.x;
        assertEquals(0f, cx, EPSILON_F);
        assertEquals(0f, cy, EPSILON_F);
        assertEquals(0f, cz, EPSILON_F);
    }

    @Test
    void swingTwistSwingAxisPerpendicularToTwistAxis() {
        Vector3f twistAxis = new Vector3f(0, 1, 0);
        Quaternionf q = unitQuat(0.3f, 0.6f, 0.2f, 0.7f);

        Quaternionf swing = new Quaternionf();
        Quaternionf twist = new Quaternionf();
        q.swingTwist(twistAxis, swing, twist);

        float dot = swing.x * twistAxis.x + swing.y * twistAxis.y + swing.z * twistAxis.z;
        assertEquals(0f, dot, EPSILON_F);
    }

    @Test
    void swingTwistIdentityInputYieldsIdentityOutputs() {
        Quaternionf identity = new Quaternionf(0, 0, 0, 1);
        Quaternionf swing = new Quaternionf();
        Quaternionf twist = new Quaternionf();
        identity.swingTwist(new Vector3f(0, 1, 0), swing, twist);

        Quaternionf id = new Quaternionf(0, 0, 0, 1);
        assertTrue(approxEqualOrNegated(id, swing, EPSILON_F));
        assertTrue(approxEqualOrNegated(id, twist, EPSILON_F));
    }

    @Test
    void swingTwistPureTwistYieldsIdentitySwing() {
        Quaternionf q = new Quaternionf().rotationY((float) (java.lang.Math.PI / 2.0));
        Quaternionf swing = new Quaternionf();
        Quaternionf twist = new Quaternionf();
        q.swingTwist(new Vector3f(0, 1, 0), swing, twist);

        Quaternionf id = new Quaternionf(0, 0, 0, 1);
        assertTrue(approxEqualOrNegated(id, swing, EPSILON_F));
    }

    @Test
    void swingTwistPureSwingYieldsIdentityTwist() {
        Quaternionf q = new Quaternionf().rotationX((float) (java.lang.Math.PI / 2.0));
        Quaternionf swing = new Quaternionf();
        Quaternionf twist = new Quaternionf();
        q.swingTwist(new Vector3f(0, 1, 0), swing, twist);

        Quaternionf id = new Quaternionf(0, 0, 0, 1);
        assertTrue(approxEqualOrNegated(id, twist, EPSILON_F));
    }

    @Test
    void swingTwistStrictThrowsOnNonUnitQuaternion() {
        MathMode prev = KernelConfig.mathMode();
        try {
            KernelConfig.setMathMode(MathMode.STRICT);
            Quaternionf nonUnit = new Quaternionf(1, 1, 1, 1);
            assertThrows(IllegalArgumentException.class,
                    () -> nonUnit.swingTwist(new Vector3f(0, 1, 0), new Quaternionf(), new Quaternionf()));
        } finally {
            KernelConfig.setMathMode(prev);
        }
    }

    @Test
    void swingTwistStrictThrowsOnNonUnitAxis() {
        MathMode prev = KernelConfig.mathMode();
        try {
            KernelConfig.setMathMode(MathMode.STRICT);
            Quaternionf q = new Quaternionf(0, 0, 0, 1);
            Vector3f nonUnitAxis = new Vector3f(0, 2, 0);
            assertThrows(IllegalArgumentException.class,
                    () -> q.swingTwist(nonUnitAxis, new Quaternionf(), new Quaternionf()));
        } finally {
            KernelConfig.setMathMode(prev);
        }
    }

    @Test
    void swingTwistNearZeroVectorPartNoNanOrInf() {
        Quaternionf nearIdentity = new Quaternionf(1E-8f, 1E-8f, 1E-8f, 1f).normalize();
        Quaternionf swing = new Quaternionf();
        Quaternionf twist = new Quaternionf();
        nearIdentity.swingTwist(new Vector3f(0, 1, 0), swing, twist);

        assertNoNanOrInf(swing, "swing");
        assertNoNanOrInf(twist, "twist");
    }

    @Test
    void swingTwistAxisAlignedWithRotationAxisNoNanOrInf() {
        Quaternionf q = new Quaternionf().rotationY((float) (java.lang.Math.PI / 3.0));
        Quaternionf swing = new Quaternionf();
        Quaternionf twist = new Quaternionf();
        q.swingTwist(new Vector3f(0, 1, 0), swing, twist);

        assertNoNanOrInf(swing, "swing");
        assertNoNanOrInf(twist, "twist");
    }

    @Test
    void swingTwistNearlyOppositeQuaternionNoNanOrInf() {
        Quaternionf nearOpposite = new Quaternionf(1E-5f, 0f, 0f, -1f).normalize();
        Quaternionf swing = new Quaternionf();
        Quaternionf twist = new Quaternionf();
        nearOpposite.swingTwist(new Vector3f(0, 1, 0), swing, twist);

        assertNoNanOrInf(swing, "swing");
        assertNoNanOrInf(twist, "twist");
    }

    private static void assertNoNanOrInf(Quaternionf q, String label) {
        assertFalse(Float.isNaN(q.x) || Float.isNaN(q.y) || Float.isNaN(q.z) || Float.isNaN(q.w), "NaN in " + label);
        assertFalse(Float.isInfinite(q.x) || Float.isInfinite(q.y) || Float.isInfinite(q.z) || Float.isInfinite(q.w), "Inf in " + label);
    }
}
