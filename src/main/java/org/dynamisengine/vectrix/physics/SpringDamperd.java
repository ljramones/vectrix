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
 * Spring-damper helper functions.
 *
 * @since 1.10.12
 */
public final class SpringDamperd {
    private SpringDamperd() {
    }

    public static double force(double displacement, double stiffness, double velocity, double damping) {
        return -stiffness * displacement - damping * velocity;
    }

    /**
     * One exact step of a critically damped spring toward {@code target}: {@code x'' = -ω² x - 2ω x'}, with
     * {@code x = current - target}. The step is exact for any {@code dt}, so the result is frame-rate independent.
     * The new velocity goes to {@code velOut[0]}.
     * <p>
     * Before 2.4.0 the decay {@code e^(-ω dt)} came from a cubic rational approximation, which was 0.1 % off at
     * {@code ω dt = 1}. It is now evaluated exactly, in double.
     */
    public static double criticallyDamped(
            double current, double target, double velocity, double angularFrequency, double dt, double[] velOut) {
        double x = (double) current - target;
        double w = angularFrequency, v = velocity;
        double e = AccurateMath.exp(-w * dt);
        double temp = (v + w * x) * dt;
        velOut[0] = ((v - w * temp) * e);
        return (target + (x + temp) * e);
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
     *
     * @since 2.4.0
     */
    public static double dampedStep(
            double current, double target, double velocity, double stiffness, double damping, double dt, double[] velOut) {
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
        velOut[0] = (v * ec - (g * v + k * x) * es);
        return (target + (x * ec + (v + g * x) * es));
    }

    /**
     * One semi-implicit Euler step of a damped spring. Despite the name, this is not an overdamped solution: it is
     * first-order accurate and unstable for stiff springs or large {@code dt}.
     *
     * @deprecated since 2.4.0; use {@link #dampedStep}, which is exact in every damping regime.
     */
    @Deprecated
    public static double overdamped(
            double current, double target, double velocity, double stiffness, double damping, double dt, double[] velOut) {
        double x = current - target;
        double accel = -stiffness * x - damping * velocity;
        double newVelocity = velocity + accel * dt;
        double newValue = current + newVelocity * dt;
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
     * @deprecated since 2.4.0; use {@link #suspensionForceFromLength} or {@link #suspensionForceFromCompression},
     *             which name their inputs unambiguously.
     */
    @Deprecated
    public static double suspensionForce(
            double compression, double restLength, double stiffness, double compressionRate, double damping) {
        return suspensionForceFromLength(compression, restLength, compressionRate, stiffness, damping);
    }

    /**
     * Suspension force along the strut, positive pushing the wheel away from the body, from the current spring
     * length: {@code k (restLength − length) − c · lengthRate}.
     *
     * @since 2.4.0
     */
    public static double suspensionForceFromLength(double length, double restLength, double lengthRate, double stiffness,
            double damping) {
        return stiffness * (restLength - length) - damping * lengthRate;
    }

    /**
     * Suspension force along the strut, positive pushing the wheel away from the body, from the compression
     * (how far the spring is shortened from rest, positive when compressed) and its rate:
     * {@code k · compression + c · compressionRate}.
     *
     * @since 2.4.0
     */
    public static double suspensionForceFromCompression(double compression, double compressionRate, double stiffness,
            double damping) {
        return stiffness * compression + damping * compressionRate;
    }

    /**
     * The damping coefficient {@code 2 sqrt(k m)} that makes a spring of stiffness {@code k} on mass {@code m}
     * critically damped. The common shortcut {@code 2 sqrt(k)} is critical only for unit mass.
     *
     * @since 2.4.0
     */
    public static double criticalDamping(double stiffness, double mass) {
        return (2.0 * Math.sqrt((double) stiffness * mass));
    }

    /**
     * The damping coefficient {@code 2 ζ sqrt(k m)} for damping ratio {@code ζ}: below 1 underdamped, 1 critical,
     * above 1 overdamped.
     *
     * @since 2.4.0
     */
    public static double dampingForRatio(double dampingRatio, double stiffness, double mass) {
        return (2.0 * dampingRatio * Math.sqrt((double) stiffness * mass));
    }

    /**
     * The stiffness {@code m (2π f)²} that makes mass {@code m} oscillate at {@code frequencyHz} (undamped).
     *
     * @since 2.4.0
     */
    public static double stiffnessForFrequency(double frequencyHz, double mass) {
        double w = 2.0 * Math.PI * frequencyHz;
        return (mass * w * w);
    }
}
