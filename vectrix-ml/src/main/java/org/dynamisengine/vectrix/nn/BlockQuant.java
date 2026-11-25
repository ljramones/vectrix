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

/**
 * Block quantization of weights, the formats llama.cpp's GGUF files ship in: blocks of small integer codes with FP16
 * scales, and the K formats' super-blocks of 256 with small integer scales per sub-block. This class holds the math,
 * independent of any memory layout: encoding a block into its codes and parameters, decoding an element, and the
 * K formats' 12-byte scale packing. DynamisGPU's {@code QuantizedMatrix} packs them for the GPU and delegates here.
 * <p>
 * <b>Formats with one scale per block</b> (32, 64 or 128 elements; llama.cpp's Q8_0, Q4_0 and Q4_1):
 * <ul>
 * <li>{@link Format#Q8}: {@code x = q d}, codes in [-127, 127], {@code d = fp16(max|x| * (1/127))};</li>
 * <li>{@link Format#Q4}: {@code x = q d}, codes in [-8, 7], {@code d = fp16(v * (-1/8))}, {@code v} the block's
 * first value of largest magnitude, which maps to -8;</li>
 * <li>{@link Format#Q4M}: {@code x = fl(q d + m)}, codes in [0, 15], {@code m = fp16(min)},
 * {@code d = fp16((max - min) * (1/15))}.</li>
 * </ul>
 * <b>K formats</b> (super-blocks of {@link #SUPER_BLOCK}; llama.cpp's Q4_K, Q5_K and Q6_K): FP16 {@code d} (and
 * {@code dmin}) and per-sub-block integer scales give {@code D = d sc} and {@code M = dmin mn}, both exact in FP32:
 * <ul>
 * <li>{@link Format#Q4K}: {@code x = fl(D q - M)}, codes in [0, 15], 8 sub-blocks of 32, 6-bit scales and mins;</li>
 * <li>{@link Format#Q5K}: as Q4K with codes in [0, 31];</li>
 * <li>{@link Format#Q6K}: {@code x = fl(D q)}, codes in [-32, 31], 16 sub-blocks of 16, int8 scales.</li>
 * </ul>
 * The decoders are bit for bit llama.cpp's dequantization. The encoders compute every scale with one correctly rounded
 * multiply (never a division) and find each code by comparing with the exact midpoints {@code (2c + 1) d / 2}
 * ({@link #nearest}), so a GPU encoder that does the same gives the same bits. The K encoder is simple (each
 * sub-block's scale from its range, codes nearest); llama.cpp's tools search the scales and are more accurate.
 * Nothing allocates.
 *
 * @since 4.0.0
 */
public final class BlockQuant {

    /** Elements per super-block of the K formats. */
    public static final int SUPER_BLOCK = 256;

    private static final float INV127 = 1f / 127f;
    private static final float INV15 = 1f / 15f;
    private static final float INV63 = 1f / 63f;
    private static final float INV31 = 1f / 31f;

    private BlockQuant() {
    }

    /** A block format. */
    public enum Format {
        /** Symmetric 8-bit: {@code x = q d}, q in [-127, 127]. */
        Q8(8, 1, 0, -127, 127),
        /** Symmetric 4-bit: {@code x = q d}, q in [-8, 7]. */
        Q4(4, 1, 0, -8, 7),
        /** 4-bit with a minimum: {@code x = fl(q d + m)}, q in [0, 15]. */
        Q4M(4, 2, 0, 0, 15),
        /** K-quant 4-bit (Q4_K): {@code x = fl(D q - M)}, q in [0, 15], sub-blocks of 32. */
        Q4K(4, 0, 32, 0, 15),
        /** K-quant 5-bit (Q5_K): {@code x = fl(D q - M)}, q in [0, 31], sub-blocks of 32. */
        Q5K(5, 0, 32, 0, 31),
        /** K-quant 6-bit (Q6_K): {@code x = fl(D q)}, q in [-32, 31], sub-blocks of 16. */
        Q6K(6, 0, 16, -32, 31);

        private final int bits;
        private final int parameters;
        private final int subBlock;
        private final int minCode;
        private final int maxCode;

