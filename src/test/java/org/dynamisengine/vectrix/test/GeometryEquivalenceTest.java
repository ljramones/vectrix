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

import org.dynamisengine.vectrix.affine.Affine4f;
import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.core.AxisAngle4d;
import org.dynamisengine.vectrix.core.AxisAngle4f;
import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Matrix3f;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Matrix4x3f;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.geometry.CullingKernels;
import org.dynamisengine.vectrix.geometry.FrustumIntersection;
import org.dynamisengine.vectrix.geometry.FrustumPlanes;
import org.dynamisengine.vectrix.geometry.GeometryUtils;
import org.dynamisengine.vectrix.geometry.Intersectionf;
import org.dynamisengine.vectrix.geometry.RayAabIntersection;
import org.dynamisengine.vectrix.soa.AABBSoA;
import org.junit.jupiter.api.Test;

/**
 * Review finding M-17: classes the twin and aliasing harnesses cannot reach, checked against independent references
 * (clip-space bounds, plane math, the batch culling kernel, {@code Intersectionf}, {@code Matrix4x3f}, quaternions and
 * the defining equations of normals and tangent frames).
 */
class GeometryEquivalenceTest {

    private static final Matrix4f VIEW_PROJ = new Matrix4f().perspective(1.1f, 1.3f, 0.5f, 80f).lookAt(3, 4, 12, 0, 0, 0, 0, 1, 0);

    private static float r(SplittableRandom rnd, double lo, double hi) {
        return (float) rnd.nextDouble(lo, hi);
    }

    @Test
    void frustumPointTestMatchesClipSpace() {
        FrustumIntersection fi = new FrustumIntersection(VIEW_PROJ);
        SplittableRandom rnd = new SplittableRandom(1);
        int inside = 0;
        for (int i = 0; i < 20000; i++) {
            Vector3f p = new Vector3f(r(rnd, -40, 40), r(rnd, -40, 40), r(rnd, -70, 20));
            Vector4f c = VIEW_PROJ.transform(new Vector4f(p, 1));
            float margin = java.lang.Math.min(java.lang.Math.min(c.w - java.lang.Math.abs(c.x), c.w - java.lang.Math.abs(c.y)),
                    c.w - java.lang.Math.abs(c.z));
            if (java.lang.Math.abs(margin) < 1e-3f * java.lang.Math.abs(c.w) + 1e-4f) {
                continue; // on a boundary: either answer is acceptable
            }
            boolean expected = margin > 0;
            inside += expected ? 1 : 0;
            assertEquals(expected, fi.testPoint(p), "point " + p);
            assertEquals(expected, fi.testPoint(p.x, p.y, p.z));
        }
        assertTrue(inside > 500, "too few points inside: " + inside);
    }

