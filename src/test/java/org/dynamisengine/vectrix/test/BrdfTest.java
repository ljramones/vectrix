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

import org.dynamisengine.vectrix.brdf.DfgLutBuilder;
import org.dynamisengine.vectrix.brdf.Diffused;
import org.dynamisengine.vectrix.brdf.LightEngineCompatf;
import org.dynamisengine.vectrix.brdf.Microfacetd;
import org.dynamisengine.vectrix.brdf.Microfacetf;
import org.dynamisengine.vectrix.core.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for the BRDF package (review §15.1): normalisation, the weak white furnace, energy bounds,
 * reciprocity, the split-sum DFG estimator against brute-force quadrature, and the LightEngine-compatible variants.
 * Integrals are evaluated numerically in spherical coordinates, independently of the formulas under test.
 */
class BrdfTest {

    private static final double[] ALPHAS = {0.05, 0.1, 0.25, 0.5, 0.75, 1.0};

    /** ∫ over the hemisphere of f(θ, φ) dω, midpoint rule with {@code nt × np} cells. */
    private interface Hemi {
        double at(double x, double y, double z);
    }

    private static double hemisphere(Hemi f, int nt, int np) {
        double sum = 0;
        double dt = 0.5 * Math.PI / nt, dp = 2 * Math.PI / np;
        for (int i = 0; i < nt; i++) {
            double t = (i + 0.5) * dt, st = Math.sin(t), ct = Math.cos(t);
            for (int j = 0; j < np; j++) {
                double p = (j + 0.5) * dp;
                sum += f.at(st * Math.cos(p), st * Math.sin(p), ct) * st;
            }
        }
        return sum * dt * dp;
    }

    @Test
    void ggxDistributionIsNormalised() {
        for (double a : ALPHAS) {
            // ∫ D(h) (n.h) dω = 2π ∫ D(μ) μ dμ over μ = cosθ; fine steps resolve the narrow peak at small α
            int n = 400_000;
            double sum = 0;
            for (int i = 0; i < n; i++) {
                double mu = (i + 0.5) / n;
                sum += Microfacetd.dGgx(mu, a) * mu;
            }
            assertEquals(1.0, 2 * Math.PI * sum / n, 1e-4, "alpha " + a);
        }
    }

    @Test
    void anisotropicGgxIsNormalised() {
        double at = 0.3, ab = 0.7;
        double v = hemisphere((x, y, z) -> Microfacetd.dGgxAnisotropic(z, x, y, at, ab) * z, 3000, 1500);
        assertEquals(1.0, v, 2e-3);
        // equal widths reduce to the isotropic distribution
        assertEquals(Microfacetd.dGgx(0.8, 0.4), Microfacetd.dGgxAnisotropic(0.8, 0.36, 0.48, 0.4, 0.4), 1e-12);
    }

    @Test
    void charlieSheenIsNormalised() {
        for (double a : new double[] {0.2, 0.5, 1.0}) {
            double v = hemisphere((x, y, z) -> Microfacetd.dCharlie(z, a) * z, 4000, 8);
            assertEquals(1.0, v, 1e-3, "alpha " + a);
        }
    }

    @Test
    void smithMaskingPassesTheWeakWhiteFurnace() {
        // Heitz 2014: ∫ G1(v) D(h) max(0, v.h) / (n.v) dω_h = 1 for every view direction
        for (double a : new double[] {0.2, 0.5, 1.0}) {
            for (double nv : new double[] {0.2, 0.5, 0.9}) {
                double vx = Math.sqrt(1 - nv * nv);
                double g1 = Microfacetd.g1Smith(nv, a);
                double v = hemisphere((x, y, z) -> {
                    double vh = vx * x + nv * z;
                    return vh > 0 ? g1 * Microfacetd.dGgx(z, a) * vh / nv : 0;
                }, 3000, 600);
                assertEquals(1.0, v, 3e-3, "alpha " + a + " nDotV " + nv);
            }
        }
    }

