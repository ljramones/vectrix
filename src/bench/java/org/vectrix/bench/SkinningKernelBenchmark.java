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
import org.vectrix.affine.DualQuatTransformf;
import org.vectrix.affine.RigidTransformf;
import org.vectrix.soa.DualQuatSoA;
import org.vectrix.soa.SkinningKernels;
import org.vectrix.soa.TransformSoA;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class SkinningKernelBenchmark {
    @Param({"256", "4096", "16384"})
    public int vertices;

    private static final int JOINTS = 64;

    private TransformSoA jointRigid;
    private DualQuatSoA jointDualQuat;
    private int[] jointIndices;
    private float[] jointWeights;
    private int[] j0, j1, j2, j3;
    private float[] w0, w1, w2, w3;
    private float[] inX, inY, inZ;
    private float[] outX, outY, outZ;

    @Setup
    public void setup() {
        jointRigid = new TransformSoA(JOINTS);
        jointDualQuat = new DualQuatSoA(JOINTS);
        jointIndices = new int[vertices * 4];
        jointWeights = new float[vertices * 4];
        j0 = new int[vertices];
        j1 = new int[vertices];
        j2 = new int[vertices];
        j3 = new int[vertices];
        w0 = new float[vertices];
        w1 = new float[vertices];
        w2 = new float[vertices];
        w3 = new float[vertices];
        inX = new float[vertices];
        inY = new float[vertices];
        inZ = new float[vertices];
        outX = new float[vertices];
        outY = new float[vertices];
        outZ = new float[vertices];

        SplittableRandom rnd = new SplittableRandom(7L);
        RigidTransformf r = new RigidTransformf();
        DualQuatTransformf dq = new DualQuatTransformf();
        for (int j = 0; j < JOINTS; j++) {
            r.translation.set((float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0));
            r.rotation.identity().rotateXYZ((float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0));
            jointRigid.tx[j] = r.translation.x;
            jointRigid.ty[j] = r.translation.y;
            jointRigid.tz[j] = r.translation.z;
            jointRigid.qx[j] = r.rotation.x;
            jointRigid.qy[j] = r.rotation.y;
            jointRigid.qz[j] = r.rotation.z;
            jointRigid.qw[j] = r.rotation.w;
            dq.setFromRigid(r);
            jointDualQuat.set(j, dq);
        }
        for (int i = 0; i < vertices; i++) {
            inX[i] = (float) rnd.nextDouble(-1.0, 1.0);
            inY[i] = (float) rnd.nextDouble(-1.0, 1.0);
            inZ[i] = (float) rnd.nextDouble(-1.0, 1.0);
            int base = i << 2;
            int j0 = rnd.nextInt(JOINTS);
            int j1 = rnd.nextInt(JOINTS);
            int j2 = rnd.nextInt(JOINTS);
            int j3 = rnd.nextInt(JOINTS);
            jointIndices[base] = j0;
            jointIndices[base + 1] = j1;
            jointIndices[base + 2] = j2;
            jointIndices[base + 3] = j3;
            this.j0[i] = j0;
            this.j1[i] = j1;
            this.j2[i] = j2;
            this.j3[i] = j3;
            float w0 = rnd.nextFloat();
            float w1 = rnd.nextFloat();
            float w2 = rnd.nextFloat();
            float w3 = rnd.nextFloat();
            float inv = 1.0f / (w0 + w1 + w2 + w3);
            jointWeights[base] = w0 * inv;
            jointWeights[base + 1] = w1 * inv;
            jointWeights[base + 2] = w2 * inv;
            jointWeights[base + 3] = w3 * inv;
            this.w0[i] = jointWeights[base];
            this.w1[i] = jointWeights[base + 1];
            this.w2[i] = jointWeights[base + 2];
            this.w3[i] = jointWeights[base + 3];
        }
    }

    @Benchmark
    public float[] skinLbs4() {
        SkinningKernels.skinLbs4(jointRigid, jointIndices, jointWeights, inX, inY, inZ, outX, outY, outZ, vertices);
        return outX;
    }

    @Benchmark
    public float[] skinLbs4SoAAuto() {
        SkinningKernels.skinLbs4SoA(jointRigid, j0, j1, j2, j3, w0, w1, w2, w3, inX, inY, inZ, outX, outY, outZ, vertices);
        return outX;
    }

    @Benchmark
    public float[] skinLbs4SoAScalarForced() {
        SkinningKernels.skinLbs4SoAScalar(jointRigid, j0, j1, j2, j3, w0, w1, w2, w3, inX, inY, inZ, outX, outY, outZ, vertices);
        return outX;
    }

    @Benchmark
    public float[] skinLbs4SoASimdForced() {
        SkinningKernels.skinLbs4SoASimd(jointRigid, j0, j1, j2, j3, w0, w1, w2, w3, inX, inY, inZ, outX, outY, outZ, vertices);
        return outX;
    }

    @Benchmark
    public float[] skinDualQuat4() {
        SkinningKernels.skinDualQuat4(jointDualQuat, jointIndices, jointWeights, inX, inY, inZ, outX, outY, outZ, vertices);
        return outX;
    }
}
