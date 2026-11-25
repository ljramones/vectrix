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

import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.sampling.AliasTable;
import org.dynamisengine.vectrix.sampling.GgxSamplingd;
import org.dynamisengine.vectrix.sampling.R2Sequence;
import org.dynamisengine.vectrix.sampling.SobolSequence;
import org.dynamisengine.vectrix.sampling.SphericalSamplingd;
import org.dynamisengine.vectrix.sampling.Warpd;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for the sampling additions (review §15.2): every density integrates to 1, sampled directions pass
 * a chi-square goodness-of-fit test against their density, area-light samples are uniform over the light's solid
 * angle, and the sequences keep their stratification.
 */
class SamplingTest {

    private interface Pdf {
        double at(double x, double y, double z);
    }

    private interface Warp {
        void sample(double u1, double u2, Vector3d dest);
    }

    /** Integral of {@code f} over the sphere (or the upper hemisphere) by the midpoint rule. */
    private static double integrate(Pdf f, boolean hemisphere, int nt, int np) {
        double tMax = hemisphere ? Math.PI / 2 : Math.PI;
        double dt = tMax / nt, dp = 2 * Math.PI / np, sum = 0;
        for (int i = 0; i < nt; i++) {
            double t = (i + 0.5) * dt, st = Math.sin(t), ct = Math.cos(t);
            for (int j = 0; j < np; j++) {
                double p = (j + 0.5) * dp;
                sum += f.at(st * Math.cos(p), st * Math.sin(p), ct) * st;
            }
        }
        return sum * dt * dp;
    }

    /**
     * Chi-square test of {@code n} samples from {@code warp} against {@code pdf}, binned over (cos θ, φ); bins whose
     * expected count is below 5 are pooled. Fails if the statistic exceeds the p = 1e-4 critical value.
     */
    private static void chiSquare(String what, Warp warp, Pdf pdf, boolean hemisphere, int n) {
        int nc = 24, np = 48;
        double cMin = hemisphere ? 0 : -1;
        long[] observed = new long[nc * np];
        SplittableRandom rnd = new SplittableRandom(what.hashCode());
        Vector3d d = new Vector3d();
        for (int i = 0; i < n; i++) {
            warp.sample(rnd.nextDouble(), rnd.nextDouble(), d);
            double c = Math.max(cMin, Math.min(1, d.z));
            int ci = Math.min(nc - 1, (int) ((c - cMin) / (1 - cMin) * nc));
            double phi = Math.atan2(d.y, d.x);
            if (phi < 0) {
                phi += 2 * Math.PI;
            }
            int pi = Math.min(np - 1, (int) (phi / (2 * Math.PI) * np));
            observed[ci * np + pi]++;
        }
        double[] expected = new double[nc * np];
        int sub = 12;
        for (int ci = 0; ci < nc; ci++) {
            for (int pi = 0; pi < np; pi++) {
                double sum = 0;
                for (int a = 0; a < sub; a++) {
                    double c = cMin + (ci + (a + 0.5) / sub) * (1 - cMin) / nc;
                    double s = Math.sqrt(Math.max(0, 1 - c * c));
                    for (int b = 0; b < sub; b++) {
                        double phi = (pi + (b + 0.5) / sub) * 2 * Math.PI / np;
                        sum += pdf.at(s * Math.cos(phi), s * Math.sin(phi), c);
                    }
                }
                // dω = d(cos θ) dφ
                expected[ci * np + pi] = n * sum / (sub * sub) * ((1 - cMin) / nc) * (2 * Math.PI / np);
            }
        }
        double chi2 = 0, poolO = 0, poolE = 0;
        int bins = 0;
        for (int i = 0; i < observed.length; i++) {
            if (expected[i] < 5) {
                poolO += observed[i];
                poolE += expected[i];
                continue;
            }
            chi2 += (observed[i] - expected[i]) * (observed[i] - expected[i]) / expected[i];
            bins++;
        }
        if (poolE >= 5) {
            chi2 += (poolO - poolE) * (poolO - poolE) / poolE;
            bins++;
        }
        int k = bins - 1;
        // Wilson-Hilferty approximation of the chi-square quantile at p = 1e-4 (z = 3.719)
        double crit = k * Math.pow(1 - 2.0 / (9 * k) + 3.719 * Math.sqrt(2.0 / (9 * k)), 3);
        assertTrue(chi2 < crit, what + ": chi2 " + chi2 + " exceeds " + crit + " (" + k + " dof)");
    }

