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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.camera.ClusterGridd;
import org.dynamisengine.vectrix.camera.ClusterGridf;
import org.dynamisengine.vectrix.core.DepthRange;
import org.dynamisengine.vectrix.camera.PhysicalCamerad;
import org.dynamisengine.vectrix.camera.Projectionsd;
import org.dynamisengine.vectrix.camera.Projectionsf;
import org.dynamisengine.vectrix.camera.ShadowCascadesd;
import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.geometry.BoundingVolumesd;
import org.dynamisengine.vectrix.sampling.HaltonSequence;
import org.junit.jupiter.api.Test;

/** Projections, depth conversions, cascades, cluster grids and the thin-lens camera against exact identities. */
class CameraMathTest {

    private static Vector4d clip(Matrix4d p, double x, double y, double z) {
        return p.transform(new Vector4d(x, y, z, 1));
    }

    private static double ndcZ(Matrix4d p, double d) {
        Vector4d c = clip(p, 0, 0, -d);
        return c.z / c.w;
    }

    @Test
    void perspectiveMapsPlanesExactlyInEveryRange() {
        double fov = 1.1, aspect = 16.0 / 9, n = 0.1, f = 500;
        for (DepthRange r : DepthRange.values()) {
            Matrix4d p = Projectionsd.perspective(fov, aspect, n, f, r, new Matrix4d());
            assertEquals(r.nearNdc(), ndcZ(p, n), 1e-15, r + " near");
            assertEquals(r.farNdc(), ndcZ(p, f), 1e-12, r + " far");
            assertEquals(n, Projectionsd.nearPlane(p, r), 1e-15 * n, r + " near extraction");
            assertEquals(f, Projectionsd.farPlane(p, r), 1e-12 * f, r + " far extraction");
            Matrix4d inf = Projectionsd.perspective(fov, aspect, n, Double.POSITIVE_INFINITY, r, new Matrix4d());
            assertEquals(r.nearNdc(), ndcZ(inf, n), 1e-15, r + " infinite near");
            assertEquals(r.farNdc(), ndcZ(inf, 1e300), 1e-15, r + " infinity");
            assertEquals(Double.POSITIVE_INFINITY, Projectionsd.farPlane(inf, r), r + " infinite far extraction");
            assertEquals(n, Projectionsd.nearPlane(inf, r), 1e-15 * n);
            for (double d = n; d < 1e6; d *= 1.37) {
                assertEquals(d, Projectionsd.linearDepth(Projectionsd.ndcDepth(inf, d), n, Double.POSITIVE_INFINITY,
                        r), 1e-9 * d, r + " infinite round trip at " + d);
                if (d <= f) {
                    assertEquals(d, Projectionsd.linearDepth(Projectionsd.ndcDepth(p, d), n, f, r), 1e-9 * d,
                            r + " round trip at " + d);
                }
            }
        }
        // reverse-Z infinite is exactly near / d, with no epsilon
        Matrix4d rz = Projectionsd.perspective(fov, aspect, n, Double.POSITIVE_INFINITY,
                DepthRange.REVERSED_ZERO_TO_ONE, new Matrix4d());
        assertEquals(0.0, rz.m22(), 0.0);
        assertEquals(n, rz.m32(), 0.0);
        // the finite ranges agree with core's perspective
        assertTrue(Projectionsd.perspective(fov, aspect, n, f, DepthRange.ZERO_TO_ONE, new Matrix4d())
                .equals(new Matrix4d().perspective(fov, aspect, n, f, true), 1e-15));
        assertTrue(Projectionsd.perspective(fov, aspect, n, f, DepthRange.NEGATIVE_ONE_TO_ONE, new Matrix4d())
                .equals(new Matrix4d().perspective(fov, aspect, n, f, false), 1e-15));
    }

    @Test
    void reverseZKeepsFloatDepthDistinct() {
        Matrix4f rev = Projectionsf.reverseZInfinite(1.0f, 1.5f, 0.1f, new Matrix4f());
        Matrix4f std = Projectionsf.perspective(1.0f, 1.5f, 0.1f, 20000f, DepthRange.ZERO_TO_ONE, new Matrix4f());
        Set<Float> revDepths = new HashSet<>(), stdDepths = new HashSet<>();
        int count = 0;
        for (float d = 1000f; d < 10000f; d += 0.5f, count++) {
            revDepths.add(Projectionsf.ndcDepth(rev, d));
            stdDepths.add(Projectionsf.ndcDepth(std, d));
        }
        System.out.printf("float depth: %d distances, reverse-Z keeps %d distinct, standard [0,1] %d%n", count,
                revDepths.size(), stdDepths.size());
        assertEquals(count, revDepths.size(), "reverse-Z resolves every 0.5 m step out to 10 km");
        assertTrue(stdDepths.size() < count / 10);
    }

