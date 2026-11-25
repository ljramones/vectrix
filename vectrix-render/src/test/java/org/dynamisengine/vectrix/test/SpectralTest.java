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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.brdf.DfgLutBuilder;
import org.dynamisengine.vectrix.brdf.Microfacetd;
import org.dynamisengine.vectrix.color.CieObserver;
import org.dynamisengine.vectrix.color.ColorGamut;
import org.dynamisengine.vectrix.color.ColorScienced;
import org.dynamisengine.vectrix.color.SpectralUpsampling;
import org.dynamisengine.vectrix.color.Spectra;
import org.dynamisengine.vectrix.core.Vector3d;
import org.junit.jupiter.api.Test;

/** Official CIE tables against published values, spectral upsampling round trips, and the cloth DFG channel. */
class SpectralTest {

    @Test
    void cieTablesReproducePublishedValues() {
        Vector3d v = new Vector3d();
        assertEquals(1.0, CieObserver.CIE_1931_2DEG.xyzBar(555, v).y, 0.0); // ȳ peaks at exactly 1 at 555 nm
        assertEquals(100.0, Spectra.d65(560), 0.0); // D65 is normalized to 100 at 560 nm
        double mid = 0.5 * (CieObserver.CIE_1931_2DEG.at(600, 0) + CieObserver.CIE_1931_2DEG.at(601, 0));
        assertEquals(mid, CieObserver.CIE_1931_2DEG.xyzBar(600.5, v).x, 1e-16);
        assertEquals(0.0, CieObserver.CIE_1964_10DEG.xyzBar(700, v).z, 0.0); // z̄10 undefined -> 0 from 560 nm
        // D65 white points: CIE 15:2004 chromaticities (x, y), to their published 5 decimals
        Vector3d w2 = Spectra.whitePointD65(CieObserver.CIE_1931_2DEG, new Vector3d());
        Vector3d w10 = Spectra.whitePointD65(CieObserver.CIE_1964_10DEG, new Vector3d());
        double s2 = w2.x + w2.y + w2.z, s10 = w10.x + w10.y + w10.z;
        System.out.printf("D65 white from 1 nm tables: 2 deg XYZ (%.6f, 1, %.6f) xy (%.6f, %.6f); 10 deg xy (%.6f, %.6f)%n",
                w2.x, w2.z, w2.x / s2, w2.y / s2, w10.x / s10, w10.y / s10);
        // XYZ: the standard D65 white (0.95047, 1, 1.08883) in every published digit
        assertEquals(0.95047, w2.x, 5e-6);
        assertEquals(1.08883, w2.z, 5e-6);
        // chromaticities: published rounded to 5 decimals, and they depend on the summation interval, so ~1e-5
        assertEquals(0.31272, w2.x / s2, 1e-5);
        assertEquals(0.32903, w2.y / s2, 1e-5);
        assertEquals(0.31382, w10.x / s10, 1e-5);
        assertEquals(0.33100, w10.y / s10, 1e-5);
        // the perfect diffuser is the white point; black is zero
        double[] ones = new double[471];
        java.util.Arrays.fill(ones, 1.0);
        Vector3d white = Spectra.reflectanceToXyz(ones, 360, CieObserver.CIE_1931_2DEG, new Vector3d());
        assertTrue(white.distance(w2) < 1e-15);
        // how far the analytic Wyman et al. fit is from the standard
        double wx = 0, wy = 0, wz = 0;
        for (int nm = 380; nm <= 780; nm++) {
            Vector3d fit = ColorScienced.cie1931XyzBar(nm, new Vector3d());
            CieObserver.CIE_1931_2DEG.xyzBar(nm, v);
            wx = Math.max(wx, Math.abs(fit.x - v.x));
            wy = Math.max(wy, Math.abs(fit.y - v.y));
            wz = Math.max(wz, Math.abs(fit.z - v.z));
        }
        System.out.printf("analytic CMF fit: max abs error per component x %.4f y %.4f z %.4f%n", wx, wy, wz);
        assertTrue(Math.max(wx, Math.max(wy, wz)) < 0.025, "the documented bound of the analytic fit");
    }

