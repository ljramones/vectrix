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

import java.util.Arrays;
import java.util.Map;

import org.dynamisengine.vectrix.nn.Loss;
import org.dynamisengine.vectrix.nn.Norms;
import org.dynamisengine.vectrix.nn.Softmax;

/**
 * Reference models of the row kernels (GpuSoftmax beyond softmax, GpuLoss, GpuNorms): each runs the Vectrix method
 * on every row, in row order, on the dispatch's buffers.
 */
final class RowsModels {

    private RowsModels() {
    }

    /** A per-row step of a model: row {@code r} of the call {@code c}. */
    private interface RowStep {
        void row(ReferenceModels.Call c, int r);
    }

    private static void rows(ReferenceModels.Call c, RowStep step) {
        int rows = c.i("rows");
        c.requireLinearGrid(rows);
        for (int r = 0; r < rows; r++) {
            step.row(c, r);
        }
    }

    private static int base(ReferenceModels.Call c, String off, String ld, int r) {
        return c.i(off) + r * c.i(ld);
    }

    static void register(Map<String, ReferenceModels.ReferenceModel> m) {
        m.put("log_softmax", c -> {
            float[] x = c.floats(0);
            float[] y = c.floats(1);
            int n = c.i("n");
            rows(c, (k, r) -> Softmax.logSoftmax(x, base(k, "xOff", "ldx", r), y, base(k, "yOff", "ldy", r), n,
                    new float[n], 0));
            c.put(1, y);
        });
        m.put("log_softmax_inplace", c -> {
            float[] x = c.floats(0);
            int n = c.i("n");
            rows(c, (k, r) -> Softmax.logSoftmax(x, base(k, "xOff", "ldx", r), x, base(k, "xOff", "ldx", r), n,
                    new float[n], 0));
            c.put(0, x);
        });
        m.put("log_sum_exp", c -> {
            float[] x = c.floats(0);
            float[] out = c.floats(1);
            int n = c.i("n");
            rows(c, (k, r) -> out[k.i("outOff") + r] = Softmax.logSumExp(x, base(k, "xOff", "ldx", r), n,
                    new float[n], 0));
            c.put(1, out);
        });
        m.put("softmax_backward", c -> backward(c, false));
        m.put("log_softmax_backward", c -> backward(c, true));
        m.put("softmax_backward_inplace", c -> backwardInPlace(c, false));
        m.put("log_softmax_backward_inplace", c -> backwardInPlace(c, true));
        m.put("cross_entropy", c -> {
            float[] x = c.floats(0);
            int[] t = c.ints(1);
            float[] loss = c.floats(2);
            int n = c.i("n");
            rows(c, (k, r) -> {
                int target = t[k.i("tOff") + r];
                loss[k.i("lossOff") + r] = target < 0 || target >= n ? Float.NaN
                        : Loss.crossEntropy(x, base(k, "xOff", "ldx", r), n, target, new float[n], 0);
            });
            c.put(2, loss);
        });
        m.put("cross_entropy_backward", c -> {
            float[] x = c.floats(0);
            int[] t = c.ints(1);
            float[] dx = c.floats(2);
            rows(c, (k, r) -> crossEntropyBackward(k, x, base(k, "xOff", "ldx", r), t[k.i("tOff") + r], dx,
                    base(k, "dxOff", "lddx", r)));
            c.put(2, dx);
        });
        m.put("cross_entropy_backward_inplace", c -> {
            float[] x = c.floats(0);
            int[] t = c.ints(1);
            rows(c, (k, r) -> crossEntropyBackward(k, x, base(k, "xOff", "ldx", r), t[k.i("tOff") + r], x,
                    base(k, "xOff", "ldx", r)));
            c.put(0, x);
        });
        m.put("layer_norm", c -> layerNorm(c, false));
        m.put("layer_norm_inplace", c -> layerNorm(c, true));
        m.put("rms_norm", c -> rmsNorm(c, false));
        m.put("rms_norm_inplace", c -> rmsNorm(c, true));
        m.put("norm_backward", c -> {
            float[] x = c.floats(0);
            float[] gamma = (c.i("flags") & 1) != 0 ? c.floats(1) : null;
            float[] stats = c.floats(2);
            float[] dy = c.floats(3);
            float[] dx = c.floats(4);
            rows(c, (k, r) -> normBackward(k, r, x, base(k, "xOff", "ldx", r), gamma, stats, dy,
                    base(k, "dyOff", "lddy", r), dx, base(k, "dxOff", "lddx", r)));
            c.put(4, dx);
        });
        m.put("norm_backward_inplace", c -> {
            float[] a = c.floats(0);
            float[] gamma = (c.i("flags") & 1) != 0 ? c.floats(1) : null;
            float[] stats = c.floats(2);
            float[] b = c.floats(3);
            boolean dxIsDy = (c.i("flags") & 2) != 0;
            rows(c, (k, r) -> {
                int ab = base(k, "aOff", "lda", r);
                int bb = base(k, "bOff", "ldb", r);
                if (dxIsDy) {
                    normBackward(k, r, b, bb, gamma, stats, a, ab, a, ab);
                } else {
                    normBackward(k, r, a, ab, gamma, stats, b, bb, a, ab);
                }
            });
            c.put(0, a);
        });
        m.put("norm_param_grads", c -> {
            int rows = c.i("rows");
            int n = c.i("n");
            c.requireLinearGrid((n + 63) / 64);
            int flags = c.i("flags");
            float[] x = c.floats(0);
            float[] stats = c.floats(1);
            float[] dy = c.floats(2);
            float[] dGamma = (flags & 1) != 0 ? c.floats(3) : null;
            float[] dBeta = (flags & 2) != 0 ? c.floats(4) : null;
            float[] dx = new float[n];
            float[] scratch = new float[2 * n];
            for (int r = 0; r < rows; r++) {
                int xb = c.i("xOff") + r * c.i("ldx");
                int dyb = c.i("dyOff") + r * c.i("lddy");
                if ((flags & 4) != 0) {
                    Norms.rmsNormBackward(x, xb, null, 0, stats, c.i("statsOff") + r, dy, dyb, dx, 0, dGamma,
                            c.i("dgOff"), n, scratch, 0);
                } else {
                    Norms.layerNormBackward(x, xb, null, 0, stats, c.i("statsOff") + 2 * r, dy, dyb, dx, 0, dGamma,
                            c.i("dgOff"), dBeta, c.i("dbOff"), n, scratch, 0);
                }
            }
            if (dGamma != null) {
                c.put(3, dGamma);
            }
            if (dBeta != null) {
                c.put(4, dBeta);
            }
        });
    }

