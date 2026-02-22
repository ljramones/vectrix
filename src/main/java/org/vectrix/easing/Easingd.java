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
package org.vectrix.easing;

/**
 * @since 1.0.0
 */

public final class Easingd {
    private Easingd() {
    }

    public static double linear(double t) { return t; }
    public static double smoothStep(double t) { return t * t * (3.0 - 2.0 * t); }
    public static double easeInQuad(double t) { return t * t; }
    public static double easeOutQuad(double t) { return 1.0 - (1.0 - t) * (1.0 - t); }
    public static double easeInOutQuad(double t) { return t < 0.5 ? 2.0 * t * t : 1.0 - 0.5 * java.lang.Math.pow(-2.0 * t + 2.0, 2.0); }

    public static double easeInCubic(double t) { return t * t * t; }
    public static double easeOutCubic(double t) { double u = 1.0 - t; return 1.0 - u * u * u; }
    public static double easeInOutCubic(double t) { return t < 0.5 ? 4.0 * t * t * t : 1.0 - 0.5 * java.lang.Math.pow(-2.0 * t + 2.0, 3.0); }

    public static double bounceOut(double t) {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (t < 1.0 / d1) return n1 * t * t;
        if (t < 2.0 / d1) {
            t -= 1.5 / d1;
            return n1 * t * t + 0.75;
        }
        if (t < 2.5 / d1) {
            t -= 2.25 / d1;
            return n1 * t * t + 0.9375;
        }
        t -= 2.625 / d1;
        return n1 * t * t + 0.984375;
    }

    public static double spring(double t, double damping, double frequency) {
        double envelope = java.lang.Math.exp(-damping * t);
        return 1.0 - envelope * java.lang.Math.cos(frequency * t);
    }
}
