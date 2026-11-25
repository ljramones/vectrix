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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Matrix3f;
import org.dynamisengine.vectrix.core.Matrix4x3d;
import org.dynamisengine.vectrix.core.Matrix4x3f;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.dsp.ParticleFilter;
import org.dynamisengine.vectrix.dsp.Spectra;
import org.dynamisengine.vectrix.dsp.Wiener;
import org.dynamisengine.vectrix.fft.ComplexFftd;
import org.dynamisengine.vectrix.fft.RealFft2d;
import org.dynamisengine.vectrix.fft.RealFftd;
import org.dynamisengine.vectrix.geometry.BoundingVolumesd;
import org.dynamisengine.vectrix.geometry.BoundingVolumesf;
import org.dynamisengine.vectrix.hash.Philox;
import org.dynamisengine.vectrix.nn.Formats;
import org.junit.jupiter.api.Test;

/**
 * The last 3.0 fixes: conservative capsule, OBB and transformed-point bounds, up-front checks in every curve batch
 * evaluator, defined int8 scales for tiny blocks, float overloads of {@code Spectra} and {@code Wiener}, and
 * Philox-driven particle resampling.
 */
class LastIssues30Test {

    private static BigDecimal bd(double v) {
        return new BigDecimal(v);
    }

    /** Asserts {@code min <= lo}, {@code max >= hi}, and that both are within {@code ulps} of the exact bounds. */
    /** The double version: within {@code ulps} ulps of {@code magnitude}, the size of the terms that were summed. */
    private static void assertContainsTightly(double min, double max, BigDecimal lo, BigDecimal hi, double ulps,
            double magnitude, String what) {
        assertTrue(bd(min).compareTo(lo) <= 0, what + ": min " + min + " cuts into " + lo);
        assertTrue(bd(max).compareTo(hi) >= 0, what + ": max " + max + " cuts into " + hi);
        double tol = ulps * Math.ulp(Math.max(magnitude, Math.max(Math.abs(min), Math.abs(max))));
        assertTrue(lo.subtract(bd(min)).doubleValue() <= tol, what + ": min not tight " + min);
        assertTrue(bd(max).subtract(hi).doubleValue() <= tol, what + ": max not tight " + max);
    }

    /** The float version: within 2 float ulps (the double bound may sit just past a float). */
    private static void assertContainsTightly(float min, float max, BigDecimal lo, BigDecimal hi, String what) {
        assertTrue(bd(min).compareTo(lo) <= 0, what + ": min " + min + " cuts into " + lo);
        assertTrue(bd(max).compareTo(hi) >= 0, what + ": max " + max + " cuts into " + hi);
        assertTrue(lo.subtract(bd(min)).doubleValue() <= 2 * Math.ulp(min), what + ": min not tight " + min);
        assertTrue(bd(max).subtract(hi).doubleValue() <= 2 * Math.ulp(max), what + ": max not tight " + max);
    }

    // ---- BoundingVolumes: capsule, OBB and transformed points round outward

    @Test
    void capsuleBoundsContainTheCapsule() {
        SplittableRandom r = new SplittableRandom(31);
        Vector3f minF = new Vector3f(), maxF = new Vector3f();
        Vector3d minD = new Vector3d(), maxD = new Vector3d();
        for (int i = 0; i < 4000; i++) {
            double span = i % 2 == 0 ? 1e5 : 10;
            float[] a = new float[3];
            float[] b = new float[3];
            for (int k = 0; k < 3; k++) {
                a[k] = (float) r.nextDouble(-span, span);
                b[k] = (float) r.nextDouble(-span, span);
            }
            float rad = (float) r.nextDouble(0, 3);
            BoundingVolumesf.aabOfCapsule(new Vector3f(a[0], a[1], a[2]), new Vector3f(b[0], b[1], b[2]), rad, minF,
                    maxF);
            BoundingVolumesd.aabOfCapsule(new Vector3d(a[0], a[1], a[2]), new Vector3d(b[0], b[1], b[2]), rad, minD,
                    maxD);
            for (int k = 0; k < 3; k++) {
                BigDecimal lo = bd(Math.min(a[k], b[k])).subtract(bd(rad));
                BigDecimal hi = bd(Math.max(a[k], b[k])).add(bd(rad));
                assertContainsTightly(minF.get(k), maxF.get(k), lo, hi, "float capsule " + i + " axis " + k);
                assertContainsTightly(minD.get(k), maxD.get(k), lo, hi, 1, 0, "double capsule " + i + " axis " + k);
            }
        }
        // exact cases stay exact
        BoundingVolumesf.aabOfCapsule(new Vector3f(1, 2, 3), new Vector3f(-1, 0, 5), 0.5f, minF, maxF);
        assertEquals(new Vector3f(-1.5f, -0.5f, 2.5f), minF);
        assertEquals(new Vector3f(1.5f, 2.5f, 5.5f), maxF);
    }

