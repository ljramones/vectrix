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
 * box and to axis-aligned box, segment to axis-aligned box, segment and triangle to triangle, and ray against capsule.
 * Shapes are passed as plain parameters (vectrix has no shape types; DynamisCollision owns them). The functions are
 * stateless and safe to call from any thread. Only {@link #distanceSquaredTriangleTriangle} allocates.
 *
 * @since 2.2.0
 */
public final class ClosestPointsf {
    private ClosestPointsf() {
    }

    /**
     * Returns the point of an oriented box closest to {@code p}: {@code p} itself if it is inside the box.
     *
     * @param p the query point
     * @param center the box center
     * @param axisX the box's first axis, unit length
     * @param axisY the box's second axis, unit length and orthogonal to the other two
     * @param axisZ the box's third axis, unit length and orthogonal to the other two
     * @param halfExtents the non-negative half extents along {@code axisX}, {@code axisY} and {@code axisZ}
     * @param dest receives the closest point; may alias an input
     * @return {@code dest}
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
     * Returns the squared distance between triangles {@code (a0, a1, a2)} and {@code (b0, b1, b2)} and writes the
     * closest points. If the triangles intersect, the result is 0 and both points are one common point. It is the
     * minimum over the six edge-against-triangle distances of {@link
     * Intersectionf#findClosestPointsLineSegmentTriangle}, which detects edges that pierce the other triangle.
     * <p>
     * This method allocates two temporary vectors and two small arrays per call, so avoid it in hot paths.
     *
     * @param a0 the first vertex of triangle A
     * @param a1 the second vertex of triangle A
     * @param a2 the third vertex of triangle A
     * @param b0 the first vertex of triangle B
     * @param b1 the second vertex of triangle B
     * @param b2 the third vertex of triangle B
     * @param pointA receives the closest point on triangle A; must not alias any vertex
     * @param pointB receives the closest point on triangle B; must not alias any vertex
     * @return the squared distance, 0 if the triangles intersect
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
     * Returns the squared distance between segment {@code (p, q)} and triangle {@code (v0, v1, v2)} and writes the
     * closest points. The result is 0 when the segment touches or pierces the triangle. Delegates to {@link
     * Intersectionf#findClosestPointsLineSegmentTriangle}.
     *
     * @param p the segment's first end point
     * @param q the segment's second end point
     * @param v0 the triangle's first vertex
     * @param v1 the triangle's second vertex
     * @param v2 the triangle's third vertex
     * @param segmentPoint receives the closest point on the segment; may alias an input
     * @param trianglePoint receives the closest point on the triangle; may alias an input
     * @return the squared distance
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
     * Returns the distance along the ray {@code origin + t dir} to its first intersection with the capsule around
     * segment {@code (a, b)} (Quilez): the smallest {@code t >= 0}, 0 if the origin is inside or on the capsule, or -1
     * if the ray misses. A zero-length segment gives a sphere.
     *
     * @param origin the ray origin
     * @param dir the ray direction, unit length
     * @param a one end of the capsule's segment
     * @param b the other end of the capsule's segment
     * @param radius the capsule radius (not squared)
     * @return the distance {@code t} to the first intersection, 0 if the origin is inside, or -1 on a miss
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

    /**
     * Returns the point of box {@code [min, max]} closest to {@code p}, that is, {@code p} clamped to the box.
     *
     * @param p the query point
     * @param min the box's minimum corner
     * @param max the box's maximum corner, at least {@code min} on every axis
     * @param dest receives the closest point; may alias an input
     * @return {@code dest}
     */
    public static Vector3f closestPointAab(Vector3fc p, Vector3fc min, Vector3fc max, Vector3f dest) {
        float px = p.x(), py = p.y(), pz = p.z();
        return dest.set(Math.min(Math.max(px, min.x()), max.x()), Math.min(Math.max(py, min.y()), max.y()),
                Math.min(Math.max(pz, min.z()), max.z()));
    }

    /**
     * Returns the squared distance from {@code p} to box {@code [min, max]}, computed in double.
     *
     * @param p the query point
     * @param min the box's minimum corner
     * @param max the box's maximum corner, at least {@code min} on every axis
     * @return the squared distance, {@code 0} if {@code p} is inside or on the box
     */
    public static float distanceSquaredPointAab(Vector3fc p, Vector3fc min, Vector3fc max) {
        double dx = axisGap(p.x(), min.x(), max.x()), dy = axisGap(p.y(), min.y(), max.y()), dz = axisGap(p.z(), min.z(), max.z());
        return (float) (dx * dx + dy * dy + dz * dz);
    }

    private static double axisGap(double x, double lo, double hi) {
        return x < lo ? lo - x : x > hi ? x - hi : 0;
    }

    /**
     * Finds the closest points between segment {@code a}–{@code b} and box {@code [min, max]}, exactly up to rounding.
     * <p>
     * The squared distance along the segment is convex and piecewise quadratic, so its minimum lies at an endpoint, at
     * one of the at most six points where the segment crosses a slab plane, or at the minimizer of one of the quadratic
     * pieces. Every candidate is evaluated, in double, with no iteration and no allocation. When the segment meets the
     * box the distance is 0, and the points are one common point. A zero-length segment is a point.
     *
     * @param a the segment's first end point
     * @param b the segment's second end point
     * @param min the box's minimum corner
     * @param max the box's maximum corner, at least {@code min} on every axis
     * @param onSegment receives the closest point on the segment; may alias an input
     * @param onBox receives the closest point on the box; may alias an input
     * @return the squared distance, 0 if the segment meets the box
     */
    public static float segmentAab(Vector3fc a, Vector3fc b, Vector3fc min, Vector3fc max, Vector3f onSegment,
            Vector3f onBox) {
        return segmentAab(a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), min.x(), min.y(), min.z(), max.x(), max.y(), max.z(),
                onSegment, onBox);
    }

    /**
     * Scalar form of {@link #segmentAab(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3f, Vector3f)}.
     *
     * @param ax the x coordinate of the segment's first end point
     * @param ay the y coordinate of the segment's first end point
     * @param az the z coordinate of the segment's first end point
     * @param bx the x coordinate of the segment's second end point
     * @param by the y coordinate of the segment's second end point
     * @param bz the z coordinate of the segment's second end point
     * @param minX the x coordinate of the box's minimum corner
     * @param minY the y coordinate of the box's minimum corner
     * @param minZ the z coordinate of the box's minimum corner
     * @param maxX the x coordinate of the box's maximum corner
     * @param maxY the y coordinate of the box's maximum corner
     * @param maxZ the z coordinate of the box's maximum corner
     * @param onSegment receives the closest point on the segment
     * @param onBox receives the closest point on the box
     * @return the squared distance, 0 if the segment meets the box
     */
    public static float segmentAab(float ax, float ay, float az, float bx, float by, float bz, float minX, float minY,
            float minZ, float maxX, float maxY, float maxZ, Vector3f onSegment, Vector3f onBox) {
        double dx = (double) bx - ax, dy = (double) by - ay, dz = (double) bz - az;
        double bestS = 0, best = segBox2(0, ax, ay, az, dx, dy, dz, minX, minY, minZ, maxX, maxY, maxZ);
        double f = segBox2(1, ax, ay, az, dx, dy, dz, minX, minY, minZ, maxX, maxY, maxZ);
        if (f < best) {
            best = f;
            bestS = 1;
        }
        // slab crossings
        for (int i = 0; i < 6; i++) {
            double d = i < 2 ? dx : i < 4 ? dy : dz;
            if (d == 0) {
                continue;
            }
            double o = i < 2 ? ax : i < 4 ? ay : az;
            double bound = i == 0 ? minX : i == 1 ? maxX : i == 2 ? minY : i == 3 ? maxY : i == 4 ? minZ : maxZ;
            double s = (bound - o) / d;
            if (s > 0 && s < 1) {
                f = segBox2(s, ax, ay, az, dx, dy, dz, minX, minY, minZ, maxX, maxY, maxZ);
                if (f < best) {
                    best = f;
                    bestS = s;
                }
            }
        }
        // minimizers of each quadratic piece: every axis below (0), inside (1) or above (2) its slab
        for (int combo = 0; combo < 27; combo++) {
            double num = 0, den = 0;
            for (int i = 0, c = combo; i < 3; i++, c /= 3) {
                int state = c % 3;
                if (state == 1) {
                    continue;
                }
                double d = i == 0 ? dx : i == 1 ? dy : dz;
                double o = i == 0 ? ax : i == 1 ? ay : az;
                double bound = state == 0 ? (i == 0 ? minX : i == 1 ? minY : minZ) : (i == 0 ? maxX : i == 1 ? maxY : maxZ);
                num += d * (o - bound);
                den += d * d;
            }
            if (den > 0) {
                double s = -num / den;
                if (s > 0 && s < 1) {
                    f = segBox2(s, ax, ay, az, dx, dy, dz, minX, minY, minZ, maxX, maxY, maxZ);
                    if (f < best) {
                        best = f;
                        bestS = s;
                    }
                }
            }
        }
        double px = ax + bestS * dx, py = ay + bestS * dy, pz = az + bestS * dz;
        onSegment.set((float) px, (float) py, (float) pz);
        onBox.set((float) Math.min(Math.max(px, minX), maxX), (float) Math.min(Math.max(py, minY), maxY),
                (float) Math.min(Math.max(pz, minZ), maxZ));
        return (float) best;
    }

    private static double segBox2(double s, double ax, double ay, double az, double dx, double dy, double dz, double minX,
            double minY, double minZ, double maxX, double maxY, double maxZ) {
        double gx = axisGap(ax + s * dx, minX, maxX), gy = axisGap(ay + s * dy, minY, maxY), gz = axisGap(az + s * dz, minZ, maxZ);
        return gx * gx + gy * gy + gz * gz;
    }

}
