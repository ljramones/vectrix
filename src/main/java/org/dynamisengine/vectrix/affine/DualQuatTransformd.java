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
package org.dynamisengine.vectrix.affine;

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaterniondc;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Dual quaternion rigid transform {@code real + ε dual} (single-precision storage; products, screw interpolation and
 * blending are evaluated in double precision). A unit dual quaternion has {@code |real| = 1} and
 * {@code real · dual = 0}; it rotates by {@code real} and then translates by {@code 2 dual real*}.
 *
 * @since 2.3.0
 */
public class DualQuatTransformd {
    public final Quaterniond real = new Quaterniond();
    public final Quaterniond dual = new Quaterniond(0, 0, 0, 0);

    public DualQuatTransformd() {
    }

    public DualQuatTransformd set(DualQuatTransformd other) {
        real.set(other.real);
        dual.set(other.dual);
        return this;
    }

    /** Sets the identity transform. @since 2.3.0 */
    public DualQuatTransformd setIdentity() {
        real.set(0.0, 0.0, 0.0, 1.0);
        dual.set(0.0, 0.0, 0.0, 0.0);
        return this;
    }

    /** Sets rotation (normalized) then translation. @since 2.3.0 */
    public DualQuatTransformd set(Quaterniondc rotation, Vector3dc translation) {
        return setRotationTranslation(rotation.x(), rotation.y(), rotation.z(), rotation.w(), translation.x(),
                translation.y(), translation.z());
    }

    private DualQuatTransformd setRotationTranslation(double rx, double ry, double rz, double rw, double tx,
            double ty, double tz) {
        double inv = 1.0 / java.lang.Math.sqrt(rx * rx + ry * ry + rz * rz + rw * rw);
        rx *= inv;
        ry *= inv;
        rz *= inv;
        rw *= inv;
        return store(rx, ry, rz, rw, 0.5 * (tx * rw + ty * rz - tz * ry), 0.5 * (-tx * rz + ty * rw + tz * rx),
                0.5 * (tx * ry - ty * rx + tz * rw), -0.5 * (tx * rx + ty * ry + tz * rz));
    }

    private DualQuatTransformd store(double rx, double ry, double rz, double rw, double dx, double dy, double dz,
            double dw) {
        real.set(rx, ry, rz, rw);
        dual.set(dx, dy, dz, dw);
        return this;
    }

    /**
     * Makes this an exact unit dual quaternion: divides by {@code |real|} and removes the dual part's component along
     * {@code real} (since 2.3.0; it previously only divided by an approximate norm).
     */
    public DualQuatTransformd normalize() {
        return normalizeInto(real.x, real.y, real.z, real.w, dual.x, dual.y, dual.z, dual.w, this);
    }

    private static DualQuatTransformd normalizeInto(double rx, double ry, double rz, double rw, double dx,
            double dy, double dz, double dw, DualQuatTransformd dest) {
        double inv = 1.0 / java.lang.Math.sqrt(rx * rx + ry * ry + rz * rz + rw * rw);
        rx *= inv;
        ry *= inv;
        rz *= inv;
        rw *= inv;
        dx *= inv;
        dy *= inv;
        dz *= inv;
        dw *= inv;
        double k = rx * dx + ry * dy + rz * dz + rw * dw;
        dest.store(rx, ry, rz, rw, dx - k * rx, dy - k * ry, dz - k * rz, dw - k * rw);
        return dest;
    }

    public Vector3d transformPosition(double x, double y, double z, Vector3d dest) {
        real.transform(x, y, z, dest);
        double rx = real.x, ry = real.y, rz = real.z, rw = real.w;
        double dx = dual.x, dy = dual.y, dz = dual.z, dw = dual.w;
        double tx = 2.0 * (-dw * rx + dx * rw - dy * rz + dz * ry);
        double ty = 2.0 * (-dw * ry + dx * rz + dy * rw - dz * rx);
        double tz = 2.0 * (-dw * rz - dx * ry + dy * rx + dz * rw);
        return dest.add(tx, ty, tz);
    }

    /** Rotates a direction (no translation). @since 2.3.0 */
    public Vector3d transformDirection(double x, double y, double z, Vector3d dest) {
        return real.transform(x, y, z, dest);
    }

    /** The rotation part. @since 2.3.0 */
    public Quaterniond getRotation(Quaterniond dest) {
        return dest.set(real);
    }

