/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.vectrix.test.TestUtil.assertMatrix4x3fEquals;
import static org.vectrix.test.TestUtil.assertVector3fEquals;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.vectrix.affine.Affine4f;
import org.vectrix.affine.RigidTransformf;
import org.vectrix.affine.Transformf;
import org.vectrix.core.Matrix4f;
import org.vectrix.core.Matrix4x3f;
import org.vectrix.core.Quaternionf;
import org.vectrix.core.Vector3f;

class Affine4fTest {
    @Test
    void mulMatchesMatrix4x3fMul() {
        SplittableRandom rnd = new SplittableRandom(11);
        for (int i = 0; i < 64; i++) {
            Transformf a = randomTransform(rnd);
            Transformf b = randomTransform(rnd);
            Affine4f am = a.toAffine4fFast(new Affine4f());
            Affine4f bm = b.toAffine4fFast(new Affine4f());
            Matrix4x3f expected = a.toAffineMat4Fast(new Matrix4x3f()).mul(b.toAffineMat4Fast(new Matrix4x3f()), new Matrix4x3f());
            Matrix4x3f actual = am.mul(bm, new Affine4f()).toMatrix4x3f(new Matrix4x3f());
            assertMatrix4x3fEquals(expected, actual, 1E-5f);
        }
    }

    @Test
    void invertRigidMatchesRigidInverse() {
        RigidTransformf r = new RigidTransformf(
                new Vector3f(1.1f, -2.3f, 0.7f),
                new Quaternionf().rotationXYZ(0.4f, -0.6f, 0.2f));
        Affine4f m = new Affine4f().translationRotateScale(r.translation, r.rotation, new Vector3f(1.0f, 1.0f, 1.0f));
        Affine4f inv = m.invertRigid(new Affine4f());
        Affine4f id = inv.mul(m, new Affine4f());
        assertEquals(1.0f, id.m00, 1E-5f);
        assertEquals(1.0f, id.m11, 1E-5f);
        assertEquals(1.0f, id.m22, 1E-5f);
        assertEquals(0.0f, id.m30, 1E-5f);
        assertEquals(0.0f, id.m31, 1E-5f);
        assertEquals(0.0f, id.m32, 1E-5f);
    }

    @Test
    void transformPointMatchesMatrix4x3f() {
        Transformf t = new Transformf(
                new Vector3f(2.0f, -1.0f, 3.0f),
                new Quaternionf().rotationXYZ(0.3f, 0.1f, -0.2f),
                new Vector3f(1.5f, 0.7f, 2.1f));
        Affine4f a = t.toAffine4fFast(new Affine4f());
        Matrix4x3f m = t.toAffineMat4Fast(new Matrix4x3f());
        Vector3f p1 = a.transformPoint(0.2f, -0.3f, 0.5f, new Vector3f());
        Vector3f p2 = m.transformPosition(new Vector3f(0.2f, -0.3f, 0.5f));
        assertVector3fEquals(p2, p1, 1E-5f);
    }

    @Test
    void transformRoundTripViaAffine() {
        Transformf t = new Transformf(
                new Vector3f(-3.0f, 2.0f, 1.0f),
                new Quaternionf().rotationXYZ(-0.2f, 0.5f, 1.1f),
                new Vector3f(0.8f, 1.2f, 2.4f));
        Affine4f a = t.toAffine4fFast(new Affine4f());
        Transformf recovered = new Transformf().set(a);
        Matrix4x3f mt = t.toAffineMat4Fast(new Matrix4x3f());
        Matrix4x3f mr = recovered.toAffineMat4Fast(new Matrix4x3f());
        assertMatrix4x3fEquals(mt, mr, 1E-4f);
    }

    @Test
    void matrix4fConversionHasAffineTail() {
        Transformf t = randomTransform(new SplittableRandom(7));
        Affine4f a = t.toAffine4fFast(new Affine4f());
        Matrix4f m = a.toMatrix4f(new Matrix4f());
        assertEquals(a.m00, m.m00(), 0.0f);
        assertEquals(a.m01, m.m01(), 0.0f);
        assertEquals(a.m02, m.m02(), 0.0f);
        assertEquals(a.m30, m.m30(), 0.0f);
        assertEquals(a.m31, m.m31(), 0.0f);
        assertEquals(a.m32, m.m32(), 0.0f);
        assertEquals(0.0f, m.m03(), 0.0f);
        assertEquals(0.0f, m.m13(), 0.0f);
        assertEquals(0.0f, m.m23(), 0.0f);
        assertEquals(1.0f, m.m33(), 0.0f);
    }

    private static Transformf randomTransform(SplittableRandom rnd) {
        Transformf t = new Transformf();
        t.translation.set((float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0));
        t.rotation.identity().rotateAxis((float) rnd.nextDouble(0.0, java.lang.Math.PI * 2.0),
                (float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0)).normalize();
        t.scale.set((float) rnd.nextDouble(0.5, 2.5), (float) rnd.nextDouble(0.5, 2.5), (float) rnd.nextDouble(0.5, 2.5));
        return t;
    }
}
