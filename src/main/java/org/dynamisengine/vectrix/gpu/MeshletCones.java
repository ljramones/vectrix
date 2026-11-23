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
package org.dynamisengine.vectrix.gpu;

import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.geometry.BoundingVolumesf;

/**
 * Normal cones for culling back-facing clusters (meshlets): build the cone, test it, and quantize it.
 * <p>
 * A cone is an axis {@code a} and the cosine of its half angle {@code α}. Every triangle normal of the cluster lies
 * within {@code α} of {@code a}. The cluster is back-facing from a camera when every triangle faces away from it.
 *
 * @since 2.4.0
 */
public final class MeshletCones {
    private MeshletCones() {
    }

    /**
     * The tightest normal cone of {@code triangleCount} triangles starting at {@code firstTriangle}. Each triangle
     * has three entries in {@code indices}, which index {@code xyz}, a packed position array.
     * <p>
     * The axis is the direction of the minimal enclosing ball of the unit normals: for cones narrower than a
     * hemisphere, that is the axis of the minimal enclosing cap. The cosine is the exact minimum of
     * {@code axis · n} over the normals, so the cone always contains every normal. Degenerate (zero-area) triangles
     * are skipped. A cluster whose normals are not within an open hemisphere, or that has no valid triangle, gets
     * {@code cos α = −1}, which is never culled.
     * <p>
     * This is a build-time function; it allocates. Indices are not validated: an index out of range throws
     * {@link ArrayIndexOutOfBoundsException}.
     *
     * @param xyz vertex positions, {@code x, y, z} per vertex; vertex {@code v} starts at {@code 3 * v}
     * @param indices triangle vertex indices, three per triangle; triangle {@code t} starts at {@code 3 * t}
     * @param firstTriangle the index of the first triangle of the cluster (in triangles, not in indices)
     * @param triangleCount the number of triangles in the cluster
     * @param dest
     *            receives the axis in {@code xyz} and {@code cos α} in {@code w}
     * @return {@code cos α}
     */
    public static float normalCone(float[] xyz, int[] indices, int firstTriangle, int triangleCount, Vector4f dest) {
        float[] normals = new float[3 * triangleCount];
        int n = 0;
        for (int t = firstTriangle; t < firstTriangle + triangleCount; t++) {
            int a = 3 * indices[3 * t], b = 3 * indices[3 * t + 1], c = 3 * indices[3 * t + 2];
            double e1x = (double) xyz[b] - xyz[a], e1y = (double) xyz[b + 1] - xyz[a + 1], e1z = (double) xyz[b + 2] - xyz[a + 2];
            double e2x = (double) xyz[c] - xyz[a], e2y = (double) xyz[c + 1] - xyz[a + 1], e2z = (double) xyz[c + 2] - xyz[a + 2];
            double nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (!(len > 0)) {
                continue;
            }
            normals[3 * n] = (float) (nx / len);
            normals[3 * n + 1] = (float) (ny / len);
            normals[3 * n + 2] = (float) (nz / len);
            n++;
        }
        if (n == 0) {
            dest.set(0, 0, 1, -1);
            return -1;
        }
        Vector4f ball = BoundingVolumesf.minimalSphere(normals, n, new Vector4f());
        double cx = ball.x, cy = ball.y, cz = ball.z, cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (!(cl > 1e-6)) {
            dest.set(0, 0, 1, -1);
            return -1;
        }
        // the cosine is measured against the float axis actually stored, then lowered by a 1e-6 margin for the
        // rounding of the test itself, so the stored cone always contains every normal
        float fx = (float) (cx / cl), fy = (float) (cy / cl), fz = (float) (cz / cl);
        double minDot = 1;
        for (int i = 0; i < n; i++) {
            minDot = Math.min(minDot, (double) fx * normals[3 * i] + (double) fy * normals[3 * i + 1]
                    + (double) fz * normals[3 * i + 2]);
        }
        float cosA = minDot > 1e-6 ? Math.nextDown((float) (minDot - 1e-6)) : -1; // a hemisphere or wider: never culled
        dest.set(fx, fy, fz, cosA);
        return cosA;
    }

