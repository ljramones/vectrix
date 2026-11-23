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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import org.dynamisengine.vectrix.core.Math;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.sampling.BestCandidateSampling;
import org.dynamisengine.vectrix.sampling.Convolution;
import org.dynamisengine.vectrix.sampling.SpiralSampling;
import org.junit.jupiter.api.Test;

/** Second audit: edge-case contracts of core.Math min/max and the samplers (2.5.0). */
class ContractsAudit2Test {

    @Test
    void minMaxDoNotDependOnArgumentOrder() {
        assertTrue(Double.isNaN(Math.min(Double.NaN, 1.0)) && Double.isNaN(Math.min(1.0, Double.NaN)));
        assertTrue(Double.isNaN(Math.max(Double.NaN, 1.0)) && Double.isNaN(Math.max(1.0, Double.NaN)));
        assertTrue(Float.isNaN(Math.min(Float.NaN, 1f)) && Float.isNaN(Math.min(1f, Float.NaN)));
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(Math.min(0.0, -0.0)));
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(Math.min(-0.0, 0.0)));
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(Math.max(-0.0, 0.0)));
        Vector3d a = new Vector3d(Double.NaN, 1, -0.0).min(new Vector3d(1, Double.NaN, 0), new Vector3d());
        Vector3d b = new Vector3d(1, Double.NaN, 0).min(new Vector3d(Double.NaN, 1, -0.0), new Vector3d());
        assertTrue(Double.isNaN(a.x) && Double.isNaN(a.y) && Double.isNaN(b.x) && Double.isNaN(b.y), a + " " + b);
        assertEquals(Double.doubleToRawLongBits(a.z), Double.doubleToRawLongBits(b.z), "-0 handled the same in both orders");
    }

    @Test
    void samplersHandleDegenerateInputs() {
        // one spiral sample sits at the center (it was NaN)
        List<float[]> pts = new ArrayList<>();
        new SpiralSampling(1).createEquiAngle(2, 3, 1, (x, y) -> pts.add(new float[] {x, y}));
        assertEquals(1, pts.size());
        assertArrayEquals(new float[] {0, 0}, pts.get(0), 0f);
        // a zero-width Gaussian is the unit impulse (it was NaN everywhere)
        float[] k = new float[9];
        Convolution.gaussianKernel(3, 3, 0f, k);
        assertArrayEquals(new float[] {0, 0, 0, 0, 1, 0, 0, 0, 0}, k, 0f);
        FloatBuffer kb = FloatBuffer.allocate(9);
        Convolution.gaussianKernel(3, 3, 0f, kb);
        assertEquals(1f, kb.get(4), 0f);
        assertEquals(0f, kb.get(0), 0f);
        // at least one candidate (0 recursed until StackOverflowError)
        assertThrows(IllegalArgumentException.class, () -> new BestCandidateSampling.Disk().numCandidates(0));
        assertThrows(IllegalArgumentException.class, () -> new BestCandidateSampling.Sphere().numCandidates(0));
        // 2D samples are written with stride 2 and match the array form
        for (int kind = 0; kind < 2; kind++) {
            float[] arr = new float[8];
            FloatBuffer buf = FloatBuffer.allocate(8);
            if (kind == 0) {
                new BestCandidateSampling.Disk().seed(1).numSamples(4).numCandidates(8).generate(arr);
                new BestCandidateSampling.Disk().seed(1).numSamples(4).numCandidates(8).generate(buf);
            } else {
                new BestCandidateSampling.Quad().seed(1).numSamples(4).numCandidates(8).generate(arr);
                new BestCandidateSampling.Quad().seed(1).numSamples(4).numCandidates(8).generate(buf);
            }
            assertArrayEquals(arr, buf.array(), 0f, kind == 0 ? "Disk" : "Quad");
        }
    }
}
