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
import org.dynamisengine.vectrix.compute.GpuActivations.Op;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * {@link GpuActivations} and {@link GpuOptim} reject invalid arguments before recording anything, with the CPU
 * kernels' exception type (the {@link ValidationTest} style).
 */
class ElementwiseValidationTest {

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

    @Test
    void forwardChecksRangesAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuActivations a = new GpuActivations(d)) {
            for (Op op : Op.values()) {
                if (op.backward()) {
                    continue;
                }
                var enc = d.encoder();
                var x = Devices.upload(d, enc, ones(10));
                var y = Devices.upload(d, enc, ones(10));
                rejected(d, enc, y, 10, () -> GpuActivationsTest.gpuForward(a, op, enc, x, 0, y, 3, 8)); // y short
                var enc2 = d.encoder();
                rejected(d, enc2, y, 10, () -> GpuActivationsTest.gpuForward(a, op, enc2, x, 3, y, 0, 8)); // x short
                var enc3 = d.encoder();
                rejected(d, enc3, y, 10, () -> GpuActivationsTest.gpuForward(a, op, enc3, x, -1, y, 0, 2));
                var enc4 = d.encoder();
                rejected(d, enc4, y, 10, () -> GpuActivationsTest.gpuForward(a, op, enc4, x, 0, y, -1, 2));
                var enc5 = d.encoder();
                rejected(d, enc5, y, 10, () -> GpuActivationsTest.gpuForward(a, op, enc5, x, 0, y, 0, -1));
                var enc6 = d.encoder();
                rejected(d, enc6, x, 10, () -> GpuActivationsTest.gpuForward(a, op, enc6, x, 0, x, 1, 4)); // overlap
                var enc7 = d.encoder();
                rejected(d, enc7, y, 10, () -> GpuActivationsTest.gpuForward(a, op, enc7, null, 0, y, 0, 1));
                var enc8 = d.encoder();
                rejected(d, enc8, x, 10, () -> GpuActivationsTest.gpuForward(a, op, enc8, x, 0, null, 0, 1));
                assertThrows(IllegalArgumentException.class, () -> GpuActivationsTest.gpuForward(a, op, null, x, 0, y,
                        0, 1));
            }
        }
    }

    @Test
    void backwardChecksRangesAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuActivations a = new GpuActivations(d)) {
            for (Op op : Op.values()) {
                if (!op.backward()) {
                    continue;
                }
                var enc = d.encoder();
                var x = Devices.upload(d, enc, ones(10));
                var dy = Devices.upload(d, enc, ones(10));
                var dx = Devices.upload(d, enc, ones(10));
                rejected(d, enc, dx, 10, () -> GpuActivationsTest.gpuBackward(a, op, enc, x, 3, dy, 0, dx, 0, 8));
                var enc2 = d.encoder();
                rejected(d, enc2, dx, 10, () -> GpuActivationsTest.gpuBackward(a, op, enc2, x, 0, dy, 3, dx, 0, 8));
                var enc3 = d.encoder();
                rejected(d, enc3, dx, 10, () -> GpuActivationsTest.gpuBackward(a, op, enc3, x, 0, dy, 0, dx, 3, 8));
                var enc4 = d.encoder();
                rejected(d, enc4, dx, 10, () -> GpuActivationsTest.gpuBackward(a, op, enc4, x, 0, dy, 0, dx, 0, -1));
                var enc5 = d.encoder();
                rejected(d, enc5, dx, 10, () -> GpuActivationsTest.gpuBackward(a, op, enc5, x, 0, dy, -2, dx, 0, 1));
                // dx is an input at another offset
                var enc6 = d.encoder();
                rejected(d, enc6, x, 10, () -> GpuActivationsTest.gpuBackward(a, op, enc6, x, 0, dy, 0, x, 2, 4));
                var enc7 = d.encoder();
                rejected(d, enc7, dy, 10, () -> GpuActivationsTest.gpuBackward(a, op, enc7, x, 0, dy, 2, dy, 0, 4));
                // dx is the forward value at its offset, but dy shares that buffer at another offset
                var enc8 = d.encoder();
                rejected(d, enc8, x, 10, () -> GpuActivationsTest.gpuBackward(a, op, enc8, x, 0, x, 5, x, 0, 4));
                var enc9 = d.encoder();
                rejected(d, enc9, dx, 10, () -> GpuActivationsTest.gpuBackward(a, op, enc9, x, 0, null, 0, dx, 0, 1));
                var enc10 = d.encoder();
                rejected(d, enc10, dx, 10, () -> GpuActivationsTest.gpuBackward(a, op, enc10, null, 0, dy, 0, dx, 0,
                        1));
                assertThrows(IllegalArgumentException.class, () -> GpuActivationsTest.gpuBackward(a, op, enc10, x, 0,
                        dy, 0, null, 0, 1));
                assertThrows(IllegalArgumentException.class, () -> GpuActivationsTest.gpuBackward(a, op, null, x, 0,
                        dy, 0, dx, 0, 1));
            }
        }
    }

    @Test
    void dropoutChecksProbabilityRangesAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuActivations a = new GpuActivations(d)) {
            float[] badP = {-0.1f, 1f, 1.5f, Float.NaN, Float.POSITIVE_INFINITY, -0f - 1e-45f};
            for (float p : badP) {
                var enc = d.encoder();
                var x = Devices.upload(d, enc, ones(10));
                var y = Devices.upload(d, enc, ones(10));
                rejected(d, enc, y, 10, () -> a.dropout(enc, 1, 0, p, x, 0, y, 0, 10));
            }
            var enc = d.encoder();
            var x = Devices.upload(d, enc, ones(10));
            var y = Devices.upload(d, enc, ones(10));
            rejected(d, enc, y, 10, () -> a.dropout(enc, 1, 0, 0.5f, x, 0, y, 3, 8));
            var enc2 = d.encoder();
            rejected(d, enc2, y, 10, () -> a.dropout(enc2, 1, 0, 0.5f, x, 3, y, 0, 8));
            var enc3 = d.encoder();
            rejected(d, enc3, y, 10, () -> a.dropout(enc3, 1, 0, 0.5f, x, 0, y, 0, -1));
            var enc4 = d.encoder();
            rejected(d, enc4, x, 10, () -> a.dropout(enc4, 1, 0, 0.5f, x, 0, x, 1, 4));
            var enc5 = d.encoder();
            rejected(d, enc5, y, 10, () -> a.dropout(enc5, 1, 0, 0.5f, null, 0, y, 0, 1));
            assertThrows(IllegalArgumentException.class, () -> a.dropout(null, 1, 0, 0.5f, x, 0, y, 0, 1));
        }
    }

    @Test
    void adamWChecksStepRangesAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuOptim o = new GpuOptim(d)) {
            var enc = d.encoder();
            var p = Devices.upload(d, enc, ones(10));
            var g = Devices.upload(d, enc, ones(10));
            var m = Devices.upload(d, enc, ones(10));
            var v = Devices.upload(d, enc, ones(10));
            rejected(d, enc, p, 10, () -> o.adamW(enc, p, 0, g, 0, m, 0, v, 0, 10, 1e-3f, 0.9f, 0.999f, 1e-8f, 0f, 0));
            var enc1 = d.encoder();
            rejected(d, enc1, p, 10, () -> o.adamW(enc1, p, 0, g, 0, m, 0, v, 0, 1, 1e-3f, 0.9f, 0.999f, 1e-8f, 0f,
                    Long.MIN_VALUE));
            var enc2 = d.encoder();
            rejected(d, enc2, p, 10, () -> o.adamW(enc2, p, 3, g, 0, m, 0, v, 0, 8, 1e-3f, 0.9f, 0.999f, 1e-8f, 0f, 1));
            var enc3 = d.encoder();
            rejected(d, enc3, p, 10, () -> o.adamW(enc3, p, 0, g, 3, m, 0, v, 0, 8, 1e-3f, 0.9f, 0.999f, 1e-8f, 0f, 1));
            var enc4 = d.encoder();
            rejected(d, enc4, p, 10, () -> o.adamW(enc4, p, 0, g, 0, m, 3, v, 0, 8, 1e-3f, 0.9f, 0.999f, 1e-8f, 0f, 1));
            var enc5 = d.encoder();
            rejected(d, enc5, p, 10, () -> o.adamW(enc5, p, 0, g, 0, m, 0, v, 3, 8, 1e-3f, 0.9f, 0.999f, 1e-8f, 0f, 1));
            var enc6 = d.encoder();
            rejected(d, enc6, p, 10, () -> o.adamW(enc6, p, 0, g, 0, m, 0, v, 0, -1, 1e-3f, 0.9f, 0.999f, 1e-8f, 0f,
                    1));
            GpuBuffer[][] aliased = {{p, p, m, v}, {p, g, p, v}, {p, g, m, p}, {p, g, g, v}, {p, g, m, g},
                {p, g, m, m}};
            for (GpuBuffer[] b : aliased) {
                var e = d.encoder();
                rejected(d, e, p, 10, () -> o.adamW(e, b[0], 0, b[1], 0, b[2], 0, b[3], 0, 1, 1e-3f, 0.9f, 0.999f,
                        1e-8f, 0f, 1));
            }
            var enc7 = d.encoder();
            rejected(d, enc7, p, 10, () -> o.adamW(enc7, p, 0, g, 0, null, 0, v, 0, 1, 1e-3f, 0.9f, 0.999f, 1e-8f, 0f,
                    1));
            assertThrows(IllegalArgumentException.class, () -> o.adamW(null, p, 0, g, 0, m, 0, v, 0, 1, 1e-3f, 0.9f,
                    0.999f, 1e-8f, 0f, 1));
        }
    }

    @Test
    void sgdChecksRangesAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuOptim o = new GpuOptim(d)) {
            var enc = d.encoder();
            var p = Devices.upload(d, enc, ones(10));
            var g = Devices.upload(d, enc, ones(10));
            var b = Devices.upload(d, enc, ones(10));
            rejected(d, enc, p, 10, () -> o.sgd(enc, p, 3, g, 0, b, 0, 8, 0.1f, 0.9f, 0f, false));
            var enc2 = d.encoder();
            rejected(d, enc2, p, 10, () -> o.sgd(enc2, p, 0, g, 3, b, 0, 8, 0.1f, 0.9f, 0f, false));
            var enc3 = d.encoder();
            rejected(d, enc3, p, 10, () -> o.sgd(enc3, p, 0, g, 0, b, 3, 8, 0.1f, 0.9f, 0f, true));
            var enc4 = d.encoder();
            rejected(d, enc4, p, 10, () -> o.sgd(enc4, p, 0, g, 0, b, 0, -1, 0.1f, 0.9f, 0f, false));
            var enc5 = d.encoder();
            rejected(d, enc5, p, 10, () -> o.sgd(enc5, p, 0, p, 0, b, 0, 1, 0.1f, 0.9f, 0f, false));
            var enc6 = d.encoder();
            rejected(d, enc6, p, 10, () -> o.sgd(enc6, p, 0, g, 0, p, 5, 1, 0.1f, 0.9f, 0f, false));
            var enc7 = d.encoder();
            rejected(d, enc7, b, 10, () -> o.sgd(enc7, p, 0, b, 0, b, 0, 1, 0.1f, 0.9f, 0f, false));
            var enc8 = d.encoder();
            rejected(d, enc8, p, 10, () -> o.sgd(enc8, p, 0, null, 0, b, 0, 1, 0.1f, 0.9f, 0f, false));
            assertThrows(IllegalArgumentException.class, () -> o.sgd(null, p, 0, g, 0, b, 0, 1, 0.1f, 0.9f, 0f,
                    false));
        }
    }

    @Test
    void emptyWorkRecordsNothing() {
        try (GpuDevice d = Devices.open("reference"); GpuActivations a = new GpuActivations(d);
                GpuOptim o = new GpuOptim(d)) {
            var enc = d.encoder();
            var buf = Devices.upload(d, enc, ones(4));
            var other = Devices.upload(d, enc, ones(4));
            var third = Devices.upload(d, enc, ones(4));
            var fourth = Devices.upload(d, enc, ones(4));
            for (Op op : Op.values()) {
                if (op.backward()) {
                    GpuActivationsTest.gpuBackward(a, op, enc, other, 4, third, 4, buf, 4, 0);
                } else {
                    GpuActivationsTest.gpuForward(a, op, enc, other, 4, buf, 4, 0);
                }
            }
            a.dropout(enc, 1, 0, 0.5f, other, 0, buf, 0, 0);
            o.adamW(enc, buf, 0, other, 0, third, 0, fourth, 0, 0, 1e-3f, 0.9f, 0.999f, 1e-8f, 0f, 1);
            o.sgd(enc, buf, 0, other, 0, third, 0, 0, 0.1f, 0.9f, 0f, true);
            var read = enc.download(buf);
            Devices.run(d, enc);
            assertArrayEquals(ones(4), Devices.floats(read, 4));
        }
    }
}
