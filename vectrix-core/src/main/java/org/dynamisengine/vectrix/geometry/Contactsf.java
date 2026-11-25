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
package org.dynamisengine.vectrix.geometry;

import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Contact kernels for pairs of primitive volumes (single precision): penetration depth, contact normal and contact
 * point. These are pure functions over coordinates. Shape types, manifolds, contact persistence and solvers belong to
 * the collision library.
 * <p>
 * Conventions shared by every function:
 * <ul>
 * <li><b>Return value:</b> the signed depth. It is positive when the volumes overlap: the distance to move the second
 * volume along {@code normal} to separate them. It is {@code 0} when they touch, and minus the separation distance when
 * they are apart.</li>
 * <li><b>{@code normal}:</b> unit length, pointing from the first volume to the second.</li>
 * <li><b>{@code point}:</b> the midpoint between the two volumes' deepest points along the normal (their closest points
 * when apart), except where a function documents otherwise.</li>
 * <li><b>Outputs:</b> {@code normal} and {@code point} must be different objects; either may be one of the input
 * vectors. No function allocates, and all are safe to call from any thread.</li>
 * </ul>
 * For an oriented box, transform the other volume into the box's frame, call the AABB version, then rotate the normal
 * and point back.
 *
 * @since 2.4.0
 */
public final class Contactsf {
    private Contactsf() {
    }

    /**
     * Computes the contact between sphere {@code (c0, r0)} and sphere {@code (c1, r1)}. The point is the midpoint of
     * {@code c0 + r0 n} and {@code c1 - r1 n}. Coincident centers give the normal {@code +X}.
     *
     * @param c0 the center of the first sphere
     * @param r0 the radius of the first sphere (not squared)
     * @param c1 the center of the second sphere
     * @param r1 the radius of the second sphere (not squared)
     * @param normal receives the unit normal from the first sphere to the second
     * @param point receives the contact point
     * @return the signed depth {@code r0 + r1 - |c1 - c0|}
     */
    public static float sphereSphere(Vector3fc c0, float r0, Vector3fc c1, float r1, Vector3f normal, Vector3f point) {
        float x0 = c0.x(), y0 = c0.y(), z0 = c0.z(), x1 = c1.x(), y1 = c1.y(), z1 = c1.z();
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float dist = (float) Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
        float nx = 1, ny = 0, nz = 0;
        if (dist > 0) {
            nx = dx / dist;
            ny = dy / dist;
            nz = dz / dist;
        }
        float h = 0.5f * (r0 - r1);
        normal.set(nx, ny, nz);
        point.set(0.5f * (x0 + x1) + nx * h, 0.5f * (y0 + y1) + ny * h, 0.5f * (z0 + z1) + nz * h);
        return r0 + r1 - dist;
    }

    /**
     * Returns whether the solid spheres overlap or touch, including when one contains the other. The radii are not
     * squared. Gives the same answer as {@link Intersectionf#testSphereSphere(Vector3fc, float, Vector3fc, float)}.
     *
     * @param c0 the center of the first sphere
     * @param r0 the radius of the first sphere
     * @param c1 the center of the second sphere
     * @param r1 the radius of the second sphere
     * @return whether the distance between the centers is at most {@code r0 + r1}, computed in double
     */
    public static boolean testSphereSphere(Vector3fc c0, float r0, Vector3fc c1, float r1) {
        double dx = (double) c1.x() - c0.x(), dy = (double) c1.y() - c0.y(), dz = (double) c1.z() - c0.z();
        double r = (double) r0 + r1;
        return dx * dx + dy * dy + dz * dz <= r * r;
    }

