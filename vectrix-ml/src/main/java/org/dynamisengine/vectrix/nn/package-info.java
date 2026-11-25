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
 * Neural-network kernels over float arrays, forward and backward: activations, softmax, layer and RMS
 * normalization, attention, rotary position embeddings, 2D convolution and pooling, cross-entropy loss, optimizer
 * steps, token sampling and low-precision formats (bfloat16, FP8, int8 blocks).
 * <p>
 * The transcendental functions are vectrix's own ({@link org.dynamisengine.vectrix.core.ReproducibleMath}): series
 * with exact rational coefficients, evaluated with fused multiply-adds identically by the scalar and Vector API
 * paths, so every kernel gives the same bits on every platform and jar. Reductions use the order of
 * {@link org.dynamisengine.vectrix.blas.Blasf}. Kernels work on caller-chosen rows, keep no state and do not
 * allocate. Matrices are row-major with explicit strides; images are channel-major ({@code C×H×W}).
 * <p>
 * <b>Contract.</b> Threading: the kernels are static and stateless, and {@code Rope}, {@code Conv2d} and
 * {@code Pool2d} objects are immutable, so all are safe to call from any thread; scratch arrays belong to one call
 * at a time. Allocation: no kernel allocates; {@code Rope.plan} allocates its frequency table. Determinism: results
 * are the same bits on every platform, with or without SIMD, under any {@code MathMode} and with or without
 * {@code -Dvectrix.strictMath}, and do not depend on how rows, heads or batches are split across threads.
 * Errors: invalid sizes and offsets throw {@code IllegalArgumentException}, in most kernels before any element is
 * written; the exceptions are named in the method comments. Precision and naming: all data is float (FP8, bfloat16
 * and int8 are stored as codes); some intermediate values are computed in double and rounded once.
 */
package org.dynamisengine.vectrix.nn;
