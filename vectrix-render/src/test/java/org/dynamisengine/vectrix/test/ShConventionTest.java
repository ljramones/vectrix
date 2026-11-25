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

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.sh.ShBasis;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link ShBasis} to the standard real SH convention (Ramamoorthi and Hanrahan; no Condon–Shortley phase):
 * index {@code l(l+1)+m}, polar angle measured from +z, azimuth {@code atan2(y, x)}. The reference is built from the
 * associated Legendre polynomials, independently of the closed-form polynomials in {@code ShBasis}.
 */
class ShConventionTest {

    private static double factorial(int n) {
        double r = 1;
        for (int i = 2; i <= n; i++) {
            r *= i;
        }
        return r;
    }

    /** Associated Legendre polynomial P_l^m(x), m >= 0, without the Condon–Shortley phase. */
    private static double legendre(int l, int m, double x) {
        double pmm = 1.0;
        double s = java.lang.StrictMath.sqrt(java.lang.Math.max(0.0, 1.0 - x * x));
        for (int i = 1; i <= m; i++) {
            pmm *= (2 * i - 1) * s;
        }
        if (l == m) {
            return pmm;
        }
        double pmmp1 = x * (2 * m + 1) * pmm;
        if (l == m + 1) {
            return pmmp1;
        }
        double pll = 0;
        for (int ll = m + 2; ll <= l; ll++) {
            pll = ((2 * ll - 1) * x * pmmp1 - (ll + m - 1) * pmm) / (ll - m);
            pmm = pmmp1;
            pmmp1 = pll;
        }
        return pll;
    }

    private static double reference(int l, int m, double x, double y, double z) {
        int am = java.lang.Math.abs(m);
        double k = java.lang.StrictMath.sqrt((2 * l + 1) / (4 * java.lang.Math.PI) * factorial(l - am) / factorial(l + am));
        double phi = java.lang.StrictMath.atan2(y, x);
        double p = legendre(l, am, z);
        if (m == 0) {
            return k * p;
        }
        double trig = m > 0 ? java.lang.StrictMath.cos(m * phi) : java.lang.StrictMath.sin(am * phi);
        return java.lang.StrictMath.sqrt(2.0) * k * trig * p;
    }

    @Test
    void basisFollowsTheStandardRealConvention() {
        SplittableRandom rnd = new SplittableRandom(4);
        double[] d = new double[16];
        float[] f = new float[16];
        for (int t = 0; t < 2000; t++) {
            double x = rnd.nextDouble(-1, 1), y = rnd.nextDouble(-1, 1), z = rnd.nextDouble(-1, 1);
            double len = java.lang.Math.sqrt(x * x + y * y + z * z);
            if (len < 1e-3) {
                continue;
            }
            x /= len;
            y /= len;
            z /= len;
            ShBasis.evaluateL3(x, y, z, d);
            ShBasis.evaluateL3((float) x, (float) y, (float) z, f);
            for (int l = 0; l <= 3; l++) {
                for (int m = -l; m <= l; m++) {
                    int i = l * (l + 1) + m;
                    double ref = reference(l, m, x, y, z);
                    assertEquals(ref, d[i], 1e-9, "Y(" + l + "," + m + ") double at index " + i);
                    assertEquals(ref, f[i], 2e-6, "Y(" + l + "," + m + ") float at index " + i);
                }
            }
        }
    }

    @Test
    void zonalFunctionsAreSymmetricAboutZ() {
        double[] up = ShBasis.evaluateL3(0.0, 0.0, 1.0, new double[16]);
        assertEquals(0.6307831305, up[6], 1e-9); // Y(2,0) at +z = 2 * 0.3153915653
        assertEquals(0.7463526652, up[12], 1e-9); // Y(3,0) at +z = 2 * 0.3731763325
        for (int i : new int[] {1, 3, 4, 5, 7, 8, 9, 10, 11, 13, 14, 15}) {
            assertEquals(0.0, up[i], 1e-12, "non-zonal index " + i + " at +z");
        }
    }
}
