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
package org.dynamisengine.vectrix.dsp;

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.fft.ComplexFftf;
import org.dynamisengine.vectrix.fft.RealFftf;

/**
 * A plan for the discrete cosine transforms of {@code n} floats; the single-precision twin of {@link Dctd}, in FFTW's
 * unnormalized conventions:
 * <ul>
 * <li>DCT-II ({@link #forward}): {@code X[k] = 2·Σ x[j]·cos(π(j + 1/2)k/n)}, any {@code n};</li>
 * <li>its exact inverse ({@link #inverse}), the DCT-III divided by {@code 2n};</li>
 * <li>DCT-IV ({@link #forwardIV}): {@code X[k] = 2·Σ x[j]·cos(π(j + 1/2)(k + 1/2)/n)}, even {@code n}; it is its
 * own inverse up to {@code 2n} ({@link #inverseIV}).</li>
 * </ul>
 * For the orthonormal DCT-II scale {@code X[0]} by {@code 1/(2√n)} and the others by {@code 1/√(2n)}.
 * <p>
 * DCT-II and III use one real FFT of size {@code n} (Makhoul's reordering), DCT-IV one complex FFT of size
 * {@code n/2}; all twiddles are exactly reduced, so the error grows only with {@code log n}. Plans are immutable and
 * shareable; calls take caller scratch, do not allocate, and allow {@code in == out} at the same offset.
 *
 * @since 2.5.0
 */
public final class Dctf {

    private final int n;
    private final RealFftf real;
    private final float[] tw2;
    private final ComplexFftf half;
    private final float[] pre4;
    private final float[] post4;
    private final int scratchSize;

    private Dctf(int n) {
        this.n = n;
        real = RealFftf.plan(n);
        tw2 = new float[2 * n];
        for (int k = 0; k < n; k++) { // exp(iπk/(2n)) = k/(4n) turns
            tw2[2 * k] = (float) AccurateMath.cosTurns(k, 4L * n);
            tw2[2 * k + 1] = (float) AccurateMath.sinTurns(k, 4L * n);
        }
        int s = real.spectrumLength() + real.scratchSize();
        if ((n & 1) == 0) {
            int m = n / 2;
            half = ComplexFftf.plan(m);
            pre4 = new float[2 * m];
            post4 = new float[2 * m];
            for (int j = 0; j < m; j++) {
                pre4[2 * j] = (float) AccurateMath.cosTurns(4L * j + 1, 8L * n); // exp(-iπ(4j + 1)/(4n))
                pre4[2 * j + 1] = (float) -AccurateMath.sinTurns(4L * j + 1, 8L * n) + 0.0f;
                post4[2 * j] = (float) AccurateMath.cosTurns(j, 2L * n); // exp(-iπj/n)
                post4[2 * j + 1] = (float) -AccurateMath.sinTurns(j, 2L * n) + 0.0f;
            }
            s = java.lang.Math.max(s, n + half.scratchSize());
        } else {
            half = null;
            pre4 = null;
            post4 = null;
        }
        scratchSize = s;
    }

    /**
     * Creates a plan.
     *
     * @param n the transform size, {@code 1 <= n <= ComplexFftf.MAX_SIZE}
     * @return the plan
     */
    public static Dctf plan(int n) {
        if (n < 1 || n > ComplexFftf.MAX_SIZE) {
            throw new IllegalArgumentException("size must be in [1, 2^28]: " + n);
        }
        return new Dctf(n);
    }

    /** @return the transform size */
    public int size() {
        return n;
    }

    /** @return the floats of scratch a call needs */
    public int scratchSize() {
        return scratchSize;
    }

    /** DCT-II of {@code in[inOff .. inOff + n)} into {@code out}. */
    public void forward(float[] in, int inOff, float[] out, int outOff, float[] scratch, int scratchOff) {
        check(in, inOff, out, outOff, scratch, scratchOff);
        int b = scratchOff;
        // v = (x0, x2, x4, ..., x5, x3, x1)
        for (int j = 0; 2 * j < n; j++) {
            scratch[b + j] = in[inOff + 2 * j];
        }
        for (int j = 0; 2 * j + 1 < n; j++) {
            scratch[b + n - 1 - j] = in[inOff + 2 * j + 1];
        }
        real.forward(scratch, b, scratch, b, scratch, b + real.spectrumLength());
        for (int k = 0; k < n; k++) {
            float vr;
            float vi;
            if (2 * k <= n) {
                vr = scratch[b + 2 * k];
                vi = scratch[b + 2 * k + 1];
            } else { // V[k] = conj(V[n - k])
                vr = scratch[b + 2 * (n - k)];
                vi = -scratch[b + 2 * (n - k) + 1];
            }
            // X[k] = 2·Re(exp(-iπk/(2n))·V[k])
            out[outOff + k] = 2 * (tw2[2 * k] * vr + tw2[2 * k + 1] * vi);
        }
    }

