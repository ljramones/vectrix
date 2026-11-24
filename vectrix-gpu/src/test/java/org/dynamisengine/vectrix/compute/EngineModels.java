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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.affine.PackedAffineKernels;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.geometry.CullingKernels;
import org.dynamisengine.vectrix.geometry.FrustumPlanes;
import org.dynamisengine.vectrix.soa.AABBSoA;
import org.dynamisengine.vectrix.soa.DualQuatSoA;
import org.dynamisengine.vectrix.soa.SkinningKernels;
import org.dynamisengine.vectrix.soa.TransformKernels;
import org.dynamisengine.vectrix.soa.TransformSoA;

/**
 * Reference models of the engine kernels (culling, transforms, skinning, packed affine): each unpacks the dispatch's
 * SoA buffers into the Vectrix types, runs the Vectrix method and packs the result back. Indices that the kernels
 * skip (joint indices or {@code order} entries out of range) are left out of the CPU call, so the model writes what
 * the kernel writes.
 */
final class EngineModels {

    private EngineModels() {
    }

    static void register(Map<String, ReferenceModels.ReferenceModel> m) {
        m.put("cull_aabb", EngineModels::cull);
        m.put("transform_compose", EngineModels::compose);
        m.put("skin_lbs", c -> skin(c, false));
        m.put("skin_dq", c -> skin(c, true));
        m.put("skin_palette", EngineModels::skinPalette);
        m.put("palette_build", EngineModels::buildPalette);
        m.put("affine_quat_translation", EngineModels::quatTranslation);
        m.put("affine_aabb", EngineModels::transformAabb);
        m.put("affine_upload", EngineModels::upload);
    }

    /** The field arrays of a TransformSoA, in the GPU plane order. */
    static float[][] fields(TransformSoA t) {
        return new float[][] {t.tx, t.ty, t.tz, t.qx, t.qy, t.qz, t.qw, t.sx, t.sy, t.sz};
    }

    /** The field arrays of a DualQuatSoA, in the GPU plane order. */
    static float[][] fields(DualQuatSoA d) {
        return new float[][] {d.rx, d.ry, d.rz, d.rw, d.dx, d.dy, d.dz, d.dw};
    }

    /** The field arrays of an AABBSoA, in the GPU plane order. */
    static float[][] fields(AABBSoA b) {
        return new float[][] {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ};
    }

    /** Copies {@code n} elements of each plane of {@code src} (from {@code off}, {@code stride} apart) out. */
    static void unpack(float[] src, int off, int stride, float[][] planes, int n) {
        for (int f = 0; f < planes.length; f++) {
            System.arraycopy(src, off + f * stride, planes[f], 0, n);
        }
    }

    /** Copies {@code n} elements of each plane into {@code dest} (from {@code off}, {@code stride} apart). */
    static void pack(float[][] planes, int n, float[] dest, int off, int stride) {
        for (int f = 0; f < planes.length; f++) {
            System.arraycopy(planes[f], 0, dest, off + f * stride, n);
        }
    }

    private static int groups(int n) {
        return (n + EngineBuffers.GROUP - 1) / EngineBuffers.GROUP;
    }

    private static void cull(ReferenceModels.Call c) {
        int n = c.i("count");
        c.requireLinearGrid(groups(n));
        FrustumPlanes fp = new FrustumPlanes();
        for (int k = 0; k < FrustumPlanes.COUNT; k++) {
            fp.x[k] = c.f("x" + k);
            fp.y[k] = c.f("y" + k);
            fp.z[k] = c.f("z" + k);
            fp.w[k] = c.f("w" + k);
        }
        AABBSoA boxes = new AABBSoA(n);
        unpack(c.floats(0), c.i("boundsOff"), c.i("boundsStride"), fields(boxes), n);
        int[] out = new int[n];
        CullingKernels.frustumCullAabbBatch(fp, boxes, out, n);
        int[] result = c.ints(1);
        System.arraycopy(out, 0, result, c.i("resultOff"), n);
        c.put(1, result);
    }

    private static void compose(ReferenceModels.Call c) {
        int n = c.i("count");
        c.requireLinearGrid(groups(n));
        TransformSoA parents = new TransformSoA(n);
        TransformSoA locals = new TransformSoA(n);
        TransformSoA out = new TransformSoA(n);
        unpack(c.floats(0), c.i("parentsOff"), c.i("parentsStride"), fields(parents), n);
        unpack(c.floats(1), c.i("localsOff"), c.i("localsStride"), fields(locals), n);
        TransformKernels.composeBatch(parents, locals, out, n);
        float[] dest = c.floats(2);
        pack(fields(out), n, dest, c.i("outOff"), c.i("outStride"));
        c.put(2, dest);
    }

    /** The vertices of a skinning dispatch whose joint indices are all valid, with their influences. */
    private record Influences(int[] vertices, int[] j, float[] w) {

