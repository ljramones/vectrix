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
import org.vectrix.simd.SimdSupport;
import org.vectrix.simd.Vector4fa;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SimdSupportBenchmark extends ThroughputBenchmark {
    @Param({"1", "4", "16", "64", "1024", "16384"})
    public int count;

    @Param({"natural", "offset"})
    public String alignment;

    @Param({"sequential", "random"})
    public String accessPattern;

    private Vector4fa[] simdA;
    private Vector4fa[] simdB;
    private Vector4fa[] simdDst;

    private float[] sx;
    private float[] sy;
    private float[] sz;
    private float[] sw;
    private float[] tx;
    private float[] ty;
    private float[] tz;
    private float[] tw;
    private float[] ox;
    private float[] oy;
    private float[] oz;
    private float[] ow;

    private int[] order;
    private int alignOffset;

    @Setup
    public void setup() {
        int n = count + 1;
        simdA = new Vector4fa[n];
        simdB = new Vector4fa[n];
        simdDst = new Vector4fa[n];

        sx = new float[n];
        sy = new float[n];
        sz = new float[n];
        sw = new float[n];
        tx = new float[n];
        ty = new float[n];
        tz = new float[n];
        tw = new float[n];
        ox = new float[n];
        oy = new float[n];
        oz = new float[n];
        ow = new float[n];

        order = new int[count];
        alignOffset = "offset".equals(alignment) ? 1 : 0;

        SplittableRandom rnd = new SplittableRandom(9922L);
        for (int i = 0; i < n; i++) {
            float ax = (float) rnd.nextDouble(-2.0, 2.0);
            float ay = (float) rnd.nextDouble(-2.0, 2.0);
            float az = (float) rnd.nextDouble(-2.0, 2.0);
            float awv = (float) rnd.nextDouble(-2.0, 2.0);
            float bx = (float) rnd.nextDouble(-2.0, 2.0);
            float by = (float) rnd.nextDouble(-2.0, 2.0);
            float bz = (float) rnd.nextDouble(-2.0, 2.0);
            float bw = (float) rnd.nextDouble(-2.0, 2.0);

            simdA[i] = new Vector4fa(ax, ay, az, awv);
            simdB[i] = new Vector4fa(bx, by, bz, bw);
            simdDst[i] = new Vector4fa(ax, ay, az, awv);

            sx[i] = ax;
            sy[i] = ay;
            sz[i] = az;
            sw[i] = awv;
            tx[i] = bx;
            ty[i] = by;
            tz[i] = bz;
            tw[i] = bw;
        }
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        if ("random".equals(accessPattern)) {
            shuffle(order, rnd.split());
        }
    }

    @Benchmark
    public int vector4faAddBatch() {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            int idx = alignOffset + order[i];
            simdDst[idx].add(simdA[idx]).add(simdB[idx]);
            sum ^= simdDst[idx].hashCode();
        }
        return sum;
    }

    @Benchmark
    public float scalarAddBatch() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            int idx = alignOffset + order[i];
            ox[idx] = sx[idx] + tx[idx];
            oy[idx] = sy[idx] + ty[idx];
            oz[idx] = sz[idx] + tz[idx];
            ow[idx] = sw[idx] + tw[idx];
            sum += ox[idx] + oy[idx] + oz[idx] + ow[idx];
        }
        return sum;
    }

    @Benchmark
    public int simdCapabilityProbeBatch() {
        int score = 0;
        for (int i = 0; i < count; i++) {
            score += SimdSupport.backend() == SimdSupport.Backend.VECTOR_API ? 3 : 1;
            score += SimdSupport.preferredFloatLanes();
            score += SimdSupport.isVectorApiAvailable() ? 5 : 2;
        }
        return score;
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
