/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
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
package org.dynamisengine.vectrix.animation;

import org.dynamisengine.vectrix.core.Quaterniond;

/**
 * Rotation curves over quaternion key arrays (double precision). Keys are flat
 * {@code x, y, z, w} arrays of unit quaternions.
 * <ul>
 * <li>{@link #unroll}: picks each key's sign so neighbours lie in the same hemisphere (q and −q are the same rotation);
 * the curves below interpolate the keys as given, so call it first.</li>
 * <li>{@link #squad}: Shoemake's SQUAD with control points from {@link #squadControlPoints} (C¹).</li>
 * <li>{@link #bezier}: spherical cubic Bezier by de Casteljau slerps (Shoemake 1985).</li>
 * <li>{@link #cumulativeBSpline}: Kim, Kim and Shin's (1995) cumulative cubic B-spline,
 * {@code q(t) = q₀ Π exp(B̃ᵢ(t) ωᵢ)} with {@code ωᵢ = log(qᵢ₋₁⁻¹ qᵢ)}: C² (continuous angular acceleration),
 * approximating rather than interpolating.</li>
 * <li>{@link #weightedMean}: the weighted geodesic (Karcher) mean, by fixed-point iteration in the tangent space;
 * exact, unlike normalized linear blending.</li>
 * </ul>
 * <p>
 * Key arrays start at index 0 with 4 doubles per key. The evaluation methods use a per-thread scratch array, which is
 * allocated on the first call in each thread.
 *
 * @since 2.3.0
 */
public final class QuaternionCurvesd {
    private QuaternionCurvesd() {
    }

    private static void load(double[] s, int o, double[] keys, int k) {
        s[o] = keys[4 * k];
        s[o + 1] = keys[4 * k + 1];
        s[o + 2] = keys[4 * k + 2];
        s[o + 3] = keys[4 * k + 3];
    }

    private static Quaterniond store(double[] s, int o, Quaterniond dest) {
        QuatKernels.normalize(s, o);
        return dest.set(s[o], s[o + 1], s[o + 2], s[o + 3]);
    }

    /**
     * Flips keys in place so that each key has a non-negative dot product with the one before it (after that one's
     * own flip). The first key is never flipped.
     *
     * @param keys the keys, 4 doubles each
     * @param count the number of keys
     * @return the number of keys flipped
     */
    public static int unroll(double[] keys, int count) {
        int flips = 0;
        for (int k = 1; k < count; k++) {
            double d = (double) keys[4 * k] * keys[4 * k - 4] + (double) keys[4 * k + 1] * keys[4 * k - 3]
                    + (double) keys[4 * k + 2] * keys[4 * k - 2] + (double) keys[4 * k + 3] * keys[4 * k - 1];
            if (d < 0.0) {
                for (int i = 0; i < 4; i++) {
                    keys[4 * k + i] = -keys[4 * k + i];
                }
                flips++;
            }
        }
        return flips;
    }

    /**
     * SQUAD control points {@code sᵢ = qᵢ exp(−(log(qᵢ⁻¹ qᵢ₋₁) + log(qᵢ⁻¹ qᵢ₊₁)) / 4)} for unrolled keys, with
     * {@code s₀ = q₀} and {@code sₙ₋₁ = qₙ₋₁}, into {@code outControls} (same layout).
     *
     * @param keys the unrolled keys, 4 doubles each, unit length
     * @param count the number of keys
     * @param outControls receives the {@code count} control points; may be {@code keys}
     * @return {@code outControls}
     */
    public static double[] squadControlPoints(double[] keys, int count, double[] outControls) {
        double[] s = QuatKernels.SCRATCH.get();
        // key k - 1 as it was before outControls[k - 1] was written, so outControls may be keys
        double p0 = 0, p1 = 0, p2 = 0, p3 = 0;
        for (int k = 0; k < count; k++) {
            double c0 = keys[4 * k], c1 = keys[4 * k + 1], c2 = keys[4 * k + 2], c3 = keys[4 * k + 3];
            if (k == 0 || k == count - 1) {
                System.arraycopy(keys, 4 * k, outControls, 4 * k, 4);
                p0 = c0;
                p1 = c1;
                p2 = c2;
                p3 = c3;
                continue;
            }
            load(s, 0, keys, k);
            QuatKernels.conj(s, 0, 4);
            s[8] = p0;
            s[9] = p1;
            s[10] = p2;
            s[11] = p3;
            QuatKernels.mul(s, 4, 8, 12);
            QuatKernels.log(s, 12, 16);
            load(s, 8, keys, k + 1);
            QuatKernels.mul(s, 4, 8, 12);
            QuatKernels.log(s, 12, 20);
            s[24] = -0.25 * (s[16] + s[20]);
            s[25] = -0.25 * (s[17] + s[21]);
            s[26] = -0.25 * (s[18] + s[22]);
            QuatKernels.exp(s, 24, 28);
            QuatKernels.mul(s, 0, 28, 32);
            QuatKernels.normalize(s, 32);
            for (int i = 0; i < 4; i++) {
                outControls[4 * k + i] = s[32 + i];
            }
            p0 = c0;
            p1 = c1;
            p2 = c2;
            p3 = c3;
        }
        return outControls;
    }

