/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
 */
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.curve.CurveReparameterizer3d;
import org.dynamisengine.vectrix.curve.CurveReparameterizer3f;
import org.dynamisengine.vectrix.curve.scalar.UniformBSpline1d;
import org.dynamisengine.vectrix.curve.scalar.UniformBSpline1f;
import org.dynamisengine.vectrix.curve.vec3.Bezier3f;
import org.dynamisengine.vectrix.curve.vec3.UniformBSpline3f;
import org.dynamisengine.vectrix.gpu.Half;
import org.dynamisengine.vectrix.gpu.OctaNormal;
import org.dynamisengine.vectrix.gpu.PackedFormats;
import org.dynamisengine.vectrix.gpu.QuatCompression;
import org.dynamisengine.vectrix.nn.Attention;
import org.dynamisengine.vectrix.nn.Conv2d;
import org.dynamisengine.vectrix.nn.Formats;
import org.dynamisengine.vectrix.nn.Norms;
import org.dynamisengine.vectrix.nn.Pool2d;
import org.dynamisengine.vectrix.nn.Sampler;
import org.dynamisengine.vectrix.soa.SkinningKernels;
import org.dynamisengine.vectrix.soa.TransformKernels;
import org.dynamisengine.vectrix.soa.TransformSoA;
import org.junit.jupiter.api.Test;

/** The 3.0 fixes in {@code gpu}, {@code soa}, {@code nn} and {@code curve}; each test names the old behaviour. */
class GpuSoaNnCurveFixesTest {

    // ---- gpu codecs ----

    @Test
    void quatCompressionEncodesRotationlessInputsAsIdentity() {
        Quaternionf d = new Quaternionf();
        float nan = Float.NaN, inf = Float.POSITIVE_INFINITY;
        for (Quaternionf q : new Quaternionf[] {new Quaternionf(0, 0, 0, 0), new Quaternionf(nan, 0, 0, 1),
            new Quaternionf(0, 0, nan, 0), new Quaternionf(inf, 0, 0, 1), new Quaternionf(0, -inf, 0, 0)}) {
            long code = QuatCompression.packSmallest3(q);
            assertEquals(QuatCompression.IDENTITY, code, q.toString()); // was 0, which decodes to a half turn about x
            QuatCompression.unpackSmallest3(code, d);
            assertEquals(new Quaternionf(0, 0, 0, 1), d);
        }
        // tiny and huge quaternions are the same rotation as their unit form
        Quaternionf unit = new Quaternionf().rotationXYZ(0.3f, -1.1f, 0.7f);
        long expected = QuatCompression.packSmallest3(unit);
        for (float s : new float[] {1e-30f, 1e-36f, 1e25f, 1e38f}) {
            Quaternionf scaled = new Quaternionf(unit.x * s, unit.y * s, unit.z * s, unit.w * s);
            Quaternionf back = QuatCompression.unpackSmallest3(QuatCompression.packSmallest3(scaled), new Quaternionf());
            assertTrue(QuatCompression.angularErrorDegrees(unit, back) < 0.01f, "scale " + s);
        }
        assertEquals(expected, QuatCompression.packSmallest3(new Quaternionf(unit.x * 4, unit.y * 4, unit.z * 4,
                unit.w * 4)));
    }

    @Test
    void octaNormalEncodesDirectionlessInputsAsUp() {
        Vector3f d = new Vector3f();
        float nan = Float.NaN, inf = Float.POSITIVE_INFINITY;
        float[][] bad = {{0, 0, 0}, {-0f, 0, -0f}, {nan, 0, 1}, {0, nan, 0}, {inf, 0, 0}, {1, -inf, 0}};
        for (float[] v : bad) {
            assertEquals(OctaNormal.UP, OctaNormal.encodeSnorm16(v[0], v[1], v[2]), Arrays.toString(v));
        }
        assertEquals(new Vector3f(0, 0, 1), OctaNormal.decodeSnorm16(OctaNormal.UP, d));
        // a subnormal vector was 1/l1 = infinity, giving NaN coordinates; a huge one overflowed the L1 norm
        OctaNormal.decodeSnorm16(OctaNormal.encodeSnorm16(1e-40f, 0, 0), d);
        assertEquals(1f, d.x, 1e-6f);
        OctaNormal.decodeSnorm16(OctaNormal.encodeSnorm16(-3e38f, 3e38f, 0), d);
        assertEquals(-0.70710677f, d.x, 1e-4f);
        assertEquals(0.70710677f, d.y, 1e-4f);
        assertEquals(OctaNormal.encodeSnorm16(0.3f, -0.5f, -0.8f), OctaNormal.encodeSnorm16(0.6f, -1f, -1.6f));
    }

