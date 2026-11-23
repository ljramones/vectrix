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
package org.dynamisengine.vectrix.sh;

/**
 * RGB SH coefficients up to order 2 (bands l = 0..2, 9 coefficients per channel), flattened as
 * {@code [c0.r, c0.g, c0.b, c1.r, ... c8.b]}: coefficient {@code i}, channel {@code ch} is at
 * {@code c[i * 3 + ch]}. Coefficient {@code i = l * (l + 1) + m} for band {@code l} and
 * {@code -l <= m <= l}.
 * <p>
 * Not thread-safe; give each thread its own instance.
  * @since 1.0.0
  */
public final class ShCoeffs9f {

    /** Creates coefficients that are all zero. */
    public ShCoeffs9f() {
    }
    /** The number of coefficients per channel: {@code (L + 1)²} for order {@code L = 2}. */
    public static final int COEFFS = 9;

    /** The number of color channels (red, green, blue). */
    public static final int CHANNELS = 3;
    /** The length of {@link #c}: {@code COEFFS * CHANNELS} = 27. */
    public static final int SIZE = COEFFS * CHANNELS;

    /** The coefficients, {@link #SIZE} values interleaved by channel: {@code c[i * CHANNELS + channel]}. */
    public final float[] c = new float[SIZE];

    /**
     * Sets every coefficient to zero.
     *
     * @return this
     */
    public ShCoeffs9f zero() {
        for (int i = 0; i < SIZE; i++) {
            c[i] = 0.0f;
        }
        return this;
    }

    /**
     * Copies all coefficients from {@code other}.
     *
     * @param other the coefficients to copy; may be {@code this}
     * @return this
     */
    public ShCoeffs9f set(ShCoeffs9f other) {
        System.arraycopy(other.c, 0, c, 0, SIZE);
        return this;
    }

    /**
     * Returns one coefficient of one channel.
     *
     * @param band the coefficient index, 0..8 (not the SH band {@code l}; see the class comment)
     * @param channel the channel: 0 red, 1 green, 2 blue
     * @return the value at {@code c[band * CHANNELS + channel]}
     * @throws ArrayIndexOutOfBoundsException if the computed index is outside {@link #c}
     */
    public float get(int band, int channel) {
        return c[band * CHANNELS + channel];
    }

    /**
     * Sets one coefficient of one channel.
     *
     * @param band the coefficient index, 0..8 (not the SH band {@code l}; see the class comment)
     * @param channel the channel: 0 red, 1 green, 2 blue
     * @param value the new value of {@code c[band * CHANNELS + channel]}
     * @return this
     * @throws ArrayIndexOutOfBoundsException if the computed index is outside {@link #c}
     */
    public ShCoeffs9f set(int band, int channel, float value) {
        c[band * CHANNELS + channel] = value;
        return this;
    }
}
