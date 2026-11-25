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
package org.dynamisengine.vectrix.bench;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.dynamisengine.vectrix.blas.Blasf;
import org.dynamisengine.vectrix.blas.Gemmf;
import org.dynamisengine.vectrix.nn.Activations;
import org.dynamisengine.vectrix.nn.Attention;
import org.dynamisengine.vectrix.nn.Conv2d;
import org.dynamisengine.vectrix.nn.Formats;
import org.dynamisengine.vectrix.nn.Norms;
import org.dynamisengine.vectrix.nn.Softmax;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** The 2.5.0 machine-learning kernels at sizes typical of a small transformer layer. */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class MlKernelBenchmark extends ThroughputBenchmark {

    private static final int DIM = 1024;
    private static final int CTX = 512;
    private static final int HEAD = 64;

    private float[] x;
    private float[] y;
    private float[] w;
    private float[] gemmA;
    private float[] gemmC;
    private float[] gemmScratch;
    private byte[] q8;
    private float[] q8s;
    private float[] keys;
    private float[] p;
    private float[] att;
    private float[] scratch;
    private float[] stats;
    private Conv2d conv;
    private float[] img;
    private float[] convW;
    private float[] convOut;
    private float[] convScratch;

    @Setup
    public void setup() {
        SplittableRandom r = new SplittableRandom(1);
        x = fill(r, DIM * 4);
        y = new float[DIM * 4];
        w = fill(r, DIM * DIM);
        gemmA = fill(r, 64 * DIM);
        gemmC = new float[64 * DIM];
        gemmScratch = new float[Gemmf.scratchSize(false, true, 64, DIM, DIM)];
        q8 = new byte[DIM * DIM];
        q8s = new float[DIM * DIM / Formats.QBLOCK];
        Formats.quantizeInt8(w, 0, DIM * DIM, q8, 0, q8s, 0);
        keys = fill(r, CTX * HEAD);
        p = new float[CTX];
        att = new float[HEAD];
        scratch = new float[2 * DIM * 4];
        stats = new float[2];
        conv = Conv2d.of(32, 32, 32, 32, 3, 3, 1, 1, 1, 1);
        img = fill(r, 32 * 32 * 32);
        convW = fill(r, conv.weightCount());
        convOut = new float[32 * 32 * 32];
        convScratch = new float[conv.scratchSize()];
    }

    private static float[] fill(SplittableRandom r, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = (float) r.nextDouble(-1, 1);
        }
        return a;
    }

    /** One 1024-element dot product. */
    @Benchmark
    public float dot() {
        return Blasf.dot(x, 0, x, DIM, DIM);
    }

    /** y = W·x for a 1024×1024 float matrix (a decoding step's projection). */
    @Benchmark
    public float[] gemv() {
        Gemmf.gemv(false, DIM, DIM, 1f, w, 0, DIM, x, 0, 0f, y, 0, null, 0);
        return y;
    }

    /** The same with int8 block-quantized weights. */
    @Benchmark
    public float[] gemvInt8() {
        Formats.gemvInt8(DIM, DIM, q8, 0, q8s, 0, x, 0, y, 0);
        return y;
    }

    /** 64 tokens × a 1024×1024 projection (x·Wᵀ): 134 MFLOP. */
    @Benchmark
    public float[] gemmBatch64() {
        Gemmf.gemm(false, true, 64, DIM, DIM, 1f, gemmA, 0, DIM, w, 0, DIM, 0f, gemmC, 0, DIM, gemmScratch, 0);
        return gemmC;
    }

    /** Softmax over 4096 logits. */
    @Benchmark
    public float[] softmax4096() {
        Softmax.softmax(x, 0, y, 0, DIM * 4);
        return y;
    }

    /** RMS norm of a 1024-element hidden state. */
    @Benchmark
    public float[] rmsNorm() {
        Norms.rmsNorm(x, 0, null, 0, 1e-6f, y, 0, DIM, stats, 0);
        return y;
    }

    /** SiLU over 4096 elements. */
    @Benchmark
    public float[] silu4096() {
        Activations.silu(x, 0, y, 0, DIM * 4);
        return y;
    }

    /** One query attending over 512 cached positions with head size 64. */
    @Benchmark
    public float[] attention512() {
        Attention.forward(x, 0, HEAD, keys, 0, HEAD, keys, 0, HEAD, HEAD, CTX, 0.125f, null, 0, p, 0, att, 0,
            scratch, 0);
        return att;
    }

    /** A 3×3 convolution, 32→32 channels on 32×32 (19 MFLOP). */
    @Benchmark
    public float[] conv3x3() {
        conv.forward(img, 0, convW, 0, null, 0, convOut, 0, convScratch, 0);
        return convOut;
    }
}
