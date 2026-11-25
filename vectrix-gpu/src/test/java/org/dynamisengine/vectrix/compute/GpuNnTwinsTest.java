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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.vectrix.compute.GpuPointwise.Op;
import org.dynamisengine.vectrix.nn.Embedding;
import org.dynamisengine.vectrix.nn.Softmax;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The 4.1 twins against Vectrix, bit for bit: {@link GpuPointwise} (every op and aliasing layout), {@link GpuEmbedding}
 * (gather, and the scatter-add in token order) and the fully masked row rule of {@link GpuSoftmax}.
 */
class GpuNnTwinsTest {

    enum Layout { SEPARATE, OUT_IS_A, OUT_IS_B, ALL_SAME }

    private static final int[] SIZES = {1, 3, 255, 257, 4099, 100_003};

    static Stream<Arguments> pointwiseCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (Op op : Op.values()) {
                // exp and log port ReproducibleMath with fused multiply-adds
                if ((op == Op.EXP || op == Op.LOG) && !Devices.fusesFma(backend)) {
                    continue;
                }
                for (Layout layout : Layout.values()) {
                    if (op.binary() || layout == Layout.SEPARATE || layout == Layout.OUT_IS_A) {
                        out.add(Arguments.of(backend, op, layout));
                    }
                }
            }
        }
        return out.stream();
    }

    private static float[] values(SplittableRandom r, int n) {
        float[] a = new float[n];
        float[] s = GpuActivationsTest.SPECIALS;
        for (int i = 0; i < n; i++) {
            int k = r.nextInt(16);
            a[i] = k == 0 ? s[r.nextInt(s.length)] : k == 1 ? Float.intBitsToFloat(r.nextInt())
                    : (float) (r.nextDouble() * 200 - 100);
        }
        return a;
    }

    private static float[] padded(int pad, float[] v) {
        float[] out = new float[pad + v.length + 2];
        Arrays.fill(out, 12345f);
        System.arraycopy(v, 0, out, pad, v.length);
        return out;
    }

    private static void record(GpuPointwise pw, Op op, GpuCommandEncoder enc, GpuBuffer a, long aOff, GpuBuffer b,
            long bOff, GpuBuffer y, long yOff, int n) {
        switch (op) {
            case EXP -> pw.exp(enc, a, aOff, y, yOff, n);
            case LOG -> pw.log(enc, a, aOff, y, yOff, n);
            case SQRT -> pw.sqrt(enc, a, aOff, y, yOff, n);
            case NEG -> pw.neg(enc, a, aOff, y, yOff, n);
            case SQUARE -> pw.square(enc, a, aOff, y, yOff, n);
            case ADD -> pw.add(enc, a, aOff, b, bOff, y, yOff, n);
            case SUB -> pw.sub(enc, a, aOff, b, bOff, y, yOff, n);
            case MUL -> pw.mul(enc, a, aOff, b, bOff, y, yOff, n);
            case DIV -> pw.div(enc, a, aOff, b, bOff, y, yOff, n);
            case MAX -> pw.max(enc, a, aOff, b, bOff, y, yOff, n);
            case MIN -> pw.min(enc, a, aOff, b, bOff, y, yOff, n);
        }
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("pointwiseCases")
    void pointwiseMatchesVectrix(String backend, Op op, Layout layout) {
        boolean hardware = Devices.hardware(backend);
        try (GpuDevice device = Devices.open(backend); GpuPointwise pw = new GpuPointwise(device);
                var enc = device.encoder()) {
            List<Runnable> checks = new ArrayList<>();
            for (int n : SIZES) {
                SplittableRandom r = new SplittableRandom(n * 131L + op.ordinal() * 7L + layout.ordinal());
                float[] a = padded(3, values(r, n));
                float[] b = padded(5, values(r, n));
                float[] expected;
                GpuBuffer out;
                switch (layout) {
                    case SEPARATE -> {
                        float[] y = padded(2, new float[n]);
                        expected = y.clone();
                        NnModels.apply(op.ordinal(), a, 3, b, 5, expected, 2, n);
                        out = Devices.upload(device, enc, y);
                        record(pw, op, enc, Devices.upload(device, enc, a), 3, Devices.upload(device, enc, b), 5, out,
                                2, n);
                    }
                    case OUT_IS_A -> {
                        expected = a.clone();
                        NnModels.apply(op.ordinal(), expected, 3, b, 5, expected, 3, n);
                        out = Devices.upload(device, enc, a);
                        record(pw, op, enc, out, 3, Devices.upload(device, enc, b), 5, out, 3, n);
                    }
                    case OUT_IS_B -> {
                        expected = b.clone();
                        NnModels.apply(op.ordinal(), a, 3, expected, 5, expected, 5, n);
                        out = Devices.upload(device, enc, b);
                        record(pw, op, enc, Devices.upload(device, enc, a), 3, out, 5, out, 5, n);
                    }
                    default -> {
                        expected = a.clone();
                        NnModels.apply(op.ordinal(), expected, 3, expected, 3, expected, 3, n);
                        out = Devices.upload(device, enc, a);
                        record(pw, op, enc, out, 3, out, 3, out, 3, n);
                    }
                }
                var read = enc.download(out);
                float[] e = expected;
                float[] flushed = flushedResult(op, layout, a, b, n);
                checks.add(() -> {
                    float[] got = Devices.floats(read, e.length);
                    float[] allowed = e.clone();
                    if (hardware) { // a GPU that flushes subnormal inputs computes from zeros
                        for (int i = 0; i < got.length; i++) {
                            if (Float.floatToIntBits(flushed[i]) == Float.floatToIntBits(got[i])) {
                                allowed[i] = got[i];
                            }
                        }
                    }
                    Bits.assertSame(allowed, got, hardware, backend + " " + op + " " + layout + " n=" + n);
                });
            }
            Devices.run(device, enc);
            checks.forEach(Runnable::run);
        }
    }

    /** The layout's result computed from inputs whose subnormals are replaced by zeros of the same sign. */
    private static float[] flushedResult(Op op, Layout layout, float[] a0, float[] b0, int n) {
        float[] a = flush(a0);
        float[] b = flush(b0);
        switch (layout) {
            case SEPARATE -> {
                float[] y = padded(2, new float[n]);
                NnModels.apply(op.ordinal(), a, 3, b, 5, y, 2, n);
                return y;
            }
            case OUT_IS_A -> {
                NnModels.apply(op.ordinal(), a, 3, b, 5, a, 3, n);
                return a;
            }
            case OUT_IS_B -> {
                NnModels.apply(op.ordinal(), a, 3, b, 5, b, 5, n);
                return b;
            }
            default -> {
                NnModels.apply(op.ordinal(), a, 3, a, 3, a, 3, n);
                return a;
            }
        }
    }

    private static float[] flush(float[] v) {
        float[] out = v.clone();
        for (int i = 0; i < out.length; i++) {
            if (Bits.subnormal(out[i])) {
                out[i] = Math.copySign(0f, out[i]);
            }
        }
        return out;
    }

    @Test
    void pointwiseRejectsBadAliasing() {
        try (GpuDevice device = Devices.open("reference"); GpuPointwise pw = new GpuPointwise(device);
                var enc = device.encoder()) {
            GpuBuffer a = Devices.buffer(device, 16);
            GpuBuffer b = Devices.buffer(device, 16);
            assertThrows(IllegalArgumentException.class, () -> pw.add(enc, a, 0, b, 0, a, 1, 4));
            assertThrows(IllegalArgumentException.class, () -> pw.exp(enc, a, 0, a, 2, 4));
            assertThrows(IllegalArgumentException.class, () -> pw.mul(enc, a, 0, b, 0, b, 0, 17));
        }
    }

    static Stream<String> backends() {
        return Devices.names().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void embeddingMatchesVectrix(String backend) {
        boolean hardware = Devices.hardware(backend);
        try (GpuDevice device = Devices.open(backend); GpuEmbedding emb = new GpuEmbedding(device);
                var enc = device.encoder()) {
            List<Runnable> checks = new ArrayList<>();
            int seed = 0;
            for (int[] shape : new int[][] {{1, 1, 1}, {7, 3, 20}, {50, 64, 300}, {1000, 17, 4099}, {3, 300, 2000}}) {
                int rows = shape[0];
                int dim = shape[1];
                int count = shape[2];
                SplittableRandom r = new SplittableRandom(++seed);
                int[] idx = new int[count + 2];
                for (int t = 0; t < idx.length; t++) {
                    idx[t] = r.nextInt(4) == 0 ? 0 : r.nextInt(rows); // row 0 repeats often
                }
                float[] table = padded(4, values(r, rows * dim));
                // gather
                float[] out = padded(1, new float[count * dim]);
                float[] gathered = out.clone();
                Embedding.gather(table, 4, rows, dim, idx, 2, count, gathered, 1);
                GpuBuffer gOut = Devices.upload(device, enc, out);
                emb.gather(enc, Devices.upload(device, enc, table), 4, rows, dim, Devices.upload(device, enc, idx), 2,
                        count, gOut, 1);
                var readGather = enc.download(gOut);
                // scatter-add
                float[] dOut = padded(3, values(r, count * dim));
                float[] dTable = padded(2, values(r, rows * dim));
                float[] summed = dTable.clone();
                Embedding.scatterAdd(dOut, 3, idx, 2, count, dim, summed, 2, rows);
                GpuEmbedding.Plan plan = GpuEmbedding.plan(idx, 2, count, rows);
                int[] planData = new int[5 + plan.data().length];
                System.arraycopy(plan.data(), 0, planData, 5, plan.data().length);
                GpuBuffer gTable = Devices.upload(device, enc, dTable);
                emb.scatterAdd(enc, Devices.upload(device, enc, dOut), 3, plan, Devices.upload(device, enc, planData),
                        5, dim, gTable, 2);
                var readScatter = enc.download(gTable);
                String what = backend + " rows=" + rows + " dim=" + dim + " count=" + count;
                checks.add(() -> Bits.assertSame(gathered, Devices.floats(readGather, gathered.length), hardware,
                        what + " gather"));
                checks.add(() -> Bits.assertSame(summed, Devices.floats(readScatter, summed.length), hardware,
                        what + " scatterAdd"));
            }
            // an out-of-range index gives a NaN row
            float[] table = {1, 2, 3, 4};
            GpuBuffer gOut = Devices.buffer(device, 6);
            emb.gather(enc, Devices.upload(device, enc, table), 0, 2, 2, Devices.upload(device, enc, new int[] {1, 2, -1}),
                    0, 3, gOut, 0);
            var bad = enc.download(gOut);
            Devices.run(device, enc);
            checks.forEach(Runnable::run);
            float[] got = Devices.floats(bad, 6);
            assertEquals(3f, got[0]);
            assertEquals(4f, got[1]);
            for (int i = 2; i < 6; i++) {
                assertEquals(Float.NaN, got[i], "element " + i);
            }
        }
    }

    @Test
    void planChecksIndicesAndOrdersTokens() {
        GpuEmbedding.Plan plan = GpuEmbedding.plan(new int[] {9, 2, 0, 2, 2, 0}, 1, 5, 3);
        // indices 2, 0, 2, 2, 0: rows 0 and 2; starts 0, 2, 5; tokens of row 0: [1, 4]; of row 2: [0, 2, 3]
        assertEquals(List.of(0, 2, 0, 2, 5, 1, 4, 0, 2, 3), Arrays.stream(plan.data()).boxed().toList());
        assertThrows(IllegalArgumentException.class, () -> GpuEmbedding.plan(new int[] {0, 3}, 0, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> GpuEmbedding.plan(new int[] {0, -1}, 0, 2, 3));
    }

    static Stream<String> fusedBackends() {
        return Devices.namesWithFusedFma().stream();
    }

    /** Rows of all -inf give zero probabilities and -inf log-probabilities, as Softmax does since 4.1. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("fusedBackends")
    void fullyMaskedRowsMatchSoftmax(String backend) {
        boolean hardware = Devices.hardware(backend);
        float inf = Float.NEGATIVE_INFINITY;
        int rows = 6;
        int n = 300;
        float[] x = new float[rows * n];
        SplittableRandom r = new SplittableRandom(5);
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) (r.nextDouble() * 10 - 5);
        }
        for (int row : new int[] {0, 3, 5}) {
            Arrays.fill(x, row * n, row * n + n, inf); // fully masked
        }
        Arrays.fill(x, n, 2 * n - 1, inf); // all but one masked
        float[] soft = new float[x.length];
        float[] logSoft = new float[x.length];
        for (int row = 0; row < rows; row++) {
            Softmax.softmax(x, row * n, soft, row * n, n);
            Softmax.logSoftmax(x, row * n, logSoft, row * n, n, new float[n], 0);
        }
        try (GpuDevice device = Devices.open(backend); GpuSoftmax gs = new GpuSoftmax(device);
                var enc = device.encoder()) {
            GpuBuffer gx = Devices.upload(device, enc, x);
            GpuBuffer y1 = Devices.buffer(device, x.length);
            GpuBuffer y2 = Devices.buffer(device, x.length);
            gs.softmax(enc, rows, gx, 0, n, y1, 0, n, n);
            gs.logSoftmax(enc, rows, gx, 0, n, y2, 0, n, n);
            var r1 = enc.download(y1);
            var r2 = enc.download(y2);
            Devices.run(device, enc);
            Bits.assertSame(soft, Devices.floats(r1, x.length), hardware, backend + " softmax");
            Bits.assertSame(logSoft, Devices.floats(r2, x.length), hardware, backend + " logSoftmax");
        }
    }
}
