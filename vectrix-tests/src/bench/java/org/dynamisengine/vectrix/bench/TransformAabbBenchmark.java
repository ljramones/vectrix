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
import org.dynamisengine.vectrix.affine.Affine4f;
import org.dynamisengine.vectrix.affine.BatchChunks;
import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.affine.PackedAffineKernels;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Matrix4x3f;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3f;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class TransformAabbBenchmark extends ThroughputBenchmark {
    @Param({"1", "4", "16", "64", "256", "1024", "4096", "16384"})
    public int count;

    @Param({"matrix4f", "affine4x3", "packedAffine"})
    public String representation;

    @Param({"uniform", "clustered"})
    public String dataDistribution;

    @Param({"hot", "cold"})
    public String accessPattern;

    @Param({"SEQUENTIAL", "STRIDED", "RANDOM", "CHUNKED"})
    public String traversalMode;

    @Param({"64", "128", "256", "512"})
    public int chunkSize;

    private Matrix4f[] matrices4f;
    private Matrix4x3f[] matrices4x3;
    private PackedAffineArray packed;
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

    private final Vector3f tmpMin = new Vector3f();
    private final Vector3f tmpMax = new Vector3f();

    @Setup
    public void setup() {
        matrices4f = new Matrix4f[count];
        matrices4x3 = new Matrix4x3f[count];
        packed = new PackedAffineArray(count);
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

        SplittableRandom rnd = new SplittableRandom(99L);
        Quaternionf q = new Quaternionf();
        Affine4f a = new Affine4f();
        for (int i = 0; i < count; i++) {
            float cx;
            float cy;
            float cz;
            if ("clustered".equals(dataDistribution)) {
                cx = (float) rnd.nextDouble(-25.0, 25.0);
                cy = (float) rnd.nextDouble(-25.0, 25.0);
                cz = (float) rnd.nextDouble(-25.0, 25.0);
            } else {
                cx = (float) rnd.nextDouble(-250.0, 250.0);
                cy = (float) rnd.nextDouble(-250.0, 250.0);
                cz = (float) rnd.nextDouble(-250.0, 250.0);
            }
            float ex = (float) rnd.nextDouble(0.1, 2.5);
            float ey = (float) rnd.nextDouble(0.1, 2.5);
            float ez = (float) rnd.nextDouble(0.1, 2.5);
            minX[i] = cx - ex;
            minY[i] = cy - ey;
            minZ[i] = cz - ez;
            maxX[i] = cx + ex;
            maxY[i] = cy + ey;
            maxZ[i] = cz + ez;

            float tx = (float) rnd.nextDouble(-40.0, 40.0);
            float ty = (float) rnd.nextDouble(-40.0, 40.0);
            float tz = (float) rnd.nextDouble(-40.0, 40.0);
            q.identity().rotateXYZ((float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0));
            float sx = (float) rnd.nextDouble(0.5, 2.0);
            float sy = (float) rnd.nextDouble(0.5, 2.0);
            float sz = (float) rnd.nextDouble(0.5, 2.0);
            matrices4f[i] = new Matrix4f().translationRotateScale(tx, ty, tz, q.x, q.y, q.z, q.w, sx, sy, sz);
            matrices4x3[i] = new Matrix4x3f().translationRotateScale(tx, ty, tz, q.x, q.y, q.z, q.w, sx, sy, sz);
            a.translationRotateScale(tx, ty, tz, q.x, q.y, q.z, q.w, sx, sy, sz);
            packed.set(i,
                    a.m00, a.m01, a.m02, a.m30,
                    a.m10, a.m11, a.m12, a.m31,
                    a.m20, a.m21, a.m22, a.m32);
        }

        int[] traversal = buildTraversal(count, traversalMode, chunkSize, rnd);
        if ("cold".equals(accessPattern)) {
            int[] coldMap = buildRandomPermutation(count, rnd.split());
            for (int i = 0; i < count; i++) {
                order[i] = coldMap[traversal[i]];
            }
        } else {
            System.arraycopy(traversal, 0, order, 0, count);
        }
    }

    @Benchmark
    public float[] transformAabbBatch() {
        if ("packedAffine".equals(representation)) {
            PackedAffineKernels.transformAabbPackedAffineBatch(
                    packed,
                    order,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    outMinX, outMinY, outMinZ,
                    outMaxX, outMaxY, outMaxZ,
                    count);
        } else if ("affine4x3".equals(representation)) {
            for (int i = 0; i < count; i++) {
                int idx = order[i];
                matrices4x3[idx].transformAab(minX[idx], minY[idx], minZ[idx], maxX[idx], maxY[idx], maxZ[idx], tmpMin, tmpMax);
                outMinX[idx] = tmpMin.x;
                outMinY[idx] = tmpMin.y;
                outMinZ[idx] = tmpMin.z;
                outMaxX[idx] = tmpMax.x;
                outMaxY[idx] = tmpMax.y;
                outMaxZ[idx] = tmpMax.z;
            }
        } else {
            for (int i = 0; i < count; i++) {
                int idx = order[i];
                matrices4f[idx].transformAab(minX[idx], minY[idx], minZ[idx], maxX[idx], maxY[idx], maxZ[idx], tmpMin, tmpMax);
                outMinX[idx] = tmpMin.x;
                outMinY[idx] = tmpMin.y;
                outMinZ[idx] = tmpMin.z;
                outMaxX[idx] = tmpMax.x;
                outMaxY[idx] = tmpMax.y;
                outMaxZ[idx] = tmpMax.z;
            }
        }
        return outMinX;
    }

    @Benchmark
    public float[] transformAabbBatchChunked() {
        if ("packedAffine".equals(representation)) {
            PackedAffineKernels.transformAabbPackedAffineChunked(
                    packed,
                    order,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    outMinX, outMinY, outMinZ,
                    outMaxX, outMaxY, outMaxZ,
                    count,
                    chunkSize);
            return outMinX;
        }

        BatchChunks.forEachChunk(count, chunkSize, (start, end) -> {
            if ("affine4x3".equals(representation)) {
                for (int i = start; i < end; i++) {
                    int idx = order[i];
                    matrices4x3[idx].transformAab(minX[idx], minY[idx], minZ[idx], maxX[idx], maxY[idx], maxZ[idx], tmpMin, tmpMax);
                    outMinX[idx] = tmpMin.x;
                    outMinY[idx] = tmpMin.y;
                    outMinZ[idx] = tmpMin.z;
                    outMaxX[idx] = tmpMax.x;
                    outMaxY[idx] = tmpMax.y;
                    outMaxZ[idx] = tmpMax.z;
                }
            } else {
                for (int i = start; i < end; i++) {
                    int idx = order[i];
                    matrices4f[idx].transformAab(minX[idx], minY[idx], minZ[idx], maxX[idx], maxY[idx], maxZ[idx], tmpMin, tmpMax);
                    outMinX[idx] = tmpMin.x;
                    outMinY[idx] = tmpMin.y;
                    outMinZ[idx] = tmpMin.z;
                    outMaxX[idx] = tmpMax.x;
                    outMaxY[idx] = tmpMax.y;
                    outMaxZ[idx] = tmpMax.z;
                }
            }
        });
        return outMinX;
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
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        return order;
    }
}
