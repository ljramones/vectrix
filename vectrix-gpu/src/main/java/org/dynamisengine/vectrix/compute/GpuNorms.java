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
 * The GPU twin of Vectrix {@code nn.Norms}: layer normalization and RMS normalization over the rows of a matrix,
 * forward and backward.
 * <p>
 * Each row is computed as the {@code Norms} method computes it: means and variances in {@code Blasf}'s summation
 * order, the variance from centred values, {@code 1/σ} as {@code 1 / sqrt(var + eps)} with a correctly rounded square
 * root, and the same fused multiply-adds. On an IEEE device the results are the bits Vectrix computes (see the
 * package comment for subnormals). One group of 256 threads computes each row.
 * <p>
 * <b>Statistics.</b> The forward kernels write the values the CPU method writes to {@code stats}, row after row:
 * {@code mean} and {@code 1/σ} of row {@code r} at {@code statsOff + 2r} and {@code statsOff + 2r + 1} for layer
 * norm, {@code 1/rms} at {@code statsOff + r} for RMS norm. The backward kernels read them from the same layout.
 * <p>
 * <b>Parameter gradients.</b> As on the CPU, {@code dGamma} and {@code dBeta} accumulate ({@code +=}) over the
 * rows, in row order: the result is the bits of calling the CPU method on rows {@code 0, 1, ...} in turn with the
 * same {@code dGamma} and {@code dBeta}. They are computed by a kernel that walks the rows for each column, recorded
 * before the input gradient so that an in-place {@code dx} does not overwrite what it reads.
 * <p>
 * Optional arguments are {@code null} as in Vectrix; the instance binds a small placeholder buffer of its own in
 * their place. Outputs must be different buffers from every other argument, except the in-place forms the CPU
 * methods allow ({@code y} is {@code x}; {@code dx} is {@code x} or {@code dy}), at the same offset and stride. The
 * pipelines and placeholders are created with the instance.
 *
 * @since 3.2.0
 */
public final class GpuNorms implements AutoCloseable {

    /** The parameter block of the two-buffer layer-norm kernel (checked against the compiled kernel). */
    static final String[] PARAMS_LAYER = {"rows", "n", "xOff", "ldx", "gOff", "bOff", "yOff", "ldy", "statsOff",
        "flags", "eps", "gridX"};

    /** The parameter block of the in-place layer-norm kernel. */
    static final String[] PARAMS_LAYER_IN_PLACE = {"rows", "n", "xOff", "ldx", "gOff", "bOff", "statsOff", "flags",
        "eps", "gridX"};

    /** The parameter block of the two-buffer RMS-norm kernel. */
    static final String[] PARAMS_RMS = {"rows", "n", "xOff", "ldx", "gOff", "yOff", "ldy", "statsOff", "flags", "eps",
        "gridX"};

    /** The parameter block of the in-place RMS-norm kernel. */
    static final String[] PARAMS_RMS_IN_PLACE = {"rows", "n", "xOff", "ldx", "gOff", "statsOff", "flags", "eps",
        "gridX"};

    /** The parameter block of the input-gradient kernel with separate buffers (layer and RMS norm). */
    static final String[] PARAMS_BACKWARD = {"rows", "n", "xOff", "ldx", "gOff", "statsOff", "dyOff", "lddy",
        "dxOff", "lddx", "flags", "gridX"};

    /** The parameter block of the in-place input-gradient kernel: {@code a} is the input {@code dx} overwrites. */
    static final String[] PARAMS_BACKWARD_IN_PLACE = {"rows", "n", "aOff", "lda", "gOff", "statsOff", "bOff", "ldb",
        "flags", "gridX"};

    /** The parameter block of the parameter-gradient kernel (layer and RMS norm). */
    static final String[] PARAMS_PARAM_GRADS = {"rows", "n", "xOff", "ldx", "statsOff", "dyOff", "lddy", "dgOff",
        "dbOff", "flags", "gridX"};

    /** The longest row: {@link GpuSoftmax#MAX_ROW}. */
    public static final int MAX_ROW = GpuSoftmax.MAX_ROW;

    private static final int LAYER = 0;
    private static final int LAYER_IN_PLACE = 1;
    private static final int RMS = 2;
    private static final int RMS_IN_PLACE = 3;
    private static final int BACKWARD = 4;
    private static final int BACKWARD_IN_PLACE = 5;
    private static final int PARAM_GRADS = 6;

