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
package org.dynamisengine.vectrix.nn;

import org.dynamisengine.vectrix.hash.Philox;
import org.dynamisengine.vectrix.core.ReproducibleMath;
//#ifdef __HAS_VECTOR_API__
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.dynamisengine.vectrix.simd.SimdSupport;
//#endif

/**
 * Element-wise activation functions over float arrays, forward and backward.
 * <p>
 * Forward kernels write {@code y[i] = f(x[i])}; backward kernels write {@code dx[i] = dy[i]·f'(x[i])}, from the
 * forward input, except sigmoid and tanh, whose derivatives are cheaper and more accurate from the forward output. Outputs may alias their inputs at the same offset. The transcendental parts are {@link org.dynamisengine.vectrix.core.ReproducibleMath}'s,
 * so results are the same bits everywhere; ReLU, sigmoid and SiLU also have Vector API paths that perform the same
 * operations. Nothing allocates.
 *
 * @since 2.5.0
 */
public final class Activations {

    private static final float GELU_TANH_K = 0.7978846f;  // √(2/π)
    private static final float GELU_TANH_C = 0.044715f;

    private Activations() {
    }

    /** {@code max(x, 0)}; NaN stays NaN. */
    public static void relu(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        int i = 0;
//#ifdef __HAS_VECTOR_API__
        if (Simd.on()) {
            for (int b = n - n % Simd.LANES; i < b; i += Simd.LANES) {
                FloatVector.fromArray(Simd.S, x, xOff + i).max(0f).intoArray(y, yOff + i);
            }
        }
//#endif
        for (; i < n; i++) {
            y[yOff + i] = Math.max(x[xOff + i], 0f);
        }
    }

    /** ReLU backward: {@code dy} where {@code x > 0}, else 0. */
    public static void reluBackward(float[] x, int xOff, float[] dy, int dyOff, float[] dx, int dxOff, int n) {
        check(x, xOff, n, "x");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        for (int i = 0; i < n; i++) {
            dx[dxOff + i] = x[xOff + i] > 0f ? dy[dyOff + i] : 0f;
        }
    }

    /** The logistic function {@code 1 / (1 + e^-x)}. */
    public static void sigmoid(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        int i = 0;
//#ifdef __HAS_VECTOR_API__
        if (Simd.on()) {
            i = Simd.logistic(x, xOff, y, yOff, n, false);
        }
//#endif
        for (; i < n; i++) {
            y[yOff + i] = ReproducibleMath.sigmoid(x[xOff + i]);
        }
    }

    /** SiLU (swish): {@code x·sigmoid(x)}. */
    public static void silu(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        int i = 0;
//#ifdef __HAS_VECTOR_API__
        if (Simd.on()) {
            i = Simd.logistic(x, xOff, y, yOff, n, true);
        }
//#endif
        for (; i < n; i++) {
            float v = x[xOff + i];
            y[yOff + i] = v * ReproducibleMath.sigmoid(v);
        }
    }

    /** SiLU backward: {@code dy·(s + x·s·(1 - s))}, {@code s = sigmoid(x)}. */
    public static void siluBackward(float[] x, int xOff, float[] dy, int dyOff, float[] dx, int dxOff, int n) {
        check(x, xOff, n, "x");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        for (int i = 0; i < n; i++) {
            float v = x[xOff + i];
            float s = ReproducibleMath.sigmoid(v);
            dx[dxOff + i] = dy[dyOff + i] * Math.fma(v * s, 1f - s, s);
        }
    }

    /** {@code tanh x}. */
    public static void tanh(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        int i = 0;
//#ifdef __HAS_VECTOR_API__
        if (Simd.on()) {
            i = Simd.tanh(x, xOff, y, yOff, n);
        }
//#endif
        for (; i < n; i++) {
            y[yOff + i] = ReproducibleMath.tanh(x[xOff + i]);
        }
    }

    /**
     * GELU, exact form: {@code x·Φ(x)} with {@link org.dynamisengine.vectrix.core.ReproducibleMath#normalCdf}, which keeps its relative accuracy for negative
     * {@code x}, where {@code (1 + erf(x/√2))/2} would cancel.
     */
    public static void gelu(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        for (int i = 0; i < n; i++) {
            float v = x[xOff + i];
            y[yOff + i] = v * ReproducibleMath.normalCdf(v);
        }
    }

