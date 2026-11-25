/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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

import org.dynamisengine.vectrix.core.CoreConfig;
import org.dynamisengine.vectrix.core.Matrix4x3d;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaterniondc;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Transform stored as translation, rotation and scale (TRS). A point maps as
 * {@code p' = translation + rotation * (scale * p)}: scale first, then rotate, then translate.
 * <p>
 * Instances are mutable and not thread-safe. The rotation is used as given, so keep it a unit quaternion. Methods
 * with a {@code dest} parameter allow {@code dest} to be {@code this} or an argument.
 * <p>
 * Double-precision twin of {@link Transformf}, for world-scale transforms (see {@code world.WorldPos}).
 *
 * @since 2.3.0
 */
public class Transformd {
    /** The translation, applied last. Starts at zero. */
    public final Vector3d translation = new Vector3d();
    /** The rotation, a unit quaternion. Starts as the identity. */
    public final Quaterniond rotation = new Quaterniond();
    /** The scale along the local x, y and z axes, applied first. Starts at {@code (1, 1, 1)}. */
    public final Vector3d scale = new Vector3d(1.0, 1.0, 1.0);

    /** Creates the identity transform. */
    public Transformd() {
    }

    /**
     * Creates a transform from copies of the three parts.
     *
     * @param translation the translation
     * @param rotation the rotation, a unit quaternion
     * @param scale the scale
     */
    public Transformd(Vector3dc translation, Quaterniondc rotation, Vector3dc scale) {
        set(translation, rotation, scale);
    }

    /**
     * Creates a copy of {@code other}.
     *
     * @param other the transform to copy
     */
    public Transformd(Transformd other) {
        set(other);
    }

    /**
     * Sets this to the identity transform.
     *
     * @return this
     */
    public Transformd identity() {
        translation.zero();
        rotation.identity();
        scale.set(1.0, 1.0, 1.0);
        return this;
    }

    /**
     * Copies {@code other} into this.
     *
     * @param other the transform to copy
     * @return this
     */
    public Transformd set(Transformd other) {
        return set(other.translation, other.rotation, other.scale);
    }

    /**
     * Copies the three parts into this.
     *
     * @param translation the translation
     * @param rotation the rotation, a unit quaternion
     * @param scale the scale
     * @return this
     */
    public Transformd set(Vector3dc translation, Quaterniondc rotation, Vector3dc scale) {
        this.translation.set(translation);
        this.rotation.set(rotation);
        this.scale.set(scale);
        return this;
    }

    /**
     * Writes this transform into {@code dest} as the 4x3 matrix {@code T * R * S}.
     *
     * @param dest receives the matrix
     * @return {@code dest}
     */
    public Matrix4x3d toAffineMat4Fast(Matrix4x3d dest) {
        return dest.translationRotateScale(translation, rotation, scale);
    }

    /**
     * Writes this transform into {@code dest} as the affine matrix {@code T * R * S}.
     *
     * @param dest receives the matrix
     * @return {@code dest}
     */
    public Affine4d toAffine4dFast(Affine4d dest) {
        return dest.translationRotateScale(translation, rotation, scale);
    }

    /**
     * Sets this to the decomposition of {@code affine}; see {@link Affine4d#getTransform(Transformd)} for
     * the conditions under which it is exact.
     *
     * @param affine the matrix to decompose
     * @return this
     */
    public Transformd set(Affine4d affine) {
        return affine.getTransform(this);
    }

    /**
     * Computes {@code dest = parent * local} in TRS form: applied to a point, {@code local} acts first. The
     * translation is exact. The rotations multiply and the scales multiply per axis, which matches the matrix product
     * only when {@code parent.scale} is uniform (or {@code local.rotation} keeps the axes aligned); otherwise the
     * product has shear, which TRS cannot represent, and the result is an approximation.
     *
     * @param parent the outer transform
     * @param local the inner transform
     * @param dest receives the composition; may be {@code parent} or {@code local}
     * @return {@code dest}
     */
    public static Transformd compose(Transformd parent, Transformd local, Transformd dest) {
        double ltx = local.translation.x * parent.scale.x;
        double lty = local.translation.y * parent.scale.y;
        double ltz = local.translation.z * parent.scale.z;

        // Read parent's translation before writing dest: dest may be parent.
        double ptx = parent.translation.x, pty = parent.translation.y, ptz = parent.translation.z;
        parent.rotation.transform(ltx, lty, ltz, dest.translation);
        dest.translation.add(ptx, pty, ptz);
        parent.rotation.mul(local.rotation, dest.rotation);
        dest.scale.set(parent.scale.x * local.scale.x, parent.scale.y * local.scale.y, parent.scale.z * local.scale.z);
        return dest;
    }

    /**
     * Inverts a rigid transform (rotation and translation, no scale): the result has the conjugate rotation, the
     * translation {@code -(rotation^-1 * translation)} and scale {@code (1, 1, 1)}. It is the exact inverse when the
     * rotation is a unit quaternion.
     * <p>
     * A transform with scale is not rigid and is rejected rather than inverted wrongly: each scale component must be
     * within {@code 1e-6} ({@link CoreConfig#STRICT_UNIT_TOLERANCE_D}) of 1, in every math mode. To invert a
     * scaled transform, convert it with {@code toAffine4dFast} and invert the matrix.
     *
     * @param dest receives the inverse; may be {@code this}
     * @return {@code dest}
     * @throws IllegalArgumentException if a scale component is not 1 within the tolerance, or is NaN; {@code dest}
     *         is unchanged in that case
     */
    public Transformd invertRigidFast(Transformd dest) {
        if (!(java.lang.Math.abs(scale.x - 1.0) <= CoreConfig.STRICT_UNIT_TOLERANCE_D)
                || !(java.lang.Math.abs(scale.y - 1.0) <= CoreConfig.STRICT_UNIT_TOLERANCE_D)
                || !(java.lang.Math.abs(scale.z - 1.0) <= CoreConfig.STRICT_UNIT_TOLERANCE_D)) {
            throw new IllegalArgumentException("not rigid: scale " + scale.x + ", " + scale.y + ", " + scale.z);
        }
        double tx = translation.x, ty = translation.y, tz = translation.z;
        dest.scale.set(1.0, 1.0, 1.0);
        rotation.conjugate(dest.rotation);
        dest.rotation.transform(-tx, -ty, -tz, dest.translation);
        return dest;
    }

    /**
     * Linearly interpolates translation and scale, and normalized-lerps the rotation ({@code nlerp}, along the
     * shorter arc). Cheaper than {@link #interpolate}, but the rotation does not turn at a constant rate.
     *
     * @param target the end transform, reached at {@code alpha = 1}
     * @param alpha the interpolation factor; 0 gives this, 1 gives {@code target}
     * @param dest receives the result; may be {@code this} or {@code target}
     * @return {@code dest}
     */
    public Transformd interpolateFast(Transformd target, double alpha, Transformd dest) {
        double invAlpha = 1.0 - alpha;
        dest.translation.set(
                translation.x * invAlpha + target.translation.x * alpha,
                translation.y * invAlpha + target.translation.y * alpha,
                translation.z * invAlpha + target.translation.z * alpha);
        dest.scale.set(
                scale.x * invAlpha + target.scale.x * alpha,
                scale.y * invAlpha + target.scale.y * alpha,
                scale.z * invAlpha + target.scale.z * alpha);
        rotation.nlerp(target.rotation, alpha, dest.rotation);
        return dest;
    }

    /**
     * Linearly interpolates translation and scale, and spherically interpolates the rotation ({@code slerp}, along
     * the shorter arc). The rotation turns at a constant rate.
     *
     * @param target the end transform, reached at {@code alpha = 1}
     * @param alpha the interpolation factor; 0 gives this, 1 gives {@code target}
     * @param dest receives the result; may be {@code this} or {@code target}
     * @return {@code dest}
     */
    public Transformd interpolate(Transformd target, double alpha, Transformd dest) {
        double invAlpha = 1.0 - alpha;
        dest.translation.set(
                translation.x * invAlpha + target.translation.x * alpha,
                translation.y * invAlpha + target.translation.y * alpha,
                translation.z * invAlpha + target.translation.z * alpha);
        dest.scale.set(
                scale.x * invAlpha + target.scale.x * alpha,
                scale.y * invAlpha + target.scale.y * alpha,
                scale.z * invAlpha + target.scale.z * alpha);
        rotation.slerp(target.rotation, alpha, dest.rotation);
        return dest;
    }
}
