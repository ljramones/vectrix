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

/**
 * Invalid arguments to the row kernels (GpuSoftmax beyond softmax, GpuLoss, GpuNorms) are rejected before anything
 * is recorded, with the CPU kernels' exception type (ValidationTest's style).
 */
class RowsValidationTest {

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

    /** One rejected call on fresh 40-element buffers x, y, z (all ones); {@code target} picks the one checked. */
    private interface Call {
        void run(GpuCommandEncoder enc, GpuBuffer x, GpuBuffer y, GpuBuffer z);
    }

    private static void reject(GpuDevice d, int target, Call call) {
        var enc = d.encoder();
        GpuBuffer[] b = {Devices.upload(d, enc, ones(40)), Devices.upload(d, enc, ones(40)),
            Devices.upload(d, enc, ones(40))};
        rejected(d, enc, b[target], 40, () -> call.run(enc, b[0], b[1], b[2]));
    }

    @Test
    void logSoftmaxAndLogSumExpCheckRowsAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuSoftmax s = new GpuSoftmax(d)) {
            reject(d, 1, (e, x, y, z) -> s.logSoftmax(e, 5, x, 0, 8, y, 0, 8, 9)); // stride below the row length
            reject(d, 1, (e, x, y, z) -> s.logSoftmax(e, 6, x, 0, 8, y, 0, 8, 8)); // 6 rows do not fit
            reject(d, 0, (e, x, y, z) -> s.logSoftmax(e, 2, x, 0, 8, x, 16, 8, 8)); // same buffer, other offset
            reject(d, 1, (e, x, y, z) -> s.logSoftmax(e, -1, x, 0, 8, y, 0, 8, 8));
            reject(d, 1, (e, x, y, z) -> s.logSoftmax(e, 1, x, 0, GpuSoftmax.MAX_ROW + 1, y, 0,
                    GpuSoftmax.MAX_ROW + 1, GpuSoftmax.MAX_ROW + 1));
            reject(d, 1, (e, x, y, z) -> s.logSoftmax(null, 1, x, 0, 8, y, 0, 8, 8));
            reject(d, 1, (e, x, y, z) -> s.logSumExp(e, 4, x, 0, 8, 8, y, 37)); // 4 results do not fit from 37
            reject(d, 0, (e, x, y, z) -> s.logSumExp(e, 2, x, 0, 8, 8, x, 30)); // out is x
            reject(d, 1, (e, x, y, z) -> s.logSumExp(e, 2, x, 0, 8, -1, y, 0));
            reject(d, 1, (e, x, y, z) -> s.logSumExp(e, 2, null, 0, 8, 8, y, 0));
        }
    }

    @Test
    void softmaxBackwardChecksAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuSoftmax s = new GpuSoftmax(d)) {
            for (boolean log : new boolean[] {false, true}) {
                Call[] calls = {
                    // dx is y at another offset
                    (e, y, dy, dx) -> backward(s, log, e, 2, y, 0, 8, dy, 0, 8, y, 16, 8, 8),
                    // dx is dy at another stride
                    (e, y, dy, dx) -> backward(s, log, e, 2, y, 0, 8, dy, 0, 8, dy, 0, 9, 8),
                    // dx, y and dy all one buffer
                    (e, y, dy, dx) -> backward(s, log, e, 2, y, 0, 8, y, 0, 8, y, 0, 8, 8),
                    // dx is y in place, dy shares y's buffer
                    (e, y, dy, dx) -> backward(s, log, e, 2, y, 0, 8, y, 16, 8, y, 0, 8, 8),
                    // dx too small
                    (e, y, dy, dx) -> backward(s, log, e, 2, y, 0, 8, dy, 0, 8, dx, 30, 8, 8),
                };
                int[] targets = {0, 1, 0, 0, 2};
                for (int i = 0; i < calls.length; i++) {
                    reject(d, targets[i], calls[i]);
                }
            }
        }
    }

    private static void backward(GpuSoftmax s, boolean log, GpuCommandEncoder e, int rows, GpuBuffer y, long yOff,
            int ldy, GpuBuffer dy, long dyOff, int lddy, GpuBuffer dx, long dxOff, int lddx, int n) {
        if (log) {
            s.logSoftmaxBackward(e, rows, y, yOff, ldy, dy, dyOff, lddy, dx, dxOff, lddx, n);
        } else {
            s.softmaxBackward(e, rows, y, yOff, ldy, dy, dyOff, lddy, dx, dxOff, lddx, n);
        }
    }

    @Test
    void crossEntropyChecksClassesTargetsAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuLoss l = new GpuLoss(d)) {
            reject(d, 2, (e, x, t, out) -> l.crossEntropy(e, 2, x, 0, 8, 0, t, 0, out, 0)); // no classes
            reject(d, 2, (e, x, t, out) -> l.crossEntropy(e, 2, x, 0, 8, 8, t, 39, out, 0)); // targets too short
            reject(d, 2, (e, x, t, out) -> l.crossEntropy(e, 2, x, 0, 8, 8, t, 0, out, 39)); // losses too short
            reject(d, 0, (e, x, t, out) -> l.crossEntropy(e, 2, x, 0, 8, 8, t, 0, x, 30)); // loss is logits
            reject(d, 1, (e, x, t, out) -> l.crossEntropy(e, 2, x, 0, 8, 8, t, 0, t, 30)); // loss is targets
            reject(d, 2, (e, x, t, out) -> l.crossEntropy(e, -1, x, 0, 8, 8, t, 0, out, 0));
            reject(d, 2, (e, x, t, dx) -> l.crossEntropyBackward(e, 2, x, 0, 8, 0, t, 0, 1f, dx, 0, 8));
            reject(d, 0, (e, x, t, dx) -> l.crossEntropyBackward(e, 2, x, 0, 8, 8, t, 0, 1f, x, 0, 9)); // stride
            reject(d, 1, (e, x, t, dx) -> l.crossEntropyBackward(e, 2, x, 0, 8, 8, t, 0, 1f, t, 0, 8)); // dx = t
            // in place, with the targets in the logits buffer
            reject(d, 0, (e, x, t, dx) -> l.crossEntropyBackward(e, 2, x, 0, 8, 8, x, 30, 1f, x, 0, 8));
            reject(d, 2, (e, x, t, dx) -> l.crossEntropyBackward(e, 2, x, 0, 8, 8, t, 0, 1f, dx, 25, 20)); // too big
        }
    }

    @Test
    void normsCheckOptionalArgumentsStatsAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuNorms k = new GpuNorms(d)) {
            reject(d, 1, (e, x, y, g) -> k.layerNorm(e, 2, x, 0, 8, g, 35, null, 0, 1e-5f, y, 0, 8, 8, null, 0));
            reject(d, 1, (e, x, y, s) -> k.layerNorm(e, 2, x, 0, 8, null, 0, null, 0, 1e-5f, y, 0, 8, 8, s, 37));
            reject(d, 1, (e, x, y, g) -> k.layerNorm(e, 2, x, 0, 8, y, 20, null, 0, 1e-5f, y, 0, 8, 8, null, 0));
            reject(d, 2, (e, x, y, s) -> k.layerNorm(e, 2, x, 0, 8, null, 0, null, 0, 1e-5f, y, 0, 8, 8, x, 30));
            reject(d, 0, (e, x, y, s) -> k.layerNorm(e, 2, x, 0, 8, null, 0, x, 20, 1e-5f, x, 0, 8, 8, null, 0));
            reject(d, 1, (e, x, y, s) -> k.rmsNorm(e, 2, x, 0, 8, null, 0, 1e-6f, y, 0, 8, 8, s, 39)); // 2 stats
            reject(d, 0, (e, x, y, s) -> k.rmsNorm(e, 2, x, 0, 8, null, 0, 1e-6f, x, 1, 8, 8, null, 0));
            reject(d, 1, (e, x, y, s) -> k.rmsNorm(e, 2, x, 0, 8, s, 0, 1e-6f, y, 0, 8, 8, s, 20)); // stats = gamma
            reject(d, 1, (e, x, y, s) -> k.rmsNorm(e, 1, x, 0, 8, null, 0, 1e-6f, y, 0, 8, -2, null, 0));
        }
    }

    @Test
    void normBackwardChecksStatsAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuNorms k = new GpuNorms(d)) {
            // stats are required, and hold two values per row (one for RMS norm)
            reject(d, 2, (e, x, dy, dx) -> k.layerNormBackward(e, 2, x, 0, 8, null, 0, null, 0, dy, 0, 8, dx, 0, 8,
                    null, 0, null, 0, 8));
            reject(d, 2, (e, x, dy, dx) -> k.layerNormBackward(e, 2, x, 0, 8, null, 0, x, 37, dy, 0, 8, dx, 0, 8,
                    null, 0, null, 0, 8));
            reject(d, 2, (e, x, dy, dx) -> k.rmsNormBackward(e, 2, x, 0, 8, null, 0, x, 39, dy, 0, 8, dx, 0, 8,
                    null, 0, 8));
            // stats checked even for empty rows
            reject(d, 2, (e, x, dy, dx) -> k.layerNormBackward(e, 2, x, 0, 0, null, 0, x, 39, dy, 0, 0, dx, 0, 0,
                    null, 0, null, 0, 0));
            // dx is x and dy at once
            reject(d, 0, (e, x, dy, dx) -> k.layerNormBackward(e, 2, x, 0, 8, null, 0, dy, 0, x, 0, 8, x, 0, 8,
                    null, 0, null, 0, 8));
            // dx is x at another offset
            reject(d, 0, (e, x, dy, dx) -> k.rmsNormBackward(e, 2, x, 0, 8, null, 0, dy, 30, dy, 0, 8, x, 16, 8,
                    null, 0, 8));
            // dx is the statistics
            reject(d, 2, (e, x, dy, dx) -> k.layerNormBackward(e, 2, x, 0, 8, null, 0, dx, 30, dy, 0, 8, dx, 0, 8,
                    null, 0, null, 0, 8));
            // dGamma and dBeta share a buffer
            reject(d, 2, (e, x, dy, g) -> k.layerNormBackward(e, 2, x, 0, 8, null, 0, x, 30, dy, 0, 8, dy, 0, 8,
                    g, 0, g, 16, 8));
            // dGamma is the input x
            reject(d, 0, (e, x, dy, dx) -> k.rmsNormBackward(e, 2, x, 0, 8, null, 0, dy, 30, dy, 0, 8, dx, 0, 8,
                    x, 20, 8));
            // dBeta too short
            reject(d, 2, (e, x, dy, b) -> k.layerNormBackward(e, 2, x, 0, 8, null, 0, x, 30, dy, 0, 8, dy, 0, 8,
                    null, 0, b, 33, 8));
        }
    }

    @Test
    void emptyWorkRecordsNothing() {
        try (GpuDevice d = Devices.open("reference"); GpuSoftmax s = new GpuSoftmax(d); GpuLoss l = new GpuLoss(d);
                GpuNorms k = new GpuNorms(d)) {
            var enc = d.encoder();
            var buf = Devices.upload(d, enc, ones(8));
            var other = Devices.upload(d, enc, ones(8));
            var stats = Devices.upload(d, enc, ones(8));
            s.logSoftmax(enc, 0, buf, 0, 4, other, 0, 4, 4);
            s.logSoftmax(enc, 3, buf, 0, 0, other, 0, 0, 0);
            s.logSumExp(enc, 0, buf, 0, 4, 4, other, 0);
            s.softmaxBackward(enc, 2, buf, 0, 0, buf, 0, 0, other, 0, 0, 0);
            s.logSoftmaxBackward(enc, 0, buf, 0, 4, buf, 0, 4, other, 0, 4, 4);
            l.crossEntropy(enc, 0, buf, 0, 4, 4, buf, 0, other, 0);
            l.crossEntropyBackward(enc, 0, buf, 0, 4, 4, buf, 0, 1f, other, 0, 4);
            k.layerNorm(enc, 2, buf, 0, 0, null, 0, null, 0, 1e-5f, other, 0, 0, 0, stats, 4); // no stats for n = 0
            k.rmsNorm(enc, 0, buf, 0, 4, null, 0, 1e-6f, other, 0, 4, 4, stats, 0);
            k.layerNormBackward(enc, 2, buf, 0, 0, null, 0, buf, 0, buf, 0, 0, other, 0, 0, stats, 0, null, 0, 0);
            k.rmsNormBackward(enc, 0, buf, 0, 4, null, 0, buf, 0, buf, 0, 4, other, 0, 4, stats, 0, 4);
            var read = enc.download(other);
            var readStats = enc.download(stats);
            Devices.run(d, enc);
            assertArrayEquals(ones(8), Devices.floats(read, 8));
            assertArrayEquals(ones(8), Devices.floats(readStats, 8));
        }
    }
}
