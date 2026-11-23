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

/**
 * Runs cascades of second-order sections ({@link BiquadDesign}) over blocks of samples, and evaluates their
 * frequency response.
 * <p>
 * Each section runs in transposed direct form II, {@code y = b0·x + s1; s1 = b1·x - a1·y + s2; s2 = b2·x - a2·y},
 * in double precision for both the double and the float overloads; the float overload rounds only the output.
 * The state is two doubles per section, held by the caller and zero at the start of a stream, so one design can
 * filter any number of channels. Blocks may be processed in any chunking with identical results, and {@code in}
 * may be {@code out}. Nothing allocates.
 * <p>
 * Like any direct-form realization, a section tuned very low ({@code f0} well below 1e-3 cycles per sample) is
 * sensitive to its coefficients: their rounding changes the response by about {@code 1e-16 / (2π f0)²}, so a
 * filter at {@code f0 = 1e-4} keeps about 9 significant digits of gain. Lower the sample rate for such filters.
 *
 * @since 2.5.0
 */
public final class Biquad {

    /** The number of state values per section. */
    public static final int STATE_STRIDE = 2;

    private Biquad() {
    }

    /**
     * Filters {@code count} samples through {@code sections} cascaded sections.
     *
     * @param coeffs the sections, {@link BiquadDesign#STRIDE} doubles each
     * @param coeffOff the offset of the first section
     * @param sections the number of sections
     * @param state {@code 2·sections} doubles carried between calls
     * @param stateOff the offset of the state
     * @param in the input samples
     * @param inOff the offset of the first input
     * @param out receives the output samples; may be {@code in}
     * @param outOff the offset of the first output
     * @param count the number of samples
     */
    public static void process(double[] coeffs, int coeffOff, int sections, double[] state, int stateOff,
            double[] in, int inOff, double[] out, int outOff, int count) {
        check(coeffs, coeffOff, sections, state, stateOff);
        range(in == null ? -1 : in.length, inOff, count, "in");
        range(out == null ? -1 : out.length, outOff, count, "out");
        for (int sec = 0; sec < sections; sec++) {
            int c = coeffOff + BiquadDesign.STRIDE * sec;
            double b0 = coeffs[c];
            double b1 = coeffs[c + 1];
            double b2 = coeffs[c + 2];
            double a1 = coeffs[c + 3];
            double a2 = coeffs[c + 4];
            int so = stateOff + STATE_STRIDE * sec;
            double s1 = state[so];
            double s2 = state[so + 1];
            double[] src = sec == 0 ? in : out;
            int srcOff = sec == 0 ? inOff : outOff;
            for (int i = 0; i < count; i++) {
                double x = src[srcOff + i];
                double y = b0 * x + s1;
                s1 = b1 * x - a1 * y + s2;
                s2 = b2 * x - a2 * y;
                out[outOff + i] = y;
            }
            state[so] = s1;
            state[so + 1] = s2;
        }
    }

    /**
     * Filters float samples, computing in double with double state.
     *
     * @see #process(double[], int, int, double[], int, double[], int, double[], int, int)
     */
    public static void process(double[] coeffs, int coeffOff, int sections, double[] state, int stateOff,
            float[] in, int inOff, float[] out, int outOff, int count) {
        check(coeffs, coeffOff, sections, state, stateOff);
        range(in == null ? -1 : in.length, inOff, count, "in");
        range(out == null ? -1 : out.length, outOff, count, "out");
        // sample-major, so every intermediate stays in double and only the output is rounded
        for (int i = 0; i < count; i++) {
            double v = in[inOff + i];
            for (int sec = 0; sec < sections; sec++) {
                int c = coeffOff + BiquadDesign.STRIDE * sec;
                int so = stateOff + STATE_STRIDE * sec;
                double y = coeffs[c] * v + state[so];
                state[so] = coeffs[c + 1] * v - coeffs[c + 3] * y + state[so + 1];
                state[so + 1] = coeffs[c + 2] * v - coeffs[c + 4] * y;
                v = y;
            }
            out[outOff + i] = (float) v;
        }
    }

    /**
     * The frequency response of the cascade at {@code f} cycles per sample.
     *
     * @param dest receives the real part at {@code dest[0]} and the imaginary part at {@code dest[1]}
     */
    public static void response(double[] coeffs, int coeffOff, int sections, double f, double[] dest) {
        if (coeffs == null || sections < 0 || coeffOff < 0
                || coeffOff > coeffs.length - BiquadDesign.STRIDE * sections) {
            throw new IllegalArgumentException("coeffs must hold " + sections + " sections from " + coeffOff);
        }
        double w = 2 * java.lang.Math.PI * f;
        double c1 = AccurateMath.cos(w);
        double s1 = -AccurateMath.sin(w);
        double c2 = AccurateMath.cos(2 * w);
        double s2 = -AccurateMath.sin(2 * w);
        double hr = 1.0;
        double hi = 0.0;
        for (int sec = 0; sec < sections; sec++) {
            int c = coeffOff + BiquadDesign.STRIDE * sec;
            double nr = coeffs[c] + coeffs[c + 1] * c1 + coeffs[c + 2] * c2;
            double ni = coeffs[c + 1] * s1 + coeffs[c + 2] * s2;
            double dr = 1.0 + coeffs[c + 3] * c1 + coeffs[c + 4] * c2;
            double di = coeffs[c + 3] * s1 + coeffs[c + 4] * s2;
            double den = dr * dr + di * di;
            double qr = (nr * dr + ni * di) / den;
            double qi = (ni * dr - nr * di) / den;
            double t = hr * qr - hi * qi;
            hi = hr * qi + hi * qr;
            hr = t;
        }
        dest[0] = hr;
        dest[1] = hi;
    }

    /** {@code |H(f)|} of the cascade. */
    public static double magnitude(double[] coeffs, int coeffOff, int sections, double f) {
        if (coeffs == null || sections < 0 || coeffOff < 0
                || coeffOff > coeffs.length - BiquadDesign.STRIDE * sections) {
            throw new IllegalArgumentException("coeffs must hold " + sections + " sections from " + coeffOff);
        }
        double w = 2 * java.lang.Math.PI * f;
        double c1 = AccurateMath.cos(w);
        double s1 = -AccurateMath.sin(w);
        double c2 = AccurateMath.cos(2 * w);
        double s2 = -AccurateMath.sin(2 * w);
        double m = 1.0;
        for (int sec = 0; sec < sections; sec++) { // the product of the sections' magnitudes
            int c = coeffOff + BiquadDesign.STRIDE * sec;
            double nr = coeffs[c] + coeffs[c + 1] * c1 + coeffs[c + 2] * c2;
            double ni = coeffs[c + 1] * s1 + coeffs[c + 2] * s2;
            double dr = 1.0 + coeffs[c + 3] * c1 + coeffs[c + 4] * c2;
            double di = coeffs[c + 3] * s1 + coeffs[c + 4] * s2;
            m *= java.lang.Math.sqrt((nr * nr + ni * ni) / (dr * dr + di * di));
        }
        return m;
    }

    private static void check(double[] coeffs, int coeffOff, int sections, double[] state, int stateOff) {
        if (sections < 0) {
            throw new IllegalArgumentException("sections must be >= 0: " + sections);
        }
        range(coeffs == null ? -1 : coeffs.length, coeffOff, BiquadDesign.STRIDE * sections, "coeffs");
        range(state == null ? -1 : state.length, stateOff, STATE_STRIDE * sections, "state");
    }

    private static void range(int length, int off, int count, String name) {
        if (length < 0 || count < 0 || off < 0 || off > length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " values from offset " + off);
        }
    }
}
