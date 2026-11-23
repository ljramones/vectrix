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
 * Hashing helpers for procedural generation, sampling, and spatial indexing: the PCG integer hash
 * ({@code PcgHash}), grid-cell hashes ({@code SpatialHash}) and the counter-based Philox4x32-10 random number
 * generator ({@code Philox}).
 * <p>
 * <b>Contract.</b> Threading: every method is a pure function of its arguments, safe to call from any thread; there
 * is no generator state. Allocation: no method allocates. Determinism: the hashes and the Philox words are integer
 * operations, the same bits on every platform; {@code Philox.fillNormal} uses {@code ReproducibleMath}, so its
 * results are also the same bits everywhere. Errors: the {@code Philox} fill methods check their offset and count
 * and throw {@link java.lang.IllegalArgumentException} before writing anything; {@code Philox.block} relies on
 * array bounds checks. Precision and naming: hashes are 32-bit {@code int}s; {@code Philox} produces 32-bit words
 * and float uniforms and normals.
 */
package org.dynamisengine.vectrix.hash;
