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
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.GpuReadback;
import org.dynamisengine.vectrix.core.ReproducibleMath;
import org.dynamisengine.vectrix.nn.Optim;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link GpuOptim} against {@code Optim.adamW} and {@code Optim.sgd}, bit for bit, on every enabled backend: several
 * consecutive steps recorded in one encoder, offsets, sizes from one element to more than a million, special values
 * in every buffer, and hyperparameters from the usual to the degenerate.
 * <p>
 * One narrow allowance on hardware, for AdamW: a moment can be subnormal (for example {@code β2·v} for
 * {@code v = Float.MIN_NORMAL}), and with a small or zero {@code eps} the flushed moment changes the parameter, not
 * only the moment. On hardware a value is also accepted when it is exactly what {@link #adamWFlushed}, the Vectrix
 * loop with every subnormal flushed, computes; that model equals Vectrix wherever no subnormal occurs.
 */
class GpuOptimTest {

    private static final int[] SIZES = {1, 2, 3, 255, 256, 257, 1793, 4099, 100_003, 1_000_003};

    /** {lr, beta1, beta2, eps, weightDecay}. */
    private static final float[][] ADAM = {
        {1e-3f, 0.9f, 0.999f, 1e-8f, 0.01f},
        {0.1f, 0.5f, 0.9f, 1e-6f, 0f},
        {3e-4f, 0.95f, 0.99999f, 0f, 0.1f},
        {0f, 0f, 0f, 1e-8f, 0f},
        {1f, 0.9f, 1f, 1e-8f, 2f}, // β2 = 1: bc2 = 0
    };

    /** The first step number of each run; each run takes three steps from it. */
    private static final long[] STEPS = {1, 10, 1_000_000, Long.MAX_VALUE - 2};

    /** {lr, momentum, weightDecay}. */
    private static final float[][] SGD = {
        {0.01f, 0.9f, 1e-4f},
        {0.1f, 0f, 0f},
        {1f, 0.99f, 0.5f},
        {0f, 0.5f, 0f},
    };

    private static final float[] SPECIALS = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -0f,
        Float.MAX_VALUE, -Float.MAX_VALUE, Float.MIN_NORMAL, 1e30f, -1e-30f};

    static float[] values(SplittableRandom r, int len, double scale, boolean nonNegative) {
        float[] a = new float[len];
        for (int i = 0; i < len; i++) {
            if (r.nextInt(40) == 0) {
                float s = SPECIALS[r.nextInt(SPECIALS.length)];
                a[i] = nonNegative && s < 0 ? -s : s;
            } else {
                double u = nonNegative ? r.nextDouble() : r.nextDouble() * 2 - 1;
                a[i] = (float) (u * scale);
            }
        }
        return a;
    }

    static Stream<Arguments> adamCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (int h = 0; h < ADAM.length; h++) {
                for (long step : STEPS) {
                    out.add(Arguments.of(backend, h, step));
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} hyper={1} step={2}")
    @MethodSource("adamCases")
    void adamWMatchesOptim(String backend, int hyper, long step) {
        float[] h = ADAM[hyper];
        try (GpuDevice device = Devices.open(backend); GpuOptim optim = new GpuOptim(device);
                var enc = device.encoder()) {
            List<Runnable> checks = new ArrayList<>();
            for (int n : SIZES) {
                SplittableRandom r = new SplittableRandom(n * 7919L + hyper * 31L + step);
                int pOff = 3;
                int gOff = 1;
                int mOff = 0;
                int vOff = 5;
                float[] p = values(r, pOff + n + 2, 2, false);
                float[] m = values(r, mOff + n + 2, 0.1, false);
                float[] v = values(r, vOff + n + 2, 0.01, true);
                float[][] g = new float[3][];
                float[] pf = p.clone();
                float[] mf = m.clone();
                float[] vf = v.clone();
                var gp = Devices.upload(device, enc, p);
                var gm = Devices.upload(device, enc, m);
                var gv = Devices.upload(device, enc, v);
                for (int s = 0; s < 3; s++) {
                    g[s] = values(r, gOff + n + 1, 1, false);
                    var gg = Devices.upload(device, enc, g[s]);
                    optim.adamW(enc, gp, pOff, gg, gOff, gm, mOff, gv, vOff, n, h[0], h[1], h[2], h[3], h[4],
                            step + s);
                    Optim.adamW(p, pOff, g[s], gOff, m, mOff, v, vOff, n, h[0], h[1], h[2], h[3], h[4], step + s);
                    adamWFlushed(pf, pOff, g[s], gOff, mf, mOff, vf, vOff, n, h[0], h[1], h[2], h[3], h[4], step + s);
                }
                GpuReadback rp = enc.download(gp);
                GpuReadback rm = enc.download(gm);
                GpuReadback rv = enc.download(gv);
                String what = backend + " adamW hyper=" + hyper + " step=" + step + " n=" + n;
                boolean hw = Devices.hardware(backend);
                checks.add(() -> {
                    assertSameOrFlushed(p, pf, Devices.floats(rp, p.length), hw, what + " p");
                    assertSameOrFlushed(m, mf, Devices.floats(rm, m.length), hw, what + " m");
                    assertSameOrFlushed(v, vf, Devices.floats(rv, v.length), hw, what + " v");
                });
            }
            Devices.run(device, enc);
            checks.forEach(Runnable::run);
        }
    }

    /** A float as a GPU that flushes subnormals holds it: subnormals become zero of the same sign. */
    static float ftz(float x) {
        return Bits.subnormal(x) ? Math.copySign(0f, x) : x;
    }

    /**
     * {@code Optim.adamW}'s loop as a GPU that flushes subnormals computes it: the same operations, with every input
     * and every result flushed. It equals {@code Optim.adamW} wherever no subnormal occurs. With {@code eps = 0} a
     * flushed subnormal moment changes the parameter itself (to an infinity or NaN), so the test needs it.
     */
    static void adamWFlushed(float[] p, int pOff, float[] g, int gOff, float[] m, int mOff, float[] v, int vOff,
            int n, float lr, float beta1, float beta2, float eps, float weightDecay, long step) {
        float bc1 = ftz((float) (1.0 - ReproducibleMath.pow(beta1, step)));
        float bc2 = ftz((float) (1.0 - ReproducibleMath.pow(beta2, step)));
        float a1 = ftz(1f - beta1);
        float a2 = ftz(1f - beta2);
        float decay = ftz(1f - lr * weightDecay);
        for (int i = 0; i < n; i++) {
            float gi = ftz(g[gOff + i]);
            float mi = ftz(Math.fma(beta1, ftz(m[mOff + i]), ftz(a1 * gi)));
            float vi = ftz(Math.fma(beta2, ftz(v[vOff + i]), ftz(ftz(a2 * gi) * gi)));
            m[mOff + i] = mi;
            v[vOff + i] = vi;
            float update = ftz(ftz(mi / bc1) / ftz(ftz((float) Math.sqrt(ftz(vi / bc2))) + eps));
            p[pOff + i] = ftz(Math.fma(-lr, update, ftz(ftz(p[pOff + i]) * decay)));
        }
    }

    /**
     * Fails unless each value of {@code actual} matches Vectrix ({@link Bits#same}) or, on hardware, is exactly the
     * value the flush-to-zero model gives for it.
     */
    static void assertSameOrFlushed(float[] expected, float[] flushed, float[] actual, boolean hardware,
            String what) {
        float[] allowed = expected.clone();
        if (hardware) {
            for (int i = 0; i < actual.length; i++) {
                if (Float.floatToIntBits(flushed[i]) == Float.floatToIntBits(actual[i])) {
                    allowed[i] = actual[i];
                }
            }
        }
        Bits.assertSame(allowed, actual, hardware, what);
    }

    static Stream<Arguments> sgdCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (int h = 0; h < SGD.length; h++) {
                for (boolean nesterov : new boolean[] {false, true}) {
                    out.add(Arguments.of(backend, h, nesterov));
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} hyper={1} nesterov={2}")
    @MethodSource("sgdCases")
    void sgdMatchesOptim(String backend, int hyper, boolean nesterov) {
        float[] h = SGD[hyper];
        try (GpuDevice device = Devices.open(backend); GpuOptim optim = new GpuOptim(device);
                var enc = device.encoder()) {
            List<Runnable> checks = new ArrayList<>();
            for (int n : SIZES) {
                SplittableRandom r = new SplittableRandom(n * 7919L + hyper * 31L + (nesterov ? 1 : 0));
                int pOff = 2;
                int gOff = 4;
                int bOff = 1;
                float[] p = values(r, pOff + n + 2, 2, false);
                float[] b = values(r, bOff + n + 2, 0.5, false);
                var gp = Devices.upload(device, enc, p);
                var gb = Devices.upload(device, enc, b);
                for (int s = 0; s < 3; s++) {
                    float[] g = values(r, gOff + n + 1, 1, false);
                    var gg = Devices.upload(device, enc, g);
                    optim.sgd(enc, gp, pOff, gg, gOff, gb, bOff, n, h[0], h[1], h[2], nesterov);
                    Optim.sgd(p, pOff, g, gOff, b, bOff, n, h[0], h[1], h[2], nesterov);
                }
                GpuReadback rp = enc.download(gp);
                GpuReadback rb = enc.download(gb);
                String what = backend + " sgd hyper=" + hyper + " nesterov=" + nesterov + " n=" + n;
                boolean hw = Devices.hardware(backend);
                checks.add(() -> {
                    Bits.assertSame(p, Devices.floats(rp, p.length), hw, what + " p");
                    Bits.assertSame(b, Devices.floats(rb, b.length), hw, what + " buf");
                });
            }
            Devices.run(device, enc);
            checks.forEach(Runnable::run);
        }
    }
}
