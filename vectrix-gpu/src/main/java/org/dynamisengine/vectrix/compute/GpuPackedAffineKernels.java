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
 * The GPU twin of Vectrix {@code affine.PackedAffineKernels}: batch kernels on packed 3×4 affine transforms.
 * <p>
 * Each kernel ports its Vectrix method's arithmetic in the same order, so on an IEEE device the results are the bits
 * {@code PackedAffineKernels} writes (see the package comment for subnormals); the upload kernel copies 32-bit
 * words, so it reproduces every bit pattern.
 * <p>
 * <b>Layouts.</b> All buffers hold 32-bit values; offsets and strides are in elements.
 * <ul>
 * <li>Packed transforms: 12 floats per entry in the {@code AFFINE_3X4_ROW_MAJOR} layout of
 * {@code PackedAffineArray}, {@code [r00 r01 r02 tx  r10 r11 r12 ty  r20 r21 r22 tz]}, entry {@code k} at
 * {@code off + 12 * k}: the bytes of {@code PackedAffineArray.raw()}.</li>
 * <li>Rotations: 4 floats per quaternion, {@code x, y, z, w}, rotation {@code i} at {@code off + 4 * i} (a
 * {@code vec4} array under std430).</li>
 * <li>Translations: three planes {@code tx, ty, tz}; component {@code c} of entry {@code i} at
 * {@code off + c * stride + i}.</li>
 * <li>Boxes: an {@code AABBSoA}-like buffer of six planes {@code minX, minY, minZ, maxX, maxY, maxZ}, coordinate
 * {@code f} of box {@code k} at {@code off + f * stride + k}.</li>
 * <li>{@code order}: 32-bit ints.</li>
 * </ul>
 * <p>
 * <b>{@code order} indices.</b> They live on the GPU, so they cannot be checked before recording, as the CPU methods
 * check them. An index outside the valid range is skipped (nothing is written for it), and the other elements are
 * processed. Where the CPU method would throw, the GPU result is therefore partial.
 * <p>
 * <b>Buffers.</b> An output must be a different buffer from every input, so transforming boxes in place, which the
 * CPU method allows, is not supported.
 * <p>
 * Methods taking arrays of objects ({@code trsToPackedAffineBatch} with {@code Transformf[]},
 * {@code matrix4fToPackedAffineBatch} with {@code Matrix4f[]}) have no GPU form. The {@code Chunked} variants give
 * the same results as the batch methods, so their GPU form is the batch method.
 * <p>
 * Three pipelines, compiled when the instance is created; one thread per transform or per element of {@code order}.
 *
 * @since 3.2.0
 */
public final class GpuPackedAffineKernels implements AutoCloseable {

    /** The parameter block of the rotation-translation kernel (checked against the compiled kernel). */
    static final String[] PARAMS_QUAT_TRANSLATION = {"count", "qOff", "tOff", "tStride", "outOff", "gridX"};

    /** The parameter block of the box kernel (checked against the compiled kernel). */
    static final String[] PARAMS_AABB = {"count", "entries", "tOff", "orderOff", "inOff", "inStride", "outOff",
        "outStride", "gridX"};

    /** The parameter block of the upload kernel (checked against the compiled kernel). */
    static final String[] PARAMS_UPLOAD = {"count", "transformCount", "tOff", "orderOff", "outOff", "gridX"};

    /** The number of floats per packed transform: 12. */
    public static final int STRIDE_FLOATS = 12;

    private final GpuDevice device;

    private final GpuComputePipeline quatTranslation;

    private final GpuComputePipeline aabb;

    private final GpuComputePipeline upload;

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create a pipeline
     */
    public GpuPackedAffineKernels(GpuDevice device) {
        Dimensions local = new Dimensions(EngineBuffers.GROUP, 1, 1);
        List<GpuComputePipeline> made = new ArrayList<>();
        try {
            made.add(Kernels.pipeline(device, "affine_quat_translation", local, PARAMS_QUAT_TRANSLATION,
                    Set.of("FP32"), Access.READ, Access.READ, Access.WRITE));
            made.add(Kernels.pipeline(device, "affine_aabb", local, PARAMS_AABB, Set.of("FP32", "INT32"),
                    Access.READ, Access.READ, Access.READ, Access.WRITE));
            made.add(Kernels.pipeline(device, "affine_upload", local, PARAMS_UPLOAD, Set.of("INT32"),
                    Access.READ, Access.READ, Access.WRITE));
        } catch (RuntimeException e) {
            made.forEach(GpuComputePipeline::close);
            throw e;
        }
        quatTranslation = made.get(0);
        aabb = made.get(1);
        upload = made.get(2);
        this.device = device;
    }

