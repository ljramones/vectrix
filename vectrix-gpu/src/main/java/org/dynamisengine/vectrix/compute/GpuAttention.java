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
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuComputePipeline;
import org.dynamisengine.gpu.api.compute.GpuDevice;

/**
 * The GPU twin of Vectrix {@code nn.Attention}: scaled dot-product attention, forward and backward, for many queries
 * (and query heads) per call.
 * <p>
 * Each query is exactly one call of the CPU method: {@link #forward} is {@code Attention.forward} per query,
 * {@link #groupedQuery} is {@code Attention.groupedQuery} per position, and the backward passes are
 * {@code Attention.backward} and {@code Attention.groupedQueryBackward} called for the queries in order, so the key
 * and value gradients accumulate in the CPU's order. Every number is computed as Vectrix computes it: the scores are
 * {@code scale·Blasf.dot(q, K_j)} plus the mask, the softmax is {@code Softmax.softmax}'s, the weighted sum is the
 * transposed {@code Gemmf.gemv}, and the backward pass uses {@code Blasf.axpy}, {@code Blasf.dot} and
 * {@code Softmax.softmaxBackward}, in the same order. On an IEEE device the results are the bits Vectrix computes
 * (see the package comment for subnormals: a probability below {@code 2^-126} becomes 0 on GPUs that flush
 * subnormals).
 * <p>
 * <b>Causal attention.</b> With {@code causal} set, the queries are the last {@code queries} positions of the
 * {@code t} keys: query {@code r} (from 0) attends to the first {@code t - queries + 1 + r} keys, as the CPU method
 * does when it is passed {@code t = position + 1}. The last query sees all {@code t} keys; with {@code queries == t}
 * this is causal self-attention over a whole sequence. Probability entries past a query's keys are not written. The
 * CPU method's additive {@code mask} applies as well, to the keys a query attends to; a mask of {@code -∞} for every
 * key makes that query's probabilities and output NaN, as on the CPU.
 * <p>
 * Five pipelines, compiled when the instance is created. The forward pass records three dispatches (scores, softmax,
 * weighted sum) and the backward pass five; dispatches in one encoder see each other's writes.
 *
 * @since 3.2.0
 */
public final class GpuAttention implements AutoCloseable {

    /** The parameter block of the scores kernel (checked against the compiled kernel). */
    static final String[] PARAMS_SCORES = {"rows", "heads", "group", "queries", "t", "causal", "width", "scaled",
        "scale", "xOff", "ldx", "mOff", "ldm", "masked", "maskOff", "ldMask", "outOff", "ldOut", "chunks", "gridX"};

    /** The parameter block of the in-place softmax kernel (checked against the compiled kernel). */
    static final String[] PARAMS_SOFTMAX = {"rows", "heads", "queries", "t", "causal", "pOff", "ldp", "gridX"};

    /** The parameter block of the softmax backward kernel (checked against the compiled kernel). */
    static final String[] PARAMS_SOFTMAX_BACKWARD = {"rows", "heads", "queries", "t", "causal", "pOff", "ldp",
        "dsOff", "ldds", "gridX"};

    /** The parameter block of the weighted-sum kernel (checked against the compiled kernel). */
    static final String[] PARAMS_MIX = {"rows", "heads", "group", "queries", "t", "causal", "width", "alpha", "mOff",
        "ldm", "wOff", "ldw", "outOff", "ldOut", "chunks", "gridX"};

    /** The parameter block of the gradient accumulation kernel (checked against the compiled kernel). */
    static final String[] PARAMS_ACCUMULATE = {"heads", "group", "kvHeads", "queries", "t", "causal", "width",
        "scaled", "scale", "wOff", "ldw", "xOff", "ldx", "destOff", "ldDest", "chunks", "gridX"};

    /** The most keys a query can attend to: 1024 reduction blocks of 256, the softmax kernels' group memory. */
    public static final int MAX_KEYS = 1024 * 256;

    /** Threads per group of the per-element kernels, and the keys or columns each of their groups covers. */
    private static final int CHUNK = 64;

    /** Threads per group of the per-row softmax kernels. */
    private static final int ROW_GROUP = 256;

    private final GpuDevice device;

    private final GpuComputePipeline scores;

    private final GpuComputePipeline softmax;

    private final GpuComputePipeline softmaxBackward;

    private final GpuComputePipeline mix;

