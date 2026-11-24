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

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Bit-for-bit comparison with Vectrix. Any NaN equals any NaN. On hardware, a zero of either sign where Vectrix has
 * a subnormal is accepted (flush to zero); nothing else is.
 */
final class Bits {

    private Bits() {
    }

    static boolean subnormal(float v) {
        return v != 0 && Math.abs(v) < Float.MIN_NORMAL;
    }

    static boolean same(float expected, float actual, boolean hardware) {
        if (Float.floatToIntBits(expected) == Float.floatToIntBits(actual)) {
            return true;
        }
        return hardware && subnormal(expected) && actual == 0;
    }

    /** Fails unless {@code actual} matches {@code expected} at every index. */
    static void assertSame(float[] expected, float[] actual, boolean hardware, String what) {
        int bad = 0;
        StringBuilder first = new StringBuilder();
        for (int i = 0; i < expected.length; i++) {
            if (!same(expected[i], actual[i], hardware)) {
                if (bad++ < 5) {
                    first.append(String.format("%n  [%d] expected %s (0x%08x), got %s (0x%08x)", i, expected[i],
                            Float.floatToRawIntBits(expected[i]), actual[i], Float.floatToRawIntBits(actual[i])));
                }
            }
        }
        if (bad > 0) {
            fail(what + ": " + bad + " of " + expected.length + " values differ from Vectrix" + first);
        }
    }

    /** Fails unless {@code actual} equals {@code expected} at every index. */
    static void assertSame(int[] expected, int[] actual, String what) {
        int bad = 0;
        StringBuilder first = new StringBuilder();
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i] && bad++ < 5) {
                first.append(String.format("%n  [%d] expected 0x%08x, got 0x%08x", i, expected[i], actual[i]));
            }
        }
        if (bad > 0) {
            fail(what + ": " + bad + " of " + expected.length + " values differ from Vectrix" + first);
        }
    }
}
