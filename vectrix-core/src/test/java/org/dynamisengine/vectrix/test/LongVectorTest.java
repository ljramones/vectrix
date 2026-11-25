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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

import org.dynamisengine.vectrix.core.Math;
import org.dynamisengine.vectrix.core.RoundingMode;
import org.dynamisengine.vectrix.core.Vector2L;
import org.dynamisengine.vectrix.core.Vector3L;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector4L;
import org.dynamisengine.vectrix.core.Vector4d;
import org.junit.jupiter.api.Test;

/**
 * The long vectors must keep 64-bit integer semantics beyond the int range they exist to cover (review
 * finding H-6). Before the fix, rounding constructors and {@code set(Vector3dc)} clamped to
 * {@code Integer.MAX_VALUE}, {@code div(float)} truncated to int with a float quotient, {@code length()} and
 * {@code distance()} overflowed to NaN above about 3.04e9 and had float precision, and {@code Vector3L}'s
 * min/max component compared through float. This class uses only API that existed before the fix.
 */
class LongVectorTest {

    private static final long FIVE_BILLION = 5_000_000_000L;

    /** Exact Euclidean length via BigInteger, rounded to the nearest double. */
    private static double exactLength(long... c) {
        BigInteger sum = BigInteger.ZERO;
        for (long v : c) {
            sum = sum.add(BigInteger.valueOf(v).multiply(BigInteger.valueOf(v)));
        }
        return new BigDecimal(sum).sqrt(MathContext.DECIMAL128).doubleValue();
    }

    @Test
    void roundingModesKeepLongRange() {
        for (int mode : new int[] {RoundingMode.TRUNCATE, RoundingMode.FLOOR, RoundingMode.CEILING,
                RoundingMode.HALF_UP, RoundingMode.HALF_DOWN, RoundingMode.HALF_EVEN}) {
            assertEquals(FIVE_BILLION, new Vector3L(5e9, 0, 0, mode).x, "mode " + mode);
            assertEquals(FIVE_BILLION, new Vector2L(5e9, 0, mode).x, "mode " + mode);
            assertEquals(-FIVE_BILLION, new Vector4L(new Vector4d(-5e9, 0, 0, 0), mode).x, "mode " + mode);
        }
        assertEquals(6_000_000_000L, new Vector3L(5_999_999_999.5, 0, 0, RoundingMode.HALF_UP).x);
        assertEquals(5_999_999_999L, new Vector3L(5_999_999_999.5, 0, 0, RoundingMode.HALF_DOWN).x);
        assertEquals(6_000_000_000L, new Vector3L(5_999_999_999.5, 0, 0, RoundingMode.HALF_EVEN).x);
    }

    @Test
    void halfRoundingIsExact() {
        assertEquals(0, Math.roundHalfUp(0.49999999999999994));
        assertEquals(0, Math.roundHalfDown(0.49999999999999994));
        long big = (1L << 52) + 1;
        assertEquals(big, Math.roundLongHalfUp((double) big));
        assertEquals(big, Math.roundLongHalfDown((double) big));
        assertEquals(3, Math.roundHalfUp(2.5));
        assertEquals(2, Math.roundHalfDown(2.5));
        assertEquals(2, Math.roundHalfEven(2.5));
        assertEquals(-3, Math.roundHalfUp(-2.5));
        assertEquals(-2, Math.roundHalfDown(-2.5));
        assertEquals(-2, Math.roundHalfEven(-2.5));
        assertEquals(3, Math.roundHalfUp(2.5f));
        assertEquals(-1, Math.roundHalfDown(-1.5f));
    }

    @Test
    void setFromDoubleVectorsDoesNotClampToInt() {
        assertEquals(FIVE_BILLION, new Vector3L().set(new Vector3d(5e9, 1, 2)).x);
        assertEquals(-FIVE_BILLION, new Vector4L().set(new Vector4d(0, 0, 0, -5e9)).w);
    }

