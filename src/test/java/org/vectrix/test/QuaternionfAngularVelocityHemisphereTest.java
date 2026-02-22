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

class QuaternionfAngularVelocityHemisphereTest {
    private static final float EPS = 1E-6f;

    private static Quaternionf unitQuat(float x, float y, float z, float w) {
        return new Quaternionf(x, y, z, w).normalize();
    }

    private static boolean approxEqualOrNegated(Quaternionf a, Quaternionf b, float eps) {
        boolean direct = java.lang.Math.abs(a.x - b.x) < eps
                && java.lang.Math.abs(a.y - b.y) < eps
                && java.lang.Math.abs(a.z - b.z) < eps
                && java.lang.Math.abs(a.w - b.w) < eps;
        boolean negated = java.lang.Math.abs(a.x + b.x) < eps
                && java.lang.Math.abs(a.y + b.y) < eps
                && java.lang.Math.abs(a.z + b.z) < eps
                && java.lang.Math.abs(a.w + b.w) < eps;
        return direct || negated;
    }

    @Test
    void angularVelocityIdenticalQuaternionsIsZero() {
        Quaternionf q = unitQuat(0.1f, -0.3f, 0.2f, 0.9f);
        Vector3f omega = q.angularVelocity(q, 0.25f, new Vector3f());
        assertEquals(0.0f, omega.x, EPS);
        assertEquals(0.0f, omega.y, EPS);
        assertEquals(0.0f, omega.z, EPS);
    }

    @Test
    void angularVelocityKnownRotationY() {
        Quaternionf q0 = new Quaternionf();
        Quaternionf q1 = new Quaternionf().rotationY((float) (java.lang.Math.PI * 0.5));
        Vector3f omega = q0.angularVelocity(q1, 1.0f, new Vector3f());
        assertEquals(0.0f, omega.x, EPS);
        assertEquals((float) (java.lang.Math.PI * 0.5), omega.y, 1E-5f);
        assertEquals(0.0f, omega.z, EPS);
    }

    @Test
    void angularVelocityIntegrateRoundTripRecoversDelta() {
        Quaternionf qPrev = unitQuat(0.3f, 0.1f, -0.2f, 0.9f);
        Quaternionf qNext = unitQuat(-0.2f, 0.4f, 0.2f, 0.85f);
        float dt = 0.125f;

        Vector3f omega = qPrev.angularVelocity(qNext, dt, new Vector3f());
        Quaternionf delta = Quaternionf.integrateAngularVelocity(omega, dt, new Quaternionf());
        Quaternionf expectedDelta = qNext.mul(new Quaternionf(qPrev).conjugate(new Quaternionf()), new Quaternionf()).normalize();

        assertTrue(approxEqualOrNegated(expectedDelta, delta, 1E-5f));
    }

    @Test
    void angularVelocityNearIdentityNoNanOrInf() {
        Quaternionf q0 = new Quaternionf();
        Quaternionf q1 = new Quaternionf(1E-8f, -2E-8f, 1E-8f, 1.0f).normalize();
        Vector3f omega = q0.angularVelocity(q1, 0.5f, new Vector3f());
        assertFalse(Float.isNaN(omega.x) || Float.isNaN(omega.y) || Float.isNaN(omega.z));
        assertFalse(Float.isInfinite(omega.x) || Float.isInfinite(omega.y) || Float.isInfinite(omega.z));
    }

    @Test
    void hemisphereAlreadyConsistentUnchanged() {
        Quaternionf[] seq = {
                new Quaternionf().rotationX(0.1f),
                new Quaternionf().rotationX(0.2f),
                new Quaternionf().rotationX(0.3f)
        };
        Quaternionf[] before = { new Quaternionf(seq[0]), new Quaternionf(seq[1]), new Quaternionf(seq[2]) };
        Quaternionf.ensureConsistentHemisphere(seq);
        assertTrue(approxEqualOrNegated(before[0], seq[0], EPS));
        assertTrue(approxEqualOrNegated(before[1], seq[1], EPS));
        assertTrue(approxEqualOrNegated(before[2], seq[2], EPS));
    }