    @Test
    void hemisphereSphereAndConeWarps() {
        Pdf cos = (x, y, z) -> Warpd.pdfCosineHemisphere(z);
        assertEquals(1.0, integrate(cos, true, 2000, 8), 1e-6);
        chiSquare("cosine hemisphere", Warpd::squareToCosineHemisphere, cos, true, 200_000);
        Pdf uh = (x, y, z) -> z > 0 ? Warpd.pdfUniformHemisphere() : 0;
        assertEquals(1.0, integrate(uh, true, 2000, 8), 1e-6);
        chiSquare("uniform hemisphere", Warpd::squareToUniformHemisphere, uh, true, 200_000);
        Pdf us = (x, y, z) -> Warpd.pdfUniformSphere();
        assertEquals(1.0, integrate(us, false, 2000, 8), 1e-6);
        chiSquare("uniform sphere", Warpd::squareToUniformSphere, us, false, 200_000);
        // on a histogram bin edge (18/24): a density step inside a bin would bias its expected count
        double cosMax = 0.75;
        Pdf cone = (x, y, z) -> Warpd.pdfUniformCone(z, cosMax);
        assertEquals(1.0, integrate(cone, true, 20000, 8), 2e-4);
        chiSquare("uniform cone", (u1, u2, dest) -> Warpd.squareToUniformCone(u1, u2, cosMax, dest), cone, true,
                200_000);
    }

    @Test
    void concentricDiskIsUniform() {
        SplittableRandom rnd = new SplittableRandom(3);
        int n = 200_000, rings = 16, sectors = 32;
        long[] counts = new long[rings * sectors];
        Vector2d p = new Vector2d();
        for (int i = 0; i < n; i++) {
            Warpd.squareToUniformDiskConcentric(rnd.nextDouble(), rnd.nextDouble(), p);
            double r2 = p.x * p.x + p.y * p.y;
            assertTrue(r2 <= 1 + 1e-12);
            double phi = Math.atan2(p.y, p.x) + Math.PI;
            counts[Math.min(rings - 1, (int) (r2 * rings)) * sectors + Math.min(sectors - 1, (int) (phi / (2 * Math.PI) * sectors))]++;
        }
        double e = (double) n / counts.length, chi2 = 0;
        for (long c : counts) {
            chi2 += (c - e) * (c - e) / e;
        }
        int k = counts.length - 1;
        assertTrue(chi2 < k * Math.pow(1 - 2.0 / (9 * k) + 3.719 * Math.sqrt(2.0 / (9 * k)), 3), "chi2 " + chi2);
    }

    @Test
    void ggxVisibleNormalSampling() {
        for (double alpha : new double[] {0.1, 0.5, 1.0}) {
            for (double theta : new double[] {0.0, 1.0, 1.45}) {
                double vx = Math.sin(theta), vz = Math.cos(theta);
                Pdf pdf = (x, y, z) -> GgxSamplingd.pdfVisibleNormal(vz, vx * x + vz * z, z, alpha);
                assertEquals(1.0, integrate(pdf, true, 4000, 400), 3e-3, "alpha " + alpha + " theta " + theta);
                if (alpha >= 0.5) {
                    chiSquare("VNDF alpha " + alpha + " theta " + theta,
                            (u1, u2, dest) -> GgxSamplingd.sampleVisibleNormal(vx, 0, vz, alpha, alpha, u1, u2, dest),
                            pdf, true, 200_000);
                }
            }
        }
        // anisotropic, with an off-axis view
        double ax = 0.3, ay = 0.8;
        double vx = 0.4, vy = 0.5, vz = Math.sqrt(1 - vx * vx - vy * vy);
        Pdf aniso = (x, y, z) -> GgxSamplingd.pdfVisibleNormalAnisotropic(vx, vy, vz, x, y, z, ax, ay);
        assertEquals(1.0, integrate(aniso, true, 4000, 1000), 3e-3);
        chiSquare("anisotropic VNDF", (u1, u2, dest) -> GgxSamplingd.sampleVisibleNormal(vx, vy, vz, ax, ay, u1, u2, dest),
                aniso, true, 200_000);
        // every sampled normal faces the viewer
        SplittableRandom rnd = new SplittableRandom(8);
        Vector3d h = new Vector3d();
        for (int i = 0; i < 10_000; i++) {
            GgxSamplingd.sampleVisibleNormal(vx, vy, vz, ax, ay, rnd.nextDouble(), rnd.nextDouble(), h);
            assertTrue(vx * h.x + vy * h.y + vz * h.z >= -1e-9);
            assertEquals(1.0, h.length(), 1e-9);
        }
    }