    private static final int PARAM_GROUP = 64;

    private static final int GAMMA = 1;
    private static final int BETA = 2;
    private static final int STATS = 4;
    private static final int DX_IS_DY = 2;
    private static final int RMS_FLAG = 4;

    private final GpuDevice device;

    private final GpuComputePipeline[] pipelines;

    /** Bound in place of absent inputs (read only). */
    private final GpuBuffer absentIn;

    /** Bound in place of absent outputs (never written); two, as written bindings must not overlap. */
    private final GpuBuffer absentOut1;

    private final GpuBuffer absentOut2;

    /**
     * Compiles the kernels on {@code device} and allocates the placeholders of absent arguments (three 4-byte
     * buffers).
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipelines or
     *         buffers
     */
    public GpuNorms(GpuDevice device) {
        GpuComputePipeline[] rowKernels = Rows.compile(device, Rows.GROUP, Set.of("FP32"),
                new Rows.Spec("layer_norm", PARAMS_LAYER, Access.READ, Access.READ, Access.READ, Access.WRITE,
                        Access.WRITE),
                new Rows.Spec("layer_norm_inplace", PARAMS_LAYER_IN_PLACE, Access.READ_WRITE, Access.READ,
                        Access.READ, Access.WRITE),
                new Rows.Spec("rms_norm", PARAMS_RMS, Access.READ, Access.READ, Access.WRITE, Access.WRITE),
                new Rows.Spec("rms_norm_inplace", PARAMS_RMS_IN_PLACE, Access.READ_WRITE, Access.READ,
                        Access.WRITE),
                new Rows.Spec("norm_backward", PARAMS_BACKWARD, Access.READ, Access.READ, Access.READ,
                        Access.READ, Access.WRITE),
                new Rows.Spec("norm_backward_inplace", PARAMS_BACKWARD_IN_PLACE, Access.READ_WRITE,
                        Access.READ, Access.READ, Access.READ));
        GpuComputePipeline[] grads;
        GpuBuffer in = null;
        GpuBuffer out1 = null;
        GpuBuffer out2 = null;
        try {
            grads = Rows.compile(device, PARAM_GROUP, Set.of("FP32"),
                    new Rows.Spec("norm_param_grads", PARAMS_PARAM_GRADS, Access.READ, Access.READ,
                            Access.READ, Access.READ_WRITE, Access.READ_WRITE));
            in = Rows.placeholder(device);
            out1 = Rows.placeholder(device);
            out2 = Rows.placeholder(device);
        } catch (RuntimeException e) {
            try {
                Rows.close(in, out1, out2);
                Rows.close(rowKernels);
            } catch (RuntimeException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
        GpuComputePipeline[] all = new GpuComputePipeline[rowKernels.length + 1];
        System.arraycopy(rowKernels, 0, all, 0, rowKernels.length);
        all[PARAM_GRADS] = grads[0];
        this.pipelines = all;
        this.absentIn = in;
        this.absentOut1 = out1;
        this.absentOut2 = out2;
        this.device = device;
    }

    /**
     * The compiled two-buffer layer-norm kernel, for applications that manage pipelines themselves. Bindings: slot 0
     * {@code x}, 1 {@code gamma}, 2 {@code beta}, 3 {@code y}, 4 {@code stats}; the parameter block holds the 32-bit
     * fields {@code rows, n, xOff, ldx, gOff, bOff, yOff, ldy, statsOff, flags, eps, gridX}, where {@code flags} is
     * 1 (gamma present) | 2 (beta) | 4 (stats).
     *
     * @return the kernel bundle
     */
    public static KernelBundle kernel() {
        return Kernels.bundle("layer_norm");
    }

    /**
     * Records layer normalization ({@code nn.Norms.layerNorm}) of {@code rows} rows of {@code n} floats:
     * {@code y = (x - mean)/σ·γ + β}, {@code σ = sqrt(var + eps)}, for row {@code r} of {@code x} from element
     * {@code xOff + r·ldx}, written from {@code yOff + r·ldy}. {@code gamma} and {@code beta} are shared by every row.
     * {@code y} may be {@code x} at the same offset and stride (in place). Nothing is recorded when {@code rows} or
     * {@code n} is 0, so {@code stats} is not written then (as on the CPU). All arguments are checked before anything
     * is recorded.
     *
     * @param encoder the encoder to record into
     * @param rows the number of rows, {@code >= 0}
     * @param x the input rows
     * @param xOff the element index of the first input row
     * @param ldx the stride between input rows, at least {@code n}
     * @param gamma the scale ({@code n} values), or {@code null} for 1
     * @param gOff the element index of the scale in {@code gamma}
     * @param beta the shift ({@code n} values), or {@code null} for 0
     * @param bOff the element index of the shift in {@code beta}
     * @param eps the term added to the variance, for example 1e-5
     * @param y receives the normalized rows; may be {@code x} at the same offset and stride
     * @param yOff the element index of the first output row
     * @param ldy the stride between output rows, at least {@code n}
     * @param n the row length, from 0 to {@link #MAX_ROW}
     * @param stats receives {@code mean} and {@code 1/σ} of each row ({@code 2·rows} values from {@code statsOff}),
     *        or {@code null}
     * @param statsOff the element index in {@code stats} of the first row's mean
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size is negative, {@code n} exceeds {@link #MAX_ROW}, an argument does
     *         not fit its buffer, an output shares a buffer with another argument other than {@code y} in place, or
     *         a required argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder layerNorm(GpuCommandEncoder encoder, int rows, GpuBuffer x, long xOff, int ldx,
            GpuBuffer gamma, long gOff, GpuBuffer beta, long bOff, float eps, GpuBuffer y, long yOff, int ldy, int n,
            GpuBuffer stats, long statsOff) {
        Kernels.notNull(encoder, "encoder");
        Rows.shape(rows, n, 0);
        Kernels.matrix(x, xOff, ldx, rows, n, "x");
        Kernels.matrix(y, yOff, ldy, rows, n, "y");
        optional(gamma, gOff, n, "gamma");
        optional(beta, bOff, n, "beta");
        optional(stats, statsOff, 2L * rows, "stats");
        boolean same = Rows.inPlace(y, yOff, ldy, "y", x, xOff, ldx, "x");
        Rows.exclusive(y, "y", gamma, beta, stats);
        Rows.exclusive(stats, "stats", x, gamma, beta);
        if (rows == 0 || n == 0) {
            return encoder;
        }
        int flags = (gamma != null ? GAMMA : 0) | (beta != null ? BETA : 0) | (stats != null ? STATS : 0);
        GpuBuffer g = gamma != null ? gamma : absentIn;
        GpuBuffer b = beta != null ? beta : absentIn;
        GpuBuffer s = stats != null ? stats : absentOut1;
        Dimensions groups = Kernels.grid(device.capabilities(), rows);
        if (same) {
            byte[] params = new Kernels.Params(PARAMS_LAYER_IN_PLACE)
                    .u32(rows).u32(n).u32(xOff).u32(ldx).u32(gOff(gamma, gOff)).u32(gOff(beta, bOff))
                    .u32(gOff(stats, statsOff)).u32(flags).f32(eps).u32(groups.x()).bytes();
            return Kernels.dispatch(encoder, pipelines[LAYER_IN_PLACE], Map.of(0, x, 1, g, 2, b, 3, s), params, groups);
        }
        byte[] params = new Kernels.Params(PARAMS_LAYER)
                .u32(rows).u32(n).u32(xOff).u32(ldx).u32(gOff(gamma, gOff)).u32(gOff(beta, bOff)).u32(yOff).u32(ldy)
                .u32(gOff(stats, statsOff)).u32(flags).f32(eps).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, pipelines[LAYER], Map.of(0, x, 1, g, 2, b, 3, y, 4, s), params, groups);
    }

    /**
     * Records the layer-norm backward pass ({@code nn.Norms.layerNormBackward}) of {@code rows} rows of {@code n}
     * floats: {@code dx = (1/σ)·(dx̂ - mean(dx̂) - x̂·mean(dx̂·x̂))}, {@code dx̂ = dy·γ}, {@code x̂ = (x - mean)/σ},
     * and {@code dγ += dy·x̂}, {@code dβ += dy} accumulated over the rows in row order. Row {@code r} of each matrix
     * starts at its offset plus {@code r} times its stride; its {@code mean, 1/σ} are at {@code statsOff + 2r}.
     * {@code dx} may be {@code x} or {@code dy} at the same offset and stride (in place), but not both. Unlike the
     * CPU method, no scratch is needed. Nothing is recorded when {@code rows} or {@code n} is 0. All arguments are
     * checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param rows the number of rows, {@code >= 0}
     * @param x the forward input
     * @param xOff the element index of the first row of {@code x}
     * @param ldx the stride between rows of {@code x}, at least {@code n}
     * @param gamma the scale used in the forward pass, or {@code null} for 1
     * @param gOff the element index of the scale in {@code gamma}
     * @param stats the {@code mean, 1/σ} pairs the forward kernel wrote ({@code 2·rows} values, checked even when
     *        {@code n = 0})
     * @param statsOff the element index in {@code stats} of the first row's mean
     * @param dy the gradient of the output
     * @param dyOff the element index of the first row of {@code dy}
     * @param lddy the stride between rows of {@code dy}, at least {@code n}
     * @param dx receives the gradient of the input; may be {@code x} or {@code dy} at the same offset and stride
     * @param dxOff the element index of the first row of {@code dx}
     * @param lddx the stride between rows of {@code dx}, at least {@code n}
     * @param dGamma accumulates the scale gradient ({@code n} values), or {@code null}
     * @param dgOff the element index in {@code dGamma}
     * @param dBeta accumulates the shift gradient ({@code n} values), or {@code null}
     * @param dbOff the element index in {@code dBeta}
     * @param n the row length, from 0 to {@link #MAX_ROW}
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size is negative, {@code n} exceeds {@link #MAX_ROW}, an argument does
     *         not fit its buffer, an output shares a buffer with another argument other than {@code dx} in place, or
     *         a required argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if a dispatch exceeds the device's limits
     */
    public GpuCommandEncoder layerNormBackward(GpuCommandEncoder encoder, int rows, GpuBuffer x, long xOff, int ldx,
            GpuBuffer gamma, long gOff, GpuBuffer stats, long statsOff, GpuBuffer dy, long dyOff, int lddy,
            GpuBuffer dx, long dxOff, int lddx, GpuBuffer dGamma, long dgOff, GpuBuffer dBeta, long dbOff, int n) {
        return backward(encoder, rows, x, xOff, ldx, gamma, gOff, stats, statsOff, dy, dyOff, lddy, dx, dxOff, lddx,
                dGamma, dgOff, dBeta, dbOff, n, false);
    }

    /**
     * Records RMS normalization ({@code nn.Norms.rmsNorm}) of {@code rows} rows of {@code n} floats:
     * {@code y = x / rms·γ}, {@code rms = sqrt(mean(x²) + eps)}, for row {@code r} of {@code x} from element
     * {@code xOff + r·ldx}, written from {@code yOff + r·ldy}. {@code gamma} is shared by every row. {@code y} may be
     * {@code x} at the same offset and stride (in place). Nothing is recorded when {@code rows} or {@code n} is 0, so
     * {@code stats} is not written then (as on the CPU). All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param rows the number of rows, {@code >= 0}
     * @param x the input rows
     * @param xOff the element index of the first input row
     * @param ldx the stride between input rows, at least {@code n}
     * @param gamma the scale ({@code n} values), or {@code null} for 1
     * @param gOff the element index of the scale in {@code gamma}
     * @param eps the term added to the mean square, for example 1e-6
     * @param y receives the normalized rows; may be {@code x} at the same offset and stride
     * @param yOff the element index of the first output row
     * @param ldy the stride between output rows, at least {@code n}
     * @param n the row length, from 0 to {@link #MAX_ROW}
     * @param stats receives {@code 1/rms} of each row ({@code rows} values from {@code statsOff}), or {@code null}
     * @param statsOff the element index in {@code stats} of the first row's value
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size is negative, {@code n} exceeds {@link #MAX_ROW}, an argument does
     *         not fit its buffer, an output shares a buffer with another argument other than {@code y} in place, or
     *         a required argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder rmsNorm(GpuCommandEncoder encoder, int rows, GpuBuffer x, long xOff, int ldx,
            GpuBuffer gamma, long gOff, float eps, GpuBuffer y, long yOff, int ldy, int n, GpuBuffer stats,
            long statsOff) {
        Kernels.notNull(encoder, "encoder");
        Rows.shape(rows, n, 0);
        Kernels.matrix(x, xOff, ldx, rows, n, "x");
        Kernels.matrix(y, yOff, ldy, rows, n, "y");
        optional(gamma, gOff, n, "gamma");
        optional(stats, statsOff, rows, "stats");
        boolean same = Rows.inPlace(y, yOff, ldy, "y", x, xOff, ldx, "x");
        Rows.exclusive(y, "y", gamma, stats);
        Rows.exclusive(stats, "stats", x, gamma);
        if (rows == 0 || n == 0) {
            return encoder;
        }
        int flags = (gamma != null ? GAMMA : 0) | (stats != null ? STATS : 0);
        GpuBuffer g = gamma != null ? gamma : absentIn;
        GpuBuffer s = stats != null ? stats : absentOut1;
        Dimensions groups = Kernels.grid(device.capabilities(), rows);
        if (same) {
            byte[] params = new Kernels.Params(PARAMS_RMS_IN_PLACE)
                    .u32(rows).u32(n).u32(xOff).u32(ldx).u32(gOff(gamma, gOff)).u32(gOff(stats, statsOff))
                    .u32(flags).f32(eps).u32(groups.x()).bytes();
            return Kernels.dispatch(encoder, pipelines[RMS_IN_PLACE], Map.of(0, x, 1, g, 2, s), params, groups);
        }
        byte[] params = new Kernels.Params(PARAMS_RMS)
                .u32(rows).u32(n).u32(xOff).u32(ldx).u32(gOff(gamma, gOff)).u32(yOff).u32(ldy)
                .u32(gOff(stats, statsOff)).u32(flags).f32(eps).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, pipelines[RMS], Map.of(0, x, 1, g, 2, y, 3, s), params, groups);
    }

    /**
     * Records the RMS-norm backward pass ({@code nn.Norms.rmsNormBackward}) of {@code rows} rows of {@code n}
     * floats: {@code dx = (1/rms)·(dx̂ - x̂·mean(dx̂·x̂))}, {@code dx̂ = dy·γ}, {@code x̂ = x/rms}, and
     * {@code dγ += dy·x̂} accumulated over the rows in row order. Row {@code r} of each matrix starts at its offset
     * plus {@code r} times its stride; its {@code 1/rms} is at {@code statsOff + r}. {@code dx} may be {@code x} or
     * {@code dy} at the same offset and stride (in place), but not both. Unlike the CPU method, no scratch is needed.
     * Nothing is recorded when {@code rows} or {@code n} is 0. All arguments are checked before anything is
     * recorded.
     *
     * @param encoder the encoder to record into
     * @param rows the number of rows, {@code >= 0}
     * @param x the forward input
     * @param xOff the element index of the first row of {@code x}
     * @param ldx the stride between rows of {@code x}, at least {@code n}
     * @param gamma the scale used in the forward pass, or {@code null} for 1
     * @param gOff the element index of the scale in {@code gamma}
     * @param stats the {@code 1/rms} values the forward kernel wrote ({@code rows} values, checked even when
     *        {@code n = 0})
     * @param statsOff the element index in {@code stats} of the first row's value
     * @param dy the gradient of the output
     * @param dyOff the element index of the first row of {@code dy}
     * @param lddy the stride between rows of {@code dy}, at least {@code n}
     * @param dx receives the gradient of the input; may be {@code x} or {@code dy} at the same offset and stride
     * @param dxOff the element index of the first row of {@code dx}
     * @param lddx the stride between rows of {@code dx}, at least {@code n}
     * @param dGamma accumulates the scale gradient ({@code n} values), or {@code null}
     * @param dgOff the element index in {@code dGamma}
     * @param n the row length, from 0 to {@link #MAX_ROW}
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size is negative, {@code n} exceeds {@link #MAX_ROW}, an argument does
     *         not fit its buffer, an output shares a buffer with another argument other than {@code dx} in place, or
     *         a required argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if a dispatch exceeds the device's limits
     */
    public GpuCommandEncoder rmsNormBackward(GpuCommandEncoder encoder, int rows, GpuBuffer x, long xOff, int ldx,
            GpuBuffer gamma, long gOff, GpuBuffer stats, long statsOff, GpuBuffer dy, long dyOff, int lddy,
            GpuBuffer dx, long dxOff, int lddx, GpuBuffer dGamma, long dgOff, int n) {
        return backward(encoder, rows, x, xOff, ldx, gamma, gOff, stats, statsOff, dy, dyOff, lddy, dx, dxOff, lddx,
                dGamma, dgOff, null, 0, n, true);
    }

    private GpuCommandEncoder backward(GpuCommandEncoder encoder, int rows, GpuBuffer x, long xOff, int ldx,
            GpuBuffer gamma, long gOff, GpuBuffer stats, long statsOff, GpuBuffer dy, long dyOff, int lddy,
            GpuBuffer dx, long dxOff, int lddx, GpuBuffer dGamma, long dgOff, GpuBuffer dBeta, long dbOff, int n,
            boolean rms) {
        Kernels.notNull(encoder, "encoder");
        Rows.shape(rows, n, 0);
        Kernels.matrix(x, xOff, ldx, rows, n, "x");
        Kernels.matrix(dy, dyOff, lddy, rows, n, "dy");
        Kernels.matrix(dx, dxOff, lddx, rows, n, "dx");
        optional(gamma, gOff, n, "gamma");
        optional(dGamma, dgOff, n, "dGamma");
        optional(dBeta, dbOff, n, "dBeta");
        Kernels.range(stats, statsOff, rms ? rows : 2L * rows, "stats");
        boolean onX = Rows.inPlace(dx, dxOff, lddx, "dx", x, xOff, ldx, "x");
        boolean onDy = Rows.inPlace(dx, dxOff, lddx, "dx", dy, dyOff, lddy, "dy");
        if (onX && onDy) {
            throw new IllegalArgumentException("dx may share a buffer with only one of x and dy");
        }
        Rows.exclusive(dx, "dx", gamma, stats, dGamma, dBeta);
        Rows.exclusive(dGamma, "dGamma", x, gamma, stats, dy, dx, dBeta);
        Rows.exclusive(dBeta, "dBeta", x, gamma, stats, dy, dx, dGamma);
        if (rows == 0 || n == 0) {
            return encoder;
        }
        int rmsFlag = rms ? RMS_FLAG : 0;
        if (dGamma != null || dBeta != null) {
            Dimensions columns = Kernels.grid(device.capabilities(), (n + PARAM_GROUP - 1) / PARAM_GROUP);
            int flags = (dGamma != null ? GAMMA : 0) | (dBeta != null ? BETA : 0) | rmsFlag;
            byte[] params = new Kernels.Params(PARAMS_PARAM_GRADS)
                    .u32(rows).u32(n).u32(xOff).u32(ldx).u32(statsOff).u32(dyOff).u32(lddy).u32(gOff(dGamma, dgOff))
                    .u32(gOff(dBeta, dbOff)).u32(flags).u32(columns.x()).bytes();
            Kernels.dispatch(encoder, pipelines[PARAM_GRADS], Map.of(0, x, 1, stats, 2, dy, 3,
                    dGamma != null ? dGamma : absentOut1, 4, dBeta != null ? dBeta : absentOut2), params, columns);
        }
        GpuBuffer g = gamma != null ? gamma : absentIn;
        int flags = (gamma != null ? GAMMA : 0) | rmsFlag;
        Dimensions groups = Kernels.grid(device.capabilities(), rows);
        if (onX || onDy) {
            byte[] params = new Kernels.Params(PARAMS_BACKWARD_IN_PLACE)
                    .u32(rows).u32(n).u32(dxOff).u32(lddx).u32(gOff(gamma, gOff)).u32(statsOff)
                    .u32(onX ? dyOff : xOff).u32(onX ? lddy : ldx).u32(flags | (onDy ? DX_IS_DY : 0))
                    .u32(groups.x()).bytes();
            return Kernels.dispatch(encoder, pipelines[BACKWARD_IN_PLACE], Map.of(0, dx, 1, g, 2, stats, 3, onX ? dy : x),
                    params, groups);
        }
        byte[] params = new Kernels.Params(PARAMS_BACKWARD)
                .u32(rows).u32(n).u32(xOff).u32(ldx).u32(gOff(gamma, gOff)).u32(statsOff).u32(dyOff).u32(lddy)
                .u32(dxOff).u32(lddx).u32(flags).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, pipelines[BACKWARD], Map.of(0, x, 1, g, 2, stats, 3, dy, 4, dx), params, groups);
    }

    /** Checks an optional vector of {@code count} values. */
    private static void optional(GpuBuffer b, long off, long count, String name) {
        if (b != null) {
            Kernels.range(b, off, count, name);
        }
    }

    /** The offset passed for an optional argument: 0 when it is absent (its placeholder is read at index 0). */
    private static long gOff(GpuBuffer b, long off) {
        return b != null ? off : 0;
    }

    /** Releases the pipelines and placeholders; recorded work keeps them alive until it completes. Idempotent. */
    @Override
    public void close() {
        try {
            Rows.close(pipelines);
        } finally {
            Rows.close(absentIn, absentOut1, absentOut2);
        }
    }
}
