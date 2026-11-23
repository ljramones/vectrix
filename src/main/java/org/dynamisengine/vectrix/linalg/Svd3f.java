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
import org.dynamisengine.vectrix.core.Vector3f;

/**
 * Singular value decomposition of 3×3 matrices (single precision; computed in double) in the signed form of McAdams et
 * al. (2011), which keeps both rotations proper, as deformation and shape-matching code needs.
 *
 * @since 2.2.0
 */
public final class Svd3f {
    private Svd3f() {
    }

    /**
     * {@code a = U diag(σ) Vᵀ} with {@code U} and {@code V} proper rotations and {@code σ0 >= σ1 >= |σ2|}. {@code σ2}
     * is negative exactly when {@code det(a) < 0} (the reflection is carried by the smallest singular value rather than
     * by a rotation). Any output may alias {@code a}. {@code V} comes from the eigenvectors of {@code aᵀa} and {@code
     * U} from a QR decomposition of {@code a V}. Uses a per-thread workspace; does not allocate after the thread's
     * first call.
     *
     * @param a the matrix to decompose
     * @param u receives the left rotation {@code U}
     * @param sigma receives the signed singular values {@code σ0, σ1, σ2}
     * @param v receives the right rotation {@code V}
     * @return {@code sigma}
     */
    public static Vector3f decompose(Matrix3fc a, Matrix3f u, Vector3f sigma, Matrix3f v) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] uu = ws.out9a, s = ws.out3a, vv = ws.out9b;
        LinalgKernels.svd3(Conv.rm(a, ws.in9), uu, s, vv);
        Conv.set(u, uu);
        Conv.set(v, vv);
        return sigma.set((float) s[0], (float) s[1], (float) s[2]);
    }

    /**
     * 2-norm condition number {@code σ0 / |σ2|}, from {@link #decompose}.
     *
     * @param a the matrix
     * @return the condition number, at least 1, or positive infinity when the smallest singular value is exactly zero
     */
    public static float condition(Matrix3fc a) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] s = ws.out3a;
        LinalgKernels.svd3(Conv.rm(a, ws.in9), ws.out9a, s, ws.out9b);
        double min = java.lang.Math.abs(s[2]);
        return min == 0.0 ? Float.POSITIVE_INFINITY : (float) (s[0] / min);
    }
}
