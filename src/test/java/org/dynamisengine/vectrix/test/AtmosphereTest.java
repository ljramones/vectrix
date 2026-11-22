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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.atmosphere.Atmosphere;
import org.dynamisengine.vectrix.atmosphere.AtmosphereModel;
import org.junit.jupiter.api.Test;

/** Atmosphere geometry, optical depth, LUT mapping and multiple scattering against independent references. */
class AtmosphereTest {

    private static final AtmosphereModel EARTH = AtmosphereModel.earth();
    private static final MathContext MC = new MathContext(50);

    /** ∫ of the ozone tent over altitudes [h0, h1], exactly (piecewise linear). */
    private static double tentIntegral(double h0, double h1, double center, double half) {
        double sum = 0;
        double[] knots = {center - half, center, center + half};
        double a = h0;
        for (int i = 0; i <= 3; i++) {
            double b = i < 3 ? Math.min(Math.max(knots[i], h0), h1) : h1;
            if (b > a) {
                double fa = Math.max(0, 1 - Math.abs(a - center) / half), fb = Math.max(0, 1 - Math.abs(b - center) / half);
                sum += 0.5 * (fa + fb) * (b - a);
            }
            a = Math.max(a, b);
        }
        return sum;
    }

    @Test
    void verticalOpticalDepthIsExact() {
        for (double alt : new double[] {0, 0.001, 150, 9_999, 10_000, 17_000, 25_000, 39_999, 60_000, 99_000}) {
            double h0 = alt;
            double r = EARTH.bottomRadius() + h0, len = EARTH.topRadius() - r;
            h0 = r - EARTH.bottomRadius(); // the altitude actually represented (r rounds to 9.3e-10 m)
            double[] acc = Atmosphere.densityIntegrals(EARTH, r, 1.0, len, new double[3]);
            double hr = EARTH.rayleighScaleHeight(), hm = EARTH.mieScaleHeight();
            double rayleigh = hr * Math.exp(-h0 / hr) * -Math.expm1(-len / hr);
            double mie = hm * Math.exp(-h0 / hm) * -Math.expm1(-len / hm);
            double ozone = tentIntegral(h0, h0 + len, EARTH.ozoneCenterAltitude(), EARTH.ozoneHalfWidth());
            assertEquals(rayleigh, acc[0], 1e-13 * rayleigh, "Rayleigh from " + alt);
            assertEquals(mie, acc[1], 1e-13 * mie + 1e-9, "Mie from " + alt);
            assertEquals(ozone, acc[2], 1e-13 * Math.max(ozone, 1), "ozone from " + alt);
        }
    }

    @Test
    void groundGeometryAgainstBigDecimal() {
        double b = EARTH.bottomRadius();
        for (double h0 : new double[] {1e-3, 1, 1000}) {
            double r = b + h0;
            // a ray just below the horizon: it grazes the ground far away
            double muHorizon = -Math.sqrt(1 - (b / r) * (b / r));
            for (double mu : new double[] {muHorizon - 1e-9, muHorizon - 1e-6, -0.01, -0.5, -1}) {
                if (mu > muHorizon) {
                    assertFalse(Atmosphere.intersectsGround(EARTH, r, mu), "above the horizon at " + h0);
                    continue;
                }
                assertTrue(Atmosphere.intersectsGround(EARTH, r, mu));
                BigDecimal rr = new BigDecimal(r), m = new BigDecimal(mu), bb = new BigDecimal(b);
                // nearer root: -rμ - sqrt(r²μ² - r² + B²)
                BigDecimal disc = rr.multiply(rr).multiply(m.multiply(m)).subtract(rr.multiply(rr)).add(bb.multiply(bb));
                BigDecimal ref = rr.multiply(m).negate().subtract(disc.sqrt(MC));
                double d = Atmosphere.distanceToGround(EARTH, r, mu);
                assertEquals(ref.doubleValue(), d, 1e-13 * ref.doubleValue() + 1e-9, h0 + " " + mu);
                assertEquals(0.0, Atmosphere.altitudeAt(EARTH, r, mu, d), 1e-8, "lands on the ground");
            }
            assertFalse(Atmosphere.intersectsGround(EARTH, r, muHorizon + 1e-9));
        }
        // altitude along a near-tangent ray at its lowest point, against BigDecimal
        double r = b + 2.0, mu = -1e-3;
        double t = -r * mu; // closest approach to the center
        BigDecimal rr = new BigDecimal(r), tt = new BigDecimal(t), m = new BigDecimal(mu);
        BigDecimal r2 = tt.multiply(tt).add(rr.multiply(tt).multiply(m).multiply(BigDecimal.valueOf(2))).add(rr.multiply(rr));
        double ref = r2.sqrt(MC).subtract(new BigDecimal(b)).doubleValue();
        assertEquals(ref, Atmosphere.altitudeAt(EARTH, r, mu, t), 1e-9);
        double naive = Math.sqrt(t * t + 2 * r * mu * t + r * r) - b;
        System.out.printf("altitude at the tangent point: exact %.12f m, stable %.12f m, naive %.12f m%n", ref,
                Atmosphere.altitudeAt(EARTH, r, mu, t), naive);
    }

