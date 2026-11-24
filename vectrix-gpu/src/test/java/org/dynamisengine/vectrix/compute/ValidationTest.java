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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Invalid arguments are rejected before anything is recorded, with the CPU kernels' exception type. */
class ValidationTest {

    /** Runs {@code body}, which must throw IllegalArgumentException, then checks {@code target} is unchanged. */
    private static void rejected(GpuDevice device, GpuCommandEncoder enc, GpuBuffer target, int count, Executable body) {
        assertThrows(IllegalArgumentException.class, body);
        var read = enc.download(target);
        Devices.run(device, enc);
        float[] ones = new float[count];
        Arrays.fill(ones, 1f);
        assertArrayEquals(ones, Devices.floats(read, count));
    }

    private static float[] ones(int n) {
        float[] a = new float[n];
        Arrays.fill(a, 1f);
        return a;
    }

    @Test
    void gemmChecksSizesAndBuffers() {
        try (GpuDevice d = Devices.open("reference"); GpuGemmf g = new GpuGemmf(d)) {
            int[][] bad = {{-1, 2, 2}, {2, -1, 2}, {2, 2, -1}};
            for (int[] s : bad) {
                var enc = d.encoder();
                var a = Devices.upload(d, enc, ones(16));
                var c = Devices.upload(d, enc, ones(16));
                rejected(d, enc, c, 16, () -> g.gemm(enc, false, false, s[0], s[1], s[2], 1f, a, 0, 4, a, 0, 4, 0f, c,
                        0, 4));
            }
            var enc = d.encoder();
            var a = Devices.upload(d, enc, ones(16));
            var c = Devices.upload(d, enc, ones(16));
            // A is 4x5 with lda 5: needs 20 elements, the buffer has 16
            rejected(d, enc, c, 16, () -> g.gemm(enc, false, false, 4, 4, 5, 1f, a, 0, 5, a, 0, 4, 0f, c, 0, 4));
            var enc2 = d.encoder();
            var a2 = Devices.upload(d, enc2, ones(16));
            var c2 = Devices.upload(d, enc2, ones(16));
            // leading dimension below the row length
            rejected(d, enc2, c2, 16, () -> g.gemm(enc2, false, false, 2, 2, 2, 1f, a2, 0, 1, a2, 0, 2, 0f, c2, 0, 2));
        }
    }

    @Test
    void gemmRejectsCAliasingAnInput() {
        try (GpuDevice d = Devices.open("reference"); GpuGemmf g = new GpuGemmf(d)) {
            var enc = d.encoder();
            var a = Devices.upload(d, enc, ones(64));
            var b = Devices.upload(d, enc, ones(64));
            rejected(d, enc, a, 64, () -> g.gemm(enc, false, false, 2, 2, 2, 1f, a, 0, 2, b, 0, 2, 0f, a, 32, 2));
            var enc2 = d.encoder();
            rejected(d, enc2, b, 64, () -> g.gemm(enc2, false, false, 2, 2, 2, 1f, a, 0, 2, b, 0, 2, 0f, b, 32, 2));
            var c = Devices.buffer(d, 4);
            assertThrows(IllegalArgumentException.class, () -> g.gemm(null, false, false, 1, 1, 1, 1f, a, 0, 1, b, 0,
                    1, 0f, c, 0, 1));
        }
    }

    @Test
    void philoxChecksTheDestination() {
        try (GpuDevice d = Devices.open("reference"); GpuPhilox p = new GpuPhilox(d)) {
            var enc = d.encoder();
            var dest = Devices.upload(d, enc, ones(10));
            rejected(d, enc, dest, 10, () -> p.fillWords(enc, 1, 0, dest, 3, 8));
            var enc2 = d.encoder();
            rejected(d, enc2, dest, 10, () -> p.fillUniform(enc2, 1, 0, dest, -1, 2));
            var enc3 = d.encoder();
            rejected(d, enc3, dest, 10, () -> p.fillUniform(enc3, 1, 0, dest, 0, -1));
            var enc4 = d.encoder();
            rejected(d, enc4, dest, 10, () -> p.fillWords(enc4, 1, 0, dest, 0, Integer.MAX_VALUE));
            var enc5 = d.encoder();
            rejected(d, enc5, dest, 10, () -> p.fillWords(enc5, 1, 0, null, 0, 1));
        }
    }

