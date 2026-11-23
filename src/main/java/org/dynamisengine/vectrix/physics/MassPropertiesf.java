/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix Contributors
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
package org.dynamisengine.vectrix.physics;

import org.dynamisengine.vectrix.core.Matrix3f;
import org.dynamisengine.vectrix.core.Matrix3fc;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Mass properties (single precision): primitive volumes, inertia tensors moved and rotated, bodies combined, and the
 * exact mass properties of a closed triangle mesh. Tensors are full symmetric 3×3 matrices about the center of mass,
 * in the frame the caller names. See {@link InertiaTensorf} for the principal moments of primitives.
 *
 * @since 2.4.0
 */
public final class MassPropertiesf {
    private MassPropertiesf() {
    }

    /**
     * Volume {@code 4/3 π r³} of a sphere.
     *
     * @param radius the radius {@code r}
     * @return the volume
     */
    public static float sphereVolume(float radius) {
        return (float) (4.0 / 3.0 * Math.PI * radius * radius * radius);
    }

    /**
     * Volume {@code 8 hx hy hz} of a box with half extents {@code hx, hy, hz}.
     *
     * @param hx the half extent along X
     * @param hy the half extent along Y
     * @param hz the half extent along Z
     * @return the volume
     */
    public static float boxVolume(float hx, float hy, float hz) {
        return 8 * hx * hy * hz;
    }

    /**
     * Volume {@code π r² h + 4/3 π r³} of a capsule whose cylindrical section has length {@code height}.
     *
     * @param radius the radius {@code r}
     * @param height the length {@code h} of the cylindrical section, without the caps
     * @return the volume
     */
    public static float capsuleVolume(float radius, float height) {
        double r = radius;
        return (float) (Math.PI * r * r * (height + 4.0 / 3.0 * r));
    }

    /**
     * Volume {@code π r² h} of a cylinder.
     *
     * @param radius the radius {@code r}
     * @param height the height {@code h}
     * @return the volume
     */
    public static float cylinderVolume(float radius, float height) {
        return (float) (Math.PI * radius * radius * height);
    }

    /**
     * Volume {@code π r² h / 3} of a cone.
     *
     * @param radius the base radius {@code r}
     * @param height the height {@code h}
     * @return the volume
     */
    public static float coneVolume(float radius, float height) {
        return (float) (Math.PI * radius * radius * height / 3.0);
    }

    /**
     * Parallel-axis theorem: the inertia about a point at {@code offset} from the center of mass, which is
     * {@code I + m (|d|² E − d dᵀ)} with {@code d = offset} and {@code E} the identity. Computed in double.
     * {@code dest} may be {@code inertiaAboutCom}.
     *
     * @param inertiaAboutCom the inertia tensor about the center of mass
     * @param mass the mass {@code m}
     * @param offset the point relative to the center of mass; only its length and direction matter, not its sign
     * @param dest receives the inertia about the point
     * @return {@code dest}
     */
    public static Matrix3f parallelAxis(Matrix3fc inertiaAboutCom, float mass, Vector3fc offset, Matrix3f dest) {
        double dx = offset.x(), dy = offset.y(), dz = offset.z(), m = mass;
        double xx = m * (dy * dy + dz * dz), yy = m * (dx * dx + dz * dz), zz = m * (dx * dx + dy * dy);
        double xy = -m * dx * dy, xz = -m * dx * dz, yz = -m * dy * dz;
        return dest.set((float) (inertiaAboutCom.m00() + xx), (float) (inertiaAboutCom.m01() + xy),
                (float) (inertiaAboutCom.m02() + xz), (float) (inertiaAboutCom.m10() + xy),
                (float) (inertiaAboutCom.m11() + yy), (float) (inertiaAboutCom.m12() + yz),
                (float) (inertiaAboutCom.m20() + xz), (float) (inertiaAboutCom.m21() + yz),
                (float) (inertiaAboutCom.m22() + zz));
    }

