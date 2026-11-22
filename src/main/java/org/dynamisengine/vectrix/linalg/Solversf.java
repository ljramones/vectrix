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
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Matrix4fc;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.core.Vector4fc;

/**
 * Small dense linear solvers (single precision; computed in double) for 3×3 and 4×4 systems: Cholesky and LDLᵀ for symmetric matrices,
 * LU with partial pivoting for general ones, and condition-number estimates. Prefer these to forming an inverse.
 *
 * @since 2.2.0
 */
public final class Solversf {
    private Solversf() {
    }

    private static double[] in3(LinalgKernels.Workspace ws, double x, double y, double z, double w) {
        double[] b = ws.in3;
        b[0] = x;
        b[1] = y;
        b[2] = z;
        b[3] = w;
        return b;
    }

    /** Cholesky factor {@code a = L Lᵀ} of a symmetric positive-definite matrix into {@code lower}; false if {@code a} is not. */
    public static boolean cholesky(Matrix3fc a, Matrix3f lower) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] l = ws.out9a;
        if (!LinalgKernels.cholesky(Conv.rm(a, ws.in9), 3, l)) {
            return false;
        }
        Conv.set(lower, l);
        return true;
    }

    /** Solves {@code L Lᵀ x = b} with a factor from {@link #cholesky(Matrix3fc, Matrix3f)}. */
    public static Vector3f solveCholesky(Matrix3fc lower, Vector3fc b, Vector3f dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] x = ws.out3a;
        LinalgKernels.choleskySolve(Conv.rm(lower, ws.in9), 3, in3(ws, b.x(), b.y(), b.z(), 0), x);
        return dest.set((float) x[0], (float) x[1], (float) x[2]);
    }

    /** {@code a = L diag(d) Lᵀ} with unit lower-triangular {@code L}, for symmetric (possibly indefinite) {@code a}; false on a zero pivot. */
    public static boolean ldlt(Matrix3fc a, Matrix3f unitLower, Vector3f d) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] l = ws.out9a, dd = ws.out3a;
        if (!LinalgKernels.ldlt(Conv.rm(a, ws.in9), 3, l, dd)) {
            return false;
        }
        Conv.set(unitLower, l);
        d.set((float) dd[0], (float) dd[1], (float) dd[2]);
        return true;
    }

    /** Solves {@code L diag(d) Lᵀ x = b} with factors from {@link #ldlt}. */
    public static Vector3f solveLdlt(Matrix3fc unitLower, Vector3fc d, Vector3fc b, Vector3f dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] x = ws.out3a, dd = ws.out3b;
        dd[0] = d.x();
        dd[1] = d.y();
        dd[2] = d.z();
        LinalgKernels.ldltSolve(Conv.rm(unitLower, ws.in9), dd, 3, in3(ws, b.x(), b.y(), b.z(), 0), x);
        return dest.set((float) x[0], (float) x[1], (float) x[2]);
    }

    /** Solves {@code a x = b} by LU with partial pivoting; false (leaving {@code dest} unchanged) if {@code a} is singular. */
    public static boolean solve(Matrix3fc a, Vector3fc b, Vector3f dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] lu = ws.lu16, x = ws.out3a;
        int[] perm = ws.perm4;
        if (!LinalgKernels.lu(Conv.rm(a, ws.in9), 3, lu, perm)) {
            return false;
        }
        LinalgKernels.luSolve(lu, 3, perm, in3(ws, b.x(), b.y(), b.z(), 0), x);
        dest.set((float) x[0], (float) x[1], (float) x[2]);
        return true;
    }

    /** Solves {@code a x = b} for 4×4 {@code a} by LU with partial pivoting; false if {@code a} is singular. */
    public static boolean solve(Matrix4fc a, Vector4fc b, Vector4f dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] lu = ws.lu16, x = ws.out3a;
        int[] perm = ws.perm4;
        if (!LinalgKernels.lu(Conv.rm(a, ws.in9), 4, lu, perm)) {
            return false;
        }
        LinalgKernels.luSolve(lu, 4, perm, in3(ws, b.x(), b.y(), b.z(), b.w()), x);
        dest.set((float) x[0], (float) x[1], (float) x[2], (float) x[3]);
        return true;
    }

    /** Cholesky factor of a symmetric positive-definite 4×4 matrix; false if it is not. */
    public static boolean cholesky(Matrix4fc a, Matrix4f lower) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] l = ws.out9a;
        if (!LinalgKernels.cholesky(Conv.rm(a, ws.in9), 4, l)) {
            return false;
        }
        Conv.set(lower, l);
        return true;
    }

    /** Solves {@code L Lᵀ x = b} with a 4×4 Cholesky factor. */
    public static Vector4f solveCholesky(Matrix4fc lower, Vector4fc b, Vector4f dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] x = ws.out3a;
        LinalgKernels.choleskySolve(Conv.rm(lower, ws.in9), 4, in3(ws, b.x(), b.y(), b.z(), b.w()), x);
        return dest.set((float) x[0], (float) x[1], (float) x[2], (float) x[3]);
    }

    /** 1-norm condition number {@code ||a||₁ ||a⁻¹||₁}; infinite for a singular matrix. */
    public static float condition1(Matrix3fc a) {
        return (float) LinalgKernels.condition1(Conv.rm(a, LinalgKernels.ws().in9), 3);
    }

    /** 1-norm condition number of a 4×4 matrix; infinite for a singular matrix. */
    public static float condition1(Matrix4fc a) {
        return (float) LinalgKernels.condition1(Conv.rm(a, LinalgKernels.ws().in9), 4);
    }
}
