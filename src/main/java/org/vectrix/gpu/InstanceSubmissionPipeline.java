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
package org.vectrix.gpu;

import org.vectrix.affine.PackedAffineArray;
import org.vectrix.affine.PackedAffineKernels;
import org.vectrix.affine.Transformf;
import org.vectrix.core.Matrix4f;
import org.vectrix.core.Vector3f;
import org.vectrix.soa.SkinningKernels;
import org.vectrix.soa.TransformSoA;

/**
 * Runtime-oriented transform + skinning + bounds/update + upload preparation path.
 *
 * Default execution path is packed-affine. A matrix fallback is retained behind
 * explicit path selection or a runtime property for compatibility/debugging.
 */
public final class InstanceSubmissionPipeline {
    public static final String PROP_FORCE_MATRIX_FALLBACK = "vectrix.runtime.instanceSubmission.forceMatrix";

    public enum Path {
        AUTO,
        PACKED_AFFINE,
        MATRIX_FALLBACK
    }

    private final boolean forceMatrixFallback;
    private PackedAffineArray packed;
    private Matrix4f[] matrices;
    private final Vector3f tmpMin = new Vector3f();
    private final Vector3f tmpMax = new Vector3f();

    public InstanceSubmissionPipeline(int initialCapacity) {
        this.forceMatrixFallback = Boolean.getBoolean(PROP_FORCE_MATRIX_FALLBACK);
        int cap = java.lang.Math.max(1, initialCapacity);
        this.packed = new PackedAffineArray(cap);
        this.matrices = new Matrix4f[cap];
        for (int i = 0; i < cap; i++) {
            matrices[i] = new Matrix4f();
        }
    }

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
        SkinningKernels.skinLbs4(joints, jointIndices, jointWeights, inX, inY, inZ, outX, outY, outZ, vertexCount);

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
}
