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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.color.ColorMathd;
import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Matrix4x3d;
import org.dynamisengine.vectrix.core.Matrix4x3f;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.geometry.BoundingVolumesd;
import org.dynamisengine.vectrix.geometry.BoundingVolumesf;
import org.dynamisengine.vectrix.geometry.FrustumPlanes;
import org.dynamisengine.vectrix.gpu.MeshletCones;
import org.dynamisengine.vectrix.gpu.PackedFormats;
import org.dynamisengine.vectrix.gpu.PackedNorm;
import org.dynamisengine.vectrix.linalg.Svd3d;
import org.junit.jupiter.api.Test;

/** GPU data contracts (2.4.0): packed formats, meshlet cones, sphere bounds, frustum packing. */
class GpuContractsTest {

    @Test
    void normalizedIntegersDecodeExactly() {
        for (int c = 0; c < 65536; c++) {
            assertEquals((float) (c / 65535.0), PackedNorm.unpackUnorm16(c), 0.0f);
            assertEquals((float) Math.max((short) c / 32767.0, -1), PackedNorm.unpackSnorm16(c), 0.0f);
            if (c < 256) {
                assertEquals((float) (c / 255.0), PackedNorm.unpackUnorm8(c), 0.0f);
                assertEquals((float) Math.max((byte) c / 127.0, -1), PackedNorm.unpackSnorm8(c), 0.0f);
                assertEquals(c, PackedNorm.packUnorm8(PackedNorm.unpackUnorm8(c)));
            }
        }
        Vector4f v = new Vector4f();
        for (int c = 0; c < 1024; c++) {
            int packed = c | (1023 - c) << 10 | (c ^ 0x155) << 20 | (c & 3) << 30;
            PackedFormats.unpackUnorm1010102(packed, v);
            assertEquals(packed, PackedFormats.packUnorm1010102(v.x, v.y, v.z, v.w), "unorm 10_10_10_2 code " + c);
            int sc = c - 512; // snorm: -512 decodes to -1 like -511, so skip it
            if (sc != -512) {
                int sp = PackedFormats.packSnorm1010102(sc / 511f, 0, 0, 0);
                assertEquals(sc, sp << 22 >> 22, "snorm 10 code " + sc);
            }
        }
    }

    /** Straight transcription of the Vulkan "shared exponent" algorithm with pow/log. */
    private static int rgb9e5Reference(float r, float g, float b) {
        double max = 511.0 / 512 * Math.pow(2, 16);
        double rc = Float.isNaN(r) ? 0 : Math.max(0, Math.min(r, max));
        double gc = Float.isNaN(g) ? 0 : Math.max(0, Math.min(g, max));
        double bc = Float.isNaN(b) ? 0 : Math.max(0, Math.min(b, max));
        double maxc = Math.max(rc, Math.max(gc, bc));
        double log = maxc > 0 ? Math.floor(Math.log(maxc) / Math.log(2)) : Double.NEGATIVE_INFINITY;
        // log() can land one off across a power of two; the definition is the binary exponent
        if (maxc > 0 && Math.pow(2, log) > maxc) {
            log--;
        } else if (maxc > 0 && Math.pow(2, log + 1) <= maxc) {
            log++;
        }
        int expP = (int) Math.max(-16, log) + 1 + 15;
        int maxs = (int) Math.floor(maxc / Math.pow(2, expP - 15 - 9) + 0.5);
        int exp = maxs == 512 ? expP + 1 : expP;
        double d = Math.pow(2, exp - 15 - 9);
        return (int) Math.floor(rc / d + 0.5) | (int) Math.floor(gc / d + 0.5) << 9 | (int) Math.floor(bc / d + 0.5) << 18
                | exp << 27;
    }

    @Test
    void sharedExponentMatchesTheSpecification() {
        SplittableRandom r = new SplittableRandom(1);
        for (int i = 0; i < 200000; i++) {
            float a = (float) Math.scalb(r.nextDouble(), r.nextInt(-30, 18));
            float b = r.nextInt(8) == 0 ? 0 : (float) Math.scalb(r.nextDouble(), r.nextInt(-30, 18));
            float c = r.nextInt(8) == 0 ? -1 : (float) (a * r.nextDouble(0, 2));
            assertEquals(rgb9e5Reference(a, b, c), PackedFormats.packRgb9e5(a, b, c), a + " " + b + " " + c);
        }
        for (float s : new float[] {0, -0f, Float.NaN, Float.POSITIVE_INFINITY, 65408, 65409, 1e30f, Float.MIN_VALUE, 0.5f}) {
            assertEquals(rgb9e5Reference(s, 1, 0), PackedFormats.packRgb9e5(s, 1, 0), "special " + s);
        }
        Vector3f v = new Vector3f();
        assertEquals(65408f, PackedFormats.unpackRgb9e5(PackedFormats.packRgb9e5(1e30f, 0, 0), v).x, 0f);
        assertEquals(1.0f, PackedFormats.unpackRgb9e5(PackedFormats.packRgb9e5(1, 0, 0), v).x, 0f);
    }

