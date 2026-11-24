/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
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
package org.dynamisengine.vectrix.compute;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.vectrix.core.DepthRange;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.geometry.CullingKernels;
import org.dynamisengine.vectrix.geometry.FrustumIntersection;
import org.dynamisengine.vectrix.geometry.FrustumPlanes;
import org.dynamisengine.vectrix.soa.AABBSoA;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link GpuCullingKernels} against {@code CullingKernels}, exactly, on every enabled backend. */
class GpuCullingKernelsTest {

    private static final int SENTINEL = 0x5EED;

    /** The projections the planes are extracted from. */
    enum Projection { PERSPECTIVE, INFINITE_PERSPECTIVE, ORTHO }

    static Stream<Arguments> counts() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (int n : EngineData.COUNTS) {
                out.add(Arguments.of(backend, n));
            }
        }
        return out.stream();
    }

    static Stream<String> backends() {
        return Devices.names().stream();
    }

    /** The view-projection matrix of a camera at {@code (1, 2, 3)} looking at the origin. */
    static Matrix4f viewProjection(Projection projection, DepthRange range) {
        boolean zeroToOne = range != DepthRange.NEGATIVE_ONE_TO_ONE;
        Matrix4f m = new Matrix4f();
        switch (projection) {
            case PERSPECTIVE -> m.perspective((float) Math.toRadians(70), 1.6f, 0.5f, 120f, zeroToOne);
            case INFINITE_PERSPECTIVE -> m.perspective((float) Math.toRadians(60), 1.25f, 0.25f,
                    Float.POSITIVE_INFINITY, zeroToOne);
            case ORTHO -> m.ortho(-30f, 25f, -20f, 22f, 1f, 90f, zeroToOne);
            default -> throw new AssertionError(projection);
        }
        if (range == DepthRange.REVERSED_ZERO_TO_ONE) {
            // depth' = w - depth: near at 1, far at 0
            m.mulLocal(new Matrix4f().m22(-1f).m32(1f));
        }
        return m.lookAt(1f, 2f, 3f, 0f, 0f, -10f, 0f, 1f, 0f);
    }

    static FrustumPlanes planes(Projection projection, DepthRange range, boolean normalize) {
        return new FrustumPlanes().set(viewProjection(projection, range), normalize, range);
    }

    /** Boxes around the view volume, so that every classification occurs, with some degenerate ones. */
    static AABBSoA boxes(SplittableRandom r, int n) {
        AABBSoA b = new AABBSoA(n);
        for (int i = 0; i < n; i++) {
            float cx = EngineData.uniform(r, -80, 80);
            float cy = EngineData.uniform(r, -80, 80);
            float cz = EngineData.uniform(r, -150, 40);
            float ex = r.nextInt(16) == 0 ? 0f : EngineData.uniform(r, 0, 12);
            float ey = EngineData.uniform(r, 0, 12);
            float ez = EngineData.uniform(r, 0, 12);
            b.set(i, cx - ex, cy - ey, cz - ez, cx + ex, cy + ey, cz + ez);
        }
        return b;
    }

    /** Runs the batch on {@code backend} with non-dense offsets and strides and compares every result. */
    static void check(GpuDevice device, GpuCullingKernels cull, boolean hardware, String what, FrustumPlanes fp,
            AABBSoA boxes, int n) {
        int[] expected = new int[n];
        CullingKernels.frustumCullAabbBatch(fp, boxes, expected, n);
        for (int i = 0; i < n; i++) {
            assertEquals(CullingKernels.classifyAabb(fp, boxes.minX[i], boxes.minY[i], boxes.minZ[i], boxes.maxX[i],
                    boxes.maxY[i], boxes.maxZ[i]), expected[i], "the CPU batch is classifyAabb per box");
        }
        float[] planes = fp.getVec4Array(new float[GpuCullingKernels.PLANE_FLOATS + 3], 3);
        int boundsOff = 5;
        int boundsStride = n + 3;
        int resultOff = 2;
        float[] bounds = EngineData.pack(EngineModels.fields(boxes), n, boundsOff, boundsStride, 4, Float.NaN);
        int[] result = new int[resultOff + n + 3];
        Arrays.fill(result, SENTINEL);
        try (var enc = device.encoder()) {
            var gb = Devices.upload(device, enc, bounds);
            var gr = Devices.upload(device, enc, result);
            cull.frustumCullAabbBatch(enc, planes, 3, gb, boundsOff, boundsStride, gr, resultOff, n);
            var read = enc.download(gr);
            Devices.run(device, enc);
            int[] want = result.clone();
            System.arraycopy(expected, 0, want, resultOff, n);
            Bits.assertSame(want, Devices.ints(read, result.length), what);
        }
    }

    @ParameterizedTest(name = "{0} n={1}")
    @MethodSource("counts")
    void matchesFrustumCullAabbBatch(String backend, int n) {
        SplittableRandom r = new SplittableRandom(n * 7919L);
        Projection projection = Projection.values()[n % 3];
        DepthRange range = DepthRange.values()[(n / 3) % 3];
        FrustumPlanes fp = planes(projection, range, n % 2 == 0);
        AABBSoA boxes = boxes(r, n);
        try (GpuDevice device = Devices.open(backend); GpuCullingKernels cull = new GpuCullingKernels(device)) {
            check(device, cull, Devices.hardware(backend), backend + " cull n=" + n, fp, boxes, n);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void everyDepthRangeNormalizationAndProjection(String backend) {
        SplittableRandom r = new SplittableRandom(17);
        int n = 3000;
        try (GpuDevice device = Devices.open(backend); GpuCullingKernels cull = new GpuCullingKernels(device)) {
            for (Projection projection : Projection.values()) {
                for (DepthRange range : DepthRange.values()) {
                    for (boolean normalize : new boolean[] {false, true}) {
                        FrustumPlanes fp = planes(projection, range, normalize);
                        AABBSoA boxes = boxes(r, n);
                        int[] cpu = new int[n];
                        CullingKernels.frustumCullAabbBatch(fp, boxes, cpu, n);
                        long inside = Arrays.stream(cpu).filter(v -> v == FrustumIntersection.INSIDE).count();
                        long outside = Arrays.stream(cpu).filter(v -> v == FrustumIntersection.OUTSIDE).count();
                        long crossing = Arrays.stream(cpu).filter(v -> v == FrustumIntersection.INTERSECT).count();
                        assertTrue(inside > 0 && outside > 0 && crossing > 0, "every classification occurs");
                        check(device, cull, Devices.hardware(backend),
                                backend + " " + projection + " " + range + " normalize=" + normalize, fp, boxes, n);
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void degenerateNanAndInfiniteBounds(String backend) {
        FrustumPlanes fp = planes(Projection.PERSPECTIVE, DepthRange.ZERO_TO_ONE, true);
        float nan = Float.NaN;
        float inf = Float.POSITIVE_INFINITY;
        float[][] special = {
            {0, 0, -10, 0, 0, -10}, // a point inside
            {0, 0, 10, 0, 0, 10}, // a point behind the camera
            {5, 5, -20, -5, -5, -30}, // min above max
            {nan, 0, -10, 1, 1, -9}, {0, nan, -10, 1, 1, -9}, {0, 0, nan, 1, 1, -9},
            {0, 0, -10, nan, 1, -9}, {0, 0, -10, 1, nan, -9}, {0, 0, -10, 1, 1, nan},
            {nan, nan, nan, nan, nan, nan},
            {nan, 0, 500, 1, 1, 501}, // NaN on a box far outside: never OUTSIDE
            {-inf, -inf, -inf, inf, inf, inf}, // everything
            {-inf, 0, -10, 1, 1, -9}, {0, 0, -10, inf, 1, -9},
            {1000, 1000, 1000, inf, inf, inf},
            {-1e30f, -1e30f, -1e30f, 1e30f, 1e30f, 1e30f},
            {-0f, -0f, -0f, 0f, 0f, 0f},
            {-3.4e38f, 0, -10, 3.4e38f, 1, -9},
        };
        // boxes whose faces lie exactly on a plane through the origin: dMax or dMin is exactly 0
        FrustumPlanes axes = new FrustumPlanes();
        float[][] axisPlanes = {{1, 0, 0, 0}, {-1, 0, 0, 4}, {0, 1, 0, 0}, {0, -1, 0, 4}, {0, 0, 1, 0}, {0, 0, -1, 4}};
        for (int k = 0; k < 6; k++) {
            axes.x[k] = axisPlanes[k][0];
            axes.y[k] = axisPlanes[k][1];
            axes.z[k] = axisPlanes[k][2];
            axes.w[k] = axisPlanes[k][3];
        }
        float[][] touching = {
            {0, 0, 0, 4, 4, 4}, {-1, 0, 0, 0, 1, 1}, {4, 0, 0, 5, 1, 1}, {-2, -2, -2, -1, 0, 0}, {1, 1, 1, 2, 2, 2},
            {0, 0, 0, 0, 0, 0}, {-0f, -0f, -0f, -0f, -0f, -0f},
        };
        try (GpuDevice device = Devices.open(backend); GpuCullingKernels cull = new GpuCullingKernels(device)) {
            check(device, cull, Devices.hardware(backend), backend + " special bounds", fp, of(special),
                    special.length);
            check(device, cull, Devices.hardware(backend), backend + " touching boxes", axes, of(touching),
                    touching.length);
        }
    }

    private static AABBSoA of(float[][] boxes) {
        AABBSoA b = new AABBSoA(boxes.length);
        for (int i = 0; i < boxes.length; i++) {
            float[] v = boxes[i];
            b.set(i, v[0], v[1], v[2], v[3], v[4], v[5]);
        }
        return b;
    }

    @Test
    void constantsAreFrustumIntersections() {
        assertArrayEquals(new int[] {FrustumIntersection.INSIDE, FrustumIntersection.INTERSECT,
            FrustumIntersection.OUTSIDE}, new int[] {GpuCullingKernels.INSIDE, GpuCullingKernels.INTERSECT,
                GpuCullingKernels.OUTSIDE});
    }
}
