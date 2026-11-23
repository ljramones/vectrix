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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.Matrix4x3f;
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector2dc;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector2fc;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.geometry.BoundingVolumesd;
import org.dynamisengine.vectrix.geometry.BoundingVolumesf;
import org.dynamisengine.vectrix.geometry.Contactsd;
import org.dynamisengine.vectrix.geometry.Contactsf;
import org.dynamisengine.vectrix.geometry.Intersectiond;
import org.dynamisengine.vectrix.geometry.Intersectionf;
import org.dynamisengine.vectrix.geometry.MeshMath;
import org.dynamisengine.vectrix.geometry.RobustPredicates;
import org.dynamisengine.vectrix.geometry.SpatialCodes;
import org.dynamisengine.vectrix.hash.Philox;
import org.dynamisengine.vectrix.linalg.LeastSquaresd;
import org.dynamisengine.vectrix.linalg.LeastSquaresf;
import org.junit.jupiter.api.Test;

/**
 * 3.0 fixes in {@code geometry}, {@code linalg} and {@code hash}: every sphere and circle method of
 * {@code Intersection*} takes the plain radius, containment counts for sphere-sphere and circle-circle overlap, the
 * concentric cases of the intersection methods are defined, bounds are conservative, and decoders, predicates and
 * random streams have defined results at their edges.
 */
class GeometryLinalgHash30Test {

    // ---- Intersection: plain radii everywhere

    @Test
    void sphereAndCircleMethodsTakeThePlainRadius() {
        // radius 2: a point at distance 1.5 is inside, at 2.5 outside (with squared radii, 2 would mean r = 1.41)
        assertTrue(Intersectionf.testAabSphere(0, 0, 0, 1, 1, 1, 2.5f, 0.5f, 0.5f, 2));
        assertFalse(Intersectionf.testAabSphere(0, 0, 0, 1, 1, 1, 3.5f, 0.5f, 0.5f, 2));
        assertTrue(Intersectiond.testAabSphere(new Vector3d(), new Vector3d(1, 1, 1), new Vector3d(2.5, 0.5, 0.5), 2));
        assertTrue(Intersectionf.testAarCircle(0, 0, 1, 1, 2.5f, 0.5f, 2));
        assertFalse(Intersectiond.testAarCircle(0, 0, 1, 1, 3.5, 0.5, 2));
        assertTrue(Intersectionf.testPointCircle(1.5f, 0, 0, 0, 2));
        assertFalse(Intersectiond.testPointCircle(2.5, 0, 0, 0, 2));
        assertTrue(Intersectionf.testLineSegmentSphere(-5, 1.5f, 0, 5, 1.5f, 0, 0, 0, 0, 2));
        assertFalse(Intersectiond.testLineSegmentSphere(-5, 2.5, 0, 5, 2.5, 0, 0, 0, 0, 2));
        assertTrue(Intersectionf.testRaySphere(0, 1.5f, -10, 0, 0, 1, 0, 0, 0, 2));
        assertFalse(Intersectiond.testRaySphere(0, 2.5, -10, 0, 0, 1, 0, 0, 0, 2));
        assertTrue(Intersectionf.testRayCircle(-10, 1.5f, 1, 0, 0, 0, 2));
        assertFalse(Intersectiond.testRayCircle(-10, 2.5, 1, 0, 0, 0, 2));
        assertTrue(Intersectionf.testCircleTriangle(0, 0, 2, 1.5f, -1, 3, -1, 3, 1));
        assertFalse(Intersectiond.testCircleTriangle(0, 0, 2, 2.5, -1, 4, -1, 4, 1));

        Vector2d t = new Vector2d();
        assertTrue(Intersectiond.intersectRaySphere(0, 0, -10, 0, 0, 1, 0, 0, 0, 2, t));
        assertEquals(8, t.x, 1e-15);
        assertEquals(12, t.y, 1e-15);
        Vector2f tf = new Vector2f();
        assertTrue(Intersectionf.intersectRayCircle(-10, 0, 1, 0, 0, 0, 2, tf));
        assertEquals(8f, tf.x, 0f);
        assertEquals(12f, tf.y, 0f);

        // circles of radius 2 and 1, centers 2 apart: they cross at x = 1.75
        Vector3d c3 = new Vector3d();
        assertTrue(Intersectiond.intersectCircleCircle(0, 0, 2, 2, 0, 1, c3));
        assertEquals(1.75, c3.x, 1e-15);
        assertEquals(Math.sqrt(4 - 1.75 * 1.75), c3.z, 1e-15);
        Vector4f c4 = new Vector4f();
        assertTrue(Intersectionf.intersectSphereSphere(0, 0, 0, 2, 2, 0, 0, 1, c4));
        assertEquals(1.75f, c4.x, 1e-6f);
        assertEquals((float) Math.sqrt(4 - 1.75 * 1.75), c4.w, 1e-6f);
    }

