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
import org.vectrix.color.ColorScienced;
import org.vectrix.color.ColorSciencef;
import org.vectrix.core.Vector3d;
import org.vectrix.core.Vector3f;
import org.vectrix.curve.CurveReparameterizer3d;
import org.vectrix.curve.CurveReparameterizer3f;
import org.vectrix.ltc.LtcEvald;
import org.vectrix.ltc.LtcTabled;
import org.vectrix.optics.SpectralSamplingd;
import org.vectrix.optics.SpectralSamplingf;
import org.vectrix.renderingmath.BentNormalConed;
import org.vectrix.renderingmath.BentNormalConef;
import org.vectrix.sh.ShBasis;
import org.vectrix.sh.ShCoeffs16f;
import org.vectrix.sh.ShConvolution;
import org.vectrix.sh.ShProjection;

class V2GapClosureTest {
    @Test
    void colorScienceAndSpectralSamplingBasics() {
        Vector3f xyz = ColorSciencef.cie1931XyzBar(555.0f, new Vector3f());
        assertTrue(xyz.x > 0.0f && xyz.y > 0.0f && xyz.z >= 0.0f);

        Vector3d rgb = ColorScienced.xyzToLinearSrgb(0.3, 0.4, 0.2, new Vector3d());
        Vector3d xyz2 = ColorScienced.linearSrgbToXyz(rgb.x, rgb.y, rgb.z, new Vector3d());
        assertEquals(0.3, xyz2.x, 2E-3);
        assertEquals(0.4, xyz2.y, 2E-3);
        assertEquals(0.2, xyz2.z, 2E-3);

        float wl = SpectralSamplingf.sampleStratified(3, 8, 0.5f);
        assertTrue(wl >= SpectralSamplingf.MIN_NM && wl <= SpectralSamplingf.MAX_NM);
        assertEquals(1.0 / (SpectralSamplingd.MAX_NM - SpectralSamplingd.MIN_NM), SpectralSamplingd.pdfUniform(), 1E-12);
    }

    @Test
    void shL3ProjectionAndEvaluationAreStable() {
        ShCoeffs16f sh = new ShCoeffs16f().zero();
        int n = 2048;
        float omega = (float) (4.0 * java.lang.Math.PI / n);
        Vector3f rgb = new Vector3f(0.5f, 0.25f, 0.75f);
        for (int i = 0; i < n; i++) {
            float z = 1.0f - 2.0f * (i + 0.5f) / n;
            float r = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - z * z));
            float phi = (float) (2.399963229728653 * i);
            float x = (float) java.lang.Math.cos(phi) * r;
            float y = (float) java.lang.Math.sin(phi) * r;
            ShProjection.accumulateSampleL3(new Vector3f(x, y, z), rgb, omega, sh);
        }
        Vector3f e = ShConvolution.evaluateL3(sh, new Vector3f(0, 1, 0), new Vector3f());
        assertTrue(Float.isFinite(e.x) && Float.isFinite(e.y) && Float.isFinite(e.z));

        float[] b = ShBasis.evaluateL3(0.2f, 0.8f, 0.4f, new float[16]);
        for (float v : b) {
            assertTrue(Float.isFinite(v));
        }
    }

    @Test
    void hermiteAndCatmullArcLengthTablesWork() {
        Vector3f p0 = new Vector3f(0, 0, 0);
        Vector3f p1 = new Vector3f(1, 1, 0);
        Vector3f m0 = new Vector3f(1, 0, 0);
        Vector3f m1 = new Vector3f(1, 0, 0);
        float[] h = CurveReparameterizer3f.buildArcLengthTableForHermite(p0, m0, p1, m1, 32, new float[33]);
        assertEquals(0.0f, h[0], 1E-6f);
        assertEquals(1.0f, h[32], 1E-6f);
        for (int i = 1; i < h.length; i++) assertTrue(h[i] >= h[i - 1]);

        double[] c = CurveReparameterizer3d.buildArcLengthTableForCatmullRom(
            new Vector3d(-1, 0, 0), new Vector3d(0, 0, 0), new Vector3d(1, 1, 0), new Vector3d(2, 1, 0),
            0.0, 32, new double[33]);
        assertEquals(0.0, c[0], 1E-12);
        assertEquals(1.0, c[32], 1E-12);
        for (int i = 1; i < c.length; i++) assertTrue(c[i] >= c[i - 1]);
    }

    @Test
    void ltcDoubleAndBentConeDoubleParity() {
        double[] table = new double[2 * 2 * 9];
        for (int t = 0; t < 4; t++) {
            int o = t * 9;
            table[o] = table[o + 4] = table[o + 8] = 1.0;
        }
        double[] m = new double[9];
        LtcTabled.sampleMat3(table, 2, 2, 0.5, 0.5, m);
        Vector3d tr = LtcEvald.transformDirection(m, new Vector3d(1, 2, 3), new Vector3d());
        assertEquals(1.0, tr.x, 1E-12);
        assertEquals(2.0, tr.y, 1E-12);
        assertEquals(3.0, tr.z, 1E-12);

        double ff = LtcEvald.formFactorRectClipped(
            new Vector3d(-1, -1, 1).normalize(),
            new Vector3d(1, -1, 1).normalize(),
            new Vector3d(1, 1, 1).normalize(),
            new Vector3d(-1, 1, 1).normalize());
        assertTrue(ff >= 0.0 && ff <= 1.0);

        double d = BentNormalConed.coneAngleFromAo(0.7);
        float f = BentNormalConef.coneAngleFromAo(0.7f);
        assertEquals((double) f, d, 1E-6);
    }
}
