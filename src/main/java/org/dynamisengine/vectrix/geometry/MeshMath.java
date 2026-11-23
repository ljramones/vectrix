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
package org.dynamisengine.vectrix.geometry;

import org.dynamisengine.vectrix.core.Epsilonf;
import org.dynamisengine.vectrix.core.Vector2fc;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Mesh-oriented geometric helpers used by render and mesh pipelines: barycentric coordinates, closest points on
 * triangles, triangle area, winding and quality measures, and per-vertex normals and tangents of indexed triangle
 * lists.
 * <p>
 * The class is stateless and safe to call from any thread. The per-triangle methods do not allocate, except
 * {@link #triangleMinAngle}; the per-vertex mesh methods allocate small temporaries as stated on each method.
 */
public final class MeshMath {
    /** Triangles with sin^2 of the angle at a below this are degenerate for {@link #barycentric}. */
    private static final float DEGENERATE_SIN2 = 1e-6f;
    /** Relative signed-area tolerance for {@link #winding2D}. */
    private static final float DEGENERATE_SIN = 1e-6f;

    private MeshMath() {
    }

    /**
     * Computes the barycentric coordinates {@code (u, v, w)} of point {@code p} with respect to triangle
     * {@code abc}, so that {@code u a + v b + w c} is the projection of {@code p} onto the triangle's plane. The
     * coordinates sum to 1 and are not clamped, so they can be negative when {@code p} lies outside the triangle.
     * <p>
     * A degenerate triangle (the squared sine of the angle at {@code a} below {@code 1e-6}; the test is relative, so it
     * does not depend on the triangle's size) gives {@code (NaN, NaN, NaN)}. {@code dest} may be any of the inputs.
     *
     * @param p the point
     * @param a the first vertex
     * @param b the second vertex
     * @param c the third vertex
     * @param dest receives the weights of {@code a}, {@code b} and {@code c} in x, y and z
     * @return {@code dest}
     */
    public static Vector3f barycentric(Vector3fc p, Vector3fc a, Vector3fc b, Vector3fc c, Vector3f dest) {
        float v0x = b.x() - a.x();
        float v0y = b.y() - a.y();
        float v0z = b.z() - a.z();
        float v1x = c.x() - a.x();
        float v1y = c.y() - a.y();
        float v1z = c.z() - a.z();
        float v2x = p.x() - a.x();
        float v2y = p.y() - a.y();
        float v2z = p.z() - a.z();

        float d00 = dot(v0x, v0y, v0z, v0x, v0y, v0z);
        float d01 = dot(v0x, v0y, v0z, v1x, v1y, v1z);
        float d11 = dot(v1x, v1y, v1z, v1x, v1y, v1z);
        float d20 = dot(v2x, v2y, v2z, v0x, v0y, v0z);
        float d21 = dot(v2x, v2y, v2z, v1x, v1y, v1z);

        float denom = d00 * d11 - d01 * d01;
        // denom = |e0 x e1|^2 = d00 * d11 * sin^2(angle) scales with length^4, so the degeneracy test is relative:
        // an absolute epsilon rejected every triangle smaller than about 3 cm (a 1 mm triangle gave NaN).
        if (!(denom > DEGENERATE_SIN2 * d00 * d11)) {
            return dest.set(Float.NaN, Float.NaN, Float.NaN);
        }

        float invDenom = 1.0f / denom;
        float v = (d11 * d20 - d01 * d21) * invDenom;
        float w = (d00 * d21 - d01 * d20) * invDenom;
        float u = 1.0f - v - w;
        return dest.set(u, v, w);
    }

    /**
     * Returns whether a barycentric coordinate triple lies inside or on the triangle: every component is at least
     * {@code -Epsilonf.ABSOLUTE}. The sum of the components is not checked; NaN components give {@code false}.
     *
     * @param barycentric the barycentric coordinates, for example from
     *        {@link #barycentric(Vector3fc, Vector3fc, Vector3fc, Vector3fc, Vector3f)}
     * @return {@code true} if the point is inside or on the boundary of the triangle
     */
    public static boolean isInsideBarycentric(Vector3fc barycentric) {
        float e = Epsilonf.ABSOLUTE;
        return barycentric.x() >= -e && barycentric.y() >= -e && barycentric.z() >= -e;
    }

    /**
     * Computes the point of triangle {@code abc} (interior, edges or vertices) closest to point {@code p}, by
     * Voronoi region classification. {@code dest} may be any of the inputs.
     *
     * @param p the point
     * @param a the first vertex
     * @param b the second vertex
     * @param c the third vertex
     * @param dest receives the closest point
     * @return {@code dest}
     */
    public static Vector3f closestPointOnTriangle(Vector3fc p, Vector3fc a, Vector3fc b, Vector3fc c, Vector3f dest) {
        float abx = b.x() - a.x();
        float aby = b.y() - a.y();
        float abz = b.z() - a.z();
        float acx = c.x() - a.x();
        float acy = c.y() - a.y();
        float acz = c.z() - a.z();
        float apx = p.x() - a.x();
        float apy = p.y() - a.y();
        float apz = p.z() - a.z();

        float d1 = dot(abx, aby, abz, apx, apy, apz);
        float d2 = dot(acx, acy, acz, apx, apy, apz);
        if (d1 <= 0.0f && d2 <= 0.0f) {
            return dest.set(a);
        }

        float bpx = p.x() - b.x();
        float bpy = p.y() - b.y();
        float bpz = p.z() - b.z();
        float d3 = dot(abx, aby, abz, bpx, bpy, bpz);
        float d4 = dot(acx, acy, acz, bpx, bpy, bpz);
        if (d3 >= 0.0f && d4 <= d3) {
            return dest.set(b);
        }

        float vc = d1 * d4 - d3 * d2;
        if (vc <= 0.0f && d1 >= 0.0f && d3 <= 0.0f) {
            float v = d1 / (d1 - d3);
            return dest.set(a.x() + abx * v, a.y() + aby * v, a.z() + abz * v);
        }

        float cpx = p.x() - c.x();
        float cpy = p.y() - c.y();
        float cpz = p.z() - c.z();
        float d5 = dot(abx, aby, abz, cpx, cpy, cpz);
        float d6 = dot(acx, acy, acz, cpx, cpy, cpz);
        if (d6 >= 0.0f && d5 <= d6) {
            return dest.set(c);
        }

        float vb = d5 * d2 - d1 * d6;
        if (vb <= 0.0f && d2 >= 0.0f && d6 <= 0.0f) {
            float w = d2 / (d2 - d6);
            return dest.set(a.x() + acx * w, a.y() + acy * w, a.z() + acz * w);
        }

        float va = d3 * d6 - d5 * d4;
        if (va <= 0.0f && (d4 - d3) >= 0.0f && (d5 - d6) >= 0.0f) {
            float w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
            return dest.set(b.x() + (c.x() - b.x()) * w, b.y() + (c.y() - b.y()) * w, b.z() + (c.z() - b.z()) * w);
        }

        float denom = 1.0f / (va + vb + vc);
        float v = vb * denom;
        float w = vc * denom;
        return dest.set(a.x() + abx * v + acx * w, a.y() + aby * v + acy * w, a.z() + abz * v + acz * w);
    }

    /**
     * Returns twice the signed area of a 2D triangle: {@code (b - a) × (c - a)}, positive when {@code a, b, c} are
     * counter-clockwise. For an exact sign on near-degenerate input use {@link RobustPredicates#orient2d}.
     *
     * @param a the first vertex
     * @param b the second vertex
     * @param c the third vertex
     * @return twice the signed area
     */
    public static float signedArea2(Vector2fc a, Vector2fc b, Vector2fc c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    /**
     * Classifies the winding of a 2D triangle. The triangle counts as degenerate when the absolute value of
     * {@link #signedArea2} is at most {@code 1e-6} times {@code |b - a|² + |c - a|²}, so the result does not depend on
     * the triangle's scale.
     *
     * @param a the first vertex
     * @param b the second vertex
     * @param c the third vertex
     * @return {@code 1} for counter-clockwise, {@code -1} for clockwise, {@code 0} for degenerate (or NaN input)
     */
    public static int winding2D(Vector2fc a, Vector2fc b, Vector2fc c) {
        float s = signedArea2(a, b, c);
        // Relative to the edge lengths, so the classification does not depend on the triangle's scale.
        float abx = b.x() - a.x(), aby = b.y() - a.y(), acx = c.x() - a.x(), acy = c.y() - a.y();
        float tol = DEGENERATE_SIN * (abx * abx + aby * aby + acx * acx + acy * acy);
        if (s > tol) {
            return 1;
        }
        if (s < -tol) {
            return -1;
        }
        return 0;
    }

    /**
     * Returns the (unsigned) area of a 3D triangle: half the length of {@code (b - a) × (c - a)}.
     *
     * @param a the first vertex
     * @param b the second vertex
     * @param c the third vertex
     * @return the area, 0 for a degenerate triangle
     */
    public static float triangleArea(Vector3fc a, Vector3fc b, Vector3fc c) {
        float abx = b.x() - a.x();
        float aby = b.y() - a.y();
        float abz = b.z() - a.z();
        float acx = c.x() - a.x();
        float acy = c.y() - a.y();
        float acz = c.z() - a.z();
        float cx = aby * acz - abz * acy;
        float cy = abz * acx - abx * acz;
        float cz = abx * acy - aby * acx;
        return 0.5f * (float) java.lang.Math.sqrt(dot(cx, cy, cz, cx, cy, cz));
    }

    private static float dot(float ax, float ay, float az, float bx, float by, float bz) {
        return ax * bx + ay * by + az * bz;
    }

    // ---- per-vertex accumulation over indexed triangle lists (positions/normals xyz, uvs st, indices 3 per triangle)

    /**
     * Vertex normals of an indexed triangle mesh into {@code normalsOut} (3 floats per vertex): the sum of the
     * adjacent face normals weighted by face area ({@code angleWeighted == false}) or by the corner angle at the
     * vertex ({@code true}, which does not depend on how faces are triangulated), normalised. Vertices with no
     * non-degenerate face, or whose weighted face normals cancel, get the zero vector.
     * <p>
     * The vertex count is {@code positions.length / 3}; the first {@code 3 * vertexCount} elements of
     * {@code normalsOut} are overwritten. Arrays start at index 0 and are not range-checked up front: a bad index
     * throws {@link ArrayIndexOutOfBoundsException} after part of the output has been written. The method allocates
     * a three-element {@code int} array per triangle.
     *
     * @param positions vertex positions, {@code x, y, z} per vertex
     * @param indices vertex indices, 3 per triangle; face normals follow the right-hand rule on the vertex order
     * @param triangleCount the number of triangles to read from {@code indices}
     * @param angleWeighted {@code true} to weight by corner angle, {@code false} to weight by face area
     * @param normalsOut receives the unit normals, {@code x, y, z} per vertex; at least {@code positions.length}
     *        elements (rounded down to a multiple of 3)
     * @since 2.2.0
     */
    public static void vertexNormals(float[] positions, int[] indices, int triangleCount, boolean angleWeighted,
            float[] normalsOut) {
        java.util.Arrays.fill(normalsOut, 0, positions.length / 3 * 3, 0.0f);
        for (int t = 0; t < triangleCount; t++) {
            int[] v = {indices[3 * t], indices[3 * t + 1], indices[3 * t + 2]};
            double ex1 = positions[3 * v[1]] - positions[3 * v[0]], ey1 = positions[3 * v[1] + 1] - positions[3 * v[0] + 1];
            double ez1 = positions[3 * v[1] + 2] - positions[3 * v[0] + 2];
            double ex2 = positions[3 * v[2]] - positions[3 * v[0]], ey2 = positions[3 * v[2] + 1] - positions[3 * v[0] + 1];
            double ez2 = positions[3 * v[2] + 2] - positions[3 * v[0] + 2];
            // cross product: twice the area, along the face normal
            double nx = ey1 * ez2 - ez1 * ey2, ny = ez1 * ex2 - ex1 * ez2, nz = ex1 * ey2 - ey1 * ex2;
            double len = java.lang.Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len == 0.0) {
                continue;
            }
            for (int c = 0; c < 3; c++) {
                double w = angleWeighted ? cornerAngle(positions, v[c], v[(c + 1) % 3], v[(c + 2) % 3]) / len : 0.5;
                normalsOut[3 * v[c]] += (float) (nx * w);
                normalsOut[3 * v[c] + 1] += (float) (ny * w);
                normalsOut[3 * v[c] + 2] += (float) (nz * w);
            }
        }
        for (int i = 0; i < positions.length / 3; i++) {
            float x = normalsOut[3 * i], y = normalsOut[3 * i + 1], z = normalsOut[3 * i + 2];
            float l = (float) java.lang.Math.sqrt(x * x + y * y + z * z);
            if (l > 0.0f) {
                normalsOut[3 * i] = x / l;
                normalsOut[3 * i + 1] = y / l;
                normalsOut[3 * i + 2] = z / l;
            }
        }
    }

    /** Interior angle at vertex {@code a} of triangle {@code (a, b, c)}, in radians. */
    private static double cornerAngle(float[] p, int a, int b, int c) {
        double ux = p[3 * b] - p[3 * a], uy = p[3 * b + 1] - p[3 * a + 1], uz = p[3 * b + 2] - p[3 * a + 2];
        double vx = p[3 * c] - p[3 * a], vy = p[3 * c + 1] - p[3 * a + 1], vz = p[3 * c + 2] - p[3 * a + 2];
        double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        return org.dynamisengine.vectrix.core.AccurateMath.atan2(java.lang.Math.sqrt(cx * cx + cy * cy + cz * cz),
                ux * vx + uy * vy + uz * vz);
    }

    /**
     * Per-vertex tangents in the style of MikkTSpace (Mikkelsen 2008) into {@code tangentsOut} (4 floats per vertex:
     * unit tangent and handedness {@code w = ±1}, with the bitangent {@code w (n × t)}). Each corner's UV-derived face
     * tangent and bitangent are projected onto the plane of that vertex's normal, normalised, and weighted by the
     * corner angle, as in MikkTSpace. Unlike MikkTSpace it does not split vertices whose corners disagree (mirrored UV
     * seams), which is mesh topology and MeshForge's job: on meshes already split at UV seams the tangent directions
     * agree. Vertices without a usable corner get {@code (1, 0, 0)} (or {@code (0, 1, 0)} when the normal is along
     * x) projected onto the plane perpendicular to their normal, with {@code w = 1}.
     * <p>
     * The vertex count is {@code positions.length / 3}; arrays start at index 0 and are not range-checked up front.
     * Triangles whose UV mapping is degenerate are skipped. The method allocates two {@code double} arrays of
     * {@code 3 * vertexCount} elements, plus a three-element {@code int} array per triangle.
     *
     * @param positions vertex positions, {@code x, y, z} per vertex
     * @param normals unit vertex normals, {@code x, y, z} per vertex
     * @param uvs texture coordinates, {@code s, t} per vertex
     * @param indices vertex indices, 3 per triangle
     * @param triangleCount the number of triangles to read from {@code indices}
     * @param tangentsOut receives the tangents, {@code x, y, z, w} per vertex; at least {@code 4 * vertexCount}
     *        elements
     * @since 2.2.0
     */
    public static void vertexTangents(float[] positions, float[] normals, float[] uvs, int[] indices,
            int triangleCount, float[] tangentsOut) {
        int vc = positions.length / 3;
        double[] ts = new double[3 * vc], bs = new double[3 * vc];
        for (int t = 0; t < triangleCount; t++) {
            int[] v = {indices[3 * t], indices[3 * t + 1], indices[3 * t + 2]};
            double e1x = positions[3 * v[1]] - positions[3 * v[0]], e1y = positions[3 * v[1] + 1] - positions[3 * v[0] + 1];
            double e1z = positions[3 * v[1] + 2] - positions[3 * v[0] + 2];
            double e2x = positions[3 * v[2]] - positions[3 * v[0]], e2y = positions[3 * v[2] + 1] - positions[3 * v[0] + 1];
            double e2z = positions[3 * v[2] + 2] - positions[3 * v[0] + 2];
            double s1 = uvs[2 * v[1]] - uvs[2 * v[0]], t1 = uvs[2 * v[1] + 1] - uvs[2 * v[0] + 1];
            double s2 = uvs[2 * v[2]] - uvs[2 * v[0]], t2 = uvs[2 * v[2] + 1] - uvs[2 * v[0] + 1];
            double det = s1 * t2 - s2 * t1;
            if (det == 0.0) {
                continue;
            }
            double r = 1.0 / det;
            double fx = (t2 * e1x - t1 * e2x) * r, fy = (t2 * e1y - t1 * e2y) * r, fz = (t2 * e1z - t1 * e2z) * r;
            double gx = (s1 * e2x - s2 * e1x) * r, gy = (s1 * e2y - s2 * e1y) * r, gz = (s1 * e2z - s2 * e1z) * r;
            for (int c = 0; c < 3; c++) {
                int i = v[c];
                double nx = normals[3 * i], ny = normals[3 * i + 1], nz = normals[3 * i + 2];
                double w = cornerAngle(positions, i, v[(c + 1) % 3], v[(c + 2) % 3]);
                accumulateProjected(ts, i, fx, fy, fz, nx, ny, nz, w);
                accumulateProjected(bs, i, gx, gy, gz, nx, ny, nz, w);
            }
        }
        for (int i = 0; i < vc; i++) {
            double nx = normals[3 * i], ny = normals[3 * i + 1], nz = normals[3 * i + 2];
            double tx = ts[3 * i], ty = ts[3 * i + 1], tz = ts[3 * i + 2];
            double d = tx * nx + ty * ny + tz * nz;
            tx -= d * nx;
            ty -= d * ny;
            tz -= d * nz;
            double l = java.lang.Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (l == 0.0) {
                // no usable corner: any tangent perpendicular to the normal
                tx = 1.0 - nx * nx;
                ty = -nx * ny;
                tz = -nx * nz;
                l = java.lang.Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (l == 0.0) {
                    tx = -ny * nx;
                    ty = 1.0 - ny * ny;
                    tz = -ny * nz;
                    l = java.lang.Math.sqrt(tx * tx + ty * ty + tz * tz);
                }
            }
            tx /= l;
            ty /= l;
            tz /= l;
            // handedness: does the accumulated bitangent point along n × t?
            double cx = ny * tz - nz * ty, cy = nz * tx - nx * tz, cz = nx * ty - ny * tx;
            double hand = cx * bs[3 * i] + cy * bs[3 * i + 1] + cz * bs[3 * i + 2] < 0.0 ? -1.0 : 1.0;
            tangentsOut[4 * i] = (float) tx;
            tangentsOut[4 * i + 1] = (float) ty;
            tangentsOut[4 * i + 2] = (float) tz;
            tangentsOut[4 * i + 3] = (float) hand;
        }
    }

    private static void accumulateProjected(double[] acc, int i, double x, double y, double z, double nx, double ny,
            double nz, double weight) {
        double d = x * nx + y * ny + z * nz;
        x -= d * nx;
        y -= d * ny;
        z -= d * nz;
        double l = java.lang.Math.sqrt(x * x + y * y + z * z);
        if (l > 0.0) {
            acc[3 * i] += weight * x / l;
            acc[3 * i + 1] += weight * y / l;
            acc[3 * i + 2] += weight * z / l;
        }
    }

    /**
     * Triangle aspect ratio {@code L_max · perimeter / (4√3 · area)}: 1 for an equilateral triangle, growing without
     * bound as the triangle degenerates (infinite for zero area). {@code L_max} is the longest edge.
     *
     * @param a the first vertex
     * @param b the second vertex
     * @param c the third vertex
     * @return the aspect ratio, at least 1, or {@link Float#POSITIVE_INFINITY} for zero area
     * @since 2.2.0
     */
    public static float triangleAspectRatio(Vector3fc a, Vector3fc b, Vector3fc c) {
        float ab = a.distance(b), bc = b.distance(c), ca = c.distance(a);
        float area = triangleArea(a, b, c);
        if (area == 0.0f) {
            return Float.POSITIVE_INFINITY;
        }
        return java.lang.Math.max(ab, java.lang.Math.max(bc, ca)) * (ab + bc + ca) / (4.0f * (float) java.lang.Math.sqrt(3.0) * area);
    }

    /**
     * Triangle radius ratio {@code 2 r_in / R_circ} (inradius over circumradius, doubled): 1 for an equilateral
     * triangle, 0 for a degenerate one.
     *
     * @param a the first vertex
     * @param b the second vertex
     * @param c the third vertex
     * @return the radius ratio in {@code [0, 1]}
     * @since 2.2.0
     */
    public static float triangleRadiusRatio(Vector3fc a, Vector3fc b, Vector3fc c) {
        float ab = a.distance(b), bc = b.distance(c), ca = c.distance(a);
        float area = triangleArea(a, b, c);
        float perimeter = ab + bc + ca;
        if (area == 0.0f || perimeter == 0.0f) {
            return 0.0f;
        }
        float rin = 2.0f * area / perimeter;
        float rcirc = ab * bc * ca / (4.0f * area);
        return 2.0f * rin / rcirc;
    }

    /**
     * Smallest interior angle of the triangle, in radians ({@code π/3} for an equilateral triangle, 0 if degenerate).
     * Allocates a nine-element {@code float} array per call.
     *
     * @param a the first vertex
     * @param b the second vertex
     * @param c the third vertex
     * @return the smallest interior angle in radians, in {@code [0, π/3]}
     * @since 2.2.0
     */
    public static float triangleMinAngle(Vector3fc a, Vector3fc b, Vector3fc c) {
        float[] p = {a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), c.x(), c.y(), c.z()};
        double m = java.lang.Math.min(cornerAngle(p, 0, 1, 2), java.lang.Math.min(cornerAngle(p, 1, 2, 0), cornerAngle(p, 2, 0, 1)));
        return (float) m;
    }
}
