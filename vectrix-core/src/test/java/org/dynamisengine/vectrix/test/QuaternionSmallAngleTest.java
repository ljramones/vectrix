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

import org.dynamisengine.vectrix.affine.RigidTransformf;
import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Review findings H-7 (float {@code log}/{@code angularVelocity} collapse for small rotations, because
 * {@code acos(w)} loses all precision as w approaches 1), M-3 ({@code swingTwist} with an output aliasing
 * {@code this}) and M-2 ({@code compose} with {@code dest == parent}).
 */
class QuaternionSmallAngleTest {

    private static final float AXIS_X = 0.267261f, AXIS_Y = 0.534522f, AXIS_Z = 0.801784f; // (1,2,3)/|(1,2,3)|

    @Test
    void floatLogIsAccurateForSmallRotations() {
        for (double angle : new double[] {1e-1, 1e-2, 1e-3, 4e-4, 1e-4, 1e-5, 1e-6}) {
            Quaternionf q = new Quaternionf().rotationAxis((float) angle, AXIS_X, AXIS_Y, AXIS_Z);
            Quaternionf log = q.log(new Quaternionf());
            double expectedZ = 0.5 * angle * AXIS_Z;
            assertEquals(expectedZ, log.z, java.lang.Math.abs(expectedZ) * 1e-4, "log.z for angle " + angle);
            assertEquals(0.0f, log.w);
            Quaternionf back = log.exp(new Quaternionf());
            assertEquals(q.x, back.x, java.lang.Math.abs(q.x) * 1e-4 + 1e-12, "exp(log(q)).x for angle " + angle);
            assertEquals(q.z, back.z, java.lang.Math.abs(q.z) * 1e-4 + 1e-12, "exp(log(q)).z for angle " + angle);
            assertEquals(q.w, back.w, 1e-6f, "exp(log(q)).w for angle " + angle);
        }
    }

    @Test
    void doubleLogMatchesFloatAndIsExact() {
        double angle = 1e-7;
        Quaterniond q = new Quaterniond().rotationAxis(angle, AXIS_X, AXIS_Y, AXIS_Z);
        Quaterniond log = q.log(new Quaterniond());
        // log(q) = (angle / 2) * axis, with the axis exactly as stored in q: q.xyz / sin(angle / 2)
        double expectedZ = 0.5 * angle * (q.z / java.lang.Math.sin(0.5 * angle));
        assertEquals(expectedZ, log.z, java.lang.Math.abs(expectedZ) * 1e-12);
    }

    @Test
    void floatAngularVelocityResolvesSlowRotation() {
        float dt = 1.0f / 60.0f;
        // Starting from identity, dq.w = cos(omega * dt / 2) rounds to exactly 1.0f for slow rotations, which is
        // where the old acos/sqrt(1 - w^2) form returned zero.
        Quaternionf[] starts = {new Quaternionf(), new Quaternionf().rotationXYZ(0.3f, -0.2f, 0.9f)};
        for (Quaternionf start : starts)
        for (float omega : new float[] {2.0f, 0.2f, 0.02f, 0.002f}) {
            Quaternionf q0 = new Quaternionf(start);
            Quaternionf step = new Quaternionf().rotationAxis(omega * dt, AXIS_X, AXIS_Y, AXIS_Z);
            Quaternionf q1 = step.mul(q0, new Quaternionf());
            Vector3f w = q0.angularVelocity(q1, dt, new Vector3f());
            // Rounding q1 to float limits accuracy to about 1e-7 / (omega * dt) relative.
            float tol = omega * 1e-3f + 2e-5f;
            assertEquals(omega * AXIS_X, w.x, tol, "omega " + omega);
            assertEquals(omega * AXIS_Y, w.y, tol, "omega " + omega);
            assertEquals(omega * AXIS_Z, w.z, tol, "omega " + omega);
            Vector3d wd = new Quaterniond(q0.x, q0.y, q0.z, q0.w).angularVelocity(
                    new Quaterniond(q1.x, q1.y, q1.z, q1.w), dt, new Vector3d());
            assertEquals(wd.length(), w.length(), tol, "f/d parity omega " + omega);
        }
    }

    @Test
    void swingTwistMayWriteIntoThis() {
        Vector3f axis = new Vector3f(0, 1, 0);
        Quaternionf q = new Quaternionf().rotationXYZ(0.7f, 1.2f, -0.4f);
        Quaternionf swing = new Quaternionf(), twist = new Quaternionf();
        new Quaternionf(q).swingTwist(axis, swing, twist);

        Quaternionf inPlaceTwist = new Quaternionf(q);
        Quaternionf swing2 = new Quaternionf();
        inPlaceTwist.swingTwist(axis, swing2, inPlaceTwist);
        assertEquals(swing, swing2, "swing when twist == this");
        assertEquals(twist, inPlaceTwist, "twist when twist == this");

        Quaternionf inPlaceSwing = new Quaternionf(q);
        Quaternionf twist3 = new Quaternionf();
        inPlaceSwing.swingTwist(axis, inPlaceSwing, twist3);
        assertEquals(swing, inPlaceSwing, "swing when swing == this");
        assertEquals(twist, twist3, "twist when swing == this");

        Quaterniond qd = new Quaterniond(q.x, q.y, q.z, q.w);
        Quaterniond sd = new Quaterniond();
        qd.swingTwist(new Vector3d(0, 1, 0), sd, qd);
        assertEquals(swing.x, sd.x, 1e-6);
        assertEquals(swing.w, sd.w, 1e-6);

        Quaternionf shared = new Quaternionf();
        assertThrows(IllegalArgumentException.class, () -> q.swingTwist(axis, shared, shared));
    }

    @Test
    void composeIntoParentMatchesSeparateDestination() {
        Transformf parent = new Transformf(new Vector3f(10, 0, 0), new Quaternionf().rotationZ(0.5f), new Vector3f(2, 2, 2));
        Transformf local = new Transformf(new Vector3f(1, 0, 0), new Quaternionf().rotationX(0.2f), new Vector3f(1, 1, 1));
        Transformf separate = Transformf.compose(parent, local, new Transformf());
        Transformf inPlace = new Transformf(parent);
        Transformf.compose(inPlace, local, inPlace);
        assertEquals(separate.translation.x, inPlace.translation.x, 1e-5f); // was 2 instead of ~11.76
        assertEquals(separate.translation.y, inPlace.translation.y, 1e-5f);
        assertEquals(separate.rotation, inPlace.rotation);
        assertEquals(separate.scale, inPlace.scale);

        RigidTransformf rp = new RigidTransformf();
        rp.translation.set(10, 0, 0);
        rp.rotation.rotationZ(0.5f);
        RigidTransformf rl = new RigidTransformf();
        rl.translation.set(1, 0, 0);
        RigidTransformf rSeparate = RigidTransformf.compose(rp, rl, new RigidTransformf());
        RigidTransformf.compose(rp, rl, rp);
        assertEquals(rSeparate.translation.x, rp.translation.x, 1e-5f);
        assertEquals(rSeparate.translation.y, rp.translation.y, 1e-5f);
    }
}