    /**
     * Computes the contact between box {@code [minA, maxA]} and box {@code [minB, maxB]}. When they overlap or touch,
     * the normal is the axis direction with the smallest push that separates them (the exact minimum translation; ties
     * go to the first of +X, -X, +Y, -Y, +Z, -Z), and the point is the center of the overlap region. When they are
     * apart, the normal and depth come from the closest points, and the point is the midpoint between them.
     *
     * @param minA the minimum corner of the first box
     * @param maxA the maximum corner of the first box
     * @param minB the minimum corner of the second box
     * @param maxB the maximum corner of the second box
     * @param normal receives the unit normal from the first box to the second
     * @param point receives the contact point
     * @return the signed depth: the minimum translation when they overlap, minus the distance between the boxes when
     *        they are apart
     */
    public static float aabbAabb(Vector3fc minA, Vector3fc maxA, Vector3fc minB, Vector3fc maxB, Vector3f normal,
            Vector3f point) {
        float aminX = minA.x(), aminY = minA.y(), aminZ = minA.z(), amaxX = maxA.x(), amaxY = maxA.y(), amaxZ = maxA.z();
        float bminX = minB.x(), bminY = minB.y(), bminZ = minB.z(), bmaxX = maxB.x(), bmaxY = maxB.y(), bmaxZ = maxB.z();
        float gx = gap(aminX, amaxX, bminX, bmaxX), gy = gap(aminY, amaxY, bminY, bmaxY), gz = gap(aminZ, amaxZ, bminZ, bmaxZ);
        float cx = 0.5f * (Math.max(aminX, bminX) + Math.min(amaxX, bmaxX));
        float cy = 0.5f * (Math.max(aminY, bminY) + Math.min(amaxY, bmaxY));
        float cz = 0.5f * (Math.max(aminZ, bminZ) + Math.min(amaxZ, bmaxZ));
        point.set(cx, cy, cz);
        if (gx != 0 || gy != 0 || gz != 0) {
            float dist = (float) Math.sqrt((double) gx * gx + (double) gy * gy + (double) gz * gz);
            normal.set(gx / dist, gy / dist, gz / dist);
            return -dist;
        }
        // overlapping: the smallest push of B along +-X, +-Y, +-Z
        float best = amaxX - bminX;
        float nx = 1, ny = 0, nz = 0;
        float p = bmaxX - aminX;
        if (p < best) {
            best = p;
            nx = -1;
        }
        p = amaxY - bminY;
        if (p < best) {
            best = p;
            nx = 0;
            ny = 1;
        }
        p = bmaxY - aminY;
        if (p < best) {
            best = p;
            nx = 0;
            ny = -1;
        }
        p = amaxZ - bminZ;
        if (p < best) {
            best = p;
            nx = 0;
            ny = 0;
            nz = 1;
        }
        p = bmaxZ - aminZ;
        if (p < best) {
            best = p;
            nx = 0;
            ny = 0;
            nz = -1;
        }
        normal.set(nx, ny, nz);
        return best;
    }

    /** Signed gap from interval A to interval B: positive when B lies above A, negative below, 0 when they overlap. */
    private static float gap(float aMin, float aMax, float bMin, float bMax) {
        if (bMin > aMax) {
            return bMin - aMax;
        }
        if (bMax < aMin) {
            return bMax - aMin;
        }
        return 0;
    }

