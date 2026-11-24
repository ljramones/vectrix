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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.vectrix.nn.Formats;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link GpuFormats} against {@code nn.Formats} on every enabled backend.
 * <p>
 * The conversions, {@code quantizeInt8} and {@code dequantizeInt8} must give the Vectrix bits on every backend, with
 * no allowance for subnormals: the kernels never let a subnormal float reach the device's arithmetic where it would
 * change the result. The encoders sweep about a million float bit patterns spread over all 2^32 in every run, and
 * every pattern with {@code -Dvectrix.gpu.exhaustive=true}; the decoders always take every code. {@code dotInt8} and
 * {@code gemvInt8} use fused multiply-adds on the device's floats, so on hardware a result may be 0 where Vectrix has
 * a subnormal ({@link Bits}); their inputs here are normal floats, zeros, infinities and NaN.
 */
class GpuFormatsTest {

    /** The encoders: bfloat16, and the two FP8 formats with and without saturation. */
    enum Kind {
        BF16, E4M3, E4M3_SAT, E5M2, E5M2_SAT;

        /** Bytes per code. */
        int bytes() {
            return this == BF16 ? 2 : 1;
        }

        /** The Vectrix code of the float with bits {@code bits}, zero-extended. */
        int cpu(int bits) {
            float v = Float.intBitsToFloat(bits);
            return switch (this) {
                case BF16 -> Formats.toBf16(v) & 0xFFFF;
                case E4M3 -> Formats.toE4M3(v, false) & 0xFF;
                case E4M3_SAT -> Formats.toE4M3(v, true) & 0xFF;
                case E5M2 -> Formats.toE5M2(v, false) & 0xFF;
                case E5M2_SAT -> Formats.toE5M2(v, true) & 0xFF;
            };
        }

        void record(GpuFormats f, GpuCommandEncoder enc, GpuBuffer x, long xOff, GpuBuffer out, long off, int n) {
            switch (this) {
                case BF16 -> f.toBf16(enc, x, xOff, out, off, n);
                case E4M3 -> f.toE4M3(enc, x, xOff, out, off, n, false);
                case E4M3_SAT -> f.toE4M3(enc, x, xOff, out, off, n, true);
                case E5M2 -> f.toE5M2(enc, x, xOff, out, off, n, false);
                case E5M2_SAT -> f.toE5M2(enc, x, xOff, out, off, n, true);
                default -> throw new AssertionError(this);
            }
        }

        /** The code at element {@code i} of packed little-endian bytes. */
        int code(ByteBuffer b, long i) {
            return bytes() == 2 ? b.getShort(Math.toIntExact(2 * i)) & 0xFFFF : b.get(Math.toIntExact(i)) & 0xFF;
        }
    }

    private static final int CHUNK = 1 << 22;

