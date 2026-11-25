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

import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Closest-point and distance queries that {@link Intersectiond} does not cover (double precision): point to oriented
 * box and to axis-aligned box, segment to axis-aligned box, segment and triangle to triangle, and ray against capsule.
 * Shapes are passed as plain parameters (vectrix has no shape types; DynamisCollision owns them). The functions are
 * stateless and safe to call from any thread, and do not allocate ({@link #distanceSquaredTriangleTriangle} keeps
 * two per-thread scratch vectors, created on its first call in each thread).
 *
 * @since 2.2.0
 */
public final class ClosestPointsd {
    private ClosestPointsd() {
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
    public static Vector3d closestPointObb(Vector3dc p, Vector3dc center, Vector3dc axisX, Vector3dc axisY,
            Vector3dc axisZ, Vector3dc halfExtents, Vector3d dest) {
        double dx = p.x() - center.x(), dy = p.y() - center.y(), dz = p.z() - center.z();
        double u = clamp(dx * axisX.x() + dy * axisX.y() + dz * axisX.z(), halfExtents.x());
        double v = clamp(dx * axisY.x() + dy * axisY.y() + dz * axisY.z(), halfExtents.y());
        double w = clamp(dx * axisZ.x() + dy * axisZ.y() + dz * axisZ.z(), halfExtents.z());
        return dest.set(center.x() + u * axisX.x() + v * axisY.x() + w * axisZ.x(),
                center.y() + u * axisX.y() + v * axisY.y() + w * axisZ.y(),
                center.z() + u * axisX.z() + v * axisY.z() + w * axisZ.z());
    }

    private static double clamp(double x, double h) {
        return java.lang.Math.max(-h, java.lang.Math.min(h, x));
    }

    /**
     * Returns the squared distance between triangles {@code (a0, a1, a2)} and {@code (b0, b1, b2)} and writes the
     * closest points. If the triangles intersect, the result is 0 and both points are one common point. It is the
     * minimum over the six edge-against-triangle distances of {@link
     * Intersectiond#findClosestPointsLineSegmentTriangle}, which detects edges that pierce the other triangle.
     * <p>
     * It does not allocate after its first call in each thread (per-thread scratch).
     *
     * @param a0 the first vertex of triangle A
     * @param a1 the second vertex of triangle A
     * @param a2 the third vertex of triangle A
     * @param b0 the first vertex of triangle B
     * @param b1 the second vertex of triangle B
     * @param b2 the third vertex of triangle B
     * @param pointA receives the closest point on triangle A; may alias any vertex
     * @param pointB receives the closest point on triangle B; may alias any vertex
     * @return the squared distance, 0 if the triangles intersect
     */
    public static double distanceSquaredTriangleTriangle(Vector3dc a0, Vector3dc a1, Vector3dc a2, Vector3dc b0,
            Vector3dc b1, Vector3dc b2, Vector3d pointA, Vector3d pointB) {
        // The best pair is kept in locals and written at the end, so pointA and pointB may alias the vertices.
        Vector3d[] scratch = TRI_TRI_SCRATCH.get();
        Vector3d s = scratch[0];
        Vector3d t = scratch[1];
        double best = Double.POSITIVE_INFINITY;
        double ax = 0, ay = 0, az = 0, bx = 0, by = 0, bz = 0;
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 3; i++) {
                Vector3dc p = side == 0 ? vertex(i, a0, a1, a2) : vertex(i, b0, b1, b2);
                Vector3dc q = side == 0 ? vertex(i + 1, a0, a1, a2) : vertex(i + 1, b0, b1, b2);
                double d = side == 0 ? segmentTriangle(p, q, b0, b1, b2, s, t) : segmentTriangle(p, q, a0, a1, a2, s, t);
                if (d < best) {
                    best = d;
                    Vector3d onA = side == 0 ? s : t;
                    Vector3d onB = side == 0 ? t : s;
                    ax = onA.x;
                    ay = onA.y;
                    az = onA.z;
                    bx = onB.x;
                    by = onB.y;
                    bz = onB.z;
                }
            }
        }
        pointA.set(ax, ay, az);
        pointB.set(bx, by, bz);
        return best;
    }

    private static final ThreadLocal<Vector3d[]> TRI_TRI_SCRATCH =
        ThreadLocal.withInitial(() -> new Vector3d[] {new Vector3d(), new Vector3d()});

    private static Vector3dc vertex(int i, Vector3dc v0, Vector3dc v1, Vector3dc v2) {
        int k = i % 3;
        return k == 0 ? v0 : k == 1 ? v1 : v2;
    }

    /**
     * Returns the squared distance between segment {@code (p, q)} and triangle {@code (v0, v1, v2)} and writes the
     * closest points. The result is 0 when the segment touches or pierces the triangle. Delegates to {@link
     * Intersectiond#findClosestPointsLineSegmentTriangle}.
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
    public static double distanceSquaredSegmentTriangle(Vector3dc p, Vector3dc q, Vector3dc v0, Vector3dc v1,
            Vector3dc v2, Vector3d segmentPoint, Vector3d trianglePoint) {
        return segmentTriangle(p, q, v0, v1, v2, segmentPoint, trianglePoint);
    }

    private static double segmentTriangle(Vector3dc p, Vector3dc q, Vector3dc v0, Vector3dc v1, Vector3dc v2,
            Vector3d sp, Vector3d tp) {
        return Intersectiond.findClosestPointsLineSegmentTriangle(p.x(), p.y(), p.z(), q.x(), q.y(), q.z(), v0.x(),
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
    public static double intersectRayCapsule(Vector3dc origin, Vector3dc dir, Vector3dc a, Vector3dc b, double radius) {
        double bax = b.x() - a.x(), bay = b.y() - a.y(), baz = b.z() - a.z();
        double oax = origin.x() - a.x(), oay = origin.y() - a.y(), oaz = origin.z() - a.z();
        double baba = bax * bax + bay * bay + baz * baz;
        double bard = bax * dir.x() + bay * dir.y() + baz * dir.z();
        double baoa = bax * oax + bay * oay + baz * oaz;
        double rdoa = dir.x() * oax + dir.y() * oay + dir.z() * oaz;
        double oaoa = oax * oax + oay * oay + oaz * oaz;
        double r2 = radius * radius;
        // origin inside: distance to the segment below the radius
        double h0 = baba > 0.0 ? java.lang.Math.max(0.0, java.lang.Math.min(1.0, baoa / baba)) : 0.0;
        double ix = oax - h0 * bax, iy = oay - h0 * bay, iz = oaz - h0 * baz;
        if (ix * ix + iy * iy + iz * iz <= r2) {
            return 0.0;
        }
        double ka = baba - bard * bard;
        double kb = baba * rdoa - baoa * bard;
        double kc = baba * oaoa - baoa * baoa - r2 * baba;
        double h = kb * kb - ka * kc;
        if (h >= 0.0 && ka > 0.0) {
            double t = (-kb - java.lang.Math.sqrt(h)) / ka;
            double y = baoa + t * bard;
            if (y > 0.0 && y < baba && t >= 0.0) {
                return t;
            }
        }
        // the caps: spheres at a and b
        double best = -1.0;
        for (int k = 0; k < 2; k++) {
            double cx = k == 0 ? oax : origin.x() - b.x();
            double cy = k == 0 ? oay : origin.y() - b.y();
            double cz = k == 0 ? oaz : origin.z() - b.z();
            double bb = dir.x() * cx + dir.y() * cy + dir.z() * cz;
            double cc = cx * cx + cy * cy + cz * cz - r2;
            double hh = bb * bb - cc;
            if (hh >= 0.0) {
                double t = -bb - java.lang.Math.sqrt(hh);
                if (t >= 0.0 && (best < 0.0 || t < best)) {
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
    public static Vector3d closestPointAab(Vector3dc p, Vector3dc min, Vector3dc max, Vector3d dest) {
        double px = p.x(), py = p.y(), pz = p.z();
        return dest.set(Math.min(Math.max(px, min.x()), max.x()), Math.min(Math.max(py, min.y()), max.y()),
                Math.min(Math.max(pz, min.z()), max.z()));
    }

    /**
     * Returns the squared distance from {@code p} to box {@code [min, max]}.
     *
     * @param p the query point
     * @param min the box's minimum corner
     * @param max the box's maximum corner, at least {@code min} on every axis
     * @return the squared distance, {@code 0} if {@code p} is inside or on the box
     */
    public static double distanceSquaredPointAab(Vector3dc p, Vector3dc min, Vector3dc max) {
        double dx = axisGap(p.x(), min.x(), max.x()), dy = axisGap(p.y(), min.y(), max.y()), dz = axisGap(p.z(), min.z(), max.z());
        return (dx * dx + dy * dy + dz * dz);
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
    public static double segmentAab(Vector3dc a, Vector3dc b, Vector3dc min, Vector3dc max, Vector3d onSegment,
            Vector3d onBox) {
        return segmentAab(a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), min.x(), min.y(), min.z(), max.x(), max.y(), max.z(),
                onSegment, onBox);
    }

    /**
     * Scalar form of {@link #segmentAab(Vector3dc, Vector3dc, Vector3dc, Vector3dc, Vector3d, Vector3d)}.
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
    public static double segmentAab(double ax, double ay, double az, double bx, double by, double bz, double minX, double minY,
            double minZ, double maxX, double maxY, double maxZ, Vector3d onSegment, Vector3d onBox) {
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
        onSegment.set(px, py, pz);
        onBox.set(Math.min(Math.max(px, minX), maxX), Math.min(Math.max(py, minY), maxY),
                Math.min(Math.max(pz, minZ), maxZ));
        return best;
    }

    private static double segBox2(double s, double ax, double ay, double az, double dx, double dy, double dz, double minX,
            double minY, double minZ, double maxX, double maxY, double maxZ) {
        double gx = axisGap(ax + s * dx, minX, maxX), gy = axisGap(ay + s * dy, minY, maxY), gz = axisGap(az + s * dz, minZ, maxZ);
        return gx * gx + gy * gy + gz * gz;
    }

}