    /** The translation {@code 2 dual real*}. @since 2.3.0 */
    public Vector3d getTranslation(Vector3d dest) {
        double rx = real.x, ry = real.y, rz = real.z, rw = real.w;
        double dx = dual.x, dy = dual.y, dz = dual.z, dw = dual.w;
        return dest.set((2.0 * (-dw * rx + dx * rw - dy * rz + dz * ry)),
                (2.0 * (-dw * ry + dx * rz + dy * rw - dz * rx)),
                (2.0 * (-dw * rz - dx * ry + dy * rx + dz * rw)));
    }

    /** {@code dest = this · right} (apply {@code right} first); {@code dest} may alias either. @since 2.3.0 */
    public DualQuatTransformd mul(DualQuatTransformd right, DualQuatTransformd dest) {
        double ax = real.x, ay = real.y, az = real.z, aw = real.w;
        double bx = dual.x, by = dual.y, bz = dual.z, bw = dual.w;
        double cx = right.real.x, cy = right.real.y, cz = right.real.z, cw = right.real.w;
        double ex = right.dual.x, ey = right.dual.y, ez = right.dual.z, ew = right.dual.w;
        // real = a c, dual = a e + b c
        return dest.store(aw * cx + ax * cw + ay * cz - az * cy, aw * cy - ax * cz + ay * cw + az * cx,
                aw * cz + ax * cy - ay * cx + az * cw, aw * cw - ax * cx - ay * cy - az * cz,
                aw * ex + ax * ew + ay * ez - az * ey + bw * cx + bx * cw + by * cz - bz * cy,
                aw * ey - ax * ez + ay * ew + az * ex + bw * cy - bx * cz + by * cw + bz * cx,
                aw * ez + ax * ey - ay * ex + az * ew + bw * cz + bx * cy - by * cx + bz * cw,
                aw * ew - ax * ex - ay * ey - az * ez + bw * cw - bx * cx - by * cy - bz * cz);
    }

    /** Inverse of a unit dual quaternion (its quaternion conjugate); {@code dest} may alias this. @since 2.3.0 */
    public DualQuatTransformd invert(DualQuatTransformd dest) {
        return dest.store(-real.x, -real.y, -real.z, real.w, -dual.x, -dual.y, -dual.z, dual.w);
    }

    /**
     * Screw linear interpolation (Kavan et al. 2006): the constant-velocity screw motion from this ({@code t = 0})
     * to {@code other} ({@code t = 1}) along the shorter rotation. Computed in closed form with
     * {@code sin(tφ)/sin φ} and a series for the one term that cancels at small angles, so it is exact for pure
     * translations, identity and tiny rotations as well. {@code dest} may alias either input. @since 2.3.0
     */
    public DualQuatTransformd sclerp(DualQuatTransformd other, double t, DualQuatTransformd dest) {
        double ax = real.x, ay = real.y, az = real.z, aw = real.w;
        double bx = dual.x, by = dual.y, bz = dual.z, bw = dual.w;
        double cx = other.real.x, cy = other.real.y, cz = other.real.z, cw = other.real.w;
        double ex = other.dual.x, ey = other.dual.y, ez = other.dual.z, ew = other.dual.w;
        if (ax * cx + ay * cy + az * cz + aw * cw < 0.0) {
            cx = -cx;
            cy = -cy;
            cz = -cz;
            cw = -cw;
            ex = -ex;
            ey = -ey;
            ez = -ez;
            ew = -ew;
        }
        // relative transform: (a*, b*) (c, e) = (a* c, a* e + b* c)
        double px = -ax, py = -ay, pz = -az, pw = aw, qx = -bx, qy = -by, qz = -bz, qw = bw;
        double rx = pw * cx + px * cw + py * cz - pz * cy;
        double ry = pw * cy - px * cz + py * cw + pz * cx;
        double rz = pw * cz + px * cy - py * cx + pz * cw;
        double rw = pw * cw - px * cx - py * cy - pz * cz;
        double sx = pw * ex + px * ew + py * ez - pz * ey + qw * cx + qx * cw + qy * cz - qz * cy;
        double sy = pw * ey - px * ez + py * ew + pz * ex + qw * cy - qx * cz + qy * cw + qz * cx;
        double sz = pw * ez + px * ey - py * ex + pz * ew + qw * cz + qx * cy - qy * cx + qz * cw;
        double sw = pw * ew - px * ex - py * ey - pz * ez + qw * cw - qx * cx - qy * cy - qz * cz;
        // translation tau = 2 s r*
        double tx = 2.0 * (-sw * rx + sx * rw - sy * rz + sz * ry);
        double ty = 2.0 * (-sw * ry + sx * rz + sy * rw - sz * rx);
        double tz = 2.0 * (-sw * rz - sx * ry + sy * rx + sz * rw);
        double vl = java.lang.Math.sqrt(rx * rx + ry * ry + rz * rz);
        double phi = AccurateMath.atan2(vl, rw);
        double tt = t;
        double rho = phi == 0.0 ? tt : AccurateMath.sin(tt * phi) / AccurateMath.sin(phi);
        double q;
        if (phi < 2.5e-3) {
            q = tt * (1.0 - tt * tt) / 3.0 + phi * phi * ((tt * tt * tt * tt * tt - tt) / 30.0
                    + tt * (1.0 - tt * tt) / 6.0);
        } else {
            double sp = AccurateMath.sin(phi);
            q = (tt * AccurateMath.cos(tt * phi) * sp - AccurateMath.cos(phi) * AccurateMath.sin(tt * phi))
                    / (sp * sp * sp);
        }
        double tv = tx * rx + ty * ry + tz * rz;
        // power: real' = (rho v, cos(t phi)), dual' = (rho (tau x v + c tau) + (tau.v) v q) / 2, w' = -t rho tau.v / 2
        double fx = rho * rx, fy = rho * ry, fz = rho * rz, fw = AccurateMath.cos(tt * phi);
        double gx = 0.5 * (rho * (ty * rz - tz * ry + rw * tx) + tv * rx * q);
        double gy = 0.5 * (rho * (tz * rx - tx * rz + rw * ty) + tv * ry * q);
        double gz = 0.5 * (rho * (tx * ry - ty * rx + rw * tz) + tv * rz * q);
        double gw = -0.5 * tt * rho * tv;
        // dest = this * power
        double ox = aw * fx + ax * fw + ay * fz - az * fy;
        double oy = aw * fy - ax * fz + ay * fw + az * fx;
        double oz = aw * fz + ax * fy - ay * fx + az * fw;
        double ow = aw * fw - ax * fx - ay * fy - az * fz;
        double dx = aw * gx + ax * gw + ay * gz - az * gy + bw * fx + bx * fw + by * fz - bz * fy;
        double dy = aw * gy - ax * gz + ay * gw + az * gx + bw * fy - bx * fz + by * fw + bz * fx;
        double dz = aw * gz + ax * gy - ay * gx + az * gw + bw * fz + bx * fy - by * fx + bz * fw;
        double dw = aw * gw - ax * gx - ay * gy - az * gz + bw * fw - bx * fx - by * fy - bz * fz;
        return normalizeInto(ox, oy, oz, ow, dx, dy, dz, dw, dest);
    }

