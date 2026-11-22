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

import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.sh.ShBasis;
import org.dynamisengine.vectrix.sh.ShCoeffs16d;
import org.dynamisengine.vectrix.sh.ShCoeffs9d;
import org.dynamisengine.vectrix.sh.ShCoeffs9f;
import org.dynamisengine.vectrix.sh.ShConvolution;
import org.dynamisengine.vectrix.sh.ShL1;
import org.dynamisengine.vectrix.sh.ShProduct;
import org.dynamisengine.vectrix.sh.ShWindowing;
import org.junit.jupiter.api.Test;

/** SH windowing, products (against exact quadrature) and the L1 irradiance paths. */
class ShExtrasTest {

    /** Sphere quadrature: Gauss-Legendre in z (n nodes) times uniform in φ (m nodes); rows x, y, z, weight. */
    static double[][] sphereQuadrature(int n, int m) {
        double[] z = new double[n], w = new double[n];
        for (int i = 0; i < n; i++) {
            double x = Math.cos(Math.PI * (i + 0.75) / (n + 0.5));
            for (int it = 0; it < 100; it++) {
                double p0 = 1, p1 = x;
                for (int k = 2; k <= n; k++) {
                    double p2 = ((2 * k - 1) * x * p1 - (k - 1) * p0) / k;
                    p0 = p1;
                    p1 = p2;
                }
                double dp = n * (x * p1 - p0) / (x * x - 1);
                double dx = p1 / dp;
                x -= dx;
                if (Math.abs(dx) < 1e-16) {
                    p0 = 1;
                    p1 = x;
                    for (int k = 2; k <= n; k++) {
                        double p2 = ((2 * k - 1) * x * p1 - (k - 1) * p0) / k;
                        p0 = p1;
                        p1 = p2;
                    }
                    dp = n * (x * p1 - p0) / (x * x - 1);
                    break;
                }
            }
            z[i] = x;
            double p0 = 1, p1 = x;
            for (int k = 2; k <= n; k++) {
                double p2 = ((2 * k - 1) * x * p1 - (k - 1) * p0) / k;
                p0 = p1;
                p1 = p2;
            }
            double dp = n * (x * p1 - p0) / (x * x - 1);
            w[i] = 2 / ((1 - x * x) * dp * dp);
        }
        double[][] q = new double[n * m][];
        for (int i = 0; i < n; i++) {
            double r = Math.sqrt(1 - z[i] * z[i]);
            for (int j = 0; j < m; j++) {
                double phi = 2 * Math.PI * (j + 0.5) / m;
                q[i * m + j] = new double[] {r * Math.cos(phi), r * Math.sin(phi), z[i], w[i] * 2 * Math.PI / m};
            }
        }
        return q;
    }

    private static double eval(double[] c, int count, int channel, double[] y) {
        double s = 0;
        for (int i = 0; i < count; i++) {
            s += c[3 * i + channel] * y[i];
        }
        return s;
    }