    @Test
    void slantOpticalDepthAgainstBruteForce() {
        double[] gx = {-0.9602898564975363, -0.7966664774136267, -0.5255324099163290, -0.1834346424956498,
            0.1834346424956498, 0.5255324099163290, 0.7966664774136267, 0.9602898564975363};
        double[] gw = {0.1012285362903763, 0.2223810344533745, 0.3137066458778873, 0.3626837833783620,
            0.3626837833783620, 0.3137066458778873, 0.2223810344533745, 0.1012285362903763};
        for (double[] c : new double[][] {{0, 0.0}, {0, 0.05}, {0, 0.5}, {2_000, -0.02}, {30_000, 0.1}, {12_000, -0.3}}) {
            double r = EARTH.bottomRadius() + c[0], mu = c[1];
            double len = Atmosphere.distanceToBoundary(EARTH, r, mu);
            double[] acc = Atmosphere.densityIntegrals(EARTH, r, mu, len, new double[3]);
            int panels = 200_000;
            double[] ref = new double[3];
            for (int p = 0; p < panels; p++) {
                double a = len * p / panels, bb = len * (p + 1) / panels, mid = 0.5 * (a + bb), hw = 0.5 * (bb - a);
                for (int i = 0; i < 8; i++) {
                    double h = Atmosphere.altitudeAt(EARTH, r, mu, mid + hw * gx[i]);
                    ref[0] += gw[i] * hw * Atmosphere.rayleighDensity(EARTH, h);
                    ref[1] += gw[i] * hw * Atmosphere.mieDensity(EARTH, h);
                    ref[2] += gw[i] * hw * Atmosphere.ozoneDensity(EARTH, h);
                }
            }
            for (int k = 0; k < 3; k++) {
                assertEquals(ref[k], acc[k], 1e-10 * Math.max(ref[k], 1), "component " + k + " at " + c[0] + ", " + c[1]);
            }
        }
    }

