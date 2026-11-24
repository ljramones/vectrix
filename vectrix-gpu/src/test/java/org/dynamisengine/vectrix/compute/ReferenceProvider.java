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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dynamisengine.gpu.api.compute.Access;
import org.dynamisengine.gpu.api.compute.Backend;
import org.dynamisengine.gpu.api.compute.BufferDescriptor;
import org.dynamisengine.gpu.api.compute.ComputeException;
import org.dynamisengine.gpu.api.compute.Dimensions;
import org.dynamisengine.gpu.api.compute.GpuAdapter;
import org.dynamisengine.gpu.api.compute.GpuCapabilities;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.GpuProvider;
import org.dynamisengine.gpu.api.compute.GpuSubmission;
import org.dynamisengine.gpu.api.compute.PipelineDescriptor;
import org.dynamisengine.gpu.api.compute.spi.ComputeDevice;
import org.dynamisengine.gpu.api.compute.spi.ComputeDriver;
import org.dynamisengine.vectrix.blas.Gemmf;
import org.dynamisengine.vectrix.hash.Philox;
import org.dynamisengine.vectrix.nn.Softmax;

/**
 * A DynamisGPU backend that runs each vectrix-gpu kernel as its Vectrix CPU method, so the Java wrappers can be
 * tested without a GPU. It decodes every parameter at the offset Slang's reflection gives for it (not the offset the
 * wrapper meant), and fails a dispatch whose groups do not cover the work, so it checks the wrappers' half of the
 * ABI: field order, offsets, strides, transposes, group counts and validation.
 */
final class ReferenceProvider implements GpuProvider {

    /** Small group-count limits, so the two-dimensional grids are exercised. */
    static final int MAX_GROUPS_X = 7;

    @Override
    public Backend backend() {
        return Backend.MOCK;
    }

    @Override
    public List<GpuAdapter> adapters() {
        return List.of(new GpuAdapter() {
            @Override
            public Backend backend() {
                return Backend.MOCK;
            }

            @Override
            public String id() {
                return "vectrix-reference:0";
            }

            @Override
            public String name() {
                return "Vectrix CPU reference";
            }

            @Override
            public GpuCapabilities capabilities() {
                return new GpuCapabilities(1L << 30, 1L << 30, 16, 8, 128, new Dimensions(1024, 1024, 64), 1024,
                        new Dimensions(MAX_GROUPS_X, 65535, 65535), Set.of("FP32", "INT32", "BOOL"), Set.of(), false);
            }

            @Override
            public GpuDevice open() {
                return new ComputeDevice(this, new Driver());
            }
        });
    }

    private static final class Driver implements ComputeDriver {

        @Override
        public Object allocate(BufferDescriptor d) {
            return ByteBuffer.allocate(Math.toIntExact(d.bytes())).order(ByteOrder.LITTLE_ENDIAN);
        }

        @Override
        public void releaseBuffer(Object b) {
        }

        private static ByteBuffer region(Region r) {
            return ((ByteBuffer) r.allocation()).slice(Math.toIntExact(r.offset()), Math.toIntExact(r.bytes()))
                    .order(ByteOrder.LITTLE_ENDIAN);
        }

        @Override
        public ByteBuffer map(Object b, long offset, long bytes, Access a) {
            return region(new Region(b, offset, bytes));
        }

        @Override
        public void unmap(Object b, Access a) {
        }

        @Override
        public Object pipeline(PipelineDescriptor d) {
            return d;
        }

        @Override
        public void releasePipeline(Object p) {
        }

        @Override
        public void close() {
        }

        @Override
        public Job submit(List<Command> commands) {
            return new Job() {
                private boolean done;

                @Override
                public GpuSubmission.Status poll() {
                    if (!done) {
                        done = true;
                        for (Command c : commands) {
                            execute(c);
                        }
                    }
                    return GpuSubmission.Status.COMPLETE;
                }

                @Override
                public void close() {
                }
            };
        }

        private static void execute(Command command) {
            switch (command) {
                case Upload u -> region(u.destination()).put(u.data());
                case Download d -> {
                    byte[] out = new byte[Math.toIntExact(d.source().bytes())];
                    region(d.source()).get(out);
                    d.result().complete(out);
                }
                case Copy c -> region(c.destination()).put(region(c.source()));
                case Fill f -> {
                    ByteBuffer b = region(f.destination());
                    while (b.hasRemaining()) {
                        b.putInt(f.pattern());
                    }
                }
                case Dispatch d -> dispatch(d);
            }
        }

