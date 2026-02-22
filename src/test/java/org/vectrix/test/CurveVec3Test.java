/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */

package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.vectrix.core.Vector3d;
import org.vectrix.core.Vector3f;
import org.vectrix.curve.CurveReparameterizer3f;
import org.vectrix.curve.vec3.Bezier3d;
import org.vectrix.curve.vec3.Bezier3f;
import org.vectrix.curve.vec3.Hermite3f;
import org.vectrix.curve.vec3.UniformBSpline3f;

class CurveVec3Test {
    @Test
    void bezierBatchOffsetMatchesSingle() {
        Vector3f p0 = new Vector3f(0, 0, 0);
        Vector3f p1 = new Vector3f(1, 2, 0);
        Vector3f p2 = new Vector3f(2, 0, 1);
        Vector3f p3 = new Vector3f(3, 3, 2);
        float[] t = {9, 0.2f, 0.5f, 0.8f, 9};
        float[] ox = new float[8], oy = new float[8], oz = new float[8];
        Bezier3f.evaluateBatch(p0, p1, p2, p3, t, 1, 3, ox, 2, oy, 2, oz, 2);
        for (int i = 0; i < 3; i++) {
            Vector3f v = Bezier3f.evaluate(p0, p1, p2, p3, t[1 + i], new Vector3f());
            assertEquals(v.x, ox[2 + i], 1E-6f);
            assertEquals(v.y, oy[2 + i], 1E-6f);
            assertEquals(v.z, oz[2 + i], 1E-6f);
        }
    }

    @Test
    void hermiteBatchOffsetMatchesSingle() {
        Vector3f p0 = new Vector3f(0, 0, 0);
        Vector3f m0 = new Vector3f(1, 0, 0);
        Vector3f p1 = new Vector3f(2, 1, -1);
        Vector3f m1 = new Vector3f(0, 1, 0);
        float[] t = {0.1f, 0.3f, 0.6f, 0.9f};
        float[] ox = new float[8], oy = new float[8], oz = new float[8];
        Hermite3f.evaluateBatch(p0, m0, p1, m1, t, 1, 2, ox, 3, oy, 3, oz, 3);
        for (int i = 0; i < 2; i++) {
            Vector3f v = Hermite3f.evaluate(p0, m0, p1, m1, t[1 + i], new Vector3f());
            assertEquals(v.x, ox[3 + i], 1E-6f);
            assertEquals(v.y, oy[3 + i], 1E-6f);
            assertEquals(v.z, oz[3 + i], 1E-6f);
        }
    }

    @Test
    void uniformBsplineAndReparameterizerWork() {
        UniformBSpline3f s = new UniformBSpline3f(new Vector3f[] {
                new Vector3f(0, 0, 0),
                new Vector3f(1, 0, 0),
                new Vector3f(2, 1, 0),
                new Vector3f(3, 1, 1),
                new Vector3f(4, 0, 1)
        });
        float[] table = new float[33];
        CurveReparameterizer3f.buildArcLengthTable(s, 0, 32, table);
        assertEquals(0.0f, table[0], 1E-6f);
        assertEquals(1.0f, table[32], 1E-6f);
        float t = CurveReparameterizer3f.mapArcLengthToT(0.5f, table);
        assertTrue(t > 0.0f && t < 1.0f);
        Vector3f v = CurveReparameterizer3f.evaluateByArcLength(s, 0, 0.5f, table, new Vector3f());
        assertTrue(Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z));
    }

    @Test
    void floatDoubleParityForBezier3() {
        Vector3f p0f = new Vector3f(0.1f, 0.2f, -0.1f);
        Vector3f p1f = new Vector3f(1.0f, -0.5f, 0.3f);
        Vector3f p2f = new Vector3f(1.5f, 0.8f, 0.6f);
        Vector3f p3f = new Vector3f(2.0f, 1.1f, -0.2f);
        Vector3f vf = Bezier3f.evaluate(p0f, p1f, p2f, p3f, 0.37f, new Vector3f());

        Vector3d p0d = new Vector3d(p0f.x, p0f.y, p0f.z);
        Vector3d p1d = new Vector3d(p1f.x, p1f.y, p1f.z);
        Vector3d p2d = new Vector3d(p2f.x, p2f.y, p2f.z);
        Vector3d p3d = new Vector3d(p3f.x, p3f.y, p3f.z);
        Vector3d vd = Bezier3d.evaluate(p0d, p1d, p2d, p3d, 0.37, new Vector3d());

        assertEquals(vd.x, vf.x, 1E-5);
        assertEquals(vd.y, vf.y, 1E-5);
        assertEquals(vd.z, vf.z, 1E-5);
    }
}
