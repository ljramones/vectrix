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
import org.vectrix.affine.RigidTransformf;
import org.vectrix.affine.Transformf;
import org.vectrix.gpu.InstanceSubmissionPipeline;
import org.vectrix.soa.TransformSoA;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SubsystemIntegrationBenchmark extends ThroughputBenchmark {
    @Param({"1024", "16384"})
    public int count;

    @Param({"4096"})
    public int vertices;

    @Param({"SEQUENTIAL", "CLUSTERED", "RANDOM", "COLD_CHUNKED"})
    public String traversalMode;

    @Param({"hot", "cold"})
    public String workingSet;

    private static final int JOINTS = 64;

    private InstanceSubmissionPipeline pipeline;
    private Transformf[] transforms;
    private int[] order;
    private float[] minX;
    private float[] minY;
    private float[] minZ;
    private float[] maxX;
    private float[] maxY;
    private float[] maxZ;
    private float[] outMinX;
    private float[] outMinY;
    private float[] outMinZ;
    private float[] outMaxX;
    private float[] outMaxY;
    private float[] outMaxZ;

    private TransformSoA joints;
    private int[] jointIndices;
    private float[] jointWeights;
    private float[] inX;
    private float[] inY;
    private float[] inZ;
    private float[] outX;
    private float[] outY;
    private float[] outZ;

    private float[] packedUpload;
    private float[] matrixUpload;

    @Setup
    public void setup() {
        pipeline = new InstanceSubmissionPipeline(count);
        transforms = new Transformf[count];
        order = new int[count];
        minX = new float[count];
        minY = new float[count];
        minZ = new float[count];
        maxX = new float[count];
        maxY = new float[count];
        maxZ = new float[count];
        outMinX = new float[count];
        outMinY = new float[count];
        outMinZ = new float[count];
        outMaxX = new float[count];
        outMaxY = new float[count];
        outMaxZ = new float[count];

        joints = new TransformSoA(JOINTS);
        jointIndices = new int[vertices * 4];
        jointWeights = new float[vertices * 4];
        inX = new float[vertices];
        inY = new float[vertices];
        inZ = new float[vertices];
        outX = new float[vertices];
        outY = new float[vertices];
        outZ = new float[vertices];

        packedUpload = new float[count * 12];
        matrixUpload = new float[count * 16];

        SplittableRandom rnd = new SplittableRandom(777L);
        for (int i = 0; i < count; i++) {
            Transformf t = new Transformf();
            t.translation.set((float) rnd.nextDouble(-200.0, 200.0), (float) rnd.nextDouble(-200.0, 200.0), (float) rnd.nextDouble(-200.0, 200.0));
            t.rotation.identity().rotateXYZ((float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0));
            t.scale.set((float) rnd.nextDouble(0.6, 2.5), (float) rnd.nextDouble(0.6, 2.5), (float) rnd.nextDouble(0.6, 2.5));
            transforms[i] = t;
            order[i] = i;
            float cx = (float) rnd.nextDouble(-80.0, 80.0);
            float cy = (float) rnd.nextDouble(-80.0, 80.0);
            float cz = (float) rnd.nextDouble(-80.0, 80.0);
            float ex = (float) rnd.nextDouble(0.5, 4.0);
            float ey = (float) rnd.nextDouble(0.5, 4.0);
            float ez = (float) rnd.nextDouble(0.5, 4.0);
            minX[i] = cx - ex;
            minY[i] = cy - ey;
            minZ[i] = cz - ez;
            maxX[i] = cx + ex;
            maxY[i] = cy + ey;
            maxZ[i] = cz + ez;
        }
        int[] traversal = buildTraversal(count, traversalMode, 128, rnd.split());
        if ("cold".equals(workingSet)) {
            int[] coldMap = buildRandomPermutation(count, rnd.split());
            for (int i = 0; i < count; i++) {
                order[i] = coldMap[traversal[i]];
            }
        } else {
            System.arraycopy(traversal, 0, order, 0, count);
        }

        RigidTransformf r = new RigidTransformf();
        for (int j = 0; j < JOINTS; j++) {
            r.translation.set((float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0));
            r.rotation.identity().rotateXYZ((float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0));
            joints.tx[j] = r.translation.x;
            joints.ty[j] = r.translation.y;
            joints.tz[j] = r.translation.z;
            joints.qx[j] = r.rotation.x;
            joints.qy[j] = r.rotation.y;
            joints.qz[j] = r.rotation.z;
            joints.qw[j] = r.rotation.w;
        }
        for (int i = 0; i < vertices; i++) {
            inX[i] = (float) rnd.nextDouble(-1.0, 1.0);
            inY[i] = (float) rnd.nextDouble(-1.0, 1.0);
            inZ[i] = (float) rnd.nextDouble(-1.0, 1.0);
            int base = i << 2;
            jointIndices[base] = rnd.nextInt(JOINTS);
            jointIndices[base + 1] = rnd.nextInt(JOINTS);
            jointIndices[base + 2] = rnd.nextInt(JOINTS);
            jointIndices[base + 3] = rnd.nextInt(JOINTS);
            float w0 = rnd.nextFloat();
            float w1 = rnd.nextFloat();
            float w2 = rnd.nextFloat();
            float w3 = rnd.nextFloat();
            float inv = 1.0f / (w0 + w1 + w2 + w3);
            jointWeights[base] = w0 * inv;
            jointWeights[base + 1] = w1 * inv;
            jointWeights[base + 2] = w2 * inv;
            jointWeights[base + 3] = w3 * inv;
        }
    }

    @Benchmark
    public float[] subsystemPackedDefaultPath() {
        return pipeline.processFrame(
                transforms, order,
                minX, minY, minZ, maxX, maxY, maxZ,
                outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ,
                joints, jointIndices, jointWeights, inX, inY, inZ, outX, outY, outZ,
                packedUpload, matrixUpload,
                count, vertices,
                InstanceSubmissionPipeline.Path.PACKED_AFFINE);
    }

    @Benchmark
    public float[] subsystemMatrixFallbackPath() {
        return pipeline.processFrame(
                transforms, order,
                minX, minY, minZ, maxX, maxY, maxZ,
                outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ,
                joints, jointIndices, jointWeights, inX, inY, inZ, outX, outY, outZ,
                packedUpload, matrixUpload,
                count, vertices,
                InstanceSubmissionPipeline.Path.MATRIX_FALLBACK);
    }

    private static void shuffle(int[] a, SplittableRandom rnd) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    private static int[] buildTraversal(int count, String mode, int chunkSize, SplittableRandom rnd) {
        if ("RANDOM".equals(mode)) {
            return buildRandomPermutation(count, rnd);
        }
        if ("CLUSTERED".equals(mode)) {
            int[] order = new int[count];
            int cluster = java.lang.Math.max(8, chunkSize / 2);
            int p = 0;
            for (int start = 0; start < count; start += cluster) {
                int end = java.lang.Math.min(start + cluster, count);
                for (int i = start; i < end; i++) {
                    order[p++] = i;
                }
            }
            return order;
        }
        if ("COLD_CHUNKED".equals(mode)) {
            int[] chunks = new int[(count + chunkSize - 1) / chunkSize];
            for (int i = 0; i < chunks.length; i++) {
                chunks[i] = i;
            }
            for (int i = chunks.length - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int t = chunks[i];
                chunks[i] = chunks[j];
                chunks[j] = t;
            }
            int[] order = new int[count];
            int p = 0;
            for (int c : chunks) {
                int start = c * chunkSize;
                int end = java.lang.Math.min(start + chunkSize, count);
                for (int i = start; i < end; i++) {
                    order[p++] = i;
                }
            }
            return order;
        }
        int[] order = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        return order;
    }

    private static int[] buildRandomPermutation(int count, SplittableRandom rnd) {
        int[] order = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        shuffle(order, rnd);
        return order;
    }
}
