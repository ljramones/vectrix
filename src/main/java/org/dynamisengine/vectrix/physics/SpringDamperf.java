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

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * Damped-spring kernels (single precision): the spring-damper force, exact time steps of a damped spring toward a
 * target, damping and stiffness coefficients for a target behavior, and suspension forces. Any consistent set of units
 * works. The steps are for unit mass, so their stiffness and damping are per unit mass.
 *
 * @since 1.10.12
 */
public final class SpringDamperf {
    private SpringDamperf() {
    }

    /**
     * Returns the spring-damper force {@code −k · displacement − c · velocity}: Hooke's law plus linear damping.
     *
     * @param displacement the displacement from the rest position
     * @param stiffness the spring stiffness {@code k}
     * @param velocity the velocity along the same axis
     * @param damping the damping coefficient {@code c}
     * @return the force, which opposes the displacement and the velocity when {@code k} and {@code c} are positive
     */
    public static float force(float displacement, float stiffness, float velocity, float damping) {
        return -stiffness * displacement - damping * velocity;
    }

    /**
     * One exact step of a critically damped spring toward {@code target}: {@code x'' = -ω² x - 2ω x'}, with
     * {@code x = current - target}. The step is exact for any {@code dt}, so the result is frame-rate independent.
     * The new velocity goes to {@code velOut[0]}. Computed in double; does not allocate.
     * <p>
     * Before 2.4.0 the decay {@code e^(-ω dt)} came from a cubic rational approximation, which was 0.1 % off at
     * {@code ω dt = 1}. It is now evaluated exactly, in double.
     *
     * @param current the current position
     * @param target the position the spring moves toward
     * @param velocity the current velocity
     * @param angularFrequency the natural angular frequency {@code ω}, in radians per unit time, at least 0
     * @param dt the time step
     * @param velOut receives the new velocity in element 0
     * @return the new position
     */
    public static float criticallyDamped(
            float current, float target, float velocity, float angularFrequency, float dt, float[] velOut) {
        double x = (double) current - target;
        double w = angularFrequency, v = velocity;
        double e = AccurateMath.exp(-w * dt);
        double temp = (v + w * x) * dt;
        velOut[0] = (float) ((v - w * temp) * e);
        return (float) (target + (x + temp) * e);
    }

    /**
     * One exact step of a damped spring toward {@code target}, for unit mass: {@code x'' = -k x - c x'} with
     * {@code x = current - target}, {@code k = stiffness} and {@code c = damping} (per unit mass, both at least 0).
     * <p>
     * All regimes are exact: underdamped, critically damped and overdamped. The step is therefore frame-rate
     * independent, and stable for any {@code dt}, including stiff springs. The new velocity goes to {@code velOut[0]}.
     * The computation runs in double and avoids cancellation:
     * <ul>
     * <li>near critical damping, a series replaces {@code sin}/{@code sinh};</li>
     * <li>when strongly overdamped, the two exponentials are evaluated separately, with the slow rate
     * {@code k / (γ + β)} instead of {@code γ − β}.</li>
     * </ul>
     * Here {@code γ = c / 2} and {@code β = sqrt(γ² − k)}. Does not allocate.
     *
     * @param current the current position
     * @param target the position the spring moves toward
     * @param velocity the current velocity
     * @param stiffness the stiffness per unit mass {@code k} (the squared undamped angular frequency), at least 0
     * @param damping the damping per unit mass {@code c}, at least 0
     * @param dt the time step
     * @param velOut receives the new velocity in element 0
     * @return the new position
     * @since 2.4.0
     */
    public static float dampedStep(
            float current, float target, float velocity, float stiffness, float damping, float dt, float[] velOut) {
        double x = (double) current - target, v = velocity, k = stiffness, g = 0.5 * damping, t = dt;
        double u = g * g - k; // > 0 overdamped, < 0 underdamped, 0 critical
        double ut2 = u * t * t;
        double ec; // e^(-g t) C(t), with C = cosh(b t) or cos(w t)
        double es; // e^(-g t) S(t), with S = sinh(b t)/b or sin(w t)/w
        if (Math.abs(ut2) < 0.25) {
            double c = 1, s = 1, ct = 1, st = 1;
            for (int n = 1; n <= 8; n++) {
                ct *= ut2 / ((2 * n - 1) * (2 * n));
                st *= ut2 / ((2 * n) * (2 * n + 1));
                c += ct;
                s += st;
            }
            double e = AccurateMath.exp(-g * t);
            ec = e * c;
            es = e * t * s;
        } else if (u > 0) {
            double b = Math.sqrt(u);
            double slow = AccurateMath.exp(-k / (g + b) * t); // e^(-(g - b) t) without cancellation
            double fast = AccurateMath.exp(-(g + b) * t);
            ec = 0.5 * (slow + fast);
            es = 0.5 * (slow - fast) / b;
        } else {
            double w = Math.sqrt(-u);
            double e = AccurateMath.exp(-g * t);
            ec = e * AccurateMath.cos(w * t);
            es = e * AccurateMath.sin(w * t) / w;
        }
        velOut[0] = (float) (v * ec - (g * v + k * x) * es);
        return (float) (target + (x * ec + (v + g * x) * es));
    }

