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

import org.dynamisengine.vectrix.affine.Affine4d;
import org.dynamisengine.vectrix.affine.Affine4f;
import org.dynamisengine.vectrix.affine.DualQuatTransformd;
import org.dynamisengine.vectrix.affine.DualQuatTransformf;
import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.affine.PackedAffineKernels;
import org.dynamisengine.vectrix.affine.Transformd;
import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.animation.PoseKernelsd;
import org.dynamisengine.vectrix.animation.PoseKernelsf;
import org.dynamisengine.vectrix.camera.Projectionsd;
import org.dynamisengine.vectrix.camera.Projectionsf;
import org.dynamisengine.vectrix.core.DepthRange;
import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Matrix4dc;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Options;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Runtime;
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3L;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.easing.Easingd;
import org.dynamisengine.vectrix.easing.Easingf;
import org.dynamisengine.vectrix.ik.DampedLeastSquaresd;
import org.dynamisengine.vectrix.ik.DampedLeastSquaresf;
import org.dynamisengine.vectrix.ik.JointLimitsd;
import org.dynamisengine.vectrix.ik.JointLimitsf;
import org.dynamisengine.vectrix.physics.PbdConstraintsd;
import org.dynamisengine.vectrix.physics.PbdConstraintsf;
import org.dynamisengine.vectrix.physics.PdControllersd;
import org.dynamisengine.vectrix.physics.PdControllersf;
import org.dynamisengine.vectrix.world.WorldPos;
import org.junit.jupiter.api.Test;

/**
 * 3.0 fixes in core, affine, world, camera, animation, ik, physics and easing. Each test reproduces a defect found
 * while documenting 2.6.0 and checks the fixed behavior.
 */
class KinematicsFixes30Test {

    // ---- core

    @Test
    void matrix4dcDeclaresFrustumAabb() {
        Matrix4d vp = Projectionsd.perspective(1.0, 1.5, 0.5, 20.0, DepthRange.ZERO_TO_ONE, new Matrix4d());
        Matrix4dc inv = new Matrix4d(vp).invert();
        Vector3d min = new Vector3d(), max = new Vector3d();
        inv.frustumAabb(DepthRange.ZERO_TO_ONE, min, max);
        assertEquals(-20.0, min.z, 1e-9);
        assertEquals(-0.5, max.z, 1e-9);
        Vector3d glMin = new Vector3d(), glMax = new Vector3d();
        Matrix4dc unit = new Matrix4d();
        unit.frustumAabb(glMin, glMax);
        assertEquals(new Vector3d(-1, -1, -1), glMin);
        assertEquals(new Vector3d(1, 1, 1), glMax);
    }

    @Test
    void formatPadsNanAndInfinityToTheWidthOfZero() {
        int width = Options.NUMBER_FORMAT.format(0.0).length();
        assertEquals(width, Runtime.format(Double.NaN, Options.NUMBER_FORMAT).length());
        assertEquals(width, Runtime.format(Double.POSITIVE_INFINITY, Options.NUMBER_FORMAT).length());
        assertEquals(width, Runtime.format(Double.NEGATIVE_INFINITY, Options.NUMBER_FORMAT).length());
        assertTrue(Runtime.format(Double.NaN, Options.NUMBER_FORMAT).endsWith("NaN"));
    }

    // ---- affine

    @Test
    void affine4dToMatrix4dFillsAMatrix4d() {
        Affine4d a = new Affine4d().translationRotateScale(1, 2, 3, 0, 0, 0, 1, 2, 2, 2);
        Matrix4d m = a.toMatrix4d(new Matrix4d());
        assertEquals(2.0, m.m00());
        assertEquals(3.0, m.m32());
        assertEquals(1.0, m.m33());
    }

