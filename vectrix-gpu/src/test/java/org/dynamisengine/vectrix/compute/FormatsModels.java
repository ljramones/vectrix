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
package org.dynamisengine.vectrix.compute;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;

import org.dynamisengine.vectrix.nn.Formats;
import org.dynamisengine.vectrix.nn.Sampler;

/**
 * Reference models of the {@link GpuFormats} and {@link GpuSampler} kernels: each runs the Vectrix method on the
 * dispatch's buffers, reading and writing the packed 8- and 16-bit data at its byte positions (element {@code i} at
 * byte {@code i} or {@code 2i}, little-endian), and checks that the dispatch's groups cover the work.
 */
final class FormatsModels {

    private FormatsModels() {
    }

    static void register(Map<String, ReferenceModels.ReferenceModel> m) {
        m.put("to_bf16", FormatsModels::toBf16);
        m.put("from_bf16", FormatsModels::fromBf16);
        m.put("to_fp8", FormatsModels::toFp8);
        m.put("from_fp8", FormatsModels::fromFp8);
        m.put("quantize_int8", FormatsModels::quantize);
        m.put("dequantize_int8", FormatsModels::dequantize);
        m.put("gemv_int8", FormatsModels::gemv);
        m.put("argmax", FormatsModels::argmax);
    }

    /** The words holding packed elements [off, off + n), perWord to a word. */
    private static long words(long off, long n, int perWord) {
        return (off + n - 1) / perWord - off / perWord + 1;
    }

    private static long groups(long work, int perGroup) {
        return (work + perGroup - 1) / perGroup;
    }

    private static float f(ByteBuffer b, long index) {
        return b.getFloat(Math.toIntExact(4 * index));
    }

    private static void toBf16(ReferenceModels.Call c) {
        long xOff = c.u("xOff");
        long hOff = c.u("hOff");
        int n = c.i("n");
        c.requireLinearGrid(groups(words(hOff, n, 2), 64));
        ByteBuffer x = c.slots().get(0);
        ByteBuffer h = c.slots().get(1);
        for (int i = 0; i < n; i++) {
            h.putShort(Math.toIntExact(2 * (hOff + i)), Formats.toBf16(f(x, xOff + i)));
        }
    }

    private static void fromBf16(ReferenceModels.Call c) {
        long hOff = c.u("hOff");
        long xOff = c.u("xOff");
        int n = c.i("n");
        c.requireLinearGrid(groups(n, 64));
        ByteBuffer h = c.slots().get(0);
        ByteBuffer x = c.slots().get(1);
        for (int i = 0; i < n; i++) {
            float v = Formats.fromBf16(h.getShort(Math.toIntExact(2 * (hOff + i))));
            x.putInt(Math.toIntExact(4 * (xOff + i)), Float.floatToRawIntBits(v));
        }
    }

    private static void toFp8(ReferenceModels.Call c) {
        long xOff = c.u("xOff");
        long cOff = c.u("cOff");
        int n = c.i("n");
        boolean e5m2 = c.u("e5m2") != 0;
        boolean saturate = c.u("saturate") != 0;
        c.requireLinearGrid(groups(words(cOff, n, 4), 64));
        ByteBuffer x = c.slots().get(0);
        ByteBuffer codes = c.slots().get(1);
        for (int i = 0; i < n; i++) {
            float v = f(x, xOff + i);
            codes.put(Math.toIntExact(cOff + i), e5m2 ? Formats.toE5M2(v, saturate) : Formats.toE4M3(v, saturate));
        }
    }

    private static void fromFp8(ReferenceModels.Call c) {
        long cOff = c.u("cOff");
        long xOff = c.u("xOff");
        int n = c.i("n");
        boolean e5m2 = c.u("e5m2") != 0;
        c.requireLinearGrid(groups(n, 64));
        ByteBuffer codes = c.slots().get(0);
        ByteBuffer x = c.slots().get(1);
        for (int i = 0; i < n; i++) {
            byte b = codes.get(Math.toIntExact(cOff + i));
            float v = e5m2 ? Formats.fromE5M2(b) : Formats.fromE4M3(b);
            x.putInt(Math.toIntExact(4 * (xOff + i)), Float.floatToRawIntBits(v));
        }
    }

