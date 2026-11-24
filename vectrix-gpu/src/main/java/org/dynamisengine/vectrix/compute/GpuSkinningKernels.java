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
package org.dynamisengine.vectrix.compute;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dynamisengine.gpu.api.compute.Access;
import org.dynamisengine.gpu.api.compute.Dimensions;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuComputePipeline;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.KernelBundle;

/**
 * The GPU twin of Vectrix {@code soa.SkinningKernels}: batch skinning with four influences per vertex, and the
 * matrix palettes for it.
 * <p>
 * Every kernel ports its Vectrix method's arithmetic in the same order, including the correctly rounded
 * {@code Math.invsqrt} that dual quaternion skinning normalizes with (computed without FP64), so on an IEEE device
 * the results are the bits {@code SkinningKernels} writes (see the package comment for subnormals). The weights are
 * used as given, not normalized.
 * <p>
 * <b>Layouts.</b> All buffers hold 32-bit values; offsets and strides are in elements.
 * <ul>
 * <li>Joints as a {@code TransformSoA}: ten planes {@code tx, ty, tz, qx, qy, qz, qw, sx, sy, sz}; field {@code f}
 * of joint {@code j} at {@code jointsOff + f * jointStride + j}.</li>
 * <li>Joints as a {@code DualQuatSoA}: eight planes {@code rx, ry, rz, rw, dx, dy, dz, dw}, addressed the same
 * way.</li>
 * <li>A matrix palette: 12 floats per joint from {@code paletteOff}, row-major
 * {@code [r00 r01 r02 tx  r10 r11 r12 ty  r20 r21 r22 tz]}, the {@code AFFINE_3X4_ROW_MAJOR} layout of
 * {@code PackedAffineArray}.</li>
 * <li>Positions: three planes {@code x, y, z}; coordinate {@code c} of vertex {@code i} at
 * {@code off + c * stride + i}.</li>
 * <li>Influences, interleaved: joint index {@code k} of vertex {@code i} at {@code indicesOff + 4 * i + k} (32-bit
 * ints), weight {@code k} at {@code weightsOff + 4 * i + k}. Per slot (the {@code SoA} methods): four planes,
 * index {@code k} of vertex {@code i} at {@code indicesOff + k * indicesStride + i}, and likewise the weights.</li>
 * </ul>
 * <p>
 * <b>Joint indices.</b> They live on the GPU, so they cannot be checked before recording, as the CPU methods check
 * them. A vertex with a joint index outside {@code [0, jointCount)} is skipped: its output is left unchanged, and
 * the other vertices are written. Where the CPU method would throw, the GPU result is therefore partial.
 * <p>
 * <b>Buffers.</b> An output must be a different buffer from every input, so skinning in place, which the CPU methods
 * allow, is not supported. Inputs may share buffers.
 * <p>
 * Four pipelines, compiled when the instance is created; one thread per vertex, or per joint for the palettes.
 *
 * @since 3.2.0
 */
public final class GpuSkinningKernels implements AutoCloseable {

    /** The parameter block of the linear blend and dual quaternion kernels (checked against the compiled kernels). */
    static final String[] PARAMS = {"count", "jointCount", "jointsOff", "jointStride", "idxOff", "idxSlot", "idxStep",
        "wOff", "wSlot", "wStep", "inOff", "inStride", "outOff", "outStride", "gridX"};

    /** The parameter block of the matrix palette skinning kernel (checked against the compiled kernel). */
    static final String[] PARAMS_PALETTE_SKIN = {"count", "jointCount", "palOff", "idxOff", "idxSlot", "idxStep",
        "wOff", "wSlot", "wStep", "inOff", "inStride", "outOff", "outStride", "gridX"};

    /** The parameter block of the palette building kernel (checked against the compiled kernel). */
    static final String[] PARAMS_PALETTE_BUILD = {"jointCount", "jointsOff", "jointStride", "palOff", "scaled",
        "gridX"};