    private final GpuComputePipeline accumulate;

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipelines
     */
    public GpuAttention(GpuDevice device) {
        Kernels.notNull(device, "device");
        Dimensions chunk = new Dimensions(CHUNK, 1, 1);
        Dimensions row = new Dimensions(ROW_GROUP, 1, 1);
        Set<String> fp32 = Set.of("FP32");
        List<GpuComputePipeline> made = new ArrayList<>();
        try {
            made.add(Kernels.pipeline(device, "attention_scores", chunk, PARAMS_SCORES, fp32, Access.READ,
                    Access.READ, Access.READ, Access.WRITE));
            made.add(Kernels.pipeline(device, "attention_softmax", row, PARAMS_SOFTMAX, fp32,
                    Access.READ_WRITE));
            made.add(Kernels.pipeline(device, "attention_softmax_backward", row, PARAMS_SOFTMAX_BACKWARD, fp32,
                    Access.READ, Access.READ_WRITE));
            made.add(Kernels.pipeline(device, "attention_mix", chunk, PARAMS_MIX, fp32, Access.READ,
                    Access.READ, Access.WRITE));
            made.add(Kernels.pipeline(device, "attention_accumulate", chunk, PARAMS_ACCUMULATE, fp32,
                    Access.READ, Access.READ, Access.READ_WRITE));
        } catch (RuntimeException e) {
            for (GpuComputePipeline p : made) {
                p.close();
            }
            throw e;
        }
        scores = made.get(0);
        softmax = made.get(1);
        softmaxBackward = made.get(2);
        mix = made.get(3);
        accumulate = made.get(4);
        this.device = device;
    }

    /**
     * The scratch the backward passes need: {@code queries·heads·t} floats, one row of score gradients per query
     * head (pass {@code heads = 1} for {@link #backward}).
     *
     * @param queries the number of queries (positions for {@link #groupedQueryBackward}), {@code >= 0}
     * @param heads the number of query heads, {@code >= 1}
     * @param t the number of keys, {@code >= 1}
     * @return the number of floats
     * @throws IllegalArgumentException if a size is out of range
     */
    public static long backwardScratchSize(int queries, int heads, int t) {
        if (queries < 0 || heads < 1 || t < 1) {
            throw new IllegalArgumentException("need queries >= 0, heads >= 1, t >= 1: " + queries + ", " + heads
                    + ", " + t);
        }
        return (long) queries * heads * t;
    }

    /**
     * Records the forward pass of {@code queries} queries against {@code t} keys and values: for each query
     * {@code r}, exactly {@code Attention.forward(q_r, d, k, ldk, v, ldv, dv, t_r, scale, mask_r, p_r, out_r)}, with
     * {@code t_r = t} or, when {@code causal}, {@code t - queries + 1 + r} (see the class comment). Query {@code r}
     * starts at {@code qOff + r·ldq}, its mask row at {@code maskOff + r·ldMask} ({@code ldMask = 0} shares one mask
     * row), its probabilities at {@code pOff + r·ldp} and its output at {@code outOff + r·ldOut}. Keys and values are
     * shared by all queries.
     * <p>
     * {@code p} and {@code out} must be different buffers from each other and from the inputs. Nothing is recorded
     * when {@code queries} is 0. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param queries the number of queries, {@code >= 0}
     * @param q the queries, {@code queries} rows of {@code d} floats
     * @param qOff the element index of query 0
     * @param ldq the stride between queries, at least {@code d}
     * @param d the key dimension, at least 1
     * @param k the keys, {@code t} rows of {@code d} floats
     * @param kOff the element index of key row 0
     * @param ldk the row stride of the keys, at least {@code d}
     * @param v the values, {@code t} rows of {@code dv} floats
     * @param vOff the element index of value row 0
     * @param ldv the row stride of the values, at least {@code dv}
     * @param dv the value dimension, at least 1
     * @param t the number of keys, from 1 to {@link #MAX_KEYS}; at least {@code queries} when {@code causal}
     * @param causal whether query {@code r} attends only to the first {@code t - queries + 1 + r} keys
     * @param scale usually {@code 1/√d}
     * @param mask additive biases, a row of {@code t} per query, or {@code null}
     * @param maskOff the element index of the first mask row; ignored if {@code mask} is null
     * @param ldMask the stride between mask rows, {@code >= 0}; 0 for one row shared by every query; ignored if
     *        {@code mask} is null
     * @param p receives the probabilities, a row of {@code t} per query (only a query's keys are written)
     * @param pOff the element index of the first probability row
     * @param ldp the stride between probability rows, at least {@code t}
     * @param out receives the attended values, a row of {@code dv} per query
     * @param outOff the element index of the first output row
     * @param ldOut the stride between output rows, at least {@code dv}
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size is out of range, a matrix does not fit its buffer with the given
     *         offset and stride (or needs element indices of 2^32 or more), {@code p} or {@code out} is another
     *         argument's buffer, or a buffer or the encoder is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if a dispatch exceeds the device's limits
     */
    public GpuCommandEncoder forward(GpuCommandEncoder encoder, int queries, GpuBuffer q, long qOff, int ldq, int d,
            GpuBuffer k, long kOff, int ldk, GpuBuffer v, long vOff, int ldv, int dv, int t, boolean causal,
            float scale, GpuBuffer mask, long maskOff, int ldMask, GpuBuffer p, long pOff, int ldp, GpuBuffer out,
            long outOff, int ldOut) {
        Shape s = shape(encoder, queries, 1, 1, d, dv, t, causal);
        checkForward(s, q, qOff, ldq, k, kOff, ldk, v, vOff, ldv, mask, maskOff, ldMask, p, pOff, ldp, out, outOff,
                ldOut);
        return recordForward(encoder, s, scale, q, qOff, ldq, k, kOff, ldk, v, vOff, ldv, mask, maskOff, ldMask, p,
                pOff, ldp, out, outOff, ldOut);
    }