    private static boolean insideTriangle(Vector3d d, Vector3d a, Vector3d b, Vector3d c) {
        double s = Math.signum(new Vector3d(a).cross(b).dot(c));
        return s * new Vector3d(a).cross(b).dot(d) >= -1e-9 && s * new Vector3d(b).cross(c).dot(d) >= -1e-9
                && s * new Vector3d(c).cross(a).dot(d) >= -1e-9;
    }

    @Test
    void sphericalTriangleSamplingIsUniformOverTheTriangle() {
        Vector3d a = new Vector3d(0.2, 0.1, 1).normalize(), b = new Vector3d(1, 0.3, 0.6).normalize();
        Vector3d c = new Vector3d(0.4, 1, 0.5).normalize();
        double omega = SphericalSamplingd.solidAngleTriangle(a, b, c);
        double quadOmega = integrate((x, y, z) -> insideTriangle(new Vector3d(x, y, z), a, b, c) ? 1 : 0, false, 3000, 6000);
        assertEquals(quadOmega, omega, 2e-3 * omega);
        // sample moments match (1/Ω) ∫ g dω for g = x, y, z, x y
        double[][] ref = new double[4][1];
        Pdf[] g = {(x, y, z) -> x, (x, y, z) -> y, (x, y, z) -> z, (x, y, z) -> x * y};
        SplittableRandom rnd = new SplittableRandom(5);
        int n = 400_000;
        double[] sum = new double[4];
        Vector3d d = new Vector3d();
        for (int i = 0; i < n; i++) {
            SphericalSamplingd.sampleTriangle(a, b, c, rnd.nextDouble(), rnd.nextDouble(), d);
            assertTrue(insideTriangle(d, a, b, c), "sample outside the triangle");
            sum[0] += d.x;
            sum[1] += d.y;
            sum[2] += d.z;
            sum[3] += d.x * d.y;
        }
        for (int k = 0; k < 4; k++) {
            Pdf gk = g[k];
            double expect = integrate((x, y, z) -> insideTriangle(new Vector3d(x, y, z), a, b, c) ? gk.at(x, y, z) : 0,
                    false, 3000, 6000) / quadOmega;
            assertEquals(expect, sum[k] / n, 3e-3, "moment " + k);
        }
    }

    @Test
    void sphericalRectangleSamplingIsUniformOverTheRectangle() {
        // rectangle in the plane z = 2: corner (-1, -0.5, 2), edges (2.5, 0, 0) and (0, 1.5, 0)
        Vector3d s = new Vector3d(-1, -0.5, 2), ex = new Vector3d(2.5, 0, 0), ey = new Vector3d(0, 1.5, 0);
        Pdf inside = (x, y, z) -> {
            if (z <= 0) {
                return 0;
            }
            double t = 2 / z, px = x * t, py = y * t;
            return px >= -1 && px <= 1.5 && py >= -0.5 && py <= 1.0 ? 1 : 0;
        };
        double quadOmega = integrate(inside, true, 3000, 6000);
        double omega = SphericalSamplingd.solidAngleRectangle(s, ex, ey);
        assertEquals(quadOmega, omega, 2e-3 * omega);
        SplittableRandom rnd = new SplittableRandom(6);
        int n = 400_000;
        double sx = 0, sy = 0;
        Vector3d d = new Vector3d();
        for (int i = 0; i < n; i++) {
            SphericalSamplingd.sampleRectangle(s, ex, ey, rnd.nextDouble(), rnd.nextDouble(), d);
            assertTrue(inside.at(d.x, d.y, d.z) == 1 || Math.abs(d.z * 1e-6) < 1, "sample misses the rectangle");
            sx += d.x;
            sy += d.y;
        }
        assertEquals(integrate((x, y, z) -> inside.at(x, y, z) * x, true, 3000, 6000) / quadOmega, sx / n, 3e-3);
        assertEquals(integrate((x, y, z) -> inside.at(x, y, z) * y, true, 3000, 6000) / quadOmega, sy / n, 3e-3);
    }

