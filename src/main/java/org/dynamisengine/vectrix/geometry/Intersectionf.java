/*
 * The MIT License
 *
 * Copyright (c) 2015-2024 Kai Burjack
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

import org.dynamisengine.vectrix.core.*;
import org.dynamisengine.vectrix.core.Math;
import org.dynamisengine.vectrix.core.Runtime;

/**
 * Contains intersection and distance tests for some 2D and 3D geometric primitives.
 * 
 * @author Kai Burjack
 */
public final class Intersectionf {

    /** Not instantiable: every member is static. */
    private Intersectionf() {
    }

    /**
     * Return value of
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, float, float, float, float, Vector3f)},
     * {@link #findClosestPointOnTriangle(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3f)},
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #findClosestPointOnTriangle(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)} or
     * {@link #intersectSweptSphereTriangle}
     * to signal that the closest point is the first vertex of the triangle.
     */
    public static final int POINT_ON_TRIANGLE_VERTEX_0 = 1;
    /**
     * Return value of
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, float, float, float, float, Vector3f)},
     * {@link #findClosestPointOnTriangle(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3f)},
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #findClosestPointOnTriangle(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)} or
     * {@link #intersectSweptSphereTriangle}
     * to signal that the closest point is the second vertex of the triangle.
     */
    public static final int POINT_ON_TRIANGLE_VERTEX_1 = 2;
    /**
     * Return value of
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, float, float, float, float, Vector3f)},
     * {@link #findClosestPointOnTriangle(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3f)},
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #findClosestPointOnTriangle(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)} or
     * {@link #intersectSweptSphereTriangle}
     * to signal that the closest point is the third vertex of the triangle.
     */
    public static final int POINT_ON_TRIANGLE_VERTEX_2 = 3;

    /**
     * Return value of
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, float, float, float, float, Vector3f)},
     * {@link #findClosestPointOnTriangle(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3f)},
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #findClosestPointOnTriangle(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)} or
     * {@link #intersectSweptSphereTriangle}
     * to signal that the closest point lies on the edge between the first and second vertex of the triangle.
     */
    public static final int POINT_ON_TRIANGLE_EDGE_01 = 4;
    /**
     * Return value of
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, float, float, float, float, Vector3f)},
     * {@link #findClosestPointOnTriangle(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3f)},
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #findClosestPointOnTriangle(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)} or
     * {@link #intersectSweptSphereTriangle}
     * to signal that the closest point lies on the edge between the second and third vertex of the triangle.
     */
    public static final int POINT_ON_TRIANGLE_EDGE_12 = 5;
    /**
     * Return value of
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, float, float, float, float, Vector3f)},
     * {@link #findClosestPointOnTriangle(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3f)},
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #findClosestPointOnTriangle(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)} or
     * {@link #intersectSweptSphereTriangle}
     * to signal that the closest point lies on the edge between the third and first vertex of the triangle.
     */
    public static final int POINT_ON_TRIANGLE_EDGE_20 = 6;

    /**
     * Return value of
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, float, float, float, float, Vector3f)},
     * {@link #findClosestPointOnTriangle(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3f)},
     * {@link #findClosestPointOnTriangle(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #findClosestPointOnTriangle(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)} or 
     * {@link #intersectSweptSphereTriangle}
     * to signal that the closest point lies on the face of the triangle.
     */
    public static final int POINT_ON_TRIANGLE_FACE = 7;

    /**
     * Return value of {@link #intersectRayAar(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #intersectRayAar(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)}
     * to indicate that the ray intersects the side of the axis-aligned rectangle with the minimum x coordinate.
     */
    public static final int AAR_SIDE_MINX = 0;
    /**
     * Return value of {@link #intersectRayAar(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #intersectRayAar(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)}
     * to indicate that the ray intersects the side of the axis-aligned rectangle with the minimum y coordinate.
     */
    public static final int AAR_SIDE_MINY = 1;
    /**
     * Return value of {@link #intersectRayAar(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #intersectRayAar(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)}
     * to indicate that the ray intersects the side of the axis-aligned rectangle with the maximum x coordinate.
     */
    public static final int AAR_SIDE_MAXX = 2;
    /**
     * Return value of {@link #intersectRayAar(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #intersectRayAar(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)}
     * to indicate that the ray intersects the side of the axis-aligned rectangle with the maximum y coordinate.
     */
    public static final int AAR_SIDE_MAXY = 3;

    /**
     * Return value of {@link #intersectLineSegmentAab(float, float, float, float, float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #intersectLineSegmentAab(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector2f)} to indicate that the line segment does not intersect the axis-aligned box;
     * or return value of {@link #intersectLineSegmentAar(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #intersectLineSegmentAar(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)} to indicate that the line segment does not intersect the axis-aligned rectangle.
     */
    public static final int OUTSIDE = -1;
    /**
     * Return value of {@link #intersectLineSegmentAab(float, float, float, float, float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #intersectLineSegmentAab(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector2f)} to indicate that one end point of the line segment lies inside of the axis-aligned box;
     * or return value of {@link #intersectLineSegmentAar(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #intersectLineSegmentAar(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)} to indicate that one end point of the line segment lies inside of the axis-aligned rectangle.
     */
    public static final int ONE_INTERSECTION = 1;
    /**
     * Return value of {@link #intersectLineSegmentAab(float, float, float, float, float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #intersectLineSegmentAab(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector2f)} to indicate that the line segment intersects two sides of the axis-aligned box
     * or lies on an edge or a side of the box;
     * or return value of {@link #intersectLineSegmentAar(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #intersectLineSegmentAar(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)} to indicate that the line segment intersects two edges of the axis-aligned rectangle
     * or lies on an edge of the rectangle.
     */
    public static final int TWO_INTERSECTION = 2;
    /**
     * Return value of {@link #intersectLineSegmentAab(float, float, float, float, float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #intersectLineSegmentAab(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector2f)} to indicate that the line segment lies completely inside of the axis-aligned box;
     * or return value of {@link #intersectLineSegmentAar(float, float, float, float, float, float, float, float, Vector2f)} and
     * {@link #intersectLineSegmentAar(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)} to indicate that the line segment lies completely inside of the axis-aligned rectangle.
     */
    public static final int INSIDE = 3;

    /**
     * Test whether the plane with the general plane equation <i>a*x + b*y + c*z + d = 0</i> intersects the sphere with center
     * <code>(centerX, centerY, centerZ)</code> and <code>radius</code>.
     * <p>
     * <code>radius</code> is the radius itself, not its square. The plane normal <code>(a, b, c)</code> does not need
     * to be unit length.
     * <p>
     * Reference: <a href="http://math.stackexchange.com/questions/943383/determine-circle-of-intersection-of-plane-and-sphere">http://math.stackexchange.com</a>
     *
     * @param a
     *          the x factor in the plane equation
     * @param b
     *          the y factor in the plane equation
     * @param c
     *          the z factor in the plane equation
     * @param d
     *          the constant in the plane equation
     * @param centerX
     *          the x coordinate of the sphere's center
     * @param centerY
     *          the y coordinate of the sphere's center
     * @param centerZ
     *          the z coordinate of the sphere's center
     * @param radius
     *          the radius of the sphere (not squared)
     * @return <code>true</code> iff the plane intersects the sphere; <code>false</code> otherwise
     */
    public static boolean testPlaneSphere(
            float a, float b, float c, float d,
            float centerX, float centerY, float centerZ, float radius) {
        float denom = (float) Math.sqrt(a * a + b * b + c * c);
        float dist = (a * centerX + b * centerY + c * centerZ + d) / denom;
        return -radius <= dist && dist <= radius;
    }

    /**
     * Test whether the plane with the general plane equation <i>a*x + b*y + c*z + d = 0</i> intersects the sphere with center
     * <code>(centerX, centerY, centerZ)</code> and <code>radius</code>, and store the center of the circle of
     * intersection in the <code>(x, y, z)</code> components of the supplied vector and the radius of that circle in the w component.
     * <p>
     * <code>radius</code> is the radius itself, not its square, and the w component receives the circle's radius, not
     * its square. The plane normal <code>(a, b, c)</code> does not need to be unit length.
     * <p>
     * Reference: <a href="http://math.stackexchange.com/questions/943383/determine-circle-of-intersection-of-plane-and-sphere">http://math.stackexchange.com</a>
     *
     * @param a
     *          the x factor in the plane equation
     * @param b
     *          the y factor in the plane equation
     * @param c
     *          the z factor in the plane equation
     * @param d
     *          the constant in the plane equation
     * @param centerX
     *          the x coordinate of the sphere's center
     * @param centerY
     *          the y coordinate of the sphere's center
     * @param centerZ
     *          the z coordinate of the sphere's center
     * @param radius
     *          the radius of the sphere (not squared)
     * @param intersectionCenterAndRadius
     *          will hold the center of the circle of intersection in the <code>(x, y, z)</code> components and its
     *          radius (not squared) in the w component
     * @return <code>true</code> iff the plane intersects the sphere; <code>false</code> otherwise
     */
    public static boolean intersectPlaneSphere(
            float a, float b, float c, float d,
            float centerX, float centerY, float centerZ, float radius,
            Vector4f intersectionCenterAndRadius) {
        float invDenom = Math.invsqrt(a * a + b * b + c * c);
        float dist = (a * centerX + b * centerY + c * centerZ + d) * invDenom;
        if (-radius <= dist && dist <= radius) {
            intersectionCenterAndRadius.x = centerX - dist * a * invDenom;
            intersectionCenterAndRadius.y = centerY - dist * b * invDenom;
            intersectionCenterAndRadius.z = centerZ - dist * c * invDenom;
            intersectionCenterAndRadius.w = (float) Math.sqrt(radius * radius - dist * dist);
            return true;
        }
        return false;
    }

    /**
     * Test whether the plane with the general plane equation <i>a*x + b*y + c*z + d = 0</i> intersects the moving sphere with center
     * <code>(cX, cY, cZ)</code>, <code>radius</code> and velocity <code>(vX, vY, vZ)</code>, and store the point of intersection
     * in the <code>(x, y, z)</code> components of the supplied vector and the time of intersection in the w component.
     * <p>
     * The normal vector <code>(a, b, c)</code> of the plane equation needs to be normalized.
     * <p>
     * <code>radius</code> is the radius itself, not its square.
     * <p>
     * Reference: Book "Real-Time Collision Detection" chapter 5.5.3 "Intersecting Moving Sphere Against Plane"
     * 
     * @param a
     *          the x factor in the plane equation
     * @param b
     *          the y factor in the plane equation
     * @param c
     *          the z factor in the plane equation
     * @param d
     *          the constant in the plane equation
     * @param cX
     *          the x coordinate of the center position of the sphere at t=0
     * @param cY
     *          the y coordinate of the center position of the sphere at t=0
     * @param cZ
     *          the z coordinate of the center position of the sphere at t=0
     * @param radius
     *          the sphere's radius (not squared)
     * @param vX
     *          the x component of the velocity of the sphere
     * @param vY
     *          the y component of the velocity of the sphere
     * @param vZ
     *          the z component of the velocity of the sphere
     * @param pointAndTime
     *          will hold the point and time of intersection (if any)
     * @return <code>true</code> iff the sphere intersects the plane; <code>false</code> otherwise
     */
    public static boolean intersectPlaneSweptSphere(
            float a, float b, float c, float d,
            float cX, float cY, float cZ, float radius,
            float vX, float vY, float vZ,
            Vector4f pointAndTime) {
        // Compute distance of sphere center to plane
        float dist = a * cX + b * cY + c * cZ + d;
        if (Math.abs(dist) <= radius) {
            // The sphere is already overlapping the plane. Set time of
            // intersection to zero and q to sphere center
            pointAndTime.set(cX, cY, cZ, 0.0f);
            return true;
        }
        float denom = a * vX + b * vY + c * vZ;
        if (denom * dist >= 0.0f) {
            // No intersection as sphere moving parallel to or away from plane
            return false;
        }
        // Sphere is moving towards the plane
        // Use +r in computations if sphere in front of plane, else -r
        float r = dist > 0.0f ? radius : -radius;
        float t = (r - dist) / denom;
        pointAndTime.set(
                cX + t * vX - r * a,
                cY + t * vY - r * b,
                cZ + t * vZ - r * c,
                t);
        return true;
    }

    /**
     * Test whether the plane with the general plane equation <i>a*x + b*y + c*z + d = 0</i> intersects the sphere moving from center
     * position <code>(t0X, t0Y, t0Z)</code> to <code>(t1X, t1Y, t1Z)</code> and having the given <code>radius</code>.
     * <p>
     * The normal vector <code>(a, b, c)</code> of the plane equation needs to be normalized.
     * <p>
     * <code>r</code> is the radius itself, not its square.
     * <p>
     * Reference: Book "Real-Time Collision Detection" chapter 5.5.3 "Intersecting Moving Sphere Against Plane"
     * 
     * @param a
     *          the x factor in the plane equation
     * @param b
     *          the y factor in the plane equation
     * @param c
     *          the z factor in the plane equation
     * @param d
     *          the constant in the plane equation
     * @param t0X
     *          the x coordinate of the start position of the sphere
     * @param t0Y
     *          the y coordinate of the start position of the sphere
     * @param t0Z
     *          the z coordinate of the start position of the sphere
     * @param r
     *          the sphere's radius (not squared)
     * @param t1X
     *          the x coordinate of the end position of the sphere
     * @param t1Y
     *          the y coordinate of the end position of the sphere
     * @param t1Z
     *          the z coordinate of the end position of the sphere
     * @return <code>true</code> if the sphere intersects the plane; <code>false</code> otherwise
     */
    public static boolean testPlaneSweptSphere(
            float a, float b, float c, float d,
            float t0X, float t0Y, float t0Z, float r,
            float t1X, float t1Y, float t1Z) {
        // Get the distance for both a and b from plane p
        float adist = t0X * a + t0Y * b + t0Z * c + d;
        float bdist = t1X * a + t1Y * b + t1Z * c + d;
        // Intersects if on different sides of plane (distances have different signs)
        if (adist * bdist < 0.0f) return true;
        // Intersects if start or end position within radius from plane
        if (Math.abs(adist) <= r || Math.abs(bdist) <= r) return true;
        // No intersection
        return false;
    }

    /**
     * Test whether the axis-aligned box with minimum corner <code>(minX, minY, minZ)</code> and maximum corner <code>(maxX, maxY, maxZ)</code>
     * intersects the plane with the general equation <i>a*x + b*y + c*z + d = 0</i>.
     * <p>
     * Reference: <a href="http://www.lighthouse3d.com/tutorials/view-frustum-culling/geometric-approach-testing-boxes-ii/">http://www.lighthouse3d.com</a> ("Geometric Approach - Testing Boxes II")
     * 
     * @param minX
     *          the x coordinate of the minimum corner of the axis-aligned box
     * @param minY
     *          the y coordinate of the minimum corner of the axis-aligned box
     * @param minZ
     *          the z coordinate of the minimum corner of the axis-aligned box
     * @param maxX
     *          the x coordinate of the maximum corner of the axis-aligned box
     * @param maxY
     *          the y coordinate of the maximum corner of the axis-aligned box
     * @param maxZ
     *          the z coordinate of the maximum corner of the axis-aligned box
     * @param a
     *          the x factor in the plane equation
     * @param b
     *          the y factor in the plane equation
     * @param c
     *          the z factor in the plane equation
     * @param d
     *          the constant in the plane equation
     * @return <code>true</code> iff the axis-aligned box intersects the plane; <code>false</code> otherwise
     */
    public static boolean testAabPlane(
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            float a, float b, float c, float d) {
        float pX, pY, pZ, nX, nY, nZ;
        if (a > 0.0f) {
            pX = maxX;
            nX = minX;
        } else {
            pX = minX;
            nX = maxX;
        }
        if (b > 0.0f) {
            pY = maxY;
            nY = minY;
        } else {
            pY = minY;
            nY = maxY;
        }
        if (c > 0.0f) {
            pZ = maxZ;
            nZ = minZ;
        } else {
            pZ = minZ;
            nZ = maxZ;
        }
        float distN = d + a * nX + b * nY + c * nZ;
        float distP = d + a * pX + b * pY + c * pZ;
        return distN <= 0.0f && distP >= 0.0f;
    }

    /**
     * Test whether the axis-aligned box with minimum corner <code>min</code> and maximum corner <code>max</code>
     * intersects the plane with the general equation <i>a*x + b*y + c*z + d = 0</i>.
     * <p>
     * Reference: <a href="http://www.lighthouse3d.com/tutorials/view-frustum-culling/geometric-approach-testing-boxes-ii/">http://www.lighthouse3d.com</a> ("Geometric Approach - Testing Boxes II")
     * 
     * @param min
     *          the minimum corner of the axis-aligned box
     * @param max
     *          the maximum corner of the axis-aligned box
     * @param a
     *          the x factor in the plane equation
     * @param b
     *          the y factor in the plane equation
     * @param c
     *          the z factor in the plane equation
     * @param d
     *          the constant in the plane equation
     * @return <code>true</code> iff the axis-aligned box intersects the plane; <code>false</code> otherwise
     */
    public static boolean testAabPlane(Vector3fc min, Vector3fc max, float a, float b, float c, float d) {
        return testAabPlane(min.x(), min.y(), min.z(), max.x(), max.y(), max.z(), a, b, c, d);
    }

    /**
     * Test whether the axis-aligned box with minimum corner <code>(minXA, minYA, minZA)</code> and maximum corner <code>(maxXA, maxYA, maxZA)</code>
     * intersects the axis-aligned box with minimum corner <code>(minXB, minYB, minZB)</code> and maximum corner <code>(maxXB, maxYB, maxZB)</code>.
     * 
     * @param minXA
     *              the x coordinate of the minimum corner of the first axis-aligned box
     * @param minYA
     *              the y coordinate of the minimum corner of the first axis-aligned box
     * @param minZA
     *              the z coordinate of the minimum corner of the first axis-aligned box
     * @param maxXA
     *              the x coordinate of the maximum corner of the first axis-aligned box
     * @param maxYA
     *              the y coordinate of the maximum corner of the first axis-aligned box
     * @param maxZA
     *              the z coordinate of the maximum corner of the first axis-aligned box
     * @param minXB
     *              the x coordinate of the minimum corner of the second axis-aligned box
     * @param minYB
     *              the y coordinate of the minimum corner of the second axis-aligned box
     * @param minZB
     *              the z coordinate of the minimum corner of the second axis-aligned box
     * @param maxXB
     *              the x coordinate of the maximum corner of the second axis-aligned box
     * @param maxYB
     *              the y coordinate of the maximum corner of the second axis-aligned box
     * @param maxZB
     *              the z coordinate of the maximum corner of the second axis-aligned box
     * @return <code>true</code> iff both axis-aligned boxes intersect; <code>false</code> otherwise
     */
    public static boolean testAabAab(
            float minXA, float minYA, float minZA,
            float maxXA, float maxYA, float maxZA,
            float minXB, float minYB, float minZB,
            float maxXB, float maxYB, float maxZB) {
        return maxXA >= minXB && maxYA >= minYB && maxZA >= minZB && 
               minXA <= maxXB && minYA <= maxYB && minZA <= maxZB;
    }

    /**
     * Test whether the axis-aligned box with minimum corner <code>minA</code> and maximum corner <code>maxA</code>
     * intersects the axis-aligned box with minimum corner <code>minB</code> and maximum corner <code>maxB</code>.
     * 
     * @param minA
     *              the minimum corner of the first axis-aligned box
     * @param maxA
     *              the maximum corner of the first axis-aligned box
     * @param minB
     *              the minimum corner of the second axis-aligned box
     * @param maxB
     *              the maximum corner of the second axis-aligned box
     * @return <code>true</code> iff both axis-aligned boxes intersect; <code>false</code> otherwise
     */
    public static boolean testAabAab(Vector3fc minA, Vector3fc maxA, Vector3fc minB, Vector3fc maxB) {
        return testAabAab(minA.x(), minA.y(), minA.z(), maxA.x(), maxA.y(), maxA.z(), minB.x(), minB.y(), minB.z(), maxB.x(), maxB.y(), maxB.z());
    }

    /**
     * Test whether two oriented boxes given via their center position, orientation and half-size, intersect.
     * <p>
     * The orientation of a box is given as three unit vectors spanning the local orthonormal basis of the box.
     * <p>
     * The size is given as the half-size along each of the unit vectors defining the orthonormal basis.
     * <p>
     * Reference: Book "Real-Time Collision Detection" chapter 4.4.1 "OBB-OBB Intersection"
     * 
     * @param b0c
     *          the center of the first box
     * @param b0uX
     *          the local X unit vector of the first box
     * @param b0uY
     *          the local Y unit vector of the first box
     * @param b0uZ
     *          the local Z unit vector of the first box
     * @param b0hs
     *          the half-size of the first box
     * @param b1c
     *          the center of the second box
     * @param b1uX
     *          the local X unit vector of the second box
     * @param b1uY
     *          the local Y unit vector of the second box
     * @param b1uZ
     *          the local Z unit vector of the second box
     * @param b1hs
     *          the half-size of the second box
     * @return <code>true</code> if both boxes intersect; <code>false</code> otherwise
     */
    public static boolean testObOb(
            Vector3f b0c, Vector3f b0uX, Vector3f b0uY, Vector3f b0uZ, Vector3f b0hs,
            Vector3f b1c, Vector3f b1uX, Vector3f b1uY, Vector3f b1uZ, Vector3f b1hs) {
        return testObOb(
                b0c.x, b0c.y, b0c.z, b0uX.x, b0uX.y, b0uX.z, b0uY.x, b0uY.y, b0uY.z, b0uZ.x, b0uZ.y, b0uZ.z, b0hs.x, b0hs.y, b0hs.z,
                b1c.x, b1c.y, b1c.z, b1uX.x, b1uX.y, b1uX.z, b1uY.x, b1uY.y, b1uY.z, b1uZ.x, b1uZ.y, b1uZ.z, b1hs.x, b1hs.y, b1hs.z);
    }

    /**
     * Test whether two oriented boxes given via their center position, orientation and half-size, intersect.
     * <p>
     * The orientation of a box is given as three unit vectors spanning the local orthonormal basis of the box.
     * <p>
     * The size is given as the half-size along each of the unit vectors defining the orthonormal basis.
     * <p>
     * Reference: Book "Real-Time Collision Detection" chapter 4.4.1 "OBB-OBB Intersection"
     * 
     * @param b0cX
     *          the x coordinate of the center of the first box
     * @param b0cY
     *          the y coordinate of the center of the first box
     * @param b0cZ
     *          the z coordinate of the center of the first box
     * @param b0uXx
     *          the x coordinate of the local X unit vector of the first box
     * @param b0uXy
     *          the y coordinate of the local X unit vector of the first box
     * @param b0uXz
     *          the z coordinate of the local X unit vector of the first box
     * @param b0uYx
     *          the x coordinate of the local Y unit vector of the first box
     * @param b0uYy
     *          the y coordinate of the local Y unit vector of the first box
     * @param b0uYz
     *          the z coordinate of the local Y unit vector of the first box
     * @param b0uZx
     *          the x coordinate of the local Z unit vector of the first box
     * @param b0uZy
     *          the y coordinate of the local Z unit vector of the first box
     * @param b0uZz
     *          the z coordinate of the local Z unit vector of the first box
     * @param b0hsX
     *          the half-size of the first box along its local X axis
     * @param b0hsY
     *          the half-size of the first box along its local Y axis
     * @param b0hsZ
     *          the half-size of the first box along its local Z axis
     * @param b1cX
     *          the x coordinate of the center of the second box
     * @param b1cY
     *          the y coordinate of the center of the second box
     * @param b1cZ
     *          the z coordinate of the center of the second box
     * @param b1uXx
     *          the x coordinate of the local X unit vector of the second box
     * @param b1uXy
     *          the y coordinate of the local X unit vector of the second box
     * @param b1uXz
     *          the z coordinate of the local X unit vector of the second box
     * @param b1uYx
     *          the x coordinate of the local Y unit vector of the second box
     * @param b1uYy
     *          the y coordinate of the local Y unit vector of the second box
     * @param b1uYz
     *          the z coordinate of the local Y unit vector of the second box
     * @param b1uZx
     *          the x coordinate of the local Z unit vector of the second box
     * @param b1uZy
     *          the y coordinate of the local Z unit vector of the second box
     * @param b1uZz
     *          the z coordinate of the local Z unit vector of the second box
     * @param b1hsX
     *          the half-size of the second box along its local X axis
     * @param b1hsY
     *          the half-size of the second box along its local Y axis
     * @param b1hsZ
     *          the half-size of the second box along its local Z axis
     * @return <code>true</code> if both boxes intersect; <code>false</code> otherwise
     */
    public static boolean testObOb(
            float b0cX, float b0cY, float b0cZ, float b0uXx, float b0uXy, float b0uXz, float b0uYx, float b0uYy, float b0uYz, float b0uZx, float b0uZy, float b0uZz, float b0hsX, float b0hsY, float b0hsZ,
            float b1cX, float b1cY, float b1cZ, float b1uXx, float b1uXy, float b1uXz, float b1uYx, float b1uYy, float b1uYz, float b1uZx, float b1uZy, float b1uZz, float b1hsX, float b1hsY, float b1hsZ) {
        float ra, rb;
        // Compute rotation matrix expressing b in a's coordinate frame
        // R[i][j] = A_i · B_j (before 2.4.0 the matrix was transposed, wrong whenever box 0 was rotated)
        float rm00 = b0uXx * b1uXx + b0uXy * b1uXy + b0uXz * b1uXz;
        float rm01 = b0uXx * b1uYx + b0uXy * b1uYy + b0uXz * b1uYz;
        float rm02 = b0uXx * b1uZx + b0uXy * b1uZy + b0uXz * b1uZz;
        float rm10 = b0uYx * b1uXx + b0uYy * b1uXy + b0uYz * b1uXz;
        float rm11 = b0uYx * b1uYx + b0uYy * b1uYy + b0uYz * b1uYz;
        float rm12 = b0uYx * b1uZx + b0uYy * b1uZy + b0uYz * b1uZz;
        float rm20 = b0uZx * b1uXx + b0uZy * b1uXy + b0uZz * b1uXz;
        float rm21 = b0uZx * b1uYx + b0uZy * b1uYy + b0uZz * b1uYz;
        float rm22 = b0uZx * b1uZx + b0uZy * b1uZy + b0uZz * b1uZz;
        // Compute common subexpressions. Add in an epsilon term to
        // counteract arithmetic errors when two edges are parallel and
        // their cross product is (near) null (see text for details)
        float EPSILON = 1E-5f;
        float arm00 = Math.abs(rm00) + EPSILON;
        float arm01 = Math.abs(rm01) + EPSILON;
        float arm02 = Math.abs(rm02) + EPSILON;
        float arm10 = Math.abs(rm10) + EPSILON;
        float arm11 = Math.abs(rm11) + EPSILON;
        float arm12 = Math.abs(rm12) + EPSILON;
        float arm20 = Math.abs(rm20) + EPSILON;
        float arm21 = Math.abs(rm21) + EPSILON;
        float arm22 = Math.abs(rm22) + EPSILON;
        // Compute translation vector t
        float tx = b1cX - b0cX, ty = b1cY - b0cY, tz = b1cZ - b0cZ;
        // Bring translation into a's coordinate frame
        float tax = tx * b0uXx + ty * b0uXy + tz * b0uXz;
        float tay = tx * b0uYx + ty * b0uYy + tz * b0uYz;
        float taz = tx * b0uZx + ty * b0uZy + tz * b0uZz;
        // Test axes L = A0, L = A1, L = A2
        ra = b0hsX;
        rb = b1hsX * arm00 + b1hsY * arm01 + b1hsZ * arm02;
        if (Math.abs(tax) > ra + rb) return false;
        ra = b0hsY;
        rb = b1hsX * arm10 + b1hsY * arm11 + b1hsZ * arm12;
        if (Math.abs(tay) > ra + rb) return false;
        ra = b0hsZ;
        rb = b1hsX * arm20 + b1hsY * arm21 + b1hsZ * arm22;
        if (Math.abs(taz) > ra + rb) return false;
        // Test axes L = B0, L = B1, L = B2
        ra = b0hsX * arm00 + b0hsY * arm10 + b0hsZ * arm20;
        rb = b1hsX;
        if (Math.abs(tax * rm00 + tay * rm10 + taz * rm20) > ra + rb) return false;
        ra = b0hsX * arm01 + b0hsY * arm11 + b0hsZ * arm21;
        rb = b1hsY;
        if (Math.abs(tax * rm01 + tay * rm11 + taz * rm21) > ra + rb) return false;
        ra = b0hsX * arm02 + b0hsY * arm12 + b0hsZ * arm22;
        rb = b1hsZ;
        if (Math.abs(tax * rm02 + tay * rm12 + taz * rm22) > ra + rb) return false;
        // Test axis L = A0 x B0
        ra = b0hsY * arm20 + b0hsZ * arm10;
        rb = b1hsY * arm02 + b1hsZ * arm01;
        if (Math.abs(taz * rm10 - tay * rm20) > ra + rb) return false;
        // Test axis L = A0 x B1
        ra = b0hsY * arm21 + b0hsZ * arm11;
        rb = b1hsX * arm02 + b1hsZ * arm00;
        if (Math.abs(taz * rm11 - tay * rm21) > ra + rb) return false;
        // Test axis L = A0 x B2
        ra = b0hsY * arm22 + b0hsZ * arm12;
        rb = b1hsX * arm01 + b1hsY * arm00;
        if (Math.abs(taz * rm12 - tay * rm22) > ra + rb) return false;
        // Test axis L = A1 x B0
        ra = b0hsX * arm20 + b0hsZ * arm00;
        rb = b1hsY * arm12 + b1hsZ * arm11;
        if (Math.abs(tax * rm20 - taz * rm00) > ra + rb) return false;
        // Test axis L = A1 x B1
        ra = b0hsX * arm21 + b0hsZ * arm01;
        rb = b1hsX * arm12 + b1hsZ * arm10;
        if (Math.abs(tax * rm21 - taz * rm01) > ra + rb) return false;
        // Test axis L = A1 x B2
        ra = b0hsX * arm22 + b0hsZ * arm02;
        rb = b1hsX * arm11 + b1hsY * arm10;
        if (Math.abs(tax * rm22 - taz * rm02) > ra + rb) return false;
        // Test axis L = A2 x B0
        ra = b0hsX * arm10 + b0hsY * arm00;
        rb = b1hsY * arm22 + b1hsZ * arm21;
        if (Math.abs(tay * rm00 - tax * rm10) > ra + rb) return false;
        // Test axis L = A2 x B1
        ra = b0hsX * arm11 + b0hsY * arm01;
        rb = b1hsX * arm22 + b1hsZ * arm20;
        if (Math.abs(tay * rm01 - tax * rm11) > ra + rb) return false;
        // Test axis L = A2 x B2
        ra = b0hsX * arm12 + b0hsY * arm02;
        rb = b1hsX * arm21 + b1hsY * arm20;
        if (Math.abs(tay * rm02 - tax * rm12) > ra + rb) return false;
        // Since no separating axis is found, the OBBs must be intersecting
        return true;
    }

