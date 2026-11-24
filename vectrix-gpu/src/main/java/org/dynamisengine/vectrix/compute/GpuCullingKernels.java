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
 * The GPU twin of Vectrix {@code geometry.CullingKernels}: batch frustum culling of axis-aligned boxes.
 * <p>
 * Each box gets the classification {@code CullingKernels.classifyAabb} gives it, with the same arithmetic, so on an
 * IEEE device the results equal {@code frustumCullAabbBatch}'s, NaN and infinite bounds included: a box is
 * {@link #OUTSIDE} only when it lies entirely behind one plane, and a box with a NaN bound is never
 * {@code OUTSIDE}.
 * <p>
 * <b>Layouts.</b> The planes are 24 floats in the order of {@code FrustumPlanes.getVec4Array}: {@code x, y, z, w}
 * of {@code PLANE_NX}, {@code PLANE_PX}, {@code PLANE_NY}, {@code PLANE_PY}, {@code PLANE_NZ}, {@code PLANE_PZ}.
 * They travel in the parameter block, so a new view needs no buffer upload. The boxes are one buffer laid out like
 * an {@code AABBSoA}: six planes {@code minX, minY, minZ, maxX, maxY, maxZ}, coordinate {@code f} of box {@code i}
 * at element {@code boundsOff + f * boundsStride + i}. The results are 32-bit ints, box {@code i} at
 * {@code resultOff + i}.
 * <p>
 * One pipeline, compiled when the instance is created; one thread per box.
 *
 * @since 3.2.0
 */
public final class GpuCullingKernels implements AutoCloseable {

    /** The parameter block, in the order the kernel declares it (checked against the compiled kernel). */
    static final String[] PARAMS = {"x0", "y0", "z0", "w0", "x1", "y1", "z1", "w1", "x2", "y2", "z2", "w2", "x3",
        "y3", "z3", "w3", "x4", "y4", "z4", "w4", "x5", "y5", "z5", "w5", "count", "boundsOff", "boundsStride",
        "resultOff", "gridX"};

    /** The number of floats of the planes: six planes of {@code (x, y, z, w)}. */
    public static final int PLANE_FLOATS = 24;

    /** {@code FrustumIntersection.INTERSECT}: the box crosses at least one plane. */
    public static final int INTERSECT = -1;

    /** {@code FrustumIntersection.INSIDE}: the box is in front of every plane. */
    public static final int INSIDE = -2;

    /** {@code FrustumIntersection.OUTSIDE}: the box is entirely behind one plane. */
    public static final int OUTSIDE = -3;

    private final GpuDevice device;

    private final GpuComputePipeline pipeline;

    /**
     * Compiles the kernel on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipeline
     */
    public GpuCullingKernels(GpuDevice device) {
        pipeline = Kernels.pipeline(device, "cull_aabb", new Dimensions(EngineBuffers.GROUP, 1, 1), PARAMS.length,
                Set.of("FP32", "INT32"), Access.READ, Access.WRITE);
        this.device = device;
    }

    /**
     * The compiled kernel, for applications that manage pipelines themselves. Bindings: slot 0 the boxes, 1 the
     * results; the parameter block holds the 24 plane floats, then {@code count, boundsOff, boundsStride, resultOff,
     * gridX} as 32-bit values ({@code gridX} is the width of the dispatch grid, which indexes groups as
     * {@code group.y * gridX + group.x}).
     *
     * @return the kernel bundle
     */
    public static KernelBundle kernel() {
        return Kernels.bundle("cull_aabb");
    }

    /**
     * Records {@code result[resultOff + i] = CullingKernels.classifyAabb(planes, box i)} for {@code i} in
     * {@code [0, count)}: {@link #INSIDE}, {@link #INTERSECT} or {@link #OUTSIDE}, as {@code frustumCullAabbBatch}
     * writes them. Nothing is recorded when {@code count} is 0 or negative, as on the CPU. {@code result} must be a
     * different buffer from {@code bounds}. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param planes the frustum planes, 24 floats from {@code planesOff} in {@code FrustumPlanes.getVec4Array}
     *        order; they are copied when this method records
     * @param planesOff the index in {@code planes} of the first float
     * @param bounds the boxes, six planes {@code minX, minY, minZ, maxX, maxY, maxZ}
     * @param boundsOff the element index of {@code minX} of box 0
     * @param boundsStride the number of elements from one plane of {@code bounds} to the next, at least
     *        {@code count}
     * @param result receives one 32-bit int per box
     * @param resultOff the element index of the result of box 0
     * @param count the number of boxes
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code planes} does not hold 24 floats from {@code planesOff}, a buffer is
     *         too small for its offset and stride (or needs element indices of 2^32 or more), {@code result} is
     *         {@code bounds}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder frustumCullAabbBatch(GpuCommandEncoder encoder, float[] planes, int planesOff,
            GpuBuffer bounds, long boundsOff, int boundsStride, GpuBuffer result, long resultOff, int count) {
        Kernels.notNull(encoder, "encoder");
        Kernels.notNull(planes, "planes");
        if (planesOff < 0 || planesOff > planes.length - PLANE_FLOATS) {
            throw new IllegalArgumentException("planes must hold " + PLANE_FLOATS + " floats from index " + planesOff
                    + " (has " + planes.length + ")");
        }
        int n = Math.max(count, 0);
        EngineBuffers.soa(bounds, boundsOff, boundsStride, 6, n, "bounds");
        Kernels.range(result, resultOff, n, "result");
        EngineBuffers.distinct(result, "result", bounds);
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = EngineBuffers.groups(device, n);
        Kernels.Params params = new Kernels.Params(PARAMS.length);
        for (int i = 0; i < PLANE_FLOATS; i++) {
            params.f32(planes[planesOff + i]);
        }
        byte[] block = params.u32(n).u32(boundsOff).u32(boundsStride).u32(resultOff).u32(groups.x()).bytes();
        return encoder.dispatch(pipeline, Map.of(0, bounds, 1, result), block, groups);
    }

    /** Releases the pipeline; work already recorded keeps it alive until it completes. Idempotent. */
    @Override
    public void close() {
        pipeline.close();
    }
}
