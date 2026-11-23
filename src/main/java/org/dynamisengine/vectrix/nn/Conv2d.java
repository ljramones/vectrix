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
package org.dynamisengine.vectrix.nn;

import org.dynamisengine.vectrix.blas.Gemmf;

/**
 * A 2D convolution layer shape (cross-correlation, as in every deep-learning framework), forward and backward, over
 * one image in channel-major ({@code C×H×W}) layout.
 * <p>
 * Weights are {@code Cout × (Cin/groups) × kh × kw}; {@code groups = Cin = Cout} is a depthwise convolution. The
 * kernels unfold the input into columns (im2col) and multiply with {@link Gemmf}, so every output element is one
 * dot product in {@code Blasf}'s documented order: results are the same bits on every platform and jar and do not
 * depend on batching. Backward passes reuse the same structure: the weight gradient is a GEMM with the transposed
 * columns, the input gradient a transposed GEMM followed by col2im, whose overlapping additions happen in a fixed
 * order. A 1D convolution is the case {@code H = kh = 1}. Shapes are immutable and shareable; calls take caller
 * scratch and do not allocate.
 *
 * @since 2.5.0
 */
public final class Conv2d {

    private final int cin;
    private final int cout;
    private final int h;
    private final int w;
    private final int kh;
    private final int kw;
    private final int strideH;
    private final int strideW;
    private final int padH;
    private final int padW;
    private final int dilH;
    private final int dilW;
    private final int groups;
    private final int hout;
    private final int wout;

    private Conv2d(int cin, int cout, int h, int w, int kh, int kw, int strideH, int strideW, int padH, int padW,
            int dilH, int dilW, int groups) {
        this.cin = cin;
        this.cout = cout;
        this.h = h;
        this.w = w;
        this.kh = kh;
        this.kw = kw;
        this.strideH = strideH;
        this.strideW = strideW;
        this.padH = padH;
        this.padW = padW;
        this.dilH = dilH;
        this.dilW = dilW;
        this.groups = groups;
        this.hout = (int) outSize(h, padH, dilH, kh, strideH);
        this.wout = (int) outSize(w, padW, dilW, kw, strideW);
    }

    /**
     * {@code floor((size + 2·pad - dil·(k - 1) - 1) / stride) + 1}, in long: below 1 when the dilated kernel does not
     * fit the padded input (a truncating division made a kernel one cell too large look like one output).
     */
    private static long outSize(int size, int pad, int dil, int k, int stride) {
        return Math.floorDiv((long) size + 2L * pad - (long) dil * (k - 1) - 1, stride) + 1;
    }

    /**
     * Describes a convolution.
     *
     * @param cin input channels, divisible by {@code groups}
     * @param cout output channels, divisible by {@code groups}
     * @param h input height
     * @param w input width
     * @param kh kernel height
     * @param kw kernel width
     * @param stride the stride in both directions, at least 1
     * @param pad the zero padding on every side, at least 0
     * @param dilation the dilation in both directions, at least 1
     * @param groups the number of groups, at least 1
     * @return the shape
     * @throws IllegalArgumentException if a size is out of range, a channel count is not divisible by
     *         {@code groups}, or the dilated kernel does not fit the padded input
     */
    public static Conv2d of(int cin, int cout, int h, int w, int kh, int kw, int stride, int pad, int dilation,
            int groups) {
        return of(cin, cout, h, w, kh, kw, stride, stride, pad, pad, dilation, dilation, groups);
    }

    /**
     * Describes a convolution with separate vertical and horizontal stride, padding and dilation.
     *
     * @param cin input channels, divisible by {@code groups}
     * @param cout output channels, divisible by {@code groups}
     * @param h input height
     * @param w input width
     * @param kh kernel height
     * @param kw kernel width
     * @param strideH the vertical stride, at least 1
     * @param strideW the horizontal stride, at least 1
     * @param padH the zero padding above and below, at least 0
     * @param padW the zero padding left and right, at least 0
     * @param dilH the vertical dilation, at least 1
     * @param dilW the horizontal dilation, at least 1
     * @param groups the number of groups, at least 1
     * @return the shape
     * @throws IllegalArgumentException if a size is out of range, a channel count is not divisible by {@code groups},
     *         the dilated kernel does not fit the padded input, or an array size (input, output, weights or scratch)
     *         does not fit in an {@code int}
     */
    public static Conv2d of(int cin, int cout, int h, int w, int kh, int kw, int strideH, int strideW, int padH,
            int padW, int dilH, int dilW, int groups) {
        if (cin < 1 || cout < 1 || h < 1 || w < 1 || kh < 1 || kw < 1 || strideH < 1 || strideW < 1 || padH < 0
                || padW < 0 || dilH < 1 || dilW < 1 || groups < 1 || cin % groups != 0 || cout % groups != 0) {
            throw new IllegalArgumentException("invalid convolution shape");
        }
        long houtL = outSize(h, padH, dilH, kh, strideH);
        long woutL = outSize(w, padW, dilW, kw, strideW);
        if (houtL < 1 || woutL < 1) {
            throw new IllegalArgumentException("the kernel does not fit the padded input");
        }
        long k = (long) (cin / groups) * kh * kw;
        long hw = houtL * woutL;
        if ((long) cin * h * w > Integer.MAX_VALUE || (long) cout * hw > Integer.MAX_VALUE
                || (long) cout * k > Integer.MAX_VALUE || k * hw > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("the convolution's arrays do not fit in an int-indexed array");
        }
        Conv2d c = new Conv2d(cin, cout, h, w, kh, kw, strideH, strideW, padH, padW, dilH, dilW, groups);
        if (c.scratchSizeLong() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("the convolution's scratch does not fit in an int-indexed array");
        }
        return c;
    }