    /**
     * The tensor {@code R I Rᵀ} of a body rotated by {@code rotation}. Computed in double; the off-diagonal pairs are
     * averaged, so the result is exactly symmetric. {@code dest} may be either input.
     *
     * @param inertia the tensor {@code I} in the body's frame
     * @param rotation the rotation {@code R} from the body's frame to the target frame (orthonormal; not checked)
     * @param dest receives the rotated tensor
     * @return {@code dest}
     */
    public static Matrix3f rotate(Matrix3fc inertia, Matrix3fc rotation, Matrix3f dest) {
        // rows of R (Matrix3f stores mColumnRow)
        double r00 = rotation.m00(), r01 = rotation.m10(), r02 = rotation.m20();
        double r10 = rotation.m01(), r11 = rotation.m11(), r12 = rotation.m21();
        double r20 = rotation.m02(), r21 = rotation.m12(), r22 = rotation.m22();
        double a00 = inertia.m00(), a01 = inertia.m10(), a02 = inertia.m20();
        double a10 = inertia.m01(), a11 = inertia.m11(), a12 = inertia.m21();
        double a20 = inertia.m02(), a21 = inertia.m12(), a22 = inertia.m22();
        // T = R A
        double t00 = r00 * a00 + r01 * a10 + r02 * a20, t01 = r00 * a01 + r01 * a11 + r02 * a21;
        double t02 = r00 * a02 + r01 * a12 + r02 * a22;
        double t10 = r10 * a00 + r11 * a10 + r12 * a20, t11 = r10 * a01 + r11 * a11 + r12 * a21;
        double t12 = r10 * a02 + r11 * a12 + r12 * a22;
        double t20 = r20 * a00 + r21 * a10 + r22 * a20, t21 = r20 * a01 + r21 * a11 + r22 * a21;
        double t22 = r20 * a02 + r21 * a12 + r22 * a22;
        // (T Rᵀ)_ij = T_i · R_j; the off-diagonal pairs are averaged so the result is exactly symmetric
        double o00 = t00 * r00 + t01 * r01 + t02 * r02, o11 = t10 * r10 + t11 * r11 + t12 * r12;
        double o22 = t20 * r20 + t21 * r21 + t22 * r22;
        double o01 = 0.5 * ((t00 * r10 + t01 * r11 + t02 * r12) + (t10 * r00 + t11 * r01 + t12 * r02));
        double o02 = 0.5 * ((t00 * r20 + t01 * r21 + t02 * r22) + (t20 * r00 + t21 * r01 + t22 * r02));
        double o12 = 0.5 * ((t10 * r20 + t11 * r21 + t12 * r22) + (t20 * r10 + t21 * r11 + t22 * r12));
        return dest.set((float) o00, (float) o01, (float) o02, (float) o01, (float) o11, (float) o12, (float) o02,
                (float) o12, (float) o22);
    }

    /**
     * Combines two bodies. Each is given by its mass, its center of mass and its inertia about that center, all in
     * one common frame. The result is the combined center of mass ({@code comOut}) and the inertia about it
     * ({@code inertiaOut}). Call repeatedly for compounds: the combination is exact and associative up to rounding.
     * Computed in double. The outputs may alias the inputs. A total mass of 0 gives NaN results.
     *
     * @param massA the mass of body A
     * @param comA the center of mass of body A
     * @param inertiaA the inertia of body A about {@code comA}
     * @param massB the mass of body B
     * @param comB the center of mass of body B
     * @param inertiaB the inertia of body B about {@code comB}
     * @param comOut receives the combined center of mass
     * @param inertiaOut receives the combined inertia about {@code comOut}
     * @return the combined mass
     */
    public static float combine(float massA, Vector3fc comA, Matrix3fc inertiaA, float massB, Vector3fc comB,
            Matrix3fc inertiaB, Vector3f comOut, Matrix3f inertiaOut) {
        double ma = massA, mb = massB, m = ma + mb;
        double ax = comA.x(), ay = comA.y(), az = comA.z(), bx = comB.x(), by = comB.y(), bz = comB.z();
        double cx = (ma * ax + mb * bx) / m, cy = (ma * ay + mb * by) / m, cz = (ma * az + mb * bz) / m;
        // offsets from the combined center; the shift terms of both bodies collapse to (ma mb / m) (|d|² E − d dᵀ)
        double dx = bx - ax, dy = by - ay, dz = bz - az, mu = ma * mb / m;
        double xx = mu * (dy * dy + dz * dz), yy = mu * (dx * dx + dz * dz), zz = mu * (dx * dx + dy * dy);
        double xy = -mu * dx * dy, xz = -mu * dx * dz, yz = -mu * dy * dz;
        inertiaOut.set((float) (inertiaA.m00() + (double) inertiaB.m00() + xx),
                (float) (inertiaA.m01() + (double) inertiaB.m01() + xy),
                (float) (inertiaA.m02() + (double) inertiaB.m02() + xz),
                (float) (inertiaA.m10() + (double) inertiaB.m10() + xy),
                (float) (inertiaA.m11() + (double) inertiaB.m11() + yy),
                (float) (inertiaA.m12() + (double) inertiaB.m12() + yz),
                (float) (inertiaA.m20() + (double) inertiaB.m20() + xz),
                (float) (inertiaA.m21() + (double) inertiaB.m21() + yz),
                (float) (inertiaA.m22() + (double) inertiaB.m22() + zz));
        comOut.set((float) cx, (float) cy, (float) cz);
        return (float) m;
    }

