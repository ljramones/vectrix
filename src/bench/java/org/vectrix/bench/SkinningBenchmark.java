/*
 * The MIT License
 *
 * Copyright (c) 2026 JOML
 */
package org.vectrix.bench;

import org.vectrix.core.Matrix4f;
import org.vectrix.core.Quaternionf;
import org.vectrix.core.Vector3f;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Skinning-style benchmark harness. Extend with full dual quaternion kernels as they land.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SkinningBenchmark extends ThroughputBenchmark {

    @Param({"1", "4", "16", "64", "256", "1024", "4096", "16384"})
    public int vertices;

    private Vector3f[] positions;
    private Vector3f[] out;
    private Vector3f[] tmpA;
    private Vector3f[] tmpB;

    private Matrix4f m0;
    private Matrix4f m1;
    private Quaternionf q0;
    private Quaternionf q1;
    private Vector3f t0;
    private Vector3f t1;

    @Setup
    public void setup() {
        positions = new Vector3f[vertices];
        out = new Vector3f[vertices];
        tmpA = new Vector3f[vertices];
        tmpB = new Vector3f[vertices];
        for (int i = 0; i < vertices; i++) {
            positions[i] = new Vector3f(i * 0.001f, 1.0f, 0.25f);
            out[i] = new Vector3f();
            tmpA[i] = new Vector3f();
            tmpB[i] = new Vector3f();
        }

        m0 = new Matrix4f().translation(0.2f, 0.0f, -0.1f).rotateXYZ(0.1f, 0.2f, 0.3f);
        m1 = new Matrix4f().translation(-0.3f, 0.5f, 0.2f).rotateXYZ(0.2f, 0.1f, -0.1f);

        q0 = new Quaternionf().rotationXYZ(0.1f, 0.2f, 0.3f);
        q1 = new Quaternionf().rotationXYZ(0.2f, 0.1f, -0.1f);
        t0 = new Vector3f(0.2f, 0.0f, -0.1f);
        t1 = new Vector3f(-0.3f, 0.5f, 0.2f);
    }

    @Benchmark
    public Vector3f[] lbsLikeMatrixSkinning() {
        final float w0 = 0.6f;
        final float w1 = 0.4f;
        for (int i = 0; i < vertices; i++) {
            positions[i].mulPosition(m0, tmpA[i]).mul(w0);
            positions[i].mulPosition(m1, tmpB[i]).mul(w1);
            out[i].set(tmpA[i]).add(tmpB[i]);
        }
        return out;
    }

    @Benchmark
    public Vector3f[] quaternionTransformBlend() {
        final float w0 = 0.6f;
        final float w1 = 0.4f;
        for (int i = 0; i < vertices; i++) {
            Vector3f p = positions[i];
            q0.transform(p, tmpA[i]).add(t0).mul(w0);
            q1.transform(p, tmpB[i]).add(t1).mul(w1);
            out[i].set(tmpA[i]).add(tmpB[i]);
        }
        return out;
    }
}
