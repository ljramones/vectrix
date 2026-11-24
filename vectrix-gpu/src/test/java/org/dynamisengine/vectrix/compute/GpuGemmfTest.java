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
import org.dynamisengine.vectrix.blas.Gemmf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link GpuGemmf} against {@code Gemmf.gemm}, bit for bit, on every enabled backend. */
class GpuGemmfTest {

    /** Shapes covering partial tiles, every k-tree depth up to five levels, and each layout. */
    private static final int[][] SHAPES = {
        {1, 1, 1}, {3, 5, 7}, {16, 16, 16}, {17, 33, 255}, {31, 7, 256}, {20, 19, 257}, {5, 9, 1000},
        {40, 24, 2049}, {2, 3, 4100}, {64, 48, 600}, {1, 70, 9000}, {7, 3, 0},
    };

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (int[] s : SHAPES) {
                for (int layout = 0; layout < 4; layout++) {
                    out.add(Arguments.of(backend, s[0], s[1], s[2], (layout & 1) != 0, (layout & 2) != 0));
                }
            }
        }
        return out.stream();
    }

    static float[] random(SplittableRandom r, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = (float) (r.nextDouble() * 2 - 1);
        }
        return a;
    }

    /** Runs one product on {@code backend} and on the CPU and compares every element of {@code c}. */
    static void check(String backend, boolean ta, boolean tb, int m, int n, int k, float alpha, float beta, float[] a,
            int aOff, int lda, float[] b, int bOff, int ldb, float[] c, int cOff, int ldc) {
        float[] expected = c.clone();
        Gemmf.gemm(ta, tb, m, n, k, alpha, a, aOff, lda, b, bOff, ldb, beta, expected, cOff, ldc,
                new float[Gemmf.scratchSize(ta, tb, m, n, k)], 0);
        try (GpuDevice device = Devices.open(backend); GpuGemmf gemm = new GpuGemmf(device);
                var enc = device.encoder()) {
            var ga = Devices.upload(device, enc, a);
            var gb = Devices.upload(device, enc, b);
            var gc = Devices.upload(device, enc, c);
            gemm.gemm(enc, ta, tb, m, n, k, alpha, ga, aOff, lda, gb, bOff, ldb, beta, gc, cOff, ldc);
            var read = enc.download(gc);
            Devices.run(device, enc);
            Bits.assertSame(expected, Devices.floats(read, c.length), Devices.hardware(backend),
                    backend + " gemm " + m + "x" + n + "x" + k + " transA=" + ta + " transB=" + tb);
        }
    }

    @ParameterizedTest(name = "{0} {1}x{2}x{3} transA={4} transB={5}")
    @MethodSource("cases")
    void matchesGemmf(String backend, int m, int n, int k, boolean ta, boolean tb) {
        SplittableRandom r = new SplittableRandom(m * 31L + n * 7L + k + (ta ? 100 : 0) + (tb ? 1000 : 0));
        int aRows = ta ? k : m;
        int aCols = ta ? m : k;
        int bRows = tb ? n : k;
        int bCols = tb ? k : n;
        // strides and offsets that are not the dense ones
        int lda = aCols + 3;
        int ldb = bCols + 1;
        int ldc = n + 2;
        int aOff = 5;
        int bOff = 2;
        int cOff = 7;
        float[] a = random(r, aOff + Math.max(0, aRows) * lda + 4);
        float[] b = random(r, bOff + Math.max(0, bRows) * ldb + 4);
        float[] c = random(r, cOff + m * ldc + 4);
        check(backend, ta, tb, m, n, k, 1.25f, -0.5f, a, aOff, lda, b, bOff, ldb, c, cOff, ldc);
        // beta == 0: C is not read, so its NaNs must not reach the result
        float[] nan = new float[c.length];
        Arrays.fill(nan, Float.NaN);
        check(backend, ta, tb, m, n, k, -0.75f, 0f, a, aOff, lda, b, bOff, ldb, nan, cOff, ldc);
    }

    static Stream<String> backends() {
        return Devices.names().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void specialValuesPropagateAsOnTheCpu(String backend) {
        SplittableRandom r = new SplittableRandom(9);
        int m = 9;
        int n = 11;
        int k = 300;
        float[] a = random(r, m * k);
        float[] b = random(r, k * n);
        a[3 * k + 10] = Float.NaN;
        a[5 * k + 299] = Float.POSITIVE_INFINITY;
        b[200 * n + 4] = Float.NEGATIVE_INFINITY;
        b[17 * n + 6] = -0f;
        for (int j = 0; j < k; j++) {
            a[7 * k + j] = 0f; // a zero row: products of +0 and -0
            b[j * n + 9] = -b[j * n + 9];
        }
        float[] c = random(r, m * n);
        check(backend, false, false, m, n, k, 1f, 1f, a, 0, k, b, 0, n, c, 0, n);
        check(backend, false, false, m, n, k, -1f, 0f, a, 0, k, b, 0, n, c, 0, n);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void sameBufferForAAndB(String backend) {
        SplittableRandom r = new SplittableRandom(4);
        int n = 37;
        int k = 513;
        float[] a = random(r, n * k);
        float[] expected = new float[n * n];
        // A·Aᵀ, both operands read from one buffer
        Gemmf.gemm(false, true, n, n, k, 1f, a, 0, k, a, 0, k, 0f, expected, 0, n,
                new float[Gemmf.scratchSize(false, true, n, n, k)], 0);
        try (GpuDevice device = Devices.open(backend); GpuGemmf gemm = new GpuGemmf(device);
                var enc = device.encoder()) {
            var ga = Devices.upload(device, enc, a);
            var gc = Devices.buffer(device, n * n);
            gemm.gemm(enc, false, true, n, n, k, 1f, ga, 0, k, ga, 0, k, 0f, gc, 0, n);
            var read = enc.download(gc);
            Devices.run(device, enc);
            Bits.assertSame(expected, Devices.floats(read, n * n), Devices.hardware(backend), backend + " A·Aᵀ");
        }
    }
}
