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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamisengine.vectrix.core.RoundingMode;
import org.dynamisengine.vectrix.core.Vector2L;
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector3L;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4L;
import org.dynamisengine.vectrix.curve.CurveReparameterizer3d;
import org.dynamisengine.vectrix.curve.CurveReparameterizer3f;
import org.dynamisengine.vectrix.core.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * API additions made with the Batch 3 fixes: long-valued {@link Vector3L} construction (parity with
 * {@link Vector2L}/{@link Vector4L}), int component indices from {@link Vector2L}, and the explicit-sample-count
 * arc-length lookup (review finding M-6).
 */
class LongVectorApiTest {

    @Test
    void vector3LAcceptsLongValues() {
        Vector3L v = new Vector3L(5_000_000_000L, -7_000_000_000L, 1L << 62);
        assertEquals(5_000_000_000L, v.x);
        assertEquals(-7_000_000_000L, v.y);
        assertEquals(1L << 62, v.z);
        assertEquals(new Vector3L(3_000_000_000L, 3_000_000_000L, 3_000_000_000L), new Vector3L(3_000_000_000L));
        assertEquals(v, new Vector3L(new long[] {5_000_000_000L, -7_000_000_000L, 1L << 62}));
        assertEquals(9_000_000_000L, new Vector3L().setComponent(1, 9_000_000_000L).y);
        assertEquals(4_000_000_001L, new Vector3L(new Vector2d(0, 0), 4_000_000_001.0, RoundingMode.HALF_EVEN).z);
        assertEquals(new Vector4L(1, 2, 3, 6_000_000_000L), new Vector4L(new long[] {1, 2, 3, 6_000_000_000L}));
    }

    @Test
    void vector2LComponentIndicesAreInts() {
        int max = new Vector2L(-5, 3).maxComponent();
        int min = new Vector2L(-5, 3).minComponent();
        assertEquals(0, max);
        assertEquals(1, min);
    }

    private static float[] bezierTable(int samples, int length) {
        float[] t = new float[length];
        CurveReparameterizer3f.buildArcLengthTableForBezier(new Vector3f(0, 0, 0), new Vector3f(1, 2, 0),
                new Vector3f(3, -1, 0), new Vector3f(4, 0, 1), samples, t);
        return t;
    }

    @Test
    void arcLengthLookupWithOversizedTableUsesTheSampleCount() {
        float[] exact = bezierTable(16, 17);
        float[] oversized = bezierTable(16, 65); // reused scratch array: entries 17..64 are zero
        for (float s = 0.0f; s <= 1.0f; s += 0.0625f) {
            float expected = CurveReparameterizer3f.mapArcLengthToT(s, exact);
            assertEquals(expected, CurveReparameterizer3f.mapArcLengthToT(s, oversized, 16), 1e-7f, "s=" + s);
        }
        assertEquals(0.526f, CurveReparameterizer3f.mapArcLengthToT(0.5f, oversized, 16), 0.05f); // was 0.984
        assertTrue(Float.isNaN(CurveReparameterizer3f.mapArcLengthToT(Float.NaN, exact)));
        assertThrows(IllegalArgumentException.class, () -> CurveReparameterizer3f.mapArcLengthToT(0.5f, exact, 17));
        assertThrows(IllegalArgumentException.class, () -> CurveReparameterizer3f.mapArcLengthToT(0.5f, exact, 0));
    }

    @Test
    void arcLengthLookupDoublePrecisionMatches() {
        double[] exact = new double[17];
        double[] oversized = new double[40];
        Vector3d p0 = new Vector3d(0, 0, 0), p1 = new Vector3d(1, 2, 0), p2 = new Vector3d(3, -1, 0), p3 = new Vector3d(4, 0, 1);
        CurveReparameterizer3d.buildArcLengthTableForBezier(p0, p1, p2, p3, 16, exact);
        CurveReparameterizer3d.buildArcLengthTableForBezier(p0, p1, p2, p3, 16, oversized);
        for (double s = 0.0; s <= 1.0; s += 0.03125) {
            assertEquals(CurveReparameterizer3d.mapArcLengthToT(s, exact),
                    CurveReparameterizer3d.mapArcLengthToT(s, oversized, 16), 1e-15, "s=" + s);
        }
        assertTrue(Double.isNaN(CurveReparameterizer3d.mapArcLengthToT(Double.NaN, exact)));
    }
}
