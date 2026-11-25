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
import org.dynamisengine.vectrix.sampling.BestCandidateSampling;
import org.dynamisengine.vectrix.sampling.PoissonSampling;
import org.dynamisengine.vectrix.sampling.SpiralSampling;
import org.dynamisengine.vectrix.sampling.StratifiedSampling;
import org.dynamisengine.vectrix.sampling.UniformSampling;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SamplingBenchmark extends ThroughputBenchmark {
    @Param({"64", "1024", "4096"})
    public int count;

    @Param({"uniform", "stratified", "poisson", "bestCandidate", "spiral"})
    public String sampler;

    @Param({"2D", "3D"})
    public String dimension;

    private float[] outX;
    private float[] outY;
    private float[] outZ;

    @Setup
    public void setup() {
        outX = new float[count];
        outY = new float[count];
        outZ = new float[count];
    }

    @Benchmark
    public float sampleBatch() {
        final float[] sum = new float[1];
        final int[] idx = new int[1];

        switch (sampler) {
            case "uniform":
                if ("3D".equals(dimension)) {
                    new UniformSampling.Sphere(17L, count, (x, y, z) -> {
                        int i = idx[0]++;
                        if (i < count) {
                            outX[i] = x;
                            outY[i] = y;
                            outZ[i] = z;
                            sum[0] += x + y + z;
                        }
                    });
                } else {
                    new UniformSampling.Disk(17L, count, (x, y) -> {
                        int i = idx[0]++;
                        if (i < count) {
                            outX[i] = x;
                            outY[i] = y;
                            sum[0] += x + y;
                        }
                    });
                }
                break;
            case "stratified": {
                int n = java.lang.Math.max(1, (int) java.lang.Math.sqrt(count));
                StratifiedSampling s = new StratifiedSampling(17L);
                s.generateRandom(n, (x, y) -> {
                    int i = idx[0]++;
                    if (i < count) {
                        outX[i] = x;
                        outY[i] = y;
                        sum[0] += x + y;
                    }
                });
                break;
            }
            case "poisson": {
                float minDist = java.lang.Math.max(0.01f, 1.6f / (float) java.lang.Math.sqrt(count));
                new PoissonSampling.Disk(17L, 1.0f, minDist, 30, (x, y) -> {
                    int i = idx[0]++;
                    if (i < count) {
                        outX[i] = x;
                        outY[i] = y;
                        sum[0] += x + y;
                    }
                });
                break;
            }
            case "bestCandidate":
                if ("3D".equals(dimension)) {
                    new BestCandidateSampling.Sphere()
                            .seed(17L)
                            .numSamples(count)
                            .numCandidates(60)
                            .generate((x, y, z) -> {
                                int i = idx[0]++;
                                if (i < count) {
                                    outX[i] = x;
                                    outY[i] = y;
                                    outZ[i] = z;
                                    sum[0] += x + y + z;
                                }
                            });
                } else {
                    new BestCandidateSampling.Quad()
                            .seed(17L)
                            .numSamples(count)
                            .numCandidates(60)
                            .generate((x, y) -> {
                                int i = idx[0]++;
                                if (i < count) {
                                    outX[i] = x;
                                    outY[i] = y;
                                    sum[0] += x + y;
                                }
                            });
                }
                break;
            case "spiral":
            default:
                new SpiralSampling(17L).createEquiAngle(1.0f, 7, count, (x, y) -> {
                    int i = idx[0]++;
                    if (i < count) {
                        outX[i] = x;
                        outY[i] = y;
                        sum[0] += x + y;
                    }
                });
                break;
        }

        return sum[0] + idx[0];
    }
}