    @Test
    void getTransformHandlesReflectionsWithAUnitRotation() {
        Quaterniond q = new Quaterniond().rotationXYZ(0.3, -0.7, 1.1);
        Affine4d a = new Affine4d().translationRotateScale(1, 2, 3, q.x, q.y, q.z, q.w, -2.0, 3.0, 0.5);
        Transformd t = a.getTransform(new Transformd());
        assertEquals(1.0, t.rotation.lengthSquared(), 1e-12, "unit rotation");
        assertTrue(t.scale.x < 0.0, "the reflection goes into the x scale");
        Affine4d back = new Affine4d().translationRotateScale(t.translation, t.rotation, t.scale);
        assertAffineEquals(a, back, 1e-12);

        // a mirror of y gives scale (-1, 1, 1) and a half turn about z: the matrix is still reproduced
        Affine4d mirror = new Affine4d();
        mirror.m11 = -1.0;
        Transformd tm = mirror.getTransform(new Transformd());
        assertAffineEquals(mirror, new Affine4d().translationRotateScale(tm.translation, tm.rotation, tm.scale), 1e-15);

        Quaternionf qf = new Quaternionf().rotationXYZ(0.3f, -0.7f, 1.1f);
        Affine4f af = new Affine4f().translationRotateScale(1, 2, 3, qf.x, qf.y, qf.z, qf.w, -2.0f, 3.0f, 0.5f);
        Transformf tf = af.getTransform(new Transformf());
        assertEquals(1.0f, tf.rotation.lengthSquared(), 1e-6f);
        Affine4f backf = new Affine4f().translationRotateScale(tf.translation, tf.rotation, tf.scale);
        assertEquals(af.m00, backf.m00, 1e-5f);
        assertEquals(af.m21, backf.m21, 1e-5f);
    }

    @Test
    void getTransformOfShearKeepsTheXColumnAndAUnitRotation() {
        Affine4d a = new Affine4d();
        a.m10 = 0.8; // y column leans toward x: shear
        a.m20 = -0.3;
        a.m21 = 0.4;
        Transformd t = a.getTransform(new Transformd());
        assertEquals(1.0, t.rotation.lengthSquared(), 1e-12);
        Vector3d x = t.rotation.transform(new Vector3d(1, 0, 0));
        assertEquals(1.0, x.x, 1e-12, "the x column keeps its direction");
        assertEquals(Math.sqrt(1 + 0.64), t.scale.y, 1e-12, "scale is the column length");
    }

    @Test
    void getTransformOfZeroColumnsIsDefined() {
        Affine4d zero = new Affine4d();
        zero.m00 = zero.m11 = zero.m22 = 0.0;
        Transformd t = zero.getTransform(new Transformd());
        assertEquals(new Quaterniond(), t.rotation);
        assertEquals(new Vector3d(), t.scale);

        // x column zero, y and z orthogonal: still reproduced
        Quaterniond q = new Quaterniond().rotationXYZ(-0.4, 0.9, 0.2);
        Affine4d a = new Affine4d().translationRotateScale(0, 0, 0, q.x, q.y, q.z, q.w, 0.0, 2.0, 3.0);
        Transformd ta = a.getTransform(new Transformd());
        assertEquals(1.0, ta.rotation.lengthSquared(), 1e-12);
        assertAffineEquals(a, new Affine4d().translationRotateScale(ta.translation, ta.rotation, ta.scale), 1e-12);

        // only the z column left
        Affine4d onlyZ = new Affine4d().translationRotateScale(0, 0, 0, q.x, q.y, q.z, q.w, 0.0, 0.0, 3.0);
        Transformd tz = onlyZ.getTransform(new Transformd());
        assertEquals(1.0, tz.rotation.lengthSquared(), 1e-12);
        assertAffineEquals(onlyZ, new Affine4d().translationRotateScale(tz.translation, tz.rotation, tz.scale), 1e-12);
    }

    private static void assertAffineEquals(Affine4d e, Affine4d a, double tol) {
        double[] x = {e.m00, e.m01, e.m02, e.m10, e.m11, e.m12, e.m20, e.m21, e.m22, e.m30, e.m31, e.m32};
        double[] y = {a.m00, a.m01, a.m02, a.m10, a.m11, a.m12, a.m20, a.m21, a.m22, a.m30, a.m31, a.m32};
        assertArrayEquals(x, y, tol);
    }

