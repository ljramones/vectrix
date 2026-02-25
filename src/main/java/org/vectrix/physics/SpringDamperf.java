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

/**
 * Spring-damper helper functions.
 *
 * @since 1.10.12
 */
public final class SpringDamperf {
    private SpringDamperf() {
    }

    public static float force(float displacement, float stiffness, float velocity, float damping) {
        return -stiffness * displacement - damping * velocity;
    }

    public static float criticallyDamped(
            float current, float target, float velocity, float angularFrequency, float dt, float[] velOut) {
        float x = current - target;
        float omegaDt = angularFrequency * dt;
        float exp = 1.0f / (1.0f + omegaDt + 0.48f * omegaDt * omegaDt + 0.235f * omegaDt * omegaDt * omegaDt);
        float temp = (velocity + angularFrequency * x) * dt;
        float newVelocity = (velocity - angularFrequency * temp) * exp;
        float newValue = target + (x + temp) * exp;
        velOut[0] = newVelocity;
        return newValue;
    }

    public static float overdamped(
            float current, float target, float velocity, float stiffness, float damping, float dt, float[] velOut) {
        float x = current - target;
        float accel = -stiffness * x - damping * velocity;
        float newVelocity = velocity + accel * dt;
        float newValue = current + newVelocity * dt;
        velOut[0] = newVelocity;
        return newValue;
    }

    public static float suspensionForce(
            float compression, float restLength, float stiffness, float compressionRate, float damping) {
        return stiffness * (restLength - compression) - damping * compressionRate;
    }
}
