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
package org.dynamisengine.vectrix.nn;

//#ifdef __HAS_VECTOR_API__
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorShuffle;
import jdk.incubator.vector.VectorSpecies;
import org.dynamisengine.vectrix.simd.SimdSupport;
//#endif

/**
 * Low-precision number formats for weights and activations: bfloat16, the two OCP 8-bit floats, and int8 block
 * quantization with a quantized dot product.
 * <p>
 * Every conversion rounds to nearest, ties to even. FP8 E4M3 is the OCP "FN" variant: finite only (max 448), with
 * {@code S.1111.111} as NaN; overflow gives NaN, or 448 when saturating. FP8 E5M2 is IEEE-like (max 57344, with
 * infinities); overflow gives infinity, or 57344 when saturating. The FP8 encoders search the exact value set, so
 * they are correct by construction for every float, subnormals included. Nothing allocates.
 *
 * @since 2.5.0
 */
public final class Formats {

    /**
     * The int8 quantization block length: each block of 32 elements shares one scale.
     */
    public static final int QBLOCK = 32;

    private static final float[] E4M3 = table(4, 3, 7, true);
    private static final float[] E5M2 = table(5, 2, 15, false);
    /** Positive finite values, ascending, with their codes: for the nearest-value search. */
    private static final float[] E4M3_POS = positives(E4M3);
    private static final float[] E5M2_POS = positives(E5M2);

    private Formats() {
    }

    /**
     * Converts a float to bfloat16 bits, rounding to nearest, ties to even. A NaN stays a NaN with the quiet bit
     * set; values that round past the largest bfloat16 become infinity.
     *
     * @param x the value
     * @return the bfloat16 bit pattern (the upper 16 bits of the rounded float)
     */
    public static short toBf16(float x) {
        int b = Float.floatToRawIntBits(x);
        if (x != x) {
            return (short) ((b >>> 16) | 0x40);
        }
        return (short) ((b + 0x7fff + ((b >>> 16) & 1)) >>> 16);
    }

    /**
     * Converts bfloat16 bits to a float, exactly.
     *
     * @param h the bfloat16 bit pattern
     * @return the float value
     */
    public static float fromBf16(short h) {
        return Float.intBitsToFloat((h & 0xffff) << 16);
    }

    /**
     * Converts a float to an FP8 E4M3 code (the OCP "FN" variant), rounding to nearest, ties to even.
     *
     * @param x the value
     * @param saturate whether values beyond the range give ±448 instead of NaN
     * @return the code; NaN gives {@code 0x7f}
     */
    public static byte toE4M3(float x, boolean saturate) {
        return encode(x, E4M3_POS, true, saturate);
    }

    /**
     * Converts an FP8 E4M3 code to a float, exactly.
     *
     * @param b the code
     * @return the value; {@code 0x7f} and {@code 0xff} give NaN
     */
    public static float fromE4M3(byte b) {
        return E4M3[b & 0xff];
    }

    /**
     * Converts a float to an FP8 E5M2 code, rounding to nearest, ties to even.
     *
     * @param x the value
     * @param saturate whether values beyond the range give ±57344 instead of ±infinity
     * @return the code; NaN gives {@code 0x7f}
     */
    public static byte toE5M2(float x, boolean saturate) {
        return encode(x, E5M2_POS, false, saturate);
    }

    /**
     * Converts an FP8 E5M2 code to a float, exactly.
     *
     * @param b the code
     * @return the value, which may be infinite or NaN
     */
    public static float fromE5M2(byte b) {
        return E5M2[b & 0xff];
    }

    /**
     * Quantizes {@code n} floats ({@code n} a multiple of {@link #QBLOCK}) to int8 by blocks of 32: each block's scale
     * is {@code max|x| / 127} and {@code q = rint(x / scale)}, so the largest element maps to ±127 exactly. An all-zero
     * block gets scale 0 and codes 0; a block containing NaN gets a NaN scale and codes 0.
     *
     * @param x the values
     * @param xOff the offset in {@code x}
     * @param n the number of values, a non-negative multiple of 32
     * @param q receives {@code n} codes in [-127, 127]
     * @param qOff the offset in {@code q}
     * @param scales receives {@code n / 32} scales
     * @param sOff the offset in {@code scales}
     * @throws IllegalArgumentException if {@code n} is not a non-negative multiple of 32 or an array is too short for
     *         its offset; nothing is written then
     */
    public static void quantizeInt8(float[] x, int xOff, int n, byte[] q, int qOff, float[] scales, int sOff) {
        if (n < 0 || n % QBLOCK != 0) {
            throw new IllegalArgumentException("n must be a multiple of " + QBLOCK + ": " + n);
        }
        range(x == null ? -1 : x.length, xOff, n, "x");
        range(q == null ? -1 : q.length, qOff, n, "q");
        range(scales == null ? -1 : scales.length, sOff, n / QBLOCK, "scales");
        for (int b = 0; b < n / QBLOCK; b++) {
            int base = b * QBLOCK;
            float amax = 0f;
            for (int j = 0; j < QBLOCK; j++) {
                amax = Math.max(amax, Math.abs(x[xOff + base + j]));
            }
            float scale = amax / 127f;
            scales[sOff + b] = scale;
            for (int j = 0; j < QBLOCK; j++) {
                float v = scale == 0f ? 0f : (float) Math.rint(x[xOff + base + j] / scale);
                q[qOff + base + j] = (byte) Math.max(-127f, Math.min(127f, v));
            }
        }
    }

