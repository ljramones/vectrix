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
import org.vectrix.affine.TransformKernels;
import org.vectrix.affine.Transformf;
import org.vectrix.soa.TransformSoA;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class TransformComposeBenchmark {
    @Param({"64", "256", "4096"})
    public int size;

    private Transformf[] parentsObj;
    private Transformf[] localsObj;
    private Transformf[] outObj;
    private TransformSoA parentsSoA;
    private TransformSoA localsSoA;
    private TransformSoA outSoA;

    @Setup
    public void setup() {
        parentsObj = new Transformf[size];
        localsObj = new Transformf[size];
        outObj = new Transformf[size];
        parentsSoA = new TransformSoA(size);
        localsSoA = new TransformSoA(size);
        outSoA = new TransformSoA(size);
        SplittableRandom rnd = new SplittableRandom(42L);
        for (int i = 0; i < size; i++) {
            Transformf p = randomTransform(rnd);
            Transformf l = randomTransform(rnd);
            parentsObj[i] = p;
            localsObj[i] = l;
            outObj[i] = new Transformf();
            parentsSoA.set(i, p);
            localsSoA.set(i, l);
        }
    }

    @Benchmark
    public Transformf[] composeObjectBatch() {
        for (int i = 0; i < size; i++) {
            Transformf.compose(parentsObj[i], localsObj[i], outObj[i]);
        }
        return outObj;
    }

    @Benchmark
    public TransformSoA composeSoABatch() {
        TransformKernels.composeBatch(parentsSoA, localsSoA, outSoA, size);
        return outSoA;
    }

    private static Transformf randomTransform(SplittableRandom rnd) {
        Transformf t = new Transformf();
        float tx = (float) (rnd.nextDouble() * 20.0 - 10.0);
        float ty = (float) (rnd.nextDouble() * 20.0 - 10.0);
        float tz = (float) (rnd.nextDouble() * 20.0 - 10.0);
        float ax = (float) (rnd.nextDouble() * 2.0 - 1.0);
        float ay = (float) (rnd.nextDouble() * 2.0 - 1.0);
        float az = (float) (rnd.nextDouble() * 2.0 - 1.0);
        float angle = (float) (rnd.nextDouble() * java.lang.Math.PI * 2.0);
        float sx = 0.5f + (float) rnd.nextDouble() * 2.0f;
        float sy = 0.5f + (float) rnd.nextDouble() * 2.0f;
        float sz = 0.5f + (float) rnd.nextDouble() * 2.0f;
        t.translation.set(tx, ty, tz);
        t.rotation.identity().rotateAxis(angle, ax, ay, az).normalize();
        t.scale.set(sx, sy, sz);
        return t;
    }
}
