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
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.dynamisengine.vectrix.core.Matrix3x2d;
import org.dynamisengine.vectrix.core.Matrix3x2f;
import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Matrix4x3f;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Bugs inherited from JOML that {@link FloatDoubleParityTest} exposed (review finding M-17, the coverage work):
 * aliasing, ignored {@code dest} parameters, an incorrect quaternion, stale matrix elements and an infinite loop.
 */
class UpstreamJomlBugsTest {

    @Test
    void vector2fMulPositionInPlaceUsesTheOldX() {
        Matrix3x2f m = new Matrix3x2f(0, 1, -1, 0, 5, 7); // 90 degree rotation, then translate (5, 7)
        assertEquals(new Vector2f(5 - 2, 7 + 1), new Vector2f(1, 2).mulPosition(m)); // y used the new x
        assertEquals(new Vector2f(-2, 1), new Vector2f(1, 2).mulDirection(m));
    }

    @Test
    void quaterniondRotationXIsARotationAboutX() {
        Quaterniond q = new Quaterniond().rotationX(0.8); // was (sin, 0, cos, 0)
        Vector3d v = q.transform(new Vector3d(0, 1, 0));
        assertEquals(0.0, v.x, 1e-15);
        assertEquals(java.lang.Math.cos(0.8), v.y, 1e-15);
        assertEquals(java.lang.Math.sin(0.8), v.z, 1e-15);
        assertEquals(new Quaterniond().rotationAxis(0.8, 1, 0, 0), q);
    }

    @Test
    void settingFromADoubleQuaternionResetsTheRestOfTheMatrix() {
        Quaterniond q = new Quaterniond().rotationXYZ(0.3, -0.2, 0.9);
        Matrix4f m = new Matrix4f().set(new float[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}).set(q);
        Quaternionf qf = new Quaternionf((float) q.x, (float) q.y, (float) q.z, (float) q.w);
        assertEquals(0.0f, m.m23(), "m23 kept its old value");
        assertTrue(m.equals(new Matrix4f().rotation(qf), 1e-6f));
        Matrix4x3f n = new Matrix4x3f().translation(5, 6, 7).set(q);
        assertTrue(n.equals(new Matrix4x3f().rotation(qf), 1e-6f), "translation kept its old value");
    }

    @Test
    void destParametersAreHonoured() {
        Quaterniond qd = new Quaterniond().rotationZ(0.5);
        Matrix4d md = new Matrix4d().translation(1, 2, 3).rotateX(0.2);
        Matrix4d copy = new Matrix4d(md);
        Matrix4d dest = new Matrix4d();
        md.rotateAround(qd, 1, 0, 0, dest);
        assertEquals(copy, md, "rotateAround modified this instead of dest");
        assertTrue(dest.equals(new Matrix4d(copy).rotateAround(qd, 1, 0, 0), 1e-12));

        Matrix4f identity = new Matrix4f();
        Matrix4f fdest = new Matrix4f().scale(3);
        identity.rotateAround(new Quaternionf().rotationZ(0.5f), 1, 0, 0, fdest);
        assertTrue(identity.equals(new Matrix4f(), 0f), "identity path wrote into this");
        assertTrue(fdest.equals(new Matrix4f().rotationAround(new Quaternionf().rotationZ(0.5f), 1, 0, 0), 1e-6f));

        Matrix3x2f s = new Matrix3x2f().translation(1, 1);
        Matrix3x2f sdest = new Matrix3x2f();
        s.scaleAround(2, 3, 4, sdest);
        assertTrue(s.equals(new Matrix3x2f().translation(1, 1), 0f), "scaleAround modified this");
        assertTrue(sdest.equals(new Matrix3x2f().translation(1, 1).scaleAround(2, 2, 3, 4), 1e-6f));
        Matrix3x2d sd = new Matrix3x2d();
        sd.scaleAround(2, 3, 4, new Matrix3x2d());
        assertEquals(new Matrix3x2d(), sd);

        Matrix4f look = new Matrix4f().rotateXYZ(0.3f, 0.5f, 0.1f);
        Matrix4f lookCopy = new Matrix4f(look);
        Matrix4f lookDest = new Matrix4f();
        look.withLookAtUp(new Vector3f(0, 1, 0), lookDest);
        assertEquals(lookCopy, look, "withLookAtUp modified this");
        assertTrue(lookDest.equals(new Matrix4f(lookCopy).withLookAtUp(0, 1, 0), 1e-6f));
    }

    @Test
    void nlerpIterativeTerminatesForAnyThreshold() {
        Quaternionf a = new Quaternionf(), b = new Quaternionf().rotationY(2.0f);
        // |dot| never exceeds 1, so a threshold >= 1 looped forever
        Quaternionf r = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> a.nlerpIterative(b, 0.5f, 1.5f, new Quaternionf()));
        assertEquals(1.0f, r.lengthSquared(), 1e-5f);
        Quaternionf s = a.nlerpIterative(b, 0.5f, 0.9999f, new Quaternionf());
        assertTrue(s.equals(a.slerp(b, 0.5f, new Quaternionf()), 1e-3f), "valid thresholds are unchanged");
        Quaterniond ad = new Quaterniond(), bd = new Quaterniond().rotationY(2.0);
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> ad.nlerpIterative(bd, 0.5, 2.0, new Quaterniond()));
    }
}