    /**
     * Records the backward pass of {@link #forward}, from its probabilities: for each query {@code r} in order,
     * exactly {@code Attention.backward(q_r, d, k, ldk, v, ldv, dv, t_r, scale, p_r, dOut_r, dq_r, dk, dV)}. It writes
     * {@code dq} and <i>accumulates</i> into {@code dk} and {@code dV} (keys and values are shared by every query, so
     * their gradients add up over the queries, in query order). {@code dk} uses the key stride {@code ldk} and
     * {@code dV} the value stride {@code ldv}, as on the CPU. The layouts and {@code causal} are those of
     * {@link #forward}; {@code scratch} replaces the CPU method's scratch.
     * <p>
     * {@code dq}, {@code dk}, {@code dV} and {@code scratch} must be different buffers from each other and from the
     * inputs. Nothing is recorded when {@code queries} is 0. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param queries the number of queries, {@code >= 0}
     * @param q the queries, {@code queries} rows of {@code d} floats
     * @param qOff the element index of query 0
     * @param ldq the stride between queries, at least {@code d}
     * @param d the key dimension, at least 1
     * @param k the keys, {@code t} rows of {@code d} floats
     * @param kOff the element index of key row 0
     * @param ldk the row stride of the keys and of {@code dk}, at least {@code d}
     * @param v the values, {@code t} rows of {@code dv} floats
     * @param vOff the element index of value row 0
     * @param ldv the row stride of the values and of {@code dV}, at least {@code dv}
     * @param dv the value dimension, at least 1
     * @param t the number of keys, from 1 to {@link #MAX_KEYS}; at least {@code queries} when {@code causal}
     * @param causal whether the forward pass was causal
     * @param scale the scale of the forward pass
     * @param p the forward probabilities, a row of {@code t} per query
     * @param pOff the element index of the first probability row
     * @param ldp the stride between probability rows, at least {@code t}
     * @param dOut the gradient of the output, a row of {@code dv} per query
     * @param dOutOff the element index of the first output gradient row
     * @param ldDOut the stride between output gradient rows, at least {@code dv}
     * @param dq receives the query gradients, a row of {@code d} per query
     * @param dqOff the element index of the first query gradient row
     * @param ldDq the stride between query gradient rows, at least {@code d}
     * @param dk the key gradients, {@code t} rows with stride {@code ldk}, added to
     * @param dkOff the element index of key gradient row 0
     * @param dV the value gradients, {@code t} rows with stride {@code ldv}, added to
     * @param dvOff the element index of value gradient row 0
     * @param scratch {@link #backwardScratchSize backwardScratchSize(queries, 1, t)} floats of working memory
     * @param scratchOff the element index of the scratch
     * @return {@code encoder}
     * @throws IllegalArgumentException if a size is out of range, a matrix does not fit its buffer with the given
     *         offset and stride (or needs element indices of 2^32 or more), a written buffer is another argument's
     *         buffer, or a buffer or the encoder is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if a dispatch exceeds the device's limits
     */
    public GpuCommandEncoder backward(GpuCommandEncoder encoder, int queries, GpuBuffer q, long qOff, int ldq, int d,
            GpuBuffer k, long kOff, int ldk, GpuBuffer v, long vOff, int ldv, int dv, int t, boolean causal,
            float scale, GpuBuffer p, long pOff, int ldp, GpuBuffer dOut, long dOutOff, int ldDOut, GpuBuffer dq,
            long dqOff, int ldDq, GpuBuffer dk, long dkOff, GpuBuffer dV, long dvOff, GpuBuffer scratch,
            long scratchOff) {
        Shape s = shape(encoder, queries, 1, 1, d, dv, t, causal);
        checkBackward(s, q, qOff, ldq, k, kOff, ldk, v, vOff, ldv, p, pOff, ldp, dOut, dOutOff, ldDOut, dq, dqOff,
                ldDq, dk, dkOff, dV, dvOff, scratch, scratchOff);
        return recordBackward(encoder, s, scale, q, qOff, ldq, k, kOff, ldk, v, vOff, ldv, p, pOff, ldp, dOut,
                dOutOff, ldDOut, dq, dqOff, ldDq, dk, dkOff, dV, dvOff, scratch, scratchOff);
    }