    @Test
    void visibilityIsTheHeightCorrelatedG2AndReciprocal() {
        for (double a : ALPHAS) {
            for (double nv = 0.05; nv <= 1.0; nv += 0.15) {
                for (double nl = 0.05; nl <= 1.0; nl += 0.15) {
                    double vis = Microfacetd.vSmithGgxCorrelated(nv, nl, a);
                    double g2 = Microfacetd.g2SmithHeightCorrelated(nv, nl, a);
                    assertEquals(g2 / (4 * nv * nl), vis, 1e-12 * vis);
                    assertEquals(vis, Microfacetd.vSmithGgxCorrelated(nl, nv, a), 1e-15 * vis);
                }
            }
        }
    }

    /** Directional albedo of the specular lobe with F = 1: ∫ D(h) V(v, l) (n.l) dω_l. */
    private static double specularAlbedo(double nv, double alpha) {
        double vx = Math.sqrt(1 - nv * nv);
        return hemisphere((lx, ly, lz) -> {
            double hx = vx + lx, hy = ly, hz = nv + lz;
            double len = Math.sqrt(hx * hx + hy * hy + hz * hz);
            return Microfacetd.dGgx(hz / len, alpha) * Microfacetd.vSmithGgxCorrelated(nv, lz, alpha) * lz;
        }, 2000, 1000);
    }

    @Test
    void specularLobeConservesEnergyAndMatchesTheDfgEstimate() {
        double[] ab = new double[2];
        for (double r : new double[] {0.3, 0.6, 1.0}) {
            double alpha = r * r;
            for (double nv : new double[] {0.15, 0.5, 0.95}) {
                double e = specularAlbedo(nv, alpha);
                assertTrue(e <= 1.0 + 1e-3, "single scattering cannot exceed 1: " + e);
                DfgLutBuilder.integrate(nv, r, 4096, DfgLutBuilder.Model.HEIGHT_CORRELATED_SMITH, ab);
                assertEquals(e, ab[0] + ab[1], 4e-3, "DFG estimator vs quadrature, roughness " + r + " nDotV " + nv);
            }
        }
        // rough surfaces lose energy to multiple scattering, which Kulla-Conty puts back (white furnace = 1)
        DfgLutBuilder.integrate(0.5, 1.0, 4096, DfgLutBuilder.Model.HEIGHT_CORRELATED_SMITH, ab);
        assertTrue(ab[0] + ab[1] < 0.9, "rough single-scattering albedo " + (ab[0] + ab[1]));
        double compensated = (ab[0] + ab[1]) * Microfacetd.energyCompensation(1.0, ab[0], ab[1]);
        assertEquals(1.0, compensated, 1e-12);
        assertEquals(1.0, Microfacetd.energyCompensation(0.0, ab[0], ab[1]), 0.0);
    }

    @Test
    void dfgTableReducesToAMirrorAtLowRoughness() {
        double[] ab = new double[2];
        for (DfgLutBuilder.Model m : DfgLutBuilder.Model.values()) {
            for (double nv : new double[] {0.3, 0.5, 0.9}) {
                DfgLutBuilder.integrate(nv, 0.02, 1024, m, ab);
                double fc = Math.pow(1 - nv, 5);
                assertEquals(1 - fc, ab[0], 3e-3, m + " a at nDotV " + nv);
                assertEquals(fc, ab[1], 3e-3, m + " b at nDotV " + nv);
            }
        }
    }

    @Test
    void dfgTableLayoutAndDeterminism() {
        int size = 8;
        float[] t1 = new float[2 * size * size], t2 = new float[2 * size * size];
        DfgLutBuilder.build(size, 128, DfgLutBuilder.Model.HEIGHT_CORRELATED_SMITH, t1);
        DfgLutBuilder.build(size, 128, DfgLutBuilder.Model.HEIGHT_CORRELATED_SMITH, t2);
        assertTrue(java.util.Arrays.equals(t1, t2));
        double[] ab = new double[2];
        DfgLutBuilder.integrate((3 + 0.5) / size, (5 + 0.5) / size, 128, DfgLutBuilder.Model.HEIGHT_CORRELATED_SMITH, ab);
        assertEquals((float) ab[0], t1[2 * (5 * size + 3)], 0f);
        assertEquals((float) ab[1], t1[2 * (5 * size + 3) + 1], 0f);
    }

