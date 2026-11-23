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

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.geometry.Intersectiond;
import org.dynamisengine.vectrix.geometry.Intersectionf;
import org.dynamisengine.vectrix.geometry.PolygonsIntersection;
import org.junit.jupiter.api.Test;

/** Audit of the JOML-inherited geometry functions against constructions with known answers. */
class GeometryAuditTest {

    @Test
    void signsAndFactors() {
        Vector4d c4 = new Vector4d();
        assertTrue(Intersectiond.intersectPlaneSphere(0, 0, 1, 0, 0, 0, 5, 10, c4));
        assertTrue(new Vector3d(c4.x, c4.y, c4.z).length() < 1e-15, "plane-sphere circle center " + c4);
        assertEquals(Math.sqrt(75), c4.w, 1e-14);
        Vector3d c3 = new Vector3d();
        assertTrue(Intersectiond.intersectLineCircle(0, 1, 0, 0, 3, 5, c3));
        assertEquals(0.0, c3.x * c3.x + c3.y * c3.y, 1e-30, "line-circle center");
        assertEquals(4.0, c3.z, 1e-15);
        Vector3d cp = Intersectiond.findClosestPointOnPlane(0, 0, 5, 0, 0, 1, 1, 2, 7, new Vector3d());
        assertTrue(cp.distance(1, 2, 5) < 1e-15, "closest point on plane " + cp);
        Vector4d pt = new Vector4d();
        assertTrue(Intersectiond.intersectPlaneSweptSphere(0, 0, 1, -10, 0, 0, 0, 1, 0, 0, 1, pt));
        assertEquals(9.0, pt.w, 1e-14, "swept sphere time");
        assertTrue(Intersectiond.testPlaneSweptSphere(0, 0, 1, -10, 0, 0, 0, 1, 0, 0, 20));
        assertTrue(Intersectiond.intersectCircleCircle(0, 0, 4, 2, 0, 1, c3));
        assertTrue(c3.distance(1.75, 0, Math.sqrt(4 - 1.75 * 1.75)) < 1e-15, "circle-circle " + c3);
        assertFalse(Intersectiond.testCircleCircle(new Vector2d(0, 0), 4, new Vector2d(6, 0), 4), "r = 2 circles 6 apart");
        assertTrue(Intersectiond.testCircleCircle(new Vector2d(0, 0), 4, new Vector2d(3, 0), 4));
        assertTrue(Intersectiond.testSphereSphere(1, 2, 3, 4, 1, 2, 3, 4), "concentric equal spheres");
        assertFalse(Intersectiond.testSphereSphere(1, 2, 3, 4, 1, 2, 3, 9), "concentric different spheres");
        // segment closest points: two points give their squared distance; short segments are not points
        Vector3d ra = new Vector3d(), rb = new Vector3d();
        assertEquals(50.0, Intersectiond.findClosestPointsLineSegments(1, 2, 3, 1, 2, 3, 4, 6, 8, 4, 6, 8, ra, rb), 0.0);
        assertEquals(1e-6, Intersectiond.findClosestPointsLineSegments(-5e-5, 0, 0, 5e-5, 0, 0, 0, -5e-5, 1e-3, 0, 5e-5,
                1e-3, ra, rb), 1e-20, "crossing segments 1e-3 apart");
        // a parallel ray never hits a plane
        assertEquals(-1.0, Intersectiond.intersectRayPlane(0, 0, -1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 1e-6), 0.0);
        // a flat box is hit through its middle
        Vector2d nf = new Vector2d();
        assertTrue(Intersectiond.intersectRayAab(-5, 0, 0, 1, 0, 0, -1, 0, -1, 1, 0, 1, nf), "flat box");
        assertTrue(Intersectiond.testRayAab(-5, 0, 0, 1, 0, 0, -1, 0, -1, 1, 0, 1));
        // a triangle behind the ray is a miss (-1), not a negative distance
        assertEquals(-1.0, Intersectiond.intersectRayTriangle(0, 0, 0, 0, 0, 1, -1, -1, -5, 1, -1, -5, 0, 1, -5, 0), 0.0);
        assertEquals(5.0, Intersectiond.intersectRayTriangle(0, 0, 0, 0, 0, -1, -1, -1, -5, 1, -1, -5, 0, 1, -5, 0), 1e-15);
        // a tiny triangle is hit head-on regardless of its size
        assertEquals(1.0, Intersectiond.intersectRayTriangle(0, 0, 0, 0, 0, 1, -3e-4, -3e-4, 1, 3e-4, -3e-4, 1, 0, 3e-4, 1, 0), 1e-15);
        // polygons: every polygon's first vertex counts for the bounds
        PolygonsIntersection poly = new PolygonsIntersection(new float[] {-10, 5, 10, 0, 10, 10}, new int[0], 3);
        assertTrue(poly.testPoint(0, 5) && poly.testPoint(9, 5), "point inside the triangle");
    }

