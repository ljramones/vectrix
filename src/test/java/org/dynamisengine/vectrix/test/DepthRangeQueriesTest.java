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

import org.dynamisengine.vectrix.core.DepthRange;
import org.dynamisengine.vectrix.camera.Projectionsd;
import org.dynamisengine.vectrix.camera.Projectionsf;
import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Matrix4dc;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Matrix4fc;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.geometry.FrustumIntersection;
import org.dynamisengine.vectrix.geometry.FrustumPlanes;
import org.junit.jupiter.api.Test;

/**
 * The JOML-inherited projection queries assumed OpenGL's [-1, 1] depth. Their DepthRange forms (2.5.0) must give
 * the same physical answers for [-1, 1], [0, 1] and reverse-Z projections of one camera.
 */
class DepthRangeQueriesTest {
    private static final float FOV = 1.1f, ASPECT = 1.6f, NEAR = 0.1f, FAR = 100f;
    private static final DepthRange[] RANGES = DepthRange.values();

    private static Matrix4f projf(DepthRange r) {
        return Projectionsf.perspective(FOV, ASPECT, NEAR, FAR, r, new Matrix4f());
    }

    private static Matrix4d projd(DepthRange r) {
        return Projectionsd.perspective(FOV, ASPECT, NEAR, FAR, r, new Matrix4d());
    }

    private static final Matrix4d VIEW = new Matrix4d().lookAt(3, 2, 5, 0.5, -0.2, 0.1, 0, 1, 0);

    @Test
    void nearFarDistances() {
        for (DepthRange r : RANGES) {
            assertEquals(NEAR, projf(r).perspectiveNear(r), 1e-6f * NEAR, "float near " + r);
            assertEquals(FAR, projf(r).perspectiveFar(r), 2e-5f * FAR, "float far " + r);
            assertEquals(Projectionsf.nearPlane(projf(r), r), projf(r).perspectiveNear(r), 1e-6f * NEAR, "agrees with Projectionsf " + r);
            assertEquals(NEAR, projd(r).perspectiveNear(r), 1e-14 * NEAR, "double near " + r);
            assertEquals(FAR, projd(r).perspectiveFar(r), 1e-12 * FAR, "double far " + r);
        }
        // the OpenGL form misreads a [0, 1] projection (the original defect)
        assertEquals(0.050025, projd(DepthRange.ZERO_TO_ONE).perspectiveNear(), 1e-6);
        assertEquals(Double.POSITIVE_INFINITY, Projectionsd.reverseZInfinite(FOV, ASPECT, NEAR, new Matrix4d())
                .perspectiveFar(DepthRange.REVERSED_ZERO_TO_ONE), 0.0);
    }

    @Test
    void cornersPlanesAndAabbsAreTheSamePhysicalFrustum() {
        Vector3d[] reference = new Vector3d[8];
        Vector3d mn = new Vector3d(), mx = new Vector3d(), refMin = null, refMax = null;
        for (DepthRange r : RANGES) {
            Matrix4d vp = new Matrix4d(projd(r)).mul(VIEW);
            for (int c = 0; c < 8; c++) {
                Vector3d p = vp.frustumCorner(c, r, new Vector3d());
                // it reprojects to the NDC corner of this depth range
                Vector4d ndc = vp.transform(new Vector4d(p, 1));
                boolean far = c >= Matrix4dc.CORNER_PXNYPZ;
                assertEquals(far ? r.farNdc() : r.nearNdc(), ndc.z / ndc.w, 1e-9, r + " corner " + c);
                assertEquals(1.0, Math.abs(ndc.x / ndc.w), 1e-9);
                if (reference[c] == null) {
                    reference[c] = p;
                } else {
                    assertTrue(p.distance(reference[c]) < 1e-9 * (1 + p.length()), "corner " + c + " differs for " + r);
                }
            }
            // each depth plane contains its four corners, and the frustum center is inside every plane
            Vector3d center = new Vector3d();
            for (Vector3d p : reference) {
                center.add(p);
            }
            center.div(8);
            for (int pl = 0; pl < 6; pl++) {
                Vector4d plane = vp.frustumPlane(pl, r, new Vector4d());
                assertTrue(plane.x * center.x + plane.y * center.y + plane.z * center.z + plane.w > 0, r + " plane " + pl + " faces in");
            }
            Vector4d nearPlane = vp.frustumPlane(Matrix4dc.PLANE_NZ, r, new Vector4d());
            for (int c = 0; c < 4; c++) {
                Vector3d p = reference[c];
                assertEquals(0.0, nearPlane.x * p.x + nearPlane.y * p.y + nearPlane.z * p.z + nearPlane.w, 1e-9, r + " near plane");
            }
            new Matrix4d(vp).invert().frustumAabb(r, mn, mx);
            if (refMin == null) {
                refMin = new Vector3d(mn);
                refMax = new Vector3d(mx);
            }
            assertTrue(mn.distance(refMin) < 1e-9 && mx.distance(refMax) < 1e-9, "frustumAabb " + r + " " + mn + mx);
        }
        // the float twin, and the OpenGL form's error on a [0, 1] matrix
        Matrix4f vpf = new Matrix4f(projf(DepthRange.ZERO_TO_ONE)).mul(new Matrix4f().set(VIEW));
        Vector3f nf = vpf.frustumCorner(Matrix4fc.CORNER_NXNYNZ, DepthRange.ZERO_TO_ONE, new Vector3f());
        assertTrue(new Vector3d(nf.x, nf.y, nf.z).distance(reference[0]) < 1e-5, "float corner " + nf);
        Vector3f wrong = vpf.frustumCorner(Matrix4fc.CORNER_NXNYNZ, new Vector3f());
        assertTrue(new Vector3d(wrong.x, wrong.y, wrong.z).distance(reference[0]) > 1e-2, "the OpenGL form is off for [0, 1]");
    }

