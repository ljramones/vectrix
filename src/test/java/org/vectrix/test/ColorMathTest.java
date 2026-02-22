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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.vectrix.color.ColorMathf;
import org.vectrix.core.Vector3f;

class ColorMathTest {
    @Test
    void srgbLinearRoundTrip() {
        float c = 0.42f;
        float lin = ColorMathf.srgbToLinear(c);
        float s = ColorMathf.linearToSrgb(lin);
        assertEquals(c, s, 1E-5f);
    }

    @Test
    void xyzRoundTrip() {
        Vector3f linear = new Vector3f(0.3f, 0.5f, 0.8f);
        Vector3f xyz = ColorMathf.linearSrgbToXyz(linear, new Vector3f());
        Vector3f back = ColorMathf.xyzToLinearSrgb(xyz, new Vector3f());
        assertEquals(linear.x, back.x, 1E-4f);
        assertEquals(linear.y, back.y, 1E-4f);
        assertEquals(linear.z, back.z, 1E-4f);
    }

    @Test
    void luminanceAndToneMapFinite() {
        float y = ColorMathf.luminanceLinear(1.0f, 1.0f, 1.0f);
        assertEquals(1.0f, y, 1E-6f);
        assertTrue(ColorMathf.reinhardToneMap(10.0f) < 1.0f);
    }
}
