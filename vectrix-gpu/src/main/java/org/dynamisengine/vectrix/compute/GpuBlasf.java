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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dynamisengine.gpu.api.compute.Access;
import org.dynamisengine.gpu.api.compute.Dimensions;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCapabilities;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuComputePipeline;
import org.dynamisengine.gpu.api.compute.GpuDevice;

/**
 * The GPU twin of Vectrix {@code blas.Blasf}: the reductions {@link #dot}, {@link #sum}, {@link #asum},
 * {@link #nrm2} and {@link #iamax}, and the element-wise {@link #axpy} and {@link #scal}, over float buffers.
 * <p>
 * <b>Reductions</b> use {@code Blasf}'s order: 256-element blocks, each summed in 8 interleaved lanes combined as
 * {@code ((a0 + a4) + (a2 + a6)) + ((a1 + a5) + (a3 + a7))}, and the blocks added pairwise, each range of blocks
 * split after its first half rounded up. The work is split exactly at those midpoints: one dispatch computes the
 * subtrees of at most 32 blocks in parallel (one group each), and a few more add the levels above them, so a vector
 * of any length uses the whole GPU and the result is the bits {@code Blasf} computes on an IEEE device (see the
 * package comment for subnormals). {@link #nrm2} also follows {@code Blasf.nrm2} when the plain sum of squares is not
 * a normal number: it finds the largest magnitude and sums again with the elements scaled by a power of two. Its
 * decisions are taken on the GPU, so the extra passes are always recorded and do nothing when not needed.
 * {@link #iamax} applies {@code Blasf.iamax}'s rules: the first NaN, otherwise the first element of largest
 * magnitude.
 * <p>
 * The results stay on the GPU: each reduction writes one 32-bit value to {@code out} at an element offset (a float,
 * or an int for {@code iamax}), and works in caller scratch of {@link #scratchSize} elements, which it may overwrite
 * entirely. Reductions recorded in one encoder run in order, so one scratch buffer serves them all.
 * <p>
 * <b>Element-wise</b> kernels round each element once, as on the CPU: {@code axpy} with a fused multiply-add.
 * <p>
 * Six pipelines, compiled when the instance is created.
 *
 * @since 3.2.0
 */
public final class GpuBlasf implements AutoCloseable {

    /** The parameter block of the first reduction pass (checked against the compiled kernel). */
    static final String[] PARAMS_PARTIAL = {"op", "n", "xOff", "yOff", "blocks", "depth", "leafOff", "stateOff",
        "need", "gridX"};

    /** The parameter block of the upper reduction passes (checked against the compiled kernel). */
    static final String[] PARAMS_COMBINE = {"blocks", "inDepth", "outDepth", "inOff", "outOff", "mode", "stateOff",
        "need", "gridX"};

    /** The parameter block of the largest-magnitude kernel (checked against the compiled kernel). */
    static final String[] PARAMS_AMAX = {"mode", "n", "xOff", "stage", "parts", "partOff", "stateOff", "outOff",
        "need", "gridX"};

    /** The parameter block of the two-buffer axpy kernel (checked against the compiled kernel). */
    static final String[] PARAMS_AXPY = {"a", "n", "xOff", "yOff", "gridX"};

    /** The parameter block of the in-place axpy kernel (checked against the compiled kernel). */
    static final String[] PARAMS_AXPY_IN_PLACE = {"a", "n", "xOff", "gridX"};

    /** The parameter block of the scal kernel (checked against the compiled kernel). */
    static final String[] PARAMS_SCAL = {"a", "n", "xOff", "gridX"};

    private static final int BLOCK = 256;

    /** Blocks per group of the first pass (8 threads per block). */
    private static final int LEAF_BLOCKS = 32;

    /** Tree levels per upper pass; the kernel has stack frames for this plus one. */
    private static final int FAN_DEPTH = 4;

    private static final int COMBINE_GROUP = 32;

    private static final int GROUP = 256;

    private static final int MAX_PARTS = 1024;

    private static final int PER_PART = 4096;

    /** Scratch elements before the node regions: nrm2's state word and exponent. */
    private static final int STATE = 2;

    private static final int OP_DOT = 0;
    private static final int OP_SUM = 1;
    private static final int OP_ASUM = 2;
    private static final int OP_SUM_SQ = 3;
    private static final int OP_SUM_SQ_SCALED = 4;

    private static final int MODE_NODES = 0;
    private static final int MODE_RESULT = 1;
    private static final int MODE_NRM2 = 2;
    private static final int MODE_NRM2_SCALED = 3;

