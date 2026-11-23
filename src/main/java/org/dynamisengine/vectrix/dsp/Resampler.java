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

/**
 * A streaming rational sample-rate converter: the rate is multiplied by {@code up/down} through a polyphase
 * Kaiser-windowed low-pass filter.
 * <p>
 * The prototype filter runs at {@code up} times the input rate with cutoff {@code 0.5/max(up, down)} cycles per
 * sample there (the lower of the two Nyquist frequencies), {@code 2·zeroCrossings·max(up, down) + 1} taps, the
 * Kaiser window for the requested stop-band attenuation, and gain {@code up}, so a constant input stays constant.
 * Each output is a dot product of {@code zeroCrossings·2·max(up, down)/up} taps with input samples, accumulated
 * with fused multiply-adds in double (also for float samples). The output lags the input by the filter's group
 * delay, {@link #delay()} output samples.
 * <p>
 * {@link #process} consumes any number of input samples and writes the outputs they complete (at most
 * {@link #maxOutput(int)}); the stream position and input history are kept in a caller-held state array of
 * {@link #stateSize()} doubles, zero at the start of a stream. Any chunking gives the same output bits. A plan is
 * immutable and shareable; calls do not allocate.
 *
 * @since 2.5.0
 */
public final class Resampler {

    private final int up;
    private final int down;
    private final int taps;
    private final int perPhase;
    private final double[] h;
    private final double delay;

    private Resampler(int up, int down, int zeroCrossings, double attenuationDb) {
        int g = gcd(up, down);
        this.up = up / g;
        this.down = down / g;
        int mx = java.lang.Math.max(this.up, this.down);
        int n = 2 * zeroCrossings * mx + 1;
        perPhase = (n + this.up - 1) / this.up;
        taps = perPhase * this.up; // padded with zeros to whole phases
        double[] proto = new double[n];
        double[] w = new double[n];
        Windows.kaiser(w, 0, n, FirDesign.kaiserBeta(attenuationDb), false);
        if (mx == 1) {
            java.util.Arrays.fill(proto, 0.0);
            proto[n / 2] = 1.0; // up == down: the identity
        } else {
            FirDesign.lowpass(proto, 0, n, 0.5 / mx, w, 0);
        }
        // polyphase layout: phase p holds proto[p + j·up], j = 0 .. perPhase-1, times the gain up
        h = new double[taps];
        for (int p = 0; p < this.up; p++) {
            for (int j = 0; j < perPhase; j++) {
                int k = p + j * this.up;
                h[p * perPhase + j] = k < n ? proto[k] * this.up : 0.0;
            }
        }
        delay = (n - 1) / 2.0 / this.down;
    }

    /**
     * Creates a converter with 32 zero crossings per side and 100 dB stop-band attenuation.
     *
     * @param up the interpolation factor, at least 1
     * @param down the decimation factor, at least 1
     * @return the plan
     */
    public static Resampler plan(int up, int down) {
        return plan(up, down, 32, 100.0);
    }

    /**
     * Creates a converter.
     *
     * @param up the interpolation factor, at least 1
     * @param down the decimation factor, at least 1
     * @param zeroCrossings the filter half-length in zero crossings of the sinc, at least 1: longer filters have
     *        narrower transition bands
     * @param attenuationDb the stop-band attenuation in dB, {@code > 0}
     * @return the plan
     */
    public static Resampler plan(int up, int down, int zeroCrossings, double attenuationDb) {
        if (up < 1 || down < 1 || zeroCrossings < 1 || !(attenuationDb > 0)) {
            throw new IllegalArgumentException("need up, down, zeroCrossings >= 1 and attenuation > 0");
        }
        long n = 2L * zeroCrossings * java.lang.Math.max(up, down) + 1;
        if (n > (1 << 24)) {
            throw new IllegalArgumentException("filter too long: " + n + " taps");
        }
        return new Resampler(up, down, zeroCrossings, attenuationDb);
    }

    /** @return the interpolation factor, reduced by the common divisor */
    public int up() {
        return up;
    }

    /** @return the decimation factor, reduced by the common divisor */
    public int down() {
        return down;
    }

    /** @return the filter's group delay in output samples */
    public double delay() {
        return delay;
    }

    /** @return the doubles of state per stream: the input history and the stream position */
    public int stateSize() {
        return perPhase;
    }

    /** @return the largest number of outputs a call with {@code inCount} inputs can write */
    public int maxOutput(int inCount) {
        return (int) (((long) inCount * up) / down + 1);
    }

    /**
     * Converts {@code count} input samples.
     *
     * @param state {@link #stateSize()} doubles, zero at the start of a stream
     * @param out room for {@link #maxOutput(int) maxOutput(count)} samples; must not overlap {@code in}
     * @return the number of outputs written
     */
    public int process(double[] in, int inOff, int count, double[] out, int outOff, double[] state, int stateOff) {
        check(in == null ? -1 : in.length, inOff, count, "in");
        return run(in, null, inOff, count, out, null, outOff, state, stateOff);
    }

    /** Converts float samples, computing in double; see {@link #process(double[], int, int, double[], int, double[], int)}. */
    public int process(float[] in, int inOff, int count, float[] out, int outOff, double[] state, int stateOff) {
        check(in == null ? -1 : in.length, inOff, count, "in");
        return run(null, in, inOff, count, null, out, outOff, state, stateOff);
    }

    private int run(double[] ind, float[] inf, int inOff, int count, double[] outd, float[] outf, int outOff,
            double[] state, int stateOff) {
        int hist = perPhase - 1;
        check(state == null ? -1 : state.length, stateOff, perPhase, "state");
        check(outd != null ? outd.length : outf == null ? -1 : outf.length, outOff, maxOutput(count), "out");
        // state[0 .. hist) holds the last inputs, oldest first; state[hist] the next output's time on the
        // upsampled grid, relative to this block's first input (input i sits at time i·up)
        long t = (long) state[stateOff + hist];
        int produced = 0;
        while (true) {
            long i0 = java.lang.Math.floorDiv(t, up);
            if (i0 >= count) {
                break;
            }
            int p = (int) (t - i0 * up);
            int hb = p * perPhase;
            double acc = 0.0;
            for (int j = 0; j < perPhase; j++) {
                long i = i0 - j;
                double x;
                if (i >= 0) {
                    x = ind != null ? ind[inOff + (int) i] : inf[inOff + (int) i];
                } else {
                    x = state[stateOff + hist + (int) i]; // i >= -hist
                }
                acc = java.lang.Math.fma(h[hb + j], x, acc);
            }
            if (outd != null) {
                outd[outOff + produced] = acc;
            } else {
                outf[outOff + produced] = (float) acc;
            }
            produced++;
            t += down;
        }
        // keep the last hist inputs of (history ++ block)
        if (count >= hist) {
            for (int k = 0; k < hist; k++) {
                int i = count - hist + k;
                state[stateOff + k] = ind != null ? ind[inOff + i] : inf[inOff + i];
            }
        } else {
            System.arraycopy(state, stateOff + count, state, stateOff, hist - count);
            for (int k = 0; k < count; k++) {
                state[stateOff + hist - count + k] = ind != null ? ind[inOff + k] : inf[inOff + k];
            }
        }
        state[stateOff + hist] = t - (long) count * up;
        return produced;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    private static void check(int length, int off, int count, String name) {
        if (length < 0 || count < 0 || off < 0 || off > length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " values from offset " + off);
        }
    }
}
