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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.nn.BlockQuant;
import org.dynamisengine.vectrix.nn.BlockQuant.Format;
import org.junit.jupiter.api.Test;

/**
 * Block quantization (4.0.0): the decoders bit for bit llama.cpp's, against gguf-py's own dequantization of the
 * synthetic GGML blocks in {@code ggml-blocks.bin} (written by DynamisGPU's {@code scripts/ggml-oracle.py}); the
 * encoders' codes the exactly rounded nearest ones; the scale packing; and the rejected inputs.
 */
class BlockQuantTest {

    private static float half(byte[] b, int at) {
        return Float.float16ToFloat((short) ((b[at] & 0xFF) | (b[at + 1] << 8)));
    }

    /** Decodes one GGML tensor's raw blocks element by element with BlockQuant. */
    private static float[] decodeGgml(int type, byte[] raw, int count) {
        float[] out = new float[count];
        switch (type) {
            case 8 -> { // Q8_0: d, 32 int8 codes
                for (int e = 0; e < count; e++) {
                    int at = e / 32 * 34;
                    out[e] = BlockQuant.decode(Format.Q8, raw[at + 2 + e % 32], half(raw, at), 0f);
                }
            }
            case 2, 3 -> { // Q4_0: d, 16 bytes; Q4_1: d, m, 16 bytes; element i low nibble, 16 + i high
                int bb = type == 2 ? 18 : 20;
                int qs = type == 2 ? 2 : 4;
                for (int e = 0; e < count; e++) {
                    int at = e / 32 * bb;
                    int i = e % 32;
                    int b = raw[at + qs + i % 16] & 0xFF;
                    int u = i < 16 ? b & 0xF : b >> 4;
                    out[e] = type == 2 ? BlockQuant.decode(Format.Q4, u - 8, half(raw, at), 0f)
                            : BlockQuant.decode(Format.Q4M, u, half(raw, at), half(raw, at + 2));
                }
            }
            case 12, 13 -> { // Q4_K, Q5_K: d, dmin, 12 scale bytes, (Q5_K: 32 high-bit bytes), 128 nibble bytes
                int bb = type == 12 ? 144 : 176;
                int qs = type == 12 ? 16 : 48;
                Format f = type == 12 ? Format.Q4K : Format.Q5K;
                for (int e = 0; e < count; e++) {
                    int at = e / 256 * bb;
                    int k = e % 256;
                    int b = raw[at + qs + 32 * (k / 64) + k % 32] & 0xFF;
                    int q = k % 64 < 32 ? b & 0xF : b >> 4;
                    if (type == 13) {
                        q |= ((raw[at + 16 + k % 32] >> (k / 32)) & 1) << 4;
                    }
                    int j = k / 32;
                    out[e] = BlockQuant.decodeK(f, q, half(raw, at), half(raw, at + 2),
                            BlockQuant.unpackScale(raw, at + 4, j, false), BlockQuant.unpackScale(raw, at + 4, j, true));
                }
            }
            case 14 -> { // Q6_K: 128 low bytes, 64 high bytes, 16 int8 scales, d
                for (int e = 0; e < count; e++) {
                    int at = e / 256 * 210;
                    int k = e % 256;
                    int half = k / 128;
                    int w = k % 128;
                    int lb = raw[at + 64 * half + w % 64] & 0xFF;
                    int lo = w < 64 ? lb & 0xF : lb >> 4;
                    int hi = (raw[at + 128 + 32 * half + w % 32] >> (2 * (w / 32))) & 3;
                    out[e] = BlockQuant.decodeK(Format.Q6K, (lo | (hi << 4)) - 32, half(raw, at + 208), 0f,
                            raw[at + 192 + k / 16], 0);
                }
            }
            default -> throw new IllegalArgumentException("type " + type);
        }
        return out;
    }