    /**
     * SQUAD on segment {@code i} (between keys i and i + 1) at {@code t} in [0, 1]:
     * {@code slerp(slerp(qᵢ, qᵢ₊₁, t), slerp(sᵢ, sᵢ₊₁, t), 2t(1 − t))}. The slerps follow the arcs as given, so
     * the keys must be unrolled.
     *
     * @param keys the unrolled keys, 4 doubles each, unit length
     * @param controls the control points from {@link #squadControlPoints}
     * @param i the segment, in {@code [0, count − 2]}
     * @param t the parameter within the segment, in [0, 1]
     * @param dest receives the normalized rotation
     * @return {@code dest}
     */
    public static Quaterniond squad(double[] keys, double[] controls, int i, double t, Quaterniond dest) {
        double[] s = QuatKernels.SCRATCH.get();
        load(s, 0, keys, i);
        load(s, 4, keys, i + 1);
        load(s, 8, controls, i);
        load(s, 12, controls, i + 1);
        QuatKernels.slerp(s, 0, 4, t, 16);
        QuatKernels.slerp(s, 8, 12, t, 20);
        QuatKernels.normalize(s, 16);
        QuatKernels.normalize(s, 20);
        QuatKernels.slerp(s, 16, 20, 2.0 * t * (1.0 - t), 24);
        return store(s, 24, dest);
    }

    /**
     * Evaluates the spherical cubic Bezier curve with control rotations {@code q0..q3} at {@code t}. The curve passes
     * through {@code q0} and {@code q3}. The slerps follow the arcs as given, so unroll the keys first.
     *
     * @param keys the key array, 4 doubles per key
     * @param first the index of {@code q0}; keys {@code first} to {@code first + 3} are used
     * @param t the curve parameter, in [0, 1]
     * @param dest receives the normalized rotation
     * @return {@code dest}
     */
    public static Quaterniond bezier(double[] keys, int first, double t, Quaterniond dest) {
        double[] s = QuatKernels.SCRATCH.get();
        for (int k = 0; k < 4; k++) {
            load(s, 4 * k, keys, first + k);
        }
        // de Casteljau: three, two, then one slerp
        for (int level = 3; level > 0; level--) {
            for (int k = 0; k < level; k++) {
                QuatKernels.slerp(s, 4 * k, 4 * k + 4, t, 32);
                QuatKernels.normalize(s, 32);
                System.arraycopy(s, 32, s, 4 * k, 4);
            }
        }
        return store(s, 0, dest);
    }

