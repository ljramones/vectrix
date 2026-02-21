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
import org.openjdk.jmh.infra.Blackhole;
import org.vectrix.core.Vector3f;
import org.vectrix.gpu.Half;
import org.vectrix.gpu.OctaNormal;
import org.vectrix.gpu.PackedNorm;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class GpuPackingBenchmark {
    @Param({"256", "4096", "65536"})
    public int size;

    private float[] values;
    private short[] halves;
    private float[] nx;
    private float[] ny;
    private float[] nz;
    private int[] packed8x4;
    private int[] packedOct16;
    private final Vector3f tmp = new Vector3f();

    @Setup
    public void setup() {
        values = new float[size];
        halves = new short[size];
        nx = new float[size];
        ny = new float[size];
        nz = new float[size];
        packed8x4 = new int[size];
        packedOct16 = new int[size];
        SplittableRandom rnd = new SplittableRandom(12345L);
        for (int i = 0; i < size; i++) {
            values[i] = (float) (rnd.nextDouble() * 2.0 - 1.0);
            float x = (float) (rnd.nextDouble() * 2.0 - 1.0);
            float y = (float) (rnd.nextDouble() * 2.0 - 1.0);
            float z = (float) (rnd.nextDouble() * 2.0 - 1.0);
            float lenInv = org.vectrix.core.Math.invsqrt(x * x + y * y + z * z);
            nx[i] = x * lenInv;
            ny[i] = y * lenInv;
            nz[i] = z * lenInv;
        }
    }

    @Benchmark
    public void halfEncode(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            short h = Half.pack(values[i]);
            halves[i] = h;
            bh.consume(h);
        }
    }

    @Benchmark
    public void halfDecode(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            float v = Half.unpack(halves[i]);
            bh.consume(v);
        }
    }

    @Benchmark
    public void snorm8x4Pack(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            int p = PackedNorm.packSnorm8x4(nx[i], ny[i], nz[i], values[i]);
            packed8x4[i] = p;
            bh.consume(p);
        }
    }

    @Benchmark
    public void snorm8x4Unpack(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            int p = packed8x4[i];
            bh.consume(PackedNorm.unpackSnorm8(p));
            bh.consume(PackedNorm.unpackSnorm8(p >>> 8));
            bh.consume(PackedNorm.unpackSnorm8(p >>> 16));
            bh.consume(PackedNorm.unpackSnorm8(p >>> 24));
        }
    }

    @Benchmark
    public void octaSnorm16Encode(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            int p = OctaNormal.encodeSnorm16(nx[i], ny[i], nz[i]);
            packedOct16[i] = p;
            bh.consume(p);
        }
    }

    @Benchmark
    public void octaSnorm16Decode(Blackhole bh) {
        for (int i = 0; i < size; i++) {
            OctaNormal.decodeSnorm16(packedOct16[i], tmp);
            bh.consume(tmp.x);
            bh.consume(tmp.y);
            bh.consume(tmp.z);
        }
    }
}