    @Test
    void frustumSphereAndBoxTestsMatchPlaneMath() {
        FrustumIntersection fi = new FrustumIntersection(VIEW_PROJ);
        FrustumPlanes planes = new FrustumPlanes().set(VIEW_PROJ, true);
        SplittableRandom rnd = new SplittableRandom(2);
        int n = 4000;
        AABBSoA boxes = new AABBSoA(n);
        for (int i = 0; i < n; i++) {
            float cx = r(rnd, -40, 40), cy = r(rnd, -40, 40), cz = r(rnd, -70, 20), rad = r(rnd, 0.05, 6);
            float minD = Float.MAX_VALUE;
            boolean outside = false;
            for (int k = 0; k < 6; k++) {
                float d = planes.x[k] * cx + planes.y[k] * cy + planes.z[k] * cz + planes.w[k];
                minD = java.lang.Math.min(minD, d);
                outside |= d < -rad;
            }
            boolean nearBoundary = java.lang.Math.abs(minD + rad) < 1e-3f || java.lang.Math.abs(minD - rad) < 1e-3f;
            if (!nearBoundary) {
                assertEquals(!outside, fi.testSphere(cx, cy, cz, rad), "sphere " + i);
                int expected = outside ? FrustumIntersection.OUTSIDE : minD >= rad ? FrustumIntersection.INSIDE : FrustumIntersection.INTERSECT;
                assertEquals(expected, fi.intersectSphere(cx, cy, cz, rad), "intersectSphere " + i);
            }
            float e = r(rnd, 0.05, 6);
            boxes.set(i, cx - e, cy - e, cz - e, cx + e, cy + e, cz + e);
        }
        int[] kernel = new int[n];
        CullingKernels.frustumCullAabbBatchScalar(new FrustumPlanes().set(VIEW_PROJ, false), boxes, kernel, n);
        int all = FrustumIntersection.PLANE_MASK_NX | FrustumIntersection.PLANE_MASK_PX | FrustumIntersection.PLANE_MASK_NY
                | (1 << FrustumIntersection.PLANE_PY) | (1 << FrustumIntersection.PLANE_NZ) | (1 << FrustumIntersection.PLANE_PZ);
        for (int i = 0; i < n; i++) {
            float x0 = boxes.minX[i], y0 = boxes.minY[i], z0 = boxes.minZ[i], x1 = boxes.maxX[i], y1 = boxes.maxY[i], z1 = boxes.maxZ[i];
            // intersectAab reports "outside" as the index (>= 0) of the plane that culled the box
            int got = fi.intersectAab(x0, y0, z0, x1, y1, z1);
            int gotClass = got >= 0 ? FrustumIntersection.OUTSIDE : got;
            assertEquals(kernel[i], gotClass, "box " + i);
            assertEquals(kernel[i] != FrustumIntersection.OUTSIDE, fi.testAab(x0, y0, z0, x1, y1, z1), "testAab " + i);
            int masked = fi.intersectAab(x0, y0, z0, x1, y1, z1, all);
            assertEquals(got >= 0, masked >= 0, "masked intersectAab " + i);
        }
    }

    @Test
    void frustumPlaneAndSegmentTests() {
        FrustumIntersection fi = new FrustumIntersection(new Matrix4f().ortho(-10, 10, -10, 10, -10, 10));
        assertTrue(fi.testPlaneXY(-1, -1, 1, 1));
        assertTrue(!fi.testPlaneXY(20, 20, 30, 30));
        assertTrue(fi.testPlaneXZ(-1, -1, 1, 1));
        assertTrue(!fi.testPlaneXZ(20, 20, 30, 30));
        assertTrue(fi.testLineSegment(-20, 0, 0, 20, 0, 0), "a segment crossing the box");
        assertTrue(fi.testLineSegment(1, 1, 1, 2, 2, 2), "a segment inside the box");
        assertTrue(!fi.testLineSegment(15, -20, 0, 15, 20, 0), "a segment entirely beyond +x");
    }

    @Test
    void rayAabIntersectionMatchesIntersectionf() {
        SplittableRandom rnd = new SplittableRandom(3);
        RayAabIntersection ray = new RayAabIntersection();
        int hits = 0;
        for (int i = 0; i < 20000; i++) {
            float ox = r(rnd, -10, 10), oy = r(rnd, -10, 10), oz = r(rnd, -10, 10);
            float dx = r(rnd, -1, 1), dy = r(rnd, -1, 1), dz = r(rnd, -1, 1);
            // zero components select the special-cased slope classes (OPP and POP were wrong)
            int zero = i % 8;
            if ((zero & 1) != 0) {
                dx = 0;
            }
            if ((zero & 2) != 0) {
                dy = 0;
            }
            if ((zero & 4) != 0 && zero != 7) {
                dz = 0;
            }
            float cx = r(rnd, -5, 5), cy = r(rnd, -5, 5), cz = r(rnd, -5, 5), e = r(rnd, 0.1, 3);
            ray.set(ox, oy, oz, dx, dy, dz);
            boolean expected = Intersectionf.testRayAab(ox, oy, oz, dx, dy, dz, cx - e, cy - e, cz - e, cx + e, cy + e, cz + e);
            hits += expected ? 1 : 0;
            assertEquals(expected, ray.test(cx - e, cy - e, cz - e, cx + e, cy + e, cz + e), "ray " + i);
        }
        assertTrue(hits > 300, "too few hits: " + hits);
    }

