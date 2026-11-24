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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.function.Consumer;

import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.junit.jupiter.api.Test;

/**
 * {@link GpuAttention} rejects invalid arguments before recording anything, with the CPU method's exception type:
 * each rejected call leaves every output buffer as it was.
 */
class GpuAttentionValidationTest {

    /** Buffers of ones for a small problem: 2 queries, d = dv = 4, t = 3, dense strides. */
    private static final class Bufs {
        final GpuBuffer q;
        final GpuBuffer k;
        final GpuBuffer v;
        final GpuBuffer mask;
        final GpuBuffer p;
        final GpuBuffer out;
        final GpuBuffer dOut;
        final GpuBuffer dq;
        final GpuBuffer dk;
        final GpuBuffer dV;
        final GpuBuffer scratch;

        Bufs(GpuDevice d, GpuCommandEncoder enc) {
            q = Devices.upload(d, enc, ones(8));
            k = Devices.upload(d, enc, ones(12));
            v = Devices.upload(d, enc, ones(12));
            mask = Devices.upload(d, enc, ones(6));
            p = Devices.upload(d, enc, ones(6));
            out = Devices.upload(d, enc, ones(8));
            dOut = Devices.upload(d, enc, ones(8));
            dq = Devices.upload(d, enc, ones(8));
            dk = Devices.upload(d, enc, ones(12));
            dV = Devices.upload(d, enc, ones(12));
            scratch = Devices.upload(d, enc, ones(6));
        }

        GpuBuffer[] written() {
            return new GpuBuffer[] {p, out, dq, dk, dV, scratch};
        }
    }

    private static float[] ones(int n) {
        float[] a = new float[n];
        Arrays.fill(a, 1f);
        return a;
    }

    /** A call on fresh buffers that must throw IllegalArgumentException and leave every written buffer unchanged. */
    private static void rejected(GpuDevice d, GpuAttention a, Call body) {
        var enc = d.encoder();
        Bufs b = new Bufs(d, enc);
        assertThrows(IllegalArgumentException.class, () -> body.run(a, enc, b));
        GpuBuffer[] targets = b.written();
        var reads = new org.dynamisengine.gpu.api.compute.GpuReadback[targets.length];
        for (int i = 0; i < targets.length; i++) {
            reads[i] = enc.download(targets[i]);
        }
        Devices.run(d, enc);
        for (int i = 0; i < targets.length; i++) {
            int n = (int) (targets[i].bytes() / 4);
            assertArrayEquals(ones(n), Devices.floats(reads[i], n));
        }
    }

    /** A recording of one call. */
    interface Call {
        void run(GpuAttention a, GpuCommandEncoder enc, Bufs b);
    }

    /** The valid forward call, with one argument changed by {@code edit}. */
    private static final class Fwd {
        int queries = 2;
        GpuBuffer q;
        long qOff;
        int ldq = 4;
        int d = 4;
        GpuBuffer k;
        long kOff;
        int ldk = 4;
        GpuBuffer v;
        long vOff;
        int ldv = 4;
        int dv = 4;
        int t = 3;
        boolean causal;
        GpuBuffer mask;
        long maskOff;
        int ldMask = 3;
        GpuBuffer p;
        long pOff;
        int ldp = 3;
        GpuBuffer out;
        long outOff;
        int ldOut = 4;

        static Call with(Consumer<Fwd> edit) {
            return (a, enc, b) -> {
                Fwd f = new Fwd();
                f.q = b.q;
                f.k = b.k;
                f.v = b.v;
                f.mask = b.mask;
                f.p = b.p;
                f.out = b.out;
                edit.accept(f);
                a.forward(enc, f.queries, f.q, f.qOff, f.ldq, f.d, f.k, f.kOff, f.ldk, f.v, f.vOff, f.ldv, f.dv, f.t,
                        f.causal, 0.5f, f.mask, f.maskOff, f.ldMask, f.p, f.pOff, f.ldp, f.out, f.outOff, f.ldOut);
            };
        }
    }

