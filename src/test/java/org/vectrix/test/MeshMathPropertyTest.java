/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.vectrix.core.Vector3f;
import org.vectrix.geometry.MeshMath;

class MeshMathPropertyTest {
    @Test
    void barycentricRoundTripRandomized() {
        SplittableRandom rnd = new SplittableRandom(0xBADC0FFEE0DDF00DL);
        Vector3f bary = new Vector3f();

        for (int i = 0; i < 2000; i++) {
            Vector3f a = randomVec3(rnd);
            Vector3f b = randomVec3(rnd);
            Vector3f c = randomVec3(rnd);
            if (MeshMath.triangleArea(a, b, c) < 1.0E-2f) {
                i--;
                continue;
            }

            float u = (float) rnd.nextDouble();
            float v = (float) rnd.nextDouble() * (1.0f - u);
            float w = 1.0f - u - v;
            Vector3f p = new Vector3f(
                    a.x * u + b.x * v + c.x * w,
                    a.y * u + b.y * v + c.y * w,
                    a.z * u + b.z * v + c.z * w);

            MeshMath.barycentric(p, a, b, c, bary);
            assertFalse(Float.isNaN(bary.x) || Float.isNaN(bary.y) || Float.isNaN(bary.z));
            assertTrue(java.lang.Math.abs((bary.x + bary.y + bary.z) - 1.0f) < 1.0E-4f);
            assertTrue(java.lang.Math.abs(bary.x - u) < 2.0E-3f);
            assertTrue(java.lang.Math.abs(bary.y - v) < 2.0E-3f);
            assertTrue(java.lang.Math.abs(bary.z - w) < 2.0E-3f);
            assertTrue(MeshMath.isInsideBarycentric(bary));
        }
    }

    @Test
    void closestPointIsInsideAndNoWorseThanVertices() {
        SplittableRandom rnd = new SplittableRandom(0x1234ABCD5678EF90L);
        Vector3f q = new Vector3f();
        Vector3f bc = new Vector3f();

        for (int i = 0; i < 2000; i++) {
            Vector3f a = randomVec3(rnd);
            Vector3f b = randomVec3(rnd);
            Vector3f c = randomVec3(rnd);
            if (MeshMath.triangleArea(a, b, c) < 1.0E-2f) {
                i--;
                continue;
            }

            Vector3f p = randomVec3(rnd);
            MeshMath.closestPointOnTriangle(p, a, b, c, q);
            MeshMath.barycentric(q, a, b, c, bc);
            assertTrue(MeshMath.isInsideBarycentric(bc) || isNearInside(bc));

            float dQ = p.distanceSquared(q);
            float dA = p.distanceSquared(a);
            float dB = p.distanceSquared(b);
            float dC = p.distanceSquared(c);
            assertTrue(dQ <= dA + 5.0E-3f);
            assertTrue(dQ <= dB + 5.0E-3f);
            assertTrue(dQ <= dC + 5.0E-3f);
        }
    }

    @Test
    void barycentricDegenerateTriangleReturnsNaNs() {
        Vector3f a = new Vector3f(1.0f, 1.0f, 1.0f);
        Vector3f b = new Vector3f(2.0f, 2.0f, 2.0f);
        Vector3f c = new Vector3f(3.0f, 3.0f, 3.0f);
        Vector3f p = new Vector3f(1.5f, 1.5f, 1.5f);
        Vector3f bc = MeshMath.barycentric(p, a, b, c, new Vector3f());
        assertTrue(Float.isNaN(bc.x) && Float.isNaN(bc.y) && Float.isNaN(bc.z));
    }

    private static Vector3f randomVec3(SplittableRandom rnd) {
        return new Vector3f(
                (float) rnd.nextDouble(-100.0, 100.0),
                (float) rnd.nextDouble(-100.0, 100.0),
                (float) rnd.nextDouble(-100.0, 100.0));
    }

    private static boolean isNearInside(Vector3f bc) {
        float e = 1.0E-4f;
        return bc.x >= -e && bc.y >= -e && bc.z >= -e;
    }
}