    /**
     * Test whether the one sphere with center <code>(aX, aY, aZ)</code> and square radius <code>radiusSquaredA</code> intersects the other
     * sphere with center <code>(bX, bY, bZ)</code> and square radius <code>radiusSquaredB</code>, and store the center of the circle of
     * intersection in the <code>(x, y, z)</code> components of the supplied vector and the radius of that circle in the w component.
     * <p>
     * The normal vector of the circle of intersection can simply be obtained by subtracting the center of either sphere from the other.
     * <p>
     * <code>radiusSquaredA</code> and <code>radiusSquaredB</code> are squared radii, not radii. The w component
     * receives the circle's radius, not its square. Concentric spheres (equal centers) always give <code>false</code>,
     * even with equal radii.
     * <p>
     * Reference: <a href="http://gamedev.stackexchange.com/questions/75756/sphere-sphere-intersection-and-circle-sphere-intersection">http://gamedev.stackexchange.com</a>
     * 
     * @param aX
     *              the x coordinate of the first sphere's center
     * @param aY
     *              the y coordinate of the first sphere's center
     * @param aZ
     *              the z coordinate of the first sphere's center
     * @param radiusSquaredA
     *              the square of the first sphere's radius
     * @param bX
     *              the x coordinate of the second sphere's center
     * @param bY
     *              the y coordinate of the second sphere's center
     * @param bZ
     *              the z coordinate of the second sphere's center
     * @param radiusSquaredB
     *              the square of the second sphere's radius
     * @param centerAndRadiusOfIntersectionCircle
     *              will hold the center of the circle of intersection in the <code>(x, y, z)</code> components and its
     *              radius (not squared) in the w component
     * @return <code>true</code> iff both spheres intersect; <code>false</code> otherwise
     */
    public static boolean intersectSphereSphere(
            float aX, float aY, float aZ, float radiusSquaredA,
            float bX, float bY, float bZ, float radiusSquaredB,
            Vector4f centerAndRadiusOfIntersectionCircle) {
        float dX = bX - aX, dY = bY - aY, dZ = bZ - aZ;
        float distSquared = dX * dX + dY * dY + dZ * dZ;
        float h = 0.5f + (radiusSquaredA - radiusSquaredB) / (2.0f * distSquared);
        float r_i = radiusSquaredA - h * h * distSquared;
        if (r_i >= 0.0f) {
            centerAndRadiusOfIntersectionCircle.x = aX + h * dX;
            centerAndRadiusOfIntersectionCircle.y = aY + h * dY;
            centerAndRadiusOfIntersectionCircle.z = aZ + h * dZ;
            centerAndRadiusOfIntersectionCircle.w = (float) Math.sqrt(r_i);
            return true;
        }
        return false;
    }

    /**
     * Test whether the one sphere with center <code>centerA</code> and square radius <code>radiusSquaredA</code> intersects the other
     * sphere with center <code>centerB</code> and square radius <code>radiusSquaredB</code>, and store the center of the circle of
     * intersection in the <code>(x, y, z)</code> components of the supplied vector and the radius of that circle in the w component.
     * <p>
     * The normal vector of the circle of intersection can simply be obtained by subtracting the center of either sphere from the other.
     * <p>
     * <code>radiusSquaredA</code> and <code>radiusSquaredB</code> are squared radii, not radii. The w component
     * receives the circle's radius, not its square. Concentric spheres (equal centers) always give <code>false</code>,
     * even with equal radii.
     * <p>
     * Reference: <a href="http://gamedev.stackexchange.com/questions/75756/sphere-sphere-intersection-and-circle-sphere-intersection">http://gamedev.stackexchange.com</a>
     * 
     * @param centerA
     *              the first sphere's center
     * @param radiusSquaredA
     *              the square of the first sphere's radius
     * @param centerB
     *              the second sphere's center
     * @param radiusSquaredB
     *              the square of the second sphere's radius
     * @param centerAndRadiusOfIntersectionCircle
     *              will hold the center of the circle of intersection in the <code>(x, y, z)</code> components and its
     *              radius (not squared) in the w component
     * @return <code>true</code> iff both spheres intersect; <code>false</code> otherwise
     */
    public static boolean intersectSphereSphere(Vector3fc centerA, float radiusSquaredA, Vector3fc centerB, float radiusSquaredB, Vector4f centerAndRadiusOfIntersectionCircle) {
        return intersectSphereSphere(centerA.x(), centerA.y(), centerA.z(), radiusSquaredA, centerB.x(), centerB.y(), centerB.z(), radiusSquaredB, centerAndRadiusOfIntersectionCircle);
    }

    /**
     * Test whether the given sphere with center <code>(sX, sY, sZ)</code> intersects the triangle given by its three vertices, and if they intersect
     * store the point of intersection into <code>result</code>.
     * <p>
     * This method also returns whether the point of intersection is on one of the triangle's vertices, edges or on the face.
     * <p>
     * <code>sR</code> is the radius itself, not its square.
     * <p>
     * Reference: Book "Real-Time Collision Detection" chapter 5.2.7 "Testing Sphere Against Triangle"
     * 
     * @param sX
     *          the x coordinate of the sphere's center
     * @param sY
     *          the y coordinate of the sphere's center
     * @param sZ
     *          the z coordinate of the sphere's center
     * @param sR
     *          the sphere's radius (not squared)
     * @param v0X
     *          the x coordinate of the first vertex of the triangle
     * @param v0Y
     *          the y coordinate of the first vertex of the triangle
     * @param v0Z
     *          the z coordinate of the first vertex of the triangle
     * @param v1X
     *          the x coordinate of the second vertex of the triangle
     * @param v1Y
     *          the y coordinate of the second vertex of the triangle
     * @param v1Z
     *          the z coordinate of the second vertex of the triangle
     * @param v2X
     *          the x coordinate of the third vertex of the triangle
     * @param v2Y
     *          the y coordinate of the third vertex of the triangle
     * @param v2Z
     *          the z coordinate of the third vertex of the triangle
     * @param result
     *          will hold the point of intersection
     * @return one of {@link #POINT_ON_TRIANGLE_VERTEX_0}, {@link #POINT_ON_TRIANGLE_VERTEX_1}, {@link #POINT_ON_TRIANGLE_VERTEX_2},
     *                {@link #POINT_ON_TRIANGLE_EDGE_01}, {@link #POINT_ON_TRIANGLE_EDGE_12}, {@link #POINT_ON_TRIANGLE_EDGE_20} or
     *                {@link #POINT_ON_TRIANGLE_FACE} or <code>0</code>
     */
    public static int intersectSphereTriangle(
            float sX, float sY, float sZ, float sR,
            float v0X, float v0Y, float v0Z,
            float v1X, float v1Y, float v1Z,
            float v2X, float v2Y, float v2Z,
            Vector3f result) {
        int closest = findClosestPointOnTriangle(v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, sX, sY, sZ, result);
        float vX = result.x - sX, vY = result.y - sY, vZ = result.z - sZ;
        float dot = vX * vX + vY * vY + vZ * vZ;
        if (dot <= sR * sR) {
            return closest;
        }
        return 0;
    }

    /**
     * Test whether the one sphere with center <code>(aX, aY, aZ)</code> and square radius <code>radiusSquaredA</code> intersects the other
     * sphere with center <code>(bX, bY, bZ)</code> and square radius <code>radiusSquaredB</code>.
     * <p>
     * "Intersects" means the two sphere <em>surfaces</em> meet (there is a circle of intersection, see
     * {@link #intersectSphereSphere}); a sphere strictly inside the other does not count. For overlapping volumes test
     * {@code distance² <= (rA + rB)²}.
     * <p>
     * <code>radiusSquaredA</code> and <code>radiusSquaredB</code> are squared radii, not radii.
     * <p>
     * Reference: <a href="http://gamedev.stackexchange.com/questions/75756/sphere-sphere-intersection-and-circle-sphere-intersection">http://gamedev.stackexchange.com</a>
     * 
     * @param aX
     *              the x coordinate of the first sphere's center
     * @param aY
     *              the y coordinate of the first sphere's center
     * @param aZ
     *              the z coordinate of the first sphere's center
     * @param radiusSquaredA
     *              the square of the first sphere's radius
     * @param bX
     *              the x coordinate of the second sphere's center
     * @param bY
     *              the y coordinate of the second sphere's center
     * @param bZ
     *              the z coordinate of the second sphere's center
     * @param radiusSquaredB
     *              the square of the second sphere's radius
     * @return <code>true</code> iff both spheres intersect; <code>false</code> otherwise
     */
    public static boolean testSphereSphere(
            float aX, float aY, float aZ, float radiusSquaredA,
            float bX, float bY, float bZ, float radiusSquaredB) {
        float dX = bX - aX, dY = bY - aY, dZ = bZ - aZ;
        float distSquared = dX * dX + dY * dY + dZ * dZ;
        if (distSquared == 0.0f) { // concentric: the surfaces meet only if the radii are equal (0/0 before 2.4.0)
            return radiusSquaredA == radiusSquaredB;
        }
        float h = 0.5f + (radiusSquaredA - radiusSquaredB) / (2.0f * distSquared);
        float r_i = radiusSquaredA - h * h * distSquared;
        return r_i >= 0.0f;
    }

    /**
     * Test whether the one sphere with center <code>centerA</code> and square radius <code>radiusSquaredA</code> intersects the other
     * sphere with center <code>centerB</code> and square radius <code>radiusSquaredB</code>.
     * <p>
     * "Intersects" means the two sphere <em>surfaces</em> meet; a sphere strictly inside the other does not count.
     * <p>
     * <code>radiusSquaredA</code> and <code>radiusSquaredB</code> are squared radii, not radii.
     * <p>
     * Reference: <a href="http://gamedev.stackexchange.com/questions/75756/sphere-sphere-intersection-and-circle-sphere-intersection">http://gamedev.stackexchange.com</a>
     * 
     * @param centerA
     *              the first sphere's center
     * @param radiusSquaredA
     *              the square of the first sphere's radius
     * @param centerB
     *              the second sphere's center
     * @param radiusSquaredB
     *              the square of the second sphere's radius
     * @return <code>true</code> iff both spheres intersect; <code>false</code> otherwise
     */
    public static boolean testSphereSphere(Vector3fc centerA, float radiusSquaredA, Vector3fc centerB, float radiusSquaredB) {
        return testSphereSphere(centerA.x(), centerA.y(), centerA.z(), radiusSquaredA, centerB.x(), centerB.y(), centerB.z(), radiusSquaredB);
    }

    /**
     * Determine the signed distance of the given point <code>(pointX, pointY, pointZ)</code> to the plane specified via its general plane equation
     * <i>a*x + b*y + c*z + d = 0</i>.
     * 
     * @param pointX
     *              the x coordinate of the point
     * @param pointY
     *              the y coordinate of the point
     * @param pointZ
     *              the z coordinate of the point
     * @param a
     *              the x factor in the plane equation
     * @param b
     *              the y factor in the plane equation
     * @param c
     *              the z factor in the plane equation
     * @param d
     *              the constant in the plane equation
     * @return the distance between the point and the plane
     */
    public static float distancePointPlane(float pointX, float pointY, float pointZ, float a, float b, float c, float d) {
        float denom = (float) Math.sqrt(a * a + b * b + c * c);
        return (a * pointX + b * pointY + c * pointZ + d) / denom;
    }

    /**
     * Determine the signed distance of the given point <code>(pointX, pointY, pointZ)</code> to the plane of the triangle specified by its three points
     * <code>(v0X, v0Y, v0Z)</code>, <code>(v1X, v1Y, v1Z)</code> and <code>(v2X, v2Y, v2Z)</code>.
     * <p>
     * If the point lies on the front-facing side of the triangle's plane, that is, if the triangle has counter-clockwise winding order
     * as seen from the point, then this method returns a positive number.
     * 
     * @param pointX
     *          the x coordinate of the point
     * @param pointY
     *          the y coordinate of the point
     * @param pointZ
     *          the z coordinate of the point
     * @param v0X
     *          the x coordinate of the first vertex of the triangle
     * @param v0Y
     *          the y coordinate of the first vertex of the triangle
     * @param v0Z
     *          the z coordinate of the first vertex of the triangle
     * @param v1X
     *          the x coordinate of the second vertex of the triangle
     * @param v1Y
     *          the y coordinate of the second vertex of the triangle
     * @param v1Z
     *          the z coordinate of the second vertex of the triangle
     * @param v2X
     *          the x coordinate of the third vertex of the triangle
     * @param v2Y
     *          the y coordinate of the third vertex of the triangle
     * @param v2Z
     *          the z coordinate of the third vertex of the triangle
     * @return the signed distance between the point and the plane of the triangle
     */
    public static float distancePointPlane(float pointX, float pointY, float pointZ,
            float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y, float v2Z) {
        float v1Y0Y = v1Y - v0Y;
        float v2Z0Z = v2Z - v0Z;
        float v2Y0Y = v2Y - v0Y;
        float v1Z0Z = v1Z - v0Z;
        float v2X0X = v2X - v0X;
        float v1X0X = v1X - v0X;
        float a = v1Y0Y * v2Z0Z - v2Y0Y * v1Z0Z;
        float b = v1Z0Z * v2X0X - v2Z0Z * v1X0X;
        float c = v1X0X * v2Y0Y - v2X0X * v1Y0Y;
        float d = -(a * v0X + b * v0Y + c * v0Z);
        return distancePointPlane(pointX, pointY, pointZ, a, b, c, d);
    }

    /**
     * Test whether the ray with given origin <code>(originX, originY, originZ)</code> and direction <code>(dirX, dirY, dirZ)</code> intersects the plane
     * containing the given point <code>(pointX, pointY, pointZ)</code> and having the normal <code>(normalX, normalY, normalZ)</code>, and return the
     * value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the intersection point.
     * <p>
     * This method returns <code>-1.0</code> if the ray does not intersect the plane, because it is either parallel to the plane or its direction points
     * away from the plane or the ray's origin is on the <i>negative</i> side of the plane (i.e. the plane's normal points away from the ray's origin).
     * <p>
     * Reference: <a href="https://www.siggraph.org/education/materials/HyperGraph/raytrace/rayplane_intersection.htm">https://www.siggraph.org/</a>
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param originZ
     *              the z coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's direction
     * @param dirY
     *              the y coordinate of the ray's direction
     * @param dirZ
     *              the z coordinate of the ray's direction
     * @param pointX
     *              the x coordinate of a point on the plane
     * @param pointY
     *              the y coordinate of a point on the plane
     * @param pointZ
     *              the z coordinate of a point on the plane
     * @param normalX
     *              the x coordinate of the plane's normal
     * @param normalY
     *              the y coordinate of the plane's normal
     * @param normalZ
     *              the z coordinate of the plane's normal
     * @param epsilon
     *              some small epsilon for when the ray is parallel to the plane
     * @return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the intersection point, if the ray
     *         intersects the plane; <code>-1.0</code> otherwise
     */
    public static float intersectRayPlane(float originX, float originY, float originZ, float dirX, float dirY, float dirZ,
            float pointX, float pointY, float pointZ, float normalX, float normalY, float normalZ, float epsilon) {
        float denom = normalX * dirX + normalY * dirY + normalZ * dirZ;
        if (denom < epsilon && denom != 0.0f) { // parallel rays never hit (Infinity before 2.4.0)
            float t = ((pointX - originX) * normalX + (pointY - originY) * normalY + (pointZ - originZ) * normalZ) / denom;
            if (t >= 0.0f)
                return t;
        }
        return -1.0f;
    }

    /**
     * Test whether the ray with given <code>origin</code> and direction <code>dir</code> intersects the plane
     * containing the given <code>point</code> and having the given <code>normal</code>, and return the
     * value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the intersection point.
     * <p>
     * This method returns <code>-1.0</code> if the ray does not intersect the plane, because it is either parallel to the plane or its direction points
     * away from the plane or the ray's origin is on the <i>negative</i> side of the plane (i.e. the plane's normal points away from the ray's origin).
     * <p>
     * Reference: <a href="https://www.siggraph.org/education/materials/HyperGraph/raytrace/rayplane_intersection.htm">https://www.siggraph.org/</a>
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's direction
     * @param point
     *              a point on the plane
     * @param normal
     *              the plane's normal
     * @param epsilon
     *              some small epsilon for when the ray is parallel to the plane
     * @return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the intersection point, if the ray
     *         intersects the plane; <code>-1.0</code> otherwise
     */
    public static float intersectRayPlane(Vector3fc origin, Vector3fc dir, Vector3fc point, Vector3fc normal, float epsilon) {
        return intersectRayPlane(origin.x(), origin.y(), origin.z(), dir.x(), dir.y(), dir.z(), point.x(), point.y(), point.z(), normal.x(), normal.y(), normal.z(), epsilon);
    }

    /**
     * Test whether the ray with given origin <code>(originX, originY, originZ)</code> and direction <code>(dirX, dirY, dirZ)</code> intersects the plane
     * given as the general plane equation <i>a*x + b*y + c*z + d = 0</i>, and return the
     * value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the intersection point.
     * <p>
     * This method returns <code>-1.0</code> if the ray does not intersect the plane, because it is either parallel to the plane or its direction points
     * away from the plane or the ray's origin is on the <i>negative</i> side of the plane (i.e. the plane's normal points away from the ray's origin).
     * <p>
     * Reference: <a href="https://www.siggraph.org/education/materials/HyperGraph/raytrace/rayplane_intersection.htm">https://www.siggraph.org/</a>
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param originZ
     *              the z coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's direction
     * @param dirY
     *              the y coordinate of the ray's direction
     * @param dirZ
     *              the z coordinate of the ray's direction
     * @param a
     *              the x factor in the plane equation
     * @param b
     *              the y factor in the plane equation
     * @param c
     *              the z factor in the plane equation
     * @param d
     *              the constant in the plane equation
     * @param epsilon
     *              some small epsilon for when the ray is parallel to the plane
     * @return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the intersection point, if the ray
     *         intersects the plane; <code>-1.0</code> otherwise
     */
    public static float intersectRayPlane(float originX, float originY, float originZ, float dirX, float dirY, float dirZ,
            float a, float b, float c, float d, float epsilon) {
        float denom = a * dirX + b * dirY + c * dirZ;
        if (denom < epsilon && denom != 0.0f) { // parallel rays never hit (Infinity before 2.4.0)
            float t = -(a * originX + b * originY + c * originZ + d) / denom;
            if (t >= 0.0f)
                return t;
        }
        return -1.0f;
    }

    /**
     * Test whether the axis-aligned box with minimum corner <code>(minX, minY, minZ)</code> and maximum corner <code>(maxX, maxY, maxZ)</code>
     * intersects the sphere with the given center <code>(centerX, centerY, centerZ)</code> and square radius <code>radiusSquared</code>.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius.
     * <p>
     * Reference: <a href="http://stackoverflow.com/questions/4578967/cube-sphere-intersection-test#answer-4579069">http://stackoverflow.com</a>
     * 
     * @param minX
     *          the x coordinate of the minimum corner of the axis-aligned box
     * @param minY
     *          the y coordinate of the minimum corner of the axis-aligned box
     * @param minZ
     *          the z coordinate of the minimum corner of the axis-aligned box
     * @param maxX
     *          the x coordinate of the maximum corner of the axis-aligned box
     * @param maxY
     *          the y coordinate of the maximum corner of the axis-aligned box
     * @param maxZ
     *          the z coordinate of the maximum corner of the axis-aligned box
     * @param centerX
     *          the x coordinate of the sphere's center
     * @param centerY
     *          the y coordinate of the sphere's center
     * @param centerZ
     *          the z coordinate of the sphere's center
     * @param radiusSquared
     *          the square of the sphere's radius
     * @return <code>true</code> iff the axis-aligned box intersects the sphere; <code>false</code> otherwise
     */
    public static boolean testAabSphere(
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            float centerX, float centerY, float centerZ, float radiusSquared) {
        float radius2 = radiusSquared;
        if (centerX < minX) {
            float d = (centerX - minX);
            radius2 -= d * d;
        } else if (centerX > maxX) {
            float d = (centerX - maxX);
            radius2 -= d * d;
        }
        if (centerY < minY) {
            float d = (centerY - minY);
            radius2 -= d * d;
        } else if (centerY > maxY) {
            float d = (centerY - maxY);
            radius2 -= d * d;
        }
        if (centerZ < minZ) {
            float d = (centerZ - minZ);
            radius2 -= d * d;
        } else if (centerZ > maxZ) {
            float d = (centerZ - maxZ);
            radius2 -= d * d;
        }
        return radius2 >= 0.0f;
    }

    /**
     * Test whether the axis-aligned box with minimum corner <code>min</code> and maximum corner <code>max</code>
     * intersects the sphere with the given <code>center</code> and square radius <code>radiusSquared</code>.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius.
     * <p>
     * Reference: <a href="http://stackoverflow.com/questions/4578967/cube-sphere-intersection-test#answer-4579069">http://stackoverflow.com</a>
     * 
     * @param min
     *          the minimum corner of the axis-aligned box
     * @param max
     *          the maximum corner of the axis-aligned box
     * @param center
     *          the sphere's center
     * @param radiusSquared
     *          the square of the sphere's radius
     * @return <code>true</code> iff the axis-aligned box intersects the sphere; <code>false</code> otherwise
     */
    public static boolean testAabSphere(Vector3fc min, Vector3fc max, Vector3fc center, float radiusSquared) {
        return testAabSphere(min.x(), min.y(), min.z(), max.x(), max.y(), max.z(), center.x(), center.y(), center.z(), radiusSquared);
    }

    /**
     * Find the point on the given plane which is closest to the specified point <code>(pX, pY, pZ)</code> and store the result in <code>result</code>.
     * 
     * @param aX
     *          the x coordinate of one point on the plane
     * @param aY
     *          the y coordinate of one point on the plane
     * @param aZ
     *          the z coordinate of one point on the plane
     * @param nX
     *          the x coordinate of the unit normal of the plane
     * @param nY
     *          the y coordinate of the unit normal of the plane
     * @param nZ
     *          the z coordinate of the unit normal of the plane
     * @param pX
     *          the x coordinate of the point
     * @param pY
     *          the y coordinate of the point
     * @param pZ
     *          the z coordinate of the point
     * @param result
     *          will hold the result
     * @return result
     */
    public static Vector3f findClosestPointOnPlane(float aX, float aY, float aZ, float nX, float nY, float nZ, float pX, float pY, float pZ, Vector3f result) {
        float d = -(nX * aX + nY * aY + nZ * aZ);
        float t = nX * pX + nY * pY + nZ * pZ + d;
        result.x = pX - t * nX;
        result.y = pY - t * nY;
        result.z = pZ - t * nZ;
        return result;
    }

    /**
     * Find the point on the given line segment which is closest to the specified point <code>(pX, pY, pZ)</code>, and store the result in <code>result</code>.
     * 
     * @param aX
     *          the x coordinate of the first end point of the line segment
     * @param aY
     *          the y coordinate of the first end point of the line segment
     * @param aZ
     *          the z coordinate of the first end point of the line segment
     * @param bX
     *          the x coordinate of the second end point of the line segment
     * @param bY
     *          the y coordinate of the second end point of the line segment
     * @param bZ
     *          the z coordinate of the second end point of the line segment
     * @param pX
     *          the x coordinate of the point
     * @param pY
     *          the y coordinate of the point
     * @param pZ
     *          the z coordinate of the point
     * @param result
     *          will hold the result
     * @return result
     */
    public static Vector3f findClosestPointOnLineSegment(float aX, float aY, float aZ, float bX, float bY, float bZ, float pX, float pY, float pZ, Vector3f result) {
        float abX = bX - aX, abY = bY - aY, abZ = bZ - aZ;
        float len2 = abX * abX + abY * abY + abZ * abZ;
        // a zero-length segment is the point a (before 2.4.0 this divided by zero and gave NaN)
        float t = len2 > 0.0f ? ((pX - aX) * abX + (pY - aY) * abY + (pZ - aZ) * abZ) / len2 : 0.0f;
        if (t < 0.0f) t = 0.0f;
        if (t > 1.0f) t = 1.0f;
        result.x = aX + t * abX;
        result.y = aY + t * abY;
        result.z = aZ + t * abZ;
        return result;
    }

