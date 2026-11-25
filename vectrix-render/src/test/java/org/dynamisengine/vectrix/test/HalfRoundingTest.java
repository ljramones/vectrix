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
import static org.junit.jupiter.api.Assertions.fail;

import org.dynamisengine.vectrix.gpu.Half;
import org.junit.jupiter.api.Test;

/**
 * {@link Half#pack(float)} must match IEEE 754 binary16 round-to-nearest-even, using
 * {@link Float#floatToFloat16(float)} as the reference. Before the fix, exact ties in the normal range rounded
 * away from zero (for example 2049 packed to 2050 instead of 2048). NaN payloads are not compared.
 */
class HalfRoundingTest {

    private static void assertMatchesJdk(int bits) {
        float f = Float.intBitsToFloat(bits);
        if (Float.isNaN(f)) {
            return;
        }
        short expected = Float.floatToFloat16(f);
        short actual = Half.pack(f);
        if (expected != actual) {
            fail(String.format("pack(%s) [0x%08X] = 0x%04X, expected 0x%04X", f, bits, actual & 0xFFFF, expected & 0xFFFF));
        }
    }

    @Test
    void documentedTieExample() {
        assertEquals(2048.0f, Half.unpack(Half.pack(2049.0f)));
        assertEquals(2052.0f, Half.unpack(Half.pack(2051.0f)));
        assertEquals(-2048.0f, Half.unpack(Half.pack(-2049.0f)));
    }

    @Test
    void everyExactTieInTheNormalRange() {
        // A tie has the 13 discarded mantissa bits equal to 0x1000. Enumerate every kept mantissa for every
        // float exponent that maps to a normal half (including the one that overflows to infinity).
        for (int sign = 0; sign <= 1; sign++) {
            for (int exp = 113; exp <= 142; exp++) {
                for (int kept = 0; kept < 1024; kept++) {
                    assertMatchesJdk((sign << 31) | (exp << 23) | (kept << 13) | 0x1000);
                }
            }
        }
    }

    @Test
    void allMantissasForBoundaryExponents() {
        // Exhaustive over the mantissa for exponents covering: half subnormals, the subnormal/normal boundary,
        // 1.0, the largest normal half and overflow to infinity.
        int[] exponents = {102, 103, 112, 113, 114, 127, 142, 143};
        for (int exp : exponents) {
            for (int sign = 0; sign <= 1; sign++) {
                int base = (sign << 31) | (exp << 23);
                for (int mant = 0; mant < (1 << 23); mant++) {
                    assertMatchesJdk(base | mant);
                }
            }
        }
    }

    @Test
    void specialValues() {
        int[] specials = {
            0x00000000, 0x80000000, // +-0
            0x7F800000, 0xFF800000, // +-Inf
            0x7F7FFFFF, 0xFF7FFFFF, // +-Float.MAX_VALUE -> +-Inf
            0x00000001, 0x80000001, // float subnormals -> +-0
            0x477FE000, 0x477FF000, // 65504 (max half), 65520 (tie -> Inf)
            0x33000000, 0x33000001, // 2^-25 (tie with 0 -> 0), just above -> smallest subnormal
        };
        for (int bits : specials) {
            assertMatchesJdk(bits);
        }
    }

    @Test
    void strideSampleOfAllFloats() {
        // Deterministic sweep over the whole 32-bit space (every 4099th pattern, a prime stride).
        for (long bits = 0; bits <= 0xFFFFFFFFL; bits += 4099) {
            assertMatchesJdk((int) bits);
        }
    }
}