    @Test
    void qTangentValidatesBitsAndHandlesDegenerateFrames() {
        Vector3f n = new Vector3f(0, 1, 0);
        Vector3f t = new Vector3f(1, 0, 0);
        Quaternionf q = new Quaternionf();
        for (int bits : new int[] {Integer.MIN_VALUE, -1, 0, 1, 32, 64}) {
            assertThrows(IllegalArgumentException.class, () -> PackedFormats.qTangent(n, t, 1, bits, q), "bits " + bits);
        }
        PackedFormats.qTangent(n, t, 1, 2, q); // the extremes are valid
        PackedFormats.qTangent(n, t, 1, 31, q);
        Vector3f nOut = new Vector3f();
        Vector3f tOut = new Vector3f();
        float nan = Float.NaN;
        Vector3f[][] frames = {
            {new Vector3f(0, 0, 0), new Vector3f(1, 0, 0)}, // zero normal: was NaN
            {new Vector3f(nan, 0, 1), new Vector3f(1, 0, 0)},
            {new Vector3f(0, 2, 0), new Vector3f(0, -3, 0)}, // tangent parallel to the normal: was NaN
            {new Vector3f(0.3f, 0.4f, -0.8f), new Vector3f(0, 0, 0)}, // zero tangent
            {new Vector3f(0.3f, 0.4f, -0.8f), new Vector3f(nan, 1, 0)},
            {new Vector3f(0, 0, -1), new Vector3f(0, 0, 5)},
        };
        for (Vector3f[] f : frames) {
            for (float sign : new float[] {1, -1}) {
                PackedFormats.qTangent(f[0], f[1], sign, 16, q);
                assertTrue(q.isFinite(), Arrays.toString(f));
                assertEquals(1f, q.lengthSquared(), 1e-5f, Arrays.toString(f));
                assertEquals(sign, Math.signum(q.w), "the bitangent sign survives");
                float s = PackedFormats.unpackQTangent(PackedFormats.packQTangent(f[0], f[1], sign), nOut, tOut);
                assertEquals(sign, s);
                assertEquals(0f, nOut.dot(tOut), 1e-3f, "tangent orthogonal to the normal");
                Vector3f expectedNormal = f[0].isFinite() && f[0].lengthSquared() > 0
                        ? new Vector3f(f[0]).normalize() : new Vector3f(0, 0, 1);
                assertEquals(1f, nOut.dot(expectedNormal), 1e-3f, Arrays.toString(f));
            }
        }
        // a regular frame is unchanged by the fallbacks: the packed and quaternion forms agree
        PackedFormats.qTangent(new Vector3f(0.2f, 0.9f, 0.1f), new Vector3f(1, 0.1f, 0), -1, 16, q);
        long packed = PackedFormats.packQTangent(new Vector3f(0.2f, 0.9f, 0.1f), new Vector3f(1, 0.1f, 0), -1);
        assertEquals(org.dynamisengine.vectrix.gpu.PackedNorm.packSnorm16(q.w) & 0xFFFFL, packed >>> 48);
    }

    // ---- gpu layouts and writers ----

    private static int firstNonZero(ByteBuffer b) {
        for (int i = 0; i < b.limit(); i++) {
            if (b.get(i) != 0) {
                return i + 1;
            }
        }
        return 0;
    }

    // ---- soa ----

