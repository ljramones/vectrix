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
package org.dynamisengine.vectrix.affine;

import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Quaternionf;

/**
 * Batch kernels for packed affine transforms in the row-major layout of {@link PackedAffineArray}. The
 * TRS conversions produce {@code T * R * S}, the same matrix as JOML's {@code translationRotateScale}.
 * <p>
 * <b>{@code order}.</b> The AABB kernels use {@code order} only to choose which entries to process: for each
 * {@code i} in {@code [0, count)} they process entry {@code k = order[i]}, reading transform {@code k} and input box
 * {@code k} and writing output box {@code k}. Inputs and outputs are indexed by entry, not by position in
 * {@code order}, so the output arrays are scattered, not packed. The upload kernels instead gather: they read
 * transform {@code order[i]} and write it to slot {@code i} of the output. Both use the first {@code count} elements
 * of {@code order}.
 * <p>
 * <b>Checks.</b> Every kernel checks its arguments before writing anything: {@code count} is not negative, every
 * array holds the entries it is asked for, every {@code order} index is in range and the output offset fits. A bad
 * argument throws {@link IllegalArgumentException} and leaves the outputs unchanged. Indices in {@code order} need
 * not be distinct, except where a method says so. The conversion kernels write entries {@code 0} to
 * {@code count - 1} of their output; the upload kernels write from an output offset. The kernels keep no state, do
 * not allocate and are safe to call from several threads on disjoint outputs.
 */
public final class PackedAffineKernels {
    private PackedAffineKernels() {
    }

    /**
     * Writes {@code count} rotation-plus-translation transforms: entry {@code i} of {@code out} becomes
     * {@code T(tx[i], ty[i], tz[i]) * R(rotation[i])}. The quaternions are not normalized; a non-unit quaternion gives
     * a matrix that also scales.
     *
     * @param rotation the rotations, unit quaternions
     * @param tx the translations x
     * @param ty the translations y
     * @param tz the translations z
     * @param out receives the transforms at entries {@code 0} to {@code count - 1}
     * @param count the number of transforms
     * @throws IllegalArgumentException if {@code count} is negative, an array or {@code out} holds fewer than
     *         {@code count} entries, or one of the first {@code count} rotations is null
     */
    public static void quatTranslationToPackedAffineBatch(
            Quaternionf[] rotation,
            float[] tx,
            float[] ty,
            float[] tz,
            PackedAffineArray out,
            int count) {
        checkCount(count);
        checkLength(tx.length, count, "tx");
        checkLength(ty.length, count, "ty");
        checkLength(tz.length, count, "tz");
        checkLength(out.size(), count, "out");
        checkElements(rotation, count, "rotation");
        for (int i = 0; i < count; i++) {
            Quaternionf q = rotation[i];
            setTrs(out.raw(), out.offsetOf(i), tx[i], ty[i], tz[i], q.x, q.y, q.z, q.w, 1.0f, 1.0f, 1.0f);
        }
    }

    /**
     * Writes {@code count} TRS transforms: entry {@code i} of {@code out} becomes {@code T * R * S} of
     * {@code transforms[i]}. The rotations are not normalized.
     *
     * @param transforms the transforms
     * @param out receives the matrices at entries {@code 0} to {@code count - 1}
     * @param count the number of transforms
     * @throws IllegalArgumentException if {@code count} is negative, {@code transforms} or {@code out} holds fewer
     *         than {@code count} entries, or one of the first {@code count} transforms is null
     */
    public static void trsToPackedAffineBatch(Transformf[] transforms, PackedAffineArray out, int count) {
        checkCount(count);
        checkLength(out.size(), count, "out");
        checkElements(transforms, count, "transforms");
        float[] dst = out.raw();
        for (int i = 0; i < count; i++) {
            Transformf t = transforms[i];
            setTrs(dst, out.offsetOf(i),
                    t.translation.x, t.translation.y, t.translation.z,
                    t.rotation.x, t.rotation.y, t.rotation.z, t.rotation.w,
                    t.scale.x, t.scale.y, t.scale.z);
        }
    }

