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
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.ReproducibleMath;
import org.junit.jupiter.api.Test;

/** {@link ReproducibleMath}'s double exponential and power over their whole range, including the edges. */
class ReproducibleMathTest {

    private static void within1Ulp(double expected, double actual, String what) {
        if (Double.isInfinite(expected) || expected == 0) {
            assertEquals(expected, actual, what);
            return;
        }
        assertTrue(Math.abs(actual - expected) <= Math.ulp(expected), what + ": " + actual + " vs " + expected);
    }

    @Test
    void expMatchesStrictMathEverywhere() {
        SplittableRandom rnd = new SplittableRandom(3);
        for (int i = 0; i < 200_000; i++) {
            double x = rnd.nextDouble(-750, 712);
            within1Ulp(StrictMath.exp(x), ReproducibleMath.exp(x), "exp(" + x + ")");
        }
        for (double x : new double[] {709.78, 709.782712893384, 709.7827128933841, 709.79, -708.4, -744.4,
            -745.1332191019411, -745.14, 0, -0.0, 1e-300, -1e-300}) {
            within1Ulp(StrictMath.exp(x), ReproducibleMath.exp(x), "exp(" + x + ")");
        }
    }

    @Test
    void expEdges() {
        assertEquals(Double.POSITIVE_INFINITY, ReproducibleMath.exp(Double.POSITIVE_INFINITY));
        assertEquals(0.0, ReproducibleMath.exp(Double.NEGATIVE_INFINITY));
        assertEquals(0.0, ReproducibleMath.exp(-1e300));
        assertEquals(Double.POSITIVE_INFINITY, ReproducibleMath.exp(1e300));
        assertTrue(Double.isNaN(ReproducibleMath.exp(Double.NaN)));
        assertEquals(1.0, ReproducibleMath.exp(0.0));
    }

    @Test
    void powEdges() {
        assertEquals(0.0, ReproducibleMath.pow(0.0, 2.0));
        assertEquals(Double.POSITIVE_INFINITY, ReproducibleMath.pow(0.0, -1.0));
        within1Ulp(1024.0, ReproducibleMath.pow(2.0, 10.0), "2^10");
        // documented: the rounding of y·log b costs about |y·log b| ulps, 2e-15 relative here
        assertEquals(1e-8, ReproducibleMath.pow(10000, -2), 2e-15 * 1e-8);
    }
}
