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

import org.junit.jupiter.api.Test;
import org.vectrix.affine.DualQuatTransformf;
import org.vectrix.affine.RigidTransformf;
import org.vectrix.core.Vector3f;
import org.vectrix.soa.DualQuatSoA;
import org.vectrix.soa.SkinningKernels;
import org.vectrix.soa.TransformSoA;

class RigidDualQuatTest {
    @Test
    void rigidInverseComposeIdentity() {
        RigidTransformf t = new RigidTransformf(
                new Vector3f(1.5f, -2.0f, 0.25f),
                new org.vectrix.core.Quaternionf().rotationXYZ(0.3f, -0.7f, 0.5f));
        RigidTransformf inv = t.invertRigidFast(new RigidTransformf());
        RigidTransformf id = RigidTransformf.compose(inv, t, new RigidTransformf());
        assertEquals(0.0f, id.translation.x, 1E-6f);
        assertEquals(0.0f, id.translation.y, 1E-6f);
        assertEquals(0.0f, id.translation.z, 1E-6f);
        assertEquals(0.0f, id.rotation.x, 1E-6f);
        assertEquals(0.0f, id.rotation.y, 1E-6f);
        assertEquals(0.0f, id.rotation.z, 1E-6f);
        assertEquals(1.0f, id.rotation.w, 1E-6f);
    }

    @Test
    void dualQuatTransformMatchesRigidTransform() {
        RigidTransformf r = new RigidTransformf(
                new Vector3f(-0.4f, 1.2f, 0.7f),
                new org.vectrix.core.Quaternionf().rotationXYZ(0.2f, 0.1f, -0.5f));
        DualQuatTransformf dq = new DualQuatTransformf().setFromRigid(r);
        Vector3f a = r.transformPosition(0.3f, -0.1f, 0.6f, new Vector3f());
        Vector3f b = dq.transformPosition(0.3f, -0.1f, 0.6f, new Vector3f());
        assertEquals(a.x, b.x, 1E-5f);
        assertEquals(a.y, b.y, 1E-5f);
        assertEquals(a.z, b.z, 1E-5f);
    }

    @Test
    void skinningKernelsSingleWeightMatch() {
        TransformSoA joints = new TransformSoA(1);
        DualQuatSoA dqs = new DualQuatSoA(1);
        RigidTransformf r = new RigidTransformf(
                new Vector3f(0.5f, -0.2f, 0.8f),
                new org.vectrix.core.Quaternionf().rotationXYZ(0.3f, -0.2f, 0.1f));
        joints.tx[0] = r.translation.x;
        joints.ty[0] = r.translation.y;
        joints.tz[0] = r.translation.z;
        joints.qx[0] = r.rotation.x;
        joints.qy[0] = r.rotation.y;
        joints.qz[0] = r.rotation.z;
        joints.qw[0] = r.rotation.w;
        dqs.set(0, new DualQuatTransformf().setFromRigid(r));

        int[] ji = {0, 0, 0, 0};
        float[] jw = {1.0f, 0.0f, 0.0f, 0.0f};
        float[] inX = {0.2f}, inY = {-0.5f}, inZ = {0.9f};
        float[] lx = {0.0f}, ly = {0.0f}, lz = {0.0f};
        float[] dx = {0.0f}, dy = {0.0f}, dz = {0.0f};

        SkinningKernels.skinLbs4(joints, ji, jw, inX, inY, inZ, lx, ly, lz, 1);
        SkinningKernels.skinDualQuat4(dqs, ji, jw, inX, inY, inZ, dx, dy, dz, 1);
        assertEquals(lx[0], dx[0], 1E-5f);
        assertEquals(ly[0], dy[0], 1E-5f);
        assertEquals(lz[0], dz[0], 1E-5f);
    }

    @Test
    void dualQuatNormalizeProducesUnitRealPart() {
        DualQuatTransformf dq = new DualQuatTransformf();
        dq.real.set(0.5f, -0.25f, 0.75f, 0.1f);
        dq.dual.set(0.2f, -0.4f, 0.6f, -0.8f);
        dq.normalize();
        float len2 = dq.real.x * dq.real.x + dq.real.y * dq.real.y + dq.real.z * dq.real.z + dq.real.w * dq.real.w;
        assertTrue(java.lang.Math.abs(len2 - 1.0f) < 1E-5f);
    }

    @Test
    void dualQuatSetCopiesComponents() {
        DualQuatTransformf a = new DualQuatTransformf();
        a.real.set(0.1f, 0.2f, 0.3f, 0.9f).normalize();
        a.dual.set(-0.3f, 0.4f, -0.5f, 0.6f);
        DualQuatTransformf b = new DualQuatTransformf().set(a);
        assertEquals(a.real.x, b.real.x, 1E-6f);
        assertEquals(a.real.y, b.real.y, 1E-6f);
        assertEquals(a.real.z, b.real.z, 1E-6f);
        assertEquals(a.real.w, b.real.w, 1E-6f);
        assertEquals(a.dual.x, b.dual.x, 1E-6f);
        assertEquals(a.dual.y, b.dual.y, 1E-6f);
        assertEquals(a.dual.z, b.dual.z, 1E-6f);
        assertEquals(a.dual.w, b.dual.w, 1E-6f);
    }
}