    /** GELU backward: {@code dy·(Φ(x) + x·φ(x))}, {@code φ} the standard normal density. */
    public static void geluBackward(float[] x, int xOff, float[] dy, int dyOff, float[] dx, int dxOff, int n) {
        check(x, xOff, n, "x");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        for (int i = 0; i < n; i++) {
            float v = x[xOff + i];
            float cdf = ReproducibleMath.normalCdf(v);
            float pdf = ReproducibleMath.normalPdf(v);
            dx[dxOff + i] = dy[dyOff + i] * Math.fma(v, pdf, cdf);
        }
    }

    /** GELU, tanh approximation: {@code x·(1 + tanh(√(2/π)·(x + 0.044715·x³)))/2} (as in GPT-2). */
    public static void geluTanh(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        int i = 0;
//#ifdef __HAS_VECTOR_API__
        if (Simd.on()) {
            i = Simd.geluTanh(x, xOff, y, yOff, n);
        }
//#endif
        for (; i < n; i++) {
            float v = x[xOff + i];
            float u = GELU_TANH_K * Math.fma(GELU_TANH_C * v, v * v, v);
            y[yOff + i] = 0.5f * v * (1f + ReproducibleMath.tanh(u));
        }
    }

    /** Softplus {@code log(1 + e^x)}, computed as {@code max(x, 0) + log1p(e^-|x|)} without overflow. */
    public static void softplus(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        for (int i = 0; i < n; i++) {
            float v = x[xOff + i];
            y[yOff + i] = Math.max(v, 0f) + ReproducibleMath.log1p(ReproducibleMath.exp(-Math.abs(v)));
        }
    }

    /** Sigmoid backward from its <i>output</i> {@code y = sigmoid(x)}: {@code dy·y·(1 - y)}. */
    public static void sigmoidBackward(float[] y, int yOff, float[] dy, int dyOff, float[] dx, int dxOff, int n) {
        check(y, yOff, n, "y");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        for (int i = 0; i < n; i++) {
            float v = y[yOff + i];
            dx[dxOff + i] = dy[dyOff + i] * (v * (1f - v));
        }
    }

    /**
     * Tanh backward from its <i>output</i> {@code y = tanh(x)}: {@code dy·(1 - y)(1 + y)}, which unlike
     * {@code 1 - y²} keeps its relative accuracy as {@code |y|} approaches 1.
     */
    public static void tanhBackward(float[] y, int yOff, float[] dy, int dyOff, float[] dx, int dxOff, int n) {
        check(y, yOff, n, "y");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        for (int i = 0; i < n; i++) {
            float v = y[yOff + i];
            dx[dxOff + i] = dy[dyOff + i] * ((1f - v) * (1f + v));
        }
    }

    /**
     * Backward of the tanh-approximated GELU, from its input:
     * {@code dy·((1 + t)/2 + x·(1 - t)(1 + t)/2·√(2/π)·(1 + 3·0.044715·x²))}, {@code t = tanh(u)}.
     */
    public static void geluTanhBackward(float[] x, int xOff, float[] dy, int dyOff, float[] dx, int dxOff, int n) {
        check(x, xOff, n, "x");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        for (int i = 0; i < n; i++) {
            float v = x[xOff + i];
            float v2 = v * v;
            float t = ReproducibleMath.tanh(GELU_TANH_K * Math.fma(GELU_TANH_C * v, v2, v));
            float du = GELU_TANH_K * Math.fma(3f * GELU_TANH_C, v2, 1f);
            float d = Math.fma(0.5f * v * ((1f - t) * (1f + t)), du, 0.5f * (1f + t));
            dx[dxOff + i] = dy[dyOff + i] * d;
        }
    }

    /** Softplus backward from its input: {@code dy·sigmoid(x)}. */
    public static void softplusBackward(float[] x, int xOff, float[] dy, int dyOff, float[] dx, int dxOff, int n) {
        check(x, xOff, n, "x");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        for (int i = 0; i < n; i++) {
            dx[dxOff + i] = dy[dyOff + i] * ReproducibleMath.sigmoid(x[xOff + i]);
        }
    }

    private static void check(float[] a, int off, int n, String name) {
        if (a == null || n < 0 || off < 0 || off > a.length - n) {
            throw new IllegalArgumentException(name + " must hold " + n + " values from offset " + off);
        }
    }

    /** Vector API helpers, in a holder so the scalar build never loads {@code jdk.incubator.vector}. */
    private static final class Simd {
//#ifdef __HAS_VECTOR_API__
        static final VectorSpecies<Float> S = FloatVector.SPECIES_PREFERRED;
        static final int LANES = S.length();
        static final boolean AVAILABLE = SimdSupport.isVectorApiAvailable() && LANES >= 2;

