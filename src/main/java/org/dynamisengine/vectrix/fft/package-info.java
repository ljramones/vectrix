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
 * Fourier transforms and FFT convolution.
 * <ul>
 * <li>{@link org.dynamisengine.vectrix.fft.ComplexFftd} / {@link org.dynamisengine.vectrix.fft.RealFftd}: planned
 * 1D transforms of any size (mixed radix 2/3/4/5, direct prime butterflies, Bluestein), complex and real input.</li>
 * <li>{@link org.dynamisengine.vectrix.fft.ComplexFft2d} / {@link org.dynamisengine.vectrix.fft.RealFft2d}: 2D
 * transforms.</li>
 * <li>{@link org.dynamisengine.vectrix.fft.BlockConvolverd}: streaming FIR filtering, direct or overlap-add.</li>
 * </ul>
 * Each has a float twin ({@code ...f}). Plans are immutable and shareable between threads; calls take caller-owned
 * scratch, never allocate, and are deterministic. Twiddle factors are computed with exact argument reduction, so
 * the error grows only with {@code log n}. The Vector API paths of the {@code -simd} jar give the same bits as the
 * scalar ones. See {@code docs/guides/signal-processing.md}.
 * <p>
 * <b>Contract.</b> Threading: plans are immutable and can be shared by any number of threads; scratch arrays and
 * {@code BlockConvolverd}/{@code BlockConvolverf} state belong to one thread at a time. Allocation: creating a plan
 * allocates its tables; transforms and {@code process}/{@code flush} calls do not allocate. Determinism: results do not depend on SIMD or thread count, but a block convolver's output bits depend on
 * where its blocks start; twiddles use {@code AccurateMath}, so they match across platforms under
 * {@code -Dvectrix.strictMath=true}. Errors: invalid sizes and offsets, too-short arrays or segments and too-small
 * scratch throw {@link java.lang.IllegalArgumentException} before any element is written; NaN and infinities
 * propagate through the arithmetic. Precision and naming: classes ending in {@code d} are double, their {@code f}
 * twins float; {@code ComplexFft2d} and {@code RealFft2d} are the two-dimensional double transforms. Complex data
 * is interleaved (real, imaginary) and 2D data row-major.
 */
package org.dynamisengine.vectrix.fft;
