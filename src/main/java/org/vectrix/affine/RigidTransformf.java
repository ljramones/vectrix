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
 * Rigid transform (translation + rotation).
 */
public class RigidTransformf {
    public final Vector3f translation = new Vector3f();
    public final Quaternionf rotation = new Quaternionf();

    public RigidTransformf() {
    }

    public RigidTransformf(Vector3fc translation, Quaternionfc rotation) {
        set(translation, rotation);
    }

    public RigidTransformf set(Vector3fc translation, Quaternionfc rotation) {
        this.translation.set(translation);
        this.rotation.set(rotation);
        return this;
    }

    public RigidTransformf set(RigidTransformf other) {
        return set(other.translation, other.rotation);
    }

    public RigidTransformf identity() {
        translation.zero();
        rotation.identity();
        return this;
    }

    public Matrix4x3f toAffineMat4Fast(Matrix4x3f dest) {
        return dest.translationRotate(translation, rotation);
    }

    public Vector3f transformPosition(float x, float y, float z, Vector3f dest) {
        rotation.transform(x, y, z, dest);
        return dest.add(translation);
    }

    public RigidTransformf invertRigidFast(RigidTransformf dest) {
        rotation.conjugate(dest.rotation);
        dest.rotation.transform(-translation.x, -translation.y, -translation.z, dest.translation);
        return dest;
    }

    public static RigidTransformf compose(RigidTransformf parent, RigidTransformf local, RigidTransformf dest) {
        parent.rotation.transform(local.translation, dest.translation);
        dest.translation.add(parent.translation);
        parent.rotation.mul(local.rotation, dest.rotation);
        return dest;
    }
}
