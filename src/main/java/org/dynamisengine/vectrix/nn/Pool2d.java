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

/**
 * 2D max and average pooling over one image in channel-major ({@code C×H×W}) layout, forward and backward.
 * <p>
 * Windows of {@code k×k} advance by {@code stride}, with {@code pad} cells of padding that never win a maximum and
 * are not counted in an average. Max pooling records the flat input index of each window's maximum (the first in
 * row-major order among equals) so the backward pass routes gradients without searching again. Deterministic; no
 * allocation.
 *
 * @since 2.5.0
 */
public final class Pool2d {

    private final int c;
    private final int h;
    private final int w;
    private final int k;
    private final int stride;
    private final int pad;
    private final int hout;
    private final int wout;

    private Pool2d(int c, int h, int w, int k, int stride, int pad) {
        this.c = c;
        this.h = h;
        this.w = w;
        this.k = k;
        this.stride = stride;
        this.pad = pad;
        this.hout = (int) (Math.floorDiv((long) h + 2L * pad - k, stride) + 1);
        this.wout = (int) (Math.floorDiv((long) w + 2L * pad - k, stride) + 1);
    }

    /**
     * Describes a pooling layer.
     *
     * @param c the number of channels
     * @param h input height
     * @param w input width
     * @param k the window size, at least 1
     * @param stride the step between windows, at least 1
     * @param pad padding on every side, at least 0 and at most {@code k/2}
     * @return the shape
     * @throws IllegalArgumentException if a size is out of range, the window does not fit the padded input, or the
     *         input or output does not fit in an {@code int}-indexed array
     */
    public static Pool2d of(int c, int h, int w, int k, int stride, int pad) {
        if (c < 1 || h < 1 || w < 1 || k < 1 || stride < 1 || pad < 0 || 2L * pad > k) {
            throw new IllegalArgumentException("invalid pooling shape");
        }
        if ((long) c * h * w > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("the input does not fit in an int-indexed array");
        }
        Pool2d p = new Pool2d(c, h, w, k, stride, pad);
        if (p.hout < 1 || p.wout < 1) {
            throw new IllegalArgumentException("the window does not fit the padded input");
        }
        if ((long) c * p.hout * p.wout > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("the output does not fit in an int-indexed array");
        }
        return p;
    }

    /**
     * Returns the output height, {@code (h + 2·pad - k) / stride + 1}.
     *
     * @return the output height, at least 1
     */
    public int outHeight() {
        return hout;
    }

    /**
     * Returns the output width, {@code (w + 2·pad - k) / stride + 1}.
     *
     * @return the output width, at least 1
     */
    public int outWidth() {
        return wout;
    }

    /**
     * Max pooling: each output is its window's largest value, the first in row-major order among equals. A NaN wins
     * over every number (the window's first NaN), so NaNs propagate; {@link Sampler#argmax} picks the same way.
     *
     * @param in the input image, {@code c·h·w} floats, channel-major
     * @param inOff the offset in {@code in}
     * @param out receives {@code c·outHeight·outWidth} floats; must not overlap {@code in}
     * @param outOff the offset in {@code out}
     * @param argmax receives the flat input index of each output's maximum (relative to {@code inOff}), or {@code null}
     * @param argOff the offset in {@code argmax}
     * @throws IllegalArgumentException if an array is too short for its offset
     */
    public void max(float[] in, int inOff, float[] out, int outOff, int[] argmax, int argOff) {
        range(in == null ? -1 : in.length, inOff, c * h * w, "in");
        range(out == null ? -1 : out.length, outOff, c * hout * wout, "out");
        if (argmax != null) {
            range(argmax.length, argOff, c * hout * wout, "argmax");
        }
        for (int ch = 0; ch < c; ch++) {
            for (int oy = 0; oy < hout; oy++) {
                for (int ox = 0; ox < wout; ox++) {
                    float m = Float.NEGATIVE_INFINITY;
                    int at = -1;
                    for (int ky = 0; ky < k; ky++) {
                        int iy = oy * stride - pad + ky;
                        if (iy < 0 || iy >= h) {
                            continue;
                        }
                        for (int kx = 0; kx < k; kx++) {
                            int ix = ox * stride - pad + kx;
                            if (ix < 0 || ix >= w) {
                                continue;
                            }
                            int idx = (ch * h + iy) * w + ix;
                            float v = in[inOff + idx];
                            if (at < 0 || v > m || v != v && m == m) {
                                m = v;
                                at = idx;
                            }
                        }
                    }
                    int o = (ch * hout + oy) * wout + ox;
                    out[outOff + o] = m;
                    if (argmax != null) {
                        argmax[argOff + o] = at;
                    }
                }
            }
        }
    }

