/*
 * The MIT License
 *
 * Copyright (c) 2015-2024 Kai Burjack
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
 * Represents a 3D rotation of a given radians about an axis represented as an
 * unit 3D vector.
 * <p>
 * This class uses single-precision components.
 * 
 * @author Kai Burjack
 */
public class AxisAngle4f implements Externalizable, Cloneable {

    private static final long serialVersionUID = 1L;

    /**
     * The number of bytes used to represent a {@code AxisAngle4f} value.
     */
    public static final int BYTES = 4*Float.BYTES;

    /**
     * The angle in radians.
     */
    public float angle;
    /**
     * The x-component of the rotation axis.
     */
    public float x;
    /**
     * The y-component of the rotation axis.
     */
    public float y;
    /**
     * The z-component of the rotation axis.
     */
    public float z;

    /**
     * Create a new {@link AxisAngle4f} with zero rotation about <code>(0, 0, 1)</code>.
     */
    public AxisAngle4f() {
        z = 1.0f;
    }

    /**
     * Create a new {@link AxisAngle4f} with the same values of <code>a</code>.
     * 
     * @param a
     *            the AngleAxis4f to copy the values from
     */
    public AxisAngle4f(AxisAngle4f a) {
        x = a.x;
        y = a.y;
        z = a.z;
        angle = (a.angle < 0.0 ? Math.PI_TIMES_2_f + a.angle % Math.PI_TIMES_2_f : a.angle) % Math.PI_TIMES_2_f;
    }

    /**
     * Create a new {@link AxisAngle4f} from the given {@link Quaternionfc}.
     * <p>
     * Reference: <a href=
     * "http://www.euclideanspace.com/maths/geometry/rotations/conversions/quaternionToAngle/"
     * >http://www.euclideanspace.com</a>
     * 
     * @param q
     *            the quaternion from which to create the new AngleAxis4f
     */
    public AxisAngle4f(Quaternionfc q) {
        fromQuaternion(q.x(), q.y(), q.z(), q.w());
    }

    /**
     * Create a new {@link AxisAngle4f} with the given values.
     *
     * @param angle
     *            the angle in radians
     * @param x
     *            the x-coordinate of the rotation axis
     * @param y
     *            the y-coordinate of the rotation axis
     * @param z
     *            the z-coordinate of the rotation axis
     */
    public AxisAngle4f(float angle, float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.angle = (angle < 0.0 ? Math.PI_TIMES_2_f + angle % Math.PI_TIMES_2_f : angle) % Math.PI_TIMES_2_f;
    }

    /**
     * Create a new {@link AxisAngle4f} with the given values.
     *
     * @param angle the angle in radians
     * @param v     the rotation axis as a {@link Vector3f}
     */
    public AxisAngle4f(float angle, Vector3fc v) {
        this(angle, v.x(), v.y(), v.z());
    }

    /**
     * Set this {@link AxisAngle4f} to the values of <code>a</code>.
     * 
     * @param a
     *            the AngleAxis4f to copy the values from
     * @return this
     */
    public AxisAngle4f set(AxisAngle4f a) {
        x = a.x;
        y = a.y;
        z = a.z;
        angle = a.angle;
        angle = (angle < 0.0 ? Math.PI_TIMES_2_f + angle % Math.PI_TIMES_2_f : angle) % Math.PI_TIMES_2_f;
        return this;
    }

    /**
     * Set this {@link AxisAngle4f} to the values of <code>a</code>.
     * 
     * @param a
     *            the AngleAxis4d to copy the values from
     * @return this
     */
    public AxisAngle4f set(AxisAngle4d a) {
        x = (float) a.x;
        y = (float) a.y;
        z = (float) a.z;
        angle = (float) a.angle;
        angle = (angle < 0.0 ? Math.PI_TIMES_2_f + angle % Math.PI_TIMES_2_f : angle) % Math.PI_TIMES_2_f;
        return this;
    }

