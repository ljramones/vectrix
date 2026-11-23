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

import org.dynamisengine.vectrix.core.Math;
import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Position-based dynamics (PBD) constraint projections (double precision). Each method computes the position
 * corrections that move particles toward satisfying one constraint; the caller's solver applies them and iterates. An
 * inverse mass of 0 pins a particle. Degenerate configurations give zero corrections; the degeneracy tests are
 * relative to the configuration's own size, so they behave the same at any scale. No method allocates.
 *
 * @since 1.10.12
 */
public final class PbdConstraintsd {
    private static final double EPS = 1E-6;
    private static final int SHAPE_MATCH_MAX_ITER = 32;

    private PbdConstraintsd() {
    }

    /**
     * Returns the correction for particle A only, from the distance constraint
     * {@code C = |posA − posB| − restLength}: {@code dA = −k wA C / (wA + wB) · (posA − posB) / |posA − posB|}.
     * For B's correction, call again with A and B (and their inverse masses) swapped. The correction is zero when both
     * particles are pinned, when they coincide, or when they are closer than {@code 1e-6 · |restLength|} (the
     * direction between them is then rounding noise). {@code dest} may be either position.
     *
     * @param posA the position of particle A
     * @param invMassA the inverse mass {@code wA} of A (0 = pinned)
     * @param posB the position of particle B
     * @param invMassB the inverse mass {@code wB} of B (0 = pinned)
     * @param restLength the rest distance
     * @param stiffness {@code k} in [0,1]; 1 solves the linearized constraint in one step
     * @param dest receives the correction for A
     * @return {@code dest}
     */
    public static Vector3d distanceConstraint(
            Vector3dc posA, double invMassA,
            Vector3dc posB, double invMassB,
            double restLength, double stiffness,
            Vector3d dest) {
        double dx = posA.x() - posB.x();
        double dy = posA.y() - posB.y();
        double dz = posA.z() - posB.z();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double w = invMassA + invMassB;
        if (!(w > 0.0) || len == 0.0 || len <= EPS * java.lang.Math.abs(restLength)) {
            return dest.set(0.0, 0.0, 0.0);
        }
        double c = len - restLength;
        double scale = -stiffness * invMassA * c / (w * len);
        return dest.set(dx * scale, dy * scale, dz * scale);
    }

    /**
     * Equal-mass form of
     * {@link #bendConstraint(Vector3dc, double, Vector3dc, double, Vector3dc, double, double, double, Vector3d, Vector3d,
     * Vector3d) bendConstraint} (all inverse masses 1).
     *
     * @param p0 the first end point
     * @param p1 the corner point
     * @param p2 the second end point
     * @param restAngle the rest angle at {@code p1}, in radians in [0, π]
     * @param stiffness in [0,1]; 1 solves the linearized constraint in one step
     * @param d0 receives the correction for {@code p0}
     * @param d1 receives the correction for {@code p1}
     * @param d2 receives the correction for {@code p2}
     */
    public static void bendConstraint(
            Vector3dc p0, Vector3dc p1, Vector3dc p2,
            double restAngle, double stiffness,
            Vector3d d0, Vector3d d1, Vector3d d2) {
        bendConstraint(p0, 1.0, p1, 1.0, p2, 1.0, restAngle, stiffness, d0, d1, d2);
    }