    /**
     * Records grouped-query attention for {@code positions} positions: for each position {@code r}, exactly
     * {@code Attention.groupedQuery(q_r, heads, d, kCache, vCache, kvHeads, dv, t_r, scale, mask_r, p_r, out_r)},
     * with {@code t_r} as in {@link #forward}. Query head {@code h} uses key/value head {@code h / (heads / kvHeads)}.
     * <p>
     * Layouts, as on the CPU with the positions one after another: {@code q} is {@code positions × heads × d} from
     * {@code qOff}; each cache row (one key) holds every key/value head, {@code kvHeads × d} keys and
     * {@code kvHeads × dv} values; {@code out} is {@code positions × heads × dv} from {@code outOff}; {@code p} is
     * {@code positions × heads} rows of {@code t} probabilities from {@code pOff} (a causal position's rows are
     * written only up to its {@code t_r} keys); the mask row of position {@code r}, shared by its heads, starts at
     * {@code maskOff + r·ldMask}.
     * <p>
     * {@code p} and {@code out} must be different buffers from each other and from the inputs. Nothing is recorded
     * when {@code positions} is 0. All arguments are checked before anything is recorded.
     *
     * @param encoder the encoder to record into
     * @param positions the number of positions, {@code >= 0}
     * @param q the queries, {@code positions × heads × d} floats
     * @param qOff the element index of the first query
     * @param heads the number of query heads, a positive multiple of {@code kvHeads}
     * @param d the key dimension per head, at least 1
     * @param kCache the key cache, {@code t} rows of {@code kvHeads × d} floats
     * @param kOff the element index of key row 0
     * @param vCache the value cache, {@code t} rows of {@code kvHeads × dv} floats
     * @param vOff the element index of value row 0
     * @param kvHeads the number of key/value heads, at least 1
     * @param dv the value dimension per head, at least 1
     * @param t the number of cached positions, from 1 to {@link #MAX_KEYS}; at least {@code positions} when
     *        {@code causal}
     * @param causal whether position {@code r} attends only to the first {@code t - positions + 1 + r} keys
     * @param scale usually {@code 1/√d}
     * @param mask additive biases, a row of {@code t} per position shared by its heads, or {@code null}
     * @param maskOff the element index of the first mask row; ignored if {@code mask} is null
     * @param ldMask the stride between mask rows, {@code >= 0}; 0 for one row shared by every position; ignored if
     *        {@code mask} is null
     * @param p receives {@code positions × heads × t} probabilities
     * @param pOff the element index of the first probability
     * @param out receives {@code positions × heads × dv} floats
     * @param outOff the element index of the first output
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code heads} is not a positive multiple of {@code kvHeads}, a size is out
     *         of range, an array does not fit its buffer (or needs element indices of 2^32 or more), {@code p} or
     *         {@code out} is another argument's buffer, or a buffer or the encoder is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if a dispatch exceeds the device's limits
     */
    public GpuCommandEncoder groupedQuery(GpuCommandEncoder encoder, int positions, GpuBuffer q, long qOff, int heads,
            int d, GpuBuffer kCache, long kOff, GpuBuffer vCache, long vOff, int kvHeads, int dv, int t,
            boolean causal, float scale, GpuBuffer mask, long maskOff, int ldMask, GpuBuffer p, long pOff,
            GpuBuffer out, long outOff) {
        Shape s = shape(encoder, positions, heads, kvHeads, d, dv, t, causal);
        int kRow = s.kRow();
        int vRow = s.vRow();
        checkForward(s, q, qOff, d, kCache, kOff, kRow, vCache, vOff, vRow, mask, maskOff, ldMask, p, pOff, t, out,
                outOff, dv);
        return recordForward(encoder, s, scale, q, qOff, d, kCache, kOff, kRow, vCache, vOff, vRow, mask, maskOff,
                ldMask, p, pOff, t, out, outOff, dv);
    }

