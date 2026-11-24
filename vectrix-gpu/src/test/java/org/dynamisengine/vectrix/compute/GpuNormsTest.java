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

import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.vectrix.nn.Norms;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link GpuNorms} against the {@code Norms} methods called row by row in row order, bit for bit, on every enabled
 * backend: outputs, statistics and the parameter gradients accumulated over the rows.
 */
class GpuNormsTest {

    /** Which optional arguments are present. */
    enum Opts {
        ALL(true, true, true), NONE(false, false, false), GAMMA_STATS(true, false, true);

        final boolean gamma;
        final boolean beta;
        final boolean stats;

        Opts(boolean gamma, boolean beta, boolean stats) {
            this.gamma = gamma;
            this.beta = beta;
            this.stats = stats;
        }
    }

    /** Where the backward passes write dx: a separate buffer, over x, or over dy. */
    enum Out { SEPARATE, ON_X, ON_DY }

    /** The parameter gradients requested: both (with gamma), none (no gamma), dGamma only (no gamma). */
    enum Grads {
        FULL(true, true, true), NONE(false, false, false), DGAMMA(false, true, false);

        final boolean gamma;
        final boolean dGamma;
        final boolean dBeta;

        Grads(boolean gamma, boolean dGamma, boolean dBeta) {
            this.gamma = gamma;
            this.dGamma = dGamma;
            this.dBeta = dBeta;
        }
    }

    private static final int X_OFF = 3;
    private static final int G_OFF = 2;
    private static final int B_OFF = 1;
    private static final int S_OFF = 1;

