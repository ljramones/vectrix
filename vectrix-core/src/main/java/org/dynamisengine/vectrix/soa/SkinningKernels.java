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
package org.dynamisengine.vectrix.soa;


/**
 * Batch skinning kernels: each output vertex blends up to four joint transforms of its bind-pose position.
 * <p>
 * Positions are structure-of-arrays ({@code inX}, {@code inY}, {@code inZ}). Joint indices and weights are
 * interleaved, four per vertex ({@code jointIndices[4·i + k]}), except in the {@code ...SoA} variants, which take
 * one array per influence slot. The weights are used as given, not normalized: if they do not sum to 1 the result is
 * scaled. Unused slots need weight 0 and any valid joint index.
 * <p>
 * Which kernel to use:
 * <ul>
 * <li>{@link #skinLbs4MatrixPalette}: linear blend skinning from a packed 3×4 matrix palette; the fastest and the
 * default. Build the palette once per frame with {@link #buildMatrixPalette12} (joint scale included) or
 * {@link #buildRigidMatrixPalette12} (rotation and translation only).</li>
 * <li>{@link #skinLbs4} and {@link #skinLbs4SoA}: linear blend skinning straight from a {@link TransformSoA},
 * without a palette. Each joint applies its full {@code T·R·S}: scale, then rotation, then translation.</li>
 * <li>{@link #skinDualQuat4} and {@link #skinDualQuat4SoA}: dual quaternion blending, which keeps volume at
 * twisting joints; slower, and rigid only, since a unit dual quaternion holds no scale.</li>
 * </ul>
 * <p>
 * All kernels are scalar in both jars (a per-lane gather of joint data measured slower than the scalar loop), do not
 * allocate and keep no state, so they are safe to call from any thread on separate outputs. They process vertices
 * {@code [0, count)} from index 0. Every array length and every joint index is checked before anything is written: a
 * short array, a joint index outside the joint data or a negative count throws {@link IllegalArgumentException} and
 * leaves the outputs unchanged. Each output array may be the corresponding input array. STRICT mode adds no
 * validation: the results are the same in both modes.
 */
public final class SkinningKernels {

    private SkinningKernels() {
    }

    /**
     * Linear blend skinning with four influences per vertex, interleaved:
     * {@code out = Σ w_k·(q_k·(s_k ∘ p)·q_k⁻¹ + t_k)}, where {@code s_k ∘ p} is the component-wise product with the
     * joint's scale. With unit scale this is rigid skinning, bit for bit. The rotations must be unit quaternions. No
     * SIMD path.
     *
     * @param joints the skinning transforms (posed world times inverse bind-pose world) of the joints
     * @param jointIndices four joint indices per vertex, interleaved: {@code jointIndices[4·i + k]}, each in
     *        {@code [0, joints.size())}
     * @param jointWeights four weights per vertex, interleaved like {@code jointIndices}
     * @param inX the bind-pose x coordinates, one per vertex
     * @param inY the bind-pose y coordinates
     * @param inZ the bind-pose z coordinates
     * @param outX receives the skinned x coordinates; may be {@code inX}
     * @param outY receives the skinned y coordinates; may be {@code inY}
     * @param outZ receives the skinned z coordinates; may be {@code inZ}
     * @param count the number of vertices, at least 0
     * @throws IllegalArgumentException if an argument is null, {@code count} is negative, an array is too short or a
     *         joint index is out of range; nothing is written then
     */
    public static void skinLbs4(TransformSoA joints, int[] jointIndices, float[] jointWeights, float[] inX, float[] inY, float[] inZ,
            float[] outX, float[] outY, float[] outZ, int count) {
        if (joints == null) {
            throw new IllegalArgumentException("joints must not be null");
        }
        checkInterleaved(jointIndices, jointWeights, joints.size(), count);
        checkPositions(inX, inY, inZ, outX, outY, outZ, count);
        for (int i = 0; i < count; i++) {
            int base = i << 2;
            skinLbs4Single(joints, jointIndices[base], jointIndices[base + 1], jointIndices[base + 2], jointIndices[base + 3], jointWeights[base], jointWeights[base + 1],
                    jointWeights[base + 2], jointWeights[base + 3], inX[i], inY[i], inZ[i], outX, outY, outZ, i);
        }
    }