    /** The valid backward call, with one argument changed by {@code edit}. */
    private static final class Bwd {
        int queries = 2;
        GpuBuffer q;
        long qOff;
        int ldq = 4;
        int d = 4;
        GpuBuffer k;
        long kOff;
        int ldk = 4;
        GpuBuffer v;
        long vOff;
        int ldv = 4;
        int dv = 4;
        int t = 3;
        boolean causal;
        GpuBuffer p;
        long pOff;
        int ldp = 3;
        GpuBuffer dOut;
        long dOutOff;
        int ldDOut = 4;
        GpuBuffer dq;
        long dqOff;
        int ldDq = 4;
        GpuBuffer dk;
        long dkOff;
        GpuBuffer dV;
        long dvOff;
        GpuBuffer scratch;
        long scratchOff;

        static Call with(Consumer<Bwd> edit) {
            return (a, enc, b) -> {
                Bwd f = new Bwd();
                f.q = b.q;
                f.k = b.k;
                f.v = b.v;
                f.p = b.p;
                f.dOut = b.dOut;
                f.dq = b.dq;
                f.dk = b.dk;
                f.dV = b.dV;
                f.scratch = b.scratch;
                edit.accept(f);
                a.backward(enc, f.queries, f.q, f.qOff, f.ldq, f.d, f.k, f.kOff, f.ldk, f.v, f.vOff, f.ldv, f.dv, f.t,
                        f.causal, 0.5f, f.p, f.pOff, f.ldp, f.dOut, f.dOutOff, f.ldDOut, f.dq, f.dqOff, f.ldDq, f.dk,
                        f.dkOff, f.dV, f.dvOff, f.scratch, f.scratchOff);
            };
        }
    }

    @Test
    void validCallsAreAccepted() {
        try (GpuDevice d = Devices.open("reference"); GpuAttention a = new GpuAttention(d)) {
            var enc = d.encoder();
            Bufs b = new Bufs(d, enc);
            Fwd.with(f -> { }).run(a, enc, b);
            Fwd.with(f -> f.causal = true).run(a, enc, b);
            Fwd.with(f -> f.ldMask = 0).run(a, enc, b);
            Fwd.with(f -> f.mask = null).run(a, enc, b);
            Bwd.with(f -> { }).run(a, enc, b);
            Devices.run(d, enc);
        }
    }

    @Test
    void forwardChecksSizes() {
        try (GpuDevice d = Devices.open("reference"); GpuAttention a = new GpuAttention(d)) {
            rejected(d, a, Fwd.with(f -> f.t = 0));
            rejected(d, a, Fwd.with(f -> f.d = 0));
            rejected(d, a, Fwd.with(f -> f.dv = 0));
            rejected(d, a, Fwd.with(f -> f.queries = -1));
            rejected(d, a, Fwd.with(f -> f.t = GpuAttention.MAX_KEYS + 1));
            rejected(d, a, Fwd.with(f -> {
                f.causal = true;
                f.queries = 4; // more queries than keys
                f.t = 1;
                f.ldp = 1;
            }));
        }
    }

