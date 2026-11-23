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

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.camera.DepthRange;
import org.dynamisengine.vectrix.camera.Projectionsf;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Matrix4fc;
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.geometry.FrustumIntersection;
import org.dynamisengine.vectrix.geometry.FrustumPlanes;
import org.dynamisengine.vectrix.geometry.GeometryUtils;
import org.dynamisengine.vectrix.geometry.Intersectiond;
import org.dynamisengine.vectrix.geometry.Intersectionf;
import org.dynamisengine.vectrix.geometry.PolygonsIntersection;
import org.dynamisengine.vectrix.geometry.RayAabIntersection;
import org.junit.jupiter.api.Test;

/** Second audit of the JOML-inherited geometry (2.5.0). */
class GeometryAudit2Test {

    /** Closest point by dense barycentric sampling: an upper bound on the true distance. */
    private static double sampledDistance(Vector3d a, Vector3d b, Vector3d c, Vector3d p) {
        double best = Double.POSITIVE_INFINITY;
        int n = 200;
        for (int i = 0; i <= n; i++) {
            for (int j = 0; i + j <= n; j++) {
                double u = (double) i / n, v = (double) j / n;
                Vector3d q = new Vector3d(a).mul(1 - u - v).add(new Vector3d(b).mul(u)).add(new Vector3d(c).mul(v));
                best = Math.min(best, q.distance(p));
            }
        }
        return best;
    }

    @Test
    void closestPointOnTriangleIsExactOnSlivers() {
        // the reviewer's sliver: p is inside, so it is its own closest point (was 1.21 away in float)
        Vector2f r = new Vector2f();
        assertEquals(Intersectionf.POINT_ON_TRIANGLE_FACE, Intersectionf.findClosestPointOnTriangle(5.696239f, 6.0664186f, -1.7377167f,
                -0.3724271f, -3.5903366f, -1.9831574f, 3.8267272f, 4.4468856f, r));
        assertEquals(0f, r.distance(3.8267272f, 4.4468856f), 0f);
        Vector3f r3 = new Vector3f();
        Intersectionf.findClosestPointOnTriangle(5.696239f, 6.0664186f, 0, -1.7377167f, -0.3724271f, 0, -3.5903366f, -1.9831574f, 0, 3.8267272f,
                4.4468856f, 0.5f, r3);
        assertTrue(r3.distance(3.8267272f, 4.4468856f, 0) < 1e-6, "3D projection " + r3);
        // the knock-on: a sphere 0.1 above that face is touched
        assertTrue(Intersectionf.intersectSphereTriangle(3.8267272f, 4.4468856f, 0.1f, 0.25f, 5.696239f, 6.0664186f, 0, -1.7377167f,
                -0.3724271f, 0, -3.5903366f, -1.9831574f, 0, new Vector3f()) != 0, "sphere meets the sliver face");
        SplittableRandom rnd = new SplittableRandom(1);
        Vector3d res = new Vector3d();
        for (int i = 0; i < 400; i++) {
            Vector3d a = new Vector3d(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian());
            Vector3d b = new Vector3d(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian());
            // every other triangle is a sliver: c nearly on the line ab
            Vector3d c = i % 2 == 0 ? new Vector3d(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian())
                    : new Vector3d(a).lerp(b, rnd.nextDouble(-0.5, 1.5)).add(rnd.nextGaussian() * 1e-3, rnd.nextGaussian() * 1e-3, rnd.nextGaussian() * 1e-3);
            Vector3d p = new Vector3d(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).mul(1.5);
            int code = Intersectiond.findClosestPointOnTriangle(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, p.x, p.y, p.z, res);
            double got = res.distance(p), sampled = sampledDistance(a, b, c, p);
            assertTrue(got <= sampled + 1e-12, "case " + i + " code " + code + ": " + got + " > sampled " + sampled);
            // the result lies on the triangle: its distance to the triangle's plane region is ~0
            assertTrue(sampledDistance(a, b, c, res) < 0.02, "result on the triangle, case " + i);
            if (code == Intersectiond.POINT_ON_TRIANGLE_VERTEX_0) {
                assertEquals(a, res);
            }
        }
    }

