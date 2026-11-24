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
 * The GPU twin of Vectrix {@code hash.Philox}: counter-based random streams (Philox-4x32-10) written into a buffer.
 * <p>
 * Element {@code e} of a fill is number {@code start + e} of the stream {@code seed}, exactly as Vectrix
 * {@code Philox.word} and {@code Philox.uniform} compute it: the arithmetic is integer, so every device produces
 * the CPU's bits. A dropout mask, a weight initialization or a sample drawn on the GPU therefore equals the one drawn
 * on the CPU, and any split of a stream into fills gives the same values. Stream positions are unsigned 64-bit and
 * wrap, as in Vectrix.
 * <p>
 * One pipeline, compiled when the instance is created. Each thread computes one Philox block (four values).
 *
 * @since 3.1.0
 */
public final class GpuPhilox implements AutoCloseable {

    /** The parameter block, in the order the kernel declares it (checked against the compiled kernel). */
    static final String[] PARAMS = {"seedLo", "seedHi", "startLo", "startHi", "off", "n", "uniform", "gridX"};

    /** The largest number of values one fill may write. */
    public static final int MAX_FILL = Integer.MAX_VALUE - 8;

    private static final int GROUP = 64;

    private final GpuDevice device;

    private final GpuComputePipeline pipeline;

    /**
     * Compiles the kernel on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipeline
     */
    public GpuPhilox(GpuDevice device) {
        pipeline = Kernels.pipeline(device, "philox", new Dimensions(GROUP, 1, 1), PARAMS.length,
                Set.of("INT32", "FP32"), Access.WRITE);
        this.device = device;
    }

    /**
     * The compiled kernel, for applications that manage pipelines themselves. Binding: slot 0 the destination; the
     * parameter block holds the 32-bit fields {@code seedLo, seedHi, startLo, startHi, off, n, uniform, gridX}.
     *
     * @return the kernel bundle
     */
    public static KernelBundle kernel() {
        return Kernels.bundle("philox");
    }

    /**
     * Records {@code dest[off + i] = Philox.word(seed, start + i)} for {@code i} in {@code [0, n)}, as 32-bit
     * integers.
     *
     * @param encoder the encoder to record into
     * @param seed the stream's seed
     * @param start the stream position of the first value, read as unsigned
     * @param dest receives the values
     * @param off the element index in {@code dest} of the first value
     * @param n the number of values, from 0 to {@link #MAX_FILL}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or {@code off} is negative, {@code n} exceeds
     *         {@link #MAX_FILL}, {@code dest} is too small, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder fillWords(GpuCommandEncoder encoder, long seed, long start, GpuBuffer dest, long off,
            int n) {
        return fill(encoder, seed, start, dest, off, n, false);
    }

    /**
     * Records {@code dest[off + i] = Philox.uniform(seed, start + i)} for {@code i} in {@code [0, n)}: floats in
     * {@code [0, 1)} carrying the top 24 bits of each word.
     *
     * @param encoder the encoder to record into
     * @param seed the stream's seed
     * @param start the stream position of the first value, read as unsigned
     * @param dest receives the values
     * @param off the element index in {@code dest} of the first value
     * @param n the number of values, from 0 to {@link #MAX_FILL}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or {@code off} is negative, {@code n} exceeds
     *         {@link #MAX_FILL}, {@code dest} is too small, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder fillUniform(GpuCommandEncoder encoder, long seed, long start, GpuBuffer dest, long off,
            int n) {
        return fill(encoder, seed, start, dest, off, n, true);
    }

    private GpuCommandEncoder fill(GpuCommandEncoder encoder, long seed, long start, GpuBuffer dest, long off, int n,
            boolean uniform) {
        Kernels.notNull(encoder, "encoder");
        if (n > MAX_FILL) {
            throw new IllegalArgumentException("n exceeds " + MAX_FILL + ": " + n);
        }
        Kernels.range(dest, off, n, "dest");
        if (n == 0) {
            return encoder;
        }
        long counters = ((start & 3) + n + 3) / 4;
        Dimensions groups = Kernels.grid(device.capabilities(), (counters + GROUP - 1) / GROUP);
        byte[] params = new Kernels.Params(PARAMS.length)
                .u32(seed).u32(seed >>> 32).u32(start).u32(start >>> 32).u32(off).u32(n).u32(uniform ? 1 : 0)
                .u32(groups.x()).bytes();
        return encoder.dispatch(pipeline, Map.of(0, dest), params, groups);
    }

    /** Releases the pipeline; work already recorded keeps it alive until it completes. Idempotent. */
    @Override
    public void close() {
        pipeline.close();
    }
}
