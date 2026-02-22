/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */

package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.vectrix.core.Vector4f;
import org.vectrix.curve.vec4.Bezier4f;

class CurveVec4Test {
    @Test
    void vec4BatchOffsetMatchesSingle() {
        Vector4f p0 = new Vector4f(0, 0, 0, 1);
        Vector4f p1 = new Vector4f(1, 2, 0, 1);
        Vector4f p2 = new Vector4f(2, 0, 1, 1);
        Vector4f p3 = new Vector4f(3, 3, 2, 1);
        float[] t = {0.1f, 0.2f, 0.5f, 0.9f};
        float[] ox = new float[10], oy = new float[10], oz = new float[10], ow = new float[10];
        Bezier4f.evaluateBatch(p0, p1, p2, p3, t, 1, 2, ox, 4, oy, 4, oz, 4, ow, 4);
        for (int i = 0; i < 2; i++) {
            Vector4f v = Bezier4f.evaluate(p0, p1, p2, p3, t[1 + i], new Vector4f());
            assertEquals(v.x, ox[4 + i], 1E-6f);
            assertEquals(v.y, oy[4 + i], 1E-6f);
            assertEquals(v.z, oz[4 + i], 1E-6f);
            assertEquals(v.w, ow[4 + i], 1E-6f);
        }
    }
}