    /**
     * Linear blend skinning with four influences per vertex, one array per influence slot:
     * {@code out = Σ w_k·(q_k·(s_k ∘ p)·q_k⁻¹ + t_k)}, as in {@link #skinLbs4}, with the same bits for the same
     * influences. The rotations must be unit quaternions. No SIMD path.
     *
     * @param joints the skinning transforms (posed world times inverse bind-pose world) of the joints
     * @param j0 the first joint index of each vertex, in {@code [0, joints.size())}
     * @param j1 the second joint index of each vertex
     * @param j2 the third joint index of each vertex
     * @param j3 the fourth joint index of each vertex
     * @param w0 the weight of {@code j0} for each vertex
     * @param w1 the weight of {@code j1} for each vertex
     * @param w2 the weight of {@code j2} for each vertex
     * @param w3 the weight of {@code j3} for each vertex
     * @param inX the bind-pose x coordinates, one per vertex
     * @param inY the bind-pose y coordinates
     * @param inZ the bind-pose z coordinates
     * @param outX receives the skinned x coordinates; may be {@code inX}
     * @param outY receives the skinned y coordinates; may be {@code inY}
     * @param outZ receives the skinned z coordinates; may be {@code inZ}
     * @param count the number of vertices, at least 0
     * @throws IllegalArgumentException if an argument is null, {@code count} is negative, an array is too short or a
     *         joint index is out of range; nothing is written then
     */
    public static void skinLbs4SoA(TransformSoA joints, int[] j0, int[] j1, int[] j2, int[] j3, float[] w0, float[] w1, float[] w2, float[] w3,
            float[] inX, float[] inY, float[] inZ, float[] outX, float[] outY, float[] outZ, int count) {
        if (joints == null) {
            throw new IllegalArgumentException("joints must not be null");
        }
        checkPerSlot(j0, j1, j2, j3, w0, w1, w2, w3, joints.size(), count);
        checkPositions(inX, inY, inZ, outX, outY, outZ, count);
        for (int i = 0; i < count; i++) {
            skinLbs4Single(joints, j0[i], j1[i], j2[i], j3[i], w0[i], w1[i], w2[i], w3[i], inX[i], inY[i], inZ[i], outX, outY, outZ, i);
        }
    }

    /**
     * Dual quaternion skinning with four influences per vertex, interleaved.
     * <p>
     * The blend is dual quaternion linear blending: each joint's dual quaternion is weighted, with its sign flipped
     * when its rotation lies in the opposite hemisphere from the first influence's ({@code j0}), the sum is divided by
     * the length of its real part, and the resulting rigid transform is applied to the position. If the blended real
     * part is exactly zero, the position is copied unchanged. The joint dual quaternions must be unit (unit real part,
     * as from {@link org.dynamisengine.vectrix.affine.DualQuatTransformf}); they hold no scale, so this skinning is
     * rigid. No SIMD path.
     *
     * @param joints the skinning transforms of the joints as dual quaternions
     * @param jointIndices four joint indices per vertex, interleaved: {@code jointIndices[4·i + k]}, each in
     *        {@code [0, joints.rx.length)}
     * @param jointWeights four weights per vertex, interleaved like {@code jointIndices}
     * @param inX the bind-pose x coordinates, one per vertex
     * @param inY the bind-pose y coordinates
     * @param inZ the bind-pose z coordinates
     * @param outX receives the skinned x coordinates; may be {@code inX}
     * @param outY receives the skinned y coordinates; may be {@code inY}
     * @param outZ receives the skinned z coordinates; may be {@code inZ}
     * @param count the number of vertices, at least 0
     * @throws IllegalArgumentException if an argument is null, {@code count} is negative, an array is too short or a
     *         joint index is out of range; nothing is written then
     */
    public static void skinDualQuat4(DualQuatSoA joints, int[] jointIndices, float[] jointWeights, float[] inX, float[] inY, float[] inZ,
            float[] outX, float[] outY, float[] outZ, int count) {
        if (joints == null) {
            throw new IllegalArgumentException("joints must not be null");
        }
        checkInterleaved(jointIndices, jointWeights, joints.rx.length, count);
        checkPositions(inX, inY, inZ, outX, outY, outZ, count);
        for (int i = 0; i < count; i++) {
            int base = i << 2;
            skinDualQuat4Single(joints, jointIndices[base], jointIndices[base + 1], jointIndices[base + 2], jointIndices[base + 3], jointWeights[base], jointWeights[base + 1],
                    jointWeights[base + 2], jointWeights[base + 3], inX[i], inY[i], inZ[i], outX, outY, outZ, i);
        }
    }

