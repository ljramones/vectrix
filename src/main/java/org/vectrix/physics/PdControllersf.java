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
package org.vectrix.physics;

import org.vectrix.core.Vector3f;
import org.vectrix.core.Vector3fc;

/**
 * Proportional-derivative controller helpers for angular control.
 *
 * @since 1.10.12
 */
public final class PdControllersf {
    private PdControllersf() {
    }

    public static Vector3f torque(Vector3fc angularError, float kp, Vector3fc angularVelocity, float kd, Vector3f dest) {
        return dest.set(
                angularError.x() * kp - angularVelocity.x() * kd,
                angularError.y() * kp - angularVelocity.y() * kd,
                angularError.z() * kp - angularVelocity.z() * kd);
    }

    public static Vector3f clampTorque(Vector3fc torque, float maxTorque, Vector3f dest) {
        float length = torque.length();
        if (length > maxTorque && length > 0.0f) {
            float scale = maxTorque / length;
            return dest.set(torque.x() * scale, torque.y() * scale, torque.z() * scale);
        }
        return dest.set(torque);
    }
}
