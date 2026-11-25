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

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.dynamisengine.vectrix.core.CoreConfig;
import org.dynamisengine.vectrix.core.MathMode;
import org.junit.jupiter.api.Test;

/**
 * Review finding M-12: there was no way to get platform-independent transcendental results. {@code java.lang.Math}
 * may use per-architecture intrinsics (about 6.9 % of double {@code sin}/{@code cos} inputs differ from
 * {@code StrictMath} on arm64), and 56 calls outside {@code core.Math} bypassed every library switch.
 */
class DeterminismTest {

    private static final String[] UNARY = {"sin", "cos", "tan", "asin", "acos", "atan", "exp"};

    private static double strictUnary(String fn, double x) {
        switch (fn) {
            case "sin": return StrictMath.sin(x);
            case "cos": return StrictMath.cos(x);
            case "tan": return StrictMath.tan(x);
            case "asin": return StrictMath.asin(x);
            case "acos": return StrictMath.acos(x);
            case "atan": return StrictMath.atan(x);
            default: return StrictMath.exp(x);
        }
    }

    @Test
    void strictMathPropertyMakesTranscendentalsBitwiseStrictMath() throws Exception {
        // joml.fastmath is set too: vectrix.strictMath must take precedence over the fast approximations.
        String savedStrict = System.setProperty("vectrix.strictMath", "true");
        String savedFast = System.setProperty("joml.fastmath", "true");
        try (URLClassLoader cl = IsolatedClasses.loader()) {
            Class<?> math = cl.loadClass("org.dynamisengine.vectrix.core.Math");
            Class<?> accurate = cl.loadClass("org.dynamisengine.vectrix.core.AccurateMath");
            SplittableRandom rnd = new SplittableRandom(17);
            for (String fn : UNARY) {
                Method m = math.getMethod(fn, double.class);
                Method a = accurate.getMethod(fn, double.class);
                for (int i = 0; i < 2000; i++) {
                    double x = fn.startsWith("a") && !fn.equals("atan") ? rnd.nextDouble(-1, 1) : rnd.nextDouble(-50, 50);
                    double expected = strictUnary(fn, x);
                    assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits((Double) m.invoke(null, x)),
                            "core.Math." + fn + "(" + x + ")");
                    assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits((Double) a.invoke(null, x)),
                            "AccurateMath." + fn + "(" + x + ")");
                }
            }
            Method atan2 = math.getMethod("atan2", double.class, double.class);
            for (int i = 0; i < 2000; i++) {
                double y = rnd.nextDouble(-5, 5), x = rnd.nextDouble(-5, 5);
                assertEquals(Double.doubleToRawLongBits(StrictMath.atan2(y, x)),
                        Double.doubleToRawLongBits((Double) atan2.invoke(null, y, x)), "atan2");
            }
        } finally {
            restore("vectrix.strictMath", savedStrict);
            restore("joml.fastmath", savedFast);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    void noTranscendentalBypassesAccurateMath() throws IOException {
        assertTrue(Files.isDirectory(Sources.mainRoots().get(0)), "run from vectrix-tests");
        Pattern direct = Pattern.compile("(?:java\\.lang\\.)?(?<![\\w.])(?:java\\.lang\\.Math|StrictMath)\\."
                + "(sin|cos|tan|asin|acos|atan|atan2|exp|log|log10|pow|sinh|cosh|tanh|cbrt|hypot|expm1|log1p)\\(");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Sources.walkMain()) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                if (p.getFileName().toString().equals("AccurateMath.java")) {
                    continue;
                }
                String src = Files.readString(p);
                Matcher m = direct.matcher(src);
                while (m.find()) {
                    offenders.add(Sources.ROOT.relativize(p) + ": " + m.group());
                }
            }
        }
        assertTrue(offenders.isEmpty(), "call AccurateMath (or core.Math) instead: " + offenders);
    }

    @Test
    void fingerprintNamesEveryBitAffectingSetting() {
        MathMode prev = CoreConfig.mathMode();
        try {
            CoreConfig.setMathMode(MathMode.FAST);
            String fast = CoreConfig.determinismFingerprint();
            CoreConfig.setMathMode(MathMode.STRICT);
            String strict = CoreConfig.determinismFingerprint();
            for (String key : new String[] {"vectrix.math.mode=", "vectrix.strictMath=", "vectrix.fastmath=",
                    "vectrix.sinLookup=", "vectrix.useMathFma=", "os.arch=", "java.vm.version="}) {
                assertTrue(fast.contains(key), key + " missing from " + fast);
            }
            assertTrue(fast.contains("vectrix.math.mode=FAST") && strict.contains("vectrix.math.mode=STRICT"));
        } finally {
            CoreConfig.setMathMode(prev);
        }
    }
}
