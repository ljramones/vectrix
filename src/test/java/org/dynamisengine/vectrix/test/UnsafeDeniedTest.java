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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.dynamisengine.vectrix.core.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * Review finding H-9: when the JVM denies {@code sun.misc.Unsafe} memory access (the JEP 471/498 end state), the
 * library must still work. Before the fix the Unsafe backend could be selected and then fail on every access, the
 * FFM backend called into Unsafe and failed to initialise, and {@code getToAddress} threw
 * {@link NoClassDefFoundError} instead of the documented {@link UnsupportedOperationException}. By default the
 * library uses the FFM backend and never touches Unsafe.
 */
class UnsafeDeniedTest {

    /** Runs in a child JVM started with {@code --sun-misc-unsafe-memory-access=deny}. */
    public static final class Child {
        private Child() {
        }

        public static void main(String[] args) throws Exception {
            Class<?> memUtil = Class.forName("org.dynamisengine.vectrix.core.MemUtil");
            java.lang.reflect.Field f = memUtil.getField("INSTANCE");
            f.setAccessible(true);
            System.out.println("backend=" + f.get(null).getClass().getSimpleName());

            Matrix4f m = new Matrix4f().translation(1, 2, 3).rotateXYZ(0.1f, 0.2f, 0.3f);
            FloatBuffer fb = ByteBuffer.allocateDirect(64 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            m.get(16, fb);
            Matrix4f back = new Matrix4f().set(16, fb);
            System.out.println("roundtrip=" + m.equals(back));
            try {
                fb.limit(20);
                m.get(16, fb);
                System.out.println("bounds=unchecked");
            } catch (IndexOutOfBoundsException e) {
                System.out.println("bounds=checked");
            }
            try {
                m.getToAddress(0L);
                System.out.println("address=returned");
            } catch (UnsupportedOperationException e) {
                System.out.println("address=unsupported");
            }
        }
    }

    @Test
    void libraryWorksWhenUnsafeMemoryAccessIsDenied() throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        // Surefire may put the library on the module path, so add its code location to the child classpath.
        String library = new File(Matrix4f.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        String classpath = library + File.pathSeparator + System.getProperty("java.class.path");
        Process p = new ProcessBuilder(java, "--sun-misc-unsafe-memory-access=deny",
                "-cp", classpath, Child.class.getName())
                .redirectErrorStream(true).start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "child JVM timed out");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), out);
        assertTrue(out.contains("backend=MemUtilFFM"), out);
        assertTrue(out.contains("roundtrip=true"), out);
        assertTrue(out.contains("bounds=checked"), out);
        assertTrue(out.contains("address=unsupported"), out);
    }

    @Test
    void defaultBackendIsFfmAndNeverTouchesUnsafe() throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String library = new File(Matrix4f.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        String classpath = library + File.pathSeparator + System.getProperty("java.class.path");
        Process p = new ProcessBuilder(java, "-cp", classpath, DefaultChild.class.getName()).redirectErrorStream(true).start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "child JVM timed out");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), out);
        assertTrue(out.contains("backend=MemUtilFFM"), out);
        // the JDK prints a terminal-deprecation warning as soon as anything uses sun.misc.Unsafe
        assertTrue(!out.contains("sun.misc.Unsafe"), "default configuration touched Unsafe:\n" + out);
    }

    /** Runs in a child JVM with default settings. */
    public static final class DefaultChild {
        private DefaultChild() {
        }

        public static void main(String[] args) throws Exception {
            java.lang.reflect.Field f = Class.forName("org.dynamisengine.vectrix.core.MemUtil").getField("INSTANCE");
            f.setAccessible(true);
            System.out.println("backend=" + f.get(null).getClass().getSimpleName());
            FloatBuffer fb = ByteBuffer.allocateDirect(64 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            new Matrix4f().rotateX(0.3f).get(0, fb);
            System.out.println("m11=" + fb.get(5));
        }
    }
}
