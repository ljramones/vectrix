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
import org.openjdk.jmh.annotations.TearDown;
import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Vector3f;

import java.util.SplittableRandom;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ParallelTransformBenchmark extends ThroughputBenchmark {
    @Param({"1024", "16384", "65536"})
    public int count;

    @Param({"256", "1024"})
    public int chunkSize;

    @Param({"2", "4", "8"})
    public int workers;

    @Param({"matrix", "packedAffine"})
    public String path;

    private Transformf[] transforms;
    private Matrix4f[] matrices;
    private PackedAffineArray packed;
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

    private ForkJoinPool pool;

    @Setup
    public void setup() {
        transforms = new Transformf[count];
        matrices = new Matrix4f[count];
        packed = new PackedAffineArray(count);

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

        SplittableRandom rnd = new SplittableRandom(5151L);
        for (int i = 0; i < count; i++) {
            Transformf t = new Transformf();
            t.translation.set((float) rnd.nextDouble(-100.0, 100.0), (float) rnd.nextDouble(-100.0, 100.0), (float) rnd.nextDouble(-100.0, 100.0));
            t.rotation.identity().rotateXYZ((float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0));
            t.scale.set((float) rnd.nextDouble(0.7, 2.0), (float) rnd.nextDouble(0.7, 2.0), (float) rnd.nextDouble(0.7, 2.0));
            transforms[i] = t;
            matrices[i] = new Matrix4f().translationRotateScale(t.translation, t.rotation, t.scale);

            float cx = (float) rnd.nextDouble(-10.0, 10.0);
            float cy = (float) rnd.nextDouble(-10.0, 10.0);
            float cz = (float) rnd.nextDouble(-10.0, 10.0);
            float ex = (float) rnd.nextDouble(0.2, 2.0);
            float ey = (float) rnd.nextDouble(0.2, 2.0);
            float ez = (float) rnd.nextDouble(0.2, 2.0);
            minX[i] = cx - ex;
            minY[i] = cy - ey;
            minZ[i] = cz - ez;
            maxX[i] = cx + ex;
            maxY[i] = cy + ey;
            maxZ[i] = cz + ez;

            float[] raw = packed.raw();
            int o = i * 12;
            raw[o] = matrices[i].m00();
            raw[o + 1] = matrices[i].m01();
            raw[o + 2] = matrices[i].m02();
            raw[o + 3] = matrices[i].m03();
            raw[o + 4] = matrices[i].m10();
            raw[o + 5] = matrices[i].m11();
            raw[o + 6] = matrices[i].m12();
            raw[o + 7] = matrices[i].m13();
            raw[o + 8] = matrices[i].m20();
            raw[o + 9] = matrices[i].m21();
            raw[o + 10] = matrices[i].m22();
            raw[o + 11] = matrices[i].m23();
        }

        pool = new ForkJoinPool(workers);
    }

    @TearDown
    public void teardown() {
        if (pool != null) {
            pool.shutdown();
        }
    }

    @Benchmark
    public float parallelTransformBatch() {
        int chunks = (count + chunkSize - 1) / chunkSize;
        pool.submit(() -> IntStream.range(0, chunks).parallel().forEach(chunk -> {
            int start = chunk * chunkSize;
            int end = java.lang.Math.min(start + chunkSize, count);
            if ("matrix".equals(path)) {
                Vector3f tmin = new Vector3f();
                Vector3f tmax = new Vector3f();
                for (int i = start; i < end; i++) {
                    matrices[i].transformAab(minX[i], minY[i], minZ[i], maxX[i], maxY[i], maxZ[i], tmin, tmax);
                    outMinX[i] = tmin.x;
                    outMinY[i] = tmin.y;
                    outMinZ[i] = tmin.z;
                    outMaxX[i] = tmax.x;
                    outMaxY[i] = tmax.y;
                    outMaxZ[i] = tmax.z;
                }
            } else {
                float[] raw = packed.raw();
                for (int i = start; i < end; i++) {
                    int o = i * 12;
                    float m00 = raw[o], m01 = raw[o + 1], m02 = raw[o + 2], tx = raw[o + 3];
                    float m10 = raw[o + 4], m11 = raw[o + 5], m12 = raw[o + 6], ty = raw[o + 7];
                    float m20 = raw[o + 8], m21 = raw[o + 9], m22 = raw[o + 10], tz = raw[o + 11];

                    float cX = (minX[i] + maxX[i]) * 0.5f;
                    float cY = (minY[i] + maxY[i]) * 0.5f;
                    float cZ = (minZ[i] + maxZ[i]) * 0.5f;
                    float eX = (maxX[i] - minX[i]) * 0.5f;
                    float eY = (maxY[i] - minY[i]) * 0.5f;
                    float eZ = (maxZ[i] - minZ[i]) * 0.5f;

                    float ncX = m00 * cX + m01 * cY + m02 * cZ + tx;
                    float ncY = m10 * cX + m11 * cY + m12 * cZ + ty;
                    float ncZ = m20 * cX + m21 * cY + m22 * cZ + tz;

                    float neX = java.lang.Math.abs(m00) * eX + java.lang.Math.abs(m01) * eY + java.lang.Math.abs(m02) * eZ;
                    float neY = java.lang.Math.abs(m10) * eX + java.lang.Math.abs(m11) * eY + java.lang.Math.abs(m12) * eZ;
                    float neZ = java.lang.Math.abs(m20) * eX + java.lang.Math.abs(m21) * eY + java.lang.Math.abs(m22) * eZ;

                    outMinX[i] = ncX - neX;
                    outMinY[i] = ncY - neY;
                    outMinZ[i] = ncZ - neZ;
                    outMaxX[i] = ncX + neX;
                    outMaxY[i] = ncY + neY;
                    outMaxZ[i] = ncZ + neZ;
                }
            }
        })).join();

        return outMaxX[count - 1] + outMaxY[count - 1] + outMaxZ[count - 1];
    }
}
