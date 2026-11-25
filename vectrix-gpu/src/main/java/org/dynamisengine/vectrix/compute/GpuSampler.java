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
 * The GPU twin of Vectrix {@code nn.Sampler.argmax}, batched over the rows of a matrix: greedy decoding of many rows
 * of logits at once, one index per row.
 * <p>
 * Each row's index is the one {@code Sampler.argmax} returns: the largest logit, the first of equal ones (so
 * {@code -0} and {@code +0} tie); the first NaN if the row holds one; {@code -1} for an empty row. The kernel compares
 * the floats' bits through an integer key with the order of Java's float comparison, so the result is the same on
 * every device, subnormal logits included. One group of 256 threads scans each row.
 * <p>
 * {@code Sampler.sample} is not ported: it sorts a candidate heap and walks a cumulative sum one token at a time, so
 * it is sequential host-side work over one row.
 * <p>
 * One pipeline, compiled when the instance is created.
 *
 * @since 3.2.0
 */
public final class GpuSampler implements AutoCloseable {

    /** The parameter block, in the order the kernel declares it (checked against the compiled kernel). */
    static final String[] PARAMS = {"rows", "n", "off", "ld", "dOff", "gridX"};

    private static final int GROUP = 256;

    private final GpuDevice device;

    private final GpuComputePipeline pipeline;

    /**
     * Compiles the kernel on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipeline
     */
    public GpuSampler(GpuDevice device) {
        pipeline = Kernels.pipeline(device, "argmax", new Dimensions(GROUP, 1, 1), PARAMS, Set.of("INT32"),
                Access.READ, Access.WRITE);
        this.device = device;
    }

    /**
     * The compiled kernel, for applications that manage pipelines themselves. Bindings: slot 0 the logits, 1 the
     * indices; the parameter block holds the 32-bit fields {@code rows, n, off, ld, dOff, gridX}.
     *
     * @return the kernel bundle
     */
    public static KernelBundle kernel() {
        return Kernels.bundle("argmax");
    }

    /**
     * Records {@code dest[destOff + r] = Sampler.argmax(logits, off + r·ld, n)} for each row {@code r} in
     * {@code [0, rows)}, as 32-bit ints: the index within the row, or {@code -1} when {@code n} is 0. Nothing is
     * recorded when {@code rows} is 0. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param rows the number of rows, {@code >= 0}
     * @param logits the rows of logits
     * @param off the element index of the first row
     * @param ld the stride between rows, at least {@code n}
     * @param n the row length, {@code >= 0}
     * @param dest receives one index per row; must be a different buffer from {@code logits}
     * @param destOff the element index of the first row's index
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size or offset is negative, the rows or the indices do not fit their
     *         buffer, {@code dest} is {@code logits}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder argmax(GpuCommandEncoder encoder, int rows, GpuBuffer logits, long off, int ld, int n,
            GpuBuffer dest, long destOff) {
        Kernels.notNull(encoder, "encoder");
        if (rows < 0 || n < 0) {
            throw new IllegalArgumentException("negative size: " + rows + ", " + n);
        }
        if (off < 0) {
            throw new IllegalArgumentException("negative offset: " + off);
        }
        Kernels.matrix(logits, off, ld, rows, n, "logits");
        Kernels.range(dest, destOff, rows, "dest");
        if (dest == logits) {
            throw new IllegalArgumentException("dest must be a different buffer from logits");
        }
        if (rows == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), rows);
        byte[] params = new Kernels.Params(PARAMS).u32(rows).u32(n).u32(off).u32(n == 0 ? 0 : ld)
                .u32(destOff).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, pipeline, Map.of(0, logits, 1, dest), params, groups);
    }

    /** Releases the pipeline; work already recorded keeps it alive until it completes. Idempotent. */
    @Override
    public void close() {
        pipeline.close();
    }
}
