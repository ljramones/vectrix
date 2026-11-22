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

import java.math.BigDecimal;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.Matrix3f;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.geometry.BoundingVolumesf;
import org.dynamisengine.vectrix.geometry.ClosestPointsf;
import org.dynamisengine.vectrix.geometry.Intersectionf;
import org.dynamisengine.vectrix.geometry.MeshMath;
import org.dynamisengine.vectrix.geometry.RobustPredicates;
import org.dynamisengine.vectrix.geometry.SpatialCodes;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for the geometry functions of review §17: exact predicates against BigDecimal determinants,
 * spatial codes, bounding volumes against brute force, the new closest-point queries against sampling and sphere
 * tracing, and the mesh normal, tangent and quality kernels.
 */
class GeometryFunctionsTest {

    private static BigDecimal b(double v) {
        return new BigDecimal(v);
    }

    @Test
    void orient2dIsExactWhereDoublesFail() {
        // Kettner et al.'s classic: points near the line y = x, a 256 x 256 ulp grid around (0.5, 0.5)
        double bx = 12, by = 12, cx = 24, cy = 24;
        int naiveWrong = 0;
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 256; j++) {
                double ax = 0.5 + i * Math.ulp(0.5), ay = 0.5 + j * Math.ulp(0.5);
                int exact = b(ax).subtract(b(cx)).multiply(b(by).subtract(b(cy)))
                        .subtract(b(ay).subtract(b(cy)).multiply(b(bx).subtract(b(cx)))).signum();
                double naive = (ax - cx) * (by - cy) - (ay - cy) * (bx - cx);
                if (Math.signum(naive) != exact) {
                    naiveWrong++;
                }
                assertEquals(exact, Math.signum(RobustPredicates.orient2d(ax, ay, bx, by, cx, cy)), 0, i + "," + j);
            }
        }
        assertTrue(naiveWrong > 1000, "the test grid should defeat plain doubles: " + naiveWrong);
    }

    private static int exactOrient3d(double[] a, double[] bb, double[] c, double[] d) {
        BigDecimal[][] m = new BigDecimal[3][3];
        double[][] p = {a, bb, c};
        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 3; k++) {
                m[i][k] = b(p[i][k]).subtract(b(d[k]));
            }
        }
        return m[0][0].multiply(m[1][1].multiply(m[2][2]).subtract(m[1][2].multiply(m[2][1])))
                .subtract(m[0][1].multiply(m[1][0].multiply(m[2][2]).subtract(m[1][2].multiply(m[2][0]))))
                .add(m[0][2].multiply(m[1][0].multiply(m[2][1]).subtract(m[1][1].multiply(m[2][0])))).signum();
    }

    @Test
    void orient3dIsExactNearCoplanarity() {
        SplittableRandom rnd = new SplittableRandom(1);
        for (int t = 0; t < 20_000; t++) {
            double[] a = {rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1)};
            double[] bb = {rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1)};
            double[] c = {rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1)};
            // d on the plane through a, b, c, then nudged by a few ulps: the fast path cannot decide these
            double u = rnd.nextDouble(-2, 2), v = rnd.nextDouble(-2, 2);
            double[] d = new double[3];
            for (int k = 0; k < 3; k++) {
                d[k] = a[k] + u * (bb[k] - a[k]) + v * (c[k] - a[k]);
                d[k] += rnd.nextInt(-3, 4) * Math.ulp(d[k]);
            }
            int exact = exactOrient3d(a, bb, c, d);
            double r = RobustPredicates.orient3d(a[0], a[1], a[2], bb[0], bb[1], bb[2], c[0], c[1], c[2], d[0], d[1], d[2]);
            assertEquals(exact, Math.signum(r), 0, "trial " + t);
        }
    }

    @Test
    void incircleAndInsphere() {
        // exactly cocircular / cospherical points give exactly zero
        assertEquals(0.0, RobustPredicates.incircle(1, 0, 0, 1, -1, 0, 0, -1), 0.0);
        assertTrue(RobustPredicates.incircle(1, 0, 0, 1, -1, 0, 0.1, 0) > 0, "inside");
        assertTrue(RobustPredicates.incircle(1, 0, 0, 1, -1, 0, 0, -1.1) < 0, "outside");
        double[] a = {1, 0, 0}, bb = {0, 1, 0}, c = {0, 0, 1}, d = {-1, 0, 0};
        double o = RobustPredicates.orient3d(a[0], a[1], a[2], bb[0], bb[1], bb[2], c[0], c[1], c[2], d[0], d[1], d[2]);
        double[] t1 = o > 0 ? a : bb, t2 = o > 0 ? bb : a; // make orient3d(a, b, c, d) positive
        assertTrue(RobustPredicates.orient3d(t1[0], t1[1], t1[2], t2[0], t2[1], t2[2], c[0], c[1], c[2], d[0], d[1], d[2]) > 0);
        assertEquals(0.0, RobustPredicates.insphere(t1[0], t1[1], t1[2], t2[0], t2[1], t2[2], c[0], c[1], c[2], d[0],
                d[1], d[2], 0, -1, 0), 0.0, "cospherical");
        assertTrue(RobustPredicates.insphere(t1[0], t1[1], t1[2], t2[0], t2[1], t2[2], c[0], c[1], c[2], d[0], d[1],
                d[2], 0.1, 0.1, 0.1) > 0, "inside");
        assertTrue(RobustPredicates.insphere(t1[0], t1[1], t1[2], t2[0], t2[1], t2[2], c[0], c[1], c[2], d[0], d[1],
                d[2], 2, 0, 0) < 0, "outside");
        // just inside / just outside the unit sphere by one ulp: the fast path cannot decide these
        double in = 1 - Math.ulp(1.0) / 2, out = 1 + Math.ulp(1.0);
        assertTrue(RobustPredicates.insphere(t1[0], t1[1], t1[2], t2[0], t2[1], t2[2], c[0], c[1], c[2], d[0], d[1],
                d[2], 0, -in, 0) > 0, "one ulp inside");
        assertTrue(RobustPredicates.insphere(t1[0], t1[1], t1[2], t2[0], t2[1], t2[2], c[0], c[1], c[2], d[0], d[1],
                d[2], 0, -out, 0) < 0, "one ulp outside");
        assertTrue(RobustPredicates.incircle(1, 0, 0, 1, -1, 0, 0, -in) > 0, "one ulp inside the circle");
        assertTrue(RobustPredicates.incircle(1, 0, 0, 1, -1, 0, 0, -out) < 0, "one ulp outside the circle");
    }

    @Test
    void mortonCodes() {
        assertEquals(1, SpatialCodes.morton3d(1, 0, 0));
        assertEquals(2, SpatialCodes.morton3d(0, 1, 0));
        assertEquals(4, SpatialCodes.morton3d(0, 0, 1));
        assertEquals(8, SpatialCodes.morton3d(2, 0, 0));
        assertEquals(1, SpatialCodes.morton2d(1, 0));
        assertEquals(2, SpatialCodes.morton2d(0, 1));
        SplittableRandom rnd = new SplittableRandom(2);
        for (int i = 0; i < 10_000; i++) {
            int x = rnd.nextInt(1 << 10), y = rnd.nextInt(1 << 10), z = rnd.nextInt(1 << 10);
            int m = SpatialCodes.morton3d(x, y, z);
            assertEquals(x, SpatialCodes.morton3dX(m));
            assertEquals(y, SpatialCodes.morton3dY(m));
            assertEquals(z, SpatialCodes.morton3dZ(m));
            int x21 = rnd.nextInt(1 << 21), y21 = rnd.nextInt(1 << 21), z21 = rnd.nextInt(1 << 21);
            long m64 = SpatialCodes.morton3d64(x21, y21, z21);
            assertEquals(x21, SpatialCodes.morton3dX(m64));
            assertEquals(z21, SpatialCodes.morton3dZ(m64));
            int x16 = rnd.nextInt(1 << 16), y16 = rnd.nextInt(1 << 16);
            assertEquals(x16, SpatialCodes.morton2dX(SpatialCodes.morton2d(x16, y16) & 0xFFFFFFFFL));
            assertEquals(y16, SpatialCodes.morton2dY(SpatialCodes.morton2d(x16, y16) & 0xFFFFFFFFL));
            int x32 = rnd.nextInt(), y32 = rnd.nextInt();
            assertEquals(x32, SpatialCodes.morton2dX(SpatialCodes.morton2d64(x32, y32)));
            assertEquals(y32, SpatialCodes.morton2dY(SpatialCodes.morton2d64(x32, y32)));
        }
    }

    @Test
    void hilbertCurvesAreBijectiveAndContinuous() {
        int bits = 4, n = 1 << bits;
        boolean[] seen = new boolean[n * n];
        int[] xy = new int[2], prev = null;
        for (long d = 0; d < (long) n * n; d++) {
            SpatialCodes.hilbert2dToXy(d, bits, xy);
            assertEquals(d, SpatialCodes.hilbert2d(xy[0], xy[1], bits));
            assertTrue(!seen[xy[1] * n + xy[0]]);
            seen[xy[1] * n + xy[0]] = true;
            if (prev != null) {
                assertEquals(1, Math.abs(xy[0] - prev[0]) + Math.abs(xy[1] - prev[1]), "2D step at " + d);
            }
            prev = xy.clone();
        }
        int b3 = 3, n3 = 1 << b3;
        boolean[] seen3 = new boolean[n3 * n3 * n3];
        int[] p = new int[3], prev3 = null;
        for (long d = 0; d < (long) n3 * n3 * n3; d++) {
            SpatialCodes.hilbert3dToXyz(d, b3, p);
            assertEquals(d, SpatialCodes.hilbert3d(p[0], p[1], p[2], b3), "3D round trip at " + d);
            assertTrue(!seen3[(p[2] * n3 + p[1]) * n3 + p[0]]);
            seen3[(p[2] * n3 + p[1]) * n3 + p[0]] = true;
            if (prev3 != null) {
                assertEquals(1, Math.abs(p[0] - prev3[0]) + Math.abs(p[1] - prev3[1]) + Math.abs(p[2] - prev3[2]),
                        "3D step at " + d);
            }
            prev3 = p.clone();
        }
        SplittableRandom rnd = new SplittableRandom(3);
        for (int i = 0; i < 2000; i++) {
            int x = rnd.nextInt(1 << 21), y = rnd.nextInt(1 << 21), z = rnd.nextInt(1 << 21);
            SpatialCodes.hilbert3dToXyz(SpatialCodes.hilbert3d(x, y, z, 21), 21, p);
            assertTrue(p[0] == x && p[1] == y && p[2] == z);
            SpatialCodes.hilbert2dToXy(SpatialCodes.hilbert2d(x, y, 21), 21, xy);
            assertTrue(xy[0] == x && xy[1] == y);
        }
    }

    @Test
    void minimalSphere() {
        SplittableRandom rnd = new SplittableRandom(4);
        for (int t = 0; t < 50; t++) {
            int n = 5 + rnd.nextInt(200);
            float[] p = new float[3 * n];
            for (int i = 0; i < 3 * n; i++) {
                p[i] = (float) rnd.nextDouble(-3, 3) * (i % 3 == 0 ? 2 : 1);
            }
            Vector4f s = BoundingVolumesf.minimalSphere(p, n, new Vector4f());
            for (int i = 0; i < n; i++) {
                double d = Math.sqrt(sq(p[3 * i] - s.x) + sq(p[3 * i + 1] - s.y) + sq(p[3 * i + 2] - s.z));
                assertTrue(d <= s.w * (1 + 1e-5) + 1e-5, "point outside");
            }
            // Badoiu-Clarkson core-set iteration: an independent (1 + ε)-approximation from above
            double cx = p[0], cy = p[1], cz = p[2];
            for (int k = 1; k < 20000; k++) {
                int far = 0;
                double best = -1;
                for (int i = 0; i < n; i++) {
                    double d = sq(p[3 * i] - cx) + sq(p[3 * i + 1] - cy) + sq(p[3 * i + 2] - cz);
                    if (d > best) {
                        best = d;
                        far = i;
                    }
                }
                cx += (p[3 * far] - cx) / (k + 1);
                cy += (p[3 * far + 1] - cy) / (k + 1);
                cz += (p[3 * far + 2] - cz) / (k + 1);
            }
            double rbc = 0;
            for (int i = 0; i < n; i++) {
                rbc = Math.max(rbc, Math.sqrt(sq(p[3 * i] - cx) + sq(p[3 * i + 1] - cy) + sq(p[3 * i + 2] - cz)));
            }
            assertTrue(s.w <= rbc * (1 + 1e-5), "Welzl radius " + s.w + " above the approximation " + rbc);
            assertTrue(s.w >= rbc / 1.02, "Welzl radius " + s.w + " far below the approximation " + rbc);
        }
        float[] cube = {-1, -1, -1, 1, -1, -1, -1, 1, -1, 1, 1, -1, -1, -1, 1, 1, -1, 1, -1, 1, 1, 1, 1, 1};
        Vector4f s = BoundingVolumesf.minimalSphere(cube, 8, new Vector4f());
        assertEquals(Math.sqrt(3), s.w, 1e-6);
        assertEquals(0, s.x, 1e-6);
    }

    private static double sq(double x) {
        return x * x;
    }

    @Test
    void orientedBoxesAndRectangles() {
        // points filling a rotated 4 x 2 x 1 box
        SplittableRandom rnd = new SplittableRandom(5);
        Matrix3f rot = new Matrix3f().rotateXYZ(0.4f, -0.9f, 0.3f);
        int n = 2000;
        float[] p = new float[3 * n];
        Vector3f q = new Vector3f();
        for (int i = 0; i < n; i++) {
            q.set((float) rnd.nextDouble(-2, 2), (float) rnd.nextDouble(-1, 1), (float) rnd.nextDouble(-0.5, 0.5));
            if (i < 8) {
                q.set((i & 1) == 0 ? -2 : 2, (i & 2) == 0 ? -1 : 1, (i & 4) == 0 ? -0.5f : 0.5f); // the corners
            }
            rot.transform(q).add(1, 2, 3);
            p[3 * i] = q.x;
            p[3 * i + 1] = q.y;
            p[3 * i + 2] = q.z;
        }
        Vector3f c = new Vector3f(), h = new Vector3f();
        Matrix3f axes = new Matrix3f();
        BoundingVolumesf.pcaObb(p, n, c, axes, h);
        assertContains(p, n, c, axes, h);
        assertEquals(2.0f, h.x, 0.02f);
        assertEquals(1.0f, h.y, 0.02f);
        assertEquals(0.5f, h.z, 0.02f);
        float pcaVol = 8 * h.x * h.y * h.z;
        float vol = BoundingVolumesf.approximateMinimalObb(p, n, c, axes, h);
        assertContains(p, n, c, axes, h);
        assertTrue(vol <= pcaVol * (1 + 1e-6f), "no worse than the PCA box");
        assertEquals(8.0f, vol, 0.24f); // approximate: within 3 % of the optimum here
        // 2D: the minimal rectangle of a rotated rectangle's corners is that rectangle, and beats every angle
        float[] xy = new float[2 * 400];
        double ang = 0.7;
        for (int i = 0; i < 400; i++) {
            double u = i < 4 ? ((i & 1) == 0 ? -3 : 3) : rnd.nextDouble(-3, 3);
            double v = i < 4 ? ((i & 2) == 0 ? -1 : 1) : rnd.nextDouble(-1, 1);
            xy[2 * i] = (float) (u * Math.cos(ang) - v * Math.sin(ang));
            xy[2 * i + 1] = (float) (u * Math.sin(ang) + v * Math.cos(ang));
        }
        Vector2f rc = new Vector2f(), ru = new Vector2f(), rh = new Vector2f();
        float area = BoundingVolumesf.minimalAreaRectangle(xy, 400, rc, ru, rh);
        assertEquals(12.0f, area, 1e-3f); // the 6 x 2 rectangle
        for (double a = 0; a < Math.PI / 2; a += 1e-3) {
            double minU = 1e9, maxU = -1e9, minV = 1e9, maxV = -1e9;
            for (int i = 0; i < 400; i++) {
                double u = xy[2 * i] * Math.cos(a) + xy[2 * i + 1] * Math.sin(a);
                double v = -xy[2 * i] * Math.sin(a) + xy[2 * i + 1] * Math.cos(a);
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
            assertTrue(area <= (maxU - minU) * (maxV - minV) + 1e-3, "angle " + a + " beats the rectangle");
        }
    }

    private static void assertContains(float[] p, int n, Vector3f c, Matrix3f axes, Vector3f h) {
        Vector3f a = new Vector3f();
        for (int k = 0; k < 3; k++) {
            axes.getColumn(k, a);
            float hk = k == 0 ? h.x : k == 1 ? h.y : h.z;
            for (int i = 0; i < n; i++) {
                float d = (p[3 * i] - c.x) * a.x + (p[3 * i + 1] - c.y) * a.y + (p[3 * i + 2] - c.z) * a.z;
                assertTrue(Math.abs(d) <= hk + 1e-4f, "point outside the box along axis " + k);
            }
        }
    }

    @Test
    void kDops() {
        SplittableRandom rnd = new SplittableRandom(6);
        float[] p = new float[3 * 100];
        for (int i = 0; i < p.length; i++) {
            p[i] = (float) rnd.nextDouble(-5, 5);
        }
        float[] mm = new float[26];
        BoundingVolumesf.kDop(p, 100, 6, mm);
        float minX = Float.MAX_VALUE;
        for (int i = 0; i < 100; i++) {
            minX = Math.min(minX, p[3 * i]);
        }
        assertEquals(minX, mm[0], 0f);
        for (int k : new int[] {14, 18, 26}) {
            BoundingVolumesf.kDop(p, 100, k, mm);
            float[] dirs = new float[39];
            int nd = BoundingVolumesf.kDopDirections(k, dirs);
            assertEquals(k / 2, nd);
            for (int dd = 0; dd < nd; dd++) {
                for (int i = 0; i < 100; i++) {
                    float v = p[3 * i] * dirs[3 * dd] + p[3 * i + 1] * dirs[3 * dd + 1] + p[3 * i + 2] * dirs[3 * dd + 2];
                    assertTrue(v >= mm[2 * dd] && v <= mm[2 * dd + 1]);
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> BoundingVolumesf.kDop(p, 100, 8, mm));
    }

    @Test
    void closestPointOnObb() {
        Matrix3f rot = new Matrix3f().rotateXYZ(0.3f, 1.2f, -0.4f);
        Vector3f c = new Vector3f(1, -2, 0.5f), h = new Vector3f(1.5f, 0.5f, 2);
        Vector3f ax = rot.getColumn(0, new Vector3f()), ay = rot.getColumn(1, new Vector3f()), az = rot.getColumn(2, new Vector3f());
        SplittableRandom rnd = new SplittableRandom(7);
        Matrix3f inv = new Matrix3f(rot).transpose();
        for (int i = 0; i < 2000; i++) {
            Vector3f pt = new Vector3f((float) rnd.nextDouble(-6, 6), (float) rnd.nextDouble(-6, 6), (float) rnd.nextDouble(-6, 6));
            Vector3f r = ClosestPointsf.closestPointObb(pt, c, ax, ay, az, h, new Vector3f());
            Vector3f local = inv.transform(new Vector3f(pt).sub(c));
            local.set(Math.max(-h.x, Math.min(h.x, local.x)), Math.max(-h.y, Math.min(h.y, local.y)),
                    Math.max(-h.z, Math.min(h.z, local.z)));
            Vector3f ref = rot.transform(local).add(c);
            assertTrue(r.distance(ref) < 1e-5f);
        }
    }

    private static double sampledTriangleDistance(Vector3f[] a, Vector3f[] b, int n) {
        double best = Double.MAX_VALUE;
        Vector3f pa = new Vector3f(), pb = new Vector3f();
        for (int i = 0; i <= n; i++) {
            for (int j = 0; i + j <= n; j++) {
                float u = (float) i / n, v = (float) j / n;
                pa.set(a[0]).mul(1 - u - v).fma(u, a[1]).fma(v, a[2]);
                for (int k = 0; k <= n; k++) {
                    for (int l = 0; k + l <= n; l++) {
                        float s = (float) k / n, t = (float) l / n;
                        pb.set(b[0]).mul(1 - s - t).fma(s, b[1]).fma(t, b[2]);
                        best = Math.min(best, pa.distance(pb));
                    }
                }
            }
        }
        return best;
    }

    @Test
    void triangleTriangleDistance() {
        SplittableRandom rnd = new SplittableRandom(8);
        Vector3f pa = new Vector3f(), pb = new Vector3f();
        int intersecting = 0;
        for (int t = 0; t < 60; t++) {
            Vector3f[] a = new Vector3f[3], b = new Vector3f[3];
            for (int i = 0; i < 3; i++) {
                a[i] = new Vector3f((float) rnd.nextDouble(-1, 1), (float) rnd.nextDouble(-1, 1), (float) rnd.nextDouble(-1, 1));
                b[i] = new Vector3f((float) rnd.nextDouble(-1, 1), (float) rnd.nextDouble(-1, 1), (float) rnd.nextDouble(-1, 1))
                        .add(t % 3 == 0 ? 0 : 0.8f, 0, 0);
            }
            float d = (float) Math.sqrt(ClosestPointsf.distanceSquaredTriangleTriangle(a[0], a[1], a[2], b[0], b[1], b[2], pa, pb));
            assertEquals(d, pa.distance(pb), 1e-4f, "reported points match the distance");
            double sampled = sampledTriangleDistance(a, b, 24);
            assertTrue(d <= sampled + 1e-4, "distance " + d + " above sampled " + sampled);
            assertTrue(d >= sampled - 0.12, "distance " + d + " far below sampled " + sampled);
            if (d == 0) {
                intersecting++;
            }
        }
        assertTrue(intersecting > 3, "some random pairs should intersect: " + intersecting);
    }

    @Test
    void segmentPiercingATriangleHasDistanceZero() {
        Vector3f v0 = new Vector3f(-1, -1, 0), v1 = new Vector3f(1, -1, 0), v2 = new Vector3f(0, 1, 0);
        Vector3f p = new Vector3f(0, 0, -1), q = new Vector3f(0.1f, -0.2f, 1);
        Vector3f s = new Vector3f(), t = new Vector3f();
        assertEquals(0f, ClosestPointsf.distanceSquaredSegmentTriangle(p, q, v0, v1, v2, s, t), 0f);
        assertEquals(0f, s.z, 1e-6f);
        // the inherited Intersectionf routine returned 0.2 here: it compared edges and end points only
        assertEquals(0f, Intersectionf.findClosestPointsLineSegmentTriangle(p.x, p.y, p.z, q.x, q.y, q.z, v0.x, v0.y,
                v0.z, v1.x, v1.y, v1.z, v2.x, v2.y, v2.z, s, t), 0f);
        assertEquals(0d, org.dynamisengine.vectrix.geometry.Intersectiond.findClosestPointsLineSegmentTriangle(p.x,
                p.y, p.z, q.x, q.y, q.z, v0.x, v0.y, v0.z, v1.x, v1.y, v1.z, v2.x, v2.y, v2.z,
                new org.dynamisengine.vectrix.core.Vector3d(), new org.dynamisengine.vectrix.core.Vector3d()), 0d);
    }

    /** Signed distance from {@code p} to the capsule around {@code (a, b)}. */
    private static double capsuleSdf(Vector3f p, Vector3f a, Vector3f b, float r) {
        Vector3f pa = new Vector3f(p).sub(a), ba = new Vector3f(b).sub(a);
        float h = Math.max(0, Math.min(1, pa.dot(ba) / ba.dot(ba)));
        return pa.sub(ba.mul(h)).length() - r;
    }

    @Test
    void rayCapsuleAgainstSphereTracing() {
        Vector3f a = new Vector3f(-1, 0.2f, 0.3f), b = new Vector3f(1.5f, -0.4f, 0.1f);
        float r = 0.6f;
        SplittableRandom rnd = new SplittableRandom(9);
        int hits = 0;
        for (int i = 0; i < 3000; i++) {
            Vector3f o = new Vector3f((float) rnd.nextDouble(-6, 6), (float) rnd.nextDouble(-6, 6), (float) rnd.nextDouble(-6, 6));
            Vector3f target = new Vector3f((float) rnd.nextDouble(-2, 2), (float) rnd.nextDouble(-1.5, 1.5), (float) rnd.nextDouble(-1.5, 1.5));
            Vector3f dir = new Vector3f(target).sub(o).normalize();
            float t = ClosestPointsf.intersectRayCapsule(o, dir, a, b, r);
            // sphere tracing along the ray
            double tt = 0;
            double hit = -1;
            if (capsuleSdf(o, a, b, r) <= 0) {
                hit = 0;
            } else {
                for (int k = 0; k < 2000 && tt < 40; k++) {
                    double dist = capsuleSdf(new Vector3f(dir).mul((float) tt).add(o), a, b, r);
                    if (dist < 1e-6) {
                        hit = tt;
                        break;
                    }
                    tt += dist;
                }
            }
            if (hit < 0) {
                assertTrue(t < 0 || capsuleSdf(new Vector3f(dir).mul(t).add(o), a, b, r) < 1e-3, "spurious hit " + i);
            } else {
                hits++;
                assertEquals(hit, t, 2e-3, "ray " + i);
            }
        }
        assertTrue(hits > 500, "too few hits: " + hits);
    }

    @Test
    void vertexNormalsTangentsAndQuality() {
        // cube with shared corners: angle-weighted normals are exactly the diagonals
        float[] pos = {-1, -1, -1, 1, -1, -1, 1, 1, -1, -1, 1, -1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1};
        int[] idx = {0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4, 3, 7, 6, 3, 6, 2, 0, 4, 7, 0, 7, 3, 1, 2, 6, 1, 6, 5};
        float[] n = new float[24];
        MeshMath.vertexNormals(pos, idx, 12, true, n);
        float inv = (float) (1 / Math.sqrt(3));
        for (int v = 0; v < 8; v++) {
            assertEquals(Math.signum(pos[3 * v]) * inv, n[3 * v], 1e-6f, "vertex " + v);
            assertEquals(Math.signum(pos[3 * v + 2]) * inv, n[3 * v + 2], 1e-6f);
        }
        MeshMath.vertexNormals(pos, idx, 12, false, n);
        for (int v = 0; v < 8; v++) {
            assertEquals(1f, new Vector3f(n[3 * v], n[3 * v + 1], n[3 * v + 2]).length(), 1e-6f);
        }
        // planar grid in XY with uv = (x, y): tangent +x, right-handed; mirrored uv: tangent -x, left-handed
        float[] gp = {0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0};
        float[] gn = {0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1};
        int[] gi = {0, 1, 2, 0, 2, 3};
        float[] tan = new float[16];
        MeshMath.vertexTangents(gp, gn, new float[] {0, 0, 1, 0, 1, 1, 0, 1}, gi, 2, tan);
        for (int v = 0; v < 4; v++) {
            assertEquals(1f, tan[4 * v], 1e-6f);
            assertEquals(1f, tan[4 * v + 3], 0f);
        }
        MeshMath.vertexTangents(gp, gn, new float[] {0, 0, -1, 0, -1, 1, 0, 1}, gi, 2, tan);
        for (int v = 0; v < 4; v++) {
            assertEquals(-1f, tan[4 * v], 1e-6f);
            assertEquals(-1f, tan[4 * v + 3], 0f);
        }
        // curved surface: tangents are unit and perpendicular to the normals
        float[] sp = {0, 0, 1, 1, 0, 0.8f, 1, 1, 0.5f, 0, 1, 0.9f}, sn = new float[12];
        MeshMath.vertexNormals(sp, gi, 2, true, sn);
        MeshMath.vertexTangents(sp, sn, new float[] {0, 0, 1, 0, 1, 1, 0, 1}, gi, 2, tan);
        for (int v = 0; v < 4; v++) {
            Vector3f t = new Vector3f(tan[4 * v], tan[4 * v + 1], tan[4 * v + 2]);
            assertEquals(1f, t.length(), 1e-5f);
            assertEquals(0f, t.dot(sn[3 * v], sn[3 * v + 1], sn[3 * v + 2]), 1e-5f);
        }
        // quality metrics
        Vector3f e0 = new Vector3f(0, 0, 0), e1 = new Vector3f(1, 0, 0), e2 = new Vector3f(0.5f, (float) (Math.sqrt(3) / 2), 0);
        assertEquals(1f, MeshMath.triangleAspectRatio(e0, e1, e2), 1e-5f);
        assertEquals(1f, MeshMath.triangleRadiusRatio(e0, e1, e2), 1e-5f);
        assertEquals((float) (Math.PI / 3), MeshMath.triangleMinAngle(e0, e1, e2), 1e-5f);
        Vector3f r2 = new Vector3f(0, 1, 0);
        assertEquals((float) ((Math.sqrt(2) + 1) / Math.sqrt(3)), MeshMath.triangleAspectRatio(e0, e1, r2), 1e-5f);
        assertEquals(0.828427f, MeshMath.triangleRadiusRatio(e0, e1, r2), 1e-5f);
        assertEquals((float) (Math.PI / 4), MeshMath.triangleMinAngle(e0, e1, r2), 1e-5f);
        Vector3f collinear = new Vector3f(2, 0, 0);
        assertEquals(Float.POSITIVE_INFINITY, MeshMath.triangleAspectRatio(e0, e1, collinear), 0f);
        assertEquals(0f, MeshMath.triangleRadiusRatio(e0, e1, collinear), 0f);
    }
}