    @Test
    void decodersMatchGgufPy() throws IOException {
        int records = 0;
        try (var in = new DataInputStream(BlockQuantTest.class.getResourceAsStream("ggml-blocks.bin"))) {
            while (in.available() > 0) {
                var h = ByteBuffer.wrap(in.readNBytes(16)).order(ByteOrder.LITTLE_ENDIAN);
                int type = h.getInt();
                int rows = h.getInt();
                int cols = h.getInt();
                int n = h.getInt();
                byte[] raw = in.readNBytes(n);
                var v = ByteBuffer.wrap(in.readNBytes(4 * rows * cols)).order(ByteOrder.LITTLE_ENDIAN);
                float[] got = decodeGgml(type, raw, rows * cols);
                for (int i = 0; i < rows * cols; i++) {
                    float want = v.getFloat();
                    assertEquals(Float.floatToIntBits(want), Float.floatToIntBits(got[i]),
                            "type " + type + " element " + i + ": " + got[i] + ", gguf-py " + want);
                }
                records++;
            }
        }
        assertEquals(6, records, "Q4_0, Q4_1, Q8_0, Q4_K, Q5_K and Q6_K");
    }

    @Test
    void nearestIsExactlyRounded() {
        var r = new SplittableRandom(1);
        for (int i = 0; i < 200_000; i++) {
            float d = Float.float16ToFloat(Float.floatToFloat16((float) (r.nextDouble(1e-4, 4))));
            if (d <= 0) {
                continue;
            }
            int c = r.nextInt(-200, 200);
            // values at and around midpoints, and random ones
            float y = switch (i % 3) {
                case 0 -> (2 * c + 1) * d * 0.5f;
                case 1 -> Math.nextUp((2 * c + 1) * d * 0.5f);
                default -> (float) (r.nextDouble(-250, 250) * d);
            };
            int want = new BigDecimal(y).divide(new BigDecimal(d), 30, RoundingMode.HALF_EVEN)
                    .setScale(0, RoundingMode.HALF_EVEN).intValueExact();
            assertEquals(want, BlockQuant.nearest(y, d), "nearest(" + y + ", " + d + ")");
        }
    }

    @Test
    void blockEncoderCodesAreNearest() {
        var r = new SplittableRandom(2);
        for (Format f : new Format[] {Format.Q8, Format.Q4, Format.Q4M}) {
            for (int block : new int[] {32, 64, 128}) {
                for (int t = 0; t < 200; t++) {
                    float[] x = new float[block + 3];
                    double spread = Math.pow(10, r.nextInt(-3, 3));
                    for (int i = 0; i < x.length; i++) {
                        x[i] = (float) (r.nextDouble(-1, 1) * spread);
                    }
                    int[] codes = new int[block + 1];
                    short[] p = new short[3];
                    BlockQuant.encodeBlock(f, x, 3, block, codes, 1, p, 1);
                    float d = Float.float16ToFloat(p[1]);
                    float m = f == Format.Q4M ? Float.float16ToFloat(p[2]) : 0f;
                    for (int i = 0; i < block; i++) {
                        int q = codes[1 + i];
                        assertTrue(q >= f.minCode() && q <= f.maxCode(), f + " code " + q);
                        float y = f == Format.Q4M ? x[3 + i] - m : x[3 + i];
                        double err = Math.abs((double) y - (double) q * d);
                        boolean clamped = q == f.minCode() || q == f.maxCode();
                        if (!clamped) {
                            // Q4's d is negative when the block's extreme is positive (it maps to -8)
                            assertTrue(err <= Math.abs(d) / 2.0, f + " block " + block + ": " + y + " -> " + q + " x " + d);
                            if (err == Math.abs(d) / 2.0) {
                                assertEquals(0, q & 1, "ties to even");
                            }
                        }
                        float back = BlockQuant.decode(f, q, d, m);
                        assertEquals(f == Format.Q4M ? q * d + m : q * d, back, 0f);
                    }
                }
            }
        }
    }

