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
import org.vectrix.geometry.MeshMath;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class MeshMathBenchmark {
    @Param({"256", "4096", "16384"})
    public int count;

    private Vector3f[] a;
    private Vector3f[] b;
    private Vector3f[] c;
    private Vector3f[] p;
    private Vector3f dest;

    @Setup
    public void setup() {
        a = new Vector3f[count];
        b = new Vector3f[count];
        c = new Vector3f[count];
        p = new Vector3f[count];
        dest = new Vector3f();
        SplittableRandom rnd = new SplittableRandom(123L);
        for (int i = 0; i < count; i++) {
            a[i] = new Vector3f((float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0));
            b[i] = new Vector3f((float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0));
            c[i] = new Vector3f((float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0));
            p[i] = new Vector3f((float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0));
        }
    }

    @Benchmark
    public Vector3f closestPointBatch() {
        for (int i = 0; i < count; i++) {
            MeshMath.closestPointOnTriangle(p[i], a[i], b[i], c[i], dest);
        }
        return dest;
    }

    @Benchmark
    public Vector3f barycentricBatch() {
        for (int i = 0; i < count; i++) {
            MeshMath.barycentric(p[i], a[i], b[i], c[i], dest);
        }
        return dest;
    }
}
