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
 * The GPU twin of Vectrix {@code nn.Loss}: cross-entropy over the rows of a matrix of logits, forward and backward,
 * with one 32-bit integer target class per row in a buffer.
 * <p>
 * Each row is computed as the {@code Loss} method computes it: the loss as {@code Softmax.logSumExp(x) - x[target]},
 * the gradient as {@code dLoss·(softmax(x) - onehot(target))}, with {@link GpuSoftmax}'s exact ports of the
 * {@code Softmax} kernels. On an IEEE device each row is the bits Vectrix computes (see the package comment for
 * subnormals). Rows are independent; one group of 256 threads computes each.
 * <p>
 * <b>Targets.</b> The CPU methods throw for a target outside {@code [0, n)}; the targets are only read on the GPU,
 * so such a row's loss is NaN instead, and its gradient row is all NaN. Nothing outside the buffers is read.
 * <p>
 * The pipelines are compiled when the instance is created: the loss, and the gradient with separate buffers and in
 * place.
 *
 * @since 3.2.0
 */
public final class GpuLoss implements AutoCloseable {

    /** The parameter block of the loss kernel (checked against the compiled kernel). */
    static final String[] PARAMS = {"rows", "n", "xOff", "ldx", "tOff", "lossOff", "gridX"};

    /** The parameter block of the gradient kernel with separate buffers. */
    static final String[] PARAMS_BACKWARD = {"rows", "n", "xOff", "ldx", "tOff", "dxOff", "lddx", "dLoss", "gridX"};

    /** The parameter block of the in-place gradient kernel. */
    static final String[] PARAMS_BACKWARD_IN_PLACE = {"rows", "n", "xOff", "ldx", "tOff", "dLoss", "gridX"};

    /** The most classes per row: {@link GpuSoftmax#MAX_ROW}. */
    public static final int MAX_ROW = GpuSoftmax.MAX_ROW;

    private static final int LOSS = 0;
    private static final int BACKWARD = 1;
    private static final int BACKWARD_IN_PLACE = 2;

    private final GpuDevice device;

    private final GpuComputePipeline[] pipelines;

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipelines
     */
    public GpuLoss(GpuDevice device) {
        pipelines = Rows.compile(device, Rows.GROUP, Set.of("FP32", "INT32"),
                new Rows.Spec("cross_entropy", PARAMS.length, Access.READ, Access.READ, Access.WRITE),
                new Rows.Spec("cross_entropy_backward", PARAMS_BACKWARD.length, Access.READ, Access.READ,
                        Access.WRITE),
                new Rows.Spec("cross_entropy_backward_inplace", PARAMS_BACKWARD_IN_PLACE.length, Access.READ_WRITE,
                        Access.READ));
        this.device = device;
    }

    /**
     * The compiled loss kernel, for applications that manage pipelines themselves. Bindings: slot 0 the logits,
     * 1 the targets (32-bit integers), 2 the losses; the parameter block holds the 32-bit fields
     * {@code rows, n, xOff, ldx, tOff, lossOff, gridX}.
     *
     * @return the kernel bundle
     */
    public static KernelBundle kernel() {
        return Kernels.bundle("cross_entropy");
    }