    /**
     * Dequantizes int8 blocks: {@code x[i] = q[i]·scale[i / 32]}.
     *
     * @param q the codes
     * @param qOff the offset in {@code q}
     * @param scales the {@code n / 32} block scales
     * @param sOff the offset in {@code scales}
     * @param x receives {@code n} values
     * @param xOff the offset in {@code x}
     * @param n the number of values, a non-negative multiple of 32
     * @throws IllegalArgumentException if {@code n} is not a non-negative multiple of 32 or an array is too short for
     *         its offset; nothing is written then
     */
    public static void dequantizeInt8(byte[] q, int qOff, float[] scales, int sOff, float[] x, int xOff, int n) {
        if (n < 0 || n % QBLOCK != 0) {
            throw new IllegalArgumentException("n must be a multiple of " + QBLOCK + ": " + n);
        }
        range(q == null ? -1 : q.length, qOff, n, "q");
        range(scales == null ? -1 : scales.length, sOff, n / QBLOCK, "scales");
        range(x == null ? -1 : x.length, xOff, n, "x");
        for (int i = 0; i < n; i++) {
            x[xOff + i] = q[qOff + i] * scales[sOff + i / QBLOCK];
        }
    }

    /**
     * The dot product of int8 block-quantized weights with floats, without dequantizing. Lane {@code l} (0..7) takes
     * the elements whose index in their block is {@code l mod 8}: within a block it sums {@code q·x} with fused
     * multiply-adds in order, and across blocks it accumulates {@code T_l = fma(scale, blockPart_l, T_l)} in block
     * order; the result is {@code ((T0 + T4) + (T2 + T6)) + ((T1 + T5) + (T3 + T7))}, the lane order of
     * {@code Blasf}. The scalar and Vector API paths follow this order exactly, so results are the same bits
     * everywhere; the error is about {@code (7 + n/32)·ε·Σ|terms|}.
     * <p>
     * Widening the codes to float limits the speed: on an Apple M4 Max with the Vector API a 4096×4096
     * {@link #gemvInt8} takes 1.8 ms against 1.05 ms for the float {@code Gemmf.gemv}. Int8 saves memory (a quarter of
     * float), not time, unless the weights would otherwise not fit in memory bandwidth or cache.
     *
     * @param q the codes
     * @param qOff the offset in {@code q}
     * @param scales the {@code n / 32} block scales
     * @param sOff the offset in {@code scales}
     * @param x the float vector
     * @param xOff the offset in {@code x}
     * @param n the number of elements, a non-negative multiple of 32
     * @return the dot product
     * @throws IllegalArgumentException if {@code n} is not a non-negative multiple of 32 or an array is too short for
     *         its offset
     */
    public static float dotInt8(byte[] q, int qOff, float[] scales, int sOff, float[] x, int xOff, int n) {
        if (n < 0 || n % QBLOCK != 0) {
            throw new IllegalArgumentException("n must be a multiple of " + QBLOCK + ": " + n);
        }
        range(q == null ? -1 : q.length, qOff, n, "q");
        range(scales == null ? -1 : scales.length, sOff, n / QBLOCK, "scales");
        range(x == null ? -1 : x.length, xOff, n, "x");
//#ifdef __HAS_VECTOR_API__
        if (Simd.on()) {
            return Simd.dotInt8(q, qOff, scales, sOff, x, xOff, n);
        }
//#endif
        float t0 = 0f;
        float t1 = 0f;
        float t2 = 0f;
        float t3 = 0f;
        float t4 = 0f;
        float t5 = 0f;
        float t6 = 0f;
        float t7 = 0f;
        for (int b = 0; b < n / QBLOCK; b++) {
            int base = b * QBLOCK;
            float a0 = 0f;
            float a1 = 0f;
            float a2 = 0f;
            float a3 = 0f;
            float a4 = 0f;
            float a5 = 0f;
            float a6 = 0f;
            float a7 = 0f;
            for (int j = 0; j < QBLOCK; j += 8) {
                int qi = qOff + base + j;
                int xi = xOff + base + j;
                a0 = Math.fma(q[qi], x[xi], a0);
                a1 = Math.fma(q[qi + 1], x[xi + 1], a1);
                a2 = Math.fma(q[qi + 2], x[xi + 2], a2);
                a3 = Math.fma(q[qi + 3], x[xi + 3], a3);
                a4 = Math.fma(q[qi + 4], x[xi + 4], a4);
                a5 = Math.fma(q[qi + 5], x[xi + 5], a5);
                a6 = Math.fma(q[qi + 6], x[xi + 6], a6);
                a7 = Math.fma(q[qi + 7], x[xi + 7], a7);
            }
            float sc = scales[sOff + b];
            t0 = Math.fma(sc, a0, t0);
            t1 = Math.fma(sc, a1, t1);
            t2 = Math.fma(sc, a2, t2);
            t3 = Math.fma(sc, a3, t3);
            t4 = Math.fma(sc, a4, t4);
            t5 = Math.fma(sc, a5, t5);
            t6 = Math.fma(sc, a6, t6);
            t7 = Math.fma(sc, a7, t7);
        }
        return ((t0 + t4) + (t2 + t6)) + ((t1 + t5) + (t3 + t7));
    }

