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

import org.dynamisengine.vectrix.simd.SimdSupport;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.dynamisengine.vectrix.soa.TransformKernels;
import org.dynamisengine.vectrix.core.CoreConfig;
import org.dynamisengine.vectrix.core.MathMode;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.geometry.CullingKernels;
import org.dynamisengine.vectrix.geometry.FrustumPlanes;
import org.dynamisengine.vectrix.soa.AABBSoA;
import org.dynamisengine.vectrix.soa.SkinningKernels;
import org.dynamisengine.vectrix.soa.TransformSoA;
import org.junit.jupiter.api.Test;

/**
 * Review finding M-15. In Vector API builds the SIMD kernels must (a) give bitwise the same results as the scalar
 * paths and (b) not be loaded at all unless used: static Vector API fields and method bodies inside the kernel
 * classes made even their scalar paths throw {@link NoClassDefFoundError} on a JVM without
 * {@code --add-modules jdk.incubator.vector}. In the default (scalar-only) build both checks hold trivially; the
 * experimental profile exercises them for real.
 */
class SimdPathTest {

    private static TransformSoA randomTransforms(SplittableRandom rnd, int n) {
        TransformSoA t = new TransformSoA(n);
        for (int i = 0; i < n; i++) {
            Quaternionf q = new Quaternionf().rotationXYZ((float) rnd.nextDouble(-3, 3), (float) rnd.nextDouble(-3, 3),
                    (float) rnd.nextDouble(-3, 3));
            t.qx[i] = q.x;
            t.qy[i] = q.y;
            t.qz[i] = q.z;
            t.qw[i] = q.w;
            t.tx[i] = (float) rnd.nextDouble(-10, 10);
            t.ty[i] = (float) rnd.nextDouble(-10, 10);
            t.tz[i] = (float) rnd.nextDouble(-10, 10);
            t.sx[i] = (float) rnd.nextDouble(0.5, 2);
            t.sy[i] = (float) rnd.nextDouble(0.5, 2);
            t.sz[i] = (float) rnd.nextDouble(0.5, 2);
        }
        return t;
    }

    private static void assertSame(TransformSoA a, TransformSoA b) {
        assertArrayEquals(a.tx, b.tx);
        assertArrayEquals(a.ty, b.ty);
        assertArrayEquals(a.tz, b.tz);
        assertArrayEquals(a.qx, b.qx);
        assertArrayEquals(a.qy, b.qy);
        assertArrayEquals(a.qz, b.qz);
        assertArrayEquals(a.qw, b.qw);
        assertArrayEquals(a.sx, b.sx);
        assertArrayEquals(a.sy, b.sy);
        assertArrayEquals(a.sz, b.sz);
    }

    private static TransformSoA copy(TransformSoA t) {
        TransformSoA c = new TransformSoA(t.size());
        System.arraycopy(t.tx, 0, c.tx, 0, t.size());
        System.arraycopy(t.ty, 0, c.ty, 0, t.size());
        System.arraycopy(t.tz, 0, c.tz, 0, t.size());
        System.arraycopy(t.qx, 0, c.qx, 0, t.size());
        System.arraycopy(t.qy, 0, c.qy, 0, t.size());
        System.arraycopy(t.qz, 0, c.qz, 0, t.size());
        System.arraycopy(t.qw, 0, c.qw, 0, t.size());
        System.arraycopy(t.sx, 0, c.sx, 0, t.size());
        System.arraycopy(t.sy, 0, c.sy, 0, t.size());
        System.arraycopy(t.sz, 0, c.sz, 0, t.size());
        return c;
    }

    @Test
    void simdComposeIsBitwiseScalarCompose() {
        int n = 1003; // not a multiple of any lane count, so the scalar tail runs too
        SplittableRandom rnd = new SplittableRandom(21);
        TransformSoA p = randomTransforms(rnd, n);
        TransformSoA l = randomTransforms(rnd, n);
        TransformSoA simd = new TransformSoA(n);
        TransformSoA scalar = new TransformSoA(n);
        MathMode prevMode = CoreConfig.mathMode();
        boolean prevSimd = SimdSupport.isEnabled();
        try {
            CoreConfig.setMathMode(MathMode.FAST);
            SimdSupport.setEnabled(false);
            TransformKernels.composeBatch(p, l, scalar, n);
            SimdSupport.setEnabled(true);
            TransformKernels.composeBatch(p, l, simd, n);
            assertSame(scalar, simd);

            TransformSoA inPlace = copy(p); // out aliases parents
            TransformKernels.composeBatch(inPlace, l, inPlace, n);
            assertSame(scalar, inPlace);
        } finally {
            CoreConfig.setMathMode(prevMode);
            SimdSupport.setEnabled(prevSimd);
        }
    }

