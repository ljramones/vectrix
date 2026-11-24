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

import org.dynamisengine.vectrix.nn.Loss;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link GpuLoss} against {@code Loss.crossEntropy} and {@code Loss.crossEntropyBackward} row by row, bit for bit,
 * on every enabled backend. A target outside {@code [0, n)} (which the CPU rejects) must give NaN.
 */
class GpuLossTest {

    /** The targets of {@code rows} rows of {@code n} classes: random, with the first and last class included. */
    static int[] targets(SplittableRandom r, int off, int rows, int n) {
        int[] t = new int[off + rows + 2];
        Arrays.fill(t, -7);
        for (int i = 0; i < rows; i++) {
            t[off + i] = i == 0 ? n - 1 : i == 1 ? 0 : r.nextInt(n);
        }
        return t;
    }

    static float lossOf(float[] x, int off, int n, int target) {
        return target < 0 || target >= n ? Float.NaN : Loss.crossEntropy(x, off, n, target, new float[n], 0);
    }

    static void gradOf(float[] x, int off, int n, int target, float dLoss, float[] dx, int dxOff) {
        if (target < 0 || target >= n) {
            Arrays.fill(dx, dxOff, dxOff + n, Float.NaN);
        } else {
            Loss.crossEntropyBackward(x, off, n, target, dLoss, dx, dxOff);
        }
    }

    static void checkLoss(String backend, int rows, int n, float[] x, int off, int ld, int[] t, int tOff, String what)
            throws Exception {
        int lossOff = 3;
        float[] loss = new float[lossOff + rows + 2];
        Arrays.fill(loss, 5f);
        float[] expected = loss.clone();
        for (int r = 0; r < rows; r++) {
            expected[lossOff + r] = lossOf(x, off + r * ld, n, t[tOff + r]);
        }
        RowsFixture.check(backend, GpuLoss::new, new Object[] {x, t, loss}, new float[][] {x.clone(), null, expected},
                (l, enc, b) -> l.crossEntropy(enc, rows, b[0], off, ld, n, b[1], tOff, b[2], lossOff),
                "crossEntropy " + what + " " + rows + "x" + n);
    }

    static void checkGrad(String backend, int rows, int n, float[] x, int off, int ld, int[] t, int tOff, float dLoss,
            boolean inPlace, String what) throws Exception {
        float[] dx = inPlace ? x : new float[2 + rows * (n + 1) + 3];
        int dxOff = inPlace ? off : 2;
        int lddx = inPlace ? ld : n + 1;
        float[] ex = x.clone();
        float[] edx = inPlace ? ex : dx.clone();
        for (int r = 0; r < rows; r++) {
            gradOf(ex, off + r * ld, n, t[tOff + r], dLoss, edx, dxOff + r * lddx);
        }
        RowsFixture.check(backend, GpuLoss::new, new Object[] {x, t, dx},
                new float[][] {ex, null, inPlace ? null : edx},
                (l, enc, b) -> l.crossEntropyBackward(enc, rows, b[0], off, ld, n, b[1], tOff, dLoss, b[2], dxOff,
                        lddx), "crossEntropyBackward " + what + " " + rows + "x" + n + (inPlace ? " in place" : ""));
    }

    static Stream<Arguments> lossCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (int[] s : RowsFixture.SHAPES) {
                out.add(Arguments.of(backend, s[0], s[1]));
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} {1}x{2}")
    @MethodSource("lossCases")
    void crossEntropyMatches(String backend, int rows, int n) throws Exception {
        SplittableRandom r = new SplittableRandom(rows * 104_729L + n);
        int ld = n + 4;
        float[] x = RowsFixture.uniform(r, 2 + rows * ld, -8, 8);
        checkLoss(backend, rows, n, x, 2, ld, targets(r, 1, rows, n), 1, "random");
    }

    static Stream<Arguments> gradCases() {
        List<Arguments> out = new ArrayList<>();
        float[] dLoss = {1f, -0.25f, 3f};
        int k = 0;
        for (String backend : Devices.names()) {
            for (int[] s : RowsFixture.SHAPES) {
                for (boolean inPlace : new boolean[] {false, true}) {
                    out.add(Arguments.of(backend, s[0], s[1], dLoss[k++ % dLoss.length], inPlace));
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} {1}x{2} dLoss={3} inPlace={4}")
    @MethodSource("gradCases")
    void crossEntropyBackwardMatches(String backend, int rows, int n, float dLoss, boolean inPlace) throws Exception {
        SplittableRandom r = new SplittableRandom(rows * 15_485_863L + n);
        int ld = n + 4;
        float[] x = RowsFixture.uniform(r, 2 + rows * ld, -8, 8);
        checkGrad(backend, rows, n, x, 2, ld, targets(r, 1, rows, n), 1, dLoss, inPlace, "random");
    }

    static Stream<Arguments> special() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            out.add(Arguments.of(backend, false));
            out.add(Arguments.of(backend, true));
        }
        return out.stream();
    }

    /**
     * Special logits (see GpuSoftmaxRowsTest.specialRows), targets on NaN and infinite logits, and targets outside
     * [0, n). dLoss stays within [-1, 1] so a probability Vectrix leaves subnormal (zero on GPUs that flush) stays
     * subnormal after scaling.
     */
    static int[] specialTargets(int rows, int n) {
        int[] t = new int[rows];
        for (int i = 0; i < rows; i++) {
            t[i] = (i * 37) % n;
        }
        t[0] = 123; // the NaN
        t[1] = 5; // +inf
        t[6] = 299; // -inf
        t[8] = 41; // -inf next to +inf
        t[9] = -1;
        t[10] = n;
        t[11] = Integer.MIN_VALUE;
        t[12] = Integer.MAX_VALUE;
        return t;
    }

    static float[] specialLogits(int rows, int n) {
        float[] x = new float[rows * n];
        System.arraycopy(GpuSoftmaxRowsTest.specialRows(9, n), 0, x, 0, 9 * n);
        SplittableRandom r = new SplittableRandom(5);
        for (int i = 9 * n; i < x.length; i++) {
            x[i] = (float) (r.nextDouble() * 4 - 2);
        }
        return x;
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("special")
    void specialRowsMatch(String backend, boolean backward) throws Exception {
        int rows = 13;
        int n = 300;
        float[] x = specialLogits(rows, n);
        int[] t = specialTargets(rows, n);
        if (backward) {
            checkGrad(backend, rows, n, x, 0, n, t, 0, -0.5f, false, "special rows");
            checkGrad(backend, rows, n, specialLogits(rows, n), 0, n, t, 0, 1f, true, "special rows");
        } else {
            checkLoss(backend, rows, n, x, 0, n, t, 0, "special rows");
        }
    }
}
