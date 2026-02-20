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
 * Memory transfer benchmark with backend/JVM property forks.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class MemoryBackendBenchmark {

    @Param({"1", "16", "256"})
    public int matrices;

    private Matrix4f[] src;
    private ByteBuffer bb;
    private FloatBuffer fb;

    @Setup
    public void setup() {
        src = new Matrix4f[matrices];
        for (int i = 0; i < matrices; i++) {
            src[i] = new Matrix4f().translation(i * 0.01f, i * 0.02f, i * -0.01f).rotateXYZ(0.1f, 0.2f, 0.3f);
        }
        bb = ByteBuffer.allocateDirect(matrices * 16 * Float.BYTES).order(ByteOrder.nativeOrder());
        fb = bb.asFloatBuffer();
    }

    @Benchmark
    @Fork(value = 1, jvmArgsAppend = {"-Djoml.noffm=true"})
    public ByteBuffer transferUnsafePathByteBuffer() {
        int off = 0;
        for (int i = 0; i < matrices; i++) {
            src[i].get(off, bb);
            off += 16 * Float.BYTES;
        }
        return bb;
    }

    @Benchmark
    @Fork(value = 1, jvmArgsAppend = {"-Djoml.noffm=true"})
    public FloatBuffer transferUnsafePathFloatBuffer() {
        int off = 0;
        for (int i = 0; i < matrices; i++) {
            src[i].get(off, fb);
            off += 16;
        }
        return fb;
    }

    @Benchmark
    @Fork(value = 1, jvmArgsAppend = {"-Djoml.nounsafe=true"})
    public ByteBuffer transferFfmOrNioByteBuffer() {
        int off = 0;
        for (int i = 0; i < matrices; i++) {
            src[i].get(off, bb);
            off += 16 * Float.BYTES;
        }
        return bb;
    }

    @Benchmark
    @Fork(value = 1, jvmArgsAppend = {"-Djoml.nounsafe=true"})
    public FloatBuffer transferFfmOrNioFloatBuffer() {
        int off = 0;
        for (int i = 0; i < matrices; i++) {
            src[i].get(off, fb);
            off += 16;
        }
        return fb;
    }
}