    /**
     * SIMD culling is the default dispatch, so it must classify exactly like scalar for every input, including NaN,
     * infinite, signed-zero and inverted bounds and corrupted planes (a NaN mismatch was fixed in review batch 4b).
     * Under the {@code test-simd} run this exercises the Vector API kernel at the preferred vector width.
     */
    @Test
    void simdCullingMatchesScalarForSpecialValues() {
        float[] special = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -0f, 1e30f, -1e30f,
            Float.MIN_VALUE};
        SplittableRandom rnd = new SplittableRandom(7);
        Matrix4f viewProj = new Matrix4f().perspective(1.1f, 1.3f, 0.5f, 80f).lookAt(3, 4, 12, 0, 0, 0, 0, 1, 0);
        for (int trial = 0; trial < 300; trial++) {
            FrustumPlanes fp = new FrustumPlanes().set(viewProj, trial % 2 == 0);
            if (trial % 5 == 0) {
                int p = rnd.nextInt(FrustumPlanes.COUNT);
                float v = special[rnd.nextInt(special.length)];
                float[][] comps = {fp.x, fp.y, fp.z, fp.w};
                comps[rnd.nextInt(4)][p] = v;
            }
            int n = 64 + rnd.nextInt(300);
            AABBSoA boxes = new AABBSoA(n);
            for (int i = 0; i < n; i++) {
                float cx = (float) rnd.nextDouble(-60, 60), cy = (float) rnd.nextDouble(-60, 60);
                float cz = (float) rnd.nextDouble(-90, 30), e = (float) rnd.nextDouble(0, 8);
                float[] v = {cx - e, cy - e, cz - e, cx + e, cy + e, cz + e};
                if (rnd.nextInt(4) == 0) {
                    v[rnd.nextInt(6)] = special[rnd.nextInt(special.length)];
                }
                if (rnd.nextInt(10) == 0) {
                    float t = v[0];
                    v[0] = v[3];
                    v[3] = t;
                }
                boxes.set(i, v[0], v[1], v[2], v[3], v[4], v[5]);
            }
            int[] scalar = new int[n];
            int[] simd = new int[n];
            int[] dispatched = new int[n];
            CullingKernels.frustumCullAabbBatchScalar(fp, boxes, scalar, n);
            CullingKernels.frustumCullAabbBatchSimd(fp, boxes, simd, n);
            CullingKernels.frustumCullAabbBatch(fp, boxes, dispatched, n);
            assertArrayEquals(scalar, simd, "SIMD vs scalar, trial " + trial);
            assertArrayEquals(scalar, dispatched, "default dispatch vs scalar, trial " + trial);
        }
    }

    @Test
    void simdCullingMatchesScalarCulling() {
        FrustumPlanes fp = new FrustumPlanes().set(new Matrix4f().perspective(1.2f, 1.5f, 0.1f, 100f).lookAt(0, 0, 5, 0, 0, 0, 0, 1, 0), false);
        int n = 300;
        SplittableRandom rnd = new SplittableRandom(29);
        AABBSoA boxes = new AABBSoA(n);
        for (int i = 0; i < n; i++) {
            float cx = (float) rnd.nextDouble(-20, 20), cy = (float) rnd.nextDouble(-20, 20), cz = (float) rnd.nextDouble(-60, 10);
            float e = (float) rnd.nextDouble(0.1, 3);
            boxes.set(i, cx - e, cy - e, cz - e, cx + e, cy + e, cz + e);
        }
        int[] a = new int[n], b = new int[n];
        CullingKernels.frustumCullAabbBatchScalar(fp, boxes, a, n);
        CullingKernels.frustumCullAabbBatchSimd(fp, boxes, b, n);
        assertArrayEquals(a, b);
    }

    /** Runs in a child JVM started without {@code --add-modules jdk.incubator.vector}. */
    public static final class NoVectorModule {
        private NoVectorModule() {
        }

        public static void main(String[] args) {
            TransformSoA joints = new TransformSoA(1);
            joints.qw[0] = 1;
            joints.sx[0] = 1;
            joints.sy[0] = 1;
            joints.sz[0] = 1;
            float[] ox = new float[64], oy = new float[64], oz = new float[64];
            int[] idx = new int[64 * 4];
            float[] wts = new float[64 * 4];
            for (int i = 0; i < 64; i++) {
                wts[i * 4] = 1;
            }
            float[] in = new float[64];
            SkinningKernels.skinLbs4(joints, idx, wts, in, in, in, ox, oy, oz, 64);
            SkinningKernels.skinLbs4Vector(joints, idx, wts, in, in, in, ox, oy, oz, 64);
            int[] zi = new int[64];
            float[] w1 = new float[64];
            SkinningKernels.skinLbs4SoA(joints, zi, zi, zi, zi, w1, w1, w1, w1, in, in, in, ox, oy, oz, 64);
            CullingKernels.frustumCullAabbBatch(new FrustumPlanes(), new AABBSoA(64), new int[64], 64);
            TransformSoA t = new TransformSoA(64);
            TransformKernels.composeBatch(t, t, new TransformSoA(64), 64);
            System.out.println("scalar paths ok");
        }
    }

    @Test
    void scalarPathsWorkWithoutTheVectorModule() throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String library = new File(TransformKernels.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        Process p = new ProcessBuilder(java, "-cp", library + File.pathSeparator + System.getProperty("java.class.path"),
                NoVectorModule.class.getName()).redirectErrorStream(true).start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "child JVM timed out");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), out);
        assertTrue(out.contains("scalar paths ok"), out);
    }
}