    /** The number of planes of a {@code TransformSoA} joint buffer. */
    public static final int TRANSFORM_FIELDS = 10;

    /** The number of planes of a {@code DualQuatSoA} joint buffer. */
    public static final int DUAL_QUAT_FIELDS = 8;

    /** The number of floats per joint of a matrix palette. */
    public static final int PALETTE_FLOATS = 12;

    private static final int INFLUENCES = 4;

    private final GpuDevice device;

    private final GpuComputePipeline lbs;

    private final GpuComputePipeline dualQuat;

    private final GpuComputePipeline paletteSkin;

    private final GpuComputePipeline paletteBuild;

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create a pipeline
     */
    public GpuSkinningKernels(GpuDevice device) {
        Dimensions local = new Dimensions(EngineBuffers.GROUP, 1, 1);
        Set<String> types = Set.of("FP32", "INT32");
        List<GpuComputePipeline> made = new ArrayList<>();
        try {
            made.add(Kernels.pipeline(device, "skin_lbs", local, PARAMS.length, types, Access.READ, Access.READ,
                    Access.READ, Access.READ, Access.WRITE));
            made.add(Kernels.pipeline(device, "skin_dq", local, PARAMS.length, types, Access.READ, Access.READ,
                    Access.READ, Access.READ, Access.WRITE));
            made.add(Kernels.pipeline(device, "skin_palette", local, PARAMS_PALETTE_SKIN.length, types, Access.READ,
                    Access.READ, Access.READ, Access.READ, Access.WRITE));
            made.add(Kernels.pipeline(device, "palette_build", local, PARAMS_PALETTE_BUILD.length, Set.of("FP32"),
                    Access.READ, Access.WRITE));
        } catch (RuntimeException e) {
            made.forEach(GpuComputePipeline::close);
            throw e;
        }
        lbs = made.get(0);
        dualQuat = made.get(1);
        paletteSkin = made.get(2);
        paletteBuild = made.get(3);
        this.device = device;
    }

    /**
     * The compiled linear blend skinning kernel ({@link #skinLbs4} and {@link #skinLbs4SoA}), for applications that
     * manage pipelines themselves. Bindings: slot 0 the joints, 1 the joint indices, 2 the weights, 3 the input
     * positions, 4 the output positions; the parameter block holds the 32-bit fields {@code count, jointCount,
     * jointsOff, jointStride, idxOff, idxSlot, idxStep, wOff, wSlot, wStep, inOff, inStride, outOff, outStride,
     * gridX}, where influence {@code k} of vertex {@code i} is at {@code idxOff + k * idxSlot + i * idxStep} (and
     * likewise for the weights) and {@code gridX} is the width of the dispatch grid, which indexes groups as
     * {@code group.y * gridX + group.x}.
     *
     * @return the kernel bundle
     */
    public static KernelBundle lbsKernel() {
        return Kernels.bundle("skin_lbs");
    }

    /**
     * The compiled dual quaternion skinning kernel ({@link #skinDualQuat4} and {@link #skinDualQuat4SoA}), with the
     * bindings and parameter block of {@link #lbsKernel()}.
     *
     * @return the kernel bundle
     */
    public static KernelBundle dualQuatKernel() {
        return Kernels.bundle("skin_dq");
    }

    /**
     * The compiled matrix palette skinning kernel ({@link #skinLbs4MatrixPalette}). Bindings: slot 0 the palette, 1
     * the joint indices, 2 the weights, 3 the input positions, 4 the output positions; the parameter block holds the
     * 32-bit fields {@code count, jointCount, palOff, idxOff, idxSlot, idxStep, wOff, wSlot, wStep, inOff, inStride,
     * outOff, outStride, gridX}, as in {@link #lbsKernel()}.
     *
     * @return the kernel bundle
     */
    public static KernelBundle paletteSkinKernel() {
        return Kernels.bundle("skin_palette");
    }

