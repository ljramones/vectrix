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

import org.vectrix.core.Quaternionf;
import org.vectrix.core.Vector3f;

/**
 * Dual quaternion rigid transform.
 */
public class DualQuatTransformf {
    public final Quaternionf real = new Quaternionf();
    public final Quaternionf dual = new Quaternionf(0, 0, 0, 0);

    public DualQuatTransformf() {
    }

    public DualQuatTransformf set(DualQuatTransformf other) {
        real.set(other.real);
        dual.set(other.dual);
        return this;
    }

    public DualQuatTransformf setFromRigid(RigidTransformf rigid) {
        float rx = rigid.rotation.x;
        float ry = rigid.rotation.y;
        float rz = rigid.rotation.z;
        float rw = rigid.rotation.w;
        float tx = rigid.translation.x;
        float ty = rigid.translation.y;
        float tz = rigid.translation.z;
        real.set(rx, ry, rz, rw).normalize();
        dual.set(
                0.5f * (tx * rw + ty * rz - tz * ry),
                0.5f * (-tx * rz + ty * rw + tz * rx),
                0.5f * (tx * ry - ty * rx + tz * rw),
                -0.5f * (tx * rx + ty * ry + tz * rz));
        return this;
    }

    public DualQuatTransformf normalize() {
        float invNorm = org.vectrix.core.Math.invsqrt(real.x * real.x + real.y * real.y + real.z * real.z + real.w * real.w);
        real.mul(invNorm);
        dual.mul(invNorm);
        return this;
    }

    public Vector3f transformPosition(float x, float y, float z, Vector3f dest) {
        real.transform(x, y, z, dest);
        float rx = real.x, ry = real.y, rz = real.z, rw = real.w;
        float dx = dual.x, dy = dual.y, dz = dual.z, dw = dual.w;
        float tx = 2.0f * (-dw * rx + dx * rw - dy * rz + dz * ry);
        float ty = 2.0f * (-dw * ry + dx * rz + dy * rw - dz * rx);
        float tz = 2.0f * (-dw * rz - dx * ry + dy * rx + dz * rw);
        return dest.add(tx, ty, tz);
    }
}