    /**
     * Dual quaternion skinning with four influences per vertex, one array per influence slot. Gives the same bits as
     * {@link #skinDualQuat4} for the same influences.
     * <p>
     * The blend is dual quaternion linear blending: each joint's dual quaternion is weighted, with its sign flipped
     * when its rotation lies in the opposite hemisphere from the first influence's ({@code j0}), the sum is divided by
     * the length of its real part, and the resulting rigid transform is applied to the position. If the blended real
     * part is exactly zero, the position is copied unchanged. The joint dual quaternions must be unit (unit real part,
     * as from {@link org.dynamisengine.vectrix.affine.DualQuatTransformf}); they hold no scale, so this skinning is
     * rigid. No SIMD path.
     *
     * @param joints the skinning transforms of the joints as dual quaternions
     * @param j0 the first joint index of each vertex, in {@code [0, joints.rx.length)}
     * @param j1 the second joint index of each vertex
     * @param j2 the third joint index of each vertex
     * @param j3 the fourth joint index of each vertex
     * @param w0 the weight of {@code j0} for each vertex
     * @param w1 the weight of {@code j1} for each vertex
     * @param w2 the weight of {@code j2} for each vertex
     * @param w3 the weight of {@code j3} for each vertex
     * @param inX the bind-pose x coordinates, one per vertex
     * @param inY the bind-pose y coordinates
     * @param inZ the bind-pose z coordinates
     * @param outX receives the skinned x coordinates; may be {@code inX}
     * @param outY receives the skinned y coordinates; may be {@code inY}
     * @param outZ receives the skinned z coordinates; may be {@code inZ}
     * @param count the number of vertices, at least 0
     * @throws IllegalArgumentException if an argument is null, {@code count} is negative, an array is too short or a
     *         joint index is out of range; nothing is written then
     */
    public static void skinDualQuat4SoA(DualQuatSoA joints, int[] j0, int[] j1, int[] j2, int[] j3, float[] w0, float[] w1, float[] w2, float[] w3,
            float[] inX, float[] inY, float[] inZ, float[] outX, float[] outY, float[] outZ, int count) {
        if (joints == null) {
            throw new IllegalArgumentException("joints must not be null");
        }
        checkPerSlot(j0, j1, j2, j3, w0, w1, w2, w3, joints.rx.length, count);
        checkPositions(inX, inY, inZ, outX, outY, outZ, count);
        for (int i = 0; i < count; i++) {
            skinDualQuat4Single(joints, j0[i], j1[i], j2[i], j3[i], w0[i], w1[i], w2[i], w3[i], inX[i], inY[i], inZ[i], outX, outY, outZ, i);
        }
    }