    @Test
    void obbBoundsContainTheBox() {
        SplittableRandom r = new SplittableRandom(32);
        Vector3f minF = new Vector3f(), maxF = new Vector3f();
        Vector3d minD = new Vector3d(), maxD = new Vector3d();
        for (int i = 0; i < 4000; i++) {
            double span = i % 2 == 0 ? 1e5 : 10;
            Matrix3f af = new Matrix3f().set(new Quaternionf().rotateXYZ((float) r.nextDouble(-3, 3),
                    (float) r.nextDouble(-3, 3), (float) r.nextDouble(-3, 3)));
            Vector3f cf = new Vector3f((float) r.nextDouble(-span, span), (float) r.nextDouble(-span, span),
                    (float) r.nextDouble(-span, span));
            Vector3f hf = new Vector3f((float) r.nextDouble(0, 5), (float) r.nextDouble(0, 5),
                    (float) r.nextDouble(0, 5));
            BoundingVolumesf.aabOfObb(cf, af, hf, minF, maxF);
            float[][] colsF = {{af.m00(), af.m01(), af.m02()}, {af.m10(), af.m11(), af.m12()},
                {af.m20(), af.m21(), af.m22()}};
            for (int k = 0; k < 3; k++) {
                BigDecimal e = bd(Math.abs(colsF[0][k])).multiply(bd(hf.x))
                        .add(bd(Math.abs(colsF[1][k])).multiply(bd(hf.y)))
                        .add(bd(Math.abs(colsF[2][k])).multiply(bd(hf.z)));
                assertContainsTightly(minF.get(k), maxF.get(k), bd(cf.get(k)).subtract(e), bd(cf.get(k)).add(e),
                        "float obb " + i + " axis " + k);
            }
            Matrix3d ad = new Matrix3d().set(new Quaterniond().rotateXYZ(r.nextDouble(-3, 3), r.nextDouble(-3, 3),
                    r.nextDouble(-3, 3)));
            Vector3d cd = new Vector3d(r.nextDouble(-span, span), r.nextDouble(-span, span), r.nextDouble(-span, span));
            Vector3d hd = new Vector3d(r.nextDouble(0, 5), r.nextDouble(0, 5), r.nextDouble(0, 5));
            BoundingVolumesd.aabOfObb(cd, ad, hd, minD, maxD);
            double[][] colsD = {{ad.m00(), ad.m01(), ad.m02()}, {ad.m10(), ad.m11(), ad.m12()},
                {ad.m20(), ad.m21(), ad.m22()}};
            for (int k = 0; k < 3; k++) {
                BigDecimal e = bd(Math.abs(colsD[0][k])).multiply(bd(hd.x))
                        .add(bd(Math.abs(colsD[1][k])).multiply(bd(hd.y)))
                        .add(bd(Math.abs(colsD[2][k])).multiply(bd(hd.z)));
                assertContainsTightly(minD.get(k), maxD.get(k), bd(cd.get(k)).subtract(e), bd(cd.get(k)).add(e), 4,
                        Math.abs(cd.get(k)) + e.doubleValue(),
                        "double obb " + i + " axis " + k);
            }
        }
        // an axis-aligned box with representable corners is exact
        BoundingVolumesf.aabOfObb(new Vector3f(1, 2, 3), new Matrix3f(), new Vector3f(0.5f, 1, 2), minF, maxF);
        assertEquals(new Vector3f(0.5f, 1, 1), minF);
        assertEquals(new Vector3f(1.5f, 3, 5), maxF);
        BoundingVolumesd.aabOfObb(new Vector3d(1, 2, 3), new Matrix3d(), new Vector3d(0.5, 1, 0), minD, maxD);
        assertEquals(new Vector3d(0.5, 1, 3), minD);
        assertEquals(new Vector3d(1.5, 3, 3), maxD);
    }

