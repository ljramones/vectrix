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

import org.dynamisengine.vectrix.core.Vector3d;

/**
 * Principal moments of inertia of solid primitives of uniform density (double precision), about their center of
 * mass and along the shape's own axes. Each method writes {@code (Ixx, Iyy, Izz)} to a vector, in mass times length
 * squared. See {@link MassPropertiesd} for full tensors and compound bodies.
 *
 * @since 1.10.12
 */
public final class InertiaTensord {
    private InertiaTensord() {
    }

    /**
     * Writes the principal moments of a solid sphere: {@code 2/5 m r²} about every axis.
     *
     * @param mass the mass {@code m}
     * @param radius the radius {@code r}
     * @param dest receives {@code (Ixx, Iyy, Izz)}
     * @return {@code dest}
     */
    public static Vector3d sphere(double mass, double radius, Vector3d dest) {
        double inertia = 0.4 * mass * radius * radius;
        return dest.set(inertia, inertia, inertia);
    }

    /**
     * Writes the principal moments of a solid box: {@code Ixx = m (hy² + hz²) / 3}, {@code Iyy = m (hx² + hz²) / 3}
     * and {@code Izz = m (hx² + hy²) / 3}.
     *
     * @param mass the mass {@code m}
     * @param hx the half extent along X
     * @param hy the half extent along Y
     * @param hz the half extent along Z
     * @param dest receives {@code (Ixx, Iyy, Izz)}
     * @return {@code dest}
     */
    public static Vector3d box(double mass, double hx, double hy, double hz, Vector3d dest) {
        double factor = mass / 3.0;
        return dest.set(
                factor * (hy * hy + hz * hz),
                factor * (hx * hx + hz * hz),
                factor * (hx * hx + hy * hy));
    }

    /**
     * Writes the principal moments of a solid capsule aligned along the +Y/-Y axis. The mass is split between the
     * cylinder and the two hemispherical caps in proportion to their volumes.
     *
     * @param mass the total mass
     * @param radius the radius of the cylinder and the caps
     * @param height length of the cylindrical section only (excluding the two hemispherical caps)
     * @param dest receives {@code (Ixx, Iyy, Izz)}, with {@code Ixx = Izz}
     * @return {@code dest}
     */
    public static Vector3d capsule(double mass, double radius, double height, Vector3d dest) {
        double r2 = radius * radius;
        double h2 = height * height;
        double mCylinder = mass * (height / (height + 4.0 / 3.0 * radius));
        double mCaps = mass - mCylinder;
        double iy = 0.5 * mCylinder * r2 + (2.0 / 5.0) * mCaps * r2;
        double ixz = (mCylinder / 12.0) * (3.0 * r2 + h2)
                // Both caps: hemisphere inertia about its own centroid ((83/320) m r^2) moved by the
                // parallel-axis theorem to the capsule centre (distance h/2 + 3r/8) gives
                // m (2r^2/5 + h^2/4 + 3hr/8).
                + mCaps * ((2.0 / 5.0) * r2 + h2 / 4.0 + (3.0 / 8.0) * height * radius);
        return dest.set(ixz, iy, ixz);
    }

    /**
     * Writes the principal moments of a solid cylinder aligned along the +Y/-Y axis: {@code Iyy = m r² / 2} and
     * {@code Ixx = Izz = m (3 r² + h²) / 12}.
     *
     * @param mass the mass {@code m}
     * @param radius the radius {@code r}
     * @param height the full height {@code h}
     * @param dest receives {@code (Ixx, Iyy, Izz)}
     * @return {@code dest}
     */
    public static Vector3d cylinder(double mass, double radius, double height, Vector3d dest) {
        double r2 = radius * radius;
        double h2 = height * height;
        double iy = 0.5 * mass * r2;
        double ixz = mass * (3.0 * r2 + h2) / 12.0;
        return dest.set(ixz, iy, ixz);
    }

    /**
     * Writes the principal moments of a solid cone aligned along the +Y/-Y axis with apex at +Y, about its center of
     * mass, which lies {@code height/4} above the base: {@code Iyy = 3/10 m r²} and
     * {@code Ixx = Izz = m (3 r² / 20 + 3 h² / 80)}.
     *
     * @param mass the mass {@code m}
     * @param radius the base radius {@code r}
     * @param height the height {@code h}
     * @param dest receives {@code (Ixx, Iyy, Izz)}
     * @return {@code dest}
     */
    public static Vector3d cone(double mass, double radius, double height, Vector3d dest) {
        double r2 = radius * radius;
        double h2 = height * height;
        double iy = (3.0 / 10.0) * mass * r2;
        double ixz = mass * (3.0 * r2 / 20.0 + 3.0 * h2 / 80.0);
        return dest.set(ixz, iy, ixz);
    }

    /**
     * Principal axes of a full inertia tensor (symmetric 3×3): the principal moments in descending order into
     * {@code moments} and the matching axes into the columns of {@code axes} (a proper rotation), so that
     * {@code inertia = axes · diag(moments) · axesᵀ}. Uses {@link org.dynamisengine.vectrix.linalg.Eigen3d}.
     *
     * @param inertia the inertia tensor; only its symmetric part is used
     * @param moments receives the principal moments, in descending order
     * @param axes receives the principal axes as columns; may be {@code inertia}
     * @return {@code moments}
     */
    public static Vector3d principalAxes(org.dynamisengine.vectrix.core.Matrix3dc inertia, Vector3d moments,
            org.dynamisengine.vectrix.core.Matrix3d axes) {
        return org.dynamisengine.vectrix.linalg.Eigen3d.symmetric(inertia, moments, axes);
    }
}