    @Test
    void superBlockEncoderCodesAreNearest() {
        var r = new SplittableRandom(3);
        for (Format f : new Format[] {Format.Q4K, Format.Q5K, Format.Q6K}) {
            for (int t = 0; t < 200; t++) {
                float[] x = new float[BlockQuant.SUPER_BLOCK];
                double spread = Math.pow(10, r.nextInt(-3, 3));
                for (int i = 0; i < x.length; i++) {
                    x[i] = (float) (r.nextDouble(-1, 1) * spread * (1 + i / 64));
                }
                int[] codes = new int[256];
                short[] p = new short[2];
                int[] sc = new int[16];
                int[] mn = new int[16];
                BlockQuant.encodeSuperBlock(f, x, 0, codes, 0, p, 0, sc, 0, mn, 0);
                float d = Float.float16ToFloat(p[0]);
                float dmin = Float.float16ToFloat(p[1]);
                for (int e = 0; e < 256; e++) {
                    int j = e / f.subBlock();
                    int q = codes[e];
                    assertTrue(q >= f.minCode() && q <= f.maxCode(), f + " code " + q);
                    if (f == Format.Q6K) {
                        assertTrue(sc[j] >= -128 && sc[j] <= 127 && mn[j] == 0, "Q6K scale " + sc[j]);
                    } else {
                        assertTrue(sc[j] >= 0 && sc[j] <= 63 && mn[j] >= 0 && mn[j] <= 63, f + " scale " + sc[j] + " min " + mn[j]);
                    }
                    float dd = d * sc[j];
                    float y = f == Format.Q6K ? x[e] : x[e] + dmin * mn[j];
                    boolean clamped = q == f.minCode() || q == f.maxCode() || dd == 0f;
                    if (!clamped) {
                        double err = Math.abs((double) y - (double) q * Math.abs(dd));
                        double alt = Math.abs((double) y - (double) q * dd);
                        assertTrue(Math.min(err, alt) <= Math.abs(dd) / 2.0, f + ": " + y + " -> " + q + " x " + dd);
                    }
                    float back = BlockQuant.decodeK(f, q, d, dmin, sc[j], mn[j]);
                    assertEquals(f == Format.Q6K ? dd * q : dd * q - dmin * mn[j], back, 0f);
                }
            }
        }
    }

    @Test
    void scalePackingRoundTrips() {
        var r = new SplittableRandom(4);
        for (int t = 0; t < 1000; t++) {
            int[] sc = new int[8];
            int[] mn = new int[8];
            for (int j = 0; j < 8; j++) {
                sc[j] = r.nextInt(64);
                mn[j] = r.nextInt(64);
            }
            byte[] b = new byte[14];
            BlockQuant.packScales(sc, 0, mn, 0, b, 1);
            for (int j = 0; j < 8; j++) {
                assertEquals(sc[j], BlockQuant.unpackScale(b, 1, j, false));
                assertEquals(mn[j], BlockQuant.unpackScale(b, 1, j, true));
            }
        }
    }

    @Test
    void rejectsWhatItCannotEncodeBeforeWriting() {
        int[] codes = new int[256];
        Arrays.fill(codes, 99);
        short[] p = {7, 7};
        float[] x = new float[256];
        x[5] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> BlockQuant.encodeBlock(Format.Q8, x, 0, 32, codes, 0, p, 0));
        x[5] = 1e9f; // d = 1e9 / 127 overflows FP16
        assertThrows(IllegalArgumentException.class, () -> BlockQuant.encodeBlock(Format.Q8, x, 0, 32, codes, 0, p, 0));
        assertThrows(IllegalArgumentException.class,
                () -> BlockQuant.encodeSuperBlock(Format.Q4K, x, 0, codes, 0, p, 0, new int[8], 0, new int[8], 0));
        assertArrayEquals(new short[] {7, 7}, p, "nothing written");
        assertEquals(99, codes[0], "nothing written");
        assertThrows(IllegalArgumentException.class, () -> BlockQuant.encodeBlock(Format.Q4K, x, 0, 32, codes, 0, p, 0));
        assertThrows(IllegalArgumentException.class, () -> BlockQuant.encodeBlock(Format.Q8, x, 0, 48, codes, 0, p, 0));
        assertThrows(IllegalArgumentException.class,
                () -> BlockQuant.encodeSuperBlock(Format.Q8, x, 0, codes, 0, p, 0, new int[8], 0, new int[8], 0));
        assertThrows(IllegalArgumentException.class, () -> BlockQuant.encodeBlock(Format.Q8, x, 240, 32, codes, 0, p, 0));
        assertThrows(IllegalArgumentException.class, () -> BlockQuant.unpackScale(new byte[12], 0, 8, false));
        assertThrows(IllegalArgumentException.class, () -> BlockQuant.packScales(new int[8], 0, new int[8], 0, new byte[11], 0));
    }
}
