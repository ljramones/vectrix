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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Review finding M-17 (coverage) and a bug finder: every public method of a type is called next to its twin with the
 * same random inputs, and the results must agree.
 * <ul>
 * <li>float vs double types ({@code Matrix4f}/{@code Matrix4d}, {@code Sdf3f}/{@code Sdf3d}, ...): within float
 * precision;</li>
 * <li>int vs long vectors ({@code Vector3i}/{@code Vector3L}): exactly, for small values that cannot overflow;</li>
 * <li>float vs double overloads inside one class ({@code core.Math}, {@code CubicCurveMath}, the SH classes).</li>
 * </ul>
 * Parameters may be primitives, arrays of them, Vectrix value types and arrays of those; arrays and objects are
 * compared after the call as well, since they are often outputs. Methods whose inputs have documented domains that
 * random values violate are listed in {@link #EXEMPT}.
 */
class TwinParityTest {

    private static final String ROOT = ValueFixtures.ROOT;

    private static final String[] FLOAT_DOUBLE = {
        "core.Vector2", "core.Vector3", "core.Vector4", "core.Matrix2", "core.Matrix3", "core.Matrix3x2", "core.Matrix4",
        "core.Matrix4x3", "core.Quaternion", "core.AxisAngle4", "core.Interpolation", "core.Range", "core.Epsilon",
        "geometry.Intersection", "easing.Easing", "sdf.Sdf3", "color.ColorMath", "color.ColorScience", "optics.Fresnel",
        "optics.Ior", "optics.SpectralRGB", "optics.SpectralSampling", "optics.ThinFilm", "renderingmath.BentNormalCone",
        "renderingmath.Interpolation", "ltc.LtcEval", "ltc.LtcTable", "fft.FFT1", "fft.Convolution", "fft.Complex",
        "curve.CurveReparameterizer3", "curve.scalar.Bezier1", "curve.scalar.CatmullRom1", "curve.scalar.Hermite1",
        "curve.vec2.Bezier2", "curve.vec2.CatmullRom2", "curve.vec2.Hermite2", "curve.vec3.Bezier3",
        "curve.vec3.CatmullRom3", "curve.vec3.Hermite3", "curve.vec4.Bezier4", "curve.vec4.CatmullRom4",
        "curve.vec4.Hermite4", "brdf.Microfacet", "brdf.Diffuse", "brdf.LightEngineCompat",
        "spherical.SphericalGaussians", "spherical.DirectionMappings", "camera.Projections", "camera.ShadowCascades",
        "camera.ClusterGrid", "camera.PhysicalCamera", "atmosphere.PhaseFunctions",
        "animation.QuaternionCurves", "animation.QuaternionSpring", "affine.DualQuatTransform",
        "sampling.Warp", "sampling.GgxSampling", "sampling.SphericalSampling",
        "color.TransferFunctions", "color.PerceptualColor", "color.Tonemap", "color.Exposure", "color.ColorSpaces",
        "linalg.Eigen3", "linalg.Svd3", "linalg.Polar3", "linalg.Qr", "linalg.Solvers", "linalg.Lie3", "linalg.LeastSquares",
        "geometry.ClosestPoints", "geometry.BoundingVolumes",
    };
    private static final String[] INT_LONG = {"core.Vector2", "core.Vector3", "core.Vector4"};
    private static final String[] OVERLOADS = {"core.Math", "curve.CubicCurveMath", "sh.ShBasis", "sh.ShProjection",
        "sh.ShConvolution", "sh.ShRotation", "sh.ShWindowing", "sh.ShProduct", "sh.ShL1"};

    /**
     * Excluded as "SimpleClass.method". Object methods have no twin meaning; the others have documented input
     * domains that random inputs violate, so their results there are unspecified:
     * <ul>
     * <li>{@code Matrix4f.rotateAffine}: requires an affine matrix (it rewrites the last row).</li>
     * <li>{@code Matrix4f.rotateTranslation}: requires a translation-only matrix.</li>
     * <li>{@code Quaternionf.nlerpIterative}: requires {@code alpha} in [0, 1] and a threshold below 1.</li>
     * <li>{@code BoundingVolumesf.pcaObb}, {@code approximateMinimalObb}: the box axes are not unique when the point
     * covariance has repeated eigenvalues (the harness passes 0 to 2 points), so float and double may choose
     * different, equally valid axes.</li>
     * </ul>
     */
    private static final Set<String> EXEMPT = new HashSet<>(Arrays.asList(
            "hashCode", "equals", "toString", "getClass", "notify", "notifyAll", "wait", "compareTo",
            "Matrix4f.rotateAffine", "Matrix4f.rotateTranslation", "Quaternionf.nlerpIterative",
            "BoundingVolumesf.pcaObb", "BoundingVolumesf.approximateMinimalObb"));

    private static final int TRIALS = 4;
    private static final double REL_TOL = 2e-3;
    private static final int PRIMITIVE_ARRAY_LENGTH = 48;
    private static final int OBJECT_ARRAY_LENGTH = 4;

    private final List<String> mismatches = new ArrayList<>();
    private int compared;
    private ExecutorService executor = newExecutor();

    private static ExecutorService newExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "parity-harness");
            t.setDaemon(true);
            return t;
        });
    }

    private static Function<Class<?>, Class<?>> mapping(String from, String to) {
        Class<?> fromPrim = from.equals("f") ? float.class : int.class;
        Class<?> toPrim = to.equals("d") ? double.class : long.class;
        return new Function<Class<?>, Class<?>>() {
            @Override
            public Class<?> apply(Class<?> c) {
                if (c == fromPrim) {
                    return toPrim;
                }
                if (c.isArray()) {
                    Class<?> e = apply(c.getComponentType());
                    return e == null ? null : Array.newInstance(e, 0).getClass();
                }
                if (c.getName().startsWith(ROOT)) {
                    String n = c.getName().replaceAll(from + "(c?)$", to + "$1");
                    try {
                        return Class.forName(n);
                    } catch (ClassNotFoundException e) {
                        return c;
                    }
                }
                return c;
            }
        };
    }

    private static boolean drivable(Class<?> c) {
        if (c.isArray()) {
            Class<?> e = c.getComponentType();
            return e == float.class || e == double.class || e == int.class || e == long.class || ValueFixtures.isValueType(e);
        }
        return c == float.class || c == double.class || c == int.class || c == long.class || c == boolean.class
                || ValueFixtures.isValueType(c);
    }

    /** The twin of {@code m} in {@code twin}: same name and arity, each parameter mapped or unchanged. */
    private static Method findTwin(Method m, Class<?> twin, Function<Class<?>, Class<?>> map, boolean sameClass) {
        Class<?>[] pf = m.getParameterTypes();
        for (Method c : twin.getMethods()) {
            if (!c.getName().equals(m.getName()) || c.getParameterCount() != pf.length || c.equals(m)) {
                continue;
            }
            if (Modifier.isStatic(c.getModifiers()) != Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            Class<?>[] pd = c.getParameterTypes();
            boolean ok = true;
            boolean anyMapped = false;
            for (int i = 0; i < pf.length && ok; i++) {
                Class<?> mapped = map.apply(pf[i]);
                if (pd[i] == mapped && mapped != pf[i]) {
                    anyMapped = true;
                } else if (pd[i] != pf[i]) {
                    ok = false;
                }
            }
            if (ok && (anyMapped || !sameClass)) {
                return c;
            }
        }
        return null;
    }

    private static Object value(Class<?> t, SplittableRandom rnd, long seed) throws ReflectiveOperationException {
        if (t == float.class) {
            return (float) rnd.nextDouble(-2, 2);
        }
        if (t == double.class) {
            return (double) (float) rnd.nextDouble(-2, 2);
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

    private static double[] numbers(Object o) throws IllegalAccessException {
        if (o == null) {
            return new double[0];
        }
        if (o.getClass().isArray()) {
            List<Double> v = new ArrayList<>();
            for (int i = 0; i < Array.getLength(o); i++) {
                for (double d : numbers(Array.get(o, i))) {
                    v.add(d);
                }
            }
            return v.stream().mapToDouble(Double::doubleValue).toArray();
        }
        if (o instanceof Number || o instanceof Boolean || o.getClass().getName().startsWith(ROOT)) {
            return ValueFixtures.flatten(o);
        }
        return new double[0];
    }

    private static boolean close(double f, double d, boolean exact) {
        if (Double.isNaN(f) || Double.isNaN(d)) {
            return Double.isNaN(f) && Double.isNaN(d);
        }
        if (exact) {
            return f == d;
        }
        if (Double.isInfinite(f) || Double.isInfinite(d)) {
            return f == d || java.lang.Math.abs(d) > 1e30;
        }
        return java.lang.Math.abs(f - d) <= REL_TOL * java.lang.Math.max(1.0, java.lang.Math.abs(d));
    }

    private void compare(String where, Object f, Object d, boolean exact) throws IllegalAccessException {
        double[] a = numbers(f), b = numbers(d);
        if (a.length != b.length) {
            return; // not comparable (different shapes)
        }
        for (int i = 0; i < a.length; i++) {
            if (!close(a[i], b[i], exact)) {
                mismatches.add(where + " [" + i + "] a=" + a[i] + " b=" + b[i]);
                return;
            }
        }
    }

    private Object call(Method m, Object receiver, Object[] args) throws Throwable {
        Future<Object> f = executor.submit(() -> m.invoke(receiver, args));
        try {
            return f.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            executor.shutdownNow();
            executor = newExecutor();
            throw e;
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable c = e.getCause();
            throw c instanceof InvocationTargetException ? c.getCause() : c;
        }
    }

    private void drive(Class<?> ca, Class<?> cb, Function<Class<?>, Class<?>> map, boolean exact) throws Exception {
        boolean sameClass = ca == cb;
        for (Method ma : ca.getMethods()) {
            // the raw-address API takes a native address as a long; random values would crash the JVM
            if (ma.getName().contains("Address")) {
                continue;
            }
            if (ma.getDeclaringClass() == Object.class || ma.isSynthetic() || EXEMPT.contains(ma.getName())
                    || EXEMPT.contains(ca.getSimpleName() + "." + ma.getName())
                    || !ma.getDeclaringClass().getName().startsWith(ROOT)) {
                continue;
            }
            Class<?>[] pa = ma.getParameterTypes();
            if (!Arrays.stream(pa).allMatch(TwinParityTest::drivable)) {
                continue;
            }
            Method mb = findTwin(ma, cb, map, sameClass);
            if (mb == null) {
                continue;
            }
            Class<?>[] pb = mb.getParameterTypes();
            boolean isStatic = Modifier.isStatic(ma.getModifiers());
            for (int t = 0; t < TRIALS; t++) {
                long seed = 31L * ma.toGenericString().hashCode() + t;
                SplittableRandom ra = new SplittableRandom(seed), rb = new SplittableRandom(seed);
                Object[] aa = new Object[pa.length], ab = new Object[pa.length];
                for (int i = 0; i < pa.length; i++) {
                    long s = seed * 1_000_003L + i;
                    aa[i] = value(pa[i], ra, s);
                    ab[i] = value(pb[i], rb, s);
                }
                Object thisA = null, thisB = null;
                if (!isStatic) {
                    if (!ValueFixtures.isValueType(ca) || !ValueFixtures.isValueType(cb)) {
                        break;
                    }
                    thisA = ValueFixtures.create(ca, seed ^ 0x9E3779B97F4A7C15L);
                    thisB = ValueFixtures.create(cb, seed ^ 0x9E3779B97F4A7C15L);
                }
                String where = ca.getSimpleName() + "." + ma.getName() + Arrays.toString(pa) + " trial " + t;
                Object rA = null, rB = null;
                Throwable eA = null, eB = null;
                try {
                    rA = call(ma, thisA, aa);
                } catch (TimeoutException e) {
                    mismatches.add(where + ": hangs");
                    break;
                } catch (Throwable e) {
                    eA = e;
                }
                try {
                    rB = call(mb, thisB, ab);
                } catch (TimeoutException e) {
                    mismatches.add(where + ": twin hangs");
                    break;
                } catch (Throwable e) {
                    eB = e;
                }
                if (eA != null || eB != null) {
                    if (eA == null || eB == null || eA.getClass() != eB.getClass()) {
                        mismatches.add(where + ": threw " + eA + " vs " + eB);
                    }
                    continue;
                }
                compared++;
                compare(where + " result", rA, rB, exact);
                if (thisA != null) {
                    compare(where + " this", thisA, thisB, exact);
                }
                for (int i = 0; i < pa.length; i++) {
                    if (!pa[i].isPrimitive()) {
                        compare(where + " arg" + i, aa[i], ab[i], exact);
                    }
                }
            }
        }
    }

    @Test
    void twinsAgree() throws Exception {
        Function<Class<?>, Class<?>> fd = mapping("f", "d");
        for (String p : FLOAT_DOUBLE) {
            drive(Class.forName(ROOT + p + "f"), Class.forName(ROOT + p + "d"), fd, false);
        }
        Function<Class<?>, Class<?>> il = mapping("i", "L");
        for (String p : INT_LONG) {
            drive(Class.forName(ROOT + p + "i"), Class.forName(ROOT + p + "L"), il, true);
        }
        for (String p : OVERLOADS) {
            Class<?> c = Class.forName(ROOT + p);
            drive(c, c, fd, false);
        }
        System.out.println("TwinParityTest: " + compared + " calls compared, " + mismatches.size() + " mismatches");
        mismatches.forEach(m -> System.out.println("  " + m));
        assertTrue(compared > 9000, "harness drove too few methods: " + compared);
        assertTrue(mismatches.isEmpty(), () -> mismatches.size() + " twin mismatches, first: " + mismatches.get(0));
    }
}
