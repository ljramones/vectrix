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

import java.net.URLClassLoader;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/**
 * Review finding M-13: {@code Affine4f} called {@code java.lang.Math.fma} directly, so {@code -Djoml.useMathFma=false}
 * (one of the flags that determine output bits) had no effect on it, unlike every other type. The flag is read at
 * class initialisation, so each setting runs in a fresh class loader.
 */
class Affine4fFmaPolicyTest {

    private static final String[] M = {"m00", "m01", "m02", "m10", "m11", "m12", "m20", "m21", "m22", "m30", "m31", "m32"};

    /** Returns {entries..., px, py, pz} for transformPoint of (x, y, z) under the given joml.useMathFma value. */
    private static float[] run(String useMathFma, float[] trs, float x, float y, float z) throws Exception {
        String saved = System.setProperty("joml.useMathFma", useMathFma);
        try (URLClassLoader cl = IsolatedClasses.loader()) {
            Class<?> affine = cl.loadClass("org.dynamisengine.vectrix.affine.Affine4f");
            Class<?> vec = cl.loadClass("org.dynamisengine.vectrix.core.Vector3f");
            Class<?> m43 = cl.loadClass("org.dynamisengine.vectrix.core.Matrix4x3f");
            Object a = affine.getConstructor().newInstance();
            Class<?>[] tenFloats = new Class<?>[10];
            java.util.Arrays.fill(tenFloats, float.class);
            Object[] args = new Object[10];
            for (int i = 0; i < 10; i++) {
                args[i] = trs[i];
            }
            affine.getMethod("translationRotateScale", tenFloats).invoke(a, args);
            Object m = affine.getMethod("toMatrix4x3f", m43).invoke(a, m43.getConstructor().newInstance());
            Object p = affine.getMethod("transformPoint", float.class, float.class, float.class, vec)
                    .invoke(a, x, y, z, vec.getConstructor().newInstance());
            float[] out = new float[15];
            for (int i = 0; i < 12; i++) {
                out[i] = (Float) m43.getMethod(M[i]).invoke(m);
            }
            out[12] = vec.getField("x").getFloat(p);
            out[13] = vec.getField("y").getFloat(p);
            out[14] = vec.getField("z").getFloat(p);
            return out;
        } finally {
            if (saved == null) {
                System.clearProperty("joml.useMathFma");
            } else {
                System.setProperty("joml.useMathFma", saved);
            }
        }
    }

    @Test
    void transformPointHonoursTheFmaFlag() throws Exception {
        SplittableRandom rnd = new SplittableRandom(13);
        int fusedDiffers = 0;
        for (int t = 0; t < 40; t++) {
            float[] trs = new float[10];
            for (int i = 0; i < 3; i++) {
                trs[i] = (float) rnd.nextDouble(-100, 100);
            }
            double qx = rnd.nextGaussian(), qy = rnd.nextGaussian(), qz = rnd.nextGaussian(), qw = rnd.nextGaussian();
            double n = java.lang.Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
            trs[3] = (float) (qx / n);
            trs[4] = (float) (qy / n);
            trs[5] = (float) (qz / n);
            trs[6] = (float) (qw / n);
            for (int i = 7; i < 10; i++) {
                trs[i] = (float) rnd.nextDouble(0.5, 3);
            }
            float x = (float) rnd.nextDouble(-10, 10), y = (float) rnd.nextDouble(-10, 10), z = (float) rnd.nextDouble(-10, 10);

            float[] plain = run("false", trs, x, y, z);
            // With FMA disabled the nested fma(a, b, c) calls must reduce to a * b + c.
            float ex = plain[0] * x + (plain[3] * y + (plain[6] * z + plain[9]));
            float ey = plain[1] * x + (plain[4] * y + (plain[7] * z + plain[10]));
            float ez = plain[2] * x + (plain[5] * y + (plain[8] * z + plain[11]));
            assertEquals(ex, plain[12], 0.0f, "x, trial " + t);
            assertEquals(ey, plain[13], 0.0f, "y, trial " + t);
            assertEquals(ez, plain[14], 0.0f, "z, trial " + t);

            // With FMA enabled the result is fused, so it differs from a * b + c in the low bits of some trials.
            // (Exact equality with java.lang.Math.fma is not asserted: the JDK's float fma can differ by an ulp
            // between its interpreted and intrinsic forms.)
            float[] fused = run("true", trs, x, y, z);
            if (Float.floatToRawIntBits(fused[12]) != Float.floatToRawIntBits(plain[12])) {
                fusedDiffers++;
            }
        }
        assertTrue(fusedDiffers > 0, "the two settings should differ in at least some low bits");
    }
}
