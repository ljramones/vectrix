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
import org.vectrix.easing.Easingf;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class EasingBenchmark extends ThroughputBenchmark {
    @Param({"64", "1024", "16384"})
    public int count;

    @Param({"linear", "smoothStep", "easeInOutCubic", "bounceOut", "spring"})
    public String curve;

    private float[] t;

    @Setup
    public void setup() {
        t = new float[count];
        SplittableRandom rnd = new SplittableRandom(2222L);
        for (int i = 0; i < count; i++) {
            t[i] = rnd.nextFloat();
        }
    }

    @Benchmark
    public float easingBatch() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            float v;
            switch (curve) {
                case "linear":
                    v = Easingf.linear(t[i]);
                    break;
                case "smoothStep":
                    v = Easingf.smoothStep(t[i]);
                    break;
                case "easeInOutCubic":
                    v = Easingf.easeInOutCubic(t[i]);
                    break;
                case "bounceOut":
                    v = Easingf.bounceOut(t[i]);
                    break;
                case "spring":
                default:
                    v = Easingf.spring(t[i], 8.0f, 16.0f);
                    break;
            }
            sum += v;
        }
        return sum;
    }
}