    /**
     * Builds a matrix palette for {@link #skinLbs4MatrixPalette} with each joint's full {@code T·R·S}: 12 floats per
     * joint, joint {@code j} at {@code matrixPalette12[12·j ..]}, in the row-major packed 3×4 layout of
     * {@link org.dynamisengine.vectrix.affine.PackedAffineArray}:
     * {@code [r00 r01 r02 tx  r10 r11 r12 ty  r20 r21 r22 tz]}, where {@code rRC} is row R, column C. Column C of the
     * 3×3 part is column C of the rotation matrix times the joint's scale on axis C, so with unit scale the palette
     * equals {@link #buildRigidMatrixPalette12}'s bit for bit.
     * <p>
     * The quaternion is not normalized, so it must be unit. Joints {@code [0, jointCount)} are converted.
     *
     * @param joints the skinning transforms of the joints
     * @param matrixPalette12 receives {@code 12·jointCount} floats from index 0
     * @param jointCount the number of joints, in {@code [0, joints.size()]}
     * @throws IllegalArgumentException if an argument is null, {@code jointCount} is out of range or the palette is
     *         too short; nothing is written then
     * @since 3.0.0
     */
    public static void buildMatrixPalette12(TransformSoA joints, float[] matrixPalette12, int jointCount) {
        checkPalette(joints, matrixPalette12, jointCount);
        writePalette(joints, matrixPalette12, jointCount, true);
    }

    /**
     * Builds a rigid matrix palette for {@link #skinLbs4MatrixPalette}: 12 floats per joint, joint {@code j} at
     * {@code matrixPalette12[12·j ..]}, in the row-major packed 3×4 layout of
     * {@link org.dynamisengine.vectrix.affine.PackedAffineArray}:
     * {@code [r00 r01 r02 tx  r10 r11 r12 ty  r20 r21 r22 tz]}, where {@code rRC} is row R, column C.
     * <p>
     * Joints are rigid: the 3×3 part is the rotation matrix of each joint's quaternion and the scale arrays are
     * ignored. For joints with scale, use {@link #buildMatrixPalette12}. The quaternion is not normalized, so it must
     * be unit. Joints {@code [0, jointCount)} are converted.
     *
     * @param joints the skinning transforms of the joints
     * @param matrixPalette12 receives {@code 12·jointCount} floats from index 0
     * @param jointCount the number of joints, in {@code [0, joints.size()]}
     * @throws IllegalArgumentException if an argument is null, {@code jointCount} is out of range or the palette is
     *         too short; nothing is written then
     */
    public static void buildRigidMatrixPalette12(TransformSoA joints, float[] matrixPalette12, int jointCount) {
        checkPalette(joints, matrixPalette12, jointCount);
        writePalette(joints, matrixPalette12, jointCount, false);
    }

    private static void writePalette(TransformSoA joints, float[] matrixPalette12, int jointCount, boolean scaled) {
        for (int j = 0; j < jointCount; j++) {
            float x = joints.qx[j];
            float y = joints.qy[j];
            float z = joints.qz[j];
            float w = joints.qw[j];
            float tx = joints.tx[j];
            float ty = joints.ty[j];
            float tz = joints.tz[j];
            float sx = scaled ? joints.sx[j] : 1.0f;
            float sy = scaled ? joints.sy[j] : 1.0f;
            float sz = scaled ? joints.sz[j] : 1.0f;

            float xx = x * x;
            float yy = y * y;
            float zz = z * z;
            float xy = x * y;
            float xz = x * z;
            float yz = y * z;
            float wx = w * x;
            float wy = w * y;
            float wz = w * z;

            int o = j * 12;
            matrixPalette12[o] = (1.0f - 2.0f * (yy + zz)) * sx;
            matrixPalette12[o + 1] = 2.0f * (xy - wz) * sy;
            matrixPalette12[o + 2] = 2.0f * (xz + wy) * sz;
            matrixPalette12[o + 3] = tx;

            matrixPalette12[o + 4] = 2.0f * (xy + wz) * sx;
            matrixPalette12[o + 5] = (1.0f - 2.0f * (xx + zz)) * sy;
            matrixPalette12[o + 6] = 2.0f * (yz - wx) * sz;
            matrixPalette12[o + 7] = ty;

            matrixPalette12[o + 8] = 2.0f * (xz - wy) * sx;
            matrixPalette12[o + 9] = 2.0f * (yz + wx) * sy;
            matrixPalette12[o + 10] = (1.0f - 2.0f * (xx + yy)) * sz;
            matrixPalette12[o + 11] = tz;
        }
    }

