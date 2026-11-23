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
package org.dynamisengine.vectrix.gpu;

import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.affine.PackedAffineKernels;
import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.soa.SkinningKernels;
import org.dynamisengine.vectrix.soa.TransformSoA;

/**
 * A reference per-frame submission path: rigid skinning, instance transforms, world-space bounds and upload
 * staging in one call ({@link #processFrame}).
 * <p>
 * The default path builds packed 3x4 affine transforms ({@link PackedAffineKernels}). A matrix path that builds
 * {@link Matrix4f} instances and uploads column-major 4x4 matrices is kept for comparison and debugging; select it
 * per call with {@link Path#MATRIX_FALLBACK}, or for {@link Path#AUTO} with the system property
 * {@value #PROP_FORCE_MATRIX_FALLBACK}.
 * <p>
 * An instance owns internal scratch (transforms, matrices, a joint palette) and is not thread-safe: use one per
 * thread. {@code processFrame} does not allocate unless the instance count or the joint count exceeds the capacity
 * reached so far, in which case it grows the scratch.
 */
public final class InstanceSubmissionPipeline {
    /**
     * System property that, when {@code "true"} at construction time, makes {@link Path#AUTO} take the matrix path.
     */
    public static final String PROP_FORCE_MATRIX_FALLBACK = "vectrix.runtime.instanceSubmission.forceMatrix";

    /** The transform path {@link #processFrame} takes. */
    public enum Path {
        /** {@link #PACKED_AFFINE}, unless {@link #PROP_FORCE_MATRIX_FALLBACK} was set when the pipeline was made. */
        AUTO,
        /** Packed 3x4 affine transforms; uploads 12 floats per instance. */
        PACKED_AFFINE,
        /** {@link Matrix4f} transforms; uploads 16 floats per instance, column-major. */
        MATRIX_FALLBACK
    }

    private final boolean forceMatrixFallback;
    private PackedAffineArray packed;
    private Matrix4f[] matrices;
    private float[] skinningMatrixPalette12;
    private final Vector3f tmpMin = new Vector3f();
    private final Vector3f tmpMax = new Vector3f();

    /**
     * Creates a pipeline with scratch for {@code initialCapacity} instances and reads
     * {@link #PROP_FORCE_MATRIX_FALLBACK} once.
     *
     * @param initialCapacity the initial instance capacity; values below 1 are treated as 1
     */
    public InstanceSubmissionPipeline(int initialCapacity) {
        this.forceMatrixFallback = Boolean.getBoolean(PROP_FORCE_MATRIX_FALLBACK);
        int cap = java.lang.Math.max(1, initialCapacity);
        this.packed = new PackedAffineArray(cap);
        this.matrices = new Matrix4f[cap];
        this.skinningMatrixPalette12 = new float[12];
        for (int i = 0; i < cap; i++) {
            matrices[i] = new Matrix4f();
        }
    }

    /**
     * Runs one frame: skins the vertices, builds the instance transforms, transforms the bounds and stages the
     * upload.
     * <ol>
     * <li>Skinning: builds a rigid palette (rotation and translation; joint scale is ignored) from all
     * {@code joints.size()} joints, then skins {@code vertexCount} positions with four weighted joints each, from
     * {@code in*} to {@code out*}.</li>
     * <li>Transforms: instance {@code j} gets {@code T * R * S} of {@code transforms[j]}, for {@code j} in
     * {@code [0, instanceCount)}.</li>
     * <li>Bounds: for each slot {@code i} in {@code [0, instanceCount)}, with {@code j = order[i]}, the local box
     * {@code (min*[j], max*[j])} is transformed by instance {@code j} and its world-space axis-aligned bounds are
     * written to {@code outMin*[j]} and {@code outMax*[j]}. The bounds arrays are indexed by instance, not by slot;
     * instances that {@code order} does not name keep their previous output.</li>
     * <li>Upload: slot {@code i} of the upload array receives the transform of instance {@code order[i]}.</li>
     * </ol>
     *
     * @param transforms the instance transforms; at least {@code instanceCount} entries
     * @param order the instance index of each upload slot; at least {@code instanceCount} entries, each in
     *        {@code [0, instanceCount)}
     * @param minX the local box minimum x per instance
     * @param minY the local box minimum y per instance
     * @param minZ the local box minimum z per instance
     * @param maxX the local box maximum x per instance
     * @param maxY the local box maximum y per instance
     * @param maxZ the local box maximum z per instance
     * @param outMinX receives the world box minimum x per instance
     * @param outMinY receives the world box minimum y per instance
     * @param outMinZ receives the world box minimum z per instance
     * @param outMaxX receives the world box maximum x per instance
     * @param outMaxY receives the world box maximum y per instance
     * @param outMaxZ receives the world box maximum z per instance
     * @param joints the joint transforms of the skeleton (rotation and translation are used)
     * @param jointIndices four joint indices per vertex, vertex {@code v} at {@code 4 * v}
     * @param jointWeights four weights per vertex, matching {@code jointIndices}
     * @param inX the bind-pose vertex x coordinates
     * @param inY the bind-pose vertex y coordinates
     * @param inZ the bind-pose vertex z coordinates
     * @param outX receives the skinned x coordinates
     * @param outY receives the skinned y coordinates
     * @param outZ receives the skinned z coordinates
     * @param packedUpload receives 12 floats per slot (packed 3x4 affine, row-major) on the packed path; not used
     *        on the matrix path
     * @param matrixUpload receives 16 floats per slot (column-major 4x4) on the matrix path; not used on the packed
     *        path
     * @param instanceCount the number of instances and upload slots
     * @param vertexCount the number of vertices to skin
     * @param path the path to take
     * @return {@code packedUpload} or {@code matrixUpload}, whichever was written
     */
    public float[] processFrame(
            Transformf[] transforms,
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
            TransformSoA joints,
            int[] jointIndices,
            float[] jointWeights,
            float[] inX,
            float[] inY,
            float[] inZ,
            float[] outX,
            float[] outY,
            float[] outZ,
            float[] packedUpload,
            float[] matrixUpload,
            int instanceCount,
            int vertexCount,
            Path path) {
        ensureCapacity(instanceCount);
        ensureSkinningPaletteCapacity(joints.size());
        SkinningKernels.buildRigidMatrixPalette12(joints, skinningMatrixPalette12, joints.size());
        SkinningKernels.skinLbs4MatrixPalette(skinningMatrixPalette12, jointIndices, jointWeights, inX, inY, inZ, outX, outY, outZ, vertexCount);

        Path effective = resolvePath(path);
        if (effective == Path.MATRIX_FALLBACK) {
            runMatrixPath(transforms, order, minX, minY, minZ, maxX, maxY, maxZ,
                    outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ, matrixUpload, instanceCount);
            return matrixUpload;
        }

        runPackedPath(transforms, order, minX, minY, minZ, maxX, maxY, maxZ,
                outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ, packedUpload, instanceCount);
        return packedUpload;
    }

