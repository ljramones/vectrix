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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.vectrix.core.IntegratorRK4d;
import org.vectrix.core.IntegratorRK4f;
import org.vectrix.core.OdeDerivatived;
import org.vectrix.core.OdeDerivativef;

class IntegratorRK4Test {
    @Test
    void rk4FloatMatchesExpGrowthInPlace() {
        OdeDerivativef deriv = (t, s, so, d, doff) -> d[doff] = s[so];
        float[] state = {1.0f};
        float[] scratch = new float[5];
        float t = 0.0f;
        float dt = 0.1f;
        for (int i = 0; i < 10; i++) {
            IntegratorRK4f.step(deriv, t, dt, state, 0, 1, scratch, 0, state, 0);
            t += dt;
        }
        assertEquals((float) java.lang.Math.exp(1.0), state[0], 3E-4f);
    }

    @Test
    void rk4DoubleMatchesExpGrowth() {
        OdeDerivatived deriv = (t, s, so, d, doff) -> d[doff] = s[so];
        double[] state = {1.0};
        double[] dest = new double[1];
        double[] scratch = new double[5];
        double t = 0.0;
        double dt = 0.1;
        for (int i = 0; i < 10; i++) {
            IntegratorRK4d.step(deriv, t, dt, state, 0, 1, scratch, 0, dest, 0);
            state[0] = dest[0];
            t += dt;
        }
        assertEquals(java.lang.Math.exp(1.0), state[0], 3E-6);
    }

    @Test
    void inPlaceAndSeparateDestinationMatch() {
        OdeDerivativef osc = (t, s, so, d, doff) -> {
            float x = s[so];
            float v = s[so + 1];
            d[doff] = v;
            d[doff + 1] = -x;
        };
        float[] src = {1.0f, 0.0f};
        float[] inPlace = {1.0f, 0.0f};
        float[] out = new float[2];
        float[] scratchA = new float[10];
        float[] scratchB = new float[10];

        IntegratorRK4f.step(osc, 0.0f, 0.016f, src, 0, 2, scratchA, 0, out, 0);
        IntegratorRK4f.step(osc, 0.0f, 0.016f, inPlace, 0, 2, scratchB, 0, inPlace, 0);

        assertEquals(out[0], inPlace[0], 1E-7f);
        assertEquals(out[1], inPlace[1], 1E-7f);
    }

    @Test
    void supportsOffsets() {
        OdeDerivativef deriv = (t, s, so, d, doff) -> d[doff] = 2.0f * s[so];
        float[] state = new float[] {-1.0f, 1.0f, -1.0f};
        float[] dest = new float[] {0.0f, 0.0f, 0.0f};
        float[] baseline = new float[] {1.0f};
        float[] scratch = new float[7];
        float[] baselineScratch = new float[5];
        IntegratorRK4f.step(deriv, 0.0f, 0.25f, state, 1, 1, scratch, 2, dest, 1);
        IntegratorRK4f.step(deriv, 0.0f, 0.25f, baseline, 0, 1, baselineScratch, 0, baseline, 0);
        assertEquals(baseline[0], dest[1], 1E-7f);
    }

    @Test
    void invalidScratchOrBoundsThrow() {
        OdeDerivativef deriv = (t, s, so, d, doff) -> d[doff] = s[so];
        assertThrows(IllegalArgumentException.class,
            () -> IntegratorRK4f.step(deriv, 0.0f, 0.1f, new float[] {1f}, 0, 1, new float[4], 0, new float[] {0f}, 0));
        assertThrows(IllegalArgumentException.class,
            () -> IntegratorRK4f.step(deriv, 0.0f, 0.1f, new float[] {1f}, 1, 1, new float[5], 0, new float[] {0f}, 0));
    }
}
