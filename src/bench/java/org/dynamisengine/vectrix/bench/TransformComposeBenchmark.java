/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.dynamisengine.vectrix.bench;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.dynamisengine.vectrix.affine.Affine4f;
import org.dynamisengine.vectrix.affine.TransformKernels;
import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.soa.TransformSoA;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class TransformComposeBenchmark extends ThroughputBenchmark {
    @Param({"1", "4", "16", "64", "256", "1024", "4096", "16384"})
    public int size;

    private Transformf[] parentsObj;
    private Transformf[] localsObj;
    private Transformf[] outObj;
    private Matrix4f[] parentsMatrix;
    private Matrix4f[] localsMatrix;
    private Matrix4f[] outMatrix;
    private Affine4f[] parentsAffine;
    private Affine4f[] localsAffine;
    private Affine4f[] outAffine;
    private TransformSoA parentsSoA;
    private TransformSoA localsSoA;
    private TransformSoA outSoA;

    @Setup
    public void setup() {
        parentsObj = new Transformf[size];
        localsObj = new Transformf[size];
        outObj = new Transformf[size];
        parentsMatrix = new Matrix4f[size];
        localsMatrix = new Matrix4f[size];
        outMatrix = new Matrix4f[size];
        parentsAffine = new Affine4f[size];
        localsAffine = new Affine4f[size];
        outAffine = new Affine4f[size];
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
            parentsMatrix[i] = new Matrix4f().translationRotateScale(p.translation, p.rotation, p.scale);
            localsMatrix[i] = new Matrix4f().translationRotateScale(l.translation, l.rotation, l.scale);
            outMatrix[i] = new Matrix4f();
            parentsAffine[i] = new Affine4f().translationRotateScale(p.translation, p.rotation, p.scale);
            localsAffine[i] = new Affine4f().translationRotateScale(l.translation, l.rotation, l.scale);
            outAffine[i] = new Affine4f();
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

    @Benchmark
    public Matrix4f[] composeMatrixBatch() {
        for (int i = 0; i < size; i++) {
            parentsMatrix[i].mul(localsMatrix[i], outMatrix[i]);
        }
        return outMatrix;
    }

    @Benchmark
    public Affine4f[] composeAffineBatch() {
        for (int i = 0; i < size; i++) {
            parentsAffine[i].mul(localsAffine[i], outAffine[i]);
        }
        return outAffine;
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
