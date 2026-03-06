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
import org.vectrix.affine.PackedAffineArray;
import org.vectrix.affine.PackedAffineKernels;
import org.vectrix.affine.RigidTransformf;
import org.vectrix.affine.Transformf;
import org.vectrix.core.Matrix4f;
import org.vectrix.core.Vector3f;
import org.vectrix.soa.SkinningKernels;
import org.vectrix.soa.TransformSoA;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class IntegrationPipelineBenchmark extends ThroughputBenchmark {
    @Param({"1024", "16384"})
    public int count;

    @Param({"1024", "8192"})
    public int vertices;

    @Param({"SEQUENTIAL", "CLUSTERED", "RANDOM", "COLD_CHUNKED"})
    public String traversalMode;

    @Param({"hot", "cold"})
    public String workingSet;

    private static final int JOINTS = 64;

    private Transformf[] transforms;
    private PackedAffineArray packed;
    private Matrix4f[] matrices;
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

    private float[] packedUpload;
    private float[] matrixUpload;
    private final Vector3f tmpMin = new Vector3f();
    private final Vector3f tmpMax = new Vector3f();

    private TransformSoA jointRigid;
    private int[] jointIndices;
    private float[] jointWeights;
    private float[] inX;
    private float[] inY;
    private float[] inZ;
    private float[] outX;
    private float[] outY;
    private float[] outZ;

    @Setup
    public void setup() {
        transforms = new Transformf[count];
        packed = new PackedAffineArray(count);
        matrices = new Matrix4f[count];
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
        packedUpload = new float[count * 12];
        matrixUpload = new float[count * 16];

        jointRigid = new TransformSoA(JOINTS);
        jointIndices = new int[vertices * 4];
        jointWeights = new float[vertices * 4];
        inX = new float[vertices];
        inY = new float[vertices];
        inZ = new float[vertices];
        outX = new float[vertices];
        outY = new float[vertices];
        outZ = new float[vertices];

        SplittableRandom rnd = new SplittableRandom(901L);
        for (int i = 0; i < count; i++) {
            Transformf t = new Transformf();
            t.translation.set((float) rnd.nextDouble(-200.0, 200.0), (float) rnd.nextDouble(-200.0, 200.0), (float) rnd.nextDouble(-200.0, 200.0));
            t.rotation.identity().rotateXYZ((float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0));
            t.scale.set((float) rnd.nextDouble(0.6, 2.5), (float) rnd.nextDouble(0.6, 2.5), (float) rnd.nextDouble(0.6, 2.5));
            transforms[i] = t;
            matrices[i] = new Matrix4f();

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
            order[i] = i;
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
            jointRigid.tx[j] = r.translation.x;
            jointRigid.ty[j] = r.translation.y;
            jointRigid.tz[j] = r.translation.z;
            jointRigid.qx[j] = r.rotation.x;
            jointRigid.qy[j] = r.rotation.y;
            jointRigid.qz[j] = r.rotation.z;
            jointRigid.qw[j] = r.rotation.w;
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
    public float[] integrationPackedPipeline() {
        PackedAffineKernels.trsToPackedAffineBatch(transforms, packed, count);
        SkinningKernels.skinLbs4(jointRigid, jointIndices, jointWeights, inX, inY, inZ, outX, outY, outZ, vertices);
        PackedAffineKernels.transformAabbPackedAffineBatch(
                packed, order, minX, minY, minZ, maxX, maxY, maxZ,
                outMinX, outMinY, outMinZ, outMaxX, outMaxY, outMaxZ, count);
        PackedAffineKernels.uploadPackedAffine(packed, order, packedUpload, count);
        return packedUpload;
    }

    @Benchmark
    public float[] integrationMatrixPipeline() {
        for (int i = 0; i < count; i++) {
            Transformf t = transforms[i];
            matrices[i].translationRotateScale(t.translation, t.rotation, t.scale);
        }
        SkinningKernels.skinLbs4(jointRigid, jointIndices, jointWeights, inX, inY, inZ, outX, outY, outZ, vertices);
        for (int i = 0; i < count; i++) {
            int idx = order[i];
            matrices[idx].transformAab(minX[idx], minY[idx], minZ[idx], maxX[idx], maxY[idx], maxZ[idx], tmpMin, tmpMax);
            outMinX[idx] = tmpMin.x;
            outMinY[idx] = tmpMin.y;
            outMinZ[idx] = tmpMin.z;
            outMaxX[idx] = tmpMax.x;
            outMaxY[idx] = tmpMax.y;
            outMaxZ[idx] = tmpMax.z;
            int base = i << 4;
            matrixUpload[base] = matrices[idx].m00();
            matrixUpload[base + 1] = matrices[idx].m01();
            matrixUpload[base + 2] = matrices[idx].m02();
            matrixUpload[base + 3] = matrices[idx].m03();
            matrixUpload[base + 4] = matrices[idx].m10();
            matrixUpload[base + 5] = matrices[idx].m11();
            matrixUpload[base + 6] = matrices[idx].m12();
            matrixUpload[base + 7] = matrices[idx].m13();
            matrixUpload[base + 8] = matrices[idx].m20();
            matrixUpload[base + 9] = matrices[idx].m21();
            matrixUpload[base + 10] = matrices[idx].m22();
            matrixUpload[base + 11] = matrices[idx].m23();
            matrixUpload[base + 12] = matrices[idx].m30();
            matrixUpload[base + 13] = matrices[idx].m31();
            matrixUpload[base + 14] = matrices[idx].m32();
            matrixUpload[base + 15] = matrices[idx].m33();
        }
        return matrixUpload;
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
