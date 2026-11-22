/*
 * The MIT License
 *
 * Copyright (c) 2026 JOML
 */
package org.dynamisengine.vectrix.bench;

import org.dynamisengine.vectrix.core.Matrix4f;
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
public class MemoryBackendBenchmark extends ThroughputBenchmark {

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
        address = java.lang.foreign.MemorySegment.ofBuffer(bb).address();
    }

    private long address;

    private void writeAndReadAddress() {
        long a = address;
        for (int i = 0; i < matrices; i++) {
            src[i].getToAddress(a);
            a += 16 * Float.BYTES;
        }
        a = address;
        for (int i = 0; i < matrices; i++) {
            src[i].setFromAddress(a);
            a += 16 * Float.BYTES;
        }
    }

    /** Raw-address API on its default FFM path: {@code matrices} writes, then {@code matrices} reads. */
    @Benchmark
    @Fork(value = 3, jvmArgsAppend = {"--enable-native-access=ALL-UNNAMED"})
    public Matrix4f addressFfm() {
        writeAndReadAddress();
        return src[0];
    }

    /** Raw-address API on the Unsafe fallback. */
    @Benchmark
    @Fork(value = 3, jvmArgsAppend = {"-Djoml.forceUnsafe=true"})
    public Matrix4f addressUnsafe() {
        writeAndReadAddress();
        return src[0];
    }

    @Benchmark
    @Fork(value = 3, jvmArgsAppend = {"-Djoml.noffm=true"})
    public ByteBuffer transferUnsafePathByteBuffer() {
        int off = 0;
        for (int i = 0; i < matrices; i++) {
            src[i].get(off, bb);
            off += 16 * Float.BYTES;
        }
        return bb;
    }

    @Benchmark
    @Fork(value = 3, jvmArgsAppend = {"-Djoml.noffm=true"})
    public FloatBuffer transferUnsafePathFloatBuffer() {
        int off = 0;
        for (int i = 0; i < matrices; i++) {
            src[i].get(off, fb);
            off += 16;
        }
        return fb;
    }

    @Benchmark
    @Fork(value = 3, jvmArgsAppend = {"-Djoml.nounsafe=true"})
    public ByteBuffer transferFfmOrNioByteBuffer() {
        int off = 0;
        for (int i = 0; i < matrices; i++) {
            src[i].get(off, bb);
            off += 16 * Float.BYTES;
        }
        return bb;
    }

    @Benchmark
    @Fork(value = 3, jvmArgsAppend = {"-Djoml.nounsafe=true"})
    public FloatBuffer transferFfmOrNioFloatBuffer() {
        int off = 0;
        for (int i = 0; i < matrices; i++) {
            src[i].get(off, fb);
            off += 16;
        }
        return fb;
    }

    @Benchmark
    @Fork(value = 3, jvmArgsAppend = {"-Djoml.forceFfm=true"})
    public ByteBuffer transferFfmPathByteBuffer() {
        int off = 0;
        for (int i = 0; i < matrices; i++) {
            src[i].get(off, bb);
            off += 16 * Float.BYTES;
        }
        return bb;
    }

    @Benchmark
    @Fork(value = 3, jvmArgsAppend = {"-Djoml.forceFfm=true"})
    public FloatBuffer transferFfmPathFloatBuffer() {
        int off = 0;
        for (int i = 0; i < matrices; i++) {
            src[i].get(off, fb);
            off += 16;
        }
        return fb;
    }
}
