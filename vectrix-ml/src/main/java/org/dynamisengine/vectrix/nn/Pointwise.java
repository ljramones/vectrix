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

import org.dynamisengine.vectrix.core.ReproducibleMath;

/**
 * Element-wise arithmetic over float arrays: the tensor operations a framework composes around the other kernels
 * (residual adds, scaling, masks, exponentials), with reproducible results.
 * <p>
 * Each element is one Java operation: {@code +}, {@code -}, {@code *}, {@code /}, {@code Math.max}/{@code min}
 * (NaN if either is NaN; {@code -0 < +0}), {@code Math.sqrt} (correctly rounded), or {@link ReproducibleMath#exp}
 * and {@link ReproducibleMath#log}, so results are the same bits on every platform. The GPU twin in vectrix-gpu
 * ({@code GpuPointwise}) computes the same bits. Outputs may alias inputs at the same offset; nothing allocates.
 *
 * @since 4.1.0
 */
public final class Pointwise {

    private Pointwise() {
    }

    /** A function of one element. */
    private interface Unary {
        float apply(float x);
    }

    /** A function of two elements. */
    private interface Binary {
        float apply(float a, float b);
    }

    /**
     * {@code y[i] = e^x[i]}, with {@link ReproducibleMath#exp(float)}.
     *
     * @param x the input
     * @param xOff the offset in {@code x}
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the offset in {@code y}
     * @param n the number of elements
     * @throws IllegalArgumentException if {@code n < 0} or an array is too short for its offset
     */
    public static void exp(float[] x, int xOff, float[] y, int yOff, int n) {
        unary(x, xOff, y, yOff, n, ReproducibleMath::exp);
    }

    /**
     * {@code y[i] = ln x[i]}, with {@link ReproducibleMath#log(float)}.
     *
     * @param x the input
     * @param xOff the offset in {@code x}
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the offset in {@code y}
     * @param n the number of elements
     * @throws IllegalArgumentException if {@code n < 0} or an array is too short for its offset
     */
    public static void log(float[] x, int xOff, float[] y, int yOff, int n) {
        unary(x, xOff, y, yOff, n, ReproducibleMath::log);
    }

    /**
     * {@code y[i] = √x[i]}, correctly rounded.
     *
     * @param x the input
     * @param xOff the offset in {@code x}
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the offset in {@code y}
     * @param n the number of elements
     * @throws IllegalArgumentException if {@code n < 0} or an array is too short for its offset
     */
    public static void sqrt(float[] x, int xOff, float[] y, int yOff, int n) {
        unary(x, xOff, y, yOff, n, v -> (float) Math.sqrt(v));
    }

    /**
     * {@code y[i] = -x[i]} (the sign bit flipped, NaN included).
     *
     * @param x the input
     * @param xOff the offset in {@code x}
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the offset in {@code y}
     * @param n the number of elements
     * @throws IllegalArgumentException if {@code n < 0} or an array is too short for its offset
     */
    public static void neg(float[] x, int xOff, float[] y, int yOff, int n) {
        unary(x, xOff, y, yOff, n, v -> -v);
    }

    /**
     * {@code y[i] = x[i]·x[i]}.
     *
     * @param x the input
     * @param xOff the offset in {@code x}
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the offset in {@code y}
     * @param n the number of elements
     * @throws IllegalArgumentException if {@code n < 0} or an array is too short for its offset
     */
    public static void square(float[] x, int xOff, float[] y, int yOff, int n) {
        unary(x, xOff, y, yOff, n, v -> v * v);
    }

    /**
     * {@code y[i] = a[i] + b[i]}.
     *
     * @param a the first input
     * @param aOff the offset in {@code a}
     * @param b the second input
     * @param bOff the offset in {@code b}
     * @param y receives the output; may be {@code a} or {@code b} at the same offset
     * @param yOff the offset in {@code y}
     * @param n the number of elements
     * @throws IllegalArgumentException if {@code n < 0} or an array is too short for its offset
     */
    public static void add(float[] a, int aOff, float[] b, int bOff, float[] y, int yOff, int n) {
        binary(a, aOff, b, bOff, y, yOff, n, (u, v) -> u + v);
    }

