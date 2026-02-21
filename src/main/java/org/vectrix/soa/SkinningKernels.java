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
package org.vectrix.soa;

import org.vectrix.experimental.KernelConfig;
import org.vectrix.experimental.MathMode;
import org.vectrix.simd.SimdSupport;
//#ifdef __HAS_VECTOR_API__
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
//#endif

/**
 * Batch skinning kernels for rigid-joint transforms.
 */
public final class SkinningKernels {
    private static final String PROP_EXPERIMENTAL_SKINNING_SIMD = "vectrix.skinning.simd";
    private static final int SIMD_BATCH_THRESHOLD = 64;
    private static final boolean EXPERIMENTAL_SKINNING_SIMD = KernelConfig.parseBoolean(System.getProperty(PROP_EXPERIMENTAL_SKINNING_SIMD), false);
//#ifdef __HAS_VECTOR_API__
    private static final VectorSpecies<Float> SKIN_SPECIES = FloatVector.SPECIES_PREFERRED;
//#endif

    private SkinningKernels() {
    }

    public static void skinLbs4(TransformSoA joints, int[] jointIndices, float[] jointWeights, float[] inX, float[] inY, float[] inZ,
            float[] outX, float[] outY, float[] outZ, int count) {
        skinLbs4Scalar(joints, jointIndices, jointWeights, inX, inY, inZ, outX, outY, outZ, count);
    }

    public static void skinLbs4Scalar(TransformSoA joints, int[] jointIndices, float[] jointWeights, float[] inX, float[] inY, float[] inZ,
            float[] outX, float[] outY, float[] outZ, int count) {
        for (int i = 0; i < count; i++) {
            int base = i << 2;
            skinLbs4Single(joints, jointIndices[base], jointIndices[base + 1], jointIndices[base + 2], jointIndices[base + 3], jointWeights[base], jointWeights[base + 1],
                    jointWeights[base + 2], jointWeights[base + 3], inX[i], inY[i], inZ[i], outX, outY, outZ, i);
        }
    }

    public static void skinLbs4SoA(TransformSoA joints, int[] j0, int[] j1, int[] j2, int[] j3, float[] w0, float[] w1, float[] w2, float[] w3,
            float[] inX, float[] inY, float[] inZ, float[] outX, float[] outY, float[] outZ, int count) {
        if (count <= 0) {
            return;
        }
        if (KernelConfig.mathMode() == MathMode.STRICT
                || !KernelConfig.simdEnabled()
                || SimdSupport.backend() != SimdSupport.Backend.VECTOR_API
                || !EXPERIMENTAL_SKINNING_SIMD
                || count < SIMD_BATCH_THRESHOLD) {
            skinLbs4SoAScalar(joints, j0, j1, j2, j3, w0, w1, w2, w3, inX, inY, inZ, outX, outY, outZ, count);
            return;
        }
        skinLbs4SoASimd(joints, j0, j1, j2, j3, w0, w1, w2, w3, inX, inY, inZ, outX, outY, outZ, count);
    }

    public static void skinLbs4SoAScalar(TransformSoA joints, int[] j0, int[] j1, int[] j2, int[] j3, float[] w0, float[] w1, float[] w2, float[] w3,
            float[] inX, float[] inY, float[] inZ, float[] outX, float[] outY, float[] outZ, int count) {
        for (int i = 0; i < count; i++) {
            skinLbs4Single(joints, j0[i], j1[i], j2[i], j3[i], w0[i], w1[i], w2[i], w3[i], inX[i], inY[i], inZ[i], outX, outY, outZ, i);
        }
    }

