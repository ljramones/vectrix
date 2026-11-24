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
 * The GPU twin of Vectrix {@code nn.Softmax}, over the rows of a matrix: softmax, log-softmax and log-sum-exp, and
 * the softmax and log-softmax backward passes.
 * <p>
 * Each row is computed as the {@code Softmax} method computes it: Java's {@code Math.max} for the maximum (so a NaN
 * anywhere makes the row NaN), {@code ReproducibleMath.exp} with the rounding-error correction of the shift,
 * {@code ReproducibleMath.log}, and {@code Blasf.sum}'s and {@code Blasf.dot}'s order for the sums. On an IEEE
 * device each row is the bits Vectrix computes (see the package comment for subnormals: probabilities below
 * {@code 2^-126} become 0 on GPUs that flush subnormals). Rows are independent; one group of 256 threads computes
 * each. The GPU methods need no scratch buffer.
 * <p>
 * The pipelines are compiled when the instance is created: for each operation one with separate buffers and, where
 * the Vectrix method allows its output to alias an input, one in place.
 *
 * @since 3.1.0
 */
public final class GpuSoftmax implements AutoCloseable {

    /** The parameter block of the two-buffer softmax kernel (checked against the compiled kernel). */
    static final String[] PARAMS = {"rows", "n", "xOff", "ldx", "yOff", "ldy", "gridX"};

    /** The parameter block of the in-place softmax kernel (checked against the compiled kernel). */
    static final String[] PARAMS_IN_PLACE = {"rows", "n", "xOff", "ldx", "gridX"};

    /** The parameter block of the two-buffer log-softmax kernel. */
    static final String[] PARAMS_LOG = {"rows", "n", "xOff", "ldx", "yOff", "ldy", "gridX"};

    /** The parameter block of the in-place log-softmax kernel. */
    static final String[] PARAMS_LOG_IN_PLACE = {"rows", "n", "xOff", "ldx", "gridX"};

    /** The parameter block of the log-sum-exp kernel. */
    static final String[] PARAMS_LSE = {"rows", "n", "xOff", "ldx", "outOff", "gridX"};

    /** The parameter block of the three-buffer softmax and log-softmax backward kernels. */
    static final String[] PARAMS_BACKWARD = {"rows", "n", "yOff", "ldy", "dyOff", "lddy", "dxOff", "lddx", "gridX"};

    /** The parameter block of the in-place backward kernels: {@code a} is the input {@code dx} overwrites. */
    static final String[] PARAMS_BACKWARD_IN_PLACE = {"rows", "n", "aOff", "lda", "bOff", "ldb", "dxIsDy", "gridX"};

    /** The longest row: 1024 reduction blocks of 256 elements, the kernels' group-memory budget. */
    public static final int MAX_ROW = 1024 * 256;

    private static final int SOFTMAX = 0;
    private static final int SOFTMAX_IN_PLACE = 1;
    private static final int LOG = 2;
    private static final int LOG_IN_PLACE = 3;
    private static final int LSE = 4;
    private static final int BACKWARD = 5;
    private static final int BACKWARD_IN_PLACE = 6;
    private static final int LOG_BACKWARD = 7;
    private static final int LOG_BACKWARD_IN_PLACE = 8;

    private final GpuDevice device;

    private final GpuComputePipeline[] pipelines;

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipelines
     */
    public GpuSoftmax(GpuDevice device) {
        pipelines = Rows.compile(device, Rows.GROUP, Set.of("FP32"),
                new Rows.Spec("softmax", PARAMS.length, Access.READ, Access.WRITE),
                new Rows.Spec("softmax_inplace", PARAMS_IN_PLACE.length, Access.READ_WRITE),
                new Rows.Spec("log_softmax", PARAMS_LOG.length, Access.READ, Access.WRITE),
                new Rows.Spec("log_softmax_inplace", PARAMS_LOG_IN_PLACE.length, Access.READ_WRITE),
                new Rows.Spec("log_sum_exp", PARAMS_LSE.length, Access.READ, Access.WRITE),
                new Rows.Spec("softmax_backward", PARAMS_BACKWARD.length, Access.READ, Access.READ, Access.WRITE),
                new Rows.Spec("softmax_backward_inplace", PARAMS_BACKWARD_IN_PLACE.length, Access.READ_WRITE,
                        Access.READ),
                new Rows.Spec("log_softmax_backward", PARAMS_BACKWARD.length, Access.READ, Access.READ,
                        Access.WRITE),
                new Rows.Spec("log_softmax_backward_inplace", PARAMS_BACKWARD_IN_PLACE.length, Access.READ_WRITE,
                        Access.READ));
        this.device = device;
    }

