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

import org.dynamisengine.vectrix.camera.DepthRange;

import org.dynamisengine.vectrix.core.Matrix4fc;

/**
 * Packed frustum plane storage for batch culling.
 */
public class FrustumPlanes {
    public static final int PLANE_NX = 0;
    public static final int PLANE_PX = 1;
    public static final int PLANE_NY = 2;
    public static final int PLANE_PY = 3;
    public static final int PLANE_NZ = 4;
    public static final int PLANE_PZ = 5;
    public static final int COUNT = 6;

    public final float[] x = new float[COUNT];
    public final float[] y = new float[COUNT];
    public final float[] z = new float[COUNT];
    public final float[] w = new float[COUNT];

    public FrustumPlanes set(Matrix4fc m) {
        return set(m, false);
    }

    public FrustumPlanes set(Matrix4fc m, boolean normalize) {
        return set(m, normalize, false);
    }

    /**
     * Extract the six planes of a view-projection matrix.
     *
     * @param zZeroToOne {@code true} for clip-space depth in [0, 1] (Vulkan, Direct3D, JOML's {@code zZeroToOne}
     *                   projections): the near plane is then {@code z >= 0} rather than {@code z >= -w}. With the
     *                   default [-1, 1] extraction such a frustum gets a near plane behind the real one, which is
     *                   conservative but culls nothing near the camera.
     */
    public FrustumPlanes set(Matrix4fc m, boolean normalize, boolean zZeroToOne) {
        return set(m, normalize, zZeroToOne ? DepthRange.ZERO_TO_ONE : DepthRange.NEGATIVE_ONE_TO_ONE);
    }

    /**
     * Extracts the planes for {@code depthRange}, including reverse-Z, which the {@code zZeroToOne} flag cannot
     * express.
     *
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
