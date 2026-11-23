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
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.blas.Blasd;
import org.dynamisengine.vectrix.blas.Blasf;
import org.dynamisengine.vectrix.blas.Gemmd;
import org.dynamisengine.vectrix.blas.Gemmf;
import org.dynamisengine.vectrix.experimental.KernelConfig;
import org.junit.jupiter.api.Test;

/** GEMM/GEMV (2.5.0): every element is the documented dot product, on every path and layout. */
class GemmTest {

    private static final int[][] SHAPES = {{1, 1, 1}, {5, 3, 7}, {8, 4, 8}, {9, 7, 33}, {17, 13, 256}, {6, 66, 300},
        {67, 5, 513}, {130, 70, 64}, {4, 2, 1000}};

    private static float[] floats(SplittableRandom r, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = (float) r.nextDouble(-1, 1);
        }
        return a;
    }

    @Test
    void floatGemmIsTheDotProductOfRowsAndColumns() {
        SplittableRandom r = new SplittableRandom(1);
        boolean was = KernelConfig.simdEnabled();
        try {
            for (int[] sh : SHAPES) {
                int m = sh[0];
                int n = sh[1];
                int k = sh[2];
                for (int t = 0; t < 4; t++) {
                    boolean ta = (t & 1) != 0;
                    boolean tb = (t & 2) != 0;
                    int lda = (ta ? m : k) + 3;
                    int ldb = (tb ? k : n) + 1;
                    int ldc = n + 2;
                    float[] a = floats(r, (ta ? k : m) * lda + 5);
                    float[] b = floats(r, (tb ? n : k) * ldb + 5);
                    float[] c0 = floats(r, m * ldc + 4);
                    for (int mode = 0; mode < 2; mode++) {
                        KernelConfig.setSimdEnabled(mode == 1);
                        for (int ab = 0; ab < 2; ab++) {
                            float alpha = ab == 0 ? 1f : -0.75f;
                            float beta = ab == 0 ? 0f : 0.5f;
                            float[] c = c0.clone();
                            if (beta == 0) {
                                java.util.Arrays.fill(c, Float.NaN); // must not be read
                            }
                            float[] s = new float[Gemmf.scratchSize(ta, tb, m, n, k) + 2];
                            Gemmf.gemm(ta, tb, m, n, k, alpha, a, 2, lda, b, 1, ldb, beta, c, 3, ldc, s, 2);
                            float[] row = new float[k];
                            float[] col = new float[k];
                            for (int i = 0; i < m; i++) {
                                for (int kk = 0; kk < k; kk++) {
                                    row[kk] = ta ? a[2 + kk * lda + i] : a[2 + i * lda + kk];
                                }
                                for (int j = 0; j < n; j++) {
                                    for (int kk = 0; kk < k; kk++) {
                                        col[kk] = tb ? b[1 + j * ldb + kk] : b[1 + kk * ldb + j];
                                    }
                                    float d = Blasf.dot(row, 0, col, 0, k);
                                    float want = beta == 0 ? alpha * d
                                        : java.lang.Math.fma(alpha, d, beta * c0[3 + i * ldc + j]);
                                    assertEquals(Float.floatToRawIntBits(want),
                                        Float.floatToRawIntBits(c[3 + i * ldc + j]),
                                        m + "x" + n + "x" + k + " ta " + ta + " tb " + tb + " simd " + mode + " at "
                                            + i + "," + j);
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            KernelConfig.setSimdEnabled(was);
        }
    }

    @Test
    void doubleGemmAndGemvAgreeWithTheDotProduct() {
        SplittableRandom r = new SplittableRandom(2);
        boolean was = KernelConfig.simdEnabled();
        try {
            int m = 23;
            int n = 11;
            int k = 777;
            double[] a = new double[m * k];
            double[] b = new double[n * k]; // B transposed: n rows of k
            for (int i = 0; i < a.length; i++) {
                a[i] = r.nextDouble(-1, 1);
            }
            for (int i = 0; i < b.length; i++) {
                b[i] = r.nextDouble(-1, 1);
            }
            for (int mode = 0; mode < 2; mode++) {
                KernelConfig.setSimdEnabled(mode == 1);
                double[] c = new double[m * n];
                Gemmd.gemm(false, true, m, n, k, 1.0, a, 0, k, b, 0, k, 0.0, c, 0, n,
                    new double[Gemmd.scratchSize(false, true, m, n, k)], 0);
                for (int j = 0; j < n; j++) {
                    double[] y = new double[m];
                    Gemmd.gemv(false, m, k, 1.0, a, 0, k, b, j * k, 0.0, y, 0, null, 0);
                    for (int i = 0; i < m; i++) {
                        double d = Blasd.dot(a, i * k, b, j * k, k);
                        assertEquals(Double.doubleToRawLongBits(d), Double.doubleToRawLongBits(c[i * n + j]));
                        assertEquals(Double.doubleToRawLongBits(d), Double.doubleToRawLongBits(y[i]), "gemv");
                    }
                }
                // transposed gemv: y = Aᵀ·x
                double[] x = new double[m];
                for (int i = 0; i < m; i++) {
                    x[i] = r.nextDouble(-1, 1);
                }
                double[] y = new double[k];
                Gemmd.gemv(true, k, m, 2.0, a, 0, k, x, 0, 0.0, y, 0, new double[m], 0);
                double[] colA = new double[m];
                for (int j = 0; j < k; j++) {
                    for (int i = 0; i < m; i++) {
                        colA[i] = a[i * k + j];
                    }
                    assertEquals(2.0 * Blasd.dot(colA, 0, x, 0, m), y[j], 0.0);
                }
            }
        } finally {
            KernelConfig.setSimdEnabled(was);
        }
        assertThrows(IllegalArgumentException.class,
            () -> Gemmf.gemm(false, false, 2, 2, 2, 1, new float[3], 0, 2, new float[4], 0, 2, 0, new float[4], 0, 2,
                new float[Gemmf.scratchSize(false, false, 2, 2, 2)], 0));
    }
}