    @Test
    void affine4fMatchesMatrix4x3f() {
        SplittableRandom rnd = new SplittableRandom(4);
        for (int i = 0; i < 200; i++) {
            Quaternionf qa = new Quaternionf().rotationXYZ(r(rnd, -3, 3), r(rnd, -3, 3), r(rnd, -3, 3));
            Quaternionf qb = new Quaternionf().rotationXYZ(r(rnd, -3, 3), r(rnd, -3, 3), r(rnd, -3, 3));
            Vector3f ta = new Vector3f(r(rnd, -9, 9), r(rnd, -9, 9), r(rnd, -9, 9)), sa = new Vector3f(r(rnd, 0.5, 2), r(rnd, 0.5, 2), r(rnd, 0.5, 2));
            Vector3f tb = new Vector3f(r(rnd, -9, 9), r(rnd, -9, 9), r(rnd, -9, 9));
            Affine4f a = new Affine4f().translationRotateScale(ta, qa, sa);
            Affine4f b = new Affine4f().translationRotateScale(tb, qb, new Vector3f(1, 1, 1));
            Matrix4x3f ma = new Matrix4x3f().translationRotateScale(ta, qa, sa);
            Matrix4x3f mb = new Matrix4x3f().translationRotateScale(tb, qb, new Vector3f(1, 1, 1));
            assertTrue(a.toMatrix4x3f(new Matrix4x3f()).equals(ma, 1e-5f), "translationRotateScale " + i);
            assertTrue(a.mul(b, new Affine4f()).toMatrix4x3f(new Matrix4x3f()).equals(ma.mul(mb, new Matrix4x3f()), 1e-4f), "mul " + i);
            assertTrue(b.invertRigid(new Affine4f()).toMatrix4x3f(new Matrix4x3f()).equals(mb.invert(new Matrix4x3f()), 1e-4f), "invertRigid " + i);
            Vector3f p = new Vector3f(r(rnd, -5, 5), r(rnd, -5, 5), r(rnd, -5, 5));
            assertTrue(a.transformPoint(p.x, p.y, p.z, new Vector3f()).distance(ma.transformPosition(p, new Vector3f())) < 1e-4f);
            assertTrue(a.transformVector(p.x, p.y, p.z, new Vector3f()).distance(ma.transformDirection(p, new Vector3f())) < 1e-4f);
            assertTrue(new Affine4f().set(a.toMatrix4f(new Matrix4f())).toMatrix4x3f(new Matrix4x3f()).equals(ma, 1e-5f));
            Transformf trs = a.getTransform(new Transformf());
            assertTrue(trs.translation.distance(ta) < 1e-4f && trs.scale.distance(sa) < 1e-4f, "getTransform TRS " + i);
            float dot = java.lang.Math.abs(trs.rotation.dot(qa));
            assertEquals(1.0f, dot, 1e-4f, "getTransform rotation " + i);
        }
    }

