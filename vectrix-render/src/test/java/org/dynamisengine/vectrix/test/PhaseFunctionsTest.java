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

import java.util.function.DoubleUnaryOperator;

import org.dynamisengine.vectrix.atmosphere.PhaseFunctionsd;
import org.dynamisengine.vectrix.atmosphere.PhaseFunctionsf;
import org.junit.jupiter.api.Test;

/** Phase functions: normalization, mean cosines, and exact sampling (CDF of the sample returns ξ). */
class PhaseFunctionsTest {

    private static final double[] GX = {-0.9602898564975363, -0.7966664774136267, -0.5255324099163290,
        -0.1834346424956498, 0.1834346424956498, 0.5255324099163290, 0.7966664774136267, 0.9602898564975363};
    private static final double[] GW = {0.1012285362903763, 0.2223810344533745, 0.3137066458778873,
        0.3626837833783620, 0.3626837833783620, 0.3137066458778873, 0.2223810344533745, 0.1012285362903763};

    /** 2π ∫_{a}^{b} p(u) du, with panels graded toward u = 1 where forward peaks live. */
    static double integrate(DoubleUnaryOperator p, double a, double b) {
        int panels = 4000;
        double sum = 0;
        for (int k = 0; k < panels; k++) {
            // graded panel edges: dense near b
            double t0 = (double) k / panels, t1 = (double) (k + 1) / panels;
            double x0 = b - (b - a) * (1 - t0) * (1 - t0), x1 = b - (b - a) * (1 - t1) * (1 - t1);
            double c = 0.5 * (x0 + x1), h = 0.5 * (x1 - x0);
            for (int i = 0; i < 8; i++) {
                sum += GW[i] * h * p.applyAsDouble(c + h * GX[i]);
            }
        }
        return 2 * Math.PI * sum;
    }

    @Test
    void normalizationAndMeanCosines() {
        assertEquals(1.0, integrate(PhaseFunctionsd::rayleigh, -1, 1), 1e-14);
        for (double g : new double[] {-0.7, -0.2, 0, 0.3, 0.8, 0.95}) {
            assertEquals(1.0, integrate(u -> PhaseFunctionsd.henyeyGreenstein(u, g), -1, 1), 1e-11, "HG " + g);
            assertEquals(g, integrate(u -> u * PhaseFunctionsd.henyeyGreenstein(u, g), -1, 1), 1e-11, "HG mean " + g);
            assertEquals(1.0, integrate(u -> PhaseFunctionsd.cornetteShanks(u, g), -1, 1), 1e-11, "CS " + g);
            for (double alpha : new double[] {0, 0.5, 1, 7, 250}) {
                assertEquals(1.0, integrate(u -> PhaseFunctionsd.draine(u, g, alpha), -1, 1), 1e-11,
                        "Draine " + g + " " + alpha);
                assertEquals(PhaseFunctionsd.draineMeanCosine(g, alpha),
                        integrate(u -> u * PhaseFunctionsd.draine(u, g, alpha), -1, 1), 1e-11, "Draine mean");
            }
            assertEquals(PhaseFunctionsd.henyeyGreenstein(0.3, g), PhaseFunctionsd.draine(0.3, g, 0), 1e-15);
        }
        assertEquals(PhaseFunctionsd.rayleigh(0.4), PhaseFunctionsd.draine(0.4, 0, 1), 1e-16);
    }

    private static double maxInversionError(DoubleUnaryOperator pdf, DoubleUnaryOperator sampler, int n) {
        double worst = 0;
        for (int i = 0; i < n; i++) {
            double xi = (i + 0.5) / n;
            double u = sampler.applyAsDouble(xi);
            assertTrue(u >= -1 && u <= 1, "u out of range: " + u);
            worst = Math.max(worst, Math.abs(integrate(pdf, -1, u) - xi));
        }
        return worst;
    }

    @Test
    void samplersInvertTheirCdfs() {
        assertTrue(maxInversionError(PhaseFunctionsd::rayleigh, PhaseFunctionsd::sampleRayleigh, 64) < 1e-13);
        for (double g : new double[] {-0.9, -0.3, -1e-9, 0, 1e-7, 0.01, 0.2, 0.6, 0.9}) {
            double e = maxInversionError(u -> PhaseFunctionsd.henyeyGreenstein(u, g),
                    xi -> PhaseFunctionsd.sampleHenyeyGreenstein(xi, g), 64);
            assertTrue(e < 1e-12, "HG g=" + g + ": " + e);
            for (double alpha : new double[] {0.5, 1, 20, 250}) {
                double ed = maxInversionError(u -> PhaseFunctionsd.draine(u, g, alpha),
                        xi -> PhaseFunctionsd.sampleDraine(xi, g, alpha), 32);
                assertTrue(ed < 1e-13, "Draine g=" + g + " alpha=" + alpha + ": " + ed);
            }
        }
        // float samplers agree with double
        for (int i = 0; i < 100; i++) {
            float xi = (i + 0.5f) / 100;
            assertEquals(PhaseFunctionsd.sampleDraine(xi, 0.7, 3), PhaseFunctionsf.sampleDraine(xi, 0.7f, 3f), 1e-6);
            assertEquals(PhaseFunctionsd.sampleHenyeyGreenstein(xi, -0.4), PhaseFunctionsf.sampleHenyeyGreenstein(xi,
                    -0.4f), 1e-6);
        }
    }

    @Test
    void draineAnalyticCdfMatchesQuadrature() throws Exception {
        java.lang.reflect.Method m = Class.forName("org.dynamisengine.vectrix.atmosphere.DraineSampling")
                .getDeclaredMethod("cdfAnalytic", double.class, double.class, double.class);
        m.setAccessible(true);
        for (double g : new double[] {-0.9, -0.3, 0.15, 0.4, 0.9}) { // where it is used
            for (double alpha : new double[] {0.5, 1, 20, 250}) {
                for (double u = -1; u <= 1; u += 0.125) {
                    double uu = u;
                    double ref = integrate(x -> PhaseFunctionsd.draine(x, g, alpha), -1, uu);
                    assertEquals(ref, (double) m.invoke(null, uu, g, alpha), 1e-13, g + " " + alpha + " " + u);
                }
            }
        }
    }

    @Test
    void fogModel() {
        for (double d : new double[] {0.05, 0.5, 1.2, 2, 4.9, 5, 10, 20, 50}) {
            double[] p = PhaseFunctionsd.fogParameters(d, new double[4]);
            assertTrue(p[0] > -1 && p[0] < 1 && p[1] > -1 && p[1] < 1 && p[2] >= 0 && p[3] >= 0 && p[3] <= 1,
                    "parameters at d=" + d + ": " + java.util.Arrays.toString(p));
            assertEquals(1.0, integrate(u -> PhaseFunctionsd.fog(u, p), -1, 1), 1e-10, "fog normalization " + d);
            if (d >= 5) {
                // large droplets: strongly forward scattering
                assertTrue(integrate(u -> u * PhaseFunctionsd.fog(u, p), -1, 1) > 0.8);
            }
        }
        // the paper's large-droplet formulas at d = 10
        double[] p = PhaseFunctionsd.fogParameters(10, new double[4]);
        assertEquals(Math.exp(-0.0990567 / (10 - 1.67154)), p[0], 1e-15);
        assertEquals(Math.exp(-0.599085 / (10 - 0.641583) - 0.665888), p[3], 1e-15);
    }
}
