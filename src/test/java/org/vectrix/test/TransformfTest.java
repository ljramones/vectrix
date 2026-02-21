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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.vectrix.test.TestUtil.assertMatrix4x3fEquals;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.vectrix.experimental.KernelConfig;
import org.vectrix.experimental.MathMode;
import org.vectrix.affine.TransformKernels;
import org.vectrix.affine.Transformf;
import org.vectrix.core.Matrix4x3f;
import org.vectrix.core.Vector3f;
import org.vectrix.soa.TransformSoA;

class TransformfTest {
    @Test
    void composeMatchesAffineMatrixProduct() {
        Transformf parent = new Transformf(
                new Vector3f(2.0f, -1.0f, 5.0f),
                new org.vectrix.core.Quaternionf().rotationXYZ(0.3f, -0.2f, 0.9f),
                new Vector3f(1.5f, 1.5f, 1.5f));
        Transformf local = new Transformf(
                new Vector3f(-3.0f, 4.0f, 0.5f),
                new org.vectrix.core.Quaternionf().rotationXYZ(-0.1f, 0.4f, -0.7f),
                new Vector3f(0.7f, 0.7f, 0.7f));

        Transformf world = Transformf.compose(parent, local, new Transformf());
        Matrix4x3f mWorldFromTrs = world.toAffineMat4Fast(new Matrix4x3f());
        Matrix4x3f mWorldFromMul = parent.toAffineMat4Fast(new Matrix4x3f())
                .mul(local.toAffineMat4Fast(new Matrix4x3f()), new Matrix4x3f());
        assertMatrix4x3fEquals(mWorldFromTrs, mWorldFromMul, 1E-5f);
    }

    @Test
    void invertRigidProducesIdentityWhenComposed() {
        Transformf t = new Transformf(
                new Vector3f(4.0f, -2.0f, 1.0f),
                new org.vectrix.core.Quaternionf().rotationXYZ(0.6f, -0.3f, 0.1f),
                new Vector3f(1.0f, 1.0f, 1.0f));
        Transformf inv = t.invertRigidFast(new Transformf());
        Transformf id = Transformf.compose(inv, t, new Transformf());
        assertEquals(0.0f, id.translation.length(), 1E-5f);
        assertEquals(0.0f, id.rotation.x, 1E-5f);
        assertEquals(0.0f, id.rotation.y, 1E-5f);
        assertEquals(0.0f, id.rotation.z, 1E-5f);
        assertEquals(1.0f, id.rotation.w, 1E-5f);
        assertEquals(1.0f, id.scale.x, 1E-6f);
        assertEquals(1.0f, id.scale.y, 1E-6f);
        assertEquals(1.0f, id.scale.z, 1E-6f);
    }

    @Test
    void batchComposeMatchesObjectCompose() {
        int count = 64;
        SplittableRandom rnd = new SplittableRandom(99L);
        TransformSoA parents = new TransformSoA(count);
        TransformSoA locals = new TransformSoA(count);
        TransformSoA out = new TransformSoA(count);
        Transformf p = new Transformf();
        Transformf l = new Transformf();
        Transformf composed = new Transformf();
        Transformf fromBatch = new Transformf();
        for (int i = 0; i < count; i++) {
            randomTransform(rnd, p);
            randomTransform(rnd, l);
            parents.set(i, p);
            locals.set(i, l);
        }
        TransformKernels.composeBatch(parents, locals, out, count);
        for (int i = 0; i < count; i++) {
            parents.get(i, p);
            locals.get(i, l);
            Transformf.compose(p, l, composed);
            out.get(i, fromBatch);
            assertEquals(composed.translation.x, fromBatch.translation.x, 1E-5f);
            assertEquals(composed.translation.y, fromBatch.translation.y, 1E-5f);
            assertEquals(composed.translation.z, fromBatch.translation.z, 1E-5f);
            assertEquals(composed.scale.x, fromBatch.scale.x, 1E-5f);
            assertEquals(composed.scale.y, fromBatch.scale.y, 1E-5f);
            assertEquals(composed.scale.z, fromBatch.scale.z, 1E-5f);
            assertEquals(composed.rotation.x, fromBatch.rotation.x, 1E-5f);
            assertEquals(composed.rotation.y, fromBatch.rotation.y, 1E-5f);
            assertEquals(composed.rotation.z, fromBatch.rotation.z, 1E-5f);
            assertEquals(composed.rotation.w, fromBatch.rotation.w, 1E-5f);
        }
    }

