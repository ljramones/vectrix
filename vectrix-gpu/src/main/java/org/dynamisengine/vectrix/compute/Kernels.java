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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dynamisengine.gpu.api.compute.Access;
import org.dynamisengine.gpu.api.compute.Backend;
import org.dynamisengine.gpu.api.compute.BindingLayout;
import org.dynamisengine.gpu.api.compute.ComputeException;
import org.dynamisengine.gpu.api.compute.Dimensions;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCapabilities;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuComputePipeline;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.KernelBundle;
import org.dynamisengine.gpu.api.compute.KernelLibrary;
import org.dynamisengine.gpu.api.compute.PipelineDescriptor;
import org.dynamisengine.gpu.api.compute.kit.Checks;
import org.dynamisengine.gpu.api.compute.kit.Grid;
import org.dynamisengine.gpu.api.compute.kit.ParameterLayout;

/**
 * The compiled kernels (src/main/slang, built by scripts/build-kernels.sh with DynamisGPU's ShaderBuild and loaded
 * through its KernelLibrary), pipelines, parameters and checks.
 */
final class Kernels {

    /** The largest element index a kernel can address (32-bit indices). */
    static final long MAX_INDEX = 0xFFFF_FFFFL;

    /** kernels/kernels.properties and the outputs it lists; bundles are named {@code vectrix.<kernel>}. */
    static final KernelLibrary LIBRARY = KernelLibrary.load(Kernels.class, "kernels", "kernels.properties", "vectrix.");

    private Kernels() {
    }

    /**
     * The kernel {@code name}: SPIR-V for Vulkan, Metal source for Metal, and for {@link Backend#MOCK} the entry
     * point's name (for test drivers that model the kernel). Its version is the start of the kernel sources' hash.
     */
    static KernelBundle bundle(String name) {
        return LIBRARY.bundle(name);
    }

    /**
     * A pipeline for kernel {@code name} through DynamisGPU's kit: the given buffer accesses in slot order. The kit
     * takes the local size, the buffers and the parameter block from the kernel's reflection; {@code local} and
     * {@code params}, the parameter block's fields in the order {@link Params} writes them, are checked against it, so
     * a parameter list out of step with its shader fails here rather than writing the wrong fields.
     */
    static GpuComputePipeline pipeline(GpuDevice device, String name, Dimensions local, String[] params,
            Set<String> types, Access... slots) {
        Checks.notNull(device, "device");
        PipelineDescriptor d = LIBRARY.pipeline(name, List.of(slots), types, Set.of());
        if (!d.localSize().equals(local)) {
            throw new IllegalStateException(name + ": local size " + local + ", reflected " + d.localSize());
        }
        ParameterLayout layout = ParameterLayout.of(LIBRARY, name);
        if (!layout.fields().equals(List.of(params)) || layout.bytes() != 4 * params.length) {
            throw new IllegalStateException(name + ": parameters " + List.of(params) + ", reflected " + layout.fields()
                    + " (" + layout.bytes() + " bytes)");
        }
        return device.pipeline(d);
    }

    /**
     * Records a dispatch of {@code pipeline}, labelled {@code vectrix.<kernel>} for DynamisGPU's per-dispatch timings
     * ({@code GpuSubmission.dispatchTimings}) and profiles.
     */
    static GpuCommandEncoder dispatch(GpuCommandEncoder encoder, GpuComputePipeline pipeline,
            Map<Integer, ? extends GpuBuffer> bindings, byte[] params, Dimensions groups) {
        return encoder.dispatch(pipeline, bindings, params, groups, "vectrix." + pipeline.descriptor().entryPoint());
    }

    /** A little-endian parameter block of 32-bit fields, written in declaration order. */
    static final class Params {
        private final ByteBuffer b;

        Params(int fields) {
            b = ByteBuffer.allocate(fields * 4).order(ByteOrder.LITTLE_ENDIAN);
        }

        /** A block for the parameter fields {@code fields}, written in that order (checked at pipeline creation). */
        Params(String[] fields) {
            this(fields.length);
        }

        Params u32(long v) {
            b.putInt((int) v);
            return this;
        }

        Params f32(float v) {
            b.putFloat(v);
            return this;
        }

        byte[] bytes() {
            if (b.hasRemaining()) {
                throw new IllegalStateException("parameter block not filled");
            }
            return b.array();
        }
    }

    /**
     * Groups for a linear range of {@code groups} groups: {@code (x, y)} with {@code x} at most the device limit.
     * Kernels take {@code x} as their {@code gridX} parameter and index groups as {@code group.y * gridX + group.x},
     * skipping indices past the range.
     */
    static Dimensions grid(GpuCapabilities caps, long groups) {
        return Grid.linear(caps, groups).groups();
    }

    /** The number of 32-bit elements in {@code b}. */
    static long elements(GpuBuffer b) {
        return Checks.elements(b, 4);
    }

    static void notNull(Object o, String name) {
        Checks.notNull(o, name);
    }

    /**
     * Checks that a {@code rows×cols} row-major matrix with leading dimension {@code ld} fits {@code buffer} from
     * element {@code off} (Gemmf's check), and that its elements have 32-bit indices. An empty matrix is not read.
     */
    static void matrix(GpuBuffer buffer, long off, int ld, int rows, int cols, String name) {
        notNull(buffer, name);
        if (rows == 0 || cols == 0) {
            return;
        }
        long end = off + (long) (rows - 1) * ld + cols;
        if (off < 0 || ld < cols || end > elements(buffer) || end - 1 > MAX_INDEX) {
            throw new IllegalArgumentException(name + ": " + rows + "x" + cols + " with leading dimension " + ld
                    + " does not fit from offset " + off + " in " + elements(buffer) + " elements");
        }
    }

    /** Checks that {@code n} elements fit {@code buffer} from element {@code off}, with 32-bit indices. */
    static void range(GpuBuffer buffer, long off, long n, String name) {
        Checks.range(buffer, off, n, 4, name);
    }
}
