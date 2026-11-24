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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.junit.jupiter.api.Test;

/**
 * The engine kernels reject invalid arguments before anything is recorded, with the CPU kernels' exception type
 * ({@link ValidationTest}'s style): each rejected call leaves its output buffer as it was.
 */
class EngineValidationTest {

    /** A call recorded into {@code enc}. */
    interface Body {
        void record(GpuCommandEncoder enc) throws Throwable;
    }

    private static float[] ones(int n) {
        float[] a = new float[n];
        Arrays.fill(a, 1f);
        return a;
    }

    /**
     * Records {@code body} on a fresh encoder, which must throw IllegalArgumentException, then checks that
     * {@code target} still holds ones.
     */
    private static void rejected(GpuDevice device, GpuBuffer target, int count, Body body) {
        var enc = device.encoder();
        assertThrows(IllegalArgumentException.class, () -> body.record(enc));
        var read = enc.download(target);
        Devices.run(device, enc);
        assertArrayEquals(ones(count), Devices.floats(read, count));
    }

    @Test
    void cullingChecksPlanesBoundsAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuCullingKernels c = new GpuCullingKernels(d)) {
            var bounds = upload(d, ones(6 * 10));
            var result = upload(d, ones(10));
            float[] planes = new float[24];
            rejected(d, result, 10, e -> c.frustumCullAabbBatch(e, new float[23], 0, bounds, 0, 10, result, 0, 10));
            rejected(d, result, 10, e -> c.frustumCullAabbBatch(e, planes, 1, bounds, 0, 10, result, 0, 10));
            rejected(d, result, 10, e -> c.frustumCullAabbBatch(e, planes, -1, bounds, 0, 10, result, 0, 10));
            rejected(d, result, 10, e -> c.frustumCullAabbBatch(e, null, 0, bounds, 0, 10, result, 0, 10));
            rejected(d, result, 10, e -> c.frustumCullAabbBatch(e, planes, 0, bounds, 0, 9, result, 0, 10)); // stride
            rejected(d, result, 10, e -> c.frustumCullAabbBatch(e, planes, 0, bounds, 1, 10, result, 0, 10)); // off
            rejected(d, result, 10, e -> c.frustumCullAabbBatch(e, planes, 0, bounds, 0, 10, result, 1, 10));
            rejected(d, result, 10, e -> c.frustumCullAabbBatch(e, planes, 0, null, 0, 10, result, 0, 10));
            rejected(d, bounds, 60, e -> c.frustumCullAabbBatch(e, planes, 0, bounds, 0, 5, bounds, 30, 5));
            assertThrows(IllegalArgumentException.class, () -> c.frustumCullAabbBatch(null, planes, 0, bounds, 0, 10,
                    result, 0, 10));
        }
    }

    @Test
    void composeChecksCountsStridesAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuTransformKernels t = new GpuTransformKernels(d)) {
            var p = upload(d, ones(100));
            var l = upload(d, ones(100));
            var o = upload(d, ones(100));
            rejected(d, o, 100, e -> t.composeBatch(e, p, 0, 10, l, 0, 10, o, 0, 10, -1));
            rejected(d, o, 100, e -> t.composeBatch(e, p, 0, 10, l, 0, 10, o, 0, 10, 11)); // stride below count
            rejected(d, o, 100, e -> t.composeBatch(e, p, 1, 10, l, 0, 10, o, 0, 10, 10)); // parents too small
            rejected(d, o, 100, e -> t.composeBatch(e, p, 0, 10, l, 0, 11, o, 0, 10, 10)); // locals too small
            rejected(d, o, 100, e -> t.composeBatch(e, p, 0, 10, l, 0, 10, o, 0, 10, 11));
            rejected(d, o, 100, e -> t.composeBatch(e, p, 0, 10, l, 0, 10, o, 0, -1, 1));
            rejected(d, p, 100, e -> t.composeBatch(e, p, 0, 10, l, 0, 10, p, 0, 10, 10)); // in place
            rejected(d, l, 100, e -> t.composeBatch(e, p, 0, 10, l, 0, 10, l, 0, 10, 10));
            rejected(d, o, 100, e -> t.composeBatch(e, null, 0, 10, l, 0, 10, o, 0, 10, 10));
        }
    }

    @Test
    void skinningChecksInfluencesPositionsAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuSkinningKernels s = new GpuSkinningKernels(d)) {
            int n = 8;
            var joints = upload(d, ones(10 * 4));
            var dq = upload(d, ones(8 * 4));
            var idx = uploadInts(d, new int[4 * n]);
            var w = upload(d, ones(4 * n));
            var in = upload(d, ones(3 * n));
            var out = upload(d, ones(3 * n));
            var palette = upload(d, ones(12 * 4));
            rejected(d, out, 3 * n, e -> s.skinLbs4(e, joints, 0, 4, 4, idx, 0, w, 0, in, 0, n, out, 0, n, -1));
            rejected(d, out, 3 * n, e -> s.skinLbs4(e, joints, 0, 4, 5, idx, 0, w, 0, in, 0, n, out, 0, n, n));
            rejected(d, out, 3 * n, e -> s.skinLbs4(e, joints, 0, 4, 0, idx, 0, w, 0, in, 0, n, out, 0, n, n));
            rejected(d, out, 3 * n, e -> s.skinLbs4(e, joints, 0, 4, -1, idx, 0, w, 0, in, 0, n, out, 0, n, n));
            rejected(d, out, 3 * n, e -> s.skinLbs4(e, joints, 0, 4, 4, idx, 1, w, 0, in, 0, n, out, 0, n, n));
            rejected(d, out, 3 * n, e -> s.skinLbs4(e, joints, 0, 4, 4, idx, 0, w, 1, in, 0, n, out, 0, n, n));
            rejected(d, out, 3 * n, e -> s.skinLbs4(e, joints, 0, 4, 4, idx, 0, w, 0, in, 0, n + 1, out, 0, n, n));
            rejected(d, out, 3 * n, e -> s.skinLbs4(e, joints, 0, 4, 4, idx, 0, w, 0, in, 0, n, out, 1, n, n));
            rejected(d, out, 3 * n, e -> s.skinLbs4(e, joints, 0, 4, 4, null, 0, w, 0, in, 0, n, out, 0, n, n));
            rejected(d, in, 3 * n, e -> s.skinLbs4(e, joints, 0, 4, 4, idx, 0, w, 0, in, 0, n, in, 0, n, n));
            rejected(d, w, 4 * n, e -> s.skinLbs4(e, joints, 0, 4, 4, idx, 0, w, 0, in, 0, n, w, 0, n, n));
            rejected(d, joints, 40, e -> s.skinLbs4(e, joints, 0, 4, 4, idx, 0, w, 0, in, 0, n, joints, 0, n, n));
            // per-slot strides
            rejected(d, out, 3 * n, e -> s.skinLbs4SoA(e, joints, 0, 4, 4, idx, 0, n - 1, w, 0, n, in, 0, n, out, 0,
                    n, n));
            rejected(d, out, 3 * n, e -> s.skinLbs4SoA(e, joints, 0, 4, 4, idx, 0, n, w, 0, -1, in, 0, n, out, 0, n,
                    n));
            rejected(d, out, 3 * n, e -> s.skinLbs4SoA(e, joints, 0, 4, 4, idx, 0, n, w, 0, n + 1, in, 0, n, out, 0,
                    n, n));
            // dual quaternions: eight planes
            rejected(d, out, 3 * n, e -> s.skinDualQuat4(e, dq, 0, 5, 5, idx, 0, w, 0, in, 0, n, out, 0, n, n));
            rejected(d, out, 3 * n, e -> s.skinDualQuat4(e, dq, 0, 4, 4, idx, 0, w, 0, in, 0, n, out, 0, n, n + 1));
            rejected(d, dq, 32, e -> s.skinDualQuat4SoA(e, dq, 0, 4, 4, idx, 0, n, w, 0, n, in, 0, n, dq, 0, 8, n));
            // palette: 12 floats per joint
            rejected(d, out, 3 * n, e -> s.skinLbs4MatrixPalette(e, palette, 0, 5, idx, 0, w, 0, in, 0, n, out, 0, n,
                    n));
            rejected(d, out, 3 * n, e -> s.skinLbs4MatrixPalette(e, palette, 1, 4, idx, 0, w, 0, in, 0, n, out, 0, n,
                    n));
            rejected(d, out, 3 * n, e -> s.skinLbs4MatrixPalette(e, palette, 0, 0, idx, 0, w, 0, in, 0, n, out, 0, n,
                    n));
            rejected(d, palette, 48, e -> s.skinLbs4MatrixPalette(e, palette, 0, 1, idx, 0, w, 0, in, 0, n, palette,
                    0, n, n));
            // palette building
            rejected(d, palette, 48, e -> s.buildMatrixPalette12(e, joints, 0, 4, palette, 0, 5));
            rejected(d, palette, 48, e -> s.buildMatrixPalette12(e, joints, 0, 4, palette, 1, 4));
            rejected(d, palette, 48, e -> s.buildRigidMatrixPalette12(e, joints, 0, 4, palette, 0, -1));
            rejected(d, palette, 48, e -> s.buildRigidMatrixPalette12(e, joints, 0, 3, palette, 0, 4));
            rejected(d, joints, 40, e -> s.buildRigidMatrixPalette12(e, joints, 0, 4, joints, 0, 3));
        }
    }

    @Test
    void packedAffineChecksSizesIndicesAndAliasing() {
        try (GpuDevice d = Devices.open("reference"); GpuPackedAffineKernels k = new GpuPackedAffineKernels(d)) {
            int n = 6;
            var q = upload(d, ones(4 * n));
            var t = upload(d, ones(3 * n));
            var out = upload(d, ones(12 * n));
            var order = uploadInts(d, new int[n]);
            var boxes = upload(d, ones(6 * n));
            var outBoxes = upload(d, ones(6 * n));
            rejected(d, out, 12 * n, e -> k.quatTranslationToPackedAffineBatch(e, q, 0, t, 0, n, out, 0, -1));
            rejected(d, out, 12 * n, e -> k.quatTranslationToPackedAffineBatch(e, q, 1, t, 0, n, out, 0, n));
            rejected(d, out, 12 * n, e -> k.quatTranslationToPackedAffineBatch(e, q, 0, t, 0, n - 1, out, 0, n));
            rejected(d, out, 12 * n, e -> k.quatTranslationToPackedAffineBatch(e, q, 0, t, 0, n, out, 12, n));
            rejected(d, q, 4 * n, e -> k.quatTranslationToPackedAffineBatch(e, q, 0, t, 0, 1, q, 0, 1));
            rejected(d, t, 3 * n, e -> k.quatTranslationToPackedAffineBatch(e, q, 0, t, 0, 1, t, 0, 1));
            // transformAabb
            rejected(d, outBoxes, 6 * n, e -> k.transformAabbPackedAffineBatch(e, n, out, 0, order, 0, boxes, 0, n,
                    outBoxes, 0, n, -1));
            rejected(d, outBoxes, 6 * n, e -> k.transformAabbPackedAffineBatch(e, -1, out, 0, order, 0, boxes, 0, n,
                    outBoxes, 0, n, 1));
            rejected(d, outBoxes, 6 * n, e -> k.transformAabbPackedAffineBatch(e, 0, out, 0, order, 0, boxes, 0, n,
                    outBoxes, 0, n, 1)); // every index out of range
            rejected(d, outBoxes, 6 * n, e -> k.transformAabbPackedAffineBatch(e, n + 1, out, 0, order, 0, boxes, 0,
                    n + 1, outBoxes, 0, n + 1, 1));
            rejected(d, outBoxes, 6 * n, e -> k.transformAabbPackedAffineBatch(e, n, out, 0, order, 1, boxes, 0, n,
                    outBoxes, 0, n, n));
            rejected(d, outBoxes, 6 * n, e -> k.transformAabbPackedAffineBatch(e, n, out, 0, order, 0, boxes, 0,
                    n - 1, outBoxes, 0, n, n));
            rejected(d, outBoxes, 6 * n, e -> k.transformAabbPackedAffineBatch(e, n, out, 0, order, 0, boxes, 0, n,
                    outBoxes, 1, n, n));
            rejected(d, boxes, 6 * n, e -> k.transformAabbPackedAffineBatch(e, n, out, 0, order, 0, boxes, 0, n,
                    boxes, 0, n, n)); // in place
            rejected(d, out, 12 * n, e -> k.transformAabbPackedAffineBatch(e, 1, out, 0, order, 0, boxes, 0, n,
                    out, 0, 1, 1));
            // upload
            var up = upload(d, ones(12 * n));
            rejected(d, up, 12 * n, e -> k.uploadPackedAffine(e, out, 0, n, order, 0, up, 0, -1));
            rejected(d, up, 12 * n, e -> k.uploadPackedAffine(e, out, 0, n + 1, order, 0, up, 0, n));
            rejected(d, up, 12 * n, e -> k.uploadPackedAffine(e, out, 0, 0, order, 0, up, 0, n));
            rejected(d, up, 12 * n, e -> k.uploadPackedAffine(e, out, 0, n, order, 0, up, 1, n));
            rejected(d, up, 12 * n, e -> k.uploadPackedAffine(e, out, 0, n, order, 1, up, 0, n));
            rejected(d, out, 12 * n, e -> k.uploadPackedAffine(e, out, 0, 1, order, 0, out, 12, 1));
            rejected(d, up, 12 * n, e -> k.uploadPackedAffine(e, out, 0, n, null, 0, up, 0, n));
        }
    }

    @Test
    void emptyWorkRecordsNothing() {
        try (GpuDevice d = Devices.open("reference"); GpuCullingKernels c = new GpuCullingKernels(d);
                GpuTransformKernels t = new GpuTransformKernels(d); GpuSkinningKernels s = new GpuSkinningKernels(d);
                GpuPackedAffineKernels k = new GpuPackedAffineKernels(d)) {
            var enc = d.encoder();
            var buf = Devices.upload(d, enc, ones(4));
            var other = Devices.upload(d, enc, ones(4));
            c.frustumCullAabbBatch(enc, new float[24], 0, other, 0, 0, buf, 0, 0);
            c.frustumCullAabbBatch(enc, new float[24], 0, other, 0, 0, buf, 0, -5); // as on the CPU: nothing
            t.composeBatch(enc, other, 0, 0, other, 0, 0, buf, 0, 0, 0);
            s.skinLbs4(enc, other, 0, 0, 0, other, 0, other, 0, other, 0, 0, buf, 0, 0, 0);
            s.skinDualQuat4SoA(enc, other, 0, 0, 0, other, 0, 0, other, 0, 0, other, 0, 0, buf, 0, 0, 0);
            s.skinLbs4MatrixPalette(enc, other, 0, 0, other, 0, other, 0, other, 0, 0, buf, 0, 0, 0);
            s.buildMatrixPalette12(enc, other, 0, 0, buf, 0, 0);
            k.quatTranslationToPackedAffineBatch(enc, other, 0, other, 0, 0, buf, 0, 0);
            k.transformAabbPackedAffineBatch(enc, 0, other, 0, other, 0, other, 0, 0, buf, 0, 0, 0);
            k.uploadPackedAffine(enc, other, 0, 0, other, 0, buf, 0, 0);
            var read = enc.download(buf);
            Devices.run(d, enc);
            assertArrayEquals(ones(4), Devices.floats(read, 4));
        }
    }

    private static GpuBuffer uploadInts(GpuDevice d, int[] data) {
        var enc = d.encoder();
        GpuBuffer b = Devices.upload(d, enc, data);
        Devices.run(d, enc);
        return b;
    }

    private static GpuBuffer upload(GpuDevice d, float[] data) {
        var enc = d.encoder();
        GpuBuffer b = Devices.upload(d, enc, data);
        Devices.run(d, enc);
        return b;
    }
}
