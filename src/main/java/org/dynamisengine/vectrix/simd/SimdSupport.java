/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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
package org.dynamisengine.vectrix.simd;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * The SIMD switch and the Vector API probe.
 * <p>
 * Vectrix ships two jars: the plain jar, all scalar, and the {@code -simd} classifier jar, whose kernels (FFT, BLAS,
 * GEMM, neural-network kernels, frustum culling, transform composition) also have Vector API paths. The SIMD paths
 * need {@code --add-modules jdk.incubator.vector} and run only while {@link #isEnabled()}: the switch starts from the
 * system property {@code vectrix.simd.enabled} (default {@code true}) and can be changed at any time with
 * {@link #setEnabled(boolean)}. Every SIMD path gives the same bits as its scalar path, so the switch changes speed,
 * never results. {@link #backend()} reports which path is in use.
 *
 * @since 1.0.0
 */
public final class SimdSupport {

    /** The system property that sets the initial state of {@link #setEnabled(boolean)}. */
    public static final String PROP_ENABLED = "vectrix.simd.enabled";

    /** The kernel backend in use. */
    public enum Backend {
        /** Plain Java. */
        SCALAR,
        /** The JDK Vector API ({@code -simd} jar, {@code jdk.incubator.vector} present, SIMD enabled). */
        VECTOR_API
    }

    private static final Capability CAPABILITY = detect();

    /** Whether this jar contains the Vector API paths (the {@code -simd} classifier). */
    private static final boolean SIMD_BUILD;

    static {
        //#ifdef __HAS_VECTOR_API__
        SIMD_BUILD = true;
        //#else
        SIMD_BUILD = false;
        //#endif
    }

    private static volatile boolean enabled = parseEnabled(System.getProperty(PROP_ENABLED));

    private SimdSupport() {
    }

    /**
     * @return the backend kernels use now: {@link Backend#VECTOR_API} in the {@code -simd} jar when the Vector API
     *         module is present and SIMD is enabled, otherwise {@link Backend#SCALAR}
     */
    public static Backend backend() {
        if (!enabled || !SIMD_BUILD) {
            return Backend.SCALAR;
        }
        return CAPABILITY.vectorApiAvailable ? Backend.VECTOR_API : Backend.SCALAR;
    }

    /**
     * @return whether this jar contains Vector API paths: true for the {@code -simd} classifier jar
     * @since 2.6.0
     */
    public static boolean isSimdBuild() {
        return SIMD_BUILD;
    }

    /**
     * @return whether SIMD paths may run (they also need the {@code -simd} jar and the Vector API module)
     * @since 2.6.0
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables the SIMD paths for all threads, effective on the next kernel call. Results do not change,
     * only speed: useful for benchmarking and for diagnosing a JVM problem.
     *
     * @param on whether SIMD paths may run
     * @since 2.6.0
     */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    private static boolean parseEnabled(String value) {
        return value == null || value.isEmpty() || Boolean.parseBoolean(value.trim());
    }

    /** @return whether the JDK Vector API module is present (whatever {@link #isEnabled()} says) */
    public static boolean isVectorApiAvailable() {
        return CAPABILITY.vectorApiAvailable;
    }

    /** @return the preferred number of float lanes of the Vector API, or 1 without it */
    public static int preferredFloatLanes() {
        return CAPABILITY.preferredFloatLanes;
    }

    private static Capability detect() {
        try {
            Class<?> floatVectorClass = Class.forName("jdk.incubator.vector.FloatVector");
            Field speciesPreferredField = floatVectorClass.getField("SPECIES_PREFERRED");
            Object speciesPreferred = speciesPreferredField.get(null);
            // Look length() up on the public VectorSpecies interface: the runtime class (FloatVector$FloatSpecies)
            // is not public, so invoking through it threw IllegalAccessException and detection always reported
            // SCALAR, even with --add-modules jdk.incubator.vector (review M-15).
            Method lengthMethod = Class.forName("jdk.incubator.vector.VectorSpecies").getMethod("length");
            int lanes = ((Integer) lengthMethod.invoke(speciesPreferred)).intValue();
            if (lanes < 1) {
                lanes = 1;
            }
            return new Capability(true, lanes);
        } catch (Throwable t) {
            return new Capability(false, 1);
        }
    }

    private static final class Capability {
        final boolean vectorApiAvailable;
        final int preferredFloatLanes;

        Capability(boolean vectorApiAvailable, int preferredFloatLanes) {
            this.vectorApiAvailable = vectorApiAvailable;
            this.preferredFloatLanes = preferredFloatLanes;
        }
    }
}
