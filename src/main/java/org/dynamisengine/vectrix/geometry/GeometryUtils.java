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
package org.dynamisengine.vectrix.geometry;

import org.dynamisengine.vectrix.core.*;
import org.dynamisengine.vectrix.core.Math;

/**
 * Useful geometry methods.
 * 
 * @author Kai Burjack
 * @author Richard Greenlees
 */
public class GeometryUtils {

    /**
     * Not meant to be instantiated: every member is static.
     *
     * @deprecated this utility class will not be instantiable from 3.0
     */
    @Deprecated(since = "2.6.0", forRemoval = true)
    public GeometryUtils() {
    }

    /**
     * Compute two arbitrary vectors perpendicular to the given normalized vector <code>(x, y, z)</code>, and store them in <code>dest1</code> and <code>dest2</code>,
     * respectively.
     * <p>
     * The computed vectors will themselves be perpendicular to each another and normalized. So the tree vectors <code>(x, y, z)</code>, <code>dest1</code> and
     * <code>dest2</code> form an orthonormal basis.
     * 
     * @param x
     *            the x coordinate of the normalized input vector
     * @param y
     *            the y coordinate of the normalized input vector
     * @param z
     *            the z coordinate of the normalized input vector
     * @param dest1
     *            will hold the first perpendicular vector
     * @param dest2
     *            will hold the second perpendicular vector
     */
    public static void perpendicular(float x, float y, float z, Vector3f dest1, Vector3f dest2) {
        float magX = z * z + y * y;
        float magY = z * z + x * x;
        float magZ = y * y + x * x;
        float mag;
        if (magX > magY && magX > magZ) {
            dest1.x = 0;
            dest1.y = z;
            dest1.z = -y;
            mag = magX;
        } else if (magY > magZ) {
            dest1.x = -z;
            dest1.y = 0;
            dest1.z = x;
            mag = magY;
        } else {
            dest1.x = y;
            dest1.y = -x;
            dest1.z = 0;
            mag = magZ;
        }
        float len = Math.invsqrt(mag);
        dest1.x *= len;
        dest1.y *= len;
        dest1.z *= len;
        dest2.x = y * dest1.z - z * dest1.y;
        dest2.y = z * dest1.x - x * dest1.z;
        dest2.z = x * dest1.y - y * dest1.x;
    }

    /**
     * Compute two arbitrary vectors perpendicular to the given normalized vector <code>v</code>, and store them in <code>dest1</code> and <code>dest2</code>,
     * respectively.
     * <p>
     * The computed vectors will themselves be perpendicular to each another and normalized. So the tree vectors <code>v</code>, <code>dest1</code> and
     * <code>dest2</code> form an orthonormal basis.
     * 
     * @param v
     *            the {@link Vector3f#normalize() normalized} input vector
     * @param dest1
     *            will hold the first perpendicular vector
     * @param dest2
     *            will hold the second perpendicular vector
     */
    public static void perpendicular(Vector3fc v, Vector3f dest1, Vector3f dest2) {
        perpendicular(v.x(), v.y(), v.z(), dest1, dest2);
    }

    /**
     * Double-precision form of {@link #perpendicular(float, float, float, Vector3f, Vector3f)}: two unit vectors that
     * form an orthonormal basis with the unit vector {@code (x, y, z)}.
     *
     * @since 2.4.0
     */
    public static void perpendicular(double x, double y, double z, Vector3d dest1, Vector3d dest2) {
        double magX = z * z + y * y;
        double magY = z * z + x * x;
        double magZ = y * y + x * x;
        double d1x, d1y, d1z, mag;
        if (magX > magY && magX > magZ) {
            d1x = 0;
            d1y = z;
            d1z = -y;
            mag = magX;
        } else if (magY > magZ) {
            d1x = -z;
            d1y = 0;
            d1z = x;
            mag = magY;
        } else {
            d1x = y;
            d1y = -x;
            d1z = 0;
            mag = magZ;
        }
        double len = 1.0 / java.lang.Math.sqrt(mag);
        d1x *= len;
        d1y *= len;
        d1z *= len;
        dest1.set(d1x, d1y, d1z);
        dest2.set(y * d1z - z * d1y, z * d1x - x * d1z, x * d1y - y * d1x);
    }

    /**
     * Double-precision form of {@link #perpendicular(Vector3fc, Vector3f, Vector3f)}.
     *
     * @since 2.4.0
     */
    public static void perpendicular(Vector3dc v, Vector3d dest1, Vector3d dest2) {
        perpendicular(v.x(), v.y(), v.z(), dest1, dest2);
    }

