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

import org.dynamisengine.gpu.api.compute.Dimensions;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuDevice;

/**
 * Checks and dispatch shapes shared by the engine kernels ({@link GpuCullingKernels}, {@link GpuTransformKernels},
 * {@link GpuSkinningKernels}, {@link GpuPackedAffineKernels}).
 * <p>
 * An SoA buffer holds each field of a Vectrix structure-of-arrays type as a plane: field {@code f} of element
 * {@code i} is at element {@code off + f * stride + i}. That is a {@code fields × count} row-major matrix with
 * leading dimension {@code stride}, so it is checked as one.
 */
final class EngineBuffers {

    /** Threads per group of every engine kernel. */
    static final int GROUP = 64;

    private EngineBuffers() {
    }

    /** Checks that {@code fields} planes of {@code count} elements, {@code stride} apart, fit from {@code off}. */
    static void soa(GpuBuffer buffer, long off, int stride, int fields, int count, String name) {
        Kernels.notNull(buffer, name);
        if (stride < 0) {
            throw new IllegalArgumentException(name + ": negative stride " + stride);
        }
        Kernels.matrix(buffer, off, stride, fields, count, name);
    }

    /** Rejects {@code out} when it is one of {@code inputs}: the kernels read and write separate buffers. */
    static void distinct(GpuBuffer out, String name, GpuBuffer... inputs) {
        for (GpuBuffer in : inputs) {
            if (in == out) {
                throw new IllegalArgumentException(name + " must be a different buffer from the inputs");
            }
        }
    }

    /** Rejects a negative count. */
    static void count(int count, String name) {
        if (count < 0) {
            throw new IllegalArgumentException(name + " must not be negative: " + count);
        }
    }

    /**
     * Rejects {@code count > 0} indices into an empty range: every one of them would be out of range, which the CPU
     * method rejects. Other out-of-range indices live on the GPU and are skipped by the kernels.
     */
    static void someValid(int count, int limit, String name) {
        if (count > 0 && limit == 0) {
            throw new IllegalArgumentException(name + ": " + count + " indices into an empty range");
        }
    }

    /** The linear grid for {@code count} threads in groups of {@link #GROUP}. */
    static Dimensions groups(GpuDevice device, int count) {
        return Kernels.grid(device.capabilities(), (count + (long) GROUP - 1) / GROUP);
    }
}
