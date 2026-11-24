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
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.GpuReadback;
import org.dynamisengine.vectrix.compute.GpuActivations.Op;
import org.dynamisengine.vectrix.core.ReproducibleMath;
import org.dynamisengine.vectrix.nn.Activations;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link GpuActivations} against {@code Activations}, bit for bit, on every enabled backend: every op, every way the
 * output may alias an input, offsets, sizes from one element to more than a million (partial groups and grids wider
 * than the reference backend's 7 group columns), and NaN, infinities, signed zeros and the branch points of the
 * reproducible functions.
 * <p>
 * One narrow allowance on hardware, beyond {@link Bits}' zero for a subnormal output: where an intermediate is
 * subnormal but the output is not, a GPU that flushes subnormals computes the output from a zero intermediate. That
 * happens in SiLU, its gradient and the softplus gradient, whose {@code s = sigmoid(x)} is subnormal for {@code x} in
 * about (-88.7, -87.3), and in softplus below about -86.6, where {@code log1p}'s {@code e/(2 + e)} is subnormal.
 * There, and only there, the Vectrix formula with that intermediate set to 0 is also accepted, bit for bit (see
 * {@link #flushed}). No input in these tests is subnormal.
 */
class GpuActivationsTest {

    /** How the buffers of a call alias. Forward ops use SEPARATE and OUT_IS_A (in place). */
    enum Layout {
        /** Every array in its own buffer. */
        SEPARATE,
        /** The forward value and dy in one buffer at different offsets, dx in its own. */
        SHARED_INPUTS,
        /** The output overwrites the forward value (the input for forward ops). */
        OUT_IS_A,
        /** dx overwrites dy. */
        OUT_IS_DY,
        /** The forward value, dy and dx are the same elements. */
        ALL_SAME
    }

    private static final int[] SIZES = {1, 2, 3, 255, 256, 257, 1000, 1793, 4099, 100_003, 1_000_003};

    /** NaN, infinities, signed zeros, extremes, and the clamps and branch points of exp, expm1, tanh and log1p. */
    static final float[] SPECIALS = {
        Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -0f, 1f, -1f, Float.MAX_VALUE,
        -Float.MAX_VALUE, Float.MIN_NORMAL, -Float.MIN_NORMAL, 0.25f, -0.25f, Math.nextDown(0.25f), 0.5f, -0.5f, 9.1f,
        -9.1f, 1e-20f, -1e-20f, 88f, 88.8f, -88f, -87.5f, -88.5f, -89f, 89f, 104f, -104f, -105f, 0.41421357f,
        -0.29289323f, 1e30f, -1e30f, 3e-19f, Float.intBitsToFloat(0x7FC00001), Float.intBitsToFloat(0xFFC00000),
    };

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (Op op : Op.values()) {
                for (Layout layout : Layout.values()) {
                    if (op.backward() || layout == Layout.SEPARATE || layout == Layout.OUT_IS_A) {
                        out.add(Arguments.of(backend, op, layout));
                    }
                }
            }
        }
        return out.stream();
    }

    /** Values for the forward value of {@code op}: the input, or for the sigmoid and tanh gradients the output. */
    static float[] forwardValues(Op op, SplittableRandom r, int n) {
        boolean fromOutput = op == Op.SIGMOID_BACKWARD || op == Op.TANH_BACKWARD;
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            int k = r.nextInt(20);
            if (k == 0) {
                a[i] = SPECIALS[r.nextInt(SPECIALS.length)];
            } else if (fromOutput) {
                a[i] = (float) (r.nextDouble() * 3 - 1.5);
            } else if (k < 4) {
                a[i] = (float) (r.nextDouble() * 240 - 120);
            } else {
                a[i] = (float) (r.nextDouble() * 16 - 8);
            }
        }
        return a;
    }

    static float[] gradients(SplittableRandom r, int n) {
        float[] dy = new float[n];
        for (int i = 0; i < n; i++) {
            dy[i] = r.nextInt(20) == 0 ? SPECIALS[r.nextInt(SPECIALS.length)] : (float) (r.nextDouble() * 16 - 8);
        }
        return dy;
    }

    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("cases")
    void matchesActivations(String backend, Op op, Layout layout) {
        try (GpuDevice device = Devices.open(backend); GpuActivations acts = new GpuActivations(device);
                var enc = device.encoder()) {
            List<Runnable> checks = new ArrayList<>();
            for (int n : SIZES) {
                SplittableRandom r = new SplittableRandom(n * 1_000_003L + op.ordinal() * 31L + layout.ordinal());
                checks.add(record(backend, device, enc, acts, op, layout, forwardValues(op, r, n), gradients(r, n),
                        "n=" + n));
            }
            Devices.run(device, enc);
            checks.forEach(Runnable::run);
        }
    }

    static Stream<Arguments> specialCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (Op op : Op.values()) {
                out.add(Arguments.of(backend, op));
            }
        }
        return out.stream();
    }

    /** Every special value as the forward value, against every special value as dy. */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("specialCases")
    void specialValuesMatchActivations(String backend, Op op) {
        int s = SPECIALS.length;
        float[] a = new float[s * s];
        float[] dy = new float[s * s];
        for (int i = 0; i < s; i++) {
            for (int j = 0; j < s; j++) {
                a[i * s + j] = SPECIALS[i];
                dy[i * s + j] = SPECIALS[j];
            }
        }
        try (GpuDevice device = Devices.open(backend); GpuActivations acts = new GpuActivations(device);
                var enc = device.encoder()) {
            List<Runnable> checks = new ArrayList<>();
            for (Layout layout : Layout.values()) {
                if (op.backward() || layout == Layout.SEPARATE || layout == Layout.OUT_IS_A) {
                    checks.add(record(backend, device, enc, acts, op, layout, a, dy, "specials " + layout));
                }
            }
            Devices.run(device, enc);
            checks.forEach(Runnable::run);
        }
    }

    /** A buffer's contents on the host and on the device. */
    private record Buf(float[] host, GpuBuffer gpu) {
    }

    private static Buf buf(GpuDevice device, GpuCommandEncoder enc, float[] host) {
        return new Buf(host, Devices.upload(device, enc, host));
    }

    /** {@code pad} sentinels, the values, then two sentinels. */
    private static float[] padded(int pad, float[] values) {
        float[] out = new float[pad + values.length + 2];
        Arrays.fill(out, 12345f);
        System.arraycopy(values, 0, out, pad, values.length);
        return out;
    }

    /**
     * Records {@code op} on the given values with the buffers aliased as {@code layout} says, computes the Vectrix
     * result with the same aliasing, and returns the check to run once the encoder completed.
     */
    private static Runnable record(String backend, GpuDevice device, GpuCommandEncoder enc, GpuActivations acts,
            Op op, Layout layout, float[] a, float[] dy, String what) {
        int n = a.length;
        int aOff = 3;
        int dyOff = 5;
        int dxOff = 2;
        Buf out;
        switch (layout) {
            case SEPARATE -> {
                Buf ba = buf(device, enc, padded(aOff, a));
                Buf bdy = buf(device, enc, padded(dyOff, dy));
                float[] dx = padded(dxOff, new float[n]);
                Arrays.fill(dx, dxOff, dxOff + n, -777f);
                out = buf(device, enc, dx);
                float[] expected = dx.clone();
                if (op.backward()) {
                    ElementwiseModels.backward(op).apply(ba.host, aOff, bdy.host, dyOff, expected, dxOff, n);
                    gpuBackward(acts, op, enc, ba.gpu, aOff, bdy.gpu, dyOff, out.gpu, dxOff, n);
                } else {
                    ElementwiseModels.forward(op).apply(ba.host, aOff, expected, dxOff, n);
                    gpuForward(acts, op, enc, ba.gpu, aOff, out.gpu, dxOff, n);
                }
                return check(backend, enc, op, out, expected, dxOff, a, dy, what + " " + layout);
            }
            case SHARED_INPUTS -> {
                int sharedDyOff = aOff + n + 4;
                float[] both = new float[sharedDyOff + n + 1];
                Arrays.fill(both, 12345f);
                System.arraycopy(a, 0, both, aOff, n);
                System.arraycopy(dy, 0, both, sharedDyOff, n);
                Buf shared = buf(device, enc, both);
                float[] dx = padded(dxOff, new float[n]);
                out = buf(device, enc, dx);
                float[] expected = dx.clone();
                ElementwiseModels.backward(op).apply(shared.host, aOff, shared.host, sharedDyOff, expected, dxOff, n);
                gpuBackward(acts, op, enc, shared.gpu, aOff, shared.gpu, sharedDyOff, out.gpu, dxOff, n);
                return check(backend, enc, op, out, expected, dxOff, a, dy, what + " " + layout);
            }
            case OUT_IS_A -> {
                out = buf(device, enc, padded(aOff, a));
                float[] expected = out.host.clone();
                if (op.backward()) {
                    Buf bdy = buf(device, enc, padded(dyOff, dy));
                    ElementwiseModels.backward(op).apply(expected, aOff, bdy.host, dyOff, expected, aOff, n);
                    gpuBackward(acts, op, enc, out.gpu, aOff, bdy.gpu, dyOff, out.gpu, aOff, n);
                } else {
                    ElementwiseModels.forward(op).apply(expected, aOff, expected, aOff, n);
                    gpuForward(acts, op, enc, out.gpu, aOff, out.gpu, aOff, n);
                }
                return check(backend, enc, op, out, expected, aOff, a, dy, what + " " + layout);
            }
            case OUT_IS_DY -> {
                Buf ba = buf(device, enc, padded(aOff, a));
                out = buf(device, enc, padded(dyOff, dy));
                float[] expected = out.host.clone();
                ElementwiseModels.backward(op).apply(ba.host, aOff, expected, dyOff, expected, dyOff, n);
                gpuBackward(acts, op, enc, ba.gpu, aOff, out.gpu, dyOff, out.gpu, dyOff, n);
                return check(backend, enc, op, out, expected, dyOff, a, dy, what + " " + layout);
            }
            default -> { // ALL_SAME
                out = buf(device, enc, padded(aOff, a));
                float[] expected = out.host.clone();
                ElementwiseModels.backward(op).apply(expected, aOff, expected, aOff, expected, aOff, n);
                gpuBackward(acts, op, enc, out.gpu, aOff, out.gpu, aOff, out.gpu, aOff, n);
                return check(backend, enc, op, out, expected, aOff, a, a, what + " " + layout);
            }
        }
    }

    private static Runnable check(String backend, GpuCommandEncoder enc, Op op, Buf out, float[] expected,
            int outOff, float[] a, float[] dy, String what) {
        GpuReadback read = enc.download(out.gpu);
        return () -> {
            float[] actual = Devices.floats(read, expected.length);
            float[] allowed = expected.clone();
            boolean hardware = Devices.hardware(backend);
            if (hardware) {
                for (int i = 0; i < a.length; i++) {
                    Float f = flushed(op, a[i], dy[i]);
                    if (f != null && Float.floatToIntBits(f) == Float.floatToIntBits(actual[outOff + i])) {
                        allowed[outOff + i] = actual[outOff + i];
                    }
                }
            }
            Bits.assertSame(allowed, actual, hardware, backend + " " + op + " " + what);
        };
    }

    /**
     * The result a GPU that flushes subnormals gives where a subnormal intermediate of {@code op} becomes 0 and the
     * output is a normal float (or infinite times it), or null where no such intermediate occurs: the sigmoid of
     * SiLU, its gradient and the softplus gradient for {@code x} in about (-88.7, -87.3), and in softplus
     * {@code e = e^-|x|} or log1p's {@code e/(2 + e)} for {@code x} below about -86.6. The formulas are the Vectrix
     * ones with that intermediate replaced by 0.
     */
    static Float flushed(Op op, float a, float dy) {
        switch (op) {
            case SILU, SILU_BACKWARD, SOFTPLUS_BACKWARD -> {
                if (!Bits.subnormal(ReproducibleMath.sigmoid(a))) {
                    return null;
                }
                float s = 0f;
                return switch (op) {
                    case SILU -> a * s;
                    case SILU_BACKWARD -> dy * Math.fma(a * s, 1f - s, s);
                    default -> dy * s;
                };
            }
            case SOFTPLUS -> {
                float e = ReproducibleMath.exp(-Math.abs(a));
                if (!Bits.subnormal(e) && !Bits.subnormal(e / (2f + e))) {
                    return null;
                }
                return Math.max(a, 0f) + 0f; // log1p(0) = 0
            }
            default -> {
                return null;
            }
        }
    }

    static void gpuForward(GpuActivations acts, Op op, GpuCommandEncoder enc, GpuBuffer x, long xOff, GpuBuffer y,
            long yOff, int n) {
        switch (op) {
            case RELU -> acts.relu(enc, x, xOff, y, yOff, n);
            case SIGMOID -> acts.sigmoid(enc, x, xOff, y, yOff, n);
            case SILU -> acts.silu(enc, x, xOff, y, yOff, n);
            case TANH -> acts.tanh(enc, x, xOff, y, yOff, n);
            case GELU_TANH -> acts.geluTanh(enc, x, xOff, y, yOff, n);
            case SOFTPLUS -> acts.softplus(enc, x, xOff, y, yOff, n);
            default -> throw new IllegalArgumentException(op.toString());
        }
    }

    static void gpuBackward(GpuActivations acts, Op op, GpuCommandEncoder enc, GpuBuffer a, long aOff, GpuBuffer dy,
            long dyOff, GpuBuffer dx, long dxOff, int n) {
        switch (op) {
            case RELU_BACKWARD -> acts.reluBackward(enc, a, aOff, dy, dyOff, dx, dxOff, n);
            case SIGMOID_BACKWARD -> acts.sigmoidBackward(enc, a, aOff, dy, dyOff, dx, dxOff, n);
            case SILU_BACKWARD -> acts.siluBackward(enc, a, aOff, dy, dyOff, dx, dxOff, n);
            case TANH_BACKWARD -> acts.tanhBackward(enc, a, aOff, dy, dyOff, dx, dxOff, n);
            case GELU_TANH_BACKWARD -> acts.geluTanhBackward(enc, a, aOff, dy, dyOff, dx, dxOff, n);
            case SOFTPLUS_BACKWARD -> acts.softplusBackward(enc, a, aOff, dy, dyOff, dx, dxOff, n);
            default -> throw new IllegalArgumentException(op.toString());
        }
    }

    // ---- dropout ----

    private static final long[] SEEDS = {0L, -1L, 0x0123_4567_89AB_CDEFL};

    /** Starts at each word of a block, past 2^32, and wrapping past 2^64. */
    private static final long[] STARTS = {0L, 1L, 2L, 3L, (1L << 40) - 2, -3L};

    private static final float[] PROBS = {0f, 0.1f, 0.5f, 0.9f, Math.nextDown(1f)};

    private static final int[] DROPOUT_SIZES = {1, 2, 3, 4, 5, 255, 256, 257, 1793, 7169, 100_003};

    static Stream<Arguments> dropoutCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (long seed : SEEDS) {
                for (long start : STARTS) {
                    for (boolean inPlace : new boolean[] {false, true}) {
                        out.add(Arguments.of(backend, seed, start, inPlace));
                    }
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} seed={1} start={2} inPlace={3}")
    @MethodSource("dropoutCases")
    void dropoutMatchesActivations(String backend, long seed, long start, boolean inPlace) {
        try (GpuDevice device = Devices.open(backend); GpuActivations acts = new GpuActivations(device);
                var enc = device.encoder()) {
            List<Runnable> checks = new ArrayList<>();
            for (float p : PROBS) {
                for (int n : DROPOUT_SIZES) {
                    SplittableRandom r = new SplittableRandom(n * 31L + Float.floatToIntBits(p));
                    float[] x = padded(3, forwardValues(Op.RELU, r, n));
                    Buf bx = buf(device, enc, x);
                    Buf out;
                    float[] expected;
                    String what = backend + " dropout seed=" + seed + " start=" + start + " p=" + p + " n=" + n;
                    if (inPlace) {
                        out = bx;
                        expected = x.clone();
                        Activations.dropout(seed, start, p, expected, 3, expected, 3, n);
                        acts.dropout(enc, seed, start, p, bx.gpu, 3, bx.gpu, 3, n);
                    } else {
                        out = buf(device, enc, padded(1, new float[n]));
                        expected = out.host.clone();
                        Activations.dropout(seed, start, p, x, 3, expected, 1, n);
                        acts.dropout(enc, seed, start, p, bx.gpu, 3, out.gpu, 1, n);
                    }
                    GpuReadback read = enc.download(out.gpu);
                    float[] exp = expected;
                    checks.add(() -> Bits.assertSame(exp, Devices.floats(read, exp.length), Devices.hardware(backend),
                            what + (inPlace ? " in place" : "")));
                }
            }
            Devices.run(device, enc);
            checks.forEach(Runnable::run);
        }
    }
}
