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
package org.dynamisengine.vectrix.sh;

import org.dynamisengine.vectrix.core.Matrix3dc;
import org.dynamisengine.vectrix.core.Matrix3fc;

/**
 * Rotation of SH coefficient sets (bands 0..2 and 0..3, RGB). A rotation maps each band onto itself, so the
 * coefficients of band {@code l} transform by a {@code (2l+1) × (2l+1)} matrix. It is computed by projection: at
 * {@code 2l + 1} fixed directions {@code d_k}, the rotated function must equal the original at {@code Rᵀ d_k}, so
 * {@code M_l = Y_l(d)⁻¹ · Y_l(Rᵀ d)}, with the inverse precomputed once. This is exact (up to rounding), works for
 * the basis exactly as {@link ShBasis} defines it, and costs one basis evaluation per sample direction.
 * <p>
 * "Rotating by R" moves a feature at direction {@code u} to {@code R u}: if {@code g = rotate(f, R)} then
 * {@code g(R u) = f(u)}. For many probes, build the matrix once with
 * {@link #rotationMatrixL2}/{@link #rotationMatrixL3} and apply it with {@link #applyL2}/{@link #applyL3}.
 * <p>
 * Matrix layout: the band blocks one after another, each row-major: band 1 (3×3 at offset 0), band 2 (5×5 at 9),
 * band 3 (7×7 at 34). {@link #L2_MATRIX_SIZE} = 34 and {@link #L3_MATRIX_SIZE} = 83 floats.
 *
 * @since 2.3.0
 */
public final class ShRotation {
    /** Floats in an L2 rotation matrix (bands 1 and 2). */
    public static final int L2_MATRIX_SIZE = 9 + 25;
    /** Floats in an L3 rotation matrix (bands 1 to 3). */
    public static final int L3_MATRIX_SIZE = 9 + 25 + 49;

    private static final double[][] DIRS = new double[4][];
    private static final double[][] INV = new double[4][];

    /** Generic, well-spread sample directions (normalised integer vectors: deterministic, no transcendentals). */
    private static final int[] SAMPLE_VECTORS = {
        3, 1, 1, -1, 4, 1, 1, -2, 5, -4, -1, 2, 2, -3, -2, -1, 1, -5, 5, 3, -2,
    };

    static {
        for (int l = 1; l <= 3; l++) {
            int n = 2 * l + 1;
            double[] dirs = new double[3 * n];
            for (int k = 0; k < n; k++) {
                double x = SAMPLE_VECTORS[3 * k], y = SAMPLE_VECTORS[3 * k + 1], z = SAMPLE_VECTORS[3 * k + 2];
                double inv = 1.0 / java.lang.Math.sqrt(x * x + y * y + z * z);
                dirs[3 * k] = x * inv;
                dirs[3 * k + 1] = y * inv;
                dirs[3 * k + 2] = z * inv;
            }
            DIRS[l] = dirs;
            double[] y = new double[n * n];
            double[] basis = new double[16];
            for (int k = 0; k < n; k++) {
                ShBasis.evaluateL3(dirs[3 * k], dirs[3 * k + 1], dirs[3 * k + 2], basis);
                for (int m = 0; m < n; m++) {
                    y[k * n + m] = basis[l * l + m];
                }
            }
            INV[l] = invert(y, n);
        }
    }

    private ShRotation() {
    }

    /** Gauss-Jordan inverse with partial pivoting (class initialisation only). */
    private static double[] invert(double[] a, int n) {
        double[] m = a.clone();
        double[] inv = new double[n * n];
        for (int i = 0; i < n; i++) {
            inv[i * n + i] = 1.0;
        }
        for (int c = 0; c < n; c++) {
            int p = c;
            for (int r = c + 1; r < n; r++) {
                if (java.lang.Math.abs(m[r * n + c]) > java.lang.Math.abs(m[p * n + c])) {
                    p = r;
                }
            }
            if (java.lang.Math.abs(m[p * n + c]) < 1e-9) {
                throw new IllegalStateException("SH rotation sample directions are degenerate for band size " + n);
            }
            for (int j = 0; j < n; j++) {
                double t = m[c * n + j];
                m[c * n + j] = m[p * n + j];
                m[p * n + j] = t;
                t = inv[c * n + j];
                inv[c * n + j] = inv[p * n + j];
                inv[p * n + j] = t;
            }
            double d = m[c * n + c];
            for (int j = 0; j < n; j++) {
                m[c * n + j] /= d;
                inv[c * n + j] /= d;
            }
            for (int r = 0; r < n; r++) {
                if (r != c) {
                    double f = m[r * n + c];
                    for (int j = 0; j < n; j++) {
                        m[r * n + j] -= f * m[c * n + j];
                        inv[r * n + j] -= f * inv[c * n + j];
                    }
                }
            }
        }
        return inv;
    }

