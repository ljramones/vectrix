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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Function;

import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.GpuReadback;

/**
 * Runs a recording on a backend over host arrays and compares the results with Vectrix: the arrays are uploaded (one
 * buffer per distinct array, so passing the same array twice aliases), the recording runs, and every float array is
 * downloaded and compared bit for bit with its expected value.
 */
final class RowsFixture {

    /** Shapes {rows, n}: short rows, block boundaries, deep block trees, more rows than grid columns, the limit. */
    static final int[][] SHAPES = {
        {1, 1}, {3, 7}, {2, 8}, {4, 255}, {4, 256}, {3, 257}, {2, 1000}, {2, 4096}, {1, 70_000}, {20, 33},
        {1, GpuSoftmax.MAX_ROW},
    };

    private RowsFixture() {
    }

    /** Records into {@code encoder} with the uploaded buffers (in the order of the arrays). */
    interface Recording<T> {
        void record(T kernels, GpuCommandEncoder encoder, GpuBuffer[] buffers);
    }

    /**
     * Uploads {@code arrays} (float[] or int[]), records, runs, and compares each float array with
     * {@code expected[i]} (null to skip).
     */
    static <T extends AutoCloseable> void check(String backend, Function<GpuDevice, T> factory, Object[] arrays,
            float[][] expected, Recording<T> recording, String what) throws Exception {
        try (GpuDevice device = Devices.open(backend)) {
            T kernels = factory.apply(device);
            try (GpuCommandEncoder enc = device.encoder()) {
                run(device, kernels, enc, arrays, expected, recording, backend, what);
            } finally {
                kernels.close();
            }
        }
    }

    private static <T> void run(GpuDevice device, T kernels, GpuCommandEncoder enc, Object[] arrays,
            float[][] expected, Recording<T> recording, String backend, String what) {
        Map<Object, GpuBuffer> uploaded = new IdentityHashMap<>();
        GpuBuffer[] buffers = new GpuBuffer[arrays.length];
        for (int i = 0; i < arrays.length; i++) {
            buffers[i] = uploaded.computeIfAbsent(arrays[i], k -> k instanceof float[] f
                    ? Devices.upload(device, enc, f) : Devices.upload(device, enc, (int[]) k));
        }
        recording.record(kernels, enc, buffers);
        List<GpuReadback> reads = new ArrayList<>();
        for (int i = 0; i < arrays.length; i++) {
            reads.add(expected[i] == null ? null : enc.download(buffers[i]));
        }
        Devices.run(device, enc);
        for (int i = 0; i < arrays.length; i++) {
            if (expected[i] != null) {
                Bits.assertSame(expected[i], Devices.floats(reads.get(i), expected[i].length),
                        Devices.hardware(backend), backend + " " + what + " (array " + i + ")");
            }
        }
    }

    /** {@code count} uniform floats in {@code [lo, hi)}. */
    static float[] uniform(SplittableRandom r, int count, double lo, double hi) {
        float[] a = new float[count];
        for (int i = 0; i < a.length; i++) {
            a[i] = (float) (lo + r.nextDouble() * (hi - lo));
        }
        return a;
    }
}
