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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.affine.PackedAffineKernels;
import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.color.ColorScienced;
import org.dynamisengine.vectrix.color.ColorSciencef;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;
import org.dynamisengine.vectrix.gpu.GpuTransformWriteKernels;
import org.dynamisengine.vectrix.ltc.LtcEvald;
import org.dynamisengine.vectrix.ltc.LtcEvalf;
import org.dynamisengine.vectrix.renderingmath.SssLutBuilder;
import org.dynamisengine.vectrix.renderingmath.SssProfile;
import org.junit.jupiter.api.Test;

/**
 * Reference-value tests for review findings H-2 (packed-affine to mat4), H-4 (CIE colour matching functions),
 * H-5/M-5 (LTC form factors and horizon clipping) and M-4 (pre-integrated SSS LUT normalisation). The
 * references are independent: JOML matrix construction, tabulated CIE 1931 data, and stratified Monte-Carlo
 * integration of the cosine-weighted solid angle.
 */
class RenderingMathReferenceTest {

    // ---------------------------------------------------------------- H-2: packed affine -> mat4

    @Test
    void packedAffineExpandsToTheSameMatrixAsJoml() {
        Transformf[] t = new Transformf[3];
        t[0] = new Transformf(new Vector3f(1, 2, 3), new Quaternionf().rotationAxis(0.7f, 0.3f, 0.9f, -0.2f).normalize(),
                new Vector3f(1, 1, 1));
        t[1] = new Transformf(new Vector3f(-4, 0.5f, 9), new Quaternionf().rotationXYZ(1.1f, -0.4f, 2.3f),
                new Vector3f(2, 0.5f, 3));
        t[2] = new Transformf();
        PackedAffineArray packed = new PackedAffineArray(3);
        PackedAffineKernels.trsToPackedAffineBatch(t, packed, 3);
        float[] out = new float[48];
        GpuTransformWriteKernels.writeMatrix4fFromPackedAffine(packed, new int[] {0, 1, 2}, out, 0, 3);
        for (int i = 0; i < 3; i++) {
            float[] expected = new Matrix4f().translationRotateScale(t[i].translation, t[i].rotation, t[i].scale)
                    .get(new float[16]);
            float[] actual = new float[16];
            System.arraycopy(out, i * 16, actual, 0, 16);
            assertArrayEquals(expected, actual, 1e-5f, "transform " + i);
        }
        // The origin must map to the translation (it mapped to 0 before the fix).
        Matrix4f m = new Matrix4f().set(java.util.Arrays.copyOfRange(out, 0, 16));
        Vector3f origin = m.transformPosition(new Vector3f());
        assertEquals(new Vector3f(1, 2, 3).distance(origin), 0.0f, 1e-5f);
    }

    // ---------------------------------------------------------------- H-4: CIE 1931 colour matching functions

    /** CIE 1931 2-degree standard observer, {nm, xBar, yBar, zBar}. */
    private static final double[][] CIE_1931 = {
        {420, 0.1344, 0.0040, 0.6456}, {440, 0.3483, 0.0230, 1.7471}, {450, 0.3362, 0.0380, 1.7721},
        {500, 0.0049, 0.3230, 0.2720}, {530, 0.1655, 0.8620, 0.0422}, {550, 0.4334, 0.9950, 0.0087},
        {555, 0.5121, 1.0000, 0.0058}, {600, 1.0622, 0.6310, 0.0008}, {650, 0.2835, 0.1070, 0.0000},
        {700, 0.0114, 0.0041, 0.0000},
    };

    @Test
    void cmfMatchesTabulatedCie1931() {
        for (double[] row : CIE_1931) {
            Vector3f f = ColorSciencef.cie1931XyzBar((float) row[0], new Vector3f());
            Vector3d d = ColorScienced.cie1931XyzBar(row[0], new Vector3d());
            String at = row[0] + " nm";
            assertEquals(row[1], f.x, 0.02, "xBar " + at);
            assertEquals(row[2], f.y, 0.02, "yBar " + at);
            assertEquals(row[3], f.z, 0.02, "zBar " + at);
            assertEquals(d.x, f.x, 1e-5, "f/d parity xBar " + at);
            assertEquals(d.y, f.y, 1e-5, "f/d parity yBar " + at);
            assertEquals(d.z, f.z, 1e-5, "f/d parity zBar " + at);
        }
    }

