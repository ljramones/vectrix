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
/**
 * Dense linear-algebra kernels for large vectors and matrices (BLAS levels 1 to 3), the numeric core for
 * machine-learning workloads: {@link org.dynamisengine.vectrix.blas.Blasf} and
 * {@link org.dynamisengine.vectrix.blas.Blasd} (dot, sum, norms, axpy, scal, iamax) and
 * {@link org.dynamisengine.vectrix.blas.Gemmf} and {@link org.dynamisengine.vectrix.blas.Gemmd} (matrix-matrix and
 * matrix-vector products).
 * <p>
 * Kernels work on arrays (the level-1 kernels also on {@link java.lang.foreign.MemorySegment}s), run
 * single-threaded on caller-chosen ranges, do not allocate, and give the same bits on every platform and with or
 * without the Vector API: every reduction has one documented evaluation order. Matrix products are defined element
 * by element as dot products in that order, so their results do not depend on batch size, tiling or thread split
 * ("batch invariance"). Matrices are row-major with an explicit row stride. On an Apple M4 Max with the Vector API
 * ({@code -simd} jar) one core reaches about 55 GFLOP/s in float and 26 in double; the scalar jar about 7.5. See
 * {@code docs/guides/machine-learning.md}.
 * <p>
 * <b>Contract.</b> Threading: the classes are stateless and safe to call from any number of threads on disjoint
 * outputs; to use several cores, split the output into disjoint ranges, each thread with its own scratch.
 * Allocation: no method allocates; {@code gemm} and transposed {@code gemv} use caller scratch sized by
 * {@code Gemmf.scratchSize}/{@code Gemmd.scratchSize}. Determinism: results are the same bits on every platform, in
 * either math mode, with or without SIMD and however the work is split; no transcendental functions are used.
 * Errors: negative sizes, bad offsets or strides, too-short arrays or segments and too-small scratch throw
 * {@link java.lang.IllegalArgumentException} before any element is written; NaN and infinities propagate as in
 * IEEE arithmetic. Precision and naming: classes ending in {@code f} work on {@code float}, those ending in
 * {@code d} on {@code double}; each pair has the same API and evaluation order.
 */
package org.dynamisengine.vectrix.blas;