        private static void dispatch(Dispatch d) {
            String name = d.descriptor().kernel().name().substring("vectrix.".length());
            Map<String, Integer> offsets = Manifest.params(name);
            ByteBuffer pb = ByteBuffer.wrap(d.parameters()).order(ByteOrder.LITTLE_ENDIAN);
            Map<String, Long> u = new HashMap<>();
            Map<String, Float> f = new HashMap<>();
            Manifest.paramTypes(name).forEach((field, type) -> {
                int at = offsets.get(field);
                if (type.equals("float32")) {
                    f.put(field, pb.getFloat(at));
                } else {
                    u.put(field, pb.getInt(at) & 0xFFFF_FFFFL);
                }
            });
            Map<Integer, ByteBuffer> slots = new HashMap<>();
            for (Bound b : d.bindings()) {
                slots.put(b.slot(), region(b.region()));
            }
            Dimensions g = d.groups();
            switch (name) {
                case "gemm" -> gemm(u, f, slots, g);
                case "philox" -> philox(u, slots, g);
                case "softmax" -> softmax(u, slots.get(0), slots.get(1), u.get("yOff"), u.get("ldy"), g);
                case "softmax_inplace" -> softmax(u, slots.get(0), slots.get(0), u.get("xOff"), u.get("ldx"), g);
                default -> throw new ComputeException(ComputeException.Code.UNSUPPORTED, "no reference for " + name);
            }
        }

        private static void require(boolean ok, String what) {
            if (!ok) {
                throw new ComputeException(ComputeException.Code.INVALID_ARGUMENT, "reference: " + what);
            }
        }

        private static float[] floats(ByteBuffer b) {
            float[] a = new float[b.capacity() / 4];
            b.asFloatBuffer().get(a);
            return a;
        }

        private static void put(ByteBuffer b, float[] a) {
            b.asFloatBuffer().put(a);
        }

        private static int i(Map<String, Long> u, String k) {
            return Math.toIntExact(u.get(k));
        }

        private static void gemm(Map<String, Long> u, Map<String, Float> f, Map<Integer, ByteBuffer> s, Dimensions g) {
            boolean ta = u.get("transA") != 0;
            boolean tb = u.get("transB") != 0;
            int m = i(u, "m");
            int n = i(u, "n");
            int k = i(u, "k");
            require((long) g.x() * 16 >= n && (long) g.y() * 16 >= m, "gemm groups " + g + " do not cover " + m + "x"
                    + n);
            float[] a = floats(s.get(0));
            float[] b = s.get(1) == s.get(0) ? a : floats(s.get(1));
            float[] c = floats(s.get(2));
            float[] scratch = new float[Gemmf.scratchSize(ta, tb, m, n, k)];
            Gemmf.gemm(ta, tb, m, n, k, f.get("alpha"), a, i(u, "aOff"), i(u, "lda"), b, i(u, "bOff"), i(u, "ldb"),
                    f.get("beta"), c, i(u, "cOff"), i(u, "ldc"), scratch, 0);
            put(s.get(2), c);
        }

        private static void philox(Map<String, Long> u, Map<Integer, ByteBuffer> s, Dimensions g) {
            long seed = u.get("seedLo") | u.get("seedHi") << 32;
            long start = u.get("startLo") | u.get("startHi") << 32;
            int off = i(u, "off");
            int n = i(u, "n");
            long counters = ((start & 3) + n + 3) / 4;
            require(u.get("gridX") == g.x() && (long) g.x() * g.y() * 64 >= counters, "philox groups " + g
                    + " do not cover " + counters + " counters");
            ByteBuffer dest = s.get(0);
            for (int e = 0; e < n; e++) {
                int v = u.get("uniform") != 0 ? Float.floatToRawIntBits(Philox.uniform(seed, start + e))
                        : Philox.word(seed, start + e);
                dest.putInt((off + e) * 4, v);
            }
        }

        private static void softmax(Map<String, Long> u, ByteBuffer xb, ByteBuffer yb, long yOff, long ldy,
                Dimensions g) {
            int rows = i(u, "rows");
            require(u.get("gridX") == g.x() && (long) g.x() * g.y() >= rows, "softmax groups " + g
                    + " do not cover " + rows + " rows");
            float[] x = floats(xb);
            float[] y = xb == yb ? x : floats(yb);
            for (int r = 0; r < rows; r++) {
                Softmax.softmax(x, i(u, "xOff") + r * i(u, "ldx"), y, Math.toIntExact(yOff + r * ldy), i(u, "n"));
            }
            put(yb, y);
        }
    }
}
