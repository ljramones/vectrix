/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix Contributors
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
 * Structure-of-arrays batch containers and operations: transforms ({@link org.dynamisengine.vectrix.soa.TransformSoA}),
 * dual quaternions and bounds stored one array per component, with batch kernels for hierarchy composition
 * ({@link org.dynamisengine.vectrix.soa.TransformKernels}) and mesh skinning
 * ({@link org.dynamisengine.vectrix.soa.SkinningKernels}).
 * <p>
 * <b>Contract.</b> Threading: the containers are mutable and not thread-safe; the kernels are static and stateless,
 * safe to call from any thread on separate outputs. Allocation: only the container constructors allocate; the
 * kernels do not. Determinism: {@code TransformKernels.composeBatch} gives the same bits on its scalar and Vector API
 * paths; the skinning kernels are scalar only. Errors: the kernels take a count and start at index 0; they check
 * the array sizes and joint indices first, so a negative count, a short array or a bad joint index throws
 * {@code IllegalArgumentException} before anything is written, and so does, in STRICT mode, a non-unit rotation
 * passed to {@code composeBatch}. Precision and naming: the containers are
 * float unless their name ends in {@code d}; the kernels work on the float containers.
 */
package org.dynamisengine.vectrix.soa;
