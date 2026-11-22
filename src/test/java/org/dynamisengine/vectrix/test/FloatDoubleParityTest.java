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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/**
 * Review finding M-17 (coverage). Every public method of each float type is called next to its double twin with the
 * same random inputs, and the results must agree to float precision. This exercises both precisions of the
 * inherited JOML types and the Vectrix packages at once, and a disagreement points at a real bug in one of them.
 * <p>
 * Only methods whose parameters are primitives or Vectrix value types are driven. Methods whose results are
 * legitimately precision-sensitive (ill-conditioned, chaotic or discontinuous) are listed in {@link #EXEMPT} with the
 * reason.
 */
class FloatDoubleParityTest {

    private static final String ROOT = "org.dynamisengine.vectrix.";
    private static final String[] PAIRS = {
        "core.Vector2", "core.Vector3", "core.Vector4", "core.Matrix2", "core.Matrix3", "core.Matrix3x2", "core.Matrix4",
        "core.Matrix4x3", "core.Quaternion", "core.AxisAngle4", "core.Interpolation", "core.Range",
        "geometry.Intersection", "easing.Easing", "sdf.Sdf3", "color.ColorMath", "color.ColorScience", "optics.Fresnel",
        "optics.Ior", "optics.SpectralRGB", "optics.ThinFilm", "renderingmath.BentNormalCone",
        "renderingmath.Interpolation", "ltc.LtcEval", "curve.scalar.Bezier1", "curve.scalar.CatmullRom1",
        "curve.scalar.Hermite1", "curve.vec2.Bezier2", "curve.vec2.CatmullRom2", "curve.vec2.Hermite2",
        "curve.vec3.Bezier3", "curve.vec3.CatmullRom3", "curve.vec3.Hermite3", "curve.vec4.Bezier4",
        "curve.vec4.CatmullRom4", "curve.vec4.Hermite4",
    };

    /**
     * Methods excluded from the comparison. Object methods have no float/double meaning; the others have documented
     * input domains that random inputs violate, so their results there are unspecified:
     * <ul>
     * <li>{@code Matrix4f.rotateAffine}: requires an affine matrix (it rewrites the last row).</li>
     * <li>{@code Matrix4f.rotateTranslation}: requires a translation-only matrix.</li>
     * <li>{@code Quaternionf.nlerpIterative}: requires {@code alpha} in [0, 1] and a threshold below 1.</li>
     * </ul>
     */
    private static final Set<String> EXEMPT = new HashSet<>(Arrays.asList(
            "hashCode", "equals", "toString", "getClass", "notify", "notifyAll", "wait", "compareTo",
            "Matrix4f.rotateAffine", "Matrix4f.rotateTranslation", "Quaternionf.nlerpIterative"));

    private static final int TRIALS = 4;
    private static final double REL_TOL = 2e-3;

    private final List<String> mismatches = new ArrayList<>();
    private int compared;

