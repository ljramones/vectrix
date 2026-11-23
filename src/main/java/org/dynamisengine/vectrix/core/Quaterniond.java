/*
 * The MIT License
 *
 * Copyright (c) 2015-2024 Richard Greenlees
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
package org.dynamisengine.vectrix.core;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Quaternion of 4 double-precision floats which can represent rotation and uniform scaling.
 *
 * @author Richard Greenlees
 * @author Kai Burjack
 */
public class Quaterniond implements Externalizable, Cloneable, Quaterniondc {

    private static final long serialVersionUID = 1L;
    private static final double ZERO_TOLERANCE = 1E-12;

    /**
     * The number of bytes used to represent a {@code Quaterniond} value.
     */
    public static final int BYTES = 4*Double.BYTES;

    /**
     * The first component of the vector part.
     */
    public double x;
    /**
     * The second component of the vector part.
     */
    public double y;
    /**
     * The third component of the vector part.
     */
    public double z;
    /**
     * The real/scalar part of the quaternion.
     */
    public double w;

    /**
     * Create a new {@link Quaterniond} and initialize it with <code>(x=0, y=0, z=0, w=1)</code>, 
     * where <code>(x, y, z)</code> is the vector part of the quaternion and <code>w</code> is the real/scalar part.
     */
    public Quaterniond() {
        this.w = 1.0;
    }

