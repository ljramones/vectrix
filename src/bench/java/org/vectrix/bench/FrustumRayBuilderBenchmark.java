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
import org.vectrix.core.Vector3f;
import org.vectrix.geometry.FrustumRayBuilder;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FrustumRayBuilderBenchmark extends ThroughputBenchmark {
    @Param({"64", "1024", "16384"})
    public int count;

    @Param({"sequentialGrid", "randomSamples"})
    public String samplePattern;

    @Param({"8", "32", "128"})
    public int gridSize;

    private FrustumRayBuilder rayBuilder;
    private float[] u;
    private float[] v;
    private int[] order;
    private final Vector3f origin = new Vector3f();
    private final Vector3f dir = new Vector3f();

    @Setup
    public void setup() {
        Matrix4f pv = new Matrix4f()
                .setPerspective((float) java.lang.Math.toRadians(60.0), 16.0f / 9.0f, 0.1f, 500.0f)
                .lookAt(0.0f, 2.0f, 8.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        rayBuilder = new FrustumRayBuilder(pv);

        u = new float[count];
        v = new float[count];
        order = new int[count];

        SplittableRandom rnd = new SplittableRandom(3003L);
        int side = java.lang.Math.max(1, (int) java.lang.Math.sqrt(count));
        for (int i = 0; i < count; i++) {
            if ("randomSamples".equals(samplePattern)) {
                u[i] = rnd.nextFloat();
                v[i] = rnd.nextFloat();
            } else {
                int x = i % side;
                int y = i / side;
                u[i] = side > 1 ? (float) x / (side - 1) : 0.5f;
                v[i] = side > 1 ? (float) y / (side - 1) : 0.5f;
            }
            order[i] = i;
        }
        if ("randomSamples".equals(samplePattern)) {
            shuffle(order, rnd.split());
        }
    }

    @Benchmark
    public float singleRayGenerationBatch() {
        rayBuilder.origin(origin);
        float sum = origin.x + origin.y + origin.z;
        for (int i = 0; i < count; i++) {
            int idx = order[i];
            rayBuilder.dir(u[idx], v[idx], dir);
            sum += dir.x + dir.y + dir.z;
        }
        return sum;
    }

    @Benchmark
    public float gridRayGenerationBatch() {
        rayBuilder.origin(origin);
        int w = gridSize;
        int h = gridSize;
        float sum = origin.x + origin.y + origin.z;
        int sampleStep = "randomSamples".equals(samplePattern) ? 3 : 1;
        for (int y = 0; y < h; y += sampleStep) {
            for (int x = 0; x < w; x += sampleStep) {
                float fu = w > 1 ? (float) x / (w - 1) : 0.5f;
                float fv = h > 1 ? (float) y / (h - 1) : 0.5f;
                rayBuilder.dir(fu, fv, dir);
                sum += dir.x + dir.y + dir.z;
            }
        }
        return sum;
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