    /**
     * The compiled palette building kernel ({@link #buildMatrixPalette12} and {@link #buildRigidMatrixPalette12}).
     * Bindings: slot 0 the joints, 1 the palette; the parameter block holds the 32-bit fields {@code jointCount,
     * jointsOff, jointStride, palOff, scaled, gridX}, with {@code scaled} 1 for {@code buildMatrixPalette12} and 0
     * for the rigid palette.
     *
     * @return the kernel bundle
     */
    public static KernelBundle paletteBuildKernel() {
        return Kernels.bundle("palette_build");
    }

    /**
     * Records {@code SkinningKernels.skinLbs4}: linear blend skinning from {@code TransformSoA} joints with four
     * interleaved influences per vertex, {@code out = Σ w_k·(q_k·(s_k ∘ p)·q_k⁻¹ + t_k)}, for vertices
     * {@code [0, count)}. The rotations must be unit quaternions. Nothing is recorded when {@code count} is 0.
     *
     * @param encoder the encoder to record into
     * @param joints the skinning transforms, a {@code TransformSoA} buffer
     * @param jointsOff the element index of {@code tx} of joint 0
     * @param jointStride the number of elements from one plane of {@code joints} to the next, at least
     *        {@code jointCount}
     * @param jointCount the number of joints; valid joint indices are {@code [0, jointCount)}
     * @param jointIndices four 32-bit int joint indices per vertex, interleaved
     * @param indicesOff the element index of the first joint index of vertex 0
     * @param jointWeights four weights per vertex, interleaved like the indices
     * @param weightsOff the element index of the first weight of vertex 0
     * @param in the bind-pose positions, planes {@code x, y, z}
     * @param inOff the element index of {@code x} of vertex 0
     * @param inStride the plane stride of {@code in}, at least {@code count}
     * @param out receives the skinned positions, planes {@code x, y, z}
     * @param outOff the element index of {@code x} of vertex 0
     * @param outStride the plane stride of {@code out}, at least {@code count}
     * @param count the number of vertices, at least 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if a count is negative, {@code count} is positive while {@code jointCount}
     *         is 0, a buffer is too small for its offset and stride (or needs element indices of 2^32 or more),
     *         {@code out} is one of the inputs, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder skinLbs4(GpuCommandEncoder encoder, GpuBuffer joints, long jointsOff, int jointStride,
            int jointCount, GpuBuffer jointIndices, long indicesOff, GpuBuffer jointWeights, long weightsOff,
            GpuBuffer in, long inOff, int inStride, GpuBuffer out, long outOff, int outStride, int count) {
        return skin(lbs, TRANSFORM_FIELDS, encoder, joints, jointsOff, jointStride, jointCount, jointIndices,
                indicesOff, 0, jointWeights, weightsOff, 0, true, in, inOff, inStride, out, outOff, outStride,
                count);
    }

    /**
     * Records {@code SkinningKernels.skinLbs4SoA}: {@link #skinLbs4} with one plane per influence slot, giving the
     * same bits for the same influences. Nothing is recorded when {@code count} is 0.
     *
     * @param encoder the encoder to record into
     * @param joints the skinning transforms, a {@code TransformSoA} buffer
     * @param jointsOff the element index of {@code tx} of joint 0
     * @param jointStride the plane stride of {@code joints}, at least {@code jointCount}
     * @param jointCount the number of joints; valid joint indices are {@code [0, jointCount)}
     * @param jointIndices four planes {@code j0, j1, j2, j3} of 32-bit int joint indices
     * @param indicesOff the element index of {@code j0} of vertex 0
     * @param indicesStride the plane stride of {@code jointIndices}, at least {@code count}
     * @param jointWeights four planes {@code w0, w1, w2, w3} of weights
     * @param weightsOff the element index of {@code w0} of vertex 0
     * @param weightsStride the plane stride of {@code jointWeights}, at least {@code count}
     * @param in the bind-pose positions, planes {@code x, y, z}
     * @param inOff the element index of {@code x} of vertex 0
     * @param inStride the plane stride of {@code in}, at least {@code count}
     * @param out receives the skinned positions, planes {@code x, y, z}
     * @param outOff the element index of {@code x} of vertex 0
     * @param outStride the plane stride of {@code out}, at least {@code count}
     * @param count the number of vertices, at least 0
     * @return {@code encoder}
     * @throws IllegalArgumentException as {@link #skinLbs4} does
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder skinLbs4SoA(GpuCommandEncoder encoder, GpuBuffer joints, long jointsOff,
            int jointStride, int jointCount, GpuBuffer jointIndices, long indicesOff, int indicesStride,
            GpuBuffer jointWeights, long weightsOff, int weightsStride, GpuBuffer in, long inOff, int inStride,
            GpuBuffer out, long outOff, int outStride, int count) {
        return skin(lbs, TRANSFORM_FIELDS, encoder, joints, jointsOff, jointStride, jointCount, jointIndices,
                indicesOff, indicesStride, jointWeights, weightsOff, weightsStride, false, in, inOff, inStride, out,
                outOff, outStride, count);
    }

    /**
     * Records {@code SkinningKernels.skinDualQuat4}: dual quaternion linear blending with four interleaved
     * influences per vertex. Each joint's dual quaternion is weighted, with its sign flipped when its rotation lies
     * in the other hemisphere from the first influence's, the sum is divided by the length of its real part, and the
     * rigid transform is applied to the position; a vertex whose blended real part is exactly zero is copied
     * unchanged. The joints must be unit dual quaternions. Nothing is recorded when {@code count} is 0.
     *
     * @param encoder the encoder to record into
     * @param joints the skinning transforms, a {@code DualQuatSoA} buffer
     * @param jointsOff the element index of {@code rx} of joint 0
     * @param jointStride the plane stride of {@code joints}, at least {@code jointCount}
     * @param jointCount the number of joints; valid joint indices are {@code [0, jointCount)}
     * @param jointIndices four 32-bit int joint indices per vertex, interleaved
     * @param indicesOff the element index of the first joint index of vertex 0
     * @param jointWeights four weights per vertex, interleaved like the indices
     * @param weightsOff the element index of the first weight of vertex 0
     * @param in the bind-pose positions, planes {@code x, y, z}
     * @param inOff the element index of {@code x} of vertex 0
     * @param inStride the plane stride of {@code in}, at least {@code count}
     * @param out receives the skinned positions, planes {@code x, y, z}
     * @param outOff the element index of {@code x} of vertex 0
     * @param outStride the plane stride of {@code out}, at least {@code count}
     * @param count the number of vertices, at least 0
     * @return {@code encoder}
     * @throws IllegalArgumentException as {@link #skinLbs4} does
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder skinDualQuat4(GpuCommandEncoder encoder, GpuBuffer joints, long jointsOff,
            int jointStride, int jointCount, GpuBuffer jointIndices, long indicesOff, GpuBuffer jointWeights,
            long weightsOff, GpuBuffer in, long inOff, int inStride, GpuBuffer out, long outOff, int outStride,
            int count) {
        return skin(dualQuat, DUAL_QUAT_FIELDS, encoder, joints, jointsOff, jointStride, jointCount, jointIndices,
                indicesOff, 0, jointWeights, weightsOff, 0, true, in, inOff, inStride, out, outOff, outStride,
                count);
    }

    /**
     * Records {@code SkinningKernels.skinDualQuat4SoA}: {@link #skinDualQuat4} with one plane per influence slot,
     * giving the same bits for the same influences. Nothing is recorded when {@code count} is 0.
     *
     * @param encoder the encoder to record into
     * @param joints the skinning transforms, a {@code DualQuatSoA} buffer
     * @param jointsOff the element index of {@code rx} of joint 0
     * @param jointStride the plane stride of {@code joints}, at least {@code jointCount}
     * @param jointCount the number of joints; valid joint indices are {@code [0, jointCount)}
     * @param jointIndices four planes {@code j0, j1, j2, j3} of 32-bit int joint indices
     * @param indicesOff the element index of {@code j0} of vertex 0
     * @param indicesStride the plane stride of {@code jointIndices}, at least {@code count}
     * @param jointWeights four planes {@code w0, w1, w2, w3} of weights
     * @param weightsOff the element index of {@code w0} of vertex 0
     * @param weightsStride the plane stride of {@code jointWeights}, at least {@code count}
     * @param in the bind-pose positions, planes {@code x, y, z}
     * @param inOff the element index of {@code x} of vertex 0
     * @param inStride the plane stride of {@code in}, at least {@code count}
     * @param out receives the skinned positions, planes {@code x, y, z}
     * @param outOff the element index of {@code x} of vertex 0
     * @param outStride the plane stride of {@code out}, at least {@code count}
     * @param count the number of vertices, at least 0
     * @return {@code encoder}
     * @throws IllegalArgumentException as {@link #skinLbs4} does
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder skinDualQuat4SoA(GpuCommandEncoder encoder, GpuBuffer joints, long jointsOff,
            int jointStride, int jointCount, GpuBuffer jointIndices, long indicesOff, int indicesStride,
            GpuBuffer jointWeights, long weightsOff, int weightsStride, GpuBuffer in, long inOff, int inStride,
            GpuBuffer out, long outOff, int outStride, int count) {
        return skin(dualQuat, DUAL_QUAT_FIELDS, encoder, joints, jointsOff, jointStride, jointCount, jointIndices,
                indicesOff, indicesStride, jointWeights, weightsOff, weightsStride, false, in, inOff, inStride, out,
                outOff, outStride, count);
    }

    /**
     * Records {@code SkinningKernels.skinLbs4MatrixPalette}: linear blend skinning from a matrix palette with four
     * interleaved influences per vertex, {@code out = Σ w_k·(M_k·p)}, for vertices {@code [0, count)}. The 3×3 part
     * is used as given, so joints may carry scale or shear. Nothing is recorded when {@code count} is 0.
     *
     * @param encoder the encoder to record into
     * @param palette the palette, 12 floats per joint in {@code AFFINE_3X4_ROW_MAJOR} order
     * @param paletteOff the element index of the first float of joint 0
     * @param jointCount the number of joints in the palette; valid joint indices are {@code [0, jointCount)}
     * @param jointIndices four 32-bit int joint indices per vertex, interleaved
     * @param indicesOff the element index of the first joint index of vertex 0
     * @param jointWeights four weights per vertex, interleaved like the indices
     * @param weightsOff the element index of the first weight of vertex 0
     * @param in the bind-pose positions, planes {@code x, y, z}
     * @param inOff the element index of {@code x} of vertex 0
     * @param inStride the plane stride of {@code in}, at least {@code count}
     * @param out receives the skinned positions, planes {@code x, y, z}
     * @param outOff the element index of {@code x} of vertex 0
     * @param outStride the plane stride of {@code out}, at least {@code count}
     * @param count the number of vertices, at least 0
     * @return {@code encoder}
     * @throws IllegalArgumentException as {@link #skinLbs4} does
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder skinLbs4MatrixPalette(GpuCommandEncoder encoder, GpuBuffer palette, long paletteOff,
            int jointCount, GpuBuffer jointIndices, long indicesOff, GpuBuffer jointWeights, long weightsOff,
            GpuBuffer in, long inOff, int inStride, GpuBuffer out, long outOff, int outStride, int count) {
        Kernels.notNull(encoder, "encoder");
        EngineBuffers.count(jointCount, "jointCount");
        Kernels.range(palette, paletteOff, (long) PALETTE_FLOATS * jointCount, "palette");
        checkVertices(jointCount, jointIndices, indicesOff, 0, jointWeights, weightsOff, 0, true, in, inOff, inStride,
                out, outOff, outStride, count);
        EngineBuffers.distinct(out, "out", palette, jointIndices, jointWeights, in);
        if (count == 0) {
            return encoder;
        }
        Dimensions groups = EngineBuffers.groups(device, count);
        byte[] params = new Kernels.Params(PARAMS_PALETTE_SKIN.length).u32(count).u32(jointCount).u32(paletteOff)
                .u32(indicesOff).u32(1).u32(INFLUENCES).u32(weightsOff).u32(1).u32(INFLUENCES).u32(inOff)
                .u32(inStride).u32(outOff).u32(outStride).u32(groups.x()).bytes();
        return encoder.dispatch(paletteSkin, Map.of(0, palette, 1, jointIndices, 2, jointWeights, 3, in, 4, out),
                params, groups);
    }

    /**
     * Records {@code SkinningKernels.buildMatrixPalette12}: joint {@code j} of {@code joints}, for {@code j} in
     * {@code [0, jointCount)}, becomes the 12 floats of its {@code T·R·S} at {@code paletteOff + 12 * j}. The
     * quaternions are not normalized, so they must be unit. Nothing is recorded when {@code jointCount} is 0.
     *
     * @param encoder the encoder to record into
     * @param joints the joint transforms, a {@code TransformSoA} buffer
     * @param jointsOff the element index of {@code tx} of joint 0
     * @param jointStride the plane stride of {@code joints}, at least {@code jointCount}
     * @param palette receives 12 floats per joint
     * @param paletteOff the element index of the first float of joint 0
     * @param jointCount the number of joints, at least 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code jointCount} is negative, a buffer is too small for its offset and
     *         stride (or needs element indices of 2^32 or more), {@code palette} is {@code joints}, or an argument
     *         is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder buildMatrixPalette12(GpuCommandEncoder encoder, GpuBuffer joints, long jointsOff,
            int jointStride, GpuBuffer palette, long paletteOff, int jointCount) {
        return buildPalette(encoder, joints, jointsOff, jointStride, palette, paletteOff, jointCount, true);
    }

    /**
     * Records {@code SkinningKernels.buildRigidMatrixPalette12}: as {@link #buildMatrixPalette12}, with the 3×3
     * part the rotation matrix of each joint's quaternion and the scale planes ignored.
     *
     * @param encoder the encoder to record into
     * @param joints the joint transforms, a {@code TransformSoA} buffer
     * @param jointsOff the element index of {@code tx} of joint 0
     * @param jointStride the plane stride of {@code joints}, at least {@code jointCount}
     * @param palette receives 12 floats per joint
     * @param paletteOff the element index of the first float of joint 0
     * @param jointCount the number of joints, at least 0
     * @return {@code encoder}
     * @throws IllegalArgumentException as {@link #buildMatrixPalette12} does
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder buildRigidMatrixPalette12(GpuCommandEncoder encoder, GpuBuffer joints, long jointsOff,
            int jointStride, GpuBuffer palette, long paletteOff, int jointCount) {
        return buildPalette(encoder, joints, jointsOff, jointStride, palette, paletteOff, jointCount, false);
    }

    private GpuCommandEncoder buildPalette(GpuCommandEncoder encoder, GpuBuffer joints, long jointsOff,
            int jointStride, GpuBuffer palette, long paletteOff, int jointCount, boolean scaled) {
        Kernels.notNull(encoder, "encoder");
        EngineBuffers.count(jointCount, "jointCount");
        EngineBuffers.soa(joints, jointsOff, jointStride, TRANSFORM_FIELDS, jointCount, "joints");
        Kernels.range(palette, paletteOff, (long) PALETTE_FLOATS * jointCount, "palette");
        EngineBuffers.distinct(palette, "palette", joints);
        if (jointCount == 0) {
            return encoder;
        }
        Dimensions groups = EngineBuffers.groups(device, jointCount);
        byte[] params = new Kernels.Params(PARAMS_PALETTE_BUILD.length).u32(jointCount).u32(jointsOff)
                .u32(jointStride).u32(paletteOff).u32(scaled ? 1 : 0).u32(groups.x()).bytes();
        return encoder.dispatch(paletteBuild, Map.of(0, joints, 1, palette), params, groups);
    }

    /** Skinning from SoA joints, with interleaved influences or one plane per slot (then with the strides). */
    private GpuCommandEncoder skin(GpuComputePipeline pipeline, int fields, GpuCommandEncoder encoder,
            GpuBuffer joints, long jointsOff, int jointStride, int jointCount, GpuBuffer jointIndices,
            long indicesOff, int indicesStride, GpuBuffer jointWeights, long weightsOff, int weightsStride,
            boolean interleaved, GpuBuffer in, long inOff, int inStride, GpuBuffer out, long outOff, int outStride,
            int count) {
        Kernels.notNull(encoder, "encoder");
        EngineBuffers.count(jointCount, "jointCount");
        EngineBuffers.soa(joints, jointsOff, jointStride, fields, jointCount, "joints");
        checkVertices(jointCount, jointIndices, indicesOff, indicesStride, jointWeights, weightsOff, weightsStride,
                interleaved, in, inOff, inStride, out, outOff, outStride, count);
        EngineBuffers.distinct(out, "out", joints, jointIndices, jointWeights, in);
        if (count == 0) {
            return encoder;
        }
        Dimensions groups = EngineBuffers.groups(device, count);
        byte[] params = new Kernels.Params(PARAMS.length).u32(count).u32(jointCount).u32(jointsOff).u32(jointStride)
                .u32(indicesOff).u32(interleaved ? 1 : indicesStride).u32(interleaved ? INFLUENCES : 1)
                .u32(weightsOff).u32(interleaved ? 1 : weightsStride).u32(interleaved ? INFLUENCES : 1)
                .u32(inOff).u32(inStride).u32(outOff).u32(outStride).u32(groups.x()).bytes();
        return encoder.dispatch(pipeline, Map.of(0, joints, 1, jointIndices, 2, jointWeights, 3, in, 4, out), params,
                groups);
    }

