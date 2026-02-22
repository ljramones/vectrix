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

public final class Easingf {
    private Easingf() {
    }

    public static float linear(float t) { return t; }
    public static float smoothStep(float t) { return t * t * (3.0f - 2.0f * t); }

    public static float easeInQuad(float t) { return t * t; }
    public static float easeOutQuad(float t) { return 1.0f - (1.0f - t) * (1.0f - t); }
    public static float easeInOutQuad(float t) { return t < 0.5f ? 2.0f * t * t : 1.0f - 0.5f * (float) java.lang.Math.pow(-2.0f * t + 2.0f, 2.0f); }

    public static float easeInCubic(float t) { return t * t * t; }
    public static float easeOutCubic(float t) { float u = 1.0f - t; return 1.0f - u * u * u; }
    public static float easeInOutCubic(float t) { return t < 0.5f ? 4.0f * t * t * t : 1.0f - 0.5f * (float) java.lang.Math.pow(-2.0f * t + 2.0f, 3.0f); }

    public static float bounceOut(float t) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (t < 1.0f / d1) return n1 * t * t;
        if (t < 2.0f / d1) {
            t -= 1.5f / d1;
            return n1 * t * t + 0.75f;
        }
        if (t < 2.5f / d1) {
            t -= 2.25f / d1;
            return n1 * t * t + 0.9375f;
        }
        t -= 2.625f / d1;
        return n1 * t * t + 0.984375f;
    }

    public static float spring(float t, float damping, float frequency) {
        float envelope = (float) java.lang.Math.exp(-damping * t);
        return 1.0f - envelope * (float) java.lang.Math.cos(frequency * t);
    }
}
