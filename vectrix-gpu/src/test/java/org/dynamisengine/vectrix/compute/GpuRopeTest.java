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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.vectrix.nn.Rope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link GpuRope} against {@code Rope.apply} and {@code applyBackward}, bit for bit. The kernel computes in double
 * with f64.slang's integer arithmetic and only converts floats, so it matches on every backend, lavapipe included.
 */
class GpuRopeTest {

    /** Positions from 0 to beyond 2^53 (where long to double rounds), and the ends of the long range. */
    private static final long[] POSITIONS = {0, 1, 2, 7, 1000, 4095, 131_071, 1L << 31, (1L << 40) + 3,
        (1L << 53) + 1, (1L << 53) + 3, Long.MAX_VALUE, -5};

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (Rope.Layout layout : Rope.Layout.values()) {
                for (boolean backward : new boolean[] {false, true}) {
                    out.add(Arguments.of(backend, layout, backward));
                }
            }
        }
        return out.stream();
    }

    private static Rope[] plans(Rope.Layout layout) {
        return new Rope[] {Rope.plan(2, 10_000, layout), Rope.plan(64, 10_000, layout),
            Rope.plan(128, 500_000, layout), Rope.plan(new double[] {1.0, 0.5, 1e-6, 3.7, 1e3}, layout)};
    }

    @ParameterizedTest(name = "{0} {1} backward={2}")
    @MethodSource("cases")
    void matchesRope(String backend, Rope.Layout layout, boolean backward) {
        try (GpuDevice device = Devices.open(backend); GpuRope gpu = new GpuRope(device); var enc = device.encoder()) {
            List<Runnable> checks = new ArrayList<>();
            int seed = 0;
            for (Rope rope : plans(layout)) {
                for (int group : new int[] {1, 3}) {
                    SplittableRandom r = new SplittableRandom(++seed * 31L + layout.ordinal() + (backward ? 7 : 0));
                    int dim = rope.dim();
                    int ldx = dim + 3;
                    int xOff = 5;
                    int count = group * (POSITIONS.length + 40);
                    float[] x = new float[xOff + count * ldx + 2];
                    for (int i = 0; i < x.length; i++) {
                        x[i] = r.nextInt(30) == 0 ? GpuActivationsTest.SPECIALS[r.nextInt(GpuActivationsTest.SPECIALS.length)]
                                : (float) (r.nextDouble() * 20 - 10);
                    }
                    int tokens = (count - 1) / group + 1;
                    int posOff = 2;
                    long[] pos = new long[posOff + tokens];
                    for (int t = 0; t < tokens; t++) {
                        pos[posOff + t] = t < POSITIONS.length ? POSITIONS[t] : r.nextLong(0, 1L << 33);
                    }
                    float[] expected = x.clone();
                    for (int v = 0; v < count; v++) {
                        long p = pos[posOff + v / group];
                        if (backward) {
                            rope.applyBackward(expected, xOff + v * ldx, p);
                        } else {
                            rope.apply(expected, xOff + v * ldx, p);
                        }
                    }
                    GpuBuffer gx = Devices.upload(device, enc, x);
                    GpuBuffer freq = Devices.buffer(device, rope.dim());
                    enc.upload(freq, GpuRope.frequencies(rope.inverseFrequencies()));
                    GpuBuffer gp = Devices.buffer(device, 2L * pos.length);
                    enc.upload(gp, GpuRope.positions(pos));
                    if (backward) {
                        gpu.applyBackward(enc, rope.dim(), gl(layout), freq, gx, xOff, ldx, count, gp, posOff, group);
                    } else {
                        gpu.apply(enc, rope.dim(), gl(layout), freq, gx, xOff, ldx, count, gp, posOff, group);
                    }
                    var read = enc.download(gx);
                    String what = backend + " dim=" + dim + " group=" + group;
                    boolean hardware = Devices.hardware(backend);
                    checks.add(() -> Bits.assertSame(expected, Devices.floats(read, expected.length), hardware, what));
                }
            }
            Devices.run(device, enc);
            checks.forEach(Runnable::run);
        }
    }

    private static GpuRope.Layout gl(Rope.Layout l) {
        return l == Rope.Layout.INTERLEAVED ? GpuRope.Layout.INTERLEAVED : GpuRope.Layout.HALF;
    }

    @Test
    void rejectsBadArguments() {
        try (GpuDevice device = Devices.open("reference"); GpuRope gpu = new GpuRope(device);
                var enc = device.encoder()) {
            GpuBuffer x = Devices.buffer(device, 64);
            GpuBuffer freq = Devices.buffer(device, 8);
            GpuBuffer pos = Devices.buffer(device, 16);
            assertThrows(IllegalArgumentException.class, () -> gpu.apply(enc, 8, GpuRope.Layout.HALF, freq, x, 0, 7, 2, pos, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> gpu.apply(enc, 8, GpuRope.Layout.HALF, freq, x, 0, 8, 9, pos, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> gpu.apply(enc, 8, GpuRope.Layout.HALF, freq, x, 0, 8, 2, pos, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> gpu.apply(enc, 8, GpuRope.Layout.HALF, freq, x, 0, 8, 2, pos, 8, 1));
            assertThrows(IllegalArgumentException.class, () -> gpu.apply(enc, 8, GpuRope.Layout.HALF, x, x, 0, 8, 2, pos, 0, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> gpu.apply(enc, 8, GpuRope.Layout.HALF, Devices.buffer(device, 4), x, 0, 8, 2, pos, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> gpu.apply(enc, 7, GpuRope.Layout.HALF, freq, x, 0, 8, 2,
                    pos, 0, 1));
        }
    }
}
