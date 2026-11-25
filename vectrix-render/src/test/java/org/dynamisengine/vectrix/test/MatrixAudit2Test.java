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

import org.dynamisengine.vectrix.core.DepthRange;
import org.dynamisengine.vectrix.camera.Projectionsd;
import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Matrix4dc;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Matrix4x3d;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector4d;
import org.junit.jupiter.api.Test;

/** Second audit of the JOML-inherited matrices (2.5.0): property flags and projection builders. */
class MatrixAudit2Test {
    private static final Matrix4d AFFINE = new Matrix4d().translation(0.3, -1.2, 2).rotateXYZ(0.4, -0.7, 1.1).scale(1.5, 0.7, 2.2);

    private static double maxDiff(Matrix4dc a, Matrix4dc b) {
        double m = 0;
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                m = Math.max(m, Math.abs(a.get(c, r) - b.get(c, r)));
            }
        }
        return m;
    }

    /** The flags must lead fast paths to the same answers as the generic path on the same elements. */
    private static void assertHonest(Matrix4dc m, String what) {
        Matrix4d generic = new Matrix4d(m).assume(Matrix4dc.PROPERTY_UNKNOWN);
        double scale = 1 + maxDiff(generic, new Matrix4d().zero());
        if (Math.abs(generic.determinant()) > 1e-9) { // a shadow projection, for example, is singular by design
            double inv = 1 + maxDiff(new Matrix4d(generic).invert(), new Matrix4d().zero());
            assertTrue(maxDiff(new Matrix4d(m).invert(), new Matrix4d(generic).invert()) < 1e-10 * inv, what + ": invert, props " + m.properties());
        }
        assertTrue(maxDiff(new Matrix4d(m).mul(AFFINE), new Matrix4d(generic).mul(AFFINE)) < 1e-10 * scale, what + ": mul");
        assertTrue(maxDiff(new Matrix4d(AFFINE).mul(m), new Matrix4d(AFFINE).mul(generic)) < 1e-10 * scale, what + ": premul");
        Vector3d p = new Vector3d(0.3, 1.7, -2.1);
        assertTrue(m.transformProject(p, new Vector3d()).distance(generic.transformProject(p, new Vector3d())) < 1e-10 * scale,
                what + ": transformProject");
    }

    @Test
    void flagsStayHonest() {
        Quaterniond q = new Quaterniond().rotateXYZ(0.3, -0.5, 0.9);
        Matrix4d persp = new Matrix4d().perspective(1, 1.5, 0.1, 100);
        // translationRotate on a matrix that held a projection: the 4th row must be rewritten
        Matrix4d tr = new Matrix4d(persp).translationRotate(1, 2, 3, q);
        assertEquals(0.0, tr.m23(), 0.0);
        assertTrue(maxDiff(tr, new Matrix4d().translation(1, 2, 3).rotate(q)) < 1e-15);
        assertHonest(tr, "translationRotate");
        Matrix4f trf = new Matrix4f().perspective(1, 1.5f, 0.1f, 100).translationRotate(1, 2, 3, new Quaternionf(q));
        assertEquals(0.0f, trf.m23(), 0.0f);
        assertHonest(new Matrix4d(new Matrix4d().translation(1, 2, 3).m13(0.25)), "m13 setter"); // 0.5 would be singular
        assertEquals(0, new Matrix4d().translation(1, 2, 3).m13(0.5).properties() & Matrix4dc.PROPERTY_AFFINE);
        assertHonest(new Matrix4d().shadow(1, 4, 2, 1, 0, 1, 0, 0), "shadow (point light)");
        assertHonest(new Matrix4d(AFFINE).shadow(1, 4, 2, 0, 0, 1, 0, 0), "shadow (directional)");
        Matrix4d p = persp;
        assertHonest(new Matrix4d().translation(1, 2, 3).mul(p.m00(), p.m01(), p.m02(), p.m03(), p.m10(), p.m11(), p.m12(), p.m13(),
                p.m20(), p.m21(), p.m22(), p.m23(), p.m30(), p.m31(), p.m32(), p.m33()), "mul(16 scalars)");
        assertHonest(new Matrix4d().rotationX(0).lerp(new Matrix4d().rotationX(3), 0.5, new Matrix4d()), "lerp of rotations");
        Matrix4d dest = new Matrix4d().translation(5, 6, 7);
        new Matrix4d().rotationXYZ(0.2, 0.4, 0.6).transpose3x3(dest);
        assertEquals(7.0, dest.m32(), 0.0, "transpose3x3 keeps dest's translation");
        assertHonest(dest, "transpose3x3(dest affine)");
        Matrix4d destP = new Matrix4d(persp);
        new Matrix4d().rotationXYZ(0.2, 0.4, 0.6).transpose3x3(destP);
        assertHonest(destP, "transpose3x3(dest perspective)");
        assertHonest(new Matrix4d().perspective(1, 1, 0.1, 10).perspective(0.8, 1.2, 0.5, 20), "perspective * perspective");
        assertHonest(new Matrix4d().perspective(1, 1, 0.1, 10).perspectiveLH(0.8, 1.2, 0.5, 20), "perspective * perspectiveLH");
        // non-unit quaternions give |q|^2 R, which is not orthonormal; unit ones keep the fast path
        Quaterniond big = new Quaterniond(q).scale(Math.sqrt(2));
        assertHonest(new Matrix4d().translationRotate(1, 2, 3, big), "translationRotate(non-unit q)");
        assertHonest(new Matrix4d().rotation(big), "rotation(non-unit q)");
        assertHonest(new Matrix4d(AFFINE).rotate(big), "rotate(non-unit q)");
        assertTrue((new Matrix4d().rotation(q).properties() & Matrix4dc.PROPERTY_ORTHONORMAL) != 0, "unit quaternions stay orthonormal");
        // Matrix4x3: lerp and transpose3x3 flags, checked through a flag-free 4x4 inverse
        Matrix4x3d l43 = new Matrix4x3d().rotationX(0).lerp(new Matrix4x3d().rotationX(3), 0.5, new Matrix4x3d());
        Matrix4d l44 = new Matrix4d().set(l43);
        assertTrue(maxDiff(new Matrix4d().set(new Matrix4x3d(l43).invert()), new Matrix4d(l44).assume(0).invert()) < 1e-12, "4x3 lerp");
        Matrix4x3d d43 = new Matrix4x3d().translation(5, 6, 7);
        new Matrix4x3d().rotationXYZ(0.2, 0.4, 0.6).transpose3x3(d43);
        assertTrue(maxDiff(new Matrix4d().set(new Matrix4x3d(d43).invert()), new Matrix4d().set(d43).assume(0).invert()) < 1e-12, "4x3 transpose3x3");
    }

    @Test
    void shadowProjectsOntoThePlane() {
        // point light L = (1, 4, 2) onto y = 0: the shadow of P is where the ray from L through P meets the plane
        Vector3d s = new Matrix4d().shadow(1, 4, 2, 1, 0, 1, 0, 0).transformProject(new Vector3d(0.3, 1, 0.2));
        assertTrue(s.distance(1 + (0.3 - 1) * 4 / 3.0, 0, 2 + (0.2 - 2) * 4 / 3.0) < 1e-15, s.toString());
        // directional light along (1, 4, 2): P - (P.y / 4) (1, 4, 2)
        Vector3d d = new Matrix4d().shadow(1, 4, 2, 0, 0, 1, 0, 0).transformProject(new Vector3d(0.3, 1, 0.2));
        assertTrue(d.distance(0.3 - 0.25, 0, 0.2 - 0.5) < 1e-15, d.toString());
    }

    @Test
    void projectionBuilders() {
        // setFromIntrinsic: the optical axis lands on the principal point, near/far on NDC -1/+1
        Matrix4d k = new Matrix4d().setFromIntrinsic(800, 820, 2, 300, 260, 640, 480, 0.1, 100);
        Vector3d axis = k.transformProject(new Vector3d(0, 0, -5));
        assertEquals(300, (axis.x + 1) / 2 * 640, 1e-9, "principal point x");
        assertEquals(260, (axis.y + 1) / 2 * 480, 1e-9, "principal point y");
        assertEquals(-1, k.transformProject(new Vector3d(0, 0, -0.1)).z, 1e-12);
        assertEquals(1, k.transformProject(new Vector3d(0, 0, -100)).z, 1e-12);
        assertHonest(k, "setFromIntrinsic");
        // left-handed frustums are the right-handed ones with z flipped, off-centre included
        for (boolean z01 : new boolean[] {false, true}) {
            Matrix4d lh = new Matrix4d().frustumLH(-2, 3, -1, 4, 0.5, 50, z01);
            Matrix4d expect = new Matrix4d().frustum(-2, 3, -1, 4, 0.5, 50, z01).scale(1, 1, -1);
            assertTrue(maxDiff(lh, expect) < 1e-15, "frustumLH " + z01);
            assertTrue(maxDiff(new Matrix4d().setFrustumLH(-2, 3, -1, 4, 0.5, 50, z01), expect) < 1e-15, "setFrustumLH " + z01);
            Matrix4d fov = new Matrix4d().perspectiveOffCenterFovLH(-0.3, 0.5, -0.2, 0.4, 0.5, 50, z01);
            Vector3d corner = fov.transformProject(new Vector3d(Math.tan(-0.3) * 0.5, Math.tan(-0.2) * 0.5, 0.5));
            assertEquals(-1, corner.x, 1e-12);
            assertEquals(-1, corner.y, 1e-12);
            // zNear = +inf: depth runs from the far plane (1) towards the near value at infinity, inside the range
            for (Matrix4d inf : new Matrix4d[] {new Matrix4d().perspectiveLH(1, 1, Double.POSITIVE_INFINITY, 50, z01),
                new Matrix4d().setPerspectiveLH(1, 1, Double.POSITIVE_INFINITY, 50, z01),
                new Matrix4d().frustumLH(-1, 1, -1, 1, Double.POSITIVE_INFINITY, 50, z01)}) {
                double atFar = inf.transformProject(new Vector3d(0, 0, 50)).z;
                double farAway = inf.transformProject(new Vector3d(0, 0, 1e12)).z;
                assertEquals(1, atFar, 1e-5, "far plane " + z01);
                assertTrue(farAway < atFar && farAway >= (z01 ? 0 : -1), "infinity stays inside, " + z01 + ": " + farAway);
            }
        }
        // rotationTowardsXY sends +X to (dirX, dirY)
        Vector3d x = new Matrix4d().rotationTowardsXY(0.6, 0.8).transformDirection(new Vector3d(1, 0, 0));
        assertTrue(x.distance(0.6, 0.8, 0) < 1e-15, x.toString());
        assertTrue(new Matrix4d().rotationTowardsXY(1, 0).equals(new Matrix4d(), 0), "dir +X is the identity");
        assertTrue(maxDiff(new Matrix4d(AFFINE).rotateTowardsXY(0.6, 0.8), new Matrix4d(AFFINE).mul(new Matrix4d().rotationTowardsXY(0.6, 0.8))) < 1e-15);
        Vector3d xf = new Vector3d();
        new Matrix4f().rotationTowardsXY(0.6f, 0.8f).transformDirection(new org.dynamisengine.vectrix.core.Vector3f(1, 0, 0))
                .get(xf);
        assertTrue(xf.distance(0.6, 0.8, 0) < 1e-7);
    }

    @Test
    void unprojectRayStartsOnTheNearPlane() {
        int[] viewport = {0, 0, 800, 600};
        Matrix4d view = new Matrix4d().lookAt(3, 2, 5, 0, 0, 0, 0, 1, 0);
        Vector3d eye = new Vector3d(3, 2, 5), forward = new Vector3d(eye).negate().normalize();
        for (DepthRange r : DepthRange.values()) {
            Matrix4d vp = Projectionsd.perspective(1.1, 1.6, 0.1, 100, r, new Matrix4d()).mul(view);
            Vector3d o = new Vector3d(), d = new Vector3d();
            vp.unprojectRay(400, 300, viewport, r, o, d);
            assertEquals(0.1, o.distance(eye), 1e-9, "origin on the near plane, " + r);
            assertTrue(new Vector3d(d).normalize().dot(forward) > 1 - 1e-9, "direction forward, " + r);
            Vector3d oi = new Vector3d(), di = new Vector3d();
            new Matrix4d(vp).invert().unprojectInvRay(400, 300, viewport, r, oi, di);
            assertTrue(oi.distance(o) < 1e-9 && new Vector3d(di).normalize().distance(new Vector3d(d).normalize()) < 1e-9, "inverse form " + r);
        }
        Matrix4d inf = Projectionsd.reverseZInfinite(1.1, 1.6, 0.1, new Matrix4d()).mul(view);
        Vector3d o = new Vector3d(), d = new Vector3d();
        inf.unprojectRay(400, 300, viewport, DepthRange.REVERSED_ZERO_TO_ONE, o, d);
        assertEquals(0.1, o.distance(eye), 1e-9, "infinite reverse-Z origin");
        assertTrue(new Vector3d(d).normalize().dot(forward) > 1 - 1e-9, "infinite reverse-Z direction");
        assertTrue(Double.isFinite(d.length()));
    }

    @Test
    void extremeScales() {
        // column lengths: no overflow or underflow at representable scales
        for (double sc : new double[] {1e155, 1e-162, 1e200, 1e-300}) {
            Vector3d sd = new Matrix4d().scaling(sc, 2 * sc, 0.5 * sc).getScale(new Vector3d());
            assertTrue(Math.abs(sd.x / sc - 1) < 1e-15 && Math.abs(sd.y / sc - 2) < 1e-15, "double getScale at " + sc + ": " + sd);
            assertEquals(1.0, new Matrix4d().scaling(sc, 1, 1).normalize3x3().m00(), 1e-15, "normalize3x3 at " + sc);
        }
        for (float sc : new float[] {2e19f, 1e-25f, 1e-35f}) {
            org.dynamisengine.vectrix.core.Vector3f sf = new Matrix4f().scaling(sc, sc, sc).getScale(new org.dynamisengine.vectrix.core.Vector3f());
            assertEquals(1.0, sf.x / sc, 1e-6, "float getScale at " + sc);
            assertEquals(1.0f, new Matrix4f().scaling(sc, 1, 1).normalize3x3().m00(), 1e-6f, "float normalize3x3 at " + sc);
        }
        // float inverses are computed in double: no Inf/NaN for well-conditioned matrices of extreme scale
        for (float sc : new float[] {1e-15f, 1e20f, 1e-30f}) {
            Matrix4f m = new Matrix4f().translation(1, 2, 3).rotateXYZ(0.3f, 0.2f, 0.1f).scale(sc);
            Matrix4d exact = new Matrix4d().set(m).invert();
            for (Matrix4f inv : new Matrix4f[] {new Matrix4f(m).invert(), new Matrix4f(m).invertAffine(), new Matrix4f(m).assume(0).invert()}) {
                for (int c = 0; c < 4; c++) {
                    for (int r = 0; r < 4; r++) {
                        double e = exact.get(c, r), g = inv.get(c, r);
                        assertTrue(Double.isFinite(g) && Math.abs(g - e) <= 1e-6 * (Math.abs(e) + 1e-30 + Math.abs(exact.get(c, c))), "float invert at " + sc
                                + " [" + c + "," + r + "] " + g + " vs " + e);
                    }
                }
            }
            org.dynamisengine.vectrix.core.Matrix3f m3 = new org.dynamisengine.vectrix.core.Matrix3f().scaling(sc).invert();
            assertEquals(1.0, m3.m00() * (double) sc, 1e-6, "Matrix3f.invert at " + sc);
            org.dynamisengine.vectrix.core.Matrix4x3f m43 = new org.dynamisengine.vectrix.core.Matrix4x3f().translation(1, 2, 3).scale(sc).invert();
            assertEquals(1.0, m43.m00() * (double) sc, 1e-6, "Matrix4x3f.invert at " + sc);
        }
    }

}
