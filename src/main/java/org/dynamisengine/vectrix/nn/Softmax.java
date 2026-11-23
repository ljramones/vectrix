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

import org.dynamisengine.vectrix.blas.Blasf;
//#ifdef __HAS_VECTOR_API__
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.dynamisengine.vectrix.simd.SimdSupport;
//#endif

/**
 * Softmax, log-softmax and log-sum-exp over one row of floats, forward and backward.
 * <p>
 * The maximum is subtracted before exponentiating, so no input overflows. Sums use {@link Blasf}'s documented
 * order and exponentials {@link NnMath#exp} (vectorized with the same operations), so results are the same bits on
 * every platform and jar. Outputs may alias inputs at the same offset; nothing allocates. Apply a kernel per row to
 * process a matrix: rows are independent, so threads may share the work freely.
 *
 * @since 2.5.0
 */
public final class Softmax {

    private Softmax() {
    }

    /** {@code y[i] = e^(x[i] - m) / Σ e^(x[j] - m)}, {@code m = max x}. */
    public static void softmax(float[] x, int xOff, float[] y, int yOff, int n) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        if (n == 0) {
            return;
        }
        float m = max(x, xOff, n);
        expShifted(x, xOff, y, yOff, n, m);
        float s = Blasf.sum(y, yOff, n);
        for (int i = 0; i < n; i++) {
            y[yOff + i] /= s;
        }
    }

    /** {@code y[i] = x[i] - m - log Σ e^(x[j] - m)}, without forming the probabilities. */
    public static void logSoftmax(float[] x, int xOff, float[] y, int yOff, int n, float[] scratch, int scratchOff) {
        check(x, xOff, n, "x");
        check(y, yOff, n, "y");
        check(scratch, scratchOff, n, "scratch");
        if (n == 0) {
            return;
        }
        float m = max(x, xOff, n);
        expShifted(x, xOff, scratch, scratchOff, n, m);
        float lse = NnMath.log(Blasf.sum(scratch, scratchOff, n));
        for (int i = 0; i < n; i++) {
            y[yOff + i] = (x[xOff + i] - m) - lse;
        }
    }

    /**
     * {@code log Σ e^(x[i])}, stable for any range ({@code -∞} for an empty row).
     *
     * @param scratch {@code n} floats of working memory
     */
    public static float logSumExp(float[] x, int xOff, int n, float[] scratch, int scratchOff) {
        check(x, xOff, n, "x");
        check(scratch, scratchOff, n, "scratch");
        if (n == 0) {
            return Float.NEGATIVE_INFINITY;
        }
        float m = max(x, xOff, n);
        if (m == Float.NEGATIVE_INFINITY || m == Float.POSITIVE_INFINITY) {
            return m;
        }
        expShifted(x, xOff, scratch, scratchOff, n, m);
        return m + NnMath.log(Blasf.sum(scratch, scratchOff, n));
    }

    /** Softmax backward from its output: {@code dx[i] = y[i]·(dy[i] - Σ dy[j]·y[j])}. */
    public static void softmaxBackward(float[] y, int yOff, float[] dy, int dyOff, float[] dx, int dxOff, int n) {
        check(y, yOff, n, "y");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        float d = Blasf.dot(dy, dyOff, y, yOff, n);
        for (int i = 0; i < n; i++) {
            dx[dxOff + i] = y[yOff + i] * (dy[dyOff + i] - d);
        }
    }

    /** Log-softmax backward from its output: {@code dx[i] = dy[i] - e^(y[i])·Σ dy[j]}. */
    public static void logSoftmaxBackward(float[] y, int yOff, float[] dy, int dyOff, float[] dx, int dxOff, int n) {
        check(y, yOff, n, "y");
        check(dy, dyOff, n, "dy");
        check(dx, dxOff, n, "dx");
        float s = Blasf.sum(dy, dyOff, n);
        for (int i = 0; i < n; i++) {
            dx[dxOff + i] = Math.fma(-NnMath.exp(y[yOff + i]), s, dy[dyOff + i]);
        }
    }

    /** The maximum; NaN if any element is NaN (max is exact, so the order does not matter). */
    static float max(float[] x, int off, int n) {
        float m = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            m = Math.max(m, x[off + i]);
        }
        return m;
    }

    /**
     * {@code y[i] = e^(x[i] - m)}, with the subtraction's rounding error {@code err} (TwoSum, exact) applied as
     * {@code e^s·(1 + err)}: a far-below-maximum input then keeps its relative accuracy instead of inheriting the
     * absolute rounding of {@code x - m}.
     */
    static void expShifted(float[] x, int xOff, float[] y, int yOff, int n, float m) {
        int i = 0;
//#ifdef __HAS_VECTOR_API__
        if (Simd.on()) {
            for (int b = n - n % Simd.LANES; i < b; i += Simd.LANES) {
                FloatVector a = FloatVector.fromArray(Simd.S, x, xOff + i);
                FloatVector s = a.sub(m);
                FloatVector bv = s.sub(a);
                FloatVector av = s.sub(bv);
                FloatVector err = a.sub(av).add(bv.neg().sub(m));
                err = err.blend(0f, s.compare(VectorOperators.GE, -104f).not()); // e^s underflows there anyway
                FloatVector e = NnMath.exp(s);
                e.fma(err, e).intoArray(y, yOff + i);
            }
        }
//#endif
        for (; i < n; i++) {
            float a = x[xOff + i];
            float s = a - m;
            float bv = s - a;
            float av = s - bv;
            float err = s >= -104f ? (a - av) + (-bv - m) : 0f; // e^s underflows otherwise; s may be -∞
            float e = NnMath.exp(s);
            y[yOff + i] = Math.fma(e, err, e);
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
