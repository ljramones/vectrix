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

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.sh.ShBasis;
import org.dynamisengine.vectrix.sh.ShCoeffs16d;
import org.dynamisengine.vectrix.sh.ShCoeffs16f;
import org.dynamisengine.vectrix.sh.ShCoeffs9f;
import org.dynamisengine.vectrix.sh.ShConvolution;
import org.dynamisengine.vectrix.sh.ShProjection;
import org.junit.jupiter.api.Test;

/**
 * The SH basis formulas appear in straight-line form in several places (array fillers, allocation-free projection
 * and evaluation) for speed. Every entry point must agree bit for bit with the array-filler path, so the copies
 * cannot drift apart.
 */
class ShConsistencyTest {

    @Test
    void allSphericalHarmonicPathsAgreeExactly() {
        SplittableRandom rnd = new SplittableRandom(9);
        for (int t = 0; t < 200; t++) {
            Vector3f d = new Vector3f((float) rnd.nextDouble(-1, 1), (float) rnd.nextDouble(-1, 1), (float) rnd.nextDouble(-1, 1)).normalize();
            Vector3f rgb = new Vector3f((float) rnd.nextDouble(), (float) rnd.nextDouble(), (float) rnd.nextDouble());
            float w = (float) rnd.nextDouble(0.001, 0.1);

            ShCoeffs9f viaVector = new ShCoeffs9f(), viaScratch = new ShCoeffs9f();
            ShProjection.accumulateSampleL2(d, rgb, w, viaVector);
            ShProjection.projectSample(d.x, d.y, d.z, rgb.x, rgb.y, rgb.z, w, new float[9], viaScratch);
            assertArrayEquals(viaScratch.c, viaVector.c, 0f, "L2 projection");

            ShCoeffs16f p16 = new ShCoeffs16f(), s16 = new ShCoeffs16f();
            ShProjection.accumulateSampleL3(d, rgb, w, p16);
            ShProjection.projectSampleL3(d.x, d.y, d.z, rgb.x, rgb.y, rgb.z, w, new float[16], s16);
            assertArrayEquals(s16.c, p16.c, 0f, "L3 projection");

            float[] out = new float[3];
            ShConvolution.evaluateL2(viaScratch, d.x, d.y, d.z, new float[9], out);
            Vector3f e = ShConvolution.evaluateL2(viaScratch, d, new Vector3f());
            assertEquals(out[0], e.x, 0f);
            assertEquals(out[1], e.y, 0f);
            assertEquals(out[2], e.z, 0f);

            float[] b16 = ShBasis.evaluateL3(d.x, d.y, d.z, new float[16]);
            float[] b9 = ShBasis.evaluateL2(d.x, d.y, d.z, new float[9]);
            for (int i = 0; i < 9; i++) {
                assertEquals(b9[i], b16[i], 0f, "L2 prefix of L3, index " + i);
            }
            float r = 0, g = 0, b = 0;
            for (int i = 0; i < 16; i++) {
                r += p16.c[3 * i] * b16[i];
                g += p16.c[3 * i + 1] * b16[i];
                b += p16.c[3 * i + 2] * b16[i];
            }
            Vector3f e3 = ShConvolution.evaluateL3(p16, d, new Vector3f());
            assertEquals(r, e3.x, 0f);
            assertEquals(g, e3.y, 0f);
            assertEquals(b, e3.z, 0f);

            Vector3d dd = new Vector3d(d), rgbd = new Vector3d(rgb);
            ShCoeffs16d pd = new ShCoeffs16d(), sd = new ShCoeffs16d();
            ShProjection.accumulateSampleL3(dd, rgbd, w, pd);
            ShProjection.projectSampleL3(dd.x, dd.y, dd.z, rgbd.x, rgbd.y, rgbd.z, w, new double[16], sd);
            assertArrayEquals(sd.c, pd.c, 0.0, "L3 projection (d)");
        }
    }
}
