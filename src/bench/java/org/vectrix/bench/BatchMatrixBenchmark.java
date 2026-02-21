/*
 * The MIT License
 *
 * Copyright (c) 2026 JOML
 */
package org.vectrix.bench;

import org.vectrix.core.Matrix4f;
import org.vectrix.core.Vector4f;
import org.openjdk.jmh.annotations.*;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

import java.util.concurrent.TimeUnit;

/**
 * Batched matrix/vector workloads. This is the baseline harness for future SIMD and SoA/AoSoA kernels.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Fork(value = 1, jvmArgsAppend = {"--add-modules", "jdk.incubator.vector", "--enable-native-access=ALL-UNNAMED"})
public class BatchMatrixBenchmark {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    @Param({"64", "256", "4096"})
    public int size;

    private Matrix4f[] a;
    private Matrix4f[] b;
    private Matrix4f[] out;
    private Vector4f[] v;
    private Vector4f[] vOut;
    private Matrix4f transformMatrix;
    private float[] inX;
    private float[] inY;
    private float[] inZ;
    private float[] inW;
    private float[] outX;
    private float[] outY;
    private float[] outZ;
    private float[] outW;

    @Setup
    public void setup() {
        a = new Matrix4f[size];
        b = new Matrix4f[size];
        out = new Matrix4f[size];
        v = new Vector4f[size];
        vOut = new Vector4f[size];
        inX = new float[size];
        inY = new float[size];
        inZ = new float[size];
        inW = new float[size];
        outX = new float[size];
        outY = new float[size];
        outZ = new float[size];
        outW = new float[size];
        transformMatrix = new Matrix4f().translation(0.3f, -0.9f, 1.7f).rotateXYZ(0.21f, 0.42f, 0.13f);
        for (int i = 0; i < size; i++) {
            a[i] = new Matrix4f().translation(i * 0.001f, 1.0f, -2.0f).rotateXYZ(0.1f, 0.2f, 0.3f);
            b[i] = new Matrix4f().scaling(1.0f + (i % 7) * 0.01f).rotateXYZ(0.3f, 0.1f, 0.2f);
            out[i] = new Matrix4f();
            v[i] = new Vector4f(i * 0.001f, 0.5f, 1.5f, 1.0f);
            vOut[i] = new Vector4f();
            inX[i] = i * 0.001f;
            inY[i] = 0.5f;
            inZ[i] = 1.5f;
            inW[i] = 1.0f;
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

    @Benchmark
    public float[] transformBatchSoAScalar() {
        float m00 = transformMatrix.m00(), m01 = transformMatrix.m01(), m02 = transformMatrix.m02(), m03 = transformMatrix.m03();
        float m10 = transformMatrix.m10(), m11 = transformMatrix.m11(), m12 = transformMatrix.m12(), m13 = transformMatrix.m13();
        float m20 = transformMatrix.m20(), m21 = transformMatrix.m21(), m22 = transformMatrix.m22(), m23 = transformMatrix.m23();
        float m30 = transformMatrix.m30(), m31 = transformMatrix.m31(), m32 = transformMatrix.m32(), m33 = transformMatrix.m33();
        for (int i = 0; i < size; i++) {
            float x = inX[i], y = inY[i], z = inZ[i], w = inW[i];
            outX[i] = m00 * x + m10 * y + m20 * z + m30 * w;
            outY[i] = m01 * x + m11 * y + m21 * z + m31 * w;
            outZ[i] = m02 * x + m12 * y + m22 * z + m32 * w;
            outW[i] = m03 * x + m13 * y + m23 * z + m33 * w;
        }
        return outX;
    }

    @Benchmark
    public float[] transformBatchSoAVector() {
        FloatVector m00 = FloatVector.broadcast(SPECIES, transformMatrix.m00());
        FloatVector m01 = FloatVector.broadcast(SPECIES, transformMatrix.m01());
        FloatVector m02 = FloatVector.broadcast(SPECIES, transformMatrix.m02());
        FloatVector m03 = FloatVector.broadcast(SPECIES, transformMatrix.m03());
        FloatVector m10 = FloatVector.broadcast(SPECIES, transformMatrix.m10());
        FloatVector m11 = FloatVector.broadcast(SPECIES, transformMatrix.m11());
        FloatVector m12 = FloatVector.broadcast(SPECIES, transformMatrix.m12());
        FloatVector m13 = FloatVector.broadcast(SPECIES, transformMatrix.m13());
        FloatVector m20 = FloatVector.broadcast(SPECIES, transformMatrix.m20());
        FloatVector m21 = FloatVector.broadcast(SPECIES, transformMatrix.m21());
        FloatVector m22 = FloatVector.broadcast(SPECIES, transformMatrix.m22());
        FloatVector m23 = FloatVector.broadcast(SPECIES, transformMatrix.m23());
        FloatVector m30 = FloatVector.broadcast(SPECIES, transformMatrix.m30());
        FloatVector m31 = FloatVector.broadcast(SPECIES, transformMatrix.m31());
        FloatVector m32 = FloatVector.broadcast(SPECIES, transformMatrix.m32());
        FloatVector m33 = FloatVector.broadcast(SPECIES, transformMatrix.m33());

        int i = 0;
        int upper = SPECIES.loopBound(size);
        for (; i < upper; i += SPECIES.length()) {
            FloatVector x = FloatVector.fromArray(SPECIES, inX, i);
            FloatVector y = FloatVector.fromArray(SPECIES, inY, i);
            FloatVector z = FloatVector.fromArray(SPECIES, inZ, i);
            FloatVector w = FloatVector.fromArray(SPECIES, inW, i);

            x.fma(m00, y.fma(m10, z.fma(m20, w.mul(m30)))).intoArray(outX, i);
            x.fma(m01, y.fma(m11, z.fma(m21, w.mul(m31)))).intoArray(outY, i);
            x.fma(m02, y.fma(m12, z.fma(m22, w.mul(m32)))).intoArray(outZ, i);
            x.fma(m03, y.fma(m13, z.fma(m23, w.mul(m33)))).intoArray(outW, i);
        }
        for (; i < size; i++) {
            float x = inX[i], y = inY[i], z = inZ[i], w = inW[i];
            outX[i] = transformMatrix.m00() * x + transformMatrix.m10() * y + transformMatrix.m20() * z + transformMatrix.m30() * w;
            outY[i] = transformMatrix.m01() * x + transformMatrix.m11() * y + transformMatrix.m21() * z + transformMatrix.m31() * w;
            outZ[i] = transformMatrix.m02() * x + transformMatrix.m12() * y + transformMatrix.m22() * z + transformMatrix.m32() * w;
            outW[i] = transformMatrix.m03() * x + transformMatrix.m13() * y + transformMatrix.m23() * z + transformMatrix.m33() * w;
        }
        return outX;
    }
}