    @Test
    void raySphereFarFromTheOrigin() {
        SplittableRandom r = new SplittableRandom(1);
        Vector2d res = new Vector2d();
        org.dynamisengine.vectrix.core.Vector2f resf = new org.dynamisengine.vectrix.core.Vector2f();
        for (int i = 0; i < 20000; i++) {
            Vector3d dir = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            Vector3d perp = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).orthogonalize(dir);
            double dist = Math.pow(10, r.nextDouble(2, 7)), radius = 1;
            double off = r.nextBoolean() ? 0.9 : 1.1; // clearly inside or outside
            Vector3d center = new Vector3d(dir).mul(dist).add(new Vector3d(perp).mul(off * radius));
            boolean expect = off < 1;
            assertEquals(expect, Intersectiond.testRaySphere(0, 0, 0, dir.x, dir.y, dir.z, center.x, center.y, center.z, 1),
                    "double at " + dist);
            assertEquals(expect, Intersectiond.intersectRaySphere(0, 0, 0, dir.x, dir.y, dir.z, center.x, center.y, center.z, 1,
                    res), "double intersect at " + dist);
            if (expect) {
                assertEquals(dist - Math.sqrt(1 - off * off), res.x, 1e-9 * dist, "entry distance");
            }
            if (dist < 1e4) { // float: the sphere position itself rounds at about dist * 6e-8
                assertEquals(expect, Intersectionf.testRaySphere(0, 0, 0, (float) dir.x, (float) dir.y, (float) dir.z, (float) center.x,
                        (float) center.y, (float) center.z, 1), "float at " + dist);
            }
        }
        // tangent rays hit
        assertTrue(Intersectiond.testRaySphere(0, 1, 0, 1, 0, 0, 10, 0, 0, 1));
        assertTrue(Intersectiond.intersectRaySphere(0, 1, 0, 1, 0, 0, 10, 0, 0, 1, res));
        assertEquals(10.0, res.x, 1e-15);
    }

    @Test
    void rayTrianglesAreWatertight() {
        SplittableRandom r = new SplittableRandom(2);
        for (int i = 0; i < 200000; i++) {
            Vector3d a = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
            Vector3d b = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
            Vector3d p = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).add(0, 0, 3);
            Vector3d q = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).add(0, 0, -3);
            // two triangles sharing edge ab, on opposite sides; aim at a point on the shared edge
            double s = r.nextDouble();
            Vector3d target = new Vector3d(a).lerp(b, s);
            Vector3d origin = new Vector3d(target).add(r.nextGaussian() * 5, r.nextGaussian() * 5, 10);
            Vector3d dir = new Vector3d(target).sub(origin).normalize();
            boolean hit1 = Intersectiond.testRayTriangle(origin.x, origin.y, origin.z, dir.x, dir.y, dir.z, a.x, a.y, a.z, b.x, b.y, b.z,
                    p.x, p.y, p.z, 0);
            boolean hit2 = Intersectiond.testRayTriangle(origin.x, origin.y, origin.z, dir.x, dir.y, dir.z, b.x, b.y, b.z, a.x, a.y, a.z,
                    q.x, q.y, q.z, 0);
            // the edge belongs to both triangles only if they lie on opposite sides as seen along the ray
            Vector3d n1 = new Vector3d(b).sub(a).cross(new Vector3d(p).sub(a));
            Vector3d n2 = new Vector3d(a).sub(b).cross(new Vector3d(q).sub(b));
            Vector3d sideP = new Vector3d(p).sub(a).cross(dir), sideQ = new Vector3d(q).sub(a).cross(dir);
            Vector3d ab = new Vector3d(b).sub(a);
            if (sideP.dot(ab) * sideQ.dot(ab) < 0 && Math.abs(n1.dot(dir)) > 1e-3 && Math.abs(n2.dot(dir)) > 1e-3) {
                assertTrue(hit1 || hit2, "ray through the shared edge slipped between both triangles at trial " + i);
            }
        }
    }

    /** Separating-axis reference for oriented boxes (15 axes), with exact rotation dot products. */
    private static boolean satReference(Vector3d c0, Vector3d[] u0, double[] h0, Vector3d c1, Vector3d[] u1, double[] h1) {
        Vector3d[] axes = new Vector3d[15];
        int k = 0;
        for (Vector3d u : u0) {
            axes[k++] = u;
        }
        for (Vector3d u : u1) {
            axes[k++] = u;
        }
        for (Vector3d a : u0) {
            for (Vector3d b : u1) {
                axes[k++] = new Vector3d(a).cross(b);
            }
        }
        Vector3d t = new Vector3d(c1).sub(c0);
        for (Vector3d ax : axes) {
            if (ax.lengthSquared() < 1e-20) {
                continue;
            }
            double ra = 0, rb = 0;
            for (int i = 0; i < 3; i++) {
                ra += h0[i] * Math.abs(u0[i].dot(ax));
                rb += h1[i] * Math.abs(u1[i].dot(ax));
            }
            if (Math.abs(t.dot(ax)) > ra + rb) {
                return false;
            }
        }
        return true;
    }

    @Test
    void orientedBoxesMatchTheSeparatingAxisReference() {
        SplittableRandom r = new SplittableRandom(3);
        int checked = 0;
        for (int i = 0; i < 50000; i++) {
            Matrix3d m0 = new Matrix3d().rotateXYZ(r.nextDouble(-3, 3), r.nextDouble(-3, 3), r.nextDouble(-3, 3));
            Matrix3d m1 = new Matrix3d().rotateXYZ(r.nextDouble(-3, 3), r.nextDouble(-3, 3), r.nextDouble(-3, 3));
            Vector3d[] u0 = {m0.getColumn(0, new Vector3d()), m0.getColumn(1, new Vector3d()), m0.getColumn(2, new Vector3d())};
            Vector3d[] u1 = {m1.getColumn(0, new Vector3d()), m1.getColumn(1, new Vector3d()), m1.getColumn(2, new Vector3d())};
            double[] h0 = {r.nextDouble(0.2, 1.5), r.nextDouble(0.2, 1.5), r.nextDouble(0.2, 1.5)};
            double[] h1 = {r.nextDouble(0.2, 1.5), r.nextDouble(0.2, 1.5), r.nextDouble(0.2, 1.5)};
            Vector3d c0 = new Vector3d(), c1 = new Vector3d(r.nextDouble(-3, 3), r.nextDouble(-3, 3), r.nextDouble(-3, 3));
            boolean ref = satReference(c0, u0, h0, c1, u1, h1);
            // skip near-touching pairs, where the 1e-8 parallel-edge padding may legitimately differ
            boolean refShrunk = satReference(c0, u0, new double[] {h0[0] - 1e-6, h0[1] - 1e-6, h0[2] - 1e-6}, c1, u1, h1);
            if (ref != refShrunk) {
                continue;
            }
            boolean got = Intersectiond.testObOb(c0.x, c0.y, c0.z, u0[0].x, u0[0].y, u0[0].z, u0[1].x, u0[1].y, u0[1].z, u0[2].x,
                    u0[2].y, u0[2].z, h0[0], h0[1], h0[2], c1.x, c1.y, c1.z, u1[0].x, u1[0].y, u1[0].z, u1[1].x, u1[1].y, u1[1].z,
                    u1[2].x, u1[2].y, u1[2].z, h1[0], h1[1], h1[2]);
            assertEquals(ref, got, "box pair " + i);
            checked++;
        }
        assertTrue(checked > 49000);
    }
}
