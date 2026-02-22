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
import org.vectrix.core.Vector3f;
import org.vectrix.ltc.LtcEvalf;
import org.vectrix.ltc.LtcTablef;
import org.vectrix.renderingmath.BentNormalConef;
import org.vectrix.renderingmath.Interpolationd;
import org.vectrix.renderingmath.Interpolationf;

class LtcInterpolationBentConeTest {
    @Test
    void bilinearAndBicubicBasics() {
        float b = Interpolationf.bilinear(0f, 1f, 2f, 3f, 0.5f, 0.5f);
        assertEquals(1.5f, b, 1E-6f);

        float c = Interpolationf.cubicHermite(0f, 1f, 2f, 3f, 0.5f);
        assertEquals(1.5f, c, 1E-6f);
        assertEquals((double) c, Interpolationd.cubicHermite(0, 1, 2, 3, 0.5), 1E-12);

        float[] patch = new float[16];
        for (int i = 0; i < 16; i++) {
            patch[i] = 4.0f;
        }
        assertEquals(4.0f, Interpolationf.bicubicBSpline(patch, 0.3f, 0.7f), 1E-6f);
    }

    @Test
    void ltcTableSamplingAndEvalStayBounded() {
        int w = 2, h = 2;
        float[] matTable = new float[w * h * 9];
        for (int t = 0; t < w * h; t++) {
            int o = t * 9;
            matTable[o] = 1f;
            matTable[o + 4] = 1f;
            matTable[o + 8] = 1f;
        }
        float[] m = new float[9];
        LtcTablef.sampleMat3(matTable, w, h, 0.4f, 0.8f, m);
        Vector3f tr = LtcEvalf.transformDirection(m, new Vector3f(1, 2, 3), new Vector3f());
        assertEquals(1f, tr.x, 1E-6f);
        assertEquals(2f, tr.y, 1E-6f);
        assertEquals(3f, tr.z, 1E-6f);

        float ffRect = LtcEvalf.formFactorRect(
            new Vector3f(-1, -1, 1).normalize(),
            new Vector3f(1, -1, 1).normalize(),
            new Vector3f(1, 1, 1).normalize(),
            new Vector3f(-1, 1, 1).normalize());
        float ffDisc = LtcEvalf.formFactorDisc(0.7f, 0.3f);
        float ffTube = LtcEvalf.formFactorTube(0.7f, 0.2f, 0.4f);
        assertTrue(ffRect >= 0f && ffRect <= 1f);
        assertTrue(ffDisc >= 0f && ffDisc <= 1f);
        assertTrue(ffTube >= 0f && ffTube <= 1f);
    }

    @Test
    void bentNormalConeUtilitiesBehaveMonotonically() {
        float a0 = BentNormalConef.coneAngleFromAo(0f);
        float a1 = BentNormalConef.coneAngleFromAo(1f);
        assertTrue(a0 <= a1);
        assertEquals(0f, a0, 1E-6f);
        assertEquals((float) (java.lang.Math.PI * 0.5), a1, 1E-6f);

        float iSame = BentNormalConef.estimateIntersectionSolidAngle(
            0.5f, new Vector3f(0, 1, 0),
            0.5f, new Vector3f(0, 1, 0));
        float iOpp = BentNormalConef.estimateIntersectionSolidAngle(
            0.5f, new Vector3f(0, 1, 0),
            0.5f, new Vector3f(0, -1, 0));
        assertTrue(iSame >= iOpp);
    }
}
