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
 * The SIMD switch and Vector API detection ({@link org.dynamisengine.vectrix.simd.SimdSupport}) for the kernels
 * that have Vector API paths and scalar fallbacks.
 * <p>
 * <b>Contract.</b> Threading: {@code SimdSupport} is safe from any thread; {@code setEnabled} writes a volatile flag
 * that every thread sees on its next kernel call. Allocation: {@code SimdSupport} allocates nothing after class
 * initialization. Determinism: the
 * switch never changes results, only speed, because every SIMD path gives the same bits as its scalar path.
 * Errors: none; a missing Vector API module is reported as {@code SCALAR}, not thrown.
 */
package org.dynamisengine.vectrix.simd;