    private static double ufloat(int bits, int m) {
        int exp = bits >>> m, man = bits & ((1 << m) - 1);
        return exp == 0 ? man * Math.pow(2, -14 - m) : (1 + man / Math.pow(2, m)) * Math.pow(2, exp - 15);
    }

    @Test
    void packedFloatsRoundToNearestEven() {
        Vector3f v = new Vector3f();
        for (int code = 0; code < (31 << 6); code++) { // every finite 11-bit code round-trips
            int packed = code | (code >> 1 & 0x7FF) << 11 | (code >> 1 & 0x3FF) << 22;
            PackedFormats.unpackR11G11B10F(packed, v);
            assertEquals((float) ufloat(code, 6), v.x, 0f, "decode " + code);
            assertEquals(packed, PackedFormats.packR11G11B10F(v.x, v.y, v.z), "round trip " + code);
        }
        SplittableRandom r = new SplittableRandom(2);
        for (int i = 0; i < 200000; i++) {
            float x = (float) Math.scalb(r.nextDouble(), r.nextInt(-24, 17));
            int q = PackedFormats.packR11G11B10F(x, 0, 0) & 0x7FF;
            if (q >= 31 << 6) {
                assertTrue(x >= 65024 + 256, "overflow only from max + half an ulp (2^8): " + x);
                continue;
            }
            double err = Math.abs(x - ufloat(q, 6));
            if (q > 0) {
                assertTrue(err <= Math.abs(x - ufloat(q - 1, 6)), "nearest below " + x);
            }
            if (q + 1 < 31 << 6) {
                double up = Math.abs(x - ufloat(q + 1, 6));
                assertTrue(err < up || (err == up && (q & 1) == 0), "nearest-even above " + x);
            }
        }
        assertEquals(0, PackedFormats.packR11G11B10F(-5, -0f, -1e-30f));
        assertTrue(Float.isNaN(PackedFormats.unpackR11G11B10F(PackedFormats.packR11G11B10F(Float.NaN, 0, 0), v).x));
        assertEquals(Float.POSITIVE_INFINITY, PackedFormats.unpackR11G11B10F(PackedFormats.packR11G11B10F(0, 0, 1e6f), v).z, 0f);
        assertEquals(64512f, PackedFormats.unpackR11G11B10F(PackedFormats.packR11G11B10F(0, 0, 64512), v).z, 0f);
    }