    static Stream<Arguments> kinds() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (Kind k : Kind.values()) {
                out.add(Arguments.of(backend, k));
            }
        }
        return out.stream();
    }

    static Stream<Arguments> backends() {
        return Devices.names().stream().map(Arguments::of);
    }

    /** dotInt8 and gemvInt8 accumulate with fma, so they match Vectrix only where fma is fused. */
    static Stream<Arguments> fusedBackends() {
        return Devices.namesWithFusedFma().stream().map(Arguments::of);
    }

    static ByteBuffer le(byte[] b) {
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
    }

    static byte[] bytesOf(int[] words) {
        ByteBuffer b = ByteBuffer.allocate(words.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.asIntBuffer().put(words);
        return b.array();
    }

    /** Packs {@code codes} little-endian into words, padded with {@code pad} bytes. */
    static int[] pack(byte[] codes, int pad) {
        int[] w = new int[(codes.length + 3) / 4];
        ByteBuffer b = ByteBuffer.allocate(w.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        Arrays.fill(b.array(), (byte) pad);
        b.put(0, codes);
        b.asIntBuffer().get(w);
        return w;
    }

    /** Every float bit pattern (exhaustive mode) or a stride-4099 sweep, through each encoder. */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("kinds")
    void encodesFloatPatterns(String backend, Kind kind) {
        boolean all = Boolean.getBoolean("vectrix.gpu.exhaustive");
        long stride = all ? 1 : 4099; // a prime: ~1M patterns spread over every exponent and sign
        long total = all ? 1L << 32 : ((1L << 32) + stride - 1) / stride;
        AtomicLong bad = new AtomicLong();
        StringBuilder first = new StringBuilder();
        try (GpuDevice device = Devices.open(backend); GpuFormats formats = new GpuFormats(device);
                GpuBuffer x = Devices.buffer(device, CHUNK);
                GpuBuffer out = Devices.buffer(device, CHUNK * kind.bytes() / 4)) {
            int[] bits = new int[CHUNK];
            for (long done = 0; done < total; done += CHUNK) {
                int count = (int) Math.min(CHUNK, total - done);
                long base = done * stride;
                for (int i = 0; i < count; i++) {
                    bits[i] = (int) (base + i * stride);
                }
                try (var enc = device.encoder()) {
                    enc.upload(x, bytesOf(bits));
                    kind.record(formats, enc, x, 0, out, 0, count);
                    var read = enc.download(out);
                    Devices.run(device, enc);
                    ByteBuffer got = le(read.bytes());
                    IntStream.range(0, count).parallel().forEach(i -> {
                        int b = bits[i];
                        int expected = kind.cpu(b);
                        int actual = kind.code(got, i);
                        if (expected != actual && bad.getAndIncrement() < 5) {
                            synchronized (first) {
                                first.append(String.format("%n  x=%s (0x%08x): expected 0x%x, got 0x%x",
                                        Float.intBitsToFloat(b), b, expected, actual));
                            }
                        }
                    });
                }
            }
        }
        if (bad.get() > 0) {
            fail(backend + " " + kind + ": " + bad.get() + " of " + total + " patterns differ" + first);
        }
    }

    /** Signed zeros, infinities, NaNs, every FP8 value, the midpoints between them and their neighbours. */
    static int[] specialPatterns() {
        List<Integer> v = new ArrayList<>();
        int[] fixed = {0, 0x80000000, 0x7F800000, 0xFF800000, 0x7FC00000, 0xFFC00000, 0x7F800001, 0xFF800001,
            0x7FBFFFFF, 0xFFFFFFFF, 0x00000001, 0x80000001, 0x007FFFFF, 0x00800000, 0x7F7FFFFF, 0xFF7FFFFF,
            0x7F7F8000, 0x7F7F7FFF, 0x3F808000, 0x3F818000, 0x3F808001, 0x3F817FFF, 0xBF808000, 0xBF818000,
            0x00008000, 0x00018000, 0x7F7F0000};
        for (int f : fixed) {
            v.add(f);
        }
        for (boolean e5m2 : new boolean[] {false, true}) {
            int finite = e5m2 ? 124 : 127;
            for (int k = 0; k < finite; k++) {
                float a = e5m2 ? Formats.fromE5M2((byte) k) : Formats.fromE4M3((byte) k);
                // the next value, or the virtual one past the largest
                float b = k + 1 < finite ? (e5m2 ? Formats.fromE5M2((byte) (k + 1)) : Formats.fromE4M3((byte) (k + 1)))
                        : 2 * a - (e5m2 ? Formats.fromE5M2((byte) (k - 1)) : Formats.fromE4M3((byte) (k - 1)));
                float mid = (a + b) / 2;
                for (float s : new float[] {a, mid, Math.nextUp(mid), Math.nextDown(mid), b, Math.nextUp(b),
                    Math.nextDown(a)}) {
                    v.add(Float.floatToRawIntBits(s));
                    v.add(Float.floatToRawIntBits(-s));
                }
            }
        }
        return v.stream().mapToInt(Integer::intValue).toArray();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("kinds")
    void encodesSpecialValues(String backend, Kind kind) {
        int[] bits = specialPatterns();
        try (GpuDevice device = Devices.open(backend); GpuFormats formats = new GpuFormats(device);
                var enc = device.encoder()) {
            GpuBuffer x = Devices.upload(device, enc, bits);
            GpuBuffer out = Devices.buffer(device, bits.length);
            kind.record(formats, enc, x, 0, out, 0, bits.length);
            var read = enc.download(out);
            Devices.run(device, enc);
            ByteBuffer got = le(read.bytes());
            int[] expected = new int[bits.length];
            int[] actual = new int[bits.length];
            for (int i = 0; i < bits.length; i++) {
                expected[i] = kind.cpu(bits[i]);
                actual[i] = kind.code(got, i);
            }
            Bits.assertSame(expected, actual, backend + " " + kind + " special values");
        }
    }

    /** Odd and unaligned offsets and counts: the elements around the range keep their sentinel bytes. */
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("kinds")
    void encodesAtAnyOffset(String backend, Kind kind) {
        SplittableRandom r = new SplittableRandom(kind.ordinal());
        int[] counts = {1, 2, 3, 4, 5, 7, 33, 130, 1000};
        int[] offsets = {0, 1, 2, 3, 5, 6};
        try (GpuDevice device = Devices.open(backend); GpuFormats formats = new GpuFormats(device)) {
            for (int n : counts) {
                for (int off : offsets) {
                    int xOff = 3;
                    int[] bits = new int[xOff + n + 2];
                    for (int i = 0; i < bits.length; i++) {
                        bits[i] = Float.floatToRawIntBits((float) (r.nextDouble() * 1000 - 500));
                    }
                    int elements = off + n + 9;
                    int words = (elements * kind.bytes() + 3) / 4;
                    byte[] expected = new byte[words * 4];
                    Arrays.fill(expected, (byte) 0xA5);
                    ByteBuffer e = le(expected);
                    for (int i = 0; i < n; i++) {
                        int c = kind.cpu(bits[xOff + i]);
                        if (kind.bytes() == 2) {
                            e.putShort(2 * (off + i), (short) c);
                        } else {
                            e.put(off + i, (byte) c);
                        }
                    }
                    try (var enc = device.encoder()) {
                        GpuBuffer x = Devices.upload(device, enc, bits);
                        int[] sentinel = new int[words];
                        Arrays.fill(sentinel, 0xA5A5A5A5);
                        GpuBuffer out = Devices.upload(device, enc, sentinel);
                        kind.record(formats, enc, x, xOff, out, off, n);
                        var read = enc.download(out);
                        Devices.run(device, enc);
                        int[] exp = new int[words];
                        e.asIntBuffer().get(exp);
                        Bits.assertSame(exp, Devices.ints(read, words), backend + " " + kind + " n=" + n + " off="
                                + off);
                    }
                }
            }
        }
    }

    /** fromBf16 of all 2^16 patterns and fromE4M3, fromE5M2 of all 256 codes, at unaligned offsets. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void decodesEveryCode(String backend) {
        try (GpuDevice device = Devices.open(backend); GpuFormats formats = new GpuFormats(device);
                var enc = device.encoder()) {
            int hOff = 1;
            byte[] halves = new byte[2 * (hOff + 65536 + 1)];
            for (int i = 0; i < 65536; i++) {
                le(halves).putShort(2 * (hOff + i), (short) i);
            }
            GpuBuffer h = Devices.upload(device, enc, pack(halves, 0x5A));
            int xOff = 2;
            GpuBuffer xh = Devices.buffer(device, xOff + 65536);
            formats.fromBf16(enc, h, hOff, xh, xOff, 65536);
            int cOff = 3;
            byte[] codes = new byte[cOff + 256];
            for (int i = 0; i < 256; i++) {
                codes[cOff + i] = (byte) i;
            }
            GpuBuffer c = Devices.upload(device, enc, pack(codes, 0x5A));
            GpuBuffer x4 = Devices.buffer(device, 256 + 1);
            GpuBuffer x5 = Devices.buffer(device, 256 + 1);
            formats.fromE4M3(enc, c, cOff, x4, 1, 256);
            formats.fromE5M2(enc, c, cOff, x5, 1, 256);
            var rh = enc.download(xh);
            var r4 = enc.download(x4);
            var r5 = enc.download(x5);
            Devices.run(device, enc);
            int[] eh = new int[65536];
            int[] e4 = new int[256];
            int[] e5 = new int[256];
            for (int i = 0; i < 65536; i++) {
                eh[i] = Float.floatToRawIntBits(Formats.fromBf16((short) i));
            }
            for (int i = 0; i < 256; i++) {
                e4[i] = Float.floatToRawIntBits(Formats.fromE4M3((byte) i));
                e5[i] = Float.floatToRawIntBits(Formats.fromE5M2((byte) i));
            }
            Bits.assertSame(eh, Arrays.copyOfRange(Devices.ints(rh, xOff + 65536), xOff, xOff + 65536),
                    backend + " fromBf16");
            Bits.assertSame(e4, Arrays.copyOfRange(Devices.ints(r4, 257), 1, 257), backend + " fromE4M3");
            Bits.assertSame(e5, Arrays.copyOfRange(Devices.ints(r5, 257), 1, 257), backend + " fromE5M2");
        }
    }

    /** A block's values: magnitudes around 2^e, signs and mantissas random, one element exactly ±2^e·(1 + f). */
    static void block(SplittableRandom r, float[] x, int at, int e) {
        for (int j = 0; j < Formats.QBLOCK; j++) {
            x[at + j] = (float) (Math.scalb(r.nextDouble() * 2 - 1, e));
        }
    }

    /** Raw scale bits and codes from the CPU, block by block (scale NaN and codes 0 for a non-finite block). */
    static void quantizeCpu(float[] x, int xOff, int n, byte[] q, int qOff, int[] scaleBits, int sOff) {
        float[] s = new float[1];
        for (int b = 0; b < n / Formats.QBLOCK; b++) {
            int base = xOff + b * Formats.QBLOCK;
            boolean finite = true;
            for (int j = 0; j < Formats.QBLOCK; j++) {
                finite &= Float.isFinite(x[base + j]);
            }
            if (finite) {
                Formats.quantizeInt8(x, base, Formats.QBLOCK, q, qOff + b * Formats.QBLOCK, s, 0);
                scaleBits[sOff + b] = Float.floatToRawIntBits(s[0]);
            } else {
                Arrays.fill(q, qOff + b * Formats.QBLOCK, qOff + (b + 1) * Formats.QBLOCK, (byte) 0);
                scaleBits[sOff + b] = 0x7FC00000;
            }
        }
    }

    /**
     * Blocks over the whole float range, subnormal scales (largest magnitudes from 2^-149 up past 2^-118, the
     * kernel's rescaling boundary, and around 127·2^-126), all-zero and signed-zero blocks, exact ties of
     * {@code x / scale}, and two non-finite blocks, at unaligned code offsets and past a group's 1024 codes.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void quantizeMatchesFormats(String backend) {
        SplittableRandom r = new SplittableRandom(42);
        int blocks = 200;
        int n = blocks * Formats.QBLOCK;
        int xOff = 5;
        float[] x = new float[xOff + n + 3];
        for (int b = 0; b < blocks; b++) {
            int at = xOff + b * Formats.QBLOCK;
            if (b < 60) {
                block(r, x, at, -149 + r.nextInt(300)); // from subnormal to near the largest float
            } else if (b < 120) {
                block(r, x, at, -150 + r.nextInt(36)); // subnormal scales: max|x| below 2^-114
            } else if (b < 130) {
                // max|x| next to 127·2^-126, where the scale crosses into the normals
                float amax = Float.intBitsToFloat(Float.floatToRawIntBits(127 * Float.MIN_NORMAL) + r.nextInt(64) - 32);
                block(r, x, at, -120);
                x[at + r.nextInt(Formats.QBLOCK)] = r.nextBoolean() ? amax : -amax;
            } else if (b < 140) {
                // max|x| next to 2^-118
                float amax = Float.intBitsToFloat(0x04800000 + r.nextInt(64) - 32);
                block(r, x, at, -119);
                x[at + r.nextInt(Formats.QBLOCK)] = r.nextBoolean() ? amax : -amax;
            } else if (b == 140) {
                Arrays.fill(x, at, at + Formats.QBLOCK, 0f);
            } else if (b == 141) {
                for (int j = 0; j < Formats.QBLOCK; j++) {
                    x[at + j] = (j & 1) == 0 ? -0f : 0f;
                }
            } else if (b < 150) {
                // scale 1 (max 127) and halves: x / scale is an exact tie
                for (int j = 0; j < Formats.QBLOCK; j++) {
                    x[at + j] = r.nextInt(-126, 126) + 0.5f;
                }
                x[at] = r.nextBoolean() ? 127f : -127f;
            } else if (b < 155) {
                // a power-of-two scale with ties, deep in the subnormal scales
                float scale = Math.scalb(1f, -140 - (b - 150));
                for (int j = 0; j < Formats.QBLOCK; j++) {
                    x[at + j] = (r.nextInt(-120, 120) + 0.5f) * scale;
                }
                x[at] = 127 * scale;
            } else if (b == 155) {
                Arrays.fill(x, at, at + Formats.QBLOCK, Float.MIN_VALUE);
                x[at + 3] = -Float.MIN_VALUE;
            } else if (b == 156) {
                block(r, x, at, 127);
                x[at + 1] = Float.MAX_VALUE;
            } else if (b == 157) {
                block(r, x, at, 3);
                x[at + 7] = Float.NaN;
            } else if (b == 158) {
                block(r, x, at, 3);
                x[at + 31] = Float.NEGATIVE_INFINITY;
            } else {
                block(r, x, at, -20 + r.nextInt(40));
            }
        }
        for (int qOff : new int[] {0, 1, 2, 3, 7}) {
            int sOff = 2;
            byte[] q = new byte[qOff + n + 6];
            Arrays.fill(q, (byte) 0xA5);
            int[] scales = new int[sOff + blocks + 1];
            Arrays.fill(scales, 0xDEADBEEF);
            int[] gq = pack(q, 0xA5);
            int[] gs = scales.clone();
            quantizeCpu(x, xOff, n, q, qOff, scales, sOff);
            try (GpuDevice device = Devices.open(backend); GpuFormats formats = new GpuFormats(device);
                    var enc = device.encoder()) {
                GpuBuffer bx = Devices.upload(device, enc, x);
                GpuBuffer bq = Devices.upload(device, enc, gq);
                GpuBuffer bs = Devices.upload(device, enc, gs);
                formats.quantizeInt8(enc, bx, xOff, n, bq, qOff, bs, sOff);
                var rq = enc.download(bq);
                var rs = enc.download(bs);
                Devices.run(device, enc);
                Bits.assertSame(scales, Devices.ints(rs, scales.length), backend + " scales qOff=" + qOff);
                Bits.assertSame(pack(q, 0xA5), Devices.ints(rq, gq.length), backend + " codes qOff=" + qOff);
            }
        }
        // the CPU method rejects the non-finite blocks the GPU marks with a NaN scale
        assertThrows(IllegalArgumentException.class, () -> Formats.quantizeInt8(x, xOff, n, new byte[n], 0,
                new float[blocks], 0));
    }

    /** Every code against normal, subnormal, signed-zero, infinite and NaN scales, at unaligned offsets. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void dequantizeMatchesFormats(String backend) {
        SplittableRandom r = new SplittableRandom(7);
        List<Float> s = new ArrayList<>(List.of(0f, -0f, 1f, -1f, Float.MIN_VALUE, -Float.MIN_VALUE,
                Float.intBitsToFloat(0x007FFFFF), Float.intBitsToFloat(0x807FFFFF), Float.MIN_NORMAL,
                Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, 1f / 127,
                Float.intBitsToFloat(0x00020000), Float.intBitsToFloat(0x00010001), 3e38f / 127));
        for (int i = 0; i < 40; i++) {
            s.add(Float.intBitsToFloat(r.nextInt(1, 0x00800000) | (r.nextBoolean() ? 0x80000000 : 0)));
            s.add((float) Math.scalb(r.nextDouble() * 2 - 1, r.nextInt(-130, 120)));
        }
        int codesPerScale = 256;
        int blocksPerScale = codesPerScale / Formats.QBLOCK;
        int blocks = s.size() * blocksPerScale;
        int n = blocks * Formats.QBLOCK;
        float[] scales = new float[1 + blocks];
        for (int b = 0; b < blocks; b++) {
            scales[1 + b] = s.get(b / blocksPerScale);
        }
        for (int qOff : new int[] {0, 3}) {
            byte[] q = new byte[qOff + n];
            for (int i = 0; i < n; i++) {
                q[qOff + i] = (byte) i;
            }
            int xOff = 4;
            float[] expected = new float[xOff + n + 2];
            Arrays.fill(expected, 1f);
            Formats.dequantizeInt8(q, qOff, scales, 1, expected, xOff, n);
            try (GpuDevice device = Devices.open(backend); GpuFormats formats = new GpuFormats(device);
                    var enc = device.encoder()) {
                GpuBuffer bq = Devices.upload(device, enc, pack(q, 0));
                GpuBuffer bs = Devices.upload(device, enc, scales);
                float[] ones = new float[expected.length];
                Arrays.fill(ones, 1f);
                GpuBuffer bx = Devices.upload(device, enc, ones);
                formats.dequantizeInt8(enc, bq, qOff, bs, 1, bx, xOff, n);
                var read = enc.download(bx);
                Devices.run(device, enc);
                Bits.assertSame(expected, Devices.floats(read, expected.length), false, backend + " dequantize qOff="
                        + qOff);
            }
        }
    }

    /** {rows, n}: one row, odd row counts, more rows than grid columns, long rows, and n = 0. */
    private static final int[][] GEMV_SHAPES = {{1, 32}, {3, 64}, {8, 96}, {9, 1024}, {70, 160}, {2, 4096}, {5, 0},
        {1, 32 * 1000}};

    static Stream<Arguments> gemvCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.namesWithFusedFma()) {
            for (int[] s : GEMV_SHAPES) {
                out.add(Arguments.of(backend, s[0], s[1]));
            }
        }
        return out.stream();
    }

    /** Random codes, scales and vectors; an unaligned code offset, so every row starts mid-word. */
    @ParameterizedTest(name = "{0} {1}x{2}")
    @MethodSource("gemvCases")
    void gemvMatchesFormats(String backend, int m, int n) {
        SplittableRandom r = new SplittableRandom(m * 31L + n);
        int qOff = 3;
        int sOff = 1;
        int xOff = 2;
        int yOff = 5;
        byte[] q = new byte[qOff + m * n + 1];
        for (int i = 0; i < q.length; i++) {
            q[i] = (byte) r.nextInt(-127, 128);
        }
        float[] scales = new float[sOff + m * (n / Formats.QBLOCK) + 1];
        for (int i = 0; i < scales.length; i++) {
            scales[i] = (float) Math.scalb(r.nextDouble() + 0.5, r.nextInt(-12, 4));
        }
        float[] x = new float[xOff + n + 1];
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) (r.nextDouble() * 8 - 4);
        }
        checkGemv(backend, m, n, q, qOff, scales, sOff, x, xOff, yOff, "random");
    }

    /** Infinities, NaN, signed zeros and zero scales in the inputs. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("fusedBackends")
    void gemvSpecialValuesMatchFormats(String backend) {
        int m = 6;
        int n = 64;
        SplittableRandom r = new SplittableRandom(3);
        byte[] q = new byte[m * n];
        for (int i = 0; i < q.length; i++) {
            q[i] = (byte) r.nextInt(-127, 128);
        }
        float[] scales = new float[m * 2];
        Arrays.fill(scales, 0.25f);
        float[] x = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = (i & 1) == 0 ? -0f : 0f;
        }
        Arrays.fill(q, 0, n, (byte) 0); // row 0: -0·0 terms, a +0 result
        q[n + 5] = 0;
        x[5] = Float.POSITIVE_INFINITY; // row 1 and on: ±infinity, and 0·infinity = NaN in row 0
        scales[2 * 2] = 0f;             // row 2: a zero scale
        scales[3 * 2 + 1] = Float.NaN;  // row 3: a NaN scale
        x[40] = Float.NaN;
        checkGemv(backend, m, n, q, 0, scales, 0, x, 0, 0, "special");
        float[] negZero = new float[n];
        Arrays.fill(negZero, -0f);
        byte[] ones = new byte[n];
        Arrays.fill(ones, (byte) 1);
        checkGemv(backend, 1, n, ones, 0, new float[] {1f, 1f}, 0, negZero, 0, 0, "minus zeros");
    }

    static void checkGemv(String backend, int m, int n, byte[] q, int qOff, float[] scales, int sOff, float[] x,
            int xOff, int yOff, String what) {
        float[] expected = new float[yOff + m + 2];
        Arrays.fill(expected, 1f);
        Formats.gemvInt8(m, n, q, qOff, scales, sOff, x, xOff, expected, yOff);
        float[] dot = {1f, 1f, Formats.dotInt8(q, qOff, scales, sOff, x, xOff, n), 1f};
        try (GpuDevice device = Devices.open(backend); GpuFormats formats = new GpuFormats(device);
                var enc = device.encoder()) {
            GpuBuffer bq = Devices.upload(device, enc, pack(q, 0));
            GpuBuffer bs = Devices.upload(device, enc, scales);
            GpuBuffer bx = Devices.upload(device, enc, x);
            float[] ones = new float[expected.length];
            Arrays.fill(ones, 1f);
            GpuBuffer by = Devices.upload(device, enc, ones);
            GpuBuffer bd = Devices.upload(device, enc, new float[] {1f, 1f, 1f, 1f});
            formats.gemvInt8(enc, m, n, bq, qOff, bs, sOff, bx, xOff, by, yOff);
            formats.dotInt8(enc, bq, qOff, bs, sOff, bx, xOff, n, bd, 2);
            var ry = enc.download(by);
            var rd = enc.download(bd);
            Devices.run(device, enc);
            boolean hw = Devices.hardware(backend);
            Bits.assertSame(expected, Devices.floats(ry, expected.length), hw, backend + " gemvInt8 " + what + " " + m
                    + "x" + n);
            Bits.assertSame(dot, Devices.floats(rd, 4), hw, backend + " dotInt8 " + what + " n=" + n);
        }
    }
}
