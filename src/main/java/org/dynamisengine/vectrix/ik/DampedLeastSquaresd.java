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
 * Damped least-squares (Levenberg-Marquardt) IK for a chain of ball joints given as positions ({@code positions}, xyz
 * per joint; joint 0 is the root), double precision. With world-space rotation
 * vectors {@code ωᵢ} at each joint, the end-effector Jacobian satisfies
 * {@code J Jᵀ = Σ (|rᵢ|² I − rᵢ rᵢᵀ)}, {@code rᵢ = p_end − pᵢ}, a 3×3 matrix, so a step is one 3×3 Cholesky solve
 * {@code (J Jᵀ + λ² I) y = e} and {@code ωᵢ = Jᵢᵀ y = rᵢ × y}: no SVD, no allocation. Damping {@code λ} (metres) keeps
 * steps bounded near singular (straight) configurations.
 *
 * @since 2.4.0
 */
public final class DampedLeastSquaresd {
    private DampedLeastSquaresd() {
    }

    /**
     * One step: writes the rotation vector of each bone ({@code count − 1} entries, xyz, radians) to
     * {@code rotationsOut} without moving the chain; if any exceeds {@code maxStepAngle} (when positive), the whole
     * step is scaled down uniformly, preserving its direction. With {@code lambda = 0} a straight chain makes the
     * system singular and the step NaN; use a positive damping.
     *
     * @param positions the joint positions, 3 doubles per joint from index 0; not modified
     * @param count the number of joints, at least 2
     * @param targetX the x coordinate of the target
     * @param targetY the y coordinate of the target
     * @param targetZ the z coordinate of the target
     * @param lambda the damping {@code λ}, in the unit of the positions
     * @param maxStepAngle the largest rotation of any bone in this step, in radians, or {@code ≤ 0} for no cap
     * @param rotationsOut receives the world-space rotation vector of each bone, 3 doubles per bone for
     *        {@code count − 1} bones; for {@link #apply}
     * @return the end-effector distance to the target before the step
     */
    public static double step(double[] positions, int count, double targetX, double targetY, double targetZ, double lambda,
            double maxStepAngle, double[] rotationsOut) {
        double[] p = positions;
        int last = count - 1;
        double ex = p[3 * last], ey = p[3 * last + 1], ez = p[3 * last + 2];
        double rx0 = targetX - ex, ry0 = targetY - ey, rz0 = targetZ - ez;
        double a00 = 0, a01 = 0, a02 = 0, a11 = 0, a12 = 0, a22 = 0;
        for (int i = 0; i < last; i++) {
            double rx = ex - p[3 * i], ry = ey - p[3 * i + 1], rz = ez - p[3 * i + 2];
            double r2 = rx * rx + ry * ry + rz * rz;
            a00 += r2 - rx * rx;
            a11 += r2 - ry * ry;
            a22 += r2 - rz * rz;
            a01 -= rx * ry;
            a02 -= rx * rz;
            a12 -= ry * rz;
        }
        double l2 = (double) lambda * lambda;
        a00 += l2;
        a11 += l2;
        a22 += l2;
        // Cholesky of the symmetric positive definite 3x3
        double l00 = java.lang.Math.sqrt(a00);
        double l10 = a01 / l00, l20 = a02 / l00;
        double l11 = java.lang.Math.sqrt(a11 - l10 * l10);
        double l21 = (a12 - l20 * l10) / l11;
        double l22 = java.lang.Math.sqrt(a22 - l20 * l20 - l21 * l21);
        double z0 = rx0 / l00, z1 = (ry0 - l10 * z0) / l11, z2 = (rz0 - l20 * z0 - l21 * z1) / l22;
        double y2 = z2 / l22, y1 = (z1 - l21 * y2) / l11, y0 = (z0 - l10 * y1 - l20 * y2) / l00;
        // the step is scaled as a whole when any joint exceeds the cap: clamping joints one by one would change the
        // step's direction, which can cycle near singular configurations
        double maxAng = 0.0;
        for (int i = 0; i < last; i++) {
            double rx = ex - p[3 * i], ry = ey - p[3 * i + 1], rz = ez - p[3 * i + 2];
            double wx = ry * y2 - rz * y1, wy = rz * y0 - rx * y2, wz = rx * y1 - ry * y0;
            maxAng = java.lang.Math.max(maxAng, java.lang.Math.sqrt(wx * wx + wy * wy + wz * wz));
        }
        double k = maxStepAngle > 0.0 && maxAng > maxStepAngle ? maxStepAngle / maxAng : 1.0;
        for (int i = 0; i < last; i++) {
            double rx = ex - p[3 * i], ry = ey - p[3 * i + 1], rz = ez - p[3 * i + 2];
            rotationsOut[3 * i] = (k * (ry * y2 - rz * y1));
            rotationsOut[3 * i + 1] = (k * (rz * y0 - rx * y2));
            rotationsOut[3 * i + 2] = (k * (rx * y1 - ry * y0));
        }
        return java.lang.Math.sqrt(rx0 * rx0 + ry0 * ry0 + rz0 * rz0);
    }