    @Test
    void infiniteReverseZFarPlaneKeepsEverything() {
        Matrix4f m = Projectionsf.reverseZInfinite(1f, 1f, 0.1f, new Matrix4f());
        for (boolean spheres : new boolean[] {true, false}) {
            FrustumIntersection fi = new FrustumIntersection().set(m, spheres, DepthRange.REVERSED_ZERO_TO_ONE);
            assertTrue(fi.testPoint(0, 0, -10) && fi.testPoint(0, 0, -1e30f), "visible points, spheres " + spheres);
            assertTrue(fi.testSphere(0, 0, -10, 1), "sphere, spheres " + spheres);
            assertTrue(fi.testAab(-1, -1, -11, 1, 1, -9), "box, spheres " + spheres);
            assertFalse(fi.testPoint(0, 0, -0.05f), "in front of the near plane");
        }
        Vector4f far = m.frustumPlane(Matrix4fc.PLANE_PZ, DepthRange.REVERSED_ZERO_TO_ONE, new Vector4f());
        assertTrue(far.equals(new Vector4f(0, 0, 0, 1)), far.toString());
        FrustumPlanes fp = new FrustumPlanes().set(m, true, DepthRange.REVERSED_ZERO_TO_ONE);
        assertEquals(1f, fp.w[FrustumPlanes.PLANE_PZ], 0f);
        assertEquals(0f, fp.z[FrustumPlanes.PLANE_PZ], 0f);
    }

    @Test
    void rayBoxEdgeCases() {
        // tiny directions: the sign of +MIN_VALUE is positive, and overflowing slopes fall back to the slab test
        assertTrue(new RayAabIntersection(-5, 0, 0, Float.MIN_VALUE, 0, 0).test(-1, -1, -1, 1, 1, 1));
        assertFalse(new RayAabIntersection(5, 0, 0, Float.MIN_VALUE, 0, 0).test(-1, -1, -1, 1, 1, 1));
        assertTrue(new RayAabIntersection(0, 0, 0, 1e-39f, 1e-39f, 1e-39f).test(-1, -1, -1, 1, 1, 1), "origin inside");
        assertTrue(new RayAabIntersection(0.5f, 0, 0, 0, 0, 0).test(-1, -1, -1, 1, 1, 1), "zero direction inside");
        assertTrue(new RayAabIntersection(-7.55587f, 7.272836f, 0.38911933f, 7.6068296f, -7.476826f, 1.8025819E-38f)
                .test(-1, -1, -1, 1, 1, 1), "the reviewer's tiny-component ray");
        SplittableRandom r = new SplittableRandom(2);
        RayAabIntersection ray = new RayAabIntersection();
        for (int i = 0; i < 100000; i++) {
            float ox = (float) r.nextDouble(-8, 8), oy = (float) r.nextDouble(-8, 8), oz = (float) r.nextDouble(-8, 8);
            float dx = (float) r.nextGaussian(), dy = (float) r.nextGaussian(), dz = (float) (r.nextGaussian() * Math.pow(10, -r.nextInt(0, 45)));
            ray.set(ox, oy, oz, dx, dy, dz);
            assertEquals(Intersectionf.testRayAab(ox, oy, oz, dx, dy, dz, -1, -1, -1, 1, 1, 1), ray.test(-1, -1, -1, 1, 1, 1), "ray " + i);
        }
        // NaN never hits
        assertFalse(Intersectionf.testRayAab(Float.NaN, 0, 0, 1, 0, 0, -1, -1, -1, 1, 1, 1));
        assertFalse(Intersectiond.testRayAab(Double.NaN, 0, 0, 1, 0, 0, -1, -1, -1, 1, 1, 1));
        assertFalse(Intersectiond.intersectRayAab(0, 0, -5, 0, Double.NaN, 1, -1, -1, -1, 1, 1, 1, new Vector2d()));
        assertFalse(Intersectionf.testRayAar(Float.NaN, 0, 1, 0, -1, -1, 1, 1));
    }