    @Test
    void clipCorrectionFlipAndJitter() {
        double fov = 0.9, aspect = 1.3, n = 0.5, f = 80;
        Matrix4d gl = Projectionsd.perspective(fov, aspect, n, f, DepthRange.NEGATIVE_ONE_TO_ONE, new Matrix4d());
        Matrix4d vk = Projectionsd.vulkanClipCorrection(new Matrix4d()).mul(gl);
        Matrix4d expected = Projectionsd.flipY(
                Projectionsd.perspective(fov, aspect, n, f, DepthRange.ZERO_TO_ONE, new Matrix4d()), new Matrix4d());
        assertTrue(vk.equals(expected, 1e-15), vk + "\n" + expected);
        SplittableRandom rnd = new SplittableRandom(1);
        Matrix4d ortho = new Matrix4d().ortho(-3, 4, -2, 5, 0.1, 30);
        for (Matrix4d p : new Matrix4d[] {gl, ortho, Projectionsd.obliqueNearPlane(gl, new Vector4d(0.1, 0.2, -1,
                -3).normalize3(), DepthRange.NEGATIVE_ONE_TO_ONE, new Matrix4d())}) {
            double jx = rnd.nextDouble(-0.01, 0.01), jy = rnd.nextDouble(-0.01, 0.01);
            Matrix4d j = Projectionsd.applyJitter(p, jx, jy, new Matrix4d());
            Matrix4d fy = Projectionsd.flipY(p, new Matrix4d());
            for (int i = 0; i < 100; i++) {
                double x = rnd.nextDouble(-2, 2), y = rnd.nextDouble(-2, 2), z = rnd.nextDouble(-20, -1);
                Vector4d a = clip(p, x, y, z), b = clip(j, x, y, z), c = clip(fy, x, y, z);
                assertEquals(a.x / a.w + jx, b.x / b.w, 1e-14);
                assertEquals(a.y / a.w + jy, b.y / b.w, 1e-14);
                assertEquals(a.z / a.w, b.z / b.w, 1e-15);
                assertEquals(-a.y / a.w, c.y / c.w, 1e-15);
            }
        }
        Vector2d jit = Projectionsd.haltonJitter(0, 8, 1920, 1080, new Vector2d());
        assertEquals(0.0, jit.x, 0.0); // Halton(1, 2) = 1/2
        assertEquals(2 * (1.0 / 3 - 0.5) / 1080, jit.y, 1e-18);
        for (int frame = 0; frame < 64; frame++) {
            Vector2d a = Projectionsd.haltonJitter(frame, 16, 1920, 1080, new Vector2d());
            assertEquals(a, Projectionsd.haltonJitter(frame + 16, 16, 1920, 1080, new Vector2d()));
            assertTrue(Math.abs(a.x) * 1920 / 2 <= 0.5 && Math.abs(a.y) * 1080 / 2 <= 0.5);
            assertEquals(2 * (HaltonSequence.haltonDouble(frame + 1, 2) - 0.5) / 1920,
                    Projectionsd.haltonJitter(frame, 0, 1920, 1080, new Vector2d()).x, 0.0);
        }
    }

    @Test
    void obliqueNearPlaneClipsExactlyAtThePlane() {
        SplittableRandom rnd = new SplittableRandom(2);
        double n = 0.2, f = 100;
        for (DepthRange r : DepthRange.values()) {
            for (double far : new double[] {f, Double.POSITIVE_INFINITY}) {
                Matrix4d p = Projectionsd.perspective(1.2, 1.4, n, far, r, new Matrix4d());
                // a tilted mirror plane 6 units ahead, keeping the side away from the camera
                Vector4d plane = new Vector4d(0.3, -0.2, -1, 0);
                double len = Math.sqrt(plane.x * plane.x + plane.y * plane.y + plane.z * plane.z);
                plane.set(plane.x / len, plane.y / len, plane.z / len, 0);
                plane.w = -(plane.z * -6.0);
                plane.w = -Math.abs(plane.w);
                Matrix4d o = Projectionsd.obliqueNearPlane(p, plane, r, new Matrix4d());
                int kept = 0, clipped = 0;
                for (int i = 0; i < 4000; i++) {
                    double z = -rnd.nextDouble(n, 60);
                    double x = rnd.nextDouble(-1, 1) * -z * 0.5, y = rnd.nextDouble(-1, 1) * -z * 0.5;
                    double side = plane.x * x + plane.y * y + plane.z * z + plane.w;
                    Vector4d c = clip(o, x, y, z), c0 = clip(p, x, y, z);
                    assertEquals(c0.x, c.x, 0.0);
                    assertEquals(c0.y, c.y, 0.0);
                    assertEquals(c0.w, c.w, 0.0);
                    double nearSide = r == DepthRange.NEGATIVE_ONE_TO_ONE ? c.z + c.w
                            : r == DepthRange.ZERO_TO_ONE ? c.z : c.w - c.z;
                    if (Math.abs(side) > 1e-9) {
                        assertEquals(side > 0, nearSide > 0, r + " point on side " + side);
                        if (side > 0) {
                            kept++;
                        } else {
                            clipped++;
                        }
                    }
                }
                assertTrue(kept > 100 && clipped > 100, r + ": " + kept + " kept, " + clipped + " clipped");
            }
        }
    }

