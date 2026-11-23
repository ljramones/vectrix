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

//#ifdef __HAS_VECTOR_API__
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import org.dynamisengine.vectrix.simd.SimdSupport;
//#endif

/**
 * Element-wise activation functions over float arrays, forward and backward.
 * <p>
 * Forward kernels write {@code y[i] = f(x[i])}; backward kernels write {@code dx[i] = dy[i]·f'(x[i])} from the
 * forward input. Outputs may alias their inputs at the same offset. The transcendental parts are {@link NnMath}'s,
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
            for (int b = n - n % Simd.LANES; i < b; i += Simd.LANES) {
                FloatVector v = FloatVector.fromArray(Simd.S, x, xOff + i);
                FloatVector e = NnMath.exp(v.neg());
                FloatVector.broadcast(Simd.S, 1f).div(e.add(1f)).intoArray(y, yOff + i);
            }
        }
//#endif
        for (; i < n; i++) {
            y[yOff + i] = NnMath.sigmoid(x[xOff + i]);
        }
    }

    /** SiLU (swish): {@code x·sigmoid(x)}. */
    public static void silu(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        int i = 0;
//#ifdef __HAS_VECTOR_API__
        if (Simd.on()) {
            for (int b = n - n % Simd.LANES; i < b; i += Simd.LANES) {
                FloatVector v = FloatVector.fromArray(Simd.S, x, xOff + i);
                FloatVector s = FloatVector.broadcast(Simd.S, 1f).div(NnMath.exp(v.neg()).add(1f));
                v.mul(s).intoArray(y, yOff + i);
            }
        }
//#endif
        for (; i < n; i++) {
            float v = x[xOff + i];
            y[yOff + i] = v * NnMath.sigmoid(v);
        }
    }

    /** SiLU backward: {@code dy·(s + x·s·(1 - s))}, {@code s = sigmoid(x)}. */
    public static void siluBackward(float[] x, int xOff, float[] dy, int dyOff, float[] dx, int dxOff, int n) {
        check(x, xOff, n, "x");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        for (int i = 0; i < n; i++) {
            float v = x[xOff + i];
            float s = NnMath.sigmoid(v);
            dx[dxOff + i] = dy[dyOff + i] * Math.fma(v * s, 1f - s, s);
        }
    }

    /** {@code tanh x}. */
    public static void tanh(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        for (int i = 0; i < n; i++) {
            y[yOff + i] = NnMath.tanh(x[xOff + i]);
        }
    }

    /**
     * GELU, exact form: {@code x·Φ(x)} with {@link NnMath#normalCdf}, which keeps its relative accuracy for negative
     * {@code x}, where {@code (1 + erf(x/√2))/2} would cancel.
     */
    public static void gelu(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        for (int i = 0; i < n; i++) {
            float v = x[xOff + i];
            y[yOff + i] = v * NnMath.normalCdf(v);
        }
    }

    /** GELU backward: {@code dy·(Φ(x) + x·φ(x))}, {@code φ} the standard normal density. */
    public static void geluBackward(float[] x, int xOff, float[] dy, int dyOff, float[] dx, int dxOff, int n) {
        check(x, xOff, n, "x");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        for (int i = 0; i < n; i++) {
            float v = x[xOff + i];
            float cdf = NnMath.normalCdf(v);
            float pdf = NnMath.normalPdf(v);
            dx[dxOff + i] = dy[dyOff + i] * Math.fma(v, pdf, cdf);
        }
    }

    /** GELU, tanh approximation: {@code x·(1 + tanh(√(2/π)·(x + 0.044715·x³)))/2} (as in GPT-2). */
    public static void geluTanh(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        for (int i = 0; i < n; i++) {
            float v = x[xOff + i];
            float u = GELU_TANH_K * Math.fma(GELU_TANH_C * v, v * v, v);
            y[yOff + i] = 0.5f * v * (1f + NnMath.tanh(u));
        }
    }

    /** Softplus {@code log(1 + e^x)}, computed as {@code max(x, 0) + log1p(e^-|x|)} without overflow. */
    public static void softplus(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        for (int i = 0; i < n; i++) {
            float v = x[xOff + i];
            y[yOff + i] = Math.max(v, 0f) + NnMath.log1p(NnMath.exp(-Math.abs(v)));
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
//#else
        private Simd() {
        }
//#endif
    }
}