    /**
     * Linear blend skinning from a matrix palette, with four influences per vertex, interleaved: {@code out = Σ
     * w_k·(M_k·p)}. The palette holds 12 floats per joint, joint {@code j} at {@code matrixPalette12[12·j ..]},
     * row-major as in {@link org.dynamisengine.vectrix.affine.PackedAffineArray}: {@code [r00 r01 r02 tx  r10 r11 r12
     * ty  r20 r21 r22 tz]}. A {@code PackedAffineArray} can be passed directly through {@code raw()}.
     * <p>
     * This is the default CPU skinning kernel and the fastest. The 3×3 part is used as given, so joints may carry scale
     * or shear when the palette has them ({@link #buildMatrixPalette12} includes the joint scale,
     * {@link #buildRigidMatrixPalette12} does not). The results are close to, but not bitwise equal to,
     * {@link #skinLbs4}'s for the same joints, since the arithmetic differs. No SIMD path.
     *
     * @param matrixPalette12 the palette, 12 floats per joint; its joint count is {@code matrixPalette12.length / 12}
     * @param jointIndices four joint indices per vertex, interleaved: {@code jointIndices[4·i + k]}, each in
     *        {@code [0, matrixPalette12.length / 12)}
     * @param jointWeights four weights per vertex, interleaved like {@code jointIndices}
     * @param inX the bind-pose x coordinates, one per vertex
     * @param inY the bind-pose y coordinates
     * @param inZ the bind-pose z coordinates
     * @param outX receives the skinned x coordinates; may be {@code inX}
     * @param outY receives the skinned y coordinates; may be {@code inY}
     * @param outZ receives the skinned z coordinates; may be {@code inZ}
     * @param count the number of vertices, at least 0
     * @throws IllegalArgumentException if an argument is null, {@code count} is negative, an array is too short or a
     *         joint index is out of range; nothing is written then
     */
    public static void skinLbs4MatrixPalette(float[] matrixPalette12, int[] jointIndices, float[] jointWeights,
            float[] inX, float[] inY, float[] inZ, float[] outX, float[] outY, float[] outZ, int count) {
        if (matrixPalette12 == null) {
            throw new IllegalArgumentException("matrixPalette12 must not be null");
        }
        checkInterleaved(jointIndices, jointWeights, matrixPalette12.length / 12, count);
        checkPositions(inX, inY, inZ, outX, outY, outZ, count);
        for (int i = 0; i < count; i++) {
            int base = i << 2;
            float x = inX[i];
            float y = inY[i];
            float z = inZ[i];

            int j0 = jointIndices[base] * 12;
            int j1 = jointIndices[base + 1] * 12;
            int j2 = jointIndices[base + 2] * 12;
            int j3 = jointIndices[base + 3] * 12;

            float w0 = jointWeights[base];
            float w1 = jointWeights[base + 1];
            float w2 = jointWeights[base + 2];
            float w3 = jointWeights[base + 3];

            float ox = 0.0f;
            float oy = 0.0f;
            float oz = 0.0f;

            ox += (matrixPalette12[j0] * x + matrixPalette12[j0 + 1] * y + matrixPalette12[j0 + 2] * z + matrixPalette12[j0 + 3]) * w0;
            oy += (matrixPalette12[j0 + 4] * x + matrixPalette12[j0 + 5] * y + matrixPalette12[j0 + 6] * z + matrixPalette12[j0 + 7]) * w0;
            oz += (matrixPalette12[j0 + 8] * x + matrixPalette12[j0 + 9] * y + matrixPalette12[j0 + 10] * z + matrixPalette12[j0 + 11]) * w0;

            ox += (matrixPalette12[j1] * x + matrixPalette12[j1 + 1] * y + matrixPalette12[j1 + 2] * z + matrixPalette12[j1 + 3]) * w1;
            oy += (matrixPalette12[j1 + 4] * x + matrixPalette12[j1 + 5] * y + matrixPalette12[j1 + 6] * z + matrixPalette12[j1 + 7]) * w1;
            oz += (matrixPalette12[j1 + 8] * x + matrixPalette12[j1 + 9] * y + matrixPalette12[j1 + 10] * z + matrixPalette12[j1 + 11]) * w1;

            ox += (matrixPalette12[j2] * x + matrixPalette12[j2 + 1] * y + matrixPalette12[j2 + 2] * z + matrixPalette12[j2 + 3]) * w2;
            oy += (matrixPalette12[j2 + 4] * x + matrixPalette12[j2 + 5] * y + matrixPalette12[j2 + 6] * z + matrixPalette12[j2 + 7]) * w2;
            oz += (matrixPalette12[j2 + 8] * x + matrixPalette12[j2 + 9] * y + matrixPalette12[j2 + 10] * z + matrixPalette12[j2 + 11]) * w2;

            ox += (matrixPalette12[j3] * x + matrixPalette12[j3 + 1] * y + matrixPalette12[j3 + 2] * z + matrixPalette12[j3 + 3]) * w3;
            oy += (matrixPalette12[j3 + 4] * x + matrixPalette12[j3 + 5] * y + matrixPalette12[j3 + 6] * z + matrixPalette12[j3 + 7]) * w3;
            oz += (matrixPalette12[j3 + 8] * x + matrixPalette12[j3 + 9] * y + matrixPalette12[j3 + 10] * z + matrixPalette12[j3 + 11]) * w3;

            outX[i] = ox;
            outY[i] = oy;
            outZ[i] = oz;
        }
    }

