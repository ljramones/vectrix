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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.dynamisengine.vectrix.affine.TransformKernels;
import org.dynamisengine.vectrix.core.CoreConfig;
import org.dynamisengine.vectrix.core.MathMode;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.geometry.CullingKernels;
import org.dynamisengine.vectrix.geometry.FrustumPlanes;
import org.dynamisengine.vectrix.soa.AABBSoA;
import org.dynamisengine.vectrix.soa.TransformSoA;

/**
 * Prints one line per public method, {@code <Class.method(params)> <hash>}, hashing the results of calling it with
 * seeded inputs, plus lines for the batch kernels at realistic sizes in FAST and STRICT mode. Run under
 * {@code -Dvectrix.strictMath=true}, the output must be identical on every platform and JVM; see
 * {@link CrossPlatformDeterminismTest}.
 * <p>
 * NaN results hash as one value: which NaN bit pattern an operation produces differs between x86 and ARM, and Java
 * allows that. Everything else, including the sign of zero, must match bit for bit.
 */
public final class DeterminismDigest {

    private static final String ROOT = ValueFixtures.ROOT;
    private static final int TRIALS = 3;
    private static final int PRIMITIVE_ARRAY_LENGTH = 48;
    private static final int OBJECT_ARRAY_LENGTH = 4;

    /** Classes that hold global configuration or touch memory, and are not part of the math surface. */
    private static final Set<String> EXCLUDED_CLASSES = new HashSet<>(Arrays.asList(
            "core.MemUtil", "core.Options", "core.CoreConfig", "core.ConfigurationException", "core.Runtime",
            "simd.SimdSupport"));

    /** Object methods, the raw-address API, {@code Math.random}, and documented-domain exemptions shared with TwinParityTest. */
    private static final Set<String> EXCLUDED_METHODS = new HashSet<>(Arrays.asList(
            "hashCode", "equals", "toString", "getClass", "notify", "notifyAll", "wait", "compareTo",
            "Quaternionf.nlerpIterative", "Quaterniond.nlerpIterative", "Math.random"));

    private DeterminismDigest() {
    }

