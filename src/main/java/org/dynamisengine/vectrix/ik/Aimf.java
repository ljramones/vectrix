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

import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Aim (look-at) rotations (single-precision interface, double-precision evaluation): the world-space rotation that
 * turns an object's current aim axis toward a target direction while keeping its up axis as close as possible to a
 * world up direction (in the plane of the target direction and world up). Built from two orthonormal frames, with the
 * rotation matrix converted to a quaternion by Shepperd's stable method.
 *
 * @since 2.4.0
 */
public final class Aimf {
    private Aimf() {
    }

    /**
     * The delta rotation {@code R} with {@code R · aimAxis ∥ toTarget}, and {@code R · upAxis} in the half-plane of
     * {@code worldUp} about the new aim direction. If {@code toTarget} is parallel to {@code worldUp}, the roll is the
     * minimal one (shortest arc from the current aim); the same fallback applies when {@code upAxis} is parallel to
     * {@code aimAxis}, and a zero {@code aimAxis} or {@code toTarget} gives the identity. All vectors in world space;
     * none need be unit. Uses per-thread scratch arrays, which are allocated on the first call in each thread.
     *
     * @param aimAxis the object's current aim direction
     * @param upAxis the object's current up direction, not parallel to {@code aimAxis}
     * @param toTarget the direction from the object to the target
     * @param worldUp the world up direction to keep {@code upAxis} close to
     * @param dest receives the unit delta rotation, to pre-multiply onto the object's world rotation
     * @return {@code dest}
     */
    public static Quaternionf aim(Vector3fc aimAxis, Vector3fc upAxis, Vector3fc toTarget, Vector3fc worldUp,
            Quaternionf dest) {
        double[][] frames = FRAMES.get();
        double[] f0 = frames[0], f1 = frames[1], m = frames[2];
        boolean ok0 = frame(aimAxis.x(), aimAxis.y(), aimAxis.z(), upAxis.x(), upAxis.y(), upAxis.z(), f0);
        boolean ok1 = frame(toTarget.x(), toTarget.y(), toTarget.z(), worldUp.x(), worldUp.y(), worldUp.z(), f1);
        double[] q = IkMath.SCRATCH.get()[1];
        if (!ok0 || !ok1) {
            IkMath.rotationTo(aimAxis.x(), aimAxis.y(), aimAxis.z(), toTarget.x(), toTarget.y(), toTarget.z(), q);
            return dest.set((float) q[0], (float) q[1], (float) q[2], (float) q[3]);
        }
        // R = F1 · F0ᵀ, frames as columns (aim, up, side)
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                m[3 * r + c] = f1[r] * f0[c] + f1[3 + r] * f0[3 + c] + f1[6 + r] * f0[6 + c];
            }
        }
        shepperd(m, q);
        return dest.set((float) q[0], (float) q[1], (float) q[2], (float) q[3]);
    }

    private static final ThreadLocal<double[][]> FRAMES = ThreadLocal.withInitial(() -> new double[][] {new double[9],
        new double[9], new double[9]});

    /** Orthonormal frame (aim, up⊥, aim × up⊥) into f (three column vectors); false if degenerate. */
    private static boolean frame(double ax, double ay, double az, double ux, double uy, double uz, double[] f) {
        double al = java.lang.Math.sqrt(ax * ax + ay * ay + az * az);
        if (al == 0.0) {
            return false;
        }
        ax /= al;
        ay /= al;
        az /= al;
        double k = ux * ax + uy * ay + uz * az;
        ux -= k * ax;
        uy -= k * ay;
        uz -= k * az;
        double ul = java.lang.Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1e-12 * java.lang.Math.max(1.0, java.lang.Math.abs(k))) {
            return false;
        }
        ux /= ul;
        uy /= ul;
        uz /= ul;
        f[0] = ax;
        f[1] = ay;
        f[2] = az;
        f[3] = ux;
        f[4] = uy;
        f[5] = uz;
        f[6] = ay * uz - az * uy;
        f[7] = az * ux - ax * uz;
        f[8] = ax * uy - ay * ux;
        return true;
    }

    /** Rotation matrix (row-major) to unit quaternion, choosing the largest diagonal term for stability. */
    static void shepperd(double[] m, double[] q) {
        double tr = m[0] + m[4] + m[8];
        if (tr > 0.0) {
            double s = 2.0 * java.lang.Math.sqrt(1.0 + tr);
            q[3] = 0.25 * s;
            q[0] = (m[7] - m[5]) / s;
            q[1] = (m[2] - m[6]) / s;
            q[2] = (m[3] - m[1]) / s;
        } else if (m[0] > m[4] && m[0] > m[8]) {
            double s = 2.0 * java.lang.Math.sqrt(1.0 + m[0] - m[4] - m[8]);
            q[3] = (m[7] - m[5]) / s;
            q[0] = 0.25 * s;
            q[1] = (m[1] + m[3]) / s;
            q[2] = (m[2] + m[6]) / s;
        } else if (m[4] > m[8]) {
            double s = 2.0 * java.lang.Math.sqrt(1.0 + m[4] - m[0] - m[8]);
            q[3] = (m[2] - m[6]) / s;
            q[0] = (m[1] + m[3]) / s;
            q[1] = 0.25 * s;
            q[2] = (m[5] + m[7]) / s;
        } else {
            double s = 2.0 * java.lang.Math.sqrt(1.0 + m[8] - m[0] - m[4]);
            q[3] = (m[3] - m[1]) / s;
            q[0] = (m[2] + m[6]) / s;
            q[1] = (m[5] + m[7]) / s;
            q[2] = 0.25 * s;
        }
        double n = java.lang.Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        q[0] /= n;
        q[1] /= n;
        q[2] /= n;
        q[3] /= n;
    }
}