    /**
     * Create a new {@link Quaterniond} and initialize its components to the given values.
     * 
     * @param x
     *          the first component of the imaginary part
     * @param y
     *          the second component of the imaginary part
     * @param z
     *          the third component of the imaginary part
     * @param w
     *          the real part
     */
    public Quaterniond(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    /**
     * Create a new {@link Quaterniond} and initialize its components to the same values as the given {@link Quaterniondc}.
     * 
     * @param source
     *          the {@link Quaterniondc} to take the component values from
     */
    public Quaterniond(Quaterniondc source) {
        x = source.x();
        y = source.y();
        z = source.z();
        w = source.w();
    }

    /**
     * Create a new {@link Quaterniond} and initialize its components to the same values as the given {@link Quaternionfc}.
     * 
     * @param source
     *          the {@link Quaternionfc} to take the component values from
     */
    public Quaterniond(Quaternionfc source) {
        x = source.x();
        y = source.y();
        z = source.z();
        w = source.w();
    }

    /**
     * Create a new {@link Quaterniond} and initialize it to represent the same rotation as the given {@link AxisAngle4f}.
     * 
     * @param axisAngle
     *          the axis-angle to initialize this quaternion with
     */
    public Quaterniond(AxisAngle4f axisAngle) {
        double s = Math.sin(axisAngle.angle * 0.5);
        x = axisAngle.x * s;
        y = axisAngle.y * s;
        z = axisAngle.z * s;
        w = Math.cosFromSin(s, axisAngle.angle * 0.5);
    }

    /**
     * Create a new {@link Quaterniond} and initialize it to represent the same rotation as the given {@link AxisAngle4d}.
     * 
     * @param axisAngle
     *          the axis-angle to initialize this quaternion with
     */
    public Quaterniond(AxisAngle4d axisAngle) {
        double s = Math.sin(axisAngle.angle * 0.5);
        x = axisAngle.x * s;
        y = axisAngle.y * s;
        z = axisAngle.z * s;
        w = Math.cosFromSin(s, axisAngle.angle * 0.5);
    }

    /**
     * @return the first component of the vector part
     */
    public double x() {
        return this.x;
    }

    /**
     * @return the second component of the vector part
     */
    public double y() {
        return this.y;
    }

    /**
     * @return the third component of the vector part
     */
    public double z() {
        return this.z;
    }

    /**
     * @return the real/scalar part of the quaternion
     */
    public double w() {
        return this.w;
    }

    /**
     * Normalize this quaternion.
     * 
     * @return this
     */
    public Quaterniond normalize() {
        double invNorm = 1.0 / Norms.norm4(x, y, z, w);
        x *= invNorm;
        y *= invNorm;
        z *= invNorm;
        w *= invNorm;
        return this;
    }

    public Quaterniond normalize(Quaterniond dest) {
        double invNorm = 1.0 / Norms.norm4(x, y, z, w);
        dest.x = x * invNorm;
        dest.y = y * invNorm;
        dest.z = z * invNorm;
        dest.w = w * invNorm;
        return dest;
    }

    /**
     * Add the quaternion <code>(x, y, z, w)</code> to this quaternion.
     * 
     * @param x
     *          the x component of the vector part
     * @param y
     *          the y component of the vector part
     * @param z
     *          the z component of the vector part
     * @param w
     *          the real/scalar component
     * @return this
     */
    public Quaterniond add(double x, double y, double z, double w) {
        return add(x, y, z, w, this);
    }

    public Quaterniond add(double x, double y, double z, double w, Quaterniond dest) {
        dest.x = this.x + x;
        dest.y = this.y + y;
        dest.z = this.z + z;
        dest.w = this.w + w;
        return dest;
    }

    /**
     * Add <code>q2</code> to this quaternion.
     * 
     * @param q2
     *          the quaternion to add to this
     * @return this
     */
    public Quaterniond add(Quaterniondc q2) { return add(q2, this); }

    public Quaterniond add(Quaterniondc q2, Quaterniond dest) {
        dest.x = x + q2.x();
        dest.y = y + q2.y();
        dest.z = z + q2.z();
        dest.w = w + q2.w();
        return dest;
    }

    /**
     * Subtract the quaternion <code>(x, y, z, w)</code> from this quaternion.
     *
     * @param x
     *          the x component of the vector part
     * @param y
     *          the y component of the vector part
     * @param z
     *          the z component of the vector part
     * @param w
     *          the real/scalar component
     * @return this
     */
    public Quaterniond sub(double x, double y, double z, double w) {
        return sub(x, y, z, w, this);
    }

    public Quaterniond sub(double x, double y, double z, double w, Quaterniond dest) {
        dest.x = this.x - x;
        dest.y = this.y - y;
        dest.z = this.z - z;
        dest.w = this.w - w;
        return dest;
    }

    /**
     * Subtract <code>q2</code> from this quaternion.
     *
     * @param q2
     *          the quaternion to add to this
     * @return this
     */
    public Quaterniond sub(Quaterniondc q2) {return sub(q2, this);}

    public Quaterniond sub(Quaterniondc q2, Quaterniond dest) {
        dest.x = x - q2.x();
        dest.y = y - q2.y();
        dest.z = z - q2.z();
        dest.w = w - q2.w();
        return dest;
    }

    public double dot(Quaterniondc otherQuat) {
        return this.x * otherQuat.x() + this.y * otherQuat.y() + this.z * otherQuat.z() + this.w * otherQuat.w();
    }

    public double angle() {
        // atan2 of the vector part: accurate for small angles, unlike 2 * acos(w)
        return 2.0 * AccurateMath.atan2(Math.sqrt(Math.fma(x, x, Math.fma(y, y, z * z))), w);
    }

    public Matrix3d get(Matrix3d dest) {
        return dest.set(this);
    }

    public Matrix3f get(Matrix3f dest) {
        return dest.set(this);
    }

    public Matrix4d get(Matrix4d dest) {
        return dest.set(this);
    }

    public Matrix4f get(Matrix4f dest) {
        return dest.set(this);
    }

    public AxisAngle4f get(AxisAngle4f dest) {
        double x = this.x;
        double y = this.y;
        double z = this.z;
        double w = this.w;
        if (w > 1.0) {
            double invNorm = 1.0 / Norms.norm4(x, y, z, w);
            x *= invNorm;
            y *= invNorm;
            z *= invNorm;
            w *= invNorm;
        }
        // Angle from atan2 and a unit axis from the vector part: acos(w) lost the angle for small rotations, and
        // below sqrt(1 - w^2) = 0.001 the axis was the raw (non-unit) vector part.
        double vLen = Math.sqrt(Math.fma(x, x, Math.fma(y, y, z * z)));
        dest.angle = (float) (2.0 * AccurateMath.atan2(vLen, w));
        if (vLen == 0) {
            dest.x = (float) x;
            dest.y = (float) y;
            dest.z = (float) z;
        } else {
            double inv = 1 / vLen;
            dest.x = (float) (x * inv);
            dest.y = (float) (y * inv);
            dest.z = (float) (z * inv);
        }
        return dest;
    }

    public AxisAngle4d get(AxisAngle4d dest) {
        double x = this.x;
        double y = this.y;
        double z = this.z;
        double w = this.w;
        if (w > 1.0) {
            double invNorm = 1.0 / Norms.norm4(x, y, z, w);
            x *= invNorm;
            y *= invNorm;
            z *= invNorm;
            w *= invNorm;
        }
        // Angle from atan2 and a unit axis from the vector part: acos(w) is inaccurate for small rotations, and
        // below sqrt(1 - w^2) = 0.001 the axis was the raw (non-unit) vector part.
        double vLen = Math.sqrt(Math.fma(x, x, Math.fma(y, y, z * z)));
        dest.angle = 2.0 * AccurateMath.atan2(vLen, w);
        if (vLen == 0) {
            dest.x = x;
            dest.y = y;
            dest.z = z;
        } else {
            double inv = 1 / vLen;
            dest.x = x * inv;
            dest.y = y * inv;
            dest.z = z * inv;
        }
        return dest;
    }

    /**
     * Set the given {@link Quaterniond} to the values of <code>this</code>.
     * 
     * @see #set(Quaterniondc)
     * 
     * @param dest
     *          the {@link Quaterniond} to set
     * @return the passed in destination
     */
    public Quaterniond get(Quaterniond dest) {
        return dest.set(this);
    }

    /**
     * Set the given {@link Quaternionf} to the values of <code>this</code>.
     * 
     * @see #set(Quaterniondc)
     * 
     * @param dest
     *          the {@link Quaternionf} to set
     * @return the passed in destination
     */
    public Quaternionf get(Quaternionf dest) {
        return dest.set(this);
    }

    /**
     * Set this quaternion to the new values.
     * 
     * @param x
     *          the new value of x
     * @param y
     *          the new value of y
     * @param z
     *          the new value of z
     * @param w
     *          the new value of w
     * @return this
     */
    public Quaterniond set(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        return this;
    }

    /**
     * Set this quaternion to be a copy of q.
     * 
     * @param q
     *          the {@link Quaterniondc} to copy
     * @return this
     */
    public Quaterniond set(Quaterniondc q) {
        x = q.x();
        y = q.y();
        z = q.z();
        w = q.w();
        return this;
    }

    /**
     * Set this quaternion to be a copy of q.
     * 
     * @param q
     *          the {@link Quaternionfc} to copy
     * @return this
     */
    public Quaterniond set(Quaternionfc q) {
        x = q.x();
        y = q.y();
        z = q.z();
        w = q.w();
        return this;
    }

    /**
     * Set this {@link Quaterniond} to be equivalent to the given
     * {@link AxisAngle4f}.
     * 
     * @param axisAngle
     *            the {@link AxisAngle4f}
     * @return this
     */
    public Quaterniond set(AxisAngle4f axisAngle) {
        return setAngleAxis(axisAngle.angle, axisAngle.x, axisAngle.y, axisAngle.z);
    }

    /**
     * Set this {@link Quaterniond} to be equivalent to the given
     * {@link AxisAngle4d}.
     * 
     * @param axisAngle
     *            the {@link AxisAngle4d}
     * @return this
     */
    public Quaterniond set(AxisAngle4d axisAngle) {
        return setAngleAxis(axisAngle.angle, axisAngle.x, axisAngle.y, axisAngle.z);
    }

    /**
     * Set this quaternion to a rotation equivalent to the supplied axis and
     * angle (in radians).
     * <p>
     * This method assumes that the given rotation axis <code>(x, y, z)</code> is already normalized
     * 
     * @param angle
     *          the angle in radians
     * @param x
     *          the x-component of the normalized rotation axis
     * @param y
     *          the y-component of the normalized rotation axis
     * @param z
     *          the z-component of the normalized rotation axis
     * @return this
     */
    public Quaterniond setAngleAxis(double angle, double x, double y, double z) {
        double s = Math.sin(angle * 0.5);
        this.x = x * s;
        this.y = y * s;
        this.z = z * s;
        this.w = Math.cosFromSin(s, angle * 0.5);
        return this;
    }

    /**
     * Set this quaternion to be a representation of the supplied axis and
     * angle (in radians).
     * 
     * @param angle
     *          the angle in radians
     * @param axis
     *          the rotation axis
     * @return this
     */
    public Quaterniond setAngleAxis(double angle, Vector3dc axis) {
        return setAngleAxis(angle, axis.x(), axis.y(), axis.z());
    }

    private void setFromUnnormalized(double m00, double m01, double m02, double m10, double m11, double m12, double m20, double m21, double m22) {
        double nm00 = m00, nm01 = m01, nm02 = m02;
        double nm10 = m10, nm11 = m11, nm12 = m12;
        double nm20 = m20, nm21 = m21, nm22 = m22;
        double lenX = Math.invsqrt(m00 * m00 + m01 * m01 + m02 * m02);
        double lenY = Math.invsqrt(m10 * m10 + m11 * m11 + m12 * m12);
        double lenZ = Math.invsqrt(m20 * m20 + m21 * m21 + m22 * m22);
        nm00 *= lenX; nm01 *= lenX; nm02 *= lenX;
        nm10 *= lenY; nm11 *= lenY; nm12 *= lenY;
        nm20 *= lenZ; nm21 *= lenZ; nm22 *= lenZ;
        setFromNormalized(nm00, nm01, nm02, nm10, nm11, nm12, nm20, nm21, nm22);
    }

    private void setFromNormalized(double m00, double m01, double m02, double m10, double m11, double m12, double m20, double m21, double m22) {
        double t;
        double tr = m00 + m11 + m22;
        if (tr >= 0.0) {
            t = Math.sqrt(tr + 1.0);
            w = t * 0.5;
            t = 0.5 / t;
            x = (m12 - m21) * t;
            y = (m20 - m02) * t;
            z = (m01 - m10) * t;
        } else {
            if (m00 >= m11 && m00 >= m22) {
                t = Math.sqrt(m00 - (m11 + m22) + 1.0);
                x = t * 0.5;
                t = 0.5 / t;
                y = (m10 + m01) * t;
                z = (m02 + m20) * t;
                w = (m12 - m21) * t;
            } else if (m11 > m22) {
                t = Math.sqrt(m11 - (m22 + m00) + 1.0);
                y = t * 0.5;
                t = 0.5 / t;
                z = (m21 + m12) * t;
                x = (m10 + m01) * t;
                w = (m20 - m02) * t;
            } else {
                t = Math.sqrt(m22 - (m00 + m11) + 1.0);
                z = t * 0.5;
                t = 0.5 / t;
                x = (m02 + m20) * t;
                y = (m21 + m12) * t;
                w = (m01 - m10) * t;
            }
        }
    }

    /**
     * Set this quaternion to be a representation of the rotational component of the given matrix.
     * <p>
     * This method assumes that the first three columns of the upper left 3x3 submatrix are no unit vectors.
     * 
     * <p>
     * The matrix must be a rotation, optionally with a positive scale per column. A mirror (negative determinant)
     * or a shear has no rotation part: the result is then neither a unit quaternion nor meaningful, and nothing
     * signals it. To split a general matrix into a rotation and a stretch, use
     * {@link org.dynamisengine.vectrix.linalg.Polar3d}.
     * @param mat
     *          the matrix whose rotational component is used to set this quaternion
     * @return this
     */
    public Quaterniond setFromUnnormalized(Matrix4fc mat) {
        setFromUnnormalized(mat.m00(), mat.m01(), mat.m02(), mat.m10(), mat.m11(), mat.m12(), mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    /**
     * Set this quaternion to be a representation of the rotational component of the given matrix.
     * <p>
     * This method assumes that the first three columns of the upper left 3x3 submatrix are no unit vectors.
     * 
     * <p>
     * The matrix must be a rotation, optionally with a positive scale per column. A mirror (negative determinant)
     * or a shear has no rotation part: the result is then neither a unit quaternion nor meaningful, and nothing
     * signals it. To split a general matrix into a rotation and a stretch, use
     * {@link org.dynamisengine.vectrix.linalg.Polar3d}.
     * @param mat
     *          the matrix whose rotational component is used to set this quaternion
     * @return this
     */
    public Quaterniond setFromUnnormalized(Matrix4x3fc mat) {
        setFromUnnormalized(mat.m00(), mat.m01(), mat.m02(), mat.m10(), mat.m11(), mat.m12(), mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    /**
     * Set this quaternion to be a representation of the rotational component of the given matrix.
     * <p>
     * This method assumes that the first three columns of the upper left 3x3 submatrix are no unit vectors.
     * 
     * <p>
     * The matrix must be a rotation, optionally with a positive scale per column. A mirror (negative determinant)
     * or a shear has no rotation part: the result is then neither a unit quaternion nor meaningful, and nothing
     * signals it. To split a general matrix into a rotation and a stretch, use
     * {@link org.dynamisengine.vectrix.linalg.Polar3d}.
     * @param mat
     *          the matrix whose rotational component is used to set this quaternion
     * @return this
     */
    public Quaterniond setFromUnnormalized(Matrix4x3dc mat) {
        setFromUnnormalized(mat.m00(), mat.m01(), mat.m02(), mat.m10(), mat.m11(), mat.m12(), mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    /**
     * Set this quaternion to be a representation of the rotational component of the given matrix.
     * <p>
     * This method assumes that the first three columns of the upper left 3x3 submatrix are unit vectors.
     * 
     * <p>
     * The matrix must be a rotation, optionally with a positive scale per column. A mirror (negative determinant)
     * or a shear has no rotation part: the result is then neither a unit quaternion nor meaningful, and nothing
     * signals it. To split a general matrix into a rotation and a stretch, use
     * {@link org.dynamisengine.vectrix.linalg.Polar3d}.
     * @param mat
     *          the matrix whose rotational component is used to set this quaternion
     * @return this
     */
    public Quaterniond setFromNormalized(Matrix4fc mat) {
        setFromNormalized(mat.m00(), mat.m01(), mat.m02(), mat.m10(), mat.m11(), mat.m12(), mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    /**
     * Set this quaternion to be a representation of the rotational component of the given matrix.
     * <p>
     * This method assumes that the first three columns of the upper left 3x3 submatrix are unit vectors.
     * 
     * <p>
     * The matrix must be a rotation, optionally with a positive scale per column. A mirror (negative determinant)
     * or a shear has no rotation part: the result is then neither a unit quaternion nor meaningful, and nothing
     * signals it. To split a general matrix into a rotation and a stretch, use
     * {@link org.dynamisengine.vectrix.linalg.Polar3d}.
     * @param mat
     *          the matrix whose rotational component is used to set this quaternion
     * @return this
     */
    public Quaterniond setFromNormalized(Matrix4x3fc mat) {
        setFromNormalized(mat.m00(), mat.m01(), mat.m02(), mat.m10(), mat.m11(), mat.m12(), mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    /**
     * Set this quaternion to be a representation of the rotational component of the given matrix.
     * <p>
     * This method assumes that the first three columns of the upper left 3x3 submatrix are unit vectors.
     * 
     * <p>
     * The matrix must be a rotation, optionally with a positive scale per column. A mirror (negative determinant)
     * or a shear has no rotation part: the result is then neither a unit quaternion nor meaningful, and nothing
     * signals it. To split a general matrix into a rotation and a stretch, use
     * {@link org.dynamisengine.vectrix.linalg.Polar3d}.
     * @param mat
     *          the matrix whose rotational component is used to set this quaternion
     * @return this
     */
    public Quaterniond setFromNormalized(Matrix4x3dc mat) {
        setFromNormalized(mat.m00(), mat.m01(), mat.m02(), mat.m10(), mat.m11(), mat.m12(), mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    /**
     * Set this quaternion to be a representation of the rotational component of the given matrix.
     * <p>
     * This method assumes that the first three columns of the upper left 3x3 submatrix are no unit vectors.
     * 
     * <p>
     * The matrix must be a rotation, optionally with a positive scale per column. A mirror (negative determinant)
     * or a shear has no rotation part: the result is then neither a unit quaternion nor meaningful, and nothing
     * signals it. To split a general matrix into a rotation and a stretch, use
     * {@link org.dynamisengine.vectrix.linalg.Polar3d}.
     * @param mat
     *          the matrix whose rotational component is used to set this quaternion
     * @return this
     */
    public Quaterniond setFromUnnormalized(Matrix4dc mat) {
        setFromUnnormalized(mat.m00(), mat.m01(), mat.m02(), mat.m10(), mat.m11(), mat.m12(), mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    /**
     * Set this quaternion to be a representation of the rotational component of the given matrix.
     * <p>
     * This method assumes that the first three columns of the upper left 3x3 submatrix are unit vectors.
     * 
     * <p>
     * The matrix must be a rotation, optionally with a positive scale per column. A mirror (negative determinant)
     * or a shear has no rotation part: the result is then neither a unit quaternion nor meaningful, and nothing
     * signals it. To split a general matrix into a rotation and a stretch, use
     * {@link org.dynamisengine.vectrix.linalg.Polar3d}.
     * @param mat
     *          the matrix whose rotational component is used to set this quaternion
     * @return this
     */
    public Quaterniond setFromNormalized(Matrix4dc mat) {
        setFromNormalized(mat.m00(), mat.m01(), mat.m02(), mat.m10(), mat.m11(), mat.m12(), mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    /**
     * Set this quaternion to be a representation of the rotational component of the given matrix.
     * <p>
     * This method assumes that the first three columns of the upper left 3x3 submatrix are no unit vectors.
     * 
     * <p>
     * The matrix must be a rotation, optionally with a positive scale per column. A mirror (negative determinant)
     * or a shear has no rotation part: the result is then neither a unit quaternion nor meaningful, and nothing
     * signals it. To split a general matrix into a rotation and a stretch, use
     * {@link org.dynamisengine.vectrix.linalg.Polar3d}.
     * @param mat
     *          the matrix whose rotational component is used to set this quaternion
     * @return this
     */
    public Quaterniond setFromUnnormalized(Matrix3fc mat) {
        setFromUnnormalized(mat.m00(), mat.m01(), mat.m02(), mat.m10(), mat.m11(), mat.m12(), mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    /**
     * Set this quaternion to be a representation of the rotational component of the given matrix.
     * <p>
     * This method assumes that the first three columns of the upper left 3x3 submatrix are unit vectors.
     * 
     * <p>
     * The matrix must be a rotation, optionally with a positive scale per column. A mirror (negative determinant)
     * or a shear has no rotation part: the result is then neither a unit quaternion nor meaningful, and nothing
     * signals it. To split a general matrix into a rotation and a stretch, use
     * {@link org.dynamisengine.vectrix.linalg.Polar3d}.
     * @param mat
     *          the matrix whose rotational component is used to set this quaternion
     * @return this
     */
    public Quaterniond setFromNormalized(Matrix3fc mat) {
        setFromNormalized(mat.m00(), mat.m01(), mat.m02(), mat.m10(), mat.m11(), mat.m12(), mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    /**
     * Set this quaternion to be a representation of the rotational component of the given matrix.
     * <p>
     * This method assumes that the first three columns of the upper left 3x3 submatrix are no unit vectors.
     * 
     * <p>
     * The matrix must be a rotation, optionally with a positive scale per column. A mirror (negative determinant)
     * or a shear has no rotation part: the result is then neither a unit quaternion nor meaningful, and nothing
     * signals it. To split a general matrix into a rotation and a stretch, use
     * {@link org.dynamisengine.vectrix.linalg.Polar3d}.
     * @param mat
     *          the matrix whose rotational component is used to set this quaternion
     * @return this
     */
    public Quaterniond setFromUnnormalized(Matrix3dc mat) {
        setFromUnnormalized(mat.m00(), mat.m01(), mat.m02(), mat.m10(), mat.m11(), mat.m12(), mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    /**
     * Set this quaternion to be a representation of the rotational component of the given matrix.
     * 
     * <p>
     * The matrix must be a rotation, optionally with a positive scale per column. A mirror (negative determinant)
     * or a shear has no rotation part: the result is then neither a unit quaternion nor meaningful, and nothing
     * signals it. To split a general matrix into a rotation and a stretch, use
     * {@link org.dynamisengine.vectrix.linalg.Polar3d}.
     * @param mat
     *          the matrix whose rotational component is used to set this quaternion
     * @return this
     */
    public Quaterniond setFromNormalized(Matrix3dc mat) {
        setFromNormalized(mat.m00(), mat.m01(), mat.m02(), mat.m10(), mat.m11(), mat.m12(), mat.m20(), mat.m21(), mat.m22());
        return this;
    }

    /**
     * Set this quaternion to be a representation of the supplied axis and
     * angle (in radians).
     * 
     * @param axis
     *          the rotation axis
     * @param angle
     *          the angle in radians
     * @return this
     */
    public Quaterniond fromAxisAngleRad(Vector3dc axis, double angle) {
        return fromAxisAngleRad(axis.x(), axis.y(), axis.z(), angle);
    }

    /**
     * Set this quaternion to be a representation of the supplied axis and
     * angle (in radians).
     * 
     * @param axisX
     *          the x component of the rotation axis
     * @param axisY
     *          the y component of the rotation axis
     * @param axisZ
     *          the z component of the rotation axis         
     * @param angle
     *          the angle in radians
     * @return this
     */
    public Quaterniond fromAxisAngleRad(double axisX, double axisY, double axisZ, double angle) {
        double hangle = angle / 2.0;
        double sinAngle = Math.sin(hangle);
        double invVLength = 1.0 / Norms.norm3(axisX, axisY, axisZ);
        double invVLengthTimesSinAngle = invVLength * sinAngle;
        x = axisX * invVLengthTimesSinAngle;
        y = axisY * invVLengthTimesSinAngle;
        z = axisZ * invVLengthTimesSinAngle;
        w = Math.cosFromSin(sinAngle, hangle);
        return this;
    }

    /**
     * Set this quaternion to be a representation of the supplied axis and
     * angle (in degrees).
     * 
     * @param axis
     *          the rotation axis
     * @param angle
     *          the angle in degrees
     * @return this
     */
    public Quaterniond fromAxisAngleDeg(Vector3dc axis, double angle) {
        return fromAxisAngleRad(axis.x(), axis.y(), axis.z(), Math.toRadians(angle));
    }

    /**
     * Set this quaternion to be a representation of the supplied axis and
     * angle (in degrees).
     * 
     * @param axisX
     *          the x component of the rotation axis
     * @param axisY
     *          the y component of the rotation axis
     * @param axisZ
     *          the z component of the rotation axis         
     * @param angle
     *          the angle in degrees
     * @return this
     */
    public Quaterniond fromAxisAngleDeg(double axisX, double axisY, double axisZ, double angle) {
        return fromAxisAngleRad(axisX, axisY, axisZ, Math.toRadians(angle));
    }

    /**
     * Multiply this quaternion by <code>q</code>.
     * <p>
     * If <code>T</code> is <code>this</code> and <code>Q</code> is the given
     * quaternion, then the resulting quaternion <code>R</code> is:
     * <p>
     * <code>R = T * Q</code>
     * <p>
     * So, this method uses post-multiplication like the matrix classes, resulting in a
     * vector to be transformed by <code>Q</code> first, and then by <code>T</code>.
     * 
     * @param q
     *          the quaternion to multiply <code>this</code> by
     * @return this
     */
    public Quaterniond mul(Quaterniondc q) {
        return mul(q, this);
    }

    public Quaterniond mul(Quaterniondc q, Quaterniond dest) {
        return mul(q.x(), q.y(), q.z(), q.w(), dest);
    }

    /**
     * Multiply this quaternion by the quaternion represented via <code>(qx, qy, qz, qw)</code>.
     * <p>
     * If <code>T</code> is <code>this</code> and <code>Q</code> is the given
     * quaternion, then the resulting quaternion <code>R</code> is:
     * <p>
     * <code>R = T * Q</code>
     * <p>
     * So, this method uses post-multiplication like the matrix classes, resulting in a
     * vector to be transformed by <code>Q</code> first, and then by <code>T</code>.
     * 
     * @param qx
     *          the x component of the quaternion to multiply <code>this</code> by
     * @param qy
     *          the y component of the quaternion to multiply <code>this</code> by
     * @param qz
     *          the z component of the quaternion to multiply <code>this</code> by
     * @param qw
     *          the w component of the quaternion to multiply <code>this</code> by
     * @return this
     */
    public Quaterniond mul(double qx, double qy, double qz, double qw) {
        return mul(qx, qy, qz, qw, this);
    }

    public Quaterniond mul(double qx, double qy, double qz, double qw, Quaterniond dest) {
        return dest.set(Math.fma(w, qx, Math.fma(x, qw, Math.fma(y, qz, -z * qy))),
                        Math.fma(w, qy, Math.fma(-x, qz, Math.fma(y, qw, z * qx))),
                        Math.fma(w, qz, Math.fma(x, qy, Math.fma(-y, qx, z * qw))),
                        Math.fma(w, qw, Math.fma(-x, qx, Math.fma(-y, qy, -z * qz))));
    }

    /**
     * Multiply this quaternion by the given scalar.
     * <p>
     * This method multiplies all of the four components by the specified scalar.
     * 
     * @param f
     *          the factor to multiply all components by
     * @return this
     */
    public Quaterniond mul(double f) {
        return mul(f, this);
    }

    public Quaterniond mul(double f, Quaterniond dest) {
        dest.x = x * f;
        dest.y = y * f;
        dest.z = z * f;
        dest.w = w * f;
        return dest;
    }

    /**
     * Pre-multiply this quaternion by <code>q</code>.
     * <p>
     * If <code>T</code> is <code>this</code> and <code>Q</code> is the given quaternion, then the resulting quaternion <code>R</code> is:
     * <p>
     * <code>R = Q * T</code>
     * <p>
     * So, this method uses pre-multiplication, resulting in a vector to be transformed by <code>T</code> first, and then by <code>Q</code>.
     * 
     * @param q
     *            the quaternion to pre-multiply <code>this</code> by
     * @return this
     */
    public Quaterniond premul(Quaterniondc q) {
        return premul(q, this);
    }

    public Quaterniond premul(Quaterniondc q, Quaterniond dest) {
        return premul(q.x(), q.y(), q.z(), q.w(), dest);
    }

    /**
     * Pre-multiply this quaternion by the quaternion represented via <code>(qx, qy, qz, qw)</code>.
     * <p>
     * If <code>T</code> is <code>this</code> and <code>Q</code> is the given quaternion, then the resulting quaternion <code>R</code> is:
     * <p>
     * <code>R = Q * T</code>
     * <p>
     * So, this method uses pre-multiplication, resulting in a vector to be transformed by <code>T</code> first, and then by <code>Q</code>.
     * 
     * @param qx
     *          the x component of the quaternion to multiply <code>this</code> by
     * @param qy
     *          the y component of the quaternion to multiply <code>this</code> by
     * @param qz
     *          the z component of the quaternion to multiply <code>this</code> by
     * @param qw
     *          the w component of the quaternion to multiply <code>this</code> by
     * @return this
     */
    public Quaterniond premul(double qx, double qy, double qz, double qw) {
        return premul(qx, qy, qz, qw, this);
    }

    public Quaterniond premul(double qx, double qy, double qz, double qw, Quaterniond dest) {
        return dest.set(Math.fma(qw, x, Math.fma(qx, w, Math.fma(qy, z, -qz * y))),
                        Math.fma(qw, y, Math.fma(-qx, z, Math.fma(qy, w, qz * x))),
                        Math.fma(qw, z, Math.fma(qx, y, Math.fma(-qy, x, qz * w))),
                        Math.fma(qw, w, Math.fma(-qx, x, Math.fma(-qy, y, -qz * z))));
    }

    public Vector3d transform(Vector3d vec){
        return transform(vec.x, vec.y, vec.z, vec);
    }

    public Vector3d transformInverse(Vector3d vec){
        return transformInverse(vec.x, vec.y, vec.z, vec);
    }

    public Vector3d transformUnit(Vector3d vec){
        return transformUnit(vec.x, vec.y, vec.z, vec);
    }

    public Vector3d transformInverseUnit(Vector3d vec){
        return transformInverseUnit(vec.x, vec.y, vec.z, vec);
    }

    public Vector3d transformPositiveX(Vector3d dest) {
        double k = 1.0 / Norms.norm4(x, y, z, w);
        double qx = x * k, qy = y * k, qz = z * k, qw = w * k;
        dest.x = (1 - 2 * (qy * qy + qz * qz));
        dest.y = (2 * (qx * qy + qw * qz));
        dest.z = (2 * (qx * qz - qw * qy));
        return dest;
    }

    public Vector4d transformPositiveX(Vector4d dest) {
        double ww = w * w;
        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double zw = z * w;
        double xy = x * y;
        double xz = x * z;
        double yw = y * w;
        dest.x = ww + xx - zz - yy;
        dest.y = xy + zw + zw + xy;
        dest.z = xz - yw + xz - yw;
        return dest;
    }

    public Vector3d transformUnitPositiveX(Vector3d dest) {
        double yy = y * y;
        double zz = z * z;
        double xy = x * y;
        double xz = x * z;
        double yw = y * w;
        double zw = z * w;
        dest.x = 1.0 - yy - yy - zz - zz;
        dest.y = xy + zw + xy + zw;
        dest.z = xz - yw + xz - yw;
        return dest;
    }

    public Vector4d transformUnitPositiveX(Vector4d dest) {
        double yy = y * y;
        double zz = z * z;
        double xy = x * y;
        double xz = x * z;
        double yw = y * w;
        double zw = z * w;
        dest.x = 1.0 - yy - yy - zz - zz;
        dest.y = xy + zw + xy + zw;
        dest.z = xz - yw + xz - yw;
        return dest;
    }

    public Vector3d transformPositiveY(Vector3d dest) {
        double k = 1.0 / Norms.norm4(x, y, z, w);
        double qx = x * k, qy = y * k, qz = z * k, qw = w * k;
        dest.x = (2 * (qx * qy - qw * qz));
        dest.y = (1 - 2 * (qx * qx + qz * qz));
        dest.z = (2 * (qy * qz + qw * qx));
        return dest;
    }

    public Vector4d transformPositiveY(Vector4d dest) {
        double ww = w * w;
        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double zw = z * w;
        double xy = x * y;
        double yz = y * z;
        double xw = x * w;
        dest.x = -zw + xy - zw + xy;
        dest.y = yy - zz + ww - xx;
        dest.z = yz + yz + xw + xw;
        return dest;
    }

    public Vector4d transformUnitPositiveY(Vector4d dest) {
        double xx = x * x;
        double zz = z * z;
        double xy = x * y;
        double yz = y * z;
        double xw = x * w;
        double zw = z * w;
        dest.x = xy - zw + xy - zw;
        dest.y = 1.0 - xx - xx - zz - zz;
        dest.z = yz + yz + xw + xw;
        return dest;
    }

    public Vector3d transformUnitPositiveY(Vector3d dest) {
        double xx = x * x;
        double zz = z * z;
        double xy = x * y;
        double yz = y * z;
        double xw = x * w;
        double zw = z * w;
        dest.x = xy - zw + xy - zw;
        dest.y = 1.0 - xx - xx - zz - zz;
        dest.z = yz + yz + xw + xw;
        return dest;
    }

    public Vector3d transformPositiveZ(Vector3d dest) {
        double k = 1.0 / Norms.norm4(x, y, z, w);
        double qx = x * k, qy = y * k, qz = z * k, qw = w * k;
        dest.x = (2 * (qx * qz + qw * qy));
        dest.y = (2 * (qy * qz - qw * qx));
        dest.z = (1 - 2 * (qx * qx + qy * qy));
        return dest;
    }

    public Vector4d transformPositiveZ(Vector4d dest) {
        double ww = w * w;
        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double xz = x * z;
        double yw = y * w;
        double yz = y * z;
        double xw = x * w;
        dest.x = yw + xz + xz + yw;
        dest.y = yz + yz - xw - xw;
        dest.z = zz - yy - xx + ww;
        return dest;
    }

    public Vector4d transformUnitPositiveZ(Vector4d dest) {
        double xx = x * x;
        double yy = y * y;
        double xz = x * z;
        double yz = y * z;
        double xw = x * w;
        double yw = y * w;
        dest.x = xz + yw + xz + yw;
        dest.y = yz + yz - xw - xw;
        dest.z = 1.0 - xx - xx - yy - yy;
        return dest;
    }

    public Vector3d transformUnitPositiveZ(Vector3d dest) {
        double xx = x * x;
        double yy = y * y;
        double xz = x * z;
        double yz = y * z;
        double xw = x * w;
        double yw = y * w;
        dest.x = xz + yw + xz + yw;
        dest.y = yz + yz - xw - xw;
        dest.z = 1.0 - xx - xx - yy - yy;
        return dest;
    }

    public Vector4d transform(Vector4d vec){
        return transform(vec, vec);
    }

    public Vector4d transformInverse(Vector4d vec){
        return transformInverse(vec, vec);
    }

    public Vector3d transform(Vector3dc vec, Vector3d dest) {
        return transform(vec.x(), vec.y(), vec.z(), dest);
    }

    public Vector3d transformInverse(Vector3dc vec, Vector3d dest) {
        return transformInverse(vec.x(), vec.y(), vec.z(), dest);
    }

    public Vector3d transform(double x, double y, double z, Vector3d dest) {
        double xx = this.x * this.x, yy = this.y * this.y, zz = this.z * this.z, ww = this.w * this.w;
        double xy = this.x * this.y, xz = this.x * this.z, yz = this.y * this.z, xw = this.x * this.w;
        double zw = this.z * this.w, yw = this.y * this.w, k = 1 / (xx + yy + zz + ww);
        return dest.set(Math.fma((xx - yy - zz + ww) * k, x, Math.fma(2 * (xy - zw) * k, y, (2 * (xz + yw) * k) * z)),
                        Math.fma(2 * (xy + zw) * k, x, Math.fma((yy - xx - zz + ww) * k, y, (2 * (yz - xw) * k) * z)),
                        Math.fma(2 * (xz - yw) * k, x, Math.fma(2 * (yz + xw) * k, y, ((zz - xx - yy + ww) * k) * z)));
    }

    public Vector3d transformInverse(double x, double y, double z, Vector3d dest) {
        double n = 1.0 / Math.fma(this.x, this.x, Math.fma(this.y, this.y, Math.fma(this.z, this.z, this.w * this.w)));
        double qx = this.x * n, qy = this.y * n, qz = this.z * n, qw = this.w * n;
        double xx = qx * qx, yy = qy * qy, zz = qz * qz, ww = qw * qw;
        double xy = qx * qy, xz = qx * qz, yz = qy * qz, xw = qx * qw;
        double zw = qz * qw, yw = qy * qw, k = 1 / (xx + yy + zz + ww);
        return dest.set(Math.fma((xx - yy - zz + ww) * k, x, Math.fma(2 * (xy + zw) * k, y, (2 * (xz - yw) * k) * z)),
                        Math.fma(2 * (xy - zw) * k, x, Math.fma((yy - xx - zz + ww) * k, y, (2 * (yz + xw) * k) * z)),
                        Math.fma(2 * (xz + yw) * k, x, Math.fma(2 * (yz - xw) * k, y, ((zz - xx - yy + ww) * k) * z)));
    }

    public Vector4d transform(Vector4dc vec, Vector4d dest) {
        // keep vec's w: it was left as dest's previous w, so the result depended on dest unless dest == vec
        double w = vec.w();
        transform(vec.x(), vec.y(), vec.z(), dest);
        dest.w = w;
        return dest;
    }

    public Vector4d transformInverse(Vector4dc vec, Vector4d dest) {
        // keep vec's w: it was left as dest's previous w, so the result depended on dest unless dest == vec
        double w = vec.w();
        transformInverse(vec.x(), vec.y(), vec.z(), dest);
        dest.w = w;
        return dest;
    }

    public Vector4d transform(double x, double y, double z, Vector4d dest) {
        double xx = this.x * this.x, yy = this.y * this.y, zz = this.z * this.z, ww = this.w * this.w;
        double xy = this.x * this.y, xz = this.x * this.z, yz = this.y * this.z, xw = this.x * this.w;
        double zw = this.z * this.w, yw = this.y * this.w, k = 1 / (xx + yy + zz + ww);
        return dest.set(Math.fma((xx - yy - zz + ww) * k, x, Math.fma(2 * (xy - zw) * k, y, (2 * (xz + yw) * k) * z)),
                        Math.fma(2 * (xy + zw) * k, x, Math.fma((yy - xx - zz + ww) * k, y, (2 * (yz - xw) * k) * z)),
                        Math.fma(2 * (xz - yw) * k, x, Math.fma(2 * (yz + xw) * k, y, ((zz - xx - yy + ww) * k) * z)), dest.w);
    }

    public Vector4d transformInverse(double x, double y, double z, Vector4d dest) {
        double n = 1.0 / Math.fma(this.x, this.x, Math.fma(this.y, this.y, Math.fma(this.z, this.z, this.w * this.w)));
        double qx = this.x * n, qy = this.y * n, qz = this.z * n, qw = this.w * n;
        double xx = qx * qx, yy = qy * qy, zz = qz * qz, ww = qw * qw;
        double xy = qx * qy, xz = qx * qz, yz = qy * qz, xw = qx * qw;
        double zw = qz * qw, yw = qy * qw, k = 1 / (xx + yy + zz + ww);
        return dest.set(Math.fma((xx - yy - zz + ww) * k, x, Math.fma(2 * (xy + zw) * k, y, (2 * (xz - yw) * k) * z)),
                        Math.fma(2 * (xy - zw) * k, x, Math.fma((yy - xx - zz + ww) * k, y, (2 * (yz + xw) * k) * z)),
                        Math.fma(2 * (xz + yw) * k, x, Math.fma(2 * (yz - xw) * k, y, ((zz - xx - yy + ww) * k) * z)));
    }

    public Vector3f transform(Vector3f vec){
        return transform(vec.x, vec.y, vec.z, vec);
    }

    public Vector3f transformInverse(Vector3f vec){
        return transformInverse(vec.x, vec.y, vec.z, vec);
    }

    public Vector4d transformUnit(Vector4d vec){
        return transformUnit(vec, vec);
    }

    public Vector4d transformInverseUnit(Vector4d vec){
        return transformInverseUnit(vec, vec);
    }

    public Vector3d transformUnit(Vector3dc vec, Vector3d dest) {
        return transformUnit(vec.x(), vec.y(), vec.z(), dest);
    }

    public Vector3d transformInverseUnit(Vector3dc vec, Vector3d dest) {
        return transformInverseUnit(vec.x(), vec.y(), vec.z(), dest);
    }

    public Vector3d transformUnit(double x, double y, double z, Vector3d dest) {
        double xx = this.x * this.x, xy = this.x * this.y, xz = this.x * this.z;
        double xw = this.x * this.w, yy = this.y * this.y, yz = this.y * this.z;
        double yw = this.y * this.w, zz = this.z * this.z, zw = this.z * this.w;
        return dest.set(Math.fma(Math.fma(-2, yy + zz, 1), x, Math.fma(2 * (xy - zw), y, (2 * (xz + yw)) * z)),
                        Math.fma(2 * (xy + zw), x, Math.fma(Math.fma(-2, xx + zz, 1), y, (2 * (yz - xw)) * z)),
                        Math.fma(2 * (xz - yw), x, Math.fma(2 * (yz + xw), y, Math.fma(-2, xx + yy, 1) * z)));
    }

    public Vector3d transformInverseUnit(double x, double y, double z, Vector3d dest) {
        double xx = this.x * this.x, xy = this.x * this.y, xz = this.x * this.z;
        double xw = this.x * this.w, yy = this.y * this.y, yz = this.y * this.z;
        double yw = this.y * this.w, zz = this.z * this.z, zw = this.z * this.w;
        return dest.set(Math.fma(Math.fma(-2, yy + zz, 1), x, Math.fma(2 * (xy + zw), y, (2 * (xz - yw)) * z)),
                        Math.fma(2 * (xy - zw), x, Math.fma(Math.fma(-2, xx + zz, 1), y, (2 * (yz + xw)) * z)),
                        Math.fma(2 * (xz + yw), x, Math.fma(2 * (yz - xw), y, Math.fma(-2, xx + yy, 1) * z)));
    }

    public Vector4d transformUnit(Vector4dc vec, Vector4d dest) {
        // keep vec's w: it was left as dest's previous w, so the result depended on dest unless dest == vec
        double w = vec.w();
        transformUnit(vec.x(), vec.y(), vec.z(), dest);
        dest.w = w;
        return dest;
    }

    public Vector4d transformInverseUnit(Vector4dc vec, Vector4d dest) {
        // keep vec's w: it was left as dest's previous w, so the result depended on dest unless dest == vec
        double w = vec.w();
        transformInverseUnit(vec.x(), vec.y(), vec.z(), dest);
        dest.w = w;
        return dest;
    }

    public Vector4d transformUnit(double x, double y, double z, Vector4d dest) {
        double xx = this.x * this.x, xy = this.x * this.y, xz = this.x * this.z;
        double xw = this.x * this.w, yy = this.y * this.y, yz = this.y * this.z;
        double yw = this.y * this.w, zz = this.z * this.z, zw = this.z * this.w;
        return dest.set(Math.fma(Math.fma(-2, yy + zz, 1), x, Math.fma(2 * (xy - zw), y, (2 * (xz + yw)) * z)),
                        Math.fma(2 * (xy + zw), x, Math.fma(Math.fma(-2, xx + zz, 1), y, (2 * (yz - xw)) * z)),
                        Math.fma(2 * (xz - yw), x, Math.fma(2 * (yz + xw), y, Math.fma(-2, xx + yy, 1) * z)),
                        dest.w);
    }

    public Vector4d transformInverseUnit(double x, double y, double z, Vector4d dest) {
        double xx = this.x * this.x, xy = this.x * this.y, xz = this.x * this.z;
        double xw = this.x * this.w, yy = this.y * this.y, yz = this.y * this.z;
        double yw = this.y * this.w, zz = this.z * this.z, zw = this.z * this.w;
        return dest.set(Math.fma(Math.fma(-2, yy + zz, 1), x, Math.fma(2 * (xy + zw), y, (2 * (xz - yw)) * z)),
                        Math.fma(2 * (xy - zw), x, Math.fma(Math.fma(-2, xx + zz, 1), y, (2 * (yz + xw)) * z)),
                        Math.fma(2 * (xz + yw), x, Math.fma(2 * (yz - xw), y, Math.fma(-2, xx + yy, 1) * z)),
                        dest.w);
    }

    public Vector3f transformUnit(Vector3f vec){
        return transformUnit(vec.x, vec.y, vec.z, vec);
    }

    public Vector3f transformInverseUnit(Vector3f vec){
        return transformInverseUnit(vec.x, vec.y, vec.z, vec);
    }

    public Vector3f transformPositiveX(Vector3f dest) {
        double ww = w * w;
        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double zw = z * w;
        double xy = x * y;
        double xz = x * z;
        double yw = y * w;
        dest.x = (float) (ww + xx - zz - yy);
        dest.y = (float) (xy + zw + zw + xy);
        dest.z = (float) (xz - yw + xz - yw);
        return dest;
    }

    public Vector4f transformPositiveX(Vector4f dest) {
        double ww = w * w;
        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double zw = z * w;
        double xy = x * y;
        double xz = x * z;
        double yw = y * w;
        dest.x = (float) (ww + xx - zz - yy);
        dest.y = (float) (xy + zw + zw + xy);
        dest.z = (float) (xz - yw + xz - yw);
        return dest;
    }

    public Vector3f transformUnitPositiveX(Vector3f dest) {
        double yy = y * y;
        double zz = z * z;
        double xy = x * y;
        double xz = x * z;
        double yw = y * w;
        double zw = z * w;
        dest.x = (float) (1.0 - yy - yy - zz - zz);
        dest.y = (float) (xy + zw + xy + zw);
        dest.z = (float) (xz - yw + xz - yw);
        return dest;
    }

    public Vector4f transformUnitPositiveX(Vector4f dest) {
        double yy = y * y;
        double zz = z * z;
        double xy = x * y;
        double xz = x * z;
        double yw = y * w;
        double zw = z * w;
        dest.x = (float) (1.0 - yy - yy - zz - zz);
        dest.y = (float) (xy + zw + xy + zw);
        dest.z = (float) (xz - yw + xz - yw);
        return dest;
    }

    public Vector3f transformPositiveY(Vector3f dest) {
        double ww = w * w;
        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double zw = z * w;
        double xy = x * y;
        double yz = y * z;
        double xw = x * w;
        dest.x = (float) (-zw + xy - zw + xy);
        dest.y = (float) (yy - zz + ww - xx);
        dest.z = (float) (yz + yz + xw + xw);
        return dest;
    }

    public Vector4f transformPositiveY(Vector4f dest) {
        double ww = w * w;
        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double zw = z * w;
        double xy = x * y;
        double yz = y * z;
        double xw = x * w;
        dest.x = (float) (-zw + xy - zw + xy);
        dest.y = (float) (yy - zz + ww - xx);
        dest.z = (float) (yz + yz + xw + xw);
        return dest;
    }

    public Vector4f transformUnitPositiveY(Vector4f dest) {
        double xx = x * x;
        double zz = z * z;
        double xy = x * y;
        double yz = y * z;
        double xw = x * w;
        double zw = z * w;
        dest.x = (float) (xy - zw + xy - zw);
        dest.y = (float) (1.0 - xx - xx - zz - zz);
        dest.z = (float) (yz + yz + xw + xw);
        return dest;
    }

    public Vector3f transformUnitPositiveY(Vector3f dest) {
        double xx = x * x;
        double zz = z * z;
        double xy = x * y;
        double yz = y * z;
        double xw = x * w;
        double zw = z * w;
        dest.x = (float) (xy - zw + xy - zw);
        dest.y = (float) (1.0 - xx - xx - zz - zz);
        dest.z = (float) (yz + yz + xw + xw);
        return dest;
    }

    public Vector3f transformPositiveZ(Vector3f dest) {
        double ww = w * w;
        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double xz = x * z;
        double yw = y * w;
        double yz = y * z;
        double xw = x * w;
        dest.x = (float) (yw + xz + xz + yw);
        dest.y = (float) (yz + yz - xw - xw);
        dest.z = (float) (zz - yy - xx + ww);
        return dest;
    }

    public Vector4f transformPositiveZ(Vector4f dest) {
        double ww = w * w;
        double xx = x * x;
        double yy = y * y;
        double zz = z * z;
        double xz = x * z;
        double yw = y * w;
        double yz = y * z;
        double xw = x * w;
        dest.x = (float) (yw + xz + xz + yw);
        dest.y = (float) (yz + yz - xw - xw);
        dest.z = (float) (zz - yy - xx + ww);
        return dest;
    }

    public Vector4f transformUnitPositiveZ(Vector4f dest) {
        double xx = x * x;
        double yy = y * y;
        double xz = x * z;
        double yz = y * z;
        double xw = x * w;
        double yw = y * w;
        dest.x = (float) (xz + yw + xz + yw);
        dest.y = (float) (yz + yz - xw - xw);
        dest.z = (float) (1.0 - xx - xx - yy - yy);
        return dest;
    }

    public Vector3f transformUnitPositiveZ(Vector3f dest) {
        double xx = x * x;
        double yy = y * y;
        double xz = x * z;
        double yz = y * z;
        double xw = x * w;
        double yw = y * w;
        dest.x = (float) (xz + yw + xz + yw);
        dest.y = (float) (yz + yz - xw - xw);
        dest.z = (float) (1.0 - xx - xx - yy - yy);
        return dest;
    }

    public Vector4f transform(Vector4f vec){
        return transform(vec, vec);
    }

    public Vector4f transformInverse(Vector4f vec){
        return transformInverse(vec, vec);
    }

    public Vector3f transform(Vector3fc vec, Vector3f dest) {
        return transform(vec.x(), vec.y(), vec.z(), dest);
    }

    public Vector3f transformInverse(Vector3fc vec, Vector3f dest) {
        return transformInverse(vec.x(), vec.y(), vec.z(), dest);
    }

    public Vector3f transform(double x, double y, double z, Vector3f dest) {
        double xx = this.x * this.x, yy = this.y * this.y, zz = this.z * this.z, ww = this.w * this.w;
        double xy = this.x * this.y, xz = this.x * this.z, yz = this.y * this.z, xw = this.x * this.w;
        double zw = this.z * this.w, yw = this.y * this.w, k = 1 / (xx + yy + zz + ww);
        return dest.set(Math.fma((xx - yy - zz + ww) * k, x, Math.fma(2 * (xy - zw) * k, y, (2 * (xz + yw) * k) * z)),
                        Math.fma(2 * (xy + zw) * k, x, Math.fma((yy - xx - zz + ww) * k, y, (2 * (yz - xw) * k) * z)),
                        Math.fma(2 * (xz - yw) * k, x, Math.fma(2 * (yz + xw) * k, y, ((zz - xx - yy + ww) * k) * z)));
    }

    public Vector3f transformInverse(double x, double y, double z, Vector3f dest) {
        double n = 1.0 / Math.fma(this.x, this.x, Math.fma(this.y, this.y, Math.fma(this.z, this.z, this.w * this.w)));
        double qx = this.x * n, qy = this.y * n, qz = this.z * n, qw = this.w * n;
        double xx = qx * qx, yy = qy * qy, zz = qz * qz, ww = qw * qw;
        double xy = qx * qy, xz = qx * qz, yz = qy * qz, xw = qx * qw;
        double zw = qz * qw, yw = qy * qw, k = 1 / (xx + yy + zz + ww);
        return dest.set(Math.fma((xx - yy - zz + ww) * k, x, Math.fma(2 * (xy + zw) * k, y, (2 * (xz - yw) * k) * z)),
                        Math.fma(2 * (xy - zw) * k, x, Math.fma((yy - xx - zz + ww) * k, y, (2 * (yz + xw) * k) * z)),
                        Math.fma(2 * (xz + yw) * k, x, Math.fma(2 * (yz - xw) * k, y, ((zz - xx - yy + ww) * k) * z)));
    }

    public Vector4f transform(Vector4fc vec, Vector4f dest) {
        // keep vec's w: it was left as dest's previous w, so the result depended on dest unless dest == vec
        float w = vec.w();
        transform(vec.x(), vec.y(), vec.z(), dest);
        dest.w = w;
        return dest;
    }

    public Vector4f transformInverse(Vector4fc vec, Vector4f dest) {
        // keep vec's w: it was left as dest's previous w, so the result depended on dest unless dest == vec
        float w = vec.w();
        transformInverse(vec.x(), vec.y(), vec.z(), dest);
        dest.w = w;
        return dest;
    }

    public Vector4f transform(double x, double y, double z, Vector4f dest) {
        double xx = this.x * this.x, yy = this.y * this.y, zz = this.z * this.z, ww = this.w * this.w;
        double xy = this.x * this.y, xz = this.x * this.z, yz = this.y * this.z, xw = this.x * this.w;
        double zw = this.z * this.w, yw = this.y * this.w, k = 1 / (xx + yy + zz + ww);
        return dest.set((float) Math.fma((xx - yy - zz + ww) * k, x, Math.fma(2 * (xy - zw) * k, y, (2 * (xz + yw) * k) * z)),
                        (float) Math.fma(2 * (xy + zw) * k, x, Math.fma((yy - xx - zz + ww) * k, y, (2 * (yz - xw) * k) * z)),
                        (float) Math.fma(2 * (xz - yw) * k, x, Math.fma(2 * (yz + xw) * k, y, ((zz - xx - yy + ww) * k) * z)), dest.w);
    }

    public Vector4f transformInverse(double x, double y, double z, Vector4f dest) {
        double n = 1.0 / Math.fma(this.x, this.x, Math.fma(this.y, this.y, Math.fma(this.z, this.z, this.w * this.w)));
        double qx = this.x * n, qy = this.y * n, qz = this.z * n, qw = this.w * n;
        double xx = qx * qx, yy = qy * qy, zz = qz * qz, ww = qw * qw;
        double xy = qx * qy, xz = qx * qz, yz = qy * qz, xw = qx * qw;
        double zw = qz * qw, yw = qy * qw, k = 1 / (xx + yy + zz + ww);
        return dest.set(Math.fma((xx - yy - zz + ww) * k, x, Math.fma(2 * (xy + zw) * k, y, (2 * (xz - yw) * k) * z)),
                        Math.fma(2 * (xy - zw) * k, x, Math.fma((yy - xx - zz + ww) * k, y, (2 * (yz + xw) * k) * z)),
                        Math.fma(2 * (xz + yw) * k, x, Math.fma(2 * (yz - xw) * k, y, ((zz - xx - yy + ww) * k) * z)), dest.w);
    }

    public Vector4f transformUnit(Vector4f vec){
        return transformUnit(vec, vec);
    }

    public Vector4f transformInverseUnit(Vector4f vec){
        return transformInverseUnit(vec, vec);
    }

    public Vector3f transformUnit(Vector3fc vec, Vector3f dest) {
        return transformUnit(vec.x(), vec.y(), vec.z(), dest);
    }

    public Vector3f transformInverseUnit(Vector3fc vec, Vector3f dest) {
        return transformInverseUnit(vec.x(), vec.y(), vec.z(), dest);
    }

    public Vector3f transformUnit(double x, double y, double z, Vector3f dest) {
        double xx = this.x * this.x, xy = this.x * this.y, xz = this.x * this.z;
        double xw = this.x * this.w, yy = this.y * this.y, yz = this.y * this.z;
        double yw = this.y * this.w, zz = this.z * this.z, zw = this.z * this.w;
        return dest.set((float) Math.fma(Math.fma(-2, yy + zz, 1), x, Math.fma(2 * (xy - zw), y, (2 * (xz + yw)) * z)),
                        (float) Math.fma(2 * (xy + zw), x, Math.fma(Math.fma(-2, xx + zz, 1), y, (2 * (yz - xw)) * z)),
                        (float) Math.fma(2 * (xz - yw), x, Math.fma(2 * (yz + xw), y, Math.fma(-2, xx + yy, 1) * z)));
    }

    public Vector3f transformInverseUnit(double x, double y, double z, Vector3f dest) {
        double xx = this.x * this.x, xy = this.x * this.y, xz = this.x * this.z;
        double xw = this.x * this.w, yy = this.y * this.y, yz = this.y * this.z;
        double yw = this.y * this.w, zz = this.z * this.z, zw = this.z * this.w;
        return dest.set((float) Math.fma(Math.fma(-2, yy + zz, 1), x, Math.fma(2 * (xy + zw), y, (2 * (xz - yw)) * z)),
                        (float) Math.fma(2 * (xy - zw), x, Math.fma(Math.fma(-2, xx + zz, 1), y, (2 * (yz + xw)) * z)),
                        (float) Math.fma(2 * (xz + yw), x, Math.fma(2 * (yz - xw), y, Math.fma(-2, xx + yy, 1) * z)));
    }

    public Vector4f transformUnit(Vector4fc vec, Vector4f dest) {
        // keep vec's w: it was left as dest's previous w, so the result depended on dest unless dest == vec
        float w = vec.w();
        transformUnit(vec.x(), vec.y(), vec.z(), dest);
        dest.w = w;
        return dest;
    }

    public Vector4f transformInverseUnit(Vector4fc vec, Vector4f dest) {
        // keep vec's w: it was left as dest's previous w, so the result depended on dest unless dest == vec
        float w = vec.w();
        transformInverseUnit(vec.x(), vec.y(), vec.z(), dest);
        dest.w = w;
        return dest;
    }

    public Vector4f transformUnit(double x, double y, double z, Vector4f dest) {
        double xx = this.x * this.x, xy = this.x * this.y, xz = this.x * this.z;
        double xw = this.x * this.w, yy = this.y * this.y, yz = this.y * this.z;
        double yw = this.y * this.w, zz = this.z * this.z, zw = this.z * this.w;
        return dest.set((float) Math.fma(Math.fma(-2, yy + zz, 1), x, Math.fma(2 * (xy - zw), y, (2 * (xz + yw)) * z)),
                        (float) Math.fma(2 * (xy + zw), x, Math.fma(Math.fma(-2, xx + zz, 1), y, (2 * (yz - xw)) * z)),
                        (float) Math.fma(2 * (xz - yw), x, Math.fma(2 * (yz + xw), y, Math.fma(-2, xx + yy, 1) * z)));
    }

    public Vector4f transformInverseUnit(double x, double y, double z, Vector4f dest) {
        double xx = this.x * this.x, xy = this.x * this.y, xz = this.x * this.z;
        double xw = this.x * this.w, yy = this.y * this.y, yz = this.y * this.z;
        double yw = this.y * this.w, zz = this.z * this.z, zw = this.z * this.w;
        return dest.set((float) Math.fma(Math.fma(-2, yy + zz, 1), x, Math.fma(2 * (xy + zw), y, (2 * (xz - yw)) * z)),
                        (float) Math.fma(2 * (xy - zw), x, Math.fma(Math.fma(-2, xx + zz, 1), y, (2 * (yz + xw)) * z)),
                        (float) Math.fma(2 * (xz + yw), x, Math.fma(2 * (yz - xw), y, Math.fma(-2, xx + yy, 1) * z)));
    }

    public Quaterniond invert(Quaterniond dest) {
        double invNorm = 1.0 / lengthSquared();
        dest.x = -x * invNorm;
        dest.y = -y * invNorm;
        dest.z = -z * invNorm;
        dest.w = w * invNorm;
        return dest;
    }

    /**
     * Invert this quaternion and {@link #normalize() normalize} it.
     * <p>
     * If this quaternion is already normalized, then {@link #conjugate()} should be used instead.
     * 
     * @see #conjugate()
     * 
     * @return this
     */
    public Quaterniond invert() {
        return invert(this);
    }

    public Quaterniond div(Quaterniondc b, Quaterniond dest) {
        double invNorm = 1.0 / Math.fma(b.x(), b.x(), Math.fma(b.y(), b.y(), Math.fma(b.z(), b.z(), b.w() * b.w())));
        double x = -b.x() * invNorm;
        double y = -b.y() * invNorm;
        double z = -b.z() * invNorm;
        double w = b.w() * invNorm;
        return dest.set(Math.fma(this.w, x, Math.fma(this.x, w, Math.fma(this.y, z, -this.z * y))),
                        Math.fma(this.w, y, Math.fma(-this.x, z, Math.fma(this.y, w, this.z * x))),
                        Math.fma(this.w, z, Math.fma(this.x, y, Math.fma(-this.y, x, this.z * w))),
                        Math.fma(this.w, w, Math.fma(-this.x, x, Math.fma(-this.y, y, -this.z * z))));
    }

    /**
     * Divide <code>this</code> quaternion by <code>b</code>.
     * <p>
     * The division expressed using the inverse is performed in the following way:
     * <p>
     * <code>this = this * b^-1</code>, where <code>b^-1</code> is the inverse of <code>b</code>.
     * 
     * @param b
     *          the {@link Quaterniondc} to divide this by
     * @return this
     */
    public Quaterniond div(Quaterniondc b) {
        return div(b, this);
    }

    public Quaterniond div(double d, Quaterniond dest) {
        dest.x = x / d;
        dest.y = y / d;
        dest.z = z / d;
        dest.w = w / d;
        return dest; // returned this, so the result was not in the returned object
    }

    /**
     * Divide this quaternion by the given scalar.
     * <p>
     * This method divides all the four components by the specified scalar.
     *
     * @param d
     *          the factor to divide all components by
     * @return this
     */
    public Quaterniond div(double d) {
        return div(d, this);
    }

    /**
     * Conjugate this quaternion.
     * 
     * @return this
     */
    public Quaterniond conjugate() {
        x = -x;
        y = -y;
        z = -z;
        return this;
    }

    public Quaterniond conjugate(Quaterniond dest) {
        dest.x = -x;
        dest.y = -y;
        dest.z = -z;
        dest.w = w;
        return dest;
    }

    /**
     * Set this quaternion to the identity.
     * 
     * @return this
     */
    public Quaterniond identity() {
        x = 0.0;
        y = 0.0;
        z = 0.0;
        w = 1.0;
        return this;
    }

    public double lengthSquared() {
        return Math.fma(x, x, Math.fma(y, y, Math.fma(z, z, w * w)));
    }

    /**
     * Set this quaternion from the supplied euler angles (in radians) with rotation order XYZ.
     * <p>
     * This method is equivalent to calling: <code>rotationX(angleX).rotateY(angleY).rotateZ(angleZ)</code>
     * <p>
     * Reference: <a href="http://gamedev.stackexchange.com/questions/13436/glm-euler-angles-to-quaternion#answer-13446">this stackexchange answer</a>
     * 
     * @param angleX
     *          the angle in radians to rotate about x
     * @param angleY
     *          the angle in radians to rotate about y
     * @param angleZ
     *          the angle in radians to rotate about z
     * @return this
     */
    public Quaterniond rotationXYZ(double angleX, double angleY, double angleZ) {
        double sx = Math.sin(angleX * 0.5);
        double cx = Math.cosFromSin(sx, angleX * 0.5);
        double sy = Math.sin(angleY * 0.5);
        double cy = Math.cosFromSin(sy, angleY * 0.5);
        double sz = Math.sin(angleZ * 0.5);
        double cz = Math.cosFromSin(sz, angleZ * 0.5);

        double cycz = cy * cz;
        double sysz = sy * sz;
        double sycz = sy * cz;
        double cysz = cy * sz;
        w = cx*cycz - sx*sysz;
        x = sx*cycz + cx*sysz;
        y = cx*sycz - sx*cysz;
        z = cx*cysz + sx*sycz;

        return this;
    }

    /**
     * Set this quaternion from the supplied euler angles (in radians) with rotation order ZYX.
     * <p>
     * This method is equivalent to calling: <code>rotationZ(angleZ).rotateY(angleY).rotateX(angleX)</code>
     * <p>
     * Reference: <a href="http://gamedev.stackexchange.com/questions/13436/glm-euler-angles-to-quaternion#answer-13446">this stackexchange answer</a>
     * 
     * @param angleX
     *          the angle in radians to rotate about x
     * @param angleY
     *          the angle in radians to rotate about y
     * @param angleZ
     *          the angle in radians to rotate about z
     * @return this
     */
    public Quaterniond rotationZYX(double angleZ, double angleY, double angleX) {
        double sx = Math.sin(angleX * 0.5);
        double cx = Math.cosFromSin(sx, angleX * 0.5);
        double sy = Math.sin(angleY * 0.5);
        double cy = Math.cosFromSin(sy, angleY * 0.5);
        double sz = Math.sin(angleZ * 0.5);
        double cz = Math.cosFromSin(sz, angleZ * 0.5);

        double cycz = cy * cz;
        double sysz = sy * sz;
        double sycz = sy * cz;
        double cysz = cy * sz;
        w = cx*cycz + sx*sysz;
        x = sx*cycz - cx*sysz;
        y = cx*sycz + sx*cysz;
        z = cx*cysz - sx*sycz;

        return this;
    }

    /**
     * Set this quaternion from the supplied euler angles (in radians) with rotation order YXZ.
     * <p>
     * This method is equivalent to calling: <code>rotationY(angleY).rotateX(angleX).rotateZ(angleZ)</code>
     * <p>
     * Reference: <a href="https://en.wikipedia.org/wiki/Conversion_between_quaternions_and_Euler_angles">https://en.wikipedia.org</a>
     * 
     * @param angleY
     *          the angle in radians to rotate about y
     * @param angleX
     *          the angle in radians to rotate about x
     * @param angleZ
     *          the angle in radians to rotate about z
     * @return this
     */
    public Quaterniond rotationYXZ(double angleY, double angleX, double angleZ) {
        double sx = Math.sin(angleX * 0.5);
        double cx = Math.cosFromSin(sx, angleX * 0.5);
        double sy = Math.sin(angleY * 0.5);
        double cy = Math.cosFromSin(sy, angleY * 0.5);
        double sz = Math.sin(angleZ * 0.5);
        double cz = Math.cosFromSin(sz, angleZ * 0.5);

        double x = cy * sx;
        double y = sy * cx;
        double z = sy * sx;
        double w = cy * cx;
        this.x = x * cz + y * sz;
        this.y = y * cz - x * sz;
        this.z = w * sz - z * cz;
        this.w = w * cz + z * sz;

        return this;
    }

    /**
     * Interpolate between <code>this</code> {@link #normalize() unit} quaternion and the specified
     * <code>target</code> {@link #normalize() unit} quaternion using spherical linear interpolation using the specified interpolation factor <code>alpha</code>.
     * <p>
     * This method resorts to non-spherical linear interpolation when the absolute dot product between <code>this</code> and <code>target</code> is
     * below <code>1E-6</code>.
     * 
     * @param target
     *          the target of the interpolation, which should be reached with <code>alpha = 1.0</code>
     * @param alpha
     *          the interpolation factor, within <code>[0..1]</code>
     * @return this
     */
    public Quaterniond slerp(Quaterniondc target, double alpha) {
        return slerp(target, alpha, this);
    }

    public Quaterniond slerp(Quaterniondc target, double alpha, Quaterniond dest) {
        return slerp(x, y, z, w, target.x(), target.y(), target.z(), target.w(), alpha, dest);
    }

    /** {@link #slerp(Quaterniondc, double, Quaterniond)} on components, so callers can blend without temporaries. */
    private static Quaterniond slerp(double ax, double ay, double az, double aw, double bx, double by, double bz, double bw, double alpha, Quaterniond dest) {
        // the angle between a and the hemisphere-aligned b from 2 atan2(|a − b|, |a + b|): accurate for every angle, so
        // no near-identity lerp fallback is needed (before 2.4.0 that fallback left results off unit length by up to
        // 2.5e-7 and the acos-style angle lost precision)
        double sign = (double) ax * bx + (double) ay * by + (double) az * bz + (double) aw * bw >= 0.0 ? 1.0 : -1.0;
        double dx = ax - sign * bx, dy = ay - sign * by, dz = az - sign * bz, dw = aw - sign * bw;
        double px = ax + sign * bx, py = ay + sign * by, pz = az + sign * bz, pw = aw + sign * bw;
        double om = 2.0 * Math.atan2(java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz + dw * dw),
                java.lang.Math.sqrt(px * px + py * py + pz * pz + pw * pw));
        double scale0, scale1;
        if (om == 0.0) {
            scale0 = 1.0 - alpha;
            scale1 = alpha;
        } else {
            double so = Math.sin(om);
            scale0 = Math.sin((1.0 - alpha) * om) / so;
            scale1 = Math.sin(alpha * om) / so;
        }
        scale1 *= sign;
        dest.x = (scale0 * ax + scale1 * bx);
        dest.y = (scale0 * ay + scale1 * by);
        dest.z = (scale0 * az + scale1 * bz);
        dest.w = (scale0 * aw + scale1 * bw);
        return dest;
    }

    /**
     * Compute the logarithm of this unit quaternion and store the result in <code>this</code>.
     *
     * @return this
     */
    public Quaterniond log() {
        return log(this);
    }

    /**
     * Compute the quaternion logarithm of this unit quaternion.
     * <p>
     * In {@link MathMode#STRICT}, non-unit inputs throw {@link IllegalArgumentException}.
     * In {@link MathMode#FAST}, caller is responsible for unit inputs.
     *
     * @param dest receives the pure-quaternion tangent-space result ({@code w=0})
     * @return dest
     * @since 1.0.0
     */
    public Quaterniond log(Quaterniond dest) {
        if (CoreConfig.mathMode() == MathMode.STRICT && !CoreConfig.withinStrictUnitTolerance(lengthSquared())) {
            throw new IllegalArgumentException("Quaternion logarithm requires unit input");
        }
        double vLen = Math.sqrt(Math.fma(x, x, Math.fma(y, y, z * z)));
        if (vLen == 0.0) {
            dest.x = 0.0;
            dest.y = 0.0;
            dest.z = 0.0;
            dest.w = 0.0;
            return dest;
        }
        // atan2 is well conditioned for every rotation; acos(w) loses all precision as w -> 1, and in float it
        // returned 0 for rotations below ~5e-4 rad (review finding H-7).
        double angle = AccurateMath.atan2(vLen, w);
        double scale = angle / vLen;
        dest.x = x * scale;
        dest.y = y * scale;
        dest.z = z * scale;
        dest.w = 0.0;
        return dest;
    }

    /**
     * Compute the exponential of this pure quaternion and store the result in <code>this</code>.
     *
     * @return this
     */
    public Quaterniond exp() {
        return exp(this);
    }

    /**
     * Compute the quaternion exponential of this pure quaternion.
     * <p>
     * In {@link MathMode#STRICT}, non-pure inputs throw {@link IllegalArgumentException}.
     *
     * @param dest receives the unit-quaternion result
     * @return dest
     * @since 1.0.0
     */
    public Quaterniond exp(Quaterniond dest) {
        if (CoreConfig.mathMode() == MathMode.STRICT && !(Math.abs(w) <= CoreConfig.STRICT_UNIT_TOLERANCE_D)) {
            throw new IllegalArgumentException("Quaternion exponential requires pure input");
        }
        double vLen = Math.sqrt(Math.fma(x, x, Math.fma(y, y, z * z)));
        if (vLen < ZERO_TOLERANCE) {
            // sin(v)/v and cos(v) are 1 to working precision; keep the vector part so exp(log(q)) round-trips
            dest.x = x;
            dest.y = y;
            dest.z = z;
            dest.w = 1.0;
            return dest;
        }
        double sin = Math.sin(vLen);
        double scale = sin / vLen;
        dest.x = x * scale;
        dest.y = y * scale;
        dest.z = z * scale;
        dest.w = Math.cosFromSin(sin, vLen);
        return dest;
    }

    /**
     * Compute the shortest-arc angular difference from {@code from} to {@code to}, returned as axis-angle in a vector.
     * <p>
     * The resulting vector direction is the rotation axis and its length is the signed rotation angle in radians.
     * Double-precision counterpart of {@link Quaternionf#angularError(Quaternionfc, Quaternionfc, Vector3f)}.
     *
     * @param from source orientation
     * @param to target orientation
     * @param dest will hold the axis-angle vector
     * @return dest
     */
    public static Vector3d angularError(Quaterniondc from, Quaterniondc to, Vector3d dest) {
        double fw = from.w(), fx = from.x(), fy = from.y(), fz = from.z();
        double tw = to.w(), tx = to.x(), ty = to.y(), tz = to.z();
        // delta = to * conjugate(from)
        double dx = -tw * fx + tx * fw - ty * fz + tz * fy;
        double dy = -tw * fy + tx * fz + ty * fw - tz * fx;
        double dz = -tw * fz - tx * fy + ty * fx + tz * fw;
        double dw = tw * fw + tx * fx + ty * fy + tz * fz;
        double sinHalfAngle = Math.sqrt(Math.fma(dx, dx, Math.fma(dy, dy, dz * dz)));
        if (sinHalfAngle == 0.0) {
            return dest.set(0.0, 0.0, 0.0);
        }
        double angle = 2.0 * AccurateMath.atan2(sinHalfAngle, Math.abs(dw));
        if (dw < 0.0) {
            angle = -angle;
        }
        double scale = angle / sinHalfAngle;
        return dest.set(dx * scale, dy * scale, dz * scale);
    }

    /**
     * Compute the inner SQUAD control point for this key quaternion {@code q_i}
     * given neighbors {@code q_{i-1}} and {@code q_{i+1}}.
     * Neighbor hemisphere correction is applied internally.
     *
     * @param prev previous key quaternion
     * @param next next key quaternion
     * @param dest receives the computed control point
     * @return dest
     * @since 1.0.0
     */
    public Quaterniond squadControlPoint(Quaterniondc prev, Quaterniondc next, Quaterniond dest) {
        double prevX = prev.x(), prevY = prev.y(), prevZ = prev.z(), prevW = prev.w();
        if (Math.fma(x, prevX, Math.fma(y, prevY, Math.fma(z, prevZ, w * prevW))) < 0.0) {
            prevX = -prevX;
            prevY = -prevY;
            prevZ = -prevZ;
            prevW = -prevW;
        }
        double nextX = next.x(), nextY = next.y(), nextZ = next.z(), nextW = next.w();
        if (Math.fma(x, nextX, Math.fma(y, nextY, Math.fma(z, nextZ, w * nextW))) < 0.0) {
            nextX = -nextX;
            nextY = -nextY;
            nextZ = -nextZ;
            nextW = -nextW;
        }
        // Allocation-free (review M-14), using dest as the only scratch quaternion. The operation sequence is the
        // same as the previous version, which allocated eight quaternions, so results are unchanged. This, prev and
        // next are read into locals first, so dest may alias any of them.
        double tx = x, ty = y, tz = z, tw = w;
        dest.set(tx, ty, tz, tw).invert(dest);
        double ix = dest.x, iy = dest.y, iz = dest.z, iw = dest.w;
        dest.mul(prevX, prevY, prevZ, prevW, dest).log(dest);
        double lpx = dest.x, lpy = dest.y, lpz = dest.z;
        dest.set(ix, iy, iz, iw).mul(nextX, nextY, nextZ, nextW, dest).log(dest);
        dest.set((lpx + dest.x) * -0.25, (lpy + dest.y) * -0.25, (lpz + dest.z) * -0.25, 0.0).exp(dest);
        double ex = dest.x, ey = dest.y, ez = dest.z, ew = dest.w;
        return dest.set(tx, ty, tz, tw).mul(ex, ey, ez, ew, dest).normalize();
    }

    /**
     * Evaluate spherical quadrangle interpolation from this start quaternion to {@code q1}.
     *
     * @param q1 end quaternion
     * @param s0 control point at start
     * @param s1 control point at end
     * @param t interpolation parameter in [0,1]
     * @param dest receives the interpolated quaternion
     * @return dest
     * @since 1.0.0
     */
    public Quaterniond squad(Quaterniondc q1, Quaterniondc s0, Quaterniondc s1, double t, Quaterniond dest) {
        // Allocation-free (review M-14): every input is read first, so dest may alias any of them.
        double ax = x, ay = y, az = z, aw = w;
        double bx = q1.x(), by = q1.y(), bz = q1.z(), bw = q1.w();
        slerp(s0.x(), s0.y(), s0.z(), s0.w(), s1.x(), s1.y(), s1.z(), s1.w(), t, dest);
        double cx = dest.x, cy = dest.y, cz = dest.z, cw = dest.w;
        slerp(ax, ay, az, aw, bx, by, bz, bw, t, dest);
        double k = 2.0 * t * (1.0 - t);
        return slerp(dest.x, dest.y, dest.z, dest.w, cx, cy, cz, cw, k, dest);
    }

    public Quaterniond swingTwist(Vector3dc twistAxis, Quaterniond swing, Quaterniond twist) {
        if (CoreConfig.mathMode() == MathMode.STRICT && !CoreConfig.withinStrictUnitTolerance(lengthSquared())) {
            throw new IllegalArgumentException("Swing-twist requires unit quaternion input");
        }
        if (swing == twist) {
            throw new IllegalArgumentException("swing and twist must be different objects");
        }
        // Read this quaternion first: swing or twist may alias this (review finding M-3).
        double qx = x, qy = y, qz = z, qw = w;
        double ax = twistAxis.x(), ay = twistAxis.y(), az = twistAxis.z();
        double axisLenSq = Math.fma(ax, ax, Math.fma(ay, ay, az * az));
        if (CoreConfig.mathMode() == MathMode.STRICT && !CoreConfig.withinStrictUnitTolerance(axisLenSq)) {
            throw new IllegalArgumentException("Swing-twist requires unit twist axis");
        }
        if (axisLenSq < ZERO_TOLERANCE) {
            if (CoreConfig.mathMode() == MathMode.STRICT) {
                throw new IllegalArgumentException("Swing-twist requires non-zero twist axis");
            }
            swing.set(qx, qy, qz, qw);
            twist.identity();
            return swing;
        }
        double invAxisLen = Math.invsqrt(axisLenSq);
        ax *= invAxisLen;
        ay *= invAxisLen;
        az *= invAxisLen;

        double proj = Math.fma(qx, ax, Math.fma(qy, ay, qz * az));
        twist.x = ax * proj;
        twist.y = ay * proj;
        twist.z = az * proj;
        twist.w = qw;

        double twistLenSq = Math.fma(twist.x, twist.x, Math.fma(twist.y, twist.y, Math.fma(twist.z, twist.z, twist.w * twist.w)));
        if (twistLenSq < ZERO_TOLERANCE) {
            twist.identity();
            swing.set(qx, qy, qz, qw);
            return swing;
        }
        double invTwistLen = Math.invsqrt(twistLenSq);
        twist.x *= invTwistLen;
        twist.y *= invTwistLen;
        twist.z *= invTwistLen;
        twist.w *= invTwistLen;

        double tx = -twist.x;
        double ty = -twist.y;
        double tz = -twist.z;
        double tw = twist.w;
        swing.set(
                Math.fma(qw, tx, Math.fma(qx, tw, Math.fma(qy, tz, -qz * ty))),
                Math.fma(qw, ty, Math.fma(-qx, tz, Math.fma(qy, tw, qz * tx))),
                Math.fma(qw, tz, Math.fma(qx, ty, Math.fma(-qy, tx, qz * tw))),
                Math.fma(qw, tw, Math.fma(-qx, tx, Math.fma(-qy, ty, -qz * tz))));
        return swing;
    }

    public Vector3d angularVelocity(Quaterniondc next, double dt, Vector3d dest) {
        if (dt == 0.0) {
            throw new IllegalArgumentException("dt");
        }
        if (CoreConfig.mathMode() == MathMode.STRICT
                && (!CoreConfig.withinStrictUnitTolerance(lengthSquared())
                || !CoreConfig.withinStrictUnitTolerance(next.lengthSquared()))) {
            throw new IllegalArgumentException("Angular velocity requires unit quaternions");
        }
        double nx = next.x(), ny = next.y(), nz = next.z(), nw = next.w();
        double dot = Math.fma(x, nx, Math.fma(y, ny, Math.fma(z, nz, w * nw)));
        if (dot < 0.0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
            nw = -nw;
        }
        double cx = -x, cy = -y, cz = -z, cw = w;
        double dqX = Math.fma(nw, cx, Math.fma(nx, cw, Math.fma(ny, cz, -nz * cy)));
        double dqY = Math.fma(nw, cy, Math.fma(-nx, cz, Math.fma(ny, cw, nz * cx)));
        double dqZ = Math.fma(nw, cz, Math.fma(nx, cy, Math.fma(-ny, cx, nz * cw)));
        double dqW = Math.fma(nw, cw, Math.fma(-nx, cx, Math.fma(-ny, cy, -nz * cz)));
        if (dqW < 0.0) {
            dqX = -dqX;
            dqY = -dqY;
            dqZ = -dqZ;
            dqW = -dqW;
        }
        // angle = 2 * atan2(|dq.xyz|, dq.w): exact for small steps, where dq.w rounds to 1 and the previous
        // acos/sqrt(1 - w^2) form reported zero angular velocity (review finding H-7).
        double sinHalf = Math.sqrt(Math.fma(dqX, dqX, Math.fma(dqY, dqY, dqZ * dqZ)));
        if (sinHalf == 0.0) {
            return dest.set(0.0, 0.0, 0.0);
        }
        double angle = 2.0 * AccurateMath.atan2(sinHalf, dqW);
        double scale = angle / (sinHalf * dt);
        return dest.set(dqX * scale, dqY * scale, dqZ * scale);
    }

    /**
     * The rotation produced by turning at angular velocity <code>omega</code> (radians per second, axis scaled by
     * rate) for <code>dt</code> seconds: the inverse of {@link #angularVelocity}. Small rates are kept exactly;
     * only a zero rate or zero <code>dt</code> gives the identity.
     *
     * @param omega angular velocity in radians/second
     * @param dt time step in seconds
     * @param dest will hold the rotation
     * @return dest
     */
    public static Quaterniond integrateAngularVelocity(Vector3dc omega, double dt, Quaterniond dest) {
        double ox = omega.x(), oy = omega.y(), oz = omega.z();
        double omegaLenSq = Math.fma(ox, ox, Math.fma(oy, oy, oz * oz));
        if (omegaLenSq == 0.0 || dt == 0.0) { // was < ZERO_TOLERANCE, which dropped rates below 1e-6 rad/s
            return dest.identity();
        }
        double omegaLen = Math.sqrt(omegaLenSq);
        double angle = omegaLen * dt;
        double halfAngle = 0.5 * angle;
        double sinHalf = Math.sin(halfAngle);
        double cosHalf = Math.cosFromSin(sinHalf, halfAngle);
        double invOmegaLen = 1.0 / omegaLen;
        return dest.set(ox * invOmegaLen * sinHalf,
                oy * invOmegaLen * sinHalf,
                oz * invOmegaLen * sinHalf,
                cosHalf);
    }

    public static void ensureConsistentHemisphere(Quaterniond[] sequence) {
        ensureConsistentHemisphere(sequence, 0, sequence.length);
    }

    public static void ensureConsistentHemisphere(Quaterniond[] sequence, int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > sequence.length) {
            throw new IllegalArgumentException("offset/length");
        }
        int end = offset + length;
        if (CoreConfig.mathMode() == MathMode.STRICT) {
            for (int i = offset; i < end; i++) {
                if (!CoreConfig.withinStrictUnitTolerance(sequence[i].lengthSquared())) {
                    throw new IllegalArgumentException("Sequence contains non-unit quaternion");
                }
            }
        }
        for (int i = offset + 1; i < end; i++) {
            Quaterniond prev = sequence[i - 1];
            Quaterniond curr = sequence[i];
            double dot = Math.fma(prev.x, curr.x, Math.fma(prev.y, curr.y, Math.fma(prev.z, curr.z, prev.w * curr.w)));
            if (dot < 0.0) {
                curr.x = -curr.x;
                curr.y = -curr.y;
                curr.z = -curr.z;
                curr.w = -curr.w;
            }
        }
    }

    /**
     * Interpolate between all of the quaternions given in <code>qs</code> via spherical linear interpolation using the specified interpolation factors <code>weights</code>,
     * and store the result in <code>dest</code>.
     * <p>
     * This method will interpolate between each two successive quaternions via {@link #slerp(Quaterniondc, double)} using their relative interpolation weights.
     * <p>
     * This method resorts to non-spherical linear interpolation when the absolute dot product of any two interpolated quaternions is below <code>1E-6f</code>.
     * <p>
     * Reference: <a href="http://gamedev.stackexchange.com/questions/62354/method-for-interpolation-between-3-quaternions#answer-62356">http://gamedev.stackexchange.com/</a>
     * 
     * @param qs
     *          the quaternions to interpolate over
     * @param weights
     *          the weights of each individual quaternion in <code>qs</code>
     * @param dest
     *          will hold the result
     * @return dest
     */
    public static Quaterniondc slerp(Quaterniond[] qs, double[] weights, Quaterniond dest) {
        dest.set(qs[0]);
        double w = weights[0];
        for (int i = 1; i < qs.length; i++) {
            double w0 = w;
            double w1 = weights[i];
            double rw1 = w1 / (w0 + w1);
            w += w1;
            dest.slerp(qs[i], rw1);
        }
        return dest;
    }

    /**
     * Apply scaling to this quaternion, which results in any vector transformed by this quaternion to change
     * its length by the given <code>factor</code>.
     * 
     * @param factor
     *          the scaling factor
     * @return this
     */
    public Quaterniond scale(double factor) {
        return scale(factor, this);
    }

    public Quaterniond scale(double factor, Quaterniond dest) {
        double sqrt = Math.sqrt(factor);
        dest.x = sqrt * x;
        dest.y = sqrt * y;
        dest.z = sqrt * z;
        dest.w = sqrt * w;
        return dest;
    }

    /**
     * Set this quaternion to represent scaling, which results in a transformed vector to change
     * its length by the given <code>factor</code>.
     * 
     * @param factor
     *          the scaling factor
     * @return this
     */
    public Quaterniond scaling(double factor) {
        double sqrt = Math.sqrt(factor);
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.w = sqrt;
        return this;
    }

    /**
     * Integrate the rotation given by the angular velocity <code>(vx, vy, vz)</code> around the x, y and z axis, respectively,
     * with respect to the given elapsed time delta <code>dt</code> and add the differentiate rotation to the rotation represented by this quaternion.
     * <p>
     * This method pre-multiplies the rotation given by <code>dt</code> and <code>(vx, vy, vz)</code> by <code>this</code>, so
     * the angular velocities are always relative to the local coordinate system of the rotation represented by <code>this</code> quaternion.
     * <p>
     * This method is equivalent to calling: <code>rotateLocal(dt * vx, dt * vy, dt * vz)</code>
     * <p>
     * Reference: <a href="http://physicsforgames.blogspot.de/2010/02/quaternions.html">http://physicsforgames.blogspot.de/</a>
     * 
     * @param dt
     *          the delta time
     * @param vx
     *          the angular velocity around the x axis
     * @param vy
     *          the angular velocity around the y axis
     * @param vz
     *          the angular velocity around the z axis
     * @return this
     */
    public Quaterniond integrate(double dt, double vx, double vy, double vz) {
        return integrate(dt, vx, vy, vz, this);
    }

    public Quaterniond integrate(double dt, double vx, double vy, double vz, Quaterniond dest) {
        double thetaX = dt * vx * 0.5;
        double thetaY = dt * vy * 0.5;
        double thetaZ = dt * vz * 0.5;
        double thetaMagSq = thetaX * thetaX + thetaY * thetaY + thetaZ * thetaZ;
        double s;
        double dqX, dqY, dqZ, dqW;
        if (thetaMagSq * thetaMagSq / 24.0 < 1E-8) {
            dqW = 1.0 - thetaMagSq * 0.5 * (1.0 - thetaMagSq / 12.0 * (1.0 - thetaMagSq / 30.0));
            s = 1.0 - thetaMagSq / 6.0 * (1.0 - thetaMagSq / 20.0 * (1.0 - thetaMagSq / 42.0));
        } else {
            double thetaMag = Math.sqrt(thetaMagSq);
            double sin = Math.sin(thetaMag);
            s = sin / thetaMag;
            dqW = Math.cosFromSin(sin, thetaMag);
        }
        dqX = thetaX * s;
        dqY = thetaY * s;
        dqZ = thetaZ * s;
        /* Pre-multiplication */
        return dest.set(Math.fma(dqW, x, Math.fma(dqX, w, Math.fma(dqY, z, -dqZ * y))),
                        Math.fma(dqW, y, Math.fma(-dqX, z, Math.fma(dqY, w, dqZ * x))),
                        Math.fma(dqW, z, Math.fma(dqX, y, Math.fma(-dqY, x, dqZ * w))),
                        Math.fma(dqW, w, Math.fma(-dqX, x, Math.fma(-dqY, y, -dqZ * z))));
    }

    /**
     * Compute a linear (non-spherical) interpolation of <code>this</code> and the given quaternion <code>q</code>
     * and store the result in <code>this</code>.
     * 
     * @param q
     *          the other quaternion
     * @param factor
     *          the interpolation factor. It is between 0.0 and 1.0
     * @return this
     */
    public Quaterniond nlerp(Quaterniondc q, double factor) {
        return nlerp(q, factor, this);
    }

    public Quaterniond nlerp(Quaterniondc q, double factor, Quaterniond dest) {
        double cosom = Math.fma(x, q.x(), Math.fma(y, q.y(), Math.fma(z, q.z(), w * q.w())));
        double scale0 = 1.0 - factor;
        double scale1 = (cosom >= 0.0) ? factor : -factor;
        dest.x = Math.fma(scale0, x, scale1 * q.x());
        dest.y = Math.fma(scale0, y, scale1 * q.y());
        dest.z = Math.fma(scale0, z, scale1 * q.z());
        dest.w = Math.fma(scale0, w, scale1 * q.w());
        double s = Math.invsqrt(Math.fma(dest.x, dest.x, Math.fma(dest.y, dest.y, Math.fma(dest.z, dest.z, dest.w * dest.w))));
        dest.x *= s;
        dest.y *= s;
        dest.z *= s;
        dest.w *= s;
        return dest;
    }

    /**
     * Interpolate between all of the quaternions given in <code>qs</code> via non-spherical linear interpolation using the
     * specified interpolation factors <code>weights</code>, and store the result in <code>dest</code>.
     * <p>
     * This method will interpolate between each two successive quaternions via {@link #nlerp(Quaterniondc, double)}
     * using their relative interpolation weights.
     * <p>
     * Reference: <a href="http://gamedev.stackexchange.com/questions/62354/method-for-interpolation-between-3-quaternions#answer-62356">http://gamedev.stackexchange.com/</a>
     * 
     * @param qs
     *          the quaternions to interpolate over
     * @param weights
     *          the weights of each individual quaternion in <code>qs</code>
     * @param dest
     *          will hold the result
     * @return dest
     */
    public static Quaterniondc nlerp(Quaterniond[] qs, double[] weights, Quaterniond dest) {
        dest.set(qs[0]);
        double w = weights[0];
        for (int i = 1; i < qs.length; i++) {
            double w0 = w;
            double w1 = weights[i];
            double rw1 = w1 / (w0 + w1);
            w += w1;
            dest.nlerp(qs[i], rw1);
        }
        return dest;
    }

    public Quaterniond nlerpIterative(Quaterniondc q, double alpha, double dotThreshold, Quaterniond dest) {
        double q1x = x, q1y = y, q1z = z, q1w = w;
        double q2x = q.x(), q2y = q.y(), q2z = q.z(), q2w = q.w();
        double dot = Math.fma(q1x, q2x, Math.fma(q1y, q2y, Math.fma(q1z, q2z, q1w * q2w)));
        double absDot = Math.abs(dot);
        double alphaN = alpha;
        // Each iteration halves the arc, so 32 take it below float/double resolution. Without the cap a threshold
        // >= 1 (|dot| never exceeds 1), or one that rounding cannot reach, looped forever.
        for (int iteration = 0; absDot < dotThreshold && iteration < 32; iteration++) {
            double scale0 = 0.5;
            double scale1 = dot >= 0.0 ? 0.5 : -0.5;
            if (alphaN < 0.5) {
                q2x = Math.fma(scale0, q2x, scale1 * q1x);
                q2y = Math.fma(scale0, q2y, scale1 * q1y);
                q2z = Math.fma(scale0, q2z, scale1 * q1z);
                q2w = Math.fma(scale0, q2w, scale1 * q1w);
                double s = Math.invsqrt(Math.fma(q2x, q2x, Math.fma(q2y, q2y, Math.fma(q2z, q2z, q2w * q2w))));
                q2x *= s;
                q2y *= s;
                q2z *= s;
                q2w *= s;
                alphaN = alphaN + alphaN;
            } else {
                q1x = Math.fma(scale0, q1x, scale1 * q2x);
                q1y = Math.fma(scale0, q1y, scale1 * q2y);
                q1z = Math.fma(scale0, q1z, scale1 * q2z);
                q1w = Math.fma(scale0, q1w, scale1 * q2w);
                double s = Math.invsqrt(Math.fma(q1x, q1x, Math.fma(q1y, q1y, Math.fma(q1z, q1z, q1w * q1w))));
                q1x *= s;
                q1y *= s;
                q1z *= s;
                q1w *= s;
                alphaN = alphaN + alphaN - 1.0;
            }
            dot = Math.fma(q1x, q2x, Math.fma(q1y, q2y, Math.fma(q1z, q2z, q1w * q2w)));
            absDot = Math.abs(dot);
        }
        double scale0 = 1.0 - alphaN;
        double scale1 = dot >= 0.0 ? alphaN : -alphaN;
        double resX = Math.fma(scale0, q1x, scale1 * q2x);
        double resY = Math.fma(scale0, q1y, scale1 * q2y);
        double resZ = Math.fma(scale0, q1z, scale1 * q2z);
        double resW = Math.fma(scale0, q1w, scale1 * q2w);
        double s = Math.invsqrt(Math.fma(resX, resX, Math.fma(resY, resY, Math.fma(resZ, resZ, resW * resW))));
        dest.x = resX * s;
        dest.y = resY * s;
        dest.z = resZ * s;
        dest.w = resW * s;
        return dest;
    }

    /**
     * Compute linear (non-spherical) interpolations of <code>this</code> and the given quaternion <code>q</code>
     * iteratively and store the result in <code>this</code>.
     * <p>
     * This method performs a series of small-step nlerp interpolations to avoid doing a costly spherical linear interpolation, like
     * {@link #slerp(Quaterniondc, double, Quaterniond) slerp},
     * by subdividing the rotation arc between <code>this</code> and <code>q</code> via non-spherical linear interpolations as long as
     * the absolute dot product of <code>this</code> and <code>q</code> is greater than the given <code>dotThreshold</code> parameter.
     * <p>
     * Thanks to <code>@theagentd</code> at <a href="http://www.java-gaming.org/">http://www.java-gaming.org/</a> for providing the code.
     * 
     * @param q
     *          the other quaternion
     * @param alpha
     *          the interpolation factor, between 0.0 and 1.0
     * @param dotThreshold
     *          the threshold for the dot product of <code>this</code> and <code>q</code> above which this method performs another iteration
     *          of a small-step linear interpolation
     * @return this
     */
    public Quaterniond nlerpIterative(Quaterniondc q, double alpha, double dotThreshold) {
        return nlerpIterative(q, alpha, dotThreshold, this);
    }

    /**
     * Interpolate between all of the quaternions given in <code>qs</code> via iterative non-spherical linear interpolation using the
     * specified interpolation factors <code>weights</code>, and store the result in <code>dest</code>.
     * <p>
     * This method will interpolate between each two successive quaternions via {@link #nlerpIterative(Quaterniondc, double, double)}
     * using their relative interpolation weights.
     * <p>
     * Reference: <a href="http://gamedev.stackexchange.com/questions/62354/method-for-interpolation-between-3-quaternions#answer-62356">http://gamedev.stackexchange.com/</a>
     * 
     * @param qs
     *          the quaternions to interpolate over
     * @param weights
     *          the weights of each individual quaternion in <code>qs</code>
     * @param dotThreshold
     *          the threshold for the dot product of each two interpolated quaternions above which {@link #nlerpIterative(Quaterniondc, double, double)} performs another iteration
     *          of a small-step linear interpolation
     * @param dest
     *          will hold the result
     * @return dest
     */
    public static Quaterniond nlerpIterative(Quaterniondc[] qs, double[] weights, double dotThreshold, Quaterniond dest) {
        dest.set(qs[0]);
        double w = weights[0];
        for (int i = 1; i < qs.length; i++) {
            double w0 = w;
            double w1 = weights[i];
            double rw1 = w1 / (w0 + w1);
            w += w1;
            dest.nlerpIterative(qs[i], rw1, dotThreshold);
        }
        return dest;
    }

    /**
     * Apply a rotation to this quaternion that maps the given direction to the positive Z axis.
     * <p>
     * Because there are multiple possibilities for such a rotation, this method will choose the one that ensures the given up direction to remain
     * parallel to the plane spanned by the <code>up</code> and <code>dir</code> vectors. 
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>Q * R</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>Q * R * v</code>, the
     * rotation added by this method will be applied first!
     * <p>
     * Reference: <a href="http://answers.unity3d.com/questions/467614/what-is-the-source-code-of-quaternionlookrotation.html">http://answers.unity3d.com</a>
     * 
     * @see #lookAlong(double, double, double, double, double, double, Quaterniond)
     * 
     * @param dir
     *              the direction to map to the positive Z axis
     * @param up
     *              the vector which will be mapped to a vector parallel to the plane
     *              spanned by the given <code>dir</code> and <code>up</code>
     * @return this
     */
    public Quaterniond lookAlong(Vector3dc dir, Vector3dc up) {
        return lookAlong(dir.x(), dir.y(), dir.z(), up.x(), up.y(), up.z(), this);
    }

    public Quaterniond lookAlong(Vector3dc dir, Vector3dc up, Quaterniond dest) {
        return lookAlong(dir.x(), dir.y(), dir.z(), up.x(), up.y(), up.z(), dest);
    }

    /**
     * Apply a rotation to this quaternion that maps the given direction to the positive Z axis.
     * <p>
     * Because there are multiple possibilities for such a rotation, this method will choose the one that ensures the given up direction to remain
     * parallel to the plane spanned by the <code>up</code> and <code>dir</code> vectors. 
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>Q * R</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>Q * R * v</code>, the
     * rotation added by this method will be applied first!
     * <p>
     * Reference: <a href="http://answers.unity3d.com/questions/467614/what-is-the-source-code-of-quaternionlookrotation.html">http://answers.unity3d.com</a>
     * 
     * @see #lookAlong(double, double, double, double, double, double, Quaterniond)
     * 
     * @param dirX
     *              the x-coordinate of the direction to look along
     * @param dirY
     *              the y-coordinate of the direction to look along
     * @param dirZ
     *              the z-coordinate of the direction to look along
     * @param upX
     *              the x-coordinate of the up vector
     * @param upY
     *              the y-coordinate of the up vector
     * @param upZ
     *              the z-coordinate of the up vector
     * @return this
     */
    public Quaterniond lookAlong(double dirX, double dirY, double dirZ, double upX, double upY, double upZ) {
        return lookAlong(dirX, dirY, dirZ, upX, upY, upZ, this);
    }

    public Quaterniond lookAlong(double dirX, double dirY, double dirZ, double upX, double upY, double upZ, Quaterniond dest) {
        // Normalize direction
        double invDirLength = Math.invsqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        double dirnX = -dirX * invDirLength;
        double dirnY = -dirY * invDirLength;
        double dirnZ = -dirZ * invDirLength;
        // left = up x dir
        double leftX, leftY, leftZ;
        leftX = upY * dirnZ - upZ * dirnY;
        leftY = upZ * dirnX - upX * dirnZ;
        leftZ = upX * dirnY - upY * dirnX;
        // normalize left
        double invLeftLength = Math.invsqrt(leftX * leftX + leftY * leftY + leftZ * leftZ);
        leftX *= invLeftLength;
        leftY *= invLeftLength;
        leftZ *= invLeftLength;
        // up x dir cancels as up approaches dir: remove the residual component along dir, then renormalize
        double leftDot = leftX * dirnX + leftY * dirnY + leftZ * dirnZ;
        leftX -= leftDot * dirnX;
        leftY -= leftDot * dirnY;
        leftZ -= leftDot * dirnZ;
        double invLeftLength2 = 1.0 / Norms.norm3(leftX, leftY, leftZ);
        leftX *= invLeftLength2;
        leftY *= invLeftLength2;
        leftZ *= invLeftLength2;
        // up = direction x left
        double upnX = dirnY * leftZ - dirnZ * leftY;
        double upnY = dirnZ * leftX - dirnX * leftZ;
        double upnZ = dirnX * leftY - dirnY * leftX;
        // invert
        double a = Math.fma(-leftX, upnY, leftY * upnX);
        double b = Math.fma(-leftZ, upnX, leftX * upnZ);
        double c = Math.fma(-leftY, upnZ, leftZ * upnY);
        double d = Math.fma(a, -dirnZ, Math.fma(b, -dirnY, c * -dirnX));
        double s = 1.0 / d;
        double nm00 = Math.fma(upnY, -dirnZ, dirnY * upnZ) * s;
        double nm01 = Math.fma(-dirnY, -leftZ, leftY * -dirnZ) * s;
        double nm02 = c * s;
        double nm10 = Math.fma(-dirnX, upnZ, -upnX * -dirnZ) * s;
        double nm11 = Math.fma(-leftX, -dirnZ, dirnX * -leftZ) * s;
        double nm12 = b * s;
        double nm20 = Math.fma(upnX, -dirnY, dirnX * upnY) * s;
        double nm21 = Math.fma(-dirnX, -leftY, leftX * -dirnY) * s;
        double nm22 = a * s;
        // Compute quaternion from matrix
        double t, x, y, z, w;
        double tr = nm00 + nm11 + nm22;
        if (tr >= 0.0) {
            t = Math.sqrt(tr + 1.0);
            w = t * 0.5;
            t = 0.5 / t;
            x = (nm12 - nm21) * t;
            y = (nm20 - nm02) * t;
            z = (nm01 - nm10) * t;
        } else {
            if (nm00 >= nm11 && nm00 >= nm22) {
                t = Math.sqrt(nm00 - (nm11 + nm22) + 1.0);
                x = t * 0.5;
                t = 0.5 / t;
                y = (nm10 + nm01) * t;
                z = (nm02 + nm20) * t;
                w = (nm12 - nm21) * t;
            } else if (nm11 > nm22) {
                t = Math.sqrt(nm11 - (nm22 + nm00) + 1.0);
                y = t * 0.5;
                t = 0.5 / t;
                z = (nm21 + nm12) * t;
                x = (nm10 + nm01) * t;
                w = (nm20 - nm02) * t;
            } else {
                t = Math.sqrt(nm22 - (nm00 + nm11) + 1.0);
                z = t * 0.5;
                t = 0.5 / t;
                x = (nm02 + nm20) * t;
                y = (nm21 + nm12) * t;
                w = (nm01 - nm10) * t;
            }
        }
        /* Multiply */
        return dest.set(Math.fma(this.w, x, Math.fma(this.x, w, Math.fma(this.y, z, -this.z * y))),
                Math.fma(this.w, y, Math.fma(-this.x, z, Math.fma(this.y, w, this.z * x))),
                Math.fma(this.w, z, Math.fma(this.x, y, Math.fma(-this.y, x, this.z * w))),
                Math.fma(this.w, w, Math.fma(-this.x, x, Math.fma(-this.y, y, -this.z * z))));
    }

    /**
     * Return a string representation of this quaternion.
     * <p>
     * This method creates a new {@link DecimalFormat} on every invocation with the format string "<code>0.000E0;-</code>".
     * 
     * @return the string representation
     */
    public String toString() {
        return Runtime.formatNumbers(toString(Options.NUMBER_FORMAT));
    }

    /**
     * Return a string representation of this quaternion by formatting the components with the given {@link NumberFormat}.
     * 
     * @param formatter
     *          the {@link NumberFormat} used to format the quaternion components with
     * @return the string representation
     */
    public String toString(NumberFormat formatter) {
        return "(" + Runtime.format(x, formatter) + " " + Runtime.format(y, formatter) + " " + Runtime.format(z, formatter) + " " + Runtime.format(w, formatter) + ")";
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
        out.writeDouble(w);
    }

    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        x = in.readDouble();
        y = in.readDouble();
        z = in.readDouble();
        w = in.readDouble();
    }

    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(w);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(x);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(y);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(z);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Quaterniond other = (Quaterniond) obj;
        if (Double.doubleToLongBits(w) != Double.doubleToLongBits(other.w))
            return false;
        if (Double.doubleToLongBits(x) != Double.doubleToLongBits(other.x))
            return false;
        if (Double.doubleToLongBits(y) != Double.doubleToLongBits(other.y))
            return false;
        if (Double.doubleToLongBits(z) != Double.doubleToLongBits(other.z))
            return false;
        return true;
    }

    /**
     * Compute the difference between <code>this</code> and the <code>other</code> quaternion
     * and store the result in <code>this</code>.
     * <p>
     * The difference is the rotation that has to be applied to get from
     * <code>this</code> rotation to <code>other</code>. If <code>T</code> is <code>this</code>, <code>Q</code>
     * is <code>other</code> and <code>D</code> is the computed difference, then the following equation holds:
     * <p>
     * <code>T * D = Q</code>
     * <p>
     * It is defined as: <code>D = T^-1 * Q</code>, where <code>T^-1</code> denotes the {@link #invert() inverse} of <code>T</code>.
     * 
     * @param other
     *          the other quaternion
     * @return this
     */
    public Quaterniond difference(Quaterniondc other) {
        return difference(other, this);
    }

    public Quaterniond difference(Quaterniondc other, Quaterniond dest) {
        double invNorm = 1.0 / lengthSquared();
        double x = -this.x * invNorm;
        double y = -this.y * invNorm;
        double z = -this.z * invNorm;
        double w = this.w * invNorm;
        dest.set(Math.fma(w, other.x(), Math.fma(x, other.w(), Math.fma(y, other.z(), -z * other.y()))),
                 Math.fma(w, other.y(), Math.fma(-x, other.z(), Math.fma(y, other.w(), z * other.x()))),
                 Math.fma(w, other.z(), Math.fma(x, other.y(), Math.fma(-y, other.x(), z * other.w()))),
                 Math.fma(w, other.w(), Math.fma(-x, other.x(), Math.fma(-y, other.y(), -z * other.z()))));
        return dest;
    }

    /**
     * Set <code>this</code> quaternion to a rotation that rotates the <code>fromDir</code> vector to point along <code>toDir</code>.
     * <p>
     * Since there can be multiple possible rotations, this method chooses the one with the shortest arc.
     * <p>
     * Reference: <a href="http://stackoverflow.com/questions/1171849/finding-quaternion-representing-the-rotation-from-one-vector-to-another#answer-1171995">stackoverflow.com</a>
     * 
     * @param fromDirX
     *              the x-coordinate of the direction to rotate into the destination direction
     * @param fromDirY
     *              the y-coordinate of the direction to rotate into the destination direction
     * @param fromDirZ
     *              the z-coordinate of the direction to rotate into the destination direction
     * @param toDirX
     *              the x-coordinate of the direction to rotate to
     * @param toDirY
     *              the y-coordinate of the direction to rotate to
     * @param toDirZ
     *              the z-coordinate of the direction to rotate to
     * @return this
     */
    public Quaterniond rotationTo(double fromDirX, double fromDirY, double fromDirZ, double toDirX, double toDirY, double toDirZ) {
        double fn = Math.invsqrt(Math.fma(fromDirX, fromDirX, Math.fma(fromDirY, fromDirY, fromDirZ * fromDirZ)));
        double tn = Math.invsqrt(Math.fma(toDirX, toDirX, Math.fma(toDirY, toDirY, toDirZ * toDirZ)));
        double fx = fromDirX * fn, fy = fromDirY * fn, fz = fromDirZ * fn;
        double tx = toDirX * tn, ty = toDirY * tn, tz = toDirZ * tn;
        double dot = fx * tx + fy * ty + fz * tz;
        double x, y, z, w;
        if (dot <= -0.9) {
            // near antiparallel the half-angle form below cancels: use axis and angle, with the axis re-orthogonalized
            // against from so the image of from stays exact while the (ill-determined) axis direction varies
            double ax = (double) fy * tz - (double) fz * ty, ay = (double) fz * tx - (double) fx * tz;
            double az = (double) fx * ty - (double) fy * tx;
            double s = java.lang.Math.sqrt(ax * ax + ay * ay + az * az);
            if (s == 0.0) { // exactly opposite: 180 degrees about any axis perpendicular to from
                if (java.lang.Math.abs(fx) < 0.9) {
                    ax = 0.0;
                    ay = fz;
                    az = -fy;
                } else {
                    ax = -fz;
                    ay = 0.0;
                    az = fx;
                }
            }
            double k = ax * fx + ay * fy + az * fz;
            ax -= k * fx;
            ay -= k * fy;
            az -= k * fz;
            double al = java.lang.Math.sqrt(ax * ax + ay * ay + az * az);
            double half = 0.5 * Math.atan2(s, (double) dot);
            double sh = Math.sin(half) / al;
            x = (ax * sh);
            y = (ay * sh);
            z = (az * sh);
            w = Math.cos(half);
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        } else {
            double sd2 = Math.sqrt((1.0 + dot) * 2.0);
            double isd2 = 1.0 / sd2;
            double cx = fy * tz - fz * ty;
            double cy = fz * tx - fx * tz;
            double cz = fx * ty - fy * tx;
            x = cx * isd2;
            y = cy * isd2;
            z = cz * isd2;
            w = sd2 * 0.5;
            double n2 = Math.invsqrt(Math.fma(x, x, Math.fma(y, y, Math.fma(z, z, w * w))));
            this.x = x * n2;
            this.y = y * n2;
            this.z = z * n2;
            this.w = w * n2;
        }
        return this;
    }

    /**
     * Set <code>this</code> quaternion to a rotation that rotates the <code>fromDir</code> vector to point along <code>toDir</code>.
     * <p>
     * Because there can be multiple possible rotations, this method chooses the one with the shortest arc.
     * 
     * @see #rotationTo(double, double, double, double, double, double)
     * 
     * @param fromDir
     *          the starting direction
     * @param toDir
     *          the destination direction
     * @return this
     */
    public Quaterniond rotationTo(Vector3dc fromDir, Vector3dc toDir) {
        return rotationTo(fromDir.x(), fromDir.y(), fromDir.z(), toDir.x(), toDir.y(), toDir.z());
    }

    public Quaterniond rotateTo(double fromDirX, double fromDirY, double fromDirZ,
                                double toDirX, double toDirY, double toDirZ, Quaterniond dest) {
        double fn = Math.invsqrt(Math.fma(fromDirX, fromDirX, Math.fma(fromDirY, fromDirY, fromDirZ * fromDirZ)));
        double tn = Math.invsqrt(Math.fma(toDirX, toDirX, Math.fma(toDirY, toDirY, toDirZ * toDirZ)));
        double fx = fromDirX * fn, fy = fromDirY * fn, fz = fromDirZ * fn;
        double tx = toDirX * tn, ty = toDirY * tn, tz = toDirZ * tn;
        double dot = fx * tx + fy * ty + fz * tz;
        double x, y, z, w;
        if (dot <= -0.9) {
            // near antiparallel the half-angle form below cancels: use axis and angle, with the axis re-orthogonalized
            // against from so the image of from stays exact while the (ill-determined) axis direction varies
            double ax = (double) fy * tz - (double) fz * ty, ay = (double) fz * tx - (double) fx * tz;
            double az = (double) fx * ty - (double) fy * tx;
            double s = java.lang.Math.sqrt(ax * ax + ay * ay + az * az);
            if (s == 0.0) { // exactly opposite: 180 degrees about any axis perpendicular to from
                if (java.lang.Math.abs(fx) < 0.9) {
                    ax = 0.0;
                    ay = fz;
                    az = -fy;
                } else {
                    ax = -fz;
                    ay = 0.0;
                    az = fx;
                }
            }
            double k = ax * fx + ay * fy + az * fz;
            ax -= k * fx;
            ay -= k * fy;
            az -= k * fz;
            double al = java.lang.Math.sqrt(ax * ax + ay * ay + az * az);
            double half = 0.5 * Math.atan2(s, (double) dot);
            double sh = Math.sin(half) / al;
            x = (ax * sh);
            y = (ay * sh);
            z = (az * sh);
            w = Math.cos(half);
        } else {
            double sd2 = Math.sqrt((1.0 + dot) * 2.0);
            double isd2 = 1.0 / sd2;
            double cx = fy * tz - fz * ty;
            double cy = fz * tx - fx * tz;
            double cz = fx * ty - fy * tx;
            x = cx * isd2;
            y = cy * isd2;
            z = cz * isd2;
            w = sd2 * 0.5;
            double n2 = Math.invsqrt(Math.fma(x, x, Math.fma(y, y, Math.fma(z, z, w * w))));
            x *= n2;
            y *= n2;
            z *= n2;
            w *= n2;
        }
        /* Multiply */
        return dest.set(Math.fma(this.w, x, Math.fma(this.x, w, Math.fma(this.y, z, -this.z * y))),
                        Math.fma(this.w, y, Math.fma(-this.x, z, Math.fma(this.y, w, this.z * x))),
                        Math.fma(this.w, z, Math.fma(this.x, y, Math.fma(-this.y, x, this.z * w))),
                        Math.fma(this.w, w, Math.fma(-this.x, x, Math.fma(-this.y, y, -this.z * z))));
    }

    /**
     * Set this {@link Quaterniond} to a rotation of the given angle in radians about the supplied
     * axis, all of which are specified via the {@link AxisAngle4f}.
     * 
     * @see #rotationAxis(double, double, double, double)
     * 
     * @param axisAngle
     *            the {@link AxisAngle4f} giving the rotation angle in radians and the axis to rotate about
     * @return this
     */
    public Quaterniond rotationAxis(AxisAngle4f axisAngle) {
        return rotationAxis(axisAngle.angle, axisAngle.x, axisAngle.y, axisAngle.z);
    }

    /**
     * Set this quaternion to a rotation of the given angle in radians about the supplied axis.
     * 
     * @param angle
     *          the rotation angle in radians
     * @param axisX
     *          the x-coordinate of the rotation axis
     * @param axisY
     *          the y-coordinate of the rotation axis
     * @param axisZ
     *          the z-coordinate of the rotation axis
     * @return this
     */
    public Quaterniond rotationAxis(double angle, double axisX, double axisY, double axisZ) {
        double hangle = angle / 2.0;
        double sinAngle = Math.sin(hangle);
        double invVLength = 1.0 / Norms.norm3(axisX, axisY, axisZ);
        return set(axisX * invVLength * sinAngle,
                   axisY * invVLength * sinAngle,
                   axisZ * invVLength * sinAngle,
                   Math.cosFromSin(sinAngle, hangle));
    }

    /**
     * Set this quaternion to represent a rotation of the given radians about the x axis.
     * 
     * @param angle
     *              the angle in radians to rotate about the x axis
     * @return this
     */
    public Quaterniond rotationX(double angle) {
        double sin = Math.sin(angle * 0.5);
        double cos = Math.cosFromSin(sin, angle * 0.5);
        return set(sin, 0, 0, cos); // was set(sin, 0, cos, 0): not a rotation about x
    }

    /**
     * Set this quaternion to represent a rotation of the given radians about the y axis.
     * 
     * @param angle
     *              the angle in radians to rotate about the y axis
     * @return this
     */
    public Quaterniond rotationY(double angle) {
        double sin = Math.sin(angle * 0.5);
        double cos = Math.cosFromSin(sin, angle * 0.5);
        return set(0, sin, 0, cos);
    }

    /**
     * Set this quaternion to represent a rotation of the given radians about the z axis.
     * 
     * @param angle
     *              the angle in radians to rotate about the z axis
     * @return this
     */
    public Quaterniond rotationZ(double angle) {
        double sin = Math.sin(angle * 0.5);
        double cos = Math.cosFromSin(sin, angle * 0.5);
        return set(0, 0, sin, cos);
    }

    /**
     * Apply a rotation to <code>this</code> that rotates the <code>fromDir</code> vector to point along <code>toDir</code>.
     * <p>
     * Since there can be multiple possible rotations, this method chooses the one with the shortest arc.
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>Q * R</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>Q * R * v</code>, the
     * rotation added by this method will be applied first!
     * 
     * @see #rotateTo(double, double, double, double, double, double, Quaterniond)
     * 
     * @param fromDirX
     *              the x-coordinate of the direction to rotate into the destination direction
     * @param fromDirY
     *              the y-coordinate of the direction to rotate into the destination direction
     * @param fromDirZ
     *              the z-coordinate of the direction to rotate into the destination direction
     * @param toDirX
     *              the x-coordinate of the direction to rotate to
     * @param toDirY
     *              the y-coordinate of the direction to rotate to
     * @param toDirZ
     *              the z-coordinate of the direction to rotate to
     * @return this
     */
    public Quaterniond rotateTo(double fromDirX, double fromDirY, double fromDirZ, double toDirX, double toDirY, double toDirZ) {
        return rotateTo(fromDirX, fromDirY, fromDirZ, toDirX, toDirY, toDirZ, this);
    }

    public Quaterniond rotateTo(Vector3dc fromDir, Vector3dc toDir, Quaterniond dest) {
        return rotateTo(fromDir.x(), fromDir.y(), fromDir.z(), toDir.x(), toDir.y(), toDir.z(), dest);
    }

    /**
     * Apply a rotation to <code>this</code> that rotates the <code>fromDir</code> vector to point along <code>toDir</code>.
     * <p>
     * Because there can be multiple possible rotations, this method chooses the one with the shortest arc.
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>Q * R</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>Q * R * v</code>, the
     * rotation added by this method will be applied first!
     * 
     * @see #rotateTo(double, double, double, double, double, double, Quaterniond)
     * 
     * @param fromDir
     *          the starting direction
     * @param toDir
     *          the destination direction
     * @return this
     */
    public Quaterniond rotateTo(Vector3dc fromDir, Vector3dc toDir) {
        return rotateTo(fromDir.x(), fromDir.y(), fromDir.z(), toDir.x(), toDir.y(), toDir.z(), this);
    }

    /**
     * Apply a rotation to <code>this</code> quaternion rotating the given radians about the x axis.
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>Q * R</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>Q * R * v</code>, the
     * rotation added by this method will be applied first!
     * 
     * @param angle
     *              the angle in radians to rotate about the x axis
     * @return this
     */
    public Quaterniond rotateX(double angle) {
        return rotateX(angle, this);
    }

    public Quaterniond rotateX(double angle, Quaterniond dest) {
        double sin = Math.sin(angle * 0.5);
        double cos = Math.cosFromSin(sin, angle * 0.5);
        return dest.set(w * sin + x * cos,
                        y * cos + z * sin,
                        z * cos - y * sin,
                        w * cos - x * sin);
    }

    /**
     * Apply a rotation to <code>this</code> quaternion rotating the given radians about the y axis.
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>Q * R</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>Q * R * v</code>, the
     * rotation added by this method will be applied first!
     * 
     * @param angle
     *              the angle in radians to rotate about the y axis
     * @return this
     */
    public Quaterniond rotateY(double angle) {
        return rotateY(angle, this);
    }

    public Quaterniond rotateY(double angle, Quaterniond dest) {
        double sin = Math.sin(angle * 0.5);
        double cos = Math.cosFromSin(sin, angle * 0.5);
        return dest.set(x * cos - z * sin,
                        w * sin + y * cos,
                        x * sin + z * cos,
                        w * cos - y * sin);
    }

    /**
     * Apply a rotation to <code>this</code> quaternion rotating the given radians about the z axis.
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>Q * R</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>Q * R * v</code>, the
     * rotation added by this method will be applied first!
     * 
     * @param angle
     *              the angle in radians to rotate about the z axis
     * @return this
     */
    public Quaterniond rotateZ(double angle) {
        return rotateZ(angle, this);
    }

    public Quaterniond rotateZ(double angle, Quaterniond dest) {
        double sin = Math.sin(angle * 0.5);
        double cos = Math.cosFromSin(sin, angle * 0.5);
        return dest.set(x * cos + y * sin,
                        y * cos - x * sin,
                        w * sin + z * cos,
                        w * cos - z * sin);
    }

    /**
     * Apply a rotation to <code>this</code> quaternion rotating the given radians about the local x axis.
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>R * Q</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>R * Q * v</code>, the
     * rotation represented by <code>this</code> will be applied first!
     * 
     * @param angle
     *              the angle in radians to rotate about the local x axis
     * @return this
     */
    public Quaterniond rotateLocalX(double angle) {
        return rotateLocalX(angle, this);
    }

    public Quaterniond rotateLocalX(double angle, Quaterniond dest) {
        double hangle = angle * 0.5;
        double s = Math.sin(hangle);
        double c = Math.cosFromSin(s, hangle);
        dest.set(c * x + s * w,
                 c * y - s * z,
                 c * z + s * y,
                 c * w - s * x);
        return dest;
    }

    /**
     * Apply a rotation to <code>this</code> quaternion rotating the given radians about the local y axis.
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>R * Q</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>R * Q * v</code>, the
     * rotation represented by <code>this</code> will be applied first!
     * 
     * @param angle
     *              the angle in radians to rotate about the local y axis
     * @return this
     */
    public Quaterniond rotateLocalY(double angle) {
        return rotateLocalY(angle, this);
    }

    public Quaterniond rotateLocalY(double angle, Quaterniond dest) {
        double hangle = angle * 0.5;
        double s = Math.sin(hangle);
        double c = Math.cosFromSin(s, hangle);
        dest.set(c * x + s * z,
                 c * y + s * w,
                 c * z - s * x,
                 c * w - s * y);
        return dest;
    }

    /**
     * Apply a rotation to <code>this</code> quaternion rotating the given radians about the local z axis.
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>R * Q</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>R * Q * v</code>, the
     * rotation represented by <code>this</code> will be applied first!
     * 
     * @param angle
     *              the angle in radians to rotate about the local z axis
     * @return this
     */
    public Quaterniond rotateLocalZ(double angle) {
        return rotateLocalZ(angle, this);
    }

    public Quaterniond rotateLocalZ(double angle, Quaterniond dest) {
        double hangle = angle * 0.5;
        double s = Math.sin(hangle);
        double c = Math.cosFromSin(s, hangle);
        dest.set(c * x - s * y,
                 c * y + s * x,
                 c * z + s * w,
                 c * w - s * z);
        return dest;
    }

    /**
     * Apply a rotation to <code>this</code> quaternion rotating the given radians about the cartesian base unit axes,
     * called the euler angles using rotation sequence <code>XYZ</code>.
     * <p>
     * This method is equivalent to calling: <code>rotateX(angleX).rotateY(angleY).rotateZ(angleZ)</code>
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>Q * R</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>Q * R * v</code>, the
     * rotation added by this method will be applied first!
     * 
     * @param angleX
     *              the angle in radians to rotate about the x axis
     * @param angleY
     *              the angle in radians to rotate about the y axis
     * @param angleZ
     *              the angle in radians to rotate about the z axis
     * @return this
     */
    public Quaterniond rotateXYZ(double angleX, double angleY, double angleZ) {
        return rotateXYZ(angleX, angleY, angleZ, this);
    }

    public Quaterniond rotateXYZ(double angleX, double angleY, double angleZ, Quaterniond dest) {
        double sx =  Math.sin(angleX * 0.5);
        double cx =  Math.cosFromSin(sx, angleX * 0.5);
        double sy =  Math.sin(angleY * 0.5);
        double cy =  Math.cosFromSin(sy, angleY * 0.5);
        double sz =  Math.sin(angleZ * 0.5);
        double cz =  Math.cosFromSin(sz, angleZ * 0.5);

        double cycz = cy * cz;
        double sysz = sy * sz;
        double sycz = sy * cz;
        double cysz = cy * sz;
        double w = cx*cycz - sx*sysz;
        double x = sx*cycz + cx*sysz;
        double y = cx*sycz - sx*cysz;
        double z = cx*cysz + sx*sycz;
        // right-multiply
        return dest.set(Math.fma(this.w, x, Math.fma(this.x, w, Math.fma(this.y, z, -this.z * y))),
                        Math.fma(this.w, y, Math.fma(-this.x, z, Math.fma(this.y, w, this.z * x))),
                        Math.fma(this.w, z, Math.fma(this.x, y, Math.fma(-this.y, x, this.z * w))),
                        Math.fma(this.w, w, Math.fma(-this.x, x, Math.fma(-this.y, y, -this.z * z))));
    }

    /**
     * Apply a rotation to <code>this</code> quaternion rotating the given radians about the cartesian base unit axes,
     * called the euler angles, using the rotation sequence <code>ZYX</code>.
     * <p>
     * This method is equivalent to calling: <code>rotateZ(angleZ).rotateY(angleY).rotateX(angleX)</code>
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>Q * R</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>Q * R * v</code>, the
     * rotation added by this method will be applied first!
     * 
     * @param angleZ
     *              the angle in radians to rotate about the z axis
     * @param angleY
     *              the angle in radians to rotate about the y axis
     * @param angleX
     *              the angle in radians to rotate about the x axis
     * @return this
     */
    public Quaterniond rotateZYX(double angleZ, double angleY, double angleX) {
        return rotateZYX(angleZ, angleY, angleX, this);
    }

    public Quaterniond rotateZYX(double angleZ, double angleY, double angleX, Quaterniond dest) {
        double sx =  Math.sin(angleX * 0.5);
        double cx =  Math.cosFromSin(sx, angleX * 0.5);
        double sy =  Math.sin(angleY * 0.5);
        double cy =  Math.cosFromSin(sy, angleY * 0.5);
        double sz =  Math.sin(angleZ * 0.5);
        double cz =  Math.cosFromSin(sz, angleZ * 0.5);

        double cycz = cy * cz;
        double sysz = sy * sz;
        double sycz = sy * cz;
        double cysz = cy * sz;
        double w = cx*cycz + sx*sysz;
        double x = sx*cycz - cx*sysz;
        double y = cx*sycz + sx*cysz;
        double z = cx*cysz - sx*sycz;
        // right-multiply
        return dest.set(Math.fma(this.w, x, Math.fma(this.x, w, Math.fma(this.y, z, -this.z * y))),
                        Math.fma(this.w, y, Math.fma(-this.x, z, Math.fma(this.y, w, this.z * x))),
                        Math.fma(this.w, z, Math.fma(this.x, y, Math.fma(-this.y, x, this.z * w))),
                        Math.fma(this.w, w, Math.fma(-this.x, x, Math.fma(-this.y, y, -this.z * z))));
    }

    /**
     * Apply a rotation to <code>this</code> quaternion rotating the given radians about the cartesian base unit axes,
     * called the euler angles, using the rotation sequence <code>YXZ</code>.
     * <p>
     * This method is equivalent to calling: <code>rotateY(angleY).rotateX(angleX).rotateZ(angleZ)</code>
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>Q * R</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>Q * R * v</code>, the
     * rotation added by this method will be applied first!
     * 
     * @param angleY
     *              the angle in radians to rotate about the y axis
     * @param angleX
     *              the angle in radians to rotate about the x axis
     * @param angleZ
     *              the angle in radians to rotate about the z axis
     * @return this
     */
    public Quaterniond rotateYXZ(double angleY, double angleX, double angleZ) {
        return rotateYXZ(angleY, angleX, angleZ, this);
    }

    public Quaterniond rotateYXZ(double angleY, double angleX, double angleZ, Quaterniond dest) {
        double sx =  Math.sin(angleX * 0.5);
        double cx =  Math.cosFromSin(sx, angleX * 0.5);
        double sy =  Math.sin(angleY * 0.5);
        double cy =  Math.cosFromSin(sy, angleY * 0.5);
        double sz =  Math.sin(angleZ * 0.5);
        double cz =  Math.cosFromSin(sz, angleZ * 0.5);

        double yx = cy * sx;
        double yy = sy * cx;
        double yz = sy * sx;
        double yw = cy * cx;
        double x = yx * cz + yy * sz;
        double y = yy * cz - yx * sz;
        double z = yw * sz - yz * cz;
        double w = yw * cz + yz * sz;
        // right-multiply
        return dest.set(Math.fma(this.w, x, Math.fma(this.x, w, Math.fma(this.y, z, -this.z * y))),
                        Math.fma(this.w, y, Math.fma(-this.x, z, Math.fma(this.y, w, this.z * x))),
                        Math.fma(this.w, z, Math.fma(this.x, y, Math.fma(-this.y, x, this.z * w))),
                        Math.fma(this.w, w, Math.fma(-this.x, x, Math.fma(-this.y, y, -this.z * z))));
    }

    public Vector3d getEulerAnglesXYZ(Vector3d eulerAngles) {
        eulerAngles.x = Math.atan2(x * w - y * z, 0.5 - x * x - y * y);
        eulerAngles.y = Math.atan2(2.0 * (x * z + y * w), 2.0 * hypotUnit(x * w - y * z, 0.5 - x * x - y * y));
        eulerAngles.z = Math.atan2(z * w - x * y, 0.5 - y * y - z * z);
        eulerAngles.x = eulerOuter(x, y, z, w, 0, 1, eulerAngles.y, 2, eulerAngles.z);
        return eulerAngles;
    }

    public Vector3d getEulerAnglesZYX(Vector3d eulerAngles) {
        eulerAngles.x = Math.atan2(y * z + w * x, 0.5 - x * x - y * y);
        eulerAngles.y = Math.atan2(-2.0 * (x * z - w * y), 2.0 * hypotUnit(y * z + w * x, 0.5 - x * x - y * y));
        eulerAngles.z = Math.atan2(x * y + w * z, 0.5 - y * y - z * z);
        eulerAngles.z = eulerOuter(x, y, z, w, 2, 1, eulerAngles.y, 0, eulerAngles.x);
        return eulerAngles;
    }

    public Vector3d getEulerAnglesZXY(Vector3d eulerAngles) {
        eulerAngles.x = Math.atan2(2.0 * (w * x + y * z), 2.0 * hypotUnit(w * y - x * z, 0.5 - y * y - x * x));
        eulerAngles.y = Math.atan2(w * y - x * z, 0.5 - y * y - x * x);
        eulerAngles.z = Math.atan2(w * z - x * y, 0.5 - z * z - x * x);
        eulerAngles.z = eulerOuter(x, y, z, w, 2, 0, eulerAngles.x, 1, eulerAngles.y);
        return eulerAngles;
    }

    public Vector3d getEulerAnglesYXZ(Vector3d eulerAngles) {
        eulerAngles.x = Math.atan2(-2.0 * (y * z - w * x), 2.0 * hypotUnit(x * z + y * w, 0.5 - y * y - x * x));
        eulerAngles.y = Math.atan2(x * z + y * w, 0.5 - y * y - x * x);
        eulerAngles.z = Math.atan2(y * x + w * z, 0.5 - x * x - z * z);
        eulerAngles.y = eulerOuter(x, y, z, w, 1, 0, eulerAngles.x, 2, eulerAngles.z);
        return eulerAngles;
    }

    public Quaterniond rotateAxis(double angle, double axisX, double axisY, double axisZ, Quaterniond dest) {
        double hangle = angle / 2.0;
        double sinAngle = Math.sin(hangle);
        double invVLength = Math.invsqrt(Math.fma(axisX, axisX, Math.fma(axisY, axisY, axisZ * axisZ)));
        double rx = axisX * invVLength * sinAngle;
        double ry = axisY * invVLength * sinAngle;
        double rz = axisZ * invVLength * sinAngle;
        double rw = Math.cosFromSin(sinAngle, hangle);
        return dest.set(Math.fma(this.w, rx, Math.fma(this.x, rw, Math.fma(this.y, rz, -this.z * ry))),
                        Math.fma(this.w, ry, Math.fma(-this.x, rz, Math.fma(this.y, rw, this.z * rx))),
                        Math.fma(this.w, rz, Math.fma(this.x, ry, Math.fma(-this.y, rx, this.z * rw))),
                        Math.fma(this.w, rw, Math.fma(-this.x, rx, Math.fma(-this.y, ry, -this.z * rz))));
    }

    public Quaterniond rotateAxis(double angle, Vector3dc axis, Quaterniond dest) {
        return rotateAxis(angle, axis.x(), axis.y(), axis.z(), dest);
    }

    /**
     * Apply a rotation to <code>this</code> quaternion rotating the given radians about the specified axis.
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>Q * R</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>Q * R * v</code>, the
     * rotation added by this method will be applied first!
     * 
     * @see #rotateAxis(double, double, double, double, Quaterniond)
     * 
     * @param angle
     *              the angle in radians to rotate about the specified axis
     * @param axis
     *              the rotation axis
     * @return this
     */
    public Quaterniond rotateAxis(double angle, Vector3dc axis) {
        return rotateAxis(angle, axis.x(), axis.y(), axis.z(), this);
    }

    /**
     * Apply a rotation to <code>this</code> quaternion rotating the given radians about the specified axis.
     * <p>
     * If <code>Q</code> is <code>this</code> quaternion and <code>R</code> the quaternion representing the 
     * specified rotation, then the new quaternion will be <code>Q * R</code>. So when transforming a
     * vector <code>v</code> with the new quaternion by using <code>Q * R * v</code>, the
     * rotation added by this method will be applied first!
     * 
     * @see #rotateAxis(double, double, double, double, Quaterniond)
     * 
     * @param angle
     *              the angle in radians to rotate about the specified axis
     * @param axisX
     *              the x coordinate of the rotation axis
     * @param axisY
     *              the y coordinate of the rotation axis
     * @param axisZ
     *              the z coordinate of the rotation axis
     * @return this
     */
    public Quaterniond rotateAxis(double angle, double axisX, double axisY, double axisZ) {
        return rotateAxis(angle, axisX, axisY, axisZ, this);
    }

    public Vector3d positiveX(Vector3d dir) {
        double invNorm = 1.0 / Norms.norm4(x, y, z, w);
        double nx = -x * invNorm;
        double ny = -y * invNorm;
        double nz = -z * invNorm;
        double nw =  w * invNorm;
        double dy = ny + ny;
        double dz = nz + nz;
        dir.x = -ny * dy - nz * dz + 1.0;
        dir.y =  nx * dy + nw * dz;
        dir.z =  nx * dz - nw * dy;
        return dir;
    }

    public Vector3d normalizedPositiveX(Vector3d dir) {
        double dy = y + y;
        double dz = z + z;
        dir.x = -y * dy - z * dz + 1.0;
        dir.y =  x * dy - w * dz;
        dir.z =  x * dz + w * dy;
        return dir;
    }

    public Vector3d positiveY(Vector3d dir) {
        double invNorm = 1.0 / Norms.norm4(x, y, z, w);
        double nx = -x * invNorm;
        double ny = -y * invNorm;
        double nz = -z * invNorm;
        double nw =  w * invNorm;
        double dx = nx + nx;
        double dy = ny + ny;
        double dz = nz + nz;
        dir.x =  nx * dy - nw * dz;
        dir.y = -nx * dx - nz * dz + 1.0;
        dir.z =  ny * dz + nw * dx;
        return dir;
    }

    public Vector3d normalizedPositiveY(Vector3d dir) {
        double dx = x + x;
        double dy = y + y;
        double dz = z + z;
        dir.x =  x * dy + w * dz;
        dir.y = -x * dx - z * dz + 1.0;
        dir.z =  y * dz - w * dx;
        return dir;
    }

    public Vector3d positiveZ(Vector3d dir) {
        double invNorm = 1.0 / Norms.norm4(x, y, z, w);
        double nx = -x * invNorm;
        double ny = -y * invNorm;
        double nz = -z * invNorm;
        double nw =  w * invNorm;
        double dx = nx + nx;
        double dy = ny + ny;
        double dz = nz + nz;
        dir.x =  nx * dz + nw * dy;
        dir.y =  ny * dz - nw * dx;
        dir.z = -nx * dx - ny * dy + 1.0;
        return dir;
    }

    public Vector3d normalizedPositiveZ(Vector3d dir) {
        double dx = x + x;
        double dy = y + y;
        double dz = z + z;
        dir.x =  x * dz - w * dy;
        dir.y =  y * dz + w * dx;
        dir.z = -x * dx - y * dy + 1.0;
        return dir;
    }

    /**
     * Conjugate <code>this</code> by the given quaternion <code>q</code> by computing <code>q * this * q^-1</code>.
     * 
     * @param q
     *          the {@link Quaterniondc} to conjugate <code>this</code> by
     * @return this
     */
    public Quaterniond conjugateBy(Quaterniondc q) {
        return conjugateBy(q, this);
    }

    /**
     * Conjugate <code>this</code> by the given quaternion <code>q</code> by computing <code>q * this * q^-1</code>
     * and store the result into <code>dest</code>.
     * 
     * @param q
     *          the {@link Quaterniondc} to conjugate <code>this</code> by
     * @param dest
     *          will hold the result
     * @return dest
     */
    public Quaterniond conjugateBy(Quaterniondc q, Quaterniond dest) {
        double invNorm = 1.0 / q.lengthSquared();
        double qix = -q.x() * invNorm, qiy = -q.y() * invNorm, qiz = -q.z() * invNorm, qiw = q.w() * invNorm;
        double qpx = Math.fma(q.w(), x, Math.fma(q.x(), w, Math.fma(q.y(), z, -q.z() * y)));
        double qpy = Math.fma(q.w(), y, Math.fma(-q.x(), z, Math.fma(q.y(), w, q.z() * x)));
        double qpz = Math.fma(q.w(), z, Math.fma(q.x(), y, Math.fma(-q.y(), x, q.z() * w)));
        double qpw = Math.fma(q.w(), w, Math.fma(-q.x(), x, Math.fma(-q.y(), y, -q.z() * z)));
        return dest.set(Math.fma(qpw, qix, Math.fma(qpx, qiw, Math.fma(qpy, qiz, -qpz * qiy))),
                        Math.fma(qpw, qiy, Math.fma(-qpx, qiz, Math.fma(qpy, qiw, qpz * qix))),
                        Math.fma(qpw, qiz, Math.fma(qpx, qiy, Math.fma(-qpy, qix, qpz * qiw))),
                        Math.fma(qpw, qiw, Math.fma(-qpx, qix, Math.fma(-qpy, qiy, -qpz * qiz))));
    }

    public boolean isFinite() {
        return Math.isFinite(x) && Math.isFinite(y) && Math.isFinite(z) && Math.isFinite(w);
    }

    public boolean equals(Quaterniondc q, double delta) {
        if (this == q)
            return true;
        if (q == null)
            return false;
        if (getClass() != q.getClass())
            return false;
        if (!Runtime.equals(x, q.x(), delta))
            return false;
        if (!Runtime.equals(y, q.y(), delta))
            return false;
        if (!Runtime.equals(z, q.z(), delta))
            return false;
        if (!Runtime.equals(w, q.w(), delta))
            return false;
        return true;
    }

    public boolean equals(double x, double y, double z, double w) {
        if (Double.doubleToLongBits(this.x) != Double.doubleToLongBits(x))
            return false;
        if (Double.doubleToLongBits(this.y) != Double.doubleToLongBits(y))
            return false;
        if (Double.doubleToLongBits(this.z) != Double.doubleToLongBits(z))
            return false;
        if (Double.doubleToLongBits(this.w) != Double.doubleToLongBits(w))
            return false;
        return true;
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * The outer Euler angle of unit quaternion {@code q = q_outer(a) · q_middle(b) · q_inner(c)}, given b and c: the
     * residual {@code q · q_inner(−c) · q_middle(−b)} is a pure rotation about the outer axis. Deriving the outer angle
     * from it keeps the three angles consistent, so they rebuild the rotation exactly even next to gimbal lock (where
     * the outer and inner angles are individually ill-determined), and at gimbal lock it absorbs the combined angle.
     * Axes are 0 = x, 1 = y, 2 = z.
     */
    /** {@code sqrt(a² + b²)} for arguments of order 1 (no overflow possible); deterministic, unlike {@code Math.hypot}. */
    static double hypotUnit(double a, double b) {
        return java.lang.Math.sqrt(a * a + b * b);
    }

    static double eulerOuter(double qx, double qy, double qz, double qw, int outer, int middle, double b, int inner,
            double c) {
        // r = q · q_inner(−c)
        double h = -0.5 * c, sh = AccurateMath.sin(h), ch = AccurateMath.cos(h);
        double bx = inner == 0 ? sh : 0.0, by = inner == 1 ? sh : 0.0, bz = inner == 2 ? sh : 0.0;
        double rx = qw * bx + qx * ch + qy * bz - qz * by;
        double ry = qw * by - qx * bz + qy * ch + qz * bx;
        double rz = qw * bz + qx * by - qy * bx + qz * ch;
        double rw = qw * ch - qx * bx - qy * by - qz * bz;
        // r = r · q_middle(−b)
        h = -0.5 * b;
        sh = AccurateMath.sin(h);
        ch = AccurateMath.cos(h);
        bx = middle == 0 ? sh : 0.0;
        by = middle == 1 ? sh : 0.0;
        bz = middle == 2 ? sh : 0.0;
        double sx = rw * bx + rx * ch + ry * bz - rz * by;
        double sy = rw * by - rx * bz + ry * ch + rz * bx;
        double sz = rw * bz + rx * by - ry * bx + rz * ch;
        double sw = rw * ch - rx * bx - ry * by - rz * bz;
        double s = outer == 0 ? sx : outer == 1 ? sy : sz;
        if (sw < 0.0) {
            s = -s;
            sw = -sw;
        }
        return 2.0 * AccurateMath.atan2(s, sw);
    }

    private static final ThreadLocal<double[]> EULER_SCRATCH = ThreadLocal.withInitial(() -> new double[3]);

    /**
     * Euler angles (by axis: x, y, z, in a per-thread array) of the rotation part of a matrix, for order {@code 0} =
     * XYZ, {@code 1} = ZYX, {@code 2} = YXZ. The columns are normalized (removing scale), the rotation is converted to a
     * quaternion, and the angles come from the same accurate formulas as {@link #getEulerAnglesXYZ}: the middle angle
     * from {@code atan2}, never {@code asin} or {@code sqrt(1 − s²)} (which produce NaN when rounding pushes |s| above 1),
     * and the outer angle from the residual rotation, so the angles rebuild the rotation exactly, also at gimbal lock.
     */
    static double[] eulerFromMatrix(int order, double m00, double m01, double m02, double m10, double m11, double m12,
            double m20, double m21, double m22) {
        double lx = 1.0 / java.lang.Math.sqrt(m00 * m00 + m01 * m01 + m02 * m02);
        double ly = 1.0 / java.lang.Math.sqrt(m10 * m10 + m11 * m11 + m12 * m12);
        double lz = 1.0 / java.lang.Math.sqrt(m20 * m20 + m21 * m21 + m22 * m22);
        m00 *= lx;
        m01 *= lx;
        m02 *= lx;
        m10 *= ly;
        m11 *= ly;
        m12 *= ly;
        m20 *= lz;
        m21 *= lz;
        m22 *= lz;
        double x, y, z, w, t;
        double tr = m00 + m11 + m22;
        if (tr >= 0.0) {
            t = java.lang.Math.sqrt(tr + 1.0);
            w = t * 0.5;
            t = 0.5 / t;
            x = (m12 - m21) * t;
            y = (m20 - m02) * t;
            z = (m01 - m10) * t;
        } else if (m00 >= m11 && m00 >= m22) {
            t = java.lang.Math.sqrt(m00 - (m11 + m22) + 1.0);
            x = t * 0.5;
            t = 0.5 / t;
            y = (m10 + m01) * t;
            z = (m02 + m20) * t;
            w = (m12 - m21) * t;
        } else if (m11 > m22) {
            t = java.lang.Math.sqrt(m11 - (m22 + m00) + 1.0);
            y = t * 0.5;
            t = 0.5 / t;
            z = (m21 + m12) * t;
            x = (m10 + m01) * t;
            w = (m20 - m02) * t;
        } else {
            t = java.lang.Math.sqrt(m22 - (m00 + m11) + 1.0);
            z = t * 0.5;
            t = 0.5 / t;
            x = (m02 + m20) * t;
            y = (m21 + m12) * t;
            w = (m01 - m10) * t;
        }
        double[] e = EULER_SCRATCH.get();
        if (order == 0) { // XYZ: outer x, middle y, inner z
            e[1] = AccurateMath.atan2(2.0 * (x * z + y * w), 2.0 * hypotUnit(x * w - y * z, 0.5 - x * x - y * y));
            e[2] = AccurateMath.atan2(z * w - x * y, 0.5 - y * y - z * z);
            e[0] = eulerOuter(x, y, z, w, 0, 1, e[1], 2, e[2]);
        } else if (order == 1) { // ZYX: outer z, middle y, inner x
            e[1] = AccurateMath.atan2(-2.0 * (x * z - w * y), 2.0 * hypotUnit(y * z + w * x, 0.5 - x * x - y * y));
            e[0] = AccurateMath.atan2(y * z + w * x, 0.5 - x * x - y * y);
            e[2] = eulerOuter(x, y, z, w, 2, 1, e[1], 0, e[0]);
        } else { // YXZ: outer y, middle x, inner z
            e[0] = AccurateMath.atan2(-2.0 * (y * z - w * x), 2.0 * hypotUnit(x * z + y * w, 0.5 - y * y - x * x));
            e[2] = AccurateMath.atan2(y * x + w * z, 0.5 - x * x - z * z);
            e[1] = eulerOuter(x, y, z, w, 1, 0, e[0], 2, e[2]);
        }
        return e;
    }
}
