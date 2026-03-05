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
import org.vectrix.affine.Affine4f;
import org.vectrix.affine.Transformf;
import org.vectrix.core.Matrix4f;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class InstanceUploadBenchmark extends ThroughputBenchmark {
    @Param({"1", "4", "16", "64", "256", "1024", "4096", "16384"})
    public int instances;

    @Param({"uniform", "clustered"})
    public String dataDistribution;

    @Param({"hot", "cold"})
    public String accessPattern;

    private Transformf[] transforms;
    private int[] order;
    private float[] matrixUpload;
    private float[] packedAffineUpload;
    private final Matrix4f tmpMatrix = new Matrix4f();
    private final Affine4f tmpAffine = new Affine4f();

    @Setup
    public void setup() {
        transforms = new Transformf[instances];
        order = new int[instances];
        matrixUpload = new float[instances * 16];
        packedAffineUpload = new float[instances * 12];
        SplittableRandom rnd = new SplittableRandom(17L);
        for (int i = 0; i < instances; i++) {
            Transformf t = new Transformf();
            if ("clustered".equals(dataDistribution)) {
                t.translation.set((float) rnd.nextDouble(-25.0, 25.0), (float) rnd.nextDouble(-25.0, 25.0), (float) rnd.nextDouble(-25.0, 25.0));
            } else {
                t.translation.set((float) rnd.nextDouble(-250.0, 250.0), (float) rnd.nextDouble(-250.0, 250.0), (float) rnd.nextDouble(-250.0, 250.0));
            }
            t.rotation.identity().rotateXYZ((float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0));
            t.scale.set((float) rnd.nextDouble(0.5, 3.0), (float) rnd.nextDouble(0.5, 3.0), (float) rnd.nextDouble(0.5, 3.0));
            transforms[i] = t;
            order[i] = i;
        }
        if ("cold".equals(accessPattern)) {
            for (int i = instances - 1; i > 0; i--) {
                int j = rnd.nextInt(i + 1);
                int v = order[i];
                order[i] = order[j];
                order[j] = v;
            }
        }
    }

    @Benchmark
    public float[] instanceUploadMatrix4f() {
        for (int i = 0; i < instances; i++) {
            int idx = order[i];
            int base = idx << 4;
            Transformf t = transforms[idx];
            tmpMatrix.translationRotateScale(t.translation, t.rotation, t.scale);
            matrixUpload[base] = tmpMatrix.m00();
            matrixUpload[base + 1] = tmpMatrix.m01();
            matrixUpload[base + 2] = tmpMatrix.m02();
            matrixUpload[base + 3] = tmpMatrix.m03();
            matrixUpload[base + 4] = tmpMatrix.m10();
            matrixUpload[base + 5] = tmpMatrix.m11();
            matrixUpload[base + 6] = tmpMatrix.m12();
            matrixUpload[base + 7] = tmpMatrix.m13();
            matrixUpload[base + 8] = tmpMatrix.m20();
            matrixUpload[base + 9] = tmpMatrix.m21();
            matrixUpload[base + 10] = tmpMatrix.m22();
            matrixUpload[base + 11] = tmpMatrix.m23();
            matrixUpload[base + 12] = tmpMatrix.m30();
            matrixUpload[base + 13] = tmpMatrix.m31();
            matrixUpload[base + 14] = tmpMatrix.m32();
            matrixUpload[base + 15] = tmpMatrix.m33();
        }
        return matrixUpload;
    }

    @Benchmark
    public float[] instanceUploadPackedAffine() {
        for (int i = 0; i < instances; i++) {
            int idx = order[i];
            int base = idx * 12;
            Transformf t = transforms[idx];
            tmpAffine.translationRotateScale(t.translation, t.rotation, t.scale);
            packedAffineUpload[base] = tmpAffine.m00;
            packedAffineUpload[base + 1] = tmpAffine.m01;
            packedAffineUpload[base + 2] = tmpAffine.m02;
            packedAffineUpload[base + 3] = tmpAffine.m10;
            packedAffineUpload[base + 4] = tmpAffine.m11;
            packedAffineUpload[base + 5] = tmpAffine.m12;
            packedAffineUpload[base + 6] = tmpAffine.m20;
            packedAffineUpload[base + 7] = tmpAffine.m21;
            packedAffineUpload[base + 8] = tmpAffine.m22;
            packedAffineUpload[base + 9] = tmpAffine.m30;
            packedAffineUpload[base + 10] = tmpAffine.m31;
            packedAffineUpload[base + 11] = tmpAffine.m32;
        }
        return packedAffineUpload;
    }
}
