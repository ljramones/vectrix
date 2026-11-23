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
package org.dynamisengine.vectrix.fft;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * A precomputed plan for the discrete Fourier transform of {@code n} real floats, for any {@code n}; the
 * single-precision twin of {@link RealFftd}.
 * <p>
 * The spectrum of a real sequence is Hermitian ({@code X[n-k] = conj(X[k])}), so only its first
 * {@code n/2 + 1} complex values are stored, interleaved: {@link #spectrumLength()} floats. {@link #forward}
 * computes {@code X[k] = Σ x[j]·exp(-2πi jk/n)}, unscaled; {@link #inverse} divides by {@code n}, so
 * {@code inverse(forward(x)) = x}. The inverse treats the imaginary parts of {@code X[0]} and, for even
 * {@code n}, {@code X[n/2]} as zero, as they are for every real sequence.
 * <p>
 * Even sizes use one complex transform of size {@code n/2} and are about twice as fast as {@link ComplexFftf};
 * odd sizes use a complex transform of size {@code n}. Accuracy, thread safety, scratch handling and
 * determinism are as for {@link ComplexFftf}: plans are immutable and shareable, calls take caller-owned scratch
 * and do not allocate.
 *
 * @since 2.5.0
 */
public final class RealFftf {

    private final int n;
    private final ComplexFftf complex;
    private final float[] roots;
    private final int scratchSize;

    private RealFftf(int n) {
        this.n = n;
        if ((n & 1) == 0) {
            int h = n / 2;
            complex = ComplexFftf.plan(h);
            roots = FftMath.toFloat(FftMath.roots(h / 2 + 1, n));
            scratchSize = complex.scratchSize();
        } else {
            complex = ComplexFftf.plan(n);
            roots = null;
            scratchSize = 2 * n + complex.scratchSize();
        }
    }

    /**
     * Creates a plan for {@code n} real values.
     *
     * @param n the transform size, {@code 1 <= n <= ComplexFftf.MAX_SIZE}
     * @return the plan
     * @throws IllegalArgumentException if {@code n} is out of range
     */
    public static RealFftf plan(int n) {
        if (n < 1 || n > ComplexFftf.MAX_SIZE) {
            throw new IllegalArgumentException("size must be in [1, 2^28]: " + n);
        }
        return new RealFftf(n);
    }

    /** @return the number of real values transformed */
    public int size() {
        return n;
    }

    /** @return the number of floats in the half spectrum: {@code 2·(n/2 + 1)} */
    public int spectrumLength() {
        return 2 * (n / 2 + 1);
    }

    /** @return the number of floats the scratch array needs for the array overloads */
    public int scratchSize() {
        return scratchSize;
    }

    /** @return the number of floats the scratch array needs for the {@link MemorySegment} overloads */
    public long segmentScratchSize() {
        return (long) spectrumLength() + scratchSize;
    }

    /**
     * Transforms {@code n} real values into the half spectrum. {@code in} is not modified, unless it is
     * {@code out}: the two may be the same array at the same offset, with room for {@link #spectrumLength()}
     * floats.
     *
     * @param in the real values
     * @param inOff the offset of the first value
     * @param out receives the {@link #spectrumLength()} floats of the half spectrum
     * @param outOff the offset of the first output
     * @param scratch caller-owned working memory of at least {@link #scratchSize()} floats
     * @param scratchOff the offset into {@code scratch}
     */
    public void forward(float[] in, int inOff, float[] out, int outOff, float[] scratch, int scratchOff) {
        check(in, inOff, n, "in");
        check(out, outOff, spectrumLength(), "out");
        check(scratch, scratchOff, scratchSize, "scratch");
        if ((n & 1) == 0) {
            forwardEven(in, inOff, out, outOff, scratch, scratchOff);
        } else {
            int c = scratchOff;
            for (int j = 0; j < n; j++) {
                scratch[c + 2 * j] = in[inOff + j];
                scratch[c + 2 * j + 1] = 0.0f;
            }
            complex.run(scratch, c, scratch, c + 2 * n, false);
            System.arraycopy(scratch, c, out, outOff, spectrumLength());
            out[outOff + 1] = 0.0f;
        }
    }

    /**
     * Inverse-transforms a half spectrum into {@code n} real values, dividing by {@code n}. {@code in} is not
     * modified, unless it is {@code out}: the two may be the same array at the same offset.
     *
     * @param in the {@link #spectrumLength()} floats of the half spectrum
     * @param inOff the offset of the first value
     * @param out receives the {@code n} real values
     * @param outOff the offset of the first output
     * @param scratch caller-owned working memory of at least {@link #scratchSize()} floats
     * @param scratchOff the offset into {@code scratch}
     */
    public void inverse(float[] in, int inOff, float[] out, int outOff, float[] scratch, int scratchOff) {
        check(in, inOff, spectrumLength(), "in");
        check(out, outOff, n, "out");
        check(scratch, scratchOff, scratchSize, "scratch");
        if ((n & 1) == 0) {
            inverseEven(in, inOff, out, outOff, scratch, scratchOff);
        } else {
            int c = scratchOff;
            int h = n / 2;
            scratch[c] = in[inOff];
            scratch[c + 1] = 0.0f;
            for (int k = 1; k <= h; k++) {
                float xr = in[inOff + 2 * k];
                float xi = in[inOff + 2 * k + 1];
                scratch[c + 2 * k] = xr;
                scratch[c + 2 * k + 1] = xi;
                scratch[c + 2 * (n - k)] = xr;
                scratch[c + 2 * (n - k) + 1] = -xi;
            }
            complex.run(scratch, c, scratch, c + 2 * n, true);
            double s = n;
            for (int j = 0; j < n; j++) {
                out[outOff + j] = (float) (scratch[c + 2 * j] / s);
            }
        }
    }

    /**
     * Transforms {@code n} native-order floats at the start of {@code in} into the half spectrum at the start
     * of {@code out}. The segments may be the same.
     *
     * @param in the real values, at least {@code 4n} bytes
     * @param out receives the half spectrum, at least {@code 4·spectrumLength()} bytes
     * @param scratch caller-owned working memory of at least {@link #segmentScratchSize()} floats
     */
    public void forward(MemorySegment in, MemorySegment out, float[] scratch) {
        checkSegments(in, n, out, spectrumLength(), scratch);
        MemorySegment.copy(in, ValueLayout.JAVA_FLOAT_UNALIGNED, 0, scratch, 0, n);
        int sp = spectrumLength();
        forward(scratch, 0, scratch, 0, scratch, sp);
        MemorySegment.copy(scratch, 0, out, ValueLayout.JAVA_FLOAT_UNALIGNED, 0, sp);
    }

    /**
     * Inverse-transforms the half spectrum at the start of {@code in} into {@code n} native-order floats at the
     * start of {@code out}, dividing by {@code n}. The segments may be the same.
     *
     * @param in the half spectrum, at least {@code 4·spectrumLength()} bytes
     * @param out receives the real values, at least {@code 4n} bytes
     * @param scratch caller-owned working memory of at least {@link #segmentScratchSize()} floats
     */
    public void inverse(MemorySegment in, MemorySegment out, float[] scratch) {
        checkSegments(in, spectrumLength(), out, n, scratch);
        int sp = spectrumLength();
        MemorySegment.copy(in, ValueLayout.JAVA_FLOAT_UNALIGNED, 0, scratch, 0, sp);
        inverse(scratch, 0, scratch, 0, scratch, sp);
        MemorySegment.copy(scratch, 0, out, ValueLayout.JAVA_FLOAT_UNALIGNED, 0, n);
    }

    private void checkSegments(MemorySegment in, int inCount, MemorySegment out, int outCount, float[] scratch) {
        if (in == null || in.byteSize() < 4L * inCount) {
            throw new IllegalArgumentException("in must hold " + inCount + " floats");
        }
        if (out == null || out.byteSize() < 4L * outCount) {
            throw new IllegalArgumentException("out must hold " + outCount + " floats");
        }
        if (scratch == null || scratch.length < segmentScratchSize()) {
            throw new IllegalArgumentException("scratch must hold " + segmentScratchSize() + " floats");
        }
    }

    private static void check(float[] a, int off, int count, String name) {
        if (a == null || off < 0 || off > a.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " floats from offset " + off);
        }
    }

    private void forwardEven(float[] in, int inOff, float[] out, int o, float[] scratch, int scratchOff) {
        int h = n / 2;
        // z_k = x_2k + i·x_2k+1 is the input itself, read as h complex values
        System.arraycopy(in, inOff, out, o, n);
        complex.run(out, o, scratch, scratchOff, false);
        float z0r = out[o];
        float z0i = out[o + 1];
        out[o] = z0r + z0i;
        out[o + 1] = 0.0f;
        out[o + n] = z0r - z0i;
        out[o + n + 1] = 0.0f;
        for (int k = 1; 2 * k <= h; k++) {
            int a = o + 2 * k;
            int b = o + 2 * (h - k);
            float zar = out[a];
            float zai = out[a + 1];
            float zbr = out[b];
            float zbi = out[b + 1];
            // E = (Z_k + conj Z_(h-k))/2, O = -i·(Z_k - conj Z_(h-k))/2
            float er = 0.5f * (zar + zbr);
            float ei = 0.5f * (zai - zbi);
            float or = 0.5f * (zai + zbi);
            float oi = -0.5f * (zar - zbr);
            float wr = roots[2 * k];
            float wi = roots[2 * k + 1];
            float tr = wr * or - wi * oi;
            float ti = wr * oi + wi * or;
            // X_k = E + W^k·O, X_(h-k) = conj(E - W^k·O)
            out[a] = er + tr;
            out[a + 1] = ei + ti;
            out[b] = er - tr;
            out[b + 1] = -(ei - ti);
        }
    }

    private void inverseEven(float[] in, int inOff, float[] out, int o, float[] scratch, int scratchOff) {
        int h = n / 2;
        float x0 = in[inOff];
        float xh = in[inOff + n];
        for (int k = 1; 2 * k <= h; k++) {
            int a = inOff + 2 * k;
            int b = inOff + 2 * (h - k);
            float xar = in[a];
            float xai = in[a + 1];
            float xbr = in[b];
            float xbi = in[b + 1];
            // E = (X_k + conj X_(h-k))/2, O = conj(W^k)·(X_k - conj X_(h-k))/2, Z_k = E + i·O
            float er = 0.5f * (xar + xbr);
            float ei = 0.5f * (xai - xbi);
            float dr = 0.5f * (xar - xbr);
            float di = 0.5f * (xai + xbi);
            float wr = roots[2 * k];
            float wi = -roots[2 * k + 1];
            float or = dr * wr - di * wi;
            float oi = dr * wi + di * wr;
            // Z_k = E + i·O, Z_(h-k) = conj(E) + i·conj(O)
            out[o + 2 * k] = er - oi;
            out[o + 2 * k + 1] = ei + or;
            out[o + 2 * (h - k)] = er + oi;
            out[o + 2 * (h - k) + 1] = -ei + or;
        }
        // Z_0 = E_0 + i·O_0 from the real parts of X_0 and X_(n/2)
        out[o] = 0.5f * (x0 + xh);
        out[o + 1] = 0.5f * (x0 - xh);
        complex.run(out, o, scratch, scratchOff, true);
        if (FftMath.isPowerOfTwo(h)) {
            float s = 1.0f / h; // exact
            for (int i = o, e = o + n; i < e; i++) {
                out[i] *= s;
            }
        } else {
            double s = h;
            for (int i = o, e = o + n; i < e; i++) {
                out[i] = (float) (out[i] / s);
            }
        }
    }
}
