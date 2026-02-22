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

class QuaterniondAngularVelocityHemisphereTest {
    private static final double EPS = 1E-12;

    private static Quaterniond unitQuat(double x, double y, double z, double w) {
        return new Quaterniond(x, y, z, w).normalize();
    }

    private static boolean approxEqualOrNegated(Quaterniond a, Quaterniond b, double eps) {
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
        Quaterniond q = unitQuat(0.1, -0.3, 0.2, 0.9);
        Vector3d omega = q.angularVelocity(q, 0.25, new Vector3d());
        assertEquals(0.0, omega.x, EPS);
        assertEquals(0.0, omega.y, EPS);
        assertEquals(0.0, omega.z, EPS);
    }

    @Test
    void angularVelocityKnownRotationY() {
        Quaterniond q0 = new Quaterniond();
        Quaterniond q1 = new Quaterniond().rotationY(java.lang.Math.PI * 0.5);
        Vector3d omega = q0.angularVelocity(q1, 1.0, new Vector3d());
        assertEquals(0.0, omega.x, EPS);
        assertEquals(java.lang.Math.PI * 0.5, omega.y, 1E-10);
        assertEquals(0.0, omega.z, EPS);
    }

    @Test
    void angularVelocityIntegrateRoundTripRecoversDelta() {
        Quaterniond qPrev = unitQuat(0.3, 0.1, -0.2, 0.9);
        Quaterniond qNext = unitQuat(-0.2, 0.4, 0.2, 0.85);
        double dt = 0.125;

        Vector3d omega = qPrev.angularVelocity(qNext, dt, new Vector3d());
        Quaterniond delta = Quaterniond.integrateAngularVelocity(omega, dt, new Quaterniond());
        Quaterniond expectedDelta = qNext.mul(new Quaterniond(qPrev).conjugate(new Quaterniond()), new Quaterniond()).normalize();

        assertTrue(approxEqualOrNegated(expectedDelta, delta, 1E-10));
    }

    @Test
    void angularVelocityNearIdentityNoNanOrInf() {
        Quaterniond q0 = new Quaterniond();
        Quaterniond q1 = new Quaterniond(1E-12, -2E-12, 1E-12, 1.0).normalize();
        Vector3d omega = q0.angularVelocity(q1, 0.5, new Vector3d());
        assertFalse(Double.isNaN(omega.x) || Double.isNaN(omega.y) || Double.isNaN(omega.z));
        assertFalse(Double.isInfinite(omega.x) || Double.isInfinite(omega.y) || Double.isInfinite(omega.z));
    }

    @Test
    void hemisphereAlreadyConsistentUnchanged() {
        Quaterniond[] seq = {
                new Quaterniond().rotationX(0.1),
                new Quaterniond().rotationX(0.2),
                new Quaterniond().rotationX(0.3)
        };
        Quaterniond[] before = { new Quaterniond(seq[0]), new Quaterniond(seq[1]), new Quaterniond(seq[2]) };
        Quaterniond.ensureConsistentHemisphere(seq);
        assertTrue(approxEqualOrNegated(before[0], seq[0], EPS));
        assertTrue(approxEqualOrNegated(before[1], seq[1], EPS));
        assertTrue(approxEqualOrNegated(before[2], seq[2], EPS));
    }

    @Test
    void hemisphereSingleFlipCorrected() {
        Quaterniond a = new Quaterniond().rotationY(0.2);
        Quaterniond b = new Quaterniond().rotationY(0.25);
        b.mul(-1.0);
        Quaterniond[] seq = { new Quaterniond(a), new Quaterniond(b) };
        Quaterniond.ensureConsistentHemisphere(seq);
        assertTrue(seq[0].dot(seq[1]) > 0.0);
    }

    @Test
    void hemisphereAlternatingFlipsCorrected() {
        Quaterniond[] seq = {
                new Quaterniond().rotationZ(0.1),
                new Quaterniond().rotationZ(0.2),
                new Quaterniond().rotationZ(0.3),
                new Quaterniond().rotationZ(0.4)
        };
        seq[1].mul(-1.0);
        seq[3].mul(-1.0);
        Quaterniond.ensureConsistentHemisphere(seq);
        for (int i = 1; i < seq.length; i++) {
            assertTrue(seq[i - 1].dot(seq[i]) >= 0.0);
        }
    }

    @Test
    void hemisphereSingleElementNoOp() {
        Quaterniond[] seq = { new Quaterniond().rotationX(0.3) };
        Quaterniond before = new Quaterniond(seq[0]);
        Quaterniond.ensureConsistentHemisphere(seq);
        assertTrue(approxEqualOrNegated(before, seq[0], EPS));
    }

    @Test
    void hemisphereEmptyNoOp() {
        Quaterniond[] seq = new Quaterniond[0];
        Quaterniond.ensureConsistentHemisphere(seq);
        assertEquals(0, seq.length);
    }

    @Test
    void hemisphereNormalizationSquadMatchesManualCorrection() {
        Quaterniond[] seq = {
                new Quaterniond().rotationY(0.1),
                new Quaterniond().rotationY(0.4),
                new Quaterniond().rotationY(0.7),
                new Quaterniond().rotationY(1.0)
        };
        seq[2].mul(-1.0);

        Quaterniond[] manual = { new Quaterniond(seq[0]), new Quaterniond(seq[1]), new Quaterniond(seq[2]), new Quaterniond(seq[3]) };
        for (int i = 1; i < manual.length; i++) {
            if (manual[i - 1].dot(manual[i]) < 0.0) {
                manual[i].mul(-1.0);
            }
        }
        Quaterniond.ensureConsistentHemisphere(seq);

        Quaterniond s0A = seq[1].squadControlPoint(seq[0], seq[2], new Quaterniond());
        Quaterniond s1A = seq[2].squadControlPoint(seq[1], seq[3], new Quaterniond());
        Quaterniond outA = seq[1].squad(seq[2], s0A, s1A, 0.35, new Quaterniond());

        Quaterniond s0B = manual[1].squadControlPoint(manual[0], manual[2], new Quaterniond());
        Quaterniond s1B = manual[2].squadControlPoint(manual[1], manual[3], new Quaterniond());
        Quaterniond outB = manual[1].squad(manual[2], s0B, s1B, 0.35, new Quaterniond());

        assertTrue(approxEqualOrNegated(outA, outB, 1E-10));
    }

    @Test
    void hemisphereStrictThrowsOnNonUnitSequenceElement() {
        MathMode prev = KernelConfig.mathMode();
        try {
            KernelConfig.setMathMode(MathMode.STRICT);
            Quaterniond[] seq = {
                    new Quaterniond(),
                    new Quaterniond(1.0, 1.0, 1.0, 1.0)
            };
            assertThrows(IllegalArgumentException.class, () -> Quaterniond.ensureConsistentHemisphere(seq));
        } finally {
            KernelConfig.setMathMode(prev);
        }
    }
}
