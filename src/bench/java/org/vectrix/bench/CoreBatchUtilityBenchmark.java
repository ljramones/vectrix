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
import org.vectrix.core.IntegratorRK4d;
import org.vectrix.core.IntegratorRK4f;
import org.vectrix.core.OdeDerivatived;
import org.vectrix.core.OdeDerivativef;
import org.vectrix.core.Quaterniond;
import org.vectrix.core.Quaternionf;
import org.vectrix.core.Vector3d;
import org.vectrix.core.Vector3f;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class CoreBatchUtilityBenchmark extends ThroughputBenchmark {
    @Param({"64", "1024", "16384"})
    public int count;

    @Param({"float", "double"})
    public String precision;

    @Param({"uniform", "identityHeavy"})
    public String distribution;

    private Quaternionf[] qa;
    private Quaternionf[] qb;
    private Quaternionf[] qOut;
    private Vector3f[] vf;
    private Vector3f[] vfOut;

    private Quaterniond[] qad;
    private Quaterniond[] qbd;
    private Quaterniond[] qOutd;
    private Vector3d[] vd;
    private Vector3d[] vdOut;

    private float[] rkStateF;
    private float[] rkDestF;
    private float[] rkScratchF;

    private double[] rkStateD;
    private double[] rkDestD;
    private double[] rkScratchD;

    private static final OdeDerivativef RK_DERIV_F = (t, state, stateOffset, dest, destOffset) -> {
        float x = state[stateOffset];
        float v = state[stateOffset + 1];
        dest[destOffset] = v;
        dest[destOffset + 1] = -x;
    };

    private static final OdeDerivatived RK_DERIV_D = (t, state, stateOffset, dest, destOffset) -> {
        double x = state[stateOffset];
        double v = state[stateOffset + 1];
        dest[destOffset] = v;
        dest[destOffset + 1] = -x;
    };

    @Setup
    public void setup() {
        qa = new Quaternionf[count];
        qb = new Quaternionf[count];
        qOut = new Quaternionf[count];
        vf = new Vector3f[count];
        vfOut = new Vector3f[count];

        qad = new Quaterniond[count];
        qbd = new Quaterniond[count];
        qOutd = new Quaterniond[count];
        vd = new Vector3d[count];
        vdOut = new Vector3d[count];

        rkStateF = new float[count * 2];
        rkDestF = new float[count * 2];
        rkScratchF = new float[count * 10];

        rkStateD = new double[count * 2];
        rkDestD = new double[count * 2];
        rkScratchD = new double[count * 10];

        SplittableRandom rnd = new SplittableRandom(5011L);
        for (int i = 0; i < count; i++) {
            Quaternionf a = new Quaternionf().rotateXYZ(
                    (float) rnd.nextDouble(-1.2, 1.2),
                    (float) rnd.nextDouble(-1.2, 1.2),
                    (float) rnd.nextDouble(-1.2, 1.2));
            Quaternionf b = new Quaternionf().rotateXYZ(
                    (float) rnd.nextDouble(-1.2, 1.2),
                    (float) rnd.nextDouble(-1.2, 1.2),
                    (float) rnd.nextDouble(-1.2, 1.2));
            if ("identityHeavy".equals(distribution) && (i & 3) == 0) {
                a.identity();
            }
            qa[i] = a;
            qb[i] = b;
            qOut[i] = new Quaternionf();
            vf[i] = new Vector3f((float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0));
            vfOut[i] = new Vector3f();

            Quaterniond ad = new Quaterniond().rotateXYZ(
                    rnd.nextDouble(-1.2, 1.2),
                    rnd.nextDouble(-1.2, 1.2),
                    rnd.nextDouble(-1.2, 1.2));
            Quaterniond bd = new Quaterniond().rotateXYZ(
                    rnd.nextDouble(-1.2, 1.2),
                    rnd.nextDouble(-1.2, 1.2),
                    rnd.nextDouble(-1.2, 1.2));
            if ("identityHeavy".equals(distribution) && (i & 3) == 0) {
                ad.identity();
            }
            qad[i] = ad;
            qbd[i] = bd;
            qOutd[i] = new Quaterniond();
            vd[i] = new Vector3d(rnd.nextDouble(-2.0, 2.0), rnd.nextDouble(-2.0, 2.0), rnd.nextDouble(-2.0, 2.0));
            vdOut[i] = new Vector3d();

            rkStateF[i * 2] = (float) rnd.nextDouble(-1.0, 1.0);
            rkStateF[i * 2 + 1] = (float) rnd.nextDouble(-1.0, 1.0);
            rkStateD[i * 2] = rnd.nextDouble(-1.0, 1.0);
            rkStateD[i * 2 + 1] = rnd.nextDouble(-1.0, 1.0);
        }
    }

    @Benchmark
    public double quaternionComposeBatch() {
        double sum = 0.0;
        if ("double".equals(precision)) {
            for (int i = 0; i < count; i++) {
                qad[i].mul(qbd[i], qOutd[i]);
                sum += qOutd[i].x + qOutd[i].y + qOutd[i].z + qOutd[i].w;
            }
        } else {
            for (int i = 0; i < count; i++) {
                qa[i].mul(qb[i], qOut[i]);
                sum += qOut[i].x + qOut[i].y + qOut[i].z + qOut[i].w;
            }
        }
        return sum;
    }

    @Benchmark
    public double quaternionNormalizeBatch() {
        double sum = 0.0;
        if ("double".equals(precision)) {
            for (int i = 0; i < count; i++) {
                qad[i].normalize(qOutd[i]);
                sum += qOutd[i].w;
            }
        } else {
            for (int i = 0; i < count; i++) {
                qa[i].normalize(qOut[i]);
                sum += qOut[i].w;
            }
        }
        return sum;
    }

    @Benchmark
    public double quaternionRotateVectorBatch() {
        double sum = 0.0;
        if ("double".equals(precision)) {
            for (int i = 0; i < count; i++) {
                qad[i].transform(vd[i], vdOut[i]);
                sum += vdOut[i].x + vdOut[i].y + vdOut[i].z;
            }
        } else {
            for (int i = 0; i < count; i++) {
                qa[i].transform(vf[i], vfOut[i]);
                sum += vfOut[i].x + vfOut[i].y + vfOut[i].z;
            }
        }
        return sum;
    }

    @Benchmark
    public double rk4Batch() {
        double sum = 0.0;
        if ("double".equals(precision)) {
            for (int i = 0; i < count; i++) {
                int stateOff = i * 2;
                int scratchOff = i * 10;
                IntegratorRK4d.step(RK_DERIV_D, 0.0, 1.0 / 60.0, rkStateD, stateOff, 2, rkScratchD, scratchOff, rkDestD, stateOff);
                sum += rkDestD[stateOff] + rkDestD[stateOff + 1];
            }
        } else {
            for (int i = 0; i < count; i++) {
                int stateOff = i * 2;
                int scratchOff = i * 10;
                IntegratorRK4f.step(RK_DERIV_F, 0.0f, 1.0f / 60.0f, rkStateF, stateOff, 2, rkScratchF, scratchOff, rkDestF, stateOff);
                sum += rkDestF[stateOff] + rkDestF[stateOff + 1];
            }
        }
        return sum;
    }
}