    /**
     * Find the closest points on the two line segments, store the point on the first line segment in <code>resultA</code> and 
     * the point on the second line segment in <code>resultB</code>, and return the square distance between both points.
     * <p>
     * Reference: Book "Real-Time Collision Detection" chapter 5.1.9 "Closest Points of Two Line Segments"
     * 
     * @param a0X
     *          the x coordinate of the first line segment's first end point
     * @param a0Y
     *          the y coordinate of the first line segment's first end point
     * @param a0Z
     *          the z coordinate of the first line segment's first end point
     * @param a1X
     *          the x coordinate of the first line segment's second end point
     * @param a1Y
     *          the y coordinate of the first line segment's second end point
     * @param a1Z
     *          the z coordinate of the first line segment's second end point
     * @param b0X
     *          the x coordinate of the second line segment's first end point
     * @param b0Y
     *          the y coordinate of the second line segment's first end point
     * @param b0Z
     *          the z coordinate of the second line segment's first end point
     * @param b1X
     *          the x coordinate of the second line segment's second end point
     * @param b1Y
     *          the y coordinate of the second line segment's second end point
     * @param b1Z
     *          the z coordinate of the second line segment's second end point
     * @param resultA
     *          will hold the point on the first line segment
     * @param resultB
     *          will hold the point on the second line segment
     * @return the square distance between the two closest points
     */
    public static float findClosestPointsLineSegments(
            float a0X, float a0Y, float a0Z, float a1X, float a1Y, float a1Z,
            float b0X, float b0Y, float b0Z, float b1X, float b1Y, float b1Z,
            Vector3f resultA, Vector3f resultB) {
        float d1x = a1X - a0X, d1y = a1Y - a0Y, d1z = a1Z - a0Z;
        float d2x = b1X - b0X, d2y = b1Y - b0Y, d2z = b1Z - b0Z;
        float rX = a0X - b0X, rY = a0Y - b0Y, rZ = a0Z - b0Z;
        float a = d1x * d1x + d1y * d1y + d1z * d1z;
        float invA = 1.0f / a;
        float e = d2x * d2x + d2y * d2y + d2z * d2z;
        float f = d2x * rX + d2y * rY + d2z * rZ;
        // a segment is a point only relative to the problem's scale (an absolute 1e-5 on squared length before 2.4.0
        // treated segments shorter than about 3e-3 as points)
        float EPSILON = 1E-12f * Math.max(Math.max(a, e), rX * rX + rY * rY + rZ * rZ);
        float s, t;
        if (a <= EPSILON && e <= EPSILON) {
            // Both segments degenerate into points
            resultA.set(a0X, a0Y, a0Z);
            resultB.set(b0X, b0Y, b0Z);
            return resultA.distanceSquared(resultB);
        }
        if (a <= EPSILON) {
            // First segment degenerates into a point
            s = 0.0f;
            t = f / e;
            t = Math.min(Math.max(t, 0.0f), 1.0f);
        } else {
            float c = d1x * rX + d1y * rY + d1z * rZ;
            if (e <= EPSILON) {
                // Second segment degenerates into a point
                t = 0.0f;
                s = Math.min(Math.max(-c * invA, 0.0f), 1.0f);
            } else {
                // The general nondegenerate case starts here
                float b = d1x * d2x + d1y * d2y + d1z * d2z;
                float denom = a * e - b * b;
                // If segments not parallel, compute closest point on L1 to L2 and
                // clamp to segment S1. Else pick arbitrary s (here 0)
                if (denom != 0.0)
                    s = Math.min(Math.max((b*f - c*e) / denom, 0.0f), 1.0f);
                else
                    s = 0.0f;
                // Compute point on L2 closest to S1(s) using
                // t = Dot((P1 + D1*s) - P2,D2) / Dot(D2,D2) = (b*s + f) / e
                t = (b * s + f) / e;
                // If t in [0,1] done. Else clamp t, recompute s for the new value
                // of t using s = Dot((P2 + D2*t) - P1,D1) / Dot(D1,D1)= (t*b - c) / a
                // and clamp s to [0, 1]
                if (t < 0.0) {
                    t = 0.0f;
                    s = Math.min(Math.max(-c * invA, 0.0f), 1.0f);
                } else if (t > 1.0) {
                    t = 1.0f;
                    s = Math.min(Math.max((b - c) * invA, 0.0f), 1.0f);
                }
            }
        }
        resultA.set(a0X + d1x * s, a0Y + d1y * s, a0Z + d1z * s);
        resultB.set(b0X + d2x * t, b0Y + d2y * t, b0Z + d2z * t);
        float dX = resultA.x - resultB.x, dY = resultA.y - resultB.y, dZ = resultA.z - resultB.z;
        return dX*dX + dY*dY + dZ*dZ;
    }

    /**
     * Find the closest points on a line segment and a triangle.
     * <p>
     * Reference: Book "Real-Time Collision Detection" chapter 5.1.10 "Closest Points of a Line Segment and a Triangle"
     * 
     * @param aX
     *          the x coordinate of the line segment's first end point
     * @param aY
     *          the y coordinate of the line segment's first end point
     * @param aZ
     *          the z coordinate of the line segment's first end point
     * @param bX
     *          the x coordinate of the line segment's second end point
     * @param bY
     *          the y coordinate of the line segment's second end point
     * @param bZ
     *          the z coordinate of the line segment's second end point
     * @param v0X
     *          the x coordinate of the triangle's first vertex
     * @param v0Y
     *          the y coordinate of the triangle's first vertex
     * @param v0Z
     *          the z coordinate of the triangle's first vertex
     * @param v1X
     *          the x coordinate of the triangle's second vertex
     * @param v1Y
     *          the y coordinate of the triangle's second vertex
     * @param v1Z
     *          the z coordinate of the triangle's second vertex
     * @param v2X
     *          the x coordinate of the triangle's third vertex
     * @param v2Y
     *          the y coordinate of the triangle's third vertex
     * @param v2Z
     *          the z coordinate of the triangle's third vertex
     * @param lineSegmentResult
     *          will hold the closest point on the line segment
     * @param triangleResult
     *          will hold the closest point on the triangle
     * @return the square distance of the closest points
     */
    public static float findClosestPointsLineSegmentTriangle(
            float aX, float aY, float aZ, float bX, float bY, float bZ,
            float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y, float v2Z,
            Vector3f lineSegmentResult, Vector3f triangleResult) {
        // a segment that crosses the triangle's interior is at distance 0 (the edge and end-point tests below
        // miss this case; inherited from JOML)
        float e1X = v1X - v0X, e1Y = v1Y - v0Y, e1Z = v1Z - v0Z;
        float e2X = v2X - v0X, e2Y = v2Y - v0Y, e2Z = v2Z - v0Z;
        float nX = e1Y * e2Z - e1Z * e2Y, nY = e1Z * e2X - e1X * e2Z, nZ = e1X * e2Y - e1Y * e2X;
        float dA = nX * (aX - v0X) + nY * (aY - v0Y) + nZ * (aZ - v0Z);
        float dB = nX * (bX - v0X) + nY * (bY - v0Y) + nZ * (bZ - v0Z);
        if ((dA <= 0.0f && dB >= 0.0f || dA >= 0.0f && dB <= 0.0f) && dA != dB) {
            float t = dA / (dA - dB);
            float pX = aX + t * (bX - aX), pY = aY + t * (bY - aY), pZ = aZ + t * (bZ - aZ);
            if (testPointInTriangle(pX, pY, pZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z)) {
                lineSegmentResult.set(pX, pY, pZ);
                triangleResult.set(pX, pY, pZ);
                return 0.0f;
            }
        }
        float min, d;
        float minlsX, minlsY, minlsZ, mintX, mintY, mintZ;
        // AB -> V0V1
        d = findClosestPointsLineSegments(aX, aY, aZ, bX, bY, bZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, lineSegmentResult, triangleResult);
        min = d;
        minlsX = lineSegmentResult.x; minlsY = lineSegmentResult.y; minlsZ = lineSegmentResult.z;
        mintX = triangleResult.x; mintY = triangleResult.y; mintZ = triangleResult.z;
        // AB -> V1V2
        d = findClosestPointsLineSegments(aX, aY, aZ, bX, bY, bZ, v1X, v1Y, v1Z, v2X, v2Y, v2Z, lineSegmentResult, triangleResult);
        if (d < min) {
            min = d;
            minlsX = lineSegmentResult.x; minlsY = lineSegmentResult.y; minlsZ = lineSegmentResult.z;
            mintX = triangleResult.x; mintY = triangleResult.y; mintZ = triangleResult.z;
        }
        // AB -> V2V0
        d = findClosestPointsLineSegments(aX, aY, aZ, bX, bY, bZ, v2X, v2Y, v2Z, v0X, v0Y, v0Z, lineSegmentResult, triangleResult);
        if (d < min) {
            min = d;
            minlsX = lineSegmentResult.x; minlsY = lineSegmentResult.y; minlsZ = lineSegmentResult.z;
            mintX = triangleResult.x; mintY = triangleResult.y; mintZ = triangleResult.z;
        }
        // segment endpoint A and plane of triangle (when A projects inside V0V1V2)
        boolean computed = false;
        float a = Float.NaN, b = Float.NaN, c = Float.NaN, nd = Float.NaN;
        if (testPointInTriangle(aX, aY, aZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z)) {
            float v1Y0Y = v1Y - v0Y;
            float v2Z0Z = v2Z - v0Z;
            float v2Y0Y = v2Y - v0Y;
            float v1Z0Z = v1Z - v0Z;
            float v2X0X = v2X - v0X;
            float v1X0X = v1X - v0X;
            a = v1Y0Y * v2Z0Z - v2Y0Y * v1Z0Z;
            b = v1Z0Z * v2X0X - v2Z0Z * v1X0X;
            c = v1X0X * v2Y0Y - v2X0X * v1Y0Y;
            computed = true;
            float invLen = Math.invsqrt(a*a + b*b + c*c);
            a *= invLen; b *= invLen; c *= invLen;
            nd = -(a * v0X + b * v0Y + c * v0Z);
            d = (a * aX + b * aY + c * aZ + nd);
            float l = d;
            d *= d;
            if (d < min) {
                min = d;
                minlsX = aX; minlsY = aY; minlsZ = aZ;
                mintX = aX - a*l; mintY = aY - b*l; mintZ = aZ - c*l;
            }
        }
        // segment endpoint B and plane of triangle (when B projects inside V0V1V2)
        if (testPointInTriangle(bX, bY, bZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z)) {
            if (!computed) {
                float v1Y0Y = v1Y - v0Y;
                float v2Z0Z = v2Z - v0Z;
                float v2Y0Y = v2Y - v0Y;
                float v1Z0Z = v1Z - v0Z;
                float v2X0X = v2X - v0X;
                float v1X0X = v1X - v0X;
                a = v1Y0Y * v2Z0Z - v2Y0Y * v1Z0Z;
                b = v1Z0Z * v2X0X - v2Z0Z * v1X0X;
                c = v1X0X * v2Y0Y - v2X0X * v1Y0Y;
                float invLen = Math.invsqrt(a*a + b*b + c*c);
                a *= invLen; b *= invLen; c *= invLen;
                nd = -(a * v0X + b * v0Y + c * v0Z);
            }
            d = (a * bX + b * bY + c * bZ + nd);
            float l = d;
            d *= d;
            if (d < min) {
                min = d;
                minlsX = bX; minlsY = bY; minlsZ = bZ;
                mintX = bX - a*l; mintY = bY - b*l; mintZ = bZ - c*l;
            }
        }
        lineSegmentResult.set(minlsX, minlsY, minlsZ);
        triangleResult.set(mintX, mintY, mintZ);
        return min;
    }

    /**
     * Determine the closest point on the triangle with the given vertices <code>(v0X, v0Y, v0Z)</code>, <code>(v1X, v1Y, v1Z)</code>, <code>(v2X, v2Y, v2Z)</code>
     * between that triangle and the given point <code>(pX, pY, pZ)</code> and store that point into the given <code>result</code>.
     * <p>
     * Additionally, this method returns whether the closest point is a vertex ({@link #POINT_ON_TRIANGLE_VERTEX_0}, {@link #POINT_ON_TRIANGLE_VERTEX_1}, {@link #POINT_ON_TRIANGLE_VERTEX_2})
     * of the triangle, lies on an edge ({@link #POINT_ON_TRIANGLE_EDGE_01}, {@link #POINT_ON_TRIANGLE_EDGE_12}, {@link #POINT_ON_TRIANGLE_EDGE_20})
     * or on the {@link #POINT_ON_TRIANGLE_FACE face} of the triangle.
     * <p>
     * Reference: Book "Real-Time Collision Detection" chapter 5.1.5 "Closest Point on Triangle to Point"
     * 
     * @param v0X
     *          the x coordinate of the first vertex of the triangle
     * @param v0Y
     *          the y coordinate of the first vertex of the triangle
     * @param v0Z
     *          the z coordinate of the first vertex of the triangle
     * @param v1X
     *          the x coordinate of the second vertex of the triangle
     * @param v1Y
     *          the y coordinate of the second vertex of the triangle
     * @param v1Z
     *          the z coordinate of the second vertex of the triangle
     * @param v2X
     *          the x coordinate of the third vertex of the triangle
     * @param v2Y
     *          the y coordinate of the third vertex of the triangle
     * @param v2Z
     *          the z coordinate of the third vertex of the triangle
     * @param pX
     *          the x coordinate of the point
     * @param pY
     *          the y coordinate of the point
     * @param pZ
     *          the y coordinate of the point
     * @param result
     *          will hold the closest point
     * @return one of {@link #POINT_ON_TRIANGLE_VERTEX_0}, {@link #POINT_ON_TRIANGLE_VERTEX_1}, {@link #POINT_ON_TRIANGLE_VERTEX_2},
     *                {@link #POINT_ON_TRIANGLE_EDGE_01}, {@link #POINT_ON_TRIANGLE_EDGE_12}, {@link #POINT_ON_TRIANGLE_EDGE_20} or
     *                {@link #POINT_ON_TRIANGLE_FACE}
     */
    public static int findClosestPointOnTriangle(
            float v0X, float v0Y, float v0Z,
            float v1X, float v1Y, float v1Z,
            float v2X, float v2Y, float v2Z,
            float pX, float pY, float pZ,
            Vector3f result) {
        // face region: the projection of p along the normal lies in the closed triangle (the projected edge-side
        // test); the projection is then the closest point. Otherwise the closest point is on the boundary.
        double nX = ((double) v1Y - v0Y) * ((double) v2Z - v0Z) - ((double) v1Z - v0Z) * ((double) v2Y - v0Y);
        double nY = ((double) v1Z - v0Z) * ((double) v2X - v0X) - ((double) v1X - v0X) * ((double) v2Z - v0Z);
        double nZ = ((double) v1X - v0X) * ((double) v2Y - v0Y) - ((double) v1Y - v0Y) * ((double) v2X - v0X);
        double nn = nX * nX + nY * nY + nZ * nZ;
        if (nn > 0 && Intersectiond.insideTriangle(pX, pY, pZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, nX, nY, nZ)) {
            double d = (((double) pX - v0X) * nX + ((double) pY - v0Y) * nY + ((double) pZ - v0Z) * nZ) / nn;
            result.x = (float) (pX - d * nX);
            result.y = (float) (pY - d * nY);
            result.z = (float) (pZ - d * nZ);
            return POINT_ON_TRIANGLE_FACE;
        }
        double ta = Intersectiond.segmentParam(v0X, v0Y, v0Z, v1X, v1Y, v1Z, pX, pY, pZ);
        double qaX = ta >= 1 ? v1X : v0X + ta * (v1X - v0X);
        double qaY = ta >= 1 ? v1Y : v0Y + ta * (v1Y - v0Y);
        double qaZ = ta >= 1 ? v1Z : v0Z + ta * (v1Z - v0Z);
        double da = (pX - qaX) * (pX - qaX) + (pY - qaY) * (pY - qaY) + (pZ - qaZ) * (pZ - qaZ);
        int ca = ta <= 0 ? POINT_ON_TRIANGLE_VERTEX_0 : ta >= 1 ? POINT_ON_TRIANGLE_VERTEX_1 : POINT_ON_TRIANGLE_EDGE_01;
        double tb = Intersectiond.segmentParam(v1X, v1Y, v1Z, v2X, v2Y, v2Z, pX, pY, pZ);
        double qbX = tb >= 1 ? v2X : v1X + tb * (v2X - v1X);
        double qbY = tb >= 1 ? v2Y : v1Y + tb * (v2Y - v1Y);
        double qbZ = tb >= 1 ? v2Z : v1Z + tb * (v2Z - v1Z);
        double db = (pX - qbX) * (pX - qbX) + (pY - qbY) * (pY - qbY) + (pZ - qbZ) * (pZ - qbZ);
        int cb = tb <= 0 ? POINT_ON_TRIANGLE_VERTEX_1 : tb >= 1 ? POINT_ON_TRIANGLE_VERTEX_2 : POINT_ON_TRIANGLE_EDGE_12;
        double tc = Intersectiond.segmentParam(v2X, v2Y, v2Z, v0X, v0Y, v0Z, pX, pY, pZ);
        double qcX = tc >= 1 ? v0X : v2X + tc * (v0X - v2X);
        double qcY = tc >= 1 ? v0Y : v2Y + tc * (v0Y - v2Y);
        double qcZ = tc >= 1 ? v0Z : v2Z + tc * (v0Z - v2Z);
        double dc = (pX - qcX) * (pX - qcX) + (pY - qcY) * (pY - qcY) + (pZ - qcZ) * (pZ - qcZ);
        int cc = tc <= 0 ? POINT_ON_TRIANGLE_VERTEX_2 : tc >= 1 ? POINT_ON_TRIANGLE_VERTEX_0 : POINT_ON_TRIANGLE_EDGE_20;
        if (da <= db && da <= dc) {
            result.x = (float) qaX;
            result.y = (float) qaY;
            result.z = (float) qaZ;
            return ca;
        }
        if (db <= dc) {
            result.x = (float) qbX;
            result.y = (float) qbY;
            result.z = (float) qbZ;
            return cb;
        }
        result.x = (float) qcX;
        result.y = (float) qcY;
        result.z = (float) qcZ;
        return cc;
    }

    /**
     * Determine the closest point on the triangle with the vertices <code>v0</code>, <code>v1</code>, <code>v2</code>
     * between that triangle and the given point <code>p</code> and store that point into the given <code>result</code>.
     * <p>
     * Additionally, this method returns whether the closest point is a vertex ({@link #POINT_ON_TRIANGLE_VERTEX_0}, {@link #POINT_ON_TRIANGLE_VERTEX_1}, {@link #POINT_ON_TRIANGLE_VERTEX_2})
     * of the triangle, lies on an edge ({@link #POINT_ON_TRIANGLE_EDGE_01}, {@link #POINT_ON_TRIANGLE_EDGE_12}, {@link #POINT_ON_TRIANGLE_EDGE_20})
     * or on the {@link #POINT_ON_TRIANGLE_FACE face} of the triangle.
     * <p>
     * Reference: Book "Real-Time Collision Detection" chapter 5.1.5 "Closest Point on Triangle to Point"
     * 
     * @param v0
     *          the first vertex of the triangle
     * @param v1
     *          the second vertex of the triangle
     * @param v2
     *          the third vertex of the triangle
     * @param p
     *          the point
     * @param result
     *          will hold the closest point
     * @return one of {@link #POINT_ON_TRIANGLE_VERTEX_0}, {@link #POINT_ON_TRIANGLE_VERTEX_1}, {@link #POINT_ON_TRIANGLE_VERTEX_2},
     *                {@link #POINT_ON_TRIANGLE_EDGE_01}, {@link #POINT_ON_TRIANGLE_EDGE_12}, {@link #POINT_ON_TRIANGLE_EDGE_20} or
     *                {@link #POINT_ON_TRIANGLE_FACE}
     */
    public static int findClosestPointOnTriangle(Vector3fc v0, Vector3fc v1, Vector3fc v2, Vector3fc p, Vector3f result) {
        return findClosestPointOnTriangle(v0.x(), v0.y(), v0.z(), v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z(), p.x(), p.y(), p.z(), result);
    }

    /**
     * Find the point on a given rectangle, specified via three of its corners, which is closest to the specified point
     * <code>(pX, pY, pZ)</code> and store the result into <code>res</code>.
     * <p>
     * Reference: Book "Real-Time Collision Detection" chapter 5.1.4.2 "Closest Point on 3D Rectangle to Point"
     * 
     * @param aX
     *          the x coordinate of the first corner point of the rectangle
     * @param aY
     *          the y coordinate of the first corner point of the rectangle
     * @param aZ
     *          the z coordinate of the first corner point of the rectangle
     * @param bX
     *          the x coordinate of the second corner point of the rectangle 
     * @param bY
     *          the y coordinate of the second corner point of the rectangle
     * @param bZ
     *          the z coordinate of the second corner point of the rectangle
     * @param cX
     *          the x coordinate of the third corner point of the rectangle
     * @param cY
     *          the y coordinate of the third corner point of the rectangle
     * @param cZ
     *          the z coordinate of the third corner point of the rectangle
     * @param pX
     *          the x coordinate of the point
     * @param pY
     *          the y coordinate of the point
     * @param pZ
     *          the z coordinate of the point
     * @param res
     *          will hold the result
     * @return res
     */
    public static Vector3f findClosestPointOnRectangle(
            float aX, float aY, float aZ,
            float bX, float bY, float bZ,
            float cX, float cY, float cZ,
            float pX, float pY, float pZ, Vector3f res) {
        float abX = bX - aX, abY = bY - aY, abZ = bZ - aZ;
        float acX = cX - aX, acY = cY - aY, acZ = cZ - aZ;
        float dX = pX - aX, dY = pY - aY, dZ = pZ - aZ;
        float qX = aX, qY = aY, qZ = aZ;
        float dist = dX * abX + dY * abY + dZ * abZ;
        float maxdist = abX * abX + abY * abY + abZ * abZ;
        float invMaxdist = 1.0f / maxdist;
        float distTimesInvMaxDist;
        if (dist >= maxdist) {
            qX += abX;
            qY += abY;
            qZ += abZ;
        } else if (dist > 0.0f) {
            distTimesInvMaxDist = dist * invMaxdist;
            qX += distTimesInvMaxDist * abX;
            qY += distTimesInvMaxDist * abY;
            qZ += distTimesInvMaxDist * abZ;
        }
        dist = dX * acX + dY * acY + dZ * acZ;
        maxdist = acX * acX + acY * acY + acZ * acZ;
        invMaxdist = 1.0f / maxdist;
        if (dist >= maxdist) {
            qX += acX;
            qY += acY;
            qZ += acZ;
        } else if (dist > 0.0f) {
            distTimesInvMaxDist = dist * invMaxdist;
            qX += distTimesInvMaxDist * acX;
            qY += distTimesInvMaxDist * acY;
            qZ += distTimesInvMaxDist * acZ;
        }
        res.x = qX;
        res.y = qY;
        res.z = qZ;
        return res;
    }

    /**
     * Determine the point of intersection between a sphere with the given center <code>(centerX, centerY, centerZ)</code> and <code>radius</code> moving
     * with the given velocity <code>(velX, velY, velZ)</code> and the triangle specified via its three vertices <code>(v0X, v0Y, v0Z)</code>, <code>(v1X, v1Y, v1Z)</code>, <code>(v2X, v2Y, v2Z)</code>.
     * <p>
     * The result is the first contact for times <code>t</code> in <code>[0, maxT)</code>, from either side of the triangle
     * (the winding does not matter). If the sphere already touches or overlaps the triangle at <code>t = 0</code>, the
     * time is <code>0</code> and the point is the triangle's closest point to the center. A sphere moving parallel to the
     * plane, or away from it, is handled exactly; contact is inclusive (a grazing sphere touches).
     * <p>
     * <code>radius</code> is the radius itself, not its square.
     * <p>
     * Reference: <a href="http://www.peroxide.dk/papers/collision/collision.pdf">Improved Collision detection and Response</a>
     * 
     * @param centerX
     *              the x coordinate of the sphere's center
     * @param centerY
     *              the y coordinate of the sphere's center
     * @param centerZ
     *              the z coordinate of the sphere's center
     * @param radius
     *              the radius of the sphere (not squared)
     * @param velX
     *              the x component of the velocity of the sphere
     * @param velY
     *              the y component of the velocity of the sphere
     * @param velZ
     *              the z component of the velocity of the sphere
     * @param v0X
     *              the x coordinate of the first triangle vertex
     * @param v0Y
     *              the y coordinate of the first triangle vertex
     * @param v0Z
     *              the z coordinate of the first triangle vertex
     * @param v1X
     *              the x coordinate of the second triangle vertex
     * @param v1Y
     *              the y coordinate of the second triangle vertex
     * @param v1Z
     *              the z coordinate of the second triangle vertex
     * @param v2X
     *              the x coordinate of the third triangle vertex
     * @param v2Y
     *              the y coordinate of the third triangle vertex
     * @param v2Z
     *              the z coordinate of the third triangle vertex
     * @param epsilon
     *              not used: parallel motion is handled exactly (kept for compatibility)
     * @param maxT
     *              the maximum intersection time
     * @param pointAndTime
     *              iff the moving sphere and the triangle intersect, this will hold the point of intersection in the <code>(x, y, z)</code> components
     *              and the time of intersection in the <code>w</code> component
     * @return {@link #POINT_ON_TRIANGLE_FACE} if the intersection point lies on the triangle's face,
     *         or {@link #POINT_ON_TRIANGLE_VERTEX_0}, {@link #POINT_ON_TRIANGLE_VERTEX_1} or {@link #POINT_ON_TRIANGLE_VERTEX_2} if the intersection point is a vertex,
     *         or {@link #POINT_ON_TRIANGLE_EDGE_01}, {@link #POINT_ON_TRIANGLE_EDGE_12} or {@link #POINT_ON_TRIANGLE_EDGE_20} if the intersection point lies on an edge;
     *         or <code>0</code> if no intersection
     */
    public static int intersectSweptSphereTriangle(
            float centerX, float centerY, float centerZ, float radius, float velX, float velY, float velZ,
            float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y, float v2Z,
            float epsilon, float maxT, Vector4f pointAndTime) {
        if (!(maxT > 0.0f)) {
            return 0;
        }
        float nX = (v1Y - v0Y) * (v2Z - v0Z) - (v1Z - v0Z) * (v2Y - v0Y);
        float nY = (v1Z - v0Z) * (v2X - v0X) - (v1X - v0X) * (v2Z - v0Z);
        float nZ = (v1X - v0X) * (v2Y - v0Y) - (v1Y - v0Y) * (v2X - v0X);
        float nLen = Math.sqrt(nX * nX + nY * nY + nZ * nZ);
        float invNLen = nLen > 0.0f ? 1.0f / nLen : 0.0f;
        float sd = ((centerX - v0X) * nX + (centerY - v0Y) * nY + (centerZ - v0Z) * nZ) * invNLen;
        float dot = (velX * nX + velY * nY + velZ * nZ) * invNLen;
        // 1. touching or overlapping at t = 0: report the closest point of the triangle
        if (nLen > 0.0f && insideTriangle(centerX, centerY, centerZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, nX, nY, nZ)) {
            if (sd * sd <= radius * radius) {
                pointAndTime.set(centerX - nX * invNLen * sd, centerY - nY * invNLen * sd, centerZ - nZ * invNLen * sd, 0.0f);
                return POINT_ON_TRIANGLE_FACE;
            }
        } else {
            float best = radius * radius;
            int code = 0;
            float d = dist2(centerX - v0X, centerY - v0Y, centerZ - v0Z);
            if (d <= best) {
                best = d;
                code = POINT_ON_TRIANGLE_VERTEX_0;
            }
            d = dist2(centerX - v1X, centerY - v1Y, centerZ - v1Z);
            if (d <= best) {
                best = d;
                code = POINT_ON_TRIANGLE_VERTEX_1;
            }
            d = dist2(centerX - v2X, centerY - v2Y, centerZ - v2Z);
            if (d <= best) {
                best = d;
                code = POINT_ON_TRIANGLE_VERTEX_2;
            }
            d = edgeDist2(v0X, v0Y, v0Z, v1X, v1Y, v1Z, centerX, centerY, centerZ);
            if (d <= best) {
                best = d;
                code = POINT_ON_TRIANGLE_EDGE_01;
            }
            d = edgeDist2(v1X, v1Y, v1Z, v2X, v2Y, v2Z, centerX, centerY, centerZ);
            if (d <= best) {
                best = d;
                code = POINT_ON_TRIANGLE_EDGE_12;
            }
            d = edgeDist2(v2X, v2Y, v2Z, v0X, v0Y, v0Z, centerX, centerY, centerZ);
            if (d <= best) {
                code = POINT_ON_TRIANGLE_EDGE_20;
            }
            if (code != 0) {
                return contact(code, 0.0f, centerX, centerY, centerZ, 0.0f, 0.0f, 0.0f,
                        v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, pointAndTime);
            }
        }
        // 2. the face, first touched where the sphere reaches the plane (it cannot be touched first while the
        //    sphere already cuts the plane: the boundary is reached first)
        float absSd = Math.abs(sd);
        if (nLen > 0.0f && sd * dot < 0.0f && absSd >= radius) {
            float t = (absSd - radius) / Math.abs(dot);
            float pX = centerX + velX * t, pY = centerY + velY * t, pZ = centerZ + velZ * t;
            if (t < maxT && insideTriangle(pX, pY, pZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, nX, nY, nZ)) {
                float off = (sd > 0.0f ? radius : -radius) * invNLen;
                pointAndTime.set(pX - nX * off, pY - nY * off, pZ - nZ * off, t);
                return POINT_ON_TRIANGLE_FACE;
            }
        }
        // 3. the vertices and edges
        float best = maxT;
        int code = 0;
        float t = vertexTime(centerX - v0X, centerY - v0Y, centerZ - v0Z, velX, velY, velZ, radius);
        if (t < best) {
            best = t;
            code = POINT_ON_TRIANGLE_VERTEX_0;
        }
        t = vertexTime(centerX - v1X, centerY - v1Y, centerZ - v1Z, velX, velY, velZ, radius);
        if (t < best) {
            best = t;
            code = POINT_ON_TRIANGLE_VERTEX_1;
        }
        t = vertexTime(centerX - v2X, centerY - v2Y, centerZ - v2Z, velX, velY, velZ, radius);
        if (t < best) {
            best = t;
            code = POINT_ON_TRIANGLE_VERTEX_2;
        }
        t = edgeTime(v0X, v0Y, v0Z, v1X, v1Y, v1Z, centerX, centerY, centerZ, velX, velY, velZ, radius);
        if (t < best) {
            best = t;
            code = POINT_ON_TRIANGLE_EDGE_01;
        }
        t = edgeTime(v1X, v1Y, v1Z, v2X, v2Y, v2Z, centerX, centerY, centerZ, velX, velY, velZ, radius);
        if (t < best) {
            best = t;
            code = POINT_ON_TRIANGLE_EDGE_12;
        }
        t = edgeTime(v2X, v2Y, v2Z, v0X, v0Y, v0Z, centerX, centerY, centerZ, velX, velY, velZ, radius);
        if (t < best) {
            best = t;
            code = POINT_ON_TRIANGLE_EDGE_20;
        }
        if (code == 0) {
            return 0;
        }
        return contact(code, best, centerX, centerY, centerZ, velX, velY, velZ,
                v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, pointAndTime);
    }

