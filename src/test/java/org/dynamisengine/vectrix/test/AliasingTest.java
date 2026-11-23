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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every destination-style method ({@code R op(..., R dest)}, the JOML convention) must give the same result when
 * {@code dest} is {@code this} or one of the arguments as when it is a separate object. The library promises this
 * throughout, and writing part of {@code dest} before reading an aliased input is the classic way to break it.
 * <p>
 * The harness finds such methods by reflection over every Vectrix class, runs each one with identical random inputs
 * and a fresh {@code dest}, then again with {@code dest} aliased, and requires bit-identical results.
 */
class AliasingTest {

    private static final int TRIALS = 3;

    /**
     * Aliasings that are rejected by contract, as "SimpleClass.method dest==target":
     * {@code swingTwist} requires distinct {@code swing} and {@code twist} objects and throws otherwise.
     */
    private static final Set<String> EXEMPT = new HashSet<>(Arrays.asList(
            "Quaternionf.swingTwist dest==arg1", "Quaterniond.swingTwist dest==arg1",
            "Quaternionfc.swingTwist dest==arg1", "Quaterniondc.swingTwist dest==arg1"));

    private final List<String> failures = new ArrayList<>();
    private int checked;
    private ExecutorService executor = newExecutor();

    private static ExecutorService newExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "aliasing-harness");
            t.setDaemon(true);
            return t;
        });
    }

    private static List<Class<?>> vectrixClasses() throws IOException {
        Path root = Paths.get("src/main/java");
        List<Class<?>> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                String name = root.relativize(p).toString().replace('/', '.').replace(".java", "");
                if (name.endsWith("module-info") || name.endsWith("package-info")) {
                    continue;
                }
                try {
                    out.add(Class.forName(name));
                } catch (ClassNotFoundException | LinkageError e) {
                    // not compiled in this profile (e.g. Vector API or JVMCI sources)
                }
            }
        }
        return out;
    }

    private static boolean drivable(Class<?> t) {
        return t == float.class || t == double.class || t == boolean.class || t == int.class || ValueFixtures.isValueType(t)
                || (t.isEnum() && t.getName().startsWith("org.dynamisengine.vectrix."));
    }

    private static Object primitive(Class<?> t, SplittableRandom rnd) {
        if (t == float.class) {
            return (float) rnd.nextDouble(-2, 2);
        }
        if (t == double.class) {
            return (double) (float) rnd.nextDouble(-2, 2);
        }
        if (t == int.class) {
            return rnd.nextInt(0, 3);
        }
        return rnd.nextBoolean();
    }

    /** Builds receiver (index -1) and arguments from per-position seeds so two calls get identical inputs. */
    private static Object[] build(Method m, long[] seeds, Object[] receiverOut) throws ReflectiveOperationException {
        Class<?>[] pt = m.getParameterTypes();
        Object[] args = new Object[pt.length];
        for (int i = 0; i < pt.length; i++) {
            SplittableRandom r = new SplittableRandom(seeds[i + 1]);
            if (pt[i].isEnum()) {
                Object[] constants = pt[i].getEnumConstants();
                args[i] = constants[(int) Math.floorMod(seeds[i + 1], (long) constants.length)];
                continue;
            }
            args[i] = pt[i].isPrimitive() ? primitive(pt[i], r) : ValueFixtures.create(pt[i], seeds[i + 1]);
        }
        if (!Modifier.isStatic(m.getModifiers())) {
            receiverOut[0] = ValueFixtures.create(m.getDeclaringClass() == Object.class ? null : receiverType(m), seeds[0]);
        }
        return args;
    }

    private static Class<?> receiverType(Method m) {
        return currentClass.get();
    }

    private static final ThreadLocal<Class<?>> currentClass = new ThreadLocal<>();

    private Object call(Method m, Object receiver, Object[] args) throws Throwable {
        Future<Object> f = executor.submit(() -> m.invoke(receiver, args));
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            executor.shutdownNow();
            executor = newExecutor();
            throw new TimeoutException("did not return within 2 s");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable c = e.getCause();
            throw c instanceof InvocationTargetException ? c.getCause() : c;
        }
    }

    private void check(Class<?> cls, Method m) throws Exception {
        Class<?>[] pt = m.getParameterTypes();
        Class<?> r = m.getReturnType();
        int destIndex = pt.length - 1;
        boolean isStatic = Modifier.isStatic(m.getModifiers());
        // alias targets: -1 = this, i = argument i
        List<Integer> targets = new ArrayList<>();
        if (!isStatic && r.isAssignableFrom(cls) && cls == r) {
            targets.add(-1);
        }
        for (int i = 0; i < destIndex; i++) {
            if (!pt[i].isPrimitive() && pt[i].isAssignableFrom(r) && ValueFixtures.concrete(pt[i]) == r) {
                targets.add(i);
            }
        }
        if (targets.isEmpty()) {
            return;
        }
        String name = cls.getSimpleName() + "." + m.getName();
        if (EXEMPT.contains(name)) {
            return;
        }
        currentClass.set(cls);
        for (int t = 0; t < TRIALS; t++) {
            long base = 1_000_003L * m.toGenericString().hashCode() + t;
            long[] seeds = new long[pt.length + 1];
            for (int i = 0; i < seeds.length; i++) {
                seeds[i] = base * 31 + i;
            }
            if (!checkDestIndependence(m, name, pt, seeds, destIndex, r)) {
                return;
            }
            for (int target : targets) {
                // Reference: a separate dest that starts as a copy of the object it will later alias, so methods
                // that write only part of dest (e.g. transpose3x3) are compared fairly.
                Object[] recv = new Object[1];
                Object[] ref = build(m, seeds, recv);
                ref[destIndex] = ValueFixtures.create(r, target == -1 ? seeds[0] : seeds[target + 1]);
                Object refResult;
                try {
                    refResult = call(m, recv[0], ref);
                } catch (TimeoutException e) {
                    failures.add(name + Arrays.toString(pt) + ": hangs (" + e.getMessage() + ")");
                    return;
                } catch (Throwable e) {
                    continue; // input outside the method's domain; nothing to compare
                }
                if (refResult != ref[destIndex]) {
                    return; // the last parameter is an input, not a destination (set, swap, mul(..., m), ...)
                }
                double[] expected = ValueFixtures.flatten(refResult);
                Object[] aliasRecv = new Object[1];
                Object[] args = build(m, seeds, aliasRecv);
                Object alias = target == -1 ? aliasRecv[0] : args[target];
                args[destIndex] = alias;
                Object result;
                String how = " dest==" + (target == -1 ? "this" : "arg" + target);
                try {
                    result = call(m, aliasRecv[0], args);
                } catch (Throwable e) {
                    if (!EXEMPT.contains(name + how)) {
                        failures.add(name + Arrays.toString(pt) + how + ": threw " + e);
                    }
                    continue;
                }
                checked++;
                double[] got = ValueFixtures.flatten(result);
                if (!Arrays.equals(expected, got) && !EXEMPT.contains(name + how)) {
                    int k = 0;
                    while (k < expected.length && Double.compare(expected[k], got[k]) == 0) {
                        k++;
                    }
                    failures.add(name + Arrays.toString(pt) + how + " trial " + t + " [" + k + "] expected "
                            + expected[k] + " got " + got[k]);
                    return;
                }
            }
        }
    }

    /**
     * Methods documented to write only part of {@code dest} (the rest keeps its previous contents), as
     * "SimpleClass.method": {@code transpose3x3} is specified as "all other matrix elements are left unchanged".
     */
    private static final Set<String> PARTIAL_WRITERS = new HashSet<>(Arrays.asList(
            "Matrix4f.transpose3x3", "Matrix4d.transpose3x3", "Matrix4x3f.transpose3x3", "Matrix4x3d.transpose3x3"));

    private int independenceChecked;

    /**
     * The result must not depend on what {@code dest} held before the call: run with two different random
     * destinations and compare. Returns false if the method should not be checked further (not a dest method).
     */
    private boolean checkDestIndependence(Method m, String name, Class<?>[] pt, long[] seeds, int destIndex, Class<?> r)
            throws Exception {
        Object[] recvA = new Object[1], recvB = new Object[1];
        Object[] a = build(m, seeds, recvA);
        Object[] b = build(m, seeds, recvB);
        a[destIndex] = ValueFixtures.create(r, seeds[destIndex + 1] ^ 0x5DEECE66DL);
        b[destIndex] = ValueFixtures.create(r, seeds[destIndex + 1] ^ 0xBB67AE8584CAA73BL);
        Object ra, rb;
        try {
            ra = call(m, recvA[0], a);
            rb = call(m, recvB[0], b);
        } catch (TimeoutException e) {
            failures.add(name + Arrays.toString(pt) + ": hangs (" + e.getMessage() + ")");
            return false;
        } catch (Throwable e) {
            return true; // outside the domain for these inputs
        }
        if (ra != a[destIndex]) {
            return false; // not a destination-style method
        }
        independenceChecked++;
        double[] fa = ValueFixtures.flatten(ra), fb = ValueFixtures.flatten(rb);
        if (!Arrays.equals(fa, fb) && !PARTIAL_WRITERS.contains(name)) {
            int k = 0;
            while (k < fa.length && Double.compare(fa[k], fb[k]) == 0) {
                k++;
            }
            failures.add(name + Arrays.toString(pt) + ": result depends on dest's previous contents [" + k + "] "
                    + fa[k] + " vs " + fb[k]);
            return false;
        }
        return true;
    }

    @Test
    void destinationParametersMayAliasInputs() throws Exception {
        for (Class<?> cls : vectrixClasses()) {
            if (!Modifier.isPublic(cls.getModifiers())) {
                continue;
            }
            for (Method m : cls.getMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length == 0 || m.isSynthetic() || !m.getDeclaringClass().getName().startsWith(ValueFixtures.ROOT)) {
                    continue;
                }
                Class<?> r = m.getReturnType();
                if (pt[pt.length - 1] != r || !ValueFixtures.isValueType(r) || r.isInterface()) {
                    continue;
                }
                if (!Modifier.isStatic(m.getModifiers()) && !ValueFixtures.isValueType(cls)) {
                    continue;
                }
                if (!Arrays.stream(pt).allMatch(AliasingTest::drivable)) {
                    continue;
                }
                check(cls, m);
            }
        }
        System.out.println("AliasingTest: " + checked + " aliased calls and " + independenceChecked
                + " dest-independence checks, " + failures.size() + " failures");
        failures.forEach(f -> System.out.println("  " + f));
        assertTrue(checked > 1000, "harness drove too few methods: " + checked);
        assertTrue(failures.isEmpty(), () -> failures.size() + " aliasing failures, first: " + failures.get(0));
    }
}