    /**
     * Records the backward pass of {@link #groupedQuery}: for each position {@code r} in order, exactly
     * {@code Attention.groupedQueryBackward(q_r, heads, d, kCache, vCache, kvHeads, dv, t_r, scale, p_r, dOut_r, dq_r,
     * dk, dV)}. It writes {@code dq} ({@code positions × heads × d}) and accumulates the key and value gradients into
     * {@code dk} and {@code dV}, laid out like the caches: positions in order and, within each, the query heads that
     * share a key/value head in head order. The other layouts are those of {@link #groupedQuery}; {@code dOut} is laid
     * out like its {@code out}.
     * <p>
     * {@code dq}, {@code dk}, {@code dV} and {@code scratch} must be different buffers from each other and from the
     * inputs. Nothing is recorded when {@code positions} is 0. All arguments are checked before anything is
     * recorded.
     *
     * @param encoder the encoder to record into
     * @param positions the number of positions, {@code >= 0}
     * @param q the queries, {@code positions × heads × d} floats
     * @param qOff the element index of the first query
     * @param heads the number of query heads, a positive multiple of {@code kvHeads}
     * @param d the key dimension per head, at least 1
     * @param kCache the key cache, {@code t} rows of {@code kvHeads × d} floats
     * @param kOff the element index of key row 0
     * @param vCache the value cache, {@code t} rows of {@code kvHeads × dv} floats
     * @param vOff the element index of value row 0
     * @param kvHeads the number of key/value heads, at least 1
     * @param dv the value dimension per head, at least 1
     * @param t the number of cached positions, from 1 to {@link #MAX_KEYS}; at least {@code positions} when
     *        {@code causal}
     * @param causal whether the forward pass was causal
     * @param scale the scale of the forward pass
     * @param p the forward probabilities, {@code positions × heads × t} floats
     * @param pOff the element index of the first probability
     * @param dOut the gradient of the output, {@code positions × heads × dv} floats
     * @param dOutOff the element index of the first output gradient
     * @param dq receives the query gradients, {@code positions × heads × d} floats
     * @param dqOff the element index of the first query gradient
     * @param dk the key gradients, laid out like {@code kCache}, added to
     * @param dkOff the element index of key gradient row 0
     * @param dV the value gradients, laid out like {@code vCache}, added to
     * @param dvOff the element index of value gradient row 0
     * @param scratch {@link #backwardScratchSize backwardScratchSize(positions, heads, t)} floats of working memory
     * @param scratchOff the element index of the scratch
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code heads} is not a positive multiple of {@code kvHeads}, a size is out
     *         of range, an array does not fit its buffer (or needs element indices of 2^32 or more), a written buffer
     *         is another argument's buffer, or a buffer or the encoder is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if a dispatch exceeds the device's limits
     */
    public GpuCommandEncoder groupedQueryBackward(GpuCommandEncoder encoder, int positions, GpuBuffer q, long qOff,
            int heads, int d, GpuBuffer kCache, long kOff, GpuBuffer vCache, long vOff, int kvHeads, int dv, int t,
            boolean causal, float scale, GpuBuffer p, long pOff, GpuBuffer dOut, long dOutOff, GpuBuffer dq,
            long dqOff, GpuBuffer dk, long dkOff, GpuBuffer dV, long dvOff, GpuBuffer scratch, long scratchOff) {
        Shape s = shape(encoder, positions, heads, kvHeads, d, dv, t, causal);
        int kRow = s.kRow();
        int vRow = s.vRow();
        checkBackward(s, q, qOff, d, kCache, kOff, kRow, vCache, vOff, vRow, p, pOff, t, dOut, dOutOff, dv, dq, dqOff,
                d, dk, dkOff, dV, dvOff, scratch, scratchOff);
        return recordBackward(encoder, s, scale, q, qOff, d, kCache, kOff, kRow, vCache, vOff, vRow, p, pOff, t, dOut,
                dOutOff, dv, dq, dqOff, d, dk, dkOff, dV, dvOff, scratch, scratchOff);
    }

