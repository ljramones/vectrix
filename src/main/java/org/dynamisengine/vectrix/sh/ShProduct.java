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

/**
 * Products of SH-projected functions: the projection of {@code f · g} back onto the same bands, through the triple
 * product (Gaunt) coefficients {@code G_ijk = ∫ Y_i Y_j Y_k dω} of {@link ShBasis}. The product of two L2 functions
 * has content up to band 4, so the result is its best approximation within L2 (or L3), not the exact product.
 * <p>
 * The coefficients are computed once at class initialisation by exact polynomial integration over the sphere
 * ({@code ∫ x^a y^b z^c dω = 4π (a-1)!! (b-1)!! (c-1)!! / (a+b+c+1)!!} for even exponents), using only arithmetic,
 * so they are identical on every platform. Typical uses: lighting times visibility (a scalar function, see
 * {@link #multiplyScalarL2}), or two lighting terms channel by channel.
 *
 * @since 2.3.0
 */
public final class ShProduct {
    /** Nonzero Gaunt entries for indices below 16: i, j, k packed as i | j << 4 | k << 8, and the values. */
    private static final int[] IDX;
    private static final double[] VAL;
    private static final float[] VAL_F;
    /** Number of entries with all three indices below 9 (they come first). */
    private static final int COUNT_L2;

    /** Monomials of each basis function: coefficient, then exponents of x, y, z, per term. */
    private static final double[][] POLY = new double[16][];