    @Test
    void projectAndUnprojectUseTheDepthBufferConvention() {
        int[] viewport = {0, 0, 800, 600};
        Vector3d eye = new Vector3d(3, 2, 5);
        for (DepthRange r : RANGES) {
            Matrix4d vp = new Matrix4d(projd(r)).mul(VIEW);
            // window depth at the near plane is the range's near NDC depth (or (z + 1) / 2 for OpenGL)
            double nearDepth = r == DepthRange.NEGATIVE_ONE_TO_ONE ? 0 : r.nearNdc();
            Vector3d p = vp.unproject(400, 300, nearDepth, viewport, r, new Vector3d());
            assertEquals(NEAR, p.distance(eye), 1e-9, "near-plane depth unprojects to the near distance, " + r);
            Vector3d w = vp.project(p.x, p.y, p.z, viewport, r, new Vector3d());
            assertEquals(nearDepth, w.z, 1e-9, "project round trip " + r);
            Vector3d q = new Vector3d(0.7, 0.1, -0.4);
            Vector3d back = vp.unproject(vp.project(q.x, q.y, q.z, viewport, r, new Vector3d()), viewport, new Vector3d());
            if (r == DepthRange.NEGATIVE_ONE_TO_ONE) {
                assertTrue(back.distance(q) < 1e-9, "OpenGL vector form unchanged");
            }
            Vector3d w2 = vp.project(q.x, q.y, q.z, viewport, r, new Vector3d());
            assertTrue(vp.unproject(w2.x, w2.y, w2.z, viewport, r, new Vector3d()).distance(q) < 1e-9, "round trip " + r);
            Matrix4d inv = new Matrix4d(vp).invert();
            assertTrue(inv.unprojectInv(w2.x, w2.y, w2.z, viewport, r, new Vector3d()).distance(q) < 1e-9, "unprojectInv " + r);
        }
    }

    @Test
    void slicesKeepTheFieldOfView() {
        for (DepthRange r : RANGES) {
            Matrix4d slice = projd(r).perspectiveFrustumSlice(1, 10, r, new Matrix4d());
            Matrix4d direct = Projectionsd.perspective(FOV, ASPECT, 1, 10, r, new Matrix4d());
            assertTrue(slice.equals(direct, 1e-12), r + "\n" + slice + "\n" + direct);
            assertEquals(FOV, slice.perspectiveFov(), 1e-12);
        }
        // the OpenGL form: the field of view was narrowed tenfold before 2.5.0
        Matrix4f s = new Matrix4f().perspective(FOV, ASPECT, NEAR, FAR).perspectiveFrustumSlice(1, 10, new Matrix4f());
        assertTrue(s.equals(new Matrix4f().perspective(FOV, ASPECT, 1, 10), 1e-5f), s.toString());
        // aliasing dest with this
        Matrix4d self = projd(DepthRange.ZERO_TO_ONE);
        self.perspectiveFrustumSlice(1, 10, DepthRange.ZERO_TO_ONE, self);
        assertTrue(self.equals(Projectionsd.perspective(FOV, ASPECT, 1, 10, DepthRange.ZERO_TO_ONE, new Matrix4d()), 1e-12));
    }

