/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
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

import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Closest-point and distance queries that {@link Intersectionf} does not cover (single precision): point to oriented
 * box, triangle to triangle, and ray against capsule. Shapes are passed as plain parameters (vectrix has no shape
 * types; DynamisCollision owns them).
 *
 * @since 2.2.0
 */
public final class ClosestPointsf {
    private ClosestPointsf() {
    }

    /**
     * Closest point to {@code p} on (or in) the oriented box with the given center, unit axes and half extents: the
     * point itself if it is inside. {@code dest} may alias an input.
     */
    public static Vector3f closestPointObb(Vector3fc p, Vector3fc center, Vector3fc axisX, Vector3fc axisY,
            Vector3fc axisZ, Vector3fc halfExtents, Vector3f dest) {
        float dx = p.x() - center.x(), dy = p.y() - center.y(), dz = p.z() - center.z();
        float u = clamp(dx * axisX.x() + dy * axisX.y() + dz * axisX.z(), halfExtents.x());
        float v = clamp(dx * axisY.x() + dy * axisY.y() + dz * axisY.z(), halfExtents.y());
        float w = clamp(dx * axisZ.x() + dy * axisZ.y() + dz * axisZ.z(), halfExtents.z());
        return dest.set(center.x() + u * axisX.x() + v * axisY.x() + w * axisZ.x(),
                center.y() + u * axisX.y() + v * axisY.y() + w * axisZ.y(),
                center.z() + u * axisX.z() + v * axisY.z() + w * axisZ.z());
    }

    private static float clamp(float x, float h) {
        return java.lang.Math.max(-h, java.lang.Math.min(h, x));
    }

    /**
     * Squared distance between triangles {@code (a0, a1, a2)} and {@code (b0, b1, b2)}, with the closest points in
     * {@code pointA} and {@code pointB}; 0 (and a common point) if they intersect. The minimum over the six
     * edge-against-triangle distances, with piercing edges detected explicitly.
     */
    public static float distanceSquaredTriangleTriangle(Vector3fc a0, Vector3fc a1, Vector3fc a2, Vector3fc b0,
            Vector3fc b1, Vector3fc b2, Vector3f pointA, Vector3f pointB) {
        Vector3fc[] ta = {a0, a1, a2};
        Vector3fc[] tb = {b0, b1, b2};
        float best = Float.POSITIVE_INFINITY;
        Vector3f s = new Vector3f(), t = new Vector3f();
        for (int side = 0; side < 2; side++) {
            Vector3fc[] e = side == 0 ? ta : tb;
            Vector3fc[] tri = side == 0 ? tb : ta;
            for (int i = 0; i < 3; i++) {
                Vector3fc p = e[i], q = e[(i + 1) % 3];
                float d = segmentTriangle(p, q, tri[0], tri[1], tri[2], s, t);
                if (d < best) {
                    best = d;
                    if (side == 0) {
                        pointA.set(s);
                        pointB.set(t);
                    } else {
                        pointA.set(t);
                        pointB.set(s);
                    }
                }
            }
        }
        return best;
    }

    /**
     * Squared distance between segment {@code (p, q)} and triangle {@code (v0, v1, v2)}, with the closest points; 0 if
     * the segment pierces the triangle (now also detected by {@link Intersectionf#findClosestPointsLineSegmentTriangle}).
     */
    public static float distanceSquaredSegmentTriangle(Vector3fc p, Vector3fc q, Vector3fc v0, Vector3fc v1,
            Vector3fc v2, Vector3f segmentPoint, Vector3f trianglePoint) {
        return segmentTriangle(p, q, v0, v1, v2, segmentPoint, trianglePoint);
    }

    private static float segmentTriangle(Vector3fc p, Vector3fc q, Vector3fc v0, Vector3fc v1, Vector3fc v2,
            Vector3f sp, Vector3f tp) {
        return Intersectionf.findClosestPointsLineSegmentTriangle(p.x(), p.y(), p.z(), q.x(), q.y(), q.z(), v0.x(),
                v0.y(), v0.z(), v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z(), sp, tp);
    }

    /**
     * First intersection of the ray {@code origin + t dir} ({@code dir} of unit length) with the capsule around segment
     * {@code (a, b)} of the given radius (Quilez): the smallest {@code t >= 0}, 0 if the origin is inside, or -1 if the
     * ray misses.
     */
    public static float intersectRayCapsule(Vector3fc origin, Vector3fc dir, Vector3fc a, Vector3fc b, float radius) {
        float bax = b.x() - a.x(), bay = b.y() - a.y(), baz = b.z() - a.z();
        float oax = origin.x() - a.x(), oay = origin.y() - a.y(), oaz = origin.z() - a.z();
        float baba = bax * bax + bay * bay + baz * baz;
        float bard = bax * dir.x() + bay * dir.y() + baz * dir.z();
        float baoa = bax * oax + bay * oay + baz * oaz;
        float rdoa = dir.x() * oax + dir.y() * oay + dir.z() * oaz;
        float oaoa = oax * oax + oay * oay + oaz * oaz;
        float r2 = radius * radius;
        // origin inside: distance to the segment below the radius
        float h0 = baba > 0.0f ? java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, baoa / baba)) : 0.0f;
        float ix = oax - h0 * bax, iy = oay - h0 * bay, iz = oaz - h0 * baz;
        if (ix * ix + iy * iy + iz * iz <= r2) {
            return 0.0f;
        }
        float ka = baba - bard * bard;
        float kb = baba * rdoa - baoa * bard;
        float kc = baba * oaoa - baoa * baoa - r2 * baba;
        float h = kb * kb - ka * kc;
        if (h >= 0.0f && ka > 0.0f) {
            float t = (-kb - (float) java.lang.Math.sqrt(h)) / ka;
            float y = baoa + t * bard;
            if (y > 0.0f && y < baba && t >= 0.0f) {
                return t;
            }
        }
        // the caps: spheres at a and b
        float best = -1.0f;
        for (int k = 0; k < 2; k++) {
            float cx = k == 0 ? oax : origin.x() - b.x();
            float cy = k == 0 ? oay : origin.y() - b.y();
            float cz = k == 0 ? oaz : origin.z() - b.z();
            float bb = dir.x() * cx + dir.y() * cy + dir.z() * cz;
            float cc = cx * cx + cy * cy + cz * cz - r2;
            float hh = bb * bb - cc;
            if (hh >= 0.0f) {
                float t = -bb - (float) java.lang.Math.sqrt(hh);
                if (t >= 0.0f && (best < 0.0f || t < best)) {
                    best = t;
                }
            }
        }
        return best;
    }
}
