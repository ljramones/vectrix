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
import org.vectrix.color.ColorMathf;
import org.vectrix.color.ColorSciencef;
import org.vectrix.core.Vector3f;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ColorBenchmark extends ThroughputBenchmark {
    @Param({"64", "1024", "16384"})
    public int count;

    private float[] r;
    private float[] g;
    private float[] b;
    private final Vector3f tmpA = new Vector3f();
    private final Vector3f tmpB = new Vector3f();

    @Setup
    public void setup() {
        r = new float[count];
        g = new float[count];
        b = new float[count];
        SplittableRandom rnd = new SplittableRandom(1111L);
        for (int i = 0; i < count; i++) {
            r[i] = (float) rnd.nextDouble(0.0, 1.0);
            g[i] = (float) rnd.nextDouble(0.0, 1.0);
            b[i] = (float) rnd.nextDouble(0.0, 1.0);
        }
    }

    @Benchmark
    public float srgbLinearRoundTripBatch() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            float lr = ColorMathf.srgbToLinear(r[i]);
            float lg = ColorMathf.srgbToLinear(g[i]);
            float lb = ColorMathf.srgbToLinear(b[i]);
            sum += ColorMathf.linearToSrgb(lr) + ColorMathf.linearToSrgb(lg) + ColorMathf.linearToSrgb(lb);
        }
        return sum;
    }

    @Benchmark
    public float xyzConversionBatch() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            ColorSciencef.linearSrgbToXyz(r[i], g[i], b[i], tmpA);
            ColorSciencef.xyzToLinearSrgb(tmpA.x, tmpA.y, tmpA.z, tmpB);
            sum += tmpB.x + tmpB.y + tmpB.z;
        }
        return sum;
    }

    @Benchmark
    public float toneMapBatch() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            tmpA.set(r[i] * 8.0f, g[i] * 8.0f, b[i] * 8.0f);
            ColorMathf.reinhardToneMap(tmpA, tmpB);
            sum += tmpB.x + tmpB.y + tmpB.z;
        }
        return sum;
    }
}
