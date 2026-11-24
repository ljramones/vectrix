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
import java.util.SplittableRandom;
import java.util.stream.Stream;

import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.affine.PackedAffineKernels;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.soa.AABBSoA;
import org.dynamisengine.vectrix.soa.SkinningKernels;
import org.dynamisengine.vectrix.soa.TransformSoA;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link GpuPackedAffineKernels} against {@code PackedAffineKernels}, bit for bit, on every enabled backend. */
class GpuPackedAffineKernelsTest {

    private static final float UNWRITTEN = 1.5f;

    static Stream<Arguments> counts() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (int n : EngineData.COUNTS) {
                out.add(Arguments.of(backend, n));
            }
        }
        return out.stream();
    }

    static Stream<String> backends() {
        return Devices.names().stream();
    }

    /** Rotations (unit, plus a few non-unit and signed-zero ones) as {@code x, y, z, w} quadruples. */
    static float[] rotations(SplittableRandom r, int n) {
        float[] q = new float[4 * n];
        for (int i = 0; i < n; i++) {
            float[] u = EngineData.unitQuaternion(r);
            if (i % 11 == 3) {
                u = new float[] {-0f, 0f, -0f, 1f};
            } else if (i % 11 == 7) {
                u = new float[] {u[0] * 2.5f, u[1] * 2.5f, u[2] * 2.5f, u[3] * 2.5f}; // scales too
            }
            System.arraycopy(u, 0, q, 4 * i, 4);
        }
        return q;
    }

    @ParameterizedTest(name = "{0} n={1}")
    @MethodSource("counts")
    void quatTranslationMatches(String backend, int n) {
        SplittableRandom r = new SplittableRandom(n * 3L);
        float[] q = rotations(r, n);
        float[][] t = new float[3][n];
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < 3; c++) {
                t[c][i] = i % 13 == 0 ? -0f : EngineData.uniform(r, -100, 100);
            }
        }
        Quaternionf[] rotation = new Quaternionf[n];
        for (int i = 0; i < n; i++) {
            rotation[i] = new Quaternionf(q[4 * i], q[4 * i + 1], q[4 * i + 2], q[4 * i + 3]);
        }
        PackedAffineArray expected = new PackedAffineArray(n);
        PackedAffineKernels.quatTranslationToPackedAffineBatch(rotation, t[0], t[1], t[2], expected, n);
        int qOff = 2;
        int tOff = 1;
        int tStride = n + 4;
        int outOff = 3;
        float[] qb = new float[qOff + 4 * n + 1];
        System.arraycopy(q, 0, qb, qOff, q.length);
        float[] tb = EngineData.pack(t, n, tOff, tStride, 2, Float.NaN);
        float[] out = EngineData.filled(outOff + 12 * n + 5, UNWRITTEN);
        float[] want = out.clone();
        System.arraycopy(expected.raw(), 0, want, outOff, 12 * n);
        try (GpuDevice device = Devices.open(backend); GpuPackedAffineKernels k = new GpuPackedAffineKernels(device);
                var enc = device.encoder()) {
            var gq = Devices.upload(device, enc, qb);
            var gt = Devices.upload(device, enc, tb);
            var go = Devices.upload(device, enc, out);
            k.quatTranslationToPackedAffineBatch(enc, gq, qOff, gt, tOff, tStride, go, outOff, n);
            var read = enc.download(go);
            Devices.run(device, enc);
            Bits.assertSame(want, Devices.floats(read, out.length), Devices.hardware(backend),
                    backend + " quatTranslation n=" + n);
        }
    }

    /** Transforms of every kind: TRS palettes (scaled and not), arbitrary 3×4 matrices with shear. */
    static PackedAffineArray transforms(SplittableRandom r, int entries) {
        TransformSoA trs = EngineData.transforms(r, entries, EngineData.Scale.NON_UNIFORM);
        float[] raw = new float[12 * entries];
        SkinningKernels.buildMatrixPalette12(trs, raw, entries);
        for (int e = 0; e < entries; e += 5) {
            for (int c = 0; c < 12; c++) {
                raw[12 * e + c] = EngineData.uniform(r, -4, 4);
            }
        }
        for (int e = 2; e < entries; e += 9) {
            PackedAffineArray id = new PackedAffineArray(1);
            System.arraycopy(id.raw(), 0, raw, 12 * e, 12);
        }
        return new PackedAffineArray(raw);
    }

    /** Boxes, some degenerate, inverted or with NaN and infinite bounds. */
    static AABBSoA boxes(SplittableRandom r, int entries) {
        AABBSoA b = new AABBSoA(entries);
        for (int i = 0; i < entries; i++) {
            float cx = EngineData.uniform(r, -50, 50);
            float cy = EngineData.uniform(r, -50, 50);
            float cz = EngineData.uniform(r, -50, 50);
            float ex = i % 17 == 0 ? 0f : EngineData.uniform(r, 0, 8);
            float ey = EngineData.uniform(r, 0, 8);
            float ez = i % 19 == 0 ? -1f : EngineData.uniform(r, 0, 8);
            b.set(i, cx - ex, cy - ey, cz - ez, cx + ex, cy + ey, cz + ez);
        }
        if (entries > 30) {
            b.minX[23] = Float.NaN;
            b.maxY[29] = Float.POSITIVE_INFINITY;
            b.minZ[30] = Float.NEGATIVE_INFINITY;
            b.set(24, -0f, -0f, -0f, 0f, 0f, 0f);
        }
        return b;
    }

    /** A shuffled permutation of a subset of the entries, with repeats when {@code repeats}, length {@code n}. */
    static int[] order(SplittableRandom r, int n, int entries, boolean repeats) {
        int[] o = new int[n];
        if (repeats) {
            for (int i = 0; i < n; i++) {
                o[i] = r.nextInt(entries);
            }
            return o;
        }
        int[] perm = java.util.stream.IntStream.range(0, entries).toArray();
        for (int i = entries - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int s = perm[i];
            perm[i] = perm[j];
            perm[j] = s;
        }
        System.arraycopy(perm, 0, o, 0, n);
        return o;
    }

    static int[] at(int[] v, int off) {
        int[] a = new int[off + v.length + 2];
        System.arraycopy(v, 0, a, off, v.length);
        return a;
    }

    /** Runs the box kernel; {@code cpuOrder} is what the CPU processes ({@code order} without skipped indices). */
    static void checkAabb(String backend, int entries, PackedAffineArray transforms, AABBSoA in, int[] order,
            int[] cpuOrder, String what) {
        int n = order.length;
        AABBSoA expected = new AABBSoA(entries);
        int outOff = 2;
        int outStride = entries + 3;
        float[] out = EngineData.filled(outOff + 5 * outStride + entries + 4, UNWRITTEN);
        EngineModels.unpack(out, outOff, outStride, EngineModels.fields(expected), entries);
        PackedAffineKernels.transformAabbPackedAffineBatch(transforms, cpuOrder, in.minX, in.minY, in.minZ, in.maxX,
                in.maxY, in.maxZ, expected.minX, expected.minY, expected.minZ, expected.maxX, expected.maxY,
                expected.maxZ, cpuOrder.length);
        float[] want = out.clone();
        EngineModels.pack(EngineModels.fields(expected), entries, want, outOff, outStride);
        int tOff = 4;
        float[] tb = EngineData.filled(tOff + 12 * entries + 2, Float.NaN);
        System.arraycopy(transforms.raw(), 0, tb, tOff, 12 * entries);
        int inOff = 1;
        int inStride = entries + 6;
        float[] ib = EngineData.pack(EngineModels.fields(in), entries, inOff, inStride, 2, Float.NaN);
        int orderOff = 3;
        try (GpuDevice device = Devices.open(backend); GpuPackedAffineKernels k = new GpuPackedAffineKernels(device);
                var enc = device.encoder()) {
            var gt = Devices.upload(device, enc, tb);
            var gord = Devices.upload(device, enc, at(order, orderOff));
            var gi = Devices.upload(device, enc, ib);
            var go = Devices.upload(device, enc, out);
            k.transformAabbPackedAffineBatch(enc, entries, gt, tOff, gord, orderOff, gi, inOff, inStride, go, outOff,
                    outStride, n);
            var read = enc.download(go);
            Devices.run(device, enc);
            Bits.assertSame(want, Devices.floats(read, out.length), Devices.hardware(backend), what);
        }
    }

    @ParameterizedTest(name = "{0} n={1}")
    @MethodSource("counts")
    void transformAabbMatches(String backend, int n) {
        SplittableRandom r = new SplittableRandom(n * 5L);
        boolean repeats = n % 2 == 0;
        int entries = repeats ? Math.max(1, n / 2) : n + 3;
        PackedAffineArray transforms = transforms(r, entries);
        AABBSoA in = boxes(r, entries);
        int[] order = order(r, n, entries, repeats);
        checkAabb(backend, entries, transforms, in, order, order, backend + " transformAabb n=" + n);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void transformAabbSkipsIndicesOutOfRange(String backend) {
        SplittableRandom r = new SplittableRandom(12);
        int entries = 300;
        int n = 250;
        PackedAffineArray transforms = transforms(r, entries);
        AABBSoA in = boxes(r, entries);
        int[] order = order(r, n, entries, false);
        int[] bad = {-1, entries, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int i = 0; i < n; i += 9) {
            order[i] = bad[(i / 9) % bad.length];
        }
        int[] cpuOrder = java.util.Arrays.stream(order).filter(v -> v >= 0 && v < entries).toArray();
        checkAabb(backend, entries, transforms, in, order, cpuOrder, backend + " transformAabb, invalid indices");
    }

    /** Runs the upload kernel and compares the output words; {@code order} may hold indices that are skipped. */
    static void checkUpload(String backend, int transformCount, float[] raw, int[] order, String what) {
        int n = order.length;
        int outOff = 5;
        float[] out = EngineData.filled(outOff + 12 * n + 3, UNWRITTEN);
        float[] want = out.clone();
        PackedAffineArray transforms = new PackedAffineArray(raw);
        for (int i = 0; i < n; i++) {
            if (order[i] >= 0 && order[i] < transformCount) {
                PackedAffineKernels.uploadPackedAffine(transforms, new int[] {order[i]}, want, outOff + 12 * i, 1);
            }
        }
        int tOff = 7;
        float[] tb = EngineData.filled(tOff + raw.length + 1, Float.NaN);
        System.arraycopy(raw, 0, tb, tOff, raw.length);
        int orderOff = 2;
        try (GpuDevice device = Devices.open(backend); GpuPackedAffineKernels k = new GpuPackedAffineKernels(device);
                var enc = device.encoder()) {
            var gt = Devices.upload(device, enc, tb);
            var gord = Devices.upload(device, enc, at(order, orderOff));
            var go = Devices.upload(device, enc, out);
            k.uploadPackedAffine(enc, gt, tOff, transformCount, gord, orderOff, go, outOff, n);
            var read = enc.download(go);
            Devices.run(device, enc);
            int[] wantBits = new int[want.length];
            for (int i = 0; i < want.length; i++) {
                wantBits[i] = Float.floatToRawIntBits(want[i]);
            }
            Bits.assertSame(wantBits, Devices.ints(read, out.length), what);
        }
    }

    @ParameterizedTest(name = "{0} n={1}")
    @MethodSource("counts")
    void uploadMatches(String backend, int n) {
        SplittableRandom r = new SplittableRandom(n * 11L);
        boolean repeats = n % 2 == 1;
        int transformCount = repeats ? Math.max(1, n / 3) : n + 1;
        float[] raw = transforms(r, transformCount).raw();
        // every bit pattern travels: NaN payloads, subnormals, signed zeros
        int[] odd = {0x7FC00001, 0xFFBFFFFF, 0x00000001, 0x807FFFFF, 0x80000000, 0x7F800000};
        for (int i = 0; i < Math.min(odd.length, raw.length); i++) {
            raw[i] = Float.intBitsToFloat(odd[i]);
        }
        checkUpload(backend, transformCount, raw, order(r, n, transformCount, repeats), backend + " upload n=" + n);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void uploadSkipsIndicesOutOfRange(String backend) {
        SplittableRandom r = new SplittableRandom(21);
        int transformCount = 40;
        int[] order = order(r, 100, transformCount, true);
        order[0] = -1;
        order[17] = transformCount;
        order[99] = Integer.MIN_VALUE;
        checkUpload(backend, transformCount, transforms(r, transformCount).raw(), order,
                backend + " upload, invalid indices");
    }
}