    @Test
    void cascadesSplitsAndStableFitting() {
        double[] s = ShadowCascadesd.practicalSplits(0.1, 400, 4, 0.7, new double[5]);
        assertEquals(0.1, s[0], 0.0);
        assertEquals(400, s[4], 0.0);
        for (int i = 1; i < 5; i++) {
            assertTrue(s[i] > s[i - 1]);
        }
        double[] log = ShadowCascadesd.practicalSplits(1, 1000, 3, 1, new double[4]);
        assertEquals(10, log[1], 1e-12);
        assertEquals(100, log[2], 1e-11);
        double[] uni = ShadowCascadesd.practicalSplits(1, 1000, 3, 0, new double[4]);
        assertEquals(334, uni[1], 1e-12);
        // the slice sphere is the minimal sphere of the slice's corners, also when the far circle suffices
        for (double[] c : new double[][] {{0.6, 0.4, 0.1, 20}, {0.6, 0.4, 20, 80}, {1.8, 1.0, 10, 11}, {0.2, 0.1, 50,
            51}}) {
            Vector4d sph = ShadowCascadesd.sliceSphere(c[0], c[1], c[2], c[3], new Vector4d());
            double[] pts = new double[24];
            int k = 0;
            for (double d : new double[] {c[2], c[3]}) {
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sy = -1; sy <= 1; sy += 2) {
                        pts[k++] = sx * c[0] * d;
                        pts[k++] = sy * c[1] * d;
                        pts[k++] = -d;
                    }
                }
            }
            Vector4d ref = BoundingVolumesd.minimalSphere(pts, 8, new Vector4d());
            assertEquals(ref.w, sph.w, 1e-12 * ref.w, "radius " + java.util.Arrays.toString(c));
            assertEquals(ref.z, sph.z, 1e-9 * ref.w, "center");
        }
        // stable fitting: as the camera moves, world points keep their sub-texel position in the shadow map
        int res = 2048;
        Vector3d dir = new Vector3d(0.3, -1, 0.2).normalize(), up = new Vector3d(0, 1, 0.001).normalize();
        Vector3d world = new Vector3d(12.34, 0.5, -7.89);
        double radius = 25;
        Matrix4d view = new Matrix4d(), proj = new Matrix4d();
        Double frac = null;
        for (int frame = 0; frame < 50; frame++) {
            Vector3d center = new Vector3d(10 + frame * 0.0731, 2 + frame * 0.013, -5 - frame * 0.051);
            ShadowCascadesd.stableLightMatrices(center, radius, dir, up, 50, res, DepthRange.ZERO_TO_ONE, view, proj);
            Vector4d c = new Matrix4d(proj).mul(view).transform(new Vector4d(world, 1));
            double tx = (c.x * 0.5 + 0.5) * res;
            double fr = tx - Math.floor(tx);
            if (frac != null) {
                assertEquals(frac, fr, 1e-6, "frame " + frame);
            }
            frac = fr;
            // the whole sphere is inside the map, and in depth range
            Matrix4d vp = new Matrix4d(proj).mul(view);
            for (Vector3d axis : new Vector3d[] {new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)}) {
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    Vector4d e = vp.transform(new Vector4d(new Vector3d(axis).mul(sgn * radius).add(center), 1));
                    assertTrue(Math.abs(e.x) <= 1 && Math.abs(e.y) <= 1 && e.z >= 0 && e.z <= 1, "sphere extreme");
                }
            }
        }
    }

    @Test
    void clusterGrid() {
        int slices = 24, tx = 16, ty = 9;
        double n = 0.1, f = 1000;
        assertEquals(n, ClusterGridd.sliceDepth(0, slices, n, f), 0.0);
        assertEquals(f, ClusterGridd.sliceDepth(slices, slices, n, f), 0.0);
        for (int k = 0; k < slices; k++) {
            double d0 = ClusterGridd.sliceDepth(k, slices, n, f);
            assertEquals(k, ClusterGridd.sliceIndex(d0, slices, n, f), "boundary " + k);
            if (k > 0) {
                assertEquals(k - 1, ClusterGridd.sliceIndex(Math.nextDown(d0), slices, n, f), "below boundary " + k);
            }
            float d0f = ClusterGridf.sliceDepth(k, slices, (float) n, (float) f);
            assertEquals(k, ClusterGridf.sliceIndex(d0f, slices, (float) n, (float) f), "float boundary " + k);
            if (k > 0) {
                assertEquals(k - 1, ClusterGridf.sliceIndex(Math.nextDown(d0f), slices, (float) n, (float) f));
            }
        }
        double tanX = 0.8, tanY = 0.45;
        SplittableRandom rnd = new SplittableRandom(3);
        Vector3d min = new Vector3d(), max = new Vector3d();
        for (int i = 0; i < 20000; i++) {
            double d = n * Math.pow(f / n, rnd.nextDouble());
            Vector3d p = new Vector3d(rnd.nextDouble(-1, 1) * d * tanX, rnd.nextDouble(-1, 1) * d * tanY, -d);
            int idx = ClusterGridd.clusterOf(p, tanX, tanY, tx, ty, slices, n, f);
            assertTrue(idx >= 0);
            int x = idx % tx, y = idx / tx % ty, k = idx / (tx * ty);
            ClusterGridd.clusterAabb(x, y, k, tx, ty, slices, n, f, tanX, tanY, min, max);
            assertTrue(p.x >= min.x - 1e-12 && p.x <= max.x + 1e-12 && p.y >= min.y - 1e-12 && p.y <= max.y + 1e-12
                    && p.z >= min.z - 1e-12 && p.z <= max.z + 1e-12, "point outside its cluster");
            assertTrue(ClusterGridd.sphereIntersectsAabb(p, 0, min, max));
        }
        assertEquals(-1, ClusterGridd.clusterOf(new Vector3d(0, 0, -2000), tanX, tanY, tx, ty, slices, n, f));
        assertEquals(-1, ClusterGridd.clusterOf(new Vector3d(5, 0, -1), tanX, tanY, tx, ty, slices, n, f));
        // spot lights: never a false negative, and a useful cull rate
        Vector3d apex = new Vector3d(1, 2, -3), axis = new Vector3d(0.3, -0.2, -1).normalize();
        double half = 0.4, range = 10;
        int culled = 0, trials = 4000;
        for (int t = 0; t < trials; t++) {
            Vector3d c = new Vector3d(rnd.nextDouble(-12, 14), rnd.nextDouble(-10, 12), rnd.nextDouble(-16, 6));
            double r = rnd.nextDouble(0.1, 2);
            boolean test = ClusterGridd.coneIntersectsSphere(apex, axis, Math.cos(half), Math.sin(half), range, c, r);
            if (!test) {
                culled++;
                for (int s2 = 0; s2 < 300; s2++) {
                    Vector3d q = new Vector3d(rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1));
                    if (q.lengthSquared() > 1) {
                        continue;
                    }
                    q.mul(r).add(c);
                    Vector3d v = new Vector3d(q).sub(apex);
                    boolean inside = v.length() <= range && v.dot(axis) >= Math.cos(half) * v.length();
                    assertFalse(inside, "culled sphere reaches into the cone");
                }
            }
        }
        assertTrue(culled > trials / 2, "cull rate " + culled);
    }

    @Test
    void thinLensCamera() {
        double f = 0.05, sensor = 0.036;
        double fov = PhysicalCamerad.fieldOfView(f, sensor);
        assertEquals(Math.toRadians(39.5977), fov, 1e-6);
        assertEquals(f, PhysicalCamerad.focalLength(fov, sensor), 1e-15);
        assertEquals(0.0, PhysicalCamerad.circleOfConfusion(f, 2, 2, 2), 0.0);
        assertEquals(0.025 * 2 / 4 * 0.05 / 1.95, PhysicalCamerad.circleOfConfusion(f, 2, 2, 4), 1e-18);
        double coc = 0.00003, h = PhysicalCamerad.hyperfocalDistance(f, 8, coc);
        assertEquals(0.0025 / (8 * coc) + 0.05, h, 1e-12);
        assertEquals(h / 2, PhysicalCamerad.depthOfFieldNear(f, 8, coc, h), 1e-12);
        assertEquals(Double.POSITIVE_INFINITY, PhysicalCamerad.depthOfFieldFar(f, 8, coc, h), 0.0);
        // the limits are exactly where the circle of confusion reaches the acceptable size
        for (double s : new double[] {1, 3, 7}) {
            double dn = PhysicalCamerad.depthOfFieldNear(f, 8, coc, s), df = PhysicalCamerad.depthOfFieldFar(f, 8, coc, s);
            assertEquals(coc, PhysicalCamerad.circleOfConfusion(f, 8, s, dn), 1e-12 * coc / coc * coc, "near " + s);
            assertEquals(coc, PhysicalCamerad.circleOfConfusion(f, 8, s, df), 1e-12, "far " + s);
        }
        assertEquals(1080 * 0.5, PhysicalCamerad.cocToPixels(0.012, 0.024, 1080), 1e-12);
    }
}
