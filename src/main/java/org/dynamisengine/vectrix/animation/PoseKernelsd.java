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
package org.dynamisengine.vectrix.animation;

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * Skeletal pose kernels over flat per-joint arrays (double precision), in the
 * layout Animis's {@code PoseBuffer} uses: translations {@code xyz}, rotations {@code xyzw} (unit quaternions) and
 * scales {@code xyz} per joint, with a parent index per joint ({@code −1} for roots, and every parent before its
 * children). No allocation; every kernel can work in place where noted.
 * <ul>
 * <li>{@link #localToModel} / {@link #modelToLocal}: forward kinematics and its inverse. Scale composes per component
 * ({@code S = S_parent ∘ S_local}), which is exact for uniform scale; non-uniform scale under rotation would shear,
 * which a TRS hierarchy cannot represent.</li>
 * <li>Quaternion array operations: {@link #mul}, {@link #normalize}, {@link #slerp} and {@link #nlerp} along the
 * shorter arc.</li>
 * <li>Blending: {@link #lerp3}, per-joint masked blends ({@link #slerpMasked}, {@link #lerp3Masked}), N-way weighted
 * blends ({@link #blendRotations}, {@link #blendVectors}), and additive layers ({@link #makeAdditiveRotations},
 * {@link #applyAdditiveRotations} with their vector counterparts).</li>
 * </ul>
 *
 * @since 2.4.0
 */
public final class PoseKernelsd {
    private PoseKernelsd() {
    }

    /**
     * Forward kinematics: computes model-space transforms from local ones, joint by joint in index order. Model arrays
     * may be the local arrays (in place). Pass {@code localS} and {@code modelS} both null for no scale; when
     * {@code localS} is null, {@code modelS} is not written.
     *
     * @param parents the parent index of each joint, {@code −1} for a root; every parent must precede its child
     * @param count the number of joints
     * @param localT the local translations, 3 doubles per joint
     * @param localR the local rotations, 4 doubles ({@code x, y, z, w}) per joint, unit length
     * @param localS the local scales, 3 doubles per joint, or null
     * @param modelT receives the model translations; may be {@code localT}
     * @param modelR receives the model rotations; may be {@code localR}
     * @param modelS receives the model scales; may be {@code localS}; must not be null when {@code localS} is not
     * @throws IllegalArgumentException if a parent index is not less than its joint's index; joints before it have
     *         already been written
     */
    public static void localToModel(int[] parents, int count, double[] localT, double[] localR, double[] localS,
            double[] modelT, double[] modelR, double[] modelS) {
        boolean scaled = localS != null;
        for (int i = 0; i < count; i++) {
            int p = parents[i];
            double tx = localT[3 * i], ty = localT[3 * i + 1], tz = localT[3 * i + 2];
            double rx = localR[4 * i], ry = localR[4 * i + 1], rz = localR[4 * i + 2], rw = localR[4 * i + 3];
            double sx = scaled ? localS[3 * i] : 1.0, sy = scaled ? localS[3 * i + 1] : 1.0;
            double sz = scaled ? localS[3 * i + 2] : 1.0;
            if (p >= 0) {
                if (p >= i) {
                    throw new IllegalArgumentException("parent " + p + " of joint " + i + " does not precede it");
                }
                double px = modelR[4 * p], py = modelR[4 * p + 1], pz = modelR[4 * p + 2], pw = modelR[4 * p + 3];
                double psx = scaled ? modelS[3 * p] : 1.0, psy = scaled ? modelS[3 * p + 1] : 1.0;
                double psz = scaled ? modelS[3 * p + 2] : 1.0;
                // translation: parent T + parent R (parent S ∘ local T)
                double vx = psx * tx, vy = psy * ty, vz = psz * tz;
                double cx = 2.0 * (py * vz - pz * vy), cy = 2.0 * (pz * vx - px * vz), cz = 2.0 * (px * vy - py * vx);
                tx = modelT[3 * p] + vx + pw * cx + (py * cz - pz * cy);
                ty = modelT[3 * p + 1] + vy + pw * cy + (pz * cx - px * cz);
                tz = modelT[3 * p + 2] + vz + pw * cz + (px * cy - py * cx);
                double nx = pw * rx + px * rw + py * rz - pz * ry;
                double ny = pw * ry - px * rz + py * rw + pz * rx;
                double nz = pw * rz + px * ry - py * rx + pz * rw;
                double nw = pw * rw - px * rx - py * ry - pz * rz;
                rx = nx;
                ry = ny;
                rz = nz;
                rw = nw;
                sx *= psx;
                sy *= psy;
                sz *= psz;
            }
            modelT[3 * i] = tx;
            modelT[3 * i + 1] = ty;
            modelT[3 * i + 2] = tz;
            modelR[4 * i] = rx;
            modelR[4 * i + 1] = ry;
            modelR[4 * i + 2] = rz;
            modelR[4 * i + 3] = rw;
            if (scaled) {
                modelS[3 * i] = sx;
                modelS[3 * i + 1] = sy;
                modelS[3 * i + 2] = sz;
            }
        }
    }

    /**
     * The inverse of {@link #localToModel}: computes local transforms from model-space ones. Children are processed
     * before their parents, so the local arrays may be the model arrays (in place). Pass {@code modelS} and
     * {@code localS} both null for no scale; when {@code modelS} is null, {@code localS} is not written.
     *
     * @param parents the parent index of each joint, {@code −1} for a root; every parent must precede its child
     * @param count the number of joints
     * @param modelT the model translations, 3 doubles per joint
     * @param modelR the model rotations, 4 doubles ({@code x, y, z, w}) per joint, unit length
     * @param modelS the model scales, 3 doubles per joint, nonzero, or null
     * @param localT receives the local translations; may be {@code modelT}
     * @param localR receives the local rotations; may be {@code modelR}
     * @param localS receives the local scales; may be {@code modelS}; must not be null when {@code modelS} is not
     * @throws IllegalArgumentException if a parent index is not less than its joint's index; joints after it have
     *         already been written
     */
    public static void modelToLocal(int[] parents, int count, double[] modelT, double[] modelR, double[] modelS,
            double[] localT, double[] localR, double[] localS) {
        boolean scaled = modelS != null;
        for (int i = count - 1; i >= 0; i--) {
            int p = parents[i];
            double tx = modelT[3 * i], ty = modelT[3 * i + 1], tz = modelT[3 * i + 2];
            double rx = modelR[4 * i], ry = modelR[4 * i + 1], rz = modelR[4 * i + 2], rw = modelR[4 * i + 3];
            double sx = scaled ? modelS[3 * i] : 1.0, sy = scaled ? modelS[3 * i + 1] : 1.0;
            double sz = scaled ? modelS[3 * i + 2] : 1.0;
            if (p >= 0) {
                if (p >= i) {
                    throw new IllegalArgumentException("parent " + p + " of joint " + i + " does not precede it");
                }
                // inverse parent rotation (conjugate of a unit quaternion)
                double px = -modelR[4 * p], py = -modelR[4 * p + 1], pz = -modelR[4 * p + 2], pw = modelR[4 * p + 3];
                double psx = scaled ? modelS[3 * p] : 1.0, psy = scaled ? modelS[3 * p + 1] : 1.0;
                double psz = scaled ? modelS[3 * p + 2] : 1.0;
                double vx = tx - modelT[3 * p], vy = ty - modelT[3 * p + 1], vz = tz - modelT[3 * p + 2];
                double cx = 2.0 * (py * vz - pz * vy), cy = 2.0 * (pz * vx - px * vz), cz = 2.0 * (px * vy - py * vx);
                tx = (vx + pw * cx + (py * cz - pz * cy)) / psx;
                ty = (vy + pw * cy + (pz * cx - px * cz)) / psy;
                tz = (vz + pw * cz + (px * cy - py * cx)) / psz;
                double nx = pw * rx + px * rw + py * rz - pz * ry;
                double ny = pw * ry - px * rz + py * rw + pz * rx;
                double nz = pw * rz + px * ry - py * rx + pz * rw;
                double nw = pw * rw - px * rx - py * ry - pz * rz;
                rx = nx;
                ry = ny;
                rz = nz;
                rw = nw;
                sx /= psx;
                sy /= psy;
                sz /= psz;
            }
            localT[3 * i] = tx;
            localT[3 * i + 1] = ty;
            localT[3 * i + 2] = tz;
            localR[4 * i] = rx;
            localR[4 * i + 1] = ry;
            localR[4 * i + 2] = rz;
            localR[4 * i + 3] = rw;
            if (scaled) {
                localS[3 * i] = sx;
                localS[3 * i + 1] = sy;
                localS[3 * i + 2] = sz;
            }
        }
    }

    /**
     * Multiplies quaternions pairwise: {@code out[i] = a[i] · b[i]} for {@code count} quaternions.
     *
     * @param a the left factors, 4 doubles per quaternion
     * @param b the right factors, 4 doubles per quaternion
     * @param out receives the products; may be {@code a} or {@code b}
     * @param count the number of quaternions
     */
    public static void mul(double[] a, double[] b, double[] out, int count) {
        for (int i = 0; i < count; i++) {
            int o = 4 * i;
            double ax = a[o], ay = a[o + 1], az = a[o + 2], aw = a[o + 3];
            double bx = b[o], by = b[o + 1], bz = b[o + 2], bw = b[o + 3];
            out[o] = (aw * bx + ax * bw + ay * bz - az * by);
            out[o + 1] = (aw * by - ax * bz + ay * bw + az * bx);
            out[o + 2] = (aw * bz + ax * by - ay * bx + az * bw);
            out[o + 3] = (aw * bw - ax * bx - ay * by - az * bz);
        }
    }

    /**
     * Normalizes {@code count} quaternions in place. A zero quaternion becomes the identity.
     *
     * @param q the quaternions, 4 doubles each
     * @param count the number of quaternions
     */
    public static void normalize(double[] q, int count) {
        for (int i = 0; i < count; i++) {
            int o = 4 * i;
            double x = q[o], y = q[o + 1], z = q[o + 2], w = q[o + 3];
            double n = java.lang.Math.sqrt(x * x + y * y + z * z + w * w);
            if (n == 0.0) {
                q[o] = 0.0;
                q[o + 1] = 0.0;
                q[o + 2] = 0.0;
                q[o + 3] = 1.0;
            } else {
                q[o] = (x / n);
                q[o + 1] = (y / n);
                q[o + 2] = (z / n);
                q[o + 3] = (w / n);
            }
        }
    }

    /** Per-joint slerp along the shorter arc at parameter {@code t} (cancellation-free angle; exact at t = 0, 1). */
    private static void slerpOne(double[] a, double[] b, int o, double t, double[] out) {
        double ax = a[o], ay = a[o + 1], az = a[o + 2], aw = a[o + 3];
        double bx = b[o], by = b[o + 1], bz = b[o + 2], bw = b[o + 3];
        if (ax * bx + ay * by + az * bz + aw * bw < 0.0) {
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
        }
        double d2 = (ax - bx) * (ax - bx) + (ay - by) * (ay - by) + (az - bz) * (az - bz) + (aw - bw) * (aw - bw);
        double s2 = (ax + bx) * (ax + bx) + (ay + by) * (ay + by) + (az + bz) * (az + bz) + (aw + bw) * (aw + bw);
        double om = 2.0 * AccurateMath.atan2(java.lang.Math.sqrt(d2), java.lang.Math.sqrt(s2));
        double wa, wb;
        if (om == 0.0) {
            wa = 1.0 - t;
            wb = t;
        } else {
            double s = AccurateMath.sin(om);
            wa = AccurateMath.sin((1.0 - t) * om) / s;
            wb = AccurateMath.sin(t * om) / s;
        }
        double x = wa * ax + wb * bx, y = wa * ay + wb * by, z = wa * az + wb * bz, w = wa * aw + wb * bw;
        double n = java.lang.Math.sqrt(x * x + y * y + z * z + w * w);
        out[o] = (x / n);
        out[o + 1] = (y / n);
        out[o + 2] = (z / n);
        out[o + 3] = (w / n);
    }

    private static void nlerpOne(double[] a, double[] b, int o, double t, double[] out) {
        double ax = a[o], ay = a[o + 1], az = a[o + 2], aw = a[o + 3];
        double bx = b[o], by = b[o + 1], bz = b[o + 2], bw = b[o + 3];
        double s = ax * bx + ay * by + az * bz + aw * bw < 0.0 ? -t : t;
        double x = (1.0 - t) * ax + s * bx, y = (1.0 - t) * ay + s * by;
        double z = (1.0 - t) * az + s * bz, w = (1.0 - t) * aw + s * bw;
        double n = java.lang.Math.sqrt(x * x + y * y + z * z + w * w);
        out[o] = (x / n);
        out[o + 1] = (y / n);
        out[o + 2] = (z / n);
        out[o + 3] = (w / n);
    }

    /**
     * Slerps {@code count} quaternion pairs at {@code t} along the shorter arc. The results are normalized, and
     * exact at {@code t = 0} and {@code t = 1}.
     *
     * @param a the start rotations, 4 doubles per quaternion, unit length
     * @param b the end rotations, 4 doubles per quaternion, unit length
     * @param t the interpolation parameter, 0 for {@code a} and 1 for {@code b}
     * @param out receives the interpolated rotations; may be {@code a} or {@code b}
     * @param count the number of quaternions
     */
    public static void slerp(double[] a, double[] b, double t, double[] out, int count) {
        for (int i = 0; i < count; i++) {
            slerpOne(a, b, 4 * i, t, out);
        }
    }

    /**
     * Normalized linear interpolation of {@code count} quaternion pairs along the shorter arc: cheaper than
     * {@link #slerp}, with non-constant angular speed.
     *
     * @param a the start rotations, 4 doubles per quaternion
     * @param b the end rotations, 4 doubles per quaternion
     * @param t the interpolation parameter, 0 for {@code a} and 1 for {@code b}
     * @param out receives the normalized results; may be {@code a} or {@code b}
     * @param count the number of quaternions
     */
    public static void nlerp(double[] a, double[] b, double t, double[] out, int count) {
        for (int i = 0; i < count; i++) {
            nlerpOne(a, b, 4 * i, t, out);
        }
    }

    /**
     * Slerps each joint along the shorter arc with its own parameter {@code t[i]} (a blend mask).
     *
     * @param a the start rotations, 4 doubles per joint, unit length
     * @param b the end rotations, 4 doubles per joint, unit length
     * @param t the per-joint parameters, one per joint
     * @param out receives the interpolated rotations; may be {@code a} or {@code b}
     * @param count the number of joints
     */
    public static void slerpMasked(double[] a, double[] b, double[] t, double[] out, int count) {
        for (int i = 0; i < count; i++) {
            slerpOne(a, b, 4 * i, t[i], out);
        }
    }

    /**
     * Interpolates 3-vectors linearly: {@code out = a + t (b − a)} for {@code count} vectors.
     *
     * @param a the start vectors, 3 doubles each
     * @param b the end vectors, 3 doubles each
     * @param t the interpolation parameter, 0 for {@code a} and 1 for {@code b}
     * @param out receives the results; may be {@code a} or {@code b}
     * @param count the number of vectors
     */
    public static void lerp3(double[] a, double[] b, double t, double[] out, int count) {
        for (int i = 0; i < 3 * count; i++) {
            out[i] = (a[i] + t * ((double) b[i] - a[i]));
        }
    }

    /**
     * Interpolates each joint's 3-vector linearly with its own parameter {@code t[i]} (a blend mask).
     *
     * @param a the start vectors, 3 doubles per joint
     * @param b the end vectors, 3 doubles per joint
     * @param t the per-joint parameters, one per joint
     * @param out receives the results; may be {@code a} or {@code b}
     * @param count the number of joints
     */
    public static void lerp3Masked(double[] a, double[] b, double[] t, double[] out, int count) {
        for (int i = 0; i < count; i++) {
            double w = t[i];
            for (int c = 3 * i; c < 3 * i + 3; c++) {
                out[c] = (a[c] + w * ((double) b[c] - a[c]));
            }
        }
    }

    /**
     * N-way weighted rotation blend: per joint, the normalized weighted sum of the poses' quaternions with each
     * aligned to the first pose's hemisphere (weights need not sum to 1; a joint with zero total weight takes the
     * first pose). This is the standard (normalized linear) blend, not the geodesic mean; for that per joint use
     * {@link QuaternionCurvesd#weightedMean}.
     *
     * @param poses the rotation arrays of the poses, 4 doubles per joint each; the first {@code poseCount} are used
     * @param weights the weight of each pose
     * @param poseCount the number of poses, at least 1
     * @param out receives the blended rotations; may be one of the poses
     * @param count the number of joints
     */
    public static void blendRotations(double[][] poses, double[] weights, int poseCount, double[] out, int count) {
        double[] first = poses[0];
        for (int i = 0; i < count; i++) {
            int o = 4 * i;
            double fx = first[o], fy = first[o + 1], fz = first[o + 2], fw = first[o + 3];
            double x = 0, y = 0, z = 0, w = 0;
            for (int k = 0; k < poseCount; k++) {
                double[] q = poses[k];
                double wk = weights[k];
                if (fx * q[o] + fy * q[o + 1] + fz * q[o + 2] + fw * q[o + 3] < 0.0) {
                    wk = -wk;
                }
                x += wk * q[o];
                y += wk * q[o + 1];
                z += wk * q[o + 2];
                w += wk * q[o + 3];
            }
            double n = java.lang.Math.sqrt(x * x + y * y + z * z + w * w);
            if (n == 0.0) {
                x = fx;
                y = fy;
                z = fz;
                w = fw;
                n = java.lang.Math.sqrt(x * x + y * y + z * z + w * w);
            }
            out[o] = (x / n);
            out[o + 1] = (y / n);
            out[o + 2] = (z / n);
            out[o + 3] = (w / n);
        }
    }

    /**
     * N-way weighted average of 3-vectors (translations or scales), divided by the weight sum. If the weights sum to
     * zero, the first pose is copied.
     *
     * @param poses the vector arrays of the poses, 3 doubles per joint each; the first {@code poseCount} are used
     * @param weights the weight of each pose
     * @param poseCount the number of poses, at least 1
     * @param out receives the blended vectors; may be one of the poses
     * @param count the number of joints
     */
    public static void blendVectors(double[][] poses, double[] weights, int poseCount, double[] out, int count) {
        double sum = 0.0;
        for (int k = 0; k < poseCount; k++) {
            sum += weights[k];
        }
        double inv = sum == 0.0 ? 0.0 : 1.0 / sum;
        for (int c = 0; c < 3 * count; c++) {
            double v = 0.0;
            for (int k = 0; k < poseCount; k++) {
                v += weights[k] * (double) poses[k][c];
            }
            out[c] = sum == 0.0 ? poses[0][c] : (v * inv);
        }
    }

    /**
     * Makes an additive rotation layer: {@code d = reference⁻¹ · pose} per joint, using the conjugate as the inverse.
     *
     * @param pose the rotations to encode, 4 doubles per joint
     * @param reference the reference rotations, 4 doubles per joint, unit length
     * @param out receives the additive rotations; may be {@code pose} or {@code reference}
     * @param count the number of joints
     */
    public static void makeAdditiveRotations(double[] pose, double[] reference, double[] out, int count) {
        for (int i = 0; i < count; i++) {
            int o = 4 * i;
            double ax = -reference[o], ay = -reference[o + 1], az = -reference[o + 2], aw = reference[o + 3];
            double bx = pose[o], by = pose[o + 1], bz = pose[o + 2], bw = pose[o + 3];
            out[o] = (aw * bx + ax * bw + ay * bz - az * by);
            out[o + 1] = (aw * by - ax * bz + ay * bw + az * bx);
            out[o + 2] = (aw * bz + ax * by - ay * bx + az * bw);
            out[o + 3] = (aw * bw - ax * bx - ay * by - az * bz);
        }
    }

    /**
     * Applies an additive rotation layer at {@code weight}: {@code out = base · slerp(identity, d, weight)} per joint,
     * taking {@code d} along the shorter arc. With {@code weight = 1} and the reference as {@code base}, this restores
     * the pose the layer was made from.
     *
     * @param base the base rotations, 4 doubles per joint
     * @param additive the additive rotations from {@link #makeAdditiveRotations}, 4 doubles per joint, unit length
     * @param weight the layer weight; 0 leaves {@code base} unchanged
     * @param out receives the layered rotations; may be {@code base} or {@code additive}
     * @param count the number of joints
     */
    public static void applyAdditiveRotations(double[] base, double[] additive, double weight, double[] out, int count) {
        for (int i = 0; i < count; i++) {
            int o = 4 * i;
            double dx = additive[o], dy = additive[o + 1], dz = additive[o + 2], dw = additive[o + 3];
            if (dw < 0.0) {
                dx = -dx;
                dy = -dy;
                dz = -dz;
                dw = -dw;
            }
            // d^weight: scale the rotation angle
            double s = java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
            double half = AccurateMath.atan2(s, dw);
            double k = s == 0.0 ? weight : AccurateMath.sin(weight * half) / s;
            double px = dx * k, py = dy * k, pz = dz * k, pw = AccurateMath.cos(weight * half);
            double bx = base[o], by = base[o + 1], bz = base[o + 2], bw = base[o + 3];
            out[o] = (bw * px + bx * pw + by * pz - bz * py);
            out[o + 1] = (bw * py - bx * pz + by * pw + bz * px);
            out[o + 2] = (bw * pz + bx * py - by * px + bz * pw);
            out[o + 3] = (bw * pw - bx * px - by * py - bz * pz);
        }
    }

    /**
     * Makes an additive vector layer (translations): {@code d = pose − reference} per joint.
     *
     * @param pose the vectors to encode, 3 doubles per joint
     * @param reference the reference vectors, 3 doubles per joint
     * @param out receives the differences; may be {@code pose} or {@code reference}
     * @param count the number of joints
     */
    public static void makeAdditiveVectors(double[] pose, double[] reference, double[] out, int count) {
        for (int i = 0; i < 3 * count; i++) {
            out[i] = ((double) pose[i] - reference[i]);
        }
    }

    /**
     * Applies an additive vector layer: {@code out = base + weight · d} per joint.
     *
     * @param base the base vectors, 3 doubles per joint
     * @param additive the differences from {@link #makeAdditiveVectors}, 3 doubles per joint
     * @param weight the layer weight
     * @param out receives the results; may be {@code base} or {@code additive}
     * @param count the number of joints
     */
    public static void applyAdditiveVectors(double[] base, double[] additive, double weight, double[] out, int count) {
        for (int i = 0; i < 3 * count; i++) {
            out[i] = (base[i] + (double) weight * additive[i]);
        }
    }
}
