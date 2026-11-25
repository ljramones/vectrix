/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.dynamisengine.vectrix.bench;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.optics.Fresnelf;
import org.dynamisengine.vectrix.optics.ThinFilmf;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class OpticsBenchmark extends ThroughputBenchmark {
    @Param({"256", "4096", "16384"})
    public int count;

    private float[] cosTheta;
    private Vector3f out;

    @Setup
    public void setup() {
        cosTheta = new float[count];
        SplittableRandom rnd = new SplittableRandom(1234);
        for (int i = 0; i < count; i++) {
            cosTheta[i] = (float) rnd.nextDouble();
        }
        out = new Vector3f();
    }

    @Benchmark
    public float fresnelDielectricLoop() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            sum += Fresnelf.dielectric(cosTheta[i], 1.0f, 1.5f);
        }
        return sum;
    }

    @Benchmark
    public Vector3f thinFilmRgbLoop() {
        for (int i = 0; i < count; i++) {
            ThinFilmf.reflectanceRgb(1.0f, 1.38f, 1.5f, 320.0f, cosTheta[i], out);
        }
        return out;
    }
}
