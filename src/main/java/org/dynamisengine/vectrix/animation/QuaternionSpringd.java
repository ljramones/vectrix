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

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaterniondc;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Critically damped rotation spring (double precision), after Holden, "Spring-It-On"
 * (2021). The offset from the goal, {@code x = 2 log(q · goal⁻¹)} (scaled angle-axis, shortest arc), follows the exact
 * solution of {@code ẍ = −y² x − 2y ẋ} with {@code y = 2 ln 2 / halflife}:
 * {@code x(dt) = e^(−y dt) (x₀ + (v₀ + y x₀) dt)}, {@code v(dt) = e^(−y dt) (v₀ − (v₀ + y x₀) y dt)}. Because the
 * solution is exact, one step of {@code dt} equals two steps of {@code dt / 2} (up to rounding): the result does not
 * depend on the frame rate. The angular velocity is in world space ({@code q̇ = ½ ω q}), in radians per second.
 *
 * @since 2.3.0
 */
public final class QuaternionSpringd {
    private QuaternionSpringd() {
    }

    /**
     * Advances the spring by {@code dt}: writes the new rotation to {@code rotationDest} and angular velocity to
     * {@code velocityDest}. {@code halflife} is the time for the offset to halve (without initial velocity).
     * Destinations may alias the inputs. Uses a per-thread scratch array, which is allocated on the first call in each
     * thread.
     *
     * @param rotation the current rotation, unit length
     * @param angularVelocity the current world-space angular velocity, in radians per second
     * @param goal the rotation the spring pulls toward, unit length
     * @param halflife the time for the offset to halve, greater than 0, in the unit of {@code dt}
     * @param dt the time step, normally in seconds
     * @param rotationDest receives the new rotation, normalized
     * @param velocityDest receives the new angular velocity
     */
    public static void step(Quaterniondc rotation, Vector3dc angularVelocity, Quaterniondc goal, double halflife,
            double dt, Quaterniond rotationDest, Vector3d velocityDest) {
        double[] s = QuatKernels.SCRATCH.get();
        s[0] = rotation.x();
        s[1] = rotation.y();
        s[2] = rotation.z();
        s[3] = rotation.w();
        s[4] = -goal.x();
        s[5] = -goal.y();
        s[6] = -goal.z();
        s[7] = goal.w();
        s[40] = goal.x();
        s[41] = goal.y();
        s[42] = goal.z();
        s[43] = goal.w();
        QuatKernels.mul(s, 0, 4, 8);
        if (s[11] < 0.0) { // shortest arc
            s[8] = -s[8];
            s[9] = -s[9];
            s[10] = -s[10];
            s[11] = -s[11];
        }
        QuatKernels.log(s, 8, 12); // half the scaled angle-axis
        double y = 2.0 * 0.6931471805599453 / halflife;
        double e = AccurateMath.exp(-y * dt);
        double vx = angularVelocity.x(), vy = angularVelocity.y(), vz = angularVelocity.z();
        double x0 = 2.0 * s[12], x1 = 2.0 * s[13], x2 = 2.0 * s[14];
        double j1x = vx + x0 * y, j1y = vy + x1 * y, j1z = vz + x2 * y;
        double nx = e * (x0 + j1x * dt), ny = e * (x1 + j1y * dt), nz = e * (x2 + j1z * dt);
        double nvx, nvy, nvz;
        nvx = e * (vx - j1x * y * dt);
        nvy = e * (vy - j1y * y * dt);
        nvz = e * (vz - j1z * y * dt);
        s[16] = 0.5 * nx;
        s[17] = 0.5 * ny;
        s[18] = 0.5 * nz;
        QuatKernels.exp(s, 16, 20);
        QuatKernels.mul(s, 20, 40, 24);
        QuatKernels.normalize(s, 24);
        rotationDest.set(s[24], s[25], s[26], s[27]);
        velocityDest.set(nvx, nvy, nvz);
    }
}