    @Test
    void gauntCoefficientsAndProducts() {
        double[][] q = sphereQuadrature(16, 32);
        double[] y = new double[16];
        // Gaunt against quadrature, and orthonormality through the constant function
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                assertEquals(i == j ? ShBasisConstants.Y0 : 0, ShProduct.gaunt(0, i, j), 1e-14);
                for (int k = 0; k < 16; k += 5) {
                    double s = 0;
                    for (double[] p : q) {
                        ShBasis.evaluateL3(p[0], p[1], p[2], y);
                        s += p[3] * y[i] * y[j] * y[k];
                    }
                    assertEquals(s, ShProduct.gaunt(i, j, k), 1e-13, i + "," + j + "," + k);
                }
            }
        }
        SplittableRandom rnd = new SplittableRandom(1);
        for (int t = 0; t < 20; t++) {
            ShCoeffs9d a = new ShCoeffs9d(), b = new ShCoeffs9d();
            for (int i = 0; i < 27; i++) {
                a.c[i] = rnd.nextDouble(-1, 1);
                b.c[i] = rnd.nextDouble(-1, 1);
            }
            ShCoeffs9d ab = ShProduct.multiplyL2(a, b, new ShCoeffs9d());
            // reference: project f·g onto L2 by quadrature
            for (int k = 0; k < 9; k++) {
                for (int ch = 0; ch < 3; ch++) {
                    double s = 0;
                    for (double[] p : q) {
                        ShBasis.evaluateL3(p[0], p[1], p[2], y);
                        s += p[3] * eval(a.c, 9, ch, y) * eval(b.c, 9, ch, y) * y[k];
                    }
                    assertEquals(s, ab.c[3 * k + ch], 1e-12);
                }
            }
            // multiplying by the constant function 1 is the identity
            ShCoeffs9d one = new ShCoeffs9d();
            one.c[0] = one.c[1] = one.c[2] = 1 / ShBasisConstants.Y0;
            ShCoeffs9d same = ShProduct.multiplyL2(a, one, new ShCoeffs9d());
            for (int i = 0; i < 27; i++) {
                assertEquals(a.c[i], same.c[i], 1e-13);
            }
            // a scalar function equals the RGB product with that function in every channel
            double[] s9 = new double[9];
            ShCoeffs9d sb = new ShCoeffs9d();
            for (int i = 0; i < 9; i++) {
                s9[i] = b.c[3 * i];
                sb.c[3 * i] = sb.c[3 * i + 1] = sb.c[3 * i + 2] = s9[i];
            }
            ShCoeffs9d viaScalar = ShProduct.multiplyScalarL2(a, s9, new ShCoeffs9d());
            ShCoeffs9d viaRgb = ShProduct.multiplyL2(a, sb, new ShCoeffs9d());
            for (int i = 0; i < 27; i++) {
                assertEquals(viaRgb.c[i], viaScalar.c[i], 1e-14);
            }
            // L3 product restricted to L2 inputs: its first nine bands match, and aliasing is safe
            ShCoeffs16d a16 = new ShCoeffs16d(), b16 = new ShCoeffs16d();
            System.arraycopy(a.c, 0, a16.c, 0, 27);
            System.arraycopy(b.c, 0, b16.c, 0, 27);
            ShProduct.multiplyL3(a16, b16, a16);
            for (int i = 0; i < 27; i++) {
                assertEquals(ab.c[i], a16.c[i], 1e-13);
            }
        }
    }

    @Test
    void windowingReducesRinging() {
        assertEquals(1.0, ShWindowing.weight(ShWindowing.Window.HANN, 0, 3.0), 0);
        assertEquals(0.0, ShWindowing.weight(ShWindowing.Window.LANCZOS, 3, 3.0), 0);
        for (ShWindowing.Window w : ShWindowing.Window.values()) {
            for (int l = 1; l < 4; l++) {
                assertTrue(ShWindowing.weight(w, l, 4.0) < ShWindowing.weight(w, l - 1, 4.0));
            }
        }
        // a hemispherical light (1 for z > 0): the truncated L2 reconstruction undershoots below zero
        double[][] q = sphereQuadrature(200, 8);
        double[] y = new double[16];
        ShCoeffs9d c = new ShCoeffs9d();
        for (double[] p : q) {
            if (p[2] > 0) {
                ShBasis.evaluateL3(p[0], p[1], p[2], y);
                for (int i = 0; i < 9; i++) {
                    c.c[3 * i] += p[3] * y[i];
                }
            }
        }
        double rawMin = minOnZAxis(c), hannMin = minOnZAxis(ShWindowing.windowL2(c, ShWindowing.Window.HANN, 3.0,
                new ShCoeffs9d())), lanczosMin = minOnZAxis(ShWindowing.windowL2(c, ShWindowing.Window.LANCZOS, 3.0,
                new ShCoeffs9d()));
        assertTrue(rawMin < -0.05, "the test light should ring: " + rawMin);
        assertTrue(hannMin > rawMin / 3 && lanczosMin > rawMin, rawMin + " " + hannMin + " " + lanczosMin);
        ShCoeffs9f f = new ShCoeffs9f();
        f.c[0] = 1;
        f.c[12] = 1;
        ShWindowing.windowL2(f, ShWindowing.Window.HANN, 3f, f);
        assertEquals(1f, f.c[0], 0f);
        assertEquals(0.25f, f.c[12], 1e-6f); // band 2 of width 3: (1 + cos(2π/3)) / 2
    }

    private static double minOnZAxis(ShCoeffs9d c) {
        double min = Double.MAX_VALUE;
        double[] y = new double[16];
        for (int i = 0; i <= 200; i++) {
            double z = -1 + i / 100.0;
            ShBasis.evaluateL3(Math.sqrt(1 - z * z), 0, z, y);
            min = Math.min(min, eval(c.c, 9, 0, y));
        }
        return min;
    }

    @Test
    void l1Irradiance() {
        SplittableRandom rnd = new SplittableRandom(2);
        double[][] q = sphereQuadrature(64, 128);
        for (int t = 0; t < 20; t++) {
            ShCoeffs9f c = new ShCoeffs9f();
            for (int i = 0; i < 27; i++) {
                c.c[i] = (float) rnd.nextDouble(-0.5, 0.5);
            }
            c.c[0] = c.c[1] = c.c[2] = 2f; // positive mean
            // linear equals the full Lambert convolution with band 2 removed
            ShCoeffs9f l1 = new ShCoeffs9f();
            System.arraycopy(c.c, 0, l1.c, 0, 12);
            ShCoeffs9f conv = ShConvolution.convolveLambertL2(l1, new ShCoeffs9f());
            Vector3f n = new Vector3f((float) rnd.nextDouble(-1, 1), (float) rnd.nextDouble(-1, 1), 0.3f).normalize();
            Vector3f ref = ShConvolution.evaluateL2(conv, n, new Vector3f());
            Vector3f lin = ShL1.irradianceLinear(c, n.x, n.y, n.z, new Vector3f());
            assertTrue(ref.distance(lin) < 1e-5f, ref + " vs " + lin);
            // non-linear: preserves the mean, never negative
            ShCoeffs9d cd = new ShCoeffs9d();
            for (int i = 0; i < 27; i++) {
                cd.c[i] = c.c[i];
            }
            double mean = 0;
            Vector3d e = new Vector3d();
            for (double[] p : q) {
                ShL1.irradianceNonLinear(cd, p[0], p[1], p[2], e);
                assertTrue(e.x >= 0 && e.y >= 0 && e.z >= 0);
                mean += p[3] * e.x;
            }
            assertEquals(Math.PI * ShBasisConstants.Y0 * cd.c[0], mean / (4 * Math.PI), 1e-6);
        }
        // weak directional term: non-linear agrees with linear to first order
        ShCoeffs9d w = new ShCoeffs9d();
        w.c[0] = 1;
        w.c[6] = 0.01; // c2 (z), red
        Vector3d lin = ShL1.irradianceLinear(w, 0, 0, 1, new Vector3d());
        Vector3d nl = ShL1.irradianceNonLinear(w, 0, 0, 1, new Vector3d());
        double r0 = Math.PI * ShBasisConstants.Y0;
        double r = (lin.x - r0) / (2 * r0); // |G| / (2 R0)
        assertEquals(lin.x, nl.x, 4 * r * r * r0, "second-order agreement");
        // strong light from +z: linear goes negative at -z, non-linear stays at 0 or above
        w.c[6] = 1.0;
        assertTrue(ShL1.irradianceLinear(w, 0, 0, -1, new Vector3d()).x < 0);
        assertTrue(ShL1.irradianceNonLinear(w, 0, 0, -1, new Vector3d()).x >= 0);
    }

    /** The two lowest normalisation constants, independently. */
    static final class ShBasisConstants {
        static final double Y0 = 0.5 / Math.sqrt(Math.PI);
    }
}