    /** Per-thread scratch: basis, rotated basis, double matrix, rotation, band buffer (double). */
    private static final ThreadLocal<double[][]> SCRATCH = ThreadLocal.withInitial(
            () -> new double[][] {new double[16], new double[49], new double[L3_MATRIX_SIZE], new double[9],
                new double[21]});
    /** Per-thread scratch: float matrix, band buffer (float). */
    private static final ThreadLocal<float[][]> SCRATCH_F = ThreadLocal.withInitial(
            () -> new float[][] {new float[L3_MATRIX_SIZE], new float[21]});

    /** Fills {@code out} (row-major blocks) with bands 1..maxBand of the rotation by {@code r} (row-major 3×3). */
    private static void build(double[] r, int maxBand, double[] out) {
        double[][] s = SCRATCH.get();
        double[] basis = s[0];
        double[] yr = s[1];
        int offset = 0;
        for (int l = 1; l <= maxBand; l++) {
            int n = 2 * l + 1;
            double[] dirs = DIRS[l];
            for (int k = 0; k < n; k++) {
                double dx = dirs[3 * k], dy = dirs[3 * k + 1], dz = dirs[3 * k + 2];
                // Rᵀ d
                double x = r[0] * dx + r[3] * dy + r[6] * dz;
                double y = r[1] * dx + r[4] * dy + r[7] * dz;
                double z = r[2] * dx + r[5] * dy + r[8] * dz;
                ShBasis.evaluateL3(x, y, z, basis);
                for (int m = 0; m < n; m++) {
                    yr[k * n + m] = basis[l * l + m];
                }
            }
            double[] inv = INV[l];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double sum = 0.0;
                    for (int k = 0; k < n; k++) {
                        sum += inv[i * n + k] * yr[k * n + j];
                    }
                    out[offset + i * n + j] = sum;
                }
            }
            offset += n * n;
        }
    }

    private static double[] rowMajor(Matrix3fc m, double[] d) {
        d[0] = m.m00();
        d[1] = m.m10();
        d[2] = m.m20();
        d[3] = m.m01();
        d[4] = m.m11();
        d[5] = m.m21();
        d[6] = m.m02();
        d[7] = m.m12();
        d[8] = m.m22();
        return d;
    }

    private static double[] rowMajor(Matrix3dc m, double[] d) {
        d[0] = m.m00();
        d[1] = m.m10();
        d[2] = m.m20();
        d[3] = m.m01();
        d[4] = m.m11();
        d[5] = m.m21();
        d[6] = m.m02();
        d[7] = m.m12();
        d[8] = m.m22();
        return d;
    }

    /**
     * Builds the L2 SH rotation matrix (bands 1..2) of {@code rotation}, computed in double and rounded to float,
     * in the block layout of the class comment. Thread-safe (per-thread scratch); does not allocate after the first
     * call on a thread.
     *
     * @param rotation a proper rotation (orthonormal, determinant 1); other matrices give meaningless results
     * @param out receives the matrix in elements 0..{@link #L2_MATRIX_SIZE} - 1
     * @return {@code out}
     * @throws ArrayIndexOutOfBoundsException if {@code out} is shorter than {@link #L2_MATRIX_SIZE}
     */
    public static float[] rotationMatrixL2(Matrix3fc rotation, float[] out) {
        return toFloat(rotation, 2, out);
    }

    /**
     * Builds the L3 SH rotation matrix (bands 1..3) of {@code rotation}, computed in double and rounded to float,
     * in the block layout of the class comment. Thread-safe (per-thread scratch); does not allocate after the first
     * call on a thread.
     *
     * @param rotation a proper rotation (orthonormal, determinant 1); other matrices give meaningless results
     * @param out receives the matrix in elements 0..{@link #L3_MATRIX_SIZE} - 1
     * @return {@code out}
     * @throws ArrayIndexOutOfBoundsException if {@code out} is shorter than {@link #L3_MATRIX_SIZE}
     */
    public static float[] rotationMatrixL3(Matrix3fc rotation, float[] out) {
        return toFloat(rotation, 3, out);
    }

    private static float[] toFloat(Matrix3fc rotation, int bands, float[] out) {
        double[][] s = SCRATCH.get();
        double[] m = s[2];
        build(rowMajor(rotation, s[3]), bands, m);
        int size = bands == 2 ? L2_MATRIX_SIZE : L3_MATRIX_SIZE;
        for (int i = 0; i < size; i++) {
            out[i] = (float) m[i];
        }
        return out;
    }

    /**
     * Builds the L2 SH rotation matrix (bands 1..2) of {@code rotation}, in double precision, in the block layout of
     * the class comment. Thread-safe (per-thread scratch); does not allocate after the first call on a thread.
     *
     * @param rotation a proper rotation (orthonormal, determinant 1); other matrices give meaningless results
     * @param out receives the matrix in elements 0..{@link #L2_MATRIX_SIZE} - 1
     * @return {@code out}
     * @throws ArrayIndexOutOfBoundsException if {@code out} is shorter than {@link #L2_MATRIX_SIZE}
     */
    public static double[] rotationMatrixL2(Matrix3dc rotation, double[] out) {
        build(rowMajor(rotation, SCRATCH.get()[3]), 2, out);
        return out;
    }

    /**
     * Builds the L3 SH rotation matrix (bands 1..3) of {@code rotation}, in double precision, in the block layout of
     * the class comment. Thread-safe (per-thread scratch); does not allocate after the first call on a thread.
     *
     * @param rotation a proper rotation (orthonormal, determinant 1); other matrices give meaningless results
     * @param out receives the matrix in elements 0..{@link #L3_MATRIX_SIZE} - 1
     * @return {@code out}
     * @throws ArrayIndexOutOfBoundsException if {@code out} is shorter than {@link #L3_MATRIX_SIZE}
     */
    public static double[] rotationMatrixL3(Matrix3dc rotation, double[] out) {
        build(rowMajor(rotation, SCRATCH.get()[3]), 3, out);
        return out;
    }

    /**
     * Applies an L2 SH rotation matrix to RGB coefficients: band 0 is copied, each band {@code l} in 1..2 is multiplied
     * by its block of {@code matrix}. Thread-safe (per-thread scratch); does not allocate after the first call on a
     * thread.
     *
     * @param matrix a matrix from {@code rotationMatrixL2}, at least {@link #L2_MATRIX_SIZE} elements
     * @param in the coefficients to rotate
     * @param dest receives the rotated coefficients; may be {@code in}
     * @return {@code dest}
     */
    public static ShCoeffs9f applyL2(float[] matrix, ShCoeffs9f in, ShCoeffs9f dest) {
        applyBands(matrix, in.c, dest.c, 2);
        return dest;
    }

    /**
     * Applies an L3 SH rotation matrix to RGB coefficients: band 0 is copied, each band {@code l} in 1..3 is multiplied
     * by its block of {@code matrix}. Thread-safe (per-thread scratch); does not allocate after the first call on a
     * thread.
     *
     * @param matrix a matrix from {@code rotationMatrixL3}, at least {@link #L3_MATRIX_SIZE} elements
     * @param in the coefficients to rotate
     * @param dest receives the rotated coefficients; may be {@code in}
     * @return {@code dest}
     */
    public static ShCoeffs16f applyL3(float[] matrix, ShCoeffs16f in, ShCoeffs16f dest) {
        applyBands(matrix, in.c, dest.c, 3);
        return dest;
    }

    /**
     * Applies an L2 SH rotation matrix to RGB coefficients: band 0 is copied, each band {@code l} in 1..2 is multiplied
     * by its block of {@code matrix}. Thread-safe (per-thread scratch); does not allocate after the first call on a
     * thread.
     *
     * @param matrix a matrix from {@code rotationMatrixL2}, at least {@link #L2_MATRIX_SIZE} elements
     * @param in the coefficients to rotate
     * @param dest receives the rotated coefficients; may be {@code in}
     * @return {@code dest}
     */
    public static ShCoeffs9d applyL2(double[] matrix, ShCoeffs9d in, ShCoeffs9d dest) {
        applyBands(matrix, in.c, dest.c, 2);
        return dest;
    }

    /**
     * Applies an L3 SH rotation matrix to RGB coefficients: band 0 is copied, each band {@code l} in 1..3 is multiplied
     * by its block of {@code matrix}. Thread-safe (per-thread scratch); does not allocate after the first call on a
     * thread.
     *
     * @param matrix a matrix from {@code rotationMatrixL3}, at least {@link #L3_MATRIX_SIZE} elements
     * @param in the coefficients to rotate
     * @param dest receives the rotated coefficients; may be {@code in}
     * @return {@code dest}
     */
    public static ShCoeffs16d applyL3(double[] matrix, ShCoeffs16d in, ShCoeffs16d dest) {
        applyBands(matrix, in.c, dest.c, 3);
        return dest;
    }

    private static void applyBands(float[] m, float[] in, float[] out, int bands) {
        float[] t = SCRATCH_F.get()[1];
        out[0] = in[0];
        out[1] = in[1];
        out[2] = in[2];
        int offset = 0;
        for (int l = 1; l <= bands; l++) {
            int n = 2 * l + 1;
            int base = 3 * l * l;
            // the whole band is computed before it is written, so dest may alias in
            for (int i = 0; i < n; i++) {
                float sr = 0.0f, sg = 0.0f, sb = 0.0f;
                for (int j = 0; j < n; j++) {
                    float w = m[offset + i * n + j];
                    sr += w * in[base + 3 * j];
                    sg += w * in[base + 3 * j + 1];
                    sb += w * in[base + 3 * j + 2];
                }
                t[3 * i] = sr;
                t[3 * i + 1] = sg;
                t[3 * i + 2] = sb;
            }
            System.arraycopy(t, 0, out, base, 3 * n);
            offset += n * n;
        }
    }

    private static void applyBands(double[] m, double[] in, double[] out, int bands) {
        double[] t = SCRATCH.get()[4];
        out[0] = in[0];
        out[1] = in[1];
        out[2] = in[2];
        int offset = 0;
        for (int l = 1; l <= bands; l++) {
            int n = 2 * l + 1;
            int base = 3 * l * l;
            for (int i = 0; i < n; i++) {
                double sr = 0.0, sg = 0.0, sb = 0.0;
                for (int j = 0; j < n; j++) {
                    double w = m[offset + i * n + j];
                    sr += w * in[base + 3 * j];
                    sg += w * in[base + 3 * j + 1];
                    sb += w * in[base + 3 * j + 2];
                }
                t[3 * i] = sr;
                t[3 * i + 1] = sg;
                t[3 * i + 2] = sb;
            }
            System.arraycopy(t, 0, out, base, 3 * n);
            offset += n * n;
        }
    }

    /**
     * Rotates L2 RGB coefficients by {@code rotation}: builds the SH rotation matrix and applies it. To rotate
     * many coefficient sets by one rotation, build the matrix once with {@code rotationMatrixL2} instead.
     * Thread-safe (per-thread scratch); does not allocate after the first call on a thread.
     *
     * @param in the coefficients to rotate
     * @param rotation a proper rotation (orthonormal, determinant 1)
     * @param dest receives the rotated coefficients; may be {@code in}
     * @return {@code dest}
     */
    public static ShCoeffs9f rotateL2(ShCoeffs9f in, Matrix3fc rotation, ShCoeffs9f dest) {
        return applyL2(rotationMatrixL2(rotation, SCRATCH_F.get()[0]), in, dest);
    }

    /**
     * Rotates L3 RGB coefficients by {@code rotation}: builds the SH rotation matrix and applies it. To rotate
     * many coefficient sets by one rotation, build the matrix once with {@code rotationMatrixL3} instead.
     * Thread-safe (per-thread scratch); does not allocate after the first call on a thread.
     *
     * @param in the coefficients to rotate
     * @param rotation a proper rotation (orthonormal, determinant 1)
     * @param dest receives the rotated coefficients; may be {@code in}
     * @return {@code dest}
     */
    public static ShCoeffs16f rotateL3(ShCoeffs16f in, Matrix3fc rotation, ShCoeffs16f dest) {
        return applyL3(rotationMatrixL3(rotation, SCRATCH_F.get()[0]), in, dest);
    }

    /**
     * Rotates L2 RGB coefficients by {@code rotation}: builds the SH rotation matrix and applies it. To rotate
     * many coefficient sets by one rotation, build the matrix once with {@code rotationMatrixL2} instead.
     * Thread-safe (per-thread scratch); does not allocate after the first call on a thread.
     *
     * @param in the coefficients to rotate
     * @param rotation a proper rotation (orthonormal, determinant 1)
     * @param dest receives the rotated coefficients; may be {@code in}
     * @return {@code dest}
     */
    public static ShCoeffs9d rotateL2(ShCoeffs9d in, Matrix3dc rotation, ShCoeffs9d dest) {
        return applyL2(rotationMatrixL2(rotation, SCRATCH.get()[2]), in, dest);
    }

    /**
     * Rotates L3 RGB coefficients by {@code rotation}: builds the SH rotation matrix and applies it. To rotate
     * many coefficient sets by one rotation, build the matrix once with {@code rotationMatrixL3} instead.
     * Thread-safe (per-thread scratch); does not allocate after the first call on a thread.
     *
     * @param in the coefficients to rotate
     * @param rotation a proper rotation (orthonormal, determinant 1)
     * @param dest receives the rotated coefficients; may be {@code in}
     * @return {@code dest}
     */
    public static ShCoeffs16d rotateL3(ShCoeffs16d in, Matrix3dc rotation, ShCoeffs16d dest) {
        return applyL3(rotationMatrixL3(rotation, SCRATCH.get()[2]), in, dest);
    }
}
