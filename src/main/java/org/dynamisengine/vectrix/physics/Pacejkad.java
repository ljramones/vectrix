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
 * Pacejka "magic formula" tyre kernels (double precision): the force curve and the slip quantities it takes.
 * Vehicle models, friction scaling and combined-slip policy belong to the physics library.
 *
 * @since 2.4.0
 */
public final class Pacejkad {
    private Pacejkad() {
    }

    /**
     * The magic formula {@code y = D sin(C atan(B x − E (B x − atan(B x))))}. The coefficients are stiffness
     * {@code B}, shape {@code C}, peak {@code D} and curvature {@code E}. Multiply {@code D} by the normal load for
     * a force. Evaluated in double.
     *
     * @param slip the slip {@code x}: a slip ratio from {@link #slipRatio} or a slip angle in radians from
     *             {@link #slipAngle}
     * @param b the stiffness factor {@code B}
     * @param c the shape factor {@code C}
     * @param d the peak value {@code D}
     * @param e the curvature factor {@code E}
     * @return the curve value {@code y}
     */
    public static double magicFormula(double slip, double b, double c, double d, double e) {
        double bx = (double) b * slip;
        return (d * AccurateMath.sin(c * AccurateMath.atan(bx - e * (bx - AccurateMath.atan(bx)))));
    }

    /**
     * The magic formula with horizontal and vertical shifts: {@code y = D sin(C atan(B x' − E (B x' − atan(B x'))))
     * + Sv}, with {@code x' = slip + Sh}.
     *
     * @param slip the slip {@code x}
     * @param b the stiffness factor {@code B}
     * @param c the shape factor {@code C}
     * @param d the peak value {@code D}
     * @param e the curvature factor {@code E}
     * @param horizontalShift the shift {@code Sh} added to the slip
     * @param verticalShift the shift {@code Sv} added to the result
     * @return the curve value {@code y}
     */
    public static double magicFormula(double slip, double b, double c, double d, double e, double horizontalShift,
            double verticalShift) {
        return magicFormula(slip + horizontalShift, b, c, d, e) + verticalShift;
    }

    /**
     * Longitudinal slip ratio {@code (ω r − v) / max(|v|, minSpeed)}. Its sign is the direction of the force
     * {@code F_z · magicFormula(slip, …)} along the wheel's forward axis, in either direction of travel. Driving
     * forward and braking in reverse are positive; braking forward and driving in reverse are negative. The speed is
     * signed: an unsigned speed would give a spurious slip near {@code −2} when reversing. {@code minSpeed > 0}
     * bounds the ratio near standstill.
     *
     * @param wheelAngularVelocity
     *            the wheel's spin, in radians per unit time, positive rolling forward
     * @param wheelRadius
     *            the wheel's rolling radius
     * @param longitudinalVelocity
     *            the contact patch's ground speed along the wheel's forward axis, signed
     * @param minSpeed
     *            the smallest denominator, greater than 0, in the unit of {@code longitudinalVelocity}
     * @return the slip ratio
     */
    public static double slipRatio(double wheelAngularVelocity, double wheelRadius, double longitudinalVelocity,
            double minSpeed) {
        double v = longitudinalVelocity;
        return (((double) wheelAngularVelocity * wheelRadius - v) / Math.max(Math.abs(v), minSpeed));
    }

    /**
     * Slip angle {@code atan2(v_lat, |v_long|)} in radians, in {@code [−π/2, π/2]}: the angle between the wheel's
     * heading and the contact patch's velocity, positive when the patch slides toward the wheel's positive lateral
     * axis. It is exact at every speed, including sideways sliding ({@code ±π/2}), and {@code 0} at rest. The
     * lateral force opposes it, for example {@code F_y = −F_z · magicFormula(α, …)}.
     *
     * @param lateralVelocity the contact patch's velocity along the wheel's lateral axis
     * @param longitudinalVelocity the contact patch's velocity along the wheel's forward axis; its sign is ignored
     * @return the slip angle, in radians
     */
    public static double slipAngle(double lateralVelocity, double longitudinalVelocity) {
        return AccurateMath.atan2(lateralVelocity, Math.abs((double) longitudinalVelocity));
    }
}
