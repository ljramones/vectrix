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
     *
     * @param dest receives the matrix
     * @return {@code dest}
     */
    public Matrix4d toMatrix4d(Matrix4d dest) {
        return dest.set(
                m00, m01, m02, 0.0,
                m10, m11, m12, 0.0,
                m20, m21, m22, 0.0,
                m30, m31, m32, 1.0);
    }

    /**
     * Decomposes this transform into translation, rotation and scale, the inverse of
     * {@link #translationRotateScale(Vector3dc, Quaterniondc, Vector3dc)}. The rotation is always a unit quaternion
     * (to rounding), and the result is defined for every finite matrix:
     * <ul>
     *   <li>Each scale factor is the length of its column. For a reflection (negative determinant) the x scale is
     *       negative, so the rotation stays proper and the TRS still reproduces the matrix.</li>
     *   <li>The rotation is the orthonormal frame built from the columns by Gram-Schmidt in x, y, z order: it keeps
     *       the direction of the x column and the plane of the x and y columns. Without shear (orthogonal columns)
     *       the TRS reproduces this matrix; with shear it cannot, since a TRS holds no shear, and the result keeps
     *       the column lengths and the x column's direction.</li>
     *   <li>A zero-length column gives scale 0; the rotation axes are then taken from the remaining columns, and
     *       from fixed perpendicular directions when those are zero too (the zero matrix gives the identity
     *       rotation).</li>
     * </ul>
     *
     * @param dest receives the translation, rotation and scale
     * @return {@code dest}
     */
    public Transformd getTransform(Transformd dest) {
        double c0x = m00, c0y = m01, c0z = m02;
        double c1x = m10, c1y = m11, c1z = m12;
        double c2x = m20, c2y = m21, c2z = m22;
        double sx = java.lang.Math.sqrt(c0x * c0x + c0y * c0y + c0z * c0z);
        double sy = java.lang.Math.sqrt(c1x * c1x + c1y * c1y + c1z * c1z);
        double sz = java.lang.Math.sqrt(c2x * c2x + c2y * c2y + c2z * c2z);
        double det = c0x * (c1y * c2z - c1z * c2y) - c1x * (c0y * c2z - c0z * c2y) + c2x * (c0y * c1z - c0z * c1y);
        if (det < 0.0) {
            sx = -sx;
        }

        // r0: the x column's direction (flipped for a reflection), else the normal of the other two columns.
        double r0x, r0y, r0z;
        if (sx != 0.0) {
            double inv = 1.0 / sx;
            r0x = c0x * inv;
            r0y = c0y * inv;
            r0z = c0z * inv;
        } else {
            r0x = c1y * c2z - c1z * c2y;
            r0y = c1z * c2x - c1x * c2z;
            r0z = c1x * c2y - c1y * c2x;
            double len = java.lang.Math.sqrt(r0x * r0x + r0y * r0y + r0z * r0z);
            if (len > 0.0) {
                r0x /= len;
                r0y /= len;
                r0z /= len;
            } else {
                // At most one independent direction remains: make r0 perpendicular to it.
                double dx = sy > 0.0 ? c1x : c2x;
                double dy = sy > 0.0 ? c1y : c2y;
                double dz = sy > 0.0 ? c1z : c2z;
                if (dx == 0.0 && dy == 0.0 && dz == 0.0) {
                    r0x = 1.0;
                    r0y = 0.0;
                    r0z = 0.0;
                } else {
                    double ax = java.lang.Math.abs(dx), ay = java.lang.Math.abs(dy), az = java.lang.Math.abs(dz);
                    if (ax <= ay && ax <= az) {
                        r0x = 0.0;
                        r0y = dz;
                        r0z = -dy;
                    } else if (ay <= az) {
                        r0x = -dz;
                        r0y = 0.0;
                        r0z = dx;
                    } else {
                        r0x = dy;
                        r0y = -dx;
                        r0z = 0.0;
                    }
                    len = java.lang.Math.sqrt(r0x * r0x + r0y * r0y + r0z * r0z);
                    r0x /= len;
                    r0y /= len;
                    r0z /= len;
                }
            }
        }

        // r1: the y column without its r0 part, else derived from the z column, else any perpendicular.
        double d1 = c1x * r0x + c1y * r0y + c1z * r0z;
        double r1x = c1x - d1 * r0x, r1y = c1y - d1 * r0y, r1z = c1z - d1 * r0z;
        double len1 = java.lang.Math.sqrt(r1x * r1x + r1y * r1y + r1z * r1z);
        if (len1 > 0.0) {
            r1x /= len1;
            r1y /= len1;
            r1z /= len1;
        } else {
            double d2 = c2x * r0x + c2y * r0y + c2z * r0z;
            double ux = c2x - d2 * r0x, uy = c2y - d2 * r0y, uz = c2z - d2 * r0z;
            double len2 = java.lang.Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (len2 > 0.0) {
                // r1 = r2 x r0, with r2 the normalized remainder of the z column.
                ux /= len2;
                uy /= len2;
                uz /= len2;
                r1x = uy * r0z - uz * r0y;
                r1y = uz * r0x - ux * r0z;
                r1z = ux * r0y - uy * r0x;
            } else {
                // the unit axis least aligned with r0, made perpendicular to it (r0 = x gives r1 = y)
                double ax = java.lang.Math.abs(r0x), ay = java.lang.Math.abs(r0y), az = java.lang.Math.abs(r0z);
                double ex = 0.0, ey = 0.0, ez = 0.0;
                if (ax <= ay && ax <= az) {
                    ex = 1.0;
                } else if (ay <= az) {
                    ey = 1.0;
                } else {
                    ez = 1.0;
                }
                double k = ex * r0x + ey * r0y + ez * r0z;
                r1x = ex - k * r0x;
                r1y = ey - k * r0y;
                r1z = ez - k * r0z;
                double len = java.lang.Math.sqrt(r1x * r1x + r1y * r1y + r1z * r1z);
                r1x /= len;
                r1y /= len;
                r1z /= len;
            }
        }

        dest.translation.set(m30, m31, m32);
        dest.scale.set(sx, sy, sz);
        // The rotation matrix has columns r0, r1 and r2 = r0 x r1; rCR is column C, row R.
        double r00 = r0x, r01 = r0y, r02 = r0z;
        double r10 = r1x, r11 = r1y, r12 = r1z;
        double r20 = r0y * r1z - r0z * r1y;
        double r21 = r0z * r1x - r0x * r1z;
        double r22 = r0x * r1y - r0y * r1x;

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
