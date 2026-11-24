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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.SplittableRandom;

import org.dynamisengine.gpu.api.compute.BufferDescriptor;
import org.dynamisengine.gpu.api.compute.BufferUsage;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.GpuReadback;
import org.dynamisengine.gpu.api.compute.GpuSubmission;
import org.dynamisengine.gpu.api.compute.Placement;
import org.dynamisengine.vectrix.blas.Gemmf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** The usage example in README.md, run on every enabled backend; the README quotes the tagged region. */
class UsageExampleTest {

    static float[] multiply(GpuDevice device, float[] a, float[] b, int m, int n, int k) {
        // tag::usage[]
        var usage = EnumSet.of(BufferUsage.STORAGE, BufferUsage.TRANSFER_SOURCE, BufferUsage.TRANSFER_DESTINATION);
        try (GpuGemmf gemm = new GpuGemmf(device); // compiles the kernel: create once per device and reuse
                GpuBuffer ga = device.allocate(new BufferDescriptor(4L * m * k, usage, Placement.PRIVATE));
                GpuBuffer gb = device.allocate(new BufferDescriptor(4L * k * n, usage, Placement.PRIVATE));
                GpuBuffer gc = device.allocate(new BufferDescriptor(4L * m * n, usage, Placement.PRIVATE));
                GpuCommandEncoder encoder = device.encoder()) {
            encoder.upload(ga, littleEndian(a)).upload(gb, littleEndian(b));
            gemm.gemm(encoder, false, false, m, n, k, 1f, ga, 0, k, gb, 0, n, 0f, gc, 0, n); // C = A·B
            GpuReadback c = encoder.download(gc);
            try (GpuSubmission done = device.queue().submit(encoder)) {
                done.await(Duration.ofSeconds(10));
            }
            return floats(c.bytes()); // the bits Gemmf.gemm computes on the CPU
        }
        // end::usage[]
    }

    static byte[] littleEndian(float[] v) {
        ByteBuffer b = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.asFloatBuffer().put(v);
        return b.array();
    }

    static float[] floats(byte[] bytes) {
        float[] v = new float[bytes.length / 4];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(v);
        return v;
    }

    static List<String> backends() {
        return Devices.namesWithFusedFma();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void exampleMatchesTheCpu(String backend) {
        int m = 24;
        int n = 40;
        int k = 700;
        SplittableRandom r = new SplittableRandom(1);
        float[] a = GpuGemmfTest.random(r, m * k);
        float[] b = GpuGemmfTest.random(r, k * n);
        float[] expected = new float[m * n];
        Gemmf.gemm(false, false, m, n, k, 1f, a, 0, k, b, 0, n, 0f, expected, 0, n,
                new float[Gemmf.scratchSize(false, false, m, n, k)], 0);
        try (GpuDevice device = Devices.open(backend)) {
            Bits.assertSame(expected, multiply(device, a, b, m, n, k), Devices.hardware(backend), backend);
        }
    }

    /** The README's Java block is exactly the tagged region above. */
    @Test
    void readmeQuotesTheExample() throws IOException {
        List<String> src = Files.readAllLines(Path.of("src/test/java/org/dynamisengine/vectrix/compute/"
                + "UsageExampleTest.java"));
        int from = src.indexOf("        // tag::usage[]") + 1;
        int to = src.indexOf("        // end::usage[]");
        StringBuilder region = new StringBuilder();
        for (String line : src.subList(from, to)) {
            region.append(line.isEmpty() ? "" : line.substring(8)).append('\n');
        }
        String readme = Files.readString(Path.of("README.md"));
        String marker = "<!-- snippet: UsageExampleTest#usage -->\n```java\n";
        int at = readme.indexOf(marker);
        int end = readme.indexOf("```", at + marker.length());
        assertEquals(region.toString(), readme.substring(at + marker.length(), end),
                "README.md must quote UsageExampleTest's usage region exactly");
    }
}
