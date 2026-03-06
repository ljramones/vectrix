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
import org.vectrix.core.Vector3d;
import org.vectrix.core.Vector3f;
import org.vectrix.renderingmath.AtmosphereParams;
import org.vectrix.renderingmath.BentNormalConed;
import org.vectrix.renderingmath.BentNormalConef;
import org.vectrix.renderingmath.Interpolationd;
import org.vectrix.renderingmath.Interpolationf;
import org.vectrix.renderingmath.SssLutBuilder;
import org.vectrix.renderingmath.SssProfile;
import org.vectrix.renderingmath.TransmittanceLutBuilder;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class RenderingMathBenchmark extends ThroughputBenchmark {
    @Param({"64", "1024", "16384"})
    public int count;

    @Param({"float", "double"})
    public String precision;

    @Param({"scalarBatch", "arrayBatch"})
    public String mode;

    @Param({"uniform", "clustered"})
    public String distribution;

    @Param({"sequential", "random"})
    public String accessPattern;

    @Param({"32", "64", "128", "256"})
    public int resolution;

    @Param({"low", "medium", "high"})
    public String quality;

    private float[] tx;
    private float[] ty;
    private float[] samples4x4f;
    private double[] samples4x4d;

    private float[] ao;
    private Vector3f[] axisAf;
    private Vector3f[] axisBf;
    private Vector3d[] axisAd;
    private Vector3d[] axisBd;
    private int[] order;

    private SssProfile sssProfile;
    private AtmosphereParams atmosphere;
    private float[] lutRgb;

    @Setup
    public void setup() {
        tx = new float[count];
        ty = new float[count];
        samples4x4f = new float[count * 16];
        samples4x4d = new double[count * 16];

        ao = new float[count];
        axisAf = new Vector3f[count];
        axisBf = new Vector3f[count];
        axisAd = new Vector3d[count];
        axisBd = new Vector3d[count];
        order = new int[count];

        SplittableRandom rnd = new SplittableRandom(7341L);
        for (int i = 0; i < count; i++) {
            tx[i] = (float) rnd.nextDouble(0.0, 1.0);
            ty[i] = (float) rnd.nextDouble(0.0, 1.0);
            for (int j = 0; j < 16; j++) {
                float v = (float) rnd.nextDouble(-2.0, 2.0);
                samples4x4f[i * 16 + j] = v;
                samples4x4d[i * 16 + j] = v;
            }
            ao[i] = "clustered".equals(distribution)
                    ? (float) rnd.nextDouble(0.4, 0.8)
                    : (float) rnd.nextDouble(0.0, 1.0);

            axisAf[i] = randomUnitVec3f(rnd);
            axisBf[i] = randomUnitVec3f(rnd);
            axisAd[i] = new Vector3d(axisAf[i].x, axisAf[i].y, axisAf[i].z);
            axisBd[i] = new Vector3d(axisBf[i].x, axisBf[i].y, axisBf[i].z);
            order[i] = i;
        }
        if ("random".equals(accessPattern)) {
            shuffle(order, rnd.split());
        }

        sssProfile = new SssProfile(
                new float[] {0.24f, 0.31f, 0.45f, 0.12f, 0.14f, 0.18f},
                new float[] {0.0064f, 0.0484f, 0.187f, 0.35f, 0.35f, 0.35f});
        atmosphere = new AtmosphereParams(
                6360_000.0f,
                6460_000.0f,
                8000.0f,
                1200.0f,
                5.8e-6f,
                13.5e-6f,
                33.1e-6f,
                2.0e-5f,
                2.0e-5f,
                2.0e-5f);
        lutRgb = new float[resolution * resolution * 3];
    }

    @Benchmark
    public double interpolationBatch() {
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            int idx = order[i];
            if ("double".equals(precision)) {
                int o = idx * 16;
                if ("arrayBatch".equals(mode)) {
                    sum += Interpolationd.bicubicHermite(samples4x4d, tx[idx], ty[idx]);
                } else {
                    sum += Interpolationd.bilinear(samples4x4d[o], samples4x4d[o + 1], samples4x4d[o + 4], samples4x4d[o + 5], tx[idx], ty[idx]);
                    sum += Interpolationd.cubicHermite(samples4x4d[o], samples4x4d[o + 1], samples4x4d[o + 2], samples4x4d[o + 3], tx[idx]);
                }
            } else {
                int o = idx * 16;
                if ("arrayBatch".equals(mode)) {
                    sum += Interpolationf.bicubicHermite(samples4x4f, tx[idx], ty[idx]);
                } else {
                    sum += Interpolationf.bilinear(samples4x4f[o], samples4x4f[o + 1], samples4x4f[o + 4], samples4x4f[o + 5], tx[idx], ty[idx]);
                    sum += Interpolationf.cubicHermite(samples4x4f[o], samples4x4f[o + 1], samples4x4f[o + 2], samples4x4f[o + 3], tx[idx]);
                }
            }
        }
        return sum;
    }

    @Benchmark
    public double bentNormalConeBatch() {
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            int idx = order[i];
            float halfA = BentNormalConef.coneAngleFromAo(ao[idx]);
            if ("double".equals(precision)) {
                double halfB = BentNormalConed.coneAngleFromAo(ao[idx] * 0.8 + 0.1);
                sum += BentNormalConed.estimateIntersectionSolidAngle(halfA, axisAd[idx], halfB, axisBd[idx]);
            } else {
                float halfB = BentNormalConef.coneAngleFromAo(ao[idx] * 0.8f + 0.1f);
                sum += BentNormalConef.estimateIntersectionSolidAngle(halfA, axisAf[idx], halfB, axisBf[idx]);
            }
        }
        return sum;
    }

    @Benchmark
    public float buildSssLut() {
        ensureLutCapacity();
        int samples = sampleCountForQuality();
        SssLutBuilder.build(resolution, resolution, sssProfile, 2.5f, 12.0f, samples, lutRgb);
        return lutRgb[0] + lutRgb[lutRgb.length - 1];
    }

    @Benchmark
    public float buildTransmittanceLut() {
        ensureLutCapacity();
        int samples = sampleCountForQuality() * 2;
        TransmittanceLutBuilder.build(resolution, resolution, atmosphere, samples, lutRgb);
        return lutRgb[0] + lutRgb[lutRgb.length - 1];
    }

    private int sampleCountForQuality() {
        if ("high".equals(quality)) {
            return 64;
        }
        if ("medium".equals(quality)) {
            return 32;
        }
        return 16;
    }

    private void ensureLutCapacity() {
        int need = resolution * resolution * 3;
        if (lutRgb.length != need) {
            lutRgb = new float[need];
        }
    }

    private static Vector3f randomUnitVec3f(SplittableRandom rnd) {
        float x = (float) rnd.nextDouble(-1.0, 1.0);
        float y = (float) rnd.nextDouble(-1.0, 1.0);
        float z = (float) rnd.nextDouble(-1.0, 1.0);
        float inv = 1.0f / (float) java.lang.Math.sqrt(x * x + y * y + z * z + 1E-8f);
        return new Vector3f(x * inv, y * inv, z * inv);
    }

    private static void shuffle(int[] a, SplittableRandom rnd) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }
}