    public static void skinLbs4SoASimd(TransformSoA joints, int[] j0, int[] j1, int[] j2, int[] j3, float[] w0, float[] w1, float[] w2, float[] w3,
            float[] inX, float[] inY, float[] inZ, float[] outX, float[] outY, float[] outZ, int count) {
//#ifdef __HAS_VECTOR_API__
        int lanes = SKIN_SPECIES.length();
        if (lanes <= 1) {
            skinLbs4SoAScalar(joints, j0, j1, j2, j3, w0, w1, w2, w3, inX, inY, inZ, outX, outY, outZ, count);
            return;
        }
        int i = 0;
        int limit = count - (count % lanes);
        for (; i < limit; i += lanes) {
            FloatVector ox = FloatVector.zero(SKIN_SPECIES);
            FloatVector oy = FloatVector.zero(SKIN_SPECIES);
            FloatVector oz = FloatVector.zero(SKIN_SPECIES);
            for (int lane = 0; lane < lanes; lane++) {
                int idx = i + lane;
                float x = inX[idx], y = inY[idx], z = inZ[idx];
                float sx = 0.0f, sy = 0.0f, sz = 0.0f;
                sx = addInfluence(joints, j0[idx], w0[idx], x, y, z, sx, 0);
                sy = addInfluence(joints, j0[idx], w0[idx], x, y, z, sy, 1);
                sz = addInfluence(joints, j0[idx], w0[idx], x, y, z, sz, 2);
                sx = addInfluence(joints, j1[idx], w1[idx], x, y, z, sx, 0);
                sy = addInfluence(joints, j1[idx], w1[idx], x, y, z, sy, 1);
                sz = addInfluence(joints, j1[idx], w1[idx], x, y, z, sz, 2);
                sx = addInfluence(joints, j2[idx], w2[idx], x, y, z, sx, 0);
                sy = addInfluence(joints, j2[idx], w2[idx], x, y, z, sy, 1);
                sz = addInfluence(joints, j2[idx], w2[idx], x, y, z, sz, 2);
                sx = addInfluence(joints, j3[idx], w3[idx], x, y, z, sx, 0);
                sy = addInfluence(joints, j3[idx], w3[idx], x, y, z, sy, 1);
                sz = addInfluence(joints, j3[idx], w3[idx], x, y, z, sz, 2);
                ox = ox.withLane(lane, sx);
                oy = oy.withLane(lane, sy);
                oz = oz.withLane(lane, sz);
            }
            ox.intoArray(outX, i);
            oy.intoArray(outY, i);
            oz.intoArray(outZ, i);
        }
        for (; i < count; i++) {
            skinLbs4Single(joints, j0[i], j1[i], j2[i], j3[i], w0[i], w1[i], w2[i], w3[i], inX[i], inY[i], inZ[i], outX, outY, outZ, i);
        }
//#else
        skinLbs4SoAScalar(joints, j0, j1, j2, j3, w0, w1, w2, w3, inX, inY, inZ, outX, outY, outZ, count);
//#endif
    }

    public static void skinDualQuat4(DualQuatSoA joints, int[] jointIndices, float[] jointWeights, float[] inX, float[] inY, float[] inZ,
            float[] outX, float[] outY, float[] outZ, int count) {
        for (int i = 0; i < count; i++) {
            int base = i << 2;
            skinDualQuat4Single(joints, jointIndices[base], jointIndices[base + 1], jointIndices[base + 2], jointIndices[base + 3], jointWeights[base], jointWeights[base + 1],
                    jointWeights[base + 2], jointWeights[base + 3], inX[i], inY[i], inZ[i], outX, outY, outZ, i);
        }
    }

    public static void skinDualQuat4SoA(DualQuatSoA joints, int[] j0, int[] j1, int[] j2, int[] j3, float[] w0, float[] w1, float[] w2, float[] w3,
            float[] inX, float[] inY, float[] inZ, float[] outX, float[] outY, float[] outZ, int count) {
        for (int i = 0; i < count; i++) {
            skinDualQuat4Single(joints, j0[i], j1[i], j2[i], j3[i], w0[i], w1[i], w2[i], w3[i], inX[i], inY[i], inZ[i], outX, outY, outZ, i);
        }
    }