    /**
     * The compiled rotation-translation kernel ({@link #quatTranslationToPackedAffineBatch}), for applications that
     * manage pipelines themselves. Bindings: slot 0 the rotations, 1 the translations, 2 the output; the parameter
     * block holds the 32-bit fields {@code count, qOff, tOff, tStride, outOff, gridX} ({@code gridX} is the width of
     * the dispatch grid, which indexes groups as {@code group.y * gridX + group.x}).
     *
     * @return the kernel bundle
     */
    public static KernelBundle quatTranslationKernel() {
        return Kernels.bundle("affine_quat_translation");
    }

    /**
     * The compiled box kernel ({@link #transformAabbPackedAffineBatch}). Bindings: slot 0 the transforms, 1
     * {@code order}, 2 the input boxes, 3 the output boxes; the parameter block holds the 32-bit fields
     * {@code count, entries, tOff, orderOff, inOff, inStride, outOff, outStride, gridX}.
     *
     * @return the kernel bundle
     */
    public static KernelBundle aabbKernel() {
        return Kernels.bundle("affine_aabb");
    }

    /**
     * The compiled upload kernel ({@link #uploadPackedAffine}). Bindings: slot 0 the transforms, 1 {@code order}, 2
     * the output; the parameter block holds the 32-bit fields {@code count, transformCount, tOff, orderOff, outOff,
     * gridX}.
     *
     * @return the kernel bundle
     */
    public static KernelBundle uploadKernel() {
        return Kernels.bundle("affine_upload");
    }

