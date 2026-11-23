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

import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Matrix4dc;
import org.dynamisengine.vectrix.core.Matrix4x3d;
import org.dynamisengine.vectrix.core.Matrix4x3dc;
import org.dynamisengine.vectrix.core.Quaterniondc;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Affine 3x4 transform matrix in column-major layout: a 4x4 matrix whose last row is implicitly {@code (0, 0, 0, 1)}.
 * Field {@code mCR} is column C, row R, as in {@link org.dynamisengine.vectrix.core.Matrix4x3d}; columns 0 to 2 hold
 * the linear part and column 3 the translation. A point maps as {@code x' = m00*x + m10*y + m20*z + m30}, and so on.
 * <p>
 * Instances are mutable and not thread-safe. Methods with a {@code dest} parameter allow {@code dest} to be
 * {@code this} or an argument.
 * <p>
 * Double-precision twin of {@link Affine4f}, for world-scale transforms (see {@code world.WorldPos}).
 *
 * @since 2.3.0
 */
public class Affine4d {
    /** Column 0 (the image of the x axis): rows 0, 1 and 2. */
    public double m00, m01, m02;
    /** Column 1 (the image of the y axis): rows 0, 1 and 2. */
    public double m10, m11, m12;
    /** Column 2 (the image of the z axis): rows 0, 1 and 2. */
    public double m20, m21, m22;
    /** Column 3: the translation x, y and z. */
    public double m30, m31, m32;

    /** Creates the identity transform. */
    public Affine4d() {
        identity();
    }

    /**
     * Creates a copy of {@code other}.
     *
     * @param other the transform to copy
     */
    public Affine4d(Affine4d other) {
        set(other);
    }

    /**
     * Creates a transform with the twelve elements of {@code other}.
     *
     * @param other the matrix to copy
     */
    public Affine4d(Matrix4x3dc other) {
        set(other);
    }

    /**
     * Sets this to the identity transform.
     *
     * @return this
     */
    public Affine4d identity() {
        m00 = 1.0;
        m01 = 0.0;
        m02 = 0.0;
        m10 = 0.0;
        m11 = 1.0;
        m12 = 0.0;
        m20 = 0.0;
        m21 = 0.0;
        m22 = 1.0;
        m30 = 0.0;
        m31 = 0.0;
        m32 = 0.0;
        return this;
    }

    /**
     * Copies {@code other} into this.
     *
     * @param other the transform to copy
     * @return this
     */
    public Affine4d set(Affine4d other) {
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

    /**
     * Copies the twelve elements of {@code other} into this.
     *
     * @param other the matrix to copy
     * @return this
     */
    public Affine4d set(Matrix4x3dc other) {
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

    /**
     * Copies the upper 3x4 part of {@code other} into this. The last row of {@code other} is ignored, so a
     * projective matrix loses its projective part.
     *
     * @param other the matrix to copy
     * @return this
     */
    public Affine4d set(Matrix4dc other) {
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

    /**
     * Sets this to {@code T * R * S}: scale first, then rotate, then translate. See
     * {@link #translationRotateScale(double, double, double, double, double, double, double, double, double, double)}.
     *
     * @param translation the translation
     * @param rotation the rotation; must be a unit quaternion
     * @param scale the scale factors along the local x, y and z axes
     * @return this
     */
    public Affine4d translationRotateScale(Vector3dc translation, Quaterniondc rotation, Vector3dc scale) {
        return translationRotateScale(
                translation.x(), translation.y(), translation.z(),
                rotation.x(), rotation.y(), rotation.z(), rotation.w(),
                scale.x(), scale.y(), scale.z());
    }

    /**
     * Sets this to {@code T * R * S}: scale first, then rotate, then translate, the same matrix as
     * {@code Matrix4x3d.translationRotateScale}. The quaternion is not normalized: a non-unit quaternion gives a
     * matrix that also scales.
     *
     * @param tx the translation x
     * @param ty the translation y
     * @param tz the translation z
     * @param qx the rotation quaternion x
     * @param qy the rotation quaternion y
     * @param qz the rotation quaternion z
     * @param qw the rotation quaternion w (scalar part)
     * @param sx the scale along the local x axis
     * @param sy the scale along the local y axis
     * @param sz the scale along the local z axis
     * @return this
     */
    public Affine4d translationRotateScale(double tx, double ty, double tz, double qx, double qy, double qz, double qw, double sx, double sy, double sz) {
        double dqx = qx + qx;
        double dqy = qy + qy;
        double dqz = qz + qz;
        double q00 = dqx * qx;
        double q11 = dqy * qy;
        double q22 = dqz * qz;
        double q01 = dqx * qy;
        double q02 = dqx * qz;
        double q03 = dqx * qw;
        double q12 = dqy * qz;
        double q13 = dqy * qw;
        double q23 = dqz * qw;
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
     * Computes {@code dest = this * right}: applied to a point, {@code right} acts first. The sums use
     * {@link org.dynamisengine.vectrix.core.Math#fma}, so they round as {@code -Dvectrix.useMathFma} selects.
     *
     * @param right the right operand
     * @param dest receives the product; may be {@code this} or {@code right}
     * @return {@code dest}
     */
    public Affine4d mul(Affine4d right, Affine4d dest) {
        double nm00 = org.dynamisengine.vectrix.core.Math.fma(m00, right.m00, org.dynamisengine.vectrix.core.Math.fma(m10, right.m01, m20 * right.m02));
        double nm01 = org.dynamisengine.vectrix.core.Math.fma(m01, right.m00, org.dynamisengine.vectrix.core.Math.fma(m11, right.m01, m21 * right.m02));
        double nm02 = org.dynamisengine.vectrix.core.Math.fma(m02, right.m00, org.dynamisengine.vectrix.core.Math.fma(m12, right.m01, m22 * right.m02));
        double nm10 = org.dynamisengine.vectrix.core.Math.fma(m00, right.m10, org.dynamisengine.vectrix.core.Math.fma(m10, right.m11, m20 * right.m12));
        double nm11 = org.dynamisengine.vectrix.core.Math.fma(m01, right.m10, org.dynamisengine.vectrix.core.Math.fma(m11, right.m11, m21 * right.m12));
        double nm12 = org.dynamisengine.vectrix.core.Math.fma(m02, right.m10, org.dynamisengine.vectrix.core.Math.fma(m12, right.m11, m22 * right.m12));
        double nm20 = org.dynamisengine.vectrix.core.Math.fma(m00, right.m20, org.dynamisengine.vectrix.core.Math.fma(m10, right.m21, m20 * right.m22));
        double nm21 = org.dynamisengine.vectrix.core.Math.fma(m01, right.m20, org.dynamisengine.vectrix.core.Math.fma(m11, right.m21, m21 * right.m22));
        double nm22 = org.dynamisengine.vectrix.core.Math.fma(m02, right.m20, org.dynamisengine.vectrix.core.Math.fma(m12, right.m21, m22 * right.m22));
        double nm30 = org.dynamisengine.vectrix.core.Math.fma(m00, right.m30, org.dynamisengine.vectrix.core.Math.fma(m10, right.m31, org.dynamisengine.vectrix.core.Math.fma(m20, right.m32, m30)));
        double nm31 = org.dynamisengine.vectrix.core.Math.fma(m01, right.m30, org.dynamisengine.vectrix.core.Math.fma(m11, right.m31, org.dynamisengine.vectrix.core.Math.fma(m21, right.m32, m31)));
        double nm32 = org.dynamisengine.vectrix.core.Math.fma(m02, right.m30, org.dynamisengine.vectrix.core.Math.fma(m12, right.m31, org.dynamisengine.vectrix.core.Math.fma(m22, right.m32, m32)));
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
     * Inverts a rigid transform: transposes the 3x3 part and sets the translation to {@code -R^T t}. The result is
     * the inverse only if the 3x3 part is a rotation (orthonormal columns); with scale or shear it is wrong, and
     * nothing checks this.
     *
     * @param dest receives the inverse; may be {@code this}
     * @return {@code dest}
     */
    public Affine4d invertRigid(Affine4d dest) {
        double nm30 = -(m00 * m30 + m01 * m31 + m02 * m32);
        double nm31 = -(m10 * m30 + m11 * m31 + m12 * m32);
        double nm32 = -(m20 * m30 + m21 * m31 + m22 * m32);
        double rm01 = m01;
        double rm02 = m02;
        double rm12 = m12;
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

    /**
     * Transforms the point {@code (x, y, z)}, translation included.
     *
     * @param x the point x
     * @param y the point y
     * @param z the point z
     * @param dest receives the transformed point
     * @return {@code dest}
     */
    public Vector3d transformPoint(double x, double y, double z, Vector3d dest) {
        dest.x = org.dynamisengine.vectrix.core.Math.fma(m00, x, org.dynamisengine.vectrix.core.Math.fma(m10, y, org.dynamisengine.vectrix.core.Math.fma(m20, z, m30)));
        dest.y = org.dynamisengine.vectrix.core.Math.fma(m01, x, org.dynamisengine.vectrix.core.Math.fma(m11, y, org.dynamisengine.vectrix.core.Math.fma(m21, z, m31)));
        dest.z = org.dynamisengine.vectrix.core.Math.fma(m02, x, org.dynamisengine.vectrix.core.Math.fma(m12, y, org.dynamisengine.vectrix.core.Math.fma(m22, z, m32)));
        return dest;
    }

    /**
     * Transforms the direction {@code (x, y, z)} by the 3x3 part only, without translation. For a transform with
     * non-uniform scale this is not the right transform for normals.
     *
     * @param x the vector x
     * @param y the vector y
     * @param z the vector z
     * @param dest receives the transformed vector
     * @return {@code dest}
     */
    public Vector3d transformVector(double x, double y, double z, Vector3d dest) {
        dest.x = org.dynamisengine.vectrix.core.Math.fma(m00, x, org.dynamisengine.vectrix.core.Math.fma(m10, y, m20 * z));
        dest.y = org.dynamisengine.vectrix.core.Math.fma(m01, x, org.dynamisengine.vectrix.core.Math.fma(m11, y, m21 * z));
        dest.z = org.dynamisengine.vectrix.core.Math.fma(m02, x, org.dynamisengine.vectrix.core.Math.fma(m12, y, m22 * z));
        return dest;
    }

    /**
     * Copies the twelve elements into {@code dest}, which uses the same column-major element names.
     *
     * @param dest receives the matrix
     * @return {@code dest}
     */
    public Matrix4x3d toMatrix4x3d(Matrix4x3d dest) {
        return dest.set(m00, m01, m02, m10, m11, m12, m20, m21, m22, m30, m31, m32);
    }

    /**
     * Writes this into a 4x4 matrix with last row {@code (0, 0, 0, 1)}.
     * Despite its name, this method fills a {@link Matrix4d}.
     *
     * @param dest receives the matrix
     * @return {@code dest}
     */
    public Matrix4d toMatrix4f(Matrix4d dest) {
        return dest.set(
                m00, m01, m02, 0.0,
                m10, m11, m12, 0.0,
                m20, m21, m22, 0.0,
                m30, m31, m32, 1.0);
    }

    /**
     * Decomposes this transform into translation, rotation and scale, the inverse of
     * {@link #translationRotateScale(Vector3dc, Quaterniondc, Vector3dc)}. Each scale factor is the
     * length of a column, so it is never negative, and the rotation is read from the columns divided by those
     * lengths. The result reproduces this matrix only if it has no shear and a positive determinant; a reflection
     * or shear gives a rotation that is not a unit quaternion. A zero-length column gives scale 0 and a zero column
     * in the rotation matrix the quaternion is read from.
     *
     * @param dest receives the translation, rotation and scale
     * @return {@code dest}
     */
    public Transformd getTransform(Transformd dest) {
        double sx = java.lang.Math.sqrt(m00 * m00 + m01 * m01 + m02 * m02);
        double sy = java.lang.Math.sqrt(m10 * m10 + m11 * m11 + m12 * m12);
        double sz = java.lang.Math.sqrt(m20 * m20 + m21 * m21 + m22 * m22);
        dest.translation.set(m30, m31, m32);
        dest.scale.set(sx, sy, sz);

        double invSx = sx > 0.0 ? 1.0 / sx : 0.0;
        double invSy = sy > 0.0 ? 1.0 / sy : 0.0;
        double invSz = sz > 0.0 ? 1.0 / sz : 0.0;
        double r00 = m00 * invSx, r01 = m01 * invSx, r02 = m02 * invSx;
        double r10 = m10 * invSy, r11 = m11 * invSy, r12 = m12 * invSy;
        double r20 = m20 * invSz, r21 = m21 * invSz, r22 = m22 * invSz;

        double t;
        double tr = r00 + r11 + r22;
        if (tr >= 0.0) {
            t = java.lang.Math.sqrt(tr + 1.0);
            dest.rotation.w = t * 0.5;
            t = 0.5 / t;
            dest.rotation.x = (r12 - r21) * t;
            dest.rotation.y = (r20 - r02) * t;
            dest.rotation.z = (r01 - r10) * t;
        } else if (r00 >= r11 && r00 >= r22) {
            t = java.lang.Math.sqrt(r00 - (r11 + r22) + 1.0);
            dest.rotation.x = t * 0.5;
            t = 0.5 / t;
            dest.rotation.y = (r10 + r01) * t;
            dest.rotation.z = (r02 + r20) * t;
            dest.rotation.w = (r12 - r21) * t;
        } else if (r11 > r22) {
            t = java.lang.Math.sqrt(r11 - (r22 + r00) + 1.0);
            dest.rotation.y = t * 0.5;
            t = 0.5 / t;
            dest.rotation.z = (r21 + r12) * t;
            dest.rotation.x = (r10 + r01) * t;
            dest.rotation.w = (r20 - r02) * t;
        } else {
            t = java.lang.Math.sqrt(r22 - (r00 + r11) + 1.0);
            dest.rotation.z = t * 0.5;
            t = 0.5 / t;
            dest.rotation.x = (r02 + r20) * t;
            dest.rotation.y = (r21 + r12) * t;
            dest.rotation.w = (r01 - r10) * t;
        }
        return dest;
    }
}