    /**
     * Records the cross-entropy ({@code nn.Loss.crossEntropy}) of {@code rows} rows of {@code n} logits:
     * {@code loss[lossOff + r] = logSumExp(x_r) - x_r[t_r]}, {@code x_r} the row from element {@code off + r·ld} of
     * {@code logits} and {@code t_r = targets[targetOff + r]}. A target outside {@code [0, n)} gives NaN. Unlike the
     * CPU method, no scratch is needed. Nothing is recorded when {@code rows} is 0. All arguments are checked before
     * anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param rows the number of rows, {@code >= 0}
     * @param logits the rows of logits
     * @param off the element index of the first row
     * @param ld the stride between rows, at least {@code n}
     * @param n the number of classes, from 1 to {@link #MAX_ROW}
     * @param targets the target class of each row, as 32-bit integers
     * @param targetOff the element index in {@code targets} of the first row's target
     * @param loss receives one loss per row, in nats, at consecutive elements
     * @param lossOff the element index in {@code loss} of the first row's loss
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code rows} is negative, {@code n} is outside {@code [1, MAX_ROW]}, a
     *         matrix or vector does not fit its buffer, {@code loss} is {@code logits} or {@code targets}, or an
     *         argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder crossEntropy(GpuCommandEncoder encoder, int rows, GpuBuffer logits, long off, int ld,
            int n, GpuBuffer targets, long targetOff, GpuBuffer loss, long lossOff) {
        Kernels.notNull(encoder, "encoder");
        Rows.shape(rows, n, 1);
        Kernels.matrix(logits, off, ld, rows, n, "logits");
        Kernels.range(targets, targetOff, rows, "targets");
        Kernels.range(loss, lossOff, rows, "loss");
        Rows.exclusive(loss, "loss", logits, targets);
        if (rows == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), rows);
        byte[] params = new Kernels.Params(PARAMS.length)
                .u32(rows).u32(n).u32(off).u32(ld).u32(targetOff).u32(lossOff).u32(groups.x()).bytes();
        return encoder.dispatch(pipelines[LOSS], Map.of(0, logits, 1, targets, 2, loss), params, groups);
    }

    /**
     * Records the gradient of {@link #crossEntropy} with respect to the logits ({@code nn.Loss.crossEntropyBackward})
     * for {@code rows} rows: {@code dx_r = dLoss·(softmax(x_r) - onehot(t_r))}, written from element
     * {@code dxOff + r·lddx}. A target outside {@code [0, n)} makes that row of {@code dx} NaN. {@code dx} may be
     * {@code logits} at the same offset and stride (in place); otherwise it must be a different buffer from the
     * logits and the targets. Nothing is recorded when {@code rows} is 0. All arguments are checked before anything
     * is recorded.
     *
     * @param encoder the encoder to record into
     * @param rows the number of rows, {@code >= 0}
     * @param logits the rows of logits
     * @param off the element index of the first row
     * @param ld the stride between rows, at least {@code n}
     * @param n the number of classes, from 1 to {@link #MAX_ROW}
     * @param targets the target class of each row, as 32-bit integers
     * @param targetOff the element index in {@code targets} of the first row's target
     * @param dLoss the gradient flowing into each row's loss (1 for a plain loss)
     * @param dx receives the gradients; may be {@code logits} at the same offset and stride
     * @param dxOff the element index of the first row of {@code dx}
     * @param lddx the stride between rows of {@code dx}, at least {@code n}
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code rows} is negative, {@code n} is outside {@code [1, MAX_ROW]}, a
     *         matrix or vector does not fit its buffer, {@code dx} is {@code logits} at a different offset or stride,
     *         a written buffer is also an input, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder crossEntropyBackward(GpuCommandEncoder encoder, int rows, GpuBuffer logits, long off,
            int ld, int n, GpuBuffer targets, long targetOff, float dLoss, GpuBuffer dx, long dxOff, int lddx) {
        Kernels.notNull(encoder, "encoder");
        Rows.shape(rows, n, 1);
        Kernels.matrix(logits, off, ld, rows, n, "logits");
        Kernels.range(targets, targetOff, rows, "targets");
        Kernels.matrix(dx, dxOff, lddx, rows, n, "dx");
        boolean same = Rows.inPlace(dx, dxOff, lddx, "dx", logits, off, ld, "logits");
        Rows.exclusive(dx, "dx", targets);
        if (rows == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), rows);
        if (same) {
            byte[] params = new Kernels.Params(PARAMS_BACKWARD_IN_PLACE.length)
                    .u32(rows).u32(n).u32(off).u32(ld).u32(targetOff).f32(dLoss).u32(groups.x()).bytes();
            return encoder.dispatch(pipelines[BACKWARD_IN_PLACE], Map.of(0, logits, 1, targets), params, groups);
        }
        byte[] params = new Kernels.Params(PARAMS_BACKWARD.length)
                .u32(rows).u32(n).u32(off).u32(ld).u32(targetOff).u32(dxOff).u32(lddx).f32(dLoss).u32(groups.x())
                .bytes();
        return encoder.dispatch(pipelines[BACKWARD], Map.of(0, logits, 1, targets, 2, dx), params, groups);
    }

    /** Releases the pipelines; work already recorded keeps them alive until it completes. Idempotent. */
    @Override
    public void close() {
        Rows.close(pipelines);
    }
}