    @Test
    void geometryUtilsSatisfyTheirDefinitions() {
        SplittableRandom rnd = new SplittableRandom(5);
        for (int i = 0; i < 500; i++) {
            Vector3f v = new Vector3f(r(rnd, -3, 3), r(rnd, -3, 3), r(rnd, -3, 3)).normalize();
            Vector3f p1 = new Vector3f(), p2 = new Vector3f();
            GeometryUtils.perpendicular(v, p1, p2);
            assertEquals(0, p1.dot(v), 1e-5f);
            assertEquals(0, p2.dot(v), 1e-5f);
            assertEquals(0, p1.dot(p2), 1e-5f);
            assertEquals(1, p1.length(), 1e-5f);
            assertEquals(1, p2.length(), 1e-5f);

            Vector3f a = new Vector3f(r(rnd, -3, 3), r(rnd, -3, 3), r(rnd, -3, 3));
            Vector3f b = new Vector3f(r(rnd, -3, 3), r(rnd, -3, 3), r(rnd, -3, 3));
            Vector3f c = new Vector3f(r(rnd, -3, 3), r(rnd, -3, 3), r(rnd, -3, 3));
            Vector3f n = new Vector3f();
            GeometryUtils.normal(a, b, c, n);
            Vector3f cross = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
            if (cross.length() > 1e-2f) {
                assertTrue(n.distance(cross.normalize()) < 1e-4f, "normal " + i);
            }
            Vector2f ua = new Vector2f(r(rnd, 0, 1), r(rnd, 0, 1)), ub = new Vector2f(r(rnd, 0, 1), r(rnd, 0, 1)), uc = new Vector2f(r(rnd, 0, 1), r(rnd, 0, 1));
            float du1 = ub.x - ua.x, dv1 = ub.y - ua.y, du2 = uc.x - ua.x, dv2 = uc.y - ua.y;
            float det = du1 * dv2 - du2 * dv1;
            if (java.lang.Math.abs(det) < 0.05f || cross.length() < 1e-2f) {
                continue;
            }
            // edge1 = du1 T + dv1 B, edge2 = du2 T + dv2 B, solved for T and B directly
            Vector3f e1 = new Vector3f(b).sub(a), e2 = new Vector3f(c).sub(a);
            Vector3f tRef = new Vector3f(e1).mul(dv2).sub(new Vector3f(e2).mul(dv1)).div(det).normalize();
            Vector3f bRef = new Vector3f(e2).mul(du1).sub(new Vector3f(e1).mul(du2)).div(det).normalize();
            Vector3f t = new Vector3f(), bt = new Vector3f(), t2 = new Vector3f(), b2 = new Vector3f();
            GeometryUtils.tangent(a, ua, b, ub, c, uc, t);
            GeometryUtils.bitangent(a, ua, b, ub, c, uc, bt);
            GeometryUtils.tangentBitangent(a, ua, b, ub, c, uc, t2, b2);
            assertTrue(t.distance(tRef) < 1e-3f, "tangent " + i + ": " + t + " vs " + tRef);
            assertTrue(bt.distance(bRef) < 1e-3f, "bitangent " + i + ": " + bt + " vs " + bRef);
            assertTrue(t2.distance(tRef) < 1e-3f && b2.distance(bRef) < 1e-3f, "tangentBitangent " + i);
        }
    }

    @Test
    void axisAngleAgreesWithQuaternions() {
        SplittableRandom rnd = new SplittableRandom(6);
        for (int i = 0; i < 300; i++) {
            Quaternionf q = new Quaternionf().rotationXYZ(r(rnd, -3, 3), r(rnd, -3, 3), r(rnd, -3, 3));
            AxisAngle4f aa = new AxisAngle4f().set(q);
            assertTrue(aa.get(new Matrix3f()).equals(q.get(new Matrix3f()), 1e-4f), "matrix " + i);
            assertEquals(1.0f, java.lang.Math.abs(aa.get(new Quaternionf()).dot(q)), 1e-4f, "round trip " + i);
            Vector3f v = new Vector3f(r(rnd, -3, 3), r(rnd, -3, 3), r(rnd, -3, 3));
            assertTrue(aa.transform(new Vector3f(v)).distance(q.transform(new Vector3f(v))) < 1e-4f, "transform " + i);
            Quaterniond qd = new Quaterniond(q.x, q.y, q.z, q.w).normalize();
            AxisAngle4d ad = new AxisAngle4d().set(qd);
            assertTrue(ad.get(new Matrix3d()).equals(qd.get(new Matrix3d()), 1e-9), "matrix d " + i);
            assertTrue(ad.transform(new Vector3d(v)).distance(qd.transform(new Vector3d(v))) < 1e-9, "transform d " + i);
        }
    }
}
