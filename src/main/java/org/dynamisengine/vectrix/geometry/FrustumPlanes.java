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

import org.dynamisengine.vectrix.core.DepthRange;

import org.dynamisengine.vectrix.core.Matrix4fc;

/**
 * Packed frustum plane storage for batch culling: the six planes of a view-projection matrix in structure-of-arrays
 * form, one array per plane coefficient. Plane {@code i} is {@code x[i] * px + y[i] * py + z[i] * pz + w[i] = 0},
 * with the normal pointing into the frustum, so a point is inside a plane when that expression is at least zero.
 * <p>
 * Instances are mutable and not thread-safe; {@code set} and {@link #getVec4Array} do not allocate.
 */
public class FrustumPlanes {

    /** Creates the six planes, all zero; call {@code set} to extract them from a matrix. */
    public FrustumPlanes() {
    }
    /** Index of the left plane ({@code x = -w} in clip space). */
    public static final int PLANE_NX = 0;
    /** Index of the right plane ({@code x = w} in clip space). */
    public static final int PLANE_PX = 1;
    /** Index of the bottom plane ({@code y = -w} in clip space). */
    public static final int PLANE_NY = 2;
    /** Index of the top plane ({@code y = w} in clip space). */
    public static final int PLANE_PY = 3;
    /** Index of the near plane (at the depth range's near NDC depth). */
    public static final int PLANE_NZ = 4;
    /** Index of the far plane (at the depth range's far NDC depth). */
    public static final int PLANE_PZ = 5;
    /** The number of planes, 6. */
    public static final int COUNT = 6;

    /** The x component of each plane's normal, indexed by {@code PLANE_NX} to {@code PLANE_PZ}. */
    public final float[] x = new float[COUNT];
    /** The y component of each plane's normal, indexed by {@code PLANE_NX} to {@code PLANE_PZ}. */
    public final float[] y = new float[COUNT];
    /** The z component of each plane's normal, indexed by {@code PLANE_NX} to {@code PLANE_PZ}. */
    public final float[] z = new float[COUNT];
    /** The constant term of each plane, indexed by {@code PLANE_NX} to {@code PLANE_PZ}. */
    public final float[] w = new float[COUNT];

    /**
     * Extracts the six planes of a view-projection matrix with OpenGL depth ({@code [-1, 1]}), without normalizing
     * them.
     *
     * @param m the view-projection matrix
     * @return this
     */
    public FrustumPlanes set(Matrix4fc m) {
        return set(m, false);
    }

    /**
     * Extracts the six planes of a view-projection matrix with OpenGL depth ({@code [-1, 1]}).
     *
     * @param m the view-projection matrix
     * @param normalize {@code true} to scale each plane so its normal has unit length, making the plane expression
     *        a signed distance
     * @return this
     */
    public FrustumPlanes set(Matrix4fc m, boolean normalize) {
        return set(m, normalize, false);
    }

    /**
     * Extracts the six planes of a view-projection matrix.
     *
     * @param m the view-projection matrix
     * @param normalize {@code true} to scale each plane so its normal has unit length, making the plane expression
     *        a signed distance
     * @param zZeroToOne {@code true} for clip-space depth in [0, 1] (Vulkan, Direct3D, JOML's {@code zZeroToOne}
     *                   projections): the near plane is then {@code z >= 0} rather than {@code z >= -w}. With the
     *                   default [-1, 1] extraction such a frustum gets a near plane behind the real one, which is
     *                   conservative but culls nothing near the camera.
     * @return this
     */
    public FrustumPlanes set(Matrix4fc m, boolean normalize, boolean zZeroToOne) {
        return set(m, normalize, zZeroToOne ? DepthRange.ZERO_TO_ONE : DepthRange.NEGATIVE_ONE_TO_ONE);
    }

    /**
     * Extracts the planes for {@code depthRange}, including reverse-Z, which the {@code zZeroToOne} flag cannot
     * express. With {@code normalize}, a plane whose normal is zero (the far plane of an infinite projection) becomes
     * {@code (0, 0, 0, ±1)}, keeping the sign of its constant term.
     *
     * @param m the view-projection matrix
     * @param normalize {@code true} to scale each plane so its normal has unit length, making the plane expression
     *        a signed distance
     * @param depthRange the clip-space depth convention of {@code m}
     * @return this
     * @since 2.5.0
     */
    public FrustumPlanes set(Matrix4fc m, boolean normalize, DepthRange depthRange) {
        setPlane(PLANE_NX, m.m03() + m.m00(), m.m13() + m.m10(), m.m23() + m.m20(), m.m33() + m.m30(), normalize);
        setPlane(PLANE_PX, m.m03() - m.m00(), m.m13() - m.m10(), m.m23() - m.m20(), m.m33() - m.m30(), normalize);
        setPlane(PLANE_NY, m.m03() + m.m01(), m.m13() + m.m11(), m.m23() + m.m21(), m.m33() + m.m31(), normalize);
        setPlane(PLANE_PY, m.m03() - m.m01(), m.m13() - m.m11(), m.m23() - m.m21(), m.m33() - m.m31(), normalize);
        // near and far planes at the depth range's NDC depths c: sgn * (row2 - c * row3), facing inwards
        float cn = (float) depthRange.nearNdc(), cf = (float) depthRange.farNdc();
        float sn = depthRange.farNdc() > depthRange.nearNdc() ? 1 : -1;
        setPlane(PLANE_NZ, sn * (m.m02() - cn * m.m03()), sn * (m.m12() - cn * m.m13()), sn * (m.m22() - cn * m.m23()),
                sn * (m.m32() - cn * m.m33()), normalize);
        setPlane(PLANE_PZ, -sn * (m.m02() - cf * m.m03()), -sn * (m.m12() - cf * m.m13()), -sn * (m.m22() - cf * m.m23()),
                -sn * (m.m32() - cf * m.m33()), normalize);
        return this;
    }

    private void setPlane(int i, float px, float py, float pz, float pw, boolean normalize) {
        if (normalize && px == 0 && py == 0 && pz == 0) { // the plane at infinity
            pw = pw >= 0 ? 1 : -1;
        } else if (normalize) {
            float inv = org.dynamisengine.vectrix.core.Math.invsqrt(px * px + py * py + pz * pz);
            px *= inv;
            py *= inv;
            pz *= inv;
            pw *= inv;
        }
        x[i] = px;
        y[i] = py;
        z[i] = pz;
        w[i] = pw;
    }

    /**
     * Writes the planes as six {@code vec4}s (normal xyz, distance w), in index order {@code PLANE_NX} to
     * {@code PLANE_PZ}, into {@code dest} from {@code offset}. This is the GLSL {@code vec4 planes[6]} layout under
     * std140, std430 and scalar alike (stride 16 bytes).
     *
     * @param dest receives 24 floats starting at {@code offset}
     * @param offset the index in {@code dest} of the first float
     * @return {@code dest}
     * @since 2.4.0
     */
    public float[] getVec4Array(float[] dest, int offset) {
        for (int i = 0; i < COUNT; i++) {
            dest[offset + 4 * i] = x[i];
            dest[offset + 4 * i + 1] = y[i];
            dest[offset + 4 * i + 2] = z[i];
            dest[offset + 4 * i + 3] = w[i];
        }
        return dest;
    }

}