    /**
     * Writes the upper 3x4 part of {@code count} matrices: entry {@code i} of {@code out} gets
     * {@code rRC = matrices[i].mCR()}. The last row of each matrix is ignored.
     *
     * @param matrices the column-major matrices
     * @param out receives the transforms at entries {@code 0} to
     *        {@code count - 1}
     * @param count the number of matrices
     * @throws IllegalArgumentException if {@code count} is negative, {@code matrices} or {@code out} holds fewer than
     *         {@code count} entries, or one of the first {@code count} matrices is null
     */
    public static void matrix4fToPackedAffineBatch(Matrix4f[] matrices, PackedAffineArray out, int count) {
        checkCount(count);
        checkLength(out.size(), count, "out");
        checkElements(matrices, count, "matrices");
        float[] dst = out.raw();
        for (int i = 0; i < count; i++) {
            Matrix4f m = matrices[i];
            int o = out.offsetOf(i);
            dst[o] = m.m00();
            dst[o + 1] = m.m10();
            dst[o + 2] = m.m20();
            dst[o + 3] = m.m30();
            dst[o + 4] = m.m01();
            dst[o + 5] = m.m11();
            dst[o + 6] = m.m21();
            dst[o + 7] = m.m31();
            dst[o + 8] = m.m02();
            dst[o + 9] = m.m12();
            dst[o + 10] = m.m22();
            dst[o + 11] = m.m32();
        }
    }