        static Influences read(ReferenceModels.Call c, int count, int jointCount) {
            int[] idx = c.ints(1);
            float[] wts = c.floats(2);
            int idxOff = c.i("idxOff");
            int idxSlot = c.i("idxSlot");
            int idxStep = c.i("idxStep");
            int wOff = c.i("wOff");
            int wSlot = c.i("wSlot");
            int wStep = c.i("wStep");
            List<Integer> valid = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                boolean ok = true;
                for (int k = 0; k < 4; k++) {
                    int j = idx[idxOff + k * idxSlot + i * idxStep];
                    ok &= j >= 0 && j < jointCount;
                }
                if (ok) {
                    valid.add(i);
                }
            }
            int n = valid.size();
            int[] vertices = new int[n];
            int[] j = new int[4 * n];
            float[] w = new float[4 * n];
            for (int v = 0; v < n; v++) {
                int i = valid.get(v);
                vertices[v] = i;
                for (int k = 0; k < 4; k++) {
                    j[4 * v + k] = idx[idxOff + k * idxSlot + i * idxStep];
                    w[4 * v + k] = wts[wOff + k * wSlot + i * wStep];
                }
            }
            return new Influences(vertices, j, w);
        }

        /** Slot {@code k} of every vertex, for the per-slot CPU methods. */
        int[] jSlot(int k) {
            int[] a = new int[vertices.length];
            for (int v = 0; v < a.length; v++) {
                a[v] = j[4 * v + k];
            }
            return a;
        }