    /** Writes the contact point of the given vertex or edge for the sphere center at time <code>t</code>. */
    private static int contact(int code, float t, float cX, float cY, float cZ, float velX, float velY, float velZ,
            float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y, float v2Z, Vector4f dest) {
        float pX = cX + velX * t, pY = cY + velY * t, pZ = cZ + velZ * t;
        switch (code) {
        case POINT_ON_TRIANGLE_VERTEX_0:
            dest.set(v0X, v0Y, v0Z, t);
            break;
        case POINT_ON_TRIANGLE_VERTEX_1:
            dest.set(v1X, v1Y, v1Z, t);
            break;
        case POINT_ON_TRIANGLE_VERTEX_2:
            dest.set(v2X, v2Y, v2Z, t);
            break;
        case POINT_ON_TRIANGLE_EDGE_01:
            edgePoint(v0X, v0Y, v0Z, v1X, v1Y, v1Z, pX, pY, pZ, t, dest);
            break;
        case POINT_ON_TRIANGLE_EDGE_12:
            edgePoint(v1X, v1Y, v1Z, v2X, v2Y, v2Z, pX, pY, pZ, t, dest);
            break;
        default:
            edgePoint(v2X, v2Y, v2Z, v0X, v0Y, v0Z, pX, pY, pZ, t, dest);
            break;
        }
        return code;
    }

    private static void edgePoint(float aX, float aY, float aZ, float bX, float bY, float bZ, float pX, float pY, float pZ, float t, Vector4f dest) {
        float eX = bX - aX, eY = bY - aY, eZ = bZ - aZ;
        float s = ((pX - aX) * eX + (pY - aY) * eY + (pZ - aZ) * eZ) / (eX * eX + eY * eY + eZ * eZ);
        s = s < 0.0f ? 0.0f : s > 1.0f ? 1.0f : s;
        dest.set(aX + s * eX, aY + s * eY, aZ + s * eZ, t);
    }

    private static float dist2(float x, float y, float z) {
        return x * x + y * y + z * z;
    }

    /** Squared distance from <code>p</code> to the interior of the segment <code>ab</code>, or infinity if it projects outside. */
    private static float edgeDist2(float aX, float aY, float aZ, float bX, float bY, float bZ, float pX, float pY, float pZ) {
        float eX = bX - aX, eY = bY - aY, eZ = bZ - aZ;
        float wX = pX - aX, wY = pY - aY, wZ = pZ - aZ;
        float len = eX * eX + eY * eY + eZ * eZ;
        float ew = eX * wX + eY * wY + eZ * wZ;
        if (!(ew > 0.0f && ew < len)) {
            return Float.POSITIVE_INFINITY;
        }
        float cX = eY * wZ - eZ * wY, cY = eZ * wX - eX * wZ, cZ = eX * wY - eY * wX;
        return (cX * cX + cY * cY + cZ * cZ) / len;
    }

    /**
     * First time at which a sphere of the given radius, whose center is at <code>w</code> relative to a point and moves
     * with <code>vel</code>, touches that point; infinity if never. Both roots come from the product forms
     * <code>|w|² − r² = (|w| − r)(|w| + r)</code> and <code>disc = (r|v| − |w × v|)(r|v| + |w × v|)</code>, which do
     * not cancel.
     */
    private static float vertexTime(float wX, float wY, float wZ, float velX, float velY, float velZ, float radius) {
        float wv = wX * velX + wY * velY + wZ * velZ;
        if (!(wv < 0.0f)) {
            return Float.POSITIVE_INFINITY;
        }
        float cX = wY * velZ - wZ * velY, cY = wZ * velX - wX * velZ, cZ = wX * velY - wY * velX;
        float cross = Math.sqrt(cX * cX + cY * cY + cZ * cZ);
        float rv = radius * Math.sqrt(velX * velX + velY * velY + velZ * velZ);
        float disc = (rv - cross) * (rv + cross);
        if (!(disc >= 0.0f)) {
            return Float.POSITIVE_INFINITY;
        }
        float wLen = Math.sqrt(wX * wX + wY * wY + wZ * wZ);
        float t = (wLen - radius) * (wLen + radius) / (Math.sqrt(disc) - wv);
        return t > 0.0f ? t : 0.0f;
    }

    /**
     * First time at which the moving sphere touches the interior of the segment <code>ab</code>; infinity if it
     * touches the segment's line only beyond an endpoint (the vertex test finds that contact), already cuts the line,
     * or never reaches it. The terms are cross products, <code>|e × (w + v t)|² = r²|e|²</code>, so they do not
     * cancel.
     */
    private static float edgeTime(float aX, float aY, float aZ, float bX, float bY, float bZ, float cX, float cY, float cZ, float velX, float velY, float velZ, float radius) {
        float eX = bX - aX, eY = bY - aY, eZ = bZ - aZ;
        float len = eX * eX + eY * eY + eZ * eZ;
        if (!(len > 0.0f)) {
            return Float.POSITIVE_INFINITY;
        }
        float wX = cX - aX, wY = cY - aY, wZ = cZ - aZ;
        float ewX = eY * wZ - eZ * wY, ewY = eZ * wX - eX * wZ, ewZ = eX * wY - eY * wX;
        float evX = eY * velZ - eZ * velY, evY = eZ * velX - eX * velZ, evZ = eX * velY - eY * velX;
        float halfB = ewX * evX + ewY * evY + ewZ * evZ;
        if (!(halfB < 0.0f)) {
            return Float.POSITIVE_INFINITY;
        }
        float eLen = Math.sqrt(len);
        float ewLen = Math.sqrt(ewX * ewX + ewY * ewY + ewZ * ewZ);
        float c = (ewLen - radius * eLen) * (ewLen + radius * eLen);
        if (!(c > 0.0f)) {
            return Float.POSITIVE_INFINITY;
        }
        float det = Math.abs(eX * (wY * velZ - wZ * velY) + eY * (wZ * velX - wX * velZ) + eZ * (wX * velY - wY * velX));
        float rev = radius * Math.sqrt(evX * evX + evY * evY + evZ * evZ);
        float disc = len * ((rev - det) * (rev + det));
        if (!(disc >= 0.0f)) {
            return Float.POSITIVE_INFINITY;
        }
        float t = c / (Math.sqrt(disc) - halfB);
        float s = (eX * (wX + velX * t) + eY * (wY + velY * t) + eZ * (wZ + velZ * t)) / len;
        return s >= 0.0f && s <= 1.0f ? t : Float.POSITIVE_INFINITY;
    }

    /** Whether the projection of <code>p</code> along <code>n = (v1 − v0) × (v2 − v0)</code> lies in the closed triangle. */
    private static boolean insideTriangle(float pX, float pY, float pZ, float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z,
            float v2X, float v2Y, float v2Z, float nX, float nY, float nZ) {
        return edgeSide(v0X, v0Y, v0Z, v1X, v1Y, v1Z, pX, pY, pZ, nX, nY, nZ) >= 0.0f
                && edgeSide(v1X, v1Y, v1Z, v2X, v2Y, v2Z, pX, pY, pZ, nX, nY, nZ) >= 0.0f
                && edgeSide(v2X, v2Y, v2Z, v0X, v0Y, v0Z, pX, pY, pZ, nX, nY, nZ) >= 0.0f;
    }

    private static float edgeSide(float aX, float aY, float aZ, float bX, float bY, float bZ, float pX, float pY, float pZ, float nX, float nY, float nZ) {
        float eX = bX - aX, eY = bY - aY, eZ = bZ - aZ;
        float wX = pX - aX, wY = pY - aY, wZ = pZ - aZ;
        return (eY * wZ - eZ * wY) * nX + (eZ * wX - eX * wZ) * nY + (eX * wY - eY * wX) * nZ;
    }


    /**
     * Test whether the projection of the given point <code>(pX, pY, pZ)</code> lies inside of the triangle defined by the three vertices
     * <code>(v0X, v0Y, v0Z)</code>, <code>(v1X, v1Y, v1Z)</code> and <code>(v2X, v2Y, v2Z)</code>.
     * <p>
     * The triangle is closed: points on its edges and vertices are inside. A degenerate (zero-area) triangle contains no point.
     * <p>
     * Reference: <a href="http://www.peroxide.dk/papers/collision/collision.pdf">Improved Collision detection and Response</a>
     * 
     * @param pX
     *              the x coordinate of the point to test
     * @param pY
     *              the y coordinate of the point to test
     * @param pZ
     *              the z coordinate of the point to test
     * @param v0X
     *              the x coordinate of the first vertex
     * @param v0Y
     *              the y coordinate of the first vertex
     * @param v0Z
     *              the z coordinate of the first vertex
     * @param v1X
     *              the x coordinate of the second vertex
     * @param v1Y
     *              the y coordinate of the second vertex
     * @param v1Z
     *              the z coordinate of the second vertex
     * @param v2X
     *              the x coordinate of the third vertex
     * @param v2Y
     *              the y coordinate of the third vertex
     * @param v2Z
     *              the z coordinate of the third vertex
     * @return <code>true</code> if the projection of the given point lies inside of the given triangle; <code>false</code> otherwise
     */
    public static boolean testPointInTriangle(float pX, float pY, float pZ, float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y, float v2Z) {
        float nX = (v1Y - v0Y) * (v2Z - v0Z) - (v1Z - v0Z) * (v2Y - v0Y);
        float nY = (v1Z - v0Z) * (v2X - v0X) - (v1X - v0X) * (v2Z - v0Z);
        float nZ = (v1X - v0X) * (v2Y - v0Y) - (v1Y - v0Y) * (v2X - v0X);
        if (!(nX * nX + nY * nY + nZ * nZ > 0.0f)) {
            return false;
        }
        return insideTriangle(pX, pY, pZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, nX, nY, nZ);
    }

    /**
     * Test whether the given ray with the origin <code>(originX, originY, originZ)</code> and normalized direction <code>(dirX, dirY, dirZ)</code>
     * intersects the given sphere with center <code>(centerX, centerY, centerZ)</code> and square radius <code>radiusSquared</code>,
     * and store the values of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> for both points (near
     * and far) of intersections into the given <code>result</code> vector.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the sphere.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius.
     * <p>
     * Reference: <a href="http://www.scratchapixel.com/lessons/3d-basic-rendering/minimal-ray-tracer-rendering-simple-shapes/ray-sphere-intersection">http://www.scratchapixel.com/</a>
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param originZ
     *              the z coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's normalized direction
     * @param dirY
     *              the y coordinate of the ray's normalized direction
     * @param dirZ
     *              the z coordinate of the ray's normalized direction
     * @param centerX
     *              the x coordinate of the sphere's center
     * @param centerY
     *              the y coordinate of the sphere's center
     * @param centerZ
     *              the z coordinate of the sphere's center
     * @param radiusSquared
     *              the square of the sphere's radius
     * @param result
     *              a vector that will contain the values of the parameter <i>t</i> in the ray equation
     *              <i>p(t) = origin + t * dir</i> for both points (near, far) of intersections with the sphere
     * @return <code>true</code> if the ray intersects the sphere; <code>false</code> otherwise
     */
    public static boolean intersectRaySphere(float originX, float originY, float originZ, float dirX, float dirY, float dirZ,
            float centerX, float centerY, float centerZ, float radiusSquared, Vector2f result) {
        double lx = centerX - (double) originX, ly = centerY - (double) originY, lz = centerZ - (double) originZ;
        double tca = lx * dirX + ly * dirY + lz * dirZ;
        // the perpendicular distance directly: |L|² − tca² cancels for distant spheres (thousands of wrong answers per
        // 10k random distant rays before 2.4.0)
        double px = lx - tca * dirX, py = ly - tca * dirY, pz = lz - tca * dirZ;
        double d2 = px * px + py * py + pz * pz;
        if (d2 > radiusSquared)
            return false;
        double thc = java.lang.Math.sqrt(radiusSquared - d2);
        // the root of larger magnitude without cancellation, the other from t0 t1 = (|L| − r)(|L| + r)
        double len = java.lang.Math.sqrt(lx * lx + ly * ly + lz * lz), r = java.lang.Math.sqrt(radiusSquared);
        double prod = (len - r) * (len + r);
        double t0, t1;
        if (tca >= 0.0) {
            t1 = tca + thc;
            t0 = t1 == 0.0 ? 0.0 : prod / t1;
        } else {
            t0 = tca - thc;
            t1 = prod / t0;
        }
        if (t1 >= 0.0) { // tangent rays (t0 == t1) hit
            result.x = (float) t0;
            result.y = (float) t1;
            return true;
        }
        return false;
    }

    /**
     * Test whether the ray with the given <code>origin</code> and normalized direction <code>dir</code>
     * intersects the sphere with the given <code>center</code> and square radius <code>radiusSquared</code>,
     * and store the values of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> for both points (near
     * and far) of intersections into the given <code>result</code> vector.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the sphere.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius.
     * <p>
     * Reference: <a href="http://www.scratchapixel.com/lessons/3d-basic-rendering/minimal-ray-tracer-rendering-simple-shapes/ray-sphere-intersection">http://www.scratchapixel.com/</a>
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's normalized direction
     * @param center
     *              the sphere's center
     * @param radiusSquared
     *              the square of the sphere's radius
     * @param result
     *              a vector that will contain the values of the parameter <i>t</i> in the ray equation
     *              <i>p(t) = origin + t * dir</i> for both points (near, far) of intersections with the sphere
     * @return <code>true</code> if the ray intersects the sphere; <code>false</code> otherwise
     */
    public static boolean intersectRaySphere(Vector3fc origin, Vector3fc dir, Vector3fc center, float radiusSquared, Vector2f result) {
        return intersectRaySphere(origin.x(), origin.y(), origin.z(), dir.x(), dir.y(), dir.z(), center.x(), center.y(), center.z(), radiusSquared, result);
    }

    /**
     * Test whether the given ray with the origin <code>(originX, originY, originZ)</code> and normalized direction <code>(dirX, dirY, dirZ)</code>
     * intersects the given sphere with center <code>(centerX, centerY, centerZ)</code> and square radius <code>radiusSquared</code>.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the sphere.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius.
     * <p>
     * Reference: <a href="http://www.scratchapixel.com/lessons/3d-basic-rendering/minimal-ray-tracer-rendering-simple-shapes/ray-sphere-intersection">http://www.scratchapixel.com/</a>
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param originZ
     *              the z coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's normalized direction
     * @param dirY
     *              the y coordinate of the ray's normalized direction
     * @param dirZ
     *              the z coordinate of the ray's normalized direction
     * @param centerX
     *              the x coordinate of the sphere's center
     * @param centerY
     *              the y coordinate of the sphere's center
     * @param centerZ
     *              the z coordinate of the sphere's center
     * @param radiusSquared
     *              the square of the sphere's radius
     * @return <code>true</code> if the ray intersects the sphere; <code>false</code> otherwise
     */
    public static boolean testRaySphere(float originX, float originY, float originZ, float dirX, float dirY, float dirZ,
            float centerX, float centerY, float centerZ, float radiusSquared) {
        double lx = centerX - (double) originX, ly = centerY - (double) originY, lz = centerZ - (double) originZ;
        double tca = lx * dirX + ly * dirY + lz * dirZ;
        // the perpendicular distance directly: |L|² − tca² cancels for distant spheres (thousands of wrong answers per
        // 10k random distant rays before 2.4.0)
        double px = lx - tca * dirX, py = ly - tca * dirY, pz = lz - tca * dirZ;
        double d2 = px * px + py * py + pz * pz;
        if (d2 > radiusSquared)
            return false;
        double thc = java.lang.Math.sqrt(radiusSquared - d2);
        // the root of larger magnitude without cancellation, the other from t0 t1 = (|L| − r)(|L| + r)
        double len = java.lang.Math.sqrt(lx * lx + ly * ly + lz * lz), r = java.lang.Math.sqrt(radiusSquared);
        double prod = (len - r) * (len + r);
        double t1 = tca >= 0.0 ? tca + thc : prod / (tca - thc); // the far root
        return t1 >= 0.0;
    }

    /**
     * Test whether the ray with the given <code>origin</code> and normalized direction <code>dir</code>
     * intersects the sphere with the given <code>center</code> and square radius.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the sphere.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius.
     * <p>
     * Reference: <a href="http://www.scratchapixel.com/lessons/3d-basic-rendering/minimal-ray-tracer-rendering-simple-shapes/ray-sphere-intersection">http://www.scratchapixel.com/</a>
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's normalized direction
     * @param center
     *              the sphere's center
     * @param radiusSquared
     *              the square of the sphere's radius
     * @return <code>true</code> if the ray intersects the sphere; <code>false</code> otherwise
     */
    public static boolean testRaySphere(Vector3fc origin, Vector3fc dir, Vector3fc center, float radiusSquared) {
        return testRaySphere(origin.x(), origin.y(), origin.z(), dir.x(), dir.y(), dir.z(), center.x(), center.y(), center.z(), radiusSquared);
    }

    /**
     * Test whether the line segment with the end points <code>(p0X, p0Y, p0Z)</code> and <code>(p1X, p1Y, p1Z)</code>
     * intersects the given sphere with center <code>(centerX, centerY, centerZ)</code> and square radius <code>radiusSquared</code>.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius. A segment whose end points are equal
     * always gives <code>false</code>.
     * <p>
     * Reference: <a href="http://paulbourke.net/geometry/circlesphere/index.html#linesphere">http://paulbourke.net/</a>
     * 
     * @param p0X
     *              the x coordinate of the line segment's first end point
     * @param p0Y
     *              the y coordinate of the line segment's first end point
     * @param p0Z
     *              the z coordinate of the line segment's first end point
     * @param p1X
     *              the x coordinate of the line segment's second end point
     * @param p1Y
     *              the y coordinate of the line segment's second end point
     * @param p1Z
     *              the z coordinate of the line segment's second end point
     * @param centerX
     *              the x coordinate of the sphere's center
     * @param centerY
     *              the y coordinate of the sphere's center
     * @param centerZ
     *              the z coordinate of the sphere's center
     * @param radiusSquared
     *              the square of the sphere's radius
     * @return <code>true</code> if the line segment intersects the sphere; <code>false</code> otherwise
     */
    public static boolean testLineSegmentSphere(float p0X, float p0Y, float p0Z, float p1X, float p1Y, float p1Z,
            float centerX, float centerY, float centerZ, float radiusSquared) {
        float dX = p1X - p0X;
        float dY = p1Y - p0Y;
        float dZ = p1Z - p0Z;
        float nom = (centerX - p0X) * dX + (centerY - p0Y) * dY + (centerZ - p0Z) * dZ;
        float den = dX * dX + dY * dY + dZ * dZ;
        float u = den > 0 ? nom / den : 0; // a zero-length segment is the point p0
        if (u < 0.0f) {
            dX = p0X - centerX;
            dY = p0Y - centerY;
            dZ = p0Z - centerZ;
        } else if (u > 1.0f) {
            dX = p1X - centerX;
            dY = p1Y - centerY;
            dZ = p1Z - centerZ;
        } else { // has to be >= 0 and <= 1
            float pX = p0X + u * dX;
            float pY = p0Y + u * dY;
            float pZ = p0Z + u * dZ;
            dX = pX - centerX;
            dY = pY - centerY;
            dZ = pZ - centerZ;
        }
        float dist = dX * dX + dY * dY + dZ * dZ;
        return dist <= radiusSquared;
    }

    /**
     * Test whether the line segment with the end points <code>p0</code> and <code>p1</code>
     * intersects the given sphere with center <code>center</code> and square radius <code>radiusSquared</code>.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius. A segment whose end points are equal
     * always gives <code>false</code>.
     * <p>
     * Reference: <a href="http://paulbourke.net/geometry/circlesphere/index.html#linesphere">http://paulbourke.net/</a>
     * 
     * @param p0
     *              the line segment's first end point
     * @param p1
     *              the line segment's second end point
     * @param center
     *              the sphere's center
     * @param radiusSquared
     *              the square of the sphere's radius
     * @return <code>true</code> if the line segment intersects the sphere; <code>false</code> otherwise
     */
    public static boolean testLineSegmentSphere(Vector3fc p0, Vector3fc p1, Vector3fc center, float radiusSquared) {
        return testLineSegmentSphere(p0.x(), p0.y(), p0.z(), p1.x(), p1.y(), p1.z(), center.x(), center.y(), center.z(), radiusSquared);
    }

    /**
     * Test whether the given ray with the origin <code>(originX, originY, originZ)</code> and direction <code>(dirX, dirY, dirZ)</code>
     * intersects the axis-aligned box given as its minimum corner <code>(minX, minY, minZ)</code> and maximum corner <code>(maxX, maxY, maxZ)</code>,
     * and return the values of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the near and far point of intersection.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the axis-aligned box.
     * <p>
     * If many boxes need to be tested against the same ray, then the {@link RayAabIntersection} class is likely more efficient.
     * <p>
     * Reference: <a href="https://dl.acm.org/citation.cfm?id=1198748">An Efficient and Robust Ray–Box Intersection</a>
     * 
     * @see #intersectRayAab(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector2f)
     * @see RayAabIntersection
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param originZ
     *              the z coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's direction
     * @param dirY
     *              the y coordinate of the ray's direction
     * @param dirZ
     *              the z coordinate of the ray's direction
     * @param minX
     *              the x coordinate of the minimum corner of the axis-aligned box
     * @param minY
     *              the y coordinate of the minimum corner of the axis-aligned box
     * @param minZ
     *              the z coordinate of the minimum corner of the axis-aligned box
     * @param maxX
     *              the x coordinate of the maximum corner of the axis-aligned box
     * @param maxY
     *              the y coordinate of the maximum corner of the axis-aligned box
     * @param maxZ
     *              the y coordinate of the maximum corner of the axis-aligned box
     * @param result
     *              a vector which will hold the resulting values of the parameter
     *              <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the near and far point of intersection
     *              iff the ray intersects the axis-aligned box
     * @return <code>true</code> if the given ray intersects the axis-aligned box; <code>false</code> otherwise
     */
    public static boolean intersectRayAab(float originX, float originY, float originZ, float dirX, float dirY, float dirZ,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ, Vector2f result) {
        if (originX != originX || originY != originY || originZ != originZ || dirX != dirX || dirY != dirY || dirZ != dirZ) {
            return false; // NaN never hits
        }
        float invDirX = 1.0f / dirX, invDirY = 1.0f / dirY, invDirZ = 1.0f / dirZ;
        float tNear, tFar, tymin, tymax, tzmin, tzmax;
        if (invDirX >= 0.0f) {
            tNear = (minX - originX) * invDirX;
            tFar = (maxX - originX) * invDirX;
        } else {
            tNear = (maxX - originX) * invDirX;
            tFar = (minX - originX) * invDirX;
        }
        if (invDirY >= 0.0f) {
            tymin = (minY - originY) * invDirY;
            tymax = (maxY - originY) * invDirY;
        } else {
            tymin = (maxY - originY) * invDirY;
            tymax = (minY - originY) * invDirY;
        }
        if (tNear > tymax || tymin > tFar)
            return false;
        if (invDirZ >= 0.0f) {
            tzmin = (minZ - originZ) * invDirZ;
            tzmax = (maxZ - originZ) * invDirZ;
        } else {
            tzmin = (maxZ - originZ) * invDirZ;
            tzmax = (minZ - originZ) * invDirZ;
        }
        if (tNear > tzmax || tzmin > tFar)
            return false;
        tNear = tymin > tNear || Float.isNaN(tNear) ? tymin : tNear;
        tFar = tymax < tFar || Float.isNaN(tFar) ? tymax : tFar;
        tNear = tzmin > tNear ? tzmin : tNear;
        tFar = tzmax < tFar ? tzmax : tFar;
        if (tNear <= tFar && tFar >= 0.0f) {
            result.x = tNear;
            result.y = tFar;
            return true;
        }
        return false;
    }

    /**
     * Test whether the ray with the given <code>origin</code> and direction <code>dir</code>
     * intersects the axis-aligned box specified as its minimum corner <code>min</code> and maximum corner <code>max</code>,
     * and return the values of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the near and far point of intersection..
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the axis-aligned box.
     * <p>
     * If many boxes need to be tested against the same ray, then the {@link RayAabIntersection} class is likely more efficient.
     * <p>
     * Reference: <a href="https://dl.acm.org/citation.cfm?id=1198748">An Efficient and Robust Ray–Box Intersection</a>
     * 
     * @see #intersectRayAab(float, float, float, float, float, float, float, float, float, float, float, float, Vector2f)
     * @see RayAabIntersection
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's direction
     * @param min
     *              the minimum corner of the axis-aligned box
     * @param max
     *              the maximum corner of the axis-aligned box
     * @param result
     *              a vector which will hold the resulting values of the parameter
     *              <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the near and far point of intersection
     *              iff the ray intersects the axis-aligned box
     * @return <code>true</code> if the given ray intersects the axis-aligned box; <code>false</code> otherwise
     */
    public static boolean intersectRayAab(Vector3fc origin, Vector3fc dir, Vector3fc min, Vector3fc max, Vector2f result) {
        return intersectRayAab(origin.x(), origin.y(), origin.z(), dir.x(), dir.y(), dir.z(), min.x(), min.y(), min.z(), max.x(), max.y(), max.z(), result);
    }