    private static void skinLbs4Single(TransformSoA joints, int j0, int j1, int j2, int j3, float w0, float w1, float w2, float w3, float x, float y,
            float z, float[] outX, float[] outY, float[] outZ, int i) {
        float ox = 0.0f;
        float oy = 0.0f;
        float oz = 0.0f;

        // each joint: scale, then rotate (q·p·q⁻¹ = p + w·t + v × t, t = 2·v × p), then translate
        float px = x * joints.sx[j0], py = y * joints.sy[j0], pz = z * joints.sz[j0];
        float rx = joints.qx[j0], ry = joints.qy[j0], rz = joints.qz[j0], rw = joints.qw[j0];
        float tx2 = 2.0f * (ry * pz - rz * py);
        float ty2 = 2.0f * (rz * px - rx * pz);
        float tz2 = 2.0f * (rx * py - ry * px);
        ox += (px + rw * tx2 + (ry * tz2 - rz * ty2) + joints.tx[j0]) * w0;
        oy += (py + rw * ty2 + (rz * tx2 - rx * tz2) + joints.ty[j0]) * w0;
        oz += (pz + rw * tz2 + (rx * ty2 - ry * tx2) + joints.tz[j0]) * w0;

        px = x * joints.sx[j1];
        py = y * joints.sy[j1];
        pz = z * joints.sz[j1];
        rx = joints.qx[j1];
        ry = joints.qy[j1];
        rz = joints.qz[j1];
        rw = joints.qw[j1];
        tx2 = 2.0f * (ry * pz - rz * py);
        ty2 = 2.0f * (rz * px - rx * pz);
        tz2 = 2.0f * (rx * py - ry * px);
        ox += (px + rw * tx2 + (ry * tz2 - rz * ty2) + joints.tx[j1]) * w1;
        oy += (py + rw * ty2 + (rz * tx2 - rx * tz2) + joints.ty[j1]) * w1;
        oz += (pz + rw * tz2 + (rx * ty2 - ry * tx2) + joints.tz[j1]) * w1;

        px = x * joints.sx[j2];
        py = y * joints.sy[j2];
        pz = z * joints.sz[j2];
        rx = joints.qx[j2];
        ry = joints.qy[j2];
        rz = joints.qz[j2];
        rw = joints.qw[j2];
        tx2 = 2.0f * (ry * pz - rz * py);
        ty2 = 2.0f * (rz * px - rx * pz);
        tz2 = 2.0f * (rx * py - ry * px);
        ox += (px + rw * tx2 + (ry * tz2 - rz * ty2) + joints.tx[j2]) * w2;
        oy += (py + rw * ty2 + (rz * tx2 - rx * tz2) + joints.ty[j2]) * w2;
        oz += (pz + rw * tz2 + (rx * ty2 - ry * tx2) + joints.tz[j2]) * w2;

        px = x * joints.sx[j3];
        py = y * joints.sy[j3];
        pz = z * joints.sz[j3];
        rx = joints.qx[j3];
        ry = joints.qy[j3];
        rz = joints.qz[j3];
        rw = joints.qw[j3];
        tx2 = 2.0f * (ry * pz - rz * py);
        ty2 = 2.0f * (rz * px - rx * pz);
        tz2 = 2.0f * (rx * py - ry * px);
        ox += (px + rw * tx2 + (ry * tz2 - rz * ty2) + joints.tx[j3]) * w3;
        oy += (py + rw * ty2 + (rz * tx2 - rx * tz2) + joints.ty[j3]) * w3;
        oz += (pz + rw * tz2 + (rx * ty2 - ry * tx2) + joints.tz[j3]) * w3;

        outX[i] = ox;
        outY[i] = oy;
        outZ[i] = oz;
    }

