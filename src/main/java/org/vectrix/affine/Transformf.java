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
package org.vectrix.affine;

import org.vectrix.core.Matrix4x3f;
import org.vectrix.core.Quaternionf;
import org.vectrix.core.Quaternionfc;
import org.vectrix.core.Vector3f;
import org.vectrix.core.Vector3fc;

/**
 * First-class transform primitive with translation/rotation/scale.
 */
public class Transformf {
    public final Vector3f translation = new Vector3f();
    public final Quaternionf rotation = new Quaternionf();
    public final Vector3f scale = new Vector3f(1.0f, 1.0f, 1.0f);

    public Transformf() {
    }

    public Transformf(Vector3fc translation, Quaternionfc rotation, Vector3fc scale) {
        set(translation, rotation, scale);
    }

    public Transformf(Transformf other) {
        set(other);
    }

    public Transformf identity() {
        translation.zero();
        rotation.identity();
        scale.set(1.0f, 1.0f, 1.0f);
        return this;
    }

    public Transformf set(Transformf other) {
        return set(other.translation, other.rotation, other.scale);
    }

    public Transformf set(Vector3fc translation, Quaternionfc rotation, Vector3fc scale) {
        this.translation.set(translation);
        this.rotation.set(rotation);
        this.scale.set(scale);
        return this;
    }

    public Matrix4x3f toAffineMat4Fast(Matrix4x3f dest) {
        return dest.translationRotateScale(translation, rotation, scale);
    }

    public Affine4f toAffine4fFast(Affine4f dest) {
        return dest.translationRotateScale(translation, rotation, scale);
    }

    public Transformf set(Affine4f affine) {
        return affine.getTransform(this);
    }

    /**
     * Compute {@code dest = parent * local} in TRS form.
     */
    public static Transformf compose(Transformf parent, Transformf local, Transformf dest) {
        float ltx = local.translation.x * parent.scale.x;
        float lty = local.translation.y * parent.scale.y;
        float ltz = local.translation.z * parent.scale.z;

        parent.rotation.transform(ltx, lty, ltz, dest.translation);
        dest.translation.add(parent.translation);
        parent.rotation.mul(local.rotation, dest.rotation);
        dest.scale.set(parent.scale.x * local.scale.x, parent.scale.y * local.scale.y, parent.scale.z * local.scale.z);
        return dest;
    }

    /**
     * Fast inverse for rigid transforms (translation + unit quaternion rotation).
     */
    public Transformf invertRigidFast(Transformf dest) {
        dest.scale.set(1.0f, 1.0f, 1.0f);
        rotation.conjugate(dest.rotation);
        dest.rotation.transform(-translation.x, -translation.y, -translation.z, dest.translation);
        return dest;
    }

    public Transformf interpolateFast(Transformf target, float alpha, Transformf dest) {
        float invAlpha = 1.0f - alpha;
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
}