        Format(int bits, int parameters, int subBlock, int minCode, int maxCode) {
            this.bits = bits;
            this.parameters = parameters;
            this.subBlock = subBlock;
            this.minCode = minCode;
            this.maxCode = maxCode;
        }

        /**
         * Returns the bits per code.
         *
         * @return the bits per code
         */
        public int bits() {
            return bits;
        }

        /**
         * Returns the FP16 parameters per block: d, or d and m; 0 for the K formats (see {@link #encodeSuperBlock}).
         *
         * @return the parameters per block
         */
        public int parameters() {
            return parameters;
        }

        /**
         * Returns whether this is a K format: super-blocks of 256 with per-sub-block scales.
         *
         * @return whether this is a K format
         */
        public boolean kQuant() {
            return subBlock != 0;
        }

        /**
         * Returns the elements per sub-block of a K format, 0 otherwise.
         *
         * @return the elements per sub-block
         */
        public int subBlock() {
            return subBlock;
        }

        /**
         * Returns the sub-blocks per super-block of a K format (8 or 16), 0 otherwise.
         *
         * @return the sub-blocks per super-block
         */
        public int subBlocks() {
            return subBlock == 0 ? 0 : SUPER_BLOCK / subBlock;
        }

        /**
         * Returns the smallest code.
         *
         * @return the smallest code
         */
        public int minCode() {
            return minCode;
        }

        /**
         * Returns the largest code.
         *
         * @return the largest code
         */
        public int maxCode() {
            return maxCode;
        }
    }

    /**
     * Returns the integer nearest to {@code y / d} (ties to even) for {@code d > 0}, found as a GPU finds it: an
     * estimate {@code rint(y * (1/d))}, corrected by comparing {@code y} with the exact midpoints
     * {@code (2c + 1) d / 2}. The midpoints are exact for the codes of these formats ({@code 2c + 1} has at most 9
     * bits and {@code d} at most 15), so the result is exactly rounded. Defined for {@code |y / d| < 2^8}; beyond,
     * it only needs to clamp correctly.
     *
     * @param y the value
     * @param d the step, positive
     * @return the nearest integer
     */
    public static int nearest(float y, float d) {
        float est = (float) Math.rint(y * (1f / d));
        int c = (int) Math.max(-300f, Math.min(300f, est));
        for (int step = 0; step < 2; step++) {
            float up = (2 * c + 1) * d * 0.5f;
            float down = (2 * c - 1) * d * 0.5f;
            if (y > up || y == up && (c & 1) != 0) {
                c++;
            } else if (y < down || y == down && (c & 1) != 0) {
                c--;
            }
        }
        return c;
    }

    /**
     * Returns the code nearest to {@code y / d}, clamped to [{@code min}, {@code max}]: 0 when {@code d} is 0, and a
     * negative {@code d} works as {@code (-y) / (-d)}.
     *
     * @param y the value
     * @param d the step
     * @param min the smallest code
     * @param max the largest code
     * @return the code
     */
    public static int nearestCode(float y, float d, int min, int max) {
        if (d == 0f) {
            return 0;
        }
        return Math.max(min, Math.min(max, d > 0 ? nearest(y, d) : nearest(-y, -d)));
    }