    @Test
    void sphereSphereAndCircleCircleAgreeOnContainment() {
        // one inside the other, concentric, touching, apart
        float[][] cases = {{0, 0, 5, 1, 0, 1}, {0, 0, 2, 0, 0, 3}, {0, 0, 2, 3, 0, 1}, {0, 0, 2, 3.5f, 0, 1}};
        boolean[] expect = {true, true, true, false};
        for (int i = 0; i < cases.length; i++) {
            float[] c = cases[i];
            assertEquals(expect[i], Intersectionf.testSphereSphere(c[0], c[1], 0, c[2], c[3], c[4], 0, c[5]), "sf " + i);
            assertEquals(expect[i], Intersectiond.testSphereSphere(c[0], c[1], 0, c[2], c[3], c[4], 0, c[5]), "sd " + i);
            assertEquals(expect[i], Intersectionf.testCircleCircle(c[0], c[1], c[2], c[3], c[4], c[5]), "cf " + i);
            assertEquals(expect[i], Intersectiond.testCircleCircle(c[0], c[1], c[2], c[3], c[4], c[5]), "cd " + i);
            assertEquals(expect[i], Contactsf.testSphereSphere(new Vector3f(c[0], c[1], 0), c[2], new Vector3f(c[3], c[4],
                    0), c[5]), "contacts " + i);
        }
    }

    @Test
    void concentricIntersectionsAreFalseAndLeaveTheDestinationUnchanged() {
        Vector4f s = new Vector4f(7, 7, 7, 7);
        assertFalse(Intersectionf.intersectSphereSphere(1, 2, 3, 2, 1, 2, 3, 2, s));
        assertFalse(Intersectionf.intersectSphereSphere(1, 2, 3, 2, 1, 2, 3, 3, s));
        assertEquals(new Vector4f(7, 7, 7, 7), s);
        Vector4d sd = new Vector4d(7, 7, 7, 7);
        assertFalse(Intersectiond.intersectSphereSphere(new Vector3d(1, 2, 3), 2, new Vector3d(1, 2, 3), 2, sd));
        assertEquals(new Vector4d(7, 7, 7, 7), sd);
        Vector3f c = new Vector3f(7, 7, 7);
        assertFalse(Intersectionf.intersectCircleCircle(1, 2, 2, 1, 2, 2, c));
        assertEquals(new Vector3f(7, 7, 7), c);
        Vector3d cd = new Vector3d(7, 7, 7);
        assertFalse(Intersectiond.intersectCircleCircle(1, 2, 2, 1, 2, 3, cd));
        assertEquals(new Vector3d(7, 7, 7), cd);
        // a sphere strictly inside the other: the surfaces do not meet
        assertFalse(Intersectiond.intersectSphereSphere(0, 0, 0, 5, 1, 0, 0, 1, sd));
    }

    @Test
    void readOnlyViewsAreAccepted() {
        Vector2fc a = new Vector2f(0, 0), move = new Vector2f(10, 0), b = new Vector2f(5, 1.5f);
        assertTrue(Intersectionf.testMovingCircleCircle(a, move, 1, b, 1));
        Vector2dc ad = new Vector2d(0, 0), moved = new Vector2d(10, 0), bd = new Vector2d(5, 2.5);
        assertFalse(Intersectiond.testMovingCircleCircle(ad, moved, 1, bd, 1));
        Vector3fc o = new Vector3f(), x = new Vector3f(1, 0, 0), y = new Vector3f(0, 1, 0), z = new Vector3f(0, 0, 1);
        Vector3fc h = new Vector3f(1, 1, 1), o2 = new Vector3f(1.5f, 0, 0), far = new Vector3f(2.5f, 0, 0);
        assertTrue(Intersectionf.testObOb(o, x, y, z, h, o2, x, y, z, h));
        assertFalse(Intersectionf.testObOb(o, x, y, z, h, far, x, y, z, h));
        Vector3dc od = new Vector3d(), xd = new Vector3d(1, 0, 0), yd = new Vector3d(0, 1, 0), zd = new Vector3d(0, 0, 1);
        Vector3dc hd = new Vector3d(1, 1, 1), o2d = new Vector3d(1.5, 0, 0);
        assertTrue(Intersectiond.testObOb(od, xd, yd, zd, hd, o2d, xd, yd, zd, hd));
    }

