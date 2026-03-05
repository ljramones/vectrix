/*
 * The MIT License
 *
 * Copyright (c) 2026 JOML
 */
package org.vectrix.bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.vectrix.core.Matrix4f;
import org.vectrix.core.Matrix4x3f;
import org.vectrix.core.Quaternionf;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class QuatMatrixConversionBenchmark extends ThroughputBenchmark {
    @Param({"1", "4", "16", "64", "256", "1024", "4096", "16384"})
    public int size;

    @Param({"uniform", "identityHeavy"})
    public String dataDistribution;

    private Quaternionf[] rotations;
    private float[] tx;
    private float[] ty;
    private float[] tz;
    private float[] sx;
    private float[] sy;
    private float[] sz;
    private Matrix4f[] outMatrix4f;
    private Matrix4x3f[] outMatrix4x3f;

    @Setup
    public void setup() {
        rotations = new Quaternionf[size];
        tx = new float[size];
        ty = new float[size];
        tz = new float[size];
        sx = new float[size];
        sy = new float[size];
        sz = new float[size];
        outMatrix4f = new Matrix4f[size];
        outMatrix4x3f = new Matrix4x3f[size];

        SplittableRandom rnd = new SplittableRandom(123L);
        for (int i = 0; i < size; i++) {
            Quaternionf q = new Quaternionf();
            if ("identityHeavy".equals(dataDistribution) && (i & 3) != 0) {
                q.identity();
            } else {
                q.rotationXYZ((float) rnd.nextDouble(-1.5, 1.5), (float) rnd.nextDouble(-1.5, 1.5), (float) rnd.nextDouble(-1.5, 1.5));
            }
            rotations[i] = q;
            tx[i] = (float) rnd.nextDouble(-20.0, 20.0);
            ty[i] = (float) rnd.nextDouble(-20.0, 20.0);
            tz[i] = (float) rnd.nextDouble(-20.0, 20.0);
            sx[i] = (float) rnd.nextDouble(0.5, 2.0);
            sy[i] = (float) rnd.nextDouble(0.5, 2.0);
            sz[i] = (float) rnd.nextDouble(0.5, 2.0);
            outMatrix4f[i] = new Matrix4f();
            outMatrix4x3f[i] = new Matrix4x3f();
        }
    }

    @Benchmark
    public Matrix4f[] quatToMatrixBatch() {
        for (int i = 0; i < size; i++) {
            Quaternionf q = rotations[i];
            outMatrix4f[i].translationRotateScale(tx[i], ty[i], tz[i], q.x, q.y, q.z, q.w, sx[i], sy[i], sz[i]);
        }
        return outMatrix4f;
    }

    @Benchmark
    public Matrix4x3f[] quatToAffineMatrixBatch() {
        for (int i = 0; i < size; i++) {
            Quaternionf q = rotations[i];
            outMatrix4x3f[i].translationRotateScale(tx[i], ty[i], tz[i], q.x, q.y, q.z, q.w, sx[i], sy[i], sz[i]);
        }
        return outMatrix4x3f;
    }
}