    static Stream<Arguments> forwardCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (int[] s : RowsFixture.SHAPES) {
                for (boolean rms : new boolean[] {false, true}) {
                    for (Opts o : Opts.values()) {
                        if (rms && o == Opts.GAMMA_STATS) {
                            continue; // the same as ALL for RMS norm (no beta)
                        }
                        for (boolean inPlace : new boolean[] {false, true}) {
                            out.add(Arguments.of(backend, s[0], s[1], rms, o, inPlace));
                        }
                    }
                }
            }
        }
        return out.stream();
    }

    /** Runs a forward norm over the rows of {@code x} on both and compares the output, x and the statistics. */
    static void checkForward(String backend, int rows, int n, float[] x, int ldx, float[] gamma, float[] beta,
            float eps, boolean rms, Opts o, boolean inPlace, String what) throws Exception {
        int ldy = inPlace ? ldx : n + 1;
        int yOff = inPlace ? X_OFF : 1;
        float[] y = inPlace ? x : new float[yOff + rows * ldy + 2];
        float[] stats = new float[S_OFF + (rms ? 1 : 2) * rows + 2];
        Arrays.fill(stats, 9f);
        float[] ex = x.clone();
        float[] ey = inPlace ? ex : y.clone();
        float[] es = stats.clone();
        for (int r = 0; r < rows; r++) {
            if (rms) {
                Norms.rmsNorm(ex, X_OFF + r * ldx, o.gamma ? gamma : null, G_OFF, eps, ey, yOff + r * ldy, n,
                        o.stats ? es : null, S_OFF + r);
            } else {
                Norms.layerNorm(ex, X_OFF + r * ldx, o.gamma ? gamma : null, G_OFF, o.beta ? beta : null, B_OFF, eps,
                        ey, yOff + r * ldy, n, o.stats ? es : null, S_OFF + 2 * r);
            }
        }
        RowsFixture.check(backend, GpuNorms::new, new Object[] {x, y, gamma, beta, stats},
                new float[][] {ex, inPlace ? null : ey, gamma.clone(), beta.clone(), es}, (k, enc, b) -> {
                    GpuBufferArgs a = new GpuBufferArgs(b, o.gamma, o.beta, o.stats);
                    if (rms) {
                        k.rmsNorm(enc, rows, b[0], X_OFF, ldx, a.gamma, G_OFF, eps, b[1], yOff, ldy, n, a.stats,
                                S_OFF);
                    } else {
                        k.layerNorm(enc, rows, b[0], X_OFF, ldx, a.gamma, G_OFF, a.beta, B_OFF, eps, b[1], yOff, ldy,
                                n, a.stats, S_OFF);
                    }
                }, (rms ? "rmsNorm " : "layerNorm ") + what + " " + rows + "x" + n + " " + o
                        + (inPlace ? " in place" : ""));
    }

    /** The optional buffers of a recording, null when absent. */
    private record GpuBufferArgs(GpuBuffer gamma, GpuBuffer beta, GpuBuffer stats) {
        GpuBufferArgs(GpuBuffer[] b, boolean g, boolean be, boolean s) {
            this(g ? b[2] : null, be ? b[3] : null, s ? b[4] : null);
        }
    }

    @ParameterizedTest(name = "{0} {1}x{2} rms={3} {4} inPlace={5}")
    @MethodSource("forwardCases")
    void forwardMatches(String backend, int rows, int n, boolean rms, Opts o, boolean inPlace) throws Exception {
        SplittableRandom r = new SplittableRandom(rows * 2_147_483_647L + n * 3L + (rms ? 1 : 0));
        int ldx = n + 5;
        float[] x = RowsFixture.uniform(r, X_OFF + rows * ldx + 2, -4, 4);
        float[] gamma = RowsFixture.uniform(r, G_OFF + n + 1, 0.5, 1.5);
        float[] beta = RowsFixture.uniform(r, B_OFF + n + 1, -1, 1);
        checkForward(backend, rows, n, x, ldx, gamma, beta, rms ? 1e-6f : 1e-5f, rms, o, inPlace, "random");
    }

    static Stream<Arguments> backwardCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (int[] s : RowsFixture.SHAPES) {
                for (boolean rms : new boolean[] {false, true}) {
                    for (Out o : Out.values()) {
                        for (Grads g : Grads.values()) {
                            out.add(Arguments.of(backend, s[0], s[1], rms, o, g));
                        }
                    }
                }
            }
        }
        return out.stream();
    }

    /**
     * Runs a backward norm on both and compares every buffer: dx, the inputs it may overwrite, and dGamma and dBeta
     * accumulated over the rows (from nonzero starting values).
     */
    static void checkBackward(String backend, int rows, int n, float[] x, int ldx, float[] gamma, float[] stats,
            float[] dy, int lddy, float[] dGamma, float[] dBeta, boolean rms, Out o, Grads g, String what)
            throws Exception {
        int dyOff = 2;
        float[] dx = switch (o) {
            case SEPARATE -> new float[1 + rows * (n + 2) + 1];
            case ON_X -> x;
            case ON_DY -> dy;
        };
        int dxOff = switch (o) {
            case SEPARATE -> 1;
            case ON_X -> X_OFF;
            case ON_DY -> dyOff;
        };
        int lddx = switch (o) {
            case SEPARATE -> n + 2;
            case ON_X -> ldx;
            case ON_DY -> lddy;
        };
        float[] ex = x.clone();
        float[] edy = dy.clone();
        float[] edx = switch (o) {
            case SEPARATE -> dx.clone();
            case ON_X -> ex;
            case ON_DY -> edy;
        };
        float[] edg = dGamma.clone();
        float[] edb = dBeta.clone();
        float[] scratch = new float[2 * n];
        for (int r = 0; r < rows; r++) {
            if (rms) {
                Norms.rmsNormBackward(ex, X_OFF + r * ldx, g.gamma ? gamma : null, G_OFF, stats, S_OFF + r, edy,
                        dyOff + r * lddy, edx, dxOff + r * lddx, g.dGamma ? edg : null, G_OFF, n, scratch, 0);
            } else {
                Norms.layerNormBackward(ex, X_OFF + r * ldx, g.gamma ? gamma : null, G_OFF, stats, S_OFF + 2 * r, edy,
                        dyOff + r * lddy, edx, dxOff + r * lddx, g.dGamma ? edg : null, G_OFF, g.dBeta ? edb : null,
                        B_OFF, n, scratch, 0);
            }
        }
        RowsFixture.check(backend, GpuNorms::new, new Object[] {x, dy, dx, gamma, stats, dGamma, dBeta},
                new float[][] {ex, edy, o == Out.SEPARATE ? edx : null, gamma.clone(), stats.clone(), edg, edb},
                (k, enc, b) -> {
                    var ga = g.gamma ? b[3] : null;
                    var dga = g.dGamma ? b[5] : null;
                    if (rms) {
                        k.rmsNormBackward(enc, rows, b[0], X_OFF, ldx, ga, G_OFF, b[4], S_OFF, b[1], dyOff, lddy, b[2],
                                dxOff, lddx, dga, G_OFF, n);
                    } else {
                        k.layerNormBackward(enc, rows, b[0], X_OFF, ldx, ga, G_OFF, b[4], S_OFF, b[1], dyOff, lddy,
                                b[2], dxOff, lddx, dga, G_OFF, g.dBeta ? b[6] : null, B_OFF, n);
                    }
                }, (rms ? "rmsNormBackward " : "layerNormBackward ") + what + " " + rows + "x" + n + " dx " + o
                        + " " + g);
    }

    /** The statistics the CPU forward pass writes for the rows of {@code x}. */
    static float[] forwardStats(float[] x, int ldx, int rows, int n, float[] gamma, boolean rms) {
        float[] stats = new float[S_OFF + 2 * rows + 1];
        float[] y = new float[n];
        for (int r = 0; r < rows; r++) {
            if (rms) {
                Norms.rmsNorm(x, X_OFF + r * ldx, gamma, G_OFF, 1e-6f, y, 0, n, stats, S_OFF + r);
            } else {
                Norms.layerNorm(x, X_OFF + r * ldx, gamma, G_OFF, null, 0, 1e-5f, y, 0, n, stats, S_OFF + 2 * r);
            }
        }
        return stats;
    }

    @ParameterizedTest(name = "{0} {1}x{2} rms={3} dx={4} {5}")
    @MethodSource("backwardCases")
    void backwardMatches(String backend, int rows, int n, boolean rms, Out o, Grads g) throws Exception {
        SplittableRandom r = new SplittableRandom(rows * 1_000_033L + n * 5L + o.ordinal() * 3L + g.ordinal());
        int ldx = n + 5;
        int lddy = n + 3;
        float[] x = RowsFixture.uniform(r, X_OFF + rows * ldx + 2, -4, 4);
        float[] gamma = RowsFixture.uniform(r, G_OFF + n + 1, 0.5, 1.5);
        float[] stats = forwardStats(x, ldx, rows, n, gamma, rms);
        float[] dy = RowsFixture.uniform(r, 2 + rows * lddy + 1, -1, 1);
        float[] dGamma = RowsFixture.uniform(r, G_OFF + n + 1, -1, 1);
        float[] dBeta = RowsFixture.uniform(r, B_OFF + n + 1, -1, 1);
        checkBackward(backend, rows, n, x, ldx, gamma, stats, dy, lddy, dGamma, dBeta, rms, o, g, "random");
    }

    static Stream<Arguments> special() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (boolean rms : new boolean[] {false, true}) {
                out.add(Arguments.of(backend, rms));
            }
        }
        return out.stream();
    }

    /** Rows with NaN, infinities, zeros of both signs, constant rows, overflowing squares and the extremes. */
    static float[] specialRows(int rows, int n, int ldx) {
        SplittableRandom r = new SplittableRandom(13);
        float[] x = RowsFixture.uniform(r, X_OFF + rows * ldx, -2, 2);
        int b = X_OFF;
        x[b + 0 * ldx + 17] = Float.NaN;
        x[b + 1 * ldx + 3] = Float.POSITIVE_INFINITY;
        x[b + 1 * ldx + 4] = Float.NEGATIVE_INFINITY;
        for (int j = 0; j < n; j++) {
            x[b + 2 * ldx + j] = (j & 1) == 0 ? 0f : -0f;
            x[b + 3 * ldx + j] = -2.5f;
        }
        x[b + 4 * ldx + 9] = 1e30f; // its square overflows: var = inf, 1/σ = 0
        x[b + 5 * ldx + 1] = Float.MAX_VALUE;
        x[b + 5 * ldx + 2] = -Float.MAX_VALUE;
        x[b + 6 * ldx + 0] = Float.POSITIVE_INFINITY;
        return x;
    }

    @ParameterizedTest(name = "{0} rms={1}")
    @MethodSource("special")
    void specialRowsMatchForward(String backend, boolean rms) throws Exception {
        int rows = 8;
        int n = 300;
        int ldx = n;
        SplittableRandom r = new SplittableRandom(3);
        float[] gamma = RowsFixture.uniform(r, G_OFF + n, 0.5, 1.5);
        float[] beta = RowsFixture.uniform(r, B_OFF + n, -1, 1);
        gamma[G_OFF + 20] = Float.NaN;
        beta[B_OFF + 21] = Float.POSITIVE_INFINITY;
        gamma[G_OFF + 22] = -0f;
        for (boolean inPlace : new boolean[] {false, true}) {
            checkForward(backend, rows, n, specialRows(rows, n, ldx), ldx, gamma, beta, 1e-5f, rms, Opts.ALL, inPlace,
                    "special rows");
            checkForward(backend, rows, n, specialRows(rows, n, ldx), ldx, gamma, beta, 0f, rms, Opts.NONE, inPlace,
                    "special rows, eps 0");
        }
    }

    @ParameterizedTest(name = "{0} rms={1}")
    @MethodSource("special")
    void specialRowsMatchBackward(String backend, boolean rms) throws Exception {
        int rows = 8;
        int n = 300;
        int ldx = n;
        SplittableRandom r = new SplittableRandom(4);
        float[] gamma = RowsFixture.uniform(r, G_OFF + n, 0.5, 1.5);
        float[] stats = forwardStats(specialRows(rows, n, ldx), ldx, rows, n, gamma, rms);
        int lddy = n;
        for (Out o : Out.values()) {
            float[] dy = RowsFixture.uniform(r, 2 + rows * lddy, -1, 1);
            dy[2 + 7 * lddy + 5] = Float.NaN;
            dy[2 + 3 * lddy + 6] = Float.NEGATIVE_INFINITY;
            for (int j = 0; j < n; j++) {
                dy[2 + 2 * lddy + j] = (j & 2) == 0 ? 0f : -0f;
            }
            float[] dGamma = RowsFixture.uniform(r, G_OFF + n, -1, 1);
            float[] dBeta = RowsFixture.uniform(r, B_OFF + n, -1, 1);
            checkBackward(backend, rows, n, specialRows(rows, n, ldx), ldx, gamma, stats, dy, lddy, dGamma, dBeta,
                    rms, o, Grads.FULL, "special rows");
        }
    }
}