    /**
     * One semi-implicit Euler step of a damped spring. Despite the name, this is not an overdamped solution: it is
     * first-order accurate and unstable for stiff springs or large {@code dt}.
     *
     * @param current the current position
     * @param target the position the spring moves toward
     * @param velocity the current velocity
     * @param stiffness the stiffness per unit mass
     * @param damping the damping per unit mass
     * @param dt the time step
     * @param velOut receives the new velocity in element 0
     * @return the new position
     * @deprecated since 2.4.0; use {@link #dampedStep}, which is exact in every damping regime.
     */
    @Deprecated
    public static float overdamped(
            float current, float target, float velocity, float stiffness, float damping, float dt, float[] velOut) {
        float x = current - target;
        float accel = -stiffness * x - damping * velocity;
        float newVelocity = velocity + accel * dt;
        float newValue = current + newVelocity * dt;
        velOut[0] = newVelocity;
        return newValue;
    }

    /**
     * Suspension force from the spring's current length: {@code k (restLength − length) − c · lengthRate}.
     * <p>
     * The parameters were misnamed: {@code compression} is the current length, and {@code compressionRate} is the
     * rate of change of that length. Passing the actual compression gives the largest force at full extension and
     * damping in the wrong direction.
     *
     * @param compression despite its name, the current spring length
     * @param restLength the spring's rest length
     * @param stiffness the spring stiffness
     * @param compressionRate despite its name, the rate of change of the spring length
     * @param damping the damping coefficient
     * @return the force along the strut, positive pushing the wheel away from the body
     * @deprecated since 2.4.0; use {@link #suspensionForceFromLength} or {@link #suspensionForceFromCompression},
     *             which name their inputs unambiguously.
     */
    @Deprecated
    public static float suspensionForce(
            float compression, float restLength, float stiffness, float compressionRate, float damping) {
        return suspensionForceFromLength(compression, restLength, compressionRate, stiffness, damping);
    }

    /**
     * Suspension force along the strut, positive pushing the wheel away from the body, from the current spring
     * length: {@code k (restLength − length) − c · lengthRate}.
     *
     * @param length the current spring length
     * @param restLength the spring's rest length
     * @param lengthRate the rate of change of the length, positive while the spring extends
     * @param stiffness the spring stiffness {@code k}
     * @param damping the damping coefficient {@code c}
     * @return the force along the strut, positive pushing the wheel away from the body
     * @since 2.4.0
     */
    public static float suspensionForceFromLength(float length, float restLength, float lengthRate, float stiffness,
            float damping) {
        return stiffness * (restLength - length) - damping * lengthRate;
    }

    /**
     * Suspension force along the strut, positive pushing the wheel away from the body, from the compression
     * (how far the spring is shortened from rest, positive when compressed) and its rate:
     * {@code k · compression + c · compressionRate}.
     *
     * @param compression how far the spring is shortened from its rest length, positive when compressed
     * @param compressionRate the rate of change of the compression, positive while the spring compresses
     * @param stiffness the spring stiffness {@code k}
     * @param damping the damping coefficient {@code c}
     * @return the force along the strut, positive pushing the wheel away from the body
     * @since 2.4.0
     */
    public static float suspensionForceFromCompression(float compression, float compressionRate, float stiffness,
            float damping) {
        return stiffness * compression + damping * compressionRate;
    }

    /**
     * The damping coefficient {@code 2 sqrt(k m)} that makes a spring of stiffness {@code k} on mass {@code m}
     * critically damped. The common shortcut {@code 2 sqrt(k)} is critical only for unit mass.
     *
     * @param stiffness the spring stiffness {@code k}, at least 0
     * @param mass the mass {@code m}, at least 0
     * @return the critical damping coefficient
     * @since 2.4.0
     */
    public static float criticalDamping(float stiffness, float mass) {
        return (float) (2.0 * Math.sqrt((double) stiffness * mass));
    }

    /**
     * The damping coefficient {@code 2 ζ sqrt(k m)} for damping ratio {@code ζ}: below 1 underdamped, 1 critical,
     * above 1 overdamped.
     *
     * @param dampingRatio the damping ratio {@code ζ}, at least 0
     * @param stiffness the spring stiffness {@code k}, at least 0
     * @param mass the mass {@code m}, at least 0
     * @return the damping coefficient
     * @since 2.4.0
     */
    public static float dampingForRatio(float dampingRatio, float stiffness, float mass) {
        return (float) (2.0 * dampingRatio * Math.sqrt((double) stiffness * mass));
    }

    /**
     * The stiffness {@code m (2π f)²} that makes mass {@code m} oscillate at {@code frequencyHz} (undamped).
     *
     * @param frequencyHz the undamped oscillation frequency {@code f}, in hertz (cycles per unit time)
     * @param mass the mass {@code m}
     * @return the stiffness
     * @since 2.4.0
     */
    public static float stiffnessForFrequency(float frequencyHz, float mass) {
        double w = 2.0 * Math.PI * frequencyHz;
        return (float) (mass * w * w);
    }
}
