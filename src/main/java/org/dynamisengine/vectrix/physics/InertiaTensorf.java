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

import org.dynamisengine.vectrix.core.Vector3f;

/**
 * Helpers to compute diagonal inertia tensor components for common solid primitives.
 *
 * @since 1.10.12
 */
public final class InertiaTensorf {
    private InertiaTensorf() {
    }

    public static Vector3f sphere(float mass, float radius, Vector3f dest) {
        float inertia = 0.4f * mass * radius * radius;
        return dest.set(inertia, inertia, inertia);
    }

    public static Vector3f box(float mass, float hx, float hy, float hz, Vector3f dest) {
        float factor = mass / 3.0f;
        return dest.set(
                factor * (hy * hy + hz * hz),
                factor * (hx * hx + hz * hz),
                factor * (hx * hx + hy * hy));
    }

    /**
     * Solid capsule aligned along the +Y/-Y axis.
     *
     * @param height length of the cylindrical section only (excluding the two hemispherical caps)
     */
    public static Vector3f capsule(float mass, float radius, float height, Vector3f dest) {
        float r2 = radius * radius;
        float h2 = height * height;
        float mCylinder = mass * (height / (height + 4.0f / 3.0f * radius));
        float mCaps = mass - mCylinder;
        float iy = 0.5f * mCylinder * r2 + (2.0f / 5.0f) * mCaps * r2;
        float ixz = (mCylinder / 12.0f) * (3.0f * r2 + h2)
                // Both caps: hemisphere inertia about its own centroid ((83/320) m r^2) moved by the
                // parallel-axis theorem to the capsule centre (distance h/2 + 3r/8) gives
                // m (2r^2/5 + h^2/4 + 3hr/8).
                + mCaps * ((2.0f / 5.0f) * r2 + h2 / 4.0f + (3.0f / 8.0f) * height * radius);
        return dest.set(ixz, iy, ixz);
    }

    /**
     * Solid cylinder aligned along the +Y/-Y axis.
     */
    public static Vector3f cylinder(float mass, float radius, float height, Vector3f dest) {
        float r2 = radius * radius;
        float h2 = height * height;
        float iy = 0.5f * mass * r2;
        float ixz = mass * (3.0f * r2 + h2) / 12.0f;
        return dest.set(ixz, iy, ixz);
    }

    /**
     * Solid cone aligned along the +Y/-Y axis with apex at +Y.
     */
    public static Vector3f cone(float mass, float radius, float height, Vector3f dest) {
        float r2 = radius * radius;
        float h2 = height * height;
        float iy = (3.0f / 10.0f) * mass * r2;
        float ixz = mass * (3.0f * r2 / 20.0f + 3.0f * h2 / 80.0f);
        return dest.set(ixz, iy, ixz);
    }

    /**
     * Principal axes of a full inertia tensor (symmetric 3×3): the principal moments in descending order into
     * {@code moments} and the matching axes into the columns of {@code axes} (a proper rotation), so that
     * {@code inertia = axes · diag(moments) · axesᵀ}. Uses {@link org.dynamisengine.vectrix.linalg.Eigen3f}.
     *
     * @return {@code moments}
     */
    public static Vector3f principalAxes(org.dynamisengine.vectrix.core.Matrix3fc inertia, Vector3f moments,
            org.dynamisengine.vectrix.core.Matrix3f axes) {
        return org.dynamisengine.vectrix.linalg.Eigen3f.symmetric(inertia, moments, axes);
    }
}