    /**
     * Returns the output height, {@code (h + 2·padH - dilH·(kh - 1) - 1) / strideH + 1}.
     *
     * @return the output height, at least 1
     */
    public int outHeight() {
        return hout;
    }

    /**
     * Returns the output width, {@code (w + 2·padW - dilW·(kw - 1) - 1) / strideW + 1}.
     *
     * @return the output width, at least 1
     */
    public int outWidth() {
        return wout;
    }

    /**
     * Returns the number of weights.
     *
     * @return {@code cout·(cin/groups)·kh·kw}
     */
    public int weightCount() {
        return cout * (cin / groups) * kh * kw;
    }

    /**
     * Returns the number of floats of scratch that {@link #forward} and {@link #backward} need: the columns of one
     * group plus the GEMM's packing.
     *
     * @return the scratch size in floats
     */
    public int scratchSize() {
        return (int) scratchSizeLong(); // of() checked that it fits
    }

    private long scratchSizeLong() {
        int k = (cin / groups) * kh * kw;
        int hw = hout * wout;
        int cg = cout / groups;
        int g1 = Gemmf.scratchSize(false, false, cg, hw, k);
        int g2 = Gemmf.scratchSize(false, true, cg, k, hw);
        int g3 = Gemmf.scratchSize(true, false, k, hw, cg);
        return (long) k * hw + Math.max(g1, Math.max(g2, g3));
    }

    /**
     * Forward pass: {@code out = conv(in, weights) + bias}.
     *
     * @param in the input image, {@code cin·h·w} floats, channel-major
     * @param inOff the offset in {@code in}
     * @param weights {@link #weightCount()} floats
     * @param wOff the offset in {@code weights}
     * @param bias {@code cout} floats, or {@code null}
     * @param bOff the offset in {@code bias}
     * @param out receives {@code cout·outHeight·outWidth} floats, channel-major; must not overlap the inputs or
     *        {@code scratch}
     * @param outOff the offset in {@code out}
     * @param scratch {@link #scratchSize()} floats of working memory
     * @param scratchOff the offset in {@code scratch}
     * @throws IllegalArgumentException if an array other than {@code bias} is null, or an array is too short for its
     *         offset; nothing is written then
     */
    public void forward(float[] in, int inOff, float[] weights, int wOff, float[] bias, int bOff, float[] out,
            int outOff, float[] scratch, int scratchOff) {
        checkCall(in, inOff, out, outOff, weights, wOff, scratch, scratchOff);
        if (bias != null) {
            range(bias, bOff, cout, "bias");
        }
        int k = (cin / groups) * kh * kw;
        int hw = hout * wout;
        int cg = cout / groups;
        int cols = scratchOff;
        int gemmScratch = scratchOff + k * hw;
        for (int g = 0; g < groups; g++) {
            im2col(in, inOff + g * (cin / groups) * h * w, scratch, cols);
            Gemmf.gemm(false, false, cg, hw, k, 1f, weights, wOff + g * cg * k, k, scratch, cols, hw, 0f, out,
                    outOff + g * cg * hw, hw, scratch, gemmScratch);
        }
        if (bias != null) {
            for (int c = 0; c < cout; c++) {
                float b = bias[bOff + c];
                for (int i = 0; i < hw; i++) {
                    out[outOff + c * hw + i] += b;
                }
            }
        }
    }

