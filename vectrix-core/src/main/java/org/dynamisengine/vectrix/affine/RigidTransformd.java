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

import org.dynamisengine.vectrix.core.Matrix4x3d;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaterniondc;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Rigid transform (translation + rotation): a point maps as {@code p' = rotation * p + translation}.
 * <p>
 * Instances are mutable and not thread-safe. The rotation is used as given, so keep it a unit quaternion. Methods
 * with a {@code dest} parameter allow {@code dest} to be {@code this} or an argument.
 * <p>
 * Double-precision twin of {@link RigidTransformf}, for world-scale transforms (see {@code world.WorldPos}).
 *
 * @since 2.3.0
 */
public class RigidTransformd {
    /** The translation, applied after the rotation. Starts at zero. */
    public final Vector3d translation = new Vector3d();
    /** The rotation, a unit quaternion. Starts as the identity. */
    public final Quaterniond rotation = new Quaterniond();

    /** Creates the identity transform. */
    public RigidTransformd() {
    }

    /**
     * Creates a transform from a copy of {@code translation} and {@code rotation}.
     *
     * @param translation the translation
     * @param rotation the rotation, a unit quaternion
     */
    public RigidTransformd(Vector3dc translation, Quaterniondc rotation) {
        set(translation, rotation);
    }

    /**
     * Copies {@code translation} and {@code rotation} into this.
     *
     * @param translation the translation
     * @param rotation the rotation, a unit quaternion
     * @return this
     */
    public RigidTransformd set(Vector3dc translation, Quaterniondc rotation) {
        this.translation.set(translation);
        this.rotation.set(rotation);
        return this;
    }

    /**
     * Copies {@code other} into this.
     *
     * @param other the transform to copy
     * @return this
     */
    public RigidTransformd set(RigidTransformd other) {
        return set(other.translation, other.rotation);
    }

    /**
     * Sets this to the identity transform.
     *
     * @return this
     */
    public RigidTransformd identity() {
        translation.zero();
        rotation.identity();
        return this;
    }

    /**
     * Writes this transform into {@code dest} as a 4x3 matrix (rotation, then translation).
     *
     * @param dest receives the matrix
     * @return {@code dest}
     */
    public Matrix4x3d toAffineMat4Fast(Matrix4x3d dest) {
        return dest.translationRotate(translation, rotation);
    }

    /**
     * Transforms the point {@code (x, y, z)}: rotates it, then adds the translation.
     *
     * @param x the point x
     * @param y the point y
     * @param z the point z
     * @param dest receives the transformed point
     * @return {@code dest}
     */
    public Vector3d transformPosition(double x, double y, double z, Vector3d dest) {
        rotation.transform(x, y, z, dest);
        return dest.add(translation);
    }

    /**
     * Computes the inverse transform: the conjugate rotation and the translation {@code -(rotation^-1 * translation)}.
     * Uses the conjugate as the inverse rotation, so it is exact only for a unit quaternion.
     *
     * @param dest receives the inverse; may be {@code this}
     * @return {@code dest}
     */
    public RigidTransformd invertRigidFast(RigidTransformd dest) {
        rotation.conjugate(dest.rotation);
        dest.rotation.transform(-translation.x, -translation.y, -translation.z, dest.translation);
        return dest;
    }

    /**
     * Computes {@code dest = parent * local}: applied to a point, {@code local} acts first.
     *
     * @param parent the outer transform
     * @param local the inner transform
     * @param dest receives the composition; may be {@code parent} or {@code local}
     * @return {@code dest}
     */
    public static RigidTransformd compose(RigidTransformd parent, RigidTransformd local, RigidTransformd dest) {
        // Read parent's translation before writing dest: dest may be parent.
        double ptx = parent.translation.x, pty = parent.translation.y, ptz = parent.translation.z;
        parent.rotation.transform(local.translation, dest.translation);
        dest.translation.add(ptx, pty, ptz);
        parent.rotation.mul(local.rotation, dest.rotation);
        return dest;
    }
}