    private static void randomTransform(SplittableRandom rnd, Transformf t) {
        t.translation.set((float) (rnd.nextDouble() * 20.0 - 10.0), (float) (rnd.nextDouble() * 20.0 - 10.0),
                (float) (rnd.nextDouble() * 20.0 - 10.0));
        t.rotation.identity().rotateAxis((float) (rnd.nextDouble() * java.lang.Math.PI * 2.0),
                (float) (rnd.nextDouble() * 2.0 - 1.0), (float) (rnd.nextDouble() * 2.0 - 1.0),
                (float) (rnd.nextDouble() * 2.0 - 1.0)).normalize();
        t.scale.set(0.5f + (float) rnd.nextDouble() * 2.0f, 0.5f + (float) rnd.nextDouble() * 2.0f,
                0.5f + (float) rnd.nextDouble() * 2.0f);
    }

    @Test
    void interpolateFastBlendsTranslationScaleAndRotation() {
        Transformf a = new Transformf(
                new Vector3f(0.0f, 0.0f, 0.0f),
                new org.vectrix.core.Quaternionf().identity(),
                new Vector3f(1.0f, 1.0f, 1.0f));
        Transformf b = new Transformf(
                new Vector3f(10.0f, -4.0f, 2.0f),
                new org.vectrix.core.Quaternionf().rotationXYZ(0.2f, -0.4f, 0.8f),
                new Vector3f(3.0f, 5.0f, 7.0f));

        Transformf out = a.interpolateFast(b, 0.25f, new Transformf());
        assertEquals(2.5f, out.translation.x, 1E-6f);
        assertEquals(-1.0f, out.translation.y, 1E-6f);
        assertEquals(0.5f, out.translation.z, 1E-6f);
        assertEquals(1.5f, out.scale.x, 1E-6f);
        assertEquals(2.0f, out.scale.y, 1E-6f);
        assertEquals(2.5f, out.scale.z, 1E-6f);
        float rotLen2 = out.rotation.x * out.rotation.x + out.rotation.y * out.rotation.y
                + out.rotation.z * out.rotation.z + out.rotation.w * out.rotation.w;
        assertTrue(java.lang.Math.abs(rotLen2 - 1.0f) < 1E-4f);
    }

    @Test
    void composeBatchStrictNormalizesQuaternionOutput() {
        MathMode prev = KernelConfig.mathMode();
        boolean prevSimd = KernelConfig.simdEnabled();
        try {
            KernelConfig.setMathMode(MathMode.STRICT);
            KernelConfig.setSimdEnabled(false);
            TransformSoA p = new TransformSoA(1);
            TransformSoA l = new TransformSoA(1);
            TransformSoA o = new TransformSoA(1);
            p.qx[0] = 0.2f;
            p.qy[0] = 0.3f;
            p.qz[0] = -0.4f;
            p.qw[0] = 0.5f;
            l.qx[0] = -0.1f;
            l.qy[0] = 0.7f;
            l.qz[0] = 0.2f;
            l.qw[0] = -0.3f;
            TransformKernels.composeBatch(p, l, o, 1);
            float len2 = o.qx[0] * o.qx[0] + o.qy[0] * o.qy[0] + o.qz[0] * o.qz[0] + o.qw[0] * o.qw[0];
            assertEquals(1.0f, len2, 1E-5f);
        } finally {
            KernelConfig.setMathMode(prev);
            KernelConfig.setSimdEnabled(prevSimd);
        }
    }
}