    /**
     * Kim-Kim-Shin cumulative cubic B-spline on segment {@code i} (between keys i and i + 1; keys i − 1 and i + 2
     * are clamped at the ends) at {@code t} in [0, 1]. Keys must be unrolled.
     *
     * @param keys the unrolled keys, 4 doubles each, unit length
     * @param count the number of keys
     * @param i the segment, in {@code [0, count − 2]}
     * @param t the parameter within the segment, in [0, 1]
     * @param dest receives the normalized rotation
     * @return {@code dest}
     */
    public static Quaterniond cumulativeBSpline(double[] keys, int count, int i, double t, Quaterniond dest) {
        double[] s = QuatKernels.SCRATCH.get();
        double tt = t;
        // cumulative basis of the uniform cubic B-spline
        double b1 = (5.0 + 3.0 * tt - 3.0 * tt * tt + tt * tt * tt) / 6.0;
        double b2 = (1.0 + 3.0 * tt + 3.0 * tt * tt - 2.0 * tt * tt * tt) / 6.0;
        double b3 = tt * tt * tt / 6.0;
        int k0 = java.lang.Math.max(i - 1, 0);
        load(s, 0, keys, k0); // running product
        int prev = k0;
        for (int j = 1; j <= 3; j++) {
            int kj = java.lang.Math.min(java.lang.Math.max(i - 1 + j, 0), count - 1);
            load(s, 4, keys, prev);
            QuatKernels.conj(s, 4, 8);
            load(s, 12, keys, kj);
            QuatKernels.mul(s, 8, 12, 16);
            QuatKernels.log(s, 16, 20);
            double bj = j == 1 ? b1 : j == 2 ? b2 : b3;
            s[20] *= bj;
            s[21] *= bj;
            s[22] *= bj;
            QuatKernels.exp(s, 20, 24);
            QuatKernels.mul(s, 0, 24, 28);
            System.arraycopy(s, 28, s, 0, 4);
            prev = kj;
        }
        return store(s, 0, dest);
    }

    /**
     * Weighted geodesic mean of {@code count} rotations: the minimizer of {@code Σ wᵢ d(m, qᵢ)²} on SO(3), by
     * iterating {@code m ← m exp(Σ wᵢ log(m⁻¹ qᵢ) / Σ wᵢ)} from the normalized linear blend until the step (a half
     * angle) is below 1e-15 rad, at most 64 iterations. Keys may be in either hemisphere. The weights must not sum to
     * zero, or the result is NaN.
     *
     * @param keys the rotations, 4 doubles each, unit length
     * @param weights the weight of each rotation
     * @param count the number of rotations, at least 1
     * @param dest receives the normalized mean rotation
     * @return {@code dest}
     */
    public static Quaterniond weightedMean(double[] keys, double[] weights, int count, Quaterniond dest) {
        double[] s = QuatKernels.SCRATCH.get();
        load(s, 0, keys, 0);
        double wsum = 0.0;
        double mx = 0, my = 0, mz = 0, mw = 0;
        for (int k = 0; k < count; k++) {
            load(s, 4, keys, k);
            double sign = QuatKernels.dot(s, 0, 4) < 0.0 ? -1.0 : 1.0;
            double w = weights[k] * sign;
            mx += w * s[4];
            my += w * s[5];
            mz += w * s[6];
            mw += w * s[7];
            wsum += weights[k];
        }
        s[0] = mx;
        s[1] = my;
        s[2] = mz;
        s[3] = mw;
        QuatKernels.normalize(s, 0);
        for (int it = 0; it < 64; it++) {
            QuatKernels.conj(s, 0, 4);
            double dx = 0, dy = 0, dz = 0;
            for (int k = 0; k < count; k++) {
                load(s, 8, keys, k);
                QuatKernels.mul(s, 4, 8, 12);
                if (s[15] < 0.0) { // shortest arc: q and -q are the same rotation
                    s[12] = -s[12];
                    s[13] = -s[13];
                    s[14] = -s[14];
                    s[15] = -s[15];
                }
                QuatKernels.log(s, 12, 16);
                dx += weights[k] * s[16];
                dy += weights[k] * s[17];
                dz += weights[k] * s[18];
            }
            s[20] = dx / wsum;
            s[21] = dy / wsum;
            s[22] = dz / wsum;
            QuatKernels.exp(s, 20, 24);
            QuatKernels.mul(s, 0, 24, 28);
            QuatKernels.normalize(s, 28);
            System.arraycopy(s, 28, s, 0, 4);
            if (java.lang.Math.sqrt(s[20] * s[20] + s[21] * s[21] + s[22] * s[22]) < 1e-15) {
                break;
            }
        }
        return store(s, 0, dest);
    }
}