    /**
     * Set this {@link AxisAngle4f} to the given values.
     * 
     * @param angle
     *            the angle in radians
     * @param x
     *            the x-coordinate of the rotation axis
     * @param y
     *            the y-coordinate of the rotation axis
     * @param z
     *            the z-coordinate of the rotation axis
     * @return this
     */
    public AxisAngle4f set(float angle, float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.angle = (angle < 0.0 ? Math.PI_TIMES_2_f + angle % Math.PI_TIMES_2_f : angle) % Math.PI_TIMES_2_f;
        return this;
    }

    /**
     * Set this {@link AxisAngle4f} to the given values.
     *
     * @param angle
     *            the angle in radians
     * @param v    
     *            the rotation axis as a {@link Vector3f}
     * @return this
     */
    public AxisAngle4f set(float angle, Vector3fc v) {
        return set(angle, v.x(), v.y(), v.z());
    }

    /**
     * Set this {@link AxisAngle4f} to be equivalent to the given
     * {@link Quaternionfc}.
     * 
     * @param q
     *            the quaternion to set this AngleAxis4f from
     * @return this
     */
    public AxisAngle4f set(Quaternionfc q) {
        fromQuaternion(q.x(), q.y(), q.z(), q.w());
        return this;
    }

    /**
     * Set this {@link AxisAngle4f} to be equivalent to the given
     * {@link Quaterniondc}.
     * 
     * @param q
     *            the quaternion to set this AngleAxis4f from
     * @return this
     */
    public AxisAngle4f set(Quaterniondc q) {
        fromQuaternion(q.x(), q.y(), q.z(), q.w());
        return this;
    }

    /**
     * Set this {@link AxisAngle4f} to be equivalent to the rotation 
     * of the given {@link Matrix3fc}.
     * <p>
     * Reference: <a href="http://www.euclideanspace.com/maths/geometry/rotations/conversions/matrixToAngle/">http://www.euclideanspace.com</a>
     * 
     * @param m
     *            the Matrix3fc to set this AngleAxis4f from
     * @return this
     */
    public AxisAngle4f set(Matrix3fc m) {
        return fromRotationMatrix(m.m00(), m.m01(), m.m02(), m.m10(), m.m11(), m.m12(), m.m20(), m.m21(), m.m22());
    }

    /**
     * Set this {@link AxisAngle4f} to be equivalent to the rotation 
     * of the given {@link Matrix3dc}.
     * <p>
     * Reference: <a href="http://www.euclideanspace.com/maths/geometry/rotations/conversions/matrixToAngle/">http://www.euclideanspace.com</a>
     * 
     * @param m
     *            the Matrix3d to set this AngleAxis4f from
     * @return this
     */
    public AxisAngle4f set(Matrix3dc m) {
        return fromRotationMatrix(m.m00(), m.m01(), m.m02(), m.m10(), m.m11(), m.m12(), m.m20(), m.m21(), m.m22());
    }

    /**
     * Set this {@link AxisAngle4f} to be equivalent to the rotational component 
     * of the given {@link Matrix4fc}.
     * <p>
     * Reference: <a href="http://www.euclideanspace.com/maths/geometry/rotations/conversions/matrixToAngle/">http://www.euclideanspace.com</a>
     * 
     * @param m
     *            the Matrix4fc to set this AngleAxis4f from
     * @return this
     */
    public AxisAngle4f set(Matrix4fc m) {
        return fromRotationMatrix(m.m00(), m.m01(), m.m02(), m.m10(), m.m11(), m.m12(), m.m20(), m.m21(), m.m22());
    }

    /**
     * Set this {@link AxisAngle4f} to be equivalent to the rotational component 
     * of the given {@link Matrix4x3fc}.
     * <p>
     * Reference: <a href="http://www.euclideanspace.com/maths/geometry/rotations/conversions/matrixToAngle/">http://www.euclideanspace.com</a>
     * 
     * @param m
     *            the Matrix4x3fc to set this AngleAxis4f from
     * @return this
     */
    public AxisAngle4f set(Matrix4x3fc m) {
        return fromRotationMatrix(m.m00(), m.m01(), m.m02(), m.m10(), m.m11(), m.m12(), m.m20(), m.m21(), m.m22());
    }

