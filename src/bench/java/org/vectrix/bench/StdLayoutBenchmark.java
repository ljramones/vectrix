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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.vectrix.gpu.StdLayout;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class StdLayoutBenchmark {
    private static final StdLayout.Type[] TYPES = {
            StdLayout.Type.VEC3,
            StdLayout.Type.FLOAT,
            StdLayout.Type.VEC4,
            StdLayout.Type.MAT4,
            StdLayout.Type.VEC2,
            StdLayout.Type.FLOAT
    };

    @Benchmark
    public int[] offsetsStd140() {
        return StdLayout.offsetsStd140(TYPES);
    }

    @Benchmark
    public int[] offsetsStd430() {
        return StdLayout.offsetsStd430(TYPES);
    }
}
