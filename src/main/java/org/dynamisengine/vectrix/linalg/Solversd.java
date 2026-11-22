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
 * LU with partial pivoting for general ones, and condition-number estimates. Prefer these to forming an inverse.
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

    /** Cholesky factor {@code a = L Lᵀ} of a symmetric positive-definite matrix into {@code lower}; false if {@code a} is not. */
    public static boolean cholesky(Matrix3dc a, Matrix3d lower) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] l = ws.out9a;
        if (!LinalgKernels.cholesky(Conv.rm(a, ws.in9), 3, l)) {
            return false;
        }
        Conv.set(lower, l);
        return true;
    }

    /** Solves {@code L Lᵀ x = b} with a factor from {@link #cholesky(Matrix3dc, Matrix3d)}. */
    public static Vector3d solveCholesky(Matrix3dc lower, Vector3dc b, Vector3d dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] x = ws.out3a;
        LinalgKernels.choleskySolve(Conv.rm(lower, ws.in9), 3, in3(ws, b.x(), b.y(), b.z(), 0), x);
        return dest.set(x[0], x[1], x[2]);
    }

    /** {@code a = L diag(d) Lᵀ} with unit lower-triangular {@code L}, for symmetric (possibly indefinite) {@code a}; false on a zero pivot. */
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

    /** Solves {@code L diag(d) Lᵀ x = b} with factors from {@link #ldlt}. */
    public static Vector3d solveLdlt(Matrix3dc unitLower, Vector3dc d, Vector3dc b, Vector3d dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] x = ws.out3a, dd = ws.out3b;
        dd[0] = d.x();
        dd[1] = d.y();
        dd[2] = d.z();
        LinalgKernels.ldltSolve(Conv.rm(unitLower, ws.in9), dd, 3, in3(ws, b.x(), b.y(), b.z(), 0), x);
        return dest.set(x[0], x[1], x[2]);
    }

    /** Solves {@code a x = b} by LU with partial pivoting; false (leaving {@code dest} unchanged) if {@code a} is singular. */
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

    /** Solves {@code a x = b} for 4×4 {@code a} by LU with partial pivoting; false if {@code a} is singular. */
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

    /** Cholesky factor of a symmetric positive-definite 4×4 matrix; false if it is not. */
    public static boolean cholesky(Matrix4dc a, Matrix4d lower) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] l = ws.out9a;
        if (!LinalgKernels.cholesky(Conv.rm(a, ws.in9), 4, l)) {
            return false;
        }
        Conv.set(lower, l);
        return true;
    }

    /** Solves {@code L Lᵀ x = b} with a 4×4 Cholesky factor. */
    public static Vector4d solveCholesky(Matrix4dc lower, Vector4dc b, Vector4d dest) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] x = ws.out3a;
        LinalgKernels.choleskySolve(Conv.rm(lower, ws.in9), 4, in3(ws, b.x(), b.y(), b.z(), b.w()), x);
        return dest.set(x[0], x[1], x[2], x[3]);
    }

    /** 1-norm condition number {@code ||a||₁ ||a⁻¹||₁}; infinite for a singular matrix. */
    public static double condition1(Matrix3dc a) {
        return LinalgKernels.condition1(Conv.rm(a, LinalgKernels.ws().in9), 3);
    }

    /** 1-norm condition number of a 4×4 matrix; infinite for a singular matrix. */
    public static double condition1(Matrix4dc a) {
        return LinalgKernels.condition1(Conv.rm(a, LinalgKernels.ws().in9), 4);
    }
}