    public static void main(String[] args) throws Exception {
        List<String> lines = new ArrayList<>();
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "determinism-digest");
            t.setDaemon(true);
            return t;
        });
        try {
            for (String name : classNames()) {
                Class<?> c;
                try {
                    c = Class.forName(name);
                } catch (Throwable e) {
                    continue; // classes needing optional modules (JVMCI) in this profile
                }
                if (!Modifier.isPublic(c.getModifiers()) || c.isInterface() || c.isEnum()) {
                    continue;
                }
                drive(c, executor, lines);
            }
            kernels(lines);
        } finally {
            executor.shutdownNow();
        }
        lines.forEach(System.out::println);
    }

    /** Every top-level class of the library, sorted. */
    private static List<String> classNames() throws Exception {
        Path root = new File(Matrix4f.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath();
        try (Stream<Path> s = Files.walk(root)) {
            return s.map(p -> root.relativize(p).toString().replace(File.separatorChar, '/'))
                    .filter(p -> p.endsWith(".class") && !p.contains("$") && !p.endsWith("module-info.class"))
                    .map(p -> p.substring(0, p.length() - 6).replace('/', '.'))
                    .filter(n -> n.startsWith(ROOT) && !EXCLUDED_CLASSES.contains(n.substring(ROOT.length())))
                    .filter(n -> !n.substring(n.lastIndexOf('.') + 1).startsWith("Jvmci"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static boolean drivable(Class<?> c) {
        if (c.isArray()) {
            Class<?> e = c.getComponentType();
            return e == float.class || e == double.class || e == int.class || e == long.class
                    || ValueFixtures.isValueType(e);
        }
        return (c.isEnum() && c.getName().startsWith("org.dynamisengine.vectrix.")) || c == float.class || c == double.class || c == int.class || c == long.class || c == boolean.class
                || ValueFixtures.isValueType(c);
    }

    private static Object value(Class<?> t, SplittableRandom rnd, long seed) throws ReflectiveOperationException {
        if (t == float.class) {
            return (float) rnd.nextDouble(-2, 2);
        }
        if (t == double.class) {
            return rnd.nextDouble(-2, 2);
        }
        if (t == int.class) {
            return rnd.nextInt(0, 3);
        }
        if (t == long.class) {
            return (long) rnd.nextInt(0, 3);
        }
        if (t == boolean.class) {
            return rnd.nextBoolean();
        }
        if (t.isEnum()) {
            Object[] constants = t.getEnumConstants();
            return constants[rnd.nextInt(constants.length)];
        }
        if (t.isArray()) {
            Class<?> e = t.getComponentType();
            int n = e.isPrimitive() ? PRIMITIVE_ARRAY_LENGTH : OBJECT_ARRAY_LENGTH;
            Object a = Array.newInstance(e, n);
            for (int i = 0; i < n; i++) {
                Array.set(a, i, value(e, rnd, seed * 31 + i));
            }
            return a;
        }
        return ValueFixtures.create(t, seed);
    }

    private static String signature(Class<?> c, Method m) {
        return c.getName().substring(ROOT.length()) + "." + m.getName() + Arrays.stream(m.getParameterTypes())
                .map(Class::getSimpleName).collect(Collectors.joining(",", "(", ")"));
    }

    private static void drive(Class<?> c, ExecutorService executor, List<String> lines) throws Exception {
        Method[] methods = c.getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::toGenericString));
        for (Method m : methods) {
            if (m.getDeclaringClass() == Object.class || m.isSynthetic() || m.getName().contains("Address")
                    // deprecated overloads forward to their replacements; hashing both would collide on simple names
                    || m.isAnnotationPresent(Deprecated.class)
                    || EXCLUDED_METHODS.contains(m.getName())
                    || EXCLUDED_METHODS.contains(c.getSimpleName() + "." + m.getName())
                    || !m.getDeclaringClass().getName().startsWith(ROOT)
                    || !Arrays.stream(m.getParameterTypes()).allMatch(DeterminismDigest::drivable)) {
                continue;
            }
            boolean isStatic = Modifier.isStatic(m.getModifiers());
            if (!isStatic && !ValueFixtures.isValueType(c)) {
                continue;
            }
            // static setters change global state that later calls would see
            if (isStatic && m.getName().startsWith("set") && m.getReturnType() == void.class) {
                continue;
            }
            Hasher h = new Hasher();
            Class<?>[] p = m.getParameterTypes();
            boolean built = true;
            for (int t = 0; t < TRIALS && built; t++) {
                long seed = 31L * m.toGenericString().hashCode() + t;
                SplittableRandom rnd = new SplittableRandom(seed);
                Object[] a = new Object[p.length];
                Object self;
                try {
                    for (int i = 0; i < p.length; i++) {
                        a[i] = value(p[i], rnd, seed * 1_000_003L + i);
                    }
                    self = isStatic ? null : ValueFixtures.create(c, seed ^ 0x9E3779B97F4A7C15L);
                } catch (ReflectiveOperationException e) {
                    built = false; // no fixture for one of the types: not drivable
                    continue;
                }
                Future<Object> f = executor.submit(() -> m.invoke(self, a));
                Object r;
                try {
                    r = f.get(10, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    throw new IllegalStateException(signature(c, m) + " did not return within 10 s");
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause() instanceof InvocationTargetException
                            ? e.getCause().getCause() : e.getCause();
                    h.text(cause.getClass().getName());
                    continue;
                }
                h.value(r);
                if (self != null) {
                    h.value(self);
                }
                for (int i = 0; i < p.length; i++) {
                    if (!p[i].isPrimitive()) {
                        h.value(a[i]);
                    }
                }
            }
            if (built) {
                lines.add(signature(c, m) + " " + h.hex());
            }
        }
    }

    // ---- batch kernels at sizes that exercise the SIMD loops and their tails ----

    private static void kernels(List<String> lines) throws Exception {
        for (MathMode mode : MathMode.values()) {
            for (int n : new int[] {1, 7, 1027}) {
                lines.add("kernel.composeBatch[" + mode + "," + n + "] "
                        + CoreConfig.callWithMathMode(mode, () -> compose(n)));
                lines.add("kernel.frustumCullAabbBatch[" + mode + "," + n + "] "
                        + CoreConfig.callWithMathMode(mode, () -> cull(n)));
            }
        }
    }

    private static TransformSoA transforms(SplittableRandom rnd, int n) {
        TransformSoA t = new TransformSoA(n);
        Quaternionf q = new Quaternionf();
        for (int i = 0; i < n; i++) {
            q.rotationXYZ((float) rnd.nextDouble(-3, 3), (float) rnd.nextDouble(-3, 3), (float) rnd.nextDouble(-3, 3));
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

    private static String compose(int n) throws IllegalAccessException {
        SplittableRandom rnd = new SplittableRandom(n);
        TransformSoA out = new TransformSoA(n);
        TransformKernels.composeBatch(transforms(rnd, n), transforms(rnd, n), out, n);
        Hasher h = new Hasher();
        for (float[] a : new float[][] {out.tx, out.ty, out.tz, out.qx, out.qy, out.qz, out.qw, out.sx, out.sy,
            out.sz}) {
            h.value(a);
        }
        return h.hex();
    }

    private static String cull(int n) throws IllegalAccessException {
        SplittableRandom rnd = new SplittableRandom(n + 1);
        Matrix4f viewProj = new Matrix4f().perspective(1.1f, 1.3f, 0.5f, 80f).lookAt(3, 4, 12, 0, 0, 0, 0, 1, 0);
        AABBSoA boxes = new AABBSoA(n);
        for (int i = 0; i < n; i++) {
            float cx = (float) rnd.nextDouble(-40, 40), cy = (float) rnd.nextDouble(-40, 40);
            float cz = (float) rnd.nextDouble(-70, 20), e = (float) rnd.nextDouble(0.05, 6);
            boxes.set(i, cx - e, cy - e, cz - e, cx + e, cy + e, cz + e);
        }
        int[] out = new int[n];
        CullingKernels.frustumCullAabbBatch(new FrustumPlanes().set(viewProj, true), boxes, out, n);
        Hasher h = new Hasher();
        h.value(out);
        return h.hex();
    }

    /** SHA-256 over canonical bits, truncated to 64 bits for the printout. */
    private static final class Hasher {
        private final MessageDigest md;

        Hasher() {
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        void text(String s) {
            md.update(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        private void bits(long v) {
            for (int i = 0; i < 8; i++) {
                md.update((byte) (v >>> (i * 8)));
            }
        }

        void value(Object o) throws IllegalAccessException {
            if (o == null) {
                text("null");
            } else if (o instanceof Float f) {
                bits(Float.floatToIntBits(f)); // floatToIntBits folds every NaN into one
            } else if (o instanceof Double d) {
                bits(Double.doubleToLongBits(d));
            } else if (o instanceof Long l) {
                bits(l);
            } else if (o instanceof Number num) {
                bits(num.intValue());
            } else if (o instanceof Boolean b) {
                bits(b ? 1 : 0);
            } else if (o instanceof Enum<?> e) {
                text(e.name());
            } else if (o.getClass().isArray()) {
                int n = Array.getLength(o);
                bits(n);
                for (int i = 0; i < n; i++) {
                    value(Array.get(o, i));
                }
            } else if (o.getClass().getName().startsWith(ROOT)) {
                for (double d : ValueFixtures.flatten(o)) {
                    bits(Double.doubleToLongBits(d));
                }
            } else {
                text(o.getClass().getName());
            }
        }

        String hex() {
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        }
    }

    static List<String> readLines(Path p) throws IOException {
        return Files.readAllLines(p);
    }
}
