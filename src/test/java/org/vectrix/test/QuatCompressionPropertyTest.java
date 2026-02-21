/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.vectrix.core.Quaternionf;
import org.vectrix.gpu.QuatCompression;

class QuatCompressionPropertyTest {
    @Test
    void randomRoundTripAngularErrorBound() {
        SplittableRandom rnd = new SplittableRandom(0xCAFEBABE12345678L);

        for (int i = 0; i < 10000; i++) {
            Quaternionf q = randomUnitQuaternion(rnd);
            long packed = QuatCompression.packSmallest3(q);
            Quaternionf out = QuatCompression.unpackSmallest3(packed, new Quaternionf());
            float errDeg = QuatCompression.angularErrorDegrees(q, out);
            assertTrue(errDeg < 0.15f);

            float norm = out.x * out.x + out.y * out.y + out.z * out.z + out.w * out.w;
            assertTrue(java.lang.Math.abs(norm - 1.0f) < 2.0E-3f);
        }
    }

    @Test
    void signEquivalentInputsDecodeToSameRotation() {
        SplittableRandom rnd = new SplittableRandom(0x55AA55AA7711EEFFL);

        for (int i = 0; i < 5000; i++) {
            Quaternionf q = randomUnitQuaternion(rnd);
            Quaternionf nq = new Quaternionf(-q.x, -q.y, -q.z, -q.w);

            Quaternionf a = QuatCompression.unpackSmallest3(QuatCompression.packSmallest3(q), new Quaternionf());
            Quaternionf b = QuatCompression.unpackSmallest3(QuatCompression.packSmallest3(nq), new Quaternionf());
            float errDeg = QuatCompression.angularErrorDegrees(a, b);
            assertTrue(errDeg < 0.2f);
        }
    }

    private static Quaternionf randomUnitQuaternion(SplittableRandom rnd) {
        float x = (float) rnd.nextDouble(-1.0, 1.0);
        float y = (float) rnd.nextDouble(-1.0, 1.0);
        float z = (float) rnd.nextDouble(-1.0, 1.0);
        float w = (float) rnd.nextDouble(-1.0, 1.0);
        Quaternionf q = new Quaternionf(x, y, z, w);
        if (q.lengthSquared() < 1.0E-8f) {
            q.set(0.0f, 0.0f, 0.0f, 1.0f);
        } else {
            q.normalize();
        }
        return q;
    }
}