    @Test
    void skinningHonoursJointScaleAndChecksItsArguments() {
        TransformSoA joints = new TransformSoA(2);
        Transformf j1 = new Transformf();
        j1.translation.set(1, 2, 3);
        j1.rotation.rotationZ(0.5f);
        j1.scale.set(2, 3, 0.5f);
        joints.set(1, j1);
        int[] ji = {1, 0, 0, 0, 1, 0, 0, 0};
        float[] jw = {1, 0, 0, 0, 0.5f, 0.5f, 0, 0};
        float[] x = {1, -2}, y = {2, 0.5f}, z = {3, 4};
        float[] ox = new float[2], oy = new float[2], oz = new float[2];
        SkinningKernels.skinLbs4(joints, ji, jw, x, y, z, ox, oy, oz, 2);
        Vector3f expected = new Matrix4f().translationRotateScale(j1.translation, j1.rotation, j1.scale)
                .transformPosition(new Vector3f(1, 2, 3));
        assertEquals(expected.x, ox[0], 1e-5f); // the scale used to be ignored
        assertEquals(expected.y, oy[0], 1e-5f);
        assertEquals(expected.z, oz[0], 1e-5f);
        float[] palette = new float[24];
        SkinningKernels.buildMatrixPalette12(joints, palette, 2);
        float[] px = new float[2], py = new float[2], pz = new float[2];
        SkinningKernels.skinLbs4MatrixPalette(palette, ji, jw, x, y, z, px, py, pz, 2);
        assertArrayEquals(ox, px, 1e-5f);
        assertArrayEquals(oy, py, 1e-5f);
        assertArrayEquals(oz, pz, 1e-5f);
        // with unit scale the scaled palette is the rigid one, bit for bit
        joints.sx[1] = joints.sy[1] = joints.sz[1] = 1;
        float[] rigid = new float[24];
        SkinningKernels.buildMatrixPalette12(joints, palette, 2);
        SkinningKernels.buildRigidMatrixPalette12(joints, rigid, 2);
        assertArrayEquals(rigid, palette);

        float[] untouched = {7, 7};
        assertThrows(IllegalArgumentException.class, () -> SkinningKernels.skinLbs4(joints, new int[] {0, 0, 0, 0, 0,
            0, 2, 0}, jw, x, y, z, untouched, untouched, untouched, 2)); // joint 2 of 2: was written up to vertex 1
        assertThrows(IllegalArgumentException.class, () -> SkinningKernels.skinLbs4(joints, new int[] {0, 0, 0, 0, 0,
            0, -1, 0}, jw, x, y, z, untouched, untouched, untouched, 2));
        assertThrows(IllegalArgumentException.class, () -> SkinningKernels.skinLbs4(joints, ji, new float[7], x, y, z,
                untouched, untouched, untouched, 2));
        assertThrows(IllegalArgumentException.class, () -> SkinningKernels.skinLbs4(joints, ji, jw, x, y, z,
                untouched, untouched, new float[1], 2));
        assertThrows(IllegalArgumentException.class, () -> SkinningKernels.skinLbs4(joints, ji, jw, x, y, z,
                untouched, untouched, untouched, -1));
        assertThrows(IllegalArgumentException.class, () -> SkinningKernels.skinLbs4SoA(joints, new int[] {0, 0},
                new int[] {0, 0}, new int[] {0, 5}, new int[] {0, 0}, x, x, x, x, x, y, z, untouched, untouched,
                untouched, 2));
        assertThrows(IllegalArgumentException.class, () -> SkinningKernels.skinLbs4MatrixPalette(new float[12], ji, jw,
                x, y, z, untouched, untouched, untouched, 2)); // a one-joint palette
        assertThrows(IllegalArgumentException.class, () -> SkinningKernels.skinDualQuat4(
                new org.dynamisengine.vectrix.soa.DualQuatSoA(1), ji, jw, x, y, z, untouched, untouched, untouched, 2));
        assertThrows(IllegalArgumentException.class, () -> SkinningKernels.buildMatrixPalette12(joints, new float[23],
                2));
        assertThrows(IllegalArgumentException.class, () -> SkinningKernels.buildRigidMatrixPalette12(joints,
                new float[36], 3));
        assertArrayEquals(new float[] {7, 7}, untouched);

        assertThrows(IllegalArgumentException.class, () -> TransformKernels.composeBatch(joints, joints,
                new TransformSoA(1), 2));
        assertThrows(IllegalArgumentException.class, () -> TransformKernels.composeBatch(joints, joints, joints, -1));
    }

