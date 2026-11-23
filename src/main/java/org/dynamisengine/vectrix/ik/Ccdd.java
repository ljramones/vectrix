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

/**
 * Cyclic coordinate descent on a chain of joint positions ({@code positions}, xyz per joint, updated in place; joint
 * 0 is the fixed root), double precision. Each sweep turns every joint, from
 * the last bone to the root, so that the end joint points at the target, rotating everything beyond it rigidly (bone
 * lengths are preserved). {@code maxStepAngle} (radians, {@code ≤ 0} for none) caps each joint's turn per sweep.
 * {@code deltasOut} (xyzw per bone, {@code count − 1} entries; null to skip; initialize to identities) accumulates each
 * bone's total world-space rotation, every turn at its own or an earlier joint, pre-multiplied in order, so
 * {@code delta_k} applied to bone {@code k}'s original vector gives its final vector, twist included.
 *
 * @since 2.4.0
 */
public final class Ccdd {
    private Ccdd() {
    }

    /**
     * Runs CCD sweeps until the end joint is within {@code tolerance} of the target or {@code maxIterations} sweeps
     * have run. Uses per-thread scratch arrays, which are allocated on the first call in each thread.
     *
     * @param positions the joint positions, 3 doubles per joint from index 0; updated in place
     * @param count the number of joints
     * @param targetX the x coordinate of the target
     * @param targetY the y coordinate of the target
     * @param targetZ the z coordinate of the target
     * @param tolerance the end-joint distance at which to stop
     * @param maxIterations the maximum number of sweeps
     * @param maxStepAngle the largest turn of one joint in one sweep, in radians, or {@code ≤ 0} for no cap
     * @param deltasOut accumulates each bone's world-space rotation, 4 doubles per bone for {@code count − 1} bones,
     *        initialized by the caller (normally to identities); or null
     * @return the final distance from the end joint to the target
     */
    public static double solve(double[] positions, int count, double targetX, double targetY, double targetZ,
            double tolerance, int maxIterations, double maxStepAngle, double[] deltasOut) {
        double[] p = positions;
        int last = count - 1;
        double[][] scratch = IkMath.SCRATCH.get();
        double[] q = scratch[1], d = scratch[2], v = scratch[4], tmp = scratch[5];
        for (int it = 0; it < maxIterations; it++) {
            if (error(p, last, targetX, targetY, targetZ) <= tolerance) {
                break;
            }
            for (int i = last - 1; i >= 0; i--) {
                double ox = p[3 * i], oy = p[3 * i + 1], oz = p[3 * i + 2];
                IkMath.rotationTo(p[3 * last] - ox, p[3 * last + 1] - oy, p[3 * last + 2] - oz, targetX - ox,
                        targetY - oy, targetZ - oz, q);
                if (maxStepAngle > 0.0) {
                    IkMath.toRotationVector(q, v);
                    double ang = java.lang.Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                    if (ang > maxStepAngle) {
                        double k = maxStepAngle / ang;
                        IkMath.fromRotationVector(v[0] * k, v[1] * k, v[2] * k, q);
                    }
                }
                for (int j = i + 1; j <= last; j++) {
                    IkMath.rotate(q, p[3 * j] - ox, p[3 * j + 1] - oy, p[3 * j + 2] - oz, tmp, 0);
                    p[3 * j] = (ox + tmp[0]);
                    p[3 * j + 1] = (oy + tmp[1]);
                    p[3 * j + 2] = (oz + tmp[2]);
                }
                if (deltasOut != null) { // every bone from i on turned by q
                    for (int k = i; k < last; k++) {
                        for (int c = 0; c < 4; c++) {
                            d[c] = deltasOut[4 * k + c];
                        }
                        IkMath.mul(q, d, d);
                        for (int c = 0; c < 4; c++) {
                            deltasOut[4 * k + c] = d[c];
                        }
                    }
                }
            }
        }
        return error(p, last, targetX, targetY, targetZ);
    }

    private static double error(double[] p, int last, double tx, double ty, double tz) {
        double ex = p[3 * last] - (double) tx, ey = p[3 * last + 1] - (double) ty, ez = p[3 * last + 2] - (double) tz;
        return java.lang.Math.sqrt(ex * ex + ey * ey + ez * ez);
    }
}