    /**
     * Set this {@link AxisAngle4f} to be equivalent to the rotational component 
     * of the given {@link Matrix4dc}.
     * <p>
     * Reference: <a href="http://www.euclideanspace.com/maths/geometry/rotations/conversions/matrixToAngle/">http://www.euclideanspace.com</a>
     * 
     * @param m
     *            the Matrix4dc to set this AngleAxis4f from
     * @return this
     */
    public AxisAngle4f set(Matrix4dc m) {
        return fromRotationMatrix(m.m00(), m.m01(), m.m02(), m.m10(), m.m11(), m.m12(), m.m20(), m.m21(), m.m22());
    }

    /**
     * Set the given {@link Quaternionf} to be equivalent to this {@link AxisAngle4f} rotation.
     * 
     * @see Quaternionf#set(AxisAngle4f)
     * 
     * @param q
     *          the quaternion to set
     * @return q
     */
    public Quaternionf get(Quaternionf q) {
        return q.set(this);
    }

    /**
     * Set the given {@link Quaterniond} to be equivalent to this {@link AxisAngle4f} rotation.
     * 
     * @see Quaterniond#set(AxisAngle4f)
     * 
     * @param q
     *          the quaternion to set
     * @return q
     */
    public Quaterniond get(Quaterniond q) {
        return q.set(this);
    }

    /**
     * Set the given {@link Matrix4f} to a rotation transformation equivalent to this {@link AxisAngle4f}.
     * 
     * @see Matrix4f#set(AxisAngle4f)
     * 
     * @param m
     *          the matrix to set
     * @return m
     */
    public Matrix4f get(Matrix4f m) {
        return m.set(this);
    }

    /**
     * Set the given {@link Matrix3f} to a rotation transformation equivalent to this {@link AxisAngle4f}.
     * 
     * @see Matrix3f#set(AxisAngle4f)
     * 
     * @param m
     *          the matrix to set
     * @return m
     */
    public Matrix3f get(Matrix3f m) {
        return m.set(this);
    }

    /**
     * Set the given {@link Matrix4d} to a rotation transformation equivalent to this {@link AxisAngle4f}.
     * 
     * @see Matrix4f#set(AxisAngle4f)
     * 
     * @param m
     *          the matrix to set
     * @return m
     */
    public Matrix4d get(Matrix4d m) {
        return m.set(this);
    }

    /**
     * Set the given {@link Matrix3d} to a rotation transformation equivalent to this {@link AxisAngle4f}.
     * 
     * @see Matrix3f#set(AxisAngle4f)
     * 
     * @param m
     *          the matrix to set
     * @return m
     */
    public Matrix3d get(Matrix3d m) {
        return m.set(this);
    }

    /**
     * Set the given {@link AxisAngle4d} to this {@link AxisAngle4f}.
     * 
     * @param dest
     *          will hold the result
     * @return dest
     */
    public AxisAngle4d get(AxisAngle4d dest) {
        return dest.set(this);
    }

    /**
     * Set the given {@link AxisAngle4f} to this {@link AxisAngle4f}.
     * 
     * @param dest
     *          will hold the result
     * @return dest
     */
    public AxisAngle4f get(AxisAngle4f dest) {
        return dest.set(this);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeFloat(angle);
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        angle = in.readFloat();
        x = in.readFloat();
        y = in.readFloat();
        z = in.readFloat();
    }

    /**
     * Normalize the axis vector.
     * 
     * @return this
     */
    public AxisAngle4f normalize() {
        float invLength = Math.invsqrt(x * x + y * y + z * z);
        x *= invLength;
        y *= invLength;
        z *= invLength;
        return this;
    }

    /**
     * Increase the rotation angle by the given amount.
     * <p>
     * This method also takes care of wrapping around.
     * 
     * @param ang
     *          the angle increase
     * @return this
     */
    public AxisAngle4f rotate(float ang) {
        angle += ang;
        angle = (angle < 0.0 ? Math.PI_TIMES_2_f + angle % (Math.PI_TIMES_2_f) : angle) % (Math.PI_TIMES_2_f);
        return this;
    }

    /**
     * Transform the given vector by the rotation transformation described by this {@link AxisAngle4f}.
     * 
     * @param v
     *          the vector to transform
     * @return v
     */
    public Vector3f transform(Vector3f v) {
        return transform(v, v);
    }