    /**
     * Records {@code PackedAffineKernels.quatTranslationToPackedAffineBatch}: entry {@code i} of the output, for
     * {@code i} in {@code [0, count)}, becomes {@code T(tx[i], ty[i], tz[i]) * R(rotation[i])}. The quaternions are
     * not normalized; a non-unit quaternion gives a matrix that also scales. Nothing is recorded when {@code count}
     * is 0. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param rotation the rotations, 4 floats {@code x, y, z, w} per quaternion
     * @param rotationOff the element index of {@code x} of rotation 0
     * @param translation the translations, planes {@code tx, ty, tz}
     * @param translationOff the element index of {@code tx} of entry 0
     * @param translationStride the plane stride of {@code translation}, at least {@code count}
     * @param out receives 12 floats per entry
     * @param outOff the element index of the first float of entry 0
     * @param count the number of transforms, at least 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code count} is negative, a buffer is too small for its offset and stride
     *         (or needs element indices of 2^32 or more), {@code out} is one of the inputs, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder quatTranslationToPackedAffineBatch(GpuCommandEncoder encoder, GpuBuffer rotation,
            long rotationOff, GpuBuffer translation, long translationOff, int translationStride, GpuBuffer out,
            long outOff, int count) {
        Kernels.notNull(encoder, "encoder");
        EngineBuffers.count(count, "count");
        Kernels.range(rotation, rotationOff, 4L * count, "rotation");
        EngineBuffers.soa(translation, translationOff, translationStride, 3, count, "translation");
        Kernels.range(out, outOff, (long) STRIDE_FLOATS * count, "out");
        EngineBuffers.distinct(out, "out", rotation, translation);
        if (count == 0) {
            return encoder;
        }
        Dimensions groups = EngineBuffers.groups(device, count);
        byte[] params = new Kernels.Params(PARAMS_QUAT_TRANSLATION).u32(count).u32(rotationOff)
                .u32(translationOff).u32(translationStride).u32(outOff).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, quatTranslation, Map.of(0, rotation, 1, translation, 2, out), params, groups);
    }

    /**
     * Records {@code PackedAffineKernels.transformAabbPackedAffineBatch}: for each {@code i} in {@code [0, count)},
     * with {@code k = order[orderOff + i]}, box {@code k} of {@code boxes} transformed by transform {@code k} is
     * enclosed in an axis-aligned box written as box {@code k} of {@code outBoxes} (the center is transformed and
     * the half extents go through the absolute values of the 3×3 part). Entries not listed in {@code order}, and
     * indices outside {@code [0, entries)}, are left unchanged. Repeated indices write the same box twice. Nothing is
     * recorded when {@code count} is 0. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param entries the number of entries: {@code transforms}, {@code boxes} and {@code outBoxes} each hold this
     *        many, and valid indices in {@code order} are {@code [0, entries)} (the CPU method's limit, the smallest
     *        of its array sizes)
     * @param transforms the transforms, 12 floats per entry
     * @param transformsOff the element index of the first float of entry 0
     * @param order the entries to process, 32-bit ints; the first {@code count} from {@code orderOff} are used
     * @param orderOff the element index of the first index
     * @param boxes the input boxes, planes {@code minX, minY, minZ, maxX, maxY, maxZ}
     * @param boxesOff the element index of {@code minX} of entry 0
     * @param boxesStride the plane stride of {@code boxes}, at least {@code entries}
     * @param outBoxes receives the output boxes, laid out like {@code boxes}
     * @param outBoxesOff the element index of {@code minX} of entry 0
     * @param outBoxesStride the plane stride of {@code outBoxes}, at least {@code entries}
     * @param count the number of elements of {@code order} to process, at least 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code count} or {@code entries} is negative, {@code count} is positive
     *         while {@code entries} is 0, a buffer is too small for its
     *         offset and stride (or needs element indices of 2^32 or more), {@code outBoxes} is one of the inputs,
     *         or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder transformAabbPackedAffineBatch(GpuCommandEncoder encoder, int entries,
            GpuBuffer transforms, long transformsOff, GpuBuffer order, long orderOff, GpuBuffer boxes, long boxesOff,
            int boxesStride, GpuBuffer outBoxes, long outBoxesOff, int outBoxesStride, int count) {
        Kernels.notNull(encoder, "encoder");
        EngineBuffers.count(count, "count");
        EngineBuffers.count(entries, "entries");
        EngineBuffers.someValid(count, entries, "order");
        Kernels.range(transforms, transformsOff, (long) STRIDE_FLOATS * entries, "transforms");
        Kernels.range(order, orderOff, count, "order");
        EngineBuffers.soa(boxes, boxesOff, boxesStride, 6, entries, "boxes");
        EngineBuffers.soa(outBoxes, outBoxesOff, outBoxesStride, 6, entries, "outBoxes");
        EngineBuffers.distinct(outBoxes, "outBoxes", transforms, order, boxes);
        if (count == 0) {
            return encoder;
        }
        Dimensions groups = EngineBuffers.groups(device, count);
        byte[] params = new Kernels.Params(PARAMS_AABB).u32(count).u32(entries).u32(transformsOff)
                .u32(orderOff).u32(boxesOff).u32(boxesStride).u32(outBoxesOff).u32(outBoxesStride).u32(groups.x())
                .bytes();
        return Kernels.dispatch(encoder, aabb, Map.of(0, transforms, 1, order, 2, boxes, 3, outBoxes), params, groups);
    }

    /**
     * Records {@code PackedAffineKernels.uploadPackedAffine}: for each {@code i} in {@code [0, count)}, the 12 floats
     * of transform {@code order[orderOff + i]} are copied to {@code outUpload[outOff + 12 * i ..]}, bit for bit. The
     * output is packed by position in {@code order}. An index outside {@code [0, transformCount)} is skipped,
     * leaving its 12 floats unchanged. Nothing is recorded when {@code count} is 0. All arguments are checked before
     * anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param transforms the transforms, 12 floats per entry
     * @param transformsOff the element index of the first float of entry 0
     * @param transformCount the number of transforms; valid indices in {@code order} are {@code [0,
     *        transformCount)}
     * @param order the entries to copy, in output order, 32-bit ints
     * @param orderOff the element index of the first index
     * @param outUpload receives {@code 12 * count} floats from {@code outOff}
     * @param outOff the element index of the first float written
     * @param count the number of transforms to copy, at least 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code count} or {@code transformCount} is negative, {@code count} is
     *         positive while {@code transformCount} is 0, a buffer is too small for its offset (or needs element indices of 2^32 or more), {@code outUpload} is one of the inputs, or
     *         an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder uploadPackedAffine(GpuCommandEncoder encoder, GpuBuffer transforms, long transformsOff,
            int transformCount, GpuBuffer order, long orderOff, GpuBuffer outUpload, long outOff, int count) {
        Kernels.notNull(encoder, "encoder");
        EngineBuffers.count(count, "count");
        EngineBuffers.count(transformCount, "transformCount");
        EngineBuffers.someValid(count, transformCount, "order");
        Kernels.range(transforms, transformsOff, (long) STRIDE_FLOATS * transformCount, "transforms");
        Kernels.range(order, orderOff, count, "order");
        Kernels.range(outUpload, outOff, (long) STRIDE_FLOATS * count, "outUpload");
        EngineBuffers.distinct(outUpload, "outUpload", transforms, order);
        if (count == 0) {
            return encoder;
        }
        Dimensions groups = EngineBuffers.groups(device, count);
        byte[] params = new Kernels.Params(PARAMS_UPLOAD).u32(count).u32(transformCount).u32(transformsOff)
                .u32(orderOff).u32(outOff).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, upload, Map.of(0, transforms, 1, order, 2, outUpload), params, groups);
    }

    /** Releases the pipelines; work already recorded keeps them alive until it completes. Idempotent. */
    @Override
    public void close() {
        quatTranslation.close();
        aabb.close();
        upload.close();
    }
}
