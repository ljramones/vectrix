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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;

import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.curve.CurveReparameterizer3f;
import org.dynamisengine.vectrix.curve.vec3.CatmullRom3f;
import org.dynamisengine.vectrix.curve.vec3.UniformBSpline3d;
import org.dynamisengine.vectrix.curve.vec3.UniformBSpline3f;
import org.dynamisengine.vectrix.curve.vec4.CatmullRom4f;
import org.dynamisengine.vectrix.sh.ShCoeffs16d;
import org.dynamisengine.vectrix.sh.ShCoeffs16f;
import org.dynamisengine.vectrix.sh.ShCoeffs9d;
import org.dynamisengine.vectrix.sh.ShCoeffs9f;
import org.dynamisengine.vectrix.sh.ShConvolution;
import org.dynamisengine.vectrix.sh.ShProjection;
import org.junit.jupiter.api.Test;

/**
 * Review finding M-14: hot-path APIs must not allocate. {@code squadControlPoint} allocated eight quaternions per
 * call, the SH convenience overloads a basis array, and the curve batch evaluators one vector per sample. The
 * results must be unchanged, so each rewrite is also compared bit for bit with the previous algorithm.
 */
class HotPathAllocationTest {

    private static final Object THREADS;
    private static final Method ALLOCATED;