    /**
     * Transform the given vector by the rotation transformation described by this {@link AxisAngle4f}
     * and store the result in <code>dest</code>.
     * 
     * @param v
     *          the vector to transform
     * @param dest
     *          will hold the result
     * @return dest
     */
    public Vector3f transform(Vector3fc v, Vector3f dest) {
        double sin = Math.sin(angle);
        double cos = Math.cosFromSin(sin, angle);
        float dot = x * v.x() + y * v.y() + z * v.z();
        dest.set((float) (v.x() * cos + sin * (y * v.z() - z * v.y()) + (1.0 - cos) * dot * x),
                 (float) (v.y() * cos + sin * (z * v.x() - x * v.z()) + (1.0 - cos) * dot * y),
                 (float) (v.z() * cos + sin * (x * v.y() - y * v.x()) + (1.0 - cos) * dot * z));
        return dest;
    }

    /**
     * Transform the given vector by the rotation transformation described by this {@link AxisAngle4f}.
     * 
     * @param v
     *          the vector to transform
     * @return v
     */
    public Vector4f transform(Vector4f v) {
        return transform(v, v);
    }

    /**
     * Transform the given vector by the rotation transformation described by this {@link AxisAngle4f}
     * and store the result in <code>dest</code>.
     * 
     * @param v
     *          the vector to transform
     * @param dest
     *          will hold the result
     * @return dest
     */
    public Vector4f transform(Vector4fc v, Vector4f dest) {
        double sin = Math.sin(angle);
        double cos = Math.cosFromSin(sin, angle);
        float dot = x * v.x() + y * v.y() + z * v.z();
        dest.set((float) (v.x() * cos + sin * (y * v.z() - z * v.y()) + (1.0 - cos) * dot * x),
                 (float) (v.y() * cos + sin * (z * v.x() - x * v.z()) + (1.0 - cos) * dot * y),
                 (float) (v.z() * cos + sin * (x * v.y() - y * v.x()) + (1.0 - cos) * dot * z),
                 v.w()); // was dest.w: the result kept dest's previous w
        return dest;
    }

    /**
     * Return a string representation of this {@link AxisAngle4f}.
     * <p>
     * This method creates a new {@link DecimalFormat} on every invocation with the format string "<code> 0.000E0;-</code>".
     * 
     * @return the string representation
     */
    public String toString() {
        return Runtime.formatNumbers(toString(Options.NUMBER_FORMAT));
    }

    /**
     * Return a string representation of this {@link AxisAngle4f} by formatting the components with the given {@link NumberFormat}.
     * 
     * @param formatter
     *          the {@link NumberFormat} used to format the vector components with
     * @return the string representation
     */
    public String toString(NumberFormat formatter) {
        return "(" + Runtime.format(x, formatter) + " " + Runtime.format(y, formatter) + " " + Runtime.format(z, formatter) + " <| " + Runtime.format(angle, formatter) + ")";
    }

