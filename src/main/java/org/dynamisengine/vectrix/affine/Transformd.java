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
 * First-class transform primitive with translation/rotation/scale.
 * <p>
 * Double-precision twin of {@link Transformf}, for world-scale transforms (see {@code world.WorldPos}).
 *
 * @since 2.3.0
 */
public class Transformd {
    public final Vector3d translation = new Vector3d();
    public final Quaterniond rotation = new Quaterniond();
    public final Vector3d scale = new Vector3d(1.0, 1.0, 1.0);

    public Transformd() {
    }

    public Transformd(Vector3dc translation, Quaterniondc rotation, Vector3dc scale) {
        set(translation, rotation, scale);
    }

    public Transformd(Transformd other) {
        set(other);
    }

    public Transformd identity() {
        translation.zero();
        rotation.identity();
        scale.set(1.0, 1.0, 1.0);
        return this;
    }

    public Transformd set(Transformd other) {
        return set(other.translation, other.rotation, other.scale);
    }

    public Transformd set(Vector3dc translation, Quaterniondc rotation, Vector3dc scale) {
        this.translation.set(translation);
        this.rotation.set(rotation);
        this.scale.set(scale);
        return this;
    }

    public Matrix4x3d toAffineMat4Fast(Matrix4x3d dest) {
        return dest.translationRotateScale(translation, rotation, scale);
    }

    public Affine4d toAffine4dFast(Affine4d dest) {
        return dest.translationRotateScale(translation, rotation, scale);
    }

    public Transformd set(Affine4d affine) {
        return affine.getTransform(this);
    }

    /**
     * Compute {@code dest = parent * local} in TRS form.
     */
    public static Transformd compose(Transformd parent, Transformd local, Transformd dest) {
        double ltx = local.translation.x * parent.scale.x;
        double lty = local.translation.y * parent.scale.y;
        double ltz = local.translation.z * parent.scale.z;

        // Read parent's translation before writing dest: dest may be parent (review finding M-2).
        double ptx = parent.translation.x, pty = parent.translation.y, ptz = parent.translation.z;
        parent.rotation.transform(ltx, lty, ltz, dest.translation);
        dest.translation.add(ptx, pty, ptz);
        parent.rotation.mul(local.rotation, dest.rotation);
        dest.scale.set(parent.scale.x * local.scale.x, parent.scale.y * local.scale.y, parent.scale.z * local.scale.z);
        return dest;
    }

    /**
     * Fast inverse for rigid transforms (translation + unit quaternion rotation).
     */
    public Transformd invertRigidFast(Transformd dest) {
        dest.scale.set(1.0, 1.0, 1.0);
        rotation.conjugate(dest.rotation);
        dest.rotation.transform(-translation.x, -translation.y, -translation.z, dest.translation);
        return dest;
    }

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
     * Higher quality TRS interpolation using SLERP for rotation.
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