    private Path resolvePath(Path requested) {
        if (requested == Path.MATRIX_FALLBACK || requested == Path.PACKED_AFFINE) {
            return requested;
        }
        return forceMatrixFallback ? Path.MATRIX_FALLBACK : Path.PACKED_AFFINE;
    }

    private void runPackedPath(
            Transformf[] transforms,
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
            float[] packedUpload,
            int count) {
        PackedAffineKernels.trsToPackedAffineBatch(transforms, packed, count);
        PackedAffineKernels.transformAabbPackedAffineBatch(
                packed, order, minX, minY, minZ, maxX, maxY, maxZ,
                outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ, count);
        PackedAffineKernels.uploadPackedAffine(packed, order, packedUpload, count);
    }

    private void runMatrixPath(
            Transformf[] transforms,
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
            float[] matrixUpload,
            int count) {
        for (int i = 0; i < count; i++) {
            Transformf t = transforms[i];
            matrices[i].translationRotateScale(t.translation, t.rotation, t.scale);
        }
        for (int i = 0; i < count; i++) {
            int idx = order[i];
            Matrix4f m = matrices[idx];
            m.transformAab(minX[idx], minY[idx], minZ[idx], maxX[idx], maxY[idx], maxZ[idx], tmpMin, tmpMax);
            outMinX[idx] = tmpMin.x;
            outMinY[idx] = tmpMin.y;
            outMinZ[idx] = tmpMin.z;
            outMaxX[idx] = tmpMax.x;
            outMaxY[idx] = tmpMax.y;
            outMaxZ[idx] = tmpMax.z;
            int base = i << 4;
            matrixUpload[base] = m.m00();
            matrixUpload[base + 1] = m.m01();
            matrixUpload[base + 2] = m.m02();
            matrixUpload[base + 3] = m.m03();
            matrixUpload[base + 4] = m.m10();
            matrixUpload[base + 5] = m.m11();
            matrixUpload[base + 6] = m.m12();
            matrixUpload[base + 7] = m.m13();
            matrixUpload[base + 8] = m.m20();
            matrixUpload[base + 9] = m.m21();
            matrixUpload[base + 10] = m.m22();
            matrixUpload[base + 11] = m.m23();
            matrixUpload[base + 12] = m.m30();
            matrixUpload[base + 13] = m.m31();
            matrixUpload[base + 14] = m.m32();
            matrixUpload[base + 15] = m.m33();
        }
    }

    private void ensureCapacity(int count) {
        if (count <= packed.size()) {
            return;
        }
        packed = new PackedAffineArray(count);
        Matrix4f[] next = new Matrix4f[count];
        System.arraycopy(matrices, 0, next, 0, matrices.length);
        for (int i = matrices.length; i < count; i++) {
            next[i] = new Matrix4f();
        }
        matrices = next;
    }

    private void ensureSkinningPaletteCapacity(int jointCount) {
        int needed = jointCount * 12;
        if (skinningMatrixPalette12.length < needed) {
            skinningMatrixPalette12 = new float[needed];
        }
    }
}