    static {
        double[] d = {ShBasis.D0, ShBasis.D1, ShBasis.D2, ShBasis.D3, ShBasis.D4, ShBasis.D5, ShBasis.D6, ShBasis.D7,
            ShBasis.D8, ShBasis.D9};
        POLY[0] = new double[] {d[0], 0, 0, 0};
        POLY[1] = new double[] {d[1], 0, 1, 0};
        POLY[2] = new double[] {d[1], 0, 0, 1};
        POLY[3] = new double[] {d[1], 1, 0, 0};
        POLY[4] = new double[] {d[2], 1, 1, 0};
        POLY[5] = new double[] {d[2], 0, 1, 1};
        POLY[6] = new double[] {3 * d[3], 0, 0, 2, -d[3], 0, 0, 0};
        POLY[7] = new double[] {d[2], 1, 0, 1};
        POLY[8] = new double[] {d[4], 2, 0, 0, -d[4], 0, 2, 0};
        POLY[9] = new double[] {3 * d[5], 2, 1, 0, -d[5], 0, 3, 0};
        POLY[10] = new double[] {d[6], 1, 1, 1};
        POLY[11] = new double[] {5 * d[7], 0, 1, 2, -d[7], 0, 1, 0};
        POLY[12] = new double[] {5 * d[8], 0, 0, 3, -3 * d[8], 0, 0, 1};
        POLY[13] = new double[] {5 * d[7], 1, 0, 2, -d[7], 1, 0, 0};
        POLY[14] = new double[] {d[9], 2, 0, 1, -d[9], 0, 2, 1};
        POLY[15] = new double[] {d[5], 3, 0, 0, -3 * d[5], 1, 2, 0};
        int[] idx = new int[4096];
        double[] val = new double[4096];
        int n = 0;
        int l2Count = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 16; j++) {
                    for (int k = 0; k < 16; k++) {
                        boolean l2 = i < 9 && j < 9 && k < 9;
                        if (l2 != (pass == 0)) {
                            continue;
                        }
                        double g = triple(POLY[i], POLY[j], POLY[k]);
                        if (java.lang.Math.abs(g) > 1e-12) {
                            idx[n] = i | j << 4 | k << 8;
                            val[n] = g;
                            n++;
                        }
                    }
                }
            }
            if (pass == 0) {
                l2Count = n;
            }
        }
        COUNT_L2 = l2Count;
        IDX = java.util.Arrays.copyOf(idx, n);
        VAL = java.util.Arrays.copyOf(val, n);
        VAL_F = new float[n];
        for (int i = 0; i < n; i++) {
            VAL_F[i] = (float) VAL[i];
        }
    }

    private ShProduct() {
    }

    private static double doubleFactorial(int n) {
        double r = 1.0;
        for (int i = n; i > 1; i -= 2) {
            r *= i;
        }
        return r;
    }

    /** ∫ x^a y^b z^c over the unit sphere. */
    private static double monomialIntegral(int a, int b, int c) {
        if ((a & 1) != 0 || (b & 1) != 0 || (c & 1) != 0) {
            return 0.0;
        }
        return 4.0 * java.lang.Math.PI * doubleFactorial(a - 1) * doubleFactorial(b - 1) * doubleFactorial(c - 1)
                / doubleFactorial(a + b + c + 1);
    }

    private static double triple(double[] p, double[] q, double[] r) {
        double sum = 0.0;
        for (int i = 0; i < p.length; i += 4) {
            for (int j = 0; j < q.length; j += 4) {
                for (int k = 0; k < r.length; k += 4) {
                    sum += p[i] * q[j] * r[k] * monomialIntegral((int) (p[i + 1] + q[j + 1] + r[k + 1]),
                            (int) (p[i + 2] + q[j + 2] + r[k + 2]), (int) (p[i + 3] + q[j + 3] + r[k + 3]));
                }
            }
        }
        return sum;
    }

    /** The Gaunt coefficient {@code ∫ Y_i Y_j Y_k dω} for indices below 16. */
    public static double gaunt(int i, int j, int k) {
        return triple(POLY[i], POLY[j], POLY[k]);
    }

    private static final ThreadLocal<float[]> SCRATCH_F = ThreadLocal.withInitial(() -> new float[48]);
    private static final ThreadLocal<double[]> SCRATCH = ThreadLocal.withInitial(() -> new double[48]);

    private static void rgb(float[] a, float[] b, float[] out, int count, int size) {
        float[] t = SCRATCH_F.get();
        java.util.Arrays.fill(t, 0, 3 * size, 0.0f);
        for (int e = 0; e < count; e++) {
            int p = IDX[e];
            int i = 3 * (p & 15), j = 3 * (p >> 4 & 15), k = 3 * (p >> 8);
            float g = VAL_F[e];
            t[k] += g * a[i] * b[j];
            t[k + 1] += g * a[i + 1] * b[j + 1];
            t[k + 2] += g * a[i + 2] * b[j + 2];
        }
        System.arraycopy(t, 0, out, 0, 3 * size);
    }

    private static void rgb(double[] a, double[] b, double[] out, int count, int size) {
        double[] t = SCRATCH.get();
        java.util.Arrays.fill(t, 0, 3 * size, 0.0);
        for (int e = 0; e < count; e++) {
            int p = IDX[e];
            int i = 3 * (p & 15), j = 3 * (p >> 4 & 15), k = 3 * (p >> 8);
            double g = VAL[e];
            t[k] += g * a[i] * b[j];
            t[k + 1] += g * a[i + 1] * b[j + 1];
            t[k + 2] += g * a[i + 2] * b[j + 2];
        }
        System.arraycopy(t, 0, out, 0, 3 * size);
    }

    private static void scalar(float[] a, float[] s, float[] out, int count, int size) {
        float[] t = SCRATCH_F.get();
        java.util.Arrays.fill(t, 0, 3 * size, 0.0f);
        for (int e = 0; e < count; e++) {
            int p = IDX[e];
            int i = 3 * (p & 15), j = p >> 4 & 15, k = 3 * (p >> 8);
            float g = VAL_F[e] * s[j];
            t[k] += g * a[i];
            t[k + 1] += g * a[i + 1];
            t[k + 2] += g * a[i + 2];
        }
        System.arraycopy(t, 0, out, 0, 3 * size);
    }

    private static void scalar(double[] a, double[] s, double[] out, int count, int size) {
        double[] t = SCRATCH.get();
        java.util.Arrays.fill(t, 0, 3 * size, 0.0);
        for (int e = 0; e < count; e++) {
            int p = IDX[e];
            int i = 3 * (p & 15), j = p >> 4 & 15, k = 3 * (p >> 8);
            double g = VAL[e] * s[j];
            t[k] += g * a[i];
            t[k + 1] += g * a[i + 1];
            t[k + 2] += g * a[i + 2];
        }
        System.arraycopy(t, 0, out, 0, 3 * size);
    }

    /** Channel-wise product of two L2 RGB functions, projected to L2; {@code dest} may alias either input. */
    public static ShCoeffs9f multiplyL2(ShCoeffs9f a, ShCoeffs9f b, ShCoeffs9f dest) {
        rgb(a.c, b.c, dest.c, COUNT_L2, 9);
        return dest;
    }

    /** Channel-wise product of two L3 RGB functions, projected to L3; {@code dest} may alias either input. */
    public static ShCoeffs16f multiplyL3(ShCoeffs16f a, ShCoeffs16f b, ShCoeffs16f dest) {
        rgb(a.c, b.c, dest.c, IDX.length, 16);
        return dest;
    }

    /** Channel-wise product (double), projected to L2. */
    public static ShCoeffs9d multiplyL2(ShCoeffs9d a, ShCoeffs9d b, ShCoeffs9d dest) {
        rgb(a.c, b.c, dest.c, COUNT_L2, 9);
        return dest;
    }

    /** Channel-wise product (double), projected to L3. */
    public static ShCoeffs16d multiplyL3(ShCoeffs16d a, ShCoeffs16d b, ShCoeffs16d dest) {
        rgb(a.c, b.c, dest.c, IDX.length, 16);
        return dest;
    }

    /**
     * Product of an L2 RGB function with a scalar L2 function {@code s} (9 coefficients, e.g. visibility),
     * projected to L2; {@code dest} may alias {@code a}.
     */
    public static ShCoeffs9f multiplyScalarL2(ShCoeffs9f a, float[] s9, ShCoeffs9f dest) {
        scalar(a.c, s9, dest.c, COUNT_L2, 9);
        return dest;
    }

    /** Product of an L3 RGB function with a scalar L3 function (16 coefficients), projected to L3. */
    public static ShCoeffs16f multiplyScalarL3(ShCoeffs16f a, float[] s16, ShCoeffs16f dest) {
        scalar(a.c, s16, dest.c, IDX.length, 16);
        return dest;
    }

    /** Double-precision {@link #multiplyScalarL2(ShCoeffs9f, float[], ShCoeffs9f)}. */
    public static ShCoeffs9d multiplyScalarL2(ShCoeffs9d a, double[] s9, ShCoeffs9d dest) {
        scalar(a.c, s9, dest.c, COUNT_L2, 9);
        return dest;
    }

    /** Double-precision {@link #multiplyScalarL3(ShCoeffs16f, float[], ShCoeffs16f)}. */
    public static ShCoeffs16d multiplyScalarL3(ShCoeffs16d a, double[] s16, ShCoeffs16d dest) {
        scalar(a.c, s16, dest.c, IDX.length, 16);
        return dest;
    }
}
