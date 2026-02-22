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
import org.vectrix.sh.ShCoeffs16f;
import org.vectrix.sh.ShConvolution;
import org.vectrix.sh.ShProjection;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class ShBenchmark {
    @Param({"256", "4096", "16384"})
    public int count;

    private Vector3f[] dirs;
    private Vector3f[] rgbs;
    private ShCoeffs16f coeffs;
    private float[] scratch;
    private Vector3f tmp;

    @Setup
    public void setup() {
        dirs = new Vector3f[count];
        rgbs = new Vector3f[count];
        SplittableRandom rnd = new SplittableRandom(99);
        for (int i = 0; i < count; i++) {
            dirs[i] = new Vector3f((float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0)).normalize();
            rgbs[i] = new Vector3f((float) rnd.nextDouble(), (float) rnd.nextDouble(), (float) rnd.nextDouble());
        }
        coeffs = new ShCoeffs16f();
        scratch = new float[16];
        tmp = new Vector3f();
    }

    @Benchmark
    public ShCoeffs16f projectL3() {
        coeffs.zero();
        float w = (float) (4.0 * java.lang.Math.PI / count);
        for (int i = 0; i < count; i++) {
            Vector3f d = dirs[i];
            Vector3f c = rgbs[i];
            ShProjection.projectSampleL3(d.x, d.y, d.z, c.x, c.y, c.z, w, scratch, coeffs);
        }
        return coeffs;
    }

    @Benchmark
    public float evaluateL3() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            Vector3f d = dirs[i];
            ShConvolution.evaluateL3(coeffs, d, tmp);
            sum += tmp.x + tmp.y + tmp.z;
        }
        return sum;
    }
}
