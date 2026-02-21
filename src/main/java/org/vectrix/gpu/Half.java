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
package org.vectrix.gpu;

/**
 * IEEE 754 binary16 (half-float) encode/decode helpers.
 */
public final class Half {
    private Half() {
    }

    /**
     * Convert a float to half-float bit representation.
     *
     * @param value the float value
     * @return half-float bits in a short
     */
    public static short pack(float value) {
        int bits = Float.floatToRawIntBits(value);
        int sign = (bits >>> 16) & 0x8000;
        int exp = (bits >>> 23) & 0xFF;
        int mant = bits & 0x7FFFFF;

        if (exp == 0xFF) {
            if (mant == 0) {
                return (short) (sign | 0x7C00);
            }
            int payload = mant >>> 13;
            if (payload == 0) {
                payload = 1;
            }
            return (short) (sign | 0x7C00 | payload);
        }

        int halfExp = exp - 127 + 15;
        if (halfExp >= 0x1F) {
            return (short) (sign | 0x7C00);
        }
        if (halfExp <= 0) {
            if (halfExp < -10) {
                return (short) sign;
            }
            int sig = mant | 0x00800000;
            int shift = 14 - halfExp;
            int rounded = sig + (1 << (shift - 1)) - 1 + ((sig >>> shift) & 1);
            return (short) (sign | (rounded >>> shift));
        }

        int rounded = mant + 0x00001000;
        if ((rounded & 0x00800000) != 0) {
            rounded = 0;
            halfExp++;
            if (halfExp >= 0x1F) {
                return (short) (sign | 0x7C00);
            }
        }
        return (short) (sign | (halfExp << 10) | (rounded >>> 13));
    }

    /**
     * Convert half-float bits to a float.
     *
     * @param bits half-float bits
     * @return decoded float
     */
    public static float unpack(short bits) {
        int h = bits & 0xFFFF;
        int sign = (h & 0x8000) << 16;
        int exp = (h >>> 10) & 0x1F;
        int mant = h & 0x03FF;

        int outBits;
        if (exp == 0) {
            if (mant == 0) {
                outBits = sign;
            } else {
                int e = -14;
                while ((mant & 0x0400) == 0) {
                    mant <<= 1;
                    e--;
                }
                mant &= 0x03FF;
                outBits = sign | ((e + 127) << 23) | (mant << 13);
            }
        } else if (exp == 0x1F) {
            outBits = sign | 0x7F800000 | (mant << 13);
        } else {
            outBits = sign | ((exp - 15 + 127) << 23) | (mant << 13);
        }
        return Float.intBitsToFloat(outBits);
    }
}
