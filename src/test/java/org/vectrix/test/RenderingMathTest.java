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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.vectrix.renderingmath.AtmosphereParams;
import org.vectrix.renderingmath.SssLutBuilder;
import org.vectrix.renderingmath.SssProfile;
import org.vectrix.renderingmath.TransmittanceLutBuilder;

class RenderingMathTest {
    @Test
    void sssLutBuildsFiniteValuesAndRespondsToNdotL() {
        SssProfile profile = new SssProfile(
            new float[] {0.3f, 0.45f, 0.6f, 0.2f, 0.15f, 0.1f},
            new float[] {0.02f, 0.03f, 0.04f, 0.2f, 0.25f, 0.35f});
        int w = 32, h = 32;
        float[] lut = new float[w * h * 3];
        SssLutBuilder.build(w, h, profile, 2.0f, 5.0f, 128, lut);

        for (float v : lut) {
            assertFalse(Float.isNaN(v) || Float.isInfinite(v));
            assertTrue(v >= 0.0f);
        }

        int row = h / 2;
        int lowNoL = (row * w + 1) * 3;
        int highNoL = (row * w + (w - 2)) * 3;
        assertTrue(lut[highNoL] >= lut[lowNoL]);
        assertTrue(lut[highNoL + 1] >= lut[lowNoL + 1]);
        assertTrue(lut[highNoL + 2] >= lut[lowNoL + 2]);
    }

    @Test
    void atmosphereLutIsBoundedAndHigherAltitudeIsMoreTransparent() {
        AtmosphereParams params = new AtmosphereParams(
            6360.0f, 6460.0f,
            8.0f, 1.2f,
            0.0058f, 0.0135f, 0.0331f,
            0.0030f, 0.0030f, 0.0030f);
        int w = 48, h = 32;
        float[] lut = new float[w * h * 3];
        TransmittanceLutBuilder.build(w, h, params, 128, lut);

        for (float v : lut) {
            assertFalse(Float.isNaN(v) || Float.isInfinite(v));
            assertTrue(v >= 0.0f && v <= 1.0f);
        }

        int muUp = (int) (0.9f * (w - 1)); // mostly upward view
        int ground = (0 * w + muUp) * 3;
        int high = ((h - 1) * w + muUp) * 3;
        assertTrue(lut[high] >= lut[ground]);
        assertTrue(lut[high + 1] >= lut[ground + 1]);
        assertTrue(lut[high + 2] >= lut[ground + 2]);
    }
}