    /**
     * The compiled two-buffer softmax kernel, for applications that manage pipelines themselves. Bindings: slot 0
     * {@code x}, 1 {@code y}; the parameter block holds the 32-bit fields {@code rows, n, xOff, ldx, yOff, ldy,
     * gridX}.
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
        return forward(encoder, rows, x, xOff, ldx, y, yOff, ldy, n, SOFTMAX);
    }

    /**
     * Records the log-softmax ({@code nn.Softmax.logSoftmax}) of {@code rows} rows of {@code n} floats:
     * {@code y[i] = (x[i] - m) - log Σ e^(x[j] - m)}, {@code m} the row's maximum, without forming the
     * probabilities. Row {@code r} of {@code x} starts at element {@code xOff + r·ldx}, and its result is written from
     * {@code yOff + r·ldy}. {@code y} may be {@code x} only at the same offset and stride (in place); otherwise the
     * two must be different buffers. Unlike the CPU method, no scratch is needed. Nothing is recorded when
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
     * @since 3.2.0
     */
    public GpuCommandEncoder logSoftmax(GpuCommandEncoder encoder, int rows, GpuBuffer x, long xOff, int ldx,
            GpuBuffer y, long yOff, int ldy, int n) {
        return forward(encoder, rows, x, xOff, ldx, y, yOff, ldy, n, LOG);
    }

