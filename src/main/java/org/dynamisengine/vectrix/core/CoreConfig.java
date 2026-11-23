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
package org.dynamisengine.vectrix.core;

/**
 * Runtime configuration for math behavior used in core primitives.
 * <p>
 * The math mode has a process-wide default ({@link #setMathMode}, or {@code -Dvectrix.math.mode}) and can be
 * overridden for one task with {@link #runWithMathMode} / {@link #callWithMathMode}. An override is visible only to
 * the thread running the task (and to tasks it forks with {@code StructuredTaskScope}, which inherit scoped values);
 * other threads keep the default. Overrides nest, and end when the task returns or throws.
 */
public final class CoreConfig {
    /** System property selecting the initial math mode ({@code FAST} or {@code STRICT}). */
    public static final String PROP_MATH_MODE = "vectrix.math.mode";

    /**
     * {@link MathMode#STRICT} tolerance for float data: a quaternion or axis is unit when
     * {@code |lengthSquared − 1| <= 1e-5}, and a quaternion is pure when {@code |w| <= 1e-5}. This is about 80 ulps
     * of 1.0, which leaves room for the rounding drift of hundreds of chained float quaternion products (about
     * 1.8e-6 after 32) while still rejecting unnormalised input.
     */
    public static final float STRICT_UNIT_TOLERANCE_F = 1e-5f;

    /** {@link MathMode#STRICT} tolerance for double data, with the same meaning as {@link #STRICT_UNIT_TOLERANCE_F}. */
    public static final double STRICT_UNIT_TOLERANCE_D = 1e-6;

    private static volatile MathMode mathMode = parseMathMode(System.getProperty(PROP_MATH_MODE));

    /** The current task's override, bound by {@link #runWithMathMode} / {@link #callWithMathMode}. */
    private static final ScopedValue<MathMode> SCOPED_MODE = ScopedValue.newInstance();

    /**
     * Number of override scopes active in any thread. While it is zero, {@link #mathMode()} skips the scoped-value
     * lookup and costs one extra volatile read.
     */
    private static final java.util.concurrent.atomic.AtomicInteger ACTIVE_OVERRIDES =
            new java.util.concurrent.atomic.AtomicInteger();

    private CoreConfig() {
    }

    /**
     * The math mode in effect for the calling thread: the innermost {@link #runWithMathMode} /
     * {@link #callWithMathMode} override if one is active, otherwise the process-wide default.
     *
     * @return the math mode in effect for the calling thread
     */
    public static MathMode mathMode() {
        // JDK 25's ScopedValue.orElse rejects null, so the default is passed as the fallback
        return ACTIVE_OVERRIDES.get() != 0 ? SCOPED_MODE.orElse(mathMode) : mathMode;
    }

    /**
     * The process-wide default math mode, ignoring any override active on the calling thread.
     *
     * @return the process-wide default math mode
     */
    public static MathMode defaultMathMode() {
        return mathMode;
    }

    /**
     * Runs {@code task} with {@code mode} as the math mode for the calling thread, without changing the mode other
     * threads see.
     *
     * @param mode the math mode for the duration of {@code task}
     * @param task the task to run on the calling thread
     * @throws IllegalArgumentException if {@code mode} or {@code task} is null
     */
    public static void runWithMathMode(MathMode mode, Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task");
        }
        callWithMathMode(mode, () -> {
            task.run();
            return null;
        });
    }

    /**
     * Calls {@code task} with {@code mode} as the math mode for the calling thread and returns its result, without
     * changing the mode other threads see. Checked exceptions thrown by {@code task} propagate unchanged.
     *
     * @param <T> the result type
     * @param <X> the type of exception {@code task} may throw
     * @param mode the math mode for the duration of {@code task}
     * @param task the task to call on the calling thread
     * @return the result of {@code task}
     * @throws X if {@code task} throws it
     * @throws IllegalArgumentException if {@code mode} or {@code task} is null
     */
    public static <T, X extends Throwable> T callWithMathMode(MathMode mode, ScopedValue.CallableOp<? extends T, X> task)
            throws X {
        if (mode == null) {
            throw new IllegalArgumentException("mode");
        }
        if (task == null) {
            throw new IllegalArgumentException("task");
        }
        ACTIVE_OVERRIDES.incrementAndGet();
        try {
            return ScopedValue.where(SCOPED_MODE, mode).call(task);
        } finally {
            ACTIVE_OVERRIDES.decrementAndGet();
        }
    }

    /**
     * Sets the process-wide default math mode. Overrides active through {@link #runWithMathMode} /
     * {@link #callWithMathMode} keep their mode until they end.
     *
     * @param mode the new default math mode
     * @throws IllegalArgumentException if {@code mode} is null
     */
    public static void setMathMode(MathMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode");
        }
        mathMode = mode;
    }

    /**
     * Parse a math-mode property value. {@code null}, empty or unrecognised values mean {@link MathMode#FAST}.
     *
     * @param value the property value, matched case-insensitively against {@code STRICT}; may be null
     * @return {@link MathMode#STRICT} for {@code "STRICT"}, otherwise {@link MathMode#FAST}
     */
    public static MathMode parseMathMode(String value) {
        if (value == null || value.isEmpty()) {
            return MathMode.FAST;
        }
        return "STRICT".equalsIgnoreCase(value) ? MathMode.STRICT : MathMode.FAST;
    }

    /**
     * Whether a float squared length counts as unit under {@link MathMode#STRICT}. NaN is never unit.
     *
     * @param lengthSquared the squared length to test
     * @return {@code true} if {@code |lengthSquared - 1| <= STRICT_UNIT_TOLERANCE_F}
     */
    public static boolean withinStrictUnitTolerance(float lengthSquared) {
        return java.lang.Math.abs(lengthSquared - 1.0f) <= STRICT_UNIT_TOLERANCE_F;
    }

    /**
     * Whether a double squared length counts as unit under {@link MathMode#STRICT}. NaN is never unit.
     *
     * @param lengthSquared the squared length to test
     * @return {@code true} if {@code |lengthSquared - 1| <= STRICT_UNIT_TOLERANCE_D}
     */
    public static boolean withinStrictUnitTolerance(double lengthSquared) {
        return java.lang.Math.abs(lengthSquared - 1.0) <= STRICT_UNIT_TOLERANCE_D;
    }

    /**
     * A one-line summary of every setting that can change Vectrix's output bits, for recording in replay files
     * and comparing on playback. Two runs can only be expected to agree bitwise when their fingerprints are
     * equal; the {@code os.arch} and {@code java.vm.version} entries only matter while
     * {@code vectrix.strictMath} is false. See {@code docs/determinism.md}.
     *
     * @return the settings as {@code name=value} pairs separated by semicolons
     */
    public static String determinismFingerprint() {
        return "vectrix.math.mode=" + mathMode()
                + ";vectrix.strictMath=" + Options.STRICT_MATH
                + ";joml.fastmath=" + Options.FASTMATH
                + ";joml.sinLookup=" + Options.SIN_LOOKUP + "/" + Options.SIN_LOOKUP_BITS
                + ";joml.useMathFma=" + Options.USE_MATH_FMA
                + ";vectrix.simd.enabled=" + System.getProperty("vectrix.simd.enabled", "true")
                + ";vectrix.skinning.simd=" + System.getProperty("vectrix.skinning.simd", "false")
                + ";vectrix.culling.simd=" + System.getProperty("vectrix.culling.simd", "true")
                + ";os.arch=" + System.getProperty("os.arch")
                + ";java.vm.version=" + System.getProperty("java.vm.version");
    }
}
