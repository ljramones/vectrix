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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.color.ColorGamut;
import org.dynamisengine.vectrix.color.ColorScienced;
import org.dynamisengine.vectrix.color.ColorSpacesd;
import org.dynamisengine.vectrix.color.ColorSpacesf;
import org.dynamisengine.vectrix.color.Exposured;
import org.dynamisengine.vectrix.color.PerceptualColord;
import org.dynamisengine.vectrix.color.Tonemapd;
import org.dynamisengine.vectrix.color.Tonemapf;
import org.dynamisengine.vectrix.color.TransferFunctionsd;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for the colour additions (review §15.4), against published reference data where it exists: the
 * ACES and Rec.2020 matrices, Ottosson's OKLab table, Sharma et al.'s CIEDE2000 test pairs, the PQ and HLG
 * definitions, the tonemappers' defining fixed points, and Wien's and Stefan-Boltzmann's laws.
 */
class ColorTest {

    private static void assertMatrix(double[] expected, double[] actual, double tol, String what) {
        for (int i = 0; i < 9; i++) {
            assertEquals(expected[i], actual[i], tol, what + " [" + i + "]");
        }
    }

    @Test
    void gamutMatricesMatchThePublishedOnes() {
        double[] m = new double[9];
        // ACES (TB-2014-004): exact up to the published 7 digits
        assertMatrix(new double[] {0.6624542, 0.1340042, 0.1561877, 0.2722287, 0.6740818, 0.0536895, -0.0055746,
            0.0040607, 1.0103391}, ColorSpacesd.toXyzMatrix(ColorGamut.ACES_CG, m), 1e-7, "AP1");
        assertMatrix(new double[] {0.9525524, 0.0, 0.0000937, 0.3439664, 0.7281661, -0.0721325, 0.0, 0.0, 1.0088252},
                ColorSpacesd.toXyzMatrix(ColorGamut.ACES_2065_1, m), 1e-7, "AP0");
        // Rec.2020 and P3 as usually published use the xy D65 white; ours use ColorMath's XYZ white (≤ 3e-4 apart)
        assertMatrix(new double[] {0.6369580, 0.1446169, 0.1688810, 0.2627002, 0.6779981, 0.0593017, 0.0, 0.0280727,
            1.0609851}, ColorSpacesd.toXyzMatrix(ColorGamut.REC2020, m), 3e-4, "Rec.2020");
        assertMatrix(new double[] {0.4865709, 0.2656677, 0.1982173, 0.2289746, 0.6917385, 0.0792869, 0.0, 0.0451134,
            1.0439444}, ColorSpacesd.toXyzMatrix(ColorGamut.DISPLAY_P3, m), 3e-4, "P3");
        assertMatrix(new double[] {0.6130974, 0.3395231, 0.0473795, 0.0701937, 0.9163539, 0.0134524, 0.0206156,
            0.1095698, 0.8698147}, ColorSpacesd.conversionMatrix(ColorGamut.SRGB, ColorGamut.ACES_CG,
                ColorSpacesd.Adaptation.BRADFORD, m), 3e-4, "sRGB to ACEScg");
    }

    @Test
    void whiteStaysWhiteAndConversionsRoundTrip() {
        Vector3d w = new Vector3d();
        for (ColorGamut from : ColorGamut.values()) {
            for (ColorGamut to : ColorGamut.values()) {
                for (ColorSpacesd.Adaptation a : new ColorSpacesd.Adaptation[] {ColorSpacesd.Adaptation.BRADFORD,
                    ColorSpacesd.Adaptation.CAT16}) {
                    // the published 7-digit sRGB pair (shared with ColorMath) is inverse only to about 1e-7
                    double tol = from == ColorGamut.SRGB || to == ColorGamut.SRGB ? 1e-6 : 1e-9;
                    ColorSpacesd.convert(new Vector3d(1, 1, 1), from, to, a, w);
                    assertEquals(1, w.x, tol, from + " -> " + to + " " + a);
                    assertEquals(1, w.y, tol);
                    assertEquals(1, w.z, tol);
                    Vector3d c = new Vector3d(0.2, 0.55, 0.9);
                    ColorSpacesd.convert(ColorSpacesd.convert(c, from, to, a, new Vector3d()), to, from, a, w);
                    assertEquals(0.2, w.x, tol);
                    assertEquals(0.55, w.y, tol);
                    assertEquals(0.9, w.z, tol);
                }
            }
        }
        Vector3f f = ColorSpacesf.convert(new Vector3f(0.2f, 0.55f, 0.9f), ColorGamut.SRGB, ColorGamut.REC2020,
                new Vector3f());
        Vector3d d = ColorSpacesd.convert(new Vector3d(0.2, 0.55, 0.9), ColorGamut.SRGB, ColorGamut.REC2020, w);
        assertEquals(d.x, f.x, 1e-6);
        assertEquals(d.z, f.z, 1e-6);
    }

