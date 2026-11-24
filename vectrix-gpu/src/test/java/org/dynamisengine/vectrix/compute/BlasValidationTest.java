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

/** {@link GpuBlasf} and {@link GpuGemmf#gemv} reject invalid arguments before recording anything. */
class BlasValidationTest {

    /** Runs {@code body}, which must throw IllegalArgumentException, then checks {@code target} is unchanged. */
    private static void rejected(GpuDevice device, GpuCommandEncoder enc, GpuBuffer target, int count, Executable body) {
        assertThrows(IllegalArgumentException.class, body);
        var read = enc.download(target);
        Devices.run(device, enc);
        assertArrayEquals(ones(count), Devices.floats(read, count));
    }

    private static float[] ones(int n) {
        float[] a = new float[n];
        Arrays.fill(a, 1f);
        return a;
    }

    /** One reduction call: (encoder, x, xOff, n, out, outOff, scratch, scratchOff). */
    private interface Reduction {
        void call(GpuCommandEncoder enc, GpuBuffer x, long xOff, int n, GpuBuffer out, long outOff, GpuBuffer scratch,
                long scratchOff);
    }

    @Test
    void reductionsCheckSizesAndBuffers() {
        try (GpuDevice d = Devices.open("reference"); GpuBlasf b = new GpuBlasf(d)) {
            Reduction[] all = {
                (e, x, xo, n, o, oo, s, so) -> b.dot(e, x, xo, x, xo, n, o, oo, s, so),
                (e, x, xo, n, o, oo, s, so) -> b.dot(e, x, 0, x, xo, n, o, oo, s, so),
                b::sum, b::asum, b::nrm2, b::iamax,
            };
            for (Reduction f : all) {
                // {xOff, n, outOff, scratchOff} that do not fit: x holds 16, out 4, scratch scratchSize(16) + 2
                long[][] bad = {{0, -1, 0, 0}, {-1, 4, 0, 0}, {13, 4, 0, 0}, {0, 17, 0, 0}, {0, 4, 4, 0},
                    {0, 4, -1, 0}, {0, 16, 0, 3}, {0, 4, 0, -1}};
                for (long[] c : bad) {
                    var enc = d.encoder();
                    var x = Devices.upload(d, enc, ones(16));
                    var out = Devices.upload(d, enc, ones(4));
                    var scratch = Devices.upload(d, enc, ones((int) GpuBlasf.scratchSize(16) + 2));
                    rejected(d, enc, out, 4, () -> f.call(enc, x, c[0], (int) c[1], out, c[2], scratch, c[3]));
                }
                var enc = d.encoder();
                var x = Devices.upload(d, enc, ones(16));
                var out = Devices.upload(d, enc, ones(4));
                var scratch = Devices.upload(d, enc, ones(8));
                // out or scratch is the input, out is scratch, or a buffer is null
                rejected(d, enc, x, 16, () -> f.call(enc, x, 0, 4, x, 8, scratch, 0));
                var enc2 = d.encoder();
                rejected(d, enc2, x, 16, () -> f.call(enc2, x, 0, 4, out, 0, x, 8));
                var enc3 = d.encoder();
                rejected(d, enc3, out, 4, () -> f.call(enc3, x, 0, 4, scratch, 0, scratch, 4));
                var enc4 = d.encoder();
                rejected(d, enc4, out, 4, () -> f.call(enc4, null, 0, 4, out, 0, scratch, 0));
                var enc5 = d.encoder();
                rejected(d, enc5, x, 16, () -> f.call(enc5, x, 0, 4, null, 0, scratch, 0));
                var enc6 = d.encoder();
                rejected(d, enc6, out, 4, () -> f.call(enc6, x, 0, 4, out, 0, null, 0));
                assertThrows(IllegalArgumentException.class, () -> f.call(null, x, 0, 4, out, 0, scratch, 0));
            }
            // dot: y is checked too, and out may not be y
            var enc = d.encoder();
            var x = Devices.upload(d, enc, ones(16));
            var y = Devices.upload(d, enc, ones(8));
            var out = Devices.upload(d, enc, ones(4));
            var scratch = Devices.upload(d, enc, ones(8));
            rejected(d, enc, out, 4, () -> b.dot(enc, x, 0, y, 0, 9, out, 0, scratch, 0));
            var enc2 = d.encoder();
            rejected(d, enc2, y, 8, () -> b.dot(enc2, x, 0, y, 0, 4, y, 4, scratch, 0));
            var enc3 = d.encoder();
            rejected(d, enc3, y, 8, () -> b.dot(enc3, x, 0, y, 0, 4, out, 0, y, 0));
            assertThrows(IllegalArgumentException.class, () -> GpuBlasf.scratchSize(-1));
        }
    }

