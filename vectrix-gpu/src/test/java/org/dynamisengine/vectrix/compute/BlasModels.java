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

import java.util.Map;

import org.dynamisengine.vectrix.blas.Blasf;
import org.dynamisengine.vectrix.blas.Gemmf;

/**
 * CPU models of the Blasf kernels (blas_*.slang) and of gemv, for the reference backend. Each models its kernel's
 * contract on the scratch buffer; the values come from Vectrix: a first-pass subtree is {@code Blasf} over its range
 * (exact, since the tree splits there), the upper passes add nodes as the pairwise tree does.
 */
final class BlasModels {

    private static final int BLOCK = 256;
    private static final int LEAF_BLOCKS = 32;
    private static final int NONE = -1;

    private BlasModels() {
    }

    static void register(Map<String, ReferenceModels.ReferenceModel> m) {
        m.put("blas_partial", BlasModels::partial);
        m.put("blas_combine", BlasModels::combine);
        m.put("blas_amax", BlasModels::amax);
        m.put("blas_axpy", c -> {
            int n = c.i("n");
            c.requireLinearGrid((n + 255) / 256);
            float[] x = c.floats(0);
            float[] y = c.floats(1);
            Blasf.axpy(c.f("a"), x, c.i("xOff"), y, c.i("yOff"), n);
            c.put(1, y);
        });
        m.put("blas_axpy_inplace", c -> {
            int n = c.i("n");
            c.requireLinearGrid((n + 255) / 256);
            float[] x = c.floats(0);
            Blasf.axpy(c.f("a"), x, c.i("xOff"), x, c.i("xOff"), n);
            c.put(0, x);
        });
        m.put("blas_scal", c -> {
            int n = c.i("n");
            c.requireLinearGrid((n + 255) / 256);
            float[] x = c.floats(0);
            Blasf.scal(c.f("a"), x, c.i("xOff"), n);
            c.put(0, x);
        });
        m.put("gemv", c -> {
            int mm = c.i("m");
            int n = c.i("n");
            boolean ta = c.u("transA") != 0;
            c.requireLinearGrid((mm + 63) / 64);
            float[] a = c.floats(0);
            float[] x = c.floats(1);
            float[] y = c.floats(2);
            Gemmf.gemv(ta, mm, n, c.f("alpha"), a, c.i("aOff"), c.i("lda"), x, c.i("xOff"), c.f("beta"), y,
                    c.i("yOff"), new float[n], 0);
            c.put(2, y);
        });
    }

    /** blas_nodeBlocks / nodeStart: {count, first block} of node idx at depth of the tree over blocks. */
    static long[] node(long blocks, int depth, long idx) {
        long c = blocks;
        long start = 0;
        for (int d = 0; d < depth; d++) {
            long left = (c + 1) / 2;
            if (((idx >> (depth - 1 - d)) & 1) != 0) {
                start += left;
                c -= left;
            } else {
                c = left;
            }
        }
        return new long[] {c, start};
    }

    private static boolean skipped(ReferenceModels.Call c, int[] scratch) {
        int need = c.i("need");
        return need != 0 && scratch[c.i("stateOff")] != need;
    }

    private static void partial(ReferenceModels.Call c) {
        int op = c.i("op");
        int n = c.i("n");
        long blocks = c.u("blocks");
        int depth = c.i("depth");
        c.require(blocks == (n + BLOCK - 1) / BLOCK, "blocks " + blocks + " for n " + n);
        c.require((blocks + (1L << depth) - 1) >> depth <= LEAF_BLOCKS, "subtrees above " + LEAF_BLOCKS + " blocks");
        c.requireLinearGrid(1L << depth);
        int[] scratch = c.ints(2);
        if (skipped(c, scratch)) {
            return;
        }
        float[] x = c.floats(0);
        float[] y = c.floats(1);
        int xOff = c.i("xOff");
        int yOff = c.i("yOff");
        float[] scaled = null;
        if (op == 4) {
            float s = Math.scalb(1f, -scratch[c.i("stateOff") + 1]);
            scaled = new float[n];
            for (int i = 0; i < n; i++) {
                scaled[i] = x[xOff + i] * s;
            }
        }
        for (long g = 0; g < 1L << depth; g++) {
            long[] nd = node(blocks, depth, g);
            if (nd[0] == 0) {
                continue;
            }
            int lo = Math.toIntExact(nd[1] * BLOCK);
            int len = (int) Math.min(nd[0] * BLOCK, n - lo);
            float v = switch (op) {
                case 0 -> Blasf.dot(x, xOff + lo, y, yOff + lo, len);
                case 1 -> Blasf.sum(x, xOff + lo, len);
                case 2 -> Blasf.asum(x, xOff + lo, len);
                case 3 -> Blasf.dot(x, xOff + lo, x, xOff + lo, len); // fma(x, x, a): Blasf's sum of squares
                case 4 -> Blasf.dot(scaled, lo, scaled, lo, len);
                default -> throw new IllegalStateException("op " + op);
            };
            scratch[Math.toIntExact(c.u("leafOff") + g)] = Float.floatToRawIntBits(v);
        }
        c.put(2, scratch);
    }

