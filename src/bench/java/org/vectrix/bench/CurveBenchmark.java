/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.bench;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.vectrix.core.Vector3f;
import org.vectrix.curve.CurveReparameterizer3f;
import org.vectrix.curve.vec3.Bezier3f;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class CurveBenchmark {
    @Param({"256", "4096", "16384"})
    public int count;

    private Vector3f p0, p1, p2, p3;
    private float[] tValues;
    private Vector3f tmp;
    private float[] arcTable;

    @Setup
    public void setup() {
        SplittableRandom rnd = new SplittableRandom(13);
        p0 = new Vector3f((float) rnd.nextDouble(), (float) rnd.nextDouble(), (float) rnd.nextDouble());
        p1 = new Vector3f((float) rnd.nextDouble(), (float) rnd.nextDouble(), (float) rnd.nextDouble());
        p2 = new Vector3f((float) rnd.nextDouble(), (float) rnd.nextDouble(), (float) rnd.nextDouble());
        p3 = new Vector3f((float) rnd.nextDouble(), (float) rnd.nextDouble(), (float) rnd.nextDouble());
        tValues = new float[count];
        for (int i = 0; i < count; i++) {
            tValues[i] = (float) i / (float) (count - 1);
        }
        tmp = new Vector3f();
        arcTable = CurveReparameterizer3f.buildArcLengthTableForBezier(p0, p1, p2, p3, 128, new float[129]);
    }

    @Benchmark
    public Vector3f bezierEvaluateLoop() {
        for (int i = 0; i < count; i++) {
            Bezier3f.evaluate(p0, p1, p2, p3, tValues[i], tmp);
        }
        return tmp;
    }

    @Benchmark
    public float mapArcLengthLoop() {
        float v = 0.0f;
        for (int i = 0; i < count; i++) {
            v += CurveReparameterizer3f.mapArcLengthToT(tValues[i], arcTable);
        }
        return v;
    }
}
