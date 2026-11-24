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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.vectrix.blas.Blasf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link GpuBlasf} against {@code Blasf}, bit for bit, on every enabled backend. */
class GpuBlasfTest {

    /**
     * Lengths: empty, within one block, at the lane and block boundaries, one first-pass group (32 blocks) and
     * just past it, and long vectors whose trees need two, three and four upper passes.
     */
    private static final int[] SIZES = {0, 1, 7, 8, 9, 255, 256, 257, 1000, 8191, 8192, 8193, 65_539, 131_073,
        1_000_003, 3_000_017};

    private static final int X_OFF = 3;
    private static final int Y_OFF = 5;
    private static final int SCRATCH_OFF = 7;

    private static final int DOT = 0;
    private static final int SUM = 1;
    private static final int ASUM = 2;
    private static final int NRM2 = 3;
    private static final int IAMAX = 4;
    private static final String[] NAMES = {"dot", "sum", "asum", "nrm2", "iamax"};

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (int n : SIZES) {
                out.add(Arguments.of(backend, n));
            }
        }
        return out.stream();
    }

    static Stream<String> backends() {
        return Devices.names().stream();
    }

    static float[] random(SplittableRandom r, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = (float) (r.nextDouble() * 2 - 1);
        }
        return a;
    }

    /** Random values of either sign with magnitudes 2^lo to 2^hi. */
    static float[] magnitudes(SplittableRandom r, int n, int lo, int hi) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            float v = Math.scalb((float) (1 + r.nextDouble()), lo + r.nextInt(hi - lo));
            a[i] = r.nextBoolean() ? v : -v;
        }
        return a;
    }

    /** Scratch filled with garbage, including the nrm2 state values, so no pass may rely on its contents. */
    static GpuBuffer garbageScratch(GpuDevice device, GpuCommandEncoder enc, long elements, SplittableRandom r) {
        int[] g = new int[Math.toIntExact(SCRATCH_OFF + elements + 5)];
        for (int i = 0; i < g.length; i++) {
            g[i] = r.nextInt();
        }
        g[SCRATCH_OFF] = 2;
        g[SCRATCH_OFF + 1] = 64;
        return Devices.upload(device, enc, g);
    }

    /** The five reductions of {@code x} (and {@code y} for dot), from one encoder and one scratch. */
    static int[] reduceOnGpu(String backend, float[] x, int xOff, float[] y, int yOff, int n) {
        try (GpuDevice device = Devices.open(backend); GpuBlasf blas = new GpuBlasf(device);
                var enc = device.encoder()) {
            var gx = Devices.upload(device, enc, x);
            var gy = Devices.upload(device, enc, y);
            var out = Devices.upload(device, enc, new int[] {-7, -7, -7, -7, -7, -7, -7, -7});
            var scratch = garbageScratch(device, enc, GpuBlasf.scratchSize(n), new SplittableRandom(n));
            blas.dot(enc, gx, xOff, gy, yOff, n, out, 1 + DOT, scratch, SCRATCH_OFF);
            blas.sum(enc, gx, xOff, n, out, 1 + SUM, scratch, SCRATCH_OFF);
            blas.asum(enc, gx, xOff, n, out, 1 + ASUM, scratch, SCRATCH_OFF);
            blas.nrm2(enc, gx, xOff, n, out, 1 + NRM2, scratch, SCRATCH_OFF);
            blas.iamax(enc, gx, xOff, n, out, 1 + IAMAX, scratch, SCRATCH_OFF);
            var read = enc.download(out);
            Devices.run(device, enc);
            int[] all = Devices.ints(read, 8);
            assertEquals(-7, all[0], "out[0] untouched");
            assertEquals(-7, all[6], "out[6] untouched");
            int[] r = new int[5];
            System.arraycopy(all, 1, r, 0, 5);
            return r;
        }
    }

    /** Compares every reduction of {@code x} (and {@code y}) with Blasf. */
    static void checkReductions(String backend, float[] x, int xOff, float[] y, int yOff, int n, String what) {
        float[] expected = {Blasf.dot(x, xOff, y, yOff, n), Blasf.sum(x, xOff, n), Blasf.asum(x, xOff, n),
            Blasf.nrm2(x, xOff, n)};
        int[] got = reduceOnGpu(backend, x, xOff, y, yOff, n);
        boolean hw = Devices.hardware(backend);
        for (int op = DOT; op <= NRM2; op++) {
            Bits.assertSame(new float[] {expected[op]}, new float[] {Float.intBitsToFloat(got[op])}, hw,
                    backend + " " + NAMES[op] + " " + what);
        }
        assertEquals(Blasf.iamax(x, xOff, n), got[IAMAX], backend + " iamax " + what);
    }

    @ParameterizedTest(name = "{0} n={1}")
    @MethodSource("cases")
    void reductionsMatchBlasf(String backend, int n) {
        SplittableRandom r = new SplittableRandom(n * 31L + 5);
        float[] x = random(r, X_OFF + n + 4);
        float[] y = random(r, Y_OFF + n + 2);
        checkReductions(backend, x, X_OFF, y, Y_OFF, n, "n=" + n);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void sumOfAVectorWithAFourLevelTree(String backend) {
        // 131,073 blocks: 8192 first-pass groups and an upper pass of 512 nodes, more than one row of groups
        int n = 33_554_433;
        SplittableRandom r = new SplittableRandom(77);
        float[] x = random(r, n + 1);
        float expected = Blasf.sum(x, 1, n);
        try (GpuDevice device = Devices.open(backend); GpuBlasf blas = new GpuBlasf(device);
                var enc = device.encoder()) {
            var gx = Devices.upload(device, enc, x);
            var out = Devices.buffer(device, 1);
            var scratch = Devices.buffer(device, GpuBlasf.scratchSize(n));
            blas.sum(enc, gx, 1, n, out, 0, scratch, 0);
            var read = enc.download(out);
            Devices.run(device, enc);
            Bits.assertSame(new float[] {expected}, Devices.floats(read, 1), Devices.hardware(backend),
                    backend + " sum n=" + n);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void dotOfAVectorWithItself(String backend) {
        int n = 70_001;
        float[] x = random(new SplittableRandom(3), n + 2);
        float expected = Blasf.dot(x, 2, x, 2, n);
        try (GpuDevice device = Devices.open(backend); GpuBlasf blas = new GpuBlasf(device);
                var enc = device.encoder()) {
            var gx = Devices.upload(device, enc, x);
            var out = Devices.buffer(device, 1);
            var scratch = Devices.buffer(device, GpuBlasf.scratchSize(n));
            blas.dot(enc, gx, 2, gx, 2, n, out, 0, scratch, 0);
            var read = enc.download(out);
            Devices.run(device, enc);
            Bits.assertSame(new float[] {expected}, Devices.floats(read, 1), Devices.hardware(backend),
                    backend + " x·x");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void specialValuesPropagateAsOnTheCpu(String backend) {
        for (int n : new int[] {300, 70_000}) {
            SplittableRandom r = new SplittableRandom(n);
            // NaN, infinities and signed zeros, one kind at a time and together
            float[][] specials = {
                {Float.NaN}, {Float.POSITIVE_INFINITY}, {Float.NEGATIVE_INFINITY},
                {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}, {Float.POSITIVE_INFINITY, Float.NaN},
                {-0f, 0f, -0f}, {Float.NaN, Float.NaN},
            };
            for (float[] s : specials) {
                float[] x = random(r, n + X_OFF);
                float[] y = random(r, n + Y_OFF);
                for (int j = 0; j < s.length; j++) {
                    x[X_OFF + r.nextInt(n)] = s[j];
                    y[Y_OFF + r.nextInt(n)] = s[j];
                }
                checkReductions(backend, x, X_OFF, y, Y_OFF, n, "n=" + n + " with " + java.util.Arrays.toString(s));
            }
            // all zeros of both signs: nrm2's rescaling finds a zero maximum
            float[] z = new float[n + X_OFF];
            for (int i = 0; i < z.length; i++) {
                z[i] = r.nextBoolean() ? 0f : -0f;
            }
            checkReductions(backend, z, X_OFF, z.clone(), X_OFF, n, "n=" + n + " zeros");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void nrm2RescalesAsOnTheCpu(String backend) {
        SplittableRandom r = new SplittableRandom(11);
        for (int n : new int[] {1, 9, 1000, 300_001}) {
            // squares that underflow to zero; that overflow; elements up to 2^128 (the scale is 2^-127, a
            // subnormal); one element near Float.MAX_VALUE among smaller ones; and an infinity among huge ones.
            // The ranges keep every scaled square normal: Blasf adds subnormal squares, a GPU may flush them
            List<float[]> inputs = new ArrayList<>();
            inputs.add(magnitudes(r, n + X_OFF, -110, -80));
            inputs.add(magnitudes(r, n + X_OFF, 70, 110));
            inputs.add(magnitudes(r, n + X_OFF, 100, 128));
            float[] one = magnitudes(r, n + X_OFF, 96, 110);
            one[X_OFF + r.nextInt(n)] = 3e38f;
            inputs.add(one);
            float[] inf = magnitudes(r, n + X_OFF, 80, 90);
            inf[X_OFF + r.nextInt(n)] = Float.NEGATIVE_INFINITY;
            inputs.add(inf);
            for (float[] x : inputs) {
                checkReductions(backend, x, X_OFF, x.clone(), X_OFF, n, "nrm2 rescaling n=" + n);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void nrm2AndIamaxOfSubnormals(String backend) {
        // Only nrm2 and iamax: they read subnormal elements exactly (as bits, and scaled from the integer
        // significand), so even a GPU that flushes subnormals matches. The sums are not checked here: there a
        // flushed subnormal changes the result.
        SplittableRandom r = new SplittableRandom(21);
        for (int n : new int[] {1, 37, 5000}) {
            for (int kind = 0; kind < 3; kind++) {
                float[] x = new float[n + X_OFF];
                for (int i = 0; i < x.length; i++) {
                    float sub = Float.intBitsToFloat(1 + r.nextInt(0x007FFFFF));
                    float v = switch (kind) {
                        case 0 -> sub; // all subnormal: the maximum is subnormal, the scale is 2^127
                        case 1 -> r.nextInt(4) == 0 ? Math.scalb((float) (1 + r.nextDouble()), -110) : sub;
                        default -> r.nextInt(8) == 0 ? 0f : sub * (1 + r.nextInt(3)) / 4f;
                    };
                    x[i] = r.nextBoolean() ? v : -v;
                }
                float expected = Blasf.nrm2(x, X_OFF, n);
                int[] got = reduceOnGpu(backend, x, X_OFF, x, X_OFF, n);
                Bits.assertSame(new float[] {expected}, new float[] {Float.intBitsToFloat(got[NRM2])},
                        Devices.hardware(backend), backend + " nrm2 of subnormals, kind " + kind + ", n=" + n);
                assertEquals(Blasf.iamax(x, X_OFF, n), got[IAMAX], backend + " iamax of subnormals n=" + n);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void iamaxTiesAndNaNs(String backend) {
        int n = 1_000_003;
        SplittableRandom r = new SplittableRandom(5);
        float[] base = random(r, n);
        // {positions, values}: ties of the maximum in different groups (the first wins, whatever the sign); a NaN
        // after a larger infinity (the NaN wins); two NaNs (the first); infinities of both signs tied
        int[][] at = {{900_001, 17}, {500_000, 499_999}, {12, 800_000}, {700_000, 3, 600_000}, {40, 41, 900_000},
            {999_000, 1}};
        float[][] values = {{7f, -7f}, {-7f, 7f}, {Float.POSITIVE_INFINITY, Float.NaN},
            {Float.NaN, Float.POSITIVE_INFINITY, -Float.NaN}, {Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY}, {-0f, 0f}};
        for (int c = 0; c < at.length; c++) {
            float[] x = base.clone();
            if (c == at.length - 1) {
                java.util.Arrays.fill(x, 0f); // only zeros: the first index
            }
            for (int j = 0; j < at[c].length; j++) {
                x[at[c][j]] = values[c][j];
            }
            for (int len : new int[] {n, 257, 9}) {
                int[] got = reduceOnGpu(backend, x, 0, x, 0, len);
                assertEquals(Blasf.iamax(x, 0, len), got[IAMAX], backend + " iamax case " + c + " n=" + len);
            }
        }
    }

    @Test
    void scratchSizeCoversEveryPass() {
        assertEquals(4, GpuBlasf.scratchSize(0));
        assertEquals(4, GpuBlasf.scratchSize(1));
        assertEquals(2 + 2 * 733 + 32, GpuBlasf.scratchSize(3_000_017)); // 512 subtrees, 733 parts
        assertEquals(278_530, GpuBlasf.scratchSize(Integer.MAX_VALUE));
    }

    static Stream<Arguments> elementwiseCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (int n : new int[] {1, 7, 255, 256, 257, 1000, 2049, 100_003}) {
                out.add(Arguments.of(backend, n));
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} n={1}")
    @MethodSource("elementwiseCases")
    void axpyAndScalMatchBlasf(String backend, int n) {
        SplittableRandom r = new SplittableRandom(n);
        float[] x = random(r, X_OFF + n + 3);
        float[] y = random(r, Y_OFF + n + 3);
        float[] z = random(r, X_OFF + n + 3);
        x[X_OFF] = Float.NaN;
        y[Y_OFF + n / 2] = Float.NEGATIVE_INFINITY;
        z[X_OFF + n - 1] = -0f;
        float a = -1.37f;
        float[] ey = y.clone();
        Blasf.axpy(a, x, X_OFF, ey, Y_OFF, n);
        float[] ez = z.clone();
        Blasf.axpy(a, ez, X_OFF, ez, X_OFF, n);
        float[] ex = x.clone();
        Blasf.scal(0.3f, ex, X_OFF, n);
        try (GpuDevice device = Devices.open(backend); GpuBlasf blas = new GpuBlasf(device);
                var enc = device.encoder()) {
            var gx = Devices.upload(device, enc, x);
            var gy = Devices.upload(device, enc, y);
            var gz = Devices.upload(device, enc, z);
            blas.axpy(enc, a, gx, X_OFF, gy, Y_OFF, n);
            blas.axpy(enc, a, gz, X_OFF, gz, X_OFF, n); // in place
            blas.scal(enc, 0.3f, gx, X_OFF, n);
            var ry = enc.download(gy);
            var rz = enc.download(gz);
            var rx = enc.download(gx);
            Devices.run(device, enc);
            boolean hw = Devices.hardware(backend);
            Bits.assertSame(ey, Devices.floats(ry, y.length), hw, backend + " axpy n=" + n);
            Bits.assertSame(ez, Devices.floats(rz, z.length), hw, backend + " axpy in place n=" + n);
            Bits.assertSame(ex, Devices.floats(rx, x.length), hw, backend + " scal n=" + n);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void axpyAndScalSpecialScales(String backend) {
        int n = 600;
        SplittableRandom r = new SplittableRandom(8);
        for (float a : new float[] {0f, -0f, Float.NaN, Float.POSITIVE_INFINITY, 1f, 3e38f}) {
            float[] x = random(r, n);
            float[] y = random(r, n);
            x[5] = Float.POSITIVE_INFINITY;
            x[6] = 0f;
            y[7] = -0f;
            x[7] = 0f;
            float[] ey = y.clone();
            Blasf.axpy(a, x, 0, ey, 0, n);
            float[] ex = x.clone();
            Blasf.scal(a, ex, 0, n);
            try (GpuDevice device = Devices.open(backend); GpuBlasf blas = new GpuBlasf(device);
                    var enc = device.encoder()) {
                var gx = Devices.upload(device, enc, x);
                var gy = Devices.upload(device, enc, y);
                blas.axpy(enc, a, gx, 0, gy, 0, n);
                blas.scal(enc, a, gx, 0, n);
                var ry = enc.download(gy);
                var rx = enc.download(gx);
                Devices.run(device, enc);
                boolean hw = Devices.hardware(backend);
                Bits.assertSame(ey, Devices.floats(ry, n), hw, backend + " axpy a=" + a);
                Bits.assertSame(ex, Devices.floats(rx, n), hw, backend + " scal a=" + a);
            }
        }
    }
}
