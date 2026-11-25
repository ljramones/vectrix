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

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.dynamisengine.vectrix.core.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * Fast-math mode (-Djoml.fastmath, opt-in) after the second audit (2.5.0): the approximations stay within their
 * documented bounds, and large arguments, zeros, infinities and NaN go to the accurate path.
 */
class FastMathAudit2Test {
    private static Method fn(String name, Class<?>... types) throws Exception {
        Method m = Class.forName("org.dynamisengine.vectrix.core.Math").getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m;
    }

    @Test
    void polynomialSinCosAndAtan2() throws Exception {
        Method sin = fn("fastSin", double.class), cos = fn("fastCos", double.class), atan2 = fn("fastAtan2Guarded", double.class, double.class);
        SplittableRandom r = new SplittableRandom(1);
        double worstS = 0, worstC = 0, worstA = 0;
        for (int i = 0; i < 200000; i++) {
            double x = r.nextDouble(-1e6, 1e6);
            worstS = Math.max(worstS, Math.abs((double) sin.invoke(null, x) - StrictMath.sin(x)));
            worstC = Math.max(worstC, Math.abs((double) cos.invoke(null, x) - StrictMath.cos(x)));
            double y = r.nextGaussian(), xx = r.nextGaussian();
            worstA = Math.max(worstA, Math.abs((double) atan2.invoke(null, y, xx) - StrictMath.atan2(y, xx)));
        }
        assertTrue(worstS < 1e-10 && worstC < 2e-10, "fast sin within 1e-10, cos within 2e-10: " + worstS + " " + worstC);
        assertTrue(worstA < 2.1e-4, "fast atan2 within 2.1e-4 rad: " + worstA);
        // large arguments: the accurate path (before 2.5.0 the sign flipped beyond 6.7e9 rad, and sin(1e19) was 0)
        for (double x : new double[] {1e10, 7e9, 1e19, 1e300}) {
            assertEquals(StrictMath.sin(x), (double) sin.invoke(null, x), 0.0, "sin(" + x + ")");
            assertEquals(StrictMath.cos(x), (double) cos.invoke(null, x), 0.0, "cos(" + x + ")");
        }
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits((double) sin.invoke(null, -0.0)), "sin(-0) = -0");
        assertTrue(Double.isNaN((double) sin.invoke(null, Double.NaN)) && Double.isNaN((double) sin.invoke(null, Double.POSITIVE_INFINITY)));
        // atan2 edge cases match StrictMath exactly
        double[][] edges = {{0, 0}, {-0.0, -1}, {0, -1}, {-0.0, 0}, {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY}, {1, 0}, {-1, -0.0},
            {Double.NaN, 1}, {1, Double.NEGATIVE_INFINITY}};
        for (double[] e : edges) {
            assertEquals(Double.doubleToRawLongBits(StrictMath.atan2(e[0], e[1])), Double.doubleToRawLongBits((double) atan2.invoke(null, e[0], e[1])),
                    "atan2(" + e[0] + ", " + e[1] + ")");
        }
    }

    /** Runs in a child JVM with the lookup table enabled. */
    public static final class LookupChild {
        public static void main(String[] args) throws Exception {
            Method sin = fn("lookupSin", float.class), cos = fn("lookupCos", float.class);
            double worst = 0;
            for (int i = 0; i <= 400000; i++) {
                float x = (float) ((i / 200000.0 - 1) * 255.9);
                worst = Math.max(worst, Math.abs((float) sin.invoke(null, x) - StrictMath.sin(x)));
                worst = Math.max(worst, Math.abs((float) cos.invoke(null, x) - StrictMath.cos(x)));
            }
            double large = 0;
            for (float x : new float[] {1e4f, 1e6f, 1e10f, 3e9f, 1e30f}) {
                large = Math.max(large, Math.abs((float) sin.invoke(null, x) - StrictMath.sin(x)));
                large = Math.max(large, Math.abs((float) cos.invoke(null, x) - StrictMath.cos(x)));
            }
            System.out.println("worst=" + worst + " large=" + large + " public=" + org.dynamisengine.vectrix.core.Math.sin(1e6f));
        }
    }

    @Test
    void lookupTableStaysInRange() throws Exception {
        String javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String library = new File(Matrix4f.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        String tests = new File(FastMathAudit2Test.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        List<String> cmd = new ArrayList<>(List.of(javaExe, "-Djoml.fastmath=true", "-Djoml.sinLookup=true", "-cp",
                library + File.pathSeparator + tests, LookupChild.class.getName()));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "child JVM timed out");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, p.exitValue(), out);
        String[] kv = out.substring(out.lastIndexOf("worst=")).split(" ");
        double worst = Double.parseDouble(kv[0].substring(6)), large = Double.parseDouble(kv[1].substring(6));
        float pub = Float.parseFloat(kv[2].substring(7));
        assertTrue(worst < 4e-5, "lookup within 4e-5 below 256 rad: " + worst);
        assertTrue(large < 1e-7, "large arguments use the accurate path: " + large);
        assertTrue(Math.abs(pub) <= 1, "sin(1e6) through the public API is a sine again: " + pub);
    }
}