    /** The inverse of {@link #forward}: the DCT-III divided by {@code 2n}. */
    public void inverse(float[] in, int inOff, float[] out, int outOff, float[] scratch, int scratchOff) {
        check(in, inOff, out, outOff, scratch, scratchOff);
        int b = scratchOff;
        // u[k] = exp(iπk/(2n))·(X[k] - i·X[n-k]), X[n] = 0; Hermitian, so only k <= n/2 is needed
        for (int k = 0; 2 * k <= n; k++) {
            float a = in[inOff + k];
            float bb = k == 0 ? 0.0f : in[inOff + n - k];
            float c = tw2[2 * k];
            float s = tw2[2 * k + 1];
            scratch[b + 2 * k] = c * a + s * bb;
            scratch[b + 2 * k + 1] = s * a - c * bb;
        }
        real.inverse(scratch, b, scratch, b, scratch, b + real.spectrumLength());
        // y[2j] = v[j]/2, y[2j + 1] = v[n - 1 - j]/2 (the real inverse already divided by n)
        int sp = real.spectrumLength();
        System.arraycopy(scratch, b, scratch, b + sp, n); // keep v while out may be in
        for (int j = 0; 2 * j < n; j++) {
            out[outOff + 2 * j] = 0.5f * scratch[b + sp + j];
        }
        for (int j = 0; 2 * j + 1 < n; j++) {
            out[outOff + 2 * j + 1] = 0.5f * scratch[b + sp + n - 1 - j];
        }
    }

    /** DCT-IV ({@code n} even) of {@code in} into {@code out}. */
    public void forwardIV(float[] in, int inOff, float[] out, int outOff, float[] scratch, int scratchOff) {
        check(in, inOff, out, outOff, scratch, scratchOff);
        if (half == null) {
            throw new IllegalStateException("the DCT-IV needs an even size: " + n);
        }
        int m = n / 2;
        int b = scratchOff;
        // t[j] = (x[2j] + i·x[n-1-2j])·exp(-iπ(4j + 1)/(4n))
        for (int j = 0; j < m; j++) {
            float xr = in[inOff + 2 * j];
            float xi = in[inOff + n - 1 - 2 * j];
            float wr = pre4[2 * j];
            float wi = pre4[2 * j + 1];
            scratch[b + 2 * j] = xr * wr - xi * wi;
            scratch[b + 2 * j + 1] = xr * wi + xi * wr;
        }
        half.forward(scratch, b, scratch, b + n);
        // c[k] = exp(-iπk/n)·T[k]; X[2k] = 2·Re c, X[n-1-2k] = -2·Im c
        for (int k = 0; k < m; k++) {
            float tr = scratch[b + 2 * k];
            float ti = scratch[b + 2 * k + 1];
            float wr = post4[2 * k];
            float wi = post4[2 * k + 1];
            out[outOff + 2 * k] = 2 * (tr * wr - ti * wi);
            out[outOff + n - 1 - 2 * k] = -2 * (tr * wi + ti * wr);
        }
    }

    /** The inverse of {@link #forwardIV}: the DCT-IV divided by {@code 2n}. */
    public void inverseIV(float[] in, int inOff, float[] out, int outOff, float[] scratch, int scratchOff) {
        forwardIV(in, inOff, out, outOff, scratch, scratchOff);
        double d = 2.0 * n;
        for (int k = 0; k < n; k++) {
            out[outOff + k] = (float) (out[outOff + k] / d);
        }
    }

    private void check(float[] in, int inOff, float[] out, int outOff, float[] scratch, int scratchOff) {
        range(in, inOff, n, "in");
        range(out, outOff, n, "out");
        range(scratch, scratchOff, scratchSize, "scratch");
    }

    private static void range(float[] a, int off, int count, String name) {
        if (a == null || off < 0 || off > a.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " floats from offset " + off);
        }
    }
}
