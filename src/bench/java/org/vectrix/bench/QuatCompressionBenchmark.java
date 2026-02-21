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
import org.vectrix.core.Quaternionf;
import org.vectrix.gpu.QuatCompression;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class QuatCompressionBenchmark {
    @Param({"256", "4096", "16384"})
    public int count;

    private Quaternionf[] src;
    private long[] packed;
    private Quaternionf tmp;

    @Setup
    public void setup() {
        src = new Quaternionf[count];
        packed = new long[count];
        tmp = new Quaternionf();
        SplittableRandom rnd = new SplittableRandom(123);
        for (int i = 0; i < count; i++) {
            src[i] = new Quaternionf().rotationXYZ((float) rnd.nextDouble(-3.0, 3.0), (float) rnd.nextDouble(-3.0, 3.0), (float) rnd.nextDouble(-3.0, 3.0));
        }
    }

    @Benchmark
    public long[] packSmallest3() {
        for (int i = 0; i < count; i++) {
            packed[i] = QuatCompression.packSmallest3(src[i]);
        }
        return packed;
    }

    @Benchmark
    public Quaternionf decodeSmallest3() {
        for (int i = 0; i < count; i++) {
            QuatCompression.unpackSmallest3(packed[i], tmp);
        }
        return tmp;
    }
}
