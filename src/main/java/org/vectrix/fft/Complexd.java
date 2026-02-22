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
package org.vectrix.fft;

/**
 * Mutable complex number value type (double-precision).
  * @since 1.0.0
  */
public final class Complexd {
    public double real;
    public double imag;

    public Complexd() {
    }

    public Complexd(double real, double imag) {
        this.real = real;
        this.imag = imag;
    }

    public Complexd set(double real, double imag) {
        this.real = real;
        this.imag = imag;
        return this;
    }

    public Complexd set(Complexd other) {
        return set(other.real, other.imag);
    }

    public Complexd add(Complexd other, Complexd dest) {
        return dest.set(real + other.real, imag + other.imag);
    }

    public Complexd sub(Complexd other, Complexd dest) {
        return dest.set(real - other.real, imag - other.imag);
    }

    public Complexd mul(Complexd other, Complexd dest) {
        double r = real * other.real - imag * other.imag;
        double i = real * other.imag + imag * other.real;
        return dest.set(r, i);
    }

    public Complexd scale(double s, Complexd dest) {
        return dest.set(real * s, imag * s);
    }

    public Complexd conjugate(Complexd dest) {
        return dest.set(real, -imag);
    }
}