    /**
     * Backward pass: <i>accumulates</i> {@code dWeights += dOut·colsᵀ} and {@code dBias += Σ dOut} (either may be
     * {@code null}), and writes {@code dIn} (or skips it when {@code null}).
     *
     * @param in the forward input, {@code cin·h·w} floats
     * @param inOff the offset in {@code in}
     * @param weights the forward weights, {@link #weightCount()} floats
     * @param wOff the offset in {@code weights}
     * @param dOut the gradient of the output, {@code cout·outHeight·outWidth} floats
     * @param dOutOff the offset in {@code dOut}
     * @param dIn receives the gradient of the input, {@code cin·h·w} floats, or {@code null}; must not overlap the
     *        other arrays
     * @param dInOff the offset in {@code dIn}
     * @param dWeights accumulates the weight gradient, {@link #weightCount()} floats, or {@code null}
     * @param dwOff the offset in {@code dWeights}
     * @param dBias accumulates the bias gradient, {@code cout} floats, or {@code null}
     * @param dbOff the offset in {@code dBias}
     * @param scratch {@link #scratchSize()} floats of working memory
     * @param scratchOff the offset in {@code scratch}
     * @throws IllegalArgumentException if {@code in}, {@code dOut}, {@code weights} or {@code scratch} is null, or an
     *         array is too short for its offset; nothing is written then
     */
    public void backward(float[] in, int inOff, float[] weights, int wOff, float[] dOut, int dOutOff, float[] dIn,
            int dInOff, float[] dWeights, int dwOff, float[] dBias, int dbOff, float[] scratch, int scratchOff) {
        checkCall(in, inOff, dOut, dOutOff, weights, wOff, scratch, scratchOff);
        if (dIn != null) {
            range(dIn, dInOff, cin * h * w, "dIn");
        }
        if (dWeights != null) {
            range(dWeights, dwOff, weightCount(), "dWeights");
        }
        if (dBias != null) {
            range(dBias, dbOff, cout, "dBias");
        }
        int k = (cin / groups) * kh * kw;
        int hw = hout * wout;
        int cg = cout / groups;
        int cols = scratchOff;
        int gemmScratch = scratchOff + k * hw;
        if (dIn != null) {
            java.util.Arrays.fill(dIn, dInOff, dInOff + cin * h * w, 0f);
        }
        for (int g = 0; g < groups; g++) {
            int inG = inOff + g * (cin / groups) * h * w;
            if (dWeights != null) {
                im2col(in, inG, scratch, cols);
                // dW_g (cg × k) += dOut_g (cg × hw) · colsᵀ
                Gemmf.gemm(false, true, cg, k, hw, 1f, dOut, dOutOff + g * cg * hw, hw, scratch, cols, hw, 1f,
                        dWeights, dwOff + g * cg * k, k, scratch, gemmScratch);
            }
            if (dIn != null) {
                // dCols (k × hw) = W_gᵀ · dOut_g, then scatter-add back into the image
                Gemmf.gemm(true, false, k, hw, cg, 1f, weights, wOff + g * cg * k, k, dOut, dOutOff + g * cg * hw, hw,
                        0f, scratch, cols, hw, scratch, gemmScratch);
                col2im(scratch, cols, dIn, dInOff + g * (cin / groups) * h * w);
            }
        }
        if (dBias != null) {
            for (int c = 0; c < cout; c++) {
                float s = org.dynamisengine.vectrix.blas.Blasf.sum(dOut, dOutOff + c * hw, hw);
                dBias[dbOff + c] += s;
            }
        }
    }

    /** Unfolds one group's channels into {@code k × hw} columns (row = channel·kh·kw + ky·kw + kx). */
    private void im2col(float[] in, int inOff, float[] cols, int colOff) {
        int cpg = cin / groups;
        int hw = hout * wout;
        for (int c = 0; c < cpg; c++) {
            for (int ky = 0; ky < kh; ky++) {
                for (int kx = 0; kx < kw; kx++) {
                    int row = (c * kh + ky) * kw + kx;
                    int base = colOff + row * hw;
                    for (int oy = 0; oy < hout; oy++) {
                        int iy = oy * strideH - padH + ky * dilH;
                        for (int ox = 0; ox < wout; ox++) {
                            int ix = ox * strideW - padW + kx * dilW;
                            cols[base + oy * wout + ox] = iy >= 0 && iy < h && ix >= 0 && ix < w
                                    ? in[inOff + (c * h + iy) * w + ix] : 0f;
                        }
                    }
                }
            }
        }
    }

    /** Adds the columns back into one group's image, in the fixed order of {@link #im2col}. */
    private void col2im(float[] cols, int colOff, float[] img, int imgOff) {
        int cpg = cin / groups;
        int hw = hout * wout;
        for (int c = 0; c < cpg; c++) {
            for (int ky = 0; ky < kh; ky++) {
                for (int kx = 0; kx < kw; kx++) {
                    int row = (c * kh + ky) * kw + kx;
                    int base = colOff + row * hw;
                    for (int oy = 0; oy < hout; oy++) {
                        int iy = oy * strideH - padH + ky * dilH;
                        if (iy < 0 || iy >= h) {
                            continue;
                        }
                        for (int ox = 0; ox < wout; ox++) {
                            int ix = ox * strideW - padW + kx * dilW;
                            if (ix >= 0 && ix < w) {
                                img[imgOff + (c * h + iy) * w + ix] += cols[base + oy * wout + ox];
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkCall(float[] in, int inOff, float[] out, int outOff, float[] weights, int wOff, float[] scratch,
            int scratchOff) {
        range(in, inOff, cin * h * w, "in");
        range(out, outOff, cout * hout * wout, "out");
        range(weights, wOff, weightCount(), "weights");
        range(scratch, scratchOff, scratchSize(), "scratch");
    }

    private static void range(float[] a, int off, int n, String name) {
        if (a == null || off < 0 || off > a.length - n) {
            throw new IllegalArgumentException(name + " must hold " + n + " values from offset " + off);
        }
    }
}