    private static void backward(ReferenceModels.Call c, boolean log) {
        float[] y = c.floats(0);
        float[] dy = c.floats(1);
        float[] dx = c.floats(2);
        int n = c.i("n");
        rows(c, (k, r) -> {
            int yb = base(k, "yOff", "ldy", r);
            int dyb = base(k, "dyOff", "lddy", r);
            int dxb = base(k, "dxOff", "lddx", r);
            if (log) {
                Softmax.logSoftmaxBackward(y, yb, dy, dyb, dx, dxb, n);
            } else {
                Softmax.softmaxBackward(y, yb, dy, dyb, dx, dxb, n);
            }
        });
        c.put(2, dx);
    }

    private static void backwardInPlace(ReferenceModels.Call c, boolean log) {
        float[] a = c.floats(0);
        float[] b = c.floats(1);
        int n = c.i("n");
        boolean dxIsDy = c.i("dxIsDy") != 0;
        rows(c, (k, r) -> {
            int ab = base(k, "aOff", "lda", r);
            int bb = base(k, "bOff", "ldb", r);
            float[] y = dxIsDy ? b : a;
            int yb = dxIsDy ? bb : ab;
            float[] dy = dxIsDy ? a : b;
            int dyb = dxIsDy ? ab : bb;
            if (log) {
                Softmax.logSoftmaxBackward(y, yb, dy, dyb, a, ab, n);
            } else {
                Softmax.softmaxBackward(y, yb, dy, dyb, a, ab, n);
            }
        });
        c.put(0, a);
    }

