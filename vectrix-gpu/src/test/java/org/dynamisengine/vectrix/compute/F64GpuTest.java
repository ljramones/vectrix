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

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.Set;
import java.util.stream.Stream;
import org.dynamisengine.gpu.api.compute.Access;
import org.dynamisengine.gpu.api.compute.Backend;
import org.dynamisengine.gpu.api.compute.BindingLayout;
import org.dynamisengine.gpu.api.compute.Dimensions;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.KernelBundle;
import org.dynamisengine.gpu.api.compute.PipelineDescriptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * f64.slang, the binary64 arithmetic on 64-bit integers that the double-precision ports use, against Java's double
 * arithmetic, bit for bit (any NaN matches any NaN). Integer arithmetic is exact on every device, so this holds on
 * every backend, lavapipe included.
 */
class F64GpuTest {

    /** The operations in f64_probe.slang's switch, in order. */
    enum Op {
        FMA, MUL, ADD, SUB, DIV, FROM_FLOAT, TO_FLOAT, TO_LONG, FROM_LONG, LESS, MIN, SCALB;

        long apply(long a, long b, long c) {
            double x = Double.longBitsToDouble(a);
            double y = Double.longBitsToDouble(b);
            double z = Double.longBitsToDouble(c);
            return switch (this) {
                case FMA -> bits(Math.fma(x, y, z));
                case MUL -> bits(x * y);
                case ADD -> bits(x + y);
                case SUB -> bits(x - y);
                case DIV -> bits(x / y);
                case FROM_FLOAT -> bits((double) Float.intBitsToFloat((int) a));
                case TO_FLOAT -> Float.floatToRawIntBits((float) x) & 0xFFFF_FFFFL;
                case TO_LONG -> (long) x;
                case FROM_LONG -> bits((double) a);
                case LESS -> x < y ? 1 : 0;
                case MIN -> bits(Math.min(x, y));
                case SCALB -> bits(Math.scalb(x, (int) b));
            };
        }

        /** Whether a result is a double whose NaNs compare as NaNs. */
        boolean doubleResult() {
            return this != TO_FLOAT && this != TO_LONG && this != LESS;
        }
    }

