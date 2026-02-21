/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.bench;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
import org.vectrix.affine.Transformf;
import org.vectrix.gpu.GpuTransformLayout;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class GpuLayoutBenchmark {
    @Param({"256", "4096", "16384"})
    public int count;

    private Transformf[] transforms;
    private GpuTransformLayout full;
    private GpuTransformLayout compact;
    private ByteBuffer fullBuf;
    private ByteBuffer compactBuf;

    @Setup
    public void setup() {
        transforms = new Transformf[count];
        SplittableRandom rnd = new SplittableRandom(77);
        for (int i = 0; i < count; i++) {
            Transformf t = new Transformf();
            t.translation.set((float) rnd.nextDouble(-100.0, 100.0), (float) rnd.nextDouble(-100.0, 100.0), (float) rnd.nextDouble(-100.0, 100.0));
            t.rotation.identity().rotateXYZ((float) rnd.nextDouble(-3.0, 3.0), (float) rnd.nextDouble(-3.0, 3.0), (float) rnd.nextDouble(-3.0, 3.0));
            t.scale.set((float) rnd.nextDouble(0.25, 8.0), (float) rnd.nextDouble(0.25, 8.0), (float) rnd.nextDouble(0.25, 8.0));
            transforms[i] = t;
        }
        full = GpuTransformLayout.floatTRS();
        compact = GpuTransformLayout.compactTRS();
        fullBuf = ByteBuffer.allocate(full.requiredBytes(count)).order(ByteOrder.LITTLE_ENDIAN);
        compactBuf = ByteBuffer.allocate(compact.requiredBytes(count)).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Benchmark
    public ByteBuffer writeFullFloat() {
        for (int i = 0; i < count; i++) {
            full.write(fullBuf, i, transforms[i]);
        }
        return fullBuf;
    }

    @Benchmark
    public ByteBuffer writeCompactPacked() {
        for (int i = 0; i < count; i++) {
            compact.write(compactBuf, i, transforms[i]);
        }
        return compactBuf;
    }
}