    public int hashCode() {
        final int prime = 31;
        int result = 1;
        float nangle = (angle < 0.0 ? Math.PI_TIMES_2_f + angle % Math.PI_TIMES_2_f : angle) % Math.PI_TIMES_2_f;
        result = prime * result + Float.floatToIntBits(nangle);
        result = prime * result + Float.floatToIntBits(x);
        result = prime * result + Float.floatToIntBits(y);
        result = prime * result + Float.floatToIntBits(z);
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AxisAngle4f other = (AxisAngle4f) obj;
        float nangle = (angle < 0.0 ? Math.PI_TIMES_2_f + angle % Math.PI_TIMES_2_f : angle) % Math.PI_TIMES_2_f;
        float nangleOther = (other.angle < 0.0 ? Math.PI_TIMES_2_f + other.angle % Math.PI_TIMES_2_f : other.angle) % Math.PI_TIMES_2_f;
        if (Float.floatToIntBits(nangle) != Float.floatToIntBits(nangleOther))
            return false;
        if (Float.floatToIntBits(x) != Float.floatToIntBits(other.x))
            return false;
        if (Float.floatToIntBits(y) != Float.floatToIntBits(other.y))
            return false;
        if (Float.floatToIntBits(z) != Float.floatToIntBits(other.z))
            return false;
        return true;
    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Axis and angle of the quaternion {@code (qx, qy, qz, qw)}: {@code angle = 2 atan2(|v|, w)}, accurate at every angle
     * (an {@code acos(w)} form loses everything below about 1e-8 rad); the axis is {@code v / |v|}, or +Z for no rotation.
     */
    private void fromQuaternion(double qx, double qy, double qz, double qw) {
        double v = java.lang.Math.sqrt(qx * qx + qy * qy + qz * qz);
        if (v == 0.0) {
            this.x = 0;
            this.y = 0;
            this.z = 1;
        } else {
            this.x = (float) (qx / v);
            this.y = (float) (qy / v);
            this.z = (float) (qz / v);
        }
        this.angle = (float) (2.0 * AccurateMath.atan2(v, qw));
    }

    /**
     * Axis and angle of the rotation part of a matrix (columns normalized first, removing scale). The angle is
     * {@code atan2(|skew|, trace − 1)}, accurate at every angle; the axis comes from the skew-symmetric part up to 120
     * degrees and from the symmetric part {@code cos θ I + (1 − cos θ) a aᵀ} beyond, where the skew part vanishes.
     */
    private AxisAngle4f fromRotationMatrix(double m00, double m01, double m02, double m10, double m11, double m12,
            double m20, double m21, double m22) {
        double lx = 1.0 / java.lang.Math.sqrt(m00 * m00 + m01 * m01 + m02 * m02);
        double ly = 1.0 / java.lang.Math.sqrt(m10 * m10 + m11 * m11 + m12 * m12);
        double lz = 1.0 / java.lang.Math.sqrt(m20 * m20 + m21 * m21 + m22 * m22);
        double n00 = m00 * lx, n01 = m01 * lx, n02 = m02 * lx;
        double n10 = m10 * ly, n11 = m11 * ly, n12 = m12 * ly;
        double n20 = m20 * lz, n21 = m21 * lz, n22 = m22 * lz;
        double sx = n12 - n21, sy = n20 - n02, sz = n01 - n10;
        double sl = java.lang.Math.sqrt(sx * sx + sy * sy + sz * sz); // 2 sin θ
        double c2 = n00 + n11 + n22 - 1.0; // 2 cos θ
        double a = AccurateMath.atan2(sl, c2);
        double ax, ay, az;
        if (c2 >= -1.0) { // θ up to 120 degrees: the skew part determines the axis
            if (sl == 0.0) {
                this.x = 0;
                this.y = 0;
                this.z = 1;
                this.angle = 0;
                return this;
            }
            ax = sx / sl;
            ay = sy / sl;
            az = sz / sl;
        } else { // near 180 degrees: the symmetric part, scaled by (1 − cos θ) ≥ 1.5
            double c = 0.5 * c2, d = 1.0 - c;
            double xx = (n00 - c) / d, yy = (n11 - c) / d, zz = (n22 - c) / d;
            double xy = (n01 + n10) / (2.0 * d), xz = (n02 + n20) / (2.0 * d), yz = (n12 + n21) / (2.0 * d);
            if (xx >= yy && xx >= zz) {
                ax = java.lang.Math.sqrt(java.lang.Math.max(xx, 0.0));
                ay = xy / ax;
                az = xz / ax;
            } else if (yy >= zz) {
                ay = java.lang.Math.sqrt(java.lang.Math.max(yy, 0.0));
                ax = xy / ay;
                az = yz / ay;
            } else {
                az = java.lang.Math.sqrt(java.lang.Math.max(zz, 0.0));
                ax = xz / az;
                ay = yz / az;
            }
            if (ax * sx + ay * sy + az * sz < 0.0) { // the skew part fixes the sign
                ax = -ax;
                ay = -ay;
                az = -az;
            }
            double al = java.lang.Math.sqrt(ax * ax + ay * ay + az * az);
            ax /= al;
            ay /= al;
            az /= al;
        }
        this.x = (float) ax;
        this.y = (float) ay;
        this.z = (float) az;
        this.angle = (float) a;
        return this;
    }
}
