/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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
package org.vectrix.affine;

import org.vectrix.core.Matrix4f;
import org.vectrix.core.Quaternionf;
import org.vectrix.core.Vector3fc;

/**
 * Batch kernels for packed affine transforms.
 */
public final class PackedAffineKernels {
    private PackedAffineKernels() {
    }

    public static void quatTranslationToPackedAffineBatch(
            Quaternionf[] rotation,
            float[] tx,
            float[] ty,
            float[] tz,
            PackedAffineArray out,
            int count) {
        for (int i = 0; i < count; i++) {
            Quaternionf q = rotation[i];
            setTrs(out.raw(), out.offsetOf(i), tx[i], ty[i], tz[i], q.x, q.y, q.z, q.w, 1.0f, 1.0f, 1.0f);
        }
    }

    public static void trsToPackedAffineBatch(Transformf[] transforms, PackedAffineArray out, int count) {
        float[] dst = out.raw();
        for (int i = 0; i < count; i++) {
            Transformf t = transforms[i];
            setTrs(dst, out.offsetOf(i),
                    t.translation.x, t.translation.y, t.translation.z,
                    t.rotation.x, t.rotation.y, t.rotation.z, t.rotation.w,
                    t.scale.x, t.scale.y, t.scale.z);
        }
    }

    public static void matrix4fToPackedAffineBatch(Matrix4f[] matrices, PackedAffineArray out, int count) {
        float[] dst = out.raw();
        for (int i = 0; i < count; i++) {
            Matrix4f m = matrices[i];
            int o = out.offsetOf(i);
            dst[o] = m.m00();
            dst[o + 1] = m.m01();
            dst[o + 2] = m.m02();
            dst[o + 3] = m.m30();
            dst[o + 4] = m.m10();
            dst[o + 5] = m.m11();
            dst[o + 6] = m.m12();
            dst[o + 7] = m.m31();
            dst[o + 8] = m.m20();
            dst[o + 9] = m.m21();
            dst[o + 10] = m.m22();
            dst[o + 11] = m.m32();
        }
    }

    public static void transformAabbPackedAffineBatch(
            PackedAffineArray transforms,
            int[] order,
            float[] minX,
            float[] minY,
            float[] minZ,
            float[] maxX,
            float[] maxY,
            float[] maxZ,
            float[] outMinX,
            float[] outMinY,
            float[] outMinZ,
            float[] outMaxX,
            float[] outMaxY,
            float[] outMaxZ,
            int count) {
        transformAabbPackedAffineRange(transforms, order, minX, minY, minZ, maxX, maxY, maxZ,
                outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ, 0, count);
    }

    public static void transformAabbPackedAffineChunked(
            PackedAffineArray transforms,
            int[] order,
            float[] minX,
            float[] minY,
            float[] minZ,
            float[] maxX,
            float[] maxY,
            float[] maxZ,
            float[] outMinX,
            float[] outMinY,
            float[] outMinZ,
            float[] outMaxX,
            float[] outMaxY,
            float[] outMaxZ,
            int count,
            int chunkSize) {
        BatchChunks.forEachChunk(count, chunkSize,
                (start, end) -> transformAabbPackedAffineRange(transforms, order, minX, minY, minZ, maxX, maxY, maxZ,
                        outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ, start, end));
    }

    public static void uploadPackedAffine(
            PackedAffineArray transforms,
            int[] order,
            float[] outUpload,
            int count) {
        uploadPackedAffineRange(transforms, order, outUpload, 0, count);
    }

    public static void uploadPackedAffineChunked(
            PackedAffineArray transforms,
            int[] order,
            float[] outUpload,
            int count,
            int chunkSize) {
        BatchChunks.forEachChunk(count, chunkSize,
                (start, end) -> uploadPackedAffineRange(transforms, order, outUpload, start, end));
    }