        float[] wSlot(int k) {
            float[] a = new float[vertices.length];
            for (int v = 0; v < a.length; v++) {
                a[v] = w[4 * v + k];
            }
            return a;
        }
    }

    /** Positions of the vertices of {@code inf}, as three arrays. */
    private static float[][] positions(ReferenceModels.Call c, Influences inf) {
        float[] in = c.floats(3);
        int off = c.i("inOff");
        int stride = c.i("inStride");
        int n = inf.vertices().length;
        float[][] p = new float[3][n];
        for (int v = 0; v < n; v++) {
            for (int axis = 0; axis < 3; axis++) {
                p[axis][v] = in[off + axis * stride + inf.vertices()[v]];
            }
        }
        return p;
    }

    /** Writes the skinned positions of the vertices of {@code inf} to slot 4. */
    private static void scatter(ReferenceModels.Call c, Influences inf, float[][] out) {
        float[] dest = c.floats(4);
        int off = c.i("outOff");
        int stride = c.i("outStride");
        for (int v = 0; v < inf.vertices().length; v++) {
            for (int axis = 0; axis < 3; axis++) {
                dest[off + axis * stride + inf.vertices()[v]] = out[axis][v];
            }
        }
        c.put(4, dest);
    }

    private static boolean interleaved(ReferenceModels.Call c) {
        return c.i("idxSlot") == 1 && c.i("idxStep") == 4 && c.i("wSlot") == 1 && c.i("wStep") == 4;
    }

    private static void skin(ReferenceModels.Call c, boolean dualQuat) {
        int count = c.i("count");
        int jointCount = c.i("jointCount");
        c.requireLinearGrid(groups(count));
        Influences inf = Influences.read(c, count, jointCount);
        float[][] p = positions(c, inf);
        int n = inf.vertices().length;
        float[][] o = new float[3][n];
        boolean il = interleaved(c);
        if (dualQuat) {
            DualQuatSoA joints = new DualQuatSoA(jointCount);
            unpack(c.floats(0), c.i("jointsOff"), c.i("jointStride"), fields(joints), jointCount);
            if (il) {
                SkinningKernels.skinDualQuat4(joints, inf.j(), inf.w(), p[0], p[1], p[2], o[0], o[1], o[2], n);
            } else {
                SkinningKernels.skinDualQuat4SoA(joints, inf.jSlot(0), inf.jSlot(1), inf.jSlot(2), inf.jSlot(3),
                        inf.wSlot(0), inf.wSlot(1), inf.wSlot(2), inf.wSlot(3), p[0], p[1], p[2], o[0], o[1], o[2],
                        n);
            }
        } else {
            TransformSoA joints = new TransformSoA(jointCount);
            unpack(c.floats(0), c.i("jointsOff"), c.i("jointStride"), fields(joints), jointCount);
            if (il) {
                SkinningKernels.skinLbs4(joints, inf.j(), inf.w(), p[0], p[1], p[2], o[0], o[1], o[2], n);
            } else {
                SkinningKernels.skinLbs4SoA(joints, inf.jSlot(0), inf.jSlot(1), inf.jSlot(2), inf.jSlot(3),
                        inf.wSlot(0), inf.wSlot(1), inf.wSlot(2), inf.wSlot(3), p[0], p[1], p[2], o[0], o[1], o[2],
                        n);
            }
        }
        scatter(c, inf, o);
    }

    private static void skinPalette(ReferenceModels.Call c) {
        int count = c.i("count");
        int jointCount = c.i("jointCount");
        c.requireLinearGrid(groups(count));
        c.require(interleaved(c), "the palette kernel is dispatched with interleaved influences");
        Influences inf = Influences.read(c, count, jointCount);
        float[][] p = positions(c, inf);
        int n = inf.vertices().length;
        float[][] o = new float[3][n];
        float[] palette = new float[12 * jointCount];
        System.arraycopy(c.floats(0), c.i("palOff"), palette, 0, palette.length);
        SkinningKernels.skinLbs4MatrixPalette(palette, inf.j(), inf.w(), p[0], p[1], p[2], o[0], o[1], o[2], n);
        scatter(c, inf, o);
    }

    private static void buildPalette(ReferenceModels.Call c) {
        int jointCount = c.i("jointCount");
        c.requireLinearGrid(groups(jointCount));
        TransformSoA joints = new TransformSoA(jointCount);
        unpack(c.floats(0), c.i("jointsOff"), c.i("jointStride"), fields(joints), jointCount);
        float[] palette = new float[12 * jointCount];
        if (c.u("scaled") != 0) {
            SkinningKernels.buildMatrixPalette12(joints, palette, jointCount);
        } else {
            SkinningKernels.buildRigidMatrixPalette12(joints, palette, jointCount);
        }
        float[] dest = c.floats(1);
        System.arraycopy(palette, 0, dest, c.i("palOff"), palette.length);
        c.put(1, dest);
    }

    private static void quatTranslation(ReferenceModels.Call c) {
        int n = c.i("count");
        c.requireLinearGrid(groups(n));
        float[] q = c.floats(0);
        int qOff = c.i("qOff");
        Quaternionf[] rotation = new Quaternionf[n];
        for (int i = 0; i < n; i++) {
            rotation[i] = new Quaternionf(q[qOff + 4 * i], q[qOff + 4 * i + 1], q[qOff + 4 * i + 2],
                    q[qOff + 4 * i + 3]);
        }
        float[][] t = new float[3][n];
        unpack(c.floats(1), c.i("tOff"), c.i("tStride"), t, n);
        PackedAffineArray out = new PackedAffineArray(n);
        PackedAffineKernels.quatTranslationToPackedAffineBatch(rotation, t[0], t[1], t[2], out, n);
        float[] dest = c.floats(2);
        System.arraycopy(out.raw(), 0, dest, c.i("outOff"), out.raw().length);
        c.put(2, dest);
    }

    /** The first {@code count} indices of {@code order} from {@code orderOff} that are in {@code [0, limit)}. */
    private static int[] validOrder(ReferenceModels.Call c, int count, int limit) {
        int[] order = c.ints(1);
        int off = c.i("orderOff");
        return java.util.stream.IntStream.range(0, count).map(i -> order[off + i]).filter(k -> k >= 0 && k < limit)
                .toArray();
    }

    private static void transformAabb(ReferenceModels.Call c) {
        int count = c.i("count");
        int entries = c.i("entries");
        c.requireLinearGrid(groups(count));
        float[] raw = new float[12 * entries];
        System.arraycopy(c.floats(0), c.i("tOff"), raw, 0, raw.length);
        PackedAffineArray transforms = new PackedAffineArray(raw);
        int[] order = validOrder(c, count, entries);
        AABBSoA in = new AABBSoA(entries);
        AABBSoA out = new AABBSoA(entries);
        float[] dest = c.floats(3);
        unpack(c.floats(2), c.i("inOff"), c.i("inStride"), fields(in), entries);
        unpack(dest, c.i("outOff"), c.i("outStride"), fields(out), entries);
        PackedAffineKernels.transformAabbPackedAffineBatch(transforms, order, in.minX, in.minY, in.minZ, in.maxX,
                in.maxY, in.maxZ, out.minX, out.minY, out.minZ, out.maxX, out.maxY, out.maxZ, order.length);
        pack(fields(out), entries, dest, c.i("outOff"), c.i("outStride"));
        c.put(3, dest);
    }

    private static void upload(ReferenceModels.Call c) {
        int count = c.i("count");
        int transformCount = c.i("transformCount");
        c.requireLinearGrid(groups(count));
        float[] raw = new float[12 * transformCount];
        System.arraycopy(c.floats(0), c.i("tOff"), raw, 0, raw.length);
        PackedAffineArray transforms = new PackedAffineArray(raw);
        int[] order = c.ints(1);
        int orderOff = c.i("orderOff");
        float[] dest = c.floats(2);
        int outOff = c.i("outOff");
        for (int i = 0; i < count; i++) {
            int k = order[orderOff + i];
            if (k >= 0 && k < transformCount) {
                PackedAffineKernels.uploadPackedAffine(transforms, new int[] {k}, dest, outOff + 12 * i, 1);
            }
        }
        c.put(2, dest);
    }
}
