/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
import org.vectrix.core.Matrix4f;
import org.vectrix.experimental.KernelConfig;
import org.vectrix.experimental.MathMode;
import org.vectrix.geometry.CullingKernels;
import org.vectrix.geometry.FrustumIntersection;
import org.vectrix.geometry.FrustumPlanes;
import org.vectrix.soa.AABBSoA;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class FrustumCullingBenchmark {
    @Param({"1024", "16384", "65536"})
    public int count;

    private AABBSoA aabbs;
    private int[] out;
    private FrustumPlanes planes;
    private FrustumIntersection frustum;

    @Setup
    public void setup() {
        Matrix4f vp = new Matrix4f()
                .perspective((float) java.lang.Math.toRadians(75.0), 16.0f / 9.0f, 0.1f, 500.0f)
                .lookAt(2.0f, 3.0f, 14.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        planes = new FrustumPlanes().set(vp, false);
        frustum = new FrustumIntersection(vp);
        aabbs = new AABBSoA(count);
        out = new int[count];
        SplittableRandom rnd = new SplittableRandom(77L);
        for (int i = 0; i < count; i++) {
            float cx = (float) rnd.nextDouble(-200.0, 200.0);
            float cy = (float) rnd.nextDouble(-120.0, 120.0);
            float cz = (float) rnd.nextDouble(-300.0, 120.0);
            float ex = (float) rnd.nextDouble(0.05, 8.0);
            float ey = (float) rnd.nextDouble(0.05, 8.0);
            float ez = (float) rnd.nextDouble(0.05, 8.0);
            aabbs.set(i, cx - ex, cy - ey, cz - ez, cx + ex, cy + ey, cz + ez);
        }
    }

    @Benchmark
    public int[] batchKernelAuto() {
        KernelConfig.setMathMode(MathMode.FAST);
        KernelConfig.setSimdEnabled(true);
        CullingKernels.frustumCullAabbBatch(planes, aabbs, out, count);
        return out;
    }

    @Benchmark
    public int[] batchKernelScalarForced() {
        CullingKernels.frustumCullAabbBatchScalar(planes, aabbs, out, count);
        return out;
    }

    @Benchmark
    public int[] batchKernelSimdForced() {
        CullingKernels.frustumCullAabbBatchSimd(planes, aabbs, out, count);
        return out;
    }

    @Benchmark
    public int[] frustumIntersectionLoop() {
        for (int i = 0; i < count; i++) {
            int r = frustum.intersectAab(aabbs.minX[i], aabbs.minY[i], aabbs.minZ[i], aabbs.maxX[i], aabbs.maxY[i], aabbs.maxZ[i]);
            out[i] = r >= 0 ? FrustumIntersection.OUTSIDE : r;
        }
        return out;
    }
}
