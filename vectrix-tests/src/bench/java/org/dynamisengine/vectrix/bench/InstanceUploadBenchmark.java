/*
 * The MIT License
 *
 * Copyright (c) 2026 JOML
 */
package org.dynamisengine.vectrix.bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.dynamisengine.vectrix.affine.BatchChunks;
import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.affine.PackedAffineKernels;
import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.core.Matrix4f;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class InstanceUploadBenchmark extends ThroughputBenchmark {
    @Param({"1", "4", "16", "64", "256", "1024", "4096", "16384"})
    public int instances;

    @Param({"uniform", "clustered"})
    public String dataDistribution;

    @Param({"hot", "cold"})
    public String accessPattern;

    @Param({"SEQUENTIAL", "STRIDED", "RANDOM", "CHUNKED"})
    public String traversalMode;

    @Param({"64", "128", "256", "512"})
    public int chunkSize;

    private Transformf[] transforms;
    private PackedAffineArray packedTransforms;
    private int[] order;
    private float[] matrixUpload;
    private float[] packedAffineUpload;
    private final Matrix4f tmpMatrix = new Matrix4f();

    @Setup
    public void setup() {
        transforms = new Transformf[instances];
        packedTransforms = new PackedAffineArray(instances);
        order = new int[instances];
        matrixUpload = new float[instances * 16];
        packedAffineUpload = new float[instances * 12];
        SplittableRandom rnd = new SplittableRandom(17L);
        for (int i = 0; i < instances; i++) {
            Transformf t = new Transformf();
            if ("clustered".equals(dataDistribution)) {
                t.translation.set((float) rnd.nextDouble(-25.0, 25.0), (float) rnd.nextDouble(-25.0, 25.0), (float) rnd.nextDouble(-25.0, 25.0));
            } else {
                t.translation.set((float) rnd.nextDouble(-250.0, 250.0), (float) rnd.nextDouble(-250.0, 250.0), (float) rnd.nextDouble(-250.0, 250.0));
            }
            t.rotation.identity().rotateXYZ((float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0));
            t.scale.set((float) rnd.nextDouble(0.5, 3.0), (float) rnd.nextDouble(0.5, 3.0), (float) rnd.nextDouble(0.5, 3.0));
            transforms[i] = t;
        }
        PackedAffineKernels.trsToPackedAffineBatch(transforms, packedTransforms, instances);

        int[] traversal = buildTraversal(instances, traversalMode, chunkSize, rnd.split());
        if ("cold".equals(accessPattern)) {
            int[] coldMap = buildRandomPermutation(instances, rnd.split());
            for (int i = 0; i < instances; i++) {
                order[i] = coldMap[traversal[i]];
            }
        } else {
            System.arraycopy(traversal, 0, order, 0, instances);
        }
    }

    @Benchmark
    public float[] instanceUploadMatrix4f() {
        for (int i = 0; i < instances; i++) {
            int idx = order[i];
            int base = i << 4;
            Transformf t = transforms[idx];
            tmpMatrix.translationRotateScale(t.translation, t.rotation, t.scale);
            matrixUpload[base] = tmpMatrix.m00();
            matrixUpload[base + 1] = tmpMatrix.m01();
            matrixUpload[base + 2] = tmpMatrix.m02();
            matrixUpload[base + 3] = tmpMatrix.m03();
            matrixUpload[base + 4] = tmpMatrix.m10();
            matrixUpload[base + 5] = tmpMatrix.m11();
            matrixUpload[base + 6] = tmpMatrix.m12();
            matrixUpload[base + 7] = tmpMatrix.m13();
            matrixUpload[base + 8] = tmpMatrix.m20();
            matrixUpload[base + 9] = tmpMatrix.m21();
            matrixUpload[base + 10] = tmpMatrix.m22();
            matrixUpload[base + 11] = tmpMatrix.m23();
            matrixUpload[base + 12] = tmpMatrix.m30();
            matrixUpload[base + 13] = tmpMatrix.m31();
            matrixUpload[base + 14] = tmpMatrix.m32();
            matrixUpload[base + 15] = tmpMatrix.m33();
        }
        return matrixUpload;
    }

    @Benchmark
    public float[] instanceUploadPackedAffine() {
        PackedAffineKernels.uploadPackedAffine(packedTransforms, order, packedAffineUpload, instances);
        return packedAffineUpload;
    }

    @Benchmark
    public float[] instanceUploadPackedAffineChunked() {
        PackedAffineKernels.uploadPackedAffineChunked(packedTransforms, order, packedAffineUpload, instances, chunkSize);
        return packedAffineUpload;
    }

    @Benchmark
    public float[] instanceUploadMatrix4fChunked() {
        BatchChunks.forEachChunk(instances, chunkSize, (start, end) -> {
            for (int i = start; i < end; i++) {
                int idx = order[i];
                int base = i << 4;
                Transformf t = transforms[idx];
                tmpMatrix.translationRotateScale(t.translation, t.rotation, t.scale);
                matrixUpload[base] = tmpMatrix.m00();
                matrixUpload[base + 1] = tmpMatrix.m01();
                matrixUpload[base + 2] = tmpMatrix.m02();
                matrixUpload[base + 3] = tmpMatrix.m03();
                matrixUpload[base + 4] = tmpMatrix.m10();
                matrixUpload[base + 5] = tmpMatrix.m11();
                matrixUpload[base + 6] = tmpMatrix.m12();
                matrixUpload[base + 7] = tmpMatrix.m13();
                matrixUpload[base + 8] = tmpMatrix.m20();
                matrixUpload[base + 9] = tmpMatrix.m21();
                matrixUpload[base + 10] = tmpMatrix.m22();
                matrixUpload[base + 11] = tmpMatrix.m23();
                matrixUpload[base + 12] = tmpMatrix.m30();
                matrixUpload[base + 13] = tmpMatrix.m31();
                matrixUpload[base + 14] = tmpMatrix.m32();
                matrixUpload[base + 15] = tmpMatrix.m33();
            }
        });
        return matrixUpload;
    }

    private static int[] buildTraversal(int count, String mode, int chunkSize, SplittableRandom rnd) {
        if ("RANDOM".equals(mode)) {
            return buildRandomPermutation(count, rnd);
        }
        if ("STRIDED".equals(mode)) {
            int[] order = new int[count];
            int step = 17;
            int idx = 0;
            for (int i = 0; i < count; i++) {
                order[i] = idx;
                idx = (idx + step) % count;
            }
            return order;
        }
        if ("CHUNKED".equals(mode)) {
            int[] order = new int[count];
            int c = chunkSize <= 0 ? 128 : chunkSize;
            int p = 0;
            for (int start = 0; start < count; start += c) {
                int end = java.lang.Math.min(start + c, count);
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
        for (int i = count - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int v = order[i];
            order[i] = order[j];
            order[j] = v;
        }
        return order;
    }
}
