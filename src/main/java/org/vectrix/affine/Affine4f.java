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

import org.vectrix.core.Matrix4f;
import org.vectrix.core.Matrix4fc;
import org.vectrix.core.Matrix4x3f;
import org.vectrix.core.Matrix4x3fc;
import org.vectrix.core.Quaternionfc;
import org.vectrix.core.Vector3f;
import org.vectrix.core.Vector3fc;

/**
 * Affine 3x4 transform matrix in column-major layout.
 */
public class Affine4f {
    public float m00, m01, m02;
    public float m10, m11, m12;
    public float m20, m21, m22;
    public float m30, m31, m32;

    public Affine4f() {
        identity();
    }

    public Affine4f(Affine4f other) {
        set(other);
    }

    public Affine4f(Matrix4x3fc other) {
        set(other);
    }

    public Affine4f identity() {
        m00 = 1.0f;
        m01 = 0.0f;
        m02 = 0.0f;
        m10 = 0.0f;
        m11 = 1.0f;
        m12 = 0.0f;
        m20 = 0.0f;
        m21 = 0.0f;
        m22 = 1.0f;
        m30 = 0.0f;
        m31 = 0.0f;
        m32 = 0.0f;
        return this;
    }

    public Affine4f set(Affine4f other) {
        m00 = other.m00;
        m01 = other.m01;
        m02 = other.m02;
        m10 = other.m10;
        m11 = other.m11;
        m12 = other.m12;
        m20 = other.m20;
        m21 = other.m21;
        m22 = other.m22;
        m30 = other.m30;
        m31 = other.m31;
        m32 = other.m32;
        return this;
    }

    public Affine4f set(Matrix4x3fc other) {
        m00 = other.m00();
        m01 = other.m01();
        m02 = other.m02();
        m10 = other.m10();
        m11 = other.m11();
        m12 = other.m12();
        m20 = other.m20();
        m21 = other.m21();
        m22 = other.m22();
        m30 = other.m30();
        m31 = other.m31();
        m32 = other.m32();
        return this;
    }

    public Affine4f set(Matrix4fc other) {
        m00 = other.m00();
        m01 = other.m01();
        m02 = other.m02();
        m10 = other.m10();
        m11 = other.m11();
        m12 = other.m12();
        m20 = other.m20();
        m21 = other.m21();
        m22 = other.m22();
        m30 = other.m30();
        m31 = other.m31();
        m32 = other.m32();
        return this;
    }

    public Affine4f translationRotateScale(Vector3fc translation, Quaternionfc rotation, Vector3fc scale) {
        return translationRotateScale(
                translation.x(), translation.y(), translation.z(),
                rotation.x(), rotation.y(), rotation.z(), rotation.w(),
                scale.x(), scale.y(), scale.z());
    }

    public Affine4f translationRotateScale(float tx, float ty, float tz, float qx, float qy, float qz, float qw, float sx, float sy, float sz) {
        float dqx = qx + qx;
        float dqy = qy + qy;
        float dqz = qz + qz;
        float q00 = dqx * qx;
        float q11 = dqy * qy;
        float q22 = dqz * qz;
        float q01 = dqx * qy;
        float q02 = dqx * qz;
        float q03 = dqx * qw;
        float q12 = dqy * qz;
        float q13 = dqy * qw;
        float q23 = dqz * qw;
        m00 = sx - (q11 + q22) * sx;
        m01 = (q01 + q23) * sx;
        m02 = (q02 - q13) * sx;
        m10 = (q01 - q23) * sy;
        m11 = sy - (q22 + q00) * sy;
        m12 = (q12 + q03) * sy;
        m20 = (q02 + q13) * sz;
        m21 = (q12 - q03) * sz;
        m22 = sz - (q11 + q00) * sz;
        m30 = tx;
        m31 = ty;
        m32 = tz;
        return this;
    }

    /**
     * Compute {@code dest = this * right}.
     */
    public Affine4f mul(Affine4f right, Affine4f dest) {
        float nm00 = java.lang.Math.fma(m00, right.m00, java.lang.Math.fma(m10, right.m01, m20 * right.m02));
        float nm01 = java.lang.Math.fma(m01, right.m00, java.lang.Math.fma(m11, right.m01, m21 * right.m02));
        float nm02 = java.lang.Math.fma(m02, right.m00, java.lang.Math.fma(m12, right.m01, m22 * right.m02));
        float nm10 = java.lang.Math.fma(m00, right.m10, java.lang.Math.fma(m10, right.m11, m20 * right.m12));
        float nm11 = java.lang.Math.fma(m01, right.m10, java.lang.Math.fma(m11, right.m11, m21 * right.m12));
        float nm12 = java.lang.Math.fma(m02, right.m10, java.lang.Math.fma(m12, right.m11, m22 * right.m12));
        float nm20 = java.lang.Math.fma(m00, right.m20, java.lang.Math.fma(m10, right.m21, m20 * right.m22));
        float nm21 = java.lang.Math.fma(m01, right.m20, java.lang.Math.fma(m11, right.m21, m21 * right.m22));
        float nm22 = java.lang.Math.fma(m02, right.m20, java.lang.Math.fma(m12, right.m21, m22 * right.m22));
        float nm30 = java.lang.Math.fma(m00, right.m30, java.lang.Math.fma(m10, right.m31, java.lang.Math.fma(m20, right.m32, m30)));
        float nm31 = java.lang.Math.fma(m01, right.m30, java.lang.Math.fma(m11, right.m31, java.lang.Math.fma(m21, right.m32, m31)));
        float nm32 = java.lang.Math.fma(m02, right.m30, java.lang.Math.fma(m12, right.m31, java.lang.Math.fma(m22, right.m32, m32)));
        dest.m00 = nm00;
        dest.m01 = nm01;
        dest.m02 = nm02;
        dest.m10 = nm10;
        dest.m11 = nm11;
        dest.m12 = nm12;
        dest.m20 = nm20;
        dest.m21 = nm21;
        dest.m22 = nm22;
        dest.m30 = nm30;
        dest.m31 = nm31;
        dest.m32 = nm32;
        return dest;
    }

