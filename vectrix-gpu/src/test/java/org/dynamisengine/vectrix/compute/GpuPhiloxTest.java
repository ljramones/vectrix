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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.vectrix.hash.Philox;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link GpuPhilox} against {@code Philox.word} and {@code Philox.uniform}: equal on every backend. */
class GpuPhiloxTest {

    private static final long[] SEEDS = {0L, 1L, -1L, 0x0123_4567_89AB_CDEFL};

    /** Starts at each word of a block, past 2^32, and wrapping past 2^64. */
    private static final long[] STARTS = {0L, 1L, 2L, 3L, 5L, (1L << 40) - 2, -3L, Long.MAX_VALUE - 1};

    private static final int[] COUNTS = {1, 2, 3, 4, 5, 63, 64, 65, 1000, 100_003};

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (long seed : SEEDS) {
                for (long start : STARTS) {
                    out.add(Arguments.of(backend, seed, start));
                }
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} seed={1} start={2}")
    @MethodSource("cases")
    void matchesPhilox(String backend, long seed, long start) {
        int total = 0;
        for (int n : COUNTS) {
            total += n + 3;
        }
        int[] words = new int[total];
        int[] uniforms = new int[total];
        try (GpuDevice device = Devices.open(backend); GpuPhilox philox = new GpuPhilox(device);
                var enc = device.encoder()) {
            int[] sentinel = new int[total];
            java.util.Arrays.fill(sentinel, 0xDEADBEEF);
            var gw = Devices.upload(device, enc, sentinel);
            var gu = Devices.upload(device, enc, sentinel);
            int off = 0;
            for (int n : COUNTS) {
                int at = off + 1; // one untouched element before and two after each fill
                philox.fillWords(enc, seed, start, gw, at, n);
                philox.fillUniform(enc, seed, start, gu, at, n);
                for (int i = 0; i < n + 3; i++) {
                    boolean in = i >= 1 && i <= n;
                    words[off + i] = in ? Philox.word(seed, start + i - 1) : 0xDEADBEEF;
                    uniforms[off + i] = in ? Float.floatToRawIntBits(Philox.uniform(seed, start + i - 1))
                            : 0xDEADBEEF;
                }
                off += n + 3;
            }
            var rw = enc.download(gw);
            var ru = enc.download(gu);
            Devices.run(device, enc);
            Bits.assertSame(words, Devices.ints(rw, total), backend + " words seed=" + seed + " start=" + start);
            Bits.assertSame(uniforms, Devices.ints(ru, total), backend + " uniform seed=" + seed + " start=" + start);
        }
    }
}