    // ---- nn ----

    @Test
    void attentionRejectsShortInputsUpFront() {
        int d = 4, dv = 3, t = 5;
        float[] q = new float[d], k = new float[t * d], v = new float[t * dv];
        float[] p = new float[t], out = new float[dv], scr = new float[2 * t];
        Attention.forward(q, 0, d, k, 0, d, v, 0, dv, dv, t, 1f, null, 0, p, 0, out, 0, scr, 0);
        Arrays.fill(p, 9);
        assertThrows(IllegalArgumentException.class, () -> Attention.forward(q, 0, d, new float[t * d - 1], 0, d, v,
                0, dv, dv, t, 1f, null, 0, p, 0, out, 0, scr, 0)); // short keys: p was half written
        assertThrows(IllegalArgumentException.class, () -> Attention.forward(new float[d - 1], 0, d, k, 0, d, v, 0,
                dv, dv, t, 1f, null, 0, p, 0, out, 0, scr, 0));
        assertThrows(IllegalArgumentException.class, () -> Attention.forward(q, 0, d, k, 0, d - 1, v, 0, dv, dv, t,
                1f, null, 0, p, 0, out, 0, scr, 0)); // stride below the row length
        assertArrayEquals(new float[] {9, 9, 9, 9, 9}, p);

        float[] dq = new float[d], dk = new float[t * d], dV = new float[t * dv], dOut = {1, 1, 1};
        Attention.backward(q, 0, d, k, 0, d, v, 0, dv, dv, t, 1f, p, 0, dOut, 0, dq, 0, dk, 0, dV, 0, scr, 0);
        float[] dVBefore = dV.clone();
        assertThrows(IllegalArgumentException.class, () -> Attention.backward(q, 0, d, k, 0, d, v, 0, dv, dv, t, 1f,
                p, 0, dOut, 0, dq, 0, new float[t * d - 1], 0, dV, 0, scr, 0)); // short dk: dV had accumulated
        assertThrows(IllegalArgumentException.class, () -> Attention.backward(q, 0, d, k, 0, d, v, 0, dv, dv, t, 1f,
                new float[t - 1], 0, dOut, 0, dq, 0, dk, 0, dV, 0, scr, 0));
        assertArrayEquals(dVBefore, dV);

        // grouped: a short output used to fail after the first heads were written
        int heads = 4, kvHeads = 2;
        float[] gq = new float[heads * d], kc = new float[t * kvHeads * d], vc = new float[t * kvHeads * dv];
        float[] gp = new float[heads * t], gout = new float[heads * dv - 1];
        Arrays.fill(gp, 9);
        assertThrows(IllegalArgumentException.class, () -> Attention.groupedQuery(gq, 0, heads, d, kc, 0, vc, 0,
                kvHeads, dv, t, 1f, null, 0, gp, 0, gout, 0, scr, 0));
        float[] gdq = new float[heads * d];
        assertThrows(IllegalArgumentException.class, () -> Attention.groupedQueryBackward(gq, 0, heads, d, kc, 0, vc,
                0, kvHeads, dv, t, 1f, gp, 0, new float[heads * dv], 0, gdq, 0, new float[t * kvHeads * d - 1], 0,
                new float[t * kvHeads * dv], 0, scr, 0));
        for (float x : gp) {
            assertEquals(9f, x);
        }
    }

