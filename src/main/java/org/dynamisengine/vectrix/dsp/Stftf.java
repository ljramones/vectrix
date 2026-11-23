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

import org.dynamisengine.vectrix.fft.RealFftf;

/**
 * A short-time Fourier transform plan over floats; the single-precision twin of {@link Stftd}.
 * <p>
 * Frame {@code t} covers samples {@code [t·hop, t·hop + n)}. {@link #analyze} windows one frame with the analysis
 * window and returns its half spectrum ({@link #spectrumLength()} doubles, as {@link RealFftf}). {@link #synthesize}
 * inverse-transforms a (possibly modified) spectrum, applies the synthesis window, adds it into a caller-held
 * overlap state of {@link #stateSize()} doubles, and emits the {@code hop} samples that are now complete.
 * <p>
 * The emitted samples are divided by the overlap sum {@code Σ wa[j + m·hop]·ws[j + m·hop]}, so analysis followed by
 * synthesis reconstructs the signal exactly (to rounding) for <i>any</i> pair of windows and hop whose overlap sum
 * has no zero, not only for COLA-compliant ones. Synthesizing frame {@code t} emits samples
 * {@code [t·hop, t·hop + hop)}; the first {@code n/hop - 1} frames lack earlier overlapping frames, so prepend
 * {@code n - hop} zeros to a signal to reconstruct it from its first sample.
 * <p>
 * A plan is immutable and shareable between threads; each stream needs its own state and scratch. Nothing
 * allocates after construction, and results are deterministic.
 *
 * @since 2.5.0
 */
public final class Stftf {

    private final int n;
    private final int hop;
    private final RealFftf fft;
    private final float[] analysis;
    private final float[] synthesis;
    private final float[] norm;
    private final int scratchSize;

    private Stftf(int n, int hop, float[] analysis, int aOff, float[] synthesis, int sOff) {
        this.n = n;
        this.hop = hop;
        fft = RealFftf.plan(n);
        this.analysis = java.util.Arrays.copyOfRange(analysis, aOff, aOff + n);
        this.synthesis = synthesis == null ? this.analysis : java.util.Arrays.copyOfRange(synthesis, sOff, sOff + n);
        norm = new float[hop];
        for (int j = 0; j < hop; j++) {
            double s = 0.0;
            for (int k = j; k < n; k += hop) {
                s += (double) this.analysis[k] * this.synthesis[k];
            }
            if (!(s != 0)) {
                throw new IllegalArgumentException("the windows' overlap sum is zero at phase " + j
                        + ": the frames cannot be reconstructed");
            }
            norm[j] = (float) s;
        }
        scratchSize = fft.spectrumLength() + fft.scratchSize();
    }

    /**
     * Creates a plan.
     *
     * @param n the frame length (the FFT size), at least 1
     * @param hop the frame advance, {@code 1 <= hop <= n}
     * @param analysisWindow {@code n} values from {@code analysisOff}, copied
     * @param analysisOff the offset of the analysis window
     * @param synthesisWindow {@code n} values from {@code synthesisOff}, copied; {@code null} reuses the analysis
     *        window
     * @param synthesisOff the offset of the synthesis window
     * @return the plan
     * @throws IllegalArgumentException if a size is out of range or the overlap sum has a zero
     */
    public static Stftf plan(int n, int hop, float[] analysisWindow, int analysisOff, float[] synthesisWindow,
            int synthesisOff) {
        if (n < 1 || hop < 1 || hop > n) {
            throw new IllegalArgumentException("need 1 <= hop <= n: n " + n + ", hop " + hop);
        }
        check(analysisWindow, analysisOff, n, "analysis window");
        if (synthesisWindow != null) {
            check(synthesisWindow, synthesisOff, n, "synthesis window");
        }
        return new Stftf(n, hop, analysisWindow, analysisOff, synthesisWindow, synthesisOff);
    }

    /** @return the frame length */
    public int frameLength() {
        return n;
    }

    /** @return the frame advance */
    public int hop() {
        return hop;
    }

    /** @return the floats in one frame's half spectrum: {@code 2·(n/2 + 1)} */
    public int spectrumLength() {
        return fft.spectrumLength();
    }

    /** @return the floats of overlap state a synthesis stream carries: {@code n} */
    public int stateSize() {
        return n;
    }

    /** @return the floats of scratch a call needs */
    public int scratchSize() {
        return scratchSize;
    }

    /**
     * Windows the frame {@code in[inOff .. inOff + n)} and transforms it.
     *
     * @param out receives {@link #spectrumLength()} floats; must not overlap {@code in}
     */
    public void analyze(float[] in, int inOff, float[] out, int outOff, float[] scratch, int scratchOff) {
        check(in, inOff, n, "in");
        check(out, outOff, spectrumLength(), "out");
        check(scratch, scratchOff, scratchSize, "scratch");
        for (int k = 0; k < n; k++) {
            out[outOff + k] = in[inOff + k] * analysis[k];
        }
        fft.forward(out, outOff, out, outOff, scratch, scratchOff);
    }

    /**
     * Synthesizes one frame: inverse-transforms {@code spectrum}, windows it, adds it into {@code state}, and
     * writes the {@code hop} completed samples to {@code out}. {@code spectrum} is not modified.
     *
     * @param spectrum the frame's half spectrum, {@link #spectrumLength()} floats
     * @param out receives {@code hop} samples
     * @param state the {@link #stateSize()} floats of overlap state, zero at the start of a stream
     */
    public void synthesize(float[] spectrum, int spectrumOff, float[] out, int outOff, float[] state,
            int stateOff, float[] scratch, int scratchOff) {
        check(spectrum, spectrumOff, spectrumLength(), "spectrum");
        check(out, outOff, hop, "out");
        check(state, stateOff, n, "state");
        check(scratch, scratchOff, scratchSize, "scratch");
        int buf = scratchOff;
        int work = scratchOff + spectrumLength();
        System.arraycopy(spectrum, spectrumOff, scratch, buf, spectrumLength());
        fft.inverse(scratch, buf, scratch, buf, scratch, work);
        for (int k = 0; k < n; k++) {
            state[stateOff + k] += scratch[buf + k] * synthesis[k];
        }
        for (int j = 0; j < hop; j++) {
            out[outOff + j] = state[stateOff + j] / norm[j];
        }
        System.arraycopy(state, stateOff + hop, state, stateOff, n - hop);
        java.util.Arrays.fill(state, stateOff + n - hop, stateOff + n, 0.0f);
    }

    private static void check(float[] a, int off, int count, String name) {
        if (a == null || off < 0 || off > a.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " floats from offset " + off);
        }
    }
}