    @Test
    void hemisphereSingleFlipCorrected() {
        Quaternionf a = new Quaternionf().rotationY(0.2f);
        Quaternionf b = new Quaternionf().rotationY(0.25f);
        b.mul(-1.0f);
        Quaternionf[] seq = { new Quaternionf(a), new Quaternionf(b) };
        Quaternionf.ensureConsistentHemisphere(seq);
        assertTrue(seq[0].dot(seq[1]) > 0.0f);
    }

    @Test
    void hemisphereAlternatingFlipsCorrected() {
        Quaternionf[] seq = {
                new Quaternionf().rotationZ(0.1f),
                new Quaternionf().rotationZ(0.2f),
                new Quaternionf().rotationZ(0.3f),
                new Quaternionf().rotationZ(0.4f)
        };
        seq[1].mul(-1.0f);
        seq[3].mul(-1.0f);
        Quaternionf.ensureConsistentHemisphere(seq);
        for (int i = 1; i < seq.length; i++) {
            assertTrue(seq[i - 1].dot(seq[i]) >= 0.0f);
        }
    }

    @Test
    void hemisphereSingleElementNoOp() {
        Quaternionf[] seq = { new Quaternionf().rotationX(0.3f) };
        Quaternionf before = new Quaternionf(seq[0]);
        Quaternionf.ensureConsistentHemisphere(seq);
        assertTrue(approxEqualOrNegated(before, seq[0], EPS));
    }

    @Test
    void hemisphereEmptyNoOp() {
        Quaternionf[] seq = new Quaternionf[0];
        Quaternionf.ensureConsistentHemisphere(seq);
        assertEquals(0, seq.length);
    }

    @Test
    void hemisphereNormalizationSquadMatchesManualCorrection() {
        Quaternionf[] seq = {
                new Quaternionf().rotationY(0.1f),
                new Quaternionf().rotationY(0.4f),
                new Quaternionf().rotationY(0.7f),
                new Quaternionf().rotationY(1.0f)
        };
        seq[2].mul(-1.0f);

        Quaternionf[] manual = { new Quaternionf(seq[0]), new Quaternionf(seq[1]), new Quaternionf(seq[2]), new Quaternionf(seq[3]) };
        for (int i = 1; i < manual.length; i++) {
            if (manual[i - 1].dot(manual[i]) < 0.0f) {
                manual[i].mul(-1.0f);
            }
        }
        Quaternionf.ensureConsistentHemisphere(seq);

        Quaternionf s0A = seq[1].squadControlPoint(seq[0], seq[2], new Quaternionf());
        Quaternionf s1A = seq[2].squadControlPoint(seq[1], seq[3], new Quaternionf());
        Quaternionf outA = seq[1].squad(seq[2], s0A, s1A, 0.35f, new Quaternionf());

        Quaternionf s0B = manual[1].squadControlPoint(manual[0], manual[2], new Quaternionf());
        Quaternionf s1B = manual[2].squadControlPoint(manual[1], manual[3], new Quaternionf());
        Quaternionf outB = manual[1].squad(manual[2], s0B, s1B, 0.35f, new Quaternionf());

        assertTrue(approxEqualOrNegated(outA, outB, 1E-6f));
    }

    @Test
    void hemisphereStrictThrowsOnNonUnitSequenceElement() {
        MathMode prev = KernelConfig.mathMode();
        try {
            KernelConfig.setMathMode(MathMode.STRICT);
            Quaternionf[] seq = {
                    new Quaternionf(),
                    new Quaternionf(1.0f, 1.0f, 1.0f, 1.0f)
            };
            assertThrows(IllegalArgumentException.class, () -> Quaternionf.ensureConsistentHemisphere(seq));
        } finally {
            KernelConfig.setMathMode(prev);
        }
    }
}