    /**
     * Dual quaternion linear blending (Kavan et al. 2008): the weighted sum with every term's sign aligned to the
     * first, made exactly unit. Weights need not sum to 1. @since 2.3.0
     */
    public static DualQuatTransformd blend(DualQuatTransformd[] transforms, double[] weights, int count,
            DualQuatTransformd dest) {
        double ax = transforms[0].real.x, ay = transforms[0].real.y, az = transforms[0].real.z;
        double aw = transforms[0].real.w;
        double rx = 0, ry = 0, rz = 0, rw = 0, dx = 0, dy = 0, dz = 0, dw = 0;
        for (int i = 0; i < count; i++) {
            DualQuatTransformd q = transforms[i];
            double w = weights[i];
            if (ax * q.real.x + ay * q.real.y + az * q.real.z + aw * q.real.w < 0.0) {
                w = -w;
            }
            rx += w * q.real.x;
            ry += w * q.real.y;
            rz += w * q.real.z;
            rw += w * q.real.w;
            dx += w * q.dual.x;
            dy += w * q.dual.y;
            dz += w * q.dual.z;
            dw += w * q.dual.w;
        }
        return normalizeInto(rx, ry, rz, rw, dx, dy, dz, dw, dest);
    }

    /**
     * Scale-aware blending: the rigid parts by {@link #blend}, and per-transform scales {@code scales} (xyz per
     * transform) linearly into {@code scaleDest}. Apply as {@code dest.transformPosition(scaleDest ∘ p)}: scale is
     * blended before the rigid motion, the two-phase scheme that keeps dual quaternion skinning free of
     * candy-wrapper artifacts while supporting (non-uniform) joint scale. @since 2.3.0
     */
    public static DualQuatTransformd blendScaled(DualQuatTransformd[] transforms, double[] scales, double[] weights,
            int count, DualQuatTransformd dest, Vector3d scaleDest) {
        double sx = 0, sy = 0, sz = 0, wsum = 0;
        for (int i = 0; i < count; i++) {
            double w = weights[i];
            sx += w * scales[3 * i];
            sy += w * scales[3 * i + 1];
            sz += w * scales[3 * i + 2];
            wsum += w;
        }
        scaleDest.set((sx / wsum), (sy / wsum), (sz / wsum));
        return blend(transforms, weights, count, dest);
    }
}
