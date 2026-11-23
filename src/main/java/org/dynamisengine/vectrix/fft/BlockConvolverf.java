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
package org.dynamisengine.vectrix.fft;

/**
 * Streaming linear convolution of a real float signal with a fixed kernel (an FIR filter), one block at a time;
 * the single-precision twin of {@link BlockConvolverd}.
 * <p>
 * Each call of {@link #process} filters up to {@link #blockSize()} input samples and writes as many output
 * samples: {@code y[t] = Σ h[j]·x[t-j]} over the whole stream. The convolution tail that spills into later blocks
 * is carried in a caller-owned state array of {@link #stateSize()} floats (initially zero); {@link #flush}
 * emits it at the end of the stream. Blocks may have any length up to the block size, so a stream can be fed in
 * chunks of any size.
 * <p>
 * The plan convolves directly, with fused multiply-adds, when that is cheaper: kernels of at most 16 taps, or
 * blocks short relative to the kernel ({@code blockSize·kernelLength <= 2·n·log2 n}). Otherwise it uses real
 * FFTs of a fast size {@code n >= blockSize + kernelLength - 1} (overlap-add), which is 5 to 60 times faster
 * for long kernels. The FFT path's error is about {@code 1e-7·||x||·||h||}; the direct path is accurate to
 * a few ulps of each sum of products.
 * <p>
 * A plan is immutable and can be shared by any number of threads, each with its own state and scratch. Calls do
 * not allocate. Results are deterministic, as for {@link ComplexFftf}.
 *
 * @since 2.5.0
 */
public final class BlockConvolverf {

    /** Kernels up to this length are always convolved directly. */
    static final int DIRECT_MAX = 16;

    /** Direct convolution costs about {@code block·len} multiply-adds; the FFT path about {@code C·n·log2 n}. */
    static boolean useDirect(int len, int block) {
        if (len <= DIRECT_MAX) {
            return true;
        }
        long n = (long) block + len;
        return (long) block * len <= FFT_COST * n * (64 - Long.numberOfLeadingZeros(n));
    }

    /** The FFT path's cost per {@code n·log2 n}, in direct multiply-adds (measured crossover, 2026-11-22). */
    static final int FFT_COST = 2;

    private final int block;
    private final int len;
    private final float[] kernel;
    private final RealFftf fft;
    private final float[] spectrum;
    private final int scratchSize;

    private BlockConvolverf(float[] h, int off, int len, int block, int mode) {
        this.block = block;
        this.len = len;
        this.kernel = java.util.Arrays.copyOfRange(h, off, off + len);
        if (mode == 1 || mode == 0 && useDirect(len, block)) {
            fft = null;
            spectrum = null;
            scratchSize = block + len - 1;
        } else {
            int n = 2 * ComplexFftf.fastSize((block + len) / 2); // even, at least block + len - 1
            fft = RealFftf.plan(n);
            // the kernel spectrum is computed in double and rounded once
            RealFftd exact = RealFftd.plan(n);
            double[] b = new double[exact.spectrumLength()];
            for (int i = 0; i < len; i++) {
                b[i] = kernel[i];
            }
            exact.forward(b, 0, b, 0, new double[exact.scratchSize()], 0);
            spectrum = FftMath.toFloat(b);
            scratchSize = fft.spectrumLength() + fft.scratchSize();
        }
    }

    /**
     * Creates a plan.
     *
     * @param kernel the filter taps {@code h[0..length)}, copied
     * @param off the offset of the first tap
     * @param length the number of taps, at least 1
     * @param blockSize the largest number of samples per {@link #process} call, at least 1
     * @return the plan
     * @throws IllegalArgumentException if the sizes are out of range
     */
    public static BlockConvolverf plan(float[] kernel, int off, int length, int blockSize) {
        if (kernel == null || length < 1 || off < 0 || off > kernel.length - length) {
            throw new IllegalArgumentException("kernel must hold " + length + " taps from offset " + off);
        }
        if (blockSize < 1 || (long) blockSize + length > ComplexFftf.MAX_SIZE) {
            throw new IllegalArgumentException("block size out of range: " + blockSize);
        }
        return new BlockConvolverf(kernel, off, length, blockSize, 0);
    }