    @Test
    void transformedPointBoundsContainThePoints() {
        SplittableRandom r = new SplittableRandom(33);
        Vector3f minF = new Vector3f(), maxF = new Vector3f();
        Vector3d minD = new Vector3d(), maxD = new Vector3d();
        int count = 12;
        for (int i = 0; i < 1500; i++) {
            double span = i % 2 == 0 ? 1e5 : 10;
            Matrix4x3f mf = new Matrix4x3f().translate((float) r.nextDouble(-span, span),
                    (float) r.nextDouble(-span, span), (float) r.nextDouble(-span, span))
                    .rotateXYZ((float) r.nextDouble(-3, 3), (float) r.nextDouble(-3, 3), (float) r.nextDouble(-3, 3))
                    .scale((float) r.nextDouble(0.2, 3), (float) r.nextDouble(0.2, 3), (float) r.nextDouble(0.2, 3));
            Matrix4x3d md = new Matrix4x3d().translate(r.nextDouble(-span, span), r.nextDouble(-span, span),
                    r.nextDouble(-span, span)).rotateXYZ(r.nextDouble(-3, 3), r.nextDouble(-3, 3), r.nextDouble(-3, 3))
                    .scale(r.nextDouble(0.2, 3), r.nextDouble(0.2, 3), r.nextDouble(0.2, 3));
            float[] pf = new float[3 * count];
            double[] pd = new double[3 * count];
            for (int j = 0; j < 3 * count; j++) {
                pf[j] = (float) r.nextDouble(-100, 100);
                pd[j] = r.nextDouble(-100, 100);
            }
            BoundingVolumesf.aabOfTransformedPoints(pf, count, mf, minF, maxF);
            BoundingVolumesd.aabOfTransformedPoints(pd, count, md, minD, maxD);
            double[][] mF = {{mf.m00(), mf.m10(), mf.m20(), mf.m30()}, {mf.m01(), mf.m11(), mf.m21(), mf.m31()},
                {mf.m02(), mf.m12(), mf.m22(), mf.m32()}};
            double[][] mD = {{md.m00(), md.m10(), md.m20(), md.m30()}, {md.m01(), md.m11(), md.m21(), md.m31()},
                {md.m02(), md.m12(), md.m22(), md.m32()}};
            for (int k = 0; k < 3; k++) {
                BigDecimal loF = null, hiF = null, loD = null, hiD = null;
                for (int j = 0; j < count; j++) {
                    BigDecimal vf = exact(mF[k], pf[3 * j], pf[3 * j + 1], pf[3 * j + 2]);
                    BigDecimal vd = exact(mD[k], pd[3 * j], pd[3 * j + 1], pd[3 * j + 2]);
                    loF = loF == null || vf.compareTo(loF) < 0 ? vf : loF;
                    hiF = hiF == null || vf.compareTo(hiF) > 0 ? vf : hiF;
                    loD = loD == null || vd.compareTo(loD) < 0 ? vd : loD;
                    hiD = hiD == null || vd.compareTo(hiD) > 0 ? vd : hiD;
                }
                assertContainsTightly(minF.get(k), maxF.get(k), loF, hiF, "float points " + i + " axis " + k);
                assertContainsTightly(minD.get(k), maxD.get(k), loD, hiD, 8,
                        100 * (Math.abs(mD[k][0]) + Math.abs(mD[k][1]) + Math.abs(mD[k][2])) + Math.abs(mD[k][3]),
                        "double points " + i + " axis " + k);
            }
        }
        // a translation of representable points is exact
        BoundingVolumesf.aabOfTransformedPoints(new float[] {1, 2, 3, -4, 5, 6}, 2, new Matrix4x3f().translation(10, 0,
                0), minF, maxF);
        assertEquals(new Vector3f(6, 2, 3), minF);
        assertEquals(new Vector3f(11, 5, 6), maxF);
        BoundingVolumesd.aabOfTransformedPoints(new double[0], 0, new Matrix4x3d(), minD, maxD);
        assertEquals(Double.POSITIVE_INFINITY, minD.x);
        assertEquals(Double.NEGATIVE_INFINITY, maxD.z);
    }

