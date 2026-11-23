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
 */
package org.dynamisengine.vectrix.dsp;
