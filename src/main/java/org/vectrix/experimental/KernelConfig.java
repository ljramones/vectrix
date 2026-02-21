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
package org.vectrix.experimental;

import org.vectrix.core.Internal;

/**
 * Global runtime configuration for kernel dispatch.
 */
@Internal("Internal runtime dispatch control surface; may change between releases.")
public final class KernelConfig {
    public static final String PROP_MATH_MODE = "vectrix.math.mode";
    public static final String PROP_SIMD_ENABLED = "vectrix.simd.enabled";

    private static volatile MathMode mathMode = parseMathMode(System.getProperty(PROP_MATH_MODE));
    private static volatile boolean simdEnabled = parseBoolean(System.getProperty(PROP_SIMD_ENABLED), true);

    private KernelConfig() {
    }

    public static MathMode mathMode() {
        return mathMode;
    }

    public static void setMathMode(MathMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode");
        }
        mathMode = mode;
    }

    public static boolean simdEnabled() {
        return simdEnabled;
    }

    public static void setSimdEnabled(boolean enabled) {
        simdEnabled = enabled;
    }

    /**
     * Reload config values from system properties.
     */
    public static void resetFromProperties() {
        mathMode = parseMathMode(System.getProperty(PROP_MATH_MODE));
        simdEnabled = parseBoolean(System.getProperty(PROP_SIMD_ENABLED), true);
    }

    public static MathMode parseMathMode(String value) {
        if (value == null || value.isEmpty()) {
            return MathMode.FAST;
        }
        return "STRICT".equalsIgnoreCase(value) ? MathMode.STRICT : MathMode.FAST;
    }

    public static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