    @Test
    void axpyAndScalCheckSizesAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuBlasf b = new GpuBlasf(d)) {
            var enc = d.encoder();
            var x = Devices.upload(d, enc, ones(16));
            var y = Devices.upload(d, enc, ones(8));
            rejected(d, enc, y, 8, () -> b.axpy(enc, 2f, x, 0, y, 0, 9)); // y too short
            var enc2 = d.encoder();
            rejected(d, enc2, y, 8, () -> b.axpy(enc2, 2f, x, 13, y, 0, 4)); // x too short from its offset
            var enc3 = d.encoder();
            rejected(d, enc3, y, 8, () -> b.axpy(enc3, 2f, x, 0, y, -1, 4));
            var enc4 = d.encoder();
            rejected(d, enc4, y, 8, () -> b.axpy(enc4, 2f, x, 0, y, 0, -1));
            var enc5 = d.encoder();
            rejected(d, enc5, x, 16, () -> b.axpy(enc5, 2f, x, 0, x, 1, 4)); // x again, other offset
            var enc6 = d.encoder();
            rejected(d, enc6, y, 8, () -> b.axpy(enc6, 2f, null, 0, y, 0, 4));
            var enc7 = d.encoder();
            rejected(d, enc7, x, 16, () -> b.scal(enc7, 2f, x, 10, 7));
            var enc8 = d.encoder();
            rejected(d, enc8, x, 16, () -> b.scal(enc8, 2f, x, -1, 2));
            var enc9 = d.encoder();
            rejected(d, enc9, x, 16, () -> b.scal(enc9, 2f, x, 0, -1));
            assertThrows(IllegalArgumentException.class, () -> b.scal(null, 2f, x, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> b.axpy(null, 2f, x, 0, y, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> new GpuBlasf(null));
        }
    }

    @Test
    void gemvChecksSizesAndBuffers() {
        try (GpuDevice d = Devices.open("reference"); GpuGemmf g = new GpuGemmf(d)) {
            int[][] bad = {{-1, 2}, {2, -1}};
            for (int[] s : bad) {
                var enc = d.encoder();
                var a = Devices.upload(d, enc, ones(16));
                var y = Devices.upload(d, enc, ones(8));
                rejected(d, enc, y, 8, () -> g.gemv(enc, false, s[0], s[1], 1f, a, 0, 4, a, 0, 0f, y, 0));
            }
            for (boolean ta : new boolean[] {false, true}) {
                var enc = d.encoder();
                var a = Devices.upload(d, enc, ones(16));
                var y = Devices.upload(d, enc, ones(8));
                // op(A) 4x5 (A 5x4 when transposed) with its dense stride needs 20 elements; the buffer has 16
                rejected(d, enc, y, 8, () -> g.gemv(enc, ta, 4, 5, 1f, a, 0, ta ? 4 : 5, a, 0, 0f, y, 0));
                var enc2 = d.encoder();
                // a stride below the row length
                rejected(d, enc2, y, 8, () -> g.gemv(enc2, ta, 2, 3, 1f, a, 0, ta ? 1 : 2, a, 0, 0f, y, 0));
                var enc3 = d.encoder();
                // x (3 from 14) and y (9) too short
                rejected(d, enc3, y, 8, () -> g.gemv(enc3, ta, 2, 3, 1f, a, 0, 3, a, 14, 0f, y, 0));
                var enc4 = d.encoder();
                rejected(d, enc4, y, 8, () -> g.gemv(enc4, ta, 9, 1, 1f, a, 0, 1, a, 0, 0f, y, 0));
                var enc5 = d.encoder();
                rejected(d, enc5, y, 8, () -> g.gemv(enc5, ta, 2, 2, 1f, a, -1, 2, a, 0, 0f, y, 0));
            }
            // y is A or x
            var enc = d.encoder();
            var a = Devices.upload(d, enc, ones(64));
            var x = Devices.upload(d, enc, ones(8));
            rejected(d, enc, a, 64, () -> g.gemv(enc, false, 2, 2, 1f, a, 0, 2, x, 0, 0f, a, 32));
            var enc2 = d.encoder();
            rejected(d, enc2, x, 8, () -> g.gemv(enc2, false, 2, 2, 1f, a, 0, 2, x, 0, 0f, x, 4));
            var enc3 = d.encoder();
            rejected(d, enc3, x, 8, () -> g.gemv(enc3, false, 2, 2, 1f, a, 0, 2, null, 0, 0f, x, 4));
            assertThrows(IllegalArgumentException.class, () -> g.gemv(null, false, 1, 1, 1f, a, 0, 1, x, 0, 0f,
                    Devices.buffer(d, 1), 0));
        }
    }

    @Test
    void emptyWorkRecordsOnlyTheEmptyResult() {
        try (GpuDevice d = Devices.open("reference"); GpuBlasf b = new GpuBlasf(d); GpuGemmf g = new GpuGemmf(d)) {
            var enc = d.encoder();
            var buf = Devices.upload(d, enc, ones(4));
            var out = Devices.upload(d, enc, ones(6));
            var scratch = Devices.buffer(d, GpuBlasf.scratchSize(0));
            b.axpy(enc, 2f, buf, 0, Devices.buffer(d, 4), 0, 0);
            b.axpy(enc, 2f, buf, 4, buf, 4, 0);
            b.scal(enc, 2f, buf, 4, 0);
            g.gemv(enc, false, 0, 3, 1f, buf, 0, 3, buf, 0, 0f, Devices.buffer(d, 4), 0);
            g.gemv(enc, true, 0, 3, 1f, buf, 0, 0, buf, 0, 0f, Devices.buffer(d, 4), 0);
            // n == 0: the empty results, as on the CPU
            b.dot(enc, buf, 4, buf, 4, 0, out, 0, scratch, 0);
            b.sum(enc, buf, 4, 0, out, 1, scratch, 0);
            b.asum(enc, buf, 4, 0, out, 2, scratch, 0);
            b.nrm2(enc, buf, 4, 0, out, 3, scratch, 0);
            b.iamax(enc, buf, 4, 0, out, 4, scratch, 0);
            var read = enc.download(buf);
            var results = enc.download(out);
            Devices.run(d, enc);
            assertArrayEquals(ones(4), Devices.floats(read, 4));
            assertArrayEquals(new int[] {0, 0, 0, 0, -1, Float.floatToRawIntBits(1f)}, Devices.ints(results, 6));
        }
    }
}