    @Test
    void invertRigidFastRejectsScale() {
        Transformf scaled = new Transformf(new Vector3f(1, 2, 3), new Quaternionf(), new Vector3f(2, 2, 2));
        Transformf dest = new Transformf();
        dest.translation.set(7, 7, 7);
        assertThrows(IllegalArgumentException.class, () -> scaled.invertRigidFast(dest));
        assertEquals(new Vector3f(7, 7, 7), dest.translation, "dest unchanged");
        Transformd scaledD = new Transformd(new Vector3d(1, 2, 3), new Quaterniond(), new Vector3d(1, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> scaledD.invertRigidFast(new Transformd()));

        // in place, unit scale within rounding
        Transformf rigid = new Transformf(new Vector3f(1, 2, 3), new Quaternionf().rotationY(0.5f),
                new Vector3f(1.000001f, 1, 1));
        rigid.invertRigidFast(rigid);
        assertEquals(new Vector3f(1, 1, 1), rigid.scale);
    }

    @Test
    void dualQuatBlendRejectsEmptyAndZeroWeightBlends() {
        DualQuatTransformf a = new DualQuatTransformf().set(new Quaternionf().rotationZ(0.4f), new Vector3f(1, 0, 0));
        DualQuatTransformf dest = new DualQuatTransformf().set(new Quaternionf().rotationX(1f), new Vector3f(0, 5, 0));
        DualQuatTransformf before = new DualQuatTransformf().set(dest);
        DualQuatTransformf[] one = {a};
        assertThrows(IllegalArgumentException.class, () -> DualQuatTransformf.blend(one, new float[] {1}, 0, dest));
        assertThrows(IllegalArgumentException.class,
            () -> DualQuatTransformf.blend(new DualQuatTransformf[0], new float[0], 0, dest));
        assertThrows(IllegalArgumentException.class, () -> DualQuatTransformf.blend(one, new float[] {0}, 1, dest));
        assertThrows(IllegalArgumentException.class, () -> DualQuatTransformf.blend(one, new float[] {1}, 2, dest));
        Vector3f scale = new Vector3f(9, 9, 9);
        assertThrows(IllegalArgumentException.class, () -> DualQuatTransformf.blendScaled(new DualQuatTransformf[] {a, a},
            new float[] {1, 1, 1, 2, 2, 2}, new float[] {1, -1}, 2, dest, scale));
        assertEquals(new Vector3f(9, 9, 9), scale, "scaleDest unchanged");
        assertEquals(before.real, dest.real, "dest unchanged");
        assertEquals(before.dual, dest.dual, "dest unchanged");

        DualQuatTransformd ad = new DualQuatTransformd().set(new Quaterniond().rotationZ(0.4), new Vector3d(1, 0, 0));
        Vector3d sd = new Vector3d();
        assertThrows(IllegalArgumentException.class, () -> DualQuatTransformd.blendScaled(new DualQuatTransformd[] {ad},
            new double[] {1, 1, 1}, new double[] {0}, 1, new DualQuatTransformd(), sd));
        assertThrows(IllegalArgumentException.class,
            () -> DualQuatTransformd.blend(new DualQuatTransformd[] {ad}, new double[] {1}, 0, new DualQuatTransformd()));
        // a valid blend still works, and the weights need not sum to 1
        DualQuatTransformd r = DualQuatTransformd.blendScaled(new DualQuatTransformd[] {ad, ad},
            new double[] {1, 1, 1, 3, 3, 3}, new double[] {1, 1}, 2, new DualQuatTransformd(), sd);
        assertEquals(new Vector3d(2, 2, 2), sd);
        assertEquals(1.0, r.real.lengthSquared(), 1e-12);
    }

    @Test
    void packedAffineArrayStartsAsIdentityAndChecksIndices() {
        PackedAffineArray a = new PackedAffineArray(2);
        assertArrayEquals(new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0},
            a.raw());
        assertThrows(IllegalArgumentException.class, () -> new PackedAffineArray(-1));
        assertThrows(IllegalArgumentException.class, () -> new PackedAffineArray(Integer.MAX_VALUE / 6));
        assertThrows(IndexOutOfBoundsException.class, () -> a.set(2, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12));
        // 12 * index overflows to 8, a valid offset, without the check
        assertThrows(IndexOutOfBoundsException.class,
            () -> a.set(0x15555556, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12));
        assertEquals(1.0f, a.raw()[0]);
    }

