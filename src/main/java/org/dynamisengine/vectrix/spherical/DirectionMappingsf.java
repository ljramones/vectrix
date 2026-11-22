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
package org.dynamisengine.vectrix.spherical;

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3f;

/**
 * Direction ↔ texture-coordinate mappings for probes and environment maps (single precision).
 * <ul>
 * <li>Octahedral (Engelhardt and Dachsbacher 2008; Cigolle et al. 2014): the sphere onto {@code [−1, 1]²}, upper
 * hemisphere in the inner diamond. Map to {@code [0, 1]²} with {@code uv * 0.5 + 0.5}. (For bit-packed normals see
 * {@code gpu.OctaNormal}.)</li>
 * <li>Hemi-octahedral: the upper hemisphere ({@code z ≥ 0}) onto the whole square.</li>
 * <li>Cubemap: face selection and face coordinates in the OpenGL / Vulkan convention (faces +X, −X, +Y, −Y, +Z, −Z
 * as 0..5; {@code u} right and {@code v} down on each face, as in the specification's major-axis table), and the
 * exact solid angle of a texel for weighting probe filtering.</li>
 * </ul>
 *
 * @since 2.3.0
 */
public final class DirectionMappingsf {
    private DirectionMappingsf() {
    }

    private static float signNotZero(float v) {
        return v >= 0.0f ? 1.0f : -1.0f;
    }

    /** Octahedral coordinates in {@code [−1, 1]²} of the direction {@code (x, y, z)} (need not be unit). */
    public static Vector2f octahedralEncode(float x, float y, float z, Vector2f dest) {
        float s = java.lang.Math.abs(x) + java.lang.Math.abs(y) + java.lang.Math.abs(z);
        float px = x / s, py = y / s;
        if (z < 0.0f) {
            float ox = (1.0f - java.lang.Math.abs(py)) * signNotZero(px);
            float oy = (1.0f - java.lang.Math.abs(px)) * signNotZero(py);
            return dest.set(ox, oy);
        }
        return dest.set(px, py);
    }

    /** Unit direction of the octahedral coordinates {@code (u, v)} in {@code [−1, 1]²}. */
    public static Vector3f octahedralDecode(float u, float v, Vector3f dest) {
        float z = 1.0f - java.lang.Math.abs(u) - java.lang.Math.abs(v);
        float x = u, y = v;
        if (z < 0.0f) {
            x = (1.0f - java.lang.Math.abs(v)) * signNotZero(u);
            y = (1.0f - java.lang.Math.abs(u)) * signNotZero(v);
        }
        float inv = 1.0f / (float) java.lang.Math.sqrt(x * x + y * y + z * z);
        return dest.set(x * inv, y * inv, z * inv);
    }

    /** Hemi-octahedral coordinates in {@code [−1, 1]²} of an upper-hemisphere direction ({@code z ≥ 0}). */
    public static Vector2f hemiOctahedralEncode(float x, float y, float z, Vector2f dest) {
        float s = java.lang.Math.abs(x) + java.lang.Math.abs(y) + z;
        float px = x / s, py = y / s;
        return dest.set(px + py, px - py);
    }

    /** Unit upper-hemisphere direction of hemi-octahedral coordinates in {@code [−1, 1]²}. */
    public static Vector3f hemiOctahedralDecode(float u, float v, Vector3f dest) {
        float x = 0.5f * (u + v), y = 0.5f * (u - v);
        float z = 1.0f - java.lang.Math.abs(x) - java.lang.Math.abs(y);
        float inv = 1.0f / (float) java.lang.Math.sqrt(x * x + y * y + z * z);
        return dest.set(x * inv, y * inv, z * inv);
    }

    /**
     * Cubemap face (0..5 for +X, −X, +Y, −Y, +Z, −Z) of the direction {@code (x, y, z)}, with its face coordinates
     * in {@code [0, 1]²} written to {@code uvDest}. Ties go to x, then y.
     */
    public static int cubeFace(float x, float y, float z, Vector2f uvDest) {
        float ax = java.lang.Math.abs(x), ay = java.lang.Math.abs(y), az = java.lang.Math.abs(z);
        int face;
        float sc, tc, ma;
        if (ax >= ay && ax >= az) {
            face = x >= 0.0f ? 0 : 1;
            sc = x >= 0.0f ? -z : z;
            tc = -y;
            ma = ax;
        } else if (ay >= az) {
            face = y >= 0.0f ? 2 : 3;
            sc = x;
            tc = y >= 0.0f ? z : -z;
            ma = ay;
        } else {
            face = z >= 0.0f ? 4 : 5;
            sc = z >= 0.0f ? x : -x;
            tc = -y;
            ma = az;
        }
        uvDest.set(0.5f * (sc / ma + 1.0f), 0.5f * (tc / ma + 1.0f));
        return face;
    }

    /** Unit direction through the face coordinates {@code (u, v)} in {@code [0, 1]²} of cubemap face {@code face}. */
    public static Vector3f cubeDirection(int face, float u, float v, Vector3f dest) {
        float sc = 2.0f * u - 1.0f, tc = 2.0f * v - 1.0f;
        float x, y, z;
        switch (face) {
            case 0: x = 1.0f; y = -tc; z = -sc; break;
            case 1: x = -1.0f; y = -tc; z = sc; break;
            case 2: x = sc; y = 1.0f; z = tc; break;
            case 3: x = sc; y = -1.0f; z = -tc; break;
            case 4: x = sc; y = -tc; z = 1.0f; break;
            case 5: x = -sc; y = -tc; z = -1.0f; break;
            default: throw new IllegalArgumentException("face " + face);
        }
        float inv = 1.0f / (float) java.lang.Math.sqrt(x * x + y * y + z * z);
        return dest.set(x * inv, y * inv, z * inv);
    }

    private static double areaElement(double x, double y) {
        return AccurateMath.atan2(x * y, java.lang.Math.sqrt(x * x + y * y + 1.0));
    }

    /** Exact solid angle of texel {@code (i, j)} of a {@code size × size} cubemap face (the same on every face). */
    public static float cubeTexelSolidAngle(int size, int i, int j) {
        double x0 = 2.0 * i / size - 1.0, x1 = 2.0 * (i + 1) / size - 1.0;
        double y0 = 2.0 * j / size - 1.0, y1 = 2.0 * (j + 1) / size - 1.0;
        return (float) (areaElement(x0, y0) - areaElement(x0, y1) - areaElement(x1, y0) + areaElement(x1, y1));
    }
}
