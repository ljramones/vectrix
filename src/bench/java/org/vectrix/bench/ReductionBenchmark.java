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
import org.vectrix.experimental.Reduction;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class ReductionBenchmark {
    @Param({"1024", "16384", "65536"})
    public int size;

    private float[] a;
    private float[] b;

    @Setup
    public void setup() {
        a = new float[size];
        b = new float[size];
        SplittableRandom rnd = new SplittableRandom(99L);
        for (int i = 0; i < size; i++) {
            a[i] = (float) rnd.nextDouble(-1.0, 1.0);
            b[i] = (float) rnd.nextDouble(-1.0, 1.0);
        }
    }

    @Benchmark
    public float sumFast() {
        return Reduction.sumFast(a);
    }

    @Benchmark
    public float sumStrict() {
        return Reduction.sumStrict(a);
    }

    @Benchmark
    public float dotFast() {
        return Reduction.dotFast(a, b);
    }

    @Benchmark
    public float dotStrict() {
        return Reduction.dotStrict(a, b);
    }
}