    @Test
    void aliasTableReproducesItsWeights() {
        float[] w = {3, 0, 1, 7.5f, 0.25f, 0, 12, 2, 2, 0.5f};
        AliasTable t = new AliasTable(w.length).build(w);
        double total = 0;
        for (float v : w) {
            total += v;
        }
        double pdfSum = 0;
        for (int i = 0; i < w.length; i++) {
            assertEquals(w[i] / total, t.pdf(i), 1e-7);
            pdfSum += t.pdf(i);
        }
        assertEquals(1.0, pdfSum, 1e-6);
        SplittableRandom rnd = new SplittableRandom(4);
        int n = 500_000;
        long[] counts = new long[w.length];
        for (int i = 0; i < n; i++) {
            counts[t.sample((float) rnd.nextDouble())]++;
        }
        double chi2 = 0;
        int k = -1;
        for (int i = 0; i < w.length; i++) {
            if (w[i] == 0) {
                assertEquals(0, counts[i], "zero-weight outcome " + i + " was drawn");
                continue;
            }
            double e = n * w[i] / total;
            chi2 += (counts[i] - e) * (counts[i] - e) / e;
            k++;
        }
        assertTrue(chi2 < 30, "chi2 " + chi2 + " with " + k + " dof"); // p ~ 1e-4 for 6 dof is 27.9
        assertThrows(IllegalArgumentException.class, () -> new AliasTable(2).build(new float[] {0, 0}));
        assertThrows(IllegalArgumentException.class, () -> new AliasTable(2).build(new float[] {1, -1}));
        assertThrows(IllegalArgumentException.class, () -> new AliasTable(2).build(new float[] {1, Float.NaN}));
    }

    @Test
    void misWeightsSumToOne() {
        SplittableRandom rnd = new SplittableRandom(2);
        for (int i = 0; i < 1000; i++) {
            float pf = (float) rnd.nextDouble(0, 10), pg = (float) rnd.nextDouble(0, 10);
            int nf = 1 + rnd.nextInt(4), ng = 1 + rnd.nextInt(4);
            assertEquals(1f, org.dynamisengine.vectrix.sampling.Warpf.balanceHeuristic(nf, pf, ng, pg)
                    + org.dynamisengine.vectrix.sampling.Warpf.balanceHeuristic(ng, pg, nf, pf), 1e-6f);
            assertEquals(1f, org.dynamisengine.vectrix.sampling.Warpf.powerHeuristic(nf, pf, ng, pg)
                    + org.dynamisengine.vectrix.sampling.Warpf.powerHeuristic(ng, pg, nf, pf), 1e-6f);
        }
    }

    @Test
    void r2SequenceIsEvenlySpread() {
        double g = 1.32471795724474602596;
        Vector2d p = new Vector2d();
        assertEquals(0.5 + 1 / g - 1, R2Sequence.r2(1, p).x, 1e-15);
        int n = 4096, cells = 8;
        int[] counts = new int[cells * cells];
        for (int i = 0; i < n; i++) {
            R2Sequence.r2(i, p);
            assertTrue(p.x >= 0 && p.x < 1 && p.y >= 0 && p.y < 1);
            counts[(int) (p.y * cells) * cells + (int) (p.x * cells)]++;
        }
        for (int c : counts) {
            // 64 per cell expected; independent uniform points would spread by about ±8 (1σ)
            assertTrue(Math.abs(c - 64) <= 3, "cell count " + c);
        }
    }

    @Test
    void owenScrambledSobolKeepsItsNetStructure() {
        int m = 8, n = 1 << m;
        for (int seed : new int[] {0, 1, 12345, -7}) {
            for (int a = 0; a <= m; a++) {
                int cx = 1 << a, cy = 1 << (m - a);
                boolean[] seen = new boolean[n];
                for (int i = 0; i < n; i++) {
                    double x = SobolSequence.sobolOwenDouble(i, 0, seed), y = SobolSequence.sobolOwenDouble(i, 1, seed);
                    assertTrue(x >= 0 && x < 1 && y >= 0 && y < 1);
                    int cell = (int) (y * cy) * cx + (int) (x * cx);
                    assertTrue(!seen[cell], "seed " + seed + ": two points in one " + cx + "x" + cy + " cell");
                    seen[cell] = true;
                }
            }
        }
        assertTrue(SobolSequence.sobolOwenDouble(5, 0, 1) != SobolSequence.sobolOwenDouble(5, 0, 2));
    }
}
