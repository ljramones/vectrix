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

import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.affine.PackedAffineKernels;
import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.soa.SkinningKernels;
import org.dynamisengine.vectrix.soa.TransformSoA;
import org.junit.jupiter.api.Test;

/**
 * Review finding M-1: the 12-float affine layout must be one convention, {@code AFFINE_3X4_ROW_MAJOR}
 * ({@code [r00 r01 r02 tx, r10 r11 r12 ty, r20 r21 r22 tz]}), shared by {@link PackedAffineArray} and the
 * skinning palette. Before the fix the packed array stored basis columns, so feeding it to
 * {@code skinLbs4MatrixPalette} or a {@code VkTransformMatrixKHR} applied the inverse rotation.
 */
class PackedAffineConventionTest {

    private static Transformf sampleTrs() {
        return new Transformf(new Vector3f(3.0f, -1.5f, 7.25f),
                new Quaternionf().rotationXYZ(0.7f, -0.4f, 1.1f), new Vector3f(2.0f, 0.5f, 1.5f));
    }

    private static Matrix4f joml(Transformf t) {
        return new Matrix4f().translationRotateScale(t.translation, t.rotation, t.scale);
    }

    /** The canonical layout from a JOML matrix: rRC is row R, column C, which JOML calls mCR. */
    private static float[] rowMajor(Matrix4f m) {
        return new float[] {
            m.m00(), m.m10(), m.m20(), m.m30(),
            m.m01(), m.m11(), m.m21(), m.m31(),
            m.m02(), m.m12(), m.m22(), m.m32(),
        };
    }

    @Test
    void trsPackingIsRowMajor() {
        Transformf t = sampleTrs();
        PackedAffineArray packed = new PackedAffineArray(1);
        PackedAffineKernels.trsToPackedAffineBatch(new Transformf[] {t}, packed, 1);
        assertArrayEquals(rowMajor(joml(t)), packed.raw(), 1e-5f);

        PackedAffineArray rigid = new PackedAffineArray(1);
        PackedAffineKernels.quatTranslationToPackedAffineBatch(new Quaternionf[] {t.rotation},
                new float[] {t.translation.x}, new float[] {t.translation.y}, new float[] {t.translation.z}, rigid, 1);
        assertArrayEquals(rowMajor(new Matrix4f().translationRotate(t.translation, t.rotation)), rigid.raw(), 1e-6f);
    }

    @Test
    void matrixPackingMatchesTrsPacking() {
        Transformf t = sampleTrs();
        PackedAffineArray fromTrs = new PackedAffineArray(1);
        PackedAffineArray fromMatrix = new PackedAffineArray(1);
        PackedAffineKernels.trsToPackedAffineBatch(new Transformf[] {t}, fromTrs, 1);
        PackedAffineKernels.matrix4fToPackedAffineBatch(new Matrix4f[] {joml(t)}, fromMatrix, 1);
        assertArrayEquals(fromTrs.raw(), fromMatrix.raw(), 1e-5f);
    }

    @Test
    void packedArrayIsAValidSkinningPalette() {
        // The review's failure case: a 90 degree Z rotation took (1,0,0) to (0,-1,0) instead of (0,1,0).
        Transformf t = new Transformf(new Vector3f(10, 20, 30),
                new Quaternionf().rotationZ((float) (java.lang.Math.PI / 2)), new Vector3f(1, 1, 1));
        PackedAffineArray packed = new PackedAffineArray(1);
        PackedAffineKernels.trsToPackedAffineBatch(new Transformf[] {t}, packed, 1);
        float[] outX = new float[1], outY = new float[1], outZ = new float[1];
        SkinningKernels.skinLbs4MatrixPalette(packed.raw(), new int[] {0, 0, 0, 0}, new float[] {1, 0, 0, 0},
                new float[] {1}, new float[] {0}, new float[] {0}, outX, outY, outZ, 1);
        assertEquals(10.0f, outX[0], 1e-5f);
        assertEquals(21.0f, outY[0], 1e-5f);
        assertEquals(30.0f, outZ[0], 1e-5f);

        // A general TRS agrees with JOML's transformPosition.
        Transformf g = sampleTrs();
        PackedAffineKernels.trsToPackedAffineBatch(new Transformf[] {g}, packed, 1);
        SkinningKernels.skinLbs4MatrixPalette(packed.raw(), new int[] {0, 0, 0, 0}, new float[] {1, 0, 0, 0},
                new float[] {0.3f}, new float[] {-2.0f}, new float[] {1.25f}, outX, outY, outZ, 1);
        Vector3f expected = joml(g).transformPosition(new Vector3f(0.3f, -2.0f, 1.25f));
        assertEquals(expected.x, outX[0], 1e-5f);
        assertEquals(expected.y, outY[0], 1e-5f);
        assertEquals(expected.z, outZ[0], 1e-5f);
    }

    @Test
    void rigidPaletteBuilderUsesTheSameLayout() {
        Transformf t = new Transformf(new Vector3f(-4, 0.5f, 2),
                new Quaternionf().rotationXYZ(-0.3f, 0.9f, 0.2f), new Vector3f(1, 1, 1));
        TransformSoA joints = new TransformSoA(1);
        joints.tx[0] = t.translation.x;
        joints.ty[0] = t.translation.y;
        joints.tz[0] = t.translation.z;
        joints.qx[0] = t.rotation.x;
        joints.qy[0] = t.rotation.y;
        joints.qz[0] = t.rotation.z;
        joints.qw[0] = t.rotation.w;
        float[] palette = new float[12];
        SkinningKernels.buildRigidMatrixPalette12(joints, palette, 1);
        PackedAffineArray packed = new PackedAffineArray(1);
        PackedAffineKernels.trsToPackedAffineBatch(new Transformf[] {t}, packed, 1);
        assertArrayEquals(palette, packed.raw(), 1e-6f);
    }

    @Test
    void aabbTransformMatchesCornerBounds() {
        Transformf t = sampleTrs();
        Matrix4f m = joml(t);
        PackedAffineArray packed = new PackedAffineArray(1);
        PackedAffineKernels.trsToPackedAffineBatch(new Transformf[] {t}, packed, 1);
        float[] mn = {-1.0f, 0.5f, -2.0f}, mx = {2.0f, 1.5f, 0.25f};
        float[][] out = new float[6][1];
        PackedAffineKernels.transformAabbPackedAffineBatch(packed, new int[] {0},
                new float[] {mn[0]}, new float[] {mn[1]}, new float[] {mn[2]},
                new float[] {mx[0]}, new float[] {mx[1]}, new float[] {mx[2]},
                out[0], out[1], out[2], out[3], out[4], out[5], 1);
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int c = 0; c < 8; c++) {
            Vector3f p = m.transformPosition(new Vector3f((c & 1) == 0 ? mn[0] : mx[0],
                    (c & 2) == 0 ? mn[1] : mx[1], (c & 4) == 0 ? mn[2] : mx[2]));
            float[] v = {p.x, p.y, p.z};
            for (int k = 0; k < 3; k++) {
                lo[k] = java.lang.Math.min(lo[k], v[k]);
                hi[k] = java.lang.Math.max(hi[k], v[k]);
            }
        }
        for (int k = 0; k < 3; k++) {
            assertEquals(lo[k], out[k][0], 1e-4f, "min " + k);
            assertEquals(hi[k], out[3 + k][0], 1e-4f, "max " + k);
        }
    }
}