    private static void skinDualQuat4Single(DualQuatSoA joints, int j0, int j1, int j2, int j3, float w0, float w1, float w2, float w3, float x, float y,
            float z, float[] outX, float[] outY, float[] outZ, int i) {
        float refX = joints.rx[j0], refY = joints.ry[j0], refZ = joints.rz[j0], refW = joints.rw[j0];

        float arx = 0.0f, ary = 0.0f, arz = 0.0f, arw = 0.0f;
        float adx = 0.0f, ady = 0.0f, adz = 0.0f, adw = 0.0f;

        float s0 = signForReference(joints, refX, refY, refZ, refW, j0);
        float s1 = signForReference(joints, refX, refY, refZ, refW, j1);
        float s2 = signForReference(joints, refX, refY, refZ, refW, j2);
        float s3 = signForReference(joints, refX, refY, refZ, refW, j3);

        float sw0 = s0 * w0;
        float sw1 = s1 * w1;
        float sw2 = s2 * w2;
        float sw3 = s3 * w3;

        arx = joints.rx[j0] * sw0 + joints.rx[j1] * sw1 + joints.rx[j2] * sw2 + joints.rx[j3] * sw3;
        ary = joints.ry[j0] * sw0 + joints.ry[j1] * sw1 + joints.ry[j2] * sw2 + joints.ry[j3] * sw3;
        arz = joints.rz[j0] * sw0 + joints.rz[j1] * sw1 + joints.rz[j2] * sw2 + joints.rz[j3] * sw3;
        arw = joints.rw[j0] * sw0 + joints.rw[j1] * sw1 + joints.rw[j2] * sw2 + joints.rw[j3] * sw3;

        adx = joints.dx[j0] * sw0 + joints.dx[j1] * sw1 + joints.dx[j2] * sw2 + joints.dx[j3] * sw3;
        ady = joints.dy[j0] * sw0 + joints.dy[j1] * sw1 + joints.dy[j2] * sw2 + joints.dy[j3] * sw3;
        adz = joints.dz[j0] * sw0 + joints.dz[j1] * sw1 + joints.dz[j2] * sw2 + joints.dz[j3] * sw3;
        adw = joints.dw[j0] * sw0 + joints.dw[j1] * sw1 + joints.dw[j2] * sw2 + joints.dw[j3] * sw3;

        float norm2 = arx * arx + ary * ary + arz * arz + arw * arw;
        if (norm2 == 0.0f) {
            outX[i] = x;
            outY[i] = y;
            outZ[i] = z;
            return;
        }
        float invNorm = org.dynamisengine.vectrix.core.Math.invsqrt(norm2);
        arx *= invNorm;
        ary *= invNorm;
        arz *= invNorm;
        arw *= invNorm;
        adx *= invNorm;
        ady *= invNorm;
        adz *= invNorm;
        adw *= invNorm;

        float tx2 = 2.0f * (ary * z - arz * y);
        float ty2 = 2.0f * (arz * x - arx * z);
        float tz2 = 2.0f * (arx * y - ary * x);
        float rx = x + arw * tx2 + (ary * tz2 - arz * ty2);
        float ry = y + arw * ty2 + (arz * tx2 - arx * tz2);
        float rz = z + arw * tz2 + (arx * ty2 - ary * tx2);

        float tx = 2.0f * (-adw * arx + adx * arw - ady * arz + adz * ary);
        float ty = 2.0f * (-adw * ary + adx * arz + ady * arw - adz * arx);
        float tz = 2.0f * (-adw * arz - adx * ary + ady * arx + adz * arw);

        outX[i] = rx + tx;
        outY[i] = ry + ty;
        outZ[i] = rz + tz;
    }