    /** Loss.crossEntropyBackward, or a NaN row for a target outside [0, n) (the kernel's documented result). */
    private static void crossEntropyBackward(ReferenceModels.Call c, float[] x, int xb, int target, float[] dx,
            int dxb) {
        int n = c.i("n");
        if (target < 0 || target >= n) {
            Arrays.fill(dx, dxb, dxb + n, Float.NaN);
        } else {
            Loss.crossEntropyBackward(x, xb, n, target, c.f("dLoss"), dx, dxb);
        }
    }

    private static void layerNorm(ReferenceModels.Call c, boolean inPlace) {
        int flags = c.i("flags");
        float[] x = c.floats(0);
        float[] gamma = (flags & 1) != 0 ? c.floats(1) : null;
        float[] beta = (flags & 2) != 0 ? c.floats(2) : null;
        int ySlot = inPlace ? 0 : 3;
        int statsSlot = inPlace ? 3 : 4;
        float[] y = inPlace ? x : c.floats(ySlot);
        float[] stats = (flags & 4) != 0 ? c.floats(statsSlot) : null;
        int n = c.i("n");
        rows(c, (k, r) -> {
            int xb = base(k, "xOff", "ldx", r);
            int yb = inPlace ? xb : base(k, "yOff", "ldy", r);
            Norms.layerNorm(x, xb, gamma, k.i("gOff"), beta, k.i("bOff"), k.f("eps"), y, yb, n, stats,
                    k.i("statsOff") + 2 * r);
        });
        c.put(ySlot, y);
        if (stats != null) {
            c.put(statsSlot, stats);
        }
    }

    private static void rmsNorm(ReferenceModels.Call c, boolean inPlace) {
        int flags = c.i("flags");
        float[] x = c.floats(0);
        float[] gamma = (flags & 1) != 0 ? c.floats(1) : null;
        int ySlot = inPlace ? 0 : 2;
        int statsSlot = inPlace ? 2 : 3;
        float[] y = inPlace ? x : c.floats(ySlot);
        float[] stats = (flags & 4) != 0 ? c.floats(statsSlot) : null;
        int n = c.i("n");
        rows(c, (k, r) -> {
            int xb = base(k, "xOff", "ldx", r);
            int yb = inPlace ? xb : base(k, "yOff", "ldy", r);
            Norms.rmsNorm(x, xb, gamma, k.i("gOff"), k.f("eps"), y, yb, n, stats, k.i("statsOff") + r);
        });
        c.put(ySlot, y);
        if (stats != null) {
            c.put(statsSlot, stats);
        }
    }

    /** The input gradient only (the parameter gradients are norm_param_grads). */
    private static void normBackward(ReferenceModels.Call c, int r, float[] x, int xb, float[] gamma, float[] stats,
            float[] dy, int dyb, float[] dx, int dxb) {
        int n = c.i("n");
        float[] scratch = new float[2 * n];
        if ((c.i("flags") & 4) != 0) {
            Norms.rmsNormBackward(x, xb, gamma, c.i("gOff"), stats, c.i("statsOff") + r, dy, dyb, dx, dxb, null, 0, n,
                    scratch, 0);
        } else {
            Norms.layerNormBackward(x, xb, gamma, c.i("gOff"), stats, c.i("statsOff") + 2 * r, dy, dyb, dx, dxb, null,
                    0, null, 0, n, scratch, 0);
        }
    }
}