    /** The checks of the influences (interleaved, or one plane per slot) and positions. */
    private static void checkVertices(int jointCount, GpuBuffer jointIndices, long indicesOff, int indicesStride,
            GpuBuffer jointWeights, long weightsOff, int weightsStride, boolean interleaved, GpuBuffer in, long inOff,
            int inStride, GpuBuffer out, long outOff, int outStride, int count) {
        EngineBuffers.count(count, "count");
        EngineBuffers.someValid(count, jointCount, "jointIndices");
        if (interleaved) {
            Kernels.range(jointIndices, indicesOff, (long) INFLUENCES * count, "jointIndices");
            Kernels.range(jointWeights, weightsOff, (long) INFLUENCES * count, "jointWeights");
        } else {
            EngineBuffers.soa(jointIndices, indicesOff, indicesStride, INFLUENCES, count, "jointIndices");
            EngineBuffers.soa(jointWeights, weightsOff, weightsStride, INFLUENCES, count, "jointWeights");
        }
        EngineBuffers.soa(in, inOff, inStride, 3, count, "in");
        EngineBuffers.soa(out, outOff, outStride, 3, count, "out");
    }

    /** Releases the pipelines; work already recorded keeps them alive until it completes. Idempotent. */
    @Override
    public void close() {
        lbs.close();
        dualQuat.close();
        paletteSkin.close();
        paletteBuild.close();
    }
}
