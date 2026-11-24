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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.dynamisengine.gpu.api.compute.BufferDescriptor;
import org.dynamisengine.gpu.api.compute.BufferUsage;
import org.dynamisengine.gpu.api.compute.GpuAdapter;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.GpuProvider;
import org.dynamisengine.gpu.api.compute.GpuReadback;

/**
 * The backends the kernel tests run on: always the {@link ReferenceProvider}; native Metal with
 * {@code -Dvectrix.gpu.metal=true} (profile {@code metal}), Vulkan with {@code -Dvectrix.gpu.vulkan=true} (profile
 * {@code vulkan}). A requested backend without a usable device fails the tests rather than skipping them.
 */
final class Devices {

    private Devices() {
    }

    /** The names of the backends to test. */
    static List<String> names() {
        List<String> names = new ArrayList<>(List.of("reference"));
        if (Boolean.getBoolean("vectrix.gpu.metal")) {
            names.add("metal");
        }
        if (Boolean.getBoolean("vectrix.gpu.vulkan")) {
            names.add("vulkan");
        }
        return names;
    }

    /**
     * The backends whose {@code fma} is fused (one rounding). Float kernels port Vectrix's {@code Math.fma}, so only
     * these can match Vectrix bit for bit. Vulkan does not require a fused fma (its precision rules inherit fma from
     * a multiply followed by an add), and Mesa's lavapipe computes it with two roundings. Integer, conversion and
     * engine kernels need no fma and run on every backend.
     */
    static List<String> namesWithFusedFma() {
        return names().stream().filter(Devices::fusesFma).toList();
    }

    private static final java.util.Map<String, Boolean> FUSED = new java.util.concurrent.ConcurrentHashMap<>();

    /** Whether backend {@code name} computes {@code fma} with one rounding (probed once with math_probe). */
    static boolean fusesFma(String name) {
        return FUSED.computeIfAbsent(name, n -> {
            float x = 1 + 0x1p-12f; // x·x = 1 + 2^-11 + 2^-24 rounds to 1 + 2^-11: the fused residual is 2^-24
            return probe(n, 8, Float.floatToRawIntBits(x)) == Float.floatToRawIntBits(0x1p-24f);
        });
    }

    /** Runs math_probe function {@code fn} on one float (given as bits) on backend {@code name}. */
    static int probe(String name, int fn, int bits) {
        try (GpuDevice device = open(name)) {
            var layout = new org.dynamisengine.gpu.api.compute.BindingLayout(List.of(
                    new org.dynamisengine.gpu.api.compute.BindingLayout.BufferBinding(0,
                            org.dynamisengine.gpu.api.compute.Access.WRITE, 4)), 20);
            try (var pipeline = device.pipeline(new org.dynamisengine.gpu.api.compute.PipelineDescriptor(
                    ReproducibleMathGpuTest.probeKernel(), "probe", layout,
                    new org.dynamisengine.gpu.api.compute.Dimensions(64, 1, 1), java.util.Map.of(),
                    java.util.Set.of("FP32", "INT32"), java.util.Set.of()));
                    var out = buffer(device, 1); var enc = device.encoder()) {
                byte[] params = new Kernels.Params(5).u32(fn).u32(bits).u32(0).u32(1).u32(1).bytes();
                enc.dispatch(pipeline, java.util.Map.of(0, out), params,
                        new org.dynamisengine.gpu.api.compute.Dimensions(1, 1, 1));
                var read = enc.download(out);
                run(device, enc);
                return ints(read, 1)[0];
            }
        }
    }

    /** Whether the backend runs on hardware that may flush subnormals to zero. */
    static boolean hardware(String name) {
        return !name.equals("reference");
    }

    /** Opens the first device of backend {@code name}. */
    static GpuDevice open(String name) {
        GpuProvider provider = switch (name) {
            case "reference" -> new ReferenceProvider();
            case "metal" -> provider("org.dynamisengine.gpu.metal.MetalProvider");
            case "vulkan" -> provider("org.dynamisengine.gpu.vulkan.compute.VulkanProvider");
            default -> throw new IllegalArgumentException(name);
        };
        List<GpuAdapter> adapters = provider.adapters();
        if (adapters.isEmpty()) {
            throw new AssertionError(name + " was requested but has no device");
        }
        return adapters.get(0).open();
    }

    private static GpuProvider provider(String className) {
        try {
            return (GpuProvider) Class.forName(className).getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(className + " is not on the test class path (use its Maven profile)", e);
        }
    }

    private static final EnumSet<BufferUsage> USAGE = EnumSet.of(BufferUsage.STORAGE, BufferUsage.TRANSFER_SOURCE,
            BufferUsage.TRANSFER_DESTINATION);

    /** A device-private buffer of {@code count} 32-bit elements (at least one). */
    static GpuBuffer buffer(GpuDevice device, long count) {
        return device.allocate(new BufferDescriptor(Math.max(1, count) * 4, USAGE,
                org.dynamisengine.gpu.api.compute.Placement.PRIVATE));
    }

    /** A buffer holding {@code data}, uploaded by {@code encoder}. */
    static GpuBuffer upload(GpuDevice device, GpuCommandEncoder encoder, float[] data) {
        GpuBuffer b = buffer(device, data.length);
        ByteBuffer bytes = ByteBuffer.allocate(Math.max(1, data.length) * 4).order(ByteOrder.LITTLE_ENDIAN);
        bytes.asFloatBuffer().put(data);
        encoder.upload(b, bytes.array());
        return b;
    }

    /** A buffer holding {@code data}, uploaded by {@code encoder}. */
    static GpuBuffer upload(GpuDevice device, GpuCommandEncoder encoder, int[] data) {
        GpuBuffer b = buffer(device, data.length);
        ByteBuffer bytes = ByteBuffer.allocate(Math.max(1, data.length) * 4).order(ByteOrder.LITTLE_ENDIAN);
        bytes.asIntBuffer().put(data);
        encoder.upload(b, bytes.array());
        return b;
    }

    /** Submits {@code encoder} and waits for it. */
    static void run(GpuDevice device, GpuCommandEncoder encoder) {
        try (var submission = device.queue().submit(encoder)) {
            if (!submission.await(Duration.ofSeconds(120))) {
                throw new AssertionError("GPU work did not complete in 120 s");
            }
        }
    }

    static float[] floats(GpuReadback r, int count) {
        float[] out = new float[count];
        ByteBuffer.wrap(r.bytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out);
        return out;
    }

    static int[] ints(GpuReadback r, int count) {
        int[] out = new int[count];
        ByteBuffer.wrap(r.bytes()).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(out);
        return out;
    }
}
