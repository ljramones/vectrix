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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dynamisengine.gpu.api.compute.Access;
import org.dynamisengine.gpu.api.compute.Dimensions;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuComputePipeline;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.KernelBundle;

/**
 * The GPU twin of Vectrix {@code nn.Embedding}: row gather by integer indices, and the gradient's scatter-add in the
 * CPU's order.
 * <p>
 * {@link #gather} reads its indices (int32) from a GPU buffer, so tokens produced on the GPU feed it directly; an
 * index outside {@code [0, rows)}, where Vectrix throws, gives a NaN output row. {@link #scatterAdd} needs a
 * {@link Plan}, built on the host from the indices: for each distinct row, its tokens in increasing order. One thread
 * per (row, element) then adds that row's gradients in token order, exactly the CPU's sum, with no atomics and no
 * two threads writing one element: bit for bit on IEEE devices. Indices are host data in training (they come from the
 * data loader), which is where the plan is built.
 *
 * @since 4.1.0
 */
public final class GpuEmbedding implements AutoCloseable {
    /** The gather kernel's parameter block (checked against the compiled kernel). */
    static final String[] PARAMS_GATHER = {"rows", "dim", "tableOff", "idxOff", "outOff", "count", "gridX"};
    /** The scatter-add kernel's parameter block. */
    static final String[] PARAMS_SCATTER = {"dim", "dOutOff", "tableOff", "planOff", "unique", "gridX"};
    private static final int GROUP = 256;
    private final GpuDevice device;
    private final GpuComputePipeline gather;
    private final GpuComputePipeline scatter;

    /**
     * The tokens of each distinct row of an index list, for {@link #scatterAdd}: {@link #data()} is the distinct rows,
     * then {@code unique + 1} start positions, then the token numbers grouped by row, ascending within each row.
     *
     * @param unique the number of distinct rows
     * @param count the number of indices
     * @param rows the table's row count the indices were checked against
     * @param data the plan, {@code 2·unique + 1 + count} ints
     */
    public record Plan(int unique, int count, int rows, int[] data) {
        /**
         * Copies the data.
         *
         * @param unique the number of distinct rows
         * @param count the number of indices
         * @param rows the table's row count
         * @param data the plan
         */
        public Plan {
            data = data.clone();
        }

        /**
         * The plan's ints.
         *
         * @return a copy of the data
         */
        @Override
        public int[] data() {
            return data.clone();
        }

        /**
         * The plan's bytes for upload: int32, little-endian.
         *
         * @return the bytes
         */
        public byte[] bytes() {
            ByteBuffer b = ByteBuffer.allocate(data.length * 4).order(ByteOrder.LITTLE_ENDIAN);
            b.asIntBuffer().put(data);
            return b.array();
        }
    }

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipelines
     */
    public GpuEmbedding(GpuDevice device) {
        Set<String> types = Set.of("FP32", "INT32");
        List<GpuComputePipeline> made = new ArrayList<>();
        try {
            made.add(Kernels.pipeline(device, "embedding_gather", new Dimensions(GROUP, 1, 1), PARAMS_GATHER, types,
                    Access.READ, Access.READ, Access.WRITE));
            made.add(Kernels.pipeline(device, "embedding_scatter_add", new Dimensions(GROUP, 1, 1), PARAMS_SCATTER,
                    types, Access.READ, Access.READ, Access.READ_WRITE));
        } catch (RuntimeException e) {
            for (GpuComputePipeline p : made) {
                p.close();
            }
            throw e;
        }
        gather = made.get(0);
        scatter = made.get(1);
        this.device = device;
    }

    /**
     * The compiled gather kernel. Bindings: slot 0 the table, 1 the indices (int32), 2 the output; the parameter block
     * holds the 32-bit fields {@code rows, dim, tableOff, idxOff, outOff, count, gridX}, one thread per output element.
     *
     * @return the kernel bundle
     */
    public static KernelBundle gatherKernel() {
        return Kernels.bundle("embedding_gather");
    }

    /**
     * The compiled scatter-add kernel. Bindings: slot 0 the output gradient, 1 the plan (int32), 2 the table gradient;
     * the parameter block holds the 32-bit fields {@code dim, dOutOff, tableOff, planOff, unique, gridX}, one thread
     * per (distinct row, element).
     *
     * @return the kernel bundle
     */
    public static KernelBundle scatterKernel() {
        return Kernels.bundle("embedding_scatter_add");
    }

    /**
     * Builds the scatter-add plan of {@code count} indices, checking them as {@code Embedding.scatterAdd} does.
     *
     * @param idx the row indices of the forward gather
     * @param idxOff the offset in {@code idx}
     * @param count the number of indices, {@code >= 0}
     * @param rows the table's row count
     * @return the plan
     * @throws IllegalArgumentException if {@code idx} is null or too short, a size is negative, or an index is
     *         outside {@code [0, rows)}
     */
    public static Plan plan(int[] idx, int idxOff, int count, int rows) {
        Kernels.notNull(idx, "idx");
        if (count < 0 || rows < 0 || idxOff < 0 || idxOff > idx.length - count) {
            throw new IllegalArgumentException("idx must hold " + count + " indices from offset " + idxOff
                    + " and rows must be >= 0");
        }
        int[] perRow = new int[rows];
        for (int t = 0; t < count; t++) {
            int r = idx[idxOff + t];
            if (r < 0 || r >= rows) {
                throw new IllegalArgumentException("index " + r + " at " + t + " is outside [0, " + rows + ")");
            }
            perRow[r]++;
        }
        int unique = 0;
        for (int r = 0; r < rows; r++) {
            if (perRow[r] > 0) {
                unique++;
            }
        }
        int[] data = new int[2 * unique + 1 + count];
        int[] slot = new int[rows]; // the plan position of each distinct row
        int k = 0;
        int start = 0;
        for (int r = 0; r < rows; r++) {
            if (perRow[r] > 0) {
                data[k] = r;
                data[unique + k] = start;
                slot[r] = start;
                start += perRow[r];
                k++;
            }
        }
        data[2 * unique] = start;
        int tokens = 2 * unique + 1;
        for (int t = 0; t < count; t++) {
            int r = idx[idxOff + t];
            data[tokens + slot[r]++] = t; // ascending t within each row
        }
        return new Plan(unique, count, rows, data);
    }

