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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.SplittableRandom;
import org.dynamisengine.gpu.api.compute.ComputeException;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.GpuRecording;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * vectrix-gpu on DynamisGPU's runtime: its kernels record into a recording (they allocate nothing per call) and replay
 * the same bits as a direct submission, and their dispatches carry {@code vectrix.<kernel>} labels in the per-dispatch
 * timings.
 */
class RuntimeIntegrationTest {
    static final Duration WAIT = Duration.ofSeconds(60);

    static java.util.List<String> backends() {
        return Devices.names();
    }

    @ParameterizedTest
    @MethodSource("backends")
    void recordedOperationsReplayTheSameBits(String backend) {
        int rows = 7, n = 300;
        var r = new SplittableRandom(5);
        float[] x = new float[rows * n];
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) r.nextDouble(-4, 4);
        }
        try (GpuDevice device = Devices.open(backend); var softmax = new GpuSoftmax(device); var act = new GpuActivations(device)) {
            GpuBuffer in, mid = Devices.buffer(device, x.length), out = Devices.buffer(device, x.length);
            float[] direct;
            try (var e = device.encoder()) {
                in = Devices.upload(device, e, x);
                softmax.softmax(e, rows, in, 0, n, mid, 0, n, n);
                act.silu(e, mid, 0, out, 0, rows * n);
                var read = e.download(out);
                try (var s = device.queue().submit(e)) {
                    assertTrue(s.await(WAIT));
                }
                direct = Devices.floats(read, x.length);
            }
            GpuRecording recording;
            try (var e = device.encoder()) {
                softmax.softmax(e, rows, in, 0, n, mid, 0, n, n);
                act.silu(e, mid, 0, out, 0, rows * n);
                try {
                    recording = e.record();
                } catch (ComputeException unsupported) {
                    Assumptions.abort(backend + " has no recordings: " + unsupported.getMessage());
                    return;
                }
            }
            try (recording) {
                for (int run = 0; run < 3; run++) {
                    try (var e = device.encoder()) {
                        e.fill(out, 0);
                        try (var s = device.queue().submit(e)) {
                            assertTrue(s.await(WAIT));
                        }
                    }
                    try (var s = device.queue().submit(recording)) {
                        assertTrue(s.await(WAIT));
                    }
                    try (var e = device.encoder()) {
                        var read = e.download(out);
                        try (var s = device.queue().submit(e)) {
                            assertTrue(s.await(WAIT));
                        }
                        float[] replayed = Devices.floats(read, x.length);
                        assertArrayEquals(direct, replayed, backend + ": replay " + run);
                    }
                }
            }
            // the dispatches' labels in the per-dispatch timings, where the backend measures them
            if (device.capabilities().timestamps()) {
                try (var e = device.encoder()) {
                    e.profile();
                    softmax.softmax(e, rows, in, 0, n, mid, 0, n, n);
                    act.silu(e, mid, 0, out, 0, rows * n);
                    try (var s = device.queue().submit(e)) {
                        assertTrue(s.await(WAIT));
                        var labels = s.dispatchTimings().stream().map(d -> d.label()).toList();
                        assertTrue(labels.stream().allMatch(l -> l != null && l.startsWith("vectrix.")), backend + ": " + labels);
                        assertTrue(labels.size() == 2, backend + ": " + labels);
                    }
                }
            }
            in.close();
            mid.close();
            out.close();
        }
    }
}