    /**
     * Transforms axis-aligned boxes by their packed affine transforms and writes the axis-aligned boxes that enclose
     * the results: the center is transformed and the half extents go through the absolute values of the 3x3 part,
     * which gives the tightest axis-aligned box around the transformed box.
     * <p>
     * For each {@code i} in {@code [0, count)}, with {@code k = order[i]}: reads transform {@code k} and the box
     * {@code minX[k]..maxZ[k]}, and writes {@code outMinX[k]..outMaxZ[k]}. Entries not listed in {@code order} are
     * left unchanged. The output arrays may be the input arrays: each entry is read before it is written. In that
     * case the indices in {@code order} must be distinct, or a repeated entry is transformed twice.
     *
     * @param transforms the transforms, indexed by entry
     * @param order the entries to process; the first {@code count} elements are used
     * @param minX the input box minimum x, indexed by entry
     * @param minY the input box minimum y
     * @param minZ the input box minimum z
     * @param maxX the input box maximum x
     * @param maxY the input box maximum y
     * @param maxZ the input box maximum z
     * @param outMinX receives the output box minimum x at index {@code order[i]}
     * @param outMinY receives the output box minimum y
     * @param outMinZ receives the output box minimum z
     * @param outMaxX receives the output box maximum x
     * @param outMaxY receives the output box maximum y
     * @param outMaxZ receives the output box maximum z
     * @param count the number of elements of {@code order} to process
     * @throws IllegalArgumentException if {@code count} is negative, {@code order} has fewer than {@code count}
     *         elements, or an index in {@code order} is negative or not below the size of {@code transforms} and the
     *         length of every box array
     */
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
        checkAabbArguments(transforms, order, minX, minY, minZ, maxX, maxY, maxZ,
                outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ, count);
        transformAabbPackedAffineRange(transforms, order, minX, minY, minZ, maxX, maxY, maxZ,
                outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ, 0, count);
    }

    /**
     * Same as {@link #transformAabbPackedAffineBatch}, processed as consecutive chunks of {@code order} on the calling
     * thread, in the same way as {@link BatchChunks#forEachChunk}; the results are identical. The arguments are
     * checked once, before the first chunk.
     *
     * @param transforms the transforms, indexed by entry
     * @param order the entries to process; the first {@code count} elements are used
     * @param minX the input box minimum x, indexed by entry
     * @param minY the input box minimum y
     * @param minZ the input box minimum z
     * @param maxX the input box maximum x
     * @param maxY the input box maximum y
     * @param maxZ the input box maximum z
     * @param outMinX receives the output box minimum x at index {@code order[i]}
     * @param outMinY receives the output box minimum y
     * @param outMinZ receives the output box minimum z
     * @param outMaxX receives the output box maximum x
     * @param outMaxY receives the output box maximum y
     * @param outMaxZ receives the output box maximum z
     * @param count the number of elements of {@code order} to process
     * @param chunkSize the number of elements per chunk; 0 or less means one chunk
     * @throws IllegalArgumentException if {@code count} is negative, {@code order} has fewer than {@code count}
     *         elements, or an index in {@code order} is negative or not below the size of {@code transforms} and the
     *         length of every box array
     */
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
        checkAabbArguments(transforms, order, minX, minY, minZ, maxX, maxY, maxZ,
                outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ, count);
        int size = chunkSize <= 0 ? java.lang.Math.max(count, 1) : chunkSize;
        for (int start = 0; start < count; start += size) {
            int end = (int) java.lang.Math.min((long) start + size, count);
            transformAabbPackedAffineRange(transforms, order, minX, minY, minZ, maxX, maxY, maxZ,
                    outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ, start, end);
        }
    }

    /**
     * Gathers transforms into a contiguous upload array in the order given, from index 0 of {@code outUpload}. Same as
     * {@link #uploadPackedAffine(PackedAffineArray, int[], float[], int, int)} with an output offset of 0.
     *
     * @param transforms the transforms, indexed by entry
     * @param order the entries to copy, in output order; the first {@code count} elements are used
     * @param outUpload receives {@code 12 * count} floats starting at index 0
     * @param count the number of transforms to copy
     * @throws IllegalArgumentException if {@code count} is negative, {@code order} has fewer than {@code count}
     *         elements, an index in {@code order} is out of range for {@code transforms}, or {@code outUpload} is
     *         shorter than {@code 12 * count}
     */
    public static void uploadPackedAffine(
            PackedAffineArray transforms,
            int[] order,
            float[] outUpload,
            int count) {
        uploadPackedAffine(transforms, order, outUpload, 0, count);
    }

    /**
     * Gathers transforms into a contiguous upload array in the order given: for each {@code i} in
     * {@code [0, count)}, the 12 floats of transform {@code order[i]} are copied to
     * {@code outUpload[outOffset + 12 * i]} to {@code outUpload[outOffset + 12 * i + 11]}. The output is packed by
     * position in {@code order}, unlike {@link #transformAabbPackedAffineBatch}, which writes by entry. If
     * {@code outUpload} is {@code transforms.raw()}, the output range must not overlap a transform that is still to
     * be read, or that transform is overwritten first.
     * <p>
     * {@code GpuTransformWriteKernels.writePackedAffine} performs the same copy; this is the reference
     * implementation.
     *
     * @param transforms the transforms, indexed by entry
     * @param order the entries to copy, in output order; the first {@code count} elements are used
     * @param outUpload receives {@code 12 * count} floats starting at {@code outOffset}
     * @param outOffset the index in {@code outUpload} of the first float written
     * @param count the number of transforms to copy
     * @throws IllegalArgumentException if {@code count} or {@code outOffset} is negative, {@code order} has fewer
     *         than {@code count} elements, an index in {@code order} is out of range for {@code transforms}, or
     *         {@code outUpload} is shorter than {@code outOffset + 12 * count}
     * @since 3.0.0
     */
    public static void uploadPackedAffine(
            PackedAffineArray transforms,
            int[] order,
            float[] outUpload,
            int outOffset,
            int count) {
        checkUploadArguments(transforms, order, outUpload, outOffset, count);
        uploadPackedAffineRange(transforms, order, outUpload, outOffset, 0, count);
    }

    /**
     * Same as {@link #uploadPackedAffine(PackedAffineArray, int[], float[], int)}, processed as consecutive chunks of
     * {@code order} on the calling thread; the results are identical.
     *
     * @param transforms the transforms, indexed by entry
     * @param order the entries to copy, in output order; the first {@code count} elements are used
     * @param outUpload receives {@code 12 * count} floats starting at index 0
     * @param count the number of transforms to copy
     * @param chunkSize the number of elements per chunk; 0 or less means one chunk
     * @throws IllegalArgumentException as {@link #uploadPackedAffine(PackedAffineArray, int[], float[], int)} does
     */
    public static void uploadPackedAffineChunked(
            PackedAffineArray transforms,
            int[] order,
            float[] outUpload,
            int count,
            int chunkSize) {
        uploadPackedAffineChunked(transforms, order, outUpload, 0, count, chunkSize);
    }

    /**
     * Same as {@link #uploadPackedAffine(PackedAffineArray, int[], float[], int, int)}, processed as consecutive
     * chunks of {@code order} on the calling thread, in the same way as {@link BatchChunks#forEachChunk}; the results
     * are identical. The arguments are checked once, before the first chunk.
     *
     * @param transforms the transforms, indexed by entry
     * @param order the entries to copy, in output order; the first {@code count} elements are used
     * @param outUpload receives {@code 12 * count} floats starting at {@code outOffset}
     * @param outOffset the index in {@code outUpload} of the first float written
     * @param count the number of transforms to copy
     * @param chunkSize the number of elements per chunk; 0 or less means one chunk
     * @throws IllegalArgumentException as {@link #uploadPackedAffine(PackedAffineArray, int[], float[], int, int)}
     *         does
     * @since 3.0.0
     */
    public static void uploadPackedAffineChunked(
            PackedAffineArray transforms,
            int[] order,
            float[] outUpload,
            int outOffset,
            int count,
            int chunkSize) {
        checkUploadArguments(transforms, order, outUpload, outOffset, count);
        int size = chunkSize <= 0 ? java.lang.Math.max(count, 1) : chunkSize;
        for (int start = 0; start < count; start += size) {
            int end = (int) java.lang.Math.min((long) start + size, count);
            uploadPackedAffineRange(transforms, order, outUpload, outOffset, start, end);
        }
    }

    private static void checkCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0: " + count);
        }
    }

    private static void checkLength(int length, long needed, String name) {
        if (length < needed) {
            throw new IllegalArgumentException(name + " holds " + length + ", needs " + needed);
        }
    }

    private static void checkElements(Object[] array, int count, String name) {
        checkLength(array.length, count, name);
        for (int i = 0; i < count; i++) {
            if (array[i] == null) {
                throw new IllegalArgumentException(name + "[" + i + "] is null");
            }
        }
    }

    private static void checkOrder(int[] order, int count, int limit) {
        checkLength(order.length, count, "order");
        for (int i = 0; i < count; i++) {
            int k = order[i];
            if (k < 0 || k >= limit) {
                throw new IllegalArgumentException("order[" + i + "] = " + k + " is outside [0, " + limit + ")");
            }
        }
    }

    private static void checkAabbArguments(
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
        checkCount(count);
        int limit = transforms.size();
        limit = java.lang.Math.min(limit, java.lang.Math.min(minX.length, java.lang.Math.min(minY.length, minZ.length)));
        limit = java.lang.Math.min(limit, java.lang.Math.min(maxX.length, java.lang.Math.min(maxY.length, maxZ.length)));
        limit = java.lang.Math.min(limit,
                java.lang.Math.min(outMinX.length, java.lang.Math.min(outMinY.length, outMinZ.length)));
        limit = java.lang.Math.min(limit,
                java.lang.Math.min(outMaxX.length, java.lang.Math.min(outMaxY.length, outMaxZ.length)));
        checkOrder(order, count, limit);
    }

    private static void checkUploadArguments(
            PackedAffineArray transforms, int[] order, float[] outUpload, int outOffset, int count) {
        checkCount(count);
        if (outOffset < 0) {
            throw new IllegalArgumentException("outOffset < 0: " + outOffset);
        }
        checkLength(outUpload.length, outOffset + (long) PackedAffineArray.STRIDE_FLOATS * count, "outUpload");
        checkOrder(order, count, transforms.size());
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
            float r00 = t[o];
            float r01 = t[o + 1];
            float r02 = t[o + 2];
            float tx = t[o + 3];
            float r10 = t[o + 4];
            float r11 = t[o + 5];
            float r12 = t[o + 6];
            float ty = t[o + 7];
            float r20 = t[o + 8];
            float r21 = t[o + 9];
            float r22 = t[o + 10];
            float tz = t[o + 11];

            float cx = (minX[idx] + maxX[idx]) * 0.5f;
            float cy = (minY[idx] + maxY[idx]) * 0.5f;
            float cz = (minZ[idx] + maxZ[idx]) * 0.5f;
            float ex = (maxX[idx] - minX[idx]) * 0.5f;
            float ey = (maxY[idx] - minY[idx]) * 0.5f;
            float ez = (maxZ[idx] - minZ[idx]) * 0.5f;

            float ncx = r00 * cx + r01 * cy + r02 * cz + tx;
            float ncy = r10 * cx + r11 * cy + r12 * cz + ty;
            float ncz = r20 * cx + r21 * cy + r22 * cz + tz;

            float nex = java.lang.Math.abs(r00) * ex + java.lang.Math.abs(r01) * ey + java.lang.Math.abs(r02) * ez;
            float ney = java.lang.Math.abs(r10) * ex + java.lang.Math.abs(r11) * ey + java.lang.Math.abs(r12) * ez;
            float nez = java.lang.Math.abs(r20) * ex + java.lang.Math.abs(r21) * ey + java.lang.Math.abs(r22) * ez;

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
            int outOffset,
            int start,
            int end) {
        float[] src = transforms.raw();
        for (int i = start; i < end; i++) {
            int idx = order[i];
            int srcBase = transforms.offsetOf(idx);
            int dstBase = outOffset + i * PackedAffineArray.STRIDE_FLOATS;
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

        // Row-major R * S: column c of the rotation is scaled by s_c.
        dst[offset] = sx - (q11 + q22) * sx;
        dst[offset + 1] = (q01 - q23) * sy;
        dst[offset + 2] = (q02 + q13) * sz;
        dst[offset + 3] = tx;

        dst[offset + 4] = (q01 + q23) * sx;
        dst[offset + 5] = sy - (q22 + q00) * sy;
        dst[offset + 6] = (q12 - q03) * sz;
        dst[offset + 7] = ty;

        dst[offset + 8] = (q02 - q13) * sx;
        dst[offset + 9] = (q12 + q03) * sy;
        dst[offset + 10] = sz - (q11 + q00) * sz;
        dst[offset + 11] = tz;
    }
}
