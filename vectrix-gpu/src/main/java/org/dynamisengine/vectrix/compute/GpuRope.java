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
import java.util.Map;
import java.util.Set;

import org.dynamisengine.gpu.api.compute.Access;
import org.dynamisengine.gpu.api.compute.Dimensions;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuComputePipeline;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.KernelBundle;
import org.dynamisengine.gpu.api.compute.kit.Checks;

/**
 * The GPU twin of Vectrix {@code nn.Rope}: rotary position embedding of many vectors in place, forward
 * ({@link #apply}) and backward ({@link #applyBackward}).
 * <p>
 * Rope computes each angle, its sine and cosine and the rotation in double and rounds once, because the angles reach
 * millions of radians at long contexts. GPUs such as Apple's have no FP64, so this twin computes them with the binary64
 * arithmetic on 64-bit integers of {@code f64.slang}, operation for operation as {@code Rope.rotate}: the results are
 * the bits Vectrix computes on every device with the {@code INT64} capability (subnormal outputs aside on GPUs that
 * flush them when read). One thread rotates one pair.
 * <p>
 * Vector {@code v} (of {@code dim} floats) starts at element {@code xOff + v·ldx} of {@code x}; its position is
 * element {@code posOff + v / group} of {@code positions}, an int64 buffer, so the {@code group} vectors of one token
 * (its heads) share one position. The inverse frequencies come from a buffer holding
 * {@link #frequencies(double[]) frequencies(rope.inverseFrequencies())}; {@code dim} is {@code rope.dim()} and the
 * layout {@code rope.layout()}.
 *
 * @since 4.1.0
 */
public final class GpuRope implements AutoCloseable {
    /** The parameter block (checked against the compiled kernel). */
    static final String[] PARAMS = {"half", "interleaved", "backward", "xOff", "ldx", "count", "posOff", "group",
        "gridX"};
    private static final int GROUP = 64;

    /** Which elements form a pair, as {@code Rope.Layout}. */
    public enum Layout {
        /** Pairs {@code (2i, 2i + 1)}. */
        INTERLEAVED,
        /** Pairs {@code (i, i + dim / 2)}. */
        HALF
    }

    private final GpuDevice device;
    private final GpuComputePipeline pipeline;

    /**
     * Compiles the kernel on {@code device}.
     *
     * @param device the device to run on; it needs the {@code INT64} capability
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipeline, for
     *         example without {@code INT64}
     */
    public GpuRope(GpuDevice device) {
        pipeline = Kernels.pipeline(device, "rope", new Dimensions(GROUP, 1, 1), PARAMS, Set.of("FP32", "INT64"),
                Access.READ_WRITE, Access.READ, Access.READ);
        this.device = device;
    }

    /**
     * The compiled kernel, for applications that manage pipelines themselves. Bindings: slot 0 {@code x} (float,
     * rotated in place), 1 the inverse frequencies (double bits), 2 the positions (int64); the parameter block holds
     * the 32-bit fields {@code half, interleaved, backward, xOff, ldx, count, posOff, group, gridX}, one thread per
     * pair ({@code count·half} threads, 64 per group).
     *
     * @return the kernel bundle
     */
    public static KernelBundle kernel() {
        return Kernels.bundle("rope");
    }

