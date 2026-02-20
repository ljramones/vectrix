/*
 * The MIT License
 *
 * Copyright (c) 2026 JOML
 */
package org.vectrix.bench;

import org.vectrix.Matrix4f;
import org.vectrix.Vector4f;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Batched matrix/vector workloads. This is the baseline harness for future SIMD and SoA/AoSoA kernels.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class BatchMatrixBenchmark {

    @Param({"64", "256", "4096"})
    public int size;

    private Matrix4f[] a;
    private Matrix4f[] b;
    private Matrix4f[] out;
    private Vector4f[] v;
    private Vector4f[] vOut;

    @Setup
    public void setup() {
        a = new Matrix4f[size];
        b = new Matrix4f[size];
        out = new Matrix4f[size];
        v = new Vector4f[size];
        vOut = new Vector4f[size];
        for (int i = 0; i < size; i++) {
            a[i] = new Matrix4f().translation(i * 0.001f, 1.0f, -2.0f).rotateXYZ(0.1f, 0.2f, 0.3f);
            b[i] = new Matrix4f().scaling(1.0f + (i % 7) * 0.01f).rotateXYZ(0.3f, 0.1f, 0.2f);
            out[i] = new Matrix4f();
            v[i] = new Vector4f(i * 0.001f, 0.5f, 1.5f, 1.0f);
            vOut[i] = new Vector4f();
        }
    }

    @Benchmark
    public Matrix4f[] mulBatch() {
        for (int i = 0; i < size; i++) {
            a[i].mul(b[i], out[i]);
        }
        return out;
    }

    @Benchmark
    public Vector4f[] transformBatch() {
        for (int i = 0; i < size; i++) {
            v[i].mul(a[i], vOut[i]);
        }
        return vOut;
    }
}
