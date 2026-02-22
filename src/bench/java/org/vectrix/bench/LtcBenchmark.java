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
import org.vectrix.core.Vector3f;
import org.vectrix.ltc.LtcEvalf;
import org.vectrix.ltc.LtcTablef;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class LtcBenchmark {
    @Param({"256", "4096", "16384"})
    public int count;

    private float[] rough;
    private float[] ndotv;
    private float[] table;
    private final float[] m3 = new float[9];
    private final Vector3f v0 = new Vector3f(-0.5f, -0.5f, 1.0f);
    private final Vector3f v1 = new Vector3f(0.5f, -0.5f, 1.0f);
    private final Vector3f v2 = new Vector3f(0.5f, 0.5f, 1.0f);
    private final Vector3f v3 = new Vector3f(-0.5f, 0.5f, 1.0f);

    @Setup
    public void setup() {
        rough = new float[count];
        ndotv = new float[count];
        SplittableRandom rnd = new SplittableRandom(91011);
        for (int i = 0; i < count; i++) {
            rough[i] = (float) rnd.nextDouble();
            ndotv[i] = (float) rnd.nextDouble();
        }

        table = new float[64 * 64 * 9];
        for (int i = 0; i < table.length; i++) {
            table[i] = (float) rnd.nextDouble();
        }
    }

    @Benchmark
    public float ltcTableSampleLoop() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            LtcTablef.sampleMat3(table, 64, 64, rough[i], ndotv[i], m3);
            sum += m3[0] + m3[4] + m3[8];
        }
        return sum;
    }

    @Benchmark
    public float ltcFormFactorRectClippedLoop() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            sum += LtcEvalf.formFactorRectClipped(v0, v1, v2, v3);
        }
        return sum;
    }
}