    /**
     * Computes the contact between sphere {@code (c, r)} and box {@code [min, max]}. When the center is inside the box
     * or on its surface, the normal comes from the box face nearest to the center (see {@link #nearestFace}), and the
     * box must move against that face's outward normal.
     *
     * @param c the sphere center
     * @param r the sphere radius (not squared)
     * @param min the box's minimum corner
     * @param max the box's maximum corner
     * @param normal receives the unit normal from the sphere to the box
     * @param point receives the contact point
     * @return the signed depth: {@code r} minus the distance from the center to the box, or, for a center inside the
     *        box, {@code r} plus the center's distance to the nearest face
     */
    public static float sphereAabb(Vector3fc c, float r, Vector3fc min, Vector3fc max, Vector3f normal, Vector3f point) {
        float x = c.x(), y = c.y(), z = c.z();
        float minX = min.x(), minY = min.y(), minZ = min.z(), maxX = max.x(), maxY = max.y(), maxZ = max.z();
        float qx = Math.min(Math.max(x, minX), maxX), qy = Math.min(Math.max(y, minY), maxY), qz = Math.min(Math.max(z, minZ), maxZ);
        float dx = qx - x, dy = qy - y, dz = qz - z;
        if (dx != 0 || dy != 0 || dz != 0) {
            float dist = (float) Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
            float nx = dx / dist, ny = dy / dist, nz = dz / dist;
            normal.set(nx, ny, nz);
            point.set(0.5f * (x + nx * r + qx), 0.5f * (y + ny * r + qy), 0.5f * (z + nz * r + qz));
            return r - dist;
        }
        float face = nearestFace(x, y, z, minX, minY, minZ, maxX, maxY, maxZ, normal);
        // the box moves against the outward normal of the face the sphere leaves through
        float nx = 0 - normal.x, ny = 0 - normal.y, nz = 0 - normal.z; // 0 - x, not -x: no negative zeros
        normal.set(nx, ny, nz);
        point.set(x + 0.5f * nx * (r - face), y + 0.5f * ny * (r - face), z + 0.5f * nz * (r - face));
        return r + face;
    }

    /**
     * Returns the face of box {@code [min, max]} nearest to point {@code p}, inside or outside the box. For a point
     * outside, every face whose plane {@code p} lies beyond contains the box point closest to {@code p}, so all of them
     * are nearest; the one whose plane {@code p} lies farthest beyond is returned. Ties go to the first of -X, +X, -Y,
     * +Y, -Z, +Z.
     *
     * @param p the point
     * @param min the box's minimum corner
     * @param max the box's maximum corner
     * @param outwardNormal receives that face's outward unit normal; may alias an input
     * @return the signed distance from {@code p} to that face (the face rectangle, not its plane): positive inside the
     *        box, negative outside (minus the distance from {@code p} to the box), 0 on the surface
     */
    public static float nearestFace(Vector3fc p, Vector3fc min, Vector3fc max, Vector3f outwardNormal) {
        return nearestFace(p.x(), p.y(), p.z(), min.x(), min.y(), min.z(), max.x(), max.y(), max.z(), outwardNormal);
    }

