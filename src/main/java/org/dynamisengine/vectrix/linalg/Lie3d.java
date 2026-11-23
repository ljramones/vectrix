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

import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Matrix3dc;
import org.dynamisengine.vectrix.core.Matrix4x3d;
import org.dynamisengine.vectrix.core.Matrix4x3dc;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Exponential and logarithm maps of SO(3) (rotations) and SE(3) (rigid motions), double precision: rotation vectors
 * and twists are the tangent-space coordinates used for twist interpolation, angular velocity integration and
 * physics. Small angles use series expansions, and the SO(3) logarithm is exact up to π.
 *
 * @since 2.2.0
 */
public final class Lie3d {
    private Lie3d() {
    }

    /**
     * Rotation {@code exp([w]×)}: angle {@code |w|} radians about {@code w} (Rodrigues), counter-clockwise when
     * looking down the axis toward the origin. Angles below {@code 1e-4} use a series expansion.
     *
     * @param w the rotation vector (axis times angle in radians)
     * @param dest receives the rotation matrix
     * @return {@code dest}
     */
    public static Matrix3d expSo3(Vector3dc w, Matrix3d dest) {
        double[] r = LinalgKernels.ws().out9a;
        LinalgKernels.expSo3(w.x(), w.y(), w.z(), r);
        return Conv.set(dest, r);
    }

    /**
     * Rotation vector {@code w} ({@code |w| <= π}) with {@code exp([w]×) = r}; {@code r} must be a rotation (it is
     * not orthonormalized). Near {@code π} the axis comes from the symmetric part of {@code r}, so the result stays
     * accurate there. {@code dest} receives the axis times the angle in radians.
     *
     * @param r the rotation matrix
     * @param dest receives the rotation vector
     * @return {@code dest}
     */
    public static Vector3d logSo3(Matrix3dc r, Vector3d dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] w = ws.out3a;
        LinalgKernels.logSo3(Conv.rm(r, ws.in9), w);
        return dest.set(w[0], w[1], w[2]);
    }

    /**
     * Rigid motion {@code exp} of the twist {@code (w, v)}: rotation {@code exp([w]×)} in the 3×3 part of
     * {@code dest} and translation {@code V v} in column 3, where {@code V} is the SE(3) left Jacobian of {@code w}.
     *
     * @param w the angular part of the twist (rotation vector, radians)
     * @param v the linear part of the twist
     * @param dest receives the rigid motion
     * @return {@code dest}
     */
    public static Matrix4x3d expSe3(Vector3dc w, Vector3dc v, Matrix4x3d dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] r = ws.out9a, t = ws.out3a, ww = ws.in3, vv = ws.out3b;
        ww[0] = w.x();
        ww[1] = w.y();
        ww[2] = w.z();
        vv[0] = v.x();
        vv[1] = v.y();
        vv[2] = v.z();
        LinalgKernels.expSe3(ww, vv, r, t);
        return dest.set(r[0], r[3], r[6], r[1], r[4], r[7], r[2], r[5], r[8], t[0], t[1], t[2]);
    }

    /**
     * Twist {@code (w, v)} of a rigid motion (rotation part a rotation, translation in column 3); inverse of
     * {@link #expSe3}. {@code |w| <= π}. {@code w} and {@code v} must be different objects.
     *
     * @param m the rigid motion
     * @param w receives the angular part of the twist (rotation vector, radians)
     * @param v receives the linear part of the twist
     */
    public static void logSe3(Matrix4x3dc m, Vector3d w, Vector3d v) {
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
        w.set(ww[0], ww[1], ww[2]);
        v.set(vv[0], vv[1], vv[2]);
    }
}
