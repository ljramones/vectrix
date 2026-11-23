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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.SimplexNoise;
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.curve.scalar.UniformBSpline1d;
import org.dynamisengine.vectrix.curve.scalar.UniformBSpline1f;
import org.dynamisengine.vectrix.curve.vec2.UniformBSpline2d;
import org.dynamisengine.vectrix.curve.vec2.UniformBSpline2f;
import org.dynamisengine.vectrix.curve.vec4.UniformBSpline4d;
import org.dynamisengine.vectrix.curve.vec4.UniformBSpline4f;
import org.dynamisengine.vectrix.geometry.FrustumRayBuilder;
import org.dynamisengine.vectrix.gpu.GpuTransformWriteKernels;
import org.dynamisengine.vectrix.gpu.InstanceSubmissionPipeline;
import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.sampling.Convolution;
import org.dynamisengine.vectrix.sampling.SpiralSampling;
import org.dynamisengine.vectrix.sampling.StratifiedSampling;
import org.dynamisengine.vectrix.soa.TransformSoA;
import org.junit.jupiter.api.Test;

/**
 * Review finding M-17: behavioural tests for classes that had no coverage at all (matrix stacks, noise, frustum rays,
 * the instance pipeline, the double quaternion interpolator, sampling helpers, the B-spline variants).
 */
class CoverageGapsTest {

    private static final String[] STACKS = {"Matrix3fStack", "Matrix3dStack", "Matrix3x2fStack", "Matrix3x2dStack",
        "Matrix4fStack", "Matrix4dStack", "Matrix4x3fStack", "Matrix4x3dStack"};

    @Test
    void matrixStacksPushPopAndBoundsCheck() throws Exception {
        for (String name : STACKS) {
            Class<?> c = Class.forName("org.dynamisengine.vectrix.core." + name);
            Object s = c.getConstructor(int.class).newInstance(2);
            Class<?> prim = name.contains("f") ? float.class : double.class;
            Method scale = c.getMethod("scale", prim);
            Object two = prim == float.class ? (Object) 2.0f : (Object) 2.0;
            Object three = prim == float.class ? (Object) 3.0f : (Object) 3.0;
            Object identity = c.getSuperclass().getConstructor().newInstance();
            scale.invoke(s, two);
            Object scaled = c.getSuperclass().getConstructor(Class.forName(c.getSuperclass().getName() + "c")).newInstance(s);
            c.getMethod("pushMatrix").invoke(s);
            scale.invoke(s, three);
            assertNotEquals(scaled, copyAsBase(c, s), name + " modified after push");
            c.getMethod("popMatrix").invoke(s);
            assertEquals(scaled, copyAsBase(c, s), name + " pop restores the pushed matrix");
            Throwable underflow = assertThrows(InvocationTargetException.class, () -> c.getMethod("popMatrix").invoke(s)).getCause();
            assertTrue(underflow instanceof IllegalStateException, name + ": " + underflow);
            c.getMethod("pushMatrix").invoke(s);
            Throwable overflow = assertThrows(InvocationTargetException.class, () -> c.getMethod("pushMatrix").invoke(s)).getCause();
            assertTrue(overflow instanceof IllegalStateException, name + ": " + overflow);
            c.getMethod("clear").invoke(s);
            assertEquals(identity, copyAsBase(c, s), name + " clear resets to identity");
        }
    }

    private static Object copyAsBase(Class<?> stack, Object s) throws ReflectiveOperationException {
        Class<?> base = stack.getSuperclass();
        return base.getConstructor(Class.forName(base.getName() + "c")).newInstance(s);
    }

    @Test
    void simplexNoiseIsBoundedSmoothAndDeterministic() {
        float max = 0;
        for (int i = 0; i < 2000; i++) {
            float x = i * 0.137f, y = i * 0.071f, z = i * 0.029f, w = i * 0.013f;
            float n2 = SimplexNoise.noise(x, y), n3 = SimplexNoise.noise(x, y, z), n4 = SimplexNoise.noise(x, y, z, w);
            for (float n : new float[] {n2, n3, n4}) {
                assertTrue(n >= -1.0001f && n <= 1.0001f, "noise out of range: " + n);
                max = java.lang.Math.max(max, java.lang.Math.abs(n));
            }
            assertEquals(n3, SimplexNoise.noise(x, y, z), 0f);
            assertEquals(n3, SimplexNoise.noise(x + 1e-4f, y, z), 0.01f, "continuity");
        }
        assertTrue(max > 0.3f, "noise is not flat");
    }

