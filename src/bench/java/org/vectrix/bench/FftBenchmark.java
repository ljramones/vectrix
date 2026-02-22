/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.bench;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.vectrix.fft.Convolutionf;
import org.vectrix.fft.FFT1f;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class FftBenchmark {
    @Param({"256", "1024", "4096"})
    public int complexCount;

    private float[] data;
    private float[] a;
    private float[] b;
    private float[] out;

    @Setup
    public void setup() {
        data = new float[complexCount * 2];
        a = new float[complexCount * 2];
        b = new float[complexCount * 2];
        out = new float[complexCount * 2];
        SplittableRandom rnd = new SplittableRandom(7);
        for (int i = 0; i < data.length; i++) {
            float v = (float) rnd.nextDouble(-1.0, 1.0);
            data[i] = v;
            a[i] = v;
            b[i] = (float) rnd.nextDouble(-1.0, 1.0);
        }
    }

    @Benchmark
    public float[] forwardInverse() {
        float[] x = data.clone();
        FFT1f.forward(x);
        FFT1f.inverse(x);
        return x;
    }

    @Benchmark
    public float[] circularConvolution() {
        Convolutionf.circular(a, b, out);
        return out;
    }
}