        private Simd() {
        }

        static boolean on() {
            return AVAILABLE && SimdSupport.backend() == SimdSupport.Backend.VECTOR_API;
        }

        static final FloatVector ONE = FloatVector.broadcast(S, 1f);
        static final FloatVector E8V = FloatVector.broadcast(S, NnConstants.E8);
        static final FloatVector E7V = FloatVector.broadcast(S, NnConstants.E7);
        static final FloatVector E6V = FloatVector.broadcast(S, NnConstants.E6);
        static final FloatVector MLN2_HI = FloatVector.broadcast(S, -NnConstants.LN2_HI);
        static final FloatVector MLN2_LO = FloatVector.broadcast(S, -NnConstants.LN2_LO);
        static final FloatVector E5V = FloatVector.broadcast(S, NnConstants.E5);
        static final FloatVector E4V = FloatVector.broadcast(S, NnConstants.E4);
        static final FloatVector E3V = FloatVector.broadcast(S, NnConstants.E3);
        static final FloatVector E2V = FloatVector.broadcast(S, NnConstants.E2);

        /** Sigmoid ({@code silu} false) or SiLU on whole vectors, with {@link org.dynamisengine.vectrix.core.ReproducibleMath#exp(float)} expanded inline. */
        static int logistic(float[] x, int xOff, float[] y, int yOff, int n, boolean silu) {
            int i = 0;
            for (int b = n - n % LANES; i < b; i += LANES) {
                FloatVector v = FloatVector.fromArray(S, x, xOff + i);
                FloatVector nv = v.neg();
                FloatVector ec = nv.max(-104f).min(89f);
                FloatVector ek = ec.mul(NnConstants.LOG2E).add(NnConstants.ROUND).sub(NnConstants.ROUND);
                FloatVector er = ek.fma(MLN2_HI, ec);
                er = ek.fma(MLN2_LO, er);
                FloatVector ep = er.fma(NnConstants.E7, NnConstants.E6);
                ep = er.fma(ep, E5V);
                ep = er.fma(ep, E4V);
                ep = er.fma(ep, E3V);
                ep = er.fma(ep, E2V);
                FloatVector et = ep.fma(er.mul(er), er).add(1f);
                IntVector eki = (IntVector) ek.convert(VectorOperators.F2I, 0);
                IntVector ek1 = eki.lanewise(VectorOperators.ASHR, 1);
                FloatVector e = et.mul(ek1.add(127).lanewise(VectorOperators.LSHL, 23).reinterpretAsFloats())
                        .mul(eki.sub(ek1).add(127).lanewise(VectorOperators.LSHL, 23).reinterpretAsFloats());
                FloatVector s = ONE.div(e.add(1f));
                (silu ? v.mul(s) : s).intoArray(y, yOff + i);
            }
            return i;
        }
        /** {@link org.dynamisengine.vectrix.core.ReproducibleMath#tanh} on whole vectors, with expm1 and exp expanded inline; returns the index reached. */
        static int tanh(float[] x, int xOff, float[] y, int yOff, int n) {
            int i = 0;
            for (int b = n - n % LANES; i < b; i += LANES) {
                FloatVector v = FloatVector.fromArray(S, x, xOff + i);
                FloatVector ha = v.abs().min(9.1f);
                FloatVector hy = ha.mul(2f);
                FloatVector hq = hy.fma(NnConstants.E10, NnConstants.E9);
                hq = hy.fma(hq, E8V);
                hq = hy.fma(hq, E7V);
                hq = hy.fma(hq, E6V);
                hq = hy.fma(hq, E5V);
                hq = hy.fma(hq, E4V);
                hq = hy.fma(hq, E3V);
                hq = hy.fma(hq, E2V);
                FloatVector hsmall = hq.fma(hy.mul(hy), hy);
                FloatVector hxc = hy.max(-104f).min(89f);
                FloatVector hxk = hxc.mul(NnConstants.LOG2E).add(NnConstants.ROUND).sub(NnConstants.ROUND);
                FloatVector hxr = hxk.fma(MLN2_HI, hxc);
                hxr = hxk.fma(MLN2_LO, hxr);
                FloatVector hxp = hxr.fma(NnConstants.E7, NnConstants.E6);
                hxp = hxr.fma(hxp, E5V);
                hxp = hxr.fma(hxp, E4V);
                hxp = hxr.fma(hxp, E3V);
                hxp = hxr.fma(hxp, E2V);
                FloatVector hxt = hxp.fma(hxr.mul(hxr), hxr).add(1f);
                IntVector hxki = (IntVector) hxk.convert(VectorOperators.F2I, 0);
                IntVector hxk1 = hxki.lanewise(VectorOperators.ASHR, 1);
                FloatVector hx = hxt.mul(hxk1.add(127).lanewise(VectorOperators.LSHL, 23).reinterpretAsFloats())
                        .mul(hxki.sub(hxk1).add(127).lanewise(VectorOperators.LSHL, 23).reinterpretAsFloats());
                FloatVector he = hx.sub(1f).blend(hsmall, hy.compare(VectorOperators.LT, 0.5f));
                FloatVector ht = he.div(he.add(2f));
                FloatVector h = ht.reinterpretAsInts().and(0x7fffffff)
                        .or(v.reinterpretAsInts().and(0x80000000)).reinterpretAsFloats();
                h.intoArray(y, yOff + i);
            }
            return i;
        }

