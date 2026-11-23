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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.blas.Blasd;
import org.dynamisengine.vectrix.blas.Blasf;
import org.dynamisengine.vectrix.experimental.KernelConfig;
import org.junit.jupiter.api.Test;

/** BLAS-1 (2.5.0): the documented reduction order, exactness bounds, SIMD/segment identity, overflow safety. */
class BlasTest {

    private static final int[] SIZES = {0, 1, 2, 7, 8, 9, 15, 16, 255, 256, 257, 511, 512, 513, 1000, 4096, 100_003};

    /** The documented order, written plainly: the reference the kernels must match bit for bit. */
    static float specDot(float[] x, float[] y, int lo, int hi) {
        if (hi - lo <= 256) {
            float[] a = new float[8];
            for (int i = lo; i < hi; i++) {
                int l = (i - lo) & 7;
                a[l] = java.lang.Math.fma(x[i], y[i], a[l]);
            }
            return ((a[0] + a[4]) + (a[2] + a[6])) + ((a[1] + a[5]) + (a[3] + a[7]));
        }
        int blocks = (hi - lo + 255) / 256;
        int mid = lo + (blocks + 1) / 2 * 256;
        return specDot(x, y, lo, mid) + specDot(x, y, mid, hi);
    }

    static double specSum(double[] x, int lo, int hi) {
        if (hi - lo <= 256) {
            double[] a = new double[8];
            for (int i = lo; i < hi; i++) {
                a[(i - lo) & 7] += x[i];
            }
            return ((a[0] + a[4]) + (a[2] + a[6])) + ((a[1] + a[5]) + (a[3] + a[7]));
        }
        int blocks = (hi - lo + 255) / 256;
        int mid = lo + (blocks + 1) / 2 * 256;
        return specSum(x, lo, mid) + specSum(x, mid, hi);
    }

    private static float[] floats(SplittableRandom r, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = (float) r.nextDouble(-1, 1);
        }
        return a;
    }

    @Test
    void reductionsFollowTheDocumentedOrderOnEveryPath() {
        SplittableRandom r = new SplittableRandom(1);
        boolean was = KernelConfig.simdEnabled();
        try {
            for (int n : SIZES) {
                float[] x = floats(r, n);
                float[] y = floats(r, n);
                double[] xd = new double[n];
                for (int i = 0; i < n; i++) {
                    xd[i] = x[i] * 1.000000123;
                }
                float want = n == 0 ? 0f : specDot(x, y, 0, n);
                double wantSum = n == 0 ? 0 : specSum(xd, 0, n);
                MemorySegment sx = Arena.ofAuto().allocate(4L * n + 4).asSlice(4); // unaligned for vectors
                MemorySegment sy = Arena.ofAuto().allocate(4L * n + 4);
                MemorySegment sd = Arena.ofAuto().allocate(8L * n + 8);
                MemorySegment.copy(x, 0, sx, ValueLayout.JAVA_FLOAT_UNALIGNED, 0, n);
                MemorySegment.copy(y, 0, sy, ValueLayout.JAVA_FLOAT_UNALIGNED, 4, n);
                MemorySegment.copy(xd, 0, sd, ValueLayout.JAVA_DOUBLE_UNALIGNED, 8, n);
                for (int mode = 0; mode < 2; mode++) {
                    KernelConfig.setSimdEnabled(mode == 1);
                    assertEquals(Float.floatToRawIntBits(want), Float.floatToRawIntBits(Blasf.dot(x, 0, y, 0, n)),
                        "dot n " + n + " simd " + mode);
                    assertEquals(Float.floatToRawIntBits(want), Float.floatToRawIntBits(Blasf.dot(sx, 0, sy, 1, n)),
                        "segment dot n " + n);
                    assertEquals(Double.doubleToRawLongBits(wantSum), Double.doubleToRawLongBits(Blasd.sum(xd, 0, n)),
                        "sum n " + n + " simd " + mode);
                    assertEquals(Double.doubleToRawLongBits(wantSum), Double.doubleToRawLongBits(Blasd.sum(sd, 1, n)),
                        "segment sum n " + n);
                }
            }
        } finally {
            KernelConfig.setSimdEnabled(was);
        }
    }

    @Test
    void reductionsAreWithinTheirErrorBound() {
        SplittableRandom r = new SplittableRandom(2);
        for (int n : new int[] {1000, 65536, 1_000_000}) {
            double[] x = new double[n];
            double[] y = new double[n];
            for (int i = 0; i < n; i++) {
                x[i] = r.nextDouble(-1, 1) * java.lang.Math.pow(2, r.nextInt(-20, 20));
                y[i] = r.nextDouble(-1, 1);
            }
            BigDecimal exact = BigDecimal.ZERO;
            BigDecimal abs = BigDecimal.ZERO;
            for (int i = 0; i < n; i++) {
                BigDecimal p = new BigDecimal(x[i]).multiply(new BigDecimal(y[i]));
                exact = exact.add(p);
                abs = abs.add(p.abs());
            }
            double bound = (32 + java.lang.Math.log(n / 256.0 + 1) / java.lang.Math.log(2)) * 0x1p-53
                * abs.doubleValue();
            double err = java.lang.Math.abs(Blasd.dot(x, 0, y, 0, n) - exact.round(MathContext.DECIMAL128).doubleValue());
            assertTrue(err <= bound, "n " + n + ": " + err + " > " + bound);
        }
    }

    @Test
    void nrm2NeitherOverflowsNorUnderflows() {
        float[] big = {3e30f, 4e30f};
        assertEquals(5e30f, Blasf.nrm2(big, 0, 2), 1e24f);
        float[] tiny = {3e-30f, 4e-30f};
        assertEquals(5e-30f, Blasf.nrm2(tiny, 0, 2), 1e-36f);
        double[] huge = {3e200, 4e200, 0};
        assertEquals(5e200, Blasd.nrm2(huge, 0, 3), 1e185);
        assertEquals(5.0, Blasd.nrm2(new double[] {3, 4}, 0, 2), 0.0);
        assertEquals(Float.POSITIVE_INFINITY, Blasf.nrm2(new float[] {1, Float.POSITIVE_INFINITY}, 0, 2), 0f);
        assertTrue(Double.isNaN(Blasd.nrm2(new double[] {1, Double.NaN}, 0, 2)));
        assertEquals(0.0, Blasd.nrm2(new double[3], 0, 3), 0.0);
    }

    @Test
    void elementwiseKernelsRoundOnce() {
        SplittableRandom r = new SplittableRandom(3);
        boolean was = KernelConfig.simdEnabled();
        try {
            for (int n : new int[] {0, 1, 5, 64, 1001}) {
                float[] x = floats(r, n);
                float[] y = floats(r, n);
                for (int mode = 0; mode < 2; mode++) {
                    KernelConfig.setSimdEnabled(mode == 1);
                    float[] z = y.clone();
                    Blasf.axpy(0.37f, x, 0, z, 0, n);
                    float[] s = x.clone();
                    Blasf.scal(-1.25f, s, 0, n);
                    for (int i = 0; i < n; i++) {
                        assertEquals(java.lang.Math.fma(0.37f, x[i], y[i]), z[i], 0f);
                        assertEquals(-1.25f * x[i], s[i], 0f);
                    }
                }
                if (n > 0) {
                    x[n / 2] = 9f;
                    x[n - 1] = -9f;
                    assertEquals(n / 2, Blasf.iamax(x, 0, n), "first of the largest");
                }
            }
            assertEquals(-1, Blasd.iamax(new double[0], 0, 0));
        } finally {
            KernelConfig.setSimdEnabled(was);
        }
    }
}
