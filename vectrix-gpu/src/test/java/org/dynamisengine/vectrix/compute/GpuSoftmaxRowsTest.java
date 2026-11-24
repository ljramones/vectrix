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
package org.dynamisengine.vectrix.compute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import org.dynamisengine.vectrix.nn.Softmax;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link GpuSoftmax#logSoftmax}, {@link GpuSoftmax#logSumExp}, {@link GpuSoftmax#softmaxBackward} and
 * {@link GpuSoftmax#logSoftmaxBackward} against the {@code Softmax} methods row by row, bit for bit, on every enabled
 * backend.
 */
class GpuSoftmaxRowsTest {

    /** Where the backward passes write dx: a separate buffer, over y, or over dy. */
    enum Out { SEPARATE, ON_Y, ON_DY }

    static Stream<Arguments> forwardCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (int[] s : RowsFixture.SHAPES) {
                for (boolean inPlace : new boolean[] {false, true}) {
                    out.add(Arguments.of(backend, s[0], s[1], inPlace));
                }
            }
        }
        return out.stream();
    }

    /** Runs logSoftmax over the rows of {@code x} on both and compares the output (and x, when in place). */
    static void checkLogSoftmax(String backend, int rows, int n, float[] x, int xOff, int ldx, int yOff, int ldy,
            boolean inPlace, String what) throws Exception {
        float[] y = inPlace ? x : new float[yOff + rows * ldy + 3];
        float[] ex = x.clone();
        float[] ey = inPlace ? ex : y.clone();
        int yo = inPlace ? xOff : yOff;
        int ly = inPlace ? ldx : ldy;
        for (int r = 0; r < rows; r++) {
            Softmax.logSoftmax(ex, xOff + r * ldx, ey, yo + r * ly, n, new float[n], 0);
        }
        RowsFixture.check(backend, GpuSoftmax::new, new Object[] {x, y}, new float[][] {ex, inPlace ? null : ey},
                (s, enc, b) -> s.logSoftmax(enc, rows, b[0], xOff, ldx, b[1], yo, ly, n),
                "logSoftmax " + what + " " + rows + "x" + n + (inPlace ? " in place" : ""));
    }

    @ParameterizedTest(name = "{0} {1}x{2} inPlace={3}")
    @MethodSource("forwardCases")
    void logSoftmaxMatches(String backend, int rows, int n, boolean inPlace) throws Exception {
        SplittableRandom r = new SplittableRandom(rows * 1_000_003L + n);
        int ldx = n + 5;
        float[] x = RowsFixture.uniform(r, 3 + rows * ldx + 2, -8, 8);
        checkLogSoftmax(backend, rows, n, x, 3, ldx, 1, n + 1, inPlace, "random");
    }

    static Stream<Arguments> lseCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (int[] s : RowsFixture.SHAPES) {
                out.add(Arguments.of(backend, s[0], s[1]));
            }
            out.add(Arguments.of(backend, 9, 0)); // empty rows: -inf each
        }
        return out.stream();
    }

    static void checkLogSumExp(String backend, int rows, int n, float[] x, int xOff, int ldx, String what)
            throws Exception {
        int outOff = 2;
        float[] out = new float[outOff + rows + 3];
        Arrays.fill(out, 7f);
        float[] expected = out.clone();
        for (int r = 0; r < rows; r++) {
            expected[outOff + r] = Softmax.logSumExp(x, xOff + r * ldx, n, new float[n], 0);
        }
        RowsFixture.check(backend, GpuSoftmax::new, new Object[] {x, out}, new float[][] {x.clone(), expected},
                (s, enc, b) -> s.logSumExp(enc, rows, b[0], xOff, ldx, n, b[1], outOff),
                "logSumExp " + what + " " + rows + "x" + n);
    }

    @ParameterizedTest(name = "{0} {1}x{2}")
    @MethodSource("lseCases")
    void logSumExpMatches(String backend, int rows, int n) throws Exception {
        SplittableRandom r = new SplittableRandom(rows * 7_919L + n);
        int ldx = n + 3;
        float[] x = RowsFixture.uniform(r, 1 + rows * ldx + 1, -8, 8);
        checkLogSumExp(backend, rows, n, x, 1, ldx, "random");
    }

    static Stream<Arguments> backwardCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (int[] s : RowsFixture.SHAPES) {
                for (Out o : Out.values()) {
                    for (boolean log : new boolean[] {false, true}) {
                        out.add(Arguments.of(backend, s[0], s[1], o, log));
                    }
                }
            }
        }
        return out.stream();
    }

    /** Runs one backward pass over rows of y and dy on both and compares every buffer. */
    static void checkBackward(String backend, int rows, int n, float[] y, int yOff, int ldy, float[] dy, int dyOff,
            int lddy, Out o, boolean log, String what) throws Exception {
        float[] dx = switch (o) {
            case SEPARATE -> new float[4 + rows * (n + 1) + 2];
            case ON_Y -> y;
            case ON_DY -> dy;
        };
        int dxOff = switch (o) {
            case SEPARATE -> 4;
            case ON_Y -> yOff;
            case ON_DY -> dyOff;
        };
        int lddx = switch (o) {
            case SEPARATE -> n + 1;
            case ON_Y -> ldy;
            case ON_DY -> lddy;
        };
        float[] ey = y.clone();
        float[] edy = dy.clone();
        float[] edx = switch (o) {
            case SEPARATE -> dx.clone();
            case ON_Y -> ey;
            case ON_DY -> edy;
        };
        for (int r = 0; r < rows; r++) {
            if (log) {
                Softmax.logSoftmaxBackward(ey, yOff + r * ldy, edy, dyOff + r * lddy, edx, dxOff + r * lddx, n);
            } else {
                Softmax.softmaxBackward(ey, yOff + r * ldy, edy, dyOff + r * lddy, edx, dxOff + r * lddx, n);
            }
        }
        RowsFixture.check(backend, GpuSoftmax::new, new Object[] {y, dy, dx},
                new float[][] {ey, edy, o == Out.SEPARATE ? edx : null}, (s, enc, b) -> {
                    if (log) {
                        s.logSoftmaxBackward(enc, rows, b[0], yOff, ldy, b[1], dyOff, lddy, b[2], dxOff, lddx, n);
                    } else {
                        s.softmaxBackward(enc, rows, b[0], yOff, ldy, b[1], dyOff, lddy, b[2], dxOff, lddx, n);
                    }
                }, (log ? "logSoftmaxBackward " : "softmaxBackward ") + what + " " + rows + "x" + n + " dx " + o);
    }

    @ParameterizedTest(name = "{0} {1}x{2} dx={3} log={4}")
    @MethodSource("backwardCases")
    void backwardMatches(String backend, int rows, int n, Out o, boolean log) throws Exception {
        SplittableRandom r = new SplittableRandom(rows * 31L + n * 7L + o.ordinal());
        int ldy = n + 3;
        int lddy = n + 2;
        float[] y = log ? RowsFixture.uniform(r, 2 + rows * ldy, -8, 0) : RowsFixture.uniform(r, 2 + rows * ldy, 0, 1);
        float[] dy = RowsFixture.uniform(r, 1 + rows * lddy + 4, -1, 1);
        checkBackward(backend, rows, n, y, 2, ldy, dy, 1, lddy, o, log, "random");
    }

    static Stream<Arguments> special() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            out.add(Arguments.of(backend, false));
            out.add(Arguments.of(backend, true));
        }
        return out.stream();
    }

    /** Rows with NaN, infinities, all minus infinity, signed zeros, ties and a range wide enough to underflow. */
    static float[] specialRows(int rows, int n) {
        float[] x = new float[rows * n];
        SplittableRandom r = new SplittableRandom(77);
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) (r.nextDouble() * 4 - 2);
        }
        x[0 * n + 123] = Float.NaN;
        x[1 * n + 5] = Float.POSITIVE_INFINITY;
        for (int j = 0; j < n; j++) {
            x[2 * n + j] = Float.NEGATIVE_INFINITY;
            x[3 * n + j] = (j & 1) == 0 ? 0f : -0f;
            x[4 * n + j] = 1.5f;
            x[5 * n + j] = (float) (r.nextDouble() * 200 - 100); // exponents far below the maximum
        }
        x[6 * n + 299] = Float.NEGATIVE_INFINITY;
        x[7 * n + 17] = Float.MAX_VALUE;
        x[7 * n + 18] = -Float.MAX_VALUE;
        x[8 * n + 40] = Float.POSITIVE_INFINITY;
        x[8 * n + 41] = Float.NEGATIVE_INFINITY;
        return x;
    }

    @ParameterizedTest(name = "{0} inPlace={1}")
    @MethodSource("special")
    void specialRowsMatchLogSoftmax(String backend, boolean inPlace) throws Exception {
        checkLogSoftmax(backend, 9, 300, specialRows(9, 300), 0, 300, 0, 300, inPlace, "special rows");
    }

    static Stream<String> backends() {
        return Devices.namesWithFusedFma().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void specialRowsMatchLogSumExp(String backend) throws Exception {
        checkLogSumExp(backend, 9, 300, specialRows(9, 300), 0, 300, "special rows");
    }

    static Stream<Arguments> specialBackward() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (Out o : Out.values()) {
                out.add(Arguments.of(backend, o, false));
                out.add(Arguments.of(backend, o, true));
            }
        }
        return out.stream();
    }

    /** y and dy rows with NaN, infinities, signed zeros and ties. */
    @ParameterizedTest(name = "{0} dx={1} log={2}")
    @MethodSource("specialBackward")
    void specialRowsMatchBackward(String backend, Out o, boolean log) throws Exception {
        int n = 300;
        int rows = 7;
        SplittableRandom r = new SplittableRandom(91);
        float[] y = log ? RowsFixture.uniform(r, rows * n, -6, 0) : RowsFixture.uniform(r, rows * n, 0, 1);
        float[] dy = RowsFixture.uniform(r, rows * n, -1, 1);
        y[0 * n + 7] = Float.NaN;
        dy[1 * n + 200] = Float.NaN;
        y[2 * n + 3] = log ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        dy[3 * n + 4] = Float.POSITIVE_INFINITY;
        dy[3 * n + 5] = Float.NEGATIVE_INFINITY;
        for (int j = 0; j < n; j++) {
            y[4 * n + j] = (j & 1) == 0 ? 0f : -0f;
            dy[4 * n + j] = (j & 2) == 0 ? 0f : -0f;
            y[5 * n + j] = log ? -1.25f : 0.125f;
            dy[5 * n + j] = 0.5f;
            dy[6 * n + j] = (j & 1) == 0 ? 0.75f : -0.75f;
        }
        checkBackward(backend, rows, n, y, 0, n, dy, 0, n, o, log, "special rows");
    }
}