    /**
     * Three-point bending constraint on the angle at {@code p1} between the edges {@code p1->p0} and
     * {@code p1->p2}: {@code C = angle - restAngle}. Writes the position-based-dynamics corrections
     * {@code d_i = -k C w_i grad_i(C) / sum_j w_j |grad_j(C)|^2}, which move the angle toward its rest value.
     * <p>
     * All corrections are zero when an edge is degenerate (zero, or shorter than {@code 1e-6} times the other edge) or
     * the edges are (anti)parallel (the sine of the angle between them below {@code 1e-6}), because the gradient
     * direction is undefined there, and when every particle is pinned. The tests are relative, so they hold at any
     * scale. Inputs are read before any output is written, so outputs may alias inputs.
     *
     * @param p0 the first end point
     * @param invMass0 inverse mass of {@code p0} (0 = pinned)
     * @param p1 the corner point
     * @param invMass1 inverse mass of {@code p1} (0 = pinned)
     * @param p2 the second end point
     * @param invMass2 inverse mass of {@code p2} (0 = pinned)
     * @param restAngle the rest angle at {@code p1}, in radians in [0, π]
     * @param stiffness in [0,1]; 1 solves the linearized constraint in one step
     * @param d0 receives the correction for {@code p0}
     * @param d1 receives the correction for {@code p1}
     * @param d2 receives the correction for {@code p2}
     */
    public static void bendConstraint(
            Vector3dc p0, double invMass0, Vector3dc p1, double invMass1, Vector3dc p2, double invMass2,
            double restAngle, double stiffness,
            Vector3d d0, Vector3d d1, Vector3d d2) {
        double v0x = p0.x() - p1.x(), v0y = p0.y() - p1.y(), v0z = p0.z() - p1.z();
        double v1x = p2.x() - p1.x(), v1y = p2.y() - p1.y(), v1z = p2.z() - p1.z();
        double l0 = Math.sqrt(v0x * v0x + v0y * v0y + v0z * v0z);
        double l1 = Math.sqrt(v1x * v1x + v1y * v1y + v1z * v1z);
        double lmax = java.lang.Math.max(l0, l1);
        if (!(l0 > EPS * lmax) || !(l1 > EPS * lmax)) {
            zero(d0, d1, d2);
            return;
        }
        double n0x = v0x / l0, n0y = v0y / l0, n0z = v0z / l0;
        double n1x = v1x / l1, n1y = v1y / l1, n1z = v1z / l1;
        double ax = n0y * n1z - n0z * n1y;
        double ay = n0z * n1x - n0x * n1z;
        double az = n0x * n1y - n0y * n1x;
        double al = Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < EPS) {
            zero(d0, d1, d2);
            return;
        }
        double angle = org.dynamisengine.vectrix.core.AccurateMath.atan2(al, n0x * n1x + n0y * n1y + n0z * n1z);
        double error = angle - restAngle;
        ax /= al;
        ay /= al;
        az /= al;
        // grad_p0(angle) = -(a x n0) / l0, grad_p2(angle) = (a x n1) / l1, grad_p1 = -(grad_p0 + grad_p2)
        double g0x = -(ay * n0z - az * n0y) / l0;
        double g0y = -(az * n0x - ax * n0z) / l0;
        double g0z = -(ax * n0y - ay * n0x) / l0;
        double g2x = (ay * n1z - az * n1y) / l1;
        double g2y = (az * n1x - ax * n1z) / l1;
        double g2z = (ax * n1y - ay * n1x) / l1;
        double g1x = -(g0x + g2x), g1y = -(g0y + g2y), g1z = -(g0z + g2z);
        double denom = invMass0 * (g0x * g0x + g0y * g0y + g0z * g0z)
                + invMass1 * (g1x * g1x + g1y * g1y + g1z * g1z)
                + invMass2 * (g2x * g2x + g2y * g2y + g2z * g2z);
        // every gradient has length at least about 1 / max(l0, l1): compare with that scale, not with a constant
        double gradScale = (invMass0 + invMass1 + invMass2) * (1.0 / (l0 * l0) + 1.0 / (l1 * l1));
        if (!(denom > EPS * gradScale)) {
            zero(d0, d1, d2);
            return;
        }
        double s = -stiffness * error / denom;
        d0.set(g0x * s * invMass0, g0y * s * invMass0, g0z * s * invMass0);
        d1.set(g1x * s * invMass1, g1y * s * invMass1, g1z * s * invMass1);
        d2.set(g2x * s * invMass2, g2y * s * invMass2, g2z * s * invMass2);
    }

    /**
     * Equal-mass form of
     * {@link #volumeConstraint(Vector3dc, double, Vector3dc, double, Vector3dc, double, Vector3dc, double, double, double,
     * Vector3d, Vector3d, Vector3d, Vector3d) volumeConstraint} (all inverse masses 1).
     *
     * @param p0 the first vertex of the tetrahedron
     * @param p1 the second vertex
     * @param p2 the third vertex
     * @param p3 the fourth vertex
     * @param restVolume the signed rest volume
     * @param stiffness in [0,1]; 1 solves the linearized constraint in one step
     * @param d0 receives the correction for {@code p0}
     * @param d1 receives the correction for {@code p1}
     * @param d2 receives the correction for {@code p2}
     * @param d3 receives the correction for {@code p3}
     * @return the current signed volume
     */
    public static double volumeConstraint(
            Vector3dc p0, Vector3dc p1, Vector3dc p2, Vector3dc p3,
            double restVolume, double stiffness,
            Vector3d d0, Vector3d d1, Vector3d d2, Vector3d d3) {
        return volumeConstraint(p0, 1.0, p1, 1.0, p2, 1.0, p3, 1.0, restVolume, stiffness, d0, d1, d2, d3);
    }

    /**
     * Tetrahedron volume constraint {@code C = V - restVolume}, with the signed volume
     * {@code V = (p1-p0) . ((p2-p0) x (p3-p0)) / 6}. Writes the corrections
     * {@code d_i = -k C w_i grad_i(V) / sum_j w_j |grad_j(V)|^2}. The corrections are mass-weighted, so they
     * conserve linear momentum. Inputs are read before any output is written, so outputs may alias inputs. A flat
     * tetrahedron is pushed back toward its rest volume; the corrections are zero only when the volume gradient
     * vanishes (all four points on one line, to {@code 1e-6} relative to the tetrahedron's size) or every vertex is
     * pinned. The test is relative, so it holds at any scale.
     *
     * @param p0 the first vertex of the tetrahedron
     * @param invMass0 inverse mass of {@code p0} (0 = pinned)
     * @param p1 the second vertex
     * @param invMass1 inverse mass of {@code p1} (0 = pinned)
     * @param p2 the third vertex
     * @param invMass2 inverse mass of {@code p2} (0 = pinned)
     * @param p3 the fourth vertex
     * @param invMass3 inverse mass of {@code p3} (0 = pinned)
     * @param restVolume the signed rest volume
     * @param stiffness in [0,1]; 1 solves the linearized constraint in one step
     * @param d0 receives the correction for {@code p0}
     * @param d1 receives the correction for {@code p1}
     * @param d2 receives the correction for {@code p2}
     * @param d3 receives the correction for {@code p3}
     * @return the current signed volume
     */
    public static double volumeConstraint(
            Vector3dc p0, double invMass0, Vector3dc p1, double invMass1,
            Vector3dc p2, double invMass2, Vector3dc p3, double invMass3,
            double restVolume, double stiffness,
            Vector3d d0, Vector3d d1, Vector3d d2, Vector3d d3) {
        double x10 = p1.x() - p0.x(), y10 = p1.y() - p0.y(), z10 = p1.z() - p0.z();
        double x20 = p2.x() - p0.x(), y20 = p2.y() - p0.y(), z20 = p2.z() - p0.z();
        double x30 = p3.x() - p0.x(), y30 = p3.y() - p0.y(), z30 = p3.z() - p0.z();
        final double sixth = 1.0 / 6.0;
        // grad_p1 = (p2-p0) x (p3-p0) / 6, grad_p2 = (p3-p0) x (p1-p0) / 6, grad_p3 = (p1-p0) x (p2-p0) / 6
        double g1x = (y20 * z30 - z20 * y30) * sixth, g1y = (z20 * x30 - x20 * z30) * sixth, g1z = (x20 * y30 - y20 * x30) * sixth;
        double g2x = (y30 * z10 - z30 * y10) * sixth, g2y = (z30 * x10 - x30 * z10) * sixth, g2z = (x30 * y10 - y30 * x10) * sixth;
        double g3x = (y10 * z20 - z10 * y20) * sixth, g3y = (z10 * x20 - x10 * z20) * sixth, g3z = (x10 * y20 - y10 * x20) * sixth;
        double g0x = -(g1x + g2x + g3x), g0y = -(g1y + g2y + g3y), g0z = -(g1z + g2z + g3z);
        double volume = x10 * g1x + y10 * g1y + z10 * g1z;
        double c = volume - restVolume;
        double denom = invMass0 * (g0x * g0x + g0y * g0y + g0z * g0z)
                + invMass1 * (g1x * g1x + g1y * g1y + g1z * g1z)
                + invMass2 * (g2x * g2x + g2y * g2y + g2z * g2z)
                + invMass3 * (g3x * g3x + g3y * g3y + g3z * g3z);
        // a gradient is an edge cross product / 6, so compare with (squared edge length / 6)^2, not with a constant
        double edges = (double) x10 * x10 + (double) y10 * y10 + (double) z10 * z10 + (double) x20 * x20
                + (double) y20 * y20 + (double) z20 * z20 + (double) x30 * x30 + (double) y30 * y30
                + (double) z30 * z30;
        double gradScale = ((double) invMass0 + invMass1 + invMass2 + invMass3) * (edges * edges / 36.0);
        if (!(denom > (double) EPS * EPS * gradScale)) {
            zero(d0, d1, d2);
            d3.set(0.0, 0.0, 0.0);
            return volume;
        }
        double s = -stiffness * c / denom;
        d0.set(g0x * s * invMass0, g0y * s * invMass0, g0z * s * invMass0);
        d1.set(g1x * s * invMass1, g1y * s * invMass1, g1z * s * invMass1);
        d2.set(g2x * s * invMass2, g2y * s * invMass2, g2z * s * invMass2);
        d3.set(g3x * s * invMass3, g3y * s * invMass3, g3z * s * invMass3);
        return volume;
    }

    /**
     * Shape-matching goal positions (Mueller et al. 2005). The rotation is the rotational part of the
     * polar decomposition of {@code A_pq = sum m_i (x_i - c)(x0_i - c0)^T}, extracted with the iterative
     * method of Mueller et al. 2016, "A Robust Method to Extract the Rotational Part of Deformations". The
     * iteration starts from a Gram-Schmidt estimate, so large rotations converge too. Goals are
     * {@code R (x0_i - c0) + c}, where {@code c} and {@code c0} are the mass-weighted centroids of the current and rest
     * positions. This method does not allocate.
     * <p>
     * If the total mass is not positive, the rotation is the identity and each goal is the current position. The
     * other degeneracy tests (a rank-deficient {@code A_pq}) are relative to the size of {@code A_pq}, so they hold at
     * any scale.
     * {@code goalPositions} may be {@code currentPositions}; its elements must not be null.
     *
     * @param currentPositions the current positions {@code x_i}
     * @param restPositions the rest positions {@code x0_i}
     * @param masses the particle masses {@code m_i}
     * @param rotationOut receives R (column-major, so {@code rotationOut.transform(v)} applies it)
     * @param goalPositions receives the goal position of each particle
     * @throws IllegalArgumentException if the four arrays differ in length
     */
    public static void shapeMatchingGoal(
            Vector3dc[] currentPositions,
            Vector3dc[] restPositions,
            double[] masses,
            Matrix3d rotationOut,
            Vector3d[] goalPositions) {
        int n = currentPositions.length;
        if (restPositions.length != n || masses.length != n || goalPositions.length != n) {
            throw new IllegalArgumentException("array lengths differ");
        }
        double massSum = 0.0;
        double ccx = 0.0, ccy = 0.0, ccz = 0.0;
        double rcx = 0.0, rcy = 0.0, rcz = 0.0;
        for (int i = 0; i < n; i++) {
            double m = masses[i];
            massSum += m;
            ccx += m * currentPositions[i].x();
            ccy += m * currentPositions[i].y();
            ccz += m * currentPositions[i].z();
            rcx += m * restPositions[i].x();
            rcy += m * restPositions[i].y();
            rcz += m * restPositions[i].z();
        }
        if (!(massSum > 0.0)) {
            rotationOut.identity();
            for (int i = 0; i < n; i++) {
                goalPositions[i].set(currentPositions[i]);
            }
            return;
        }
        double inv = 1.0 / massSum;
        ccx *= inv; ccy *= inv; ccz *= inv;
        rcx *= inv; rcy *= inv; rcz *= inv;

        // A_pq, element a<r><c> = sum m p_r q_c (row r, column c)
        double a00 = 0, a01 = 0, a02 = 0, a10 = 0, a11 = 0, a12 = 0, a20 = 0, a21 = 0, a22 = 0;
        for (int i = 0; i < n; i++) {
            double m = masses[i];
            double qx = restPositions[i].x() - rcx, qy = restPositions[i].y() - rcy, qz = restPositions[i].z() - rcz;
            double px = currentPositions[i].x() - ccx, py = currentPositions[i].y() - ccy, pz = currentPositions[i].z() - ccz;
            a00 += m * px * qx; a01 += m * px * qy; a02 += m * px * qz;
            a10 += m * py * qx; a11 += m * py * qy; a12 += m * py * qz;
            a20 += m * pz * qx; a21 += m * pz * qy; a22 += m * pz * qz;
        }

        // Thresholds below are relative to the size of A_pq, which scales with mass times length squared.
        double aNorm = java.lang.Math.sqrt(a00 * a00 + a01 * a01 + a02 * a02 + a10 * a10 + a11 * a11 + a12 * a12
                + a20 * a20 + a21 * a21 + a22 * a22);
        double tiny = 1e-12 * aNorm;
        // Initial estimate: Gram-Schmidt on the first two columns of A_pq (identity if degenerate).
        double qw = 1, qx = 0, qy = 0, qz = 0;
        double c0x = a00, c0y = a10, c0z = a20;
        double c1x = a01, c1y = a11, c1z = a21;
        double c0l = java.lang.Math.sqrt(c0x * c0x + c0y * c0y + c0z * c0z);
        if (c0l > tiny) {
            c0x /= c0l; c0y /= c0l; c0z /= c0l;
            double d = c0x * c1x + c0y * c1y + c0z * c1z;
            c1x -= d * c0x; c1y -= d * c0y; c1z -= d * c0z;
            double c1l = java.lang.Math.sqrt(c1x * c1x + c1y * c1y + c1z * c1z);
            if (c1l > tiny) {
                c1x /= c1l; c1y /= c1l; c1z /= c1l;
                double c2x = c0y * c1z - c0z * c1y, c2y = c0z * c1x - c0x * c1z, c2z = c0x * c1y - c0y * c1x;
                // rotation matrix (columns c0, c1, c2) -> quaternion (Shepperd)
                double tr = c0x + c1y + c2z;
                if (tr > 0) {
                    double t = java.lang.Math.sqrt(tr + 1.0) * 2;
                    qw = 0.25 * t; qx = (c1z - c2y) / t; qy = (c2x - c0z) / t; qz = (c0y - c1x) / t;
                } else if (c0x > c1y && c0x > c2z) {
                    double t = java.lang.Math.sqrt(1.0 + c0x - c1y - c2z) * 2;
                    qw = (c1z - c2y) / t; qx = 0.25 * t; qy = (c1x + c0y) / t; qz = (c2x + c0z) / t;
                } else if (c1y > c2z) {
                    double t = java.lang.Math.sqrt(1.0 + c1y - c0x - c2z) * 2;
                    qw = (c2x - c0z) / t; qx = (c1x + c0y) / t; qy = 0.25 * t; qz = (c2y + c1z) / t;
                } else {
                    double t = java.lang.Math.sqrt(1.0 + c2z - c0x - c1y) * 2;
                    qw = (c0y - c1x) / t; qx = (c2x + c0z) / t; qy = (c2y + c1z) / t; qz = 0.25 * t;
                }
            }
        }

        // Mueller 2016 iteration: rotate R toward A until sum_k R_k x A_k vanishes.
        double r00 = 1, r10 = 0, r20 = 0, r01 = 0, r11 = 1, r21 = 0, r02 = 0, r12 = 0, r22 = 1;
        for (int iter = 0; iter <= SHAPE_MATCH_MAX_ITER; iter++) {
            double ql = java.lang.Math.sqrt(qw * qw + qx * qx + qy * qy + qz * qz);
            qw /= ql; qx /= ql; qy /= ql; qz /= ql;
            r00 = 1 - 2 * (qy * qy + qz * qz); r10 = 2 * (qx * qy + qw * qz); r20 = 2 * (qx * qz - qw * qy);
            r01 = 2 * (qx * qy - qw * qz); r11 = 1 - 2 * (qx * qx + qz * qz); r21 = 2 * (qy * qz + qw * qx);
            r02 = 2 * (qx * qz + qw * qy); r12 = 2 * (qy * qz - qw * qx); r22 = 1 - 2 * (qx * qx + qy * qy);
            if (iter == SHAPE_MATCH_MAX_ITER) {
                break;
            }
            double ox = (r10 * a20 - r20 * a10) + (r11 * a21 - r21 * a11) + (r12 * a22 - r22 * a12);
            double oy = (r20 * a00 - r00 * a20) + (r21 * a01 - r01 * a21) + (r22 * a02 - r02 * a22);
            double oz = (r00 * a10 - r10 * a00) + (r01 * a11 - r11 * a01) + (r02 * a12 - r12 * a02);
            double dot = r00 * a00 + r10 * a10 + r20 * a20 + r01 * a01 + r11 * a11 + r21 * a21
                    + r02 * a02 + r12 * a12 + r22 * a22;
            double scale = 1.0 / (java.lang.Math.abs(dot) + 1.0e-9 * aNorm + Double.MIN_NORMAL);
            ox *= scale; oy *= scale; oz *= scale;
            double w = java.lang.Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (w < 1.0e-9) {
                break;
            }
            double h = 0.5 * w, sh = org.dynamisengine.vectrix.core.AccurateMath.sin(h) / w;
            double rw = org.dynamisengine.vectrix.core.AccurateMath.cos(h), rx = ox * sh, ry = oy * sh, rz = oz * sh;
            // q = r * q
            double nw = rw * qw - rx * qx - ry * qy - rz * qz;
            double nx = rw * qx + rx * qw + ry * qz - rz * qy;
            double ny = rw * qy - rx * qz + ry * qw + rz * qx;
            double nz = rw * qz + rx * qy - ry * qx + rz * qw;
            qw = nw; qx = nx; qy = ny; qz = nz;
        }
        rotationOut.set(
                r00, r10, r20,
                r01, r11, r21,
                r02, r12, r22);

        for (int i = 0; i < n; i++) {
            Vector3dc q = restPositions[i];
            double vx = q.x() - rcx, vy = q.y() - rcy, vz = q.z() - rcz;
            goalPositions[i].set(
                    rotationOut.m00 * vx + rotationOut.m10 * vy + rotationOut.m20 * vz + ccx,
                    rotationOut.m01 * vx + rotationOut.m11 * vy + rotationOut.m21 * vz + ccy,
                    rotationOut.m02 * vx + rotationOut.m12 * vy + rotationOut.m22 * vz + ccz);
        }
    }

    private static void zero(Vector3d a, Vector3d b, Vector3d c) {
        a.set(0.0, 0.0, 0.0);
        b.set(0.0, 0.0, 0.0);
        c.set(0.0, 0.0, 0.0);
    }
}