    /**
     * Exact mass properties of a closed, outward-wound (counter-clockwise seen from outside) triangle mesh of
     * uniform {@code density}, following Eberly's "Polyhedral Mass Properties (Revisited)".
     * <p>
     * The volume integrals are taken relative to the mean vertex and accumulated in double, so a mesh far from the
     * origin loses no precision to the parallel-axis subtraction. Writes the center of mass to {@code comOut} and the
     * inertia about it to {@code inertiaOut}. An inside-out mesh gives a negative mass. A mesh of zero volume gives
     * mass 0 and the mean vertex as its center of mass. The arrays start at index 0 and are not checked up front: a bad
     * index throws {@link ArrayIndexOutOfBoundsException}. Does not allocate.
     *
     * @param xyz
     *            vertex positions, packed {@code x, y, z} per vertex
     * @param vertexCount
     *            the number of vertices; their mean is the reference point of the integrals
     * @param indices
     *            three vertex indices per triangle
     * @param triangleCount
     *            the number of triangles
     * @param density
     *            the uniform density
     * @param comOut
     *            receives the center of mass
     * @param inertiaOut
     *            receives the inertia tensor about the center of mass
     * @return the mass {@code density × volume}
     */
    public static float polyhedron(float[] xyz, int vertexCount, int[] indices, int triangleCount, float density,
            Vector3f comOut, Matrix3f inertiaOut) {
        double rx = 0, ry = 0, rz = 0;
        for (int v = 0; v < vertexCount; v++) {
            rx += xyz[3 * v];
            ry += xyz[3 * v + 1];
            rz += xyz[3 * v + 2];
        }
        if (vertexCount > 0) {
            rx /= vertexCount;
            ry /= vertexCount;
            rz /= vertexCount;
        }
        double i0 = 0, i1 = 0, i2 = 0, i3 = 0, i4 = 0, i5 = 0, i6 = 0, i7 = 0, i8 = 0, i9 = 0;
        for (int t = 0; t < triangleCount; t++) {
            int a = 3 * indices[3 * t], b = 3 * indices[3 * t + 1], c = 3 * indices[3 * t + 2];
            double x0 = xyz[a] - rx, y0 = xyz[a + 1] - ry, z0 = xyz[a + 2] - rz;
            double x1 = xyz[b] - rx, y1 = xyz[b + 1] - ry, z1 = xyz[b + 2] - rz;
            double x2 = xyz[c] - rx, y2 = xyz[c + 1] - ry, z2 = xyz[c + 2] - rz;
            double a1 = x1 - x0, b1 = y1 - y0, c1 = z1 - z0, a2 = x2 - x0, b2 = y2 - y0, c2 = z2 - z0;
            double d0 = b1 * c2 - b2 * c1, d1 = a2 * c1 - a1 * c2, d2 = a1 * b2 - a2 * b1;
            // Eberly's subexpressions for each coordinate
            double t0 = x0 + x1, f1x = t0 + x2, t1 = x0 * x0, t2 = t1 + x1 * t0, f2x = t2 + x2 * f1x;
            double f3x = x0 * t1 + x1 * t2 + x2 * f2x;
            double g0x = f2x + x0 * (f1x + x0), g1x = f2x + x1 * (f1x + x1), g2x = f2x + x2 * (f1x + x2);
            t0 = y0 + y1;
            double f1y = t0 + y2;
            t1 = y0 * y0;
            t2 = t1 + y1 * t0;
            double f2y = t2 + y2 * f1y, f3y = y0 * t1 + y1 * t2 + y2 * f2y;
            double g0y = f2y + y0 * (f1y + y0), g1y = f2y + y1 * (f1y + y1), g2y = f2y + y2 * (f1y + y2);
            t0 = z0 + z1;
            double f1z = t0 + z2;
            t1 = z0 * z0;
            t2 = t1 + z1 * t0;
            double f2z = t2 + z2 * f1z, f3z = z0 * t1 + z1 * t2 + z2 * f2z;
            double g0z = f2z + z0 * (f1z + z0), g1z = f2z + z1 * (f1z + z1), g2z = f2z + z2 * (f1z + z2);
            i0 += d0 * f1x;
            i1 += d0 * f2x;
            i2 += d1 * f2y;
            i3 += d2 * f2z;
            i4 += d0 * f3x;
            i5 += d1 * f3y;
            i6 += d2 * f3z;
            i7 += d0 * (y0 * g0x + y1 * g1x + y2 * g2x);
            i8 += d1 * (z0 * g0y + z1 * g1y + z2 * g2y);
            i9 += d2 * (x0 * g0z + x1 * g1z + x2 * g2z);
        }
        double vol = i0 / 6;
        double sx = i1 / 24, sy = i2 / 24, sz = i3 / 24;
        double xx2 = i4 / 60, yy2 = i5 / 60, zz2 = i6 / 60, xy = i7 / 120, yz = i8 / 120, zx = i9 / 120;
        double cx = vol != 0 ? sx / vol : 0, cy = vol != 0 ? sy / vol : 0, cz = vol != 0 ? sz / vol : 0;
        double rho = density;
        double ixx = rho * (yy2 + zz2 - vol * (cy * cy + cz * cz));
        double iyy = rho * (zz2 + xx2 - vol * (cz * cz + cx * cx));
        double izz = rho * (xx2 + yy2 - vol * (cx * cx + cy * cy));
        double ixy = -rho * (xy - vol * cx * cy), iyz = -rho * (yz - vol * cy * cz), ixz = -rho * (zx - vol * cz * cx);
        inertiaOut.set((float) ixx, (float) ixy, (float) ixz, (float) ixy, (float) iyy, (float) iyz, (float) ixz,
                (float) iyz, (float) izz);
        comOut.set((float) (rx + cx), (float) (ry + cy), (float) (rz + cz));
        return (float) (rho * vol);
    }
}
