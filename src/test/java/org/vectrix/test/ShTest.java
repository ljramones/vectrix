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
import org.vectrix.sh.ShBasis;
import org.vectrix.sh.ShCoeffs9f;
import org.vectrix.sh.ShConvolution;
import org.vectrix.sh.ShProjection;

class ShTest {
    @Test
    void basisYUpHasExpectedL1AtUpDirection() {
        float[] b = ShBasis.evaluateL2(0.0f, 1.0f, 0.0f, new float[9]);
        assertEquals(0.2820948f, b[0], 1E-6f);
        assertEquals(0.48860252f, b[1], 1E-6f);
        assertEquals(0.0f, b[2], 1E-6f);
        assertEquals(0.0f, b[3], 1E-6f);
    }

    @Test
    void projectingConstantRadianceProducesDominantL0() {
        ShCoeffs9f sh = new ShCoeffs9f().zero();
        int n = 2048;
        float omega = (float) (4.0 * java.lang.Math.PI / n);
        Vector3f rgb = new Vector3f(0.8f, 0.4f, 0.2f);
        for (int i = 0; i < n; i++) {
            float z = 1.0f - 2.0f * (i + 0.5f) / n;
            float r = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - z * z));
            float phi = (float) (2.399963229728653 * i);
            float x = (float) java.lang.Math.cos(phi) * r;
            float y = (float) java.lang.Math.sin(phi) * r;
            ShProjection.accumulateSampleL2(new Vector3f(x, y, z), rgb, omega, sh);
        }
        float c0r = sh.c[0];
        assertTrue(java.lang.Math.abs(c0r) > 1E-3f);
        for (int i = 1; i < 9; i++) {
            assertTrue(java.lang.Math.abs(sh.c[i * 3]) < java.lang.Math.abs(c0r) * 0.1f);
        }
    }

    @Test
    void lambertConvolutionOfConstantFieldYieldsPiTimesRadiance() {
        ShCoeffs9f sh = new ShCoeffs9f().zero();
        int n = 4096;
        float omega = (float) (4.0 * java.lang.Math.PI / n);
        Vector3f rgb = new Vector3f(0.5f, 0.25f, 0.75f);
        for (int i = 0; i < n; i++) {
            float z = 1.0f - 2.0f * (i + 0.5f) / n;
            float r = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - z * z));
            float phi = (float) (2.399963229728653 * i);
            float x = (float) java.lang.Math.cos(phi) * r;
            float y = (float) java.lang.Math.sin(phi) * r;
            ShProjection.accumulateSampleL2(new Vector3f(x, y, z), rgb, omega, sh);
        }
        ShCoeffs9f irr = ShConvolution.convolveLambertL2(sh, new ShCoeffs9f());
        Vector3f e = ShConvolution.evaluateL2(irr, new Vector3f(0, 1, 0), new Vector3f());
        assertEquals((float) java.lang.Math.PI * rgb.x, e.x, 2E-2f);
        assertEquals((float) java.lang.Math.PI * rgb.y, e.y, 2E-2f);
        assertEquals((float) java.lang.Math.PI * rgb.z, e.z, 2E-2f);
    }

    @Test
    void zeroAllocationProjectionMatchesConvenienceProjection() {
        ShCoeffs9f a = new ShCoeffs9f().zero();
        ShCoeffs9f b = new ShCoeffs9f().zero();
        Vector3f dir = new Vector3f(0.2f, 0.9f, -0.1f).normalize();
        Vector3f rgb = new Vector3f(0.7f, 0.3f, 0.9f);
        float solidAngle = 0.0125f;

        ShProjection.accumulateSampleL2(dir, rgb, solidAngle, a);
        ShProjection.projectSample(dir.x, dir.y, dir.z, rgb.x, rgb.y, rgb.z, solidAngle, new float[9], b);

        for (int i = 0; i < ShCoeffs9f.SIZE; i++) {
            assertEquals(a.c[i], b.c[i], 1E-7f);
        }
    }

    @Test
    void zeroAllocationIrradianceMatchesConvenienceEvaluation() {
        ShCoeffs9f sh = new ShCoeffs9f().zero();
        ShProjection.projectSample(0.0f, 1.0f, 0.0f, 0.8f, 0.4f, 0.2f, 1.0f, new float[9], sh);
        ShProjection.projectSample(1.0f, 0.0f, 0.0f, 0.1f, 0.2f, 0.3f, 0.5f, new float[9], sh);
        Vector3f n = new Vector3f(0.3f, 0.8f, 0.4f).normalize();

        Vector3f expected = ShConvolution.evaluateL2(sh, n, new Vector3f());
        float[] out = new float[3];
        ShConvolution.evaluateIrradiance(sh, n.x, n.y, n.z, new float[9], out);

        assertEquals(expected.x, out[0], 1E-7f);
        assertEquals(expected.y, out[1], 1E-7f);
        assertEquals(expected.z, out[2], 1E-7f);
    }
}
