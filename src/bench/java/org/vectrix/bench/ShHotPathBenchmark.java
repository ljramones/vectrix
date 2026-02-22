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
import org.vectrix.sh.ShCoeffs9f;
import org.vectrix.sh.ShConvolution;
import org.vectrix.sh.ShProjection;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class ShHotPathBenchmark {
    @Param({"256", "4096", "16384"})
    public int count;

    private float[] dirX;
    private float[] dirY;
    private float[] dirZ;
    private float[] rgbR;
    private float[] rgbG;
    private float[] rgbB;
    private final float[] scratch = new float[9];
    private final float[] rgbOut = new float[3];
    private final ShCoeffs9f coeffs = new ShCoeffs9f();

    @Setup
    public void setup() {
        dirX = new float[count];
        dirY = new float[count];
        dirZ = new float[count];
        rgbR = new float[count];
        rgbG = new float[count];
        rgbB = new float[count];

        SplittableRandom rnd = new SplittableRandom(5678);
        for (int i = 0; i < count; i++) {
            float x = (float) (rnd.nextDouble() * 2.0 - 1.0);
            float y = (float) (rnd.nextDouble() * 2.0 - 1.0);
            float z = (float) (rnd.nextDouble() * 2.0 - 1.0);
            float lenInv = (float) (1.0 / Math.sqrt(x * x + y * y + z * z + 1E-12));
            dirX[i] = x * lenInv;
            dirY[i] = y * lenInv;
            dirZ[i] = z * lenInv;
            rgbR[i] = (float) rnd.nextDouble();
            rgbG[i] = (float) rnd.nextDouble();
            rgbB[i] = (float) rnd.nextDouble();
        }
    }

    @Benchmark
    public ShCoeffs9f projectSampleZeroAllocLoop() {
        coeffs.zero();
        for (int i = 0; i < count; i++) {
            ShProjection.projectSample(dirX[i], dirY[i], dirZ[i], rgbR[i], rgbG[i], rgbB[i], 1.0f, scratch, coeffs);
        }
        return coeffs;
    }

    @Benchmark
    public float evaluateIrradianceZeroAllocLoop() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            ShConvolution.evaluateIrradiance(coeffs, dirX[i], dirY[i], dirZ[i], scratch, rgbOut);
            sum += rgbOut[0] + rgbOut[1] + rgbOut[2];
        }
        return sum;
    }
}