    private static void skinLbs4Single(TransformSoA joints, int j0, int j1, int j2, int j3, float w0, float w1, float w2, float w3, float x, float y,
            float z, float[] outX, float[] outY, float[] outZ, int i) {
        float ox = 0.0f;
        float oy = 0.0f;
        float oz = 0.0f;

        float rx = joints.qx[j0], ry = joints.qy[j0], rz = joints.qz[j0], rw = joints.qw[j0];
        float tx2 = 2.0f * (ry * z - rz * y);
        float ty2 = 2.0f * (rz * x - rx * z);
        float tz2 = 2.0f * (rx * y - ry * x);
        ox += (x + rw * tx2 + (ry * tz2 - rz * ty2) + joints.tx[j0]) * w0;
        oy += (y + rw * ty2 + (rz * tx2 - rx * tz2) + joints.ty[j0]) * w0;
        oz += (z + rw * tz2 + (rx * ty2 - ry * tx2) + joints.tz[j0]) * w0;

        rx = joints.qx[j1];
        ry = joints.qy[j1];
        rz = joints.qz[j1];
        rw = joints.qw[j1];
        tx2 = 2.0f * (ry * z - rz * y);
        ty2 = 2.0f * (rz * x - rx * z);
        tz2 = 2.0f * (rx * y - ry * x);
        ox += (x + rw * tx2 + (ry * tz2 - rz * ty2) + joints.tx[j1]) * w1;
        oy += (y + rw * ty2 + (rz * tx2 - rx * tz2) + joints.ty[j1]) * w1;
        oz += (z + rw * tz2 + (rx * ty2 - ry * tx2) + joints.tz[j1]) * w1;

        rx = joints.qx[j2];
        ry = joints.qy[j2];
        rz = joints.qz[j2];
        rw = joints.qw[j2];
        tx2 = 2.0f * (ry * z - rz * y);
        ty2 = 2.0f * (rz * x - rx * z);
        tz2 = 2.0f * (rx * y - ry * x);
        ox += (x + rw * tx2 + (ry * tz2 - rz * ty2) + joints.tx[j2]) * w2;
        oy += (y + rw * ty2 + (rz * tx2 - rx * tz2) + joints.ty[j2]) * w2;
        oz += (z + rw * tz2 + (rx * ty2 - ry * tx2) + joints.tz[j2]) * w2;

        rx = joints.qx[j3];
        ry = joints.qy[j3];
        rz = joints.qz[j3];
        rw = joints.qw[j3];
        tx2 = 2.0f * (ry * z - rz * y);
        ty2 = 2.0f * (rz * x - rx * z);
        tz2 = 2.0f * (rx * y - ry * x);
        ox += (x + rw * tx2 + (ry * tz2 - rz * ty2) + joints.tx[j3]) * w3;
        oy += (y + rw * ty2 + (rz * tx2 - rx * tz2) + joints.ty[j3]) * w3;
        oz += (z + rw * tz2 + (rx * ty2 - ry * tx2) + joints.tz[j3]) * w3;

        outX[i] = ox;
        outY[i] = oy;
        outZ[i] = oz;
    }

    private static float addInfluence(TransformSoA joints, int j, float w, float x, float y, float z, float acc, int component) {
        float rx = joints.qx[j], ry = joints.qy[j], rz = joints.qz[j], rw = joints.qw[j];
        float tx2 = 2.0f * (ry * z - rz * y);
        float ty2 = 2.0f * (rz * x - rx * z);
        float tz2 = 2.0f * (rx * y - ry * x);
        float v;
        if (component == 0) {
            v = x + rw * tx2 + (ry * tz2 - rz * ty2) + joints.tx[j];
        } else if (component == 1) {
            v = y + rw * ty2 + (rz * tx2 - rx * tz2) + joints.ty[j];
        } else {
            v = z + rw * tz2 + (rx * ty2 - ry * tx2) + joints.tz[j];
        }
        return acc + v * w;
    }

