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
package org.dynamisengine.vectrix.linalg;

import org.dynamisengine.vectrix.core.Matrix3f;
import org.dynamisengine.vectrix.core.Matrix3fc;
import org.dynamisengine.vectrix.core.Matrix4x3f;
import org.dynamisengine.vectrix.core.Matrix4x3fc;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Exponential and logarithm maps of SO(3) (rotations) and SE(3) (rigid motions), double precision: rotation vectors
 * and twists are the tangent-space coordinates used for twist interpolation, angular velocity integration and
 * physics. Small angles use series expansions, and the SO(3) logarithm is exact up to π.
 *
 * @since 2.2.0
 */
public final class Lie3f {
    private Lie3f() {
    }

    /** Rotation {@code exp([w]×)}: angle {@code |w|} about {@code w} (Rodrigues). */
    public static Matrix3f expSo3(Vector3fc w, Matrix3f dest) {
        double[] r = LinalgKernels.ws().out9a;
        LinalgKernels.expSo3(w.x(), w.y(), w.z(), r);
        return Conv.set(dest, r);
    }

    /** Rotation vector {@code w} ({@code |w| <= π}) with {@code exp([w]×) = r}; {@code r} must be a rotation. */
    public static Vector3f logSo3(Matrix3fc r, Vector3f dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] w = ws.out3a;
        LinalgKernels.logSo3(Conv.rm(r, ws.in9), w);
        return dest.set((float) w[0], (float) w[1], (float) w[2]);
    }

    /** Rigid motion {@code exp} of the twist {@code (w, v)}: rotation {@code exp([w]×)} and translation {@code V v}. */
    public static Matrix4x3f expSe3(Vector3fc w, Vector3fc v, Matrix4x3f dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] r = ws.out9a, t = ws.out3a, ww = ws.in3, vv = ws.out3b;
        ww[0] = w.x();
        ww[1] = w.y();
        ww[2] = w.z();
        vv[0] = v.x();
        vv[1] = v.y();
        vv[2] = v.z();
        LinalgKernels.expSe3(ww, vv, r, t);
        return dest.set((float) r[0], (float) r[3], (float) r[6], (float) r[1], (float) r[4], (float) r[7], (float) r[2], (float) r[5], (float) r[8], (float) t[0], (float) t[1], (float) t[2]);
    }

    /** Twist {@code (w, v)} of a rigid motion (rotation part a rotation, translation in column 3); inverse of {@link #expSe3}. */
    public static void logSe3(Matrix4x3fc m, Vector3f w, Vector3f v) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] r = ws.in9, t = ws.in3, ww = ws.out3a, vv = ws.out3b;
        r[0] = m.m00();
        r[1] = m.m10();
        r[2] = m.m20();
        r[3] = m.m01();
        r[4] = m.m11();
        r[5] = m.m21();
        r[6] = m.m02();
        r[7] = m.m12();
        r[8] = m.m22();
        t[0] = m.m30();
        t[1] = m.m31();
        t[2] = m.m32();
        LinalgKernels.logSe3(r, t, ww, vv);
        w.set((float) ww[0], (float) ww[1], (float) ww[2]);
        v.set((float) vv[0], (float) vv[1], (float) vv[2]);
    }
}