    /**
     * {@code y = W·x} for an {@code m×n} int8 block-quantized row-major {@code W} (row {@code i} at codes
     * {@code q[qOff + i·n ..]} and scales {@code scales[sOff + i·n/32 ..]}): each element is {@link #dotInt8}.
     * Only {@code y} is checked up front; the rows are checked one by one, so a short {@code q} or {@code scales}
     * throws after the earlier elements of {@code y} are written.
     *
     * @param m the number of rows
     * @param n the number of columns, a non-negative multiple of 32
     * @param q the codes of {@code W}, row-major
     * @param qOff the offset of row 0 in {@code q}
     * @param scales the block scales of {@code W}, {@code n / 32} per row
     * @param sOff the offset of row 0's scales in {@code scales}
     * @param x the vector, {@code n} floats
     * @param xOff the offset in {@code x}
     * @param y receives {@code m} floats; must not overlap {@code x}
     * @param yOff the offset in {@code y}
     * @throws IllegalArgumentException if {@code m < 0}, {@code n} is not a non-negative multiple of 32, or an array
     *         is too short for its offset
     */
    public static void gemvInt8(int m, int n, byte[] q, int qOff, float[] scales, int sOff, float[] x, int xOff,
            float[] y, int yOff) {
        range(y == null ? -1 : y.length, yOff, m, "y");
        for (int i = 0; i < m; i++) {
            y[yOff + i] = dotInt8(q, qOff + i * n, scales, sOff + i * (n / QBLOCK), x, xOff, n);
        }
    }

