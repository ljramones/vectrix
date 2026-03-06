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
import org.vectrix.hash.PcgHash;
import org.vectrix.hash.SpatialHash;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class HashBenchmark extends ThroughputBenchmark {
    @Param({"64", "1024", "16384"})
    public int count;

    @Param({"uniform", "clustered"})
    public String distribution;

    @Param({"scalar", "vec3", "cellCoord"})
    public String keyShape;

    private int[] x;
    private int[] y;
    private int[] z;

    @Setup
    public void setup() {
        x = new int[count];
        y = new int[count];
        z = new int[count];
        SplittableRandom rnd = new SplittableRandom(12345L);
        int spread = "clustered".equals(distribution) ? 64 : 1 << 20;
        for (int i = 0; i < count; i++) {
            x[i] = rnd.nextInt(-spread, spread);
            y[i] = rnd.nextInt(-spread, spread);
            z[i] = rnd.nextInt(-spread, spread);
        }
    }

    @Benchmark
    public int pcgHashLoop() {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            switch (keyShape) {
                case "scalar":
                    sum ^= PcgHash.hash32(x[i]);
                    break;
                case "vec3":
                    sum ^= PcgHash.hash32(x[i], y[i], z[i]);
                    break;
                case "cellCoord":
                default:
                    sum ^= PcgHash.hash32((x[i] * 73856093) ^ (y[i] * 19349663) ^ (z[i] * 83492791));
                    break;
            }
        }
        return sum;
    }

    @Benchmark
    public int spatialHashLoop() {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum ^= SpatialHash.hashCell3i(x[i], y[i], z[i]);
        }
        return sum;
    }
}
