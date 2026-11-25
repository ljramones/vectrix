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
import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Matrix4dc;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.core.Vector4dc;

/**
 * Small dense linear solvers (double precision) for 3×3 and 4×4 systems: Cholesky and LDLᵀ for symmetric matrices,
 * LU with partial pivoting for general ones, and 1-norm condition numbers. Prefer these to forming an inverse.
 * <p>
 * Every input is read before an output is written, so outputs may alias inputs. The methods use a per-thread
 * workspace: they are safe to call from any thread and do not allocate after a thread's first call.
 *
 * @since 2.2.0
 */
public final class Solversd {
    private Solversd() {
    }

    private static double[] in3(LinalgKernels.Workspace ws, double x, double y, double z, double w) {
        double[] b = ws.in3;
        b[0] = x;
        b[1] = y;
        b[2] = z;
        b[3] = w;
        return b;
    }

    /**
     * Computes the Cholesky factor {@code a = L Lᵀ} of a symmetric positive-definite matrix. Only the lower triangle
     * of {@code a} is read. {@code lower} may be {@code a}.
     *
     * @param a the symmetric positive-definite matrix
     * @param lower receives the lower-triangular factor {@code L} (zeros above the diagonal); unchanged on failure
     * @return {@code true} on success, {@code false} if a pivot is not positive (the matrix is not positive definite,
     *         or contains NaN)
     */
    public static boolean cholesky(Matrix3dc a, Matrix3d lower) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] l = ws.out9a;
        if (!LinalgKernels.cholesky(Conv.rm(a, ws.in9), 3, l)) {
            return false;
        }
        Conv.set(lower, l);
        return true;
    }

    /**
     * Solves {@code L Lᵀ x = b} with a factor from {@link #cholesky(Matrix3dc, Matrix3d)}. Nothing is checked: a zero
     * on the diagonal of {@code lower} gives infinite or NaN components. {@code dest} may be {@code b}.
     *
     * @param lower the lower-triangular Cholesky factor
     * @param b the right-hand side
     * @param dest receives the solution {@code x}
     * @return {@code dest}
     */
    public static Vector3d solveCholesky(Matrix3dc lower, Vector3dc b, Vector3d dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] x = ws.out3a;
        LinalgKernels.choleskySolve(Conv.rm(lower, ws.in9), 3, in3(ws, b.x(), b.y(), b.z(), 0), x);
        return dest.set(x[0], x[1], x[2]);
    }

    /**
     * Computes the factorization {@code a = L diag(d) Lᵀ} with unit lower-triangular {@code L}, for a symmetric
     * (possibly indefinite) matrix, without pivoting. Only the lower triangle of {@code a} is read. {@code unitLower}
     * may be {@code a}.
     *
     * @param a the symmetric matrix
     * @param unitLower receives {@code L}, with ones on the diagonal; unchanged on failure
     * @param d receives the diagonal of {@code diag(d)}; unchanged on failure
     * @return {@code true} on success, {@code false} if a pivot is zero or not finite
     */
    public static boolean ldlt(Matrix3dc a, Matrix3d unitLower, Vector3d d) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] l = ws.out9a, dd = ws.out3a;
        if (!LinalgKernels.ldlt(Conv.rm(a, ws.in9), 3, l, dd)) {
            return false;
        }
        Conv.set(unitLower, l);
        d.set(dd[0], dd[1], dd[2]);
        return true;
    }

    /**
     * Solves {@code L diag(d) Lᵀ x = b} with factors from {@link #ldlt}. Nothing is checked: a zero in {@code d}
     * gives infinite or NaN components. {@code dest} may be any of the inputs.
     *
     * @param unitLower the unit lower-triangular factor {@code L}
     * @param d the diagonal factor
     * @param b the right-hand side
     * @param dest receives the solution {@code x}
     * @return {@code dest}
     */
    public static Vector3d solveLdlt(Matrix3dc unitLower, Vector3dc d, Vector3dc b, Vector3d dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] x = ws.out3a, dd = ws.out3b;
        dd[0] = d.x();
        dd[1] = d.y();
        dd[2] = d.z();
        LinalgKernels.ldltSolve(Conv.rm(unitLower, ws.in9), dd, 3, in3(ws, b.x(), b.y(), b.z(), 0), x);
        return dest.set(x[0], x[1], x[2]);
    }

    /**
     * Solves {@code a x = b} by LU decomposition with partial pivoting. Only an exactly zero pivot counts as
     * singular; a nearly singular matrix gives a large, inaccurate solution (see {@link #condition1(Matrix3dc)}).
     * {@code dest} may be {@code b}.
     *
     * @param a the matrix
     * @param b the right-hand side
     * @param dest receives the solution {@code x}; unchanged if {@code a} is singular
     * @return {@code true} on success, {@code false} if {@code a} is singular
     */
    public static boolean solve(Matrix3dc a, Vector3dc b, Vector3d dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] lu = ws.lu16, x = ws.out3a;
        int[] perm = ws.perm4;
        if (!LinalgKernels.lu(Conv.rm(a, ws.in9), 3, lu, perm)) {
            return false;
        }
        LinalgKernels.luSolve(lu, 3, perm, in3(ws, b.x(), b.y(), b.z(), 0), x);
        dest.set(x[0], x[1], x[2]);
        return true;
    }

    /**
     * Solves {@code a x = b} for a 4×4 matrix by LU decomposition with partial pivoting. Only an exactly zero pivot
     * counts as singular. {@code dest} may be {@code b}.
     *
     * @param a the matrix
     * @param b the right-hand side
     * @param dest receives the solution {@code x}; unchanged if {@code a} is singular
     * @return {@code true} on success, {@code false} if {@code a} is singular
     */
    public static boolean solve(Matrix4dc a, Vector4dc b, Vector4d dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] lu = ws.lu16, x = ws.out3a;
        int[] perm = ws.perm4;
        if (!LinalgKernels.lu(Conv.rm(a, ws.in9), 4, lu, perm)) {
            return false;
        }
        LinalgKernels.luSolve(lu, 4, perm, in3(ws, b.x(), b.y(), b.z(), b.w()), x);
        dest.set(x[0], x[1], x[2], x[3]);
        return true;
    }

    /**
     * Computes the Cholesky factor {@code a = L Lᵀ} of a symmetric positive-definite 4×4 matrix. Only the lower
     * triangle of {@code a} is read. {@code lower} may be {@code a}.
     *
     * @param a the symmetric positive-definite matrix
     * @param lower receives the lower-triangular factor {@code L} (zeros above the diagonal); unchanged on failure
     * @return {@code true} on success, {@code false} if a pivot is not positive (the matrix is not positive definite,
     *         or contains NaN)
     */
    public static boolean cholesky(Matrix4dc a, Matrix4d lower) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] l = ws.out9a;
        if (!LinalgKernels.cholesky(Conv.rm(a, ws.in9), 4, l)) {
            return false;
        }
        Conv.set(lower, l);
        return true;
    }

    /**
     * Solves {@code L Lᵀ x = b} with a 4×4 factor from {@link #cholesky(Matrix4dc, Matrix4d)}. Nothing is checked: a
     * zero on the diagonal of {@code lower} gives infinite or NaN components. {@code dest} may be {@code b}.
     *
     * @param lower the lower-triangular Cholesky factor
     * @param b the right-hand side
     * @param dest receives the solution {@code x}
     * @return {@code dest}
     */
    public static Vector4d solveCholesky(Matrix4dc lower, Vector4dc b, Vector4d dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] x = ws.out3a;
        LinalgKernels.choleskySolve(Conv.rm(lower, ws.in9), 4, in3(ws, b.x(), b.y(), b.z(), b.w()), x);
        return dest.set(x[0], x[1], x[2], x[3]);
    }

    /**
     * Returns the 1-norm condition number {@code ||a||₁ ||a⁻¹||₁}, computed exactly from an LU decomposition (not
     * estimated). Large values mean that solving with {@code a} loses about {@code log10} of that many digits.
     *
     * @param a the matrix
     * @return the condition number, at least 1, or {@code POSITIVE_INFINITY} if a pivot is exactly zero
     */
    public static double condition1(Matrix3dc a) {
        return LinalgKernels.condition1(Conv.rm(a, LinalgKernels.ws().in9), 3);
    }

    /**
     * Returns the 1-norm condition number {@code ||a||₁ ||a⁻¹||₁} of a 4×4 matrix, computed exactly from an LU
     * decomposition.
     *
     * @param a the matrix
     * @return the condition number, at least 1, or {@code POSITIVE_INFINITY} if a pivot is exactly zero
     */
    public static double condition1(Matrix4dc a) {
        return LinalgKernels.condition1(Conv.rm(a, LinalgKernels.ws().in9), 4);
    }
}
