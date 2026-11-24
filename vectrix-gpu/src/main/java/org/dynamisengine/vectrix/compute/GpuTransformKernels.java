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
 * The GPU twin of Vectrix {@code soa.TransformKernels}: batch composition of translation-rotation-scale transforms.
 * <p>
 * Each output is computed with {@code composeBatch}'s arithmetic, in the same order, so on an IEEE device the
 * results are the bits {@code TransformKernels.composeBatch} writes (see the package comment for subnormals).
 * <p>
 * <b>Layout.</b> A {@code TransformSoA} is one buffer of ten planes in the order of its fields:
 * {@code tx, ty, tz, qx, qy, qz, qw, sx, sy, sz}; field {@code f} of transform {@code i} is at element
 * {@code off + f * stride + i}. The stride is at least the number of transforms used; a buffer holding
 * {@code TransformSoA} arrays of length {@code n} back to back has stride {@code n}.
 * <p>
 * One pipeline, compiled when the instance is created; one thread per transform.
 *
 * @since 3.2.0
 */
public final class GpuTransformKernels implements AutoCloseable {

    /** The parameter block, in the order the kernel declares it (checked against the compiled kernel). */
    static final String[] PARAMS = {"count", "parentsOff", "parentsStride", "localsOff", "localsStride", "outOff",
        "outStride", "gridX"};

    /** The number of planes of a {@code TransformSoA} buffer. */
    public static final int TRANSFORM_FIELDS = 10;

    private final GpuDevice device;

    private final GpuComputePipeline pipeline;

    /**
     * Compiles the kernel on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipeline
     */
    public GpuTransformKernels(GpuDevice device) {
        pipeline = Kernels.pipeline(device, "transform_compose", new Dimensions(EngineBuffers.GROUP, 1, 1),
                PARAMS.length, Set.of("FP32"), Access.READ, Access.READ, Access.WRITE);
        this.device = device;
    }

    /**
     * The compiled kernel, for applications that manage pipelines themselves. Bindings: slot 0 the parents, 1 the
     * locals, 2 the output; the parameter block holds {@code count, parentsOff, parentsStride, localsOff,
     * localsStride, outOff, outStride, gridX} as 32-bit values ({@code gridX} is the width of the dispatch grid,
     * which indexes groups as {@code group.y * gridX + group.x}).
     *
     * @return the kernel bundle
     */
    public static KernelBundle kernel() {
        return Kernels.bundle("transform_compose");
    }

    /**
     * Records {@code out[i] = parents[i] * locals[i]} for {@code i} in {@code [0, count)}, as
     * {@code TransformKernels.composeBatch} computes it: {@code t = tP + qP·(sP ∘ tL)}, {@code q = qP·qL},
     * {@code s = sP ∘ sL}. The rotations should be unit quaternions; they are neither checked nor renormalized, so
     * the results are those of FAST mode (STRICT mode gives the same bits, after checking the rotations on the
     * CPU). {@code parents} and {@code locals} may be the same buffer; {@code out} must be a different buffer from
     * both (the CPU method also allows composing in place). Nothing is recorded when {@code count} is 0. All
     * arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param parents the parent transforms, a {@code TransformSoA} buffer
     * @param parentsOff the element index of {@code tx} of parent 0
     * @param parentsStride the number of elements from one plane of {@code parents} to the next, at least
     *        {@code count}
     * @param locals the local transforms, a {@code TransformSoA} buffer
     * @param localsOff the element index of {@code tx} of local 0
     * @param localsStride the plane stride of {@code locals}, at least {@code count}
     * @param out receives the composed transforms, a {@code TransformSoA} buffer
     * @param outOff the element index of {@code tx} of output 0
     * @param outStride the plane stride of {@code out}, at least {@code count}
     * @param count the number of transforms, at least 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code count} is negative, a buffer is too small for its offset and stride
     *         (or needs element indices of 2^32 or more), {@code out} is {@code parents} or {@code locals}, or an
     *         argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder composeBatch(GpuCommandEncoder encoder, GpuBuffer parents, long parentsOff,
            int parentsStride, GpuBuffer locals, long localsOff, int localsStride, GpuBuffer out, long outOff,
            int outStride, int count) {
        Kernels.notNull(encoder, "encoder");
        EngineBuffers.count(count, "count");
        EngineBuffers.soa(parents, parentsOff, parentsStride, TRANSFORM_FIELDS, count, "parents");
        EngineBuffers.soa(locals, localsOff, localsStride, TRANSFORM_FIELDS, count, "locals");
        EngineBuffers.soa(out, outOff, outStride, TRANSFORM_FIELDS, count, "out");
        EngineBuffers.distinct(out, "out", parents, locals);
        if (count == 0) {
            return encoder;
        }
        Dimensions groups = EngineBuffers.groups(device, count);
        byte[] params = new Kernels.Params(PARAMS.length).u32(count).u32(parentsOff).u32(parentsStride)
                .u32(localsOff).u32(localsStride).u32(outOff).u32(outStride).u32(groups.x()).bytes();
        return encoder.dispatch(pipeline, Map.of(0, parents, 1, locals, 2, out), params, groups);
    }

    /** Releases the pipeline; work already recorded keeps it alive until it completes. Idempotent. */
    @Override
    public void close() {
        pipeline.close();
    }
}