    // ---------------------------------------------------------------- H-5 / M-5: LTC form factors

    private interface Region {
        boolean contains(double x, double y, double z);
    }

    /** (1/pi) * integral over the upper hemisphere of cos(theta) * [dir in region], by stratified sampling. */
    private static double monteCarloFormFactor(Region region) {
        int n = 1400;
        long inside = 0;
        for (int i = 0; i < n; i++) {
            double u1 = (i + 0.5) / n;
            double r = Math.sqrt(u1), z = Math.sqrt(1 - u1);
            for (int j = 0; j < n; j++) {
                double phi = 2 * Math.PI * (j + 0.5) / n;
                if (region.contains(r * Math.cos(phi), r * Math.sin(phi), z)) {
                    inside++;
                }
            }
        }
        return (double) inside / ((double) n * n);
    }

    private static Region cap(double cosTheta, double alpha) {
        double sinTheta = Math.sqrt(1 - cosTheta * cosTheta), cosAlpha = Math.cos(alpha);
        return (x, y, z) -> x * sinTheta + z * cosTheta >= cosAlpha;
    }

    /** Convex spherical polygon with counter-clockwise (front-facing) vertices. */
    private static Region polygon(double[][] v) {
        return (x, y, z) -> {
            for (int i = 0; i < v.length; i++) {
                double[] a = v[i], b = v[(i + 1) % v.length];
                double nx = a[1] * b[2] - a[2] * b[1], ny = a[2] * b[0] - a[0] * b[2], nz = a[0] * b[1] - a[1] * b[0];
                if (nx * x + ny * y + nz * z < 0) {
                    return false;
                }
            }
            return true;
        };
    }

    private static double[] unit(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[] {x / l, y / l, z / l};
    }

    @Test
    void discFormFactorMatchesIntegrationIncludingHorizonCrossing() {
        double[][] cases = { // {cosTheta, alpha}
            {1.0, 0.11}, {1.0, 0.6}, {1.0, Math.PI / 2}, {0.8, 0.3}, {0.3, 0.5}, {0.1, 0.4}, {0.0, 0.3}, {-0.2, 0.5},
            {-0.8, 0.3},
        };
        for (double[] c : cases) {
            double ref = monteCarloFormFactor(cap(c[0], c[1]));
            float f = LtcEvalf.formFactorDisc((float) c[0], (float) c[1]);
            double d = LtcEvald.formFactorDisc(c[0], c[1]);
            String at = "cos=" + c[0] + " alpha=" + c[1];
            assertEquals(ref, d, 1.5e-3 + 0.01 * ref, at);
            assertEquals(d, f, 1e-5, "f/d parity " + at);
        }
        // Fully visible cap: exactly cos(theta) * sin^2(alpha). The old code returned half of this.
        assertEquals(0.8 * Math.pow(Math.sin(0.3), 2), LtcEvald.formFactorDisc(0.8, 0.3), 1e-12);
    }

    @Test
    void tubeFormFactorMatchesEquivalentSmallRectangle() {
        float halfW = 0.02f, halfL = 0.05f;
        Vector3fc[] rect = {
            new Vector3f(-halfL, -halfW, 1).normalize(), new Vector3f(halfL, -halfW, 1).normalize(),
            new Vector3f(halfL, halfW, 1).normalize(), new Vector3f(-halfL, halfW, 1).normalize(),
        };
        float rectFf = LtcEvalf.formFactorRect(rect[0], rect[1], rect[2], rect[3]);
        float tube = LtcEvalf.formFactorTube(1.0f, halfW, halfL);
        assertEquals(rectFf, tube, 0.02f * rectFf); // was half the rectangle value
    }