    private GpuCommandEncoder forward(GpuCommandEncoder encoder, int rows, GpuBuffer x, long xOff, int ldx,
            GpuBuffer y, long yOff, int ldy, int n, int kernel) {
        Kernels.notNull(encoder, "encoder");
        Rows.shape(rows, n, 0);
        Kernels.matrix(x, xOff, ldx, rows, n, "x");
        Kernels.matrix(y, yOff, ldy, rows, n, "y");
        boolean same = Rows.inPlace(y, yOff, ldy, "y", x, xOff, ldx, "x");
        if (rows == 0 || n == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), rows);
        if (same) {
            byte[] params = new Kernels.Params(PARAMS_IN_PLACE.length)
                    .u32(rows).u32(n).u32(xOff).u32(ldx).u32(groups.x()).bytes();
            return encoder.dispatch(pipelines[kernel + 1], Map.of(0, x), params, groups);
        }
        byte[] params = new Kernels.Params(PARAMS.length)
                .u32(rows).u32(n).u32(xOff).u32(ldx).u32(yOff).u32(ldy).u32(groups.x()).bytes();
        return encoder.dispatch(pipelines[kernel], Map.of(0, x, 1, y), params, groups);
    }

    /**
     * Records the log-sum-exp ({@code nn.Softmax.logSumExp}) of {@code rows} rows of {@code n} floats:
     * {@code out[outOff + r] = log Σ e^(x[r][i])}, computed stably as the CPU method does; the maximum itself when it
     * is infinite, and {@code -∞} for empty rows ({@code n = 0}). Row {@code r} of {@code x} starts at element
     * {@code xOff + r·ldx}. {@code out} must be a different buffer from {@code x}. Unlike the CPU method, no scratch
     * is needed. Nothing is recorded when {@code rows} is 0. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param rows the number of rows, {@code >= 0}
     * @param x the input rows
     * @param xOff the element index of the first input row
     * @param ldx the stride between input rows, at least {@code n}
     * @param n the row length, from 0 to {@link #MAX_ROW}
     * @param out receives one value per row, at consecutive elements
     * @param outOff the element index in {@code out} of the first row's value
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size is negative, {@code n} exceeds {@link #MAX_ROW}, {@code x} or the
     *         {@code rows} results do not fit their buffers, {@code out} is {@code x}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     * @since 3.2.0
     */
    public GpuCommandEncoder logSumExp(GpuCommandEncoder encoder, int rows, GpuBuffer x, long xOff, int ldx, int n,
            GpuBuffer out, long outOff) {
        Kernels.notNull(encoder, "encoder");
        Rows.shape(rows, n, 0);
        Kernels.matrix(x, xOff, ldx, rows, n, "x");
        Kernels.range(out, outOff, rows, "out");
        Rows.exclusive(out, "out", x);
        if (rows == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), rows);
        byte[] params = new Kernels.Params(PARAMS_LSE.length)
                .u32(rows).u32(n).u32(xOff).u32(n == 0 ? 0 : ldx).u32(outOff).u32(groups.x()).bytes();
        return encoder.dispatch(pipelines[LSE], Map.of(0, x, 1, out), params, groups);
    }

    /**
     * Records the softmax backward pass ({@code nn.Softmax.softmaxBackward}) of {@code rows} rows of {@code n}
     * floats, from the forward output: {@code dx[i] = y[i]·(dy[i] - Σ dy[j]·y[j])}. Row {@code r} of each matrix
     * starts at its offset plus {@code r} times its stride. {@code dx} may be {@code y} or {@code dy} at the same
     * offset and stride (in place), but not both; otherwise it must be a different buffer from them ({@code y} and
     * {@code dy} may share a buffer). Nothing is recorded when {@code rows} or {@code n} is 0. All arguments are
     * checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param rows the number of rows, {@code >= 0}
     * @param y the forward output
     * @param yOff the element index of the first row of {@code y}
     * @param ldy the stride between rows of {@code y}, at least {@code n}
     * @param dy the gradient of the output
     * @param dyOff the element index of the first row of {@code dy}
     * @param lddy the stride between rows of {@code dy}, at least {@code n}
     * @param dx receives the gradient of the input; may be {@code y} or {@code dy} at the same offset and stride
     * @param dxOff the element index of the first row of {@code dx}
     * @param lddx the stride between rows of {@code dx}, at least {@code n}
     * @param n the row length, from 0 to {@link #MAX_ROW}
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size is negative, {@code n} exceeds {@link #MAX_ROW}, a matrix does not
     *         fit its buffer, {@code dx} shares a buffer with an input other than in place, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     * @since 3.2.0
     */
    public GpuCommandEncoder softmaxBackward(GpuCommandEncoder encoder, int rows, GpuBuffer y, long yOff, int ldy,
            GpuBuffer dy, long dyOff, int lddy, GpuBuffer dx, long dxOff, int lddx, int n) {
        return backward(encoder, rows, y, yOff, ldy, dy, dyOff, lddy, dx, dxOff, lddx, n, BACKWARD);
    }

    /**
     * Records the log-softmax backward pass ({@code nn.Softmax.logSoftmaxBackward}) of {@code rows} rows of
     * {@code n} floats, from the forward output: {@code dx[i] = dy[i] - e^(y[i])·Σ dy[j]} (one fused multiply-add).
     * Row {@code r} of each matrix starts at its offset plus {@code r} times its stride. {@code dx} may be {@code y}
     * or {@code dy} at the same offset and stride (in place), but not both; otherwise it must be a different buffer
     * from them ({@code y} and {@code dy} may share a buffer). Nothing is recorded when {@code rows} or {@code n} is
     * 0. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param rows the number of rows, {@code >= 0}
     * @param y the forward output
     * @param yOff the element index of the first row of {@code y}
     * @param ldy the stride between rows of {@code y}, at least {@code n}
     * @param dy the gradient of the output
     * @param dyOff the element index of the first row of {@code dy}
     * @param lddy the stride between rows of {@code dy}, at least {@code n}
     * @param dx receives the gradient of the input; may be {@code y} or {@code dy} at the same offset and stride
     * @param dxOff the element index of the first row of {@code dx}
     * @param lddx the stride between rows of {@code dx}, at least {@code n}
     * @param n the row length, from 0 to {@link #MAX_ROW}
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size is negative, {@code n} exceeds {@link #MAX_ROW}, a matrix does not
     *         fit its buffer, {@code dx} shares a buffer with an input other than in place, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     * @since 3.2.0
     */
    public GpuCommandEncoder logSoftmaxBackward(GpuCommandEncoder encoder, int rows, GpuBuffer y, long yOff, int ldy,
            GpuBuffer dy, long dyOff, int lddy, GpuBuffer dx, long dxOff, int lddx, int n) {
        return backward(encoder, rows, y, yOff, ldy, dy, dyOff, lddy, dx, dxOff, lddx, n, LOG_BACKWARD);
    }

    private GpuCommandEncoder backward(GpuCommandEncoder encoder, int rows, GpuBuffer y, long yOff, int ldy,
            GpuBuffer dy, long dyOff, int lddy, GpuBuffer dx, long dxOff, int lddx, int n, int kernel) {
        Kernels.notNull(encoder, "encoder");
        Rows.shape(rows, n, 0);
        Kernels.matrix(y, yOff, ldy, rows, n, "y");
        Kernels.matrix(dy, dyOff, lddy, rows, n, "dy");
        Kernels.matrix(dx, dxOff, lddx, rows, n, "dx");
        boolean onY = Rows.inPlace(dx, dxOff, lddx, "dx", y, yOff, ldy, "y");
        boolean onDy = Rows.inPlace(dx, dxOff, lddx, "dx", dy, dyOff, lddy, "dy");
        if (onY && onDy) {
            throw new IllegalArgumentException("dx may share a buffer with only one of y and dy");
        }
        if (rows == 0 || n == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), rows);
        if (onY || onDy) {
            GpuBuffer other = onY ? dy : y;
            byte[] params = new Kernels.Params(PARAMS_BACKWARD_IN_PLACE.length)
                    .u32(rows).u32(n).u32(dxOff).u32(lddx).u32(onY ? dyOff : yOff).u32(onY ? lddy : ldy)
                    .u32(onDy ? 1 : 0).u32(groups.x()).bytes();
            return encoder.dispatch(pipelines[kernel + 1], Map.of(0, dx, 1, other), params, groups);
        }
        byte[] params = new Kernels.Params(PARAMS_BACKWARD.length)
                .u32(rows).u32(n).u32(yOff).u32(ldy).u32(dyOff).u32(lddy).u32(dxOff).u32(lddx).u32(groups.x())
                .bytes();
        return encoder.dispatch(pipelines[kernel], Map.of(0, y, 1, dy, 2, dx), params, groups);
    }

    /** Releases the pipelines; work already recorded keeps them alive until it completes. Idempotent. */
    @Override
    public void close() {
        Rows.close(pipelines);
    }
}
