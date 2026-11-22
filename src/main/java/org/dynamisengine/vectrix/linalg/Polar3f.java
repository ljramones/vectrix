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

/**
 * Polar decomposition of 3×3 matrices (single precision; computed in double), from the signed SVD: the rotation closest to a matrix,
 * as used by shape matching, deformation gradients and orthonormalising drifting rotation matrices.
 *
 * @since 2.2.0
 */
public final class Polar3f {
    private Polar3f() {
    }

    /**
     * {@code a = R S} with {@code R = U Vᵀ} a proper rotation and {@code S = V diag(σ) Vᵀ} symmetric (positive
     * semi-definite when {@code det(a) >= 0}; otherwise the reflection is in {@code S}). {@code R} is the rotation that
     * minimises {@code ||a - R||_F}. Outputs may alias {@code a}.
     *
     * @return {@code rotation}
     */
    public static Matrix3f decompose(Matrix3fc a, Matrix3f rotation, Matrix3f stretch) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] r = ws.out9a, s = ws.out9b;
        LinalgKernels.polar3(Conv.rm(a, ws.in9), r, s);
        Conv.set(stretch, s);
        return Conv.set(rotation, r);
    }

    /** The rotation factor of {@link #decompose} alone. {@code dest} may alias {@code a}. */
    public static Matrix3f rotation(Matrix3fc a, Matrix3f dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] r = ws.out9a;
        LinalgKernels.polar3(Conv.rm(a, ws.in9), r, ws.out9b);
        return Conv.set(dest, r);
    }
}