    private static void transformAabbPackedAffineRange(
            PackedAffineArray transforms,
            int[] order,
            float[] minX,
            float[] minY,
            float[] minZ,
            float[] maxX,
            float[] maxY,
            float[] maxZ,
            float[] outMinX,
            float[] outMinY,
            float[] outMinZ,
            float[] outMaxX,
            float[] outMaxY,
            float[] outMaxZ,
            int start,
            int end) {
        float[] t = transforms.raw();
        for (int i = start; i < end; i++) {
            int idx = order[i];
            int o = transforms.offsetOf(idx);
            float m00 = t[o];
            float m01 = t[o + 1];
            float m02 = t[o + 2];
            float tx = t[o + 3];
            float m10 = t[o + 4];
            float m11 = t[o + 5];
            float m12 = t[o + 6];
            float ty = t[o + 7];
            float m20 = t[o + 8];
            float m21 = t[o + 9];
            float m22 = t[o + 10];
            float tz = t[o + 11];

            float cx = (minX[idx] + maxX[idx]) * 0.5f;
            float cy = (minY[idx] + maxY[idx]) * 0.5f;
            float cz = (minZ[idx] + maxZ[idx]) * 0.5f;
            float ex = (maxX[idx] - minX[idx]) * 0.5f;
            float ey = (maxY[idx] - minY[idx]) * 0.5f;
            float ez = (maxZ[idx] - minZ[idx]) * 0.5f;

            float ncx = m00 * cx + m10 * cy + m20 * cz + tx;
            float ncy = m01 * cx + m11 * cy + m21 * cz + ty;
            float ncz = m02 * cx + m12 * cy + m22 * cz + tz;

            float nex = java.lang.Math.abs(m00) * ex + java.lang.Math.abs(m10) * ey + java.lang.Math.abs(m20) * ez;
            float ney = java.lang.Math.abs(m01) * ex + java.lang.Math.abs(m11) * ey + java.lang.Math.abs(m21) * ez;
            float nez = java.lang.Math.abs(m02) * ex + java.lang.Math.abs(m12) * ey + java.lang.Math.abs(m22) * ez;

            outMinX[idx] = ncx - nex;
            outMinY[idx] = ncy - ney;
            outMinZ[idx] = ncz - nez;
            outMaxX[idx] = ncx + nex;
            outMaxY[idx] = ncy + ney;
            outMaxZ[idx] = ncz + nez;
        }
    }

    private static void uploadPackedAffineRange(
            PackedAffineArray transforms,
            int[] order,
            float[] outUpload,
            int start,
            int end) {
        float[] src = transforms.raw();
        for (int i = start; i < end; i++) {
            int idx = order[i];
            int srcBase = transforms.offsetOf(idx);
            int dstBase = i * PackedAffineArray.STRIDE_FLOATS;
            outUpload[dstBase] = src[srcBase];
            outUpload[dstBase + 1] = src[srcBase + 1];
            outUpload[dstBase + 2] = src[srcBase + 2];
            outUpload[dstBase + 3] = src[srcBase + 3];
            outUpload[dstBase + 4] = src[srcBase + 4];
            outUpload[dstBase + 5] = src[srcBase + 5];
            outUpload[dstBase + 6] = src[srcBase + 6];
            outUpload[dstBase + 7] = src[srcBase + 7];
            outUpload[dstBase + 8] = src[srcBase + 8];
            outUpload[dstBase + 9] = src[srcBase + 9];
            outUpload[dstBase + 10] = src[srcBase + 10];
            outUpload[dstBase + 11] = src[srcBase + 11];
        }
    }

    private static void setTrs(
            float[] dst,
            int offset,
            float tx,
            float ty,
            float tz,
            float qx,
            float qy,
            float qz,
            float qw,
            float sx,
            float sy,
            float sz) {
        float dqx = qx + qx;
        float dqy = qy + qy;
        float dqz = qz + qz;
        float q00 = dqx * qx;
        float q11 = dqy * qy;
        float q22 = dqz * qz;
        float q01 = dqx * qy;
        float q02 = dqx * qz;
        float q03 = dqx * qw;
        float q12 = dqy * qz;
        float q13 = dqy * qw;
        float q23 = dqz * qw;

        dst[offset] = sx - (q11 + q22) * sx;
        dst[offset + 1] = (q01 + q23) * sx;
        dst[offset + 2] = (q02 - q13) * sx;
        dst[offset + 3] = tx;

        dst[offset + 4] = (q01 - q23) * sy;
        dst[offset + 5] = sy - (q22 + q00) * sy;
        dst[offset + 6] = (q12 + q03) * sy;
        dst[offset + 7] = ty;

        dst[offset + 8] = (q02 + q13) * sz;
        dst[offset + 9] = (q12 - q03) * sz;
        dst[offset + 10] = sz - (q11 + q00) * sz;
        dst[offset + 11] = tz;
    }
}
