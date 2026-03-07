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
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.sdf.Sdf3f;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SdfBenchmark extends ThroughputBenchmark {
    @Param({"64", "1024", "16384"})
    public int count;

    @Param({"sphere", "box", "capsule", "cylinder", "torus"})
    public String shapeType;

    @Param({"1", "2", "4"})
    public int compositionDepth;

    private float[] px;
    private float[] py;
    private float[] pz;
    private Vector3f halfExtents;
    private Vector3f segA;
    private Vector3f segB;

    @Setup
    public void setup() {
        px = new float[count];
        py = new float[count];
        pz = new float[count];
        SplittableRandom rnd = new SplittableRandom(8080L);
        for (int i = 0; i < count; i++) {
            px[i] = (float) rnd.nextDouble(-6.0, 6.0);
            py[i] = (float) rnd.nextDouble(-6.0, 6.0);
            pz[i] = (float) rnd.nextDouble(-6.0, 6.0);
        }
        halfExtents = new Vector3f(1.5f, 1.0f, 0.75f);
        segA = new Vector3f(-0.5f, -1.0f, 0.0f);
        segB = new Vector3f(0.5f, 1.0f, 0.0f);
    }

    @Benchmark
    public float sdfEvaluateBatch() {
        float sum = 0.0f;
        Vector3f p = new Vector3f();
        for (int i = 0; i < count; i++) {
            float d = Float.POSITIVE_INFINITY;
            for (int c = 0; c < compositionDepth; c++) {
                p.set(px[i] + c * 0.25f, py[i] - c * 0.2f, pz[i] + c * 0.15f);
                float v;
                switch (shapeType) {
                    case "sphere":
                        v = Sdf3f.sphere(p, 1.25f);
                        break;
                    case "box":
                        v = Sdf3f.box(p, halfExtents);
                        break;
                    case "capsule":
                        v = Sdf3f.capsule(p, segA, segB, 0.65f);
                        break;
                    case "cylinder":
                        v = Sdf3f.cylinderY(p, 0.9f, 1.4f);
                        break;
                    case "torus":
                    default:
                        v = Sdf3f.torusY(p, 1.2f, 0.35f);
                        break;
                }
                d = java.lang.Math.min(d, v);
            }
            sum += d;
        }
        return sum;
    }
}