    @Test
    void clippedPolygonMatchesIntegrationOfTheVisiblePart() {
        double[][][] polys = {
            {unit(0.6, -0.3, 0.4), unit(0.7, 0.4, -0.3), unit(0.2, 0.2, 0.9)}, // triangle crossing the horizon
            {unit(-0.5, -0.5, -0.2), unit(0.5, -0.5, 0.6), unit(0.4, 0.6, 0.3), unit(-0.6, 0.4, -0.4)}, // quad crossing
            {unit(0.3, -0.3, 0.9), unit(0.3, 0.3, 0.9), unit(-0.3, 0.3, 0.9), unit(-0.3, -0.3, 0.9)}, // fully above
        };
        for (int k = 0; k < polys.length; k++) {
            double[][] p = polys[k];
            if (!isCounterClockwise(p)) {
                p = reverse(p);
            }
            double ref = monteCarloFormFactor(polygon(p));
            Vector3fc[] vf = new Vector3fc[p.length];
            Vector3dc[] vd = new Vector3dc[p.length];
            for (int i = 0; i < p.length; i++) {
                vf[i] = new Vector3f((float) p[i][0], (float) p[i][1], (float) p[i][2]);
                vd[i] = new Vector3d(p[i][0], p[i][1], p[i][2]);
            }
            double d = LtcEvald.formFactorPolygonClipped(vd, p.length);
            float f = LtcEvalf.formFactorPolygonClipped(vf, p.length);
            assertEquals(ref, d, 1.5e-3 + 0.01 * ref, "polygon " + k);
            assertEquals(d, f, 2e-5, "f/d parity polygon " + k);
            // one-sided: reversed winding is the back face
            assertEquals(0.0, LtcEvald.formFactorPolygonClipped(reverseD(vd), p.length), 0.0, "back face " + k);
        }
    }

    @Test
    void polygonEntirelyBelowHorizonIsZero() {
        Vector3fc[] v = {new Vector3f(1, 0, -0.1f).normalize(), new Vector3f(0, 1, -0.1f).normalize(),
            new Vector3f(-1, 0, -0.1f).normalize()};
        assertEquals(0.0f, LtcEvalf.formFactorPolygonClipped(v, 3));
    }

    private static boolean isCounterClockwise(double[][] p) {
        // Sum of (v_i x v_i+1) . centroid > 0 means counter-clockwise as seen from the origin.
        double cx = 0, cy = 0, cz = 0, s = 0;
        for (double[] a : p) {
            cx += a[0]; cy += a[1]; cz += a[2];
        }
        for (int i = 0; i < p.length; i++) {
            double[] a = p[i], b = p[(i + 1) % p.length];
            s += (a[1] * b[2] - a[2] * b[1]) * cx + (a[2] * b[0] - a[0] * b[2]) * cy + (a[0] * b[1] - a[1] * b[0]) * cz;
        }
        return s > 0;
    }

    private static double[][] reverse(double[][] p) {
        double[][] r = new double[p.length][];
        for (int i = 0; i < p.length; i++) {
            r[i] = p[p.length - 1 - i];
        }
        return r;
    }

    private static Vector3dc[] reverseD(Vector3dc[] p) {
        Vector3dc[] r = new Vector3dc[p.length];
        for (int i = 0; i < p.length; i++) {
            r[i] = p[p.length - 1 - i];
        }
        return r;
    }

    // ---------------------------------------------------------------- M-4: pre-integrated SSS LUT

    @Test
    void flatSurfaceReproducesNdotLForAnyProfileWidth() {
        int w = 32, h = 4;
        for (float variance : new float[] {0.25f, 1.0f, 4.0f}) {
            SssProfile profile = SssProfile.singleGaussian(1, 1, 1, variance, variance * 0.5f, variance * 2);
            float[] lut = new float[w * h * 3];
            SssLutBuilder.build(w, h, profile, 1e-4f, 3.0f * (float) Math.sqrt(variance * 2), 256, lut);
            for (int x = 0; x < w; x++) {
                float ndotl = (x + 0.5f) / w;
                for (int c = 0; c < 3; c++) {
                    assertEquals(ndotl, lut[x * 3 + c], 2e-3f, "variance " + variance + " x " + x + " ch " + c);
                }
            }
        }
    }

    @Test
    void curvedSurfaceWrapsLightAndStaysBounded() {
        int w = 16, h = 8;
        SssProfile profile = SssProfile.singleGaussian(0.6f, 0.3f, 0.1f, 1.0f, 0.4f, 0.1f);
        float[] lut = new float[w * h * 3];
        SssLutBuilder.build(w, h, profile, 1.0f, 3.0f, 256, lut);
        for (float v : lut) {
            assertTrue(v >= 0.0f && v <= 1.0f + 1e-5f, "out of range " + v);
        }
        // Light wraps into the terminator on curved skin: grazing NdotL is brighter than on a flat surface.
        int x = 0, yCurved = h - 1;
        assertTrue(lut[(yCurved * w + x) * 3] > (x + 0.5f) / w, "no wrap at the terminator");
    }
}