    private static byte encode(float x, float[] pos, boolean finiteOnly, boolean saturate) {
        int sign = Float.floatToRawIntBits(x) < 0 ? 0x80 : 0;
        if (x != x) {
            return (byte) 0x7f; // NaN in both formats
        }
        float a = Math.abs(x);
        int count = pos.length / 2;
        float max = pos[2 * (count - 1)];
        int maxCode = (int) pos[2 * (count - 1) + 1];
        // the virtual value just past max, one ulp of max beyond it
        float over = 2f * max - pos[2 * (count - 2)];
        int code;
        if (a >= over || a == Float.POSITIVE_INFINITY) {
            code = -1;
        } else if (a > max) {
            // between max and the virtual next value: the nearer wins, a tie goes to the even code
            float dm = a - max;
            float dv = over - a;
            code = dm < dv || dm == dv && (maxCode & 1) == 0 ? maxCode : -1;
        } else {
            // the largest value <= a, by binary search over the ascending positives
            int lo = 0;
            int hi = count - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (pos[2 * mid] <= a) {
                    lo = mid;
                } else {
                    hi = mid - 1;
                }
            }
            float v0 = pos[2 * lo];
            if (v0 == a || lo == count - 1) {
                code = (int) pos[2 * lo + 1];
            } else {
                float v1 = pos[2 * lo + 2];
                float d0 = a - v0; // exact: both are small binary fractions of float range
                float d1 = v1 - a;
                int c0 = (int) pos[2 * lo + 1];
                int c1 = (int) pos[2 * lo + 3];
                code = d0 < d1 ? c0 : d1 < d0 ? c1 : (c0 & 1) == 0 ? c0 : c1;
            }
        }
        if (code < 0) { // overflow
            if (saturate) {
                code = maxCode;
            } else {
                return (byte) (finiteOnly ? 0x7f | sign : 0x7c | sign); // E4M3: NaN; E5M2: infinity
            }
        }
        return (byte) (code | sign);
    }

    /** The decoded values of all 256 codes. */
    private static float[] table(int ebits, int mbits, int bias, boolean finiteOnly) {
        float[] t = new float[256];
        int emax = (1 << ebits) - 1;
        for (int b = 0; b < 256; b++) {
            float s = (b & 0x80) != 0 ? -1f : 1f;
            int e = (b >>> mbits) & emax;
            int m = b & ((1 << mbits) - 1);
            float v;
            if (finiteOnly && e == emax && m == (1 << mbits) - 1) {
                v = Float.NaN;
            } else if (!finiteOnly && e == emax) {
                v = m == 0 ? Float.POSITIVE_INFINITY : Float.NaN;
            } else if (e == 0) {
                v = Math.scalb((float) m, 1 - bias - mbits);
            } else {
                v = Math.scalb((float) ((1 << mbits) | m), e - bias - mbits);
            }
            t[b] = s * v;
        }
        return t;
    }

    /** Pairs (value, code) of the non-negative finite values, ascending. */
    private static float[] positives(float[] t) {
        int count = 0;
        for (int b = 0; b < 128; b++) {
            if (t[b] == t[b] && t[b] != Float.POSITIVE_INFINITY) {
                count++;
            }
        }
        float[] p = new float[2 * count];
        int k = 0;
        for (int b = 0; b < 128; b++) { // codes 0..127 are already ascending
            if (t[b] == t[b] && t[b] != Float.POSITIVE_INFINITY) {
                p[2 * k] = t[b];
                p[2 * k + 1] = b;
                k++;
            }
        }
        return p;
    }

    private static void range(int length, int off, int n, String name) {
        if (length < 0 || n < 0 || off < 0 || off > length - n) {
            throw new IllegalArgumentException(name + " must hold " + n + " values from offset " + off);
        }
    }

    /**
     * Vector API int8 kernel: the 8 logical lanes of a block on one 8-lane or two 4-lane float vectors, the codes
     * widened with {@code convertShape}. Everything stays in one method, so no vector is boxed.
     */
    private static final class Simd {
//#ifdef __HAS_VECTOR_API__
        static final VectorSpecies<Float> S = FloatVector.SPECIES_PREFERRED.length() >= 8 ? FloatVector.SPECIES_256
                : FloatVector.SPECIES_PREFERRED;
        static final int LANES = S.length();
        static final VectorSpecies<Byte> BS = VectorSpecies.of(byte.class, S.vectorShape());
        static final boolean AVAILABLE = SimdSupport.isVectorApiAvailable() && (LANES == 4 || LANES == 8);
        static final VectorShuffle<Float> HALF = VectorShuffle.fromOp(S, i -> (i + LANES / 2) % LANES);

        private Simd() {
        }

        static boolean on() {
            return AVAILABLE && SimdSupport.backend() == SimdSupport.Backend.VECTOR_API;
        }

        static float dotInt8(byte[] q, int qOff, float[] scales, int sOff, float[] x, int xOff, int n) {
            FloatVector t0 = FloatVector.zero(S);
            FloatVector t1 = FloatVector.zero(S);
            int bytesPerVector = BS.length(); // 4·LANES: one load widens to four float vectors
            for (int b = 0; b < n / QBLOCK; b++) {
                int base = b * QBLOCK;
                FloatVector c0 = FloatVector.zero(S);
                FloatVector c1 = FloatVector.zero(S);
                for (int j = 0; j < QBLOCK; j += bytesPerVector) {
                    ByteVector qb = ByteVector.fromArray(BS, q, qOff + base + j);
                    for (int part = 0; part < 4; part++) {
                        FloatVector qf = (FloatVector) qb.convertShape(VectorOperators.B2F, S, part);
                        int e = j + part * LANES; // the first element of this float vector
                        FloatVector xf = FloatVector.fromArray(S, x, xOff + base + e);
                        if (LANES == 8 || (e & 4) == 0) {
                            c0 = qf.fma(xf, c0);
                        } else {
                            c1 = qf.fma(xf, c1);
                        }
                    }
                }
                FloatVector sc = FloatVector.broadcast(S, scales[sOff + b]);
                t0 = sc.fma(c0, t0);
                t1 = sc.fma(c1, t1);
            }
            FloatVector p = LANES == 8 ? t0.add(t0.rearrange(HALF)) : t0.add(t1);
            return (p.lane(0) + p.lane(2)) + (p.lane(1) + p.lane(3));
        }
//#else
        private Simd() {
        }
//#endif
    }
}