    @Test
    void forwardChecksLayouts() {
        try (GpuDevice d = Devices.open("reference"); GpuAttention a = new GpuAttention(d)) {
            rejected(d, a, Fwd.with(f -> f.ldq = 3)); // stride below d
            rejected(d, a, Fwd.with(f -> f.qOff = 1)); // 2 queries of 4 do not fit 8 floats from 1
            rejected(d, a, Fwd.with(f -> f.ldk = 3));
            rejected(d, a, Fwd.with(f -> f.kOff = 1));
            rejected(d, a, Fwd.with(f -> f.vOff = -1));
            rejected(d, a, Fwd.with(f -> f.ldv = 5));
            rejected(d, a, Fwd.with(f -> f.maskOff = 1)); // two mask rows of 3 need 6 floats
            rejected(d, a, Fwd.with(f -> f.ldMask = -1));
            rejected(d, a, Fwd.with(f -> f.ldMask = 4));
            rejected(d, a, Fwd.with(f -> f.ldp = 2)); // stride below t
            rejected(d, a, Fwd.with(f -> f.pOff = 1));
            rejected(d, a, Fwd.with(f -> f.ldOut = 5));
            rejected(d, a, Fwd.with(f -> f.q = null));
            rejected(d, a, Fwd.with(f -> f.p = null));
            rejected(d, a, (at, enc, b) -> at.forward(null, 2, b.q, 0, 4, 4, b.k, 0, 4, b.v, 0, 4, 4, 3, false, 1f,
                    null, 0, 0, b.p, 0, 3, b.out, 0, 4));
        }
    }

    @Test
    void forwardRejectsWrittenBuffersThatAreInputs() {
        try (GpuDevice d = Devices.open("reference"); GpuAttention a = new GpuAttention(d)) {
            rejected(d, a, Fwd.with(f -> f.out = f.p));
            rejected(d, a, Fwd.with(f -> {
                f.p = f.k; // p in the key buffer, after the keys
                f.pOff = 0;
            }));
            rejected(d, a, Fwd.with(f -> f.out = f.v));
            rejected(d, a, Fwd.with(f -> f.out = f.q));
            rejected(d, a, Fwd.with(f -> f.p = f.mask));
        }
    }

    @Test
    void backwardChecksSizesLayoutsAndBuffers() {
        try (GpuDevice d = Devices.open("reference"); GpuAttention a = new GpuAttention(d)) {
            rejected(d, a, Bwd.with(f -> f.t = 0));
            rejected(d, a, Bwd.with(f -> f.queries = -1));
            rejected(d, a, Bwd.with(f -> f.scratchOff = 1)); // 2·3 floats needed
            rejected(d, a, Bwd.with(f -> f.scratch = null));
            rejected(d, a, Bwd.with(f -> f.ldp = 2));
            rejected(d, a, Bwd.with(f -> f.dOutOff = 1));
            rejected(d, a, Bwd.with(f -> f.ldDq = 3));
            rejected(d, a, Bwd.with(f -> f.dkOff = 1));
            rejected(d, a, Bwd.with(f -> f.dvOff = 1));
            rejected(d, a, Bwd.with(f -> f.dk = f.dq));
            rejected(d, a, Bwd.with(f -> f.dV = f.dk));
            rejected(d, a, Bwd.with(f -> f.dk = f.k));
            rejected(d, a, Bwd.with(f -> f.dV = f.v));
            rejected(d, a, Bwd.with(f -> f.dq = f.q));
            rejected(d, a, Bwd.with(f -> f.scratch = f.p));
            rejected(d, a, Bwd.with(f -> f.scratch = f.dV));
            rejected(d, a, Bwd.with(f -> f.dq = f.dOut));
        }
    }

