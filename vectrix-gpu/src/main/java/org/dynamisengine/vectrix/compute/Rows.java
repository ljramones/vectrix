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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.dynamisengine.gpu.api.compute.Access;
import org.dynamisengine.gpu.api.compute.BufferDescriptor;
import org.dynamisengine.gpu.api.compute.BufferUsage;
import org.dynamisengine.gpu.api.compute.Dimensions;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuComputePipeline;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.Placement;
import org.dynamisengine.gpu.api.compute.kit.PipelineSet;

/** Shared plumbing of the row kernels (GpuSoftmax, GpuLoss, GpuNorms): pipelines, row checks and buffer aliasing. */
final class Rows {

    /** The threads of a row kernel's group; one group computes one row. */
    static final int GROUP = 256;

    /** The longest row a row kernel reduces: 1024 reduction blocks of 256 elements (its group-memory budget). */
    static final int MAX_ROW = 1024 * 256;

    private Rows() {
    }

    /** A kernel to compile: its resource name, parameter count and buffer accesses in slot order. */
    record Spec(String name, String[] params, Access... slots) {
    }

    /** Compiles {@code specs} in order with a local size of {@code local} threads; on failure closes what it made. */
    static GpuComputePipeline[] compile(GpuDevice device, int local, Set<String> types, Spec... specs) {
        if (device == null) {
            throw new IllegalArgumentException("device is null");
        }
        List<GpuComputePipeline> made = new ArrayList<>();
        try {
            for (Spec s : specs) {
                made.add(Kernels.pipeline(device, s.name(), new Dimensions(local, 1, 1), s.params(), types,
                        s.slots()));
            }
        } catch (RuntimeException e) {
            close(made.toArray(new AutoCloseable[0]));
            throw e;
        }
        return made.toArray(new GpuComputePipeline[0]);
    }

    /** A 4-byte buffer bound in the slot of an absent optional argument; the kernels never write it. */
    static GpuBuffer placeholder(GpuDevice device) {
        return device.allocate(new BufferDescriptor(4, EnumSet.of(BufferUsage.STORAGE), Placement.PRIVATE));
    }

    /** Closes every resource, rethrowing the first failure after trying them all. */
    static void close(AutoCloseable... resources) {
        PipelineSet.closeAll(resources);
    }

    /** Checks the batch shape: {@code rows, n >= minN} and {@code n <= MAX_ROW}. */
    static void shape(int rows, int n, int minN) {
        if (rows < 0 || n < 0) {
            throw new IllegalArgumentException("negative size: " + rows + ", " + n);
        }
        if (n < minN) {
            throw new IllegalArgumentException("n must be at least " + minN + ": " + n);
        }
        if (n > MAX_ROW) {
            throw new IllegalArgumentException("row length exceeds " + MAX_ROW + ": " + n);
        }
    }

    /**
     * Checks that the written buffer {@code w} is none of {@code others} (nulls ignored): a kernel never binds a
     * buffer it writes in a second slot.
     */
    static void exclusive(GpuBuffer w, String name, GpuBuffer... others) {
        for (GpuBuffer o : others) {
            if (o != null && o == w) {
                throw new IllegalArgumentException(name + " must be a different buffer from the other arguments");
            }
        }
    }

    /** Checks that output {@code out} is input {@code in} at the same offset and stride, if it is that buffer. */
    static boolean inPlace(GpuBuffer out, long outOff, int ldOut, String outName, GpuBuffer in, long inOff, int ldIn,
            String inName) {
        if (out != in) {
            return false;
        }
        if (outOff != inOff || ldOut != ldIn) {
            throw new IllegalArgumentException(outName + " may be " + inName + " only at the same offset and stride");
        }
        return true;
    }
}
