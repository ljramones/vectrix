/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */

package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.vectrix.curve.scalar.Bezier1d;
import org.vectrix.curve.scalar.Bezier1f;
import org.vectrix.curve.scalar.CatmullRom1f;
import org.vectrix.curve.scalar.Hermite1f;
import org.vectrix.curve.scalar.UniformBSpline1f;

class CurveScalarTest {
    @Test
    void bezierEndpointsAndBatchOffset() {
        assertEquals(1.0f, Bezier1f.evaluate(1, 2, 3, 4, 0.0f), 1E-6f);
        assertEquals(4.0f, Bezier1f.evaluate(1, 2, 3, 4, 1.0f), 1E-6f);

        float[] t = {9.0f, 0.1f, 0.3f, 0.7f, 9.0f};
        float[] out = new float[6];
        Bezier1f.evaluateBatch(1, 2, 3, 4, t, 1, 3, out, 2);
        assertEquals(0.0f, out[1], 1E-6f);
        assertEquals(Bezier1f.evaluate(1, 2, 3, 4, t[1]), out[2], 1E-6f);
        assertEquals(Bezier1f.evaluate(1, 2, 3, 4, t[2]), out[3], 1E-6f);
        assertEquals(Bezier1f.evaluate(1, 2, 3, 4, t[3]), out[4], 1E-6f);
    }

    @Test
    void hermiteAndCatmullProduceFiniteValues() {
        float h = Hermite1f.evaluate(0.0f, 1.0f, 2.0f, -1.0f, 0.4f);
        float c = CatmullRom1f.evaluate(-1.0f, 0.0f, 2.0f, 3.0f, 0.4f, 0.0f);
        assertEquals(false, Float.isNaN(h));
        assertEquals(false, Float.isNaN(c));
    }

    @Test
    void uniformBsplineBatchOffsetMatchesSingle() {
        UniformBSpline1f s = new UniformBSpline1f(new float[] {0, 1, 2, 3, 4, 5});
        float[] t = {0.0f, 0.2f, 0.5f, 0.9f};
        float[] out = new float[8];
        s.evaluateBatch(1, t, 1, 2, out, 3);
        assertEquals(s.evaluate(1, t[1]), out[3], 1E-6f);
        assertEquals(s.evaluate(1, t[2]), out[4], 1E-6f);
    }

    @Test
    void floatDoubleParityForBezier() {
        float tf = 0.37f;
        double td = 0.37;
        double d = Bezier1d.evaluate(0.1, 1.2, -0.4, 2.3, td);
        float f = Bezier1f.evaluate(0.1f, 1.2f, -0.4f, 2.3f, tf);
        assertEquals(d, f, 1E-5);
    }
}