    private static void skinDualQuat4Single(DualQuatSoA joints, int j0, int j1, int j2, int j3, float w0, float w1, float w2, float w3, float x, float y,
            float z, float[] outX, float[] outY, float[] outZ, int i) {
        float refX = joints.rx[j0], refY = joints.ry[j0], refZ = joints.rz[j0], refW = joints.rw[j0];

        float arx = 0.0f, ary = 0.0f, arz = 0.0f, arw = 0.0f;
        float adx = 0.0f, ady = 0.0f, adz = 0.0f, adw = 0.0f;

        float s0 = signForReference(joints, refX, refY, refZ, refW, j0);
        float s1 = signForReference(joints, refX, refY, refZ, refW, j1);
        float s2 = signForReference(joints, refX, refY, refZ, refW, j2);
        float s3 = signForReference(joints, refX, refY, refZ, refW, j3);

        float sw0 = s0 * w0;
        float sw1 = s1 * w1;
        float sw2 = s2 * w2;
        float sw3 = s3 * w3;

        arx = joints.rx[j0] * sw0 + joints.rx[j1] * sw1 + joints.rx[j2] * sw2 + joints.rx[j3] * sw3;
        ary = joints.ry[j0] * sw0 + joints.ry[j1] * sw1 + joints.ry[j2] * sw2 + joints.ry[j3] * sw3;
        arz = joints.rz[j0] * sw0 + joints.rz[j1] * sw1 + joints.rz[j2] * sw2 + joints.rz[j3] * sw3;
        arw = joints.rw[j0] * sw0 + joints.rw[j1] * sw1 + joints.rw[j2] * sw2 + joints.rw[j3] * sw3;

        adx = joints.dx[j0] * sw0 + joints.dx[j1] * sw1 + joints.dx[j2] * sw2 + joints.dx[j3] * sw3;
        ady = joints.dy[j0] * sw0 + joints.dy[j1] * sw1 + joints.dy[j2] * sw2 + joints.dy[j3] * sw3;
        adz = joints.dz[j0] * sw0 + joints.dz[j1] * sw1 + joints.dz[j2] * sw2 + joints.dz[j3] * sw3;
        adw = joints.dw[j0] * sw0 + joints.dw[j1] * sw1 + joints.dw[j2] * sw2 + joints.dw[j3] * sw3;

        float norm2 = arx * arx + ary * ary + arz * arz + arw * arw;
        if (norm2 == 0.0f) {
            outX[i] = x;
            outY[i] = y;
            outZ[i] = z;
            return;
        }
        float invNorm = org.vectrix.core.Math.invsqrt(norm2);
        arx *= invNorm;
        ary *= invNorm;
        arz *= invNorm;
        arw *= invNorm;
        adx *= invNorm;
        ady *= invNorm;
        adz *= invNorm;
        adw *= invNorm;

        float tx2 = 2.0f * (ary * z - arz * y);
        float ty2 = 2.0f * (arz * x - arx * z);
        float tz2 = 2.0f * (arx * y - ary * x);
        float rx = x + arw * tx2 + (ary * tz2 - arz * ty2);
        float ry = y + arw * ty2 + (arz * tx2 - arx * tz2);
        float rz = z + arw * tz2 + (arx * ty2 - ary * tx2);

        float tx = 2.0f * (-adw * arx + adx * arw - ady * arz + adz * ary);
        float ty = 2.0f * (-adw * ary + adx * arz + ady * arw - adz * arx);
        float tz = 2.0f * (-adw * arz - adx * ary + ady * arx + adz * arw);

        outX[i] = rx + tx;
        outY[i] = ry + ty;
        outZ[i] = rz + tz;
    }

    private static float signForReference(DualQuatSoA joints, float refX, float refY, float refZ, float refW, int j) {
        float rx = joints.rx[j], ry = joints.ry[j], rz = joints.rz[j], rw = joints.rw[j];
        float dot = refX * rx + refY * ry + refZ * rz + refW * rw;
        return dot < 0.0f ? -1.0f : 1.0f;
    }
}
