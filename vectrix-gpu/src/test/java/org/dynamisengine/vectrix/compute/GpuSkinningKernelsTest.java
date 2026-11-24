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
import java.util.SplittableRandom;
import java.util.stream.Stream;

import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.vectrix.soa.DualQuatSoA;
import org.dynamisengine.vectrix.soa.SkinningKernels;
import org.dynamisengine.vectrix.soa.TransformSoA;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link GpuSkinningKernels} against {@code SkinningKernels}, bit for bit, on every enabled backend. */
class GpuSkinningKernelsTest {

    /** What the output buffer holds before a dispatch; it must survive where nothing is written. */
    private static final float UNWRITTEN = 7.25f;

    /** The skinning methods. */
    enum Method { LBS, LBS_SOA, DUAL_QUAT, DUAL_QUAT_SOA, PALETTE, RIGID_PALETTE }

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (Method method : Method.values()) {
                for (int n : EngineData.COUNTS) {
                    out.add(Arguments.of(backend, method, n));
                }
            }
        }
        return out.stream();
    }

    static Stream<Arguments> paletteCases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (int n : new int[] {1, 5, 64, 65, 449, 3001}) {
                for (EngineData.Scale scale : EngineData.Scale.values()) {
                    out.add(Arguments.of(backend, n, scale));
                }
            }
        }
        return out.stream();
    }

    static Stream<String> backends() {
        return Devices.names().stream();
    }

    /** Four interleaved influences and a bind-pose position per vertex. */
    record Mesh(int n, int[] idx, float[] w, float[] x, float[] y, float[] z) {

        int[] jSlot(int k) {
            int[] a = new int[n];
            for (int i = 0; i < n; i++) {
                a[i] = idx[4 * i + k];
            }
            return a;
        }

        float[] wSlot(int k) {
            float[] a = new float[n];
            for (int i = 0; i < n; i++) {
                a[i] = w[4 * i + k];
            }
            return a;
        }
    }

    /** Weights of every kind: one influence, all -0, normalized, unnormalized, negative, repeated joints. */
    static Mesh mesh(SplittableRandom r, int n, int jointCount) {
        int[] idx = new int[4 * n];
        float[] w = new float[4 * n];
        float[] x = new float[n];
        float[] y = new float[n];
        float[] z = new float[n];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < 4; k++) {
                idx[4 * i + k] = r.nextInt(jointCount);
            }
            int kind = r.nextInt(8);
            for (int k = 0; k < 4; k++) {
                w[4 * i + k] = switch (kind) {
                    case 0 -> k == 0 ? 1f : 0f;
                    case 1 -> -0f;
                    case 2 -> 0.25f;
                    case 3 -> EngineData.uniform(r, -0.5f, 1.5f);
                    default -> EngineData.uniform(r, 0f, 1f);
                };
            }
            if (kind == 4) {
                idx[4 * i + 1] = idx[4 * i]; // the same joint twice
            }
            x[i] = r.nextInt(32) == 0 ? -0f : EngineData.uniform(r, -2, 2);
            y[i] = EngineData.uniform(r, -2, 2);
            z[i] = r.nextInt(32) == 0 ? 0f : EngineData.uniform(r, -2, 2);
        }
        return new Mesh(n, idx, w, x, y, z);
    }

    /** Joints as dual quaternions, a third of them negated (the other hemisphere), so the sign flips are used. */
    static DualQuatSoA dualQuats(SplittableRandom r, int jointCount) {
        DualQuatSoA d = EngineData.dualQuats(r, jointCount);
        for (int j = 0; j < jointCount; j += 3) {
            for (float[] f : EngineModels.fields(d)) {
                f[j] = -f[j];
            }
        }
        return d;
    }

    /** An int buffer with {@code planes} {@code stride} apart from {@code off}. */
    static int[] packInts(int[][] planes, int n, int off, int stride) {
        int[] a = new int[off + (planes.length - 1) * stride + n + 3];
        for (int f = 0; f < planes.length; f++) {
            System.arraycopy(planes[f], 0, a, off + f * stride, n);
        }
        return a;
    }

    /** An int buffer holding {@code v} from {@code off}. */
    static int[] at(int[] v, int off) {
        int[] a = new int[off + v.length + 2];
        System.arraycopy(v, 0, a, off, v.length);
        return a;
    }

    /** A float buffer holding {@code v} from {@code off}. */
    static float[] at(float[] v, int off) {
        float[] a = EngineData.filled(off + v.length + 2, Float.NaN);
        System.arraycopy(v, 0, a, off, v.length);
        return a;
    }

    /** One GPU skinning call, given the device buffers. */
    interface Call {
        void record(GpuSkinningKernels k, GpuCommandEncoder enc, GpuBuffer joints, GpuBuffer idx, GpuBuffer w,
                GpuBuffer in, GpuBuffer out);
    }

    private static final int JOINTS_OFF = 3;
    private static final int IDX_OFF = 1;
    private static final int W_OFF = 6;
    private static final int IN_OFF = 4;
    private static final int OUT_OFF = 1;

    /**
     * Runs {@code call} and compares the whole output buffer with {@code expected} (the CPU positions, or null where
     * the GPU must leave the buffer unchanged).
     */
    static void run(String backend, String what, float[] joints, int[] idx, float[] w, Mesh mesh, float[][] expected,
            boolean[] written, Call call) {
        int n = mesh.n();
        int inStride = n + 2;
        int outStride = n + 3;
        float[] in = EngineData.pack(new float[][] {mesh.x(), mesh.y(), mesh.z()}, n, IN_OFF, inStride, 2, Float.NaN);
        float[] out = EngineData.filled(OUT_OFF + 2 * outStride + n + 2, UNWRITTEN);
        float[] want = out.clone();
        for (int i = 0; i < n; i++) {
            if (written[i]) {
                for (int axis = 0; axis < 3; axis++) {
                    want[OUT_OFF + axis * outStride + i] = expected[axis][i];
                }
            }
        }
        try (GpuDevice device = Devices.open(backend); GpuSkinningKernels k = new GpuSkinningKernels(device);
                var enc = device.encoder()) {
            var gj = Devices.upload(device, enc, joints);
            var gi = Devices.upload(device, enc, idx);
            var gw = Devices.upload(device, enc, w);
            var gin = Devices.upload(device, enc, in);
            var gout = Devices.upload(device, enc, out);
            call.record(k, enc, gj, gi, gw, gin, gout);
            var read = enc.download(gout);
            Devices.run(device, enc);
            Bits.assertSame(want, Devices.floats(read, out.length), Devices.hardware(backend), what);
        }
    }

    static boolean[] all(int n) {
        boolean[] b = new boolean[n];
        java.util.Arrays.fill(b, true);
        return b;
    }

    /** Runs {@code method} on {@code mesh}, whose joint indices may include invalid ones (marked in {@code valid}). */
    static void check(String backend, Method method, Mesh mesh, int jointCount, SplittableRandom r, boolean[] valid,
            float[][] expected, TransformSoA joints, DualQuatSoA dq, float[] palette) {
        int n = mesh.n();
        int jointStride = jointCount + 5;
        int[] idxSoA = packInts(new int[][] {mesh.jSlot(0), mesh.jSlot(1), mesh.jSlot(2), mesh.jSlot(3)}, n, 2, n + 1);
        float[] wSoA = EngineData.pack(new float[][] {mesh.wSlot(0), mesh.wSlot(1), mesh.wSlot(2), mesh.wSlot(3)}, n,
                3, n + 2, 1, Float.NaN);
        String what = backend + " " + method + " n=" + n;
        int inStride = n + 2;
        int outStride = n + 3;
        switch (method) {
            case LBS -> run(backend, what, EngineData.pack(EngineModels.fields(joints), jointCount, JOINTS_OFF,
                    jointStride, 2, Float.NaN), at(mesh.idx(), IDX_OFF), at(mesh.w(), W_OFF), mesh, expected, valid,
                    (k, enc, gj, gi, gw, gin, gout) -> k.skinLbs4(enc, gj, JOINTS_OFF, jointStride, jointCount, gi,
                            IDX_OFF, gw, W_OFF, gin, IN_OFF, inStride, gout, OUT_OFF, outStride, n));
            case LBS_SOA -> run(backend, what, EngineData.pack(EngineModels.fields(joints), jointCount, JOINTS_OFF,
                    jointStride, 2, Float.NaN), idxSoA, wSoA, mesh, expected, valid,
                    (k, enc, gj, gi, gw, gin, gout) -> k.skinLbs4SoA(enc, gj, JOINTS_OFF, jointStride, jointCount,
                            gi, 2, n + 1, gw, 3, n + 2, gin, IN_OFF, inStride, gout, OUT_OFF, outStride, n));
            case DUAL_QUAT -> run(backend, what, EngineData.pack(EngineModels.fields(dq), jointCount, JOINTS_OFF,
                    jointStride, 2, Float.NaN), at(mesh.idx(), IDX_OFF), at(mesh.w(), W_OFF), mesh, expected, valid,
                    (k, enc, gj, gi, gw, gin, gout) -> k.skinDualQuat4(enc, gj, JOINTS_OFF, jointStride, jointCount,
                            gi, IDX_OFF, gw, W_OFF, gin, IN_OFF, inStride, gout, OUT_OFF, outStride, n));
            case DUAL_QUAT_SOA -> run(backend, what, EngineData.pack(EngineModels.fields(dq), jointCount, JOINTS_OFF,
                    jointStride, 2, Float.NaN), idxSoA, wSoA, mesh, expected, valid,
                    (k, enc, gj, gi, gw, gin, gout) -> k.skinDualQuat4SoA(enc, gj, JOINTS_OFF, jointStride,
                            jointCount, gi, 2, n + 1, gw, 3, n + 2, gin, IN_OFF, inStride, gout, OUT_OFF, outStride,
                            n));
            case PALETTE, RIGID_PALETTE -> run(backend, what, at(palette, JOINTS_OFF), at(mesh.idx(), IDX_OFF),
                    at(mesh.w(), W_OFF), mesh, expected, valid,
                    (k, enc, gj, gi, gw, gin, gout) -> k.skinLbs4MatrixPalette(enc, gj, JOINTS_OFF, jointCount, gi,
                            IDX_OFF, gw, W_OFF, gin, IN_OFF, inStride, gout, OUT_OFF, outStride, n));
            default -> throw new AssertionError(method);
        }
    }

    /** The CPU result of {@code method} for the vertices of {@code mesh} (all indices valid). */
    static float[][] cpu(Method method, Mesh mesh, TransformSoA joints, DualQuatSoA dq, float[] palette) {
        int n = mesh.n();
        float[][] o = new float[3][n];
        switch (method) {
            case LBS -> SkinningKernels.skinLbs4(joints, mesh.idx(), mesh.w(), mesh.x(), mesh.y(), mesh.z(), o[0],
                    o[1], o[2], n);
            case LBS_SOA -> SkinningKernels.skinLbs4SoA(joints, mesh.jSlot(0), mesh.jSlot(1), mesh.jSlot(2),
                    mesh.jSlot(3), mesh.wSlot(0), mesh.wSlot(1), mesh.wSlot(2), mesh.wSlot(3), mesh.x(), mesh.y(),
                    mesh.z(), o[0], o[1], o[2], n);
            case DUAL_QUAT -> SkinningKernels.skinDualQuat4(dq, mesh.idx(), mesh.w(), mesh.x(), mesh.y(), mesh.z(),
                    o[0], o[1], o[2], n);
            case DUAL_QUAT_SOA -> SkinningKernels.skinDualQuat4SoA(dq, mesh.jSlot(0), mesh.jSlot(1), mesh.jSlot(2),
                    mesh.jSlot(3), mesh.wSlot(0), mesh.wSlot(1), mesh.wSlot(2), mesh.wSlot(3), mesh.x(), mesh.y(),
                    mesh.z(), o[0], o[1], o[2], n);
            case PALETTE, RIGID_PALETTE -> SkinningKernels.skinLbs4MatrixPalette(palette, mesh.idx(), mesh.w(),
                    mesh.x(), mesh.y(), mesh.z(), o[0], o[1], o[2], n);
            default -> throw new AssertionError(method);
        }
        return o;
    }

    static float[] palette(Method method, TransformSoA joints, int jointCount) {
        float[] p = new float[12 * jointCount];
        if (method == Method.RIGID_PALETTE) {
            SkinningKernels.buildRigidMatrixPalette12(joints, p, jointCount);
        } else {
            SkinningKernels.buildMatrixPalette12(joints, p, jointCount);
        }
        return p;
    }

    @ParameterizedTest(name = "{0} {1} n={2}")
    @MethodSource("cases")
    void matchesSkinningKernels(String backend, Method method, int n) {
        SplittableRandom r = new SplittableRandom(n * 31L + method.ordinal());
        int jointCount = 1 + r.nextInt(80);
        EngineData.Scale scale = EngineData.Scale.values()[n % 3];
        TransformSoA joints = EngineData.transforms(r, jointCount, scale);
        DualQuatSoA dq = dualQuats(r, jointCount);
        float[] palette = palette(method, joints, jointCount);
        Mesh mesh = mesh(r, n, jointCount);
        check(backend, method, mesh, jointCount, r, all(n), cpu(method, mesh, joints, dq, palette), joints, dq,
                palette);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void verticesWithAnInvalidJointIndexAreLeftUnchanged(String backend) {
        SplittableRandom r = new SplittableRandom(99);
        int n = 500;
        int jointCount = 20;
        TransformSoA joints = EngineData.transforms(r, jointCount, EngineData.Scale.NON_UNIFORM);
        DualQuatSoA dq = dualQuats(r, jointCount);
        Mesh mesh = mesh(r, n, jointCount);
        boolean[] valid = all(n);
        int[] bad = {-1, jointCount, Integer.MIN_VALUE, Integer.MAX_VALUE, jointCount + 1000};
        for (int i = 3; i < n; i += 7) {
            mesh.idx()[4 * i + r.nextInt(4)] = bad[r.nextInt(bad.length)];
            valid[i] = false;
        }
        // the CPU result of the valid vertices alone
        int[] keep = java.util.stream.IntStream.range(0, n).filter(i -> valid[i]).toArray();
        Mesh good = new Mesh(keep.length, new int[4 * keep.length], new float[4 * keep.length],
                new float[keep.length], new float[keep.length], new float[keep.length]);
        for (int v = 0; v < keep.length; v++) {
            int i = keep[v];
            System.arraycopy(mesh.idx(), 4 * i, good.idx(), 4 * v, 4);
            System.arraycopy(mesh.w(), 4 * i, good.w(), 4 * v, 4);
            good.x()[v] = mesh.x()[i];
            good.y()[v] = mesh.y()[i];
            good.z()[v] = mesh.z()[i];
        }
        for (Method method : Method.values()) {
            float[] palette = palette(method, joints, jointCount);
            float[][] part = cpu(method, good, joints, dq, palette);
            float[][] expected = new float[3][n];
            for (int v = 0; v < keep.length; v++) {
                for (int axis = 0; axis < 3; axis++) {
                    expected[axis][keep[v]] = part[axis][v];
                }
            }
            check(backend, method, mesh, jointCount, r, valid, expected, joints, dq, palette);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void identityAndZeroWeightBlends(String backend) {
        SplittableRandom r = new SplittableRandom(5);
        int n = 300;
        int jointCount = 4;
        TransformSoA joints = new TransformSoA(jointCount); // identities
        DualQuatSoA dq = new DualQuatSoA(jointCount); // identities
        dq.rw[1] = -1f; // the identity in the other hemisphere
        Mesh mesh = mesh(r, n, jointCount);
        for (int i = 0; i < n; i += 5) {
            for (int k = 0; k < 4; k++) {
                mesh.w()[4 * i + k] = k % 2 == 0 ? 0f : -0f; // blends to an exact zero real part: copied
            }
        }
        for (int i = 1; i < n; i += 5) {
            mesh.idx()[4 * i] = 0;
            mesh.idx()[4 * i + 1] = 1;
            mesh.w()[4 * i] = 0.5f;
            mesh.w()[4 * i + 1] = 0.5f; // q and -q with equal weights: flipped, not cancelled
        }
        for (Method method : Method.values()) {
            float[] palette = palette(method, joints, jointCount);
            check(backend, method, mesh, jointCount, r, all(n), cpu(method, mesh, joints, dq, palette), joints, dq,
                    palette);
        }
    }

    @ParameterizedTest(name = "{0} joints={1} {2}")
    @MethodSource("paletteCases")
    void palettesMatchBuildMatrixPalette12(String backend, int jointCount, EngineData.Scale scale) {
        SplittableRandom r = new SplittableRandom(jointCount * 13L + scale.ordinal());
        TransformSoA joints = EngineData.transforms(r, jointCount, scale);
        if (jointCount > 1) {
            joints.qx[1] = 2f; // not unit: used as given
        }
        int jointsOff = 2;
        int stride = jointCount + 7;
        int palOff = 5;
        float[] jb = EngineData.pack(EngineModels.fields(joints), jointCount, jointsOff, stride, 3, Float.NaN);
        for (boolean scaled : new boolean[] {true, false}) {
            float[] expected = new float[12 * jointCount];
            if (scaled) {
                SkinningKernels.buildMatrixPalette12(joints, expected, jointCount);
            } else {
                SkinningKernels.buildRigidMatrixPalette12(joints, expected, jointCount);
            }
            float[] pal = EngineData.filled(palOff + 12 * jointCount + 4, UNWRITTEN);
            float[] want = pal.clone();
            System.arraycopy(expected, 0, want, palOff, expected.length);
            try (GpuDevice device = Devices.open(backend); GpuSkinningKernels k = new GpuSkinningKernels(device);
                    var enc = device.encoder()) {
                var gj = Devices.upload(device, enc, jb);
                var gp = Devices.upload(device, enc, pal);
                if (scaled) {
                    k.buildMatrixPalette12(enc, gj, jointsOff, stride, gp, palOff, jointCount);
                } else {
                    k.buildRigidMatrixPalette12(enc, gj, jointsOff, stride, gp, palOff, jointCount);
                }
                var read = enc.download(gp);
                Devices.run(device, enc);
                Bits.assertSame(want, Devices.floats(read, pal.length), Devices.hardware(backend),
                        backend + " palette scaled=" + scaled + " joints=" + jointCount + " " + scale);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void paletteWithShearIsUsedAsGiven(String backend) {
        SplittableRandom r = new SplittableRandom(3);
        int jointCount = 9;
        int n = 777;
        float[] palette = new float[12 * jointCount];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = EngineData.uniform(r, -3, 3);
        }
        Mesh mesh = mesh(r, n, jointCount);
        float[][] expected = cpu(Method.PALETTE, mesh, null, null, palette);
        check(backend, Method.PALETTE, mesh, jointCount, r, all(n), expected, null, null, palette);
    }
}
