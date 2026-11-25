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
import org.dynamisengine.vectrix.core.Vector4f;
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
        assertTrue(Intersectiond.intersectCircleCircle(0, 0, 2, 2, 0, 1, c3));
        assertTrue(c3.distance(1.75, 0, Math.sqrt(4 - 1.75 * 1.75)) < 1e-15, "circle-circle " + c3);
        assertFalse(Intersectiond.testCircleCircle(new Vector2d(0, 0), 2, new Vector2d(6, 0), 2), "r = 2 circles 6 apart");
        assertTrue(Intersectiond.testCircleCircle(new Vector2d(0, 0), 2, new Vector2d(3, 0), 2));
        assertTrue(Intersectiond.testSphereSphere(1, 2, 3, 2, 1, 2, 3, 2), "concentric equal spheres");
        assertTrue(Intersectiond.testSphereSphere(1, 2, 3, 2, 1, 2, 3, 3), "concentric spheres: containment counts (3.0)");
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


    /** Distance from {@code c} to the triangle, via the closest-point routine. */
    private static double triDist(Vector3d a, Vector3d b, Vector3d c, Vector3d p, Vector3d tmp) {
        Intersectiond.findClosestPointOnTriangle(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, p.x, p.y, p.z, tmp);
        return tmp.distance(p);
    }

    /** First contact time by bisection on the convex distance function; NaN if none before maxT. */
    private static double referenceContact(Vector3d a, Vector3d b, Vector3d c, Vector3d c0, Vector3d v, double r, double maxT) {
        Vector3d p = new Vector3d(), tmp = new Vector3d();
        if (triDist(a, b, c, c0, tmp) <= r) {
            return 0;
        }
        double lo = 0, hi = maxT; // ternary search for the minimum distance
        for (int i = 0; i < 200; i++) {
            double m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3;
            if (triDist(a, b, c, p.set(v).mul(m1).add(c0), tmp) < triDist(a, b, c, p.set(v).mul(m2).add(c0), tmp)) {
                hi = m2;
            } else {
                lo = m1;
            }
        }
        double tMin = (lo + hi) / 2;
        if (triDist(a, b, c, p.set(v).mul(tMin).add(c0), tmp) > r) {
            return Double.NaN;
        }
        lo = 0;
        hi = tMin;
        for (int i = 0; i < 200; i++) {
            double m = (lo + hi) / 2;
            if (triDist(a, b, c, p.set(v).mul(m).add(c0), tmp) <= r) {
                hi = m;
            } else {
                lo = m;
            }
        }
        return hi;
    }

    @Test
    void sweptSphereTriangleMatchesFirstContact() {
        SplittableRandom r = new SplittableRandom(4);
        Vector4d res = new Vector4d();
        Vector4f resf = new Vector4f();
        Vector3d tmp = new Vector3d();
        int hits = 0, kinds = 0;
        for (int i = 0; i < 20000; i++) {
            Vector3d a = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
            Vector3d b = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
            Vector3d c = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
            Vector3d c0 = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).mul(3);
            Vector3d v = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).mul(3);
            int kind = i % 4;
            if (kind == 1) { // parallel to the plane, at a height where it may cut it
                Vector3d n = new Vector3d(b).sub(a).cross(new Vector3d(c).sub(a)).normalize();
                v.sub(new Vector3d(n).mul(v.dot(n)));
                c0.sub(new Vector3d(n).mul(c0.dot(n) - a.dot(n))).add(new Vector3d(n).mul(r.nextDouble(-1, 1)));
            } else if (kind == 2) { // moving away from wherever it is
                v.set(c0).sub(new Vector3d(a).add(b).add(c).div(3)).normalize(3 * r.nextDouble());
            }
            double rad = r.nextDouble(0.1, 1.0), maxT = r.nextDouble(0.5, 2);
            double ref = referenceContact(a, b, c, c0, v, rad, maxT);
            int code = Intersectiond.intersectSweptSphereTriangle(c0.x, c0.y, c0.z, rad, v.x, v.y, v.z, a.x, a.y, a.z, b.x, b.y, b.z,
                    c.x, c.y, c.z, 0, maxT, res);
            String ctx = "case " + i + " kind " + kind + " ref " + ref + " got " + code + " " + res;
            if (Double.isNaN(ref) || ref > maxT * (1 - 1e-9)) {
                // grazing misses can be within rounding of a touch
                if (code != 0) {
                    Vector3d at = new Vector3d(v).mul(res.w).add(c0);
                    assertEquals(rad, triDist(a, b, c, at, tmp), 1e-9, ctx);
                }
                continue;
            }
            assertTrue(code != 0, ctx);
            assertEquals(ref, res.w, 1e-9 * (1 + ref), ctx);
            Vector3d at = new Vector3d(v).mul(res.w).add(c0);
            triDist(a, b, c, at, tmp);
            assertTrue(tmp.distance(res.x, res.y, res.z) < 1e-7, "contact point " + ctx);
            hits++;
            kinds |= 1 << code;
            int codef = Intersectionf.intersectSweptSphereTriangle((float) c0.x, (float) c0.y, (float) c0.z, (float) rad, (float) v.x,
                    (float) v.y, (float) v.z, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, (float) c.x,
                    (float) c.y, (float) c.z, 0, (float) maxT, resf);
            if (ref < maxT * 0.99) {
                assertTrue(codef != 0, "float " + ctx);
                assertEquals(ref, resf.w, 2e-3 * (1 + ref), "float " + ctx);
            }
        }
        assertTrue(hits > 800, "hits " + hits);
        assertEquals(0b11111110, kinds, "every contact kind occurs");
        // a sphere sitting on the face moving away still touches at t = 0, and from behind it is hit too
        assertEquals(Intersectiond.POINT_ON_TRIANGLE_FACE, Intersectiond.intersectSweptSphereTriangle(0.2, 0.2, 1, 1, 0, 0, 1,
                0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, res));
        assertEquals(0.0, res.w, 0.0);
        assertEquals(Intersectiond.POINT_ON_TRIANGLE_FACE, Intersectiond.intersectSweptSphereTriangle(0.2, 0.2, -5, 1, 0, 0, 1,
                0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 10, res));
        assertEquals(4.0, res.w, 1e-15);
        assertTrue(new Vector3d(res.x, res.y, res.z).distance(0.2, 0.2, 0) < 1e-15);
        assertEquals(0, Intersectiond.intersectSweptSphereTriangle(0.2, 0.2, 2, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 10, res));
        // sliding parallel inside the plane into an edge
        assertEquals(Intersectiond.POINT_ON_TRIANGLE_EDGE_20, Intersectiond.intersectSweptSphereTriangle(-3, 0.3, 0.5, 1, 1, 0, 0,
                0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 10, res));
        assertEquals(3 - Math.sqrt(0.75), res.w, 1e-15);
    }

    @Test
    void pointInTriangleIsClosed() {
        // every edge midpoint and vertex is inside, whatever the winding; just outside is not
        double[][] tri = {{0, 0, 0}, {4, 0, 0}, {0, 4, 0}};
        for (int w = 0; w < 2; w++) {
            double[] p0 = tri[0], p1 = tri[w == 0 ? 1 : 2], p2 = tri[w == 0 ? 2 : 1];
            for (double[] q : new double[][] {{2, 0}, {2, 2}, {0, 2}, {0, 0}, {4, 0}, {0, 4}, {1, 1}}) {
                assertTrue(Intersectiond.testPointInTriangle(q[0], q[1], 7, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]),
                        "3D " + q[0] + "," + q[1]);
                assertTrue(Intersectionf.testPointInTriangle((float) q[0], (float) q[1], 7, (float) p0[0], (float) p0[1], (float) p0[2],
                        (float) p1[0], (float) p1[1], (float) p1[2], (float) p2[0], (float) p2[1], (float) p2[2]));
                assertTrue(Intersectiond.testPointTriangle(q[0], q[1], p0[0], p0[1], p1[0], p1[1], p2[0], p2[1]), "2D " + q[0] + "," + q[1]);
                assertTrue(Intersectionf.testPointTriangle((float) q[0], (float) q[1], (float) p0[0], (float) p0[1], (float) p1[0],
                        (float) p1[1], (float) p2[0], (float) p2[1]));
            }
            for (double[] q : new double[][] {{2, -1e-12}, {2.0000001, 2}, {-1e-12, 2}, {5, 0}}) {
                assertFalse(Intersectiond.testPointInTriangle(q[0], q[1], 0, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2]));
                assertFalse(Intersectiond.testPointTriangle(q[0], q[1], p0[0], p0[1], p1[0], p1[1], p2[0], p2[1]));
            }
        }
        // degenerate triangles: a segment contains its own points only
        assertTrue(Intersectiond.testPointTriangle(1, 1, 0, 0, 2, 2, 1, 1));
        assertFalse(Intersectiond.testPointTriangle(3, 3, 0, 0, 2, 2, 1, 1));
        assertFalse(Intersectiond.testPointInTriangle(1, 1, 0, 0, 0, 0, 2, 2, 0, 1, 1, 0));
    }

    @Test
    void movingCirclesMatchTheClosestApproach() {
        assertTrue(Intersectiond.testMovingCircleCircle(0, 0, -5, 0, 1, 1, 0, 1), "overlapping and moving apart");
        assertTrue(Intersectiond.testMovingCircleCircle(0, 0, 10, 0, 1, 12, 0, 1), "touches exactly at the end");
        assertFalse(Intersectiond.testMovingCircleCircle(0, 0, 10, 0, 1, 12.5, 0, 1));
        assertTrue(Intersectiond.testMovingCircleCircle(0, 0, 10, 0, 1, 5, 1.5, 1));
        assertFalse(Intersectiond.testMovingCircleCircle(0, 0, 10, 0, 1, 5, 2.5, 1));
        assertTrue(Intersectiond.testMovingCircleCircle(0, 0, 0, 0, 1, 1.5, 0, 1), "not moving, overlapping");
        assertFalse(Intersectiond.testMovingCircleCircle(0, 0, 0, 0, 1, 3, 0, 1), "not moving, apart");
        SplittableRandom r = new SplittableRandom(5);
        for (int i = 0; i < 100000; i++) {
            double ax = r.nextDouble(-5, 5), ay = r.nextDouble(-5, 5), mx = r.nextDouble(-8, 8), my = r.nextDouble(-8, 8);
            double bx = r.nextDouble(-5, 5), by = r.nextDouble(-5, 5), ra = r.nextDouble(0.1, 2), rb = r.nextDouble(0.1, 2);
            double cx = bx - ax, cy = by - ay, m2 = mx * mx + my * my;
            double s = m2 > 0 ? Math.max(0, Math.min(1, (mx * cx + my * cy) / m2)) : 0;
            double dist = Math.hypot(cx - s * mx, cy - s * my) - (ra + rb);
            if (Math.abs(dist) < 1e-9) {
                continue;
            }
            assertEquals(dist < 0, Intersectiond.testMovingCircleCircle(ax, ay, mx, my, ra, bx, by, rb), "case " + i);
            if (Math.abs(dist) > 1e-4) {
                assertEquals(dist < 0, Intersectionf.testMovingCircleCircle((float) ax, (float) ay, (float) mx, (float) my, (float) ra,
                        (float) bx, (float) by, (float) rb), "float case " + i);
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
