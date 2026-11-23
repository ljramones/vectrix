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
 * Proportional-derivative controller helpers for angular control.
 *
 * @since 1.10.12
 */
public final class PdControllersd {
    private PdControllersd() {
    }

    public static Vector3d torque(Vector3dc angularError, double kp, Vector3dc angularVelocity, double kd, Vector3d dest) {
        return dest.set(
                angularError.x() * kp - angularVelocity.x() * kd,
                angularError.y() * kp - angularVelocity.y() * kd,
                angularError.z() * kp - angularVelocity.z() * kd);
    }

    public static Vector3d clampTorque(Vector3dc torque, double maxTorque, Vector3d dest) {
        double length = torque.length();
        if (length > maxTorque && length > 0.0) {
            double scale = maxTorque / length;
            return dest.set(torque.x() * scale, torque.y() * scale, torque.z() * scale);
        }
        return dest.set(torque);
    }
}
