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
package org.vectrix.sh;

/**
 * RGB SH coefficients for L0..L2 (9 bands), flattened as [c0.r, c0.g, c0.b, c1.r, ... c8.b].
 */
public final class ShCoeffs9f {
    public static final int BANDS = 9;
    public static final int CHANNELS = 3;
    public static final int SIZE = BANDS * CHANNELS;

    public final float[] c = new float[SIZE];

    public ShCoeffs9f zero() {
        for (int i = 0; i < SIZE; i++) {
            c[i] = 0.0f;
        }
        return this;
    }

    public ShCoeffs9f set(ShCoeffs9f other) {
        System.arraycopy(other.c, 0, c, 0, SIZE);
        return this;
    }

    public float get(int band, int channel) {
        return c[band * CHANNELS + channel];
    }

    public ShCoeffs9f set(int band, int channel, float value) {
        c[band * CHANNELS + channel] = value;
        return this;
    }
}
