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
import org.dynamisengine.vectrix.core.Vector3d;

/**
 * Eigen-decomposition of symmetric 3×3 matrices (double precision) by cyclic Jacobi rotations, for inertia principal
 * axes, PCA of point sets and covariance analysis.
 *
 * @since 2.2.0
 */
public final class Eigen3d {
    private Eigen3d() {
    }

    /**
     * {@code a = V diag(λ) Vᵀ}: writes the eigenvalues in descending order to {@code eigenvalues} and the matching unit
     * eigenvectors to the columns of {@code eigenvectors}, which is a proper rotation (det +1). Only the symmetric part
     * of {@code a} is used. {@code eigenvectors} may alias {@code a}. The Jacobi iteration stops when the off-diagonal
     * part is negligible or after 64 sweeps. Uses a per-thread workspace; does not allocate after the thread's first
     * call.
     *
     * @param a the symmetric matrix
     * @param eigenvalues receives the eigenvalues, largest first
     * @param eigenvectors receives the unit eigenvectors as columns, in the order of {@code eigenvalues}
     * @return {@code eigenvalues}
     */
    public static Vector3d symmetric(Matrix3dc a, Vector3d eigenvalues, Matrix3d eigenvectors) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] w = ws.out3a, v = ws.out9a;
        LinalgKernels.eigenSym3(Conv.rm(a, ws.in9), w, v);
        Conv.set(eigenvectors, v);
        return eigenvalues.set(w[0], w[1], w[2]);
    }
}
