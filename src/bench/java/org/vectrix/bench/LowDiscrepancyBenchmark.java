/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.bench;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.vectrix.sampling.SobolSequence;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class LowDiscrepancyBenchmark {
    @Param({"256", "4096", "16384"})
    public int count;

    private static final int SEED = 0x9E3779B9;
    private float[] outX;
    private float[] outY;

    @Setup
    public void setup() {
        outX = new float[count];
        outY = new float[count];
    }

    @Benchmark
    public float sobolScrambledLoop() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            sum += SobolSequence.sobolScrambled(i, 0, SEED);
            sum += SobolSequence.sobolScrambled(i, 1, SEED);
        }
        return sum;
    }

    @Benchmark
    public float sobolScrambledBatch2DLoop() {
        SobolSequence.sobolScrambledBatch2D(0, count, SEED, outX, 0, outY, 0);
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            sum += outX[i] + outY[i];
        }
        return sum;
    }
}