    /** nrm2's state: the sum of squares was not normal, find the largest magnitude. */
    private static final int NEED_MAX = 1;

    /** nrm2's state: sum the scaled squares. */
    private static final int NEED_SCALED = 2;

    private final GpuDevice device;

    private final GpuComputePipeline partial;

    private final GpuComputePipeline combine;

    private final GpuComputePipeline amax;

    private final GpuComputePipeline axpyTwo;

    private final GpuComputePipeline axpyInPlace;

    private final GpuComputePipeline scalPipe;

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipelines
     */
    public GpuBlasf(GpuDevice device) {
        Set<String> types = Set.of("FP32", "INT32");
        Dimensions group = new Dimensions(GROUP, 1, 1);
        List<GpuComputePipeline> made = new ArrayList<>();
        try {
            made.add(Kernels.pipeline(device, "blas_partial", group, PARAMS_PARTIAL.length, types, Access.READ,
                    Access.READ, Access.READ_WRITE));
            made.add(Kernels.pipeline(device, "blas_combine", new Dimensions(COMBINE_GROUP, 1, 1),
                    PARAMS_COMBINE.length, types, Access.READ_WRITE, Access.READ_WRITE));
            made.add(Kernels.pipeline(device, "blas_amax", group, PARAMS_AMAX.length, types, Access.READ,
                    Access.READ_WRITE, Access.READ_WRITE));
            made.add(Kernels.pipeline(device, "blas_axpy", group, PARAMS_AXPY.length, types, Access.READ,
                    Access.READ_WRITE));
            made.add(Kernels.pipeline(device, "blas_axpy_inplace", group, PARAMS_AXPY_IN_PLACE.length, types,
                    Access.READ_WRITE));
            made.add(Kernels.pipeline(device, "blas_scal", group, PARAMS_SCAL.length, types, Access.READ_WRITE));
        } catch (RuntimeException e) {
            made.forEach(GpuComputePipeline::close);
            throw e;
        }
        partial = made.get(0);
        combine = made.get(1);
        amax = made.get(2);
        axpyTwo = made.get(3);
        axpyInPlace = made.get(4);
        scalPipe = made.get(5);
        this.device = device;
    }

