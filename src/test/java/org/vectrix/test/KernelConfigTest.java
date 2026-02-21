/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.vectrix.experimental.KernelConfig;
import org.vectrix.experimental.MathMode;
import org.vectrix.simd.SimdSupport;

class KernelConfigTest {
    @Test
    void parseMathModeDefaultsAndStrict() {
        assertEquals(MathMode.FAST, KernelConfig.parseMathMode(null));
        assertEquals(MathMode.FAST, KernelConfig.parseMathMode(""));
        assertEquals(MathMode.STRICT, KernelConfig.parseMathMode("STRICT"));
        assertEquals(MathMode.STRICT, KernelConfig.parseMathMode("strict"));
        assertEquals(MathMode.FAST, KernelConfig.parseMathMode("something-else"));
    }

    @Test
    void parseBooleanDefaults() {
        assertTrue(KernelConfig.parseBoolean(null, true));
        assertFalse(KernelConfig.parseBoolean(null, false));
        assertTrue(KernelConfig.parseBoolean("true", false));
        assertFalse(KernelConfig.parseBoolean("false", true));
    }

    @Test
    void resetFromPropertiesReadsMathAndSimdFlags() {
        String prevMode = System.getProperty(KernelConfig.PROP_MATH_MODE);
        String prevSimd = System.getProperty(KernelConfig.PROP_SIMD_ENABLED);
        try {
            System.setProperty(KernelConfig.PROP_MATH_MODE, "STRICT");
            System.setProperty(KernelConfig.PROP_SIMD_ENABLED, "false");
            KernelConfig.resetFromProperties();
            assertEquals(MathMode.STRICT, KernelConfig.mathMode());
            assertFalse(KernelConfig.simdEnabled());
        } finally {
            restoreProperty(KernelConfig.PROP_MATH_MODE, prevMode);
            restoreProperty(KernelConfig.PROP_SIMD_ENABLED, prevSimd);
            KernelConfig.resetFromProperties();
        }
    }

    @Test
    void simdSupportQueryIsSafe() {
        int lanes = SimdSupport.preferredFloatLanes();
        assertTrue(lanes >= 1);
        SimdSupport.Backend backend = SimdSupport.backend();
        assertTrue(backend == SimdSupport.Backend.SCALAR || backend == SimdSupport.Backend.VECTOR_API);
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
