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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.dynamisengine.vectrix.core.Vector2L;
import org.dynamisengine.vectrix.core.Vector2i;
import org.dynamisengine.vectrix.core.Vector3L;
import org.dynamisengine.vectrix.core.Vector3i;
import org.dynamisengine.vectrix.core.Vector4i;
import org.junit.jupiter.api.Test;

/**
 * Squared lengths and distances of the int and long vectors return a long. They used to wrap silently on overflow
 * (a negative "squared distance"); they now compute exactly and throw {@link ArithmeticException} when the result
 * does not fit. {@code gridDistance} on the int vectors summed int differences and overflowed too.
 */
class IntegerVectorOverflowTest {

    @Test
    void exactResultsAreUnchanged() {
        assertEquals(14, new Vector3i(1, 2, 3).lengthSquared());
        assertEquals(27, new Vector3i(1, 2, 3).distanceSquared(4, 5, 6));
        assertEquals(9, new Vector3i(1, 2, 3).gridDistance(4, 5, 6));
        assertEquals(9_000_000_000_000_000_000L, new Vector2L(3_000_000_000L, 0).lengthSquared()); // fits: < 2^63
    }

    @Test
    void overflowThrowsInsteadOfWrapping() {
        // (2^32 - 1)^2 overflows a long: it used to come back negative
        assertThrows(ArithmeticException.class,
                () -> new Vector2i(Integer.MAX_VALUE, 0).distanceSquared(Integer.MIN_VALUE, 0));
        assertThrows(ArithmeticException.class,
                () -> new Vector4i(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE).lengthSquared());
        assertThrows(ArithmeticException.class, () -> new Vector3L().set(4_000_000_000L, 0, 0).lengthSquared());
        assertThrows(ArithmeticException.class, () -> new Vector3L().set(Long.MAX_VALUE, 0, 0).distanceSquared(-1, 0, 0));
        assertThrows(ArithmeticException.class, () -> new Vector2L(Long.MAX_VALUE, 0).gridDistance(Long.MIN_VALUE, 0));
    }

    @Test
    void intGridDistanceDoesNotOverflow() {
        // the int difference wrapped to -1 and the result was 1
        assertEquals((long) Integer.MAX_VALUE - Integer.MIN_VALUE, new Vector2i(Integer.MAX_VALUE, 0).gridDistance(Integer.MIN_VALUE, 0));
        assertEquals(3L * ((long) Integer.MAX_VALUE - Integer.MIN_VALUE),
                new Vector3i(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
                        .gridDistance(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE));
    }
}
