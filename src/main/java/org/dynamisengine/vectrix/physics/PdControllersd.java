/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix Contributors
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
package org.dynamisengine.vectrix.physics;

import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Proportional-derivative (PD) controller kernels for angular control (double precision).
 *
 * @since 1.10.12
 */
public final class PdControllersd {
    private PdControllersd() {
    }

    /**
     * Returns the PD torque {@code kp · angularError − kd · angularVelocity}, per component. {@code dest} may be
     * either input.
     *
     * @param angularError the angular error to drive to zero
     * @param kp the proportional gain
     * @param angularVelocity the current angular velocity, in radians per unit time
     * @param kd the derivative gain
     * @param dest receives the torque
     * @return {@code dest}
     */
    public static Vector3d torque(Vector3dc angularError, double kp, Vector3dc angularVelocity, double kd, Vector3d dest) {
        return dest.set(
                angularError.x() * kp - angularVelocity.x() * kd,
                angularError.y() * kp - angularVelocity.y() * kd,
                angularError.z() * kp - angularVelocity.z() * kd);
    }

    /**
     * Limits the length of {@code torque} to {@code maxTorque}. A longer torque is scaled to that length, keeping its
     * direction; any other is copied unchanged. {@code dest} may be {@code torque}.
     *
     * @param torque the torque to limit
     * @param maxTorque the largest allowed length, at least 0
     * @param dest receives the limited torque
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code maxTorque} is negative or NaN; {@code dest} is unchanged then
     */
    public static Vector3d clampTorque(Vector3dc torque, double maxTorque, Vector3d dest) {
        if (!(maxTorque >= 0)) {
            throw new IllegalArgumentException("maxTorque must be at least 0: " + maxTorque);
        }
        double length = torque.length();
        if (length > maxTorque && length > 0.0) {
            double scale = maxTorque / length;
            return dest.set(torque.x() * scale, torque.y() * scale, torque.z() * scale);
        }
        return dest.set(torque);
    }
}