    /**
     * Calculate the normal of a surface defined by points <code>v1</code>, <code>v2</code> and <code>v3</code> and store it in <code>dest</code>.
     * 
     * @param v0
     *            the first position
     * @param v1
     *            the second position
     * @param v2
     *            the third position
     * @param dest
     *            will hold the result
     */
    /**
     * Double-precision triangle normal: the unit cross product {@code (v1 - v0) × (v2 - v0)}.
     *
     * @since 2.5.0
     */
    public static void normal(double v0X, double v0Y, double v0Z, double v1X, double v1Y, double v1Z, double v2X, double v2Y,
            double v2Z, Vector3d dest) {
        double ax = v1X - v0X, ay = v1Y - v0Y, az = v1Z - v0Z;
        double bx = v2X - v0X, by = v2Y - v0Y, bz = v2Z - v0Z;
        // scale each edge by a power of two (exact) so neither the cross product nor its square can overflow or
        // underflow; the direction is unchanged
        double sa = pow2Scale(ax, ay, az), sb = pow2Scale(bx, by, bz);
        ax *= sa;
        ay *= sa;
        az *= sa;
        bx *= sb;
        by *= sb;
        bz *= sb;
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        dest.set(nx / l, ny / l, nz / l);
    }

    /**
     * Double-precision form of {@link #normal(Vector3fc, Vector3fc, Vector3fc, Vector3f)}.
     *
     * @since 2.5.0
     */
    public static void normal(Vector3dc v0, Vector3dc v1, Vector3dc v2, Vector3d dest) {
        normal(v0.x(), v0.y(), v0.z(), v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z(), dest);
    }

    public static void normal(Vector3fc v0, Vector3fc v1, Vector3fc v2, Vector3f dest) {
        normal(v0.x(), v0.y(), v0.z(), v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z(), dest);
    }

    /**
     * Calculate the normal of a surface defined by points <code>(v1X, v1Y, v1Z)</code>, <code>(v2X, v2Y, v2Z)</code> and <code>(v3X, v3Y, v3Z)</code>
     * and store it in <code>dest</code>.
     * 
     * @param v0X
     *            the x coordinate of the first position
     * @param v0Y
     *            the y coordinate of the first position
     * @param v0Z
     *            the z coordinate of the first position
     * @param v1X
     *            the x coordinate of the second position
     * @param v1Y
     *            the y coordinate of the second position
     * @param v1Z
     *            the z coordinate of the second position
     * @param v2X
     *            the x coordinate of the third position
     * @param v2Y
     *            the y coordinate of the third position
     * @param v2Z
     *            the z coordinate of the third position
     * @param dest
     *            will hold the result
     */
    public static void normal(float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y, float v2Z, Vector3f dest) {
        // in double, rounded once: the float cross product of float differences lost up to 0.086 rad on thin triangles
        double ax = (double) v1X - v0X, ay = (double) v1Y - v0Y, az = (double) v1Z - v0Z;
        double bx = (double) v2X - v0X, by = (double) v2Y - v0Y, bz = (double) v2Z - v0Z;
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        dest.set((float) (nx / l), (float) (ny / l), (float) (nz / l));
    }

    /**
     * Calculate the surface tangent for the three supplied vertices and UV coordinates and store the result in <code>dest</code>.
     *
     * @param v1
     *            XYZ of first vertex
     * @param uv1
     *            UV of first vertex
     * @param v2
     *            XYZ of second vertex
     * @param uv2
     *            UV of second vertex
     * @param v3
     *            XYZ of third vertex
     * @param uv3
     *            UV of third vertex
     * @param dest
     *            the tangent will be stored here
     */
    public static void tangent(Vector3fc v1, Vector2fc uv1, Vector3fc v2, Vector2fc uv2, Vector3fc v3, Vector2fc uv3, Vector3f dest) {
        // read every input first: the destinations may alias the inputs; computed in double
        double dv1 = (double) uv2.y() - uv1.y(), dv2 = (double) uv3.y() - uv1.y();
        double du1 = (double) uv2.x() - uv1.x(), du2 = (double) uv3.x() - uv1.x();
        double e1x = (double) v2.x() - v1.x(), e1y = (double) v2.y() - v1.y(), e1z = (double) v2.z() - v1.z();
        double e2x = (double) v3.x() - v1.x(), e2y = (double) v3.y() - v1.y(), e2z = (double) v3.z() - v1.z();
        double f = 1.0 / (du1 * dv2 - du2 * dv1);
        double tx = f * (dv2 * e1x - dv1 * e2x), ty = f * (dv2 * e1y - dv1 * e2y), tz = f * (dv2 * e1z - dv1 * e2z);
        double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
        dest.set((float) (tx / tl), (float) (ty / tl), (float) (tz / tl));
    }