    /**
     * Encodes a block of a format with one scale per block ({@link Format#Q8}, {@link Format#Q4} or
     * {@link Format#Q4M}): its codes, and its parameters as FP16 bits ({@code d}, then for Q4M {@code m}).
     *
     * @param format the format
     * @param x the values
     * @param xOff the offset of the block in {@code x}
     * @param block the elements per block: 32, 64 or 128
     * @param codes receives the {@code block} codes (signed for Q8 and Q4)
     * @param cOff the offset in {@code codes}
     * @param params receives {@code format.parameters()} FP16 bit patterns
     * @param pOff the offset in {@code params}
     * @throws IllegalArgumentException if the format is a K format, the block size is not 32, 64 or 128, an array is
     *         too short, a value is NaN or infinite, or a parameter overflows FP16; nothing is written then
     */
    public static void encodeBlock(Format format, float[] x, int xOff, int block, int[] codes, int cOff,
            short[] params, int pOff) {
        if (format == null || format.kQuant()) {
            throw new IllegalArgumentException("encodeBlock takes Q8, Q4 or Q4M: " + format);
        }
        if (block != 32 && block != 64 && block != 128) {
            throw new IllegalArgumentException("block must be 32, 64 or 128: " + block);
        }
        range(x == null ? -1 : x.length, xOff, block, "x");
        range(codes == null ? -1 : codes.length, cOff, block, "codes");
        range(params == null ? -1 : params.length, pOff, format.parameters, "params");
        finite(x, xOff, block);
        float lo = Float.POSITIVE_INFINITY;
        float hi = Float.NEGATIVE_INFINITY;
        float amax = 0f;
        float extreme = 0f;
        for (int i = 0; i < block; i++) {
            float v = x[xOff + i];
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
            if (Math.abs(v) > amax) { // the first of largest magnitude
                amax = Math.abs(v);
                extreme = v;
            }
        }
        float m = 0f;
        short dBits;
        short mBits = 0;
        if (format == Format.Q8) {
            dBits = Float.floatToFloat16(amax * INV127);
        } else if (format == Format.Q4) {
            dBits = Float.floatToFloat16(extreme * -0.125f);
        } else {
            mBits = Float.floatToFloat16(lo);
            m = Float.float16ToFloat(mBits);
            dBits = Float.floatToFloat16((hi - lo) * INV15);
        }
        float d = Float.float16ToFloat(dBits);
        if (Float.isInfinite(d) || Float.isInfinite(m)) {
            throw new IllegalArgumentException("the block's parameters overflow FP16 at x[" + xOff + "]");
        }
        params[pOff] = dBits;
        if (format == Format.Q4M) {
            params[pOff + 1] = mBits;
        }
        for (int i = 0; i < block; i++) {
            float y = format == Format.Q4M ? x[xOff + i] - m : x[xOff + i];
            codes[cOff + i] = nearestCode(y, d, format.minCode, format.maxCode);
        }
    }

