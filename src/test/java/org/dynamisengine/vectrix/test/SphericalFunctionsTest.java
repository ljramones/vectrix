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

import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.spherical.DirectionMappingsd;
import org.dynamisengine.vectrix.spherical.DirectionMappingsf;
import org.dynamisengine.vectrix.spherical.SphericalGaussiansd;
import org.dynamisengine.vectrix.spherical.SphericalGaussiansf;
import org.junit.jupiter.api.Test;

/** Spherical Gaussians against quadrature, SG fitting on von Mises-Fisher samples, and direction mappings. */
class SphericalFunctionsTest {

    private static final Vector3d Z = new Vector3d(0, 0, 1);

    private static Vector3d randomDir(SplittableRandom rnd) {
        Vector3d v;
        do {
            v = new Vector3d(rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1));
        } while (v.lengthSquared() > 1 || v.lengthSquared() < 1e-4);
        return v.normalize();
    }

    @Test
    void sphericalGaussianIntegralsAndProducts() {
        double[][] q = ShExtrasTest.sphereQuadrature(800, 64);
        Vector3d v = new Vector3d();
        for (double lambda : new double[] {0, 1e-4, 0.5, 5, 50, 300}) {
            double s = 0;
            for (double[] p : q) {
                s += p[3] * SphericalGaussiansd.evaluate(Z, lambda, 2, v.set(p[0], p[1], p[2]));
            }
            assertEquals(s, SphericalGaussiansd.integral(lambda, 2), 1e-9 * Math.max(1, s), "λ = " + lambda);
        }
        assertEquals(4 * Math.PI, SphericalGaussiansd.integral(0, 1), 1e-15);
        SplittableRandom rnd = new SplittableRandom(1);
        double[][] q2 = ShExtrasTest.sphereQuadrature(200, 400);
        for (int t = 0; t < 20; t++) {
            Vector3d a1 = randomDir(rnd), a2 = randomDir(rnd);
            double l1 = rnd.nextDouble(0.5, 15), l2 = rnd.nextDouble(0.5, 15), c1 = rnd.nextDouble(0.1, 3);
            double c2 = rnd.nextDouble(0.1, 3);
            Vector4d lobe = new Vector4d();
            double amp = SphericalGaussiansd.product(a1, l1, c1, a2, l2, c2, lobe);
            Vector3d axis = new Vector3d(lobe.x, lobe.y, lobe.z);
            double inner = 0;
            for (double[] p : q2) {
                v.set(p[0], p[1], p[2]);
                double prod = SphericalGaussiansd.evaluate(a1, l1, c1, v) * SphericalGaussiansd.evaluate(a2, l2, c2, v);
                inner += p[3] * prod;
                if (p[3] > 0 && rnd.nextInt(500) == 0) {
                    assertEquals(prod, SphericalGaussiansd.evaluate(axis, lobe.w, amp, v), 1e-12 * Math.max(1, prod));
                }
            }
            assertEquals(inner, SphericalGaussiansd.innerProduct(a1, l1, c1, a2, l2, c2), 1e-6 * Math.max(1e-3, inner));
        }
        // exact cancellation keeps the first axis
        Vector4d lobe = new Vector4d();
        SphericalGaussiansd.product(Z, 3, 1, new Vector3d(0, 0, -1), 3, 1, lobe);
        assertEquals(new Vector4d(0, 0, 1, 0), lobe);
    }

    @Test
    void irradianceApproximations() {
        double[][] q = ShExtrasTest.sphereQuadrature(1000, 256);
        Vector3d v = new Vector3d();
        double worstFitted = 0, worstInner = 0;
        for (double lambda : new double[] {1, 2, 4, 8, 16, 32, 64}) {
            for (double angle = 0; angle <= Math.PI; angle += Math.PI / 12) {
                Vector3d n = new Vector3d(Math.sin(angle), 0, Math.cos(angle));
                double ref = 0;
                for (double[] p : q) {
                    v.set(p[0], p[1], p[2]);
                    ref += p[3] * SphericalGaussiansd.evaluate(Z, lambda, 1, v) * Math.max(0, n.dot(v));
                }
                double scale = SphericalGaussiansd.integral(lambda, 1); // irradiance under the light: ~ this
                worstFitted = Math.max(worstFitted,
                        Math.abs(SphericalGaussiansd.irradianceFitted(Z, lambda, 1, n) - ref) / scale);
                worstInner = Math.max(worstInner,
                        Math.abs(SphericalGaussiansd.irradianceInnerProduct(Z, lambda, 1, n) - ref) / scale);
            }
        }
        System.out.printf("SG irradiance: fitted max error %.4f, inner product %.4f (relative to the lobe integral)%n",
                worstFitted, worstInner);
        assertTrue(worstFitted < 0.02, "fitted " + worstFitted);
        assertTrue(worstInner < 0.16, "inner product " + worstInner); // a coarse method; see its Javadoc
    }

    @Test
    void fittingRecoversVonMisesFisherLobes() {
        SplittableRandom rnd = new SplittableRandom(2);
        for (double lambda : new double[] {2, 10, 40}) {
            int n = 40000;
            float[] dirs = new float[3 * n], w = new float[n];
            Vector3d axis = randomDir(rnd);
            Vector3d t1 = new Vector3d(), t2 = new Vector3d();
            (Math.abs(axis.x) < 0.9 ? new Vector3d(1, 0, 0) : new Vector3d(0, 1, 0)).orthogonalizeUnit(axis, t1);
            axis.cross(t1, t2);
            for (int i = 0; i < n; i++) {
                double c = 1 + Math.log(rnd.nextDouble() * (1 - Math.exp(-2 * lambda)) + Math.exp(-2 * lambda)) / lambda;
                double s = Math.sqrt(Math.max(0, 1 - c * c)), phi = rnd.nextDouble(2 * Math.PI);
                dirs[3 * i] = (float) (c * axis.x + s * (Math.cos(phi) * t1.x + Math.sin(phi) * t2.x));
                dirs[3 * i + 1] = (float) (c * axis.y + s * (Math.cos(phi) * t1.y + Math.sin(phi) * t2.y));
                dirs[3 * i + 2] = (float) (c * axis.z + s * (Math.cos(phi) * t1.z + Math.sin(phi) * t2.z));
                w[i] = 1f / n;
            }
            Vector4f lobe = new Vector4f();
            float amp = SphericalGaussiansf.fit(dirs, 0, w, 0, n, lobe);
            assertEquals(lambda, lobe.w, 0.05 * lambda, "sharpness");
            assertTrue(new Vector3f(lobe.x, lobe.y, lobe.z).dot((float) axis.x, (float) axis.y, (float) axis.z) > 0.999f);
            assertEquals(1f, SphericalGaussiansf.integral(lobe.w, amp), 1e-5f, "integral equals the total weight");
        }
    }

    @Test
    void anisotropicSphericalGaussians() {
        double[][] q = ShExtrasTest.sphereQuadrature(1200, 1200);
        Vector3d x = new Vector3d(1, 0, 0), y = new Vector3d(0, 1, 0), v = new Vector3d();
        assertEquals(3.0, SphericalGaussiansd.evaluateAnisotropic(x, y, Z, 5, 9, 3, Z), 0);
        for (double[] bw : new double[][] {{40, 80}, {100, 100}, {30, 300}}) {
            double s = 0;
            for (double[] p : q) {
                s += p[3] * SphericalGaussiansd.evaluateAnisotropic(x, y, Z, bw[0], bw[1], 1, v.set(p[0], p[1], p[2]));
            }
            double approx = SphericalGaussiansd.integralAnisotropic(bw[0], bw[1], 1);
            assertEquals(s, approx, 0.03 * s, bw[0] + ", " + bw[1]);
        }
    }

    @Test
    void octahedralMappings() {
        SplittableRandom rnd = new SplittableRandom(3);
        Vector2d uv = new Vector2d();
        Vector3d back = new Vector3d();
        Vector2f uvf = new Vector2f();
        Vector3f backf = new Vector3f();
        for (int i = 0; i < 20000; i++) {
            Vector3d d = randomDir(rnd);
            DirectionMappingsd.octahedralEncode(d.x, d.y, d.z, uv);
            assertTrue(Math.abs(uv.x) + 0 <= 1 && Math.abs(uv.y) <= 1);
            assertTrue(DirectionMappingsd.octahedralDecode(uv.x, uv.y, back).distance(d) < 1e-14);
            DirectionMappingsf.octahedralEncode((float) d.x, (float) d.y, (float) d.z, uvf);
            assertTrue(DirectionMappingsf.octahedralDecode(uvf.x, uvf.y, backf).distance((float) d.x, (float) d.y,
                    (float) d.z) < 1e-6f);
            assertEquals(d.z >= 0, Math.abs(uv.x) + Math.abs(uv.y) <= 1 + 1e-15, "upper hemisphere in the diamond");
            Vector3d h = new Vector3d(d.x, d.y, Math.abs(d.z));
            DirectionMappingsd.hemiOctahedralEncode(h.x, h.y, h.z, uv);
            assertTrue(Math.abs(uv.x) <= 1 + 1e-15 && Math.abs(uv.y) <= 1 + 1e-15);
            assertTrue(DirectionMappingsd.hemiOctahedralDecode(uv.x, uv.y, back).distance(h) < 1e-14);
            // every point of the square decodes to a unit direction that encodes back to it
            double u = rnd.nextDouble(-1, 1), w = rnd.nextDouble(-1, 1);
            DirectionMappingsd.octahedralDecode(u, w, back);
            assertEquals(1.0, back.length(), 1e-15);
            DirectionMappingsd.octahedralEncode(back.x, back.y, back.z, uv);
            assertEquals(u, uv.x, 1e-14);
            assertEquals(w, uv.y, 1e-14);
        }
    }

    @Test
    void cubemapMappingAndTexelSolidAngles() {
        Vector2d uv = new Vector2d();
        assertEquals(0, DirectionMappingsd.cubeFace(1, 0, 0, uv));
        assertEquals(new Vector2d(0.5, 0.5), uv);
        assertEquals(0, DirectionMappingsd.cubeFace(1, 0.5, 0, uv));
        assertEquals(0.25, uv.y, 1e-15, "v points down: +y is near the top of the +X face");
        assertEquals(4, DirectionMappingsd.cubeFace(0.5, 0, 1, uv));
        assertEquals(0.75, uv.x, 1e-15, "u points right on +Z: +x");
        assertEquals(2, DirectionMappingsd.cubeFace(0, 1, 0.5, uv));
        assertEquals(0.75, uv.y, 1e-15, "on +Y, v follows +z");
        SplittableRandom rnd = new SplittableRandom(4);
        Vector3d back = new Vector3d();
        for (int i = 0; i < 20000; i++) {
            Vector3d d = randomDir(rnd);
            int face = DirectionMappingsd.cubeFace(d.x, d.y, d.z, uv);
            assertTrue(uv.x >= 0 && uv.x <= 1 && uv.y >= 0 && uv.y <= 1);
            assertTrue(DirectionMappingsd.cubeDirection(face, uv.x, uv.y, back).distance(d) < 1e-14);
            int f2 = DirectionMappingsd.cubeFace(back.x, back.y, back.z, new Vector2d());
            assertEquals(face, f2);
        }
        for (int size : new int[] {1, 7, 64}) {
            double total = 0;
            for (int j = 0; j < size; j++) {
                for (int i = 0; i < size; i++) {
                    double sa = DirectionMappingsd.cubeTexelSolidAngle(size, i, j);
                    total += sa;
                    double x = 2 * (i + 0.5) / size - 1, y = 2 * (j + 0.5) / size - 1;
                    double approx = 4.0 / (size * size) / Math.pow(1 + x * x + y * y, 1.5);
                    if (size == 64) {
                        assertEquals(approx, sa, 1e-3 * approx);
                    }
                }
            }
            assertEquals(4 * Math.PI, 6 * total, 1e-12, "size " + size);
        }
        assertEquals(4 * Math.PI / 6, DirectionMappingsf.cubeTexelSolidAngle(1, 0, 0), 1e-6f);
    }
}
