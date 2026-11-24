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
 * The GPU twin of Vectrix {@code nn.Softmax.softmax}, over the rows of a matrix: each row {@code r} of {@code y}
 * becomes {@code e^(x[r][i] - m) / Σ e^(x[r][j] - m)}, {@code m} the row's maximum.
 * <p>
 * Each row is computed as {@code Softmax.softmax} computes it: Java's {@code Math.max} for the maximum (so a NaN
 * anywhere makes the row NaN), {@code ReproducibleMath.exp} with the rounding-error correction of the shift, and
 * {@code Blasf.sum}'s order for the sum. On an IEEE device each row is the bits Vectrix computes (see the package
 * comment for subnormals: probabilities below {@code 2^-126} become 0 on GPUs that flush subnormals). Rows are
 * independent; one group of 256 threads computes each.
 * <p>
 * Two pipelines, compiled when the instance is created: one with separate input and output buffers, one in place.
 *
 * @since 3.1.0
 */
public final class GpuSoftmax implements AutoCloseable {

    /** The parameter block of the two-buffer kernel (checked against the compiled kernel). */
    static final String[] PARAMS = {"rows", "n", "xOff", "ldx", "yOff", "ldy", "gridX"};

    /** The parameter block of the in-place kernel (checked against the compiled kernel). */
    static final String[] PARAMS_IN_PLACE = {"rows", "n", "xOff", "ldx", "gridX"};

    /** The longest row: 1024 reduction blocks of 256 elements, the kernel's group-memory budget. */
    public static final int MAX_ROW = 1024 * 256;

    private static final int GROUP = 256;

    private final GpuDevice device;

    private final GpuComputePipeline twoBuffers;

    private final GpuComputePipeline inPlace;

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipelines
     */
    public GpuSoftmax(GpuDevice device) {
        Dimensions local = new Dimensions(GROUP, 1, 1);
        twoBuffers = Kernels.pipeline(device, "softmax", local, PARAMS.length, Set.of("FP32"), Access.READ,
                Access.WRITE);
        GpuComputePipeline second;
        try {
            second = Kernels.pipeline(device, "softmax_inplace", local, PARAMS_IN_PLACE.length, Set.of("FP32"),
                    Access.READ_WRITE);
        } catch (RuntimeException e) {
            twoBuffers.close();
            throw e;
        }
        inPlace = second;
        this.device = device;
    }

    /**
     * The compiled two-buffer kernel, for applications that manage pipelines themselves. Bindings: slot 0 {@code x},
     * 1 {@code y}; the parameter block holds the 32-bit fields {@code rows, n, xOff, ldx, yOff, ldy, gridX}.
     *
     * @return the kernel bundle
     */
    public static KernelBundle kernel() {
        return Kernels.bundle("softmax");
    }

    /**
     * Records the softmax of {@code rows} rows of {@code n} floats: row {@code r} of {@code x} starts at element
     * {@code xOff + r·ldx}, and its result is written from {@code yOff + r·ldy}. {@code y} may be {@code x} only at
     * the same offset and stride (in place); otherwise the two must be different buffers. Nothing is recorded when
     * {@code rows} or {@code n} is 0. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param rows the number of rows, {@code >= 0}
     * @param x the input rows
     * @param xOff the element index of the first input row
     * @param ldx the stride between input rows, at least {@code n}
     * @param y receives the output rows; may be {@code x} at the same offset and stride
     * @param yOff the element index of the first output row
     * @param ldy the stride between output rows, at least {@code n}
     * @param n the row length, from 0 to {@link #MAX_ROW}
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size is negative, {@code n} exceeds {@link #MAX_ROW}, a matrix does not
     *         fit its buffer, {@code y} is {@code x} at a different offset or stride, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder softmax(GpuCommandEncoder encoder, int rows, GpuBuffer x, long xOff, int ldx, GpuBuffer y,
            long yOff, int ldy, int n) {
        Kernels.notNull(encoder, "encoder");
        if (rows < 0 || n < 0) {
            throw new IllegalArgumentException("negative size: " + rows + ", " + n);
        }
        if (n > MAX_ROW) {
            throw new IllegalArgumentException("row length exceeds " + MAX_ROW + ": " + n);
        }
        Kernels.matrix(x, xOff, ldx, rows, n, "x");
        Kernels.matrix(y, yOff, ldy, rows, n, "y");
        boolean same = x == y;
        if (same && (xOff != yOff || ldx != ldy)) {
            throw new IllegalArgumentException("y may be x only at the same offset and stride");
        }
        if (rows == 0 || n == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), rows);
        if (same) {
            byte[] params = new Kernels.Params(PARAMS_IN_PLACE.length)
                    .u32(rows).u32(n).u32(xOff).u32(ldx).u32(groups.x()).bytes();
            return encoder.dispatch(inPlace, Map.of(0, x), params, groups);
        }
        byte[] params = new Kernels.Params(PARAMS.length)
                .u32(rows).u32(n).u32(xOff).u32(ldx).u32(yOff).u32(ldy).u32(groups.x()).bytes();
        return encoder.dispatch(twoBuffers, Map.of(0, x, 1, y), params, groups);
    }

    /** Releases the pipelines; work already recorded keeps them alive until it completes. Idempotent. */
    @Override
    public void close() {
        try {
            twoBuffers.close();
        } finally {
            inPlace.close();
        }
    }
}