    @Test
    void groupedChecksHeadsAndLayouts() {
        try (GpuDevice d = Devices.open("reference"); GpuAttention a = new GpuAttention(d)) {
            // 1 position, 2 heads of d = dv = 2 over 1 key/value head, t = 3: q 4, caches 3x2, p 6, out 4
            Call ok = (at, enc, b) -> at.groupedQuery(enc, 1, b.q, 0, 2, 2, b.k, 0, b.v, 0, 1, 2, 3, false, 1f, null,
                    0, 0, b.p, 0, b.out, 0);
            var enc = d.encoder();
            ok.run(a, enc, new Bufs(d, enc));
            Devices.run(d, enc);
            rejected(d, a, (at, e, b) -> at.groupedQuery(e, 1, b.q, 0, 3, 2, b.k, 0, b.v, 0, 2, 2, 3, false, 1f, null,
                    0, 0, b.p, 0, b.out, 0)); // 3 heads over 2
            rejected(d, a, (at, e, b) -> at.groupedQuery(e, 1, b.q, 0, 2, 2, b.k, 0, b.v, 0, 0, 2, 3, false, 1f, null,
                    0, 0, b.p, 0, b.out, 0)); // no key/value heads
            rejected(d, a, (at, e, b) -> at.groupedQuery(e, 2, b.q, 1, 2, 2, b.k, 0, b.v, 0, 1, 2, 3, false, 1f, null,
                    0, 0, b.p, 0, b.out, 0)); // 2 positions of 4 queries from 1
            rejected(d, a, (at, e, b) -> at.groupedQuery(e, 1, b.q, 0, 2, 2, b.k, 0, b.v, 0, 2, 4, 3, false, 1f, null,
                    0, 0, b.p, 0, b.out, 0)); // value cache rows of 2x4 do not fit
            rejected(d, a, (at, e, b) -> at.groupedQuery(e, 2, b.q, 0, 2, 2, b.k, 0, b.v, 0, 1, 2, 3, false, 1f, null,
                    0, 0, b.p, 0, b.out, 0)); // p needs 2x2x3
            rejected(d, a, (at, e, b) -> at.groupedQuery(e, 1, b.q, 0, 2, 2, b.k, 0, b.v, 0, 1, 2, 3, false, 1f, b.p,
                    0, 0, b.p, 0, b.out, 0)); // p is the mask
            rejected(d, a, (at, e, b) -> at.groupedQueryBackward(e, 1, b.q, 0, 2, 2, b.k, 0, b.v, 0, 1, 2, 3, false,
                    1f, b.p, 0, b.dOut, 0, b.dq, 0, b.dk, 0, b.dV, 0, b.scratch, 1)); // scratch needs 2x3 from 1
            rejected(d, a, (at, e, b) -> at.groupedQueryBackward(e, 1, b.q, 0, 2, 2, b.k, 0, b.v, 0, 1, 2, 3, false,
                    1f, b.p, 0, b.dOut, 0, b.dq, 0, b.dk, 0, b.dk, 0, b.scratch, 0)); // dV is dk
            rejected(d, a, (at, e, b) -> at.groupedQueryBackward(e, 1, b.q, 0, 2, 2, b.k, 0, b.v, 0, 1, 2, 3, false,
                    1f, b.p, 0, b.dOut, 0, b.dq, 0, b.dk, 10, b.dV, 0, b.scratch, 0)); // dk rows do not fit
        }
    }

    @Test
    void emptyWorkRecordsNothingAndScratchSizes() {
        try (GpuDevice d = Devices.open("reference"); GpuAttention a = new GpuAttention(d)) {
            var enc = d.encoder();
            Bufs b = new Bufs(d, enc);
            Fwd.with(f -> f.queries = 0).run(a, enc, b);
            Bwd.with(f -> f.queries = 0).run(a, enc, b);
            a.groupedQuery(enc, 0, b.q, 0, 2, 2, b.k, 0, b.v, 0, 1, 2, 3, true, 1f, b.mask, 0, 3, b.p, 0, b.out, 0);
            var reads = new org.dynamisengine.gpu.api.compute.GpuReadback[b.written().length];
            for (int i = 0; i < reads.length; i++) {
                reads[i] = enc.download(b.written()[i]);
            }
            Devices.run(d, enc);
            for (int i = 0; i < reads.length; i++) {
                int n = (int) (b.written()[i].bytes() / 4);
                assertArrayEquals(ones(n), Devices.floats(reads[i], n));
            }
        }
        assertEquals(24L, GpuAttention.backwardScratchSize(2, 4, 3));
        assertThrows(IllegalArgumentException.class, () -> GpuAttention.backwardScratchSize(-1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> GpuAttention.backwardScratchSize(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> GpuAttention.backwardScratchSize(1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new GpuAttention(null));
    }
}
