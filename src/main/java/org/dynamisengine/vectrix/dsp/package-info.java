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
 * Signal processing built on {@link org.dynamisengine.vectrix.fft}: window functions, FIR and IIR filter design,
 * spectral analysis, short-time and discrete cosine transforms, resampling, wavelets, Wiener and adaptive filtering,
 * and state estimation. Classes without a precision suffix work in double; some have float overloads.
 * <p>
 * Frequencies are in cycles per sample ({@code f / sampleRate}, between 0 and 0.5). Everything is deterministic and
 * allocation-free on the processing paths, and filter state is held by the caller, so one design can drive any
 * number of channels and threads. The Kalman, extended and unscented Kalman and particle filters estimate state from
 * noisy measurements. See {@code docs/guides/signal-processing.md} and {@code docs/guides/state-estimation.md}.
 * <p>
 * <b>Contract.</b> Threading: the static classes keep no state and are safe to call from any thread; plans
 * ({@link org.dynamisengine.vectrix.dsp.Stftd}, {@link org.dynamisengine.vectrix.dsp.Dctd},
 * {@link org.dynamisengine.vectrix.dsp.Mdctd}, {@link org.dynamisengine.vectrix.dsp.Correlation},
 * {@link org.dynamisengine.vectrix.dsp.Resampler}, {@link org.dynamisengine.vectrix.dsp.Wavelet} and their float
 * twins) are immutable and shareable, while filter state and scratch belong to one thread at a time;
 * {@link org.dynamisengine.vectrix.dsp.ExtendedKalman} and {@link org.dynamisengine.vectrix.dsp.UnscentedKalman}
 * instances own work arrays, so use one per stream and thread. Allocation: no processing method allocates; making
 * a plan or a filter instance allocates, as do the setup methods {@code Windows.chebyshev},
 * {@code FirDesign.remez}, {@code Smoothers.savitzkyGolay}, {@code Wavelet.daubechies} and the filter copies
 * returned by {@code Wavelet.lowpass} and {@code Wavelet.highpass}. Determinism: every sum runs in a fixed order,
 * results do not depend on SIMD, and the streaming filters ({@code Biquad}, {@code Resampler}) give the same bits
 * however a stream is chunked; windows, twiddles and filter designs use {@code AccurateMath}, so they match across
 * platforms under {@code -Dvectrix.strictMath}, and the particle filter uses {@code ReproducibleMath}. Errors: bad
 * sizes, offsets and parameters throw {@link IllegalArgumentException}, in the methods that take offsets before
 * any element is written unless the method says otherwise; the Kalman filters, the Toeplitz solvers and the Wiener
 * helpers without offsets use their arrays from index 0 and check their lengths up front, while the adaptive and
 * particle filters rely on Java's bounds checks; a failed numerical precondition (a covariance that is not positive
 * definite, a DCT-IV of odd size) throws {@link IllegalStateException}, and a singular Toeplitz system or a FIR
 * design without gain throws {@link IllegalArgumentException} before any output is written. The Kalman filters
 * give the gating distance of a measurement without updating ({@code innovationDistance}), and
 * {@link org.dynamisengine.vectrix.dsp.InnovationFunction} lets the extended filter wrap angle innovations. Precision and naming: unsuffixed classes are double; {@code Stftf},
 * {@code Dctf} and {@code Mdctf} are the float twins of the {@code ...d} plans; the float overloads of
 * {@code Windows}, {@code Biquad}, {@code Goertzel} and {@code Resampler} compute in double and round only their
 * outputs, and {@code Smoothers.median} works on floats.
 */
package org.dynamisengine.vectrix.dsp;