    /**
     * {@code y[i] = a[i] - b[i]}.
     *
     * @param a the first input
     * @param aOff the offset in {@code a}
     * @param b the second input
     * @param bOff the offset in {@code b}
     * @param y receives the output; may be {@code a} or {@code b} at the same offset
     * @param yOff the offset in {@code y}
     * @param n the number of elements
     * @throws IllegalArgumentException if {@code n < 0} or an array is too short for its offset
     */
    public static void sub(float[] a, int aOff, float[] b, int bOff, float[] y, int yOff, int n) {
        binary(a, aOff, b, bOff, y, yOff, n, (u, v) -> u - v);
    }

    /**
     * {@code y[i] = a[i]·b[i]}.
     *
     * @param a the first input
     * @param aOff the offset in {@code a}
     * @param b the second input
     * @param bOff the offset in {@code b}
     * @param y receives the output; may be {@code a} or {@code b} at the same offset
     * @param yOff the offset in {@code y}
     * @param n the number of elements
     * @throws IllegalArgumentException if {@code n < 0} or an array is too short for its offset
     */
    public static void mul(float[] a, int aOff, float[] b, int bOff, float[] y, int yOff, int n) {
        binary(a, aOff, b, bOff, y, yOff, n, (u, v) -> u * v);
    }

    /**
     * {@code y[i] = a[i] / b[i]}.
     *
     * @param a the dividend
     * @param aOff the offset in {@code a}
     * @param b the divisor
     * @param bOff the offset in {@code b}
     * @param y receives the output; may be {@code a} or {@code b} at the same offset
     * @param yOff the offset in {@code y}
     * @param n the number of elements
     * @throws IllegalArgumentException if {@code n < 0} or an array is too short for its offset
     */
    public static void div(float[] a, int aOff, float[] b, int bOff, float[] y, int yOff, int n) {
        binary(a, aOff, b, bOff, y, yOff, n, (u, v) -> u / v);
    }

    /**
     * {@code y[i] = Math.max(a[i], b[i])}: NaN if either is NaN, and {@code +0} above {@code -0}.
     *
     * @param a the first input
     * @param aOff the offset in {@code a}
     * @param b the second input
     * @param bOff the offset in {@code b}
     * @param y receives the output; may be {@code a} or {@code b} at the same offset
     * @param yOff the offset in {@code y}
     * @param n the number of elements
     * @throws IllegalArgumentException if {@code n < 0} or an array is too short for its offset
     */
    public static void max(float[] a, int aOff, float[] b, int bOff, float[] y, int yOff, int n) {
        binary(a, aOff, b, bOff, y, yOff, n, Math::max);
    }

    /**
     * {@code y[i] = Math.min(a[i], b[i])}: NaN if either is NaN, and {@code -0} below {@code +0}.
     *
     * @param a the first input
     * @param aOff the offset in {@code a}
     * @param b the second input
     * @param bOff the offset in {@code b}
     * @param y receives the output; may be {@code a} or {@code b} at the same offset
     * @param yOff the offset in {@code y}
     * @param n the number of elements
     * @throws IllegalArgumentException if {@code n < 0} or an array is too short for its offset
     */
    public static void min(float[] a, int aOff, float[] b, int bOff, float[] y, int yOff, int n) {
        binary(a, aOff, b, bOff, y, yOff, n, Math::min);
    }

    private static void unary(float[] x, int xOff, float[] y, int yOff, int n, Unary f) {
        range(x, xOff, n, "x");
        range(y, yOff, n, "y");
        for (int i = 0; i < n; i++) {
            y[yOff + i] = f.apply(x[xOff + i]);
        }
    }

    private static void binary(float[] a, int aOff, float[] b, int bOff, float[] y, int yOff, int n, Binary f) {
        range(a, aOff, n, "a");
        range(b, bOff, n, "b");
        range(y, yOff, n, "y");
        for (int i = 0; i < n; i++) {
            y[yOff + i] = f.apply(a[aOff + i], b[bOff + i]);
        }
    }

    private static void range(float[] a, int off, int n, String name) {
        if (a == null || n < 0 || off < 0 || off > a.length - n) {
            throw new IllegalArgumentException(name + " must hold " + n + " values from offset " + off);
        }
    }
}