    @Test
    void jakobHanikaUpsamplingRoundTrips() {
        SplittableRandom rnd = new SplittableRandom(1);
        double[] c = new double[3], rgb = new double[3];
        long t0 = System.nanoTime();
        int fits = 0;
        for (ColorGamut gamut : new ColorGamut[] {ColorGamut.SRGB, ColorGamut.DISPLAY_P3, ColorGamut.REC2020}) {
            int converged = 0, total = 0;
            for (int i = 0; i < 300; i++) {
                double r = rnd.nextDouble(0.02, 0.98), g = rnd.nextDouble(0.02, 0.98), b = rnd.nextDouble(0.02, 0.98);
                total++;
                fits++;
                if (SpectralUpsampling.fit(r, g, b, gamut, c)) {
                    converged++;
                    SpectralUpsampling.rgbOf(c, gamut, rgb);
                    // the nanometre-domain coefficients cancel (x ~ c0 λ² with λ ~ 600): about 1e-10 is their limit
                    assertEquals(r, rgb[0], 1e-9, gamut + " r");
                    assertEquals(g, rgb[1], 1e-9, gamut + " g");
                    assertEquals(b, rgb[2], 1e-9, gamut + " b");
                    for (int nm = 360; nm <= 830; nm += 10) {
                        double s = SpectralUpsampling.evaluate(c, nm);
                        assertTrue(s > 0 && s < 1, "bounded reflectance");
                    }
                }
            }
            System.out.printf("spectral upsampling %s: %d of %d random colours fitted exactly%n", gamut, converged, total);
            if (gamut != ColorGamut.REC2020) {
                // every sRGB and P3 colour is a reflectance colour; saturated Rec. 2020 colours can lie outside
                assertEquals(total, converged, gamut + " coverage");
            }
        }
        System.out.printf("spectral upsampling: %.0f us per fit%n", (System.nanoTime() - t0) / 1e3 / fits);
        // grey is a constant spectrum
        assertTrue(SpectralUpsampling.fit(0.3, 0.3, 0.3, ColorGamut.SRGB, c));
        for (int nm = 360; nm <= 830; nm += 47) {
            assertEquals(0.3, SpectralUpsampling.evaluate(c, nm), 1e-9);
        }
        assertThrows(IllegalArgumentException.class, () -> SpectralUpsampling.fit(0.5, 0.5, 0.5, ColorGamut.ACES_CG, c));
        // brighter than the perfect diffuser: no reflectance can do it, and the fit says so
        assertTrue(!SpectralUpsampling.fit(1.2, 1.2, 1.2, ColorGamut.SRGB, c));
    }

    /** ∫ D_Charlie V_Neubelt nDotL over the light hemisphere, by a fine midpoint rule in (cos θ_l, φ_l). */
    private static double clothReference(double nDotV, double roughness) {
        double alpha = roughness * roughness;
        double vx = Math.sqrt(1 - nDotV * nDotV), vz = nDotV;
        int nt = 2000, np = 1000;
        double sum = 0;
        for (int i = 0; i < nt; i++) {
            double lz = (i + 0.5) / nt;
            double ls = Math.sqrt(1 - lz * lz);
            for (int j = 0; j < np; j++) {
                double phi = 2 * Math.PI * (j + 0.5) / np;
                double lx = ls * Math.cos(phi), ly = ls * Math.sin(phi);
                double hx = vx + lx, hy = ly, hz = vz + lz;
                double hl = Math.sqrt(hx * hx + hy * hy + hz * hz);
                sum += Microfacetd.dCharlie(hz / hl, alpha) * Microfacetd.vNeubelt(nDotV, lz) * lz;
            }
        }
        return sum * (1.0 / nt) * (2 * Math.PI / np);
    }

    @Test
    void clothDfgChannel() {
        for (double[] p : new double[][] {{0.9, 0.3}, {0.5, 0.6}, {0.2, 0.9}, {0.7, 1.0}}) {
            double ref = clothReference(p[0], p[1]);
            double est = DfgLutBuilder.integrateCloth(p[0], p[1], 1 << 16);
            assertEquals(ref, est, 2e-3 * ref, "NoV " + p[0] + ", roughness " + p[1]);
        }
        float[] lut = new float[16 * 16];
        DfgLutBuilder.buildCloth(16, 1024, lut);
        assertEquals((float) DfgLutBuilder.integrateCloth(5.5 / 16, 9.5 / 16, 1024), lut[9 * 16 + 5], 0f);
        for (float v : lut) {
            assertTrue(v >= 0 && Float.isFinite(v)); // sheen is not energy-bounded: up to ~2.8 at grazing, low roughness
        }
    }
}
