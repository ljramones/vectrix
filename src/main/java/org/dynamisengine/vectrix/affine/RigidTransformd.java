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
 * Rigid transform (translation + rotation).
 * <p>
 * Double-precision twin of {@link RigidTransformf}, for world-scale transforms (see {@code world.WorldPos}).
 *
 * @since 2.3.0
 */
public class RigidTransformd {
    public final Vector3d translation = new Vector3d();
    public final Quaterniond rotation = new Quaterniond();

    public RigidTransformd() {
    }

    public RigidTransformd(Vector3dc translation, Quaterniondc rotation) {
        set(translation, rotation);
    }

    public RigidTransformd set(Vector3dc translation, Quaterniondc rotation) {
        this.translation.set(translation);
        this.rotation.set(rotation);
        return this;
    }

    public RigidTransformd set(RigidTransformd other) {
        return set(other.translation, other.rotation);
    }

    public RigidTransformd identity() {
        translation.zero();
        rotation.identity();
        return this;
    }

    public Matrix4x3d toAffineMat4Fast(Matrix4x3d dest) {
        return dest.translationRotate(translation, rotation);
    }

    public Vector3d transformPosition(double x, double y, double z, Vector3d dest) {
        rotation.transform(x, y, z, dest);
        return dest.add(translation);
    }

    public RigidTransformd invertRigidFast(RigidTransformd dest) {
        rotation.conjugate(dest.rotation);
        dest.rotation.transform(-translation.x, -translation.y, -translation.z, dest.translation);
        return dest;
    }

    public static RigidTransformd compose(RigidTransformd parent, RigidTransformd local, RigidTransformd dest) {
        // Read parent's translation before writing dest: dest may be parent (review finding M-2).
        double ptx = parent.translation.x, pty = parent.translation.y, ptz = parent.translation.z;
        parent.rotation.transform(local.translation, dest.translation);
        dest.translation.add(ptx, pty, ptz);
        parent.rotation.mul(local.rotation, dest.rotation);
        return dest;
    }
}