    private static BigDecimal exact(double[] row, double x, double y, double z) {
        return bd(row[0]).multiply(bd(x)).add(bd(row[1]).multiply(bd(y))).add(bd(row[2]).multiply(bd(z)))
                .add(bd(row[3]));
    }

    // ---- curve batch evaluators check their arrays up front

    @Test
    void everyCurveBatchEvaluatorChecksItsArraysBeforeWriting() throws Exception {
        int checked = 0;
        for (String pkg : new String[] {"scalar", "vec2", "vec3", "vec4"}) {
            String dim = pkg.equals("scalar") ? "1" : pkg.substring(3);
            for (String kind : new String[] {"Bezier", "Hermite", "CatmullRom", "UniformBSpline"}) {
                for (String precision : new String[] {"f", "d"}) {
                    Class<?> c = Class.forName("org.dynamisengine.vectrix.curve." + pkg + "." + kind + dim + precision);
                    for (Method m : c.getMethods()) {
                        if (m.getName().equals("evaluateBatch")) {
                            checkBatch(c, m);
                            checked++;
                        }
                    }
                }
            }
        }
        assertEquals(32, checked);
    }

    private static void checkBatch(Class<?> c, Method m) throws Exception {
        Class<?>[] types = m.getParameterTypes();
        int first = 0;
        while (!types[first].isArray()) {
            first++;
        }
        Class<?> elem = types[first].getComponentType();
        int arrays = (types.length - first - 1) / 2; // tValues, tOffset, length, then (out, outOffset) pairs
        Object target = Modifier.isStatic(m.getModifiers()) ? null : spline(c);
        Object[] prefix = new Object[first];
        for (int i = 0; i < first; i++) {
            prefix[i] = types[i] == float.class ? (Object) 0.25f : types[i] == double.class ? (Object) 0.25
                    : types[i] == int.class ? (Object) 0 : vector(types[i]);
        }
        String what = c.getSimpleName() + "." + m.getName();
        // valid: 5 values from offset 1 of 6-element arrays
        Object[] args = batchArgs(prefix, elem, arrays, 6, -1, 1, -1, 5, -1);
        m.invoke(target, args);
        for (int a = 1; a < arrays; a++) {
            assertNotEquals(7.0, ((Number) Array.get(args[first + 1 + 2 * a], 5)).doubleValue(), what + " wrote");
        }
        for (int a = 0; a < arrays; a++) {
            assertRejected(m, target, batchArgs(prefix, elem, arrays, 6, a, 1, -1, 6, -1), first, arrays,
                    what + " short array " + a);
            assertRejected(m, target, batchArgs(prefix, elem, arrays, 6, -1, 1, a, 5, -1), first, arrays,
                    what + " negative offset " + a);
            assertRejected(m, target, batchArgs(prefix, elem, arrays, 6, -1, 1, -1, 5, a), first, arrays,
                    what + " null array " + a);
        }
        assertRejected(m, target, batchArgs(prefix, elem, arrays, 6, -1, 1, -1, -1, -1), first, arrays,
                what + " negative length");
    }