        /** The tanh-approximated GELU on whole vectors, with tanh expanded inline. */
        static int geluTanh(float[] x, int xOff, float[] y, int yOff, int n) {
            int i = 0;
            for (int b = n - n % LANES; i < b; i += LANES) {
                FloatVector v = FloatVector.fromArray(S, x, xOff + i);
                FloatVector u = v.mul(GELU_TANH_C).fma(v.mul(v), v).mul(GELU_TANH_K);
                FloatVector ha = u.abs().min(9.1f);
                FloatVector hy = ha.mul(2f);
                FloatVector hq = hy.fma(NnConstants.E10, NnConstants.E9);
                hq = hy.fma(hq, E8V);
                hq = hy.fma(hq, E7V);
                hq = hy.fma(hq, E6V);
                hq = hy.fma(hq, E5V);
                hq = hy.fma(hq, E4V);
                hq = hy.fma(hq, E3V);
                hq = hy.fma(hq, E2V);
                FloatVector hsmall = hq.fma(hy.mul(hy), hy);
                FloatVector hxc = hy.max(-104f).min(89f);
                FloatVector hxk = hxc.mul(NnConstants.LOG2E).add(NnConstants.ROUND).sub(NnConstants.ROUND);
                FloatVector hxr = hxk.fma(MLN2_HI, hxc);
                hxr = hxk.fma(MLN2_LO, hxr);
                FloatVector hxp = hxr.fma(NnConstants.E7, NnConstants.E6);
                hxp = hxr.fma(hxp, E5V);
                hxp = hxr.fma(hxp, E4V);
                hxp = hxr.fma(hxp, E3V);
                hxp = hxr.fma(hxp, E2V);
                FloatVector hxt = hxp.fma(hxr.mul(hxr), hxr).add(1f);
                IntVector hxki = (IntVector) hxk.convert(VectorOperators.F2I, 0);
                IntVector hxk1 = hxki.lanewise(VectorOperators.ASHR, 1);
                FloatVector hx = hxt.mul(hxk1.add(127).lanewise(VectorOperators.LSHL, 23).reinterpretAsFloats())
                        .mul(hxki.sub(hxk1).add(127).lanewise(VectorOperators.LSHL, 23).reinterpretAsFloats());
                FloatVector he = hx.sub(1f).blend(hsmall, hy.compare(VectorOperators.LT, 0.5f));
                FloatVector ht = he.div(he.add(2f));
                FloatVector h = ht.reinterpretAsInts().and(0x7fffffff)
                        .or(u.reinterpretAsInts().and(0x80000000)).reinterpretAsFloats();
                v.mul(0.5f).mul(h.add(1f)).intoArray(y, yOff + i);
            }
            return i;
        }
//#else
        private Simd() {
        }
//#endif
    }

    /**
     * Inverted dropout: keeps element {@code i} when {@code org.dynamisengine.vectrix.hash.Philox.uniform(seed, start + i) >= p} and scales it by
     * {@code 1/(1 - p)}, else writes 0. The same call on the upstream gradient is the backward pass.
     *
     * @param p the drop probability, in [0, 1)
     * @since 2.6.0 (2.5.0 as {@code Philox.dropout})
     */
    public static void dropout(long seed, long start, float p, float[] x, int xOff, float[] y, int yOff, int n) {
        if (!(p >= 0f && p < 1f)) {
            throw new IllegalArgumentException("p must be in [0, 1): " + p);
        }
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        float scale = 1f / (1f - p);
        for (int i = 0; i < n; i++) {
            y[yOff + i] = org.dynamisengine.vectrix.hash.Philox.uniform(seed, start + i) >= p ? x[xOff + i] * scale : 0f;
        }
    }
}
