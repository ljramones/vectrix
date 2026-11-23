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
package org.dynamisengine.vectrix.sdf;

import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Exact signed distance functions of basic 3D shapes (single precision), centered at the origin unless they take end
 * points: each returns the Euclidean distance from {@code p} to the shape's surface, negative inside, zero on the
 * surface and positive outside. Transform {@code p} into the shape's local frame first to place or rotate a shape.
 * <p>
 * The methods are stateless, do not allocate and are safe to call from any thread. Lengths are in the units of
 * {@code p}.
 *
 * @since 1.0.0
 */

public final class Sdf3f {
    private Sdf3f() {
    }

    /**
     * Signed distance to a sphere centered at the origin.
     *
     * @param p the query point
     * @param radius the sphere's radius (not squared)
     * @return the signed distance, negative inside
     */
    public static float sphere(Vector3fc p, float radius) {
        return p.length() - radius;
    }

    /**
     * Signed distance to an axis-aligned box centered at the origin.
     *
     * @param p the query point
     * @param halfExtents the box's half size along x, y and z, each non-negative
     * @return the signed distance, negative inside
     */
    public static float box(Vector3fc p, Vector3fc halfExtents) {
        float qx = java.lang.Math.abs(p.x()) - halfExtents.x();
        float qy = java.lang.Math.abs(p.y()) - halfExtents.y();
        float qz = java.lang.Math.abs(p.z()) - halfExtents.z();
        float ox = java.lang.Math.max(qx, 0.0f);
        float oy = java.lang.Math.max(qy, 0.0f);
        float oz = java.lang.Math.max(qz, 0.0f);
        float outside = (float) java.lang.Math.sqrt(ox * ox + oy * oy + oz * oz);
        float inside = java.lang.Math.min(java.lang.Math.max(qx, java.lang.Math.max(qy, qz)), 0.0f);
        return outside + inside;
    }

    /**
     * Signed distance to a capsule: all points within {@code radius} of the segment from {@code a} to {@code b}. If
     * {@code a == b} the capsule is a sphere around {@code a}.
     *
     * @param p the query point
     * @param a one end of the capsule's axis segment
     * @param b the other end of the capsule's axis segment
     * @param radius the capsule's radius (not squared)
     * @return the signed distance, negative inside
     */
    public static float capsule(Vector3fc p, Vector3fc a, Vector3fc b, float radius) {
        float pax = p.x() - a.x(), pay = p.y() - a.y(), paz = p.z() - a.z();
        float bax = b.x() - a.x(), bay = b.y() - a.y(), baz = b.z() - a.z();
        float len2 = bax * bax + bay * bay + baz * baz;
        // a == b is a sphere; the projection was 0/0 = NaN, which min/max pass through.
        float h = len2 > 0.0f ? java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, (pax * bax + pay * bay + paz * baz) / len2)) : 0.0f;
        float dx = pax - bax * h, dy = pay - bay * h, dz = paz - baz * h;
        return (float) java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz) - radius;
    }

    /**
     * Signed distance to a capped cylinder centered at the origin with its axis along y.
     *
     * @param p the query point
     * @param radius the cylinder's radius (not squared)
     * @param halfHeight half the cylinder's height along y
     * @return the signed distance, negative inside
     */
    public static float cylinderY(Vector3fc p, float radius, float halfHeight) {
        float dxz = (float) java.lang.Math.sqrt(p.x() * p.x() + p.z() * p.z()) - radius;
        float dy = java.lang.Math.abs(p.y()) - halfHeight;
        float ox = java.lang.Math.max(dxz, 0.0f);
        float oy = java.lang.Math.max(dy, 0.0f);
        return (float) java.lang.Math.sqrt(ox * ox + oy * oy) + java.lang.Math.min(java.lang.Math.max(dxz, dy), 0.0f);
    }

    /**
     * Signed distance to a torus centered at the origin, lying in the xz plane (its axis is y).
     *
     * @param p the query point
     * @param majorRadius the distance from the origin to the center of the tube
     * @param minorRadius the radius of the tube
     * @return the signed distance, negative inside the tube
     */
    public static float torusY(Vector3fc p, float majorRadius, float minorRadius) {
        float qx = (float) java.lang.Math.sqrt(p.x() * p.x() + p.z() * p.z()) - majorRadius;
        return (float) java.lang.Math.sqrt(qx * qx + p.y() * p.y()) - minorRadius;
    }
}
