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
 * scalar ones. The original radix-2 classes {@code FFT1d}, {@code FFT1f}, {@code Convolutiond} and
 * {@code Convolutionf} are deprecated. See {@code docs/guides/signal-processing.md}.
 */
package org.dynamisengine.vectrix.fft;
