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
package org.dynamisengine.vectrix.fft;

/**
 * Mutable complex number value type (single-precision). The methods with a {@code dest} parameter leave
 * {@code this} and {@code other} unchanged, and {@code dest} may be {@code this} or {@code other}.
 *
 * @since 1.0.0
 * @deprecated moved to {@link org.dynamisengine.vectrix.core.Complexf}
 */
@Deprecated(since = "2.6.0", forRemoval = true)
public final class Complexf {
    /** The real part. */
    public float real;
    /** The imaginary part. */
    public float imag;

    /** Creates the complex number 0. */
    public Complexf() {
    }

    /**
     * Creates the complex number {@code real + i·imag}.
     *
     * @param real the real part
     * @param imag the imaginary part
     */
    public Complexf(float real, float imag) {
        this.real = real;
        this.imag = imag;
    }

    /**
     * Sets this number to {@code real + i·imag}.
     *
     * @param real the real part
     * @param imag the imaginary part
     * @return this
     */
    public Complexf set(float real, float imag) {
        this.real = real;
        this.imag = imag;
        return this;
    }

    /**
     * Sets this number to the value of {@code other}.
     *
     * @param other the number to copy
     * @return this
     */
    public Complexf set(Complexf other) {
        return set(other.real, other.imag);
    }

    /**
     * Computes {@code this + other} and stores it in {@code dest}.
     *
     * @param other the number to add
     * @param dest receives the result
     * @return {@code dest}
     */
    public Complexf add(Complexf other, Complexf dest) {
        return dest.set(real + other.real, imag + other.imag);
    }

    /**
     * Computes {@code this - other} and stores it in {@code dest}.
     *
     * @param other the number to subtract
     * @param dest receives the result
     * @return {@code dest}
     */
    public Complexf sub(Complexf other, Complexf dest) {
        return dest.set(real - other.real, imag - other.imag);
    }

    /**
     * Computes the complex product {@code this · other} and stores it in {@code dest}.
     *
     * @param other the number to multiply by
     * @param dest receives the result
     * @return {@code dest}
     */
    public Complexf mul(Complexf other, Complexf dest) {
        float r = real * other.real - imag * other.imag;
        float i = real * other.imag + imag * other.real;
        return dest.set(r, i);
    }

    /**
     * Multiplies both parts by the real factor {@code s} and stores the result in {@code dest}.
     *
     * @param s the real factor
     * @param dest receives the result
     * @return {@code dest}
     */
    public Complexf scale(float s, Complexf dest) {
        return dest.set(real * s, imag * s);
    }

    /**
     * Stores the complex conjugate {@code real - i·imag} in {@code dest}.
     *
     * @param dest receives the result
     * @return {@code dest}
     */
    public Complexf conjugate(Complexf dest) {
        return dest.set(real, -imag);
    }

    /**
     * Copies this number into the replacement type in the {@code core} package.
     *
     * @param dest receives this number
     * @return {@code dest}
     */
    public org.dynamisengine.vectrix.core.Complexf get(org.dynamisengine.vectrix.core.Complexf dest) {
        return dest.set(real, imag);
    }
}
