/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.vectrix.affine.DualQuatTransformf;
import org.vectrix.affine.Transformf;
import org.vectrix.core.Quaternionf;
import org.vectrix.core.Vector3f;
import org.vectrix.soa.AABBSoA;
import org.vectrix.soa.DualQuatSoA;
import org.vectrix.soa.TransformSoA;

class SoAStorageTest {
    @Test
    void transformSoADefaultsAndRoundTrip() {
        TransformSoA soa = new TransformSoA(3);
        assertEquals(3, soa.size());
        for (int i = 0; i < soa.size(); i++) {
            assertEquals(1.0f, soa.qw[i], 0.0f);
            assertEquals(1.0f, soa.sx[i], 0.0f);
            assertEquals(1.0f, soa.sy[i], 0.0f);
            assertEquals(1.0f, soa.sz[i], 0.0f);
        }

        Transformf in = new Transformf(
                new Vector3f(3.0f, -2.0f, 4.0f),
                new Quaternionf().rotationXYZ(0.1f, 0.2f, -0.3f),
                new Vector3f(2.0f, 2.5f, 3.0f));
        soa.set(1, in);

        Transformf out = soa.get(1, new Transformf());
        assertEquals(in.translation.x, out.translation.x, 1E-6f);
        assertEquals(in.translation.y, out.translation.y, 1E-6f);
        assertEquals(in.translation.z, out.translation.z, 1E-6f);
        assertEquals(in.rotation.x, out.rotation.x, 1E-6f);
        assertEquals(in.rotation.y, out.rotation.y, 1E-6f);
        assertEquals(in.rotation.z, out.rotation.z, 1E-6f);
        assertEquals(in.rotation.w, out.rotation.w, 1E-6f);
        assertEquals(in.scale.x, out.scale.x, 1E-6f);
        assertEquals(in.scale.y, out.scale.y, 1E-6f);
        assertEquals(in.scale.z, out.scale.z, 1E-6f);
    }

    @Test
    void dualQuatSoADefaultsAndSet() {
        DualQuatSoA soa = new DualQuatSoA(2);
        assertEquals(1.0f, soa.rw[0], 0.0f);
        assertEquals(1.0f, soa.rw[1], 0.0f);

        DualQuatTransformf dq = new DualQuatTransformf();
        dq.real.set(0.2f, -0.1f, 0.3f, 0.92f).normalize();
        dq.dual.set(0.4f, -0.5f, 0.6f, -0.7f);
        soa.set(1, dq);

        assertEquals(dq.real.x, soa.rx[1], 1E-6f);
        assertEquals(dq.real.y, soa.ry[1], 1E-6f);
        assertEquals(dq.real.z, soa.rz[1], 1E-6f);
        assertEquals(dq.real.w, soa.rw[1], 1E-6f);
        assertEquals(dq.dual.x, soa.dx[1], 1E-6f);
        assertEquals(dq.dual.y, soa.dy[1], 1E-6f);
        assertEquals(dq.dual.z, soa.dz[1], 1E-6f);
        assertEquals(dq.dual.w, soa.dw[1], 1E-6f);
    }

    @Test
    void aabbSoASetAndSize() {
        AABBSoA soa = new AABBSoA(2);
        assertEquals(2, soa.size());
        soa.set(1, -1.0f, -2.0f, -3.0f, 4.0f, 5.0f, 6.0f);
        assertEquals(-1.0f, soa.minX[1], 0.0f);
        assertEquals(-2.0f, soa.minY[1], 0.0f);
        assertEquals(-3.0f, soa.minZ[1], 0.0f);
        assertEquals(4.0f, soa.maxX[1], 0.0f);
        assertEquals(5.0f, soa.maxY[1], 0.0f);
        assertEquals(6.0f, soa.maxZ[1], 0.0f);
    }
}