    /**
     * Fast inverse for rigid affine transforms (orthonormal basis + translation).
     */
    public Affine4f invertRigid(Affine4f dest) {
        float nm30 = -(m00 * m30 + m01 * m31 + m02 * m32);
        float nm31 = -(m10 * m30 + m11 * m31 + m12 * m32);
        float nm32 = -(m20 * m30 + m21 * m31 + m22 * m32);
        float rm01 = m01;
        float rm02 = m02;
        float rm12 = m12;
        dest.m00 = m00;
        dest.m01 = m10;
        dest.m02 = m20;
        dest.m10 = rm01;
        dest.m11 = m11;
        dest.m12 = m21;
        dest.m20 = rm02;
        dest.m21 = rm12;
        dest.m22 = m22;
        dest.m30 = nm30;
        dest.m31 = nm31;
        dest.m32 = nm32;
        return dest;
    }

    public Vector3f transformPoint(float x, float y, float z, Vector3f dest) {
        dest.x = java.lang.Math.fma(m00, x, java.lang.Math.fma(m10, y, java.lang.Math.fma(m20, z, m30)));
        dest.y = java.lang.Math.fma(m01, x, java.lang.Math.fma(m11, y, java.lang.Math.fma(m21, z, m31)));
        dest.z = java.lang.Math.fma(m02, x, java.lang.Math.fma(m12, y, java.lang.Math.fma(m22, z, m32)));
        return dest;
    }

    public Vector3f transformVector(float x, float y, float z, Vector3f dest) {
        dest.x = java.lang.Math.fma(m00, x, java.lang.Math.fma(m10, y, m20 * z));
        dest.y = java.lang.Math.fma(m01, x, java.lang.Math.fma(m11, y, m21 * z));
        dest.z = java.lang.Math.fma(m02, x, java.lang.Math.fma(m12, y, m22 * z));
        return dest;
    }

    public Matrix4x3f toMatrix4x3f(Matrix4x3f dest) {
        return dest.set(m00, m01, m02, m10, m11, m12, m20, m21, m22, m30, m31, m32);
    }

    public Matrix4f toMatrix4f(Matrix4f dest) {
        return dest.set(
                m00, m01, m02, 0.0f,
                m10, m11, m12, 0.0f,
                m20, m21, m22, 0.0f,
                m30, m31, m32, 1.0f);
    }

    /**
     * Decompose this affine transform into TRS.
     */
    public Transformf getTransform(Transformf dest) {
        float sx = (float) java.lang.Math.sqrt(m00 * m00 + m01 * m01 + m02 * m02);
        float sy = (float) java.lang.Math.sqrt(m10 * m10 + m11 * m11 + m12 * m12);
        float sz = (float) java.lang.Math.sqrt(m20 * m20 + m21 * m21 + m22 * m22);
        dest.translation.set(m30, m31, m32);
        dest.scale.set(sx, sy, sz);

        float invSx = sx > 0.0f ? 1.0f / sx : 0.0f;
        float invSy = sy > 0.0f ? 1.0f / sy : 0.0f;
        float invSz = sz > 0.0f ? 1.0f / sz : 0.0f;
        float r00 = m00 * invSx, r01 = m01 * invSx, r02 = m02 * invSx;
        float r10 = m10 * invSy, r11 = m11 * invSy, r12 = m12 * invSy;
        float r20 = m20 * invSz, r21 = m21 * invSz, r22 = m22 * invSz;

        float t;
        float tr = r00 + r11 + r22;
        if (tr >= 0.0f) {
            t = (float) java.lang.Math.sqrt(tr + 1.0f);
            dest.rotation.w = t * 0.5f;
            t = 0.5f / t;
            dest.rotation.x = (r12 - r21) * t;
            dest.rotation.y = (r20 - r02) * t;
            dest.rotation.z = (r01 - r10) * t;
        } else if (r00 >= r11 && r00 >= r22) {
            t = (float) java.lang.Math.sqrt(r00 - (r11 + r22) + 1.0f);
            dest.rotation.x = t * 0.5f;
            t = 0.5f / t;
            dest.rotation.y = (r10 + r01) * t;
            dest.rotation.z = (r02 + r20) * t;
            dest.rotation.w = (r12 - r21) * t;
        } else if (r11 > r22) {
            t = (float) java.lang.Math.sqrt(r11 - (r22 + r00) + 1.0f);
            dest.rotation.y = t * 0.5f;
            t = 0.5f / t;
            dest.rotation.z = (r21 + r12) * t;
            dest.rotation.x = (r10 + r01) * t;
            dest.rotation.w = (r20 - r02) * t;
        } else {
            t = (float) java.lang.Math.sqrt(r22 - (r00 + r11) + 1.0f);
            dest.rotation.z = t * 0.5f;
            t = 0.5f / t;
            dest.rotation.x = (r02 + r20) * t;
            dest.rotation.y = (r21 + r12) * t;
            dest.rotation.w = (r01 - r10) * t;
        }
        return dest;
    }
}