    /** Releases the pipelines; work already recorded keeps them alive until it completes. Idempotent. */
    @Override
    public void close() {
        try {
            scores.close();
        } finally {
            try {
                softmax.close();
            } finally {
                try {
                    softmaxBackward.close();
                } finally {
                    try {
                        mix.close();
                    } finally {
                        accumulate.close();
                    }
                }
            }
        }
    }

    /**
     * The sizes of a call: {@code rows = queries·heads} attention rows (row {@code r·heads + h} is head {@code h} of
     * query {@code r}), query head {@code h} reading key/value head {@code h / group}.
     */
    private record Shape(int queries, int heads, int kvHeads, int group, int d, int dv, int t, boolean causal,
            int rows) {

        int kRow() {
            return kvHeads * d;
        }

        int vRow() {
            return kvHeads * dv;
        }
    }

    private static Shape shape(GpuCommandEncoder encoder, int queries, int heads, int kvHeads, int d, int dv, int t,
            boolean causal) {
        Kernels.notNull(encoder, "encoder");
        if (heads < 1 || kvHeads < 1 || heads % kvHeads != 0) {
            throw new IllegalArgumentException("heads must be a positive multiple of kvHeads: " + heads + ", "
                    + kvHeads);
        }
        if (t < 1 || d < 1 || dv < 1) {
            throw new IllegalArgumentException("need t, d, dv >= 1");
        }
        if (queries < 0) {
            throw new IllegalArgumentException("negative number of queries: " + queries);
        }
        if (t > MAX_KEYS) {
            throw new IllegalArgumentException("more than " + MAX_KEYS + " keys: " + t);
        }
        if (causal && t < queries) {
            throw new IllegalArgumentException("causal attention needs at least as many keys as queries: " + t + " < "
                    + queries);
        }
        if ((long) kvHeads * d > Integer.MAX_VALUE || (long) kvHeads * dv > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("a cache row does not fit in an int stride");
        }
        long rows = (long) queries * heads;
        if (rows > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("too many query rows: " + rows);
        }
        return new Shape(queries, heads, kvHeads, heads / kvHeads, d, dv, t, causal, (int) rows);
    }

    private static void checkForward(Shape s, GpuBuffer q, long qOff, int ldq, GpuBuffer k, long kOff, int ldk,
            GpuBuffer v, long vOff, int ldv, GpuBuffer mask, long maskOff, int ldMask, GpuBuffer p, long pOff, int ldp,
            GpuBuffer out, long outOff, int ldOut) {
        Kernels.matrix(q, qOff, ldq, s.rows(), s.d(), "q");
        Kernels.matrix(k, kOff, ldk, s.t(), s.kRow(), "k");
        Kernels.matrix(v, vOff, ldv, s.t(), s.vRow(), "v");
        if (mask != null) {
            if (ldMask < 0) {
                throw new IllegalArgumentException("negative mask stride: " + ldMask);
            }
            Kernels.range(mask, maskOff, s.queries() == 0 ? 0 : (long) (s.queries() - 1) * ldMask + s.t(), "mask");
        }
        Kernels.matrix(p, pOff, ldp, s.rows(), s.t(), "p");
        Kernels.matrix(out, outOff, ldOut, s.rows(), s.dv(), "out");
        written(p, "p", q, k, v, mask, out);
        written(out, "out", q, k, v, mask);
    }