    @Test
    void quantizeInt8RejectsNonFiniteValues() {
        float[] x = new float[64];
        for (int i = 0; i < 64; i++) {
            x[i] = i - 30;
        }
        byte[] q = new byte[64];
        float[] s = new float[2];
        for (float bad : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            float[] xb = x.clone();
            xb[40] = bad;
            assertThrows(IllegalArgumentException.class, () -> Formats.quantizeInt8(xb, 0, 64, q, 0, s, 0));
        }
        assertArrayEquals(new byte[64], q); // a NaN block used to get a NaN scale and zero codes
        assertArrayEquals(new float[2], s);
        Formats.quantizeInt8(x, 0, 64, q, 0, s, 0);
        assertEquals(30f / 127f, s[0]);
        assertEquals(-127, q[0]);

        float[] y = {5, 5};
        assertThrows(IllegalArgumentException.class, () -> Formats.gemvInt8(2, 32, new byte[63], 0, new float[2], 0,
                new float[32], 0, y, 0)); // short codes: y[0] was written first
        assertThrows(IllegalArgumentException.class, () -> Formats.gemvInt8(2, 32, new byte[64], 0, new float[1], 0,
                new float[32], 0, y, 0));
        assertThrows(IllegalArgumentException.class, () -> Formats.gemvInt8(2, 31, new byte[64], 0, new float[2], 0,
                new float[32], 0, y, 0));
        assertThrows(IllegalArgumentException.class, () -> Formats.gemvInt8(-1, 32, new byte[64], 0, new float[2], 0,
                new float[32], 0, y, 0));
        assertArrayEquals(new float[] {5, 5}, y);
    }

    @Test
    void normsCheckTheirStatistics() {
        float[] x = {1, 2, 3, 4};
        float[] y = {9, 9, 9, 9};
        assertThrows(IllegalArgumentException.class, () -> Norms.layerNorm(x, 0, null, 0, null, 0, 1e-5f, y, 0, 4,
                new float[1], 0)); // y used to be written before stats threw
        assertThrows(IllegalArgumentException.class, () -> Norms.rmsNorm(x, 0, null, 0, 1e-5f, y, 0, 4,
                new float[1], 1));
        assertArrayEquals(new float[] {9, 9, 9, 9}, y);
        float[] scratch = new float[8];
        float[] dGamma = {3, 3, 3, 3};
        assertThrows(IllegalArgumentException.class, () -> Norms.layerNormBackward(x, 0, null, 0, new float[1], 0, x,
                0, y, 0, dGamma, 0, null, 0, 4, scratch, 0));
        assertThrows(IllegalArgumentException.class, () -> Norms.rmsNormBackward(x, 0, null, 0, null, 0, x, 0, y, 0,
                dGamma, 0, 4, scratch, 0));
        assertArrayEquals(new float[] {3, 3, 3, 3}, dGamma);
        assertArrayEquals(new float[] {9, 9, 9, 9}, y);
    }

