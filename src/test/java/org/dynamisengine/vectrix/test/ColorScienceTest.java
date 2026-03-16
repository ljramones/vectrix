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
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.dynamisengine.vectrix.color.ColorSciencef;
import org.dynamisengine.vectrix.core.Vector3f;

class ColorScienceTest {

    @Test
    void kelvinWarmColorAt1800K() {
        Vector3f rgb = ColorSciencef.kelvinToLinearRgb(1800f, new Vector3f());
        assertTrue(rgb.x > rgb.z, "1800 K should be warm (red > blue)");
    }

    @Test
    void kelvinNearNeutralAt5500K() {
        Vector3f rgb = ColorSciencef.kelvinToLinearRgb(5500f, new Vector3f());
        assertTrue(Math.abs(rgb.x - rgb.y) < 0.25f, "5500 K should be near-neutral");
    }

    @Test
    void kelvinCoolAt6500K() {
        Vector3f cool = ColorSciencef.kelvinToLinearRgb(6500f, new Vector3f());
        Vector3f warm = ColorSciencef.kelvinToLinearRgb(1800f, new Vector3f());
        assertTrue(cool.z > warm.z, "6500 K should have more blue than 1800 K");
    }

    @Test
    void kelvinComponentsInUnitRange() {
        Vector3f rgb = ColorSciencef.kelvinToLinearRgb(3200f, new Vector3f());
        assertTrue(rgb.x >= 0f && rgb.x <= 1f);
        assertTrue(rgb.y >= 0f && rgb.y <= 1f);
        assertTrue(rgb.z >= 0f && rgb.z <= 1f);
    }

    @Test
    void kelvinWritesIntoDest() {
        Vector3f dest = new Vector3f();
        Vector3f returned = ColorSciencef.kelvinToLinearRgb(4000f, dest);
        assertTrue(dest == returned, "must return the dest object");
    }

    @Test
    void kelvinRejectsNullDest() {
        assertThrows(IllegalArgumentException.class,
                () -> ColorSciencef.kelvinToLinearRgb(5000f, null));
    }

    @Test
    void kelvinRejectsNonPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> ColorSciencef.kelvinToLinearRgb(0f, new Vector3f()));
        assertThrows(IllegalArgumentException.class,
                () -> ColorSciencef.kelvinToLinearRgb(-100f, new Vector3f()));
    }
}
