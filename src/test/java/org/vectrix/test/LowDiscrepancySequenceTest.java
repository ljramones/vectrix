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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.vectrix.core.Vector2f;
import org.vectrix.sampling.HaltonSequence;
import org.vectrix.sampling.SobolSequence;

class LowDiscrepancySequenceTest {
    private static final float EPS_F = 1E-6f;
    private static final double EPS_D = 1E-12;

    @Test
    void haltonHasKnownReferenceValues() {
        float[] base2 = {0f, 0.5f, 0.25f, 0.75f, 0.125f, 0.625f, 0.375f, 0.875f};
        float[] base3 = {0f, 1f / 3f, 2f / 3f, 1f / 9f, 4f / 9f, 7f / 9f, 2f / 9f, 5f / 9f};
        for (int i = 0; i < base2.length; i++) {
            assertEquals(base2[i], HaltonSequence.halton(i, 2), EPS_F);
            assertEquals(base3[i], HaltonSequence.halton(i, 3), EPS_F);
        }
    }

    @Test
    void sobol2DHasKnownReferenceValues() {
        float[] x = {0f, 0.5f, 0.75f, 0.25f, 0.375f, 0.875f, 0.625f, 0.125f};
        float[] y = {0f, 0.5f, 0.25f, 0.75f, 0.375f, 0.875f, 0.125f, 0.625f};
        Vector2f p = new Vector2f();
        for (int i = 0; i < x.length; i++) {
            SobolSequence.sobol2D(i, p);
            assertEquals(x[i], p.x, EPS_F);
            assertEquals(y[i], p.y, EPS_F);
        }
    }

    @Test
    void sequencesAreDeterministic() {
        assertEquals(HaltonSequence.halton(23, 5), HaltonSequence.halton(23, 5), EPS_F);
        assertEquals(SobolSequence.sobol(129, 2), SobolSequence.sobol(129, 2), EPS_F);
    }

    @Test
    void batchMatchesIndividualWithOffsets() {
        int start = 5;
        int count = 16;
        int offset = 3;
        float[] hx = new float[offset + count + 2];
        float[] hy = new float[offset + count + 2];
        float[] sx = new float[offset + count + 2];
        float[] sy = new float[offset + count + 2];

        HaltonSequence.haltonBatch2D(start, count, hx, offset, hy, offset);
        SobolSequence.sobolBatch2D(start, count, sx, offset, sy, offset);
        for (int i = 0; i < count; i++) {
            int idx = start + i;
            assertEquals(HaltonSequence.halton(idx, 2), hx[offset + i], EPS_F);
            assertEquals(HaltonSequence.halton(idx, 3), hy[offset + i], EPS_F);
            assertEquals(SobolSequence.sobol(idx, 0), sx[offset + i], EPS_F);
            assertEquals(SobolSequence.sobol(idx, 1), sy[offset + i], EPS_F);
        }
    }

    @Test
    void samplesStayInUnitIntervalAndFillDomain() {
        int n = 1024;
        double sumHx = 0.0;
        double sumHy = 0.0;
        double sumSx = 0.0;
        double sumSy = 0.0;
        for (int i = 0; i < n; i++) {
            float hx = HaltonSequence.halton(i, 2);
            float hy = HaltonSequence.halton(i, 3);
            float sx = SobolSequence.sobol(i, 0);
            float sy = SobolSequence.sobol(i, 1);
            assertTrue(hx >= 0f && hx < 1f);
            assertTrue(hy >= 0f && hy < 1f);
            assertTrue(sx >= 0f && sx < 1f);
            assertTrue(sy >= 0f && sy < 1f);
            sumHx += hx;
            sumHy += hy;
            sumSx += sx;
            sumSy += sy;
        }
        assertEquals(0.5, sumHx / n, 2E-2);
        assertEquals(0.5, sumHy / n, 2E-2);
        assertEquals(0.5, sumSx / n, 2E-2);
        assertEquals(0.5, sumSy / n, 2E-2);
    }

    @Test
    void doubleParityMatchesFloat() {
        for (int i = 0; i < 64; i++) {
            assertEquals((double) HaltonSequence.halton(i, 2), HaltonSequence.haltonDouble(i, 2), EPS_D);
            assertEquals((double) SobolSequence.sobol(i, 3), SobolSequence.sobolDouble(i, 3), EPS_D);
            assertEquals((double) SobolSequence.sobolScrambled(i, 2, 12345), SobolSequence.sobolScrambledDouble(i, 2, 12345), 1E-7);
        }
    }

    @Test
    void scrambledSobolDiffersAndIsDeterministic() {
        int seed = 0x1234ABCD;
        for (int i = 0; i < 64; i++) {
            float a = SobolSequence.sobolScrambled(i, 0, seed);
            float b = SobolSequence.sobolScrambled(i, 0, seed);
            assertEquals(a, b, EPS_F);
        }
        float uns = SobolSequence.sobol(17, 1);
        float scr = SobolSequence.sobolScrambled(17, 1, seed);
        assertNotEquals(uns, scr);
    }

    @Test
    void scrambledBatchMatchesIndividualAndHasReasonableMean() {
        int start = 11;
        int count = 256;
        int seed = 424242;
        int off = 5;
        float[] sx = new float[off + count + 1];
        float[] sy = new float[off + count + 1];
        SobolSequence.sobolScrambledBatch2D(start, count, seed, sx, off, sy, off);

        double meanX = 0.0;
        double meanY = 0.0;
        for (int i = 0; i < count; i++) {
            int idx = start + i;
            float ix = SobolSequence.sobolScrambled(idx, 0, seed);
            float iy = SobolSequence.sobolScrambled(idx, 1, seed);
            assertEquals(ix, sx[off + i], EPS_F);
            assertEquals(iy, sy[off + i], EPS_F);
            assertTrue(ix >= 0.0f && ix < 1.0f);
            assertTrue(iy >= 0.0f && iy < 1.0f);
            meanX += ix;
            meanY += iy;
        }
        assertEquals(0.5, meanX / count, 4E-2);
        assertEquals(0.5, meanY / count, 4E-2);
    }
}