    @Test
    void orthoCropAndGridRangeFollowThePhysicalFrustum() {
        Matrix4d light = new Matrix4d().lookAt(10, 20, 5, 0, 0, 0, 0, 1, 0);
        Matrix4d reference = null;
        for (DepthRange r : RANGES) {
            Matrix4d invVp = new Matrix4d(projd(r)).mul(VIEW).invert();
            Matrix4d crop = invVp.orthoCrop(light, r, new Matrix4d());
            // every frustum corner lands inside the crop's NDC box, on this range's depth interval
            double lo = Math.min(r.nearNdc(), r.farNdc()), hi = Math.max(r.nearNdc(), r.farNdc());
            for (int c = 0; c < 8; c++) {
                Vector3d p = new Matrix4d(projd(r)).mul(VIEW).frustumCorner(c, r, new Vector3d());
                Vector3d ndc = crop.mul(light, new Matrix4d()).transformProject(p, new Vector3d());
                assertTrue(Math.abs(ndc.x) <= 1 + 1e-9 && Math.abs(ndc.y) <= 1 + 1e-9, r + " corner " + c + " " + ndc);
                assertTrue(ndc.z >= lo - 1e-9 && ndc.z <= hi + 1e-9, r + " crop depth " + ndc.z);
            }
            Matrix4d grid = new Matrix4d();
            // only this (the camera's inverse view-projection) depends on the depth range; the projector is fixed
            Matrix4d projector = new Matrix4d(projd(DepthRange.NEGATIVE_ONE_TO_ONE)).mul(VIEW);
            Matrix4d range = invVp.projectedGridRange(projector, -0.5, 0.5, r, grid);
            assertTrue(range != null, "the grid plane crosses the frustum");
            if (range != null) {
                if (reference == null) {
                    reference = new Matrix4d(grid);
                } else {
                    assertTrue(grid.equals(reference, 1e-9), "projectedGridRange " + r);
                }
            }
        }
    }

    @Test
    void cullingPlanesSitAtTheRealNearAndFar() {
        for (DepthRange r : RANGES) {
            Matrix4f vp = new Matrix4f(projf(r)).mul(new Matrix4f().set(VIEW));
            FrustumIntersection fi = new FrustumIntersection().set(vp, true, r);
            FrustumPlanes fp = new FrustumPlanes().set(vp, true, r);
            Vector3d eye = new Vector3d(3, 2, 5), dir = new Vector3d(0.5, -0.2, 0.1).sub(eye).normalize();
            for (double[] dd : new double[][] {{0.95 * NEAR, 0}, {1.05 * NEAR, 1}, {0.99 * FAR, 1}, {1.01 * FAR, 0}}) {
                Vector3d p = new Vector3d(dir).mul(dd[0]).add(eye);
                boolean inside = dd[1] == 1;
                assertEquals(inside, fi.testPoint((float) p.x, (float) p.y, (float) p.z), r + " FrustumIntersection at " + dd[0]);
                boolean planesInside = true;
                for (int pl = 0; pl < FrustumPlanes.COUNT; pl++) {
                    planesInside &= fp.x[pl] * p.x + fp.y[pl] * p.y + fp.z[pl] * p.z + fp.w[pl] >= 0;
                }
                assertEquals(inside, planesInside, r + " FrustumPlanes at " + dd[0]);
            }
        }
        // the OpenGL form keeps points between n/2 and n of a [0, 1] projection (it culls too little)
        Matrix4f vk = new Matrix4f(projf(DepthRange.ZERO_TO_ONE)).mul(new Matrix4f().set(VIEW));
        Vector3d eye = new Vector3d(3, 2, 5), dir = new Vector3d(0.5, -0.2, 0.1).sub(eye).normalize();
        Vector3d close = new Vector3d(dir).mul(0.7 * NEAR).add(eye);
        assertTrue(new FrustumIntersection().set(vk).testPoint((float) close.x, (float) close.y, (float) close.z));
        assertFalse(new FrustumIntersection().set(vk, true, DepthRange.ZERO_TO_ONE).testPoint((float) close.x, (float) close.y,
                (float) close.z));
    }

}
