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
package org.dynamisengine.vectrix.ik;

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaterniondc;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Joint-limit projections for a joint's local rotation (double precision),
 * based on the swing-twist decomposition {@code q = swing · twist}: {@code twist} rotates about the bone axis and
 * {@code swing} tilts the axis. Limits are applied in the decomposition's angle space; the swing limit is an
 * elliptical cone, clamped by radial projection (the swing direction is kept, its angle reduced to the ellipse).
 *
 * @since 2.4.0
 */
public final class JointLimitsd {
    private JointLimitsd() {
    }

    /**
     * Returns the signed twist angle of {@code q} about {@code axis}: the angle of the twist part of its swing-twist
     * decomposition. {@code q} and {@code −q} give the same angle.
     *
     * @param q the rotation, unit length
     * @param axis the twist axis, unit length
     * @return the twist angle in radians, in [−π, π]
     */
    public static double twistAngle(Quaterniondc q, Vector3dc axis) {
        double p = q.x() * (double) axis.x() + q.y() * (double) axis.y() + q.z() * (double) axis.z();
        double w = q.w();
        if (w < 0.0) {
            p = -p;
            w = -w;
        }
        return (2.0 * AccurateMath.atan2(p, w));
    }

    /**
     * Clamps {@code q} to an elliptical swing cone and a twist range about unit bone axis {@code axis}:
     * {@code swingAxisA} (unit, perpendicular to {@code axis}) is the direction with swing limit {@code limitA}, and
     * {@code axis × swingAxisA} has {@code limitB} (radians); the twist angle is clamped to
     * {@code [minTwist, maxTwist]}. When nothing is clamped, {@code dest} receives {@code q} recomposed from its
     * parts, possibly negated (the same rotation).
     *
     * @param q the joint's local rotation, unit length
     * @param axis the bone axis, unit length
     * @param swingAxisA the direction of limit {@code limitA}, unit length and perpendicular to {@code axis}
     * @param limitA the largest swing angle toward {@code swingAxisA}, in radians, greater than 0
     * @param limitB the largest swing angle toward {@code axis × swingAxisA}, in radians, greater than 0
     * @param minTwist the smallest twist angle, in radians
     * @param maxTwist the largest twist angle, in radians
     * @param dest receives the clamped rotation; may be {@code q}
     * @return {@code true} if the swing or the twist was clamped
     */
    public static boolean clampSwingTwist(Quaterniondc q, Vector3dc axis, Vector3dc swingAxisA, double limitA,
            double limitB, double minTwist, double maxTwist, Quaterniond dest) {
        double qx = q.x(), qy = q.y(), qz = q.z(), qw = q.w();
        double ax = axis.x(), ay = axis.y(), az = axis.z();
        // twist: the projection of the vector part onto the axis
        double p = qx * ax + qy * ay + qz * az;
        double tx = p * ax, ty = p * ay, tz = p * az, tw = qw;
        double tn = java.lang.Math.sqrt(tx * tx + ty * ty + tz * tz + tw * tw);
        if (tn < 1e-12) { // a 180-degree swing: the twist is undefined; take none
            tx = 0.0;
            ty = 0.0;
            tz = 0.0;
            tw = 1.0;
        } else {
            tx /= tn;
            ty /= tn;
            tz /= tn;
            tw /= tn;
        }
        // swing = q · twist⁻¹
        double sx = qw * -tx + qx * tw + qy * -tz - qz * -ty;
        double sy = qw * -ty - qx * -tz + qy * tw + qz * -tx;
        double sz = qw * -tz + qx * -ty - qy * -tx + qz * tw;
        double sw = qw * tw - qx * -tx - qy * -ty - qz * -tz;
        boolean clamped = false;
        // twist angle
        double tp = tx * ax + ty * ay + tz * az, twv = tw;
        if (twv < 0.0) {
            tp = -tp;
            twv = -twv;
        }
        double theta = 2.0 * AccurateMath.atan2(tp, twv);
        double ct = java.lang.Math.max(minTwist, java.lang.Math.min(maxTwist, theta));
        if (ct != theta) {
            clamped = true;
            double h = 0.5 * ct;
            double sh = AccurateMath.sin(h);
            tx = ax * sh;
            ty = ay * sh;
            tz = az * sh;
            tw = AccurateMath.cos(h);
        }
        // swing as a rotation vector in the plane perpendicular to the axis
        if (sw < 0.0) {
            sx = -sx;
            sy = -sy;
            sz = -sz;
            sw = -sw;
        }
        double sl = java.lang.Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl > 0.0) {
            double angle = 2.0 * AccurateMath.atan2(sl, sw);
            double vx = sx / sl * angle, vy = sy / sl * angle, vz = sz / sl * angle;
            double bx = ay * swingAxisA.z() - az * swingAxisA.y(), by = az * swingAxisA.x() - ax * swingAxisA.z();
            double bz = ax * swingAxisA.y() - ay * swingAxisA.x();
            double va = vx * swingAxisA.x() + vy * swingAxisA.y() + vz * swingAxisA.z();
            double vb = vx * bx + vy * by + vz * bz;
            double e = (va / limitA) * (va / limitA) + (vb / limitB) * (vb / limitB);
            if (e > 1.0) {
                clamped = true;
                double k = 1.0 / java.lang.Math.sqrt(e);
                double na = angle * k;
                double hh = 0.5 * na;
                double s = AccurateMath.sin(hh) / sl;
                sx *= s;
                sy *= s;
                sz *= s;
                sw = AccurateMath.cos(hh);
            }
        }
        // q' = swing · twist
        dest.set((sw * tx + sx * tw + sy * tz - sz * ty), (sw * ty - sx * tz + sy * tw + sz * tx),
                (sw * tz + sx * ty - sy * tx + sz * tw), (sw * tw - sx * tx - sy * ty - sz * tz));
        return clamped;
    }

    /**
     * Clamps {@code q} to a circular swing cone of half-angle {@code maxSwing} about {@code axis}, with unlimited
     * twist: {@link #clampSwingTwist} with equal limits.
     *
     * @param q the joint's local rotation, unit length
     * @param axis the bone axis, unit length
     * @param anyPerpendicular any unit vector perpendicular to {@code axis}
     * @param maxSwing the cone half-angle in radians, greater than 0
     * @param dest receives the clamped rotation; may be {@code q}
     * @return {@code true} if the swing was clamped
     */
    public static boolean clampCone(Quaterniondc q, Vector3dc axis, Vector3dc anyPerpendicular, double maxSwing,
            Quaterniond dest) {
        return clampSwingTwist(q, axis, anyPerpendicular, maxSwing, maxSwing, -java.lang.Math.PI,
                java.lang.Math.PI, dest);
    }

    /**
     * Hinge about unit {@code axis}: removes all swing and clamps the remaining rotation angle to
     * {@code [minAngle, maxAngle]}. The result is always a pure rotation about {@code axis}.
     *
     * @param q the joint's local rotation, unit length
     * @param axis the hinge axis, unit length
     * @param minAngle the smallest hinge angle, in radians
     * @param maxAngle the largest hinge angle, in radians
     * @param dest receives the clamped rotation; may be {@code q}
     * @return {@code true} if the result differs from {@code q} ({@code |q · dest| < 1 − 1e-6})
     */
    public static boolean clampHinge(Quaterniondc q, Vector3dc axis, double minAngle, double maxAngle, Quaterniond dest) {
        double p = q.x() * (double) axis.x() + q.y() * (double) axis.y() + q.z() * (double) axis.z();
        double w = q.w();
        double n = java.lang.Math.sqrt(p * p + w * w);
        double theta = n < 1e-12 ? 0.0 : 2.0 * AccurateMath.atan2(w < 0.0 ? -p : p, java.lang.Math.abs(w));
        double c = java.lang.Math.max(minAngle, java.lang.Math.min(maxAngle, theta));
        double h = 0.5 * c, s = AccurateMath.sin(h);
        double ox = q.x(), oy = q.y(), oz = q.z(), ow = q.w();
        dest.set((axis.x() * s), (axis.y() * s), (axis.z() * s), AccurateMath.cos(h));
        double d = java.lang.Math.abs(ox * dest.x + oy * dest.y + oz * dest.z + ow * dest.w);
        return d < 1.0 - 1e-6;
    }
}