    /**
     * Determine whether the undirected line segment with the end points <code>(p0X, p0Y, p0Z)</code> and <code>(p1X, p1Y, p1Z)</code>
     * intersects the axis-aligned box given as its minimum corner <code>(minX, minY, minZ)</code> and maximum corner <code>(maxX, maxY, maxZ)</code>,
     * and return the values of the parameter <i>t</i> in the ray equation <i>p(t) = origin + p0 * (p1 - p0)</i> of the near and far point of intersection.
     * <p>
     * This method returns <code>true</code> for a line segment whose either end point lies inside the axis-aligned box.
     * <p>
     * Reference: <a href="https://dl.acm.org/citation.cfm?id=1198748">An Efficient and Robust Ray–Box Intersection</a>
     * 
     * @see #intersectLineSegmentAab(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector2f)
     * 
     * @param p0X
     *              the x coordinate of the line segment's first end point
     * @param p0Y
     *              the y coordinate of the line segment's first end point
     * @param p0Z
     *              the z coordinate of the line segment's first end point
     * @param p1X
     *              the x coordinate of the line segment's second end point
     * @param p1Y
     *              the y coordinate of the line segment's second end point
     * @param p1Z
     *              the z coordinate of the line segment's second end point
     * @param minX
     *              the x coordinate of one corner of the axis-aligned box
     * @param minY
     *              the y coordinate of one corner of the axis-aligned box
     * @param minZ
     *              the z coordinate of one corner of the axis-aligned box
     * @param maxX
     *              the x coordinate of the opposite corner of the axis-aligned box
     * @param maxY
     *              the y coordinate of the opposite corner of the axis-aligned box
     * @param maxZ
     *              the y coordinate of the opposite corner of the axis-aligned box
     * @param result
     *              a vector which will hold the resulting values of the parameter
     *              <i>t</i> in the ray equation <i>p(t) = p0 + t * (p1 - p0)</i> of the near and far point of intersection
     *              iff the line segment intersects the axis-aligned box
     * @return {@link #INSIDE} if the line segment lies completely inside of the axis-aligned box; or
     *         {@link #OUTSIDE} if the line segment lies completely outside of the axis-aligned box; or
     *         {@link #ONE_INTERSECTION} if one of the end points of the line segment lies inside of the axis-aligned box; or
     *         {@link #TWO_INTERSECTION} if the line segment intersects two sides of the axis-aligned box
     *         or lies on an edge or a side of the box
     */
    public static int intersectLineSegmentAab(float p0X, float p0Y, float p0Z, float p1X, float p1Y, float p1Z,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ, Vector2f result) {
        float dirX = p1X - p0X, dirY = p1Y - p0Y, dirZ = p1Z - p0Z;
        float invDirX = 1.0f / dirX, invDirY = 1.0f / dirY, invDirZ = 1.0f / dirZ;
        float tNear, tFar, tymin, tymax, tzmin, tzmax;
        if (invDirX >= 0.0f) {
            tNear = (minX - p0X) * invDirX;
            tFar = (maxX - p0X) * invDirX;
        } else {
            tNear = (maxX - p0X) * invDirX;
            tFar = (minX - p0X) * invDirX;
        }
        if (invDirY >= 0.0f) {
            tymin = (minY - p0Y) * invDirY;
            tymax = (maxY - p0Y) * invDirY;
        } else {
            tymin = (maxY - p0Y) * invDirY;
            tymax = (minY - p0Y) * invDirY;
        }
        if (tNear > tymax || tymin > tFar)
            return OUTSIDE;
        if (invDirZ >= 0.0f) {
            tzmin = (minZ - p0Z) * invDirZ;
            tzmax = (maxZ - p0Z) * invDirZ;
        } else {
            tzmin = (maxZ - p0Z) * invDirZ;
            tzmax = (minZ - p0Z) * invDirZ;
        }
        if (tNear > tzmax || tzmin > tFar)
            return OUTSIDE;
        tNear = tymin > tNear || Float.isNaN(tNear) ? tymin : tNear;
        tFar = tymax < tFar || Float.isNaN(tFar) ? tymax : tFar;
        tNear = tzmin > tNear ? tzmin : tNear;
        tFar = tzmax < tFar ? tzmax : tFar;
        int type = OUTSIDE;
        if (tNear <= tFar && tNear <= 1.0f && tFar >= 0.0f) {
            if (tNear >= 0.0f && tFar > 1.0f) {
                tFar = tNear;
                type = ONE_INTERSECTION;
            } else if (tNear < 0.0f && tFar <= 1.0f) {
                tNear = tFar;
                type = ONE_INTERSECTION;
            } else if (tNear < 0.0f && tFar > 1.0f) {
                type = INSIDE;
            } else {
                type = TWO_INTERSECTION;
            }
            result.x = tNear;
            result.y = tFar;
        }
        return type;
    }

    /**
     * Determine whether the undirected line segment with the end points <code>p0</code> and <code>p1</code>
     * intersects the axis-aligned box given as its minimum corner <code>min</code> and maximum corner <code>max</code>,
     * and return the values of the parameter <i>t</i> in the ray equation <i>p(t) = origin + p0 * (p1 - p0)</i> of the near and far point of intersection.
     * <p>
     * This method returns <code>true</code> for a line segment whose either end point lies inside the axis-aligned box.
     * <p>
     * Reference: <a href="https://dl.acm.org/citation.cfm?id=1198748">An Efficient and Robust Ray–Box Intersection</a>
     * 
     * @see #intersectLineSegmentAab(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector2f)
     * 
     * @param p0
     *              the line segment's first end point
     * @param p1
     *              the line segment's second end point
     * @param min
     *              the minimum corner of the axis-aligned box
     * @param max
     *              the maximum corner of the axis-aligned box
     * @param result
     *              a vector which will hold the resulting values of the parameter
     *              <i>t</i> in the ray equation <i>p(t) = p0 + t * (p1 - p0)</i> of the near and far point of intersection
     *              iff the line segment intersects the axis-aligned box
     * @return {@link #INSIDE} if the line segment lies completely inside of the axis-aligned box; or
     *         {@link #OUTSIDE} if the line segment lies completely outside of the axis-aligned box; or
     *         {@link #ONE_INTERSECTION} if one of the end points of the line segment lies inside of the axis-aligned box; or
     *         {@link #TWO_INTERSECTION} if the line segment intersects two sides of the axis-aligned box
     *         or lies on an edge or a side of the box
     */
    public static int intersectLineSegmentAab(Vector3fc p0, Vector3fc p1, Vector3fc min, Vector3fc max, Vector2f result) {
        return intersectLineSegmentAab(p0.x(), p0.y(), p0.z(), p1.x(), p1.y(), p1.z(), min.x(), min.y(), min.z(), max.x(), max.y(), max.z(), result);
    }