    /**
     * The scratch, in 32-bit elements, that a reduction of {@code n} elements needs ({@link #dot}, {@link #sum},
     * {@link #asum}, {@link #nrm2} and {@link #iamax} alike): 2 state words; then one value per subtree of the first
     * pass (the least power of two of at least {@code ceil(n / 256) / 32}) or two per group of the
     * largest-magnitude pass (at most 2048), whichever is more; then a sixteenth of the subtree values for the upper
     * passes. At most 278,530 elements, for {@code n = Integer.MAX_VALUE}.
     *
     * @param n the number of elements, {@code >= 0}
     * @return the scratch size in elements
     * @throws IllegalArgumentException if {@code n < 0}
     */
    public static long scratchSize(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("negative size: " + n);
        }
        int depth = depth(blocks(n));
        return STATE + regionA(n) + (depth > FAN_DEPTH ? 1L << (depth - FAN_DEPTH) : 0);
    }

    private static long blocks(int n) {
        return (n + (long) BLOCK - 1) / BLOCK;
    }

    /** The depth of the first pass's subtrees: the least at which no subtree has more than 32 blocks. */
    private static int depth(long blocks) {
        int d = 0;
        while ((blocks + (1L << d) - 1) >> d > LEAF_BLOCKS) {
            d++;
        }
        return d;
    }

    /** The groups of the largest-magnitude pass. */
    private static int parts(int n) {
        return (int) Math.min(MAX_PARTS, Math.max(1, (n + (long) PER_PART - 1) / PER_PART));
    }

    private static long regionA(int n) {
        return Math.max(1L << depth(blocks(n)), 2L * parts(n));
    }

    /**
     * Records {@code out[outOff] = Σ x[i]·y[i]}, {@code Blasf.dot} of {@code n} elements from {@code xOff} and
     * {@code yOff} (0 for {@code n == 0}). {@code x} and {@code y} may be the same buffer. All arguments are checked
     * before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param x the first input
     * @param xOff the element index of the first element of {@code x}
     * @param y the second input; may be {@code x}
     * @param yOff the element index of the first element of {@code y}
     * @param n the number of elements, {@code >= 0}
     * @param out receives the float result
     * @param outOff the element index of the result in {@code out}
     * @param scratch at least {@link #scratchSize scratchSize(n)} elements from {@code scratchOff}, overwritten
     * @param scratchOff the element index of the first scratch element
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n < 0}, an offset is negative, a buffer is too short (or needs
     *         element indices of 2^32 or more), {@code out} or {@code scratch} is an input or {@code out} is
     *         {@code scratch}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if a dispatch exceeds the device's limits
     */
    public GpuCommandEncoder dot(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff, int n,
            GpuBuffer out, long outOff, GpuBuffer scratch, long scratchOff) {
        checkReduction(encoder, x, xOff, y, yOff, n, out, outOff, scratch, scratchOff);
        return reduce(encoder, OP_DOT, x, xOff, y, yOff, n, out, outOff, scratch, scratchOff, MODE_RESULT, 0);
    }

    /**
     * Records {@code out[outOff] = Σ x[i]}, {@code Blasf.sum} of {@code n} elements from {@code xOff} (0 for
     * {@code n == 0}). All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param x the input
     * @param xOff the element index of the first element of {@code x}
     * @param n the number of elements, {@code >= 0}
     * @param out receives the float result
     * @param outOff the element index of the result in {@code out}
     * @param scratch at least {@link #scratchSize scratchSize(n)} elements from {@code scratchOff}, overwritten
     * @param scratchOff the element index of the first scratch element
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n < 0}, an offset is negative, a buffer is too short (or needs
     *         element indices of 2^32 or more), {@code out} or {@code scratch} is {@code x} or {@code out} is
     *         {@code scratch}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if a dispatch exceeds the device's limits
     */
    public GpuCommandEncoder sum(GpuCommandEncoder encoder, GpuBuffer x, long xOff, int n, GpuBuffer out, long outOff,
            GpuBuffer scratch, long scratchOff) {
        checkReduction(encoder, x, xOff, x, xOff, n, out, outOff, scratch, scratchOff);
        return reduce(encoder, OP_SUM, x, xOff, x, xOff, n, out, outOff, scratch, scratchOff, MODE_RESULT, 0);
    }

    /**
     * Records {@code out[outOff] = Σ |x[i]|}, {@code Blasf.asum} of {@code n} elements from {@code xOff} (0 for
     * {@code n == 0}). All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param x the input
     * @param xOff the element index of the first element of {@code x}
     * @param n the number of elements, {@code >= 0}
     * @param out receives the float result
     * @param outOff the element index of the result in {@code out}
     * @param scratch at least {@link #scratchSize scratchSize(n)} elements from {@code scratchOff}, overwritten
     * @param scratchOff the element index of the first scratch element
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n < 0}, an offset is negative, a buffer is too short (or needs
     *         element indices of 2^32 or more), {@code out} or {@code scratch} is {@code x} or {@code out} is
     *         {@code scratch}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if a dispatch exceeds the device's limits
     */
    public GpuCommandEncoder asum(GpuCommandEncoder encoder, GpuBuffer x, long xOff, int n, GpuBuffer out, long outOff,
            GpuBuffer scratch, long scratchOff) {
        checkReduction(encoder, x, xOff, x, xOff, n, out, outOff, scratch, scratchOff);
        return reduce(encoder, OP_ASUM, x, xOff, x, xOff, n, out, outOff, scratch, scratchOff, MODE_RESULT, 0);
    }

    /**
     * Records {@code out[outOff] = sqrt(Σ x[i]²)}, {@code Blasf.nrm2} of {@code n} elements from {@code xOff}: 0 for
     * {@code n == 0} or all zeros, NaN if an element is NaN, infinity if one is infinite. When the plain sum of
     * squares is not a normal number, the sum is recomputed with the elements scaled by a power of two, as on the
     * CPU; the passes for that are always recorded, and return at once when they are not needed. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param x the input
     * @param xOff the element index of the first element of {@code x}
     * @param n the number of elements, {@code >= 0}
     * @param out receives the float result
     * @param outOff the element index of the result in {@code out}
     * @param scratch at least {@link #scratchSize scratchSize(n)} elements from {@code scratchOff}, overwritten
     * @param scratchOff the element index of the first scratch element
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n < 0}, an offset is negative, a buffer is too short (or needs
     *         element indices of 2^32 or more), {@code out} or {@code scratch} is {@code x} or {@code out} is
     *         {@code scratch}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if a dispatch exceeds the device's limits
     */
    public GpuCommandEncoder nrm2(GpuCommandEncoder encoder, GpuBuffer x, long xOff, int n, GpuBuffer out,
            long outOff, GpuBuffer scratch, long scratchOff) {
        checkReduction(encoder, x, xOff, x, xOff, n, out, outOff, scratch, scratchOff);
        if (n == 0) {
            return reduce(encoder, OP_SUM_SQ, x, xOff, x, xOff, 0, out, outOff, scratch, scratchOff, MODE_RESULT, 0);
        }
        reduce(encoder, OP_SUM_SQ, x, xOff, x, xOff, n, out, outOff, scratch, scratchOff, MODE_NRM2, 0);
        largest(encoder, 1, x, xOff, n, out, outOff, scratch, scratchOff, NEED_MAX);
        return reduce(encoder, OP_SUM_SQ_SCALED, x, xOff, x, xOff, n, out, outOff, scratch, scratchOff,
                MODE_NRM2_SCALED, NEED_SCALED);
    }

    /**
     * Records {@code out[outOff] = } {@code Blasf.iamax} of {@code n} elements from {@code xOff}, as a 32-bit int:
     * the index (relative to {@code xOff}) of the first NaN if there is one, otherwise of the first element of
     * largest magnitude, or {@code -1} for {@code n == 0}. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param x the input
     * @param xOff the element index of the first element of {@code x}
     * @param n the number of elements, {@code >= 0}
     * @param out receives the int result
     * @param outOff the element index of the result in {@code out}
     * @param scratch at least {@link #scratchSize scratchSize(n)} elements from {@code scratchOff}, overwritten
     * @param scratchOff the element index of the first scratch element
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n < 0}, an offset is negative, a buffer is too short (or needs
     *         element indices of 2^32 or more), {@code out} or {@code scratch} is {@code x} or {@code out} is
     *         {@code scratch}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if a dispatch exceeds the device's limits
     */
    public GpuCommandEncoder iamax(GpuCommandEncoder encoder, GpuBuffer x, long xOff, int n, GpuBuffer out,
            long outOff, GpuBuffer scratch, long scratchOff) {
        checkReduction(encoder, x, xOff, x, xOff, n, out, outOff, scratch, scratchOff);
        return largest(encoder, 0, x, xOff, n, out, outOff, scratch, scratchOff, 0);
    }

    /**
     * Records {@code y[i] = fma(a, x[i], y[i])} for {@code n} elements, {@code Blasf.axpy}. {@code y} may be
     * {@code x} at the same offset; otherwise the two must be different buffers. Nothing is recorded when
     * {@code n == 0}. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param a the scale factor
     * @param x the input
     * @param xOff the element index of the first element of {@code x}
     * @param y the vector to add to; receives the result. May be {@code x} at the same offset
     * @param yOff the element index of the first element of {@code y}
     * @param n the number of elements, {@code >= 0}
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n < 0}, an offset is negative, a buffer is too short (or needs
     *         element indices of 2^32 or more), {@code y} is {@code x} at another offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder axpy(GpuCommandEncoder encoder, float a, GpuBuffer x, long xOff, GpuBuffer y, long yOff,
            int n) {
        Kernels.notNull(encoder, "encoder");
        Kernels.range(x, xOff, n, "x");
        Kernels.range(y, yOff, n, "y");
        boolean same = x == y;
        if (same && xOff != yOff) {
            throw new IllegalArgumentException("y may be x only at the same offset");
        }
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), (n + (long) GROUP - 1) / GROUP);
        if (same) {
            byte[] params = new Kernels.Params(PARAMS_AXPY_IN_PLACE.length).f32(a).u32(n).u32(xOff).u32(groups.x())
                    .bytes();
            return encoder.dispatch(axpyInPlace, Map.of(0, x), params, groups);
        }
        byte[] params = new Kernels.Params(PARAMS_AXPY.length).f32(a).u32(n).u32(xOff).u32(yOff).u32(groups.x())
                .bytes();
        return encoder.dispatch(axpyTwo, Map.of(0, x, 1, y), params, groups);
    }

    /**
     * Records {@code x[i] = a·x[i]} for {@code n} elements, in place, {@code Blasf.scal}. Nothing is recorded when
     * {@code n == 0}. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param a the scale factor
     * @param x the vector to scale; receives the result
     * @param xOff the element index of the first element of {@code x}
     * @param n the number of elements, {@code >= 0}
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n < 0}, the offset is negative, the buffer is too short (or needs
     *         element indices of 2^32 or more), or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder scal(GpuCommandEncoder encoder, float a, GpuBuffer x, long xOff, int n) {
        Kernels.notNull(encoder, "encoder");
        Kernels.range(x, xOff, n, "x");
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), (n + (long) GROUP - 1) / GROUP);
        byte[] params = new Kernels.Params(PARAMS_SCAL.length).f32(a).u32(n).u32(xOff).u32(groups.x()).bytes();
        return encoder.dispatch(scalPipe, Map.of(0, x), params, groups);
    }

    private static void checkReduction(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff,
            int n, GpuBuffer out, long outOff, GpuBuffer scratch, long scratchOff) {
        Kernels.notNull(encoder, "encoder");
        if (n < 0) {
            throw new IllegalArgumentException("negative size: " + n);
        }
        Kernels.range(x, xOff, n, "x");
        Kernels.range(y, yOff, n, "y");
        Kernels.range(out, outOff, 1, "out");
        Kernels.range(scratch, scratchOff, scratchSize(n), "scratch");
        if (out == x || out == y || scratch == x || scratch == y) {
            throw new IllegalArgumentException("out and scratch must be different buffers from the inputs");
        }
        if (out == scratch) {
            throw new IllegalArgumentException("out must be a different buffer from scratch");
        }
    }

    /**
     * The pairwise reduction {@code op} of {@code n} elements: the subtrees of the first pass, then the levels above
     * them FAN_DEPTH at a time, alternating between the two node regions of the scratch. The last pass
     * writes to {@code out} as {@code finalMode} says; with {@code need} not 0 every pass runs only in that nrm2
     * state.
     */
    private GpuCommandEncoder reduce(GpuCommandEncoder encoder, int op, GpuBuffer x, long xOff, GpuBuffer y,
            long yOff, int n, GpuBuffer out, long outOff, GpuBuffer scratch, long scratchOff, int finalMode,
            int need) {
        GpuCapabilities caps = device.capabilities();
        long blocks = blocks(n);
        int depth = depth(blocks);
        long state = scratchOff;
        long regionA = scratchOff + STATE;
        long regionB = regionA + regionA(n);
        if (n > 0) {
            Dimensions groups = Kernels.grid(caps, 1L << depth);
            byte[] params = new Kernels.Params(PARAMS_PARTIAL.length).u32(op).u32(n).u32(xOff).u32(yOff).u32(blocks)
                    .u32(depth).u32(regionA).u32(state).u32(need).u32(groups.x()).bytes();
            encoder.dispatch(partial, Map.of(0, x, 1, y, 2, scratch), params, groups);
        }
        int in = depth;
        long inOff = regionA;
        do {
            int to = Math.max(0, in - FAN_DEPTH);
            long toOff = inOff == regionA ? regionB : regionA;
            boolean last = to == 0;
            Dimensions groups = Kernels.grid(caps, ((1L << to) + COMBINE_GROUP - 1) / COMBINE_GROUP);
            byte[] params = new Kernels.Params(PARAMS_COMBINE.length).u32(blocks).u32(in).u32(to).u32(inOff)
                    .u32(last ? outOff : toOff).u32(last ? finalMode : MODE_NODES).u32(state).u32(need)
                    .u32(groups.x()).bytes();
            encoder.dispatch(combine, Map.of(0, scratch, 1, out), params, groups);
            in = to;
            inOff = toOff;
        } while (in > 0);
        return encoder;
    }

    /** The largest-magnitude passes: mode 0 for iamax (the index to {@code out}), 1 for nrm2's maximum. */
    private GpuCommandEncoder largest(GpuCommandEncoder encoder, int mode, GpuBuffer x, long xOff, int n,
            GpuBuffer out, long outOff, GpuBuffer scratch, long scratchOff, int need) {
        long partOff = scratchOff + STATE;
        int parts = n == 0 ? 0 : parts(n);
        Map<Integer, GpuBuffer> bind = Map.of(0, x, 1, scratch, 2, out);
        if (parts > 0) {
            Dimensions groups = Kernels.grid(device.capabilities(), parts);
            byte[] params = new Kernels.Params(PARAMS_AMAX.length).u32(mode).u32(n).u32(xOff).u32(0).u32(parts)
                    .u32(partOff).u32(scratchOff).u32(outOff).u32(need).u32(groups.x()).bytes();
            encoder.dispatch(amax, bind, params, groups);
        }
        byte[] params = new Kernels.Params(PARAMS_AMAX.length).u32(mode).u32(n).u32(xOff).u32(1).u32(parts)
                .u32(partOff).u32(scratchOff).u32(outOff).u32(need).u32(1).bytes();
        return encoder.dispatch(amax, bind, params, new Dimensions(1, 1, 1));
    }

    /** Releases the pipelines; work already recorded keeps them alive until it completes. Idempotent. */
    @Override
    public void close() {
        RuntimeException failure = null;
        for (GpuComputePipeline p : List.of(partial, combine, amax, axpyTwo, axpyInPlace, scalPipe)) {
            try {
                p.close();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
