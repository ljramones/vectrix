/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.vectrix.core.Vector2f;
import org.vectrix.core.Vector3f;
import org.vectrix.geometry.MeshMath;

class MeshMathTest {
    @Test
    void barycentricCenter() {
        Vector3f a = new Vector3f(0.0f, 0.0f, 0.0f);
        Vector3f b = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f c = new Vector3f(0.0f, 1.0f, 0.0f);
        Vector3f p = new Vector3f(1.0f / 3.0f, 1.0f / 3.0f, 0.0f);
        Vector3f bc = MeshMath.barycentric(p, a, b, c, new Vector3f());
        assertEquals(1.0f, bc.x + bc.y + bc.z, 1E-6f);
        assertTrue(MeshMath.isInsideBarycentric(bc));
    }

    @Test
    void closestPointOnTriangle() {
        Vector3f a = new Vector3f(0.0f, 0.0f, 0.0f);
        Vector3f b = new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f c = new Vector3f(0.0f, 1.0f, 0.0f);
        Vector3f p = new Vector3f(2.0f, 2.0f, 0.0f);
        Vector3f q = MeshMath.closestPointOnTriangle(p, a, b, c, new Vector3f());
        assertEquals(0.5f, q.x, 1E-6f);
        assertEquals(0.5f, q.y, 1E-6f);
        assertEquals(0.0f, q.z, 1E-6f);
    }

    @Test
    void windingAndArea() {
        Vector2f a = new Vector2f(0.0f, 0.0f);
        Vector2f b = new Vector2f(1.0f, 0.0f);
        Vector2f c = new Vector2f(0.0f, 1.0f);
        assertEquals(1, MeshMath.winding2D(a, b, c));
        assertEquals(-1, MeshMath.winding2D(a, c, b));
        assertEquals(0, MeshMath.winding2D(a, b, new Vector2f(2.0f, 0.0f)));
        assertEquals(0.5f, MeshMath.triangleArea(new Vector3f(a, 0.0f), new Vector3f(b, 0.0f), new Vector3f(c, 0.0f)), 1E-6f);
    }

    @Test
    void outsideBarycentricDetected() {
        Vector3f bc = new Vector3f(-0.2f, 0.7f, 0.5f);
        assertFalse(MeshMath.isInsideBarycentric(bc));
    }
}
