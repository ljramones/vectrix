/*
 * The MIT License
 *
 * Copyright (c) 2026 JOML
 */
package org.vectrix.bench;

import org.vectrix.Matrix4f;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.TimeUnit;

/**
 * GPU-upload-style staging benchmark: AoS matrix serialization vs SoA staging.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class InteropBenchmark {

    @Param({"64", "1024", "8192"})
    public int count;

    private Matrix4f[] matrices;
    private ByteBuffer aosBuffer;

    private float[] soa00;
    private float[] soa11;
    private float[] soa22;
    private float[] soa33;

    @Setup
    public void setup() {
        matrices = new Matrix4f[count];
        for (int i = 0; i < count; i++) {
            matrices[i] = new Matrix4f().translation(i * 0.01f, i * 0.02f, i * 0.03f).rotateXYZ(0.1f, 0.2f, 0.3f);
        }
        aosBuffer = ByteBuffer.allocateDirect(count * 16 * Float.BYTES).order(ByteOrder.nativeOrder());

        soa00 = new float[count];
        soa11 = new float[count];
        soa22 = new float[count];
        soa33 = new float[count];
    }

    @Benchmark
    public ByteBuffer stageAoSMatrixUpload() {
        int off = 0;
        for (int i = 0; i < count; i++) {
            matrices[i].get(off, aosBuffer);
            off += 16 * Float.BYTES;
        }
        return aosBuffer;
    }

    @Benchmark
    public FloatBuffer stageSoADiagonalUpload() {
        for (int i = 0; i < count; i++) {
            Matrix4f m = matrices[i];
            soa00[i] = m.m00();
            soa11[i] = m.m11();
            soa22[i] = m.m22();
            soa33[i] = m.m33();
        }
        return FloatBuffer.wrap(soa00);
    }
}