    /**
     * Encodes a K-format super-block of {@link #SUPER_BLOCK} values (simply: each sub-block's scale from its range,
     * codes nearest): its codes, its FP16 {@code d} and {@code dmin} (Q6K: {@code d} and 0), and each sub-block's
     * integer scale and min (Q6K: int8 scales, mins 0).
     *
     * @param format {@link Format#Q4K}, {@link Format#Q5K} or {@link Format#Q6K}
     * @param x the values
     * @param xOff the offset of the super-block in {@code x}
     * @param codes receives the 256 codes (signed for Q6K)
     * @param cOff the offset in {@code codes}
     * @param params receives the FP16 bits of {@code d} and {@code dmin}
     * @param pOff the offset in {@code params}
     * @param scales receives {@code format.subBlocks()} scales: 6-bit (0 to 63), or int8 for Q6K
     * @param sOff the offset in {@code scales}
     * @param mins receives {@code format.subBlocks()} 6-bit mins (0 for Q6K)
     * @param mOff the offset in {@code mins}
     * @throws IllegalArgumentException if the format is not a K format, an array is too short, a value is NaN or
     *         infinite, or a scale overflows FP16; nothing is written then
     */
    public static void encodeSuperBlock(Format format, float[] x, int xOff, int[] codes, int cOff, short[] params,
            int pOff, int[] scales, int sOff, int[] mins, int mOff) {
        if (format == null || !format.kQuant()) {
            throw new IllegalArgumentException("encodeSuperBlock takes Q4K, Q5K or Q6K: " + format);
        }
        int sub = format.subBlock;
        int subs = format.subBlocks();
        range(x == null ? -1 : x.length, xOff, SUPER_BLOCK, "x");
        range(codes == null ? -1 : codes.length, cOff, SUPER_BLOCK, "codes");
        range(params == null ? -1 : params.length, pOff, 2, "params");
        range(scales == null ? -1 : scales.length, sOff, subs, "scales");
        range(mins == null ? -1 : mins.length, mOff, subs, "mins");
        finite(x, xOff, SUPER_BLOCK);
        if (format == Format.Q6K) {
            // each sub-block's signed extreme maps to -32; the int8 scales share d, whose extreme scale maps to -128
            float extremeScale = 0f;
            for (int j = 0; j < subs; j++) {
                float s = q6Scale(x, xOff + j * sub, sub);
                if (Math.abs(s) > Math.abs(extremeScale)) {
                    extremeScale = s;
                }
            }
            short dBits = Float.floatToFloat16(extremeScale * (-1f / 128f));
            float d = Float.float16ToFloat(dBits);
            if (Float.isInfinite(d)) {
                throw new IllegalArgumentException("the super-block's scale overflows FP16 at x[" + xOff + "]");
            }
            params[pOff] = dBits;
            params[pOff + 1] = 0;
            for (int j = 0; j < subs; j++) {
                int sc = nearestCode(q6Scale(x, xOff + j * sub, sub), d, -128, 127);
                scales[sOff + j] = sc;
                mins[mOff + j] = 0;
                float dd = d * sc; // exact: 11 bits times 8
                for (int i = 0; i < sub; i++) {
                    codes[cOff + j * sub + i] = nearestCode(x[xOff + j * sub + i], dd, -32, 31);
                }
            }
            return;
        }
        // Q4K, Q5K: x = D q - M with M >= 0 subtracting the sub-block's negative minimum
        float inv = format == Format.Q4K ? INV15 : INV31;
        float maxScale = 0f;
        float maxMin = 0f;
        for (int j = 0; j < subs; j++) {
            float mn = subMin(x, xOff + j * sub, sub);
            maxScale = Math.max(maxScale, (Math.max(subMax(x, xOff + j * sub, sub), 0f) + mn) * inv);
            maxMin = Math.max(maxMin, mn);
        }
        short dBits = Float.floatToFloat16(maxScale * INV63);
        short mBits = Float.floatToFloat16(maxMin * INV63);
        float d = Float.float16ToFloat(dBits);
        float dmin = Float.float16ToFloat(mBits);
        if (Float.isInfinite(d) || Float.isInfinite(dmin)) {
            throw new IllegalArgumentException("the super-block's scale overflows FP16 at x[" + xOff + "]");
        }
        params[pOff] = dBits;
        params[pOff + 1] = mBits;
        for (int j = 0; j < subs; j++) {
            float mn = subMin(x, xOff + j * sub, sub);
            float scale = (Math.max(subMax(x, xOff + j * sub, sub), 0f) + mn) * inv;
            int sc = nearestCode(scale, d, 0, 63);
            int mc = nearestCode(mn, dmin, 0, 63);
            scales[sOff + j] = sc;
            mins[mOff + j] = mc;
            float dd = d * sc; // exact: 11 bits times 6
            float mm = dmin * mc;
            for (int i = 0; i < sub; i++) {
                codes[cOff + j * sub + i] = nearestCode(x[xOff + j * sub + i] + mm, dd, 0, format.maxCode);
            }
        }
    }

    /** A Q6K sub-block's scale: its first value of largest magnitude times -1/32. */
    private static float q6Scale(float[] x, int off, int n) {
        float amax = 0f;
        float extreme = 0f;
        for (int i = 0; i < n; i++) {
            float v = x[off + i];
            if (Math.abs(v) > amax) {
                amax = Math.abs(v);
                extreme = v;
            }
        }
        return extreme * (-1f / 32f);
    }