    private static void checkBackward(Shape s, GpuBuffer q, long qOff, int ldq, GpuBuffer k, long kOff, int ldk,
            GpuBuffer v, long vOff, int ldv, GpuBuffer p, long pOff, int ldp, GpuBuffer dOut, long dOutOff,
            int ldDOut, GpuBuffer dq, long dqOff, int ldDq, GpuBuffer dk, long dkOff, GpuBuffer dV, long dvOff,
            GpuBuffer scratch, long scratchOff) {
        Kernels.matrix(q, qOff, ldq, s.rows(), s.d(), "q");
        Kernels.matrix(k, kOff, ldk, s.t(), s.kRow(), "k");
        Kernels.matrix(v, vOff, ldv, s.t(), s.vRow(), "v");
        Kernels.matrix(p, pOff, ldp, s.rows(), s.t(), "p");
        Kernels.matrix(dOut, dOutOff, ldDOut, s.rows(), s.dv(), "dOut");
        Kernels.matrix(dq, dqOff, ldDq, s.rows(), s.d(), "dq");
        Kernels.matrix(dk, dkOff, ldk, s.t(), s.kRow(), "dk");
        Kernels.matrix(dV, dvOff, ldv, s.t(), s.vRow(), "dV");
        Kernels.range(scratch, scratchOff, (long) s.rows() * s.t(), "scratch");
        written(dq, "dq", q, k, v, p, dOut, dk, dV, scratch);
        written(dk, "dk", q, k, v, p, dOut, dV, scratch);
        written(dV, "dV", q, k, v, p, dOut, scratch);
        written(scratch, "scratch", q, k, v, p, dOut);
    }

    /** Rejects a written buffer that is also one of {@code others} (null entries never match). */
    private static void written(GpuBuffer target, String name, GpuBuffer... others) {
        for (GpuBuffer o : others) {
            if (o == target) {
                throw new IllegalArgumentException(name + " must be a different buffer from the other arguments");
            }
        }
    }

    private static int chunks(int n) {
        return (n + CHUNK - 1) / CHUNK;
    }

    private GpuCommandEncoder recordForward(GpuCommandEncoder encoder, Shape s, float scale, GpuBuffer q, long qOff,
            int ldq, GpuBuffer k, long kOff, int ldk, GpuBuffer v, long vOff, int ldv, GpuBuffer mask, long maskOff,
            int ldMask, GpuBuffer p, long pOff, int ldp, GpuBuffer out, long outOff, int ldOut) {
        if (s.rows() == 0) {
            return encoder;
        }
        boolean masked = mask != null;
        // p_j = scale·Blasf.dot(q, K_j, d) + mask_j; without a mask the slot is bound to q and not read
        recordScores(encoder, s, s.d(), true, scale, q, qOff, ldq, k, kOff, ldk, masked ? mask : q,
                masked ? maskOff : 0, masked ? ldMask : 0, masked, p, pOff, ldp);
        // Softmax.softmax(p), in place
        Dimensions rowGroups = Kernels.grid(device.capabilities(), s.rows());
        byte[] params = new Kernels.Params(PARAMS_SOFTMAX)
                .u32(s.rows()).u32(s.heads()).u32(s.queries()).u32(s.t()).u32(s.causal() ? 1 : 0)
                .u32(pOff).u32(ldp).u32(rowGroups.x()).bytes();
        Kernels.dispatch(encoder, softmax, Map.of(0, p), params, rowGroups);
        // out = Vᵀ·p
        recordMix(encoder, s, s.dv(), 1f, v, vOff, ldv, p, pOff, ldp, out, outOff, ldOut);
        return encoder;
    }

