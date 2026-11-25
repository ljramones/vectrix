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
import org.dynamisengine.vectrix.fft.BlockConvolverf;
import org.dynamisengine.vectrix.fft.ComplexFftf;
import org.dynamisengine.vectrix.fft.RealFftf;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class FftBenchmark extends ThroughputBenchmark {
    @Param({"256", "1024", "4096", "65536"})
    public int complexCount;

    private float[] data;
    private float[] a;
    private float[] b;
    private float[] out;
    private float[] work;
    private ComplexFftf plan;
    private float[] scratch;
    private RealFftf realPlan;
    private float[] real;
    private float[] spectrum;
    private float[] realScratch;
    private BlockConvolverf filter;
    private float[] filterState;
    private float[] filterScratch;

    @Setup
    public void setup() {
        data = new float[complexCount * 2];
        a = new float[complexCount * 2];
        b = new float[complexCount * 2];
        out = new float[complexCount * 2];
        SplittableRandom rnd = new SplittableRandom(7);
        for (int i = 0; i < data.length; i++) {
            float v = (float) rnd.nextDouble(-1.0, 1.0);
            data[i] = v;
            a[i] = v;
            b[i] = (float) rnd.nextDouble(-1.0, 1.0);
        }
        work = data.clone();
        plan = ComplexFftf.plan(complexCount);
        scratch = new float[plan.scratchSize()];
        realPlan = RealFftf.plan(complexCount);
        real = java.util.Arrays.copyOf(b, complexCount);
        spectrum = new float[realPlan.spectrumLength()];
        realScratch = new float[realPlan.scratchSize()];
        // a 255-tap FIR filter over blocks of complexCount samples
        filter = BlockConvolverf.plan(a, 0, 255, complexCount);
        filterState = new float[filter.stateSize()];
        filterScratch = new float[filter.scratchSize()];
    }

    /** The planned transform (2.5.0), forward and inverse in place: compare with {@link #forwardInverse}. */
    @Benchmark
    public float[] planForwardInverse() {
        System.arraycopy(data, 0, work, 0, work.length);
        plan.forward(work, 0, scratch, 0);
        plan.inverse(work, 0, scratch, 0);
        return work;
    }

    @Benchmark
    public float[] realForwardInverse() {
        realPlan.forward(real, 0, spectrum, 0, realScratch, 0);
        realPlan.inverse(spectrum, 0, real, 0, realScratch, 0);
        return real;
    }

    @Benchmark
    public float[] firBlock() {
        filter.process(real, 0, complexCount, out, 0, filterState, 0, filterScratch, 0);
        return out;
    }

}