    /**
     * Max-pooling backward: {@code dIn} is cleared, then each {@code dOut} adds to its window's maximum. Every index in
     * {@code argmax} is checked to lie in {@code [0, c·h·w)} before anything is written.
     *
     * @param argmax the indices {@link #max} recorded, each in {@code [0, c·h·w)}
     * @param argOff the offset in {@code argmax}
     * @param dOut the gradient of the output, {@code c·outHeight·outWidth} floats
     * @param dOutOff the offset in {@code dOut}
     * @param dIn receives the gradient of the input, {@code c·h·w} floats; must not overlap {@code dOut}
     * @param dInOff the offset in {@code dIn}
     * @throws IllegalArgumentException if an array is null or too short for its offset, or an {@code argmax} index is
     *         out of range; nothing is written then
     */
    public void maxBackward(int[] argmax, int argOff, float[] dOut, int dOutOff, float[] dIn, int dInOff) {
        if (dIn == null || dOut == null) {
            throw new IllegalArgumentException("dIn and dOut must not be null");
        }
        int n = c * hout * wout;
        int inSize = c * h * w;
        range(argmax == null ? -1 : argmax.length, argOff, n, "argmax");
        range(dOut.length, dOutOff, n, "dOut");
        range(dIn.length, dInOff, inSize, "dIn");
        for (int o = 0; o < n; o++) {
            int at = argmax[argOff + o];
            if (at < 0 || at >= inSize) {
                throw new IllegalArgumentException("argmax[" + (argOff + o) + "] = " + at + " is outside [0, "
                        + inSize + ")");
            }
        }
        java.util.Arrays.fill(dIn, dInOff, dInOff + c * h * w, 0f);
        for (int o = 0; o < n; o++) {
            dIn[dInOff + argmax[argOff + o]] += dOut[dOutOff + o];
        }
    }

    /**
     * Average pooling over the in-bounds cells of each window; padding cells are not counted.
     *
     * @param in the input image, {@code c·h·w} floats, channel-major
     * @param inOff the offset in {@code in}
     * @param out receives {@code c·outHeight·outWidth} floats; must not overlap {@code in}
     * @param outOff the offset in {@code out}
     * @throws IllegalArgumentException if an array is too short for its offset
     */
    public void average(float[] in, int inOff, float[] out, int outOff) {
        range(in == null ? -1 : in.length, inOff, c * h * w, "in");
        range(out == null ? -1 : out.length, outOff, c * hout * wout, "out");
        for (int ch = 0; ch < c; ch++) {
            for (int oy = 0; oy < hout; oy++) {
                for (int ox = 0; ox < wout; ox++) {
                    float s = 0f;
                    int cnt = 0;
                    for (int ky = 0; ky < k; ky++) {
                        int iy = oy * stride - pad + ky;
                        if (iy < 0 || iy >= h) {
                            continue;
                        }
                        for (int kx = 0; kx < k; kx++) {
                            int ix = ox * stride - pad + kx;
                            if (ix >= 0 && ix < w) {
                                s += in[inOff + (ch * h + iy) * w + ix];
                                cnt++;
                            }
                        }
                    }
                    out[outOff + (ch * hout + oy) * wout + ox] = s / cnt;
                }
            }
        }
    }

    /**
     * Average-pooling backward: {@code dIn} is cleared, then each {@code dOut/count} spreads over its window.
     *
     * @param dOut the gradient of the output, {@code c·outHeight·outWidth} floats
     * @param dOutOff the offset in {@code dOut}
     * @param dIn receives the gradient of the input, {@code c·h·w} floats; must not overlap {@code dOut}
     * @param dInOff the offset in {@code dIn}
     * @throws IllegalArgumentException if an array is null or too short for its offset
     */
    public void averageBackward(float[] dOut, int dOutOff, float[] dIn, int dInOff) {
        if (dIn == null || dOut == null) {
            throw new IllegalArgumentException("dIn and dOut must not be null");
        }
        range(dOut.length, dOutOff, c * hout * wout, "dOut");
        range(dIn.length, dInOff, c * h * w, "dIn");
        java.util.Arrays.fill(dIn, dInOff, dInOff + c * h * w, 0f);
        for (int ch = 0; ch < c; ch++) {
            for (int oy = 0; oy < hout; oy++) {
                for (int ox = 0; ox < wout; ox++) {
                    int y0 = Math.max(0, oy * stride - pad);
                    int y1 = Math.min(h, oy * stride - pad + k);
                    int x0 = Math.max(0, ox * stride - pad);
                    int x1 = Math.min(w, ox * stride - pad + k);
                    float g = dOut[dOutOff + (ch * hout + oy) * wout + ox] / ((y1 - y0) * (x1 - x0));
                    for (int iy = y0; iy < y1; iy++) {
                        for (int ix = x0; ix < x1; ix++) {
                            dIn[dInOff + (ch * h + iy) * w + ix] += g;
                        }
                    }
                }
            }
        }
    }

    private static void range(int length, int off, int n, String name) {
        if (length < 0 || off < 0 || off > length - n) {
            throw new IllegalArgumentException(name + " must hold " + n + " values from offset " + off);
        }
    }
}