    /** Testing and benchmarking: {@code mode} 1 forces the direct path, 2 the FFT path. */
    static BlockConvolverf plan(float[] kernel, int off, int length, int blockSize, int mode) {
        plan(kernel, off, length, blockSize);
        return new BlockConvolverf(kernel, off, length, blockSize, mode);
    }

    /** @return the largest number of samples per {@link #process} call */
    public int blockSize() {
        return block;
    }

    /** @return the number of taps */
    public int kernelLength() {
        return len;
    }

    /** @return the number of floats of state carried between blocks: {@code kernelLength - 1} */
    public int stateSize() {
        return len - 1;
    }

    /** @return the number of floats the scratch array needs */
    public int scratchSize() {
        return scratchSize;
    }

    /** @return whether the kernel is convolved with FFTs rather than directly */
    public boolean usesFft() {
        return fft != null;
    }

    /**
     * Filters {@code count} samples. {@code in} and {@code out} may be the same array at the same offset.
     *
     * @param in the input samples
     * @param inOff the offset of the first input
     * @param count the number of samples, {@code 0 <= count <= blockSize()}
     * @param out receives {@code count} output samples
     * @param outOff the offset of the first output
     * @param state the {@link #stateSize()} floats carried between calls, zero at the start of a stream
     * @param stateOff the offset of the state
     * @param scratch caller-owned working memory of at least {@link #scratchSize()} floats
     * @param scratchOff the offset into {@code scratch}
     */
    public void process(float[] in, int inOff, int count, float[] out, int outOff, float[] state, int stateOff,
            float[] scratch, int scratchOff) {
        if (count < 0 || count > block) {
            throw new IllegalArgumentException("count must be in [0, " + block + "]: " + count);
        }
        check(in, inOff, count, "in");
        check(out, outOff, count, "out");
        check(state, stateOff, len - 1, "state");
        check(scratch, scratchOff, scratchSize, "scratch");
        int y = scratchOff;
        int total = count + len - 1;
        if (fft == null) {
            for (int t = 0; t < total; t++) {
                int j0 = java.lang.Math.max(0, t - count + 1);
                int j1 = java.lang.Math.min(len - 1, t);
                float sum = 0.0f;
                for (int j = j0; j <= j1; j++) {
                    sum = java.lang.Math.fma(kernel[j], in[inOff + t - j], sum);
                }
                scratch[y + t] = sum;
            }
        } else {
            int n = fft.size();
            System.arraycopy(in, inOff, scratch, y, count);
            java.util.Arrays.fill(scratch, y + count, y + n, 0.0f);
            int fs = y + fft.spectrumLength();
            fft.forward(scratch, y, scratch, y, scratch, fs);
            for (int k = 0, e = fft.spectrumLength(); k < e; k += 2) {
                float ar = scratch[y + k];
                float ai = scratch[y + k + 1];
                float br = spectrum[k];
                float bi = spectrum[k + 1];
                scratch[y + k] = ar * br - ai * bi;
                scratch[y + k + 1] = ar * bi + ai * br;
            }
            fft.inverse(scratch, y, scratch, y, scratch, fs);
        }
        int carried = len - 1;
        for (int t = 0; t < count; t++) {
            out[outOff + t] = t < carried ? scratch[y + t] + state[stateOff + t] : scratch[y + t];
        }
        // new tail: the spill of this block plus what is left of the previous tail
        for (int i = 0; i < carried; i++) {
            float v = scratch[y + count + i];
            int prev = count + i;
            state[stateOff + i] = prev < carried ? v + state[stateOff + prev] : v;
        }
    }

    /**
     * Writes the remaining {@link #stateSize()} output samples of a stream and clears the state.
     *
     * @param out receives {@code kernelLength - 1} samples
     * @param outOff the offset of the first output
     * @param state the state
     * @param stateOff the offset of the state
     */
    public void flush(float[] out, int outOff, float[] state, int stateOff) {
        check(out, outOff, len - 1, "out");
        check(state, stateOff, len - 1, "state");
        System.arraycopy(state, stateOff, out, outOff, len - 1);
        java.util.Arrays.fill(state, stateOff, stateOff + len - 1, 0.0f);
    }

    private static void check(float[] a, int off, int count, String name) {
        if (a == null || off < 0 || off > a.length - count) {
            throw new IllegalArgumentException(name + " must hold " + count + " floats from offset " + off);
        }
    }
}