    /**
     * Test whether the given ray with the origin <code>(originX, originY, originZ)</code> and direction <code>(dirX, dirY, dirZ)</code>
     * intersects the axis-aligned box given as its minimum corner <code>(minX, minY, minZ)</code> and maximum corner <code>(maxX, maxY, maxZ)</code>.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the axis-aligned box.
     * <p>
     * If many boxes need to be tested against the same ray, then the {@link RayAabIntersection} class is likely more efficient.
     * <p>
     * Reference: <a href="https://dl.acm.org/citation.cfm?id=1198748">An Efficient and Robust Ray–Box Intersection</a>
     * 
     * @see #testRayAab(Vector3fc, Vector3fc, Vector3fc, Vector3fc)
     * @see RayAabIntersection
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param originZ
     *              the z coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's direction
     * @param dirY
     *              the y coordinate of the ray's direction
     * @param dirZ
     *              the z coordinate of the ray's direction
     * @param minX
     *              the x coordinate of the minimum corner of the axis-aligned box
     * @param minY
     *              the y coordinate of the minimum corner of the axis-aligned box
     * @param minZ
     *              the z coordinate of the minimum corner of the axis-aligned box
     * @param maxX
     *              the x coordinate of the maximum corner of the axis-aligned box
     * @param maxY
     *              the y coordinate of the maximum corner of the axis-aligned box
     * @param maxZ
     *              the y coordinate of the maximum corner of the axis-aligned box
     * @return <code>true</code> if the given ray intersects the axis-aligned box; <code>false</code> otherwise
     */
    public static boolean testRayAab(float originX, float originY, float originZ, float dirX, float dirY, float dirZ,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (originX != originX || originY != originY || originZ != originZ || dirX != dirX || dirY != dirY || dirZ != dirZ) {
            return false; // NaN never hits
        }
        // Fast miss rejection when the ray starts outside and points away from a slab.
        if ((originX < minX && dirX <= 0.0f) || (originX > maxX && dirX >= 0.0f)
                || (originY < minY && dirY <= 0.0f) || (originY > maxY && dirY >= 0.0f)
                || (originZ < minZ && dirZ <= 0.0f) || (originZ > maxZ && dirZ >= 0.0f)) {
            return false;
        }
        float invDirX = 1.0f / dirX, invDirY = 1.0f / dirY, invDirZ = 1.0f / dirZ;
        return testRayAabInvDir(originX, originY, originZ, invDirX, invDirY, invDirZ, minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Like {@link #testRayAab(float, float, float, float, float, float, float, float, float, float, float, float)}
     * but with precomputed inverse direction values.
     * <p>
     * This overload is intended for batch kernels testing many boxes against the same or preprocessed rays.
     *
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param originZ
     *              the z coordinate of the ray's origin
     * @param invDirX
     *              the inverse x coordinate of the ray's direction
     * @param invDirY
     *              the inverse y coordinate of the ray's direction
     * @param invDirZ
     *              the inverse z coordinate of the ray's direction
     * @param minX
     *              the x coordinate of the minimum corner of the axis-aligned box
     * @param minY
     *              the y coordinate of the minimum corner of the axis-aligned box
     * @param minZ
     *              the z coordinate of the minimum corner of the axis-aligned box
     * @param maxX
     *              the x coordinate of the maximum corner of the axis-aligned box
     * @param maxY
     *              the y coordinate of the maximum corner of the axis-aligned box
     * @param maxZ
     *              the y coordinate of the maximum corner of the axis-aligned box
     * @return <code>true</code> if the given ray intersects the axis-aligned box; <code>false</code> otherwise
     */
    public static boolean testRayAabInvDir(float originX, float originY, float originZ, float invDirX, float invDirY, float invDirZ,
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float tNear, tFar, tymin, tymax, tzmin, tzmax;
        if (invDirX >= 0.0f) {
            tNear = (minX - originX) * invDirX;
            tFar = (maxX - originX) * invDirX;
        } else {
            tNear = (maxX - originX) * invDirX;
            tFar = (minX - originX) * invDirX;
        }
        if (invDirY >= 0.0f) {
            tymin = (minY - originY) * invDirY;
            tymax = (maxY - originY) * invDirY;
        } else {
            tymin = (maxY - originY) * invDirY;
            tymax = (minY - originY) * invDirY;
        }
        if (tNear > tymax || tymin > tFar)
            return false;
        if (invDirZ >= 0.0f) {
            tzmin = (minZ - originZ) * invDirZ;
            tzmax = (maxZ - originZ) * invDirZ;
        } else {
            tzmin = (maxZ - originZ) * invDirZ;
            tzmax = (minZ - originZ) * invDirZ;
        }
        if (tNear > tzmax || tzmin > tFar)
            return false;
        tNear = tymin > tNear || Float.isNaN(tNear) ? tymin : tNear;
        tFar = tymax < tFar || Float.isNaN(tFar) ? tymax : tFar;
        tNear = tzmin > tNear ? tzmin : tNear;
        tFar = tzmax < tFar ? tzmax : tFar;
        return tNear <= tFar && tFar >= 0.0f;
    }

    /**
     * Test whether the ray with the given <code>origin</code> and direction <code>dir</code>
     * intersects the axis-aligned box specified as its minimum corner <code>min</code> and maximum corner <code>max</code>.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the axis-aligned box.
     * <p>
     * If many boxes need to be tested against the same ray, then the {@link RayAabIntersection} class is likely more efficient.
     * <p>
     * Reference: <a href="https://dl.acm.org/citation.cfm?id=1198748">An Efficient and Robust Ray–Box Intersection</a>
     *  
     * @see #testRayAab(float, float, float, float, float, float, float, float, float, float, float, float)
     * @see RayAabIntersection
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's direction
     * @param min
     *              the minimum corner of the axis-aligned box
     * @param max
     *              the maximum corner of the axis-aligned box
     * @return <code>true</code> if the given ray intersects the axis-aligned box; <code>false</code> otherwise
     */
    public static boolean testRayAab(Vector3fc origin, Vector3fc dir, Vector3fc min, Vector3fc max) {
        return testRayAab(origin.x(), origin.y(), origin.z(), dir.x(), dir.y(), dir.z(), min.x(), min.y(), min.z(), max.x(), max.y(), max.z());
    }

    /**
     * Test whether the given ray with the origin <code>(originX, originY, originZ)</code> and direction <code>(dirX, dirY, dirZ)</code>
     * intersects the frontface of the triangle consisting of the three vertices <code>(v0X, v0Y, v0Z)</code>, <code>(v1X, v1Y, v1Z)</code> and <code>(v2X, v2Y, v2Z)</code>.
     * <p>
     * This is an implementation of the <a href="http://www.graphics.cornell.edu/pubs/1997/MT97.pdf">
     * Fast, Minimum Storage Ray/Triangle Intersection</a> method.
     * <p>
     * This test implements backface culling, that is, it will return <code>false</code> when the triangle is in clockwise
     * winding order assuming a <i>right-handed</i> coordinate system when seen along the ray's direction, even if the ray intersects the triangle.
     * This is in compliance with how OpenGL handles backface culling with default frontface/backface settings.
     * 
     * @see #testRayTriangleFront(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3fc, float)
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param originZ
     *              the z coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's direction
     * @param dirY
     *              the y coordinate of the ray's direction
     * @param dirZ
     *              the z coordinate of the ray's direction
     * @param v0X
     *              the x coordinate of the first vertex
     * @param v0Y
     *              the y coordinate of the first vertex
     * @param v0Z
     *              the z coordinate of the first vertex
     * @param v1X
     *              the x coordinate of the second vertex
     * @param v1Y
     *              the y coordinate of the second vertex
     * @param v1Z
     *              the z coordinate of the second vertex
     * @param v2X
     *              the x coordinate of the third vertex
     * @param v2Y
     *              the y coordinate of the third vertex
     * @param v2Z
     *              the z coordinate of the third vertex
     * @param epsilon
     *              the minimum distance along the ray for a hit (the test is watertight and handles parallel rays exactly; before 2.4.0 this was a tolerance on the Möller-Trumbore determinant)
     * @return <code>true</code> if the given ray intersects the frontface of the triangle; <code>false</code> otherwise
     */
    public static boolean testRayTriangleFront(float originX, float originY, float originZ, float dirX, float dirY, float dirZ,
            float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y, float v2Z,
            float epsilon) {
        return !Double.isNaN(Intersectiond.rayTriangleWatertight(originX, originY, originZ, dirX, dirY, dirZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, epsilon, true));
    }

    /**
     * Test whether the ray with the given <code>origin</code> and the given <code>dir</code> intersects the frontface of the triangle consisting of the three vertices
     * <code>v0</code>, <code>v1</code> and <code>v2</code>.
     * <p>
     * This is an implementation of the <a href="http://www.graphics.cornell.edu/pubs/1997/MT97.pdf">
     * Fast, Minimum Storage Ray/Triangle Intersection</a> method.
     * <p>
     * This test implements backface culling, that is, it will return <code>false</code> when the triangle is in clockwise
     * winding order assuming a <i>right-handed</i> coordinate system when seen along the ray's direction, even if the ray intersects the triangle.
     * This is in compliance with how OpenGL handles backface culling with default frontface/backface settings.
     * 
     * @see #testRayTriangleFront(float, float, float, float, float, float, float, float, float, float, float, float, float, float, float, float)
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's direction
     * @param v0
     *              the position of the first vertex
     * @param v1
     *              the position of the second vertex
     * @param v2
     *              the position of the third vertex
     * @param epsilon
     *              the minimum distance along the ray for a hit (the test is watertight and handles parallel rays exactly; before 2.4.0 this was a tolerance on the Möller-Trumbore determinant)
     * @return <code>true</code> if the given ray intersects the frontface of the triangle; <code>false</code> otherwise
     */
    public static boolean testRayTriangleFront(Vector3fc origin, Vector3fc dir, Vector3fc v0, Vector3fc v1, Vector3fc v2, float epsilon) {
        return testRayTriangleFront(origin.x(), origin.y(), origin.z(), dir.x(), dir.y(), dir.z(), v0.x(), v0.y(), v0.z(), v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z(), epsilon);
    }

    /**
     * Test whether the given ray with the origin <code>(originX, originY, originZ)</code> and direction <code>(dirX, dirY, dirZ)</code>
     * intersects the triangle consisting of the three vertices <code>(v0X, v0Y, v0Z)</code>, <code>(v1X, v1Y, v1Z)</code> and <code>(v2X, v2Y, v2Z)</code>.
     * <p>
     * This is an implementation of the <a href="http://www.graphics.cornell.edu/pubs/1997/MT97.pdf">
     * Fast, Minimum Storage Ray/Triangle Intersection</a> method.
     * <p>
     * This test does not take into account the winding order of the triangle, so a ray will intersect a front-facing triangle as well as a back-facing triangle.
     * 
     * @see #testRayTriangle(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3fc, float)
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param originZ
     *              the z coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's direction
     * @param dirY
     *              the y coordinate of the ray's direction
     * @param dirZ
     *              the z coordinate of the ray's direction
     * @param v0X
     *              the x coordinate of the first vertex
     * @param v0Y
     *              the y coordinate of the first vertex
     * @param v0Z
     *              the z coordinate of the first vertex
     * @param v1X
     *              the x coordinate of the second vertex
     * @param v1Y
     *              the y coordinate of the second vertex
     * @param v1Z
     *              the z coordinate of the second vertex
     * @param v2X
     *              the x coordinate of the third vertex
     * @param v2Y
     *              the y coordinate of the third vertex
     * @param v2Z
     *              the z coordinate of the third vertex
     * @param epsilon
     *              the minimum distance along the ray for a hit (the test is watertight and handles parallel rays exactly; before 2.4.0 this was a tolerance on the Möller-Trumbore determinant)
     * @return <code>true</code> if the given ray intersects the frontface of the triangle; <code>false</code> otherwise
     */
    public static boolean testRayTriangle(float originX, float originY, float originZ, float dirX, float dirY, float dirZ,
            float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y, float v2Z,
            float epsilon) {
        return !Double.isNaN(Intersectiond.rayTriangleWatertight(originX, originY, originZ, dirX, dirY, dirZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, epsilon, false));
    }

    /**
     * Test whether the ray with the given <code>origin</code> and the given <code>dir</code> intersects the frontface of the triangle consisting of the three vertices
     * <code>v0</code>, <code>v1</code> and <code>v2</code>.
     * <p>
     * This is an implementation of the <a href="http://www.graphics.cornell.edu/pubs/1997/MT97.pdf">
     * Fast, Minimum Storage Ray/Triangle Intersection</a> method.
     * <p>
     * This test does not take into account the winding order of the triangle, so a ray will intersect a front-facing triangle as well as a back-facing triangle.
     * 
     * @see #testRayTriangle(float, float, float, float, float, float, float, float, float, float, float, float, float, float, float, float)
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's direction
     * @param v0
     *              the position of the first vertex
     * @param v1
     *              the position of the second vertex
     * @param v2
     *              the position of the third vertex
     * @param epsilon
     *              the minimum distance along the ray for a hit (the test is watertight and handles parallel rays exactly; before 2.4.0 this was a tolerance on the Möller-Trumbore determinant)
     * @return <code>true</code> if the given ray intersects the frontface of the triangle; <code>false</code> otherwise
     */
    public static boolean testRayTriangle(Vector3fc origin, Vector3fc dir, Vector3fc v0, Vector3fc v1, Vector3fc v2, float epsilon) {
        return testRayTriangle(origin.x(), origin.y(), origin.z(), dir.x(), dir.y(), dir.z(), v0.x(), v0.y(), v0.z(), v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z(), epsilon);
    }

    /**
     * Determine whether the given ray with the origin <code>(originX, originY, originZ)</code> and direction <code>(dirX, dirY, dirZ)</code>
     * intersects the frontface of the triangle consisting of the three vertices <code>(v0X, v0Y, v0Z)</code>, <code>(v1X, v1Y, v1Z)</code> and <code>(v2X, v2Y, v2Z)</code>
     * and return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the point of intersection.
     * <p>
     * This is an implementation of the <a href="http://www.graphics.cornell.edu/pubs/1997/MT97.pdf">
     * Fast, Minimum Storage Ray/Triangle Intersection</a> method.
     * <p>
     * This test implements backface culling, that is, it will return <code>false</code> when the triangle is in clockwise
     * winding order assuming a <i>right-handed</i> coordinate system when seen along the ray's direction, even if the ray intersects the triangle.
     * This is in compliance with how OpenGL handles backface culling with default frontface/backface settings.
     * 
     * @see #testRayTriangleFront(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3fc, float)
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param originZ
     *              the z coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's direction
     * @param dirY
     *              the y coordinate of the ray's direction
     * @param dirZ
     *              the z coordinate of the ray's direction
     * @param v0X
     *              the x coordinate of the first vertex
     * @param v0Y
     *              the y coordinate of the first vertex
     * @param v0Z
     *              the z coordinate of the first vertex
     * @param v1X
     *              the x coordinate of the second vertex
     * @param v1Y
     *              the y coordinate of the second vertex
     * @param v1Z
     *              the z coordinate of the second vertex
     * @param v2X
     *              the x coordinate of the third vertex
     * @param v2Y
     *              the y coordinate of the third vertex
     * @param v2Z
     *              the z coordinate of the third vertex
     * @param epsilon
     *              the minimum distance along the ray for a hit (the test is watertight and handles parallel rays exactly; before 2.4.0 this was a tolerance on the Möller-Trumbore determinant)
     * @return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the point of intersection
     *         if the ray intersects the frontface of the triangle; <code>-1.0</code> otherwise
     */
    public static float intersectRayTriangleFront(float originX, float originY, float originZ, float dirX, float dirY, float dirZ,
            float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y, float v2Z,
            float epsilon) {
        double t = Intersectiond.rayTriangleWatertight(originX, originY, originZ, dirX, dirY, dirZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, epsilon, true);
        return Double.isNaN(t) ? -1.0f : (float) t;
    }

    /**
     * Determine whether the ray with the given <code>origin</code> and the given <code>dir</code> intersects the frontface of the triangle consisting of the three vertices
     * <code>v0</code>, <code>v1</code> and <code>v2</code> and return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the point of intersection.
     * <p>
     * This is an implementation of the <a href="http://www.graphics.cornell.edu/pubs/1997/MT97.pdf">
     * Fast, Minimum Storage Ray/Triangle Intersection</a> method.
     * <p>
     * This test implements backface culling, that is, it will return <code>false</code> when the triangle is in clockwise
     * winding order assuming a <i>right-handed</i> coordinate system when seen along the ray's direction, even if the ray intersects the triangle.
     * This is in compliance with how OpenGL handles backface culling with default frontface/backface settings.
     * 
     * @see #intersectRayTriangleFront(float, float, float, float, float, float, float, float, float, float, float, float, float, float, float, float)
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's direction
     * @param v0
     *              the position of the first vertex
     * @param v1
     *              the position of the second vertex
     * @param v2
     *              the position of the third vertex
     * @param epsilon
     *              the minimum distance along the ray for a hit (the test is watertight and handles parallel rays exactly; before 2.4.0 this was a tolerance on the Möller-Trumbore determinant)
     * @return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the point of intersection
     *         if the ray intersects the frontface of the triangle; <code>-1.0</code> otherwise
     */
    public static float intersectRayTriangleFront(Vector3fc origin, Vector3fc dir, Vector3fc v0, Vector3fc v1, Vector3fc v2, float epsilon) {
        return intersectRayTriangleFront(origin.x(), origin.y(), origin.z(), dir.x(), dir.y(), dir.z(), v0.x(), v0.y(), v0.z(), v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z(), epsilon);
    }

    /**
     * Determine whether the given ray with the origin <code>(originX, originY, originZ)</code> and direction <code>(dirX, dirY, dirZ)</code>
     * intersects the triangle consisting of the three vertices <code>(v0X, v0Y, v0Z)</code>, <code>(v1X, v1Y, v1Z)</code> and <code>(v2X, v2Y, v2Z)</code>
     * and return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the point of intersection.
     * <p>
     * This is an implementation of the <a href="http://www.graphics.cornell.edu/pubs/1997/MT97.pdf">
     * Fast, Minimum Storage Ray/Triangle Intersection</a> method.
     * <p>
     * This test does not take into account the winding order of the triangle, so a ray will intersect a front-facing triangle as well as a back-facing triangle.
     * 
     * @see #testRayTriangle(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3fc, float)
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param originZ
     *              the z coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's direction
     * @param dirY
     *              the y coordinate of the ray's direction
     * @param dirZ
     *              the z coordinate of the ray's direction
     * @param v0X
     *              the x coordinate of the first vertex
     * @param v0Y
     *              the y coordinate of the first vertex
     * @param v0Z
     *              the z coordinate of the first vertex
     * @param v1X
     *              the x coordinate of the second vertex
     * @param v1Y
     *              the y coordinate of the second vertex
     * @param v1Z
     *              the z coordinate of the second vertex
     * @param v2X
     *              the x coordinate of the third vertex
     * @param v2Y
     *              the y coordinate of the third vertex
     * @param v2Z
     *              the z coordinate of the third vertex
     * @param epsilon
     *              the minimum distance along the ray for a hit (the test is watertight and handles parallel rays exactly; before 2.4.0 this was a tolerance on the Möller-Trumbore determinant)
     * @return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the point of intersection
     *         if the ray intersects the triangle; <code>-1.0</code> otherwise
     */
    public static float intersectRayTriangle(float originX, float originY, float originZ, float dirX, float dirY, float dirZ,
            float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y, float v2Z,
            float epsilon) {
        double t = Intersectiond.rayTriangleWatertight(originX, originY, originZ, dirX, dirY, dirZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, epsilon, false);
        return Double.isNaN(t) ? -1.0f : (float) t;
    }

    /**
     * Determine whether the ray with the given <code>origin</code> and the given <code>dir</code> intersects the triangle consisting of the three vertices
     * <code>v0</code>, <code>v1</code> and <code>v2</code> and return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the point of intersection.
     * <p>
     * This is an implementation of the <a href="http://www.graphics.cornell.edu/pubs/1997/MT97.pdf">
     * Fast, Minimum Storage Ray/Triangle Intersection</a> method.
     * <p>
     * This test does not take into account the winding order of the triangle, so a ray will intersect a front-facing triangle as well as a back-facing triangle.
     * 
     * @see #intersectRayTriangle(float, float, float, float, float, float, float, float, float, float, float, float, float, float, float, float)
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's direction
     * @param v0
     *              the position of the first vertex
     * @param v1
     *              the position of the second vertex
     * @param v2
     *              the position of the third vertex
     * @param epsilon
     *              the minimum distance along the ray for a hit (the test is watertight and handles parallel rays exactly; before 2.4.0 this was a tolerance on the Möller-Trumbore determinant)
     * @return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the point of intersection
     *         if the ray intersects the triangle; <code>-1.0</code> otherwise
     */
    public static float intersectRayTriangle(Vector3fc origin, Vector3fc dir, Vector3fc v0, Vector3fc v1, Vector3fc v2, float epsilon) {
        return intersectRayTriangle(origin.x(), origin.y(), origin.z(), dir.x(), dir.y(), dir.z(), v0.x(), v0.y(), v0.z(), v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z(), epsilon);
    }

    /**
     * Test whether the line segment with the end points <code>(p0X, p0Y, p0Z)</code> and <code>(p1X, p1Y, p1Z)</code>
     * intersects the triangle consisting of the three vertices <code>(v0X, v0Y, v0Z)</code>, <code>(v1X, v1Y, v1Z)</code> and <code>(v2X, v2Y, v2Z)</code>,
     * regardless of the winding order of the triangle or the direction of the line segment between its two end points.
     * <p>
     * Reference: <a href="http://www.graphics.cornell.edu/pubs/1997/MT97.pdf">
     * Fast, Minimum Storage Ray/Triangle Intersection</a>
     * 
     * @see #testLineSegmentTriangle(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3fc, float)
     * 
     * @param p0X
     *              the x coordinate of the line segment's first end point
     * @param p0Y
     *              the y coordinate of the line segment's first end point
     * @param p0Z
     *              the z coordinate of the line segment's first end point
     * @param p1X
     *              the x coordinate of the line segment's second end point
     * @param p1Y
     *              the y coordinate of the line segment's second end point
     * @param p1Z
     *              the z coordinate of the line segment's second end point
     * @param v0X
     *              the x coordinate of the first vertex
     * @param v0Y
     *              the y coordinate of the first vertex
     * @param v0Z
     *              the z coordinate of the first vertex
     * @param v1X
     *              the x coordinate of the second vertex
     * @param v1Y
     *              the y coordinate of the second vertex
     * @param v1Z
     *              the z coordinate of the second vertex
     * @param v2X
     *              the x coordinate of the third vertex
     * @param v2Y
     *              the y coordinate of the third vertex
     * @param v2Z
     *              the z coordinate of the third vertex
     * @param epsilon
     *              a small epsilon when testing line segments that are almost parallel to the triangle
     * @return <code>true</code> if the given line segment intersects the triangle; <code>false</code> otherwise
     */
    public static boolean testLineSegmentTriangle(float p0X, float p0Y, float p0Z, float p1X, float p1Y, float p1Z,
            float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y, float v2Z,
            float epsilon) {
        float dirX = p1X - p0X;
        float dirY = p1Y - p0Y;
        float dirZ = p1Z - p0Z;
        float t = intersectRayTriangle(p0X, p0Y, p0Z, dirX, dirY, dirZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, epsilon);
        return t >= 0.0f && t <= 1.0f;
    }

    /**
     * Test whether the line segment with the end points <code>p0</code> and <code>p1</code>
     * intersects the triangle consisting of the three vertices <code>(v0X, v0Y, v0Z)</code>, <code>(v1X, v1Y, v1Z)</code> and <code>(v2X, v2Y, v2Z)</code>,
     * regardless of the winding order of the triangle or the direction of the line segment between its two end points.
     * <p>
     * Reference: <a href="http://www.graphics.cornell.edu/pubs/1997/MT97.pdf">
     * Fast, Minimum Storage Ray/Triangle Intersection</a>
     * 
     * @see #testLineSegmentTriangle(float, float, float, float, float, float, float, float, float, float, float, float, float, float, float, float)
     * 
     * @param p0
     *              the line segment's first end point
     * @param p1
     *              the line segment's second end point
     * @param v0
     *              the position of the first vertex
     * @param v1
     *              the position of the second vertex
     * @param v2
     *              the position of the third vertex
     * @param epsilon
     *              a small epsilon when testing line segments that are almost parallel to the triangle
     * @return <code>true</code> if the given line segment intersects the triangle; <code>false</code> otherwise
     */
    public static boolean testLineSegmentTriangle(Vector3fc p0, Vector3fc p1, Vector3fc v0, Vector3fc v1, Vector3fc v2, float epsilon) {
        return testLineSegmentTriangle(p0.x(), p0.y(), p0.z(), p1.x(), p1.y(), p1.z(), v0.x(), v0.y(), v0.z(), v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z(), epsilon);
    }

    /**
     * Determine whether the line segment with the end points <code>(p0X, p0Y, p0Z)</code> and <code>(p1X, p1Y, p1Z)</code>
     * intersects the triangle consisting of the three vertices <code>(v0X, v0Y, v0Z)</code>, <code>(v1X, v1Y, v1Z)</code> and <code>(v2X, v2Y, v2Z)</code>,
     * regardless of the winding order of the triangle or the direction of the line segment between its two end points,
     * and return the point of intersection.
     * <p>
     * Reference: <a href="http://www.graphics.cornell.edu/pubs/1997/MT97.pdf">
     * Fast, Minimum Storage Ray/Triangle Intersection</a>
     * 
     * @see #intersectLineSegmentTriangle(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3fc, float, Vector3f)
     * 
     * @param p0X
     *              the x coordinate of the line segment's first end point
     * @param p0Y
     *              the y coordinate of the line segment's first end point
     * @param p0Z
     *              the z coordinate of the line segment's first end point
     * @param p1X
     *              the x coordinate of the line segment's second end point
     * @param p1Y
     *              the y coordinate of the line segment's second end point
     * @param p1Z
     *              the z coordinate of the line segment's second end point
     * @param v0X
     *              the x coordinate of the first vertex
     * @param v0Y
     *              the y coordinate of the first vertex
     * @param v0Z
     *              the z coordinate of the first vertex
     * @param v1X
     *              the x coordinate of the second vertex
     * @param v1Y
     *              the y coordinate of the second vertex
     * @param v1Z
     *              the z coordinate of the second vertex
     * @param v2X
     *              the x coordinate of the third vertex
     * @param v2Y
     *              the y coordinate of the third vertex
     * @param v2Z
     *              the z coordinate of the third vertex
     * @param epsilon
     *              a small epsilon when testing line segments that are almost parallel to the triangle
     * @param intersectionPoint
     *              the point of intersection
     * @return <code>true</code> if the given line segment intersects the triangle; <code>false</code> otherwise
     */
    public static boolean intersectLineSegmentTriangle(float p0X, float p0Y, float p0Z, float p1X, float p1Y, float p1Z,
            float v0X, float v0Y, float v0Z, float v1X, float v1Y, float v1Z, float v2X, float v2Y, float v2Z,
            float epsilon, Vector3f intersectionPoint) {
        float dirX = p1X - p0X;
        float dirY = p1Y - p0Y;
        float dirZ = p1Z - p0Z;
        float t = intersectRayTriangle(p0X, p0Y, p0Z, dirX, dirY, dirZ, v0X, v0Y, v0Z, v1X, v1Y, v1Z, v2X, v2Y, v2Z, epsilon);
        if (t >= 0.0f && t <= 1.0f) {
            intersectionPoint.x = p0X + dirX * t;
            intersectionPoint.y = p0Y + dirY * t;
            intersectionPoint.z = p0Z + dirZ * t;
            return true;
        }
        return false;
    }

    /**
     * Determine whether the line segment with the end points <code>p0</code> and <code>p1</code>
     * intersects the triangle consisting of the three vertices <code>(v0X, v0Y, v0Z)</code>, <code>(v1X, v1Y, v1Z)</code> and <code>(v2X, v2Y, v2Z)</code>,
     * regardless of the winding order of the triangle or the direction of the line segment between its two end points,
     * and return the point of intersection.
     * <p>
     * Reference: <a href="http://www.graphics.cornell.edu/pubs/1997/MT97.pdf">
     * Fast, Minimum Storage Ray/Triangle Intersection</a>
     * 
     * @see #intersectLineSegmentTriangle(float, float, float, float, float, float, float, float, float, float, float, float, float, float, float, float, Vector3f)
     * 
     * @param p0
     *              the line segment's first end point
     * @param p1
     *              the line segment's second end point
     * @param v0
     *              the position of the first vertex
     * @param v1
     *              the position of the second vertex
     * @param v2
     *              the position of the third vertex
     * @param epsilon
     *              a small epsilon when testing line segments that are almost parallel to the triangle
     * @param intersectionPoint
     *              the point of intersection
     * @return <code>true</code> if the given line segment intersects the triangle; <code>false</code> otherwise
     */
    public static boolean intersectLineSegmentTriangle(Vector3fc p0, Vector3fc p1, Vector3fc v0, Vector3fc v1, Vector3fc v2, float epsilon, Vector3f intersectionPoint) {
        return intersectLineSegmentTriangle(p0.x(), p0.y(), p0.z(), p1.x(), p1.y(), p1.z(), v0.x(), v0.y(), v0.z(), v1.x(), v1.y(), v1.z(), v2.x(), v2.y(), v2.z(), epsilon, intersectionPoint);
    }

    /**
     * Determine whether the line segment with the end points <code>(p0X, p0Y, p0Z)</code> and <code>(p1X, p1Y, p1Z)</code>
     * intersects the plane given as the general plane equation <i>a*x + b*y + c*z + d = 0</i>,
     * and return the point of intersection.
     * 
     * @param p0X
     *              the x coordinate of the line segment's first end point
     * @param p0Y
     *              the y coordinate of the line segment's first end point
     * @param p0Z
     *              the z coordinate of the line segment's first end point
     * @param p1X
     *              the x coordinate of the line segment's second end point
     * @param p1Y
     *              the y coordinate of the line segment's second end point
     * @param p1Z
     *              the z coordinate of the line segment's second end point
     * @param a
     *              the x factor in the plane equation
     * @param b
     *              the y factor in the plane equation
     * @param c
     *              the z factor in the plane equation
     * @param d
     *              the constant in the plane equation
     * @param intersectionPoint
     *              the point of intersection
     * @return <code>true</code> if the given line segment intersects the plane; <code>false</code> otherwise
     */
    public static boolean intersectLineSegmentPlane(float p0X, float p0Y, float p0Z, float p1X, float p1Y, float p1Z,
            float a, float b, float c, float d, Vector3f intersectionPoint) {
        float dirX = p1X - p0X;
        float dirY = p1Y - p0Y;
        float dirZ = p1Z - p0Z;
        float denom = a * dirX + b * dirY + c * dirZ;
        float t = -(a * p0X + b * p0Y + c * p0Z + d) / denom;
        if (t >= 0.0f && t <= 1.0f) {
            intersectionPoint.x = p0X + t * dirX;
            intersectionPoint.y = p0Y + t * dirY;
            intersectionPoint.z = p0Z + t * dirZ;
            return true;
        }
        return false;
    }

    /**
     * Test whether the line with the general line equation <i>a*x + b*y + c = 0</i> intersects the circle with center
     * <code>(centerX, centerY)</code> and <code>radius</code>.
     * <p>
     * <code>radius</code> is the radius itself, not its square. The line normal <code>(a, b)</code> does not need to be
     * unit length.
     * <p>
     * Reference: <a href="http://math.stackexchange.com/questions/943383/determine-circle-of-intersection-of-plane-and-sphere">http://math.stackexchange.com</a>
     *
     * @param a
     *          the x factor in the line equation
     * @param b
     *          the y factor in the line equation
     * @param c
     *          the constant in the line equation
     * @param centerX
     *          the x coordinate of the circle's center
     * @param centerY
     *          the y coordinate of the circle's center
     * @param radius
     *          the radius of the circle (not squared)
     * @return <code>true</code> iff the line intersects the circle; <code>false</code> otherwise
     */
    public static boolean testLineCircle(float a, float b, float c, float centerX, float centerY, float radius) {
        float denom = (float) Math.sqrt(a * a + b * b);
        float dist = (a * centerX + b * centerY + c) / denom;
        return -radius <= dist && dist <= radius;
    }

    /**
     * Test whether the line with the general line equation <i>a*x + b*y + c = 0</i> intersects the circle with center
     * <code>(centerX, centerY)</code> and <code>radius</code>, and store the center of the line segment of
     * intersection in the <code>(x, y)</code> components of the supplied vector and the half-length of that line segment in the z component.
     * <p>
     * <code>radius</code> is the radius itself, not its square, and the z component receives the half-length, not its
     * square.
     * <p>
     * Reference: <a href="http://math.stackexchange.com/questions/943383/determine-circle-of-intersection-of-plane-and-sphere">http://math.stackexchange.com</a>
     *
     * @param a
     *          the x factor in the line equation
     * @param b
     *          the y factor in the line equation
     * @param c
     *          the constant in the line equation
     * @param centerX
     *          the x coordinate of the circle's center
     * @param centerY
     *          the y coordinate of the circle's center
     * @param radius
     *          the radius of the circle (not squared)
     * @param intersectionCenterAndHL
     *          will hold the center of the line segment of intersection in the <code>(x, y)</code> components and the half-length in the z component
     * @return <code>true</code> iff the line intersects the circle; <code>false</code> otherwise
     */
    public static boolean intersectLineCircle(float a, float b, float c, float centerX, float centerY, float radius, Vector3f intersectionCenterAndHL) {
        float invDenom = Math.invsqrt(a * a + b * b);
        float dist = (a * centerX + b * centerY + c) * invDenom;
        if (-radius <= dist && dist <= radius) {
            intersectionCenterAndHL.x = centerX - dist * a * invDenom;
            intersectionCenterAndHL.y = centerY - dist * b * invDenom;
            intersectionCenterAndHL.z = (float) Math.sqrt(radius * radius - dist * dist);
            return true;
        }
        return false;
    }

    /**
     * Test whether the line defined by the two points <code>(x0, y0)</code> and <code>(x1, y1)</code> intersects the circle with center
     * <code>(centerX, centerY)</code> and <code>radius</code>, and store the center of the line segment of
     * intersection in the <code>(x, y)</code> components of the supplied vector and the half-length of that line segment in the z component.
     * <p>
     * <code>radius</code> is the radius itself, not its square, and the z component receives the half-length, not its
     * square.
     * <p>
     * Reference: <a href="http://math.stackexchange.com/questions/943383/determine-circle-of-intersection-of-plane-and-sphere">http://math.stackexchange.com</a>
     *
     * @param x0
     *          the x coordinate of the first point on the line
     * @param y0
     *          the y coordinate of the first point on the line
     * @param x1
     *          the x coordinate of the second point on the line
     * @param y1
     *          the y coordinate of the second point on the line
     * @param centerX
     *          the x coordinate of the circle's center
     * @param centerY
     *          the y coordinate of the circle's center
     * @param radius
     *          the radius of the circle (not squared)
     * @param intersectionCenterAndHL
     *          will hold the center of the line segment of intersection in the <code>(x, y)</code> components and the half-length in the z component
     * @return <code>true</code> iff the line intersects the circle; <code>false</code> otherwise
     */
    public static boolean intersectLineCircle(float x0, float y0, float x1, float y1, float centerX, float centerY, float radius, Vector3f intersectionCenterAndHL) {
        // Build general line equation from two points and use the other method
        return intersectLineCircle(y0 - y1, x1 - x0, (x0 - x1) * y0 + (y1 - y0) * x0, centerX, centerY, radius, intersectionCenterAndHL);
    }

    /**
     * Test whether the axis-aligned rectangle with minimum corner <code>(minX, minY)</code> and maximum corner <code>(maxX, maxY)</code>
     * intersects the line with the general equation <i>a*x + b*y + c = 0</i>.
     * <p>
     * Reference: <a href="http://www.lighthouse3d.com/tutorials/view-frustum-culling/geometric-approach-testing-boxes-ii/">http://www.lighthouse3d.com</a> ("Geometric Approach - Testing Boxes II")
     * 
     * @param minX
     *          the x coordinate of the minimum corner of the axis-aligned rectangle
     * @param minY
     *          the y coordinate of the minimum corner of the axis-aligned rectangle
     * @param maxX
     *          the x coordinate of the maximum corner of the axis-aligned rectangle
     * @param maxY
     *          the y coordinate of the maximum corner of the axis-aligned rectangle
     * @param a
     *          the x factor in the line equation
     * @param b
     *          the y factor in the line equation
     * @param c
     *          the constant in the plane equation
     * @return <code>true</code> iff the axis-aligned rectangle intersects the line; <code>false</code> otherwise
     */
    public static boolean testAarLine(float minX, float minY, float maxX, float maxY, float a, float b, float c) {
        float pX, pY, nX, nY;
        if (a > 0.0f) {
            pX = maxX;
            nX = minX;
        } else {
            pX = minX;
            nX = maxX;
        }
        if (b > 0.0f) {
            pY = maxY;
            nY = minY;
        } else {
            pY = minY;
            nY = maxY;
        }
        float distN = c + a * nX + b * nY;
        float distP = c + a * pX + b * pY;
        return distN <= 0.0f && distP >= 0.0f;
    }

    /**
     * Test whether the axis-aligned rectangle with minimum corner <code>min</code> and maximum corner <code>max</code>
     * intersects the line with the general equation <i>a*x + b*y + c = 0</i>.
     * <p>
     * Reference: <a href="http://www.lighthouse3d.com/tutorials/view-frustum-culling/geometric-approach-testing-boxes-ii/">http://www.lighthouse3d.com</a> ("Geometric Approach - Testing Boxes II")
     * 
     * @param min
     *          the minimum corner of the axis-aligned rectangle
     * @param max
     *          the maximum corner of the axis-aligned rectangle
     * @param a
     *          the x factor in the line equation
     * @param b
     *          the y factor in the line equation
     * @param c
     *          the constant in the line equation
     * @return <code>true</code> iff the axis-aligned rectangle intersects the line; <code>false</code> otherwise
     */
    public static boolean testAarLine(Vector2fc min, Vector2fc max, float a, float b, float c) {
        return testAarLine(min.x(), min.y(), max.x(), max.y(), a, b, c);
    }

    /**
     * Test whether the axis-aligned rectangle with minimum corner <code>(minX, minY)</code> and maximum corner <code>(maxX, maxY)</code>
     * intersects the line defined by the two points <code>(x0, y0)</code> and <code>(x1, y1)</code>.
     * <p>
     * Reference: <a href="http://www.lighthouse3d.com/tutorials/view-frustum-culling/geometric-approach-testing-boxes-ii/">http://www.lighthouse3d.com</a> ("Geometric Approach - Testing Boxes II")
     * 
     * @param minX
     *          the x coordinate of the minimum corner of the axis-aligned rectangle
     * @param minY
     *          the y coordinate of the minimum corner of the axis-aligned rectangle
     * @param maxX
     *          the x coordinate of the maximum corner of the axis-aligned rectangle
     * @param maxY
     *          the y coordinate of the maximum corner of the axis-aligned rectangle
     * @param x0
     *          the x coordinate of the first point on the line
     * @param y0
     *          the y coordinate of the first point on the line
     * @param x1
     *          the x coordinate of the second point on the line
     * @param y1
     *          the y coordinate of the second point on the line
     * @return <code>true</code> iff the axis-aligned rectangle intersects the line; <code>false</code> otherwise
     */
    public static boolean testAarLine(float minX, float minY, float maxX, float maxY, float x0, float y0, float x1, float y1) {
        float a = y0 - y1;
        float b = x1 - x0;
        float c = -b * y0 - a * x0;
        return testAarLine(minX, minY, maxX, maxY, a, b, c);
    }

    /**
     * Test whether the axis-aligned rectangle with minimum corner <code>(minXA, minYA)</code> and maximum corner <code>(maxXA, maxYA)</code>
     * intersects the axis-aligned rectangle with minimum corner <code>(minXB, minYB)</code> and maximum corner <code>(maxXB, maxYB)</code>.
     * 
     * @param minXA
     *              the x coordinate of the minimum corner of the first axis-aligned rectangle
     * @param minYA
     *              the y coordinate of the minimum corner of the first axis-aligned rectangle
     * @param maxXA
     *              the x coordinate of the maximum corner of the first axis-aligned rectangle
     * @param maxYA
     *              the y coordinate of the maximum corner of the first axis-aligned rectangle
     * @param minXB
     *              the x coordinate of the minimum corner of the second axis-aligned rectangle
     * @param minYB
     *              the y coordinate of the minimum corner of the second axis-aligned rectangle
     * @param maxXB
     *              the x coordinate of the maximum corner of the second axis-aligned rectangle
     * @param maxYB
     *              the y coordinate of the maximum corner of the second axis-aligned rectangle
     * @return <code>true</code> iff both axis-aligned rectangles intersect; <code>false</code> otherwise
     */
    public static boolean testAarAar(float minXA, float minYA, float maxXA, float maxYA, float minXB, float minYB, float maxXB, float maxYB) {
        return maxXA >= minXB && maxYA >= minYB &&  minXA <= maxXB && minYA <= maxYB;
    }

    /**
     * Test whether the axis-aligned rectangle with minimum corner <code>minA</code> and maximum corner <code>maxA</code>
     * intersects the axis-aligned rectangle with minimum corner <code>minB</code> and maximum corner <code>maxB</code>.
     * 
     * @param minA
     *              the minimum corner of the first axis-aligned rectangle
     * @param maxA
     *              the maximum corner of the first axis-aligned rectangle
     * @param minB
     *              the minimum corner of the second axis-aligned rectangle
     * @param maxB
     *              the maximum corner of the second axis-aligned rectangle
     * @return <code>true</code> iff both axis-aligned rectangles intersect; <code>false</code> otherwise
     */
    public static boolean testAarAar(Vector2fc minA, Vector2fc maxA, Vector2fc minB, Vector2fc maxB) {
        return testAarAar(minA.x(), minA.y(), maxA.x(), maxA.y(), minB.x(), minB.y(), maxB.x(), maxB.y());
    }

    /**
     * Test whether a given circle with center <code>(aX, aY)</code> and radius <code>aR</code> and travelled distance vector <code>(maX, maY)</code>
     * intersects a given static circle with center <code>(bX, bY)</code> and radius <code>bR</code>.
     * <p>
     * Note that the case of two moving circles can always be reduced to this case by expressing the moved distance of one of the circles relative
     * to the other.
     * <p>
     * Circles that already touch or overlap at the start intersect, whichever way they move. Touching counts.
     * <p>
     * <code>aR</code> and <code>bR</code> are radii, not squared radii.
     * <p>
     * Reference: <a href="https://www.gamasutra.com/view/feature/131424/pool_hall_lessons_fast_accurate_.php?page=2">https://www.gamasutra.com</a>
     * 
     * @param aX
     *              the x coordinate of the first circle's center
     * @param aY
     *              the y coordinate of the first circle's center
     * @param maX
     *              the x coordinate of the first circle's travelled distance vector
     * @param maY
     *              the y coordinate of the first circle's travelled distance vector
     * @param aR
     *              the radius of the first circle (not squared)
     * @param bX
     *              the x coordinate of the second circle's center
     * @param bY
     *              the y coordinate of the second circle's center
     * @param bR
     *              the radius of the second circle (not squared)
     * @return <code>true</code> if both circle intersect; <code>false</code> otherwise
     */
    public static boolean testMovingCircleCircle(float aX, float aY, float maX, float maY, float aR, float bX, float bY, float bR) {
        float cX = bX - aX, cY = bY - aY;
        float rr = aR + bR;
        float cLen = Math.sqrt(cX * cX + cY * cY);
        if (cLen <= rr) {
            return true; // touching or overlapping at the start
        }
        float mc = maX * cX + maY * cY;
        if (!(mc > 0.0f)) {
            return false; // not approaching
        }
        // |m|² (R² − perpendicular distance²) and |c|² − R² in product form, so neither cancels
        float cross = Math.abs(maX * cY - maY * cX);
        float rm = rr * Math.sqrt(maX * maX + maY * maY);
        float disc = (rm - cross) * (rm + cross);
        if (!(disc >= 0.0f)) {
            return false;
        }
        // the first contact time (|c|² − R²) / (m·c + sqrt(disc)) is at most 1
        return (cLen - rr) * (cLen + rr) <= mc + Math.sqrt(disc);
    }

    /**
     * Test whether a given circle with center <code>centerA</code> and radius <code>aR</code> and travelled distance vector <code>moveA</code>
     * intersects a given static circle with center <code>centerB</code> and radius <code>bR</code>.
     * <p>
     * Note that the case of two moving circles can always be reduced to this case by expressing the moved distance of one of the circles relative
     * to the other.
     * <p>
     * Circles that already touch or overlap at the start intersect, whichever way they move. Touching counts.
     * <p>
     * <code>aR</code> and <code>bR</code> are radii, not squared radii.
     * <p>
     * Reference: <a href="https://www.gamasutra.com/view/feature/131424/pool_hall_lessons_fast_accurate_.php?page=2">https://www.gamasutra.com</a>
     * 
     * @param centerA
     *              the coordinates of the first circle's center
     * @param moveA
     *              the coordinates of the first circle's travelled distance vector
     * @param aR
     *              the radius of the first circle (not squared)
     * @param centerB
     *              the coordinates of the second circle's center
     * @param bR
     *              the radius of the second circle (not squared)
     * @return <code>true</code> if both circle intersect; <code>false</code> otherwise
     */
    public static boolean testMovingCircleCircle(Vector2f centerA, Vector2f moveA, float aR, Vector2f centerB, float bR) {
        return testMovingCircleCircle(centerA.x, centerA.y, moveA.x, moveA.y, aR, centerB.x, centerB.y, bR);
    }

    /**
     * Test whether the one circle with center <code>(aX, aY)</code> and square radius <code>radiusSquaredA</code> intersects the other
     * circle with center <code>(bX, bY)</code> and square radius <code>radiusSquaredB</code>, and store the center of the line segment of
     * intersection in the <code>(x, y)</code> components of the supplied vector and the half-length of that line segment in the z component.
     * <p>
     * This method returns <code>false</code> when one circle contains the other circle.
     * <p>
     * <code>radiusSquaredA</code> and <code>radiusSquaredB</code> are squared radii, not radii. The z component
     * receives the half-length, not its square. Concentric circles (equal centers) always give <code>false</code>, even
     * with equal radii.
     * <p>
     * Reference: <a href="http://gamedev.stackexchange.com/questions/75756/sphere-sphere-intersection-and-circle-sphere-intersection">http://gamedev.stackexchange.com</a>
     * 
     * @param aX
     *              the x coordinate of the first circle's center
     * @param aY
     *              the y coordinate of the first circle's center
     * @param radiusSquaredA
     *              the square of the first circle's radius
     * @param bX
     *              the x coordinate of the second circle's center
     * @param bY
     *              the y coordinate of the second circle's center
     * @param radiusSquaredB
     *              the square of the second circle's radius
     * @param intersectionCenterAndHL
     *              will hold the center of the line segment of intersection in the <code>(x, y)</code> components and
     *              the half-length in the z component
     * @return <code>true</code> iff both circles intersect; <code>false</code> otherwise
     */
    public static boolean intersectCircleCircle(float aX, float aY, float radiusSquaredA, float bX, float bY, float radiusSquaredB, Vector3f intersectionCenterAndHL) {
        float dX = bX - aX, dY = bY - aY;
        float distSquared = dX * dX + dY * dY;
        float h = 0.5f + (radiusSquaredA - radiusSquaredB) / (2.0f * distSquared);
        float r2 = radiusSquaredA - h * h * distSquared;
        if (r2 >= 0.0f) {
            float r_i = (float) Math.sqrt(r2);
            intersectionCenterAndHL.x = aX + h * dX;
            intersectionCenterAndHL.y = aY + h * dY;
            intersectionCenterAndHL.z = r_i;
            return true;
        }
        return false;
    }

    /**
     * Test whether the one circle with center <code>centerA</code> and square radius <code>radiusSquaredA</code> intersects the other
     * circle with center <code>centerB</code> and square radius <code>radiusSquaredB</code>, and store the center of the line segment of
     * intersection in the <code>(x, y)</code> components of the supplied vector and the half-length of that line segment in the z component.
     * <p>
     * This method returns <code>false</code> when one circle contains the other circle.
     * <p>
     * <code>radiusSquaredA</code> and <code>radiusSquaredB</code> are squared radii, not radii. The z component
     * receives the half-length, not its square. Concentric circles (equal centers) always give <code>false</code>, even
     * with equal radii.
     * <p>
     * Reference: <a href="http://gamedev.stackexchange.com/questions/75756/sphere-sphere-intersection-and-circle-sphere-intersection">http://gamedev.stackexchange.com</a>
     * 
     * @param centerA
     *              the first circle's center
     * @param radiusSquaredA
     *              the square of the first circle's radius
     * @param centerB
     *              the second circle's center
     * @param radiusSquaredB
     *              the square of the second circle's radius
     * @param intersectionCenterAndHL
     *              will hold the center of the line segment of intersection in the <code>(x, y)</code> components and the half-length in the z component
     * @return <code>true</code> iff both circles intersect; <code>false</code> otherwise
     */
    public static boolean intersectCircleCircle(Vector2fc centerA, float radiusSquaredA, Vector2fc centerB, float radiusSquaredB, Vector3f intersectionCenterAndHL) {
        return intersectCircleCircle(centerA.x(), centerA.y(), radiusSquaredA, centerB.x(), centerB.y(), radiusSquaredB, intersectionCenterAndHL);
    }

    /**
     * Test whether the one circle with center <code>(aX, aY)</code> and squared radius <code>rA</code> intersects the
     * other circle with center <code>(bX, bY)</code> and squared radius <code>rB</code>.
     * Both are squared radii, not radii.
     * <p>
     * This method returns <code>true</code> when one circle contains the other circle.
     * <p>
     * Reference: <a href="http://math.stackexchange.com/questions/275514/two-circles-overlap">http://math.stackexchange.com/</a>
     * 
     * @param aX
     *              the x coordinate of the first circle's center
     * @param aY
     *              the y coordinate of the first circle's center
     * @param rA
     *              the square of the first circle's radius
     * @param bX
     *              the x coordinate of the second circle's center
     * @param bY
     *              the y coordinate of the second circle's center
     * @param rB
     *              the square of the second circle's radius
     * @return <code>true</code> iff both circles intersect; <code>false</code> otherwise
     */
    public static boolean testCircleCircle(float aX, float aY, float rA, float bX, float bY, float rB) {
        float d = (aX - bX) * (aX - bX) + (aY - bY) * (aY - bY);
        // rA and rB are squared radii, as documented (before 2.4.0 they were used as radii)
        float ra = (float) Math.sqrt(rA), rb = (float) Math.sqrt(rB);
        return d <= (ra + rb) * (ra + rb);
    }

    /**
     * Test whether the one circle with center <code>centerA</code> and square radius <code>radiusSquaredA</code> intersects the other
     * circle with center <code>centerB</code> and square radius <code>radiusSquaredB</code>.
     * <p>
     * This method returns <code>true</code> when one circle contains the other circle.
     * <p>
     * <code>radiusSquaredA</code> and <code>radiusSquaredB</code> are squared radii, not radii.
     * <p>
     * Reference: <a href="http://gamedev.stackexchange.com/questions/75756/sphere-sphere-intersection-and-circle-sphere-intersection">http://gamedev.stackexchange.com</a>
     * 
     * @param centerA
     *              the first circle's center
     * @param radiusSquaredA
     *              the square of the first circle's radius
     * @param centerB
     *              the second circle's center
     * @param radiusSquaredB
     *              the square of the second circle's radius
     * @return <code>true</code> iff both circles intersect; <code>false</code> otherwise
     */
    public static boolean testCircleCircle(Vector2fc centerA, float radiusSquaredA, Vector2fc centerB, float radiusSquaredB) {
        return testCircleCircle(centerA.x(), centerA.y(), radiusSquaredA, centerB.x(), centerB.y(), radiusSquaredB);
    }

    /**
     * Determine the signed distance of the given point <code>(pointX, pointY)</code> to the line specified via its general plane equation
     * <i>a*x + b*y + c = 0</i>.
     * <p>
     * Reference: <a href="http://mathworld.wolfram.com/Point-LineDistance2-Dimensional.html">http://mathworld.wolfram.com</a>
     * 
     * @param pointX
     *              the x coordinate of the point
     * @param pointY
     *              the y coordinate of the point
     * @param a
     *              the x factor in the plane equation
     * @param b
     *              the y factor in the plane equation
     * @param c
     *              the constant in the plane equation
     * @return the distance between the point and the line
     */
    public static float distancePointLine(float pointX, float pointY, float a, float b, float c) {
        float denom = (float) Math.sqrt(a * a + b * b);
        return (a * pointX + b * pointY + c) / denom;
    }

    /**
     * Determine the signed distance of the given point <code>(pointX, pointY)</code> to the line defined by the two points <code>(x0, y0)</code> and <code>(x1, y1)</code>.
     * <p>
     * Reference: <a href="http://mathworld.wolfram.com/Point-LineDistance2-Dimensional.html">http://mathworld.wolfram.com</a>
     * 
     * @param pointX
     *              the x coordinate of the point
     * @param pointY
     *              the y coordinate of the point
     * @param x0
     *              the x coordinate of the first point on the line
     * @param y0
     *              the y coordinate of the first point on the line
     * @param x1
     *              the x coordinate of the second point on the line
     * @param y1
     *              the y coordinate of the second point on the line
     * @return the distance between the point and the line
     */
    public static float distancePointLine(float pointX, float pointY, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float denom = (float) Math.sqrt(dx * dx + dy * dy);
        return (dx * (y0 - pointY) - (x0 - pointX) * dy) / denom;
    }

    /**
     * Compute the distance of the given point <code>(pX, pY, pZ)</code> to the line defined by the two points <code>(x0, y0, z0)</code> and <code>(x1, y1, z1)</code>.
     * <p>
     * Reference: <a href="http://mathworld.wolfram.com/Point-LineDistance3-Dimensional.html">http://mathworld.wolfram.com</a>
     * 
     * @param pX
     *          the x coordinate of the point
     * @param pY
     *          the y coordinate of the point
     * @param pZ
     *          the z coordinate of the point
     * @param x0
     *          the x coordinate of the first point on the line
     * @param y0
     *          the y coordinate of the first point on the line
     * @param z0
     *          the z coordinate of the first point on the line
     * @param x1
     *          the x coordinate of the second point on the line
     * @param y1
     *          the y coordinate of the second point on the line
     * @param z1
     *          the z coordinate of the second point on the line
     * @return the distance between the point and the line
     */
    public static float distancePointLine(float pX, float pY, float pZ, 
            float x0, float y0, float z0, float x1, float y1, float z1) {
        float d21x = x1 - x0, d21y = y1 - y0, d21z = z1 - z0;
        float d10x = x0 - pX, d10y = y0 - pY, d10z = z0 - pZ;
        float cx = d21y * d10z - d21z * d10y, cy = d21z * d10x - d21x * d10z, cz = d21x * d10y - d21y * d10x;
        return (float) Math.sqrt((cx*cx + cy*cy + cz*cz) / (d21x*d21x + d21y*d21y + d21z*d21z));
    }

    /**
     * Test whether the ray with given origin <code>(originX, originY)</code> and direction <code>(dirX, dirY)</code> intersects the line
     * containing the given point <code>(pointX, pointY)</code> and having the normal <code>(normalX, normalY)</code>, and return the
     * value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the intersection point.
     * <p>
     * This method returns <code>-1.0</code> if the ray does not intersect the line, because it is either parallel to the line or its direction points
     * away from the line or the ray's origin is on the <i>negative</i> side of the line (i.e. the line's normal points away from the ray's origin).
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's direction
     * @param dirY
     *              the y coordinate of the ray's direction
     * @param pointX
     *              the x coordinate of a point on the line
     * @param pointY
     *              the y coordinate of a point on the line
     * @param normalX
     *              the x coordinate of the line's normal
     * @param normalY
     *              the y coordinate of the line's normal
     * @param epsilon
     *              some small epsilon for when the ray is parallel to the line
     * @return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the intersection point, if the ray
     *         intersects the line; <code>-1.0</code> otherwise
     */
    public static float intersectRayLine(float originX, float originY, float dirX, float dirY, float pointX, float pointY, float normalX, float normalY, float epsilon) {
        float denom = normalX * dirX + normalY * dirY;
        if (denom < epsilon && denom != 0.0f) { // parallel rays never hit (Infinity before 2.4.0)
            float t = ((pointX - originX) * normalX + (pointY - originY) * normalY) / denom;
            if (t >= 0.0f)
                return t;
        }
        return -1.0f;
    }

    /**
     * Test whether the ray with given <code>origin</code> and direction <code>dir</code> intersects the line
     * containing the given <code>point</code> and having the given <code>normal</code>, and return the
     * value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the intersection point.
     * <p>
     * This method returns <code>-1.0</code> if the ray does not intersect the line, because it is either parallel to the line or its direction points
     * away from the line or the ray's origin is on the <i>negative</i> side of the line (i.e. the line's normal points away from the ray's origin).
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's direction
     * @param point
     *              a point on the line
     * @param normal
     *              the line's normal
     * @param epsilon
     *              some small epsilon for when the ray is parallel to the line
     * @return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the intersection point, if the ray
     *         intersects the line; <code>-1.0</code> otherwise
     */
    public static float intersectRayLine(Vector2fc origin, Vector2fc dir, Vector2fc point, Vector2fc normal, float epsilon) {
        return intersectRayLine(origin.x(), origin.y(), dir.x(), dir.y(), point.x(), point.y(), normal.x(), normal.y(), epsilon);
    }

    /**
     * Determine whether the ray with given origin <code>(originX, originY)</code> and direction <code>(dirX, dirY)</code> intersects the undirected line segment
     * given by the two end points <code>(aX, bY)</code> and <code>(bX, bY)</code>, and return the value of the parameter <i>t</i> in the ray equation
     * <i>p(t) = origin + t * dir</i> of the intersection point, if any.
     * <p>
     * This method returns <code>-1.0</code> if the ray does not intersect the line segment.
     * 
     * @see #intersectRayLineSegment(Vector2fc, Vector2fc, Vector2fc, Vector2fc)
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's direction
     * @param dirY
     *              the y coordinate of the ray's direction
     * @param aX
     *              the x coordinate of the line segment's first end point
     * @param aY
     *              the y coordinate of the line segment's first end point
     * @param bX
     *              the x coordinate of the line segment's second end point
     * @param bY
     *              the y coordinate of the line segment's second end point
     * @return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the intersection point, if the ray
     *         intersects the line segment; <code>-1.0</code> otherwise
     */
    public static float intersectRayLineSegment(float originX, float originY, float dirX, float dirY, float aX, float aY, float bX, float bY) {
        return (float) Intersectiond.intersectRayLineSegment(originX, originY, dirX, dirY, aX, aY, bX, bY);
    }

    /**
     * Determine whether the ray with given <code>origin</code> and direction <code>dir</code> intersects the undirected line segment
     * given by the two end points <code>a</code> and <code>b</code>, and return the value of the parameter <i>t</i> in the ray equation
     * <i>p(t) = origin + t * dir</i> of the intersection point, if any.
     * <p>
     * This method returns <code>-1.0</code> if the ray does not intersect the line segment.
     * 
     * @see #intersectRayLineSegment(float, float, float, float, float, float, float, float)
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's direction
     * @param a
     *              the line segment's first end point
     * @param b
     *              the line segment's second end point
     * @return the value of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the intersection point, if the ray
     *         intersects the line segment; <code>-1.0</code> otherwise
     */
    public static float intersectRayLineSegment(Vector2fc origin, Vector2fc dir, Vector2fc a, Vector2fc b) {
        return intersectRayLineSegment(origin.x(), origin.y(), dir.x(), dir.y(), a.x(), a.y(), b.x(), b.y());
    }

    /**
     * Test whether the axis-aligned rectangle with minimum corner <code>(minX, minY)</code> and maximum corner <code>(maxX, maxY)</code>
     * intersects the circle with the given center <code>(centerX, centerY)</code> and square radius <code>radiusSquared</code>.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius.
     * <p>
     * Reference: <a href="http://stackoverflow.com/questions/4578967/cube-sphere-intersection-test#answer-4579069">http://stackoverflow.com</a>
     * 
     * @param minX
     *          the x coordinate of the minimum corner of the axis-aligned rectangle
     * @param minY
     *          the y coordinate of the minimum corner of the axis-aligned rectangle
     * @param maxX
     *          the x coordinate of the maximum corner of the axis-aligned rectangle
     * @param maxY
     *          the y coordinate of the maximum corner of the axis-aligned rectangle
     * @param centerX
     *          the x coordinate of the circle's center
     * @param centerY
     *          the y coordinate of the circle's center
     * @param radiusSquared
     *          the square of the circle's radius
     * @return <code>true</code> iff the axis-aligned rectangle intersects the circle; <code>false</code> otherwise
     */
    public static boolean testAarCircle(float minX, float minY, float maxX, float maxY, float centerX, float centerY, float radiusSquared) {
        float radius2 = radiusSquared;
        if (centerX < minX) {
            float d = (centerX - minX);
            radius2 -= d * d;
        } else if (centerX > maxX) {
            float d = (centerX - maxX);
            radius2 -= d * d;
        }
        if (centerY < minY) {
            float d = (centerY - minY);
            radius2 -= d * d;
        } else if (centerY > maxY) {
            float d = (centerY - maxY);
            radius2 -= d * d;
        }
        return radius2 >= 0.0f;
    }

    /**
     * Test whether the axis-aligned rectangle with minimum corner <code>min</code> and maximum corner <code>max</code>
     * intersects the circle with the given <code>center</code> and square radius <code>radiusSquared</code>.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius.
     * <p>
     * Reference: <a href="http://stackoverflow.com/questions/4578967/cube-sphere-intersection-test#answer-4579069">http://stackoverflow.com</a>
     * 
     * @param min
     *          the minimum corner of the axis-aligned rectangle
     * @param max
     *          the maximum corner of the axis-aligned rectangle
     * @param center
     *          the circle's center
     * @param radiusSquared
     *          the square of the circle's radius
     * @return <code>true</code> iff the axis-aligned rectangle intersects the circle; <code>false</code> otherwise
     */
    public static boolean testAarCircle(Vector2fc min, Vector2fc max, Vector2fc center, float radiusSquared) {
        return testAarCircle(min.x(), min.y(), max.x(), max.y(), center.x(), center.y(), radiusSquared);
    }

    /**
     * Determine the closest point on the triangle with the given vertices <code>(v0X, v0Y)</code>, <code>(v1X, v1Y)</code>, <code>(v2X, v2Y)</code>
     * between that triangle and the given point <code>(pX, pY)</code> and store that point into the given <code>result</code>.
     * <p>
     * Additionally, this method returns whether the closest point is a vertex ({@link #POINT_ON_TRIANGLE_VERTEX_0}, {@link #POINT_ON_TRIANGLE_VERTEX_1}, {@link #POINT_ON_TRIANGLE_VERTEX_2})
     * of the triangle, lies on an edge ({@link #POINT_ON_TRIANGLE_EDGE_01}, {@link #POINT_ON_TRIANGLE_EDGE_12}, {@link #POINT_ON_TRIANGLE_EDGE_20})
     * or on the {@link #POINT_ON_TRIANGLE_FACE face} of the triangle.
     * <p>
     * Reference: Book "Real-Time Collision Detection" chapter 5.1.5 "Closest Point on Triangle to Point"
     * 
     * @param v0X
     *          the x coordinate of the first vertex of the triangle
     * @param v0Y
     *          the y coordinate of the first vertex of the triangle
     * @param v1X
     *          the x coordinate of the second vertex of the triangle
     * @param v1Y
     *          the y coordinate of the second vertex of the triangle
     * @param v2X
     *          the x coordinate of the third vertex of the triangle
     * @param v2Y
     *          the y coordinate of the third vertex of the triangle
     * @param pX
     *          the x coordinate of the point
     * @param pY
     *          the y coordinate of the point
     * @param result
     *          will hold the closest point
     * @return one of {@link #POINT_ON_TRIANGLE_VERTEX_0}, {@link #POINT_ON_TRIANGLE_VERTEX_1}, {@link #POINT_ON_TRIANGLE_VERTEX_2},
     *                {@link #POINT_ON_TRIANGLE_EDGE_01}, {@link #POINT_ON_TRIANGLE_EDGE_12}, {@link #POINT_ON_TRIANGLE_EDGE_20} or
     *                {@link #POINT_ON_TRIANGLE_FACE}
     */
    public static int findClosestPointOnTriangle(float v0X, float v0Y, float v1X, float v1Y, float v2X, float v2Y, float pX, float pY, Vector2f result) {
        // inside the closed triangle (exact orientation signs, either winding): p is its own closest point
        double area = RobustPredicates.orient2d(v0X, v0Y, v1X, v1Y, v2X, v2Y);
        if (area != 0) {
            double o1 = RobustPredicates.orient2d(v0X, v0Y, v1X, v1Y, pX, pY);
            double o2 = RobustPredicates.orient2d(v1X, v1Y, v2X, v2Y, pX, pY);
            double o3 = RobustPredicates.orient2d(v2X, v2Y, v0X, v0Y, pX, pY);
            if (area > 0 ? o1 >= 0 && o2 >= 0 && o3 >= 0 : o1 <= 0 && o2 <= 0 && o3 <= 0) {
                result.x = pX;
                result.y = pY;
                return POINT_ON_TRIANGLE_FACE;
            }
        }
        double ta = Intersectiond.segmentParam(v0X, v0Y, 0, v1X, v1Y, 0, pX, pY, 0);
        double qaX = ta >= 1 ? v1X : v0X + ta * (v1X - v0X);
        double qaY = ta >= 1 ? v1Y : v0Y + ta * (v1Y - v0Y);
        double da = (pX - qaX) * (pX - qaX) + (pY - qaY) * (pY - qaY);
        int ca = ta <= 0 ? POINT_ON_TRIANGLE_VERTEX_0 : ta >= 1 ? POINT_ON_TRIANGLE_VERTEX_1 : POINT_ON_TRIANGLE_EDGE_01;
        double tb = Intersectiond.segmentParam(v1X, v1Y, 0, v2X, v2Y, 0, pX, pY, 0);
        double qbX = tb >= 1 ? v2X : v1X + tb * (v2X - v1X);
        double qbY = tb >= 1 ? v2Y : v1Y + tb * (v2Y - v1Y);
        double db = (pX - qbX) * (pX - qbX) + (pY - qbY) * (pY - qbY);
        int cb = tb <= 0 ? POINT_ON_TRIANGLE_VERTEX_1 : tb >= 1 ? POINT_ON_TRIANGLE_VERTEX_2 : POINT_ON_TRIANGLE_EDGE_12;
        double tc = Intersectiond.segmentParam(v2X, v2Y, 0, v0X, v0Y, 0, pX, pY, 0);
        double qcX = tc >= 1 ? v0X : v2X + tc * (v0X - v2X);
        double qcY = tc >= 1 ? v0Y : v2Y + tc * (v0Y - v2Y);
        double dc = (pX - qcX) * (pX - qcX) + (pY - qcY) * (pY - qcY);
        int cc = tc <= 0 ? POINT_ON_TRIANGLE_VERTEX_2 : tc >= 1 ? POINT_ON_TRIANGLE_VERTEX_0 : POINT_ON_TRIANGLE_EDGE_20;
        if (da <= db && da <= dc) {
            result.x = (float) qaX;
            result.y = (float) qaY;
            return ca;
        }
        if (db <= dc) {
            result.x = (float) qbX;
            result.y = (float) qbY;
            return cb;
        }
        result.x = (float) qcX;
        result.y = (float) qcY;
        return cc;
    }

    /**
     * Determine the closest point on the triangle with the vertices <code>v0</code>, <code>v1</code>, <code>v2</code>
     * between that triangle and the given point <code>p</code> and store that point into the given <code>result</code>.
     * <p>
     * Additionally, this method returns whether the closest point is a vertex ({@link #POINT_ON_TRIANGLE_VERTEX_0}, {@link #POINT_ON_TRIANGLE_VERTEX_1}, {@link #POINT_ON_TRIANGLE_VERTEX_2})
     * of the triangle, lies on an edge ({@link #POINT_ON_TRIANGLE_EDGE_01}, {@link #POINT_ON_TRIANGLE_EDGE_12}, {@link #POINT_ON_TRIANGLE_EDGE_20})
     * or on the {@link #POINT_ON_TRIANGLE_FACE face} of the triangle.
     * <p>
     * Reference: Book "Real-Time Collision Detection" chapter 5.1.5 "Closest Point on Triangle to Point"
     * 
     * @param v0
     *          the first vertex of the triangle
     * @param v1
     *          the second vertex of the triangle
     * @param v2
     *          the third vertex of the triangle
     * @param p
     *          the point
     * @param result
     *          will hold the closest point
     * @return one of {@link #POINT_ON_TRIANGLE_VERTEX_0}, {@link #POINT_ON_TRIANGLE_VERTEX_1}, {@link #POINT_ON_TRIANGLE_VERTEX_2},
     *                {@link #POINT_ON_TRIANGLE_EDGE_01}, {@link #POINT_ON_TRIANGLE_EDGE_12}, {@link #POINT_ON_TRIANGLE_EDGE_20} or
     *                {@link #POINT_ON_TRIANGLE_FACE}
     */
    public static int findClosestPointOnTriangle(Vector2fc v0, Vector2fc v1, Vector2fc v2, Vector2fc p, Vector2f result) {
        return findClosestPointOnTriangle(v0.x(), v0.y(), v1.x(), v1.y(), v2.x(), v2.y(), p.x(), p.y(), result);
    }

    /**
     * Test whether the given ray with the origin <code>(originX, originY)</code> and normalized direction
     * <code>(dirX, dirY)</code> intersects the given circle with center <code>(centerX, centerY)</code>
     * and square radius <code>radiusSquared</code>,
     * and store the values of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> for both points (near
     * and far) of intersections into the given <code>result</code> vector.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the circle.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius. The direction must be unit length: the
     * returned <i>t</i> values are distances along it.
     * <p>
     * Reference: <a href="http://www.scratchapixel.com/lessons/3d-basic-rendering/minimal-ray-tracer-rendering-simple-shapes/ray-sphere-intersection">http://www.scratchapixel.com/</a>
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's normalized direction
     * @param dirY
     *              the y coordinate of the ray's normalized direction
     * @param centerX
     *              the x coordinate of the circle's center
     * @param centerY
     *              the y coordinate of the circle's center
     * @param radiusSquared
     *              the square of the circle's radius
     * @param result
     *              a vector that will contain the values of the parameter <i>t</i> in the ray equation
     *              <i>p(t) = origin + t * dir</i> for both points (near, far) of intersections with the circle
     * @return <code>true</code> if the ray intersects the circle; <code>false</code> otherwise
     */
    public static boolean intersectRayCircle(float originX, float originY, float dirX, float dirY, 
            float centerX, float centerY, float radiusSquared, Vector2f result) {
        double lx = centerX - (double) originX, ly = centerY - (double) originY;
        double tca = lx * dirX + ly * dirY;
        // the perpendicular distance directly: |L|² − tca² cancels for distant circles (thousands of wrong answers per
        // 10k random distant rays before 2.4.0)
        double px = lx - tca * dirX, py = ly - tca * dirY;
        double d2 = px * px + py * py;
        if (d2 > radiusSquared)
            return false;
        double thc = java.lang.Math.sqrt(radiusSquared - d2);
        // the root of larger magnitude without cancellation, the other from t0 t1 = (|L| − r)(|L| + r)
        double len = java.lang.Math.sqrt(lx * lx + ly * ly), r = java.lang.Math.sqrt(radiusSquared);
        double prod = (len - r) * (len + r);
        double t0, t1;
        if (tca >= 0.0) {
            t1 = tca + thc;
            t0 = t1 == 0.0 ? 0.0 : prod / t1;
        } else {
            t0 = tca - thc;
            t1 = prod / t0;
        }
        if (t1 >= 0.0) { // tangent rays (t0 == t1) hit
            result.x = (float) t0;
            result.y = (float) t1;
            return true;
        }
        return false;
    }

    /**
     * Test whether the ray with the given <code>origin</code> and normalized direction <code>dir</code>
     * intersects the circle with the given <code>center</code> and square radius <code>radiusSquared</code>,
     * and store the values of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> for both points (near
     * and far) of intersections into the given <code>result</code> vector.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the circle.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius. The direction must be unit length: the
     * returned <i>t</i> values are distances along it.
     * <p>
     * Reference: <a href="http://www.scratchapixel.com/lessons/3d-basic-rendering/minimal-ray-tracer-rendering-simple-shapes/ray-sphere-intersection">http://www.scratchapixel.com/</a>
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's normalized direction
     * @param center
     *              the circle's center
     * @param radiusSquared
     *              the square of the circle's radius
     * @param result
     *              a vector that will contain the values of the parameter <i>t</i> in the ray equation
     *              <i>p(t) = origin + t * dir</i> for both points (near, far) of intersections with the circle
     * @return <code>true</code> if the ray intersects the circle; <code>false</code> otherwise
     */
    public static boolean intersectRayCircle(Vector2fc origin, Vector2fc dir, Vector2fc center, float radiusSquared, Vector2f result) {
        return intersectRayCircle(origin.x(), origin.y(), dir.x(), dir.y(), center.x(), center.y(), radiusSquared, result);
    }

    /**
     * Test whether the given ray with the origin <code>(originX, originY)</code> and normalized direction
     * <code>(dirX, dirY)</code> intersects the given circle with center <code>(centerX, centerY)</code>
     * and square radius <code>radiusSquared</code>.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the circle.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius. The direction must be unit length.
     * <p>
     * Reference: <a href="http://www.scratchapixel.com/lessons/3d-basic-rendering/minimal-ray-tracer-rendering-simple-shapes/ray-sphere-intersection">http://www.scratchapixel.com/</a>
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's normalized direction
     * @param dirY
     *              the y coordinate of the ray's normalized direction
     * @param centerX
     *              the x coordinate of the circle's center
     * @param centerY
     *              the y coordinate of the circle's center
     * @param radiusSquared
     *              the square of the circle's radius
     * @return <code>true</code> if the ray intersects the circle; <code>false</code> otherwise
     */
    public static boolean testRayCircle(float originX, float originY, float dirX, float dirY, 
            float centerX, float centerY, float radiusSquared) {
        double lx = centerX - (double) originX, ly = centerY - (double) originY;
        double tca = lx * dirX + ly * dirY;
        // the perpendicular distance directly: |L|² − tca² cancels for distant circles (thousands of wrong answers per
        // 10k random distant rays before 2.4.0)
        double px = lx - tca * dirX, py = ly - tca * dirY;
        double d2 = px * px + py * py;
        if (d2 > radiusSquared)
            return false;
        double thc = java.lang.Math.sqrt(radiusSquared - d2);
        // the root of larger magnitude without cancellation, the other from t0 t1 = (|L| − r)(|L| + r)
        double len = java.lang.Math.sqrt(lx * lx + ly * ly), r = java.lang.Math.sqrt(radiusSquared);
        double prod = (len - r) * (len + r);
        double t1 = tca >= 0.0 ? tca + thc : prod / (tca - thc); // the far root
        return t1 >= 0.0;
    }

    /**
     * Test whether the ray with the given <code>origin</code> and normalized direction <code>dir</code>
     * intersects the circle with the given <code>center</code> and square radius.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the circle.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius. The direction must be unit length.
     * <p>
     * Reference: <a href="http://www.scratchapixel.com/lessons/3d-basic-rendering/minimal-ray-tracer-rendering-simple-shapes/ray-sphere-intersection">http://www.scratchapixel.com/</a>
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's normalized direction
     * @param center
     *              the circle's center
     * @param radiusSquared
     *              the square of the circle's radius
     * @return <code>true</code> if the ray intersects the circle; <code>false</code> otherwise
     */
    public static boolean testRayCircle(Vector2fc origin, Vector2fc dir, Vector2fc center, float radiusSquared) {
        return testRayCircle(origin.x(), origin.y(), dir.x(), dir.y(), center.x(), center.y(), radiusSquared);
    }

    /**
     * Determine whether the given ray with the origin <code>(originX, originY)</code> and direction <code>(dirX, dirY)</code>
     * intersects the axis-aligned rectangle given as its minimum corner <code>(minX, minY)</code> and maximum corner <code>(maxX, maxY)</code>,
     * and return the values of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the near and far point of intersection
     * as well as the side of the axis-aligned rectangle the ray intersects.
     * <p>
     * This method also detects an intersection for a ray whose origin lies inside the axis-aligned rectangle.
     * <p>
     * Reference: <a href="https://dl.acm.org/citation.cfm?id=1198748">An Efficient and Robust Ray–Box Intersection</a>
     * 
     * @see #intersectRayAar(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's direction
     * @param dirY
     *              the y coordinate of the ray's direction
     * @param minX
     *              the x coordinate of the minimum corner of the axis-aligned rectangle
     * @param minY
     *              the y coordinate of the minimum corner of the axis-aligned rectangle
     * @param maxX
     *              the x coordinate of the maximum corner of the axis-aligned rectangle
     * @param maxY
     *              the y coordinate of the maximum corner of the axis-aligned rectangle
     * @param result
     *              a vector which will hold the values of the parameter <i>t</i> in the ray equation
     *              <i>p(t) = origin + t * dir</i> of the near and far point of intersection
     * @return the side on which the near intersection occurred as one of
     *         {@link #AAR_SIDE_MINX}, {@link #AAR_SIDE_MINY}, {@link #AAR_SIDE_MAXX} or {@link #AAR_SIDE_MAXY};
     *         or <code>-1</code> if the ray does not intersect the axis-aligned rectangle;
     */
    public static int intersectRayAar(float originX, float originY, float dirX, float dirY, 
            float minX, float minY, float maxX, float maxY, Vector2f result) {
        if (originX != originX || originY != originY || dirX != dirX || dirY != dirY) {
            return -1; // NaN never hits
        }
        float invDirX = 1.0f / dirX, invDirY = 1.0f / dirY;
        float tNear, tFar, tymin, tymax;
        if (invDirX >= 0.0f) {
            tNear = (minX - originX) * invDirX;
            tFar = (maxX - originX) * invDirX;
        } else {
            tNear = (maxX - originX) * invDirX;
            tFar = (minX - originX) * invDirX;
        }
        if (invDirY >= 0.0f) {
            tymin = (minY - originY) * invDirY;
            tymax = (maxY - originY) * invDirY;
        } else {
            tymin = (maxY - originY) * invDirY;
            tymax = (minY - originY) * invDirY;
        }
        if (tNear > tymax || tymin > tFar)
            return OUTSIDE;
        tNear = tymin > tNear || Float.isNaN(tNear) ? tymin : tNear;
        tFar = tymax < tFar || Float.isNaN(tFar) ? tymax : tFar;
        int side = -1; // no intersection side
        if (tNear <= tFar && tFar >= 0.0f) {
            float px = originX + tNear * dirX;
            float py = originY + tNear * dirY;
            result.x = tNear;
            result.y = tFar;
            float daX = Math.abs(px - minX);
            float daY = Math.abs(py - minY);
            float dbX = Math.abs(px - maxX);
            float dbY = Math.abs(py - maxY);
            side = 0; // min x coordinate
            float min = daX;
            if (daY < min) {
                min = daY;
                side = 1; // min y coordinate
            }
            if (dbX < min) {
                min = dbX;
                side = 2; // max xcoordinate
            }
            if (dbY < min)
                side = 3; // max y coordinate
        }
        return side;
    }

    /**
     * Determine whether the given ray with the given <code>origin</code> and direction <code>dir</code>
     * intersects the axis-aligned rectangle given as its minimum corner <code>min</code> and maximum corner <code>max</code>,
     * and return the values of the parameter <i>t</i> in the ray equation <i>p(t) = origin + t * dir</i> of the near and far point of intersection
     * as well as the side of the axis-aligned rectangle the ray intersects.
     * <p>
     * This method also detects an intersection for a ray whose origin lies inside the axis-aligned rectangle.
     * <p>
     * Reference: <a href="https://dl.acm.org/citation.cfm?id=1198748">An Efficient and Robust Ray–Box Intersection</a>
     * 
     * @see #intersectRayAar(float, float, float, float, float, float, float, float, Vector2f)
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's direction
     * @param min
     *              the minimum corner of the axis-aligned rectangle
     * @param max
     *              the maximum corner of the axis-aligned rectangle
     * @param result
     *              a vector which will hold the values of the parameter <i>t</i> in the ray equation
     *              <i>p(t) = origin + t * dir</i> of the near and far point of intersection
     * @return the side on which the near intersection occurred as one of
     *         {@link #AAR_SIDE_MINX}, {@link #AAR_SIDE_MINY}, {@link #AAR_SIDE_MAXX} or {@link #AAR_SIDE_MAXY};
     *         or <code>-1</code> if the ray does not intersect the axis-aligned rectangle;
     */
    public static int intersectRayAar(Vector2fc origin, Vector2fc dir, Vector2fc min, Vector2fc max, Vector2f result) {
        return intersectRayAar(origin.x(), origin.y(), dir.x(), dir.y(), min.x(), min.y(), max.x(), max.y(), result);
    }

    /**
     * Determine whether the undirected line segment with the end points <code>(p0X, p0Y)</code> and <code>(p1X, p1Y)</code>
     * intersects the axis-aligned rectangle given as its minimum corner <code>(minX, minY)</code> and maximum corner <code>(maxX, maxY)</code>,
     * and store the values of the parameter <i>t</i> in the ray equation <i>p(t) = p0 + t * (p1 - p0)</i> of the near and far point of intersection
     * into <code>result</code>.
     * <p>
     * This method also detects an intersection of a line segment whose either end point lies inside the axis-aligned rectangle.
     * <p>
     * Reference: <a href="https://dl.acm.org/citation.cfm?id=1198748">An Efficient and Robust Ray–Box Intersection</a>
     *
     * @see #intersectLineSegmentAar(Vector2fc, Vector2fc, Vector2fc, Vector2fc, Vector2f)
     * 
     * @param p0X
     *              the x coordinate of the line segment's first end point
     * @param p0Y
     *              the y coordinate of the line segment's first end point
     * @param p1X
     *              the x coordinate of the line segment's second end point
     * @param p1Y
     *              the y coordinate of the line segment's second end point
     * @param minX
     *              the x coordinate of the minimum corner of the axis-aligned rectangle
     * @param minY
     *              the y coordinate of the minimum corner of the axis-aligned rectangle
     * @param maxX
     *              the x coordinate of the maximum corner of the axis-aligned rectangle
     * @param maxY
     *              the y coordinate of the maximum corner of the axis-aligned rectangle
     * @param result
     *              a vector which will hold the values of the parameter <i>t</i> in the ray equation
     *              <i>p(t) = p0 + t * (p1 - p0)</i> of the near and far point of intersection
     * @return {@link #INSIDE} if the line segment lies completely inside of the axis-aligned rectangle; or
     *         {@link #OUTSIDE} if the line segment lies completely outside of the axis-aligned rectangle; or
     *         {@link #ONE_INTERSECTION} if one of the end points of the line segment lies inside of the axis-aligned rectangle; or
     *         {@link #TWO_INTERSECTION} if the line segment intersects two edges of the axis-aligned rectangle or lies on one edge of the rectangle
     */
    public static int intersectLineSegmentAar(float p0X, float p0Y, float p1X, float p1Y, 
            float minX, float minY, float maxX, float maxY, Vector2f result) {
        float dirX = p1X - p0X, dirY = p1Y - p0Y;
        float invDirX = 1.0f / dirX, invDirY = 1.0f / dirY;
        float tNear, tFar, tymin, tymax;
        if (invDirX >= 0.0f) {
            tNear = (minX - p0X) * invDirX;
            tFar = (maxX - p0X) * invDirX;
        } else {
            tNear = (maxX - p0X) * invDirX;
            tFar = (minX - p0X) * invDirX;
        }
        if (invDirY >= 0.0f) {
            tymin = (minY - p0Y) * invDirY;
            tymax = (maxY - p0Y) * invDirY;
        } else {
            tymin = (maxY - p0Y) * invDirY;
            tymax = (minY - p0Y) * invDirY;
        }
        if (tNear > tymax || tymin > tFar)
            return OUTSIDE;
        tNear = tymin > tNear || Float.isNaN(tNear) ? tymin : tNear;
        tFar = tymax < tFar || Float.isNaN(tFar) ? tymax : tFar;
        int type = OUTSIDE;
        if (tNear <= tFar && tNear <= 1.0f && tFar >= 0.0f) {
            if (tNear >= 0.0f && tFar > 1.0f) {
                tFar = tNear;
                type = ONE_INTERSECTION;
            } else if (tNear < 0.0f && tFar <= 1.0f) {
                tNear = tFar;
                type = ONE_INTERSECTION;
            } else if (tNear < 0.0f && tFar > 1.0f) {
                type = INSIDE;
            } else {
                type = TWO_INTERSECTION;
            }
            result.x = tNear;
            result.y = tFar;
        }
        return type;
    }

    /**
     * Determine whether the undirected line segment with the end points <code>p0</code> and <code>p1</code>
     * intersects the axis-aligned rectangle given as its minimum corner <code>min</code> and maximum corner <code>max</code>,
     * and store the values of the parameter <i>t</i> in the ray equation <i>p(t) = p0 + t * (p1 - p0)</i> of the near and far point of intersection
     * into <code>result</code>.
     * <p>
     * This method also detects an intersection of a line segment whose either end point lies inside the axis-aligned rectangle.
     * <p>
     * Reference: <a href="https://dl.acm.org/citation.cfm?id=1198748">An Efficient and Robust Ray–Box Intersection</a>
     *
     * #see {@link #intersectLineSegmentAar(float, float, float, float, float, float, float, float, Vector2f)}
     * 
     * @param p0
     *              the line segment's first end point
     * @param p1
     *              the line segment's second end point
     * @param min
     *              the minimum corner of the axis-aligned rectangle
     * @param max
     *              the maximum corner of the axis-aligned rectangle
     * @param result
     *              a vector which will hold the values of the parameter <i>t</i> in the ray equation
     *              <i>p(t) = p0 + t * (p1 - p0)</i> of the near and far point of intersection
     * @return {@link #INSIDE} if the line segment lies completely inside of the axis-aligned rectangle; or
     *         {@link #OUTSIDE} if the line segment lies completely outside of the axis-aligned rectangle; or
     *         {@link #ONE_INTERSECTION} if one of the end points of the line segment lies inside of the axis-aligned rectangle; or
     *         {@link #TWO_INTERSECTION} if the line segment intersects two edges of the axis-aligned rectangle
     */
    public static int intersectLineSegmentAar(Vector2fc p0, Vector2fc p1, Vector2fc min, Vector2fc max, Vector2f result) {
        return intersectLineSegmentAar(p0.x(), p0.y(), p1.x(), p1.y(), min.x(), min.y(), max.x(), max.y(), result);
    }

    /**
     * Test whether the given ray with the origin <code>(originX, originY)</code> and direction <code>(dirX, dirY)</code>
     * intersects the given axis-aligned rectangle given as its minimum corner <code>(minX, minY)</code> and maximum corner <code>(maxX, maxY)</code>.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the axis-aligned rectangle.
     * <p>
     * Reference: <a href="https://dl.acm.org/citation.cfm?id=1198748">An Efficient and Robust Ray–Box Intersection</a>
     * 
     * @see #testRayAar(Vector2fc, Vector2fc, Vector2fc, Vector2fc)
     * 
     * @param originX
     *              the x coordinate of the ray's origin
     * @param originY
     *              the y coordinate of the ray's origin
     * @param dirX
     *              the x coordinate of the ray's direction
     * @param dirY
     *              the y coordinate of the ray's direction
     * @param minX
     *          the x coordinate of the minimum corner of the axis-aligned rectangle
     * @param minY
     *          the y coordinate of the minimum corner of the axis-aligned rectangle
     * @param maxX
     *          the x coordinate of the maximum corner of the axis-aligned rectangle
     * @param maxY
     *          the y coordinate of the maximum corner of the axis-aligned rectangle
     * @return <code>true</code> if the given ray intersects the axis-aligned rectangle; <code>false</code> otherwise
     */
    public static boolean testRayAar(float originX, float originY, float dirX, float dirY, float minX, float minY, float maxX, float maxY) {
        if (originX != originX || originY != originY || dirX != dirX || dirY != dirY) {
            return false; // NaN never hits
        }
        float invDirX = 1.0f / dirX, invDirY = 1.0f / dirY;
        float tNear, tFar, tymin, tymax;
        if (invDirX >= 0.0f) {
            tNear = (minX - originX) * invDirX;
            tFar = (maxX - originX) * invDirX;
        } else {
            tNear = (maxX - originX) * invDirX;
            tFar = (minX - originX) * invDirX;
        }
        if (invDirY >= 0.0f) {
            tymin = (minY - originY) * invDirY;
            tymax = (maxY - originY) * invDirY;
        } else {
            tymin = (maxY - originY) * invDirY;
            tymax = (minY - originY) * invDirY;
        }
        if (tNear > tymax || tymin > tFar)
            return false;
        tNear = tymin > tNear || Float.isNaN(tNear) ? tymin : tNear;
        tFar = tymax < tFar || Float.isNaN(tFar) ? tymax : tFar;
        return tNear <= tFar && tFar >= 0.0f;
    }

    /**
     * Test whether the ray with the given <code>origin</code> and direction <code>dir</code>
     * intersects the given axis-aligned rectangle specified as its minimum corner <code>min</code> and maximum corner <code>max</code>.
     * <p>
     * This method returns <code>true</code> for a ray whose origin lies inside the axis-aligned rectangle.
     * <p>
     * Reference: <a href="https://dl.acm.org/citation.cfm?id=1198748">An Efficient and Robust Ray–Box Intersection</a>
     * 
     * @see #testRayAar(float, float, float, float, float, float, float, float)
     * 
     * @param origin
     *              the ray's origin
     * @param dir
     *              the ray's direction
     * @param min
     *              the minimum corner of the axis-aligned rectangle
     * @param max
     *              the maximum corner of the axis-aligned rectangle
     * @return <code>true</code> if the given ray intersects the axis-aligned rectangle; <code>false</code> otherwise
     */
    public static boolean testRayAar(Vector2fc origin, Vector2fc dir, Vector2fc min, Vector2fc max) {
        return testRayAar(origin.x(), origin.y(), dir.x(), dir.y(), min.x(), min.y(), max.x(), max.y());
    }

    /**
     * Test whether the given point <code>(pX, pY)</code> lies inside the triangle with the vertices <code>(v0X, v0Y)</code>, <code>(v1X, v1Y)</code>, <code>(v2X, v2Y)</code>.
     * 
     * <p>
     * The triangle is closed and either winding works: points on its edges and vertices are inside. The orientation
     * tests are exact ({@link RobustPredicates#orient2d}).
     *
     * @param pX
     *          the x coordinate of the point
     * @param pY
     *          the y coordinate of the point
     * @param v0X
     *          the x coordinate of the first vertex of the triangle
     * @param v0Y
     *          the y coordinate of the first vertex of the triangle
     * @param v1X
     *          the x coordinate of the second vertex of the triangle
     * @param v1Y
     *          the y coordinate of the second vertex of the triangle
     * @param v2X
     *          the x coordinate of the third vertex of the triangle
     * @param v2Y
     *          the y coordinate of the third vertex of the triangle
     * @return <code>true</code> iff the point lies inside the triangle; <code>false</code> otherwise
     */
    public static boolean testPointTriangle(float pX, float pY, float v0X, float v0Y, float v1X, float v1Y, float v2X, float v2Y) {
        // exact orientation signs: a point on an edge or vertex is inside for either winding
        double d1 = RobustPredicates.orient2d(v0X, v0Y, v1X, v1Y, pX, pY);
        double d2 = RobustPredicates.orient2d(v1X, v1Y, v2X, v2Y, pX, pY);
        double d3 = RobustPredicates.orient2d(v2X, v2Y, v0X, v0Y, pX, pY);
        if (d1 == 0.0 && d2 == 0.0 && d3 == 0.0) {
            // a degenerate triangle with the point on its line: inside its extent
            return pX >= Math.min(v0X, Math.min(v1X, v2X)) && pX <= Math.max(v0X, Math.max(v1X, v2X))
                    && pY >= Math.min(v0Y, Math.min(v1Y, v2Y)) && pY <= Math.max(v0Y, Math.max(v1Y, v2Y));
        }
        boolean neg = d1 < 0.0 || d2 < 0.0 || d3 < 0.0;
        boolean pos = d1 > 0.0 || d2 > 0.0 || d3 > 0.0;
        return !(neg && pos);
    }

    /**
     * Test whether the given <code>point</code> lies inside the triangle with the vertices <code>v0</code>, <code>v1</code>, <code>v2</code>.
     * 
     * @param v0
     *          the first vertex of the triangle
     * @param v1
     *          the second vertex of the triangle
     * @param v2
     *          the third vertex of the triangle
     * @param point
     *          the point
     * @return <code>true</code> iff the point lies inside the triangle; <code>false</code> otherwise
     */
    public static boolean testPointTriangle(Vector2fc point, Vector2fc v0, Vector2fc v1, Vector2fc v2) {
        return testPointTriangle(point.x(), point.y(), v0.x(), v0.y(), v1.x(), v1.y(), v2.x(), v2.y());
    }

    /**
     * Test whether the given point <code>(pX, pY)</code> lies inside the axis-aligned rectangle with the minimum corner <code>(minX, minY)</code>
     * and maximum corner <code>(maxX, maxY)</code>.
     * 
     * @param pX
     *          the x coordinate of the point
     * @param pY
     *          the y coordinate of the point
     * @param minX
     *          the x coordinate of the minimum corner of the axis-aligned rectangle
     * @param minY
     *          the y coordinate of the minimum corner of the axis-aligned rectangle
     * @param maxX
     *          the x coordinate of the maximum corner of the axis-aligned rectangle
     * @param maxY
     *          the y coordinate of the maximum corner of the axis-aligned rectangle
     * @return <code>true</code> iff the point lies inside the axis-aligned rectangle; <code>false</code> otherwise
     */
    public static boolean testPointAar(float pX, float pY, float minX, float minY, float maxX, float maxY) {
        return pX >= minX && pY >= minY && pX <= maxX && pY <= maxY;
    }

    /**
     * Test whether the point <code>(pX, pY)</code> lies inside the circle with center <code>(centerX, centerY)</code> and square radius <code>radiusSquared</code>.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius. A point on the circle counts as inside.
     * 
     * @param pX
     *          the x coordinate of the point
     * @param pY
     *          the y coordinate of the point
     * @param centerX
     *          the x coordinate of the circle's center
     * @param centerY
     *          the y coordinate of the circle's center
     * @param radiusSquared
     *          the square of the circle's radius
     * @return <code>true</code> iff the point lies inside the circle; <code>false</code> otherwise
     */
    public static boolean testPointCircle(float pX, float pY, float centerX, float centerY, float radiusSquared) {
        float dx = pX - centerX;
        float dy = pY - centerY;
        float dx2 = dx * dx;
        float dy2 = dy * dy;
        return dx2 + dy2 <= radiusSquared;
    }

    /**
     * Test whether the circle with center <code>(centerX, centerY)</code> and square radius <code>radiusSquared</code> intersects the triangle with counter-clockwise vertices
     * <code>(v0X, v0Y)</code>, <code>(v1X, v1Y)</code>, <code>(v2X, v2Y)</code>.
     * <p>
     * The vertices of the triangle must be specified in counter-clockwise order.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius.
     * <p>
     * Reference: <a href="http://www.phatcode.net/articles.php?id=459">http://www.phatcode.net/</a>
     * 
     * @param centerX
     *          the x coordinate of the circle's center
     * @param centerY
     *          the y coordinate of the circle's center
     * @param radiusSquared
     *          the square of the circle's radius
     * @param v0X
     *          the x coordinate of the first vertex of the triangle
     * @param v0Y
     *          the y coordinate of the first vertex of the triangle
     * @param v1X
     *          the x coordinate of the second vertex of the triangle
     * @param v1Y
     *          the y coordinate of the second vertex of the triangle
     * @param v2X
     *          the x coordinate of the third vertex of the triangle
     * @param v2Y
     *          the y coordinate of the third vertex of the triangle
     * @return <code>true</code> iff the circle intersects the triangle; <code>false</code> otherwise
     */
    public static boolean testCircleTriangle(float centerX, float centerY, float radiusSquared, float v0X, float v0Y, float v1X, float v1Y, float v2X, float v2Y) {
        float c1x = centerX - v0X, c1y = centerY - v0Y;
        float c1sqr = c1x * c1x + c1y * c1y - radiusSquared;
        if (c1sqr <= 0.0f)
            return true;
        float c2x = centerX - v1X, c2y = centerY - v1Y;
        float c2sqr = c2x * c2x + c2y * c2y - radiusSquared;
        if (c2sqr <= 0.0f)
            return true;
        float c3x = centerX - v2X, c3y = centerY - v2Y;
        float c3sqr = c3x * c3x + c3y * c3y - radiusSquared;
        if (c3sqr <= 0.0f)
            return true;
        float e1x = v1X - v0X, e1y = v1Y - v0Y;
        float e2x = v2X - v1X, e2y = v2Y - v1Y;
        float e3x = v0X - v2X, e3y = v0Y - v2Y;
        if (e1x * c1y - e1y * c1x >= 0.0f && e2x * c2y - e2y * c2x >= 0.0f && e3x * c3y - e3y * c3x >= 0.0f)
            return true;
        float k = c1x * e1x + c1y * e1y;
        if (k >= 0.0f) {
            float len = e1x * e1x + e1y * e1y;
            if (k <= len) {
                if (c1sqr * len <= k * k)
                    return true;
            }
        }
        k = c2x * e2x + c2y * e2y;
        if (k > 0.0f) {
            float len = e2x * e2x + e2y * e2y;
            if (k <= len) {
                if (c2sqr * len <= k * k)
                    return true;
            }
        }
        k = c3x * e3x + c3y * e3y;
        if (k >= 0.0f) {
            float len = e3x * e3x + e3y * e3y;
            if (k < len) {
                if (c3sqr * len <= k * k)
                    return true;
            }
        }
        return false;
    }

    /**
     * Test whether the circle with given <code>center</code> and square radius <code>radiusSquared</code> intersects the triangle with counter-clockwise vertices
     * <code>v0</code>, <code>v1</code>, <code>v2</code>.
     * <p>
     * The vertices of the triangle must be specified in counter-clockwise order.
     * <p>
     * <code>radiusSquared</code> is the square of the radius, not the radius.
     * <p>
     * Reference: <a href="http://www.phatcode.net/articles.php?id=459">http://www.phatcode.net/</a>
     * 
     * @param center
     *          the circle's center
     * @param radiusSquared
     *          the square of the circle's radius
     * @param v0
     *          the first vertex of the triangle
     * @param v1
     *          the second vertex of the triangle
     * @param v2
     *          the third vertex of the triangle
     * @return <code>true</code> iff the circle intersects the triangle; <code>false</code> otherwise
     */
    public static boolean testCircleTriangle(Vector2fc center, float radiusSquared, Vector2fc v0, Vector2fc v1, Vector2fc v2) {
        return testCircleTriangle(center.x(), center.y(), radiusSquared, v0.x(), v0.y(), v1.x(), v1.y(), v2.x(), v2.y());
    }

    /**
     * Determine whether the polygon specified by the given sequence of <code>(x, y)</code> coordinate pairs intersects with the ray
     * with given origin <code>(originX, originY, originZ)</code> and direction <code>(dirX, dirY, dirZ)</code>, and store the point of intersection
     * into the given vector <code>p</code>.
     * <p>
     * If the polygon intersects the ray, this method returns the index of the polygon edge intersecting the ray, that is, the index of the 
     * first vertex of the directed line segment. The second vertex is always that index + 1, modulus the number of polygon vertices.
     * 
     * @param verticesXY
     *          the sequence of <code>(x, y)</code> coordinate pairs of all vertices of the polygon
     * @param originX
     *          the x coordinate of the ray's origin
     * @param originY
     *          the y coordinate of the ray's origin
     * @param dirX
     *          the x coordinate of the ray's direction
     * @param dirY
     *          the y coordinate of the ray's direction
     * @param p
     *          will hold the point of intersection
     * @return the index of the first vertex of the polygon edge that intersects the ray; or <code>-1</code> if the ray does not intersect the polygon
     */
    public static int intersectPolygonRay(float[] verticesXY, float originX, float originY, float dirX, float dirY, Vector2f p) {
        double nearestT = Double.POSITIVE_INFINITY;
        int count = verticesXY.length >> 1;
        int edgeIndex = -1;
        double aX = verticesXY[(count-1)<<1], aY = verticesXY[((count-1)<<1) + 1];
        for (int i = 0; i < count; i++) {
            double bX = verticesXY[i << 1], bY = verticesXY[(i << 1) + 1];
            double doaX = originX - aX, doaY = originY - aY;
            double dbaX = bX - aX, dbaY = bY - aY;
            double invDbaDir = 1.0 / (dbaY * dirX - dbaX * dirY);
            double t = (dbaX * doaY - dbaY * doaX) * invDbaDir;
            if (t >= 0.0 && t < nearestT) {
                double t2 = (doaY * dirX - doaX * dirY) * invDbaDir;
                if (t2 >= 0.0 && t2 <= 1.0) {
                    edgeIndex = (i - 1 + count) % count;
                    nearestT = t;
                    p.x = (float) (originX + t * dirX);
                    p.y = (float) (originY + t * dirY);
                }
            }
            aX = bX;
            aY = bY;
        }
        return edgeIndex;
    }

    /**
     * Determine whether the polygon specified by the given sequence of <code>vertices</code> intersects with the ray
     * with given origin <code>(originX, originY, originZ)</code> and direction <code>(dirX, dirY, dirZ)</code>, and store the point of intersection
     * into the given vector <code>p</code>.
     * <p>
     * If the polygon intersects the ray, this method returns the index of the polygon edge intersecting the ray, that is, the index of the 
     * first vertex of the directed line segment. The second vertex is always that index + 1, modulus the number of polygon vertices.
     * 
     * @param vertices
     *          the sequence of <code>(x, y)</code> coordinate pairs of all vertices of the polygon
     * @param originX
     *          the x coordinate of the ray's origin
     * @param originY
     *          the y coordinate of the ray's origin
     * @param dirX
     *          the x coordinate of the ray's direction
     * @param dirY
     *          the y coordinate of the ray's direction
     * @param p
     *          will hold the point of intersection
     * @return the index of the first vertex of the polygon edge that intersects the ray; or <code>-1</code> if the ray does not intersect the polygon
     */
    public static int intersectPolygonRay(Vector2fc[] vertices, float originX, float originY, float dirX, float dirY, Vector2f p) {
        float nearestT = Float.POSITIVE_INFINITY;
        int count = vertices.length;
        int edgeIndex = -1;
        float aX = vertices[count-1].x(), aY = vertices[count-1].y();
        for (int i = 0; i < count; i++) {
            Vector2fc b = vertices[i];
            float bX = b.x(), bY = b.y();
            float doaX = originX - aX, doaY = originY - aY;
            float dbaX = bX - aX, dbaY = bY - aY;
            float invDbaDir = 1.0f / (dbaY * dirX - dbaX * dirY);
            float t = (dbaX * doaY - dbaY * doaX) * invDbaDir;
            if (t >= 0.0f && t < nearestT) {
                float t2 = (doaY * dirX - doaX * dirY) * invDbaDir;
                if (t2 >= 0.0f && t2 <= 1.0f) {
                    edgeIndex = (i - 1 + count) % count;
                    nearestT = t;
                    p.x = originX + t * dirX;
                    p.y = originY + t * dirY;
                }
            }
            aX = bX;
            aY = bY;
        }
        return edgeIndex;
    }

    /**
     * Determine whether the two lines, specified via two points lying on each line, intersect each other, and store the point of intersection
     * into the given vector <code>p</code>.
     * 
     * @param ps1x
     *          the x coordinate of the first point on the first line
     * @param ps1y
     *          the y coordinate of the first point on the first line
     * @param pe1x
     *          the x coordinate of the second point on the first line
     * @param pe1y
     *          the y coordinate of the second point on the first line
     * @param ps2x
     *          the x coordinate of the first point on the second line
     * @param ps2y
     *          the y coordinate of the first point on the second line
     * @param pe2x
     *          the x coordinate of the second point on the second line
     * @param pe2y
     *          the y coordinate of the second point on the second line
     * @param p
     *          will hold the point of intersection
     * @return <code>true</code> iff the two lines intersect; <code>false</code> otherwise
     */
    public static boolean intersectLineLine(float ps1x, float ps1y, float pe1x, float pe1y, float ps2x, float ps2y, float pe2x, float pe2y, Vector2f p) {
        double d1x = ps1x - pe1x;
        double d1y = pe1y - ps1y;
        double d1ps1 = d1y * ps1x + d1x * ps1y;
        double d2x = ps2x - pe2x;
        double d2y = pe2y - ps2y;
        double d2ps2 = d2y * ps2x + d2x * ps2y;
        double det = d1y * d2x - d2y * d1x;
        if (det == 0.0)
            return false;
        p.x = (float) ((d2x * d1ps1 - d1x * d2ps2) / det);
        p.y = (float) ((d1y * d2ps2 - d2y * d1ps1) / det);
        return true;
    }

    private static boolean separatingAxis(Vector2f[] v1s, Vector2f[] v2s, float aX, float aY) {
        float minA = Float.POSITIVE_INFINITY, maxA = Float.NEGATIVE_INFINITY;
        float minB = Float.POSITIVE_INFINITY, maxB = Float.NEGATIVE_INFINITY;
        int maxLen = Math.max(v1s.length, v2s.length);
        /* Project both polygons on axis */
        for (int k = 0; k < maxLen; k++) {
            if (k < v1s.length) {
                Vector2f v1 = v1s[k];
                float d = v1.x * aX + v1.y * aY;
                if (d < minA) minA = d;
                if (d > maxA) maxA = d;
            }
            if (k < v2s.length) {
                Vector2f v2 = v2s[k];
                float d = v2.x * aX + v2.y * aY;
                if (d < minB) minB = d;
                if (d > maxB) maxB = d;
            }
            /* Early-out if overlap found */
            if (minA <= maxB && minB <= maxA) {
                return false;
            }
        }
        return true;
    }

    /**
     * Test if the two convex polygons, given via their vertices, intersect.
     * 
     * @param v1s
     *          the vertices of the first convex polygon
     * @param v2s
     *          the vertices of the second convex polygon
     * @return <code>true</code> if the convex polygons intersect; <code>false</code> otherwise
     */
    public static boolean testPolygonPolygon(Vector2f[] v1s, Vector2f[] v2s) {
        /* Try to find a separating axis using the first polygon's edges */
        for (int i = 0, j = v1s.length - 1; i < v1s.length; j = i, i++) {
            Vector2f s = v1s[i], t = v1s[j];
            if (separatingAxis(v1s, v2s, s.y - t.y, t.x - s.x))
                return false;
        }
        /* Try to find a separating axis using the second polygon's edges */
        for (int i = 0, j = v2s.length - 1; i < v2s.length; j = i, i++) {
            Vector2f s = v2s[i], t = v2s[j];
            if (separatingAxis(v1s, v2s, s.y - t.y, t.x - s.x))
                return false;
        }
        return true;
    }

}