    @Test
    void packedAffineKernelsCheckBeforeWriting() {
        PackedAffineArray src = new PackedAffineArray(3);
        src.set(2, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        float[] out = new float[30];
        // a bad index in the middle of order: nothing written
        assertThrows(IllegalArgumentException.class,
            () -> PackedAffineKernels.uploadPackedAffine(src, new int[] {0, 3, 1}, out, 3));
        assertThrows(IllegalArgumentException.class,
            () -> PackedAffineKernels.uploadPackedAffine(src, new int[] {0, -1}, out, 2));
        assertThrows(IllegalArgumentException.class,
            () -> PackedAffineKernels.uploadPackedAffine(src, new int[] {0, 1, 2}, out, 7, 3));
        assertThrows(IllegalArgumentException.class,
            () -> PackedAffineKernels.uploadPackedAffineChunked(src, new int[] {0, 1, 2}, out, -1, 2, 1));
        assertThrows(IllegalArgumentException.class,
            () -> PackedAffineKernels.uploadPackedAffine(src, new int[] {0}, out, 2));
        assertArrayEquals(new float[30], out, "nothing written");

        // the output offset
        PackedAffineKernels.uploadPackedAffine(src, new int[] {2, 0}, out, 5, 2);
        assertEquals(1.0f, out[5]);
        assertEquals(12.0f, out[16]);
        assertEquals(1.0f, out[17], "identity r00 of transform 0");
        assertEquals(0.0f, out[4]);
        float[] chunked = new float[30];
        PackedAffineKernels.uploadPackedAffineChunked(src, new int[] {2, 0}, chunked, 5, 2, 1);
        assertArrayEquals(out, chunked);

        float[] box = {0, 0};
        float[] outBox = {7, 7};
        assertThrows(IllegalArgumentException.class, () -> PackedAffineKernels.transformAabbPackedAffineBatch(src,
            new int[] {0, 2}, box, box, box, box, box, box, outBox, outBox, outBox, outBox, outBox, outBox, 2));
        assertThrows(IllegalArgumentException.class, () -> PackedAffineKernels.transformAabbPackedAffineChunked(src,
            new int[] {1, 5}, box, box, box, box, box, box, outBox, outBox, outBox, outBox, outBox, outBox, 2, 1));
        assertArrayEquals(new float[] {7, 7}, outBox, "nothing written");

        Quaternionf[] rotations = {new Quaternionf(), null};
        PackedAffineArray dst = new PackedAffineArray(2);
        float[] t = {5, 5};
        assertThrows(IllegalArgumentException.class,
            () -> PackedAffineKernels.quatTranslationToPackedAffineBatch(rotations, t, t, t, dst, 2));
        assertThrows(IllegalArgumentException.class,
            () -> PackedAffineKernels.trsToPackedAffineBatch(new Transformf[] {new Transformf()}, dst, 3));
        assertThrows(IllegalArgumentException.class,
            () -> PackedAffineKernels.matrix4fToPackedAffineBatch(new Matrix4f[3], dst, 1));
        assertEquals(0.0f, dst.raw()[3], "nothing written");
    }

    // ---- world

    @Test
    void worldPosNormalizeIsAtomic() {
        WorldPos p = new WorldPos(10);
        p.set(5000.0, 0.0, 0.0);
        Vector3L cell = new Vector3L(p.cell);
        Vector3d offset = new Vector3d(p.offset);
        p.offset.x = 3000.0; // valid on x, invalid on z
        p.offset.z = Double.NaN;
        assertThrows(ArithmeticException.class, p::normalize);
        assertEquals(cell, p.cell, "cell unchanged");
        assertEquals(3000.0, p.offset.x, "offset unchanged");
        assertTrue(Double.isNaN(p.offset.z));

        WorldPos q = new WorldPos(10);
        q.set(5000.0, 0.0, 0.0);
        assertThrows(ArithmeticException.class, () -> q.add(1000.0, 0.0, Double.POSITIVE_INFINITY));
        assertEquals(cell, q.cell);
        assertEquals(offset, q.offset);
        assertThrows(ArithmeticException.class, () -> q.set(1.0, 2.0, Double.NaN));
        assertEquals(cell, q.cell);
        assertEquals(offset, q.offset);

        WorldPos edge = new WorldPos(0);
        edge.cell.set(Long.MAX_VALUE, 0, 0);
        edge.offset.set(0.25, 5.0, 0.0);
        edge.normalize(); // y moves 5 cells, x none
        assertEquals(new Vector3L(Long.MAX_VALUE, 5, 0), edge.cell);
        edge.offset.set(2.0, -7.0, 0.0);
        assertThrows(ArithmeticException.class, edge::normalize);
        assertEquals(new Vector3L(Long.MAX_VALUE, 5, 0), edge.cell, "y not moved before x overflowed");
        assertEquals(-7.0, edge.offset.y);
    }

    // ---- camera

    @Test
    void haltonJitterRejectsFramesWithoutACycleUpFront() {
        Vector2f dest = new Vector2f(3, 4);
        assertThrows(IllegalArgumentException.class,
            () -> Projectionsf.haltonJitter(Integer.MAX_VALUE, 0, 100, 100, dest));
        assertThrows(IllegalArgumentException.class, () -> Projectionsf.haltonJitter(-1, -5, 100, 100, dest));
        assertEquals(new Vector2f(3, 4), dest);
        assertThrows(IllegalArgumentException.class,
            () -> Projectionsd.haltonJitter(Integer.MAX_VALUE, 0, 100, 100, new Vector2d()));
        // with a cycle any frame is fine, negative ones count back
        assertEquals(Projectionsd.haltonJitter(7, 8, 100, 100, new Vector2d()),
            Projectionsd.haltonJitter(-1, 8, 100, 100, new Vector2d()));
        Projectionsd.haltonJitter(Integer.MAX_VALUE - 1, 0, 100, 100, new Vector2d());
    }

    @Test
    void ndcDepthOfAPointIsExactForObliqueProjections() {
        Matrix4d proj = Projectionsd.perspective(1.2, 1.0, 0.1, 100.0, DepthRange.ZERO_TO_ONE, new Matrix4d());
        // symmetric: the point form agrees with the distance form anywhere at that distance
        assertEquals(Projectionsd.ndcDepth(proj, 10.0), Projectionsd.ndcDepth(proj, 3.0, -2.0, -10.0), 1e-15);
        Matrix4d oblique = Projectionsd.obliqueNearPlane(proj, new Vector4d(0.3, 0.2, -1.0, -2.0),
            DepthRange.ZERO_TO_ONE, new Matrix4d());
        Vector4d clip = oblique.transform(new Vector4d(3.0, -2.0, -10.0, 1.0));
        double exact = clip.z / clip.w;
        assertEquals(exact, Projectionsd.ndcDepth(oblique, 3.0, -2.0, -10.0), 1e-14);
        assertTrue(Math.abs(exact - Projectionsd.ndcDepth(oblique, 10.0)) > 1e-3, "the distance form is not exact");
        Matrix4f obliqueF = new Matrix4f().set(oblique);
        assertEquals(exact, Projectionsf.ndcDepth(obliqueF, 3.0f, -2.0f, -10.0f), 1e-5);
    }

    // ---- animation

    @Test
    void poseKernelsCheckTheHierarchyBeforeWriting() {
        int[] badLast = {-1, 0, 2};
        float[] t = new float[9], r = new float[12], s = new float[9];
        float[] mt = new float[9], mr = new float[12], ms = new float[9];
        for (int i = 0; i < 3; i++) {
            r[4 * i + 3] = 1;
            t[3 * i] = i + 1;
        }
        assertThrows(IllegalArgumentException.class, () -> PoseKernelsf.localToModel(badLast, 3, t, r, s, mt, mr, ms));
        assertArrayEquals(new float[9], mt, "nothing written before the bad parent");
        assertThrows(IllegalArgumentException.class, () -> PoseKernelsf.modelToLocal(badLast, 3, t, r, s, mt, mr, ms));
        assertArrayEquals(new float[9], mt);
        int[] ok = {-1, 0, 1};
        assertThrows(IllegalArgumentException.class, () -> PoseKernelsf.localToModel(ok, 3, t, r, null, mt, mr, ms));
        assertThrows(IllegalArgumentException.class, () -> PoseKernelsf.modelToLocal(ok, 3, t, r, s, mt, mr, null));
        assertThrows(IllegalArgumentException.class,
            () -> PoseKernelsf.localToModel(ok, 3, t, r, s, mt, new float[8], ms));
        assertThrows(IllegalArgumentException.class, () -> PoseKernelsf.localToModel(ok, -1, t, r, s, mt, mr, ms));
        assertArrayEquals(new float[9], mt);
        double[] td = new double[9], rd = new double[12], md = new double[9], mrd = new double[12];
        assertThrows(IllegalArgumentException.class,
            () -> PoseKernelsd.localToModel(new int[] {-1, 1, 0}, 3, td, rd, null, md, mrd, null));
        assertThrows(IllegalArgumentException.class,
            () -> PoseKernelsd.modelToLocal(ok, 3, td, rd, new double[9], md, mrd, null));
        PoseKernelsf.localToModel(ok, 3, t, r, null, mt, mr, null);
        assertEquals(6.0f, mt[6]);
    }

    // ---- ik

    @Test
    void zeroSwingLimitGivesNoNan() {
        Vector3f axis = new Vector3f(0, 1, 0), swingA = new Vector3f(1, 0, 0);
        // a swing toward x only: with swingAxisA = z (limit 0), it has no component along A
        Quaternionf swingB = new Quaternionf().rotationAxis(0.3f, 1, 0, 0); // rotates y toward +z: vector part on x
        Quaternionf dest = new Quaternionf();
        boolean clamped = JointLimitsf.clampSwingTwist(swingB, axis, new Vector3f(0, 0, 1), 0.0f, 1.0f, -1f, 1f, dest);
        assertFalse(Float.isNaN(dest.x) || Float.isNaN(dest.w));
        assertFalse(clamped, "swing only along the B direction, within its limit");
        assertEquals(swingB.x, dest.x, 1e-6f);
        // swing along the zero-limit direction is removed
        clamped = JointLimitsf.clampSwingTwist(swingB, axis, swingA, 0.0f, 1.0f, -1f, 1f, dest);
        assertTrue(clamped);
        assertEquals(1.0f, Math.abs(dest.w), 1e-6f);

        Quaterniond qd = new Quaterniond().rotationAxis(0.3, 1, 0, 0);
        Quaterniond dd = new Quaterniond();
        JointLimitsd.clampSwingTwist(qd, new Vector3d(0, 1, 0), new Vector3d(0, 0, 1), 0.0, 0.5, -1, 1, dd);
        assertEquals(qd.x, dd.x, 1e-12);
        assertTrue(JointLimitsd.clampCone(qd, new Vector3d(0, 1, 0), new Vector3d(0, 0, 1), 0.0, dd));
        assertEquals(1.0, Math.abs(dd.w), 1e-12);
    }

    @Test
    void dampedLeastSquaresWithoutDampingHandlesAStraightChain() {
        // a straight chain along a generic direction (float rounding makes it nearly, not exactly, straight)
        float[] p = new float[12];
        for (int i = 0; i < 4; i++) {
            p[3 * i] = 0.48f * i;
            p[3 * i + 1] = 0.6f * i;
            p[3 * i + 2] = -0.64f * i;
        }
        float[] rot = new float[9];
        float err = DampedLeastSquaresf.step(p, 4, 0.3f, 2.5f, -1.0f, 0.0f, 0.0f, rot);
        assertTrue(err > 0);
        for (float v : rot) {
            assertTrue(Float.isFinite(v) && Math.abs(v) < 2.0f, "finite, modest step: " + v);
        }
        // target on the chain's own line beyond reach: no useful rotation
        double[] pd = {0, 0, 0, 1, 0, 0, 2, 0, 0};
        double[] rd = new double[6];
        DampedLeastSquaresd.step(pd, 3, 5, 0, 0, 0.0, 0.0, rd);
        assertArrayEquals(new double[6], rd, 1e-9);
        // every joint at the end effector: zero rotations
        double[] same = {1, 1, 1, 1, 1, 1};
        double[] rs = {9, 9, 9};
        assertEquals(1.0, DampedLeastSquaresd.step(same, 2, 1, 1, 2, 0.0, 0.0, rs), 0.0);
        assertArrayEquals(new double[3], rs);
        // the solver still converges undamped on a bent chain
        double[] bent = {0, 0, 0, 1, 0, 0, 1, 1, 0};
        double e = DampedLeastSquaresd.solve(bent, 3, 1.5, 0.8, 0.3, 0.0, 0.0, 1e-9, 50, new double[6]);
        assertTrue(e < 1e-9, "error " + e);
    }

    // ---- physics

    @Test
    void clampTorqueRejectsNegativeMaxTorque() {
        Vector3f dest = new Vector3f(1, 2, 3);
        assertThrows(IllegalArgumentException.class, () -> PdControllersf.clampTorque(new Vector3f(5, 0, 0), -1f, dest));
        assertThrows(IllegalArgumentException.class,
            () -> PdControllersf.clampTorque(new Vector3f(5, 0, 0), Float.NaN, dest));
        assertEquals(new Vector3f(1, 2, 3), dest);
        assertThrows(IllegalArgumentException.class,
            () -> PdControllersd.clampTorque(new Vector3d(5, 0, 0), -1, new Vector3d()));
        assertEquals(new Vector3d(), PdControllersd.clampTorque(new Vector3d(5, 0, 0), 0, new Vector3d()));
    }

    @Test
    void pbdConstraintsAreScaleInvariant() {
        for (double scale : new double[] {1e-4, 1.0, 1e4}) {
            Vector3d p0 = new Vector3d(1, 0, 0).mul(scale), p1 = new Vector3d(), p2 = new Vector3d(0.2, 1, 0).mul(scale);
            Vector3d d0 = new Vector3d(), d1 = new Vector3d(), d2 = new Vector3d();
            PbdConstraintsd.bendConstraint(p0, p1, p2, 1.0, 1.0, d0, d1, d2);
            Vector3d u0 = new Vector3d(), u1 = new Vector3d(), u2 = new Vector3d();
            PbdConstraintsd.bendConstraint(new Vector3d(1, 0, 0), new Vector3d(), new Vector3d(0.2, 1, 0), 1.0, 1.0,
                u0, u1, u2);
            assertTrue(d0.length() > 0, "bend corrects at scale " + scale);
            assertEquals(u0.length() * scale, d0.length(), 1e-9 * scale, "bend scales with size " + scale);

            Vector3d q0 = new Vector3d(), q1 = new Vector3d(1, 0, 0).mul(scale);
            Vector3d q2 = new Vector3d(0, 1, 0).mul(scale), q3 = new Vector3d(0, 0, 1).mul(scale);
            Vector3d e0 = new Vector3d(), e1 = new Vector3d(), e2 = new Vector3d(), e3 = new Vector3d();
            double rest = 2.0 / 6.0 * scale * scale * scale;
            PbdConstraintsd.volumeConstraint(q0, q1, q2, q3, rest, 1.0, e0, e1, e2, e3);
            assertTrue(e1.length() > 0, "volume corrects at scale " + scale);

            Vector3f f0 = new Vector3f(1, 0, 0).mul((float) scale), f2 = new Vector3f(0.2f, 1, 0).mul((float) scale);
            Vector3f g0 = new Vector3f(), g1 = new Vector3f(), g2 = new Vector3f();
            PbdConstraintsf.bendConstraint(f0, new Vector3f(), f2, 1.0f, 1.0f, g0, g1, g2);
            assertTrue(g0.length() > 0, "float bend corrects at scale " + scale);
        }
        // a tiny distance constraint still corrects; coinciding particles do not
        Vector3d dest = PbdConstraintsd.distanceConstraint(new Vector3d(1e-8, 0, 0), 1, new Vector3d(), 1, 2e-8, 1,
            new Vector3d());
        assertEquals(0.5e-8, dest.x, 1e-20);
        assertEquals(new Vector3d(), PbdConstraintsd.distanceConstraint(new Vector3d(), 1, new Vector3d(), 1, 1, 1,
            new Vector3d()));
        // collinear tetrahedron: no correction
        Vector3d z0 = new Vector3d(), z1 = new Vector3d(), z2 = new Vector3d(), z3 = new Vector3d();
        PbdConstraintsd.volumeConstraint(new Vector3d(), new Vector3d(1e5, 0, 0), new Vector3d(2e5, 0, 0),
            new Vector3d(3e5, 0, 0), 1.0, 1.0, z0, z1, z2, z3);
        assertEquals(new Vector3d(), z1);
    }

    // ---- easing

    @Test
    void springEndsExactlyAtOne() {
        float[][] params = {{8, 20}, {1, 10}, {3, 0}, {0.5f, 7}, {20, 50}};
        for (float[] k : params) {
            assertEquals(1.0f, Easingf.spring(1.0f, k[0], k[1]), 0.0f);
            assertEquals(0.0f, Easingf.spring(0.0f, k[0], k[1]), 0.0f);
            assertEquals(1.0, Easingd.spring(1.0, k[0], k[1]), 0.0);
            assertEquals(0.0, Easingd.spring(0.0, k[0], k[1]), 0.0);
        }
        // with damping 8 the curve differs from the plain spring by less than e^-8
        for (int i = 0; i <= 10; i++) {
            double t = i / 10.0;
            double plain = 1.0 - Math.exp(-8 * t) * Math.cos(20 * t);
            assertEquals(plain, Easingd.spring(t, 8, 20), Math.exp(-8) + 1e-15);
        }
    }
}