    /**
     * Arguments with {@code arrays} arrays of {@code len} elements filled with 7 (array {@code shortOne} one
     * shorter, array {@code nullOne} null), each at offset {@code off} (array {@code negOne} at -1), for
     * {@code count} values; tValues come first, then the outputs.
     */
    private static Object[] batchArgs(Object[] prefix, Class<?> elem, int arrays, int len, int shortOne, int off,
            int negOne, int count, int nullOne) {
        Object[] args = new Object[prefix.length + 1 + 2 * arrays];
        System.arraycopy(prefix, 0, args, 0, prefix.length);
        int p = prefix.length;
        for (int a = 0; a < arrays; a++) {
            Object arr = Array.newInstance(elem, a == shortOne ? len - 1 : len);
            for (int i = 0; i < Array.getLength(arr); i++) {
                Array.set(arr, i, elem == float.class ? (Object) (a == 0 ? 0.5f : 7f) : (Object) (a == 0 ? 0.5 : 7.0));
            }
            args[p++] = a == nullOne ? null : arr;
            args[p++] = a == negOne ? -1 : off;
            if (a == 0) {
                args[p++] = count;
            }
        }
        return args;
    }

    private static void assertRejected(Method m, Object target, Object[] args, int first, int arrays, String what)
            throws Exception {
        InvocationTargetException e = assertThrows(InvocationTargetException.class, () -> m.invoke(target, args),
                what);
        assertInstanceOf(IllegalArgumentException.class, e.getCause(), what);
        for (int a = 1; a < arrays; a++) {
            Object out = args[first + 1 + 2 * a];
            for (int i = 0; out != null && i < Array.getLength(out); i++) {
                assertEquals(7.0, ((Number) Array.get(out, i)).doubleValue(), what + ": wrote output " + a);
            }
        }
    }

    private static Object vector(Class<?> readOnly) throws Exception {
        String name = readOnly.getName();
        return Class.forName(name.substring(0, name.length() - 1)).getConstructor().newInstance();
    }

    private static Object spline(Class<?> c) throws Exception {
        Class<?> param = c.getConstructors()[0].getParameterTypes()[0];
        Class<?> elem = param.getComponentType();
        Object points = Array.newInstance(elem, 5);
        for (int i = 0; i < 5; i++) {
            Array.set(points, i, elem.isPrimitive() ? (elem == float.class ? (Object) (float) i : (Object) (double) i)
                    : vector(elem));
        }
        return c.getConstructors()[0].newInstance(points);
    }

    // ---- nn.Formats.quantizeInt8: tiny blocks