    private static float walk(int[] scratch, long inOff, int inDepth, long count, int depth, long idx) {
        if (depth == inDepth || count == 1) {
            return Float.intBitsToFloat(scratch[Math.toIntExact(inOff + (idx << (inDepth - depth)))]);
        }
        float left = walk(scratch, inOff, inDepth, (count + 1) / 2, depth + 1, 2 * idx);
        return left + walk(scratch, inOff, inDepth, count / 2, depth + 1, 2 * idx + 1);
    }

    private static void combine(ReferenceModels.Call c) {
        long blocks = c.u("blocks");
        int inDepth = c.i("inDepth");
        int outDepth = c.i("outDepth");
        int mode = c.i("mode");
        c.require(inDepth >= outDepth && inDepth - outDepth <= 7, "combine " + inDepth + " -> " + outDepth);
        c.require(mode == 0 || outDepth == 0, "a result from more than one node");
        c.requireLinearGrid(((1L << outDepth) + 31) / 32);
        int[] scratch = c.ints(0);
        if (skipped(c, scratch)) {
            return;
        }
        int[] result = c.ints(1);
        int state = c.i("stateOff");
        for (long idx = 0; idx < 1L << outDepth; idx++) {
            float v = 0f;
            if (blocks != 0) {
                long count = node(blocks, outDepth, idx)[0];
                if (count == 0) {
                    continue;
                }
                v = walk(scratch, c.u("inOff"), inDepth, count, outDepth, idx);
            }
            int out = c.i("outOff");
            switch (mode) {
                case 0 -> scratch[Math.toIntExact(out + idx)] = Float.floatToRawIntBits(v);
                case 1 -> result[out] = Float.floatToRawIntBits(v);
                case 2 -> {
                    int flag = 0;
                    if (v >= Float.MIN_NORMAL && v < Float.POSITIVE_INFINITY) {
                        result[out] = Float.floatToRawIntBits((float) Math.sqrt(v));
                    } else if (v != v) {
                        result[out] = Float.floatToRawIntBits(v);
                    } else {
                        flag = 1;
                    }
                    scratch[state] = flag;
                }
                default -> result[out] = Float.floatToRawIntBits(Math.scalb((float) Math.sqrt(v),
                        scratch[state + 1]));
            }
        }
        c.put(0, scratch);
        c.put(1, result);
    }

    /** blas_amax's order: whether (ka, ia) comes before (kb, ib); keys are |x| bits, NONE no candidate. */
    private static boolean before(int ka, int ia, int kb, int ib) {
        if (ib == NONE) {
            return ia != NONE;
        }
        if (ia == NONE) {
            return false;
        }
        boolean na = ka > 0x7F800000;
        boolean nb = kb > 0x7F800000;
        if (na || nb) {
            return na && nb ? ia < ib : na;
        }
        return ka != kb ? ka > kb : ia < ib;
    }

    private static void amax(ReferenceModels.Call c) {
        int n = c.i("n");
        int stage = c.i("stage");
        int parts = c.i("parts");
        int[] scratch = c.ints(1);
        if (stage == 0) {
            c.requireLinearGrid(parts);
            c.require((long) (parts - 1) * 256 < n, "a part without elements");
        } else {
            c.require(c.u("gridX") == 1 && c.groups().x() * c.groups().y() >= 1, "stage 1 runs one group");
        }
        if (skipped(c, scratch)) {
            return;
        }
        int partOff = c.i("partOff");
        if (stage == 0) {
            int[] x = c.ints(0);
            int xOff = c.i("xOff");
            for (int g = 0; g < parts; g++) {
                int bk = 0;
                int bi = NONE;
                // the union of the group's threads' elements: every best of a subset reduces to the same best
                for (long i = (long) g * 256; i < n; i += (long) parts * 256) {
                    for (int t = 0; t < 256 && i + t < n; t++) {
                        int k = x[xOff + (int) i + t] & 0x7FFFFFFF;
                        if (before(k, (int) i + t, bk, bi)) {
                            bk = k;
                            bi = (int) i + t;
                        }
                    }
                }
                scratch[partOff + 2 * g] = bk;
                scratch[partOff + 2 * g + 1] = bi;
            }
            c.put(1, scratch);
            return;
        }
        int bk = 0;
        int bi = NONE;
        for (int j = 0; j < parts; j++) {
            if (before(scratch[partOff + 2 * j], scratch[partOff + 2 * j + 1], bk, bi)) {
                bk = scratch[partOff + 2 * j];
                bi = scratch[partOff + 2 * j + 1];
            }
        }
        int[] result = c.ints(2);
        int state = c.i("stateOff");
        if (c.i("mode") == 0) {
            result[c.i("outOff")] = bi;
        } else if (bk == 0 || bk == 0x7F800000) {
            result[c.i("outOff")] = bk;
            scratch[state] = 0;
        } else {
            scratch[state] = 2;
            scratch[state + 1] = (bk >>> 23) - 127;
        }
        c.put(1, scratch);
        c.put(2, result);
    }
}