    // ---- Contacts.nearestFace

    @Test
    void nearestFaceOutsideTheBoxIsTheNearestFaceAtItsDistance() {
        SplittableRandom r = new SplittableRandom(11);
        Vector3d min = new Vector3d(-1, -2, -3), max = new Vector3d(2, 1, 0.5);
        Vector3d n = new Vector3d();
        for (int i = 0; i < 5000; i++) {
            Vector3d p = new Vector3d(r.nextDouble(-6, 6), r.nextDouble(-6, 6), r.nextDouble(-6, 6));
            double got = Contactsd.nearestFace(p, min, max, n);
            // brute force: distance from p to each face rectangle
            double best = Double.POSITIVE_INFINITY;
            for (int axis = 0; axis < 3; axis++) {
                for (int side = 0; side < 2; side++) {
                    best = Math.min(best, faceDistance(p, min, max, axis, side));
                }
            }
            int axis = n.x != 0 ? 0 : n.y != 0 ? 1 : 2;
            int side = (axis == 0 ? n.x : axis == 1 ? n.y : n.z) > 0 ? 1 : 0;
            assertEquals(best, faceDistance(p, min, max, axis, side), 1e-12, "the face returned is a nearest face");
            boolean inside = p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y && p.z >= min.z && p.z <= max.z;
            assertEquals(inside ? best : -best, got, 1e-12, "signed distance to that face at " + p);
        }
        Vector3f nf = new Vector3f();
        // beyond +X by 3 and +Y by 4: the box corner edge is 5 away
        assertEquals(-5f, Contactsf.nearestFace(new Vector3f(5, 5, 0), new Vector3f(-2, -1, -1), new Vector3f(2, 1, 1),
                nf), 1e-6f);
        assertEquals(new Vector3f(0, 1, 0), nf);
    }

    private static double faceDistance(Vector3d p, Vector3d min, Vector3d max, int axis, int side) {
        double[] lo = {min.x, min.y, min.z}, hi = {max.x, max.y, max.z}, q = {p.x, p.y, p.z};
        double s = 0;
        for (int k = 0; k < 3; k++) {
            double c = k == axis ? (side == 0 ? lo[k] : hi[k]) : Math.min(Math.max(q[k], lo[k]), hi[k]);
            s += (q[k] - c) * (q[k] - c);
        }
        return Math.sqrt(s);
    }

    // ---- RobustPredicates

    @Test
    void predicatesReturnNaNForNonFiniteInputInsteadOfThrowing() {
        // finite left term, NaN right term: this reached the exact path and threw NumberFormatException before 3.0
        assertTrue(Double.isNaN(RobustPredicates.orient2d(1, Double.NaN, 0, 1, 0, 0)));
        assertTrue(Double.isNaN(RobustPredicates.orient2d(Double.POSITIVE_INFINITY, 0, 0, 1, 0, 0)));
        assertTrue(Double.isNaN(RobustPredicates.orient3d(0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, Double.NaN)));
        assertTrue(Double.isNaN(RobustPredicates.orient3d(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, Float.NEGATIVE_INFINITY, 0f,
                1f)));
        assertTrue(Double.isNaN(RobustPredicates.incircle(0, 0, 1, 0, 0, 1, Double.NaN, 0.2)));
        assertTrue(Double.isNaN(RobustPredicates.incircle(0, 0, 1, 0, 0, 1, Double.POSITIVE_INFINITY, 0.2)));
        assertTrue(Double.isNaN(RobustPredicates.insphere(0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0.1, Double.NaN, 0.1)));
        assertTrue(Double.isNaN(RobustPredicates.insphere(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0.1f, 0.1f,
                Float.POSITIVE_INFINITY)));
        // finite but huge coordinates still get the exact sign
        assertEquals(1.0, Math.signum(RobustPredicates.orient2d(-1e300, -1e300, 1e300, -1e300, 0, 1e300)));
    }

