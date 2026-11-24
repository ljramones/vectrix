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
 * The GPU twin of Vectrix {@code blas.Gemmf}: {@link #gemm} ({@code C = α·op(A)·op(B) + β·C}) and {@link #gemv}
 * ({@code y = α·op(A)·x + β·y}) over row-major float buffers.
 * <p>
 * Each element of {@code op(A)·op(B)} is Vectrix's {@code Blasf.dot} of a row of {@code op(A)} and a column of
 * {@code op(B)}, in the same order (256-element blocks of 8 interleaved lanes, added pairwise), so on an IEEE device
 * the result is the bits {@code Gemmf.gemm} computes (see the package comment for subnormals). The result is
 * {@code α·s} when {@code β == 0} ({@code C} is not read, so it may hold NaN), and {@code fma(α, s, β·c)} otherwise.
 * <p>
 * Two pipelines, compiled when the instance is created. The gemm kernel computes 16×16 tiles of {@code C}, one
 * element per thread; the gemv kernel computes 64 consecutive elements of {@code y} per group, one per thread.
 *
 * @since 3.1.0
 */
public final class GpuGemmf implements AutoCloseable {

    /** The parameter block, in the order the kernel declares it (checked against the compiled kernel). */
    static final String[] PARAMS = {"transA", "transB", "m", "n", "k", "alpha", "beta", "aOff", "lda", "bOff", "ldb",
        "cOff", "ldc"};

    /** The parameter block of the gemv kernel (checked against the compiled kernel). */
    static final String[] PARAMS_GEMV = {"transA", "m", "n", "alpha", "beta", "aOff", "lda", "xOff", "yOff", "gridX"};

    private static final int GEMV_ROWS = 64;

    private static final int TILE = 16;

    private final GpuDevice device;

    private final GpuComputePipeline pipeline;

    private final GpuComputePipeline gemvPipeline;

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipelines
     */
    public GpuGemmf(GpuDevice device) {
        pipeline = Kernels.pipeline(device, "gemm", new Dimensions(TILE, TILE, 1), PARAMS.length, Set.of("FP32"),
                Access.READ, Access.READ, Access.READ_WRITE);
        GpuComputePipeline second;
        try {
            second = Kernels.pipeline(device, "gemv", new Dimensions(GEMV_ROWS, 1, 1), PARAMS_GEMV.length,
                    Set.of("FP32"), Access.READ, Access.READ, Access.READ_WRITE);
        } catch (RuntimeException e) {
            pipeline.close();
            throw e;
        }
        gemvPipeline = second;
        this.device = device;
    }

    /**
     * The compiled kernel, for applications that manage pipelines themselves. Bindings: slot 0 {@code A}, 1
     * {@code B}, 2 {@code C}; the parameter block holds the fields of {@link #gemm} in order, as 32-bit values.
     *
     * @return the kernel bundle
     */
    public static KernelBundle kernel() {
        return Kernels.bundle("gemm");
    }

    /**
     * The compiled gemv kernel, for applications that manage pipelines themselves. Bindings: slot 0 {@code A}, 1
     * {@code x}, 2 {@code y}; the parameter block holds the 32-bit fields
     * {@code transA, m, n, alpha, beta, aOff, lda, xOff, yOff, gridX} (groups of 64 threads, one per element of
     * {@code y}, on a linear grid {@code gridX} groups wide).
     *
     * @return the kernel bundle
     * @since 3.2.0
     */
    public static KernelBundle gemvKernel() {
        return Kernels.bundle("gemv");
    }

    /**
     * Records {@code C = α·op(A)·op(B) + β·C}, where {@code op(A)} is {@code m×k}, {@code op(B)} is {@code k×n} and
     * {@code C} is {@code m×n}, all row-major with leading dimensions (row strides) {@code lda}, {@code ldb},
     * {@code ldc}, as in {@code Gemmf.gemm}. Offsets are in elements. {@code C} must be a different buffer from
     * {@code A} and {@code B}; {@code A} and {@code B} may be the same buffer. Nothing is recorded when {@code m} or
     * {@code n} is 0. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param transA whether {@code op(A)} is {@code Aᵀ}; {@code A} is then stored as {@code k×m}
     * @param transB whether {@code op(B)} is {@code Bᵀ}; {@code B} is then stored as {@code n×k}
     * @param m the number of rows of {@code C}, {@code >= 0}
     * @param n the number of columns of {@code C}, {@code >= 0}
     * @param k the inner dimension, {@code >= 0}; for {@code k == 0} the product is zero
     * @param alpha the scale of the product
     * @param a the buffer holding {@code A}
     * @param aOff the element index of {@code A[0][0]}
     * @param lda the row stride of {@code A}, at least its number of columns
     * @param b the buffer holding {@code B}
     * @param bOff the element index of {@code B[0][0]}
     * @param ldb the row stride of {@code B}, at least its number of columns
     * @param beta the scale of the previous {@code C}
     * @param c the buffer holding {@code C}; receives the result
     * @param cOff the element index of {@code C[0][0]}
     * @param ldc the row stride of {@code C}, at least {@code n}
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size is negative, a matrix does not fit its buffer with the given offset
     *         and stride (or needs element indices of 2^32 or more), {@code C} is {@code A} or {@code B}, or an
     *         argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder gemm(GpuCommandEncoder encoder, boolean transA, boolean transB, int m, int n, int k,
            float alpha, GpuBuffer a, long aOff, int lda, GpuBuffer b, long bOff, int ldb, float beta, GpuBuffer c,
            long cOff, int ldc) {
        Kernels.notNull(encoder, "encoder");
        if (m < 0 || n < 0 || k < 0) {
            throw new IllegalArgumentException("negative size: " + m + ", " + n + ", " + k);
        }
        Kernels.matrix(a, aOff, lda, transA ? k : m, transA ? m : k, "a");
        Kernels.matrix(b, bOff, ldb, transB ? n : k, transB ? k : n, "b");
        Kernels.matrix(c, cOff, ldc, m, n, "c");
        if (c == a || c == b) {
            throw new IllegalArgumentException("c must be a different buffer from a and b");
        }
        if (m == 0 || n == 0) {
            return encoder;
        }
        byte[] params = new Kernels.Params(PARAMS.length)
                .u32(transA ? 1 : 0).u32(transB ? 1 : 0).u32(m).u32(n).u32(k).f32(alpha).f32(beta)
                .u32(aOff).u32(lda).u32(bOff).u32(ldb).u32(cOff).u32(ldc).bytes();
        Dimensions groups = new Dimensions((n + TILE - 1) / TILE, (m + TILE - 1) / TILE, 1);
        return encoder.dispatch(pipeline, Map.of(0, a, 1, b, 2, c), params, groups);
    }

    /**
     * Records {@code y = α·op(A)·x + β·y}, where {@code op(A)} is {@code m×n} ({@code A} is stored as {@code n×m}
     * when transposed), row-major with row stride {@code lda}, and {@code x} and {@code y} are contiguous, as in
     * {@code Gemmf.gemv}. Each element is {@code Blasf.dot} of a row of {@code op(A)} with {@code x}, so on an IEEE
     * device the result is the bits {@code Gemmf.gemv} computes; {@code y} is not read when {@code β == 0}. Offsets
     * are in elements. {@code y} must be a different buffer from {@code A} and {@code x}; {@code A} and {@code x} may
     * be the same buffer. Nothing is recorded when {@code m} is 0; for {@code n == 0} the product is zero. All
     * arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param transA whether {@code op(A)} is {@code Aᵀ}
     * @param m the length of {@code y} and the number of rows of {@code op(A)}, {@code >= 0}
     * @param n the length of {@code x} and the number of columns of {@code op(A)}, {@code >= 0}
     * @param alpha the scale of the product
     * @param a the buffer holding {@code A}
     * @param aOff the element index of {@code A[0][0]}
     * @param lda the row stride of {@code A}, at least its number of columns
     * @param x the buffer holding {@code x}
     * @param xOff the element index of the first element of {@code x}
     * @param beta the scale of the previous {@code y}
     * @param y the buffer holding {@code y}; receives the result
     * @param yOff the element index of the first element of {@code y}
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size is negative, the matrix or a vector does not fit its buffer with
     *         the given offset and stride (or needs element indices of 2^32 or more), {@code y} is {@code A} or
     *         {@code x}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     * @since 3.2.0
     */
    public GpuCommandEncoder gemv(GpuCommandEncoder encoder, boolean transA, int m, int n, float alpha, GpuBuffer a,
            long aOff, int lda, GpuBuffer x, long xOff, float beta, GpuBuffer y, long yOff) {
        Kernels.notNull(encoder, "encoder");
        if (m < 0 || n < 0) {
            throw new IllegalArgumentException("negative size: " + m + ", " + n);
        }
        Kernels.matrix(a, aOff, lda, transA ? n : m, transA ? m : n, "a");
        Kernels.range(x, xOff, n, "x");
        Kernels.range(y, yOff, m, "y");
        if (y == a || y == x) {
            throw new IllegalArgumentException("y must be a different buffer from a and x");
        }
        if (m == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), (m + (long) GEMV_ROWS - 1) / GEMV_ROWS);
        byte[] params = new Kernels.Params(PARAMS_GEMV.length)
                .u32(transA ? 1 : 0).u32(m).u32(n).f32(alpha).f32(beta).u32(aOff).u32(lda).u32(xOff).u32(yOff)
                .u32(groups.x()).bytes();
        return encoder.dispatch(gemvPipeline, Map.of(0, a, 1, x, 2, y), params, groups);
    }

    /** Releases the pipelines; work already recorded keeps them alive until it completes. Idempotent. */
    @Override
    public void close() {
        try {
            pipeline.close();
        } finally {
            gemvPipeline.close();
        }
    }
}