    @Test
    void srgb8AndQTangents() {
        for (int c = 0; c < 256; c++) {
            assertEquals(c, PackedFormats.encodeSrgb8(PackedFormats.decodeSrgb8(c)), "sRGB code " + c);
            assertEquals((float) ColorMathd.srgbToLinear(c / 255.0), PackedFormats.decodeSrgb8(c), 0f);
        }
        SplittableRandom r = new SplittableRandom(3);
        for (int i = 0; i < 100000; i++) {
            float x = (float) r.nextDouble();
            assertTrue(Math.abs(255 * ColorMathd.linearToSrgb(x) - PackedFormats.encodeSrgb8(x)) <= 0.5, "nearest code " + x);
        }
        Vector3f n = new Vector3f(), t = new Vector3f();
        for (int i = 0; i < 50000; i++) {
            Vector3f n0 = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian()).normalize();
            Vector3f t0 = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian());
            if (i < 8) { // 180-degree frames: the quaternion has w = 0, where the sign is easiest to lose
                n0.set(0, 0, 1);
                t0.set(-1, i * 1e-7f, 0);
            }
            float sign = i % 2 == 0 ? 1 : -1;
            Vector3f tOrtho = new Vector3f(t0).sub(new Vector3f(n0).mul(n0.dot(t0))).normalize();
            float s = PackedFormats.unpackQTangent(PackedFormats.packQTangent(n0, t0, sign), n, t);
            assertEquals(sign, s, "bitangent sign, frame " + i);
            assertTrue(n.distance(n0) < 2e-4 && t.distance(tOrtho) < 2e-4, "frame " + i + " " + n + " " + t);
        }
    }

    @Test
    void meshletConesAreTightAndConservative() {
        SplittableRandom r = new SplittableRandom(4);
        Vector4f cone = new Vector4f(), qcone = new Vector4f();
        int culled = 0, qculled = 0;
        for (int trial = 0; trial < 300; trial++) {
            Vector3d a0 = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            double spread = r.nextDouble(0, 1.3);
            int tris = r.nextInt(1, 40);
            float[] xyz = new float[9 * tris];
            int[] idx = new int[3 * tris];
            for (int k = 0; k < tris; k++) {
                // a random normal within spread of a0, and a small triangle with that normal
                Vector3d nrm;
                do {
                    nrm = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                } while (Math.acos(Math.min(1, nrm.dot(a0))) > spread);
                Vector3d u = new Vector3d(nrm).orthogonalize(new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()));
                Vector3d w = new Vector3d(nrm).cross(u);
                Vector3d p = new Vector3d(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1));
                Vector3d[] v = {p, new Vector3d(p).add(new Vector3d(u).mul(0.1)), new Vector3d(p).add(new Vector3d(w).mul(0.1))};
                for (int j = 0; j < 3; j++) {
                    xyz[9 * k + 3 * j] = (float) v[j].x;
                    xyz[9 * k + 3 * j + 1] = (float) v[j].y;
                    xyz[9 * k + 3 * j + 2] = (float) v[j].z;
                    idx[3 * k + j] = 3 * k + j;
                }
            }
            float cosA = MeshletCones.normalCone(xyz, idx, 0, tris, cone);
            double[] normals = new double[3 * tris];
            double bestSampled = -1;
            for (int k = 0; k < tris; k++) {
                Vector3d e1 = new Vector3d(xyz[9 * k + 3] - xyz[9 * k], xyz[9 * k + 4] - xyz[9 * k + 1], xyz[9 * k + 5] - xyz[9 * k + 2]);
                Vector3d e2 = new Vector3d(xyz[9 * k + 6] - xyz[9 * k], xyz[9 * k + 7] - xyz[9 * k + 1], xyz[9 * k + 8] - xyz[9 * k + 2]);
                Vector3d nk = e1.cross(e2).normalize();
                normals[3 * k] = nk.x;
                normals[3 * k + 1] = nk.y;
                normals[3 * k + 2] = nk.z;
                assertTrue(cone.x * nk.x + cone.y * nk.y + cone.z * nk.z >= cosA, "cone contains normal " + k + " trial " + trial);
            }
            for (int s = 0; s < 4000; s++) { // no sampled axis near a0 gives a tighter cone
                Vector3d ax = new Vector3d(a0).add(r.nextGaussian() * 0.3, r.nextGaussian() * 0.3, r.nextGaussian() * 0.3).normalize();
                double m = 1;
                for (int k = 0; k < tris; k++) {
                    m = Math.min(m, ax.x * normals[3 * k] + ax.y * normals[3 * k + 1] + ax.z * normals[3 * k + 2]);
                }
                bestSampled = Math.max(bestSampled, m);
            }
            if (bestSampled > 0.01) {
                assertTrue(cosA >= bestSampled - 1e-4, "tight: " + cosA + " vs sampled " + bestSampled);
            }
            int q = MeshletCones.quantize(cone.x, cone.y, cone.z, cone.w);
            MeshletCones.dequantize(q, qcone);
            for (int k = 0; k < tris; k++) {
                assertTrue(qcone.x * normals[3 * k] + qcone.y * normals[3 * k + 1] + qcone.z * normals[3 * k + 2] >= qcone.w,
                        "quantized cone contains normal " + k);
            }
            Vector4f sphere = BoundingVolumesf.minimalSphere(xyz, 3 * tris, new Vector4f());
            for (int c = 0; c < 200; c++) {
                float ex = (float) (r.nextGaussian() * 6), ey = (float) (r.nextGaussian() * 6), ez = (float) (r.nextGaussian() * 6);
                for (Vector4f cn : new Vector4f[] {cone, qcone}) {
                    if (!MeshletCones.isBackfacing(sphere.x, sphere.y, sphere.z, sphere.w, cn.x, cn.y, cn.z, cn.w, ex, ey, ez)) {
                        continue;
                    }
                    if (cn == cone) {
                        culled++;
                    } else {
                        qculled++;
                    }
                    for (int k = 0; k < tris; k++) {
                        for (int j = 0; j < 3; j++) {
                            double vx = xyz[9 * k + 3 * j] - ex, vy = xyz[9 * k + 3 * j + 1] - ey, vz = xyz[9 * k + 3 * j + 2] - ez;
                            assertTrue(normals[3 * k] * vx + normals[3 * k + 1] * vy + normals[3 * k + 2] * vz > 0,
                                    "culled a cluster with a front-facing triangle, trial " + trial);
                        }
                    }
                }
            }
        }
        assertTrue(culled > 1000 && qculled > 500, "the test culls: " + culled + " / " + qculled);
    }

    @Test
    void transformedSpheresAndFrustumPacking() {
        SplittableRandom r = new SplittableRandom(5);
        Vector4d out = new Vector4d();
        Vector4f outf = new Vector4f();
        for (int i = 0; i < 20000; i++) {
            Matrix4x3d m = new Matrix4x3d().translation(r.nextDouble(-9, 9), r.nextDouble(-9, 9), r.nextDouble(-9, 9));
            m.rotateXYZ(r.nextDouble(-3, 3), r.nextDouble(-3, 3), r.nextDouble(-3, 3));
            if (i % 3 == 0) {
                m.scale(2, 2, 0.5); // a repeated singular value
            } else {
                Matrix3d shear = new Matrix3d(1, r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), 1, 0, 0, 0,
                        r.nextDouble(0.2, 3));
                m.mul(new Matrix4x3d().set(shear));
            }
            Matrix3d lin = new Matrix3d().set(m);
            Vector3d sigma = Svd3d.decompose(lin, new Matrix3d(), new Vector3d(), new Matrix3d());
            double exact = 1.5 * Math.abs(sigma.x);
            BoundingVolumesd.transformSphere(new Vector4d(0.3, -0.2, 0.7, 1.5), m, out);
            assertTrue(out.w >= exact * (1 - 1e-15) && out.w <= exact * (1 + 1e-13), "double radius " + out.w + " vs " + exact);
            Vector3d c = m.transformPosition(new Vector3d(0.3, -0.2, 0.7));
            assertTrue(c.distance(out.x, out.y, out.z) < 1e-12);
            Matrix4x3f mf = new Matrix4x3f().set((float) m.m00(), (float) m.m01(), (float) m.m02(), (float) m.m10(),
                    (float) m.m11(), (float) m.m12(), (float) m.m20(), (float) m.m21(), (float) m.m22(), (float) m.m30(),
                    (float) m.m31(), (float) m.m32());
            BoundingVolumesf.transformSphere(new Vector4f(0.3f, -0.2f, 0.7f, 1.5f), mf, outf);
            Matrix3d linf = new Matrix3d(mf.m00(), mf.m01(), mf.m02(), mf.m10(), mf.m11(), mf.m12(), mf.m20(), mf.m21(), mf.m22());
            double exactF = 1.5 * Math.abs(Svd3d.decompose(linf, new Matrix3d(), new Vector3d(), new Matrix3d()).x);
            assertTrue(outf.w >= exactF, "float radius never under-covers " + outf.w + " vs " + exactF);
            // the largest-column shortcut under-covers sheared matrices
            if (i % 3 != 0) {
                double col = Math.max(new Vector3d(lin.m00, lin.m01, lin.m02).length(),
                        Math.max(new Vector3d(lin.m10, lin.m11, lin.m12).length(), new Vector3d(lin.m20, lin.m21, lin.m22).length()));
                assertTrue(col <= Math.abs(sigma.x) * (1 + 1e-12));
            }
        }
        FrustumPlanes fp = new FrustumPlanes().set(new Matrix4f().perspective(1.1f, 1.5f, 0.1f, 100f, true), true, true);
        float[] packed = fp.getVec4Array(new float[26], 2);
        for (int p = 0; p < FrustumPlanes.COUNT; p++) {
            assertEquals(fp.x[p], packed[2 + 4 * p], 0f);
            assertEquals(fp.y[p], packed[3 + 4 * p], 0f);
            assertEquals(fp.z[p], packed[4 + 4 * p], 0f);
            assertEquals(fp.w[p], packed[5 + 4 * p], 0f);
        }
    }
}
