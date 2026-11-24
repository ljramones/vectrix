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
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.vectrix.nn.Attention;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link GpuAttention} against {@code nn.Attention}, bit for bit, on every enabled backend: each query (or position)
 * of a GPU call is compared with one CPU call, and the backward passes with the CPU method called for the queries in
 * order (so the accumulated key and value gradients are compared in the CPU's order).
 */
class GpuAttentionTest {

    /** No mask, one mask row per query, one mask row shared by every query. */
    enum MaskMode { NONE, PER_QUERY, SHARED }

    /** Values the outputs start with, so entries a call must not write are checked too. */
    private static final float SENTINEL = 3.5f;

    /**
     * One problem. {@code grouped} selects {@link GpuAttention#groupedQuery} (dense per-position layouts, as on the
     * CPU); otherwise {@code heads == kvHeads == 1} and {@link GpuAttention#forward} with offsets and padded strides.
     * {@code sharedKv} (not grouped) puts the keys and values in one buffer, rows of {@code [key | value]}.
     */
    record Problem(boolean grouped, boolean sharedKv, int queries, int heads, int kvHeads, int d, int dv, int t,
            boolean causal, MaskMode maskMode) {

        int rows() {
            return queries * heads;
        }

        /** The keys query {@code r} attends to. */
        int keys(int r) {
            return causal ? t - queries + 1 + r : t;
        }

        float scale() {
            return (float) (1 / Math.sqrt(d));
        }

        int kRow() {
            return kvHeads * d;
        }

        int vRow() {
            return kvHeads * dv;
        }

        // Layouts: offsets everywhere; padded strides when not grouped (the grouped layouts are fixed).
        int qOff() {
            return 3;
        }

        int ldq() {
            return grouped ? d : d + 2;
        }

        int kOff() {
            return 5;
        }

        int ldk() {
            return grouped ? kRow() : sharedKv ? d + dv : d + 1;
        }

        int vOff() {
            return sharedKv ? kOff() + d : 1;
        }

        int ldv() {
            return grouped ? vRow() : sharedKv ? d + dv : dv + 3;
        }

        int maskOff() {
            return 2;
        }

        int ldMask() {
            return maskMode == MaskMode.SHARED ? 0 : t + 3;
        }

        int pOff() {
            return 4;
        }

        int ldp() {
            return grouped ? t : t + 5;
        }

        int outOff() {
            return 6;
        }

        int ldOut() {
            return grouped ? dv : dv + 1;
        }

        int dOutOff() {
            return 2;
        }

        int ldDOut() {
            return grouped ? dv : dv + 2;
        }

        int dqOff() {
            return 1;
        }

        int ldDq() {
            return grouped ? d : d + 3;
        }

        int dkOff() {
            return 3;
        }

        int dvOff() {
            return 2;
        }

        int scratchOff() {
            return 5;
        }

        @Override
        public String toString() {
            return (grouped ? "grouped " + heads + "/" + kvHeads + " heads, " : "")
                    + (sharedKv ? "one k/v buffer, " : "") + queries + " queries, d " + d
                    + ", dv " + dv + ", t " + t + (causal ? " causal" : "") + ", mask " + maskMode;
        }
    }

    /** The inputs and output buffers of a problem, as CPU arrays. */
    static final class Data {
        final Problem pb;
        float[] q;
        float[] k;
        float[] v;
        float[] mask;
        float[] p;
        float[] out;
        float[] dOut;
        float[] dq;
        float[] dk;
        float[] dV;

        Data(Problem pb, long seed) {
            this.pb = pb;
            SplittableRandom r = new SplittableRandom(seed);
            q = random(r, pb.qOff() + (pb.rows() - 1) * pb.ldq() + pb.d() + 2);
            k = random(r, pb.kOff() + (pb.t() - 1) * pb.ldk() + pb.kRow() + pb.vRow() + 3);
            v = pb.sharedKv() ? k : random(r, pb.vOff() + (pb.t() - 1) * pb.ldv() + pb.vRow() + 1);
            if (pb.maskMode() != MaskMode.NONE) {
                mask = random(r, pb.maskOff() + (pb.queries() - 1) * pb.ldMask() + pb.t() + 2);
            }
            p = filled(pb.pOff() + (pb.rows() - 1) * pb.ldp() + pb.t() + 3);
            out = filled(pb.outOff() + (pb.rows() - 1) * pb.ldOut() + pb.dv() + 2);
            dOut = random(r, pb.dOutOff() + (pb.rows() - 1) * pb.ldDOut() + pb.dv() + 1);
            dq = filled(pb.dqOff() + (pb.rows() - 1) * pb.ldDq() + pb.d() + 2);
            dk = random(r, pb.dkOff() + (pb.t() - 1) * pb.ldk() + pb.kRow() + 2);
            dV = random(r, pb.dvOff() + (pb.t() - 1) * pb.ldv() + pb.vRow() + 2);
        }
    }

    static float[] random(SplittableRandom r, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = (float) (r.nextDouble() * 2 - 1);
        }
        return a;
    }

    static float[] filled(int n) {
        float[] a = new float[n];
        Arrays.fill(a, SENTINEL);
        return a;
    }

    /** The CPU forward pass: {@code p} and {@code out} of {@code x} become the results. */
    static void cpuForward(Data x) {
        Problem pb = x.pb;
        float[] scratch = new float[pb.t()];
        for (int r = 0; r < pb.queries(); r++) {
            int n = pb.keys(r);
            int maskOff = x.mask == null ? 0 : pb.maskOff() + r * pb.ldMask();
            if (!pb.grouped()) {
                Attention.forward(x.q, pb.qOff() + r * pb.ldq(), pb.d(), x.k, pb.kOff(), pb.ldk(), x.v, pb.vOff(),
                        pb.ldv(), pb.dv(), n, pb.scale(), x.mask, maskOff, x.p, pb.pOff() + r * pb.ldp(), x.out,
                        pb.outOff() + r * pb.ldOut(), scratch, 0);
            } else {
                float[] pr = new float[pb.heads() * n];
                Attention.groupedQuery(x.q, pb.qOff() + r * pb.heads() * pb.d(), pb.heads(), pb.d(), x.k, pb.kOff(),
                        x.v, pb.vOff(), pb.kvHeads(), pb.dv(), n, pb.scale(), x.mask, maskOff, pr, 0, x.out,
                        pb.outOff() + r * pb.heads() * pb.dv(), scratch, 0);
                for (int h = 0; h < pb.heads(); h++) {
                    System.arraycopy(pr, h * n, x.p, pb.pOff() + (r * pb.heads() + h) * pb.t(), n);
                }
            }
        }
    }

    /** The CPU backward pass from {@code x.p}: {@code dq}, {@code dk} and {@code dV} of {@code x} are updated. */
    static void cpuBackward(Data x) {
        Problem pb = x.pb;
        float[] scratch = new float[2 * pb.t()];
        for (int r = 0; r < pb.queries(); r++) {
            int n = pb.keys(r);
            if (!pb.grouped()) {
                Attention.backward(x.q, pb.qOff() + r * pb.ldq(), pb.d(), x.k, pb.kOff(), pb.ldk(), x.v, pb.vOff(),
                        pb.ldv(), pb.dv(), n, pb.scale(), x.p, pb.pOff() + r * pb.ldp(), x.dOut,
                        pb.dOutOff() + r * pb.ldDOut(), x.dq, pb.dqOff() + r * pb.ldDq(), x.dk, pb.dkOff(), x.dV,
                        pb.dvOff(), scratch, 0);
            } else {
                float[] pr = new float[pb.heads() * n];
                for (int h = 0; h < pb.heads(); h++) {
                    System.arraycopy(x.p, pb.pOff() + (r * pb.heads() + h) * pb.t(), pr, h * n, n);
                }
                Attention.groupedQueryBackward(x.q, pb.qOff() + r * pb.heads() * pb.d(), pb.heads(), pb.d(), x.k,
                        pb.kOff(), x.v, pb.vOff(), pb.kvHeads(), pb.dv(), n, pb.scale(), pr, 0, x.dOut,
                        pb.dOutOff() + r * pb.heads() * pb.dv(), x.dq, pb.dqOff() + r * pb.heads() * pb.d(), x.dk,
                        pb.dkOff(), x.dV, pb.dvOff(), scratch, 0);
            }
        }
    }

    /** Copies the arrays of {@code x}, for the CPU to compute the expected results in. */
    static Data copy(Data x) {
        Data c = new Data(x.pb, 0);
        c.q = x.q;
        c.k = x.k;
        c.v = x.v;
        c.mask = x.mask;
        c.p = x.p.clone();
        c.out = x.out.clone();
        c.dOut = x.dOut;
        c.dq = x.dq.clone();
        c.dk = x.dk.clone();
        c.dV = x.dV.clone();
        return c;
    }

    /**
     * Runs the forward pass of {@code in} on {@code backend} and on the CPU and compares {@code p} and {@code out};
     * then, from the CPU probabilities, the backward pass, comparing {@code dq}, {@code dk} and {@code dV}.
     */
    static void check(String backend, Data in) {
        Problem pb = in.pb;
        Data expected = copy(in);
        cpuForward(expected);
        Data back = copy(in);
        back.p = expected.p.clone();
        Data backExpected = copy(back);
        cpuBackward(backExpected);
        boolean hw = Devices.hardware(backend);
        try (GpuDevice device = Devices.open(backend); GpuAttention attention = new GpuAttention(device);
                var enc = device.encoder()) {
            GpuBuffer gq = Devices.upload(device, enc, in.q);
            GpuBuffer gk = Devices.upload(device, enc, in.k);
            GpuBuffer gv = pb.sharedKv() ? gk : Devices.upload(device, enc, in.v);
            GpuBuffer gmask = in.mask == null ? null : Devices.upload(device, enc, in.mask);
            GpuBuffer gp = Devices.upload(device, enc, in.p);
            GpuBuffer gout = Devices.upload(device, enc, in.out);
            // the backward pass reads the CPU's probabilities, so its check does not depend on the forward one
            GpuBuffer gpb = Devices.upload(device, enc, back.p);
            GpuBuffer gdOut = Devices.upload(device, enc, in.dOut);
            GpuBuffer gdq = Devices.upload(device, enc, in.dq);
            GpuBuffer gdk = Devices.upload(device, enc, in.dk);
            GpuBuffer gdV = Devices.upload(device, enc, in.dV);
            GpuBuffer scratch = Devices.buffer(device,
                    pb.scratchOff() + GpuAttention.backwardScratchSize(pb.queries(), pb.heads(), pb.t()) + 1);
            if (!pb.grouped()) {
                attention.forward(enc, pb.queries(), gq, pb.qOff(), pb.ldq(), pb.d(), gk, pb.kOff(), pb.ldk(), gv,
                        pb.vOff(), pb.ldv(), pb.dv(), pb.t(), pb.causal(), pb.scale(), gmask, pb.maskOff(),
                        pb.ldMask(), gp, pb.pOff(), pb.ldp(), gout, pb.outOff(), pb.ldOut());
                attention.backward(enc, pb.queries(), gq, pb.qOff(), pb.ldq(), pb.d(), gk, pb.kOff(), pb.ldk(), gv,
                        pb.vOff(), pb.ldv(), pb.dv(), pb.t(), pb.causal(), pb.scale(), gpb, pb.pOff(), pb.ldp(),
                        gdOut, pb.dOutOff(), pb.ldDOut(), gdq, pb.dqOff(), pb.ldDq(), gdk, pb.dkOff(), gdV,
                        pb.dvOff(), scratch, pb.scratchOff());
            } else {
                attention.groupedQuery(enc, pb.queries(), gq, pb.qOff(), pb.heads(), pb.d(), gk, pb.kOff(), gv,
                        pb.vOff(), pb.kvHeads(), pb.dv(), pb.t(), pb.causal(), pb.scale(), gmask, pb.maskOff(),
                        pb.ldMask(), gp, pb.pOff(), gout, pb.outOff());
                attention.groupedQueryBackward(enc, pb.queries(), gq, pb.qOff(), pb.heads(), pb.d(), gk, pb.kOff(),
                        gv, pb.vOff(), pb.kvHeads(), pb.dv(), pb.t(), pb.causal(), pb.scale(), gpb, pb.pOff(), gdOut,
                        pb.dOutOff(), gdq, pb.dqOff(), gdk, pb.dkOff(), gdV, pb.dvOff(), scratch, pb.scratchOff());
            }
            var rp = enc.download(gp);
            var rout = enc.download(gout);
            var rdq = enc.download(gdq);
            var rdk = enc.download(gdk);
            var rdV = enc.download(gdV);
            Devices.run(device, enc);
            String what = backend + " " + pb;
            Bits.assertSame(expected.p, Devices.floats(rp, in.p.length), hw, what + ": p");
            Bits.assertSame(expected.out, Devices.floats(rout, in.out.length), hw, what + ": out");
            Bits.assertSame(backExpected.dq, Devices.floats(rdq, in.dq.length), hw, what + ": dq");
            Bits.assertSame(backExpected.dk, Devices.floats(rdk, in.dk.length), hw, what + ": dk");
            Bits.assertSame(backExpected.dV, Devices.floats(rdV, in.dV.length), hw, what + ": dV");
        }
    }

    /**
     * {queries, d, dv, t, causal}: single elements, every side of the 256-element block boundary for d, dv and t,
     * deeper pairwise trees, more rows than the reference grid's 7 columns, causal prefill (queries == t) and causal
     * decoding of the last few positions.
     */
    private static final int[][] SHAPES = {
        {1, 1, 1, 1, 0}, {3, 7, 5, 9, 0}, {2, 255, 3, 256, 0}, {2, 256, 70, 255, 1}, {3, 257, 257, 257, 0},
        {5, 8, 9, 600, 1}, {1, 520, 1, 3, 0}, {3, 3, 260, 1100, 0}, {2, 16, 16, 2049, 1}, {40, 16, 12, 40, 1},
        {300, 4, 4, 300, 1}, {9, 64, 64, 129, 0},
    };

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (int[] s : SHAPES) {
                for (MaskMode mode : MaskMode.values()) {
                    out.add(Arguments.of(backend, new Problem(false, false, s[0], 1, 1, s[1], s[2], s[3], s[4] != 0, mode)));
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("cases")
    void matchesAttention(String backend, Problem pb) {
        check(backend, new Data(pb, pb.hashCode()));
    }

    /** {positions, heads, kvHeads, d, dv, t, causal}: multi-query, grouped and multi-head ratios. */
    private static final int[][] GROUPED = {
        {1, 8, 2, 16, 16, 33, 0}, {3, 4, 4, 8, 12, 300, 0}, {2, 6, 1, 64, 32, 257, 0}, {5, 6, 3, 260, 7, 5, 1},
        {16, 4, 2, 8, 8, 16, 1}, {3, 12, 4, 3, 5, 520, 1}, {1, 2, 1, 1, 1, 1, 0},
    };

    static Stream<Arguments> grouped() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (int[] s : GROUPED) {
                for (MaskMode mode : MaskMode.values()) {
                    out.add(Arguments.of(backend, new Problem(true, false, s[0], s[1], s[2], s[3], s[4], s[5], s[6] != 0,
                            mode)));
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("grouped")
    void matchesGroupedQuery(String backend, Problem pb) {
        check(backend, new Data(pb, pb.hashCode()));
    }

    static Stream<String> backends() {
        return Devices.namesWithFusedFma().stream();
    }

    /** The most keys a query can attend to, for one query: the softmax's whole group-memory budget. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void longestRow(String backend) {
        Problem pb = new Problem(false, false, 1, 1, 1, 4, 3, GpuAttention.MAX_KEYS, false, MaskMode.PER_QUERY);
        check(backend, new Data(pb, 11));
    }

    /** Keys and values interleaved in one buffer, rows of {@code [key | value]} (read-only bindings may share it). */
    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void keysAndValuesInOneBuffer(String backend) {
        Problem pb = new Problem(false, true, 6, 1, 1, 12, 20, 50, true, MaskMode.PER_QUERY);
        check(backend, new Data(pb, 5));
    }

    /** Sets row {@code r} of a mask (or other row-major array) of row stride {@code ld} from {@code off} to {@code v}. */
    private static void fillRow(float[] a, int off, int ld, int r, int n, float v) {
        Arrays.fill(a, off + r * ld, off + r * ld + n, v);
    }

    /**
     * The CPU rules for special values, forward: excluded keys ({@code -∞} in the mask), a wholly masked query (its
     * probabilities and output are NaN), a NaN in the mask, an infinite query element, keys far below the maximum,
     * signed zeros in the keys and values, and an infinite value. The backward pass then runs from those
     * probabilities, so the NaN rows reach every key and value gradient they touch.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void specialValuesForward(String backend) {
        Problem pb = new Problem(false, false, 6, 1, 1, 40, 24, 300, false, MaskMode.PER_QUERY);
        Data x = new Data(pb, 21);
        int t = pb.t();
        for (int j = 0; j < t; j++) {
            x.mask[pb.maskOff() + j] = j % 3 == 0 ? Float.NEGATIVE_INFINITY : 0f;
            x.mask[pb.maskOff() + 4 * pb.ldMask() + j] = j % 2 == 1 ? -50f : 0f;
        }
        fillRow(x.mask, pb.maskOff(), pb.ldMask(), 1, t, Float.NEGATIVE_INFINITY);
        x.mask[pb.maskOff() + 2 * pb.ldMask() + 17] = Float.NaN;
        x.mask[pb.maskOff() + 4 * pb.ldMask() + 123] = 30f;
        x.q[pb.qOff() + 3 * pb.ldq() + 5] = Float.POSITIVE_INFINITY;
        fillRow(x.k, pb.kOff(), pb.ldk(), 250, pb.d(), -0f);
        x.v[pb.vOff() + 100 * pb.ldv() + 3] = Float.POSITIVE_INFINITY;
        fillRow(x.v, pb.vOff(), pb.ldv(), 101, pb.dv(), -0f);
        check(backend, x);
    }

    /**
     * Special values in the backward pass, from finite probabilities: a query that attends to one key (a one-hot
     * row with exact zeros), excluded keys, a zero and a negative-zero output gradient, keys far below the maximum,
     * and output gradients of 1e30.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void specialValuesBackward(String backend) {
        Problem pb = new Problem(false, false, 6, 1, 1, 33, 17, 270, true, MaskMode.PER_QUERY);
        Data x = new Data(pb, 22);
        int keys1 = pb.keys(1);
        fillRow(x.mask, pb.maskOff(), pb.ldMask(), 1, keys1, Float.NEGATIVE_INFINITY);
        x.mask[pb.maskOff() + pb.ldMask() + 7] = 0f;
        for (int j = 0; j < pb.t(); j++) {
            if (j % 5 == 0) {
                x.mask[pb.maskOff() + 2 * pb.ldMask() + j] = Float.NEGATIVE_INFINITY;
            }
            if (j % 2 == 0) {
                x.mask[pb.maskOff() + 5 * pb.ldMask() + j] = -50f;
            }
        }
        fillRow(x.dOut, pb.dOutOff(), pb.ldDOut(), 3, pb.dv(), 0f);
        fillRow(x.dOut, pb.dOutOff(), pb.ldDOut(), 4, pb.dv(), -0f);
        fillRow(x.dOut, pb.dOutOff(), pb.ldDOut(), 0, pb.dv(), 1e30f);
        x.dOut[pb.dOutOff() + 5] = -1e30f;
        check(backend, x);
    }
}