    @Test
    void tinyInt8BlocksKeepMeaningfulCodes() {
        float[] x = new float[64];
        for (int j = 0; j < 32; j++) {
            x[j] = (j - 16) * Float.MIN_VALUE;   // subnormals -16..15 ulps: before 3.0 the scale underflowed to 0
        }
        x[32] = 190 * Float.MIN_VALUE;          // 190/127 rounded to nearest is 1 ulp: 190 would clamp to 127
        x[33] = -95 * Float.MIN_VALUE;
        x[34] = 3 * Float.MIN_VALUE;
        byte[] q = new byte[64];
        float[] scales = new float[2];
        Formats.quantizeInt8(x, 0, 64, q, 0, scales, 0);
        assertEquals(Float.MIN_VALUE, scales[0]);
        float[] back = new float[64];
        Formats.dequantizeInt8(q, 0, scales, 0, back, 0, 64);
        for (int j = 0; j < 32; j++) {
            assertEquals(j - 16, q[j]);
            assertEquals(x[j], back[j], "subnormal values are encoded exactly");
        }
        assertEquals(2 * Float.MIN_VALUE, scales[1]);
        assertEquals(95, q[32]);
        assertEquals(-48, q[33]); // -47.5 rounds to even
        assertEquals(2, q[34]);
        for (int j = 32; j < 64; j++) {
            assertTrue(Math.abs(back[j] - x[j]) <= scales[1] / 2, "error within half a scale");
        }

        // random tiny blocks: codes within ±127, error within half a scale, scale never below max|x| / 127
        SplittableRandom r = new SplittableRandom(34);
        float[] blk = new float[32];
        byte[] qb = new byte[32];
        float[] sb = new float[1];
        float[] bb = new float[32];
        for (int i = 0; i < 2000; i++) {
            double mag = Math.scalb(1.0, -149 + r.nextInt(30));
            double amax = 0;
            for (int j = 0; j < 32; j++) {
                blk[j] = (float) (r.nextDouble(-1, 1) * mag);
                amax = Math.max(amax, Math.abs(blk[j]));
            }
            Formats.quantizeInt8(blk, 0, 32, qb, 0, sb, 0);
            Formats.dequantizeInt8(qb, 0, sb, 0, bb, 0, 32);
            if (amax == 0) {
                assertEquals(0f, sb[0]);
                continue;
            }
            assertTrue(sb[0] >= amax / 127, "case " + i);
            int largest = 0;
            for (int j = 0; j < 32; j++) {
                assertTrue(Math.abs(qb[j]) <= 127);
                largest = Math.max(largest, Math.abs(qb[j]));
                assertTrue(Math.abs((double) bb[j] - blk[j]) <= sb[0] * 0.5000001 + Math.ulp(blk[j]),
                        "case " + i + " element " + j);
            }
            assertTrue(largest >= Math.min(63, Math.round(amax / Float.MIN_VALUE)), "case " + i + ": " + largest);
        }

        // normal blocks are unchanged: the scale is max|x| / 127 rounded to nearest, and the largest maps to 127
        float[] normal = new float[32];
        normal[5] = -3.5f;
        normal[9] = 1e-3f;
        Formats.quantizeInt8(normal, 0, 32, qb, 0, sb, 0);
        assertEquals(3.5f / 127f, sb[0]);
        assertEquals(-127, qb[5]);
    }

    // ---- dsp.Spectra and dsp.Wiener: float overloads round the double results once

    private static float[] floats(SplittableRandom r, int n) {
        float[] f = new float[n];
        for (int i = 0; i < n; i++) {
            f[i] = (float) r.nextDouble(-1, 1);
        }
        return f;
    }

    private static double[] widen(float[] f) {
        double[] d = new double[f.length];
        for (int i = 0; i < f.length; i++) {
            d[i] = f[i];
        }
        return d;
    }

    private static float[] narrow(double[] d, int off, int n) {
        float[] f = new float[n];
        for (int i = 0; i < n; i++) {
            f[i] = (float) d[off + i];
        }
        return f;
    }

    private static float[] slice(float[] f, int off, int n) {
        return java.util.Arrays.copyOfRange(f, off, off + n);
    }