    private GpuCommandEncoder recordBackward(GpuCommandEncoder encoder, Shape s, float scale, GpuBuffer q, long qOff,
            int ldq, GpuBuffer k, long kOff, int ldk, GpuBuffer v, long vOff, int ldv, GpuBuffer p, long pOff,
            int ldp, GpuBuffer dOut, long dOutOff, int ldDOut, GpuBuffer dq, long dqOff, int ldDq, GpuBuffer dk,
            long dkOff, GpuBuffer dV, long dvOff, GpuBuffer scratch, long scratchOff) {
        if (s.rows() == 0) {
            return encoder;
        }
        int t = s.t();
        // ds_j = Blasf.dot(dOut, V_j, dv): a row of t per attention row in the scratch
        recordScores(encoder, s, s.dv(), false, 1f, dOut, dOutOff, ldDOut, v, vOff, ldv, dOut, 0, 0, false, scratch,
                scratchOff, t);
        // ds = Softmax.softmaxBackward(p, ds), in place
        Dimensions rowGroups = Kernels.grid(device.capabilities(), s.rows());
        byte[] params = new Kernels.Params(PARAMS_SOFTMAX_BACKWARD)
                .u32(s.rows()).u32(s.heads()).u32(s.queries()).u32(t).u32(s.causal() ? 1 : 0)
                .u32(pOff).u32(ldp).u32(scratchOff).u32(t).u32(rowGroups.x()).bytes();
        Kernels.dispatch(encoder, softmaxBackward, Map.of(0, p, 1, scratch), params, rowGroups);
        // dq = scale·Kᵀ·ds
        recordMix(encoder, s, s.d(), scale, k, kOff, ldk, scratch, scratchOff, t, dq, dqOff, ldDq);
        // dK_j += (scale·ds_j)·q and dV_j += p_j·dOut, over the attention rows in order
        recordAccumulate(encoder, s, s.d(), true, scale, scratch, scratchOff, t, q, qOff, ldq, dk, dkOff, ldk);
        recordAccumulate(encoder, s, s.dv(), false, 1f, p, pOff, ldp, dOut, dOutOff, ldDOut, dV, dvOff, ldv);
        return encoder;
    }

    private void recordScores(GpuCommandEncoder encoder, Shape s, int width, boolean scaled, float scale, GpuBuffer x,
            long xOff, int ldx, GpuBuffer m, long mOff, int ldm, GpuBuffer mask, long maskOff, int ldMask,
            boolean masked, GpuBuffer out, long outOff, int ldOut) {
        int chunks = chunks(s.t());
        Dimensions groups = Kernels.grid(device.capabilities(), (long) s.rows() * chunks);
        byte[] params = new Kernels.Params(PARAMS_SCORES)
                .u32(s.rows()).u32(s.heads()).u32(s.group()).u32(s.queries()).u32(s.t()).u32(s.causal() ? 1 : 0)
                .u32(width).u32(scaled ? 1 : 0).f32(scale).u32(xOff).u32(ldx).u32(mOff).u32(ldm)
                .u32(masked ? 1 : 0).u32(maskOff).u32(ldMask).u32(outOff).u32(ldOut).u32(chunks).u32(groups.x())
                .bytes();
        Kernels.dispatch(encoder, scores, Map.of(0, x, 1, m, 2, mask, 3, out), params, groups);
    }

    private void recordMix(GpuCommandEncoder encoder, Shape s, int width, float alpha, GpuBuffer m, long mOff, int ldm,
            GpuBuffer w, long wOff, int ldw, GpuBuffer out, long outOff, int ldOut) {
        int chunks = chunks(width);
        Dimensions groups = Kernels.grid(device.capabilities(), (long) s.rows() * chunks);
        byte[] params = new Kernels.Params(PARAMS_MIX)
                .u32(s.rows()).u32(s.heads()).u32(s.group()).u32(s.queries()).u32(s.t()).u32(s.causal() ? 1 : 0)
                .u32(width).f32(alpha).u32(mOff).u32(ldm).u32(wOff).u32(ldw).u32(outOff).u32(ldOut).u32(chunks)
                .u32(groups.x()).bytes();
        Kernels.dispatch(encoder, mix, Map.of(0, m, 1, w, 2, out), params, groups);
    }

    private void recordAccumulate(GpuCommandEncoder encoder, Shape s, int width, boolean scaled, float scale,
            GpuBuffer w, long wOff, int ldw, GpuBuffer x, long xOff, int ldx, GpuBuffer dest, long destOff,
            int ldDest) {
        int chunks = chunks(width);
        Dimensions groups = Kernels.grid(device.capabilities(), (long) s.t() * s.kvHeads() * chunks);
        byte[] params = new Kernels.Params(PARAMS_ACCUMULATE)
                .u32(s.heads()).u32(s.group()).u32(s.kvHeads()).u32(s.queries()).u32(s.t()).u32(s.causal() ? 1 : 0)
                .u32(width).u32(scaled ? 1 : 0).f32(scale).u32(wOff).u32(ldw).u32(xOff).u32(ldx).u32(destOff)
                .u32(ldDest).u32(chunks).u32(groups.x()).bytes();
        Kernels.dispatch(encoder, accumulate, Map.of(0, w, 1, x, 2, dest), params, groups);
    }
}