    /** A Q4K or Q5K sub-block's min term: minus its minimum when that is negative, else 0. */
    private static float subMin(float[] x, int off, int n) {
        float lo = Float.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            lo = Math.min(lo, x[off + i]);
        }
        return lo < 0 ? -lo : 0f;
    }

    private static float subMax(float[] x, int off, int n) {
        float hi = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            hi = Math.max(hi, x[off + i]);
        }
        return hi;
    }

    /**
     * Decodes an element of a format with one scale per block: {@code q d}, or for Q4M {@code fl(q d + m)}.
     *
     * @param format {@link Format#Q8}, {@link Format#Q4} or {@link Format#Q4M}
     * @param code the code
     * @param d the block's scale
     * @param m the block's minimum (Q4M; ignored otherwise)
     * @return the value
     */
    public static float decode(Format format, int code, float d, float m) {
        float q = code;
        return format == Format.Q4M ? q * d + m : q * d;
    }

    /**
     * Decodes an element of a K format, bit for bit llama.cpp: {@code D = d sc} and {@code M = dmin mn} (exact), then
     * {@code fl(D q - M)}, or {@code fl(D q)} for Q6K.
     *
     * @param format {@link Format#Q4K}, {@link Format#Q5K} or {@link Format#Q6K}
     * @param code the code
     * @param d the super-block's {@code d}
     * @param dmin the super-block's {@code dmin} (ignored for Q6K)
     * @param scale the sub-block's scale
     * @param min the sub-block's min (ignored for Q6K)
     * @return the value
     */
    public static float decodeK(Format format, int code, float d, float dmin, int scale, int min) {
        float q = code;
        float dd = d * scale;
        return format == Format.Q6K ? dd * q : dd * q - dmin * min;
    }

    /**
     * Packs eight 6-bit scales and mins into llama.cpp's 12 bytes (the inverse of its {@code get_scale_min_k4}), the
     * way Q4_K and Q5_K blocks store them.
     *
     * @param scales the eight scales, 0 to 63
     * @param sOff the offset in {@code scales}
     * @param mins the eight mins, 0 to 63
     * @param mOff the offset in {@code mins}
     * @param out receives 12 bytes
     * @param at the offset in {@code out}
     * @throws IllegalArgumentException if an array is too short
     */
    public static void packScales(int[] scales, int sOff, int[] mins, int mOff, byte[] out, int at) {
        range(scales == null ? -1 : scales.length, sOff, 8, "scales");
        range(mins == null ? -1 : mins.length, mOff, 8, "mins");
        range(out == null ? -1 : out.length, at, 12, "out");
        for (int j = 0; j < 4; j++) {
            int sc = scales[sOff + j];
            int sc4 = scales[sOff + j + 4];
            int mn = mins[mOff + j];
            int mn4 = mins[mOff + j + 4];
            out[at + j] = (byte) ((sc & 63) | ((sc4 >> 4) << 6));
            out[at + j + 4] = (byte) ((mn & 63) | ((mn4 >> 4) << 6));
            out[at + j + 8] = (byte) ((sc4 & 15) | ((mn4 & 15) << 4));
        }
    }

    /**
     * Returns sub-block {@code j}'s 6-bit scale ({@code min = false}) or min ({@code min = true}) from llama.cpp's 12
     * packed bytes ({@code get_scale_min_k4}).
     *
     * @param packed the bytes
     * @param at the offset of the 12 bytes
     * @param j the sub-block, 0 to 7
     * @param min whether to return the min
     * @return the scale or min, 0 to 63
     */
    public static int unpackScale(byte[] packed, int at, int j, boolean min) {
        if (j < 0 || j > 7) {
            throw new IllegalArgumentException("j must be in [0, 7]: " + j);
        }
        if (packed == null) {
            throw new IllegalArgumentException("packed is null");
        }
        range(packed.length, at, 12, "packed");
        if (j < 4) {
            return (min ? packed[at + j + 4] : packed[at + j]) & 63;
        }
        int q8 = packed[at + j + 4] & 0xFF;
        int lo = packed[at + j - 4] & 0xFF;
        int mid = packed[at + j] & 0xFF;
        return min ? (q8 >> 4) | ((mid >> 6) << 4) : (q8 & 0xF) | ((lo >> 6) << 4);
    }

    private static void finite(float[] x, int off, int n) {
        for (int i = 0; i < n; i++) {
            float v = x[off + i];
            if (v - v != 0f) { // NaN or ±infinity
                throw new IllegalArgumentException("x[" + (off + i) + "] = " + v + " is not finite");
            }
        }
    }

    private static void range(int length, int off, int n, String name) {
        if (length < 0) {
            throw new IllegalArgumentException(name + " is null");
        }
        if (off < 0 || n < 0 || off > length - n) {
            throw new IllegalArgumentException(name + " must hold " + n + " values from offset " + off + " (has "
                    + length + ")");
        }
    }
}
