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
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.vectrix.nn.Softmax;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link GpuSoftmax} against {@code Softmax.softmax} row by row, bit for bit, on every enabled backend. */
class GpuSoftmaxTest {

    /** {rows, n}: rows shorter than the group, block boundaries, deep block trees, more rows than grid columns. */
    private static final int[][] SHAPES = {
        {1, 1}, {3, 7}, {2, 8}, {4, 255}, {4, 256}, {3, 257}, {2, 1000}, {2, 4096}, {1, 70_000}, {20, 33},
        {1, GpuSoftmax.MAX_ROW},
    };

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (int[] s : SHAPES) {
                for (boolean inPlace : new boolean[] {false, true}) {
                    out.add(Arguments.of(backend, s[0], s[1], inPlace));
                }
            }
        }
        return out.stream();
    }

    /** Runs the rows of {@code x} through both and compares the whole output buffer. */
    static void check(String backend, int rows, int n, float[] x, int xOff, int ldx, int yOff, int ldy,
            boolean inPlace, String what) {
        float[] y = inPlace ? null : new float[yOff + rows * ldy + 3];
        float[] expected = inPlace ? x.clone() : y.clone();
        for (int r = 0; r < rows; r++) {
            if (inPlace) {
                Softmax.softmax(expected, xOff + r * ldx, expected, xOff + r * ldx, n);
            } else {
                Softmax.softmax(x, xOff + r * ldx, expected, yOff + r * ldy, n);
            }
        }
        try (GpuDevice device = Devices.open(backend); GpuSoftmax softmax = new GpuSoftmax(device);
                var enc = device.encoder()) {
            var gx = Devices.upload(device, enc, x);
            var gy = inPlace ? gx : Devices.upload(device, enc, y);
            if (inPlace) {
                softmax.softmax(enc, rows, gx, xOff, ldx, gx, xOff, ldx, n);
            } else {
                softmax.softmax(enc, rows, gx, xOff, ldx, gy, yOff, ldy, n);
            }
            var read = enc.download(gy);
            Devices.run(device, enc);
            Bits.assertSame(expected, Devices.floats(read, expected.length), Devices.hardware(backend),
                    backend + " softmax " + what + " " + rows + "x" + n + (inPlace ? " in place" : ""));
        }
    }

    @ParameterizedTest(name = "{0} {1}x{2} inPlace={3}")
    @MethodSource("cases")
    void matchesSoftmax(String backend, int rows, int n, boolean inPlace) {
        SplittableRandom r = new SplittableRandom(rows * 1_000_003L + n);
        int ldx = n + 5;
        int xOff = 3;
        float[] x = new float[xOff + rows * ldx + 2];
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) (r.nextDouble() * 16 - 8);
        }
        check(backend, rows, n, x, xOff, ldx, 1, n + 1, inPlace, "random");
    }

    static Stream<Arguments> special() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            out.add(Arguments.of(backend, false));
            out.add(Arguments.of(backend, true));
        }
        return out.stream();
    }

    /** Rows with NaN, infinities, all minus infinity, signed zeros, ties and a range wide enough to underflow. */
    @ParameterizedTest(name = "{0} inPlace={1}")
    @MethodSource("special")
    void specialRowsMatchSoftmax(String backend, boolean inPlace) {
        int n = 300;
        int rows = 8;
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
        check(backend, rows, n, x, 0, n, 0, n, inPlace, "special rows");
    }
}