    /** Formats.quantizeInt8 block by block; a block holding a NaN or an infinity gets scale NaN and codes 0. */
    private static void quantize(ReferenceModels.Call c) {
        long xOff = c.u("xOff");
        int n = c.i("n");
        long qOff = c.u("qOff");
        long sOff = c.u("sOff");
        c.requireLinearGrid(groups(words(qOff, n, 4), 256));
        ByteBuffer x = c.slots().get(0);
        ByteBuffer q = c.slots().get(1);
        ByteBuffer scales = c.slots().get(2);
        float[] block = new float[Formats.QBLOCK];
        byte[] codes = new byte[Formats.QBLOCK];
        float[] scale = new float[1];
        for (int b = 0; b < n / Formats.QBLOCK; b++) {
            boolean finite = true;
            for (int j = 0; j < Formats.QBLOCK; j++) {
                block[j] = f(x, xOff + (long) b * Formats.QBLOCK + j);
                finite &= Float.isFinite(block[j]);
            }
            if (finite) {
                Formats.quantizeInt8(block, 0, Formats.QBLOCK, codes, 0, scale, 0);
            } else {
                Arrays.fill(codes, (byte) 0);
                scale[0] = Float.NaN;
            }
            scales.putInt(Math.toIntExact(4 * (sOff + b)), Float.floatToRawIntBits(scale[0]));
            for (int j = 0; j < Formats.QBLOCK; j++) {
                q.put(Math.toIntExact(qOff + (long) b * Formats.QBLOCK + j), codes[j]);
            }
        }
    }

    private static void dequantize(ReferenceModels.Call c) {
        long qOff = c.u("qOff");
        long sOff = c.u("sOff");
        long xOff = c.u("xOff");
        int n = c.i("n");
        c.requireLinearGrid(groups(n, 64));
        ByteBuffer q = c.slots().get(0);
        ByteBuffer scales = c.slots().get(1);
        ByteBuffer x = c.slots().get(2);
        byte[] codes = new byte[Formats.QBLOCK];
        float[] scale = new float[1];
        float[] out = new float[Formats.QBLOCK];
        for (int b = 0; b < n / Formats.QBLOCK; b++) {
            for (int j = 0; j < Formats.QBLOCK; j++) {
                codes[j] = q.get(Math.toIntExact(qOff + (long) b * Formats.QBLOCK + j));
            }
            scale[0] = f(scales, sOff + b);
            Formats.dequantizeInt8(codes, 0, scale, 0, out, 0, Formats.QBLOCK);
            for (int j = 0; j < Formats.QBLOCK; j++) {
                x.putInt(Math.toIntExact(4 * (xOff + (long) b * Formats.QBLOCK + j)),
                        Float.floatToRawIntBits(out[j]));
            }
        }
    }

    private static void gemv(ReferenceModels.Call c) {
        int m = c.i("m");
        int n = c.i("n");
        int qOff = c.i("qOff");
        int sOff = c.i("sOff");
        int xOff = c.i("xOff");
        int yOff = c.i("yOff");
        c.requireLinearGrid(groups(m, 8));
        ByteBuffer qb = c.slots().get(0);
        byte[] q = new byte[qb.capacity()];
        qb.get(0, q);
        float[] scales = c.floats(1);
        float[] x = c.floats(2);
        float[] y = c.floats(3);
        Formats.gemvInt8(m, n, q, qOff, scales, sOff, x, xOff, y, yOff);
        c.put(3, y);
    }

    private static void argmax(ReferenceModels.Call c) {
        int rows = c.i("rows");
        int n = c.i("n");
        int off = c.i("off");
        int ld = c.i("ld");
        int dOff = c.i("dOff");
        c.requireLinearGrid(rows);
        float[] logits = c.floats(0);
        ByteBuffer dest = c.slots().get(1);
        for (int r = 0; r < rows; r++) {
            dest.putInt(4 * (dOff + r), n == 0 ? -1 : Sampler.argmax(logits, off + r * ld, n));
        }
    }
}
