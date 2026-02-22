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
import org.vectrix.core.Vector3f;
import org.vectrix.sdf.Sdf3f;

class SdfTest {
    @Test
    void sphereSignConvention() {
        assertEquals(-1.0f, Sdf3f.sphere(new Vector3f(0, 0, 0), 1.0f), 1E-6f);
        assertEquals(0.0f, Sdf3f.sphere(new Vector3f(1, 0, 0), 1.0f), 1E-6f);
    }

    @Test
    void boxAndTorusFinite() {
        float dBox = Sdf3f.box(new Vector3f(2, 0, 0), new Vector3f(1, 1, 1));
        float dTorus = Sdf3f.torusY(new Vector3f(1, 0, 0), 1.0f, 0.25f);
        assertTrue(Float.isFinite(dBox));
        assertTrue(Float.isFinite(dTorus));
    }
}