    @Test
    void transmittanceLutMapping() {
        int w = 256, h = 64;
        SplittableRandom rnd = new SplittableRandom(1);
        double[] uv = new double[2], rm = new double[2];
        for (int i = 0; i < 5000; i++) {
            double r = EARTH.bottomRadius() + rnd.nextDouble() * (EARTH.topRadius() - EARTH.bottomRadius());
            double b = EARTH.bottomRadius();
            double muH = -Math.sqrt(Math.max(0, 1 - (b / r) * (b / r)));
            double mu = muH + rnd.nextDouble() * (1 - muH);
            Atmosphere.transmittanceLutUv(EARTH, r, mu, w, h, uv);
            assertTrue(uv[0] >= 0.5 / w - 1e-12 && uv[0] <= 1 - 0.5 / w + 1e-12 && uv[1] >= 0.5 / h - 1e-12
                    && uv[1] <= 1 - 0.5 / h + 1e-12);
            Atmosphere.transmittanceLutRMu(EARTH, uv[0], uv[1], w, h, rm);
            assertEquals(r, rm[0], 1e-15 * r * 4);
            assertEquals(mu, rm[1], 1e-9, "mu round trip at r=" + r);
        }
        float[] lut = Atmosphere.transmittanceLut(EARTH, 16, 8, new float[16 * 8 * 3]);
        double[] t = new double[3];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                Atmosphere.transmittanceLutRMu(EARTH, (x + 0.5) / 16, (y + 0.5) / 8, 16, 8, rm);
                Atmosphere.transmittanceToTop(EARTH, rm[0], rm[1], t);
                assertEquals((float) t[2], lut[3 * (y * 16 + x) + 2], 0f);
                assertTrue(t[2] <= t[0], "blue is attenuated at least as much as red");
            }
        }
        // straight up from sea level: exp(-optical depth) with the analytic vertical integrals
        Atmosphere.transmittanceToTop(EARTH, EARTH.bottomRadius(), 1, t);
        double len = EARTH.topRadius() - EARTH.bottomRadius();
        double tau = EARTH.rayleighScattering(0) * 8000 * -Math.expm1(-len / 8000)
                + EARTH.mieExtinction(0) * 1200 * -Math.expm1(-len / 1200) + EARTH.ozoneAbsorption(0) * 15000;
        assertEquals(Math.exp(-tau), t[0], 1e-15);
    }

    @Test
    void multipleScattering() {
        double r = EARTH.bottomRadius() + 500;
        double[] a = Atmosphere.multipleScattering(EARTH, r, 0.6, 64, 40, new double[3]);
        double[] steps = Atmosphere.multipleScattering(EARTH, r, 0.6, 64, 160, new double[3]);
        double[] dirs = Atmosphere.multipleScattering(EARTH, r, 0.6, 1024, 40, new double[3]);
        System.out.printf("Psi_ms at 500 m, muSun 0.6: %s; steps x4: %s; directions x16: %s%n",
                java.util.Arrays.toString(a), java.util.Arrays.toString(steps), java.util.Arrays.toString(dirs));
        for (int c = 0; c < 3; c++) {
            assertTrue(a[c] > 0);
            assertEquals(steps[c], a[c], 2e-3 * steps[c], "ray-march convergence, channel " + c);
            assertEquals(dirs[c], a[c], 2e-2 * dirs[c], "direction convergence, channel " + c);
        }
        assertTrue(a[2] > a[0], "Rayleigh makes blue multiple scattering stronger");
        double[] night = Atmosphere.multipleScattering(EARTH, r, -0.5, 64, 40, new double[3]);
        assertTrue(night[2] < a[2] && night[2] >= 0);
        // no scattering and a black ground: nothing
        AtmosphereModel vacuum = new AtmosphereModel(6_360_000, 6_460_000, 8000, 1200, 0.8, 25_000, 15_000,
                new double[3], new double[3], new double[3], new double[3], new double[3]);
        double[] z = Atmosphere.multipleScattering(vacuum, r, 0.6, 64, 40, new double[3]);
        assertEquals(0.0, z[0] + z[1] + z[2], 0.0);
        // the LUT (sun transmittance from a table) agrees with exact integration
        int w = 8, h = 4;
        long t0 = System.nanoTime();
        float[] lut = Atmosphere.multipleScatteringLut(EARTH, w, h, 64, 20, new float[w * h * 3]);
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("multiple-scattering LUT %dx%d (64 directions, 20 steps): %.0f ms%n", w, h, ms);
        for (int y = 0; y < h; y += 3) {
            for (int x = 0; x < w; x += 3) {
                double rr = EARTH.bottomRadius() + (EARTH.topRadius() - EARTH.bottomRadius()) * (y + 0.5) / h;
                double mus = -1 + 2 * (x + 0.5) / w;
                double[] e = Atmosphere.multipleScattering(EARTH, rr, mus, 64, 20, new double[3]);
                for (int c = 0; c < 3; c++) {
                    assertEquals(e[c], lut[3 * (y * w + x) + c], 1e-3 * e[c] + 1e-12, "LUT texel " + x + "," + y);
                }
            }
        }
    }

    @Test
    void modelIsImmutableAndValidated() {
        double[] ray = EARTH.rayleighScattering();
        ray[0] = 99;
        assertEquals(5.802e-6, EARTH.rayleighScattering(0), 0);
        assertEquals(AtmosphereModel.earth(), EARTH);
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereModel(1, 0.5, 1, 1, 0, 1, 1, new double[3],
                new double[3], new double[3], new double[3], new double[3]));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereModel(1, 2, 1, 1, 0, 1, 1, new double[3],
                new double[] {1, 1, 1}, new double[3], new double[3], new double[3]));
    }
}
