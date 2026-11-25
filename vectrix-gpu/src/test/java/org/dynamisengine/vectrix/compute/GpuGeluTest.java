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
import org.dynamisengine.vectrix.core.ReproducibleMath;
import org.dynamisengine.vectrix.nn.Activations;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link GpuActivations#gelu} and {@link GpuActivations#geluBackward}, the exact GELU computed partly in double
 * through f64.slang, against {@code Activations.gelu} and {@code geluBackward} bit for bit, in every aliasing layout.
 * The float parts use fma, so like the other activations this runs on devices that fuse it.
 */
class GpuGeluTest {

    enum Layout { SEPARATE, SHARED_INPUTS, OUT_IS_A, OUT_IS_DY, ALL_SAME }

    private static final int[] SIZES = {1, 3, 257, 4099, 65_537};

    /** The specials of the activation tests, plus normalCdf's branch points: ±√2 as float, and erfc's clamp. */
    static final float[] SPECIALS;

    static {
        float sqrt2 = 1.4142135f;
        float[] extra = {sqrt2, -sqrt2, Math.nextUp(sqrt2), Math.nextDown(sqrt2), Math.nextUp(-sqrt2),
            Math.nextDown(-sqrt2), -14.28f, -14.3f, -38f, -13f, -13.5f, -12.9f, 2.77f, 2.78f, 5.5f, -5.5f, 0.001f,
            -0.001f, Float.MIN_VALUE, -Float.MIN_VALUE};
        float[] base = GpuActivationsTest.SPECIALS;
        SPECIALS = Arrays.copyOf(base, base.length + extra.length);
        System.arraycopy(extra, 0, SPECIALS, base.length, extra.length);
    }

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (boolean backward : new boolean[] {false, true}) {
                for (Layout layout : Layout.values()) {
                    if (backward || layout == Layout.SEPARATE || layout == Layout.OUT_IS_A) {
                        out.add(Arguments.of(backend, backward, layout));
                    }
                }
            }
        }
        return out.stream();
    }

    static float[] values(SplittableRandom r, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            int k = r.nextInt(20);
            a[i] = k == 0 ? SPECIALS[r.nextInt(SPECIALS.length)]
                    : k == 1 ? Float.intBitsToFloat(r.nextInt())
                    : k < 5 ? (float) (r.nextDouble() * 40 - 30)
                    : (float) (r.nextDouble() * 8 - 4);
        }
        return a;
    }

    @ParameterizedTest(name = "{0} backward={1} {2}")
    @MethodSource("cases")
    void matchesActivations(String backend, boolean backward, Layout layout) {
        try (GpuDevice device = Devices.open(backend); GpuActivations acts = new GpuActivations(device);
                var enc = device.encoder()) {
            List<Runnable> checks = new ArrayList<>();
            for (int n : SIZES) {
                SplittableRandom r = new SplittableRandom(n * 7919L + layout.ordinal() + (backward ? 100 : 0));
                checks.add(record(backend, device, enc, acts, backward, layout, values(r, n), values(r, n),
                        "n=" + n));
            }
            int s = SPECIALS.length;
            float[] a = new float[s * s];
            float[] dy = new float[s * s];
            for (int i = 0; i < s; i++) {
                for (int j = 0; j < s; j++) {
                    a[i * s + j] = SPECIALS[i];
                    dy[i * s + j] = SPECIALS[j];
                }
            }
            checks.add(record(backend, device, enc, acts, backward, layout, a, dy, "specials"));
            Devices.run(device, enc);
            checks.forEach(Runnable::run);
        }
    }

    private record Buf(float[] host, GpuBuffer gpu) {
    }

    private static Buf buf(GpuDevice device, GpuCommandEncoder enc, float[] host) {
        return new Buf(host, Devices.upload(device, enc, host));
    }

    private static float[] padded(int pad, float[] values) {
        float[] out = new float[pad + values.length + 2];
        Arrays.fill(out, 12345f);
        System.arraycopy(values, 0, out, pad, values.length);
        return out;
    }

    private static Runnable record(String backend, GpuDevice device, GpuCommandEncoder enc, GpuActivations acts,
            boolean backward, Layout layout, float[] a, float[] dy, String what) {
        int n = a.length;
        int aOff = 3;
        int dyOff = 5;
        int dxOff = 2;
        switch (layout) {
            case SEPARATE -> {
                Buf ba = buf(device, enc, padded(aOff, a));
                Buf bdy = buf(device, enc, padded(dyOff, dy));
                Buf out = buf(device, enc, padded(dxOff, new float[n]));
                float[] expected = out.host.clone();
                if (backward) {
                    Activations.geluBackward(ba.host, aOff, bdy.host, dyOff, expected, dxOff, n);
                    acts.geluBackward(enc, ba.gpu, aOff, bdy.gpu, dyOff, out.gpu, dxOff, n);
                } else {
                    Activations.gelu(ba.host, aOff, expected, dxOff, n);
                    acts.gelu(enc, ba.gpu, aOff, out.gpu, dxOff, n);
                }
                return check(backend, enc, backward, out, expected, dxOff, a, dy, what + " " + layout);
            }
            case SHARED_INPUTS -> {
                int sharedDyOff = aOff + n + 4;
                float[] both = new float[sharedDyOff + n + 1];
                Arrays.fill(both, 12345f);
                System.arraycopy(a, 0, both, aOff, n);
                System.arraycopy(dy, 0, both, sharedDyOff, n);
                Buf shared = buf(device, enc, both);
                Buf out = buf(device, enc, padded(dxOff, new float[n]));
                float[] expected = out.host.clone();
                Activations.geluBackward(shared.host, aOff, shared.host, sharedDyOff, expected, dxOff, n);
                acts.geluBackward(enc, shared.gpu, aOff, shared.gpu, sharedDyOff, out.gpu, dxOff, n);
                return check(backend, enc, true, out, expected, dxOff, a, dy, what + " " + layout);
            }
            case OUT_IS_A -> {
                Buf out = buf(device, enc, padded(aOff, a));
                float[] expected = out.host.clone();
                if (backward) {
                    Buf bdy = buf(device, enc, padded(dyOff, dy));
                    Activations.geluBackward(expected, aOff, bdy.host, dyOff, expected, aOff, n);
                    acts.geluBackward(enc, out.gpu, aOff, bdy.gpu, dyOff, out.gpu, aOff, n);
                } else {
                    Activations.gelu(expected, aOff, expected, aOff, n);
                    acts.gelu(enc, out.gpu, aOff, out.gpu, aOff, n);
                }
                return check(backend, enc, backward, out, expected, aOff, a, dy, what + " " + layout);
            }
            case OUT_IS_DY -> {
                Buf ba = buf(device, enc, padded(aOff, a));
                Buf out = buf(device, enc, padded(dyOff, dy));
                float[] expected = out.host.clone();
                Activations.geluBackward(ba.host, aOff, expected, dyOff, expected, dyOff, n);
                acts.geluBackward(enc, ba.gpu, aOff, out.gpu, dyOff, out.gpu, dyOff, n);
                return check(backend, enc, true, out, expected, dyOff, a, dy, what + " " + layout);
            }
            default -> {
                Buf out = buf(device, enc, padded(aOff, a));
                float[] expected = out.host.clone();
                Activations.geluBackward(expected, aOff, expected, aOff, expected, aOff, n);
                acts.geluBackward(enc, out.gpu, aOff, out.gpu, aOff, out.gpu, aOff, n);
                return check(backend, enc, true, out, expected, aOff, a, a, what + " " + layout);
            }
        }
    }

    private static Runnable check(String backend, GpuCommandEncoder enc, boolean backward, Buf out,
            float[] expected, int outOff, float[] a, float[] dy, String what) {
        GpuReadback read = enc.download(out.gpu);
        return () -> {
            float[] actual = Devices.floats(read, expected.length);
            float[] allowed = expected.clone();
            boolean hardware = Devices.hardware(backend);
            if (hardware) {
                for (int i = 0; i < a.length; i++) {
                    Float f = flushed(backward, a[i], dy[i]);
                    if (f != null && Float.floatToIntBits(f) == Float.floatToIntBits(actual[outOff + i])) {
                        allowed[outOff + i] = actual[outOff + i];
                    }
                }
            }
            Bits.assertSame(allowed, actual, hardware, backend + " gelu" + (backward ? "Backward " : " ") + what);
        };
    }

    /**
     * The result on a GPU that flushes subnormal floats to zero: Φ(x) and φ(x), computed exactly by the integer code,
     * the inputs, and the float intermediate fma(x, φ, Φ) each read as 0 when subnormal. Null if nothing is flushed.
     */
    static Float flushed(boolean backward, float a, float dy) {
        float c = flush(ReproducibleMath.normalCdf(a));
        float x = flush(a);
        if (!backward) {
            return x * c;
        }
        float p = flush(ReproducibleMath.normalPdf(a));
        return flush(dy) * flush(Math.fma(x, p, c));
    }

    private static float flush(float v) {
        return Bits.subnormal(v) ? Math.copySign(0f, v) : v;
    }
}
