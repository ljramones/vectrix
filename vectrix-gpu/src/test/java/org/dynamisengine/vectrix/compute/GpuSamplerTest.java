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

import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.vectrix.nn.Sampler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link GpuSampler#argmax} against {@code Sampler.argmax} row by row on every enabled backend: equal indices, with no
 * allowance for subnormals (the kernel compares bits).
 */
class GpuSamplerTest {

    /** {rows, n}: rows shorter than the group, group boundaries, long rows, more rows than grid columns, n = 0. */
    private static final int[][] SHAPES = {{1, 1}, {3, 7}, {2, 255}, {4, 256}, {3, 257}, {2, 1000}, {20, 33},
        {1, 100_003}, {5, 0}};

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (int[] s : SHAPES) {
                out.add(Arguments.of(backend, s[0], s[1]));
            }
        }
        return out.stream();
    }

    static Stream<Arguments> backends() {
        return Devices.names().stream().map(Arguments::of);
    }

    static void check(String backend, int rows, int n, float[] logits, int off, int ld, String what) {
        int destOff = 2;
        int[] expected = new int[destOff + rows + 3];
        Arrays.fill(expected, 0x5EED);
        for (int r = 0; r < rows; r++) {
            expected[destOff + r] = Sampler.argmax(logits, off + r * ld, n);
        }
        try (GpuDevice device = Devices.open(backend); GpuSampler sampler = new GpuSampler(device);
                var enc = device.encoder()) {
            var gl = Devices.upload(device, enc, logits);
            int[] sentinel = new int[expected.length];
            Arrays.fill(sentinel, 0x5EED);
            var gd = Devices.upload(device, enc, sentinel);
            sampler.argmax(enc, rows, gl, off, ld, n, gd, destOff);
            var read = enc.download(gd);
            Devices.run(device, enc);
            Bits.assertSame(expected, Devices.ints(read, expected.length), backend + " argmax " + what + " " + rows
                    + "x" + n);
        }
    }

    /** Random logits from a small set of values, so rows hold many ties. */
    @ParameterizedTest(name = "{0} {1}x{2}")
    @MethodSource("cases")
    void matchesArgmax(String backend, int rows, int n) {
        SplittableRandom r = new SplittableRandom(rows * 7919L + n);
        int off = 3;
        int ld = n + 4;
        float[] x = new float[off + rows * ld + 1];
        for (int i = 0; i < x.length; i++) {
            x[i] = r.nextInt(-50, 50) / 4f;
        }
        check(backend, rows, n, x, off, ld, "random");
    }

    /** NaNs (the first wins), ties, signed zeros, infinities and subnormals, in rows longer than the group. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void specialRowsMatchArgmax(String backend) {
        int n = 700;
        int rows = 12;
        float[] x = new float[rows * n];
        SplittableRandom r = new SplittableRandom(5);
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) (r.nextDouble() * 4 - 2);
        }
        x[0 * n + 300] = Float.NaN; // a later NaN read by an earlier thread
        x[0 * n + 44] = Float.NaN;
        x[0 * n + 45] = Float.POSITIVE_INFINITY;
        x[1 * n + 699] = Float.intBitsToFloat(0xFFC00001); // a negative NaN, last
        x[1 * n + 3] = Float.POSITIVE_INFINITY;
        for (int j = 0; j < n; j++) {
            x[2 * n + j] = Float.NEGATIVE_INFINITY;
            x[3 * n + j] = (j & 1) == 0 ? -0f : 0f; // -0 first: ±0 tie, index 0
            x[4 * n + j] = 1.5f;
            x[5 * n + j] = Float.intBitsToFloat(1 + (j * 37) % 500); // distinct subnormals
            x[6 * n + j] = -Float.intBitsToFloat(1 + j); // negative subnormals: the first is the largest
            x[7 * n + j] = j % 300 == 299 ? 2.5f : 1f; // equal maxima in three threads' strides
            x[8 * n + j] = Float.intBitsToFloat(0x80000000 | (j == 600 ? 0 : 1 + j)); // -0 at 600 beats them
        }
        x[9 * n + 256] = Float.MAX_VALUE;
        x[9 * n + 512] = Float.MAX_VALUE;
        x[10 * n + 699] = Float.POSITIVE_INFINITY;
        x[11 * n + 0] = Float.NaN;
        check(backend, rows, n, x, 0, n, "special rows");
    }
}
