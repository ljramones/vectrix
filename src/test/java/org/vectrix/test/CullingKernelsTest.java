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
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.vectrix.core.Matrix4f;
import org.vectrix.experimental.KernelConfig;
import org.vectrix.experimental.MathMode;
import org.vectrix.geometry.CullingKernels;
import org.vectrix.geometry.FrustumIntersection;
import org.vectrix.geometry.FrustumPlanes;
import org.vectrix.soa.AABBSoA;

class CullingKernelsTest {
    @Test
    void classificationMatchesFrustumIntersectionForRandomAabbs() {
        Matrix4f vp = new Matrix4f()
                .perspective((float) java.lang.Math.toRadians(70.0), 16.0f / 9.0f, 0.1f, 200.0f)
                .lookAt(0.0f, 1.0f, 8.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
        FrustumIntersection fr = new FrustumIntersection(vp);
        FrustumPlanes fp = new FrustumPlanes().set(vp, true);
        SplittableRandom rnd = new SplittableRandom(1234);

        AABBSoA aabbs = new AABBSoA(512);
        int[] out = new int[aabbs.size()];
        for (int i = 0; i < aabbs.size(); i++) {
            float cx = (float) rnd.nextDouble(-40.0, 40.0);
            float cy = (float) rnd.nextDouble(-25.0, 25.0);
            float cz = (float) rnd.nextDouble(-80.0, 20.0);
            float ex = (float) rnd.nextDouble(0.05, 4.0);
            float ey = (float) rnd.nextDouble(0.05, 4.0);
            float ez = (float) rnd.nextDouble(0.05, 4.0);
            aabbs.set(i, cx - ex, cy - ey, cz - ez, cx + ex, cy + ey, cz + ez);
        }

        CullingKernels.frustumCullAabbBatch(fp, aabbs, out, aabbs.size());
        for (int i = 0; i < aabbs.size(); i++) {
            int expected = fr.intersectAab(aabbs.minX[i], aabbs.minY[i], aabbs.minZ[i], aabbs.maxX[i], aabbs.maxY[i], aabbs.maxZ[i]);
            int actual = out[i];
            if (expected >= 0) {
                expected = FrustumIntersection.OUTSIDE;
            }
            assertEquals(expected, actual);
        }
    }

    @Test
    void overloadUsingViewProjMatchesExplicitPlanes() {
        Matrix4f vp = new Matrix4f().perspective((float) java.lang.Math.toRadians(60.0), 1.0f, 0.1f, 100.0f);
        AABBSoA aabbs = new AABBSoA(3);
        aabbs.set(0, -0.2f, -0.2f, -2.0f, 0.2f, 0.2f, -1.5f);
        aabbs.set(1, 20.0f, 20.0f, -2.0f, 21.0f, 21.0f, -1.0f);
        aabbs.set(2, -2.0f, -2.0f, -0.2f, 2.0f, 2.0f, -0.05f);
        int[] a = new int[3];
        int[] b = new int[3];
        CullingKernels.frustumCullAabbBatch(new FrustumPlanes().set(vp, false), aabbs, a, 3);
        CullingKernels.frustumCullAabbBatch(vp, aabbs, b, 3);
        assertEquals(a[0], b[0]);
        assertEquals(a[1], b[1]);
        assertEquals(a[2], b[2]);
    }

    @Test
    void scalarAndSimdPathsMatch() {
        Matrix4f vp = new Matrix4f().perspective((float) java.lang.Math.toRadians(75.0), 1.5f, 0.1f, 300.0f);
        FrustumPlanes fp = new FrustumPlanes().set(vp, false);
        SplittableRandom rnd = new SplittableRandom(42);
        AABBSoA aabbs = new AABBSoA(257);
        for (int i = 0; i < aabbs.size(); i++) {
            float cx = (float) rnd.nextDouble(-50.0, 50.0);
            float cy = (float) rnd.nextDouble(-50.0, 50.0);
            float cz = (float) rnd.nextDouble(-200.0, 20.0);
            float ex = (float) rnd.nextDouble(0.1, 8.0);
            float ey = (float) rnd.nextDouble(0.1, 8.0);
            float ez = (float) rnd.nextDouble(0.1, 8.0);
            aabbs.set(i, cx - ex, cy - ey, cz - ez, cx + ex, cy + ey, cz + ez);
        }
        int[] scalar = new int[aabbs.size()];
        int[] simd = new int[aabbs.size()];
        CullingKernels.frustumCullAabbBatchScalar(fp, aabbs, scalar, aabbs.size());
        CullingKernels.frustumCullAabbBatchSimd(fp, aabbs, simd, aabbs.size());
        for (int i = 0; i < aabbs.size(); i++) {
            assertEquals(scalar[i], simd[i]);
        }
    }

    @Test
    void strictModeUsesScalarPath() {
        Matrix4f vp = new Matrix4f().perspective((float) java.lang.Math.toRadians(60.0), 1.0f, 0.1f, 100.0f);
        FrustumPlanes fp = new FrustumPlanes().set(vp, false);
        AABBSoA aabbs = new AABBSoA(64);
        for (int i = 0; i < aabbs.size(); i++) {
            aabbs.set(i, -1.0f, -1.0f, -3.0f - i * 0.01f, 1.0f, 1.0f, -2.0f - i * 0.01f);
        }
        int[] auto = new int[aabbs.size()];
        int[] scalar = new int[aabbs.size()];
        MathMode prevMode = KernelConfig.mathMode();
        boolean prevSimd = KernelConfig.simdEnabled();
        try {
            KernelConfig.setMathMode(MathMode.STRICT);
            KernelConfig.setSimdEnabled(true);
            CullingKernels.frustumCullAabbBatch(fp, aabbs, auto, aabbs.size());
            CullingKernels.frustumCullAabbBatchScalar(fp, aabbs, scalar, aabbs.size());
        } finally {
            KernelConfig.setMathMode(prevMode);
            KernelConfig.setSimdEnabled(prevSimd);
        }
        for (int i = 0; i < aabbs.size(); i++) {
            assertEquals(scalar[i], auto[i]);
        }
    }
}