    @Test
    void spectraFloatOverloadsRoundTheDoubleResults() {
        SplittableRandom r = new SplittableRandom(35);
        for (int n : new int[] {64, 45}) {
            RealFftd plan = RealFftd.plan(n);
            int bins = n / 2 + 1;
            float[] win = floats(r, n + 2);
            float[] x = floats(r, 400);
            float[] y = floats(r, 400);
            double[] scratch = new double[Spectra.floatCrossScratchSize(plan) + 3];
            for (float[] w : new float[][] {win, null}) {
                double[] wd = w == null ? null : widen(w);
                double[] psd = new double[bins + 1];
                Spectra.periodogram(plan, wd, 2, widen(x), 5, psd, 1, scratch, 3);
                float[] psdF = new float[bins + 2];
                Spectra.periodogram(plan, w, 2, x, 5, psdF, 2, scratch, 3);
                assertArrayEquals(narrow(psd, 1, bins), slice(psdF, 2, bins), "periodogram " + n);

                int segs = Spectra.welch(plan, wd, 2, 17, widen(x), 3, 390, psd, 1, scratch, 3);
                assertEquals(segs, Spectra.welch(plan, w, 2, 17, x, 3, 390, psdF, 2, scratch, 3));
                assertArrayEquals(narrow(psd, 1, bins), slice(psdF, 2, bins), "welch " + n);

                double[] cross = new double[2 * bins];
                Spectra.crossWelch(plan, wd, 2, 20, widen(x), 1, widen(y), 4, 380, cross, 0, scratch, 3);
                float[] crossF = new float[2 * bins];
                Spectra.crossWelch(plan, w, 2, 20, x, 1, y, 4, 380, crossF, 0, scratch, 3);
                assertArrayEquals(narrow(cross, 0, 2 * bins), crossF, "crossWelch " + n);
            }
            float[] untouched = new float[bins];
            assertThrows(IllegalArgumentException.class, () -> Spectra.periodogram(plan, null, 0, x, 0, untouched, 0,
                    new double[Spectra.scratchSize(plan)], 0));
            assertThrows(IllegalArgumentException.class, () -> Spectra.welch(plan, null, 0, 0, x, 0, 400, untouched, 0,
                    new double[Spectra.floatScratchSize(plan)], 0));
            assertArrayEquals(new float[bins], untouched);

            ComplexFftd cplan = ComplexFftd.plan(n);
            double[] an = new double[2 * n];
            Spectra.analytic(cplan, widen(x), 7, an, 0, new double[Spectra.scratchSize(cplan)], 0);
            float[] anF = new float[2 * n + 1];
            Spectra.analytic(cplan, x, 7, anF, 1, new double[Spectra.floatScratchSize(cplan)], 0);
            assertArrayEquals(narrow(an, 0, 2 * n), slice(anF, 1, 2 * n), "analytic " + n);
            float[] inPlace = x.clone();
            Spectra.analytic(cplan, inPlace, 0, inPlace, 0, new double[Spectra.floatScratchSize(cplan)], 0);
            Spectra.analytic(cplan, widen(x), 0, an, 0, new double[Spectra.scratchSize(cplan)], 0);
            assertArrayEquals(narrow(an, 0, 2 * n), slice(inPlace, 0, 2 * n), "analytic in place " + n);
        }
    }