    private static float nearestFace(float x, float y, float z, float minX, float minY, float minZ, float maxX, float maxY,
            float maxZ, Vector3f outwardNormal) {
        float best = x - minX;
        float nx = -1, ny = 0, nz = 0;
        float d = maxX - x;
        if (d < best) {
            best = d;
            nx = 1;
        }
        d = y - minY;
        if (d < best) {
            best = d;
            nx = 0;
            ny = -1;
        }
        d = maxY - y;
        if (d < best) {
            best = d;
            nx = 0;
            ny = 1;
        }
        d = z - minZ;
        if (d < best) {
            best = d;
            nx = 0;
            ny = 0;
            nz = -1;
        }
        d = maxZ - z;
        if (d < best) {
            best = d;
            nx = 0;
            ny = 0;
            nz = 1;
        }
        if (best < 0) {
            // outside: the faces whose planes p lies beyond all contain the closest box point, so the one chosen is a
            // nearest face; its distance is the distance to the box, not to its plane (before 3.0 the plane distance)
            double dx = x < minX ? (double) minX - x : x > maxX ? (double) x - maxX : 0;
            double dy = y < minY ? (double) minY - y : y > maxY ? (double) y - maxY : 0;
            double dz = z < minZ ? (double) minZ - z : z > maxZ ? (double) z - maxZ : 0;
            best = (float) -Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        outwardNormal.set(nx, ny, nz);
        return best;
    }

    /**
     * Computes the contact between a capsule (segment {@code a}–{@code b}, radius {@code r}) and box
     * {@code [min, max]}.
     * <ul>
     * <li><b>Segment outside the box:</b> the result comes from the exact closest points ({@link
     * ClosestPointsf#segmentAab(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3f, Vector3f)}).</li>
     * <li><b>Segment touching or inside the box:</b> the depth is the exact minimum translation of the segment out of
     * the box, plus {@code r}. It comes from the separating-axis test over the three box axes and the three
     * {@code (b − a) × axis} directions, which is exact for a segment against a box. The point is then the segment
     * point inside the box that the closest-point search found.</li>
     * </ul>
     *
     * @param a one end of the capsule's segment
     * @param b the other end of the capsule's segment
     * @param r the capsule radius (not squared)
     * @param min the box's minimum corner
     * @param max the box's maximum corner
     * @param normal receives the unit normal from the capsule to the box
     * @param point receives the contact point
     * @return the signed depth
     */
    public static float capsuleAabb(Vector3fc a, Vector3fc b, float r, Vector3fc min, Vector3fc max, Vector3f normal,
            Vector3f point) {
        float ax = a.x(), ay = a.y(), az = a.z(), bx = b.x(), by = b.y(), bz = b.z();
        float minX = min.x(), minY = min.y(), minZ = min.z(), maxX = max.x(), maxY = max.y(), maxZ = max.z();
        float d2 = ClosestPointsf.segmentAab(ax, ay, az, bx, by, bz, minX, minY, minZ, maxX, maxY, maxZ, normal, point);
        float sx = normal.x, sy = normal.y, sz = normal.z; // the point on the segment
        if (d2 > 0) {
            float dist = (float) Math.sqrt(d2);
            float nx = (point.x - sx) / dist, ny = (point.y - sy) / dist, nz = (point.z - sz) / dist;
            point.set(0.5f * (sx + nx * r + point.x), 0.5f * (sy + ny * r + point.y), 0.5f * (sz + nz * r + point.z));
            normal.set(nx, ny, nz);
            return r - dist;
        }
        double ux = (double) bx - ax, uy = (double) by - ay, uz = (double) bz - az;
        double u2 = ux * ux + uy * uy + uz * uz;
        double cx = 0.5 * ((double) minX + maxX), cy = 0.5 * ((double) minY + maxY), cz = 0.5 * ((double) minZ + maxZ);
        double hx = 0.5 * ((double) maxX - minX), hy = 0.5 * ((double) maxY - minY), hz = 0.5 * ((double) maxZ - minZ);
        double best = Double.POSITIVE_INFINITY, bnx = 1, bny = 0, bnz = 0;
        for (int i = 0; i < 6; i++) {
            double lx, ly, lz;
            if (i < 3) {
                lx = i == 0 ? 1 : 0;
                ly = i == 1 ? 1 : 0;
                lz = i == 2 ? 1 : 0;
            } else {
                // u x e_i
                lx = i == 3 ? 0 : i == 4 ? -uz : uy;
                ly = i == 3 ? uz : i == 4 ? 0 : -ux;
                lz = i == 3 ? -uy : i == 4 ? ux : 0;
                double l2 = lx * lx + ly * ly + lz * lz;
                if (!(l2 > 1e-12 * u2)) {
                    continue; // parallel to that box axis: already covered by the face axes
                }
                double inv = 1 / Math.sqrt(l2);
                lx *= inv;
                ly *= inv;
                lz *= inv;
            }
            double pa = ax * lx + ay * ly + az * lz, pb = bx * lx + by * ly + bz * lz;
            double segMin = Math.min(pa, pb), segMax = Math.max(pa, pb);
            double pc = cx * lx + cy * ly + cz * lz;
            double ext = hx * Math.abs(lx) + hy * Math.abs(ly) + hz * Math.abs(lz);
            double up = segMax - (pc - ext); // push the box along +L
            double down = (pc + ext) - segMin; // push the box along -L
            if (up < best) {
                best = up;
                bnx = lx;
                bny = ly;
                bnz = lz;
            }
            if (down < best) {
                best = down;
                bnx = 0 - lx; // 0 - x, not -x: no negative zeros
                bny = 0 - ly;
                bnz = 0 - lz;
            }
        }
        point.set(sx, sy, sz);
        normal.set((float) bnx, (float) bny, (float) bnz);
        return (float) (Math.max(best, 0) + r);
    }
}