    static {
        try {
            // Through reflection: the library module does not require java.management, and reflection implies
            // readability.
            THREADS = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null);
            ALLOCATED = Class.forName("com.sun.management.ThreadMXBean").getMethod("getCurrentThreadAllocatedBytes");
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static double bytesPerCall(Runnable r) {
        try {
            for (int i = 0; i < 2000; i++) {
                r.run();
            }
            int n = 20000;
            long before = (Long) ALLOCATED.invoke(THREADS);
            for (int i = 0; i < n; i++) {
                r.run();
            }
            long after = (Long) ALLOCATED.invoke(THREADS);
            return (double) (after - before) / n;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertNoAllocation(String what, Runnable r) {
        double b = bytesPerCall(r);
        assertTrue(b < 1.0, what + " allocates " + b + " bytes per call");
    }

    @Test
    void hotPathsDoNotAllocate() {
        Quaternionf q = new Quaternionf().rotationXYZ(0.1f, 0.2f, 0.3f), p = new Quaternionf().rotationXYZ(0.2f, 0.1f, 0f);
        Quaternionf n = new Quaternionf().rotationXYZ(0.3f, 0.4f, 0.1f), d = new Quaternionf();
        assertNoAllocation("Quaternionf.squadControlPoint", () -> q.squadControlPoint(p, n, d));
        assertNoAllocation("Quaternionf.squad", () -> q.squad(p, n, p, 0.3f, d));
        Quaterniond qd = new Quaterniond(q.x, q.y, q.z, q.w), pd = new Quaterniond(p.x, p.y, p.z, p.w);
        Quaterniond nd = new Quaterniond(n.x, n.y, n.z, n.w), dd = new Quaterniond();
        assertNoAllocation("Quaterniond.squadControlPoint", () -> qd.squadControlPoint(pd, nd, dd));
        assertNoAllocation("Quaterniond.squad", () -> qd.squad(pd, nd, pd, 0.3, dd));

        Vector3f p0 = new Vector3f(0, 0, 0), p1 = new Vector3f(1, 2, 0), p2 = new Vector3f(3, -1, 0), p3 = new Vector3f(4, 0, 1);
        float[] t = new float[64], ox = new float[64], oy = new float[64], oz = new float[64];
        for (int i = 0; i < 64; i++) {
            t[i] = i / 63f;
        }
        assertNoAllocation("CatmullRom3f.evaluateBatch", () -> CatmullRom3f.evaluateBatch(p0, p1, p2, p3, 0.5f, t, 0, 64, ox, 0, oy, 0, oz, 0));
        UniformBSpline3f spline = new UniformBSpline3f(new Vector3f[] {p0, p1, p2, p3, p0});
        assertNoAllocation("UniformBSpline3f.evaluateBatch", () -> spline.evaluateBatch(1, t, 0, 64, ox, 0, oy, 0, oz, 0));

        Vector3f dir = new Vector3f(0, 0.6f, 0.8f), rgb = new Vector3f(1, 1, 1), out = new Vector3f();
        ShCoeffs9f c9 = new ShCoeffs9f();
        ShCoeffs16f c16 = new ShCoeffs16f();
        assertNoAllocation("ShProjection.accumulateSampleL2", () -> ShProjection.accumulateSampleL2(dir, rgb, 0.01f, c9));
        assertNoAllocation("ShProjection.accumulateSampleL3", () -> ShProjection.accumulateSampleL3(dir, rgb, 0.01f, c16));
        assertNoAllocation("ShConvolution.evaluateL2", () -> ShConvolution.evaluateL2(c9, dir, out));
        assertNoAllocation("ShConvolution.evaluateL3", () -> ShConvolution.evaluateL3(c16, dir, out));
        Vector3d dird = new Vector3d(0, 0.6, 0.8), rgbd = new Vector3d(1, 1, 1), outd = new Vector3d();
        ShCoeffs9d c9d = new ShCoeffs9d();
        ShCoeffs16d c16d = new ShCoeffs16d();
        assertNoAllocation("ShProjection.accumulateSampleL2 (d)", () -> ShProjection.accumulateSampleL2(dird, rgbd, 0.01, c9d));
        assertNoAllocation("ShConvolution.evaluateL3 (d)", () -> ShConvolution.evaluateL3(c16d, dird, outd));

        // linalg: per-frame users (shape matching, deformation) call these per cluster or element
        org.dynamisengine.vectrix.core.Matrix3f m3 = new org.dynamisengine.vectrix.core.Matrix3f().rotateXYZ(0.3f, 0.2f, 0.1f)
                .scale(2f, 1f, 0.5f);
        org.dynamisengine.vectrix.core.Matrix3f u3 = new org.dynamisengine.vectrix.core.Matrix3f();
        org.dynamisengine.vectrix.core.Matrix3f v3 = new org.dynamisengine.vectrix.core.Matrix3f();
        Vector3f s3 = new Vector3f(0.1f, 0.2f, 0.3f);
        assertNoAllocation("Svd3f.decompose", () -> org.dynamisengine.vectrix.linalg.Svd3f.decompose(m3, u3, s3, v3));
        assertNoAllocation("Eigen3f.symmetric", () -> org.dynamisengine.vectrix.linalg.Eigen3f.symmetric(m3, s3, u3));
        assertNoAllocation("Polar3f.decompose", () -> org.dynamisengine.vectrix.linalg.Polar3f.decompose(m3, u3, v3));
        assertNoAllocation("Solversf.solve", () -> org.dynamisengine.vectrix.linalg.Solversf.solve(m3, s3, s3));
        assertNoAllocation("Lie3f.logSo3", () -> org.dynamisengine.vectrix.linalg.Lie3f.logSo3(
                org.dynamisengine.vectrix.linalg.Lie3f.expSo3(s3, u3), s3));

        // Table builders allocate two vectors per table (setup cost), no longer one per sample.
        float[] table = new float[257];
        double perTable = bytesPerCall(() -> CurveReparameterizer3f.buildArcLengthTableForBezier(p0, p1, p2, p3, 256, table));
        assertTrue(perTable <= 64, "arc-length table builder allocates " + perTable + " bytes per table");
    }

    /** The previous squadControlPoint, written with the public API (it allocated eight quaternions). */
    private static Quaternionf referenceControlPoint(Quaternionf q, Quaternionf prev, Quaternionf next) {
        Quaternionf pv = new Quaternionf(prev), nx = new Quaternionf(next);
        if (q.dot(pv) < 0) {
            pv.set(-pv.x, -pv.y, -pv.z, -pv.w);
        }
        if (q.dot(nx) < 0) {
            nx.set(-nx.x, -nx.y, -nx.z, -nx.w);
        }
        Quaternionf inv = q.invert(new Quaternionf());
        Quaternionf lp = inv.mul(pv, new Quaternionf()).log(new Quaternionf());
        Quaternionf ln = inv.mul(nx, new Quaternionf()).log(new Quaternionf());
        Quaternionf e = new Quaternionf((lp.x + ln.x) * -0.25f, (lp.y + ln.y) * -0.25f, (lp.z + ln.z) * -0.25f, 0f).exp(new Quaternionf());
        return q.mul(e, new Quaternionf()).normalize();
    }

    @Test
    void squadResultsAreUnchangedAndAliasSafe() {
        Quaternionf q = new Quaternionf().rotationXYZ(0.4f, -0.7f, 1.1f);
        Quaternionf prev = new Quaternionf().rotationXYZ(-0.2f, 0.3f, 0.9f);
        Quaternionf next = new Quaternionf().rotationXYZ(1.0f, -0.1f, 0.5f);
        Quaternionf expected = referenceControlPoint(q, prev, next);
        assertEquals(expected, q.squadControlPoint(prev, next, new Quaternionf()));
        Quaternionf self = new Quaternionf(q);
        assertEquals(expected, self.squadControlPoint(prev, next, self), "dest == this");
        Quaternionf pAlias = new Quaternionf(prev);
        assertEquals(expected, q.squadControlPoint(pAlias, next, pAlias), "dest == prev");
        Quaternionf nAlias = new Quaternionf(next);
        assertEquals(expected, q.squadControlPoint(prev, nAlias, nAlias), "dest == next");

        Quaternionf s0 = q.squadControlPoint(prev, next, new Quaternionf());
        Quaternionf s1 = next.squadControlPoint(q, prev, new Quaternionf());
        for (float t : new float[] {0f, 0.25f, 0.5f, 0.9f}) {
            Quaternionf eb = q.slerp(next, t, new Quaternionf());
            Quaternionf cb = new Quaternionf(s0).slerp(s1, t, new Quaternionf());
            Quaternionf ref = eb.slerp(cb, 2f * t * (1f - t), new Quaternionf());
            assertEquals(ref, q.squad(next, s0, s1, t, new Quaternionf()), "t=" + t);
            Quaternionf a = new Quaternionf(q);
            assertEquals(ref, a.squad(next, s0, s1, t, a), "dest == this, t=" + t);
            Quaternionf b = new Quaternionf(next);
            assertEquals(ref, q.squad(b, s0, s1, t, b), "dest == q1, t=" + t);
            Quaternionf c = new Quaternionf(s1);
            assertEquals(ref, q.squad(next, s0, c, t, c), "dest == s1, t=" + t);
        }
    }

    @Test
    void curveBatchesMatchPointEvaluation() {
        Vector3f p0 = new Vector3f(0, 0, 0), p1 = new Vector3f(1, 2, 0), p2 = new Vector3f(3, -1, 0), p3 = new Vector3f(4, 0, 1);
        float[] t = {0f, 0.1f, 0.5f, 0.77f, 1f};
        float[] ox = new float[5], oy = new float[5], oz = new float[5];
        CatmullRom3f.evaluateBatch(p0, p1, p2, p3, 0.5f, t, 0, 5, ox, 0, oy, 0, oz, 0);
        for (int i = 0; i < 5; i++) {
            Vector3f v = CatmullRom3f.evaluate(p0, p1, p2, p3, t[i], 0.5f, new Vector3f());
            assertEquals(v.x, ox[i], 0f);
            assertEquals(v.y, oy[i], 0f);
            assertEquals(v.z, oz[i], 0f);
        }
        Vector3d[] cp = {new Vector3d(0, 0, 0), new Vector3d(1, 2, 0), new Vector3d(3, -1, 0), new Vector3d(4, 0, 1)};
        UniformBSpline3d spline = new UniformBSpline3d(cp);
        double[] td = {0, 0.3, 1}, dx = new double[3], dy = new double[3], dz = new double[3];
        spline.evaluateBatch(0, td, 0, 3, dx, 0, dy, 0, dz, 0);
        Vector3d atHalf = spline.evaluate(0, 0.3, new Vector3d());
        assertArrayEquals(new double[] {atHalf.x, atHalf.y, atHalf.z}, new double[] {dx[1], dy[1], dz[1]}, 0.0);
        // Defensive copy: mutating the caller's control points does not change the curve.
        cp[1].set(100, 100, 100);
        assertEquals(atHalf, spline.evaluate(0, 0.3, new Vector3d()));
    }

    @Test
    void catmullRom4fDerivativeIsAliasSafe() {
        Vector4f p0 = new Vector4f(0, 0, 0, 0), p1 = new Vector4f(1, 2, 0, 1), p2 = new Vector4f(3, -1, 0, 2), p3 = new Vector4f(4, 0, 1, 3);
        Vector4f expected = CatmullRom4f.derivative(p0, p1, p2, p3, 0.4f, 0.5f, new Vector4f());
        Vector4f alias = new Vector4f(p1);
        // It used to evaluate the curve into dest first, corrupting p1 when dest == p1.
        assertEquals(expected, CatmullRom4f.derivative(p0, alias, p2, p3, 0.4f, 0.5f, alias));
    }
}