    /**
     * Calculate the surface bitangent for the three supplied vertices and UV coordinates and store the result in <code>dest</code>.
     *
     * @param v1
     *            XYZ of first vertex
     * @param uv1
     *            UV of first vertex
     * @param v2
     *            XYZ of second vertex
     * @param uv2
     *            UV of second vertex
     * @param v3
     *            XYZ of third vertex
     * @param uv3
     *            UV of third vertex
     * @param dest
     *            the binormal will be stored here
     */
    public static void bitangent(Vector3fc v1, Vector2fc uv1, Vector3fc v2, Vector2fc uv2, Vector3fc v3, Vector2fc uv3, Vector3f dest) {
        // read every input first: the destinations may alias the inputs; computed in double
        double dv1 = (double) uv2.y() - uv1.y(), dv2 = (double) uv3.y() - uv1.y();
        double du1 = (double) uv2.x() - uv1.x(), du2 = (double) uv3.x() - uv1.x();
        double e1x = (double) v2.x() - v1.x(), e1y = (double) v2.y() - v1.y(), e1z = (double) v2.z() - v1.z();
        double e2x = (double) v3.x() - v1.x(), e2y = (double) v3.y() - v1.y(), e2z = (double) v3.z() - v1.z();
        double f = 1.0 / (du1 * dv2 - du2 * dv1);
        double bx = f * (-du2 * e1x + du1 * e2x), by = f * (-du2 * e1y + du1 * e2y), bz = f * (-du2 * e1z + du1 * e2z);
        double bl = Math.sqrt(bx * bx + by * by + bz * bz);
        dest.set((float) (bx / bl), (float) (by / bl), (float) (bz / bl));
    }

    /**
     * Calculate the surface tangent and bitangent for the three supplied vertices and UV coordinates and store the result in <code>dest</code>.
     *
     * @param v1
     *            XYZ of first vertex
     * @param uv1
     *            UV of first vertex
     * @param v2
     *            XYZ of second vertex
     * @param uv2
     *            UV of second vertex
     * @param v3
     *            XYZ of third vertex
     * @param uv3
     *            UV of third vertex
     * @param destTangent
     *            the tangent will be stored here
     * @param destBitangent
     *            the bitangent will be stored here
     */
    public static void tangentBitangent(Vector3fc v1, Vector2fc uv1, Vector3fc v2, Vector2fc uv2, Vector3fc v3, Vector2fc uv3, Vector3f destTangent, Vector3f destBitangent) {
        // read every input first: the destinations may alias the inputs; computed in double
        double dv1 = (double) uv2.y() - uv1.y(), dv2 = (double) uv3.y() - uv1.y();
        double du1 = (double) uv2.x() - uv1.x(), du2 = (double) uv3.x() - uv1.x();
        double e1x = (double) v2.x() - v1.x(), e1y = (double) v2.y() - v1.y(), e1z = (double) v2.z() - v1.z();
        double e2x = (double) v3.x() - v1.x(), e2y = (double) v3.y() - v1.y(), e2z = (double) v3.z() - v1.z();
        double f = 1.0 / (du1 * dv2 - du2 * dv1);
        double tx = f * (dv2 * e1x - dv1 * e2x), ty = f * (dv2 * e1y - dv1 * e2y), tz = f * (dv2 * e1z - dv1 * e2z);
        double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
        double bx = f * (-du2 * e1x + du1 * e2x), by = f * (-du2 * e1y + du1 * e2y), bz = f * (-du2 * e1z + du1 * e2z);
        double bl = Math.sqrt(bx * bx + by * by + bz * bz);
        destTangent.set((float) (tx / tl), (float) (ty / tl), (float) (tz / tl));
        destBitangent.set((float) (bx / bl), (float) (by / bl), (float) (bz / bl));
    }


    /** A power of two bringing the largest component of (x, y, z) into [1, 2); 1 for zero or non-finite input. */
    private static double pow2Scale(double x, double y, double z) {
        double m = java.lang.Math.max(java.lang.Math.abs(x), java.lang.Math.max(java.lang.Math.abs(y), java.lang.Math.abs(z)));
        return m > 0 && m < Double.POSITIVE_INFINITY ? java.lang.Math.scalb(1.0, -java.lang.Math.getExponent(m)) : 1.0;
    }

}