    @Test
    void frustumRaysPassThroughTheFrustumCorners() {
        Matrix4f viewProj = new Matrix4f().perspective(1.2f, 1.5f, 0.1f, 50f).lookAt(1, 2, 5, 0, 0, 0, 0, 1, 0);
        FrustumRayBuilder rays = new FrustumRayBuilder(viewProj);
        Vector3f origin = (Vector3f) rays.origin(new Vector3f());
        assertTrue(origin.distance(1, 2, 5) < 1e-4f, "origin " + origin);
        Matrix4f inv = new Matrix4f(viewProj).invert();
        for (float[] uv : new float[][] {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {0.5f, 0.5f}}) {
            Vector3f dir = new Vector3f(rays.dir(uv[0], uv[1], new Vector3f())).normalize();
            Vector3f far = inv.transformProject(new Vector3f(uv[0] * 2 - 1, uv[1] * 2 - 1, 1));
            Vector3f expected = far.sub(origin).normalize();
            assertTrue(dir.distance(expected) < 1e-4f, "uv " + uv[0] + "," + uv[1] + ": " + dir + " vs " + expected);
        }
    }

    @Test
    void instancePipelinePathsAgree() {
        int n = 5;
        Transformf[] t = new Transformf[n];
        int[] order = {4, 0, 3, 1, 2};
        float[][] bounds = new float[6][n];
        for (int i = 0; i < n; i++) {
            t[i] = new Transformf(new Vector3f(i, -i, 2 * i), new Quaternionf().rotationXYZ(0.1f * i, 0.2f, -0.3f * i),
                    new Vector3f(1 + 0.1f * i, 1, 2));
            bounds[0][i] = -1; bounds[1][i] = -2; bounds[2][i] = -0.5f;
            bounds[3][i] = 1; bounds[4][i] = 0.5f; bounds[5][i] = 3;
        }
        TransformSoA joints = new TransformSoA(1);
        joints.qw[0] = 1;
        float[] vin = {1, 2, 3, 4}, vout = new float[4];
        float[][] boxPacked = new float[6][n], boxMatrix = new float[6][n];
        float[] packedUpload = new float[12 * n], matrixUpload = new float[16 * n];
        InstanceSubmissionPipeline pipe = new InstanceSubmissionPipeline(2); // grows
        pipe.processFrame(t, order, bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5],
                boxPacked[0], boxPacked[1], boxPacked[2], boxPacked[3], boxPacked[4], boxPacked[5],
                joints, new int[16], new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0}, vin, vin, vin, vout, vout, vout,
                packedUpload, matrixUpload, n, 4, InstanceSubmissionPipeline.Path.PACKED_AFFINE);
        pipe.processFrame(t, order, bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5],
                boxMatrix[0], boxMatrix[1], boxMatrix[2], boxMatrix[3], boxMatrix[4], boxMatrix[5],
                joints, new int[16], new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0}, vin, vin, vin, vout, vout, vout,
                packedUpload, matrixUpload, n, 4, InstanceSubmissionPipeline.Path.MATRIX_FALLBACK);
        for (int k = 0; k < 6; k++) {
            assertArrayEquals(boxMatrix[k], boxPacked[k], 1e-4f, "bounds " + k);
        }
        // the packed upload, expanded by the GPU writer, is exactly the matrix upload
        float[] expanded = new float[16 * n];
        int[] identityOrder = {0, 1, 2, 3, 4};
        GpuTransformWriteKernels.writeMatrix4fFromPackedAffine(new PackedAffineArray(packedUpload), identityOrder, expanded, 0, n);
        assertArrayEquals(matrixUpload, expanded, 1e-5f);
        assertArrayEquals(vin, vout, 0f, "identity joint leaves vertices in place");
    }

    @Test
    void samplingHelpersProduceWellFormedOutput() {
        float[] kernel = new float[5 * 5];
        Convolution.gaussianKernel(5, 5, 1.0f, kernel);
        float sum = 0;
        for (float k : kernel) {
            sum += k;
        }
        assertEquals(1.0f, sum, 1e-5f);
        assertEquals(kernel[0], kernel[24], 1e-7f); // symmetric
        assertTrue(kernel[12] > kernel[0], "peak at the centre");

        List<float[]> spiral = new ArrayList<>();
        new SpiralSampling(7).createEquiAngle(2.0f, 3, 100, (x, y) -> spiral.add(new float[] {x, y}));
        assertEquals(100, spiral.size());
        for (float[] p : spiral) {
            assertTrue(p[0] * p[0] + p[1] * p[1] <= 4.0f + 1e-4f, "inside the radius");
        }

        List<float[]> strata = new ArrayList<>();
        new StratifiedSampling(3).generateRandom(4, (x, y) -> strata.add(new float[] {x, y}));
        assertEquals(16, strata.size());
        boolean[] cell = new boolean[16];
        for (float[] p : strata) {
            assertTrue(p[0] >= -1 && p[0] <= 1 && p[1] >= -1 && p[1] <= 1, "in [-1,1]^2");
            int cx = java.lang.Math.min(3, (int) ((p[0] + 1) * 2)), cy = java.lang.Math.min(3, (int) ((p[1] + 1) * 2));
            cell[cy * 4 + cx] = true;
        }
        for (boolean c : cell) {
            assertTrue(c, "one sample per stratum");
        }
    }

    @Test
    void bsplineVariantsAgreeAcrossDimensionsAndPrecisions() {
        float[] xs = {0, 1, 3, 4, 6};
        float[] ys = {1, -1, 2, 0, 5};
        UniformBSpline1f sx = new UniformBSpline1f(xs);
        UniformBSpline1d sxd = new UniformBSpline1d(new double[] {0, 1, 3, 4, 6});
        UniformBSpline2f s2 = new UniformBSpline2f(new Vector2f[] {new Vector2f(0, 1), new Vector2f(1, -1), new Vector2f(3, 2),
            new Vector2f(4, 0), new Vector2f(6, 5)});
        UniformBSpline2d s2d = new UniformBSpline2d(new Vector2d[] {new Vector2d(0, 1), new Vector2d(1, -1), new Vector2d(3, 2),
            new Vector2d(4, 0), new Vector2d(6, 5)});
        Vector4f[] c4 = new Vector4f[5];
        Vector4d[] c4d = new Vector4d[5];
        for (int i = 0; i < 5; i++) {
            c4[i] = new Vector4f(xs[i], ys[i], -xs[i], 2 * ys[i]);
            c4d[i] = new Vector4d(xs[i], ys[i], -xs[i], 2 * ys[i]);
        }
        UniformBSpline4f s4 = new UniformBSpline4f(c4);
        UniformBSpline4d s4d = new UniformBSpline4d(c4d);
        for (int seg = 0; seg < 2; seg++) {
            for (float t = 0; t <= 1; t += 0.125f) {
                float x = sx.evaluate(seg, t);
                Vector2f p2 = s2.evaluate(seg, t, new Vector2f());
                Vector4f p4 = s4.evaluate(seg, t, new Vector4f());
                assertEquals(x, p2.x, 1e-6f);
                assertEquals(x, p4.x, 1e-6f);
                assertEquals(-x, p4.z, 1e-6f);
                assertEquals(2 * p2.y, p4.w, 1e-5f);
                assertEquals(x, sxd.evaluate(seg, t), 1e-5);
                assertEquals(p2.y, s2d.evaluate(seg, t, new Vector2d()).y, 1e-5);
                assertEquals(p4.w, s4d.evaluate(seg, t, new Vector4d()).w, 1e-5);
            }
        }
    }
}
