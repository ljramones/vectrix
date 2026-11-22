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
import org.dynamisengine.vectrix.color.ColorMathd;
import org.dynamisengine.vectrix.color.ColorScienced;
import org.dynamisengine.vectrix.color.ColorSciencef;
import org.dynamisengine.vectrix.core.Vector3d;
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

    @Test
    void kelvinIsSmoothAcrossTheWholeRange() {
        // the Helland fit used before jumped by 0.03 at 6600 K; the Planckian spline's joins move < 1e-3
        Vector3d prev = ColorScienced.kelvinToLinearRgb(1000.0, new Vector3d());
        Vector3d cur = new Vector3d();
        for (int t = 1001; t <= 26000; t++) {
            ColorScienced.kelvinToLinearRgb(t, cur);
            double step = java.lang.Math.max(java.lang.Math.abs(cur.x - prev.x),
                    java.lang.Math.max(java.lang.Math.abs(cur.y - prev.y), java.lang.Math.abs(cur.z - prev.z)));
            assertTrue(step < 1e-3, "step of " + step + " at " + t + " K");
            assertTrue(cur.x >= 0 && cur.y >= 0 && cur.z >= 0 && cur.x <= 1 && cur.y <= 1 && cur.z <= 1, t + " K");
            assertEquals(1.0, java.lang.Math.max(cur.x, java.lang.Math.max(cur.y, cur.z)), 1e-12, t + " K");
            prev.set(cur);
        }
    }

    @Test
    void kelvinFollowsThePlanckianLocus() {
        // reference: Planck's law integrated against the CIE 1931 colour-matching functions
        Vector3d bar = new Vector3d();
        for (int t = 2700; t <= 25000; t += 700) {
            double x = 0, y = 0, z = 0;
            for (double wl = 360; wl <= 830; wl += 0.5) {
                double l = wl * 1e-9;
                double b = 1.0 / (java.lang.StrictMath.pow(l, 5) * (java.lang.StrictMath.exp(1.4388e-2 / (l * t)) - 1));
                ColorScienced.cie1931XyzBar(wl, bar);
                x += b * bar.x;
                y += b * bar.y;
                z += b * bar.z;
            }
            Vector3d xyz = ColorMathd.linearSrgbToXyz(ColorScienced.kelvinToLinearRgb(t, new Vector3d()), new Vector3d());
            double sum = xyz.x + xyz.y + xyz.z;
            assertEquals(x / (x + y + z), xyz.x / sum, 2e-3, "x at " + t + " K");
            assertEquals(y / (x + y + z), xyz.y / sum, 2e-3, "y at " + t + " K");
        }
    }
}