    /**
     * Records {@code Embedding.gather}: output row {@code t} is table row {@code idx[idxOff + t]}.
     *
     * @param encoder the encoder to record into
     * @param table the table, {@code rows × dim} floats, row-major
     * @param tableOff the element index in {@code table} of the table
     * @param rows the number of table rows
     * @param dim the row length
     * @param idx the row indices, int32
     * @param idxOff the element index in {@code idx} of the first index
     * @param count the number of indices
     * @param out receives {@code count × dim} floats; a different buffer from the inputs
     * @param outOff the element index in {@code out} of the first output
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size or offset is negative, a buffer is too small, {@code out} is an input
     *         buffer, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder gather(GpuCommandEncoder encoder, GpuBuffer table, long tableOff, int rows, int dim,
            GpuBuffer idx, long idxOff, int count, GpuBuffer out, long outOff) {
        Kernels.notNull(encoder, "encoder");
        if (rows < 0 || dim < 0 || count < 0) {
            throw new IllegalArgumentException("rows, dim and count must be >= 0");
        }
        Kernels.range(table, tableOff, (long) rows * dim, "table");
        Kernels.range(idx, idxOff, count, "idx");
        Kernels.range(out, outOff, (long) count * dim, "out");
        if (out == table || out == idx) {
            throw new IllegalArgumentException("out must be a different buffer from the table and the indices");
        }
        long elements = (long) count * dim;
        if (elements == 0) {
            return encoder;
        }
        below32(elements, tableOff + (long) rows * dim, outOff + elements, idxOff + count);
        Dimensions groups = Kernels.grid(device.capabilities(), (elements + GROUP - 1) / GROUP);
        byte[] params = new Kernels.Params(PARAMS_GATHER).u32(rows).u32(dim).u32(tableOff).u32(idxOff).u32(outOff)
                .u32(count).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, gather, Map.of(0, table, 1, idx, 2, out), params, groups);
    }

    /**
     * Records {@code Embedding.scatterAdd}: adds gradient row {@code t} into table-gradient row {@code idx[t]}, rows
     * selected several times summing in token order.
     *
     * @param encoder the encoder to record into
     * @param dOut the output gradient, {@code count × dim} floats
     * @param dOutOff the element index in {@code dOut} of the first gradient
     * @param plan the plan of the indices ({@link #plan})
     * @param planBuffer a buffer holding {@code plan.bytes()} from element {@code planOff}
     * @param planOff the element index in {@code planBuffer} of the plan
     * @param dim the row length
     * @param dTable the table gradient, {@code plan.rows() × dim} floats, accumulated into; a different buffer from
     *        the inputs
     * @param tableOff the element index in {@code dTable} of the table gradient
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size or offset is negative, a buffer is too small, {@code dTable} is an
     *         input buffer, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder scatterAdd(GpuCommandEncoder encoder, GpuBuffer dOut, long dOutOff, Plan plan,
            GpuBuffer planBuffer, long planOff, int dim, GpuBuffer dTable, long tableOff) {
        Kernels.notNull(encoder, "encoder");
        Kernels.notNull(plan, "plan");
        if (dim < 0) {
            throw new IllegalArgumentException("dim must be >= 0");
        }
        Kernels.range(dOut, dOutOff, (long) plan.count() * dim, "dOut");
        Kernels.range(planBuffer, planOff, plan.data.length, "planBuffer");
        Kernels.range(dTable, tableOff, (long) plan.rows() * dim, "dTable");
        if (dTable == dOut || dTable == planBuffer) {
            throw new IllegalArgumentException("dTable must be a different buffer from dOut and the plan");
        }
        long threads = (long) plan.unique() * dim;
        if (threads == 0) {
            return encoder;
        }
        below32(threads, dOutOff + (long) plan.count() * dim, tableOff + (long) plan.rows() * dim,
                planOff + plan.data.length);
        Dimensions groups = Kernels.grid(device.capabilities(), (threads + GROUP - 1) / GROUP);
        byte[] params = new Kernels.Params(PARAMS_SCATTER).u32(dim).u32(dOutOff).u32(tableOff).u32(planOff)
                .u32(plan.unique()).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, scatter, Map.of(0, dOut, 1, planBuffer, 2, dTable), params, groups);
    }

    private static void below32(long... ends) {
        for (long e : ends) {
            if (e > 0xFFFF_FFFFL) {
                throw new IllegalArgumentException("element indices must stay below 2^32");
            }
        }
    }

    @Override
    public void close() {
        try {
            gather.close();
        } finally {
            scatter.close();
        }
    }
}
