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
package org.dynamisengine.vectrix.atmosphere;

import org.dynamisengine.vectrix.core.AccurateMath;

/** Shared double-precision Draine inversion for {@link PhaseFunctionsf} and {@link PhaseFunctionsd}. */
final class DraineSampling {
    /**
     * Below this |g| the analytic CDF (which divides by g³) is less accurate than 16-point quadrature, above it the
     * quadrature is (the forward peak sharpens). Measured worst CDF errors over α in [0.5, 250]: quadrature 1.3e-14
     * for |g| ≤ 0.1, analytic 1.6e-14 for |g| ≥ 0.2; both about 5e-14 at the crossover.
     */
    static final double CLOSED_FORM_MIN_G = 0.15;

    private DraineSampling() {
    }

    static double sample(double xi, double g, double alpha) {
        if (alpha == 0.0) {
            return PhaseFunctionsf.sampleHg(xi, g);
        }
        if (java.lang.Math.abs(g) >= CLOSED_FORM_MIN_G) {
            double u = closedForm(xi, g, alpha);
            if (u >= -1.0 && u <= 1.0) {
                return polish(u, xi, g, alpha);
            }
        }
        return invert(xi, g, alpha);
    }

    /** Jendersie and d'Eon, supplemental eq. 6. */
    static double closedForm(double xi, double g, double a) {
        double g2 = g * g, g4 = g2 * g2;
        double t0 = a - a * g2;
        double t1 = a * g4 - a;
        double t2 = -3.0 * (4.0 * (g4 - g2) + t1 * (1.0 + g2));
        double t3 = g * (2.0 * xi - 1.0);
        double t4 = 3.0 * g2 * (1.0 + t3) + a * (2.0 + g2 * (1.0 + (1.0 + 2.0 * g2) * t3));
        double t5 = t0 * (t1 * t2 + t4 * t4) + t1 * t1 * t1;
        double t6 = t0 * 4.0 * (g4 - g2);
        double t7 = AccurateMath.cbrt(t5 + java.lang.Math.sqrt(t5 * t5 - t6 * t6 * t6));
        double t8 = 2.0 * (t1 + t6 / t7 + t7) / t0;
        double t9 = java.lang.Math.sqrt(6.0 * (1.0 + g2) + t8);
        double w = java.lang.Math.sqrt(6.0 * (1.0 + g2) - t8 + 8.0 * t4 / (t0 * t9)) - t9;
        return g / 2.0 + (1.0 / (2.0 * g) - 1.0 / (8.0 * g) * w * w);
    }

    /** Unnormalized density in u: (1 − g²)(1 + α u²) / (1 + g² − 2gu)^(3/2). */
    private static double density(double u, double g, double a) {
        double den = 1.0 + g * g - 2.0 * g * u;
        return (1.0 - g * g) * (1.0 + a * u * u) / (den * java.lang.Math.sqrt(den));
    }

    private static double normalization(double g, double a) {
        // ∫_{-1}^{1} density du = 2 (1 + α (1 + 2g²)/3)
        return 2.0 * (1.0 + a * (1.0 + 2.0 * g * g) / 3.0);
    }

    private static final double[] GL_X = {-0.9602898564975363, -0.7966664774136267, -0.5255324099163290,
        -0.1834346424956498, 0.1834346424956498, 0.5255324099163290, 0.7966664774136267, 0.9602898564975363};
    private static final double[] GL_W = {0.1012285362903763, 0.2223810344533745, 0.3137066458778873,
        0.3626837833783620, 0.3626837833783620, 0.3137066458778873, 0.2223810344533745, 0.1012285362903763};

    /** CDF by 8-point Gauss-Legendre on [−1, u] split in two panels: exact to rounding for smooth densities. */
    static double cdf(double u, double g, double a) {
        double sum = 0.0;
        double lo = -1.0, mid = 0.5 * (u - 1.0);
        for (int p = 0; p < 2; p++) {
            double x0 = p == 0 ? lo : mid, x1 = p == 0 ? mid : u;
            double c = 0.5 * (x0 + x1), h = 0.5 * (x1 - x0);
            for (int i = 0; i < 8; i++) {
                sum += GL_W[i] * h * density(c + h * GL_X[i], g, a);
            }
        }
        return sum / normalization(g, a);
    }

    /**
     * Analytic CDF (Jendersie and d'Eon, supplemental section 2.2), well conditioned for {@code |g| ≥}
     * {@link #CLOSED_FORM_MIN_G}; it divides by {@code g³}.
     */
    static double cdfAnalytic(double u, double g, double a) {
        double g2 = g * g, g3 = g2 * g, g4 = g2 * g2, g5 = g4 * g, g6 = g4 * g2;
        double v = java.lang.Math.sqrt(g2 - 2.0 * g * u + 1.0);
        double w = -2.0 * a * g6 + g4 * (a * (u * u - 2.0) - 3.0) - g2 * (a * (v + u * u - 2.0) + 3.0 * (v - 1.0));
        return (w + 2.0 * a * g5 * (v + u) + (a + 3.0) * g3 * v - 2.0 * a * (v - 1.0) - 2.0 * a * g * u)
                / (2.0 * g3 * (2.0 * a * g2 + a + 3.0) * v);
    }

    private static double cdfAny(double u, double g, double a) {
        return java.lang.Math.abs(g) >= CLOSED_FORM_MIN_G ? cdfAnalytic(u, g, a) : cdf(u, g, a);
    }

    private static double polish(double u, double xi, double g, double a) {
        return newton(u, xi, g, a);
    }

    /** Safeguarded Newton (bisection fallback) on the CDF. */
    private static double invert(double xi, double g, double a) {
        return newton(PhaseFunctionsf.sampleHg(xi, g), xi, g, a);
    }

    private static double newton(double start, double xi, double g, double a) {
        double lo = -1.0, hi = 1.0;
        double u = start;
        double norm = normalization(g, a);
        for (int it = 0; it < 60; it++) {
            double f = cdfAny(u, g, a) - xi;
            if (f > 0.0) {
                hi = u;
            } else {
                lo = u;
            }
            double step = f / (density(u, g, a) / norm);
            double nu = u - step;
            if (!(nu > lo && nu < hi)) {
                nu = 0.5 * (lo + hi);
            }
            if (java.lang.Math.abs(nu - u) <= 1e-15) {
                return nu;
            }
            u = nu;
        }
        return u;
    }
}