    private static Class<?> twin(Class<?> c) {
        if (c == float.class) {
            return double.class;
        }
        if (c == Float.class) {
            return Double.class;
        }
        if (c.getName().startsWith(ROOT)) {
            String n = c.getName().replaceAll("f(c?)$", "d$1");
            try {
                return Class.forName(n);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        return c;
    }

    private static boolean drivable(Class<?> c) {
        return c == float.class || c == boolean.class || (c.getName().startsWith(ROOT) && twin(c) != null && !c.isEnum());
    }

    /** A fresh, randomly filled instance of a Vectrix value type (or interface: its implementation). */
    private static Object create(Class<?> t, long seed) throws ReflectiveOperationException {
        Class<?> impl = t;
        if (t.isInterface()) {
            impl = Class.forName(t.getName().substring(0, t.getName().length() - 1)); // Vector3fc -> Vector3f
        }
        Constructor<?> ctor = impl.getDeclaredConstructor();
        Object o = ctor.newInstance();
        SplittableRandom rnd = new SplittableRandom(seed);
        for (Field f : fields(impl)) {
            double v = rnd.nextDouble(-2, 2);
            if (f.getType() == float.class) {
                f.setFloat(o, (float) v);
            } else if (f.getType() == double.class) {
                f.setDouble(o, (float) v); // the same float-representable value on both sides
            } else if (f.getType() == int.class && f.getName().equals("properties")) {
                f.setInt(o, 0); // unknown: no identity/affine shortcuts from stale flags
            }
        }
        if (impl.getSimpleName().startsWith("Quaternion")) {
            impl.getMethod("normalize").invoke(o);
        }
        return o;
    }

    /** Numeric fields sorted by name, so float and double twins line up even if declared in a different order. */
    private static List<Field> fields(Class<?> c) {
        List<Field> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && (f.getType() == float.class || f.getType() == double.class
                        || (f.getType() == int.class && f.getName().equals("properties")))) {
                    f.setAccessible(true);
                    out.add(f);
                }
            }
        }
        out.sort(java.util.Comparator.comparing(Field::getName));
        return out;
    }

    private static double[] numbers(Object o) throws IllegalAccessException {
        if (o instanceof Float) {
            return new double[] {(Float) o};
        }
        if (o instanceof Double) {
            return new double[] {(Double) o};
        }
        if (o instanceof Boolean) {
            return new double[] {((Boolean) o) ? 1 : 0};
        }
        if (o instanceof Integer) {
            return new double[] {(Integer) o};
        }
        List<Field> fs = fields(o.getClass());
        List<Double> v = new ArrayList<>();
        for (Field f : fs) {
            if (f.getName().equals("properties")) {
                continue;
            }
            v.add(f.getType() == float.class ? f.getFloat(o) : f.getDouble(o));
        }
        double[] a = new double[v.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = v.get(i);
        }
        return a;
    }

    private static boolean close(double f, double d) {
        if (Double.isNaN(f) || Double.isNaN(d)) {
            return Double.isNaN(f) && Double.isNaN(d);
        }
        if (Double.isInfinite(f) || Double.isInfinite(d)) {
            return f == d || java.lang.Math.abs(d) > 1e30;
        }
        return java.lang.Math.abs(f - d) <= REL_TOL * java.lang.Math.max(1.0, java.lang.Math.abs(d));
    }

    private void compare(String where, Object f, Object d) throws IllegalAccessException {
        if (f == null || d == null) {
            if (f != d) {
                mismatches.add(where + ": null vs non-null");
            }
            return;
        }
        double[] a = numbers(f), b = numbers(d);
        if (a.length != b.length) {
            return; // differently shaped types (e.g. float[] vs double[] holders); not comparable
        }
        for (int i = 0; i < a.length; i++) {
            if (!close(a[i], b[i])) {
                mismatches.add(where + " [" + i + "] f=" + a[i] + " d=" + b[i]);
                return;
            }
        }
    }

    private void drive(Class<?> fc, Class<?> dc) throws Exception {
        for (Method mf : fc.getMethods()) {
            if (mf.getDeclaringClass() == Object.class || EXEMPT.contains(mf.getName())
                    || EXEMPT.contains(fc.getSimpleName() + "." + mf.getName()) || mf.isSynthetic()) {
                continue;
            }
            Class<?>[] pf = mf.getParameterTypes();
            if (!Arrays.stream(pf).allMatch(FloatDoubleParityTest::drivable)) {
                continue;
            }
            Class<?>[] pd = new Class<?>[pf.length];
            for (int i = 0; i < pf.length; i++) {
                pd[i] = twin(pf[i]);
            }
            Method md;
            try {
                md = dc.getMethod(mf.getName(), pd);
            } catch (NoSuchMethodException e) {
                continue; // no double twin with the mapped signature
            }
            boolean isStatic = Modifier.isStatic(mf.getModifiers());
            if (isStatic != Modifier.isStatic(md.getModifiers())) {
                continue;
            }
            for (int t = 0; t < TRIALS; t++) {
                long seed = 31L * mf.toGenericString().hashCode() + t;
                SplittableRandom rnd = new SplittableRandom(seed);
                Object[] af = new Object[pf.length], ad = new Object[pf.length];
                for (int i = 0; i < pf.length; i++) {
                    if (pf[i] == float.class) {
                        float v = (float) rnd.nextDouble(-2, 2);
                        af[i] = v;
                        ad[i] = (double) v;
                    } else if (pf[i] == boolean.class) {
                        boolean v = rnd.nextBoolean();
                        af[i] = v;
                        ad[i] = v;
                    } else {
                        long s = rnd.nextLong();
                        af[i] = create(pf[i], s);
                        ad[i] = create(pd[i], s);
                    }
                }
                Object rf = null, rd = null, thisF = null, thisD = null;
                if (!isStatic) {
                    long s = rnd.nextLong();
                    try {
                        thisF = create(fc, s);
                        thisD = create(dc, s);
                    } catch (NoSuchMethodException e) {
                        break; // no default constructor
                    }
                }
                Throwable ef = null, ed = null;
                try {
                    rf = mf.invoke(thisF, af);
                } catch (InvocationTargetException e) {
                    ef = e.getCause();
                }
                try {
                    rd = md.invoke(thisD, ad);
                } catch (InvocationTargetException e) {
                    ed = e.getCause();
                }
                String where = fc.getSimpleName() + "." + mf.getName() + Arrays.toString(pf) + " trial " + t;
                if (ef != null || ed != null) {
                    if (ef == null || ed == null || ef.getClass() != ed.getClass()) {
                        mismatches.add(where + ": f threw " + ef + ", d threw " + ed);
                    }
                    continue;
                }
                compared++;
                compare(where + " result", rf, rd);
                if (thisF != null) {
                    compare(where + " this", thisF, thisD);
                }
                for (int i = 0; i < af.length; i++) {
                    if (!pf[i].isPrimitive()) {
                        compare(where + " arg" + i, af[i], ad[i]);
                    }
                }
            }
        }
    }

    @Test
    void floatAndDoubleTypesAgree() throws Exception {
        for (String p : PAIRS) {
            drive(Class.forName(ROOT + p + "f"), Class.forName(ROOT + p + "d"));
        }
        System.out.println("FloatDoubleParityTest: " + compared + " calls compared, " + mismatches.size() + " mismatches");
        mismatches.forEach(m -> System.out.println("  " + m));
        assertTrue(compared > 1000, "harness drove too few methods: " + compared);
        assertTrue(mismatches.isEmpty(), () -> mismatches.size() + " float/double mismatches, first: " + mismatches.get(0));
    }
}
