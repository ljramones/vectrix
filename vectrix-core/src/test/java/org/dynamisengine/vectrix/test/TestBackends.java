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
package org.dynamisengine.vectrix.test;

import org.dynamisengine.vectrix.geometry.CullingKernels;
import org.dynamisengine.vectrix.geometry.FrustumPlanes;
import org.dynamisengine.vectrix.simd.SimdSupport;
import org.dynamisengine.vectrix.soa.AABBSoA;

/**
 * Runs a batch kernel on a chosen backend through the public switch ({@link SimdSupport#setEnabled}), for tests that
 * compare the scalar and Vector API paths. The SIMD path runs only in the {@code -simd} build with the Vector API
 * module present and for batch sizes the kernel accepts; otherwise both calls take the scalar path.
 */
final class TestBackends {

    private TestBackends() {
    }

    static void cullScalar(FrustumPlanes frustum, AABBSoA bounds, int[] out, int count) {
        boolean prev = SimdSupport.isEnabled();
        SimdSupport.setEnabled(false);
        try {
            CullingKernels.frustumCullAabbBatch(frustum, bounds, out, count);
        } finally {
            SimdSupport.setEnabled(prev);
        }
    }

    static void cullSimd(FrustumPlanes frustum, AABBSoA bounds, int[] out, int count) {
        boolean prev = SimdSupport.isEnabled();
        SimdSupport.setEnabled(true);
        try {
            CullingKernels.frustumCullAabbBatch(frustum, bounds, out, count);
        } finally {
            SimdSupport.setEnabled(prev);
        }
    }
}