    @Test
    void floatIncircleAndInsphereMatchTheDoubleVersions() {
        SplittableRandom r = new SplittableRandom(5);
        for (int i = 0; i < 2000; i++) {
            float[] v = new float[15];
            for (int k = 0; k < v.length; k++) {
                // a coarse grid makes cocircular and cospherical (exact-path) cases common
                v[k] = r.nextInt(-4, 5) * 0.25f;
            }
            assertEquals(RobustPredicates.incircle((double) v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]),
                    RobustPredicates.incircle(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]), 0.0);
            assertEquals(RobustPredicates.insphere((double) v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9],
                    v[10], v[11], v[12], v[13], v[14]), RobustPredicates.insphere(v[0], v[1], v[2], v[3], v[4], v[5], v[6],
                    v[7], v[8], v[9], v[10], v[11], v[12], v[13], v[14]), 0.0);
        }
        // d inside the unit circle through (1, 0), (0, 1), (-1, 0) (counter-clockwise)
        assertTrue(RobustPredicates.incircle(1f, 0f, 0f, 1f, -1f, 0f, 0.1f, 0.1f) > 0);
        assertEquals(0.0, RobustPredicates.incircle(1f, 0f, 0f, 1f, -1f, 0f, 0f, -1f), 0.0);
    }

    // ---- SpatialCodes

    @Test
    void morton2dDecodesNegativeCodesFromTheIntOverload() {
        int code = SpatialCodes.morton2d(0xABCD, 0xFFFF);
        assertTrue(code < 0, "y bit 15 sets the sign bit");
        assertEquals(0xABCD, SpatialCodes.morton2dX(code));
        assertEquals(0xFFFF, SpatialCodes.morton2dY(code));
        SplittableRandom r = new SplittableRandom(4);
        for (int i = 0; i < 10_000; i++) {
            int x = r.nextInt(1 << 16), y = r.nextInt(1 << 16);
            int m = SpatialCodes.morton2d(x, y);
            assertEquals(x, SpatialCodes.morton2dX(m));
            assertEquals(y, SpatialCodes.morton2dY(m));
        }
    }

    // ---- MeshMath

    @Test
    void vertexTangentsValidatesItsScratch() {
        float[] pos = {0, 0, 0, 1, 0, 0, 0, 1, 0};
        float[] nrm = {0, 0, 1, 0, 0, 1, 0, 0, 1};
        float[] uv = {0, 0, 1, 0, 0, 1};
        int[] idx = {0, 1, 2};
        float[] tan = new float[12];
        assertEquals(18, MeshMath.vertexTangentsScratchSize(3));
        assertThrows(IllegalArgumentException.class, () -> MeshMath.vertexTangentsScratchSize(-1));
        assertThrows(IllegalArgumentException.class, () -> MeshMath.vertexTangents(pos, nrm, uv, idx, 1, tan,
                new double[18], 1));
        assertThrows(IllegalArgumentException.class, () -> MeshMath.vertexTangents(pos, nrm, uv, idx, 1, tan,
                new double[18], -1));
        assertArrayEquals(new float[12], tan, "nothing written on a bad scratch");
        MeshMath.vertexTangents(pos, nrm, uv, idx, 1, tan, new double[20], 2);
        assertEquals(1f, tan[0], 1e-6f);
        assertEquals(1f, tan[3], 0f);
    }

    // ---- BoundingVolumes

    @Test
    void transformSphereContainsTheTransformedSphereAfterRounding() {
        // a point sphere far from the origin: before 3.0 the radius stayed 0 while the center was rounded to float
        Matrix4x3f m = new Matrix4x3f().translate(1.0e6f, -3.0e5f, 7.0e4f).rotateXYZ(0.3f, -0.7f, 1.1f);
        Vector4f s = new Vector4f(0.1f, 0.2f, 0.3f, 0);
        Vector4f out = BoundingVolumesf.transformSphere(s, m, new Vector4f());
        Vector3d exact = new Vector3d(
                (double) m.m00() * s.x + (double) m.m10() * s.y + (double) m.m20() * s.z + m.m30(),
                (double) m.m01() * s.x + (double) m.m11() * s.y + (double) m.m21() * s.z + m.m31(),
                (double) m.m02() * s.x + (double) m.m12() * s.y + (double) m.m22() * s.z + m.m32());
        assertTrue(exact.distance(out.x, out.y, out.z) <= out.w, "point inside: " + out);
        assertTrue(out.w < 0.1f, "still tight: " + out.w);

        SplittableRandom r = new SplittableRandom(8);
        for (int i = 0; i < 2000; i++) {
            Matrix4x3f mf = new Matrix4x3f().translate((float) r.nextDouble(-1e5, 1e5), (float) r.nextDouble(-1e5, 1e5),
                    (float) r.nextDouble(-1e5, 1e5)).rotateXYZ((float) r.nextDouble(-3, 3), (float) r.nextDouble(-3, 3),
                    (float) r.nextDouble(-3, 3)).scale((float) r.nextDouble(0.2, 3));
            Vector4f sf = new Vector4f((float) r.nextDouble(-50, 50), (float) r.nextDouble(-50, 50),
                    (float) r.nextDouble(-50, 50), (float) r.nextDouble(0, 0.01));
            Vector4f of = BoundingVolumesf.transformSphere(sf, mf, new Vector4f());
            // the farthest transformed surface point is at most |center' - out center| + s r, computed in double
            double cx = (double) mf.m00() * sf.x + (double) mf.m10() * sf.y + (double) mf.m20() * sf.z + mf.m30();
            double cy = (double) mf.m01() * sf.x + (double) mf.m11() * sf.y + (double) mf.m21() * sf.z + mf.m31();
            double cz = (double) mf.m02() * sf.x + (double) mf.m12() * sf.y + (double) mf.m22() * sf.z + mf.m32();
            double scale = new Vector3d(mf.m00(), mf.m01(), mf.m02()).length(); // uniform scale times rotation
            assertTrue(new Vector3d(cx, cy, cz).distance(of.x, of.y, of.z) + sf.w * scale <= of.w, "case " + i);
        }
    }

    @Test
    void cylinderBoundsAreConservative() {
        SplittableRandom r = new SplittableRandom(9);
        MathContext mc = new MathContext(40);
        Vector3f minF = new Vector3f(), maxF = new Vector3f();
        Vector3d minD = new Vector3d(), maxD = new Vector3d();
        for (int i = 0; i < 5000; i++) {
            float[] a = {(float) r.nextDouble(-100, 100), (float) r.nextDouble(-100, 100), (float) r.nextDouble(-100, 100)};
            float[] b = {(float) r.nextDouble(-100, 100), (float) r.nextDouble(-100, 100), (float) r.nextDouble(-100, 100)};
            float rad = (float) r.nextDouble(0.01, 10);
            BoundingVolumesf.aabOfCylinder(new Vector3f(a[0], a[1], a[2]), new Vector3f(b[0], b[1], b[2]), rad, minF, maxF);
            BoundingVolumesd.aabOfCylinder(new Vector3d(a[0], a[1], a[2]), new Vector3d(b[0], b[1], b[2]), rad, minD, maxD);
            float[] lf = {minF.x, minF.y, minF.z}, hf = {maxF.x, maxF.y, maxF.z};
            double[] ld = {minD.x, minD.y, minD.z}, hd = {maxD.x, maxD.y, maxD.z};
            BigDecimal[] u = new BigDecimal[3];
            BigDecimal len2 = BigDecimal.ZERO;
            for (int k = 0; k < 3; k++) {
                u[k] = new BigDecimal(b[k]).subtract(new BigDecimal(a[k]));
                len2 = len2.add(u[k].multiply(u[k]));
            }
            BigDecimal len = len2.sqrt(mc);
            for (int k = 0; k < 3; k++) {
                BigDecimal perp2 = len2.subtract(u[k].multiply(u[k]));
                BigDecimal ext = new BigDecimal(rad).multiply(perp2.sqrt(mc)).divide(len, mc);
                BigDecimal lo = new BigDecimal(Math.min(a[k], b[k])).subtract(ext);
                BigDecimal hi = new BigDecimal(Math.max(a[k], b[k])).add(ext);
                assertTrue(new BigDecimal(lf[k]).compareTo(lo) <= 0 && new BigDecimal(hf[k]).compareTo(hi) >= 0,
                        "float case " + i + " axis " + k);
                assertTrue(new BigDecimal(ld[k]).compareTo(lo) <= 0 && new BigDecimal(hd[k]).compareTo(hi) >= 0,
                        "double case " + i + " axis " + k);
                // and tight: within a few float ulps
                assertTrue(lo.subtract(new BigDecimal(lf[k])).doubleValue() <= 4 * Math.ulp(lf[k]), "tight " + i);
            }
        }
    }

    @Test
    void minimalAreaRectangleAndKDopValidateUpFront() {
        Vector2f c = new Vector2f(9, 9), u = new Vector2f(9, 9), h = new Vector2f(9, 9);
        assertThrows(IllegalArgumentException.class, () -> BoundingVolumesf.minimalAreaRectangle(new float[4], 0, c, u, h));
        assertThrows(IllegalArgumentException.class, () -> BoundingVolumesf.minimalAreaRectangle(new float[4], 3, c, u, h));
        assertEquals(new Vector2f(9, 9), c);
        Vector2d cd = new Vector2d(), ud = new Vector2d(), hd = new Vector2d();
        assertThrows(IllegalArgumentException.class, () -> BoundingVolumesd.minimalAreaRectangle(new double[4], -1, cd, ud,
                hd));
        float[] dirs = new float[20];
        assertThrows(IllegalArgumentException.class, () -> BoundingVolumesf.kDopDirections(14, dirs));
        assertArrayEquals(new float[20], dirs, "nothing written");
        assertEquals(7, BoundingVolumesf.kDopDirections(14, new float[21]));
        assertThrows(IllegalArgumentException.class, () -> BoundingVolumesf.kDop(new float[6], 2, 14, new float[13]));
        assertThrows(IllegalArgumentException.class, () -> BoundingVolumesd.kDop(new double[6], 3, 6, new double[6]));
        // the shared tables are copied, never handed out
        float[] d1 = new float[39];
        BoundingVolumesf.kDopDirections(26, d1);
        d1[0] = 42;
        float[] d2 = new float[39];
        BoundingVolumesf.kDopDirections(26, d2);
        assertEquals(1f, d2[0]);
    }

    // ---- LeastSquares

    @Test
    void leastSquaresWithoutQMatchesTheNormalEquationsAndChecksSizes() {
        SplittableRandom r = new SplittableRandom(12);
        int m = 40, n = 4;
        double[] a = new double[m * n], b = new double[m];
        for (int i = 0; i < a.length; i++) {
            a[i] = r.nextDouble(-1, 1);
        }
        for (int i = 0; i < m; i++) {
            b[i] = r.nextDouble(-1, 1);
        }
        double[] x = new double[n];
        assertTrue(LeastSquaresd.solve(a, b, m, n, x));
        // the residual is orthogonal to the columns of A
        for (int j = 0; j < n; j++) {
            double dot = 0;
            for (int i = 0; i < m; i++) {
                double res = b[i];
                for (int k = 0; k < n; k++) {
                    res -= a[i * n + k] * x[k];
                }
                dot += a[i * n + j] * res;
            }
            assertEquals(0, dot, 1e-13);
        }
        // the scratch overload gives the same bits, ignores stale scratch, and x may alias b
        double[] scratch = new double[LeastSquaresd.scratchSize(m, n) + 5];
        java.util.Arrays.fill(scratch, Double.NaN);
        double[] x2 = new double[n];
        assertTrue(LeastSquaresd.solve(a, b, m, n, x2, scratch, 5));
        assertArrayEquals(x, x2, 0.0);
        double[] bAlias = b.clone();
        assertTrue(LeastSquaresd.solve(a, bAlias, m, n, bAlias));
        assertArrayEquals(x, java.util.Arrays.copyOf(bAlias, n), 0.0);
        assertEquals(m * n + m, LeastSquaresd.scratchSize(m, n));
        assertEquals(m * n + m, LeastSquaresf.scratchSize(m, n));
        assertThrows(IllegalArgumentException.class, () -> LeastSquaresd.solve(a, b, m, n, x2, new double[10], 0));
        assertThrows(IllegalArgumentException.class, () -> LeastSquaresd.solve(new double[3], b, m, n, x2));
        assertThrows(IllegalArgumentException.class, () -> LeastSquaresd.solve(a, b, m, n, new double[n - 1]));
        assertThrows(IllegalArgumentException.class, () -> LeastSquaresd.scratchSize(2, 3));
        // rank deficient: false, and x is left unchanged
        double[] keep = {5, 6};
        assertFalse(LeastSquaresd.solve(new double[] {1, 2, 2, 4, 3, 6}, new double[] {1, 2, 3}, 3, 2, keep));
        assertArrayEquals(new double[] {5, 6}, keep, 0.0);
        float[] keepF = {5, 6};
        assertFalse(LeastSquaresf.solve(new float[] {1, 2, 2, 4, 3, 6}, new float[] {1, 2, 3}, 3, 2, keepF));
        assertArrayEquals(new float[] {5, 6}, keepF, 0f);
        // float: computed in double from the same values
        float[] af = new float[m * n], bf = new float[m], xf = new float[n];
        double[] ad = new double[m * n], bd = new double[m], xd = new double[n];
        for (int i = 0; i < af.length; i++) {
            af[i] = (float) a[i];
            ad[i] = af[i];
        }
        for (int i = 0; i < m; i++) {
            bf[i] = (float) b[i];
            bd[i] = bf[i];
        }
        assertTrue(LeastSquaresf.solve(af, bf, m, n, xf));
        assertTrue(LeastSquaresd.solve(ad, bd, m, n, xd));
        for (int k = 0; k < n; k++) {
            assertEquals((float) xd[k], xf[k], 0f);
        }
    }

    // ---- Philox

    @Test
    void philoxBlockChecksItsRange() {
        int[] out = {9, 9, 9, 9, 9};
        assertThrows(IllegalArgumentException.class, () -> Philox.block(0, 0, 0, 0, 0, 0, out, 2));
        assertThrows(IllegalArgumentException.class, () -> Philox.block(0, 0, 0, 0, 0, 0, out, -1));
        assertThrows(IllegalArgumentException.class, () -> Philox.block(0, 0, 0, 0, 0, 0, null, 0));
        assertArrayEquals(new int[] {9, 9, 9, 9, 9}, out);
        Philox.block(0, 0, 0, 0, 0, 0, out, 1);
        assertEquals(9, out[0]);
    }

    @Test
    void doubleUniformsCarry53BitsFromTwoWords() {
        long seed = 0x0123456789ABCDEFL;
        boolean lowBitSeen = false;
        for (long i = 0; i < 4096; i++) {
            double u = Philox.uniformDouble(seed, i);
            assertTrue(u >= 0 && u < 1);
            long bits = (long) (u * 0x1p53);
            assertEquals(u, bits * 0x1p-53, 0.0, "a multiple of 2^-53");
            long hi = Philox.word(seed, 2 * i) & 0xffffffffL;
            long lo = (Philox.word(seed, 2 * i + 1) >>> 11) & 0x1fffffL;
            assertEquals((hi << 21) | lo, bits, "words 2i and 2i + 1 at " + i);
            lowBitSeen |= (bits & 1) != 0;
        }
        assertTrue(lowBitSeen, "more than 24 bits of randomness");
        double[] all = new double[100];
        Philox.fillUniform(seed, 1000, all, 0, 100);
        double[] parts = new double[102];
        Philox.fillUniform(seed, 1000, parts, 2, 37);
        Philox.fillUniform(seed, 1037, parts, 39, 63);
        assertArrayEquals(all, java.util.Arrays.copyOfRange(parts, 2, 102), 0.0);
        assertThrows(IllegalArgumentException.class, () -> Philox.fillUniform(seed, 0, new double[3], 1, 3));
    }

    @Test
    void doubleNormalsAreSplitInvariantAndStandard() {
        long seed = 77;
        int n = 200_000;
        double[] all = new double[n];
        Philox.fillNormal(seed, 5, 0.0, 1.0, all, 0, n);
        double[] parts = new double[n];
        Philox.fillNormal(seed, 5, 0.0, 1.0, parts, 0, 1001); // an odd split: a pair spans the calls
        Philox.fillNormal(seed, 1006, 0.0, 1.0, parts, 1001, n - 1001);
        assertArrayEquals(all, parts, 0.0);
        double sum = 0, sum2 = 0;
        for (double v : all) {
            assertTrue(Double.isFinite(v));
            sum += v;
            sum2 += v * v;
        }
        double mean = sum / n, var = sum2 / n - mean * mean;
        assertEquals(0, mean, 0.01);
        assertEquals(1, var, 0.01);
        double[] shifted = new double[4];
        Philox.fillNormal(seed, 5, 3.0, 2.0, shifted, 0, 4);
        for (int i = 0; i < 4; i++) {
            assertEquals(Math.fma(all[i], 2.0, 3.0), shifted[i], 1e-15);
        }
        assertThrows(IllegalArgumentException.class, () -> Philox.fillNormal(seed, 0, 0.0, 1.0, new double[2], 0, 3));
    }
}