    @Test
    void convAndPoolCheckBiasGradientsAndIndices() {
        Conv2d conv = Conv2d.of(2, 3, 5, 5, 3, 3, 1, 1, 1, 1);
        float[] in = new float[2 * 25], w = new float[conv.weightCount()], scr = new float[conv.scratchSize()];
        float[] out = new float[3 * 25];
        Arrays.fill(out, 9);
        assertThrows(IllegalArgumentException.class, () -> conv.forward(in, 0, w, 0, new float[2], 0, out, 0, scr,
                0)); // short bias: out was fully computed first
        for (float o : out) {
            assertEquals(9f, o);
        }
        float[] dW = new float[conv.weightCount()];
        Arrays.fill(dW, 1);
        float[] dIn = new float[2 * 25];
        Arrays.fill(dIn, 2);
        assertThrows(IllegalArgumentException.class, () -> conv.backward(in, 0, w, 0, out, 0, dIn, 0, dW, 0,
                new float[2], 0, scr, 0));
        assertThrows(IllegalArgumentException.class, () -> conv.backward(in, 0, w, 0, out, 0, new float[49], 0, dW,
                0, null, 0, scr, 0));
        assertThrows(IllegalArgumentException.class, () -> conv.backward(in, 0, w, 0, out, 0, dIn, 0,
                new float[conv.weightCount() - 1], 0, null, 0, scr, 0));
        for (float g : dW) {
            assertEquals(1f, g);
        }
        for (float g : dIn) {
            assertEquals(2f, g);
        }
        // a kernel one cell larger than the input looked like one output under a truncating division
        assertThrows(IllegalArgumentException.class, () -> Conv2d.of(1, 1, 1, 1, 2, 2, 2, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Pool2d.of(1, 1, 1, 2, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> Conv2d.of(65536, 65536, 1, 1, 1, 1, 1, 0, 1, 1));

        Pool2d pool = Pool2d.of(1, 4, 4, 2, 2, 0);
        float[] dOut = {1, 1, 1, 1};
        float[] pdIn = new float[16];
        Arrays.fill(pdIn, 5);
        assertThrows(IllegalArgumentException.class, () -> pool.maxBackward(new int[] {0, 1, 16, 2}, 0, dOut, 0,
                pdIn, 0)); // was an ArrayIndexOutOfBoundsException after dIn was cleared
        assertThrows(IllegalArgumentException.class, () -> pool.maxBackward(new int[] {0, -1, 2, 3}, 0, dOut, 0,
                pdIn, 0));
        for (float g : pdIn) {
            assertEquals(5f, g);
        }
    }

    @Test
    void samplerArgmaxTreatsNanLikePoolMax() {
        float nan = Float.NaN;
        float[][] rows = {{1, nan, 3, nan}, {nan, 5, 2, 1}, {1, 3, 2, 3}, {-1, -3, nan, 9}, {2, 2, 2, 2}};
        Pool2d pool = Pool2d.of(1, 2, 2, 2, 1, 0); // one window over the four values, row-major
        for (float[] row : rows) {
            float[] out = new float[1];
            int[] arg = new int[1];
            pool.max(row, 0, out, 0, arg, 0);
            assertEquals(arg[0], Sampler.argmax(row, 0, 4), Arrays.toString(row)); // {1, NaN, 3, NaN} gave 2
        }
        assertEquals(1, Sampler.argmax(new float[] {0, nan}, 0, 2));
        assertEquals(-1, Sampler.argmax(new float[0], 0, 0));
        assertEquals(1, Sampler.argmax(new float[] {7, 0, 9}, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> Sampler.argmax(new float[3], 1, 3));
        assertThrows(IllegalArgumentException.class, () -> Sampler.argmax(new float[3], -1, 1));
        assertThrows(IllegalArgumentException.class, () -> Sampler.argmax(new float[3], 0, -1));
        assertThrows(IllegalArgumentException.class, () -> Sampler.argmax(null, 0, 0));
    }

    // ---- curve ----

    @Test
    void scalarBSplinesCopyTheirControlPoints() {
        float[] pf = {0, 1, 2, 3, 4};
        UniformBSpline1f sf = new UniformBSpline1f(pf);
        float before = sf.evaluate(1, 0.5f);
        pf[3] = 100; // used to change the curve
        assertEquals(before, sf.evaluate(1, 0.5f));
        double[] pd = {0, 1, 2, 3, 4};
        UniformBSpline1d sd = new UniformBSpline1d(pd);
        double beforeD = sd.evaluate(1, 0.5);
        pd[3] = 100;
        assertEquals(beforeD, sd.evaluate(1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new UniformBSpline1f(null));
        float[] out = {7, 7};
        assertThrows(IllegalArgumentException.class, () -> sf.evaluateBatch(0, new float[] {0.5f}, 0, 2, out, 0));
        assertThrows(IllegalArgumentException.class, () -> sf.evaluateBatch(0, new float[] {0.5f, 1}, 0, 2, out, 1));
        assertArrayEquals(new float[] {7, 7}, out);
        sd.evaluateBatch(0, new double[] {0, 1}, 0, 2, new double[2], 0);
    }

    @Test
    void arcLengthTablesReportTheirLengthForMultiSegmentPaths() {
        // two straight Bézier segments of lengths 3 and 1 with uneven control points: constant speed over the path
        Vector3f a0 = new Vector3f(0, 0, 0), a1 = new Vector3f(0.1f, 0, 0), a2 = new Vector3f(0.2f, 0, 0);
        Vector3f a3 = new Vector3f(3, 0, 0);
        Vector3f b1 = new Vector3f(3.9f, 0, 0), b2 = new Vector3f(3.95f, 0, 0), b3 = new Vector3f(4, 0, 0);
        float[] ta = new float[257], tb = new float[257];
        float la = CurveReparameterizer3f.buildArcLengthTableForBezier(a0, a1, a2, a3, 256, ta);
        float lb = CurveReparameterizer3f.buildArcLengthTableForBezier(a3, b1, b2, b3, 256, tb);
        assertEquals(3f, la, 1e-5f);
        assertEquals(1f, lb, 1e-5f);
        Vector3f p = new Vector3f();
        for (float dist = 0; dist <= 4f; dist += 0.25f) {
            if (dist <= la) {
                Bezier3f.evaluate(a0, a1, a2, a3, CurveReparameterizer3f.mapArcLengthToT(dist / la, ta), p);
            } else {
                Bezier3f.evaluate(a3, b1, b2, b3, CurveReparameterizer3f.mapArcLengthToT((dist - la) / lb, tb), p);
            }
            assertEquals(dist, p.x, 2e-3f, "distance " + dist);
        }
        double ld = CurveReparameterizer3d.buildArcLengthTableForBezier(new Vector3d(0, 0, 0), new Vector3d(0, 1, 0),
                new Vector3d(0, 2, 0), new Vector3d(0, 3, 0), 16, new double[17]);
        assertEquals(3.0, ld, 1e-12);
        // degenerate segments: zero length keeps the uniform table, as does an overflowing one
        float[] flat = new float[5];
        assertEquals(0f, CurveReparameterizer3f.buildArcLengthTableForBezier(a0, a0, a0, a0, 4, flat));
        assertArrayEquals(new float[] {0, 0.25f, 0.5f, 0.75f, 1}, flat);
        Vector3f far = new Vector3f(3e38f, 0, 0);
        Vector3f nfar = new Vector3f(-3e38f, 0, 0);
        CurveReparameterizer3f.buildArcLengthTableForBezier(nfar, nfar, far, far, 4, flat);
        assertArrayEquals(new float[] {0, 0.25f, 0.5f, 0.75f, 1}, flat);
    }

    @Test
    void arcLengthLookupRejectsATableOfAnotherSampleCount() {
        UniformBSpline3f s = new UniformBSpline3f(new Vector3f[] {new Vector3f(0, 0, 0), new Vector3f(1, 2, 0),
            new Vector3f(3, -1, 0), new Vector3f(4, 0, 1)});
        float[] exact = new float[17];
        float[] oversized = new float[33];
        CurveReparameterizer3f.buildArcLengthTable(s, 0, 16, exact);
        CurveReparameterizer3f.buildArcLengthTable(s, 0, 16, oversized);
        Vector3f p = new Vector3f();
        CurveReparameterizer3f.evaluateByArcLength(s, 0, 0.5f, exact, p);
        // the oversized table ends in zeros: it was silently mapped as a 32-sample table
        assertThrows(IllegalArgumentException.class, () -> CurveReparameterizer3f.evaluateByArcLength(s, 0, 0.5f,
                oversized, p));
        assertThrows(IllegalArgumentException.class, () -> CurveReparameterizer3f.mapArcLengthToT(0.5f, oversized));
        assertThrows(IllegalArgumentException.class, () -> CurveReparameterizer3f.mapArcLengthToT(0.5f, exact, 8));
        assertEquals(CurveReparameterizer3f.mapArcLengthToT(0.3f, exact),
                CurveReparameterizer3f.mapArcLengthToT(0.3f, oversized, 16));
        double[] od = new double[40];
        CurveReparameterizer3d.buildArcLengthTableForBezier(new Vector3d(), new Vector3d(1, 2, 0),
                new Vector3d(3, -1, 0), new Vector3d(4, 0, 1), 16, od);
        assertThrows(IllegalArgumentException.class, () -> CurveReparameterizer3d.mapArcLengthToT(0.5, od));
        assertNotEquals(0.0, CurveReparameterizer3d.mapArcLengthToT(0.5, od, 16));
    }
}