    @Test
    void wienerFloatOverloadsRoundTheDoubleResults() {
        SplittableRandom r = new SplittableRandom(36);
        // fir
        float[] rxx = {2.0f, 1.3f, 0.7f, 0.2f, -0.1f};
        float[] rdx = {1.0f, 0.5f, -0.25f, 0.1f, 0.05f};
        double[] w = new double[5];
        Wiener.fir(widen(rxx), widen(rdx), 5, w, new double[10]);
        float[] wf = new float[5];
        Wiener.fir(rxx, rdx, 5, wf, new double[10]);
        assertArrayEquals(narrow(w, 0, 5), wf, "fir");

        // gain, noncausal, apply
        float[] s = floats(r, 40);
        float[] nz = floats(r, 40);
        s[3] = 0;
        nz[3] = 0;
        double[] g = new double[40];
        Wiener.gain(widen(s), widen(nz), 40, 0.1f, g);
        float[] gf = new float[40];
        Wiener.gain(s, nz, 40, 0.1f, gf);
        assertArrayEquals(narrow(g, 0, 40), gf, "gain");
        float[] pxy = floats(r, 80);
        double[] h = new double[80];
        Wiener.noncausal(widen(s), widen(pxy), 40, h);
        float[] hf = new float[80];
        Wiener.noncausal(s, pxy, 40, hf);
        assertArrayEquals(narrow(h, 0, 80), hf, "noncausal");
        double[] spec = widen(pxy);
        Wiener.apply(spec, 0, widen(gf), 40);
        float[] specF = pxy.clone();
        Wiener.apply(specF, 0, gf, 40);
        assertArrayEquals(narrow(spec, 0, 80), specF, "apply");

        // impulseResponse
        RealFftd plan = RealFftd.plan(32);
        float[] resp = floats(r, plan.spectrumLength());
        double[] taps = new double[32];
        Wiener.impulseResponse(plan, widen(resp), taps, new double[plan.spectrumLength() + plan.scratchSize()]);
        float[] tapsF = new float[32];
        Wiener.impulseResponse(plan, resp, tapsF, new double[plan.spectrumLength() + plan.scratchSize()]);
        assertArrayEquals(narrow(taps, 0, 32), tapsF, "impulseResponse");

        // deconvolve, scalar and per-bin ratios, out = y
        float[] yv = floats(r, 34);
        float[] kern = {0.5f, 0.3f, 0.2f};
        double[] scr = new double[Wiener.deconvolveScratchSize(plan)];
        double[] out = new double[32];
        Wiener.deconvolve(plan, widen(yv), 2, widen(kern), 0, 3, 0.01, out, 0, scr, 0);
        float[] outF = yv.clone();
        Wiener.deconvolve(plan, outF, 2, kern, 0, 3, 0.01, outF, 2, scr, 0);
        assertArrayEquals(narrow(out, 0, 32), slice(outF, 2, 32), "deconvolve");
        float[] nsr = new float[17];
        for (int k = 0; k < 17; k++) {
            nsr[k] = 0.001f * (k + 1);
        }
        Wiener.deconvolve(plan, widen(yv), 2, widen(kern), 0, 3, widen(nsr), out, 0, scr, 0);
        Wiener.deconvolve(plan, yv, 2, kern, 0, 3, nsr, outF, 0, scr, 0);
        assertArrayEquals(narrow(out, 0, 32), slice(outF, 0, 32), "deconvolve per bin");
        nsr[4] = -1;
        float[] keep = new float[32];
        assertThrows(IllegalArgumentException.class, () -> Wiener.deconvolve(plan, yv, 2, kern, 0, 3, nsr, keep, 0,
                scr, 0));
        assertArrayEquals(new float[32], keep);

        // deconvolve2d needs its own scratch size
        RealFft2d p2 = RealFft2d.plan(8, 12);
        float[] img = floats(r, 96);
        float[] k2 = {0.4f, 0.2f, 0.2f, 0.1f, 0.1f, 0f};
        double[] out2 = new double[96];
        Wiener.deconvolve2d(p2, widen(img), 0, widen(k2), 0, 2, 3, 0.02, out2, 0,
                new double[Wiener.deconvolve2dScratchSize(p2)], 0);
        float[] img2 = img.clone();
        Wiener.deconvolve2d(p2, img2, 0, k2, 0, 2, 3, 0.02, img2, 0,
                new double[Wiener.deconvolve2dFloatScratchSize(p2)], 0);
        assertArrayEquals(narrow(out2, 0, 96), img2, "deconvolve2d");
        assertThrows(IllegalArgumentException.class, () -> Wiener.deconvolve2d(p2, img, 0, k2, 0, 2, 3, 0.02, keep, 0,
                new double[Wiener.deconvolve2dScratchSize(p2)], 0));

        // adaptive2d, given and estimated noise, out = img
        for (double noise : new double[] {0.05, 0}) {
            double[] ad = new double[96];
            double nu = Wiener.adaptive2d(widen(img), 8, 12, 1, noise, ad, new double[2 * 9 * 13]);
            float[] adF = img.clone();
            assertEquals(nu, Wiener.adaptive2d(adF, 8, 12, 1, noise, adF, new double[2 * 9 * 13]));
            assertArrayEquals(narrow(ad, 0, 96), adF, "adaptive2d " + noise);
        }
        assertThrows(IllegalArgumentException.class, () -> Wiener.adaptive2d(img, 8, 12, 1, 0, new float[95],
                new double[2 * 9 * 13]));
    }

    // ---- dsp.ParticleFilter: resampling with a Philox uniform

    @Test
    void systematicResampleCanDrawItsUniformFromPhilox() {
        double[] w = {0.1, 0.4, 0.05, 0.3, 0.15};
        int[] a = new int[5];
        int[] b = new int[5];
        for (long step = 0; step < 50; step++) {
            ParticleFilter.systematicResample(w, 5, Philox.uniformDouble(42L, step), a);
            ParticleFilter.systematicResample(w, 5, 42L, step, b);
            assertArrayEquals(a, b, "step " + step);
        }
    }
}
