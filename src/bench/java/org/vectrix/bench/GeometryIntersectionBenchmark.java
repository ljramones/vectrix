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
import org.vectrix.geometry.Intersectiond;
import org.vectrix.geometry.Intersectionf;
import org.vectrix.geometry.PolygonsIntersection;
import org.vectrix.geometry.RayAabIntersection;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class GeometryIntersectionBenchmark extends ThroughputBenchmark {
    @Param({"64", "1024", "16384"})
    public int count;

    @Param({"hitHeavy", "missHeavy", "mixed"})
    public String distribution;

    @Param({"sequential", "random"})
    public String accessPattern;

    @Param({"float", "double"})
    public String precision;

    @Param({"4", "16", "64"})
    public int verts;

    private float[] ox;
    private float[] oy;
    private float[] oz;
    private float[] dx;
    private float[] dy;
    private float[] dz;

    private float[] minX;
    private float[] minY;
    private float[] minZ;
    private float[] maxX;
    private float[] maxY;
    private float[] maxZ;

    private float[] t0x;
    private float[] t0y;
    private float[] t0z;
    private float[] t1x;
    private float[] t1y;
    private float[] t1z;
    private float[] t2x;
    private float[] t2y;
    private float[] t2z;

    private RayAabIntersection[] slopeIntersectors;
    private int[] order;

    private float[] polygonVerts;
    private PolygonsIntersection polygon;
    private float[] px;
    private float[] py;

    @Setup
    public void setup() {
        ox = new float[count];
        oy = new float[count];
        oz = new float[count];
        dx = new float[count];
        dy = new float[count];
        dz = new float[count];

        minX = new float[count];
        minY = new float[count];
        minZ = new float[count];
        maxX = new float[count];
        maxY = new float[count];
        maxZ = new float[count];

        t0x = new float[count];
        t0y = new float[count];
        t0z = new float[count];
        t1x = new float[count];
        t1y = new float[count];
        t1z = new float[count];
        t2x = new float[count];
        t2y = new float[count];
        t2z = new float[count];

        slopeIntersectors = new RayAabIntersection[count];
        order = new int[count];
        px = new float[count];
        py = new float[count];

        SplittableRandom rnd = new SplittableRandom(4401L);
        for (int i = 0; i < count; i++) {
            float cx = (float) rnd.nextDouble(-50.0, 50.0);
            float cy = (float) rnd.nextDouble(-50.0, 50.0);
            float cz = (float) rnd.nextDouble(-50.0, 50.0);
            float ex = (float) rnd.nextDouble(0.4, 3.2);
            float ey = (float) rnd.nextDouble(0.4, 3.2);
            float ez = (float) rnd.nextDouble(0.4, 3.2);
            minX[i] = cx - ex;
            minY[i] = cy - ey;
            minZ[i] = cz - ez;
            maxX[i] = cx + ex;
            maxY[i] = cy + ey;
            maxZ[i] = cz + ez;

            t0x[i] = cx - ex;
            t0y[i] = cy - ey;
            t0z[i] = cz;
            t1x[i] = cx + ex;
            t1y[i] = cy - ey;
            t1z[i] = cz;
            t2x[i] = cx;
            t2y[i] = cy + ey;
            t2z[i] = cz + ez;

            if ("hitHeavy".equals(distribution) || ("mixed".equals(distribution) && (i & 1) == 0)) {
                ox[i] = cx + 10.0f;
                oy[i] = cy;
                oz[i] = cz;
                dx[i] = -1.0f;
                dy[i] = 0.0f;
                dz[i] = 0.0f;
            } else {
                ox[i] = cx + 10.0f;
                oy[i] = cy + 10.0f;
                oz[i] = cz + 10.0f;
                dx[i] = 1.0f;
                dy[i] = 0.7f;
                dz[i] = 0.3f;
            }

            slopeIntersectors[i] = new RayAabIntersection(ox[i], oy[i], oz[i], dx[i], dy[i], dz[i]);
            order[i] = i;

            px[i] = (float) rnd.nextDouble(-1.5, 1.5);
            py[i] = (float) rnd.nextDouble(-1.5, 1.5);
        }
        if ("random".equals(accessPattern)) {
            shuffle(order, rnd.split());
        }

        polygonVerts = new float[verts * 2];
        for (int i = 0; i < verts; i++) {
            double a = (i * 2.0 * java.lang.Math.PI) / verts;
            polygonVerts[i * 2] = (float) java.lang.Math.cos(a);
            polygonVerts[i * 2 + 1] = (float) java.lang.Math.sin(a);
        }
        polygon = new PolygonsIntersection(polygonVerts, null, verts);
    }

    @Benchmark
    public int rayAabBatch() {
        int hits = 0;
        for (int i = 0; i < count; i++) {
            int idx = order[i];
            boolean hit;
            if ("double".equals(precision)) {
                hit = Intersectiond.testRayAab(ox[idx], oy[idx], oz[idx], dx[idx], dy[idx], dz[idx], minX[idx], minY[idx], minZ[idx], maxX[idx], maxY[idx], maxZ[idx]);
            } else {
                hit = Intersectionf.testRayAab(ox[idx], oy[idx], oz[idx], dx[idx], dy[idx], dz[idx], minX[idx], minY[idx], minZ[idx], maxX[idx], maxY[idx], maxZ[idx]);
            }
            hits += hit ? 1 : 0;
        }
        return hits;
    }

    @Benchmark
    public int rayTriangleBatch() {
        int hits = 0;
        for (int i = 0; i < count; i++) {
            int idx = order[i];
            boolean hit;
            if ("double".equals(precision)) {
                hit = Intersectiond.testRayTriangle(
                        ox[idx], oy[idx], oz[idx],
                        dx[idx], dy[idx], dz[idx],
                        t0x[idx], t0y[idx], t0z[idx],
                        t1x[idx], t1y[idx], t1z[idx],
                        t2x[idx], t2y[idx], t2z[idx],
                        1E-6);
            } else {
                hit = Intersectionf.testRayTriangle(
                        ox[idx], oy[idx], oz[idx],
                        dx[idx], dy[idx], dz[idx],
                        t0x[idx], t0y[idx], t0z[idx],
                        t1x[idx], t1y[idx], t1z[idx],
                        t2x[idx], t2y[idx], t2z[idx],
                        1E-6f);
            }
            hits += hit ? 1 : 0;
        }
        return hits;
    }

    @Benchmark
    public int rayAabSlopeBatch() {
        int hits = 0;
        for (int i = 0; i < count; i++) {
            int idx = order[i];
            boolean hit = slopeIntersectors[idx].test(minX[idx], minY[idx], minZ[idx], maxX[idx], maxY[idx], maxZ[idx]);
            hits += hit ? 1 : 0;
        }
        return hits;
    }

    @Benchmark
    public int polygonPointBatch() {
        int inside = 0;
        for (int i = 0; i < count; i++) {
            int idx = order[i];
            inside += polygon.testPoint(px[idx], py[idx]) ? 1 : 0;
        }
        return inside;
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