    @Test
    void divisionKeepsLongPrecision() {
        long p = (1L << 24) + 1;
        assertEquals(p, new Vector2L(p, 0).div(1.0f).x);
        assertEquals(FIVE_BILLION / 2, new Vector3L().set(FIVE_BILLION, 0, 0).div(2.0f).x);
        assertEquals(-FIVE_BILLION, new Vector4L(FIVE_BILLION, 0, 0, 0).div(-1.0f).x);
    }

    @Test
    void lengthAndDistanceAreFiniteAndDoublePrecise() {
        long[][] cases = {
            {4_000_000_000L, 0, 0}, {100_000_001L, 0, 0}, {3_100_000_000L, -2_900_000_000L, 1},
            {1L << 40, (1L << 40) + 1, 7}, {Integer.MAX_VALUE, Integer.MIN_VALUE, 0},
        };
        for (long[] c : cases) {
            double exact = exactLength(c);
            Vector3L v = new Vector3L().set(c[0], c[1], c[2]);
            assertEquals(exact, v.length(), java.lang.Math.ulp(exact) * 2, "Vector3L.length " + v);
            assertEquals(exact, Vector3L.length(c[0], c[1], c[2]), java.lang.Math.ulp(exact) * 2);
            assertEquals(exact, v.distance(new Vector3L()), java.lang.Math.ulp(exact) * 2);
            assertEquals(exact, Vector3L.distance(c[0], c[1], c[2], 0, 0, 0), java.lang.Math.ulp(exact) * 2);
            double exact2 = exactLength(c[0], c[1]);
            assertEquals(exact2, new Vector2L(c[0], c[1]).length(), java.lang.Math.ulp(exact2) * 2);
            assertEquals(exact2, new Vector2L(c[0], c[1]).distance(0, 0), java.lang.Math.ulp(exact2) * 2);
            double exact4 = exactLength(c[0], c[1], c[2], c[0]);
            Vector4L w = new Vector4L(c[0], c[1], c[2], c[0]);
            assertEquals(exact4, w.length(), java.lang.Math.ulp(exact4) * 2);
            assertEquals(exact4, w.distance(0, 0, 0, 0), java.lang.Math.ulp(exact4) * 2);
        }
        // exact difference before squaring: 2^60 + 1 vs 2^60 is distance 1
        assertEquals(1.0, new Vector3L().set((1L << 60) + 1, 0, 0).distance(new Vector3L().set(1L << 60, 0, 0)));
        assertEquals(16777217.0, new Vector4L((1L << 24) + 1, 0, 0, 0).distance(0, 0, 0, 0));
    }

    @Test
    void minMaxComponentCompareInLong() {
        Vector3L v = new Vector3L().set(1L << 40, (1L << 40) + 1, 0);
        assertEquals(1, v.maxComponent());
        assertEquals(2, v.minComponent());
        Vector3L u = new Vector3L().set((1L << 40) + 1, 1L << 40, (1L << 40) + 2);
        assertEquals(2, u.maxComponent());
        assertEquals(1, u.minComponent());
    }

    @Test
    void toStringIsExact() {
        assertEquals("(78187493530 -1 0)", new Vector3L().set(78_187_493_530L, -1, 0).toString());
        assertEquals("(9223372036854775807 -9223372036854775808)", new Vector2L(Long.MAX_VALUE, Long.MIN_VALUE).toString());
        assertEquals("(1 2 3 4)", new Vector4L(1, 2, 3, 4).toString());
    }

    @Test
    void arithmeticAndEqualityAtLargeMagnitudes() {
        Vector3L a = new Vector3L().set(FIVE_BILLION, -FIVE_BILLION, 1L << 50);
        Vector3L b = new Vector3L().set(1, 2, 3);
        Vector3L sum = new Vector3L(a).add(b);
        assertEquals(FIVE_BILLION + 1, sum.x);
        assertEquals(-FIVE_BILLION + 2, sum.y);
        assertEquals((1L << 50) + 3, sum.z);
        assertEquals(a, new Vector3L(sum).sub(b));
        assertEquals(a.hashCode(), new Vector3L(sum).sub(b).hashCode());
        assertNotEquals(a, sum);
        assertEquals(new Vector3L().set(2 * FIVE_BILLION, -2 * FIVE_BILLION, 1L << 51), new Vector3L(a).mul(2));
    }
}
