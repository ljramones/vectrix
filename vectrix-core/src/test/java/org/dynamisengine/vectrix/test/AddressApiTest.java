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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.dynamisengine.vectrix.core.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * The raw-address API ({@code getToAddress}, {@code getTransposedToAddress}, {@code setFromAddress},
 * {@code setTransposedFromAddress}) must write the same bytes as the matching {@code ByteBuffer} methods and read
 * back what it wrote, on the FFM path (the default) and the Unsafe fallback, and must degrade to
 * {@link UnsupportedOperationException} when neither native access nor Unsafe is available.
 */
class AddressApiTest {

    private static final String[] TYPES = {
        "Vector2f", "Vector2d", "Vector2i", "Vector2L", "Vector3f", "Vector3d", "Vector3i", "Vector3L",
        "Vector4f", "Vector4d", "Vector4i", "Vector4L", "Matrix2f", "Matrix2d", "Matrix3f", "Matrix3d",
        "Matrix3x2f", "Matrix3x2d", "Matrix4f", "Matrix4d", "Matrix4x3f", "Matrix4x3d",
    };
    private static final int BYTES = 256;

    /** Runs every address method of every type; returns the number of methods checked. */
    static int checkAll() throws ReflectiveOperationException {
        int checked = 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(BYTES, 16);
            for (String name : TYPES) {
                Class<?> type = Class.forName("org.dynamisengine.vectrix.core." + name);
                for (String[] pair : new String[][] {{"getToAddress", "get", "setFromAddress"},
                        {"getTransposedToAddress", "getTransposed", "setTransposedFromAddress"}}) {
                    Method toAddress = method(type, pair[0], long.class);
                    if (toAddress == null) {
                        continue;
                    }
                    Object a = filled(type);
                    seg.fill((byte) 0);
                    toAddress.invoke(a, seg.address());

                    Method toBuffer = method(type, pair[1], ByteBuffer.class);
                    if (toBuffer != null) {
                        ByteBuffer bb = ByteBuffer.allocateDirect(BYTES).order(ByteOrder.nativeOrder());
                        toBuffer.invoke(a, bb);
                        for (int i = 0; i < BYTES; i++) {
                            if (bb.get(i) != seg.get(ValueLayout.JAVA_BYTE, i)) {
                                throw new AssertionError(name + "." + pair[0] + " differs from " + pair[1]
                                        + "(ByteBuffer) at byte " + i);
                            }
                        }
                    }
                    checked++;

                    Method fromAddress = method(type, pair[2], long.class);
                    if (fromAddress != null) {
                        Object b = type.getConstructor().newInstance();
                        fromAddress.invoke(b, seg.address());
                        if (!a.equals(b)) {
                            throw new AssertionError(name + "." + pair[2] + " did not read back " + pair[0]
                                    + ": " + a + " vs " + b);
                        }
                        checked++;
                    }
                }
            }
        }
        return checked;
    }

    private static Method method(Class<?> type, String name, Class<?> param) {
        try {
            return type.getMethod(name, param);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /** An instance whose numeric fields all hold distinct values, with its property flags cleared. */
    private static Object filled(Class<?> type) throws ReflectiveOperationException {
        Object o = type.getConstructor().newInstance();
        List<Field> fields = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType().isPrimitive() && !f.getName().startsWith("__")) {
                fields.add(f);
            }
        }
        fields.sort((x, y) -> x.getName().compareTo(y.getName()));
        int i = 1;
        for (Field f : fields) {
            f.setAccessible(true);
            Class<?> t = f.getType();
            if (f.getName().equals("properties")) {
                f.setInt(o, 0);
            } else if (t == float.class) {
                f.setFloat(o, i++ + 0.25f);
            } else if (t == double.class) {
                f.setDouble(o, i++ + 0.125);
            } else if (t == int.class) {
                f.setInt(o, i++ * 1000 + 7);
            } else if (t == long.class) {
                f.setLong(o, (i++ * 1000L + 7) << 33);
            }
        }
        return o;
    }

    @Test
    void addressApiMatchesTheBufferApi() throws ReflectiveOperationException {
        // 22 types; matrices also have the transposed forms
        assertTrue(checkAll() >= 60);
    }

    /** Runs {@link #checkAll()} in a child JVM. */
    public static final class Child {
        private Child() {
        }

        public static void main(String[] args) throws Exception {
            try {
                System.out.println("checked=" + checkAll());
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof UnsupportedOperationException)) {
                    throw e;
                }
                System.out.println("address=unsupported");
            }
        }
    }

    private static String child(String... jvmArgs) throws Exception {
        String javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String library = new File(Matrix4f.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        String tests = new File(AddressApiTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getPath();
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.addAll(List.of(jvmArgs));
        cmd.addAll(List.of("-cp", library + File.pathSeparator + tests, Child.class.getName()));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "child JVM timed out");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), out);
        return out;
    }

    @Test
    void defaultUsesFfmAndNeverTouchesUnsafe() throws Exception {
        String out = child();
        assertTrue(out.contains("checked="), out);
        assertTrue(out.contains("restricted method"), "expected the JDK's native-access warning:\n" + out);
        assertFalse(out.contains("sun.misc.Unsafe"), "the default address path touched Unsafe:\n" + out);
    }

    @Test
    void enablingNativeAccessSilencesEveryWarning() throws Exception {
        String out = child("--enable-native-access=ALL-UNNAMED");
        assertTrue(out.contains("checked="), out);
        assertFalse(out.contains("WARNING"), out);
    }

    @Test
    void forceUnsafeUsesTheUnsafePath() throws Exception {
        String out = child("-Djoml.forceUnsafe=true");
        assertTrue(out.contains("checked="), out);
        assertTrue(out.contains("sun.misc.Unsafe"), "expected the Unsafe deprecation warning:\n" + out);
        assertFalse(out.contains("restricted method"), out);
    }

    @Test
    void deniedNativeAccessFallsBackToUnsafe() throws Exception {
        String out = child("--illegal-native-access=deny");
        assertTrue(out.contains("checked="), out);
        assertTrue(out.contains("sun.misc.Unsafe"), out);
    }

    @Test
    void neitherNativeAccessNorUnsafeIsUnsupported() throws Exception {
        String out = child("--illegal-native-access=deny", "--sun-misc-unsafe-memory-access=deny");
        assertTrue(out.contains("address=unsupported"), out);
    }
}