    /**
     * Whether a cluster with bounding sphere {@code (c, radius)} and normal cone {@code (axis, cosA)} is entirely
     * back-facing as seen from {@code camera}. That holds when, for every point {@code p} in the sphere and every
     * normal {@code n} in the cone, {@code n · (p − camera) > 0}.
     * <p>
     * With {@code d = c − camera}, the test is exact: {@code (d·a) cos α − |d × a| sin α > radius}. It never culls
     * a cluster with a visible triangle. Cones of {@code α ≥ 90°} are never back-facing.
     *
     * @param cx the bounding sphere center, x
     * @param cy the bounding sphere center, y
     * @param cz the bounding sphere center, z
     * @param radius the bounding sphere radius
     * @param ax
     *            the unit cone axis, x (with {@code ay}, {@code az})
     * @param ay the unit cone axis, y
     * @param az the unit cone axis, z
     * @param cosA the cosine of the cone's half angle; a value of 0 or less (or NaN) is never back-facing
     * @param cameraX the camera position, x
     * @param cameraY the camera position, y
     * @param cameraZ the camera position, z
     * @return {@code true} if the whole cluster faces away from the camera and can be culled
     */
    public static boolean isBackfacing(float cx, float cy, float cz, float radius, float ax, float ay, float az,
            float cosA, float cameraX, float cameraY, float cameraZ) {
        if (!(cosA > 0)) {
            return false;
        }
        double sinA = Math.sqrt((1 - (double) cosA) * (1 + (double) cosA));
        double dx = (double) cx - cameraX, dy = (double) cy - cameraY, dz = (double) cz - cameraZ;
        double da = dx * ax + dy * ay + dz * az;
        double crx = dy * az - dz * ay, cry = dz * ax - dx * az, crz = dx * ay - dy * ax;
        return da * cosA - Math.sqrt(crx * crx + cry * cry + crz * crz) * sinA > radius;
    }

    /**
     * Packs a cone into 32 bits: the axis as three snorm8 (x in the low byte), and {@code cos α} as snorm8 in the
     * high byte. The packed cone always contains the original. The stored cosine is that of {@code α + δ}, where
     * {@code δ} is the angle between the axis and its quantized form, and it is rounded down. A cone with
     * {@code cos α <= 0} (or NaN) stores {@code cos α = -1}, which is never culled.
     *
     * @param ax the unit cone axis, x
     * @param ay the unit cone axis, y
     * @param az the unit cone axis, z
     * @param cosA the cosine of the cone's half angle
     * @return the packed cone
     */
    public static int quantize(float ax, float ay, float az, float cosA) {
        int qx = PackedNorm.packSnorm8(ax), qy = PackedNorm.packSnorm8(ay), qz = PackedNorm.packSnorm8(az);
        double bx = PackedNorm.unpackSnorm8(qx), by = PackedNorm.unpackSnorm8(qy), bz = PackedNorm.unpackSnorm8(qz);
        double bl = Math.sqrt(bx * bx + by * by + bz * bz);
        int code;
        if (!(cosA > 0) || !(bl > 0)) {
            code = -127;
        } else {
            double cosD = Math.min(1, (ax * bx + ay * by + az * bz) / bl);
            double sinD = Math.sqrt(Math.max(0, (1 - cosD) * (1 + cosD)));
            double sinA = Math.sqrt((1 - (double) cosA) * (1 + (double) cosA));
            // cos(alpha + delta), less a 1e-6 margin for the float normalization at decode time
            double widened = cosA * cosD - sinA * sinD - 1e-6;
            code = widened > 0 ? (int) Math.floor(widened * 127) : -127;
            // the decoded cosine is code / 127 computed in float: step down if that rounds above the bound
            while (code > -127 && code / 127f > widened) {
                code--;
            }
        }
        return qx | qy << 8 | qz << 16 | (code & 0xFF) << 24;
    }

    /**
     * Unpacks {@link #quantize}: the unit axis in {@code xyz} and {@code cos α} in {@code w}. A zero axis code
     * gives {@code (0, 0, 1, -1)}.
     *
     * @param packed the packed cone
     * @param dest receives the axis and the cosine
     * @return {@code dest}
     */
    public static Vector4f dequantize(int packed, Vector4f dest) {
        float x = PackedNorm.unpackSnorm8(packed), y = PackedNorm.unpackSnorm8(packed >>> 8);
        float z = PackedNorm.unpackSnorm8(packed >>> 16);
        double l = Math.sqrt((double) x * x + (double) y * y + (double) z * z);
        float cosA = PackedNorm.unpackSnorm8(packed >>> 24);
        if (!(l > 0)) {
            return dest.set(0, 0, 1, -1);
        }
        return dest.set((float) (x / l), (float) (y / l), (float) (z / l), cosA);
    }
}
