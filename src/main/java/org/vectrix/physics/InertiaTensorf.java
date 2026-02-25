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
package org.vectrix.physics;

import org.vectrix.core.Vector3f;

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
     */
    public static Vector3f capsule(float mass, float radius, float height, Vector3f dest) {
        float r2 = radius * radius;
        float h2 = height * height;
        float mCylinder = mass * (height / (height + 4.0f / 3.0f * radius));
        float mCaps = mass - mCylinder;
        float iy = 0.5f * mCylinder * r2 + (2.0f / 5.0f) * mCaps * r2;
        float ixz = (mCylinder / 12.0f) * (3.0f * r2 + h2)
                + mCaps * ((2.0f / 5.0f) * r2 + h2 / 2.0f + (3.0f / 8.0f) * height * radius);
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
}
