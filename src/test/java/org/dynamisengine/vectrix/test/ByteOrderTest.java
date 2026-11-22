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
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.dynamisengine.vectrix.core.Quaternionf;
import org.junit.jupiter.api.Test;

/**
 * Buffer writes must mean the same values whatever the buffer's byte order and memory backend: big-endian and
 * little-endian heap buffers (NIO path) and native-order direct buffers (raw-memory path) must all read back
 * identically, bit for bit, in their own order. A 64-bit write of a packed float pair only works in one order; one such write made
 * {@code Quaternionf.getAsMatrix4f} put the 1.0 in m32 instead of m33 for big-endian buffers.
 */
class ByteOrderTest {

    private static final int CAPACITY = 256;

    private static List<Class<?>> valueTypes() throws IOException {
        Path root = Paths.get("src/main/java/org/dynamisengine/vectrix/core");
        List<Class<?>> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(root)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String n = p.getFileName().toString();
                if (!n.endsWith(".java")) {
                    continue;
                }
                try {
                    Class<?> c = Class.forName(ValueFixtures.ROOT + "core." + n.substring(0, n.length() - 5));
                    if (!c.isInterface() && ValueFixtures.isValueType(c)) {
                        out.add(c);
                    }
                } catch (ClassNotFoundException | LinkageError e) {
                    // not part of this build
                }
            }
        }
        return out;
    }

    /** The buffer's contents decoded in its own byte order, in the element size the type writes. */
    private static double[] values(ByteBuffer b, char kind) {
        int size = kind == 'd' || kind == 'L' ? 8 : 4;
        double[] v = new double[CAPACITY / size];
        for (int i = 0; i < v.length; i++) {
            int at = i * size;
            v[i] = kind == 'd' ? b.getDouble(at) : kind == 'L' ? b.getLong(at) : kind == 'i' ? b.getInt(at) : b.getFloat(at);
        }
        return v;
    }

    /** 'f', 'd', 'i' or 'L' from the type name (Matrix4dStack -> 'd'). */
    private static char kind(Class<?> c) {
        String n = c.getSimpleName().replace("Stack", "");
        return n.charAt(n.length() - 1);
    }

    /** Compares every buffer writer of every core value type; returns the failures. */
    static List<String> compareAllWriters() throws Exception {
        int checked = 0;
        List<String> failures = new ArrayList<>();
        for (Class<?> c : valueTypes()) {
            for (Method m : c.getMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != ByteBuffer.class) {
                    continue;
                }
                boolean indexed = pt.length == 2 && pt[0] == int.class && pt[1] == ByteBuffer.class;
                if (!indexed && !(pt.length == 1 && pt[0] == ByteBuffer.class)) {
                    continue;
                }
                Object src = ValueFixtures.create(c, 42);
                ByteBuffer big = ByteBuffer.allocate(CAPACITY).order(ByteOrder.BIG_ENDIAN);
                ByteBuffer little = ByteBuffer.allocate(CAPACITY).order(ByteOrder.LITTLE_ENDIAN);
                ByteBuffer direct = ByteBuffer.allocateDirect(CAPACITY).order(ByteOrder.nativeOrder());
                for (ByteBuffer b : new ByteBuffer[] {big, little, direct}) {
                    if (indexed) {
                        m.invoke(src, 0, b);
                    } else {
                        m.invoke(src, b);
                    }
                }
                checked++;
                String n = m.getName();
                char k = n.startsWith("getAsMatrix") || n.contains("Floats") || n.equals("getf") ? 'f' : kind(c);
                double[] vb = values(big, k), vl = values(little, k), vd = values(direct, k);
                if (!Arrays.equals(vb, vl) || !Arrays.equals(vl, vd)) {
                    int i = 0;
                    while (i < vb.length && vb[i] == vl[i] && vl[i] == vd[i]) {
                        i++;
                    }
                    failures.add(c.getSimpleName() + "." + m.getName() + Arrays.toString(pt) + " element " + i
                            + ": big=" + vb[i] + " little=" + vl[i] + " direct=" + vd[i]);
                }
            }
        }
        if (checked < 50) {
            failures.add("too few buffer writers found: " + checked);
        }
        return failures;
    }

    @Test
    void writesMeanTheSameInEveryByteOrder() throws Exception {
        List<String> failures = compareAllWriters();
        assertTrue(failures.isEmpty(), () -> "byte-order dependent writes: " + failures);
    }

    /** Runs {@link #compareAllWriters()} in a child JVM, so the non-default backends are checked too. */
    public static final class Child {
        private Child() {
        }

        public static void main(String[] args) throws Exception {
            List<String> failures = compareAllWriters();
            failures.forEach(f -> System.out.println("FAIL " + f));
            System.out.println("failures=" + failures.size());
        }
    }

    @Test
    void otherBackendsWriteTheSameBytes() throws Exception {
        String javaExe = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java";
        String library = new java.io.File(Quaternionf.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        String classpath = library + java.io.File.pathSeparator + System.getProperty("java.class.path");
        for (String[] flags : new String[][] {{"-Djoml.forceUnsafe=true"}, {"-Djoml.noffm=true", "-Djoml.nounsafe=true"}}) {
            List<String> cmd = new ArrayList<>(Arrays.asList(javaExe));
            cmd.addAll(Arrays.asList(flags));
            cmd.addAll(Arrays.asList("-cp", classpath, Child.class.getName()));
            Process p = new ProcessBuilder(cmd).directory(new java.io.File(System.getProperty("user.dir")))
                    .redirectErrorStream(true).start();
            assertTrue(p.waitFor(120, java.util.concurrent.TimeUnit.SECONDS), "child JVM timed out");
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(out.contains("failures=0"), Arrays.toString(flags) + ":\n" + out);
        }
    }

    @Test
    void quaternionMatrixIsAffineInABigEndianBuffer() {
        ByteBuffer big = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN);
        new Quaternionf().rotationY(0.5f).getAsMatrix4f(big);
        assertEquals(0.0f, big.getFloat(56), "m32");
        assertEquals(1.0f, big.getFloat(60), "m33");
    }

    @Test
    void wordPairsPutTheFirstValueAtTheLowerAddress() throws Exception {
        Class<?> memUtil = Class.forName(ValueFixtures.ROOT + "core.MemUtil");
        Method pair = memUtil.getDeclaredMethod("pairInts", int.class, int.class);
        Method first = memUtil.getDeclaredMethod("firstInt", long.class);
        Method second = memUtil.getDeclaredMethod("secondInt", long.class);
        pair.setAccessible(true);
        first.setAccessible(true);
        second.setAccessible(true);
        long w = (Long) pair.invoke(null, 0x11223344, 0x55667788);
        ByteBuffer native8 = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(0, w);
        assertEquals(0x11223344, native8.getInt(0));
        assertEquals(0x55667788, native8.getInt(4));
        assertEquals(0x11223344, (int) (Integer) first.invoke(null, w));
        assertEquals(0x55667788, (int) (Integer) second.invoke(null, w));
    }
}