    @Test
    void frustumSpheresAndMasks() {
        Matrix4f vp = new Matrix4f().perspective(1.1f, 1.6f, 0.1f, 100f).lookAt(3, 2, 5, 0, 0, 0, 0, 1, 0);
        FrustumIntersection normalized = new FrustumIntersection().set(vp, true);
        FrustumIntersection raw = new FrustumIntersection().set(vp, false);
        SplittableRandom r = new SplittableRandom(3);
        for (int i = 0; i < 20000; i++) {
            float x = (float) r.nextDouble(-20, 20), y = (float) r.nextDouble(-20, 20), z = (float) r.nextDouble(-20, 20), rad = (float) r.nextDouble(0.01, 5);
            if (Math.abs(normalized.distanceToPlane(x, y, z, x, y, z, FrustumIntersection.PLANE_NX)) < 1e-3) {
                continue;
            }
            assertEquals(normalized.testSphere(x, y, z, rad), raw.testSphere(x, y, z, rad), "testSphere " + i);
            assertEquals(normalized.intersectSphere(x, y, z, rad), raw.intersectSphere(x, y, z, rad), "intersectSphere " + i);
            assertEquals(normalized.distanceToPlane(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, FrustumIntersection.PLANE_PY),
                    raw.distanceToPlane(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, FrustumIntersection.PLANE_PY), 1e-3f * (1 + Math.abs(x) + Math.abs(y) + Math.abs(z)));
        }
        // a mask only skips planes for culling: INSIDE still refers to all six planes (conservative, as documented)
        Vector3f eye = new Vector3f(3, 2, 5), toward = new Vector3f(0, 0, 0).sub(eye).normalize();
        Vector3f c = new Vector3f(toward).mul(10).add(eye);
        assertEquals(FrustumIntersection.INSIDE, normalized.intersectAab(c.x - 0.1f, c.y - 0.1f, c.z - 0.1f, c.x + 0.1f, c.y + 0.1f, c.z + 0.1f,
                FrustumIntersection.PLANE_MASK_NX));
    }

    @Test
    void tangentFramesAndNormals() {
        Vector3f v1 = new Vector3f(1, 2, 3), v2 = new Vector3f(3, 2, 3), v3 = new Vector3f(1, 2, 6);
        Vector2f uv1 = new Vector2f(0, 0), uv2 = new Vector2f(1, 0), uv3 = new Vector2f(0, 1);
        for (int alias = 0; alias < 3; alias++) {
            Vector3f a1 = new Vector3f(v1), a2 = new Vector3f(v2), a3 = new Vector3f(v3), b = new Vector3f();
            Vector3f t = alias == 0 ? a1 : alias == 1 ? a2 : a3;
            GeometryUtils.tangentBitangent(a1, uv1, a2, uv2, a3, uv3, t, b);
            assertTrue(b.distance(0, 0, 1) < 1e-7 && t.distance(1, 0, 0) < 1e-7, "aliasing input " + alias + ": T " + t + " B " + b);
        }
        Vector3f n = new Vector3f();
        GeometryUtils.normal(3.2586508f, -0.4499593f, -0.13984048f, 2.530347f, -1.5665654f, 0.2909896f, 2.7080274f, -1.294153f, 0.18588221f, n);
        assertTrue(n.distance(0.5159076f, -0.5798104f, -0.6306023f) < 2e-6, "thin-triangle normal " + n);
        Vector3d nd = new Vector3d();
        GeometryUtils.normal(new Vector3d(0, 0, 0), new Vector3d(1e200, 0, 0), new Vector3d(0, 1e200, 0), nd);
        assertTrue(nd.equals(0, 0, 1), "double normal at 1e200: " + nd);
        // float 2D intersections are computed in double
        float t = Intersectionf.intersectRayLineSegment(1.2413232f, 4.3614383f, -0.031537212f, 0.015624778f, -3.520547f, -0.34687012f, 1.703124f, 4.869734f);
        assertEquals(1.0000034184, t, 2e-7);
    }

    @Test
    void polygonStartsAreValidated() {
        float[] squares = {0, 0, 2, 0, 2, 2, 0, 2, 1, 1, 3, 1, 3, 3, 1, 3};
        assertThrows(IllegalArgumentException.class, () -> new PolygonsIntersection(squares, new int[] {0, 4}, 8), "start 0");
        assertThrows(IllegalArgumentException.class, () -> new PolygonsIntersection(squares, new int[] {7}, 8), "start at the last vertex");
        assertThrows(IllegalArgumentException.class, () -> new PolygonsIntersection(squares, new int[] {4, 5}, 8), "one-vertex polygon");
        // overlapping squares cancel where they overlap: the documented even-odd rule
        PolygonsIntersection two = new PolygonsIntersection(squares, new int[] {4}, 8);
        assertFalse(two.testPoint(1.5f, 1.5f));
        assertTrue(two.testPoint(0.5f, 0.5f) && two.testPoint(2.5f, 2.5f));
    }
}
