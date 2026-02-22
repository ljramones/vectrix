/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.vectrix.core.Ranged;
import org.vectrix.core.Rangef;

class RangeTest {
    @Test
    void floatRangeUtilitiesWork() {
        assertEquals(0.5f, Rangef.inverseLerp(2.0f, 6.0f, 4.0f), 1E-7f);
        assertEquals(15.0f, Rangef.remap(0.5f, 0.0f, 1.0f, 10.0f, 20.0f), 1E-7f);
        assertEquals(10.0f, Rangef.remapClamped(-0.5f, 0.0f, 1.0f, 10.0f, 20.0f), 1E-7f);
        assertEquals(20.0f, Rangef.remapClamped(1.5f, 0.0f, 1.0f, 10.0f, 20.0f), 1E-7f);
        assertEquals(0.0f, Rangef.saturate(-2.0f), 1E-7f);
        assertEquals(1.0f, Rangef.saturate(2.0f), 1E-7f);
        assertEquals(3.0f, Rangef.clamp(5.0f, -1.0f, 3.0f), 1E-7f);
    }

    @Test
    void doubleRangeUtilitiesWork() {
        assertEquals(0.5, Ranged.inverseLerp(2.0, 6.0, 4.0), 1E-12);
        assertEquals(15.0, Ranged.remap(0.5, 0.0, 1.0, 10.0, 20.0), 1E-12);
        assertEquals(10.0, Ranged.remapClamped(-0.5, 0.0, 1.0, 10.0, 20.0), 1E-12);
        assertEquals(20.0, Ranged.remapClamped(1.5, 0.0, 1.0, 10.0, 20.0), 1E-12);
        assertEquals(0.0, Ranged.saturate(-2.0), 1E-12);
        assertEquals(1.0, Ranged.saturate(2.0), 1E-12);
        assertEquals(3.0, Ranged.clamp(5.0, -1.0, 3.0), 1E-12);
    }

    @Test
    void invalidRangesThrow() {
        assertThrows(IllegalArgumentException.class, () -> Rangef.inverseLerp(1.0f, 1.0f, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> Ranged.inverseLerp(1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Rangef.clamp(0.0f, 2.0f, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> Ranged.clamp(0.0, 2.0, 1.0));
    }
}