    @Test
    void diffuseLobes() {
        assertEquals(1.0, hemisphere((x, y, z) -> Diffused.lambert() * z, 2000, 4), 1e-6);
        for (double nv = 0.1; nv < 1; nv += 0.2) {
            for (double nl = 0.1; nl < 1; nl += 0.2) {
                assertEquals(Diffused.lambert(), Diffused.orenNayar(nv, nl, 0.3, 0.0), 1e-15);
                assertEquals(Diffused.orenNayar(nv, nl, 0.2, 0.6), Diffused.orenNayar(nl, nv, 0.2, 0.6), 1e-15);
                assertEquals(Diffused.burley(nv, nl, 0.7, 0.4), Diffused.burley(nl, nv, 0.7, 0.4), 1e-15);
            }
        }
        assertEquals(Diffused.lambert(), Diffused.burley(1, 1, 1, 0.8), 1e-15);
        // Oren-Nayar flattens: grazing retro-reflection (light behind the viewer) is brighter than Lambert
        assertTrue(Diffused.orenNayar(0.3, 0.3, 1.0, 0.5) > Diffused.lambert());
    }

    @Test
    void schlickFresnel() {
        assertEquals(0.04f, Microfacetf.fSchlick(0.04f, 1f, 1f), 0f);
        assertEquals(1f, Microfacetf.fSchlick(0.04f, 1f, 0f), 0f);
        Vector3f rgb = Microfacetf.fSchlick(new Vector3f(0.04f, 0.5f, 0.9f), 1f, 0.3f, new Vector3f());
        assertEquals(Microfacetf.fSchlick(0.5f, 1f, 0.3f), rgb.y, 0f);
        Vector3f rough0 = Microfacetf.fSchlickRoughness(new Vector3f(0.04f, 0.5f, 0.9f), 0f, 0.3f, new Vector3f());
        assertEquals(rgb.x, rough0.x, 0f);
        assertEquals(rgb.z, rough0.z, 0f);
        // roughness lowers the grazing reflectance of dielectrics: f90 becomes max(1 - roughness, f0) = 0.1
        float rough = Microfacetf.fSchlickRoughness(new Vector3f(0.04f), 0.9f, 0.1f, new Vector3f()).x;
        assertEquals(Microfacetf.fSchlick(0.04f, 0.1f, 0.1f), rough, 1e-7f);
        assertTrue(rough < Microfacetf.fSchlick(0.04f, 1f, 0.1f));
    }

    @Test
    void lightEngineCompatMatchesTheShaderFormulas() {
        for (float r = 0.05f; r <= 1f; r += 0.1f) {
            for (float c = 0.0f; c <= 1f; c += 0.1f) {
                // GLSL from VulkanMainFragmentShaderPart0 / GlShaderSources, written out again
                float a = r * r, a2 = a * a, d = (c * c) * (a2 - 1f) + 1f;
                assertEquals(a2 / Math.max(3.14159f * d * d, 0.0001f), LightEngineCompatf.distributionGgx(c, r), 0f);
                float rr = r + 1f, k = (rr * rr) / 8f;
                float g = c / Math.max(c * (1f - k) + k, 0.0001f);
                assertEquals(g, LightEngineCompatf.geometrySchlickGgx(c, r), 0f);
                assertEquals(g * g, LightEngineCompatf.geometrySmith(c, c, r), 0f);
                assertEquals(0.04f + 0.96f * (float) Math.pow(1 - c, 5), LightEngineCompatf.fresnelSchlick(c, 0.04f), 1e-6f);
                if (c > 0.05f) {
                    // the distribution differs from the reference only by π ≈ 3.14159 (relative 8.4e-7)
                    assertEquals(Microfacetf.dGgx(c, a), LightEngineCompatf.distributionGgx(c, r),
                            2e-6f * Microfacetf.dGgx(c, a));
                }
            }
        }
    }
}
