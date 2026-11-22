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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Reflection fixtures for the property harnesses: random, reproducible instances of Vectrix value types (vectors,
 * matrices, quaternions, transforms) and a flat numeric view for comparing them.
 */
final class ValueFixtures {

    static final String ROOT = "org.dynamisengine.vectrix.";

    private ValueFixtures() {
    }

    /** The concrete class for a value type or its read-only interface ({@code Vector3fc} -> {@code Vector3f}). */
    static Class<?> concrete(Class<?> t) {
        if (!t.isInterface()) {
            return t;
        }
        try {
            return Class.forName(t.getName().substring(0, t.getName().length() - 1));
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /** Whether {@code t} is a Vectrix type that can be created with random contents. */
    static boolean isValueType(Class<?> t) {
        if (!t.getName().startsWith(ROOT) || t.isEnum() || t.isArray()) {
            return false;
        }
        Class<?> c = concrete(t);
        if (c == null || Modifier.isAbstract(c.getModifiers()) || !t.isAssignableFrom(c)) {
            return false;
        }
        try {
            c.getConstructor();
        } catch (NoSuchMethodException e) {
            return false;
        }
        return !numericFields(c).isEmpty() || !valueFields(c).isEmpty();
    }

    static Object create(Class<?> t, long seed) throws ReflectiveOperationException {
        Class<?> c = concrete(t);
        Constructor<?> ctor = c.getConstructor();
        Object o = ctor.newInstance();
        fill(o, new SplittableRandom(seed));
        return o;
    }

    static void fill(Object o, SplittableRandom rnd) throws ReflectiveOperationException {
        Class<?> c = o.getClass();
        for (Field f : numericFields(c)) {
            Class<?> ft = f.getType();
            if (f.getName().equals("properties")) {
                f.setInt(o, 0); // unknown: no identity/affine shortcuts from stale flags
            } else if (ft == float.class) {
                f.setFloat(o, (float) rnd.nextDouble(-2, 2));
            } else if (ft == double.class) {
                f.setDouble(o, (float) rnd.nextDouble(-2, 2)); // float-representable, so f/d twins match
            } else if (ft == int.class) {
                f.setInt(o, rnd.nextInt(-5, 6));
            } else if (ft == long.class) {
                f.setLong(o, rnd.nextLong(-5, 6));
            }
        }
        for (Field f : valueFields(c)) {
            Object nested = f.get(o);
            if (nested != null) {
                fill(nested, rnd);
            }
        }
        if (c.getSimpleName().startsWith("Quaternion")) {
            c.getMethod("normalize").invoke(o);
        }
    }

    /** Non-static float/double/int/long fields, sorted by name (twins may declare them in a different order). */
    static List<Field> numericFields(Class<?> c) {
        List<Field> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                Class<?> ft = f.getType();
                // "__" fields are layout padding (e.g. Quaternionf.__pad in JVMCI builds), not state
                if (!Modifier.isStatic(f.getModifiers()) && !f.getName().startsWith("__")
                        && (ft == float.class || ft == double.class || ft == int.class || ft == long.class)) {
                    f.setAccessible(true);
                    out.add(f);
                }
            }
        }
        out.sort(Comparator.comparing(Field::getName));
        return out;
    }

    /** Non-static fields holding nested Vectrix value objects (e.g. {@code Transformf.translation}). */
    static List<Field> valueFields(Class<?> c) {
        List<Field> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType().getName().startsWith(ROOT) && !f.getType().isEnum()) {
                    f.setAccessible(true);
                    out.add(f);
                }
            }
        }
        out.sort(Comparator.comparing(Field::getName));
        return out;
    }

    /** All numeric state, nested objects included, as doubles in a fixed order ("properties" excluded). */
    static double[] flatten(Object o) throws IllegalAccessException {
        List<Double> v = new ArrayList<>();
        flatten(o, v);
        double[] a = new double[v.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = v.get(i);
        }
        return a;
    }

    private static void flatten(Object o, List<Double> v) throws IllegalAccessException {
        if (o instanceof Number) {
            v.add(((Number) o).doubleValue());
            return;
        }
        if (o instanceof Boolean) {
            v.add(((Boolean) o) ? 1.0 : 0.0);
            return;
        }
        for (Field f : numericFields(o.getClass())) {
            if (!f.getName().equals("properties")) {
                v.add(((Number) f.get(o)).doubleValue());
            }
        }
        for (Field f : valueFields(o.getClass())) {
            Object nested = f.get(o);
            if (nested != null) {
                flatten(nested, v);
            }
        }
    }
}