    private static final int N = 1 << 18;

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (Op op : Op.values()) {
                out.add(Arguments.of(backend, op));
            }
        }
        return out.stream();
    }

    static KernelBundle probeKernel() {
        Map<Backend, KernelBundle.Executable> ex = new EnumMap<>(Backend.class);
        ex.put(Backend.VULKAN, new KernelBundle.Executable(KernelBundle.Format.SPIRV, resource("f64_probe.spv")));
        ex.put(Backend.METAL, new KernelBundle.Executable(KernelBundle.Format.MSL_SOURCE,
                resource("f64_probe.metal")));
        ex.put(Backend.MOCK, new KernelBundle.Executable(KernelBundle.Format.MOCK,
                "f64Probe".getBytes(StandardCharsets.UTF_8)));
        return new KernelBundle("vectrix.f64_probe", "test", ex);
    }

    private static byte[] resource(String name) {
        try (InputStream in = F64GpuTest.class.getResourceAsStream("testkernels/" + name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long bits(double d) {
        return Double.doubleToRawLongBits(d);
    }

    /** Values that stress the edges: zeros, subnormals, the normal boundary, near overflow, infinities, NaN. */
    private static final double[] SPECIAL = {0.0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE, 2 * Double.MIN_VALUE,
        Double.MIN_NORMAL, -Double.MIN_NORMAL, Math.nextDown(Double.MIN_NORMAL), Double.MAX_VALUE, -Double.MAX_VALUE,
        Math.nextDown(Double.MAX_VALUE), Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN, 1.0, -1.0,
        0.5, 2.0, 3.0, 1.0 / 3.0, Math.nextUp(1.0), Math.nextDown(1.0), 0x1p-1022, 0x1p-1074, 0x1p1023, 0x1.8p-1074,
        6755399441055744.0, 0x1p52, 0x1p53, 0x1p63, -0x1p63, 1e308, 1e-308, 1e-320};

    /** A random double: any bit pattern, a value near 1, a subnormal, a special value, or an integer. */
    private static long randomDouble(SplittableRandom r) {
        return switch (r.nextInt(6)) {
            case 0 -> r.nextLong();
            case 1 -> bits((r.nextDouble() * 2 - 1) * Math.scalb(1.0, r.nextInt(-60, 60)));
            case 2 -> r.nextLong() & 0x800F_FFFF_FFFF_FFFFL; // subnormal (or zero)
            case 3 -> bits(SPECIAL[r.nextInt(SPECIAL.length)]);
            case 4 -> bits((double) r.nextLong(-(1L << 53), 1L << 53));
            default -> bits(Math.scalb(r.nextDouble() + 1, r.nextInt(-1074, 1024)) * (r.nextBoolean() ? 1 : -1));
        };
    }

    /** Inputs for {@code op}, with targeted cases (cancellation, ties, overflow and underflow) mixed in. */
    static long[][] inputs(Op op, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        long[] a = new long[N];
        long[] b = new long[N];
        long[] c = new long[N];
        for (int i = 0; i < N; i++) {
            a[i] = randomDouble(r);
            b[i] = randomDouble(r);
            c[i] = randomDouble(r);
            double x = Double.longBitsToDouble(a[i]);
            double y = Double.longBitsToDouble(b[i]);
            switch (op) {
                case FMA -> {
                    if (i % 4 == 1) {
                        c[i] = bits(-(x * y));                       // exact cancellation down to the residual
                    } else if (i % 4 == 2) {
                        c[i] = bits(-(x * y) * (1 + 0x1p-40));       // near cancellation
                    }
                }
                case ADD, SUB -> {
                    if (i % 3 == 1) {
                        b[i] = bits((op == Op.ADD ? -1 : 1) * x * (1 + 0x1p-52 * r.nextInt(-8, 8)));
                    } else if (i % 3 == 2) {
                        b[i] = bits(Math.ulp(x) * (r.nextInt(5) - 2) / 2); // half-ulp ties
                    }
                }
                case MUL, DIV -> {
                    if (i % 3 == 1) {                                   // results near the subnormal boundary
                        b[i] = bits(Math.scalb(r.nextDouble() + 1, -1022 - Math.getExponent(x)
                                + (op == Op.DIV ? 2 * Math.getExponent(x) + 1022 : 0)));
                    }
                }
                case FROM_FLOAT -> a[i] = r.nextInt() & 0xFFFF_FFFFL;
                case TO_FLOAT -> {
                    if (i % 2 == 1) {                                   // the float range and its subnormals
                        a[i] = bits(Math.scalb(r.nextDouble() + 1, r.nextInt(-152, 130)) * (r.nextBoolean() ? 1 : -1));
                    }
                }
                case TO_LONG -> a[i] = bits((double) r.nextLong(-(1L << 62), 1L << 62) * (i % 2 == 0 ? 1 : 0x1p-20));
                case FROM_LONG -> a[i] = i % 2 == 0 ? r.nextLong(-(1L << 62), 1L << 62) : r.nextLong(-(1L << 54), 1L << 54);
                case SCALB -> {
                    b[i] = r.nextInt(-1022, 1024);
                    if (i % 2 == 1) {
                        a[i] = bits(Math.scalb(r.nextDouble() + 1, r.nextInt(-60, 60)));
                    }
                }
                default -> { }
            }
        }
        return new long[][] {a, b, c};
    }

    private static GpuBuffer upload(GpuDevice device, org.dynamisengine.gpu.api.compute.GpuCommandEncoder enc,
            long[] data) {
        GpuBuffer buf = Devices.buffer(device, 2L * data.length);
        ByteBuffer bytes = ByteBuffer.allocate(data.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        bytes.asLongBuffer().put(data);
        enc.upload(buf, bytes.array());
        return buf;
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("cases")
    void matchesJavaDoubleArithmetic(String backend, Op op) {
        long[][] in = inputs(op, 0x5EED_0000L + op.ordinal());
        long[] got = new long[N];
        try (GpuDevice device = Devices.open(backend)) {
            BindingLayout layout = new BindingLayout(List.of(
                    new BindingLayout.BufferBinding(0, Access.READ, 8), new BindingLayout.BufferBinding(1, Access.READ, 8),
                    new BindingLayout.BufferBinding(2, Access.READ, 8), new BindingLayout.BufferBinding(3, Access.WRITE, 8)),
                    12);
            try (var pipeline = device.pipeline(new PipelineDescriptor(probeKernel(), "f64Probe", layout,
                    new Dimensions(64, 1, 1), Map.of(), Set.of("INT64", "INT32"), Set.of()));
                    var enc = device.encoder();
                    var a = upload(device, enc, in[0]);
                    var b = upload(device, enc, in[1]);
                    var c = upload(device, enc, in[2]);
                    var out = Devices.buffer(device, 2L * N)) {
                var grid = Kernels.grid(device.capabilities(), (N + 63) / 64);
                byte[] params = new Kernels.Params(3).u32(op.ordinal()).u32(N).u32(grid.x()).bytes();
                enc.dispatch(pipeline, Map.of(0, a, 1, b, 2, c, 3, out), params, grid);
                var read = enc.download(out);
                Devices.run(device, enc);
                ByteBuffer.wrap(read.bytes()).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(got);
            }
        }
        int bad = 0;
        StringBuilder first = new StringBuilder();
        for (int i = 0; i < N; i++) {
            long expected = op.apply(in[0][i], in[1][i], in[2][i]);
            boolean same = expected == got[i] || (op.doubleResult()
                    && Double.isNaN(Double.longBitsToDouble(expected)) && Double.isNaN(Double.longBitsToDouble(got[i])))
                    || (op == Op.TO_FLOAT && Float.isNaN(Float.intBitsToFloat((int) expected))
                    && Float.isNaN(Float.intBitsToFloat((int) got[i])));
            if (!same && bad++ < 5) {
                first.append(String.format("%n  %s(%s, %s, %s) [0x%016x 0x%016x 0x%016x]: expected 0x%016x, got 0x%016x",
                        op, Double.longBitsToDouble(in[0][i]), Double.longBitsToDouble(in[1][i]),
                        Double.longBitsToDouble(in[2][i]), in[0][i], in[1][i], in[2][i], expected, got[i]));
            }
        }
        if (bad > 0) {
            fail(backend + " " + op + ": " + bad + " of " + N + " differ" + first);
        }
    }
}
