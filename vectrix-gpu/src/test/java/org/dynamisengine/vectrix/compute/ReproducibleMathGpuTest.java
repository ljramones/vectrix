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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.dynamisengine.gpu.api.compute.Access;
import org.dynamisengine.gpu.api.compute.Backend;
import org.dynamisengine.gpu.api.compute.BindingLayout;
import org.dynamisengine.gpu.api.compute.Dimensions;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.KernelBundle;
import org.dynamisengine.gpu.api.compute.PipelineDescriptor;
import org.dynamisengine.vectrix.core.ReproducibleMath;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The Slang ports of the {@code ReproducibleMath} float functions (common.slang) against the Java, through the
 * test-only math_probe kernel. Every run sweeps about a million bit patterns spread over all 2^32;
 * {@code -Dvectrix.gpu.exhaustive=true} checks every float. On hardware that flushes subnormals to zero, a
 * subnormal input may be read as a zero of the same sign, and a result below {@code 2^-124} may be zero (it is
 * subnormal, or came from a subnormal intermediate); everything else must match bit for bit.
 */
class ReproducibleMathGpuTest {

    /** The functions in math_probe.slang's switch, in order. */
    enum Fn {
        EXP(ReproducibleMath::exp), EXPM1(ReproducibleMath::expm1), TANH(ReproducibleMath::tanh),
        SIGMOID(ReproducibleMath::sigmoid), LOG(ReproducibleMath::log), LOG1P(ReproducibleMath::log1p),
        /** The GPU's own sqrt against Java's correctly rounded one. */
        SQRT(x -> (float) Math.sqrt(x)), ABS(Math::abs),
        /** fma(x, x, -x·x): the rounding error of x·x when fma is fused (math_probe case 8). */
        FMA_RESIDUAL(x -> Math.fma(x, x, -(x * x)));

        /** Whether the Slang port uses fma, so only a fused fma can match Java. */
        boolean needsFusedFma() {
            return this != SQRT && this != ABS;
        }

        final FloatOp op;

        Fn(FloatOp op) {
            this.op = op;
        }
    }

    interface FloatOp {
        float apply(float x);
    }

    private static final int CHUNK = 1 << 24;

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (Fn fn : Fn.values()) {
                if (!fn.needsFusedFma() || Devices.fusesFma(backend)) {
                    out.add(Arguments.of(backend, fn));
                }
            }
        }
        return out.stream();
    }

    static KernelBundle probeKernel() {
        Map<Backend, KernelBundle.Executable> ex = new EnumMap<>(Backend.class);
        ex.put(Backend.VULKAN, new KernelBundle.Executable(KernelBundle.Format.SPIRV, resource("math_probe.spv")));
        ex.put(Backend.METAL, new KernelBundle.Executable(KernelBundle.Format.MSL_SOURCE,
                resource("math_probe.metal")));
        ex.put(Backend.MOCK, new KernelBundle.Executable(KernelBundle.Format.MOCK,
                "probe".getBytes(StandardCharsets.UTF_8)));
        return new KernelBundle("vectrix.math_probe", "test", ex);
    }

    private static byte[] resource(String name) {
        try (InputStream in = ReproducibleMathGpuTest.class.getResourceAsStream("testkernels/" + name)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Whether {@code actual} is an acceptable GPU result for input bits {@code bits}. */
    static boolean accept(Fn fn, int bits, int actual, boolean hardware) {
        float x = Float.intBitsToFloat(bits);
        float expected = fn.op.apply(x);
        if (Bits.same(expected, Float.intBitsToFloat(actual), hardware)) {
            return true;
        }
        if (!hardware) {
            return false;
        }
        if (Bits.subnormal(x) && Bits.same(fn.op.apply(Math.copySign(0f, x)), Float.intBitsToFloat(actual), true)) {
            return true;
        }
        // an intermediate below 2^-126 flushed to zero (log1p's s = f/(2 + f) for |f| near 2^-126)
        return Float.intBitsToFloat(actual) == 0 && Math.abs(expected) < 0x1p-124f;
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("cases")
    void matchesReproducibleMath(String backend, Fn fn) {
        boolean all = Boolean.getBoolean("vectrix.gpu.exhaustive");
        long stride = all ? 1 : 4099; // a prime: ~1M patterns spread over every exponent and sign
        long total = all ? 1L << 32 : ((1L << 32) + stride - 1) / stride;
        boolean hardware = Devices.hardware(backend);
        AtomicLong bad = new AtomicLong();
        StringBuilder first = new StringBuilder();
        try (GpuDevice device = Devices.open(backend)) {
            BindingLayout layout = new BindingLayout(List.of(new BindingLayout.BufferBinding(0, Access.WRITE, 4)),
                    20);
            try (var pipeline = device.pipeline(new PipelineDescriptor(probeKernel(), "probe", layout,
                    new Dimensions(64, 1, 1), Map.of(), Set.of("FP32", "INT32"), Set.of()));
                    var out = Devices.buffer(device, CHUNK)) {
                for (long done = 0; done < total; done += CHUNK) {
                    int count = (int) Math.min(CHUNK, total - done);
                    long base = done * stride;
                    var grid = Kernels.grid(device.capabilities(), (count + 63) / 64);
                    byte[] params = new Kernels.Params(5).u32(fn.ordinal()).u32(base).u32(stride).u32(count)
                            .u32(grid.x()).bytes();
                    try (var enc = device.encoder()) {
                        enc.dispatch(pipeline, Map.of(0, out), params, grid);
                        var read = enc.download(out);
                        Devices.run(device, enc);
                        int[] got = Devices.ints(read, count);
                        IntStream.range(0, count).parallel().forEach(i -> {
                            int bits = (int) (base + (long) i * stride);
                            if (!accept(fn, bits, got[i], hardware) && bad.getAndIncrement() < 5) {
                                synchronized (first) {
                                    first.append(String.format("%n  x=%s (0x%08x): expected %s, got %s",
                                            Float.intBitsToFloat(bits), bits, fn.op.apply(Float.intBitsToFloat(bits)),
                                            Float.intBitsToFloat(got[i])));
                                }
                            }
                        });
                    }
                }
            }
        }
        if (bad.get() > 0) {
            fail(backend + " " + fn + ": " + bad.get() + " of " + total + " inputs differ" + first);
        }
    }
}
