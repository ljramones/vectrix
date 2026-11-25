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
package org.dynamisengine.vectrix.core;

/**
 * Mutable complex number value type (double-precision). Not thread-safe.
 *
 * @since 2.6.0 (earlier as {@code fft.Complexd})
 */
public final class Complexd {
    /** The real part. */
    public double real;
    /** The imaginary part. */
    public double imag;

    /** Creates the complex number {@code 0 + 0i}. */
    public Complexd() {
    }

    /**
     * Creates the complex number {@code real + imag i}.
     *
     * @param real the real part
     * @param imag the imaginary part
     */
    public Complexd(double real, double imag) {
        this.real = real;
        this.imag = imag;
    }

    /**
     * Sets both parts.
     *
     * @param real the real part
     * @param imag the imaginary part
     * @return this
     */
    public Complexd set(double real, double imag) {
        this.real = real;
        this.imag = imag;
        return this;
    }

    /**
     * Copies the value of another complex number.
     *
     * @param other the complex number to copy
     * @return this
     */
    public Complexd set(Complexd other) {
        return set(other.real, other.imag);
    }

    /**
     * Adds {@code other} to this number and stores the sum in {@code dest}. {@code dest} may be {@code this} or
     * {@code other}.
     *
     * @param other the number to add
     * @param dest receives the result
     * @return {@code dest}
     */
    public Complexd add(Complexd other, Complexd dest) {
        return dest.set(real + other.real, imag + other.imag);
    }

    /**
     * Subtracts {@code other} from this number and stores the difference in {@code dest}. {@code dest} may be
     * {@code this} or {@code other}.
     *
     * @param other the number to subtract
     * @param dest receives the result
     * @return {@code dest}
     */
    public Complexd sub(Complexd other, Complexd dest) {
        return dest.set(real - other.real, imag - other.imag);
    }

    /**
     * Multiplies this number by {@code other} and stores the product in {@code dest}. {@code dest} may be {@code this}
     * or {@code other}.
     *
     * @param other the factor
     * @param dest receives the result
     * @return {@code dest}
     */
    public Complexd mul(Complexd other, Complexd dest) {
        double r = real * other.real - imag * other.imag;
        double i = real * other.imag + imag * other.real;
        return dest.set(r, i);
    }

    /**
     * Multiplies both parts by a real factor and stores the result in {@code dest}, which may be {@code this}.
     *
     * @param s the real factor
     * @param dest receives the result
     * @return {@code dest}
     */
    public Complexd scale(double s, Complexd dest) {
        return dest.set(real * s, imag * s);
    }

    /**
     * Stores the complex conjugate {@code real - imag i} in {@code dest}, which may be {@code this}.
     *
     * @param dest receives the result
     * @return {@code dest}
     */
    public Complexd conjugate(Complexd dest) {
        return dest.set(real, -imag);
    }
}
