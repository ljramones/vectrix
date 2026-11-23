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
package org.dynamisengine.vectrix.easing;

/**
 * Easing functions in float precision: each maps a normalized time {@code t} to a progress value, with
 * {@code f(0) = 0} and {@code f(1) = 1}, except {@code spring}, which only settles toward 1.
 * <p>
 * {@code t} is not clamped: values outside {@code [0, 1]} extrapolate the formula, so clamp {@code t} yourself if
 * the input can leave that range. The transcendental parts use {@code AccurateMath}, so they follow
 * {@code -Dvectrix.strictMath}. The functions are stateless and do not allocate. {@link Easingd} is the double twin.
 *
 * @since 1.0.0
 */
public final class Easingf {
    private Easingf() {
    }

    /**
     * The identity, {@code t}.
     *
     * @param t the normalized time, usually in {@code [0, 1]}; not clamped
     * @return the eased value
     */
    public static float linear(float t) { return t; }

    /**
     * Hermite smoothstep {@code t²·(3 - 2t)}: zero slope at both ends.
     *
     * @param t the normalized time, usually in {@code [0, 1]}; not clamped
     * @return the eased value
     */
    public static float smoothStep(float t) { return t * t * (3.0f - 2.0f * t); }

    /**
     * Quadratic ease-in {@code t²}: starts slow.
     *
     * @param t the normalized time, usually in {@code [0, 1]}; not clamped
     * @return the eased value
     */
    public static float easeInQuad(float t) { return t * t; }

    /**
     * Quadratic ease-out {@code 1 - (1 - t)²}: ends slow.
     *
     * @param t the normalized time, usually in {@code [0, 1]}; not clamped
     * @return the eased value
     */
    public static float easeOutQuad(float t) { return 1.0f - (1.0f - t) * (1.0f - t); }

    /**
     * Quadratic ease-in-out: {@code 2t²} below {@code t = 0.5}, {@code 1 - (2 - 2t)²/2} from there.
     *
     * @param t the normalized time, usually in {@code [0, 1]}; not clamped
     * @return the eased value
     */
    public static float easeInOutQuad(float t) { return t < 0.5f ? 2.0f * t * t : 1.0f - 0.5f * (float) org.dynamisengine.vectrix.core.AccurateMath.pow(-2.0f * t + 2.0f, 2.0f); }

    /**
     * Cubic ease-in {@code t³}.
     *
     * @param t the normalized time, usually in {@code [0, 1]}; not clamped
     * @return the eased value
     */
    public static float easeInCubic(float t) { return t * t * t; }

    /**
     * Cubic ease-out {@code 1 - (1 - t)³}.
     *
     * @param t the normalized time, usually in {@code [0, 1]}; not clamped
     * @return the eased value
     */
    public static float easeOutCubic(float t) { float u = 1.0f - t; return 1.0f - u * u * u; }

    /**
     * Cubic ease-in-out: {@code 4t³} below {@code t = 0.5}, {@code 1 - (2 - 2t)³/2} from there.
     *
     * @param t the normalized time, usually in {@code [0, 1]}; not clamped
     * @return the eased value
     */
    public static float easeInOutCubic(float t) { return t < 0.5f ? 4.0f * t * t * t : 1.0f - 0.5f * (float) org.dynamisengine.vectrix.core.AccurateMath.pow(-2.0f * t + 2.0f, 3.0f); }

    /**
     * Bounce ease-out (the common Penner form): four parabolic arcs of decreasing height that reach 1 at {@code t = 1}.
     * Below 0 it returns {@code 7.5625·t²}; above 1 it continues the last arc.
     *
     * @param t the normalized time, usually in {@code [0, 1]}; not clamped
     * @return the eased value
     */
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

    /**
     * Damped spring settling at 1: {@code 1 - e^(-damping·t)·cos(frequency·t)}. It overshoots 1 when the oscillation is
     * underdamped; it reaches exactly 1 only as {@code t} grows.
     *
     * @param t the time, usually in {@code [0, 1]}; not clamped
     * @param damping the decay rate of the envelope per unit of {@code t}; larger settles faster
     * @param frequency the angular frequency in radians per unit of {@code t}
     * @return the eased value; 0 at {@code t = 0}
     */
    public static float spring(float t, float damping, float frequency) {
        float envelope = (float) org.dynamisengine.vectrix.core.AccurateMath.exp(-damping * t);
        return 1.0f - envelope * (float) org.dynamisengine.vectrix.core.AccurateMath.cos(frequency * t);
    }
}