    private static float signForReference(DualQuatSoA joints, float refX, float refY, float refZ, float refW, int j) {
        float rx = joints.rx[j], ry = joints.ry[j], rz = joints.rz[j], rw = joints.rw[j];
        float dot = refX * rx + refY * ry + refZ * rz + refW * rw;
        return dot < 0.0f ? -1.0f : 1.0f;
    }

    // ---- up-front checks ----

    private static void checkCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
    }

    private static void checkLength(float[] a, long n, String name) {
        if (a == null || a.length < n) {
            throw new IllegalArgumentException(name + " must hold " + n + " values");
        }
    }

    private static void checkIndices(int[] a, long n, int jointCount, String name) {
        if (a == null || a.length < n) {
            throw new IllegalArgumentException(name + " must hold " + n + " values");
        }
        for (int i = 0; i < n; i++) {
            int j = a[i];
            if (j < 0 || j >= jointCount) {
                throw new IllegalArgumentException(name + "[" + i + "] = " + j + " is outside [0, " + jointCount + ")");
            }
        }
    }

    private static void checkInterleaved(int[] jointIndices, float[] jointWeights, int jointCount, int count) {
        checkCount(count);
        checkIndices(jointIndices, 4L * count, jointCount, "jointIndices");
        checkLength(jointWeights, 4L * count, "jointWeights");
    }

    private static void checkPerSlot(int[] j0, int[] j1, int[] j2, int[] j3, float[] w0, float[] w1, float[] w2,
            float[] w3, int jointCount, int count) {
        checkCount(count);
        checkIndices(j0, count, jointCount, "j0");
        checkIndices(j1, count, jointCount, "j1");
        checkIndices(j2, count, jointCount, "j2");
        checkIndices(j3, count, jointCount, "j3");
        checkLength(w0, count, "w0");
        checkLength(w1, count, "w1");
        checkLength(w2, count, "w2");
        checkLength(w3, count, "w3");
    }

    private static void checkPositions(float[] inX, float[] inY, float[] inZ, float[] outX, float[] outY, float[] outZ,
            int count) {
        checkLength(inX, count, "inX");
        checkLength(inY, count, "inY");
        checkLength(inZ, count, "inZ");
        checkLength(outX, count, "outX");
        checkLength(outY, count, "outY");
        checkLength(outZ, count, "outZ");
    }

    private static void checkPalette(TransformSoA joints, float[] matrixPalette12, int jointCount) {
        if (joints == null) {
            throw new IllegalArgumentException("joints must not be null");
        }
        if (jointCount < 0 || jointCount > joints.size()) {
            throw new IllegalArgumentException("jointCount " + jointCount + " is outside [0, " + joints.size() + "]");
        }
        checkLength(matrixPalette12, 12L * jointCount, "matrixPalette12");
    }
}
