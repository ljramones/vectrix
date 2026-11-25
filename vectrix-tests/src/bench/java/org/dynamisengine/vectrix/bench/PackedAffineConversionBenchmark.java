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
import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.affine.PackedAffineKernels;
import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Quaternionf;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class PackedAffineConversionBenchmark extends ThroughputBenchmark {
    @Param({"1", "4", "16", "64", "256", "1024", "4096", "16384"})
    public int size;

    private Quaternionf[] rotations;
    private float[] tx;
    private float[] ty;
    private float[] tz;
    private Transformf[] transforms;
    private Matrix4f[] matrices;
    private PackedAffineArray out;

    @Setup
    public void setup() {
        rotations = new Quaternionf[size];
        tx = new float[size];
        ty = new float[size];
        tz = new float[size];
        transforms = new Transformf[size];
        matrices = new Matrix4f[size];
        out = new PackedAffineArray(size);

        SplittableRandom rnd = new SplittableRandom(1234L);
        for (int i = 0; i < size; i++) {
            float px = (float) rnd.nextDouble(-120.0, 120.0);
            float py = (float) rnd.nextDouble(-120.0, 120.0);
            float pz = (float) rnd.nextDouble(-120.0, 120.0);
            Quaternionf q = new Quaternionf().rotationXYZ(
                    (float) rnd.nextDouble(-1.8, 1.8),
                    (float) rnd.nextDouble(-1.8, 1.8),
                    (float) rnd.nextDouble(-1.8, 1.8));
            float sx = (float) rnd.nextDouble(0.5, 3.0);
            float sy = (float) rnd.nextDouble(0.5, 3.0);
            float sz = (float) rnd.nextDouble(0.5, 3.0);

            rotations[i] = new Quaternionf(q);
            tx[i] = px;
            ty[i] = py;
            tz[i] = pz;

            Transformf t = new Transformf();
            t.translation.set(px, py, pz);
            t.rotation.set(q);
            t.scale.set(sx, sy, sz);
            transforms[i] = t;

            matrices[i] = new Matrix4f().translationRotateScale(px, py, pz, q.x, q.y, q.z, q.w, sx, sy, sz);
        }
    }

    @Benchmark
    public float[] quatTranslationToPackedAffineBatch() {
        PackedAffineKernels.quatTranslationToPackedAffineBatch(rotations, tx, ty, tz, out, size);
        return out.raw();
    }

    @Benchmark
    public float[] trsToPackedAffineBatch() {
        PackedAffineKernels.trsToPackedAffineBatch(transforms, out, size);
        return out.raw();
    }

    @Benchmark
    public float[] matrix4fToPackedAffineBatch() {
        PackedAffineKernels.matrix4fToPackedAffineBatch(matrices, out, size);
        return out.raw();
    }
}
