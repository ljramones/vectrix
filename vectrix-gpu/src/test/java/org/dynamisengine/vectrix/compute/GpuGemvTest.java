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

/** {@link GpuGemmf#gemv} against {@code Gemmf.gemv}, bit for bit, on every enabled backend. */
class GpuGemvTest {

    /**
     * {m, n}: partial groups, every lane and block boundary, deep n-trees, more groups than the reference grid's
     * width (7 groups of 64 rows), and n == 0.
     */
    private static final int[][] SHAPES = {
        {1, 1}, {3, 7}, {64, 8}, {65, 255}, {7, 256}, {20, 257}, {5, 1000}, {130, 2049}, {500, 300}, {2, 9000},
        {1, 70_001}, {7, 0},
    };

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (int[] s : SHAPES) {
                for (boolean ta : new boolean[] {false, true}) {
                    out.add(Arguments.of(backend, s[0], s[1], ta));
                }
            }
        }
        return out.stream();
    }

    static Stream<String> backends() {
        return Devices.namesWithFusedFma().stream();
    }

    /** Runs one product on {@code backend} and on the CPU and compares every element of {@code y}. */
    static void check(String backend, boolean ta, int m, int n, float alpha, float[] a, int aOff, int lda, float[] x,
            int xOff, float beta, float[] y, int yOff) {
        float[] expected = y.clone();
        Gemmf.gemv(ta, m, n, alpha, a, aOff, lda, x, xOff, beta, expected, yOff, new float[n], 0);
        try (GpuDevice device = Devices.open(backend); GpuGemmf gemm = new GpuGemmf(device);
                var enc = device.encoder()) {
            var ga = Devices.upload(device, enc, a);
            var gx = Devices.upload(device, enc, x);
            var gy = Devices.upload(device, enc, y);
            gemm.gemv(enc, ta, m, n, alpha, ga, aOff, lda, gx, xOff, beta, gy, yOff);
            var read = enc.download(gy);
            Devices.run(device, enc);
            Bits.assertSame(expected, Devices.floats(read, y.length), Devices.hardware(backend),
                    backend + " gemv " + m + "x" + n + " transA=" + ta);
        }
    }

    @ParameterizedTest(name = "{0} {1}x{2} transA={3}")
    @MethodSource("cases")
    void matchesGemv(String backend, int m, int n, boolean ta) {
        SplittableRandom r = new SplittableRandom(m * 31L + n + (ta ? 100 : 0));
        int aRows = ta ? n : m;
        int aCols = ta ? m : n;
        int lda = aCols + 3; // not the dense stride
        int aOff = 5;
        int xOff = 2;
        int yOff = 7;
        float[] a = GpuGemmfTest.random(r, aOff + aRows * lda + 4);
        float[] x = GpuGemmfTest.random(r, xOff + n + 3);
        float[] y = GpuGemmfTest.random(r, yOff + m + 4);
        check(backend, ta, m, n, 1.25f, a, aOff, lda, x, xOff, -0.5f, y, yOff);
        // beta == 0: y is not read, so its NaNs must not reach the result
        float[] nan = new float[y.length];
        Arrays.fill(nan, Float.NaN);
        check(backend, ta, m, n, -0.75f, a, aOff, lda, x, xOff, 0f, nan, yOff);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void specialValuesPropagateAsOnTheCpu(String backend) {
        SplittableRandom r = new SplittableRandom(9);
        int m = 9;
        int n = 300;
        for (boolean ta : new boolean[] {false, true}) {
            float[] a = GpuGemmfTest.random(r, m * n);
            float[] x = GpuGemmfTest.random(r, n);
            int lda = ta ? m : n;
            a[ta ? 10 * lda + 3 : 3 * lda + 10] = Float.NaN;
            a[ta ? 299 * lda + 5 : 5 * lda + 299] = Float.POSITIVE_INFINITY;
            x[200] = Float.NEGATIVE_INFINITY;
            x[17] = -0f;
            for (int j = 0; j < n; j++) {
                a[ta ? j * lda + 7 : 7 * lda + j] = 0f; // a zero row of op(A): products of +0 and -0
            }
            float[] y = GpuGemmfTest.random(r, m);
            check(backend, ta, m, n, 1f, a, 0, lda, x, 0, 1f, y, 0);
            check(backend, ta, m, n, -1f, a, 0, lda, x, 0, 0f, y, 0);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void matrixAndVectorInOneBuffer(String backend) {
        SplittableRandom r = new SplittableRandom(4);
        int m = 37;
        int n = 513;
        // A at 0, x right after it, read from one buffer
        float[] ax = GpuGemmfTest.random(r, m * n + n);
        float[] expected = new float[m];
        Gemmf.gemv(false, m, n, 1f, ax, 0, n, ax, m * n, 0f, expected, 0, null, 0);
        try (GpuDevice device = Devices.open(backend); GpuGemmf gemm = new GpuGemmf(device);
                var enc = device.encoder()) {
            var gax = Devices.upload(device, enc, ax);
            var gy = Devices.buffer(device, m);
            gemm.gemv(enc, false, m, n, 1f, gax, 0, n, gax, m * n, 0f, gy, 0);
            var read = enc.download(gy);
            Devices.run(device, enc);
            Bits.assertSame(expected, Devices.floats(read, m), Devices.hardware(backend), backend + " A·x, one buffer");
        }
    }
}
