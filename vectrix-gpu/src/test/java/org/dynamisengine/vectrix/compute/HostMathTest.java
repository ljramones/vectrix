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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.ReproducibleMath;
import org.junit.jupiter.api.Test;

/** {@link HostMath} returns the bits of the {@code ReproducibleMath} functions it copies. */
class HostMathTest {

    private static final double[] SPECIALS = {0.0, -0.0, 1.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY, Double.MIN_VALUE, Double.MIN_NORMAL, Math.nextDown(Double.MIN_NORMAL),
        Double.MAX_VALUE, 0.9, 0.999, 0.99999, 1e-300, 709.7, 709.8, 710.0, -745.0, -745.2, -746.0, -708.4};

    private static void same(double expected, double actual, String what) {
        assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual), what);
    }

    @Test
    void logAndExpMatch() {
        for (double x : SPECIALS) {
            same(ReproducibleMath.log(x), HostMath.log(x), "log " + x);
            same(ReproducibleMath.exp(x), HostMath.exp(x), "exp " + x);
            same(ReproducibleMath.exp(-x), HostMath.exp(-x), "exp " + -x);
        }
        SplittableRandom r = new SplittableRandom(5);
        for (int i = 0; i < 200_000; i++) {
            double x = Double.longBitsToDouble(r.nextLong() & Long.MAX_VALUE);
            same(ReproducibleMath.log(x), HostMath.log(x), "log " + x);
            double e = r.nextDouble() * 1500 - 750;
            same(ReproducibleMath.exp(e), HostMath.exp(e), "exp " + e);
        }
    }

    /** As {@code Optim.adamW} calls it: a float base and a long step. */
    @Test
    void biasCorrectionsMatch() {
        float[] betas = {0f, 0.5f, 0.9f, 0.95f, 0.99f, 0.999f, 0.9999f, 0.99999f, Math.nextDown(1f), 1f,
            Float.MIN_NORMAL, Float.NaN};
        long[] steps = {1, 2, 3, 10, 100, 1000, 12_345, 1_000_000, 1L << 40, Long.MAX_VALUE};
        for (float b : betas) {
            for (long t : steps) {
                same(ReproducibleMath.pow(b, t), HostMath.pow(b, t), "pow " + b + " " + t);
            }
        }
        SplittableRandom r = new SplittableRandom(9);
        for (int i = 0; i < 200_000; i++) {
            float b = (float) r.nextDouble();
            long t = 1 + r.nextLong(r.nextBoolean() ? 100_000 : Long.MAX_VALUE);
            same(ReproducibleMath.pow(b, t), HostMath.pow(b, t), "pow " + b + " " + t);
            double x = r.nextDouble() * 10;
            double y = r.nextDouble() * 200 - 100;
            same(ReproducibleMath.pow(x, y), HostMath.pow(x, y), "pow " + x + " " + y);
        }
    }
}
