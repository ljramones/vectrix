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
 * box, triangle to triangle, and ray against capsule. Shapes are passed as plain parameters (vectrix has no shape
 * types; DynamisCollision owns them).
 *
 * @since 2.2.0
 */
public final class ClosestPointsd {
    private ClosestPointsd() {
    }

    /**
     * Closest point to {@code p} on (or in) the oriented box with the given center, unit axes and half extents: the
     * point itself if it is inside. {@code dest} may alias an input.
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
     * Squared distance between triangles {@code (a0, a1, a2)} and {@code (b0, b1, b2)}, with the closest points in
     * {@code pointA} and {@code pointB}; 0 (and a common point) if they intersect. The minimum over the six
     * edge-against-triangle distances, with piercing edges detected explicitly.
     */
    public static double distanceSquaredTriangleTriangle(Vector3dc a0, Vector3dc a1, Vector3dc a2, Vector3dc b0,
            Vector3dc b1, Vector3dc b2, Vector3d pointA, Vector3d pointB) {
        Vector3dc[] ta = {a0, a1, a2};
        Vector3dc[] tb = {b0, b1, b2};
        double best = Double.POSITIVE_INFINITY;
        Vector3d s = new Vector3d(), t = new Vector3d();
        for (int side = 0; side < 2; side++) {
            Vector3dc[] e = side == 0 ? ta : tb;
            Vector3dc[] tri = side == 0 ? tb : ta;
            for (int i = 0; i < 3; i++) {
                Vector3dc p = e[i], q = e[(i + 1) % 3];
                double d = segmentTriangle(p, q, tri[0], tri[1], tri[2], s, t);
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
     * the segment pierces the triangle (now also detected by {@link Intersectiond#findClosestPointsLineSegmentTriangle}).
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
     * First intersection of the ray {@code origin + t dir} ({@code dir} of unit length) with the capsule around segment
     * {@code (a, b)} of the given radius (Quilez): the smallest {@code t >= 0}, 0 if the origin is inside, or -1 if the
     * ray misses.
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
}