    /**
     * Applies per-bone world rotation vectors to the chain (from the last bone to the root, each about its joint and
     * moving everything beyond it), preserving bone lengths. Uses per-thread scratch arrays, which are allocated on
     * the first call in each thread.
     *
     * @param positions the joint positions, 3 doubles per joint from index 0; updated in place
     * @param count the number of joints
     * @param rotations the world-space rotation vector of each bone (axis times angle in radians), 3 doubles per bone,
     *        as written by {@link #step}
     */
    public static void apply(double[] positions, int count, double[] rotations) {
        double[] p = positions;
        int last = count - 1;
        double[][] scratch = IkMath.SCRATCH.get();
        double[] q = scratch[3], tmp = scratch[6];
        for (int i = last - 1; i >= 0; i--) {
            IkMath.fromRotationVector(rotations[3 * i], rotations[3 * i + 1], rotations[3 * i + 2], q);
            double ox = p[3 * i], oy = p[3 * i + 1], oz = p[3 * i + 2];
            for (int j = i + 1; j <= last; j++) {
                IkMath.rotate(q, p[3 * j] - ox, p[3 * j + 1] - oy, p[3 * j + 2] - oz, tmp, 0);
                p[3 * j] = (ox + tmp[0]);
                p[3 * j + 1] = (oy + tmp[1]);
                p[3 * j + 2] = (oz + tmp[2]);
            }
        }
    }

    /**
     * Iterates {@link #step} and {@link #apply} until the error is at most {@code tolerance} or {@code maxIterations}
     * steps have been applied.
     *
     * @param positions the joint positions, 3 doubles per joint from index 0; updated in place
     * @param count the number of joints, at least 2
     * @param targetX the x coordinate of the target
     * @param targetY the y coordinate of the target
     * @param targetZ the z coordinate of the target
     * @param lambda the damping {@code λ}, in the unit of the positions, greater than 0
     * @param maxStepAngle the largest rotation of any bone per step, in radians, or {@code ≤ 0} for no cap
     * @param tolerance the end-effector distance at which to stop
     * @param maxIterations the maximum number of steps
     * @param scratchRotations working storage of at least {@code 3 (count − 1)} doubles; its contents are overwritten
     * @return the final distance from the end effector to the target
     */
    public static double solve(double[] positions, int count, double targetX, double targetY, double targetZ,
            double lambda, double maxStepAngle, double tolerance, int maxIterations, double[] scratchRotations) {
        for (int it = 0; it < maxIterations; it++) {
            double err = step(positions, count, targetX, targetY, targetZ, lambda, maxStepAngle, scratchRotations);
            if (err <= tolerance) {
                return err;
            }
            apply(positions, count, scratchRotations);
        }
        int last = count - 1;
        double ex = positions[3 * last] - (double) targetX, ey = positions[3 * last + 1] - (double) targetY;
        double ez = positions[3 * last + 2] - (double) targetZ;
        return java.lang.Math.sqrt(ex * ex + ey * ey + ez * ez);
    }
}