    /**
     * The bytes of inverse frequencies ({@code Rope.inverseFrequencies()}) for the frequency buffer: doubles,
     * little-endian.
     *
     * @param f the {@code dim / 2} inverse frequencies
     * @return the bytes to upload
     * @throws IllegalArgumentException if {@code f} is null
     */
    public static byte[] frequencies(double[] f) {
        Kernels.notNull(f, "f");
        ByteBuffer b = ByteBuffer.allocate(f.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        b.asDoubleBuffer().put(f);
        return b.array();
    }

    /**
     * The bytes of {@code positions} for the position buffer: int64, little-endian.
     *
     * @param positions the positions
     * @return the bytes to upload
     * @throws IllegalArgumentException if {@code positions} is null
     */
    public static byte[] positions(long[] positions) {
        Kernels.notNull(positions, "positions");
        ByteBuffer b = ByteBuffer.allocate(positions.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        b.asLongBuffer().put(positions);
        return b.array();
    }

    /**
     * Records {@code Rope.apply} on {@code count} vectors, in place.
     *
     * @param encoder the encoder to record into
     * @param dim the vector dimension, {@code rope.dim()}: even and at least 2
     * @param layout which elements form a pair, as {@code rope.layout()}
     * @param frequencies a buffer holding {@link #frequencies(double[])} of the {@code dim / 2} inverse frequencies
     * @param x the vectors, rotated in place
     * @param xOff the element index in {@code x} of the first vector
     * @param ldx the element distance between vectors, {@code >= dim}
     * @param count the number of vectors, {@code >= 0}; nothing is recorded for 0
     * @param positions the positions, int64
     * @param posOff the element index in {@code positions} of the first vector's position
     * @param group the number of consecutive vectors sharing a position, {@code >= 1}
     * @return {@code encoder}
     * @throws IllegalArgumentException if an argument is null, negative or out of range, {@code ldx} is less than
     *         the dimension, or {@code x} is one of the other buffers
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder apply(GpuCommandEncoder encoder, int dim, Layout layout, GpuBuffer frequencies, GpuBuffer x, long xOff,
            int ldx, int count, GpuBuffer positions, long posOff, int group) {
        return record(encoder, dim, layout, frequencies, x, xOff, ldx, count, positions, posOff, group, false);
    }

    /**
     * Records {@code Rope.applyBackward} on {@code count} gradient vectors, in place: the inverse rotation. The
     * arguments are those of {@link #apply}.
     *
     * @param encoder the encoder to record into
     * @param dim the vector dimension, {@code rope.dim()}: even and at least 2
     * @param layout which elements form a pair, as {@code rope.layout()}
     * @param frequencies a buffer holding {@link #frequencies(double[])} of the {@code dim / 2} inverse frequencies
     * @param dy the gradient vectors, rotated in place
     * @param dyOff the element index in {@code dy} of the first vector
     * @param ldy the element distance between vectors, {@code >= dim}
     * @param count the number of vectors, {@code >= 0}; nothing is recorded for 0
     * @param positions the positions, int64
     * @param posOff the element index in {@code positions} of the first vector's position
     * @param group the number of consecutive vectors sharing a position, {@code >= 1}
     * @return {@code encoder}
     * @throws IllegalArgumentException as for {@link #apply}
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder applyBackward(GpuCommandEncoder encoder, int dim, Layout layout, GpuBuffer frequencies, GpuBuffer dy,
            long dyOff, int ldy, int count, GpuBuffer positions, long posOff, int group) {
        return record(encoder, dim, layout, frequencies, dy, dyOff, ldy, count, positions, posOff, group, true);
    }

    private GpuCommandEncoder record(GpuCommandEncoder encoder, int dim, Layout layout, GpuBuffer frequencies,
            GpuBuffer x, long xOff, int ldx, int count, GpuBuffer positions, long posOff, int group, boolean backward) {
        Kernels.notNull(encoder, "encoder");
        Kernels.notNull(layout, "layout");
        if (dim < 2 || (dim & 1) != 0) {
            throw new IllegalArgumentException("dim must be even and at least 2: " + dim);
        }
        Kernels.notNull(frequencies, "frequencies");
        Kernels.notNull(x, "x");
        Kernels.notNull(positions, "positions");
        int half = dim / 2;
        if (count < 0 || group < 1 || ldx < dim || xOff < 0 || posOff < 0) {
            throw new IllegalArgumentException("need count >= 0, group >= 1, ldx >= dim (" + dim
                    + ") and offsets >= 0");
        }
        if (x == frequencies || x == positions) {
            throw new IllegalArgumentException("x must be a different buffer from the frequencies and positions");
        }
        Checks.range(frequencies, 0, half, 8, "frequencies");
        if (count == 0) {
            return encoder;
        }
        Kernels.range(x, xOff, (long) (count - 1) * ldx + dim, "x");
        Checks.range(positions, posOff, (count - 1) / group + 1, 8, "positions");
        long threads = (long) count * half;
        if (threads > 0xFFFF_FFFFL || xOff + (long) (count - 1) * ldx + dim > 0xFFFF_FFFFL
                || posOff + (count - 1L) / group + 1 > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("element indices must stay below 2^32");
        }
        Dimensions groups = Kernels.grid(device.capabilities(), (threads + GROUP - 1) / GROUP);
        byte[] params = new Kernels.Params(PARAMS).u32(half).u32(layout == Layout.INTERLEAVED ? 1 : 0)
                .u32(backward ? 1 : 0).u32(xOff).u32(ldx).u32(count).u32(posOff).u32(group).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, pipeline, Map.of(0, x, 1, frequencies, 2, positions), params, groups);
    }

    @Override
    public void close() {
        pipeline.close();
    }
}