    @Test
    void softmaxChecksRowsAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuSoftmax s = new GpuSoftmax(d)) {
            var enc = d.encoder();
            var x = Devices.upload(d, enc, ones(40));
            var y = Devices.upload(d, enc, ones(40));
            rejected(d, enc, y, 40, () -> s.softmax(enc, 5, x, 0, 8, y, 0, 8, 9)); // stride below the row length
            var enc2 = d.encoder();
            rejected(d, enc2, y, 40, () -> s.softmax(enc2, 6, x, 0, 8, y, 0, 8, 8)); // 6 rows do not fit
            var enc3 = d.encoder();
            rejected(d, enc3, x, 40, () -> s.softmax(enc3, 2, x, 0, 8, x, 16, 8, 8)); // same buffer, other offset
            var enc4 = d.encoder();
            rejected(d, enc4, x, 40, () -> s.softmax(enc4, 1, x, 0, 8, x, 0, 9, 8)); // same buffer, other stride
            var enc5 = d.encoder();
            rejected(d, enc5, y, 40, () -> s.softmax(enc5, -1, x, 0, 8, y, 0, 8, 8));
            var enc6 = d.encoder();
            rejected(d, enc6, y, 40, () -> s.softmax(enc6, 1, x, 0, GpuSoftmax.MAX_ROW + 1, y, 0,
                    GpuSoftmax.MAX_ROW + 1, GpuSoftmax.MAX_ROW + 1));
        }
    }

    @Test
    void emptyWorkRecordsNothing() {
        try (GpuDevice d = Devices.open("reference"); GpuGemmf g = new GpuGemmf(d); GpuPhilox p = new GpuPhilox(d);
                GpuSoftmax s = new GpuSoftmax(d)) {
            var enc = d.encoder();
            var buf = Devices.upload(d, enc, ones(4));
            g.gemm(enc, false, false, 0, 3, 1, 1f, buf, 0, 1, buf, 0, 3, 0f, Devices.buffer(d, 4), 0, 3);
            g.gemm(enc, false, false, 3, 0, 1, 1f, buf, 0, 1, buf, 0, 0, 0f, Devices.buffer(d, 4), 0, 0);
            p.fillWords(enc, 1, 0, buf, 4, 0);
            s.softmax(enc, 0, buf, 0, 4, buf, 0, 4, 4);
            s.softmax(enc, 3, buf, 0, 0, buf, 0, 0, 0);
            var read = enc.download(buf);
            Devices.run(d, enc);
            assertArrayEquals(ones(4), Devices.floats(read, 4));
        }
    }

    @Test
    void formatConversionsCheckPackedRangesAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuFormats f = new GpuFormats(d)) {
            // 8 words: 16 bfloat16 or 32 FP8 codes
            var enc = d.encoder();
            var x = Devices.upload(d, enc, ones(40));
            var p = Devices.upload(d, enc, ones(8));
            Executable[] bad = {
                () -> f.toBf16(enc, x, 0, p, 0, 17), // 17 bfloat16 do not fit 8 words
                () -> f.toBf16(enc, x, 0, p, 1, 16), // nor 16 from offset 1
                () -> f.toBf16(enc, x, 30, p, 0, 16), // x too short from 30
                () -> f.toBf16(enc, x, 0, p, -1, 2),
                () -> f.toBf16(enc, x, 0, p, 0, -1),
                () -> f.toBf16(enc, x, 0, x, 20, 4), // the same buffer
                () -> f.toBf16(enc, null, 0, p, 0, 1),
                () -> f.toBf16(null, x, 0, p, 0, 1),
                () -> f.fromBf16(enc, p, 15, x, 0, 2),
                () -> f.toE4M3(enc, x, 0, p, 0, 33, false), // 33 codes do not fit 8 words
                () -> f.toE5M2(enc, x, 0, p, 29, 4, true),
                () -> f.fromE4M3(enc, p, 0, x, 0, 41),
                () -> f.fromE5M2(enc, p, -2, x, 0, 1),
                () -> f.fromE5M2(enc, x, 0, x, 20, 4),
            };
            for (Executable body : bad) {
                assertThrows(IllegalArgumentException.class, body);
            }
            var read = enc.download(p);
            Devices.run(d, enc);
            assertArrayEquals(ones(8), Devices.floats(read, 8));
        }
    }

    @Test
    void int8KernelsCheckBlocksRangesAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuFormats f = new GpuFormats(d)) {
            var enc = d.encoder();
            var x = Devices.upload(d, enc, ones(64));
            var q = Devices.upload(d, enc, ones(16)); // 64 codes
            var s = Devices.upload(d, enc, ones(2));
            var y = Devices.upload(d, enc, ones(4));
            Executable[] bad = {
                () -> f.quantizeInt8(enc, x, 0, 33, q, 0, s, 0), // not a multiple of 32
                () -> f.quantizeInt8(enc, x, 0, -32, q, 0, s, 0),
                () -> f.quantizeInt8(enc, x, 0, 64, q, 1, s, 0), // 64 codes from 1
                () -> f.quantizeInt8(enc, x, 0, 64, q, 0, s, 1), // 2 scales from 1
                () -> f.quantizeInt8(enc, x, 1, 64, q, 0, s, 0),
                () -> f.quantizeInt8(enc, x, 0, 32, x, 0, s, 0),
                () -> f.quantizeInt8(enc, x, 0, 32, q, 0, q, 0),
                () -> f.quantizeInt8(enc, x, 0, 32, q, 0, x, 0),
                () -> f.dequantizeInt8(enc, q, 0, s, 0, x, 0, 48),
                () -> f.dequantizeInt8(enc, q, 0, s, 0, q, 0, 32),
                () -> f.dequantizeInt8(enc, q, 0, s, 0, s, 0, 32),
                () -> f.dotInt8(enc, q, 0, s, 0, x, 0, 31, y, 0),
                () -> f.dotInt8(enc, q, 0, s, 0, x, 0, 32, y, 4), // dest past the end
                () -> f.dotInt8(enc, q, 0, s, 0, x, 0, 32, x, 40), // dest is an input
                () -> f.gemvInt8(enc, -1, 32, q, 0, s, 0, x, 0, y, 0),
                () -> f.gemvInt8(enc, 3, 32, q, 0, s, 0, x, 0, y, 0), // 3 rows: 96 codes, 3 scales
                () -> f.gemvInt8(enc, 2, 32, q, 0, s, 0, x, 40, y, 0), // x too short from 40
                () -> f.gemvInt8(enc, 2, 32, q, 0, s, 0, x, 0, y, 3), // 2 results from 3
                () -> f.gemvInt8(enc, 2, 32, q, 0, s, 0, x, 0, s, 0),
                () -> f.gemvInt8(enc, 1, 32, q, 0, s, 0, x, 0, null, 0),
            };
            for (Executable body : bad) {
                assertThrows(IllegalArgumentException.class, body);
            }
            var rq = enc.download(q);
            var rs = enc.download(s);
            var ry = enc.download(y);
            var rx = enc.download(x);
            Devices.run(d, enc);
            assertArrayEquals(ones(16), Devices.floats(rq, 16));
            assertArrayEquals(ones(2), Devices.floats(rs, 2));
            assertArrayEquals(ones(4), Devices.floats(ry, 4));
            assertArrayEquals(ones(64), Devices.floats(rx, 64));
        }
    }

    @Test
    void argmaxChecksRowsAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuSampler s = new GpuSampler(d)) {
            var enc = d.encoder();
            var x = Devices.upload(d, enc, ones(40));
            var dest = Devices.upload(d, enc, ones(4));
            Executable[] bad = {
                () -> s.argmax(enc, 5, x, 0, 8, 9, dest, 0), // stride below the row length
                () -> s.argmax(enc, 4, x, 9, 8, 8, dest, 0), // 4 rows do not fit from 9
                () -> s.argmax(enc, 5, x, 0, 8, 8, dest, 0), // 5 indices do not fit
                () -> s.argmax(enc, 2, x, 0, 8, 8, dest, 3),
                () -> s.argmax(enc, -1, x, 0, 8, 8, dest, 0),
                () -> s.argmax(enc, 1, x, 0, 8, -1, dest, 0),
                () -> s.argmax(enc, 1, x, -1, 0, 0, dest, 0),
                () -> s.argmax(enc, 1, x, 0, 8, 8, x, 32), // the same buffer
                () -> s.argmax(enc, 1, null, 0, 8, 8, dest, 0),
                () -> s.argmax(null, 1, x, 0, 8, 8, dest, 0),
            };
            for (Executable body : bad) {
                assertThrows(IllegalArgumentException.class, body);
            }
            rejected(d, enc, dest, 4, () -> s.argmax(enc, 1, x, 0, 8, 8, dest, -1));
        }
    }

    @Test
    void emptyFormatWorkRecordsNothing() {
        try (GpuDevice d = Devices.open("reference"); GpuFormats f = new GpuFormats(d);
                GpuSampler s = new GpuSampler(d)) {
            var enc = d.encoder();
            var x = Devices.upload(d, enc, ones(4));
            var p = Devices.upload(d, enc, ones(4));
            f.toBf16(enc, x, 4, p, 8, 0);
            f.fromBf16(enc, p, 8, x, 4, 0);
            f.toE4M3(enc, x, 0, p, 16, 0, false);
            f.fromE5M2(enc, p, 16, x, 0, 0);
            f.quantizeInt8(enc, x, 0, 0, p, 0, Devices.buffer(d, 1), 0);
            f.dequantizeInt8(enc, p, 0, Devices.buffer(d, 1), 0, x, 0, 0);
            f.gemvInt8(enc, 0, 32, p, 0, Devices.buffer(d, 1), 0, Devices.buffer(d, 32), 0, x, 0);
            s.argmax(enc, 0, p, 0, 4, 4, x, 0);
            var rx = enc.download(x);
            var rp = enc.download(p);
            Devices.run(d, enc);
            assertArrayEquals(ones(4), Devices.floats(rx, 4));
            assertArrayEquals(ones(4), Devices.floats(rp, 4));
        }
    }
}