    @Test
    void transferFunctions() {
        assertEquals(1.0, TransferFunctionsd.pqEncodeNits(10000), 1e-12);
        assertEquals(0.508078, TransferFunctionsd.pqEncodeNits(100), 1e-6);
        assertTrue(TransferFunctionsd.pqEncode(0) < 1e-6);
        assertEquals(0.5, TransferFunctionsd.hlgOetf(1.0 / 12.0), 1e-12);
        assertEquals(1.0, TransferFunctionsd.hlgOetf(1.0), 1e-6);
        for (double y = 1e-4; y <= 1.0; y *= 1.7) {
            assertEquals(y, TransferFunctionsd.pqDecode(TransferFunctionsd.pqEncode(y)), 1e-9 * Math.max(1, y));
            assertEquals(y, TransferFunctionsd.hlgInverseOetf(TransferFunctionsd.hlgOetf(y)), 1e-9);
            assertEquals(y, TransferFunctionsd.rec709InverseOetf(TransferFunctionsd.rec709Oetf(y)), 1e-9);
            assertEquals(y, TransferFunctionsd.gamma22Decode(TransferFunctionsd.gamma22Encode(y)), 1e-12);
        }
    }

    @Test
    void oklabMatchesOttossonsTable() {
        double[][] xyz = {{0.950, 1.000, 1.089}, {1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        double[][] lab = {{1.000, 0.000, 0.000}, {0.450, 1.236, -0.019}, {0.922, -0.671, 0.263}, {0.153, -1.415, -0.449}};
        Vector3d o = new Vector3d();
        for (int i = 0; i < 4; i++) {
            PerceptualColord.oklabFromXyz(new Vector3d(xyz[i][0], xyz[i][1], xyz[i][2]), o);
            assertEquals(lab[i][0], o.x, 1e-3, "L " + i);
            assertEquals(lab[i][1], o.y, 1e-3, "a " + i);
            assertEquals(lab[i][2], o.z, 1e-3, "b " + i);
        }
        PerceptualColord.oklabFromLinearSrgb(new Vector3d(1, 1, 1), o);
        assertEquals(1.0, o.x, 1e-6);
        assertEquals(0.0, o.y, 1e-6);
        assertEquals(0.0, o.z, 1e-6);
        Vector3d c = new Vector3d(0.3, 0.7, 0.05);
        PerceptualColord.linearSrgbFromOklab(PerceptualColord.oklabFromLinearSrgb(c, new Vector3d()), o);
        assertEquals(c.x, o.x, 1e-6);
        assertEquals(c.y, o.y, 1e-6);
        assertEquals(c.z, o.z, 1e-6);
        Vector3d lch = PerceptualColord.labToLch(new Vector3d(0.5, -0.1, 0.1), new Vector3d());
        assertEquals(135.0, lch.z, 1e-9);
        Vector3d back = PerceptualColord.lchToLab(lch, new Vector3d());
        assertEquals(-0.1, back.y, 1e-12);
    }

    @Test
    void cielab() {
        Vector3d lab = PerceptualColord.cielabFromXyz(new Vector3d(0.95047, 1, 1.08883), new Vector3d());
        assertEquals(100, lab.x, 1e-9);
        assertEquals(0, lab.y, 1e-9);
        assertEquals(0, lab.z, 1e-9);
        // sRGB red (Lindbloom): L* 53.2408, a* 80.0925, b* 67.2032
        Vector3d red = org.dynamisengine.vectrix.color.ColorMathd.linearSrgbToXyz(new Vector3d(1, 0, 0), new Vector3d());
        PerceptualColord.cielabFromXyz(red, lab);
        assertEquals(53.2408, lab.x, 1e-3);
        assertEquals(80.0925, lab.y, 1e-3);
        assertEquals(67.2032, lab.z, 1e-3);
        Vector3d xyz = PerceptualColord.xyzFromCielab(lab, new Vector3d());
        assertEquals(red.x, xyz.x, 1e-12);
        assertEquals(red.z, xyz.z, 1e-12);
    }

    @Test
    void ciede2000MatchesSharmasTestData() {
        double[][] pairs = {
            {50.0000, 2.6772, -79.7751, 50.0000, 0.0000, -82.7485, 2.0425},
            {50.0000, 3.1571, -77.2803, 50.0000, 0.0000, -82.7485, 2.8615},
            {50.0000, 2.8361, -74.0200, 50.0000, 0.0000, -82.7485, 3.4412},
            {50.0000, -1.3802, -84.2814, 50.0000, 0.0000, -82.7485, 1.0000},
            {50.0000, -1.1848, -84.8006, 50.0000, 0.0000, -82.7485, 1.0000},
            {50.0000, -0.9009, -85.5211, 50.0000, 0.0000, -82.7485, 1.0000},
            {50.0000, 0.0000, 0.0000, 50.0000, -1.0000, 2.0000, 2.3669},
            {50.0000, 2.5000, 0.0000, 73.0000, 25.0000, -18.0000, 27.1492},
            {50.0000, 2.5000, 0.0000, 61.0000, -5.0000, 29.0000, 22.8977},
            {50.0000, 2.5000, 0.0000, 56.0000, -27.0000, -3.0000, 31.9030},
            {50.0000, 2.5000, 0.0000, 58.0000, 24.0000, 15.0000, 19.4535},
        };
        for (double[] p : pairs) {
            Vector3d a = new Vector3d(p[0], p[1], p[2]), b = new Vector3d(p[3], p[4], p[5]);
            assertEquals(p[6], PerceptualColord.deltaE2000(a, b), 1e-4, java.util.Arrays.toString(p));
            assertEquals(p[6], PerceptualColord.deltaE2000(b, a), 1e-4, "symmetric");
        }
    }

    @Test
    void hsvAndHsl() {
        Vector3d o = new Vector3d();
        PerceptualColord.rgbToHsv(new Vector3d(1, 0, 0), o);
        assertEquals(0, o.x, 0);
        assertEquals(1, o.y, 0);
        assertEquals(1, o.z, 0);
        assertEquals(120, PerceptualColord.rgbToHsv(new Vector3d(0, 1, 0), o).x, 1e-12);
        assertEquals(0.5, PerceptualColord.rgbToHsl(new Vector3d(1, 0, 0), o).z, 0);
        SplittableRandom rnd = new SplittableRandom(1);
        for (int i = 0; i < 1000; i++) {
            Vector3d c = new Vector3d(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble());
            Vector3d r1 = PerceptualColord.hsvToRgb(PerceptualColord.rgbToHsv(c, new Vector3d()), new Vector3d());
            Vector3d r2 = PerceptualColord.hslToRgb(PerceptualColord.rgbToHsl(c, new Vector3d()), new Vector3d());
            assertEquals(c.x, r1.x, 1e-12);
            assertEquals(c.y, r1.y, 1e-12);
            assertEquals(c.z, r1.z, 1e-12);
            assertEquals(c.x, r2.x, 1e-12);
            assertEquals(c.z, r2.z, 1e-12);
        }
    }

    @Test
    void tonemappers() {
        assertEquals(2.54 / 3.16, Tonemapd.acesNarkowicz(1.0), 1e-12);
        assertEquals(0.267, Tonemapd.lottes(0.18), 1e-9);
        assertEquals(1.0, Tonemapd.lottes(8.0), 1e-9);
        assertEquals(0.3, Tonemapd.uchimura(0.3), 1e-12); // inside the linear section
        assertEquals(1.0, Tonemapd.reinhardExtended(4.0, 4.0), 1e-12);
        Vector3d n = Tonemapd.khronosPbrNeutral(new Vector3d(0.1, 0.2, 0.3), new Vector3d());
        assertEquals(0.06, n.x, 1e-12); // below the compression start only the 0.04 offset applies
        assertEquals(0.26, n.z, 1e-12);
        // per-channel curves: in [0, 1], monotonic; vector curves keep grey neutral
        double prevA = -1, prevU = -1, prevL = -1, prevG = -1, prevH = -1, prevK = -1;
        Vector3d g = new Vector3d(), h = new Vector3d(), k = new Vector3d();
        for (double x = 0.0; x <= 64.0; x += 0.01) {
            double a = Tonemapd.acesNarkowicz(x), u = Tonemapd.uchimura(x), l = Tonemapd.lottes(x);
            Tonemapd.agx(new Vector3d(x), g);
            Tonemapd.acesHill(new Vector3d(x), h);
            Tonemapd.khronosPbrNeutral(new Vector3d(x), k);
            for (double v : new double[] {a, u, g.x, h.x, k.x}) {
                assertTrue(v >= -1e-9 && v <= 1 + 1e-9, "out of range " + v + " at " + x);
            }
            // AgX dips by ~3e-6 where its log clamp engages (see Tonemapd.agx)
            assertTrue(a >= prevA && u >= prevU - 1e-12 && l >= prevL && g.x >= prevG - 1e-5 && h.x >= prevH
                    && k.x >= prevK - 1e-12, "not monotonic at " + x);
            assertTrue(l <= 1.0, "Lottes above 1 at " + x);
            // the published AgX inset matrix's rows sum to 1.00014, 0.99996 and 0.99995, so grey is neutral to ~2e-4
            assertEquals(g.x, g.z, 1e-3 * Math.max(g.x, 1e-3), "AgX grey at " + x);
            assertEquals(h.x, h.y, 2e-3, "ACES grey at " + x);
            prevA = a;
            prevU = u;
            prevL = l;
            prevG = g.x;
            prevH = h.x;
            prevK = k.x;
        }
        // DynamisLightEngine's shader curve: color = vec3(1.0) - exp(-color * exposure)
        for (float x = 0; x < 8; x += 0.25f) {
            assertEquals(1f - (float) Math.exp(-x * 1.5f), Tonemapf.lightEngineExponential(x, 1.5f), 1e-6f);
        }
        assertEquals(1f - (float) Math.exp(-2f * 0.0001f), Tonemapf.lightEngineExponential(2f, 0f), 1e-7f);
    }

    @Test
    void exposure() {
        assertEquals(Math.log(25600) / Math.log(2), Exposured.ev100(16, 0.01, 100), 1e-12); // sunny 16: EV100 ≈ 14.64
        assertEquals(1.0 / 1.2, Exposured.exposureFromEv100(0), 1e-15);
        for (int ev = -4; ev <= 12; ev++) {
            assertEquals(ev, Exposured.ev100FromAverageLuminance(0.125 * Math.pow(2, ev)), 1e-12);
        }
        assertEquals(1.0, Exposured.radiometricFromPhotometric(Exposured.photometricFromRadiometric(1.0)), 1e-15);
    }

    @Test
    void planckRadianceObeysWienAndStefanBoltzmann() {
        double t = 5778;
        double best = 0, bestL = 0, total = 0;
        for (double l = 1; l <= 200_000; l += 0.05) {
            double b = ColorScienced.planckRadiance(l, t);
            total += b * 0.05;
            if (b > best) {
                best = b;
                bestL = l;
            }
        }
        assertEquals(2.897771955e-3 / t * 1e9, bestL, 0.05); // Wien: λmax T = b
        assertEquals(5.670374419e-8 * Math.pow(t, 4), Math.PI * total, 1e-4 * 5.670374419e-8 * Math.pow(t, 4));
    }
}
